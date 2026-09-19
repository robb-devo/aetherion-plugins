package de.aetherion.items.item;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.rank.RankBadgeService;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Voided455Drops {

    private static final Set<UUID> USED = ConcurrentHashMap.newKeySet();
    private static boolean loaded;

    private Voided455Drops() {
    }

    public static boolean mvpPityOpen(Player player) {
        if (player == null) {
            return false;
        }
        load();
        if (USED.contains(player.getUniqueId())) {
            return false;
        }
        return isMvpPlusPlus(player);
    }

    public static void markDropped(Player player) {
        if (player == null) {
            return;
        }
        load();
        if (!USED.add(player.getUniqueId())) {
            return;
        }
        save();
    }

    private static boolean isMvpPlusPlus(Player player) {
        if (player.hasPermission("group.mvpplusplus")
                || player.hasPermission("aetherion.rank.mvpplusplus")) {
            return true;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.ranks() == null) {
            return RankBadgeService.DAVID.equals(player.getUniqueId());
        }
        RankBadgeService ranks = plugin.ranks();
        return ranks.hasMvpPlusPlus(player.getUniqueId())
                || RankBadgeService.DAVID.equals(player.getUniqueId());
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("mvp-pity-used")) {
            try {
                USED.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static synchronized void save() {
        File file = file();
        if (file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : USED) {
            ids.add(id.toString());
        }
        yaml.set("mvp-pity-used", ids);
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    private static File file() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return null;
        }
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            return new File(plugin.getDataFolder(), "voided-455.yml");
        }
        return new File(plugin.getDataFolder(), "voided-455.yml");
    }
}
