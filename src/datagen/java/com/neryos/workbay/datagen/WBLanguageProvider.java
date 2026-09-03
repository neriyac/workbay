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
        addItem(WBItems.SHOPSTEEL, "Shopsteel");
        addItem(WBItems.HOUSING, "Housing");

        // Chat, one-shot. SPEC.md §6.
        add(WorkbayLang.messageKey("room_created"), "Workbay %s created. Write this code down \u2014 "
            + "if you lose the block, an operator can get you back in with it.");
        add(WorkbayLang.messageKey("break_warning"), "Breaking this Workbay leaves its bays behind. "
            + "The machines keep running; you'll need code %s to get back in.");
        add(WorkbayLang.messageKey("locked"), "This Workbay is locked.");

        // Tooltips. SPEC.md §6: at most four lines unshifted.
        add(WorkbayLang.tooltipKey("hosting"), "Hosting: %s / %s machines");
        add(WorkbayLang.tooltipKey("buses"), "Buses: %s configured");
        add(WorkbayLang.tooltipKey("code"), "Code %s");
    }
}
