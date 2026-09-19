package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AetherionSetListener implements Listener, Runnable {

    private static final long SAVE_COOLDOWN_MS = 15L * 60L * 1000L;
    private static final NamespacedKey MINION_KEY = AetherKeys.SET_MINION;
    private static final NamespacedKey OWNER_KEY = AetherKeys.SET_MINION_OWNER;
    private static final NamespacedKey NO_SAVE = AetherKeys.NO_SET_SAVE;
    private static final NamespacedKey BOSS_KEY = AetherKeys.BOSS_ID;
    private static final NamespacedKey BOSS_MINION_KEY = AetherKeys.BOSS_MINION;
    private static final NamespacedKey PET_KEY = AetherKeys.PET_ENTITY;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> nextSave = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> minions = new ConcurrentHashMap<>();
    private int tick;
    private final Map<UUID, Float> glideYaw = new ConcurrentHashMap<>();

    public AetherionSetListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 2L, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::purgeOrphanMinions, 5L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::purgeOrphanMinions, 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!wearingFullSet(player)) {
            return;
        }
        if (isUnsavable(event.getCause())) {
            return;
        }
        Byte blocked = player.getPersistentDataContainer().get(NO_SAVE, PersistentDataType.BYTE);
        if (blocked != null && blocked == (byte) 1) {
            return;
        }
        double remaining = player.getHealth() - event.getFinalDamage();
        if (remaining > 0.05) {
            return;
        }
        int tier = de.aetherion.items.item.DungeonCore.wornAetherionSetTier(player, itemManager);
        long now = System.currentTimeMillis();
        Long next = nextSave.get(player.getUniqueId());
        if (next != null && now < next) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0);
        nextSave.put(player.getUniqueId(), now + de.aetherion.items.item.DungeonCore.aetherionSaveCooldownMs(tier));

        double restored = Math.max(4.0, maxHealth(player) * de.aetherion.items.item.DungeonCore.aetherionSaveHealth(tier));
        HealthListener health = AetherionItems.getInstance().getHealthListener();
        if (health != null) {
            double missing = restored - player.getHealth();
            if (missing > 0) {
                health.heal(player, missing);
            }
        } else {
            player.setHealth(Math.min(maxHealth(player), Math.max(player.getHealth(), restored)));
        }
        player.setFireTicks(0);
        player.setNoDamageTicks(40);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.85f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.45f, 1.4f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.15);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 24, 0.6, 0.8, 0.6, 0.2);
        int minutes = (int) Math.max(1L, de.aetherion.items.item.DungeonCore.aetherionSaveCooldownMs(tier) / 60000L);
        player.sendMessage("§5Aetherion §7refused your death. §8(" + minutes + "m)");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMinionDamaged(EntityDamageEvent event) {
        if (!isMinion(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
        if (event.getEntity() instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            living.setHealth(Math.max(1.0, living.getMaxHealth()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinionHurtPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!isMinion(damager)) {
            return;
        }
        if (event.getEntity() instanceof Player || !isValidEnemy(livingOf(event.getEntity()), damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionTarget(EntityTargetLivingEntityEvent event) {
        if (!isMinion(event.getEntity())) {
            return;
        }
        if (event.getTarget() instanceof Player || event.getTarget() == null || !isValidEnemy(event.getTarget(), event.getEntity())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinionExplode(EntityExplodeEvent event) {
        if (!isMinion(event.getEntity())) {
            return;
        }
        event.blockList().clear();
        event.setYield(0);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinionChangeBlock(EntityChangeBlockEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinionPortal(EntityPortalEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMinionDeath(EntityDeathEvent event) {
        if (!isMinion(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.getEntity().remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractMinion(PlayerInteractEntityEvent event) {
        if (isMinion(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeMinion(event.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!player.isOnline() || player.isDead() || !wearingFullSet(player)) {
                    removeMinion(player.getUniqueId());
                    continue;
                }
                maintainMinion(player);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Mini Aetherion tick failed for " + player.getName() + ": " + ex.getMessage());
                removeMinion(player.getUniqueId());
            }
        }
        minions.entrySet().removeIf(entry -> {
            if (Bukkit.getPlayer(entry.getKey()) != null) {
                return false;
            }
            LivingEntity minion = entity(entry.getValue());
            if (minion != null && minion.isValid()) {
                minion.remove();
            }
            return true;
        });
        if (tick % 20 == 0) {
            purgeOrphanMinions();
        }
    }

    private void maintainMinion(Player player) {
        int tier = de.aetherion.items.item.DungeonCore.wornAetherionSetTier(player, itemManager);
        UUID minionId = minions.get(player.getUniqueId());
        LivingEntity minion = entity(minionId);
        if (minion instanceof Phantom phantom && phantom.isValid() && !phantom.isDead()) {
            hoverNear(player, phantom);
            applyMiniScale(phantom, de.aetherion.items.item.DungeonCore.aetherionMiniScale(tier));
            int interval = Math.max(8, de.aetherion.items.item.DungeonCore.aetherionSpitIntervalTicks(tier));
            if (tick % interval == 0) {
                spitPurpleFire(player, phantom);
            }
            return;
        }
        if (minion != null && minion.isValid()) {
            minion.remove();
        }
        spawnMinion(player);
    }

    private void hoverNear(Player player, LivingEntity minion) {
        if (minion instanceof Phantom phantom) {
            phantom.setSize(0);
            phantom.setShouldBurnInDay(false);
            phantom.setTarget(null);
            phantom.setAI(false);
        }
        applyMiniScale(minion, 0.48);
        minion.setGravity(false);
        minion.setCollidable(false);
        minion.setInvulnerable(true);
        minion.setFireTicks(0);
        if (minion instanceof org.bukkit.entity.Mob mob) {
            mob.setAware(false);
            mob.setAI(false);
        }
        Location home = followSpot(player);
        Location current = minion.getLocation();
        if (!sameWorld(current, home)) {
            minion.teleport(home);
            return;
        }
        double dist = current.distance(home);
        Location next;
        if (dist > 11) {
            next = home.clone();
        } else if (dist > 0.45) {
            Vector to = home.toVector().subtract(current.toVector());
            next = current.clone().add(to.normalize().multiply(Math.min(0.16, dist)));
        } else {
            double bob = Math.sin(tick * 0.045) * 0.08;
            next = current.clone();
            next.setY(home.getY() + bob);
        }
        float targetYaw = dist > 0.45
                ? yawOf(home.toVector().subtract(current.toVector()))
                : glideYaw.getOrDefault(player.getUniqueId(), current.getYaw());
        float yaw = turnToward(glideYaw.getOrDefault(player.getUniqueId(), current.getYaw()), targetYaw, 1.8f);
        glideYaw.put(player.getUniqueId(), yaw);
        next.setYaw(yaw);
        next.setPitch(8f);
        minion.teleport(next);
        minion.setRotation(yaw, 8f);
        if (tick % 16 == 0) {
            minion.getWorld().spawnParticle(Particle.DRAGON_BREATH, next, 1, 0.12, 0.08, 0.12, 0);
        }
    }

    private static float yawOf(Vector vector) {
        if (vector.lengthSquared() < 0.0001) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
    }

    private static float turnToward(float current, float target, float maxStep) {
        float delta = target - current;
        while (delta > 180f) {
            delta -= 360f;
        }
        while (delta < -180f) {
            delta += 360f;
        }
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }

    private static void applyMiniScale(LivingEntity entity, double scale) {
        Attribute attribute = scaleAttribute();
        if (attribute != null) {
            try {
                entity.registerAttribute(attribute);
            } catch (IllegalArgumentException ignored) {
            }
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(scale);
            }
        }
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            Class<?> type = handle.getClass();
            while (type != null && type != Object.class) {
                try {
                    java.lang.reflect.Method method = type.getDeclaredMethod("setScale", float.class);
                    method.setAccessible(true);
                    method.invoke(handle, (float) scale);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void spitPurpleFire(Player owner, LivingEntity minion) {
        LivingEntity target = nearestEnemy(minion.getLocation(), minion);
        if (target == null) {
            return;
        }
        Location from = minion.getLocation().add(0, Math.max(0.35, minion.getHeight() * 0.45), 0);
        Vector step = target.getEyeLocation().toVector().subtract(from.toVector());
        if (step.lengthSquared() < 0.01) {
            return;
        }
        step.normalize().multiply(0.55);
        Location cursor = from.clone();
        for (int i = 0; i < 18; i++) {
            cursor.add(step);
            minion.getWorld().spawnParticle(Particle.DRAGON_BREATH, cursor, 3, 0.08, 0.08, 0.08, 0.01);
            minion.getWorld().spawnParticle(Particle.WITCH, cursor, 1, 0.04, 0.04, 0.04, 0);
            Location eye = target.getEyeLocation();
            if (sameWorld(cursor, eye) && cursor.distanceSquared(eye) <= 1.35 * 1.35) {
                break;
            }
        }
        if (isValidEnemy(target, minion)) {
            int tier = de.aetherion.items.item.DungeonCore.wornAetherionSetTier(owner, itemManager);
            boolean boss = target.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING);
            double amount = de.aetherion.items.item.DungeonCore.aetherionSpitDamage(tier, boss);
            de.aetherion.items.combat.ScriptedHits.run(() -> target.damage(amount, minion));
            target.setFireTicks(Math.max(target.getFireTicks(), 40));
        }
        minion.getWorld().playSound(from, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.35f, 1.55f);
    }

    private void spawnMinion(Player player) {
        Location at = followSpot(player);
        int tier = de.aetherion.items.item.DungeonCore.wornAetherionSetTier(player, itemManager);
        Phantom phantom = player.getWorld().spawn(at, Phantom.class, spawned -> {
            spawned.setCustomName("§d✦ Mini Aetherion");
            spawned.setCustomNameVisible(true);
            spawned.setGlowing(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setCollidable(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setAware(false);
            spawned.setAI(false);
            spawned.setSize(0);
            spawned.setShouldBurnInDay(false);
            spawned.setTarget(null);
            spawned.getPersistentDataContainer().set(MINION_KEY, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
            applyMiniScale(spawned, de.aetherion.items.item.DungeonCore.aetherionMiniScale(tier));
        });
        minions.put(player.getUniqueId(), phantom.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.55f);
    }

    private static boolean sameWorld(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null
                && b.getWorld() != null
                && a.getWorld().equals(b.getWorld());
    }

    private Location followSpot(Player player) {
        Location origin = player.getLocation();
        double yaw = Math.toRadians(origin.getYaw());
        return origin.clone().add(Math.sin(yaw) * 2.1, 2.35, -Math.cos(yaw) * 2.1);
    }

    private void removeMinion(UUID playerId) {
        UUID minionId = minions.remove(playerId);
        glideYaw.remove(playerId);
        LivingEntity minion = entity(minionId);
        if (minion != null && minion.isValid()) {
            minion.remove();
        }
    }

    private LivingEntity nearestEnemy(Location origin, Entity self) {
        LivingEntity best = null;
        double bestDist = 16 * 16;
        for (Entity found : origin.getWorld().getNearbyEntities(origin, 16, 10, 16)) {
            if (!(found instanceof LivingEntity living) || living.equals(self)) {
                continue;
            }
            if (!isValidEnemy(living, self)) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidEnemy(LivingEntity living, Entity self) {
        if (living == null || living.equals(self) || living instanceof Player) {
            return false;
        }
        if (living.isDead() || !living.isValid()) {
            return false;
        }
        if (isMinion(living) || living.hasMetadata("NPC")) {
            return false;
        }
        if (living.getPersistentDataContainer().has(PET_KEY, PersistentDataType.BYTE)
                || living.getPersistentDataContainer().has(BOSS_MINION_KEY, PersistentDataType.STRING)) {
            return false;
        }
        if (living.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING)) {
            return true;
        }
        if (living instanceof EnderDragon) {
            return false;
        }
        boolean dungeonMob = living.getPersistentDataContainer().has(
                AetherKeys.DUNGEON_MOB,
                PersistentDataType.STRING
        );
        if (living instanceof Phantom && !dungeonMob) {
            return false;
        }
        return dungeonMob || living instanceof Enemy;
    }

    private boolean wearingFullSet(Player player) {
        return isPiece(player.getInventory().getHelmet(), "aetherion_helmet")
                && isPiece(player.getInventory().getChestplate(), "aetherion_chestplate")
                && isPiece(player.getInventory().getLeggings(), "aetherion_leggings")
                && isPiece(player.getInventory().getBoots(), "aetherion_boots");
    }

    private boolean isPiece(ItemStack item, String id) {
        return id.equalsIgnoreCase(itemManager.getItemId(item));
    }

    private boolean isMinion(Entity entity) {
        if (entity instanceof org.bukkit.entity.EnderDragonPart part) {
            entity = part.getParent();
        }
        return entity != null && entity.getPersistentDataContainer().has(MINION_KEY, PersistentDataType.BYTE);
    }

    private void purgeOrphanMinions() {
        Set<UUID> live = new HashSet<>(minions.values());
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Entity root = entity;
                if (entity instanceof org.bukkit.entity.EnderDragonPart part) {
                    root = part.getParent();
                }
                if (root == null || !root.isValid() || root instanceof Player || live.contains(root.getUniqueId())) {
                    continue;
                }
                if (!isLeftoverMinion(root)) {
                    continue;
                }
                root.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover Mini Aetherion entit" + (removed == 1 ? "y" : "ies") + ".");
        }
    }

    private boolean isLeftoverMinion(Entity entity) {
        if (entity.getPersistentDataContainer().has(MINION_KEY, PersistentDataType.BYTE)) {
            return true;
        }
        if (entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING)
                || entity.getPersistentDataContainer().has(BOSS_MINION_KEY, PersistentDataType.STRING)) {
            return false;
        }
        String name = entity.getCustomName();
        if (name == null) {
            return false;
        }
        String plain = ChatColor.stripColor(name).toLowerCase();
        if (!plain.contains("mini aetherion")) {
            return false;
        }
        return entity instanceof IronGolem
                || entity instanceof EnderDragon
                || entity instanceof Phantom;
    }

    private LivingEntity livingOf(Entity entity) {
        return entity instanceof LivingEntity living ? living : null;
    }

    private LivingEntity entity(UUID id) {
        if (id == null) {
            return null;
        }
        Entity found = Bukkit.getEntity(id);
        return found instanceof LivingEntity living ? living : null;
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(maxHealthAttribute());
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private static boolean isUnsavable(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER;
    }

    private static Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException ignored) {
            return Attribute.valueOf("MAX_HEALTH");
        }
    }

    private static Attribute scaleAttribute() {
        try {
            return Attribute.valueOf("GENERIC_SCALE");
        } catch (IllegalArgumentException ignored) {
            try {
                return Attribute.valueOf("SCALE");
            } catch (IllegalArgumentException ignoredToo) {
                return null;
            }
        }
    }
}
