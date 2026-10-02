package de.aetherion.farming;

import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.dev.ScarecrowProp;
import de.aetherion.farming.island.FarmIsleZones;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
 * Solo waves are small; every extra player nearby adds birds, and everyone who
 * was around on success shares the boost.
 */
public final class ScarecrowEvent implements Listener, StatProvider, Runnable {

    private static final int BOOST_TICKS = 20 * 60;
    private static final double FORTUNE_BONUS = 50.0d;
    private static final double HARVEST_BONUS = 50.0d;

    private final AetherionFarming plugin;
    private final org.bukkit.NamespacedKey birdKey;
    private final Map<UUID, Long> boostUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final List<UUID> birdIds = new ArrayList<>();
    private final Set<UUID> helpers = ConcurrentHashMap.newKeySet();

    private Location center;
    private BukkitTask task;
    private int ticksLeft;
    private int birdsTotal;
    private int eventTicks;
    private boolean active;
    private boolean registered;

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
        long interval = Math.max(20L * 60L, plugin.getConfig().getLong("scarecrow.interval-ticks", 20L * 300L));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::trySpawn, 20L * 45L, interval);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireBoosts, 20L, 20L);
    }

    public void shutdown() {
        endEvent(false);
        boostUntil.clear();
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
        if (!active) {
            return;
        }
        ticksLeft--;
        pruneBirds();
        flapBirds();
        updateBars();
        if (birdIds.isEmpty()) {
            succeed();
            return;
        }
        if (ticksLeft <= 0) {
            endEvent(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent event) {
        if (!isBird(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
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
        boostUntil.remove(event.getPlayer().getUniqueId());
        hideBar(event.getPlayer().getUniqueId());
    }

    private double viewRange() {
        return Math.max(8.0d, plugin.getConfig().getDouble("scarecrow.view-range", 24.0d));
    }

    private double radius() {
        return Math.max(2.0d, plugin.getConfig().getDouble("scarecrow.radius", 8.0d));
    }

    private void trySpawn() {
        ScarecrowProp prop = plugin.scarecrow();
        if (active || prop == null || Bukkit.getOnlinePlayers().isEmpty()) {
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

    private void begin(Location anchor) {
        List<Player> watching = nearbyPlayers(anchor, viewRange());
        if (watching.isEmpty()) {
            return;
        }
        active = true;
        center = anchor.clone().add(0, 1.0, 0);
        eventTicks = Math.max(20 * 5, plugin.getConfig().getInt("scarecrow.duration-ticks", 20 * 20));
        ticksLeft = eventTicks;
        int base = Math.max(1, plugin.getConfig().getInt("scarecrow.birds.base", 3));
        int perExtra = Math.max(0, plugin.getConfig().getInt("scarecrow.birds.per-extra-player", 2));
        int cap = Math.max(base, plugin.getConfig().getInt("scarecrow.birds.max", 12));
        birdsTotal = Math.min(cap, base + perExtra * Math.max(0, watching.size() - 1));
        birdIds.clear();
        helpers.clear();
        for (int i = 0; i < birdsTotal; i++) {
            spawnBird(i);
        }
        for (Player nearby : watching) {
            nearby.sendActionBar(Component.text("Birds on the scarecrow. Click them.", NamedTextColor.GREEN));
            nearby.playSound(center, Sound.ENTITY_PARROT_AMBIENT, 0.7f, 1.2f);
        }
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
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
        if (!active || entity == null || !birdIds.remove(entity.getUniqueId())) {
            return;
        }
        helpers.add(player.getUniqueId());
        Location at = entity.getLocation();
        entity.remove();
        for (Player nearby : nearbyPlayers(at, viewRange())) {
            nearby.spawnParticle(Particle.CLOUD, at, 8, 0.15, 0.15, 0.15, 0.02);
            nearby.playSound(at, Sound.ENTITY_PARROT_FLY, 0.55f, 1.35f);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.6f);
        int left = birdIds.size();
        if (left > 0) {
            player.sendActionBar(Component.text("Shooed. " + left + " left.", NamedTextColor.YELLOW));
        }
        updateBars();
        if (birdIds.isEmpty()) {
            succeed();
        }
    }

    private void succeed() {
        if (!active) {
            return;
        }
        long until = System.currentTimeMillis() + (BOOST_TICKS / 20L) * 1000L;
        Set<UUID> rewarded = new HashSet<>(helpers);
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            rewarded.add(nearby.getUniqueId());
        }
        Location pulse = center;
        for (UUID id : rewarded) {
            boostUntil.put(id, until);
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                grantBoostFeedback(player);
            }
        }
        if (pulse != null && pulse.getWorld() != null) {
            for (Player nearby : nearbyPlayers(pulse, viewRange())) {
                nearby.spawnParticle(Particle.HAPPY_VILLAGER, pulse, 28, 0.9, 0.6, 0.9, 0.02);
                nearby.playSound(pulse, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.35f);
            }
        }
        endEvent(true);
    }

    private void grantBoostFeedback(Player player) {
        player.sendMessage("§6Golden Hour! §7+50 Fortune, +50 Harvest for 60s.");
        player.sendActionBar(Component.text("Golden Hour · +50 Fortune · +50 Harvest  (60s)", NamedTextColor.GOLD));
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Golden Hour", NamedTextColor.GOLD),
                Component.text("+50 Fortune · +50 Harvest", NamedTextColor.YELLOW),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(80),
                        java.time.Duration.ofMillis(1600),
                        java.time.Duration.ofMillis(220)
                )
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.65f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 14, 0.35, 0.45, 0.35, 0.02);
    }

    private void endEvent(boolean success) {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : List.copyOf(birdIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        birdIds.clear();
        for (UUID id : List.copyOf(bars.keySet())) {
            hideBar(id);
        }
        if (!success && center != null) {
            for (Player nearby : nearbyPlayers(center, viewRange())) {
                nearby.sendActionBar(Component.text("The birds left. Crops are fine.", NamedTextColor.GRAY));
            }
        }
        helpers.clear();
        center = null;
        ticksLeft = 0;
        birdsTotal = 0;
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

    private void updateBars() {
        if (!active || center == null) {
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
        double progress = Math.max(0.0d, Math.min(1.0d, ticksLeft / (double) Math.max(1, eventTicks)));
        int left = birdIds.size();
        String title = "Scarecrow watch  " + left + "/" + Math.max(birdsTotal, left);
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            paint(nearby, title, progress);
        }
    }

    private void paint(Player player, String title, double progress) {
        float clamped = (float) Math.max(0.0d, Math.min(1.0d, progress));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(
                    Component.text(title),
                    clamped,
                    BossBar.Color.YELLOW,
                    BossBar.Overlay.NOTCHED_20
            );
            bars.put(player.getUniqueId(), bar);
            QuestBars.suppress(player);
            player.showBossBar(bar);
            return;
        }
        bar.name(Component.text(title));
        bar.progress(clamped);
        player.showBossBar(bar);
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
        QuestBars.unsuppress(playerId);
    }

    private void expireBoosts() {
        long now = System.currentTimeMillis();
        boostUntil.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text("Field boost faded.", NamedTextColor.GRAY));
            }
            return true;
        });
    }

    private boolean isBird(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(birdKey, PersistentDataType.BYTE);
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
