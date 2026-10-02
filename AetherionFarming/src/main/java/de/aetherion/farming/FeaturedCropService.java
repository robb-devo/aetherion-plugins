package de.aetherion.farming;

import de.aetherion.farming.island.FarmIsleZones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hourly “featured” crop on the Farm Isle — mild extra drop chance while active.
 * Rotates among the core resource crops (not every exotic).
 */
public final class FeaturedCropService implements Runnable {

    private static final List<Material> POOL = List.of(
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.SUGAR_CANE
    );

    private final AetherionFarming plugin;
    private Material featured = Material.WHEAT;
    private BukkitTask task;
    private long hourEndsAtMs;

    public FeaturedCropService(AetherionFarming plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("featured-crop.enabled", true)) {
            return;
        }
        roll(true);
        long ticks = Math.max(20L * 60L, plugin.getConfig().getLong("featured-crop.rotate-ticks", 20L * 60L * 60L));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, ticks, ticks);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::toastNearby, 20L * 45L, 20L * 90L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        roll(false);
    }

    public Material featured() {
        return featured;
    }

    public boolean isFeatured(Material material) {
        if (material == null || featured == null) {
            return false;
        }
        if (material == featured) {
            return true;
        }
        // Block forms ↔ item forms
        return switch (featured) {
            case WHEAT -> material == Material.WHEAT || material == Material.WHEAT_SEEDS;
            case CARROT -> material == Material.CARROT || material == Material.CARROTS;
            case POTATO -> material == Material.POTATO || material == Material.POTATOES;
            case BEETROOT -> material == Material.BEETROOT || material == Material.BEETROOTS
                    || material == Material.BEETROOT_SEEDS;
            case SUGAR_CANE -> material == Material.SUGAR_CANE;
            default -> false;
        };
    }

    /** Extra whole crop items to grant (0/1) when harvesting the featured crop. */
    public int bonusAmount() {
        if (!plugin.getConfig().getBoolean("featured-crop.enabled", true)) {
            return 0;
        }
        double chance = plugin.getConfig().getDouble("featured-crop.bonus-chance", 0.35d);
        return ThreadLocalRandom.current().nextDouble() < chance ? 1 : 0;
    }

    public String prettyName() {
        return switch (featured) {
            case WHEAT -> "Wheat";
            case CARROT -> "Carrot";
            case POTATO -> "Potato";
            case BEETROOT -> "Beetroot";
            case SUGAR_CANE -> "Sugar Cane";
            default -> featured.name();
        };
    }

    private void roll(boolean silent) {
        Material next = POOL.get(ThreadLocalRandom.current().nextInt(POOL.size()));
        // Prefer a change when possible
        if (POOL.size() > 1) {
            int guard = 0;
            while (next == featured && guard++ < 6) {
                next = POOL.get(ThreadLocalRandom.current().nextInt(POOL.size()));
            }
        }
        featured = next;
        long hours = Math.max(1L, plugin.getConfig().getLong("featured-crop.rotate-ticks", 20L * 60L * 60L) / (20L * 60L * 60L));
        hourEndsAtMs = System.currentTimeMillis() + hours * 3_600_000L;
        if (!silent) {
            announce();
        }
        plugin.getLogger().info("Featured farm crop: " + prettyName());
    }

    private void announce() {
        String msg = "§aFarm Isle Featured §8· §e" + prettyName()
                + " §7pays a little extra this hour.";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (FarmIsleZones.inFarmIsleFootprint(plugin, player.getLocation())) {
                player.sendMessage(msg);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Featured: " + prettyName(),
                        net.kyori.adventure.text.format.NamedTextColor.GOLD
                ));
            }
        }
    }

    private void toastNearby() {
        if (!plugin.getConfig().getBoolean("featured-crop.enabled", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (FarmIsleZones.inFarmIsleFootprint(plugin, player.getLocation())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Featured crop: " + prettyName(),
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW
                ));
            }
        }
    }
}
