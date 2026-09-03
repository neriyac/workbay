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
    private static final int HEIGHT = 232;

    private static final int LEFT_X = 14;
    private static final int MID_X = 128;
    private static final int RIGHT_X = 236;
    private static final int NODE_W = 70;
    private static final int NODE_H = 18;
    private static final int TOP_Y = 60;
    private static final int PITCH = 22;

    FlowPage(WorkbayScreen screen) {
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
        header(g, mouseX, mouseY, "FLOW");
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        g.drawString(font, "pulled from", x(LEFT_X), y(48), Draw.TEXT_FAINT, false);
        g.drawString(font, "bays", x(MID_X + 24), y(48), Draw.TEXT_FAINT, false);
        g.drawString(font, "sent to", x(RIGHT_X), y(48), Draw.TEXT_FAINT, false);

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
            g.drawString(font, WorkbayScreen.gui("flow.empty").getString(), x(MID_X - 20), y(TOP_Y + 4),
                Draw.TEXT_FAINT, false);
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
                node(g, x(RIGHT_X), py, name(link.targetBlock().orElse(null)), Draw.WELL);
                arrow(g, x(MID_X) + NODE_W, rowY + NODE_H / 2, x(RIGHT_X), py + NODE_H / 2, colour);
            } else {
                int py = y(TOP_Y + inRow++ * PITCH);
                node(g, x(LEFT_X), py, name(link.targetBlock().orElse(null)), Draw.WELL);
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
        g.drawString(screen.font(), screen.font().plainSubstrByWidth(label.getString(), NODE_W - 8),
            px + 4, py + 5, Draw.TEXT_DIM, false);
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

    private void legend(GuiGraphics g) {
        var font = screen.font();
        int py = y(HEIGHT - 34);
        g.fill(x(14), py + 3, x(30), py + 4, Draw.GREEN);
        g.drawString(font, WorkbayScreen.gui("flow.legend.out").getString(), x(34), py, Draw.TEXT_DIM, false);

        int py2 = py + 12;
        for (int i = 0; i < 16; i += 6) {
            g.fill(x(14 + i), py2 + 3, x(14 + i + 3), py2 + 4, Draw.BLUE);
        }
        g.drawString(font, WorkbayScreen.gui("flow.legend.internal").getString(), x(34), py2,
            Draw.TEXT_DIM, false);

        g.fill(x(174), py + 3, x(190), py + 4, Draw.AMBER);
        g.drawString(font, WorkbayScreen.gui("flow.legend.stalled").getString(), x(194), py,
            Draw.TEXT_DIM, false);
    }

    private static int colourFor(BusRunner.BusStatus status) {
        return switch (status) {
            case RUNNING -> Draw.GREEN;
            case IDLE -> Draw.EDGE_LIGHT;
            case DISABLED -> Draw.GREY;
            case TARGET_MISSING, CONNECTOR_GONE -> Draw.RED;
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE,
                 RESOURCE_NOT_CARRIED -> Draw.AMBER;
        };
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
