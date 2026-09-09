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

    /**
     * <b>An icon that is a real item rather than a drawn grid.</b>
     *
     * <p>The grids stay for everything that means an <em>action</em> or a <em>direction</em>: no
     * object means "down", "yes" or "sort", and a picture of one that happens to be near the idea
     * is the trick that put a grass block on "items". What an object <em>can</em> mean is a noun,
     * and where the noun exists in the game already the game's own picture beats a redrawing of
     * it — a player has seen a name tag ten thousand times and has never seen our twelve pixels.
     * Neriya's call, after the items icon.
     *
     * <p>Carried as a one-element {@code String[]} so that not one of the thirty call sites, two
     * button helpers or the palette argument has to change: a row of pixels can never begin with
     * {@code @}, so {@link #draw} can tell the two apart in its first line and everything else in
     * this file stays what it was.
     */
    private static String[] item(String id) {
        return new String[] { "@" + id };
    }

    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> STACKS =
        new java.util.HashMap<>();

    /**
     * One item sprite, {@code side} pixels wide instead of the sixteen it is drawn at, dimmed to
     * the panel when the colour it was asked for is a faint one.
     *
     * <p>An item sprite cannot be tinted -- {@code renderItem} goes through the item renderer and
     * never sees {@code GuiGraphics#setColor} -- so "disabled" is the wash every other disabled
     * thing on these screens uses, laid over the top.
     */
    public static void sprite(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack,
        int x, int y, int side, boolean full) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(side / 16.0F, side / 16.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        if (!full) {
            Draw.disabled(graphics, x, y, side, side);
        }
    }

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

    /** Opens Bay View. A window, deliberately not a machine. */
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

    /**
     * <b>Rename.</b> The thing you rename with, and the one object in the game that means exactly that.
     */
    public static final String[] RENAME = item("minecraft:name_tag");

    /**
     * <b>Redstone.</b> The genre's own picture of a redstone gate; EnderIO and Mekanism both use it. The mode is carried by the button lighting and by the icon dimming when the bay ignores redstone altogether.
     */
    public static final String[] REDSTONE = item("minecraft:redstone_torch");

    /**
     * <b>Copy.</b> Book and quill: the pair reads as copy/paste the way a floppy disk reads as save, and the quill is what tells it from PASTE on the button beside it.
     */
    public static final String[] COPY = item("minecraft:writable_book");

    /**
     * <b>Paste.</b> The written half of that pair.
     */
    public static final String[] PASTE = item("minecraft:written_book");

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

    /**
     * <b>Filter.</b> A funnel. The only vanilla object that means 'some of this goes through'.
     */
    public static final String[] FILTER = item("minecraft:hopper");

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
     * <b>Door.</b> The rooms tab, and a room's way out is a door.
     */
    public static final String[] DOOR = item("minecraft:iron_door");

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
     * <b>Guest.</b> Rendered as the block model, which is a head: the one icon here that is 3D and is better for it.
     */
    public static final String[] GUEST = item("minecraft:player_head");

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
        // A row of pixels cannot start with '@'; an item icon is nothing else. See #item.
        if (icon.length == 1 && icon[0].startsWith("@")) {
            sprite(graphics, STACKS.computeIfAbsent(icon[0], id ->
                new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(id.substring(1))))),
                x, y, 12, lit >= 0xC0);
            return;
        }
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
