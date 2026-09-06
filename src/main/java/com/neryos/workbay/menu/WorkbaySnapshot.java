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
    /** Levy banked by this network's Assay, not items in anyone's pockets. SPEC.md §3. */
    int levy,
    /** The skim, as a whole percent. Drawn at rest on the bays screen and on every item row. */
    int skimRate,
    /**
     * Tagged items the skim has taken but not yet turned into Levy, out of
     * {@code AssayBlock.itemsPerLevy()}. On the screen because a banked total that only moves once
     * every 200 ticks cannot answer "am I earning right now" - this number can, and it is the only
     * part of the economy that visibly moves while the player watches.
     */
    int skimmed,
    /**
     * How many Workbay blocks of this network stand in the world, and how many the server allows.
     * On the screen because the cap is invisible otherwise: a player finds out by crafting a second
     * Workbay, carrying it somewhere and having the placement refused. SPEC.md §14.
     */
    int deployed,
    int maxDeployed,
    /**
     * Whether <b>this server</b> will open a hosted machine's own screen where the player stands
     * (SPEC.md §0). On the snapshot rather than read from the client's own config file because the
     * two installations can disagree, and the button has to name the trip the player is actually
     * about to get: a button reading "open its screen" that teleports you into a bay is the mod
     * lying about what it just did.
     */
    boolean remoteScreens) {

    public static final WorkbaySnapshot EMPTY = new WorkbaySnapshot("", false, 1, 0, 0, 1,
        List.of(), List.of(), WorkbayRecord.Upgrades.NONE, 0, 0, 0, 1, 1, false);

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
        Codec.INT.fieldOf("Levy").forGetter(WorkbaySnapshot::levy),
        Codec.INT.fieldOf("SkimRate").forGetter(WorkbaySnapshot::skimRate),
        Codec.INT.fieldOf("Skimmed").forGetter(WorkbaySnapshot::skimmed),
        Codec.INT.fieldOf("Deployed").forGetter(WorkbaySnapshot::deployed),
        Codec.INT.fieldOf("MaxDeployed").forGetter(WorkbaySnapshot::maxDeployed),
        Codec.BOOL.optionalFieldOf("RemoteScreens", false).forGetter(WorkbaySnapshot::remoteScreens)
    ).apply(i, WorkbaySnapshot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbaySnapshot> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** How many links are in a state the player has to do something about. The header's red count. */
    public int problems() {
        return (int) links.stream().filter(link -> link.status().isProblem()).count();
    }

    public Bay bay(int index) {
        return bays.stream().filter(b -> b.index() == index).findFirst()
            .orElseGet(() -> new Bay(index, Optional.empty(), 0, 0, State.EMPTY, FaceConfig.NONE,
                "", com.neryos.workbay.world.RedstoneMode.ALWAYS));
    }

    /** What a bay's status pip says, and what colour the machine block's status line draws. */
    public enum State { EMPTY, RUNNING, IDLE, INERT, LOCKED }

    public record Bay(int index, Optional<ResourceLocation> hosted, int energy, int energyCapacity,
        State state, FaceConfig faces, String name,
        com.neryos.workbay.world.RedstoneMode redstone) {

        public static final Codec<Bay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Index").forGetter(Bay::index),
            ResourceLocation.CODEC.optionalFieldOf("Hosted").forGetter(Bay::hosted),
            Codec.INT.fieldOf("Energy").forGetter(Bay::energy),
            Codec.INT.fieldOf("EnergyCapacity").forGetter(Bay::energyCapacity),
            WBCodecs.ofEnum(State.class).fieldOf("State").forGetter(Bay::state),
            FaceConfig.CODEC.fieldOf("Faces").forGetter(Bay::faces),
            Codec.STRING.fieldOf("Name").forGetter(Bay::name),
            WBCodecs.ofEnum(com.neryos.workbay.world.RedstoneMode.class).fieldOf("Redstone")
                .forGetter(Bay::redstone)
        ).apply(i, Bay::new));
    }

    /**
     * One LINKS row. Carries the whole {@link BusConfig} rather than a summary because the row's
     * gear opens the same fields and a second, thinner copy would drift from the first.
     */
    public record Link(BusConfig config, BusRunner.BusStatus status,
        Optional<ResourceLocation> targetBlock, Optional<Integer> targetBay) {

        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
            BusConfig.CODEC.fieldOf("Config").forGetter(Link::config),
            WBCodecs.ofEnum(BusRunner.BusStatus.class).fieldOf("Status").forGetter(Link::status),
            ResourceLocation.CODEC.optionalFieldOf("TargetBlock").forGetter(Link::targetBlock),
            // Only ever present for an internal (bay-to-bay) link, computed server-side because the
            // client has no way to invert a Backshop position back into a bay index.
            Codec.INT.optionalFieldOf("TargetBay").forGetter(Link::targetBay)
        ).apply(i, Link::new));

        /**
         * What the row calls this link: the name the player gave it, or one derived from what it
         * points at. Empty means "the target block's own name", which only the client can resolve
         * because that is where the language file lives.
         *
         * <p>Derived on every draw rather than stored at creation. A stored default was literally
         * the word "Bay link" on every internal row, and baking the target into it instead would go
         * stale the first time somebody retargeted the link.
         */
        public Optional<String> label() {
            if (!config.name().isBlank()) {
                return Optional.of(config.name());
            }
            return config.internal() ? targetBay().map(bay -> "Bay " + (bay + 1)) : Optional.empty();
        }
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
