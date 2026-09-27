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
 * <p>
 * Attractor: a thin green "lantern smoke" column over his head reads from the far
 * end of the pier; walk up close and he tells you how to talk to him — once.
 */
public final class EgonHintParticles {

    private static final String NPC_ID = "egon";
    private static final String QUEST_ID = "welcome_aboard";
    private static final double VIEW_DISTANCE = 22.0;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE * VIEW_DISTANCE;
    /** Column over his head — client culls plain particles past ~32 blocks anyway. */
    private static final double BEACON_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final double GREET_DISTANCE_SQUARED = 5.5 * 5.5;
    private static final long GREET_COOLDOWN_MS = 5L * 60L * 1000L;

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Particle.DustOptions dust =
            new Particle.DustOptions(Color.fromRGB(72, 210, 96), 1.05f);
    private final Particle.DustOptions beacon =
            new Particle.DustOptions(Color.fromRGB(120, 225, 130), 0.85f);
    private final java.util.Map<java.util.UUID, Long> greeted = new java.util.concurrent.ConcurrentHashMap<>();

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

        // Rising column: one mote per tick, climbing 2.3 → 4.4 over eight ticks.
        Location column = base.clone().add(0.0, 1.95 + (phase % 8) * 0.3, 0.0);

        for (Player player : world.getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(base);
            if (distanceSquared > BEACON_DISTANCE_SQUARED) {
                continue;
            }
            if (questManager.getQuestState(player, quest) != QuestState.AVAILABLE) {
                continue;
            }

            player.spawnParticle(Particle.DUST, column, 1, 0.03, 0.0, 0.03, 0.0, beacon);
            if (distanceSquared <= GREET_DISTANCE_SQUARED) {
                maybeGreet(player, egon);
            }
            if (distanceSquared > VIEW_DISTANCE_SQUARED) {
                continue;
            }

            player.spawnParticle(Particle.DUST, ringA, 1, 0.0, 0.0, 0.0, 0.0, dust);
            player.spawnParticle(Particle.DUST, ringB, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (phase % 2 == 0) {
                player.spawnParticle(Particle.HAPPY_VILLAGER, soft, 1, 0.12, 0.18, 0.12, 0.0);
            }
        }
    }

    /** Close enough to click but hasn't: Egon says how, once every few minutes. */
    private void maybeGreet(Player player, QuestNPC egon) {
        if (!de.aetherion.quests.lang.PlayerLang.hasChosen(player)
                || HarbourArrival.isPlaying(player)
                || de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)) {
            return;
        }
        if (plugin.getDialogManager() != null && plugin.getDialogManager().isSpeaking(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = greeted.get(player.getUniqueId());
        if (last != null && now - last < GREET_COOLDOWN_MS) {
            return;
        }
        greeted.put(player.getUniqueId(), now);
        String name = egon.getName() == null || egon.getName().isBlank() ? "Egon" : egon.getName();
        for (String line : de.aetherion.quests.lang.LangPack.dialogs(player, "egon_greet_near", new String[] {
                "That's me. Right-click — I don't bite, I kit."
        })) {
            de.aetherion.quests.npc.LivingNpcProfile.say(player, NPC_ID, name, line);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.quests.lang.LangPack.ui(player, "egon_greet_action", "→ Right-click Egon"),
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
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
