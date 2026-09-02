package com.neryos.cleanenv.content.counter;

import com.neryos.cleanenv.init.CEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder block entity for the Phase 1 vertical slice. It holds one number, ticks it
 * up once a second, persists it across save/load, and exposes it to an open menu.
 * <p>
 * The point is the wiring, not the behaviour. Delete it once real content exists.
 */
public class CounterBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_COUNT = "Count";
    private static final int TICKS_PER_STEP = 20;

    private int count = 0;
    private int ticksSinceLastStep = 0;

    /** Server-to-client bridge for the one synced field. Index 0 is {@link #count}. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? count : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                count = value;
            }
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public CounterBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntities.COUNTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CounterBlockEntity be) {
        be.ticksSinceLastStep++;
        if (be.ticksSinceLastStep >= TICKS_PER_STEP) {
            be.ticksSinceLastStep = 0;
            be.count++;
            // Without this the new value never reaches disk.
            be.setChanged();
        }
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
        setChanged();
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_COUNT, count);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        count = tag.getInt(TAG_COUNT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cleanenv.counter_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CounterMenu(containerId, playerInventory, data);
    }
}
