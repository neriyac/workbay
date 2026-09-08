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

    /**
     * The flow map. Was four arms meeting in the middle, which reads as "expand", not as a map --
     * a flow map is <b>things joined by routes</b>, so this is two sources routed into one target.
     * The nodes are hollow 3x3 boxes rather than solid 2x2 ones: filled squares that small merge
     * into the lines they are attached to and the whole icon reads as a pitchfork.
     */
public static final String[] MAP = {
        "............",
        "###.....###.",
        "#.#.....#.#.",
        "###.....###.",
        "..#.......#.",
        "..#########.",
        ".....#......",
        ".....#......",
        "....###.....",
        "....#.#.....",
        "....###.....",
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

    /**
     * The trip to a hosted machine. ART.md: it must read as <b>reaching the machine</b>, not as a
     * doorway -- one button carries "open its screen where you stand" and "walk into the bay"
     * (SPEC.md §5), and a door is wrong for the first of those. So: a distance crossed, and the
     * machine at the end of it. {@link #SCREEN} is the window; this is the journey.
     */
public static final String[] ENTER = {
        "............",
        "............",
        ".........###",
        "....#....###",
        "....##...###",
        "########.###",
        "########.###",
        "....##...###",
        "....#....###",
        ".........###",
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
        ".##.........",
        "............",
        ".##########.",
    };

    /**
     * A repeater: two posts on a base plate. Third attempt, and the first that reads as itself.
     * A ring drew as a gemstone, a torch drew as a trophy -- a blob on a stem is a blob on a stem
     * at twelve pixels -- and redstone dust's cross was indistinguishable from {@link #PLUS},
     * which is a button that means "add" two inches away on the same screen. This silhouette
     * looks like nothing else in the set.
     */
    public static final String[] REDSTONE = {
        "............",
        "...##...##..",
        "...##...##..",
        "...##...##..",
        "...##...##..",
        ".##########.",
        ".##########.",
        ".##########.",
        "............",
        "............",
        "............",
        "............",
    };

    /** Items: a crate. */
    /** Two sheets, the back one offset — the copy idiom every desktop has taught since 1984. */
    public static final String[] COPY = {
        "............",
        "..#######...",
        "..#.....#...",
        "..#..######.",
        "..#..#....#.",
        "..#..#....#.",
        "..####....#.",
        ".....#....#.",
        ".....#....#.",
        ".....######.",
        "............",
        "............",
    };

    /** A clipboard with its clip. Deliberately unlike COPY at a glance, not a mirrored twin. */
    public static final String[] PASTE = {
        "............",
        "....####....",
        "..#.####.#..",
        "..##########",
        "..#........#",
        "..#..####..#",
        "..#........#",
        "..#..####..#",
        "..#........#",
        "..##########",
        "............",
        "............",
    };

    /** Into the machine: the arrow passes through the wall of the box, which is the only part of
     *  the picture that separates it from {@link #EXTRACT}. */
public static final String[] INSERT = {
        "............",
        ".....#######",
        ".....#.....#",
        "..#..#.....#",
        "..##.#.....#",
        "#####......#",
        "#####......#",
        "..##.#.....#",
        "..#..#.....#",
        ".....#.....#",
        ".....#######",
        "............",
    };

    /** Out of the machine, and the mirror of {@link #INSERT} on purpose: the pair only works if
     *  the two are the same drawing seen from opposite sides. */
public static final String[] EXTRACT = {
        "............",
        "#######.....",
        "#.....#.....",
        "#.....#..#..",
        "#.....#..##.",
        "#......#####",
        "#......#####",
        "#.....#..##.",
        "#.....#..#..",
        "#.....#.....",
        "#######.....",
        "............",
    };

    /** Out of the machine, into the target. The machine is the left of a row, the target its right. */
    public static final String[] ARROW_RIGHT = {
        "............",
        "............",
        ".......#....",
        "........#...",
        ".#########..",
        ".##########.",
        ".##########.",
        ".#########..",
        "........#...",
        ".......#....",
        "............",
        "............",
    };

    /**
     * Down the exchange column: what goes in the top slot comes out of the bottom one. The only
     * arrow on these screens that means item flow rather than which end of a link is which.
     */
    public static final String[] ARROW_DOWN = {
        "............",
        "....####....",
        "....####....",
        "....####....",
        "....####....",
        "....####....",
        ".##########.",
        "..########..",
        "...######...",
        "....####....",
        ".....##.....",
        "............",
    };

    /** Out of the target, into the machine. */
    public static final String[] ARROW_LEFT = {
        "............",
        "............",
        "....#.......",
        "...#........",
        "..#########.",
        ".##########.",
        ".##########.",
        "..#########.",
        "...#........",
        "....#.......",
        "............",
        "............",
    };

    public static final String[] CHECK = {
        "............",
        "............",
        "..........#.",
        ".........##.",
        "........##..",
        ".#.....##...",
        ".##...##....",
        "..##.##.....",
        "...###......",
        "....#.......",
        "............",
        "............",
    };

    public static final String[] CROSS = {
        "............",
        "............",
        "..#......#..",
        "..##....##..",
        "...##..##...",
        "....####....",
        "....####....",
        "...##..##...",
        "..##....##..",
        "..#......#..",
        "............",
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


    /**
     * ROOMS. A doorway with a floor line, which is what the page is about — somewhere to walk into.
     * Not a house or a box: both read as storage, and this mod already draws a bay as a box.
     */
    public static final String[] DOOR = {
        "............",
        "..########..",
        "..##....##..",
        "..##....##..",
        "..##....##..",
        "..##....##..",
        "..##....##..",
        "..##.##.##..",
        "..##....##..",
        "..##....##..",
        "############",
        "............",
    };

    /** The room anchor toggle: a shackle over two flukes, read at twelve pixels as "held down". */
    public static final String[] ANCHOR = {
        "....####....",
        "...##..##...",
        "...##..##...",
        "....####....",
        ".....##.....",
        "..########..",
        ".....##.....",
        ".....##.....",
        "##...##...##",
        "##...##...##",
        ".##########.",
        "...######...",
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

    /**
     * On/off. The universal power symbol, and deliberately <b>not</b> a tick: a tick in the picker
     * means "selected for adding" and a tick on a row meant "enabled", which is one control saying
     * two things. SPEC.md §7.
     */
    public static final String[] POWER = {
        "............",
        ".....##.....",
        ".....##.....",
        "..##.##.##..",
        ".##..##..##.",
        ".##......##.",
        ".##......##.",
        ".##......##.",
        "..##....##..",
        "...######...",
        "............",
        "............",
    };

    /**
     * Return to the page you came from. Was a left arrow, which is what {@link #ARROW_LEFT} is:
     * two controls drawing one picture on one screen. A return arrow says <i>where</i> it goes
     * back to, which a bare arrow does not.
     */
public static final String[] BACK = {
        "............",
        "..........##",
        "..........##",
        "..........##",
        "..#.......##",
        ".##.......##",
        "###########.",
        "###########.",
        ".##.........",
        "..#.........",
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
