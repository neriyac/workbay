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

    private static final int CUBE_CX = 266;
    private static final int CUBE_CY = 106;
    private static final int CUBE_SIZE = 30;
    private static final int FACES_X = 232;
    private static final int WELL_X = 232;
    private static final int WELL_Y = 76;
    private static final int WELL_W = 68;
    private static final int WELL_H = 62;

    /** Client-side view state: the list's filter, sort, scroll and which row's gear is open. */
    private enum Filter { THIS_BAY, ALL_BAYS, PROBLEMS }

    private enum Sort { BAY, TYPE, STATUS }

    /**
     * What Copy holds. Static and client-side on purpose: the point of copying a bay is pasting it
     * onto the next seven, and the server is told the whole config in the paste action itself, so
     * there is no second clipboard anywhere to disagree with this one.
     */
    @org.jetbrains.annotations.Nullable
    private static FaceConfig copied;

    private static Filter filter = Filter.THIS_BAY;
    private static Sort sort = Sort.STATUS;
    private static BusConfig.Resource faceType = BusConfig.Resource.ITEM;

    /** Kept between openings, so the angle a player turned a machine to is still there next time. */
    private static final BlockPreview PREVIEW = new BlockPreview();

    private int scroll;
    @org.jetbrains.annotations.Nullable
    private UUID openGear;

    BaysPage(WorkbayScreen screen) {
        super(screen);
        height = Math.clamp(screen.availableHeight() - 8, MIN_HEIGHT, MAX_HEIGHT);
        // Eight bay slots always fit, however short the window is; they lose pitch, not slots.
        rackPitch = Math.clamp((height - RACK_Y - 12) / BayGeometry.MAX_BAYS, 20, 26);
        slot = rackPitch - 2;
        linksY = height - LINKS_Y_FROM_BOTTOM < 170 ? 170 : height - LINKS_Y_FROM_BOTTOM;
        rowY = linksY + 20;
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
        faces(g, mouseX, mouseY);
        links(g, mouseX, mouseY);
    }

    // ------------------------------------------------------ header y 30..44

    private void summary(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        var font = screen.font();
        int used = (int) snap.bays().stream()
            .filter(bay -> bay.hosted().isPresent()).count();

        int textX = x(8);
        g.drawString(font, snap.bays().isEmpty() ? "" : used + " / " + snap.bayCapacity() + " bays",
            textX, y(31), Draw.TEXT_DIM, false);
        g.drawString(font, snap.links().size() + " links", textX + 62, y(31), Draw.TEXT_DIM, false);

        int problems = snap.problems();
        g.drawString(font, problems == 0 ? "no problems" : problems + " problem" + (problems == 1 ? "" : "s"),
            textX + 112, y(31), problems == 0 ? Draw.TEXT_FAINT : Draw.RED, false);

        // A bar with no figure beside it reads as broken, and an empty one reads as broken twice
        // over, so with no capacity at all the words replace the bar entirely (SPEC.md §7).
        boolean powered = snap.energyCapacity() > 0;
        String power = powered
            ? Draw.compact(snap.energy()) + " / " + Draw.compact(snap.energyCapacity())
            : WorkbayScreen.gui("power.none").getString();
        g.drawString(font, power, x(246 - font.width(power)), y(31),
            powered ? Draw.TEXT_DIM : Draw.TEXT_FAINT, false);
        if (powered) {
            Draw.bar(g, x(250), y(29), 44, 9, snap.energy(), snap.energyCapacity(), Draw.AMBER);
            screen.hit(x(250), y(29), 44, 9, () -> { },
                WorkbayScreen.gui("power", Draw.exact(snap.energy()),
                    Draw.exact(snap.energyCapacity())),
                WorkbayScreen.gui("power.tip"));
        }

        g.drawString(font, snap.code(), x(8), y(height - 12), Draw.TEXT_FAINT, false);
        // The separator between the header band and the working area.
        g.fill(x(6), y(44), x(WIDTH - 6), y(45), Draw.EDGE_DARK);
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
        Component name = bay.hosted().map(BaysPage::displayName)
            .orElse(WorkbayScreen.gui("bay.empty"));
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
        g.drawString(font, font.plainSubstrByWidth(empty
                ? WorkbayScreen.gui("bay.n", snap.selectedBay() + 1).getString()
                : displayName(bay.hosted().orElseThrow()).getString(), room),
            x(98), y(56), Draw.TEXT, false);

        if (bay.energyCapacity() > 0) {
            Draw.bar(g, x(98), y(68), 84, 9, bay.energy(), bay.energyCapacity(), Draw.AMBER);
            String power = Draw.compact(bay.energy()) + " / " + Draw.compact(bay.energyCapacity());
            g.drawString(font, power, x(FACES_X - 4 - font.width(power)), y(69), Draw.TEXT_DIM, false);
            screen.hit(x(98), y(68), 84, 9, () -> { },
                WorkbayScreen.gui("power", Draw.exact(bay.energy()),
                    Draw.exact(bay.energyCapacity())),
                WorkbayScreen.gui("power.machine.tip"));
        } else if (!empty) {
            // A machine with no energy handler. The old placeholder was a bare dash floating
            // beside an empty bar, which read as a rendering fault rather than as a fact.
            g.drawString(font, WorkbayScreen.gui("power.none").getString(), x(98), y(69),
                Draw.TEXT_FAINT, false);
        }

        g.drawString(font, font.plainSubstrByWidth(statusLine(bay).getString(), room),
            x(98), y(82), statusColour(bay.state()), false);

        // The button row at y=98, 20x20 on a 24px pitch. Bay View is not here: SPEC.md §4 says a
        // control whose screen is not built is hidden, not drawn faint, because faint is honest for
        // one session and furniture after two.
        boolean canEject = !empty;
        actionButton(g, mouseX, mouseY, x(50), WBIcons.EJECT, canEject,
            () -> screen.send(WorkbayAction.EJECT),
            WorkbayScreen.gui("button.eject"), WorkbayScreen.gui("button.eject.tip"));
        unbuiltButton(g, x(74), y(98), 20, WBIcons.RENAME,
            WorkbayScreen.gui("button.rename"), WorkbayScreen.gui("unbuilt"));
        unbuiltButton(g, x(98), y(98), 20, WBIcons.REDSTONE,
            WorkbayScreen.gui("button.redstone"), WorkbayScreen.gui("unbuilt"));

        // Copy and paste. Eight bays running the same machine is the first complaint this mod will
        // get, and Mekanism answers it with a Configuration Card (SPEC.md §7).
        actionButton(g, mouseX, mouseY, x(122), WBIcons.COPY, true,
            () -> copied = bay.faces(),
            WorkbayScreen.gui("button.copy"), WorkbayScreen.gui("button.copy.tip"));
        actionButton(g, mouseX, mouseY, x(146), WBIcons.PASTE, copied != null,
            () -> screen.send(WorkbayAction.PASTE_BAY, copied.bits()),
            WorkbayScreen.gui("button.paste"),
            WorkbayScreen.gui(copied == null ? "button.paste.empty" : "button.paste.tip"));
    }

    /** A 20x20 button in the machine row: enabled draws lit and clicks, disabled draws sunken. */
    private void actionButton(GuiGraphics g, int mouseX, int mouseY, int px, String[] icon,
        boolean enabled, Runnable onClick, Component name, Component tip) {
        boolean hover = screen.hovered(px, y(98), 20, 20, mouseX, mouseY);
        Draw.button(g, px, y(98), 20, 20, hover && enabled, false, enabled);
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
        String[][] icons = { WBIcons.ITEMS, WBIcons.FLUIDS, WBIcons.ENERGY };
        for (int i = 0; i < 3; i++) {
            BusConfig.Resource resource = BusConfig.Resource.values()[i];
            int px = x(232 + i * 23);
            int py = y(52);
            boolean active = faceType == resource;
            boolean hover = screen.hovered(px, py, 20, 18, mouseX, mouseY);
            Draw.button(g, px, py, 20, 18, hover, active);
            WBIcons.draw(g, icons[i], px + 4, py + 3, active ? Draw.TEXT : Draw.TEXT_DIM);
            screen.hit(px, py, 20, 18, () -> faceType = resource,
                WorkbayScreen.gui("faces." + resource.getSerializedName()),
                WorkbayScreen.gui("faces.tip"));
        }

        Draw.well(g, x(WELL_X), y(WELL_Y), WELL_W, WELL_H);

        // The machine's own block, at whatever angle the player has dragged it to. Not a drawn
        // cube: somebody configuring an Enrichment Chamber's faces needs to see one.
        BlockState state = blockFor(bay.hosted());
        if (state == null) {
            // Wrapped, because the well is 68 wide and this sentence is not.
            var lines = screen.font().split(WorkbayScreen.gui("faces.empty"), WELL_W - 8);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(screen.font(), lines.get(i), x(WELL_X + 4), y(WELL_Y + 8 + i * 10),
                    Draw.TEXT_FAINT, false);
            }
        } else {
            PREVIEW.render(g, state, x(CUBE_CX), y(CUBE_CY), CUBE_SIZE);
            PREVIEW.renderFaces(g, screen.font(), x(CUBE_CX), y(CUBE_CY), CUBE_SIZE,
                bay.faces(), faceType);
        }

        var font = screen.font();
        g.drawString(font, WorkbayScreen.gui("faces.drag").getString(), x(WELL_X), y(WELL_Y + WELL_H + 2),
            Draw.TEXT_FAINT, false);
        g.fill(x(232), y(150), x(238), y(156), Draw.GREEN);
        g.drawString(font, "in", x(240), y(150), Draw.TEXT_DIM, false);
        g.fill(x(266), y(150), x(272), y(156), Draw.BLUE);
        g.drawString(font, "out", x(274), y(150), Draw.TEXT_DIM, false);
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

        g.drawString(font, "LINKS", x(LIST_X + 4), y(linksY + 4), Draw.TEXT, false);

        iconButton(g, mouseX, mouseY, x(LIST_X + 44), y(linksY), WBIcons.FILTER, true,
            () -> filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length],
            WorkbayScreen.gui("links.filter." + filter.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.filter.tip"));
        iconButton(g, mouseX, mouseY, x(LIST_X + 66), y(linksY), WBIcons.SORT, true,
            () -> sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length],
            WorkbayScreen.gui("links.sort." + sort.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.sort.tip"));

        int pairX = x(LIST_X + LIST_W - 46);
        boolean pairHover = screen.hovered(pairX, y(linksY), 46, 18, mouseX, mouseY);
        Draw.button(g, pairX, y(linksY), 46, 18, pairHover, false);
        WBIcons.draw(g, WBIcons.PLUS, pairX + 3, y(linksY + 3), Draw.TEXT);
        g.drawString(font, "Pair", pairX + 17, y(linksY + 5), Draw.TEXT, false);
        screen.hit(pairX, y(linksY), 46, 18, () -> screen.send(WorkbayAction.PAIR),
            WorkbayScreen.gui("links.pair"), WorkbayScreen.gui("links.pair.tip"));

        List<WorkbaySnapshot.Link> visible = visibleLinks(snap);
        Draw.well(g, x(LIST_X), y(rowY - 4), LIST_W, rows * ROW_PITCH + 8);
        // Empty rows are drawn as empty rows. SPEC.md §7: the alternative is growing the panel to
        // fit the list, which moves every control under the player's cursor as links are added.
        for (int emptyRow = 0; emptyRow < rows; emptyRow++) {
            int ruleY = y(rowY + emptyRow * ROW_PITCH) + ROW_PITCH - 2;
            g.fill(x(LIST_X + 4), ruleY, x(LIST_X + LIST_W - 10), ruleY + 1, 0x12FFFFFF);
        }

        if (visible.isEmpty()) {
            g.drawString(font, WorkbayScreen.gui("links.none").getString(),
                x(LIST_X + 8), y(rowY + 8), Draw.TEXT_FAINT, false);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, visible.size() - rows));
        for (int visibleRow = 0; visibleRow < rows && visibleRow + scroll < visible.size(); visibleRow++) {
            row(g, mouseX, mouseY, visible.get(visibleRow + scroll), y(rowY + visibleRow * ROW_PITCH));
        }
        if (visible.size() > rows) {
            scrollbar(g, visible.size());
        }
    }

    private void scrollbar(GuiGraphics g, int total) {
        int trackX = x(LIST_X + LIST_W - 6);
        int trackY = y(rowY);
        int trackH = rows * ROW_PITCH;
        g.fill(trackX, trackY, trackX + 4, trackY + trackH, Draw.EDGE_DARK);
        int knob = Math.max(8, trackH * rows / total);
        int offset = (trackH - knob) * scroll / Math.max(1, total - rows);
        g.fill(trackX, trackY + offset, trackX + 4, trackY + offset + knob, Draw.EDGE_LIGHT);
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

        g.fill(px, py + 4, px + 10, py + 14, Draw.EDGE_DARK);
        g.fill(px + 1, py + 5, px + 9, py + 13, statusColour(link.status()));
        screen.hit(px, py + 4, 10, 10, () -> { },
            statusName(link.status()), statusHelp(link.status()));

        String[] typeIcon = switch (config.resource()) {
            case ITEM -> WBIcons.ITEMS;
            case FLUID -> WBIcons.FLUIDS;
            case ENERGY -> WBIcons.ENERGY;
        };
        WBIcons.draw(g, typeIcon, px + 14, py + 3, Draw.TEXT_DIM);
        screen.hit(px + 14, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_CYCLE_RESOURCE, config.id()),
            WorkbayScreen.gui("links.type." + config.resource().getSerializedName()),
            WorkbayScreen.gui("links.type.tip"));

        WBIcons.draw(g, config.mode() == BusConfig.Mode.INSERT ? WBIcons.INSERT : WBIcons.EXTRACT,
            px + 30, py + 3, Draw.TEXT_DIM);
        screen.hit(px + 30, py + 3, 12, 12,
            () -> screen.send(WorkbayAction.LINK_FLIP_MODE, config.id()),
            WorkbayScreen.gui("links.mode." + config.mode().getSerializedName()),
            WorkbayScreen.gui("links.mode.tip"));

        g.drawString(font, font.plainSubstrByWidth(config.name(), 52), px + 48, py + 5,
            config.enabled() ? Draw.TEXT : Draw.TEXT_FAINT, false);

        Component target = link.status().isProblem()
            ? statusName(link.status())
            : link.targetBlock().map(BaysPage::displayName).orElse(WorkbayScreen.gui("links.unknown"));
        g.drawString(font, font.plainSubstrByWidth(target.getString(), 92), px + 104, py + 5,
            link.status().isProblem() ? statusColour(link.status()) : Draw.TEXT_DIM, false);

        filterSlot(g, px + 200, py + 1, config);

        boolean open = config.id().equals(openGear);
        iconButton(g, mouseX, mouseY, px + 222, py, WBIcons.GEAR, open,
            () -> openGear = open ? null : config.id(),
            WorkbayScreen.gui("links.gear"), WorkbayScreen.gui("links.gear.tip"));

        if (open) {
            gearMenu(g, mouseX, mouseY, config, px + 132, py + ROW_PITCH - 2);
        }
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

    /** The gear's two real controls. Everything else it will hold is SPEC.md §5's link settings. */
    private void gearMenu(GuiGraphics g, int mouseX, int mouseY, BusConfig config, int px, int py) {
        var font = screen.font();
        Draw.panel(g, px, py, 106, 20);
        boolean hoverToggle = screen.hovered(px + 2, py + 2, 50, 16, mouseX, mouseY);
        Draw.button(g, px + 2, py + 2, 50, 16, hoverToggle, !config.enabled());
        g.drawString(font, config.enabled() ? "Disable" : "Enable", px + 6, py + 6,
            Draw.TEXT, false);
        screen.hit(px + 2, py + 2, 50, 16, () -> {
            screen.send(WorkbayAction.LINK_TOGGLE_ENABLED, config.id());
            openGear = null;
        }, WorkbayScreen.gui("links.toggle"), WorkbayScreen.gui("links.toggle.tip"));

        boolean hoverRemove = screen.hovered(px + 54, py + 2, 50, 16, mouseX, mouseY);
        Draw.button(g, px + 54, py + 2, 50, 16, hoverRemove, false);
        g.drawString(font, "Remove", px + 58, py + 6, Draw.RED, false);
        screen.hit(px + 54, py + 2, 50, 16, () -> {
            screen.send(WorkbayAction.LINK_REMOVE, config.id());
            openGear = null;
        }, WorkbayScreen.gui("links.remove"), WorkbayScreen.gui("links.remove.tip"));
    }

    private List<WorkbaySnapshot.Link> visibleLinks(WorkbaySnapshot snap) {
        Comparator<WorkbaySnapshot.Link> order = switch (sort) {
            case BAY -> Comparator.comparingInt(link -> link.config().bay());
            case TYPE -> Comparator.comparing(link -> link.config().resource());
            // Problems first: the default, because the screen's job at rest is to surface them.
            case STATUS -> Comparator.comparing((WorkbaySnapshot.Link link) -> !link.status().isProblem())
                .thenComparing(link -> link.config().bay());
        };
        return snap.links().stream()
            .filter(link -> switch (filter) {
                case THIS_BAY -> link.config().bay() == snap.selectedBay();
                case ALL_BAYS -> true;
                case PROBLEMS -> link.status().isProblem();
            })
            .sorted(order)
            .toList();
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
            case DISABLED -> Draw.GREY;
            case TARGET_MISSING, CONNECTOR_GONE -> Draw.RED;
            // Amber is "you can fix this from here". The face config and an unreachable machine
            // both are; a target that has gone is not.
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE,
                 RESOURCE_NOT_CARRIED -> Draw.AMBER;
        };
    }

    private static Component statusName(BusRunner.BusStatus status) {
        return WorkbayScreen.gui("status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component statusHelp(BusRunner.BusStatus status) {
        return WorkbayScreen.gui("status." + status.name().toLowerCase(java.util.Locale.ROOT) + ".tip");
    }

    private static ItemStack iconFor(Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.ITEM::get).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static Component displayName(ResourceLocation id) {
        var item = BuiltInRegistries.ITEM.get(id);
        if (item != net.minecraft.world.item.Items.AIR) {
            return item.getDescription();
        }
        var block = BuiltInRegistries.BLOCK.get(id);
        return block != net.minecraft.world.level.block.Blocks.AIR
            ? block.getName()
            : Component.literal(id.toString());
    }
}
