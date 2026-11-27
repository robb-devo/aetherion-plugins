package de.aetherion.items.menu;

import de.aetherion.core.api.AetherServices;
import de.aetherion.items.progress.PathLadder;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Handbook (GRUNDLAGEN rule 4, "the Journal explains, NPCs point there"), 2026-11-27: a real written book, so
 * it reads like one. First the player's path (nine milestones, hover for what each needs and brings), then the
 * next three goals, the contents (click a line to turn there) and one short chapter per system, English or
 * German by the reader's language. Opened from Journal › Today ("Path"), Journal › Settings and {@code /handbook}.
 */
public final class HandbookBook implements TabExecutor {

    /** About the width of a book line (114 px) in average characters, and the lines a page holds. */
    private static final int LINE_CHARS = 19;
    private static final int PAGE_LINES = 14;

    private record Topic(String key, String tocEn, String tocDe, String[] en, String[] de) {
    }

    private static final List<Topic> TOPICS = List.of(
            new Topic("journal", "Journal", "Journal", new String[] {
                    "The book in hotbar slot 9 is your Journal. Every system lives in it.",
                    "The ribbons along the top are chapters: Today, Character, Journey, Atlas, Workshop, Market, Codex, Company, Settings.",
                    "New chapters appear when you unlock them. A glowing line is the one a teacher wants you to open.",
                    "Today shows your quest, what opens next and your path."
            }, new String[] {
                    "Das Buch auf Hotbar-Platz 9 ist dein Journal. Jedes System steckt darin.",
                    "Die Bänder oben sind Kapitel: Heute, Charakter, Reise, Atlas, Werkstatt, Markt, Codex, Gemeinschaft, Optionen.",
                    "Neue Kapitel erscheinen, wenn du sie freischaltest. Eine leuchtende Zeile will ein Lehrer von dir geöffnet sehen.",
                    "Heute zeigt deine Quest, was als Nächstes kommt, und deinen Weg."
            }),
            new Topic("skills", "Skills & Talents", "Skills & Talente", new String[] {
                    "Everything you do trains a skill: Mining, Farming, Foraging, Fishing, Combat and Dungeon.",
                    "All skills together raise your Aetherion Level.",
                    "Skills open talents. Put them into talent slots: Journal › Character › Skills. A talent grows with its skill.",
                    "Talent slots open at Aetherion Level 1, 5, 10, 20, 35, 50 and 75. Talent sets save a whole selection (Level 1, 10, 25)."
            }, new String[] {
                    "Alles, was du tust, trainiert einen Skill: Mining, Farming, Foraging, Fishing, Combat und Dungeon.",
                    "Alle Skills zusammen heben dein Aetherion-Level.",
                    "Skills schalten Talente frei. Die kommen in Talent-Plätze: Journal › Charakter › Skills. Ein Talent wächst mit seinem Skill.",
                    "Talent-Plätze gibt es ab Aetherion-Level 1, 5, 10, 20, 35, 50 und 75. Talent-Sets speichern eine ganze Auswahl (Level 1, 10, 25)."
            }),
            new Topic("gear", "Gear", "Ausrüstung", new String[] {
                    "Gear comes in tiers, T1 to T5.",
                    "A recipe puts the old piece in the middle and the materials around it.",
                    "Higher tiers need Compressed material (2 stacks become 1) and Compacted material (32 Compressed become 1).",
                    "Save whole armour sets as Loadouts: Journal › Character › Loadouts."
            }, new String[] {
                    "Ausrüstung hat Stufen, T1 bis T5.",
                    "Ein Rezept nimmt das alte Teil in die Mitte und das Material drumherum.",
                    "Höhere Stufen brauchen Gepresstes (2 Stacks werden 1) und Verdichtetes (32 Gepresste werden 1).",
                    "Ganze Rüstungs-Sets speicherst du als Loadouts: Journal › Charakter › Loadouts."
            }),
            new Topic("anvil", "Anvil & Boosters", "Amboss & Booster", new String[] {
                    "Boosters make an item better at one thing: more damage, more health, more wood per tree.",
                    "Socket them at the Anvil: at Temper's forge on the Mines road, or Journal › Workshop › Anvil.",
                    "Item on top, booster into a free slot below. Taking it out is one click. Nothing breaks.",
                    "New boosters are made in the Recipe Book."
            }, new String[] {
                    "Booster machen einen Gegenstand in einer Sache besser: mehr Schaden, mehr Leben, mehr Holz pro Baum.",
                    "Du setzt sie am Amboss ein: in Tempers Schmiede an der Minenstraße oder unter Journal › Werkstatt › Amboss.",
                    "Gegenstand oben drauf, Booster in einen freien Platz darunter. Raus geht es mit einem Klick. Nichts geht kaputt.",
                    "Neue Booster stellst du im Rezeptbuch her."
            }),
            new Topic("recipes", "Recipes", "Rezepte", new String[] {
                    "Journal › Workshop › Recipes is the Recipe Book: every recipe with its ingredients.",
                    "Craft at a workbench in the world or with Journal › Workshop › Crafting.",
                    "The Craftsman at the harbour market opens the Recipe Book for you."
            }, new String[] {
                    "Journal › Werkstatt › Rezepte ist das Rezeptbuch: jedes Rezept mit seinen Zutaten.",
                    "Hergestellt wird an einer Werkbank in der Welt oder unter Journal › Werkstatt › Werkbank.",
                    "Der Craftsman am Hafenmarkt schaltet dir das Rezeptbuch frei."
            }),
            new Topic("felling", "Felling", "Baumfällen", new String[] {
                    "Hit a trunk with your axe until the notch is through. Better axes need fewer hits: T1 six, T5 two.",
                    "Now and then a gold spot lights up on the trunk. Hit it while it glows: that hit counts double, with extra wood.",
                    "A last hit on gold is a clean cut and builds your streak. The tree falls away from you. Thick trees and Titans take more hits."
            }, new String[] {
                    "Schlag mit der Axt gegen den Stamm, bis die Kerbe durch ist. Bessere Äxte brauchen weniger Schläge: T1 sechs, T5 zwei.",
                    "Ab und zu leuchtet eine goldene Stelle am Stamm. Triffst du sie, zählt der Schlag doppelt und es gibt Extra-Holz.",
                    "Gold als letzter Schlag ist ein sauberer Schnitt und baut deine Serie. Der Baum fällt von dir weg. Dicke Bäume und Titanen brauchen mehr Schläge."
            }),
            new Topic("pets", "Pets", "Pets", new String[] {
                    "Wild pets live in their own habitats: forest, mushroom, swamp, shore and more.",
                    "Hold a Catch Sphere, look at a wild pet and right-click to throw it.",
                    "Equip one in Journal › Character › Pets. It follows you, fights with you and levels with you.",
                    "Lark at the Clucksworth fields teaches it; the Aetherlex lists every pet."
            }, new String[] {
                    "Wilde Pets leben in ihren Lebensräumen: Wald, Pilze, Sumpf, Küste und mehr.",
                    "Halt eine Fangkugel, schau ein wildes Pet an und wirf sie mit Rechtsklick.",
                    "Rüste eins aus unter Journal › Charakter › Pets. Es folgt dir, kämpft mit dir und levelt mit dir.",
                    "Lark an den Feldern von Clucksworth bringt es dir bei, das Aetherlex kennt jedes Pet."
            }),
            new Topic("codex", "Codex", "Codex", new String[] {
                    "The Codex counts every block you break and every mob and boss you beat.",
                    "Every count has tiers. A new tier pays coins: claim them in Codex › Milestones.",
                    "Bosses keep their kill count and loot table in the Boss Journal."
            }, new String[] {
                    "Der Codex zählt jeden Block, den du abbaust, und jedes Monster und jeden Boss, den du besiegst.",
                    "Jede Zählung hat Stufen. Eine neue Stufe zahlt Coins: abholen unter Codex › Meilensteine.",
                    "Bosse führen Kills und Beute im Boss-Teil des Codex."
            }),
            new Topic("travel", "Getting around", "Unterwegs", new String[] {
                    "Walk into a camp once and it becomes a teleport: Journal › Atlas › Teleports.",
                    "/capital takes you back to town. Slime pads at the island's edge carry you to the four skill isles.",
                    "Dungeons: /dhub. The Exile opens at Aetherion Level 75."
            }, new String[] {
                    "Lauf einmal in ein Lager hinein, dann wird es ein Teleport: Journal › Atlas › Teleports.",
                    "/capital bringt dich zurück in die Stadt. Schleim-Pads am Inselrand bringen dich zu den vier Skill-Inseln.",
                    "Dungeons: /dhub. Das Exil öffnet sich ab Aetherion-Level 75."
            }),
            new Topic("island", "Island & Guild", "Insel & Gilde", new String[] {
                    "At Aetherion Level 20 you get your own island: Journal › Company › Island.",
                    "Your island has its own goals. Journal › Today shows the next one.",
                    "With an island you can join a guild. Founding one opens at Level 75."
            }, new String[] {
                    "Ab Aetherion-Level 20 bekommst du deine eigene Insel: Journal › Gemeinschaft › Insel.",
                    "Deine Insel hat eigene Ziele. Journal › Heute zeigt das nächste.",
                    "Mit einer Insel kannst du einer Gilde beitreten. Eine gründen geht ab Level 75."
            }),
            new Topic("market", "Market", "Markt", new String[] {
                    "Shop: spend Aether Crystals. Always open.",
                    "Bazaar: offers from players for resources. The trader buys them too.",
                    "Auction House: gear and tools from other players."
            }, new String[] {
                    "Shop: Aether Crystals ausgeben. Immer offen.",
                    "Basar: Angebote von Spielern für Rohstoffe. Der Händler kauft sie auch.",
                    "Auktionshaus: Ausrüstung und Werkzeug von anderen Spielern."
            }),
            new Topic("bosses", "Bosses & Dungeons", "Bosse & Dungeons", new String[] {
                    "Borderlands bosses: hold a Borderlands Spirit vial and right-click the grey powder at the Rite Circle.",
                    "Crypt vials from the caves below go to the Proctor at the Colosseum.",
                    "Dungeons have three floors, each with a boss. The final raid: Aetherion, called with the Aetherion Summons at the Veil."
            }, new String[] {
                    "Borderlands-Bosse: Phiole in die Hand und Rechtsklick auf das graue Pulver am Ritus-Kreis.",
                    "Crypt-Phiolen aus den Höhlen darunter bringst du zum Proctor im Kolosseum.",
                    "Dungeons haben drei Ebenen mit je einem Boss. Der letzte Raid: Aetherion, gerufen mit den Aetherion Summons am Veil."
            })
    );

