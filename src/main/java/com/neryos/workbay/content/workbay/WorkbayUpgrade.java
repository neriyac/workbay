package com.neryos.workbay.content.workbay;

import com.neryos.workbay.config.WorkbayConfig;
import com.neryos.workbay.init.WBItems;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Every upgrade a v1 Workbay can hold, on one enum. SPEC.md §1.
 *
 * <p>Upgrades are <b>consumed on install</b> and recorded as counters, so there is no upgrade
 * inventory, no slot grid and no removal path. The dropped Workbay item carries every counter, so
 * moving a base loses nothing.
 *
 * <p>The Anchor, the Room Frames and the Annex Plate are in the tier table but are v2 (SPEC.md §16),
 * and are deliberately absent here rather than present and inert.
 */
public enum WorkbayUpgrade implements StringRepresentable {
    /**
     * <b>The maximum is a config value, not a number argued for here.</b> How long the ladder
     * should be is a balance question, and balance numbers in this mod come from measurement -
     * so the ceiling is {@code maxBaysPerWorkbay} minus the bays the base Workbay already grants,
     * and a pack that has actually played to the top can move it without a release. It is derived
     * from that knob rather than being a second one: two numbers that can disagree about the same
     * ceiling is how a player ends up holding a plate that installs and does nothing.
     */
    EXPANSION_PLATE("expansion_plate",
        () -> WorkbayConfig.SERVER.maxBaysPerWorkbay.get() - WorkbayRecord.BASE_BAYS,
        2, 6, () -> WBItems.EXPANSION_PLATE.get()),
    RESONATOR("resonator", () -> 1, 24, 0, () -> WBItems.RESONATOR.get()),
    MULTICHANNEL("multichannel", () -> 1, 24, 0, () -> WBItems.MULTICHANNEL.get());

    private final String name;
    private final IntSupplier max;
    private final int baseCost;
    private final int costStep;
    private final Supplier<Item> item;

    WorkbayUpgrade(String name, IntSupplier max, int baseCost, int costStep, Supplier<Item> item) {
        this.name = name;
        this.max = max;
        this.baseCost = baseCost;
        this.costStep = costStep;
        this.item = item;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int max() {
        return Math.max(0, max.getAsInt());
    }

    /**
     * What installing the next one costs in Levy. SPEC.md §1: "rising", which a crafting recipe
     * cannot express - a recipe costs the same the tenth time as the first. The rise lives here,
     * where the install happens and the screen can name it.
     *
     * <p><b>The first rung is cheap and the climb is steep.</b> Plate 1 costs 2 and plate 6 costs
     * 32, so the whole ladder is 102 Levy where it used to be 42. That is where the entry cost
     * went: SPEC.md §0 prices the upgrades, not the entry, and the recipes below the first Workbay
     * were carrying weight that belongs here. A first plate the player reaches in a couple of
     * minutes is what teaches them the dial is worth turning; everything after it is the game.
     */
    public int levyCost(int installed) {
        return baseCost + costStep * Math.max(0, installed);
    }

    public Item item() {
        return item.get();
    }
}
