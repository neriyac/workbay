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
