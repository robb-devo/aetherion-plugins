package de.aetherion.farming;

import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.dev.ScarecrowProp;
import de.aetherion.farming.island.FarmIsleZones;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
 * Scarecrow minigame — birds land around a placed {@link ScarecrowProp} on the
 * Farm Isle. Click them to shoo. Clearing the wave grants the same Golden Hour
 * boost the crop-field bird scare uses (own StatProvider, same numbers).
 *
 * <p>Same loop grammar as the bird scare, fishing and fell bars:
 * <ol>
 *   <li><b>Telegraph</b> — the flock wheels over the scarecrow while the bar fills.</li>
 *   <li><b>Interact</b> — click the birds; the bar points at the nearest one.</li>
 *   <li><b>Result</b> — Golden Hour, or a readable "they left" (never a debuff).</li>
 *   <li><b>Reward</b> — boost for everyone around, a little Farming XP for helpers.</li>
 * </ol>
 *
 * Solo waves are small; every extra player nearby adds birds, and everyone who
 * was around on success shares the boost.
 */
public final class ScarecrowEvent implements Listener, StatProvider, Runnable {

    static final String LEASE = "scarecrow";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int BOOST_TICKS = 20 * 60;
    private static final double FORTUNE_BONUS = 50.0d;
    private static final double HARVEST_BONUS = 50.0d;
    /** Telegraph length — a touch shorter than the field flock; the scarecrow is a known spot. */
    private static final int INCOMING_TICKS = 40;
    /** Ticks with nobody in range before a wave gives up quietly. */
    private static final int EMPTY_FIELD_TICKS = 40;
    /** Last stretch of the timer: bar turns red, one "hurry" cue. */
    private static final int HURRY_TICKS = 80;
    /** Farming XP for anyone who shooed at least one bird (field flock pays 12). */
    private static final int HELPER_XP = 8;

    private enum Phase {
        IDLE,
        INCOMING,
        ACTIVE
    }

    private final AetherionFarming plugin;
    private final org.bukkit.NamespacedKey birdKey;
    private final Map<UUID, Long> boostUntil = new ConcurrentHashMap<>();
    private final Set<UUID> fadeWarned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final List<UUID> birdIds = new ArrayList<>();
    private final Set<UUID> helpers = ConcurrentHashMap.newKeySet();
    /** One "equip a Farming skill" nudge per player per boot. */
    private final Set<UUID> tipped = ConcurrentHashMap.newKeySet();

    private Location center;
    private BukkitTask task;
    private BukkitTask spawnTask;
    private BukkitTask expireTask;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int ticksLeft;
    private int birdsTotal;
    private int eventTicks;
    private int emptyTicks;
    private boolean registered;
    /** Players in view range this tick — computed once, shared by every effect. */
    private List<Player> nearbyNow = List.of();

