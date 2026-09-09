package com.neryos.workbay.content.assay;

import com.mojang.serialization.MapCodec;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.WorkbaySounds;
import com.neryos.workbay.config.WorkbayConfig;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    /**
     * SPEC.md §3: tagged items become one Levy, and it takes a while to do it. <b>Both are config
     * now</b> (defaults 64 and 200), and so is the dial's range below.
     *
     * <p>Methods rather than constants, and that is the point: a constant is inlined by javac into
     * every class that reads it, so a host's edited value would reach the caller that happened to
     * recompile and nothing else. It also means the number is read where it is <em>used</em> — the
     * tick that converts, the click that moves the dial — rather than once at class load, which is
     * the dead-path shape this mod has now found four times.
     */
    public static int itemsPerLevy() {
        return WorkbayConfig.SERVER.itemsPerLevy.get();
    }

    public static int convertTicks() {
        return WorkbayConfig.SERVER.levyConvertTicks.get();
    }

    /**
     * The skim dial's range. SPEC.md §3 parked the maximum here "until something else needs to be
     * recipe-driven too"; what happened instead is that the whole balance surface moved to config
     * in one go (see {@link WorkbayConfig} for which half went where and why), so this is a knob
     * on the server's file rather than a field in a recipe type invented to hold one integer.
     */
    public static int maxRate() {
        return WorkbayConfig.SERVER.maxSkimPercent.get();
    }

    public static int rateStep() {
        return WorkbayConfig.SERVER.skimStepPercent.get();
    }

    public AssayBlock(Properties properties) {
        super(properties);
    }

    /**
     * The cartridge SPEC.md §7 asks for, and the same box the model draws. Without it the Assay
     * would collide as a full cube — a player inside a bay would be stopped by air beside a thing
     * that plainly is not there, which reads as a bug in the room rather than as a shape.
     */
    private static final VoxelShape SHAPE = Block.box(2, 0, 5, 14, 14, 11);

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
        BlockPos pos, CollisionContext context) {
        return SHAPE;
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
            WorkbaySounds.refuse(player, WorkbayLang.message("reject.assay_needs_bay"));
        }
        return InteractionResult.SUCCESS;
    }
}
