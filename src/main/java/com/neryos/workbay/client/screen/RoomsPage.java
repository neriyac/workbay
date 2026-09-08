package com.neryos.workbay.client.screen;

import com.neryos.workbay.content.room.RoomColour;
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

    /**
     * Clear of the header band, which is now the title and the tab row and nothing else: the
     * back arrow that used to sit on its own line at y 24 is gone, and the twenty pixels it
     * cost are the reason four rooms plus five rungs fit inside 240 with room to spare rather
     * than with one pixel.
     */
    private static final int LADDER_Y = 26;

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
    private static final int ROOM_NAME_W = 84;
    private static final int ROOM_SIZE_X = 94;
    private static final int ROOM_SIZE_W = 96;
    private static final int SETTINGS_X = ROW_W - 102;
    private static final int ANCHOR_X = ROW_W - 82;
    private static final int ENTER_X = ROW_W - 62;
    private static final int ENTER_W = 58;

    // The settings window.
    private static final int WIN_W = 200;
    private static final int WIN_PAD = 8;
    private static final int SWATCH = 16;
    private static final int SWATCH_PITCH = 18;
    private static final int SWATCHES_PER_ROW = 8;
    private static final int BIOME_H = 13;
    /**
     * How many biomes the list shows at once. <b>The window's height does not depend on how many
     * there are</b> — the first draft drew one row per tag entry, which is fine for the six we ship
     * and is a three-thousand-pixel window the moment a pack adds Biomes O' Plenty or Terralith.
     * Six rows and a scrollbar, with the search box above, is Nature Compass's answer and it is the
     * right one: a list you can type at does not care how long it is.
     */
    private static final int BIOME_ROWS = 6;
    private static final int SEARCH_W = 84;
    private static final int SEARCH_H = 12;
    /** The scrollbar track down the right of the list, drawn only when there is more than fits. */
    private static final int SCROLLBAR_W = 6;

    /** Which room's settings window is open, or -1. Client-side and never sent anywhere. */
    private int open = -1;

    /** First visible row of the biome list. Reset whenever the search narrows it. */
    private int scroll;

    /** The search text as of the last frame, so a change can reset the scroll. */
    private String lastQuery = "";

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
        // Last, so its hit boxes win: the screen dispatches clicks in reverse registration order,
        // which is exactly "whatever is drawn on top".
        if (open >= 0) {
            settings(g, mouseX, mouseY);
        }
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
            // A name is never faint -- see UpgradesPage. What you can afford is the price's job.
            text(g, WorkbayScreen.gui(key), px + NAME_X, py + TEXT_Y, NAME_W, Draw.TEXT);
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
        // A rule between the two halves. The ladder and the rooms are the same eighteen-pixel row
        // in the same well, so nine of them in a column read as one list and nothing said which
        // were things to buy and which were things you own.
        g.fill(x(ROW_X), y(top - 5), x(ROW_X + ROW_W), y(top - 4), Draw.EDGE_DARK);
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
                swatch(g, mouseX, mouseY, px + SETTINGS_X, py, room);
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
     * The room's colour, drawn as the colour itself rather than as an icon of one, and the way into
     * that room's settings. A swatch is the one control here whose job is to show a value you can
     * only judge by looking, so the button <b>is</b> the value.
     */
    private void swatch(GuiGraphics g, int mouseX, int mouseY, int px, int py,
        WorkbaySnapshot.Room room) {
        boolean hover = screen.hovered(px, py, 18, 18, mouseX, mouseY);
        Draw.button(g, px, py, 18, 18, hover, open == room.index());
        g.fill(px + 4, py + 4, px + 14, py + 14, 0xFF000000 | room.colour().tint());
        int index = room.index();
        screen.hit(px, py, 18, 18, () -> setOpen(open == index ? -1 : index),
            WorkbayScreen.gui("rooms.settings"),
            WorkbayScreen.gui("rooms.colour",
                WorkbayScreen.gui("colour." + room.colour().getSerializedName())),
            WorkbayScreen.gui("rooms.biome", biomeName(room.biome())));
    }

    /** Opening or closing the window, and the search field that belongs to it, in one place. */
    private void setOpen(int index) {
        open = index;
        scroll = 0;
        lastQuery = "";
        if (index < 0) {
            screen.closeFilter();
        }
    }

    /**
     * Escape closes the window rather than the screen. Without it, a player who opened the picker
     * to look at the colours is thrown out of the Workbay entirely by the one key everybody presses
     * to back out of a thing.
     */
    @Override
    boolean escaped() {
        if (open < 0) {
            return false;
        }
        setOpen(-1);
        return true;
    }

    /** The biome list scrolls; nothing else on this page does. */
    @Override
    boolean scrolled(double mouseX, double mouseY, double delta) {
        if (open < 0) {
            return false;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    /**
     * One room's settings, over the page. <b>A window, not two cycle buttons.</b>
     *
     * <p>The cycle buttons were built first and were the wrong control twice over: the biome one
     * was a twelve-pixel icon that never changed, so pressing it looked exactly like nothing
     * happening, and neither ever showed what the other choices <em>were</em>. Mekanism answers the
     * same question with a window -- {@code GuiColorWindow}, {@code GuiRobitSkinSelect} -- and that
     * is the right answer: a value you pick out of a set is a list, not a step.
     */
    private void settings(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot.Room room = snapshot().rooms().stream()
            .filter(r -> r.index() == open && r.built()).findFirst().orElse(null);
        if (room == null) {
            setOpen(-1);
            return;
        }
        int rows = (RoomColour.values().length + SWATCHES_PER_ROW - 1) / SWATCHES_PER_ROW;
        int listH = BIOME_ROWS * BIOME_H + 2;
        int h = WIN_PAD * 2 + 12 + 10 + rows * SWATCH_PITCH + 8 + SEARCH_H + 2 + listH;
        int wx = x((WIDTH - WIN_W) / 2);
        int wy = y(Math.max(2, (height() - h) / 2));

        // The page stays drawn behind it; a scrim says which of the two is taking clicks. The
        // full-page hit under the window is what closes it, and it is registered first so every
        // control of the window itself still wins -- clicks resolve in reverse order.
        g.fill(x(0), y(0), x(WIDTH), y(height()), 0xB4000000);
        screen.hit(x(0), y(0), WIDTH, height(), () -> setOpen(-1));
        Draw.panel(g, wx, wy, WIN_W, h);

        int cursor = wy + WIN_PAD;
        String name = room.name().isEmpty()
            ? WorkbayScreen.gui("rooms.name", room.index() + 1).getString() : room.name();
        Draw.text(g, screen.font(), name, wx + WIN_PAD, cursor, WIN_W - WIN_PAD * 2 - 20,
            Draw.TEXT);
        // A close button, because "click the dark part" is not a control anybody can see. Mekanism
        // puts one on every window it opens for the same reason.
        int closeX = wx + WIN_W - WIN_PAD - 14;
        int closeY = cursor - 3;
        boolean closeHover = screen.hovered(closeX, closeY, 14, 14, mouseX, mouseY);
        Draw.button(g, closeX, closeY, 14, 14, closeHover, false);
        WBIcons.draw(g, WBIcons.CROSS, closeX + 1, closeY + 1, Draw.TEXT_DIM);
        screen.hit(closeX, closeY, 14, 14, () -> setOpen(-1),
            WorkbayScreen.gui("rooms.settings.close"));
        cursor += 12;

        Draw.text(g, screen.font(), WorkbayScreen.gui("rooms.colour.label").getString(),
            wx + WIN_PAD, cursor, WIN_W - WIN_PAD * 2, Draw.TEXT_DIM);
        cursor += 10;
        for (RoomColour colour : RoomColour.values()) {
            int i = colour.ordinal();
            int cx = wx + WIN_PAD + (i % SWATCHES_PER_ROW) * SWATCH_PITCH;
            int cy = cursor + (i / SWATCHES_PER_ROW) * SWATCH_PITCH;
            boolean chosen = colour == room.colour();
            boolean hover = screen.hovered(cx, cy, SWATCH, SWATCH, mouseX, mouseY);
            Draw.button(g, cx, cy, SWATCH, SWATCH, hover, chosen);
            g.fill(cx + 3, cy + 3, cx + SWATCH - 3, cy + SWATCH - 3, 0xFF000000 | colour.tint());
            if (chosen) {
                // A ring, not a shade. Draw.button's active fill sits *behind* a swatch that is
                // ten solid pixels of colour, so the one the room is actually painted could not be
                // picked out of the eleven.
                Draw.bevel(g, cx, cy, SWATCH, SWATCH, true, Draw.SELECT, Draw.SELECT);
            }
            long packed = room.index() | ((long) i << 16);
            screen.hit(cx, cy, SWATCH, SWATCH,
                () -> screen.send(WorkbayAction.SET_ROOM_COLOUR, packed),
                WorkbayScreen.gui("colour." + colour.getSerializedName()));
        }
        cursor += rows * SWATCH_PITCH + 8;

        // Label on the left of the line, search field on the right of it: one line for "what this
        // is" and "how to find one in it", which is the shape every long list in the genre uses.
        int searchX = wx + WIN_W - WIN_PAD - SEARCH_W;
        Draw.text(g, screen.font(), WorkbayScreen.gui("rooms.biome.label").getString(),
            wx + WIN_PAD, cursor + 2, searchX - wx - WIN_PAD - 4, Draw.TEXT_DIM);
        String query = screen.openFilter(searchX + 4, cursor + 2, SEARCH_W - 8, SEARCH_H - 2);
        Draw.slot(g, searchX, cursor, SEARCH_W, SEARCH_H);
        // The field is a real widget drawn by the screen, so the only thing needed here is a hit
        // that takes the click back off the scrim and gives it to the box.
        screen.hit(searchX, cursor, SEARCH_W, SEARCH_H, screen::focusFilter);
        if (query.isEmpty()) {
            Draw.text(g, screen.font(), WorkbayScreen.gui("rooms.biome.search").getString(),
                searchX + 4, cursor + 2, SEARCH_W - 8, Draw.TEXT_FAINT);
        }
        cursor += SEARCH_H + 2;

        java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>
            matches = matching(query);
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scroll = 0;
        }
        int listW = WIN_W - WIN_PAD * 2;
        Draw.well(g, wx + WIN_PAD, cursor, listW, listH);
        boolean bar = matches.size() > BIOME_ROWS;
        int rowW = listW - 2 - (bar ? SCROLLBAR_W + 1 : 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, matches.size() - BIOME_ROWS));

        if (matches.isEmpty()) {
            // A list with nothing in it says which of the two reasons it is, because they need
            // different things done about them: an empty tag is a pack's doing, an empty search
            // is the player's.
            Draw.text(g, screen.font(),
                WorkbayScreen.gui(biomeChoices().isEmpty() ? "rooms.biome.none"
                    : "rooms.biome.nomatch").getString(),
                wx + WIN_PAD + 5, cursor + 5, listW - 10, Draw.TEXT_FAINT);
        }
        for (int row = 0; row < BIOME_ROWS && scroll + row < matches.size(); row++) {
            var key = matches.get(scroll + row);
            String id = key.location().toString();
            int bx = wx + WIN_PAD + 1;
            int by = cursor + 1 + row * BIOME_H;
            boolean chosen = id.equals(room.biome());
            boolean hover = screen.hovered(bx, by, rowW, BIOME_H, mouseX, mouseY);
            Draw.button(g, bx, by, rowW, BIOME_H, hover, chosen);
            Draw.text(g, screen.font(), biomeName(id).getString(), bx + 5, by + 3, rowW - 10,
                chosen ? Draw.TEXT : Draw.TEXT_DIM);
            long index = room.index();
            screen.hit(bx, by, rowW, BIOME_H,
                () -> screen.sendText(WorkbayAction.SET_ROOM_BIOME, index, id),
                Component.literal(id), WorkbayScreen.gui("rooms.biome.tip"));
        }
        if (bar) {
            // Six wide and drawn as two flat fills. At four, with a bevel on the knob, the whole
            // bar was three pixels of edge and one of knob -- a sliver that read as a rendering
            // seam down the side of the list rather than as something you could drag.
            int trackX = wx + WIN_PAD + listW - 1 - SCROLLBAR_W;
            int trackH = listH - 2;
            g.fill(trackX, cursor + 1, trackX + SCROLLBAR_W, cursor + 1 + trackH, Draw.TUBE);
            int knobH = Math.max(8, trackH * BIOME_ROWS / matches.size());
            int knobY = cursor + 1
                + (trackH - knobH) * scroll / Math.max(1, matches.size() - BIOME_ROWS);
            g.fill(trackX + 1, knobY, trackX + SCROLLBAR_W - 1, knobY + knobH, Draw.EDGE_LIGHT);
        }
    }

    /**
     * The choices whose name or id contains the search text, in the tag's own order.
     *
     * <p>Matched against the <b>translated name</b> as well as the id, because a player looking for
     * a cherry grove types "cherry", and a pack whose biome id is {@code terralith:yellowstone}
     * still calls it Yellowstone on screen.
     */
    private static java.util.List<
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> matching(
        String query) {
        var all = biomeChoices();
        if (query.isBlank()) {
            return all;
        }
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        return all.stream().filter(key -> {
            String id = key.location().toString();
            return id.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || biomeName(id).getString().toLowerCase(java.util.Locale.ROOT).contains(needle);
        }).toList();
    }

    /**
     * The tag, read from the <b>client's own</b> registry: tags are synced, so the picker needs
     * nothing on the snapshot -- which is as well, because its codec group is full at sixteen.
     */
    private static java.util.List<
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> biomeChoices() {
        net.minecraft.client.multiplayer.ClientLevel level =
            net.minecraft.client.Minecraft.getInstance().level;
        return level == null ? java.util.List.of()
            : com.neryos.workbay.world.RoomBiomes.choices(level.registryAccess());
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
