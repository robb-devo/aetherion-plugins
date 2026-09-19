package de.aetherion.items.manager;

import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ActiveEquipmentStats {

    private static final List<StatProvider> statProviders = new ArrayList<>();

    private final ItemManager itemManager;

    public ActiveEquipmentStats(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    /**
     * Register a provider, replacing any previous instance of the same class.
     * Prevents soft-reload / enable loops from stacking pet/skill stats (esp. SPEED).
     */
    public static void registerProvider(StatProvider provider) {
        if (provider == null) {
            return;
        }
        Class<?> type = provider.getClass();
        statProviders.removeIf(existing -> existing != null && existing.getClass() == type);
        statProviders.add(provider);
    }

    public static void unregisterProvider(StatProvider provider) {
        if (provider != null) {
            statProviders.remove(provider);
        }
    }

    /** Wipe all providers (plugin disable). */
    public static void clearProviders() {
        statProviders.clear();
    }

    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 0.0;
        }

        double total = 0.0;

        ItemStack[] slots = getActiveItems(player);
        boolean suppressOffhand = charmsSuppressed(player);
        for (int i = 0; i < slots.length; i++) {
            if (i == 1 && suppressOffhand) {
                continue;
            }
            total += getItemStat(player, slots[i], capability);
        }

        double multiplier = 1.0;
        for (StatProvider provider : statProviders) {
            if (provider == null) {
                continue;
            }

            total += provider.getStat(player, capability);
            double extra = provider.getMultiplier(player, capability);
            if (extra > 0.0) {
                multiplier *= extra;
            }
        }

        return total * multiplier;
    }

    private ItemStack[] getActiveItems(Player player) {
        return new ItemStack[] {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
    }

    private static boolean charmsSuppressed(Player player) {
        Long until = player.getPersistentDataContainer().get(
                de.aetherion.items.core.ItemKeys.charmSuppressedUntil(),
                org.bukkit.persistence.PersistentDataType.LONG
        );
        return until != null && System.currentTimeMillis() < until;
    }

    private double getItemStat(Player player, ItemStack item, ItemCapability capability) {
        if (item == null || item.getType().isAir() || !itemManager.isAetherionItem(item)) {
            return 0.0;
        }

        ItemProfile profile = itemManager.getProfile(item);

        if (!profile.hasCapability(capability)) {
            return 0.0;
        }

        double value = itemManager.getStat(item, capability);
        String itemId = itemManager.getItemId(item);
        if (de.aetherion.items.item.DungeonCore.canInfuse(itemId)) {
            value *= de.aetherion.items.item.DungeonCore.rarityStatMultiplier(itemManager.getRarity(item));
        }
        boolean dungeon = de.aetherion.items.dungeon.DungeonArmor.inDungeon(player);
        boolean nativeGear = de.aetherion.items.dungeon.DungeonArmor.isNativeDungeonGear(item, itemManager);
        // Native dungeon gear stays weak in the overworld; dungeon power = cores + gear level.
        if (value > 0 && nativeGear && !dungeon) {
            value *= de.aetherion.items.dungeon.DungeonArmor.OVERWORLD_STAT_MULTIPLIER;
        }
        if (value > 0 && dungeon) {
            value *= de.aetherion.items.dungeon.DungeonGearProgress.dungeonBonusMultiplier(
                    item,
                    itemManager,
                    de.aetherion.items.dungeon.DungeonGearProgress.level(item)
            );
        }
        return value > 0 ? value : 0.0;
    }
}
