package com.neryos.workbay.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The mod's whole icon set, as 12x12 pixel grids. SPEC.md §4.
 *
 * <p>SPEC.md calls for an icon atlas rather than text glyphs, and this is that atlas with the PNG
 * left out: same uniform 12x12 grid, drawn straight into the screen. Being data rather than a
 * texture buys two things a file could not — every icon tints to whatever colour the row needs, and
 * changing one is a line of text rather than a round trip through an image editor.
 *
 * <p>{@code #} is the icon, {@code .} is nothing. One string per row, twelve rows, twelve columns.
 */
public final class WBIcons {
    private WBIcons() {}

    public static final String[] UPGRADE = {
        "............",
        ".....##.....",
        "....####....",
        "...######...",
        "..###..###..",
        ".##......##.",
        "....####....",
        "....####....",
        "....####....",
        "....####....",
        "............",
        "............",
    };

    public static final String[] MAP = {
        "............",
        ".###....###.",
        ".###....###.",
        ".###....###.",
        "...##..##...",
        ".....##.....",
        ".....##.....",
        "...##..##...",
        ".###....###.",
        ".###....###.",
        ".###....###.",
        "............",
    };

    public static final String[] LOCK = {
        "............",
        "....####....",
        "...##..##...",
        "...##..##...",
        "...##..##...",
        "..########..",
        "..########..",
        "..###..###..",
        "..###..###..",
        "..########..",
        "..########..",
        "............",
    };

    public static final String[] UNLOCK = {
        "............",
        "......####..",
        ".....##..##.",
        ".....##..##.",
        ".....##..##.",
        "..########..",
        "..########..",
        "..###..###..",
        "..###..###..",
        "..########..",
        "..########..",
        "............",
    };

    /** Opens the machine's own screen. A window, deliberately not a machine. */
    public static final String[] SCREEN = {
        "............",
        ".##########.",
        ".##########.",
        ".#........#.",
        ".#.######.#.",
        ".#.######.#.",
        ".#.######.#.",
        ".#........#.",
        ".##########.",
        "............",
        "............",
        "............",
    };

    public static final String[] EJECT = {
        "............",
        ".....##.....",
        "....####....",
        "...######...",
        "..########..",
        ".##########.",
        "............",
        "............",
        ".##########.",
        ".##########.",
        "............",
        "............",
    };

    public static final String[] RENAME = {
        "............",
        "........###.",
        ".......####.",
        "......####..",
        ".....####...",
        "....####....",
        "...####.....",
        "..####......",
        ".####.......",
        ".###........",
        ".##.........",
        "............",
    };

    public static final String[] REDSTONE = {
        "............",
        ".....##.....",
        "....####....",
        "...######...",
        "..###..###..",
        "..##....##..",
        "..###..###..",
        "...######...",
        "....####....",
        ".....##.....",
        "............",
        "............",
    };

    /** Items: a crate. */
    public static final String[] ITEMS = {
        "............",
        "..########..",
        "..#......#..",
        "..#.####.#..",
        "..#.####.#..",
        "..#.####.#..",
        "..#.####.#..",
        "..#.####.#..",
        "..#......#..",
        "..########..",
        "............",
        "............",
    };

    /** Fluids: a droplet. */
    public static final String[] FLUIDS = {
        "............",
        ".....##.....",
        ".....##.....",
        "....####....",
        "...######...",
        "..########..",
        "..########..",
        "..########..",
        "...######...",
        "....####....",
        "............",
        "............",
    };

    /** Energy: a bolt. */
    public static final String[] ENERGY = {
        "............",
        ".......###..",
        "......###...",
        ".....###....",
        "....######..",
        "...######...",
        "......###...",
        ".....###....",
        "....###.....",
        "...###......",
        "............",
        "............",
    };

    /** Insert: into the bar on the right. */
    public static final String[] INSERT = {
        "............",
        "............",
        "......##.##.",
        ".......####.",
        "..########..",
        "..########..",
        "..########..",
        ".......####.",
        "......##.##.",
        "............",
        "............",
        "............",
    };

    /** Extract: out of the bar on the left. */
    public static final String[] EXTRACT = {
        "............",
        "............",
        ".##.##......",
        ".####.......",
        "..########..",
        "..########..",
        "..########..",
        ".####.......",
        ".##.##......",
        "............",
        "............",
        "............",
    };

    public static final String[] GEAR = {
        "............",
        "...#.##.#...",
        "...######...",
        "..########..",
        ".###....###.",
        ".##......##.",
        ".##......##.",
        ".###....###.",
        "..########..",
        "...######...",
        "...#.##.#...",
        "............",
    };

    /** Filter: a funnel. Also the filter-view button in the LINKS header. */
    public static final String[] FILTER = {
        "............",
        ".##########.",
        ".##########.",
        "..########..",
        "...######...",
        "....####....",
        "....####....",
        "....####....",
        "....####....",
        "............",
        "............",
        "............",
    };

    public static final String[] SORT = {
        "............",
        ".##########.",
        ".##########.",
        "............",
        ".########...",
        ".########...",
        "............",
        ".######.....",
        ".######.....",
        "............",
        ".####.......",
        ".####.......",
    };

    public static final String[] PLUS = {
        "............",
        "............",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "..########..",
        "..########..",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "............",
        "............",
    };

    public static final String[] BACK = {
        "............",
        "............",
        ".....##.....",
        "....##......",
        "...##.......",
        "..##########",
        "..##########",
        "...##.......",
        "....##......",
        ".....##.....",
        "............",
        "............",
    };

    /** Draws one icon with its top-left corner at (x, y), tinted {@code argb}. */
    public static void draw(GuiGraphics graphics, String[] icon, int x, int y, int argb) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int run = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && run < 0) {
                    run = col;
                } else if (!on && run >= 0) {
                    // One fill per horizontal run rather than per pixel: a solid icon costs a
                    // dozen quads instead of a hundred and forty-four.
                    graphics.fill(x + run, y + row, x + col, y + row + 1, argb);
                    run = -1;
                }
            }
        }
    }
}
