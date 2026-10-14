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
 * <p>Armed only on the backends listed in {@code network.routed-commands.on} (default mmo-c, mmo-i).
 * A label a local plugin owns (other than AetherionHub / AetherionDungeons, whose worlds live elsewhere)
 * stays local — except the {@code force-main} economy labels (Bazaar / Auction House live on mmo-r only).
 */
public final class MainCommandRouter implements Listener {

    private static final List<String> DEFAULT_MAIN = List.of(
            "spawn", "hub", "spawns", "hubs", "spawnmenu",
            "harbour", "oreridge", "ore_ridge", "mines", "capital", "forage", "forageisle", "farm", "farmisle",
            "borderlands", "colosseum", "eldervale", "mining", "miningisle", "fishing", "fishingisle",
            "summit", "skyreach", "whisperwood", "wilds", "origin", "originisle", "originjournal");
    private static final List<String> DEFAULT_DUNGEON = List.of("dhub", "dungeonhub", "dh");
    /**
     * Global economy lives on the main world only (one market.yml). These leave even though Items answers
     * them locally — a second Bazaar / Auction House per backend would split (and could duplicate) stock.
     */
    private static final List<String> DEFAULT_FORCE_MAIN = List.of(
            "bazaar", "bz", "aetherbazaar", "ah", "auction", "auctionhouse", "aetherah");
    private static final Set<String> FOREIGN_OWNERS = Set.of("AetherionHub", "AetherionDungeons");

    private final AetherionCore plugin;
    private final NetworkRouter router;
    private final Map<String, String> labelToRole = new HashMap<>();
    private final Set<String> forced = new HashSet<>();

    public MainCommandRouter(AetherionCore plugin, NetworkRouter router) {
        this.plugin = plugin;
        this.router = router;
        var cfg = plugin.getConfig();
        String base = "network.routed-commands.";
        for (String label : cfg.isSet(base + "main") ? cfg.getStringList(base + "main") : DEFAULT_MAIN) {
            put(label, "main");
        }
        for (String label : cfg.isSet(base + "dungeon") ? cfg.getStringList(base + "dungeon") : DEFAULT_DUNGEON) {
            put(label, "dungeon");
        }
        for (String label : cfg.isSet(base + "force-main") ? cfg.getStringList(base + "force-main") : DEFAULT_FORCE_MAIN) {
            put(label, "main");
            if (label != null && !label.isBlank()) {
                forced.add(label.trim().toLowerCase(Locale.ROOT));
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        String role = labelToRole.get(label);
        if (role == null) {
            return;
        }
        PluginCommand local = Bukkit.getPluginCommand(label);
        if (local != null && !forced.contains(label) && !FOREIGN_OWNERS.contains(local.getPlugin().getName())) {
            return;
        }
        String target = router.serverFor(role);
        if (target.isBlank() || target.equalsIgnoreCase(router.here())) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        // Replay without the namespace: the owning plugin on the target answers the plain label.
        String replay = label + line.substring(first.length());
        if (!router.travel(player, target, RouteBook.Kind.COMMAND, replay, null, "command:" + label)) {
            player.sendMessage("§7That road is closed right now — try again in a moment.");
        }
    }
}
