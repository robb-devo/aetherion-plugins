package de.aetherion.guilds.placeholder;

import de.aetherion.guilds.AetherionGuilds;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.util.GuildFormat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GuildPlaceholderExpansion extends PlaceholderExpansion {

    private static final int TAB_LINES = 8;

    private final AetherionGuilds plugin;

    public GuildPlaceholderExpansion(AetherionGuilds plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aetherguild";
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
        try {
            String key = params.toLowerCase(Locale.ROOT);
            boolean onIsland = plugin.getIslands() != null && plugin.getIslands().isGuildWorld(player.getWorld());
            Guild here = onIsland ? plugin.guildAt(player.getLocation()) : null;
            return switch (key) {
                case "onisland", "on_island" -> onIsland ? "yes" : "no";
                case "name" -> {
                    Guild guild = plugin.getGuilds().byPlayer(player.getUniqueId());
                    yield guild == null ? "" : guild.name();
                }
                case "island", "island_name", "area" -> onIsland ? plugin.islandTitle(player.getLocation()) : "";
                case "island_level" -> here == null ? "" : Integer.toString(here.islandLevel());
                case "quarry_count" -> here == null ? "0" : Integer.toString(here.minions().size());
                case "member_count" -> here == null ? "0" : Integer.toString(here.members().size());
                case "online_count" -> here == null ? "0" : Integer.toString(here.onlineMembers());
                case "online_names" -> here == null ? "" : onlineNames(here);
                default -> {
                    if (key.startsWith("quarry_")) {
                        yield here == null ? "" : quarryLine(here, key.substring("quarry_".length()));
                    }
                    if (key.startsWith("member_")) {
                        yield here == null ? "" : memberLine(here, key.substring("member_".length()));
                    }
                    yield null;
                }
            };
        } catch (Exception ignored) {
            return "";
        }
    }

    private String quarryLine(Guild guild, String indexRaw) {
        int index;
        try {
            index = Integer.parseInt(indexRaw) - 1;
        } catch (NumberFormatException ignored) {
            return "";
        }
        List<QuarryMinion> quarries = new ArrayList<>(guild.minions());
        quarries.sort(Comparator
                .comparing((QuarryMinion minion) -> minion.quarryType().display())
                .thenComparing(Comparator.comparingInt(QuarryMinion::level).reversed()));
        if (index == TAB_LINES - 1 && quarries.size() > TAB_LINES) {
            return "&7+ &f" + (quarries.size() - (TAB_LINES - 1)) + " &7more";
        }
        if (index < 0 || index >= quarries.size() || index >= TAB_LINES) {
            return "";
        }
        QuarryMinion minion = quarries.get(index);
        return GuildFormat.storageLine(minion.quarryType(), minion.level(), plugin.getMinions().preview(minion));
    }

    private String memberLine(Guild guild, String indexRaw) {
        int index;
        try {
            index = Integer.parseInt(indexRaw) - 1;
        } catch (NumberFormatException ignored) {
            return "";
        }
        List<Map.Entry<UUID, GuildRank>> roster = roster(guild);
        if (index < 0 || index >= roster.size()) {
            return "";
        }
        Map.Entry<UUID, GuildRank> entry = roster.get(index);
        OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
        String name = member.getName() == null ? "Unknown" : member.getName();
        boolean online = member.isOnline();
        return (online ? "&a" : "&7") + name;
    }

    private String onlineNames(Guild guild) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<UUID, GuildRank> entry : roster(guild)) {
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null && online.isOnline()) {
                names.add(online.getName());
            }
        }
        if (names.isEmpty()) {
            return "&7Nobody online";
        }
        return "&a" + String.join("&7, &a", names);
    }

    private List<Map.Entry<UUID, GuildRank>> roster(Guild guild) {
        List<Map.Entry<UUID, GuildRank>> roster = new ArrayList<>(guild.members().entrySet());
        roster.sort(Comparator
                .comparing((Map.Entry<UUID, GuildRank> entry) -> {
                    Player online = Bukkit.getPlayer(entry.getKey());
                    return online != null && online.isOnline() ? 0 : 1;
                })
                .thenComparing(entry -> -entry.getValue().weight())
                .thenComparing(entry -> {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    return name == null ? "" : name.toLowerCase(Locale.ROOT);
                }));
        return roster;
    }
}
