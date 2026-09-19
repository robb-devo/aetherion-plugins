package de.aetherion.quests.ui;


import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.BossEngineAPI;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;


public class QuestCompass {


    private static final String[] ARROWS = {
            "↑", "↗", "→", "↘", "↓", "↙", "←", "↖"
    };

    private static final double ARRIVED_DISTANCE = 4.0;

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private BukkitTask task;


    public QuestCompass(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }


    public void start() {
        if (task != null) {
            task.cancel();
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }


    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }


    public String arrow(Player player) {
        CompassData data = data(player);
        return data == null ? "" : data.arrow();
    }


    public String distance(Player player) {
        CompassData data = data(player);
        return data == null ? "" : data.distanceText();
    }


    public String line(Player player) {
        CompassData data = data(player);
        if (data == null) {
            return "";
        }
        if (data.arrived()) {
            return "§a" + data.arrow();
        }
        if (data.otherWorld()) {
            return "§7" + data.arrow();
        }
        return "§e" + data.arrow() + " §f" + data.distanceText();
    }


    public String targetName(Player player) {
        Quest quest = tracked(player);
        if (quest == null) {
            return "";
        }

        QuestState state = questManager.getQuestState(player, quest);
        if (state == QuestState.ACTIVE) {
            String bossId = incompleteBossId(player, quest);
            if (bossId != null) {
                BossEngineAPI api = bosses();
                if (api != null) {
                    String name = api.displayName(bossId);
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }
                return bossId;
            }
            if (needsMerchantChest(player, quest)) {
                return "Sample Chest";
            }
            if ("forge_coal".equalsIgnoreCase(quest.getId())) {
                return "Ore Ridge";
            }
            if ("dock_pass".equalsIgnoreCase(quest.getId())) {
                return "Tackle";
            }
            if ("first_shift".equalsIgnoreCase(quest.getId())) {
                return "Shaft Foreman · Mine";
            }
            if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
                int catchProgress = questManager.getProgress(player, quest.getId(), "ANY");
                if (catchProgress >= 1) {
                    return "Lark";
                }
            }
            if ("border_rites".equalsIgnoreCase(quest.getId())
                    && QuestProgressDisplay.hasBorderlandsSpiritVial(player)) {
                return "Altar";
            }
            return "";
        }

