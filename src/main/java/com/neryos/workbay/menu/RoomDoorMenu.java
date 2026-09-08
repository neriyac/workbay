package com.neryos.workbay.menu;

import com.neryos.workbay.init.WBMenus;
import com.neryos.workbay.world.RoomGeometry;
import com.neryos.workbay.world.RoomRecord;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.RoomVisit;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The screen a room's wall opens: the way out, and the way to the network's other rooms.
 * SPEC.md §8.
 *
 * <p><b>Its own menu rather than the Workbay's.</b> {@link WorkbayMenu} is built from a Workbay
 * <em>block entity</em>, and the whole point of a room is that it works with no Workbay standing in
 * the world at all — which is the failure Compact Machines is known for. This one is built from the
 * player and the block they clicked, so it opens for somebody who has lost everything.
 *
 * <p>No slots, like the Workbay's. Everything it draws rides the menu-open buffer.
 */
public class RoomDoorMenu extends AbstractContainerMenu {

    /** Every room slot the network owns, and which of them the player is standing in. */
    public record View(List<WorkbaySnapshot.Room> rooms, int current) {

        public static final View EMPTY = new View(List.of(), -1);

        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.fromCodecWithRegistries(WorkbaySnapshot.Room.CODEC.listOf()),
                View::rooms,
                ByteBufCodecs.VAR_INT, View::current,
                View::new);
    }

    private final View view;

    /** Client side: the whole view arrives in the open buffer, so frame 1 is already correct. */
    public RoomDoorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, View.STREAM_CODEC.decode(buffer));
    }

    public RoomDoorMenu(int containerId, View view) {
        super(WBMenus.ROOM_DOOR.get(), containerId);
        this.view = view;
    }

    public View view() {
        return view;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * <b>Always valid.</b> A distance check would be a way to strand somebody: the player is
     * standing inside the thing they clicked, and the only two buttons take them somewhere else.
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Leave, or step to another room. Nothing else, so there is nothing else to guard. */
    public void act(WorkbayAction action, int arg, ServerPlayer player) {
        switch (action) {
            case LEAVE_ROOM -> {
                player.closeContainer();
                RoomVisit.leave(player);
            }
            case ENTER_ROOM -> {
                WorkbayRecord record = networkOf(player).orElse(null);
                if (record == null) {
                    return;
                }
                player.closeContainer();
                // Leaving first, so the return address is the overworld the player came from and
                // never the room next door: two rooms deep would otherwise send them to a room.
                RoomVisit.leave(player);
                RoomVisit.enter(player, record, arg);
            }
            default -> { }
        }
    }

    /**
     * Opens the screen for whoever clicked a shell block.
     *
     * <p><b>The room is read off the player, not off the block.</b> The obvious version asked the
     * registry which room contains the clicked position and got nothing for the floor and the
     * walls — {@code roomAt} answers for a room's <em>interior</em>, and the shell is by definition
     * not inside it. The player standing in the room is the fact that matters anyway: they cannot
     * reach another room's wall.
     */
    public static void open(ServerPlayer player, BlockPos clicked) {
        RoomRegistry registry = RoomRegistry.get(player.server);
        RoomRecord here = RoomVisit.roomOf(player).flatMap(registry::room).orElse(null);
        WorkbayRecord record = networkOf(player).orElse(null);
        if (record == null || here == null || !record.rooms().contains(here.id())) {
            return;
        }
        View view = view(player, record, here);
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, who) -> new RoomDoorMenu(id, view),
            com.neryos.workbay.WorkbayLang.gui("door.title")),
            buffer -> View.STREAM_CODEC.encode(buffer, view));
    }

    /**
     * The network whose room the player is in. Read from the room they are recorded as occupying
     * rather than from the block, because that is the record the standing rule already trusts.
     */
    private static Optional<WorkbayRecord> networkOf(ServerPlayer player) {
        RoomRegistry registry = RoomRegistry.get(player.server);
        Optional<UUID> room = RoomVisit.roomOf(player);
        if (room.isEmpty()) {
            return Optional.empty();
        }
        return registry.ownedBy(player.getUUID()).stream()
            .filter(record -> record.rooms().contains(room.get()))
            .findFirst();
    }

    private static View view(ServerPlayer player, WorkbayRecord record, RoomRecord here) {
        List<RoomRecord> known = RoomRegistry.get(player.server).roomsOf(record);
        List<WorkbaySnapshot.Room> rooms = new java.util.ArrayList<>();
        int current = -1;
        for (int index = 0; index < record.roomCapacity(); index++) {
            RoomRecord room = index < known.size() ? known.get(index) : null;
            if (room != null && room.id().equals(here.id())) {
                current = index;
            }
            rooms.add(new WorkbaySnapshot.Room(index, room == null ? "" : room.name().orElse(""),
                room == null ? 0 : RoomGeometry.interior(room.builtTier()),
                room == null ? 0 : room.chunkCost(),
                room != null && room.built(),
                room != null && room.anchored(),
                room == null ? "" : room.effectiveBiome().location().toString(),
                room == null ? com.neryos.workbay.content.room.RoomColour.DEFAULT : room.colour()));
        }
        return new View(rooms, current);
    }
}
