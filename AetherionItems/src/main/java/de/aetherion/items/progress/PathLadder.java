package de.aetherion.items.progress;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.listener.AutoPickupListener;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's path (GRUNDLAGEN §7, 2026-11-27): nine milestones, each one only a gate the game already has
 * (quests, Aetherion level, boss kills, skill level). The Handbook shows it; Today points at it. Nothing here
 * unlocks anything: it reads what is there and says what comes next.
 */
public final class PathLadder {

    public enum State { DONE, NOW, LATER }

    /** One milestone as the reader sees it. */
    public record Step(int number, String name, String todo, String reward, State state) {
    }

    /** The four Borderlands rite bosses (BorderlandsRiteService.T1). */
    private static final String[] RITE_BOSSES = {"hollow_lurker", "mcnugget", "bridge_troll", "skuldugery"};

    private PathLadder() {
    }

    /** What the gates read today. */
    private record Facts(boolean wood, boolean pickaxe, boolean booster, boolean rite, boolean floorOne,
                         boolean pathwarden, boolean finale, int level, int bestSkill, String bestSkillName) {
    }

    private static Facts facts(Player player) {
        QuestProgressAccess quests = AetherServices.quests();
        AetherionItems plugin = AetherionItems.getInstance();
        SkillService skills = plugin == null ? null : plugin.getSkills();
        CodexService codex = plugin == null ? null : plugin.getCodex();
        int level = skills == null ? 0 : skills.accountLevel(player);
        int best = 0;
        String bestName = null;
        if (skills != null) {
            for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
                int at = skills.skillLevel(player, category);
                if (at > best) {
                    best = at;
                    bestName = category.plain();
                }
            }
        }
        boolean rite = false;
        if (codex != null) {
            for (String boss : RITE_BOSSES) {
                rite |= codex.bossKills(player, boss) > 0L;
            }
        }
        return new Facts(
                done(quests, player, "gather_wood"),
                done(quests, player, "a_simple_craft"),
                done(quests, player, "lesson_boost"),
                rite,
                codex != null && codex.bossKills(player, "dungeon_sentinel") > 0L,
                codex != null && codex.bossKills(player, "pathwarden") > 0L,
                codex != null && codex.bossKills(player, "aetherion") > 0L,
                level, best, bestName);
    }

    private static boolean done(QuestProgressAccess quests, Player player, String quest) {
        return quests != null && quests.isQuestCompleted(player, quest);
    }

    /** The nine milestones, the first one not done marked NOW. */
    public static List<Step> steps(Player player, boolean de) {
        Facts f = facts(player);
        boolean[] reached = {
                f.wood(),
                f.level() >= AutoPickupListener.UNLOCK_LEVEL && f.pickaxe(),
                f.booster(),
                f.rite(),
                f.level() >= ProgressionService.ISLAND_LEVEL,
                f.floorOne(),
                f.bestSkill() >= 50,
                f.level() >= 75 && f.pathwarden(),
                f.finale()
        };
        String[][] text = de ? DE : EN;
        List<Step> out = new ArrayList<>();
        boolean nowGiven = false;
        for (int i = 0; i < reached.length; i++) {
            State state;
            if (reached[i]) {
                state = State.DONE;
            } else if (!nowGiven) {
                state = State.NOW;
                nowGiven = true;
            } else {
                state = State.LATER;
            }
            out.add(new Step(i + 1, text[i][0], text[i][1], text[i][2], state));
        }
        return out;
    }

    public static int doneCount(List<Step> steps) {
        int n = 0;
        for (Step step : steps) {
            if (step.state() == State.DONE) {
                n++;
            }
        }
        return n;
    }

    /** Up to three concrete things to do next, in the order the path asks for them. */
    public static List<String> nextGoals(Player player, boolean de) {
        Facts f = facts(player);
        List<String> out = new ArrayList<>();
        if (!f.wood()) {
            out.add(de ? "Bring Egon zehn Eichenstämme (Hafenpier)." : "Bring Egon ten oak logs (harbour pier).");
        }
        if (f.level() < AutoPickupListener.UNLOCK_LEVEL) {
            out.add(de ? "Erreiche Aetherion-Level " + AutoPickupListener.UNLOCK_LEVEL + " (jetzt " + f.level() + ")."
                    : "Reach Aetherion Level " + AutoPickupListener.UNLOCK_LEVEL + " (now " + f.level() + ").");
        }
        if (!f.pickaxe()) {
            out.add(de ? "Bau eine Mining Pickaxe (Craftsman am Hafenmarkt)."
                    : "Make a Mining Pickaxe (Craftsman, harbour market).");
        }
        if (!f.booster()) {
            out.add(de ? "Setz einen Booster ein (Tempers Schmiede an der Minenstraße)."
                    : "Socket a booster (Temper's forge on the Mines road).");
        }
        if (!f.rite()) {
            out.add(de ? "Besiege einen Boss am Ritus-Kreis (Borderlands)."
                    : "Beat a boss at the Rite Circle (Borderlands).");
        }
        if (f.level() < 10) {
            out.add(de ? "Aetherion-Level 10 bringt Talent-Platz 3 (jetzt " + f.level() + ")."
                    : "Aetherion Level 10 brings talent slot 3 (now " + f.level() + ").");
        }
        if (f.level() < ProgressionService.ISLAND_LEVEL) {
            out.add(de ? "Aetherion-Level " + ProgressionService.ISLAND_LEVEL + " bringt deine eigene Insel."
                    : "Aetherion Level " + ProgressionService.ISLAND_LEVEL + " brings your own island.");
        }
        if (!f.floorOne()) {
            out.add(de ? "Schaff Dungeon-Ebene 1 (/dhub)." : "Clear Dungeon Floor 1 (/dhub).");
        }
        if (f.bestSkill() < 50) {
            String best = f.bestSkillName() == null ? "" : (de ? " (bester: " : " (best: ") + f.bestSkillName() + " " + f.bestSkill() + ")";
            out.add(de ? "Bring einen Skill auf Level 50" + best + "." : "Get one skill to level 50" + best + ".");
        }
        if (f.level() < 75) {
            out.add(de ? "Erreiche Aetherion-Level 75 (jetzt " + f.level() + ")." : "Reach Aetherion Level 75 (now " + f.level() + ").");
        }
        if (!f.pathwarden()) {
            out.add(de ? "Besiege den Pathwarden (Kolosseum)." : "Beat the Pathwarden (Colosseum).");
        }
        if (!f.finale()) {
            out.add(de ? "Besiege Aetherion mit den Aetherion Summons am Veil."
                    : "Beat Aetherion with the Aetherion Summons at the Veil.");
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private static final String[][] EN = {
            {"Arrived", "Bring Egon ten oak logs.", "Your first tools and the Journal."},
            {"Toolmaker", "Reach Aetherion Level 5 and make a Mining Pickaxe.", "Auto pickup, talent slot 2."},
            {"Geared", "Socket your first booster at the Anvil.", "Borderlands and the Rite are open."},
            {"Rite Victor", "Beat a Borderlands boss at the Rite Circle.", "Level 10: talent slot 3 and a second talent set."},
            {"Islander", "Reach Aetherion Level 20.", "Your own island, guilds, Compacted recipes, talent slot 4."},
            {"Delver", "Clear Dungeon Floor 1 (T2 gear recommended).", "Dungeon Cores; Level 25: a third talent set."},
            {"Master", "Get one skill to level 50.", "Level 35 and 50: talent slots 5 and 6."},
            {"Exile", "Reach Aetherion Level 75 and beat the Pathwarden.", "The Exile, founding a guild, talent slot 7."},
            {"Bellbearer", "Craft the Aetherion Summons and beat Aetherion.", "The finale."},
    };

    private static final String[][] DE = {
            {"Angekommen", "Bring Egon zehn Eichenstämme.", "Dein erstes Werkzeug und das Journal."},
            {"Werkzeugmacher", "Erreiche Aetherion-Level 5 und bau eine Mining Pickaxe.", "Auto-Pickup, Talent-Platz 2."},
            {"Gerüstet", "Setz deinen ersten Booster am Amboss ein.", "Borderlands und Ritus sind offen."},
            {"Ritus-Sieger", "Besiege einen Borderlands-Boss am Ritus-Kreis.", "Level 10: Talent-Platz 3 und ein zweites Talent-Set."},
            {"Inselbesitzer", "Erreiche Aetherion-Level 20.", "Eigene Insel, Gilden, verdichtete Rezepte, Talent-Platz 4."},
            {"Tiefgänger", "Schaff Dungeon-Ebene 1 (T2-Ausrüstung empfohlen).", "Dungeon-Cores; Level 25: ein drittes Talent-Set."},
            {"Meister", "Bring einen Skill auf Level 50.", "Level 35 und 50: Talent-Plätze 5 und 6."},
            {"Exilant", "Erreiche Aetherion-Level 75 und besiege den Pathwarden.", "Das Exil, eine Gilde gründen, Talent-Platz 7."},
            {"Glockenträger", "Stell die Aetherion Summons her und besiege Aetherion.", "Das Finale."},
    };
}
