package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.QuestNpcAppearance;
import de.aetherion.quests.service.QuestNPCSpawnService;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.Set;

public final class NpcGuardListener implements Listener {

    private final QuestNPCSpawnService spawnService = new QuestNPCSpawnService();

    public NpcGuardListener(AetherionQuests plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 100L, 100L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Set<String> ids = new HashSet<>();
        for (Entity entity : event.getChunk().getEntities()) {
            String npcId = QuestNpcAppearance.npcId(entity);
            if (npcId != null && !npcId.isBlank()) {
                ids.add(npcId.toLowerCase());
            }
            if (entity instanceof Villager villager && QuestNpcAppearance.npcId(villager) != null) {
                QuestNpcAppearance.silenceLiving(villager);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (String npcId : ids) {
                spawnService.ensureUnique(npcId, null);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (QuestNpcAppearance.npcId(event.getEntity()) != null) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        String npcId = QuestNpcAppearance.npcId(event.getEntity());
        if (npcId == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> spawnService.ensureUnique(npcId, null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCareer(VillagerCareerChangeEvent event) {
        if (QuestNpcAppearance.npcId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(VillagerAcquireTradeEvent event) {
        if (QuestNpcAppearance.npcId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (QuestNpcAppearance.npcId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private void sweep() {
        spawnService.ensureAllUnique();
    }
}
