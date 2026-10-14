package de.aetherion.quests.util;


import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.NPCType;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.tutorial.CombatTeach;
import de.aetherion.quests.ui.QuestHint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;


/**
 * Orientation spine + soft story order gates (not skill gates).
 * <pre>
 *   Act I   · Anker Harbour   Egon (welcome_aboard) → Forager (gather_wood: the Fell) → Egon's kit + Capital teleport
 *   Act II  · The Capital     Miss Ledger (lesson_manager: loadout + Codex) → Temper (lesson_boost)
 *   Act III · The Roads       Sergeant Vex (lesson_combat — combat stub) → Miss Ledger's stamp → the pads
 * </pre>
 * Act map: {@code docs/ONBOARDING_ACT_MAP.md}. Quartermaster, Foreman, Farmer, Lark and Tackle are side roads
 * after the stamp now; their quests still run for anyone who already holds them.
 */
public final class QuestStoryGate {


    private QuestStoryGate() {
    }

    /** One spine stop: who the arrow points at, the bar label, and the action-bar tip. */
    public record Stop(String npcId, String label, String note, String tipKey, String tipEnglish, NamedTextColor color) {
    }

    private static final Stop EGON = new Stop("egon", "Egon", null,
            "tip.tutorial_egon_pier", "Act I · Egon · the pier", NamedTextColor.GREEN);
    private static final Stop FORAGER = new Stop("lumberjack", "Forager", null,
            "tip.tutorial_forager", "Act I · the Forager · fell oak on the green", NamedTextColor.GREEN);
    private static final Stop EGON_OAK = new Stop("egon", "Egon", null,
            "tip.tutorial_egon_oak", "Act I · ten oak to Egon · the pier", NamedTextColor.GREEN);
    private static final Stop LEDGER = new Stop("ledger", "Miss Ledger", "/capital",
            "tip.tutorial_ledger_loadout", "Act II · Miss Ledger · Capital · Nether Star → Teleports", NamedTextColor.LIGHT_PURPLE);
    private static final Stop TEMPER = new Stop("booster_tutor", "Temper", "Boosters",
            "tip.tutorial_temper_boost", "Act II · Temper · south road · boosters", NamedTextColor.GOLD);
    private static final Stop VEX = new Stop(CombatTeach.NPC_ID, "Sergeant Vex", "Borderlands gate",
            "tip.tutorial_vex_gate", "Act III · Sergeant Vex · Borderlands gate", NamedTextColor.RED);
    private static final Stop STAMP = new Stop("ledger", "Miss Ledger", "/capital",
            "tip.tutorial_stamp", "Act III · Miss Ledger's stamp · /capital", NamedTextColor.LIGHT_PURPLE);

    /**
     * Talk freely during orientation: the spine, plus desks and guides that never hand out a quest.
     * Every other quest-giver waits for the stamp — a side quest accepted mid-spine would abort the spine one.
     */
    private static final Set<String> TUTORIAL_NPCS = Set.of(
            "egon",
            "lumberjack",
            "ledger",
            "booster_tutor",
            CombatTeach.NPC_ID,
            "craftsman",
            "fishmonger",
            "merchant",
            "bar_whisper",
            "vince",
            "liquidator",
            "stall_crumb",
            "stall_tack",
            "stall_brine",
            "dock_whisper",
            "bait_theory",
            "farm_isle_guide",
            "forage_pad_guide",
            "eldervale_welcome"
    );

    /** The spine, in order. These quests may start before the stamp; nothing else may. */
    private static final String[] SPINE = {
            "welcome_aboard",
            "gather_wood",
            "lesson_manager",
            "lesson_boost",
            CombatTeach.QUEST_ID
    };

    private static final Set<String> TUTORIAL_QUESTS = Set.of(SPINE);

    /** Filed when these are done; Miss Ledger stamps it shut. */
    private static final String[] TUTORIAL_DONE_QUESTS = {
            "gather_wood",
            "lesson_manager",
            "lesson_boost",
            CombatTeach.QUEST_ID
    };

    /** The pre-2026-10-15 spine (Mine → Temper → Ledger → Fields). Whoever finished it stays finished. */
    private static final String[] LEGACY_DONE_QUESTS = {
            "lesson_boost",
            "lesson_manager",
            "farm_hand",
            "pocket_zoo"
    };

