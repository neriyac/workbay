package com.neryos.workbay.client.screen;

import com.neryos.workbay.world.FaceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Every shape the three screens are made of. SPEC.md §4 and §7.
 *
 * <p>Drawn from rectangles rather than a background PNG. That is not a shortcut: the layout in
 * SPEC.md §4 is explicitly a starting point rather than a freeze, and a hand-cut texture makes
 * every later nudge a round trip through an image editor. It also means the status colours are one
 * palette in one file instead of pixels somebody has to match by eye.
 *
 * <p><b>Nothing outside this class draws a bare rectangle, and nothing outside it draws a
 * string.</b> SPEC.md §7's screen-chrome rules come down to one thing — everything has a light
 * top-left edge and a dark bottom-right one — and they hold only if there is exactly one place that
 * knows how. {@link #text} is the same argument applied to words: see its comment.
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
    /**
     * The inside of a gauge. Far darker than {@link #WELL} and than the panel, because a gauge is
     * the one container here whose <em>contents</em> are the reading — the tube has to get out of
     * the way of two pixels of fluid.
     */
    public static final int TUBE = 0xFF0D1015;
    /** The line along the top of whatever a gauge holds. Turns a sliver into a surface. */
    public static final int SURFACE = 0x99FFFFFF;
    /** Quarter marks, light enough to survive a bright fluid and a dark tube alike. */
    public static final int GRADUATION = 0x40FFFFFF;

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

    /**
     * The wash over something present but not usable. One colour, one call, so "unavailable" looks
     * the same everywhere — SPEC.md §7's rule that a disabled thing must not read as an enabled one.
     */
    public static void disabled(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x66101216);
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

    /**
     * A vertical gauge: a tall, narrow tube filled from the bottom.
     *
     * <p>Vertical because that is what a buffer looks like everywhere else in the genre, and a
     * player reads "half full" off a tall column without reading anything. A ten-pixel horizontal
     * strip with a number beside it reads as a progress bar at best and as nothing at worst.
     *
     * <p>Three things make a <em>low</em> level still read as a level, and all three come from
     * Mekanism's {@code GuiGauge}: the tube is much darker than the panel, so it is visibly a
     * container rather than a shadow; {@link #gaugeGlass} draws quarter graduations over the
     * contents, so an almost-empty tube is still a scale with something at the bottom of it; and
     * the fill carries a bright line along its surface, so two pixels of content read as a
     * surface rather than as an edge.
     */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, int value, int max,
        int argb) {
        gaugeTube(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, (argb & 0x00FFFFFF) | 0x33000000);
        if (max > 0 && value > 0) {
            int top = y + h - 1 - fill(h, value, max);
            g.fill(x + 1, top, x + w - 1, y + h - 1, argb);
            g.fill(x + 1, top, x + w - 1, top + 1, SURFACE);
        }
        gaugeGlass(g, x, y, w, h);
    }

    /**
     * The same gauge, filled with the fluid's own still texture. EnderIO's
     * {@code FluidStackWidget}, which is public domain and does exactly this.
     *
     * <p>Not {@link #gauge} tinted with {@code getTintColor}. Water's tint is white - the blue is
     * in the texture - so a tinted column draws every ordinary fluid as a white smear and says
     * nothing about which one it is. The texture is what a player recognises.
     *
     * <p>Tiled upwards at the sprite's native 16 pixels rather than stretched to fit, and clipped
     * to the fill line, so a gauge that is a third full shows a third of a real fluid rather than a
     * whole one squashed. The tube's inside is sixteen pixels wide for that reason: at fourteen,
     * every tile lost two columns of the sprite.
     */
    /**
     * One fluid, filling a square. The filter panel's answer to "which fluid is this slot", where
     * an item slot would draw an item.
     *
     * <p>The still texture, tinted, exactly as {@link #fluidGauge} draws it — a fluid a player
     * recognises in a gauge and does not recognise in a filter slot is two names for one thing.
     */
    public static void fluidIcon(GuiGraphics g, int x, int y, int size,
        net.minecraft.world.level.material.Fluid fluid) {
        var stack = new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000);
        var extensions = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
            .of(fluid);
        var sprite = Minecraft.getInstance()
            .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .apply(extensions.getStillTexture(stack));
        int tint = extensions.getTintColor(stack);
        g.setColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F,
            (tint & 0xFF) / 255.0F, 1.0F);
        g.blit(x, y, 0, size, size, sprite);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void fluidGauge(GuiGraphics g, int x, int y, int w, int h,
        net.neoforged.neoforge.fluids.FluidStack fluid, int capacity) {
        gaugeTube(g, x, y, w, h);
        if (fluid.isEmpty() || capacity <= 0) {
            gaugeGlass(g, x, y, w, h);
            return;
        }
        int filled = fill(h, fluid.getAmount(), capacity);
        var extensions = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
            .of(fluid.getFluid());
        var sprite = Minecraft.getInstance()
            .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .apply(extensions.getStillTexture(fluid));
        int tint = extensions.getTintColor(fluid);
        g.setColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F,
            (tint & 0xFF) / 255.0F, 1.0F);
        int bottom = y + h - 1;
        g.enableScissor(x + 1, bottom - filled, x + w - 1, bottom);
        for (int up = 0; up < filled; up += 16) {
            g.blit(x + 1, bottom - up - 16, 0, 16, 16, sprite);
        }
        g.disableScissor();
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.fill(x + 1, bottom - filled, x + w - 1, bottom - filled + 1, SURFACE);
        gaugeGlass(g, x, y, w, h);
    }

    /**
     * How many pixels of a gauge {@code value} fills. <b>Two at the minimum, not one.</b> A single
     * lit row sitting against the tube's own bottom edge is an edge, not a level - which is how
     * 2,000 of 32,000 came to read as an empty box.
     */
    private static int fill(int h, int value, int max) {
        return Math.clamp((long) (h - 2) * value / max, 2, h - 2);
    }

    /**
     * The empty tube: a recess much darker than the panel, with the sunken bevel every other
     * container on these screens has.
     *
     * <p>Darker than {@link #WELL}, which was the fault. A well one shade off the panel is a shape
     * the eye does not separate from it, so a gauge with a little in it read as a flat grey box
     * with nothing in it at all.
     */
    private static void gaugeTube(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, TUBE);
        bevel(g, x, y, w, h, false);
    }

    /**
     * The glass, drawn <b>over</b> the contents: quarter graduations, a highlight down the left
     * edge and a shadow down the right and across the top.
     *
     * <p>Over rather than under is the whole point - it is what makes the fluid look like it is
     * inside something. Mekanism does the same thing with a one-bit overlay texture
     * ({@code gui/gauge/standard.png}: a half-width tick every six pixels and a full-width one at
     * the midpoint); this draws the same idea in code because this mod has no gauge texture and
     * quarters survive a gauge of any height, which a fixed six-pixel pitch does not.
     */
    private static void gaugeGlass(GuiGraphics g, int x, int y, int w, int h) {
        int left = x + 1;
        int right = x + w - 1;
        int top = y + 1;
        int bottom = y + h - 1;
        for (int quarter = 1; quarter < 4; quarter++) {
            int ty = bottom - (h - 2) * quarter / 4;
            g.fill(left, ty, quarter == 2 ? right : left + (w - 2) / 2, ty + 1, GRADUATION);
        }
        g.fill(left, top, left + 1, bottom, 0x26FFFFFF);
        g.fill(right - 1, top, right, bottom, 0x33000000);
        g.fill(left, top, right, top + 1, 0x33000000);
    }

    // ------------------------------------------------------------------ text

    /**
     * <b>The one place this mod draws a string, and it is always told the width it has.</b>
     *
     * <p>Text running off its box was fixed twice and came back twice, because both fixes audited a
     * list of call sites. Auditing a list closes the list; it does not close the hole the list came
     * out of. The hole was {@code GuiGraphics#drawString}, which takes a position and no width, so
     * every one of forty-odd call sites was free to be wrong on its own and nothing anywhere could
     * tell. Here a width is not optional, so a string that does not fit is <em>cut</em> rather than
     * spilled — that outcome is structural, not something a call site remembers to ask for.
     * {@code tools/check-text.sh} fails the build on any {@code drawString} outside this file, so
     * the next call site cannot opt out either.
     *
     * <p><b>And a cut string is still a fault</b> — the player cannot read it — so with the F3
     * debug overlay on, every box is outlined: <span>red where the string was cut</span>, faint
     * cyan where it fit. One screenshot of a screen then shows every overflow at once, and a box
     * drawn wider than the panel it sits in shows up in the same picture, which is the fault
     * clipping alone cannot catch. F3 rather than a config or a keybind: it is already the
     * game's "show me the internals" toggle, and it costs one field read. Vanilla only accepts F3
     * with no screen open, so it is pressed in the world and the screen opened after.
     *
     * @return true when the whole string fitted; false when it was cut, so the caller can offer
     *         the whole of it in a tooltip
     */
    public static boolean text(GuiGraphics g, Font font, String s, int px, int py, int room,
        int colour) {
        return draw(g, font, s, px, px, py, room, colour);
    }

    /** Right-aligned: the box ends at {@code rightX}, and a cut string still starts inside it. */
    public static boolean textRight(GuiGraphics g, Font font, String s, int rightX, int py,
        int room, int colour) {
        return draw(g, font, s, rightX - room, rightX - Math.min(room, font.width(s)), py, room,
            colour);
    }

    /** Centred on {@code centreX}, inside a box of {@code room} centred on the same point. */
    public static boolean textCentre(GuiGraphics g, Font font, String s, int centreX, int py,
        int room, int colour) {
        return draw(g, font, s, centreX - room / 2,
            centreX - Math.min(room, font.width(s)) / 2, py, room, colour);
    }

    /**
     * {@code boxX} is where the width the string was <em>given</em> starts; {@code textX} is where
     * the string itself lands inside it. They differ for anything not left-aligned, and the outline
     * must follow the box rather than the text — an outline drawn from the text would sit further
     * right than the space the string was actually allowed, which is the one thing the outline
     * exists to show. It said so on its own first screenshot.
     */
    private static boolean draw(GuiGraphics g, Font font, String s, int boxX, int textX, int py,
        int room, int colour) {
        boolean fits = font.width(s) <= room;
        g.drawString(font, fits ? s
            : font.plainSubstrByWidth(s, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS,
            textX, py, colour, false);
        box(g, boxX, py, room, font.lineHeight, fits);
        return fits;
    }

    /**
     * A sentence too long for one line, wrapped to {@code room} and drawn down from {@code py}.
     * The wrapped form is the only kind of long text on these screens that is not a fault, so it
     * says so here rather than each caller splitting by hand and hoping the widths match.
     */
    public static void wrapped(GuiGraphics g, Font font, Component text, int px, int py, int room,
        int colour) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, room);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), px, py + i * 10, colour, false);
            box(g, px, py + i * 10, room, font.lineHeight, true);
        }
    }

    /**
     * A tooltip, wrapped and with its first line bolded.
     *
     * <p>This mod's tooltips are written as full sentences (SPEC.md §4: "tooltips are where this
     * mod's text budget is spent"), and {@code renderComponentTooltip} does not wrap — one of them
     * unbroken is half the screen wide. Every tooltip is (title, explanation), the convention
     * vanilla's own item tooltips use, so the bold is here rather than at each call site.
     *
     * <p>Here rather than on one screen because there are two screens that draw tooltips, and a
     * rule that lives on one of them is a rule the other gets wrong.
     */
    public static List<net.minecraft.util.FormattedCharSequence> tooltip(Font font,
        List<Component> lines) {
        List<net.minecraft.util.FormattedCharSequence> wrapped = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Component line = i == 0
                ? lines.get(0).copy().withStyle(style -> style.withBold(true))
                : lines.get(i);
            wrapped.addAll(font.split(line, TOOLTIP_WIDTH));
        }
        return wrapped;
    }

    private static final int TOOLTIP_WIDTH = 200;

    private static final String ELLIPSIS = "…";

    /** The debug outline. Costs one boolean read per string when it is off, which it normally is. */
    private static void box(GuiGraphics g, int px, int py, int room, int h, boolean fits) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            return;
        }
        int colour = fits ? 0x5533D6D6 : 0xFFFF0000;
        int top = py - 1;
        int bottom = py + h;
        g.fill(px, top, px + room, top + 1, colour);
        g.fill(px, bottom, px + room, bottom + 1, colour);
        g.fill(px, top, px + 1, bottom + 1, colour);
        g.fill(px + room - 1, top, px + room, bottom + 1, colour);
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
