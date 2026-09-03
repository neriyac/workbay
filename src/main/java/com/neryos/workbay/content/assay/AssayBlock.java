package com.neryos.workbay.content.assay;

import com.mojang.serialization.MapCodec;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.init.WBBlocks;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Assay. The mod's own machine, and the only thing that makes Levy. SPEC.md §3.
 *
 * <p><b>It has no block entity and no capability on any face</b>, which is the whole design rather
 * than an omission. Nothing can pipe into it and nothing can pipe out of it, so the goods it
 * converts can only be the ones the Workbay itself diverts, and the Levy it makes can only be a
 * balance the Workbay's own screen reads. On the floor it is inert — not because a rule forbids it,
 * but because there is nothing to connect to. SPEC.md §14 gives it one courtesy: right-clicking it
 * out in the world says so.
 *
 * <p>Its state lives on the {@link WorkbayRecord}, not here. A bay is a position in another
 * dimension; the network is what earns the Levy, and the network is what spends it.
 */
public class AssayBlock extends Block {
    public static final MapCodec<AssayBlock> CODEC = simpleCodec(AssayBlock::new);

    /**
     * What the skim takes a cut of. An item tag, so a pack extends it with one JSON file and it
     * works for a mod we have never seen — a fixed list of three items would turn a tax on your
     * factory into a fetch quest wearing a theme. SPEC.md §3.
     */
    public static final TagKey<Item> LEVY_INPUT = TagKey.create(Registries.ITEM, Workbay.rl("levy_input"));

    /** SPEC.md §3, provisional: 64 tagged items become one Levy, and it takes 200 ticks to do it. */
    public static final int ITEMS_PER_LEVY = 64;
    public static final int CONVERT_TICKS = 200;

    /**
     * The dial's range. SPEC.md §3 puts the maximum in the Assay's recipe JSON; there is no custom
     * recipe type in the mod and inventing one to hold a single integer would be the whole point of
     * §0's "six knobs, everything else is a tag or a recipe" read backwards. It lives here until
     * something else needs to be recipe-driven too.
     */
    public static final int MAX_RATE = 25;
    public static final int RATE_STEP = 5;

    public AssayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Whether this network is actually running an Assay. The skim and the conversion are both gated
     * on it: taking a cut of somebody's goods with nothing to turn them into is theft, not a tax.
     */
    public static boolean rackedIn(WorkbayRecord record) {
        return record.bays().stream()
            .anyMatch(bay -> bay.hosted().filter(WBBlocks.ASSAY.getId()::equals).isPresent());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(WorkbayLang.message("reject.assay_needs_bay"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
