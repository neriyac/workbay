package com.neryos.workbay.world;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How much of somebody else's room a guest gets. SPEC.md §8.
 *
 * <p>Two levels, because two is what the room is actually for: somebody you are showing the place
 * to, and somebody helping you run it. A third — <em>may use what is here but may not change
 * it</em> — is the one that keeps suggesting itself and is deliberately not here; see
 * {@code OPEN_ISSUES}, because the case for it is real and it is a decision, not an omission.
 *
 * <p>The owner is not a level. An owner is the network that owns the room, and there is exactly
 * one; putting them on this ring would make "owner" a thing you could be invited to.
 */
public enum RoomGuest implements StringRepresentable {
    /**
     * May stand in the room and nothing else. No block broken, no block placed, no container
     * opened — a right-click on a chest is a change to what is in it, and this level is the one
     * that means "I am showing you the place".
     */
    LOOK("look"),
    /** May do anything in the room the owner may. The walls are still not breakable by anyone. */
    BUILD("build");

    public static final Codec<RoomGuest> CODEC = StringRepresentable.fromEnum(RoomGuest::values);

    private final String name;

    RoomGuest(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** One step round the ring, which with two of them is the other one. */
    public RoomGuest step() {
        return this == LOOK ? BUILD : LOOK;
    }
}
