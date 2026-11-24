package de.aetherion.core.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player texts that point at the Manager ("Nether Star → Skills", "in the Manager") read as Aether Journal paths while
 * the journal is on ({@code journal.enabled} in the AetherionItems config): "Journal (slot 9) › Character › Skills".
 *
 * <p>Wrap a text with {@link #t} where it is built; off, it comes back unchanged, so one jar serves servers with and
 * without the journal. Texts without "Manager", "Nether Star" or "Netherstern" are never touched. The chapter of each Manager tile
 * mirrors the journal's chapters (de.aetherion.items.menu.JournalMenu).
 */
public final class JournalText {

    private static volatile boolean enabled;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** Manager tile names (English and German, longest first) → chapter and the journal's line name. */
    private static final String[][] TILES = {
            {"green Recipe Book", "Workshop", "Recipes"},
            {"grünes Recipe Book", "Workshop", "Recipes"},
            {"Crafting Table", "Workshop", "Crafting"},
            {"Auction House", "Market", "Auction House"},
            {"Recipe Book", "Workshop", "Recipes"},
            {"green book", "Workshop", "Recipes"},
            {"grünes Buch", "Workshop", "Recipes"},
            {"Island tab", "Company", "Island"},
            {"Island-Tab", "Company", "Island"},
            {"Teleports", "Atlas", "Teleports"},
            {"Crafting", "Workshop", "Crafting"},
            {"Loadouts", "Character", "Loadouts"},
            {"Loadout", "Character", "Loadouts"},
            {"Recipes", "Workshop", "Recipes"},
            {"Storage", "Character", "Storage"},
            {"Spawns", "Atlas", "Teleports"},
            {"Skills", "Character", "Skills"},
            {"Amboss", "Workshop", "Anvil"},
            {"Bazaar", "Market", "Bazaar"},
            {"Island", "Company", "Island"},
            {"Anvil", "Workshop", "Anvil"},
            {"Lager", "Character", "Storage"},
            {"Stats", "Character", "Stats"},
            {"Codex", "Codex", "Codex"},
            {"Guild", "Company", "Guild"},
            {"Pets", "Character", "Pets"},
            {"Shop", "Market", "Shop"},
    };
    private static final Pattern ARROW;

    static {
        StringBuilder tiles = new StringBuilder();
        for (String[] tile : TILES) {
            if (!tiles.isEmpty()) {
                tiles.append('|');
            }
            tiles.append(Pattern.quote(tile[0]));
        }
        // "Journal" + up to 24 characters of slot talk ("(slot 9)", ", slot §e9§f") + "→" + a tile name.
        ARROW = Pattern.compile("((?:Aether )?Journal)([^→.:!?]{0,24}?)(\\s*(?:§.)*)\\s*→\\s*((?:§.)*)(" + tiles
                + ")(?![\\p{L}-])");
    }

    private static final Pattern DOUBLE = Pattern.compile("Journal(\\s*(?:§.)*\\s*)→\\s*(?:§.)*\\s*(?:Aether )?Journal");
    /** "Manager" as a word; a colour code right before it ("§eManager → Pets") still counts as a word start. */
    private static final Pattern WORD = Pattern.compile("(?<!(?<!§)[\\p{L}.])Manager(?![\\p{L}])");
    /**
     * Whole sentences that only made sense for the star (English and German), and slot talk that would name the
     * journal twice ("in the Journal, slot 9: your Journal"). Applied before the word rewrite.
     */
    private static final String[][] SENTENCES = {
            {"That Nether Star isn't jewelry. It's the Aetherion Manager.",
                    "That book in slot 9 isn't decoration. It's your Aether Journal."},
            {"Der Nether Star ist kein Schmuck. Das ist der Aetherion Manager.",
                    "Das Buch in Slot 9 ist keine Deko. Das ist dein Aether Journal."},
            {"The Nether Star in your inventory.", "The book in slot 9 of your hotbar."},
            {", slot 9: your Manager.", ", slot 9."},
            {", Slot 9: dein Manager.", ", Slot 9."},
            {"/aetherion for the Manager", "/journal for the Manager"},
    };

    private JournalText() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        CACHE.clear();
    }

    public static boolean enabled() {
        return enabled;
    }

    /** {@code text} as the journal reads it, or unchanged while the journal is off. */
    public static String t(String text) {
        if (!enabled || text == null
                || (!text.contains("Manager") && !text.contains("Nether Star") && !text.contains("Netherstern"))) {
            return text;
        }
        return CACHE.computeIfAbsent(text, JournalText::rewrite);
    }

    /** The rewrite itself, journal on or off (tests). */
    static String rewrite(String text) {
        String out = text;
        for (String[] sentence : SENTENCES) {
            out = out.replace(sentence[0], sentence[1]);
        }
        out = out.replace("Aetherion Manager", "Aether Journal")
                .replace("Der Manager", "Das Journal")
                .replace("der Manager", "das Journal")
                .replace("Nether Star", "Journal")
                .replace("Netherstern", "Journal");
        out = WORD.matcher(out).replaceAll("Journal");
        // German: das Journal (neuter), where the Manager and the Nether Star were masculine
        out = out.replace("Der Journal", "Das Journal").replace("der Journal", "das Journal")
                .replace("Der Aether Journal", "Das Aether Journal").replace("der Aether Journal", "das Aether Journal");
        out = DOUBLE.matcher(out).replaceAll("Journal");
        Matcher m = ARROW.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String[] tile = tile(m.group(5));
            String colour = m.group(3).stripTrailing();
            String replacement = m.group(1) + m.group(2) + colour + " › " + tile[1] + " › " + m.group(4) + tile[2];
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        out = sb.toString()
                .replace("(it blinks)", "(it glows)")
                .replace("it blinks", "it glows")
                .replace("will blink", "will glow")
                .replace("(es blinkt)", "(es leuchtet)")
                .replace(" blinkt", " leuchtet")
                .replace("blinking", "glowing");
        return out;
    }

    private static String[] tile(String name) {
        for (String[] tile : TILES) {
            if (tile[0].equals(name)) {
                return tile;
            }
        }
        return new String[]{name, "", name};
    }
}
