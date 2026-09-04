package com.neryos.workbay.client.screen;

import com.neryos.workbay.menu.BayViewMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Bay View. SPEC.md §5.
 *
 * <p><b>It must not look like the machine's own screen.</b> Our panel, our chrome, our header, and
 * a permanent line naming what it cannot reach — a screen that mimics a machine's and silently
 * lacks half of its controls reads as a broken mod rather than as a deliberate limit. That line is
 * drawn at rest, not hidden in a tooltip, because the player who needs it is the one who has not
 * thought to hover anything.
 *
 * <p>Under the item grid, a gauge per fluid tank and one for energy. They are here because they are
 * the two things a player tops a machine up with that are not items, and both have a standard
 * capability to read them from. Progress does not, so there is no bar for it and there will not be
 * one.
 */
public class BayViewScreen extends AbstractContainerScreen<BayViewMenu> {

    private static final int WIDTH = 176;

    /**
     * The bar, and the figures right-aligned in what is left of the panel's width.
     *
     * <p>Ninety for the figures, and the bar takes what is left rather than the other way round.
     * The widest thing this line can ever say is a full Mekanism cube — {@code 9999.9k / 9999.9k},
     * ninety pixels — and a bar that is a few pixels shorter is a bar, while a figure that is a few
     * pixels short is {@code 1600.0k / 16...}. The units live in the tooltip for the same reason:
     * they cost fourteen more pixels than the line has, and the amber fill and the fluid's own
     * texture already say which of the two a row is.
     */
    private static final int FIGURES_W = 90;
    private static final int GAUGE_W = WIDTH - 16 - FIGURES_W - 4;

    public BayViewScreen(BayViewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = menu.height();
        // Vanilla puts the inventory label 94 up from the bottom, which is where nothing of ours is.
        inventoryLabelY = menu.inventoryY() - 10;
        titleLabelX = 8;
        titleLabelY = 8;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        Draw.panel(g, leftPos, topPos, imageWidth, imageHeight);

        // What is in the bay, named with its own item beside it, so the screen says which machine
        // it is looking into before the player reads a word.
        ItemStack icon = menu.machineId().map(BuiltInRegistries.ITEM::get).map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
        if (!icon.isEmpty()) {
            g.renderItem(icon, leftPos + imageWidth - 26, topPos + 6);
        }

        for (int index = 0; index < menu.machineSlots(); index++) {
            Draw.slot(g, leftPos + BayViewMenu.GRID_X + (index % BayViewMenu.COLUMNS) * 18 - 1,
                topPos + BayViewMenu.GRID_Y + (index / BayViewMenu.COLUMNS) * 18 - 1, 18, 18);
        }

        int inventoryY = menu.inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Draw.slot(g, leftPos + BayViewMenu.GRID_X + column * 18 - 1,
                    topPos + inventoryY + row * 18 - 1, 18, 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            Draw.slot(g, leftPos + BayViewMenu.GRID_X + column * 18 - 1,
                topPos + inventoryY + 58 - 1, 18, 18);
        }

        gauges(g);

        // SPEC.md §5's permanent line, in the gap the layout reserves for it.
        Draw.wrapped(g, font, WorkbayScreen.gui("bayview.limits"), leftPos + 8,
            topPos + menu.limitsY(), imageWidth - 16, Draw.TEXT_FAINT);
    }

    /** The tanks, then energy. One row each, in the order the layout reserved room for. */
    private void gauges(GuiGraphics g) {
        BayViewMenu.State state = menu.state();
        int row = 0;
        for (BayViewMenu.Tank tank : state.tanks()) {
            int y = gaugeY(row++);
            Draw.fluidBar(g, leftPos + 8, y, GAUGE_W, BayViewMenu.GAUGE_H, tank.contents(),
                tank.capacity());
            Draw.textRight(g, font, figures(tank.contents().getAmount(), tank.capacity()),
                leftPos + imageWidth - 8, y + 1, FIGURES_W, Draw.TEXT_DIM);
        }
        if (state.energyCapacity() > 0) {
            int y = gaugeY(row);
            Draw.bar(g, leftPos + 8, y, GAUGE_W, BayViewMenu.GAUGE_H, state.energy(),
                state.energyCapacity(), Draw.AMBER);
            Draw.textRight(g, font, figures(state.energy(), state.energyCapacity()),
                leftPos + imageWidth - 8, y + 1, FIGURES_W, Draw.TEXT_DIM);
        }
    }

    private static String figures(int value, int capacity) {
        return Draw.compact(value) + " / " + Draw.compact(capacity);
    }

    private int gaugeY(int row) {
        return topPos + BayViewMenu.gaugesY(menu.machineSlots()) + row * BayViewMenu.GAUGE_PITCH;
    }

    /** Which gauge row the cursor is over, or -1. The whole row, not just the bar. */
    private int gaugeAt(int mouseX, int mouseY) {
        if (mouseX < leftPos + 8 || mouseX >= leftPos + imageWidth - 8) {
            return -1;
        }
        int row = (mouseY - gaugeY(0)) / BayViewMenu.GAUGE_PITCH;
        return row >= 0 && row < menu.gauges() && mouseY >= gaugeY(0) ? row : -1;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // renderLabels draws in panel-local coordinates, so the room is the panel's own width less
        // the margin and, for the title, the machine's item sprite in the top right corner.
        Draw.text(g, font, title.getString(), titleLabelX, titleLabelY, imageWidth - 8 - 28,
            Draw.TEXT);
        Draw.text(g, font, playerInventoryTitle.getString(), 8, inventoryLabelY, imageWidth - 16,
            Draw.TEXT_DIM);
    }

    /**
     * Clicking a tank does what right-clicking one in the world does: the container on the cursor
     * fills it, or an empty one takes from it. The server decides both — this only says which row
     * was hit, and even that only to keep the click from reaching the slot machinery underneath.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = gaugeAt((int) mouseX, (int) mouseY);
        if (row >= 0 && row < menu.state().tanks().size() && minecraft != null
            && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                BayViewMenu.FLUID_BUTTON);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int row = gaugeAt(mouseX, mouseY);
        if (row >= 0) {
            // Draw#tooltip, not renderComponentTooltip: the mod's tooltips are sentences, and
            // unwrapped one of them is half the screen wide.
            g.renderTooltip(font, Draw.tooltip(font, gaugeTooltip(row)), mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    /**
     * The exact figures, and — for a tank — the fluid's own name, which the bar cannot carry. A
     * texture says "some blue liquid"; only the name says whether it is water or something that
     * will ruin the recipe.
     */
    private List<Component> gaugeTooltip(int row) {
        BayViewMenu.State state = menu.state();
        if (row >= state.tanks().size()) {
            return List.of(WorkbayScreen.gui("bayview.energy"),
                WorkbayScreen.gui("power", Draw.exact(state.energy()),
                    Draw.exact(state.energyCapacity())),
                WorkbayScreen.gui("bayview.energy.tip"));
        }
        BayViewMenu.Tank tank = state.tanks().get(row);
        Component name = tank.contents().isEmpty()
            ? WorkbayScreen.gui("bayview.tank.empty")
            : tank.contents().getHoverName();
        return List.of(name,
            WorkbayScreen.gui("bayview.tank", Draw.exact(tank.contents().getAmount()),
                Draw.exact(tank.capacity())),
            WorkbayScreen.gui("bayview.tank.tip"));
    }
}
