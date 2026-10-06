package de.aetherion.quests.story;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcLife;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.NPCType;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.ui.QuestHint;
import de.aetherion.quests.ui.QuestProgressDisplay;
import de.aetherion.quests.util.QuestSkillGate;
import de.aetherion.quests.util.QuestStoryGate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Click routing for "Das Schlaflied". Runs before the generic NPC flow and only claims a click
 * when a story beat belongs to it:
 * <ol>
 *   <li>a running TALK step ends on this NPC,</li>
 *   <li>this NPC takes the DELIVER items of a running step,</li>
 *   <li>this NPC gave the running step (short reminder),</li>
 *   <li>this NPC gives the next step (offer, or a "not yet" with the level),</li>
 *   <li>the five story NPCs always speak their stage line (+ small talk).</li>
 * </ol>
 * Hollis and Deed fall back to their normal dialog when no story beat is theirs.
 */
public final class StoryFlow {

    public static final Set<String> STORY_NPCS = Set.of("ysolde", "berit", "nell", "hesper", "ferryman");

    /** Existing cast that hands out a story step. */
    private static final Set<String> GIVERS = Set.of("town_crier", "isle_clerk");

    private static final long AFTER_COMPLETE_TICKS = 40L;

    private StoryFlow() {
    }

    public static boolean isStoryNpc(String npcId) {
        return npcId != null && STORY_NPCS.contains(npcId.toLowerCase(Locale.ROOT));
    }

