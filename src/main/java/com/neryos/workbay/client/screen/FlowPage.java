package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen 2 — the flow map. SPEC.md §4.
 *
 * <p>Three columns: outside, inside, outside. A solid arrow crosses the world boundary; a
 * <b>dashed blue arrow is bay to bay and never leaves the Workbay</b>, and that one is the whole
 * reason the screen exists — an automation can live entirely inside the block, and nothing else in
 * the mod shows the player that. A stalled flow draws amber.
 */
class FlowPage extends WorkbayPage {

    private static final int WIDTH = 320;

    /**
     * Three node columns and two arrow gaps, filling the panel between 12px margins. A node was 70
     * wide, which gives a name 62 pixels — less than "Enrichment Chamber" needs, and this screen is
     * nothing but machine names. 84 is what the empty space between the columns was worth.
     */
    private static final int LEFT_X = 12;
    private static final int MID_X = 118;
    private static final int RIGHT_X = 224;
    private static final int NODE_W = 84;
    private static final int NODE_H = 18;
    private static final int TOP_Y = 60;
    private static final int PITCH = 22;

    /**
     * <b>This page is as tall as what it draws.</b> A fixed 232 was wrong at both ends: eight bays
     * at {@link #PITCH} from {@link #TOP_Y} put the last node's bottom edge at 232 and the legend
     * was drawn at {@code HEIGHT - 34}, <em>under</em> rows seven and eight; three bays left the
     * bottom two fifths of the panel empty. Both were invisible until the screen was opened at a
     * real window size.
     *
     * <p>Measured once, in the constructor, because {@link #height()} is read by {@code init()}
     * before anything renders and the two must agree. {@code init()} re-runs on every page change,
     * so a bay installed on the upgrades screen is counted the next time this one is opened —
     * which is the only way the count can move.
     */
    private final int rows;
    private final int legendY;

    FlowPage(WorkbayScreen screen) {
        super(screen);
        WorkbaySnapshot snap = screen.snapshot();
        long bays = snap.bays().stream().filter(bay -> bay.hosted().isPresent()).count();
        long out = snap.links().stream()
            .filter(link -> link.config().mode() == BusConfig.Mode.INSERT).count();
        long in = snap.links().size() - out;
        this.rows = (int) Math.max(1, Math.max(bays, Math.max(in, out)));
        this.legendY = TOP_Y + (rows - 1) * PITCH + NODE_H + 10;
    }

    @Override
    int width() {
        return WIDTH;
    }

    @Override
    int height() {
        // The legend's two lines, then the same bottom margin the top gets.
        return legendY + 12 + 9 + 8;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        header(g, mouseX, mouseY, "FLOW");
        WorkbaySnapshot snap = snapshot();

        // Each heading gets its own column's width, so a translation cannot reach into the next.
        text(g, "pulled from", x(LEFT_X), y(48), NODE_W, Draw.TEXT_FAINT);
        text(g, "bays", x(MID_X + 30), y(48), NODE_W - 30, Draw.TEXT_FAINT);
        text(g, "sent to", x(RIGHT_X), y(48), NODE_W, Draw.TEXT_FAINT);

        // The bays first: they are the spine, and both outside columns hang off them.
        Map<Integer, Integer> bayRow = new LinkedHashMap<>();
        List<Integer> occupied = new ArrayList<>();
        for (WorkbaySnapshot.Bay bay : snap.bays()) {
            if (bay.hosted().isPresent()) {
                occupied.add(bay.index());
            }
        }
        for (int i = 0; i < occupied.size(); i++) {
            int rowY = y(TOP_Y + i * PITCH);
            bayRow.put(occupied.get(i), rowY);
            WorkbaySnapshot.Bay bay = snap.bay(occupied.get(i));
            node(g, x(MID_X), rowY, name(bay.hosted().orElse(null)), Draw.PANEL_LIGHT);
        }
        if (occupied.isEmpty()) {
            text(g, WorkbayScreen.gui("flow.empty"), x(MID_X - 20), y(TOP_Y + 4),
                NODE_W + 40, Draw.TEXT_FAINT);
        }

        int inRow = 0;
        int outRow = 0;
        for (WorkbaySnapshot.Link link : snap.links()) {
            Integer rowY = bayRow.get(link.config().bay());
            if (rowY == null) {
                continue;
            }
            boolean insert = link.config().mode() == BusConfig.Mode.INSERT;
            int colour = colourFor(link.status());

            // Bay to bay: both ends are this Workbay's own bays, so nothing crosses the boundary.
            if (isInternal(snap, link)) {
                dashed(g, x(MID_X) + NODE_W + 4, rowY + NODE_H / 2,
                    x(MID_X) + NODE_W + 16, rowY + NODE_H / 2, Draw.BLUE);
                continue;
            }
            if (insert) {
                int py = y(TOP_Y + outRow++ * PITCH);
                node(g, x(RIGHT_X), py, targetName(link), Draw.WELL);
                arrow(g, x(MID_X) + NODE_W, rowY + NODE_H / 2, x(RIGHT_X), py + NODE_H / 2, colour);
            } else {
                int py = y(TOP_Y + inRow++ * PITCH);
                node(g, x(LEFT_X), py, targetName(link), Draw.WELL);
                arrow(g, x(LEFT_X) + NODE_W, py + NODE_H / 2, x(MID_X), rowY + NODE_H / 2, colour);
            }
        }

        legend(g);
    }

