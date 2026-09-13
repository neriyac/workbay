# Workbay

**One block that hosts your machines out of sight and reaches them wirelessly.**

Minecraft 1.21.1 · NeoForge 21.1.249+ · Java 21 · MIT · no dependencies

A Workbay racks up to eight of any mod's machines — real blocks, still ticking, still running their
own recipes — in a private dimension, and links them to the chests, tanks and machines you left
standing in the world through small Connector blocks. No cables, no floor of boxes. A Room, in three
sizes, racks in a bay like a machine and travels as an item.

**It saves space, never TPS.** A hosted machine costs exactly what it costs on the floor, because it
*is* the same block entity in a dimension of the mod's own. The mod's own overhead is measured, not
claimed: about 0.05 ms per tick per busy network on a dedicated server, no forced chunks
(`perf/`, `./gradlew runBenchmark`). `MODPAGE.md` is the player-facing text; `CHANGELOG.md` is the
release history.

## Install

Drop `workbay-neoforge-1.21.1-<version>.jar` into `mods/` on the client **and** the server. JEI,
EMI and Mekanism are optional: with a recipe viewer you can drag an ingredient into a link's
filter; with Mekanism, links carry chemicals too. Opening a hosted machine's own screen from where
you stand uses two Mixins; `config/workbay-mixins.properties` turns them off, and the mod then
falls back to walking into the bay.

## Play

1. Craft **Shopsteel** (1 iron + 1 amethyst shard makes 2), then a **Workbay**: 4 glass,
   4 Shopsteel, 1 ender pearl. Place it — it binds to you.
2. Hold a machine, right-click the Workbay, click the large slot beside the name. It lives inside
   now; its own screen is one button away.
3. Craft a **Connector** (Shopsteel, redstone, Shopsteel — makes 4), right-click the Workbay with
   it to pair, place it against any chest, tank or machine. Back at the Workbay, press **Add** on
   a bay and tick the Connector: a row appears. Switch it on, set direction, resource and filter.

Every item explains itself behind Shift. `/workbay why <block>` says whether a block can be hosted.

## Build and test

```
./gradlew build                 # the jar, in build/libs/
./tools/verify.sh               # build + 179 gametests + doc caps: what CI would run
./gradlew runClient             # the game, with Mekanism and JEI loaded
./gradlew runData               # after a datagen change; commit src/generated
./tools/publish.sh              # dry run of the release; --real uploads (see the script)
```

`SPEC.md` is the design, with every settled decision and the alternative it rejected. `CLAUDE.md`
is the working manual; `HANDOFF.md` the current state; `OPEN_ISSUES.md` the known problems.
Bugs: <https://github.com/neriyac/workbay/issues>.

## Licence

MIT. Parts are derived from [EnderIO](https://github.com/Team-EnderIO/EnderIO), which is public
domain (Unlicense). Modpacks welcome.
