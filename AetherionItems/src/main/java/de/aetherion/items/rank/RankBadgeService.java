package de.aetherion.items.rank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankBadgeService implements Listener {

    public record Rank(String group, int weight, String prefix, String display) {
    }

    public static final List<Rank> RANKS = List.of(
            new Rank("adventurer", 10, "&8[&7✦&8] &f", "§7Adventurer"),
            new Rank("veteran", 20, "&8[&a⚔&8] &f", "§aVeteran"),
            new Rank("champion", 30, "&8[&9◆&8] &f", "§9Champion"),
            new Rank("legend", 40, "&8[&6✧&8] &f", "§6Legend"),
            new Rank("mythwright", 50, "&8[&d✶&8] &f", "§dMythwright"),
            new Rank("aetherborn", 60, "&8[&5☯&8] &f", "§5§lAetherborn"),
            new Rank("celestine", 70, "&8[&b✧&8] &f", "§bCelestine"),
            new Rank("sovereign", 80, "&8[&e♛&8] &f", "§e§lSovereign"),
            new Rank("ascendant", 82, "&8[&3◈&8] &f", "§3Ascendant"),
            new Rank("empyrean", 84, "&8[&c❖&8] &f", "§c§lEmpyrean"),
            new Rank("eternal", 86, "&8[&4✶&8] &f", "§4§lEternal"),
            new Rank("aetherion", 88, "&8[&5♛&8] &f", "§5§lAetherion"),
            new Rank("mvpplusplus", 90, "&6[MVP&c++&6] &f", "§6MVP§c++"),
            // Ultra extras — high TAB, never parked at 50 next to mythwright.
            // Beta 94 (cosmetic only) · Monkey 95 (content + celestial) · Admin 100.
            new Rank("beta", 94, CelestialDye.betaPrefixStatic(), "§b§lBeta Tester"),
            new Rank("monkey", 95, CelestialDye.monkeyPrefixStatic(), "§d§lMonkey"),
            new Rank("admin", 100, "&c[Admin] &f", "§cAdmin")
    );

    /**
     * Staff / Homie cosmetic extras sit on top of Aetherion XP ranks.
     * Standing rule (Peter): every future Homie special rank copies Monkey —
     * EXTRA + high TAB weight (near admin / mvpplusplus) + Dev Menu ultra slot.
     * Never add them to the Adventurer→Aetherion progression row.
     * <p>
     * Monkey = content tools + celestial dye. Beta = celestial cosmetics only.
     */
    private static final Set<String> EXTRA = Set.of("mvpplusplus", "admin", "monkey", "beta");

    /** LP-fallback extras: do not wipe on XP sync (Dev Menu / {@code /lp parent add}). */
    private static final Set<String> LP_FALLBACK_EXTRAS = Set.of("monkey", "beta");

    private static final String DEFAULT_GROUP = "mvpplusplus";
    private static final String DEFAULT_PREFIX = "&6[MVP&c++&6] ";
    public static final UUID DAVID = UUID.fromString("365ffd9c-009c-4a9d-b0a5-14dea8768dee");

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, String> assigned = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> extras = new ConcurrentHashMap<>();
    private final Set<UUID> forced = ConcurrentHashMap.newKeySet();

    public RankBadgeService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-ranks.yml");
        load();
    }

    public static RankBadgeService apply(JavaPlugin plugin) {
        if (plugin == null) {
            return null;
        }
        RankBadgeService service = new RankBadgeService(plugin);
        plugin.getServer().getPluginManager().registerEvents(service, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            service.syncGroups();
            for (Player player : Bukkit.getOnlinePlayers()) {
                service.applyTo(player);
            }
        }, 40L);
        return service;
    }

    public boolean isMvpPlusPlus(UUID playerId) {
        return playerId != null && mvpPlayers().contains(playerId);
    }

    public boolean hasMvpPlusPlus(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (isMvpPlusPlus(playerId)) {
            return true;
        }
        String stored = extras.get(playerId);
        return "mvpplusplus".equalsIgnoreCase(stored);
    }

    public String mvpPrefix() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks.mvpplusplus");
        String prefix = section == null ? DEFAULT_PREFIX : section.getString("prefix", DEFAULT_PREFIX);
        return ChatColor.translateAlternateColorCodes('&', prefix == null ? DEFAULT_PREFIX : prefix);
    }

    public String mvpGroup() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks.mvpplusplus");
        String group = section == null ? DEFAULT_GROUP : section.getString("group", DEFAULT_GROUP);
        return group == null || group.isBlank() ? DEFAULT_GROUP : group.toLowerCase(Locale.ROOT);
    }

    public Rank rankByGroup(String group) {
        if (group == null || group.isBlank() || group.equalsIgnoreCase("default")) {
            return RANKS.get(0);
        }
        if (group.equalsIgnoreCase("owner")) {
            group = "admin";
        }
        String key = group.toLowerCase(Locale.ROOT);
        for (Rank rank : RANKS) {
            if (rank.group().equals(key)) {
                return rank;
            }
        }
        return RANKS.get(0);
    }

    public boolean isExtra(String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        String key = group.toLowerCase(Locale.ROOT);
        return EXTRA.contains(key) || key.equals("owner");
    }

    public String displayTitle(Player player) {
        return aetherionTitle(player);
    }

    public String aetherionTitle(Player player) {
        if (player == null) {
            return "";
        }
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        int level = items == null || items.getSkills() == null ? 1 : items.getSkills().accountLevel(player);
        return de.aetherion.items.skill.AetherionLevel.coloredTitle(level);
    }

    public String ultraTitle(Player player) {
        Rank extra = extraRank(player);
        if (extra == null) {
            return "";
        }
        if (CelestialDye.isCelestialGroup(extra.group())) {
            return CelestialDye.badgeForGroup(extra.group()).trim();
        }
        return extra.display();
    }

    public boolean hasUltra(Player player) {
        return extraRank(player) != null;
    }

    public String sidebarLine1(Player player) {
        return hasUltra(player) ? ultraTitle(player) : aetherionTitle(player);
    }

    public String sidebarLine2(Player player) {
        return hasUltra(player) ? aetherionTitle(player) : "";
    }

    public String tabPrefix(Player player) {
        if (player == null) {
            return "§f";
        }
        StringBuilder prefix = new StringBuilder();
        Rank extra = extraRank(player);
        if (extra != null) {
            prefix.append(switch (extra.group()) {
                case "admin" -> "§c[Admin] ";
                case "mvpplusplus" -> "§6[MVP§c++§6] ";
                case "monkey", "beta" -> CelestialDye.badgeForGroup(extra.group());
                default -> "";
            });
        }
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        if (items != null && items.getSkills() != null) {
            prefix.append(items.getSkills().accountTag(player)).append(" ");
        }
        prefix.append("§f");
        return prefix.toString();
    }

    public String nametag(Player player) {
        if (player == null) {
            return "";
        }
        return tabPrefix(player) + player.getName();
    }

    public void syncProgression(Player player, int level) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (forced.contains(player.getUniqueId())) {
            applyTo(player);
            return;
        }
        String wanted = de.aetherion.items.skill.AetherionLevel.rankGroup(level);
        Rank current = rankByGroup(aetherionGroup(player.getUniqueId()));
        Rank next = rankByGroup(wanted);
        if (next.weight() >= current.weight() && !next.group().equals(current.group())) {
            setAetherionGroup(player.getUniqueId(), next.group(), false);
        } else {
            applyTo(player);
        }
    }

    public String rankOf(UUID playerId) {
        return aetherionGroup(playerId);
    }

    public String aetherionGroup(UUID playerId) {
        if (playerId == null) {
            return "adventurer";
        }
        String stored = assigned.get(playerId);
        if (stored != null && !stored.isBlank() && !isExtra(stored)) {
            return rankByGroup(stored).group();
        }
        Player online = Bukkit.getPlayer(playerId);
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        if (online != null && items != null && items.getSkills() != null) {
            return de.aetherion.items.skill.AetherionLevel.rankGroup(items.getSkills().accountLevel(online));
        }
        return "adventurer";
    }

    public boolean setRank(UUID playerId, String group) {
        if (playerId == null) {
            return false;
        }
        Rank rank = rankByGroup(group);
        if (isExtra(rank.group())) {
            String previous = extras.get(playerId);
            if (previous != null && !previous.equalsIgnoreCase(rank.group())) {
                LuckPermsSilent.removeGroup(playerId, previous);
            }
            extras.put(playerId, rank.group());
            save();
            applyLuckPerms(playerId, aetherionGroup(playerId), rank);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) {
                paint(online);
            }
            return true;
        }
        forced.add(playerId);
        return setAetherionGroup(playerId, rank.group(), true);
    }

    public void syncToLevel(UUID playerId) {
        if (playerId == null) {
            return;
        }
        forced.remove(playerId);
        Player online = Bukkit.getPlayer(playerId);
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        int level = online != null && items != null && items.getSkills() != null
                ? items.getSkills().accountLevel(online)
                : 1;
        setAetherionGroup(playerId, de.aetherion.items.skill.AetherionLevel.rankGroup(level), false);
    }

    private boolean setAetherionGroup(UUID playerId, String group, boolean persistForce) {
        Rank rank = rankByGroup(group);
        assigned.put(playerId, rank.group());
        if (!persistForce) {
            forced.remove(playerId);
        }
        save();
        applyLuckPerms(playerId, rank.group(), extraFor(playerId));
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            paint(online);
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyTo(player), 40L);
    }

    public void applyTo(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        applyLuckPerms(player.getUniqueId(), aetherionGroup(player.getUniqueId()), extraFor(player.getUniqueId()));
        paint(player);
    }

    public Rank extraRank(Player player) {
        return player == null ? null : extraFor(player.getUniqueId());
    }

    public Rank extraFor(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()
                && (player.isOp()
                || player.hasPermission("group.admin")
                || player.hasPermission("aetherion.rank.admin"))) {
            return rankByGroup("admin");
        }
        String stored = extras.get(playerId);
        if (stored != null && isExtra(stored)) {
            return rankByGroup(stored);
        }
        if (isMvpPlusPlus(playerId)) {
            return rankByGroup(mvpGroup());
        }
        return null;
    }

    /** Remove a staff/content extra (Monkey, MVP++, Admin) without touching XP progression. */
    public boolean clearExtra(UUID playerId, String group) {
        if (playerId == null || group == null || group.isBlank()) {
            return false;
        }
        String key = group.toLowerCase(Locale.ROOT);
        String stored = extras.get(playerId);
        if (stored == null || !stored.equalsIgnoreCase(key)) {
            return false;
        }
        extras.remove(playerId);
        save();
        LuckPermsSilent.removeGroup(playerId, key);
        applyLuckPerms(playerId, aetherionGroup(playerId), extraFor(playerId));
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            paint(online);
        }
        return true;
    }

    private void paint(Player player) {
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer hex =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                        .character('&')
                        .hexColors()
                        .build();
        // Same celestial / ultra nametag for chat displayName and TAB list.
        String raw = nametag(player).replace('§', '&');
        net.kyori.adventure.text.Component name = hex.deserialize(raw);
        player.displayName(name);
        player.playerListName(name);
    }

    private void syncGroups() {
        Set<String> managed = managedGroups();
        LuckPermsSilent.syncGroups(RANKS, mvpGroup());
        for (UUID playerId : mvpPlayers()) {
            LuckPermsSilent.applyUser(playerId, aetherionGroup(playerId), mvpGroup(), managed);
        }
        if (!isMvpPlusPlus(DAVID)) {
            LuckPermsSilent.applyUser(DAVID, aetherionGroup(DAVID), extraGroupName(DAVID), managed);
        }
    }

    private void applyLuckPerms(UUID playerId, String aetherionGroup, Rank extra) {
        if (playerId == null || aetherionGroup == null) {
            return;
        }
        LuckPermsSilent.applyUser(
                playerId,
                rankByGroup(aetherionGroup).group(),
                extra == null ? null : extra.group(),
                managedGroups()
        );
    }

    private String extraGroupName(UUID playerId) {
        Rank extra = extraFor(playerId);
        return extra == null ? null : extra.group();
    }

    private Set<String> managedGroups() {
        Set<String> groups = new java.util.HashSet<>();
        for (Rank rank : RANKS) {
            // Monkey / Beta are grantable via Dev Menu / LP fallback and must not be wiped on XP sync.
            if (!LP_FALLBACK_EXTRAS.contains(rank.group())) {
                groups.add(rank.group());
            }
        }
        groups.add(mvpGroup());
        groups.add("admin");
        groups.add("owner");
        return groups;
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null || !file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String key = playerId.toString();
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null && section.contains(key)) {
            try {
                String group = rankByGroup(section.getString(key, "adventurer")).group();
                if (!isExtra(group)) {
                    assigned.put(playerId, group);
                }
            } catch (RuntimeException ignored) {
            }
        }
        ConfigurationSection extraSection = config.getConfigurationSection("extras");
        if (extraSection != null && extraSection.contains(key)) {
            try {
                String group = rankByGroup(extraSection.getString(key, "")).group();
                if (isExtra(group)) {
                    extras.put(playerId, group);
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (config.getStringList("forced").contains(key)) {
            forced.add(playerId);
        } else {
            forced.remove(playerId);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    String group = rankByGroup(section.getString(key, "adventurer")).group();
                    if (!isExtra(group)) {
                        assigned.put(UUID.fromString(key), group);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection extraSection = config.getConfigurationSection("extras");
        if (extraSection != null) {
            for (String key : extraSection.getKeys(false)) {
                try {
                    String group = rankByGroup(extraSection.getString(key, "")).group();
                    if (isExtra(group)) {
                        extras.put(UUID.fromString(key), group);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (String raw : config.getStringList("forced")) {
            try {
                forced.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void flush() {
        save();
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        assigned.forEach((id, group) -> config.set("players." + id, group));
        extras.forEach((id, group) -> config.set("extras." + id, group));
        if (!forced.isEmpty()) {
            config.set("forced", forced.stream().map(UUID::toString).toList());
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
        } catch (IOException ignored) {
        }
    }

    private List<UUID> mvpPlayers() {
        List<UUID> players = new ArrayList<>();
        List<String> raw = plugin.getConfig().getStringList("ranks.mvpplusplus.players");
        if (raw.isEmpty()) {
            raw = List.of("c46afc0c-488b-430f-83a7-98f8a5df3ac5");
        }
        for (String value : raw) {
            try {
                players.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return players;
    }
}
