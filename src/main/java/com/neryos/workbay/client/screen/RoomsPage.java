package com.neryos.workbay.client.screen;

import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * ROOMS. SPEC.md §5, and the one page where a number is the whole point.
 *
 * <p>The room ladder is on this page rather than on UPGRADES because <b>what a Frame costs is a
 * chunk count</b>, and a chunk count means nothing without the rooms it applies to beside it. It
 * also keeps UPGRADES at the four rows it was drawn and photographed with.
 *
 * <p>Rows are one line rather than the two UPGRADES uses. They can be: this page is 296 wide where
 * that one is 194, because it has no preview well to make room for — so name, description, count
 * and price all fit across, and five rungs plus four rooms fit down inside 240.
 */
class RoomsPage extends WorkbayPage {

    private static final int WIDTH = 320;

    private static final int ROW_X = 12;
    private static final int ROW_W = 296;
    private static final int ROW_H = 18;
    private static final int ROW_PITCH = 20;

    /** Clear of the header band: the title sits at y 6 and the back button at y 24. */
    private static final int LADDER_Y = 46;

    /**
     * Everything that costs chunks, in the order the player climbs it. The Anchor is here rather
     * than on UPGRADES for the same reason as the Frames: what it buys is measured in chunks, and
     * the switches it turns on are the room rows directly below it.
     */
    private static final WorkbayUpgrade[] LADDER = {
        WorkbayUpgrade.ROOM_FRAME, WorkbayUpgrade.WIDE_ROOM_FRAME, WorkbayUpgrade.VAST_ROOM_FRAME,
        WorkbayUpgrade.ANNEX_PLATE, WorkbayUpgrade.ANCHOR,
    };

    /** Row columns, fixed rather than derived from what happens to be in them. */
    private static final int TEXT_X = 22;
    private static final int NAME_W = 104;
    private static final int DESC_X = 130;
    private static final int DESC_W = 92;
    private static final int COUNT_RIGHT = ROW_W - 60;
    private static final int COUNT_W = 34;
    private static final int PRICE_RIGHT = ROW_W - 26;
    private static final int PRICE_W = 56;

    RoomsPage(WorkbayScreen screen) {
        super(screen);
    }

    @Override
    int width() {
        return WIDTH;
    }

    /**
     * Exactly as tall as it has rows. Four rooms is 240, which is the floor Minecraft's guiScale
     * cap guarantees — a page taller than that is invisible in a narrow band of window sizes
     * (OPEN_ISSUES), and this one deliberately stops at it.
     */
    @Override
    int height() {
        return roomsTop() + Math.max(1, snapshot().rooms().size()) * ROW_PITCH + 8;
    }

    private int roomsTop() {
        return LADDER_Y + LADDER.length * ROW_PITCH + 6;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        header(g, mouseX, mouseY, "ROOMS");
        ladder(g, mouseX, mouseY);
        rooms(g, mouseX, mouseY);
    }

