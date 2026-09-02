package com.neryos.cleanenv.content.counter;

import com.neryos.cleanenv.init.CEMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * One menu, one synced field. The field travels through {@link ContainerData}, which
 * vanilla already diffs and sends every tick to whoever has the screen open — no custom
 * packet needed for a plain int.
 */
public class CounterMenu extends AbstractContainerMenu {

    private final ContainerData data;

    /** Client side: no block entity exists here, so the data starts empty and is filled by sync. */
    public CounterMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainerData(1));
    }

    /** Server side: reads straight from the block entity. */
    public CounterMenu(int containerId, Inventory playerInventory, ContainerData data) {
        super(CEMenus.COUNTER.get(), containerId);
        this.data = data;
        addDataSlots(data);
    }

    public int getCount() {
        return data.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No slots in this menu, so there is nothing to shift-click.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
