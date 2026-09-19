package de.aetherion.items.item;

import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemLore {

    /**
     * Compact booster prototype (active rows + muted availability chips).
     * Set to {@code false} to restore the previous full per-type list.
     */
    private static final boolean USE_COMPACT_BOOSTER_SECTION = true;

    /** How many empty-availability chips fit on one lore line. */
    private static final int EMPTY_BOOSTERS_PER_LINE = 4;

    private ItemLore() {
    }

    public static void stripBoosterSections(List<String> lore) {
        int index = findCoreBoosterSection(lore);

        if (index < 0) {
            return;
        }

        while (lore.size() > index) {
            lore.remove(lore.size() - 1);
        }

        while (!lore.isEmpty() && lore.get(lore.size() - 1).isBlank()) {
            lore.remove(lore.size() - 1);
        }
    }

    public static void appendBoosterSections(
            List<String> lore,
            ItemStats stats,
            ItemProfile profile
    ) {
        appendBoosterRows(lore, stats, applicableBoosters(stats, profile));
    }

    public static void appendBoosterSections(
            List<String> lore,
            ItemStats stats,
            boolean showSpread,
            boolean showAttackSpread,
            boolean showHealth
    ) {
        appendBoosterSections(lore, stats, showSpread, showAttackSpread, showHealth, false, false, false, false, false);
    }

    public static void appendBoosterSections(
            List<String> lore,
            ItemStats stats,
            boolean showSpread,
            boolean showAttackSpread,
            boolean showHealth,
            boolean showSpeed,
            boolean showCatchRate,
            boolean showHarvest,
            boolean showCritChance,
            boolean showCritDamage
    ) {
        List<BoosterType> types = new ArrayList<>();
        types.add(BoosterType.COAL);
        types.add(BoosterType.IRON);
        types.add(BoosterType.GOLD);
        types.add(BoosterType.DIAMOND);
        if (showSpread) {
            types.add(BoosterType.EMERALD);
        }
        if (showAttackSpread) {
            types.add(BoosterType.REDSTONE);
        }
        if (showHealth) {
            types.add(BoosterType.LAPIS);
        }
        if (showSpeed) {
            types.add(BoosterType.GLOWSTONE);
        }
        if (showCatchRate) {
            types.add(BoosterType.WHEAT);
        }
        if (showHarvest) {
            types.add(BoosterType.CARROT);
        }
        if (showCritDamage) {
            types.add(BoosterType.OAK);
        }
        if (showCritChance) {
            types.add(BoosterType.BIRCH);
        }
        appendBoosterRows(lore, stats, types);
    }

    private static void appendBoosterRows(
            List<String> lore,
            ItemStats stats,
            List<BoosterType> types
    ) {
        if (USE_COMPACT_BOOSTER_SECTION) {
            appendBoosterRowsCompact(lore, stats, types);
        } else {
            appendBoosterRowsLegacy(lore, stats, types);
        }
    }

    /**
     * Prototype: only applied boosters as full rows; remaining allowed types
     * as compact muted chips; total lives in the header.
     */
    private static void appendBoosterRowsCompact(
            List<String> lore,
            ItemStats stats,
            List<BoosterType> types
    ) {
        if (lore == null || types == null || types.isEmpty()) {
            return;
        }

        ItemStats safe = stats == null ? new ItemStats() : stats;
        List<BoosterType> active = new ArrayList<>();
        List<BoosterType> empty = new ArrayList<>();
        for (BoosterType type : types) {
            if (safe.boosterCount(type) > 0) {
                active.add(type);
            } else {
                empty.add(type);
            }
        }

        lore.add("");
        lore.add("§8Boosters  §f" + safe.getTotalBoosters() + "§7/" + BoosterLimits.MAX_TOTAL);

        if (!active.isEmpty()) {
            int nameWidth = 0;
            for (BoosterType type : active) {
                nameWidth = Math.max(nameWidth, type.displayName().length());
            }
            for (BoosterType type : active) {
                lore.add(boosterLine(type, safe.boosterCount(type), nameWidth));
            }
        }

        appendEmptyBoosterHints(lore, empty);
    }

    private static void appendEmptyBoosterHints(List<String> lore, List<BoosterType> empty) {
        if (empty == null || empty.isEmpty()) {
            return;
        }

        StringBuilder line = new StringBuilder();
        int onLine = 0;
        for (BoosterType type : empty) {
            if (onLine >= EMPTY_BOOSTERS_PER_LINE) {
                lore.add(line.toString());
                line.setLength(0);
                onLine = 0;
            }
            if (onLine > 0) {
                line.append("  ");
            }
            line.append(type.loreColor())
                    .append(type.emblem())
                    .append(" §8")
                    .append(type.displayName());
            onLine++;
        }
        if (onLine > 0) {
            lore.add(line.toString());
        }
    }

    /**
     * Previous design: one row per allowed booster (including x0) + Total line.
     * Kept for easy rollback via {@link #USE_COMPACT_BOOSTER_SECTION}.
     */
    private static void appendBoosterRowsLegacy(
            List<String> lore,
            ItemStats stats,
            List<BoosterType> types
    ) {
        if (lore == null || types == null || types.isEmpty()) {
            return;
        }

        ItemStats safe = stats == null ? new ItemStats() : stats;
        int nameWidth = 0;
        for (BoosterType type : types) {
            nameWidth = Math.max(nameWidth, type.displayName().length());
        }

        lore.add("");
        lore.add("§8Boosters");
        for (BoosterType type : types) {
            lore.add(boosterLine(type, safe.boosterCount(type), nameWidth));
        }
        lore.add("§8Total: §f" + safe.getTotalBoosters() + "§7/" + BoosterLimits.MAX_TOTAL);
    }

    private static String boosterLine(BoosterType type, int count, int nameWidth) {
        String name = padName(type.displayName(), nameWidth);
        if (count > 0) {
            String color = type.loreColor();
            return color + type.emblem() + "  " + color + name + "  §fx" + count;
        }
        return "§8" + type.emblem() + "  §7" + name + "  §8x0";
    }

    private static String padName(String name, int width) {
        if (name.length() >= width) {
            return name;
        }
        return name + " ".repeat(width - name.length());
    }

    private static List<BoosterType> applicableBoosters(ItemStats stats, ItemProfile profile) {
        List<BoosterType> types = new ArrayList<>();
        if (profile != null && profile.allowsCoreBoosters()) {
            types.add(BoosterType.COAL);
            types.add(BoosterType.IRON);
            types.add(BoosterType.GOLD);
            types.add(BoosterType.DIAMOND);
        }
        for (BoosterType type : BoosterType.values()) {
            if (type.isSpecial() && profile != null && profile.allowsSpecialBooster(type)) {
                types.add(type);
            }
        }
        if (stats != null) {
            for (BoosterType type : BoosterType.values()) {
                if (stats.boosterCount(type) > 0 && !types.contains(type)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    public static void replaceBoosterSections(
            List<String> lore,
            ItemStats stats,
            ItemProfile profile
    ) {
        stripBoosterSections(lore);
        appendBoosterSections(lore, stats, profile);
    }

    public static void updateDisplayedStats(
            List<String> lore,
            ItemStats stats,
            ItemProfile profile
    ) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line == null || isBoosterRow(line) || isBoosterHeader(line)) {
                continue;
            }

            if (line.contains("Mining Power:")) {
                lore.set(i, "§7⛏ Mining Power: §f+" + formatStat(stats.getMiningPower()));
            } else if (line.contains("Fortune:")) {
                lore.set(i, "§7💎 Fortune: §f+" + formatStat(stats.getFortune()));
            } else if (line.contains("Crit Chance:")) {
                lore.set(i, "§7✧ Crit Chance: §f+" + formatStat(stats.getCritChance()) + "%");
            } else if (line.contains("Crit Damage:")) {
                lore.set(i, "§7✧ Crit Damage: §f+" + formatStat(stats.getCritDamage()) + "%");
            } else if (line.contains("Undead Damage:")) {
                lore.set(i, "§7☠ Undead Damage: §f+" + formatStat(stats.getUndeadDamage()) + "%");
            } else if (line.contains("Undead Resist:")) {
                lore.set(i, "§7☠ Undead Resist: §f+" + formatStat(stats.getUndeadResist()) + "%");
            } else if (line.contains("Damage:") && !line.contains("Attack")) {
                lore.set(i, "§7⚔ Damage: §f+" + formatStat(stats.getDamage()));
            } else if (line.contains("Defense:")) {
                lore.set(i, "§7🛡 Defense: §f+" + formatStat(stats.getDefense()));
            } else if (line.contains("Health:") && !line.contains("Lapis")) {
                lore.set(i, "§7❤ Health: §f+" + formatStat(stats.getHealth()));
            } else if (line.contains("Attack Spread:")) {
                lore.set(i, "§7⚔ Attack Spread: §f+" + formatStat(stats.getAttackSpread()));
            } else if (line.contains("Catch Rate:")) {
                lore.set(i, "§7☘ Catch Rate: §f+" + formatStat(stats.getCatchRate()) + "%");
            } else if (line.contains("Fish Speed:")) {
                lore.set(i, "§7🎣 Fish Speed: §f+" + formatStat(stats.getFishingSpeed()));
            } else if (line.contains("Fish Catch:")) {
                lore.set(i, "§7🐟 Fish Catch: §f+" + formatStat(stats.getFishingCatch()));
            } else if (line.contains("Speed:") && !line.contains("Glowstone") && !line.contains("Fish")) {
                lore.set(i, "§7✦ Speed: §f+" + formatStat(stats.getSpeed()) + "%");
            } else if (line.contains("Harvest:") && !line.contains("Carrot Harvest")) {
                lore.set(i, "§7🌾 Harvest: §f+" + formatStat(stats.getHarvestSpread()));
            } else if (line.contains("Spread:") && !line.contains("Attack") && !line.contains("Emerald") && !line.contains("Harvest")) {
                lore.set(i, "§7✦ Spread: §f+" + formatStat(stats.getSpread()));
            }
        }

        stripInvalidSpeedLines(lore, profile);
        ensureStatLines(lore, stats, profile);
        replaceBoosterSections(lore, stats, profile);
    }

    public static void ensureStatLines(
            List<String> lore,
            ItemStats stats,
            ItemProfile profile
    ) {
        if (profile == null) {
            return;
        }

        int insertAt = findStatBlockEnd(lore);

        if (insertAt < 0) {
            insertAt = firstContentIndex(lore);
        }

        if (insertAt < 0) {
            insertAt = findCoreBoosterSection(lore);
        }

        if (insertAt < 0) {
            insertAt = lore.size();
        }

        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.MINING_POWER,
                "Mining Power:", "§7⛏ Mining Power: §f+" + formatStat(stats.getMiningPower()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.FORTUNE,
                "Fortune:", "§7💎 Fortune: §f+" + formatStat(stats.getFortune()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.DAMAGE,
                "⚔ Damage:", "§7⚔ Damage: §f+" + formatStat(stats.getDamage()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.DEFENSE,
                "Defense:", "§7🛡 Defense: §f+" + formatStat(stats.getDefense()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.HEALTH,
                "❤ Health:", "§7❤ Health: §f+" + formatStat(stats.getHealth()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.SPREAD,
                "✦ Spread:", "§7✦ Spread: §f+" + formatStat(stats.getSpread()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.HARVEST_SPREAD,
                "Harvest:", "§7🌾 Harvest: §f+" + formatStat(stats.getHarvestSpread()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.FISHING_SPEED,
                "Fish Speed:", "§7🎣 Fish Speed: §f+" + formatStat(stats.getFishingSpeed()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.FISHING_CATCH,
                "Fish Catch:", "§7🐟 Fish Catch: §f+" + formatStat(stats.getFishingCatch()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.ATTACK_SPREAD,
                "Attack Spread:", "§7⚔ Attack Spread: §f+" + formatStat(stats.getAttackSpread()));
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.UNDEAD_DAMAGE,
                "Undead Damage:", "§7☠ Undead Damage: §f+" + formatStat(stats.getUndeadDamage()) + "%");
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.UNDEAD_RESIST,
                "Undead Resist:", "§7☠ Undead Resist: §f+" + formatStat(stats.getUndeadResist()) + "%");
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.CRIT_CHANCE,
                "✧ Crit Chance:", "§7✧ Crit Chance: §f+" + formatStat(stats.getCritChance()) + "%");
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.CRIT_DAMAGE,
                "✧ Crit Damage:", "§7✧ Crit Damage: §f+" + formatStat(stats.getCritDamage()) + "%");
        insertAt = insertStatLine(lore, insertAt, profile, ItemCapability.SPEED,
                "✦ Speed:", "§7✦ Speed: §f+" + formatStat(stats.getSpeed()) + "%");
        insertStatLine(lore, insertAt, profile, ItemCapability.PET_CATCH_RATE,
                "☘ Catch Rate:", "§7☘ Catch Rate: §f+" + formatStat(stats.getCatchRate()) + "%");
    }

    public static String formatStat(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.0000001d) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.GERMAN, "%.2f", value);
    }

    private static int insertStatLine(
            List<String> lore,
            int insertAt,
            ItemProfile profile,
            ItemCapability capability,
            String marker,
            String line
    ) {
        if (!profile.hasCapability(capability) || containsMarker(lore, marker)) {
            return insertAt;
        }

        lore.add(insertAt, line);
        return insertAt + 1;
    }

    private static boolean containsMarker(List<String> lore, String marker) {
        for (String line : lore) {
            if (line.contains(marker)) {
                return true;
            }
        }

        return false;
    }

    private static void stripInvalidSpeedLines(List<String> lore, ItemProfile profile) {
        if (lore == null || lore.isEmpty()) {
            return;
        }
        boolean allowSpeed = has(profile, ItemCapability.SPEED);
        boolean seenSpeed = false;
        var it = lore.iterator();
        while (it.hasNext()) {
            String line = it.next();
            if (line == null || !isMovementSpeedLine(line)) {
                continue;
            }
            if (!allowSpeed || seenSpeed) {
                it.remove();
                continue;
            }
            seenSpeed = true;
        }
    }

    private static boolean isMovementSpeedLine(String line) {
        if (line == null || line.contains("Glowstone") || line.contains("Fish")) {
            return false;
        }
        String plain = line.replaceAll("§.", "").trim();
        return plain.startsWith("✦ Speed:") || plain.startsWith("Speed:");
    }

    private static boolean has(ItemProfile profile, ItemCapability capability) {
        return profile != null && profile.hasCapability(capability);
    }

    public static boolean isStatLine(String line) {
        return isDisplayedStatLine(line);
    }

    public static boolean isBoosterLine(String line) {
        if (line == null) {
            return false;
        }
        if (isBoosterHeader(line) || isBoosterRow(line)) {
            return true;
        }
        String plain = line.replaceAll("§.", "").trim();
        return plain.equalsIgnoreCase("Special Boosters:")
                || plain.equalsIgnoreCase("Special Boosters")
                || (plain.startsWith("Total:") && plain.contains("/"));
    }

    private static int findStatBlockEnd(List<String> lore) {
        int lastStat = -1;

        for (int i = 0; i < lore.size(); i++) {
            if (isDisplayedStatLine(lore.get(i))) {
                lastStat = i;
            }
        }

        return lastStat < 0 ? -1 : lastStat + 1;
    }

    private static boolean isDisplayedStatLine(String line) {
        if (line == null || isBoosterRow(line) || isBoosterHeader(line)) {
            return false;
        }
        if (line.contains("Lapis") || line.contains("Glowstone") || line.contains("Emerald")) {
            return false;
        }

        return line.contains("Mining Power:")
                || line.contains("Fortune:")
                || line.contains("Crit Chance:")
                || line.contains("Crit Damage:")
                || (line.contains("Damage:") && !line.contains("Attack") && !line.contains("Undead"))
                || line.contains("Undead Damage:")
                || line.contains("Undead Resist:")
                || line.contains("Defense:")
                || (line.contains("Health:") && !line.contains("Lapis"))
                || line.contains("Attack Spread:")
                || line.contains("Catch Rate:")
                || (line.contains("Speed:") && !line.contains("Glowstone") && !line.contains("Fish"))
                || (line.contains("Harvest:") && !line.contains("Carrot Harvest"))
                || line.contains("Fish Speed:")
                || line.contains("Fish Catch:")
                || (line.contains("Spread:") && !line.contains("Attack") && !line.contains("Emerald"));
    }

    private static int firstContentIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (isBoosterHeader(line)) {
                return i > 0 && lore.get(i - 1).isBlank() ? i - 1 : i;
            }
            return i;
        }
        return -1;
    }

    private static int findCoreBoosterSection(List<String> lore) {
        int special = -1;
        int total = -1;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line == null) {
                continue;
            }

            if (isBoosterHeader(line)) {
                return i > 0 && lore.get(i - 1).isBlank() ? i - 1 : i;
            }
            if (special < 0 && line.contains("Special Boosters:")) {
                special = i;
            }
            if (total < 0 && line.contains("Total:") && line.contains("/")) {
                total = i;
            }
        }
        int index = special >= 0 ? special : total;
        if (index < 0) {
            return -1;
        }
        return index > 0 && lore.get(index - 1).isBlank() ? index - 1 : index;
    }

    private static boolean isBoosterHeader(String line) {
        if (line == null || line.contains("Special")) {
            return false;
        }
        String plain = line.replaceAll("§.", "").trim();
        if (plain.equalsIgnoreCase("Boosters:") || plain.equalsIgnoreCase("Boosters")) {
            return true;
        }
        // Compact prototype: "Boosters  3/14"
        return plain.length() > 8
                && plain.regionMatches(true, 0, "Boosters", 0, 8)
                && Character.isWhitespace(plain.charAt(8));
    }

    private static boolean isBoosterRow(String line) {
        if (line == null) {
            return false;
        }
        String plain = line.replaceAll("§.", "").trim();
        if (plain.startsWith("●") || plain.startsWith("◆")) {
            return true;
        }
        return plain.contains("Coal Boosters:")
                || plain.contains("Iron Boosters:")
                || plain.contains("Gold Boosters:")
                || plain.contains("Diamond Boosters:")
                || plain.contains("Emerald Spread:")
                || plain.contains("Redstone Attack Spread:")
                || plain.contains("Lapis Health:")
                || plain.contains("Glowstone Speed:")
                || plain.contains("Wheat Catch Rate:")
                || plain.contains("Carrot Harvest:")
                || plain.contains("Birch Crit Chance:")
                || plain.contains("Oak Crit Damage:");
    }
}