    public ScarecrowEvent(AetherionFarming plugin) {
        this.plugin = plugin;
        this.birdKey = new org.bukkit.NamespacedKey(plugin, "scarecrow_bird");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scarecrow.enabled", true)) {
            return;
        }
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            ActiveEquipmentStats.registerProvider(this);
            registered = true;
        }
        cancel(spawnTask);
        cancel(expireTask);
        long interval = Math.max(20L * 60L, plugin.getConfig().getLong("scarecrow.interval-ticks", 20L * 300L));
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::trySpawn, 20L * 45L, interval);
        expireTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireBoosts, 20L, 20L);
    }

    public void shutdown() {
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
        if (ticksLeft == HURRY_TICKS) {
            for (Player nearby : nearbyNow) {
                nearby.playSound(nearby.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 0.7f);
            }
        }
        pruneBirds();
        flapBirds();
        if (ticksLeft % 2 == 0) {
            updateBars();
        }
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
        helpers.remove(id);
        hideBar(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hideBar(event.getPlayer().getUniqueId());
    }

    private double viewRange() {
        return Math.max(8.0d, plugin.getConfig().getDouble("scarecrow.view-range", 24.0d));
    }

    private double radius() {
        return Math.max(2.0d, plugin.getConfig().getDouble("scarecrow.radius", 8.0d));
    }

    // ------------------------------------------------------------------ lifecycle

    private void trySpawn() {
        ScarecrowProp prop = plugin.scarecrow();
        if (phase != Phase.IDLE || prop == null || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        List<Location> candidates = new ArrayList<>();
        for (Location anchor : prop.anchors()) {
            if (!FarmIsleZones.inFarmIsleFootprint(plugin, anchor)) {
                continue;
            }
            if (!nearbyPlayers(anchor, viewRange()).isEmpty()) {
                candidates.add(anchor);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        begin(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    /** DEV: start a wave at the nearest placed scarecrow (within 64 blocks), ignoring the timer. */
    public String devStart(Player player) {
        ScarecrowProp prop = plugin.scarecrow();
        if (prop == null) {
            return "§cNo scarecrow prop service.";
        }
        if (phase != Phase.IDLE) {
            endEvent(false, true);
        }
        Location best = null;
        double bestDistance = 64.0d * 64.0d;
        for (Location anchor : prop.anchors()) {
            if (anchor.getWorld() == null || !anchor.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = anchor.distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = anchor;
            }
        }
        if (best == null) {
            return "§cNo scarecrow within 64 blocks. Place one from DEV → Farming Island → Props.";
        }
        begin(best);
        if (phase == Phase.IDLE) {
            return "§cNobody in survival near that scarecrow.";
        }
        return "§aScarecrow wave incoming §7(" + birdsTotal + " birds).";
    }

    /** Telegraph: the flock wheels over the scarecrow first. Birds land when the bar fills. */
    private void begin(Location anchor) {
        List<Player> watching = nearbyPlayers(anchor, viewRange());
        if (watching.isEmpty()) {
            return;
        }
        phase = Phase.INCOMING;
        phaseTicks = 0;
        emptyTicks = 0;
        center = anchor.clone().add(0, 1.0, 0);
        eventTicks = Math.max(20 * 5, plugin.getConfig().getInt("scarecrow.duration-ticks", 20 * 20));
        ticksLeft = eventTicks;
        int base = Math.max(1, plugin.getConfig().getInt("scarecrow.birds.base", 3));
        int perExtra = Math.max(0, plugin.getConfig().getInt("scarecrow.birds.per-extra-player", 2));
        int cap = Math.max(base, plugin.getConfig().getInt("scarecrow.birds.max", 12));
        birdsTotal = Math.min(cap, base + perExtra * Math.max(0, watching.size() - 1));
        birdIds.clear();
        helpers.clear();
        nearbyNow = watching;
        for (Player nearby : watching) {
            nearby.sendActionBar(LEGACY.deserialize("§eBirds circling the scarecrow…"));
            nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.7f, 0.8f);
        }
        cancel(task);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        updateBars();
    }

    private void land() {
        phase = Phase.ACTIVE;
        for (int i = 0; i < birdsTotal; i++) {
            spawnBird(i);
        }
        for (Player nearby : nearbyNow) {
            nearby.sendActionBar(LEGACY.deserialize("§aBirds on the scarecrow. §fClick them."));
            nearby.playSound(center, Sound.ENTITY_PARROT_AMBIENT, 0.7f, 1.2f);
            nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.6f, 1.3f);
            nearby.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.6, 0), 12, 1.2, 0.4, 1.2, 0.02);
        }
        updateBars();
    }

    private void spawnBird(int index) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = (Math.PI * 2.0d * index) / Math.max(1, birdsTotal) + rng.nextDouble() * 0.5d;
        double spread = 1.5d + rng.nextDouble() * (radius() - 1.5d);
        Location at = center.clone().add(
                Math.cos(angle) * spread,
                rng.nextDouble() * 1.2d - 0.4d,
                Math.sin(angle) * spread
        );
        EntityType type = rng.nextBoolean() ? EntityType.CHICKEN : EntityType.PARROT;
        LivingEntity bird = (LivingEntity) center.getWorld().spawnEntity(at, type);
        bird.setPersistent(false);
        bird.setSilent(true);
        bird.setInvulnerable(true);
        bird.setCollidable(false);
        bird.setGravity(false);
        bird.setAI(false);
        bird.setRemoveWhenFarAway(true);
        bird.setCanPickupItems(false);
        bird.customName(Component.text("Crop Pest", NamedTextColor.GRAY));
        bird.setCustomNameVisible(false);
        bird.getPersistentDataContainer().set(birdKey, PersistentDataType.BYTE, (byte) 1);
        birdIds.add(bird.getUniqueId());
    }

    private void shoo(Player player, Entity entity) {
        if (phase != Phase.ACTIVE || entity == null || !birdIds.remove(entity.getUniqueId())) {
            return;
        }
        helpers.add(player.getUniqueId());
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

    private void succeed() {
        if (phase != Phase.ACTIVE) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> rewarded = new HashSet<>(helpers);
        for (Player nearby : nearbyNow) {
            rewarded.add(nearby.getUniqueId());
        }
        Location pulse = center;
        for (UUID id : rewarded) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            int seconds = BOOST_TICKS / 20 + BirdScareEvent.birdLawSeconds(player);
            boostUntil.put(id, now + seconds * 1000L);
            fadeWarned.remove(id);
            grantBoostFeedback(player, seconds, helpers.contains(id));
        }
        if (pulse != null && pulse.getWorld() != null) {
            for (Player nearby : nearbyNow) {
                nearby.spawnParticle(Particle.HAPPY_VILLAGER, pulse, 28, 0.9, 0.6, 0.9, 0.02);
                nearby.spawnParticle(Particle.FIREWORK, pulse.clone().add(0, 0.4, 0), 16, 0.55, 0.35, 0.55, 0.02);
                nearby.playSound(pulse, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.35f);
            }
        }
        endEvent(true, false);
    }

    private void grantBoostFeedback(Player player, int seconds, boolean helper) {
        String boost = "+" + (int) FORTUNE_BONUS + " Fortune · +" + (int) HARVEST_BONUS + " Harvest";
        player.sendMessage("§6Golden Hour! §7" + boost + " for §f" + seconds + "s§7.");
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Golden Hour", NamedTextColor.GOLD),
                LEGACY.deserialize("§e" + boost + " §8· §f" + seconds + "s"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(80),
                        java.time.Duration.ofMillis(1600),
                        java.time.Duration.ofMillis(220)
                )
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.65f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.4f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 14, 0.35, 0.45, 0.35, 0.02);
        if (helper) {
            FarmingSkills.bonus(player, HELPER_XP);
        }
        UUID id = player.getUniqueId();
        // Reward line after the title clears: boost + the helper's Farming bar (same beat as the field flock).
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
        for (UUID id : List.copyOf(bars.keySet())) {
            hideBar(id);
        }
        if (!success && !quiet && was == Phase.ACTIVE && center != null) {
            for (Player nearby : nearbyPlayers(center, viewRange())) {
                nearby.sendActionBar(LEGACY.deserialize("§7The birds left §8· §f" + left
                        + " §7still on the scarecrow. Crops are fine."));
                nearby.playSound(center, Sound.ENTITY_PARROT_FLY, 0.5f, 0.7f);
                for (Location at : leaving) {
                    nearby.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 4, 0.1, 0.3, 0.1, 0.03);
                }
            }
        }
        helpers.clear();
        nearbyNow = List.of();
        center = null;
        ticksLeft = 0;
        phaseTicks = 0;
        emptyTicks = 0;
        birdsTotal = 0;
    }

    // ------------------------------------------------------------------ ambience

    /** Telegraph flock: a slow wheel of wisps above the scarecrow, wing beats every half second. */
    private void circlingFx() {
        if (center == null || center.getWorld() == null || phaseTicks % 3 != 0) {
            return;
        }
        double spin = phaseTicks * 0.2d;
        double height = 4.5d - (phaseTicks / (double) INCOMING_TICKS) * 3.0d;
        for (Player nearby : nearbyNow) {
            for (int i = 0; i < 3; i++) {
                double angle = spin + (Math.PI * 2.0d * i) / 3.0d;
                Location wisp = center.clone().add(Math.cos(angle) * 2.2d, height, Math.sin(angle) * 2.2d);
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
            bird.setVelocity(new Vector(
                    (rng.nextDouble() - 0.5d) * 0.08d,
                    Math.sin(ticksLeft * 0.25d + id.hashCode()) * 0.05d,
                    (rng.nextDouble() - 0.5d) * 0.08d
            ));
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
        for (UUID id : List.copyOf(bars.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !withinViewRange(player, center)) {
                hideBar(id);
            }
        }
        String label = "§6Scarecrow";
        if (phase == Phase.INCOMING) {
            float fill = (float) Math.min(1.0d, phaseTicks / (double) INCOMING_TICKS);
            for (Player nearby : nearbyNow) {
                paint(nearby, label + " §8• §eBirds circling…", fill, BossBar.Color.YELLOW);
            }
            return;
        }
        double progress = Math.max(0.0d, Math.min(1.0d, ticksLeft / (double) Math.max(1, eventTicks)));
        int left = birdIds.size();
        Location aim = nearestBirdLocation();
        BossBar.Color color = ticksLeft < HURRY_TICKS ? BossBar.Color.RED : BossBar.Color.YELLOW;
        for (Player nearby : nearbyNow) {
            String arrow = directionArrow(nearby.getLocation(), aim != null ? aim : center);
            String title = label + " §8• §f" + arrow + " §eShoo the birds §8• §f" + left
                    + "§7/§f" + Math.max(birdsTotal, left)
                    + (ticksLeft < HURRY_TICKS ? " §8· §churry" : "");
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
        Component name = LEGACY.deserialize(BarChrome.legacy(BarChrome.Family.EVENT, BarChrome.Icon.BIRD, title));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, clamped, color, BossBar.Overlay.NOTCHED_20);
            bars.put(player.getUniqueId(), bar);
            // Named lease: never hands the quest bar back over a fishing / fell / bird-scare HUD.
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
    }

    private Location nearestBirdLocation() {
        if (center == null) {
            return null;
        }
        Location best = null;
        double bestDist = Double.MAX_VALUE;
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

    private boolean isBird(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(birdKey, PersistentDataType.BYTE);
    }

    private static void cancel(BukkitTask handle) {
        if (handle != null) {
            handle.cancel();
        }
    }

    private boolean withinViewRange(Player player, Location at) {
        if (player == null || at == null || at.getWorld() == null || !player.getWorld().equals(at.getWorld())) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
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

    private static List<Player> nearbyPlayers(Location at, double range) {
        List<Player> out = new ArrayList<>();
        if (at == null || at.getWorld() == null) {
            return out;
        }
        double r2 = range * range;
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
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
