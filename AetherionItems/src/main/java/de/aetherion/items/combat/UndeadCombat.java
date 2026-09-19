package de.aetherion.items.combat;

import de.aetherion.items.manager.ItemManager;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;

public final class UndeadCombat {

    private UndeadCombat() {
    }

    public static boolean isUndead(Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType type = entity.getType();
        return switch (type) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, GIANT,
                    SKELETON, STRAY, WITHER_SKELETON, WITHER, PHANTOM,
                    ZOGLIN, SKELETON_HORSE, ZOMBIE_HORSE, BOGGED -> true;
            default -> {
                String name = type.name();
                yield name.contains("ZOMBIE")
                        || name.contains("SKELETON")
                        || name.contains("WITHER")
                        || name.equals("BOGGED");
            }
        };
    }

    public static Entity attackerOf(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    public static boolean wearingFullSet(Player player, ItemManager itemManager, String prefix) {
        if (player == null || itemManager == null || prefix == null || prefix.isBlank()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        return startsWith(itemManager.getItemId(inventory.getHelmet()), prefix)
                && startsWith(itemManager.getItemId(inventory.getChestplate()), prefix)
                && startsWith(itemManager.getItemId(inventory.getLeggings()), prefix)
                && startsWith(itemManager.getItemId(inventory.getBoots()), prefix);
    }

    public static boolean startsWith(String itemId, String prefix) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public static boolean isPiece(ItemStack item, ItemManager itemManager, String prefix) {
        return itemManager != null && startsWith(itemManager.getItemId(item), prefix);
    }
}
