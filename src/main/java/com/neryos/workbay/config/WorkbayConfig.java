package com.neryos.workbay.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Every number that decides how this mod plays, and nothing that has entries. SPEC.md §13.
 *
 * <h2>Where the line is, and why</h2>
 *
 * <p>Two audiences. A <b>datapack</b> is what a modpack ships: versioned with the pack, loaded per
 * world, and the place for anything with <em>entries</em> — what a Workbay costs
 * ({@code data/workbay/recipe/}), what feeds the Levy ({@code #workbay:levy_input}), and what may
 * be hosted (SPEC.md §11's tags). All three already exist and are already the only place those
 * answers live, so a pack carrying a mod we have never seen fixes a bad interaction there without
 * waiting for us. <b>Config</b> is what one host tunes for one server: switches, caps, and the
 * scalars below.
 *
 * <p><b>Nothing new joined the datapack side, on purpose.</b> What was left in Java was
 * <em>scalars</em> — a rising cost ladder, a conversion rate, a throughput ceiling — and a scalar
 * in a datapack registry buys a pack nothing that a shipped {@code defaultconfigs/} TOML does not
 * already give it, while costing a reload listener, a sync packet, and a second place the same
 * ladder can be written down and disagree with itself. SPEC.md §0 settled "everything else is a
 * tag or a recipe" against <em>a config section per subsystem</em>; the ladder is the one case
 * §3 and {@link com.neryos.workbay.content.workbay.WorkbayUpgrade#levyCost} had already recorded
 * that a recipe cannot express, because a recipe costs the same the tenth time as the first.
 *
 * <p>The one number that <em>would</em> genuinely be data is what a single item is worth to the
 * Levy — iron one, diamond eight — rather than sixty-four of anything. Nobody has asked for it,
 * so it is written down (OPEN_ISSUES) rather than built.
 *
 * <p><b>The mixin switch stays in its own file</b> and is deliberately not absorbed here:
 * {@link com.neryos.workbay.remote.RemoteConfig} is read before class transformation, which is
 * earlier than any {@code ModConfigSpec} exists. Moving it here would turn "this mod does not
 * patch {@code Level}" into "this mod patches it and returns early", which is a weaker promise.
 *
 * <p>All of the gameplay ones are {@code SERVER}: the only type synced to clients and the only one
 * overridable per-world under {@code saves/<world>/serverconfig}. Synced matters — the upgrades
 * screen prices the ladder on the client through
 * {@link com.neryos.workbay.content.workbay.WorkbayUpgrade#levyCost}. Never {@code STARTUP}: the
 * Anchor is always registered and only its <em>behaviour</em> is gated, because gating
 * registration desyncs registries between a server and its clients.
 */
public class WorkbayConfig {

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
        public final ModConfigSpec.BooleanValue allowAnchors;
        public final ModConfigSpec.IntValue maxAnchoredWorkbaysPerPlayer;
        public final ModConfigSpec.IntValue maxAnchoredRoomsPerNetwork;
        public final ModConfigSpec.IntValue anchorGraceMinutes;
        public final ModConfigSpec.IntValue maxBaysPerWorkbay;
        public final ModConfigSpec.IntValue maxNetworksPerPlayer;
        public final ModConfigSpec.IntValue maxDeployedWorkbaysPerNetwork;

        public final ModConfigSpec.IntValue linkDefaultRate;
        public final ModConfigSpec.IntValue linkMaxRate;
        public final ModConfigSpec.IntValue linkDefaultSpeed;
        public final ModConfigSpec.IntValue impellerStep;
        public final ModConfigSpec.IntValue maxImpellers;

        public final ModConfigSpec.IntValue itemsPerLevy;
        public final ModConfigSpec.IntValue levyConvertTicks;
        public final ModConfigSpec.IntValue maxSkimPercent;
        public final ModConfigSpec.IntValue skimStepPercent;

        public final ModConfigSpec.IntValue expansionPlateCost;
        public final ModConfigSpec.IntValue expansionPlateCostStep;
        public final ModConfigSpec.IntValue resonatorCost;
        public final ModConfigSpec.IntValue resonatorCostStep;
        public final ModConfigSpec.IntValue multichannelCost;
        public final ModConfigSpec.IntValue multichannelCostStep;
        public final ModConfigSpec.IntValue impellerCost;
        public final ModConfigSpec.IntValue impellerCostStep;
        public final ModConfigSpec.IntValue roomFrameCost;
        public final ModConfigSpec.IntValue wideRoomFrameCost;
        public final ModConfigSpec.IntValue vastRoomFrameCost;
        public final ModConfigSpec.IntValue annexPlateCost;
        public final ModConfigSpec.IntValue annexPlateCostStep;
        public final ModConfigSpec.IntValue anchorCost;

        Server(ModConfigSpec.Builder builder) {
            allowAnchors = builder
                .comment("Controls whether the Anchor upgrade does anything. With this disabled the",
                    "Anchor can still be crafted and installed, but no chunk is ever force-loaded.")
                .define("allowAnchors", true);

            maxAnchoredWorkbaysPerPlayer = builder
                .comment("How many of one player's Workbays may force-load at once. Further Anchors",
                    "install but stay inactive.")
                .defineInRange("maxAnchoredWorkbaysPerPlayer", 4, 0, 1024);

            maxAnchoredRoomsPerNetwork = builder
                .comment("How many of a network's rooms may be anchored at once.",
                    "This is the number a host actually pays: an occupied room costs no ticket at",
                    "all, and an anchored one holds 1, 4 or 9 chunks depending on its Room Frame.",
                    "One anchored vast room is nine ticking chunks; four of them is thirty-six.")
                .defineInRange("maxAnchoredRoomsPerNetwork", 1, 0, 64);

            anchorGraceMinutes = builder
                .comment("How long a player's anchored Workbays keep running after they log out.")
                .defineInRange("anchorGraceMinutes", 5, 0, 1440);

            // Also the length of the Expansion Plate ladder: the plate's maximum is this minus the
            // two bays a base Workbay already grants (WorkbayUpgrade.EXPANSION_PLATE). One number,
            // so the ceiling and the ladder cannot disagree about where the top is. Eight is where
            // the bay rack stops fitting the screen and where BayGeometry's Y spacing was baked
            // into saved worlds, so raising it past eight is a rack and geometry job, not an edit
            // here -- which is exactly why the number below it is the one worth measuring.
            maxBaysPerWorkbay = builder
                .comment("Hard ceiling on bays, regardless of Expansion Plates installed. The base",
                    "Workbay grants two, so this also sets how many Expansion Plates can be",
                    "installed: this minus two.")
                .defineInRange("maxBaysPerWorkbay", 8, com.neryos.workbay.world.WorkbayRecord.BASE_BAYS,
                    com.neryos.workbay.world.BayGeometry.MAX_BAYS);

            maxNetworksPerPlayer = builder
                .comment("How many separate Workbay networks one player may own. An unbound Workbay",
                    "item placed once a player already owns this many mints no new one and refuses.")
                .defineInRange("maxNetworksPerPlayer", 1, 1, 64);

            maxDeployedWorkbaysPerNetwork = builder
                .comment("How many Workbay blocks may be bound to one network at once. Placing an",
                    "unbound item reuses the player's existing network's bays, upgrades and links -",
                    "there is no code to lose - but only up to this many live at the same time.",
                    "Raising this above 1 is not fully load-bearing yet: each deployed Workbay still",
                    "keeps its own energy buffer and its own bus tick timing rather than sharing one,",
                    "so two entrances to the same network do not yet split one energy pool or agree",
                    "on redstone/pulse timing. Left adjustable for packs that accept that; default 1",
                    "sidesteps it entirely.")
                .defineInRange("maxDeployedWorkbaysPerNetwork", 1, 1, 64);

            // ------------------------------------------------------------------ throughput
            //
            // How hard a link pushes. The ceiling is a separate knob from the default on purpose:
            // lowering the default only slows links nobody has touched, and lowering the ceiling
            // slows the ones somebody deliberately turned up.
            builder.push("throughput");

            linkDefaultRate = builder
                .comment("What one move of a new link is worth. The unit is per resource: one for",
                    "an item, 100mB for a fluid, 1000 FE for energy - so a link is born able to",
                    "feed a furnace and unable to keep up with a Mekanism machine, which is the",
                    "shape of the Impeller ladder.")
                .defineInRange("linkDefaultRate", 8, 1, 100_000);

            // Enforced in BusRunner#rate, on every move, rather than where a rate is stored: a
            // ceiling checked only where somebody sets a number is one that a saved world, a
            // pasted config or an older version of this mod walks straight past.
            linkMaxRate = builder
                .comment("The ceiling on one link's rate, before Impellers. Applied on every move,",
                    "so lowering it slows links that are already running.")
                .defineInRange("linkMaxRate", 64, 1, 1_000_000);

            linkDefaultSpeed = builder
                .comment("Ticks between moves for a new link. Must divide 1200, the transfer",
                    "wheel; the screen only ever offers 10, 20, 40, 60, 100 and 200.")
                .defineInRange("linkDefaultSpeed", 20, 5, 1200);

            impellerStep = builder
                .comment("What one Impeller multiplies a link by. It doubles the pile and halves",
                    "the wait, so one level is worth this squared.")
                .defineInRange("impellerStep", 2, 1, 16);

            maxImpellers = builder
                .comment("How many Impellers one Workbay may hold. At the shipped two, an item",
                    "link tops out level with EnderIO's enhanced conduit and an energy link just",
                    "under its plain one - a mod that sells space rather than throughput has no",
                    "business beating a cable mod at cables. Raise it if your pack disagrees.")
                .defineInRange("maxImpellers", 2, 0, 16);

            builder.pop();

            // ------------------------------------------------------------------ the Levy
            builder.push("levy");

            itemsPerLevy = builder
                .comment("How many skimmed items make one Levy. Every item counts the same; what",
                    "goes into the pot at all is #workbay:levy_input, which is a tag and belongs",
                    "to your datapack rather than to this file.")
                .defineInRange("itemsPerLevy", 64, 1, 4096);

            levyConvertTicks = builder
                .comment("How long a racked Assay takes to turn one full batch into one Levy.")
                .defineInRange("levyConvertTicks", 200, 1, 24000);

            maxSkimPercent = builder
                .comment("The top of the skim dial. The mod's whole income is behind this number",
                    "and what it costs is throughput the player gave up, so it is the first thing",
                    "to move when the upgrade ladder feels too long or too short.")
                .defineInRange("maxSkimPercent", 25, 0, 100);

            skimStepPercent = builder
                .comment("How far one click of the skim dial moves it.")
                .defineInRange("skimStepPercent", 5, 1, 100);

            builder.pop();

            // ------------------------------------------------------------- the upgrade ladder
            //
            // Two numbers per upgrade, because SPEC.md section 1 asks for a *rising* cost and a
            // recipe cannot express one. Written out one by one rather than looped over
            // WorkbayUpgrade.values(): this class is built in a static initialiser and that enum
            // reads this class back, so iterating it here is a class-init cycle waiting for
            // whoever reorders two lines.
            builder.comment("What installing the Nth of an upgrade costs in Levy: cost + step * N.",
                    "The crafting half of the same ladder is in your datapack",
                    "(data/workbay/recipe); this is the half a recipe cannot say, because a recipe",
                    "costs the same the tenth time as the first.")
                .push("upgradeCosts");

            expansionPlateCost = builder.defineInRange("expansionPlateCost", 2, 0, 100_000);
            expansionPlateCostStep = builder.defineInRange("expansionPlateCostStep", 6, 0, 100_000);
            resonatorCost = builder.defineInRange("resonatorCost", 24, 0, 100_000);
            resonatorCostStep = builder.defineInRange("resonatorCostStep", 0, 0, 100_000);
            multichannelCost = builder.defineInRange("multichannelCost", 24, 0, 100_000);
            multichannelCostStep = builder.defineInRange("multichannelCostStep", 0, 0, 100_000);
            impellerCost = builder.defineInRange("impellerCost", 12, 0, 100_000);
            impellerCostStep = builder.defineInRange("impellerCostStep", 12, 0, 100_000);
            // No step on the Frames: only one is ever installed, the highest wins, and a step on a
            // one-off is a knob that can never be read. The steepness is in the gap between them.
            roomFrameCost = builder.defineInRange("roomFrameCost", 40, 0, 100_000);
            wideRoomFrameCost = builder.defineInRange("wideRoomFrameCost", 120, 0, 100_000);
            vastRoomFrameCost = builder.defineInRange("vastRoomFrameCost", 300, 0, 100_000);
            annexPlateCost = builder.defineInRange("annexPlateCost", 30, 0, 100_000);
            annexPlateCostStep = builder.defineInRange("annexPlateCostStep", 30, 0, 100_000);
            anchorCost = builder.defineInRange("anchorCost", 200, 0, 100_000);

            builder.pop();
        }
    }

    /** Rendering only, never gameplay. */
    public static class Client {
        public final ModConfigSpec.BooleanValue showHostabilityInTooltips;
        public final ModConfigSpec.BooleanValue renderMiniatures;

        Client(ModConfigSpec.Builder builder) {
            showHostabilityInTooltips = builder
                .comment("Adds one line to every block's tooltip saying whether a Workbay can host it.",
                    "The single most useful teaching moment in the mod, and intrusive to some packs.")
                .define("showHostabilityInTooltips", true);

            renderMiniatures = builder
                .comment("Draws a miniature of each hosted machine in the Workbay's display case.")
                .define("renderMiniatures", true);
        }
    }
}
