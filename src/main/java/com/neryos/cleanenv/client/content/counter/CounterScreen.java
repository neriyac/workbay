package com.neryos.cleanenv.client.content.counter;

import com.neryos.cleanenv.content.counter.CounterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CounterScreen extends AbstractContainerScreen<CounterMenu> {

    // Vanilla's plain generic background, so no texture of our own is needed yet.
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public CounterScreen(CounterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        // The whole point of the slice: a server-side value visible on the client.
        graphics.drawString(font,
            Component.translatable("screen.cleanenv.counter_block.count", menu.getCount()),
            titleLabelX, titleLabelY + 20, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
