package de.aetherion.farming;

import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.island.FarmIslandService;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crop-field event: birds circle, land on a crop patch, players shoo them.
 * Same loop grammar as the fishing and fell bars:
 * <ol>
 *   <li><b>Telegraph</b> — "Birds circling…" bar fills while a flock wheels overhead.</li>
 *   <li><b>Interact</b> — click the birds. Bold Crows hop once before they give up.</li>
 *   <li><b>Result</b> — Field clear, or Clean sweep with half the timer left. Fail is readable, never a debuff.</li>
 *   <li><b>Reward</b> — temporary Fortune + Harvest for everyone on the field, Farming XP for helpers,
 *       boost length grows with Farming level.</li>
 * </ol>
 * Harbour farm and (midgame) the Farm Isle both host it; the isle flock is bigger and bolder.
 * Owned by AetherionFarming; Items only supplies StatProvider + skills.
 */
public final class BirdScareEvent implements Listener, StatProvider, Runnable {

    static final String LEASE = "bird-scare";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int SCAN_RADIUS = 18;
    private static final int INCOMING_TICKS = 50;
    private static final int EVENT_TICKS = 20 * 12;
    private static final int ISLE_EVENT_TICKS = 20 * 14;
    /** Ticks with nobody in range before a flock gives up quietly. */
    private static final int EMPTY_FIELD_TICKS = 40;
    private static final int BASE_BOOST_SECONDS = 60;
    private static final int ISLE_BOOST_SECONDS = 75;
    private static final int CLEAN_SWEEP_BONUS_SECONDS = 30;
    /** +5s boost per 20 levels of the player's best Farming skill (up to +25s). */
    private static final int SKILL_BOOST_SECONDS_PER_TIER = 5;
    /** Clear the field with at least this share of the timer left → Clean sweep. */
    private static final double CLEAN_SWEEP_TIME_LEFT = 0.5d;
    private static final double FORTUNE_BONUS = 50.0d;
    private static final double HARVEST_BONUS = 50.0d;
    private static final int MIN_BIRDS = 2;
    private static final int MAX_BIRDS = 4;
    private static final int ISLE_MIN_BIRDS = 3;
    private static final int ISLE_MAX_BIRDS = 5;
    /** Farming XP for anyone who shooed at least one bird. */
    private static final int HELPER_XP = 12;
    private static final int SWEEP_XP = 6;
    private static final int BOLD_HITS = 2;
    /** A bold crow ignores hits for a moment after hopping (also eats off-hand double events). */
    private static final int BOLD_GRACE_TICKS = 6;
    /** Fallback when config has no view-range — bossbar / birds only this close. */
    private static final double DEFAULT_VIEW_RANGE = 25.0d;

    private enum Phase {
        IDLE,
        INCOMING,
        ACTIVE
    }

    private final AetherionFarming plugin;
    private final org.bukkit.NamespacedKey birdKey;
    private final org.bukkit.NamespacedKey boldKey;
    private final Map<UUID, Long> boostUntil = new ConcurrentHashMap<>();
    private final Set<UUID> fadeWarned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> boldGraceUntil = new ConcurrentHashMap<>();
    private final List<UUID> birdIds = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final Set<UUID> helpers = ConcurrentHashMap.newKeySet();
    /** One "equip a Farming skill" nudge per player per boot. */
    private final Set<UUID> tipped = ConcurrentHashMap.newKeySet();

    private Location center;
    private UUID hostId;
    private BukkitTask task;
    private BukkitTask spawnTask;
    private BukkitTask expireTask;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int ticksLeft;
    private int eventTicks;
    private int emptyTicks;
    private int birdsTotal;
    private boolean isle;
    private boolean registered;
    /** Players in view range this tick — computed once, shared by every effect. */
    private List<Player> nearbyNow = List.of();

    BirdScareEvent(AetherionFarming plugin) {
        this.plugin = plugin;
        this.birdKey = new org.bukkit.NamespacedKey(plugin, "scare_bird");
        this.boldKey = new org.bukkit.NamespacedKey(plugin, "scare_bird_bold");
    }

    private double viewRange() {
        return Math.max(8.0d, plugin.getConfig().getDouble("bird-scare.view-range", DEFAULT_VIEW_RANGE));
    }

