package de.aetherion.items.item;

import de.aetherion.items.model.Rarity;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The tooltip spine (docs/ITEM_TOOLTIP_SPEC.md). Every Aetherion lore goes through {@link #normalize}.
 * <p>
 * Lore is read as <b>paragraphs</b> (blank-line separated, the way factories author it). Machine lines
 * (stats, boosters, levels, dungeon state, value, rarity) are lifted out and placed in their fixed slot;
 * everything else stays together in authored order, so an ability header never loses its text.
 * The output is idempotent: normalizing a normalized lore changes nothing.
 * <pre>
 * Type
 * (mark)
 *
 * Stats (fixed order) + properties
 *
 * Flavor (§7§o) + origin (§8§o)
 *
 * Ability / body blocks (authored order)
 *
 * Boosters
 *
 * Progress + dungeon state
 *
 * Value
 * RARITY
 * </pre>
 */
public final class LoreLayout {

    /** Visible characters per line before prose wraps. */
    public static final int WRAP = 46;

    public static final String FLAVOR = "§7§o";
    public static final String ORIGIN = "§8§o";
    public static final String HEADER = "§6";

    private static final Pattern RARITY_FOOTER = Pattern.compile(
            "^(?:✦\\s*)?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|AETHERED|AETHERION|GOD)(?:\\s+[A-Z0-9][A-Z0-9 ]*)?(?:\\s*·.*)?$"
    );
    private static final Pattern PROPERTY = Pattern.compile(
            "^(?:\\S{1,2}\\s+)?(Fire Rate|Throw|Life Absorption|Heal|Draw|Volley|Lightning|Fire|Frost):.*$"
    );
    private static final Pattern PURE_PROSE_BLOCKER = Pattern.compile(
            "(?i).*(\\d|%|:|right-click|left-click|click|sneak|hold right|heal|damage|crit|chance|"
                    + "dungeon|core|craft|place|consum|needs|arrows|cooldown|blocks|full set|set bonus|"
                    + "equip|stats|catch rate|sells|coins|auto-|absorption|reflect|identify).*"
    );

    private LoreLayout() {
    }

    public static List<String> normalize(List<String> lore, String itemId) {
        return normalize(lore, itemId, null);
    }

    public static List<String> normalize(List<String> lore, String itemId, Rarity rarity) {
        if (lore == null) {
            return null;
        }
        String typeLabel = ItemSlotLabel.plain(itemId);
        String typeLine = typeLabel == null ? null : "§8" + typeLabel;
        String curated = ItemFlavor.curatedFor(itemId);
        String generic = ItemFlavor.jokeFor(itemId);
        String curatedPlain = curated == null ? null : plain(curated);
        String genericPlain = generic == null ? null : plain(generic);

        List<String> marks = new ArrayList<>();
        List<String> stats = new ArrayList<>();
        List<String> props = new ArrayList<>();
        List<String> flavor = new ArrayList<>();
        List<String> origin = new ArrayList<>();
        List<String> boosters = new ArrayList<>();
        List<String> progress = new ArrayList<>();
        List<String> dungeon = new ArrayList<>();
        List<String> value = new ArrayList<>();
        List<List<String>> blocks = new ArrayList<>();

        List<String> paragraph = new ArrayList<>();
        List<String> input = new ArrayList<>(lore);
        input.add("");
        for (String raw : input) {
            String line = raw == null ? "" : stripTrailing(raw);
            if (line.isBlank()) {
                flushBlock(blocks, paragraph);
                continue;
            }
            if (isRarityFooter(line)) {
                continue;
            }
            if (typeLine != null && line.equals(typeLine)) {
                continue;
            }
            if (isTypeLabelLine(line)) {
                continue;
            }
            if (isMarkLine(line)) {
                addUnique(marks, line);
                continue;
            }
            if (isValueLine(line)) {
                addUnique(value, line);
                continue;
            }
            if (ItemLore.isBoosterLine(line)) {
                boosters.add(line);
                continue;
            }
            if (ItemLore.isStatLine(line)) {
                addUniqueStat(stats, line);
                continue;
            }
            if (isDungeonStatPreview(line)) {
                continue;
            }
            if (isPropertyLine(line)) {
                addUnique(props, line);
                continue;
            }
            if (isLevelLine(line)) {
                addUnique(progress, line);
                continue;
            }
            if (isDungeonLine(line)) {
                addUnique(dungeon, line);
                continue;
            }
            if (line.startsWith(FLAVOR)) {
                if (curated == null) {
                    addUnique(flavor, line);
                }
                continue;
            }
            if (line.startsWith(ORIGIN)) {
                addUnique(origin, line);
                continue;
            }
            String linePlain = plain(line);
            if ((curatedPlain != null && curatedPlain.equals(linePlain))
                    || (genericPlain != null && genericPlain.equals(linePlain))) {
                continue;
            }
            paragraph.add(line);
        }

        // Flavor: curated line > the item's own quip > generic set line. Never more than two lines.
        if (curated != null) {
            flavor.clear();
            flavor.addAll(wrap(FLAVOR + plain(curated)));
            blocks.removeIf(LoreLayout::isPureProse);
        } else if (flavor.isEmpty()) {
            int quip = firstPureProse(blocks);
            if (quip >= 0) {
                List<String> lines = blocks.remove(quip);
                String first = plain(lines.get(0)).trim();
                String text = lines.size() > 1 && first.length() + plain(lines.get(1)).trim().length() <= 48
                        ? first + " " + plain(lines.get(1)).trim()
                        : first;
                flavor.addAll(wrap(FLAVOR + text));
            } else if (generic != null) {
                flavor.addAll(wrap(FLAVOR + plain(generic)));
            }
        }
        // Origin: a lone dark-gray line closing the authored lore ("Torn from the Pathwarden").
        if (origin.isEmpty() && !blocks.isEmpty()) {
            List<String> last = blocks.get(blocks.size() - 1);
            if (last.size() == 1 && isOriginCandidate(last.get(0))) {
                origin.add(ORIGIN + plain(last.get(0)));
                blocks.remove(blocks.size() - 1);
            }
        }

        List<List<String>> shaped = new ArrayList<>();
        for (List<String> block : blocks) {
            List<String> body = new ArrayList<>(block);
            List<String> out = new ArrayList<>();
            if (!body.isEmpty() && isAbilityHeader(body.get(0))) {
                out.add(header(body.remove(0)));
            }
            out.addAll(flow(body));
            if (!out.isEmpty()) {
                shaped.add(out);
            }
        }

        stats.sort((a, b) -> {
            ItemLore.ParsedStat pa = ItemLore.parseStat(a);
            ItemLore.ParsedStat pb = ItemLore.parseStat(b);
            return Integer.compare(pa == null ? 99 : pa.stat().ordinal(), pb == null ? 99 : pb.stat().ordinal());
        });

        List<String> out = new ArrayList<>();
        if (typeLine != null) {
            out.add(typeLine);
        }
        out.addAll(marks);
        List<String> statBlock = new ArrayList<>(stats);
        statBlock.addAll(props);
        appendBlock(out, statBlock);
        List<String> flavorBlock = new ArrayList<>(flavor);
        flavorBlock.addAll(origin);
        appendBlock(out, flavorBlock);
        for (List<String> block : shaped) {
            appendBlock(out, block);
        }
        appendBlock(out, boosters);
        List<String> state = new ArrayList<>(progress);
        state.addAll(dungeon);
        appendBlock(out, state);
        List<String> footer = new ArrayList<>(value);
        String rarityLine = rarityFooterLine(rarity, itemId, typeLabel);
        if (rarityLine != null) {
            footer.add(rarityLine);
        }
        appendBlock(out, footer);
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /* =========================================================
     * FOOTER
     * ========================================================= */

    public static String rarityFooterLine(Rarity rarity, String itemId, String categoryPlain) {
        if (ItemPresentation.isGod(itemId)) {
            return "§c§lGOD";
        }
        if (rarity == null) {
            return null;
        }
        return rarity.getChatColor() + "§l" + rarity.name();
    }

    public static int indexBeforeValue(List<String> lore) {
        if (lore == null) {
            return -1;
        }
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (isValueLine(line) || isRarityFooter(line)) {
                return i > 0 && lore.get(i - 1) != null && lore.get(i - 1).isBlank() ? i - 1 : i;
            }
        }
        return -1;
    }

    /* =========================================================
     * BLOCK SHAPING
     * ========================================================= */

    private static void flushBlock(List<List<String>> blocks, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new ArrayList<>(paragraph));
            paragraph.clear();
        }
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
        String needle = plain(line);
        for (String existing : block) {
            if (needle.equals(plain(existing))) {
                return;
            }
        }
        block.add(line);
    }

    private static void addUniqueStat(List<String> stats, String line) {
        ItemLore.ParsedStat parsed = ItemLore.parseStat(line);
        for (String existing : stats) {
            ItemLore.ParsedStat other = ItemLore.parseStat(existing);
            if (other != null && parsed != null && other.stat() == parsed.stat()) {
                return;
            }
        }
        stats.add(line);
    }

    private static int firstPureProse(List<List<String>> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            if (isPureProse(blocks.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Wraps a block. When a line overflows, the spill moves into the next line if that line continues
     * the sentence (starts lowercase), so authored line breaks never leave a lonely word behind.
     */
    static List<String> flow(List<String> lines) {
        List<String> out = new ArrayList<>();
        List<String> queue = new ArrayList<>(lines);
        for (int i = 0; i < queue.size(); i++) {
            String line = queue.get(i);
            if (visibleLength(line) <= WRAP) {
                out.add(line);
                continue;
            }
            List<String> wrapped = wrap(line);
            String spill = wrapped.get(wrapped.size() - 1);
            if (wrapped.size() > 1 && i + 1 < queue.size() && isContinuation(queue.get(i + 1))) {
                out.addAll(wrapped.subList(0, wrapped.size() - 1));
                queue.set(i + 1, spill + " " + queue.get(i + 1).replaceFirst("^((?:§[0-9a-fk-or])*)\\s+", "$1"));
            } else {
                out.addAll(wrapped);
            }
        }
        return out;
    }

    private static boolean isContinuation(String line) {
        String plain = plain(line).trim();
        return !plain.isEmpty() && Character.isLowerCase(plain.charAt(0));
    }

    /** A block that is only a quip: short prose, no numbers, no mechanics words, no header. */
    private static boolean isPureProse(List<String> block) {
        if (block == null || block.isEmpty() || block.size() > 3) {
            return false;
        }
        if (isAbilityHeader(block.get(0))) {
            return false;
        }
        for (String line : block) {
            String plain = plain(line).trim();
            if (plain.length() < 8 || PURE_PROSE_BLOCKER.matcher(plain).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOriginCandidate(String line) {
        if (line == null || !line.trim().startsWith("§8")) {
            return false;
        }
        String plain = plain(line).trim();
        if (plain.length() < 6 || plain.length() > 48 || plain.contains(":") || plain.matches(".*\\d.*")) {
            return false;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        return !(lower.startsWith("click") || lower.startsWith("right-click") || lower.startsWith("left-click")
                || lower.startsWith("hold") || lower.startsWith("sneak") || lower.startsWith("place")
                || lower.startsWith("apply") || lower.startsWith("stamp") || lower.startsWith("players")
                || lower.contains("dungeon core") || lower.contains("cd off") || lower.contains("consumed"));
    }

    static boolean isAbilityHeader(String line) {
        if (line == null) {
            return false;
        }
        String plain = plain(line).trim();
        return plain.startsWith("✦ ") || plain.startsWith("☀ ") || plain.startsWith("Ability · ")
                || plain.equals("Full Set") || plain.startsWith("Full Set:") || plain.startsWith("Full Set ·")
                || plain.startsWith("Set: ");
    }

    /** {@code §6✦ Name} — the rest of the header keeps its own accents (e.g. {@code §8· §eRight-click}). */
    private static String header(String line) {
        String raw = line.trim();
        String stripped = raw.replaceFirst("^(?:§[0-9a-fk-or])*(?:✦|☀|Ability ·)?\\s*(?:§[0-9a-fk-or])*", "");
        return HEADER + "✦ " + stripped;
    }

    /* =========================================================
     * WRAPPING (color aware)
     * ========================================================= */

    public static List<String> wrap(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        if (visibleLength(line) <= WRAP) {
            out.add(line);
            return out;
        }
        String[] words = line.split(" ");
        StringBuilder current = new StringBuilder();
        int visible = 0;
        String activeFormat = leadingFormat(line);
        for (String word : words) {
            int len = visibleLength(word);
            if (visible > 0 && visible + 1 + len > WRAP) {
                String done = current.toString();
                out.add(done);
                activeFormat = lastFormat(activeFormat, done);
                current = new StringBuilder(activeFormat);
                visible = 0;
            }
            if (visible > 0) {
                current.append(' ');
                visible++;
            }
            current.append(word);
            visible += len;
        }
        if (visible > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static String leadingFormat(String line) {
        StringBuilder format = new StringBuilder();
        int i = 0;
        while (i + 1 < line.length() && line.charAt(i) == '§') {
            format.append(line, i, i + 2);
            i += 2;
        }
        return format.toString();
    }

    /** Color + format codes active at the end of {@code text}, starting from {@code start}. */
    private static String lastFormat(String start, String text) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        String all = start + text;
        for (int i = 0; i + 1 < all.length(); i++) {
            if (all.charAt(i) != '§') {
                continue;
            }
            char code = Character.toLowerCase(all.charAt(i + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
                color = code == 'r' ? "" : "§" + code;
                formats.setLength(0);
            } else if (code >= 'k' && code <= 'o') {
                formats.append('§').append(code);
            }
            i++;
        }
        return color + formats;
    }

    private static int visibleLength(String text) {
        return text == null ? 0 : ChatColor.stripColor(text).length();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    static String plain(String line) {
        return line == null ? "" : ChatColor.stripColor(line);
    }

    /* =========================================================
     * LINE KINDS
     * ========================================================= */

    static boolean isRarityFooter(String line) {
        if (line == null) {
            return false;
        }
        String stripped = plain(line).trim();
        return !stripped.contains(":") && RARITY_FOOTER.matcher(stripped).matches();
    }

    static boolean isValueLine(String line) {
        if (line == null) {
            return false;
        }
        return line.contains("Value:") || line.contains("AH value:") || line.contains("Trade value:")
                || line.contains("Wert:") || line.contains("AH-Wert:") || line.contains("Handelswert:");
    }

    static boolean isMarkLine(String line) {
        return line != null && line.equals(de.aetherion.items.dungeon.WardenPrisonLook.MARK_LINE);
    }

    static boolean isPropertyLine(String line) {
        if (line == null) {
            return false;
        }
        return PROPERTY.matcher(plain(line).trim()).matches();
    }

    static boolean isLevelLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = plain(line).trim();
        String lower = plain.toLowerCase(Locale.ROOT);
        if (plain.contains("Hoe Level:")
                || plain.contains("Rod Level:")
                || plain.contains("Axe Level:")
                || plain.contains("Gaff Level:")
                || plain.contains("Gear Level:")
                || plain.contains("Charm Level:")
                || plain.startsWith("Blueprint Tier:")) {
            return true;
        }
        if (plain.contains("█") && (plain.contains("/") || lower.contains("max"))) {
            return true;
        }
        return lower.contains("per level")
                || lower.contains("levels from catches")
                || lower.contains("nothing left to prove")
                || lower.contains("menagerie filed");
    }

    static boolean isDungeonLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = plain(line).trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("dungeon:")
                || lower.startsWith("dungeon +")
                || lower.startsWith("infused:")
                || lower.startsWith("level cap")
                || lower.startsWith("rarity bonus")
                || lower.startsWith("dungeonized")
                || lower.startsWith("cores + gear level")
                || lower.startsWith("dungeon-bound")
                || lower.startsWith("dungeon weapon.")
                || lower.startsWith("relic piece.")
                || lower.startsWith("relic weapon.")
                || lower.startsWith("not dungeon gear until")
                || lower.startsWith("overworld: ")
                || lower.matches("overworld \\d+%.*")) {
            return true;
        }
        if (lower.startsWith("lv ") && (lower.contains("■") || lower.contains("□") || lower.contains("max"))) {
            return true;
        }
        if (lower.contains("anvil +") && lower.contains("core")) {
            return true;
        }
        return lower.contains("dungeon core")
                && (lower.contains("dungeon gear")
                || lower.contains("then it levels")
                || lower.contains("rarity up")
                || lower.contains("level cap"));
    }

    static boolean isHintLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = plain(line).toLowerCase(Locale.ROOT);
        return lower.contains("hold shift") || lower.contains("sneak in inventory") || isDungeonLine(line);
    }

    static boolean isCategoryBanner(String line) {
        return isTypeLabelLine(line);
    }

    /** Legacy type banners that the type line replaces ("Aetherion Mining Set", "⛏ Mining Tool II", …). */
    static boolean isTypeLabelLine(String line) {
        if (line == null) {
            return false;
        }
        String raw = plain(line).trim();
        if (raw.startsWith("Type: ") || raw.endsWith("· hold in the off hand")) {
            return true;
        }
        String plain = raw.toLowerCase(Locale.ROOT)
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
                || plain.endsWith(" spark")
                || plain.equals("aetherion foraging")
                || plain.equals("aetherion farm pantry"))) {
            return true;
        }
        if (plain.equals("aetherion charm")
                || plain.equals("starter tool")
                || plain.equals("basic aetherion tool")
                || plain.equals("basic aetherion weapon")
                || plain.equals("basic armor")
                || plain.equals("basic weapon")
                || plain.equals("off hand accessory")
                || plain.equals("off hand catch tool")
                || plain.startsWith("god kit")
                || plain.equals("test arena prototype set")
                || plain.matches("first real (aetherion )?(sword|pick|axe|hoe|hunting bow|shortbow)")) {
            return true;
        }
        if (plain.matches("(mining|combat|farming|foraging|fishing|catcher|dungeon|diving|woodcutting) (gear|tool|weapon|armor|pickaxe|axe|hoe|rod|gaff|helmet|chestplate|leggings|boots)( [ivx]+)?")) {
            return true;
        }
        return plain.equals("farming tool")
                || plain.equals("foraging tool")
                || plain.equals("fishing tool")
                || plain.equals("catcher tool")
                || plain.equals("diving armor");
    }

    static boolean isDungeonStatPreview(String line) {
        return de.aetherion.items.dungeon.DungeonArmor.isStandaloneDungeonStatPreview(line);
    }

    static boolean isAbilityLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = plain(line).toLowerCase(Locale.ROOT);
        return lower.contains("right-click")
                || lower.contains("hold right")
                || lower.contains("no arrows required");
    }

    static boolean isMechanicalExtra(String line) {
        if (line == null) {
            return false;
        }
        String plain = plain(line).trim();
        return plain.contains("+") || plain.contains("%");
    }

    static boolean isPersonalFlavor(String line) {
        if (line == null) {
            return false;
        }
        return line.startsWith(FLAVOR);
    }
}
