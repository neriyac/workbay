package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.neryos.workbay.bus.BusConfig;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which of a hosted machine's faces a link may use, per resource type. SPEC.md §4's isometric cube.
 *
 * <p>Three faces of state per resource, six faces each, three values each — packed two bits per
 * face into one int per resource rather than a map, because this rides in every snapshot the screen
 * receives and a map of maps would be most of the packet.
 *
 * <p><b>All-{@code NONE} means "any face", not "no face".</b> That is what an unconfigured bay
 * looks like, and it has to keep working exactly as it did before anyone opened the screen —
 * a machine that stops moving items because the player looked at it is the worst outcome here.
 */
public record FaceConfig(int items, int fluids, int energy) {

    public static final FaceConfig NONE = new FaceConfig(0, 0, 0);

    public static final Codec<FaceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("Items", 0).forGetter(FaceConfig::items),
        Codec.INT.optionalFieldOf("Fluids", 0).forGetter(FaceConfig::fluids),
        Codec.INT.optionalFieldOf("Energy", 0).forGetter(FaceConfig::energy)
    ).apply(i, FaceConfig::new));

    /** What one face does for one resource. Grey, green, blue on the cube, in this order. */
    public enum Role {
        NONE, INPUT, OUTPUT;

        public Role next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public Role role(BusConfig.Resource resource, Direction face) {
        return Role.values()[(packed(resource) >> (face.ordinal() * 2)) & 0b11];
    }

    public FaceConfig cycled(BusConfig.Resource resource, Direction face) {
        int shift = face.ordinal() * 2;
        int updated = (packed(resource) & ~(0b11 << shift))
            | (role(resource, face).next().ordinal() << shift);
        return switch (resource) {
            case ITEM -> new FaceConfig(updated, fluids, energy);
            case FLUID -> new FaceConfig(items, updated, energy);
            case ENERGY -> new FaceConfig(items, fluids, updated);
        };
    }

    /**
     * The faces a link may bind to, given what it is doing at the machine end.
     *
     * @param takingOut true when the link pulls out of the hosted machine, so it wants OUTPUT faces
     * @return every direction when nothing is configured — see the class note
     */
    public Set<Direction> usable(BusConfig.Resource resource, boolean takingOut) {
        if (packed(resource) == 0) {
            return EnumSet.allOf(Direction.class);
        }
        Role wanted = takingOut ? Role.OUTPUT : Role.INPUT;
        EnumSet<Direction> allowed = EnumSet.noneOf(Direction.class);
        for (Direction face : Direction.values()) {
            if (role(resource, face) == wanted) {
                allowed.add(face);
            }
        }
        // Configured, but not for this direction of travel. Falling back to every face would make
        // the config a decoration; an empty set is what makes the cube mean something.
        return allowed;
    }

    private int packed(BusConfig.Resource resource) {
        return switch (resource) {
            case ITEM -> items;
            case FLUID -> fluids;
            case ENERGY -> energy;
        };
    }
}