    private void ladder(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        for (int i = 0; i < LADDER.length; i++) {
            WorkbayUpgrade upgrade = LADDER[i];
            int px = x(ROW_X);
            int py = y(LADDER_Y + i * ROW_PITCH);
            int installed = snap.upgrades().installed(upgrade);
            boolean maxed = installed >= upgrade.max();
            int cost = upgrade.levyCost(installed);
            boolean affordable = snap.levy() >= cost;
            boolean canInstall = !maxed && affordable;

            Draw.well(g, px, py, ROW_W, ROW_H);
            g.renderItem(new ItemStack(upgrade.item()), px + 2, py + 1);

            String key = "upgrade." + upgrade.getSerializedName();
            text(g, WorkbayScreen.gui(key), px + TEXT_X, py + 5, NAME_W,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            text(g, WorkbayScreen.gui(key + ".desc"), px + DESC_X, py + 5, DESC_W, Draw.TEXT_FAINT);
            textRight(g, installed + " / " + upgrade.max(), px + COUNT_RIGHT, py + 5, COUNT_W,
                maxed ? Draw.GREEN : Draw.TEXT_DIM);
            // The price is on the row and never only in a tooltip: it is the thing being decided.
            textRight(g, maxed ? "" : WorkbayScreen.gui("upgrades.levy", cost).getString(),
                px + PRICE_RIGHT, py + 5, PRICE_W,
                maxed ? Draw.TEXT_FAINT : affordable ? Draw.GREEN : Draw.RED);

            int addX = px + ROW_W - 20;
            boolean hover = canInstall && screen.hovered(addX, py, 18, 18, mouseX, mouseY);
            Draw.button(g, addX, py, 18, 18, hover, maxed, canInstall);
            WBIcons.draw(g, WBIcons.PLUS, addX + 3, py + 3,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            int ordinal = upgrade.ordinal();
            // The same three tooltip shapes UPGRADES uses, for the same reason: a maxed row has
            // nothing to price and an unaffordable one should not say the price twice.
            screen.hit(addX, py, 18, 18,
                canInstall ? () -> screen.send(WorkbayAction.INSTALL_UPGRADE, ordinal) : () -> { },
                maxed ? new net.minecraft.network.chat.Component[] {
                    WorkbayScreen.gui(key), WorkbayScreen.gui("upgrades.maxed") }
                    : affordable ? new net.minecraft.network.chat.Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.add", WorkbayScreen.gui(key)),
                        WorkbayScreen.gui("upgrades.cost", cost) }
                    : new net.minecraft.network.chat.Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.unaffordable", snap.levy(), cost) });
        }
    }

    private void rooms(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        int top = roomsTop();
        if (snap.rooms().isEmpty()) {
            Draw.well(g, x(ROW_X), y(top), ROW_W, ROW_H);
            text(g, WorkbayScreen.gui("rooms.none"), x(ROW_X) + 6, y(top) + 5, ROW_W - 12,
                Draw.TEXT_FAINT);
            return;
        }
        for (WorkbaySnapshot.Room room : snap.rooms()) {
            int px = x(ROW_X);
            int py = y(top + room.index() * ROW_PITCH);
            Draw.well(g, px, py, ROW_W, ROW_H);

            String name = room.name().isEmpty()
                ? WorkbayScreen.gui("rooms.name", room.index() + 1).getString() : room.name();
            text(g, name, px + 6, py + 5, NAME_W, Draw.TEXT);

            // Size and price on one line, because they are one decision. An unopened slot says so
            // rather than showing a size it has not got.
            String size = room.built()
                ? WorkbayScreen.gui("rooms.size", room.interior(), room.interior(),
                    room.chunkCost()).getString()
                : WorkbayScreen.gui("rooms.empty").getString();
            text(g, size, px + DESC_X, py + 5, 120, room.built() ? Draw.TEXT_DIM : Draw.TEXT_FAINT);

            // The Anchor toggle only exists once the network owns an Anchor. SPEC.md §4: a control
            // whose feature is not there is hidden, not drawn faint — faint is honest for one
            // session and furniture after two.
            int anchorX = px + ROW_W - 82;
            if (snap.upgrades().anchors() > 0 && room.built()) {
                iconButton(g, mouseX, mouseY, anchorX, py, WBIcons.ANCHOR, room.anchored(),
                    () -> screen.send(WorkbayAction.TOGGLE_ROOM_ANCHOR, room.index()),
                    WorkbayScreen.gui(room.anchored() ? "rooms.anchored" : "rooms.unanchored"),
                    WorkbayScreen.gui(room.anchored() ? "rooms.anchored.tip" : "rooms.unanchored.tip",
                        room.chunkCost()));
            }

            int enterX = px + ROW_W - 62;
            boolean hover = screen.hovered(enterX, py, 58, 18, mouseX, mouseY);
            Draw.button(g, enterX, py, 58, 18, hover, false);
            textCentre(g, WorkbayScreen.gui(room.built() ? "rooms.enter" : "rooms.open").getString(),
                enterX + 29, py + 5, 54, Draw.TEXT);
            screen.hit(enterX, py, 58, 18,
                () -> screen.send(WorkbayAction.ENTER_ROOM, room.index()),
                WorkbayScreen.gui(room.built() ? "rooms.enter" : "rooms.open"),
                WorkbayScreen.gui("rooms.enter.tip"));
        }
    }
}
