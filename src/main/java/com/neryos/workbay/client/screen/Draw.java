package com.neryos.workbay.client.screen;

import com.neryos.workbay.world.FaceConfig;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Every shape the three screens are made of. SPEC.md §4.
 *
 * <p>Drawn from rectangles rather than a background PNG. That is not a shortcut: the layout in
 * SPEC.md §4 is explicitly a starting point rather than a freeze, and a hand-cut texture makes
 * every later nudge a round trip through an image editor. It also means the status colours are one
 * palette in one file instead of pixels somebody has to match by eye.
 */
public final class Draw {
    private Draw() {}

    // The palette. One place, so a status colour cannot mean two things in two screens.
    public static final int PANEL = 0xFF2B2E33;
    public static final int PANEL_LIGHT = 0xFF3A3E45;
    public static final int WELL = 0xFF191B1F;
    public static final int EDGE_LIGHT = 0xFF4E535C;
    public static final int EDGE_DARK = 0xFF14161A;
    public static final int TEXT = 0xFFE6E8EC;
    public static final int TEXT_DIM = 0xFF9298A3;
    public static final int TEXT_FAINT = 0xFF5F6672;

    public static final int GREEN = 0xFF4CC46A;
    public static final int BLUE = 0xFF4C8FD6;
    public static final int AMBER = 0xFFD8A33A;
    public static final int RED = 0xFFD65C4C;
    public static final int GREY = 0xFF565C66;
    public static final int SELECT = 0xFF5AA9E6;

    /** A raised panel: mid fill, light top-left edge, dark bottom-right. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, EDGE_LIGHT);
        g.fill(x, y, x + 1, y + h, EDGE_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DARK);
    }

    /** A sunken well: the opposite bevel, for anything content sits inside. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
        g.fill(x, y, x + w, y + 1, EDGE_DARK);
        g.fill(x, y, x + 1, y + h, EDGE_DARK);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_LIGHT);
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        g.fill(x, y, x + w, y + h, active ? EDGE_LIGHT : hovered ? PANEL_LIGHT : PANEL);
        g.fill(x, y, x + w, y + 1, hovered ? SELECT : EDGE_LIGHT);
        g.fill(x, y, x + 1, y + h, hovered ? SELECT : EDGE_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DARK);
    }

    /** A horizontal fill bar. Empty draws the well alone, so zero never reads as one pixel of full. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, int value, int max, int argb) {
        well(g, x, y, w, h);
        if (max <= 0 || value <= 0) {
            return;
        }
        int filled = Math.max(1, (int) ((long) (w - 2) * Math.min(value, max) / max));
        g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, argb);
    }

    public static int roleColour(FaceConfig.Role role) {
        return switch (role) {
            case INPUT -> GREEN;
            case OUTPUT -> BLUE;
            case NONE -> GREY;
        };
    }

    /** Compact energy: 12.4k rather than 12400, which does not fit and nobody reads anyway. */
    public static String compact(int value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        if (value < 10_000_000) {
            return (value / 100 / 10.0) + "k";
        }
        return (value / 100_000 / 10.0) + "M";
    }
}
