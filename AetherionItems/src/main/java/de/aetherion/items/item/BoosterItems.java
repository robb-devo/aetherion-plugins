package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.TexturedHeads;
import de.aetherion.items.model.BoosterStats;
import de.aetherion.items.model.BoosterType;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Booster item factories extracted from {@link CustomItem}.
 */
public final class BoosterItems {

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "coal_booster" -> coal();
            case "iron_booster" -> iron();
            case "gold_booster" -> gold();
            case "diamond_booster" -> diamond();
            case "emerald_booster" -> emerald();
            case "redstone_booster" -> redstone();
            case "lapis_booster" -> lapis();
            case "glowstone_booster" -> glowstone();
            case "wheat_booster" -> wheat();
            case "carrot_booster" -> carrot();
            case "oak_booster" -> oak();
            case "birch_booster" -> birch();
            default -> null;
        };
    }

    public ItemStack coal() {
        return create(Material.COAL_BLOCK, "coal_booster", BoosterType.COAL, "§8Coal Booster", 3001);
    }

    public ItemStack iron() {
        return create(Material.IRON_BLOCK, "iron_booster", BoosterType.IRON, "§fIron Booster", 3002);
    }

    public ItemStack gold() {
        return create(Material.GOLD_BLOCK, "gold_booster", BoosterType.GOLD, "§6Gold Booster", 3003);
    }

    public ItemStack diamond() {
        return create(Material.DIAMOND_BLOCK, "diamond_booster", BoosterType.DIAMOND, "§bDiamond Booster", 3004);
    }

    public ItemStack emerald() {
        return create(Material.EMERALD_BLOCK, "emerald_booster", BoosterType.EMERALD, "§aEmerald Booster", 3005);
    }

    public ItemStack redstone() {
        return create(Material.REDSTONE_BLOCK, "redstone_booster", BoosterType.REDSTONE, "§cRedstone Booster", 3006);
    }

    public ItemStack lapis() {
        return create(Material.LAPIS_BLOCK, "lapis_booster", BoosterType.LAPIS, "§9Lapis Booster", 3007);
    }

    public ItemStack glowstone() {
        return create(Material.GLOWSTONE, "glowstone_booster", BoosterType.GLOWSTONE, "§eGlowstone Booster", 3009);
    }

    public ItemStack wheat() {
        return create(Material.HAY_BLOCK, "wheat_booster", BoosterType.WHEAT, "§6Wheat Booster", 3010);
    }

    public ItemStack carrot() {
        ItemStack item = TexturedHeads.hashed("2448c183a7640867e42118e69c3f4d15db1ffb0d93646b77078ecedca2a43454");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyBoosterData(meta, "carrot_booster", BoosterType.CARROT);
            meta.setDisplayName("§6Carrot Harvest Booster");
            meta.setLore(BoosterStats.createItemLore(BoosterType.CARROT));
            meta.setCustomModelData(3013);
            ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack oak() {
        return create(Material.OAK_LOG, "oak_booster", BoosterType.OAK, "§6Oak Crit Damage Booster", 3011);
    }

    public ItemStack birch() {
        return create(Material.BIRCH_LOG, "birch_booster", BoosterType.BIRCH, "§fBirch Crit Chance Booster", 3012);
    }

    public ItemStack random() {
        BoosterType[] types = BoosterType.values();
        return of(types[ThreadLocalRandom.current().nextInt(types.length)]);
    }

    public List<ItemStack> showcase() {
        List<ItemStack> pool = new ArrayList<>();
        for (BoosterType type : BoosterType.values()) {
            ItemStack booster = of(type);
            if (booster != null && !booster.getType().isAir()) {
                pool.add(booster);
            }
        }
        return pool;
    }

    public ItemStack of(BoosterType type) {
        if (type == null) {
            return random();
        }
        return switch (type) {
            case COAL -> coal();
            case IRON -> iron();
            case GOLD -> gold();
            case DIAMOND -> diamond();
            case EMERALD -> emerald();
            case REDSTONE -> redstone();
            case LAPIS -> lapis();
            case GLOWSTONE -> glowstone();
            case WHEAT -> wheat();
            case CARROT -> carrot();
            case OAK -> oak();
            case BIRCH -> birch();
        };
    }

    private ItemStack create(
            Material material,
            String itemId,
            BoosterType boosterType,
            String displayName,
            int modelData
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyBoosterData(meta, itemId, boosterType);
            meta.setDisplayName(displayName);
            meta.setLore(BoosterStats.createItemLore(boosterType));
            meta.setCustomModelData(modelData);
            ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyBoosterData(ItemMeta meta, String itemId, BoosterType boosterType) {
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(
                ItemKeys.boosterType(),
                PersistentDataType.STRING,
                boosterType.name()
        );
        meta.setMaxStackSize(1);
    }
}
