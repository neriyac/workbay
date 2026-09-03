package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.world.BayVisit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Data attached to something the mod does not own. One holder, the same shape as the rest of {@code init}. */
public final class WBAttachments {
    private WBAttachments() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Workbay.MOD_ID);

    /**
     * Where a bay visitor came from. On the player, not in the network record: it is the
     * <em>player</em> who has to be put back, one player can only be in one bay, and a record that
     * outlived its Workbay would strand them. Serialized, so a server restart with somebody inside
     * still knows the way home.
     */
    public static final Supplier<AttachmentType<BayVisit.Return>> BAY_RETURN =
        ATTACHMENTS.register("bay_return", () -> AttachmentType
            .<BayVisit.Return>builder(() -> null)
            .serialize(BayVisit.Return.CODEC)
            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
