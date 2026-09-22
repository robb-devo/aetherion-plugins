package de.aetherion.items.blueprint;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.GearTooltip;
import de.aetherion.items.item.ItemPresentation;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Blueprint tool tiers T1–T4 via Upgrade Stones II/III/IV (Eldervale Forgehand only).
 */
public final class BlueprintUpgrade {

    public static final int MAX_TIER = 4;
    /** Wave 3: T4 lands near T5 specialty, not 3.8× T1. */
    public static final int STAT_REV = 3;
    public static final String STONE_PREFIX = "blueprint_upgrade_stone_";

    private static final Set<String> TOOL_IDS = Set.of(
            "vein_siphon",
            "canopy_cleaver",
            "bounty_hoe",
            "wild_sight",
            "tide_latch",
            "resonance_scythe"
    );

    private BlueprintUpgrade() {
    }

    public static boolean isBlueprintTool(String itemId) {
        return itemId != null && TOOL_IDS.contains(itemId.toLowerCase(Locale.ROOT));
    }

    public static boolean isStone(ItemStack item) {
        return stoneTargetTier(item) > 0;
    }

    /** Stone II → target tier 2, III → 3, IV → 4. */
    public static int stoneTargetTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.blueprintUpgradeStone(),
                PersistentDataType.INTEGER
        );
        if (stored != null && stored >= 2 && stored <= MAX_TIER) {
            return stored;
        }
        return 0;
    }

    public static int tier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(
                ItemKeys.blueprintTier(),
                PersistentDataType.INTEGER
        );
        if (tier != null && tier >= 1) {
            return Math.min(MAX_TIER, tier);
        }
        // Legacy Vein Siphon stub.
        Integer siphon = meta.getPersistentDataContainer().get(
                ItemKeys.siphonTier(),
                PersistentDataType.INTEGER
        );
        if (siphon != null && siphon >= 1) {
            return Math.min(MAX_TIER, siphon);
        }
        return 1;
    }

    public static void writeTier(ItemMeta meta, int tier) {
        if (meta == null) {
            return;
        }
        int use = Math.max(1, Math.min(MAX_TIER, tier));
        meta.getPersistentDataContainer().set(ItemKeys.blueprintTier(), PersistentDataType.INTEGER, use);
        meta.getPersistentDataContainer().set(ItemKeys.siphonTier(), PersistentDataType.INTEGER, use);
        stampRev(meta);
    }

    public static ItemStack createStone(int targetTier) {
        int use = Math.max(2, Math.min(MAX_TIER, targetTier));
        de.aetherion.items.model.Rarity rarity = rarityForStone(use);
        Material mat = switch (use) {
            case 2 -> Material.AMETHYST_SHARD;
            case 3 -> Material.GOLD_NUGGET;
            default -> Material.NETHERITE_SCRAP;
        };
        String color = String.valueOf(rarity.getChatColor());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + "§lUpgrade Stone " + roman(use));
            meta.setLore(List.of(
                    "§7✦ " + color + rarity.name(),
                    "",
                    "§7Apply on a §bBlueprint tool §7at the",
                    "§6Eldervale Forgehand§7.",
                    "§7Raises tool to §fTier " + roman(use) + "§7.",
                    "",
                    "§8Requires current Tier " + roman(use - 1)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    ItemKeys.item(),
                    PersistentDataType.STRING,
                    STONE_PREFIX + use
            );
            meta.getPersistentDataContainer().set(
                    ItemKeys.blueprintUpgradeStone(),
                    PersistentDataType.INTEGER,
                    use
            );
            meta.getPersistentDataContainer().set(
                    ItemKeys.rarity(),
                    PersistentDataType.STRING,
                    rarity.name()
            );
            meta.setCustomModelData(3200 + use);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static de.aetherion.items.model.Rarity rarityForStone(int targetTier) {
        return switch (Math.max(2, Math.min(MAX_TIER, targetTier))) {
            case 2 -> de.aetherion.items.model.Rarity.EPIC;
            case 3 -> de.aetherion.items.model.Rarity.LEGENDARY;
            default -> de.aetherion.items.model.Rarity.MYTHIC;
        };
    }

    public static boolean canApply(ItemManager items, ItemStack tool, ItemStack stone) {
        if (items == null || tool == null || stone == null) {
            return false;
        }
        if (!isBlueprintTool(items.getItemId(tool))) {
            return false;
        }
        int target = stoneTargetTier(stone);
        if (target < 2) {
            return false;
        }
        return tier(tool) + 1 == target;
    }

    public static ItemStack apply(ItemManager items, ItemStack tool, ItemStack stone) {
        if (!canApply(items, tool, stone)) {
            return null;
        }
        int next = stoneTargetTier(stone);
        ItemStack result = tool.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }
        ItemStats stats = items.getItemStats(result);
        String itemId = items.getItemId(result);
        int from = tier(tool);
        rebaseToTier(stats, itemId, from, next);
        items.saveItemStats(meta, stats);
        writeTier(meta, next);
        stampRev(meta);
        patchLore(meta, itemId, next, stats, items.getProfile(result));
        result.setItemMeta(meta);
        ItemMeta polished = result.getItemMeta();
        if (polished != null) {
            ItemPresentation.polish(polished);
            result.setItemMeta(polished);
        }
        GearTooltip.finish(result, items, true);
        de.aetherion.items.model.BoosterSockets.copyOnto(items, tool, result);
        return result;
    }

    /** Ability / vacuum scaling for Vein Siphon. */
    public static int siphonRadius(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 12;
            case 3 -> 15;
            case 4 -> 20;
            default -> 10;
        };
    }

    public static int siphonMaxOres(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 22;
            case 3 -> 32;
            case 4 -> 50;
            default -> 15;
        };
    }

    public static long siphonCooldownTicks(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 20L * 38;
            case 3 -> 20L * 30;
            case 4 -> 20L * 20;
            default -> 20L * 45;
        };
    }

    /** Canopy Cleaver Perfect Fell cooldown (ms). */
    public static long canopyCleaverCooldownMs(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 16_000L;
            case 3 -> 12_000L;
            case 4 -> 8_000L;
            default -> 20_000L;
        };
    }

    /** Resonance Scythe sonic wave cooldown (ticks). */
    public static long resonanceCooldownTicks(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 20L * 4;
            case 3 -> 20L * 3;
            case 4 -> 20L * 2;
            default -> 20L * 5;
        };
    }

    /** Incremental multiply when moving into this tier. Wave 3: cum ×2.06 to T4. */
    public static double stepMultiplier(int targetTier) {
        return switch (clamp(targetTier)) {
            case 2 -> 1.22;
            case 3 -> 1.28;
            case 4 -> 1.32;
            default -> 1.0;
        };
    }

    public static double cumulativeMultiplier(int tier) {
        double mul = 1.0;
        int use = clamp(tier);
        for (int t = 2; t <= use; t++) {
            mul *= stepMultiplier(t);
        }
        return mul;
    }

    /**
     * Live items: rebuild the scaled fields from T1 × cumulative, keep booster extras.
     */
    public static boolean migrate(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null || meta == null || !isBlueprintTool(itemId)) {
            return false;
        }
        Integer revision = meta.getPersistentDataContainer().get(
                ItemKeys.blueprintStatRev(),
                PersistentDataType.INTEGER
        );
        if (revision != null && revision >= STAT_REV) {
            return false;
        }
        int current = 1;
        Integer stored = meta.getPersistentDataContainer().get(
                ItemKeys.blueprintTier(),
                PersistentDataType.INTEGER
        );
        if (stored != null && stored >= 1) {
            current = Math.min(MAX_TIER, stored);
        } else {
            Integer siphon = meta.getPersistentDataContainer().get(
                    ItemKeys.siphonTier(),
                    PersistentDataType.INTEGER
            );
            if (siphon != null && siphon >= 1) {
                current = Math.min(MAX_TIER, siphon);
            }
        }
        int fromRev = revision == null ? 0 : revision;
        ItemStats oldBase = scaledBase(itemId, current, fromRev < STAT_REV);
        ItemStats newBase = scaledBase(itemId, current, false);
        if (oldBase == null || newBase == null) {
            return false;
        }
        rebaseScaled(stats, oldBase, newBase);
        stampRev(meta);
        return true;
    }

    public static ItemStats tier1Base(String itemId) {
        return tier1Base(itemId, false);
    }

    /** {@code legacy=true} is the pre-Wave-3 T1 snapshot used to peel extras off live items. */
    public static ItemStats tier1Base(String itemId, boolean legacy) {
        if (itemId == null) {
            return null;
        }
        ItemStats stats = new ItemStats();
        String id = itemId.toLowerCase(Locale.ROOT);
        if (legacy) {
            switch (id) {
                case "vein_siphon" -> {
                    stats.setMiningPower(40);
                    stats.setFortune(65);
                    stats.setSpread(5);
                }
                case "canopy_cleaver" -> {
                    stats.setFortune(55);
                    stats.setSpread(8);
                }
                case "bounty_hoe" -> {
                    stats.setFortune(40);
                    stats.setHarvestSpread(140);
                }
                case "wild_sight" -> stats.setCatchRate(14);
                case "tide_latch" -> {
                    stats.setFortune(40);
                    stats.setFishingSpeed(35);
                    stats.setFishingCatch(45);
                }
                case "resonance_scythe" -> {
                    stats.setDamage(96);
                    stats.setAttackSpread(7);
                    stats.setCritChance(16);
                    stats.setCritDamage(118);
                }
                default -> {
                    return null;
                }
            }
            return stats;
        }
        switch (id) {
            case "vein_siphon" -> {
                stats.setMiningPower(48);
                stats.setFortune(96);
                stats.setSpread(5);
            }
            case "canopy_cleaver" -> {
                stats.setFortune(72);
                stats.setSpread(6);
            }
            case "bounty_hoe" -> {
                stats.setFortune(80);
                stats.setHarvestSpread(100);
            }
            case "wild_sight" -> stats.setCatchRate(8);
            case "tide_latch" -> {
                stats.setFortune(72);
                stats.setFishingSpeed(14);
                stats.setFishingCatch(120);
            }
            case "resonance_scythe" -> {
                stats.setDamage(36);
                stats.setAttackSpread(6);
                stats.setCritChance(8);
                stats.setCritDamage(50);
            }
            default -> {
                return null;
            }
        }
        return stats;
    }

    private static double legacyCumulative(int tier) {
        double mul = 1.0;
        int use = clamp(tier);
        double[] steps = {1.0, 1.40, 1.55, 1.75};
        for (int t = 2; t <= use; t++) {
            mul *= steps[t - 1];
        }
        return mul;
    }

    private static ItemStats scaledBase(String itemId, int tier, boolean legacy) {
        ItemStats base = tier1Base(itemId, legacy);
        if (base == null) {
            return null;
        }
        double mul = legacy ? legacyCumulative(tier) : cumulativeMultiplier(tier);
        if (mul != 1.0) {
            scaleStats(base, mul);
        }
        return base;
    }

    private static void rebaseToTier(ItemStats actual, String itemId, int fromTier, int toTier) {
        ItemStats oldBase = scaledBase(itemId, fromTier, false);
        ItemStats newBase = scaledBase(itemId, toTier, false);
        if (oldBase == null || newBase == null) {
            return;
        }
        rebaseScaled(actual, oldBase, newBase);
    }

    private static void rebaseScaled(ItemStats actual, ItemStats oldBase, ItemStats newBase) {
        actual.setMiningPower(newBase.getMiningPower() + extra(actual.getMiningPower(), oldBase.getMiningPower()));
        actual.setFortune(newBase.getFortune() + extra(actual.getFortune(), oldBase.getFortune()));
        actual.setSpread(newBase.getSpread() + extra(actual.getSpread(), oldBase.getSpread()));
        actual.setHarvestSpread(newBase.getHarvestSpread() + extra(actual.getHarvestSpread(), oldBase.getHarvestSpread()));
        actual.setDamage(newBase.getDamage() + extra(actual.getDamage(), oldBase.getDamage()));
        actual.setCritChance(newBase.getCritChance() + extra(actual.getCritChance(), oldBase.getCritChance()));
        actual.setCritDamage(newBase.getCritDamage() + extra(actual.getCritDamage(), oldBase.getCritDamage()));
        actual.setAttackSpread(newBase.getAttackSpread() + extra(actual.getAttackSpread(), oldBase.getAttackSpread()));
        actual.setFishingSpeed(newBase.getFishingSpeed() + extra(actual.getFishingSpeed(), oldBase.getFishingSpeed()));
        actual.setFishingCatch(newBase.getFishingCatch() + extra(actual.getFishingCatch(), oldBase.getFishingCatch()));
        actual.setCatchRate(newBase.getCatchRate() + extra(actual.getCatchRate(), oldBase.getCatchRate()));
    }

    private static double extra(double actual, double oldBase) {
        return Math.max(0.0, actual - oldBase);
    }

    public static void stampRev(ItemMeta meta) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.blueprintStatRev(), PersistentDataType.INTEGER, STAT_REV);
        }
    }

    private static void scaleStats(ItemStats stats, double mul) {
        if (stats == null || mul == 1.0) {
            return;
        }
        stats.setMiningPower(stats.getMiningPower() * mul);
        stats.setFortune(stats.getFortune() * mul);
        stats.setSpread(stats.getSpread() * mul);
        stats.setHarvestSpread(stats.getHarvestSpread() * mul);
        stats.setDamage(stats.getDamage() * mul);
        stats.setCritChance(stats.getCritChance() * mul);
        stats.setCritDamage(stats.getCritDamage() * mul);
        stats.setAttackSpread(stats.getAttackSpread() * mul);
        stats.setFishingSpeed(stats.getFishingSpeed() * mul);
        stats.setFishingCatch(stats.getFishingCatch() * mul);
        stats.setCatchRate(stats.getCatchRate() * mul);
    }

    public static void patchLore(ItemMeta meta, String itemId, int tier, ItemStats stats, ItemProfile profile) {
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> {
            String plain = line == null ? "" : line.replaceAll("§.", "");
            return plain.contains("Blueprint Tier")
                    || plain.startsWith("Does not level");
        });
        // Refresh common displayed number lines when present.
        if (stats != null && profile != null) {
            de.aetherion.items.item.ItemLore.updateDisplayedStats(lore, stats, profile);
        }
        String tierLine = "§bBlueprint Tier: §f" + roman(tier) + "/" + roman(MAX_TIER);
        int insert = 0;
        for (int i = 0; i < lore.size(); i++) {
            String plain = lore.get(i) == null ? "" : lore.get(i).replaceAll("§.", "");
            if (plain.contains("RARE") || plain.contains("EPIC") || plain.contains("LEGENDARY")
                    || plain.contains("MYTHIC") || plain.contains("COMMON") || plain.contains("UNCOMMON")) {
                insert = i + 1;
                break;
            }
        }
        lore.add(insert, tierLine);
        if ("vein_siphon".equalsIgnoreCase(itemId)) {
            lore.removeIf(line -> {
                String plain = line == null ? "" : line.replaceAll("§.", "");
                return plain.contains("pull ores in")
                        || plain.startsWith("Cooldown:");
            });
            int ability = indexOfAbility(lore);
            lore.add(ability, "§7Right-click: pull ores in §f" + siphonRadius(tier) + "§7 blocks");
            lore.add(ability + 1, "§7Cooldown: §f" + (siphonCooldownTicks(tier) / 20) + "s §8· max "
                    + siphonMaxOres(tier));
        } else if ("canopy_cleaver".equalsIgnoreCase(itemId)) {
            lore.removeIf(line -> {
                String plain = line == null ? "" : line.replaceAll("§.", "");
                return plain.startsWith("Cooldown:");
            });
            int ability = indexOfAbility(lore);
            lore.add(Math.min(lore.size(), ability + 2),
                    "§7Cooldown: §f" + (canopyCleaverCooldownMs(tier) / 1000L) + "s");
        } else if ("resonance_scythe".equalsIgnoreCase(itemId)) {
            lore.removeIf(line -> {
                String plain = line == null ? "" : line.replaceAll("§.", "");
                return plain.startsWith("Cooldown:");
            });
            int ability = indexOfAbility(lore);
            lore.add(Math.min(lore.size(), ability + 2),
                    "§7Cooldown: §f" + (resonanceCooldownTicks(tier) / 20) + "s");
        }
        meta.setLore(lore);
    }

    private static int indexOfAbility(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String plain = lore.get(i) == null ? "" : lore.get(i).replaceAll("§.", "");
            if (plain.contains("Ability")) {
                return i + 1;
            }
        }
        return Math.min(lore.size(), 6);
    }

    public static String roman(int tier) {
        return switch (clamp(tier)) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(tier);
        };
    }

    private static int clamp(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }
}
