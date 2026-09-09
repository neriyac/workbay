package com.neryos.workbay.world;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.config.WorkbayConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * What a room costs to keep loaded. SPEC.md §12.
 *
 * <p><b>An occupied room costs nothing.</b> A player standing in one loads its chunks by being
 * there, so the only room that needs a ticket is an empty one somebody wants to keep running — and
 * that is a decision taken per room, beside the number of chunks it holds, rather than a switch the
 * Anchor upgrade flips on for everything at once.
 *
 * <p>The count is the price and the page prints it: 1, 4 or 9 chunks by tier.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public final class RoomAnchors {
    private RoomAnchors() {}

    /** True when this network may switch on one more room. Reads the knob where it is used. */
    public static boolean canAnchorAnother(RoomRegistry registry, WorkbayRecord record,
        RoomRecord except) {
        if (!WorkbayConfig.SERVER.allowAnchors.get() || record.upgrades().anchors() <= 0) {
            return false;
        }
        long already = registry.roomsOf(record).stream()
            .filter(room -> room.anchored() && !room.id().equals(except.id()))
            .count();
        return already < WorkbayConfig.SERVER.maxAnchoredRoomsPerNetwork.get();
    }

    /**
     * Whether this network's Workbay holds <b>its own</b> chunk loaded. SPEC.md §12's first Anchor
     * row — "the bay column stays loaded with nobody around" — which needs the block itself to keep
     * ticking, because the block is what runs the links and mirrors the column.
     *
     * <p>{@code maxAnchoredWorkbaysPerPlayer} is the switch, and this is its first reader. A host
     * who wants the Anchor to run rooms and nothing else sets it to zero. OPEN_ISSUES #52.
     */
    public static boolean anchorsOwnChunk(WorkbayRecord record) {
        return WorkbayConfig.SERVER.allowAnchors.get()
            && WorkbayConfig.SERVER.maxAnchoredWorkbaysPerPlayer.get() > 0
            && record.upgrades().anchors() > 0;
    }

    /** Registers or drops the tickets for one room, keyed by its own stable UUID (SPEC.md §12). */
    public static void apply(ServerLevel backshop, RoomRecord room) {
        if (!room.built()) {
            return;
        }
        for (ChunkPos chunk : RoomGeometry.chunks(room.region(), room.builtTier())) {
            if (room.anchored()) {
                WorkbayTickets.force(backshop, room.id(), chunk);
            } else {
                WorkbayTickets.release(backshop, room.id(), chunk);
            }
        }
    }

    /**
     * Re-registers every anchored room after a restart.
     *
     * <p>{@code WorkbayTickets#validate} purges every ticket on world load, which is FTB-Chunks'
     * answer and the right one — a ticket nobody can account for leaks forever. The other half of
     * that answer is putting back the ones the registry does vouch for, and it has to happen
     * <em>after</em> the server is up, because the callback runs while the registry may not be
     * loaded yet.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel backshop = server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null) {
            return;
        }
        RoomRegistry registry = RoomRegistry.get(server);
        for (WorkbayRecord record : registry.all()) {
            for (RoomRecord room : registry.roomsOf(record)) {
                if (room.anchored()) {
                    apply(backshop, room);
                }
            }
            // And the Workbay's own chunk. Without this an anchored network comes back from a
            // restart with nothing ticking until somebody walks to the block, which is the same
            // silence OPEN_ISSUES #52 is about with a longer fuse. `lastKnownPos` is where the
            // record says its block is; one deployed Workbay per network is the default, and a
            // second one re-forces its own chunk on its first tick.
            if (!anchorsOwnChunk(record)) {
                continue;
            }
            record.lastKnownPos().ifPresent(where -> {
                ServerLevel level = server.getLevel(where.dimension());
                if (level != null) {
                    WorkbayTickets.force(level,
                        WorkbayTickets.owner(where.dimension(), where.pos()),
                        new net.minecraft.world.level.ChunkPos(where.pos()));
                }
            });
        }
    }
}
