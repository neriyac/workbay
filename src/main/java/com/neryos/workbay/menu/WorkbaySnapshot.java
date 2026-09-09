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
    boolean remoteScreens,
    /**
     * This network's rooms, in slot order, one entry per slot the upgrades entitle it to —
     * including the ones nobody has opened yet, which is what {@code built} is false for. On the
     * snapshot because the page has to price a room before it exists.
     */
    List<Room> rooms) {

    public static final WorkbaySnapshot EMPTY = new WorkbaySnapshot("", false, 1, 0, 0, 1,
        List.of(), List.of(), WorkbayRecord.Upgrades.NONE, 1, 1, false, List.of());

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
        Codec.INT.fieldOf("Deployed").forGetter(WorkbaySnapshot::deployed),
        Codec.INT.fieldOf("MaxDeployed").forGetter(WorkbaySnapshot::maxDeployed),
        Codec.BOOL.optionalFieldOf("RemoteScreens", false).forGetter(WorkbaySnapshot::remoteScreens),
        Room.CODEC.listOf().optionalFieldOf("Rooms", List.of()).forGetter(WorkbaySnapshot::rooms)
    ).apply(i, WorkbaySnapshot::new));

    /**
     * One room slot, as the ROOMS page needs it. {@code interior} and {@code chunkCost} are what
     * the room <b>is</b>, not what the network's Frame entitles it to: an unopened slot is 0 and 0
     * and says {@code Empty}, and a room built before an upgrade still reads its own size until
     * somebody walks back into it and it grows.
     */
    /**
     * {@code biome} is the biome's id, not its name: the client turns it into
     * {@code biome.<namespace>.<path>}, which is the key every biome in every mod already has, so
     * the row names a modded biome correctly without this mod shipping a string for it.
     */
    public record Room(int index, String name, int interior, int chunkCost, boolean built,
        boolean anchored, String biome, com.neryos.workbay.content.room.RoomColour colour,
        /**
         * Who has been invited into this room, in the order the room lists them, so the screen can
         * name each one and step its level. Only ever this network's own rooms travel on a
         * snapshot, so this cannot leak a guest list to anybody the list is not about.
         */
        java.util.List<com.neryos.workbay.world.RoomRecord.Guest> guests) {
        public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Index").forGetter(Room::index),
            Codec.STRING.fieldOf("Name").forGetter(Room::name),
            Codec.INT.fieldOf("Interior").forGetter(Room::interior),
            Codec.INT.fieldOf("ChunkCost").forGetter(Room::chunkCost),
            Codec.BOOL.fieldOf("Built").forGetter(Room::built),
            Codec.BOOL.fieldOf("Anchored").forGetter(Room::anchored),
            Codec.STRING.optionalFieldOf("Biome", "").forGetter(Room::biome),
            com.neryos.workbay.content.room.RoomColour.CODEC.optionalFieldOf("Colour",
                com.neryos.workbay.content.room.RoomColour.DEFAULT).forGetter(Room::colour),
            com.neryos.workbay.world.RoomRecord.Guest.CODEC.listOf()
                .optionalFieldOf("Guests", java.util.List.of()).forGetter(Room::guests)
        ).apply(i, Room::new));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbaySnapshot> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** How many links are in a state the player has to do something about. The header's red count. */
    public int problems() {
        return (int) links.stream().filter(link -> link.status().isProblem()).count();
    }

    /**
     * What a room is called wherever it is named: the name the player gave it, or its slot.
     *
     * <p>Here rather than on ROOMS because three screens say it now — the ROOMS row, a LINKS row
     * into that room, and the flow map's node for it — and a room called "Ore Room" on one page
     * and "Room 2" on another is two rooms as far as the player can tell.
     */
    public net.minecraft.network.chat.Component roomLabel(int index) {
        String named = rooms.stream().filter(room -> room.index() == index)
            .map(Room::name).findFirst().orElse("");
        return named.isBlank()
            ? com.neryos.workbay.WorkbayLang.gui("rooms.name", index + 1)
            : net.minecraft.network.chat.Component.literal(named);
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
        Optional<ResourceLocation> targetBlock, Optional<Integer> targetBay,
        /**
         * Which of this network's rooms this link's Connector stands in, by <b>slot</b>. Present
         * only for a link into a room, and computed server-side for the same reason
         * {@code targetBay} is: the client cannot invert a Backshop position into a room, and
         * without this the one thing on the screen that says <em>where</em> the barrel is would be
         * a pair of six-figure coordinates in a dimension the player cannot walk to.
         *
         * <p>The slot and not the name, so a room the player renamed reads the same here as it
         * does on ROOMS without the server having to render a string the client already knows how
         * to build — {@link #rooms()} is on this same snapshot.
         */
        Optional<Integer> targetRoom) {

        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
            BusConfig.CODEC.fieldOf("Config").forGetter(Link::config),
            WBCodecs.ofEnum(BusRunner.BusStatus.class).fieldOf("Status").forGetter(Link::status),
            ResourceLocation.CODEC.optionalFieldOf("TargetBlock").forGetter(Link::targetBlock),
            // Only ever present for an internal (bay-to-bay) link, computed server-side because the
            // client has no way to invert a Backshop position back into a bay index.
            Codec.INT.optionalFieldOf("TargetBay").forGetter(Link::targetBay),
            Codec.INT.optionalFieldOf("TargetRoom").forGetter(Link::targetRoom)
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
