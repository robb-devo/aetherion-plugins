package de.aetherion.items.world;

import de.aetherion.core.AetherEntities;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BossSpawnAccess;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.util.QuestProgressHook;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Borderlands combat rite: rare spirit vials from hostiles → light-gray powder altar → T1 boss.
 */
public final class BorderlandsRiteService implements Listener {

    public static final String QUEST_ID = "border_rites";
    /** T1 rite vials — surface tiers only. Crypt has its own T2 vials. */
    public static final double DROP_CHANCE_T1 = 0.05;
    public static final double DROP_CHANCE_T2 = 0.10;
    public static final double DROP_CHANCE_T3 = 0.15;
    public static final double DROP_CHANCE_CRYPT = 0.10;
    /** @deprecated use tier chances */
    public static final double DROP_CHANCE = DROP_CHANCE_T1;
    public static final int RITUAL_SECONDS = 10;

    /** Standing coords reported by player; powder is under / at this spot. */
    public static final double ALTAR_X = 237.5;
    public static final double ALTAR_Y = 60.0;
    public static final double ALTAR_Z = 231.5;
    public static final String ALTAR_WORLD = "world";
    public static final double ALTAR_REACH = 3.5;
    public static final double RITE_NPC_SAFE_RADIUS = 20.0;
    /** Clear / no-spawn bubble around an active rite boss. */
    public static final double BOSS_CLEAR_RADIUS = 18.0;

    /** Rite / tutorial vial pool — only these four land bosses. */
    public static final List<SpiritBoss> T1 = List.of(
            new SpiritBoss("hollow_lurker", "Hollow Lurker", Color.fromRGB(90, 40, 50)),
            new SpiritBoss("mcnugget", "McNugget", Color.fromRGB(180, 90, 40)),
            new SpiritBoss("bridge_troll", "Bridge Troll", Color.fromRGB(70, 90, 60)),
            new SpiritBoss("skuldugery", "Skuldugery", Color.fromRGB(120, 50, 40))
    );

    /** Colosseum / Crypt T2 pool — starts with Pathwarden. */
    public static final List<SpiritBoss> T2 = List.of(
            new SpiritBoss("pathwarden", "Pathwarden", Color.fromRGB(160, 40, 40))
    );

    /** Alias — Pathwarden / Squidward / others are never in this pool. */
    public static final List<SpiritBoss> RITE_POOL = T1;

    private final JavaPlugin plugin;
    private final MobZoneService mobZones;
    private final Map<UUID, Long> ritualBusyUntil = new ConcurrentHashMap<>();
    /** Active boss arenas: no T1–T3 spawn / walk-in while the boss lives. */
    private final List<BossBubble> activeBossBubbles = new CopyOnWriteArrayList<>();
    /** Chunk load/unload only queues one sweep; the scan itself stays on the scheduler. */
    private boolean outlineSweepQueued;
    private volatile double riteNpcX = 251.5;
    private volatile double riteNpcY = 65.0;
    private volatile double riteNpcZ = 173.5;
    private volatile String riteNpcWorld = ALTAR_WORLD;

    private static volatile BorderlandsRiteService instance;