    /** True when farm-zone is off, or the location is inside the configured farm bubble. */
    private boolean inFarmZone(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("bird-scare.farm-zone.enabled", true)) {
            return true;
        }
        String worldName = plugin.getConfig().getString("bird-scare.farm-zone.world", "world");
        if (!at.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        double fx = plugin.getConfig().getDouble("bird-scare.farm-zone.x", -211.5d);
        double fz = plugin.getConfig().getDouble("bird-scare.farm-zone.z", 183.5d);
        double radius = Math.max(16.0d, plugin.getConfig().getDouble("bird-scare.farm-zone.radius", 90.0d));
        double dx = at.getX() - fx;
        double dz = at.getZ() - fz;
        return (dx * dx + dz * dz) <= radius * radius;
    }

    /** The shared Farm Isle (midgame, Farming 10 portal) hosts the bolder flock. */
    private boolean isIsle(World world) {
        if (world == null || !plugin.getConfig().getBoolean("bird-scare.farm-island.enabled", true)) {
            return false;
        }
        String isleWorld = plugin.getConfig().getString("farm-island.world", FarmIslandService.DEFAULT_WORLD);
        return world.getName().equalsIgnoreCase(isleWorld);
    }

    void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            ActiveEquipmentStats.registerProvider(this);
            registered = true;
        }
        cancel(spawnTask);
        cancel(expireTask);
        long interval = Math.max(20L * 60L, plugin.getConfig().getLong("bird-scare.interval-ticks", 20L * 120L));
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::trySpawn, 20L * 30L, interval);
        expireTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireBoosts, 20L, 20L);
    }

    void shutdown() {
        endEvent(false, true);
        cancel(spawnTask);
        cancel(expireTask);
        spawnTask = null;
        expireTask = null;
        boostUntil.clear();
        fadeWarned.clear();
        if (registered) {
            HandlerList.unregisterAll(this);
            ActiveEquipmentStats.unregisterProvider(this);
            registered = false;
        }
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 0.0d;
        }
        Long until = boostUntil.get(player.getUniqueId());
        if (until == null || until <= System.currentTimeMillis()) {
            return 0.0d;
        }
        if (capability == ItemCapability.FORTUNE) {
            return FORTUNE_BONUS;
        }
        if (capability == ItemCapability.HARVEST_SPREAD) {
            return HARVEST_BONUS;
        }
        return 0.0d;
    }

    @Override
    public void run() {
        if (phase == Phase.IDLE) {
            return;
        }
        nearbyNow = nearbyPlayers(center, viewRange());
        if (nearbyNow.isEmpty()) {
            if (++emptyTicks >= EMPTY_FIELD_TICKS) {
                endEvent(false, true);
                return;
            }
        } else {
            emptyTicks = 0;
        }
        if (phase == Phase.INCOMING) {
            phaseTicks++;
            circlingFx();
            if (phaseTicks % 2 == 0) {
                updateBars();
            }
            if (phaseTicks >= INCOMING_TICKS) {
                land();
            }
            return;
        }
        ticksLeft--;
        pruneBirds();
        if (ticksLeft % 10 == 0) {
            syncBirdVisibility();
        }
        pulseParticles();
        if (ticksLeft % 2 == 0) {
            updateBars();
        }
        flapBirds();
        peck();
        if (birdIds.isEmpty()) {
            succeed();
            return;
        }
        if (ticksLeft <= 0) {
            endEvent(false, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent event) {
        if (!isBird(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        // Right-click fires once per hand — only count the main hand.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        shoo(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!isBird(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        Player player = attacker(event.getDamager());
        if (player != null) {
            shoo(player, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isBird(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isBird(event.getEntity()) || isBird(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boostUntil.remove(id);
        fadeWarned.remove(id);
        hideBar(id);
        helpers.remove(id);
        if (id.equals(hostId)) {
            // The field keeps its flock for whoever is still there; empty fields end on their own.
            hostId = null;
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hideBar(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ lifecycle

    private void trySpawn() {
        if (phase != Phase.IDLE || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            if (Crops.isDungeonWorld(player.getWorld())) {
                continue;
            }
            boolean onIsle = isIsle(player.getWorld());
            if (!onIsle && !inFarmZone(player.getLocation())) {
                continue;
            }
            Location crop = nearCrops(player.getLocation(), SCAN_RADIUS);
            if (crop == null || (!onIsle && !inFarmZone(crop))) {
                continue;
            }
            candidates.add(new Candidate(player, crop, onIsle));
        }
        if (candidates.isEmpty()) {
            return;
        }
        Candidate pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        begin(pick.player(), pick.crop(), pick.isle());
    }

    private record Candidate(Player player, Location crop, boolean isle) {
    }

    /** Telegraph: the flock circles first. Birds land when the bar fills. */
    private void begin(Player host, Location crop, boolean onIsle) {
        phase = Phase.INCOMING;
        phaseTicks = 0;
        emptyTicks = 0;
        isle = onIsle;
        hostId = host.getUniqueId();
        center = crop.clone().add(0.5, 0.15, 0.5);
        eventTicks = isle ? ISLE_EVENT_TICKS : EVENT_TICKS;
        ticksLeft = eventTicks;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        birdsTotal = isle
                ? ISLE_MIN_BIRDS + rng.nextInt(ISLE_MAX_BIRDS - ISLE_MIN_BIRDS + 1)
                : MIN_BIRDS + rng.nextInt(MAX_BIRDS - MIN_BIRDS + 1);
        birdIds.clear();
        viewers.clear();
        helpers.clear();
        boldGraceUntil.clear();
        nearbyNow = nearbyPlayers(center, viewRange());
        for (Player nearby : nearbyNow) {
            viewers.add(nearby.getUniqueId());
            nearby.sendActionBar(LEGACY.deserialize("§eBirds circling the " + fieldWord() + "…"));
            nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.7f, 0.8f);
        }
        cancel(task);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        updateBars();
    }

    private void land() {
        phase = Phase.ACTIVE;
        int bold = isle ? (birdsTotal >= 5 ? 2 : 1) : (birdsTotal >= 4 ? 1 : 0);
        for (int i = 0; i < birdsTotal; i++) {
            spawnBird(i, i < bold);
        }
        String hint = bold > 0 ? " §8· §7Bold crows take two." : "";
        for (Player nearby : nearbyNow) {
            viewers.add(nearby.getUniqueId());
            nearby.sendActionBar(LEGACY.deserialize("§aBirds on the crops. §fClick them." + hint));
            nearby.playSound(center, Sound.ENTITY_PARROT_AMBIENT, 0.7f, 1.2f);
            nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.6f, 1.3f);
            nearby.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.9, 0), 14, 1.0, 0.3, 1.0, 0.02);
        }
        updateBars();
    }

    private void spawnBird(int index, boolean bold) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = (Math.PI * 2.0d * index) / Math.max(1, birdsTotal) + rng.nextDouble() * 0.4d;
        double radius = 0.7d + rng.nextDouble() * 1.1d;
        Location at = center.clone().add(Math.cos(angle) * radius, 0.55d + rng.nextDouble() * 0.35d, Math.sin(angle) * radius);
        Class<? extends LivingEntity> type = bold || rng.nextBoolean() ? Parrot.class : Chicken.class;
        LivingEntity bird = center.getWorld().spawn(at, type, spawned -> {
            spawned.setPersistent(false);
            spawned.setSilent(true);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setGravity(false);
            spawned.setAI(false);
            spawned.setRemoveWhenFarAway(true);
            spawned.setCanPickupItems(false);
            spawned.getPersistentDataContainer().set(birdKey, PersistentDataType.BYTE, (byte) 1);
            if (bold) {
                if (spawned instanceof Parrot parrot) {
                    parrot.setVariant(Parrot.Variant.GRAY);
                }
                spawned.customName(Component.text("Bold Crow", NamedTextColor.DARK_GRAY));
                spawned.setCustomNameVisible(true);
                spawned.getPersistentDataContainer().set(boldKey, PersistentDataType.INTEGER, BOLD_HITS);
            } else {
                spawned.customName(Component.text("Crop Pest", NamedTextColor.GRAY));
                spawned.setCustomNameVisible(false);
            }
        });
        birdIds.add(bird.getUniqueId());
        // Hidden by default — only players within view range see the event.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, bird);
        }
        for (Player nearby : nearbyNow) {
            nearby.showEntity(plugin, bird);
        }
    }

    private void shoo(Player player, Entity entity) {
        if (phase != Phase.ACTIVE || entity == null || !birdIds.contains(entity.getUniqueId())) {
            return;
        }
        helpers.add(player.getUniqueId());
        int now = Bukkit.getCurrentTick();
        Integer grace = boldGraceUntil.get(entity.getUniqueId());
        if (grace != null && now < grace) {
            return;
        }
        Integer hits = entity.getPersistentDataContainer().get(boldKey, PersistentDataType.INTEGER);
        if (hits != null && hits > 1) {
            hopBold(player, entity, hits - 1, now);
            return;
        }
        birdIds.remove(entity.getUniqueId());
        boldGraceUntil.remove(entity.getUniqueId());
        Location at = entity.getLocation();
        entity.remove();
        for (Player nearby : nearbyNow) {
            nearby.spawnParticle(Particle.CLOUD, at, 8, 0.15, 0.15, 0.15, 0.02);
            nearby.playSound(at, Sound.ENTITY_PARROT_FLY, 0.55f, 1.35f);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.6f);
        int left = birdIds.size();
        if (left > 0) {
            player.sendActionBar(LEGACY.deserialize("§eShooed. §f" + left + " §7left."));
        }
        updateBars();
        if (birdIds.isEmpty()) {
            succeed();
        }
    }

    /** Bold crow: first shoo only moves it. The new beat — a tiny chase, not a fight. */
    private void hopBold(Player player, Entity crow, int hitsLeft, int now) {
        crow.getPersistentDataContainer().set(boldKey, PersistentDataType.INTEGER, hitsLeft);
        boldGraceUntil.put(crow.getUniqueId(), now + BOLD_GRACE_TICKS);
        Location from = crow.getLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble() * Math.PI * 2.0d;
        double radius = 1.4d + rng.nextDouble() * 1.2d;
        Location hop = center.clone().add(Math.cos(angle) * radius, 0.6d + rng.nextDouble() * 0.3d, Math.sin(angle) * radius);
        hop.setYaw(from.getYaw() + 180f);
        crow.teleport(hop);
        for (Player nearby : nearbyNow) {
            nearby.spawnParticle(Particle.CLOUD, from, 6, 0.12, 0.12, 0.12, 0.02);
            nearby.playSound(from, Sound.ENTITY_PARROT_AMBIENT, 0.6f, 0.6f);
        }
        player.sendActionBar(LEGACY.deserialize("§7The crow disagrees. §8Once more."));
        updateBars();
    }

    private void succeed() {
        if (phase != Phase.ACTIVE) {
            return;
        }
        boolean sweep = ticksLeft >= eventTicks * CLEAN_SWEEP_TIME_LEFT;
        long nowMs = System.currentTimeMillis();
        Set<UUID> rewarded = new HashSet<>(helpers);
        for (Player nearby : nearbyNow) {
            rewarded.add(nearby.getUniqueId());
        }
        Location field = center;
        for (UUID id : rewarded) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            int seconds = (isle ? ISLE_BOOST_SECONDS : BASE_BOOST_SECONDS)
                    + (sweep ? CLEAN_SWEEP_BONUS_SECONDS : 0)
                    + FarmingSkills.boostTier(player) * SKILL_BOOST_SECONDS_PER_TIER;
            boostUntil.put(id, nowMs + seconds * 1000L);
            fadeWarned.remove(id);
            grantBoostFeedback(player, field, seconds, sweep, helpers.contains(id));
        }
        if (field != null && field.getWorld() != null) {
            for (Player nearby : nearbyNow) {
                nearby.spawnParticle(Particle.HAPPY_VILLAGER, field.clone().add(0, 0.6, 0), 28, 0.9, 0.45, 0.9, 0.02);
                nearby.spawnParticle(Particle.FIREWORK, field.clone().add(0, 0.8, 0), sweep ? 26 : 18, 0.55, 0.35, 0.55, 0.02);
                nearby.playSound(field, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, sweep ? 1.6f : 1.35f);
            }
        }
        endEvent(true, false);
    }

    private void grantBoostFeedback(Player player, Location field, int seconds, boolean sweep, boolean helper) {
        String boost = "+" + (int) FORTUNE_BONUS + " Fortune · +" + (int) HARVEST_BONUS + " Harvest";
        player.sendMessage((sweep ? "§6Clean sweep. " : "§aField clear. ")
                + "§7" + boost + " for §f" + seconds + "s§7."
                + (sweep ? " §8(+" + CLEAN_SWEEP_BONUS_SECONDS + "s for speed)" : ""));
        player.showTitle(net.kyori.adventure.title.Title.title(
                sweep ? Component.text("Clean sweep", NamedTextColor.GOLD) : Component.text("Field clear", NamedTextColor.GREEN),
                LEGACY.deserialize("§6" + boost + " §8· §f" + seconds + "s"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(80),
                        java.time.Duration.ofMillis(1400),
                        java.time.Duration.ofMillis(220)
                )
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.65f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.4f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 14, 0.35, 0.45, 0.35, 0.02);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.1, 0), 8, 0.25, 0.35, 0.25, 0.01);
        if (helper) {
            FarmingSkills.bonus(player, HELPER_XP + (sweep ? SWEEP_XP : 0));
        }
        if (field != null && field.getWorld() != null && player.getWorld().equals(field.getWorld())) {
            player.playSound(field, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.35f, 1.5f);
        }
        UUID id = player.getUniqueId();
        // Reward line after the title clears: boost + the helper's Farming bar.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline() || !boostUntil.containsKey(id)) {
                return;
            }
            StringBuilder line = new StringBuilder("§6" + boost + " §7(" + seconds + "s)");
            String credit = helper ? FarmingSkills.credit(online) : null;
            if (credit != null) {
                line.append("  §8│  ").append(credit);
            } else if (helper && tipped.add(id)) {
                online.sendMessage("§8Tip: equip a Farming skill in §7/skills farming §8— it levels on every harvest,"
                        + " and cleared fields pay extra.");
            }
            online.sendActionBar(LEGACY.deserialize(line.toString()));
        }, 30L);
    }

    private void endEvent(boolean success, boolean quiet) {
        Phase was = phase;
        phase = Phase.IDLE;
        cancel(task);
        task = null;
        int left = birdIds.size();
        List<Location> leaving = new ArrayList<>();
        for (UUID id : List.copyOf(birdIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                leaving.add(entity.getLocation());
                entity.remove();
            }
        }
        birdIds.clear();
        boldGraceUntil.clear();
        for (UUID id : List.copyOf(bars.keySet())) {
            hideBar(id);
        }
        if (!success && !quiet && was == Phase.ACTIVE && center != null) {
            for (Player nearby : nearbyPlayers(center, viewRange())) {
                nearby.sendActionBar(LEGACY.deserialize("§7The birds left §8· §f" + left
                        + " §7still pecking on the way out. Crops are fine."));
                nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.5f, 0.7f);
                for (Location at : leaving) {
                    nearby.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 4, 0.1, 0.3, 0.1, 0.03);
                }
            }
        }
        viewers.clear();
        helpers.clear();
        nearbyNow = List.of();
        center = null;
        hostId = null;
        ticksLeft = 0;
        phaseTicks = 0;
        emptyTicks = 0;
        birdsTotal = 0;
        isle = false;
    }

    // ------------------------------------------------------------------ ambience

    /** Telegraph flock: a slow wheel of wisps above the field, wing beats every half second. */
    private void circlingFx() {
        if (center == null || center.getWorld() == null || phaseTicks % 3 != 0) {
            return;
        }
        double spin = phaseTicks * 0.18d;
        double height = 5.5d - (phaseTicks / (double) INCOMING_TICKS) * 3.5d;
        for (Player nearby : nearbyNow) {
            for (int i = 0; i < 3; i++) {
                double angle = spin + (Math.PI * 2.0d * i) / 3.0d;
                Location wisp = center.clone().add(Math.cos(angle) * 2.4d, height, Math.sin(angle) * 2.4d);
                nearby.spawnParticle(Particle.CLOUD, wisp, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (phaseTicks % 12 == 0) {
                nearby.playSound(center.clone().add(0, height, 0), Sound.ENTITY_PARROT_FLY, 0.45f, 0.9f);
            }
        }
    }

    private void pruneBirds() {
        Iterator<UUID> it = birdIds.iterator();
        while (it.hasNext()) {
            Entity entity = Bukkit.getEntity(it.next());
            if (entity == null || !entity.isValid()) {
                it.remove();
            }
        }
    }

    private void pulseParticles() {
        if (center == null || center.getWorld() == null || ticksLeft % 4 != 0) {
            return;
        }
        Location at = center.clone().add(0, 0.4, 0);
        for (Player nearby : nearbyNow) {
            nearby.spawnParticle(Particle.CRIT, at, 6, 0.9, 0.25, 0.9, 0.01);
            nearby.spawnParticle(Particle.HAPPY_VILLAGER, center, 3, 0.7, 0.2, 0.7, 0.0);
        }
    }

    /** Readable threat without a debuff: a bird pecks, a few crop crumbs fly. */
    private void peck() {
        if (ticksLeft % 30 != 0 || birdIds.isEmpty()) {
            return;
        }
        Entity bird = Bukkit.getEntity(birdIds.get(ThreadLocalRandom.current().nextInt(birdIds.size())));
        if (bird == null || !bird.isValid()) {
            return;
        }
        Location at = bird.getLocation();
        Block below = at.clone().add(0, -0.6, 0).getBlock();
        Block crop = Crops.isCrop(below.getType()) ? below : center.getBlock();
        for (Player nearby : nearbyNow) {
            nearby.spawnParticle(Particle.BLOCK, at.clone().add(0, -0.3, 0), 5, 0.08, 0.05, 0.08, 0.0,
                    crop.getBlockData());
            nearby.playSound(at, Sound.BLOCK_CROP_BREAK, 0.25f, 1.4f);
        }
    }

    private void syncBirdVisibility() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        for (Player player : center.getWorld().getPlayers()) {
            boolean near = withinViewRange(player, center);
            for (UUID id : birdIds) {
                Entity bird = Bukkit.getEntity(id);
                if (bird == null || !bird.isValid()) {
                    continue;
                }
                if (near) {
                    player.showEntity(plugin, bird);
                } else {
                    player.hideEntity(plugin, bird);
                }
            }
        }
    }

    private void flapBirds() {
        if (ticksLeft % 5 != 0) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (UUID id : birdIds) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity bird) || !bird.isValid()) {
                continue;
            }
            Vector bob = new Vector(
                    (rng.nextDouble() - 0.5d) * 0.08d,
                    Math.sin(ticksLeft * 0.25d + id.hashCode()) * 0.05d,
                    (rng.nextDouble() - 0.5d) * 0.08d
            );
            bird.setVelocity(bob);
            bird.setRotation(bird.getYaw() + (rng.nextFloat() - 0.5f) * 18f, -8f);
        }
    }

    // ------------------------------------------------------------------ HUD

    private void updateBars() {
        if (phase == Phase.IDLE || center == null) {
            for (UUID id : List.copyOf(bars.keySet())) {
                hideBar(id);
            }
            return;
        }
        // Drop anyone who walked away — Adventure bars are per-audience only.
        for (UUID id : List.copyOf(bars.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !withinViewRange(player, center)) {
                hideBar(id);
            }
        }
        String label = isle ? "§6Farm Isle" : "§6Field";
        if (phase == Phase.INCOMING) {
            float fill = (float) Math.min(1.0d, phaseTicks / (double) INCOMING_TICKS);
            for (Player nearby : nearbyNow) {
                paint(nearby, label + " §8• §eBirds circling…", fill, BossBar.Color.YELLOW);
            }
            return;
        }
        double progress = Math.max(0.0d, Math.min(1.0d, ticksLeft / (double) Math.max(1, eventTicks)));
        int left = birdIds.size();
        int bold = boldLeft();
        Location aim = nearestBirdLocation();
        BossBar.Color color = ticksLeft < 80 ? BossBar.Color.RED : BossBar.Color.YELLOW;
        for (Player nearby : nearbyNow) {
            if (viewers.add(nearby.getUniqueId())) {
                nearby.sendActionBar(LEGACY.deserialize("§aBirds on the crops. §fClick them."));
            }
            String arrow = directionArrow(nearby.getLocation(), aim != null ? aim : center);
            String title = label + " §8• §f" + arrow + " §eShoo the birds §8• §f" + left
                    + "§7/§f" + Math.max(birdsTotal, left)
                    + (bold > 0 ? " §8· §7" + bold + " bold" : "");
            paint(nearby, title, (float) progress, color);
        }
    }

    private void paint(Player player, String title, float progress, BossBar.Color color) {
        if (player == null || center == null || !withinViewRange(player, center)) {
            if (player != null) {
                hideBar(player.getUniqueId());
            }
            return;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        Component name = LEGACY.deserialize(title);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, clamped, color, BossBar.Overlay.NOTCHED_20);
            bars.put(player.getUniqueId(), bar);
            QuestBars.suppress(player, LEASE);
            player.showBossBar(bar);
            return;
        }
        bar.name(name);
        bar.progress(clamped);
        bar.color(color);
    }

    private void hideBar(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BossBar bar = bars.remove(playerId);
        if (bar != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.hideBossBar(bar);
            }
        }
        QuestBars.release(playerId, LEASE);
        viewers.remove(playerId);
    }

    private int boldLeft() {
        int bold = 0;
        for (UUID id : birdIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.getPersistentDataContainer().has(boldKey, PersistentDataType.INTEGER)) {
                Integer hits = entity.getPersistentDataContainer().get(boldKey, PersistentDataType.INTEGER);
                if (hits != null && hits > 1) {
                    bold++;
                }
            }
        }
        return bold;
    }

    private Location nearestBirdLocation() {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        if (center == null) {
            return null;
        }
        for (UUID id : birdIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid()) {
                continue;
            }
            double dist = entity.getLocation().distanceSquared(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity.getLocation();
            }
        }
        return best;
    }

    private static String directionArrow(Location from, Location target) {
        if (from == null || target == null) {
            return "◆";
        }
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 2.5d) {
            return "●";
        }
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = targetYaw - from.getYaw();
        while (diff < -180.0d) {
            diff += 360.0d;
        }
        while (diff > 180.0d) {
            diff -= 360.0d;
        }
        int index = (int) Math.round(diff / 45.0d);
        if (index < 0) {
            index += 8;
        }
        if (index >= 8) {
            index = 0;
        }
        return ARROWS[index];
    }

    /** Boost upkeep: one heads-up at 10s, one line when it fades. */
    private void expireBoosts() {
        long now = System.currentTimeMillis();
        boostUntil.entrySet().removeIf(entry -> {
            UUID id = entry.getKey();
            Player player = Bukkit.getPlayer(id);
            long left = entry.getValue() - now;
            if (left > 0) {
                if (left <= 10_000L && fadeWarned.add(id) && player != null && player.isOnline()) {
                    player.sendActionBar(LEGACY.deserialize("§6Field boost §8· §710s left"));
                }
                return false;
            }
            fadeWarned.remove(id);
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text("Field boost faded.", NamedTextColor.GRAY));
            }
            return true;
        });
    }

    private String fieldWord() {
        return isle ? "isle" : "field";
    }

    private boolean isBird(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(birdKey, PersistentDataType.BYTE);
    }

    private static void cancel(BukkitTask handle) {
        if (handle != null) {
            handle.cancel();
        }
    }

    private static boolean sameWorld(Player player, Location at) {
        return player != null && at != null && at.getWorld() != null && player.getWorld().equals(at.getWorld());
    }

    /** Same world, not spectator, within view range on XZ (ignores height). */
    private boolean withinViewRange(Player player, Location at) {
        if (!sameWorld(player, at) || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        double range = viewRange();
        double dx = player.getLocation().getX() - at.getX();
        double dz = player.getLocation().getZ() - at.getZ();
        return (dx * dx + dz * dz) <= range * range;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static Location nearCrops(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        List<Location> found = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    var block = world.getBlockAt(ox + x, oy + y, oz + z);
                    if (Crops.isCrop(block.getType()) && Crops.isMature(block)) {
                        found.add(block.getLocation());
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.get(ThreadLocalRandom.current().nextInt(found.size()));
    }

    private static List<Player> nearbyPlayers(Location at, double range) {
        List<Player> out = new ArrayList<>();
        if (at == null || at.getWorld() == null) {
            return out;
        }
        double r2 = range * range;
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dx = player.getLocation().getX() - at.getX();
            double dz = player.getLocation().getZ() - at.getZ();
            if ((dx * dx + dz * dz) <= r2) {
                out.add(player);
            }
        }
        return out;
    }
}
