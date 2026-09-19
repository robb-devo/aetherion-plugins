package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floor 2 snowman: ice-shard storms, reflected hits, four hearths, then a puddle.
 */
final class FrostboundDirector {

    private static final int SHARD_COUNT = 16;
    private static final int WINDUP_TICKS = 80;
    private static final int HEARTH_WINDOW = 300;
    private static final int HEARTHS_NEEDED = 2;
    private static final int MELT_TICKS = 110;

    private final BossInstance instance;
    private final List<Entity> dress = new ArrayList<>();
    private final List<Entity> shards = new ArrayList<>();
    private final List<Entity> hearths = new ArrayList<>();
    private final List<Fireball> bolts = new ArrayList<>();

    private int deathTicks = -1;
    private int orbitTicks = -1;
    private int hearthTicks = -1;
    private int hearthHits;
    private boolean dressed;
    private boolean hearthAnnounced;
    private Location meltFocus;
    private double startScale = 2.8;
    private Material puddleWas = Material.SNOW_BLOCK;

    FrostboundDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isFrostbound() {
        return instance.getTemplate() != null
                && "dungeon_frostbound".equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    boolean reflecting() {
        return isFrostbound()
                && !isDying()
                && !instance.isTransitioning()
                && isHearthPhase();
    }

    void onBind() {
        if (!isFrostbound()) {
            return;
        }
        dressed = false;
        LivingEntity entity = instance.getEntity();
        if (entity instanceof Snowman golem) {
            golem.setDerp(false);
        }
        dress(true);
    }

    boolean beginDeath() {
        if (!isFrostbound() || isDying()) {
            return false;
        }
        clearOrbit();
        clearHearths();
        clearBolts();
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
            meltFocus = entity.getLocation().clone();
            startScale = AttributeUtil.getBase(entity, AttributeUtil.scale(), 2.8);
        } else {
            meltFocus = instance.getSpawnLocation();
        }
        deathTicks = 0;
        World world = meltFocus.getWorld();
        if (world != null) {
            world.playSound(meltFocus, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.55f);
            world.playSound(meltFocus, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 0.7f);
        }
        say("&b&lThe Frostbound&7: &fOh. That's warm. I filed a complaint.");
        return true;
    }

    boolean abort() {
        boolean dying = isDying();
        deathTicks = -1;
        orbitTicks = -1;
        hearthTicks = -1;
        hearthHits = 0;
        hearthAnnounced = false;
        dressed = false;
        clearOrbit();
        clearHearths();
        clearBolts();
        clearDress();
        return dying;
    }

