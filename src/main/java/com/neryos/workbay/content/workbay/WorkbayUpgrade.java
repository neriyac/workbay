package com.neryos.workbay.content.workbay;

import com.neryos.workbay.init.WBItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

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
    EXPANSION_PLATE("expansion_plate", 7, () -> WBItems.EXPANSION_PLATE.get()),
    RESONATOR("resonator", 1, () -> WBItems.RESONATOR.get()),
    MULTICHANNEL("multichannel", 1, () -> WBItems.MULTICHANNEL.get());

    private final String name;
    private final int max;
    private final Supplier<Item> item;

    WorkbayUpgrade(String name, int max, Supplier<Item> item) {
        this.name = name;
        this.max = max;
        this.item = item;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int max() {
        return max;
    }

    public Item item() {
        return item.get();
    }
}
