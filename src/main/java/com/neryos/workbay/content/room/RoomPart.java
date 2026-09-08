package com.neryos.workbay.content.room;

import net.minecraft.util.StringRepresentable;

/**
 * Which piece of a room's shell a block is. SPEC.md §8.
 *
 * <p>One block with a part rather than six blocks: they share a colour, a hardness, a refusal to be
 * broken and the right-click that opens the way out, and every one of those would otherwise be
 * written six times.
 *
 * <p>The four {@code DOOR_*} parts are the quarters of one <b>2×2 door</b>. Two wide because a
 * room's wall is sixteen blocks across and sixteen has no middle block — a one-block doorway sits
 * off-centre by half a block and looks like a hatch somebody forgot to finish. Two wide is exactly
 * centred on the seam, and it reads as a door because it is the shape of one.
 */
public enum RoomPart implements StringRepresentable {
    WALL("wall"),
    FLOOR("floor"),
    /**
     * The bottom course of every wall: a plinth with a chamfered top.
     *
     * <p>It exists because the first room anybody looked at was one grey value on all six faces,
     * and the thing that fixes that is not a better wall texture — it is a <b>line where the floor
     * meets the wall</b>. Every real room has one and no box does.
     */
    SKIRTING("skirting"),
    /**
     * The ceiling, and deliberately <em>darker</em> than the wall under it.
     *
     * <p>The first attempt made it a bright luminous panel, to explain where a sealed room's light
     * comes from. That cannot work: Minecraft shades a bottom face to half, so 250 under the
     * ceiling renders darker than 200 on a wall and no texture value can beat it. What a ceiling
     * can be is unmistakably not the wall — coffered, so the room has a top rather than a fourth
     * copy of its sides.
     */
    CEILING("ceiling"),
    DOOR_BOTTOM_LEFT("door_bl"),
    DOOR_BOTTOM_RIGHT("door_br"),
    DOOR_TOP_LEFT("door_tl"),
    DOOR_TOP_RIGHT("door_tr");

    private final String name;

    RoomPart(String name) {
        this.name = name;
    }

    /** True for the one face a player stands on, which is the only part with its own tint. */
    public boolean isFloor() {
        return this == FLOOR;
    }

    /** True for the four quarters of a drawn door, which are the only blocks that open one. */
    public boolean isDoor() {
        return this == DOOR_BOTTOM_LEFT || this == DOOR_BOTTOM_RIGHT
            || this == DOOR_TOP_LEFT || this == DOOR_TOP_RIGHT;
    }

    /** The texture this part draws, under {@code textures/block/}. */
    public String texture() {
        return "room_" + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
