package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.FishingItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DivingGearListener implements Listener {

    private final ItemManager items;

    public DivingGearListener(AetherionItems plugin, ItemManager items) {
        this.items = items;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            int pieces = pieces(player);
            if (pieces <= 0) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, false));
            if (pieces >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false, false));
            }
            if (pieces >= 4 && player.isInWater()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 60, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0, true, false, false));
            }
        }
    }

    private int pieces(Player player) {
        int count = 0;
        for (ItemStack item : new ItemStack[]{
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        }) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (FishingItems.isDiving(items.getItemId(item))) {
                count++;
            }
        }
        return count;
    }
}
