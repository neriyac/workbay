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

    /** Where the two lines of text beside a gauge start, and how much room they get. */
    private static final int LABEL_X = 8 + BayViewMenu.GAUGE_W + 6;
    private static final int LABEL_W = WIDTH - LABEL_X - 8;

    /** And the same for the line beside the two fluid-container slots. */
    private static final int HINT_X = BayViewMenu.EXCHANGE_OUT_X + 22;
    private static final int HINT_W = WIDTH - HINT_X - 8;

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
        exchange(g);

        // SPEC.md §5's permanent line, in the gap the layout reserves for it.
        Draw.wrapped(g, font, WorkbayScreen.gui("bayview.limits"), leftPos + 8,
            topPos + menu.limitsY(), imageWidth - 16, Draw.TEXT_FAINT);
    }

    /**
     * The tanks, then energy. One row each: the gauge, then what it holds and how much.
     *
     * <p><b>The name is drawn, not left to the tooltip.</b> A blue column is "some liquid" until
     * something says whether it is water or the thing that ruins the recipe, and the player who
     * needs to know that is the one who has not thought to hover anything - the same argument
     * SPEC.md §5 makes for the limits line.
     */
    private void gauges(GuiGraphics g) {
        BayViewMenu.State state = menu.state();
        int row = 0;
        for (BayViewMenu.Tank tank : state.tanks()) {
            int y = gaugeY(row++);
            Draw.fluidGauge(g, leftPos + 8, y, BayViewMenu.GAUGE_W, BayViewMenu.GAUGE_H,
                tank.contents(), tank.capacity());
            label(g, y, tankName(tank).getString(),
                WorkbayScreen.gui("bayview.tank", Draw.compact(tank.contents().getAmount()),
                    Draw.compact(tank.capacity())).getString());
        }
        if (state.energyCapacity() > 0) {
            int y = gaugeY(row);
            Draw.gauge(g, leftPos + 8, y, BayViewMenu.GAUGE_W, BayViewMenu.GAUGE_H, state.energy(),
                state.energyCapacity(), Draw.AMBER);
            label(g, y, WorkbayScreen.gui("bayview.energy").getString(),
                WorkbayScreen.gui("power", Draw.compact(state.energy()),
                    Draw.compact(state.energyCapacity())).getString());
        }
    }

    /**
     * The two fluid-container slots, and one line saying what they are for — or, when the last
     * exchange refused, why.
     *
     * <p>The line is there at rest, not only on a refusal. Two unlabelled slots under a gauge are a
     * guess, and the player who has to guess is the one who has not thought to hover anything: the
     * same argument SPEC.md §5 makes for the limits sentence.
     */
    private void exchange(GuiGraphics g) {
        if (!menu.hasTanks()) {
            return;
        }
        int y = topPos + menu.exchangeY();
        Draw.slot(g, leftPos + BayViewMenu.EXCHANGE_IN_X - 1, y - 1, 18, 18);
        Draw.slot(g, leftPos + BayViewMenu.EXCHANGE_OUT_X - 1, y - 1, 18, 18);
        BayViewMenu.Hint hint = menu.state().hint();
        Draw.wrapped(g, font, hintText(hint), leftPos + HINT_X, y + 1, HINT_W,
            hint == BayViewMenu.Hint.NONE ? Draw.TEXT_FAINT : Draw.AMBER);
    }

    /** The mixing refusal is the only one that names something, because it is the only one the
     * gauge above it does not already show. */
    private Component hintText(BayViewMenu.Hint hint) {
        return switch (hint) {
            case NONE -> WorkbayScreen.gui("bayview.exchange");
            case MIXED -> WorkbayScreen.gui("bayview.exchange.mixed", heldFluid().getString());
            case BLOCKED -> WorkbayScreen.gui("bayview.exchange.blocked");
            case REFUSED -> WorkbayScreen.gui("bayview.exchange.refused");
        };
    }

    private Component heldFluid() {
        return menu.state().tanks().stream()
            .filter(tank -> !tank.contents().isEmpty())
            .findFirst()
            .map(tank -> tank.contents().getHoverName())
            .orElseGet(() -> WorkbayScreen.gui("bayview.tank.empty"));
    }

    /** What a gauge is, then how full it is. The exact figures stay in the tooltip. */
    private void label(GuiGraphics g, int y, String what, String figures) {
        Draw.text(g, font, what, leftPos + LABEL_X, y + 3, LABEL_W, Draw.TEXT);
        Draw.text(g, font, figures, leftPos + LABEL_X, y + 14, LABEL_W, Draw.TEXT_DIM);
    }

    private static Component tankName(BayViewMenu.Tank tank) {
        return tank.contents().isEmpty()
            ? WorkbayScreen.gui("bayview.tank.empty")
            : tank.contents().getHoverName();
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
        return List.of(tankName(tank),
            WorkbayScreen.gui("bayview.tank", Draw.exact(tank.contents().getAmount()),
                Draw.exact(tank.capacity())),
            WorkbayScreen.gui("bayview.tank.tip"));
    }
}
