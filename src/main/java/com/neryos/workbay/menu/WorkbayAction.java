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
    TOGGLE_ROOM_ANCHOR
}
