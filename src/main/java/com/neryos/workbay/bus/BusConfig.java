package com.neryos.workbay.bus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

import java.util.Optional;
import java.util.UUID;

/**
 * One bus: one bay, one target, one resource. SPEC.md §4 and §5.
 *
 * <p>A bus targets exactly one position. No priority, no round-robin, no distance tiebreak — those
 * only mean anything with several targets, and buses are cheap enough that three destinations are
 * three buses. That decision deletes three widgets, a persisted rotation index, and everything they
 * would need synced, saved and tested.
 *
 * <p>Immutable, so a bus config can be handed around and compared with {@code equals} for the
 * server-side polling that {@code broadcastChanges} does.
 */
public record BusConfig(
    UUID id,
    String name,
    int bay,
    Resource resource,
    Mode mode,
    Optional<GlobalPos> target,
    Optional<Direction> targetFace,
    Optional<Direction> machineFace,
    int rate,
    int speed,
    DyeColor channel,
    boolean enabled) {

    /**
     * Legal speeds, in ticks. A fixed list rather than free entry so every one of them divides the
     * 1200-tick wheel in SPEC.md §9 — that is the whole reason the wheel works.
     */
    public static final int[] SPEEDS = { 10, 20, 40, 60, 100, 200 };

    public static final int DEFAULT_RATE = 8;
    public static final int DEFAULT_SPEED = 20;

    public static final Codec<BusConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("Id").forGetter(BusConfig::id),
        Codec.STRING.fieldOf("Name").forGetter(BusConfig::name),
        Codec.INT.fieldOf("Bay").forGetter(BusConfig::bay),
        StringRepresentable.fromEnum(Resource::values).fieldOf("Resource").forGetter(BusConfig::resource),
        StringRepresentable.fromEnum(Mode::values).fieldOf("Mode").forGetter(BusConfig::mode),
        GlobalPos.CODEC.optionalFieldOf("Target").forGetter(BusConfig::target),
        Direction.CODEC.optionalFieldOf("TargetFace").forGetter(BusConfig::targetFace),
        Direction.CODEC.optionalFieldOf("MachineFace").forGetter(BusConfig::machineFace),
        Codec.INT.fieldOf("Rate").forGetter(BusConfig::rate),
        Codec.INT.fieldOf("Speed").forGetter(BusConfig::speed),
        DyeColor.CODEC.optionalFieldOf("Channel", DyeColor.WHITE).forGetter(BusConfig::channel),
        Codec.BOOL.optionalFieldOf("Enabled", true).forGetter(BusConfig::enabled)
    ).apply(i, BusConfig::new));

    /**
     * Through the codec rather than {@code StreamCodec.composite}, which caps at six components and
     * this exceeds. SPEC.md §4 says to nest sub-records instead; there is nothing here that groups
     * naturally yet, and a nesting invented only to satisfy the cap would read worse.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, BusConfig> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static BusConfig create(UUID id, int bay, Resource resource, Mode mode, GlobalPos target) {
        return new BusConfig(id, resource.defaultName(mode), bay, resource, mode,
            Optional.of(target), Optional.empty(), Optional.empty(),
            DEFAULT_RATE, DEFAULT_SPEED, DyeColor.WHITE, true);
    }

    public BusConfig withRate(int newRate) {
        return new BusConfig(id, name, bay, resource, mode, target, targetFace, machineFace,
            newRate, speed, channel, enabled);
    }

    public BusConfig withSpeed(int newSpeed) {
        return new BusConfig(id, name, bay, resource, mode, target, targetFace, machineFace,
            rate, newSpeed, channel, enabled);
    }

    public BusConfig withEnabled(boolean nowEnabled) {
        return new BusConfig(id, name, bay, resource, mode, target, targetFace, machineFace,
            rate, speed, channel, nowEnabled);
    }

    /** A bus that was never linked does not tick at all; its row says so instead. */
    public boolean linked() {
        return target.isPresent();
    }

    public enum Resource implements StringRepresentable {
        ITEM("item"), FLUID("fluid"), ENERGY("energy");

        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        String defaultName(Mode mode) {
            return (mode == Mode.INSERT ? "Send " : "Pull ") + name;
        }
    }

    /**
     * What the bus does <em>at its target</em>. Insert pushes the hosted machine's output into the
     * target; extract pulls from the target into the hosted machine. Stated this way round because
     * the target is the end the player picks and names.
     */
    public enum Mode implements StringRepresentable {
        INSERT("insert"), EXTRACT("extract");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
