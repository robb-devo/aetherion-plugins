package de.aetherion.bossengine.manager;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.event.BossDeathEvent;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.event.BossSpawnEvent;
import de.aetherion.bossengine.hud.BossBarHud;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossState;
import de.aetherion.bossengine.integration.worldguard.WorldGuardSpawnGuard;
import de.aetherion.bossengine.instance.saint.HangingSaintDirector;
import de.aetherion.bossengine.loot.HollowReliquary;
import de.aetherion.bossengine.loot.LootService;
import de.aetherion.bossengine.loot.SeraphineMusicBox;
import de.aetherion.bossengine.loot.WorldEaterBonusChest;
import de.aetherion.bossengine.model.BossTemplate;
import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.SpawnCondition;
import de.aetherion.bossengine.skill.SkillTrigger;
import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BossManager {

    /** Only the Hollow Sun pays through a Hollow Reliquary; every other boss keeps the direct payout. */
    private static final String RELIQUARY_BOSS = "hollow_sun";
    /** Quiet beat after Hollow Sun supernova before the ember lands. */
    private static final long RELIQUARY_DELAY_TICKS = 30L;
    /** Quiet beat after Seraphine's death before the music box lowers. */
    private static final int MUSIC_BOX_DELAY_TICKS = 40;
    /** The World Eater pays through the Bonus Chest that generates once the world is saved. */
    private static final String BONUS_CHEST_BOSS = de.aetherion.bossengine.instance.worldeater.WorldEaterSite.EATER_ID;
    /** Beat after "Saved the game" before the Bonus Chest starts generating. */
    private static final int BONUS_CHEST_DELAY_TICKS = 20;

    private final JavaPlugin plugin;
    private final TemplateManager templates;
    private final SkillManager skills;
    private final BossKeys keys;
    private final LootService lootService;
    private final Map<UUID, BossInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Integer> chunkTickets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> bodyChunkAnchor = new ConcurrentHashMap<>();
    private final Map<String, Long> spawnAnnounceAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBodySpawnAt = new ConcurrentHashMap<>();
    private final Map<String, Object> spawnLocks = new ConcurrentHashMap<>();
    private final BossBarHud bossBars = new BossBarHud();
    private BukkitTask ticker;
    private int uniquenessPulse;

    public BossManager(
            JavaPlugin plugin,
            TemplateManager templates,
            SkillManager skills,
            BossKeys keys,
            LootService lootService
    ) {
        this.plugin = plugin;
        this.templates = templates;
        this.skills = skills;
        this.keys = keys;
        this.lootService = lootService;
    }

    public void start() {
        int interval = Math.max(1, plugin.getConfig().getInt("tick-interval-ticks", 1));
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        bossBars.hideAll();
        new ArrayList<>(instances.values()).forEach(instance ->
                despawn(instance, BossDespawnEvent.Reason.PLUGIN_DISABLE, null)
        );
    }

    public Optional<BossInstance> spawn(
            String templateId,
            Location location,
            SpawnCause cause,
            Player initiator
    ) {
        return spawn(templateId, location, cause, initiator, null, null);
    }

    public Optional<BossInstance> spawn(
            String templateId,
            Location location,
            SpawnCause cause,
            Player initiator,
            String spawnerId,
            SpawnCondition override
    ) {
        Optional<BossTemplate> templateOpt = templates.get(templateId);
        if (templateOpt.isEmpty() || location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        BossTemplate template = templateOpt.get();
        SpawnCondition conditions = override == null ? template.getConditions() : override;

        // Serialize per-template so stationary + core/command can't race past max-instances.
        synchronized (spawnLock(template.getId())) {
            cullExtras(template.getId(), conditions.getMaxInstances());
            if (countActive(template.getId()) >= conditions.getMaxInstances()
                    || countLivingTagged(template.getId()) >= conditions.getMaxInstances()) {
                return Optional.empty();
            }
            if (spawnerId != null && !spawnerId.isBlank() && isSpawnerOccupied(spawnerId)) {
                return Optional.empty();
            }

            BossInstance instance = new BossInstance(
                    plugin,
                    keys,
                    template,
                    location,
                    cause == null ? SpawnCause.UNKNOWN : cause,
                    spawnerId,
                    conditions
            );
            applyWorldRaidScale(instance, location);

            BossSpawnEvent spawnEvent = new BossSpawnEvent(instance, location, instance.getSpawnCause(), initiator);
            Bukkit.getPluginManager().callEvent(spawnEvent);
            if (spawnEvent.isCancelled()) {
                return Optional.empty();
            }

            LivingEntity entity = spawnEntity(template, location, instance);
            if (entity == null || !entity.isValid()) {
                plugin.getLogger().warning(
                        "Boss spawn for '" + template.getId()
                                + "' was cancelled or removed immediately. "
                                + "Check DeluxeHub (disable-mobs / spawn world), MythicMobs, or WorldGuard deny-spawn."
                );
                return Optional.empty();
            }

            instance.bindEntity(entity);
            instances.put(instance.getInstanceId(), instance);
            instances.put(entity.getUniqueId(), instance);
            retainChunks(location);
            followBodyChunks(instance);
            bossBars.refresh(instance);

            plugin.getLogger().info(
                    "Spawned boss '" + template.getId()
                            + "' combatHP=" + (int) instance.getCombatHealth()
                            + "/" + (int) instance.getCombatMaxHealth()
                            + " vanillaHP=" + String.format(java.util.Locale.US, "%.1f", entity.getHealth())
                            + "/" + String.format(java.util.Locale.US, "%.1f", entity.getMaxHealth())
                            + " leash=" + conditions.getLeashRadius()
                            + " spawner=" + (spawnerId == null ? "-" : spawnerId)
            );
            plugin.getServer().getScheduler().runTaskLater(plugin, instance::reapplyCombatStats, 1L);
            plugin.getServer().getScheduler().runTaskLater(plugin, instance::reapplyCombatStats, 5L);

            skills.execute(instance, SkillTrigger.ON_SPAWN, initiator, 0);

            if (plugin.getConfig().getBoolean("announce-spawn", true)
                    && (location.getWorld() == null || !location.getWorld().getName().startsWith("aedun_"))
                    && tryGlobalSpawnAnnounce(template.getId())) {
                double reach = 48.0;
                double reachSq = reach * reach;
                for (Player player : location.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(location) <= reachSq) {
                        player.sendMessage(TextUtil.component(template.getDisplayName() + " &7has spawned."));
                    }
                }
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!instance.isAlive()) {
                    plugin.getLogger().warning(
                            "Boss '" + template.getId()
                                    + "' vanished 1 tick after spawn. "
                                    + "A hub/mob-cleanup plugin is likely deleting it (DeluxeHub is the usual cause in spawn worlds)."
                    );
                }
            }, 1L);

            return Optional.of(instance);
        }
    }

    public Optional<BossInstance> getByEntity(Entity entity) {
        if (entity instanceof org.bukkit.entity.EnderDragonPart part) {
            entity = part.getParent();
        }
        if (entity == null) {
            return Optional.empty();
        }
        BossInstance direct = instances.get(entity.getUniqueId());
        if (direct != null) {
            return Optional.of(direct);
        }
        return keys.instanceId(entity).map(instances::get);
    }

    public Collection<BossInstance> getActive() {
        return instances.values().stream()
                .filter(BossInstance::isEncounterActive)
                .distinct()
                .toList();
    }

    public Collection<BossInstance> getOccupying() {
        return instances.values().stream()
                .filter(BossInstance::occupiesSpawnSlot)
                .distinct()
                .toList();
    }

    public List<BossInstance> getByTemplate(String templateId) {
        return getOccupying().stream()
                .filter(instance -> instance.getTemplate().getId().equalsIgnoreCase(templateId))
                .toList();
    }

    public long countActive(String templateId) {
        return getByTemplate(templateId).size();
    }

    public long countLivingTagged(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return 0L;
        }
        long count = 0L;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!entity.isValid() || entity.isDead() || keys.isMinion(entity)) {
                    continue;
                }
                if (keys.templateId(entity).filter(id -> id.equalsIgnoreCase(templateId)).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    private Object spawnLock(String templateId) {
        String key = templateId == null ? "" : templateId.toLowerCase(Locale.ROOT);
        return spawnLocks.computeIfAbsent(key, ignored -> new Object());
    }

    /** Drop registered + orphan bodies past max-instances for one template. Keeps the oldest. */
    private void cullExtras(String templateId, int maxInstances) {
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        int max = Math.max(1, maxInstances);
        List<BossInstance> live = new ArrayList<>(getByTemplate(templateId));
        while (live.size() > max) {
            despawn(live.remove(live.size() - 1), BossDespawnEvent.Reason.PLUGIN_DISABLE, null);
        }
        int slotsLeft = max - (int) countActive(templateId);
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!entity.isValid() || entity.isDead() || keys.isMinion(entity)) {
                    continue;
                }
                if (keys.templateId(entity).filter(id -> id.equalsIgnoreCase(templateId)).isEmpty()) {
                    continue;
                }
                if (instances.containsKey(entity.getUniqueId())) {
                    continue;
                }
                if (slotsLeft <= 0) {
                    entity.remove();
                } else {
                    slotsLeft--;
                }
            }
        }
    }

    public int reclaimOrphans(SpawnerManager spawners) {
        int reclaimed = 0;
        int removed = 0;
        Map<String, List<LivingEntity>> byTemplate = new java.util.HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!keys.isBoss(entity) || keys.isMinion(entity) || !entity.isValid() || entity.isDead()) {
                    continue;
                }
                keys.templateId(entity).ifPresent(id ->
                        byTemplate.computeIfAbsent(id.toLowerCase(java.util.Locale.ROOT), ignored -> new ArrayList<>()).add(entity)
                );
            }
        }
        for (Map.Entry<String, List<LivingEntity>> entry : byTemplate.entrySet()) {
            Optional<BossTemplate> templateOpt = templates.get(entry.getKey());
            List<LivingEntity> found = entry.getValue();
            if (templateOpt.isEmpty()) {
                found.forEach(Entity::remove);
                removed += found.size();
                continue;
            }
            BossTemplate template = templateOpt.get();
            int max = Math.max(1, template.getConditions().getMaxInstances());
            List<BossInstance> occupying = new ArrayList<>(getByTemplate(template.getId()));
            List<LivingEntity> keep = new ArrayList<>();
            for (LivingEntity entity : found) {
                if (instances.containsKey(entity.getUniqueId())) {
                    keep.add(entity);
                }
            }
            // Rebind orphans onto existing encounters that lost their body (leave/rejoin).
            // Never mint a second Hollow Sun encounter for a body that already belongs to one fight.
            for (LivingEntity entity : new ArrayList<>(found)) {
                if (keep.contains(entity) || instances.containsKey(entity.getUniqueId())) {
                    continue;
                }
                BossInstance needy = null;
                for (BossInstance candidate : occupying) {
                    if (candidate != null && candidate.getState() == BossState.ALIVE && !candidate.hasLivingBody()) {
                        needy = candidate;
                        break;
                    }
                }
                if (needy != null) {
                    UUID oldId = needy.getEntity() == null ? null : needy.getEntity().getUniqueId();
                    if (oldId != null) {
                        instances.remove(oldId);
                    }
                    needy.rebindBody(entity);
                    keys.tagBoss(entity, template.getId(), needy.getInstanceId());
                    instances.put(entity.getUniqueId(), needy);
                    followBodyChunks(needy);
                    keep.add(entity);
                    reclaimed++;
                    plugin.getLogger().info(
                            "Boss uniqueness: rebound orphan '" + template.getId()
                                    + "' onto existing encounter (no twin)."
                    );
                    continue;
                }
                if (occupying.size() >= max || keep.size() >= max) {
                    entity.remove();
                    removed++;
                }
            }
            // Registered extras past max-instances must die too (race leftovers).
            while (keep.size() > max) {
                LivingEntity extra = keep.remove(keep.size() - 1);
                BossInstance tracked = instances.remove(extra.getUniqueId());
                if (tracked != null) {
                    instances.remove(tracked.getInstanceId());
                    despawn(tracked, BossDespawnEvent.Reason.PLUGIN_DISABLE, null);
                } else {
                    extra.remove();
                }
                removed++;
            }
            occupying = new ArrayList<>(getByTemplate(template.getId()));
            for (LivingEntity entity : found) {
                if (keep.contains(entity) || !entity.isValid() || entity.isDead()) {
                    continue;
                }
                if (occupying.size() >= max || keep.size() >= max) {
                    entity.remove();
                    removed++;
                    continue;
                }
                String spawnerId = spawners == null ? null : spawners.matchingSpawnerId(template.getId(), entity.getLocation());
                BossInstance instance = new BossInstance(
                        plugin,
                        keys,
                        template,
                        entity.getLocation(),
                        SpawnCause.UNKNOWN,
                        spawnerId,
                        template.getConditions()
                );
                applyWorldRaidScale(instance, entity.getLocation());
                keys.tagBoss(entity, template.getId(), instance.getInstanceId());
                instance.bindEntity(entity);
                instances.put(instance.getInstanceId(), instance);
                instances.put(entity.getUniqueId(), instance);
                retainChunks(entity.getLocation());
                plugin.getServer().getScheduler().runTaskLater(plugin, instance::reapplyCombatStats, 1L);
                keep.add(entity);
                occupying.add(instance);
                reclaimed++;
            }
        }
        if (reclaimed > 0 || removed > 0) {
            plugin.getLogger().info("Boss uniqueness: reclaimed " + reclaimed + ", removed extras " + removed + ".");
        }
        return reclaimed;
    }

    public void absorbChunk(Entity[] entities, SpawnerManager spawners) {
        if (entities == null || entities.length == 0) {
            return;
        }
        boolean needsReclaim = false;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)
                    || !keys.isBoss(living)
                    || keys.isMinion(living)
                    || !living.isValid()
                    || living.isDead()
                    || instances.containsKey(living.getUniqueId())) {
                continue;
            }
            String templateId = keys.templateId(living).orElse(null);
            if (templateId == null) {
                continue;
            }
            int max = templates.get(templateId)
                    .map(template -> Math.max(1, template.getConditions().getMaxInstances()))
                    .orElse(1);
            // Prefer rebinding onto the living encounter over letting a twin linger.
            BossInstance needy = null;
            for (BossInstance candidate : getByTemplate(templateId)) {
                if (candidate != null && candidate.getState() == BossState.ALIVE && !candidate.hasLivingBody()) {
                    needy = candidate;
                    break;
                }
            }
            if (needy != null) {
                UUID oldId = needy.getEntity() == null ? null : needy.getEntity().getUniqueId();
                if (oldId != null) {
                    instances.remove(oldId);
                }
                needy.rebindBody(living);
                keys.tagBoss(living, templateId, needy.getInstanceId());
                instances.put(living.getUniqueId(), needy);
                followBodyChunks(needy);
                plugin.getLogger().info(
                        "Boss '" + templateId + "' orphan body absorbed into existing encounter."
                );
                continue;
            }
            if (countActive(templateId) >= max || countLivingTagged(templateId) > max) {
                living.remove();
            } else {
                needsReclaim = true;
            }
        }
        if (needsReclaim) {
            reclaimOrphans(spawners);
        }
    }

    public boolean tryGlobalSpawnAnnounce(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = spawnAnnounceAt.get(templateId.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 45_000L) {
            return false;
        }
        spawnAnnounceAt.put(templateId.toLowerCase(Locale.ROOT), now);
        return true;
    }

    public boolean isSpawnerOccupied(String spawnerId) {
        if (spawnerId == null || spawnerId.isBlank()) {
            return false;
        }
        return getOccupying().stream()
                .anyMatch(instance -> spawnerId.equalsIgnoreCase(instance.getSpawnerId()));
    }

    /**
     * True when this boss id already owns its max live slots.
     * Force-loads known home chunks so unloaded bodies still count.
     */
    public boolean isTemplateAtCap(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return false;
        }
        int max = templates.get(templateId)
                .map(template -> Math.max(1, template.getConditions().getMaxInstances()))
                .orElse(1);
        loadHomesForTemplate(templateId);
        cullExtras(templateId, max);
        return countActive(templateId) >= max || countLivingTagged(templateId) >= max;
    }

    private void loadHomesForTemplate(String templateId) {
        for (BossInstance instance : getByTemplate(templateId)) {
            loadRecoveryChunks(instance);
        }
    }

    public boolean payoutDeath(BossInstance instance, Player killer) {
        if (instance == null || !instance.markLootPaid()) {
            return false;
        }
        if (isSandboxWorld(instance)) {
            // Test Arena / aether_test — no loot clutter
            onDeath(instance);
            return true;
        }
        if (killer == null) {
            killer = instance.getDamageTracker().topDamager().orElse(null);
        }
        BossDeathEvent event = lootService.buildDeathEvent(instance, killer);
        if (!payIntoMusicBox(instance, event) && !payIntoReliquary(instance, event) && !payIntoBonusChest(instance, event)) {
            lootService.grant(event);
        }
        onDeath(instance);
        return true;
    }

    /**
     * Hanging Saint: XP and the recap land now; the items are lowered onto her stage in a music
     * box and claimed there per player. The box holds the stage until it is gone. False (plain
     * payout) when there is no standing stage to land on.
     */
    private boolean payIntoMusicBox(BossInstance instance, BossDeathEvent event) {
        if (instance.getTemplate() == null
                || !HangingSaintDirector.ID.equalsIgnoreCase(instance.getTemplate().getId())) {
            return false;
        }
        Location stage = instance.getSaintStageCenter();
        if (stage == null || stage.getWorld() == null) {
            return false;
        }
        Map<UUID, List<ItemStack>> bundles = lootService.grantToChest(event);
        if (bundles.isEmpty()) {
            return true;
        }
        retainChunks(stage);
        SeraphineMusicBox.place(
                plugin,
                stage,
                instance.getTemplate().getDisplayName(),
                bundles,
                MUSIC_BOX_DELAY_TICKS,
                () -> releaseChunks(stage)
        );
        return true;
    }

    /**
     * The World Eater pays through a Bonus Chest that generates at the heart of the regenerated
     * island (or where it was summoned): XP and the recap land now, the items wait in the chest.
     * At the site, the chest closing lets the site reset itself.
     */
    private boolean payIntoBonusChest(BossInstance instance, BossDeathEvent event) {
        if (instance.getTemplate() == null || !BONUS_CHEST_BOSS.equalsIgnoreCase(instance.getTemplate().getId())) {
            return false;
        }
        Location spot = instance.getWorldEaterChestSpot();
        if (spot == null) {
            spot = instance.getSpawnLocation();
        }
        if (spot == null || spot.getWorld() == null) {
            return false;
        }
        Map<UUID, List<ItemStack>> bundles = lootService.grantToChest(event);
        Location at = spot.clone();
        retainChunks(at);
        WorldEaterBonusChest.place(
                plugin,
                at,
                instance.getTemplate().getDisplayName(),
                bundles,
                BONUS_CHEST_DELAY_TICKS,
                () -> {
                    releaseChunks(at);
                    de.aetherion.bossengine.instance.worldeater.WorldEaterSite.chestFinished();
                }
        );
        return true;
    }

    /**
     * The Hollow Sun pays through a Hollow Reliquary that falls on its anchor once the supernova has
     * cleared: experience and the recap land now, the item shares wait in the chest.
     *
     * @return false when the reliquary cannot take over and loot should be granted the normal way
     */
    private boolean payIntoReliquary(BossInstance instance, BossDeathEvent event) {
        if (instance.getTemplate() == null
                || !RELIQUARY_BOSS.equalsIgnoreCase(instance.getTemplate().getId())
                || !event.isDropLoot()
                || event.getLootByPlayer().isEmpty()) {
            return false;
        }
        Location anchor = instance.getSpawnLocation();
        if (anchor == null || anchor.getWorld() == null) {
            return false;
        }
        Location at = anchor.clone();
        String bossName = instance.getTemplate().getDisplayName();
        Map<UUID, List<ItemStack>> bundles = lootService.grantToChest(event);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!HollowReliquary.place(plugin, at, bossName, bundles)) {
                        payDirectly(bundles, at);
                    }
                },
                RELIQUARY_DELAY_TICKS
        );
        return true;
    }

    /** Safety net: if the reliquary cannot land, the shares still reach their owners. */
    private void payDirectly(Map<UUID, List<ItemStack>> bundles, Location at) {
        bundles.forEach((playerId, items) -> {
            Player player = Bukkit.getPlayer(playerId);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (player != null && player.isOnline()) {
                    player.getInventory().addItem(item.clone()).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                } else if (at.getWorld() != null) {
                    at.getWorld().dropItemNaturally(at, item.clone());
                }
            }
        });
    }

    private static boolean isSandboxWorld(BossInstance instance) {
        if (instance == null) {
            return false;
        }
        org.bukkit.Location at = instance.getEntity() != null
                ? instance.getEntity().getLocation()
                : instance.getSpawnLocation();
        if (at == null || at.getWorld() == null) {
            return false;
        }
        String name = at.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return name.equals("aether_test") || name.startsWith("aether_test_");
    }

    public void onDeath(BossInstance instance) {
        if (instance == null) {
            return;
        }
        instance.setState(BossState.DEAD);
        skills.execute(instance, SkillTrigger.ON_DEATH);
        instance.despawnMinions();
        bossBars.hide(instance);
        unregister(instance);
    }

    public void refreshHud(BossInstance instance) {
        bossBars.refresh(instance);
    }

    public void despawn(BossInstance instance, BossDespawnEvent.Reason reason, LeashAction leashAction) {
        if (instance == null) {
            return;
        }
        instance.setState(BossState.DESPAWNING);
        Bukkit.getPluginManager().callEvent(new BossDespawnEvent(instance, reason, leashAction));
        instance.despawnMinions();
        bossBars.hide(instance);
        if (instance.getEntity() != null && instance.getEntity().isValid()) {
            instance.getEntity().remove();
        }
        unregister(instance);
    }

    public void tickSkills(BossInstance instance) {
        skills.execute(instance, SkillTrigger.ON_TIMER);
    }

    public SkillManager getSkillManager() {
        return skills;
    }

    public TemplateManager getTemplates() {
        return templates;
    }

    public BossBarHud getHud() {
        return bossBars;
    }

    private void tick() {
        for (BossInstance instance : new ArrayList<>(getOccupying())) {
            if (instance.getState() == BossState.ALIVE && instance.hasLivingBody()) {
                instance.clearMissingBodyTicks();
                followBodyChunks(instance);
            } else if (instance.getState() == BossState.ALIVE && !instance.hasLivingBody()) {
                restoreMissing(instance);
            }
            if (!instance.isEncounterActive()) {
                continue;
            }
            boolean cinematicDone = instance.tick();
            if (cinematicDone) {
                finishDragonKill(instance);
                continue;
            }
            if (!instance.isCinematicDying() && !instance.isTransitioning()) {
                tickSkills(instance);
            }
            if (instance.getState() == BossState.DESPAWNING) {
                despawn(instance, BossDespawnEvent.Reason.LEASH, instance.getConditions().getLeashAction());
            }
        }
        bossBars.tick(getActive());
        // Every second for max-instances=1 bosses (Hollow Sun leave/rejoin twins).
        if (++uniquenessPulse % 20 == 0) {
            enforceAllCaps();
        }
    }

    /** Hard uniqueness pass — especially Hollow Sun after world leave/rejoin. */
    public void enforceAllCaps() {
        for (BossTemplate template : templates.getAll()) {
            if (template == null || template.getId() == null) {
                continue;
            }
            int max = Math.max(1, template.getConditions().getMaxInstances());
            if (max != 1 && countLivingTagged(template.getId()) <= max) {
                continue;
            }
            loadHomesForTemplate(template.getId());
            cullExtras(template.getId(), max);
        }
    }

    public void forgetRemoved(BossInstance instance) {
        if (instance == null || instances.get(instance.getInstanceId()) != instance) {
            return;
        }
        instance.abortCinematic();
        instance.despawnMinions();
        bossBars.hide(instance);
        unregister(instance);
    }

    private void restoreMissing(BossInstance instance) {
        if (instance == null || instance.getState() != BossState.ALIVE) {
            return;
        }
        if (instance.isBodyRestoreSuppressed()) {
            loadRecoveryChunks(instance);
            return;
        }

        Location spawn = instance.getSpawnLocation();
        Location lastSeen = instance.getLastSeenLocation();
        loadRecoveryChunks(instance);

        LivingEntity old = instance.getEntity();
        UUID oldId = old == null ? null : old.getUniqueId();

        // After chunk load: same UUID often comes back (unload ≠ death).
        if (oldId != null) {
            Entity refreshed = Bukkit.getEntity(oldId);
            if (refreshed instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                instance.rebindBody(living);
                instances.put(living.getUniqueId(), instance);
                followBodyChunks(instance);
                plugin.getLogger().info(
                        "Boss '" + instance.getTemplate().getId()
                                + "' body reattached after chunk load (same UUID)."
                );
                return;
            }
        }
        if (old != null && old.isValid() && !old.isDead()) {
            instances.put(old.getUniqueId(), instance);
            followBodyChunks(instance);
            return;
        }

        LivingEntity recovered = findExistingBody(instance, oldId, lastSeen);
        if (recovered == null && spawn != null) {
            recovered = findExistingBody(instance, oldId, spawn);
        }
        // Hard uniqueness: never mint a twin if another tagged body of this template exists.
        if (recovered == null) {
            recovered = findAnyTaggedBody(instance.getTemplate().getId(), spawn != null ? spawn : lastSeen);
        }
        if (recovered != null) {
            if (oldId != null) {
                instances.remove(oldId);
            }
            instance.rebindBody(recovered);
            instances.put(recovered.getUniqueId(), instance);
            followBodyChunks(instance);
            plugin.getLogger().info(
                    "Boss '" + instance.getTemplate().getId()
                            + "' body recovered nearby (no replacement)."
            );
            return;
        }

        // Cap already filled by another encounter — never create a second Hollow Sun.
        int max = Math.max(1, instance.getConditions().getMaxInstances());
        if (countLivingTagged(instance.getTemplate().getId()) >= max
                || countActive(instance.getTemplate().getId()) > max) {
            cullExtras(instance.getTemplate().getId(), max);
            return;
        }

        int missingTicks = instance.noteMissingBodyTick();
        // max-instances=1: never mint a twin while the unloaded body may still reload from disk.
        // Hold the slot, keep force-loading, and only replace after a long confirmed absence.
        boolean unique = max <= 1;
        int waitTicks = unique
                ? (instance.isBodyUnloaded() ? 400 : 200)
                : (instance.isBodyUnloaded() ? 100 : 60);
        if (missingTicks < waitTicks) {
            return;
        }

        Location focus = lastSeen != null ? lastSeen : spawn;
        if (!playersNear(focus, 96) && !playersNear(spawn, 96)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastBodySpawnAt.get(instance.getInstanceId());
        long throttleMs = unique ? 60_000L
                : ("hollow_lurker".equalsIgnoreCase(instance.getTemplate().getId()) ? 45_000L : 20_000L);
        if (last != null && now - last < throttleMs) {
            return;
        }

        Location at = lastSeen != null ? lastSeen.clone() : (spawn == null ? null : spawn.clone());
        if (at == null || at.getWorld() == null) {
            return;
        }
        // One last wide load+scan so a leave/rejoin body is rebound instead of duplicated.
        forceLoadAround(at, 4);
        if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(at.getWorld())) {
            forceLoadAround(spawn, 4);
        }
        LivingEntity late = findExistingBody(instance, oldId, at);
        if (late == null && spawn != null) {
            late = findExistingBody(instance, oldId, spawn);
        }
        if (late == null) {
            late = findAnyTaggedBody(instance.getTemplate().getId(), at);
        }
        if (late != null) {
            if (oldId != null) {
                instances.remove(oldId);
            }
            instance.rebindBody(late);
            instances.put(late.getUniqueId(), instance);
            followBodyChunks(instance);
            plugin.getLogger().info(
                    "Boss '" + instance.getTemplate().getId()
                            + "' body found after wide load — rebound, no replacement."
            );
            return;
        }
        if (unique && countLivingTagged(instance.getTemplate().getId()) >= 1) {
            cullExtras(instance.getTemplate().getId(), 1);
            return;
        }

        at.getChunk().load(true);
        at = safeStandNear(at, 2);

        LivingEntity entity = spawnEntity(instance.getTemplate(), at, instance);
        if (entity == null || !entity.isValid()) {
            plugin.getLogger().warning(
                    "Boss '" + instance.getTemplate().getId()
                            + "' lost its body mid-fight. Holding the spawn slot instead of creating a new boss."
            );
            return;
        }
        lastBodySpawnAt.put(instance.getInstanceId(), now);
        if (oldId != null) {
            instances.remove(oldId);
        }
        instance.rebindBody(entity);
        instances.put(entity.getUniqueId(), instance);
        followBodyChunks(instance);
        plugin.getLogger().warning(
                "Boss '" + instance.getTemplate().getId()
                        + "' body was gone after wait. Spawned replacement at last seen. Same fight, same HP."
        );
    }

    private void forceLoadAround(Location center, int chunkRadius) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int r = Math.max(0, chunkRadius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                world.getChunkAt(x, z).load(true);
            }
        }
        retainChunks(center);
    }

    private void loadRecoveryChunks(BossInstance instance) {
        Location spawn = instance.getSpawnLocation();
        Location lastSeen = instance.getLastSeenLocation();
        if (lastSeen != null && lastSeen.getWorld() != null) {
            lastSeen.getChunk().load(true);
            retainChunks(lastSeen);
        }
        if (spawn != null && spawn.getWorld() != null) {
            spawn.getChunk().load(true);
            retainChunks(spawn);
        }
    }

    private void followBodyChunks(BossInstance instance) {
        if (instance == null) {
            return;
        }
        LivingEntity body = instance.getEntity();
        Location here = body != null && body.isValid()
                ? body.getLocation()
                : instance.getLastSeenLocation();
        if (here == null || here.getWorld() == null) {
            return;
        }
        UUID id = instance.getInstanceId();
        Location prev = bodyChunkAnchor.get(id);
        if (prev != null
                && prev.getWorld() != null
                && prev.getWorld().equals(here.getWorld())
                && (prev.getBlockX() >> 4) == (here.getBlockX() >> 4)
                && (prev.getBlockZ() >> 4) == (here.getBlockZ() >> 4)) {
            return;
        }
        if (prev != null) {
            releaseChunks(prev);
        }
        retainChunks(here);
        bodyChunkAnchor.put(id, here.clone());
    }

    private boolean playersNear(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double r2 = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.isOnline() && player.getLocation().distanceSquared(location) <= r2) {
                return true;
            }
        }
        return false;
    }

    private LivingEntity findExistingBody(BossInstance instance, UUID oldId, Location around) {
        if (oldId != null) {
            Entity found = Bukkit.getEntity(oldId);
            if (found instanceof LivingEntity living && isInstanceBody(instance, living)) {
                return living;
            }
        }
        if (around == null || around.getWorld() == null) {
            return null;
        }
        for (Entity nearby : around.getWorld().getNearbyEntities(around, 96, 48, 96)) {
            if (nearby instanceof LivingEntity living && isInstanceBody(instance, living)) {
                return living;
            }
        }
        return null;
    }

    /** Any living tagged body for this template (other encounter / orphan twin). */
    private LivingEntity findAnyTaggedBody(String templateId, Location preferNear) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!entity.isValid() || entity.isDead() || keys.isMinion(entity)) {
                    continue;
                }
                if (keys.templateId(entity).filter(id -> id.equalsIgnoreCase(templateId)).isEmpty()) {
                    continue;
                }
                if (preferNear == null || preferNear.getWorld() == null || !preferNear.getWorld().equals(world)) {
                    if (best == null) {
                        best = entity;
                    }
                    continue;
                }
                double dist = entity.getLocation().distanceSquared(preferNear);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entity;
                }
            }
        }
        return best;
    }

    private boolean isInstanceBody(BossInstance instance, LivingEntity entity) {
        return entity != null
                && entity.isValid()
                && !entity.isDead()
                && keys.instanceId(entity).filter(id -> id.equals(instance.getInstanceId())).isPresent();
    }

    private void finishDragonKill(BossInstance instance) {
        payoutDeath(instance, null);
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        entity.setInvulnerable(false);
        // Magma/Slime bosses must not split into vanilla children on cinematic kill.
        if (entity instanceof org.bukkit.entity.MagmaCube cube) {
            cube.setSize(1);
        } else if (entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(1);
        }
        try {
            entity.setHealth(0);
        } catch (IllegalArgumentException ignored) {
            entity.remove();
        }
    }

    private void unregister(BossInstance instance) {
        instances.remove(instance.getInstanceId());
        if (instance.getEntity() != null) {
            instances.remove(instance.getEntity().getUniqueId());
        }
        Location bodyAnchor = bodyChunkAnchor.remove(instance.getInstanceId());
        if (bodyAnchor != null) {
            releaseChunks(bodyAnchor);
        }
        releaseChunks(instance.getSpawnLocation());
        lastBodySpawnAt.remove(instance.getInstanceId());
    }

    @SuppressWarnings("unchecked")
    private LivingEntity spawnEntity(BossTemplate template, Location location, BossInstance instance) {
        World world = location.getWorld();
        Class<? extends Entity> entityClass = template.getEntityType().getEntityClass();
        if (world == null || entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            plugin.getLogger().warning("Cannot spawn boss " + template.getId() + ": invalid entity type.");
            return null;
        }

        location.getChunk().load();

        Class<? extends LivingEntity> livingClass = (Class<? extends LivingEntity>) entityClass;
        AtomicReference<LivingEntity> spawned = new AtomicReference<>();

        WorldGuardSpawnGuard.runGuarded(() -> spawned.set(world.spawn(
                location,
                livingClass,
                CreatureSpawnEvent.SpawnReason.CUSTOM,
                entity -> {
                    keys.tagBoss(entity, template.getId(), instance.getInstanceId());
                    // Spawner-managed arena bosses must not serialize into region files — leftovers
                    // (Ashen Void dragon + loot props) were surviving restarts from entities/*.mca.
                    boolean arenaBoss = "dungeon_aetherion".equalsIgnoreCase(template.getId())
                            || (location.getWorld() != null
                            && "ashen_void".equalsIgnoreCase(location.getWorld().getName()));
                    entity.setPersistent(!arenaBoss);
                    entity.setRemoveWhenFarAway(false);
                    if (entity instanceof org.bukkit.entity.EnderDragon dragon) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.HOVER);
                        dragon.setGravity(false);
                    }
                }
        )));

        return spawned.get();
    }

    private Location safeStandNear(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) {
            return origin;
        }
        World world = origin.getWorld();
        int r = Math.max(1, radius);
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = 0; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location probe = origin.clone().add(dx, dy, dz);
                    org.bukkit.block.Block ground = probe.getBlock();
                    org.bukkit.block.Block above = ground.getRelative(0, 1, 0);
                    org.bukkit.block.Block below = ground.getRelative(0, -1, 0);
                    if (ground.getType().isSolid() || above.getType().isSolid()) {
                        continue;
                    }
                    if (!below.getType().isSolid() && !below.isLiquid()) {
                        continue;
                    }
                    double dist = probe.distanceSquared(origin);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = probe;
                    }
                }
            }
        }
        if (best == null) {
            return origin;
        }
        best.setYaw(origin.getYaw());
        best.setPitch(0);
        return best;
    }

    private void retainChunks(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (int x = chunkX - 2; x <= chunkX + 2; x++) {
            for (int z = chunkZ - 2; z <= chunkZ + 2; z++) {
                String key = chunkKey(world, x, z);
                chunkTickets.merge(key, 1, Integer::sum);
                world.addPluginChunkTicket(x, z, plugin);
            }
        }
    }

    private void releaseChunks(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (int x = chunkX - 2; x <= chunkX + 2; x++) {
            for (int z = chunkZ - 2; z <= chunkZ + 2; z++) {
                String key = chunkKey(world, x, z);
                int remaining = chunkTickets.merge(key, -1, Integer::sum);
                if (remaining <= 0) {
                    chunkTickets.remove(key);
                    world.removePluginChunkTicket(x, z, plugin);
                }
            }
        }
    }

    private void applyWorldRaidScale(BossInstance instance, Location location) {
        if (instance == null || location == null || location.getWorld() == null) {
            return;
        }
        String worldName = location.getWorld().getName().toLowerCase(Locale.ROOT);
        if (worldName.startsWith("aedun_") || worldName.startsWith("ae_dun")) {
            return;
        }
        if ("dungeon_sentinel".equalsIgnoreCase(instance.getTemplate().getId())) {
            return;
        }
        double radius = Math.max(48.0, instance.getConditions().getLeashRadius());
        int players = countNearbyPlayers(location, radius);
        instance.applyRaidScale(players);
        if (players <= 1) {
            return;
        }
        String message = instance.getTemplate().getDisplayName()
                + " &7scaled for &f" + players + " &7players.";
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radius * radius) {
                player.sendMessage(TextUtil.component(message));
            }
        }
    }

    private static int countNearbyPlayers(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return 1;
        }
        double radiusSq = radius * radius;
        int count = 0;
        for (Player player : location.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= radiusSq) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static String chunkKey(World world, int chunkX, int chunkZ) {
        return world.getUID() + ":" + chunkX + ":" + chunkZ;
    }
}
