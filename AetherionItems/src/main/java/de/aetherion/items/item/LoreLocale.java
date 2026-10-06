package de.aetherion.items.item;

import de.aetherion.core.api.AetherServices;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.lore.StoryBooks;
import de.aetherion.items.lore.StoryLore;
import de.aetherion.items.model.BoosterType;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * German tooltips. English stays the canonical lore (factories, {@link LoreLayout}, every mutator);
 * German is a last pass over the finished English layout, driven by {@code lang/de.yml}:
 * <ul>
 *   <li><b>glossary</b> ({@link LoreGlossary}): stat labels, type line, rarity word, booster names, chrome;</li>
 *   <li><b>patterns</b>: lines with numbers ({@code Value: {0} coins each});</li>
 *   <li><b>text / flavor</b>: prose, keyed by the English text. Missing key = that line stays English.</li>
 * </ul>
 * Everything is reversible ({@link #toEnglish}), so in-place updates (boosters, levels, dungeon cores)
 * always work on English and the item is re-localized afterwards. A German item carries the PDC tag
 * {@code lore_lang = de}; {@code lore_cache} always holds the English lore.
 */
public final class LoreLocale {

    private static final String TAG_DE = "de";
    private static final String COLORS = "(?:§[0-9a-fk-or])*";

    private static final Pattern ROW = Pattern.compile(
            "^(" + COLORS + ")((?:\\S{1,2} )?)([A-Za-z][A-Za-z ]*?): (" + COLORS + ")([+-]?)(\\d[\\d.,]*)([%s❤x]*)(.*)$");
    private static final Pattern BOOSTER_HEADER = Pattern.compile("^(" + COLORS + ")(\\S+) (" + COLORS + "\\d+" + COLORS + "/\\d+.*)$");
    private static final Pattern BOOSTER_ROW = Pattern.compile("^(" + COLORS + ")● (.+?) (" + COLORS + "x\\d+)$");
    private static final Pattern RARITY_ROW = Pattern.compile(
            "^(" + COLORS + ")(Common|Uncommon|Rare|Epic|Legendary|Mythic|Aethered) (" + COLORS + "\\+[\\d.,]+%?)$");
    private static final Pattern RARITY_ROW_DE = Pattern.compile(
            "^(" + COLORS + ")([A-ZÄÖÜ][a-zäöüß]+) (" + COLORS + "\\+[\\d.,]+%?)$");
    private static final Pattern RARITY_LINE = Pattern.compile("^(" + COLORS + ")([A-ZÄÖÜß]{3,})$");
    private static final Pattern NUMBER_SEP = Pattern.compile("(?<=\\d)[.,](?=\\d)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-zÄÖÜäöüß]");

    private static final ThreadLocal<Boolean> VIEW = new ThreadLocal<>();
    private static final ThreadLocal<int[]> HOLD = ThreadLocal.withInitial(() -> new int[1]);

    private static volatile Tables tables = new Tables();
    private static volatile JavaPlugin plugin;

    private LoreLocale() {
    }

    /* =========================================================
     * TABLES
     * ========================================================= */

    private record Unit(String en, String de) {
    }

    private record Rule(Pattern enRegex, Pattern deRegex, String enTemplate, String deTemplate, int length) {
    }

    private static final class Tables {
        final Map<String, Unit> byEn = new HashMap<>();
        final Map<String, Unit> byDe = new HashMap<>();
        final Map<String, String> nameByEn = new HashMap<>();
        final Map<String, String> nameByDe = new HashMap<>();
        final List<Rule> rules = new ArrayList<>();
        final List<Rule> nameRules = new ArrayList<>();
        final Map<String, List<String>> books = new HashMap<>();
        final Map<String, String> authors = new HashMap<>();
        boolean ready;
    }

    /** Reads {@code lang/de.yml} from the jar. Safe to call again (reload). */
    public static void load(JavaPlugin owner) {
        plugin = owner;
        Tables next = new Tables();
        try (InputStream in = owner.getResource("lang/de.yml")) {
            if (in == null) {
                owner.getLogger().warning("lang/de.yml missing: item tooltips stay English.");
                LoreGlossary.clear();
                tables = next;
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            LoreGlossary.load(yaml.getConfigurationSection("glossary"));
            readUnits(yaml, "text", next);
            readUnits(yaml, "flavor", next);
            readNames(yaml, next);
            readRules(yaml, "patterns", next.rules);
            readRules(yaml, "name_patterns", next.nameRules);
            readBooks(yaml, next);
            next.ready = LoreGlossary.loaded();
        } catch (Exception ex) {
            owner.getLogger().warning("lang/de.yml could not be read: " + ex.getMessage());
            LoreGlossary.clear();
            next = new Tables();
        }
        tables = next;
        audit(owner);
    }

    public static void shutdown() {
        tables = new Tables();
        LoreGlossary.clear();
        plugin = null;
    }

    public static boolean ready() {
        return tables.ready;
    }

    private static void readUnits(YamlConfiguration yaml, String path, Tables out) {
        for (Map<?, ?> entry : yaml.getMapList(path)) {
            String en = str(entry.get("en"));
            String de = str(entry.get("de"));
            if (en == null || de == null) {
                continue;
            }
            String key = key(en);
            if (key.isEmpty()) {
                continue;
            }
            Unit unit = new Unit(en, de);
            out.byEn.putIfAbsent(key, unit);
            out.byDe.putIfAbsent(key(de), unit);
        }
    }

    private static void readNames(YamlConfiguration yaml, Tables out) {
        for (Map<?, ?> entry : yaml.getMapList("names")) {
            String en = str(entry.get("en"));
            String de = str(entry.get("de"));
            if (en == null || de == null) {
                continue;
            }
            out.nameByEn.putIfAbsent(key(en), key(de));
            out.nameByDe.putIfAbsent(key(de), key(en));
        }
    }

    private static void readBooks(YamlConfiguration yaml, Tables out) {
        ConfigurationSection books = yaml.getConfigurationSection("books");
        if (books != null) {
            for (String id : books.getKeys(false)) {
                List<String> pages = books.getStringList(id);
                if (!pages.isEmpty()) {
                    out.books.put(id, new ArrayList<>(pages));
                }
            }
        }
        ConfigurationSection authors = yaml.getConfigurationSection("authors");
        if (authors != null) {
            for (String en : authors.getKeys(false)) {
                String de = authors.getString(en);
                if (de != null && !de.isBlank()) {
                    out.authors.put(en, de);
                }
            }
        }
    }

    private static void readRules(YamlConfiguration yaml, String path, List<Rule> into) {
        for (Map<?, ?> entry : yaml.getMapList(path)) {
            String en = str(entry.get("en"));
            String de = str(entry.get("de"));
            if (en == null || de == null) {
                continue;
            }
            try {
                into.add(new Rule(compile(en), compile(de), en, de, key(en).length()));
            } catch (RuntimeException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("lang/de.yml pattern skipped: " + en);
                }
            }
        }
        // most specific (longest literal) first
        into.sort(Comparator.comparingInt(Rule::length).reversed());
    }

    private static Pattern compile(String template) {
        String plain = key(template);
        StringBuilder regex = new StringBuilder("^");
        Matcher matcher = PLACEHOLDER.matcher(plain);
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(plain.substring(last, matcher.start())));
            regex.append("(.+?)");
            last = matcher.end();
        }
        regex.append(Pattern.quote(plain.substring(last))).append('$');
        return Pattern.compile(regex.toString());
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Lookup key: color codes stripped, whitespace collapsed. */
    static String key(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.stripColor(text).replaceAll("\\s+", " ").trim();
    }

    /* =========================================================
     * VIEW SCOPE + TAG
     * ========================================================= */

    /** Restores the previous viewer scope and hold level on {@link #close()}. */
    public static final class Scope implements AutoCloseable {
        private final Boolean previousView;
        private final int previousHold;

        private Scope(Boolean view, Boolean hold) {
            previousView = VIEW.get();
            previousHold = HOLD.get()[0];
            if (view != null) {
                VIEW.set(view);
            }
            if (hold != null) {
                HOLD.get()[0] = hold ? previousHold + 1 : 0;
            }
        }

        @Override
        public void close() {
            if (previousView == null) {
                VIEW.remove();
            } else {
                VIEW.set(previousView);
            }
            HOLD.get()[0] = previousHold;
        }
    }

    public static boolean german(Player player) {
        return ready() && AetherServices.german(player);
    }

    /** Items made or touched inside this scope are written in {@code player}'s language. */
    public static Scope viewing(Player player) {
        return new Scope(german(player), null);
    }

    /** Forces English (factory capture, template reads). */
    public static Scope english() {
        return new Scope(Boolean.FALSE, null);
    }

    /** Keeps the scope but sets the language explicitly; {@code hold} postpones localizing until a finish. */
    public static Scope scope(Boolean german, boolean hold) {
        return new Scope(german, hold);
    }

    /** The viewer scope if one is open, otherwise null. */
    public static Boolean scopedGerman() {
        return VIEW.get();
    }

    public static boolean holding() {
        return HOLD.get()[0] > 0;
    }

    public static boolean tagged(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        return TAG_DE.equals(meta.getPersistentDataContainer().get(ItemKeys.loreLang(), PersistentDataType.STRING));
    }

    public static boolean tagged(ItemStack item) {
        return item != null && item.hasItemMeta() && tagged(item.getItemMeta());
    }

    public static void setTag(ItemMeta meta, boolean german) {
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (german) {
            pdc.set(ItemKeys.loreLang(), PersistentDataType.STRING, TAG_DE);
        } else {
            pdc.remove(ItemKeys.loreLang());
        }
    }

    /** German when a scope says so, otherwise whatever the item already is. */
    public static boolean target(ItemMeta meta) {
        Boolean scoped = VIEW.get();
        boolean wanted = scoped != null ? scoped : tagged(meta);
        return wanted && ready();
    }

    /* =========================================================
     * LORE / NAME ENTRY POINTS
     * ========================================================= */

    /** English lore of {@code meta}, whatever language it currently shows. */
    public static List<String> canonicalLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return null;
        }
        List<String> lore = new ArrayList<>(meta.getLore());
        if (!tagged(meta)) {
            return lore;
        }
        return toEnglish(lore, itemId(meta));
    }

    /** {@link #canonicalLore} as a fresh, never-null list. */
    public static List<String> loreOrEmpty(ItemMeta meta) {
        List<String> lore = canonicalLore(meta);
        return lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    /** Puts the item back to English lore (no-op when it already is). */
    public static void canonicalize(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (canonicalize(meta)) {
            item.setItemMeta(meta);
        }
    }

    public static boolean canonicalize(ItemMeta meta) {
        if (meta == null || !tagged(meta)) {
            return false;
        }
        List<String> en = canonicalLore(meta);
        meta.setLore(en);
        setTag(meta, false);
        return true;
    }

    /**
     * Last step of every tooltip write: the finished English lore becomes what this item should show
     * (German or English) and the tag follows. While {@link #holding()} the English lore is returned untouched.
     */
    public static List<String> present(ItemMeta meta, String itemId, List<String> english) {
        if (english == null || holding()) {
            return english;
        }
        boolean german = target(meta);
        boolean was = tagged(meta);
        setTag(meta, german);
        if (german || was) {
            localizeBook(meta, german);
        }
        return german ? toGerman(english, itemId) : english;
    }

    /** Readable story finds carry their pages, title and author in the book itself. */
    private static void localizeBook(ItemMeta meta, boolean german) {
        if (!(meta instanceof BookMeta book)) {
            return;
        }
        String findId = meta.getPersistentDataContainer().get(StoryLore.FIND_ID, PersistentDataType.STRING);
        StoryLore lore = findId == null ? null : StoryLore.byId(findId);
        if (lore == null) {
            return;
        }
        Tables t = tables;
        List<String> pages = german ? t.books.get(lore.findId()) : null;
        book.setPages(pages != null ? pages : StoryBooks.pages(lore.findId()));
        String title = german ? t.nameByEn.get(key(lore.displayName())) : null;
        if (title == null) {
            title = lore.displayName();
        }
        book.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
        String author = lore.authorEnglish();
        String authorDe = german ? t.authors.get(author) : null;
        book.setAuthor(authorDe != null ? authorDe : author);
    }

    /** Writes English lore through the language layer (German items stay German). */
    public static void writeLore(ItemMeta meta, List<String> english) {
        if (meta == null) {
            return;
        }
        meta.setLore(present(meta, itemId(meta), english));
    }

    private static String itemId(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }

    /** One coloured English line in German (when {@code german} and the table knows it), else unchanged. */
    public static String translateLine(String coloredEnglish, boolean german) {
        if (!german || coloredEnglish == null || !ready()) {
            return coloredEnglish;
        }
        Unit unit = tables.byEn.get(key(coloredEnglish));
        return unit == null ? coloredEnglish : unit.de();
    }

    /** German display name for an English plain name, or null when there is none. */
    public static String germanName(String englishPlain) {
        Tables t = tables;
        String exact = t.nameByEn.get(key(englishPlain));
        if (exact != null) {
            return exact;
        }
        String plain = key(englishPlain);
        for (Rule rule : t.nameRules) {
            Matcher m = rule.enRegex().matcher(plain);
            if (m.matches()) {
                return key(fill(rule.deTemplate(), m, true));
            }
        }
        return null;
    }

    /** English plain name for a German one; unknown names come back unchanged. */
    public static String englishName(String plain) {
        Tables t = tables;
        String english = t.nameByDe.get(key(plain));
        if (english != null) {
            return english;
        }
        String clean = key(plain);
        for (Rule rule : t.nameRules) {
            Matcher m = rule.deRegex().matcher(clean);
            if (m.matches()) {
                return key(fill(rule.enTemplate(), m, false));
            }
        }
        return plain;
    }

    /* =========================================================
     * ENGLISH → GERMAN
     * ========================================================= */

    public static List<String> toGerman(List<String> english, String itemId) {
        if (english == null || !ready()) {
            return english;
        }
        Tables t = tables;
        List<String> out = new ArrayList<>(english.size() + 4);
        String typeEn = ItemSlotLabel.plain(itemId);
        int i = 0;
        int n = english.size();
        while (i < n) {
            String line = english.get(i);
            if (line == null || line.isBlank()) {
                out.add("");
                i++;
                continue;
            }
            if (i == 0 && typeEn != null && key(line).equals(typeEn)) {
                String de = LoreGlossary.slot(typeEn);
                out.add(de == null ? line : "§8" + de);
                i++;
                continue;
            }
            if (isFits(line)) {
                int end = i;
                List<String> names = new ArrayList<>();
                while (end < n && english.get(end) != null && isFits(english.get(end))) {
                    names.addAll(fitsNames(english.get(end)));
                    end++;
                }
                out.addAll(germanFits(names));
                i = end;
                continue;
            }
            if (line.startsWith(LoreLayout.FLAVOR) || line.startsWith(LoreLayout.ORIGIN)) {
                String prefix = line.startsWith(LoreLayout.FLAVOR) ? LoreLayout.FLAVOR : LoreLayout.ORIGIN;
                int end = i;
                while (end < n && english.get(end) != null && english.get(end).startsWith(prefix)) {
                    end++;
                }
                i = flavorToGerman(english, i, end, prefix, t, out);
                continue;
            }
            String single = machineToGerman(line);
            if (single != null) {
                out.add(single);
                i++;
                continue;
            }
            // prose group: longest run of plain lines first
            int limit = i;
            while (limit + 1 < n && limit - i < 6 && opaqueFree(english.get(limit + 1), t)) {
                limit++;
            }
            boolean done = false;
            for (int j = limit; j > i; j--) {
                Unit unit = t.byEn.get(joined(english, i, j));
                if (unit != null) {
                    emit(out, unit.de());
                    i = j + 1;
                    done = true;
                    break;
                }
            }
            if (done) {
                continue;
            }
            Unit unit = t.byEn.get(key(line));
            if (unit != null) {
                emit(out, unit.de());
                i++;
                continue;
            }
            String ruled = ruleToGerman(line, t);
            if (ruled != null) {
                emit(out, ruled);
                i++;
                continue;
            }
            String listed = translateParts(line, true, t);
            out.add(listed != null ? listed : line);
            i++;
        }
        return out;
    }

    /**
     * Flavor/origin block {@code [from, to)}: greedy, longest run of (wrapped) lines first, so a block of
     * several sentences translates sentence by sentence. A run without an entry stays English line by line.
     */
    private static int flavorToGerman(List<String> lines, int from, int to, String prefix, Tables t, List<String> out) {
        int i = from;
        while (i < to) {
            boolean done = false;
            for (int j = Math.min(to - 1, i + 7); j >= i; j--) {
                Unit unit = t.byEn.get(joined(lines, i, j));
                if (unit != null) {
                    out.addAll(LoreLayout.wrap(prefix + key(unit.de())));
                    i = j + 1;
                    done = true;
                    break;
                }
            }
            if (!done) {
                out.add(lines.get(i));
                i++;
            }
        }
        return to;
    }

    private static int flavorToEnglish(List<String> lines, int from, int to, String prefix, Tables t, List<String> out) {
        int i = from;
        while (i < to) {
            boolean done = false;
            for (int j = Math.min(to - 1, i + 7); j >= i; j--) {
                Unit unit = t.byDe.get(joined(lines, i, j));
                if (unit != null) {
                    out.addAll(LoreLayout.wrap(prefix + key(unit.en())));
                    i = j + 1;
                    done = true;
                    break;
                }
            }
            if (!done) {
                out.add(lines.get(i));
                i++;
            }
        }
        return to;
    }

    /**
     * A line that is only names joined by {@code " · "} ({@code Tank · Assassin · Soldier}): every part is
     * translated through the names table and the colour codes stay where they were. Null when any part is unknown.
     */
    private static String translateParts(String line, boolean toGerman, Tables t) {
        String plain = ChatColor.stripColor(line);
        if (plain == null || !plain.contains(" · ")) {
            return null;
        }
        Map<String, String> names = toGerman ? t.nameByEn : t.nameByDe;
        String[] parts = plain.split(" · ", -1);
        List<String> translated = new ArrayList<>();
        for (String part : parts) {
            String hit = names.get(key(part));
            if (hit == null) {
                return null;
            }
            translated.add(hit);
        }
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (int k = 0; k < parts.length; k++) {
            String part = parts[k].trim();
            int at = line.indexOf(part, from);
            if (at < 0) {
                return null;
            }
            out.append(line, from, at).append(translated.get(k));
            from = at + part.length();
        }
        out.append(line.substring(from));
        return out.toString();
    }

    private static boolean opaqueFree(String line, Tables t) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (isFits(line) || line.startsWith(LoreLayout.FLAVOR) || line.startsWith(LoreLayout.ORIGIN)) {
            return false;
        }
        if (machineToGerman(line) != null) {
            return false;
        }
        return ruleToGerman(line, t) == null;
    }

    private static String joined(List<String> lines, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int k = from; k <= to; k++) {
            if (k > from) {
                out.append(' ');
            }
            out.append(key(lines.get(k)));
        }
        return out.toString();
    }

    private static void emit(List<String> out, String colored) {
        for (String part : colored.split("\n")) {
            out.addAll(LoreLayout.wrap(part));
        }
    }

    /** Stat rows, booster header/rows, rarity footer. Null when the line is none of these. */
    private static String machineToGerman(String line) {
        String plain = key(line);
        Matcher header = BOOSTER_HEADER.matcher(line);
        if (plain.startsWith("Boosters ") && header.matches() && header.group(2).equals("Boosters")) {
            return header.group(1) + LoreGlossary.chrome("boosters", "Boosters") + " " + header.group(3);
        }
        Matcher row = BOOSTER_ROW.matcher(line);
        if (row.matches()) {
            String de = LoreGlossary.booster(row.group(2));
            if (de != null) {
                return row.group(1) + "● " + de + " " + row.group(3);
            }
        }
        Matcher stat = ROW.matcher(line);
        if (stat.matches() && !HAS_LETTER.matcher(stat.group(8)).find()) {
            String de = LoreGlossary.stat(stat.group(3));
            if (de != null) {
                return stat.group(1) + stat.group(2) + de + ": " + stat.group(4) + stat.group(5)
                        + swap(stat.group(6)) + stat.group(7) + swap(stat.group(8));
            }
        }
        Matcher rarity = RARITY_LINE.matcher(line);
        if (rarity.matches() && line.contains("§l")) {
            String de = LoreGlossary.rarity(rarity.group(2));
            if (de != null) {
                return rarity.group(1) + de;
            }
        }
        Matcher rarityRow = RARITY_ROW.matcher(line);
        if (rarityRow.matches()) {
            String de = LoreGlossary.rarityTitle(rarityRow.group(2));
            if (de != null) {
                return rarityRow.group(1) + de + " " + swap(rarityRow.group(3));
            }
        }
        return null;
    }

    private static String ruleToGerman(String line, Tables t) {
        String plain = key(line);
        for (Rule rule : t.rules) {
            Matcher m = rule.enRegex().matcher(plain);
            if (m.matches()) {
                return withLead(fill(rule.deTemplate(), m, true), line);
            }
        }
        return null;
    }

    /** A pattern template without its own leading colour keeps the colour the source line started with. */
    private static String withLead(String template, String source) {
        if (template.startsWith("§")) {
            return template;
        }
        Matcher lead = Pattern.compile("^(" + COLORS + ")").matcher(source);
        return lead.find() ? lead.group(1) + template : template;
    }

    /** Fills {@code {n}} holes: known names are translated, numbers swap their separators. */
    private static String fill(String template, Matcher groups, boolean toGerman) {
        Tables t = tables;
        Matcher holes = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (holes.find()) {
            out.append(template, last, holes.start());
            int index = Integer.parseInt(holes.group(1)) + 1;
            if (index <= groups.groupCount()) {
                String raw = groups.group(index);
                String named = (toGerman ? t.nameByEn : t.nameByDe).get(key(raw));
                out.append(named != null ? named : swap(raw));
            } else {
                out.append(holes.group());
            }
            last = holes.end();
        }
        out.append(template.substring(last));
        return out.toString();
    }

    /** {@code 1,234.5} ↔ {@code 1.234,5}. The same call converts both ways. */
    static String swap(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = NUMBER_SEP.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, m.group().equals(".") ? "," : ".");
        }
        m.appendTail(out);
        return out.toString();
    }

    /* =========================================================
     * GERMAN → ENGLISH
     * ========================================================= */

    /** Reverses {@link #toGerman}. Lines it does not know are already English and stay as they are. */
    public static List<String> toEnglish(List<String> lore, String itemId) {
        if (lore == null || !ready()) {
            return lore;
        }
        Tables t = tables;
        List<String> out = new ArrayList<>(lore.size() + 4);
        String typeEn = ItemSlotLabel.plain(itemId);
        int i = 0;
        int n = lore.size();
        while (i < n) {
            String line = lore.get(i);
            if (line == null || line.isBlank()) {
                out.add("");
                i++;
                continue;
            }
            if (i == 0 && typeEn != null) {
                String de = LoreGlossary.slot(typeEn);
                if (de != null && key(line).equals(de)) {
                    out.add("§8" + typeEn);
                    i++;
                    continue;
                }
            }
            if (isFitsGerman(line)) {
                int end = i;
                List<String> names = new ArrayList<>();
                while (end < n && lore.get(end) != null && isFitsGerman(lore.get(end))) {
                    names.addAll(fitsNamesGerman(lore.get(end)));
                    end++;
                }
                out.addAll(ItemLore.fitsLines(names, "Fits"));
                i = end;
                continue;
            }
            if (line.startsWith(LoreLayout.FLAVOR) || line.startsWith(LoreLayout.ORIGIN)) {
                String prefix = line.startsWith(LoreLayout.FLAVOR) ? LoreLayout.FLAVOR : LoreLayout.ORIGIN;
                int end = i;
                while (end < n && lore.get(end) != null && lore.get(end).startsWith(prefix)) {
                    end++;
                }
                i = flavorToEnglish(lore, i, end, prefix, t, out);
                continue;
            }
            String single = machineToEnglish(line);
            if (single != null) {
                out.add(single);
                i++;
                continue;
            }
            int limit = i;
            while (limit + 1 < n && limit - i < 8 && opaqueFreeGerman(lore.get(limit + 1), t)) {
                limit++;
            }
            boolean done = false;
            for (int j = limit; j > i; j--) {
                Unit unit = t.byDe.get(joined(lore, i, j));
                if (unit != null) {
                    emitEnglish(out, unit.en());
                    i = j + 1;
                    done = true;
                    break;
                }
            }
            if (done) {
                continue;
            }
            Unit unit = t.byDe.get(key(line));
            if (unit != null) {
                emitEnglish(out, unit.en());
                i++;
                continue;
            }
            String ruled = ruleToEnglish(line, t);
            if (ruled != null) {
                emitEnglish(out, ruled);
                i++;
                continue;
            }
            String listed = translateParts(line, false, t);
            out.add(listed != null ? listed : line);
            i++;
        }
        return out;
    }

    private static boolean opaqueFreeGerman(String line, Tables t) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (isFitsGerman(line) || line.startsWith(LoreLayout.FLAVOR) || line.startsWith(LoreLayout.ORIGIN)) {
            return false;
        }
        if (machineToEnglish(line) != null) {
            return false;
        }
        return ruleToEnglish(line, t) == null;
    }

    private static void emitEnglish(List<String> out, String colored) {
        for (String part : colored.split("\n")) {
            out.add(part);
        }
    }

    private static String machineToEnglish(String line) {
        String plain = key(line);
        String boosters = LoreGlossary.chromeGerman("boosters");
        if (boosters != null && plain.startsWith(boosters + " ")) {
            Matcher header = BOOSTER_HEADER.matcher(line);
            if (header.matches() && header.group(2).equals(boosters)) {
                return header.group(1) + "Boosters " + header.group(3);
            }
        }
        Matcher row = BOOSTER_ROW.matcher(line);
        if (row.matches()) {
            String en = LoreGlossary.boosterEnglish(row.group(2));
            if (en != null) {
                return row.group(1) + "● " + en + " " + row.group(3);
            }
        }
        Matcher stat = ROW.matcher(line);
        if (stat.matches() && !HAS_LETTER.matcher(stat.group(8)).find()) {
            String en = LoreGlossary.statEnglish(stat.group(3));
            if (en != null) {
                return stat.group(1) + stat.group(2) + en + ": " + stat.group(4) + stat.group(5)
                        + swap(stat.group(6)) + stat.group(7) + swap(stat.group(8));
            }
        }
        Matcher rarity = RARITY_LINE.matcher(line);
        if (rarity.matches() && line.contains("§l")) {
            String en = LoreGlossary.rarityEnglish(rarity.group(2));
            if (en != null) {
                return rarity.group(1) + en;
            }
        }
        Matcher rarityRow = RARITY_ROW_DE.matcher(line);
        if (rarityRow.matches()) {
            String en = LoreGlossary.rarityTitleEnglish(rarityRow.group(2));
            if (en != null) {
                return rarityRow.group(1) + en + " " + swap(rarityRow.group(3));
            }
        }
        return null;
    }

    private static String ruleToEnglish(String line, Tables t) {
        String plain = key(line);
        for (Rule rule : t.rules) {
            Matcher m = rule.deRegex().matcher(plain);
            if (m.matches()) {
                return withLead(fill(rule.enTemplate(), m, false), line);
            }
        }
        return null;
    }

    /* =========================================================
     * BOOSTER "FITS" LINES
     * ========================================================= */

    private static boolean isFits(String line) {
        String plain = key(line);
        if (!(plain.startsWith("Fits:") || plain.startsWith("·"))) {
            return false;
        }
        List<String> names = fitsNames(line);
        if (names.isEmpty()) {
            return false;
        }
        for (String name : names) {
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
        }
        return true;
    }

    private static List<String> fitsNames(String line) {
        String plain = key(line);
        String body = plain.startsWith("Fits:") ? plain.substring(5) : plain.startsWith("·") ? plain.substring(1) : plain;
        List<String> names = new ArrayList<>();
        for (String part : body.split("·")) {
            if (!part.trim().isEmpty()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    private static boolean isFitsGerman(String line) {
        String word = LoreGlossary.chromeGerman("fits");
        String plain = key(line);
        if (word == null || !(plain.startsWith(word + ":") || plain.startsWith("·"))) {
            return false;
        }
        List<String> names = fitsNamesGerman(line);
        if (names.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (LoreGlossary.boosterEnglish(name) == null) {
                return false;
            }
        }
        return true;
    }

    private static List<String> fitsNamesGerman(String line) {
        String word = LoreGlossary.chromeGerman("fits");
        String plain = key(line);
        String body = word != null && plain.startsWith(word + ":") ? plain.substring(word.length() + 1)
                : plain.startsWith("·") ? plain.substring(1) : plain;
        List<String> names = new ArrayList<>();
        for (String part : body.split("·")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            String english = LoreGlossary.boosterEnglish(name);
            names.add(english == null ? name : english);
        }
        return names;
    }

    private static List<String> germanFits(List<String> englishNames) {
        List<String> german = new ArrayList<>();
        for (String name : englishNames) {
            String de = LoreGlossary.booster(name);
            german.add(de == null ? name : de);
        }
        return ItemLore.fitsLines(german, LoreGlossary.chrome("fits", "Fits"));
    }

    /* =========================================================
     * SELF-CHECK
     * ========================================================= */

    private static void audit(JavaPlugin owner) {
        if (!tables.ready) {
            return;
        }
        List<String> stats = new ArrayList<>();
        for (ItemLore.StatLine line : ItemLore.StatLine.values()) {
            stats.add(line.label());
        }
        List<String> boosters = new ArrayList<>();
        for (BoosterType type : BoosterType.values()) {
            boosters.add(type.displayName());
        }
        List<String> slots = new ArrayList<>();
        for (de.aetherion.items.model.ItemProfile profile : de.aetherion.items.model.ItemProfile.values()) {
            String label = ItemSlotLabel.plain(profile.getItemId());
            if (label != null) {
                slots.add(label);
            }
        }
        java.util.Set<String> missing = LoreGlossary.missing(stats, boosters, slots);
        Tables t = tables;
        owner.getLogger().info("Item tooltips: German ready (" + t.byEn.size() + " lines, " + t.rules.size()
                + " patterns, " + t.nameByEn.size() + " names, " + missing.size() + " glossary gaps).");
        if (!missing.isEmpty()) {
            owner.getLogger().warning("lang/de.yml glossary gaps (those labels stay English): " + missing);
        }
    }
}