    /** Opens the whole handbook: path, goals, contents, every chapter. */
    public static void open(Player player) {
        open(player, null);
    }

    /** Opens the handbook with {@code topic} first (unknown or null: the path first). */
    public static void open(Player player, String topic) {
        if (player == null) {
            return;
        }
        player.openBook(build(player, AetherServices.german(player), topic));
    }

    public static boolean hasTopic(String key) {
        return key != null && TOPICS.stream().anyMatch(t -> t.key().equalsIgnoreCase(key));
    }

    static Book build(Player player, boolean de, String first) {
        // Sections in reading order; a requested chapter moves to the front.
        Map<String, List<Component>> sections = new LinkedHashMap<>();
        Topic lead = null;
        for (Topic topic : TOPICS) {
            if (topic.key().equalsIgnoreCase(first == null ? "" : first)) {
                lead = topic;
            }
        }
        if (lead != null) {
            sections.put(lead.key(), chapter(lead, de));
        }
        sections.put("path", pathPages(player, de));
        sections.put("goals", goalPages(player, de));
        sections.put("contents", List.of(Component.empty()));
        for (Topic topic : TOPICS) {
            if (topic != lead) {
                sections.put(topic.key(), chapter(topic, de));
            }
        }
        // Page numbers (1-based) of every section, then the real contents page.
        Map<String, Integer> pageOf = new LinkedHashMap<>();
        int page = 1;
        for (Map.Entry<String, List<Component>> section : sections.entrySet()) {
            pageOf.put(section.getKey(), page);
            page += section.getValue().size();
        }
        sections.put("contents", List.of(contents(de, pageOf)));
        List<Component> pages = new ArrayList<>();
        for (List<Component> section : sections.values()) {
            pages.addAll(section);
        }
        return Book.book(Component.text(de ? "Handbuch" : "Handbook"), Component.text("Aetherion"), pages);
    }