        if (state == QuestState.READY) {
            if (quest.hasTurnInNpc()) {
                QuestNPC turnIn = QuestNPCRegistry.getNPC(quest.getTurnInNpcId());
                if (turnIn != null && turnIn.getName() != null && !turnIn.getName().isBlank()) {
                    return turnIn.getName();
                }
            }
            QuestNPC npc = QuestNPCRegistry.findForQuest(quest);
            if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
                return npc.getName();
            }
        }

        return quest.getTitle();
    }


    public String questTitle(Player player) {
        Quest quest = tracked(player);
        return quest == null ? "" : quest.getTitle();
    }


    public Location targetLocation(Player player) {
        if (player == null) {
            return null;
        }

        Quest quest = tracked(player);
        if (quest == null) {
            return null;
        }

        QuestState state = questManager.getQuestState(player, quest);

        // Turn-in: point at turn-in NPC when set, else quest giver.
        if (state == QuestState.READY) {
            if (quest.hasTurnInNpc()) {
                Location turnIn = npcLocation(QuestNPCRegistry.getNPC(quest.getTurnInNpcId()));
                if (turnIn != null) {
                    return turnIn;
                }
            }
            return npcLocation(QuestNPCRegistry.findForQuest(quest));
        }

        if (state != QuestState.ACTIVE) {
            return null;
        }

        // TALK objectives → point at the target NPC.
        Location talkTarget = talkTargetLocation(quest);
        if (talkTarget != null) {
            return talkTarget;
        }

        // Explicit quest waypoint (optional).
        if (quest.hasWaypoint()) {
            org.bukkit.World world = Bukkit.getWorld(quest.getTargetWorld());
            if (world != null) {
                return new Location(world, quest.getTargetX(), quest.getTargetY(), quest.getTargetZ());
            }
        }

        // Boss hunts → live boss position.
        Location hunt = huntLocation(player, quest);
        if (hunt != null) {
            return hunt;
        }

        // Merchant sample chest → actual chest block.
        Location chest = merchantChestLocation(player, quest);
        if (chest != null) {
            return chest;
        }

        // Activity quests (fish, forage, mine, craft, cows, …) have no pin —
        // except coal run: point at Ore Ridge once the hub marker is planted.
        if ("forge_coal".equalsIgnoreCase(quest.getId())) {
            Location ridge = hubSpawnLocation("ore_ridge");
            if (ridge != null) {
                return ridge;
            }
        }

        // Dock Pass: get the slip from Tackle first (READY already pins Fishmonger).
        if ("dock_pass".equalsIgnoreCase(quest.getId())) {
            Location tackle = npcLocation(QuestNPCRegistry.getNPC("fisher"));
            if (tackle != null) {
                return tackle;
            }
        }

        // First Shift: pin Shaft Foreman / mines mouth while gathering ore.
        if ("first_shift".equalsIgnoreCase(quest.getId())) {
            Location foreman = npcLocation(QuestNPCRegistry.getNPC("foreman"));
            if (foreman != null) {
                return foreman;
            }
            Location mines = hubSpawnLocation("mines");
            if (mines != null) {
                return mines;
            }
        }

        // Pocket Zoo: after the catch (and while equipping), pin Lark so the bar arrow finds him.
        if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
            int catchProgress = questManager.getProgress(player, quest.getId(), "ANY");
            if (catchProgress >= 1) {
                Location lark = npcLocation(QuestNPCRegistry.getNPC("lark"));
                if (lark != null) {
                    return lark;
                }
            }
        }

        // Border Rites: only after the spirit vial — pin the powder altar (boss spawn).
        if ("border_rites".equalsIgnoreCase(quest.getId())
                && QuestProgressDisplay.hasBorderlandsSpiritVial(player)) {
            Location altar = borderlandsAltarLocation();
            if (altar != null) {
                return altar;
            }
        }
        return null;
    }

    private static Location borderlandsAltarLocation() {
        try {
            org.bukkit.World world = Bukkit.getWorld(
                    de.aetherion.items.world.BorderlandsRiteService.ALTAR_WORLD);
            if (world == null) {
                return null;
            }
            return new Location(
                    world,
                    de.aetherion.items.world.BorderlandsRiteService.ALTAR_X,
                    de.aetherion.items.world.BorderlandsRiteService.ALTAR_Y,
                    de.aetherion.items.world.BorderlandsRiteService.ALTAR_Z
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Location hubSpawnLocation(String id) {
        try {
            Class<?> api = Class.forName("de.aetherion.hub.api.AetherionHubAPI");
            Object at = api.getMethod("location", String.class).invoke(null, id);
            return at instanceof Location location ? location : null;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return null;
        }
    }


    private Location talkTargetLocation(Quest quest) {
        if (quest == null) {
            return null;
        }
        for (Objective objective : quest.getObjectives()) {
            if (objective == null || objective.getType() != ObjectiveType.TALK) {
                continue;
            }
            String target = objective.getTarget();
            if (target == null || target.isBlank()) {
                continue;
            }
            Location at = npcLocation(QuestNPCRegistry.getNPC(target));
            if (at != null) {
                return at;
            }
        }
        return null;
    }

    private Quest tracked(Player player) {
        Quest quest = questManager.getTrackedQuest(player);
        if (quest != null) {
            return quest;
        }
        return questManager.findActiveOrReadyQuest(player);
    }


    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location target = targetLocation(player);

            if (target != null) {
                player.setCompassTarget(target);
            }
            // Bossbar is independent of a map pin (collect/kill quests have no target).
            QuestProgressDisplay.showProgress(player, questManager);
        }

        QuestHint.tickAll(questManager);
    }


    private CompassData data(Player player) {
        Location target = targetLocation(player);
        if (player == null || target == null || player.getLocation().getWorld() == null) {
            return null;
        }

        Location from = player.getLocation();

        if (target.getWorld() == null || !from.getWorld().equals(target.getWorld())) {
            return new CompassData("◆", "", true, false);
        }

        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= ARRIVED_DISTANCE) {
            return new CompassData("●", "0m", false, true);
        }

        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = targetYaw - from.getYaw();

        while (diff < -180.0) {
            diff += 360.0;
        }
        while (diff > 180.0) {
            diff -= 360.0;
        }

        int index = (int) Math.round(diff / 45.0);
        if (index < 0) {
            index += 8;
        }
        if (index >= 8) {
            index = 0;
        }

        return new CompassData(ARROWS[index], formatDistance(distance), false, false);
    }


    private Location huntLocation(Player player, Quest quest) {
        String bossId = incompleteBossId(player, quest);
        if (bossId == null) {
            return null;
        }
        BossEngineAPI api = bosses();
        if (api == null) {
            return null;
        }
        return api.locate(bossId, player.getLocation());
    }


    private String incompleteBossId(Player player, Quest quest) {
        BossEngineAPI api = bosses();
        if (api == null || quest == null) {
            return null;
        }
        for (Objective objective : quest.getObjectives()) {
            if (objective == null || objective.getType() != ObjectiveType.KILL) {
                continue;
            }
            String target = objective.getTarget();
            if (target == null || !api.isKnownBoss(target)) {
                continue;
            }
            int required = Math.max(1, objective.getAmount());
            int progress = questManager.getProgress(player, quest.getId(), target);
            if (progress < required) {
                return target;
            }
        }
        return null;
    }


    private Location merchantChestLocation(Player player, Quest quest) {
        if (!needsMerchantChest(player, quest)) {
            return null;
        }
        de.aetherion.quests.chest.MerchantChestService chests = plugin.getMerchantChests();
        if (chests == null) {
            return null;
        }
        return chests.nearestLocation(player.getLocation());
    }

    private boolean needsMerchantChest(Player player, Quest quest) {
        if (player == null || quest == null) {
            return false;
        }
        for (Objective objective : quest.getObjectives()) {
            if (objective == null || objective.getType() != ObjectiveType.USE) {
                continue;
            }
            String target = objective.getTarget();
            if (target == null || !target.equalsIgnoreCase("MERCHANT_CHEST")) {
                continue;
            }
            int required = Math.max(1, objective.getAmount());
            int progress = questManager.getProgress(player, quest.getId(), target);
            if (progress < required) {
                return true;
            }
        }
        return false;
    }


    private static BossEngineAPI bosses() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        return BossEngine.getAPI();
    }


    private Location npcLocation(QuestNPC npc) {
        if (npc == null) {
            return null;
        }

        if (npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }

        NPCDataStorage storage = plugin.getNpcDataStorage();
        if (storage == null) {
            return null;
        }

        return storage.getSavedLocation(npc.getId());
    }


    private static String formatDistance(double distance) {
        if (distance >= 1000) {
            return String.format(Locale.US, "%.1fkm", distance / 1000.0);
        }
        return Math.round(distance) + "m";
    }


    private record CompassData(
            String arrow,
            String distanceText,
            boolean otherWorld,
            boolean arrived
    ) {
    }

}
