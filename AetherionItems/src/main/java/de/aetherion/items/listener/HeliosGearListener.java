package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helios's loot.
 *
 * <p><b>Solstice, Constellation.</b> Right-click: a cyan portal opens at your hand and its magenta twin
 * above the point you aim at (up to 24 blocks). A thread of light links them; on the beat your light
 * goes in one and falls out of the other as a lance of sunlight, burning everything under it. Players,
 * pets and bosses' minions are never touched; bosses take the hit like any sword swing would.
 *
 * <p><b>Dawnbearer, full set: Second Dawn.</b> Dropping below 30 % health with all four pieces, you go
 * supernova: heal 30 %, Absorption II, a ring of light that throws nearby enemies back and burns them.
 * Once every two minutes.
 */
public class HeliosGearListener implements Listener {

    private static final long SOLSTICE_CD_MS = 10_000L;
    private static final long DAWN_CD_MS = 120_000L;
    private static final double LANCE_DAMAGE = 70.0;
    private static final double LANCE_RADIUS = 2.8;
    private static final double NOVA_DAMAGE = 45.0;
    private static final double NOVA_RADIUS = 6.0;
    private static final Color CYAN = Color.fromRGB(80, 220, 255);
    private static final Color MAGENTA = Color.fromRGB(255, 90, 220);
    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final String[] SET = {"helios_crown", "helios_heartplate", "helios_orbit_greaves", "helios_dawn_treads"};

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> lastLance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDawn = new ConcurrentHashMap<>();

