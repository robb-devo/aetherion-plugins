package de.aetherion.items.model;

import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.item.ItemLore;
import de.aetherion.items.item.ItemPresentation;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class BoosterApplier {

    public enum Status {
        APPLIED,
        NOT_ITEM,
        NO_RARITY,
        FULL,
        NOT_ALLOWED
    }

    private BoosterApplier() {
    }

    public static Status apply(ItemManager items, ItemStack item, BoosterType type) {
        return apply(items, item, type, 1);
    }

    public static Status apply(ItemManager items, ItemStack item, BoosterType type, int times) {
        if (items == null || item == null || type == null || times <= 0) {
            return Status.NOT_ITEM;
        }
        Status last = Status.NOT_ITEM;
        for (int i = 0; i < times; i++) {
            last = applyOnce(items, item, type);
            if (last != Status.APPLIED) {
                return last;
            }
        }
        return last;
    }

    private static Status applyOnce(ItemManager items, ItemStack item, BoosterType type) {
        if (!items.isAetherionItem(item) || items.getBoosterType(item) != null) {
            return Status.NOT_ITEM;
        }
        ItemProfile profile = items.getProfile(item);
        Rarity rarity = items.getRarity(item);
        if (rarity == null) {
            return Status.NO_RARITY;
        }
        ItemStats stats = items.getItemStats(item);
        if (stats.getTotalBoosters() >= BoosterLimits.MAX_TOTAL) {
            return Status.FULL;
        }
        if (type.isCore()) {
            if (!profile.allowsCoreBoosters()) {
                return Status.NOT_ALLOWED;
            }
            applyCore(stats, profile, type, rarity);
        } else {
            if (!profile.allowsSpecialBooster(type)) {
                return Status.NOT_ALLOWED;
            }
            applySpecial(stats, type, rarity);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Status.NOT_ITEM;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        items.saveItemStats(meta, stats);
        List<String> existing = meta.getLore();
        if (existing != null) {
            List<String> lore = new ArrayList<>(existing);
            ItemLore.updateDisplayedStats(lore, stats, profile);
            meta.setLore(lore);
        }
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return Status.APPLIED;
    }

    /** Reverse one socketed booster. Counts and flats drop together. */
    public static Status remove(ItemManager items, ItemStack item, BoosterType type) {
        if (items == null || item == null || type == null || !items.isAetherionItem(item)) {
            return Status.NOT_ITEM;
        }
        ItemStats stats = items.getItemStats(item);
        if (stats.boosterCount(type) <= 0) {
            return Status.NOT_ITEM;
        }
        ItemProfile profile = items.getProfile(item);
        Rarity rarity = items.getRarity(item);
        if (rarity == null) {
            return Status.NO_RARITY;
        }
        if (type.isCore()) {
            removeCore(stats, profile, type, rarity);
        } else {
            removeSpecial(stats, type, rarity);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Status.NOT_ITEM;
        }
        items.saveItemStats(meta, stats);
        List<String> existing = meta.getLore();
        if (existing != null) {
            List<String> lore = new ArrayList<>(existing);
            ItemLore.updateDisplayedStats(lore, stats, profile);
            meta.setLore(lore);
        }
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return Status.APPLIED;
    }

    private static void applyCore(ItemStats stats, ItemProfile profile, BoosterType type, Rarity rarity) {
        double amount = BoosterStats.getCoreFlat(type, rarity);
        if (profile.hasCapability(ItemCapability.MINING_POWER)) {
            stats.setMiningPower(stats.getMiningPower() + amount);
        }
        if (profile.hasCapability(ItemCapability.FORTUNE)) {
            stats.setFortune(stats.getFortune() + amount);
        }
        if (profile.hasCapability(ItemCapability.DAMAGE)) {
            stats.setDamage(stats.getDamage() + amount);
        }
        if (profile.hasCapability(ItemCapability.DEFENSE)) {
            stats.setDefense(stats.getDefense() + amount);
        }
        switch (type) {
            case COAL -> stats.setCoalBoosters(stats.getCoalBoosters() + 1);
            case IRON -> stats.setIronBoosters(stats.getIronBoosters() + 1);
            case GOLD -> stats.setGoldBoosters(stats.getGoldBoosters() + 1);
            case DIAMOND -> stats.setDiamondBoosters(stats.getDiamondBoosters() + 1);
            default -> {
            }
        }
    }

    private static void applySpecial(ItemStats stats, BoosterType type, Rarity rarity) {
        double value = BoosterStats.getSpecialStat(type, rarity);
        switch (type) {
            case EMERALD -> {
                stats.setSpread(stats.getSpread() + value);
                stats.setEmeraldBoosters(stats.getEmeraldBoosters() + 1);
            }
            case REDSTONE -> {
                stats.setAttackSpread(stats.getAttackSpread() + value);
                stats.setRedstoneBoosters(stats.getRedstoneBoosters() + 1);
            }
            case LAPIS -> {
                stats.setHealth(stats.getHealth() + value);
                stats.setLapisBoosters(stats.getLapisBoosters() + 1);
            }
            case GLOWSTONE -> {
                stats.setSpeed(stats.getSpeed() + value);
                stats.setGlowstoneBoosters(stats.getGlowstoneBoosters() + 1);
            }
            case WHEAT -> {
                stats.setCatchRate(stats.getCatchRate() + value);
                stats.setWheatBoosters(stats.getWheatBoosters() + 1);
            }
            case CARROT -> {
                stats.setHarvestSpread(stats.getHarvestSpread() + value);
                stats.setCarrotBoosters(stats.getCarrotBoosters() + 1);
            }
            case OAK -> {
                stats.setCritDamage(stats.getCritDamage() + value);
                stats.setOakBoosters(stats.getOakBoosters() + 1);
            }
            case BIRCH -> {
                stats.setCritChance(stats.getCritChance() + value);
                stats.setBirchBoosters(stats.getBirchBoosters() + 1);
            }
            default -> {
            }
        }
    }

    private static void removeCore(ItemStats stats, ItemProfile profile, BoosterType type, Rarity rarity) {
        double amount = BoosterStats.getCoreFlat(type, rarity);
        if (profile.hasCapability(ItemCapability.MINING_POWER)) {
            stats.setMiningPower(Math.max(0.0, stats.getMiningPower() - amount));
        }
        if (profile.hasCapability(ItemCapability.FORTUNE)) {
            stats.setFortune(Math.max(0.0, stats.getFortune() - amount));
        }
        if (profile.hasCapability(ItemCapability.DAMAGE)) {
            stats.setDamage(Math.max(0.0, stats.getDamage() - amount));
        }
        if (profile.hasCapability(ItemCapability.DEFENSE)) {
            stats.setDefense(Math.max(0.0, stats.getDefense() - amount));
        }
        switch (type) {
            case COAL -> stats.setCoalBoosters(Math.max(0, stats.getCoalBoosters() - 1));
            case IRON -> stats.setIronBoosters(Math.max(0, stats.getIronBoosters() - 1));
            case GOLD -> stats.setGoldBoosters(Math.max(0, stats.getGoldBoosters() - 1));
            case DIAMOND -> stats.setDiamondBoosters(Math.max(0, stats.getDiamondBoosters() - 1));
            default -> {
            }
        }
    }

    private static void removeSpecial(ItemStats stats, BoosterType type, Rarity rarity) {
        double value = BoosterStats.getSpecialStat(type, rarity);
        switch (type) {
            case EMERALD -> {
                stats.setSpread(Math.max(0.0, stats.getSpread() - value));
                stats.setEmeraldBoosters(Math.max(0, stats.getEmeraldBoosters() - 1));
            }
            case REDSTONE -> {
                stats.setAttackSpread(Math.max(0.0, stats.getAttackSpread() - value));
                stats.setRedstoneBoosters(Math.max(0, stats.getRedstoneBoosters() - 1));
            }
            case LAPIS -> {
                stats.setHealth(Math.max(0.0, stats.getHealth() - value));
                stats.setLapisBoosters(Math.max(0, stats.getLapisBoosters() - 1));
            }
            case GLOWSTONE -> {
                stats.setSpeed(Math.max(0.0, stats.getSpeed() - value));
                stats.setGlowstoneBoosters(Math.max(0, stats.getGlowstoneBoosters() - 1));
            }
            case WHEAT -> {
                stats.setCatchRate(Math.max(0.0, stats.getCatchRate() - value));
                stats.setWheatBoosters(Math.max(0, stats.getWheatBoosters() - 1));
            }
            case CARROT -> {
                stats.setHarvestSpread(Math.max(0.0, stats.getHarvestSpread() - value));
                stats.setCarrotBoosters(Math.max(0, stats.getCarrotBoosters() - 1));
            }
            case OAK -> {
                stats.setCritDamage(Math.max(0.0, stats.getCritDamage() - value));
                stats.setOakBoosters(Math.max(0, stats.getOakBoosters() - 1));
            }
            case BIRCH -> {
                stats.setCritChance(Math.max(0.0, stats.getCritChance() - value));
                stats.setBirchBoosters(Math.max(0, stats.getBirchBoosters() - 1));
            }
            default -> {
            }
        }
    }
}
