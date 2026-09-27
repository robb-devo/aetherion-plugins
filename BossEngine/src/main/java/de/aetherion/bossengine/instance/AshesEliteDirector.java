package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floor 3 (Throne of Ashes) elites. Throne servants, not second endbosses.
 *
 * <p>The Chainwarden jails: shackle a single player, lock a cage around himself, sweep a half-circle.
 * The Cinder Herald speaks for the burned sky: ember bolts, cinder rain, an ember lance, and
 * "Burned Sky" — a red field where only two blue soul wards are safe (the Sovereign's lesson, small).
 *
 * <p>Tell language matches the Sovereign: red = danger, white = fires now, blue = soul ward (safe).
 * Death cinematics leave the body valid so BossManager's kill fires a vanilla death event.
 */
final class AshesEliteDirector {

    static final String CHAINWARDEN = "ashen_chainwarden";
    static final String HERALD = "cinder_herald";

    private static final Color EMBER = Color.fromRGB(255, 122, 32);
    private static final Color CINDER = Color.fromRGB(255, 70, 20);
    private static final Color GOLD = Color.fromRGB(255, 196, 64);
    private static final Color CRIMSON = Color.fromRGB(205, 18, 40);
    private static final Color ASH = Color.fromRGB(96, 90, 88);
    private static final Color IRON = Color.fromRGB(58, 56, 60);
    private static final Color SOUL = Color.fromRGB(90, 210, 255);
    private static final Color WHITE = Color.fromRGB(255, 245, 235);

    private static final int SHACKLE_FOLLOW = 30;
    private static final int SHACKLE_LOCK = 14;
    private static final double SHACKLE_RADIUS = 1.9;
    private static final int LOCKDOWN_WIND = 40;
    private static final double LOCKDOWN_HALF = 4.5;
    private static final int SWEEP_WIND = 24;
    private static final double SWEEP_RADIUS = 7.0;

    private static final int BOLT_WIND = 14;
    private static final int RAIN_WIND = 30;
    private static final int RAIN_STAGGER = 3;
    private static final double RAIN_RADIUS = 2.2;
    private static final int SKY_WIND = 70;
    private static final double SKY_RADIUS = 12.0;
    private static final double WARD_RADIUS = 2.2;
    private static final int LANCE_WIND = 26;
    private static final double LANCE_LENGTH = 16.0;
    private static final double LANCE_WIDTH = 1.6;
    private static final double HOVER = 2.4;

    private static final int WARDEN_DEATH = 64;
    private static final int HERALD_DEATH = 72;
    private static final Location THRONE = new Location(null, 266.5, 97.5, -70.5);

    private enum Kind { NONE, CHAINWARDEN, HERALD }

    private enum Cast { IDLE, SHACKLE, LOCKDOWN, SWEEP, BOLT, RAIN, SKY, LANCE }

    private final BossInstance instance;
    private final List<Entity> fx = new ArrayList<>();
    private final List<Location> marks = new ArrayList<>();
    private final List<Location> wards = new ArrayList<>();
    private final boolean[] skyUsed = new boolean[2];

    private Cast cast = Cast.IDLE;
    private int castTick;
    private int castCd;
    private int boltCd;
    private int rotation;
    private boolean enraged;
    private UUID castTarget;
    private Location castAt;
    private Vector castDir;
    private double lift;

    private int deathTicks = -1;
    private Location deathFocus;
    private Location mote;

    AshesEliteDirector(BossInstance instance) {
        this.instance = instance;
    }

    private Kind kind() {
        if (instance.getTemplate() == null || instance.getTemplate().getId() == null) {
            return Kind.NONE;
        }
        return switch (instance.getTemplate().getId().toLowerCase(Locale.ROOT)) {
            case CHAINWARDEN -> Kind.CHAINWARDEN;
            case HERALD -> Kind.HERALD;
            default -> Kind.NONE;
        };
    }

    boolean isMine() {
        return kind() != Kind.NONE;
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    void onBind() {
        if (!isMine()) {
            return;
        }
        clearFx();
        endCast();
        castCd = 60;
        boltCd = 30;
        rotation = 0;
        lift = 0;
        enraged = instance.healthPercent() <= 50.0;
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        if (kind() == Kind.CHAINWARDEN) {
            dressJailer(entity);
        } else {
            entity.setGravity(false);
        }
    }

    void abort() {
        clearFx();
        endCast();
        deathTicks = -1;
        mote = null;
    }

    /** Removes cinematic props only; safe to call from despawn paths. */
    void clearFx() {
        Iterator<Entity> iterator = fx.iterator();
        while (iterator.hasNext()) {
            Entity prop = iterator.next();
            if (prop != null && prop.isValid()) {
                prop.remove();
            }
            iterator.remove();
        }
    }

    boolean beginDeath() {
        if (!isMine() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        endCast();
        deathFocus = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : instance.getSpawnLocation().clone();
        if (deathFocus.getWorld() == null) {
            return false;
        }
        deathTicks = 0;
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
        }
        World world = deathFocus.getWorld();
        if (kind() == Kind.CHAINWARDEN) {
            world.playSound(deathFocus, Sound.ENTITY_WITHER_SKELETON_DEATH, 1.2f, 0.5f);
            world.playSound(deathFocus, Sound.BLOCK_CHAIN_BREAK, 1.3f, 0.5f);
            shout("&8&lThe Chainwarden&7: &fNo… the cells were… mine to keep.");
            spawnChainRing(entity);
        } else {
            world.playSound(deathFocus, Sound.ENTITY_BLAZE_HURT, 1.2f, 0.45f);
            world.playSound(deathFocus, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.5f);
            shout("&6&lCinder Herald&7: &eMy voice… goes on without me.");
            mote = deathFocus.clone().add(0, entity != null ? entity.getHeight() * 0.5 : 1.5, 0);
        }
        return true;
    }

    boolean tick() {
        if (!isMine()) {
            return false;
        }
        if (isDying()) {
            return kind() == Kind.CHAINWARDEN ? tickWardenDeath() : tickHeraldDeath();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        if (instance.isTransitioning()) {
            if (cast != Cast.IDLE) {
                endCast();
            }
            return false;
        }
        if (!enraged && instance.healthPercent() <= 50.0) {
            enraged = true;
            shout(kind() == Kind.CHAINWARDEN
                    ? "&8&lThe Chainwarden&7: &cEvery cell has room for one more."
                    : "&6&lCinder Herald&7: &eListen — &cthe sky is still burning.");
        }
        if (kind() == Kind.CHAINWARDEN) {
            tickWarden(entity);
        } else {
            tickHerald(entity);
        }
        return false;
    }

    // ------------------------------------------------------------------ Chainwarden

    private void tickWarden(LivingEntity entity) {
        wardenAmbient(entity);
        if (cast != Cast.IDLE) {
            freeze(entity);
            castTick++;
            switch (cast) {
                case SHACKLE -> tickShackle(entity);
                case LOCKDOWN -> tickLockdown(entity);
                case SWEEP -> tickSweep(entity);
                default -> endCast();
            }
            return;
        }
        if (castCd > 0) {
            castCd--;
            return;
        }
        Player near = nearest(entity, 22);
        if (near == null) {
            return;
        }
        Cast next = switch (rotation++ % 3) {
            case 0 -> Cast.SHACKLE;
            case 1 -> Cast.LOCKDOWN;
            default -> enraged ? Cast.SWEEP : Cast.SHACKLE;
        };
        startWardenCast(entity, next, near);
    }

    private void startWardenCast(LivingEntity entity, Cast next, Player near) {
        World world = entity.getWorld();
        Location at = entity.getLocation();
        cast = next;
        castTick = 0;
        switch (next) {
            case SHACKLE -> {
                Player pick = randomTarget(entity, 20);
                Player target = pick != null ? pick : near;
                castTarget = target.getUniqueId();
                castAt = ground(target.getLocation());
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1.3f, 0.5f);
                world.playSound(at, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.55f);
                target.sendActionBar(TextUtil.component("&cShackled! &7Move out of the ring before it locks."));
            }
            case LOCKDOWN -> {
                castAt = ground(at);
                world.playSound(at, Sound.BLOCK_IRON_DOOR_CLOSE, 1.3f, 0.5f);
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.6f);
                shout("&8&lThe Chainwarden&7: &cLockdown. &7(&fleave the cage&7)");
            }
            case SWEEP -> {
                castAt = ground(at);
                castDir = flat(near.getLocation().toVector().subtract(at.toVector()));
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.45f);
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.45f);
            }
            default -> endCast();
        }
    }

    private void tickShackle(LivingEntity entity) {
        World world = entity.getWorld();
        Player target = castTarget == null ? null : org.bukkit.Bukkit.getPlayer(castTarget);
        int follow = enraged ? SHACKLE_FOLLOW - 6 : SHACKLE_FOLLOW;
        Location hand = entity.getLocation().clone().add(0, entity.getHeight() * 0.62, 0);
        if (castTick <= follow) {
            if (target != null && instance.isCombatTarget(target) && target.getWorld().equals(world)) {
                castAt = lerp(castAt, ground(target.getLocation()), 0.35);
                face(entity, target.getLocation());
            }
            ring(castAt, SHACKLE_RADIUS, CRIMSON, 22, 1.3f);
            if (castTick % 2 == 0) {
                chainLine(hand, castAt.clone().add(0, 0.3, 0), IRON);
            }
            if (castTick % 8 == 0) {
                world.playSound(castAt, Sound.BLOCK_CHAIN_STEP, 1.0f, 0.6f + castTick * 0.02f);
            }
            if (castTick == follow) {
                world.playSound(castAt, Sound.BLOCK_CHAIN_HIT, 1.3f, 0.5f);
            }
            return;
        }
        int lock = castTick - follow;
        boolean strobe = SHACKLE_LOCK - lock < 6;
        Color edge = strobe && lock % 2 == 0 ? WHITE : CRIMSON;
        ring(castAt, SHACKLE_RADIUS, edge, 26, 1.5f);
        ring(castAt, SHACKLE_RADIUS * (1.0 - lock / (double) SHACKLE_LOCK), CRIMSON, 12, 1.0f);
        chainLine(hand, castAt.clone().add(0, 0.3, 0), strobe ? WHITE : IRON);
        if (lock < SHACKLE_LOCK) {
            return;
        }
        boolean caught = false;
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player) || horizontal(player.getLocation(), castAt) > SHACKLE_RADIUS
                    || Math.abs(player.getLocation().getY() - castAt.getY()) > 3.0) {
                continue;
            }
            caught = true;
            BossHits.hurt(player, entity, 45);
            Vector pull = entity.getLocation().toVector().subtract(player.getLocation().toVector());
            pull = flat(pull).multiply(1.35).setY(0.38);
            player.setVelocity(pull);
            player.addPotionEffect(new PotionEffect(slowness(), 40, 1, false, true, true));
            chainLine(hand, player.getLocation().add(0, 1.0, 0), WHITE);
            world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.2f, 0.6f);
        }
        if (caught) {
            world.playSound(castAt, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            world.spawnParticle(Particle.BLOCK, castAt.clone().add(0, 0.2, 0), 30, 0.6, 0.1, 0.6, 0.1, Material.CHAIN.createBlockData());
        } else {
            world.playSound(castAt, Sound.BLOCK_CHAIN_FALL, 1.1f, 0.7f);
            world.spawnParticle(Particle.SMOKE, castAt.clone().add(0, 0.2, 0), 14, 0.5, 0.05, 0.5, 0.01);
        }
        finishCast(enraged ? 60 : 85);
    }

    private void tickLockdown(LivingEntity entity) {
        World world = entity.getWorld();
        double t = castTick / (double) LOCKDOWN_WIND;
        boolean strobe = LOCKDOWN_WIND - castTick < 8;
        if (castTick % 2 == 0) {
            Color floor = strobe && castTick % 4 == 0 ? WHITE : EMBER;
            square(castAt, LOCKDOWN_HALF, floor, 0.75, 0.12);
            cageBars(castAt, LOCKDOWN_HALF, 5.0 * (1.0 - t), strobe ? WHITE : ASH);
        }
        if (castTick % 5 == 0) {
            world.playSound(castAt, Sound.BLOCK_CHAIN_STEP, 1.1f, 0.5f + (float) t);
        }
        if (castTick == LOCKDOWN_WIND - 8) {
            world.playSound(castAt, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.3f, 0.5f);
        }
        if (castTick < LOCKDOWN_WIND) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Location p = player.getLocation();
            if (Math.abs(p.getX() - castAt.getX()) > LOCKDOWN_HALF || Math.abs(p.getZ() - castAt.getZ()) > LOCKDOWN_HALF
                    || Math.abs(p.getY() - castAt.getY()) > 3.5) {
                continue;
            }
            BossHits.hurt(player, entity, 60);
            player.setVelocity(player.getVelocity().add(new Vector(0, 0.55, 0)));
        }
        for (double d = -LOCKDOWN_HALF; d <= LOCKDOWN_HALF; d += 1.5) {
            for (Location corner : new Location[]{
                    castAt.clone().add(d, 0.2, -LOCKDOWN_HALF), castAt.clone().add(d, 0.2, LOCKDOWN_HALF),
                    castAt.clone().add(-LOCKDOWN_HALF, 0.2, d), castAt.clone().add(LOCKDOWN_HALF, 0.2, d)}) {
                world.spawnParticle(Particle.BLOCK, corner, 4, 0.15, 0.1, 0.15, 0.08, Material.BLACKSTONE.createBlockData());
            }
        }
        world.spawnParticle(Particle.EXPLOSION, castAt.clone().add(0, 0.5, 0), 2, 1.5, 0.2, 1.5, 0);
        world.spawnParticle(Particle.WHITE_ASH, castAt.clone().add(0, 1.0, 0), 80, LOCKDOWN_HALF * 0.6, 1.0, LOCKDOWN_HALF * 0.6, 0);
        world.playSound(castAt, Sound.BLOCK_ANVIL_LAND, 1.1f, 0.5f);
        world.playSound(castAt, Sound.BLOCK_IRON_DOOR_CLOSE, 1.3f, 0.45f);
        world.playSound(castAt, Sound.BLOCK_CHAIN_BREAK, 1.2f, 0.5f);
        finishCast(enraged ? 70 : 95);
    }

    private void tickSweep(LivingEntity entity) {
        World world = entity.getWorld();
        Location at = entity.getLocation();
        face(entity, at.clone().add(castDir));
        boolean strobe = SWEEP_WIND - castTick < 6;
        if (castTick % 2 == 0) {
            cone(castAt, castDir, SWEEP_RADIUS, strobe ? WHITE : CRIMSON);
        }
        if (castTick < SWEEP_WIND) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Vector to = player.getLocation().toVector().subtract(castAt.toVector());
            to.setY(0);
            if (to.length() > SWEEP_RADIUS || Math.abs(player.getLocation().getY() - castAt.getY()) > 3.0) {
                continue;
            }
            if (to.lengthSquared() > 0.01 && to.clone().normalize().dot(castDir) < 0.0) {
                continue;
            }
            BossHits.hurt(player, entity, 50);
            Vector push = to.lengthSquared() < 0.01 ? castDir.clone() : to.normalize();
            player.setVelocity(push.multiply(1.1).setY(0.4));
        }
        for (int i = -6; i <= 6; i++) {
            Vector dir = rotate(castDir, i * 15.0);
            world.spawnParticle(Particle.SWEEP_ATTACK, castAt.clone().add(dir.multiply(4.0)).add(0, 1.0, 0), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ITEM, castAt.clone().add(rotate(castDir, i * 15.0).multiply(5.5)).add(0, 0.8, 0),
                    1, 0.1, 0.1, 0.1, 0.05, new ItemStack(Material.CHAIN));
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
        world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 1.1f, 0.6f);
        finishCast(70);
    }

    private void wardenAmbient(LivingEntity entity) {
        long t = instance.getTicksAlive();
        if (t % 6 != 0) {
            return;
        }
        World world = entity.getWorld();
        Location feet = entity.getLocation();
        world.spawnParticle(Particle.DUST, feet.clone().add(0, 0.15, 0), 3, 0.5, 0.05, 0.5, new Particle.DustOptions(ASH, 1.4f));
        world.spawnParticle(Particle.SMALL_FLAME, feet.clone().add(0, entity.getHeight() * 0.55, 0), 1, 0.35, 0.3, 0.35, 0.005);
        if (enraged) {
            world.spawnParticle(Particle.DUST, feet.clone().add(0, entity.getHeight() * 0.8, 0), 2, 0.3, 0.3, 0.3,
                    new Particle.DustOptions(CRIMSON, 1.2f));
        }
        if (t % 60 == 0) {
            world.playSound(feet, Sound.BLOCK_CHAIN_STEP, 0.6f, 0.5f);
        }
    }

    private void dressJailer(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(trimmed(Material.NETHERITE_HELMET, TrimMaterial.GOLD, TrimPattern.RIB));
        equipment.setChestplate(trimmed(Material.NETHERITE_CHESTPLATE, TrimMaterial.REDSTONE, TrimPattern.SILENCE));
        equipment.setLeggings(trimmed(Material.CHAINMAIL_LEGGINGS, TrimMaterial.NETHERITE, TrimPattern.RIB));
        equipment.setBoots(trimmed(Material.NETHERITE_BOOTS, TrimMaterial.GOLD, TrimPattern.SNOUT));
        equipment.setItemInMainHand(new ItemStack(Material.CHAIN));
        equipment.setItemInOffHand(new ItemStack(Material.TRIAL_KEY));
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
        equipment.setItemInOffHandDropChance(0f);
    }

    private void spawnChainRing(LivingEntity entity) {
        if (deathFocus.getWorld() == null) {
            return;
        }
        double height = entity != null ? entity.getHeight() * 0.55 : 1.6;
        for (int i = 0; i < 6; i++) {
            Location at = deathFocus.clone().add(0, height, 0);
            ItemDisplay link = deathFocus.getWorld().spawn(at, ItemDisplay.class, display -> {
                display.setItemStack(new ItemStack(Material.CHAIN));
                display.setPersistent(false);
                display.setBrightness(new Display.Brightness(12, 12));
                display.setInterpolationDuration(3);
                display.setTeleportDuration(2);
            });
            fx.add(link);
        }
        Location keyAt = deathFocus.clone().add(0, height + 0.4, 0);
        ItemDisplay key = deathFocus.getWorld().spawn(keyAt, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.TRIAL_KEY));
            display.setPersistent(false);
            display.setGlowing(true);
            display.setGlowColorOverride(GOLD);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(2);
        });
        fx.add(key);
    }

    /** 0-30 chains bind tight and spin; 30 snap outward; keys fall to the ash; 64 end. */
    private boolean tickWardenDeath() {
        deathTicks++;
        World world = deathFocus.getWorld();
        LivingEntity entity = instance.getEntity();
        if (world == null) {
            clearFx();
            return true;
        }
        if (entity != null && entity.isValid()) {
            entity.teleport(deathFocus);
            entity.setVelocity(new Vector(0, 0, 0));
        }
        double height = entity != null && entity.isValid() ? entity.getHeight() * 0.55 : 1.6;
        Location core = deathFocus.clone().add(0, height, 0);
        int links = Math.min(6, fx.size());
        for (int i = 0; i < links; i++) {
            Entity prop = fx.get(i);
            if (!(prop instanceof ItemDisplay link) || !link.isValid()) {
                continue;
            }
            double angle = Math.PI * 2 * i / 6.0 + deathTicks * (deathTicks < 30 ? 0.12 + deathTicks * 0.01 : 0.05);
            double radius = deathTicks < 30 ? 1.8 - deathTicks * 0.035 : 0.75 + (deathTicks - 30) * 0.45;
            double y = deathTicks < 30 ? Math.sin(deathTicks * 0.3 + i) * 0.25 : (deathTicks - 30) * 0.08 - (deathTicks - 30) * (deathTicks - 30) * 0.006;
            Location at = core.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            at.setYaw((float) Math.toDegrees(-angle));
            link.teleport(at);
            float scale = deathTicks < 44 ? 0.9f : Math.max(0.01f, 0.9f - (deathTicks - 44) * 0.06f);
            link.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) angle, 0, 0, 1),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            if (deathTicks > 44 && scale <= 0.02f) {
                link.remove();
            }
        }
        if (deathTicks < 30) {
            world.spawnParticle(Particle.DUST, core, 6, 0.6, 0.6, 0.6, new Particle.DustOptions(IRON, 1.3f));
            if (deathTicks % 6 == 0) {
                world.playSound(core, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f + deathTicks * 0.03f);
            }
        }
        if (deathTicks == 30) {
            world.playSound(core, Sound.BLOCK_CHAIN_BREAK, 1.5f, 0.45f);
            world.playSound(core, Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.6f);
            world.playSound(core, Sound.ENTITY_WITHER_SKELETON_DEATH, 1.0f, 0.45f);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.BLOCK, core, 60, 0.6, 0.8, 0.6, 0.2, Material.CHAIN.createBlockData());
            world.spawnParticle(Particle.WHITE_ASH, core, 90, 1.2, 1.2, 1.2, 0.02);
            ring(ground(deathFocus), 4.0, EMBER, 36, 1.6f);
        }
        if (deathTicks > 30 && deathTicks < 52) {
            world.spawnParticle(Particle.WHITE_ASH, core.clone().add(0, 1.5, 0), 20, 2.0, 0.5, 2.0, 0);
        }
        Entity keyProp = fx.size() > 6 ? fx.get(6) : null;
        if (keyProp instanceof ItemDisplay key && key.isValid()) {
            Location floor = ground(deathFocus).add(0.4, 0.15, 0.3);
            if (deathTicks <= 30) {
                key.teleport(core.clone().add(0, 0.4 + Math.sin(deathTicks * 0.25) * 0.1, 0));
            } else {
                double fall = Math.min(1.0, (deathTicks - 30) / 12.0);
                Location from = core.clone().add(0, 0.4, 0);
                Location at = lerp(from, floor, fall * fall);
                at.setYaw(deathTicks * 9f);
                key.teleport(at);
                if (deathTicks == 42) {
                    world.playSound(floor, Sound.BLOCK_BELL_USE, 1.2f, 1.6f);
                    world.playSound(floor, Sound.BLOCK_CHAIN_FALL, 1.0f, 1.2f);
                    world.spawnParticle(Particle.DUST, floor, 20, 0.3, 0.05, 0.3, new Particle.DustOptions(GOLD, 1.2f));
                    shout("&8&o…the keys fall into the ash. &7The cells stand open.");
                }
            }
        }
        if (deathTicks >= WARDEN_DEATH) {
            world.playSound(deathFocus, Sound.BLOCK_IRON_DOOR_OPEN, 1.3f, 0.55f);
            clearFx();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Cinder Herald

    private void tickHerald(LivingEntity entity) {
        freeze(entity);
        heraldAmbient(entity);
        boolean moving = cast == Cast.IDLE || cast == Cast.BOLT;
        hover(entity, moving);
        if (cast != Cast.IDLE) {
            castTick++;
            switch (cast) {
                case BOLT -> tickBolt(entity);
                case RAIN -> tickRain(entity);
                case SKY -> tickSky(entity);
                case LANCE -> tickLance(entity);
                default -> endCast();
            }
            return;
        }
        double pct = instance.healthPercent();
        int skyIdx = pct <= 30.0 ? 1 : pct <= 70.0 ? 0 : -1;
        if (skyIdx >= 0 && !skyUsed[skyIdx] && nearest(entity, 26) != null) {
            skyUsed[skyIdx] = true;
            if (skyIdx == 1) {
                skyUsed[0] = true;
            }
            startSky(entity);
            return;
        }
        Player near = nearest(entity, 26);
        if (near == null) {
            return;
        }
        if (castCd > 0) {
            castCd--;
            if (boltCd > 0) {
                boltCd--;
            } else {
                startBolt(entity, near);
            }
            return;
        }
        if (rotation++ % 2 == 0 || !enraged) {
            startRain(entity);
        } else {
            startLance(entity, near);
        }
    }

    private void startBolt(LivingEntity entity, Player near) {
        cast = Cast.BOLT;
        castTick = 0;
        Player target = randomTarget(entity, 24);
        castAt = ground((target != null ? target : near).getLocation());
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.7f);
    }

    private void tickBolt(LivingEntity entity) {
        World world = entity.getWorld();
        Location from = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        boolean strobe = BOLT_WIND - castTick < 4;
        if (castTick % 2 == 0) {
            line(from, castAt.clone().add(0, 0.2, 0), strobe ? WHITE : EMBER, 0.7, 1.0f);
        }
        ring(castAt, 1.5, strobe ? WHITE : EMBER, 14, 1.2f);
        if (castTick < BOLT_WIND) {
            return;
        }
        world.spawnParticle(Particle.FLAME, castAt.clone().add(0, 0.3, 0), 18, 0.5, 0.2, 0.5, 0.04);
        world.spawnParticle(Particle.LAVA, castAt.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0);
        world.playSound(castAt, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.9f);
        hitCircle(entity, castAt, 1.6, 22, false);
        cast = Cast.IDLE;
        castTick = 0;
        boltCd = enraged ? 30 : 42;
    }

    private void startRain(LivingEntity entity) {
        cast = Cast.RAIN;
        castTick = 0;
        marks.clear();
        int count = enraged ? 7 : 5;
        List<Player> targets = targets(entity, 24);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : targets) {
            if (marks.size() >= count) {
                break;
            }
            marks.add(ground(player.getLocation()));
        }
        Location base = targets.isEmpty() ? ground(entity.getLocation()) : ground(targets.get(0).getLocation());
        while (marks.size() < count) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 2.5 + random.nextDouble() * 5.0;
            marks.add(ground(base.clone().add(Math.cos(angle) * dist, 1.5, Math.sin(angle) * dist)));
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.3f, 0.5f);
        world.playSound(entity.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.5f);
    }

    private void tickRain(LivingEntity entity) {
        World world = entity.getWorld();
        for (int i = 0; i < marks.size(); i++) {
            Location mark = marks.get(i);
            int local = castTick - i * RAIN_STAGGER;
            if (local < 0 || local > RAIN_WIND) {
                continue;
            }
            if (local < RAIN_WIND) {
                boolean strobe = RAIN_WIND - local < 6;
                ring(mark, RAIN_RADIUS, strobe && local % 2 == 0 ? WHITE : EMBER, 20, 1.3f);
                double drop = (1.0 - local / (double) RAIN_WIND) * 11.0;
                world.spawnParticle(Particle.FLAME, mark.clone().add(0, drop, 0), 3, 0.15, 0.2, 0.15, 0.0);
                world.spawnParticle(Particle.DUST, mark.clone().add(0, drop + 0.6, 0), 2, 0.1, 0.3, 0.1,
                        new Particle.DustOptions(CINDER, 1.6f));
                continue;
            }
            world.spawnParticle(Particle.FLAME, mark.clone().add(0, 0.3, 0), 30, 0.9, 0.2, 0.9, 0.05);
            world.spawnParticle(Particle.LAVA, mark.clone().add(0, 0.3, 0), 6, 0.6, 0.1, 0.6, 0);
            world.spawnParticle(Particle.EXPLOSION, mark.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.BLOCK, mark.clone().add(0, 0.2, 0), 14, 0.7, 0.1, 0.7, 0.08, Material.MAGMA_BLOCK.createBlockData());
            world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 1.25f);
            world.playSound(mark, Sound.BLOCK_LAVA_POP, 1.0f, 0.6f);
            hitCircle(entity, mark, RAIN_RADIUS, 40, true);
        }
        if (castTick > (marks.size() - 1) * RAIN_STAGGER + RAIN_WIND) {
            marks.clear();
            finishCast(enraged ? 70 : 95);
        }
    }

    private void startSky(LivingEntity entity) {
        cast = Cast.SKY;
        castTick = 0;
        castAt = ground(entity.getLocation());
        wards.clear();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double first = random.nextDouble() * Math.PI * 2;
        double second = first + Math.PI * (0.6 + random.nextDouble() * 0.8);
        for (double angle : new double[]{first, second}) {
            double dist = 5.0 + random.nextDouble() * 4.0;
            wards.add(ground(castAt.clone().add(Math.cos(angle) * dist, 2.0, Math.sin(angle) * dist)));
        }
        World world = entity.getWorld();
        world.playSound(castAt, Sound.ENTITY_BLAZE_AMBIENT, 1.5f, 0.4f);
        world.playSound(castAt, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
        world.playSound(castAt, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 0.6f);
        shout("&6&lCinder Herald&7: &eThe sky burned once. &cIt remembers.");
        for (Player player : targets(entity, SKY_RADIUS + 8)) {
            player.sendTitle("§c§lBURNED SKY", "§7Stand in a §bblue soul ward", 4, 50, 10);
        }
    }

    private void tickSky(LivingEntity entity) {
        World world = entity.getWorld();
        double t = castTick / (double) SKY_WIND;
        lift = Math.min(3.0, castTick * 0.15);
        boolean strobe = SKY_WIND - castTick < 10;
        if (castTick % 2 == 0) {
            ring(castAt, SKY_RADIUS, strobe && castTick % 4 == 0 ? WHITE : CRIMSON, 64, 1.5f);
        }
        if (castTick % 3 == 0) {
            world.spawnParticle(Particle.DUST, castAt.clone().add(0, 0.12, 0), 40, SKY_RADIUS * 0.5, 0.02, SKY_RADIUS * 0.5,
                    new Particle.DustOptions(CINDER, 1.3f));
            world.spawnParticle(Particle.WHITE_ASH, castAt.clone().add(0, 9.0, 0), (int) (30 + t * 60), SKY_RADIUS * 0.55, 1.0,
                    SKY_RADIUS * 0.55, 0);
        }
        for (Location ward : wards) {
            ring(ward, WARD_RADIUS, SOUL, 22, 1.4f);
            if (castTick % 3 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, ward.clone().add(0, 0.2, 0), 4, WARD_RADIUS * 0.4, 0.05, WARD_RADIUS * 0.4, 0.01);
                for (double y = 0; y < 5.0; y += 0.6) {
                    world.spawnParticle(Particle.DUST, ward.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, new Particle.DustOptions(SOUL, 1.1f));
                }
            }
        }
        if (castTick % 10 == 0) {
            world.playSound(castAt, Sound.BLOCK_FIRE_AMBIENT, 1.3f, 0.5f + (float) t);
        }
        if (castTick == SKY_WIND - 10) {
            world.playSound(castAt, Sound.BLOCK_BELL_RESONATE, 1.4f, 0.6f);
            world.playSound(castAt, Sound.ENTITY_BLAZE_AMBIENT, 1.5f, 0.35f);
        }
        if (castTick < SKY_WIND) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Location p = player.getLocation();
            if (horizontal(p, castAt) > SKY_RADIUS || Math.abs(p.getY() - castAt.getY()) > 6.0) {
                continue;
            }
            boolean warded = false;
            for (Location ward : wards) {
                if (horizontal(p, ward) <= WARD_RADIUS + 0.3) {
                    warded = true;
                    break;
                }
            }
            if (warded) {
                world.spawnParticle(Particle.SOUL, p.clone().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3, 0.02);
                player.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                continue;
            }
            BossHits.hurt(player, entity, 70);
            player.setFireTicks(Math.max(player.getFireTicks(), 40));
            world.spawnParticle(Particle.FLAME, p.clone().add(0, 1.0, 0), 20, 0.3, 0.6, 0.3, 0.03);
        }
        world.spawnParticle(Particle.FLASH, castAt.clone().add(0, 6.0, 0), 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2 * i / 40.0;
            double r = SKY_RADIUS * (0.3 + ThreadLocalRandom.current().nextDouble() * 0.7);
            Location at = castAt.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
            world.spawnParticle(Particle.FLAME, at, 4, 0.2, 0.4, 0.2, 0.03);
        }
        world.playSound(castAt, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.6f);
        world.playSound(castAt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.7f);
        world.playSound(castAt, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.4f);
        wards.clear();
        finishCast(80);
    }

    private void startLance(LivingEntity entity, Player near) {
        cast = Cast.LANCE;
        castTick = 0;
        castAt = ground(entity.getLocation());
        castDir = flat(near.getLocation().toVector().subtract(castAt.toVector()));
        World world = entity.getWorld();
        world.playSound(castAt, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.45f);
        world.playSound(castAt, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.6f);
    }

    private void tickLance(LivingEntity entity) {
        World world = entity.getWorld();
        boolean strobe = LANCE_WIND - castTick < 6;
        Vector side = new Vector(-castDir.getZ(), 0, castDir.getX()).multiply(LANCE_WIDTH * 0.5);
        if (castTick % 2 == 0) {
            Location end = castAt.clone().add(castDir.clone().multiply(LANCE_LENGTH));
            Color edge = strobe && castTick % 4 == 0 ? WHITE : CRIMSON;
            line(castAt.clone().add(side).add(0, 0.15, 0), end.clone().add(side).add(0, 0.15, 0), edge, 0.6, 1.3f);
            line(castAt.clone().subtract(side).add(0, 0.15, 0), end.clone().subtract(side).add(0, 0.15, 0), edge, 0.6, 1.3f);
            line(castAt.clone().add(0, 0.1, 0), end.clone().add(0, 0.1, 0), EMBER, 1.4, 0.9f);
        }
        if (castTick < LANCE_WIND) {
            return;
        }
        for (double d = 0; d <= LANCE_LENGTH; d += 0.5) {
            Location p = castAt.clone().add(castDir.clone().multiply(d)).add(0, 0.8, 0);
            world.spawnParticle(Particle.FLAME, p, 3, 0.15, 0.15, 0.15, 0.02);
            if (d % 2 == 0) {
                world.spawnParticle(Particle.LAVA, p, 1, 0, 0, 0, 0);
            }
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Vector rel = player.getLocation().toVector().subtract(castAt.toVector());
            double along = rel.getX() * castDir.getX() + rel.getZ() * castDir.getZ();
            if (along < -0.5 || along > LANCE_LENGTH + 0.5 || Math.abs(rel.getY()) > 3.0) {
                continue;
            }
            double across = Math.abs(rel.getX() * -castDir.getZ() + rel.getZ() * castDir.getX());
            if (across > LANCE_WIDTH) {
                continue;
            }
            BossHits.hurt(player, entity, 55);
            player.setFireTicks(Math.max(player.getFireTicks(), 30));
        }
        world.playSound(castAt, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f);
        world.playSound(castAt, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
        finishCast(80);
    }

    /** Hovering drift: keep a lane of distance, face the nearest target. AI stays off. */
    private void hover(LivingEntity entity, boolean moving) {
        if (cast != Cast.SKY && lift > 0) {
            lift = Math.max(0, lift - 0.12);
        }
        Location at = entity.getLocation();
        Player near = nearest(entity, 30);
        Location next = at.clone();
        if (near != null) {
            face(entity, near.getLocation());
            next.setYaw(entity.getLocation().getYaw());
            if (moving) {
                Vector to = flat(near.getLocation().toVector().subtract(at.toVector()));
                double dist = horizontal(near.getLocation(), at);
                Vector step = new Vector();
                if (dist > 10.0) {
                    step.add(to.clone().multiply(0.12));
                } else if (dist < 6.0) {
                    step.add(to.clone().multiply(-0.12));
                }
                step.add(new Vector(-to.getZ(), 0, to.getX()).multiply(0.04));
                Location probe = at.clone().add(step);
                if (!probe.getBlock().getType().isSolid() && !probe.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    next.add(step);
                }
            }
        }
        double floor = floorY(next);
        double want = floor + HOVER + lift + Math.sin(instance.getTicksAlive() * 0.08) * 0.25;
        double ceiling = ceilingY(next, floor, want + entity.getHeight() + 1.0);
        want = Math.max(floor, Math.min(want, ceiling - entity.getHeight() - 0.2));
        next.setY(at.getY() + (want - at.getY()) * 0.15);
        next.setPitch(0f);
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(next);
            }
        });
    }

    private void heraldAmbient(LivingEntity entity) {
        long t = instance.getTicksAlive();
        World world = entity.getWorld();
        Location core = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = t * 0.15 + i * Math.PI / 2;
                world.spawnParticle(enraged ? Particle.SOUL_FIRE_FLAME : Particle.FLAME,
                        core.clone().add(Math.cos(angle) * 1.3, Math.sin(t * 0.1 + i) * 0.4, Math.sin(angle) * 1.3), 1, 0, 0, 0, 0);
            }
        }
        if (t % 5 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, core, 6, 0.8, 0.8, 0.8, 0);
            world.spawnParticle(Particle.DUST, core.clone().add(0, -1.4, 0), 2, 0.3, 0.3, 0.3, new Particle.DustOptions(EMBER, 1.2f));
        }
        if (t % 40 == 0) {
            world.playSound(core, Sound.ENTITY_BLAZE_BURN, 0.7f, 0.6f);
        }
    }

    /** Flames gutter, the body sinks, one ember climbs out and flies to the Throne. */
    private boolean tickHeraldDeath() {
        deathTicks++;
        World world = deathFocus.getWorld();
        LivingEntity entity = instance.getEntity();
        if (world == null) {
            return true;
        }
        double sink = Math.min(1.0, deathTicks / 30.0);
        Location body = deathFocus.clone();
        body.setY(deathFocus.getY() - (deathFocus.getY() - floorY(deathFocus)) * sink * sink);
        if (entity != null && entity.isValid()) {
            entity.teleport(body);
            entity.setVelocity(new Vector(0, 0, 0));
        }
        Location core = body.clone().add(0, entity != null && entity.isValid() ? entity.getHeight() * 0.5 : 1.2, 0);
        if (deathTicks < 30) {
            if (deathTicks % 3 == 0) {
                world.spawnParticle(Particle.SMOKE, core, 10, 0.5, 0.6, 0.5, 0.02);
                world.spawnParticle(Particle.FLAME, core, Math.max(1, 8 - deathTicks / 4), 0.6, 0.6, 0.6, 0.01);
            }
            if (deathTicks % 8 == 0) {
                world.playSound(core, Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 0.5f + deathTicks * 0.02f);
            }
            mote = core.clone();
        }
        if (deathTicks == 30) {
            world.playSound(core, Sound.ENTITY_BLAZE_DEATH, 1.2f, 0.5f);
            world.spawnParticle(Particle.WHITE_ASH, core, 120, 1.2, 1.2, 1.2, 0.02);
            world.spawnParticle(Particle.SMOKE, core, 40, 0.8, 0.8, 0.8, 0.04);
            world.playSound(core, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        }
        if (deathTicks > 30 && mote != null) {
            Location goal = throneFor(world);
            Vector toward = goal.toVector().subtract(mote.toVector());
            double speed = Math.min(1.6, 0.25 + (deathTicks - 30) * 0.05);
            if (toward.lengthSquared() > speed * speed) {
                Vector step = toward.normalize().multiply(speed);
                step.setY(step.getY() + Math.max(0, 0.6 - (deathTicks - 30) * 0.03));
                mote.add(step);
            }
            world.spawnParticle(Particle.DUST, mote, 4, 0.05, 0.05, 0.05, new Particle.DustOptions(EMBER, 1.8f));
            world.spawnParticle(Particle.FLAME, mote, 2, 0.04, 0.04, 0.04, 0.005);
            world.spawnParticle(Particle.END_ROD, mote, 1, 0, 0, 0, 0);
            if (deathTicks % 6 == 0) {
                world.playSound(mote, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.6f + (deathTicks - 30) * 0.02f);
            }
            if (deathTicks == 46) {
                shout("&8&o…an ember drifts toward the Throne. &cThe Sovereign stirs.");
                world.playSound(goal, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);
            }
        }
        if (deathTicks >= HERALD_DEATH) {
            if (mote != null) {
                world.spawnParticle(Particle.FLASH, mote, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.FLAME, mote, 20, 0.3, 0.3, 0.3, 0.05);
            }
            mote = null;
            return true;
        }
        return false;
    }

    /** Floor 3 Throne if we're in an Ashes world close enough; otherwise straight up. */
    private Location throneFor(World world) {
        Location throne = THRONE.clone();
        throne.setWorld(world);
        if (deathFocus.distanceSquared(throne) <= 160 * 160) {
            return throne;
        }
        return deathFocus.clone().add(0, 30, 0);
    }

    // ------------------------------------------------------------------ shared

    private void finishCast(int cooldown) {
        cast = Cast.IDLE;
        castTick = 0;
        castCd = cooldown;
        castTarget = null;
        LivingEntity entity = instance.getEntity();
        if (kind() == Kind.CHAINWARDEN && entity instanceof Mob mob && entity.isValid()) {
            mob.setAI(true);
        }
    }

    private void endCast() {
        cast = Cast.IDLE;
        castTick = 0;
        castTarget = null;
        marks.clear();
        wards.clear();
    }

    private void freeze(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        if (kind() == Kind.CHAINWARDEN) {
            entity.setVelocity(new Vector(0, Math.min(0, entity.getVelocity().getY()), 0));
        }
    }

    private void hitCircle(LivingEntity entity, Location center, double radius, double power, boolean burn) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Location p = player.getLocation();
            if (horizontal(p, center) > radius || Math.abs(p.getY() - center.getY()) > 3.0) {
                continue;
            }
            BossHits.hurt(player, entity, power);
            if (burn) {
                player.setFireTicks(Math.max(player.getFireTicks(), 30));
            }
        }
    }

    private List<Player> targets(LivingEntity entity, double range) {
        List<Player> list = new ArrayList<>();
        double r2 = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (instance.isCombatTarget(player) && player.getLocation().distanceSquared(entity.getLocation()) <= r2) {
                list.add(player);
            }
        }
        java.util.Collections.shuffle(list);
        return list;
    }

    private Player randomTarget(LivingEntity entity, double range) {
        List<Player> list = targets(entity, range);
        return list.isEmpty() ? null : list.get(0);
    }

    private Player nearest(LivingEntity entity, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(entity.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private void face(LivingEntity entity, Location target) {
        Vector to = target.toVector().subtract(entity.getLocation().toVector());
        to.setY(0);
        if (to.lengthSquared() < 0.01) {
            return;
        }
        Location look = entity.getLocation().clone();
        look.setDirection(to);
        entity.setRotation(look.getYaw(), 0f);
    }

    private void ring(Location center, double radius, Color color, int points, float size) {
        World world = center.getWorld();
        if (world == null || radius <= 0.05) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * radius, center.getY() + 0.12,
                    center.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, dust);
        }
    }

    private void square(Location center, double half, Color color, double step, double y) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        for (double d = -half; d <= half + 0.001; d += step) {
            world.spawnParticle(Particle.DUST, center.getX() + d, center.getY() + y, center.getZ() - half, 1, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, center.getX() + d, center.getY() + y, center.getZ() + half, 1, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, center.getX() - half, center.getY() + y, center.getZ() + d, 1, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, center.getX() + half, center.getY() + y, center.getZ() + d, 1, 0, 0, 0, dust);
        }
    }

    /** Vertical bars on the cage edge; {@code bottom} drops from 5 to 0 as the cage slams. */
    private void cageBars(Location center, double half, double bottom, Color color) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        double top = 5.2;
        for (double d = -half; d <= half + 0.001; d += half / 2.0) {
            double[][] posts = {{d, -half}, {d, half}, {-half, d}, {half, d}};
            for (double[] post : posts) {
                for (double y = bottom; y <= top; y += 0.6) {
                    world.spawnParticle(Particle.DUST, center.getX() + post[0], center.getY() + y, center.getZ() + post[1],
                            1, 0, 0, 0, dust);
                }
            }
        }
    }

    private void cone(Location center, Vector dir, double radius, Color color) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.3f);
        for (double r = 2.0; r <= radius + 0.01; r += 2.5) {
            for (int deg = -90; deg <= 90; deg += 10) {
                Vector v = rotate(dir, deg).multiply(r);
                world.spawnParticle(Particle.DUST, center.getX() + v.getX(), center.getY() + 0.12, center.getZ() + v.getZ(),
                        1, 0, 0, 0, dust);
            }
        }
        for (int edge : new int[]{-90, 90}) {
            for (double r = 0.5; r <= radius; r += 0.6) {
                Vector v = rotate(dir, edge).multiply(r);
                world.spawnParticle(Particle.DUST, center.getX() + v.getX(), center.getY() + 0.12, center.getZ() + v.getZ(),
                        1, 0, 0, 0, dust);
            }
        }
    }

    private void chainLine(Location from, Location to, Color color) {
        line(from, to, color, 0.45, 1.0f);
    }

    private void line(Location from, Location to, Color color, double spacing, float size) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        int steps = Math.max(1, (int) (length / spacing));
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i <= steps; i++) {
            Location p = from.clone().add(delta.clone().multiply(i / (double) steps));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, dust);
        }
    }

    private static Vector rotate(Vector dir, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vector(dir.getX() * cos - dir.getZ() * sin, 0, dir.getX() * sin + dir.getZ() * cos);
    }

    private static Vector flat(Vector v) {
        Vector out = new Vector(v.getX(), 0, v.getZ());
        if (out.lengthSquared() < 1.0e-4) {
            return new Vector(1, 0, 0);
        }
        return out.normalize();
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Top of the first solid block at or below {@code at} (up to 12 down). */
    private static double floorY(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return at.getY();
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int start = at.getBlockY();
        for (int y = start; y >= start - 12; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                return y + 1.0;
            }
        }
        return at.getY() - HOVER;
    }

    /** Bottom of the first solid block above {@code floor}, scanning up to {@code limit}. */
    private static double ceilingY(Location at, double floor, double limit) {
        World world = at.getWorld();
        if (world == null) {
            return limit;
        }
        for (int y = (int) Math.floor(floor); y <= (int) Math.ceil(limit); y++) {
            if (world.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getType().isSolid()) {
                return y;
            }
        }
        return limit;
    }

    private static Location ground(Location loc) {
        Location at = loc.clone();
        at.setY(floorY(loc));
        return at;
    }

    private static Location lerp(Location from, Location to, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Location(
                to.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t,
                from.getYaw(),
                from.getPitch()
        );
    }

    private static ItemStack trimmed(Material material, TrimMaterial trimMaterial, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(trimMaterial, pattern));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static PotionEffectType slowness() {
        PotionEffectType type = PotionEffectType.getByName("SLOWNESS");
        return type != null ? type : PotionEffectType.getByName("SLOW");
    }

    private void shout(String line) {
        LivingEntity entity = instance.getEntity();
        Location origin = entity != null && entity.isValid() ? entity.getLocation() : instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= 56 * 56) {
                player.sendMessage(TextUtil.component(line));
            }
        }
    }
}
