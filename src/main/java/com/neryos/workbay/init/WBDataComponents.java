package com.neryos.workbay.init;

import com.neryos.workbay.Workbay;
import com.neryos.workbay.content.workbay.WorkbayBinding;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WBDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Workbay.MOD_ID);

    /** What a dropped Workbay remembers about its bays. See {@link WorkbayBinding}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WorkbayBinding>> BINDING =
        COMPONENTS.register("binding", () -> DataComponentType.<WorkbayBinding>builder()
            .persistent(WorkbayBinding.CODEC)
            .networkSynchronized(WorkbayBinding.STREAM_CODEC)
            .build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
