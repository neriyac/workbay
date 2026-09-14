#!/usr/bin/env bash
# Cuts the reusable half of this repo into a new-mod template and zips it.
#
#   ./tools/make-template.sh            -> ../NeoForge-1.21.1-Template.zip
#   ./tools/make-template.sh out.zip
#
# A script rather than a hand-made zip, because the last hand-made one went stale the session
# after it was cut. Anything Workbay-specific is skipped, not stripped: a file is either generic
# enough to copy whole or it is replaced by a skeleton written here, and there is no third case
# where a copy gets edited into shape by hand.
#
# The template's mod id is `newmod`, package com.example.newmod, holder prefix NM.

set -eu
cd "$(dirname "$0")/.." || exit 1
root=$(pwd)
zip_out=${1:-"$root/../NeoForge-1.21.1-Template.zip"}

stage="build/template/newmod"
rm -rf build/template
mkdir -p "$stage"

src="$stage/src/main/java/com/example/newmod"
gen="$stage/src/datagen/java/com/example/newmod/datagen"
tst="$stage/src/gametest/java/com/example/newmod/gametests"
mkdir -p "$src/init" "$src/client/screen" "$src/config" "$src/network" "$gen" "$tst"
mkdir -p "$stage/src/gametest/resources/META-INF"

# ---------------------------------------------------------------- copied whole
copy() {
  mkdir -p "$stage/$(dirname "$1")"
  cp "$1" "$stage/$1"
}

for f in gradlew gradlew.bat settings.gradle .gitattributes .gitignore \
         gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties \
         .githooks/pre-commit .githooks/pre-push \
         tools/check-docs.sh tools/check-text.sh tools/install-hooks.sh \
         tools/mc-drive.ps1 tools/verify.sh \
         src/main/templates/META-INF/neoforge.mods.toml \
         docs/dev/MOD_MAP.md; do
  copy "$f"
done
chmod +x "$stage"/tools/*.sh "$stage"/.githooks/* "$stage/gradlew"

# The one path in tools/ that names this project.
sed -i -e 's|com/neryos/workbay/client|com/example/newmod/client|' \
       -e 's|WorkbayPage#text|a page'"'"'s #text|' "$stage/tools/check-text.sh"

# ------------------------------------------------- copied, with the mod renamed
# Draw and the icon atlas are ~870 lines of hand-tuned GUI primitives with no game logic in them.
# Draw's one coupling to this mod is roleColour, deleted below.
rename() {
  sed -e 's/com\.neryos\.workbay/com.example.newmod/g' \
      -e 's/Workbay/NewMod/g' \
      -e 's/workbay/newmod/g' \
      -e 's/\bWB/NM/g' "$1"
}

rename src/main/java/com/neryos/workbay/client/screen/Draw.java \
  | sed -e '/^import com\.example\.newmod\.world\.FaceConfig;\r\?$/d' \
        -e '/public static int roleColour/,/^    }\r\?$/d' \
  > "$src/client/screen/Draw.java"
rename src/main/java/com/neryos/workbay/client/screen/WBIcons.java > "$src/client/screen/NMIcons.java"
rename src/main/java/com/neryos/workbay/WorkbayLang.java > "$src/NewModLang.java"
rename src/gametest/java/com/neryos/workbay/gametests/WorkbayTests.java > "$tst/NewModTests.java"

for f in "$src/client/screen/Draw.java" "$src/client/screen/NMIcons.java" "$src/NewModLang.java"; do
  grep -q 'FaceConfig\|neryos' "$f" && { echo "$f still names this project"; exit 1; }
done

# build.gradle, minus the three third-party mods only Workbay's own tests need.
python - "$root/build.gradle" "$stage/build.gradle" <<'PY'
import re, sys
text = open(sys.argv[1], encoding='utf-8').read().replace('\r\n', '\n')
# The whole repositories block exists for Mekanism/JEI/EMI.
text = re.sub(r'\nrepositories \{.*?\n\}\n', '\n', text, flags=re.S)
# Their dependency lines, with the comments that introduce them.
for block in [
    r'\n *// A real third-party mod.*?\n *localRuntime "mekanism[^\n]*\n',
    r'\n *// Ghost ingredients.*?\n *compileOnly "dev\.emi[^\n]*\n',
    r"\n *// Mekanism's API.*?\n *compileOnly \"mekanism[^\n]*\n",
]:
    text = re.sub(block, '\n', text, flags=re.S)
