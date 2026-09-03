package com.neryos.workbay.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Six knobs. SPEC.md §13.
 *
 * <p>Everything else is a tag or a recipe, because that is where pack authors already work and it
 * needs no config file at all: what can be hosted, what counts as Levy input, the maximum tax rate,
 * every recipe cost and every room size. A pack with a mod we have never seen can fix a bad
 * interaction without waiting for us.
 *
 * <p>All four are {@code SERVER}: the only type synced to clients and the only one overridable
 * per-world under {@code saves/<world>/serverconfig}. Never {@code STARTUP} — the Anchor is always
 * registered and only its <em>behaviour</em> is gated, because gating registration desyncs
 * registries between a server and its clients.
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
        public final ModConfigSpec.IntValue anchorGraceMinutes;
        public final ModConfigSpec.IntValue maxBaysPerWorkbay;
        public final ModConfigSpec.IntValue maxNetworksPerPlayer;
        public final ModConfigSpec.IntValue maxDeployedWorkbaysPerNetwork;

        Server(ModConfigSpec.Builder builder) {
            allowAnchors = builder
                .comment("Controls whether the Anchor upgrade does anything. With this disabled the",
                    "Anchor can still be crafted and installed, but no chunk is ever force-loaded.")
                .define("allowAnchors", true);

            maxAnchoredWorkbaysPerPlayer = builder
                .comment("How many of one player's Workbays may force-load at once. Further Anchors",
                    "install but stay inactive.")
                .defineInRange("maxAnchoredWorkbaysPerPlayer", 4, 0, 1024);

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