    public HeliosGearListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ================================================================== Solstice */

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!"helios_solstice".equalsIgnoreCase(itemManager.getItemId(player.getInventory().getItemInMainHand()))) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastLance.get(player.getUniqueId());
        if (last != null && now - last < SOLSTICE_CD_MS) {
            long left = (SOLSTICE_CD_MS - (now - last) + 999) / 1000;
            player.sendActionBar(net.kyori.adventure.text.Component.text("Constellation: " + left + "s",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
            return;
        }
        lastLance.put(player.getUniqueId(), now);
        constellation(player);
    }

    private void constellation(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), 24.0, FluidCollisionMode.NEVER, true, 0.4,
                e -> e instanceof LivingEntity && !e.equals(player));
        Location target = hit == null ? eye.clone().add(eye.getDirection().multiply(24.0))
                : hit.getHitPosition().toLocation(world);
        // Drop the aim point onto the ground below it.
        RayTraceResult ground = world.rayTraceBlocks(target.clone().add(0, 0.5, 0), new Vector(0, -1, 0), 12.0, FluidCollisionMode.NEVER, true);
        if (ground != null) {
            target = ground.getHitPosition().toLocation(world);
        }
        Location hand = eye.clone().add(eye.getDirection().multiply(1.2)).add(0, -0.3, 0);
        Location above = target.clone().add(0, 6.0, 0);
        List<Display> props = new ArrayList<>();
        BlockDisplay beam = display(world, above, Material.WHITE_CONCRETE, GOLD);
        props.add(beam);
        world.playSound(hand, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1f, 1.2f);
        world.playSound(above, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1f, 0.9f);
        final Location fTarget = target;
        for (int k = 0; k <= 14; k++) {
            final int t = k;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                ring(world, hand, CYAN, 0.6, t);
                ring(world, above, MAGENTA, 1.1, t);
                if (t < 8) {
                    // The thread: light-points flowing from the hand's portal to its twin.
                    thread(world, hand, above, t);
                }
                if (t == 8) {
                    world.playSound(fTarget, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.8f);
                    world.playSound(fTarget, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5f, 1.6f);
                    lance(beam, above, fTarget, 0.9f, 1);
                    strike(player, world, fTarget);
                }
                if (t == 12) {
                    lance(beam, above, fTarget, 0.02f, 3);
                }
            }, k);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> props.forEach(Entity::remove), 18L);
    }

    private void strike(Player player, World world, Location at) {
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5, 0), 40, 1.2, 0.3, 1.2, 0.05, null, true);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 30, 1.6, 0.1, 1.6, 0, new Particle.DustOptions(GOLD, 1.6f), true);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build();
        for (Entity e : world.getNearbyEntities(at, LANCE_RADIUS, 4.0, LANCE_RADIUS)) {
            if (e instanceof LivingEntity living && !e.equals(player) && canHit(living)) {
                living.damage(LANCE_DAMAGE, source);
                living.setFireTicks(Math.max(living.getFireTicks(), 60));
            }
        }
    }

    private static void ring(World world, Location c, Color color, double r, int t) {
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + t * 0.35;
            Location p = c.clone().add(Math.cos(a) * r, Math.sin(a) * r * 0.25, Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f), true);
        }
    }

    private static void thread(World world, Location a, Location b, int t) {
        Vector d = b.toVector().subtract(a.toVector());
        for (int i = 0; i < 4; i++) {
            double f = ((t / 8.0) + i / 4.0) % 1.0;
            Location p = a.clone().add(d.clone().multiply(f));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 0.9f), true);
        }
    }

    private BlockDisplay display(World world, Location at, Material m, Color glow) {
        return world.spawn(at, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(m.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f), new Quaternionf()));
        });
    }

    /** A vertical lance from {@code top} down to {@code floor}, drawn by the display sitting at {@code top}. */
    private static void lance(BlockDisplay d, Location top, Location floor, float width, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        float len = (float) Math.max(0.5, top.getY() - floor.getY());
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interp);
        d.setTransformation(new Transformation(new Vector3f(-width / 2f, -len, -width / 2f), new Quaternionf(),
                new Vector3f(width, len, width), new Quaternionf()));
    }

    /* ================================================================== Dawnbearer: Second Dawn */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !wearsSet(player)) {
            return;
        }
        // Check on the next tick, after every damage handler has settled the health.
        plugin.getServer().getScheduler().runTask(plugin, () -> secondDawn(player));
    }

    @SuppressWarnings("deprecation")
    private void secondDawn(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        double max = Math.max(1.0, player.getMaxHealth());
        if (player.getHealth() / max >= 0.30) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastDawn.get(player.getUniqueId());
        if (last != null && now - last < DAWN_CD_MS) {
            return;
        }
        lastDawn.put(player.getUniqueId(), now);
        World world = player.getWorld();
        Location at = player.getLocation();
        HealthListener health = AetherionItems.getInstance().getHealthListener();
        if (health != null) {
            health.heal(player, max * 0.30);
        } else {
            player.setHealth(Math.min(max, player.getHealth() + max * 0.30));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 30, 0, false, false, false));
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.4f);
        world.playSound(at, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8f, 1.3f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.15, null, true);
        DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build();
        for (int k = 0; k < 8; k++) {
            final double r = 1.0 + k * (NOVA_RADIUS / 8.0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    Location p = at.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.4f), true);
                }
            }, k);
        }
        for (Entity e : world.getNearbyEntities(at, NOVA_RADIUS, 3.0, NOVA_RADIUS)) {
            if (e instanceof LivingEntity living && !e.equals(player) && canHit(living)) {
                living.damage(NOVA_DAMAGE, source);
                living.setFireTicks(Math.max(living.getFireTicks(), 40));
                Vector push = living.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (push.lengthSquared() > 1e-4) {
                    living.setVelocity(push.normalize().multiply(0.9).setY(0.35));
                }
            }
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("Second Dawn", net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    private boolean wearsSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null || armor.length < 4) {
            return false;
        }
        // Armor contents run boots -> helmet.
        for (int i = 0; i < 4; i++) {
            if (!SET[3 - i].equalsIgnoreCase(itemManager.getItemId(armor[i]))) {
                return false;
            }
        }
        return true;
    }

    private static boolean canHit(LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !(AetherEntities.isPet(target) || AetherEntities.isBossMinion(target) || AetherEntities.isSetMinion(target));
    }
}
