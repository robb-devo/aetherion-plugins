package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.Component;
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

/**
 * Entry point for item presentation: rarity-colored name + spec layout (docs/ITEM_TOOLTIP_SPEC.md).
 */
public final class ItemPresentation {

    private static final Pattern DECIMAL = Pattern.compile("(?<![\\d.,])(\\d+)[.,](\\d{1,2})(?![\\d])");
    private static final String DECORATION = "✦✧★☆⚝✪";
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
        boolean german = LoreLocale.target(meta);
        applyName(meta, itemId, rarity, german);
        TooltipStyle.apply(meta, rarity);
        if (LoreTemplates.capturing()) {
            return;
        }
        if (meta.hasLore() && meta.getLore() != null) {
            // English is canonical: a German item is taken back to English, laid out, then localized again.
            List<String> english = LoreLocale.canonicalLore(meta);
            List<String> laidOut = polishLore(english, itemId, rarity);
            meta.setLore(LoreLocale.present(meta, itemId, laidOut));
        }
    }

    public static Component coloredName(String plain, String itemId, Rarity rarity) {
        String text = cleanName(plain);
        if (text.isBlank()) {
            return Component.empty();
        }
        if (isGod(itemId)) {
            return rainbow(text);
        }
        Component component = Component.text(text).decoration(TextDecoration.ITALIC, false);
        if (rarity != null) {
            component = component.color(rarity.textColor());
        }
        return component;
    }

    /** Drops decorative glyph runs around a name ({@code ✦✦✦ Worldbite} → {@code Worldbite}). */
    public static String cleanName(String plain) {
        if (plain == null) {
            return "";
        }
        String text = plain.trim();
        int start = 0;
        while (start < text.length() && (DECORATION.indexOf(text.charAt(start)) >= 0 || text.charAt(start) == ' ')) {
            start++;
        }
        int end = text.length();
        while (end > start && (DECORATION.indexOf(text.charAt(end - 1)) >= 0 || text.charAt(end - 1) == ' ')) {
            end--;
        }
        return text.substring(start, end);
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

    private static void applyName(ItemMeta meta, String itemId, Rarity rarity, boolean german) {
        String plain = plainName(meta);
        if (plain.isBlank()) {
            return;
        }
        // names are canonical English too: a German name is mapped back, then localized for the viewer
        String english = LoreLocale.englishName(plain);
        String shown = german ? LoreLocale.germanName(english) : english;
        if (shown == null) {
            shown = english;
        }
        if (rarity == null && !isGod(itemId)) {
            if (!shown.equals(plain)) {
                String legacy = meta.hasDisplayName() ? meta.getDisplayName() : "";
                String codes = legacy.replaceFirst("^((?:§[0-9a-fk-or])*).*$", "$1");
                if (!codes.isEmpty() && ChatColor.stripColor(legacy).equals(plain)
                        && legacy.equals(codes + plain)) {
                    meta.setDisplayName(codes + shown);
                }
            }
            return;
        }
        meta.displayName(coloredName(shown, itemId, rarity));
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
        List<String> cleaned = new ArrayList<>(lore.size());
        for (String line : lore) {
            cleaned.add(reformatNumbers(line));
        }
        DungeonCore.ensureHint(cleaned, itemId);
        return LoreLayout.normalize(cleaned, itemId, rarity);
    }

    /** One number style everywhere: {@code 100.00 → 100}, {@code 1,50 → 1.5}. Thousands separators stay. */
    public static String reformatNumbers(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        Matcher matcher = DECIMAL.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String whole = matcher.group(1);
            String fraction = matcher.group(2).replaceAll("0+$", "");
            String replacement = fraction.isEmpty() ? whole : whole + "." + fraction;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