    public BorderlandsRiteService(JavaPlugin plugin, MobZoneService mobZones) {
        this.plugin = plugin;
        this.mobZones = mobZones;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Particle column only — no beacon/blocks. One-shot cleanup if an old buried beacon remains.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanupAltarBeaconBlocks, 40L);
        // Outline block displays are not spawned. A startup sweep alone looked like a
        // fix after restart; the 5-tick task kept appending invisible displays overnight.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::sweepAltarOutlines, 60L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepAltarOutlines, 200L, 200L);
        plugin.getServer().getScheduler().runTask(plugin, BorderlandsRiteService::purgeOutlineTeams);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAltarBeams, 20L, 5L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshRiteNpcCenter, 60L, 100L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickBossBubbles, 20L, 20L);
        refreshRiteNpcCenter();
    }

    public static boolean isMobSafeZone(Location location) {
        BorderlandsRiteService service = instance;
        if (service == null || location == null || location.getWorld() == null) {
            return false;
        }
        return service.inMobSafeZone(location) || service.inActiveBossBubble(location);
    }

    private boolean inMobSafeZone(Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(riteNpcWorld)) {
            return false;
        }
        double dx = location.getX() - riteNpcX;
        double dy = location.getY() - riteNpcY;
        double dz = location.getZ() - riteNpcZ;
        return dx * dx + dy * dy + dz * dz <= RITE_NPC_SAFE_RADIUS * RITE_NPC_SAFE_RADIUS;
    }

    private boolean inActiveBossBubble(Location location) {
        if (location.getWorld() == null || activeBossBubbles.isEmpty()) {
            return false;
        }
        double r2 = BOSS_CLEAR_RADIUS * BOSS_CLEAR_RADIUS;
        for (BossBubble bubble : activeBossBubbles) {
            Location at = bubble.center;
            if (at.getWorld() == null || !at.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (location.distanceSquared(at) <= r2) {
                return true;
            }
        }
        return false;
    }

    private void tickBossBubbles() {
        if (activeBossBubbles.isEmpty()) {
            return;
        }
        for (BossBubble bubble : activeBossBubbles) {
            Location at = bubble.center;
            if (at.getWorld() == null) {
                activeBossBubbles.remove(bubble);
                continue;
            }
            Entity boss = bubble.bossId == null ? null : Bukkit.getEntity(bubble.bossId);
            if (boss == null || !boss.isValid() || boss.isDead()) {
                // Boss may not be chunk-loaded — give a grace window, then drop.
                if (System.currentTimeMillis() > bubble.keepUntilMs) {
                    activeBossBubbles.remove(bubble);
                }
                continue;
            }
            bubble.keepUntilMs = System.currentTimeMillis() + 15_000L;
            // Sweep T1–T3 that wander back in.
            clearTrashMobs(at, BOSS_CLEAR_RADIUS);
        }
    }

    private void refreshRiteNpcCenter() {
        try {
            org.bukkit.plugin.Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
            if (quests == null || !quests.isEnabled()) {
                return;
            }
            java.io.File file = new java.io.File(quests.getDataFolder(), "npcs.yml");
            if (!file.exists()) {
                return;
            }
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection section =
                    yaml.getConfigurationSection("npcs.rite_keeper");
            if (section == null) {
                return;
            }
            riteNpcWorld = section.getString("world", ALTAR_WORLD);
            riteNpcX = section.getDouble("x", riteNpcX);
            riteNpcY = section.getDouble("y", riteNpcY);
            riteNpcZ = section.getDouble("z", riteNpcZ);
        } catch (Throwable ignored) {
        }
    }

    /** Magenta core of the rite marker (distinct from vanilla white/yellow beacon). */
    private static final Particle.DustOptions BEAM_CORE =
            new Particle.DustOptions(Color.fromRGB(210, 45, 255), 3.6f);
    private static final Particle.DustOptions BEAM_GLOW =
            new Particle.DustOptions(Color.fromRGB(255, 120, 255), 2.4f);
    private static final Particle.DustOptions BEAM_RIM =
            new Particle.DustOptions(Color.fromRGB(40, 230, 255), 1.8f);
    private static final Particle.DustTransition BEAM_FADE =
            new Particle.DustTransition(
                    Color.fromRGB(220, 50, 255),
                    Color.fromRGB(50, 240, 255),
                    2.8f);

    /**
     * Remove leftover buried beacon/iron/magenta glass from earlier iterations.
     * Altar surface stays light-gray concrete powder (world build untouched after this).
     */
    private void cleanupAltarBeaconBlocks() {
        World world = Bukkit.getWorld(ALTAR_WORLD);
        if (world == null) {
            return;
        }
        int x = (int) Math.floor(ALTAR_X);
        int y = (int) Math.floor(ALTAR_Y);
        int z = (int) Math.floor(ALTAR_Z);

        Block surface = world.getBlockAt(x, y, z);
        if (surface.getType() == Material.BEACON
                || surface.getType().name().contains("GLASS")
                || surface.getType() == Material.AIR) {
            surface.setType(Material.LIGHT_GRAY_CONCRETE_POWDER, false);
        }

        Block under = world.getBlockAt(x, y - 1, z);
        if (under.getType() == Material.BEACON) {
            under.setType(Material.STONE, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block base = world.getBlockAt(x + dx, y - 2, z + dz);
                if (base.getType() == Material.IRON_BLOCK) {
                    base.setType(Material.STONE, false);
                }
            }
        }
    }

    private static final Particle.DustOptions BEAM_SOFT =
            new Particle.DustOptions(Color.fromRGB(190, 80, 255), 1.2f);
    private static final Particle.DustOptions PAD_GLOW =
            new Particle.DustOptions(Color.fromRGB(255, 140, 255), 1.35f);
    private static final Particle.DustOptions PAD_RING =
            new Particle.DustOptions(Color.fromRGB(60, 230, 255), 1.15f);

    /**
     * Loud magenta→cyan particle column. {@code force=true} bypasses the ~16-block particle
     * cull so players ~120–180 blocks out still see the beam (view-distance permitting).
     * Bottom ~3 blocks stay soft; the powder pad gets a light ring so it reads as special.
     */
    private void tickAltarBeams() {
        World world = Bukkit.getWorld(ALTAR_WORLD);
        if (world == null || ritualActiveNearAltar()) {
            return;
        }
        Location base = new Location(world, ALTAR_X, ALTAR_Y + 1.05, ALTAR_Z);
        Location pad = new Location(world, ALTAR_X, ALTAR_Y + 0.12, ALTAR_Z);
        final double farSq = 200.0 * 200.0;
        final double nearSq = 48.0 * 48.0;
        final int softUntil = 3; // first ~3 blocks: light fade-in above the powder

        for (Player player : world.getPlayers()) {
            double distSq = player.getLocation().distanceSquared(base);
            if (distSq > farSq) {
                continue;
            }
            boolean near = distSq <= nearSq;
            int height = near ? 56 : 72;
            int step = near ? 1 : 2;
            for (int y = 0; y < height; y += step) {
                Location point = base.clone().add(0, y, 0);
                if (y < softUntil) {
                    // Soft base — don't bury the clickable powder.
                    if (near && y % 2 == 0) {
                        player.spawnParticle(Particle.DUST, point, 1, 0.04, 0.0, 0.04, 0.0, BEAM_SOFT, true);
                    }
                    continue;
                }
                player.spawnParticle(Particle.DUST, point, 1, 0.02, 0.0, 0.02, 0.0, BEAM_CORE, true);
                if (y % 2 == 0) {
                    player.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1, 0.06, 0.0, 0.06, 0.0, BEAM_FADE, true);
                }
                if (near && y % 3 == 0) {
                    player.spawnParticle(Particle.DUST, point, 1, 0.14, 0.0, 0.14, 0.0, BEAM_RIM, true);
                    player.spawnParticle(Particle.DUST, point, 1, 0.08, 0.0, 0.08, 0.0, BEAM_GLOW, true);
                }
                if (near && y % 5 == 0) {
                    player.spawnParticle(Particle.END_ROD, point, 1, 0.05, 0.0, 0.05, 0.0, null, true);
                }
            }
            // Summon pad marker: ring around the block + light sparkles (block face stays readable).
            if (near) {
                double spin = (System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2.0;
                for (int i = 0; i < 6; i++) {
                    double ang = spin + (Math.PI * 2.0 * i / 6.0);
                    Location rim = pad.clone().add(Math.cos(ang) * 0.55, 0.02, Math.sin(ang) * 0.55);
                    player.spawnParticle(Particle.DUST, rim, 1, 0.0, 0.0, 0.0, 0.0, PAD_RING, true);
                }
                player.spawnParticle(Particle.DUST, pad.clone().add(0, 0.35, 0), 2, 0.18, 0.05, 0.18, 0.0, PAD_GLOW, true);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, pad.clone().add(0, 0.25, 0), 2, 0.22, 0.08, 0.22, 0.005, null, true);
                player.spawnParticle(Particle.END_ROD, pad.clone().add(0, 0.45, 0), 1, 0.12, 0.05, 0.12, 0.0, null, true);
            }
        }
    }

    /** Invisible powder BlockDisplay. Nothing in the plugin spawns this anymore. */
    public static final String OUTLINE_TAG = "aetherion_altar_outline";
    private static final String OUTLINE_TEAM_PREFIX = "ae_altar_";

    /**
     * Spawn contract: there is none.
     * <p>
     * The altar is a normal enchantment table. The old outline was a BlockDisplay
     * with {@code setVisibleByDefault(false)} — players never saw a glow. A task
     * every 5 ticks called {@code ensureAltarOutline}, and whenever the tracked
     * UUID was missing it spawned another display and tried to delete "the others".
     * That delete did not keep the set at one, so the task appended about four
     * displays a second (~230k over 16 hours). Restart only ran a one-shot scrub
     * of whatever was already loaded.
     * <p>
     * Ritual VFX stays on the particle column in {@link #tickAltarBeams()}. This
     * sweep deletes every leftover {@code aetherion_altar_outline}. It does not
     * spawn a replacement.
     */
    private void sweepAltarOutlines() {
        int removed = discardAltarOutlines();
        if (removed > 0) {
            purgeOutlineTeams();
            plugin.getLogger().info(
                    "Removed " + removed + " altar outline block displays (no outline is spawned)."
            );
        }
    }

    /**
     * Deletes every loaded altar outline. Paper sometimes rejects {@code remove()}
     * during a chunk update; those ids are retried on the next tick. Nothing is spawned.
     */
    private int discardAltarOutlines() {
        List<UUID> pending = new ArrayList<>();
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntitiesByClass(BlockDisplay.class))) {
                if (!isAltarOutline(entity)) {
                    continue;
                }
                if (tryRemoveOutline(entity)) {
                    removed++;
                } else {
                    pending.add(entity.getUniqueId());
                }
            }
        }
        if (!pending.isEmpty() && plugin.isEnabled()) {
            List<UUID> ids = List.copyOf(pending);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int later = 0;
                for (UUID id : ids) {
                    Entity entity = Bukkit.getEntity(id);
                    if (isAltarOutline(entity) && tryRemoveOutline(entity)) {
                        later++;
                    }
                }
                if (later > 0) {
                    purgeOutlineTeams();
                    plugin.getLogger().info(
                            "Removed " + later + " altar outline block displays after chunk update."
                    );
                }
            });
        }
        return removed;
    }

    private static boolean tryRemoveOutline(Entity entity) {
        try {
            entity.setPersistent(false);
            entity.remove();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAltarOutline(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(OUTLINE_TAG);
    }

    public void shutdown() {
        sweepAltarOutlines();
        purgeOutlineTeams();
    }

    public static void shutdownActive() {
        BorderlandsRiteService service = instance;
        if (service != null) {
            service.shutdown();
        }
    }

    @EventHandler
    public void onChunkLoadDropAltarOutlines(ChunkLoadEvent event) {
        if (event.getChunk() == null || !chunkHasAltarOutline(event.getChunk().getEntities())) {
            return;
        }
        queueOutlineSweep();
    }

    @EventHandler
    public void onChunkUnloadDropAltarOutlines(ChunkUnloadEvent event) {
        if (event.getChunk() == null || !chunkHasAltarOutline(event.getChunk().getEntities())) {
            return;
        }
        // remove() during chunk unload is rejected. Drop them on the next tick
        // if they are still loaded, and again when the chunk loads.
        queueOutlineSweep();
    }

    private void queueOutlineSweep() {
        if (outlineSweepQueued || !plugin.isEnabled()) {
            return;
        }
        outlineSweepQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            outlineSweepQueued = false;
            sweepAltarOutlines();
        });
    }

    private static boolean chunkHasAltarOutline(Entity[] entities) {
        if (entities == null) {
            return false;
        }
        for (Entity entity : entities) {
            if (isAltarOutline(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paper stores entity members as {@code $uuid}. Plain-UUID remove/hasEntry
     * never matched, so altar color cycling left hundreds of thousands of
     * stale members in {@code scoreboard.dat} and join packets blew past the limit.
     */
    public static void purgeOutlineTeams() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith(OUTLINE_TEAM_PREFIX)) {
                continue;
            }
            for (String entry : List.copyOf(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
    }

    private boolean ritualActiveNearAltar() {
        long now = System.currentTimeMillis();
        for (Long until : ritualBusyUntil.values()) {
            if (until != null && until > now) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalNearRiteNpc(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof org.bukkit.entity.Animals)) {
            return;
        }
        // Never block BossEngine / system bosses (McNugget is a CHICKEN).
        if (de.aetherion.core.AetherEntities.isBoss(event.getEntity())
                || de.aetherion.core.AetherEntities.isSystemOwned(event.getEntity())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.DEFAULT
                && reason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        // CUSTOM boss spawns are tagged a tick later — allow CUSTOM at the altar always.
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM && isAltarNearby(event.getLocation())) {
            return;
        }
        if (isMobSafeZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isAltarNearby(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (!at.getWorld().getName().equalsIgnoreCase(ALTAR_WORLD)) {
            return false;
        }
        double dx = at.getX() - ALTAR_X;
        double dy = at.getY() - ALTAR_Y;
        double dz = at.getZ() - ALTAR_Z;
        return dx * dx + dy * dy + dz * dz <= 64.0; // 8m
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBorderlandsKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Location at = event.getEntity().getLocation();
        if (mobZones == null || !mobZones.containsBorderlands(at)) {
            return;
        }
        double chance = vialChanceFor(event.getEntity());
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        boolean crypt = WildlifeLooks.tierOf(event.getEntity()) == WildlifeLooks.TIER_CRYPT;
        SpiritBoss pick = crypt
                ? T2.get(ThreadLocalRandom.current().nextInt(T2.size()))
                : RITE_POOL.get(ThreadLocalRandom.current().nextInt(RITE_POOL.size()));
        ItemStack vial = crypt ? createCryptSpirit(pick) : createSpirit(pick);
        if (vial == null) {
            return;
        }
        Map<Integer, ItemStack> overflow = killer.getInventory().addItem(vial);
        if (!overflow.isEmpty()) {
            at.getWorld().dropItemNaturally(at, vial);
        }
        if (crypt) {
            killer.sendMessage("§5Crypt §8» §fA stronger spirit vial — §6" + pick.display() + "§f.");
            killer.playSound(killer.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.75f, 0.55f);
            at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1, 0), 22, 0.4, 0.5, 0.4, 0.01);
            // Soft hint toward the Colosseum Proctor.
            try {
                Class.forName("de.aetherion.quests.ui.QuestHint")
                        .getMethod("show", org.bukkit.entity.Player.class, String.class, String.class)
                        .invoke(null, killer, "arena_proctor", "Proctor");
            } catch (ReflectiveOperationException ignored) {
            }
            killer.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Hint · Proctor · Colosseum",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
        } else {
            killer.sendMessage("§cBorderlands §8» §fA spirit vial coagulates — §c" + pick.display() + "§f.");
            killer.playSound(killer.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 0.7f);
            at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 18, 0.4, 0.5, 0.4, 0.01);
        }
    }

    /** T1 5% · Sturdy 10% · Brute 15% · Crypt 10% (T2 vials). */
    private static double vialChanceFor(LivingEntity entity) {
        return switch (WildlifeLooks.tierOf(entity)) {
            case WildlifeLooks.TIER_STURDY -> DROP_CHANCE_T2;
            case WildlifeLooks.TIER_BRUTE -> DROP_CHANCE_T3;
            case WildlifeLooks.TIER_CRYPT -> DROP_CHANCE_CRYPT;
            default -> DROP_CHANCE_T1;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrinkSpirit(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!isSpirit(item) && !isCryptSpirit(item)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cAltar vial — not a drink.");
    }

    /** Block the drink wind-up in open air; altar / Colosseum pad clicks stay usable. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTryDrinkSpirit(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (!isSpirit(hand) && !isCryptSpirit(hand)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material type = block.getType();
        if (type != Material.LIGHT_GRAY_CONCRETE_POWDER) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (isCryptSpirit(hand)) {
            event.getPlayer().sendMessage("§6This vial belongs to the Colosseum — §7glowing mark in the ring (after the Proctor shows you once).");
            return;
        }
        if (!isSpirit(hand)) {
            return;
        }
        if (!isAltarBlock(block)) {
            event.getPlayer().sendMessage("§cWrong powder. §7The Borderlands rite needs the marked altar.");
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Long busy = ritualBusyUntil.get(player.getUniqueId());
        if (busy != null && busy > System.currentTimeMillis()) {
            player.sendMessage("§cRitual already running.");
            return;
        }
        String bossId = spiritBossId(hand);
        SpiritBoss boss = byId(bossId);
        if (boss == null) {
            player.sendMessage("§cThis vial is empty of meaning.");
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        startRitual(player, boss, block.getLocation().add(0.5, 1.0, 0.5));
    }

    private void startRitual(Player player, SpiritBoss boss, Location spawnAt) {
        ritualBusyUntil.put(player.getUniqueId(), System.currentTimeMillis() + (RITUAL_SECONDS + 2) * 1000L);
        World world = spawnAt.getWorld();
        // Clear trash mobs as soon as the vial is placed — fight focus starts here.
        clearTrashMobs(spawnAt, BOSS_CLEAR_RADIUS);
        igniteRitual(player, spawnAt, boss);
        QuestProgressHook.noteUsed(player, "BORDERLANDS_RITE");

        player.sendMessage("§cRite §8» §fCalling §c" + boss.display() + "§f… stand ready.");

        BossBar bar = BossBar.bossBar(
                Component.text("Summoning " + boss.display(), NamedTextColor.RED, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);

        final int[] left = {RITUAL_SECONDS};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                player.hideBossBar(bar);
                task.cancel();
                return;
            }
            if (left[0] <= 0) {
                player.hideBossBar(bar);
                task.cancel();
                spawnBoss(player, boss, spawnAt);
                return;
            }
            float progress = left[0] / (float) RITUAL_SECONDS;
            bar.progress(Math.max(0f, Math.min(1f, progress)));
            bar.name(Component.text(
                    "Summoning " + boss.display() + " · " + left[0] + "s",
                    NamedTextColor.RED,
                    TextDecoration.BOLD
            ));
            if (world != null) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, spawnAt, 10, 0.45, 0.35, 0.45, 0.01);
                world.spawnParticle(Particle.SMOKE, spawnAt.clone().add(0, 0.4, 0), 8, 0.35, 0.25, 0.35, 0.01);
                if (left[0] % 2 == 0) {
                    world.playSound(spawnAt, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.35f, 0.75f);
                }
            }
            left[0]--;
        }, 0L, 20L);
    }

    /** Soft thunder + spark column — readable, not ear-bleed. */
    private void igniteRitual(Player player, Location spawnAt, SpiritBoss boss) {
        World world = spawnAt.getWorld();
        if (world == null) {
            return;
        }
        // Quiet distant thunder — volume low, pitch slightly high so it doesn't boom.
        world.playSound(spawnAt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.28f, 1.35f);
        world.playSound(spawnAt, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.22f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 0.55f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 0.8f);

        world.spawnParticle(Particle.FLASH, spawnAt.clone().add(0, 1.2, 0), 1);
        world.spawnParticle(Particle.EXPLOSION, spawnAt.clone().add(0, 0.6, 0), 1);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, spawnAt, 55, 0.7, 0.8, 0.7, 0.03);
        world.spawnParticle(Particle.SOUL, spawnAt.clone().add(0, 1, 0), 35, 0.5, 0.9, 0.5, 0.02);
        world.spawnParticle(Particle.LAVA, spawnAt, 12, 0.4, 0.2, 0.4, 0);
        for (int y = 0; y < 18; y++) {
            Location column = spawnAt.clone().add(0, y * 0.55, 0);
            world.spawnParticle(Particle.END_ROD, column, 2, 0.05, 0.1, 0.05, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, column, 3, 0.12, 0.12, 0.12, 0.01);
        }
        // Tiny fake strike for the caster only (no real lightning entity / fire).
        player.spawnParticle(Particle.FIREWORK, spawnAt.clone().add(0, 1.5, 0), 25, 0.3, 0.6, 0.3, 0.05);
    }

    private void spawnBoss(Player player, SpiritBoss boss, Location at) {
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses == null) {
            player.sendMessage("§cBossEngine offline — rite fizzles.");
            ritualBusyUntil.remove(player.getUniqueId());
            return;
        }
        clearTrashMobs(at, BOSS_CLEAR_RADIUS);
        boolean ok = bosses.spawn(boss.id(), at, player);
        if (!ok) {
            ok = bosses.spawnSandbox(boss.id(), at, player);
        }
        World world = at.getWorld();
        if (ok) {
            player.sendMessage("§cRite §8» §f§c" + boss.display() + " §frises. It can walk. Make it stop.");
            if (world != null) {
                world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.32f, 1.25f);
                world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.15f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 1);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 60, 0.8, 1.0, 0.8, 0.04);
                world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 1);
            }
            // Next tick: latch onto the spawned boss entity and hold the clear bubble.
            plugin.getServer().getScheduler().runTask(plugin, () -> openBossBubble(at));
        } else {
            player.sendMessage("§cRite failed — too many bosses active, or template missing.");
            player.getInventory().addItem(createSpirit(boss));
        }
        ritualBusyUntil.remove(player.getUniqueId());
    }

    private void openBossBubble(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        clearTrashMobs(at, BOSS_CLEAR_RADIUS);
        UUID bossId = findNearbyBossId(at);
        activeBossBubbles.add(new BossBubble(
                at.clone(),
                bossId,
                System.currentTimeMillis() + 20_000L
        ));
    }

    private static UUID findNearbyBossId(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDist = 36.0;
        for (Entity entity : world.getNearbyEntities(at, 6, 6, 6)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            if (!AetherEntities.isBoss(living) && !AetherEntities.isSystemOwned(living)) {
                continue;
            }
            // Prefer actual bosses over minions.
            double dist = living.getLocation().distanceSquared(at);
            if (AetherEntities.isBoss(living)) {
                dist -= 1000;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best == null ? null : best.getUniqueId();
    }

    /** Despawn Borderlands T1–T3 trash (not bosses / pets / T4 crypt). */
    private void clearTrashMobs(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double r2 = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius + 1, radius + 1, radius + 1)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            if (AetherEntities.isSystemOwned(living) || AetherEntities.isBoss(living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(center) > r2) {
                continue;
            }
            byte tier = WildlifeLooks.tierOf(living);
            boolean zoneMob = living.getPersistentDataContainer()
                    .has(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
            // Clear T1/T2/T3 and untagged hostiles in the bubble — leave crypt T4 alone.
            if (tier == WildlifeLooks.TIER_CRYPT) {
                continue;
            }
            if (tier == WildlifeLooks.TIER_NORMAL
                    || tier == WildlifeLooks.TIER_STURDY
                    || tier == WildlifeLooks.TIER_BRUTE
                    || (zoneMob && living instanceof Monster)) {
                living.remove();
            }
        }
    }

    private static final class BossBubble {
        private final Location center;
        private final UUID bossId;
        private long keepUntilMs;

        private BossBubble(Location center, UUID bossId, long keepUntilMs) {
            this.center = center;
            this.bossId = bossId;
            this.keepUntilMs = keepUntilMs;
        }
    }

    public static ItemStack createSpirit(SpiritBoss boss) {
        if (boss == null || !isRiteBoss(boss.id())) {
            return null;
        }
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lBorderlands Spirit §8· §f" + boss.display());
            meta.setLore(List.of(
                    "§7Coagulated from Borderlands hostiles.",
                    "§7Boss: §c" + boss.display(),
                    "",
                    "§eRight-click §7the light-gray powder altar",
                    "§7in the Borderlands to begin the rite.",
                    "§8Not drinkable. Not for sale."
            ));
            meta.setColor(boss.color());
            try {
                meta.setBasePotionType(PotionType.WATER);
            } catch (Throwable ignored) {
            }
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.borderlandsSpirit(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.borderlandsSpiritBoss(),
                    PersistentDataType.STRING,
                    boss.id()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createCryptSpirit(SpiritBoss boss) {
        if (boss == null || !isCryptBoss(boss.id())) {
            return null;
        }
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lCrypt Spirit §8· §f" + boss.display());
            meta.setLore(List.of(
                    "§7Drawn from the Crypt's stronger dead.",
                    "§7Boss: §6" + boss.display(),
                    "",
                    "§eFirst time: §7show the §6Proctor §7at the Colosseum.",
                    "§eAfter: §7right-click the §eglowing mark §7in the ring.",
                    "§8Not drinkable. Not for sale."
            ));
            meta.setColor(boss.color());
            try {
                meta.setBasePotionType(PotionType.WATER);
            } catch (Throwable ignored) {
            }
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.cryptSpirit(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(
                    ItemKeys.borderlandsSpiritBoss(),
                    PersistentDataType.STRING,
                    boss.id()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSpirit(String bossId) {
        SpiritBoss boss = byId(bossId);
        return boss == null ? null : createSpirit(boss);
    }

    public static ItemStack createCryptSpirit(String bossId) {
        SpiritBoss boss = byCryptId(bossId);
        return boss == null ? null : createCryptSpirit(boss);
    }

    public static boolean isSpirit(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.borderlandsSpirit(), PersistentDataType.BYTE)
                && !isCryptSpirit(item);
    }

    public static boolean isCryptSpirit(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.cryptSpirit(), PersistentDataType.BYTE);
    }

    public static boolean playerHoldsCryptSpirit(Player player) {
        if (player == null) {
            return false;
        }
        return isCryptSpirit(player.getInventory().getItemInMainHand())
                || isCryptSpirit(player.getInventory().getItemInOffHand());
    }

    public static ItemStack findCryptSpirit(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isCryptSpirit(main)) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return isCryptSpirit(off) ? off : null;
    }

    public static String spiritBossId(ItemStack item) {
        if (!isSpirit(item) && !isCryptSpirit(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.borderlandsSpiritBoss(),
                PersistentDataType.STRING
        );
    }

    private boolean isAltarBlock(Block block) {
        if (block.getWorld() == null || !ALTAR_WORLD.equalsIgnoreCase(block.getWorld().getName())) {
            return false;
        }
        double dx = (block.getX() + 0.5) - ALTAR_X;
        double dy = (block.getY() + 0.5) - ALTAR_Y;
        double dz = (block.getZ() + 0.5) - ALTAR_Z;
        return dx * dx + dy * dy + dz * dz <= ALTAR_REACH * ALTAR_REACH;
    }

    private static SpiritBoss byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        // Explicit deny — never treat Pathwarden / Squidward as T1 rite vials.
        if (id.equalsIgnoreCase("pathwarden") || id.equalsIgnoreCase("squidward")) {
            return null;
        }
        for (SpiritBoss boss : RITE_POOL) {
            if (boss.id().equalsIgnoreCase(id)) {
                return boss;
            }
        }
        return null;
    }

    private static SpiritBoss byCryptId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (SpiritBoss boss : T2) {
            if (boss.id().equalsIgnoreCase(id)) {
                return boss;
            }
        }
        return null;
    }

    public static boolean isRiteBoss(String id) {
        return byId(id) != null;
    }

    public static boolean isCryptBoss(String id) {
        return byCryptId(id) != null;
    }

    public record SpiritBoss(String id, String display, Color color) {
    }
}
