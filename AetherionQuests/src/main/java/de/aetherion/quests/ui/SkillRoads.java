package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.talk.TalkUx;
import de.aetherion.quests.util.QuestStoryGate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Red thread after orientation: Miss Ledger hands the player the "open roads" —
 * Forage Isle (Twig), Farm Isle (Harrow), Fishing (Tackle) and Steel (Vex, later Rite Warden).
 * <p>
 * Soft by design: the player picks one road (reply chips) or wanders. Exactly one soft
 * compass target is set at a time. No quest state is touched and nothing is locked.
 */
public final class SkillRoads {

    /** Set once the Forage road was pointed at, so the harbour pad crumb doesn't repeat it. */
    public static final String FORAGE_POINTED_KEY = "forage_harbour_pad_hint";

    public enum Road {
        FORAGE, FARM, FISH, STEEL
    }

    private SkillRoads() {
    }

    /* ------------------------------------------------------------------ graduation */

    /**
     * Graduation beat (first stamp only): two lines from Ledger, a quiet default compass
     * target, then the road picker. Classic mode / no TalkUx: one summary line + default.
     */
    public static void graduation(Player player, QuestNPC ledger) {
        if (player == null || ledger == null || !player.isOnline()) {
            return;
        }
        QuestManager qm = questManager();
        Road fallback = defaultRoad(player, qm);
        // Default target so the bossbar never goes blank (and drops Ledger's own pin) — a chip replaces it.
        QuestHint.clearPending(player);
        QuestHint.show(player, npcId(player, qm, fallback), hintLabel(player, qm, fallback));

        say(player, ledger, LangPack.say(player, "ledger.roads.intro",
                "Skills grow out there, {player}. Not at my desk."));

        if (!talkReady(player)) {
            say(player, ledger, LangPack.say(player, "ledger.roads.list",
                    "§aTwig§f's pad: Forage Isle. §eHarrow§f's portal: Farm Isle. §bTackle§f: fishing."));
            point(player, ledger, fallback, false);
            return;
        }
        say(player, ledger, LangPack.say(player, "ledger.roads.menu",
                "Wood, wheat, fish — or steel. Pick one. The arrow follows."));
        later(player, DialogPace.LINE_GAP_TICKS * 2L + 10L, () -> {
            if (!offer(player, ledger)) {
                point(player, ledger, fallback, true);
            }
        });
    }

    /** Ledger desk chip "Where next?": same picker, one prompt line. */
    public static void deskPrompt(Player player, QuestNPC ledger) {
        if (player == null || ledger == null) {
            return;
        }
        say(player, ledger, LangPack.say(player, "ledger.roads.menu",
                "Wood, wheat, fish — or steel. Pick one. The arrow follows."));
        later(player, DialogPace.LINE_GAP_TICKS + 10L, () -> {
            if (!offer(player, ledger)) {
                point(player, ledger, defaultRoad(player, questManager()), true);
            }
        });
    }

    /** Road chips beside Ledger (max 5: four roads + "I'll wander."). */
    public static boolean offer(Player player, QuestNPC ledger) {
        TalkUx talk = TalkUx.get();
        if (talk == null || player == null || ledger == null || !talk.enabledFor(player)) {
            return false;
        }
        QuestManager qm = questManager();
        List<TalkUx.Choice> choices = new ArrayList<>();
        for (Road road : Road.values()) {
            if (road == Road.STEEL && steelDone(player, qm)) {
                continue;
            }
            String key = labelKey(player, qm, road);
            choices.add(new TalkUx.Choice(
                    label(player, key),
                    color(road),
                    () -> point(player, ledger, road, true),
                    false,
                    echo(player, key)
            ));
        }
        choices.add(new TalkUx.Choice(
                LangPack.say(player, "ledger.roads.wander.label", "I'll wander."),
                NamedTextColor.GRAY,
                () -> {
                    // Their call: no compass nag. The desk's "Where next?" brings the roads back.
                    QuestHint.clearPending(player);
                    say(player, ledger, LangPack.say(player, "ledger.roads.wander.line",
                            "Good. The map's yours. My desk isn't going anywhere."));
                },
                false,
                LangPack.say(player, "ledger.roads.wander.echo", "I'll look around.")
        ));
        return talk.choices(player, ledger.getId(), ledger.getName(), choices);
    }

    /**
     * Point at one road: speaker line, one compass target, one action-bar tip.
     * {@code speak=false} skips the line (already covered by a summary).
     */
    public static void point(Player player, QuestNPC speaker, Road road, boolean speak) {
        if (player == null || road == null || !player.isOnline()) {
            return;
        }
        QuestManager qm = questManager();
        String key = labelKey(player, qm, road);
        if (speak && speaker != null) {
            say(player, speaker, line(player, lineKey(player, qm, road)));
        }
        QuestHint.clearPending(player);
        QuestHint.show(player, npcId(player, qm, road), hintLabel(player, qm, road));
        player.sendActionBar(Component.text(tip(player, key), color(road)));
        if (road == Road.FORAGE) {
            markForagePointed(player);
        }
    }

    /** Plain default when the player doesn't pick: the harbour lesson first, then the isle trees. */
    public static Road defaultRoad(Player player, QuestManager qm) {
        if (qm != null && player != null && !QuestStoryGate.questCompleted(player, qm, "a_good_catch")) {
            return Road.FISH;
        }
        return Road.FORAGE;
    }

