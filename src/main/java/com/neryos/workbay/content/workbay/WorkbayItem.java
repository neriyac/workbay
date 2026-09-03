package com.neryos.workbay.content.workbay;

import com.neryos.workbay.WorkbayLang;
import com.neryos.workbay.config.WorkbayConfig;
import com.neryos.workbay.init.WBDataComponents;
import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The Workbay in the hand. SPEC.md §14's network model.
 *
 * <p>Refuses a placement that would exceed {@code maxDeployedWorkbaysPerNetwork} before the block
 * goes down at all, rather than placing it and taking it back — the gate belongs on the item's
 * {@code useOn}, not on {@code WorkbayBlock#setPlacedBy}, which only ever sees a placement that has
 * already happened.
 */
public class WorkbayItem extends BlockItem {

    public WorkbayItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel() instanceof ServerLevel server && player != null) {
            RoomRegistry registry = RoomRegistry.get(server.getServer());
            WorkbayRecord target = targetNetwork(registry, player, context.getItemInHand());
            int maxDeployed = WorkbayConfig.SERVER.maxDeployedWorkbaysPerNetwork.get();
            if (target != null && target.deployedCount() >= maxDeployed) {
                player.displayClientMessage(
                    WorkbayLang.message("network_deployed_full", maxDeployed), true);
                return InteractionResult.FAIL;
            }
            if (target == null) {
                int maxNetworks = WorkbayConfig.SERVER.maxNetworksPerPlayer.get();
                if (registry.ownedBy(player.getUUID()).size() >= maxNetworks) {
                    player.displayClientMessage(
                        WorkbayLang.message("network_cap_reached", maxNetworks), true);
                    return InteractionResult.FAIL;
                }
            }
        }
        return super.useOn(context);
    }

    /**
     * The network this placement would bind to: the one named on the item if it still exists,
     * otherwise the player's own existing network (there is at most
     * {@code maxNetworksPerPlayer} of those). {@code null} means this placement would mint a new
     * one, which is gated separately.
     */
    private static WorkbayRecord targetNetwork(RoomRegistry registry, Player player, ItemStack stack) {
        WorkbayBinding binding = stack.get(WBDataComponents.BINDING.get());
        if (binding != null) {
            var existing = registry.byId(binding.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        var owned = registry.ownedBy(player.getUUID());
        return owned.isEmpty() ? null : owned.get(0);
    }
}
