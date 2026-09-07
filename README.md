# Workbay

**One block that hosts your machines out of sight and reaches them wirelessly.**

Minecraft 1.21.1 · NeoForge · v0.1.0 · MIT

---

## What it is for

A tech base ends up as a floor of machines you never look at, joined by cables you look at
constantly. Workbay takes the machines off the floor. You rack up to eight of them inside one
block — real machines, from any mod, still ticking, still running their own recipes — and you
link that block to the chests, tanks and machines you left standing in the world.

No cables. No sorting corridor. One block where a room used to be.

## What it does **not** do

**It saves space, never TPS.** A hosted machine costs exactly what it costs on the floor, because
it *is* the same block entity, ticking in the same way, in a dimension of the mod's own. Anyone who
profiles this mod will find that, so it is written here first. The numbers are below, and they are
measured, not estimated.

It is also not a routing mod. A link has one source and one target — three destinations are three
links. If you want channels, priorities and round-robin, you want XNet, and the two get along fine.

## The measured numbers

Every figure is a **difference between two recordings**, not a reading of one: each scenario
profiles five 400-tick windows with nothing of its own built, builds, and profiles five more. On
minus off, median of five. Taken with the game's own metrics profiler (what `/perf start` uses) on
a headless server. `./gradlew runBenchmark` reproduces all of it.

| Scenario | ms/tick |
|---|---|
| One Workbay, eight busy links, 6.4 items/tick | **0.011** block entity + **0.013–0.029** for its dimension |
| Sixteen separate networks, 128 busy links, 102 items/tick | **0.102** + **0.208** — linear, no cliff |
| Sixty-four vanilla hoppers doing the same job, 7.8 items/tick | **0.034** |
| Sixteen furnaces racked in bays / standing on the floor | **0.0030** / **0.0028** |
| Four Workbay screens open / four hosted machines' own screens open | **0.0069** / **0.0029** |

Per item moved, the whole path — block entity plus the hosted chunk it holds — is about **3 µs**,
against a vanilla hopper's **4 µs**. Hosting a machine costs what the floor costs: sixteen furnaces
smelting in bays and sixteen smelting in the overworld came out inside each other's spread.

An idle base is the common case, so it was measured too: a link whose destination is full costs
**0.006** ms/tick.

## What you need

- **Minecraft 1.21.1** and **NeoForge 21.1.249** or newer. Java 21.
- Nothing else. No library mod, no dependency.
- On a server, the mod goes on **both** ends. Opening a hosted machine's own screen from where you
  stand uses two Mixins; a host who wants nothing patched turns them off in
  `config/workbay-mixins.properties`, and the mod falls back to walking into the bay instead.

## What it works with

Any mod's machine block that has a block entity and does not need the world around it. Furnaces,
smelters, tanks, generators, chests. Mekanism is what it is tested against, in an automated
cross-mod test suite that runs on every build.

It refuses, and says why in one line, anything that would break: multiblock parts, machines that
work on neighbouring blocks, anything driven by rotation, and anything a pack has denied through
the `workbay:host_denied` tag. Every block in the game carries a one-line tooltip saying whether it
can be hosted, which shows up in JEI and EMI too.

JEI and EMI are optional — with either installed you can drag an ingredient straight into a link's
filter. Neither is required and the mod loads with neither.

## Getting started

1. Drop the jar in `mods/`.
2. Craft a **Workbay**: 4 glass, 4 Shopsteel (1 iron + 1 amethyst shard makes 2), 1 eye of ender.
   Reachable the evening you first visit the End.
3. Place it. It binds to you — lose the block and a fresh, uncrafted Workbay picks the network
   straight back up, with the same bays, links and upgrades.
4. Hold a machine, right-click the Workbay, and click the empty bay slot. That machine now lives
   inside, and one button away is its own screen — the real one, opened where you are standing.
5. Craft a **Connector**, right-click the Workbay with it to pair it, then place it against any
   chest, tank or machine in the world. A row appears. Switch it on.

Everything above the base — more bays, other dimensions, all three resource types down one
Connector, twice the throughput — is bought with **Levy**, which you make by taxing your own
factory a percentage you set yourself. The dial starts at 0% and the mod never takes a cut you did
not ask for.

## Known rough edges in 0.1.0

The version number is 0.1.0 for one reason: bay geometry is written into saved worlds, and this is
the first build anyone outside its own repository has run. It is finished enough to use; the record
format may still move.

- **A link's rate and speed cannot be set from the screen yet.** They exist and the Impeller
  upgrade raises them; the per-link dial is not built.
- **Filters match an item or a fluid by identity — no tags.** "All ores" means listing them.
- **Links carry items, fluids and energy, but not Mekanism chemicals.** A hosted machine's gas
  tank keeps working; no link can move what is in it yet.
- **Rooms are not built.** Bays hold one machine each; hand-built multiblocks are a later version.

## Licence

MIT. Do what you like with it, including in a modpack. Parts are derived from
[EnderIO](https://github.com/Team-EnderIO/EnderIO), which is public domain (Unlicense).
