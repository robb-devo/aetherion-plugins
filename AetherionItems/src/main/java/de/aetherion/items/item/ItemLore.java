package de.aetherion.items.item;

import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stat block + booster block for every Aetherion tooltip (see docs/ITEM_TOOLTIP_SPEC.md).
 * One stat order, one number format, zero stats hidden, compact boosters.
 */
public final class ItemLore {

    /** Max visible characters for the booster "Fits" line before it wraps. */
    private static final int FITS_WIDTH = 40;

    /** Canonical stat order. Enum order == tooltip order. */
    public enum StatLine {
        DAMAGE("⚔", "Damage", false, ItemCapability.DAMAGE, ItemStats::getDamage),
        ATTACK_SPREAD("⚔", "Attack Spread", false, ItemCapability.ATTACK_SPREAD, ItemStats::getAttackSpread),
        CRIT_CHANCE("✧", "Crit Chance", true, ItemCapability.CRIT_CHANCE, ItemStats::getCritChance),
        CRIT_DAMAGE("✧", "Crit Damage", true, ItemCapability.CRIT_DAMAGE, ItemStats::getCritDamage),
        UNDEAD_DAMAGE("☠", "Undead Damage", true, ItemCapability.UNDEAD_DAMAGE, ItemStats::getUndeadDamage),
        HEALTH("❤", "Health", false, ItemCapability.HEALTH, ItemStats::getHealth),
        DEFENSE("🛡", "Defense", false, ItemCapability.DEFENSE, ItemStats::getDefense),
        UNDEAD_RESIST("☠", "Undead Resist", true, ItemCapability.UNDEAD_RESIST, ItemStats::getUndeadResist),
        SPEED("✦", "Speed", true, ItemCapability.SPEED, ItemStats::getSpeed),
        MINING_POWER("⛏", "Mining Power", false, ItemCapability.MINING_POWER, ItemStats::getMiningPower),
        FORTUNE("💎", "Fortune", false, ItemCapability.FORTUNE, ItemStats::getFortune),
        SPREAD("✦", "Spread", false, ItemCapability.SPREAD, ItemStats::getSpread),
        HARVEST("🌾", "Harvest", false, ItemCapability.HARVEST_SPREAD, ItemStats::getHarvestSpread),
        FISH_SPEED("🎣", "Fish Speed", false, ItemCapability.FISHING_SPEED, ItemStats::getFishingSpeed),
        FISH_CATCH("🐟", "Fish Catch", false, ItemCapability.FISHING_CATCH, ItemStats::getFishingCatch),
        CATCH_RATE("☘", "Catch Rate", true, ItemCapability.PET_CATCH_RATE, ItemStats::getCatchRate);

        private final String icon;
        private final String label;
        private final boolean percent;
        private final ItemCapability capability;
        private final ToDoubleFunction<ItemStats> getter;

        StatLine(String icon, String label, boolean percent, ItemCapability capability, ToDoubleFunction<ItemStats> getter) {
            this.icon = icon;
            this.label = label;
            this.percent = percent;
            this.capability = capability;
            this.getter = getter;
        }

        public String label() {
            return label;
        }

        public boolean percent() {
            return percent;
        }

        public ItemCapability capability() {
            return capability;
        }

        public double value(ItemStats stats) {
            return stats == null ? 0.0 : getter.applyAsDouble(stats);
        }

        /** {@code §7⚔ Damage: §f+36} */
        public String line(double value) {
            String sign = value < 0 ? "-" : "+";
            return "§7" + icon + " " + label + ": §f" + sign + formatStat(Math.abs(value)) + (percent ? "%" : "");
        }

        static StatLine fromLabel(String raw) {
            if (raw == null) {
                return null;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            return switch (key) {
                case "damage" -> DAMAGE;
                case "attack spread" -> ATTACK_SPREAD;
                case "crit chance" -> CRIT_CHANCE;
                case "crit damage" -> CRIT_DAMAGE;
                case "undead damage" -> UNDEAD_DAMAGE;
                case "health" -> HEALTH;
                case "defense" -> DEFENSE;
                case "undead resist" -> UNDEAD_RESIST;
                case "speed" -> SPEED;
                case "mining power" -> MINING_POWER;
                case "fortune" -> FORTUNE;
                case "spread" -> SPREAD;
                case "harvest", "harvest spread" -> HARVEST;
                case "fish speed", "fishing speed" -> FISH_SPEED;
                case "fish catch", "fishing catch" -> FISH_CATCH;
                case "catch rate", "pet catch rate" -> CATCH_RATE;
                default -> null;
            };
        }
    }

