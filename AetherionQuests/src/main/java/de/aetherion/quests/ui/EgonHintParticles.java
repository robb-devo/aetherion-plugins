package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Soft green aura only around Egon for players who have not yet
 * accepted Welcome Aboard. Per-player particles; disappears on accept.
 */
public final class EgonHintParticles {

    private static final String NPC_ID = "egon";
    private static final String QUEST_ID = "welcome_aboard";
    private static final double VIEW_DISTANCE = 22.0;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE * VIEW_DISTANCE;

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Particle.DustOptions dust =
            new Particle.DustOptions(Color.fromRGB(72, 210, 96), 1.05f);

    private BukkitTask task;
    private int phase;

    public EgonHintParticles(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        QuestNPC egon = QuestNPCRegistry.getNPC(NPC_ID);
        if (egon == null) {
            return;
        }

        Location base = resolveLocation(egon);
        if (base == null || base.getWorld() == null) {
            return;
        }
        base = base.clone().add(0.0, 0.35, 0.0);

        Quest quest = questManager.getQuest(QUEST_ID);
        if (quest == null) {
            return;
        }

        phase = (phase + 1) % 16;

        double angle = phase * (Math.PI / 8.0);
        double radius = 0.85;
        Location ringA = base.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
        Location ringB = base.clone().add(
                Math.cos(angle + Math.PI) * radius,
                0.55,
                Math.sin(angle + Math.PI) * radius
        );
        Location soft = base.clone().add(0.0, 1.05, 0.0);
        World world = base.getWorld();

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(base) > VIEW_DISTANCE_SQUARED) {
                continue;
            }
            if (questManager.getQuestState(player, quest) != QuestState.AVAILABLE) {
                continue;
            }

            player.spawnParticle(Particle.DUST, ringA, 1, 0.0, 0.0, 0.0, 0.0, dust);
            player.spawnParticle(Particle.DUST, ringB, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (phase % 2 == 0) {
                player.spawnParticle(Particle.HAPPY_VILLAGER, soft, 1, 0.12, 0.18, 0.12, 0.0);
            }
        }
    }

    private Location resolveLocation(QuestNPC egon) {
        LivingNpcService living = plugin.getLivingNpcService();
        if (LivingNpcService.isLiving(NPC_ID) && living != null) {
            Location livingLoc = living.locationOf(NPC_ID);
            if (livingLoc != null) {
                return livingLoc;
            }
            if (plugin.getNpcDataStorage() != null) {
                Location saved = plugin.getNpcDataStorage().getSavedLocation(NPC_ID);
                if (saved != null) {
                    return saved;
                }
            }
        }
        if (egon.getEntityId() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(egon.getEntityId());
        if (entity == null || !entity.isValid()) {
            return null;
        }
        return entity.getLocation();
    }
}
