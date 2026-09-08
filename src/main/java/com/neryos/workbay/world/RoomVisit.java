package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.init.WBAttachments;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Standing in a room. SPEC.md §8.
 *
 * <p>Deliberately not {@link BayVisit}. A bay visit is a screen with a trip hidden inside it and
 * ends the moment the screen closes; a room is a place, and its occupant is meant to stand there
 * with nothing open, log out in it, and log back in where they left off. The two rules are
 * opposites, so they are two classes rather than one with a flag in it.
 *
 * <p>What the two share is the door: {@link BayVisit#admit} is the only route into the Backshop,
 * and rooms go through it rather than opening a second one.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public final class RoomVisit {
    private RoomVisit() {}

    /**
     * Where an occupant came from, and the room they are in.
     *
     * <p>Persisted, and that is the point: SPEC.md §14 says a player who disconnects inside a room
     * keeps their return position, unlike a bay visitor, whose visit is simply over.
     */
    public record Inside(UUID room, ResourceKey<Level> dimension, Vec3 where, float yRot, float xRot) {
        public static final Codec<Inside> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("room").forGetter(Inside::room),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Inside::dimension),
            Vec3.CODEC.fieldOf("where").forGetter(Inside::where),
            Codec.FLOAT.fieldOf("y_rot").forGetter(Inside::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(Inside::xRot)
        ).apply(i, Inside::new));
    }

    /**
     * {@code hasData}, never {@code getData}: asking for an absent attachment materialises its
     * default, which here is null, and NeoForge then fails to serialize it at the next player save.
     */
    public static boolean isInside(ServerPlayer player) {
        return player.hasData(WBAttachments.ROOM_RETURN.get());
    }

    /** Which room they are in, as far as the attachment knows. Empty when they are in none. */
    public static java.util.Optional<UUID> roomOf(ServerPlayer player) {
        return isInside(player)
            ? java.util.Optional.of(player.getData(WBAttachments.ROOM_RETURN.get()).room())
            : java.util.Optional.empty();
    }

    // ------------------------------------------------------------- permission

    /**
     * Who may be in a room. SPEC.md 8.
     *
     * <p><b>The owner, and whoever the owner invited to that room.</b> Nothing else, including an
     * operator: an op who wants in can invite themselves from the screen, and a silent exception
     * here would be a rule that is true until it is not.
     *
     * <p>An orphaned room -- one no Workbay record lists any more -- has no owner and therefore
     * admits nobody, which is the right answer: there is no screen anywhere that could invite you
     * to it.
     */
    public static boolean mayEnter(RoomRegistry registry, UUID player, RoomRecord room) {
        return registry.ownerOf(room).map(owner -> owner.equals(player)).orElse(false)
            || room.guestLevel(player).isPresent();
    }

    /** May change what is in here: the owner, or a guest invited at {@link RoomGuest#BUILD}. */
    public static boolean mayBuild(RoomRegistry registry, UUID player, RoomRecord room) {
        return registry.ownerOf(room).map(owner -> owner.equals(player)).orElse(false)
            || room.guestLevel(player).filter(level -> level == RoomGuest.BUILD).isPresent();
    }

    /** True when this player may still be standing where they are. Used on login and every tick. */
    public static boolean mayStay(ServerPlayer player) {
        if (!isInside(player)) {
            return false;
        }
        RoomRegistry registry = RoomRegistry.get(player.server);
        RoomRecord room = registry
            .room(player.getData(WBAttachments.ROOM_RETURN.get()).room()).orElse(null);
        return room != null
            && RoomGeometry.inside(player.blockPosition(), room.region(), room.builtTier())
            && mayEnter(registry, player.getUUID(), room);
    }

    /**
     * Sends a player into one of their network's rooms, building or growing it first.
     *
     * @return false when there is no room to enter — no dimension, no such room slot, or no Room
     *         Frame installed
     */
    public static boolean enter(ServerPlayer player, WorkbayRecord record, int index) {
        ServerLevel backshop = player.server.getLevel(WorkbayDimensions.BACKSHOP);
        if (backshop == null || index < 0 || index >= record.roomCapacity()) {
            return false;
        }
        RoomRegistry registry = RoomRegistry.get(player.server);
        RoomRecord room = roomSlot(registry, record, index);
        if (room == null) {
            return false;
        }
        // A Workbay is a block anybody may right-click, and until this line ENTER_ROOM was the one
        // action on the screen with no owner check on it at all -- not even the lock. Standing at
        // somebody's Workbay was standing in every room they own.
        if (!mayEnter(registry, player.getUUID(), room)) {
            player.displayClientMessage(
                com.neryos.workbay.WorkbayLang.message("room_not_yours"), true);
            return false;
        }
        // Every entry re-checks the size, because a Frame installed while nobody was in here has
        // to grow the shell before anybody stands in it.
        RoomRecord grown = RoomBuilder.ensure(backshop, room, record.upgrades().roomTier());
        if (!grown.built()) {
            return false;
        }
        if (grown != room) {
            registry.putRoom(grown);
        }

        player.setData(WBAttachments.ROOM_RETURN.get(), new Inside(grown.id(),
            player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        Vec3 spot = RoomGeometry.entrySpot(grown.region());
        BayVisit.admit(() -> player.teleportTo(backshop, spot.x, spot.y, spot.z, Set.of(),
            RoomGeometry.ENTRY_YAW, 0.0F));
        if (!player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            player.removeData(WBAttachments.ROOM_RETURN.get());
            return false;
        }
        return true;
    }

    /**
     * The room in slot {@code index}, minting rooms up to it as needed.
     *
     * <p>Regions are allocated here rather than at install (SPEC.md §8), so a Room Frame that is
     * fitted and never used spends nothing. Slots below the one asked for are minted too, because
     * a room's slot is its position in the list and skipping one would make the fourth room the
     * second next time it is opened.
     */
    private static RoomRecord roomSlot(RoomRegistry registry, WorkbayRecord record, int index) {
        java.util.List<UUID> ids = new java.util.ArrayList<>(record.rooms());
        boolean changed = false;
        while (ids.size() <= index) {
            ids.add(registry.createRoom().id());
            changed = true;
        }
        if (changed) {
            registry.put(record.withRooms(ids));
        }
        return registry.room(ids.get(index)).orElse(null);
    }

    /**
     * Puts an occupant back where they came from. Silent and harmless if they are not one.
     *
     * <p>Reads the way home off the <b>player</b>, which is what lets any shell block work with no
     * Workbay standing in the world at all.
     */
    public static boolean leave(ServerPlayer player) {
        if (!isInside(player)) {
            return false;
        }
        Inside home = player.getData(WBAttachments.ROOM_RETURN.get());
        ServerLevel level = player.server.getLevel(home.dimension());
        if (level == null) {
            // Their own dimension is gone — a datapack change, most likely. The overworld is a
            // worse answer than the right one and a much better answer than leaving them sealed in.
            level = player.server.overworld();
        }
        player.removeData(WBAttachments.ROOM_RETURN.get());
        player.teleportTo(level, home.where().x, home.where().y, home.where().z, Set.of(),
            home.yRot(), home.xRot());
        return true;
    }

    /**
     * A player who logs in <b>already inside</b> a room has their room brought up to date, because
     * nothing else will: {@link RoomBuilder#ensure} runs on entry, and this player is not entering.
     *
     * <p>It is not a nicety. A room built by an older version of the mod has an older version's
     * shell, and the way out is a property of the shell — somebody who logged out in a room and
     * came back after an update would be standing in a box whose walls do not open.
     */
    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isInside(player)) {
            return;
        }
        ServerLevel backshop = player.server.getLevel(WorkbayDimensions.BACKSHOP);
        RoomRegistry registry = RoomRegistry.get(player.server);
        RoomRecord room = registry.room(player.getData(WBAttachments.ROOM_RETURN.get()).room())
            .orElse(null);
        if (backshop == null || room == null) {
            return;
        }
        // Removed from the room while they were offline: they must not wake up in it. Checked
        // before the shell is repaired, because repairing a room for somebody who is about to be
        // put out of it is work done for nobody.
        if (!mayEnter(registry, player.getUUID(), room)) {
            leave(player);
            return;
        }
        RoomRecord fixed = RoomBuilder.ensure(backshop, room, room.builtTier());
        if (fixed != room) {
            registry.putRoom(fixed);
        }
    }

    // ----------------------------------------------------------- block guards

    /**
     * What a {@link RoomGuest#LOOK} guest may not do: break a block, place one, or right-click one.
     *
     * <p>The third is on the list on purpose. Opening a chest does not change a block and does
     * change what is in it, which is the whole of what a room in a chain holds -- a level that let
     * a guest empty every barrel in the room would be "look only" in name.
     *
     * <p>Scoped to a room's own interior. Everything else in the Backshop is void with a bedrock
     * floor that {@link BayVisit} already refuses to let anybody stand on.
     */
    private static boolean refused(net.minecraft.world.entity.Entity who,
        net.minecraft.core.BlockPos where) {
        if (!(who instanceof ServerPlayer player)
            || !player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            return false;
        }
        RoomRegistry registry = RoomRegistry.get(player.server);
        RoomRecord room = registry.roomAt(where).orElse(null);
        if (room == null || mayBuild(registry, player.getUUID(), room)) {
            return false;
        }
        player.displayClientMessage(com.neryos.workbay.WorkbayLang.message("room_look_only"), true);
        return true;
    }

    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (refused(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(
        net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (refused(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClick(
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (refused(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Keeps an occupant honest, and nothing else: a room has no screen to watch and no timeout.
     *
     * <p>The one thing checked is that they are still inside the room they were sent to. Outside
     * it, the Backshop is void with a bedrock floor and nothing to do, so whatever moved them —
     * a teleport, another mod, a command — puts them back where they came from instead.
     */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isInside(player)) {
            return;
        }
        if (!player.level().dimension().equals(WorkbayDimensions.BACKSHOP)) {
            // Something else took them out. The record must not outlive the visit, or it is a
            // return address for a room they are no longer in.
            player.removeData(WBAttachments.ROOM_RETURN.get());
            return;
        }
        // Inside the bounds of the room they were sent to, and still allowed in it. Regions are
        // 512 blocks apart, which is nothing to somebody who has just launched himself upward with
        // an item from another mod -- and being un-invited while standing in a room has to take
        // effect where the player is, not the next time they ask to come in.
        if (!mayStay(player)) {
            leave(player);
        }
    }
}
