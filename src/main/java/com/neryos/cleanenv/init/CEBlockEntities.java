package com.neryos.cleanenv.init;

import com.neryos.cleanenv.CleanEnv;
import com.neryos.cleanenv.content.counter.CounterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CleanEnv.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterBlockEntity>> COUNTER =
        BLOCK_ENTITIES.register("counter", () ->
            BlockEntityType.Builder.of(CounterBlockEntity::new, CEBlocks.COUNTER_BLOCK.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
