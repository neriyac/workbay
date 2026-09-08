package com.neryos.workbay.client.screen;

import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * ROOMS. SPEC.md §5, and the one page where a number is the whole point.
 *
 * <p>The room ladder is on this page rather than on UPGRADES because <b>what a Frame costs is a
 * chunk count</b>, and a chunk count means nothing without the rooms it applies to beside it. It
 * also keeps UPGRADES at the four rows it was drawn and photographed with.
 *
 * <p>Rows are one line rather than the two UPGRADES uses, because five rungs plus four rooms only
 * fit inside 240 that way. <b>Every column below is a disjoint span of the 296</b>, written as a
 * first and a last x rather than an x plus a width, for one reason: the first draft gave the
 * description, the count and the price overlapping spans and all three printed on top of each
 * other -- "A 14x14 room, 11./ 1" -- on a page nobody had looked at yet. The strings were then cut
 * to the columns they get, which is why a Frame's row says the size and each room's row says the
 * chunks: the count belongs beside the room it is charged for.
 */
class RoomsPage extends WorkbayPage {

    private static final int WIDTH = 320;

    private static final int ROW_X = 12;
    private static final int ROW_W = 296;
    private static final int ROW_H = 18;
    private static final int ROW_PITCH = 20;
    /** One line of text, centred in an 18px row. */
    private static final int TEXT_Y = 5;

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

    // Ladder columns. 2..20 is the item, and the four spans below never touch.
    private static final int NAME_X = 22;
    private static final int NAME_W = 96;
    private static final int DESC_X = 122;
    private static final int DESC_W = 62;
    private static final int COUNT_RIGHT = 214;
    private static final int COUNT_W = 28;
    private static final int PRICE_RIGHT = 272;
    private static final int PRICE_W = 56;
    private static final int ADD_X = ROW_W - 20;

    // Room columns. The three buttons are fixed to the right edge and the two strings share what is
    // left, so the longest room name and "46x46, 9 chunks" both have their own room.
    private static final int ROOM_NAME_X = 6;
    private static final int ROOM_NAME_W = 64;
    private static final int ROOM_SIZE_X = 74;
    private static final int ROOM_SIZE_W = 96;
    private static final int COLOUR_X = ROW_W - 122;
    private static final int BIOME_X = ROW_W - 102;
    private static final int ANCHOR_X = ROW_W - 82;
    private static final int ENTER_X = ROW_W - 62;
    private static final int ENTER_W = 58;

    RoomsPage(WorkbayScreen screen) {
        super(screen);
    }

    @Override
    int width() {
        return WIDTH;
    }

