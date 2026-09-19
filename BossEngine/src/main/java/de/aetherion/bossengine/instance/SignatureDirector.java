package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.fx.FakeDestruction;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Unique death cinematics. Sparky and Aetherion keep their own directors.
 * Nobody else reuses Sparky's suck-and-spit.
 */
final class SignatureDirector {

    private final BossInstance instance;
    private final List<Entity> props = new ArrayList<>();
    private int deathTicks = -1;
    private Theme theme = Theme.NONE;
    private Location focus;
    private double startScale = 1.0;

    SignatureDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    boolean beginDeath() {
        theme = Theme.of(instance.getTemplate() == null ? "" : instance.getTemplate().getId());
        if (theme == Theme.NONE || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        deathTicks = 0;
        focus = entity.getLocation().clone();
        startScale = AttributeUtil.getBase(entity, AttributeUtil.scale(), 1.0);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setVelocity(new Vector(0, 0, 0));
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        World world = entity.getWorld();
        world.playSound(focus, theme.startSound, 1.15f, theme.startPitch);
        shout(theme.line);
        return true;
    }

    boolean beginFallbackDeath() {
        if (isDying()) {
            return true;
        }
        theme = Theme.DEFAULT;
        LivingEntity entity = instance.getEntity();
        deathTicks = 0;
        focus = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : instance.getSpawnLocation();
        startScale = entity != null
                ? AttributeUtil.getBase(entity, AttributeUtil.scale(), 1.0)
                : 1.0;
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
        }
        World world = focus.getWorld();
        if (world != null) {
            world.playSound(focus, theme.startSound, 1.15f, theme.startPitch);
        }
        shout(theme.line);
        return true;
    }

    boolean abort() {
        boolean dying = isDying();
        deathTicks = -1;
        clearProps();
        return dying;
    }

