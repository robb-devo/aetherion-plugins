package de.aetherion.items.economy;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listed coin values for trader / AH / lore.
 * Primary source: {@code economy.yml} sections {@code items}, {@code resources},
 * {@code crafted}, {@code drops}. Compress formulas in {@link EconomyCurve} fill gaps only.
 */
public final class ItemValueService {

    private static final Map<Rarity, Long> RARITY_FALLBACK = Map.of(
            Rarity.COMMON, 2_500L,
            Rarity.UNCOMMON, 12_000L,
            Rarity.RARE, 45_000L,
            Rarity.EPIC, 180_000L,
            Rarity.LEGENDARY, 650_000L,
            Rarity.MYTHIC, 2_200_000L
    );

    private final ItemManager itemManager;
    private final Map<Material, Long> vanilla = new EnumMap<>(Material.class);
    private final Map<String, Long> items = new ConcurrentHashMap<>();

    public ItemValueService(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        File file = new File(plugin.getDataFolder(), "economy.yml");
        ensureEconomyFile(plugin, file);
        load(file);
    }

    private static void ensureEconomyFile(JavaPlugin plugin, File file) {
        final int expectedVersion = 2;
        if (!file.exists()) {
            plugin.saveResource("economy.yml", false);
            return;
        }
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
        int version = existing.getInt("economy-version", 1);
        if (version < expectedVersion) {
            plugin.getLogger().info("Updating economy.yml to version " + expectedVersion + " (was " + version + ").");
            plugin.saveResource("economy.yml", true);
        }
    }