# The publish block is worth keeping, but it names this mod's optional dependencies,
# its slug and its repository. A new mod fills those in; it does not inherit them.
text = text.replace("        optional('jei', 'emi', 'mekanism')\n", '')
for line in ["        optional { slug = 'jei' }\n",
             "        optional { slug = 'emi' }\n",
             "        optional { slug = 'mekanism' }\n"]:
    text = text.replace(line, '')
text = text.replace("projectSlug = 'workbay'", "projectSlug = 'newmod'")
text = text.replace("repository = 'neriyac/workbay'", "repository = 'you/newmod'")
text = text.replace(
    "            programArguments.addAll '--quickPlaySingleplayer', 'New World'\n", '')
open(sys.argv[2], 'w', encoding='utf-8', newline='\n').write(text)
PY
if grep -qi 'mekanism\|jei\|emi' "$stage/build.gradle"; then
  echo "build.gradle still names a third-party mod"; exit 1
fi

# gradle.properties, minus those versions, with the placeholder identity.
grep -v 'mekanism_version\|jei_version\|emi_version\|Third-party mod loaded\|Optional at runtime' \
  gradle.properties \
  | sed -e 's/^mod_id=.*/mod_id=newmod/' -e 's/^mod_name=.*/mod_name=New Mod/' \
        -e 's/^mod_group_id=.*/mod_group_id=com.example.newmod/' > "$stage/gradle.properties"

cat > "$stage/src/gametest/resources/META-INF/neoforge.mods.toml" <<'TOML'
modLoader="javafml"
loaderVersion="[1,)"
license="All Rights Reserved"

[[mods]]
modId="newmod_tests"
displayName="New Mod: Tests"
TOML

# ------------------------------------------------------------------- skeletons
cat > "$src/NewMod.java" <<'JAVA'
package com.example.newmod;

import com.example.newmod.config.NewModConfig;
import com.example.newmod.init.NMBlockEntities;
import com.example.newmod.init.NMBlocks;
import com.example.newmod.init.NMCapabilities;
import com.example.newmod.init.NMCreativeTabs;
import com.example.newmod.init.NMDataComponents;
import com.example.newmod.init.NMItems;
import com.example.newmod.init.NMMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * The mod. One holder class per registry type in {@code init/}, each registered here, so there is
 * one list of everything the mod adds instead of DeferredRegister fields scattered through the
 * content classes. EnderIO's shape.
 */
@Mod(NewMod.MOD_ID)
public class NewMod {
    public static final String MOD_ID = "newmod";

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public NewMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, NewModConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, NewModConfig.CLIENT_SPEC);

        NMBlocks.register(modEventBus);
        NMBlockEntities.register(modEventBus);
        NMItems.register(modEventBus);
        NMDataComponents.register(modEventBus);
        NMCreativeTabs.register(modEventBus);
        NMMenus.register(modEventBus);
        modEventBus.addListener(NMCapabilities::register);
    }
}
JAVA

cat > "$src/client/NewModClient.java" <<'JAVA'
package com.example.newmod.client;

import com.example.newmod.NewMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half of the mod. Nothing here may be reachable from the server side. */
@EventBusSubscriber(modid = NewMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NewModClient {
    private NewModClient() {}

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        // event.register(NMMenus.EXAMPLE.get(), ExampleScreen::new);
    }
}
JAVA

