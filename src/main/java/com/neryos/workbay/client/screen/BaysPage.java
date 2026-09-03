package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import com.neryos.workbay.world.FaceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
 * <p>Two numbers moved from SPEC.md §4's starting point, and both for the same reason it was
 * rewritten in the first place. The screen is <b>316 tall, not 268</b>: at 268 the LINKS list got
 * ninety pixels, which is three rows, in the same section that requires it to stay readable at
 * thirty. At 316 it gets six and a scrollbar. The rows themselves stayed at the specified 20px
 * pitch — shrinking those to buy rows back would have cost the icons their size.
 */
class BaysPage extends WorkbayPage {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 316;

    private static final int RACK_X = 8;
    private static final int RACK_Y = 50;
    private static final int RACK_PITCH = 26;
    private static final int SLOT = 24;

    private static final int LINKS_Y = 170;
    private static final int ROW_Y = 190;
    private static final int ROW_PITCH = 20;
    private static final int ROWS = 6;
    private static final int LIST_X = 40;
    private static final int LIST_W = 268;

    private static final int CUBE_CX = 266;
    private static final int CUBE_CY = 112;
    private static final int CUBE_SIZE = 26;

    /** Which of the three faces the cube is showing. Rotating swaps to the other three. */
    private static final Direction[] FRONT = { Direction.UP, Direction.WEST, Direction.SOUTH };
    private static final Direction[] BACK = { Direction.DOWN, Direction.EAST, Direction.NORTH };

    /** Client-side view state: the list's filter, sort, scroll and which row's gear is open. */
    private enum Filter { THIS_BAY, ALL_BAYS, PROBLEMS }

    private enum Sort { BAY, TYPE, STATUS }

    private static Filter filter = Filter.THIS_BAY;
    private static Sort sort = Sort.STATUS;
    private static BusConfig.Resource faceType = BusConfig.Resource.ITEM;
    private static boolean cubeFlipped;

    private int scroll;
    @org.jetbrains.annotations.Nullable
    private UUID openGear;

    BaysPage(WorkbayScreen screen) {
        super(screen);
    }

    @Override
    int width() {
        return WIDTH;
    }

    @Override
    int height() {
        return HEIGHT;
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

        g.drawString(font, "Power", x(216), y(31), Draw.TEXT_DIM, false);
        Draw.bar(g, x(250), y(29), 44, 9, snap.energy(), snap.energyCapacity(), Draw.AMBER);
        screen.hit(x(250), y(29), 44, 9, () -> { },
            WorkbayScreen.gui("power", snap.energy(), snap.energyCapacity()),
            WorkbayScreen.gui("power.tip"));

        g.drawString(font, snap.code(), x(8), y(HEIGHT - 12), Draw.TEXT_FAINT, false);
        // The separator between the header band and the working area.
        g.fill(x(6), y(44), x(WIDTH - 6), y(45), Draw.EDGE_DARK);
    }

    // ------------------------------------------------------------- bay rack

