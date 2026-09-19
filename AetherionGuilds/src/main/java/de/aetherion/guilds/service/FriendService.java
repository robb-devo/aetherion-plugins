package de.aetherion.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FriendService {

    private static final long REQUEST_MS = 1000L * 60 * 10;
    private static final int MAX_FRIENDS = 50;
    private static final long VISIT_COOLDOWN_MS = 3000L;

    private final JavaPlugin plugin;
    private final PersonalIslandService personal;
    private final File file;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Long> visitCooldown = new ConcurrentHashMap<>();

    public FriendService(JavaPlugin plugin, PersonalIslandService personal) {
        this.plugin = plugin;
        this.personal = personal;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        load();
    }

    public List<UUID> friendsOf(UUID playerId) {
        return new ArrayList<>(friends.getOrDefault(playerId, Set.of()));
    }

    public boolean areFriends(UUID a, UUID b) {
        return a != null && b != null && friends.getOrDefault(a, Set.of()).contains(b);
    }

    public List<UUID> incomingOf(UUID playerId) {
        Map<UUID, Long> pending = incoming.get(playerId);
        if (pending == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<UUID> ids = new ArrayList<>();
        pending.entrySet().removeIf(entry -> entry.getValue() < now);
        ids.addAll(pending.keySet());
        return ids;
    }

    public void request(Player sender, Player target) {
        if (sender == null || target == null) {
            if (sender != null) {
                sender.sendMessage("§cPlayer not found.");
            }
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage("§cYou cannot add yourself.");
            return;
        }
        if (areFriends(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cYou are already friends.");
            return;
        }
        if (friendsOf(sender.getUniqueId()).size() >= MAX_FRIENDS) {
            sender.sendMessage("§cFriend list is full (" + MAX_FRIENDS + ").");
            return;
        }
        Map<UUID, Long> theirs = incoming.computeIfAbsent(target.getUniqueId(), key -> new ConcurrentHashMap<>());
        theirs.put(sender.getUniqueId(), System.currentTimeMillis() + REQUEST_MS);
        save();
        sender.sendMessage("§aFriend request sent to §f" + target.getName() + "§a.");
        target.sendMessage("§b" + sender.getName() + " §7wants to be friends.");
        target.sendMessage("§7Use §f/friend accept " + sender.getName() + " §7or §f/friend deny " + sender.getName() + "§7.");
    }

    public void accept(Player player, String name) {
        Player sender = Bukkit.getPlayerExact(name);
        UUID otherId = sender != null ? sender.getUniqueId() : matchIncoming(player.getUniqueId(), name);
        if (otherId == null) {
            player.sendMessage("§cNo request from that player.");
            return;
        }
        Map<UUID, Long> pending = incoming.get(player.getUniqueId());
        if (pending == null || pending.remove(otherId) == null) {
            player.sendMessage("§cThat request expired.");
            return;
        }
        addPair(player.getUniqueId(), otherId);
        save();
        String otherName = sender != null ? sender.getName() : name;
        player.sendMessage("§aYou are now friends with §f" + otherName + "§a.");
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§a" + player.getName() + " §7accepted your friend request.");
        }
    }

    public void deny(Player player, String name) {
        Player sender = Bukkit.getPlayerExact(name);
        UUID otherId = sender != null ? sender.getUniqueId() : matchIncoming(player.getUniqueId(), name);
        Map<UUID, Long> pending = incoming.get(player.getUniqueId());
        if (otherId == null || pending == null || pending.remove(otherId) == null) {
            player.sendMessage("§cNo request from that player.");
            return;
        }
        save();
        player.sendMessage("§7Request declined.");
    }

    public void remove(Player player, String name) {
        Player other = Bukkit.getPlayerExact(name);
        UUID otherId = other != null ? other.getUniqueId() : findFriendByName(player.getUniqueId(), name);
        if (otherId == null || !areFriends(player.getUniqueId(), otherId)) {
            player.sendMessage("§cThat player is not on your friend list.");
            return;
        }
        friends.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet()).remove(otherId);
        friends.computeIfAbsent(otherId, key -> ConcurrentHashMap.newKeySet()).remove(player.getUniqueId());
        save();
        player.sendMessage("§7Removed §f" + name + " §7from friends.");
        if (other != null && other.isOnline()) {
            other.sendMessage("§c" + player.getName() + " §7removed you as a friend.");
        }
    }

    public void visit(Player player, String name) {
        if (player == null || name == null || name.isBlank()) {
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        UUID targetId = target != null ? target.getUniqueId() : findFriendByName(player.getUniqueId(), name);
        if (targetId == null) {
            player.sendMessage("§cThat player is not online.");
            return;
        }
        if (player.getUniqueId().equals(targetId)) {
            player.sendMessage("§cYou are already here.");
            return;
        }
        if (!areFriends(player.getUniqueId(), targetId)) {
            player.sendMessage("§cYou can only visit friends.");
            return;
        }
        if (isDungeon(player.getWorld().getName()) || (target != null && isDungeon(target.getWorld().getName()))) {
            player.sendMessage("§cYou cannot visit while either of you is in a dungeon.");
            return;
        }
        long now = System.currentTimeMillis();
        Long ready = visitCooldown.get(player.getUniqueId());
        if (ready != null && ready > now) {
            player.sendMessage("§cWait a moment before visiting again.");
            return;
        }
        visitCooldown.put(player.getUniqueId(), now + VISIT_COOLDOWN_MS);

        String display = target != null ? target.getName() : name;
        if (personal != null && personal.byOwner(targetId) != null) {
            personal.visit(player, targetId);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.15f);
            player.sendMessage("§aVisiting §f" + display + "§a's island.");
            if (target != null && target.isOnline()) {
                target.sendMessage("§b" + player.getName() + " §7is visiting your island.");
            }
            return;
        }
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cThat player has no island and is offline.");
            return;
        }
        player.teleport(target.getLocation());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.15f);
        player.sendMessage("§aVisiting §f" + target.getName() + "§a.");
        target.sendMessage("§b" + player.getName() + " §7is visiting you.");
    }

    private static boolean isDungeon(String worldName) {
        return worldName != null && worldName.startsWith("aedun_");
    }

    public void notifyJoin(Player player) {
        List<UUID> waiting = incomingOf(player.getUniqueId());
        if (!waiting.isEmpty()) {
            player.sendMessage("§bYou have §f" + waiting.size() + " §bfriend request(s). §7/friend list");
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        friends.forEach((id, set) -> {
            List<String> values = new ArrayList<>();
            set.forEach(friend -> values.add(friend.toString()));
            config.set("players." + id + ".friends", values);
        });
        incoming.forEach((id, map) -> map.forEach((from, expire) ->
                config.set("players." + id + ".incoming." + from, expire)));
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save friends.yml: " + exception.getMessage());
        }
    }

    private void addPair(UUID a, UUID b) {
        friends.computeIfAbsent(a, key -> ConcurrentHashMap.newKeySet()).add(b);
        friends.computeIfAbsent(b, key -> ConcurrentHashMap.newKeySet()).add(a);
        Map<UUID, Long> otherIncoming = incoming.get(b);
        if (otherIncoming != null) {
            otherIncoming.remove(a);
        }
    }

    private UUID matchIncoming(UUID playerId, String name) {
        if (name == null) {
            return null;
        }
        for (UUID id : incomingOf(playerId)) {
            String found = Bukkit.getOfflinePlayer(id).getName();
            if (found != null && found.equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    private UUID findFriendByName(UUID playerId, String name) {
        if (name == null) {
            return null;
        }
        for (UUID id : friendsOf(playerId)) {
            String found = Bukkit.getOfflinePlayer(id).getName();
            if (found != null && found.equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String friend : config.getStringList("players." + key + ".friends")) {
                try {
                    set.add(UUID.fromString(friend));
                } catch (IllegalArgumentException ignored) {
                }
            }
            friends.put(id, set);
            ConfigurationSection incomingSection = config.getConfigurationSection("players." + key + ".incoming");
            if (incomingSection != null) {
                Map<UUID, Long> map = new ConcurrentHashMap<>();
                for (String from : incomingSection.getKeys(false)) {
                    try {
                        map.put(UUID.fromString(from), incomingSection.getLong(from));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                incoming.put(id, map);
            }
        }
    }
}
