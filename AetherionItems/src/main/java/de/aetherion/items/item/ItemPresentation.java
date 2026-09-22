package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemPresentation {

    private static final Pattern STAT_NUMBER = Pattern.compile("(\\d+)[.,](\\d+)");
    private static final int[] RAINBOW = {
            0xFF5E7E, 0xFF9A5C, 0xFFD76A, 0x7DFFB3, 0x6AD5FF, 0x7B8CFF, 0xC9A7FF, 0xFF8AD4
    };

    private ItemPresentation() {
    }

    public static boolean isGod(String itemId) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("god_");
    }

    public static void polish(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        String itemId = meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        Rarity rarity = rarityOf(meta);
        applyName(meta, itemId, rarity);
        TooltipStyle.apply(meta, rarity);
        if (meta.hasLore() && meta.lore() != null) {
            meta.setLore(polishLore(new ArrayList<>(meta.getLore()), itemId, rarity));
        }
    }

    public static Component coloredName(String plain, String itemId, Rarity rarity) {
        String text = plain == null ? "" : plain.trim();
        if (text.isBlank()) {
            return Component.empty();
        }
        if (isGod(itemId)) {
            return rainbow(text);
        }
        if (itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("simple_")) {
            return Component.text(text)
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.GRAY);
        }
        Component component = Component.text(text).decoration(TextDecoration.ITALIC, false);
        if (rarity != null) {
            component = component.color(rarity.textColor());
        }
        return component;
    }

    public static Component rainbow(String text) {
        Component name = Component.empty().decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int rgb = RAINBOW[i % RAINBOW.length];
            name = name.append(Component.text(character).color(TextColor.color(rgb)));
        }
        return name;
    }

    private static void applyName(ItemMeta meta, String itemId, Rarity rarity) {
        String plain = plainName(meta);
        if (plain.isBlank()) {
            return;
        }
        if (rarity == null && !isGod(itemId)) {
            return;
        }
        meta.displayName(coloredName(plain, itemId, rarity));
    }

    private static String plainName(ItemMeta meta) {
        if (meta.displayName() != null) {
            String fromComponent = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
            if (!fromComponent.isBlank()) {
                return fromComponent;
            }
        }
        if (meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName()).trim();
        }
        return "";
    }

    private static Rarity rarityOf(ItemMeta meta) {
        String raw = meta.getPersistentDataContainer().get(ItemKeys.rarity(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> polishLore(List<String> lore, String itemId, Rarity rarity) {
        List<String> cleaned = new ArrayList<>();
        for (String line : lore) {
            if (isRarityHeader(line)) {
                continue;
            }
            cleaned.add(reformatNumbers(line));
        }
        while (!cleaned.isEmpty() && cleaned.get(0).isBlank()) {
            cleaned.remove(0);
        }
        cleaned.removeIf(ItemPresentation::isDuplicateAetherbladeLine);
        ItemFlavor.inject(cleaned, itemId);
        DungeonCore.ensureHint(cleaned, itemId);
        punctuate(cleaned);
        return LoreLayout.normalize(cleaned, itemId, rarity);
    }

    private static boolean isDuplicateAetherbladeLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim().toLowerCase(Locale.ROOT);
        return plain.equals("a mythical blade. origin unknown");
    }

    private static boolean isRarityHeader(String line) {
        return LoreLayout.isRarityFooter(line);
    }

    public static String reformatNumbers(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        Matcher matcher = STAT_NUMBER.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String whole = matcher.group(1);
            String fraction = matcher.group(2);
            String replacement;
            if (fraction.matches("0+")) {
                replacement = whole;
            } else {
                replacement = whole + "," + (fraction.length() == 1 ? fraction + "0" : fraction);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void punctuate(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (!isFlavor(line)) {
                continue;
            }
            if (i + 1 < lore.size() && isContinuation(lore.get(i + 1))) {
                continue;
            }
            lore.set(i, ensureSentence(line));
        }
    }

    private static boolean isFlavor(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String stripped = ChatColor.stripColor(line).trim();
        if (stripped.length() < 12 || stripped.contains(":")) {
            return false;
        }
        if (line.contains("Boosters") || line.contains("Mining Power") || line.contains("Fortune")
                || line.contains("Damage") || line.contains("Defense") || line.contains("Health")
                || line.contains("Spread") || line.contains("Harvest") || line.contains("Fish Speed")
                || line.contains("Fish Catch") || line.contains("Crit") || line.contains("Speed")
                || line.contains("Catch Rate") || line.contains("Hoe Level") || line.contains("Rod Level")
                || stripped.startsWith("●") || stripped.startsWith("◆")) {
            return false;
        }
        return line.trim().startsWith("§7");
    }

    private static boolean isContinuation(String line) {
        if (line == null || !line.trim().startsWith("§7")) {
            return false;
        }
        String stripped = ChatColor.stripColor(line).trim();
        return !stripped.isEmpty() && Character.isLowerCase(stripped.charAt(0));
    }

    private static String ensureSentence(String line) {
        String stripped = ChatColor.stripColor(line).trim();
        if (stripped.endsWith(".") || stripped.endsWith("!") || stripped.endsWith("?") || stripped.endsWith("...")) {
            return line;
        }
        int words = stripped.split("\\s+").length;
        if (words < 3) {
            return line;
        }
        return line + ".";
    }
}
