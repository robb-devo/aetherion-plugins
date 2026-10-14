package de.aetherion.quests.tutorial;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.bridge.QuestProgressBridge;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.util.QuestSkillGate;
import de.aetherion.quests.util.QuestStoryGate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The orientation beats the game can't report by itself:
 * <ul>
 *   <li>Codex opened → {@value #CODEX} for Miss Ledger's loadout lesson (Items has no hook for it).</li>
 *   <li>Kit on + blade up → {@link CombatTeach#READY} for Vex's combat beat (checked once a second, only
 *       while that quest runs).</li>
 *   <li>Aetherion Level 4 → 5 while online: Ledger names the auto-pickup unlock once, so the Items title
 *       isn't the only clue.</li>
 *   <li>Act cards for Act II and III, once per player.</li>
 * </ul>
 */
public final class OrientationWatch implements Listener {

    /** USE target: any Codex page opened (Collection, Bestiary, hub, …). */
    public static final String CODEX = "AETHER_CODEX";
    private static final String SKILL = "AETHER_SKILL";
    private static final String LOADOUT_QUEST = "lesson_manager";
    private static final String AUTOPICKUP_TOLD = "autopickup_told";

    private static OrientationWatch instance;

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Map<UUID, Integer> lastLevel = new ConcurrentHashMap<>();

    private OrientationWatch(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public static void start(AetherionQuests plugin, QuestManager questManager) {
        if (instance != null || plugin == null || questManager == null) {
            return;
        }
        instance = new OrientationWatch(plugin, questManager);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, instance::tick, 40L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        try {
            if (event.getInventory().getHolder(false) instanceof de.aetherion.items.codex.CodexView) {
                QuestProgressBridge.noteUsed(player, CODEX);
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLevel.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        Quest combat = questManager.getQuest(CombatTeach.QUEST_ID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (combat != null && questManager.getQuestState(player, combat) == QuestState.ACTIVE
                    && CombatTeach.ready(player)) {
                QuestProgressBridge.noteUsed(player, CombatTeach.READY);
            }
            watchLevel(player);
        }
    }

    /** One line from Miss Ledger the first time a player crosses into auto pickup while online. */
    private void watchLevel(Player player) {
        int level = QuestSkillGate.accountLevel(player);
        if (level < 0) {
            return;
        }
        Integer before = lastLevel.put(player.getUniqueId(), level);
        if (before == null || before >= QuestStoryGate.AUTO_PICKUP_LEVEL || level < QuestStoryGate.AUTO_PICKUP_LEVEL) {
            return;
        }
        if (plugin.getPlayerQuestStorage() == null
                || plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), AUTOPICKUP_TOLD)) {
            return;
        }
        plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), AUTOPICKUP_TOLD);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                ledger(player, LangPack.say(player, "ledger.autopickup_on",
                        "That chime? Aetherion Level §e5§f, {player}. Drops fly to you now — auto pickup."));
            }
        }, 50L);
    }

    /**
     * Miss Ledger reacts while the player is still inside the Manager: one line per half of her lesson,
     * and only while the other half is open (the "objectives complete" banner covers the end).
     */
    public static void noteLesson(Player player, String questId, String target) {
        OrientationWatch watch = instance;
        if (watch == null || player == null || !LOADOUT_QUEST.equalsIgnoreCase(questId) || target == null) {
            return;
        }
        Quest quest = watch.questManager.getQuest(LOADOUT_QUEST);
        if (quest == null || watch.questManager.getQuestState(player, quest) != QuestState.ACTIVE) {
            return;
        }
        boolean skill = watch.questManager.getProgress(player, LOADOUT_QUEST, SKILL) >= 1;
        boolean codex = watch.questManager.getProgress(player, LOADOUT_QUEST, CODEX) >= 1;
        if (SKILL.equalsIgnoreCase(target) && skill && !codex) {
            watch.later(player, 20L, () -> {
                ledger(player, LangPack.say(player, "ledger.loadout_set",
                        "There — that's a loadout. That skill levels as you work its trade."));
                ledger(player, LangPack.say(player, "ledger.codex_next",
                        "Now your §eCodex§f — top-left of that same page, or §e/codex§f."));
            });
        } else if (CODEX.equalsIgnoreCase(target) && codex && !skill) {
            watch.later(player, 20L, () -> {
                ledger(player, LangPack.say(player, "ledger.codex_seen",
                        "Your ledger. Every tree, ore and kill lands in there — tiers pay coins."));
                ledger(player, LangPack.say(player, "ledger.skill_next",
                        "Now §dSkills§f — the blinking tab. Equip one."));
            });
        }
    }

    /**
     * Title card when an act opens (Act II at Miss Ledger, Act III at Sergeant Vex). Once per player;
     * Act I is the harbour arrival card.
     */
    public static void actCard(Player player, int act) {
        OrientationWatch watch = instance;
        if (watch == null || player == null || act < 2 || act > 3 || watch.plugin.getPlayerQuestStorage() == null) {
            return;
        }
        String flag = "act_card_" + act;
        if (watch.plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), flag)) {
            return;
        }
        watch.plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), flag);
        String title = act == 2
                ? LangPack.ui(player, "act2_title", "ACT II · THE CAPITAL")
                : LangPack.ui(player, "act3_title", "ACT III · THE ROADS");
        String subtitle = act == 2
                ? LangPack.ui(player, "act2_subtitle", "your loadout · your Codex · boosters")
                : LangPack.ui(player, "act3_subtitle", "steel · teleports · the stamp");
        player.showTitle(Title.title(
                Component.text(title, act == 2 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(subtitle, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(1900), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.5f, 0.7f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    private void later(Player player, long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, ticks);
    }

    private static void ledger(Player player, String line) {
        LivingNpcProfile.say(player, "ledger", "Miss Ledger", line);
    }
}
