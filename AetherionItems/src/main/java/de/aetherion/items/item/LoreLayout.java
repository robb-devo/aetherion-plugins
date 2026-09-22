package de.aetherion.items.item;

import de.aetherion.items.model.Rarity;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One lore shape for every item:
 * category, stats, flavor, extras, boosters, level, hints, rarity footer, value.
 */
public final class LoreLayout {

    private static final java.util.regex.Pattern RARITY_FOOTER = java.util.regex.Pattern.compile(
            "(?i)^(?:§.)*(?:✦\\s*)?(?:§.)*(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|AETHERED|GOD ITEM)\\b.*$"
    );

    private LoreLayout() {
    }

    public static List<String> normalize(List<String> lore, String itemId) {
        return normalize(lore, itemId, null);
    }

    public static List<String> normalize(List<String> lore, String itemId, Rarity rarity) {
        if (lore == null || lore.isEmpty()) {
            return lore;
        }

        List<String> stats = new ArrayList<>();
        List<String> flavor = new ArrayList<>();
        List<String> extras = new ArrayList<>();
        List<String> boosters = new ArrayList<>();
        List<String> level = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        List<String> value = new ArrayList<>();

        String joke = ItemFlavor.jokeFor(itemId);
        String jokePlain = joke == null ? null : ChatColor.stripColor(joke);
        String bannerLabel = null;

        for (String line : lore) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (isRarityFooter(line)) {
                continue;
            }
            if (isTypeLabelLine(line)) {
                if (bannerLabel == null) {
                    bannerLabel = ItemSlotLabel.fromBannerLine(line);
                    if (bannerLabel == null) {
                        bannerLabel = cleanTypeLabel(line);
                    }
                }
                continue;
            }
            if (isValueLine(line)) {
                addUnique(value, line);
                continue;
            }
            if (ItemLore.isBoosterLine(line)) {
                addUnique(boosters, line);
                continue;
            }
            if (isLevelLine(line)) {
                addUnique(level, line);
                continue;
            }
            if (isHintLine(line)) {
                addUnique(hints, line);
                continue;
            }
            if (ItemLore.isStatLine(line) || isDungeonStatPreview(line)) {
                addUnique(stats, line);
                continue;
            }
            if (isAbilityLine(line) || isMechanicalExtra(line)) {
                addUnique(extras, line);
                continue;
            }
            if (jokePlain != null && jokePlain.equals(ChatColor.stripColor(line))) {
                continue;
            }
            if (jokePlain != null && isPersonalFlavor(line)) {
                continue;
            }
            if (isPersonalFlavor(line)) {
                addUnique(flavor, line);
                continue;
            }
            addUnique(extras, line);
        }

        String fromId = ItemSlotLabel.plain(itemId);
        // Prefer precise slot label (e.g. Mining Pickaxe) over vague banners (Mining Gear / Aetherion Tool).
        String cat = preferLabel(fromId, bannerLabel);
        // No gray type line at the top — rarity footer at the bottom already carries type + rarity.

        if (joke != null) {
            flavor.clear();
            flavor.add(joke);
        }

        // Drop any leftover type echoes that slipped into other blocks.
        stripMatchingTypeEchoes(flavor, cat);
        stripMatchingTypeEchoes(extras, cat);
        stripMatchingTypeEchoes(hints, cat);

        List<String> footer = new ArrayList<>();
        String rarityFooter = rarityFooterLine(rarity, itemId, cat);
        if (rarityFooter != null) {
            footer.add(rarityFooter);
        }

        List<String> out = new ArrayList<>();
        appendBlock(out, stats);
        appendBlock(out, flavor);
        appendBlock(out, extras);
        appendBlock(out, boosters);
        appendBlock(out, level);
        appendBlock(out, hints);
        appendBlock(out, footer);
        appendBlock(out, value);
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static String rarityFooterLine(Rarity rarity, String itemId, String categoryPlain) {
        if (rarity == null && !ItemPresentation.isGod(itemId)) {
            return null;
        }
        String rarityName;
        String color;
        if (ItemPresentation.isGod(itemId)) {
            rarityName = "GOD";
            color = "§c";
        } else {
            rarityName = rarity.name();
            color = rarity.getChatColor().toString();
        }
        String type = categoryPlain != null && !categoryPlain.isBlank()
                ? categoryPlain.toUpperCase(Locale.ROOT)
                : ItemSlotLabel.footerType(itemId);
        return color + "§l" + rarityName + " " + type;
    }