    /** A parsed stat line: stat, shown value and any trailing suffix (e.g. dungeon preview). */
    public record ParsedStat(StatLine stat, double value, String suffix) {
    }

    private static final Pattern STAT_PATTERN = Pattern.compile(
            "^(?:\\S{1,2}\\s+)?([A-Za-z][A-Za-z ]*?):\\s*([+-]?)(\\d+(?:[.,]\\d+)?)\\s*(%?)(.*)$"
    );

    private ItemLore() {
    }

    /* =========================================================
     * NUMBERS
     * ========================================================= */

    /** English stat number: {@code 12}, {@code 1.5}, {@code 0.35}. */
    public static String formatStat(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        double rounded = Math.round(value * 100.0d) / 100.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0000001d) {
            return Long.toString(Math.round(rounded));
        }
        String text = String.format(Locale.US, "%.2f", rounded);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /* =========================================================
     * STAT LINES
     * ========================================================= */

    public static ParsedStat parseStat(String line) {
        if (line == null || line.isBlank() || isBoosterRow(line) || isBoosterHeader(line)) {
            return null;
        }
        String plain = line.replaceAll("§.", "").trim();
        Matcher matcher = STAT_PATTERN.matcher(plain);
        if (!matcher.matches()) {
            return null;
        }
        StatLine stat = StatLine.fromLabel(matcher.group(1));
        if (stat == null) {
            return null;
        }
        String number = matcher.group(3).replace(',', '.');
        double value;
        try {
            value = Double.parseDouble(number);
        } catch (NumberFormatException ex) {
            return null;
        }
        if ("-".equals(matcher.group(2))) {
            value = -value;
        }
        String rest = matcher.group(5) == null ? "" : matcher.group(5).trim();
        // Anything after the number must be a short parenthesised suffix, otherwise it is prose.
        if (!rest.isEmpty() && !rest.startsWith("(")) {
            return null;
        }
        return new ParsedStat(stat, value, suffixOf(line));
    }

    /** Raw (colored) trailing suffix after the value, e.g. {@code " §d(+81)"}; empty if none. */
    private static String suffixOf(String line) {
        int open = line.lastIndexOf('(');
        if (open < 0) {
            return "";
        }
        int start = open;
        while (start >= 2 && line.charAt(start - 2) == '§') {
            start -= 2;
        }
        while (start > 0 && line.charAt(start - 1) == ' ') {
            start--;
        }
        return line.substring(start);
    }

    public static boolean isStatLine(String line) {
        return parseStat(line) != null;
    }

    /**
     * Rewrites the stat block from the item's real stats (fixed order, zeros hidden)
     * and rebuilds the booster block.
     */
    public static void updateDisplayedStats(List<String> lore, ItemStats stats, ItemProfile profile) {
        if (lore == null) {
            return;
        }
        rebuildStatBlock(lore, stats, profile);
        replaceBoosterSections(lore, stats, profile);
    }

    public static void ensureStatLines(List<String> lore, ItemStats stats, ItemProfile profile) {
        if (lore == null || profile == null) {
            return;
        }
        rebuildStatBlock(lore, stats, profile);
    }

    private static void rebuildStatBlock(List<String> lore, ItemStats stats, ItemProfile profile) {
        Map<StatLine, Double> authored = new EnumMap<>(StatLine.class);
        int insertAt = -1;
        for (int i = 0; i < lore.size(); i++) {
            ParsedStat parsed = parseStat(lore.get(i));
            if (parsed == null) {
                continue;
            }
            if (insertAt < 0) {
                insertAt = i;
            }
            authored.putIfAbsent(parsed.stat(), parsed.value());
            lore.remove(i);
            i--;
        }
        List<String> block = new ArrayList<>();
        for (StatLine stat : StatLine.values()) {
            boolean capable = profile != null && profile.hasCapability(stat.capability());
            double value = stat.value(stats);
            if (Math.abs(value) < 0.0000001d && !capable) {
                Double fromLore = authored.get(stat);
                value = fromLore == null ? 0.0 : fromLore;
            }
            if (Math.abs(value) < 0.0000001d) {
                continue;
            }
            block.add(stat.line(value));
        }
        if (block.isEmpty()) {
            return;
        }
        if (insertAt < 0) {
            insertAt = firstContentIndex(lore);
        }
        if (insertAt < 0 || insertAt > lore.size()) {
            insertAt = lore.size();
        }
        lore.addAll(insertAt, block);
    }

