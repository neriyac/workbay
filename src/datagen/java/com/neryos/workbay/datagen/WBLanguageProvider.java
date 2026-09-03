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

        // Tooltips. SPEC.md §6: at most four lines unshifted.
        add(WorkbayLang.tooltipKey("hosting"), "Hosting: %s / %s machines");
        add(WorkbayLang.tooltipKey("buses"), "Buses: %s configured");
        add(WorkbayLang.tooltipKey("code"), "Code %s");
    }
}
