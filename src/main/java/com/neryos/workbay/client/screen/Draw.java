package com.neryos.workbay.client.screen;

import com.neryos.workbay.world.FaceConfig;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Every shape the three screens are made of. SPEC.md §4 and §7.
 *
 * <p>Drawn from rectangles rather than a background PNG. That is not a shortcut: the layout in
 * SPEC.md §4 is explicitly a starting point rather than a freeze, and a hand-cut texture makes
 * every later nudge a round trip through an image editor. It also means the status colours are one
 * palette in one file instead of pixels somebody has to match by eye.
 *
 * <p><b>Nothing outside this class draws a bare rectangle.</b> SPEC.md §7's screen-chrome rules
 * come down to one thing — everything has a light top-left edge and a dark bottom-right one — and
 * they hold only if there is exactly one place that knows how.
 */
public final class Draw {
    private Draw() {}

    // The palette. One place, so a status colour cannot mean two things in two screens.
    public static final int PANEL = 0xFF2B2E33;
    public static final int PANEL_LIGHT = 0xFF3D424A;
    /** A big content area: the links list, the face preview. Content sits inside it. */
    public static final int WELL = 0xFF191B1F;
    /** One slot. Lighter than a well on purpose — a black square reads as a hole, not a slot. */
    public static final int SLOT = 0xFF212429;

    // Far enough apart to survive a dark panel. At one pixel, a one-shade edge is no edge.
    public static final int EDGE_LIGHT = 0xFF6A7280;
    public static final int EDGE_DARK = 0xFF0D0F12;

    /** Two weights, per SPEC.md §7: bright for what the player acts on, dim for labels. */
    public static final int TEXT = 0xFFF0F2F5;
    public static final int TEXT_DIM = 0xFFA6AEBA;
    /** The third exists only for disabled, and disabled must look disabled. */
    public static final int TEXT_FAINT = 0xFF636A76;

    public static final int GREEN = 0xFF4CC46A;
    public static final int BLUE = 0xFF4C8FD6;
    public static final int AMBER = 0xFFD8A33A;
    public static final int RED = 0xFFD65C4C;
    public static final int GREY = 0xFF565C66;
    public static final int SELECT = 0xFF5AA9E6;

    /**
     * The one helper SPEC.md §7 asks for. Raised puts the light edge top-left, sunken flips it;
     * that single difference is what separates a button from a slot at a glance.
     */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean raised) {
        bevel(g, x, y, w, h, raised, EDGE_LIGHT, EDGE_DARK);
    }

    public static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean raised,
        int light, int dark) {
        int topLeft = raised ? light : dark;
        int bottomRight = raised ? dark : light;
        g.fill(x, y, x + w, y + 1, topLeft);
        g.fill(x, y, x + 1, y + h, topLeft);
        g.fill(x, y + h - 1, x + w, y + h, bottomRight);
        g.fill(x + w - 1, y, x + w, y + h, bottomRight);
    }

    /** A raised panel: mid fill, light top-left edge, dark bottom-right. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        bevel(g, x, y, w, h, true);
    }

    /** A sunken well: the opposite bevel, for anything content sits inside. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
        bevel(g, x, y, w, h, false);
    }

    /**
     * One slot. Sunken, mid-grey, with an inner shadow along the top and left — vanilla's
     * treatment of every inventory cell, and the reason an empty one still reads as a slot.
     */
    public static void slot(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT);
        bevel(g, x, y, w, h, false);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33000000);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x33000000);
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        button(g, x, y, w, h, hovered, active, true);
    }

    /**
     * A button. <b>Disabled draws sunken and unlit</b> rather than identical to enabled: the first
     * playable screens had four buttons side by side where one worked, and all four looked alike.
     */
    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered,
        boolean active, boolean enabled) {
        if (!enabled) {
            g.fill(x, y, x + w, y + h, 0xFF24272C);
            bevel(g, x, y, w, h, false, 0xFF34383F, EDGE_DARK);
            return;
        }
        g.fill(x, y, x + w, y + h, active ? EDGE_LIGHT : hovered ? PANEL_LIGHT : PANEL);
        bevel(g, x, y, w, h, true, hovered ? SELECT : EDGE_LIGHT, EDGE_DARK);
    }

    /**
     * A horizontal fill bar.
     *
     * <p>The empty part is the bar's own colour at a fifth, not the bare slot. An outlined
     * rectangle with nothing in it reads as a text field that failed to render, which is exactly
     * how the upgrades screen's power bar looked sitting above "0 / 100000 FE" - so the one reading
     * a player most needs to recognise was the one that did not look like a bar at all. Tinted, the
     * empty channel is visibly the same object as the full one, and zero is a bar that is empty.
     *
     * <p>Zero still fills no pixels: the minimum of one pixel applies only above zero, so a bar
     * that is nearly empty is distinguishable from one that is empty.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, int value, int max, int argb) {
        slot(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, (argb & 0x00FFFFFF) | 0x33000000);
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

    /** The full figure, grouped. For tooltips, where there is room and the player asked. */
    public static String exact(int value) {
        return String.format("%,d", value);
    }
}
