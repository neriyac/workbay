package com.neryos.workbay.content.connector;

import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBBlockEntities;
import com.neryos.workbay.init.WBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The world end of a link. SPEC.md §0 and §9.
 *
 * <p>It stores which Workbay it is paired to and nothing else — the links themselves live on the
 * Workbay, because that is what ticks them and what the screen reads. This block entity is only how
 * a position in the world finds its way back to that list.
 */
public class ConnectorBlockEntity extends BlockEntity {
    private static final String PAIRING_KEY = "Pairing";

    @Nullable
    private ConnectorPairing pairing;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(WBBlockEntities.CONNECTOR.get(), pos, state);
    }

    public Optional<ConnectorPairing> pairing() {
        return Optional.ofNullable(pairing);
    }

    public void pairTo(ConnectorPairing newPairing) {
        this.pairing = newPairing;
        setChanged();
    }

    /**
     * The Workbay this Connector belongs to, or empty when it is unpaired, its Workbay's chunk is
     * not loaded, or the block that used to be there is a different Workbay now.
     *
     * <p>Only ever called from a player action — placing, using or breaking the Connector — never
     * from a tick, because {@code getBlockEntity} on an arbitrary position can trigger a synchronous
     * chunk load on the server thread (SPEC.md §9).
     */
    public Optional<WorkbayBlockEntity> workbay() {
        if (pairing == null || !(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        GlobalPos at = pairing.workbayPos();
        ServerLevel workbayLevel = server.getServer().getLevel(at.dimension());
        if (workbayLevel == null || !workbayLevel.isLoaded(at.pos())) {
            return Optional.empty();
        }
        if (!(workbayLevel.getBlockEntity(at.pos()) instanceof WorkbayBlockEntity workbay)) {
            return Optional.empty();
        }
        return workbay.workbayId().filter(id -> id.equals(pairing.workbayId())).map(id -> workbay);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (pairing != null) {
            tag.put(PAIRING_KEY, ConnectorPairing.CODEC.encodeStart(NbtOps.INSTANCE, pairing)
                .getOrThrow(e -> new IllegalStateException("could not write a Connector pairing: " + e)));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pairing = tag.contains(PAIRING_KEY)
            ? ConnectorPairing.CODEC.parse(NbtOps.INSTANCE, tag.get(PAIRING_KEY)).result().orElse(null)
            : null;
    }

    /** A broken Connector drops one that is still paired, so re-placing it restores the link. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (pairing != null) {
            components.set(WBDataComponents.PAIRING.get(), pairing);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        ConnectorPairing carried = input.get(WBDataComponents.PAIRING.get());
        if (carried != null) {
            pairing = carried;
        }
    }

    /** Otherwise the pairing is in {@code block_entity_data} as well and ctrl-pick clones it. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(PAIRING_KEY);
    }
}
