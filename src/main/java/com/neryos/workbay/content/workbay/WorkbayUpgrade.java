package com.neryos.workbay.content.workbay;

import com.neryos.workbay.config.WorkbayConfig;
import com.neryos.workbay.init.WBItems;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Every upgrade a v1 Workbay can hold, on one enum. SPEC.md §1.
 *
 * <p>Upgrades are <b>consumed on install</b> and recorded as counters, so there is no upgrade
 * inventory, no slot grid and no removal path. The dropped Workbay item carries every counter, so
 * moving a base loses nothing.
 *
 * <p>The Anchor, the Room Frames and the Annex Plate are in the tier table but are v2 (SPEC.md §16),
 * and are deliberately absent here rather than present and inert.
 */
public enum WorkbayUpgrade implements StringRepresentable {
    /**
     * <b>The maximum is a config value, not a number argued for here.</b> How long the ladder
     * should be is a balance question, and balance numbers in this mod come from measurement -
     * so the ceiling is {@code maxBaysPerWorkbay} minus the bays the base Workbay already grants,
     * and a pack that has actually played to the top can move it without a release. It is derived
     * from that knob rather than being a second one: two numbers that can disagree about the same
     * ceiling is how a player ends up holding a plate that installs and does nothing.
     */
    EXPANSION_PLATE("expansion_plate",
        () -> WorkbayConfig.SERVER.maxBaysPerWorkbay.get() - WorkbayRecord.BASE_BAYS,
        () -> WBItems.EXPANSION_PLATE.get()),
    /**
     * Cross-dimension reach, and a host's switch over it. Its max is
     * {@code allowCrossDimensionLinks} for the same reason the Anchor's is {@code allowAnchors}:
     * a host who does not want one network reaching into somebody else's End base gets an upgrade
     * that cannot be installed, rather than a registry that changes shape (SPEC.md §13). Every
     * cross-dimension link then reads "Needs Resonator" for ever, which is a refusal the row
     * already draws.
     */
    RESONATOR("resonator",
        () -> WorkbayConfig.SERVER.allowCrossDimensionLinks.get() ? 1 : 0,
        () -> WBItems.RESONATOR.get()),
    MULTICHANNEL("multichannel", () -> 1,
        () -> WBItems.MULTICHANNEL.get()),
    /**
     * Throughput, and the only upgrade that changes a number every link already has.
     *
     * <p>Two, because the third would be arguing about somebody else's mod: at two the ladder tops
     * out level with EnderIO's enhanced item conduit and just under its plain energy one, and a
     * mod that sells space rather than TPS (SPEC.md §0) has no business beating a cable mod at
     * cables. The rate a link is born with is deliberately modest -- an unupgraded link feeds a
     * furnace and starves a Mekanism machine, which is the shape of the ladder.
     *
     * <p>Two is now the <em>default</em> of {@code maxImpellers} rather than a number in this
     * file: the argument above is ours to make and a pack that has played past it is entitled to
     * disagree, which is the whole reason the ladder moved to config.
     */
    IMPELLER("impeller", () -> WorkbayConfig.SERVER.maxImpellers.get(),
        () -> WBItems.IMPELLER.get()),

    /**
     * The room line. Three Frames, <b>highest wins</b>, and one size for every room the network
     * owns — a Frame per room would mean a size stored per room, a way to say which room a Frame
     * is going into, and a player holding a Wide Frame with no idea where it landed.
     *
     * <p>Each is its own enum constant rather than one Frame with a level, because the install path
     * is a max and a counter and this is the shape that fits it: {@code installed} answers 1 once
     * the tier is already at least this one, so a smaller Frame on a bigger room is refused as
     * "already fitted" rather than quietly downgrading a room somebody is standing in.
     */
    ROOM_FRAME("room_frame", () -> 1,
        () -> WBItems.ROOM_FRAME.get()),
    WIDE_ROOM_FRAME("wide_room_frame", () -> 1,
        () -> WBItems.WIDE_ROOM_FRAME.get()),
    VAST_ROOM_FRAME("vast_room_frame", () -> 1,
        () -> WBItems.VAST_ROOM_FRAME.get()),

    /**
     * Lets a room be kept running while it is empty, and the bay column while nobody is near.
     * Grants the <em>ability</em> only: SPEC.md §0 switches each room on separately, beside the
     * number of chunks it holds, so one upgrade cannot quietly light thirty-six of them.
     *
     * <p>Its max is the config switch, so a host who wants no force loading in their JVM gets an
     * upgrade that cannot be installed rather than a registry that changes shape (SPEC.md §13).
     */
    ANCHOR("anchor", () -> WorkbayConfig.SERVER.allowAnchors.get() ? 1 : 0,
        () -> WBItems.ANCHOR.get()),

    /**
     * +1 room each, at whatever size the Frame says. The ceiling is {@code maxRoomsPerNetwork}
     * minus the one room a Room Frame already grants — derived from that knob rather than being a
     * second one, exactly as the Expansion Plate is derived from {@code maxBaysPerWorkbay}: two
     * numbers that can disagree about the same ceiling is how a player ends up holding a plate
     * that installs and does nothing. A room is a chunk bill, so it is a number a host owns.
     */
    ANNEX_PLATE("annex_plate",
        () -> WorkbayConfig.SERVER.maxRoomsPerNetwork.get() - 1,
        () -> WBItems.ANNEX_PLATE.get());

    /**
     * True for the upgrades the ROOMS page owns. They are bought there rather than on UPGRADES
     * because what they cost is a <b>chunk count</b>, and a chunk count means nothing without the
     * rooms it applies to beside it.
     */
    public boolean aboutRooms() {
        return this != EXPANSION_PLATE && this != RESONATOR && this != MULTICHANNEL
            && this != IMPELLER;
    }

    /** The Room Frame tier this upgrade grants, or 0 for anything that is not a Frame. */
    public int roomTier() {
        return switch (this) {
            case ROOM_FRAME -> 1;
            case WIDE_ROOM_FRAME -> 2;
            case VAST_ROOM_FRAME -> 3;
            default -> 0;
        };
    }

    private final String name;
    private final IntSupplier max;
    private final Supplier<Item> item;

    WorkbayUpgrade(String name, IntSupplier max, Supplier<Item> item) {
        this.name = name;
        this.max = max;
        this.item = item;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int max() {
        return Math.max(0, max.getAsInt());
    }


    public Item item() {
        return item.get();
    }
}
