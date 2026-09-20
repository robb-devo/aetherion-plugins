package de.aetherion.items.placeholder;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class CoinPlaceholderExpansion extends PlaceholderExpansion {

    private final AetherionItems plugin;
    private final CoinService coins;
    private final ActiveEquipmentStats equipment;

    public CoinPlaceholderExpansion(AetherionItems plugin, CoinService coins) {
        this.plugin = plugin;
        this.coins = coins;
        this.equipment = new ActiveEquipmentStats(plugin.getItemManager());
    }

    @Override
    public String getIdentifier() {
        return "aetherion";
    }

    @Override
    public String getAuthor() {
        return "Aetherion";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return "";
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "coins", "gold", "balance" -> coins == null ? "0" : coins.formatted(player);
            case "coins_raw", "gold_raw", "balance_raw" -> coins == null ? "0" : Long.toString(coins.get(player));
            case "shards", "aether_shards" -> plugin.getShards() == null ? "0" : plugin.getShards().formatted(player);
            case "shards_raw", "aether_shards_raw" -> plugin.getShards() == null ? "0" : Long.toString(plugin.getShards().get(player));
            case "rank" -> plugin.ranks() == null ? "" : ChatColor.stripColor(plugin.ranks().aetherionTitle(player));
            case "level_title" -> plugin.ranks() == null
                    ? (plugin.getSkills() == null
                    ? ""
                    : de.aetherion.items.skill.AetherionLevel.coloredTitle(plugin.getSkills().accountLevel(player)))
                    : plugin.ranks().aetherionTitle(player);
            case "ultra_rank", "rank_ultra" -> plugin.ranks() == null ? "" : plugin.ranks().ultraTitle(player);
            case "has_ultra" -> plugin.ranks() != null && plugin.ranks().hasUltra(player) ? "yes" : "no";
            case "sidebar_1" -> plugin.ranks() == null ? "" : plugin.ranks().sidebarLine1(player);
            case "sidebar_2" -> plugin.ranks() == null ? "" : plugin.ranks().sidebarLine2(player);
            case "xpboost", "xp_boost" -> plugin.xpBoost() == null || !plugin.xpBoost().active(player)
                    ? ""
                    : plugin.xpBoost().formatted(player);
            case "level", "aether_level" -> plugin.getSkills() == null ? "1" : Integer.toString(plugin.getSkills().accountLevel(player));
            case "level_colored", "aether_level_colored" -> plugin.getSkills() == null
                    ? "§71"
                    : de.aetherion.items.skill.AetherionLevel.coloredLevel(plugin.getSkills().accountLevel(player));
            case "level_tag", "aether_level_tag" -> plugin.getSkills() == null ? "§7[1]" : plugin.getSkills().accountTag(player);
            case "tab_prefix", "nametag_prefix", "chat_prefix",
                    "list_prefix", "playerlist_prefix", "scoreboard_prefix" ->
                    plugin.ranks() == null ? "§f" : plugin.ranks().tabPrefix(player);
            case "monkey_badge", "celestial_monkey" -> de.aetherion.items.rank.CelestialDye.monkeyBadge();
            case "beta_badge", "rainbow_beta" -> de.aetherion.items.rank.RainbowDye.badge();
            case "celestial_badge" -> de.aetherion.items.rank.CelestialDye.badge("[Celestial]");
            case "nametag", "list_name", "playerlist_name" ->
                    plugin.ranks() == null ? player.getName() : plugin.ranks().nametag(player);
            case "level_xp", "aether_level_xp" -> plugin.getSkills() == null ? "0" : Long.toString(plugin.getSkills().accountXp(player));
            case "level_into", "aether_level_into", "level_xp_into" -> {
                if (plugin.getSkills() == null) {
                    yield "0";
                }
                long xp = plugin.getSkills().accountXp(player);
                yield Long.toString(de.aetherion.items.skill.AetherionLevel.intoLevel(xp));
            }
            case "level_needed", "aether_level_needed", "level_xp_needed" -> {
                if (plugin.getSkills() == null) {
                    yield "100";
                }
                int level = plugin.getSkills().accountLevel(player);
                long needed = de.aetherion.items.skill.AetherionLevel.xpToNext(level);
                yield needed <= 0L ? "MAX" : Long.toString(needed);
            }
            case "level_bar", "aether_level_xpbar" -> plugin.getSkills() == null ? "" : de.aetherion.items.skill.AetherionLevel.bar(plugin.getSkills().accountXp(player));
            case "area", "region" -> plugin.getAreas() == null ? "Wilderness" : plugin.getAreas().nameAt(player.getLocation());
            case "weather", "area_weather" -> weatherLine(player);
            case "damage" -> statStr(player, ItemCapability.DAMAGE);
            case "defense" -> statStr(player, ItemCapability.DEFENSE);
            case "health" -> statStr(player, ItemCapability.HEALTH);
            case "hp", "current_hp", "health_current" -> {
                double hp = Math.max(0.0, player.getHealth());
                yield Integer.toString((int) Math.round(hp));
            }
            case "max_hp", "health_max" -> {
                double max = maxHealthOf(player);
                yield Integer.toString((int) Math.round(max));
            }
            case "hp_display", "health_display" -> {
                double hp = Math.max(0.0, player.getHealth());
                double max = maxHealthOf(player);
                yield (int) Math.round(hp) + "/" + (int) Math.round(max);
            }
            case "speed" -> statStr(player, ItemCapability.SPEED);
            case "crit_chance" -> statStr(player, ItemCapability.CRIT_CHANCE);
            case "crit_damage" -> statStr(player, ItemCapability.CRIT_DAMAGE);
            case "mining_power" -> statStr(player, ItemCapability.MINING_POWER);
            case "fortune" -> statStr(player, ItemCapability.FORTUNE);
            case "spread" -> statStr(player, ItemCapability.SPREAD);
            case "attack_spread" -> statStr(player, ItemCapability.ATTACK_SPREAD);
            case "harvest", "harvest_spread" -> statStr(player, ItemCapability.HARVEST_SPREAD);
            case "catch_rate", "pet_catch_rate" -> statStr(player, ItemCapability.PET_CATCH_RATE);
            case "fishing_speed", "fish_speed" -> statStr(player, ItemCapability.FISHING_SPEED);
            case "fishing_catch", "fish_catch" -> statStr(player, ItemCapability.FISHING_CATCH);
            default -> skillPlaceholder(player, params.toLowerCase(Locale.ROOT));
        };
    }

    private String skillPlaceholder(Player player, String key) {
        var skills = plugin.getSkills();
        if (skills == null) {
            return "";
        }
        if (key.startsWith("skill_extra_")) {
            Integer line = parseIndex(key.substring("skill_extra_".length()));
            return line == null ? "" : skills.tabExtraLine(player, line - 1);
        }
        if (key.startsWith("skill_")) {
            Integer slot = parseIndex(key.substring("skill_".length()));
            return slot == null ? null : skills.tabSlotLine(player, slot - 1);
        }
        return null;
    }

    /** Soft-read forage isle weather for TAB; off-isle returns em dash for layout room. */
    private static String weatherLine(Player player) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (player == null || foraging == null) {
            return "—";
        }
        de.aetherion.core.api.ForageWeatherView state = foraging.weather(player);
        if (state == null) {
            return "—";
        }
        if (state.habitat() == null || "none".equals(String.valueOf(state.habitat()))) {
            return "—";
        }
        String label = prettyWeather(state.kind());
        if (state.source() != null && "RITUAL".equalsIgnoreCase(String.valueOf(state.source()))) {
            return label + " §8(rite)";
        }
        return label;
    }

    private static String prettyWeather(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Clear";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "CLEAR" -> "Clear";
            case "FOG" -> "Fog";
            case "RAIN" -> "Rain";
            case "DRIZZLE" -> "Drizzle";
            case "SNOW" -> "Snow";
            case "WINDY" -> "Windy";
            default -> {
                String lower = key.toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    private static Integer parseIndex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            return value < 1 ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String statStr(Player player, ItemCapability cap) {
        try {
            double val = equipment.getStat(player, cap);
            if (val == (long) val) {
                return Long.toString((long) val);
            }
            return String.format(Locale.US, "%.1f", val);
        } catch (Exception ignored) {
            return "0";
        }
    }

    private static double maxHealthOf(Player player) {
        try {
            var attr = player.getAttribute(org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH"));
            if (attr != null) {
                return Math.max(1.0, attr.getValue());
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            var attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                return Math.max(1.0, attr.getValue());
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }
}
