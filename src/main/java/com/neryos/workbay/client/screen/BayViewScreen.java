package com.neryos.workbay.client.screen;

import com.neryos.workbay.menu.BayViewMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Bay View. SPEC.md §5.
 *
 * <p><b>It must not look like the machine's own screen.</b> Our panel, our chrome, our header, and
 * a permanent line naming what it cannot reach — a screen that mimics a machine's and silently
 * lacks half of its controls reads as a broken mod rather than as a deliberate limit. That line is
 * drawn at rest, not hidden in a tooltip, because the player who needs it is the one who has not
 * thought to hover anything.
 */
public class BayViewScreen extends AbstractContainerScreen<BayViewMenu> {

    private static final int WIDTH = 176;

    public BayViewScreen(BayViewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = BayViewMenu.height(menu.machineSlots());
        // Vanilla puts the inventory label 94 up from the bottom, which is where nothing of ours is.
        inventoryLabelY = BayViewMenu.inventoryY(menu.machineSlots()) - 10;
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

        int inventoryY = BayViewMenu.inventoryY(menu.machineSlots());
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

        // SPEC.md §5's permanent line, in the gap the layout reserves for it.
        int limitsY = topPos + BayViewMenu.GRID_Y + menu.rows() * 18 + 6;
        var lines = font.split(WorkbayScreen.gui("bayview.limits"), imageWidth - 16);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), leftPos + 8, limitsY + i * 10, Draw.TEXT_FAINT, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Draw.TEXT, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, Draw.TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
