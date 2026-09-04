package com.neryos.workbay.client.screen;

import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import com.neryos.workbay.menu.WorkbaySnapshot;
import com.neryos.workbay.network.ActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All three Workbay screens. SPEC.md §4.
 *
 * <p><b>No player inventory and no slots.</b> A machine enters a bay by clicking the bay slot while
 * holding it, so there is no grid to drag from and nothing for vanilla's slot machinery to do.
 *
 * <p>Clickable regions are registered as rectangles while the page draws, rather than as
 * {@code Button} widgets built in {@code init()}. With a layout this dense that keeps each control's
 * geometry, its tooltip and its action in one place — the alternative is the same coordinates
 * written out three times and drifting apart the first time anything moves.
 */
public class WorkbayScreen extends AbstractContainerScreen<WorkbayMenu> {

    /** Which screen is showing. All three share one menu; there is nothing to re-bind between them. */
    public enum Page { BAYS, FLOW, UPGRADES }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private Page page = Page.BAYS;

    @Nullable
    private WorkbayPage current;

    /**
     * The rename field, when one is open. SPEC.md §4: an {@code EditBox} over the name line,
     * committed by Return and abandoned by Escape.
     */
    @Nullable
    private net.minecraft.client.gui.components.EditBox renaming;

    @Nullable
    private java.util.function.Consumer<String> onRenamed;

    /**
     * True while a right-click is being dispatched, so every cycling control steps <b>backwards</b>.
     *
     * <p>A field read during dispatch rather than a second Runnable on every {@code hit}: forty-odd
     * call sites would each have to name a mirrored lambda, and the mirror is always the same
     * question — which way round the ring. The server-side ones carry it on the action packet; the
     * client-only ones (the list's filter and sort) read it straight from here.
     */
    private boolean back;

    public WorkbayScreen(WorkbayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        current = switch (page) {
            case BAYS -> new BaysPage(this);
            case FLOW -> new FlowPage(this);
            case UPGRADES -> new UpgradesPage(this);
        };
        imageWidth = current.width();
        imageHeight = current.height();
        // init() clears every widget, so a field left over from before a resize would be a ghost.
        renaming = null;
        onRenamed = null;
        super.init();
    }

    public void goTo(Page next) {
        page = next;
        // Re-running init is what resizes the window: the three pages are different heights, and
        // leftPos/topPos are derived from imageWidth/imageHeight in super.init().
        init(minecraft, width, height);
    }

    public Page page() {
        return page;
    }

    public WorkbaySnapshot snapshot() {
        return menu.snapshot();
    }

    public int left() {
        return leftPos;
    }

    public int top() {
        return topPos;
    }

    /** Opens the rename field over a line of the page. Called from the page while it draws. */
    public void beginRename(int x, int y, int w, int h, String initial,
        java.util.function.Consumer<String> committed) {
        if (renaming != null) {
            return;
        }
        renaming = new net.minecraft.client.gui.components.EditBox(font, x, y, w, h,
            Component.empty());
        renaming.setMaxLength(48);
        renaming.setValue(initial);
        renaming.moveCursorToEnd(false);
        renaming.setFocused(true);
        setFocused(renaming);
        onRenamed = committed;
        addRenderableWidget(renaming);
    }

    public boolean renaming() {
        return renaming != null;
    }

    private void endRename(boolean commit) {
        if (renaming == null) {
            return;
        }
        String value = renaming.getValue();
        removeWidget(renaming);
        renaming = null;
        setFocused(null);
        var committed = onRenamed;
        onRenamed = null;
        if (commit && committed != null) {
            committed.accept(value);
        }
    }

