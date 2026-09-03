package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.workbay.WorkbayUpgrade;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbaySnapshot;
import com.neryos.workbay.world.FaceConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
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

    /** The block preview turns as the player looks at it, since it is the only art on the screen. */
    private static boolean flipped;

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
        footer(g);
    }

    /** Left: the block, its power bar at full size with exact figures, and the incoming rate. */
    private void preview(GuiGraphics g, int mouseX, int mouseY) {
        var font = screen.font();
        WorkbaySnapshot snap = snapshot();

        Draw.well(g, x(12), y(52), 96, 74);
        Direction[] shown = flipped
            ? new Direction[] { Direction.UP, Direction.EAST, Direction.NORTH }
            : new Direction[] { Direction.UP, Direction.WEST, Direction.SOUTH };
        // The same isometric cube the bay's face config uses, with every face neutral: this one is
        // a picture of the block, not a control.
        Draw.isoCube(g, font, x(60), y(84), 30, FaceConfig.NONE, BusConfig.Resource.ITEM,
            shown[0], shown[1], shown[2]);
        iconButton(g, mouseX, mouseY, x(90), y(106), WBIcons.ROTATE, flipped, () -> flipped = !flipped,
            WorkbayScreen.gui("faces.rotate"), WorkbayScreen.gui("faces.rotate.tip"));

        Draw.bar(g, x(12), y(132), 96, 12, snap.energy(), snap.energyCapacity(), Draw.AMBER);
        g.drawString(font, snap.energy() + " / " + snap.energyCapacity() + " FE", x(12), y(148),
            Draw.TEXT_DIM, false);
        g.drawString(font, WorkbayScreen.gui("upgrades.rate",
                com.neryos.workbay.content.workbay.WorkbayBlockEntity.MAX_FE_PER_TICK).getString(),
            x(12), y(160), Draw.TEXT_FAINT, false);
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

            Draw.well(g, px, py, ROW_W, ROW_H);

            ItemStack icon = new ItemStack(upgrade.item());
            g.renderItem(icon, px + 6, py + 9);

            String key = "upgrade." + upgrade.getSerializedName();
            g.drawString(font, WorkbayScreen.gui(key).getString(), px + 28, py + 6,
                maxed ? Draw.TEXT_FAINT : Draw.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(
                    WorkbayScreen.gui(key + ".desc").getString(), ROW_W - 84),
                px + 28, py + 19, Draw.TEXT_FAINT, false);

            g.drawString(font, installed + " / " + upgrade.max(), px + ROW_W - 66, py + 13,
                maxed ? Draw.GREEN : Draw.TEXT_DIM, false);

            int addX = px + ROW_W - 30;
            int addY = py + 8;
            if (maxed) {
                Draw.button(g, addX, addY, 22, 18, false, true);
                WBIcons.draw(g, WBIcons.PLUS, addX + 5, addY + 3, Draw.TEXT_FAINT);
                screen.hit(addX, addY, 22, 18, () -> { },
                    WorkbayScreen.gui(key), WorkbayScreen.gui("upgrades.maxed"));
            } else {
                boolean hover = screen.hovered(addX, addY, 22, 18, mouseX, mouseY);
                Draw.button(g, addX, addY, 22, 18, hover, false);
                WBIcons.draw(g, WBIcons.PLUS, addX + 5, addY + 3, Draw.TEXT);
                screen.hit(addX, addY, 22, 18,
                    () -> screen.send(WorkbayAction.INSTALL_UPGRADE, index),
                    WorkbayScreen.gui(key), WorkbayScreen.gui("upgrades.add",
                        WorkbayScreen.gui(key)), WorkbayScreen.gui(key + ".cost"));
            }
        }
    }

    /** Levy in stock and the tax rate. The Assay is not built, so the rate is honestly zero. */
    private void footer(GuiGraphics g) {
        var font = screen.font();
        g.fill(x(12), y(HEIGHT - 26), x(WIDTH - 12), y(HEIGHT - 25), Draw.EDGE_DARK);
        g.drawString(font, WorkbayScreen.gui("upgrades.levy", snapshot().levyInInventory()).getString(),
            x(12), y(HEIGHT - 19), Draw.TEXT_DIM, false);
        g.drawString(font, WorkbayScreen.gui("upgrades.tax", 0).getString(),
            x(178), y(HEIGHT - 19), Draw.TEXT_FAINT, false);
    }
}
