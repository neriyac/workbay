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

    // ------------------------------------------------------- what a link carries

    /**
     * <b>Items.</b> Not one block, because no single block means "items" — a grass block meant
     * "the first thing in the registry", a chest means storage and an ingot means metal. What
     * means <em>items</em> is <b>several different ones together</b>, so this is three: a bar of
     * something, a gem, and a pinch of dust, each in its own colour. The colours are the whole
     * reason it works at twelve pixels — in one tint the three shapes merge into a smudge.
     */
    public static final String[] ITEM = {
        "............",
        "............",
        "...22....11.",
        "..2222..1111",
        "..2222..1111",
        "...22....11.",
        "..44444444..",
        ".4444444444.",
        ".3333333333.",
        "..33333333..",
        "............",
        "............",
    };

    /**
     * Items' palette: redstone red, lapis blue, and the ingot in two greys — a body and a lit top
     * face, which is what makes a bar read as <em>an ingot</em> rather than as a line.
     *
     * <p><b>Both of the first draft's mistakes were about size, not colour.</b> The ingot was a
     * three-row parallelogram, which at twelve pixels is a scratch across the bottom of the icon,
     * and the dust was drawn as a cross — the same shape as {@link #PLUS}, a button two inches
     * away on the same screen. Shipped, judged in a real client at the size it is drawn, and
     * redrawn: the three shapes now touch, so they read as one heap of goods rather than as three
     * things that happen to be near each other.
     */
    public static final int[] ITEM_COLOURS =
        { 0xFFC9483C, 0xFF3F63C4, 0xFFA8AEB8, 0xFFDEE3EA };

    /**
     * <b>Energy.</b> A bolt. The one glyph in the genre that needs no caption, and the reason the
     * kink is two rows deep rather than one: at one row it reads as a lightning-shaped scratch.
     */
    public static final String[] ENERGY = {
        "............",
        "......####..",
        ".....####...",
        "....####....",
        "...####.....",
        "..#######...",
        "...#####....",
        "....####....",
        "...####.....",
        "..####......",
        ".####.......",
        "............",
    };

    /**
     * <b>Fluid.</b> A drop: pointed at the top, round at the bottom. Drawn rather than sampled
     * from water, because a link carries whatever fluid it is pointed at and a blue puddle would
     * name one of them.
     */
    public static final String[] FLUID = {
        ".....##.....",
        ".....##.....",
        "....####....",
        "....####....",
        "...######...",
        "..########..",
        ".##########.",
        ".##########.",
        ".##########.",
        "..########..",
        "...######...",
        "............",
    };

    /**
     * <b>Chemical.</b> A round-bottomed flask with something in it. The neck is what separates it
     * from {@link #FLUID} at this size — a bulb alone is a drop drawn upside down.
     */
    public static final String[] CHEMICAL = {
        "...######...",
        "....#..#....",
        "....#..#....",
        "....#..#....",
        "...#....#...",
        "..#......#..",
        ".#........#.",
        ".#........#.",
        ".##########.",
        ".##########.",
        "..########..",
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

    /**
     * The trip to a hosted machine. ART.md: it must read as <b>reaching the machine</b>, not as a
     * doorway -- one button carries "open its screen where you stand" and "walk into the bay"
     * (SPEC.md §5), and a door is wrong for the first of those. So: a distance crossed, and the
     * machine at the end of it: a distance crossed, and the machine's own wall at the far side.
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
     * A redstone torch. Fourth attempt, and the first that reads as itself — because it is the
     * first drawn in <b>two colours</b>. A red head on a brown stick is a redstone torch to
     * anybody who has played the game; the same silhouette in one tint is the trophy that got the
     * torch rejected the first time. A repeater shipped in between and read as an anvil, and
     * redstone dust's cross was indistinguishable from {@link #PLUS} two inches away.
     */
    public static final String[] REDSTONE = {
        "............",
        "....1111....",
        "...111111...",
        "...111111...",
        "....1111....",
        ".....22.....",
        ".....22.....",
        ".....22.....",
        ".....22.....",
        ".....22.....",
        "............",
        "............",
    };

    /** The torch's palette: redstone red, stick brown. */
    public static final int[] REDSTONE_COLOURS = { 0xFFD03A32, 0xFF9A6B3F };

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

    /**
     * A guest. Head and shoulders, solid, with the head outlined and the shoulders filled.
     * Rendered at 1x and 4x against three rejected drafts, the way every icon in this set was: an
     * outlined bust vanishes at twelve pixels, one with arms reads as a robot, and one with a gap
     * between head and shoulders reads as two shapes rather than as a person.
     */
    public static final String[] GUEST = {
        "............",
        "....####....",
        "...##..##...",
        "...##..##...",
        "....####....",
        "...######...",
        "..########..",
        ".##########.",
        ".##########.",
        ".##########.",
        "............",
        "............",
    };

    /** The other half of a stepper. Same bar as PLUS, so the pair reads as one control. */
    public static final String[] MINUS = {
        "............",
        "............",
        "............",
        "............",
        "............",
        "..########..",
        "..########..",
        "............",
        "............",
        "............",
        "............",
        "............",
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
     * Draws one icon with its top-left corner at (x, y). {@code #} takes {@code argb}; the digits
     * {@code 1}..{@code 9} take {@code palette[0]}..{@code palette[8]}.
     *
     * <p><b>A palette is what made three of these icons possible at all.</b> Items is a bar, a gem
     * and a pinch of dust; in one tint those are a smudge, and in three colours they are three
     * things. The redstone torch is the same story — a blob on a stick is a trophy until the blob
     * is red.
     *
     * <p>A palette colour is still <b>scaled by how bright the tint is</b>, so a coloured icon
     * dims exactly where a monochrome one does: a control drawn with {@link Draw#TEXT_FAINT}
     * must not be the one thing on a disabled row that stays at full strength.
     */
    public static void draw(GuiGraphics graphics, String[] icon, int x, int y, int argb,
        int... palette) {
        int lit = Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF));
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int run = -1;
            int runColour = 0;
            for (int col = 0; col <= line.length(); col++) {
                char at = col < line.length() ? line.charAt(col) : '.';
                int colour = at == '#' ? argb
                    : at >= '1' && at <= '9' && at - '1' < palette.length
                        ? dim(palette[at - '1'], lit) : 0;
                if (colour != 0 && run >= 0 && colour != runColour) {
                    graphics.fill(x + run, y + row, x + col, y + row + 1, runColour);
                    run = -1;
                }
                if (colour != 0 && run < 0) {
                    run = col;
                    runColour = colour;
                } else if (colour == 0 && run >= 0) {
                    // One fill per horizontal run rather than per pixel: a solid icon costs a
                    // dozen quads instead of a hundred and forty-four.
                    graphics.fill(x + run, y + row, x + col, y + row + 1, runColour);
                    run = -1;
                }
            }
        }
    }

    /** A palette colour at the tint's own brightness. See {@link #draw}. */
    private static int dim(int argb, int lit) {
        int out = argb & 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            out |= (argb >>> shift & 0xFF) * lit / 255 << shift;
        }
        return out;
    }
}
