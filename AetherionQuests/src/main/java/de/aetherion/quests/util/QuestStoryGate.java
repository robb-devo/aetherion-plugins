package de.aetherion.quests.util;


import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.ui.QuestHint;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;


/**
 * Soft story order gates (not skill gates).
 */
public final class QuestStoryGate {


    private QuestStoryGate() {
    }

    /**
     * First-hour cast. Everyone else waits until {@link #tutorialDone}.
     * Soft order: Mine → Temper → Ledger → Fields (Farmer + Lark) → back to Ledger.
     */
    private static final Set<String> TUTORIAL_NPCS = Set.of(
            "egon",
            "lumberjack",
            "quartermaster",
            "fisher",
            "fishmonger",
            "craftsman",
            "farmer",
            "lark",
            "foreman",
            "miner",
            "ledger",
            "vex",
            "booster_tutor",
            "stall_crumb",
            "stall_tack",
            "stall_brine",
            "dock_whisper",
            "bait_theory",
            // Farm isle portal guide — portal itself is Farming-skill gated only.
            "farm_isle_guide",
            // Harbour forage pad guide — soft hint to the isle clerk.
            "forage_pad_guide",
            // Eldervale welcome — pad is blueprint-gated; NPC is flavor only.
            "eldervale_welcome",
            // Harbour flavor — XP tip + casino host + crystal desk + coin desk
            "bar_whisper",
            "vince",
            "liquidator",
            "merchant"
    );

    /** Soft spine that must be filed before the world opens. Miss Ledger stamps it shut. */
    private static final String[] TUTORIAL_DONE_QUESTS = {
            "lesson_boost",
            "lesson_manager",
            "farm_hand",
            "pocket_zoo"
    };