cat > "$src/config/NewModConfig.java" <<'JAVA'
package com.example.newmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Config knobs, and only knobs: anything a pack would want to change per-recipe or per-block is a
 * tag or a recipe instead, because that is where pack authors already work.
 *
 * <p>{@code SERVER} is the only type synced to clients and the only one overridable per-world under
 * {@code saves/<world>/serverconfig}. Never {@code STARTUP} for anything that gates registration:
 * that desyncs registries between a server and its clients. Gate behaviour, not registration.
 */
public class NewModConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();

        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    public static class Server {
        public final ModConfigSpec.BooleanValue example;

        Server(ModConfigSpec.Builder builder) {
            example = builder
                .comment("Replace me. A config value nobody ever changes is a constant.")
                .define("example", true);
        }
    }

    /** Rendering only, never gameplay. */
    public static class Client {
        public final ModConfigSpec.BooleanValue exampleClient;

        Client(ModConfigSpec.Builder builder) {
            exampleClient = builder
                .comment("Replace me.")
                .define("exampleClient", true);
        }
    }
}
JAVA

cat > "$src/init/NMBlocks.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class NMBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NewMod.MOD_ID);
    /** Block items. Plain items live in {@link NMItems}. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NewMod.MOD_ID);

    // public static final DeferredBlock<Block> EXAMPLE = registerWithItem("example", Block::new,
    //     BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK));

    /**
     * A block and its BlockItem in one call. Register the item separately instead when the item
     * needs its own class -- a custom tooltip, or a placement it can refuse.
     */
    static <B extends Block> DeferredBlock<B> registerWithItem(String name,
        Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
        var holder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.registerSimpleBlockItem(holder);
        return holder;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
JAVA

cat > "$src/init/NMItems.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NewMod.MOD_ID);

    // public static final DeferredItem<Item> EXAMPLE =
    //     ITEMS.registerSimpleItem("example", new Item.Properties());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
JAVA

cat > "$src/init/NMBlockEntities.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NMBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NewMod.MOD_ID);

    // public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExampleBlockEntity>>
    //     EXAMPLE = BLOCK_ENTITIES.register("example", () ->
    //         BlockEntityType.Builder.of(ExampleBlockEntity::new, NMBlocks.EXAMPLE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
JAVA

cat > "$src/init/NMDataComponents.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What an item stack remembers. 1.21's replacement for item NBT. */
public class NMDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NewMod.MOD_ID);

    // A component needs both codecs: persistent() to survive a save, networkSynchronized() to
    // reach the client. Skipping the second one makes tooltips wrong on a server only.
    // public static final DeferredHolder<DataComponentType<?>, DataComponentType<Example>> EXAMPLE =
    //     COMPONENTS.register("example", () -> DataComponentType.<Example>builder()
    //         .persistent(Example.CODEC).networkSynchronized(Example.STREAM_CODEC).build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
JAVA

cat > "$src/init/NMMenus.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NMMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, NewMod.MOD_ID);

    // IMenuTypeExtension.create rather than MenuType::new: it is what lets the menu's opening
    // state ride the menu-open buffer, so the screen is correct on frame 1 instead of flashing
    // defaults while it waits for the first sync packet.
    // public static final DeferredHolder<MenuType<?>, MenuType<ExampleMenu>> EXAMPLE =
    //     MENUS.register("example", () -> IMenuTypeExtension.create(ExampleMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
JAVA

cat > "$src/init/NMCreativeTabs.java" <<'JAVA'
package com.example.newmod.init;

import com.example.newmod.NewMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NMCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NewMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
        CREATIVE_MODE_TABS.register(NewMod.MOD_ID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + NewMod.MOD_ID))
            .icon(() -> new ItemStack(Items.CRAFTING_TABLE))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> {
                addAll(NMBlocks.ITEMS, output);
                addAll(NMItems.ITEMS, output);
            })
            .build());

    public static void register(IEventBus bus) {
        CREATIVE_MODE_TABS.register(bus);
    }

    /** Everything registered, in registration order, so a new item never needs a line here. */
    private static void addAll(DeferredRegister<Item> items, CreativeModeTab.Output output) {
        for (var entry : items.getEntries()) {
            output.accept(entry.get());
        }
    }
}
JAVA

