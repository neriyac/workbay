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
        var font = screen.font();
        g.pose().pushPose();
        g.pose().translate(x(8), y(6), 0);
        g.pose().scale(1.6F, 1.6F, 1.0F);
        g.drawString(font, title, 0, 0, Draw.TEXT, false);
        g.pose().popPose();

        WorkbaySnapshot snap = snapshot();

        // Three 18x18 buttons, top right. The lock is the only one that changes what it draws.
        boolean onBays = screen.page() == WorkbayScreen.Page.BAYS;
        iconButton(g, mouseX, mouseY, x(width() - 66), y(6), WBIcons.UPGRADE,
            screen.page() == WorkbayScreen.Page.UPGRADES,
            () -> screen.goTo(WorkbayScreen.Page.UPGRADES),
            WorkbayScreen.gui("button.upgrades"), WorkbayScreen.gui("button.upgrades.tip"));
        iconButton(g, mouseX, mouseY, x(width() - 44), y(6), WBIcons.MAP,
            screen.page() == WorkbayScreen.Page.FLOW,
            () -> screen.goTo(WorkbayScreen.Page.FLOW),
            WorkbayScreen.gui("button.flow"), WorkbayScreen.gui("button.flow.tip"));
        iconButton(g, mouseX, mouseY, x(width() - 22), y(6),
            snap.locked() ? WBIcons.LOCK : WBIcons.UNLOCK, snap.locked(),
            () -> screen.send(WorkbayAction.TOGGLE_LOCK),
            WorkbayScreen.gui(snap.locked() ? "button.unlock" : "button.lock"),
            WorkbayScreen.gui("button.lock.tip"));

        if (!onBays) {
            iconButton(g, mouseX, mouseY, x(8), y(24), WBIcons.BACK, false,
                () -> screen.goTo(WorkbayScreen.Page.BAYS),
                WorkbayScreen.gui("button.back"), WorkbayScreen.gui("button.back.tip"));
        }
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
     * is a later section. Drawn at 40% with a tooltip saying so — a screen that hides its unbuilt
     * controls tells the player less than one that admits to them.
     */
    protected void unbuiltButton(GuiGraphics g, int px, int py, int size, String[] icon,
        Component name, Component why) {
        Draw.button(g, px, py, size, size, false, false);
        WBIcons.draw(g, icon, px + (size - 12) / 2, py + (size - 12) / 2, Draw.TEXT_FAINT);
        screen.hit(px, py, size, size, () -> { }, name, why);
    }
}