    /**
     * Tutorial content is done after Temper + Ledger skills + Fields (wheat + pet).
     * Miss Ledger delivers the graduation / help desk — Farmer and Lark do not.
     */
    public static boolean tutorialDone(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return false;
        }
        for (String id : TUTORIAL_DONE_QUESTS) {
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

    public static boolean isTutorialNpc(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        return TUTORIAL_NPCS.contains(npcId.toLowerCase(Locale.ROOT));
    }

    private static final Set<String> TUTORIAL_QUESTS = Set.of(
            "welcome_aboard",
            "gather_wood",
            "forge_coal",
            "first_shift",
            "farm_hand",
            "a_good_catch",
            "dock_pass",
            "pocket_zoo",
            "lesson_steel",
            "lesson_boost",
            "lesson_manager"
    );

    public static boolean isTutorialQuest(String questId) {
        if (questId == null || questId.isBlank()) {
            return false;
        }
        return TUTORIAL_QUESTS.contains(questId.toLowerCase(Locale.ROOT));
    }

    /** All tutorial quest IDs (for admin / Dev Menu skip). */
    public static Set<String> tutorialQuestIds() {
        return TUTORIAL_QUESTS;
    }

    /** Gate quests that mark orientation complete. */
    public static String[] tutorialDoneQuestIds() {
        return TUTORIAL_DONE_QUESTS.clone();
    }

    /** Post-tutorial / future NPCs while tutorial is still open. */
    public static boolean blockedByTutorial(Player player, QuestManager questManager, String npcId) {
        if (isTutorialNpc(npcId)) {
            return false;
        }
        // Rite Warden has its own gate — still tutorial-locked, but handled separately.
        if ("rite_keeper".equalsIgnoreCase(npcId)) {
            return false;
        }
        // Farm isle guide / portal: Farming skill only — never tutorial.
        if ("farm_isle_guide".equalsIgnoreCase(npcId)) {
            return false;
        }
        // Forage pad guide: soft hint to island clerk — talk anytime.
        if ("forage_pad_guide".equalsIgnoreCase(npcId)) {
            return false;
        }
        // Eldervale welcome: pad is blueprint-gated; talk anytime.
        if ("eldervale_welcome".equalsIgnoreCase(npcId)) {
            return false;
        }
        return !tutorialDone(player, questManager);
    }

    public static String[] tutorialBlockedLines() {
        return new String[] {
                "Complete the tutorial first.",
                "Harbour → Mine → Temper → Miss Ledger → Fields. Then Ledger stamps you free.",
                "Come back when she's stamped you. This desk isn't for rookies mid-lesson."
        };
    }

    /**
     * Plain tips for /guide and Discord (same spine as {@link #redirectToTutorial}).
     * Empty list = tutorial finished (caller may add post-tutorial tips).
     */
    public static java.util.List<String> guideTips(Player player) {
        java.util.List<String> tips = new java.util.ArrayList<>();
        if (player == null) {
            return tips;
        }
        QuestManager qm = questManager();
        if (qm == null) {
            tips.add("Talk to §aEgon §7on the pier at Anker Harbour — start orientation there.");
            return tips;
        }
        if (tutorialDone(player, qm)) {
            return tips;
        }

        Quest active = qm.findActiveOrReadyQuest(player);
        if (active != null && isTutorialQuest(active.getId())) {
            tips.add("Active quest: §f" + active.getTitle() + "§7 — " + active.getDescription());
        }

        // Soft spine: Harbour (Egon) → QM → Mine → Temper → Ledger → Fields → Ledger.
        if (!questCompleted(player, qm, "gather_wood")) {
            Quest welcome = qm.getQuest("welcome_aboard");
            QuestState welcomeState = welcome == null
                    ? QuestState.COMPLETED
                    : qm.getQuestState(player, welcome);
            if (welcomeState == QuestState.AVAILABLE) {
                tips.add("Go to §aEgon §7on the pier at §fAnker Harbour§7. He's your first stop.");
                tips.add("Follow the green particles / trail if you see them — they point to Egon.");
            } else if (welcomeState == QuestState.ACTIVE || welcomeState == QuestState.READY) {
                tips.add("Egon sent you to the §aForager §7up the hill — talk to him, then chop oak.");
                tips.add("Bring the oak logs back to §aEgon §7on the pier.");
            } else {
                tips.add("Chop oak and deliver §f10 oak logs §7to §aEgon §7on the pier.");
            }
            return tips;
        }
        if (!questCompleted(player, qm, "forge_coal")) {
            tips.add("Next: §eQuartermaster §7past the little market — coal run (§f20 coal§7).");
            return tips;
        }
        if (!questCompleted(player, qm, "first_shift")) {
            tips.add("Next: §eShaft Foreman §7at Shabby Mine — finish his shift.");
            return tips;
        }
        if (!questCompleted(player, qm, "lesson_boost")) {
            tips.add("Next: §eTemper §7(Booster Tutor) at the harbour — fuse one booster.");
            return tips;
        }
        if (!questCompleted(player, qm, "lesson_manager")) {
            tips.add("Next: §dMiss Ledger §7at Capital — unlocks Skills in the Manager.");
            return tips;
        }
        if (!questCompleted(player, qm, "farm_hand")) {
            tips.add("Next: §eFarmer §7at the Fields — wheat.");
            return tips;
        }
        if (!questCompleted(player, qm, "pocket_zoo")) {
            tips.add("Next: §dLark §7at the Fields fence — one pet catch.");
            return tips;
        }

        tips.add("Return to §dMiss Ledger §7— she closes orientation / tutorial.");
        return tips;
    }

    /** Soft next stop while orientation is still open. */
    public static void redirectToTutorial(Player player, String speakerName) {
        if (player == null) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "Someone" : speakerName;
        player.sendMessage("");
        npcSay(player, name, "redirect.tutorial", "Complete the tutorial first.");

        QuestManager qm = questManager();
        if (qm == null) {
            npcSay(player, name, "redirect.ledger_fields", "§eMiss Ledger §fcloses orientation after the Fields.");
            player.sendMessage("");
            QuestHint.show(player, "ledger", "Miss Ledger");
            return;
        }

        // Soft spine: Harbour (Egon) → QM → Mine → Temper → Ledger → Fields → Ledger.
        if (!questCompleted(player, qm, "gather_wood")) {
            npcSay(player, name, "redirect.egon", "§aEgon §fat the pier — oak first. Then the Quartermaster.");
            player.sendMessage("");
            QuestHint.show(player, "egon", "Egon");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Egon · wood",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
            return;
        }
        if (!questCompleted(player, qm, "forge_coal")) {
            npcSay(player, name, "redirect.qm", "§eQuartermaster §fpast the little market — coal run next.");
            player.sendMessage("");
            QuestHint.show(player, "quartermaster", "Quartermaster");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Quartermaster · coal",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "first_shift")) {
            npcSay(player, name, "redirect.foreman", "§eShaft Foreman §fat Shabby Mine — finish his shift.");
            player.sendMessage("");
            QuestHint.show(player, "foreman", "Shaft Foreman");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Mines · Shaft Foreman",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "lesson_boost")) {
            npcSay(player, name, "redirect.temper", "§eTemper §f— Booster Tutor. Fuse one booster, then keep going.");
            player.sendMessage("");
            QuestHint.show(player, "booster_tutor", "Temper");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Temper first",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            return;
        }
        if (!questCompleted(player, qm, "lesson_manager")) {
            npcSay(player, name, "redirect.ledger_skills", "§dMiss Ledger §fat Capital — Skills in the Manager.");
            player.sendMessage("");
            QuestHint.show(player, "ledger", "Miss Ledger");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Miss Ledger · Skills",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }
        if (!questCompleted(player, qm, "farm_hand")) {
            npcSay(player, name, "redirect.farmer", "§eFarmer §fat the Fields — wheat next.");
            player.sendMessage("");
            QuestHint.show(player, "farmer", "Farmer");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Fields · Farmer",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "pocket_zoo")) {
            npcSay(player, name, "redirect.lark", "§dLark §fat the fence — one catch for his collection.");
            player.sendMessage("");
            QuestHint.show(player, "lark", "Lark");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Tutorial · Fields · Lark",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }

        npcSay(player, name, "redirect.stamp", "§dMiss Ledger §fcloses orientation. Go get stamped.");
        player.sendMessage("");
        QuestHint.show(player, "ledger", "Miss Ledger");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Tutorial · Miss Ledger · graduation",
                net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
        ));
    }

    private static QuestManager questManager() {
        try {
            de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
            return plugin == null ? null : plugin.getQuestManager();
        } catch (Throwable ignored) {
            return null;
        }
    }


    /** Miss Ledger / Skills only after First Shift is filed. */
    public static boolean ledgerUnlocked(Player player, QuestManager questManager) {
        if (player == null || questManager == null) {
            return false;
        }
        Quest firstShift = questManager.getQuest("first_shift");
        if (firstShift == null) {
            return true;
        }
        return questManager.getQuestState(player, firstShift) == QuestState.COMPLETED;
    }

    /** Borderlands Rite Warden — after full orientation (Ledger stamps tutorial). */
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
        return "§eOrientation first. §7Miss Ledger closes the tutorial after the Fields.";
    }

