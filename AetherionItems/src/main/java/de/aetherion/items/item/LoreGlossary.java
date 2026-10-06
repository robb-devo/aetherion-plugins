package de.aetherion.items.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The one German glossary for item tooltips (stat labels, slot/type labels, rarity words, booster
 * names, fixed chrome words). Loaded from the {@code glossary:} section of {@code lang/de.yml}.
 * Every machine line (stat rows, boosters, type line, rarity footer) is built from here, so one
 * tooltip never mixes English and German labels. English stays canonical in code; this class only
 * maps English terms to German and back.
 */
public final class LoreGlossary {

    private static volatile Data data = new Data();

    private LoreGlossary() {
    }

    private static final class Data {
        final Map<String, String> stat = new HashMap<>();
        final Map<String, String> statRev = new HashMap<>();
        final Map<String, String> slot = new HashMap<>();
        final Map<String, String> slotRev = new HashMap<>();
        final Map<String, String> word = new HashMap<>();
        final Map<String, String> rarity = new HashMap<>();
        final Map<String, String> rarityRev = new HashMap<>();
        final Map<String, String> booster = new HashMap<>();
        final Map<String, String> boosterRev = new HashMap<>();
        final Map<String, String> chrome = new HashMap<>();
        final Map<String, String> chromeRev = new HashMap<>();
    }

    static void load(ConfigurationSection root) {
        Data next = new Data();
        if (root != null) {
            fill(root.getConfigurationSection("stat"), next.stat, next.statRev);
            fill(root.getConfigurationSection("slot"), next.slot, next.slotRev);
            fill(root.getConfigurationSection("word"), next.word, null);
            fill(root.getConfigurationSection("rarity"), next.rarity, next.rarityRev);
            fill(root.getConfigurationSection("booster"), next.booster, next.boosterRev);
            fill(root.getConfigurationSection("chrome"), next.chrome, next.chromeRev);
        }
        data = next;
    }

    static void clear() {
        data = new Data();
    }

    private static void fill(ConfigurationSection section, Map<String, String> forward, Map<String, String> reverse) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            forward.put(key, value);
            if (reverse != null) {
                reverse.putIfAbsent(value, key);
            }
        }
    }

    public static boolean loaded() {
        return !data.stat.isEmpty();
    }

    /* ---------------------------------------------------------- stat labels */

    /** German label for an English stat/property label ({@code Crit Chance}), or null. */
    public static String stat(String english) {
        return english == null ? null : data.stat.get(english);
    }

    public static String statEnglish(String german) {
        return german == null ? null : data.statRev.get(german);
    }

    /* ---------------------------------------------------------- slot / type */

    /**
     * German type line for an English one ({@code Mining Pickaxe}, {@code Helmet · Worldhide Set}).
     * Exact entry first, then part by part ({@code " · "}), then word by word. Null = not fully known.
     */
    public static String slot(String english) {
        if (english == null || english.isBlank()) {
            return null;
        }
        Data d = data;
        String exact = d.slot.get(english);
        if (exact != null) {
            return exact;
        }
        String[] parts = english.split(" · ", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = translatePart(d, parts[i]);
            if (part == null) {
                return null;
            }
            if (i > 0) {
                out.append(" · ");
            }
            out.append(part);
        }
        return out.toString();
    }

    private static String translatePart(Data d, String part) {
        String exact = d.slot.get(part);
        if (exact != null) {
            return exact;
        }
        String[] words = part.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String translated = d.word.get(word);
            if (translated == null) {
                // proper nouns (Aetherion, Worldhide, Saint's, …) are listed in "word" as themselves;
                // an unknown word makes the whole label "not fully known".
                return null;
            }
            if (i > 0) {
                out.append(' ');
            }
            out.append(translated);
        }
        return out.toString();
    }

    /** Reverse of {@link #slot}: English label for a German one, or null. */
    public static String slotEnglish(String german, String knownEnglish) {
        if (german == null) {
            return null;
        }
        if (knownEnglish != null && german.equals(slot(knownEnglish))) {
            return knownEnglish;
        }
        return data.slotRev.get(german);
    }

    /* ---------------------------------------------------------- rarity */

    public static String rarity(String english) {
        return english == null ? null : data.rarity.get(english.toUpperCase(Locale.ROOT));
    }

    public static String rarityEnglish(String german) {
        return german == null ? null : data.rarityRev.get(german);
    }

    /** Title-case rarity word ({@code Common} → {@code Gewöhnlich}), as used in the booster value rows. */
    public static String rarityTitle(String englishTitle) {
        String upper = rarity(englishTitle);
        return upper == null ? null : titleCase(upper);
    }

    /** Reverse of {@link #rarityTitle}: {@code Gewöhnlich} → {@code Common}. */
    public static String rarityTitleEnglish(String germanTitle) {
        if (germanTitle == null) {
            return null;
        }
        String english = rarityEnglish(germanTitle.toUpperCase(Locale.ROOT));
        return english == null ? null : titleCase(english);
    }

    private static String titleCase(String upper) {
        return upper.isEmpty() ? upper : upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
    }

    /* ---------------------------------------------------------- boosters */

    public static String booster(String english) {
        return english == null ? null : data.booster.get(english);
    }

    public static String boosterEnglish(String german) {
        return german == null ? null : data.boosterRev.get(german);
    }

    /* ---------------------------------------------------------- chrome */

    /** Fixed tooltip words: {@code boosters}, {@code fits}. Falls back to the English default. */
    public static String chrome(String key, String english) {
        String value = data.chrome.get(key);
        return value == null ? english : value;
    }

    public static String chromeGerman(String key) {
        return data.chrome.get(key);
    }

    /* ---------------------------------------------------------- self-check */

    /** Terms the code depends on that the glossary does not translate (logged once on load). */
    public static Set<String> missing(Iterable<String> statLabels, Iterable<String> boosterNames, Iterable<String> slotLabels) {
        Data d = data;
        Set<String> out = new TreeSet<>();
        for (String label : statLabels) {
            if (!d.stat.containsKey(label)) {
                out.add("stat." + label);
            }
        }
        for (String name : boosterNames) {
            if (!d.booster.containsKey(name)) {
                out.add("booster." + name);
            }
        }
        for (String rarity : new String[] {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "AETHERED"}) {
            if (!d.rarity.containsKey(rarity)) {
                out.add("rarity." + rarity);
            }
        }
        for (String key : new String[] {"boosters", "fits"}) {
            if (!d.chrome.containsKey(key)) {
                out.add("chrome." + key);
            }
        }
        for (String label : slotLabels) {
            if (label != null && slot(label) == null) {
                out.add("slot." + label);
            }
        }
        return out;
    }

    /** Snapshot for tests / tooling. */
    public static Map<String, String> statTable() {
        return new LinkedHashMap<>(data.stat);
    }
}
