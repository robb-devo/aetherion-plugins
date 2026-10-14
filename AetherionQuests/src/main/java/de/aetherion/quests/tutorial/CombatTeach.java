package de.aetherion.quests.tutorial;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Orientation Act III, beat 6 — "How You Fight Here". <b>Reserved for the combat elevate (Saturday).</b>
 * <p>
 * Today it is a stub: Sergeant Vex says the drill is being rebuilt and only checks the kit is on
 * (two armour pieces and a weapon in hand). Everything the new combat needs is already wired — quest id,
 * NPC, spot, objective hook, lines, the spine gate — so Saturday fills {@code TODO(combat-sat)} here and in
 * nothing else of the spine.
 * <ul>
 *   <li>Quest {@value #QUEST_ID}, registered by {@link #register}; offered by {@value #NPC_ID} ahead of his
 *       Borderlands lesson ({@code QuestStoryGate.spineQuestFor}).</li>
 *   <li>Spot: Vex at the Borderlands gate (live npcs.yml, world 161.5 58 73.5). Walking in unlocks the
 *       Borderlands teleport, which the spine uses as its walk-in lesson.</li>
 *   <li>Objective {@value #READY}: noted by {@link OrientationWatch} once {@link #ready} holds.</li>
 * </ul>
 */
public final class CombatTeach {

    public static final String QUEST_ID = "lesson_combat";
    public static final String NPC_ID = "vex";
    /** Kit on, blade up. Today's only objective. */
    public static final String READY = "COMBAT_READY";
    /** Config switch for the real drill (default off = stub lines). */
    public static final String LIVE_PATH = "onboarding.combat-teach.live";

    // TODO(combat-sat): the drill's objectives. The Items combat system reports each move through
    // QuestProgressHook.noteUsed(player, "<TARGET>") and register() adds them below READY, e.g.
    //   COMBAT_DODGE  — dash/roll through a telegraphed hit
    //   COMBAT_GUARD  — one clean guard / parry window
    //   COMBAT_HEAVY  — one charged / heavy hit that lands
    // Add a display name per target in model.Objective#getDisplayName. Nothing else in the spine changes.

    private CombatTeach() {
    }

    /** Registers the spine quest. Called from {@code QuestRegistry.registerAll}. */
    public static void register(QuestManager questManager) {
        Quest quest = new Quest(
                QUEST_ID,
                "How You Fight Here",
                "Sergeant Vex wants you kitted: armour on, blade in hand. The full drill lands with the new combat."
        );
        quest.setServiceId(NPC_ID);
        quest.setTurnInNpcId(NPC_ID);
        quest.addObjective(new Objective(ObjectiveType.USE, READY, 1));
        // TODO(combat-sat): when live(), add the drill objectives here (see the targets above).
        quest.addReward(new Reward("Coins", 60));
        quest.addReward(new Reward("XP", 60));
        questManager.registerQuest(quest);
    }

    /**
     * True once the new combat ships and the drill should run instead of the stub.
     * TODO(combat-sat): also read the Items combat kill switch ({@code combat.system.enabled}) so a disabled
     * combat system falls back to the stub on its own.
     */
    public static boolean live() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin != null && plugin.getConfig().getBoolean(LIVE_PATH, false);
    }

    /** Today's pass: two armour pieces worn and a weapon in the main hand. */
    public static boolean ready(Player player) {
        // TODO(combat-sat): the live drill completes on its own objectives; keep this as the entry check.
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

    /** Vex's offer lines for this beat (dialog id {@code vex_combat_intro}). */
    public static String[] introLines(Player player) {
        // TODO(combat-sat): when live(), the drill's intro — what the new combat asks of a rookie, in Vex's voice.
        return LangPack.dialogs(player, "vex_combat_intro", new String[] {
                "Sergeant Vex. Fighting here isn't hold-click-and-pray.",
                "The drill's being rebuilt — footwork, timing, the lot. When it lands, I put you through it.",
                "Today: armour on — all of Egon's kit — and a blade in your hand. Then look at me."
        });
    }

    /** Short nudge while the beat is running and the kit isn't on yet. */
    public static String[] notReadyLines(Player player) {
        boolean armour = player != null && armourPieces(player) >= 2;
        return armour
                ? new String[] {LangPack.say(player, "vex.combat_blade", "Armour: fine. Hands: empty. §eBlade§f in your main hand, {player}.")}
                : new String[] {LangPack.say(player, "vex.combat_armour", "Armour in the armour slots, {player}. All of Egon's kit. Then the blade.")};
    }

    /** Vex's lines once the pass is in (turn-in brief). */
    public static String[] passLines(Player player) {
        // TODO(combat-sat): the drill's debrief (what they just did right) replaces the first line when live().
        return new String[] {
                LangPack.say(player, "vex.combat_pass", "Adequate. That's a pass — for today."),
                LangPack.say(player, "vex.combat_kit", "Before the waste: a combat skill in your loadout, damage boosters on the blade."),
                LangPack.say(player, "vex.combat_stamp", "Now §dMiss Ledger§f's stamp. §e/capital§f — you've got the teleport.")
        };
    }
}
