package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Void Stick (aetherion_void_stick) — the Void Drinks.
 * Holding right-click opens a small pitch-black maw at the stick's tip, rimmed in abyssal teal and wrapped in dark
 * haze, that grows as its thirst builds. Ink and glow-ink stream into it; everything in range smears a streak of
 * ink toward the caster as it is dragged; every drinkable foe is tied to the maw by a writhing ink straw with life
 * motes flowing up it. Each drain beat is a gulp: the foe's life is wrung out in red-to-black, fat motes ride the
 * straw into the maw, it swells, and a ripple closes on the caster's chest. Letting go makes it exhale and seal.
 * Pull range, interval, drain and heal are unchanged.
 */
public class VoidStickListener implements Listener {

    private static final double RANGE = 25.0;
    private static final long PULL_INTERVAL_MS = 180L;
    /** The maw closes this many ticks after the last pull beat (held right-click beats every ~4 ticks). */
    private static final int RELEASE_TICKS = 8;
    private static final int MAX_STRAWS = 5;
    private static final int MAX_SMEARS = 12;
    private static final Color INK = Color.fromRGB(14, 8, 24);
    private static final Color ABYSS = Color.fromRGB(18, 120, 125);
    private static final Color GLOW = Color.fromRGB(90, 255, 225);
    private static final Color LIFE = Color.fromRGB(205, 20, 45);
    private static final Display.Brightness VOID = new Display.Brightness(0, 0);

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> lastPull = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pulse = new ConcurrentHashMap<>();
    private final Map<UUID, Drink> drinks = new ConcurrentHashMap<>();

    public VoidStickListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Plugin disable: removes every open maw. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

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
        if (!isVoidStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
        pull(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isVoidStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        pull(player);
    }

    private void pull(Player player) {
        ItemStack stick = player.getInventory().getItemInMainHand();
        int tier = de.aetherion.items.item.DungeonCore.tier(stick);
        long now = System.currentTimeMillis();
        Long last = lastPull.get(player.getUniqueId());
        if (last != null && now - last < de.aetherion.items.item.DungeonCore.voidIntervalMs(tier)) {
            return;
        }
        lastPull.put(player.getUniqueId(), now);
        int beat = pulse.merge(player.getUniqueId(), 1, Integer::sum);
        Drink drink = drinks.get(player.getUniqueId());
        if (drink == null) {
            drink = new Drink(player);
            drinks.put(player.getUniqueId(), drink);
        }
        drink.lastBeat = Bukkit.getCurrentTick();

        List<LivingEntity> drained = new ArrayList<>();
        List<LivingEntity> drinkable = new ArrayList<>();
        int smears = 0;
        Location feet = player.getLocation();
        double range = de.aetherion.items.item.DungeonCore.voidRange(tier);
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity.equals(player) || !entity.isValid()) {
                continue;
            }
            Vector to = player.getLocation().toVector().subtract(entity.getLocation().toVector());
            double dist = to.length();
            if (dist < 0.8 || dist > range) {
                continue;
            }
            to.normalize().multiply(0.04 + (1.0 - dist / range) * 0.045);
            entity.setVelocity(entity.getVelocity().multiply(0.9).add(to));

            if (beat % 2 == 0 && smears < MAX_SMEARS) {
                smears++;
                Vector toward = feet.toVector().subtract(entity.getLocation().toVector()).normalize();
                entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation().add(0, 0.15, 0), 0,
                        toward.getX(), 0.05, toward.getZ(), 0.18);
            }

