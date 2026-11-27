package de.aetherion.items.menu;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Journal in German (2026-11-27). JournalMenu builds every page in English; for a German reader the finished
 * page goes through here once: line labels, the title, and the name and lore of every item. Phrases are replaced
 * whole-word, longest first, so "Workshop" never touches "Shop". Names follow the word list (GRUNDLAGEN §3):
 * Werte, Lager, Werkbank, Rezepte, Amboss, Insel, Gilde; Skills, Pets, Loadouts, Shop, Teleports and Codex stay.
 */
final class JournalDe {

    private JournalDe() {
    }

    private static final String[][] PHRASES = {
            // Chapters (ribbons, title plate, "In Character › Skills")
            {"Today", "Heute"}, {"Character", "Charakter"}, {"Journey", "Reise"}, {"Workshop", "Werkstatt"},
            {"Market", "Markt"}, {"Company", "Gemeinschaft"}, {"Settings", "Optionen"},
            // Chapter blurbs
            {"Your goal, what opens next, the essentials.", "Dein Ziel, was als Nächstes kommt, das Wichtigste."},
            {"Stats, skills, pets, storage, loadouts.", "Werte, Skills, Pets, Lager, Loadouts."},
            {"Your quest, turn-ins and the talk log.", "Deine Quest, Abgaben und die Gespräche."},
            {"Teleports, dungeons and the isles' journals.", "Teleports, Dungeons und die Bücher der Inseln."},
            {"Crafting, recipes and the anvil.", "Werkbank, Rezepte und Amboss."},
            {"Shop, Bazaar and Auction House.", "Shop, Basar und Auktionshaus."},
            {"Collection, Bestiary, bosses and milestones.", "Sammlungen, Bestiarium, Bosse und Meilensteine."},
            {"Party, friends, island and guild.", "Party, Freunde, Insel und Gilde."},
            {"Language, talk mode, reports, the Handbook.", "Sprache, Gespräche, Meldungen, das Handbuch."},
            // Tile texts
            {"Your level, gear and active stats.", "Dein Level, deine Ausrüstung und aktive Werte."},
            {"Your skills and talents. Talents go into slots; more slots come with your Aetherion Level.",
                    "Deine Skills und Talente. Talente kommen in Plätze; mehr Plätze gibt es mit dem Aetherion-Level."},
            {"Your caught pets. Equip one: they add stats, quietly.", "Deine gefangenen Pets. Rüste eins aus: Es gibt dir Werte, ganz leise."},
            {"Your personal storage. Extra pages for the pack rat.", "Dein persönliches Lager. Extra Seiten für Sammler."},
            {"Save and swap armour sets.", "Rüstungs-Sets speichern und wechseln."},
            {"Spend Aether Crystals. Always open.", "Aether Crystals ausgeben. Immer offen."},
            {"Player listings for resources. The trader buys them too.", "Angebote von Spielern für Rohstoffe. Der Händler kauft sie auch."},
            {"Auction House: gear and tools, buyout listings.", "Auktionshaus: Ausrüstung und Werkzeug zum Sofortkauf."},
            {"Every camp you have walked into, one click away. Boss quests unlock more.",
                    "Jedes Lager, in das du gelaufen bist, einen Klick entfernt. Boss-Quests schalten mehr frei."},
            {"Collection, Bestiary, Boss Journal and milestones in one book.", "Sammlungen, Bestiarium, Bosse und Meilensteine in einem Buch."},
            {"A portable workbench.", "Eine Werkbank zum Mitnehmen."},
            {"The Recipe Book: every Aetherion recipe.", "Das Rezeptbuch: jedes Aetherion-Rezept."},
            {"Socket boosters into your gear: item on top, boosters below. Swap any time.",
                    "Booster in deine Ausrüstung einsetzen: Gegenstand oben, Booster darunter. Jederzeit tauschbar."},
            {"Your private island: a click takes you home. Without one, the island menu opens.",
                    "Deine eigene Insel: Ein Klick bringt dich heim. Ohne Insel öffnet sich das Insel-Menü."},
            {"Shared island, invites, ranks and guild quarries: the guild menu.",
                    "Gemeinsame Insel, Einladungen, Ränge und Gilden-Steinbrüche: das Gildenmenü."},
            // Commands
            {"The dungeon hub, from anywhere.", "Der Dungeon-Hub, von überall."},
            {"Cross to the exile island, or come back.", "Zur Exil-Insel übersetzen oder zurückkommen."},
            {"Back to the Capital, once you have been there.", "Zurück in die Hauptstadt, sobald du dort warst."},
            {"Foraging Isle: journal, tour and wayfinder.", "Foraging-Insel: Buch, Führung und Wegweiser."},
            {"Mining Isle: journal, compass and crew tour.", "Mining-Insel: Buch, Kompass und Führung."},
            {"Origin Journal: districts, glowcaps, bells, skyways.", "Atlas der Hauptinsel: Bezirke, Glowcaps, Glocken, Skyways."},
            {"Make or manage a party.", "Eine Party gründen oder verwalten."},
            {"Friends list, visits and guild invites.", "Freundesliste, Besuche und Gildeneinladungen."},
            {"Your island's menu: quarries, friends, land and biome.", "Das Menü deiner Insel: Steinbrüche, Freunde, Land und Biom."},
            {"Quest and hint language.", "Sprache für Quests und Hinweise."},
            {"Bubbles and reply chips, or classic chat.", "Sprechblasen mit Antwort-Knöpfen oder klassischer Chat."},
            {"Send a bug report to the team.", "Einen Fehler ans Team melden."},
            {"The beta checklist.", "Die Beta-Checkliste."},
            {"Next steps, and where to find a recipe.", "Nächste Schritte und wo du ein Rezept findest."},
            {"The pet encyclopedia.", "Das Pet-Lexikon."},
            {"Your recent conversations, as a book.", "Deine letzten Gespräche, als Buch."},
            {"How everything works, and your path.", "Wie alles funktioniert, und dein Weg."},
            {"Nine milestones from the harbour to the finale. Hover the lines in the book for what each needs.",
                    "Neun Meilensteine vom Hafen bis zum Finale. Zeig im Buch auf eine Zeile für Details."},
            // Codex chapter
            {"Every block you have broken, in tiers.", "Jeder Block, den du abgebaut hast, in Stufen."},
            {"Every mob you have beaten, in tiers.", "Jedes Monster, das du besiegt hast, in Stufen."},
            {"The Boss Journal and the Chronicle.", "Die Bosse und die Chronik."},
            {"What the Codex pays out, and what is still to claim.", "Was der Codex auszahlt und was noch abzuholen ist."},
            {"Collection, Bestiary and skill seals have tiers waiting for you.", "Sammlungen, Bestiarium und Skill-Siegel haben Stufen für dich bereit."},
            // Guide tips
            {"Miss Ledger wants one talent in a slot: pick any talent and drop it in a free slot.",
                    "Miss Ledger will ein Talent in einem Platz sehen: Nimm irgendeins und leg es in einen freien Platz."},
            {"Lark wants your catch equipped: open Pets and equip it.", "Lark will deinen Fang ausgerüstet sehen: Öffne Pets und rüste ihn aus."},
            {"Find the Mining Pickaxe (2nd recipe): Simple Pickaxe in the middle, coal around it.",
                    "Such die Mining Pickaxe (2. Rezept): Simple Pickaxe in die Mitte, Kohle drumherum."},
            {"Right-click the campfire Marker, then set your camp in Teleports.",
                    "Rechtsklick auf den Lagerfeuer-Marker, dann dein Lager unter Teleports setzen."},
            // Lore and line bits
            {"QUEST TIP", "QUEST-TIPP"}, {"Click to turn there", "Klicken zum Hinblättern"},
            {"Click to turn here", "Klicken zum Hinblättern"}, {"New chapter in your journal:", "Neues Kapitel in deinem Journal:"},
            {"New chapter:", "Neues Kapitel:"}, {"Done. Bring these back:", "Fertig. Bring das zurück:"},
            {"Click for your Journey", "Klicken für deine Reise"}, {"Ready to turn in", "Bereit zum Abgeben"},
            {"READY TO TURN IN", "BEREIT ZUM ABGEBEN"}, {"YOUR QUEST", "DEINE QUEST"}, {"ACTIVE", "AKTIV"},
            {"Click to open the Codex", "Klicken öffnet den Codex"}, {"for your active stats", "für deine aktiven Werte"},
            {"Opens next", "Kommt als Nächstes"}, {"Lives in", "Steht in"}, {"to show the way", "zeigt den Weg"},
            {"for the next step in chat", "für den nächsten Schritt im Chat"}, {"ISLAND GOAL", "INSEL-ZIEL"},
            {"Island goal", "Insel-Ziel"}, {"for your island menu", "für dein Insel-Menü"},
            {"On the island server:", "Auf dem Insel-Server:"}, {"NEXT STEP", "NÄCHSTER SCHRITT"},
            {"Next step", "Nächster Schritt"}, {"for every tip in chat", "für alle Tipps im Chat"},
            {"No quest right now", "Gerade keine Quest"}, {"No quest", "Keine Quest"},
            {"Talk to the people around you.", "Sprich mit den Leuten um dich herum."},
            {"Some of them have work.", "Manche haben Arbeit für dich."}, {"Talk log", "Gespräche"},
            {"this page", "diese Seite"}, {"not written yet", "noch nicht geschrieben"},
            {"The next chapter to open.", "Das nächste Kapitel."}, {"Or pick a ribbon along the top.", "Oder oben ein Band wählen."},
            {"Close the journal", "Journal schließen"}, {"Back to Today", "Zurück zu Heute"},
            {"is glowing", "leuchtet"}, {"glowing", "leuchtet"}, {"Not open yet.", "Noch nicht offen."},
            {"Click to open", "Klicken zum Öffnen"}, {"Your path", "Dein Weg"}, {"Path", "Weg"},
            {"Handbook", "Handbuch"}, {"Now:", "Jetzt:"}, {"§7Step §f", "§7Schritt §f"}, {"§7of §f", "§7von §f"},
            {"Click", "Klicken"}, {"§8· next", "§8· als Nächstes"}, {"✦ new", "✦ neu"},
            // Tile and command labels
            {"Stats", "Werte"}, {"Storage", "Lager"}, {"Bazaar", "Basar"}, {"Auction House", "Auktionshaus"},
            {"Auction", "Auktion"}, {"Crafting", "Werkbank"}, {"Recipes", "Rezepte"}, {"Anvil", "Amboss"},
            {"Island", "Insel"}, {"Guild", "Gilde"}, {"Exile", "Exil"}, {"Capital", "Hauptstadt"},
            {"Mine Isle", "Mining-Insel"}, {"Origin", "Hauptinsel"}, {"Friends", "Freunde"}, {"Isle menu", "Insel-Menü"},
            {"Language", "Sprache"}, {"Talk mode", "Gespräche"}, {"Report", "Melden"}, {"Checklist", "Checkliste"},
            {"Guide", "Wegweiser"}, {"Collection", "Sammlungen"}, {"Bestiary", "Bestiarium"}, {"Bosses", "Bosse"},
            {"Milestones", "Meilensteine"},
    };

