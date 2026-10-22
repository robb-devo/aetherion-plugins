package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Commands that belong to another backend leave for it, so typing {@code /capital} on the isles works like
 * typing it in the capital: the character crosses to mmo-r and the command runs there on arrival.
 *
 * <p>Armed on every backend but the main world ({@code network.routed-commands.everywhere}, default true; the old
 * {@code on} list still adds backends when it is switched off). The built-in labels always count; the config lists
 * only add to them, so an old saved config can't switch a road off.
 * <ul>
 *   <li>mmo-c / mmo-i / mmo-d: main-world travel labels always cross, whoever answers them locally (an Essentials
 *   {@code /spawn} on the exile must not keep people there). Only AetherionDungeons keeps its own labels on mmo-d
 *   (its {@code /spawn} already sends people home through its own pipeline).</li>
 *   <li>hub: only labels nobody answers there cross (the lobby keeps its own warps and {@code /spawn}); every road
 *   from the hub leads to mmo-r first, which forwards {@code /island} & co. itself.</li>
 * </ul>
 */
public final class MainCommandRouter implements Listener {

    private static final List<String> DEFAULT_MAIN = List.of(
            "spawn", "hub", "spawns", "hubs", "spawnmenu",
            "harbour", "harbor", "hafen", "oreridge", "ore_ridge", "mines", "capital", "forage", "forageisle", "farm",
            "farmisle", "borderlands", "colosseum", "eldervale", "mining", "miningisle", "fishing", "fishingisle",
            "summit", "skyreach", "whisperwood", "wilds", "origin", "originisle", "originjournal");
    private static final List<String> DEFAULT_DUNGEON = List.of("dhub", "dungeonhub", "dh");
    /**
     * Global economy lives on the main world only (one market.yml). These leave even though Items answers
     * them locally — a second Bazaar / Auction House per backend would split (and could duplicate) stock.
     */
    private static final List<String> DEFAULT_FORCE_MAIN = List.of(
            "bazaar", "bz", "aetherbazaar", "ah", "auction", "auctionhouse", "aetherah");
    /** From the hub only: island commands go to mmo-r first, whose island link carries on to the isles. */
    private static final List<String> HUB_ISLANDS = List.of(
            "island", "is", "insel", "guild", "g", "gilde", "friend", "friends", "freund", "freunde");
    /** Spellings players type that the owning plugin may not know: replayed as the label it does. */
    private static final Map<String, String> CANONICAL = Map.of("harbor", "harbour", "hafen", "harbour");
    private static final Set<String> FOREIGN_OWNERS = Set.of("AetherionHub", "AetherionDungeons");

    private final AetherionCore plugin;
    private final NetworkRouter router;
    private final Map<String, String> labelToRole = new HashMap<>();
    private final Set<String> forced = new HashSet<>();
    private final Set<String> hubOnly = new HashSet<>();

    public MainCommandRouter(AetherionCore plugin, NetworkRouter router) {
        this.plugin = plugin;
        this.router = router;
        var cfg = plugin.getConfig();
        String base = "network.routed-commands.";
        for (String label : DEFAULT_MAIN) {
            put(label, "main");
        }
        for (String label : cfg.getStringList(base + "main")) {
            put(label, "main");
        }
        for (String label : DEFAULT_DUNGEON) {
            put(label, "dungeon");
        }
        for (String label : cfg.getStringList(base + "dungeon")) {
            put(label, "dungeon");
        }
        List<String> market = new java.util.ArrayList<>(DEFAULT_FORCE_MAIN);
        if (cfg.isSet(base + "force-main")) {
            // an explicit empty list keeps a local market (documented opt-out); otherwise defaults + additions
            List<String> configured = cfg.getStringList(base + "force-main");
            if (configured.isEmpty()) {
                market.clear();
            } else {
                market.addAll(configured);
            }
        }
        for (String label : market) {
            put(label, "main");
            if (label != null && !label.isBlank()) {
                forced.add(label.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String label : HUB_ISLANDS) {
            hubOnly.add(label);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[Route] command routing " + (armedHere() ? "armed on " + router.here()
                + " (" + labelToRole.size() + " labels)" : "off on " + router.here()));
    }

    private void put(String label, String role) {
        if (label != null && !label.isBlank()) {
            labelToRole.put(label.trim().toLowerCase(Locale.ROOT), role);
        }
    }

    public boolean armedHere() {
        return router.routesCommandsHere();
    }

    /** LOW + ignoreCancelled: the transfer freeze and the LOCKED shutdown countdown (LOWEST) win first. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '/' || !armedHere()) {
            return;
        }
        String line = raw.substring(1).trim();
        if (line.isEmpty()) {
            return;
        }
        String first = line.split("\\s+")[0].toLowerCase(Locale.ROOT);
        String label = first.contains(":") ? first.substring(first.indexOf(':') + 1) : first;
        String here = router.here();
        boolean hub = ServerNames.isHub(here);
        String role = labelToRole.get(label);
        if (role == null && hub && hubOnly.contains(label)) {
            role = "main";
        }
        if (role == null) {
            return;
        }
        PluginCommand local = Bukkit.getPluginCommand(label);
        String owner = local == null ? null : local.getPlugin().getName();
        if (owner != null && !forced.contains(label)) {
            if (hub) {
                return; // the lobby answers its own warps
            }
            if (ServerNames.isDungeon(here) && "AetherionDungeons".equals(owner)) {
                return; // Dungeons' /spawn /hub /dhub on mmo-d
            }
            if (!"main".equals(role) && !FOREIGN_OWNERS.contains(owner)) {
                return;
            }
        }
        // From the hub every road runs through the main world (the character lives there, not on the hub).
        String target = hub ? router.serverFor("main") : router.serverFor(role);
        if (target.isBlank() || target.equalsIgnoreCase(here)) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        // Replay without the namespace, as the label the owning plugin on the target answers.
        String replay = CANONICAL.getOrDefault(label, label) + line.substring(first.length());
        if (!router.travel(player, target, RouteBook.Kind.COMMAND, replay, null, "command:" + label)) {
            player.sendMessage("§7That road is closed right now — try again in a moment.");
        }
    }
}
