package com.neryos.workbay.network;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.menu.BayViewMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Bay View's tanks and energy, server to client. SPEC.md §5.
 *
 * <p>Item slots ride the ordinary container sync; nothing else in a menu does, and vanilla's only
 * other channel is a sixteen-bit data slot, which a tank of 10,000 mB or a cube of 1,600,000 FE
 * does not fit in. Sent only when it differs from what was last sent.
 */
public record BayViewPacket(int containerId, BayViewMenu.State state) implements CustomPacketPayload {

    public static final Type<BayViewPacket> TYPE = new Type<>(Workbay.rl("bay_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BayViewPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BayViewPacket::containerId,
            BayViewMenu.State.STREAM_CODEC, BayViewPacket::state,
            BayViewPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The container-id guard is the same one {@link SnapshotPacket} carries, for the same reason. */
    public static void handle(BayViewPacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof BayViewMenu menu
            && menu.containerId == packet.containerId()) {
            menu.applyState(packet.state());
        }
    }
}
