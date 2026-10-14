package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ProgressAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /island}, {@code /guild}, {@code /friend} on every character backend that does not run
 * AetherionGuilds itself (mmo-r after the migration, mmo-c, mmo-d): the player crosses to the island
 * backend (mmo-i) and the command runs there on arrival — home, menu, build tool, visit, all of it.
 *
 * <p>Registered at server load, only for labels nobody else took. Never on mmo-i (Guilds answers there)
 * and never on the hub. The Items manager buttons call the same commands.
 */
public final class IslandLink implements Listener {

    private static volatile boolean remote;

    private final AetherionCore plugin;
    private final NetworkRouter router;
    private final List<Command> registered = new ArrayList<>();
    private final Set<UUID> nudged = ConcurrentHashMap.newKeySet();
    private final File nudgeFile;
    private boolean nudgeDirty;

    public IslandLink(AetherionCore plugin, NetworkRouter router) {
        this.plugin = plugin;
        this.router = router;
        this.nudgeFile = new File(plugin.getDataFolder(), "island-nudges.yml");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** True when this backend sends {@code /island} & co. to the island backend (Items asks before greying buttons). */
    public static boolean remoteAvailable() {
        return remote;
    }

    public String islandsServer() {
        return router.serverFor("islands");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (!registered.isEmpty()) {
            return;
        }
        String here = router.here();
        if (!plugin.getConfig().getBoolean("network.islands.route-commands", true)) {
            plugin.getLogger().info("[Isles] /island routing off by config.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("AetherionGuilds") != null) {
            plugin.getLogger().info("[Isles] AetherionGuilds runs on " + here + " — /island stays local.");
            return;
        }
        if (ServerNames.isIslands(here) || ServerNames.isHub(here) || !ServerNames.isCharacterBackend(here)) {
            return;
        }
        CommandMap map = Bukkit.getCommandMap();
        register(map, new Shim("island", "Your island (crosses to the Isles)", List.of("is", "insel", "aetherisland"),
                List.of("create", "home", "menu", "build", "tool", "production", "land", "belts", "tier", "guide", "done")));
        register(map, new Shim("guild", "Your guild (crosses to the Isles)", List.of("g", "gilde", "aetherguild"),
                List.of("create", "invite", "accept", "deny", "leave", "kick", "rank", "home", "project", "build",
                        "land", "belts", "disband", "menu", "quarry")));
        register(map, new Shim("friend", "Friends and island visits (crosses to the Isles)", List.of("friends", "freund", "freunde"),
                List.of("add", "accept", "deny", "remove", "visit", "list")));
        remote = !registered.isEmpty();
        plugin.getLogger().info("[Isles] " + here + " routes /island /guild /friend → " + islandsServer()
                + " (" + registered.size() + " command(s)).");
        if (remote && ServerNames.isMain(here) && plugin.getConfig().getBoolean("network.islands.nudge", true)) {
            loadNudges();
            Bukkit.getScheduler().runTaskTimer(plugin, this::nudgeTick, 20L * 30, 20L * 30);
        }
    }

    private void register(CommandMap map, Shim shim) {
        if (map.getCommand(shim.getName()) != null) {
            plugin.getLogger().warning("[Isles] /" + shim.getName() + " is taken by another plugin here — not routed.");
            return;
        }
        List<String> free = new ArrayList<>();
        for (String alias : shim.wanted) {
            if (map.getCommand(alias) == null) {
                free.add(alias);
            }
        }
        shim.setAliases(free);
        map.register("aetherioncore", shim);
        registered.add(shim);
    }

    /** Best effort on disable (Paper exposes the known-command map). */
    public void unregister() {
        if (registered.isEmpty()) {
            return;
        }
        try {
            var known = Bukkit.getCommandMap().getKnownCommands();
            for (Command command : registered) {
                known.values().removeIf(value -> value == command);
                command.unregister(Bukkit.getCommandMap());
            }
        } catch (RuntimeException ignored) {
            // shutting down anyway
        }
        registered.clear();
        remote = false;
        saveNudges();
    }

    private boolean gate(Player player, String label) {
        if (player.hasPermission("aetherion.guild.admin")) {
            return true;
        }
        ProgressAccess progress = AetherServices.progress();
        if (progress == null) {
            return true;
        }
        try {
            if (label.equals("island") && !progress.island(player)) {
                player.sendMessage(progress.islandHint());
                return false;
            }
            if (label.equals("guild") && !progress.guild(player)) {
                player.sendMessage(progress.guildHint());
                return false;
            }
        } catch (RuntimeException ex) {
            return true;
        }
        return true;
    }

    private final class Shim extends Command {

        private final List<String> wanted;
        private final List<String> subs;

        Shim(String name, String description, List<String> aliases, List<String> subs) {
            super(name, description, "/" + name, List.of());
            this.wanted = aliases;
            this.subs = subs;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            String canonical = getName();
            if (!(args.length > 0 && args[0].equalsIgnoreCase("admin")) && !gate(player, canonical)) {
                return true;
            }
            if (nudged.add(player.getUniqueId())) {
                nudgeDirty = true;
            }
            String replay = canonical + (args.length == 0 ? "" : " " + String.join(" ", args));
            String target = islandsServer();
            if (!router.travel(player, target, RouteBook.Kind.COMMAND, replay, NetworkRouter.DEPART_ISLANDS, canonical)) {
                player.sendMessage("§7The isles are out of reach right now — try again in a moment.");
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                return subs.stream().filter(sub -> sub.startsWith(prefix)).toList();
            }
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            return List.of();
        }
    }

    // ------------------------------------------------------------------ one-time nudge (main world)

    /**
     * Guilds' unlock beat (title, chime, starter pick) plays on the isles at the first visit. On the main
     * world, where people level, a single clickable line points there once the island gate opens.
     */
    private void nudgeTick() {
        ProgressAccess progress = AetherServices.progress();
        if (progress == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (nudged.contains(id)) {
                continue;
            }
            boolean open;
            try {
                open = progress.island(player);
            } catch (RuntimeException ex) {
                continue;
            }
            if (!open) {
                continue;
            }
            nudged.add(id);
            nudgeDirty = true;
            Component line = LegacyComponentSerializer.legacySection()
                    .deserialize("§a✦ §7A patch of sky is yours to claim. §a§n[Sail to your island]")
                    .clickEvent(ClickEvent.runCommand("/island"))
                    .hoverEvent(HoverEvent.showText(Component.text("/island")));
            player.sendMessage(line);
        }
        if (nudgeDirty) {
            saveNudges();
        }
    }

    private void loadNudges() {
        if (!nudgeFile.isFile()) {
            return;
        }
        for (String raw : YamlConfiguration.loadConfiguration(nudgeFile).getStringList("nudged")) {
            try {
                nudged.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    private void saveNudges() {
        if (!nudgeDirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : nudged) {
            ids.add(id.toString());
        }
        yaml.set("nudged", ids);
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, nudgeFile, plugin.getLogger());
            nudgeDirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("[Isles] Could not save island-nudges.yml: " + ex.getMessage());
        }
    }
}