    public static void redirectToTemper(Player player, String speakerName) {
        if (player == null) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "Rite Warden" : speakerName;
        QuestManager qm = questManager();
        // Prefer soft next step if Temper is already done.
        if (qm != null && questCompleted(player, qm, "lesson_boost")) {
            redirectToTutorial(player, name);
            return;
        }
        player.sendMessage("");
        npcSay(player, name, "redirect.rite_early", "You're early. The waste doesn't hand out spirits to soft gear.");
        npcSay(player, name, "redirect.rite_temper", "§eTemper §fat the harbour — fuse a booster. Then keep orientation going.");
        player.sendMessage("");
        QuestHint.show(player, "booster_tutor", "Temper");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Hint · Temper · Booster Tutor",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }

    public static String[] riteKeeperBlockedLines() {
        return new String[] {
                "Locked. Finish orientation first — Fields, then Miss Ledger stamps you free.",
                "Temper, Ledger, wheat, one pet. Then we talk spirits.",
                "Miss Ledger closes the tutorial. Come back when she has."
        };
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
        return "§eShaft Foreman first. §7Mine shift, then we open Skills.";
    }


    public static void redirectToForeman(Player player, String ledgerName) {
        if (player == null) {
            return;
        }
        String name = ledgerName == null || ledgerName.isBlank() ? "Miss Ledger" : ledgerName;
        player.sendMessage("");
        npcSay(player, name, "redirect.ledger_early", "You're early. Skills aren't open yet.");
        npcSay(player, name, "redirect.ledger_foreman", "§eShaft Foreman §fat the Mines — do his shift. Then come back.");
        player.sendMessage("");
        QuestHint.show(player, "foreman", "Shaft Foreman");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Hint · Mines · Shaft Foreman",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW
        ));
    }


    public static String[] ledgerBlockedLines() {
        return new String[] {
                "You're early. Skills stay locked until the mine shift is done.",
                "Shaft Foreman at the Mines. Finish his shift — then come back.",
                "Then I unlock the Manager Skills tab. Not before."
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

    private static void npcSay(Player player, String speakerName, String key, String english) {
        npcSay(player, speakerName, de.aetherion.quests.lang.LangPack.msg(player, "say." + key, english));
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
