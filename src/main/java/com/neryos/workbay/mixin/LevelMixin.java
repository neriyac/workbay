package com.neryos.workbay.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.neryos.workbay.client.RemoteMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a screen find the machine it was opened for.
 *
 * <p>Client only. A modded menu's client-side constructor reads a position out of the open packet
 * and looks it up in {@code Minecraft.getInstance().level} -- its only level. A hosted machine is
 * in the Backshop, whose chunks that client was never sent, so the lookup misses and the throw
 * that follows is turned into a disconnect (OPEN_ISSUES, "Facts worth not rediscovering").
 *
 * <p>Only ever consulted when vanilla found <b>nothing</b>, so a real block entity always wins and
 * the added cost on a hit is a null check. On a miss it is one lookup in a map that is empty unless
 * a remote screen is open.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @ModifyReturnValue(method = "getBlockEntity", at = @At("RETURN"))
    private BlockEntity workbay$remoteMachine(BlockEntity original, BlockPos pos) {
        return original != null ? original : RemoteMachines.shadow(pos);
    }
}
