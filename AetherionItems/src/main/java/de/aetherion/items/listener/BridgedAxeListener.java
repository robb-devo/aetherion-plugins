package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bridged Axe: throw every 15s and steal a cut of damage dealt.
 * The throw is a two-handed heave with a gold arc and a rattle of the troll's toll chain. The axe itself tumbles
 * end over end through the air, its head carving a gold ribbon and whirring. It bites: into a foe (splinters, a gold
 * slash, the toll's coins spilling toward the thrower) or into the floor or wall, sits lodged and shuddering, then
 * rips free and spins back to the thrower's hand, trailing the stolen toll.
 */
public class BridgedAxeListener implements Listener {

    private static final int COOLDOWN_TICKS = 300;
    private static final double THROW_SPEED = 1.7;
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final int LODGE_TICKS = 9;
    private static final int RETURN_TICKS = 9;
    private static final float AXE_SCALE = 1.3f;
    private static final Color TOLL_GOLD = Color.fromRGB(255, 196, 70);
    private static final Color TARNISH = Color.fromRGB(150, 105, 35);
    private static final Color BLOOD = Color.fromRGB(170, 20, 30);
    private static final ItemStack COIN = new ItemStack(Material.GOLD_NUGGET);

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final Map<UUID, Axe> flights = new ConcurrentHashMap<>();

    public BridgedAxeListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    /** Plugin disable: removes every thrown or returning axe display. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isBridgedAxe(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§6Bridged Axe §7recharging… §f" + left + "s"
            ));
            return;
        }

        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        int cooldown = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                de.aetherion.items.item.DungeonCore.bridgedCooldownTicks(tier)
        );
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
        player.setCooldown(item.getType(), cooldown);
        de.aetherion.items.combat.AbilityCooldownHud.arm(player, "bridged_axe", "Bridged Axe", tick + cooldown, item);
        throwAxe(player, item);
        scheduleReady(player, cooldown);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        Byte tagged = snowball.getPersistentDataContainer().get(
                ItemKeys.thrownAxe(),
                PersistentDataType.BYTE
        );
        if (tagged == null || tagged != 1) {
            return;
        }
        Axe axe = flights.remove(snowball.getUniqueId());
        Location impact = snowball.getLocation();
        Vector travel = snowball.getVelocity();

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living
                && living.isValid()
                && !living.isDead()
                && !(living instanceof Player)
                && !(living instanceof ArmorStand)
                && snowball.getShooter() instanceof Player player) {

            Double stored = snowball.getPersistentDataContainer().get(
                    ItemKeys.damage(),
                    PersistentDataType.DOUBLE
            );
            double damage = stored == null ? 12.0 : stored;
            boolean crit = de.aetherion.items.combat.DamageNumbers.isCrit(snowball);
            if (crit) {
                de.aetherion.items.combat.DamageNumbers.markCrit(player);
            }
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(snowball)
                    .build();
            living.damage(damage, source);

            Double steal = snowball.getPersistentDataContainer().get(
                    ItemKeys.lifesteal(),
                    PersistentDataType.DOUBLE
            );
            boolean toll = steal != null && steal > 0;
            if (toll) {
                heal(player, damage * steal);
            }

            biteFlesh(player, living, travel, crit, toll);
            if (axe != null) {
                axe.lodgeIn(living, impact, toll);
            }
        } else {
            Block block = event.getHitBlock();
            biteGround(impact, block);
            if (axe != null) {
                axe.lodgeAt(impact.clone().add(safeDir(travel).multiply(0.25)));
            }
        }

        event.setCancelled(true);
        snowball.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        if (!isBridgedAxe(player.getInventory().getItemInMainHand())) {
            return;
        }
        double percent = lifestealOf(player.getInventory().getItemInMainHand());
        if (percent <= 0) {
            return;
        }
        heal(player, event.getFinalDamage() * percent);
    }

    private void throwAxe(Player player, ItemStack item) {
        Vector velocity = player.getEyeLocation().getDirection().multiply(
                de.aetherion.items.item.DungeonCore.bridgedThrowSpeed(de.aetherion.items.item.DungeonCore.tier(item))
        );
        Snowball snowball = player.launchProjectile(Snowball.class, velocity);
        snowball.setShooter(player);
        snowball.setGravity(true);
        snowball.setItem(item.clone());

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 12.0;
        }
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            de.aetherion.items.combat.DamageNumbers.tagCrit(snowball);
        }

        snowball.getPersistentDataContainer().set(ItemKeys.thrownAxe(), PersistentDataType.BYTE, (byte) 1);
        snowball.getPersistentDataContainer().set(ItemKeys.damage(), PersistentDataType.DOUBLE, damage);
        snowball.getPersistentDataContainer().set(ItemKeys.lifesteal(), PersistentDataType.DOUBLE, lifestealOf(item));

        ItemDisplay display = spawnAxe(snowball.getLocation(), item);
        if (display != null) {
            for (Player viewer : snowball.getWorld().getPlayers()) {
                viewer.hideEntity(plugin, snowball);
            }
            Axe axe = new Axe(player, snowball, display);
            flights.put(snowball.getUniqueId(), axe);
            axe.runTaskTimer(plugin, 1L, 1L);
        } else {
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                if (!snowball.isValid() || snowball.isDead()) {
                    task.cancel();
                    return;
                }
                snowball.getWorld().spawnParticle(Particle.CRIT, snowball.getLocation(), 2, 0.04, 0.04, 0.04, 0.01);
            }, 1L, 1L);
        }

        heave(player);
    }

    /** The two-handed heave: a gold arc from over the shoulder down to the release, and the toll chain rattling. */
    private void heave(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = look.getCrossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector up = right.getCrossProduct(look).normalize();
        Location pivot = eye.clone().add(right.clone().multiply(0.3));
        Particle.DustOptions arc = new Particle.DustOptions(TOLL_GOLD, 1.0f);
        Particle.DustOptions dim = new Particle.DustOptions(TARNISH, 0.7f);
        for (int i = 0; i <= 10; i++) {
            double theta = Math.toRadians(110 - i * 13);
            Vector dir = look.clone().multiply(Math.cos(theta)).add(up.clone().multiply(Math.sin(theta)));
            Location p = pivot.clone().add(dir.multiply(1.2));
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, i > 6 ? arc : dim);
        }
        Location release = eye.clone().add(look.clone().multiply(1.1));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, release, 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.CRIT, release, 8, 0.1, 0.1, 0.1, 0.05);

        player.getWorld().playSound(eye, Sound.ITEM_TRIDENT_THROW, 1.05f, 0.75f);
        player.getWorld().playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
        player.getWorld().playSound(eye, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.7f);
        player.getWorld().playSound(eye, Sound.BLOCK_CHAIN_PLACE, 0.6f, 0.6f);
    }

    /** The axe bites a foe: splinters, a gold slash across the body and, when the toll is taken, coins spill. */
    private void biteFlesh(Player player, LivingEntity living, Vector travel, boolean crit, boolean toll) {
        World world = living.getWorld();
        Location chest = living.getLocation().add(0, living.getHeight() * 0.55, 0);
        Vector fwd = safeDir(travel);
        Vector side = fwd.getCrossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector diag = side.clone().add(new Vector(0, 1, 0)).normalize();
        Particle.DustOptions slash = new Particle.DustOptions(TOLL_GOLD, 1.2f);
        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0 * 2.0 - 1.0;
            Location p = chest.clone().add(diag.clone().multiply(t * 0.75)).add(fwd.clone().multiply(-0.35 * (1 - t * t)));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, slash);
        }
        world.spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.25, 0.25, 0.25, 0.2);
        world.spawnParticle(Particle.SWEEP_ATTACK, living.getLocation().add(0, 1, 0), 1);
        world.spawnParticle(Particle.BLOCK, chest, 10, 0.2, 0.25, 0.2, 0.1, Material.SPRUCE_PLANKS.createBlockData());
        world.playSound(living.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.05f, 0.7f);
        world.playSound(living.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.55f, 1.35f);
        world.playSound(living.getLocation(), Sound.ITEM_AXE_STRIP, 0.7f, 0.7f);
        if (crit) {
            world.spawnParticle(Particle.ENCHANTED_HIT, chest, 16, 0.3, 0.35, 0.3, 0.3);
            world.playSound(living.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 0.8f);
        }
        if (toll) {
            Vector home = player.getLocation().add(0, 1.2, 0).toVector().subtract(chest.toVector());
            if (home.lengthSquared() > 1.0E-4) {
                home.normalize();
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                world.spawnParticle(Particle.ITEM, chest, 0,
                        home.getX() + random.nextDouble(-0.3, 0.3),
                        home.getY() + random.nextDouble(0.4, 0.8),
                        home.getZ() + random.nextDouble(-0.3, 0.3),
                        random.nextDouble(0.28, 0.42), COIN);
            }
            world.spawnParticle(Particle.DUST, chest, 5, 0.2, 0.25, 0.2, 0, new Particle.DustOptions(BLOOD, 1.1f));
        }
    }

    /** The axe bites the floor or a wall: chips of whatever it hit, and a solid thunk. */
    private void biteGround(Location at, Block block) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        if (block != null && block.getType().isSolid()) {
            world.spawnParticle(Particle.BLOCK, at, 14, 0.15, 0.15, 0.15, 0.1, block.getBlockData());
        }
        world.spawnParticle(Particle.CRIT, at, 8, 0.15, 0.15, 0.15, 0.05);
        world.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.4f);
        world.playSound(at, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 0.8f);
        world.playSound(at, Sound.ITEM_AXE_STRIP, 0.6f, 1.0f);
    }

    /** A soft chain clink when the throw is back, so the thrower hears it without watching the hotbar. */
    private void scheduleReady(Player player, int cooldown) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.6f, 1.4f);
        }, Math.max(1, cooldown));
    }

    private boolean isBridgedAxe(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.GOLDEN_AXE) {
            return false;
        }
        return "bridged_axe".equalsIgnoreCase(itemManager.getItemId(item));
    }

    private double lifestealOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Double value = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.lifesteal(),
                PersistentDataType.DOUBLE
        );
        return value == null ? 0 : Math.max(0, value) * de.aetherion.items.item.DungeonCore.skillEffect(
                de.aetherion.items.item.DungeonCore.tier(item)
        );
    }

    private void heal(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        HealthListener health = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().getHealthListener();
        if (health != null) {
            health.heal(player, amount);
        }
        player.getWorld().spawnParticle(
                Particle.HEART,
                player.getLocation().add(0, 1.25, 0),
                2,
                0.2,
                0.15,
                0.2,
                0
        );
    }

    private static Vector safeDir(Vector v) {
        if (v == null || v.lengthSquared() < 1.0E-6) {
            return new Vector(0, 0, 1);
        }
        return v.clone().normalize();
    }

    private ItemDisplay spawnAxe(Location at, ItemStack item) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            ItemStack shown = item.clone();
            shown.setAmount(1);
            ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
                spawned.setItemStack(shown);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                spawned.setPersistent(false);
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setTeleportDuration(1);
                spawned.setInterpolationDuration(1);
                spawned.setTransformation(axeTransform(0, 0));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The axe sprite turned edge-on to its heading, tumbling by {@code spin}, with an optional side tilt. */
    private static Transformation axeTransform(double spin, double tilt) {
        Quaternionf rot = new Quaternionf()
                .rotateZ((float) tilt)
                .rotateX((float) -spin)
                .rotateY((float) (Math.PI / 2));
        return new Transformation(new Vector3f(), rot, new Vector3f(AXE_SCALE, AXE_SCALE, AXE_SCALE), new Quaternionf());
    }

    private enum Phase { FLYING, LODGED, RETURNING }

    /** One thrown axe: tumbles with the (hidden) projectile, lodges where it bites, then flies home. */
    private final class Axe extends BukkitRunnable {
        final Player player;
        final World world;
        final Snowball ball;
        final ItemDisplay display;
        Phase phase = Phase.FLYING;
        Location at;
        Vector heading;
        double spin;
        int age;
        int phaseAge;
        LivingEntity host;
        Vector hostOffset;
        Location returnFrom;
        boolean carriesToll;

        Axe(Player player, Snowball ball, ItemDisplay display) {
            this.player = player;
            this.world = ball.getWorld();
            this.ball = ball;
            this.display = display;
            this.at = ball.getLocation();
            this.heading = safeDir(ball.getVelocity());
        }

        @Override
        public void run() {
            age++;
            phaseAge++;
            if (!display.isValid()) {
                finish();
                return;
            }
            switch (phase) {
                case FLYING -> fly();
                case LODGED -> sit();
                case RETURNING -> home();
            }
        }

        void fly() {
            if (!ball.isValid() || ball.isDead()) {
                flights.remove(ball.getUniqueId());
                startReturn(at);
                return;
            }
            if (age > MAX_FLIGHT_TICKS) {
                flights.remove(ball.getUniqueId());
                ball.remove();
                startReturn(at);
                return;
            }
            at = ball.getLocation();
            heading = safeDir(ball.getVelocity());
            spin += 0.85;
            pose(at, heading, spin, 0);

            Location head = at.clone().add(new Vector(0, 1, 0).multiply(Math.cos(spin) * 0.5))
                    .add(heading.clone().multiply(Math.sin(spin) * 0.5));
            world.spawnParticle(Particle.DUST, head, 1, 0, 0, 0, 0, new Particle.DustOptions(TOLL_GOLD, 0.9f));
            world.spawnParticle(Particle.DUST, at, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(TARNISH, 0.6f));
            if (age % 2 == 0) {
                world.spawnParticle(Particle.CRIT, at, 1, 0.04, 0.04, 0.04, 0.01);
            }
            if (age % 4 == 0) {
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.7f);
            }
        }

        void lodgeIn(LivingEntity living, Location impact, boolean toll) {
            host = living;
            Location center = living.getLocation().add(0, living.getHeight() * 0.55, 0);
            Vector off = impact.toVector().subtract(living.getLocation().toVector());
            Vector inward = center.toVector().subtract(impact.toVector());
            if (inward.lengthSquared() > 0.04) {
                off.add(inward.normalize().multiply(0.2));
            }
            hostOffset = off;
            carriesToll = toll;
            enterLodge(impact);
        }

        void lodgeAt(Location point) {
            enterLodge(point);
        }

        private void enterLodge(Location point) {
            at = point.clone();
            phase = Phase.LODGED;
            phaseAge = 0;
            spin = Math.PI / 2 + 0.35;
            pose(at, heading, spin, 0);
        }

        /** Sits in whatever it hit, shuddering for the first few ticks, then rips free. */
        void sit() {
            if (host != null) {
                if (!host.isValid() || host.isDead() || host.getWorld() != world) {
                    host = null;
                } else {
                    at = host.getLocation().add(hostOffset);
                }
            }
            double shudder = phaseAge <= 5 ? Math.sin(phaseAge * 2.6) * 0.28 * (1.0 - phaseAge / 6.0) : 0.0;
            pose(at, heading, spin, shudder);
            if (phaseAge >= LODGE_TICKS) {
                world.spawnParticle(Particle.CRIT, at, 6, 0.12, 0.12, 0.12, 0.08);
                if (host == null) {
                    Block block = at.getBlock();
                    if (block.getType().isSolid()) {
                        world.spawnParticle(Particle.BLOCK, at, 8, 0.12, 0.12, 0.12, 0.08, block.getBlockData());
                    }
                } else if (carriesToll) {
                    world.spawnParticle(Particle.DUST, at, 4, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(BLOOD, 1.0f));
                }
                world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 0.5f, 1.3f);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 1.3f);
                startReturn(at);
            }
        }

        private void startReturn(Location from) {
            phase = Phase.RETURNING;
            phaseAge = 0;
            returnFrom = from.clone();
            host = null;
        }

        /** Spins back along a shallow arc to the thrower's hand, trailing gold (and blood, if it took the toll). */
        void home() {
            if (!player.isOnline() || player.getWorld() != world
                    || player.getLocation().distanceSquared(returnFrom) > 48.0 * 48.0) {
                finish();
                return;
            }
            Location hand = hand();
            double t = Math.min(1.0, phaseAge / (double) RETURN_TICKS);
            double e = t * t * (3.0 - 2.0 * t);
            Vector span = hand.toVector().subtract(returnFrom.toVector());
            Location next = returnFrom.clone().add(span.clone().multiply(e)).add(0, Math.sin(Math.PI * t) * 1.2, 0);
            heading = safeDir(next.toVector().subtract(at.toVector()));
            at = next;
            spin += 1.0;
            pose(at, heading, spin, 0);
            world.spawnParticle(Particle.DUST, at, 1, 0.03, 0.03, 0.03, 0, new Particle.DustOptions(TOLL_GOLD, 0.9f));
            if (carriesToll && phaseAge % 2 == 0) {
                world.spawnParticle(Particle.DUST, at, 1, 0.06, 0.06, 0.06, 0, new Particle.DustOptions(BLOOD, 0.9f));
            }
            if (phaseAge % 3 == 0) {
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.25f, 1.9f);
            }
            if (t >= 1.0) {
                world.playSound(hand, Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.1f);
                world.playSound(hand, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.6f, 1.0f);
                Particle.DustOptions glint = new Particle.DustOptions(TOLL_GOLD, 0.8f);
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10;
                    world.spawnParticle(Particle.DUST, hand.clone().add(Math.cos(a) * 0.35, 0, Math.sin(a) * 0.35), 1,
                            0, 0, 0, 0, glint);
                }
                world.spawnParticle(Particle.CRIT, hand, 4, 0.1, 0.1, 0.1, 0.05);
                finish();
            }
        }

        private Location hand() {
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            Vector right = look.getCrossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1.0E-4) {
                right = new Vector(1, 0, 0);
            }
            right.normalize();
            return eye.add(right.multiply(0.35)).add(look.multiply(0.3)).add(0, -0.45, 0);
        }

        private void pose(Location where, Vector dir, double spinNow, double tilt) {
            Location placed = where.clone();
            placed.setDirection(dir);
            placed.setPitch(0);
            display.teleport(placed);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(axeTransform(spinNow, tilt));
        }

        private void finish() {
            cancel();
            flights.remove(ball.getUniqueId(), this);
            LIVE.remove(display);
            if (display.isValid()) {
                display.remove();
            }
        }
    }
}
