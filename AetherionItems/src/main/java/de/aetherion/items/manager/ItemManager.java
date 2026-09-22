package de.aetherion.items.manager;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.ArmorAppearance;
import de.aetherion.items.item.BossWeaponLook;
import de.aetherion.items.item.ItemLore;
import de.aetherion.items.model.BoosterStats;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final AetherionItems plugin;

    public ItemManager(AetherionItems plugin) {
        this.plugin = plugin;
    }

    public AetherionItems getPlugin() {
        return plugin;
    }

    public NamespacedKey getItemKey() {
        return ItemKeys.item();
    }

    public boolean isAetherionItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public boolean isShortbow(ItemStack item) {
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return false;
        }

        Byte flag = meta.getPersistentDataContainer().get(
                ItemKeys.shortbow(),
                PersistentDataType.BYTE
        );

        if (flag != null && flag == 1) {
            return true;
        }

        String itemId = getItemId(item);
        return itemId != null && itemId.toLowerCase().contains("shortbow");
    }

    public int getShortbowIntervalTicks(ItemStack item) {
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return 20;
        }

        Integer interval = meta.getPersistentDataContainer().get(
                ItemKeys.shortbowInterval(),
                PersistentDataType.INTEGER
        );

        return interval == null ? 20 : Math.max(2, interval);
    }

    public boolean isLongbow(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta != null && meta.getPersistentDataContainer().has(ItemKeys.longbow(), PersistentDataType.BYTE)) {
            return true;
        }
        String itemId = getItemId(item);
        return itemId != null && itemId.toLowerCase().contains("longbow");
    }

    public int getLongbowChargeTicks(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return 32;
        }
        Integer charge = meta.getPersistentDataContainer().get(ItemKeys.longbowCharge(), PersistentDataType.INTEGER);
        return charge == null ? 32 : Math.max(12, charge);
    }

    public boolean needsAmmo(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(ItemKeys.needsAmmo(), PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public int getPoisonTicks(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return 0;
        }
        Integer ticks = meta.getPersistentDataContainer().get(ItemKeys.poisonTicks(), PersistentDataType.INTEGER);
        if (ticks != null) {
            return Math.max(0, ticks);
        }
        String itemId = getItemId(item);
        return "venom_dagger".equalsIgnoreCase(itemId) ? 80 : 0;
    }

    public boolean isWand(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta != null) {
            Byte flag = meta.getPersistentDataContainer().get(ItemKeys.wand(), PersistentDataType.BYTE);
            if (flag != null && flag == 1) {
                return true;
            }
        }
        String itemId = getItemId(item);
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase();
        return id.equals("wooden_rod") || id.equals("copper_rod") || id.equals("frost_shard")
                || id.equals("mender_staff") || id.equals("ember_rod");
    }

    public String getWandType(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta != null) {
            String stored = meta.getPersistentDataContainer().get(ItemKeys.wandType(), PersistentDataType.STRING);
            if (stored != null && !stored.isBlank()) {
                return stored.toLowerCase();
            }
        }
        String itemId = getItemId(item);
        if (itemId == null) {
            return "lightning";
        }
        return switch (itemId.toLowerCase()) {
            case "copper_rod" -> "chain";
            case "frost_shard" -> "frost";
            case "mender_staff" -> "heal";
            case "ember_rod" -> "ember";
            default -> "lightning";
        };
    }

    public double getWandRange(ItemStack item) {
        return readMetaDouble(item, ItemKeys.wandRange(), 30.0);
    }

    public long getWandCooldownMs(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return 10_000L;
        }
        Long value = meta.getPersistentDataContainer().get(ItemKeys.wandCooldown(), PersistentDataType.LONG);
        return value == null ? 10_000L : Math.max(1_000L, value);
    }

    public double getWandBlast(ItemStack item) {
        return readMetaDouble(item, ItemKeys.wandBlast(), 2.6);
    }

    public int getWandChain(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.wandChain(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public int getWandSlowTicks(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.wandSlowTicks(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public boolean isCustomCrossbow(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta != null) {
            Byte flag = meta.getPersistentDataContainer().get(ItemKeys.crossbow(), PersistentDataType.BYTE);
            if (flag != null && flag == 1) {
                return true;
            }
        }
        String itemId = getItemId(item);
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase();
        // Legacy id kept for recipes/profiles; item is a BOW now.
        if ("hunter_crossbow".equals(id)) {
            return false;
        }
        return id.contains("crossbow");
    }

    private double readMetaDouble(ItemStack item, org.bukkit.NamespacedKey key, double fallback) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return fallback;
        }
        Double value = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return value == null ? fallback : value;
    }

    public String getItemId(ItemStack item) {
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer().get(
                ItemKeys.item(),
                PersistentDataType.STRING
        );
    }

    public ItemProfile getProfile(ItemStack item) {
        return ItemProfile.fromItemId(getItemId(item));
    }

    public Rarity getRarity(ItemStack item) {
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return null;
        }

        String rarity = meta.getPersistentDataContainer().get(
                ItemKeys.rarity(),
                PersistentDataType.STRING
        );

        if (rarity == null) {
            return null;
        }

        try {
            return Rarity.valueOf(rarity);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public BoosterType getBoosterType(ItemStack item) {
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return null;
        }

        String type = meta.getPersistentDataContainer().get(
                ItemKeys.boosterType(),
                PersistentDataType.STRING
        );

        if (type == null) {
            return null;
        }

        try {
            return BoosterType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void refreshPlayerItems(Player player) {
        refreshPlayerItems(player, de.aetherion.items.item.GearTooltip.expanding(player));
    }

    public void refreshPlayerItems(Player player, boolean expanded) {
        if (player == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();

        for (ItemStack item : inventory.getContents()) {
            refreshItemLore(item, expanded);
        }

        for (ItemStack item : inventory.getArmorContents()) {
            refreshItemLore(item, expanded);
        }

        refreshItemLore(inventory.getItemInOffHand(), expanded);
    }

    public void refreshItemLore(ItemStack item) {
        refreshItemLore(item, false);
    }

    public void refreshItemLore(ItemStack item, boolean expanded) {
        if (!isAetherionItem(item)) {
            return;
        }

        de.aetherion.items.item.GearTooltip.restore(item);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String id = getItemId(item);
        BoosterType booster = getBoosterType(item);
        if (booster != null) {
            meta.setLore(BoosterStats.createItemLore(booster));
            meta.setMaxStackSize(de.aetherion.items.item.BoosterItems.MAX_STACK);
        } else {
            ItemProfile profile = getProfile(item);
            if (profile != ItemProfile.UNKNOWN && meta.getLore() != null) {
                ItemStats stats = migrateProgressionStats(meta, id);
                List<String> lore = new ArrayList<>(meta.getLore());
                ItemLore.updateDisplayedStats(lore, stats, profile);
                meta.setLore(lore);
            }
        }

        if (de.aetherion.items.item.DungeonCore.canInfuse(id)) {
            de.aetherion.items.item.DungeonCore.patchLore(meta, id, de.aetherion.items.item.DungeonCore.tier(item));
        }

        ArmorAppearance.apply(meta, id);
        BossWeaponLook.apply(meta, id);
        de.aetherion.items.item.ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        de.aetherion.items.item.GearTooltip.finish(item, this, expanded);
    }

    private ItemStats migrateProgressionStats(ItemMeta meta, String itemId) {
        ItemStats stats = statsFromMeta(meta);
        if (meta == null) {
            return stats;
        }
        if (de.aetherion.items.item.StarterSetBalance.apply(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        if (de.aetherion.items.item.AccessoryItems.migrate(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        if (de.aetherion.items.item.BossGearBalance.migrate(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        if (de.aetherion.items.item.FishingItems.migrate(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        if (de.aetherion.items.blueprint.BlueprintUpgrade.migrate(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        if (de.aetherion.items.item.CatcherItems.migrate(itemId, stats, meta)) {
            saveItemStats(meta, stats);
        }
        Integer revision = meta.getPersistentDataContainer().get(ItemKeys.statRev(), PersistentDataType.INTEGER);
        if (revision != null && revision >= de.aetherion.items.item.ProgressionItems.STAT_REV) {
            return stats;
        }
        de.aetherion.items.item.ProgressionItems.migrateBaseStats(itemId, stats);
        saveItemStats(meta, stats);
        meta.getPersistentDataContainer().set(
                ItemKeys.statRev(),
                PersistentDataType.INTEGER,
                de.aetherion.items.item.ProgressionItems.STAT_REV
        );
        return stats;
    }

    private ItemStats statsFromMeta(ItemMeta meta) {
        ItemStats stats = new ItemStats();
        if (meta == null) {
            return stats;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        stats.setMiningPower(readDouble(pdc, ItemKeys.miningPower()));
        stats.setFortune(readDouble(pdc, ItemKeys.fortune()));
        stats.setDamage(readDouble(pdc, ItemKeys.damage()));
        stats.setDefense(readDouble(pdc, ItemKeys.defense()));
        stats.setHealth(readDouble(pdc, ItemKeys.health()));
        stats.setSpread(readDouble(pdc, ItemKeys.spread()));
        stats.setAttackSpread(readDouble(pdc, ItemKeys.attackSpread()));
        stats.setSpeed(readDouble(pdc, ItemKeys.speed()));
        stats.setCatchRate(readDouble(pdc, ItemKeys.catchRate()));
        stats.setCritChance(readDouble(pdc, ItemKeys.critChance()));
        stats.setCritDamage(readDouble(pdc, ItemKeys.critDamage()));
        stats.setUndeadDamage(readDouble(pdc, ItemKeys.undeadDamage()));
        stats.setUndeadResist(readDouble(pdc, ItemKeys.undeadResist()));
        stats.setHarvestSpread(readDouble(pdc, ItemKeys.harvestSpread()));
        stats.setFishingSpeed(readDouble(pdc, ItemKeys.fishingSpeed()));
        stats.setFishingCatch(readDouble(pdc, ItemKeys.fishingCatch()));
        stats.setCoalBoosters(readInt(pdc, ItemKeys.coalBoosters()));
        stats.setIronBoosters(readInt(pdc, ItemKeys.ironBoosters()));
        stats.setGoldBoosters(readInt(pdc, ItemKeys.goldBoosters()));
        stats.setDiamondBoosters(readInt(pdc, ItemKeys.diamondBoosters()));
        stats.setEmeraldBoosters(readInt(pdc, ItemKeys.emeraldBoosters()));
        stats.setRedstoneBoosters(readInt(pdc, ItemKeys.redstoneBoosters()));
        stats.setLapisBoosters(readInt(pdc, ItemKeys.lapisBoosters()));
        stats.setGlowstoneBoosters(readInt(pdc, ItemKeys.glowstoneBoosters()));
        stats.setWheatBoosters(readInt(pdc, ItemKeys.wheatBoosters()));
        stats.setCarrotBoosters(readInt(pdc, ItemKeys.carrotBoosters()));
        stats.setOakBoosters(readInt(pdc, ItemKeys.oakBoosters()));
        stats.setBirchBoosters(readInt(pdc, ItemKeys.birchBoosters()));
        return stats;
    }

    public boolean isSameAetherionItem(ItemStack actual, ItemStack expected) {
        String expectedId = getItemId(expected);

        if (expectedId == null) {
            return actual != null
                    && expected != null
                    && actual.getType() == expected.getType();
        }

        return expectedId.equals(getItemId(actual));
    }

    public ItemStats getItemStats(ItemStack item) {
        ItemStats stats = new ItemStats();
        ItemMeta meta = metaOf(item);

        if (meta == null) {
            return stats;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        stats.setMiningPower(readDouble(pdc, ItemKeys.miningPower()));
        stats.setFortune(readDouble(pdc, ItemKeys.fortune()));
        stats.setDamage(readDouble(pdc, ItemKeys.damage()));
        stats.setDefense(readDouble(pdc, ItemKeys.defense()));
        stats.setHealth(readDouble(pdc, ItemKeys.health()));
        stats.setSpread(readDouble(pdc, ItemKeys.spread()));
        stats.setAttackSpread(readDouble(pdc, ItemKeys.attackSpread()));

        stats.setSpeed(readDouble(pdc, ItemKeys.speed()));
        stats.setCatchRate(readDouble(pdc, ItemKeys.catchRate()));
        stats.setCritChance(readDouble(pdc, ItemKeys.critChance()));
        stats.setCritDamage(readDouble(pdc, ItemKeys.critDamage()));
        stats.setUndeadDamage(readDouble(pdc, ItemKeys.undeadDamage()));
        stats.setUndeadResist(readDouble(pdc, ItemKeys.undeadResist()));
        stats.setHarvestSpread(readDouble(pdc, ItemKeys.harvestSpread()));
        stats.setFishingSpeed(readDouble(pdc, ItemKeys.fishingSpeed()));
        stats.setFishingCatch(readDouble(pdc, ItemKeys.fishingCatch()));

        stats.setCoalBoosters(readInt(pdc, ItemKeys.coalBoosters()));
        stats.setIronBoosters(readInt(pdc, ItemKeys.ironBoosters()));
        stats.setGoldBoosters(readInt(pdc, ItemKeys.goldBoosters()));
        stats.setDiamondBoosters(readInt(pdc, ItemKeys.diamondBoosters()));
        stats.setEmeraldBoosters(readInt(pdc, ItemKeys.emeraldBoosters()));
        stats.setRedstoneBoosters(readInt(pdc, ItemKeys.redstoneBoosters()));
        stats.setLapisBoosters(readInt(pdc, ItemKeys.lapisBoosters()));
        stats.setGlowstoneBoosters(readInt(pdc, ItemKeys.glowstoneBoosters()));
        stats.setWheatBoosters(readInt(pdc, ItemKeys.wheatBoosters()));
        stats.setCarrotBoosters(readInt(pdc, ItemKeys.carrotBoosters()));
        stats.setOakBoosters(readInt(pdc, ItemKeys.oakBoosters()));
        stats.setBirchBoosters(readInt(pdc, ItemKeys.birchBoosters()));

        return stats;
    }

    public void applyItemData(ItemMeta meta, Rarity rarity, ItemStats stats) {
        if (meta == null || stats == null) {
            return;
        }

        if (rarity != null) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.rarity(),
                    PersistentDataType.STRING,
                    rarity.name()
            );
            de.aetherion.items.item.TooltipStyle.apply(meta, rarity);
        }

        saveItemStats(meta, stats);
    }

    public void saveItemStats(ItemMeta meta, ItemStats stats) {
        if (meta == null || stats == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(ItemKeys.miningPower(), PersistentDataType.DOUBLE, stats.getMiningPower());
        pdc.set(ItemKeys.fortune(), PersistentDataType.DOUBLE, stats.getFortune());
        pdc.set(ItemKeys.damage(), PersistentDataType.DOUBLE, stats.getDamage());
        pdc.set(ItemKeys.defense(), PersistentDataType.DOUBLE, stats.getDefense());
        pdc.set(ItemKeys.health(), PersistentDataType.DOUBLE, stats.getHealth());
        pdc.set(ItemKeys.spread(), PersistentDataType.DOUBLE, stats.getSpread());
        pdc.set(ItemKeys.attackSpread(), PersistentDataType.DOUBLE, stats.getAttackSpread());
        pdc.set(ItemKeys.speed(), PersistentDataType.DOUBLE, stats.getSpeed());
        pdc.set(ItemKeys.catchRate(), PersistentDataType.DOUBLE, stats.getCatchRate());
        pdc.set(ItemKeys.critChance(), PersistentDataType.DOUBLE, stats.getCritChance());
        pdc.set(ItemKeys.critDamage(), PersistentDataType.DOUBLE, stats.getCritDamage());
        pdc.set(ItemKeys.undeadDamage(), PersistentDataType.DOUBLE, stats.getUndeadDamage());
        pdc.set(ItemKeys.undeadResist(), PersistentDataType.DOUBLE, stats.getUndeadResist());
        pdc.set(ItemKeys.harvestSpread(), PersistentDataType.DOUBLE, stats.getHarvestSpread());
        pdc.set(ItemKeys.fishingSpeed(), PersistentDataType.DOUBLE, stats.getFishingSpeed());
        pdc.set(ItemKeys.fishingCatch(), PersistentDataType.DOUBLE, stats.getFishingCatch());

        pdc.set(ItemKeys.coalBoosters(), PersistentDataType.INTEGER, stats.getCoalBoosters());
        pdc.set(ItemKeys.ironBoosters(), PersistentDataType.INTEGER, stats.getIronBoosters());
        pdc.set(ItemKeys.goldBoosters(), PersistentDataType.INTEGER, stats.getGoldBoosters());
        pdc.set(ItemKeys.diamondBoosters(), PersistentDataType.INTEGER, stats.getDiamondBoosters());
        pdc.set(ItemKeys.emeraldBoosters(), PersistentDataType.INTEGER, stats.getEmeraldBoosters());
        pdc.set(ItemKeys.redstoneBoosters(), PersistentDataType.INTEGER, stats.getRedstoneBoosters());
        pdc.set(ItemKeys.lapisBoosters(), PersistentDataType.INTEGER, stats.getLapisBoosters());
        pdc.set(ItemKeys.glowstoneBoosters(), PersistentDataType.INTEGER, stats.getGlowstoneBoosters());
        pdc.set(ItemKeys.wheatBoosters(), PersistentDataType.INTEGER, stats.getWheatBoosters());
        pdc.set(ItemKeys.carrotBoosters(), PersistentDataType.INTEGER, stats.getCarrotBoosters());
        pdc.set(ItemKeys.oakBoosters(), PersistentDataType.INTEGER, stats.getOakBoosters());
        pdc.set(ItemKeys.birchBoosters(), PersistentDataType.INTEGER, stats.getBirchBoosters());
    }

    public double getStat(ItemStack item, ItemCapability capability) {
        ItemMeta meta = metaOf(item);

        if (meta == null || capability == null) {
            return 0.0;
        }

        Double value = meta.getPersistentDataContainer().get(
                ItemKeys.forCapability(capability),
                PersistentDataType.DOUBLE
        );

        return value != null ? value : 0.0;
    }

    public ItemStack applyUpgradeProgress(
            ItemStack result,
            ItemStack previousItem,
            ItemStack previousTemplate
    ) {
        if (result == null || previousItem == null || !isAetherionItem(previousItem)) {
            return result;
        }

        ItemStats oldActual = getItemStats(previousItem);
        boolean hoeProgress = de.aetherion.items.item.FarmingHoeProgress.isHoe(previousItem);
        boolean rodProgress = de.aetherion.items.item.FishingRodProgress.canLevel(previousItem);
        boolean axeProgress = de.aetherion.items.item.ForagingAxeProgress.isAxe(previousItem);
        boolean gaffProgress = de.aetherion.items.item.CatcherGaffProgress.canLevel(previousItem);

        if (oldActual.getTotalBoosters() <= 0 && !hoeProgress && !rodProgress && !axeProgress && !gaffProgress) {
            return result;
        }

        ItemStats oldBase = previousTemplate != null
                ? getItemStats(previousTemplate)
                : new ItemStats();
        ItemStats merged = ItemStats.upgradeFrom(oldActual, oldBase, getItemStats(result));

        ItemMeta meta = result.getItemMeta();

        if (meta == null) {
            return result;
        }

        saveItemStats(meta, merged);

        java.util.List<String> lore = meta.hasLore()
                ? new java.util.ArrayList<>(meta.getLore())
                : new java.util.ArrayList<>();

        de.aetherion.items.item.ItemLore.updateDisplayedStats(lore, merged, getProfile(result));
        meta.setLore(lore);
        de.aetherion.items.item.ItemPresentation.polish(meta);
        result.setItemMeta(meta);
        de.aetherion.items.item.FarmingHoeProgress.copy(previousItem, result);
        de.aetherion.items.item.FishingRodProgress.copy(previousItem, result);
        de.aetherion.items.item.ForagingAxeProgress.copy(previousItem, result);
        de.aetherion.items.item.CatcherGaffProgress.copy(previousItem, result);
        return result;
    }

    private ItemMeta metaOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }

        return item.getItemMeta();
    }

    private double readDouble(PersistentDataContainer pdc, NamespacedKey key) {
        Double value = pdc.get(key, PersistentDataType.DOUBLE);
        return value != null ? value : 0.0;
    }

    private int readInt(PersistentDataContainer pdc, NamespacedKey key) {
        Integer value = pdc.get(key, PersistentDataType.INTEGER);
        return value != null ? value : 0;
    }
}