    boolean tick() {
        if (!isFrostbound()) {
            return false;
        }
        if (isDying()) {
            return tickMelt();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        if (entity instanceof Snowman golem) {
            golem.setDerp(false);
        }
        dress(false);
        if (instance.isTransitioning() && iceTransition()) {
            tickOrbit(entity);
            return false;
        }
        if (orbitTicks >= 0) {
            shootShards(entity);
        }
        if (isHearthPhase()) {
            tickHearth(entity);
        } else {
            if (hearthTicks >= 0 || !hearths.isEmpty()) {
                clearHearths();
                hearthTicks = -1;
                hearthHits = 0;
            }
        }
        return false;
    }

    boolean click(Player player, Entity clicked) {
        if (!reflecting() || player == null || clicked == null) {
            return false;
        }
        Entity hearth = hearthOf(clicked);
        if (hearth == null) {
            return false;
        }
        Location from = hearth.getLocation().clone().add(0, 0.7, 0);
        removeHearth(hearth);
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid()) {
            return true;
        }
        World world = from.getWorld();
        if (world == null) {
            return true;
        }
        Location aim = boss.getLocation().clone().add(0, boss.getHeight() * 0.55, 0);
        Vector dir = aim.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.04) {
            dir = new Vector(0, 0.2, 1);
        }
        Vector launch = dir.normalize().multiply(1.15);
        Fireball bolt = world.spawn(from, Fireball.class, spawned -> {
            spawned.setShooter(player);
            spawned.setYield(0f);
            spawned.setIsIncendiary(false);
            spawned.setGravity(false);
            spawned.setGlowing(true);
            spawned.setSilent(true);
            instance.getKeys().tagHearthBolt(spawned, instance.getInstanceId());
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
            spawned.setDirection(launch);
            spawned.setVelocity(launch);
        });
        bolts.add(bolt);
        world.playSound(from, Sound.ITEM_FIRECHARGE_USE, 1.15f, 0.85f);
        world.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 0.85f, 0.7f);
        world.playSound(from, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 1.25f);
        world.spawnParticle(Particle.FLAME, from, 28, 0.25, 0.25, 0.25, 0.06);
        world.spawnParticle(Particle.LAVA, from, 8, 0.2, 0.2, 0.2, 0.02);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cHearth launched. Don't miss."));
        player.sendMessage(TextUtil.component("&7Hearth's in the air. Two hits. Clock's running."));
        return true;
    }

    void reflect(Player player, double incoming) {
        if (player == null || !player.isValid() || player.isDead()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        Location at = entity == null ? player.getLocation() : entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.85f, 1.55f);
            world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.2f);
            world.spawnParticle(Particle.SNOWFLAKE, at, 16, 0.5, 0.4, 0.5, 0.03);
            world.spawnParticle(Particle.SNOWFLAKE, at, 8, 0.3, 0.25, 0.3, 0.02);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("§bEiskalt."));
        double power = Math.max(90.0, incoming * 0.85);
        BossHits.hurt(player, entity, power);
    }

    void hearthArrived(Entity projectile) {
        if (projectile != null) {
            bolts.remove(projectile);
            if (projectile.isValid()) {
                projectile.remove();
            }
        }
        if (!reflecting()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        Location at = entity == null ? instance.getSpawnLocation() : entity.getLocation().clone().add(0, entity.getHeight() * 0.45, 0);
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.25f, 0.8f);
            world.playSound(at, Sound.ENTITY_GENERIC_BURN, 1.15f, 0.65f);
            world.playSound(at, Sound.ENTITY_BLAZE_HURT, 0.85f, 0.55f);
            world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 0.55f);
            world.spawnParticle(Particle.EXPLOSION, at, 3, 0.25, 0.2, 0.25, 0);
            world.spawnParticle(Particle.FLAME, at, 36, 0.7, 0.55, 0.7, 0.08);
            world.spawnParticle(Particle.LAVA, at, 22, 0.55, 0.45, 0.55, 0.04);
            world.spawnParticle(Particle.SMOKE, at, 18, 0.45, 0.35, 0.45, 0.03);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 10, 0.35, 0.3, 0.35, 0.02);
        }
        hearthHits++;
        say("&b&lThe Frostbound&7: &f" + hearthHits + "/" + HEARTHS_NEEDED + " &7hearths. Ice hates this.");
        if (hearthHits >= HEARTHS_NEEDED) {
            instance.killFromHearth();
        }
    }

    private boolean iceTransition() {
        String id = phaseId(instance.getPendingPhase());
        return "blizzard".equals(id) || "hearth".equals(id);
    }

    private boolean isHearthPhase() {
        return "hearth".equals(phaseId(instance.getCurrentPhase()));
    }

    private static String phaseId(de.aetherion.bossengine.model.BossPhase phase) {
        return phase == null || phase.getId() == null ? "" : phase.getId().toLowerCase();
    }

    private void tickOrbit(LivingEntity entity) {
        Location spawn = instance.getSpawnLocation();
        entity.teleport(spawn);
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        if (orbitTicks < 0) {
            spawnShards(entity);
            orbitTicks = 0;
            boolean brutal = "hearth".equals(phaseId(instance.getPendingPhase()));
            say(brutal
                    ? "&b&lThe Frostbound&7: &fSecond coat. This one bites."
                    : "&b&lThe Frostbound&7: &fShards are charging. Stand somewhere else.");
        }
        orbitTicks++;
        boolean brutal = "hearth".equals(phaseId(instance.getPendingPhase()));
        double progress = Math.min(1.0, orbitTicks / (double) WINDUP_TICKS);
        double spin = 0.07 + progress * (brutal ? 0.58 : 0.42);
        double radius = (brutal ? 4.6 : 3.4) + progress * 0.35;
        Location core = entity.getLocation().clone().add(0, entity.getHeight() * 0.55, 0);
        World world = core.getWorld();
        for (int i = 0; i < shards.size(); i++) {
            Entity shard = shards.get(i);
            if (shard == null || !shard.isValid()) {
                continue;
            }
            double angle = orbitTicks * spin + (Math.PI * 2 * i) / Math.max(1, shards.size());
            Location at = core.clone().add(Math.cos(angle) * radius, Math.sin(angle * 2) * 0.35, Math.sin(angle) * radius);
            shard.teleport(at);
        }
        if (world != null) {
            world.spawnParticle(Particle.SNOWFLAKE, core, brutal ? 10 : 6, radius * 0.4, 0.4, radius * 0.4, 0.01);
            if (orbitTicks % 8 == 0) {
                world.playSound(core, Sound.BLOCK_GLASS_HIT, 0.55f + (float) progress * 0.5f, 0.7f + (float) progress);
            }
            if (orbitTicks % 16 == 0) {
                world.playSound(core, Sound.ENTITY_SNOW_GOLEM_HURT, 0.7f, 0.45f);
            }
        }
    }

    private void spawnShards(LivingEntity entity) {
        clearOrbit();
        World world = entity.getWorld();
        if (world == null) {
            return;
        }
        Location core = entity.getLocation().clone().add(0, entity.getHeight() * 0.55, 0);
        boolean brutal = "hearth".equals(phaseId(instance.getPendingPhase()));
        Material ice = brutal ? Material.BLUE_ICE : Material.PACKED_ICE;
        float size = brutal ? 0.55f : 0.42f;
        for (int i = 0; i < SHARD_COUNT; i++) {
            BlockDisplay display = world.spawn(core, BlockDisplay.class, spawned -> {
                spawned.setBlock(ice.createBlockData());
                spawned.setTransformation(scaled(-size / 2f, -size / 2f, -size / 2f, size));
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setTeleportDuration(1);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(Color.fromRGB(160, 220, 255));
                instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
            });
            shards.add(display);
        }
        world.playSound(core, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.55f);
        world.playSound(core, Sound.ENTITY_SNOW_GOLEM_AMBIENT, 1.1f, 0.5f);
    }

    private void shootShards(LivingEntity entity) {
        boolean brutal = isHearthPhase() || "hearth".equals(phaseId(instance.getPendingPhase()));
        double damage = instance.scaleDamage(brutal ? 175.0 : 118.0);
        Location core = entity.getLocation().clone().add(0, entity.getHeight() * 0.45, 0);
        World world = core.getWorld();
        if (world != null) {
            world.playSound(core, Sound.BLOCK_GLASS_BREAK, 1.35f, 0.45f);
            world.playSound(core, Sound.ENTITY_WITHER_SHOOT, 0.55f, 1.4f);
            world.spawnParticle(Particle.SNOWFLAKE, core, 30, 1.2, 0.6, 1.2, 0.04);
            world.spawnParticle(Particle.SNOWFLAKE, core, 18, 0.8, 0.4, 0.8, 0.08);
        }
        Player target = nearestPlayer(entity, 28);
        for (Entity shard : shards) {
            if (shard == null || !shard.isValid() || world == null) {
                continue;
            }
            Location from = shard.getLocation();
            Vector away = from.toVector().subtract(core.toVector());
            if (away.lengthSquared() < 0.04) {
                away = new Vector(ThreadLocalRandom.current().nextGaussian(), 0.1, ThreadLocalRandom.current().nextGaussian());
            }
            away.setY(0.12);
            away.normalize().multiply(brutal ? 1.55 : 1.25);
            if (target != null && ThreadLocalRandom.current().nextDouble() < (brutal ? 0.55 : 0.35)) {
                Vector home = target.getEyeLocation().toVector().subtract(from.toVector());
                if (home.lengthSquared() > 0.04) {
                    away = away.multiply(0.45).add(home.normalize().multiply(brutal ? 1.15 : 0.9));
                }
            }
            Vector velocity = away;
            world.spawn(from, Snowball.class, spawned -> {
                spawned.setShooter(entity);
                spawned.setItem(new ItemStack(brutal ? Material.BLUE_ICE : Material.PACKED_ICE));
                instance.getKeys().tagIceShard(spawned, damage, 100);
                spawned.setVelocity(velocity);
            });
            shard.remove();
        }
        shards.clear();
        orbitTicks = -1;
    }

    private void tickHearth(LivingEntity entity) {
        entity.teleport(instance.getSpawnLocation());
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (hearthTicks < 0) {
            startHearthWindow(entity);
        }
        hearthTicks++;
        tickBolts(entity);
        tickHearthVisuals(entity);
        Location core = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        World world = core.getWorld();
        if (world != null && hearthTicks % 8 == 0) {
            world.spawnParticle(Particle.SNOWFLAKE, core, 8, 0.8, 0.6, 0.8, 0.01);
            world.spawnParticle(Particle.END_ROD, core, 2, 0.3, 0.4, 0.3, 0.01);
        }
        int left = Math.max(0, HEARTH_WINDOW - hearthTicks);
        if (hearthTicks % 20 == 0) {
            for (Player player : nearby(entity, 48)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§cHearths §f" + hearthHits + "/" + HEARTHS_NEEDED
                                + "  §8·  §b" + (left / 20) + "s"
                ));
            }
        }
        if (hearthTicks >= HEARTH_WINDOW) {
            failWindow(entity);
        }
    }

    private void startHearthWindow(LivingEntity entity) {
        clearHearths();
        clearBolts();
        hearthHits = 0;
        hearthTicks = 0;
        World world = entity.getWorld();
        if (world == null) {
            return;
        }
        if (!hearthAnnounced) {
            hearthAnnounced = true;
            say("&b&lThe Frostbound&7: &fHands off. I'm reflecting.");
            say("&7Four hearths. Two hits. Fifteen seconds. Don't make me refill.");
        } else {
            say("&b&lThe Frostbound&7: &fTimer's up. Ice heals. Try again, warmer.");
        }
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.05f, 0.7f);
        world.playSound(entity.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.4f);
        for (Location spot : hearthSpots(entity)) {
            spawnHearth(spot);
        }
    }

    private void failWindow(LivingEntity entity) {
        double heal = instance.getCombatMaxHealth() * 0.22;
        instance.healCombat(heal);
        World world = entity.getWorld();
        if (world != null) {
            world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.1f, 0.55f);
            world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);
            world.spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 1.2, 0), 40, 1.1, 0.8, 1.1, 0.04);
        }
        startHearthWindow(entity);
    }

    private List<Location> hearthSpots(LivingEntity entity) {
        Location spawn = instance.getSpawnLocation();
        World world = spawn.getWorld();
        List<Location> spots = new ArrayList<>();
        double leash = Math.max(16.0, instance.getConditions().getLeashRadius());
        double reach = Math.min(20.0, leash * 0.72);
        // Endless XL test arena: keep hearths in a small clickable square (not Floor 2).
        if (instance.getKeys().hasCompactHearths(entity)) {
            reach = 8.0;
        }
        double[][] offsets = {{reach, reach}, {reach, -reach}, {-reach, reach}, {-reach, -reach}};
        for (double[] offset : offsets) {
            Location candidate = spawn.clone().add(offset[0], 0, offset[1]);
            if (world != null) {
                Vector dir = new Vector(offset[0], 0, offset[1]);
                if (dir.lengthSquared() > 0.2) {
                    RayTraceResult hit = world.rayTraceBlocks(
                            spawn.clone().add(0, 1.1, 0),
                            dir.normalize(),
                            reach,
                            FluidCollisionMode.NEVER,
                            true
                    );
                    if (hit != null && hit.getHitPosition() != null) {
                        Vector pos = hit.getHitPosition();
                        candidate = new Location(world, pos.getX(), spawn.getY(), pos.getZ());
                        Vector back = spawn.toVector().subtract(candidate.toVector());
                        if (back.lengthSquared() > 0.2) {
                            candidate.add(back.normalize().multiply(2.2));
                        }
                    }
                }
                candidate.setY(spawn.getY());
                while (candidate.getBlock().getType().isSolid() && candidate.getY() < spawn.getY() + 4) {
                    candidate.add(0, 1, 0);
                }
            }
            spots.add(candidate);
        }
        return spots;
    }

    private void spawnHearth(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ArmorStand stand = world.spawn(location, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(false);
            spawned.setSmall(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setGlowing(true);
            spawned.setCustomNameVisible(true);
            spawned.customName(TextUtil.component("&cHearth &7· click"));
            instance.getKeys().tagFrostHearth(spawned, instance.getInstanceId());
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
            EntityEquipment equipment = spawned.getEquipment();
            if (equipment != null) {
                ItemStack helm = new ItemStack(Material.MAGMA_CREAM);
                equipment.setHelmet(helm);
                ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
                if (chest.getItemMeta() instanceof LeatherArmorMeta meta) {
                    meta.setColor(Color.fromRGB(255, 90, 20));
                    chest.setItemMeta(meta);
                }
                equipment.setChestplate(chest);
            }
        });
        ItemDisplay flame = world.spawn(location.clone().add(0, 0.7, 0), ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(Material.FIRE_CHARGE));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(Color.fromRGB(255, 90, 20));
            spawned.setTeleportDuration(1);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.45f, -0.45f, -0.45f),
                    new AxisAngle4f(),
                    new Vector3f(0.95f, 0.95f, 0.95f),
                    new AxisAngle4f()
            ));
            instance.getKeys().tagFrostHearth(spawned, instance.getInstanceId());
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
        Interaction hit = world.spawn(location.clone().add(0, 0.15, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.15f);
            spawned.setInteractionHeight(1.45f);
            spawned.setResponsive(true);
            instance.getKeys().tagFrostHearth(spawned, instance.getInstanceId());
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
        stand.addPassenger(flame);
        stand.addPassenger(hit);
        hearths.add(stand);
        hearths.add(flame);
        hearths.add(hit);
        world.spawnParticle(Particle.FLAME, location.clone().add(0, 0.7, 0), 22, 0.25, 0.3, 0.25, 0.03);
        world.spawnParticle(Particle.LAVA, location.clone().add(0, 0.5, 0), 6, 0.15, 0.2, 0.15, 0.01);
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 0.7f, 0.65f);
        world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.55f, 1.15f);
    }

    private void tickHearthVisuals(LivingEntity entity) {
        World world = entity.getWorld();
        if (world == null) {
            return;
        }
        float spin = (hearthTicks * 0.28f) % ((float) Math.PI * 2f);
        for (Entity hearth : hearths) {
            if (hearth instanceof ItemDisplay display && display.isValid()) {
                display.setTransformation(new Transformation(
                        new Vector3f(-0.45f, -0.45f, -0.45f),
                        new AxisAngle4f(spin, 0f, 1f, 0f),
                        new Vector3f(0.95f, 0.95f, 0.95f),
                        new AxisAngle4f()
                ));
            }
            if (!(hearth instanceof ArmorStand stand) || !stand.isValid()) {
                continue;
            }
            if (hearthTicks % 2 != 0) {
                continue;
            }
            Location at = stand.getLocation().clone().add(0, 0.75, 0);
            world.spawnParticle(Particle.FLAME, at, 5, 0.18, 0.22, 0.18, 0.012);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 2, 0.12, 0.16, 0.12, 0.008);
            if (hearthTicks % 8 == 0) {
                world.spawnParticle(Particle.LAVA, at, 1, 0.08, 0.08, 0.08, 0);
            }
        }
    }

    private void tickBolts(LivingEntity entity) {
        Location aim = entity.getLocation().clone().add(0, entity.getHeight() * 0.55, 0);
        Iterator<Fireball> iterator = bolts.iterator();
        while (iterator.hasNext()) {
            Fireball bolt = iterator.next();
            if (bolt == null || !bolt.isValid()) {
                iterator.remove();
                continue;
            }
            Vector to = aim.toVector().subtract(bolt.getLocation().toVector());
            if (to.lengthSquared() < 2.6) {
                iterator.remove();
                hearthArrived(bolt);
                continue;
            }
            Vector homing = to.normalize().multiply(1.28);
            bolt.setDirection(homing);
            bolt.setVelocity(homing);
            Location at = bolt.getLocation();
            World world = bolt.getWorld();
            world.spawnParticle(Particle.FLAME, at, 10, 0.16, 0.16, 0.16, 0.03);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 4, 0.1, 0.1, 0.1, 0.015);
            world.spawnParticle(Particle.LAVA, at, 2, 0.08, 0.08, 0.08, 0);
            world.spawnParticle(Particle.SMOKE, at, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private Entity hearthOf(Entity clicked) {
        Entity current = clicked;
        for (int i = 0; i < 3 && current != null; i++) {
            if (instance.getKeys().isFrostHearth(current)
                    && instance.getKeys().frostHearthInstance(current)
                    .filter(id -> id.equals(instance.getInstanceId()))
                    .isPresent()) {
                Entity vehicle = current.getVehicle();
                return vehicle != null && instance.getKeys().isFrostHearth(vehicle) ? vehicle : current;
            }
            current = current.getVehicle();
        }
        return null;
    }

    private void removeHearth(Entity hearth) {
        List<Entity> drop = new ArrayList<>();
        drop.add(hearth);
        drop.addAll(hearth.getPassengers());
        Entity vehicle = hearth.getVehicle();
        if (vehicle != null) {
            drop.add(vehicle);
            drop.addAll(vehicle.getPassengers());
        }
        for (Entity entity : drop) {
            hearths.remove(entity);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private boolean tickMelt() {
        LivingEntity entity = instance.getEntity();
        boolean body = entity != null && entity.isValid() && !entity.isDead();
        deathTicks++;
        double t = Math.min(1.0, deathTicks / (double) MELT_TICKS);
        Location hold = (meltFocus == null
                ? (body ? entity.getLocation() : instance.getSpawnLocation())
                : meltFocus).clone();
        if (body) {
            entity.teleport(hold);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setFallDistance(0);
            AttributeUtil.setBase(entity, AttributeUtil.scale(), Math.max(0.18, startScale * (1.0 - t * 0.92)));
        }
        World world = hold.getWorld();
        Location core = hold.clone().add(0, Math.max(0.2, (body ? entity.getHeight() : 1.8) * 0.35), 0);
        if (world != null) {
            world.spawnParticle(Particle.DRIPPING_WATER, core, 8, 0.35, 0.25, 0.35, 0);
            world.spawnParticle(Particle.CLOUD, core, 4, 0.3, 0.15, 0.3, 0.01);
            world.spawnParticle(Particle.SNOWFLAKE, core, 6, 0.5, 0.3, 0.5, 0.01);
            if (deathTicks % 7 == 0) {
                world.playSound(core, Sound.BLOCK_LAVA_EXTINGUISH, 0.45f, 0.8f + (float) t);
                world.playSound(core, Sound.BLOCK_SNOW_BREAK, 0.5f, 0.7f);
            }
        }
        if (deathTicks >= MELT_TICKS) {
            dropPuddle(hold);
            say("&b&lThe Frostbound&7: &7That's a puddle. Floor 2 is over. Bring a bucket for the lawsuit.");
            if (world != null) {
                world.playSound(hold, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.7f);
                world.playSound(hold, Sound.BLOCK_WATER_AMBIENT, 1.0f, 1.1f);
                world.spawnParticle(Particle.CLOUD, hold.clone().add(0, 0.2, 0), 24, 0.5, 0.1, 0.5, 0.08);
                world.spawnParticle(Particle.CLOUD, hold, 16, 0.6, 0.2, 0.6, 0.02);
            }
            clearDress();
            return true;
        }
        return false;
    }

    private void dropPuddle(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        Block block = at.getBlock();
        puddleWas = block.getType().isAir() ? at.clone().subtract(0, 1, 0).getBlock().getType() : block.getType();
        Block floor = block.getType().isAir() ? at.clone().subtract(0, 1, 0).getBlock() : block;
        Material previous = floor.getType();
        floor.setType(Material.WATER_CAULDRON, false);
        instance.getPlugin().getServer().getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (floor.getType() == Material.WATER_CAULDRON) {
                floor.setType(previous.isAir() ? Material.SNOW_BLOCK : previous, false);
            }
        }, 400L);
        if (puddleWas == Material.AIR) {
            puddleWas = Material.SNOW_BLOCK;
        }
    }

    private void dress(boolean force) {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (!force && dressed && !dress.isEmpty()) {
            poseDress(entity);
            return;
        }
        clearDress();
        World world = entity.getWorld();
        if (world == null) {
            return;
        }
        Location at = entity.getLocation();
        dress.add(block(world, at, Material.SNOW_BLOCK, 1.35f, Color.fromRGB(240, 250, 255)));
        dress.add(block(world, at, Material.SNOW_BLOCK, 1.05f, Color.fromRGB(230, 245, 255)));
        dress.add(block(world, at, Material.PACKED_ICE, 0.38f, Color.fromRGB(140, 210, 255)));
        dress.add(block(world, at, Material.BLUE_ICE, 0.28f, Color.fromRGB(90, 180, 255)));
        dress.add(item(world, at, Material.STICK, 0.9f));
        dress.add(item(world, at, Material.STICK, 0.9f));
        dress.add(item(world, at, Material.CARROT, 0.35f));
        dressed = true;
        poseDress(entity);
    }

    private BlockDisplay block(World world, Location at, Material material, float size, Color glow) {
        return world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setTransformation(scaled(-size / 2f, 0f, -size / 2f, size));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(1);
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(glow);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
    }

    private ItemDisplay item(World world, Location at, Material material, float size) {
        return world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(material));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setTransformation(scaled(-size / 2f, 0f, -size / 2f, size));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(1);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
    }

    private void poseDress(LivingEntity entity) {
        Location base = entity.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double height = Math.max(1.6, entity.getHeight());
        double[][] offsets = {
                {0, 0.12, 0},
                {0, height * 0.42, 0},
                {0.18, height * 0.40, 0.42},
                {0.12, height * 0.52, 0.38},
                {height * 0.55, height * 0.48, 0.05},
                {-height * 0.55, height * 0.48, 0.05},
                {0.05, height * 0.78, 0.42}
        };
        for (int i = 0; i < dress.size() && i < offsets.length; i++) {
            Entity piece = dress.get(i);
            if (piece == null || !piece.isValid()) {
                continue;
            }
            double lx = offsets[i][0];
            double ly = offsets[i][1];
            double lz = offsets[i][2];
            double x = lx * Math.cos(-yaw) - lz * Math.sin(-yaw);
            double z = lx * Math.sin(-yaw) + lz * Math.cos(-yaw);
            Location at = base.clone().add(x, ly, z);
            at.setYaw(base.getYaw() + (i == 5 ? 180 : i == 4 ? 0 : 0));
            piece.teleport(at);
        }
    }

    private static Transformation scaled(float tx, float ty, float tz, float size) {
        return new Transformation(
                new Vector3f(tx, ty, tz),
                new AxisAngle4f(),
                new Vector3f(size, size, size),
                new AxisAngle4f()
        );
    }

    private void clearOrbit() {
        for (Entity entity : shards) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        shards.clear();
    }

    private void clearHearths() {
        for (Entity entity : hearths) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        hearths.clear();
    }

    private void clearBolts() {
        for (Fireball bolt : bolts) {
            if (bolt != null && bolt.isValid()) {
                bolt.remove();
            }
        }
        bolts.clear();
    }

    private void clearDress() {
        for (Entity entity : dress) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        dress.clear();
        dressed = false;
    }

    private void say(String line) {
        Location at = instance.getEntity() != null && instance.getEntity().isValid()
                ? instance.getEntity().getLocation()
                : instance.getSpawnLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 80 * 80) {
                player.sendMessage(TextUtil.component(line));
            }
        }
    }

    private List<Player> nearby(LivingEntity entity, double radius) {
        List<Player> found = new ArrayList<>();
        World world = entity.getWorld();
        double max = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(entity.getLocation()) <= max) {
                found.add(player);
            }
        }
        return found;
    }

    private Player nearestPlayer(LivingEntity entity, double radius) {
        Player best = null;
        double bestDist = radius * radius;
        for (Player player : nearby(entity, radius)) {
            double dist = player.getLocation().distanceSquared(entity.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }
}
