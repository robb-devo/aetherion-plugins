package de.aetherion.farming;

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
import org.bukkit.World;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optional crop-field event: birds land on a crop patch. Click them to shoo.
 * Success: temporary Fortune + Harvest. Fail/ignore: no debuff.
 */
public final class BirdScareEvent implements Listener, StatProvider, Runnable {

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int SCAN_RADIUS = 18;
    private static final int EVENT_TICKS = 20 * 12;
    private static final int BOOST_TICKS = 20 * 60;
    private static final double FORTUNE_BONUS = 50.0d;
    private static final double HARVEST_BONUS = 50.0d;
    private static final int MIN_BIRDS = 2;
    private static final int MAX_BIRDS = 4;
    /** Fallback when config has no view-range — bossbar / birds only this close. */
    private static final double DEFAULT_VIEW_RANGE = 25.0d;

    private final AetherionFarming plugin;
    private final org.bukkit.NamespacedKey birdKey;
    private final Map<UUID, Long> boostUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final List<UUID> birdIds = new ArrayList<>();
    private final List<UUID> viewers = new ArrayList<>();
    private final java.util.Set<UUID> helpers = ConcurrentHashMap.newKeySet();

    private Location center;
    private UUID hostId;
    private BukkitTask task;
    private int ticksLeft;
    private int birdsTotal;
    private boolean active;
    private boolean registered;

    BirdScareEvent(AetherionFarming plugin) {
        this.plugin = plugin;
        this.birdKey = new org.bukkit.NamespacedKey(plugin, "scare_bird");
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

    void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            ActiveEquipmentStats.registerProvider(this);
            registered = true;
        }
        long interval = Math.max(20L * 60L, plugin.getConfig().getLong("bird-scare.interval-ticks", 20L * 120L));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::trySpawn, 20L * 30L, interval);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireBoosts, 20L, 20L);
    }

    void shutdown() {
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
        syncBirdVisibility();
        pulseParticles();
        updateBars();
        flapBirds();
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
        if (hostId != null && hostId.equals(event.getPlayer().getUniqueId())) {
            endEvent(false);
        }
    }

    private void trySpawn() {
        if (active || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            if (Crops.isDungeonWorld(player.getWorld())) {
                continue;
            }
            if (!inFarmZone(player.getLocation())) {
                continue;
            }
            Location crop = nearCrops(player.getLocation(), SCAN_RADIUS);
            if (crop != null && inFarmZone(crop)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Player host = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location crop = nearCrops(host.getLocation(), SCAN_RADIUS);
        if (crop == null || !inFarmZone(crop)) {
            return;
        }
        begin(host, crop);
    }

    private void begin(Player host, Location crop) {
        active = true;
        hostId = host.getUniqueId();
        center = crop.clone().add(0.5, 0.15, 0.5);
        ticksLeft = EVENT_TICKS;
        birdsTotal = MIN_BIRDS + ThreadLocalRandom.current().nextInt(MAX_BIRDS - MIN_BIRDS + 1);
        birdIds.clear();
        viewers.clear();
        helpers.clear();
        helpers.add(host.getUniqueId());
        for (int i = 0; i < birdsTotal; i++) {
            spawnBird(i);
        }
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            viewers.add(nearby.getUniqueId());
            nearby.sendActionBar(Component.text("Birds on the crops. Click them.", NamedTextColor.GREEN));
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
        double angle = (Math.PI * 2.0d * index) / birdsTotal + rng.nextDouble() * 0.4d;
        double radius = 0.7d + rng.nextDouble() * 1.1d;
        Location at = center.clone().add(Math.cos(angle) * radius, 0.55d + rng.nextDouble() * 0.35d, Math.sin(angle) * radius);
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
        // Hidden by default — only players within view range see the event.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, bird);
        }
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            nearby.showEntity(plugin, bird);
        }
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
        java.util.Set<UUID> rewarded = new java.util.HashSet<>(helpers);
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            rewarded.add(nearby.getUniqueId());
        }
        Location pulse = center;
        for (UUID id : rewarded) {
            boostUntil.put(id, until);
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            grantBoostFeedback(player, pulse);
        }
        if (pulse != null && pulse.getWorld() != null) {
            for (Player nearby : nearbyPlayers(pulse, viewRange())) {
                nearby.spawnParticle(Particle.HAPPY_VILLAGER, pulse.clone().add(0, 0.6, 0), 28, 0.9, 0.45, 0.9, 0.02);
                nearby.spawnParticle(Particle.FIREWORK, pulse.clone().add(0, 0.8, 0), 18, 0.55, 0.35, 0.55, 0.02);
                nearby.playSound(pulse, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.35f);
            }
        }
        endEvent(true);
    }

    private void grantBoostFeedback(Player player, Location field) {
        player.sendMessage("§aField clear. §7+50 Fortune, +50 Harvest for 60s.");
        player.sendActionBar(Component.text("+50 Fortune · +50 Harvest  (60s)", NamedTextColor.GOLD));
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Field clear", NamedTextColor.GREEN),
                Component.text("+50 Fortune · +50 Harvest", NamedTextColor.GOLD),
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && boostUntil.containsKey(player.getUniqueId())) {
                player.sendActionBar(Component.text("+50 Fortune · +50 Harvest  (60s)", NamedTextColor.GOLD));
            }
        }, 25L);
        if (field != null && field.getWorld() != null && player.getWorld().equals(field.getWorld())) {
            player.playSound(field, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.35f, 1.5f);
        }
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
        viewers.clear();
        helpers.clear();
        if (!success && center != null) {
            for (Player nearby : nearbyPlayers(center, viewRange())) {
                nearby.sendActionBar(Component.text("The birds left. Crops are fine.", NamedTextColor.GRAY));
            }
        }
        center = null;
        hostId = null;
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

    private void pulseParticles() {
        if (center == null || center.getWorld() == null || ticksLeft % 4 != 0) {
            return;
        }
        Location at = center.clone().add(0, 0.4, 0);
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            nearby.spawnParticle(Particle.CRIT, at, 6, 0.9, 0.25, 0.9, 0.01);
            nearby.spawnParticle(Particle.HAPPY_VILLAGER, center, 3, 0.7, 0.2, 0.7, 0.0);
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

    private void updateBars() {
        if (!active || center == null) {
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
        double progress = Math.max(0.0d, Math.min(1.0d, ticksLeft / (double) EVENT_TICKS));
        int left = birdIds.size();
        Location aim = nearestBirdLocation();
        for (Player nearby : nearbyPlayers(center, viewRange())) {
            if (!viewers.contains(nearby.getUniqueId())) {
                viewers.add(nearby.getUniqueId());
                nearby.sendActionBar(Component.text("Birds on the crops. Click them.", NamedTextColor.GREEN));
            }
            String arrow = directionArrow(nearby.getLocation(), aim != null ? aim : center);
            String title = arrow + " Shoo the birds  " + left + "/" + Math.max(birdsTotal, left);
            paint(nearby, title, progress);
        }
    }

    private void paint(Player player, String title, double progress) {
        if (player == null || center == null || !withinViewRange(player, center)) {
            if (player != null) {
                hideBar(player.getUniqueId());
            }
            return;
        }
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
            QuestBossBarHook.suppress(player);
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
        QuestBossBarHook.unsuppress(playerId);
        viewers.remove(playerId);
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
