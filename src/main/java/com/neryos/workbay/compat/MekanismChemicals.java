package com.neryos.workbay.compat;

import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism's chemicals, read the way its fluids and energy already are. SPEC.md §5.
 *
 * <p><b>Why this is the one mod with a name in the source.</b> Every other gauge on Bay View comes
 * from a NeoForge capability that any mod can implement, and this class exists because a chemical
 * has none — Mekanism registers its own, and a machine that holds gas would otherwise show a
 * hosted tank as simply absent. Mekanism is too large a share of what anyone racks for "we cannot
 * see it" to be the answer.
 *
 * <p><b>Compiled against its API only, and guarded twice.</b> `compileOnly`, exactly as the JEI and
 * EMI plugins are, so the mod builds and loads with Mekanism absent; every entry point checks
 * {@link ModList} first, and the capability is looked up by name rather than by importing
 * {@code mekanism.common}. {@code BlockCapability.createSided} returns the same object Mekanism
 * registered when the name and type match, which is how a capability is shared without a hard
 * dependency.
 */
public final class MekanismChemicals {
    private MekanismChemicals() {}

    private static final String MOD_ID = "mekanism";

    /** One chemical tank, flattened to what a gauge needs and nothing more. */
    public record Tank(Component name, long amount, long capacity) {}

    public static boolean present() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Every chemical tank a block holds, or nothing at all when Mekanism is absent or the block has
     * none. Reads the null side: this is a <em>reading</em>, and the null side is the one that
     * reports every tank rather than the subset one face is configured for — the same split
     * SPEC.md §5 already makes between reading a machine and writing to it.
     */
    public static List<Tank> tanks(ServerLevel level, BlockPos pos) {
        if (!present() || !level.isLoaded(pos)) {
            return List.of();
        }
        return Impl.tanks(level, pos);
    }

    /**
     * Everything that names a Mekanism type, in a class of its own, because loading a class is
     * what resolves the types in its fields. A {@code static final} capability of type
     * {@code IChemicalHandler} on the outer class is resolved when the *guard* is called -- before
     * {@link #present()} can answer -- so a mod that loads perfectly well without Mekanism still
     * threw {@code NoClassDefFoundError} the first time anything read a bay. Found by opening Bay
     * View in a plain instance; a dev run always has Mekanism on the classpath and never can.
     * A nested class is initialised on its own first use, which is here, after the guard passed.
     */
    private static final class Impl {
        private Impl() {}

        /**
         * The same capability object Mekanism registers, built from its name.
         * {@code createSided} returns the very object when the name and type match, which is how a
         * capability is shared without a hard dependency.
         */
        private static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL =
            BlockCapability.createSided(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "chemical_handler"),
                IChemicalHandler.class);

        static List<Tank> tanks(ServerLevel level, BlockPos pos) {
            IChemicalHandler handler = level.getCapability(CHEMICAL, pos, null);
            if (handler == null) {
                return List.of();
            }
            List<Tank> tanks = new ArrayList<>();
            for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
                ChemicalStack held = handler.getChemicalInTank(tank);
                long capacity = handler.getChemicalTankCapacity(tank);
                if (capacity > 0) {
                    tanks.add(new Tank(held.isEmpty() ? Component.empty() : held.getTextComponent(),
                        held.isEmpty() ? 0L : held.getAmount(), capacity));
                }
            }
            return List.copyOf(tanks);
        }
    }
}
