package com.neryos.workbay.world;

import com.neryos.workbay.Workbay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.List;
import java.util.UUID;

/**
 * Force-loading for the Backshop. SPEC §12.
 *
 * <p>Tickets are keyed by a stable UUID, never a {@code BlockPos} — the BlockPos overload is
 * unexercised in every repo surveyed, and the owner has to survive the Workbay being broken and
 * re-placed anyway. {@code ticking} is always {@code true}: the cheaper non-ticking tier is
 * contradicted by real evidence (OPEN_ISSUES #11) and there is no saving worth the risk.
 */
public final class WorkbayTickets {
    private WorkbayTickets() {}

    public static final TicketController TICKETS =
        new TicketController(Workbay.rl("backshop"), WorkbayTickets::validate);

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterTicketControllersEvent event) -> event.register(TICKETS));
    }

    /**
     * Runs on world load. It <em>must</em> exist or forced chunks leak forever. FTB-Chunks' answer:
     * purge everything, then re-register from the authoritative registry — never try to match
     * owners here, where the registry may not be loaded yet.
     *
     * <p>Re-registration lands with the Anchor. Until then this is purge-only, which is the safe
     * direction: a ticket that outlives its owner is a permanent leak, an Anchor that needs
     * re-arming after a restart is a missing feature.
     */
    private static void validate(ServerLevel level, TicketHelper helper) {
        List.copyOf(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets);
        List.copyOf(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets);
    }

    /**
     * Who a ticket belongs to when the owner is a <em>block</em> rather than a room.
     *
     * <p>Mirroring used to register under the network's id, which is one owner for every Workbay
     * standing on one record: releasing the first block's tickets released the chunks the second
     * still believed it held. A position in a dimension already is a stable name for a block, so
     * there is nothing to store and nothing to migrate. OPEN_ISSUES #53.
     */
    public static UUID owner(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
        net.minecraft.core.BlockPos pos) {
        return UUID.nameUUIDFromBytes((dimension.location() + "@" + pos.asLong())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Loads the chunk and registers a ticking ticket for it, in that order — the sync load first
     * so the caller can touch the chunk on the same tick, then the ticket so it stays.
     */
    public static void force(ServerLevel level, UUID owner, ChunkPos chunk) {
        level.getChunk(chunk.x, chunk.z);
        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, true, true);
        level.getChunkSource().save(false);
    }

    public static void release(ServerLevel level, UUID owner, ChunkPos chunk) {
        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, false, true);
    }
}