    private void rack(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        for (int index = 0; index < 8; index++) {
            int px = x(RACK_X + 2);
            int py = y(RACK_Y + index * RACK_PITCH);
            WorkbaySnapshot.Bay bay = snap.bay(index);
            boolean locked = bay.state() == WorkbaySnapshot.State.LOCKED;
            boolean selected = index == snap.selectedBay();
            boolean hover = screen.hovered(px, py, SLOT, SLOT, mouseX, mouseY);

            if (selected) {
                g.fill(x(RACK_X - 2), py, x(RACK_X + 1), py + SLOT, Draw.SELECT);
            }
            Draw.well(g, px, py, SLOT, SLOT);
            if (hover && !locked) {
                g.fill(px + 1, py + 1, px + SLOT - 1, py + SLOT - 1, 0x33FFFFFF);
            }

            ItemStack icon = iconFor(bay.hosted());
            if (!icon.isEmpty()) {
                // The hosted machine's own item, so a bay is identified at a glance (SPEC.md §4).
                g.renderItem(icon, px + 4, py + 4);
            }
            if (locked) {
                g.fill(px + 1, py + 1, px + SLOT - 1, py + SLOT - 1, 0x99000000);
            }
            // The 5x5 status pip, top-left, inside the slot.
            g.fill(px + 2, py + 2, px + 7, py + 7, pipColour(bay.state()));

            int captured = index;
            screen.hit(px, py, SLOT, SLOT, () -> screen.send(WorkbayAction.SELECT_BAY, captured),
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
        Draw.well(g, slotX, slotY, 40, 40);
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

        g.drawString(font, empty
            ? WorkbayScreen.gui("bay.n", snap.selectedBay() + 1).getString()
            : displayName(bay.hosted().orElseThrow()).getString(),
            x(98), y(56), Draw.TEXT, false);

        Draw.bar(g, x(98), y(68), 84, 9, bay.energy(), bay.energyCapacity(), Draw.AMBER);
        g.drawString(font, bay.energyCapacity() == 0 ? "—"
                : Draw.compact(bay.energy()) + " / " + Draw.compact(bay.energyCapacity()) + " FE",
            x(186), y(69), Draw.TEXT_DIM, false);

        g.drawString(font, statusLine(bay).getString(), x(98), y(82), statusColour(bay.state()), false);

        // Four 20x20 buttons at y=98. Only Eject does anything: Bay View, rename and the redstone
        // gate are SPEC.md §5's and are drawn faint rather than hidden, so the layout is honest
        // about what is not built rather than quietly missing three controls.
        unbuiltButton(g, x(50), y(98), 20, WBIcons.SCREEN,
            WorkbayScreen.gui("button.bay_view"), WorkbayScreen.gui("unbuilt"));
        boolean canEject = !empty;
        boolean hoverEject = screen.hovered(x(74), y(98), 20, 20, mouseX, mouseY);
        Draw.button(g, x(74), y(98), 20, 20, hoverEject && canEject, false);
        WBIcons.draw(g, WBIcons.EJECT, x(78), y(102), canEject ? Draw.TEXT : Draw.TEXT_FAINT);
        if (canEject) {
            screen.hit(x(74), y(98), 20, 20, () -> screen.send(WorkbayAction.EJECT),
                WorkbayScreen.gui("button.eject"), WorkbayScreen.gui("button.eject.tip"));
        }
        unbuiltButton(g, x(98), y(98), 20, WBIcons.RENAME,
            WorkbayScreen.gui("button.rename"), WorkbayScreen.gui("unbuilt"));
        unbuiltButton(g, x(122), y(98), 20, WBIcons.REDSTONE,
            WorkbayScreen.gui("button.redstone"), WorkbayScreen.gui("unbuilt"));
    }

    private Component statusLine(WorkbaySnapshot.Bay bay) {
        return WorkbayScreen.gui("bay." + bay.state().name().toLowerCase(java.util.Locale.ROOT));
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

        // Three 20x18 type buttons. The cube shows the selected type only, which is why a face can
        // be an input for items and an output for energy without the picture contradicting itself.
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

        Draw.well(g, x(232), y(76), 68, 56);
        Direction[] shown = cubeFlipped ? BACK : FRONT;
        Draw.isoCube(g, screen.font(), x(CUBE_CX), y(CUBE_CY), CUBE_SIZE, bay.faces(), faceType,
            shown[0], shown[1], shown[2]);

        // One hit region per visible face, tested in the same order they are drawn on top of.
        faceHit(shown[0], (mx, my) -> Draw.inTopFace(x(CUBE_CX), y(CUBE_CY), CUBE_SIZE, mx, my), bay);
        faceHit(shown[1], (mx, my) -> Draw.inLeftFace(x(CUBE_CX), y(CUBE_CY), CUBE_SIZE, mx, my), bay);
        faceHit(shown[2], (mx, my) -> Draw.inRightFace(x(CUBE_CX), y(CUBE_CY), CUBE_SIZE, mx, my), bay);

        iconButton(g, mouseX, mouseY, x(258), y(138), WBIcons.ROTATE, cubeFlipped,
            () -> cubeFlipped = !cubeFlipped,
            WorkbayScreen.gui("faces.rotate"), WorkbayScreen.gui("faces.rotate.tip"));

        // A legend, because green and blue mean nothing on their own.
        var font = screen.font();
        g.fill(x(232), y(140), x(238), y(146), Draw.GREEN);
        g.drawString(font, "in", x(240), y(140), Draw.TEXT_DIM, false);
        g.fill(x(232), y(150), x(238), y(156), Draw.BLUE);
        g.drawString(font, "out", x(240), y(150), Draw.TEXT_DIM, false);
    }

    /**
     * A cube face is a rhombus, so its click region is not a rectangle: the hit carries the shape
     * test and the bounding box is only the cheap first pass.
     */
    private void faceHit(Direction face, WorkbayScreen.Inside inside, WorkbaySnapshot.Bay bay) {
        int px = x(CUBE_CX) - CUBE_SIZE;
        int py = y(CUBE_CY) - CUBE_SIZE;
        FaceConfig.Role role = bay.faces().role(faceType, face);
        screen.hit(px, py, CUBE_SIZE * 2, CUBE_SIZE * 2 + CUBE_SIZE, inside,
            () -> screen.send(WorkbayAction.CYCLE_FACE, faceType.ordinal() | (face.ordinal() << 4)),
            WorkbayScreen.gui("faces.face", face.getName()),
            WorkbayScreen.gui("faces.role." + role.name().toLowerCase(java.util.Locale.ROOT)));
    }

    // ------------------------------------------------------------- the list

    private void links(GuiGraphics g, int mouseX, int mouseY) {
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        g.drawString(font, "LINKS", x(LIST_X + 4), y(LINKS_Y + 4), Draw.TEXT, false);

        iconButton(g, mouseX, mouseY, x(LIST_X + 44), y(LINKS_Y), WBIcons.FILTER, true,
            () -> filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length],
            WorkbayScreen.gui("links.filter." + filter.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.filter.tip"));
        iconButton(g, mouseX, mouseY, x(LIST_X + 66), y(LINKS_Y), WBIcons.SORT, true,
            () -> sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length],
            WorkbayScreen.gui("links.sort." + sort.name().toLowerCase(java.util.Locale.ROOT)),
            WorkbayScreen.gui("links.sort.tip"));

        int pairX = x(LIST_X + LIST_W - 46);
        boolean pairHover = screen.hovered(pairX, y(LINKS_Y), 46, 18, mouseX, mouseY);
        Draw.button(g, pairX, y(LINKS_Y), 46, 18, pairHover, false);
        WBIcons.draw(g, WBIcons.PLUS, pairX + 3, y(LINKS_Y + 3), Draw.TEXT);
        g.drawString(font, "Pair", pairX + 17, y(LINKS_Y + 5), Draw.TEXT, false);
        screen.hit(pairX, y(LINKS_Y), 46, 18, () -> screen.send(WorkbayAction.PAIR),
            WorkbayScreen.gui("links.pair"), WorkbayScreen.gui("links.pair.tip"));

        List<WorkbaySnapshot.Link> visible = visibleLinks(snap);
        Draw.well(g, x(LIST_X), y(ROW_Y - 4), LIST_W, ROWS * ROW_PITCH + 8);

        if (visible.isEmpty()) {
            g.drawString(font, WorkbayScreen.gui("links.none").getString(),
                x(LIST_X + 8), y(ROW_Y + 8), Draw.TEXT_FAINT, false);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, visible.size() - ROWS));
        for (int slot = 0; slot < ROWS && slot + scroll < visible.size(); slot++) {
            row(g, mouseX, mouseY, visible.get(slot + scroll), y(ROW_Y + slot * ROW_PITCH));
        }
        if (visible.size() > ROWS) {
            scrollbar(g, visible.size());
        }
    }

    private void scrollbar(GuiGraphics g, int total) {
        int trackX = x(LIST_X + LIST_W - 6);
        int trackY = y(ROW_Y);
        int trackH = ROWS * ROW_PITCH;
        g.fill(trackX, trackY, trackX + 4, trackY + trackH, Draw.EDGE_DARK);
        int knob = Math.max(8, trackH * ROWS / total);
        int offset = (trackH - knob) * scroll / Math.max(1, total - ROWS);
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
        }

        g.fill(px, py + 4, px + 10, py + 14, statusColour(link.status()));
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

        // The filter slot is drawn where SPEC.md §4 puts it; filters themselves are §5's.
        unbuiltButton(g, px + 200, py + 1, 16, WBIcons.FILTER,
            WorkbayScreen.gui("links.filterslot"), WorkbayScreen.gui("unbuilt"));

        boolean open = config.id().equals(openGear);
        iconButton(g, mouseX, mouseY, px + 222, py, WBIcons.GEAR, open,
            () -> openGear = open ? null : config.id(),
            WorkbayScreen.gui("links.gear"), WorkbayScreen.gui("links.gear.tip"));

        if (open) {
            gearMenu(g, mouseX, mouseY, config, px + 132, py + ROW_PITCH - 2);
        }
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
        if (mouseY < y(ROW_Y - 4) || mouseY > y(ROW_Y + ROWS * ROW_PITCH + 4)) {
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
            case TARGET_NOT_LOADED, TARGET_NO_PORT -> Draw.AMBER;
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
