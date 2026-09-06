package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
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
    /** Stops four pixels short of the exchange column, which shares the band to its right. */
    private static final int LABEL_W = BayViewMenu.EXCHANGE_IN_X - 4 - LABEL_X;

    /** The hint gets the panel's full width on its own line under the block, not a narrow gutter. */
    private static final int HINT_X = 8;
    private static final int HINT_W = WIDTH - 16;

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

        BayViewMenu.MachineLayout layout = menu.layout();
        for (int index = 0; index < menu.machineSlots(); index++) {
            int x = leftPos + layout.xs()[index] - 1;
            int y = topPos + layout.ys()[index] - 1;
            Draw.slot(g, x, y, 18, 18);
            if (!menu.slotInfo().get(index).writable()) {
                // A slot nothing can go into is drawn unlit, the same way a disabled button is
                // (SPEC.md §7). It reads as unavailable at a glance instead of only on hover —
                // a grid that looks ordinary and silently refuses every click is the same fault as
                // a dead link reading IDLE.
                Draw.disabled(g, x + 1, y + 1, 16, 16);
            }
        }
        if (layout.grouped()) {
            // Item flow, left to right: what the machine will take on the left, what it will only
            // give on the right. Drawn, never clicked — there is no progress behind it to read, so
            // an arrow that filled would be an arrow that lied. SPEC.md §5.
            WBIcons.draw(g, WBIcons.ARROW_RIGHT, leftPos + BayViewMenu.ARROW_X,
                topPos + BayViewMenu.GRID_Y + 3, Draw.TEXT_FAINT);
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
        exchange(g, mouseX, mouseY);

        // SPEC.md §5's permanent line. One line drawn, the sentence on hover.
        Draw.text(g, font, WorkbayScreen.gui("bayview.limits.short").getString(), leftPos + 8,
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
            label(g, y, tankName(tank).getString());
        }
        for (BayViewMenu.Chemical gas : state.chemicals()) {
            int y = gaugeY(row++);
            // No texture: a chemical has no fluid to take one from, so the bar carries the level
            // and the name carries what it is. Clamped into an int because the gauge draws a ratio
            // and Mekanism's capacities do not fit one.
            long shown = Math.min(gas.amount(), gas.capacity());
            Draw.gauge(g, leftPos + 8, y, BayViewMenu.GAUGE_W, BayViewMenu.GAUGE_H,
                (int) (shown * 1000L / Math.max(1L, gas.capacity())), 1000, Draw.GREEN);
            label(g, y, gas.name().getString().isEmpty()
                ? WorkbayScreen.gui("bayview.chemical.empty").getString()
                : gas.name().getString());
        }
        if (state.energyCapacity() > 0) {
            int y = gaugeY(row);
            Draw.gauge(g, leftPos + 8, y, BayViewMenu.GAUGE_W, BayViewMenu.GAUGE_H, state.energy(),
                state.energyCapacity(), Draw.ENERGY);
            label(g, y, WorkbayScreen.gui("bayview.energy").getString());
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
    private void exchange(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.hasTanks()) {
            return;
        }
        int y = topPos + menu.exchangeY();
        int x = leftPos + BayViewMenu.EXCHANGE_IN_X;
        Draw.slot(g, x - 1, y - 1, 18, 18);
        Draw.slot(g, x - 1, y - 1 + BayViewMenu.EXCHANGE_OUT_DY, 18, 18);
        // The arrow is item flow, not fluid flow: what goes in the top comes out of the bottom,
        // whichever way the fluid is moving. It is drawn, never clicked - the direction button
        // beside it is the control, and one glyph that is sometimes a button is how a player
        // learns to distrust every glyph.
        WBIcons.draw(g, WBIcons.ARROW_DOWN, x + 3, y + BayViewMenu.EXCHANGE_ARROW_DY,
            Draw.TEXT_FAINT);

        int modeX = leftPos + BayViewMenu.EXCHANGE_MODE_X;
        int modeY = y + BayViewMenu.EXCHANGE_MODE_DY;
        boolean hovered = overMode(mouseX, mouseY);
        Draw.button(g, modeX, modeY, 18, 18, hovered, false);
        WBIcons.draw(g, menu.state().mode() == BusConfig.Mode.INSERT
            ? WBIcons.INSERT : WBIcons.EXTRACT, modeX + 3, modeY + 3, Draw.TEXT);

        BayViewMenu.Hint hint = menu.state().hint();
        if (hint == BayViewMenu.Hint.NONE) {
            // Nothing wrong, so nothing said. The row stays reserved rather than reclaimed: a
            // panel that changes height when a bucket goes in moves every slot under the cursor.
            return;
        }
        Draw.wrapped(g, font, hintText(hint),
            leftPos + HINT_X,
            topPos + menu.gaugesY()
                + BayViewMenu.gaugesBlock(menu.gauges(), true) + 1,
            HINT_W, hint == BayViewMenu.Hint.NONE ? Draw.TEXT_FAINT : Draw.AMBER);
    }

    /** Which machine slot the cursor is over, or -1. */
    private int machineSlotAt(int mouseX, int mouseY) {
        BayViewMenu.MachineLayout layout = menu.layout();
        for (int index = 0; index < menu.machineSlots(); index++) {
            int x = leftPos + layout.xs()[index];
            int y = topPos + layout.ys()[index];
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return index;
            }
        }
        return -1;
    }

    /**
     * What the slot is, over whatever is in it. <b>The name of the thing first, then what the slot
     * is for</b> — the item is what the player came to read, and the role is the part this screen
     * knows that the machine's own screen would have shown with a coloured border.
     *
     * <p>"Read-only here" is on every slot this screen cannot write to, which on a Mekanism machine
     * is all of them. Saying nothing there is how a player learns by losing a click.
     */
    private List<Component> slotTooltip(int index) {
        BayViewMenu.SlotInfo info = menu.slotInfo().get(index);
        List<Component> lines = new java.util.ArrayList<>();
        ItemStack held = menu.getSlot(index).getItem();
        if (!held.isEmpty()) {
            lines.add(held.getHoverName());
        }
        lines.add(WorkbayScreen.gui(switch (info.role()) {
            case IN -> "bayview.slot.in";
            case FUEL -> "bayview.slot.fuel";
            case OUT -> "bayview.slot.out";
        }));
        if (info.fluidContainer()) {
            lines.add(WorkbayScreen.gui("bayview.slot.fluid"));
        }
        if (!info.writable()) {
            lines.add(WorkbayScreen.gui("bayview.slot.readonly"));
        }
        return lines;
    }

    /** The limits line's box. The whole line, so a player brushing past it gets the sentence. */
    private boolean overLimits(int mouseX, int mouseY) {
        int y = topPos + menu.limitsY();
        return mouseX >= leftPos + 8 && mouseX < leftPos + imageWidth - 8
            && mouseY >= y && mouseY < y + font.lineHeight;
    }

    /** The direction button's box, in screen coordinates. */
    private boolean overMode(int mouseX, int mouseY) {
        if (!menu.hasTanks()) {
            return false;
        }
        int x = leftPos + BayViewMenu.EXCHANGE_MODE_X;
        int y = topPos + menu.exchangeY() + BayViewMenu.EXCHANGE_MODE_DY;
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    /** The mixing refusal is the only one that names something, because it is the only one the
     * gauge above it does not already show. */
    private Component hintText(BayViewMenu.Hint hint) {
        return switch (hint) {
            case NONE -> WorkbayScreen.gui("bayview.exchange");
            case MIXED -> WorkbayScreen.gui("bayview.exchange.mixed", heldFluid().getString());
            case BLOCKED -> WorkbayScreen.gui("bayview.exchange.blocked");
            case REFUSED -> WorkbayScreen.gui("bayview.exchange.refused");
            case WRONG_WAY -> WorkbayScreen.gui("bayview.exchange.wrong_way");
        };
    }

    private Component heldFluid() {
        return menu.state().tanks().stream()
            .filter(tank -> !tank.contents().isEmpty())
            .findFirst()
            .map(tank -> tank.contents().getHoverName())
            .orElseGet(() -> WorkbayScreen.gui("bayview.tank.empty"));
    }

    /**
     * What a gauge is, and only that. <b>The figures are a hover, not a line.</b> A gauge already
     * says how full it is — that is the entire reason it is a gauge — so a number repeating it is a
     * line the eye has to skip past on every screen. The name cannot be read off the picture, so
     * the name stays; the exact millibuckets go where the exact anything goes.
     */
    private void label(GuiGraphics g, int y, String what) {
        Draw.text(g, font, what, leftPos + LABEL_X, y + 3, LABEL_W, Draw.TEXT);
    }

    private static Component tankName(BayViewMenu.Tank tank) {
        return tank.contents().isEmpty()
            ? WorkbayScreen.gui("bayview.tank.empty")
            : tank.contents().getHoverName();
    }

    private int gaugeY(int row) {
        return topPos + menu.gaugesY() + row * BayViewMenu.GAUGE_PITCH;
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
     * One control means one thing: left-click is the next value in the cycle, right-click the
     * previous. With two directions they land on the same place, and a right-click that did
     * nothing would read as a broken button.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overMode((int) mouseX, (int) mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                BayViewMenu.MODE_BUTTON);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        if (overLimits(mouseX, mouseY)) {
            g.renderTooltip(font, Draw.tooltip(font, List.of(
                WorkbayScreen.gui("bayview.limits.short"),
                WorkbayScreen.gui("bayview.limits"))), mouseX, mouseY);
            return;
        }
        if (overMode(mouseX, mouseY)) {
            g.renderTooltip(font, Draw.tooltip(font, List.of(
                WorkbayScreen.gui(menu.state().mode() == BusConfig.Mode.INSERT
                    ? "bayview.exchange.into" : "bayview.exchange.outof"),
                WorkbayScreen.gui("bayview.exchange.direction.tip"))), mouseX, mouseY);
            return;
        }
        int slot = machineSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            g.renderTooltip(font, Draw.tooltip(font, slotTooltip(slot)), mouseX, mouseY);
            return;
        }
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
        if (row >= state.tanks().size() + state.chemicals().size()) {
            return List.of(WorkbayScreen.gui("bayview.energy"),
                WorkbayScreen.gui("power", Draw.exact(state.energy()),
                    Draw.exact(state.energyCapacity())),
                WorkbayScreen.gui("bayview.energy.tip"));
        }
        if (row >= state.tanks().size() && row < state.tanks().size() + state.chemicals().size()) {
            BayViewMenu.Chemical gas = state.chemicals().get(row - state.tanks().size());
            return List.of(
                gas.name().getString().isEmpty()
                    ? WorkbayScreen.gui("bayview.chemical.empty") : gas.name(),
                WorkbayScreen.gui("bayview.chemical", Draw.exact((int) Math.min(gas.amount(),
                    Integer.MAX_VALUE)), Draw.exact((int) Math.min(gas.capacity(),
                    Integer.MAX_VALUE))),
                WorkbayScreen.gui("bayview.chemical.tip"));
        }
        BayViewMenu.Tank tank = state.tanks().get(row);
        return List.of(tankName(tank),
            WorkbayScreen.gui("bayview.tank", Draw.exact(tank.contents().getAmount()),
                Draw.exact(tank.capacity())),
            WorkbayScreen.gui("bayview.tank.tip"));
    }
}
