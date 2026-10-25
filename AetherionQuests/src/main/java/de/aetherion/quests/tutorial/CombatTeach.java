package de.aetherion.quests.tutorial;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.reward.Reward;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orientation Act III, beat 6 — "How You Fight Here": Sergeant Vex's combat drill at the Borderlands gate.
 * <p>
 * Kit check plus three moves, each on the Items combat kit's training golem (yellow "!" jab, red "!!" slam):
 * <ul>
 *   <li>{@value #READY}: credited on accept. Everyone already wears Egon's kit, so Vex no longer inspects it
 *   (Robb, 2026-10-25); {@link OrientationWatch} still credits it for older accepts.</li>
 *   <li>{@value #PARRY}: a perfect parry (swap-hands key, F by default) — the golem's jab.</li>
 *   <li>{@value #DODGE}: a roll (Sneak + Jump) when the golem's unparryable slam turns white.</li>
 *   <li>{@value #FINISHER}: three timed (full-charge) hits on one target.</li>
 * </ul>
 * The moves are reported by Items ({@code combat.system.CombatSystem#drill} → {@code QuestProgressHook.noteUsed})
 * from any fight, so Borderlands mobs count too, but the golem means nobody waits for a mob: Vex puts one up
 * beside the player on accept and again on any visit without one. A refused spawn gets a "find open ground"
 * line instead.
 * <p>
 * The drill needs the Items kit. {@value #LIVE_PATH} (default on) and the kit running at startup put the moves
 * on the quest; otherwise it stays the gear-check stub. If the kit goes off later, {@link OrientationWatch}
 * credits the moves once the kit check passes, so nobody is stuck. Players who passed the stub stay passed
 * ({@code QuestStoryGate.tutorialDone} only reads the quest state).
 */
public final class CombatTeach {

    public static final String QUEST_ID = "lesson_combat";
    public static final String NPC_ID = "vex";
    /** Kit on, blade up. */
    public static final String READY = "COMBAT_READY";
    /** Drill moves, reported by Items {@code CombatSystem} (keep the strings in step). */
    public static final String PARRY = "COMBAT_PARRY";
    public static final String DODGE = "COMBAT_DODGE";
    public static final String FINISHER = "COMBAT_FINISHER";
    /** Config switch for the drill; on unless a server sets it false (then the gear-check stub runs). */
    public static final String LIVE_PATH = "onboarding.combat-teach.live";

    private static final String[] MOVES = {PARRY, DODGE, FINISHER};
    /** A golem farther away than this counts as gone: Vex puts up a fresh one. */
    private static final double GOLEM_RANGE = 16.0;

    /** Set by {@link #register}: the quest carries the drill moves (not the stub). */
    private static boolean drill;

    private CombatTeach() {
    }

    /** Registers the spine quest. Called from {@code QuestRegistry.registerAll}, after Items is enabled. */
    public static void register(QuestManager questManager) {
        drill = switchOn() && kitRunning();
        Quest quest = new Quest(
                QUEST_ID,
                "How You Fight Here",
                drill
                        ? "Sergeant Vex's drill at the Borderlands gate: parry his golem, roll through its slam, land a finisher."
                        : "Sergeant Vex wants you kitted: armour on, blade in hand. The full drill lands with the new combat."
        );
        quest.setServiceId(NPC_ID);
        quest.setTurnInNpcId(NPC_ID);
        quest.addObjective(new Objective(ObjectiveType.USE, READY, 1));
        if (drill) {
            for (String move : MOVES) {
                quest.addObjective(new Objective(ObjectiveType.USE, move, 1));
            }
        }
        quest.addReward(new Reward("Coins", 60));
        quest.addReward(new Reward("XP", 60));
        questManager.registerQuest(quest);
    }

    /** True when the quest runs the drill (switch on and the Items kit was up at startup). */
    public static boolean live() {
        return drill;
    }

    private static boolean switchOn() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null || plugin.getConfig().getBoolean(LIVE_PATH, true);
    }

    /** The Items combat kit is loaded and enabled (an older Items jar without the drill API counts as off). */
    public static boolean kitRunning() {
        try {
            return de.aetherion.items.combat.system.CombatSystem.running();
        } catch (LinkageError ignored) {
            return false;
        }
    }

    public static boolean isMove(String target) {
        for (String move : MOVES) {
            if (move.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /* =========================================================
     * KIT CHECK
     * ========================================================= */

    /** Kit check: two armour pieces worn and a weapon in the main hand. */
    public static boolean ready(Player player) {
        return player != null && armourPieces(player) >= 2 && holdsWeapon(player);
    }

    public static int armourPieces(Player player) {
        int worn = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                worn++;
            }
        }
        return worn;
    }

    public static boolean holdsWeapon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        String type = hand.getType().name();
        if (type.endsWith("_SWORD") || type.endsWith("_AXE") || hand.getType() == Material.BOW
                || hand.getType() == Material.CROSSBOW || hand.getType() == Material.TRIDENT
                || hand.getType() == Material.MACE) {
            return true;
        }
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            String id = items == null || items.getItemManager() == null ? null : items.getItemManager().getItemId(hand);
            if (id == null) {
                return false;
            }
            String lower = id.toLowerCase(Locale.ROOT);
            for (String word : new String[] {"sword", "blade", "katana", "cleaver", "axe", "bow", "wand", "staff",
                    "spear", "dagger", "mace", "scythe"}) {
                if (lower.contains(word)) {
                    return true;
                }
            }
        } catch (NoClassDefFoundError ignored) {
        }
        return false;
    }

    /* =========================================================
     * LINES + FLOW
     * ========================================================= */

    /** Vex's offer: the drill in three lines (dialog {@code vex_combat_drill}), or the stub's kit check. */
    public static String[] introLines(Player player) {
        if (drill) {
            return LangPack.dialogs(player, "vex_combat_drill", new String[] {
                    "Sergeant Vex. Spam-clicking gets you killed out there. So we drill on my golem.",
                    "Yellow §e!§f: parry when it flashes white. Red §c!!§f: roll clear when it turns white.",
                    "Then three full swings in a row: a §6finisher§f. Your keys are in chat. Ready?"
            });
        }
        return LangPack.dialogs(player, "vex_combat_intro", new String[] {
                "Sergeant Vex. Fighting here isn't hold-click-and-pray.",
                "The drill's being rebuilt — footwork, timing, the lot. When it lands, I put you through it.",
                "Today: armour on — all of Egon's kit — and a blade in your hand. Then look at me."
        });
    }

    /** Vex's answer to "Sir, yes sir" (replaces his Borderlands "ten hostiles" bark for this beat). */
    public static String acceptLine(Player player) {
        return drill
                ? LangPack.say(player, "vex.drill_go", "Good. Golem's coming up beside you. Watch its fist, not my face.")
                : LangPack.say(player, "vex.combat_go", "Good. Kit on, blade up. Then look at me.");
    }

    /** Quest accepted: the kit counts at once, the golem goes up beside the player, the keys go to chat. */
    public static void started(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        de.aetherion.quests.bridge.QuestProgressBridge.noteUsed(player, READY);
        if (drill) {
            keys(player);
        }
        if (drill && kitRunning() && !armGolem(player)) {
            vex(player, noRoomLine(player));
        }
        if (!holdsWeapon(player)) {
            vex(player, bladeLine(player));
        }
    }

    /**
     * The drill's keys as the player has them bound (the client fills in its own key): a friend with his own
     * layout looked for F for ages. Chat, so it stays readable while the golem swings.
     */
    public static void keys(Player player) {
        boolean german = de.aetherion.core.exile.Say.german(player);
        net.kyori.adventure.text.format.TextColor key = net.kyori.adventure.text.format.NamedTextColor.YELLOW;
        net.kyori.adventure.text.format.TextColor txt = net.kyori.adventure.text.format.NamedTextColor.GRAY;
        player.sendMessage(net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text(german ? "⚔ Deine Tasten » " : "⚔ Your keys » ",
                        net.kyori.adventure.text.format.TextColor.color(0xFFC94A)))
                .append(net.kyori.adventure.text.Component.text(german ? "Parieren " : "Parry ", txt))
                .append(net.kyori.adventure.text.Component.keybind("key.swapOffhand", key))
                .append(net.kyori.adventure.text.Component.text(german ? " · Rolle " : " · Roll ", txt))
                .append(net.kyori.adventure.text.Component.keybind("key.sneak", key))
                .append(net.kyori.adventure.text.Component.text(" + ", txt))
                .append(net.kyori.adventure.text.Component.keybind("key.jump", key))
                .append(net.kyori.adventure.text.Component.text(german ? " · Finisher: 3 volle Schläge" : " · Finisher: 3 full swings", txt))
                .build());
    }

    private static String bladeLine(Player player) {
        return LangPack.say(player, "vex.drill_blade", "Blade in your main hand, {player}. A parry needs one.");
    }

    /** Short nudge while the beat is running and the kit isn't on yet. */
    public static String[] notReadyLines(Player player) {
        boolean armour = player != null && armourPieces(player) >= 2;
        return armour
                ? new String[] {LangPack.say(player, "vex.combat_blade", "Armour: fine. Hands: empty. §eBlade§f in your main hand, {player}.")}
                : new String[] {LangPack.say(player, "vex.combat_armour", "Armour in the armour slots, {player}. All of Egon's kit. Then the blade.")};
    }

    /**
     * Talking to Vex while the beat runs: the kit first, then a golem if theirs is gone (relog, wandered off,
     * expired) and one hint for the next move still open.
     */
    public static String[] visitLines(Player player) {
        if (!drill) {
            return ready(player) ? new String[0] : notReadyLines(player);
        }
        if (!holdsWeapon(player)) {
            return new String[] {bladeLine(player)};
        }
        if (!kitRunning()) {
            return new String[] {LangPack.say(player, "vex.drill_off", "Drill's grounded today. Kit on counts — report.")};
        }
        List<String> lines = new ArrayList<>();
        if (!hasGolem(player)) {
            lines.add(armGolem(player)
                    ? LangPack.say(player, "vex.drill_rearm", "Fresh golem, beside you. Again.")
                    : noRoomLine(player));
        }
        String hint = hintLine(player);
        if (hint != null) {
            lines.add(hint);
        }
        return lines.toArray(new String[0]);
    }

    /** One line for the first open move, in drill order. Null when all three are in. */
    private static String hintLine(Player player) {
        QuestManager qm = questManager();
        if (qm == null) {
            return null;
        }
        if (qm.getProgress(player, QUEST_ID, PARRY) < 1 || qm.getProgress(player, QUEST_ID, DODGE) < 1) {
            keys(player);
        }
        if (qm.getProgress(player, QUEST_ID, PARRY) < 1) {
            return LangPack.say(player, "vex.drill_hint_parry2",
                    "Yellow §e!§f: press your parry key the moment the fist flashes white. Not before.");
        }
        if (qm.getProgress(player, QUEST_ID, DODGE) < 1) {
            return LangPack.say(player, "vex.drill_hint_dodge2",
                    "Red §c!!§f: hold sneak, jump the moment it turns white.");
        }
        if (qm.getProgress(player, QUEST_ID, FINISHER) < 1) {
            return LangPack.say(player, "vex.drill_hint_finisher",
                    "Three hits, same target, each at full charge. Spam resets the count.");
        }
        return null;
    }

    /**
     * A drill move counted for the first time (QuestProgressBridge, after the progress landed): Vex calls it,
     * and closes the drill once the last objective is in.
     */
    public static void noteMove(Player player, String target) {
        if (player == null || target == null || !isMove(target)) {
            return;
        }
        String line;
        if (PARRY.equalsIgnoreCase(target)) {
            line = LangPack.say(player, "vex.drill_parried", "Clean parry. It reels — that's your opening.");
        } else if (DODGE.equalsIgnoreCase(target)) {
            line = LangPack.say(player, "vex.drill_rolled", "Out from under it. Good.");
        } else {
            line = LangPack.say(player, "vex.drill_finished", "Three clean swings. That's a finisher. Spam never gets there.");
        }
        vex(player, line);
        QuestManager qm = questManager();
        Quest quest = qm == null ? null : qm.getQuest(QUEST_ID);
        if (quest != null && qm.getQuestState(player, quest) == QuestState.READY) {
            vex(player, LangPack.say(player, "vex.drill_done", "That's the drill. Report, {player}."));
        }
    }

    /** Vex's lines once the pass is in (turn-in brief). */
    public static String[] passLines(Player player) {
        List<String> lines = new ArrayList<>();
        if (drill) {
            lines.add(LangPack.say(player, "vex.drill_pass", "Parry, roll, finisher. That's a pass — a real one."));
            lines.add(LangPack.say(player, "vex.drill_riposte",
                    "Free tip: a full swing right after a parry is a §dRiposte§f. §e/combat§f lists the rest."));
        } else {
            lines.add(LangPack.say(player, "vex.combat_pass", "Adequate. That's a pass — for today."));
        }
        lines.add(LangPack.say(player, "vex.combat_tempo",
                "Every blade has its §e⚡ Tempo§f, it's on the item. Daggers swing fast, hammers slow. Strike when the crosshair is full."));
        lines.add(LangPack.say(player, "vex.combat_kit", "Before the waste: a combat skill in your loadout, damage boosters on the blade."));
        lines.add(LangPack.say(player, "vex.combat_stamp", "Now §dMiss Ledger§f's stamp. §e/capital§f — you've got the teleport."));
        return lines.toArray(new String[0]);
    }

    /** Drill passed: the golem goes, so it doesn't swing at whoever walks past the gate next. */
    public static void passed(Player player) {
        try {
            de.aetherion.items.combat.system.CombatSystem.clearGolem(player);
        } catch (LinkageError ignored) {
        }
    }

    /* =========================================================
     * HELPERS
     * ========================================================= */

    private static String noRoomLine(Player player) {
        return LangPack.say(player, "vex.drill_no_room",
                "No room for my golem here. Open ground and talk to me — or past the gate, any hostile counts.");
    }

    private static boolean armGolem(Player player) {
        try {
            return de.aetherion.items.combat.system.CombatSystem.drillGolem(player);
        } catch (LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasGolem(Player player) {
        try {
            return de.aetherion.items.combat.system.CombatSystem.hasGolem(player, GOLEM_RANGE);
        } catch (LinkageError ignored) {
            return false;
        }
    }

    private static void vex(Player player, String line) {
        QuestNPC npc = QuestNPCRegistry.getNPC(NPC_ID);
        if (npc != null) {
            LivingNpcProfile.say(player, npc, line);
        } else {
            LivingNpcProfile.say(player, NPC_ID, "Sergeant Vex", line);
        }
    }

    private static QuestManager questManager() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getQuestManager();
    }
}
