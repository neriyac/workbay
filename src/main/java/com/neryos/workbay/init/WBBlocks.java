package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.content.assay.AssayBlock;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.content.connector.ConnectorItem;
import com.neryos.workbay.content.port.PortBlock;
import com.neryos.workbay.content.workbay.WorkbayBlock;
import com.neryos.workbay.content.workbay.WorkbayItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class WBBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Workbay.MOD_ID);
    /** Block items. Plain items live in {@link WBItems}. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Workbay.MOD_ID);

    public static final DeferredBlock<WorkbayBlock> WORKBAY = registerWorkbay();

    /**
     * Generated into a bay, never given to a player. No BlockItem at all, so it cannot be crafted,
     * picked or given, and -1 hardness so it cannot be broken. SPEC.md §2: a bay with a missing wall
     * is an unrecoverable state.
     */
    /**
     * The world end of a link (SPEC.md §0). A thin plate on any block, paired to a Workbay before
     * it is placed. Its BlockItem is custom only so the tooltip can name what it is paired to.
     */
    public static final DeferredBlock<ConnectorBlock> CONNECTOR = registerConnector();

    /**
     * The mod's only machine, and the only source of Levy. Built to be racked: no faces, no block
     * entity, nothing to connect to on the floor. SPEC.md §2 and §3.
     */
    public static final DeferredBlock<AssayBlock> ASSAY = registerWithItem("assay", AssayBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .mapColor(MapColor.COLOR_PURPLE)
            .sound(SoundType.METAL)
            .strength(3.0F)
            .noOcclusion());

    public static final DeferredBlock<PortBlock> PORT = BLOCKS.register("port", () ->
        new PortBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .sound(SoundType.METAL)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
            .isValidSpawn((state, level, pos, type) -> false)));

    /**
     * Its BlockItem is custom (not {@code registerWithItem}) so it can refuse a placement that
     * would exceed {@code maxDeployedWorkbaysPerNetwork} before the block ever goes down, rather
     * than placing it and then having to take it back.
     */
    private static DeferredBlock<WorkbayBlock> registerWorkbay() {
        var holder = BLOCKS.registerBlock("workbay", WorkbayBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_BLACK)
                .sound(SoundType.METAL)
                .strength(3.5F)
                // The other half of SPEC.md §7's "distinguishable in the dark". A texture is only
                // as visible as the light falling on it, so an unlit base would hide all three
                // readings equally well; the block lights itself instead, the way a furnace does.
                // Never 15: a Workbay is furniture, not a lamp, and a wall of them should not
                // light a base for free.
                .lightLevel(state -> switch (state.getValue(WorkbayBlock.STATE)) {
                    case IDLE -> 3;
                    case RUNNING -> 10;
                    case STUCK -> 8;
                })
                .noOcclusion());
        ITEMS.register("workbay", () -> new WorkbayItem(holder.get(), new net.minecraft.world.item.Item.Properties()));
        return holder;
    }

    private static DeferredBlock<ConnectorBlock> registerConnector() {
        var holder = BLOCKS.registerBlock("connector", ConnectorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.COLOR_BLACK)
                .sound(SoundType.METAL)
                .strength(1.5F)
                .noOcclusion());
        ITEMS.register("connector", () -> new ConnectorItem(holder.get(), new net.minecraft.world.item.Item.Properties()));
        return holder;
    }

    static <B extends Block> DeferredBlock<B> registerWithItem(String name,
        Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
        var blockHolder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.registerSimpleBlockItem(blockHolder);
        return blockHolder;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
