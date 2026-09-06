package com.neryos.workbay.bus;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
        return moveItems(from, to, budget, allowed, false);
    }

    /**
     * @param simulate ask what this move would carry and commit nothing. This is what a bus binds
     *                 its destination on: a handler's slot count and its own {@code isItemValid}
     *                 are both things it <em>says</em>, and both are wrong on shipping mods
     *                 (SPEC.md §9). The same call the commit will make cannot lie.
     */
    public static int moveItems(IItemHandler from, IItemHandler to, int budget,
        java.util.function.Predicate<ItemStack> allowed, boolean simulate) {
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
            if (simulate) {
                return accepted;
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
     * Takes up to {@code budget} matching items out of a handler and <b>destroys them</b>. The
     * Assay's skim (SPEC.md §3): the goods do not go anywhere, they become Levy, and there is no
     * buffer for them to sit in because the Assay has no faces for one to be reachable through.
     *
     * <p>Taken from the <em>source</em> before the move rather than deducted from what arrives, so
     * a partly-refused insert can never leave the tax having been charged twice.
     *
     * @return how many items were actually taken
     */
    public static int take(IItemHandler from, int budget,
        java.util.function.Predicate<ItemStack> allowed) {
        int taken = 0;
        for (int slot = 0; slot < from.getSlots() && taken < budget; slot++) {
            ItemStack sample = from.extractItem(slot, budget - taken, true);
            if (sample.isEmpty() || !allowed.test(sample)) {
                continue;
            }
            taken += from.extractItem(slot, sample.getCount(), false).getCount();
        }
        return taken;
    }

    /**
     * Moves up to {@code budget} millibuckets. Same shape as the item path, for the same reason:
     * ask the source what it is offering, then ask the destination what it would take <em>of that
     * offer</em>, then commit exactly that.
     *
     * <p><b>The commit drains by stack, not by amount.</b> {@code drain(int, EXECUTE)} takes from
     * whichever tank the handler feels like, so on a multi-tank machine it can hand back a
     * different fluid from the one the destination agreed to accept — and then the fill refuses and
     * the difference has to be pushed back into a handler that may not take it. Draining the exact
     * stack that was simulated cannot pick a different fluid.
     *
     * @param simulate ask what this move would carry and commit nothing. What a bus binds its
     *                 destination on. SPEC.md §9.
     */
    public static int moveFluid(IFluidHandler from, IFluidHandler to, int budget) {
        return moveFluid(from, to, budget, stack -> true, false);
    }

    /**
     * The most this source would hand over of something the filter allows, or empty.
     *
     * <p><b>Tank by tank, not {@code drain(budget, ...)}.</b> An amount-drain takes from whichever
     * tank the handler feels like, so on a machine holding water in one tank and lava in another a
     * filter asking for water gets offered lava and the link reads as empty. This is the fluid
     * spelling of the item path's per-slot loop, and it is the same reason.
     */
    public static FluidStack offer(IFluidHandler from, int budget,
        java.util.function.Predicate<FluidStack> allowed) {
        for (int tank = 0; tank < from.getTanks(); tank++) {
            FluidStack held = from.getFluidInTank(tank);
            if (held.isEmpty() || !allowed.test(held)) {
                continue;
            }
            FluidStack available = from.drain(
                held.copyWithAmount(Math.min(budget, held.getAmount())),
                IFluidHandler.FluidAction.SIMULATE);
            if (!available.isEmpty()) {
                return available;
            }
        }
        return FluidStack.EMPTY;
    }

    /**
     * @param allowed the link's filter, applied to what the source is offering before anything is
     *                committed. SPEC.md §5: whatever decides a match runs before the move, never
     *                after it, or a refused fluid has to be pushed back into a handler that may not
     *                take it.
     */
    public static int moveFluid(IFluidHandler from, IFluidHandler to, int budget,
        java.util.function.Predicate<FluidStack> allowed, boolean simulate) {
        if (budget <= 0) {
            return 0;
        }
        FluidStack available = offer(from, budget, allowed);
        if (available.isEmpty()) {
            return 0;
        }
        int accepted = to.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }
        if (simulate) {
            return accepted;
        }
        FluidStack taken = from.drain(available.copyWithAmount(accepted),
            IFluidHandler.FluidAction.EXECUTE);
        if (taken.isEmpty()) {
            return 0;
        }
        int moved = to.fill(taken, IFluidHandler.FluidAction.EXECUTE);
        if (moved < taken.getAmount()) {
            // The destination changed its mind between the simulation and the commit. Put the
            // remainder back rather than dropping it, exactly as the item path does.
            from.fill(taken.copyWithAmount(taken.getAmount() - moved),
                IFluidHandler.FluidAction.EXECUTE);
        }
        return moved;
    }

    /**
     * Moves up to {@code budget} energy. Energy handlers already simulate natively, so the pattern
     * is the same shape: ask what would move, then move exactly that.
     */
    public static int moveEnergy(IEnergyStorage from, IEnergyStorage to, int budget) {
        // No canExtract/canReceive gate. They are what a handler *says*, Mekanism's FE wrapper
        // hardcodes both to true on every face, and the simulation two lines down is the same call
        // the commit makes - so the flags could only ever turn a working move away. SPEC.md §9.
        if (budget <= 0) {
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
