package com.neryos.workbay.content.connector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Which Workbay a Connector belongs to, and which bay its link lands on. SPEC.md §0 and §9.
 *
 * <p>Carried on the item so a Connector is paired <em>before</em> it is placed — the block already
 * knows its Workbay when {@code setPlacedBy} runs, and there is no second step where a freshly
 * placed Connector sits in the world doing nothing.
 *
 * <p>{@code workbayPos} is here as well as {@code workbayId} so the block can reach the Workbay's
 * block entity without walking the registry every time; the id is what it verifies against, because
 * a Workbay can be broken and re-placed somewhere else.
 *
 * <p>{@code code} is for the tooltip. A tooltip renders on the client, which has no registry to ask.
 */
public record ConnectorPairing(UUID workbayId, GlobalPos workbayPos, String code, int bay) {

    public static final Codec<ConnectorPairing> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("WorkbayId").forGetter(ConnectorPairing::workbayId),
        GlobalPos.CODEC.fieldOf("WorkbayPos").forGetter(ConnectorPairing::workbayPos),
        Codec.STRING.fieldOf("Code").forGetter(ConnectorPairing::code),
        Codec.INT.optionalFieldOf("Bay", 0).forGetter(ConnectorPairing::bay)
    ).apply(i, ConnectorPairing::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPairing> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public ConnectorPairing withBay(int newBay) {
        return new ConnectorPairing(workbayId, workbayPos, code, newBay);
    }
}
