package com.neryos.workbay.world;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A hosted machine's own screen, opened by standing in front of it. SPEC.md §5.
 *
 * <p>The screen cannot be opened remotely: the client resolves a menu's block entity out of the one
 * level it has, and a miss disconnects it (OPEN_ISSUES, "Facts worth not rediscovering"). So the
 * player is put in the bay, the machine is right-clicked for them once their client has the chunk,
 * and closing that screen puts them straight back. To the player it is a screen that opened and
 * closed; the trip is an implementation detail.
 *
 * <p><b>Nobody is in the Backshop without an open screen.</b> That rule, not the walls, is what
 * keeps a visitor in the bay: anyone there with no screen up, or outside the bay they were sent
 * to, is put back where they came from on the next tick, whatever moved them. Nothing enters the
 * dimension by any route but {@link #enter}.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public final class BayVisit {
    private BayVisit() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Where a visitor came from, so they can be put back exactly there: the spot, the way they were
     * facing, the machine they went to see — and <b>the screen they left</b>. Coming back to the
     * ground with nothing open is not where they were; they were in the Workbay screen, on a bay,
     * and closing a machine's screen should undo the visit rather than undo that too.
     *
     * <p>The screen half is optional in the codec because this attachment is persisted and a visit
     * saved by an older build has no such field. A missing one just means the old behaviour.
     */
    public record Return(ResourceKey<Level> dimension, Vec3 where, float yRot, float xRot,
        BlockPos machine, java.util.Optional<net.minecraft.core.GlobalPos> workbay, int bay) {
        public static final Codec<Return> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Return::dimension),
            Vec3.CODEC.fieldOf("where").forGetter(Return::where),
            Codec.FLOAT.fieldOf("y_rot").forGetter(Return::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(Return::xRot),
            BlockPos.CODEC.fieldOf("machine").forGetter(Return::machine),
            net.minecraft.core.GlobalPos.CODEC.optionalFieldOf("workbay").forGetter(Return::workbay),
            Codec.INT.optionalFieldOf("bay", 0).forGetter(Return::bay)
        ).apply(instance, Return::new));
    }

    /**
     * Ticks a visitor has waited for the bay chunk to reach their client, the visitors whose chunk
     * has gone, and ticks since a visitor's screen closed. Per player, deliberately not persisted:
     * a visit that is interrupted by a logout is over, and {@link #onLogin} sees to that.
     */
    private static final Map<UUID, Integer> OPENING = new HashMap<>();
    private static final Set<UUID> SENT = new HashSet<>();
    private static final Map<UUID, Integer> CLOSED = new HashMap<>();

    /**
     * Five seconds for the chunk. The server writes it at the end of the tick the player arrives
     * in, so the normal wait is one tick; anything near this is a client that is not receiving.
     */
    public static final int OPEN_TIMEOUT = 100;

    /**
     * Ticks a closed screen may stay closed before the visitor is sent home. A mod that switches
     * containers by closing one and opening another from the client can straddle a tick boundary
     * between the two packets, and a return fired in that gap ejects the player exactly when they
     * open a settings tab. Measured against Mekanism: its own switches (Digital Miner config, the
     * multiblock stats tabs) go through {@code ServerPlayer#openMenu} inside one packet handler and
     * never show a gap at all, so this covers the pattern, not Mekanism.
     */
    public static final int GRACE = 2;

    /** True only while {@link #enter} is moving a player, which is the one admissible route in. */
    private static boolean admitting;

    /**
     * The one interior cell a player fits in: a corner column, two blocks of air, diagonally
     * adjacent to the machine. <b>No geometry constant moves for this</b> — SPEC.md §8 bakes them
     * into saved worlds.
     */
    private static Vec3 standingSpot(ChunkPos column, int bay) {
        BlockPos corner = BayGeometry.shellOrigin(column, bay).offset(1, 1, 1);
        return new Vec3(corner.getX() + 0.5, corner.getY(), corner.getZ() + 0.5);
    }

    /**
     * Sends a player into one of their bays; the machine's screen follows once their client can
     * show it.
     *
     * @return false when there is nothing to visit — no dimension, no such bay, or an empty one,
     *         because a visitor with no screen to open is put out on the next tick anyway
     */
    public static boolean enter(ServerPlayer player, WorkbayRecord record, int bay,
        @org.jetbrains.annotations.Nullable net.minecraft.core.GlobalPos workbay) {
        ServerLevel backshop = player.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null || bay < 0 || bay >= record.bayCapacity()) {
            return false;
        }
        BlockPos machine = BayBuilder.ensure(backshop, record.bayColumn(), bay);
        if (backshop.getBlockState(machine).isAir()) {
            return false;
        }
        player.setData(WBAttachments.BAY_RETURN.get(), new Return(player.level().dimension(),
            player.position(), player.getYRot(), player.getXRot(), machine,
            java.util.Optional.ofNullable(workbay), bay));

        Vec3 spot = standingSpot(record.bayColumn(), bay);
        // Facing east and a little down: from this corner that is the Port on the machine's north
        // face, one block away at eye height, which is what the screen will be in front of.
        OPENING.put(player.getUUID(), 0);
        SENT.remove(player.getUUID());
        CLOSED.remove(player.getUUID());
        admitting = true;
        try {
            player.teleportTo(backshop, spot.x, spot.y, spot.z, Set.of(), -90.0F, 7.0F);
        } finally {
            admitting = false;
        }
        if (!player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            forget(player);
            return false;
        }
        return true;
    }

    /** Puts a visitor back where they came from. Silent and harmless if they are not one. */
    public static boolean leave(ServerPlayer player) {
        if (!isVisiting(player)) {
            return false;
        }
        Return home = player.getData(WBAttachments.BAY_RETURN.get());
        ServerLevel level = player.server.getLevel(home.dimension());
        if (level == null) {
            // Their own dimension is gone - a datapack change, most likely. The overworld is a
            // worse answer than the right one and a much better answer than leaving them sealed in.
            level = player.server.overworld();
        }
        forget(player);
        player.teleportTo(level, home.where().x, home.where().y, home.where().z, Set.of(),
            home.yRot(), home.xRot());
        reopen(player, home);
        return true;
    }

    /**
     * Puts the Workbay screen back up on the bay the visitor left from. Best effort by design: a
     * Workbay that was broken while its owner stood in the bay leaves them on the ground, which is
     * the truth. Never throws the player anywhere — this only ever opens a screen.
     */
    private static void reopen(ServerPlayer player, Return home) {
        if (home.workbay().isEmpty()) {
            return;
        }
        net.minecraft.core.GlobalPos at = home.workbay().get();
        if (!player.level().dimension().equals(at.dimension())) {
            return;
        }
        ServerLevel level = player.server.getLevel(at.dimension());
        if (level == null || !level.isLoaded(at.pos())) {
            return;
        }
        if (level.getBlockEntity(at.pos())
            instanceof com.neryos.workbay.content.workbay.WorkbayBlockEntity workbay
            && workbay.record().isPresent()) {
            com.neryos.workbay.menu.WorkbayMenu.open(player, workbay, home.bay());
        }
    }

    private static void forget(ServerPlayer player) {
        player.removeData(WBAttachments.BAY_RETURN.get());
        OPENING.remove(player.getUUID());
        SENT.remove(player.getUUID());
        CLOSED.remove(player.getUUID());
    }

    /**
     * {@code hasData}, never {@code getData}. Asking for an absent attachment <em>materialises</em>
     * its default, and this one's default is null - which NeoForge then tries to encode at the next
     * player save and logs "Failed to serialize data attachment".
     */
    public static boolean isVisiting(ServerPlayer player) {
        return player.hasData(WBAttachments.BAY_RETURN.get());
    }

    /**
     * The whole visit, one tick at a time: wait for the chunk, open the screen, and send the
     * visitor home the moment they have no screen — or are not where they were put.
     */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            if (isVisiting(player)) {
                // Something else took them out. The record must not outlive the visit, or it is a
                // ticket back in that nothing checked.
                forget(player);
            }
            return;
        }
        if (!isVisiting(player)) {
            toSpawn(player);
            return;
        }
        Return home = player.getData(WBAttachments.BAY_RETURN.get());
        if (!inside(player, home.machine())) {
            leave(player);
            return;
        }
        Integer waited = OPENING.get(id);
        if (waited != null) {
            if (SENT.remove(id)) {
                OPENING.remove(id);
                if (!open(player, home.machine())) {
                    player.displayClientMessage(WorkbayLang.message("bay_no_screen",
                        player.serverLevel().getBlockState(home.machine()).getBlock().getName()), true);
                    leave(player);
                }
            } else if (waited >= OPEN_TIMEOUT) {
                LOGGER.warn("{} waited {} ticks for Backshop chunk {} to reach their client; sending them home",
                    player.getGameProfile().getName(), waited, new ChunkPos(home.machine()));
                player.displayClientMessage(WorkbayLang.message("bay_load_timeout"), true);
                leave(player);
            } else {
                OPENING.put(id, waited + 1);
            }
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            Integer closed = CLOSED.remove(id);
            if (closed != null) {
                LOGGER.debug("{} reopened a screen {} tick(s) after closing one", player.getGameProfile().getName(), closed);
            }
            return;
        }
        int closed = CLOSED.merge(id, 1, Integer::sum);
        LOGGER.debug("{} has had no screen for {} tick(s)", player.getGameProfile().getName(), closed);
        if (closed > GRACE) {
            leave(player);
        }
    }

    /** The interior is the machine and its 26 neighbours; a visitor's feet are always among them. */
    private static boolean inside(ServerPlayer player, BlockPos machine) {
        BlockPos feet = player.blockPosition();
        return Math.abs(feet.getX() - machine.getX()) <= 1
            && Math.abs(feet.getY() - machine.getY()) <= 1
            && Math.abs(feet.getZ() - machine.getZ()) <= 1;
    }

    /**
     * The bay chunk's packet has been written to this visitor's connection. The open packet goes
     * down the same ordered connection, so "written before" is "received before", which is what
     * the client's block-entity lookup needs. Fired by {@code PlayerChunkSender#sendChunk} itself,
     * which is the only honest signal: "tracked and not pending" reads true for a chunk that was
     * not ready when the view was applied and has therefore never been queued at all — measured,
     * by {@code enteringOpensTheMachinesScreenOnlyAfterItsChunkIsSent} going red on it.
     */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerPlayer player = event.getPlayer();
        if (OPENING.containsKey(player.getUUID()) && isVisiting(player)
            && event.getPos().equals(new ChunkPos(player.getData(WBAttachments.BAY_RETURN.get()).machine()))) {
            SENT.add(player.getUUID());
        }
    }

    /**
     * An empty-handed right-click on the machine's north face, which is the Port the visitor is
     * looking at. The same call {@code PortBlock} forwards, so every mod's screen opens with no
     * per-mod code; a block that answers a click with no screen is a block that cannot be visited.
     */
    private static boolean open(ServerPlayer player, BlockPos machine) {
        BlockState hosted = player.serverLevel().getBlockState(machine);
        if (!hosted.isAir()) {
            hosted.useWithoutItem(player.serverLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(machine), Direction.NORTH, machine, false));
        }
        return player.containerMenu != player.inventoryMenu;
    }

    /** No recorded way home: the spawn point is the only answer that is certainly not solid rock. */
    private static void toSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
            Set.of(), 0.0F, 0.0F);
    }

    /** The Backshop admits nobody but a visitor {@link #enter} is moving, whatever mod is asking. */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getDimension().equals(WorkbayDimensions.BACKSHOP) && !admitting) {
            event.setCanceled(true);
        }
    }

    /**
     * Nobody logs back in inside a bay. Someone who quits inside one and comes back to find the
     * Workbay broken or the mod's dimension missing would be entombed with no block to break — so
     * the return happens on the way in, before they can discover any of that.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level().dimension().equals(WorkbayDimensions.BACKSHOP)
            && !leave(player)) {
            toSpawn(player);
        }
    }
}
