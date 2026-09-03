package com.neryos.workbay.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Four knobs. SPEC.md §13.
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

            maxBaysPerWorkbay = builder
                .comment("Hard ceiling on bays, regardless of Expansion Plates installed.")
                .defineInRange("maxBaysPerWorkbay", 8, 1, 8);
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
