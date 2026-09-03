package com.neryos.workbay.client.screen;

import com.neryos.workbay.world.FaceConfig;
import net.minecraft.core.Direction;
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

    /**
     * The isometric bay cube from SPEC.md §4: three faces visible, each filled by its role and
     * labelled with its direction letter, each one clickable.
     *
     * <p>Drawn as scanlines rather than as a texture because the fill colour is per face and per
     * resource — a texture would need twenty-seven variants of the same cube.
     */
    public static void isoCube(GuiGraphics g, net.minecraft.client.gui.Font font, int cx, int cy,
        int size, FaceConfig faces, com.neryos.workbay.bus.BusConfig.Resource resource,
        Direction top, Direction left, Direction right) {

        int halfW = size;
        int halfH = size / 2;

        // Top face: a rhombus centred on (cx, cy - halfH).
        rhombus(g, cx, cy - halfH, halfW, halfH, roleColour(faces.role(resource, top)));
        // Left and right faces: the same rhombus sheared down into a parallelogram.
        parallelogram(g, cx - halfW, cy - halfH, halfW, halfH, size, true,
            roleColour(faces.role(resource, left)));
        parallelogram(g, cx, cy, halfW, halfH, size, false,
            roleColour(faces.role(resource, right)));

        letter(g, font, cx, cy - halfH - 3, top);
        letter(g, font, cx - halfW / 2 - 2, cy + size / 2 - 4, left);
        letter(g, font, cx + halfW / 2 - 2, cy + size / 2 - 4, right);
    }

    /** Where each of the cube's three visible faces was drawn, for hit testing a click. */
    public static boolean inTopFace(int cx, int cy, int size, double mx, double my) {
        double dx = Math.abs(mx - cx) / (double) size;
        double dy = Math.abs(my - (cy - size / 2.0)) / (size / 2.0);
        return dx + dy <= 1.0;
    }

    public static boolean inLeftFace(int cx, int cy, int size, double mx, double my) {
        return mx >= cx - size && mx < cx && my >= cy - size / 2.0 && my < cy + size
            && !inTopFace(cx, cy, size, mx, my) && withinShear(cx - size, cy - size / 2.0, size, mx, my, true);
    }

    public static boolean inRightFace(int cx, int cy, int size, double mx, double my) {
        return mx >= cx && mx < cx + size && my >= cy - size / 2.0 && my < cy + size
            && !inTopFace(cx, cy, size, mx, my) && withinShear(cx, cy, size, mx, my, false);
    }

    private static boolean withinShear(double x0, double y0, int size, double mx, double my, boolean leftward) {
        double t = (mx - x0) / size;
        double top = leftward ? y0 + (size / 2.0) * t : y0 - (size / 2.0) * t;
        return my >= top && my <= top + size;
    }

    public static int roleColour(FaceConfig.Role role) {
        return switch (role) {
            case INPUT -> GREEN;
            case OUTPUT -> BLUE;
            case NONE -> GREY;
        };
    }

    private static void rhombus(GuiGraphics g, int cx, int cy, int halfW, int halfH, int argb) {
        for (int row = -halfH; row < halfH; row++) {
            int width = (int) (halfW * (1.0 - Math.abs(row + 0.5) / halfH));
            g.fill(cx - width, cy + row, cx + width, cy + row + 1, argb);
        }
        outlineRhombus(g, cx, cy, halfW, halfH);
    }

    private static void outlineRhombus(GuiGraphics g, int cx, int cy, int halfW, int halfH) {
        for (int row = -halfH; row < halfH; row++) {
            int width = (int) (halfW * (1.0 - Math.abs(row + 0.5) / halfH));
            g.fill(cx - width, cy + row, cx - width + 1, cy + row + 1, EDGE_DARK);
            g.fill(cx + width - 1, cy + row, cx + width, cy + row + 1, EDGE_DARK);
        }
    }

    /** One side face: a column of rows whose top edge slopes, shaded darker than the top face. */
    private static void parallelogram(GuiGraphics g, int x, int y, int halfW, int halfH, int height,
        boolean leftward, int argb) {
        int shaded = shade(argb, leftward ? 0.78F : 0.58F);
        for (int col = 0; col < halfW; col++) {
            double t = col / (double) halfW;
            int top = (int) (y + (leftward ? halfH * t : halfH * -t));
            g.fill(x + col, top, x + col + 1, top + height, shaded);
        }
        // The vertical seam where the two side faces meet, so the cube reads as a solid.
        g.fill(leftward ? x + halfW - 1 : x, y + (leftward ? halfH : 0),
            leftward ? x + halfW : x + 1, y + (leftward ? halfH : 0) + height, EDGE_DARK);
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void letter(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y,
        Direction face) {
        g.drawString(font, String.valueOf(Character.toUpperCase(face.getName().charAt(0))),
            x, y, 0xFF000000, false);
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
