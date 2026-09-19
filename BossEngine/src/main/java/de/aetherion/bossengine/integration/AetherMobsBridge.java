package de.aetherion.bossengine.integration;

import de.aetherion.core.AetherEntities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Soft integration with AetherMobs / Aetherlex.
 * Pets live as tagged ItemDisplays and must never be treated as bosses.
 */
public class AetherMobsBridge {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("AetherMobs");
    }

    public boolean isPetEntity(Entity entity) {
        return AetherEntities.isPet(entity);
    }

    public double bossDropMultiplier(org.bukkit.entity.Player player) {
        if (!isAvailable() || player == null) {
            return 1.0;
        }
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin == null) {
                return 1.0;
            }
            Object value = plugin.getClass()
                    .getMethod("getBossDropMultiplier", org.bukkit.entity.Player.class)
                    .invoke(plugin, player);
            if (value instanceof Number number) {
                return Math.max(1.0, number.doubleValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 1.0;
    }

    public Optional<ItemStack> createCatchSphere(String id, int amount) {
        if (!isAvailable() || id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin == null) {
                return Optional.empty();
            }
            Object manager = plugin.getClass().getMethod("getBetaSphereManager").invoke(plugin);
            Object created = manager.getClass()
                    .getMethod("createCatchSphere", String.class)
                    .invoke(manager, id);
            if (!(created instanceof ItemStack item)) {
                return Optional.empty();
            }
            item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
            return Optional.of(item);
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }
}