cat > "$src/init/NMCapabilities.java" <<'JAVA'
package com.example.newmod.init;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What this mod's blocks expose to other mods: item handlers, fluid handlers, energy.
 *
 * <p>Decide per face, not once. A block that hands out the same handler on every side has no way
 * to say "coal goes in the bottom" later without breaking whatever was already piped into it.
 */
public final class NMCapabilities {
    private NMCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NMBlockEntities.EXAMPLE.get(),
        //     (be, side) -> be.energy());
    }
}
JAVA

cat > "$src/network/NMNetwork.java" <<'JAVA'
package com.example.newmod.network;

import com.example.newmod.NewMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Every custom packet, registered in one place. The version string belongs to the whole protocol,
 * so bump it when a payload's shape changes and old clients should be refused rather than confused.
 */
@EventBusSubscriber(modid = NewMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NMNetwork {
    private NMNetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // registrar.playToClient(ExamplePacket.TYPE, ExamplePacket.STREAM_CODEC, ExamplePacket::handle);
    }
}
JAVA

# ---------------------------------------------------------------------- datagen
cat > "$gen/NewModDataGen.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

/**
 * Datagen entry point. Its own source set but the same mod id as {@link NewMod}, which is how
 * EnderIO does it -- these classes never ship in the jar, only their output under
 * src/generated/resources does.
 */
@Mod(NewMod.MOD_ID)
public class NewModDataGen {

    public NewModDataGen(IEventBus modEventBus) {
        modEventBus.addListener(this::onGatherData);
    }

    private void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new NMBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new NMItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new NMLanguageProvider(output));

        NMBlockTagProvider blockTags =
            new NMBlockTagProvider(output, event.getLookupProvider(), existingFileHelper);
        generator.addProvider(event.includeServer(), blockTags);
        // Item tags have to be told what the block tags contain, or an item tag that mirrors a
        // block tag cannot be validated.
        generator.addProvider(event.includeServer(), new NMItemTagProvider(output,
            event.getLookupProvider(), blockTags.contentsGetter(), existingFileHelper));
        generator.addProvider(event.includeServer(), new NMRecipeProvider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
            List.of(new LootTableProvider.SubProviderEntry(NMLootTableProvider::new, LootContextParamSets.BLOCK)),
            event.getLookupProvider()));
    }
}
JAVA

cat > "$gen/NMBlockStateProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class NMBlockStateProvider extends BlockStateProvider {

    public NMBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, NewMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // simpleBlock(NMBlocks.EXAMPLE.get());
        //
        // For a horizontally-facing block, rotate by toYRot() + 180, not toYRot(). toYRot is the
        // yaw of a player *looking* that way, which is the opposite of the angle a north-facing
        // model has to be turned by -- the bare value puts the front of every block on its back.
    }
}
JAVA

cat > "$gen/NMItemModelProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class NMItemModelProvider extends ItemModelProvider {

    public NMItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, NewMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // blockItem("example");
        // flatItem("example_ingot", "iron_ingot");
    }

    /** The item form of a block: reuses the block model, so there is one model to change. */
    private void blockItem(String name) {
        withExistingParent(name, ResourceLocation.fromNamespaceAndPath(NewMod.MOD_ID, "block/" + name));
    }

    /** A flat item on a borrowed vanilla texture. Placeholder art that is not an empty square. */
    private void flatItem(String name, String vanillaTexture) {
        withExistingParent(name, ResourceLocation.withDefaultNamespace("item/generated"))
            .texture("layer0", ResourceLocation.withDefaultNamespace("item/" + vanillaTexture));
    }
}
JAVA

cat > "$gen/NMLanguageProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Every string the player sees. Keys are built by {@link com.example.newmod.NewModLang}, so a
 * missing one is a rename the compiler catches rather than a typo somebody screenshots.
 */
public class NMLanguageProvider extends LanguageProvider {

