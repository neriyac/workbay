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
    private static final int HEIGHT = 232;

    private static final int ROW_X = 118;
    private static final int ROW_W = 194;
    private static final int ROW_Y = 52;
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
    private static final int WELL_Y = 52;
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
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        Draw.well(g, x(WELL_X), y(WELL_Y), WELL_W, WELL_H);
        // The Workbay's own block, rendered by the game rather than drawn: this screen is where a
        // player looks at what they are upgrading.
        PREVIEW.render(g, BlockPreview.facingCamera(
                com.neryos.workbay.init.WBBlocks.WORKBAY.get().defaultBlockState()),
            x(WELL_X + WELL_W / 2), y(WELL_Y + WELL_H / 2), 34);
        g.drawString(font, WorkbayScreen.gui("faces.drag").getString(), x(WELL_X), y(WELL_Y + WELL_H + 3),
            Draw.TEXT_FAINT, false);

        Draw.bar(g, x(WELL_X), y(WELL_Y + WELL_H + 16), WELL_W, 12,
            snap.energy(), snap.energyCapacity(), Draw.AMBER);
        g.drawString(font, snap.energy() + " / " + snap.energyCapacity() + " FE",
            x(WELL_X), y(WELL_Y + WELL_H + 32), Draw.TEXT_DIM, false);
        g.drawString(font, WorkbayScreen.gui("upgrades.rate",
                com.neryos.workbay.content.workbay.WorkbayBlockEntity.MAX_FE_PER_TICK).getString(),
            x(WELL_X), y(WELL_Y + WELL_H + 44), Draw.TEXT_FAINT, false);

        // How many of this network's Workbays are standing, out of how many the server allows.
        // Drawn on the first one a player builds, because the cap is otherwise invisible until they
        // have crafted a second, carried it somewhere and had the placement refused.
        boolean full = snap.deployed() >= snap.maxDeployed();
        g.drawString(font,
            WorkbayScreen.gui("upgrades.deployed", snap.deployed(), snap.maxDeployed()).getString(),
            x(WELL_X), y(WELL_Y + WELL_H + 56), full ? Draw.AMBER : Draw.TEXT_FAINT, false);
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
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        for (WorkbayUpgrade upgrade : WorkbayUpgrade.values()) {
            int index = upgrade.ordinal();
            int px = x(ROW_X);
            int py = y(ROW_Y + index * ROW_PITCH);
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
            clip(g, WorkbayScreen.gui(key), px + TEXT_X, py + 6, NAME_W,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            g.drawString(font, count, px + RIGHT_EDGE - font.width(count), py + 6,
                maxed ? Draw.GREEN : Draw.TEXT_DIM, false);

            // The price is on the row rather than in the tooltip, because it is the thing the
            // player is deciding on and a hover is one step too late for that.
            String price = maxed ? "" : WorkbayScreen.gui("upgrades.levy", cost).getString();
            g.drawString(font, price, px + RIGHT_EDGE - font.width(price), py + 19,
                maxed ? Draw.TEXT_FAINT : affordable ? Draw.GREEN : Draw.RED, false);
            clip(g, WorkbayScreen.gui(key + ".desc"), px + TEXT_X, py + 19, DESC_W,
                Draw.TEXT_FAINT);

            int addX = px + ROW_W - 30;
            int addY = py + 8;
            boolean hover = canInstall && screen.hovered(addX, addY, 22, 18, mouseX, mouseY);
            Draw.button(g, addX, addY, 22, 18, hover, maxed, canInstall);
            WBIcons.draw(g, WBIcons.PLUS, addX + 5, addY + 3,
                canInstall ? Draw.TEXT : Draw.TEXT_FAINT);
            screen.hit(addX, addY, 22, 18,
                canInstall ? () -> screen.send(WorkbayAction.INSTALL_UPGRADE, index) : () -> { },
                WorkbayScreen.gui(key),
                maxed ? WorkbayScreen.gui("upgrades.maxed")
                    : affordable ? WorkbayScreen.gui("upgrades.add", WorkbayScreen.gui(key))
                    : WorkbayScreen.gui("upgrades.unaffordable", snap.levy(), cost),
                maxed ? WorkbayScreen.gui("upgrades.maxed") : WorkbayScreen.gui("upgrades.cost", cost));
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
        g.drawString(font, WorkbayScreen.gui("upgrades.levy", snap.levy()).getString(),
            x(12), y(HEIGHT - 19), snap.levy() > 0 ? Draw.TEXT : Draw.TEXT_DIM, false);

        // The dial. Left-click steps up by five, right-click down, the way every cycling control in
        // this mod works — and it clamps at both ends rather than wrapping, because a dial that
        // rolls 25% straight back to nothing is a dial that switches somebody's income off by
        // accident. Right-aligned off the panel's own edge rather than a hardcoded x.
        String rate = WorkbayScreen.gui("skim", snap.skimRate()).getString();
        int w = font.width(rate) + 10;
        int px = x(WIDTH - 12 - w);
        int py = y(HEIGHT - 23);
        boolean on = snap.skimRate() > 0;
        boolean hover = screen.hovered(px, py, w, 16, mouseX, mouseY);
        Draw.button(g, px, py, w, 16, hover, on);
        g.drawString(font, rate, px + 5, py + 4, on ? Draw.AMBER : Draw.TEXT_FAINT, false);
        screen.hit(px, py, w, 16, () -> screen.send(WorkbayAction.SET_SKIM),
            WorkbayScreen.gui("skim.name", snap.skimRate()), WorkbayScreen.gui("skim.tip"));
    }
}
