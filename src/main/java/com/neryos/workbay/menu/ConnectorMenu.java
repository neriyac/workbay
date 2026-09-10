package com.neryos.workbay.menu;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.content.connector.ConnectorBlockEntity;
import com.neryos.workbay.content.workbay.WorkbayBlockEntity;
import com.neryos.workbay.init.WBMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * What right-clicking a placed Connector opens. OPEN_ISSUES #77.
 *
 * <p><b>A rename panel and nothing else.</b> The gesture used to mint a link -- the next resource
 * this Connector did not carry, then the next bay of the network -- which made one block appear
 * four times in a list that is supposed to have one row per Connector. A Connector is one object
 * with one name and one home bay, and the only thing the world gesture is for is the name; every
 * other question about it is answered on the bay screen, where the bay is.
 *
 * <p><b>Its own menu type</b> rather than the Workbay's, for {@link RoomDoorMenu}'s reason:
 * {@link WorkbayMenu} is built from a Workbay block entity, and a player standing at a Connector
 * is nowhere near one -- often in another dimension. This is built from the player and the block
 * they clicked.
 *
 * <p>No slots. Everything it draws rides the menu-open buffer, so frame 1 is already correct.
 */
public class ConnectorMenu extends AbstractContainerMenu {

    /**
     * {@code name} is what the player gave it, empty for one that was never named; {@code fallback}
     * is what it is called then -- <b>its own coordinates</b>, resolved server-side because that is
     * where the block is. {@code bay} is the home bay, or -1 for a Connector on no bay at all.
     */
    public record View(BlockPos pos, String name, String fallback, int bay, boolean linked) {

        public static final View EMPTY = new View(BlockPos.ZERO, "", "", -1, false);

        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, View::pos,
                ByteBufCodecs.stringUtf8(64), View::name,
                ByteBufCodecs.stringUtf8(64), View::fallback,
                ByteBufCodecs.VAR_INT, View::bay,
                ByteBufCodecs.BOOL, View::linked,
                View::new);
    }

    private final View view;

    /** Client side: the whole view arrives in the open buffer. */
    public ConnectorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, View.STREAM_CODEC.decode(buffer));
    }

    public ConnectorMenu(int containerId, View view) {
        super(WBMenus.CONNECTOR.get(), containerId);
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
     * Valid while the player is within reach of the block they clicked. Unlike the room door there
     * is somewhere to walk away to, and a rename that lands after walking off is a rename of a
     * block the player is no longer looking at.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide
            || player.blockPosition().distSqr(view.pos()) <= 64;
    }

    /**
     * The one thing this screen does. Empty clears the name and the row goes back to the
     * coordinates, exactly the way an empty bay name goes back to the machine's own.
     */
    public void act(WorkbayAction action, String text, ServerPlayer player) {
        if (action != WorkbayAction.SET_CONNECTOR_NAME) {
            return;
        }
        link(player.serverLevel(), view.pos()).ifPresent(found ->
            found.workbay().addBus(found.link().withName(text.strip())));
        player.closeContainer();
    }

    /** A Connector's single link, and the Workbay holding it. #77: there is never more than one. */
    private record Found(WorkbayBlockEntity workbay, BusConfig link) {}

    private static Optional<Found> link(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector)) {
            return Optional.empty();
        }
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        return connector.workbay().flatMap(workbay -> workbay.linksAt(here).stream().findFirst()
            .map(bus -> new Found(workbay, bus)));
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        Optional<Found> found = link(player.serverLevel(), pos);
        View view = new View(pos,
            found.map(f -> f.link().name()).orElse(""),
            pos.getX() + " " + pos.getY() + " " + pos.getZ(),
            found.map(f -> f.link().bay()).orElse(-1),
            found.isPresent());
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, who) -> new ConnectorMenu(id, view),
            com.neryos.workbay.WorkbayLang.gui("connector.title")),
            buffer -> View.STREAM_CODEC.encode(buffer, view));
    }
}
