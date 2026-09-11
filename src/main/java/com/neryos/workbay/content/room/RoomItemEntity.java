package com.neryos.workbay.content.room;

import com.neryos.workbay.init.WBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * A room lying on the ground. SPEC.md §0: never destroyed.
 *
 * <p>Two of the six ways an item dies are the entity's and not the item's, so a room on the
 * ground is this rather than a plain {@link ItemEntity}: it <b>never ages</b> (vanilla's own
 * unlimited-lifetime mark, which also stops two rooms merging into a stack), and one that falls
 * out of the world <b>turns up on the overworld's spawn</b> instead of being discarded -- the one
 * place every player can find and nothing can hide.
 */
public class RoomItemEntity extends ItemEntity {

    public RoomItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
    }

    /** Takes the place of the plain entity NeoForge was about to spawn, motion and all. */
    public RoomItemEntity(Level level, Entity was, ItemStack stack) {
        this(WBEntities.ROOM_ITEM.get(), level);
        setPos(was.getX(), was.getY(), was.getZ());
        setDeltaMovement(was.getDeltaMovement());
        setItem(stack.copy());
        if (was instanceof ItemEntity item && item.getOwner() != null) {
            setThrower(item.getOwner());
        }
        setDefaultPickUpDelay();
    }

    @Override
    public void tick() {
        setUnlimitedLifetime();
        super.tick();
    }

    /**
     * The void. Vanilla discards anything sixty-four blocks under the world; a room goes to the
     * overworld's spawn instead, on top of whatever stands there, and stops falling.
     */
    @Override
    protected void onBelowWorld() {
        if (!(level() instanceof ServerLevel here)) {
            return;
        }
        ServerLevel overworld = here.getServer().overworld();
        BlockPos spawn = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
            overworld.getSharedSpawnPos());
        Vec3 at = Vec3.atBottomCenterOf(spawn);
        setDeltaMovement(Vec3.ZERO);
        teleportTo(overworld, at.x, at.y, at.z, Set.of(), getYRot(), getXRot());
    }
}
