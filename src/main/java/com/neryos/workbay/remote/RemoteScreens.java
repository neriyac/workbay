package com.neryos.workbay.remote;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.network.RemoteMachinePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is looking at a hosted machine's own screen from somewhere else, and at which machine.
 *
 * <p>Server-side state. {@link com.neryos.workbay.mixin.PlayerMixin} is the only reader that is not
 * ours: vanilla closes a menu whose {@code stillValid} fails, and every menu that uses the standard
 * helper bottoms out in {@code Player#canInteractWithBlock} -- a distance check against a position
 * the player is nowhere near, and in a dimension they are not even in.
 *
 * <p>An entry lives exactly as long as the screen. Nothing here is persisted: a player who logs out
 * with a remote screen open logs back in with no entry, and the reach they had dies with it.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public final class RemoteScreens {
    private static final Map<UUID, GlobalPos> OPEN = new ConcurrentHashMap<>();

    private RemoteScreens() {}

    public static void opened(Player player, GlobalPos machine) {
        OPEN.put(player.getUUID(), machine);
    }

    public static void closed(Player player) {
        OPEN.remove(player.getUUID());
    }

    /**
     * Whether this player is holding a remote screen on the block at {@code pos}.
     *
     * <p>Position only, not dimension: {@code canInteractWithBlock} is asked about the player's own
     * level and never says which block it means. The cost of that is one block, at the exact
     * coordinates of a machine in a bay, reachable from anywhere for as long as its screen is open.
     * Bays are generated far from where anyone builds, and the alternative is a hook that does not
     * exist.
     */
    public static boolean isOpenAt(Player player, BlockPos pos) {
        GlobalPos machine = OPEN.get(player.getUUID());
        return machine != null && machine.pos().equals(pos);
    }

    public static GlobalPos openMachine(Player player) {
        return OPEN.get(player.getUUID());
    }

    /**
     * Opens a hosted machine's own screen where the player is standing.
     *
     * <p>The machine is right-clicked exactly as {@code PortBlock} right-clicks it for a player
     * standing in the bay -- the block's own {@code useWithoutItem}, so it opens its own menu with
     * its own data, and no mod is named here. The only difference is the level: the bay's, not the
     * player's.
     *
     * <p>The client's copy goes first, down the same ordered connection, or the menu's client-side
     * constructor looks up a block entity that is not there yet and the throw disconnects them.
     */
    public static boolean open(ServerPlayer player, ServerLevel bay, BlockPos machine) {
        BlockState hosted = bay.getBlockState(machine);
        BlockEntity entity = bay.getBlockEntity(machine);
        if (hosted.isAir() || entity == null) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new RemoteMachinePacket(machine, hosted,
            entity.saveWithFullMetadata(bay.registryAccess())));

        opened(player, GlobalPos.of(bay.dimension(), machine));
        opening = true;
        try {
            hosted.useWithoutItem(bay, player,
                new BlockHitResult(Vec3.atCenterOf(machine), Direction.NORTH, machine, false));
        } finally {
            opening = false;
        }

        if (player.containerMenu == player.inventoryMenu) {
            close(player);
            return false;
        }
        return true;
    }

    /** Ends the reach and takes the client's copy back. */
    public static void close(ServerPlayer player) {
        GlobalPos machine = OPEN.remove(player.getUUID());
        if (machine != null) {
            PacketDistributor.sendToPlayer(player,
                new RemoteMachinePacket(machine.pos(), net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState(), new CompoundTag()));
        }
    }

    /**
     * True only while {@link #open} is inside the machine's own right-click.
     *
     * <p>Opening a menu closes whichever one was up, so without this the close event fired for the
     * screen the player pressed the button on would delete the entry that same call. Server thread
     * only, which is the only thread that opens a menu.
     */
    private static boolean opening;

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!opening && event.getEntity() instanceof ServerPlayer player) {
            close(player);
        }
    }
}
