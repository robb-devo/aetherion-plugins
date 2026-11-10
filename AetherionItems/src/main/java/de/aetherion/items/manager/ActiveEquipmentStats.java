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
    private final EquippedGear.StatReader itemStat = this::getItemStat;

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

        // Read off the items once and kept until a piece changes; see EquippedGear.
        boolean dungeon = de.aetherion.items.dungeon.DungeonArmor.inDungeon(player);
        boolean suppressOffhand = charmsSuppressed(player);
        for (EquippedGear.Slot slot : EquippedGear.ALL) {
            if (slot == EquippedGear.Slot.OFF_HAND && suppressOffhand) {
                continue;
            }
            total += EquippedGear.stat(player, slot, capability, dungeon, itemStat);
        }

        double multiplier = 1.0;
        // Snapshot so a provider register/unregister mid-tick cannot skip gear or blow the HUD timer.
        for (StatProvider provider : List.copyOf(statProviders)) {
            if (provider == null) {
                continue;
            }
            try {
                total += provider.getStat(player, capability);
                double extra = provider.getMultiplier(player, capability);
                if (extra > 0.0) {
                    multiplier *= extra;
                }
            } catch (RuntimeException ignored) {
                // Bad provider must not zero the whole gear stack for this tick.
            }
        }

        return total * multiplier;
    }

    private static boolean charmsSuppressed(Player player) {
        Long until = player.getPersistentDataContainer().get(
                de.aetherion.items.core.ItemKeys.charmSuppressedUntil(),
                org.bukkit.persistence.PersistentDataType.LONG
        );
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * What one item gives, in the dungeon or in the overworld. Depends on the item alone, which is what lets
     * {@link EquippedGear} keep the answer until the item changes.
     */
    private double getItemStat(ItemStack item, ItemCapability capability, boolean dungeon) {
        if (item == null || item.getType().isAir() || !itemManager.isAetherionItem(item)) {
            return 0.0;
        }

        ItemProfile profile = itemManager.getProfile(item);
        if (profile == null) {
            profile = ItemProfile.UNKNOWN;
        }

        double value = itemManager.getStat(item, capability);
        if (value <= 0.0) {
            return 0.0;
        }
        // Known profiles without this capability stay gated; UNKNOWN still counts written PDC stats.
        if (profile != ItemProfile.UNKNOWN && !profile.hasCapability(capability)) {
            return 0.0;
        }

        String itemId = itemManager.getItemId(item);
        if (de.aetherion.items.item.DungeonCore.canInfuse(itemId)) {
            value *= de.aetherion.items.item.DungeonCore.rarityStatMultiplier(itemManager.getRarity(item));
        }
        // Dungeon gear stays weaker in the overworld; dungeon power = cores + gear level.
        // Native Floor-I gear x0.38 (unchanged); P3 B2: relic-identified T2 x0.60, T3 x0.70.
        double overworld = de.aetherion.items.dungeon.DungeonArmor.overworldMultiplier(item, itemManager);
        if (overworld < 1.0 && !dungeon) {
            value *= overworld;
        }
        if (dungeon) {
            value *= de.aetherion.items.dungeon.DungeonGearProgress.dungeonBonusMultiplier(
                    item,
                    itemManager,
                    de.aetherion.items.dungeon.DungeonGearProgress.level(item)
            );
        }
        return value > 0 ? value : 0.0;
    }
}
