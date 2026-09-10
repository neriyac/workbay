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
        addItem(WBItems.EXPANSION_PLATE, "Expansion Plate");
        addItem(WBItems.ROOM_FRAME, "Room Frame");
        addItem(WBItems.WIDE_ROOM_FRAME, "Wide Room Frame");
        addItem(WBItems.VAST_ROOM_FRAME, "Vast Room Frame");
        addItem(WBItems.ANNEX_PLATE, "Annex Plate");
        addItem(WBItems.ANCHOR, "Anchor");
        addItem(WBItems.RESONATOR, "Resonator");
        addItem(WBItems.IMPELLER, "Impeller");

        // Chat, one-shot. SPEC.md §6 and §14's network model: a Workbay belongs to the player who
        // placed it, not to the specific item, so there is no code to lose.
        add(WorkbayLang.messageKey("room_created"), "Workbay network created and bound to your "
            + "account. Lose the block and a fresh, uncrafted Workbay picks it straight back up.");
        add(WorkbayLang.messageKey("network_created"), "%s created. Everything in it is yours; "
            + "break the block and it sleeps here until you put one back.");
        add(WorkbayLang.messageKey("network_reused"), "Workbay reconnected \u2014 same bays, same "
            + "links, same upgrades as before.");
        add(WorkbayLang.messageKey("network_cap_reached"), "You already own the maximum of %s "
            + "Workbay network(s). Break one before starting another.");
        // <b>Not a refusal.</b> The block is standing where it was put; it simply holds nothing
        // yet, and the message says where to go and settle that.
        add(WorkbayLang.messageKey("network_quota"), "This Workbay holds no network yet \u2014 "
            + "every one you own already has a block. Open it to move one here.");
        add(WorkbayLang.messageKey("network_transferred"), "%s moved here. The block it came from "
            + "is standing empty.");
        add(WorkbayLang.messageKey("break_warning"), "Breaking this Workbay leaves its bays behind. "
            + "The machines keep running \u2014 place any fresh Workbay to get back in.");
        add(WorkbayLang.messageKey("locked"), "This Workbay is locked.");

        // Rooms have an owner, and a guest list per room. SPEC.md 8. Each of these says who
        // decides, because the answer is never "the server" and never "wait a bit".
        add(WorkbayLang.messageKey("room_not_yours"), "That room isn't yours. Ask its owner to "
            + "invite you from the room's own settings.");
        add(WorkbayLang.messageKey("room_use_only"), "You were invited to work this room, not to "
            + "change it. Open what is here as you like; its owner can raise that to May build.");
        add(WorkbayLang.messageKey("room_look_only"), "You were invited to look at this room, not "
            + "to change it. Its owner can raise that to Build.");
        add(WorkbayLang.messageKey("guest_unknown"), "This server has never seen a player called "
            + "%s. They have to have logged in once before they can be invited.");
        add(WorkbayLang.messageKey("guest_is_owner"), "That is you. The room is already yours.");

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

        // The Connector, which is where every link comes from. SPEC.md §0.
        // <b>Paired to the network, and to no bay at all.</b> A Connector may carry a channel
        // on every bay of its Workbay at once, so nothing may say it is "on" one -- and pairing
        // no longer picks one, because placing it no longer mints anything.
        add(WorkbayLang.messageKey("connector_paired"), "Connector paired to %s. Place it, then "
            + "add it to a bay.");
        add(WorkbayLang.messageKey("connector_unpaired"), "This Connector isn't paired yet. "
            + "Right-click a Workbay with it first.");
        add(WorkbayLang.messageKey("connector_linked"), "Connector set on %s, for %s. Add it on a "
            + "bay to give that bay a channel.");
        add(WorkbayLang.messageKey("connector_no_workbay"), "The Workbay this Connector is paired "
            + "to isn't loaded. Go back to it and place this again.");


        // ---- The screens. SPEC.md §4 and §6: icons buy back the horizontal room the link rows
        // need, so every icon button pays for itself with a tooltip written as a full sentence.
        add(WorkbayLang.guiKey("title"), "Workbay");
        add(WorkbayLang.guiKey("unbuilt"), "Not built yet.");
        add(WorkbayLang.guiKey("power"), "%s / %s FE");
        add(WorkbayLang.guiKey("power.tip"), "Energy stored in the Workbay itself. Hosted machines keep their own.");
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
        add(WorkbayLang.guiKey("button.upgrades.tip"), "Install Expansion Plates, a Resonator or "
            + "an Impeller.");
        add(WorkbayLang.guiKey("button.networks"), "Networks");
        add(WorkbayLang.guiKey("button.networks.tip"), "Which network this Workbay is, and every "
            + "other one you own. Move one here, or rename it.");
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
        add(WorkbayLang.guiKey("links.target.tip"), "What this link is pointed at.");
        add(WorkbayLang.guiKey("links.rename.tip"), "Right-click to name this Connector. The "
            + "name is the block's, so every channel through it reads the same. Empty goes back "
            + "to the derived name.");
        add(WorkbayLang.guiKey("button.rename"), "Rename");
        add(WorkbayLang.guiKey("button.rename.tip"), "Name this bay. Return commits, Escape cancels, empty goes back to the machine's.");
        add(WorkbayLang.guiKey("redstone.always"), "Redstone: always");
        add(WorkbayLang.guiKey("redstone.always.tip"), "These links run whatever the redstone is doing. Right-click steps back.");
        add(WorkbayLang.guiKey("redstone.with_signal"), "Redstone: with a signal");
        add(WorkbayLang.guiKey("redstone.with_signal.tip"), "This bay's links run only while the "
            + "Workbay has a redstone signal.");
        add(WorkbayLang.guiKey("redstone.without_signal"), "Redstone: without a signal");
        add(WorkbayLang.guiKey("redstone.without_signal.tip"), "This bay's links run only while the "
            + "Workbay has no redstone signal.");
        add(WorkbayLang.guiKey("redstone.pulse"), "Redstone: pulse");
        add(WorkbayLang.guiKey("redstone.pulse.tip"), "One operation each time the signal turns on.");
        // The forms that share a 130-pixel line with the bay's status.
        add(WorkbayLang.guiKey("redstone.short.always"), "");
        add(WorkbayLang.guiKey("redstone.short.with_signal"), "with signal");
        add(WorkbayLang.guiKey("redstone.short.without_signal"), "no signal");
        add(WorkbayLang.guiKey("redstone.short.pulse"), "pulse");
        add(WorkbayLang.guiKey("status.held_by_redstone"), "Held");
        add(WorkbayLang.guiKey("status.held_by_redstone.tip"), "Waiting on this bay's redstone mode. Nothing is wrong.");
        add(WorkbayLang.guiKey("button.copy"), "Copy this bay");
        add(WorkbayLang.guiKey("button.copy.tip"), "Copies this bay's face settings.");
        add(WorkbayLang.guiKey("button.paste"), "Paste onto this bay");
        add(WorkbayLang.guiKey("button.paste.tip"), "Replaces this bay's face settings with the copied ones.");
        add(WorkbayLang.guiKey("button.paste.empty"), "Copy a bay first.");

        add(WorkbayLang.guiKey("bay.n"), "Bay %s");
        add(WorkbayLang.guiKey("bay.here"), "Bay %s · %s · Workbay %s");
        add(WorkbayLang.guiKey("bay.empty"), "Empty bay");
        add(WorkbayLang.guiKey("bay.empty.tip"), "Hold a machine and click here to rack it.");
        add(WorkbayLang.guiKey("bay.rack.tip"), "Hold a machine and click to rack it in this bay.");
        // The same instruction, drawn rather than hovered: a fresh Workbay is an empty rack and
        // the tooltip was the only place that said what to do about it. BaysPage#machine.
        add(WorkbayLang.guiKey("bay.rack.hint"), "Hold a machine and click the slot.");
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
        // The fourth one, missing since the faces cycler grew a chemical tab: the tooltip's title
        // read `gui.workbay.faces.chemical` on any install with Mekanism. Exactly the fault
        // check-lang.py was written for, on a prefix it was not watching -- so the prefix is in
        // BUILT now and this cannot be the third time.
        add(WorkbayLang.guiKey("faces.chemical"), "Chemical faces");
        add(WorkbayLang.guiKey("faces.tip"), "Click a face to cycle it in, out, off. Right-click steps back.");
        add(WorkbayLang.guiKey("faces.face"), "Face: %s");
        add(WorkbayLang.guiKey("faces.role.none"), "Unset. With no face set, links use whichever "
            + "face answers.");
        add(WorkbayLang.guiKey("faces.role.input"), "Input. Links may push into this face.");
        add(WorkbayLang.guiKey("faces.role.output"), "Output. Links may pull out of this face.");
        add(WorkbayLang.guiKey("faces.drag"), "Drag to turn");
        add(WorkbayLang.guiKey("faces.empty"), "Rack a machine to set its faces.");

        add(WorkbayLang.guiKey("links.none"), "No channels on this bay yet. Press Add and pick a "
            + "Connector.");
        add(WorkbayLang.guiKey("links.pair"), "Pair a Connector");
        add(WorkbayLang.guiKey("links.pair.tip"), "Pairs the held Connector to this Workbay. Place "
            + "it on the block you want to reach, then add it to whichever bays should talk "
            + "through it.");
        add(WorkbayLang.guiKey("links.internal"), "Link a bay");
        add(WorkbayLang.guiKey("links.internal.tip"), "A link straight to another bay, with no Connector to place.");
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
        // The fourth resource only exists when Mekanism is installed, which is why this line was
        // missing and the tooltip's title read `gui.workbay.links.type.chemical` in bold.
        // tools/check-lang.sh now walks every resource rather than the three anybody sees.
        add(WorkbayLang.guiKey("links.type.chemical"), "Carries chemicals");
        add(WorkbayLang.guiKey("links.type.tip"), "Click to change what this link carries. Right-click steps back.");
        add(WorkbayLang.guiKey("links.none.here"), "No links on this bay. %s on other bays \u2014 press Add to move one here.");
        add(WorkbayLang.guiKey("links.adding"), "TO BAY %s");
        add(WorkbayLang.guiKey("links.add"), "Add a channel");
        add(WorkbayLang.guiKey("links.add.close"), "Back to this bay's links");
        add(WorkbayLang.guiKey("links.add.tip"), "Everything this bay can talk through: a "
            + "Connector standing in the world, or another bay.");
        add(WorkbayLang.guiKey("links.add.none.links"), "No Connectors yet. Pair one and place it "
            + "against a chest, tank or machine.");
        add(WorkbayLang.guiKey("links.add.none.bays"), "No other bays. Rack a second one first.");
        add(WorkbayLang.guiKey("links.add.tab.connectors"), "Connectors in the world");
        add(WorkbayLang.guiKey("links.add.tab.bays"), "Bays in this Workbay");
        add(WorkbayLang.guiKey("links.add.tab.tip"), "Every Connector this network owns, or the "
            + "bays in this rack.");
        add(WorkbayLang.guiKey("links.add.apply"), "Add %s ticked");
        add(WorkbayLang.guiKey("links.add.apply.tip"), "Adds everything ticked on both tabs in "
            + "one go, then closes the picker.");
        add(WorkbayLang.guiKey("links.add.connector"), "New channel through %s");
        add(WorkbayLang.guiKey("links.add.connector.tip"), "Gives bay %s its own channel through "
            + "this Connector — its own resource, direction, filter and rate. Every other bay "
            + "keeps what it has; one Connector can serve all of them at once.");
        add(WorkbayLang.guiKey("links.add.connector.back"), "Give %s its channel back");
        add(WorkbayLang.guiKey("links.add.connector.back.tip"), "A channel through this Connector "
            + "is waiting off its bay. This puts it on bay %s with its resource, filter and rate "
            + "kept, instead of adding a blank one beside it.");
        add(WorkbayLang.guiKey("links.add.bay"), "Bay %s");
        add(WorkbayLang.guiKey("links.add.bay.tip"), "Makes a link straight to that bay. No "
            + "Connector, no block in the world.");
        add(WorkbayLang.guiKey("links.add.nowire"), "no wire");
        // <b>The same six words the face cube uses</b>, not the compass's. One screen naming the
        // same six directions two ways is one of them being wrong; BlockPreview owns the mapping.
        add(WorkbayLang.guiKey("links.face.any"), "Any face");
        add(WorkbayLang.guiKey("links.face.bottom"), "Bottom face");
        add(WorkbayLang.guiKey("links.face.top"), "Top face");
        add(WorkbayLang.guiKey("links.face.front"), "Front face");
        add(WorkbayLang.guiKey("links.face.back"), "Back face");
        add(WorkbayLang.guiKey("links.face.left"), "Left face");
        add(WorkbayLang.guiKey("links.face.right"), "Right face");

        // The two ends of a row's arrow, which are places now and not resources.
        add(WorkbayLang.guiKey("links.from"), "From");
        add(WorkbayLang.guiKey("links.into"), "Into");
        add(WorkbayLang.guiKey("links.end.bay"), "Bay %s · %s");
        add(WorkbayLang.guiKey("links.end.emptybay"), "This bay has nothing racked in it, so this "
            + "end of the link reaches nothing.");
        add(WorkbayLang.guiKey("links.end.unknown"), "Nothing is remembered about the block at "
            + "this end yet.");
        add(WorkbayLang.guiKey("links.face.tip"), "Which side of the target to reach into. Pin one when in and out are on "
            + "different faces; right-click steps back.");
        add(WorkbayLang.guiKey("links.mode.insert"), "Sends into the target");
        add(WorkbayLang.guiKey("links.mode.extract"), "Pulls out of the target");
        add(WorkbayLang.guiKey("links.mode.tip"), "Click to turn this link around.");
        add(WorkbayLang.guiKey("links.remove"), "Take off this bay");
        add(WorkbayLang.guiKey("links.remove.tip"), "The link keeps its filter, rate and name and "
            + "waits under Add. Breaking the Connector is what deletes one.");

        add(WorkbayLang.messageKey("workbay_stamped"), "This Workbay will join network %s.");
        add(WorkbayLang.messageKey("network_reused_named"), "Joined network %s.");
        add(WorkbayLang.messageKey("room_occupied"), "Somebody is in that room.");
        add(WorkbayLang.messageKey("room_not_empty"), "There is a %s in that room. A room is only "
            + "given back once it is empty - nothing you built is thrown away here.");
        add(WorkbayLang.messageKey("room_removed"), "Room %s handed back.");
        add(WorkbayLang.guiKey("rooms.remove"), "Hand this room back");
        add(WorkbayLang.guiKey("rooms.remove.tip"), "Takes the shell down and frees the slot. Only "
            + "an empty room, and it asks again before it does it.");
        add(WorkbayLang.guiKey("rooms.remove.sure"), "Hand it back?");
        add(WorkbayLang.guiKey("rooms.remove.sure.tip"), "Click again to take the room down. "
            + "Anything else on this screen calls it off.");
        add(WorkbayLang.guiKey("status.no_power"), "No power");
        add(WorkbayLang.guiKey("status.short.no_power"), "No power");
        add(WorkbayLang.guiKey("status.no_power.tip"), "The Workbay's buffer is empty. Running "
            + "links costs power; feed the block and they start again where they left off.");
        add(WorkbayLang.guiKey("status.detached"), "Not on a bay");
        add(WorkbayLang.guiKey("status.short.detached"), "No bay");
        add(WorkbayLang.guiKey("status.detached.tip"), "Taken off its bay and kept. Put it on one "
            + "with Add.");
        add(WorkbayLang.guiKey("links.enable"), "Off");
        add(WorkbayLang.guiKey("links.enable.tip"), "A new link starts off. Click to turn it on.");
        add(WorkbayLang.guiKey("links.disable"), "On");
        add(WorkbayLang.guiKey("links.disable.tip"), "Click to stop this link without deleting it.");
        add(WorkbayLang.guiKey("links.problems"), "%s needing attention");
        add(WorkbayLang.guiKey("links.problems.tip"), "Show only the links in trouble.");
        add(WorkbayLang.guiKey("links.bay"), "Bay %s");
        add(WorkbayLang.guiKey("links.bay.tip"), "Which bay this link belongs to.");
        // The filter. SPEC.md §5: it matches on what a thing is and nothing else, so every string
        // here names an item or a fluid and none of them mentions a mode, a tag or a count.
        add(WorkbayLang.guiKey("filter.none"), "No filter");
        add(WorkbayLang.guiKey("filter.some.allow"), "Whitelist, %s listed");
        add(WorkbayLang.guiKey("filter.some.deny"), "Blacklist, %s listed");
        add(WorkbayLang.guiKey("filter.tip"), "What this link may carry. Empty carries everything.");
        add(WorkbayLang.guiKey("filter.energy"), "Energy has nothing to filter");
        add(WorkbayLang.guiKey("filter.energy.tip"), "Energy is a number, so there is nothing here to name.");
        // "Whitelist" and "Blacklist", not "Only these" and "All but these". The genre settled
        // this vocabulary years ago -- XNet, EnderIO and LaserIO all use it -- and a player who
        // already knows the words does not have to read a button to find out it is the same idea
        // under a nicer name. The sentence explaining which is which lives in the tooltip.
        add(WorkbayLang.guiKey("filter.allow"), "Whitelist");
        add(WorkbayLang.guiKey("filter.deny"), "Blacklist");
        add(WorkbayLang.guiKey("filter.mode.tip"), "Whitelist carries only what is listed; blacklist carries the rest.");
        add(WorkbayLang.guiKey("filter.inventory"), "Your inventory");
        add(WorkbayLang.guiKey("filter.inventory.tip"), "Click to put a copy on the cursor, then "
            + "click a filter slot to list it. Nothing leaves your inventory - a filter names an "
            + "item, it does not hold one.");
        add(WorkbayLang.guiKey("filter.inventory.putback.tip"), "Click to put down what is on the "
            + "cursor.");
        add(WorkbayLang.guiKey("filter.slot"), "Empty");
        add(WorkbayLang.guiKey("filter.slot.tip.item"), "Click to drop what you are carrying here. "
            + "With empty hands it lists what you are holding, and a recipe-list drag works too.");
        add(WorkbayLang.guiKey("filter.slot.tip.fluid"), "Click to drop what you are carrying here. "
            + "A bucket or tank lists the fluid inside it; a recipe-list drag works too.");
        add(WorkbayLang.guiKey("filter.entry.tip"), "Click to lift it off. Right-click to drop it.");
        add(WorkbayLang.guiKey("filter.carrying"), "Click a slot to list it, anywhere else to drop it.");
        add(WorkbayLang.guiKey("filter.listed.allow"), "Only what is listed goes through.");
        add(WorkbayLang.guiKey("filter.listed.deny"), "Everything except what is listed goes through.");
        add(WorkbayLang.guiKey("filter.empty"), "Nothing listed. This link carries everything.");
        add(WorkbayLang.guiKey("filter.entry.tag.tip"), "Everything in this tag, not just this "
            + "one thing. Shift-click for the next tag, or round to the item itself.");
        add(WorkbayLang.guiKey("filter.chemical"), "List what is in the tank");
        add(WorkbayLang.guiKey("filter.chemical.tip"), "A chemical has no item to drag in, so a "
            + "chemical filter is named from the tank it is already in. Click again to carry "
            + "everything.");
        add(WorkbayLang.guiKey("filter.chemical.none"), "Carrying every chemical this tank offers.");
        add(WorkbayLang.guiKey("filter.chemical.faces"), "Faces are Mekanism's side config.");
        add(WorkbayLang.guiKey("filter.close"), "Back to the links");
        add(WorkbayLang.guiKey("filter.close.tip"), "Saved as you change it.");
        add(WorkbayLang.guiKey("links.unknown"), "not loaded");
        // SPEC.md 5's link settings: what one link moves in a step, and how long it waits.
        add(WorkbayLang.guiKey("links.rate"), "Rate");
        add(WorkbayLang.guiKey("links.rate.tip"), "How much this link moves in one step. Hold "
            + "shift for ten at a time, ctrl for a hundred.");
        add(WorkbayLang.guiKey("links.speed"), "Speed");
        add(WorkbayLang.guiKey("links.speed.ticks"), "%st");
        add(WorkbayLang.guiKey("links.speed.tip"), "Ticks between steps. Plus is faster; the "
            + "list is fixed so every speed divides the wheel.");
        // What a link into a room is called, on the LINKS row and on the flow map's node. The
        // block first, because the room is the thing that repeats down a chain and the block is
        // the thing that tells one stage from the next.
        add(WorkbayLang.guiKey("links.in_room"), "%s in %s");

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
        add(WorkbayLang.guiKey("status.short.needs_resonator"), "Off-world");
        add(WorkbayLang.guiKey("status.running"), "Running");
        add(WorkbayLang.guiKey("status.running.tip"), "This link moved something on its last turn.");
        add(WorkbayLang.guiKey("status.idle"), "Idle");
        add(WorkbayLang.guiKey("status.idle.tip"), "Nothing to move. This is the normal resting "
            + "state.");
        add(WorkbayLang.guiKey("status.disabled"), "Disabled");
        add(WorkbayLang.guiKey("status.disabled.tip"), "Turned off in this link's settings.");
        add(WorkbayLang.guiKey("status.connector_gone"), "Connector gone");
        add(WorkbayLang.guiKey("status.connector_gone.tip"), "Its Connector was broken. The row clears itself shortly.");
        add(WorkbayLang.guiKey("status.target_missing"), "Target missing");
        add(WorkbayLang.guiKey("status.target_missing.tip"), "The dimension this link points into is gone.");
        add(WorkbayLang.guiKey("status.target_not_loaded"), "Chunk not loaded");
        add(WorkbayLang.guiKey("status.target_not_loaded.tip"), "The target's chunk is unloaded. It resumes on its own.");
        add(WorkbayLang.guiKey("status.target_no_port"), "No port");
        add(WorkbayLang.guiKey("status.target_no_port.tip"), "The target is there but has nothing "
            + "this link can connect to on any face.");
        add(WorkbayLang.guiKey("status.machine_no_port"), "Machine unreachable");
        add(WorkbayLang.guiKey("status.machine_no_port.tip"), "The machine answers on none of the faces this link may use. "
            + "Most open a face only once you set it in their own side config.");
        add(WorkbayLang.guiKey("status.machine_no_face"), "No face for this");
        add(WorkbayLang.guiKey("status.needs_resonator"), "Needs a Resonator");
        add(WorkbayLang.guiKey("status.needs_resonator.tip"), "This link crosses into another dimension. Install a Resonator to let it.");
        add(WorkbayLang.guiKey("status.machine_no_face.tip"), "This bay has faces set, but none for this link's direction. "
            + "Mark one in to pull, out to send.");

        add(WorkbayLang.guiKey("flow.empty"), "No machines racked yet.");
        add(WorkbayLang.guiKey("flow.unknown"), "not loaded");
        add(WorkbayLang.guiKey("flow.legend.out"), "crosses the world");
        add(WorkbayLang.guiKey("flow.legend.resting"), "resting");
        add(WorkbayLang.guiKey("flow.legend.broken"), "broken");
        add(WorkbayLang.guiKey("flow.legend.internal"), "stays inside");
        add(WorkbayLang.guiKey("flow.canvas"), "Flow map");
        add(WorkbayLang.guiKey("flow.canvas.tip"), "Every link this Workbay has, as the graph it "
            + "makes. Drag to move the view, scroll to zoom. Hover a box for what it is.");
        add(WorkbayLang.guiKey("flow.drive"), "Drag to move · scroll to zoom");
        add(WorkbayLang.guiKey("flow.node.bay"), "Bay %s of this Workbay");
        add(WorkbayLang.guiKey("flow.node.at"), "%s %s %s");
        add(WorkbayLang.guiKey("flow.node.face"), "Face: %s");
        add(WorkbayLang.guiKey("flow.legend.stalled"), "stalled");
        add(WorkbayLang.guiKey("flow.legend.live"), "moving right now");

        // Both of these are drawn in a 96-pixel column, so both are written to fit one. The
        // sentence each is short for is the tooltip beside it.
        add(WorkbayLang.guiKey("upgrades.rate"), "Up to %s FE/t");
        // ---------------------------------------------------------------- NETWORKS
        //
        // One Workbay block is one network. Everything here is written for a player who has just
        // placed a second or a third Workbay and wants to know what happened, so nothing on this
        // page uses the word "refused" and nothing prints a network code.
        add(WorkbayLang.guiKey("networks.quota"), "Workbay quota reached");
        // The other reason a Workbay holds nothing: its network was moved into another block. Not
        // amber and not called a quota - the player is under the limit and this is one click from
        // being a working Workbay.
        add(WorkbayLang.guiKey("networks.empty"), "This Workbay holds no network");
        add(WorkbayLang.guiKey("networks.empty.tip"), "Start one here, or move one of yours in.");
        add(WorkbayLang.guiKey("networks.new"), "New network");
        add(WorkbayLang.guiKey("networks.new.tip"), "Makes a fresh network in this Workbay: two "
            + "empty bays, no Connectors, nothing racked. It counts against the number of networks "
            + "you may own.");
        add(WorkbayLang.guiKey("networks.quota.tip"), "Every network you own already has a block. "
            + "Pick one and press Transfer to move it here.");
        add(WorkbayLang.guiKey("networks.none"), "You do not own a network yet.");
        add(WorkbayLang.guiKey("networks.footer"), "%s of %s networks");
        add(WorkbayLang.guiKey("networks.footer.tip"), "One Workbay block is one network, and this "
            + "server lets you own this many. Placing more Workbays is never refused: one placed "
            + "with no network left to take stands there empty until you move a network into it. "
            + "A network with no block sleeps and keeps everything - bays, machines, Connectors "
            + "and channels - until a block is pointed at it again.");
        add(WorkbayLang.guiKey("networks.here"), "This one");
        add(WorkbayLang.guiKey("networks.live"), "Placed");
        add(WorkbayLang.guiKey("networks.asleep"), "Asleep");
        add(WorkbayLang.guiKey("networks.nowhere"), "No block placed");
        // The number after the word: a lang file has no plural forms, and "1 bays" was on the
        // screen. Photographed.
        add(WorkbayLang.guiKey("networks.holding"), "Bays %s \u00b7 Connectors %s");
        add(WorkbayLang.guiKey("networks.rename"), "Rename this network");
        add(WorkbayLang.guiKey("networks.rename.tip"), "One network, one name \u2014 it changes "
            + "everywhere this network is named at once.");
        add(WorkbayLang.guiKey("networks.transfer"), "Transfer here");
        add(WorkbayLang.guiKey("networks.transfer.asleep.tip"), "Moves %s into this Workbay whole: "
            + "its bays, the machines in them, its Connectors, its channels and its upgrades.");
        add(WorkbayLang.guiKey("networks.transfer.live.tip"), "Moves %s into this Workbay whole. "
            + "The block it is in now is left standing and empty; nothing in the network is lost.");
        add(WorkbayLang.guiKey("networks.transfer.here.tip"), "This Workbay is already this "
            + "network.");

        add(WorkbayLang.guiKey("upgrades.add"), "Install one %s");
        add(WorkbayLang.guiKey("upgrades.maxed"), "You have as many of these as a Workbay takes.");
        // The three descriptions have 74 pixels each.
        // Anything longer is cut, and a cut description is worse than a short one - it reads as a
        // rendering fault and tells the player nothing. The sentence lives in the Add button's
        // tooltip, which is where this mod's text budget goes.
        add(WorkbayLang.guiKey("upgrade.expansion_plate"), "Expansion Plate");
        add(WorkbayLang.guiKey("upgrade.expansion_plate.desc"), "+1 bay");
        add(WorkbayLang.guiKey("upgrade.expansion_plate.long"),
            "One more bay on this rack. A machine lives in a bay, so this is how many "
            + "machines one Workbay can hold at once.");
        add(WorkbayLang.guiKey("upgrade.room_frame"), "Room Frame");
        add(WorkbayLang.guiKey("upgrade.room_frame.desc"), "14x14");
        add(WorkbayLang.guiKey("upgrade.room_frame.long"),
            "Buys your first room, and sets the size of every room this network owns. "
            + "Installing a bigger Frame later grows the room you already have where it "
            + "stands: nothing is moved and nothing inside it is lost.");
        add(WorkbayLang.guiKey("upgrade.wide_room_frame"), "Wide Room Frame");
        add(WorkbayLang.guiKey("upgrade.wide_room_frame.desc"), "30x30");
        add(WorkbayLang.guiKey("upgrade.wide_room_frame.long"),
            "Grows every room this network owns to 30 blocks across, in place. What is "
            + "built inside stays exactly where it is; the walls move outwards around it.");
        add(WorkbayLang.guiKey("upgrade.vast_room_frame"), "Vast Room Frame");
        add(WorkbayLang.guiKey("upgrade.vast_room_frame.desc"), "46x46");
        add(WorkbayLang.guiKey("upgrade.vast_room_frame.long"),
            "Grows every room this network owns to 46 blocks across, in place. What is "
            + "built inside stays exactly where it is; the walls move outwards around it.");
        add(WorkbayLang.guiKey("upgrade.annex_plate"), "Annex Plate");
        add(WorkbayLang.guiKey("upgrade.annex_plate.desc"), "+1 room");
        add(WorkbayLang.guiKey("upgrade.annex_plate.long"),
            "One more room, at whatever size your Room Frame sets. It does not change the "
            + "size of the rooms you have.");
        add(WorkbayLang.guiKey("upgrade.anchor"), "Anchor");
        add(WorkbayLang.guiKey("upgrade.anchor.desc"), "While away");
        add(WorkbayLang.guiKey("upgrade.anchor.long"),
            "Keeps the Backshop running with nobody there. It holds this Workbay's own "
            + "chunk and its bay column, which is what lets its links keep moving goods after "
            + "you log out, and it lets a room be switched on below to hold its own chunks "
            + "too.");

        add(WorkbayLang.guiKey("button.rooms"), "Rooms");
        add(WorkbayLang.guiKey("button.rooms.tip"), "Somewhere to build, out the back. A Connector works in a room, so one can be a "
            + "stage in a chain rather than only a place to stand.");
        add(WorkbayLang.guiKey("rooms.none"), "Install a Room Frame above for your first room.");
        add(WorkbayLang.guiKey("rooms.name"), "Room %s");
        add(WorkbayLang.guiKey("rooms.rename.tip"),
            "Right-click to name this room. Empty goes back to Room 1, Room 2.");
        add(WorkbayLang.guiKey("rooms.rename.unbuilt"), "Open the room first.");

        // The room window's two tabs, and everything on the guests one.
        add(WorkbayLang.guiKey("rooms.tab.room"), "Room");
        add(WorkbayLang.guiKey("rooms.tab.room.tip"), "What this room looks like: its colour and "
            + "its biome.");
        add(WorkbayLang.guiKey("rooms.tab.guests"), "Guests");
        add(WorkbayLang.guiKey("rooms.tab.guests.tip"), "Who else may be in this room.");
        add(WorkbayLang.guiKey("rooms.guests.label"), "Guests");
        add(WorkbayLang.guiKey("rooms.guests.tip"), "To this room only \u2014 never to your others, and never to the dimension.");
        add(WorkbayLang.guiKey("rooms.guests.name"), "player name");
        add(WorkbayLang.guiKey("rooms.guests.none"), "Nobody but you.");
        add(WorkbayLang.guiKey("rooms.guests.invite"), "Invite");
        add(WorkbayLang.guiKey("rooms.guests.invite.tip"), "Invites them to look. Raise to May work if they are here to run the place, or to May build if they are here to change it.");
        add(WorkbayLang.guiKey("rooms.guests.remove"), "Remove %s");
        add(WorkbayLang.guiKey("rooms.guests.remove.tip"), "They are put out of the room at once, "
            + "even if they are standing in it.");
        add(WorkbayLang.guiKey("rooms.guest.look"), "Look only");
        add(WorkbayLang.guiKey("rooms.guest.look.tip"), "May stand here. No blocks broken or "
            + "placed, and no containers opened. Click to raise to May work.");
        add(WorkbayLang.guiKey("rooms.guest.use"), "May work");
        add(WorkbayLang.guiKey("rooms.guest.use.tip"), "May open what is here and take from it, "
            + "and may break or place nothing. Click to raise to May build.");
        add(WorkbayLang.guiKey("rooms.guest.build"), "May build");
        add(WorkbayLang.guiKey("rooms.guest.build.tip"), "May do anything in this room that you "
            + "can. Click to drop back to Look only.");
        add(WorkbayLang.guiKey("rooms.size"), "%sx%s, %s chunks");
        add(WorkbayLang.guiKey("rooms.size.one"), "%sx%s, %s chunk");
        add(WorkbayLang.guiKey("rooms.empty"), "Not opened yet");
        add(WorkbayLang.guiKey("rooms.enter"), "Enter");
        add(WorkbayLang.guiKey("rooms.open"), "Open");
        // The Exit block is gone; the way out is the door in the middle of each of the four walls.
        // This string still said to find an Exit block, which a player would have hunted a
        // 46-block room for.
        add(WorkbayLang.guiKey("rooms.enter.tip"), "Go and stand in it. A door in the middle of each wall brings you back \u2014 and "
            + "a Connector works in here, so a barrel in a room is a stage in a chain.");
        add(WorkbayLang.guiKey("rooms.anchored"), "Anchored");
        add(WorkbayLang.guiKey("rooms.anchored.tip"), "Keeps what is inside this room running with nobody in it, holding %s "
            + "chunks loaded. Click to switch off.");
        add(WorkbayLang.guiKey("rooms.unanchored"), "Not anchored");
        // The Connector's rename panel. One field, and a line saying what it is called when the
        // field is left empty -- which is the block's own coordinates, not a stored default.
        add(WorkbayLang.guiKey("connector.title"), "Name this Connector");
        add(WorkbayLang.guiKey("connector.hint"), "Empty leaves it \"%s\"");
        add(WorkbayLang.guiKey("connector.unused"), "No channels yet · open a bay, press Add "
            + "and pick this");
        add(WorkbayLang.guiKey("connector.channel"), "Carries one channel");
        add(WorkbayLang.guiKey("connector.channels"), "Carries %s channels, across its bays");
        add(WorkbayLang.guiKey("connector.save"), "Save");

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
        add(WorkbayLang.guiKey("rooms.settings"), "Room settings");
        add(WorkbayLang.guiKey("rooms.settings.close"), "Done");
        add(WorkbayLang.guiKey("rooms.colour.label"), "Walls");
        add(WorkbayLang.guiKey("rooms.biome.label"), "Behaves like");
        // A tag a pack may fill with hundreds, so the list is searched rather than scrolled past.
        add(WorkbayLang.guiKey("rooms.biome.search"), "Search");
        add(WorkbayLang.guiKey("rooms.biome.nomatch"), "Nothing by that name");
        add(WorkbayLang.guiKey("rooms.biome.none"), "This pack lists no room biomes");
        add(WorkbayLang.guiKey("rooms.colour"), "Walls: %s");
        add(WorkbayLang.guiKey("rooms.colour.tip"), "What this room's shell is painted.");
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
        add(WorkbayLang.guiKey("rooms.biome.tip"), "What a machine in here reads for temperature and rainfall. Nothing falls "
            + "from the sky: a cold room is cold, it does not snow.");
        add(WorkbayLang.guiKey("rooms.unanchored.tip"), "What is inside runs only while somebody is in here. Anchoring holds %s "
            + "chunks loaded.");
        add(WorkbayLang.messageKey("anchor_capped"), "This network may anchor %s room(s) at a time. "
            + "Switch another one off first.");
        add(WorkbayLang.guiKey("upgrade.resonator"), "Resonator");
        add(WorkbayLang.guiKey("upgrade.resonator.desc"), "Any dimension");
        add(WorkbayLang.guiKey("upgrade.resonator.long"),
            "Lets a link's two ends stand in different dimensions. Without one, a Connector "
            + "in the Nether cannot be reached from an overworld Workbay -- the Backshop "
            + "itself always can.");
        // The Impeller shipped with no name at all: its row on the upgrades screen drew
        // "gui.workbay.upgr..." and "gui.workbay.u...", in red boxes, because the screen builds
        // this key from the enum and nobody added the row when the enum grew. Found by opening
        // the screen at a real window size, which nothing else in this mod can do for you.
        add(WorkbayLang.guiKey("upgrade.impeller"), "Impeller");
        add(WorkbayLang.guiKey("upgrade.impeller.desc"), "Faster links");
        add(WorkbayLang.guiKey("upgrade.impeller.long"),
            "Every link on this Workbay moves twice as much per step and waits half as long "
            + "between steps. Two of them stack.");

        // The gauges under the grid. The figures on the line have no unit - it does not fit - so
        // the tooltip is where "mB" and "FE" are actually said.
        // The fluid slots. The resting line is the label - two unnamed slots are a guess - and the
        // three others are the refusals. Only mixing names anything: whether a tank is full or
        // empty is already drawn, in a gauge, directly above them.
        add(WorkbayLang.messageKey("reject.op_only_data"), "%s carries settings only an operator "
            + "may place. Racking it would rack an empty one, so it is refused instead.");
        add(WorkbayLang.messageKey("filter_no_chemical"), "There is no chemical in that tank to "
            + "name. Fill it first, then list what is in it.");
        // Entering the bay. SPEC.md §5: the machine's own screen is reachable only by standing in
        // front of it, so this is the one control that answers "how do I upgrade the thing".
        add(WorkbayLang.guiKey("button.open"), "Open its screen");
        add(WorkbayLang.guiKey("button.open.tip"), "Opens the machine's own screen without leaving where you stand.");
        add(WorkbayLang.guiKey("button.enter"), "Enter bay");
        add(WorkbayLang.guiKey("button.enter.tip"), "Puts you next to it and opens its own screen. Close that to come back.");
        add(WorkbayLang.messageKey("bay_enter_failed"), "That bay cannot be entered.");
        add(WorkbayLang.messageKey("bay_no_screen"), "%s has no screen of its own.");
        add(WorkbayLang.messageKey("bay_load_timeout"),
            "The bay did not reach your client in time. Try again.");
        // The number alone. The row has about thirty pixels for this and the sentence is sixty.

        add(WorkbayLang.messageKey("pair_needs_connector"), "Hold a Connector \u2014 main hand or off "
            + "hand \u2014 to pair one to this bay.");
        add(WorkbayLang.messageKey("upgrade_maxed"), "This Workbay already has as many of those as "
            + "it takes.");
        add(WorkbayLang.messageKey("annex_needs_frame"), "An Annex Plate adds a room to a network "
            + "that already has one. Install a Room Frame first.");
        add(WorkbayLang.messageKey("upgrade_missing"), "You don't have one of those to install.");

        // Tooltips. SPEC.md §6: at most four lines unshifted.
        add(WorkbayLang.tooltipKey("hosting"), "Hosting: %s / %s machines");
        add(WorkbayLang.tooltipKey("buses"), "Buses: %s configured");
        add(WorkbayLang.tooltipKey("code"), "Code %s");
        add(WorkbayLang.tooltipKey("connector_unpaired"), "Not paired. Right-click a Workbay with "
            + "this to pair it.");
        add(WorkbayLang.tooltipKey("connector_paired"), "Paired \u00b7 network %s");

        // The guide, on every item's Information tab in JEI and EMI. SPEC.md §6's voice at the one
        // length it is ever allowed: a player right-clicking a Workbay in a recipe viewer used to
        // get a crafting grid and no sentence saying what the block is for. The Workbay's page is
        // the only one that teaches a sequence, because it is the only one anybody reads first.
        add(WorkbayLang.infoKey("workbay.1"), "A Workbay hosts other mods' machines inside a "
            + "private dimension and reaches them wirelessly. A racked machine runs exactly as it "
            + "did on your floor \u2014 it is out of sight, not out of the world. It does not save "
            + "TPS. What it saves is the floor space, and the cables.");
        add(WorkbayLang.infoKey("workbay.2"), "The first three things to do:");
        add(WorkbayLang.infoKey("workbay.3"), "1. Place it and right-click it. The column down the "
            + "left is your bays; a fresh Workbay has two, and the rest are bought later. Hold a "
            + "machine and click the large slot beside the name to rack it.");
        add(WorkbayLang.infoKey("workbay.4"), "2. Right-click the Workbay with a Connector to pair "
            + "the two, then place that Connector against any chest, tank or machine in the world. "
            + "Placing it is what makes the link, and the link appears as a row on this screen.");
        add(WorkbayLang.infoKey("workbay.5"), "3. Set that row's direction and what it carries. "
            + "Insert pushes into the block the Connector is stuck to; Extract pulls out of it. "
            + "The Workbay glows while goods are moving and goes amber when a link needs you.");
        add(WorkbayLang.infoKey("workbay.6"), "Everything past that is an upgrade you craft and "
            + "fit on the Upgrades tab — more bays, faster links, and rooms out the back. "
            + "Each one is consumed when it goes in, and there is no taking it out again.");

        add(WorkbayLang.infoKey("connector.1"), "One end of a link, as a block you can point at. "
            + "Right-click a Workbay with it to pair the two; its tooltip then names the network. "
            + "Place it against a chest, tank or machine and the network can reach that block. It "
            + "carries nothing yet — open a bay, press Add, and pick it. Break it and every "
            + "channel through it is gone.");
        add(WorkbayLang.infoKey("connector.2"), "It reaches all six faces of the block it is stuck "
            + "to, so which side you put it on does not matter — and one Connector serves every "
            + "bay at once. Add it on the bay holding your power cube for energy, and again on the "
            + "bay holding your generator for items: two channels, one plate, each with its own "
            + "resource, direction, filter and rate. Its name is the block's, so renaming it "
            + "anywhere renames it everywhere.");
        add(WorkbayLang.infoKey("connector.3"), "A link out of the dimension its Workbay stands in "
            + "needs a Resonator. Anything inside that dimension, and anything in the Backshop, "
            + "does not.");


        add(WorkbayLang.infoKey("expansion_plate.1"), "One more bay, consumed on install. Each one "
            + "One more bay, up to the eight the rack holds.");
        add(WorkbayLang.infoKey("annex_plate.1"), "One more room, consumed on install. It needs a "
            + "Room Frame first \u2014 with no rooms to add to it would install and do nothing, so "
            + "it refuses instead.");

        add(WorkbayLang.infoKey("room_frame.1"), "A room: a private, sealed cube out the back of "
            + "the Backshop that you walk into and build in. Consumed on install.");
        add(WorkbayLang.infoKey("room_frame.2"), "The Frame you install sets the size of every room "
            + "you own \u2014 14 blocks on a side, 30 for a Wide Frame, 46 for a Vast one. A larger "
            + "Frame expands the room you already have rather than giving you a second; the Annex "
            + "Plate is what gives you a second.");
        add(WorkbayLang.infoKey("room_frame.3"), "Pick a room's biome and its colour on the Rooms "
            + "tab, and invite other players in there while you are at it. The four doors in the "
            + "walls are the way out, and they cannot be broken.");

        add(WorkbayLang.infoKey("anchor.1"), "Keeps the Backshop running with nobody standing at "
            + "the Workbay, so a chain of machines out the back carries on while you are away "
            + "doing something else. Switch it on per room, on the Rooms tab.");
        add(WorkbayLang.infoKey("anchor.2"), "It holds nothing while its owner is offline. Log out "
            + "and the chunks are let go; log back in and they are taken again, with the chain "
            + "carrying on from where it stopped. Nothing is lost in the gap, because nothing this "
            + "mod moves is ever in transit between two ticks.");

        add(WorkbayLang.infoKey("resonator.1"), "Lets this network's links reach into other "
            + "dimensions. Without one, a Connector in the Nether pointing at a Workbay in the "
            + "Overworld sits still, and its row says so.");
        add(WorkbayLang.infoKey("impeller.1"), "Doubles what every link moves in a step and halves "
            + "the wait between steps \u2014 both, on every link this network has. Two may be "
            + "fitted. Throughput is the one thing a fresh Workbay is deliberately short of.");

        add(WorkbayLang.infoKey("shopsteel.1"), "An intermediate. Smelted, and used in almost "
            + "everything this mod makes.");
        add(WorkbayLang.infoKey("housing.1"), "An intermediate. The shell every craftable in this "
            + "mod is built on.");
    }
}
