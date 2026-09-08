package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Every string the player sees. SPEC.md §6: plain, present tense, second person only when giving an
 * instruction; state what happened, then why, then what to do.
 */
public class WBLanguageProvider extends LanguageProvider {

    public WBLanguageProvider(PackOutput output) {
        super(output, Workbay.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + Workbay.MOD_ID, "Workbay");

        addBlock(WBBlocks.WORKBAY, "Workbay");
        addBlock(WBBlocks.PORT, "Port");
        addBlock(WBBlocks.ROOM_WALL, "Room Wall");
        addItem(WBItems.SHOPSTEEL, "Shopsteel");
        addItem(WBItems.HOUSING, "Housing");
        addBlock(WBBlocks.CONNECTOR, "Connector");
        addBlock(WBBlocks.ASSAY, "Assay");
        addItem(WBItems.EXPANSION_PLATE, "Expansion Plate");
        addItem(WBItems.ROOM_FRAME, "Room Frame");
        addItem(WBItems.WIDE_ROOM_FRAME, "Wide Room Frame");
        addItem(WBItems.VAST_ROOM_FRAME, "Vast Room Frame");
        addItem(WBItems.ANNEX_PLATE, "Annex Plate");
        addItem(WBItems.ANCHOR, "Anchor");
        addItem(WBItems.RESONATOR, "Resonator");
        addItem(WBItems.MULTICHANNEL, "Multichannel Upgrade");
        addItem(WBItems.IMPELLER, "Impeller");

        // Chat, one-shot. SPEC.md §6 and §14's network model: a Workbay belongs to the player who
        // placed it, not to the specific item, so there is no code to lose.
        add(WorkbayLang.messageKey("room_created"), "Workbay network created and bound to your "
            + "account. Lose the block and a fresh, uncrafted Workbay picks it straight back up.");
        add(WorkbayLang.messageKey("network_reused"), "Workbay reconnected \u2014 same bays, same "
            + "links, same upgrades as before.");
        add(WorkbayLang.messageKey("network_cap_reached"), "You already own the maximum of %s "
            + "Workbay network(s). Break one before starting another.");
        add(WorkbayLang.messageKey("network_deployed_full"), "This network already has %s Workbay(s) "
            + "placed. Break one of them before placing another.");
        add(WorkbayLang.messageKey("break_warning"), "Breaking this Workbay leaves its bays behind. "
            + "The machines keep running \u2014 place any fresh Workbay to get back in.");
        add(WorkbayLang.messageKey("locked"), "This Workbay is locked.");

        // Insert rejections. Action bar, RED. SPEC.md §6: say what happened, then why, then what
        // to do - and never blame the player for trying.
        add(WorkbayLang.messageKey("reject.not_a_block"), "%s isn't a block. A bay holds machines.");
        add(WorkbayLang.messageKey("reject.no_machine"), "%s has nothing running inside it. A bay "
            + "can only hold a block with a block entity.");
        add(WorkbayLang.messageKey("reject.needs_neighbours"), "%s works by connecting to the blocks "
            + "around it. A bay has no neighbours to connect to.");
        add(WorkbayLang.messageKey("reject.no_ports"), "%s has no item, fluid or energy ports. A bus "
            + "would have nothing to connect to.");
        add(WorkbayLang.messageKey("reject.kinetic"), "%s runs on rotational force. A bay can't turn "
            + "a shaft.");
        add(WorkbayLang.messageKey("reject.multiblock"), "%s is part of a multiblock. A bay holds one "
            + "block; build it in a room instead.");
        add(WorkbayLang.messageKey("reject.world_interacting"), "%s works on the world around it. A "
            + "bay is empty, so it would have nothing to do.");
        add(WorkbayLang.messageKey("reject.immovable"), "%s can't be safely relocated.");
        add(WorkbayLang.messageKey("reject.recursion"), "A Workbay can't go inside a Workbay.");
        add(WorkbayLang.messageKey("reject.pack_denied"), "This pack doesn't allow %s to be hosted.");
        add(WorkbayLang.messageKey("reject.protected"), "You can't take %s from here.");
        add(WorkbayLang.messageKey("reject.no_bay"), "Every bay is full. Install an Expansion Plate "
            + "for another.");
        // OPEN_ISSUES #27: a bay slot that refuses in silence is unexplainable. Every branch of
        // WorkbayMenu#rack now names itself.
        add(WorkbayLang.messageKey("reject.bay_occupied"), "This bay already has something standing "
            + "in it. Pick an empty bay, or eject what is there first.");
        add(WorkbayLang.messageKey("reject.empty_hand"), "Hold the machine you want to rack, then "
            + "click the bay.");
        add(WorkbayLang.messageKey("reject.rack_failed"), "%s wouldn't stand up in a bay, so nothing "
            + "was placed. You still have it.");
        add(WorkbayLang.messageKey("reject.assay_needs_bay"), "The Assay only works racked in a bay. "
            + "It has no faces to connect to out here.");

        // The Connector, which is where every link comes from. SPEC.md §0.
        add(WorkbayLang.messageKey("connector_paired"), "Connector paired. Place it against the "
            + "block you want to link.");
        add(WorkbayLang.messageKey("connector_unpaired"), "This Connector isn't paired yet. "
            + "Right-click a Workbay with it first.");
        add(WorkbayLang.messageKey("connector_linked"), "Linked to %s. Workbay %s has a row for it, "
            + "switched off until you turn it on.");
        add(WorkbayLang.messageKey("connector_full"), "This Connector already carries everything it "
            + "can. Install a Multichannel Upgrade to carry items, fluids and energy on one.");
        add(WorkbayLang.messageKey("connector_no_workbay"), "The Workbay this Connector is paired "
            + "to isn't loaded. Go back to it and place this again.");


        // ---- The screens. SPEC.md §4 and §6: icons buy back the horizontal room the link rows
        // need, so every icon button pays for itself with a tooltip written as a full sentence.
        add(WorkbayLang.guiKey("title"), "Workbay");
        add(WorkbayLang.guiKey("unbuilt"), "Not built yet.");
        add(WorkbayLang.guiKey("power"), "%s / %s FE");
        add(WorkbayLang.guiKey("power.tip"), "Energy stored in the Workbay itself. Hosted machines "
            + "keep their own.");
        add(WorkbayLang.guiKey("power.machine.tip"), "Energy stored in this machine.");
        add(WorkbayLang.guiKey("power.none"), "No power");

        // The header's three counters. Keys rather than strings built in the Java: they sit on the
        // same line as power.none, which has always been a key, and a count with no plural rule is
        // how "1 links" got shipped.
        add(WorkbayLang.guiKey("count.bays"), "%s / %s bays");
        add(WorkbayLang.guiKey("count.links"), "%s links");
        add(WorkbayLang.guiKey("count.links.one"), "1 link");
        add(WorkbayLang.guiKey("count.problems"), "%s problems");
        add(WorkbayLang.guiKey("count.problems.one"), "1 problem");
        add(WorkbayLang.guiKey("count.problems.none"), "no problems");

        add(WorkbayLang.guiKey("button.upgrades"), "Upgrades");
        add(WorkbayLang.guiKey("button.upgrades.tip"), "Install Expansion Plates, a Resonator or a "
            + "Multichannel Upgrade.");
        add(WorkbayLang.guiKey("button.flow"), "Flow map");
        add(WorkbayLang.guiKey("button.flow.tip"), "See what every link moves, and which flows never "
            + "leave this block.");
        add(WorkbayLang.guiKey("button.lock"), "Lock");
        add(WorkbayLang.guiKey("button.unlock"), "Unlock");
        add(WorkbayLang.guiKey("button.lock.tip"), "A locked Workbay only lets its owner rack, eject "
            + "or change anything.");
        add(WorkbayLang.guiKey("button.back"), "Back to bays");
        add(WorkbayLang.guiKey("button.back.tip"), "Return to the bays screen.");
        add(WorkbayLang.guiKey("button.bay_view"), "Bay View");
        add(WorkbayLang.guiKey("button.eject"), "Eject");
        add(WorkbayLang.guiKey("button.eject.tip"), "Take this machine out of its bay, with "
            + "everything inside it.");
        add(WorkbayLang.guiKey("links.target.tip"), "The block this link is pointed at. Shown as "
            + "the block itself because a row of names all read alike at a glance and a chest "
            + "does not.");
        add(WorkbayLang.guiKey("links.rename.tip"), "Right-click to name this link. A name of "
            + "your own is what the flow map draws instead of the target's coordinates. Return "
            + "commits it, Escape leaves it alone, and an empty name goes back to the derived one.");
        add(WorkbayLang.guiKey("button.rename"), "Rename");
        add(WorkbayLang.guiKey("button.rename.tip"), "Give this bay a name of your own. Return "
            + "commits it, Escape leaves it alone, and an empty name goes back to the machine's.");
        add(WorkbayLang.guiKey("redstone.always"), "Redstone: always");
        add(WorkbayLang.guiKey("redstone.always.tip"), "This bay's links run whatever the redstone "
            + "at the Workbay is doing. Click to change; right-click steps back.");
        add(WorkbayLang.guiKey("redstone.with_signal"), "Redstone: with a signal");
        add(WorkbayLang.guiKey("redstone.with_signal.tip"), "This bay's links run only while the "
            + "Workbay has a redstone signal.");
        add(WorkbayLang.guiKey("redstone.without_signal"), "Redstone: without a signal");
        add(WorkbayLang.guiKey("redstone.without_signal.tip"), "This bay's links run only while the "
            + "Workbay has no redstone signal.");
        add(WorkbayLang.guiKey("redstone.pulse"), "Redstone: pulse");
        add(WorkbayLang.guiKey("redstone.pulse.tip"), "One operation each time the signal turns on, "
            + "so a clock moves exactly one load per tick of the clock.");
        // The forms that share a 130-pixel line with the bay's status.
        add(WorkbayLang.guiKey("redstone.short.always"), "");
        add(WorkbayLang.guiKey("redstone.short.with_signal"), "with signal");
        add(WorkbayLang.guiKey("redstone.short.without_signal"), "no signal");
        add(WorkbayLang.guiKey("redstone.short.pulse"), "pulse");
        add(WorkbayLang.guiKey("status.held_by_redstone"), "Held");
        add(WorkbayLang.guiKey("status.held_by_redstone.tip"), "Waiting on this bay's redstone "
            + "mode. Nothing is wrong; this is what you asked it to do.");
        add(WorkbayLang.guiKey("button.copy"), "Copy this bay");
        add(WorkbayLang.guiKey("button.copy.tip"), "Copies this bay's face settings, so the next "
            + "seven do not have to be set by hand.");
        add(WorkbayLang.guiKey("button.paste"), "Paste onto this bay");
        add(WorkbayLang.guiKey("button.paste.tip"), "Replaces this bay's face settings with the "
            + "ones you copied.");
        add(WorkbayLang.guiKey("button.paste.empty"), "Copy a bay first.");

        add(WorkbayLang.guiKey("bay.n"), "Bay %s");
        add(WorkbayLang.guiKey("bay.empty"), "Empty bay");
        add(WorkbayLang.guiKey("bay.empty.tip"), "Hold a machine and click here to rack it.");
        add(WorkbayLang.guiKey("bay.rack.tip"), "Hold a machine and click to rack it in this bay.");
        add(WorkbayLang.guiKey("bay.locked"), "Locked bay");
        add(WorkbayLang.guiKey("bay.locked.tip"), "Install an Expansion Plate to open this bay.");
        add(WorkbayLang.guiKey("bay.running"), "Running");
        add(WorkbayLang.guiKey("bay.idle"), "Idle");
        add(WorkbayLang.guiKey("bay.inert"), "Nothing can reach this machine on any face. It keeps "
            + "running; no link can use it.");
        // The one-word forms the machine block's status line uses. The sentences above are tooltips.
        add(WorkbayLang.guiKey("bay.short.running"), "Running");
        add(WorkbayLang.guiKey("bay.short.idle"), "Idle");
        add(WorkbayLang.guiKey("bay.short.inert"), "No ports");
        add(WorkbayLang.guiKey("bay.short.empty"), "Empty bay");
        add(WorkbayLang.guiKey("bay.short.locked"), "Locked");

        add(WorkbayLang.guiKey("faces.item"), "Item faces");
        add(WorkbayLang.guiKey("faces.fluid"), "Fluid faces");
        add(WorkbayLang.guiKey("faces.energy"), "Energy faces");
        add(WorkbayLang.guiKey("faces.tip"), "The cube shows one resource type at a time, so a face "
            + "can take items in and send energy out. Click a face to cycle it in, out, off; "
            + "right-click steps back.");
        add(WorkbayLang.guiKey("faces.face"), "Face: %s");
        add(WorkbayLang.guiKey("faces.role.none"), "Unset. With no face set, links use whichever "
            + "face answers.");
        add(WorkbayLang.guiKey("faces.role.input"), "Input. Links may push into this face.");
        add(WorkbayLang.guiKey("faces.role.output"), "Output. Links may pull out of this face.");
        add(WorkbayLang.guiKey("faces.drag"), "Drag to turn");
        add(WorkbayLang.guiKey("faces.empty"), "Rack a machine to set its faces.");

        add(WorkbayLang.guiKey("links.none"), "No links yet. Pair a Connector and place it on "
            + "something.");
        add(WorkbayLang.guiKey("links.pair"), "Pair a Connector");
        add(WorkbayLang.guiKey("links.pair.tip"), "Pairs the Connector in your hand to this Workbay "
            + "and the selected bay. Place it on the block you want linked.");
        add(WorkbayLang.guiKey("links.internal"), "Link a bay");
        add(WorkbayLang.guiKey("links.internal.tip"), "Adds a link from the selected bay straight "
            + "to another one, with no Connector and nothing to place in the world.");
        add(WorkbayLang.guiKey("links.internal.retarget"), "Change which bay");
        add(WorkbayLang.guiKey("links.internal.retarget.tip"), "Click to point this link at the "
            + "next bay instead. Right-click steps back.");
        add(WorkbayLang.messageKey("internal_link_needs_second_bay"), "This Workbay only has one "
            + "bay. Install an Expansion Plate before linking bay to bay.");
        add(WorkbayLang.guiKey("links.filter.this_bay"), "Showing: this bay");
        add(WorkbayLang.guiKey("links.filter.all_bays"), "Showing: all bays");
        add(WorkbayLang.guiKey("links.filter.problems"), "Showing: problems only");
        add(WorkbayLang.guiKey("links.filter.tip"), "Click to change which links this list shows. Right-click steps back.");
        add(WorkbayLang.guiKey("links.sort.added"), "In the order they were made");
        add(WorkbayLang.guiKey("links.sort.bay"), "Sorted by bay");
        add(WorkbayLang.guiKey("links.sort.type"), "Sorted by resource type");
        add(WorkbayLang.guiKey("links.sort.status"), "Sorted by status, problems first");
        add(WorkbayLang.guiKey("links.sort.tip"), "Click to change the order. Right-click steps back.");
        add(WorkbayLang.guiKey("links.type.item"), "Carries items");
        add(WorkbayLang.guiKey("links.type.fluid"), "Carries fluids");
        add(WorkbayLang.guiKey("links.type.energy"), "Carries energy");
        add(WorkbayLang.guiKey("links.type.tip"), "Click to change what this link carries. Right-click steps back.");
        add(WorkbayLang.guiKey("links.none.here"), "No links on this bay. %s on other bays \u2014 press Add to move one here.");
        add(WorkbayLang.guiKey("links.adding"), "TO BAY %s");
        add(WorkbayLang.guiKey("links.add"), "Add a link");
        add(WorkbayLang.guiKey("links.add.close"), "Back to this bay's links");
        add(WorkbayLang.guiKey("links.add.tip"), "Lists everything this bay could be attached "
            + "to: a Connector another bay is holding, or another bay directly.");
        add(WorkbayLang.guiKey("links.add.none.links"), "No links on other bays. Pair a Connector "
            + "and place it on something.");
        add(WorkbayLang.guiKey("links.add.none.bays"), "No other bays. Rack a second one first.");
        add(WorkbayLang.guiKey("links.add.tab.connectors"), "Links other bays hold");
        add(WorkbayLang.guiKey("links.add.tab.bays"), "Bays in this Workbay");
        add(WorkbayLang.guiKey("links.add.tab.tip"), "Connectors already standing in the world, or "
            + "the bays in this rack, which need no Connector at all.");
        add(WorkbayLang.guiKey("links.add.apply"), "Attach %s ticked");
        add(WorkbayLang.guiKey("links.add.apply.tip"), "Attaches everything ticked on both tabs in "
            + "one go, then closes the picker.");
        add(WorkbayLang.guiKey("links.add.link"), "%s, held by bay %s");
        add(WorkbayLang.guiKey("links.add.link.tip"), "Hands this link to bay %s. The Connector "
            + "stays where it is; only which bay it feeds changes.");
        add(WorkbayLang.guiKey("links.add.bay"), "Bay %s");
        add(WorkbayLang.guiKey("links.add.bay.tip"), "Makes a link straight to that bay. No "
            + "Connector, no block in the world.");
        add(WorkbayLang.guiKey("links.add.nowire"), "no wire");
        add(WorkbayLang.guiKey("links.face.any"), "Any face");
        add(WorkbayLang.guiKey("links.face.down"), "Bottom face");
        add(WorkbayLang.guiKey("links.face.up"), "Top face");
        add(WorkbayLang.guiKey("links.face.north"), "North face");
        add(WorkbayLang.guiKey("links.face.south"), "South face");
        add(WorkbayLang.guiKey("links.face.west"), "West face");
        add(WorkbayLang.guiKey("links.face.east"), "East face");
        add(WorkbayLang.guiKey("links.face.tip"), "Which side of the target block this link "
            + "reaches into. Machines that keep their input and output in separate slots expose "
            + "them on separate faces, so pin one. Any face uses whichever answers first. "
            + "Right-click steps back.");
        add(WorkbayLang.guiKey("links.mode.insert"), "Sends into the target");
        add(WorkbayLang.guiKey("links.mode.extract"), "Pulls out of the target");
        add(WorkbayLang.guiKey("links.mode.tip"), "Click to turn this link around.");
        add(WorkbayLang.guiKey("links.remove"), "Remove this link");
        add(WorkbayLang.guiKey("links.remove.tip"), "The Connector stays where it is. Right-click it "
            + "to link it again.");
        add(WorkbayLang.guiKey("links.enable"), "Off");
        add(WorkbayLang.guiKey("links.enable.tip"), "A new link starts off, so nothing moves until "
            + "you have looked at the row. Click to turn it on.");
        add(WorkbayLang.guiKey("links.disable"), "On");
        add(WorkbayLang.guiKey("links.disable.tip"), "Click to stop this link without deleting it.");
        add(WorkbayLang.guiKey("links.problems"), "%s needing attention");
        add(WorkbayLang.guiKey("links.problems.tip"), "Click to list only the links that are in "
            + "trouble. Each one is marked with a red bar and says what is wrong with it.");
        add(WorkbayLang.guiKey("links.bay"), "Bay %s");
        add(WorkbayLang.guiKey("links.bay.tip"), "The bay this link moves to and from. Every link "
            + "belongs to exactly one.");
        // The filter. SPEC.md §5: it matches on what a thing is and nothing else, so every string
        // here names an item or a fluid and none of them mentions a mode, a tag or a count.
        add(WorkbayLang.guiKey("filter.none"), "No filter");
        add(WorkbayLang.guiKey("filter.some.allow"), "Whitelist, %s listed");
        add(WorkbayLang.guiKey("filter.some.deny"), "Blacklist, %s listed");
        add(WorkbayLang.guiKey("filter.tip"), "Click to choose what this link may carry. With "
            + "nothing listed it carries everything.");
        add(WorkbayLang.guiKey("filter.energy"), "Energy has nothing to filter");
        add(WorkbayLang.guiKey("filter.energy.tip"), "A filter names an item or a fluid. Energy is "
            + "a number, so there is nothing here to name.");
        // "Whitelist" and "Blacklist", not "Only these" and "All but these". The genre settled
        // this vocabulary years ago -- XNet, EnderIO and LaserIO all use it -- and a player who
        // already knows the words does not have to read a button to find out it is the same idea
        // under a nicer name. The sentence explaining which is which lives in the tooltip.
        add(WorkbayLang.guiKey("filter.allow"), "Whitelist");
        add(WorkbayLang.guiKey("filter.deny"), "Blacklist");
        add(WorkbayLang.guiKey("filter.mode.tip"), "Whitelist carries only what is listed. "
            + "Blacklist carries everything except it. Click to swap.");
        add(WorkbayLang.guiKey("filter.slot"), "Empty");
        add(WorkbayLang.guiKey("filter.slot.tip.item"), "Click to drop what you are carrying here. "
            + "With empty hands it lists what you are holding, and a recipe-list drag works too.");
        add(WorkbayLang.guiKey("filter.slot.tip.fluid"), "Click to drop what you are carrying here. "
            + "A bucket or tank lists the fluid inside it; a recipe-list drag works too.");
        add(WorkbayLang.guiKey("filter.entry.tip"), "Click to lift this off the list. "
            + "Right-click to take it off without picking it up.");
        add(WorkbayLang.guiKey("filter.carrying"), "Click a slot to list it, anywhere else to drop it.");
        add(WorkbayLang.guiKey("filter.listed.allow"), "Only what is listed goes through.");
        add(WorkbayLang.guiKey("filter.listed.deny"), "Everything except what is listed goes through.");
        add(WorkbayLang.guiKey("filter.empty"), "Nothing listed. This link carries everything.");
        add(WorkbayLang.guiKey("filter.close"), "Back to the links");
        add(WorkbayLang.guiKey("filter.close.tip"), "The filter is saved as you change it. There "
            + "is nothing to confirm.");
        add(WorkbayLang.guiKey("links.unknown"), "not loaded");

        // The row's column is 60 pixels. The long names above are tooltip titles and do not fit
        // it; "No face for this" arrived on screen as "No face f". Same split as bay.short.*.
        add(WorkbayLang.guiKey("status.short.running"), "Running");
        add(WorkbayLang.guiKey("status.short.idle"), "Idle");
        add(WorkbayLang.guiKey("status.short.disabled"), "Off");
        add(WorkbayLang.guiKey("status.short.held_by_redstone"), "Held");
        add(WorkbayLang.guiKey("status.short.connector_gone"), "Gone");
        add(WorkbayLang.guiKey("status.short.target_missing"), "No target");
        add(WorkbayLang.guiKey("status.short.target_not_loaded"), "Unloaded");
        add(WorkbayLang.guiKey("status.short.target_no_port"), "No port");
        add(WorkbayLang.guiKey("status.short.machine_no_port"), "No machine");
        add(WorkbayLang.guiKey("status.short.machine_no_face"), "No face");
        add(WorkbayLang.guiKey("status.running"), "Running");
        add(WorkbayLang.guiKey("status.running.tip"), "This link moved something on its last turn.");
        add(WorkbayLang.guiKey("status.idle"), "Idle");
        add(WorkbayLang.guiKey("status.idle.tip"), "Nothing to move. This is the normal resting "
            + "state.");
        add(WorkbayLang.guiKey("status.disabled"), "Disabled");
        add(WorkbayLang.guiKey("status.disabled.tip"), "Turned off in this link's settings.");
        add(WorkbayLang.guiKey("status.connector_gone"), "Connector gone");
        add(WorkbayLang.guiKey("status.connector_gone.tip"), "The Connector this link hangs off has "
            + "been broken. The row clears itself shortly.");
        add(WorkbayLang.guiKey("status.target_missing"), "Target missing");
        add(WorkbayLang.guiKey("status.target_missing.tip"), "The dimension this link points into is "
            + "gone. Nothing will move until it comes back.");
        add(WorkbayLang.guiKey("status.target_not_loaded"), "Chunk not loaded");
        add(WorkbayLang.guiKey("status.target_not_loaded.tip"), "The target's chunk is unloaded. The "
            + "link resumes on its own when somebody goes back.");
        add(WorkbayLang.guiKey("status.target_no_port"), "No port");
        add(WorkbayLang.guiKey("status.target_no_port.tip"), "The target is there but has nothing "
            + "this link can connect to on any face.");
        add(WorkbayLang.guiKey("status.machine_no_port"), "Machine unreachable");
        add(WorkbayLang.guiKey("status.machine_no_port.tip"), "The hosted machine answers on none "
            + "of the faces this link may use. Many machines open a face only once you set it to "
            + "input or output in their own side configuration - enter the bay and check. Run "
            + "/workbay ports while looking at one to see exactly which faces answer.");
        add(WorkbayLang.guiKey("status.machine_no_face"), "No face for this");
        add(WorkbayLang.guiKey("status.machine_no_face.tip"), "This bay has faces set, but none "
            + "marked for this link's direction. Mark one in on the cube for a link that pulls, "
            + "or out for one that sends. Setting every face back to unset uses whichever answers.");

        add(WorkbayLang.guiKey("flow.empty"), "No machines racked yet.");
        add(WorkbayLang.guiKey("flow.unknown"), "not loaded");
        add(WorkbayLang.guiKey("flow.legend.out"), "crosses the world");
        add(WorkbayLang.guiKey("flow.legend.resting"), "resting");
        add(WorkbayLang.guiKey("flow.legend.broken"), "broken");
        add(WorkbayLang.guiKey("flow.legend.internal"), "stays inside");
        add(WorkbayLang.guiKey("flow.canvas"), "Flow map");
        add(WorkbayLang.guiKey("flow.canvas.tip"), "Every link this Workbay has, as the graph it "
            + "makes. Drag to move the view, scroll to zoom. Hover a box for what it is.");
        add(WorkbayLang.guiKey("flow.node.bay"), "Bay %s of this Workbay");
        add(WorkbayLang.guiKey("flow.node.at"), "%s %s %s");
        add(WorkbayLang.guiKey("flow.node.face"), "Face: %s");
        add(WorkbayLang.guiKey("flow.legend.stalled"), "stalled");
        add(WorkbayLang.guiKey("flow.legend.live"), "moving right now");

        // Both of these are drawn in a 96-pixel column, so both are written to fit one. The
        // sentence each is short for is the tooltip beside it.
        add(WorkbayLang.guiKey("upgrades.rate"), "Up to %s FE/t");
        add(WorkbayLang.guiKey("upgrades.deployed"), "Workbays placed: %s / %s");
        add(WorkbayLang.guiKey("upgrades.deployed.short"), "Placed: %s / %s");
        add(WorkbayLang.guiKey("upgrades.deployed.tip"), "How many Workbay blocks of this network "
            + "stand in the world. Every one of them opens onto the same bays, links and upgrades.");
        add(WorkbayLang.guiKey("upgrades.deployed.full"), "This network is at its limit. Placing "
            + "another Workbay is refused and the item stays in your hand — break this one "
            + "first, or raise maxDeployedWorkbaysPerNetwork in the server config.");
        add(WorkbayLang.guiKey("upgrades.levy"), "Levy: %s");
        add(WorkbayLang.guiKey("upgrades.cost"), "Costs %s Levy, taken from this Workbay's balance "
            + "when you install it. The next one costs more.");
        add(WorkbayLang.guiKey("upgrades.unaffordable"), "You have %s Levy and this one costs %s. "
            + "Raise the skim, or move more goods through your links.");
        add(WorkbayLang.guiKey("upgrades.add"), "Install one %s");
        add(WorkbayLang.guiKey("upgrades.maxed"), "You have as many of these as a Workbay takes.");
        // The three descriptions have 74 pixels each, beside a price that reaches "Levy: 102".
        // Anything longer is cut, and a cut description is worse than a short one - it reads as a
        // rendering fault and tells the player nothing. The sentence lives in the Add button's
        // tooltip, which is where this mod's text budget goes.
        add(WorkbayLang.guiKey("upgrade.expansion_plate"), "Expansion Plate");
        add(WorkbayLang.guiKey("upgrade.expansion_plate.desc"), "+1 bay");
        add(WorkbayLang.guiKey("upgrade.room_frame"), "Room Frame");
        add(WorkbayLang.guiKey("upgrade.room_frame.desc"), "14x14");
        add(WorkbayLang.guiKey("upgrade.wide_room_frame"), "Wide Room Frame");
        add(WorkbayLang.guiKey("upgrade.wide_room_frame.desc"), "30x30");
        add(WorkbayLang.guiKey("upgrade.vast_room_frame"), "Vast Room Frame");
        add(WorkbayLang.guiKey("upgrade.vast_room_frame.desc"), "46x46");
        add(WorkbayLang.guiKey("upgrade.annex_plate"), "Annex Plate");
        add(WorkbayLang.guiKey("upgrade.annex_plate.desc"), "+1 room");
        add(WorkbayLang.guiKey("upgrade.anchor"), "Anchor");
        add(WorkbayLang.guiKey("upgrade.anchor.desc"), "While empty");

        add(WorkbayLang.guiKey("button.rooms"), "Rooms");
        add(WorkbayLang.guiKey("button.rooms.tip"), "Somewhere to build, out the back. A room only "
            + "runs while somebody is standing in it, unless you anchor it.");
        add(WorkbayLang.guiKey("rooms.none"), "Install a Room Frame above for your first room.");
        add(WorkbayLang.guiKey("rooms.name"), "Room %s");
        add(WorkbayLang.guiKey("rooms.size"), "%sx%s, %s chunks");
        add(WorkbayLang.guiKey("rooms.size.one"), "%sx%s, %s chunk");
        add(WorkbayLang.guiKey("rooms.empty"), "Not opened yet");
        add(WorkbayLang.guiKey("rooms.enter"), "Enter");
        add(WorkbayLang.guiKey("rooms.open"), "Open");
        add(WorkbayLang.guiKey("rooms.enter.tip"), "Go and stand in it. Click the Exit block by the "
            + "door to come back to exactly where you are now.");
        add(WorkbayLang.guiKey("rooms.anchored"), "Anchored");
        add(WorkbayLang.guiKey("rooms.anchored.tip"), "This room keeps running while you are away, "
            + "and holds %s chunks loaded to do it. Click to switch it off.");
        add(WorkbayLang.guiKey("rooms.unanchored"), "Not anchored");
        add(WorkbayLang.guiKey("door.title"), "Way out");
        add(WorkbayLang.guiKey("door.elsewhere"), "Or go straight to");
        add(WorkbayLang.guiKey("door.leave"), "Leave");
        add(WorkbayLang.guiKey("door.leave.tip"), "Back to exactly where you were standing when "
            + "you came in.");
        add(WorkbayLang.guiKey("door.here"), "You are here");
        add(WorkbayLang.guiKey("door.go"), "Go");
        add(WorkbayLang.guiKey("door.go.tip"), "Straight into that room. You still come out where "
            + "you first came in.");
        add(WorkbayLang.guiKey("door.open"), "Open");
        add(WorkbayLang.guiKey("rooms.colour"), "Walls: %s");
        add(WorkbayLang.guiKey("rooms.colour.tip"), "What this room's shell is painted. Click for "
            + "the next colour, right-click for the previous one.");
        // The palette lives in RoomColour and these are its names. Ten strings rather than ten
        // textures is the whole point of tinting one greyscale wall.
        add(WorkbayLang.guiKey("colour.slate"), "Slate");
        add(WorkbayLang.guiKey("colour.charcoal"), "Charcoal");
        add(WorkbayLang.guiKey("colour.bone"), "Bone");
        add(WorkbayLang.guiKey("colour.sand"), "Sand");
        add(WorkbayLang.guiKey("colour.clay"), "Clay");
        add(WorkbayLang.guiKey("colour.rose"), "Rose");
        add(WorkbayLang.guiKey("colour.plum"), "Plum");
        add(WorkbayLang.guiKey("colour.sky"), "Sky");
        add(WorkbayLang.guiKey("colour.teal"), "Teal");
        add(WorkbayLang.guiKey("colour.sage"), "Sage");
        add(WorkbayLang.guiKey("colour.overworld"), "Overworld");
        add(WorkbayLang.guiKey("rooms.biome"), "Biome: %s");
        add(WorkbayLang.guiKey("rooms.biome.tip"), "What a machine in this room reads for "
            + "temperature and rainfall. Click to step to the next one. Nothing falls from the "
            + "sky in a room, because there is no sky: a cold room is cold, it does not snow.");
        add(WorkbayLang.guiKey("rooms.unanchored.tip"), "This room only runs while somebody is "
            + "inside it, and costs nothing while it is empty. Anchoring it holds %s chunks loaded.");
        add(WorkbayLang.messageKey("anchor_capped"), "This network may anchor %s room(s) at a time. "
            + "Switch another one off first.");
        add(WorkbayLang.guiKey("upgrade.resonator"), "Resonator");
        add(WorkbayLang.guiKey("upgrade.resonator.desc"), "Any dimension");
        add(WorkbayLang.guiKey("upgrade.multichannel"), "Multichannel");
        add(WorkbayLang.guiKey("upgrade.multichannel.desc"), "All 3 in one");
        // The Impeller shipped with no name at all: its row on the upgrades screen drew
        // "gui.workbay.upgr..." and "gui.workbay.u...", in red boxes, because the screen builds
        // this key from the enum and nobody added the row when the enum grew. Found by opening
        // the screen at a real window size, which nothing else in this mod can do for you.
        add(WorkbayLang.guiKey("upgrade.impeller"), "Impeller");
        add(WorkbayLang.guiKey("upgrade.impeller.desc"), "Faster links");

        // The skim. SPEC.md §3: goods going missing must be explained exactly where the loss is
        // noticed, so the rate is on the dial, on the bays screen and on every row it applies to.
        // The bays screen's Levy readout. On that screen and not only the upgrades one because a
        // player who has to change screens to find out whether they are earning does not change
        // screens - they conclude the dial does nothing.
        // Bay View. SPEC.md §5: our own screen, and a permanent line saying what it cannot reach.
        add(WorkbayLang.guiKey("bayview.title"), "Bay View · Bay %s");
        add(WorkbayLang.guiKey("bayview.limits"), "Recipe modes, side configuration and upgrade "
            + "slots stay on the machine. Eject it to change those.");
        // The gauges under the grid. The figures on the line have no unit - it does not fit - so
        // the tooltip is where "mB" and "FE" are actually said.
        add(WorkbayLang.guiKey("bayview.tank"), "%s / %s mB");
        add(WorkbayLang.guiKey("bayview.tank.empty"), "Empty tank");
        add(WorkbayLang.guiKey("bayview.tank.tip"), "Fill it from a container in the slot below, "
            + "or put an empty container there to draw from it.");
        // The fluid slots. The resting line is the label - two unnamed slots are a guess - and the
        // three others are the refusals. Only mixing names anything: whether a tank is full or
        // empty is already drawn, in a gauge, directly above them.
        add(WorkbayLang.guiKey("bayview.limits.short"), "Settings stay on the machine.");
        add(WorkbayLang.guiKey("bayview.chemical"), "%s / %s mB");
        add(WorkbayLang.guiKey("bayview.chemical.empty"), "Empty chemical tank");
        add(WorkbayLang.guiKey("bayview.chemical.tip"), "A Mekanism chemical. It has no bucket, so "
            + "this screen shows it and a link carries it; there is nothing to click here.");
        add(WorkbayLang.guiKey("bayview.slot.in"), "Input slot");
        add(WorkbayLang.guiKey("bayview.slot.fuel"), "Fuel slot");
        add(WorkbayLang.guiKey("bayview.slot.out"), "Output slot");
        add(WorkbayLang.guiKey("bayview.slot.fluid"), "Takes a bucket or tank, which is how this "
            + "machine's own fluid is filled by hand.");
        add(WorkbayLang.guiKey("bayview.slot.readonly"), "Read-only here. This machine only accepts "
            + "items through a link, or from inside the bay.");
        add(WorkbayLang.guiKey("bayview.exchange"), "Container in, result out.");
        add(WorkbayLang.guiKey("bayview.exchange.mixed"), "This tank holds %s.");
        add(WorkbayLang.guiKey("bayview.exchange.blocked"), "Take the result out first.");
        add(WorkbayLang.guiKey("bayview.exchange.refused"), "Nothing moves either way.");
        add(WorkbayLang.guiKey("bayview.exchange.wrong_way"), "Press the arrow - it is set the "
            + "other way.");
        add(WorkbayLang.guiKey("bayview.exchange.into"), "Filling the machine");
        add(WorkbayLang.guiKey("bayview.exchange.outof"), "Emptying the machine");
        add(WorkbayLang.guiKey("bayview.exchange.direction.tip"), "Which way the container in the "
            + "top slot carries fluid. Click to swap. The result always comes out of the bottom "
            + "slot.");
        add(WorkbayLang.guiKey("bayview.energy"), "Stored energy");
        add(WorkbayLang.guiKey("bayview.energy.tip"), "The machine's own buffer. The Workbay shares "
            + "power to it automatically - there is nothing to click here.");
        add(WorkbayLang.guiKey("button.bayview"), "Bay View");
        add(WorkbayLang.guiKey("button.bayview.tip"), "Opens this machine's item slots, tanks and "
            + "power so you can top it up by hand, without a link.");
        add(WorkbayLang.messageKey("bayview_unreachable"), "Nothing in this bay answers by hand. "
            + "A machine with no slots, tanks or power is reached by a link or not at all.");
        // Entering the bay. SPEC.md §5: the machine's own screen is reachable only by standing in
        // front of it, so this is the one control that answers "how do I upgrade the thing".
        add(WorkbayLang.guiKey("button.open"), "Open its screen");
        add(WorkbayLang.guiKey("button.open.tip"), "Opens the machine's own screen - recipe modes, "
            + "side configuration and upgrade slots, drawn by the mod that made it - without "
            + "leaving where you stand.");
        add(WorkbayLang.guiKey("button.enter"), "Enter bay");
        add(WorkbayLang.guiKey("button.enter.tip"), "Takes you into the bay, next to the machine, "
            + "and opens its own screen - recipe modes, side configuration and upgrade slots, "
            + "drawn by the mod that made it. Close it and you are back here.");
        add(WorkbayLang.messageKey("bay_enter_failed"), "That bay cannot be entered.");
        add(WorkbayLang.messageKey("bay_no_screen"), "%s has no screen of its own.");
        add(WorkbayLang.messageKey("bay_load_timeout"),
            "The bay did not reach your client in time. Try again.");
        add(WorkbayLang.guiKey("levy"), "Levy %s");
        add(WorkbayLang.guiKey("levy.name"), "Levy banked: %s");
        add(WorkbayLang.guiKey("levy.batch"), "%s / %s to the next");
        add(WorkbayLang.guiKey("levy.tip"), "The Assay is converting. Every batch of skimmed goods "
            + "becomes one Levy, and Levy is what installs upgrades.");
        add(WorkbayLang.guiKey("levy.no_assay"), "No Assay racked");
        add(WorkbayLang.guiKey("levy.no_assay.tip"), "Nothing is being earned. Rack an Assay in a "
            + "bay, then turn the skim dial up on the upgrades screen.");
        add(WorkbayLang.guiKey("levy.dial_off"), "Skim at 0%");
        add(WorkbayLang.guiKey("levy.dial_off.tip"), "Nothing is being earned. The Assay is racked "
            + "but the skim dial is at zero, so your links are keeping everything they carry.");
        add(WorkbayLang.guiKey("skim"), "Skim %s%%");
        add(WorkbayLang.guiKey("skim.name"), "Skim: %s%% of the goods your links carry");
        add(WorkbayLang.guiKey("skim.tip"), "Items your links move that count as refined goods are "
            + "taken at this rate and turned into Levy by the Assay. Click to raise it by five, "
            + "right-click to lower it. At zero the Workbay takes nothing.");
        // The number alone. The row has about thirty pixels for this and the sentence is sixty.
        add(WorkbayLang.guiKey("skim.row"), "%s%%");
        add(WorkbayLang.guiKey("skim.row.tip"), "This link hands the Assay that share of the "
            + "refined goods it carries, so less arrives at the far end than leaves.");
        add(WorkbayLang.guiKey("skim.no_assay"), "No Assay racked");
        add(WorkbayLang.guiKey("skim.no_assay.short"), "No Assay");
        add(WorkbayLang.guiKey("skim.no_assay.tip"), "Nothing is being skimmed: the rate only "
            + "applies while an Assay is racked in one of this Workbay's bays.");

        add(WorkbayLang.messageKey("pair_needs_connector"), "Hold a Connector to pair one.");
        add(WorkbayLang.messageKey("upgrade_maxed"), "This Workbay already has as many of those as "
            + "it takes.");
        add(WorkbayLang.messageKey("annex_needs_frame"), "An Annex Plate adds a room to a network "
            + "that already has one. Install a Room Frame first.");
        add(WorkbayLang.messageKey("upgrade_missing"), "You don't have one of those to install.");
        add(WorkbayLang.messageKey("upgrade_needs_levy"), "That costs %s Levy and this Workbay has "
            + "%s. Raise the skim, or move more goods through your links.");

        // Tooltips. SPEC.md §6: at most four lines unshifted.
        add(WorkbayLang.tooltipKey("hosting"), "Hosting: %s / %s machines");
        add(WorkbayLang.tooltipKey("buses"), "Buses: %s configured");
        add(WorkbayLang.tooltipKey("code"), "Code %s");
        add(WorkbayLang.tooltipKey("connector_unpaired"), "Not paired. Right-click a Workbay with "
            + "this to pair it.");
        add(WorkbayLang.tooltipKey("connector_paired"), "Paired \u00b7 bay %s");
    }
}
