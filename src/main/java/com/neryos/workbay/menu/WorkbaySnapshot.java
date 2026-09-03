package com.neryos.workbay.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.world.FaceConfig;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Everything all three screens draw, in one immutable record. SPEC.md §4.
 *
 * <p>One snapshot, polled server-side with {@code !Objects.equals} and resent only when it differs.
 * <b>Not indexed sync slots</b> — EnderIO's own {@code SyncSlot} indices are positional, the two
 * directions use different index spaces, and its failure path is a bare {@code // TODO: Log this
 * error}. A whole record that either matches or does not cannot drift.
 *
 * <p>Nothing here is a {@code Component}. Names are resolved from the block id on the client, which
 * is where the language file lives, so a snapshot is the same size whatever language is loaded.
 */
public record WorkbaySnapshot(
    String code,
    boolean locked,
    int bayCapacity,
    int selectedBay,
    int energy,
    int energyCapacity,
    List<Bay> bays,
    List<Link> links,
    WorkbayRecord.Upgrades upgrades,
    int levyInInventory) {

    public static final WorkbaySnapshot EMPTY = new WorkbaySnapshot("", false, 1, 0, 0, 1,
        List.of(), List.of(), WorkbayRecord.Upgrades.NONE, 0);

    public static final Codec<WorkbaySnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("Code").forGetter(WorkbaySnapshot::code),
        Codec.BOOL.fieldOf("Locked").forGetter(WorkbaySnapshot::locked),
        Codec.INT.fieldOf("BayCapacity").forGetter(WorkbaySnapshot::bayCapacity),
        Codec.INT.fieldOf("SelectedBay").forGetter(WorkbaySnapshot::selectedBay),
        Codec.INT.fieldOf("Energy").forGetter(WorkbaySnapshot::energy),
        Codec.INT.fieldOf("EnergyCapacity").forGetter(WorkbaySnapshot::energyCapacity),
        Bay.CODEC.listOf().fieldOf("Bays").forGetter(WorkbaySnapshot::bays),
        Link.CODEC.listOf().fieldOf("Links").forGetter(WorkbaySnapshot::links),
        WorkbayRecord.Upgrades.CODEC.fieldOf("Upgrades").forGetter(WorkbaySnapshot::upgrades),
        Codec.INT.fieldOf("Levy").forGetter(WorkbaySnapshot::levyInInventory)
    ).apply(i, WorkbaySnapshot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbaySnapshot> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** How many links are in a state the player has to do something about. The header's red count. */
    public int problems() {
        return (int) links.stream().filter(link -> link.status().isProblem()).count();
    }

    public Bay bay(int index) {
        return bays.stream().filter(b -> b.index() == index).findFirst()
            .orElseGet(() -> new Bay(index, Optional.empty(), 0, 0, State.EMPTY, FaceConfig.NONE));
    }

    /** What a bay's status pip says, and what colour the machine block's status line draws. */
    public enum State { EMPTY, RUNNING, IDLE, INERT, LOCKED }

    public record Bay(int index, Optional<ResourceLocation> hosted, int energy, int energyCapacity,
        State state, FaceConfig faces) {

        public static final Codec<Bay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Index").forGetter(Bay::index),
            ResourceLocation.CODEC.optionalFieldOf("Hosted").forGetter(Bay::hosted),
            Codec.INT.fieldOf("Energy").forGetter(Bay::energy),
            Codec.INT.fieldOf("EnergyCapacity").forGetter(Bay::energyCapacity),
            WBCodecs.ofEnum(State.class).fieldOf("State").forGetter(Bay::state),
            FaceConfig.CODEC.fieldOf("Faces").forGetter(Bay::faces)
        ).apply(i, Bay::new));
    }

    /**
     * One LINKS row. Carries the whole {@link BusConfig} rather than a summary because the row's
     * gear opens the same fields and a second, thinner copy would drift from the first.
     */
    public record Link(BusConfig config, BusRunner.BusStatus status,
        Optional<ResourceLocation> targetBlock) {

        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
            BusConfig.CODEC.fieldOf("Config").forGetter(Link::config),
            WBCodecs.ofEnum(BusRunner.BusStatus.class).fieldOf("Status").forGetter(Link::status),
            ResourceLocation.CODEC.optionalFieldOf("TargetBlock").forGetter(Link::targetBlock)
        ).apply(i, Link::new));
    }

    /** One helper rather than a StringRepresentable on every enum that only ever rides a packet. */
    static final class WBCodecs {
        private WBCodecs() {}

        static <E extends Enum<E>> Codec<E> ofEnum(Class<E> type) {
            E[] values = type.getEnumConstants();
            return Codec.intRange(0, values.length - 1).xmap(i -> values[i], Enum::ordinal);
        }
    }
}
