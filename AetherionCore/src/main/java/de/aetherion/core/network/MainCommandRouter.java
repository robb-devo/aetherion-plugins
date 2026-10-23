package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;

import com.mojang.brigadier.arguments.StringArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 *
 * <p>Each label this backend actually takes is a real command that reports success. A label that is only
 * cancelled in {@link PlayerCommandPreprocessEvent} is not in the client's command tree, so the line is drawn
 * in the error color even though the player still arrives. The hop itself waits one tick so that success
 * reaches the client before Velocity drops this backend.
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
    private static final Set<String> FOREIGN_OWNERS = Set.of("AetherionHub", "AetherionDungeons");

    private final AetherionCore plugin;
    private final NetworkRouter router;
    private final Map<String, String> labelToRole = new HashMap<>();
    private final Set<String> forced = new HashSet<>();
    private final Set<String> hubOnly = new HashSet<>();
    /** Labels whose Brigadier node is the one the client will run. Preprocess must not cancel these. */
    private final Set<String> published = new HashSet<>();

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
        // Late, so plugin.yml commands already exist and a label we take can replace the node the client sees.
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS
                .newHandler(event -> publish(event.registrar()))
                .priority(1_000));
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

    private void publish(Commands commands) {
        published.clear();
        if (!armedHere()) {
            return;
        }
        int wanted = 0;
        for (String label : candidates()) {
            if (!shouldPublish(label)) {
                continue;
            }
            wanted++;
            String name = label;
            try {
                var node = Commands.literal(name)
                        .executes(ctx -> run(ctx.getSource().getSender(), name, ""))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx.getSource().getSender(), name,
                                        StringArgumentType.getString(ctx, "args"))))
                        .build();
                if (registeredRoot(commands.register(node, "Teleport"), name)) {
                    published.add(name);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[Route] /" + name + " stayed on the old path: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("[Route] " + published.size() + "/" + wanted
                + " teleport commands report success on " + router.here());
    }

    private Set<String> candidates() {
        Set<String> labels = new LinkedHashSet<>(labelToRole.keySet());
        if (ServerNames.isHub(router.here())) {
            labels.addAll(hubOnly);
        }
        return labels;
    }

    private boolean shouldPublish(String label) {
        if (targetOf(label) == null) {
            return false;
        }
        return TravelCommandRules.publish(true, true, steals(label));
    }

    private boolean steals(String label) {
        String owner = ownerPlugin(label);
        boolean hub = ServerNames.isHub(router.here());
        boolean dungeonKeeps = ServerNames.isDungeon(router.here()) && "AetherionDungeons".equals(owner);
        return TravelCommandRules.steals(owner != null, forced.contains(label), hub, dungeonKeeps,
                "main".equals(roleOf(label)), owner != null && FOREIGN_OWNERS.contains(owner));
    }

    /** Plugin.yml owner, matching the old preprocess check. Anything else counts as free. */
    private String ownerPlugin(String label) {
        PluginCommand local = Bukkit.getPluginCommand(label);
        return local == null ? null : local.getPlugin().getName();
    }

    private String roleOf(String label) {
        String role = labelToRole.get(label);
        if (role == null && ServerNames.isHub(router.here()) && hubOnly.contains(label)) {
            return "main";
        }
        return role;
    }

    /** Destination, or null when this backend does not take the label. */
    private String targetOf(String label) {
        if (!armedHere()) {
            return null;
        }
        String role = roleOf(label);
        if (role == null) {
            return null;
        }
        String here = router.here();
        String target = ServerNames.isHub(here) ? router.serverFor("main") : router.serverFor(role);
        if (target == null || target.isBlank() || target.equalsIgnoreCase(here)) {
            return null;
        }
        return target;
    }

    private static boolean registeredRoot(Set<String> registered, String label) {
        if (registered == null) {
            return false;
        }
        for (String name : registered) {
            if (label.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Success goes out on this tick. The connect is the next tick, so the client records the command
     * before this backend disconnects it.
     */
    private int run(CommandSender sender, String label, String args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§7Players only.");
            return TravelCommandRules.SUCCESS;
        }
        String target = targetOf(label);
        if (target == null) {
            return TravelCommandRules.SUCCESS;
        }
        String suffix = args == null || args.isBlank() ? "" : " " + args;
        String replay = TravelCommandRules.replay(label, suffix);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!router.travel(player, target, RouteBook.Kind.COMMAND, replay, null, "command:" + label)) {
                player.sendMessage("§7That road is closed right now — try again in a moment.");
            }
        });
        return TravelCommandRules.SUCCESS;
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
        String token = line.split("\\s+", 2)[0];
        String label = TravelCommandRules.bareLabel(token);
        if (label.isEmpty() || roleOf(label) == null) {
            return;
        }
        String suffix = line.substring(token.length());
        if (published.contains(label)) {
            // Let the registered command report success. Lowercase so /Capital still matches the node.
            String normalized = "/" + label + suffix;
            if (!normalized.equals(raw)) {
                event.setMessage(normalized);
            }
            return;
        }
        if (!steals(label)) {
            return;
        }
        String target = targetOf(label);
        if (target == null) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        String replay = TravelCommandRules.replay(label, suffix);
        if (!router.travel(player, target, RouteBook.Kind.COMMAND, replay, null, "command:" + label)) {
            player.sendMessage("§7That road is closed right now — try again in a moment.");
        }
    }
}