    public NMLanguageProvider(PackOutput output) {
        super(output, NewMod.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + NewMod.MOD_ID, "New Mod");
        // addBlock(NMBlocks.EXAMPLE, "Example");
        // add(NewModLang.messageKey("rejected"), "...");
    }
}
JAVA

cat > "$gen/NMBlockTagProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class NMBlockTagProvider extends BlockTagsProvider {

    public NMBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
        ExistingFileHelper existingFileHelper) {
        super(output, registries, NewMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Any block whose properties carry requiresCorrectToolForDrops -- everything copied from
        // IRON_BLOCK does -- needs a mineable tag, or no tool is ever "correct" for it: it mines at
        // unmodified speed and drops nothing at all in survival, silently.
        // tag(BlockTags.MINEABLE_WITH_PICKAXE).add(NMBlocks.EXAMPLE.get());
        // tag(BlockTags.NEEDS_STONE_TOOL).add(NMBlocks.EXAMPLE.get());
    }
}
JAVA

cat > "$gen/NMItemTagProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.NewMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class NMItemTagProvider extends ItemTagsProvider {

    public NMItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
        CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(output, registries, blockTags, NewMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Prefer the convention tags every modded material already joins (Tags.Items.INGOTS and
        // friends) over a hardcoded list: a list says nothing about a mod you have never seen.
    }
}
JAVA

cat > "$gen/NMRecipeProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;

import java.util.concurrent.CompletableFuture;

/** Generated, but it ships as pack-editable JSON: a pack rebalancing you never needs you. */
public class NMRecipeProvider extends RecipeProvider {

    public NMRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NMBlocks.EXAMPLE.get())
        //     .pattern("###").define('#', Items.IRON_INGOT)
        //     .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(output);
    }
}
JAVA

cat > "$gen/NMLootTableProvider.java" <<'JAVA'
package com.example.newmod.datagen;

import com.example.newmod.init.NMBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.stream.Collectors;

/** Without a loot table a block simply vanishes when broken. Every droppable block needs one. */
public class NMLootTableProvider extends BlockLootSubProvider {

    public NMLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // dropSelf(NMBlocks.EXAMPLE.get());
        //
        // A block that keeps something in its block entity needs CopyComponentsFunction too, or
        // breaking it drops a blank copy and orphans whatever it was holding.
    }

    /** Every registered block, so a block with no table fails the build rather than the game. */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return NMBlocks.BLOCKS.getEntries().stream().map(e -> (Block) e.value()).collect(Collectors.toList());
    }
}
JAVA

cat > "$tst/SmokeTests.java" <<'JAVA'
package com.example.newmod.gametests;

import com.example.newmod.config.NewModConfig;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

/**
 * The shape every other test copies: StructureTemplateBuilder for the world, so no .nbt file is
 * needed, and one assertion that would actually go red.
 */
@ForEachTest(groups = "smoke")
public class SmokeTests {

    @GameTest
    @TestHolder(description = "The server config is registered and readable on a running server.")
    public static void serverConfigIsLoaded(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            // Reading a ModConfigSpec value that was never registered throws, so this is less a
            // test of the default than proof the spec is loaded -- the way config silently fails.
            helper.assertValueEqual(NewModConfig.SERVER.example.get(), true, "example");
            helper.succeed();
        });
    }
}
JAVA

# ------------------------------------------------------------------------ docs
cat > "$stage/README.md" <<'MD'
# NeoForge 1.21.1 mod template

Minecraft 1.21.1, NeoForge 21.1.249, Java 21, ModDevGradle 2.0.146.

The reusable half of a shipped mod with that mod's content removed -- not an MDK with extra files.
Everything here has been run: `build`, `runData`, `runGameTestServer`, `runClient`.

## Start

```bash
./tools/install-hooks.sh     # points git at .githooks (one-time)
./tools/verify.sh            # green before you write a line of your own
```

Then rename. `newmod` stands in for your mod id (lowercase, `[a-z][a-z0-9_]{1,63}`):

