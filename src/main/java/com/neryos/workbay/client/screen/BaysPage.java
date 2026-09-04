package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import com.neryos.workbay.world.BayGeometry;
import com.neryos.workbay.world.FaceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Screen 1 — Bays. SPEC.md §4.
 *
 * <p><b>Its job at rest is to answer "is anything wrong?" before the player reads anything.</b> The
 * problem count in the header and the status pip on each bay do that; everything else is what they
 * look at once the answer is yes.
 *
 * <p><b>The height is not fixed at SPEC.md §4's 268.</b> At 268 the LINKS list gets ninety pixels,
 * which is three rows — the exact fault that got the old section replaced, in the same section that
 * requires the list to stay readable at thirty. It wants 316. But Minecraft only ever guarantees a
 * 240-tall scaled canvas, so a fixed 268 already runs off the bottom at a high GUI scale and 316 is
 * worse. The page therefore takes whatever the window has between 240 and 316, and the bay rack's
 * pitch and the number of visible rows are derived from that rather than written down.
 */
class BaysPage extends WorkbayPage {

    private static final int WIDTH = 320;

    /**
     * SPEC.md §4 starts at 268 tall. Minecraft only ever guarantees a 240-tall scaled canvas, so a
     * fixed 268 is a screen that runs off the bottom for anyone at a high GUI scale — and 316, which
     * is what the LINKS list actually wants, is worse. So the page takes what the window has between
     * those two, and the rack pitch and the row count follow from it.
     */
    private static final int MIN_HEIGHT = 240;
    private static final int MAX_HEIGHT = 316;

    private static final int RACK_X = 8;
    private static final int RACK_Y = 50;

    private static final int LINKS_Y_FROM_BOTTOM = 146;
    private static final int ROW_PITCH = 20;
    private static final int LIST_X = 40;
    private static final int LIST_W = 268;

    private final int height;
    private final int rackPitch;
    private final int slot;
    private final int linksY;
    private final int rowY;
    private final int rows;

    /**
     * The faces column. 80 wide rather than 68, and everything in it derived from that: at 68 the
     * empty-bay hint wrapped to four lines in a column three words wide, the three type buttons
     * stopped two pixels short of the well above them, and the turn hint and the in/out key sat on
     * hardcoded x's that matched neither. The column read unfinished next to the rest of the screen
     * because it was the one part of it laid out by hand.
     */
    private static final int FACES_X = 232;
    private static final int WELL_X = FACES_X;
    private static final int WELL_W = 80;
    private static final int WELL_Y = 76;
    private static final int WELL_H = 62;
    /** Three type buttons, filling the column exactly: 3 x 24 on a 28 pitch is the well's width. */
    private static final int TYPE_W = 24;
    private static final int TYPE_PITCH = 28;
    private static final int CUBE_CX = WELL_X + WELL_W / 2;
    private static final int CUBE_CY = 106;
    private static final int CUBE_SIZE = 30;

    /** The header's power readout: right-aligned here, and where the counters beside it must stop. */
    private static final int POWER_X = 246;
    private static final int POWER_W = 58;

    /** The Levy readout's column: from the machine row's left edge to the faces panel. */
    private static final int LEVY_W = FACES_X - 4 - 50;
    /** The skim readout, between the sort button and the Add button. */
    private static final int SKIM_W = LIST_W - 46 - 40 - 124;

    /** Client-side view state: the list's filter, sort, scroll and which row's gear is open. */
    private enum Filter { THIS_BAY, ALL_BAYS, PROBLEMS }

    /** ADDED is first and is the default: a list that reorders itself is a list you cannot learn. */
    private enum Sort { ADDED, BAY, TYPE, STATUS }

    /**
     * What Copy holds. Static and client-side on purpose: the point of copying a bay is pasting it
     * onto the next seven, and the server is told the whole config in the paste action itself, so
     * there is no second clipboard anywhere to disagree with this one.
     */
    @org.jetbrains.annotations.Nullable
    private static FaceConfig copied;

    // This bay, in the order links were made. The list is per bay the way XNet's channels are per
    // controller-block: a link belongs to exactly one bay, and the screen you configure a bay on
    // should show that bay's links and nothing else. ALL_BAYS is one click away on the funnel.
    private static Filter filter = Filter.THIS_BAY;
    private static Sort sort = Sort.ADDED;
    private static BusConfig.Resource faceType = BusConfig.Resource.ITEM;

    /** Kept between openings, so the angle a player turned a machine to is still there next time. */
    private static final BlockPreview PREVIEW = new BlockPreview();

    /**
     * Which bay and machine {@link #PREVIEW}'s angle belongs to. Keeping the angle is right for the
     * machine the player turned and wrong for the next one: racking a block, or switching bays, was
     * showing whatever side the last one had been left on, which for a chest is its back. The angle
     * now survives a re-open and resets the moment the thing in the well is a different thing.
     */
    @org.jetbrains.annotations.Nullable
    private static String previewSubject;

    private int scroll;

    /**
     * True while the list is showing what you could attach to this bay rather than what already is.
     *
     * <p>A picker, not a popup: it reuses the row list, its scrolling and its hit testing whole. A
     * floating panel over the list would need its own version of all three, and would cover the
     * bay rack the player is picking for.
     */
    private static boolean adding;

    /**
     * The picker's two lists. A Connector already standing in the world and a bay with no wire at
     * all are attached the same way and end up as the same kind of link, but they are found in
     * completely different ways -- one by walking your base, one by looking at this rack -- and
     * mixing them into one scrolling list made both harder to find.
     */
    private enum AddTab { CONNECTORS, BAYS }

    private static AddTab addTab = AddTab.CONNECTORS;

    /**
     * What is ticked in the picker, so several things can be attached in one go. Ticking is the
     * whole reason the picker has a confirm button: attaching links one at a time closed the list
     * after each one, which for a bay that needs six of them is five needless round trips.
     */
    private static final java.util.Set<UUID> pickedLinks = new java.util.LinkedHashSet<>();
    private static final java.util.Set<Integer> pickedBays = new java.util.LinkedHashSet<>();

    BaysPage(WorkbayScreen screen) {
        super(screen);
        height = Math.clamp(screen.availableHeight() - 8, MIN_HEIGHT, MAX_HEIGHT);
        // Eight bay slots always fit, however short the window is; they lose pitch, not slots.
        rackPitch = Math.clamp((height - RACK_Y - 12) / BayGeometry.MAX_BAYS, 20, 26);
        slot = rackPitch - 2;
        linksY = height - LINKS_Y_FROM_BOTTOM < 170 ? 170 : height - LINKS_Y_FROM_BOTTOM;
        // The header row's tallest control (Pair, 18px) reached to linksY+18; rowY-4 is the box's
        // own top edge, so the old +20 left only 2px of clearance and visually touched. Reported
        // from play as "no space between Pair/Sort/Filter and the list."
        rowY = linksY + 28;
        rows = Math.max(2, (height - rowY - 14) / ROW_PITCH);
    }

    @Override
    int width() {
        return WIDTH;
    }

    @Override
    int height() {
        return height;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        header(g, mouseX, mouseY, "WORKBAY");
        summary(g, mouseX, mouseY);
        rack(g, mouseX, mouseY);
        machine(g, mouseX, mouseY);
        levy(g, mouseX, mouseY);
        faces(g, mouseX, mouseY);
        links(g, mouseX, mouseY);
    }

    // ------------------------------------------------------ header y 30..44

