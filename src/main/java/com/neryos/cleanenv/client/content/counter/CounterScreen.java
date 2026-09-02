package com.neryos.cleanenv.client.content.counter;

import com.neryos.cleanenv.content.counter.CounterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A menu with no slots has no business blitting a chest texture, so this panel is drawn
 * rather than blitted: four fills in vanilla's own GUI palette give the bevelled border
 * without shipping a texture or an atlas sprite.
 */
public class CounterScreen extends AbstractContainerScreen<CounterMenu> {

    // Vanilla's container-GUI palette, sampled from the shared widget textures.
    private static final int FACE      = 0xFFC6C6C6;
    private static final int HIGHLIGHT = 0xFFFFFFFF; // top and left inner bevel
    private static final int SHADOW    = 0xFF555555; // bottom and right inner bevel
    private static final int OUTLINE   = 0xFF000000;
    private static final int TEXT      = 0x404040;

    /** Outer edge to glyph, all four sides. The border lives inside it. */
    private static final int PADDING = 8;
    private static final int LINE_GAP = 5;
    /** Inked rows of a line: the font's 9px box keeps its last 2 for descenders. */
    private static final int GLYPH = 7;

    public CounterScreen(CounterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = PADDING + 9 + LINE_GAP + GLYPH + PADDING;
        this.titleLabelX = PADDING;
        this.titleLabelY = PADDING;
    }

    @Override
    protected void init() {
        // Width comes from the longest line, so there is no magic number to keep in sync
        // with a renamed or translated title. The count is measured at a fixed six digits
        // rather than its current value, so the panel does not resize as it ticks.
        int content = Math.max(font.width(title), font.width(countLine(999999)));
        this.imageWidth = PADDING + content + PADDING;
        super.init(); // centres the panel, and needs the final imageWidth to do it
    }

    private static Component countLine(int count) {
        return Component.translatable("screen.cleanenv.counter_block.count", count);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        graphics.fill(x, y, x + w, y + h, OUTLINE);                     // 1px outer edge
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, HIGHLIGHT);   // lit top-left bevel
        graphics.fill(x + 2, y + 2, x + w - 1, y + h - 1, SHADOW);      // dark bottom-right bevel
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, FACE);        // panel face
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        // The whole point of the slice: a server-side value visible on the client.
        graphics.drawString(font, countLine(menu.getCount()),
            titleLabelX, titleLabelY + 9 + LINE_GAP, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
