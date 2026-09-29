package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.dungeon.DungeonArmor;
import de.aetherion.items.dungeon.DungeonCalling;
import de.aetherion.items.dungeon.DungeonGearTier;
import de.aetherion.items.dungeon.DungeonWeaponKind;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.Rarity;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DungeonCore {

    public static final int MAX_TIER = 3;
    public static final String ITEM_ID = "dungeon_core";
    public static final String BOSS_CORE_HINT =
            "§8Dungeon Core: dungeon gear. Then it levels in dungeons.";
    public static final String AETHERION_CORE_HINT =
            "§8Dungeonized. Cores: stronger mini, less CD, higher cap.";
    public static final String ATTUNED_CORE_HINT =
            "§8Anvil + Dungeon Core: rarity up, +25 level cap.";

    private static final Set<String> INFUSABLE = Set.of(
            "warped_blade",
            "gravwell_cleaver",
            "ashen_katana",
            "skuldugery_shortbow",
            "aetherblade",
            "bridged_axe",
            "aetherion_void_stick",
            "hollow_longbow",
            "squids_boot",
            "aetherion_helmet",
            "aetherion_chestplate",
            "aetherion_leggings",
            "aetherion_boots",
            "rotten_cleaver",
            "webweave_fang",
            "bone_knife",
            "obsidian_maul",
            "mender_staff",
            "staff_of_technical_difficulties",
            "void_vacuum_charm",
            "thermal_core",
            "pickaxe_core_of_the_burrower",
            "insolvent_ledger"
    );

    private DungeonCore() {
    }

    public static boolean isCore(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.equals(ITEM_ID) || lower.startsWith("dungeon_core_");
    }

    public static int coreGrade(ItemStack item) {
        if (!isCore(item)) {
            return 0;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.equals("dungeon_core_3")) {
                return 3;
            }
            if (lower.equals("dungeon_core_2")) {
                return 2;
            }
        }
        Integer stored = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.dungeonCoreTier(), PersistentDataType.INTEGER);
        if (stored != null && stored >= 1 && stored <= MAX_TIER) {
            return stored;
        }
        return 1;
    }

    public static String itemIdForGrade(int grade) {
        int safe = Math.max(1, Math.min(MAX_TIER, grade));
        return safe == 1 ? ITEM_ID : ITEM_ID + "_" + safe;
    }

    public static int tier(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer core = meta.getPersistentDataContainer().get(ItemKeys.dungeonCoreTier(), PersistentDataType.INTEGER);
        Integer warped = meta.getPersistentDataContainer().get(ItemKeys.warpedTier(), PersistentDataType.INTEGER);
        int stored = Math.max(
                core == null ? 0 : Math.max(0, core),
                warped == null ? 0 : Math.max(0, warped)
        );
        return Math.min(MAX_TIER, stored);
    }

    public static void writeTier(ItemMeta meta, int tier) {
        meta.getPersistentDataContainer().set(ItemKeys.dungeonCoreTier(), PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(ItemKeys.warpedTier(), PersistentDataType.INTEGER, tier);
    }

    public static boolean canInfuse(String itemId) {
        if (itemId == null) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        // Mining / farming / foraging / fishing skill sets stay out on purpose.
        // Only boss uniques + dungeon calling/weapon gear accept cores.
        if (INFUSABLE.contains(lower)) {
            return true;
        }
        if (DungeonWeaponKind.fromItemId(lower) != null) {
            return true;
        }
        return DungeonCalling.fromItemId(lower) != null;
    }

    public static boolean isAetherionEndgame(String itemId) {
        if (itemId == null) {
            return false;
        }
        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case "aetherion_helmet", "aetherion_chestplate", "aetherion_leggings",
                 "aetherion_boots", "aetherion_void_stick" -> true;
            default -> false;
        };
    }

    public static String coreHint(String itemId) {
        if (isAetherionEndgame(itemId)) {
            return AETHERION_CORE_HINT;
        }
        if (DungeonArmor.isAttuned(itemId)) {
            return ATTUNED_CORE_HINT;
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(itemId);
        if (kind != null && DungeonGearTier.fromItemId(itemId) == DungeonGearTier.T1) {
            return ATTUNED_CORE_HINT;
        }
        return BOSS_CORE_HINT;
    }

    public static String coreHint(ItemStack item, ItemManager items) {
        if (item != null && items != null && DungeonArmor.isNativeDungeonGear(item, items)) {
            if (isAetherionEndgame(items.getItemId(item))) {
                return AETHERION_CORE_HINT;
            }
            return ATTUNED_CORE_HINT;
        }
        return coreHint(items == null ? null : items.getItemId(item));
    }

    public static void ensureHint(List<String> lore, String itemId) {
        if (lore == null || !canInfuse(itemId)) {
            return;
        }
        boolean infused = false;
        List<String> kept = new ArrayList<>();
        for (String line : lore) {
            if (line == null) {
                kept.add(null);
                continue;
            }
            String plain = plainText(line).toLowerCase(Locale.ROOT);
            if (plain.contains("infused") && plain.contains("dungeon core")) {
                infused = true;
                kept.add(line);
                continue;
            }
            if (isReplaceableCoreHint(plain)) {
                continue;
            }
            kept.add(line);
        }
        lore.clear();
        lore.addAll(kept);
        if (infused && !isAetherionEndgame(itemId)) {
            return;
        }
        String hint = coreHint(itemId);
        lore.add(hintInsertIndex(lore), hint);
    }

    private static boolean isReplaceableCoreHint(String plain) {
        if (plain.contains("infused") && plain.contains("dungeon core")) {
            return false;
        }
        if (plain.startsWith("dungeonized")) {
            return true;
        }
        if (plain.contains("anvil +") && plain.contains("core")) {
            return true;
        }
        return plain.contains("dungeon core")
                && (plain.contains("dungeon gear")
                || plain.contains("dungeon-bound")
                || plain.contains("rarity up")
                || plain.contains("level cap")
                || plain.contains("compatible")
                || plain.contains("then it levels"));
    }

    private static int hintInsertIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String plain = lore.get(i) == null ? "" : plainText(lore.get(i)).toLowerCase(Locale.ROOT);
            if (plain.startsWith("dungeon:")
                    || plain.startsWith("lv ")
                    || plain.startsWith("infused:")
                    || plain.startsWith("level cap")
                    || plain.startsWith("rarity bonus")) {
                return i;
            }
        }
        return lore.size();
    }

    public static double rarityStatMultiplier(Rarity rarity) {
        if (rarity == null) {
            return 1.0d;
        }
        return switch (rarity) {
            case EPIC -> 1.05d;
            case LEGENDARY -> 1.10d;
            case MYTHIC, AETHERED -> 1.16d;
            default -> 1.0d;
        };
    }

    public static int rarityStatPercent(Rarity rarity) {
        return (int) Math.round((rarityStatMultiplier(rarity) - 1.0d) * 100.0d);
    }

    public static Rarity rarityForTier(Rarity current, int tier) {
        Rarity floor = Rarity.RARE;
        for (int i = 0; i < Math.max(0, Math.min(MAX_TIER, tier)); i++) {
            floor = floor.next();
        }
        if (current == null) {
            return floor;
        }
        return current.ordinal() >= floor.ordinal() ? current : floor;
    }

    public static void applyInfusionRarity(ItemMeta meta, int tier) {
        if (meta == null || tier <= 0) {
            return;
        }
        String raw = meta.getPersistentDataContainer().get(ItemKeys.rarity(), PersistentDataType.STRING);
        Rarity current = null;
        if (raw != null && !raw.isBlank()) {
            try {
                current = Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Rarity next = rarityForTier(current, tier);
        meta.getPersistentDataContainer().set(ItemKeys.rarity(), PersistentDataType.STRING, next.name());
    }

    public static int wornMaxTier(Player player, ItemManager items) {
        if (player == null || items == null) {
            return 0;
        }
        int max = 0;
        ItemStack[] slots = {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
        for (ItemStack slot : slots) {
            String id = items.getItemId(slot);
            if (canInfuse(id)) {
                max = Math.max(max, tier(slot));
            }
        }
        return max;
    }

    public static int wornAetherionSetTier(Player player, ItemManager items) {
        if (player == null || items == null) {
            return 0;
        }
        int max = 0;
        ItemStack[] slots = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
        for (ItemStack slot : slots) {
            String id = items.getItemId(slot);
            if (id == null) {
                continue;
            }
            switch (id.toLowerCase(Locale.ROOT)) {
                case "aetherion_helmet", "aetherion_chestplate", "aetherion_leggings", "aetherion_boots" ->
                        max = Math.max(max, tier(slot));
                default -> {
                }
            }
        }
        return max;
    }

    public static double dungeonStatMultiplier(int tier) {
        return switch (Math.max(0, Math.min(MAX_TIER, tier))) {
            case 1 -> 1.08d;
            case 2 -> 1.22d;
            case 3 -> 1.40d;
            default -> 1.0d;
        };
    }

    public static int dungeonStatPercent(int tier) {
        return (int) Math.round((dungeonStatMultiplier(tier) - 1.0d) * 100.0d);
    }

    public static double warpedRange(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 5.8;
            case 2 -> 7.2;
            case 3 -> 9.5;
            default -> 5.0;
        };
    }

    public static int warpedCooldownTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 148;
            case 2 -> 112;
            case 3 -> 72;
            default -> 160;
        };
    }

    public static int shortbowIntervalTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 18;
            case 2 -> 14;
            case 3 -> 8;
            default -> 20;
        };
    }

    public static int shortbowVolley(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 2;
            case 2, 3 -> 3;
            default -> 1;
        };
    }

    public static int shortbowPierce(int tier) {
        return clamp(tier) >= 3 ? 4 : 0;
    }

    public static boolean shortbowHitsEndermen(int tier) {
        return clamp(tier) >= 3;
    }

    public static String shortbowVolleyLore(int tier) {
        return switch (clamp(tier)) {
            case 1 -> "§6Volley: §f2 arrows";
            case 2 -> "§6Volley: §f3 arrows";
            case 3 -> "§6Volley: §f3 arrows §8· piercing, hits Endermen";
            default -> "§8Core I: two arrows. Core II: three. Core III: piercing, Endermen.";
        };
    }

    public static int aetherbladeCooldownTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 184;
            case 2 -> 140;
            case 3 -> 88;
            default -> 200;
        };
    }

    public static int aetherbladeTargets(int tier) {
        return switch (clamp(tier)) {
            case 2 -> 6;
            case 3 -> 8;
            default -> 5;
        };
    }

    public static double aetherbladeRange(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 16.5;
            case 2 -> 19.0;
            case 3 -> 24.0;
            default -> 16.0;
        };
    }

    public static int bridgedCooldownTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 276;
            case 2 -> 210;
            case 3 -> 130;
            default -> 300;
        };
    }

    public static double bridgedThrowSpeed(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 1.80;
            case 2 -> 2.05;
            case 3 -> 2.50;
            default -> 1.70;
        };
    }

    public static double skillEffect(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 1.08d;
            case 2 -> 1.25d;
            case 3 -> 1.55d;
            default -> 1.0d;
        };
    }

    public static double voidRange(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 26.0;
            case 2 -> 30.0;
            case 3 -> 36.0;
            default -> 25.0;
        };
    }

    public static long voidIntervalMs(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 165L;
            case 2 -> 130L;
            case 3 -> 90L;
            default -> 180L;
        };
    }

    public static double voidDrain(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 5.4;
            case 2 -> 7.0;
            case 3 -> 11.0;
            default -> 5.0;
        };
    }

    public static double squidRange(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 4.7;
            case 2 -> 5.6;
            case 3 -> 7.2;
            default -> 4.5;
        };
    }

    public static double squidPercent(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 0.011;
            case 2 -> 0.015;
            case 3 -> 0.024;
            default -> 0.01;
        };
    }

    public static double squidCap(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 55.0;
            case 2 -> 70.0;
            case 3 -> 100.0;
            default -> 50.0;
        };
    }

    public static int longbowChargeTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 36;
            case 2 -> 28;
            case 3 -> 16;
            default -> 40;
        };
    }

    public static long aetherionSaveCooldownMs(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 14L * 60L * 1000L;
            case 2 -> 11L * 60L * 1000L;
            case 3 -> 7L * 60L * 1000L;
            default -> 15L * 60L * 1000L;
        };
    }

    public static double aetherionSaveHealth(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 0.38d;
            case 2 -> 0.48d;
            case 3 -> 0.62d;
            default -> 0.35d;
        };
    }

    public static double aetherionSpitDamage(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 8.5;
            case 2 -> 10.5;
            case 3 -> 14.0;
            default -> 8.0;
        };
    }

    public static double aetherionSpitDamage(int tier, boolean boss) {
        double base = aetherionSpitDamage(tier);
        return boss ? base * 2.4 : base;
    }

    public static double aetherionMiniScale(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 0.58d;
            case 2 -> 0.72d;
            case 3 -> 0.92d;
            default -> 0.48d;
        };
    }

    public static int aetherionSpitIntervalTicks(int tier) {
        return switch (clamp(tier)) {
            case 1 -> 24;
            case 2 -> 18;
            case 3 -> 12;
            default -> 30;
        };
    }

    public static void patchLore(ItemMeta meta, String itemId, int tier) {
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        List<String> next = new ArrayList<>();
        boolean infused = false;
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        for (String line : lore) {
            String plain = line.replaceAll("§.", "").toLowerCase(Locale.ROOT);
            if (plain.contains("infused") && plain.contains("dungeon core")) {
                infused = true;
                next.add(infusedLine(tier));
                continue;
            }
            if ((plain.contains("in dungeons:") || plain.startsWith("in dungeons"))
                    && plain.contains("%")
                    && !plain.contains("gear level")
                    && !plain.startsWith("dungeon:")) {
                continue;
            }
            if (id.equals("warped_blade") && plain.contains("right-click to blink")) {
                int seconds = Math.max(1, (warpedCooldownTicks(tier) + 19) / 20);
                next.add("§7Right-click to blink " + strip(warpedRange(tier)) + " blocks");
                next.add("§7forward. §8(" + seconds + "s)");
                continue;
            }
            if (id.equals("warped_blade") && plain.contains("forward.") && plain.contains("(")) {
                continue;
            }
            if (id.contains("shortbow") && plain.contains("fire rate")) {
                next.add("§6⚡ Fire Rate: §f" + String.format(Locale.US, "%.2fs", shortbowIntervalTicks(tier) / 20.0));
                continue;
            }
            if (id.contains("shortbow") && (plain.contains("two arrows")
                    || plain.contains("volley")
                    || plain.contains("piercing")
                    || plain.contains("endermen")
                    || (plain.contains("core i") && plain.contains("arrows")))) {
                next.add(shortbowVolleyLore(tier));
                continue;
            }
            if (id.equals("aetherblade") && plain.contains("blink to the")) {
                next.add("§7Right-click: blink to the " + aetherbladeTargets(tier));
                continue;
            }
            if (id.equals("aetherblade") && plain.contains("nearest enemies")) {
                int seconds = Math.max(1, (aetherbladeCooldownTicks(tier) + 19) / 20);
                next.add("§7nearest enemies. §8(" + seconds + "s)");
                continue;
            }
            if (id.equals("bridged_axe") && plain.contains("life absorption")) {
                next.add("§6❤ Life Absorption: §f"
                        + strip(BossGearBalance.BRIDGED_LIFESTEAL * 100.0 * skillEffect(tier)) + "%");
                continue;
            }
            if (id.equals("bridged_axe") && plain.contains("throw:")) {
                int seconds = Math.max(1, (bridgedCooldownTicks(tier) + 19) / 20);
                next.add("§6⚡ Throw: §f" + seconds + "s");
                continue;
            }
            if (id.equals("aetherion_void_stick") && plain.contains("within")) {
                next.add("§7within " + strip(voidRange(tier)) + " blocks and drain life.");
                continue;
            }
            if (id.equals("squids_boot") && plain.contains("nearby hostiles take")) {
                next.add("§7Nearby hostiles take §f" + strip(squidPercent(tier) * 100.0) + "% §7HP");
                continue;
            }
            if (id.equals("squids_boot") && plain.contains("each second")) {
                next.add("§7each second. §8(max " + strip(squidCap(tier)) + ")");
                continue;
            }
            if (id.equals("hollow_longbow") && plain.contains("draw:")) {
                next.add("§eDraw: §f" + String.format(Locale.US, "%.1fs", longbowChargeTicks(tier) / 20.0)
                        + " §8for a full shot.");
                continue;
            }
            if (id.startsWith("aetherion_") && !id.contains("void") && plain.contains("survive death")) {
                int minutes = (int) Math.max(1L, aetherionSaveCooldownMs(tier) / 60000L);
                next.add("§7Survive death once. §8(" + minutes + "m)");
                continue;
            }
            if (plain.contains("anvil +") && plain.contains("dungeon core") && tier > 0) {
                continue;
            }
            if (plain.contains("can be upgraded later")) {
                continue;
            }
            next.add(line);
        }
        if (!infused && tier > 0) {
            next.add(infusedLine(tier));
        }
        if (id.equals("warped_blade")) {
            boolean hasBlink = false;
            for (String line : next) {
                if (plainText(line).contains("right-click to blink")) {
                    hasBlink = true;
                    break;
                }
            }
            if (!hasBlink) {
                int seconds = Math.max(1, (warpedCooldownTicks(tier) + 19) / 20);
                next.add("§7Right-click to blink " + strip(warpedRange(tier)) + " blocks");
                next.add("§7forward. §8(" + seconds + "s)");
            }
        }
        if (id.contains("shortbow")) {
            boolean hasRate = false;
            for (String line : next) {
                if (plainText(line).contains("fire rate")) {
                    hasRate = true;
                    break;
                }
            }
            if (!hasRate) {
                next.add("§6⚡ Fire Rate: §f" + String.format(Locale.US, "%.2fs", shortbowIntervalTicks(tier) / 20.0));
            }
        }
        if (id.contains("longbow")) {
            boolean hasDraw = false;
            for (String line : next) {
                if (plainText(line).contains("draw:")) {
                    hasDraw = true;
                    break;
                }
            }
            if (!hasDraw) {
                next.add("§eDraw: §f" + String.format(Locale.US, "%.1fs", longbowChargeTicks(tier) / 20.0)
                        + " §8for a full shot.");
            }
        }
        if (tier > 0 && isWeaponId(id)) {
            boolean bound = false;
            for (String line : next) {
                String plain = line.replaceAll("§.", "").toLowerCase(Locale.ROOT);
                if (plain.contains("dungeon-bound") || plain.contains("dungeon weapon")) {
                    bound = true;
                    break;
                }
            }
            if (!bound) {
                next.add("§5Dungeon-bound weapon. Levels in dungeons.");
            }
        }
        if (id.contains("shortbow") && tier > 0) {
            boolean hasVolley = false;
            for (String line : next) {
                String plain = line.replace("§", "").toLowerCase(Locale.ROOT);
                if (plain.contains("volley") || plain.contains("two arrows")
                        || plain.contains("piercing") || plain.contains("endermen")) {
                    hasVolley = true;
                    break;
                }
            }
            if (!hasVolley) {
                next.add(shortbowVolleyLore(tier));
            }
        }
        meta.setLore(next);
    }

    public static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(value);
        };
    }

    private static String infusedLine(int tier) {
        return "§dInfused: Dungeon Core §f" + roman(tier) + "/" + roman(MAX_TIER);
    }

    private static boolean isWeaponId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return id.contains("sword")
                || id.contains("bow")
                || id.contains("blade")
                || id.contains("axe")
                || id.contains("stick")
                || id.contains("wand")
                || id.contains("mace")
                || id.contains("staff")
                || id.contains("glaive")
                || id.contains("cleaver")
                || id.contains("knife")
                || id.contains("dagger")
                || id.contains("maul")
                || id.contains("gaff")
                || id.contains("fang");
    }

    private static int clamp(int tier) {
        return Math.max(0, Math.min(MAX_TIER, tier));
    }

    private static String strip(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String plainText(String line) {
        return line == null ? "" : line.replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }
}