```bash
grep -rl 'newmod\|NewMod\|com\.example' --exclude-dir=.git --exclude-dir=build . \
  | xargs sed -i 's/com\.example\.newmod/com.you.yourmod/g; s/NewMod/YourMod/g; s/newmod/yourmod/g'
mv src/main/java/com/example src/main/java/com/you      # and datagen, gametest
```

`NM` is the holder prefix in `init/`; rename it to your own initials or leave it. Then
`./gradlew runData` and `./tools/verify.sh` again.

## What is in here, and why

| Piece | Why |
|---|---|
| `init/NM*` holders | One class per registry type, all registered from the mod class. EnderIO's shape. Scattered `DeferredRegister` fields do not scale. |
| `src/datagen` source set | Datagen classes never ship in the jar, only their output does. |
| `src/gametest` as its own mod | Tests never ship, and never load in `runClient`/`runServer`. |
| `client/screen/Draw.java` | Every shape a screen is made of, drawn from rectangles: no background PNG, so a layout change is not a round trip through an image editor. `#text` is the only thing allowed to draw a string, and `tools/check-text.sh` enforces that. |
| `client/screen/NMIcons.java` | 12x12 icons as pixel grids in code. No atlas to cut, and an icon is judged by rendering candidates to a PNG rather than restarting the game once per guess. |
| `NewModLang` | Every translation key built in one place, so the generated language file and the code reading it cannot drift apart. |
| `tools/verify.sh` + `.githooks` | Local CI. No Actions, no minutes, no remote. |
| `tools/check-docs.sh` | Two line budgets: docs read every session, and docs looked up on demand. Both otherwise only grow. |
| `tools/mc-drive.ps1` | Drives `runClient` without a human. Every line in it cost a session to find; read its header before using it. |
| `MOD_MAP.md` | Routes a question to one of 20 reference mods instead of grepping all of them. |
| `CLAUDE.md` | The conventions, so a session does not rediscover them. |

## Four things that will bite if you undo them

1. **Never set `neoforge.enabledGameTestNamespaces`.** The MDK sets it to the mod id. NeoForge's
   test framework registers gametests under *its* namespace, so any value there silently drops
   every test and `GameTestServer` dies with "No test functions were given!" -- which reads like a
   broken test, not a broken filter.
2. **Keep `loadedMods` restricted on the `data` run.** Without it datagen also loads the gametest
   mod, which cannot construct: the test framework is not on the datagen classpath.
3. **`GameTestServer` discards datapack dimensions.** It bakes world dimensions against an empty
   `LevelStem` registry (`net.minecraft.server.Main` uses `datapackDimensions()` instead), so a
   custom dimension declared in a datapack leaves `server.getLevel()` returning null in tests. The
   only merge point left is the `flat` world preset's own dimension map: ship
   `data/minecraft/worldgen/world_preset/flat.json` from the *test* mod, never from the main one.
4. **`check` depends on `compileGametestJava` and `compileDatagenJava`** on purpose. Without it a
   compile error in either source set passes `build` and surfaces only when someone runs them.

## Reference mods

Not included -- they are ~456 MB. Clone what you need next to the project as `../reference/<Name>`:

```bash
git clone --depth 1 --branch 1.21.1 https://github.com/Team-EnderIO/EnderIO.git ../reference/EnderIO
```

`MOD_MAP.md` lists all twenty with branches and licences. **EnderIO is the only one you may copy
from freely** (Unlicense: code and art, no attribution). Everything else is restricted, and asset
licences rarely match code licences. Learning from any of them is always fine.
MD

cat > "$stage/CLAUDE.md" <<'MD'
# CLAUDE.md

NeoForge 1.21.1 mod, Java 21, ModDevGradle. `SPEC.md` specifies it.
Mod id `newmod`, package `com.example.newmod`.

## Read order

1. `HANDOFF.md` -- state, decisions, next step.
2. `OPEN_ISSUES.md` -- known problems. Check before debugging anything; it may already be there.
3. `SPEC.md` -- the settled decisions, each with the alternative that was rejected and why.
   Do not re-open them.