    /**
     * While the rename field is open it takes the keyboard whole. Without this, {@code e} closes
     * the screen mid-word and Escape throws the player out instead of abandoning the edit — both
     * are {@link AbstractContainerScreen}'s defaults and both are wrong here.
     */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (renaming != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                endRename(true);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                endRename(false);
                return true;
            }
            return renaming.keyPressed(key, scan, modifiers) || renaming.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (renaming != null) {
            return renaming.charTyped(typed, modifiers);
        }
        return super.charTyped(typed, modifiers);
    }

    public net.minecraft.client.gui.Font font() {
        return font;
    }

    /** The scaled canvas height, which a page has to fit inside however the player has it scaled. */
    public int availableHeight() {
        return height;
    }

    // ------------------------------------------------------------- clickable

    /**
     * A string that would not fit where it was drawn, and the whole of what it said.
     *
     * <p>Kept apart from {@link Hit} on purpose: a cut string must never swallow the click that
     * belongs to the row it sits on, and it must never shadow that row's own tooltip either. So
     * these are consulted only after every hit has declined, and never for clicks at all. Registered
     * by {@code WorkbayPage#clip}, which is the only thing in the mod allowed to shorten a string.
     */
    private record Overflow(int x, int y, int w, int h, Component full) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Overflow> overflows = new ArrayList<>();

    public void overflow(int x, int y, int w, int h, Component full) {
        overflows.add(new Overflow(x, y, w, h, full));
    }

    /** A shape inside a hit's bounding box. The isometric cube's faces are the only non-rectangles. */
    public interface Inside {
        boolean test(double mx, double my);
    }

    private record Hit(int x, int y, int w, int h, @Nullable Inside shape, Runnable onClick,
        @Nullable List<Component> tooltip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h
                && (shape == null || shape.test(mx, my));
        }
    }

    /** Registers a clickable region in screen coordinates. Called by a page while it draws. */
    public void hit(int x, int y, int w, int h, Runnable onClick, Component... tooltip) {
        hits.add(new Hit(x, y, w, h, null, onClick, tooltip.length == 0 ? null : List.of(tooltip)));
    }

    /** The same, for a region that is not a rectangle: the box is tested first, then the shape. */
    public void hit(int x, int y, int w, int h, Inside shape, Runnable onClick, Component... tooltip) {
        hits.add(new Hit(x, y, w, h, shape, onClick, tooltip.length == 0 ? null : List.of(tooltip)));
    }

    /**
     * A slot that takes an item dragged out of a recipe viewer. Collected while the page draws, the
     * same way clicks are, so a slot's geometry is written once — and read by both the JEI and the
     * EMI plugin, neither of which then knows anything about the layout.
     */
    public record Ghost(int x, int y, int w, int h, java.util.function.Consumer<ItemStack> accept) {}

    public void ghost(int x, int y, int w, int h, java.util.function.Consumer<ItemStack> accept) {
        ghosts.add(new Ghost(x, y, w, h, accept));
    }

    public List<Ghost> ghostTargets() {
        return ghosts;
    }

    /** Which way a cycling control was asked to step. Right-click is backwards. */
    public boolean back() {
        return back;
    }

    public boolean hovered(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        hits.clear();
        ghosts.clear();
        overflows.clear();
        // Cleared here, not in the page: the flow and upgrade pages have no rows to hover, and a
        // highlight left behind by the bays page would outline a block nothing on screen mentions.
        com.neryos.workbay.client.LinkHighlight.clear();
        Draw.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (current != null) {
            current.render(graphics, mouseX, mouseY);
        }
    }

    /** Vanilla would draw "Workbay" and "Inventory" here; this screen has neither in that place. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        for (Hit hit : hits) {
            if (hit.tooltip() != null && hit.contains(mouseX, mouseY)) {
                graphics.renderTooltip(font, wrapTooltip(hit.tooltip()), mouseX, mouseY);
                return;
            }
        }
        // Only when nothing else claimed the pixel. Most cut strings sit inside a row whose own
        // tooltip already names them in full; this is for the ones that do not.
        for (Overflow cut : overflows) {
            if (cut.contains(mouseX, mouseY)) {
                graphics.renderTooltip(font, wrapTooltip(List.of(cut.full())), mouseX, mouseY);
                return;
            }
        }
    }

    private List<net.minecraft.util.FormattedCharSequence> wrapTooltip(List<Component> lines) {
        return Draw.tooltip(font, lines);
    }

    @Override
    public void removed() {
        super.removed();
        com.neryos.workbay.client.LinkHighlight.clear();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The block preview takes the press first: inside it a press starts a turn, and only a
        // press that never turned into one counts as a click on a face.
        if (current != null && current.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        if (renaming != null && !renaming.isMouseOver(mouseX, mouseY)) {
            endRename(true);
            return true;
        }
        // Right-click is the same control asked for the previous value instead of the next. Every
        // control in the mod that cycles reads it, and the ones that do not simply ignore it, which
        // is why it is one flag here rather than a second handler on each.
        if (button == 0 || button == 1) {
            back = button == 1;
            // Reverse order, so a control drawn on top of another wins the click the way it looks.
            for (int i = hits.size() - 1; i >= 0; i--) {
                if (hits.get(i).contains(mouseX, mouseY)) {
                    hits.get(i).onClick().run();
                    back = false;
                    playClick();
                    return true;
                }
            }
            back = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (current != null && current.mouseDragged(dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (current != null && current.mouseReleased(mouseX, mouseY)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (current != null && current.scrolled(mouseX, mouseY, deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // --------------------------------------------------------------- actions

    public void send(WorkbayAction action) {
        send(action, 0, Optional.empty(), Optional.empty());
    }

    public void send(WorkbayAction action, long arg) {
        send(action, arg, Optional.empty(), Optional.empty());
    }

    public void send(WorkbayAction action, UUID link) {
        send(action, 0, Optional.of(link), Optional.empty());
    }

    /** An action that needs both a number and a row: the filter slot is the only one so far. */
    public void send(WorkbayAction action, long arg, UUID link) {
        send(action, arg, Optional.of(link), Optional.empty());
    }

    public void sendText(WorkbayAction action, String text) {
        send(action, 0, Optional.empty(), Optional.of(text));
    }

    private void send(WorkbayAction action, long arg, Optional<UUID> link, Optional<String> text) {
        PacketDistributor.sendToServer(
            new ActionPacket(menu.containerId, action, arg, link, text, back));
        // Applied here as well so the selection tracks the click rather than the round trip.
        if (action == WorkbayAction.SELECT_BAY) {
            menu.setSelectedBayClientSide((int) arg);
        }
    }

    public static Component gui(String key, Object... args) {
        return WorkbayLang.gui(key, args);
    }
}
