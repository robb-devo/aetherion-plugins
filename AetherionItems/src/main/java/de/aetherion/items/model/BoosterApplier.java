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
}