    /** @return true when the click was a story beat (the caller stops). */
    public static boolean handle(Player player, QuestNPC npc, QuestManager qm, DialogManager dm) {
        if (player == null || npc == null || qm == null || dm == null || npc.getId() == null) {
            return false;
        }
        String id = npc.getId().toLowerCase(Locale.ROOT);
        boolean story = STORY_NPCS.contains(id);
        if (!story && !GIVERS.contains(id)) {
            return false;
        }
        if (!QuestStoryGate.tutorialDone(player, qm)) {
            if (!story) {
                return false;
            }
            speak(player, npc, dm, "story_" + id + ".before", null);
            QuestStoryGate.redirectToTutorial(player, npc.getName());
            return true;
        }

        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            if (id.equals(step.talk()) && state(qm, player, step) == QuestState.ACTIVE) {
                finishTalk(player, npc, qm, dm, step);
                return true;
            }
        }
        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            QuestState state = state(qm, player, step);
            if (id.equals(step.turnIn()) && (state == QuestState.ACTIVE || state == QuestState.READY)) {
                turnIn(player, npc, qm, dm, step);
                return true;
            }
        }
        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            QuestState state = state(qm, player, step);
            String remind = "story_" + id + ".remind_" + step.beat();
            if (id.equals(step.giver()) && (state == QuestState.ACTIVE || state == QuestState.READY)
                    && StoryLines.has(remind)) {
                speak(player, npc, dm, remind, null);
                QuestProgressDisplay.showProgress(player, qm);
                return true;
            }
        }

        LullabyQuests.Step next = nextOpen(qm, player);
        if (next != null && id.equals(next.giver())) {
            if (gateOk(qm, player, next)) {
                speak(player, npc, dm, "story_" + id + ".offer_" + next.beat(), next.id());
                return true;
            }
            String wait = waitLines(next);
            if (story && wait != null) {
                speak(player, npc, dm, wait, null);
                return true;
            }
            // Deed before Level 20: his normal intro already names the island level.
            return false;
        }

        if (story) {
            speak(player, npc, dm, flavor(player, qm, id), null);
            return true;
        }
        return false;
    }

    /**
     * Another plugin's NPC was clicked (Sister Aurel lives in AetherionHub). Finishes a running
     * TALK step there and returns that NPC's lines in the reader's language.
     */
    public static List<String> talkFromElsewhere(Player player, String speakerId) {
        QuestManager qm = LoreFinds.questManager();
        if (qm == null || player == null || speakerId == null) {
            return List.of();
        }
        String id = speakerId.toLowerCase(Locale.ROOT);
        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            if (!id.equals(step.talk()) || state(qm, player, step) != QuestState.ACTIVE) {
                continue;
            }
            Quest quest = qm.getQuest(step.id());
            qm.completeQuest(player, quest);
            String[] lines = StoryLines.lines(player, "story_" + id + "." + step.beat() + "_done");
            pointNext(player, step);
            return lines == null ? List.of() : List.of(lines);
        }
        return List.of();
    }

    // ------------------------------------------------------------------ beats

    private static void finishTalk(Player player, QuestNPC npc, QuestManager qm, DialogManager dm,
                                   LullabyQuests.Step step) {
        qm.completeQuest(player, qm.getQuest(step.id()));
        celebrate(player, npc);
        later(player, () -> afterStep(player, npc, qm, dm, step));
    }

    private static void turnIn(Player player, QuestNPC npc, QuestManager qm, DialogManager dm,
                               LullabyQuests.Step step) {
        Quest quest = qm.getQuest(step.id());
        qm.syncDeliverProgress(player);
        if (!qm.areAllObjectivesComplete(player, quest)) {
            String remind = "story_" + npc.getId().toLowerCase(Locale.ROOT) + ".remind_" + step.beat();
            if (StoryLines.has(remind)) {
                speak(player, npc, dm, remind, null);
            }
            QuestProgressDisplay.showProgress(player, qm);
            return;
        }
        if (!qm.consumeDeliverItems(player, quest)) {
            LivingNpcProfile.say(player, npc, LangPack.say(player, "any.items_first", "Hands look empty, {player}. Items first."));
            QuestProgressDisplay.showProgress(player, qm);
            return;
        }
        qm.completeQuest(player, quest);
        celebrate(player, npc);
        later(player, () -> afterStep(player, npc, qm, dm, step));
    }

    /** "Done" lines; when the same NPC gives the next step, its offer (or its level gate) follows. */
    private static void afterStep(Player player, QuestNPC npc, QuestManager qm, DialogManager dm,
                                  LullabyQuests.Step done) {
        String id = npc.getId().toLowerCase(Locale.ROOT);
        String lines = "story_" + id + "." + done.beat() + "_done";
        LullabyQuests.Step next = LullabyQuests.next(done);
        String offerQuest = null;
        if (next != null && id.equals(next.giver()) && state(qm, player, next) == QuestState.AVAILABLE) {
            if (gateOk(qm, player, next)) {
                String hook = chainOffer(next);
                if (hook != null) {
                    lines += "+" + hook;
                }
                offerQuest = next.id();
            } else {
                String gate = chainGate(next);
                if (gate != null) {
                    lines += "+" + gate;
                }
            }
        }
        speak(player, npc, dm, lines, offerQuest);
        pointNext(player, done);
    }

    /** Offer lines when an offer follows "done" lines (empty part = the done lines already lead in). */
    private static String chainOffer(LullabyQuests.Step next) {
        return switch (next.id()) {
            case LullabyQuests.GEAR -> "story_berit.gear_hook";
            case LullabyQuests.INSIGNIA -> "story_ysolde.offer_insignia";
            case LullabyQuests.SHORT_TOLL -> "story_ysolde.offer_short_toll";
            case LullabyQuests.LETTERS -> "story_ysolde.letters_hook";
            case LullabyQuests.THIRD_ANCHOR -> "story_ysolde.third_anchor_hook";
            default -> null;
        };
    }

    private static String chainGate(LullabyQuests.Step next) {
        return switch (next.id()) {
            case LullabyQuests.LETTERS -> "story_ysolde.letters_gate";
            case LullabyQuests.THIRD_ANCHOR -> "story_ysolde.anchor_gate";
            default -> null;
        };
    }

    private static String waitLines(LullabyQuests.Step next) {
        return switch (next.id()) {
            case LullabyQuests.LETTERS -> "story_ysolde.musing_seal+story_ysolde.letters_gate";
            case LullabyQuests.THIRD_ANCHOR -> "story_ysolde.musing_letters+story_ysolde.anchor_gate";
            case LullabyQuests.SILENT_ROAD -> "story_ysolde.musing_fading+story_ysolde.road_gate";
            case LullabyQuests.FORGOTTEN -> "story_nell.wait";
            default -> null;
        };
    }

    /** When the next step lives with someone else, point there (soft hint + action bar). */
    private static void pointNext(Player player, LullabyQuests.Step done) {
        switch (done.id()) {
            case LullabyQuests.SHORT_TOLL -> tip(player, "story_next.deed",
                    "Next · Deed, the Isle Clerk · at Aetherion Level 20", NamedTextColor.GOLD);
            case LullabyQuests.THIRD_ANCHOR -> {
                QuestHint.show(player, "nell", "Nell");
                tip(player, "story_next.nell", "Next · Nell · harbour lanes · Aetherion Level 50", NamedTextColor.GOLD);
            }
            case LullabyQuests.FORGOTTEN -> {
                QuestHint.show(player, "ysolde", "Ysolde Thorne");
                tip(player, "story_next.ysolde_road", "Tell Ysolde · Archive · Aetherion Level 60", NamedTextColor.GOLD);
            }
            case LullabyQuests.SILENT_ROAD -> tip(player, "story_next.veil",
                    "The Veil Quay is open · the ferry waits for Level 75", NamedTextColor.AQUA);
            default -> {
            }
        }
    }

    private static String flavor(Player player, QuestManager qm, String id) {
        int stage = StoryState.stageOf(player);
        return switch (id) {
            case "ysolde" -> switch (stage) {
                case 7 -> "story_ysolde.after_road";
                case 6 -> "story_ysolde.musing_fading";
                case 5 -> "story_ysolde.after_anchor";
                case 4 -> "story_ysolde.musing_letters";
                case 3 -> "story_ysolde.musing_seal";
                case 2 -> "story_ysolde.after_toll";
                default -> "story_ysolde.default";
            };
            case "berit" -> stage >= 6 ? "story_berit.stranger"
                    : StoryState.completed(qm, player, LullabyQuests.GEAR) ? "story_berit.after_gear"
                    : "story_berit.default";
            case "nell" -> stage >= 6 ? "story_nell.after" : "story_nell.default";
            case "hesper" -> stage >= 5 ? "story_hesper.after" : "story_hesper.default";
            case "ferryman" -> !StoryState.hasPlayerFlag(player, StoryState.VEIL_QUAY) ? "story_ferryman.default"
                    : meetsLevel(player, 75) ? "story_ferryman.soon" : "story_ferryman.waiting";
            default -> "story_" + id + ".default";
        };
    }

    // ------------------------------------------------------------------ helpers

    static QuestState state(QuestManager qm, Player player, LullabyQuests.Step step) {
        Quest quest = qm.getQuest(step.id());
        return quest == null ? null : qm.getQuestState(player, quest);
    }

    /** First unfinished step, if it is open to offer (previous step done, not running yet). */
    static LullabyQuests.Step nextOpen(QuestManager qm, Player player) {
        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            QuestState state = state(qm, player, step);
            if (state == QuestState.COMPLETED) {
                continue;
            }
            return state == QuestState.AVAILABLE ? step : null;
        }
        return null;
    }

    private static boolean gateOk(QuestManager qm, Player player, LullabyQuests.Step step) {
        Quest quest = qm.getQuest(step.id());
        return quest != null && QuestSkillGate.failReason(player, quest) == null;
    }

    private static boolean meetsLevel(Player player, int level) {
        Quest probe = new Quest("story_level_probe", "", "");
        probe.requireAccountLevel(level);
        return QuestSkillGate.failReason(player, probe) == null;
    }

    /**
     * Plays {@code dialogId} as this NPC through the normal dialog pipeline (bubble, pacing,
     * German overlay). With {@code questId} the speech ends on that quest's offer chips.
     */
    private static void speak(Player player, QuestNPC npc, DialogManager dm, String dialogId, String questId) {
        if (!StoryLines.has(dialogId)) {
            return;
        }
        QuestNPC view = new QuestNPC(
                npc.getId(),
                npc.getName(),
                questId == null ? NPCType.FLAVOR : NPCType.QUEST,
                questId == null ? "" : questId,
                dialogId,
                ""
        );
        dm.startDialog(player, view);
    }

    private static void celebrate(Player player, QuestNPC npc) {
        LivingNpcLife life = LivingNpcLife.get();
        if (life != null && LivingNpcProfile.isLiving(npc.getId())) {
            life.celebrate(player, npc.getId());
        }
    }

    private static void later(Player player, Runnable task) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, AFTER_COMPLETE_TICKS);
    }

    private static void tip(Player player, String key, String english, NamedTextColor color) {
        player.sendActionBar(Component.text(LangPack.ui(player, "tip." + key, english), color));
    }
}
