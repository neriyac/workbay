package com.neryos.workbay.client.screen;

import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Screen 3 — Upgrades. SPEC.md §4 and §1.
 *
 * <p>Upgrades are <b>consumed on install</b> and recorded as counters, so there is no slot grid and
 * no removal path: an Add button takes the item out of the player's inventory and the count goes up
 * by one, for good. Rows the player cannot afford draw at 45% rather than disappearing, because the
 * ladder is the progression and hiding its rungs hides the game.
 *
 * <p>Taller than SPEC.md §4's 206: three upgrade rows at 34px plus the header and the Levy footer
 * do not fit in 206 without the rows losing their one-line description, which is the only thing on
 * the row that says what the upgrade does.
 */
class UpgradesPage extends WorkbayPage {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 212;

    private static final int ROW_X = 118;
    private static final int ROW_W = 194;
    private static final int ROW_Y = 32;
    private static final int ROW_H = 34;
    private static final int ROW_PITCH = 38;

    /**
     * The row's three columns, fixed rather than derived from what happens to be in them.
     *
     * <p>Both the count and the price used to be right-aligned against the Add button and the text
     * clamped to whatever they left over, so a cost that grew a digit ate the description's last
     * characters — the row that told you the least was the one about to charge you the most. The
     * price column is sized for {@code Levy: 102}, the top of the Expansion Plate ladder, and the
     * text gets everything to its left whatever the number turns out to be.
     */
    private static final int TEXT_X = 28;
    private static final int RIGHT_EDGE = ROW_W - 36;
    private static final int COUNT_W = 32;
    private static final int PRICE_W = 52;
    private static final int NAME_W = RIGHT_EDGE - COUNT_W - TEXT_X - 4;
    private static final int DESC_W = RIGHT_EDGE - PRICE_W - TEXT_X - 4;

    /** The block preview turns as the player drags it, since it is the only art on the screen. */
    private static final BlockPreview PREVIEW = new BlockPreview();

    private static final int WELL_X = 12;
    private static final int WELL_Y = 32;
    private static final int WELL_W = 96;
    private static final int WELL_H = 74;

    UpgradesPage(WorkbayScreen screen) {
        super(screen);
    }

    @Override
    int width() {
        return WIDTH;
    }

    @Override
    int height() {
        return HEIGHT;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        header(g, mouseX, mouseY, "UPGRADES");
        preview(g, mouseX, mouseY);
        rows(g, mouseX, mouseY);
        footer(g, mouseX, mouseY);
    }