    public static int indexBeforeValue(List<String> lore) {
        if (lore == null) {
            return -1;
        }
        for (int i = 0; i < lore.size(); i++) {
            if (isValueLine(lore.get(i))) {
                return i > 0 && lore.get(i - 1) != null && lore.get(i - 1).isBlank() ? i - 1 : i;
            }
        }
        return -1;
    }

    private static void appendBlock(List<String> out, List<String> block) {
        if (block == null || block.isEmpty()) {
            return;
        }
        if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) {
            out.add("");
        }
        out.addAll(block);
    }

    private static void addUnique(List<String> block, String line) {
        String needle = ChatColor.stripColor(line);
        for (String existing : block) {
            if (needle.equals(ChatColor.stripColor(existing))) {
                return;
            }
        }
        block.add(line);
    }

    static boolean isRarityFooter(String line) {
        if (line == null) {
            return false;
        }
        String stripped = ChatColor.stripColor(line).trim();
        // Booster items list "COMMON: +2.8" per rarity. Those are stats, not the footer.
        if (stripped.indexOf(':') >= 0) {
            return false;
        }
        return RARITY_FOOTER.matcher(stripped).matches();
    }

    static boolean isValueLine(String line) {
        if (line == null) {
            return false;
        }
        return line.contains("Value:") || line.contains("AH value:") || line.contains("Trade value:");
    }

    static boolean isLevelLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        String lower = plain.toLowerCase(Locale.ROOT);
        if (plain.contains("Hoe Level:")
                || plain.contains("Rod Level:")
                || plain.contains("Axe Level:")
                || plain.contains("Gaff Level:")
                || plain.contains("Gear Level:")
                || plain.contains("Charm Level:")) {
            return true;
        }
        if (plain.contains("█") && (plain.contains("/") || lower.contains("max"))) {
            return true;
        }
        if (lower.contains("per level")
                || lower.contains("levels from catches")
                || lower.contains("nothing left to prove")
                || lower.contains("menagerie filed")) {
            return true;
        }
        return lower.startsWith("dungeon:")
                || lower.startsWith("lv ")
                || lower.startsWith("infused:")
                || lower.startsWith("level cap")
                || lower.contains("level bonus");
    }

    static boolean isHintLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        if (lower.contains("hold shift") || lower.contains("sneak in inventory")) {
            return true;
        }
        if (lower.startsWith("dungeonized")
                || lower.contains("dungeon-bound")
                || lower.contains("rarity bonus")
                || (lower.contains("anvil +") && lower.contains("core"))) {
            return true;
        }
        return lower.contains("dungeon core")
                && (lower.contains("dungeon gear")
                || lower.contains("then it levels")
                || lower.contains("rarity up")
                || lower.contains("level cap")
                || lower.contains("compatible")
                || lower.contains("stronger mini"));
    }

    static boolean isCategoryBanner(String line) {
        return isTypeLabelLine(line);
    }

    /** Gray type/slot lines — kept once as category, never repeated. */
    static boolean isTypeLabelLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isEmpty()) {
            return false;
        }
        if (plain.startsWith("aetherion ") && (plain.endsWith(" set")
                || plain.endsWith(" tool")
                || plain.endsWith(" weapon")
                || plain.endsWith(" armor")
                || plain.endsWith(" gear")
                || plain.endsWith(" charm")
                || plain.endsWith(" spark"))) {
            return true;
        }
        if (plain.equals("aetherion charm")
                || plain.equals("starter tool")
                || plain.equals("basic aetherion tool")
                || plain.equals("basic aetherion weapon")
                || plain.equals("basic armor")
                || plain.startsWith("god kit")
                || plain.equals("test arena prototype")) {
            return true;
        }
        if (plain.matches("(mining|combat|farming|foraging|fishing|catcher|dungeon) (gear|tool|weapon|armor|pickaxe|axe|hoe|rod|gaff|helmet|chestplate|leggings|boots)( [ivx]+)?")) {
            return true;
        }
        return plain.matches("combat armor( [ivx]+)?")
                || plain.matches("mining armor( [ivx]+)?")
                || plain.equals("farming tool")
                || plain.equals("farming armor")
                || plain.equals("foraging tool")
                || plain.equals("foraging armor")
                || plain.equals("fishing tool")
                || plain.equals("fishing armor")
                || plain.equals("diving armor")
                || plain.equals("catcher tool")
                || plain.equals("catcher armor");
    }

    private static String cleanTypeLabel(String line) {
        if (line == null) {
            return null;
        }
        String plain = ChatColor.stripColor(line).trim()
                .replaceAll("(?i)^aetherion\\s+", "")
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isEmpty()) {
            return null;
        }
        String[] parts = plain.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (part.length() <= 3 && part.equals(part.toUpperCase(Locale.ROOT))) {
                out.append(part);
            } else {
                out.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    out.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return out.toString();
    }

    private static String preferLabel(String fromId, String fromBanner) {
        if (fromId != null && !fromId.isBlank()) {
            // Vague banners lose to precise id labels.
            if (fromBanner == null || fromBanner.isBlank()) {
                return fromId;
            }
            String banner = fromBanner.toLowerCase(Locale.ROOT);
            if (banner.equals("mining gear") || banner.equals("combat gear")
                    || banner.equals("farming gear") || banner.equals("foraging gear")
                    || banner.equals("fishing gear") || banner.equals("catcher gear")
                    || banner.endsWith(" tool") || banner.endsWith(" weapon")
                    || banner.endsWith(" set") || banner.endsWith(" armor")) {
                if (!fromId.equalsIgnoreCase(fromBanner) && !fromId.toLowerCase(Locale.ROOT).endsWith(" gear")) {
                    return fromId;
                }
            }
            // Prefer longer/more specific.
            if (fromId.length() >= fromBanner.length()) {
                return fromId;
            }
            return fromBanner;
        }
        return fromBanner;
    }

    private static void stripMatchingTypeEchoes(List<String> block, String categoryPlain) {
        if (block == null || block.isEmpty()) {
            return;
        }
        String cat = categoryPlain == null ? "" : categoryPlain.toLowerCase(Locale.ROOT).trim();
        block.removeIf(line -> {
            if (isTypeLabelLine(line)) {
                return true;
            }
            if (cat.isEmpty()) {
                return false;
            }
            String plain = ChatColor.stripColor(line).trim().toLowerCase(Locale.ROOT);
            if (plain.equals(cat)) {
                return true;
            }
            // "Aetherion Mining Tool" vs category "Mining Pickaxe"
            String stripped = plain.replaceFirst("^aetherion\\s+", "");
            return stripped.equals(cat)
                    || (cat.startsWith("mining") && (stripped.equals("mining tool") || stripped.equals("mining gear")))
                    || (cat.startsWith("combat") && (stripped.equals("combat tool") || stripped.equals("combat gear")
                    || stripped.equals("combat weapon") || stripped.equals("combat set")))
                    || (cat.startsWith("farming") && (stripped.equals("farming tool") || stripped.equals("farming gear")))
                    || (cat.startsWith("foraging") && (stripped.equals("foraging tool") || stripped.equals("foraging gear")))
                    || (cat.startsWith("fishing") && (stripped.equals("fishing tool") || stripped.equals("fishing gear")))
                    || (cat.startsWith("catcher") && (stripped.equals("catcher tool") || stripped.equals("catcher gear")));
        });
    }

    static boolean isDungeonStatPreview(String line) {
        return de.aetherion.items.dungeon.DungeonArmor.isStandaloneDungeonStatPreview(line);
    }

    static boolean isAbilityLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return lower.contains("right-click")
                || lower.contains("hold right")
                || lower.startsWith("forward.")
                || lower.contains("no arrows required")
                || lower.contains("warp step")
                || lower.contains("blink");
    }

    static boolean isMechanicalExtra(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        String lower = plain.toLowerCase(Locale.ROOT);
        if (lower.contains("off-hand")
                || lower.contains("hold in the off hand")
                || lower.contains("breathe underwater")
                || lower.contains("full set:")) {
            return true;
        }
        return plain.contains("+") || plain.contains("%");
    }

    static boolean isPersonalFlavor(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("§7") && !trimmed.startsWith("§8")) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        return plain.length() >= 12 && !plain.contains(":");
    }
}