    private static int firstContentIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line == null || line.isBlank() || LoreLayout.isRarityFooter(line)) {
                continue;
            }
            return i;
        }
        return lore.isEmpty() ? 0 : -1;
    }

    /* =========================================================
     * BOOSTERS
     * ========================================================= */

    public static void stripBoosterSections(List<String> lore) {
        if (lore == null) {
            return;
        }
        lore.removeIf(line -> line != null && isBoosterLine(line));
        // collapse doubled blanks left behind
        for (int i = lore.size() - 1; i > 0; i--) {
            if (isBlank(lore.get(i)) && isBlank(lore.get(i - 1))) {
                lore.remove(i);
            }
        }
        while (!lore.isEmpty() && isBlank(lore.get(lore.size() - 1))) {
            lore.remove(lore.size() - 1);
        }
    }

    public static void appendBoosterSections(List<String> lore, ItemStats stats, ItemProfile profile) {
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

    public static void replaceBoosterSections(List<String> lore, ItemStats stats, ItemProfile profile) {
        stripBoosterSections(lore);
        appendBoosterSections(lore, stats, profile);
    }

    /**
     * {@code Boosters 3/14}, one row per applied type, then the still-open types on one "Fits" line.
     */
    private static void appendBoosterRows(List<String> lore, ItemStats stats, List<BoosterType> types) {
        if (lore == null || types == null || types.isEmpty()) {
            return;
        }
        ItemStats safe = stats == null ? new ItemStats() : stats;
        int total = safe.getTotalBoosters();
        List<String> block = new ArrayList<>();
        block.add("§8Boosters §f" + total + "§8/" + BoosterLimits.MAX_TOTAL);
        List<BoosterType> open = new ArrayList<>();
        for (BoosterType type : types) {
            int count = safe.boosterCount(type);
            if (count > 0) {
                block.add(type.loreColor() + type.emblem() + " " + type.displayName() + " §fx" + count);
            } else {
                open.add(type);
            }
        }
        if (total < BoosterLimits.MAX_TOTAL && !open.isEmpty()) {
            block.addAll(fitsLines(open));
        }
        if (!lore.isEmpty() && !isBlank(lore.get(lore.size() - 1))) {
            lore.add("");
        }
        lore.addAll(block);
    }

    private static List<String> fitsLines(List<BoosterType> open) {
        List<String> names = new ArrayList<>();
        for (BoosterType type : open) {
            names.add(type.displayName());
        }
        return fitsLines(names, "Fits");
    }

    /** The "Fits" rows for already localized booster names ({@code word} is the localized "Fits"). */
    static List<String> fitsLines(List<String> names, String word) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder("§8" + word + ": ");
        int visible = (word + ": ").length();
        boolean first = true;
        for (String name : names) {
            int add = (first ? 0 : 3) + name.length();
            if (!first && visible + add > FITS_WIDTH) {
                lines.add(line.toString());
                line = new StringBuilder("§8· ");
                visible = 2;
                first = true;
            }
            if (!first) {
                line.append("§8 · ");
            }
            line.append("§7").append(name);
            visible += (first ? 0 : 3) + name.length();
            first = false;
        }
        lines.add(line.toString());
        return lines;
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

    public static boolean isBoosterLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (isBoosterHeader(line) || isBoosterRow(line) || isFitsLine(line)) {
            return true;
        }
        String plain = line.replaceAll("§.", "").trim();
        return plain.equalsIgnoreCase("Special Boosters:")
                || plain.equalsIgnoreCase("Special Boosters")
                || (plain.startsWith("Total:") && plain.contains("/"));
    }

    private static boolean isBoosterHeader(String line) {
        if (line == null || line.contains("Special")) {
            return false;
        }
        String plain = line.replaceAll("§.", "").trim();
        if (plain.equalsIgnoreCase("Boosters:") || plain.equalsIgnoreCase("Boosters")) {
            return true;
        }
        return plain.matches("(?i)Boosters\\s+\\d+\\s*/\\s*\\d+");
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

    /** {@code Fits: Coal · Iron} and its wrapped continuation {@code · Gold · Diamond}. */
    private static boolean isFitsLine(String line) {
        String plain = line.replaceAll("§.", "").trim();
        String body;
        if (plain.startsWith("Fits:")) {
            body = plain.substring(5);
        } else if (plain.startsWith("·")) {
            body = plain.substring(1);
        } else {
            return false;
        }
        String[] parts = body.split("·");
        int names = 0;
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            boolean known = false;
            for (BoosterType type : BoosterType.values()) {
                if (type.displayName().equalsIgnoreCase(name)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return false;
            }
            names++;
        }
        return names > 0;
    }

    private static boolean isBlank(String line) {
        return line == null || line.isBlank();
    }
}