    /** Left: the block, its power bar at full size with exact figures, and the incoming rate. */
    private void preview(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();

        Draw.well(g, x(WELL_X), y(WELL_Y), WELL_W, WELL_H);
        // The Workbay's own block, rendered by the game rather than drawn: this screen is where a
        // player looks at what they are upgrading.
        PREVIEW.render(g, BlockPreview.facingCamera(
                com.neryos.workbay.init.WBBlocks.WORKBAY.get().defaultBlockState()),
            x(WELL_X + WELL_W / 2), y(WELL_Y + WELL_H / 2), 34);
        textCentre(g, WorkbayScreen.gui("faces.drag").getString(), x(WELL_X + WELL_W / 2),
            y(WELL_Y + WELL_H + 3), WELL_W, Draw.TEXT_FAINT);

        Draw.bar(g, x(WELL_X), y(WELL_Y + WELL_H + 16), WELL_W, 12,
            snap.energy(), snap.energyCapacity(), Draw.ENERGY);
        // Compact, like every other energy figure in the mod: "0 / 100000 FE" is 74 pixels in a
        // column 96 wide before the capacity grows a digit, and the exact figure is in the tooltip.
        text(g, Draw.compact(snap.energy()) + " / " + Draw.compact(snap.energyCapacity()) + " FE",
            x(WELL_X), y(WELL_Y + WELL_H + 32), WELL_W, Draw.TEXT_DIM);
        screen.hit(x(WELL_X), y(WELL_Y + WELL_H + 16), WELL_W, 26, () -> { },
            WorkbayScreen.gui("power", Draw.exact(snap.energy()), Draw.exact(snap.energyCapacity())),
            WorkbayScreen.gui("power.tip"));
        text(g, WorkbayScreen.gui("upgrades.rate",
                com.neryos.workbay.content.workbay.WorkbayBlockEntity.MAX_FE_PER_TICK),
            x(WELL_X), y(WELL_Y + WELL_H + 44), WELL_W, Draw.TEXT_FAINT);

        // How many of this network's Workbays are standing, out of how many the server allows.
        // Drawn on the first one a player builds, because the cap is otherwise invisible until they
        // have crafted a second, carried it somewhere and had the placement refused.
        boolean full = snap.deployed() >= snap.maxDeployed();
        // The short form on the line, the sentence in the tooltip: this column is 96 wide.
        text(g, WorkbayScreen.gui("upgrades.deployed.short", snap.deployed(), snap.maxDeployed()),
            x(WELL_X), y(WELL_Y + WELL_H + 56), WELL_W, full ? Draw.AMBER : Draw.TEXT_FAINT);
        screen.hit(x(WELL_X), y(WELL_Y + WELL_H + 54), WELL_W, 12, () -> { },
            WorkbayScreen.gui("upgrades.deployed", snap.deployed(), snap.maxDeployed()),
            WorkbayScreen.gui(full ? "upgrades.deployed.full" : "upgrades.deployed.tip"));
    }

