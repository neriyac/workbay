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
        addItem(WBItems.SHOPSTEEL, "Shopsteel");
        addItem(WBItems.HOUSING, "Housing");
        addBlock(WBBlocks.CONNECTOR, "Connector");
        addItem(WBItems.LEVY, "Levy");
        addItem(WBItems.EXPANSION_PLATE, "Expansion Plate");
        addItem(WBItems.RESONATOR, "Resonator");
        addItem(WBItems.MULTICHANNEL, "Multichannel Upgrade");

        // Chat, one-shot. SPEC.md §6.
        add(WorkbayLang.messageKey("room_created"), "Workbay %s created. Write this code down \u2014 "
            + "if you lose the block, an operator can get you back in with it.");
        add(WorkbayLang.messageKey("break_warning"), "Breaking this Workbay leaves its bays behind. "
            + "The machines keep running; you'll need code %s to get back in.");
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
        add(WorkbayLang.messageKey("reject.assay_needs_bay"), "The Assay only works racked in a bay. "
            + "It has no faces to connect to out here.");

        // The Connector, which is where every link comes from. SPEC.md §0.
        add(WorkbayLang.messageKey("connector_paired"), "Connector paired to Workbay %s. Place it "
            + "against the block you want to link.");
        add(WorkbayLang.messageKey("connector_unpaired"), "This Connector isn't paired yet. "
            + "Right-click a Workbay with it first.");
        add(WorkbayLang.messageKey("connector_linked"), "Linked to %s. Workbay %s now has a row for "
            + "it.");
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
        add(WorkbayLang.guiKey("button.rename"), "Rename");
        add(WorkbayLang.guiKey("button.redstone"), "Redstone");
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
            + "can take items in and send energy out.");
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
        add(WorkbayLang.guiKey("links.filter.this_bay"), "Showing: this bay");
        add(WorkbayLang.guiKey("links.filter.all_bays"), "Showing: all bays");
        add(WorkbayLang.guiKey("links.filter.problems"), "Showing: problems only");
        add(WorkbayLang.guiKey("links.filter.tip"), "Click to change which links this list shows.");
        add(WorkbayLang.guiKey("links.sort.bay"), "Sorted by bay");
        add(WorkbayLang.guiKey("links.sort.type"), "Sorted by resource type");
        add(WorkbayLang.guiKey("links.sort.status"), "Sorted by status, problems first");
        add(WorkbayLang.guiKey("links.sort.tip"), "Click to change the order.");
        add(WorkbayLang.guiKey("links.type.item"), "Carries items");
        add(WorkbayLang.guiKey("links.type.fluid"), "Carries fluids");
        add(WorkbayLang.guiKey("links.type.energy"), "Carries energy");
        add(WorkbayLang.guiKey("links.type.tip"), "Click to change what this link carries.");
        add(WorkbayLang.guiKey("links.mode.insert"), "Sends into the target");
        add(WorkbayLang.guiKey("links.mode.extract"), "Pulls out of the target");
        add(WorkbayLang.guiKey("links.mode.tip"), "Click to turn this link around.");
        add(WorkbayLang.guiKey("links.gear"), "Link settings");
        add(WorkbayLang.guiKey("links.gear.tip"), "Open this link's controls, including removing it.");
        add(WorkbayLang.guiKey("links.toggle"), "Enable or disable");
        add(WorkbayLang.guiKey("links.toggle.tip"), "A disabled link stays in the list and moves "
            + "nothing.");
        add(WorkbayLang.guiKey("links.remove"), "Remove this link");
        add(WorkbayLang.guiKey("links.remove.tip"), "The Connector stays where it is. Right-click it "
            + "to link it again.");
        add(WorkbayLang.guiKey("links.filterslot"), "Filter");
        add(WorkbayLang.guiKey("links.unknown"), "not loaded");

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
            + "of the faces this link may use. Check the cube, or eject and re-rack it.");
        add(WorkbayLang.guiKey("status.machine_no_face"), "No face for this");
        add(WorkbayLang.guiKey("status.machine_no_face.tip"), "This bay has faces set, but none "
            + "marked for this link's direction. Mark one in on the cube for a link that pulls, "
            + "or out for one that sends. Setting every face back to unset uses whichever answers.");

        add(WorkbayLang.guiKey("flow.empty"), "No machines racked yet.");
        add(WorkbayLang.guiKey("flow.unknown"), "not loaded");
        add(WorkbayLang.guiKey("flow.legend.out"), "leaves the Workbay");
        add(WorkbayLang.guiKey("flow.legend.internal"), "bay to bay, never leaves");
        add(WorkbayLang.guiKey("flow.legend.stalled"), "stalled");

        add(WorkbayLang.guiKey("upgrades.rate"), "Accepts up to %s FE/t");
        add(WorkbayLang.guiKey("upgrades.levy"), "Levy in your inventory: %s");
        add(WorkbayLang.guiKey("upgrades.tax"), "Tax rate: %s%% — the Assay is not built yet");
        add(WorkbayLang.guiKey("upgrades.add"), "Install one %s");
        add(WorkbayLang.guiKey("upgrades.maxed"), "You have as many of these as a Workbay takes.");
        add(WorkbayLang.guiKey("upgrade.expansion_plate"), "Expansion Plate");
        add(WorkbayLang.guiKey("upgrade.expansion_plate.desc"), "+1 bay, up to eight");
        add(WorkbayLang.guiKey("upgrade.expansion_plate.cost"), "Costs 4 Levy to craft, rising.");
        add(WorkbayLang.guiKey("upgrade.resonator"), "Resonator");
        add(WorkbayLang.guiKey("upgrade.resonator.desc"), "Reaches other dimensions");
        add(WorkbayLang.guiKey("upgrade.resonator.cost"), "Costs 4 Levy and an Ender Eye to craft.");
        add(WorkbayLang.guiKey("upgrade.multichannel"), "Multichannel");
        add(WorkbayLang.guiKey("upgrade.multichannel.desc"), "One Connector, all three types");
        add(WorkbayLang.guiKey("upgrade.multichannel.cost"), "Costs 4 Levy and an Amethyst Shard to "
            + "craft.");

        add(WorkbayLang.messageKey("pair_needs_connector"), "Hold a Connector to pair one.");
        add(WorkbayLang.messageKey("upgrade_maxed"), "This Workbay already has as many of those as "
            + "it takes.");
        add(WorkbayLang.messageKey("upgrade_missing"), "You don't have one of those to install.");

        // Tooltips. SPEC.md §6: at most four lines unshifted.
        add(WorkbayLang.tooltipKey("hosting"), "Hosting: %s / %s machines");
        add(WorkbayLang.tooltipKey("buses"), "Buses: %s configured");
        add(WorkbayLang.tooltipKey("code"), "Code %s");
        add(WorkbayLang.tooltipKey("connector_unpaired"), "Not paired. Right-click a Workbay with "
            + "this to pair it.");
        add(WorkbayLang.tooltipKey("connector_paired"), "Paired to %s, bay %s");
    }
}
