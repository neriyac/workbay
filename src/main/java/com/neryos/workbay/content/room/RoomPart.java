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

    /** The texture this part draws, under {@code textures/block/}. */
    public String texture() {
        return "room_" + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
