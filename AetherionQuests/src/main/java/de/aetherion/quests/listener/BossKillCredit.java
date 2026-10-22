package de.aetherion.quests.listener;

import de.aetherion.bossengine.event.BossDeathEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Boss hunts complete from BossEngine's death event: some bosses (Pathwarden among them) end without a vanilla death
 * the quest listener could see, so Closed Road never finished. Everyone who hit the boss, its killer and players
 * within 128 blocks get the kill, once (see {@link QuestObjectiveListener#creditBossKill}).
 * <p>
 * Registered only when BossEngine is loaded (soft dependency).
 */
public final class BossKillCredit implements Listener {

    private final QuestObjectiveListener objectives;

    public BossKillCredit(QuestObjectiveListener objectives) {
        this.objectives = objectives;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(BossDeathEvent event) {
        if (event.getInstance() == null || event.getInstance().getTemplate() == null) {
            return;
        }
        String bossId = event.getInstance().getTemplate().getId();
        if (bossId == null) {
            return;
        }
        Set<Player> credited = new LinkedHashSet<>();
        if (event.getKiller() != null) {
            credited.add(event.getKiller());
        }
        for (UUID id : event.getDamageMap().keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                credited.add(player);
            }
        }
        LivingEntity body = event.getInstance().getEntity();
        Location at = body != null ? body.getLocation() : event.getInstance().getSpawnLocation();
        if (at != null && at.getWorld() != null) {
            for (Player nearby : at.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(at) <= 128 * 128) {
                    credited.add(nearby);
                }
            }
        }
        for (Player player : credited) {
            objectives.creditBossKill(player, bossId);
        }
    }
}
