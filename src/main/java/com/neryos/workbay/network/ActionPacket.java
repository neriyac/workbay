package com.neryos.workbay.network;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.menu.WorkbayAction;
import com.neryos.workbay.menu.WorkbayMenu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/**
 * One button press, client to server. SPEC.md §4.
 *
 * <p>One packet for every action rather than one per button: each is an id and at most one number,
 * and a payload class per button would be a dozen places to forget the container-id guard.
 *
 * <p>{@code arg} is a long because pasting a bay's face config is one action carrying 36 bits of
 * it. A second packet type for that one button would be the same guard written twice.
 */
public record ActionPacket(int containerId, WorkbayAction action, long arg,
    Optional<UUID> link, Optional<String> text)
    implements CustomPacketPayload {

    public static final Type<ActionPacket> TYPE = new Type<>(Workbay.rl("action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, WorkbayAction> ACTION =
        ByteBufCodecs.VAR_INT.map(i -> WorkbayAction.values()[Math.clamp(i, 0,
            WorkbayAction.values().length - 1)], WorkbayAction::ordinal).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionPacket::containerId,
            ACTION, ActionPacket::action,
            ByteBufCodecs.VAR_LONG, ActionPacket::arg,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), ActionPacket::link,
            // Capped, because it is drawn on a 130-pixel line and arrives from a client.
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs::optional), ActionPacket::text,
            ActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ActionPacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof WorkbayMenu menu
            && menu.containerId == packet.containerId()) {
            menu.act(packet.action(), packet.arg(), packet.link(), packet.text());
        }
    }
}
