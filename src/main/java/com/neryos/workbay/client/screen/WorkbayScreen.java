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

    public net.minecraft.client.gui.Font font() {
        return font;
    }

    /** The scaled canvas height, which a page has to fit inside however the player has it scaled. */
    public int availableHeight() {
        return height;
    }

    // ------------------------------------------------------------- clickable

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

    public boolean hovered(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        hits.clear();
        ghosts.clear();
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
                graphics.renderComponentTooltip(font, hit.tooltip(), mouseX, mouseY);
                return;
            }
        }
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
        if (button == 0) {
            // Reverse order, so a control drawn on top of another wins the click the way it looks.
            for (int i = hits.size() - 1; i >= 0; i--) {
                if (hits.get(i).contains(mouseX, mouseY)) {
                    hits.get(i).onClick().run();
                    playClick();
                    return true;
                }
            }
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
        send(action, 0, Optional.empty());
    }

    public void send(WorkbayAction action, long arg) {
        send(action, arg, Optional.empty());
    }

    public void send(WorkbayAction action, UUID link) {
        send(action, 0, Optional.of(link));
    }

    /** An action that needs both a number and a row: the filter slot is the only one so far. */
    public void send(WorkbayAction action, long arg, UUID link) {
        send(action, arg, Optional.of(link));
    }

    private void send(WorkbayAction action, long arg, Optional<UUID> link) {
        PacketDistributor.sendToServer(new ActionPacket(menu.containerId, action, arg, link));
        // Applied here as well so the selection tracks the click rather than the round trip.
        if (action == WorkbayAction.SELECT_BAY) {
            menu.setSelectedBayClientSide((int) arg);
        }
    }

    public static Component gui(String key, Object... args) {
        return WorkbayLang.gui(key, args);
    }
}