    /**
     * Orientation content is done: the new spine, the old one, or Miss Ledger already stamped the file.
     * The story ({@code StoryFlow}) and every post-tutorial desk read this one answer.
     */
    public static boolean tutorialDone(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return false;
        }
        return allCompleted(player, questManager, TUTORIAL_DONE_QUESTS)
                || allCompleted(player, questManager, LEGACY_DONE_QUESTS)
                || stamped(player);
    }

    /**
     * Orientation is not only done but <b>filed</b>: Miss Ledger stamped it.
     * Between Vex and the stamp the arrow belongs to Ledger alone — Vex's Borderlands lesson must not
     * count as "graduated" or strangers skip the roads beat.
     */
    public static boolean graduated(Player player, QuestManager questManager) {
        return tutorialDone(player, questManager) && stamped(player);
    }

    private static boolean stamped(Player player) {
        de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
        return player != null && plugin != null && plugin.getPlayerQuestStorage() != null
                && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), "tutorial_graduation");
    }

    private static boolean allCompleted(Player player, QuestManager questManager, String[] ids) {
        for (String id : ids) {
            if (!questCompleted(player, questManager, id)) {
                return false;
            }
        }
        return true;
    }

    public static boolean questCompleted(Player player, QuestManager questManager, String questId) {
        if (player == null || questManager == null || questId == null) {
            return false;
        }
        Quest quest = questManager.getQuest(questId);
        if (quest == null) {
            return true;
        }
        return questManager.getQuestState(player, quest) == QuestState.COMPLETED;
    }

    private static QuestState state(Player player, QuestManager questManager, String questId) {
        Quest quest = questManager.getQuest(questId);
        return quest == null ? QuestState.COMPLETED : questManager.getQuestState(player, quest);
    }

    public static boolean isTutorialNpc(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        return TUTORIAL_NPCS.contains(npcId.toLowerCase(Locale.ROOT));
    }

    public static boolean isTutorialQuest(String questId) {
        if (questId == null || questId.isBlank()) {
            return false;
        }
        return TUTORIAL_QUESTS.contains(questId.toLowerCase(Locale.ROOT));
    }

    /** All spine quest IDs (for admin / Dev Menu skip). */
    public static Set<String> tutorialQuestIds() {
        return TUTORIAL_QUESTS;
    }

    /** Gate quests that mark orientation complete. */
    public static String[] tutorialDoneQuestIds() {
        return TUTORIAL_DONE_QUESTS.clone();
    }

    /**
     * Post-tutorial quest-givers while orientation is still open. Quest-free NPCs (buskers, stalls, the
     * harbour cast) always talk; Rite Warden keeps its own gate.
     */
    public static boolean blockedByTutorial(Player player, QuestManager questManager, String npcId) {
        if (isTutorialNpc(npcId)) {
            return false;
        }
        if ("rite_keeper".equalsIgnoreCase(npcId)) {
            return false;
        }
        if (!offersQuest(npcId)) {
            return false;
        }
        return !tutorialDone(player, questManager);
    }

    private static boolean offersQuest(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc == null || npc.getType() != NPCType.QUEST) {
            return false;
        }
        boolean quest = npc.getQuestId() != null && !npc.getQuestId().isBlank();
        boolean service = npc.getServiceId() != null && !npc.getServiceId().isBlank();
        return quest || service;
    }

    public static String[] tutorialBlockedLines() {
        return new String[] {
                "Not yet, {player}. This desk opens after orientation.",
                "Miss Ledger stamps you through. Then I'm all yours."
        };
    }

    /* =========================================================
     * SPINE
     * ========================================================= */

    /**
     * Where orientation goes next for this player, or null once the stamp is down. Each spine NPC's
     * quest may be taken in any order after Act I; the arrow just suggests the tidy one.
     */
    public static Stop nextStop(Player player, QuestManager qm) {
        if (player == null || qm == null) {
            return null;
        }
        if (tutorialDone(player, qm)) {
            return stamped(player) ? null : STAMP;
        }
        if (state(player, qm, "welcome_aboard") == QuestState.AVAILABLE) {
            return EGON;
        }
        if (!questCompleted(player, qm, "gather_wood")) {
            return state(player, qm, "gather_wood") == QuestState.READY ? EGON_OAK : FORAGER;
        }
        if (!questCompleted(player, qm, "lesson_manager")) {
            return LEDGER;
        }
        if (!questCompleted(player, qm, "lesson_boost")) {
            return TEMPER;
        }
        if (!questCompleted(player, qm, CombatTeach.QUEST_ID)) {
            return VEX;
        }
        return STAMP;
    }

    /** NPC id of the next spine stop, or null when orientation is filed. */
    public static String spineNpc(Player player, QuestManager qm) {
        Stop stop = nextStop(player, qm);
        return stop == null ? null : stop.npcId();
    }

    /** 1–3 while orientation is open (the act the next stop belongs to), 0 once stamped. */
    public static int act(Player player, QuestManager qm) {
        Stop stop = nextStop(player, qm);
        if (stop == null) {
            return 0;
        }
        if (stop == EGON || stop == FORAGER || stop == EGON_OAK) {
            return 1;
        }
        if (stop == LEDGER || stop == TEMPER) {
            return 2;
        }
        return 3;
    }

    /** Put the arrow, the bossbar and one action-bar tip on a spine stop. */
    public static void point(Player player, Stop stop) {
        if (player == null || stop == null) {
            return;
        }
        QuestHint.show(player, stop.npcId(), stop.label(), stop.note());
        player.sendActionBar(Component.text(LangPack.ui(player, stop.tipKey(), stop.tipEnglish()), stop.color()));
    }

    /**
     * The quest a spine NPC hands out right now when it differs from its everyday quest:
     * Sergeant Vex runs the combat beat ({@link CombatTeach}) before his Borderlands lesson.
     */
    public static Quest spineQuestFor(Player player, QuestNPC npc, QuestManager qm) {
        if (player == null || npc == null || qm == null || npc.getId() == null) {
            return null;
        }
        if (CombatTeach.NPC_ID.equalsIgnoreCase(npc.getId())
                && !questCompleted(player, qm, CombatTeach.QUEST_ID)
                && !tutorialDone(player, qm)) {
            return qm.getQuest(CombatTeach.QUEST_ID);
        }
        return null;
    }

    /**
     * Deny line when a spine quest is opened before its act, else null.
     * Act II and III open with Egon's kit; inside them the order is a suggestion.
     */
    public static String spineGateFail(Player player, QuestManager qm, String questId) {
        if (player == null || qm == null || questId == null) {
            return null;
        }
        if ("lesson_manager".equalsIgnoreCase(questId)) {
            return ledgerFailReason(player, qm);
        }
        if (("lesson_boost".equalsIgnoreCase(questId) || CombatTeach.QUEST_ID.equalsIgnoreCase(questId))
                && !questCompleted(player, qm, "gather_wood")) {
            return LangPack.say(player, "gate.kit_first", "§eEgon's kit first. §7Ten oak on the harbour pier.");
        }
        return null;
    }

    /* =========================================================
     * GUIDE (/guide, Discord)
     * ========================================================= */

    /**
     * Plain tips for /guide and Discord (same spine as {@link #redirectToTutorial}): the act checklist,
     * the running quest, then one "next". Empty list = orientation filed (caller adds post-tutorial tips).
     * German readers get {@code ui.guide.*} from lang/de.yml. Items strips the colours.
     */
    public static java.util.List<String> guideTips(Player player) {
        java.util.List<String> tips = new java.util.ArrayList<>();
        if (player == null) {
            return tips;
        }
        QuestManager qm = questManager();
        if (qm == null) {
            tips.add(guide(player, "no_quests", "Talk to §aEgon §7on the pier at Anker Harbour — start orientation there."));
            return tips;
        }
        Stop stop = nextStop(player, qm);
        if (stop == null) {
            return tips;
        }
        int act = act(player, qm);
        tips.add(actLine(player, 1, act, guide(player, "act1", "Act I · Anker Harbour — Egon, the Fell, your kit")));
        tips.add(actLine(player, 2, act, guide(player, "act2", "Act II · The Capital — your loadout & Codex, then boosters")));
        tips.add(actLine(player, 3, act, guide(player, "act3", "Act III · The Roads — Sergeant Vex, then the stamp")));

        Quest active = qm.findActiveOrReadyQuest(player);
        if (active != null && isTutorialQuest(active.getId())) {
            String title = LangPack.questTitle(player, active.getId(), active.getTitle());
            String description = LangPack.questDescription(player, active.getId(), active.getDescription());
            tips.add(guide(player, "active", "Active quest: §f{0}§7 — {1}")
                    .replace("{0}", title)
                    .replace("{1}", description));
        }

        if (stop == EGON) {
            tips.add(guide(player, "egon", "Go to §aEgon §7on the pier at §fAnker Harbour§7. He's your first stop."));
            tips.add(guide(player, "egon_trail", "Follow the green glow on the planks — it leads to Egon."));
        } else if (stop == FORAGER) {
            tips.add(guide(player, "forager_fell", "§aThe Forager §7up the hill shows you the Fell — swing when the bar hits green."));
        } else if (stop == EGON_OAK) {
            tips.add(guide(player, "oak_back", "Bring the oak logs back to §aEgon §7on the pier."));
        } else if (stop == LEDGER) {
            tips.add(guide(player, "ledger_capital", "Next: §dMiss Ledger §7at the Capital — Nether Star → Teleports → Capital, or §f/capital§7."));
        } else if (stop == TEMPER) {
            tips.add(guide(player, "temper_south", "Next: §eTemper §7on the south road toward the Mines — fuse one booster."));
        } else if (stop == VEX) {
            tips.add(guide(player, "vex_gate", "Next: §cSergeant Vex §7at the Borderlands gate — armour on, blade up."));
        } else {
            tips.add(guide(player, "stamp", "Back to §dMiss Ledger §7— she stamps orientation shut and shows you the isles."));
        }
        int level = QuestSkillGate.accountLevel(player);
        if (level > 0 && level < AUTO_PICKUP_LEVEL) {
            tips.add(guide(player, "autopickup", "Auto pickup turns on at Aetherion Level §f5§7 — you're Level §f{0}§7.")
                    .replace("{0}", String.valueOf(level)));
        }
        return tips;
    }

    /** Aetherion Level at which drops start flying to you (Items {@code AutoPickupListener.UNLOCK_LEVEL}). */
    public static final int AUTO_PICKUP_LEVEL = 5;

    private static String actLine(Player player, int line, int current, String text) {
        String mark = line < current ? "§a✔ " : line == current ? "§e▶ " : "§8· ";
        return mark + (line == current ? "§f" : "§7") + text;
    }

    private static String guide(Player player, String key, String english) {
        return LangPack.ui(player, "guide." + key, english);
    }

    /* =========================================================
     * REDIRECTS
     * ========================================================= */

    /** How ready the player looks for Vex's first fight — cheap, soft check (no locks). */
    public enum CombatReadiness {
        NO_ARMOUR,
        NO_BOOSTER,
        READY
    }

    /**
     * Armour worn (2+ pieces — Egon's kit or better) and Temper's booster lesson filed.
     * Used only to decide whether Sergeant Vex opens his Borderlands lesson with a gear check.
     */
    public static CombatReadiness combatReadiness(Player player, QuestManager questManager) {
        if (player == null) {
            return CombatReadiness.READY;
        }
        if (CombatTeach.armourPieces(player) < 2) {
            return CombatReadiness.NO_ARMOUR;
        }
        if (questManager != null && !questCompleted(player, questManager, "lesson_boost")) {
            return CombatReadiness.NO_BOOSTER;
        }
        return CombatReadiness.READY;
    }

    /** Soft next stop while orientation is still open, said by whoever the player just bumped into. */
    public static void redirectToTutorial(Player player, String speakerName) {
        if (player == null) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "Someone" : speakerName;
        player.sendMessage("");
        npcSay(player, name, LangPack.say(player, "redirect.lead", "Where you're actually meant to be:"));

        QuestManager qm = questManager();
        Stop stop = qm == null ? STAMP : nextStop(player, qm);
        if (stop == null) {
            stop = STAMP;
        }
        npcSay(player, name, redirectLine(player, stop));
        player.sendMessage("");
        point(player, stop);
    }

    private static String redirectLine(Player player, Stop stop) {
        if (stop == EGON) {
            return LangPack.say(player, "redirect.egon_pier", "§aEgon §fon the pier. He kits you out — that's where it starts.");
        }
        if (stop == FORAGER) {
            return LangPack.say(player, "redirect.forager", "§aThe Forager§f, up the hill from the pier. Oak, on the green.");
        }
        if (stop == EGON_OAK) {
            return LangPack.say(player, "redirect.egon_oak", "§aEgon§f's waiting on his oak. Pier.");
        }
        if (stop == LEDGER) {
            return LangPack.say(player, "redirect.ledger_capital", "§dMiss Ledger§f, Capital. Nether Star → Teleports, or §e/capital§f.");
        }
        if (stop == TEMPER) {
            return LangPack.say(player, "redirect.temper_south", "§eTemper§f, south road toward the Mines. Boosters.");
        }
        if (stop == VEX) {
            return LangPack.say(player, "redirect.vex_gate", "§cSergeant Vex§f, Borderlands gate. He wants you kitted.");
        }
        return LangPack.say(player, "redirect.stamp", "§dMiss Ledger §fcloses orientation. Go get stamped.");
    }

    /**
     * Back after a relog or restart mid-orientation with nothing tracked: the soft arrow (in memory only) is gone,
     * so put the spine's next stop back on the bar — no speech, one action-bar line. Never after the stamp, never
     * over a running quest or a trail that already exists. Egon's own nudge covers the very first stop.
     */
    public static void resumeTrail(Player player) {
        QuestManager qm = questManager();
        if (player == null || !player.isOnline() || qm == null || graduated(player, qm)) {
            return;
        }
        if (qm.findActiveOrReadyQuest(player) != null || QuestHint.pendingNpc(player) != null) {
            return;
        }
        Stop stop = nextStop(player, qm);
        if (stop == null || stop == EGON) {
            return;
        }
        point(player, stop);
    }

    private static QuestManager questManager() {
        try {
            de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
            return plugin == null ? null : plugin.getQuestManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* =========================================================
     * MISS LEDGER (Act II) — opens once Egon's kit is on
     * ========================================================= */

    /** Miss Ledger opens the loadout once Act I (Egon's kit) is filed. */
    public static boolean ledgerUnlocked(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return false;
        }
        return questCompleted(player, questManager, "gather_wood");
    }

    /**
     * @return deny line if lesson_manager is blocked, else null
     */
    public static String ledgerFailReason(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return null;
        }
        if (ledgerUnlocked(player, questManager)) {
            return null;
        }
        return LangPack.say(player, "gate.ledger_kit_first", "§eEgon's kit first. §7Ten oak on the harbour pier, then I open your loadout.");
    }

    /** Ledger before Act I: back to the harbour. */
    public static void redirectBeforeLedger(Player player, String ledgerName) {
        if (player == null) {
            return;
        }
        String name = ledgerName == null || ledgerName.isBlank() ? "Miss Ledger" : ledgerName;
        player.sendMessage("");
        npcSay(player, name, LangPack.say(player, "ledger.early_kit", "You're early, {player}. No kit, no loadout."));
        npcSay(player, name, LangPack.say(player, "ledger.early_egon", "§aEgon§f, harbour pier. Ten oak. Then come back."));
        player.sendMessage("");
        QuestManager qm = questManager();
        Stop stop = qm == null ? EGON : nextStop(player, qm);
        point(player, stop == null ? EGON : stop);
    }

    public static String[] ledgerBlockedLines() {
        return new String[] {
                "You're early, {player}. No kit, no loadout.",
                "§aEgon§f, on the harbour pier. Ten oak — the Forager shows you how.",
                "Then I open your Skills. Not a minute before."
        };
    }

    /* =========================================================
     * RITE WARDEN — after the stamp's worth of orientation
     * ========================================================= */

    /** Borderlands Rite Warden — after full orientation. */
    public static boolean riteKeeperUnlocked(Player player, QuestManager questManager) {
        return tutorialDone(player, questManager);
    }

    public static String riteKeeperFailReason(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return null;
        }
        if (riteKeeperUnlocked(player, questManager)) {
            return null;
        }
        return "§eOrientation first. §7Miss Ledger stamps you through after Sergeant Vex.";
    }

    public static String[] riteKeeperBlockedLines() {
        return new String[] {
                "Not yet, {player}. The waste doesn't take rookies mid-lesson.",
                "Ledger, Temper, Sergeant Vex. Then we discuss spirits and the powder altar.",
                "Miss Ledger closes the tutorial. Come back when she has."
        };
    }


    private static void npcSay(Player player, String speakerName, String line) {
        if (player == null || line == null) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "Someone" : speakerName;
        QuestNPC npc = findByName(name);
        if (npc != null) {
            LivingNpcProfile.say(player, npc, line);
            return;
        }
        LivingNpcProfile.say(player, null, name, line);
    }


    private static QuestNPC findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (QuestNPC npc : QuestNPCRegistry.getAll().values()) {
            if (name.equalsIgnoreCase(npc.getName())) {
                return npc;
            }
        }
        return null;
    }
}