    /**
     * A link is internal when the Connector is stuck to a block that is itself in the Backshop —
     * which in v1 can only be another bay of this Workbay.
     */
    private static boolean isInternal(WorkbaySnapshot snap, WorkbaySnapshot.Link link) {
        return link.config().target().dimension().equals(
            com.neryos.workbay.world.WorkbayDimensions.BACKSHOP);
    }

    private void node(GuiGraphics g, int px, int py, Component label, int fill) {
        Draw.well(g, px, py, NODE_W, NODE_H);
        g.fill(px + 1, py + 1, px + NODE_W - 1, py + NODE_H - 1, fill);
        text(g, label, px + 4, py + 5, NODE_W - 8, Draw.TEXT_DIM);
    }

    /** A straight line with a two-pixel head. Enough to read a direction at this size. */
    private void arrow(GuiGraphics g, int x1, int y1, int x2, int y2, int colour) {
        line(g, x1, y1, x2, y2, colour, 1);
        g.fill(x2 - 4, y2 - 2, x2 - 1, y2 + 1, colour);
        g.fill(x2 - 3, y2 - 3, x2 - 1, y2 + 2, colour);
    }

    private void dashed(GuiGraphics g, int x1, int y1, int x2, int y2, int colour) {
        line(g, x1, y1, x2, y2, colour, 3);
        g.fill(x2 - 3, y2 - 2, x2, y2 + 1, colour);
    }

    /**
     * @param dash 1 draws solid; anything higher skips two pixels in every {@code dash}, which is
     *             what tells a bay-to-bay flow apart from one that leaves the block
     */
    private void line(GuiGraphics g, int x1, int y1, int x2, int y2, int colour, int dash) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            if (dash > 1 && (i / 2) % dash != 0) {
                continue;
            }
            int px = x1 + (x2 - x1) * i / Math.max(1, steps);
            int py = y1 + (y2 - y1) * i / Math.max(1, steps);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }

    /** Two columns of key, each entry a 16px rule and a label with the rest of its column. */
    private void legend(GuiGraphics g) {
        int py = y(legendY);
        int right = MID_X + 56;
        int leftRoom = right - LEFT_X - 20 - 8;
        int rightRoom = WIDTH - right - 20 - 12;
        // Both legend rows sit below every node, whatever the tallest column came to.

        g.fill(x(LEFT_X), py + 3, x(LEFT_X + 16), py + 4, Draw.GREEN);
        text(g, WorkbayScreen.gui("flow.legend.out"), x(LEFT_X + 20), py, leftRoom, Draw.TEXT_DIM);

        int py2 = py + 12;
        for (int i = 0; i < 16; i += 6) {
            g.fill(x(LEFT_X + i), py2 + 3, x(LEFT_X + i + 3), py2 + 4, Draw.BLUE);
        }
        text(g, WorkbayScreen.gui("flow.legend.internal"), x(LEFT_X + 20), py2, leftRoom,
            Draw.TEXT_DIM);

        g.fill(x(right), py + 3, x(right + 16), py + 4, Draw.AMBER);
        text(g, WorkbayScreen.gui("flow.legend.stalled"), x(right + 20), py, rightRoom,
            Draw.TEXT_DIM);
    }

    private static int colourFor(BusRunner.BusStatus status) {
        return switch (status) {
            case RUNNING -> Draw.GREEN;
            case IDLE -> Draw.EDGE_LIGHT;
            case DISABLED, HELD_BY_REDSTONE -> Draw.GREY;
            case TARGET_MISSING, CONNECTOR_GONE -> Draw.RED;
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE -> Draw.AMBER;
        };
    }

    /**
     * A node on an outside column. The same rule the LINKS list follows: name the target, and when
     * this client cannot know the block — the chunk is not loaded, which is the ordinary case for
     * anything far from the player — say <b>where</b> it is instead. Five nodes all reading "not
     * loaded" is a map of one place.
     */
    private static Component targetName(WorkbaySnapshot.Link link) {
        return link.targetBlock()
            .filter(id -> !id.equals(ResourceLocation.withDefaultNamespace("air")))
            .map(FlowPage::name)
            .orElseGet(() -> Component.literal(link.config().target().pos().getX() + " "
                + link.config().target().pos().getZ()));
    }

    private static Component name(ResourceLocation id) {
        if (id == null) {
            return WorkbayScreen.gui("flow.unknown");
        }
        var block = BuiltInRegistries.BLOCK.get(id);
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
            return block.getName();
        }
        var item = BuiltInRegistries.ITEM.get(id);
        return item != net.minecraft.world.item.Items.AIR ? item.getDescription()
            : Component.literal(id.getPath());
    }
}
