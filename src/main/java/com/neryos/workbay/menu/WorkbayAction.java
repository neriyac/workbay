package com.neryos.workbay.menu;

/**
 * Everything the screens can ask the server to do. SPEC.md §4.
 *
 * <p>One enum and one packet rather than a payload type per button: every one of these is
 * "an id and at most one number", and thirteen near-identical payload classes would be thirteen
 * places to forget the container-id guard.
 */
public enum WorkbayAction {
    /** Which bay the screen is showing. Server-side because the face buttons act on it. */
    SELECT_BAY,
    /** Rack whatever the player is holding into the selected bay. */
    RACK,
    /** Take the selected bay's machine back out, with everything inside it. */
    EJECT,
    TOGGLE_LOCK,
    /** {@code arg} packs the resource ordinal and the direction ordinal. */
    CYCLE_FACE,
    /** Pair the held Connector to this Workbay and the selected bay. */
    PAIR,
    LINK_FLIP_MODE,
    LINK_CYCLE_RESOURCE,
    LINK_TOGGLE_ENABLED,
    LINK_REMOVE,
    /** {@code arg} is a {@link com.neryos.workbay.content.workbay.WorkbayUpgrade} ordinal. */
    INSTALL_UPGRADE,
    /** {@code arg} is a whole {@link com.neryos.workbay.world.FaceConfig} in bits. */
    PASTE_BAY,
    /**
     * One entry of a link's filter. {@code link} names the row; {@code arg} packs the slot in its
     * high 32 bits and, in the low 32, the network id of the item or fluid plus one — zero clears
     * that slot. Which registry the id belongs to is the link's own resource, the same way
     * {@link com.neryos.workbay.bus.BusFilter} reads its entries.
     */
    SET_FILTER,
    /** Whitelist or blacklist, for the link {@code link} names. */
    TOGGLE_FILTER_DENY,
    /**
     * Bay to bay, no Connector. {@code arg} is the bay to point at; an arg that names the source
     * bay itself, or a bay this Workbay does not have, falls back to the next other bay.
     */
    CREATE_INTERNAL_LINK,
    /** {@code link} names the row and {@code arg} the bay to hand it to. */
    LINK_ASSIGN_BAY,
    /** Internal links only. {@code link} names the row; steps its target to the next other bay. */
    LINK_CYCLE_TARGET_BAY,
    /** {@code link} names the row; steps which face of the target block it reaches into. */
    LINK_CYCLE_TARGET_FACE,
    /** {@code text} is the selected bay's new name; empty falls back to the machine's own. */
    SET_BAY_NAME,
    /**
     * {@code link} names the row and {@code text} its new name; empty falls back to what the row
     * derives from its target. Appended at the end of this enum on purpose -- {@link
     * com.neryos.workbay.network.ActionPacket} puts an action on the wire as its ordinal, so
     * inserting one in the middle would silently rename every action after it.
     */
    SET_LINK_NAME,
    /** Cycles the selected bay's {@link com.neryos.workbay.world.RedstoneMode}. */
    CYCLE_REDSTONE,
    /** Steps the Assay's skim rate up by five, or down by five on a right-click. SPEC.md §3. */
    SET_SKIM,
    /**
     * Opens Bay View on the selected bay (SPEC.md §5). A second menu rather than a page of this
     * one: it is the only screen in the mod with real slots, and this menu deliberately has none.
     */
    OPEN_BAY_VIEW,
    /**
     * Gets the player to the selected machine's own screen (SPEC.md §5). <b>Where they stand if
     * both sides can</b>, and a trip into the bay if either cannot — {@code arg} is 1 when the
     * <em>client</em> has the mixins on, because a client without them cannot find a machine in a
     * chunk it was never sent, and only the client knows its own file.
     */
    ENTER_BAY,
    /** Puts the player in room {@code arg} of this network, building it on its first visit. */
    ENTER_ROOM,
    /**
     * Switches room {@code arg}'s Anchor on or off. Off is the default and the whole point: an
     * Anchor that lit every room the network owns would buy 36 ticking chunks with one upgrade.
     */
    TOGGLE_ROOM_ANCHOR,
    /**
     * Steps room {@code arg}'s biome to the next entry of {@code #workbay:room_biomes} and writes
     * it over the room's chunks. Appended at the end for the same reason {@link #SET_LINK_NAME} is:
     * an action travels as its ordinal.
     */
    CYCLE_ROOM_BIOME,
    /**
     * Repaints room {@code arg}'s shell. A right-click steps backwards, the way {@link #SET_SKIM}
     * does: ten colours is five clicks either way instead of nine one way.
     */
    CYCLE_ROOM_COLOUR,
    /**
     * Out of the room the player is standing in, back to where they entered from. Only
     * {@link RoomDoorMenu} sends it -- the Workbay screen has no way to be open in a room.
     */
    LEAVE_ROOM,
    /**
     * Room {@code arg}'s biome, chosen from a list rather than stepped: {@code arg} packs the room
     * in its low 16 bits and the index into {@link WorkbaySnapshot#roomBiomes} in the next 16.
     * A cycle button showed no state at all — it was an icon that never changed, so clicking it
     * looked exactly like nothing happening.
     */
    SET_ROOM_BIOME,
    /** The same for the shell's colour; the high half is a {@code RoomColour} ordinal. */
    SET_ROOM_COLOUR,
    /**
     * Invites a player into room {@code arg} at {@link com.neryos.workbay.world.RoomGuest#LOOK}.
     * {@code text} is the name they are known by, resolved to a UUID server-side -- the client has
     * no profile cache and could only ever send a name.
     *
     * <p>Appended at the end for the same reason every other room action was: an action travels as
     * its ordinal, so inserting one renames every action after it on the wire.
     */
    INVITE_ROOM_GUEST,
    /**
     * Steps one guest's level. {@code arg} packs the room in its low 16 bits and the guest's
     * position in that room's list in the next 16.
     */
    CYCLE_ROOM_GUEST,
    /** Un-invites one guest. Packed the same way as {@link #CYCLE_ROOM_GUEST}. */
    REMOVE_ROOM_GUEST,
    /**
     * How much one link moves in a step. {@code link} names the row and {@code arg} is the new
     * value, clamped server-side against {@code linkMaxRate} -- the client works out the step and
     * the server decides what is legal, because a config a client cannot see is the server's.
     */
    SET_LINK_RATE,
    /**
     * How long one link waits between steps. {@code arg} is an index into
     * {@link com.neryos.workbay.bus.BusConfig#SPEEDS}: a free number would not divide the tick
     * wheel, which is the whole reason the list is fixed.
     */
    SET_LINK_SPEED
}
