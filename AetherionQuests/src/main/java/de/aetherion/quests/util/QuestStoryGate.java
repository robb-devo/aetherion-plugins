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
                "Not yet, {player}. This desk opens after orientation.",
                "Harbour → Mine → Temper → Miss Ledger → Fields. Then you're free."
        };
    }

    /**
     * Plain tips for /guide and Discord (same spine as {@link #redirectToTutorial}).
     * One "next" at a time. Empty list = tutorial finished (caller may add post-tutorial tips).
     * German readers get {@code ui.guide.*} from lang/de.yml.
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
        if (tutorialDone(player, qm)) {
            return tips;
        }

        Quest active = qm.findActiveOrReadyQuest(player);
        if (active != null && isTutorialQuest(active.getId())) {
            String title = de.aetherion.quests.lang.LangPack.questTitle(player, active.getId(), active.getTitle());
            String description = de.aetherion.quests.lang.LangPack.questDescription(player, active.getId(), active.getDescription());
            tips.add(guide(player, "active", "Active quest: §f{0}§7 — {1}")
                    .replace("{0}", title)
                    .replace("{1}", description));
        }

        // Soft spine: Harbour (Egon) → QM → Mine → Temper → Ledger → Fields → Ledger.
        if (!questCompleted(player, qm, "gather_wood")) {
            Quest welcome = qm.getQuest("welcome_aboard");
            QuestState welcomeState = welcome == null
                    ? QuestState.COMPLETED
                    : qm.getQuestState(player, welcome);
            if (welcomeState == QuestState.AVAILABLE) {
                tips.add(guide(player, "egon", "Go to §aEgon §7on the pier at §fAnker Harbour§7. He's your first stop."));
                tips.add(guide(player, "egon_trail", "Follow the green glow on the planks — it leads to Egon."));
            } else if (welcomeState == QuestState.ACTIVE || welcomeState == QuestState.READY) {
                tips.add(guide(player, "forager", "Egon sent you to the §aForager §7up the hill — talk to him, then chop oak."));
                tips.add(guide(player, "oak_back", "Bring the oak logs back to §aEgon §7on the pier."));
            } else {
                tips.add(guide(player, "oak_deliver", "Chop oak and deliver §f10 oak logs §7to §aEgon §7on the pier."));
            }
            return tips;
        }
        if (!questCompleted(player, qm, "forge_coal")) {
            tips.add(guide(player, "quartermaster", "Next: §eQuartermaster §7past the little market — coal run (§f20 coal§7)."));
            return tips;
        }
        if (!questCompleted(player, qm, "first_shift")) {
            tips.add(guide(player, "foreman", "Next: §eShaft Foreman §7at Shabby Mine — finish his shift (§f/mines§7)."));
            return tips;
        }
        if (!questCompleted(player, qm, "lesson_boost")) {
            tips.add(guide(player, "temper", "Next: §eTemper §7(Booster Tutor) on the road to Capital — fuse one booster."));
            return tips;
        }
        if (!questCompleted(player, qm, "lesson_manager")) {
            tips.add(guide(player, "ledger", "Next: §dMiss Ledger §7at Capital — she opens Skills in the Manager."));
            return tips;
        }
        if (!questCompleted(player, qm, "farm_hand")) {
            tips.add(guide(player, "farmer", "Next: §eFarmer §7at the Fields — 48 wheat, shoo the birds."));
            return tips;
        }
        if (!questCompleted(player, qm, "pocket_zoo")) {
            tips.add(guide(player, "lark", "Next: §dLark §7at the Fields fence — one pet catch."));
            return tips;
        }

        tips.add(guide(player, "stamp", "Back to §dMiss Ledger §7— she stamps orientation shut and shows you the isles."));
        return tips;
    }

    private static String guide(Player player, String key, String english) {
        return de.aetherion.quests.lang.LangPack.ui(player, "guide." + key, english);
    }

    /** How ready the player looks for Vex's first fight — cheap, soft check (no locks). */
    public enum CombatReadiness {
        NO_ARMOUR,
        NO_BOOSTER,
        READY
    }

    /**
     * Armour worn (2+ pieces — Egon's kit or better) and Temper's booster lesson filed.
     * Used only to decide whether Sergeant Vex opens with a gear check.
     */
    public static CombatReadiness combatReadiness(Player player, QuestManager questManager) {
        if (player == null) {
            return CombatReadiness.READY;
        }
        int worn = 0;
        for (org.bukkit.inventory.ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                worn++;
            }
        }
        if (worn < 2) {
            return CombatReadiness.NO_ARMOUR;
        }
        if (questManager != null && !questCompleted(player, questManager, "lesson_boost")) {
            return CombatReadiness.NO_BOOSTER;
        }
        return CombatReadiness.READY;
    }

    /** Soft next stop while orientation is still open. */
    public static void redirectToTutorial(Player player, String speakerName) {
        if (player == null) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "Someone" : speakerName;
        player.sendMessage("");
        npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.lead", "Where you're actually meant to be:"));

        QuestManager qm = questManager();
        if (qm == null) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.ledger_fields", "§eMiss Ledger §fcloses orientation after the Fields."));
            player.sendMessage("");
            QuestHint.show(player, "ledger", "Miss Ledger");
            return;
        }

        // Soft spine: Harbour (Egon) → QM → Mine → Temper → Ledger → Fields → Ledger.
        if (!questCompleted(player, qm, "gather_wood")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.egon", "§aEgon §fat the pier — oak first. Then the Quartermaster."));
            player.sendMessage("");
            QuestHint.show(player, "egon", "Egon");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_egon", "Tutorial · Egon · wood"),
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
            return;
        }
        if (!questCompleted(player, qm, "forge_coal")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.quartermaster", "§eQuartermaster §fpast the little market — coal run next."));
            player.sendMessage("");
            QuestHint.show(player, "quartermaster", "Quartermaster");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_quartermaster", "Tutorial · Quartermaster · coal"),
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "first_shift")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.foreman", "§eShaft Foreman §fat Shabby Mine — finish his shift."));
            player.sendMessage("");
            QuestHint.show(player, "foreman", "Shaft Foreman");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_foreman", "Tutorial · Mines · Shaft Foreman"),
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "lesson_boost")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.temper", "§eTemper §f— Booster Tutor. Fuse one booster, then keep going."));
            player.sendMessage("");
            QuestHint.show(player, "booster_tutor", "Temper");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_temper", "Tutorial · Temper first"),
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            return;
        }
        if (!questCompleted(player, qm, "lesson_manager")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.ledger_skills", "§dMiss Ledger §fat Capital — Skills in the Manager."));
            player.sendMessage("");
            QuestHint.show(player, "ledger", "Miss Ledger");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_ledger", "Tutorial · Miss Ledger · Skills"),
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }
        if (!questCompleted(player, qm, "farm_hand")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.farmer", "§eFarmer §fat the Fields — wheat next."));
            player.sendMessage("");
            QuestHint.show(player, "farmer", "Farmer");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_farmer", "Tutorial · Fields · Farmer"),
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }
        if (!questCompleted(player, qm, "pocket_zoo")) {
            npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.lark", "§dLark §fat the fence — one catch for his collection."));
            player.sendMessage("");
            QuestHint.show(player, "lark", "Lark");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_lark", "Tutorial · Fields · Lark"),
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }

        npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "redirect.stamp", "§dMiss Ledger §fcloses orientation. Go get stamped."));
        player.sendMessage("");
        QuestHint.show(player, "ledger", "Miss Ledger");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.quests.lang.LangPack.ui(player, "tip.tutorial_graduation", "Tutorial · Miss Ledger · graduation"),
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
        npcSay(player, name, "You're early. The waste doesn't hand out spirits to soft gear.");
        npcSay(player, name, "§eTemper §fat the harbour — fuse a booster. Then keep orientation going.");
        player.sendMessage("");
        QuestHint.show(player, "booster_tutor", "Temper");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Hint · Temper · Booster Tutor",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }

    public static String[] riteKeeperBlockedLines() {
        return new String[] {
                "Not yet, {player}. The waste doesn't take rookies mid-lesson.",
                "Temper, Ledger, wheat, one pet. Then we discuss spirits and the powder altar.",
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
        return de.aetherion.quests.lang.LangPack.say(player, "gate.ledger_foreman_first", "§eShaft Foreman first. §7Mine shift, then we open Skills.");
    }


    public static void redirectToForeman(Player player, String ledgerName) {
        if (player == null) {
            return;
        }
        String name = ledgerName == null || ledgerName.isBlank() ? "Miss Ledger" : ledgerName;
        player.sendMessage("");
        npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "ledger.early", "You're early. Skills aren't open yet."));
        npcSay(player, name, de.aetherion.quests.lang.LangPack.say(player, "ledger.early_foreman", "§eShaft Foreman §fat the Mines — do his shift. Then come back."));
        player.sendMessage("");
        QuestHint.show(player, "foreman", "Shaft Foreman");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.quests.lang.LangPack.ui(player, "tip.mines_foreman", "Hint · Mines · Shaft Foreman"),
                net.kyori.adventure.text.format.NamedTextColor.YELLOW
        ));
    }


    public static String[] ledgerBlockedLines() {
        return new String[] {
                "You're early, {player}. Skills stay shut until the mine shift's done.",
                "§eShaft Foreman§f, at the Mines. Finish his shift, then come back.",
                "Then I open the Skills tab. Not a minute before."
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
