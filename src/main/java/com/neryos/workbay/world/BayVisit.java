package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Standing in the bay, in front of the real machine. SPEC.md §5.
 *
 * <p><b>Why the player moves rather than the screen.</b> Bay View can only ever show what a
 * capability exposes — item slots, and nothing else. Recipe modes, side configuration, security and
 * upgrade slots are each mod's own, reachable through no shared contract, and re-drawing them per
 * mod is the infinite work SPEC.md §0 rules out. The obvious alternative, opening the hosted
 * block's own menu across the dimension boundary, was built and measured against a real Mekanism
 * machine, and it does not merely fail: the server sends the open packet happily, then the client
 * looks the position up in the only level it has, finds nothing, and <em>disconnects</em> —
 * {@code IllegalStateException: Client could not locate tile at BlockPos{x=8, y=26, z=8}}. See
 * OPEN_ISSUES "Facts worth not rediscovering".
 *
 * <p>So the machine's own screen is reachable exactly one way: be next to it. Then a right-click is
 * an ordinary right-click, every mod's GUI works with zero per-mod code, and nothing can be kicked
 * for. Compact Machines has run on that answer for years.
 *
 * <p><b>The player never has to eject a machine to configure it</b>, which is the chore this whole
 * mod exists to remove — so this is a core requirement, not a convenience.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public final class BayVisit {
    private BayVisit() {}

    /** Where a visitor came from, so they can be put back exactly there. */
    public record Return(ResourceKey<Level> dimension, Vec3 where, float yRot, float xRot) {
        public static final Codec<Return> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
                .fieldOf("dimension").forGetter(Return::dimension),
            Vec3.CODEC.fieldOf("where").forGetter(Return::where),
            Codec.FLOAT.fieldOf("y_rot").forGetter(Return::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(Return::xRot)
        ).apply(instance, Return::new));
    }

    /**
     * How long sneak has been held, in ticks, per player. Deliberately not persisted: a hold that
     * is interrupted by a logout should start again, and a map that outlives the session is a leak.
     */
    private static final Map<UUID, Integer> HOLDING = new HashMap<>();

    /** Twenty ticks. Long enough that a sneak-place inside the bay does not throw you out. */
    private static final int HOLD_TICKS = 20;

    /**
     * The one interior cell a player fits in.
     *
     * <p>The bay is a 3x3x3 interior with the machine at its centre and a Port on each of the six
     * faces, which leaves the eight corners and twelve edge cells. A corner column — the corner and
     * the cell above it — is two blocks of air, and it is diagonally adjacent to the machine, which
     * is a metre and a half from the eye and well inside reach. <b>No geometry constant moves for
     * this</b>: SPEC.md §8 bakes them into saved worlds and changing one strands every machine
     * already hosted.
     */
    private static Vec3 standingSpot(net.minecraft.world.level.ChunkPos column, int bay) {
        BlockPos corner = BayGeometry.shellOrigin(column, bay).offset(1, 1, 1);
        return new Vec3(corner.getX() + 0.5, corner.getY(), corner.getZ() + 0.5);
    }

    /**
     * Sends a player into one of their bays.
     *
     * @return false when there is nothing to visit, so the caller can say so out loud
     */
    public static boolean enter(ServerPlayer player, WorkbayRecord record, int bay) {
        ServerLevel backshop = player.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null || bay < 0 || bay >= record.bayCapacity()) {
            return false;
        }
        // Build it if it is not there. A player standing in an unbuilt bay would be inside solid
        // rock, and an empty bay is a perfectly reasonable thing to want to look at.
        BayBuilder.ensure(backshop, record.bayColumn(), bay);

        player.setData(WBAttachments.BAY_RETURN.get(), new Return(player.level().dimension(),
            player.position(), player.getYRot(), player.getXRot()));

        Vec3 spot = standingSpot(record.bayColumn(), bay);
        // Facing east and a little down, which from this corner is the Port on the machine's north
        // face, one block away at eye height. Not the machine: six Ports seal it, so the Port is
        // what a player can actually click - and PortBlock passes the click straight through.
        player.teleportTo(backshop, spot.x, spot.y, spot.z, Set.of(), -90.0F, 7.0F);
        player.displayClientMessage(WorkbayLang.message("bay_entered"), false);
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
        player.removeData(WBAttachments.BAY_RETURN.get());
        HOLDING.remove(player.getUUID());
        player.teleportTo(level, home.where().x, home.where().y, home.where().z, Set.of(),
            home.yRot(), home.xRot());
        return true;
    }

    /**
     * {@code hasData}, never {@code getData}. Asking for an absent attachment <em>materialises</em>
     * its default, and this one's default is null - which NeoForge then tries to encode at the next
     * player save and logs "Failed to serialize data attachment". Caught by the gametest's own
     * teardown, which is the only place a mock player is ever written to disk.
     */
    public static boolean isVisiting(ServerPlayer player) {
        return player.hasData(WBAttachments.BAY_RETURN.get());
    }

    /**
     * Hold sneak to leave.
     *
     * <p>A held key rather than a block to click, because the bay is sealed: whatever the exit is,
     * it has to work with the player boxed into two blocks of air beside a machine whose own screen
     * may be open. Nothing in a bay can swallow a keypress, and a mod's GUI cannot either — sneak
     * only counts while no screen is open, which is what the container check is.
     */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.level().dimension().equals(WorkbayDimensions.BACKSHOP)
            || !isVisiting(player)) {
            return;
        }
        boolean sneaking = player.isShiftKeyDown()
            && player.containerMenu == player.inventoryMenu;
        if (!sneaking) {
            HOLDING.remove(player.getUUID());
            return;
        }
        int held = HOLDING.merge(player.getUUID(), 1, Integer::sum);
        if (held == 1) {
            player.displayClientMessage(WorkbayLang.message("bay_leaving"), true);
        }
        if (held >= HOLD_TICKS) {
            leave(player);
        }
    }

    /**
     * Nobody logs back in inside a bay.
     *
     * <p>A bay is a sealed 3x3x3 box that only exists while its Workbay does. Someone who quits
     * inside one and comes back to find the Workbay broken, the world converted, or the mod's
     * dimension missing would be entombed with no way out and no block to break — so the return
     * happens on the way in, before they can discover any of that.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            if (!leave(player)) {
                // No recorded way home: they were put here by something else, or the attachment
                // was lost. The spawn point is the only answer that is certainly not solid rock.
                ServerLevel overworld = player.server.overworld();
                BlockPos spawn = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    Set.of(), 0.0F, 0.0F);
            }
        }
    }
}