Look up, do not read whole: `SPEC.md` (read the one section you are building, not the file) and
`MOD_MAP.md` (**before any research** -- which reference mod solves a problem, and which directory
inside it).

## Context budget

`tools/check-docs.sh` runs as a pre-commit hook and budgets **two sets separately**: the
always-read docs above (600 lines) and the reference docs opened on demand (1300). A new `*.md`
counts against the tight budget unless it is added to the reference list, which forces the decision
rather than hiding it.

Growth in the always-read set is paid for somewhere else in that set, so when updating them **trim
as much as you add**. Delete what stopped being true: finished work, resolved issues, decisions
nobody will revisit. `HANDOFF.md` is current state, not a changelog -- git has the history.

Never fix a size problem by splitting a file -- two files cost the same context as one and add a
lookup step. Rewrite shorter instead.

## Research

Answer from `MOD_MAP.md` first. Never grep across `../reference/` -- twenty repos will drown the
context window, which is the exact thing the map exists to prevent. If the map has no row for the
question, find the answer, then add one row. The clones are **read-only**.

**Copying:** EnderIO only -- public domain (Unlicense), code and textures, no attribution.
Everything else is restricted, and asset licences almost never match code licences. Learning from
any of them is always fine.

## Conventions

**Registration** -- one holder class per registry type in `init/`, named `NM<Thing>`, each exposing
`register(IEventBus)`, all called from the mod class's constructor. EnderIO's shape. Do not scatter
`DeferredRegister` fields across content classes.

**Text** -- `Draw#text` is the only thing that draws a string and it takes the width the string has;
`tools/check-text.sh` fails the build on any `drawString` elsewhere. F3 outlines every text box, red
where it was cut.

**Gametests** -- `src/gametest`, its own mod (`newmod_tests`), NeoForge's test framework. Use
`StructureTemplateBuilder` so no `.nbt` file is needed. Prefer a regression test named after the
bug it covers.

**Never set `neoforge.enabledGameTestNamespaces`.** The test framework registers gametests under
its own namespace; any filter there silently drops all of them and `GameTestServer` dies with "No
test functions were given!".

**Generated output** -- `build/`, `run/` and `src/generated/` are not hand-edited. Change the
generator, re-run it.

## Verify before claiming done

```bash
./tools/verify.sh              # build + gametests + doc caps. The one to run.
./tools/verify.sh --fast       # same, without the gametest server
./gradlew runData              # after any datagen change; commit src/generated
./gradlew runClient            # opens the game; needed for anything visual
```

Run them and read the output. "Should work" is not a result. A green `build` only proves it
compiles; only a gametest proves a behaviour works -- and a test that cannot fail proves nothing,
so when adding one, break the code once and confirm it goes red.

**Driving `runClient` without a human.** Use `tools/mc-drive.ps1`: dot-source it, call `Shot` then
`Sync-Shot`, then `Key` / `Say` / `Click` / `Drag` / `SneakClick`. It encodes everything that cost a
session to find. Foreground is taken by dropping `SPI_SETFOREGROUNDLOCKTIMEOUT` to zero, and
**every input function asserts the game is in front first** -- without that guard a lost focus types
into whatever the human is using. Keys go through `keybd_event`, because GLFW reads the key callback
and `SendKeys` only posts `WM_CHAR`; command text goes through the clipboard, because SendKeys drops
and reorders characters. **Never press Escape "to be safe"** -- with nothing open it opens the pause
menu and every key after it goes nowhere.

Screenshots are the game's own **F2**, into `run/screenshots/` -- never capture the desktop.
Screenshot pixels are **framebuffer** pixels and `GetClientRect` gives **logical** ones, so
`Sync-Shot` measures the newest screenshot rather than the window; nothing persists between
`powershell -Command` invocations, so **call it in every one**.

`Click` only aims in **GUIs**. In the world the game uses the **crosshair**, so moving the mouse
does nothing -- aim with `/tp @s <x> <y> <z> <yaw> <pitch>`, yaw **-90 east**, 90 west, 180 north,
then click at the screen centre. `facing` computes from the **feet** while the camera renders from
the **eye**, so a `facing` target's y must be the real one minus **1.62**.

