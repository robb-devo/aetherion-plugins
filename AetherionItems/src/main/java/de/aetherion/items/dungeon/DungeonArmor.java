package de.aetherion.items.dungeon;

import de.aetherion.items.item.ItemLore;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class DungeonArmor {

    public static final Color VESTIGE_COLOR = Color.fromRGB(108, 110, 118);
    /** Native dungeon gear only: weak outside dungeons. In-dungeon power is cores + gear level. */
    public static final double OVERWORLD_STAT_MULTIPLIER = 0.38d;

    private DungeonArmor() {
    }

    public static boolean inDungeon(Player player) {
        return player != null && inDungeon(player.getWorld());
    }

    public static boolean inDungeon(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }

    public static boolean isVestige(String itemId) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("dungeon_vestige_");
    }

    public static boolean isVestige(ItemStack item, ItemManager items) {
        return items != null && isVestige(items.getItemId(item));
    }

    public static boolean isAttuned(String itemId) {
        if (DungeonCalling.fromItemId(itemId) == null) {
            return false;
        }
        DungeonGearTier tier = DungeonGearTier.fromItemId(itemId);
        return tier == null || tier == DungeonGearTier.T1;
    }

    public static boolean isAttuned(ItemStack item, ItemManager items) {
        return items != null && isAttuned(items.getItemId(item));
    }

    public static boolean isNativeDungeonGear(ItemStack item, ItemManager items) {
        if (item == null || items == null) {
            return false;
        }
        String id = items.getItemId(item);
        if (isAttuned(id)) {
            return true;
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(id);
        if (kind != null && DungeonGearTier.fromItemId(id) == DungeonGearTier.T1) {
            return true;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(
                de.aetherion.items.core.ItemKeys.dungeonNative(),
                org.bukkit.persistence.PersistentDataType.BYTE
        );
        return flag != null && flag == 1;
    }

    public static boolean consumeAndReplace(
            Player player,
            EquipmentSlot preferredHand,
            DungeonPiece armorPiece,
            Predicate<ItemStack> match,
            ItemStack replacement
    ) {
        if (player == null || match == null || replacement == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int preferred = preferredHand == EquipmentSlot.OFF_HAND ? 40 : inventory.getHeldItemSlot();
        boolean consumed = takeFromSlot(player, preferred, match, replacement)
                || (preferred != 40 && takeFromSlot(player, 40, match, replacement))
                || takeFromWorn(player, armorPiece, match, replacement);
        if (!consumed) {
            for (DungeonPiece piece : DungeonPiece.values()) {
                if (piece != armorPiece && takeFromWorn(player, piece, match, replacement)) {
                    consumed = true;
                    break;
                }
            }
        }
        if (!consumed) {
            for (int slot = 0; slot < 36; slot++) {
                if (slot == preferred) {
                    continue;
                }
                if (takeFromSlot(player, slot, match, replacement)) {
                    consumed = true;
                    break;
                }
            }
        }
        if (consumed) {
            player.updateInventory();
        }
        return consumed;
    }

    private static boolean takeFromSlot(
            Player player,
            int slot,
            Predicate<ItemStack> match,
            ItemStack replacement
    ) {
        PlayerInventory inventory = player.getInventory();
        return replaceStack(
                player,
                inventory.getItem(slot),
                match,
                replacement,
                remaining -> inventory.setItem(slot, remaining)
        );
    }

    private static boolean takeFromWorn(
            Player player,
            DungeonPiece piece,
            Predicate<ItemStack> match,
            ItemStack replacement
    ) {
        if (piece == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        return replaceStack(
                player,
                piece.worn(inventory),
                match,
                replacement,
                remaining -> piece.wear(inventory, remaining)
        );
    }

    private static boolean replaceStack(
            Player player,
            ItemStack current,
            Predicate<ItemStack> match,
            ItemStack replacement,
            java.util.function.Consumer<ItemStack> write
    ) {
        if (current == null || current.getType().isAir() || !match.test(current)) {
            return false;
        }
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
            write.accept(current);
            giveOrDrop(player, replacement);
            return true;
        }
        write.accept(replacement);
        return true;
    }

    private static void stripWornIf(PlayerInventory inventory, DungeonPiece piece, Predicate<ItemStack> match) {
        if (inventory == null || match == null) {
            return;
        }
        if (piece == null) {
            for (DungeonPiece each : DungeonPiece.values()) {
                stripWornIf(inventory, each, match);
            }
            return;
        }
        ItemStack worn = piece.worn(inventory);
        if (worn != null && !worn.getType().isAir() && match.test(worn)) {
            piece.wear(inventory, null);
        }
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    public static int wornPieces(Player player, ItemManager items, DungeonCalling calling) {
        if (player == null || items == null || calling == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        if (matches(items.getItemId(inventory.getHelmet()), calling)) {
            count++;
        }
        if (matches(items.getItemId(inventory.getChestplate()), calling)) {
            count++;
        }
        if (matches(items.getItemId(inventory.getLeggings()), calling)) {
            count++;
        }
        if (matches(items.getItemId(inventory.getBoots()), calling)) {
            count++;
        }
        return count;
    }

    public static boolean fullSet(Player player, ItemManager items, DungeonCalling calling) {
        return wornPieces(player, items, calling) >= 4;
    }

    public static ItemStats vestigeStats(DungeonPiece piece) {
        ItemStats stats = new ItemStats();
        switch (piece) {
            case HELMET -> {
                stats.setDefense(3);
                stats.setHealth(4);
            }
            case CHESTPLATE -> {
                stats.setDefense(4);
                stats.setHealth(7);
            }
            case LEGGINGS -> {
                stats.setDefense(3);
                stats.setHealth(5);
            }
            case BOOTS -> {
                stats.setDefense(3);
                stats.setHealth(4);
            }
        }
        return stats;
    }

    public static List<String> vestigeLore(DungeonPiece piece, ItemStats stats) {
        List<String> lore = new ArrayList<>();
        lore.add("§7✦ §bRARE");
        lore.add("");
        addStatLines("dungeon_vestige_" + piece.id(), stats, lore);
        lore.add("");
        lore.add("§7Dungeon Vestige " + piece.display());
        lore.add("§7Blank on purpose. The dungeon sells");
        lore.add("§7identities, not fashion.");
        lore.add("");
        lore.add("§eRight-click §7to choose a calling.");
        lore.add("§8This vestige is consumed. You keep the chosen piece.");
        lore.add("§8Tank · Assassin · Soldier · Healer · Shaman");
        return lore;
    }

    public static List<String> attunedLore(DungeonCalling calling, DungeonPiece piece, ItemStats stats) {
        return attunedLore(calling, piece, stats, DungeonGearTier.T1);
    }

    public static List<String> attunedLore(
            DungeonCalling calling,
            DungeonPiece piece,
            ItemStats stats,
            DungeonGearTier tier
    ) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        List<String> lore = new ArrayList<>();
        lore.add(safe.rarityLine());
        lore.add("");
        addStatLines(calling.itemId(safe, piece), stats, lore);
        lore.add("");
        lore.add(calling.color()
                + (safe == DungeonGearTier.T1 ? "Dungeon Calling: " : "Calling: ")
                + calling.display()
                + (safe == DungeonGearTier.T1 ? "" : " §8· " + safe.roman()));
        lore.add("§7" + calling.flavor());
        lore.add("");
        lore.add("§8Likes: §7" + calling.boosters());
        if (safe == DungeonGearTier.T1) {
            lore.add("§8Outside dungeons: §7this set is polite about it.");
            lore.add(de.aetherion.items.item.DungeonCore.ATTUNED_CORE_HINT);
        } else {
            lore.add("§8Overworld boss piece. Not dungeon gear yet.");
            lore.add(de.aetherion.items.item.DungeonCore.BOSS_CORE_HINT);
        }
        if (calling == DungeonCalling.ASSASSIN) {
            lore.add("");
            lore.addAll(assassinLore(null, null));
        }
        if (safe == DungeonGearTier.T1) {
            lore.add("");
            lore.addAll(DungeonGearProgress.previewLines(0, 0, 1.0d));
        }
        return lore;
    }

    public static List<String> weaponLore(DungeonWeaponKind kind, DungeonGearTier tier, ItemStats stats) {
        return weaponLore(kind, tier, stats, tier == null || tier == DungeonGearTier.T1);
    }

    public static List<String> weaponLore(
            DungeonWeaponKind kind,
            DungeonGearTier tier,
            ItemStats stats,
            boolean dungeonGear
    ) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        List<String> lore = new ArrayList<>();
        lore.add(safe.rarityLine());
        lore.add("");
        addStatLines(kind.itemId(safe), stats, lore);
        lore.add("");
        lore.add("§7" + kind.flavor());
        lore.add(dungeonGear
                ? "§8Dungeon weapon. Levels in dungeons. Cores raise the cap."
                : "§8Boss weapon. Same rules as the others.");
        if (kind == DungeonWeaponKind.BOW) {
            lore.add("§7Hold right-click to fire. Needs arrows.");
        }
        if (kind == DungeonWeaponKind.WAND) {
            lore.add(safe == DungeonGearTier.T3
                    ? "§6Right-click: §7ember burst. Chains once."
                    : "§6Right-click: §7bolt where you look.");
        }
        if (kind == DungeonWeaponKind.STAFF) {
            lore.add("§6Right-click: §7heal nearby players.");
        }
        if (kind == DungeonWeaponKind.MACE) {
            lore.add("§8Slow. Heavy. The hallway notices.");
        }
        lore.add(dungeonGear
                ? de.aetherion.items.item.DungeonCore.ATTUNED_CORE_HINT
                : de.aetherion.items.item.DungeonCore.BOSS_CORE_HINT);
        if (dungeonGear) {
            lore.add("");
            lore.addAll(DungeonGearProgress.previewLines(0, 0, 1.0d));
        }
        return lore;
    }

    public static int assassinShotInterval(Player player, ItemManager items, int base) {
        return Math.max(5, (int) Math.round(base * assassinIntervalFactor(player, items)));
    }

    public static double assassinIntervalFactor(Player player, ItemManager items) {
        int pieces = wornPieces(player, items, DungeonCalling.ASSASSIN);
        if (pieces <= 0) {
            return 1.0d;
        }
        double factor = 1.0d - 0.08d * pieces;
        if (inDungeon(player)) {
            double gear = DungeonGearProgress.wornLevelMultiplier(player, items);
            factor -= 0.12d * gear;
            if (fullSet(player, items, DungeonCalling.ASSASSIN)) {
                factor -= 0.06d * gear;
            }
        }
        return Math.max(0.25d, factor);
    }

    public static double assassinCooldownFactor(Player player, ItemManager items) {
        int pieces = wornPieces(player, items, DungeonCalling.ASSASSIN);
        if (pieces <= 0) {
            return 1.0d;
        }
        double cut = 0.05d * pieces;
        if (inDungeon(player)) {
            cut += 0.08d * DungeonGearProgress.wornLevelMultiplier(player, items);
        }
        return Math.max(0.58d, 1.0d - cut);
    }

    public static List<String> assassinLore(Player player, ItemManager items) {
        List<String> lore = new ArrayList<>();
        lore.add("§6Shade · Bow");
        lore.add("§7Draw / fire interval:");
        lore.add("§8  1 pc   §f-8%");
        lore.add("§8  2 pcs  §f-16%");
        lore.add("§8  3 pcs  §f-24%");
        lore.add("§8  4 pcs  §f-32%");
        lore.add("§8Dungeon extra: §f-12% §7× gear level");
        lore.add("§8Full set extra: §f-6% §7× gear level");
        lore.add("§7Ability cooldown:");
        lore.add("§8  1 / 2 / 3 / 4 pcs  §f-5% / -10% / -15% / -20%");
        lore.add("§8Dungeon extra: §f-8% §7× gear level §8(floor -42%)");
        lore.add("§7Full set:");
        lore.add("§8  Overworld  §f+5 dmg  +4% crit");
        lore.add("§8  Dungeon    §f+14 dmg  +10% crit  +18% crit dmg  §7× gear");
        int pieces = wornPieces(player, items, DungeonCalling.ASSASSIN);
        if (pieces > 0) {
            int fireCut = (int) Math.round((1.0d - assassinIntervalFactor(player, items)) * 100.0d);
            int cdCut = (int) Math.round((1.0d - assassinCooldownFactor(player, items)) * 100.0d);
            lore.add("§aNow: §f" + pieces + " pc  fire §a-" + fireCut + "%  §7cd §a-" + cdCut + "%");
        }
        return lore;
    }

    public static void refreshAssassinLore(ItemStack item, Player player, ItemManager items) {
        if (item == null || items == null || DungeonCalling.fromItemId(items.getItemId(item)) != DungeonCalling.ASSASSIN) {
            return;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        List<String> next = new ArrayList<>();
        boolean wrote = false;
        for (String line : lore) {
            if (isAssassinLoreLine(line)) {
                if (!wrote) {
                    next.addAll(assassinLore(player, items));
                    wrote = true;
                }
                continue;
            }
            next.add(line);
        }
        if (!wrote) {
            int insert = indexAfterBoosters(next);
            next.addAll(insert, assassinLore(player, items));
            next.add(insert, "");
        }
        meta.setLore(next);
        item.setItemMeta(meta);
    }

    private static int indexAfterBoosters(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String plain = lore.get(i) == null ? "" : lore.get(i).replaceAll("§.", "").toLowerCase(Locale.ROOT);
            if (plain.contains("boosters:") || plain.startsWith("likes:")) {
                return Math.min(lore.size(), i + 1);
            }
        }
        return lore.size();
    }

    private static boolean isAssassinLoreLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = line.replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();
        if (plain.isEmpty()) {
            return false;
        }
        if (plain.startsWith("shade") && (plain.contains("bow") || plain.contains("·"))) {
            return true;
        }
        if (plain.startsWith("now:") && (plain.contains("fire") || plain.contains("cd"))) {
            return true;
        }
        if (plain.contains("draw / fire") || plain.contains("draw/fire")) {
            return true;
        }
        if (plain.contains("ability cooldown")) {
            return true;
        }
        if (plain.contains("dungeon extra:") || plain.contains("full set extra:")) {
            return true;
        }
        if (plain.contains("1 / 2 / 3 / 4 pcs")) {
            return true;
        }
        if (plain.contains("overworld") && plain.contains("dmg")) {
            return true;
        }
        if (plain.contains("dungeon") && plain.contains("crit dmg")) {
            return true;
        }
        if (plain.contains("bow fire rate") || plain.contains("full set in dungeons")) {
            return true;
        }
        return plain.startsWith("1 pc") || plain.startsWith("2 pcs") || plain.startsWith("3 pcs")
                || plain.startsWith("4 pcs") || plain.startsWith("full set:");
    }

    private static boolean matches(String itemId, DungeonCalling calling) {
        return calling != null && calling.equals(DungeonCalling.fromItemId(itemId));
    }

    public static void addRelicStatLines(String itemId, ItemStats stats, List<String> lore) {
        addStatLines(itemId, stats, lore);
    }

    /**
     * Effective combat stats while inside a dungeon (same multipliers as ActiveEquipmentStats).
     */
    public static boolean showsDungeonStatPreview(ItemStack item, ItemManager items) {
        if (item == null || items == null || !items.isAetherionItem(item)) {
            return false;
        }
        String id = items.getItemId(item);
        if (isVestige(id)) {
            return false;
        }
        return isNativeDungeonGear(item, items)
                || de.aetherion.items.item.DungeonCore.tier(item) > 0
                || de.aetherion.items.item.DungeonCore.isAetherionEndgame(id);
    }

    /**
     * Hypixel-style: append dungeon values inline on each stat line, e.g. {@code Damage: +10 §d(+32)}.
     * Only touches dungeon / dungeonized items ({@link #showsDungeonStatPreview}).
     */
    public static void applyDungeonStatPreview(List<String> lore, ItemStack item, ItemManager items) {
        stripDungeonStatPreview(lore);
        if (lore == null || !showsDungeonStatPreview(item, items)) {
            return;
        }
        ItemStats stats = items.getItemStats(item);
        ItemProfile profile = items.getProfile(item);
        if (stats == null || profile == null) {
            return;
        }
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String clean = stripInlineDungeonSuffix(line);
            String suffix = inlineDungeonSuffix(clean, item, items, stats, profile);
            if (suffix != null) {
                lore.set(i, clean + suffix);
            } else {
                lore.set(i, clean);
            }
        }
    }

    /**
     * Removes legacy standalone {@code (+X Label)} lines and any existing inline dungeon suffixes.
     */
    public static void stripDungeonStatPreview(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return;
        }
        lore.removeIf(DungeonArmor::isStandaloneDungeonStatPreview);
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line != null) {
                lore.set(i, stripInlineDungeonSuffix(line));
            }
        }
        for (int i = lore.size() - 2; i >= 0; i--) {
            if (lore.get(i) != null && lore.get(i).isBlank()
                    && i + 1 < lore.size()
                    && lore.get(i + 1) != null && lore.get(i + 1).isBlank()) {
                lore.remove(i);
            }
        }
    }

    public static boolean isStandaloneDungeonStatPreview(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        return plain.startsWith("(+") && plain.endsWith(")");
    }

    public static double previewDungeonValue(ItemStack item, ItemManager items, double base) {
        if (item == null || items == null || base <= 0.0) {
            return 0.0;
        }
        double value = base;
        String itemId = items.getItemId(item);
        if (de.aetherion.items.item.DungeonCore.canInfuse(itemId)) {
            value *= de.aetherion.items.item.DungeonCore.rarityStatMultiplier(items.getRarity(item));
        }
        value *= DungeonGearProgress.dungeonBonusMultiplier(
                item,
                items,
                DungeonGearProgress.level(item)
        );
        return Math.round(value * 10.0d) / 10.0d;
    }

    /** Light purple — readable dungeon accent, not gray. */
    private static final String DUNGEON_PREVIEW_COLOR = "§d";

    /** Matches one or many trailing dungeon preview suffixes (§x(+N) / §x(+N%)), dot or comma decimals. */
    private static final Pattern INLINE_DUNGEON_SUFFIX = Pattern.compile(
            "(?:\\s|§[0-9a-fk-or])*§[0-9a-fk-or]\\(\\+[0-9]+(?:[.,][0-9]+)?%?\\)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static String stripInlineDungeonSuffix(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        String current = line;
        String next;
        do {
            next = INLINE_DUNGEON_SUFFIX.matcher(current).replaceFirst("").stripTrailing();
            if (next.equals(current)) {
                break;
            }
            current = next;
        } while (true);
        return current;
    }

    private static String inlineDungeonSuffix(
            String line,
            ItemStack item,
            ItemManager items,
            ItemStats stats,
            ItemProfile profile
    ) {
        if (line.contains("Crit Damage:")) {
            return suffixFor(profile, ItemCapability.CRIT_DAMAGE, previewDungeonValue(item, items, stats.getCritDamage()), true);
        }
        if (line.contains("Crit Chance:")) {
            return suffixFor(profile, ItemCapability.CRIT_CHANCE, previewDungeonValue(item, items, stats.getCritChance()), true);
        }
        if (line.contains("Undead Resist:")) {
            return suffixFor(profile, ItemCapability.UNDEAD_RESIST, previewDungeonValue(item, items, stats.getUndeadResist()), true);
        }
        if (line.contains("Attack Spread:")) {
            return suffixFor(profile, ItemCapability.ATTACK_SPREAD, previewDungeonValue(item, items, stats.getAttackSpread()), false);
        }
        if (line.contains("Damage:") && !line.contains("Attack") && !line.contains("Undead")) {
            return suffixFor(profile, ItemCapability.DAMAGE, previewDungeonValue(item, items, stats.getDamage()), false);
        }
        if (line.contains("Defense:")) {
            return suffixFor(profile, ItemCapability.DEFENSE, previewDungeonValue(item, items, stats.getDefense()), false);
        }
        if (line.contains("Health:") && !line.contains("Lapis")) {
            return suffixFor(profile, ItemCapability.HEALTH, previewDungeonValue(item, items, stats.getHealth()), false);
        }
        if (line.contains("Speed:") && !line.contains("Glowstone") && !line.contains("Fish")) {
            return suffixFor(profile, ItemCapability.SPEED, previewDungeonValue(item, items, stats.getSpeed()), true);
        }
        return null;
    }

    private static String suffixFor(ItemProfile profile, ItemCapability capability, double value, boolean percent) {
        if (value <= 0.0 || profile == null || !profile.hasCapability(capability)) {
            return null;
        }
        return " " + DUNGEON_PREVIEW_COLOR + "(+" + formatPreviewStat(value) + (percent ? "%" : "") + ")";
    }

    /** Dot decimals only — German commas broke strip/re-apply and stacked duplicate suffixes. */
    private static String formatPreviewStat(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.0000001d) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static void addStatLines(String itemId, ItemStats stats, List<String> lore) {
        ItemProfile profile = ItemProfile.fromItemId(itemId);
        add(lore, profile, ItemCapability.DAMAGE, stats.getDamage(),
                "§7⚔ Damage: §f+" + ItemLore.formatStat(stats.getDamage()));
        add(lore, profile, ItemCapability.DEFENSE, stats.getDefense(),
                "§7🛡 Defense: §f+" + ItemLore.formatStat(stats.getDefense()));
        add(lore, profile, ItemCapability.HEALTH, stats.getHealth(),
                "§7❤ Health: §f+" + ItemLore.formatStat(stats.getHealth()));
        add(lore, profile, ItemCapability.ATTACK_SPREAD, stats.getAttackSpread(),
                "§7⚔ Attack Spread: §f+" + ItemLore.formatStat(stats.getAttackSpread()));
        add(lore, profile, ItemCapability.UNDEAD_RESIST, stats.getUndeadResist(),
                "§7☠ Undead Resist: §f+" + ItemLore.formatStat(stats.getUndeadResist()) + "%");
        add(lore, profile, ItemCapability.CRIT_CHANCE, stats.getCritChance(),
                "§7✧ Crit Chance: §f+" + ItemLore.formatStat(stats.getCritChance()) + "%");
        add(lore, profile, ItemCapability.CRIT_DAMAGE, stats.getCritDamage(),
                "§7✧ Crit Damage: §f+" + ItemLore.formatStat(stats.getCritDamage()) + "%");
        add(lore, profile, ItemCapability.SPEED, stats.getSpeed(),
                "§7✦ Speed: §f+" + ItemLore.formatStat(stats.getSpeed()) + "%");
    }

    private static void add(
            List<String> lore,
            ItemProfile profile,
            ItemCapability capability,
            double value,
            String line
    ) {
        if (value > 0.0 && profile != null && profile.hasCapability(capability)) {
            lore.add(line);
        }
    }
}