    private void summary(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        var font = screen.font();
        int used = (int) snap.bays().stream()
            .filter(bay -> bay.hosted().isPresent()).count();

        // Three counters, packed left to right against where the power readout starts, rather than
        // sitting on hardcoded pitches with guessed widths. Guessed widths were wrong twice on one
        // line: "no problems" arrived as "no proble..." in a 60-wide box, and "128 links" does not
        // fit 46 either. Packed, each one has exactly what it needs and the last one has the rest,
        // which is the only version of this that cannot be wrong for a count nobody tried.
        int textX = x(8);
        int limit = x(POWER_X - POWER_W);
        int cursor = textX;
        if (!snap.bays().isEmpty()) {
            cursor = counter(g, WorkbayScreen.gui("count.bays", used, snap.bayCapacity()),
                cursor, limit, Draw.TEXT_DIM);
        }
        int links = snap.links().size();
        cursor = counter(g, WorkbayScreen.gui(links == 1 ? "count.links.one" : "count.links", links),
            cursor, limit, Draw.TEXT_DIM);

        // The count and the list have to agree, and the list is filtered to one bay by default — so
        // the count is a control, not a label: clicking it shows exactly the rows it is counting.
        // Without that the header can honestly say "1 problem" while every visible row looks fine.
        int problems = snap.problems();
        String problemText = WorkbayScreen.gui(problems == 0 ? "count.problems.none"
            : problems == 1 ? "count.problems.one" : "count.problems", problems).getString();
        boolean problemHover = problems > 0
            && screen.hovered(cursor, y(29), font.width(problemText) + 2, 12, mouseX, mouseY);
        text(g, problemText, cursor, y(31), limit - cursor,
            problems == 0 ? Draw.TEXT_FAINT : problemHover ? Draw.TEXT : Draw.RED);
        if (problems > 0) {
            screen.hit(cursor, y(29), font.width(problemText) + 2, 12, () -> {
                filter = Filter.PROBLEMS;
                adding = false;
                scroll = 0;
            }, WorkbayScreen.gui("links.problems", problems),
                WorkbayScreen.gui("links.problems.tip"));
        }

        // A bar with no figure beside it reads as broken, and an empty one reads as broken twice
        // over, so with no capacity at all the words replace the bar entirely (SPEC.md §7).
        boolean powered = snap.energyCapacity() > 0;
        String power = powered
            ? Draw.compact(snap.energy()) + " / " + Draw.compact(snap.energyCapacity())
            : WorkbayScreen.gui("power.none").getString();
        // 58, not 48: at 48 "0 / 100.0k" arrived as "0 / 100..." on the very first screen a
        // player sees. The room between the problem count and the bar was there all along.
        textRight(g, power, x(POWER_X), y(31), POWER_W, powered ? Draw.TEXT_DIM : Draw.TEXT_FAINT);
        if (powered) {
            Draw.bar(g, x(250), y(29), 44, 9, snap.energy(), snap.energyCapacity(), Draw.AMBER);
            screen.hit(x(250), y(29), 44, 9, () -> { },
                WorkbayScreen.gui("power", Draw.exact(snap.energy()),
                    Draw.exact(snap.energyCapacity())),
                WorkbayScreen.gui("power.tip"));
        }

        // No room code on screen. SPEC.md §14: a network belongs to a player, and the Workbay finds
        // it again by owner when it is rebuilt, so there is nothing here for a player to read out,
        // type in, or lose.
        // The separator between the header band and the working area.
        g.fill(x(6), y(44), x(WIDTH - 6), y(45), Draw.EDGE_DARK);
    }

    /**
     * One header counter, given exactly the width it needs, and clamped so it can never run into
     * the readout to its right.
     *
     * @return where the next counter starts
     */
    private int counter(GuiGraphics g, Component label, int px, int limit, int colour) {
        String s = label.getString();
        int room = Math.min(screen.font().width(s), limit - px);
        text(g, s, px, y(31), room, colour);
        return px + room + 6;
    }

    // ------------------------------------------------------------- bay rack

    private void rack(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        for (int index = 0; index < 8; index++) {
            int px = x(RACK_X + 2);
            int py = y(RACK_Y + index * rackPitch);
            WorkbaySnapshot.Bay bay = snap.bay(index);
            boolean locked = bay.state() == WorkbaySnapshot.State.LOCKED;
            boolean selected = index == snap.selectedBay();
            boolean hover = screen.hovered(px, py, slot, slot, mouseX, mouseY);

            if (selected) {
                g.fill(x(RACK_X - 2), py, x(RACK_X + 1), py + slot, Draw.SELECT);
            }
            Draw.slot(g, px, py, slot, slot);
            if (hover && !locked) {
                g.fill(px + 1, py + 1, px + slot - 1, py + slot - 1, 0x33FFFFFF);
            }

            ItemStack icon = iconFor(bay.hosted());
            if (!icon.isEmpty()) {
                // The hosted machine's own item, so a bay is identified at a glance (SPEC.md §4).
                g.renderItem(icon, px + 4, py + 4);
            }
            if (locked) {
                g.fill(px + 1, py + 1, px + slot - 1, py + slot - 1, 0x99000000);
            }
            // The 5x5 status pip, top-left, inside the slot.
            g.fill(px + 2, py + 2, px + 7, py + 7, pipColour(bay.state()));

            int captured = index;
            screen.hit(px, py, slot, slot, () -> screen.send(WorkbayAction.SELECT_BAY, captured),
                bayTooltip(bay));
        }
    }

    private Component[] bayTooltip(WorkbaySnapshot.Bay bay) {
        Component name = bay.name().isEmpty()
            ? bay.hosted().map(BaysPage::displayName).orElse(WorkbayScreen.gui("bay.empty"))
            : Component.literal(bay.name());
        return switch (bay.state()) {
            case LOCKED -> new Component[] {
                WorkbayScreen.gui("bay.locked").copy().withStyle(ChatFormatting.GRAY),
                WorkbayScreen.gui("bay.locked.tip") };
            case EMPTY -> new Component[] {
                WorkbayScreen.gui("bay.n", bay.index() + 1), WorkbayScreen.gui("bay.empty.tip") };
            case INERT -> new Component[] {
                name, WorkbayScreen.gui("bay.inert").copy().withStyle(ChatFormatting.GOLD) };
            default -> new Component[] {
                name, WorkbayScreen.gui("bay." + bay.state().name().toLowerCase(java.util.Locale.ROOT)) };
        };
    }

    private static int pipColour(WorkbaySnapshot.State state) {
        return switch (state) {
            case RUNNING -> Draw.GREEN;
            case IDLE -> Draw.BLUE;
            case INERT -> Draw.AMBER;
            case LOCKED -> Draw.EDGE_DARK;
            case EMPTY -> Draw.GREY;
        };
    }

    // ------------------------------------------- machine block x 50..220