    /**
     * Exactly as tall as it has rows. Four rooms is 240, which is the floor Minecraft's guiScale
     * cap guarantees -- a page taller than that is invisible in a narrow band of window sizes
     * (OPEN_ISSUES), and this one deliberately stops at it. Measured at 880x680 on a 150% display,
     * which is inside that band: it fits with one pixel to spare.
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
            // Faint means "you cannot have this", so a row you already own is not faint: the page
            // read as five disabled things the first time it was on a screen.
            int nameColour = maxed || affordable ? Draw.TEXT : Draw.TEXT_FAINT;
            text(g, WorkbayScreen.gui(key), px + NAME_X, py + TEXT_Y, NAME_W, nameColour);
            text(g, WorkbayScreen.gui(key + ".desc"), px + DESC_X, py + TEXT_Y, DESC_W,
                Draw.TEXT_FAINT);
            textRight(g, installed + " / " + upgrade.max(), px + COUNT_RIGHT, py + TEXT_Y, COUNT_W,
                maxed ? Draw.GREEN : Draw.TEXT_DIM);
            // The price is on the row and never only in a tooltip: it is the thing being decided.
            textRight(g, maxed ? "" : WorkbayScreen.gui("upgrades.levy", cost).getString(),
                px + PRICE_RIGHT, py + TEXT_Y, PRICE_W,
                maxed ? Draw.TEXT_FAINT : affordable ? Draw.GREEN : Draw.RED);

            int addX = px + ADD_X;
            boolean hover = canInstall && screen.hovered(addX, py, 18, 18, mouseX, mouseY);
            Draw.button(g, addX, py, 18, 18, hover, maxed, canInstall);
            WBIcons.draw(g, WBIcons.PLUS, addX + 3, py + 3,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            int ordinal = upgrade.ordinal();
            // The same three tooltip shapes UPGRADES uses, for the same reason: a maxed row has
            // nothing to price and an unaffordable one should not say the price twice.
            screen.hit(addX, py, 18, 18,
                canInstall ? () -> screen.send(WorkbayAction.INSTALL_UPGRADE, ordinal) : () -> { },
                maxed ? new Component[] {
                    WorkbayScreen.gui(key), WorkbayScreen.gui("upgrades.maxed") }
                    : affordable ? new Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.add", WorkbayScreen.gui(key)),
                        WorkbayScreen.gui("upgrades.cost", cost) }
                    : new Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.unaffordable", snap.levy(), cost) });
        }
    }

    private void rooms(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();
        int top = roomsTop();
        if (snap.rooms().isEmpty()) {
            Draw.well(g, x(ROW_X), y(top), ROW_W, ROW_H);
            text(g, WorkbayScreen.gui("rooms.none"), x(ROW_X) + 6, y(top) + TEXT_Y, ROW_W - 12,
                Draw.TEXT_FAINT);
            return;
        }
        for (WorkbaySnapshot.Room room : snap.rooms()) {
            int px = x(ROW_X);
            int py = y(top + room.index() * ROW_PITCH);
            Draw.well(g, px, py, ROW_W, ROW_H);

            String name = room.name().isEmpty()
                ? WorkbayScreen.gui("rooms.name", room.index() + 1).getString() : room.name();
            text(g, name, px + ROOM_NAME_X, py + TEXT_Y, ROOM_NAME_W, Draw.TEXT);

            // Size and price on one line, because they are one decision. An unopened slot says so
            // rather than showing a size it has not got.
            String size = room.built()
                ? WorkbayScreen.gui(room.chunkCost() == 1 ? "rooms.size.one" : "rooms.size",
                    room.interior(), room.interior(), room.chunkCost()).getString()
                : WorkbayScreen.gui("rooms.empty").getString();
            text(g, size, px + ROOM_SIZE_X, py + TEXT_Y, ROOM_SIZE_W,
                room.built() ? Draw.TEXT_DIM : Draw.TEXT_FAINT);

            // Only a built room has a shell to paint or chunks to write a biome over, and an
            // unopened one has no record to remember either choice on -- SPEC.md §8 spends the
            // region on first entry.
            if (room.built()) {
                swatch(g, mouseX, mouseY, px + COLOUR_X, py, room);
                iconButton(g, mouseX, mouseY, px + BIOME_X, py, WBIcons.BIOME, false,
                    () -> screen.send(WorkbayAction.CYCLE_ROOM_BIOME, room.index()),
                    WorkbayScreen.gui("rooms.biome", biomeName(room.biome())),
                    WorkbayScreen.gui("rooms.biome.tip"));
            }

            // The Anchor toggle only exists once the network owns an Anchor. SPEC.md §4: a control
            // whose feature is not there is hidden, not drawn faint -- faint is honest for one
            // session and furniture after two.
            if (snap.upgrades().anchors() > 0 && room.built()) {
                iconButton(g, mouseX, mouseY, px + ANCHOR_X, py, WBIcons.ANCHOR, room.anchored(),
                    () -> screen.send(WorkbayAction.TOGGLE_ROOM_ANCHOR, room.index()),
                    WorkbayScreen.gui(room.anchored() ? "rooms.anchored" : "rooms.unanchored"),
                    WorkbayScreen.gui(room.anchored() ? "rooms.anchored.tip" : "rooms.unanchored.tip",
                        room.chunkCost()));
            }

            int enterX = px + ENTER_X;
            boolean hover = screen.hovered(enterX, py, ENTER_W, 18, mouseX, mouseY);
            Draw.button(g, enterX, py, ENTER_W, 18, hover, false);
            textCentre(g, WorkbayScreen.gui(room.built() ? "rooms.enter" : "rooms.open").getString(),
                enterX + ENTER_W / 2, py + TEXT_Y, ENTER_W - 4, Draw.TEXT);
            screen.hit(enterX, py, ENTER_W, 18,
                () -> screen.send(WorkbayAction.ENTER_ROOM, room.index()),
                WorkbayScreen.gui(room.built() ? "rooms.enter" : "rooms.open"),
                WorkbayScreen.gui("rooms.enter.tip"));
        }
    }

    /**
     * The room's colour, drawn as the colour itself rather than as an icon of one. A swatch is the
     * one control on this page whose whole job is to show a value the player can only judge by
     * looking at it, so the button <b>is</b> the value.
     */
    private void swatch(GuiGraphics g, int mouseX, int mouseY, int px, int py,
        WorkbaySnapshot.Room room) {
        boolean hover = screen.hovered(px, py, 18, 18, mouseX, mouseY);
        Draw.button(g, px, py, 18, 18, hover, false);
        g.fill(px + 4, py + 4, px + 14, py + 14, 0xFF000000 | room.colour().tint());
        screen.hit(px, py, 18, 18,
            () -> screen.send(WorkbayAction.CYCLE_ROOM_COLOUR, room.index()),
            WorkbayScreen.gui("rooms.colour",
                WorkbayScreen.gui("colour." + room.colour().getSerializedName())),
            WorkbayScreen.gui("rooms.colour.tip"));
    }

    /**
     * A biome's own name, from its own mod's lang file: every biome in the game already has
     * {@code biome.<namespace>.<path>}, so a modded biome added to {@code #workbay:room_biomes}
     * names itself and this mod ships no string for it.
     */
    private static Component biomeName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Component.literal(id)
            : Component.translatable("biome." + location.getNamespace() + "."
                + location.getPath());
    }
}
