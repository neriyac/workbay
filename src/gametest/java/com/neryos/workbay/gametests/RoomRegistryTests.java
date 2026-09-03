package com.neryos.workbay.gametests;

import com.neryos.workbay.world.RoomRegistry;
import com.neryos.workbay.world.WorkbayDimensions;
import com.neryos.workbay.world.WorkbayRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SPEC.md §14. The registry is what makes a lost Workbay recoverable, so it is worth pinning down. */
@ForEachTest(groups = "room_registry")
public class RoomRegistryTests {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000a11ce");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    @GameTest
    @TestHolder(description = "The registry mints unique codes and non-overlapping bay columns.")
    public static void mintsUniqueCodesAndColumns(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            RoomRegistry registry = new RoomRegistry();
            RandomSource random = RandomSource.create(1234L);

            Set<String> codes = new HashSet<>();
            Set<Long> columns = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                WorkbayRecord record = registry.create(ALICE, "Alice", random);
                if (!codes.add(record.code())) {
                    helper.fail("the registry minted the same code twice: " + record.code());
                    return;
                }
                if (!columns.add(record.bayColumn().toLong())) {
                    helper.fail("two Workbays were given the same bay column: " + record.bayColumn());
                    return;
                }
                if (record.code().length() != 14 || record.code().charAt(4) != '-'
                    || record.code().charAt(9) != '-') {
                    helper.fail("code is not formatted XXXX-XXXX-XXXX: " + record.code());
                    return;
                }
                // No ambiguous letters: a code has to survive being read off a screenshot.
                if (record.code().matches(".*[CEIOU].*")) {
                    helper.fail("code contains an ambiguous letter: " + record.code());
                    return;
                }
            }

            // A forced-chunk ticket covers radius 2, so anything closer than 5 chunks apart means an
            // anchored Workbay would hold its neighbour's bays loaded too (OPEN_ISSUES #17).
            WorkbayRecord first = registry.ownedBy(ALICE).get(0);
            helper.assertValueEqual(registry.size(), 200, "registry size");
            if (first.bayColumn().x % 5 != 0 && first.bayColumn().z % 5 != 0) {
                helper.fail("bay columns are not spaced far enough apart: " + first.bayColumn());
            }
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder(description = "A code can be looked up with or without its dashes, in any case.")
    public static void codeLookupIsForgiving(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            RoomRegistry registry = new RoomRegistry();
            WorkbayRecord record = registry.create(ALICE, "Alice", RandomSource.create(7L));

            for (String typed : List.of(record.code(), record.code().replace("-", ""),
                record.code().toLowerCase(java.util.Locale.ROOT))) {
                Optional<WorkbayRecord> found = registry.byCode(typed);
                if (found.isEmpty() || !found.get().id().equals(record.id())) {
                    helper.fail("looking up " + typed + " did not find the Workbay it belongs to");
                    return;
                }
            }
            if (registry.byCode("0000-0000-0000").isPresent()) {
                helper.fail("an unknown code found something");
            }
            helper.succeed();
        });
    }

    /**
     * The whole point of the registry: an operator can still find a player's Workbay after the
     * block is gone. If a save and load loses any of this, that recovery path is gone with it.
     */
    @GameTest
    @TestHolder(description = "Every registry field survives a save and load.")
    public static void survivesSaveAndLoad(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            RoomRegistry before = new RoomRegistry();
            RandomSource random = RandomSource.create(99L);

            WorkbayRecord alice = before.create(ALICE, "Alice", random);
            before.create(BOB, "Bob", random);

            alice = alice
                .withUpgrades(new WorkbayRecord.Upgrades(3, 1, 1, 2, 17))
                .withLocked(true)
                .withLastKnownPos(GlobalPos.of(WorkbayDimensions.BACKSHOP, new BlockPos(12, 34, -56)))
                .withBays(List.of(
                    new WorkbayRecord.Bay(0, Optional.of(
                        net.minecraft.resources.ResourceLocation.parse("mekanism:enrichment_chamber"))),
                    WorkbayRecord.Bay.empty(1)));
            before.put(alice);

            RoomRegistry after = RoomRegistry.roundTrip(before, registries);

            helper.assertValueEqual(after.size(), 2, "records after reload");
            WorkbayRecord reloaded = after.byId(alice.id()).orElse(null);
            if (reloaded == null) {
                helper.fail("Alice's Workbay is not in the reloaded registry");
                return;
            }
            helper.assertValueEqual(reloaded, alice, "the whole record after reload");

            // The bay column must not move: it is where the machines physically are.
            helper.assertValueEqual(reloaded.bayColumn(), alice.bayColumn(), "bay column after reload");
            // Codes have to keep working after a restart or recovery is impossible.
            if (after.byCode(alice.code()).isEmpty()) {
                helper.fail("the code index was not rebuilt on load");
                return;
            }
            // A new Workbay must not be handed a column an old one already owns.
            WorkbayRecord fresh = after.create(BOB, "Bob", random);
            if (fresh.bayColumn().equals(alice.bayColumn())) {
                helper.fail("the bay column allocator restarted from zero after a reload");
            }
            helper.succeed();
        });
    }

    /**
     * SPEC.md §14: DataVersion is the entire migration mechanism, because NeoForge has no mod
     * DataFixers. A registry written by a newer build must refuse to load rather than quietly
     * discard the fields it does not understand.
     */
    @GameTest
    @TestHolder(description = "A registry from a newer version refuses to load instead of losing data.")
    public static void refusesAnUnknownDataVersion(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            RoomRegistry before = new RoomRegistry();
            before.create(ALICE, "Alice", RandomSource.create(3L));

            CompoundTag tag = before.save(new CompoundTag(), registries);
            helper.assertValueEqual(tag.getInt(RoomRegistry.VERSION_KEY), RoomRegistry.DATA_VERSION,
                "DataVersion written");

            tag.putInt(RoomRegistry.VERSION_KEY, RoomRegistry.DATA_VERSION + 1);
            try {
                RoomRegistry.load(tag, registries);
                helper.fail("a registry from a newer version loaded anyway, silently dropping "
                    + "whatever that version added");
            } catch (IllegalStateException expected) {
                helper.succeed();
            }
        });
    }

    @GameTest
    @TestHolder(description = "The registry lives on the overworld and is the same instance every time.")
    public static void livesOnTheOverworld(final DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1));

        test.onGameTest(ExtendedGameTestHelper.class, helper -> {
            var server = helper.getLevel().getServer();
            RoomRegistry registry = RoomRegistry.get(server);
            helper.assertNotNull(registry, "RoomRegistry.get returned null");
            if (RoomRegistry.get(server) != registry) {
                helper.fail("RoomRegistry.get built a second instance; the two would overwrite "
                    + "each other at save time");
            }
            helper.succeed();
        });
    }
}