    /** Counts with a word: "Turn in 2", "Claim 3", "3 rewards to claim". */
    private static final Pattern TURN_IN = Pattern.compile("Turn in (\\d+)");
    private static final Pattern CLAIM = Pattern.compile("Claim (\\d+)");
    private static final Pattern REWARDS = Pattern.compile("(\\d+) rewards? to claim");

    private static final List<Map.Entry<Pattern, String>> RULES = new ArrayList<>();
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    static {
        List<String[]> sorted = new ArrayList<>(List.of(PHRASES));
        sorted.sort(Comparator.comparingInt((String[] p) -> p[0].length()).reversed());
        for (String[] phrase : sorted) {
            // A colour code right before a word ("§eClick") still counts as a word start.
            Pattern pattern = Pattern.compile("(?<!(?<!§)[\\p{L}])" + Pattern.quote(phrase[0]) + "(?![\\p{L}])");
            RULES.add(Map.entry(pattern, Matcher.quoteReplacement(phrase[1])));
        }
    }

    /** {@code text} in German (unknown words stay as they are). */
    static String t(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return CACHE.computeIfAbsent(text, JournalDe::translate);
    }

    private static String translate(String text) {
        String out = TURN_IN.matcher(text).replaceAll("Abgeben: $1");
        out = CLAIM.matcher(out).replaceAll("Abholen: $1");
        out = REWARDS.matcher(out).replaceAll("$1 abzuholen");
        for (Map.Entry<Pattern, String> rule : RULES) {
            out = rule.getKey().matcher(out).replaceAll(rule.getValue());
        }
        return out;
    }

    /** Name and lore of {@code item} in German, in place. */
    @SuppressWarnings("deprecation")
    static void localize(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean changed = false;
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            String de = t(name);
            if (!de.equals(name)) {
                meta.setDisplayName(de);
                changed = true;
            }
        }
        if (meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>();
            boolean loreChanged = false;
            for (String line : meta.getLore()) {
                String de = t(line);
                loreChanged |= !de.equals(line);
                lore.add(de);
            }
            if (loreChanged) {
                meta.setLore(lore);
                changed = true;
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
    }
}
