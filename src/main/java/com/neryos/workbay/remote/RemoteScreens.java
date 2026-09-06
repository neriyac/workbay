package com.neryos.workbay.remote;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.network.RemoteMachinePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    /** Where a machine really is: the bay's level, and its position in it. */
    public record Open(ServerLevel bay, BlockPos pos) {}

    /**
     * How often the viewer's copy is brought up to date, in ticks.
     *
     * <p>A machine tells its watchers about a change by sending to whoever tracks its chunk
     * <em>in its own level</em>, and a player looking from another dimension tracks nothing. So the
     * change lands on the machine and the screen keeps drawing the moment it was opened, which is
     * indistinguishable from a button that does nothing. This is the missing half of the chunk a
     * remote viewer never got.
     */
    private static final int REFRESH = 5;

    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();

    /** The last tag each viewer was sent, so an unchanged machine costs one comparison. */
    private static final Map<UUID, CompoundTag> SENT = new ConcurrentHashMap<>();

    private RemoteScreens() {}

    public static void opened(Player player, ServerLevel bay, BlockPos machine) {
        OPEN.put(player.getUUID(), new Open(bay, machine));
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
        Open machine = OPEN.get(player.getUUID());
        return machine != null && machine.pos().equals(pos);
    }

    /**
     * The machine itself, answered to whoever asks the wrong level for it.
     *
     * <p>This is the whole of what the mod is willing to fake, and it is deliberately a fact about
     * a <b>block</b>: one machine, at its own real coordinates, findable while its own screen is
     * open. <b>Nothing about the player is ever faked</b> — not their level, not their position,
     * not their distance to anything — so a mod that gates on how close somebody is standing still
     * measures the real gap and still says no. What a player gains here is exactly the authority
     * they already have by walking into the bay, which is the test for whether this is a cheat.
     *
     * <p>{@code level != bay} both stops this answering the bay's own lookups — which would
     * recurse — and keeps the machine from appearing twice in the level it really lives in.
     */
    public static BlockEntity machineAt(Level level, BlockPos pos) {
        if (OPEN.isEmpty()) {
            return null;
        }
        for (Open machine : OPEN.values()) {
            if (machine.pos().equals(pos) && level != machine.bay()) {
                return machine.bay().getBlockEntity(pos);
            }
        }
        return null;
    }

    /**
     * Whether an open machine sits in this chunk. The other half of the same question: mods ask
     * whether a chunk is loaded <em>before</em> they ask for a block entity, and give up silently
     * if it is not (Mekanism's {@code WorldUtils.isBlockLoaded}, the near-universal shape).
     */
    public static boolean chunkHasOpenMachine(int chunkX, int chunkZ) {
        if (OPEN.isEmpty()) {
            return false;
        }
        for (Open machine : OPEN.values()) {
            if (SectionPos.blockToSectionCoord(machine.pos().getX()) == chunkX
                && SectionPos.blockToSectionCoord(machine.pos().getZ()) == chunkZ) {
                return true;
            }
        }
        return false;
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
        CompoundTag tag = entity.saveWithFullMetadata(bay.registryAccess());
        SENT.put(player.getUUID(), tag);
        PacketDistributor.sendToPlayer(player, new RemoteMachinePacket(machine, hosted, tag));

        opened(player, bay, machine);
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

    /** Sends the copy again whenever the machine differs from what that viewer was last sent. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (OPEN.isEmpty() || event.getServer().getTickCount() % REFRESH != 0) {
            return;
        }
        // Named for the profiler: this is one getUpdateTag per viewer per five ticks, and it is
        // the only per-tick cost a screen anywhere in the mod adds to the server.
        event.getServer().getProfiler().push("workbay:remoteScreens");
        try {
            resend(event);
        } finally {
            event.getServer().getProfiler().pop();
        }
    }

    private static void resend(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, Open> entry : OPEN.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Open machine = entry.getValue();
            BlockEntity live = machine.bay().getBlockEntity(machine.pos());
            if (live == null) {
                continue;
            }
            // getUpdateTag, not the full save: this is the packet a chunk-tracking client would be
            // sent, and mods wire their client-visible state into that pair on purpose. Mekanism's
            // side config arrives through TileComponentConfig#readFromUpdateTag and through
            // nothing else -- its container sync carries only the eject flag, which is exactly why
            // Eject worked here and the faces did not.
            CompoundTag tag = live.getUpdateTag(machine.bay().registryAccess());
            if (!tag.equals(SENT.get(entry.getKey()))) {
                SENT.put(entry.getKey(), tag);
                PacketDistributor.sendToPlayer(player, new RemoteMachinePacket(machine.pos(),
                    machine.bay().getBlockState(machine.pos()), tag));
            }
        }
    }

    /** Ends the reach and takes the client's copy back. */
    public static void close(ServerPlayer player) {
        SENT.remove(player.getUUID());
        Open machine = OPEN.remove(player.getUUID());
        if (machine != null) {
            PacketDistributor.sendToPlayer(player, new RemoteMachinePacket(machine.pos(),
                Blocks.AIR.defaultBlockState(), new CompoundTag()));
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
