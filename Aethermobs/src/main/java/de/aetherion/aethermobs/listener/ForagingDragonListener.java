package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ultra-rare Forest Dragon on log breaks / successful chop (exclusive catch).
 * Wild forest spawns stay shared/stealable via spawn weight.
 */
public final class ForagingDragonListener implements Listener {

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("aetherion", "item");

    private final AetherMobs plugin;

    public ForagingDragonListener(AetherMobs plugin) {
        this.plugin = plugin;
    }

    /** Successful fell-mark chop — slightly better odds than raw log breaks. */
    public static void tryFromSuccessfulChop(Player player) {
        AetherMobs plugin = AetherMobs.getInstance();
        if (plugin == null || player == null || !player.isOnline()) {
            return;
        }
        double chance = chanceFor(player.getInventory().getItemInMainHand(), true);
        if (chance <= 0.0d || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        spawnFor(plugin, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        if (!isLog(block.getType())) {
            return;
        }
        String world = block.getWorld().getName().toLowerCase(Locale.ROOT);
        if (world.startsWith("aedun_") || world.startsWith("ae_dun")
                || world.equals("aether_farm_island") || world.startsWith("aether_farm_")) {
            return;
        }
        double chance = chanceFor(player.getInventory().getItemInMainHand(), false);
        if (chance <= 0.0d || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        spawnFor(plugin, player);
    }

    private static void spawnFor(AetherMobs plugin, Player player) {
        if (!plugin.getPetSpawnManager().spawnFishedPet(player, "forest_dragon")) {
            return;
        }
        TextColor mythic = Rarity.MYTHIC.textColor();
        player.sendMessage("§5✦ §5The canopy parted. A §5Mythic Forest Dragon§5.");
        player.sendMessage("§8Only you can catch it — and the catch is guaranteed.");
        player.showTitle(Title.title(
                Component.text("Mythic Forest Dragon", mythic),
                Component.text("Yours alone. Any sphere works.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(2800), Duration.ofMillis(400))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 0.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.65f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.95f, 0.55f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 24, 0.55, 0.45, 0.55, 0.0);
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1.3, 0), 18, 0.45, 0.4, 0.45, 0.02,
                Material.OAK_LEAVES.createBlockData());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendActionBar(Component.text("Mythic Forest Dragon — guaranteed catch.", mythic));
        }, 12L);
    }

    private static double chanceFor(ItemStack axe, boolean chopSuccess) {
        String id = itemId(axe);
        if (id == null) {
            return 0.0d;
        }
        String key = id.toLowerCase(Locale.ROOT);
        double base = switch (key) {
            case "simple_axe" -> 0.00002d;
            case "foraging_axe" -> 0.00015d;
            case "foraging_axe_2" -> 0.00035d;
            case "foraging_axe_3" -> 0.0008d;
            case "foraging_axe_4" -> 0.0015d;
            case "foraging_axe_5" -> 0.0025d;
            default -> key.startsWith("foraging_axe") ? 0.00015d : 0.0d;
        };
        if (base <= 0.0d) {
            return 0.0d;
        }
        // Successful timing chop is a bit kinder than raw log breaks.
        return chopSuccess ? base * 2.5d : base;
    }

    private static boolean isLog(Material type) {
        return type != null && (Tag.LOGS.isTagged(type) || Tag.LOGS_THAT_BURN.isTagged(type));
    }

    private static String itemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
    }
}