`setblock` skips `setPlacedBy`, so a block that records something there must be placed by hand. And
a second dev client holds the save's `session.lock`; quit the old one with `CloseMainWindow()`,
never `Stop-Process`.

## Scope

Do not add features, dependencies or abstractions that were not asked for. No interface with one
implementation, no config for a value that never changes, no scaffolding for a phase that has not
started. When something is blocked, or ambiguous enough that two readings mean different work, ask.
Otherwise pick the obvious option, say which, and continue.

## Session end

**Not optional, and not only at the end.** Every session updates `HANDOFF.md` and `OPEN_ISSUES.md`
(trimming as you go) and commits. A session that changed code and left the docs alone did not
finish; so did one that left the working tree dirty. If the context is running out, write the docs
*first* and finish the code after -- an unwritten decision is lost, unfinished code is still in the
diff.

`HANDOFF.md` must end each session answering three things: what changed and why (blunt, present
tense, current state -- not a changelog), **what is left**, and every question you answered by
choosing rather than by asking. A finding that closed an investigation goes in `OPEN_ISSUES.md` so
the next session does not pay for it twice.
MD

cat > "$stage/SPEC.md" <<'MD'
# SPEC

What this mod is, and every decision already settled.

## 0. Settled

Each entry names the decision, the alternative rejected, and why. These are not re-opened; a new
argument goes in as a new entry, not as an edit to an old one.

- (none yet)

## 1. ...

Sections are looked up, not read whole -- one section per thing being built.

Comments in the code carried over from the template cite section numbers from the mod it was cut
from. Renumber or delete them as you write your own.
MD

cat > "$stage/HANDOFF.md" <<'MD'
# HANDOFF

Current state, not a changelog. Three things every session: what changed and why, what is left, and
every question answered by choosing rather than by asking.

## State

Fresh from the template. `./tools/verify.sh` is green: the mod loads, registers nothing yet, and one
smoke gametest proves the config spec reaches a running server.

## What is left

Everything.

## Chosen rather than asked

- (nothing yet)
MD

cat > "$stage/OPEN_ISSUES.md" <<'MD'
# OPEN ISSUES

Numbered, and deleted when resolved -- not a trophy shelf. Check here before debugging anything.

## Open

- (none)

## Facts worth not rediscovering

1. **`neoforge.enabledGameTestNamespaces` must stay unset.** NeoForge's test framework registers
   gametests under its own namespace, so any value there drops every test and `GameTestServer` dies
   with "No test functions were given!".
2. **The `data` run must keep `loadedMods` restricted to the mod itself.** Otherwise datagen loads
   the gametest mod, which cannot construct -- the test framework is not on the datagen classpath.
3. **`GameTestServer` discards datapack dimensions.** It bakes world dimensions against an empty
   `LevelStem` registry, so a custom dimension declared in a datapack leaves `server.getLevel()`
   null. Merge it into the `flat` world preset shipped by the *test* mod instead.
4. **A block copied from `IRON_BLOCK` needs a mineable tag.** Its properties carry
   `requiresCorrectToolForDrops`, so without the tag no tool is ever correct: it mines at unmodified
   speed and drops nothing in survival, silently.
MD

# --------------------------------------------------------------------- package
cd "$stage/.."
rm -f "$zip_out"
if command -v zip > /dev/null 2>&1; then
  zip -qr "$zip_out" newmod
else
  # PowerShell cannot read an MSYS path, and Compress-Archive is the only zip on a bare Windows.
  win_out=$(cygpath -w "$zip_out" 2>/dev/null || echo "$zip_out")
  powershell -NoProfile -Command \
    "Compress-Archive -Path newmod -DestinationPath '$win_out' -Force" > /dev/null
fi
cd "$root"

echo "template: $(find "$stage" -type f | wc -l) files -> $zip_out"