    private void machine(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        WorkbaySnapshot.Bay bay = snap.bay(snap.selectedBay());
        var font = screen.font();

        int slotX = x(50);
        int slotY = y(52);
        Draw.slot(g, slotX, slotY, 40, 40);
        ItemStack icon = iconFor(bay.hosted());
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(slotX + 4, slotY + 4, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        boolean empty = bay.hosted().isEmpty();
        if (empty && bay.state() != WorkbaySnapshot.State.LOCKED) {
            // The whole insert flow, in the one place it happens.
            screen.hit(slotX, slotY, 40, 40, () -> screen.send(WorkbayAction.RACK),
                WorkbayScreen.gui("bay.empty"), WorkbayScreen.gui("bay.rack.tip"));
            if (screen.hovered(slotX, slotY, 40, 40, mouseX, mouseY)) {
                g.fill(slotX + 1, slotY + 1, slotX + 39, slotY + 39, 0x33FFFFFF);
            }
        }

        // Everything in this column is clamped to where the faces panel starts. A machine name is
        // whatever another mod called it, and an unclamped one runs across the cube and off the
        // panel entirely.
        int room = FACES_X - 4 - 98;
        String shown = nameOf(bay, snap.selectedBay());
        if (!screen.renaming()) {
            text(g, shown, x(98), y(56), room, Draw.TEXT);
        }

        if (bay.energyCapacity() > 0) {
            // The bar stops where the figures start rather than at a fixed 84. At 84 it ran twelve
            // pixels under a Mekanism cube's "0 / 1600.0k" - invisible while an empty bar was black
            // inside, and plain the moment the empty part got its tint.
            String power = Draw.compact(bay.energy()) + " / " + Draw.compact(bay.energyCapacity());
            int barW = Math.max(20, FACES_X - 4 - font.width(power) - 6 - 98);
            Draw.bar(g, x(98), y(68), barW, 9, bay.energy(), bay.energyCapacity(), Draw.AMBER);
            textRight(g, power, x(FACES_X - 4), y(69), room - barW - 6, Draw.TEXT_DIM);
            screen.hit(x(98), y(68), barW, 9, () -> { },
                WorkbayScreen.gui("power", Draw.exact(bay.energy()),
                    Draw.exact(bay.energyCapacity())),
                WorkbayScreen.gui("power.machine.tip"));
        } else if (!empty) {
            // A machine with no energy handler. The old placeholder was a bare dash floating
            // beside an empty bar, which read as a rendering fault rather than as a fact.
            text(g, WorkbayScreen.gui("power.none"), x(98), y(69), room, Draw.TEXT_FAINT);
        }

        // The redstone mode shares this line with the status, right-aligned, and it is the short
        // form: "Redstone: without a signal" is 156 pixels on a line 130 wide and ran straight
        // through the status text. The long form is the button's tooltip title.
        String mode = bay.redstone() == com.neryos.workbay.world.RedstoneMode.ALWAYS ? ""
            : WorkbayScreen.gui("redstone.short." + bay.redstone().getSerializedName()).getString();
        int modeRoom = mode.isEmpty() ? 0 : Math.min(font.width(mode), room / 2);
        if (!mode.isEmpty()) {
            textRight(g, mode, x(FACES_X - 4), y(82), modeRoom, Draw.TEXT_DIM);
        }
        int statusRoom = room - (mode.isEmpty() ? 0 : modeRoom + 6);
        text(g, statusLine(bay), x(98), y(82), statusRoom, statusColour(bay.state()));

        // The button row at y=98, 20x20 on a 24px pitch. Bay View is the last of the six and is
        // real now: SPEC.md §4's rule is that a control whose screen is not built is hidden rather
        // than drawn faint, and the corollary is that it appears the moment the screen exists.
        //
        // All five are drawn glyphs, not item sprites. An item says what a thing is made of, not
        // what it does: a sheet of paper does not read as "copy" and a boot reads as nothing at
        // all. Item sprites are kept for content — the hosted machine, the bay rack, the three
        // resource types — which is the same line Mekanism and AE2 draw.
        boolean canEject = !empty;
        actionButton(g, mouseX, mouseY, x(50), WBIcons.EJECT, canEject, false,
            () -> screen.send(WorkbayAction.EJECT),
            WorkbayScreen.gui("button.eject"), WorkbayScreen.gui("button.eject.tip"));
        boolean unlocked = bay.state() != WorkbaySnapshot.State.LOCKED;
        actionButton(g, mouseX, mouseY, x(74), WBIcons.RENAME, unlocked, false,
            () -> screen.beginRename(x(98), y(53), room, 14, bay.name(),
                typed -> screen.sendText(WorkbayAction.SET_BAY_NAME, typed)),
            WorkbayScreen.gui("button.rename"), WorkbayScreen.gui("button.rename.tip"));
        // Lit only while the gate is actually in use, so the button says which state it is in
        // before the player hovers it.
        boolean gated = bay.redstone() != com.neryos.workbay.world.RedstoneMode.ALWAYS;
        actionButton(g, mouseX, mouseY, x(98), WBIcons.REDSTONE, unlocked, gated,
            () -> screen.send(WorkbayAction.CYCLE_REDSTONE),
            WorkbayScreen.gui("redstone." + bay.redstone().getSerializedName()),
            WorkbayScreen.gui("redstone." + bay.redstone().getSerializedName() + ".tip"));

        // Copy and paste. Eight bays running the same machine is the first complaint this mod will
        // get, and Mekanism answers it with a Configuration Card (SPEC.md §7).
        actionButton(g, mouseX, mouseY, x(122), WBIcons.COPY, true, false,
            () -> copied = bay.faces(),
            WorkbayScreen.gui("button.copy"), WorkbayScreen.gui("button.copy.tip"));
        actionButton(g, mouseX, mouseY, x(146), WBIcons.PASTE, copied != null, false,
            () -> screen.send(WorkbayAction.PASTE_BAY, copied.bits()),
            WorkbayScreen.gui("button.paste"),
            WorkbayScreen.gui(copied == null ? "button.paste.empty" : "button.paste.tip"));

        // Bay View. SPEC.md §1 grants it to the base Workbay, which is what settles §5's stray
        // "unlocked by the first Expansion Plate": a racked container that cannot be filled by hand
        // is a hole in the loop, not a feature to sell an upgrade with.
        actionButton(g, mouseX, mouseY, x(170), WBIcons.SCREEN, !empty, false,
            () -> screen.send(WorkbayAction.OPEN_BAY_VIEW),
            WorkbayScreen.gui("button.bayview"), WorkbayScreen.gui("button.bayview.tip"));

        // Enter bay. The other half of §5, and the half a player asks for first: Bay View can only
        // show what a capability exposes, and a machine's recipe mode, side config and upgrade
        // slots are exposed by nothing. Standing next to the block is the only way to its own
        // screen - opening that screen remotely disconnects the client - so this opens that screen
        // by way of the bay. Disabled on an empty bay: nobody stays in the Backshop without a
        // screen open, and an empty bay has none to open.
        actionButton(g, mouseX, mouseY, x(194), WBIcons.ENTER, !empty, false,
            () -> screen.send(WorkbayAction.ENTER_BAY),
            WorkbayScreen.gui("button.enter"), WorkbayScreen.gui("button.enter.tip"));
    }

    /**
     * The Levy readout, in the strip the machine block and the LINKS list leave empty.
     *
     * <p>It is on <b>this</b> screen because the alternative is a player changing screens to find
     * out whether the dial they turned is doing anything, and a player who has to go and look does
     * not look. The banked total alone cannot answer that - it moves once every 200 ticks - so the
     * line under it is the batch filling up, which moves while they watch. With nothing coming it
     * says which nothing it is: no Assay racked, or the dial still at zero.
     */
    private void levy(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        var font = screen.font();
        boolean assay = hasAssay(snap);
        boolean earning = skimming(snap);

        String value = WorkbayScreen.gui("levy", snap.levy()).getString();
        text(g, value, x(50), y(124), LEVY_W, earning ? Draw.AMBER : Draw.TEXT_DIM);

        Component state = !assay ? WorkbayScreen.gui("levy.no_assay")
            : snap.skimRate() == 0 ? WorkbayScreen.gui("levy.dial_off")
            : WorkbayScreen.gui("levy.batch", snap.skimmed(),
                com.neryos.workbay.content.assay.AssayBlock.ITEMS_PER_LEVY);
        text(g, state, x(50), y(136), LEVY_W, earning ? Draw.TEXT_DIM : Draw.TEXT_FAINT);

        int w = Math.min(LEVY_W, Math.max(font.width(value), font.width(state.getString()))) + 4;
        screen.hit(x(48), y(122), w, 26, () -> { },
            WorkbayScreen.gui("levy.name", snap.levy()),
            WorkbayScreen.gui(earning ? "levy.tip" : assay ? "levy.dial_off.tip" : "levy.no_assay.tip"));
    }

    private static net.minecraft.world.item.ItemStack resourceItem(BusConfig.Resource resource) {
        return switch (resource) {
            case ITEM -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK);
            case ENERGY -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL);
            case FLUID -> {
                var water = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
                water.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(
                        Optional.of(net.minecraft.world.item.alchemy.Potions.WATER),
                        Optional.empty(), java.util.List.of()));
                yield water;
            }
        };
    }

    /** Every item render in the mod is 16x16; this scales one down to sit where a glyph used to. */
    private void resourceIcon(GuiGraphics g, BusConfig.Resource resource, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(resourceItem(resource), 0, 0);
        g.pose().popPose();
    }

    /** A 20x20 button in the machine row: enabled draws lit and clicks, disabled draws sunken. */
    private void actionButton(GuiGraphics g, int mouseX, int mouseY, int px, String[] icon,
        boolean enabled, boolean lit, Runnable onClick, Component name, Component tip) {
        boolean hover = screen.hovered(px, y(98), 20, 20, mouseX, mouseY);
        Draw.button(g, px, y(98), 20, 20, hover && enabled, lit && enabled, enabled);
        WBIcons.draw(g, icon, px + 4, y(102), enabled ? Draw.TEXT : Draw.TEXT_FAINT);
        screen.hit(px, y(98), 20, 20, enabled ? onClick : () -> { }, name, tip);
    }

    /**
     * The short form. The long one — "nothing can reach this machine on any face" — is a sentence,
     * and a sentence does not fit on a line 130 pixels wide; it lives in the bay's tooltip.
     */
    private Component statusLine(WorkbaySnapshot.Bay bay) {
        return WorkbayScreen.gui("bay.short." + bay.state().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int statusColour(WorkbaySnapshot.State state) {
        return switch (state) {
            case RUNNING -> Draw.GREEN;
            case INERT -> Draw.AMBER;
            case LOCKED -> Draw.TEXT_FAINT;
            default -> Draw.TEXT_DIM;
        };
    }

    // ---------------------------------------------- faces x 232..300

    private void faces(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        WorkbaySnapshot.Bay bay = snap.bay(snap.selectedBay());

        // Three 20x18 type buttons. The block shows one resource type at a time, which is why a
        // face can take items in and send energy out without the picture contradicting itself.
        for (int i = 0; i < 3; i++) {
            BusConfig.Resource resource = BusConfig.Resource.values()[i];
            int px = x(WELL_X + i * TYPE_PITCH);
            int py = y(52);
            boolean active = faceType == resource;
            boolean hover = screen.hovered(px, py, TYPE_W, 18, mouseX, mouseY);
            Draw.button(g, px, py, TYPE_W, 18, hover, active);
            resourceIcon(g, resource, px + (TYPE_W - 12) / 2, py + 3, 0.75F);
            screen.hit(px, py, TYPE_W, 18, () -> faceType = resource,
                WorkbayScreen.gui("faces." + resource.getSerializedName()),
                WorkbayScreen.gui("faces.tip"));
        }

        Draw.well(g, x(WELL_X), y(WELL_Y), WELL_W, WELL_H);

        // The machine's own block, at whatever angle the player has dragged it to. Not a drawn
        // cube: somebody configuring an Enrichment Chamber's faces needs to see one.
        BlockState state = blockFor(bay.hosted());
        String subject = bay.index() + ":" + bay.hosted().map(ResourceLocation::toString).orElse("");
        if (!subject.equals(previewSubject)) {
            previewSubject = subject;
            PREVIEW.reset();
        }
        if (state == null) {
            // Wrapped, and started low enough that two lines sit in the middle of the well
            // rather than pinned to its top edge with an empty half underneath.
            wrapped(g, WorkbayScreen.gui("faces.empty"), x(WELL_X + 4), y(WELL_Y + 20),
                WELL_W - 8, Draw.TEXT_FAINT);
        } else {
            // A face marker projects to wherever its face's centre lands on screen, which at a
            // steep enough drag angle is genuinely outside the well - the marker is correct, the
            // well is just not wide enough to contain every angle. Scissored to the well's own
            // rectangle so a marker (or letter) never spills onto the panel around it, reported
            // from play as text sticking out of the cube.
            g.enableScissor(x(WELL_X), y(WELL_Y), x(WELL_X + WELL_W), y(WELL_Y + WELL_H));
            PREVIEW.render(g, state, x(CUBE_CX), y(CUBE_CY), CUBE_SIZE);
            PREVIEW.renderFaces(g, screen.font(), x(CUBE_CX), y(CUBE_CY), CUBE_SIZE,
                bay.faces(), faceType);
            g.disableScissor();
        }

        // Under the well: the turn hint, then the in/out key, both inside the well's own width and
        // both starting from its own edges. They used to sit on hardcoded x's that were neither.
        textCentre(g, WorkbayScreen.gui("faces.drag").getString(), x(WELL_X + WELL_W / 2),
            y(WELL_Y + WELL_H + 4), WELL_W, Draw.TEXT_FAINT);

        int keyY = y(WELL_Y + WELL_H + 18);
        int half = WELL_W / 2;
        g.fill(x(WELL_X), keyY + 1, x(WELL_X + 6), keyY + 7, Draw.GREEN);
        text(g, "in", x(WELL_X + 10), keyY, half - 12, Draw.TEXT_DIM);
        g.fill(x(WELL_X + half), keyY + 1, x(WELL_X + half + 6), keyY + 7, Draw.BLUE);
        text(g, "out", x(WELL_X + half + 10), keyY, half - 12, Draw.TEXT_DIM);
    }

    private boolean inWell(double mx, double my) {
        return mx >= x(WELL_X) && mx < x(WELL_X + WELL_W)
            && my >= y(WELL_Y) && my < y(WELL_Y + WELL_H);
    }

    @Override
    boolean mousePressed(double mouseX, double mouseY, int button) {
        if (button != 0 || !inWell(mouseX, mouseY)) {
            return false;
        }
        PREVIEW.press();
        return true;
    }

    @Override
    boolean mouseDragged(double dragX, double dragY) {
        PREVIEW.drag(dragX, dragY);
        return false;
    }

    /** A press that never turned into a turn is a click on whichever face it landed on. */
    @Override
    boolean mouseReleased(double mouseX, double mouseY) {
        if (!PREVIEW.release() || !inWell(mouseX, mouseY)) {
            return false;
        }
        Direction face = PREVIEW.faceAt(mouseX, mouseY, x(CUBE_CX), y(CUBE_CY), CUBE_SIZE);
        if (face != null) {
            screen.send(WorkbayAction.CYCLE_FACE, faceType.ordinal() | (face.ordinal() << 4));
        }
        return true;
    }

    /**
     * The block to draw, turned to face the camera. A machine's default state is not reliably the
     * one whose front points north — some mods default south — and the preview has to open showing
     * the front of the thing, every time, or the player is configuring the back of a machine they
     * cannot identify.
     */
    @org.jetbrains.annotations.Nullable
    private static BlockState blockFor(Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.BLOCK::get)
            .filter(block -> block != net.minecraft.world.level.block.Blocks.AIR)
            .map(net.minecraft.world.level.block.Block::defaultBlockState)
            .map(BlockPreview::facingCamera)
            .orElse(null);
    }

    // ------------------------------------------------------------- the list

    private void links(GuiGraphics g, int mouseX, int mouseY) {
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        int pairX = x(LIST_X + LIST_W - 46);
        int addX = pairX - 40;

        if (adding) {
            text(g, WorkbayScreen.gui("links.adding", snap.selectedBay() + 1),
                x(LIST_X + 4), y(linksY + 4), 48, Draw.TEXT);
            // Laid out left to right with the widths written down, because the first version put
            // the Bays tab and Back on top of each other: heading to LIST_X+52, two 44-wide tabs,
            // then Back, then the confirm where Pair sits on the normal list.
            tab(g, mouseX, mouseY, x(LIST_X + 56), AddTab.CONNECTORS, "Links");
            tab(g, mouseX, mouseY, x(LIST_X + 102), AddTab.BAYS, "Bays");

            // Confirm, where Pair sits on the normal list: the count is the whole point of the
            // checkboxes, so it is on the button rather than anywhere the eye has to hunt for it.
            int picked = pickedLinks.size() + pickedBays.size();
            boolean confirmHover = screen.hovered(pairX, y(linksY), 46, 18, mouseX, mouseY);
            Draw.button(g, pairX, y(linksY), 46, 18, confirmHover, false);
            textCentre(g, picked == 0 ? "Add" : "Add " + picked, pairX + 23, y(linksY + 5), 42,
                picked == 0 ? Draw.TEXT_FAINT : Draw.TEXT);
            screen.hit(pairX, y(linksY), 46, 18, this::applyPicked,
                WorkbayScreen.gui("links.add.apply", picked),
                WorkbayScreen.gui("links.add.apply.tip"));

            // No icon beside the word: "Back" is 22px and the icon another 12, which did not fit
            // the 36 the button had and spilled over its right edge.
            int backX = x(LIST_X + 160);
            boolean backHover = screen.hovered(backX, y(linksY), 40, 18, mouseX, mouseY);
            Draw.button(g, backX, y(linksY), 40, 18, backHover, false);
            textCentre(g, "Back", backX + 20, y(linksY + 5), 36, Draw.TEXT);
            screen.hit(backX, y(linksY), 40, 18, this::closePicker,
                WorkbayScreen.gui("links.add.close"), WorkbayScreen.gui("links.add.tip"));

            candidates(g, mouseX, mouseY, snap);
            return;
        }

        // Whose links these are. The list is per bay, so a heading that does not say which bay is
        // the one thing that can make the whole screen lie to you.
        text(g, filter == Filter.THIS_BAY ? "LINKS · BAY " + (snap.selectedBay() + 1) : "LINKS",
            x(LIST_X + 4), y(linksY + 4), 70, Draw.TEXT);

        // Clear of the heading, which is no longer the fixed-width word "LINKS": it now carries the
        // bay number, and at LIST_X+44 the funnel sat on top of it.
        iconButton(g, mouseX, mouseY, x(LIST_X + 76), y(linksY), WBIcons.FILTER, true,
            () -> filter = Filter.values()[Math.floorMod(
                filter.ordinal() + (screen.back() ? -1 : 1), Filter.values().length)],
            WorkbayScreen.gui("links.filter." + filter.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.filter.tip"));
        iconButton(g, mouseX, mouseY, x(LIST_X + 98), y(linksY), WBIcons.SORT, true,
            () -> sort = Sort.values()[Math.floorMod(
                sort.ordinal() + (screen.back() ? -1 : 1), Sort.values().length)],
            WorkbayScreen.gui("links.sort." + sort.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.sort.tip"));

        // The skim, at rest, directly above the rows it takes from. SPEC.md §3: goods going missing
        // must be explained where the loss is noticed, and the loss is noticed on these rows. It is
        // read-only here — the dial itself lives on the upgrades screen, beside the Levy it buys.
        boolean assay = hasAssay(snap);
        // The short form on the line, the sentence in the tooltip: "No Assay racked" is 76 pixels
        // in a slot 62 wide however the header is packed, so it arrived as "No Ass...".
        String rate = assay || snap.skimRate() == 0
            ? WorkbayScreen.gui("skim", snap.skimRate()).getString()
            : WorkbayScreen.gui("skim.no_assay.short").getString();
        text(g, rate, x(LIST_X + 120), y(linksY + 5), SKIM_W,
            skimming(snap) ? Draw.AMBER : Draw.TEXT_FAINT);
        screen.hit(x(LIST_X + 118), y(linksY + 2), Math.min(SKIM_W, font.width(rate)) + 4, 14, () -> { },
            assay || snap.skimRate() == 0
                ? WorkbayScreen.gui("skim.name", snap.skimRate())
                : WorkbayScreen.gui("skim.no_assay"),
            assay || snap.skimRate() == 0
                ? WorkbayScreen.gui("skim.tip") : WorkbayScreen.gui("skim.no_assay.tip"));

        boolean pairHover = screen.hovered(pairX, y(linksY), 46, 18, mouseX, mouseY);
        Draw.button(g, pairX, y(linksY), 46, 18, pairHover, false);
        // The Connector's own item, because the button only does anything while you are holding
        // one and a plus sign does not say that.
        g.renderItem(new ItemStack(com.neryos.workbay.init.WBBlocks.CONNECTOR.get()),
            pairX + 1, y(linksY + 1));
        text(g, "Pair", pairX + 20, y(linksY + 5), 22, Draw.TEXT);
        screen.hit(pairX, y(linksY), 46, 18, () -> screen.send(WorkbayAction.PAIR),
            WorkbayScreen.gui("links.pair"), WorkbayScreen.gui("links.pair.tip"));

        boolean addHover = screen.hovered(addX, y(linksY), 36, 18, mouseX, mouseY);
        Draw.button(g, addX, y(linksY), 36, 18, addHover, false);
        WBIcons.draw(g, WBIcons.PLUS, addX + 3, y(linksY + 3), Draw.TEXT);
        text(g, "Add", addX + 15, y(linksY + 5), 18, Draw.TEXT);
        screen.hit(addX, y(linksY), 36, 18, () -> {
            adding = true;
            scroll = 0;
        }, WorkbayScreen.gui("links.add"), WorkbayScreen.gui("links.add.tip"));

        List<WorkbaySnapshot.Link> visible = visibleLinks(snap);
        Draw.well(g, x(LIST_X), y(rowY - 4), LIST_W, rows * ROW_PITCH + 8);
        // Empty rows are drawn as empty rows. SPEC.md §7: the alternative is growing the panel to
        // fit the list, which moves every control under the player's cursor as links are added.
        for (int emptyRow = 0; emptyRow < rows; emptyRow++) {
            int ruleY = y(rowY + emptyRow * ROW_PITCH) + ROW_PITCH - 2;
            g.fill(x(LIST_X + 4), ruleY, x(LIST_X + LIST_W - 10), ruleY + 1, 0x12FFFFFF);
        }

        if (visible.isEmpty()) {
            // "No links yet. Pair a Connector" is a lie the moment the list is scoped to one bay
            // and the links are all on another. Say which case this is.
            int elsewhere = snap.links().size();
            Component empty = filter == Filter.THIS_BAY && elsewhere > 0
                ? WorkbayScreen.gui("links.none.here", elsewhere)
                : WorkbayScreen.gui("links.none");
            // Wrapped, not cut: this is the one line on the screen whose whole job is to explain
            // an empty list, and half of that sentence explains nothing. It is the sentence that
            // ran off the right-hand edge of the list and out over the world behind the window.
            wrapped(g, empty, x(LIST_X + 8), y(rowY + 6), LIST_W - 20, Draw.TEXT_FAINT);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, visible.size() - rows));
        scrollbar(g, visible.size());
        for (int visibleRow = 0; visibleRow < rows && visibleRow + scroll < visible.size(); visibleRow++) {
            row(g, mouseX, mouseY, visible.get(visibleRow + scroll), y(rowY + visibleRow * ROW_PITCH));
        }
    }

    /**
     * One row. Per SPEC.md §4: status swatch, resource icon, direction icon, name, target or status,
     * filter slot, gear — and <b>remove lives inside the gear</b>, because two controls per row is
     * the ceiling and a delete button on every row is how somebody deletes the wrong one.
     */
    private void row(GuiGraphics g, int mouseX, int mouseY, WorkbaySnapshot.Link link, int py) {
        var font = screen.font();
        BusConfig config = link.config();
        int px = x(LIST_X + 4);
        boolean rowHover = screen.hovered(px, py, LIST_W - 14, ROW_PITCH - 2, mouseX, mouseY);
        if (rowHover) {
            g.fill(px, py, px + LIST_W - 14, py + ROW_PITCH - 2, 0x18FFFFFF);
            // And outline the block it points at, out in the world. SPEC.md §7.
            com.neryos.workbay.client.LinkHighlight.set(config.target());
        }

        // Broken gets a red bar in the well's own left gutter, where nothing else lives. The
        // header can say "1 problem" and every row still look fine otherwise: the status swatch
        // alone is a 10px chip that a player scanning thirty rows does not see, and a disabled row
        // is dim, which is what "broken" used to look like too. Dim is the player's own doing;
        // red is the mod's.
        if (link.status().isProblem()) {
            g.fill(px - 3, py, px - 1, py + ROW_PITCH - 2, Draw.RED);
        }

        // On and off are one click on the row, not two clicks through a menu. A power symbol, not
        // a tick: the picker's tick means "selected for adding", and one control that means two
        // things is a control that means neither.
        boolean on = config.enabled();
        Draw.slot(g, px, py + 3, 12, 12);
        WBIcons.draw(g, WBIcons.POWER, px, py + 3, on ? Draw.GREEN : Draw.TEXT_FAINT);
        screen.hit(px, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_TOGGLE_ENABLED, config.id()),
            WorkbayScreen.gui(on ? "links.disable" : "links.enable"),
            WorkbayScreen.gui(on ? "links.disable.tip" : "links.enable.tip"));

        g.fill(px + 16, py + 4, px + 26, py + 14, Draw.EDGE_DARK);
        g.fill(px + 17, py + 5, px + 25, py + 13, statusColour(link.status()));
        screen.hit(px + 16, py + 4, 10, 10, () -> { },
            statusName(link.status()), statusHelp(link.status()));

        resourceIcon(g, config.resource(), px + 30, py + 3, 0.75F);
        screen.hit(px + 30, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_CYCLE_RESOURCE, config.id()),
            WorkbayScreen.gui("links.type." + config.resource().getSerializedName()),
            WorkbayScreen.gui("links.type.tip"));

        // A plain arrow, pointing the way the resource travels: the machine is this row's left and
        // the target its right, so insert points right and extract points left.
        boolean insert = config.mode() == BusConfig.Mode.INSERT;
        WBIcons.draw(g, insert ? WBIcons.ARROW_RIGHT : WBIcons.ARROW_LEFT, px + 46, py + 3,
            insert ? Draw.BLUE : Draw.GREEN);
        screen.hit(px + 46, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_FLIP_MODE, config.id()),
            WorkbayScreen.gui("links.mode." + config.mode().getSerializedName()),
            WorkbayScreen.gui("links.mode.tip"));

        // Which bay, because the list shows every bay's links by default now.
        String badge = "B" + (config.bay() + 1);
        text(g, badge, px + 62, py + 5, 16, Draw.TEXT_DIM);
        screen.hit(px + 62, py + 3, 16, 12, () -> { },
            WorkbayScreen.gui("links.bay", config.bay() + 1), WorkbayScreen.gui("links.bay.tip"));

        // The name, and where it comes from when the player has not given one: the bay an internal
        // link points at, or the target block's own name. Four rows all called "Bay link" was the
        // whole list unreadable at a glance.
        String label = link.label()
            .orElseGet(() -> link.targetBlock().map(BaysPage::displayName)
                .orElse(WorkbayScreen.gui("links.unknown")).getString());

        // And what this row is losing, on this row. A tax the player only finds by opening another
        // screen is the best bug-report generator in the mod (SPEC.md §3). Only item links: fluids
        // and energy are never skimmed.
        //
        // Just the number. "25% skimmed" is sixty pixels on a row that has thirty to spare: drawn
        // right-aligned it ran back over the bay badge and squeezed the link's own name out
        // entirely, so the row read "B225% skimmed Chest". Found in play, on the first row the
        // feature ever drew. The sentence lives in the tooltip, which is where this mod's text
        // budget is spent anyway (SPEC.md §4).
        boolean taxed = skimming(snapshot()) && config.resource() == BusConfig.Resource.ITEM;
        String cut = taxed
            ? WorkbayScreen.gui("skim.row", snapshot().skimRate()).getString() : "";
        int cutW = taxed ? Math.min(font.width(cut), 22) : 0;
        if (taxed) {
            textRight(g, cut, px + 132, py + 5, cutW, Draw.AMBER);
            screen.hit(px + 132 - cutW, py + 3, cutW, 12, () -> { },
                WorkbayScreen.gui("skim.name", snapshot().skimRate()),
                WorkbayScreen.gui("skim.row.tip"));
        }
        text(g, label, px + 80, py + 5, taxed ? 48 - cutW : 50,
            on ? Draw.TEXT : Draw.TEXT_FAINT);

        // The right-hand column is the status word whenever there is one, for every kind of link.
        // An internal row used to print "→ Bay 2" in amber and nothing else, so the one broken link
        // the header was counting never said what was wrong with it anywhere on screen.
        boolean broken = link.status().isProblem();
        if (config.internal()) {
            // Bay to bay: the target is a bay number, not a block, and — unlike every other link —
            // there is nothing in the world to re-anchor it to, so this is the one target a player
            // may actually change from the row.
            String bayTarget = broken ? statusShort(link.status()).getString()
                : link.targetBay().map(b -> "→ Bay " + (b + 1))
                    .orElse(WorkbayScreen.gui("links.unknown").getString());
            text(g, bayTarget, px + 136, py + 5, 60,
                broken ? statusColour(link.status()) : Draw.BLUE);
            screen.hit(px + 136, py + 2, 60, ROW_PITCH - 4,
                () -> screen.send(WorkbayAction.LINK_CYCLE_TARGET_BAY, config.id()),
                broken ? statusName(link.status()) : WorkbayScreen.gui("links.internal.retarget"),
                broken ? statusHelp(link.status())
                    : WorkbayScreen.gui("links.internal.retarget.tip"));
        } else {
            Component target = broken
                ? statusShort(link.status())
                : link.targetBlock().map(BaysPage::displayName).orElse(WorkbayScreen.gui("links.unknown"));
            text(g, target, px + 136, py + 5, 60,
                broken ? statusColour(link.status()) : Draw.TEXT_DIM);
        }

        faceButton(g, mouseX, mouseY, px + 200, py + 3, config);
        filterSlot(g, px + 216, py + 1, config);

        WBIcons.draw(g, WBIcons.CROSS, px + 238, py + 3,
            screen.hovered(px + 238, py + 3, 12, 12, mouseX, mouseY) ? Draw.RED : Draw.TEXT_FAINT);
        screen.hit(px + 238, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_REMOVE, config.id()),
            WorkbayScreen.gui("links.remove"), WorkbayScreen.gui("links.remove.tip"));
    }

    /**
     * Which face of the target block this link reaches into.
     *
     * <p>The runner has honoured a pinned face since buses existed; nothing ever let a player set
     * one. A machine with a separate input and output face is unusable without it — the link takes
     * whichever face answers first, which is the wrong one about half the time.
     *
     * <p>Compass letters here, unlike on the preview cube: the target is a block out in the world
     * standing at an orientation the mod did not choose, so "north side of it" is the only thing
     * that means anything to somebody looking at their own base.
     */
    private void faceButton(GuiGraphics g, int mouseX, int mouseY, int px, int py,
        BusConfig config) {
        Optional<net.minecraft.core.Direction> face = config.targetFace();
        String letter = face
            .map(d -> String.valueOf(Character.toUpperCase(d.getName().charAt(0))))
            .orElse("-");
        boolean hover = screen.hovered(px, py, 12, 12, mouseX, mouseY);
        Draw.slot(g, px, py, 12, 12);
        textCentre(g, letter, px + 6, py + 2, 10,
            face.isPresent() ? (hover ? Draw.TEXT : Draw.AMBER) : Draw.TEXT_FAINT);
        screen.hit(px, py, 12, 12,
            () -> screen.send(WorkbayAction.LINK_CYCLE_TARGET_FACE, config.id()),
            face.map(d -> WorkbayScreen.gui("links.face." + d.getSerializedName()))
                .orElse(WorkbayScreen.gui("links.face.any")),
            WorkbayScreen.gui("links.face.tip"));
    }

    /**
     * The row's ghost slot. One item, dragged in from JEI or EMI or picked off the cursor, and
     * clicked to clear. SPEC.md §5's filter <em>items</em> hold nine to thirty-six entries and know
     * about components; this is the one-item form the row has always drawn a slot for.
     *
     * <p>The item is washed white at pose Z+300 so it never reads as a real stack sitting in a
     * slot, which is the anti-dupe convention §5 requires of every ghost slot in the mod.
     */
    private void filterSlot(GuiGraphics g, int px, int py, BusConfig config) {
        Draw.slot(g, px, py, 16, 16);
        Optional<ResourceLocation> filter = config.filter();
        if (filter.isEmpty()) {
            WBIcons.draw(g, WBIcons.FILTER, px + 2, py + 2, Draw.TEXT_FAINT);
            screen.hit(px, py, 16, 16, () -> setFilterFromCursor(config),
                WorkbayScreen.gui("links.filterslot"), WorkbayScreen.gui("links.filterslot.tip"));
        } else {
            ItemStack ghost = new ItemStack(BuiltInRegistries.ITEM.get(filter.get()));
            g.renderItem(ghost, px, py);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(px, py, px + 16, py + 16, 0x60FFFFFF);
            g.pose().popPose();
            screen.hit(px, py, 16, 16,
                () -> screen.send(WorkbayAction.SET_FILTER, -1L, config.id()),
                WorkbayScreen.gui("links.filterslot.set", ghost.getHoverName()),
                WorkbayScreen.gui("links.filterslot.clear"));
        }
        screen.ghost(px, py, 16, 16, dropped -> setFilter(config, dropped));
    }

    /** Clicking an empty slot while holding something is the no-recipe-viewer way in. */
    private void setFilterFromCursor(BusConfig config) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            setFilter(config, minecraft.player.getMainHandItem());
        }
    }

    private void setFilter(BusConfig config, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        screen.send(WorkbayAction.SET_FILTER,
            BuiltInRegistries.ITEM.getId(stack.getItem()), config.id());
    }

    /** One of the picker's two tabs, drawn as a button that stays pressed while it is the one shown. */
    private void tab(GuiGraphics g, int mouseX, int mouseY, int px, AddTab which, String label) {
        boolean active = addTab == which;
        boolean hover = screen.hovered(px, y(linksY), 44, 18, mouseX, mouseY);
        Draw.button(g, px, y(linksY), 44, 18, hover, active);
        textCentre(g, label, px + 22, y(linksY + 5), 40, active ? Draw.TEXT : Draw.TEXT_DIM);
        screen.hit(px, y(linksY), 44, 18, () -> {
            addTab = which;
            scroll = 0;
        }, WorkbayScreen.gui("links.add.tab." + which.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.add.tab.tip"));
    }

    private void closePicker() {
        adding = false;
        pickedLinks.clear();
        pickedBays.clear();
        scroll = 0;
    }

    /**
     * Attaches everything that is ticked, then leaves the picker.
     *
     * <p>One action per pick rather than one action carrying a list. The two are different actions
     * on the server -- handing over an existing link, and minting a new internal one -- and each
     * already validates its own arguments, so a batching packet would buy nothing but a second
     * place for the same rules to be written down.
     */
    private void applyPicked() {
        int bay = snapshot().selectedBay();
        pickedLinks.forEach(id -> screen.send(WorkbayAction.LINK_ASSIGN_BAY, bay, id));
        pickedBays.forEach(target -> screen.send(WorkbayAction.CREATE_INTERNAL_LINK, target));
        closePicker();
    }

    /**
     * What the selected bay could be attached to: on the Links tab every link currently held by
     * another bay, and on the Bays tab every other bay, for a link that needs no Connector.
     *
     * <p>Reassigning rather than creating is deliberate. A Connector is the link (SPEC.md §0), so a
     * Connector already standing in the world is not a link waiting to be made -- it is a link
     * belonging to the wrong bay, and the fix is to hand it over, not to make a second one.
     */
    private void candidates(GuiGraphics g, int mouseX, int mouseY, WorkbaySnapshot snap) {
        int selected = snap.selectedBay();

        List<WorkbaySnapshot.Link> loose = snap.links().stream()
            .filter(link -> link.config().bay() != selected)
            .toList();
        // Bays that exist and are not this one. A bay with no machine is still worth offering: the
        // link outlives the machine, and racking one later is the normal order of work.
        List<Integer> bays = java.util.stream.IntStream.range(0, snap.bayCapacity())
            .filter(bay -> bay != selected)
            .boxed()
            .toList();

        Draw.well(g, x(LIST_X), y(rowY - 4), LIST_W, rows * ROW_PITCH + 8);
        int total = addTab == AddTab.CONNECTORS ? loose.size() : bays.size();
        if (total == 0) {
            wrapped(g, WorkbayScreen.gui(addTab == AddTab.CONNECTORS
                    ? "links.add.none.links" : "links.add.none.bays"),
                x(LIST_X + 8), y(rowY + 6), LIST_W - 20, Draw.TEXT_FAINT);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, total - rows));
        scrollbar(g, total);

        for (int visibleRow = 0; visibleRow < rows && visibleRow + scroll < total; visibleRow++) {
            int index = visibleRow + scroll;
            int py = y(rowY + visibleRow * ROW_PITCH);
            int px = x(LIST_X + 4);
            boolean hover = screen.hovered(px, py, LIST_W - 14, ROW_PITCH - 2, mouseX, mouseY);
            if (hover) {
                g.fill(px, py, px + LIST_W - 14, py + ROW_PITCH - 2, 0x18FFFFFF);
            }

            if (addTab == AddTab.CONNECTORS) {
                WorkbaySnapshot.Link link = loose.get(index);
                BusConfig config = link.config();
                boolean ticked = pickedLinks.contains(config.id());
                if (hover) {
                    com.neryos.workbay.client.LinkHighlight.set(config.target());
                }
                checkbox(g, px, py + 3, ticked);
                resourceIcon(g, config.resource(), px + 18, py + 3, 0.75F);
                String label = link.label()
                    .orElseGet(() -> link.targetBlock().map(BaysPage::displayName)
                        .orElse(WorkbayScreen.gui("links.unknown")).getString());
                text(g, label, px + 34, py + 5, 70, ticked ? Draw.TEXT : Draw.TEXT_DIM);
                // An internal link's target is a machine in the Backshop, which this client has
                // never loaded, so asking for the block there gets air. Name the bay instead --
                // the same branch the row itself makes.
                String from = config.internal()
                    ? link.targetBay().map(b -> "\u2192 Bay " + (b + 1))
                        .orElse(WorkbayScreen.gui("links.unknown").getString())
                    : link.targetBlock().map(BaysPage::displayName)
                        .orElse(WorkbayScreen.gui("links.unknown")).getString();
                text(g, from, px + 110, py + 5, 90,
                    config.internal() ? Draw.BLUE : Draw.TEXT_DIM);
                text(g, "B" + (config.bay() + 1), px + 206, py + 5, 20, Draw.TEXT_FAINT);
                screen.hit(px, py, LIST_W - 14, ROW_PITCH - 2, () -> {
                    if (!pickedLinks.remove(config.id())) {
                        pickedLinks.add(config.id());
                    }
                }, WorkbayScreen.gui("links.add.link", label, config.bay() + 1),
                    WorkbayScreen.gui("links.add.link.tip", selected + 1));
            } else {
                int bay = bays.get(index);
                boolean ticked = pickedBays.contains(bay);
                WorkbaySnapshot.Bay other = snap.bays().size() > bay ? snap.bays().get(bay) : null;
                checkbox(g, px, py + 3, ticked);
                WBIcons.draw(g, WBIcons.ARROW_RIGHT, px + 18, py + 3, Draw.BLUE);
                String label = "Bay " + (bay + 1)
                    + (other == null || other.name().isEmpty() ? "" : " \u00b7 " + other.name());
                text(g, label, px + 34, py + 5, 120, ticked ? Draw.TEXT : Draw.TEXT_DIM);
                text(g, WorkbayScreen.gui("links.add.nowire"), px + 160, py + 5, 90,
                    Draw.TEXT_FAINT);
                screen.hit(px, py, LIST_W - 14, ROW_PITCH - 2, () -> {
                    if (!pickedBays.remove(Integer.valueOf(bay))) {
                        pickedBays.add(bay);
                    }
                }, WorkbayScreen.gui("links.add.bay", bay + 1),
                    WorkbayScreen.gui("links.add.bay.tip"));
            }
        }
    }

    /**
     * The list's scrollbar, drawn only when the list actually scrolls.
     *
     * <p>Rows stop at {@code LIST_X + LIST_W - 10}, so the track lives in the ten pixels the well
     * already leaves at its right edge and nothing has to move to make room. Without it the only
     * clue that a list continued past the last visible row was the wheel doing something.
     */
    private void scrollbar(GuiGraphics g, int total) {
        if (total <= rows) {
            return;
        }
        int trackX = x(LIST_X + LIST_W - 9);
        int trackY = y(rowY - 2);
        int trackH = rows * ROW_PITCH + 4;
        g.fill(trackX, trackY, trackX + 5, trackY + trackH, Draw.EDGE_DARK);

        // At least six pixels tall: a thumb proportional to a thirty-row list is two pixels and
        // reads as a speck of dirt on the screen rather than as a control.
        int thumbH = Math.max(6, trackH * rows / total);
        int travel = trackH - thumbH;
        int thumbY = trackY + (total == rows ? 0 : travel * scroll / (total - rows));
        g.fill(trackX + 1, thumbY, trackX + 4, thumbY + thumbH, Draw.TEXT_FAINT);
    }

    /**
     * The picker's tick, and the only tick in the mod: it means <b>selected for adding</b>. The
     * list's rows carry a power symbol instead, because one control cannot mean "ticked to attach"
     * on one list and "switched on" on the other.
     */
    private void checkbox(GuiGraphics g, int px, int py, boolean ticked) {
        Draw.slot(g, px, py, 12, 12);
        if (ticked) {
            WBIcons.draw(g, WBIcons.CHECK, px, py, Draw.GREEN);
        }
    }

    private List<WorkbaySnapshot.Link> visibleLinks(WorkbaySnapshot snap) {
        // ADDED has no comparator on purpose: the snapshot arrives in the order the links were
        // made, and re-sorting it is what makes rows move under the player's cursor.
        Comparator<WorkbaySnapshot.Link> order = switch (sort) {
            case ADDED -> null;
            case BAY -> Comparator.comparingInt(link -> link.config().bay());
            case TYPE -> Comparator.comparing(link -> link.config().resource());
            // Problems first: the default, because the screen's job at rest is to surface them.
            case STATUS -> Comparator.comparing((WorkbaySnapshot.Link link) -> !link.status().isProblem())
                .thenComparing(link -> link.config().bay());
        };
        var kept = snap.links().stream()
            .filter(link -> switch (filter) {
                case THIS_BAY -> link.config().bay() == snap.selectedBay();
                case ALL_BAYS -> true;
                case PROBLEMS -> link.status().isProblem();
            });
        return (order == null ? kept : kept.sorted(order)).toList();
    }

    @Override
    boolean scrolled(double mouseX, double mouseY, double delta) {
        if (mouseY < y(rowY - 4) || mouseY > y(rowY + rows * ROW_PITCH + 4)) {
            return false;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    /**
     * The three failing statuses are never collapsed into one another (SPEC.md §4): a target that is
     * gone, a target whose chunk is not loaded, and a target with nothing to connect to are three
     * different things to do next, and one amber blob for all three tells the player none of them.
     */
    private static int statusColour(BusRunner.BusStatus status) {
        return switch (status) {
            case RUNNING -> Draw.GREEN;
            case IDLE -> Draw.BLUE;
            case DISABLED, HELD_BY_REDSTONE -> Draw.GREY;
            case TARGET_MISSING, CONNECTOR_GONE -> Draw.RED;
            // Amber is "you can fix this from here". The face config and an unreachable machine
            // both are; a target that has gone is not.
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE -> Draw.AMBER;
        };
    }

    /**
     * The row's form of a status, two words at most. The long one is the tooltip's title and does
     * not fit a 60-pixel column: "No face for this" arrived on screen as "No face f", which reads
     * as a rendering fault rather than as a fault in the link. Same split as {@code bay.short.*}.
     */
    private static Component statusShort(BusRunner.BusStatus status) {
        return WorkbayScreen.gui("status.short." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component statusName(BusRunner.BusStatus status) {
        return WorkbayScreen.gui("status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component statusHelp(BusRunner.BusStatus status) {
        return WorkbayScreen.gui("status." + status.name().toLowerCase(java.util.Locale.ROOT) + ".tip");
    }

    /**
     * Whether an Assay is racked anywhere in this network. Read off the bay list rather than sent
     * as a flag: the snapshot already carries what is in every bay, and a second field saying the
     * same thing is a second field that can disagree with the first.
     */
    private static boolean hasAssay(WorkbaySnapshot snap) {
        return snap.bays().stream().anyMatch(bay ->
            bay.hosted().filter(com.neryos.workbay.init.WBBlocks.ASSAY.getId()::equals).isPresent());
    }

    /** A rate with no Assay behind it takes nothing, so no row may claim it is losing anything. */
    private static boolean skimming(WorkbaySnapshot snap) {
        return snap.skimRate() > 0 && hasAssay(snap);
    }

    private static ItemStack iconFor(Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.ITEM::get).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** A bay shows the name the player gave it, or the machine's, or just its number. */
    private static String nameOf(WorkbaySnapshot.Bay bay, int selected) {
        if (!bay.name().isEmpty()) {
            return bay.name();
        }
        return bay.hosted().map(BaysPage::displayName).map(Component::getString)
            .orElseGet(() -> WorkbayScreen.gui("bay.n", selected + 1).getString());
    }

    private static Component displayName(ResourceLocation id) {
        var item = BuiltInRegistries.ITEM.get(id);
        if (item != net.minecraft.world.item.Items.AIR) {
            return item.getDescription();
        }
        var block = BuiltInRegistries.BLOCK.get(id);
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
            return block.getName();
        }
        // Air is not a block the player put there — it is this client not having the target, which
        // happens for anything in the Backshop and for a chunk nobody has loaded. Printing
        // "minecraft:air" tells them the mod is broken; the raw id is still worth showing for a
        // modded block whose registration this client really is missing.
        return id.equals(ResourceLocation.withDefaultNamespace("air"))
            ? WorkbayScreen.gui("links.unknown")
            : Component.literal(id.toString());
    }
}
