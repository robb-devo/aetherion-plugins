package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sergeant Vex frames the first fight — nothing about the numbers changes.
 * <p>
 * During {@code lesson_steel}: the first hit on a Borderlands hostile gets a word from
 * the gate about the damage numbers you just saw (white = you, gold ✦ = crit); the first
 * kill gets a count. Each fires once per player (persisted with the starter-kit flags).
 * After a death: the old soft nudge toward gear.
 */
public final class VexDeathHintListener implements Listener {

    private static final String QUEST_ID = "lesson_steel";
    private static final long COOLDOWN_MS = 90_000L;

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingHint = new ConcurrentHashMap<>();

    public VexDeathHintListener(JavaPlugin plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstHit(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event);
        if (attacker == null || event.getFinalDamage() <= 0.05d || !isOnLessonSteel(attacker)) {
            return;
        }
        if (!QuestObjectiveListener.isBorderlandsHostile(event.getEntity())) {
            return;
        }
        if (!claimOnce(attacker, "steel_primer_hit")) {
            return;
        }
        // A beat after the number has floated up, so the eye is already on it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!attacker.isOnline()) {
                return;
            }
            String[] lines = LangPack.dialogs(attacker, "vex_primer_hit", new String[] {
                    "§7(from the gate)§f See the number that jumped off it? That's you. Not a guess.",
                    "Gold §6✦§f ones are crits — watch which swings make them. White is honest work."
            });
            for (String line : lines) {
                LivingNpcProfile.say(attacker, "vex", "Sergeant Vex", line);
            }
        }, 14L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !isOnLessonSteel(killer) || !QuestObjectiveListener.isBorderlandsHostile(dead)) {
            return;
        }
        if (!claimOnce(killer, "steel_primer_kill")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!killer.isOnline()) {
                return;
            }
            LivingNpcProfile.say(killer, "vex", "Sergeant Vex", LangPack.ui(killer, "vex_primer_kill",
                    "One. The count's on your bar up top. Nine more and we talk."));
        }, 12L);
    }

    private static boolean claimOnce(Player player, String key) {
        AetherionQuests quests = AetherionQuests.getInstance();
        if (quests == null || quests.getPlayerQuestStorage() == null) {
            return false;
        }
        if (quests.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), key)) {
            return false;
        }
        quests.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), key);
        return true;
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isOnLessonSteel(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHint.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        // Mark once on death; whisper only after respawn (avoids double dialog).
        pendingHint.put(player.getUniqueId(), Boolean.TRUE);
        lastHint.put(player.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(pendingHint.remove(player.getUniqueId()))) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> whisper(player), 20L);
    }

    private boolean isOnLessonSteel(Player player) {
        Quest quest = questManager.getQuest(QUEST_ID);
        if (quest == null || player == null) {
            return false;
        }
        QuestState state = questManager.getQuestState(player, quest);
        return state == QuestState.ACTIVE || state == QuestState.READY;
    }

    private void whisper(Player player) {
        if (player == null || !player.isOnline() || !isOnLessonSteel(player)) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§cSergeant Vex §8» §fYou're vertical again.");
        if (QuestStoryGate.questCompleted(player, questManager, "lesson_boost")) {
            player.sendMessage("§cSergeant Vex §8» §7Trouble? Combat set + boosters. Recipe Book and anvil. Then finish the ten.");
        } else {
            player.sendMessage("§cSergeant Vex §8» §7Trouble? §eTemper§7 teaches boosters. Combat set from the Recipe Book. Soft gear.");
            de.aetherion.quests.ui.QuestHint.show(player, "booster_tutor", "Temper");
        }
        player.sendMessage("");
    }
}
