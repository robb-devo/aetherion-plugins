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
            // Ultra extras — high TAB, never parked on the XP row.
            // Beta 93 (rainbow) · Citrus 94 (citrus dye) · Monkey 95 (content + celestial) · Admin 100.
            new Rank("beta", 93, RainbowDye.prefixStatic(), "§d§lBeta Tester"),
            new Rank("citrus", 94, CitrusDye.prefixStatic(), "§e§lCitrus"),
            new Rank("monkey", 95, CelestialDye.monkeyPrefixStatic(), "§b§lMonkey"),
            new Rank("admin", 100, "&c[Admin] &f", "§cAdmin")
    );

    /**
     * Staff / Homie cosmetic extras sit on top of Aetherion XP ranks.
     * Standing rule: every future Homie special rank copies Monkey —
     * EXTRA + high TAB weight (near admin / mvpplusplus).
     * Never add them to the Adventurer→Aetherion progression row.
     * <p>
     * Monkey = content tools + celestial #B2FFFF. Citrus = citrus dye only.
     * Beta = rainbow cosmetics only. The level title stays
     * {@link #aetherionTitle(Player)} — dyes are a prefix, not a replacement.
     * TAB GROUPS order (Monkey first among Homie cosmetics):
     * {@code admin, monkey, citrus, beta, mvpplusplus, …progression}.
     * <p>
     * Ultras stay until removed — XP progression sync never overwrites them.
     */
    private static final Set<String> EXTRA = Set.of("admin", "monkey", "citrus", "beta", "mvpplusplus");

    private static final String DEFAULT_GROUP = "mvpplusplus";
    private static final String DEFAULT_PREFIX = "&6[MVP&c++&6] ";
    public static final UUID DAVID = UUID.fromString("365ffd9c-009c-4a9d-b0a5-14dea8768dee");
    /**
     * Robb's profile (IGN {@code A3therion}). This is the existing hard-coded owner
     * id from {@code ranks.mvpplusplus.players} — not a name check. Mojang's
     * {@code Robb} account is a different UUID and must not receive Admin.
     * Admin cosmetic display is exclusive to this id, and only when the rank menu stored it.
     */
    public static final UUID ROBB = UUID.fromString("c46afc0c-488b-430f-83a7-98f8a5df3ac5");

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
                service.scheduleScoreboardRebind(player);
            }
        }, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, service::paintOnlineDyed, 80L, 8L);
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
        return isPermanentExtra(group);
    }

    /** Ultra extras — never part of XP progression wipe. */
    public static boolean isPermanentExtra(String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        String key = group.toLowerCase(Locale.ROOT);
        return EXTRA.contains(key) || key.equals("owner");
    }

    /** LuckPerms groups XP sync may replace. Extras are never in this set. */
    public static Set<String> xpManagedGroups() {
        Set<String> groups = new java.util.LinkedHashSet<>();
        for (Rank rank : RANKS) {
            if (!isPermanentExtra(rank.group())) {
                groups.add(rank.group());
            }
        }
        return Set.copyOf(groups);
    }

    public static Set<String> extraGroups() {
        return EXTRA;
    }

    public static int rankWeight(String group) {
        if (group == null || group.isBlank()) {
            return 0;
        }
        String key = group.toLowerCase(Locale.ROOT);
        if (key.equals("owner")) {
            key = "admin";
        }
        for (Rank rank : RANKS) {
            if (rank.group().equals(key)) {
                return rank.weight();
            }
        }
        return 0;
    }

    public String displayTitle(Player player) {
        return aetherionTitle(player);
    }

    /** Level title (Adventurer, Veteran, …). Cosmetics do not replace this. */
    public String aetherionTitle(Player player) {
        if (player == null) {
            return "";
        }
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        int level = items == null || items.getSkills() == null ? 1 : items.getSkills().accountLevel(player);
        return de.aetherion.items.skill.AetherionLevel.coloredTitle(level);
    }

    public String ultraTitle(Player player) {
        String group = player == null ? null : cosmeticGroup(player);
        if (group == null || group.isBlank()) {
            Rank extra = extraRank(player);
            if (extra == null) {
                return "";
            }
            group = extra.group();
        }
        if ("monkey".equalsIgnoreCase(group)) {
            return CelestialDye.monkeyBadge().trim();
        }
        if ("citrus".equalsIgnoreCase(group)) {
            return CitrusDye.badge().trim();
        }
        if ("beta".equalsIgnoreCase(group)) {
            return RainbowDye.badge().trim();
        }
        Rank extra = extraRank(player);
        return extra == null ? "" : extra.display();
    }

    public boolean hasUltra(Player player) {
        return extraRank(player) != null;
    }

    /**
     * Right-hand scoreboard line. Same order as chat and the Tab player list:
     * special rank (if any), then the colored level tag, then the name.
     */
    public String sidebarLine1(Player player) {
        return nametag(player);
    }

    public String sidebarLine2(Player player) {
        return hasUltra(player) ? aetherionTitle(player) : "";
    }

    /**
     * Composite used by {@code %aetherion_tab_prefix%} and {@code %aetherion_chat_prefix%}.
     * Cosmetic dye first, then the account level tag. The level title is separate.
     */
    public String tabPrefix(Player player) {
        if (player == null) {
            return "§f";
        }
        String levelTag = "";
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        if (items != null && items.getSkills() != null) {
            levelTag = items.getSkills().accountTag(player);
        }
        return composePrefix(cosmeticGroup(player), levelTag, true);
    }

    /**
     * {@code dye + levelTag + §f}. Animated dyes follow the server tick.
     * Pass {@code animated=false} in tests so the frame stays at 0.
     */
    public static String composePrefix(String cosmeticGroup, String levelTag, boolean animated) {
        StringBuilder prefix = new StringBuilder();
        prefix.append(animated ? dyePrefix(cosmeticGroup) : dyePrefixStatic(cosmeticGroup));
        if (levelTag != null && !levelTag.isBlank()) {
            prefix.append(levelTag).append(" ");
        }
        prefix.append("§f");
        return prefix.toString();
    }

    /**
     * Chat / compact TAB / nametag dye.
     * {@code citrus} → citrus letters; {@code monkey} → #B2FFFF; {@code beta} → rainbow.
     */
    public static String dyePrefix(String extraGroup) {
        if (extraGroup == null || extraGroup.isBlank()) {
            return "";
        }
        return switch (extraGroup.toLowerCase(Locale.ROOT)) {
            case "admin" -> "§c[Admin] ";
            case "mvpplusplus" -> "§6[MVP§c++§6] ";
            case "monkey" -> CelestialDye.monkeyBadge();
            case "citrus" -> CitrusDye.badge();
            case "beta" -> RainbowDye.badge();
            default -> "";
        };
    }

    /** Offline / test variant — no tick. */
    public static String dyePrefixStatic(String extraGroup) {
        if (extraGroup == null || extraGroup.isBlank()) {
            return "";
        }
        return switch (extraGroup.toLowerCase(Locale.ROOT)) {
            case "admin" -> "§c[Admin] ";
            case "mvpplusplus" -> "§6[MVP§c++§6] ";
            case "monkey" -> CelestialDye.monkeyPrefixStatic();
            case "citrus" -> CitrusDye.prefixStatic();
            case "beta" -> RainbowDye.prefixStatic();
            default -> "";
        };
    }

    /**
     * Live cosmetic ultra. The Dev Menu row in {@code player-ranks.yml} is the only source.
     */
    public String cosmeticGroup(Player player) {
        if (player == null) {
            return null;
        }
        Rank extra = extraFor(player.getUniqueId());
        return extra == null ? null : extra.group();
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
        if (isPermanentExtra(next.group()) || isPermanentExtra(wanted)) {
            applyTo(player);
            return;
        }
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
        if ("admin".equals(rank.group()) && !isRobb(playerId)) {
            return false;
        }
        if (isExtra(rank.group())) {
            String previous = extras.get(playerId);
            if (previous != null && !previous.equalsIgnoreCase(rank.group())) {
                LuckPermsSilent.removeGroup(playerId, previous);
                if ("monkey".equalsIgnoreCase(previous)) {
                    LuckPermsSilent.revokeContentKitFromUser(playerId);
                }
            }
            extras.put(playerId, rank.group());
            save();
            applyLuckPerms(playerId, aetherionGroup(playerId));
            Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) {
                paint(online);
                scheduleScoreboardRebind(online);
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
        applyLuckPerms(playerId, rank.group());
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            paint(online);
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applyTo(player);
            // Stored extra is readable now. Rechecks also land after TAB's 2500ms join delay.
            scheduleScoreboardRebind(player);
        }, 40L);
    }

    public void applyTo(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // Stored Dev Menu row only. Do not read LuckPerms parents or permission nodes.
        applyLuckPerms(player.getUniqueId(), aetherionGroup(player.getUniqueId()));
        paint(player);
    }

    public Rank extraRank(Player player) {
        return player == null ? null : extraFor(player.getUniqueId());
    }

    /**
     * Cosmetic extra for display.
     * Only the Dev Menu → Ranks row stored in {@code player-ranks.yml}.
     * LuckPerms groups, permission nodes, join, OP, and {@code ranks.mvpplusplus.players}
     * do not paint a badge. Admin is that stored row for {@link #ROBB} only.
     */
    public Rank extraFor(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String group = storedDisplayGroup(playerId, extras.get(playerId));
        return group == null ? null : rankByGroup(group);
    }

    /**
     * Group name to paint, or null when nothing was stored from Dev Menu.
     * OP and permission checks are intentionally not inputs.
     */
    static String storedDisplayGroup(UUID playerId, String stored) {
        if (playerId == null || stored == null || stored.isBlank()) {
            return null;
        }
        String key = stored.toLowerCase(Locale.ROOT);
        if ("owner".equals(key)) {
            key = "admin";
        }
        if (!EXTRA.contains(key)) {
            return null;
        }
        if ("admin".equals(key) && !isRobb(playerId)) {
            return null;
        }
        return key;
    }

    /** Robb / A3therion. The Admin cosmetic is exclusive to this profile. */
    public static boolean isRobb(UUID playerId) {
        return playerId != null && ROBB.equals(playerId);
    }

    private static boolean isDisplayableExtra(UUID playerId, String stored) {
        if (stored == null || !isPermanentExtra(stored)) {
            return false;
        }
        return !"admin".equalsIgnoreCase(stored) || isRobb(playerId);
    }

    /** Remove a staff/content extra without touching XP progression. */
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
        if ("monkey".equals(key)) {
            LuckPermsSilent.revokeContentKitFromUser(playerId);
        }
        applyLuckPerms(playerId, aetherionGroup(playerId));
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            paint(online);
            scheduleScoreboardRebind(online);
        }
        return true;
    }

    /**
     * TAB may already have locked {@code main} for this player. Recheck so
     * {@code %aetherion_has_ultra%=yes} can select the {@code ultra} board.
     */
    private void scheduleScoreboardRebind(Player player) {
        TabScoreboardRebind.schedule(plugin, player);
    }

    private void paint(Player player) {
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer hex =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                        .character('&')
                        .hexColors()
                        .build();
        String raw = nametag(player).replace('§', '&');
        net.kyori.adventure.text.Component name = hex.deserialize(raw);
        player.displayName(name);
        player.playerListName(name);
        player.customName(name);
    }

    private void paintOnlineDyed() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Rank extra = extraFor(player.getUniqueId());
            if (extra == null) {
                continue;
            }
            // Dyes animate. Admin and the other extras stay on the display name so the
            // right-hand scoreboard (which reads display name) keeps the special rank.
            paint(player);
        }
    }

    private void syncGroups() {
        // XP group metadata only. Does not read LuckPerms users, permission nodes,
        // or ranks.mvpplusplus.players into player-ranks.yml or the badge.
        LuckPermsSilent.syncGroups(RANKS, mvpGroup());
    }

    /**
     * Mirrors the XP progression parent into LuckPerms and strips cosmetic parents.
     * A stored Dev Menu extra is never attached as a LuckPerms group.
     */
    private void applyLuckPerms(UUID playerId, String aetherionGroup) {
        if (playerId == null || aetherionGroup == null) {
            return;
        }
        LuckPermsSilent.applyUser(
                playerId,
                rankByGroup(aetherionGroup).group(),
                managedGroups()
        );
        if ("monkey".equalsIgnoreCase(extras.get(playerId))) {
            LuckPermsSilent.grantContentKitToUser(playerId);
        }
    }

    private Set<String> managedGroups() {
        return xpManagedGroups();
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
                if ("admin".equals(group) && !isRobb(playerId)) {
                    extras.remove(playerId);
                } else if (isPermanentExtra(group)) {
                    extras.put(playerId, group);
                } else {
                    assigned.put(playerId, group);
                }
            } catch (RuntimeException ignored) {
            }
        }
        ConfigurationSection extraSection = config.getConfigurationSection("extras");
        if (extraSection != null && extraSection.contains(key)) {
            try {
                String group = rankByGroup(extraSection.getString(key, "")).group();
                if (isDisplayableExtra(playerId, group)) {
                    extras.put(playerId, group);
                } else if ("admin".equals(group)) {
                    extras.remove(playerId);
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
        boolean droppedForeignAdmin = false;
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    String group = rankByGroup(section.getString(key, "adventurer")).group();
                    UUID id = UUID.fromString(key);
                    if ("admin".equals(group) && !isRobb(id)) {
                        droppedForeignAdmin = true;
                        continue;
                    }
                    if (isPermanentExtra(group)) {
                        extras.put(id, group);
                    } else {
                        assigned.put(id, group);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection extraSection = config.getConfigurationSection("extras");
        if (extraSection != null) {
            for (String key : extraSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String group = rankByGroup(extraSection.getString(key, "")).group();
                    if (isDisplayableExtra(id, group)) {
                        extras.put(id, group);
                    } else if ("admin".equals(group)) {
                        droppedForeignAdmin = true;
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
        if (droppedForeignAdmin) {
            save();
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
