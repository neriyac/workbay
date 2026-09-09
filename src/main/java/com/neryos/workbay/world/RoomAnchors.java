package com.neryos.workbay.world;

import com.neryos.workbay.config.WorkbayConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * What a room costs to keep loaded. SPEC.md §12.
 *
 * <p><b>An occupied room costs nothing.</b> A player standing in one loads its chunks by being
 * there, so the only room that needs a ticket is an empty one somebody wants to keep running — and
 * that is a decision taken per room, beside the number of chunks it holds, rather than a switch the
 * Anchor upgrade flips on for everything at once.
 *
 * <p>The count is the price and the page prints it: 1, 4 or 9 chunks by tier.
 *
 * <p><b>And every ticket here is conditional on {@link AnchorPresence}</b>, which is §12's last
 * line: anchored says what the network is entitled to, online says whether it holds it now.
 */
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
     *
     * <p>"Nobody around" is not "nobody online". OPEN_ISSUES #55.
     */
    public static boolean anchorsOwnChunk(MinecraftServer server, WorkbayRecord record) {
        return WorkbayConfig.SERVER.allowAnchors.get()
            && WorkbayConfig.SERVER.maxAnchoredWorkbaysPerPlayer.get() > 0
            && record.upgrades().anchors() > 0
            && AnchorPresence.holding(server, record.owner());
    }

    /** Registers or drops the tickets for one room, keyed by its own stable UUID (SPEC.md §12). */
    public static void apply(ServerLevel backshop, RoomRecord room) {
        if (!room.built()) {
            return;
        }
        MinecraftServer server = backshop.getServer();
        // An orphaned room is listed by nobody, so it has no owner who could be online — and
        // `orElse(false)` releasing it is the right answer rather than a fallback.
        boolean hold = room.anchored() && RoomRegistry.get(server).ownerOf(room)
            .map(owner -> AnchorPresence.holding(server, owner)).orElse(false);
        for (ChunkPos chunk : RoomGeometry.chunks(room.region(), room.builtTier())) {
            if (hold) {
                WorkbayTickets.force(backshop, room.id(), chunk);
            } else {
                WorkbayTickets.release(backshop, room.id(), chunk);
            }
        }
    }

    /**
     * The other ticket a network owns: the chunk its Workbay block stands in.
     *
     * <p>Registered from here rather than only from the block's own tick because the block cannot
     * arm itself. Once the ticket is gone the chunk unloads, the block stops ticking, and nothing
     * in the world is left that could notice the owner logged back in — the same reason
     * {@code onServerStarted} used to re-register it after a restart, except that a restart is now
     * covered by the same login that covers a logout, and one path is fewer than two.
     */
    static void applyOwnChunk(MinecraftServer server, WorkbayRecord record) {
        record.lastKnownPos().ifPresent(where -> {
            ServerLevel level = server.getLevel(where.dimension());
            if (level == null) {
                return;
            }
            UUID owner = WorkbayTickets.owner(where.dimension(), where.pos());
            ChunkPos chunk = new ChunkPos(where.pos());
            if (!anchorsOwnChunk(server, record)) {
                WorkbayTickets.release(level, owner, chunk);
                return;
            }
            WorkbayTickets.force(level, owner, chunk);
            // `lastKnownPos` is where the record last saw its block, and nothing clears it when
            // somebody breaks one -- so without this a Workbay that no longer exists holds a chunk
            // from every login until the owner leaves again. Forcing sync-loads the chunk, so by
            // here the block is readable and the answer is not a guess.
            if (!(level.getBlockEntity(where.pos())
                instanceof com.neryos.workbay.content.workbay.WorkbayBlockEntity)) {
                WorkbayTickets.release(level, owner, chunk);
            }
        });
    }
}
