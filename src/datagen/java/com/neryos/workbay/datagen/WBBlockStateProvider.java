package com.neryos.workbay.datagen;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.content.connector.ConnectorBlock;
import com.neryos.workbay.init.WBBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class WBBlockStateProvider extends BlockStateProvider {

    public WBBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Workbay.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Placeholder art on vanilla textures. SPEC.md §7 wants a dark cabinet with a glass front
        // and three lit states; that is Phase 3, and every state currently draws the same model.
        ModelFile model = models().orientable("workbay",
            mcBlock("polished_deepslate"), mcBlock("blast_furnace_front"), mcBlock("polished_deepslate"));

        // toYRot() + 180, not toYRot(). toYRot is the yaw of a player *looking* that way, which is
        // the opposite of the angle a north-facing model has to be turned by, so the bare value puts
        // the front of every block in this mod on its back. Vanilla's furnace blockstate is the
        // reference: facing=east is y=90, and Direction.EAST.toYRot() is 270.
        getVariantBuilder(WBBlocks.WORKBAY.get()).forAllStates(state -> ConfiguredModel.builder()
            .modelFile(model)
            .rotationY(((int) state.getValue(HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360)
            .build());

        // A thin plate drawn on the NORTH side, then rotated onto whichever face it is stuck to.
        // Same +180 as above; here getting it wrong put the plate on the far side of the block it
        // was stuck to, inside it, where nobody could see it at all.
        ModelFile plate = models().withExistingParent("connector", mcBlock("block"))
            .texture("particle", mcBlock("netherite_block"))
            .texture("plate", mcBlock("netherite_block"))
            .element().from(4, 4, 0).to(12, 12, 2)
            .allFaces((face, builder) -> builder.texture("#plate")).end();

        getVariantBuilder(WBBlocks.CONNECTOR.get()).forAllStates(state -> {
            Direction facing = state.getValue(ConnectorBlock.FACING);
            return ConfiguredModel.builder()
                .modelFile(plate)
                // x=90 puts the plate on the bottom of its own cell, which is where a Connector
                // pointing down belongs. Checked in game against a full block: on a chest it looks
                // like it floats, but that is the chest being 14/16 tall, not the rotation.
                .rotationX(facing == Direction.DOWN ? 90 : facing == Direction.UP ? 270 : 0)
                .rotationY(facing.getAxis().isHorizontal() ? ((int) facing.toYRot() + 180) % 360 : 0)
                .build();
        });

        // A Port is only ever seen by a player standing in a room, which cannot happen in v1.
        simpleBlock(WBBlocks.PORT.get(), models().cubeAll("port", mcBlock("chiseled_polished_blackstone")));
    }

    private static ResourceLocation mcBlock(String path) {
        return ResourceLocation.withDefaultNamespace("block/" + path);
    }
}
