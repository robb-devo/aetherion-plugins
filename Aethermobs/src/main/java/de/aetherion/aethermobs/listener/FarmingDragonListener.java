package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.items.farming.Crops;
import de.aetherion.items.listener.CropHarvestListener;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ultra-rare Nature Dragon on ripe crop breaks (exclusive catch).
 * Wild farm spawns stay shared/stealable via spawn weight.
 */
public final class FarmingDragonListener implements Listener {

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("aetherion", "item");

    private final AetherMobs plugin;

    public FarmingDragonListener(AetherMobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        if (block.hasMetadata(CropHarvestListener.CROP_BREAK_METADATA)) {
            return;
        }
        if (Crops.isDungeonWorld(block.getWorld()) || !Crops.isMature(block)) {
            return;
        }
        double chance = chanceFor(player.getInventory().getItemInMainHand());
        if (chance <= 0.0d || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        if (!plugin.getPetSpawnManager().spawnFishedPet(player, "nature_dragon")) {
            return;
        }
        TextColor mythic = Rarity.MYTHIC.textColor();
        player.sendMessage("§5✦ §5The furrow sighed. A §5Mythic Nature Dragon§5.");
        player.sendMessage("§8Only you can catch it — and the catch is guaranteed.");
        player.showTitle(Title.title(
                Component.text("Mythic Nature Dragon", mythic),
                Component.text("Yours alone. Any sphere works.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(2800), Duration.ofMillis(400))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 0.75f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.65f, 1.45f);
        player.playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.9f, 0.55f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 28, 0.55, 0.45, 0.55, 0.0);
        player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation().add(0, 1.4, 0), 22, 0.5, 0.55, 0.5, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendActionBar(Component.text("Mythic Nature Dragon — guaranteed catch.", mythic));
        }, 12L);
    }

    private static double chanceFor(ItemStack hoe) {
        String id = itemId(hoe);
        if (id == null) {
            return 0.0d;
        }
        String key = id.toLowerCase();
        return switch (key) {
            case "simple_hoe" -> 0.00002d;
            case "farming_hoe" -> 0.00015d;
            case "farming_hoe_2" -> 0.00035d;
            case "farming_hoe_3" -> 0.0008d;
            case "farming_hoe_4" -> 0.0015d;
            case "farming_hoe_5" -> 0.0025d;
            default -> key.startsWith("farming_hoe") ? 0.00015d : 0.0d;
        };
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
