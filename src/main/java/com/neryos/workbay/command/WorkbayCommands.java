package com.neryos.workbay.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.neryos.workbay.Workbay;
import com.neryos.workbay.host.HostChecks;
import com.neryos.workbay.host.HostResult;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * SPEC.md §11. Thirty lines that turn the denylist from a support burden into self-service: a pack
 * author who wants to know why their favourite machine will not go in a bay can ask, instead of
 * filing an issue and waiting.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID)
public class WorkbayCommands {

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("workbay")
            .then(Commands.literal("why")
                .then(Commands.argument("block", ResourceLocationArgument.id())
                    .suggests((context, builder) -> {
                        BuiltInRegistries.BLOCK.keySet().forEach(id -> builder.suggest(id.toString()));
                        return builder.buildFuture();
                    })
                    .executes(context -> why(context.getSource(),
                        ResourceLocationArgument.getId(context, "block")))));
        event.getDispatcher().register(root);
    }

    private static int why(CommandSourceStack source, ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || (block == Blocks.AIR && !id.equals(ResourceLocation.withDefaultNamespace("air")))) {
            source.sendFailure(Component.literal("No block is registered as " + id));
            return 0;
        }

        BlockState state = block.defaultBlockState();
        HostResult result = HostChecks.evaluate(new ItemStack(block));

        source.sendSuccess(() -> Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)
            .append(result.allowed()
                ? Component.literal(" can be hosted.").withStyle(ChatFormatting.GREEN)
                : Component.literal(" can't be hosted: ").withStyle(ChatFormatting.RED)
                    .append(result.message())), false);

        // The inputs behind the verdict, so the answer is checkable rather than an oracle.
        line(source, "in #workbay:host_allowed", state.is(HostChecks.HOST_ALLOWED));
        line(source, "in #workbay:host_denied", state.is(HostChecks.HOST_DENIED));
        line(source, "has a block entity", state.hasBlockEntity());
        line(source, "obtainable as an item", block.asItem() != net.minecraft.world.item.Items.AIR);
        source.sendSuccess(() -> Component.literal("  piston reaction: ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(state.getPistonPushReaction().name()).withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("  connection properties: ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(String.valueOf(connectionProperties(state))).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String label, boolean value) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(value ? "yes" : "no")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY)), false);
    }

    private static int connectionProperties(BlockState state) {
        int found = 0;
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Boolean.class) {
                switch (property.getName()) {
                    case "north", "east", "south", "west", "up", "down" -> found++;
                    default -> { }
                }
            }
        }
        return found;
    }
}