    public long valueOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        return unitValue(stack) * Math.max(1, stack.getAmount());
    }

    public long unitValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        String itemId = itemManager.getItemId(stack);
        if (itemId != null) {
            long value = unitValue(itemId, itemManager.getRarity(stack));
            return value > 0L ? value : RARITY_FALLBACK.get(Rarity.COMMON);
        }
        Long listed = vanilla.get(stack.getType());
        if (listed != null) {
            return listed;
        }
        return VanillaValues.of(stack.getType());
    }

    public long unitValue(String itemId) {
        return unitValue(itemId, null);
    }

    public long unitValue(String itemId, Rarity rarity) {
        if (itemId == null || itemId.isBlank()) {
            return 0L;
        }
        Long listed = items.get(itemId.toLowerCase(Locale.ROOT));
        if (listed != null) {
            return listed;
        }
        if (rarity != null) {
            return RARITY_FALLBACK.getOrDefault(rarity, RARITY_FALLBACK.get(Rarity.COMMON));
        }
        return RARITY_FALLBACK.get(Rarity.COMMON);
    }

    public boolean canSell(ItemStack stack) {
        return isResource(stack) && unitValue(stack) > 0L;
    }

    public boolean traderTakes(ItemStack stack) {
        return canSell(stack) && !isBlockedBulk(stack);
    }

    public boolean traderTakesVanilla(ItemStack stack) {
        if (stack == null || itemManager.getItemId(stack) != null) {
            return false;
        }
        return traderTakes(stack);
    }

    private boolean isBlockedBulk(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        if (de.aetherion.items.world.BorderlandsRiteService.isSpirit(stack)
                || de.aetherion.items.world.BorderlandsRiteService.isCryptSpirit(stack)) {
            return true;
        }
        if (CompressedResource.isTradeable(itemManager.getItemId(stack))) {
            return false;
        }
        Material material = stack.getType();
        if (isEquipmentMaterial(material)) {
            return true;
        }
        String name = material.name();
        if (name.endsWith("_HEAD")
                || name.endsWith("_SKULL")
                || name.endsWith("_SPAWN_EGG")
                || name.contains("SHULKER_BOX")) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(ItemKeys.manager(), PersistentDataType.BYTE);
    }

    public boolean isResource(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (de.aetherion.items.world.BorderlandsRiteService.isSpirit(stack)
                || de.aetherion.items.world.BorderlandsRiteService.isCryptSpirit(stack)) {
            return false;
        }
        String itemId = itemManager.getItemId(stack);
        if (itemId != null) {
            return CompressedResource.isTradeable(itemId);
        }
        return unitValue(stack) > 0L;
    }

    public boolean isGear(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String itemId = itemManager.getItemId(stack);
        return itemId != null && !CompressedResource.isTradeable(itemId) && unitValue(stack) > 0L;
    }

    public long gearBuyback(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        if (CompressedResource.isTradeable(itemManager.getItemId(stack))) {
            return 0L;
        }
        boolean gear = isGear(stack);
        boolean vanillaGear = itemManager.getItemId(stack) == null && isEquipmentMaterial(stack.getType());
        if (!gear && !vanillaGear) {
            return 0L;
        }
        long value = valueOf(stack);
        if (value <= 0L) {
            return 0L;
        }
        return Math.max(1L, Math.round(value * 0.32d));
    }

    public boolean showsListedValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (de.aetherion.items.util.GuiItems.isGui(stack)) {
            return false;
        }
        String itemId = itemManager.getItemId(stack);
        if (itemId != null) {
            if (CompressedResource.isTradeable(itemId)) {
                return true;
            }
            return isEquipmentId(itemId) || isEquipmentMaterial(stack.getType());
        }
        return isEquipmentMaterial(stack.getType());
    }

    private static boolean isEquipmentId(String itemId) {
        String id = itemId.toLowerCase(Locale.ROOT);
        return id.contains("helmet")
                || id.contains("chestplate")
                || id.contains("leggings")
                || id.contains("boots")
                || id.contains("sword")
                || id.contains("blade")
                || id.contains("pickaxe")
                || id.contains("axe")
                || id.contains("bow")
                || id.contains("crossbow")
                || id.contains("dagger")
                || id.contains("knife")
                || id.contains("maul")
                || id.contains("hammer")
                || id.contains("scythe")
                || id.contains("staff")
                || id.contains("rod")
                || id.contains("hoe")
                || id.contains("charm")
                || id.contains("glaive")
                || id.contains("cleaver")
                || id.contains("wand")
                || id.contains("relic")
                || id.contains("vestige")
                || id.contains("shovel")
                || id.contains("trident")
                || id.contains("void_stick")
                || id.contains("mace")
                || id.contains("catcher")
                || id.contains("booster")
                || id.contains("sack")
                || id.contains("pendant")
                || id.contains("ring")
                || id.contains("crown");
    }

    private static boolean isEquipmentMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || material == Material.BOW
                || material == Material.CROSSBOW
                || material == Material.TRIDENT
                || material == Material.SHIELD
                || material == Material.FISHING_ROD
                || material == Material.MACE
                || material == Material.SHEARS;
    }

    private void load(File file) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection vanillaSection = config.getConfigurationSection("vanilla");
        if (vanillaSection != null) {
            for (String key : vanillaSection.getKeys(false)) {
                try {
                    vanilla.put(Material.valueOf(key.toUpperCase(Locale.ROOT)), vanillaSection.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        mergeItemSection(config.getConfigurationSection("items"));
        mergeItemSection(config.getConfigurationSection("resources"));
        mergeItemSection(config.getConfigurationSection("crafted"));
        mergeItemSection(config.getConfigurationSection("drops"));

        // Safety-net for new CompressedResource entries not yet listed in yml.
        for (CompressedResource resource : CompressedResource.values()) {
            long unit = vanilla.getOrDefault(resource.input(), VanillaValues.of(resource.input()));
            long compressed = EconomyCurve.compressed(unit);
            items.putIfAbsent(resource.compressedId(), compressed);
            long compacted = EconomyCurve.compacted(items.getOrDefault(resource.compressedId(), compressed));
            items.putIfAbsent(resource.compactedId(), compacted);
            if (resource.canRefine()) {
                items.putIfAbsent(resource.refinedId(), EconomyCurve.refined(compacted));
            }
            if (resource.hasQuarry()) {
                items.putIfAbsent(resource.quarryItemId(), EconomyCurve.DEFAULT_QUARRY);
            }
        }
        items.putIfAbsent(QuarryItems.SHARD_ID, EconomyCurve.QUARRY_SHARD);
        items.putIfAbsent(QuarryItems.CORE_ID, EconomyCurve.QUARRY_CORE);
        items.putIfAbsent(QuarryItems.COMPRESSOR_ID, EconomyCurve.QUARRY_COMPRESSOR);
        items.putIfAbsent(QuarryItems.COMPACTOR_ID, EconomyCurve.QUARRY_COMPACTOR);
        for (IsleHeartwood heart : IsleHeartwood.values()) {
            items.putIfAbsent(heart.itemId(), EconomyCurve.DEFAULT_QUARRY);
        }
        // Disabled nether woods stay in enum but skip auto economy noise if desired — still listed.
    }

    private void mergeItemSection(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            items.put(key.toLowerCase(Locale.ROOT), section.getLong(key));
        }
    }

    /** @deprecated use {@link EconomyCurve#compressed(long)} */
    public static long compressedValue(long unitValue) {
        return EconomyCurve.compressed(unitValue);
    }

    /** @deprecated use {@link EconomyCurve#compacted(long)} */
    public static long compactedValue(long compressedValue) {
        return EconomyCurve.compacted(compressedValue);
    }
}
