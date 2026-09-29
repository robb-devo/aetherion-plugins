package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.dungeon.DungeonArmor;
import de.aetherion.items.economy.TexturedHeads;
import de.aetherion.items.dungeon.DungeonCalling;
import de.aetherion.items.dungeon.DungeonGearTier;
import de.aetherion.items.dungeon.DungeonPiece;
import de.aetherion.items.dungeon.DungeonRelic;
import de.aetherion.items.dungeon.DungeonWeaponKind;
import de.aetherion.items.dungeon.SetWeaponKind;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterStats;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CustomItem {

    private final ItemManager itemManager;
    private final ProgressionItems progression;
    private final FarmingItems farming;
    private final ForagingItems foraging;
    private final FishingItems fishing;
    private final CatcherItems catcher;
    private final AccessoryItems accessories;
    private final BoosterItems boosters;
    private final List<java.util.function.Function<String, ItemStack>> idFactories;
    private String lastItemId;
    private ItemStats lastStats;

    public CustomItem(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.progression = new ProgressionItems(itemManager);
        this.farming = new FarmingItems(itemManager);
        this.foraging = new ForagingItems(itemManager);
        this.fishing = new FishingItems(itemManager);
        this.catcher = new CatcherItems(itemManager);
        this.accessories = new AccessoryItems(itemManager);
        this.boosters = new BoosterItems();
        this.idFactories = List.of(
                progression::byId,
                farming::byId,
                foraging::byId,
                fishing::byId,
                catcher::byId,
                accessories::byId,
                boosters::byId,
                this::createDungeonFromId
        );
    }

    public ProgressionItems progression() {
        return progression;
    }

    public FarmingItems farming() {
        return farming;
    }

    public ForagingItems foraging() {
        return foraging;
    }

    public FishingItems fishing() {
        return fishing;
    }

    public CatcherItems catcher() {
        return catcher;
    }

    public AccessoryItems accessories() {
        return accessories;
    }

    public BoosterItems boosters() {
        return boosters;
    }


    /*
     * =========================================================
     * HIDE VANILLA ITEM INFORMATION
     * =========================================================
     */

    private void hideVanillaAttributes(ItemMeta meta) {

        NamespacedKey hiddenAttributeKey =
                ItemKeys.key("hidden_attribute");

        AttributeModifier hiddenAttributeModifier =
                new AttributeModifier(
                        hiddenAttributeKey,
                        0.0,
                        AttributeModifier.Operation.ADD_NUMBER
                );

        meta.addAttributeModifier(
                Attribute.GENERIC_LUCK,
                hiddenAttributeModifier
        );

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES
        );

        meta.addItemFlags(
                ItemFlag.HIDE_UNBREAKABLE
        );

        ArmorAppearance.apply(meta, lastItemId);
        BossWeaponLook.apply(meta, lastItemId);
        ItemPresentation.polish(meta);
    }


    /*
     * =========================================================
     * NORMAL BOOSTER SECTION
     * =========================================================
     */

    private List<String> createLore(
            List<String> baseLore,
            boolean showSpread,
            boolean showAttackSpread,
            boolean showHealth
    ) {
        if (lastItemId != null && !lastItemId.isBlank()) {
            return createLore(lastItemId, baseLore, lastStats);
        }

        List<String> lore = new ArrayList<>(baseLore);

        ItemLore.appendBoosterSections(
                lore,
                new ItemStats(),
                showSpread,
                showAttackSpread,
                showHealth
        );

        return lore;
    }


    private List<String> createLore(
            String itemId,
            List<String> baseLore
    ) {
        return createLore(itemId, baseLore, lastStats);
    }

    private List<String> createLore(
            String itemId,
            List<String> baseLore,
            ItemStats stats
    ) {
        List<String> lore = new ArrayList<>(baseLore);
        ItemProfile profile = ItemProfile.fromItemId(itemId);
        ItemStats safe = stats == null ? new ItemStats() : stats;
        ItemLore.updateDisplayedStats(lore, safe, profile);
        return lore;
    }


    private void setItemId(ItemMeta meta, String itemId) {
        this.lastItemId = itemId;
        meta.getPersistentDataContainer().set(
                itemManager.getItemKey(),
                PersistentDataType.STRING,
                itemId
        );
    }


    /*
     * =========================================================
     * ITEM DATA
     * =========================================================
     */

    private void applyItemData(
            ItemMeta meta,
            Rarity rarity,
            ItemStats stats
    ) {
        if (meta != null && stats != null) {
            String id = meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
            if (id == null || id.isBlank()) {
                id = lastItemId;
            }
            StarterSetBalance.ensureBase(id, stats, meta);
            this.lastStats = stats;
        }
        itemManager.applyItemData(meta, rarity, stats);
    }


    /*
     * =========================================================
     * BOOSTER DATA
     * =========================================================
     */

    private void applyBoosterData(
            ItemMeta meta,
            String itemId,
            BoosterType boosterType
    ) {

        setItemId(meta, itemId);

        meta.getPersistentDataContainer().set(
                ItemKeys.boosterType(),
                PersistentDataType.STRING,
                boosterType.name()
        );

        meta.setMaxStackSize(BoosterItems.MAX_STACK);
    }


    /*
     * =========================================================
     * COLORED LEATHER ARMOR HELPER
     * =========================================================
     */

    private void applyArmorColor(
            ItemMeta meta,
            Color color
    ) {

        if (meta instanceof LeatherArmorMeta leatherMeta) {

            leatherMeta.setColor(color);
        }
    }


    /*
     * =========================================================
     * BEGINNER PICKAXE
     * =========================================================
     */

    public ItemStack create() {

        ItemStack item =
                new ItemStack(
                        Material.DIAMOND_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "beginner_pickaxe");

            ItemStats stats =
                    new ItemStats();

            stats.setMiningPower(2);
            stats.setFortune(8);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aBeginner's Pickaxe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7⛏ Mining Power: §f+2",
                                    "§7💎 Fortune: §f+8",
                                    "§7✦ Spread: §f+0.00",
                                    "",
                                    "§8Starter Tool"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1001);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE PICKAXE
     * =========================================================
     */

    public ItemStack createSimplePickaxe() {

        ItemStack item =
                new ItemStack(
                        Material.WOODEN_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_pickaxe");

            ItemStats stats =
                    new ItemStats();

            stats.setMiningPower(14);
            stats.setFortune(22);
            stats.setSpread(1);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Pickaxe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7⛏ Mining Power: §f+14",
                                    "§7💎 Fortune: §f+22",
                                    "§7✦ Spread: §f+0.00",
                                    "",
                                    "§8First real Aetherion pick"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1002);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE AXE
     * =========================================================
     */

    public ItemStack createSimpleAxe() {

        ItemStack item =
                new ItemStack(
                        Material.WOODEN_AXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_axe");

            ItemStats stats =
                    new ItemStats();

            stats.setFortune(8.0);
            stats.setSpread(1.0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Axe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7💎 Fortune: §f+8.00",
                                    "§7✦ Spread: §f+1.00",
                                    "",
                                    "§7🪓 Woodcutting Tool",
                                    "",
                                    "§8First real Aetherion axe"
                            ),
                            true,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(1003);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE SWORD
     * =========================================================
     */

    public ItemStack createSimpleSword() {

        ItemStack item =
                new ItemStack(
                        Material.WOODEN_SWORD
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_sword");

            ItemStats stats =
                    new ItemStats();

            stats.setDamage(10.0);
            stats.setAttackSpread(2.0);
            stats.setCritChance(4.0);
            stats.setCritDamage(38.0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Sword"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7⚔ Damage: §f+10.00",
                                    "§7⚔ Attack Spread: §f+2.00",
                                    "§7✧ Crit Chance: §f+4.00%",
                                    "§7✧ Crit Damage: §f+38.00%",
                                    "",
                                    "§7⚔ Basic Weapon",
                                    "",
                                    "§8First real Aetherion sword"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(1004);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    public ItemStack createSplinterGlaive() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "splinter_glaive");
            ItemStats stats = new ItemStats();
            stats.setDamage(16.0);
            stats.setAttackSpread(3.0);
            stats.setCritChance(6.0);
            stats.setCritDamage(48.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.setDisplayName("§aSplinter Glaive");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+16.00",
                    "§7⚔ Attack Spread: §f+3.00",
                    "§7✧ Crit Chance: §f+6.00%",
                    "§7✧ Crit Damage: §f+48.00%",
                    "",
                    "§7Oak that learned to be a spear.",
                    "§8Aetherion Weapon"
            ), false, true, false));
            meta.setCustomModelData(1018);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createAshenCleaver() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ashen_cleaver");
            ItemStats stats = new ItemStats();
            stats.setDamage(24.0);
            stats.setAttackSpread(5.0);
            stats.setCritChance(8.0);
            stats.setCritDamage(58.0);
            applyItemData(meta, Rarity.RARE, stats);
            meta.setDisplayName("§bAshen Cleaver");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7⚔ Damage: §f+24.00",
                    "§7⚔ Attack Spread: §f+5.00",
                    "§7✧ Crit Chance: §f+8.00%",
                    "§7✧ Crit Damage: §f+58.00%",
                    "",
                    "§7Coal dust in the edge. The trees remember.",
                    "§8Aetherion Weapon"
            ), false, true, false));
            meta.setCustomModelData(1019);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createSimpleLongbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "simple_longbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(16.0);
            stats.setCritChance(4.0);
            stats.setCritDamage(42.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.getPersistentDataContainer().set(ItemKeys.longbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.longbowCharge(), PersistentDataType.INTEGER, 32);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§7Simple Longbow");
            meta.setLore(createLore(List.of(
                    "§7✦ §7UNCOMMON",
                    "",
                    "§7⚔ Damage: §f+16.00",
                    "§7✧ Crit Chance: §f+4.00%",
                    "§7✧ Crit Damage: §f+42.00%",
                    "",
                    "§eDraw: §f1.6s §8for a full shot.",
                    "§7Needs arrows. Slow, but it hits.",
                    "",
                    "§8First real hunting bow"
            ), false, true, false));
            meta.setCustomModelData(2210);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createSimpleShortbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "simple_shortbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(8.0);
            stats.setCritChance(3.0);
            stats.setCritDamage(38.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.shortbowInterval(), PersistentDataType.INTEGER, 30);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§7Simple Shortbow");
            meta.setLore(createLore(List.of(
                    "§7✦ §7UNCOMMON",
                    "",
                    "§7⚔ Damage: §f+8.00",
                    "§7✧ Crit Chance: §f+3.00%",
                    "§7✧ Crit Damage: §f+38.00%",
                    "",
                    "§6⚡ Fire Rate: §f1.50s",
                    "§7Hold right-click to fire.",
                    "§7Needs arrows.",
                    "",
                    "§8First real shortbow"
            ), false, true, false));
            meta.setCustomModelData(2202);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createWoodenRod() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "wooden_rod");
            ItemStats stats = new ItemStats();
            stats.setDamage(48.0);
            stats.setCritChance(5.0);
            stats.setCritDamage(50.0);
            applyItemData(meta, Rarity.COMMON, stats);
            meta.setDisplayName("§eWooden Rod");
            meta.setLore(createLore(List.of(
                    "§7✦ §fCOMMON",
                    "",
                    "§7⚔ Lightning: §f+48.00",
                    "§7✧ Crit Chance: §f+5.00%",
                    "§7✧ Crit Damage: §f+50.00%",
                    "",
                    "§eSomeone tried to fish with this.",
                    "§7It was a terrible fishing rod.",
                    "§7It is a much better lightning stick.",
                    "",
                    "§6Right-click: §7bolt where you look.",
                    "§8Range 30 · 10s cooldown"
            ), false, true, false));
            markWand(meta, "lightning", 30.0, 10_000L, 2.6, 0, 0);
            meta.setCustomModelData(2601);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createHollowLongbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "hollow_longbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(88.0);
            stats.setAttackSpread(12.0);
            stats.setCritChance(12.0);
            stats.setCritDamage(85.0);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            meta.getPersistentDataContainer().set(ItemKeys.longbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.longbowCharge(), PersistentDataType.INTEGER, 40);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§6Hollow Longbow");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⚔ Damage: §f+88.00",
                    "§7⚔ Attack Spread: §f+12.00",
                    "§7✧ Crit Chance: §f+12.00%",
                    "§7✧ Crit Damage: §f+85.00%",
                    "",
                    "§eDraw: §f2.0s §8for a full shot.",
                    "§7Needs arrows. Pierces one target.",
                    DungeonCore.BOSS_CORE_HINT,
                    "",
                    "§8Hollow Lurker · details later"
            ), false, true, false));
            meta.setCustomModelData(2211);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBoneKnife() {
        ItemStack item = new ItemStack(Material.STONE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "bone_knife");
            ItemStats stats = new ItemStats();
            stats.setDamage(8.0);
            stats.setCritChance(18.0);
            stats.setCritDamage(90.0);
            applyItemData(meta, Rarity.COMMON, stats);
            meta.setDisplayName("§fBone Knife");
            meta.setLore(createLore(List.of(
                    "§7✦ §fCOMMON",
                    "",
                    "§7⚔ Damage: §f+8.00",
                    "§7✧ Crit Chance: §f+18.00%",
                    "§7✧ Crit Damage: §f+90.00%",
                    "",
                    "§eCarved from something that",
                    "§edidn't stay buried.",
                    "",
                    "§8Cheap. Sharp. Mean on a crit."
            ), false, true, false));
            meta.setCustomModelData(1005);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createVenomDagger() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "venom_dagger");
            ItemStats stats = new ItemStats();
            stats.setDamage(12.0);
            stats.setCritChance(10.0);
            stats.setCritDamage(55.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.getPersistentDataContainer().set(ItemKeys.poisonTicks(), PersistentDataType.INTEGER, 80);
            meta.setDisplayName("§aVenom Dagger");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+12.00",
                    "§7✧ Crit Chance: §f+10.00%",
                    "§7✧ Crit Damage: §f+55.00%",
                    "",
                    "§2On hit: §7Poison for 4s.",
                    "§eA polite way to ruin",
                    "§esomeone's afternoon.",
                    "",
                    "§8Early toxin blade"
            ), false, true, false));
            meta.setCustomModelData(1006);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createIronLongbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "iron_longbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(32.0);
            stats.setCritChance(6.0);
            stats.setCritDamage(55.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.getPersistentDataContainer().set(ItemKeys.longbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.longbowCharge(), PersistentDataType.INTEGER, 28);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§aIron Longbow");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+32.00",
                    "§7✧ Crit Chance: §f+6.00%",
                    "§7✧ Crit Damage: §f+55.00%",
                    "",
                    "§eDraw: §f1.4s §8for a full shot.",
                    "§7Needs arrows. The hunting bow, grown up.",
                    "",
                    "§8Upgrade of the Longbow"
            ), false, true, false));
            meta.setCustomModelData(2212);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createReinforcedShortbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "reinforced_shortbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(15.0);
            stats.setCritChance(5.0);
            stats.setCritDamage(45.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.shortbowInterval(), PersistentDataType.INTEGER, 24);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName("§aReinforced Shortbow");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+15.00",
                    "§7✧ Crit Chance: §f+5.00%",
                    "§7✧ Crit Damage: §f+45.00%",
                    "",
                    "§6⚡ Fire Rate: §f1.20s",
                    "§7Hold right-click to fire.",
                    "§7Needs arrows.",
                    "",
                    "§8Upgrade of the Shortbow"
            ), false, true, false));
            meta.setCustomModelData(2203);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createHunterCrossbow() {
        // No custom weapon — plain leather only (NPC hand / leftover give paths).
        return new ItemStack(Material.LEATHER);
    }

    public ItemStack createCopperRod() {
        ItemStack item = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "copper_rod");
            ItemStats stats = new ItemStats();
            stats.setDamage(42.0);
            stats.setCritChance(6.0);
            stats.setCritDamage(55.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            markWand(meta, "chain", 28.0, 8_000L, 2.2, 2, 0);
            meta.setDisplayName("§eCopper Rod");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Lightning: §f+42.00",
                    "§7✧ Crit Chance: §f+6.00%",
                    "§7✧ Crit Damage: §f+55.00%",
                    "",
                    "§eThe wooden rod, but it learned to gossip.",
                    "§7Bolt jumps to 2 extra targets.",
                    "",
                    "§6Right-click: §7chain lightning.",
                    "§8Range 28 · 8s cooldown"
            ), false, true, false));
            meta.setCustomModelData(2602);
            meta.setUnbreakable(true);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createFrostShard() {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "frost_shard");
            ItemStats stats = new ItemStats();
            stats.setDamage(22.0);
            stats.setCritChance(4.0);
            stats.setCritDamage(40.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            markWand(meta, "frost", 24.0, 6_000L, 1.8, 0, 80);
            meta.setDisplayName("§bFrost Shard");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Frost: §f+22.00",
                    "§7✧ Crit Chance: §f+4.00%",
                    "§7✧ Crit Damage: §f+40.00%",
                    "",
                    "§bCold enough to make a skeleton shiver.",
                    "§7Slows whatever it hits.",
                    "",
                    "§6Right-click: §7frost burst.",
                    "§8Range 24 · 6s cooldown · Slow 4s"
            ), false, true, false));
            meta.setCustomModelData(2603);
            meta.setUnbreakable(true);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createRottenHelmet() {
        return leatherArmor(
                Material.LEATHER_HELMET,
                "rotten_helmet",
                "§aRotten Helmet",
                Rarity.UNCOMMON,
                Color.fromRGB(92, 118, 48),
                2301,
                rottenStats(3.0, 8.0, 5.0, 5.0),
                rottenLore("Helmet", "3.00", "8.00", "5.00", "5.00")
        );
    }

    public ItemStack createRottenChestplate() {
        return leatherArmor(
                Material.LEATHER_CHESTPLATE,
                "rotten_chestplate",
                "§aRotten Chestplate",
                Rarity.UNCOMMON,
                Color.fromRGB(92, 118, 48),
                2302,
                rottenStats(6.0, 14.0, 8.0, 8.0),
                rottenLore("Chestplate", "6.00", "14.00", "8.00", "8.00")
        );
    }

    public ItemStack createRottenLeggings() {
        return leatherArmor(
                Material.LEATHER_LEGGINGS,
                "rotten_leggings",
                "§aRotten Leggings",
                Rarity.UNCOMMON,
                Color.fromRGB(92, 118, 48),
                2303,
                rottenStats(5.0, 12.0, 6.0, 6.0),
                rottenLore("Leggings", "5.00", "12.00", "6.00", "6.00")
        );
    }

    public ItemStack createRottenBoots() {
        return leatherArmor(
                Material.LEATHER_BOOTS,
                "rotten_boots",
                "§aRotten Boots",
                Rarity.UNCOMMON,
                Color.fromRGB(92, 118, 48),
                2304,
                rottenStats(3.0, 8.0, 5.0, 5.0),
                rottenLore("Boots", "3.00", "8.00", "5.00", "5.00")
        );
    }

    public ItemStack createRottenCleaver() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "rotten_cleaver");
            ItemStats stats = new ItemStats();
            stats.setDamage(14.0);
            stats.setAttackSpread(3.0);
            stats.setCritChance(5.0);
            stats.setCritDamage(42.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.setDisplayName("§aRotten Cleaver");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+14.00",
                    "§7⚔ Attack Spread: §f+3.00",
                    "§7✧ Crit Chance: §f+5.00%",
                    "§7✧ Crit Damage: §f+42.00%",
                    "",
                    "§7Flesh learned to hold an edge.",
                    "§8Matches the Rotten set."
            ), false, true, false));
            meta.setCustomModelData(1020);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBoneHelmet() {
        return leatherArmor(
                Material.LEATHER_HELMET,
                "bone_helmet",
                "§fBone Helmet",
                Rarity.UNCOMMON,
                Color.fromRGB(236, 228, 205),
                2311,
                boneStats(3.0, 8.0, 1.5, 0.0),
                boneLore("Helmet", "3.00", "8.00", "1.50", null)
        );
    }

    public ItemStack createBoneChestplate() {
        return leatherArmor(
                Material.LEATHER_CHESTPLATE,
                "bone_chestplate",
                "§fBone Chestplate",
                Rarity.UNCOMMON,
                Color.fromRGB(236, 228, 205),
                2312,
                boneStats(6.0, 14.0, 2.0, 0.0),
                boneLore("Chestplate", "6.00", "14.00", "2.00", null)
        );
    }

    public ItemStack createBoneLeggings() {
        return leatherArmor(
                Material.LEATHER_LEGGINGS,
                "bone_leggings",
                "§fBone Leggings",
                Rarity.UNCOMMON,
                Color.fromRGB(236, 228, 205),
                2313,
                boneStats(5.0, 12.0, 1.5, 0.0),
                boneLore("Leggings", "5.00", "12.00", "1.50", null)
        );
    }

    public ItemStack createBoneBoots() {
        return leatherArmor(
                Material.LEATHER_BOOTS,
                "bone_boots",
                "§fBone Boots",
                Rarity.UNCOMMON,
                Color.fromRGB(236, 228, 205),
                2314,
                boneStats(3.0, 8.0, 1.0, 2.0),
                boneLore("Boots", "3.00", "8.00", "1.00", "2.00")
        );
    }

    public ItemStack createWebweaveHelmet() {
        return leatherArmor(
                Material.LEATHER_HELMET,
                "webweave_helmet",
                "§fWebweave Helmet",
                Rarity.UNCOMMON,
                Color.fromRGB(214, 208, 198),
                2321,
                webStats(1.0, 4.0, 3.0),
                webLore("Helmet", "1.00", "4.00", "3.00")
        );
    }

    public ItemStack createWebweaveChestplate() {
        return leatherArmor(
                Material.LEATHER_CHESTPLATE,
                "webweave_chestplate",
                "§fWebweave Chestplate",
                Rarity.UNCOMMON,
                Color.fromRGB(214, 208, 198),
                2322,
                webStats(4.0, 8.0, 4.0),
                webLore("Chestplate", "4.00", "8.00", "4.00")
        );
    }

    public ItemStack createWebweaveLeggings() {
        return leatherArmor(
                Material.LEATHER_LEGGINGS,
                "webweave_leggings",
                "§fWebweave Leggings",
                Rarity.UNCOMMON,
                Color.fromRGB(214, 208, 198),
                2323,
                webStats(3.0, 6.0, 4.0),
                webLore("Leggings", "3.00", "6.00", "4.00")
        );
    }

    public ItemStack createWebweaveBoots() {
        return leatherArmor(
                Material.LEATHER_BOOTS,
                "webweave_boots",
                "§fWebweave Boots",
                Rarity.UNCOMMON,
                Color.fromRGB(214, 208, 198),
                2324,
                webStats(1.0, 4.0, 6.0),
                webLore("Boots", "1.00", "4.00", "6.00")
        );
    }

    public ItemStack createWebweaveFang() {
        ItemStack item = new ItemStack(Material.STONE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "webweave_fang");
            ItemStats stats = new ItemStats();
            stats.setDamage(12.0);
            stats.setCritChance(14.0);
            stats.setCritDamage(55.0);
            stats.setSpeed(4.0);
            applyItemData(meta, Rarity.UNCOMMON, stats);
            meta.setDisplayName("§fWebweave Fang");
            meta.setLore(createLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7⚔ Damage: §f+12.00",
                    "§7✧ Crit Chance: §f+14.00%",
                    "§7✧ Crit Damage: §f+55.00%",
                    "§7⚡ Speed: §f+4.00%",
                    "",
                    "§7A fang the web decided to keep.",
                    "§8Matches the Webweave set."
            ), false, true, false));
            meta.setCustomModelData(1021);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStats rottenStats(double defense, double health, double undeadDamage, double undeadResist) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        stats.setUndeadDamage(undeadDamage);
        stats.setUndeadResist(undeadResist);
        return stats;
    }

    private List<String> rottenLore(String piece, String defense, String health, String damage, String resist) {
        return List.of(
                "§7✦ §aUNCOMMON",
                "",
                "§7🛡 Defense: §f+" + defense,
                "§7❤ Health: §f+" + health,
                "§7☠ Undead Damage: §f+" + damage + "%",
                "§7☠ Undead Resist: §f+" + resist + "%",
                "",
                "§2Rotten " + piece,
                "§7Smells like a bad idea. Works like a great one",
                "§7against anything that should stay dead.",
                "",
                "§8Full set: §7+10% Undead Damage & Resist"
        );
    }

    private ItemStats boneStats(double defense, double health, double critChance, double speed) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        stats.setCritChance(critChance);
        stats.setSpeed(speed);
        return stats;
    }

    private List<String> boneLore(String piece, String defense, String health, String crit, String speed) {
        List<String> lore = new java.util.ArrayList<>(List.of(
                "§7✦ §aUNCOMMON",
                "",
                "§7🛡 Defense: §f+" + defense,
                "§7❤ Health: §f+" + health,
                "§7✧ Crit Chance: §f+" + crit + "%"
        ));
        if (speed != null) {
            lore.add("§7✦ Speed: §f+" + speed + "%");
        }
        lore.addAll(List.of(
                "",
                "§fBone " + piece,
                "§7Assembled with more confidence than anatomy.",
                "",
                "§8Full set: §7+4% Crit Chance"
        ));
        return lore;
    }

    /*
     * =========================================================
     * IRONHIDE SET (Tank, EPIC)
     * =========================================================
     */

    private ItemStats ironhideStats(double defense, double health) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        return stats;
    }

    private List<String> ironhideLore(String piece, String defense, String health) {
        return List.of(
                "§7✦ §5EPIC",
                "",
                "§7🛡 Defense: §f+" + defense,
                "§7❤ Health: §f+" + health,
                "",
                "§7Ironhide " + piece,
                "§7Built to take a beating and ask for more.",
                "",
                "§8Full set: §7+20 Defense"
        );
    }

    public ItemStack createIronhideHelmet() {
        ItemStack item = new ItemStack(Material.IRON_HELMET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ironhide_helmet");
            applyItemData(meta, Rarity.EPIC, ironhideStats(20.0, 36.0));
            meta.setDisplayName("§5Ironhide Helmet");
            meta.setLore(createLore(ironhideLore("Helmet", "20.00", "36.00"), false, false, true));
            meta.setCustomModelData(2501);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createIronhideChestplate() {
        ItemStack item = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ironhide_chestplate");
            applyItemData(meta, Rarity.EPIC, ironhideStats(38.0, 56.0));
            meta.setDisplayName("§5Ironhide Chestplate");
            meta.setLore(createLore(ironhideLore("Chestplate", "38.00", "56.00"), false, false, true));
            meta.setCustomModelData(2502);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createIronhideLeggings() {
        ItemStack item = new ItemStack(Material.IRON_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ironhide_leggings");
            applyItemData(meta, Rarity.EPIC, ironhideStats(30.0, 44.0));
            meta.setDisplayName("§5Ironhide Leggings");
            meta.setLore(createLore(ironhideLore("Leggings", "30.00", "44.00"), false, false, true));
            meta.setCustomModelData(2503);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createIronhideBoots() {
        ItemStack item = new ItemStack(Material.IRON_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ironhide_boots");
            applyItemData(meta, Rarity.EPIC, ironhideStats(20.0, 34.0));
            meta.setDisplayName("§5Ironhide Boots");
            meta.setLore(createLore(ironhideLore("Boots", "20.00", "34.00"), false, false, true));
            meta.setCustomModelData(2504);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    /*
     * =========================================================
     * HEALER SET (Support, RARE)
     * =========================================================
     */

    private ItemStats healerStats(double defense, double health) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        return stats;
    }

    private List<String> healerLore(String piece, String defense, String health) {
        return List.of(
                "§7✦ §bRARE",
                "",
                "§7🛡 Defense: §f+" + defense,
                "§7❤ Health: §f+" + health,
                "",
                "§aHealer " + piece,
                "§7Woven with restorative emerald threads.",
                "",
                "§8Full set: §7Heal Aura (8 blocks, 2❤, 5s)"
        );
    }

    public ItemStack createHealerHelmet() {
        return leatherArmor(
                Material.LEATHER_HELMET,
                "healer_helmet",
                "§aHealer Helmet",
                Rarity.RARE,
                Color.fromRGB(80, 200, 80),
                2511,
                healerStats(14.0, 22.0),
                healerLore("Helmet", "14.00", "22.00")
        );
    }

    public ItemStack createHealerChestplate() {
        return leatherArmor(
                Material.LEATHER_CHESTPLATE,
                "healer_chestplate",
                "§aHealer Chestplate",
                Rarity.RARE,
                Color.fromRGB(80, 200, 80),
                2512,
                healerStats(20.0, 30.0),
                healerLore("Chestplate", "20.00", "30.00")
        );
    }

    public ItemStack createHealerLeggings() {
        return leatherArmor(
                Material.LEATHER_LEGGINGS,
                "healer_leggings",
                "§aHealer Leggings",
                Rarity.RARE,
                Color.fromRGB(80, 200, 80),
                2513,
                healerStats(18.0, 26.0),
                healerLore("Leggings", "18.00", "26.00")
        );
    }

    public ItemStack createHealerBoots() {
        return leatherArmor(
                Material.LEATHER_BOOTS,
                "healer_boots",
                "§aHealer Boots",
                Rarity.RARE,
                Color.fromRGB(80, 200, 80),
                2514,
                healerStats(12.0, 18.0),
                healerLore("Boots", "12.00", "18.00")
        );
    }

    /*
     * =========================================================
     * MENDER STAFF (Heal Wand, RARE)
     * =========================================================
     */

    public ItemStack createMenderStaff() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "mender_staff");
            ItemStats stats = new ItemStats();
            stats.setDamage(18.0);
            stats.setCritChance(4.0);
            stats.setCritDamage(40.0);
            applyItemData(meta, Rarity.RARE, stats);
            markWand(meta, "heal", 28.0, 5_000L, 2.4, 0, 0);
            meta.setDisplayName("§aMender Staff");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7💚 Heal: §f+4❤ (8 blocks)",
                    "§7✧ Crit Chance: §f+4.00%",
                    "§7✧ Crit Damage: §f+40.00%",
                    "",
                    "§aA staff infused with life energy.",
                    "§7Heals all nearby players.",
                    "",
                    "§6Right-click: §7group heal.",
                    "§8Range 28 · 5s cooldown"
            ), false, true, false));
            meta.setCustomModelData(2604);
            meta.setUnbreakable(true);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    /*
     * =========================================================
     * EMBER ROD (Fire Wand, RARE)
     * =========================================================
     */

    public ItemStack createEmberRod() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "ember_rod");
            ItemStats stats = new ItemStats();
            stats.setDamage(38.0);
            stats.setCritChance(5.0);
            stats.setCritDamage(50.0);
            applyItemData(meta, Rarity.RARE, stats);
            markWand(meta, "ember", 28.0, 5_000L, 2.4, 0, 0);
            meta.setDisplayName("§6Ember Rod");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7⚔ Fire: §f+38.00",
                    "§7✧ Crit Chance: §f+5.00%",
                    "§7✧ Crit Damage: §f+50.00%",
                    "",
                    "§6Forged from compressed redstone and flame.",
                    "§7Sets targets ablaze.",
                    "",
                    "§6Right-click: §7fire burst.",
                    "§8Range 28 · 5s cooldown"
            ), false, true, false));
            meta.setCustomModelData(2605);
            meta.setUnbreakable(true);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    /*
     * =========================================================
     * REINFORCED PICKAXE (Mining Tool, RARE)
     * =========================================================
     */

    public ItemStack createReinforcedPickaxe() {
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "reinforced_pickaxe");
            ItemStats stats = new ItemStats();
            stats.setMiningPower(18.0);
            stats.setFortune(28.0);
            stats.setSpread(3.0);
            applyItemData(meta, Rarity.RARE, stats);
            meta.setDisplayName("§bReinforced Pickaxe");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7⛏ Mining Power: §f+18.00",
                    "§7💎 Fortune: §f+28.00",
                    "§7✦ Spread: §f+3.00",
                    "",
                    "§7Compressed iron reinforces every strike.",
                    "",
                    "§8Aetherion Mining Tool"
            ), true, false, false));
            meta.setCustomModelData(1008);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    /*
     * =========================================================
     * OBSIDIAN MAUL (Tank Weapon, RARE)
     * =========================================================
     */

    public ItemStack createObsidianMaul() {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "obsidian_maul");
            ItemStats stats = new ItemStats();
            stats.setDamage(35.0);
            stats.setDefense(8.0);
            stats.setCritChance(3.0);
            stats.setCritDamage(35.0);
            applyItemData(meta, Rarity.RARE, stats);
            meta.setDisplayName("§8Obsidian Maul");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7⚔ Damage: §f+35.00",
                    "§7🛡 Defense: §f+8.00",
                    "§7✧ Crit Chance: §f+3.00%",
                    "§7✧ Crit Damage: §f+35.00%",
                    "",
                    "§8Slow. Heavy. Final.",
                    "§7A hammer carved from volcanic glass.",
                    "",
                    "§8Aetherion Tank Weapon"
            ), false, true, false));
            meta.setCustomModelData(2606);
            meta.setUnbreakable(true);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStats webStats(double defense, double health, double speed) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        stats.setSpeed(speed);
        return stats;
    }

    private List<String> webLore(String piece, String defense, String health, String speed) {
        return List.of(
                "§7✦ §aUNCOMMON",
                "",
                "§7🛡 Defense: §f+" + defense,
                "§7❤ Health: §f+" + health,
                "§7✦ Speed: §f+" + speed + "%",
                "",
                "§fWebweave " + piece,
                "§7Light as a cobweb. Almost as comforting.",
                "",
                "§8Full set: §7+5% Speed"
        );
    }

    private ItemStack leatherArmor(
            Material material,
            String id,
            String name,
            Rarity rarity,
            Color color,
            int model,
            ItemStats stats,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, id);
            applyItemData(meta, rarity, stats);
            meta.setDisplayName(name);
            meta.setLore(createLore(lore, false, false, true));
            meta.setCustomModelData(model);
            meta.setUnbreakable(true);
            applyArmorColor(meta, color);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void markWand(
            ItemMeta meta,
            String type,
            double range,
            long cooldownMs,
            double blast,
            int chain,
            int slowTicks
    ) {
        meta.getPersistentDataContainer().set(ItemKeys.wand(), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ItemKeys.wandType(), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(ItemKeys.wandRange(), PersistentDataType.DOUBLE, range);
        meta.getPersistentDataContainer().set(ItemKeys.wandCooldown(), PersistentDataType.LONG, cooldownMs);
        meta.getPersistentDataContainer().set(ItemKeys.wandBlast(), PersistentDataType.DOUBLE, blast);
        meta.getPersistentDataContainer().set(ItemKeys.wandChain(), PersistentDataType.INTEGER, chain);
        meta.getPersistentDataContainer().set(ItemKeys.wandSlowTicks(), PersistentDataType.INTEGER, slowTicks);
    }


    /*
     * =========================================================
     * SIMPLE HOE
     * =========================================================
     */

    public ItemStack createSimpleHoe() {

        ItemStack item =
                new ItemStack(
                        Material.WOODEN_HOE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_hoe");

            ItemStats stats =
                    new ItemStats();

            stats.setFortune(10.0);
            stats.setHarvestSpread(18.0);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Hoe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7🌾 Harvest: §f+18.00",
                                    "§7💎 Fortune: §f+10.00",
                                    "",
                                    "§7🌱 Farming Tool",
                                    "",
                                    "§8First real Aetherion hoe"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1005);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE HELMET
     * =========================================================
     */

    public ItemStack createSimpleHelmet() {

        ItemStack item =
                new ItemStack(
                        Material.LEATHER_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_helmet");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(1.0);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+1.00",
                                    "§7❤ Health: §f+0.00",
                                    "",
                                    "§8Basic Armor"
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2001);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE CHESTPLATE
     * =========================================================
     */

    public ItemStack createSimpleChestplate() {

        ItemStack item =
                new ItemStack(
                        Material.LEATHER_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_chestplate");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(3.0);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+3.00",
                                    "§7❤ Health: §f+0.00",
                                    "",
                                    "§8Basic Armor"
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2002);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE LEGGINGS
     * =========================================================
     */

    public ItemStack createSimpleLeggings() {

        ItemStack item =
                new ItemStack(
                        Material.LEATHER_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_leggings");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(2.0);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+2.00",
                                    "§7❤ Health: §f+0.00",
                                    "",
                                    "§8Basic Armor"
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2003);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SIMPLE BOOTS
     * =========================================================
     */

    public ItemStack createSimpleBoots() {

        ItemStack item =
                new ItemStack(
                        Material.LEATHER_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "simple_boots");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(1.0);
            stats.setSpread(0);
            stats.setAttackSpread(0);
            stats.setHealth(0);

            applyItemData(
                    meta,
                    Rarity.UNCOMMON,
                    stats
            );

            meta.setDisplayName(
                    "§7Simple Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §7UNCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+1.00",
                                    "§7❤ Health: §f+0.00",
                                    "",
                                    "§8Basic Armor"
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2004);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * =========================================================
     * COMBAT SET
     * =========================================================
     * =========================================================
     *
     * Rarity: COMMON
     * Color: Green
     *
     * Defense:
     * Helmet     2
     * Chestplate 6
     * Leggings   4
     * Boots      2
     *
     * Attack Spread exists on every armor piece.
     * =========================================================
     */


    /*
     * =========================================================
     * COMBAT HELMET
     * =========================================================
     */

    public ItemStack createCombatHelmet() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_helmet");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(6);
            stats.setAttackSpread(3);
            stats.setSpread(0.0);
            stats.setHealth(10);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aCombat Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+6.0",
                                    "§7⚔ Attack Spread: §f+3.0",
                                    "§7❤ Health: §f+10.0",
                                    "",
                                    "§c⚔ Combat Armor",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2101);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(180, 30, 30)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT CHESTPLATE
     * =========================================================
     */

    public ItemStack createCombatChestplate() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_chestplate");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(12);
            stats.setAttackSpread(4);
            stats.setSpread(0.0);
            stats.setHealth(16);
            stats.setCritChance(5);
            stats.setCritDamage(35);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aCombat Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+12.0",
                                    "§7⚔ Attack Spread: §f+4.0",
                                    "§7❤ Health: §f+16.0",
                                    "§7✧ Crit Chance: §f+5.0%",
                                    "§7✧ Crit Damage: §f+35.0%",
                                    "",
                                    "§c⚔ Combat Armor",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2102);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(180, 30, 30)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT LEGGINGS
     * =========================================================
     */

    public ItemStack createCombatLeggings() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_leggings");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(8);
            stats.setAttackSpread(3);
            stats.setSpread(0.0);
            stats.setHealth(12);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aCombat Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+8.0",
                                    "§7⚔ Attack Spread: §f+3.0",
                                    "§7❤ Health: §f+12.0",
                                    "",
                                    "§c⚔ Combat Armor",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2103);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(180, 30, 30)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT BOOTS
     * =========================================================
     */

    public ItemStack createCombatBoots() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_boots");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(6);
            stats.setAttackSpread(2);
            stats.setSpread(0.0);
            stats.setHealth(10);
            stats.setSpeed(2.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aCombat Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+6.0",
                                    "§7⚔ Attack Spread: §f+2.0",
                                    "§7❤ Health: §f+10.0",
                                    "§7✦ Speed: §f+2.00%",
                                    "",
                                    "§c⚔ Combat Armor",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2104);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(180, 30, 30)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * COMBAT II HELMET
     * =========================================================
     *
     * Rarity: RARE
     * Material: Iron
     *
     * Keeps the same capabilities as Combat I:
     * - Defense
     * - Health
     * - Attack Spread
     *
     * =========================================================
     */

    public ItemStack createCombatHelmet2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_helmet_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(14);
            stats.setHealth(20);
            stats.setAttackSpread(5);
            stats.setSpread(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Combat Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+14.0",
                                    "§7❤ Health: §f+20.0",
                                    "§7⚔ Attack Spread: §f+5.0",
                                    "",
                                    "§c⚔ Combat Armor II",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2111);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT II CHESTPLATE
     * =========================================================
     */

    public ItemStack createCombatChestplate2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_chestplate_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(28);
            stats.setHealth(32);
            stats.setAttackSpread(7);
            stats.setSpread(0.0);
            stats.setCritChance(7);
            stats.setCritDamage(50);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Combat Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+28.0",
                                    "§7❤ Health: §f+32.0",
                                    "§7⚔ Attack Spread: §f+7.0",
                                    "§7✧ Crit Chance: §f+7.0%",
                                    "§7✧ Crit Damage: §f+50.0%",
                                    "",
                                    "§c⚔ Combat Armor II",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2112);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT II LEGGINGS
     * =========================================================
     */

    public ItemStack createCombatLeggings2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_leggings_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(18);
            stats.setHealth(24);
            stats.setAttackSpread(5);
            stats.setSpread(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Combat Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+18.0",
                                    "§7❤ Health: §f+24.0",
                                    "§7⚔ Attack Spread: §f+5.0",
                                    "",
                                    "§c⚔ Combat Armor II",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2113);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT II BOOTS
     * =========================================================
     */

    public ItemStack createCombatBoots2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_boots_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(12);
            stats.setHealth(18);
            stats.setAttackSpread(4);
            stats.setSpread(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Combat Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+12.0",
                                    "§7❤ Health: §f+18.0",
                                    "§7⚔ Attack Spread: §f+4.0",
                                    "",
                                    "§c⚔ Combat Armor II",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2114);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * COMBAT SWORD
     * =========================================================
     */

    public ItemStack createCombatSword() {

        ItemStack item =
                new ItemStack(
                        Material.STONE_SWORD
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_sword");

            ItemStats stats =
                    new ItemStats();

            stats.setDamage(14.0);
            stats.setAttackSpread(3.0);
            stats.setCritChance(5.0);
            stats.setCritDamage(42.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aCombat Sword"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7⚔ Damage: §f+14.00",
                                    "§7⚔ Attack Spread: §f+3.00",
                                    "§7✧ Crit Chance: §f+5.00%",
                                    "§7✧ Crit Damage: §f+42.00%",
                                    "",
                                    "§c⚔ Combat Weapon",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(1014);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * COMBAT II SWORD
     * =========================================================
     *
     * Rarity: RARE
     * Material: Iron Sword
     *
     * Keeps the Combat progression:
     * - Damage
     * - Attack Spread
     *
     * =========================================================
     */

    public ItemStack createCombatSword2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_SWORD
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_sword_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDamage(22.0);
            stats.setAttackSpread(5.0);
            stats.setCritChance(6.0);
            stats.setCritDamage(40.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Combat Sword"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7⚔ Damage: §f+22.00",
                                    "§7⚔ Attack Spread: §f+5.00",
                                    "§7✧ Crit Chance: §f+6.00%",
                                    "§7✧ Crit Damage: §f+40.00%",
                                    "",
                                    "§c⚔ Combat Weapon II",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2121);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }
    /*
     * =========================================================
     * =========================================================
     * COMBAT III
     * =========================================================
     * =========================================================
     */

    public ItemStack createCombatHelmet3() {

        ItemStack item = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_helmet_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(28);
            stats.setHealth(36);
            stats.setAttackSpread(8);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Combat Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+28.0",
                                    "§7❤ Health: §f+36.0",
                                    "§7⚔ Attack Spread: §f+8.0",
                                    "",
                                    "§c⚔ Combat Armor III",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2131);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatChestplate3() {

        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_chestplate_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(52);
            stats.setHealth(55);
            stats.setAttackSpread(12);
            stats.setSpread(0.0);
            stats.setCritChance(10);
            stats.setCritDamage(70);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Combat Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+52.0",
                                    "§7❤ Health: §f+55.0",
                                    "§7⚔ Attack Spread: §f+12.0",
                                    "§7✧ Crit Chance: §f+10.0%",
                                    "§7✧ Crit Damage: §f+70.0%",
                                    "",
                                    "§c⚔ Combat Armor III",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2132);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatLeggings3() {

        ItemStack item = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_leggings_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(36);
            stats.setHealth(42);
            stats.setAttackSpread(8);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Combat Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+36.0",
                                    "§7❤ Health: §f+42.0",
                                    "§7⚔ Attack Spread: §f+8.0",
                                    "",
                                    "§c⚔ Combat Armor III",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2133);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatBoots3() {

        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_boots_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(24);
            stats.setHealth(32);
            stats.setAttackSpread(6);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Combat Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+24.0",
                                    "§7❤ Health: §f+32.0",
                                    "§7⚔ Attack Spread: §f+6.0",
                                    "",
                                    "§c⚔ Combat Armor III",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2134);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatSword3() {

        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_sword_3");

            ItemStats stats = new ItemStats();

            stats.setDamage(40.0);
            stats.setAttackSpread(9.0);
            stats.setCritChance(9.0);
            stats.setCritDamage(58.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Combat Sword");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7⚔ Damage: §f+40.00",
                                    "§7⚔ Attack Spread: §f+9.00",
                                    "§7✧ Crit Chance: §f+9.00%",
                                    "§7✧ Crit Damage: §f+58.00%",
                                    "",
                                    "§c⚔ Combat Weapon III",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2141);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT IV
     * =========================================================
     */

    public ItemStack createCombatHelmet4() {

        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_helmet_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(50);
            stats.setHealth(60);
            stats.setAttackSpread(12);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Combat Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+50.0",
                                    "§7❤ Health: §f+60.0",
                                    "§7⚔ Attack Spread: §f+12.0",
                                    "",
                                    "§c⚔ Combat Armor IV",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2151);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatChestplate4() {

        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_chestplate_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(95);
            stats.setHealth(95);
            stats.setAttackSpread(18);
            stats.setSpread(0.0);
            stats.setCritChance(13);
            stats.setCritDamage(95);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Combat Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+95.0",
                                    "§7❤ Health: §f+95.0",
                                    "§7⚔ Attack Spread: §f+18.0",
                                    "§7✧ Crit Chance: §f+13.0%",
                                    "§7✧ Crit Damage: §f+95.0%",
                                    "",
                                    "§c⚔ Combat Armor IV",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2152);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatLeggings4() {

        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_leggings_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(65);
            stats.setHealth(70);
            stats.setAttackSpread(12);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Combat Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+65.0",
                                    "§7❤ Health: §f+70.0",
                                    "§7⚔ Attack Spread: §f+12.0",
                                    "",
                                    "§c⚔ Combat Armor IV",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2153);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatBoots4() {

        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_boots_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(45);
            stats.setHealth(55);
            stats.setAttackSpread(9);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Combat Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+45.0",
                                    "§7❤ Health: §f+55.0",
                                    "§7⚔ Attack Spread: §f+9.0",
                                    "",
                                    "§c⚔ Combat Armor IV",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2154);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatSword4() {

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_sword_4");

            ItemStats stats = new ItemStats();

            stats.setDamage(72.0);
            stats.setAttackSpread(14.0);
            stats.setCritChance(13.0);
            stats.setCritDamage(82.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Combat Sword");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7⚔ Damage: §f+72.00",
                                    "§7⚔ Attack Spread: §f+14.00",
                                    "§7✧ Crit Chance: §f+13.00%",
                                    "§7✧ Crit Damage: §f+82.00%",
                                    "",
                                    "§c⚔ Combat Weapon IV",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2161);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * COMBAT V
     * =========================================================
     */

    public ItemStack createCombatHelmet5() {

        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_helmet_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(90);
            stats.setHealth(100);
            stats.setAttackSpread(18);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Combat Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+90.0",
                                    "§7❤ Health: §f+100.0",
                                    "§7⚔ Attack Spread: §f+18.0",
                                    "",
                                    "§c⚔ Combat Armor V",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2171);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatChestplate5() {

        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_chestplate_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(170);
            stats.setHealth(160);
            stats.setAttackSpread(26);
            stats.setSpread(0.0);
            stats.setCritChance(16);
            stats.setCritDamage(120);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Combat Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+170.0",
                                    "§7❤ Health: §f+160.0",
                                    "§7⚔ Attack Spread: §f+26.0",
                                    "§7✧ Crit Chance: §f+16.0%",
                                    "§7✧ Crit Damage: §f+120.0%",
                                    "",
                                    "§c⚔ Combat Armor V",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2172);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatLeggings5() {

        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_leggings_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(115);
            stats.setHealth(120);
            stats.setAttackSpread(18);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Combat Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+115.0",
                                    "§7❤ Health: §f+120.0",
                                    "§7⚔ Attack Spread: §f+18.0",
                                    "",
                                    "§c⚔ Combat Armor V",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2173);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatBoots5() {

        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_boots_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(80);
            stats.setHealth(95);
            stats.setAttackSpread(14);
            stats.setSpread(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Combat Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+80.0",
                                    "§7❤ Health: §f+95.0",
                                    "§7⚔ Attack Spread: §f+14.0",
                                    "",
                                    "§c⚔ Combat Armor V",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2174);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createCombatSword5() {

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "combat_sword_5");

            ItemStats stats = new ItemStats();

            stats.setDamage(130.0);
            stats.setAttackSpread(22.0);
            stats.setCritChance(18.0);
            stats.setCritDamage(110.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Combat Sword");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7⚔ Damage: §f+130.00",
                                    "§7⚔ Attack Spread: §f+22.00",
                                    "§7✧ Crit Chance: §f+18.00%",
                                    "§7✧ Crit Damage: §f+110.00%",
                                    "",
                                    "§c⚔ Combat Weapon V",
                                    "",
                                    "§8Aetherion Combat Set"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2181);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * =========================================================
     * MINING III
     * =========================================================
     * =========================================================
     */

    public ItemStack createMiningHelmet3() {

        ItemStack item = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_helmet_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(14);
            stats.setMiningPower(42);
            stats.setFortune(85);
            stats.setSpread(8);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Mining Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+14",
                                    "§7⛏ Mining Power: §f+42",
                                    "§7💎 Fortune: §f+85",
                                    "§7✦ Spread: §f+10.00",
                                    "",
                                    "§9⛏ Mining Armor III",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2231);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningChestplate3() {

        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_chestplate_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(40);
            stats.setMiningPower(60);
            stats.setFortune(120);
            stats.setSpread(12);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Mining Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+40",
                                    "§7⛏ Mining Power: §f+60",
                                    "§7💎 Fortune: §f+120",
                                    "§7✦ Spread: §f+10.00",
                                    "",
                                    "§9⛏ Mining Armor III",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2232);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningLeggings3() {

        ItemStack item = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_leggings_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(28);
            stats.setMiningPower(50);
            stats.setFortune(100);
            stats.setSpread(10);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Mining Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+28",
                                    "§7⛏ Mining Power: §f+50",
                                    "§7💎 Fortune: §f+100",
                                    "§7✦ Spread: §f+10.00",
                                    "",
                                    "§9⛏ Mining Armor III",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2233);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningBoots3() {

        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_boots_3");

            ItemStats stats = new ItemStats();

            stats.setDefense(14);
            stats.setMiningPower(42);
            stats.setFortune(85);
            stats.setSpread(8);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Mining Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7🛡 Defense: §f+14",
                                    "§7⛏ Mining Power: §f+42",
                                    "§7💎 Fortune: §f+85",
                                    "§7✦ Spread: §f+10.00",
                                    "",
                                    "§9⛏ Mining Armor III",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2234);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningPickaxe3() {

        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_pickaxe_3");

            ItemStats stats = new ItemStats();

            stats.setMiningPower(48);
            stats.setFortune(80);
            stats.setSpread(7);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.EPIC, stats);

            meta.setDisplayName("§5Diamond Mining Pickaxe");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7⛏ Mining Power: §f+48",
                                    "§7💎 Fortune: §f+80",
                                    "§7✦ Spread: §f+7.00",
                                    "",
                                    "§9⛏ Mining Tool III",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2241);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING IV
     * =========================================================
     */

    public ItemStack createMiningHelmet4() {

        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_helmet_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(22);
            stats.setMiningPower(80);
            stats.setFortune(160);
            stats.setSpread(14);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Mining Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+22",
                                    "§7⛏ Mining Power: §f+80",
                                    "§7💎 Fortune: §f+160",
                                    "§7✦ Spread: §f+20.00",
                                    "",
                                    "§9⛏ Mining Armor IV",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2251);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningChestplate4() {

        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_chestplate_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(65);
            stats.setMiningPower(110);
            stats.setFortune(220);
            stats.setSpread(20);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Mining Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+65",
                                    "§7⛏ Mining Power: §f+110",
                                    "§7💎 Fortune: §f+220",
                                    "§7✦ Spread: §f+20.00",
                                    "",
                                    "§9⛏ Mining Armor IV",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2252);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningLeggings4() {

        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_leggings_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(45);
            stats.setMiningPower(90);
            stats.setFortune(180);
            stats.setSpread(16);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Mining Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+45",
                                    "§7⛏ Mining Power: §f+90",
                                    "§7💎 Fortune: §f+180",
                                    "§7✦ Spread: §f+20.00",
                                    "",
                                    "§9⛏ Mining Armor IV",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2253);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningBoots4() {

        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_boots_4");

            ItemStats stats = new ItemStats();

            stats.setDefense(22);
            stats.setMiningPower(80);
            stats.setFortune(160);
            stats.setSpread(14);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Mining Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+22",
                                    "§7⛏ Mining Power: §f+80",
                                    "§7💎 Fortune: §f+160",
                                    "§7✦ Spread: §f+20.00",
                                    "",
                                    "§9⛏ Mining Armor IV",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2254);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningPickaxe4() {

        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_pickaxe_4");

            ItemStats stats = new ItemStats();

            stats.setMiningPower(88);
            stats.setFortune(145);
            stats.setSpread(13);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.LEGENDARY, stats);

            meta.setDisplayName("§6Netherite Mining Pickaxe");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7⛏ Mining Power: §f+88",
                                    "§7💎 Fortune: §f+145",
                                    "§7✦ Spread: §f+13.00",
                                    "",
                                    "§9⛏ Mining Tool IV",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2261);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING V
     * =========================================================
     */

    public ItemStack createMiningHelmet5() {

        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_helmet_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(35);
            stats.setMiningPower(150);
            stats.setFortune(300);
            stats.setSpread(24);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Mining Helmet");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+35",
                                    "§7⛏ Mining Power: §f+150",
                                    "§7💎 Fortune: §f+300",
                                    "§7✦ Spread: §f+40.00",
                                    "",
                                    "§9⛏ Mining Armor V",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2271);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningChestplate5() {

        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_chestplate_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(100);
            stats.setMiningPower(200);
            stats.setFortune(420);
            stats.setSpread(34);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Mining Chestplate");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+100",
                                    "§7⛏ Mining Power: §f+200",
                                    "§7💎 Fortune: §f+420",
                                    "§7✦ Spread: §f+40.00",
                                    "",
                                    "§9⛏ Mining Armor V",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2272);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningLeggings5() {

        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_leggings_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(70);
            stats.setMiningPower(170);
            stats.setFortune(350);
            stats.setSpread(28);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Mining Leggings");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+70",
                                    "§7⛏ Mining Power: §f+170",
                                    "§7💎 Fortune: §f+350",
                                    "§7✦ Spread: §f+40.00",
                                    "",
                                    "§9⛏ Mining Armor V",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2273);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningBoots5() {

        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_boots_5");

            ItemStats stats = new ItemStats();

            stats.setDefense(35);
            stats.setMiningPower(150);
            stats.setFortune(300);
            stats.setSpread(24);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Mining Boots");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+35",
                                    "§7⛏ Mining Power: §f+150",
                                    "§7💎 Fortune: §f+300",
                                    "§7✦ Spread: §f+40.00",
                                    "",
                                    "§9⛏ Mining Armor V",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2274);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createMiningPickaxe5() {

        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_pickaxe_5");

            ItemStats stats = new ItemStats();

            stats.setMiningPower(160);
            stats.setFortune(260);
            stats.setSpread(22);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(meta, Rarity.MYTHIC, stats);

            meta.setDisplayName("§dMythic Mining Pickaxe");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7⛏ Mining Power: §f+160",
                                    "§7💎 Fortune: §f+260",
                                    "§7✦ Spread: §f+22.00",
                                    "",
                                    "§9⛏ Mining Tool V",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2281);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * =========================================================
     * MINING SET
     * =========================================================
     * =========================================================
     *
     * Rarity: COMMON
     * Color: Green
     *
     * Every armor piece has:
     * Mining Power
     * Fortune
     * Spread
     *
     * =========================================================
     */


    /*
     * =========================================================
     * MINING HELMET
     * =========================================================
     */

    public ItemStack createMiningHelmet() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_helmet");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(4);
            stats.setMiningPower(10);
            stats.setFortune(18);
            stats.setSpread(2);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aMining Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+4",
                                    "§7⛏ Mining Power: §f+10",
                                    "§7💎 Fortune: §f+18",
                                    "§7✦ Spread: §f+1.00",
                                    "",
                                    "§9⛏ Mining Armor",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2201);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(35, 80, 190)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING CHESTPLATE
     * =========================================================
     */

    public ItemStack createMiningChestplate() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_chestplate");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(8);
            stats.setMiningPower(14);
            stats.setFortune(24);
            stats.setSpread(3);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aMining Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+8",
                                    "§7⛏ Mining Power: §f+14",
                                    "§7💎 Fortune: §f+24",
                                    "§7✦ Spread: §f+2.00",
                                    "",
                                    "§9⛏ Mining Armor",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2202);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(35, 80, 190)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING LEGGINGS
     * =========================================================
     */

    public ItemStack createMiningLeggings() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_leggings");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(6);
            stats.setMiningPower(12);
            stats.setFortune(20);
            stats.setSpread(2);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aMining Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+6",
                                    "§7⛏ Mining Power: §f+12",
                                    "§7💎 Fortune: §f+20",
                                    "§7✦ Spread: §f+1.00",
                                    "",
                                    "§9⛏ Mining Armor",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2203);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(35, 80, 190)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING BOOTS
     * =========================================================
     */

    public ItemStack createMiningBoots() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_boots");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(4);
            stats.setMiningPower(10);
            stats.setFortune(16);
            stats.setSpread(2);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);
            stats.setSpeed(2.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aMining Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7🛡 Defense: §f+4",
                                    "§7⛏ Mining Power: §f+10",
                                    "§7💎 Fortune: §f+16",
                                    "§7✦ Spread: §f+1.00",
                                    "§7✦ Speed: §f+2.00%",
                                    "",
                                    "§9⛏ Mining Armor",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2204);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Color.fromRGB(35, 80, 190)
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING II HELMET
     * =========================================================
     */

    public ItemStack createMiningHelmet2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_helmet_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(8);
            stats.setMiningPower(22);
            stats.setFortune(45);
            stats.setSpread(4);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Mining Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+8",
                                    "§7⛏ Mining Power: §f+22",
                                    "§7💎 Fortune: §f+45",
                                    "§7✦ Spread: §f+5.00",
                                    "",
                                    "§9⛏ Mining Armor II",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2211);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING II CHESTPLATE
     * =========================================================
     */

    public ItemStack createMiningChestplate2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_chestplate_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(24);
            stats.setMiningPower(35);
            stats.setFortune(65);
            stats.setSpread(6);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Mining Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+24",
                                    "§7⛏ Mining Power: §f+35",
                                    "§7💎 Fortune: §f+65",
                                    "§7✦ Spread: §f+5.00",
                                    "",
                                    "§9⛏ Mining Armor II",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2212);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING II LEGGINGS
     * =========================================================
     */

    public ItemStack createMiningLeggings2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_leggings_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(16);
            stats.setMiningPower(28);
            stats.setFortune(55);
            stats.setSpread(5);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Mining Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+16",
                                    "§7⛏ Mining Power: §f+28",
                                    "§7💎 Fortune: §f+55",
                                    "§7✦ Spread: §f+5.00",
                                    "",
                                    "§9⛏ Mining Armor II",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2213);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING II BOOTS
     * =========================================================
     */

    public ItemStack createMiningBoots2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_boots_2");

            ItemStats stats =
                    new ItemStats();

            stats.setDefense(8);
            stats.setMiningPower(22);
            stats.setFortune(45);
            stats.setSpread(4);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Mining Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+8",
                                    "§7⛏ Mining Power: §f+22",
                                    "§7💎 Fortune: §f+45",
                                    "§7✦ Spread: §f+5.00",
                                    "",
                                    "§9⛏ Mining Armor II",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2214);
            meta.setUnbreakable(true);

            applyArmorColor(
                    meta,
                    Rarity.RARE.getArmorColor()
            );

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * MINING PICKAXE
     * =========================================================
     */

    public ItemStack createBlueprintVeinSiphon() {
        return createSkillBlueprint(
                "blueprint_vein_siphon",
                "§bBlueprint: Vein Siphon",
                "§7Schematic for a suction mining tool.",
                3101
        );
    }

    public ItemStack createVeinSiphon() {
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "vein_siphon");
            ItemStats stats = new ItemStats();
            stats.setMiningPower(48);
            stats.setFortune(96);
            stats.setSpread(5);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);
            applyItemData(meta, Rarity.RARE, stats);
            meta.getPersistentDataContainer().set(ItemKeys.siphonParts(), PersistentDataType.INTEGER, 0);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            meta.setDisplayName("§bVein Siphon");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7⛏ Mining Power: §f+48",
                    "§7💎 Fortune: §f+96",
                    "§7✦ Spread: §f+5.00",
                    "",
                    "§eAbility · Ore Vacuum",
                    "§7Right-click: pull ores in §f10§7 blocks",
                    "§7Cooldown: §f45s §8· max 15",
                    "",
                    "§6Mining Boss Damage: §f10 + rarity",
                    "§7Pick hits on ore trolls.",
                    "",
                    "§8Aetherion Mining Tool"
            ), true, false, false));
            meta.setCustomModelData(3102);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBlueprintCanopyCleaver() {
        return createSkillBlueprint(
                "blueprint_canopy_cleaver",
                "§aBlueprint: Canopy Cleaver",
                "§7Schematic for a one-swing tree feller.",
                3111
        );
    }

    public ItemStack createCanopyCleaver() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "canopy_cleaver");
            ItemStats stats = new ItemStats();
            stats.setFortune(72);
            stats.setSpread(6);
            applyItemData(meta, Rarity.RARE, stats);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            meta.setDisplayName("§aCanopy Cleaver");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7💎 Fortune: §f+72",
                    "§7✦ Spread: §f+6.00",
                    "",
                    "§eAbility · Perfect Fell",
                    "§7Click the trunk base once —",
                    "§7instant perfect chop. Tree falls.",
                    "§7Cooldown: §f20s",
                    "",
                    "§8Aetherion Foraging Tool"
            ), true, false, false));
            meta.setCustomModelData(3112);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBlueprintBountyHoe() {
        return createSkillBlueprint(
                "blueprint_bounty_hoe",
                "§eBlueprint: Bounty Hoe",
                "§7Schematic for a high-yield harvest hoe.",
                3121
        );
    }

    public ItemStack createBountyHoe() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "bounty_hoe");
            ItemStats stats = new ItemStats();
            // Specialty = harvest spread, not god fortune (T5 hoe is 200/300).
            stats.setFortune(80);
            stats.setHarvestSpread(100);
            applyItemData(meta, Rarity.RARE, stats);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            meta.setDisplayName("§eBounty Hoe");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7💎 Fortune: §f+80",
                    "§7✦ Harvest Spread: §f+100",
                    "",
                    "§eAbility · Packed Harvest",
                    "§7Cuts more neighbouring mature crops.",
                    "",
                    "§8Aetherion Farming Tool"
            ), true, false, false));
            meta.setCustomModelData(3122);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBlueprintWildSight() {
        return createSkillBlueprint(
                "blueprint_wild_sight",
                "§dBlueprint: Wild Sight",
                "§7Schematic for a pet-sense lens.",
                3131
        );
    }

    public ItemStack createWildSight() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "wild_sight");
            ItemStats stats = new ItemStats();
            stats.setCatchRate(8);
            applyItemData(meta, Rarity.RARE, stats);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            meta.setDisplayName("§dWild Sight");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7☘ Catch Rate: §f+8.00%",
                    "",
                    "§eAbility · Pet Sense",
                    "§7Hold to see nearby wild pets",
                    "§7through walls — rarity glow + name.",
                    "",
                    "§8Aetherion Catcher Tool"
            ), true, false, false));
            meta.setCustomModelData(3132);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBlueprintTideLatch() {
        return createSkillBlueprint(
                "blueprint_tide_latch",
                "§3Blueprint: Tide Latch",
                "§7Schematic for a rod that hooks water pets.",
                3141
        );
    }

    public ItemStack createTideLatch() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "tide_latch");
            ItemStats stats = new ItemStats();
            stats.setFortune(72);
            stats.setFishingSpeed(14);
            stats.setFishingCatch(120);
            applyItemData(meta, Rarity.RARE, stats);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            meta.setDisplayName("§3Tide Latch");
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7💎 Fortune: §f+72",
                    "§7🌊 Fishing Speed: §f+14",
                    "§7🎣 Fishing Catch: §f+120",
                    "",
                    "§eAbility · Aquatic Hook",
                    "§7Chance to reel aquatic pets,",
                    "§7including deep-sea water pets.",
                    "",
                    "§8Aetherion Fishing Tool"
            ), true, false, false));
            meta.setCustomModelData(3142);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBlueprintResonanceScythe() {
        return createSkillBlueprint(
                "blueprint_resonance_scythe",
                "§5Blueprint: Resonance Scythe",
                "§7Schematic for the sonic combat scythe.",
                3151
        );
    }

    private ItemStack createSkillBlueprint(String id, String name, String line, int cmd) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, id);
            applyItemData(meta, Rarity.RARE, new ItemStats());
            meta.setDisplayName(name);
            meta.setLore(createLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    line,
                    "§eStamp at the Surveyor desk.",
                    "",
                    "§8Dropped by Tiny Ore Trolls"
            ), false, true, false));
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createMiningPickaxe() {

        ItemStack item =
                new ItemStack(
                        Material.STONE_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_pickaxe");

            ItemStats stats =
                    new ItemStats();

            stats.setMiningPower(14);
            stats.setFortune(24);
            stats.setSpread(2);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.COMMON,
                    stats
            );

            meta.setDisplayName(
                    "§aMining Pickaxe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §aCOMMON",
                                    "",
                                    "§7⛏ Mining Power: §f+14",
                                    "§7💎 Fortune: §f+24",
                                    "§7✦ Spread: §f+2.00",
                                    "",
                                    "§9⛏ Mining Tool",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1015);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * MINING II PICKAXE
     * =========================================================
     *
     * Rarity: RARE
     * Material: Iron Pickaxe
     *
     * Keeps the Mining progression:
     * - Mining Power
     * - Fortune
     *
     * =========================================================
     */

    public ItemStack createMiningPickaxe2() {

        ItemStack item =
                new ItemStack(
                        Material.IRON_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "mining_pickaxe_2");

            ItemStats stats =
                    new ItemStats();

            stats.setMiningPower(26);
            stats.setFortune(44);
            stats.setSpread(4);
            stats.setAttackSpread(0.0);
            stats.setHealth(0.0);

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bIron Mining Pickaxe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7⛏ Mining Power: §f+26",
                                    "§7💎 Fortune: §f+44",
                                    "§7✦ Spread: §f+4.00",
                                    "",
                                    "§9⛏ Mining Tool II",
                                    "",
                                    "§8Aetherion Mining Set"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(2221);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * GOD ITEM HELPER
     * =========================================================
     */

    private ItemStats createGodStats() {

        ItemStats stats =
                new ItemStats();

        stats.setMiningPower(250.0);
        stats.setFortune(250.0);
        stats.setDamage(250.0);
        stats.setDefense(250.0);

        stats.setSpread(250.0);
        stats.setAttackSpread(250.0);
        stats.setHealth(250.0);
        stats.setSpeed(250.0);
        stats.setCatchRate(250.0);
        stats.setCritChance(250.0);
        stats.setCritDamage(250.0);

        stats.setCoalBoosters(0);
        stats.setIronBoosters(0);
        stats.setGoldBoosters(0);
        stats.setDiamondBoosters(0);

        stats.setEmeraldBoosters(0);
        stats.setRedstoneBoosters(0);
        stats.setLapisBoosters(0);

        return stats;
    }


    /*
     * =========================================================
     * GOD ITEM LORE
     * =========================================================
     */

    private void applyGodLore(
            ItemMeta meta,
            String type
    ) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7It shouldn't exist.");
        baseLore.add("");
        baseLore.add("§7⛏ Mining Power: §f+250.00");
        baseLore.add("§7💎 Fortune: §f+250.00");
        baseLore.add("§7⚔ Damage: §f+250.00");
        baseLore.add("§7🛡 Defense: §f+250.00");
        baseLore.add("§7✦ Spread: §f+250.00");
        baseLore.add("§7⚔ Attack Spread: §f+250.00");
        baseLore.add("§7❤ Health: §f+250.00");
        baseLore.add("§7✧ Crit Chance: §f+250.00%");
        baseLore.add("§7✧ Crit Damage: §f+250.00%");
        baseLore.add("§7✦ Speed: §f+250.00%");
        baseLore.add("§7☘ Catch Rate: §f+250.00%");
        baseLore.add("");
        baseLore.add("§8Type: §f" + type);

        if (lastItemId != null && !lastItemId.isBlank()) {
            meta.setLore(createLore(lastItemId, baseLore));
            return;
        }

        meta.setLore(baseLore);
    }


    /*
     * =========================================================
     * GOD PICKAXE
     * =========================================================
     */

    public ItemStack createGodPickaxe() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_pickaxe");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Pickaxe"
            );

            applyGodLore(
                    meta,
                    "Pickaxe"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD AXE
     * =========================================================
     */

    public ItemStack createGodAxe() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_AXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_axe");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Axe"
            );

            applyGodLore(
                    meta,
                    "Axe"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD SWORD
     * =========================================================
     */

    public ItemStack createGodSword() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_SWORD
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_sword");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Sword"
            );

            applyGodLore(
                    meta,
                    "Sword"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD HELMET
     * =========================================================
     */

    public ItemStack createGodHelmet() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_helmet");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Helmet"
            );

            applyGodLore(
                    meta,
                    "Helmet"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD CHESTPLATE
     * =========================================================
     */

    public ItemStack createGodChestplate() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_chestplate");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Chestplate"
            );

            applyGodLore(
                    meta,
                    "Chestplate"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD LEGGINGS
     * =========================================================
     */

    public ItemStack createGodLeggings() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_leggings");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Leggings"
            );

            applyGodLore(
                    meta,
                    "Leggings"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD BOOTS
     * =========================================================
     */

    public ItemStack createGodBoots() {

        ItemStack item =
                new ItemStack(
                        Material.NETHERITE_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god_boots");

            ItemStats stats =
                    createGodStats();

            applyItemData(
                    meta,
                    Rarity.MYTHIC,
                    stats
            );

            meta.setDisplayName(
                    "§dGOD Boots"
            );

            applyGodLore(
                    meta,
                    "Boots"
            );

            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - ZERO STAT HELPER
     * =========================================================
     */

    private ItemStats createGod2Stats() {

        ItemStats stats =
                new ItemStats();

        stats.setMiningPower(0.0);
        stats.setFortune(0.0);
        stats.setDamage(0.0);
        stats.setDefense(0.0);

        stats.setHealth(0.0);
        stats.setSpread(0.0);
        stats.setAttackSpread(0.0);

        stats.setCoalBoosters(0);
        stats.setIronBoosters(0);
        stats.setGoldBoosters(0);
        stats.setDiamondBoosters(0);

        stats.setEmeraldBoosters(0);
        stats.setRedstoneBoosters(0);
        stats.setLapisBoosters(0);

        return stats;
    }


    /*
     * =========================================================
     * GOD KIT 2 - PICKAXE
     * =========================================================
     */

    public ItemStack createGod2Pickaxe() {

        ItemStack item =
                new ItemStack(
                        Material.STONE_PICKAXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_pickaxe");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Pickaxe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7⛏ Mining Power: §f+0.00",
                                    "§7💎 Fortune: §f+0.00",
                                    "§7✦ Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Item"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1011);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - AXE
     * =========================================================
     */

    public ItemStack createGod2Axe() {

        ItemStack item =
                new ItemStack(
                        Material.STONE_AXE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_axe");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Axe"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7⛏ Mining Power: §f+0.00",
                                    "§7💎 Fortune: §f+0.00",
                                    "§7✦ Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Item"
                            ),
                            true,
                            false,
                            false
                    )
            );

            meta.setCustomModelData(1012);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - SWORD
     * =========================================================
     */

    public ItemStack createGod2Sword() {

        ItemStack item =
                new ItemStack(
                        Material.STONE_SWORD
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_sword");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Sword"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7⚔ Damage: §f+0.00",
                                    "§7⚔ Attack Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Weapon"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(1013);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - HELMET
     * =========================================================
     */

    public ItemStack createGod2Helmet() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_HELMET
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_helmet");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Helmet"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+0.00",
                                    "§7❤ Health: §f+0.00",
                                    "§7✦ Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Armor"
                            ),
                            true,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2011);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - CHESTPLATE
     * =========================================================
     */

    public ItemStack createGod2Chestplate() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_CHESTPLATE
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_chestplate");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Chestplate"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+0.00",
                                    "§7❤ Health: §f+0.00",
                                    "§7⚔ Attack Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Armor"
                            ),
                            false,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2012);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - LEGGINGS
     * =========================================================
     */

    public ItemStack createGod2Leggings() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_LEGGINGS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_leggings");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Leggings"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+0.00",
                                    "§7❤ Health: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Armor"
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2013);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * GOD KIT 2 - BOOTS
     * =========================================================
     */

    public ItemStack createGod2Boots() {

        ItemStack item =
                new ItemStack(
                        Material.CHAINMAIL_BOOTS
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "god2_boots");

            ItemStats stats =
                    createGod2Stats();

            applyItemData(
                    meta,
                    Rarity.RARE,
                    stats
            );

            meta.setDisplayName(
                    "§bGOD Kit 2 Boots"
            );

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §bRARE",
                                    "",
                                    "§7🛡 Defense: §f+0.00",
                                    "§7❤ Health: §f+0.00",
                                    "§7✦ Spread: §f+0.00",
                                    "§7⚔ Attack Spread: §f+0.00",
                                    "",
                                    "§8GOD Kit 2 Test Armor"
                            ),
                            true,
                            true,
                            true
                    )
            );

            meta.setCustomModelData(2014);
            meta.setUnbreakable(true);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * BOOSTERS (factories live in BoosterItems)
     * =========================================================
     */

    public ItemStack createCoalBooster() {
        return boosters.coal();
    }

    public ItemStack createIronBooster() {
        return boosters.iron();
    }

    public ItemStack createGoldBooster() {
        return boosters.gold();
    }

    public ItemStack createDiamondBooster() {
        return boosters.diamond();
    }

    public ItemStack createEmeraldBooster() {
        return boosters.emerald();
    }

    public ItemStack createRedstoneBooster() {
        return boosters.redstone();
    }

    public ItemStack createLapisBooster() {
        return boosters.lapis();
    }

    public ItemStack createGlowstoneBooster() {
        return boosters.glowstone();
    }

    public ItemStack createWheatBooster() {
        return boosters.wheat();
    }

    public ItemStack createCarrotBooster() {
        return boosters.carrot();
    }

    public ItemStack createOakBooster() {
        return boosters.oak();
    }

    public ItemStack createBirchBooster() {
        return boosters.birch();
    }

    public ItemStack createRandomBooster() {
        return boosters.random();
    }

    public List<ItemStack> boosterShowcase() {
        return boosters.showcase();
    }

    public ItemStack booster(BoosterType type) {
        return boosters.of(type);
    }

    /*
     * =========================================================
     * AETHERBLADE
     * =========================================================
     *
     * Mythic drop from McNugget.
     * Right-click blinks between nearby enemies.
     * =========================================================
     */

    public ItemStack createAetherblade() {

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "aetherblade");

            ItemStats stats = BossGearBalance.base("aetherblade");

            applyItemData(meta, Rarity.MYTHIC, stats);
            BossGearBalance.stamp(meta);

            meta.setDisplayName("§dAetherblade");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §dMYTHIC",
                                    "",
                                    "§7⚔ Damage: §f+155.00",
                                    "§7⚔ Attack Spread: §f+18.00",
                                    "§7✧ Crit Chance: §f+20.00%",
                                    "§7✧ Crit Damage: §f+140.00%",
                                    "",
                                    "§5✦ Rift Dance",
                                    "§7Right-click: blink to the 5",
                                    "§7nearest enemies. §8(10s)",
                                    DungeonCore.BOSS_CORE_HINT
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2202);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * BRIDGED AXE
     * =========================================================
     *
     * Legendary drop from the Bridge Troll.
     * Right-click throws the axe every 15s. Melee and throws
     * steal a portion of damage dealt as health.
     * =========================================================
     */

    public ItemStack createBridgedAxe() {

        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "bridged_axe");

            ItemStats stats = BossGearBalance.base("bridged_axe");

            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);

            meta.getPersistentDataContainer().set(
                    ItemKeys.lifesteal(),
                    PersistentDataType.DOUBLE,
                    BossGearBalance.BRIDGED_LIFESTEAL
            );

            meta.setDisplayName("§6Bridged Axe");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7⚔ Damage: §f+90.00",
                                    "§7⚔ Attack Spread: §f+14.00",
                                    "§7✧ Crit Chance: §f+13.00%",
                                    "§7✧ Crit Damage: §f+90.00%",
                                    "",
                                    "§6❤ Life Absorption: §f5%",
                                    "§6⚡ Throw: §f15s",
                                    "§7Right-click to hurl the axe.",
                                    "§7Hits steal health on melee and throw.",
                                    DungeonCore.BOSS_CORE_HINT,
                                    "",
                                    "§8Torn from the Bridge Troll"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2203);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * SQUID'S BOOT
     * =========================================================
     *
     * Legendary drop from Squidward.
     * Pulses ink damage to nearby hostiles once per second.
     * =========================================================
     */

    public ItemStack createSquidsBoot() {

        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "squids_boot");

            ItemStats stats = BossGearBalance.base("squids_boot");

            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);
            applyArmorColor(meta, Color.fromRGB(18, 22, 48));

            meta.setDisplayName("§6Squid's Boot");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7🛡 Defense: §f+20.00",
                                    "§7❤ Health: §f+24.00",
                                    "§7✦ Speed: §f+6.00%",
                                    "",
                                    "§8Atleast one of them...",
                                    "",
                                    "§5✦ Ink Pulse",
                                    "§7Nearby hostiles take §f1% §7HP",
                                    "§7each second. §8(max " + de.aetherion.items.core.BoosterLimits.MAX_TOTAL + ")",
                                    DungeonCore.BOSS_CORE_HINT
                            ),
                            false,
                            false,
                            true
                    )
            );

            meta.setCustomModelData(2204);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * WARPED BLADE
     * =========================================================
     *
     * Epic drop from the Hollow Lurker.
     * Right-click blinks 5 blocks forward. Upgrade path later.
     * =========================================================
     */

    public ItemStack createWarpedBlade() {

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "warped_blade");

            ItemStats stats = BossGearBalance.base("warped_blade");

            applyItemData(meta, Rarity.EPIC, stats);
            BossGearBalance.stamp(meta);

            meta.setDisplayName("§5Warped Blade");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5EPIC",
                                    "",
                                    "§7⚔ Damage: §f+52.00",
                                    "§7⚔ Attack Spread: §f+8.00",
                                    "§7✧ Crit Chance: §f+9.00%",
                                    "§7✧ Crit Damage: §f+60.00%",
                                    "",
                                    "§5What the..?",
                                    "",
                                    "§5✦ Warp Step",
                                    "§7Right-click to blink 5 blocks",
                                    "§7forward. §8(8s)",
                                    DungeonCore.BOSS_CORE_HINT,
                                    "",
                                    "§8Torn from the Hollow Lurker"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2401);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    public ItemStack createStaffOfTechnicalDifficulties() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "staff_of_technical_difficulties");
            ItemStats stats = BossGearBalance.base("staff_of_technical_difficulties");
            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);
            meta.setDisplayName("§6Staff of Technical Difficulties");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⚔ Damage: §f+105.00",
                    "§7⚔ Attack Spread: §f+10.00",
                    "§7✧ Crit Chance: §f+12.00%",
                    "§7✧ Crit Damage: §f+72.00%",
                    "",
                    "§5✦ Freeze Frame",
                    "§7Right-click: bank incoming hits for §f4s§7.",
                    "§7Then dump the pile into the nearest enemy.",
                    "§8Sir Balthazar still denies the bug.",
                    DungeonCore.BOSS_CORE_HINT
            ), false, true, false));
            meta.setCustomModelData(2501);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createVoidVacuumCharm() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "void_vacuum_charm");
            ItemStats stats = new ItemStats();
            stats.setSpeed(8.0);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            meta.setDisplayName("§6Void Vacuum Charm");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7✦ Speed: §f+8.00%",
                    "",
                    "§eOff-hand accessory",
                    "§5✦ Auto-Pickup",
                    "§7Pulls nearby drops. The lobby stays tidy.",
                    "§8The Lobby Cleaner wants it back.",
                    DungeonCore.BOSS_CORE_HINT
            ), false, false, false));
            meta.setCustomModelData(2502);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createThermalCore() {
        ItemStack item = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "thermal_core");
            ItemStats stats = new ItemStats();
            stats.setDamage(8.0);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            meta.setDisplayName("§6Thermal Core");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⚔ Damage: §f+8.00",
                    "",
                    "§eOff-hand accessory",
                    "§5✦ Overclock",
                    "§7Faster melee swings. Shortbows fire §f0.2s §7sooner.",
                    "§cEach hit nicks you. Sparky calls it tuition.",
                    "§8Guild quarry leftover.",
                    DungeonCore.BOSS_CORE_HINT
            ), false, false, false));
            meta.setCustomModelData(2503);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createPickaxeCoreOfTheBurrower() {
        ItemStack item = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "pickaxe_core_of_the_burrower");
            ItemStats stats = BossGearBalance.base("pickaxe_core_of_the_burrower");
            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);
            meta.setDisplayName("§6Pickaxe Core of the Burrower");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⛏ Fortune: §f+62.00",
                    "§7⛏ Mining Power: §f+28.00",
                    "§7🛡 Defense: §f+14.00",
                    "",
                    "§eOff-hand accessory",
                    "§5✦ Quarry Hunger",
                    "§7Mines harder. Hits back a little in combat.",
                    "§8Baron von Wurm still wants the picks.",
                    DungeonCore.BOSS_CORE_HINT
            ), false, false, false));
            meta.setCustomModelData(2504);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createInsolventLedger() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "insolvent_ledger");
            ItemStats stats = BossGearBalance.base("insolvent_ledger");
            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);
            meta.setDisplayName("§6Insolvent Ledger");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⚔ Damage: §f+20.00",
                    "",
                    "§eOff-hand accessory",
                    "§5✦ Foreclosure",
                    "§7Kills pay extra coins. The house always wins.",
                    "§8You are briefly the house.",
                    "§8The Insolvent Wither left a bookmark.",
                    DungeonCore.BOSS_CORE_HINT
            ), false, false, false));
            meta.setCustomModelData(2505);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }


    /*
     * =========================================================
     * DUNGEON CORE
     * =========================================================
     *
     * Rare dungeon-chest drop. Anvil-infuse boss relics,
     * Aetherion armor, and dungeon calling sets.
     * Each core raises rarity, level cap, and dungeon stats.
     * =========================================================
     */

    public ItemStack createDungeonCore() {
        return createDungeonCoreTier(1);
    }

    public ItemStack createBlueprintUpgradeStone2() {
        return de.aetherion.items.blueprint.BlueprintUpgrade.createStone(2);
    }

    public ItemStack createBlueprintUpgradeStone3() {
        return de.aetherion.items.blueprint.BlueprintUpgrade.createStone(3);
    }

    public ItemStack createBlueprintUpgradeStone4() {
        return de.aetherion.items.blueprint.BlueprintUpgrade.createStone(4);
    }

    public ItemStack createDungeonCore2() {
        return createDungeonCoreTier(2);
    }

    public ItemStack createDungeonCore3() {
        return createDungeonCoreTier(3);
    }

    public ItemStack createDungeonVestigeHelmet() {
        return dungeonVestige(DungeonPiece.HELMET);
    }

    public ItemStack createDungeonVestigeChestplate() {
        return dungeonVestige(DungeonPiece.CHESTPLATE);
    }

    public ItemStack createDungeonVestigeLeggings() {
        return dungeonVestige(DungeonPiece.LEGGINGS);
    }

    public ItemStack createDungeonVestigeBoots() {
        return dungeonVestige(DungeonPiece.BOOTS);
    }

    public ItemStack createRandomDungeonVestige() {
        DungeonPiece[] pieces = DungeonPiece.values();
        return dungeonVestige(pieces[ThreadLocalRandom.current().nextInt(pieces.length)]);
    }

    public ItemStack createDungeonArmor(DungeonCalling calling, DungeonPiece piece) {
        return createDungeonArmor(calling, piece, DungeonGearTier.T1);
    }

    public ItemStack createDungeonArmor(DungeonCalling calling, DungeonPiece piece, DungeonGearTier tier) {
        if (calling == null || piece == null) {
            return dungeonVestige(piece == null ? DungeonPiece.CHESTPLATE : piece);
        }
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        ItemStats stats = calling.stats(piece, safe);
        ItemStack item = dungeonArmorPiece(
                calling.material(piece),
                calling.itemId(safe, piece),
                calling.displayName(safe, piece),
                calling.leatherColor(),
                calling.model(safe, piece),
                stats,
                DungeonArmor.attunedLore(calling, piece, stats, safe),
                safe.rarity()
        );
        de.aetherion.items.dungeon.DungeonGearProgress.ensure(item, itemManager);
        return item;
    }

    public ItemStack createDungeonWeapon(DungeonWeaponKind kind, DungeonGearTier tier) {
        return createDungeonWeapon(kind, tier, tier == null || tier == DungeonGearTier.T1);
    }

    public ItemStack createDungeonWeapon(DungeonWeaponKind kind, DungeonGearTier tier, boolean dungeonFound) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        DungeonWeaponKind weapon = kind == null ? DungeonWeaponKind.SWORD : kind;
        boolean nativeGear = dungeonFound || safe == DungeonGearTier.T1;
        ItemStats stats = weapon.stats(safe);
        ItemStack item = new ItemStack(weapon.material(safe));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, weapon.itemId(safe));
            applyItemData(meta, safe.rarity(), stats);
            meta.setDisplayName(weapon.displayName(safe));
            meta.setLore(createLore(weapon.itemId(safe), DungeonArmor.weaponLore(weapon, safe, stats, nativeGear), stats));
            meta.setCustomModelData(weapon.model(safe));
            meta.setUnbreakable(true);
            if (nativeGear) {
                meta.getPersistentDataContainer().set(ItemKeys.dungeonNative(), PersistentDataType.BYTE, (byte) 1);
            }
            if (weapon == DungeonWeaponKind.BOW) {
                meta.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
                meta.getPersistentDataContainer().set(
                        ItemKeys.shortbowInterval(),
                        PersistentDataType.INTEGER,
                        safe == DungeonGearTier.T3 ? 18 : safe == DungeonGearTier.T1 ? 28 : 26
                );
                meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 1);
            }
            if (weapon == DungeonWeaponKind.WAND) {
                if (safe == DungeonGearTier.T3) {
                    markWand(meta, "ember", 32.0, 6_000L, 2.8, 1, 0);
                } else {
                    markWand(meta, "lightning", 30.0, 8_000L, 2.5, 0, 0);
                }
            }
            if (weapon == DungeonWeaponKind.STAFF) {
                if (safe == DungeonGearTier.T3) {
                    markWand(meta, "heal", 32.0, 4_000L, 2.8, 0, 0);
                } else {
                    markWand(meta, "heal", 28.0, 5_000L, 2.4, 0, 0);
                }
            }
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        de.aetherion.items.dungeon.DungeonGearProgress.ensure(item, itemManager);
        return item;
    }

    public ItemStack createDungeonRelic(DungeonGearTier tier, DungeonPiece piece) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        ItemStats stats = DungeonRelic.stats(safe, piece);
        return dungeonArmorPiece(
                piece.leather(),
                DungeonRelic.itemId(safe, piece),
                DungeonRelic.displayName(safe, piece),
                safe.leather(),
                2780 + safe.floor() * 10 + piece.ordinal(),
                stats,
                DungeonRelic.lore(safe, piece, stats),
                safe.rarity()
        );
    }

    public ItemStack createDungeonWeaponRelic(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        ItemStats stats = DungeonRelic.stats(safe, null);
        ItemStack item = new ItemStack(safe.relicIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, DungeonRelic.weaponId(safe));
            applyItemData(meta, safe.rarity(), stats);
            meta.setDisplayName(DungeonRelic.displayName(safe, null));
            meta.setLore(createLore(DungeonRelic.weaponId(safe), DungeonRelic.lore(safe, null, stats), stats));
            meta.setCustomModelData(2790 + safe.floor());
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createWeaponSchematic() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, SetWeaponKind.SCHEMATIC_ID);
            applyItemData(meta, Rarity.EPIC, new ItemStats());
            meta.setDisplayName("§dWeapon Schematic");
            meta.setLore(createLore(List.of(
                    "§7✦ §5EPIC",
                    "",
                    "§7Unidentified overworld schematic.",
                    "§7Pick a weapon that matches an armor set.",
                    "",
                    "§8Combat · Mining · Farming · Fishing",
                    "§8Rotten · Bone · Webweave",
                    "§8Ironhide · Healer · Catcher",
                    "",
                    "§eRight-click §7to identify.",
                    "§8Boss drop. There is no undo."
            ), false, false, false));
            meta.setCustomModelData(2795);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createDungeonWeaponSchematic() {
        return createDungeonWeaponSchematic(1);
    }

    public ItemStack createDungeonWeaponSchematic(int floor) {
        DungeonGearTier tier = DungeonGearTier.fromFloor(floor);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, DungeonWeaponKind.SCHEMATIC_ID);
            applyItemData(meta, tier.rarity(), new ItemStats());
            meta.getPersistentDataContainer().set(
                    ItemKeys.dungeonSchematicFloor(),
                    PersistentDataType.INTEGER,
                    tier.floor()
            );
            meta.setDisplayName("§5Dungeon Weapon Schematic");
            meta.setLore(createLore(List.of(
                    tier.rarityLine(),
                    "",
                    "§7Unidentified dungeon schematic.",
                    "§7Pick a weapon that matches a vestige class.",
                    "",
                    "§bTank §8· §5Assassin §8· §cSoldier",
                    "§eHealer §8· §3Shaman",
                    "",
                    "§8Floor " + tier.roman() + ". No farm tools.",
                    "",
                    "§eRight-click §7to identify.",
                    "§8Dungeon drop. There is no undo."
            ), false, false, false));
            meta.setCustomModelData(2796);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createRandomDungeonRelic(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        String id = DungeonRelic.randomArmorId(safe);
        return createDungeonFromId(id);
    }

    public ItemStack createRandomDungeonRelicT2() {
        return createRandomDungeonRelic(DungeonGearTier.T2);
    }

    public ItemStack createRandomDungeonRelicT3() {
        return createRandomDungeonRelic(DungeonGearTier.T3);
    }

    public ItemStack createDungeonFromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        if (DungeonWeaponKind.isSchematic(id)) {
            return createDungeonWeaponSchematic();
        }
        if (SetWeaponKind.isSchematic(id)) {
            return createWeaponSchematic();
        }
        if (DungeonRelic.isWeaponRelic(id)) {
            return createDungeonWeaponRelic(DungeonRelic.tier(id));
        }
        if (DungeonRelic.isUnidentified(id)) {
            DungeonPiece piece = DungeonRelic.piece(id);
            return piece == null ? null : createDungeonRelic(DungeonRelic.tier(id), piece);
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(id);
        if (kind != null) {
            return createDungeonWeapon(kind, DungeonGearTier.fromItemId(id));
        }
        DungeonCalling calling = DungeonCalling.fromItemId(id);
        DungeonPiece piece = DungeonPiece.fromItemId(id);
        if (calling != null && piece != null) {
            return createDungeonArmor(calling, piece, DungeonGearTier.fromItemId(id));
        }
        return null;
    }

    /**
     * Cross-plugin factory used via {@link de.aetherion.core.api.ItemFactoryAccess}.
     * Domain factories first, then dungeon ids, then legacy {@code createXxx} methods.
     */
    public ItemStack createById(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        for (java.util.function.Function<String, ItemStack> factory : idFactories) {
            ItemStack created = factory.apply(id);
            if (created != null) {
                return created;
            }
        }
        try {
            java.lang.reflect.Method method = getClass().getMethod(toCreateMethodName(id));
            Object created = method.invoke(this);
            return created instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String toCreateMethodName(String itemId) {
        StringBuilder builder = new StringBuilder("create");
        for (String part : itemId.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.chars().allMatch(Character::isDigit)) {
                builder.append(part);
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private ItemStack dungeonVestige(DungeonPiece piece) {
        ItemStats stats = DungeonArmor.vestigeStats(piece);
        return dungeonArmorPiece(
                piece.leather(),
                "dungeon_vestige_" + piece.id(),
                "§7Dungeon Vestige " + piece.display(),
                DungeonArmor.VESTIGE_COLOR,
                2691 + piece.ordinal(),
                stats,
                DungeonArmor.vestigeLore(piece, stats),
                Rarity.RARE
        );
    }

    private ItemStack createDungeonCoreTier(int grade) {
        int safe = Math.max(1, Math.min(DungeonCore.MAX_TIER, grade));
        Material material = switch (safe) {
            case 2 -> Material.ENDER_EYE;
            case 3 -> Material.NETHER_STAR;
            default -> Material.HEART_OF_THE_SEA;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, DungeonCore.itemIdForGrade(safe));
            meta.getPersistentDataContainer().set(
                    ItemKeys.dungeonCoreTier(),
                    PersistentDataType.INTEGER,
                    safe
            );
            applyItemData(meta, Rarity.EPIC, new ItemStats());
            meta.setDisplayName("§5Dungeon Core " + DungeonCore.roman(safe));
            meta.setLore(List.of(
                    "§7✦ §5EPIC",
                    "",
                    coreFlavor(safe),
                    "§7Anvil: boss gear becomes dungeon gear.",
                    "§7Then it levels. Stronger inside dungeons.",
                    "§7Rarity itself adds a little extra to stats.",
                    "§8T1 is a nudge. T3 is the real argument.",
                    "§8Requires Core " + DungeonCore.roman(safe)
                            + " on a relic at " + previousCoreLabel(safe) + ".",
                    "",
                    "§8Floor " + safe + " dungeon caches"
            ));
            meta.setCustomModelData(2500 + safe);
            meta.setMaxStackSize(1);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String coreFlavor(int grade) {
        return switch (grade) {
            case 2 -> "§5Second spark. The dungeon is less amused.";
            case 3 -> "§5The last honest piece of a dishonest floor.";
            default -> "§5A dungeon coughed. This is the hairball.";
        };
    }

    private static String previousCoreLabel(int grade) {
        return switch (grade) {
            case 2 -> "Core I";
            case 3 -> "Core II";
            default -> "no infusion";
        };
    }

    private ItemStack dungeonArmorPiece(
            Material material,
            String id,
            String name,
            Color color,
            int model,
            ItemStats stats,
            List<String> lore,
            Rarity rarity
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, id);
            applyItemData(meta, rarity == null ? Rarity.RARE : rarity, stats);
            meta.setDisplayName(name);
            meta.setLore(createLore(id, lore, stats));
            meta.setCustomModelData(model);
            meta.setUnbreakable(true);
            if (color != null) {
                applyArmorColor(meta, color);
            }
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }


    /*
     * =========================================================
     * AETHERION SET
     * =========================================================
     */

    public ItemStack createRandomAetherionArmor() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> createAetherionHelmet();
            case 1 -> createAetherionChestplate();
            case 2 -> createAetherionLeggings();
            default -> createAetherionBoots();
        };
    }

    public ItemStack createAetherionHelmet() {
        return createAetherionArmor(
                Material.NETHERITE_HELMET,
                "aetherion_helmet",
                "§d✦✦✦ §5Aetherion Helmet",
                70, 110, 22, 22, 6, 42, 2, 2311
        );
    }

    public ItemStack createAetherionChestplate() {
        return createAetherionArmor(
                Material.NETHERITE_CHESTPLATE,
                "aetherion_chestplate",
                "§d✦✦✦ §5Aetherion Chestplate",
                140, 180, 42, 35, 12, 70, 3, 2312
        );
    }

    public ItemStack createAetherionLeggings() {
        return createAetherionArmor(
                Material.NETHERITE_LEGGINGS,
                "aetherion_leggings",
                "§d✦✦✦ §5Aetherion Leggings",
                105, 145, 32, 28, 8, 55, 3, 2313
        );
    }

    public ItemStack createAetherionBoots() {
        return createAetherionArmor(
                Material.NETHERITE_BOOTS,
                "aetherion_boots",
                "§d✦✦✦ §5Aetherion Boots",
                70, 110, 22, 20, 6, 42, 6, 2314
        );
    }

    private ItemStack createAetherionArmor(
            Material material,
            String itemId,
            String displayName,
            double defense,
            double health,
            double damage,
            double attackSpread,
            double critChance,
            double critDamage,
            double speed,
            int modelData
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, itemId);
            ItemStats stats = new ItemStats();
            stats.setDefense(defense);
            stats.setHealth(health);
            stats.setDamage(damage);
            stats.setAttackSpread(attackSpread);
            stats.setCritChance(critChance);
            stats.setCritDamage(critDamage);
            stats.setSpeed(speed);
            applyItemData(meta, Rarity.MYTHIC, stats);
            BossGearBalance.stamp(meta);
            meta.setDisplayName(displayName);
            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5MYTHIC",
                                    "",
                                    "§7🛡 Defense: §f+" + formatAetherionStat(defense),
                                    "§7❤ Health: §f+" + formatAetherionStat(health),
                                    "§7⚔ Damage: §f+" + formatAetherionStat(damage),
                                    "§7⚔ Attack Spread: §f+" + formatAetherionStat(attackSpread),
                                    "§7✧ Crit Chance: §f+" + formatAetherionStat(critChance) + "%",
                                    "§7✧ Crit Damage: §f+" + formatAetherionStat(critDamage) + "%",
                                    "§7✦ Speed: §f+" + formatAetherionStat(speed) + "%",
                                    "",
                                    "§5Full Set",
                                    "§7Survive death once. §8(15m)",
                                    "§7A mini Ender Dragon follows you.",
                                    "§7It spits purple fire at mobs.",
                                    DungeonCore.AETHERION_CORE_HINT,
                                    "",
                                    "§8Forged from the last dragon"
                            ),
                            false,
                            true,
                            true
                    )
            );
            meta.setCustomModelData(modelData);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createAetherionVoidStick() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "aetherion_void_stick");
            ItemStats stats = BossGearBalance.base("aetherion_void_stick");
            applyItemData(meta, Rarity.MYTHIC, stats);
            BossGearBalance.stamp(meta);
            meta.setDisplayName("§d✦✦✦ §5Void Stick");
            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §5MYTHIC",
                                    "",
                                    "§7⚔ Damage: §f+100.00",
                                    "",
                                    "§5✦ Slow Singularity",
                                    "§7Right-click to pull foes",
                                    "§7within 25 blocks and drain life.",
                                    "§8Hold right-click. Not while idle.",
                                    DungeonCore.AETHERION_CORE_HINT,
                                    "",
                                    "§8Aetherion's leftover spark"
                            ),
                            false,
                            false,
                            false
                    )
            );
            meta.setCustomModelData(2315);
            meta.setUnbreakable(true);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatAetherionStat(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }


    /*
     * =========================================================
     * CATCHER SET
     * =========================================================
     */

    public ItemStack createCatcherHelmet() {
        return catcher.helmet(1);
    }

    public ItemStack createCatcherChestplate() {
        return catcher.chestplate(1);
    }

    public ItemStack createCatcherLeggings() {
        return catcher.leggings(1);
    }

    public ItemStack createCatcherBoots() {
        return catcher.boots(1);
    }

    public ItemStack createCatcherGaff() {
        return catcher.gaff(1);
    }

    /*
     * =========================================================
     * SHORTBOW
     * =========================================================
     *
     * Hold right-click to fire. No arrows required.
     * First shortbow is Skuldugery's legendary drop.
     * =========================================================
     */

    public ItemStack createShortbow() {
        return createSkuldugeryShortbow();
    }

    public ItemStack createSkuldugeryShortbow() {

        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            setItemId(meta, "skuldugery_shortbow");

            ItemStats stats = BossGearBalance.base("skuldugery_shortbow");

            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);

            meta.getPersistentDataContainer().set(
                    ItemKeys.shortbow(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            meta.getPersistentDataContainer().set(
                    ItemKeys.shortbowInterval(),
                    PersistentDataType.INTEGER,
                    20
            );

            meta.setDisplayName("§6Skuldugery's Shortbow");

            meta.setLore(
                    createLore(
                            List.of(
                                    "§7✦ §6LEGENDARY",
                                    "",
                                    "§7⚔ Damage: §f+88.00",
                                    "§7⚔ Attack Spread: §f+16.00",
                                    "§7✧ Crit Chance: §f+12.00%",
                                    "§7✧ Crit Damage: §f+85.00%",
                                    "",
                                    "§6⚡ Fire Rate: §f1.00s",
                                    "§7Hold right-click to fire.",
                                    "§7No arrows required. Hits Endermen.",
                                    "§8Core I: two arrows. Core II: three. Core III: piercing, Endermen.",
                                    DungeonCore.BOSS_CORE_HINT,
                                    "",
                                    "§8Skuldugery's personal bow"
                            ),
                            false,
                            true,
                            false
                    )
            );

            meta.setCustomModelData(2201);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            hideVanillaAttributes(meta);

            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * AETHERION RECIPE BOOK
     * =========================================================
     *
     * Special utility item used to open the
     * Aetherion Recipe Book GUI.
     *
     * ID:
     * recipe_book
     *
     * Uses the normal Minecraft Book texture for now.
     * =========================================================
     */

    public ItemStack createRecipeBook() {

        ItemStack item =
                new ItemStack(
                        Material.BOOK
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            /*
             * =================================================
             * AETHERION ITEM ID
             * =================================================
             */

            setItemId(meta, "recipe_book");


            /*
             * =================================================
             * DISPLAY
             * =================================================
             */

            meta.setDisplayName(
                    "§6Aetherion Recipe Book"
            );

            meta.setLore(
                    List.of(
                            "§7✦ §6AETHERION",
                            "",
                            "§7Discover and explore",
                            "§7all available recipes.",
                            "",
                            "§eRight-click to open"
                    )
            );


            /*
             * =================================================
             * ITEM SETTINGS
             * =================================================
             */

            meta.setMaxStackSize(1);
            meta.setCustomModelData(3008);


            /*
             * =================================================
             * APPLY META
             * =================================================
             */

            item.setItemMeta(meta);
        }

        return item;
    }

    /*
     * =========================================================
     * TEST ARENA GEAR (sandbox prototypes — unbreakable, real stats)
     * =========================================================
     */

    public ItemStack createEchoBlade() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "echo_blade");
            ItemStats stats = new ItemStats();
            stats.setDamage(95);
            stats.setCritChance(14);
            stats.setCritDamage(110);
            stats.setAttackSpread(4);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "echo_blade");
            meta.setDisplayName("§d✦ Echo Blade");
            meta.setLore(createLore("echo_blade", List.of(
                    "§7✦ §6LEGENDARY §8· §dSandbox",
                    "",
                    "§7⚔ Damage: §f+95.00",
                    "§7⚔ Attack Spread: §f+4.00",
                    "§7✧ Crit Chance: §f+14.00%",
                    "§7✧ Crit Damage: §f+110.00%",
                    "",
                    "§d✦ Afterimage Slash",
                    "§7On hit: a ghost swing strikes",
                    "§7again after §f0.4s §7for §d40% §7damage.",
                    "",
                    "§8Test Arena · pairs with The Echo"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createParityGauntlets() {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "parity_gauntlets");
            ItemStats stats = new ItemStats();
            stats.setDamage(88);
            stats.setCritChance(10);
            stats.setCritDamage(95);
            stats.setAttackSpread(6);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "parity_gauntlets");
            meta.setDisplayName("§b✦ Parity Gauntlets");
            meta.setLore(createLore("parity_gauntlets", List.of(
                    "§7✦ §6LEGENDARY §8· §bSandbox",
                    "",
                    "§7⚔ Damage: §f+88.00",
                    "§7⚔ Attack Spread: §f+6.00",
                    "§7✧ Crit Chance: §f+10.00%",
                    "§7✧ Crit Damage: §f+95.00%",
                    "",
                    "§b✦ Twin Sync",
                    "§7Hit two different targets within",
                    "§f0.9s §7→ next hit deals §b+55% §7damage.",
                    "",
                    "§8Test Arena · pairs with Parity"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createRulebreakerCharm() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "rulebreaker_charm");
            ItemStats stats = new ItemStats();
            stats.setDamage(12);
            stats.setCritChance(8);
            stats.setCritDamage(40);
            applyItemData(meta, Rarity.EPIC, stats);
            stampTestGear(meta, "rulebreaker_charm");
            meta.setDisplayName("§6✦ Rulebreaker Charm");
            meta.setLore(createLore("rulebreaker_charm", List.of(
                    "§7✦ §5EPIC §8· §6Sandbox Charm",
                    "",
                    "§7⚔ Damage: §f+12.00",
                    "§7✧ Crit Chance: §f+8.00%",
                    "§7✧ Crit Damage: §f+40.00%",
                    "",
                    "§6✦ Writ of Exception",
                    "§7Off-hand: ignore Curator rules.",
                    "§7Softens Broker pressure.",
                    "§eRight-click: §7purge §cSlowness §7(12s CD).",
                    "",
                    "§8Test Arena · off-hand"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createMetronomeBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "metronome_bow");
            ItemStats stats = new ItemStats();
            stats.setDamage(120);
            stats.setCritChance(16);
            stats.setCritDamage(125);
            stats.setAttackSpread(8);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "metronome_bow");
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 0);
            meta.setDisplayName("§4✦ Metronome Bow");
            meta.setLore(createLore("metronome_bow", List.of(
                    "§7✦ §6LEGENDARY §8· §4Sandbox",
                    "",
                    "§7⚔ Damage: §f+120.00",
                    "§7⚔ Attack Spread: §f+8.00",
                    "§7✧ Crit Chance: §f+16.00%",
                    "§7✧ Crit Damage: §f+125.00%",
                    "",
                    "§4✦ On the Beat",
                    "§7No arrows needed. Shots on a",
                    "§f2s §7pulse deal §c+40% §7damage.",
                    "§7Off-beat shots are weakened.",
                    "",
                    "§8Test Arena · pairs with Heartbeat"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createSoftlockPlate() {
        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "softlock_plate");
            ItemStats stats = new ItemStats();
            stats.setDefense(85);
            stats.setHealth(14);
            stats.setDamage(8);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "softlock_plate");
            meta.setDisplayName("§c✦ Softlock Plate");
            meta.setLore(createLore("softlock_plate", List.of(
                    "§7✦ §6LEGENDARY §8· §cSandbox",
                    "",
                    "§7🛡 Defense: §f+85.00",
                    "§7❤ Health: §f+14.00",
                    "§7⚔ Damage: §f+8.00",
                    "",
                    "§c✦ Hardened Inventory",
                    "§7Ignores Softlock slot shocks.",
                    "§7When hit: §c10% §7chance to reflect",
                    "§7§f8 §7true damage (no CD spam).",
                    "",
                    "§8Test Arena · pairs with Softlock"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createNullstepBoots() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "nullstep_boots");
            ItemStats stats = new ItemStats();
            stats.setDefense(42);
            stats.setSpeed(18);
            stats.setHealth(6);
            applyItemData(meta, Rarity.EPIC, stats);
            stampTestGear(meta, "nullstep_boots");
            meta.setDisplayName("§3✦ Nullstep Boots");
            meta.setLore(createLore("nullstep_boots", List.of(
                    "§7✦ §5EPIC §8· §3Sandbox",
                    "",
                    "§7🛡 Defense: §f+42.00",
                    "§7❤ Health: §f+6.00",
                    "§7✦ Speed: §f+18.00%",
                    "",
                    "§3✦ Void Tread",
                    "§7Resist Gravity Clerk pull.",
                    "§7Sneak in air: brief hover /",
                    "§7slow-fall (§f6s §7CD).",
                    "",
                    "§8Test Arena · Nullspace / Gravity"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBrokerContract() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "broker_contract");
            ItemStats stats = new ItemStats();
            stats.setDamage(6);
            stats.setCritChance(5);
            applyItemData(meta, Rarity.RARE, stats);
            stampTestGear(meta, "broker_contract");
            meta.setDisplayName("§e✦ Broker's Contract");
            meta.setLore(createLore("broker_contract", List.of(
                    "§7✦ §9RARE §8· §eSandbox",
                    "",
                    "§7⚔ Damage: §f+6.00",
                    "§7✧ Crit Chance: §f+5.00%",
                    "",
                    "§e✦ Sealed Bargain",
                    "§7Right-click: spend §c10% HP",
                    "§7for §aStrength II §7(8s).",
                    "§8Never consumed. §7CD §f14s§7.",
                    "",
                    "§8Test Arena · pairs with Broker"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createGravwellCleaver() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "gravwell_cleaver");
            ItemStats stats = BossGearBalance.base("gravwell_cleaver");
            applyItemData(meta, Rarity.LEGENDARY, stats);
            BossGearBalance.stamp(meta);
            BossWeaponLook.apply(meta, "gravwell_cleaver");
            meta.setDisplayName("§5Gravwell Cleaver");
            meta.setLore(createLore(List.of(
                    "§7✦ §6LEGENDARY",
                    "",
                    "§7⚔ Damage: §f+115.00",
                    "§7⚔ Attack Spread: §f+4.00",
                    "§7✧ Crit Chance: §f+13.00%",
                    "§7✧ Crit Damage: §f+110.00%",
                    "",
                    "§5The gate keeps a toll. This collects it.",
                    "",
                    "§5✦ Event Horizon",
                    "§7Right-click: open a gravity well,",
                    "§7pull hostiles in, then detonate.",
                    "§8Players & pets ignored. §7CD §f5s§7.",
                    DungeonCore.BOSS_CORE_HINT,
                    "",
                    "§8Torn from the Pathwarden"
            ), false, true, false));
            meta.setCustomModelData(3301);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createStormcallerMaul() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "stormcaller_maul");
            ItemStats stats = new ItemStats();
            stats.setDamage(112);
            stats.setCritChance(8);
            stats.setCritDamage(130);
            stats.setAttackSpread(5);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "stormcaller_maul");
            meta.setDisplayName("§e✦ Stormcaller Maul");
            meta.setLore(createLore("stormcaller_maul", List.of(
                    "§7✦ §6LEGENDARY §8· §eSandbox",
                    "",
                    "§7⚔ Damage: §f+112.00",
                    "§7⚔ Attack Spread: §f+5.00",
                    "§7✧ Crit Chance: §f+8.00%",
                    "§7✧ Crit Damage: §f+130.00%",
                    "",
                    "§e✦ Thunderstorm",
                    "§7Right-click: call a §f6s §7storm.",
                    "§7Quiet bolts hunt enemies in view.",
                    "§8No player/pet targets. §7CD §f24s§7 §8(off in arena).",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setCustomModelData(3302);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createResonanceScythe() {
        return createResonanceScythe(true);
    }

    /** @param testStamp when false, player/blueprint craft without sandbox stamp */
    public ItemStack createResonanceScythe(boolean testStamp) {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "resonance_scythe");
            ItemStats stats = new ItemStats();
            stats.setDamage(36);
            stats.setCritChance(8);
            stats.setCritDamage(50);
            stats.setAttackSpread(6);
            applyItemData(meta, Rarity.EPIC, stats);
            de.aetherion.items.blueprint.BlueprintUpgrade.writeTier(meta, 1);
            if (testStamp) {
                stampTestGear(meta, "resonance_scythe");
            }
            meta.setDisplayName("§3✦ Resonance Scythe");
            meta.setLore(createLore("resonance_scythe", List.of(
                    testStamp ? "§7✦ §5EPIC §8· §3Sandbox" : "§7✦ §5EPIC",
                    "§bBlueprint Tier: §fI/IV",
                    "",
                    "§7⚔ Damage: §f+36.00",
                    "§7⚔ Attack Spread: §f+6.00",
                    "§7✧ Crit Chance: §f+8.00%",
                    "§7✧ Crit Damage: §f+50.00%",
                    "",
                    "§3✦ Resonance Wave",
                    "§7Right-click: fire a sonic line",
                    "§7with small AoE along the path.",
                    "§7Cooldown: §f5s",
                    "",
                    testStamp ? "§8Test Arena prototype" : "§8Aetherion Combat Tool"
            ), stats));
            meta.setCustomModelData(3303);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createJudgmentStaff() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "judgment_staff");
            ItemStats stats = new ItemStats();
            stats.setDamage(88);
            stats.setCritChance(14);
            stats.setCritDamage(120);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "judgment_staff");
            meta.setDisplayName("§a✦ Judgment Staff");
            meta.setLore(createLore("judgment_staff", List.of(
                    "§7✦ §6LEGENDARY §8· §aSandbox",
                    "",
                    "§7⚔ Damage: §f+88.00",
                    "§7✧ Crit Chance: §f+14.00%",
                    "§7✧ Crit Damage: §f+120.00%",
                    "",
                    "§a✦ Judgment Beam",
                    "§7Right-click: beacon beam follows look",
                    "§7§aGreen → §cRed§7, pulses, then nukes.",
                    "§8CD off in Test Arena.",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createDashDagger() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "dash_dagger");
            ItemStats stats = new ItemStats();
            stats.setDamage(78);
            stats.setCritChance(22);
            stats.setCritDamage(135);
            stats.setAttackSpread(2);
            applyItemData(meta, Rarity.EPIC, stats);
            stampTestGear(meta, "dash_dagger");
            meta.setDisplayName("§f✦ Dash Dagger");
            meta.setLore(createLore("dash_dagger", List.of(
                    "§7✦ §5EPIC §8· §fSandbox",
                    "",
                    "§7⚔ Damage: §f+78.00",
                    "§7⚔ Attack Spread: §f+2.00",
                    "§7✧ Crit Chance: §f+22.00%",
                    "§7✧ Crit Damage: §f+135.00%",
                    "",
                    "§f✦ Forward Dash",
                    "§7Right-click: real dash ~§f5 §7blocks",
                    "§7(velocity, not teleport).",
                    "§8CD off in Test Arena.",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createCycloneRod() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "cyclone_rod");
            ItemStats stats = new ItemStats();
            stats.setDamage(84);
            stats.setCritChance(10);
            stats.setCritDamage(100);
            stats.setAttackSpread(6);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "cyclone_rod");
            meta.setDisplayName("§f✦ Cyclone Rod");
            meta.setLore(createLore("cyclone_rod", List.of(
                    "§7✦ §6LEGENDARY §8· §fSandbox",
                    "",
                    "§7⚔ Damage: §f+84.00",
                    "§7⚔ Attack Spread: §f+6.00",
                    "§7✧ Crit Chance: §f+10.00%",
                    "§7✧ Crit Damage: §f+100.00%",
                    "",
                    "§f✦ Cyclone",
                    "§7Right-click: spin enemies in a tornado,",
                    "§7then fling them far out.",
                    "§8CD off in Test Arena.",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createPrismStaff() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "prism_staff");
            ItemStats stats = new ItemStats();
            stats.setDamage(90);
            stats.setCritChance(12);
            stats.setCritDamage(110);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "prism_staff");
            meta.setDisplayName("§d✦ Prism Staff");
            meta.setLore(createLore("prism_staff", List.of(
                    "§7✦ §6LEGENDARY §8· §dSandbox",
                    "",
                    "§7⚔ Damage: §f+90.00",
                    "§7✧ Crit Chance: §f+12.00%",
                    "§7✧ Crit Damage: §f+110.00%",
                    "",
                    "§d✦ Twin Prisms",
                    "§7Right-click: two half-buried cubes",
                    "§7orbit opposite ways, shrink & spin,",
                    "§7then §cBig Bang§7.",
                    "§8CD off in Test Arena.",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createCascadeShortbow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "cascade_shortbow");
            ItemStats stats = new ItemStats();
            stats.setDamage(98);
            stats.setCritChance(15);
            stats.setCritDamage(115);
            stats.setAttackSpread(5);
            applyItemData(meta, Rarity.LEGENDARY, stats);
            stampTestGear(meta, "cascade_shortbow");
            meta.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.shortbowInterval(), PersistentDataType.INTEGER, 14);
            meta.getPersistentDataContainer().set(ItemKeys.needsAmmo(), PersistentDataType.BYTE, (byte) 0);
            meta.setDisplayName("§6✦ Cascade Shortbow");
            meta.setLore(createLore("cascade_shortbow", List.of(
                    "§7✦ §6LEGENDARY §8· §6Sandbox Shortbow",
                    "",
                    "§7⚔ Damage: §f+98.00",
                    "§7⚔ Attack Spread: §f+5.00",
                    "§7✧ Crit Chance: §f+15.00%",
                    "§7✧ Crit Damage: §f+115.00%",
                    "",
                    "§6✦ Cascade Bolt",
                    "§eSneak + Right-click: §7huge bolt",
                    "§7chains up to §f10 §7targets.",
                    "§8Attack Spread ignored. §7Arena: no CD.",
                    "",
                    "§8Test Arena prototype"
            ), stats));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            hideVanillaAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void stampTestGear(ItemMeta meta, String id) {
        if (meta == null || id == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                de.aetherion.core.AetherKeys.namespaced("aetherion", "test_gear"),
                PersistentDataType.STRING,
                id
        );
    }

    /**
     * Provisional DEV / ritual item. Ascends an equipped Mythic dragon to Aethered.
     */
    public ItemStack createDragonAscensionVial() {
        ItemStack item = new ItemStack(Material.DRAGON_BREATH);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setItemId(meta, "dragon_ascension_vial");
            applyItemData(meta, Rarity.AETHERED, new ItemStats());
            meta.setDisplayName("§4✦ Dragon Ascension Vial");
            meta.setLore(List.of(
                    "",
                    "§7Provisional ritual item.",
                    "§7Equip a §5Mythic §7dragon, then right-click.",
                    "§7Raises it to §4Aethered §7(max level 200).",
                    "",
                    "§8DEV / endgame placeholder"
            ));
            meta.setMaxStackSize(16);
            item.setItemMeta(meta);
        }
        return item;
    }
}