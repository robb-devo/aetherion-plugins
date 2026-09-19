package de.aetherion.items.world;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * First time a player enters the Borderlands Crypt (Y ≤ 55) → new-area discover polish,
 * plus a light recurring cue while they stay below the ceiling.
 */
public final class CryptDiscoverListener implements Listener, Runnable {

    private static final int CUE_EVERY_TICKS = 60;

    private final JavaPlugin plugin;
    private final MobZoneService mobZones;
    private final File file;
    private final Set<UUID> discovered = new HashSet<>();
    private int tick;

    public CryptDiscoverListener(JavaPlugin plugin, MobZoneService mobZones) {
        this.plugin = plugin;
        this.mobZones = mobZones;
        this.file = new File(plugin.getDataFolder(), "crypt-discover.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 20L);
    }

    @Override
    public void run() {
        tick++;
        boolean cuePulse = tick % CUE_EVERY_TICKS == 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (mobZones == null || !mobZones.inCrypt(player.getLocation())) {
                continue;
            }
            if (!discovered.contains(player.getUniqueId())) {
                if (discovered.add(player.getUniqueId())) {
                    save();
                    announce(player);
                }
                continue;
            }
            if (cuePulse) {
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                        "§5Crypt §8· §cDanger"
                ));
            }
        }
    }

    private void announce(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.85f);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.55f, 0.7f);
        player.showTitle(Title.title(
                LegacyComponentSerializer.legacySection().deserialize("§5§lNEW AREA"),
                LegacyComponentSerializer.legacySection().deserialize("§fCrypt §8· §cDanger"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(500))
        ));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§5✦ §fCrypt §7discovered"
        ));
        player.sendMessage("§5✦ §eNew area: §fCrypt§e.");
        player.sendMessage("§7Something stronger waits in the dark. Be careful.");
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String raw : config.getStringList("discovered")) {
            try {
                discovered.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discovered", discovered.stream().map(UUID::toString).sorted().toList());
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save crypt-discover.yml: " + exception.getMessage());
        }
    }
}