    public static void markForagePointed(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (player != null && plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), FORAGE_POINTED_KEY);
        }
    }

    /* ------------------------------------------------------------------ lookups */

    static String npcId(Player player, QuestManager qm, Road road) {
        return switch (road) {
            case FORAGE -> "forage_pad_guide";
            case FARM -> "farm_isle_guide";
            case FISH -> "fisher";
            case STEEL -> vexDone(player, qm) ? "rite_keeper" : "vex";
        };
    }

    private static String hintLabel(Player player, QuestManager qm, Road road) {
        return switch (road) {
            case FORAGE -> "Twig · Forage Pad";
            case FARM -> "Harrow · Farm Isle";
            case FISH -> "Tackle · Fishing";
            case STEEL -> vexDone(player, qm) ? "Rite Warden" : "Sergeant Vex";
        };
    }

    /** Label / tip key: forage, farm, fish, steel (Vex) or rite (after Vex). */
    private static String labelKey(Player player, QuestManager qm, Road road) {
        return switch (road) {
            case FORAGE -> "forage";
            case FARM -> "farm";
            case FISH -> "fish";
            case STEEL -> vexDone(player, qm) ? "rite" : "steel";
        };
    }

    private static String lineKey(Player player, QuestManager qm, Road road) {
        String key = labelKey(player, qm, road);
        if (road == Road.FISH && qm != null && QuestStoryGate.questCompleted(player, qm, "a_good_catch")) {
            return key + ".line_done";
        }
        return key + ".line";
    }

    // Explicit keys (no string building) so the DE validator and grep see every one.

    private static String label(Player player, String key) {
        return switch (key) {
            case "forage" -> LangPack.say(player, "ledger.roads.forage.label", "Forage Isle · Twig");
            case "farm" -> LangPack.say(player, "ledger.roads.farm.label", "Farm Isle · Harrow");
            case "fish" -> LangPack.say(player, "ledger.roads.fish.label", "Fishing · Tackle");
            case "rite" -> LangPack.say(player, "ledger.roads.rite.label", "Bosses · Rite Warden");
            default -> LangPack.say(player, "ledger.roads.steel.label", "Combat · Vex");
        };
    }

    private static String echo(Player player, String key) {
        return switch (key) {
            case "forage" -> LangPack.say(player, "ledger.roads.forage.echo", "Trees, please.");
            case "farm" -> LangPack.say(player, "ledger.roads.farm.echo", "Fields for me.");
            case "fish" -> LangPack.say(player, "ledger.roads.fish.echo", "I'll fish.");
            case "rite" -> LangPack.say(player, "ledger.roads.rite.echo", "Bigger things to hit.");
            default -> LangPack.say(player, "ledger.roads.steel.echo", "Something to hit.");
        };
    }

    private static String line(Player player, String lineKey) {
        return switch (lineKey) {
            case "forage.line" -> LangPack.say(player, "ledger.roads.forage.line",
                    "§aTwig§f's slime pad, by the Forager. Isle trees, bigger chop.");
            case "farm.line" -> LangPack.say(player, "ledger.roads.farm.line",
                    "§eHarrow§f's portal, far end of the barn. Farming §e10§f gets you in.");
            case "fish.line" -> LangPack.say(player, "ledger.roads.fish.line",
                    "§bTackle§f, end of the harbour pier. Rod, patience, five fish.");
            case "fish.line_done" -> LangPack.say(player, "ledger.roads.fish.line_done",
                    "§bTackle§f's still on the pier. Ask him where the big ones went.");
            case "rite.line" -> LangPack.say(player, "ledger.roads.rite.line",
                    "§cRite Warden§f, out in the waste. Bring a vial. And nerve.");
            default -> LangPack.say(player, "ledger.roads.steel.line",
                    "§cSergeant Vex§f, Borderlands gate. Armour on before you knock.");
        };
    }

    private static String tip(Player player, String key) {
        return switch (key) {
            case "forage" -> LangPack.ui(player, "tip.road_forage", "→ Twig · slime pad · Forage Isle");
            case "farm" -> LangPack.ui(player, "tip.road_farm", "→ Harrow · barn far end · Farm Isle");
            case "fish" -> LangPack.ui(player, "tip.road_fish", "→ Tackle · harbour pier · fishing");
            case "rite" -> LangPack.ui(player, "tip.road_rite", "→ Rite Warden · Borderlands");
            default -> LangPack.ui(player, "tip.road_steel", "→ Sergeant Vex · gear on first");
        };
    }

    private static NamedTextColor color(Road road) {
        return switch (road) {
            case FORAGE -> NamedTextColor.GREEN;
            case FARM -> NamedTextColor.YELLOW;
            case FISH -> NamedTextColor.AQUA;
            case STEEL -> NamedTextColor.RED;
        };
    }

    private static boolean vexDone(Player player, QuestManager qm) {
        return qm != null && player != null && QuestStoryGate.questCompleted(player, qm, "lesson_steel");
    }

    /** Vex and the Rite are both filed — the steel road has nothing left to point at. */
    private static boolean steelDone(Player player, QuestManager qm) {
        return vexDone(player, qm) && QuestStoryGate.questCompleted(player, qm, "border_rites");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static boolean talkReady(Player player) {
        TalkUx talk = TalkUx.get();
        return talk != null && talk.enabledFor(player);
    }

    private static void say(Player player, QuestNPC npc, String line) {
        LivingNpcProfile.say(player, npc, line);
    }

    private static void later(Player player, long ticks, Runnable task) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, ticks);
    }

    private static QuestManager questManager() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getQuestManager();
    }
}
