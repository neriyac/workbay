package com.neryos.workbay.bus;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Moving things. Knows nothing about any mod. SPEC.md §9.
 *
 * <p>Two rules run through all of it. <b>Simulate, then commit, always against an explicit
 * budget</b>, so a machine removed mid-operation can never leave a partial move behind. And
 * <b>{@code copy()} before handing a stack to a foreign handler</b> — both are defences EnderIO
 * carries against real misbehaving mods, and both cost nothing.
 *
 * <p>Whole stacks per operation, never one item at a time: rates are per tick everywhere in this
 * mod, and moving single items would make a rate of 64 mean sixty-four separate handler round trips.
 */
public final class BusTransfer {
    private BusTransfer() {}

    /**
     * Moves up to {@code budget} items from one handler to another.
     *
     * @return how many items actually moved
     */
    public static int moveItems(IItemHandler from, IItemHandler to, int budget) {
        return moveItems(from, to, budget, stack -> true);
    }

    /**
     * @param allowed the link's filter. Applied to what the source offers, before anything is
     *                committed, so a filtered link never has to put an item back.
     */
    public static int moveItems(IItemHandler from, IItemHandler to, int budget,
        java.util.function.Predicate<ItemStack> allowed) {
        if (budget <= 0) {
            return 0;
        }
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack available = from.extractItem(slot, budget, true);
            if (available.isEmpty() || !allowed.test(available)) {
                continue;
            }
            // A handler may hand back more than a stack in one go; clamp so the foreign insert is
            // never asked to take an over-sized stack it may or may not handle correctly.
            int wanted = Math.min(available.getCount(), available.getMaxStackSize());
            ItemStack offer = available.copy();
            offer.setCount(wanted);

            ItemStack refused = ItemHandlerHelper.insertItemStacked(to, offer.copy(), true);
            int accepted = wanted - refused.getCount();
            if (accepted <= 0) {
                continue;
            }

            ItemStack taken = from.extractItem(slot, accepted, false);
            if (taken.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(to, taken.copy(), false);
            if (!leftover.isEmpty()) {
                // The destination changed its mind between the simulation and the commit. Put the
                // remainder back rather than dropping it: this is exactly how items go missing.
                ItemHandlerHelper.insertItemStacked(from, leftover, false);
                return taken.getCount() - leftover.getCount();
            }
            return taken.getCount();
        }
        return 0;
    }

    /**
     * Moves up to {@code budget} energy. Energy handlers already simulate natively, so the pattern
     * is the same shape: ask what would move, then move exactly that.
     */
    public static int moveEnergy(IEnergyStorage from, IEnergyStorage to, int budget) {
        if (budget <= 0 || !from.canExtract() || !to.canReceive()) {
            return 0;
        }
        int available = from.extractEnergy(budget, true);
        if (available <= 0) {
            return 0;
        }
        int accepted = to.receiveEnergy(available, true);
        if (accepted <= 0) {
            return 0;
        }
        int taken = from.extractEnergy(accepted, false);
        int moved = to.receiveEnergy(taken, false);
        if (moved < taken) {
            // Give back what the destination refused after saying it would take it.
            from.receiveEnergy(taken - moved, false);
        }
        return moved;
    }
}
