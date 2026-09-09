package com.neryos.workbay.compat;

import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.init.WBItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/**
 * The guide, as data. SPEC.md §6.
 *
 * <p><b>There was nowhere a player could look.</b> Right-clicking a Workbay in a recipe viewer
 * gave them a crafting grid and nothing else — no statement of what the block is for, and no
 * first move to make once it is placed. That is the exact moment somebody asks what to do next,
 * and every mod in this genre answers it on that tab.
 *
 * <p>It lives here rather than in either plugin because both viewers want the same list and
 * neither's API is ours: JEI hands a page to {@code addIngredientInfo}, EMI wraps one in an
 * {@code EmiInfoRecipe}, and both are three lines over this. The same argument as the ghost slots
 * next door — two adapters onto one list, and no interface pretending to unify two foreign APIs.
 *
 * <p><b>No text is written here.</b> Every line is a key, so the pages are translated with
 * everything else and the copy stays in one file (SPEC.md §6: the lang file is the truth).
 */
public final class WorkbayGuide {
    private WorkbayGuide() {}

    /** One item's page: the thing it is about, and the paragraphs, in order. */
    public record Page(Supplier<? extends ItemLike> item, List<Component> lines) {}

    /**
     * The Workbay's page carries the first three things to do and the others do not, because a
     * player reads exactly one of these before they start and it is the one on the block they just
     * crafted. Everything after it answers "what is this for" in a paragraph.
     */
    public static List<Page> pages() {
        return List.of(
            page(WBBlocks.WORKBAY, "workbay", 6),
            page(WBBlocks.CONNECTOR, "connector", 3),
            page(WBItems.EXPANSION_PLATE, "expansion_plate", 1),
            page(WBItems.ROOM_FRAME, "room_frame", 3),
            page(WBItems.WIDE_ROOM_FRAME, "room_frame", 3),
            page(WBItems.VAST_ROOM_FRAME, "room_frame", 3),
            page(WBItems.ANNEX_PLATE, "annex_plate", 1),
            page(WBItems.ANCHOR, "anchor", 2),
            page(WBItems.RESONATOR, "resonator", 1),
            page(WBItems.MULTICHANNEL, "multichannel", 1),
            page(WBItems.IMPELLER, "impeller", 1),
            page(WBItems.SHOPSTEEL, "shopsteel", 1),
            page(WBItems.HOUSING, "housing", 1));
    }

    /**
     * Keys are {@code info.workbay.<name>.1} upward, so a page grows by adding a line here.
     *
     * <p>A blank line between paragraphs, because both viewers draw the list straight down a
     * column about twenty-five characters wide: without one, six paragraphs are a wall and the
     * numbered steps stop looking like steps. <b>A space, not {@link Component#empty()}</b> --
     * JEI wraps each component with the font and an empty one splits to no lines at all, so the
     * gap silently was not there.
     */
    private static Page page(Supplier<? extends ItemLike> item, String name, int lines) {
        List<Component> text = new java.util.ArrayList<>();
        for (int n = 1; n <= lines; n++) {
            if (n > 1) {
                text.add(Component.literal(" "));
            }
            text.add(WorkbayLang.info(name + "." + n));
        }
        return new Page(item, List.copyOf(text));
    }
}