    /* ------------------------------------------------------------------ pages */

    private static List<Component> pathPages(Player player, boolean de) {
        List<PathLadder.Step> steps = PathLadder.steps(player, de);
        TextComponent.Builder page = Component.text()
                .append(title((de ? "Dein Weg " : "Your path ") + PathLadder.doneCount(steps) + "/" + steps.size()));
        for (PathLadder.Step step : steps) {
            String mark;
            NamedTextColor colour;
            switch (step.state()) {
                case DONE -> {
                    mark = "✔ ";
                    colour = NamedTextColor.DARK_GREEN;
                }
                case NOW -> {
                    mark = "➜ ";
                    colour = NamedTextColor.GOLD;
                }
                default -> {
                    mark = "• ";
                    colour = NamedTextColor.GRAY;
                }
            }
            Component hover = Component.text()
                    .append(Component.text(step.number() + ". " + step.name(), colour, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text((de ? "Was: " : "Do: ") + step.todo(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text((de ? "Bringt: " : "Brings: ") + step.reward(), NamedTextColor.YELLOW))
                    .build();
            Component line = Component.text(mark + step.name(), colour)
                    .hoverEvent(HoverEvent.showText(hover));
            if (step.state() == PathLadder.State.NOW) {
                line = line.decorate(TextDecoration.BOLD);
            }
            page.append(line).append(Component.newline());
        }
        page.append(Component.newline())
                .append(Component.text(de ? "Zeig auf eine Zeile für Details." : "Point at a line for details.",
                        NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        return List.of(page.build());
    }

    private static List<Component> goalPages(Player player, boolean de) {
        List<String> goals = PathLadder.nextGoals(player, de);
        List<String> paragraphs = new ArrayList<>();
        if (goals.isEmpty()) {
            paragraphs.add(de ? "Alles geschafft. Respekt." : "All done. Respect.");
        }
        for (int i = 0; i < goals.size(); i++) {
            paragraphs.add((i + 1) + ". " + goals.get(i));
        }
        return pack(de ? "Nächste Ziele" : "Next goals", paragraphs, NamedTextColor.BLACK);
    }

    private static Component contents(boolean de, Map<String, Integer> pageOf) {
        TextComponent.Builder page = Component.text().append(title(de ? "Inhalt" : "Contents"));
        page.append(link(de ? "Dein Weg" : "Your path", pageOf.get("path")));
        page.append(link(de ? "Nächste Ziele" : "Next goals", pageOf.get("goals")));
        for (Topic topic : TOPICS) {
            page.append(link(de ? topic.tocDe() : topic.tocEn(), pageOf.get(topic.key())));
        }
        return page.build();
    }

    private static Component link(String label, Integer page) {
        if (page == null) {
            return Component.empty();
        }
        return Component.text("› " + label, NamedTextColor.DARK_BLUE)
                .clickEvent(ClickEvent.changePage(page))
                .hoverEvent(HoverEvent.showText(Component.text("→ " + page, NamedTextColor.GRAY)))
                .append(Component.newline());
    }

    private static List<Component> chapter(Topic topic, boolean de) {
        return pack(de ? topic.tocDe() : topic.tocEn(), List.of(de ? topic.de() : topic.en()), NamedTextColor.BLACK);
    }

    /** Paragraphs onto pages: a page takes about {@link #PAGE_LINES} lines, the title two of them on the first page. */
    private static List<Component> pack(String heading, List<String> paragraphs, NamedTextColor colour) {
        List<Component> pages = new ArrayList<>();
        TextComponent.Builder page = Component.text().append(title(heading));
        int used = 2;
        boolean empty = true;
        for (String paragraph : paragraphs) {
            int lines = lines(paragraph);
            int need = lines + (empty ? 0 : 1);
            if (!empty && used + need > PAGE_LINES) {
                pages.add(page.build());
                page = Component.text();
                used = 0;
                empty = true;
                need = lines;
            }
            if (!empty) {
                page.append(Component.newline()).append(Component.newline());
            }
            page.append(Component.text(paragraph, colour));
            used += need;
            empty = false;
        }
        pages.add(page.build());
        return pages;
    }

    private static int lines(String paragraph) {
        int visible = paragraph.replaceAll("§.", "").length();
        return Math.max(1, (visible + LINE_CHARS - 1) / LINE_CHARS);
    }

    private static Component title(String text) {
        return Component.text()
                .append(Component.text(text, NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .build();
    }

    /* ------------------------------------------------------------------ /handbook [topic] */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        open(player, args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Topic topic : TOPICS) {
                if (topic.key().startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(topic.key());
                }
            }
        }
        return out;
    }
}
