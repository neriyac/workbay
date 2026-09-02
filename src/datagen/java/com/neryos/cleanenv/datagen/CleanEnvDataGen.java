package com.neryos.cleanenv.datagen;

import com.neryos.cleanenv.CleanEnv;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;

/**
 * Datagen entry point. Lives in its own source set and its own class but under the same
 * mod id as {@link CleanEnv}, which is how EnderIO does it — the classes never ship, only
 * their output under src/generated/resources does.
 */
@Mod(CleanEnv.MOD_ID)
public class CleanEnvDataGen {

    public CleanEnvDataGen(IEventBus modEventBus) {
        modEventBus.addListener(this::onGatherData);
    }

    private void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new CEBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new CEItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeServer(), new CERecipeProvider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, java.util.Set.of(),
            List.of(new LootTableProvider.SubProviderEntry(CELootTableProvider::new, LootContextParamSets.BLOCK)),
            event.getLookupProvider()));
    }
}