    @Override
    boolean mousePressed(double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < x(WELL_X) || mouseX >= x(WELL_X + WELL_W)
            || mouseY < y(WELL_Y) || mouseY >= y(WELL_Y + WELL_H)) {
            return false;
        }
        PREVIEW.press();
        return true;
    }

    @Override
    boolean mouseDragged(double dragX, double dragY) {
        PREVIEW.drag(dragX, dragY);
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY) {
        PREVIEW.release();
        return false;
    }

    private void rows(GuiGraphics g, int mouseX, int mouseY) {
        WorkbaySnapshot snap = snapshot();

        int row = -1;
        for (WorkbayUpgrade upgrade : WorkbayUpgrade.values()) {
            // The room line is on the ROOMS page, beside the rooms whose chunk cost it sets. This
            // page keeps the four rungs it was drawn for, and its rows stay two lines deep.
            if (upgrade.aboutRooms()) {
                continue;
            }
            row++;
            int index = upgrade.ordinal();
            int px = x(ROW_X);
            int py = y(ROW_Y + row * ROW_PITCH);
            int installed = snap.upgrades().installed(upgrade);
            boolean maxed = installed >= upgrade.max();
            int cost = upgrade.levyCost(installed);
            // SPEC.md §4: an unaffordable row draws faint rather than disappearing, because the
            // ladder is the progression and hiding its rungs hides the game.
            boolean affordable = snap.levy() >= cost;
            boolean canInstall = !maxed && affordable;

            Draw.well(g, px, py, ROW_W, ROW_H);

            ItemStack icon = new ItemStack(upgrade.item());
            g.renderItem(icon, px + 6, py + 9);

            String key = "upgrade." + upgrade.getSerializedName();
            // Name over description on the left, count over price on the right. Both right-hand
            // figures are right-aligned inside one fixed column, so neither can reach back into
            // the text.
            String count = installed + " / " + upgrade.max();
            // A name is never faint. It says what the thing <em>is</em>, and that does not change
            // with what is in the bank: on a network that cannot afford anything the whole page
            // came out greyed, four dead rows with nothing for the eye to land on. Whether you
            // can have it is already said twice on the row -- the price in red, and the + drawn
            // disabled -- and neither of those is the name's job. Same rule on ROOMS.
            text(g, WorkbayScreen.gui(key), px + TEXT_X, py + 6, NAME_W, Draw.TEXT);
            textRight(g, count, px + RIGHT_EDGE, py + 6, COUNT_W,
                maxed ? Draw.GREEN : Draw.TEXT_DIM);

            // The price is on the row rather than in the tooltip, because it is the thing the
            // player is deciding on and a hover is one step too late for that.
            String price = maxed ? "" : WorkbayScreen.gui("upgrades.levy", cost).getString();
            textRight(g, price, px + RIGHT_EDGE, py + 19, PRICE_W,
                maxed ? Draw.TEXT_FAINT : affordable ? Draw.GREEN : Draw.RED);
            text(g, WorkbayScreen.gui(key + ".desc"), px + TEXT_X, py + 19, DESC_W,
                Draw.TEXT_FAINT);

            int addX = px + ROW_W - 30;
            int addY = py + 8;
            boolean hover = canInstall && screen.hovered(addX, addY, 22, 18, mouseX, mouseY);
            Draw.button(g, addX, addY, 22, 18, hover, maxed, canInstall);
            WBIcons.draw(g, WBIcons.PLUS, addX + 5, addY + 3,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            screen.hit(addX, addY, 22, 18,
                canInstall ? () -> screen.send(WorkbayAction.INSTALL_UPGRADE, index) : () -> { },
                // One line, not two. A maxed row printed "as many as a Workbay takes" twice, and an
                // unaffordable one gave the price in both halves -- "you have 1 Levy and this costs
                // 24" followed by "costs 24 Levy". The price line is what is left to say only when
                // the player can already afford it.
                maxed ? new net.minecraft.network.chat.Component[] {
                    WorkbayScreen.gui(key), WorkbayScreen.gui("upgrades.maxed") }
                    : affordable ? new net.minecraft.network.chat.Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.add", WorkbayScreen.gui(key)),
                        WorkbayScreen.gui("upgrades.cost", cost) }
                    : new net.minecraft.network.chat.Component[] {
                        WorkbayScreen.gui(key),
                        WorkbayScreen.gui("upgrades.unaffordable", snap.levy(), cost) });
        }
    }

    /**
     * Levy in stock and the skim dial. SPEC.md §3 and §4: the two live together because they are
     * the two ends of one loop — the rate is what buys the balance, and the balance is what the
     * rows above are spending. Nothing else in the mod reads or writes either.
     */
    private void footer(GuiGraphics g, int mouseX, int mouseY) {
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();
        g.fill(x(12), y(HEIGHT - 26), x(WIDTH - 12), y(HEIGHT - 25), Draw.EDGE_DARK);
        // Amber, like every other Levy figure in the mod. This is the number every price on the
        // page is being compared against, and it was the one drawn in the same plain white as a
        // row's name -- so the balance read as furniture and the prices read as the decision.
        text(g, WorkbayScreen.gui("upgrades.levy", snap.levy()), x(12), y(HEIGHT - 19),
            WIDTH - 24 - 80, snap.levy() > 0 ? Draw.AMBER : Draw.TEXT_DIM);

        // The dial. Left-click steps up by five, right-click down, the way every cycling control in
        // this mod works — and it clamps at both ends rather than wrapping, because a dial that
        // rolls 25% straight back to nothing is a dial that switches somebody's income off by
        // accident. Right-aligned off the panel's own edge rather than a hardcoded x.
        String rate = WorkbayScreen.gui("skim", snap.skimRate()).getString();
        int w = Draw.width(font, rate) + 10;
        int px = x(WIDTH - 12 - w);
        int py = y(HEIGHT - 23);
        boolean on = snap.skimRate() > 0;
        boolean hover = screen.hovered(px, py, w, 16, mouseX, mouseY);
        Draw.button(g, px, py, w, 16, hover, on);
        text(g, rate, px + 5, py + 4, w - 10, on ? Draw.AMBER : Draw.TEXT_FAINT);
        screen.hit(px, py, w, 16, () -> screen.send(WorkbayAction.SET_SKIM),
            WorkbayScreen.gui("skim.name", snap.skimRate()), WorkbayScreen.gui("skim.tip"));
    }
}
