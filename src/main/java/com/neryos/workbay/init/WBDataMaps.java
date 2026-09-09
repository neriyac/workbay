package com.neryos.workbay.init;

import com.mojang.serialization.Codec;
import com.neryos.workbay.Workbay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What one item is worth to the Assay. SPEC.md §3 and §13, OPEN_ISSUES #34.
 *
 * <p><b>A Levy was sixty-four of anything.</b> {@code #workbay:levy_input} says <em>whether</em>
 * something feeds the Assay and never how much, so a diamond and a copper ingot were the same
 * sixty-fourth of an upgrade — the one balance number §13 already admitted wants to be data rather
 * than a config scalar, because it is a table with entries in it and every other list of things in
 * this mod is a datapack.
 *
 * <p><b>A data map rather than a datapack registry.</b> It is NeoForge's own answer to exactly this
 * shape — a value attached to entries of an existing registry — and it takes <b>tags</b> as keys,
 * which is what makes one line cover every ingot in every mod. A registry of our own would have
 * needed a reload listener, a sync packet and a code path to resolve tags itself.
 *
 * <p>Unsynced: the skim runs on the server and the only number a screen prints is the running
 * total, which is on the record and already synced.
 */
public final class WBDataMaps {
    private WBDataMaps() {}

    /**
     * How much one of this item counts for. Absent means <b>one</b>, which is what every item was
     * worth before this existed, so a pack that ships nothing keeps the old behaviour exactly.
     */
    public static final DataMapType<net.minecraft.world.item.Item, Integer> LEVY_VALUE =
        DataMapType.builder(Workbay.rl("levy_value"), Registries.ITEM, Codec.INT).build();

    /** The default, and the reason a missing entry is never a reason to skim nothing. */
    public static final int DEFAULT_LEVY_VALUE = 1;

    /**
     * What this whole stack is worth. Clamped at zero so a pack cannot write a negative value and
     * make a link that <em>earns</em> the player goods.
     */
    public static int levyValue(ItemStack stack) {
        Integer each = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(LEVY_VALUE);
        return Math.max(0, each == null ? DEFAULT_LEVY_VALUE : each) * stack.getCount();
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterDataMapTypesEvent event) -> event.register(LEVY_VALUE));
    }
}