    boolean tick() {
        if (!isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        boolean body = entity != null && entity.isValid() && !entity.isDead();
        deathTicks++;
        Location hold = (focus == null
                ? (body ? entity.getLocation() : instance.getSpawnLocation())
                : focus).clone();
        hold.add(0, theme.motionY(deathTicks), 0);
        if (theme == Theme.TROLL && deathTicks >= 38 && focus != null) {
            hold = focus.clone();
        }
        if (body) {
            entity.teleport(hold);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setFallDistance(0);
        }
        Location core = hold.clone().add(0, (body ? entity.getHeight() : 2.0) * 0.45, 0);
        World world = core.getWorld();
        if (world == null) {
            return deathTicks >= theme.duration;
        }
        switch (theme) {
            case COLOSSUS -> colossus(world, core, entity);
            case LURKER -> lurker(world, core, entity);
            case SKULL -> skull(world, core, entity);
            case NUGGET -> nugget(world, core, entity);
            case TROLL -> troll(world, core, entity);
            case SQUID -> squid(world, core, entity);
            case BALTHAZAR -> balthazar(world, core, entity);
            case BARON -> baron(world, core, entity);
            case WITHER -> wither(world, core, entity);
            case CLEANER -> cleaner(world, core, entity);
            case SENTINEL -> sentinel(world, core, entity);
            case FROST -> frost(world, core, entity);
            default -> generic(world, core, entity);
        }
        if (deathTicks >= theme.duration) {
            world.playSound(core, theme.endSound, 1.25f, theme.endPitch);
            clearProps();
            return true;
        }
        return false;
    }

    private void colossus(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.PORTAL, core, 18, 1.0, 0.8, 1.0, 0.45);
        world.spawnParticle(Particle.ENCHANT, core, 12, 0.8, 0.6, 0.8, 0.5);
        int pillars = 6;
        for (int i = 0; i < pillars; i++) {
            double angle = (Math.PI * 2 * i) / pillars + deathTicks * 0.04;
            double radius = 3.4 - Math.min(2.4, deathTicks * 0.028);
            Location base = core.clone().add(Math.cos(angle) * radius, -0.6, Math.sin(angle) * radius);
            for (double y = 0; y < 5.2; y += 0.4) {
                world.spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, y, 0), 1, 0.04, 0.04, 0.04, 0.01);
            }
        }
        if (deathTicks % 12 == 0) {
            world.playSound(core, Sound.ENTITY_RAVAGER_ROAR, 0.5f, 0.45f + deathTicks * 0.004f);
            world.playSound(core, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.6f);
            FakeDestruction.blockBurst(world, core, Material.AMETHYST_BLOCK, 8);
        }
        if (deathTicks == 96) {
            world.spawnParticle(Particle.PORTAL, core, 80, 0.2, 0.2, 0.2, 1.2);
            world.playSound(core, Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.55f);
            FakeDestruction.blockBurst(world, core, Material.CRYING_OBSIDIAN, 18);
        }
        scaleToward(entity, 0.35);
    }

    private void lurker(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.SOUL, core, 14, 0.5, 0.35, 0.5, 0.02);
        world.spawnParticle(Particle.SCULK_SOUL, core.clone().add(0, -0.4, 0), 8, 0.4, 0.15, 0.4, 0.01);
        double ring = Math.max(0.6, 4.2 - deathTicks * 0.045);
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                    core.clone().add(Math.cos(angle) * ring, -0.7, Math.sin(angle) * ring),
                    1, 0, 0, 0, 0);
        }
        if (deathTicks % 10 == 0) {
            world.playSound(core, Sound.ENTITY_WARDEN_HEARTBEAT, 0.55f, 0.7f - deathTicks * 0.004f);
        }
        if (deathTicks == 78) {
            world.spawnParticle(Particle.SOUL, core.clone().add(0, -0.8, 0), 40, 0.3, 0.2, 0.3, 0.04);
            world.playSound(core, Sound.ENTITY_WARDEN_DEATH, 0.9f, 0.55f);
            FakeDestruction.blockBurst(world, core, Material.SCULK, 14);
        }
        scaleToward(entity, 0.25);
    }

    private void skull(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.FLAME, core, 10, 0.6, 0.5, 0.6, 0.02);
        if (deathTicks % 5 == 0) {
            double yaw = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            Location from = core.clone().add(Math.cos(yaw) * 7.5, 3.5 + ThreadLocalRandom.current().nextDouble(), Math.sin(yaw) * 7.5);
            Vector to = core.toVector().subtract(from.toVector()).normalize().multiply(0.35);
            for (int i = 0; i < 8; i++) {
                world.spawnParticle(Particle.FLAME, from.clone().add(to.clone().multiply(i * 1.1)), 1, 0, 0, 0, 0);
            }
            world.playSound(core, Sound.ENTITY_SKELETON_SHOOT, 0.45f, 0.7f);
        }
        if (deathTicks == 88) {
            world.spawnParticle(Particle.FLAME, core, 50, 1.2, 0.8, 1.2, 0.08);
            world.spawnParticle(Particle.ITEM, core, 28, 0.8, 0.6, 0.8, 0.12, new ItemStack(Material.BONE));
            world.playSound(core, Sound.ENTITY_BLAZE_DEATH, 1.1f, 0.55f);
            world.playSound(core, Sound.ENTITY_SKELETON_DEATH, 1.0f, 0.5f);
        }
        scaleToward(entity, 0.5);
    }

    private void nugget(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.CLOUD, core, 8, 0.6, 0.4, 0.6, 0.02);
        world.spawnParticle(Particle.ITEM, core, 6, 0.5, 0.4, 0.5, 0.04, new ItemStack(Material.FEATHER));
        double inflate = startScale * (1.0 + Math.min(1.15, deathTicks / 55.0));
        AttributeUtil.setBase(entity, AttributeUtil.scale(), inflate);
        if (deathTicks % 8 == 0) {
            world.playSound(core, Sound.ENTITY_CHICKEN_AMBIENT, 0.8f, 0.55f + deathTicks * 0.006f);
        }
        if (deathTicks == 82) {
            world.spawnParticle(Particle.CLOUD, core, 40, 1.1, 0.8, 1.1, 0.08);
            world.spawnParticle(Particle.ITEM, core, 40, 1.0, 0.8, 1.0, 0.15, new ItemStack(Material.FEATHER));
            world.spawnParticle(Particle.ITEM, core, 12, 0.5, 0.4, 0.5, 0.1, new ItemStack(Material.EGG));
            world.playSound(core, Sound.ENTITY_CHICKEN_DEATH, 1.3f, 0.6f);
            world.playSound(core, Sound.ENTITY_CHICKEN_EGG, 1.1f, 0.5f);
            AttributeUtil.setBase(entity, AttributeUtil.scale(), 0.35);
        }
    }

    private void troll(World world, Location core, LivingEntity entity) {
        if (deathTicks < 36) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, core, 8, 0.5, 0.6, 0.5, 0.03);
            if (deathTicks % 6 == 0) {
                toss(core, Material.GOLD_NUGGET, 0.35, 0.55);
                world.playSound(core, Sound.ENTITY_PIGLIN_BRUTE_AMBIENT, 0.5f, 0.7f);
            }
        } else if (deathTicks == 38) {
            world.playSound(focus, Sound.BLOCK_ANVIL_LAND, 1.55f, 0.4f);
            world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 0.8f);
            world.spawnParticle(Particle.GUST, focus.clone().add(0, 0.2, 0), 3, 0.4, 0.05, 0.4, 0);
            world.spawnParticle(Particle.ITEM, focus, 24, 0.9, 0.3, 0.9, 0.12, new ItemStack(Material.GOLD_INGOT));
            FakeDestruction.blockBurst(world, focus, Material.GOLD_BLOCK, 12);
        } else {
            world.spawnParticle(Particle.CRIT, core, 6, 0.5, 0.2, 0.5, 0.04);
        }
    }

    private void squid(World world, Location core, LivingEntity entity) {
        double radius = deathTicks < 70 ? deathTicks * 0.055 : Math.max(0.2, 3.8 - (deathTicks - 70) * 0.12);
        int points = 22;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            for (int j = 0; j < 6; j++) {
                double pitch = -Math.PI / 2 + (Math.PI * j / 5.0);
                Location p = core.clone().add(
                        Math.cos(a) * Math.cos(pitch) * radius,
                        Math.sin(pitch) * radius,
                        Math.sin(a) * Math.cos(pitch) * radius
                );
                world.spawnParticle(Particle.SQUID_INK, p, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticle(Particle.BUBBLE, core, 8, 0.4, 0.4, 0.4, 0.2);
        if (deathTicks % 10 == 0) {
            world.playSound(core, Sound.ENTITY_SQUID_SQUIRT, 0.55f, 0.5f);
        }
        if (deathTicks == 108) {
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, core, 40, 0.3, 1.4, 0.3, 0.55);
            world.playSound(core, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.55f);
        }
        scaleToward(entity, 0.4);
    }

    private void balthazar(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.WITCH, core, 10, 0.7, 0.6, 0.7, 0.02);
        world.spawnParticle(Particle.ENCHANT, core, 16, 0.9, 0.7, 0.9, 0.7);
        if (deathTicks % 9 == 0) {
            double yaw = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            Location ghost = core.clone().add(Math.cos(yaw) * 2.6, 0.2, Math.sin(yaw) * 2.6);
            world.spawnParticle(Particle.WITCH, ghost, 12, 0.2, 0.7, 0.2, 0.01);
            world.spawnParticle(Particle.FLASH, ghost, 1, 0, 0, 0, 0);
            world.playSound(core, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 0.8f);
        }
        if (deathTicks == 100) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, core, 50, 0.7, 1.0, 0.7, 0.35);
            world.playSound(core, Sound.ITEM_TOTEM_USE, 1.05f, 0.75f);
            world.playSound(core, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.0f, 0.7f);
        }
        scaleToward(entity, 0.3);
    }

    private void baron(World world, Location core, LivingEntity entity) {
        Location hole = (focus == null ? core : focus).clone();
        if (deathTicks < 48) {
            world.spawnParticle(Particle.BLOCK, hole.clone().add(0, 0.1, 0), 14, 0.45, 0.08, 0.45, 0.12,
                    Material.DIRT.createBlockData());
            if (deathTicks % 8 == 0) {
                world.playSound(core, Sound.BLOCK_GRAVEL_BREAK, 0.8f, 0.45f);
            }
            scaleToward(entity, 0.2);
            return;
        }
        geyserUp(world, hole, deathTicks - 48);
        if (deathTicks == 52) {
            world.playSound(hole, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.5f);
            world.playSound(hole, Sound.ENTITY_SILVERFISH_DEATH, 1.15f, 0.4f);
            FakeDestruction.spawnDebris(hole, instance, 14, 0.35, 1.55, 36,
                    new Material[]{Material.DIRT, Material.COARSE_DIRT, Material.GRAVEL, Material.ROOTED_DIRT});
        }
        if (deathTicks > 52) {
            world.spawnParticle(Particle.CLOUD, hole.clone().add(0, 2.5, 0), 6, 0.35, 0.8, 0.35, 0.02);
        }
    }

    private void wither(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.SMOKE, core, 10, 0.7, 0.6, 0.7, 0.02);
        for (int i = 0; i < 3; i++) {
            double a = deathTicks * 0.18 + i * (Math.PI * 2 / 3);
            Location orbit = core.clone().add(Math.cos(a) * 2.6, 0.9 + Math.sin(deathTicks * 0.12 + i) * 0.4, Math.sin(a) * 2.6);
            world.spawnParticle(Particle.SMOKE, orbit, 3, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(Particle.SOUL, orbit, 2, 0.04, 0.04, 0.04, 0);
            world.spawnParticle(Particle.DUST, orbit, 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.4f));
        }
        if (deathTicks % 4 == 0) {
            toss(core, Material.GOLD_NUGGET, 0.28, 0.7);
        }
        if (deathTicks % 12 == 0) {
            world.playSound(core, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.65f);
        }
        if (deathTicks == 118) {
            world.spawnParticle(Particle.SMOKE, core, 40, 1.1, 0.8, 1.1, 0.04);
            world.spawnParticle(Particle.ITEM, core, 28, 0.9, 0.7, 0.9, 0.14, new ItemStack(Material.GOLD_INGOT));
            world.playSound(core, Sound.ENTITY_WITHER_DEATH, 1.15f, 0.7f);
        }
        scaleToward(entity, 0.45);
    }

    private void cleaner(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.PORTAL, core, 16, 0.7, 0.6, 0.7, 0.4);
        double length = Math.max(0.4, 8.0 - deathTicks * 0.07);
        Vector[] dirs = {
                new Vector(1, 0, 0), new Vector(-1, 0, 0),
                new Vector(0, 0, 1), new Vector(0, 0, -1)
        };
        for (Vector dir : dirs) {
            for (double d = 0.3; d <= length; d += 0.4) {
                Location p = core.clone().add(dir.clone().multiply(d));
                world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0, 0, 0);
            }
        }
        if (deathTicks % 8 == 0) {
            world.playSound(core, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.5f);
        }
        pullPlayers(core, 0.07);
        if (deathTicks == 118) {
            world.spawnParticle(Particle.REVERSE_PORTAL, core, 50, 0.3, 0.3, 0.3, 0.4);
            world.playSound(core, Sound.BLOCK_END_PORTAL_SPAWN, 0.85f, 0.65f);
        }
        scaleToward(entity, 0.4);
    }

    private void sentinel(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, core, 10, 0.6, 0.5, 0.6, 0.04);
        world.spawnParticle(Particle.CRIT, core, 6, 0.4, 0.3, 0.4, 0.05);
        if (deathTicks % 7 == 0) {
            toss(core, deathTicks % 14 == 0 ? Material.IRON_INGOT : Material.CHAIN, 0.32, 0.45);
            world.playSound(core, Sound.BLOCK_CHAIN_BREAK, 0.6f, 0.7f + deathTicks * 0.003f);
        }
        if (deathTicks == 86) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, core, 40, 1.0, 0.8, 1.0, 0.08);
            world.spawnParticle(Particle.SMOKE, core, 16, 0.6, 0.5, 0.6, 0.02);
            world.playSound(core, Sound.ENTITY_IRON_GOLEM_DEATH, 1.15f, 0.7f);
            world.playSound(core, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.85f);
        }
        scaleToward(entity, 0.5);
    }

    private void frost(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.SNOWFLAKE, core, 12, 0.8, 0.5, 0.8, 0.02);
        int spikes = 8;
        double height = Math.min(4.8, deathTicks * 0.055);
        for (int i = 0; i < spikes; i++) {
            double angle = (Math.PI * 2 * i) / spikes;
            Location base = (focus == null ? core : focus).clone().add(Math.cos(angle) * 2.4, 0.05, Math.sin(angle) * 2.4);
            for (double y = 0; y < height; y += 0.3) {
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, y, 0), 2, 0.03, 0.05, 0.03, 0.01,
                        Material.PACKED_ICE.createBlockData());
            }
        }
        if (deathTicks % 9 == 0) {
            world.playSound(core, Sound.BLOCK_GLASS_BREAK, 0.45f, 0.8f);
        }
        if (deathTicks == 92) {
            FakeDestruction.blockBurst(world, core, Material.PACKED_ICE, 24);
            FakeDestruction.blockBurst(world, core, Material.BLUE_ICE, 12);
            world.spawnParticle(Particle.SNOWFLAKE, core, 40, 1.2, 0.8, 1.2, 0.04);
            world.playSound(core, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.4f);
            world.playSound(core, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 0.55f);
        }
        scaleToward(entity, 0.45);
    }

    private void generic(World world, Location core, LivingEntity entity) {
        world.spawnParticle(Particle.SMOKE, core, 8, 0.5, 0.4, 0.5, 0.02);
        if (deathTicks == 40) {
            world.spawnParticle(Particle.CLOUD, core, 16, 0.6, 0.4, 0.6, 0.03);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.9f);
        }
        scaleToward(entity, 0.65);
    }

    private void geyserUp(World world, Location hole, int tick) {
        double height = Math.min(7.5, tick * 0.22);
        for (double y = 0; y < height; y += 0.35) {
            world.spawnParticle(Particle.BLOCK, hole.clone().add(0, y, 0), 5, 0.15, 0.08, 0.15, 0.06,
                    Material.DIRT.createBlockData());
        }
    }

    private void toss(Location core, Material material, double speed, double lift) {
        World world = core.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Item item = world.dropItem(core.clone().add(0, 0.4, 0), new ItemStack(material));
        item.setPickupDelay(32767);
        item.setUnlimitedLifetime(true);
        item.setCanMobPickup(false);
        double yaw = random.nextDouble() * Math.PI * 2;
        item.setVelocity(new Vector(
                Math.cos(yaw) * speed,
                lift,
                Math.sin(yaw) * speed
        ));
        props.add(item);
        instance.getPlugin().getServer().getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (item.isValid()) {
                item.remove();
            }
        }, 36L);
    }

    private void pullPlayers(Location core, double strength) {
        World world = core.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Vector to = core.toVector().subtract(player.getLocation().toVector());
            double dist = to.length();
            if (dist < 2.4 || dist > 14.0) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(to.normalize().multiply(strength)));
        }
    }

    private void scaleToward(LivingEntity entity, double floorMul) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        double t = Math.min(1.0, deathTicks / (double) Math.max(1, theme.duration - 20));
        AttributeUtil.setBase(entity, AttributeUtil.scale(), Math.max(0.35, startScale * (1.0 - t * (1.0 - floorMul))));
    }

    private void clearProps() {
        Iterator<Entity> iterator = props.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            iterator.remove();
        }
    }

    private void shout(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        org.bukkit.Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(TextUtil.component(line))
        );
    }

    private enum Theme {
        NONE("", Sound.ENTITY_GENERIC_EXPLODE, 1.0f, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 80),
        COLOSSUS("&5&lAether Colossus&7: &fThe aether takes the body back.",
                Sound.ENTITY_RAVAGER_ROAR, 0.45f, Sound.BLOCK_END_PORTAL_SPAWN, 0.55f, 118),
        LURKER("&8&lHollow Lurker&7 collapses into the dark it came from.",
                Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, Sound.ENTITY_WARDEN_DEATH, 0.5f, 100),
        SKULL("&6&lSkuldugery&7: &cThe ash keeps the bones.",
                Sound.ENTITY_SKELETON_DEATH, 0.5f, Sound.ENTITY_BLAZE_DEATH, 0.55f, 112),
        NUGGET("&6&lMcNugget&7 is recalled to the fryer.",
                Sound.ENTITY_CHICKEN_AMBIENT, 0.4f, Sound.ENTITY_CHICKEN_DEATH, 0.7f, 104),
        TROLL("&6&lBridge Troll&7: &eToll's over. Keep the change.",
                Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 0.45f, Sound.BLOCK_ANVIL_LAND, 0.45f, 86),
        SQUID("&8&lSquidward&7 empties the tank.",
                Sound.ENTITY_SQUID_DEATH, 0.45f, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.55f, 122),
        BALTHAZAR("&5&lSir Balthazar&7: &dPatch reverted. Magician not found.",
                Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.55f, Sound.ITEM_TOTEM_USE, 0.65f, 122),
        BARON("&8&lBaron von Wurm&7 files back into the dirt.",
                Sound.ENTITY_SILVERFISH_AMBIENT, 0.4f, Sound.ENTITY_SILVERFISH_DEATH, 0.45f, 108),
        WITHER("&8&lThe Insolvent Wither&7: &6Assets seized. Attitude remains.",
                Sound.ENTITY_WITHER_SPAWN, 0.45f, Sound.ENTITY_WITHER_DEATH, 0.55f, 132),
        CLEANER("&5&lThe Lobby Cleaner&7: &8Bag's full. Shift's over.",
                Sound.ENTITY_ENDERMAN_STARE, 0.45f, Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 132),
        SENTINEL("&5&lPrototype Sentinel&7 sheds its last plate.",
                Sound.ENTITY_IRON_GOLEM_HURT, 0.55f, Sound.ENTITY_IRON_GOLEM_DEATH, 0.65f, 104),
        FROST("&b&lThe Frostbound&7: &fThaw complete. Floor's a lawsuit.",
                Sound.BLOCK_GLASS_BREAK, 0.5f, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.55f, 112),
        DEFAULT("&7The boss unravels.",
                Sound.ENTITY_GENERIC_EXPLODE, 0.7f, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 70);

        final String line;
        final Sound startSound;
        final float startPitch;
        final Sound endSound;
        final float endPitch;
        final int duration;

        Theme(String line, Sound startSound, float startPitch, Sound endSound, float endPitch, int duration) {
            this.line = line;
            this.startSound = startSound;
            this.startPitch = startPitch;
            this.endSound = endSound;
            this.endPitch = endPitch;
            this.duration = duration;
        }

        double motionY(int tick) {
            return switch (this) {
                case LURKER -> -Math.min(2.9, tick * 0.032);
                case BARON -> tick < 48 ? -Math.min(2.5, tick * 0.048) : Math.min(5.2, (tick - 48) * 0.16);
                case TROLL -> tick < 36 ? Math.min(2.8, tick * 0.085) : 0;
                case NUGGET -> 0.18 * Math.sin(tick * 0.35);
                case SQUID -> Math.min(1.1, tick * 0.012);
                case FROST -> Math.min(1.0, tick * 0.01);
                case COLOSSUS -> Math.min(1.9, tick * 0.02);
                case CLEANER -> Math.min(1.5, tick * 0.016);
                case WITHER -> Math.min(1.8, tick * 0.018);
                case BALTHAZAR -> Math.min(1.6, tick * 0.016);
                case SKULL -> Math.min(1.5, tick * 0.016);
                case SENTINEL -> Math.min(1.1, tick * 0.014);
                default -> Math.min(1.2, tick * 0.016);
            };
        }

        static Theme of(String id) {
            String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
            return switch (key) {
                case "aetherion", "dungeon_aetherion", "sparky", "dungeon_frostbound", "pathwarden" -> NONE;
                case "aether_colossus" -> COLOSSUS;
                case "hollow_lurker" -> LURKER;
                case "skuldugery" -> SKULL;
                case "mcnugget" -> NUGGET;
                case "bridge_troll" -> TROLL;
                case "squidward" -> SQUID;
                case "sir_balthazar" -> BALTHAZAR;
                case "baron_von_wurm" -> BARON;
                case "insolvent_wither" -> WITHER;
                case "lobby_cleaner" -> CLEANER;
                case "dungeon_sentinel" -> SENTINEL;
                default -> DEFAULT;
            };
        }
    }
}
