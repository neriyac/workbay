package com.neryos.workbay.client.screen;

import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One of the three screens. SPEC.md §4.
 *
 * <p>Each page owns its own size, because they differ, and the header band they share is drawn here
 * so the three navigation buttons cannot drift apart between them.
 */
abstract class WorkbayPage {

    protected final WorkbayScreen screen;

    WorkbayPage(WorkbayScreen screen) {
        this.screen = screen;
    }

    abstract int width();

    abstract int height();

    abstract void render(GuiGraphics graphics, int mouseX, int mouseY);

    /**
     * True when Escape closed something this page had open, rather than the screen. Only ROOMS
     * has one — the settings window.
     */
    boolean escaped() {
        return false;
    }

    /** True when this page consumed the scroll. Only the LINKS list does. */
    boolean scrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** True when the press started a block turn, so the hit list must not also fire. */
    boolean mousePressed(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseDragged(double dragX, double dragY) {
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        return false;
    }

    protected WorkbaySnapshot snapshot() {
        return screen.snapshot();
    }

    protected int x(int local) {
        return screen.left() + local;
    }

    protected int y(int local) {
        return screen.top() + local;
    }

    /**
     * The header band every page carries: the title, the three navigation buttons, and the
     * terminal's own power bar. SPEC.md §4 puts the problem count here too, on the bays page.
     */
    protected void header(GuiGraphics g, int mouseX, int mouseY, String title) {
        g.pose().pushPose();
        g.pose().translate(x(8), y(6), 0);
        g.pose().scale(1.6F, 1.6F, 1.0F);
        // Room is in the scaled frame, so it is the gap to the first header button divided by the
        // scale — otherwise a title box drawn at 1.6x claims 1.6x the pixels it was told it had.
        Draw.text(g, screen.font(), title, 0, 0, (int) ((width() - 96 - 8) / 1.6F), Draw.TEXT);
        g.pose().popPose();

        WorkbaySnapshot snap = snapshot();

        // Four 18x18 buttons, top right. The lock is the only one that changes what it draws.
        // ROOMS is always here, never hidden until a Frame is installed: the Frames are bought on
        // that page, so hiding it until you own one is a door locked from the inside.
        tab(g, mouseX, mouseY, width() - 88, WBIcons.DOOR, WorkbayScreen.Page.ROOMS, "rooms");
        tab(g, mouseX, mouseY, width() - 66, WBIcons.UPGRADE, WorkbayScreen.Page.UPGRADES,
            "upgrades");
        tab(g, mouseX, mouseY, width() - 44, WBIcons.MAP, WorkbayScreen.Page.FLOW, "flow");
        iconButton(g, mouseX, mouseY, x(width() - 22), y(6),
            snap.locked() ? WBIcons.LOCK : WBIcons.UNLOCK, snap.locked(),
            () -> screen.send(WorkbayAction.TOGGLE_LOCK),
            WorkbayScreen.gui(snap.locked() ? "button.unlock" : "button.lock"),
            WorkbayScreen.gui("button.lock.tip"));
    }

    /**
     * One of the three page buttons. <b>Pressing the page you are already on goes back to BAYS</b>,
     * which is what the separate back arrow used to do.
     *
     * <p>That arrow sat alone on its own line under the title and cost three of the four pages a
     * whole twenty-pixel row to say one word — on ROOMS, the page that has to fit five ladder rungs
     * and four rooms inside 240. A tab bar where the lit tab drops you back is the same gesture
     * with no row and no second control, and the tooltip says so rather than leaving it to be
     * discovered.
     */
    private void tab(GuiGraphics g, int mouseX, int mouseY, int px, String[] icon,
        WorkbayScreen.Page target, String key) {
        boolean here = screen.page() == target;
        iconButton(g, mouseX, mouseY, x(px), y(6), icon, here,
            () -> screen.goTo(here ? WorkbayScreen.Page.BAYS : target),
            WorkbayScreen.gui("button." + key),
            WorkbayScreen.gui(here ? "button.back.tip" : "button." + key + ".tip"));
    }

    /**
     * Every string a page draws, with the width it has. {@link Draw#text} does the cutting and the
     * debug outline; this adds the one thing a page can do and {@code Draw} cannot — hand the whole
     * of a cut string back through a tooltip, so the player can still find out what it said.
     *
     * <p>Nothing in this mod calls {@code drawString}. {@code tools/check-text.sh} enforces it.
     */
    protected void text(GuiGraphics g, String s, int px, int py, int room, int colour) {
        if (!Draw.text(g, screen.font(), s, px, py, room, colour)) {
            cut(px, py, room, s);
        }
    }

    protected void text(GuiGraphics g, Component s, int px, int py, int room, int colour) {
        text(g, s.getString(), px, py, room, colour);
    }

    /** Right-aligned against {@code rightX}. Figures that must not reach back into the text. */
    protected void textRight(GuiGraphics g, String s, int rightX, int py, int room, int colour) {
        if (!Draw.textRight(g, screen.font(), s, rightX, py, room, colour)) {
            cut(rightX - room, py, room, s);
        }
    }

    /** Centred on {@code centreX}. Button labels, and nothing else so far. */
    protected void textCentre(GuiGraphics g, String s, int centreX, int py, int room, int colour) {
        if (!Draw.textCentre(g, screen.font(), s, centreX, py, room, colour)) {
            cut(centreX - room / 2, py, room, s);
        }
    }

    protected void wrapped(GuiGraphics g, Component s, int px, int py, int room, int colour) {
        Draw.wrapped(g, screen.font(), s, px, py, room, colour);
    }

    private void cut(int px, int py, int room, String full) {
        screen.overflow(px, py - 1, room, screen.font().lineHeight + 1, Component.literal(full));
    }


    /** Draws an 18x18 icon button and registers its click and tooltip in one place. */
    protected void iconButton(GuiGraphics g, int mouseX, int mouseY, int px, int py, String[] icon,
        boolean active, Runnable onClick, Component... tooltip) {
        boolean hover = screen.hovered(px, py, 18, 18, mouseX, mouseY);
        Draw.button(g, px, py, 18, 18, hover, active);
        WBIcons.draw(g, icon, px + 3, py + 3, active ? Draw.TEXT : Draw.TEXT_DIM);
        screen.hit(px, py, 18, 18, onClick, tooltip);
    }

    /**
     * A control that is drawn where SPEC.md §4 puts it but does nothing yet, because what it opens
     * is a later section. Drawn <em>disabled</em>, with the sunken unlit chrome every other
     * disabled control uses, and a tooltip saying so.
     */
    protected void unbuiltButton(GuiGraphics g, int px, int py, int size, String[] icon,
        Component name, Component why) {
        Draw.button(g, px, py, size, size, false, false, false);
        WBIcons.draw(g, icon, px + (size - 12) / 2, py + (size - 12) / 2, Draw.TEXT_FAINT);
        screen.hit(px, py, size, size, () -> { }, name, why);
    }
}
