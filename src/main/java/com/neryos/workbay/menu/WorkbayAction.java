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
    INSTALL_UPGRADE
}