            if (entity instanceof Item) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !canDrain(living)) {
                continue;
            }
            drinkable.add(living);
            if (beat % 3 != 0) {
                continue;
            }
            double amount = de.aetherion.items.item.DungeonCore.voidDrain(tier);
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(player)
                    .build();
            living.damage(amount, source);
            HealthListener health = AetherionItems.getInstance().getHealthListener();
            if (health != null) {
                health.heal(player, amount * 0.3);
            }
            drained.add(living);
        }
        drinkable.sort(Comparator.comparingDouble(living -> living.getLocation().distanceSquared(feet)));
        drink.straws = new ArrayList<>(drinkable.subList(0, Math.min(MAX_STRAWS, drinkable.size())));
        if (!drained.isEmpty()) {
            drink.gulp(drained);
        }
    }

    private boolean canDrain(LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (AetherEntities.isPet(target)
                || AetherEntities.isBoss(target)
                || AetherEntities.isBossMinion(target)
                || AetherEntities.isSetMinion(target)) {
            return false;
        }
        return true;
    }

    private boolean isVoidStick(ItemStack item) {
        return "aetherion_void_stick".equalsIgnoreCase(itemManager.getItemId(item));
    }

    /** One held channel: the maw, its straws and gulps, from the first beat until the caster lets go. */
    private final class Drink extends BukkitRunnable {
        final Player player;
        final World world;
        final BlockDisplay pupil;
        final BlockDisplay haze;
        final float phase;
        final List<Swallow> swallows = new ArrayList<>();
        List<LivingEntity> straws = new ArrayList<>();
        int lastBeat;
        int age;
        int ripple = -1;
        float swell;

        Drink(Player player) {
            this.player = player;
            this.world = player.getWorld();
            this.phase = (float) ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location maw = maw();
            this.pupil = spawn(maw, Material.BLACK_CONCRETE, VOID, ABYSS);
            this.haze = spawn(maw, Material.TINTED_GLASS, VOID, null);
            world.playSound(maw, Sound.BLOCK_CONDUIT_ACTIVATE, 0.45f, 1.5f);
            world.playSound(maw, Sound.BLOCK_SCULK_CHARGE, 0.5f, 0.5f);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                world.spawnParticle(Particle.GLOW_SQUID_INK, maw, 0, Math.cos(a), Math.sin(a) * 0.4, Math.sin(a), 0.08);
            }
            runTaskTimer(plugin, 1L, 1L);
        }

        @Override
        public void run() {
            boolean held = player.isOnline() && !player.isDead() && player.getWorld() == world
                    && isVoidStick(player.getInventory().getItemInMainHand());
            if (!held) {
                close(false);
                return;
            }
            if (Bukkit.getCurrentTick() - lastBeat > RELEASE_TICKS) {
                close(true);
                return;
            }
            age++;
            float thirst = Math.min(1f, age / 60f);
            swell *= 0.7f;
            Location maw = maw();
            poseMaw(maw, thirst);
            inflow(maw, thirst);
            drawStraws(maw);
            tickSwallows(maw);
            tickRipple();
            if (age % 16 == 0) {
                world.playSound(maw, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.35f, 0.5f + thirst * 0.3f);
            }
        }

        /** The stick's tip, pushed out and down so the maw sits below the crosshair. */
        Location maw() {
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            Vector right = look.getCrossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1.0E-4) {
                right = new Vector(1, 0, 0);
            }
            right.normalize();
            Vector up = right.getCrossProduct(look).normalize();
            Location at = eye.add(look.multiply(1.4)).add(right.multiply(0.3)).add(up.multiply(-0.35));
            at.setYaw(0);
            at.setPitch(0);
            return at;
        }

        void poseMaw(Location maw, float thirst) {
            float size = 0.14f + 0.2f * thirst + swell;
            if (pupil != null && pupil.isValid()) {
                pupil.teleport(maw);
                Quaternionf rot = new Quaternionf().rotateY(age * 0.09f).rotateX(age * 0.05f + phase);
                ease(pupil, centered(size, rot), 1);
            }
            if (haze != null && haze.isValid()) {
                haze.teleport(maw);
                Quaternionf rot = new Quaternionf().rotateY(-age * 0.06f + phase).rotateZ(age * 0.04f);
                ease(haze, centered(size * 1.55f, rot), 1);
            }
        }

        /** Ink and glow-ink drawn in from all around, plus a teal whirl spiralling into the pupil. */
        void inflow(Location maw, float thirst) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int count = thirst > 0.5f ? 3 : 2;
            for (int i = 0; i < count; i++) {
                Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                double r = 0.7 + 0.4 * thirst;
                Location from = maw.clone().add(dir.clone().multiply(r));
                Particle ink = i == 0 && age % 2 == 0 ? Particle.GLOW_SQUID_INK : Particle.SQUID_INK;
                world.spawnParticle(ink, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), 0.09);
            }
            Vector look = player.getEyeLocation().getDirection().normalize();
            Vector a = look.getCrossProduct(new Vector(0, 1, 0));
            if (a.lengthSquared() < 1.0E-4) {
                a = new Vector(1, 0, 0);
            }
            a.normalize();
            Vector b = a.getCrossProduct(look).normalize();
            Particle.DustOptions whirl = new Particle.DustOptions(ABYSS, 0.5f);
            for (int k = 0; k < 3; k++) {
                double s = (age * 0.08 + k / 3.0) % 1.0;
                double r = (0.35 + 0.3 * thirst) * (1.0 - s);
                double ang = age * 0.5 + k * 2.09 + s * 3.0;
                Location p = maw.clone().add(a.clone().multiply(Math.cos(ang) * r)).add(b.clone().multiply(Math.sin(ang) * r));
                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, whirl);
            }
        }

        /** A writhing ink straw from each tethered foe to the maw, a life mote riding up each one. */
        void drawStraws(Location maw) {
            for (int s = 0; s < straws.size(); s++) {
                LivingEntity target = straws.get(s);
                if (!target.isValid() || target.isDead() || target.getWorld() != world) {
                    continue;
                }
                Location from = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                Vector span = maw.toVector().subtract(from.toVector());
                double len = span.length();
                if (len < 0.8) {
                    continue;
                }
                Vector side = span.getCrossProduct(new Vector(0, 1, 0));
                if (side.lengthSquared() < 1.0E-4) {
                    side = new Vector(1, 0, 0);
                }
                side.normalize();
                double wobblePhase = phase + s * 1.7;
                if ((age + s) % 2 == 0) {
                    int n = (int) Math.max(6, Math.min(18, len / 0.6));
                    for (int i = 1; i < n; i++) {
                        double t = i / (double) n;
                        world.spawnParticle(Particle.DUST, strawPoint(from, span, side, t, wobblePhase), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(mix(INK, ABYSS, t), (float) (1.0 - t * 0.35)));
                    }
                }
                double bead = (age * 0.11 + s * 0.23) % 1.0;
                Location mote = strawPoint(from, span, side, bead, wobblePhase);
                world.spawnParticle(Particle.DUST, mote, 1, 0, 0, 0, 0, new Particle.DustOptions(LIFE, 1.2f));
                world.spawnParticle(Particle.DUST, mote, 1, 0.03, 0.03, 0.03, 0, new Particle.DustOptions(GLOW, 0.45f));
                if (age % 4 == s % 4) {
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, from, 2, 0.15, 0.2, 0.15, 0,
                            new Particle.DustTransition(LIFE, INK, 0.9f));
                }
            }
        }

        Location strawPoint(Location from, Vector span, Vector side, double t, double wobblePhase) {
            double wobble = Math.sin(t * Math.PI * 2.5 - age * 0.6 + wobblePhase) * 0.35 * Math.sin(Math.PI * t);
            double sag = -0.4 * Math.sin(Math.PI * t);
            return from.clone().add(span.clone().multiply(t)).add(side.clone().multiply(wobble)).add(0, sag, 0);
        }

        /** A drain beat: life wrung out of each foe, fat motes launched up the straws, a gulp and a chest ripple. */
        void gulp(List<LivingEntity> drained) {
            Location maw = maw();
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.7f, 0.55f);
            world.playSound(maw, Sound.BLOCK_SCULK_CHARGE, 0.35f, 0.6f);
            int shown = 0;
            for (LivingEntity target : drained) {
                if (shown++ >= MAX_STRAWS) {
                    break;
                }
                Location chest = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, chest, 6, 0.25, 0.3, 0.25, 0,
                        new Particle.DustTransition(LIFE, INK, 1.3f));
                world.spawnParticle(Particle.SQUID_INK, chest, 3, 0.2, 0.2, 0.2, 0.02);
                if (swallows.size() < MAX_STRAWS * 2) {
                    swallows.add(new Swallow(chest));
                }
            }
            ripple = 0;
        }

        void tickSwallows(Location maw) {
            Iterator<Swallow> it = swallows.iterator();
            while (it.hasNext()) {
                Swallow swallow = it.next();
                swallow.age++;
                double t = Math.min(1.0, swallow.age / 5.0);
                Vector span = maw.toVector().subtract(swallow.from.toVector());
                Location at = swallow.from.clone().add(span.clone().multiply(t * t));
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(LIFE, 1.6f));
                world.spawnParticle(Particle.DUST, at.clone().subtract(span.clone().multiply(0.06)), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(INK, 1.1f));
                if (t >= 1.0) {
                    swell = Math.min(0.2f, swell + 0.08f);
                    world.spawnParticle(Particle.GLOW_SQUID_INK, maw, 1, 0.05, 0.05, 0.05, 0.01);
                    it.remove();
                }
            }
        }

        /** A teal ring closing in on the caster's chest after each gulp: the drink going down. */
        void tickRipple() {
            if (ripple < 0) {
                return;
            }
            ripple++;
            if (ripple > 4) {
                ripple = -1;
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.1, 0), 3, 0.15, 0.2, 0.15, 0,
                        new Particle.DustOptions(LIFE, 0.8f));
                return;
            }
            double r = 1.1 * (1.0 - ripple / 5.0);
            Location chest = player.getLocation().add(0, 1.1, 0);
            Particle.DustOptions dust = new Particle.DustOptions(ABYSS, 0.8f);
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2 * i / 14 + ripple * 0.3;
                world.spawnParticle(Particle.DUST, chest.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, dust);
            }
        }

        /** Seals the maw; {@code exhale} plays the release (the caster let go) instead of just vanishing. */
        void close(boolean exhale) {
            cancel();
            drinks.remove(player.getUniqueId(), this);
            if (exhale && player.isOnline()) {
                Location maw = maw();
                ease(pupil, centered(0.01f, new Quaternionf()), 3);
                ease(haze, centered(0.5f, new Quaternionf().rotateY(age * 0.06f)), 2);
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10;
                    world.spawnParticle(Particle.GLOW_SQUID_INK, maw, 0, Math.cos(a), 0.2, Math.sin(a), 0.15);
                }
                world.spawnParticle(Particle.SQUID_INK, maw, 6, 0.15, 0.15, 0.15, 0.03);
                world.playSound(maw, Sound.ENTITY_GLOW_SQUID_SQUIRT, 0.5f, 0.6f);
                world.playSound(maw, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.4f, 0.9f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    discard(pupil);
                    discard(haze);
                }, 4L);
            } else {
                discard(pupil);
                discard(haze);
            }
        }
    }

    private static final class Swallow {
        final Location from;
        int age;

        Swallow(Location from) {
            this.from = from;
        }
    }

    private static Transformation centered(float size, Quaternionf rot) {
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(size / 2f, size / 2f, size / 2f));
        return new Transformation(half.negate(), rot, new Vector3f(size, size, size), new Quaternionf());
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, Material material, Display.Brightness light, Color glow) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setBrightness(light);
                spawned.setTeleportDuration(1);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(centered(0.01f, new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }
}
