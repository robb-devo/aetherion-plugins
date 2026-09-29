package de.aetherion.mining.isle;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Things that live in the rock. They're built from display entities, not mobs, so they work in a
 * peaceful dig world, never trip the natural-spawn guards and can't be farmed with a spawner. Every
 * one has a job:
 * <ul>
 *   <li><b>Cinder Mite</b> (Emberseam, deep bands): skitters out of hot ore and bites (burns).
 *       Two hits. Drops coal.</li>
 *   <li><b>Gloam Moth</b> (Rootdeep Grotto): harmless and quick, it bolts. Catch it for
 *       <i>Gloam-lit</i> (Crystal Finds ×1.5 for a minute) and glow berries.</li>
 *   <li><b>Stonejaw</b> (Deep Works and below, after a Seam Burst): a cart-sized ore guardian.
 *       Telegraphed ground slams. When it dies it leaves a guaranteed Flawless-or-better geode, an
 *       ore burst and its jaw (Hollis pays for it).</li>
 *   <li><b>Shardling</b> (Amethyst Mine, high Resonance): a floating crystal that locks on and
 *       zaps. Drops amethyst shards.</li>
 * </ul>
 * Damage scales with max health, so a Hollow Sun tank and a fresh miner feel it the same. Heat Ward
 * and the Canary Cage cut it.
 */
public final class MineCritters implements Listener {

    public enum Kind {
        CINDER_MITE("Cinder Mite", "§6", 2, 0.34d, false, 20 * 40, 3),
        GLOAM_MOTH("Gloam Moth", "§b", 1, 0.42d, true, 20 * 20, 2),
        STONEJAW("Stonejaw", "§4", 16, 0.22d, false, 20 * 120, 1),
        SHARDLING("Shardling", "§d", 3, 0.3d, true, 20 * 45, 4);

        final String display;
        final String color;
        final int health;
        final double speed;
        final boolean flies;
        final int life;
        final int perPlayer;

        Kind(String display, String color, int health, double speed, boolean flies, int life, int perPlayer) {
            this.display = display;
            this.color = color;
            this.health = health;
            this.speed = speed;
            this.flies = flies;
            this.life = life;
            this.perPlayer = perPlayer;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String colored() {
            return color + display;
        }

        public static Kind byId(String id) {
            if (id == null) {
                return null;
            }
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static final int TICK_STEP = 2;
    private static final int GLOBAL_CAP = 32;
    private static final long STONEJAW_COOLDOWN_MS = 10L * 60_000L;

    private final class Critter {
        final UUID id = UUID.randomUUID();
        final Kind kind;
        final List<Entity> parts = new ArrayList<>();
        final int bornTick;
        Display body;
        BlockDisplay jaw;
        TextDisplay label;
        Interaction hitbox;
        Location at;
        UUID target;
        int hp;
        int nextAttack;
        int telegraphUntil;
        Location telegraphAt;
        double phase;
        boolean dead;

        Critter(Kind kind, Location at, UUID target) {
            this.kind = kind;
            this.at = at.clone();
            this.target = target;
            this.hp = kind.health;
            this.bornTick = Bukkit.getCurrentTick();
            this.nextAttack = bornTick + 30;
            this.phase = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d);
        }
    }

    private final MineIsle isle;
    private final NamespacedKey partKey;
    private final Map<UUID, Critter> byPart = new ConcurrentHashMap<>();
    private final Map<UUID, Critter> all = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stonejawCool = new ConcurrentHashMap<>();
    private BukkitTask task;

    MineCritters(MineIsle isle) {
        this.isle = isle;
        this.partKey = new NamespacedKey(isle.plugin(), "mine_critter");
    }

    void start() {
        purgeStrays();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 20L, TICK_STEP);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Critter critter : List.copyOf(all.values())) {
            remove(critter);
        }
    }

    /** Quit: forget an expired Stonejaw cooldown (a running one survives a relog). */
    void forget(UUID id) {
        Long cool = stonejawCool.get(id);
        if (cool != null && cool <= System.currentTimeMillis()) {
            stonejawCool.remove(id);
        }
    }

    public int count() {
        return all.size();
    }

    private int countFor(UUID player, Kind kind) {
        int n = 0;
        for (Critter critter : all.values()) {
            if (critter.kind == kind && player.equals(critter.target)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ spawning rules (isle)

    /** Strike hook: mites in the heat, moths in the grotto, the odd Stonejaw after a deep Seam Burst. */
    void onMined(Player player, IsleOre ore, Location at, MineDistricts.Band band) {
        if (!isle.plugin().getConfig().getBoolean("critters.enabled", true) || ore == IsleOre.STONE) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        MineDistricts.District district = isle.districts().at(at);
        String where = district == null ? "" : district.id();
        if (where.startsWith("emberseam") ? rng.nextDouble() < 0.05d : band.deep() && rng.nextDouble() < 0.012d) {
            spawnNear(Kind.CINDER_MITE, player, at);
        }
        if (where.equals("rootdeep") && rng.nextDouble() < 0.03d) {
            spawnNear(Kind.GLOAM_MOTH, player, at);
        }
    }

    /** A Seam Burst below the Deep Works line may wake a Stonejaw. */
    void onSeamBurst(Player player, Location at) {
        if (at == null || !isle.districts().band(at).deep() || !isle.plugin().getConfig().getBoolean("critters.enabled", true)) {
            return;
        }
        Long cool = stonejawCool.get(player.getUniqueId());
        if (cool != null && cool > System.currentTimeMillis()) {
            return;
        }
        for (Critter critter : all.values()) {
            if (critter.kind == Kind.STONEJAW) {
                return;
            }
        }
        double chance = isle.plugin().getConfig().getDouble("critters.stonejaw-chance", 0.12d)
                * (isle.events().kind() == MineEvents.Kind.TREMOR ? 2.0d : 1.0d);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        stonejawCool.put(player.getUniqueId(), System.currentTimeMillis() + STONEJAW_COOLDOWN_MS);
        Location spot = floorNear(at, 4);
        if (spot == null) {
            return;
        }
        World world = spot.getWorld();
        world.playSound(spot, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 0.8f, 1.3f);
        world.spawnParticle(Particle.BLOCK, spot, 80, 1.0, 0.4, 1.0, 0.1, Material.DEEPSLATE.createBlockData());
        for (Player visitor : MineWorld.visitors(isle.plugin())) {
            if (visitor.getWorld().equals(world) && visitor.getLocation().distanceSquared(spot) < 64.0d * 64.0d) {
                visitor.sendMessage("§4☠ The rock opens its mouth. §cA Stonejaw §7has woken up near §f" + player.getName()
                        + "§7. §8Break its jaw, keep what it guards.");
            }
        }
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> spawn(Kind.STONEJAW, spot, player), 30L);
    }

    public boolean spawnNear(Kind kind, Player player, Location at) {
        if (countFor(player.getUniqueId(), kind) >= kind.perPlayer || all.size() >= GLOBAL_CAP) {
            return false;
        }
        Location spot = kind.flies ? airNear(at) : floorNear(at, 3);
        if (spot == null) {
            return false;
        }
        return spawn(kind, spot, player) != null;
    }

    // ------------------------------------------------------------------ building a critter

    Critter spawn(Kind kind, Location at, Player target) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        Critter critter = new Critter(kind, at, target == null ? null : target.getUniqueId());
        switch (kind) {
            case CINDER_MITE -> {
                critter.body = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(Material.MAGMA_BLOCK.createBlockData());
                    spawned.setTransformation(scale(0.38f, 0.26f, 0.5f, 0.0f));
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(Color.fromRGB(255, 110, 20));
                    spawned.setBrightness(new Display.Brightness(12, 12));
                    spawned.setTeleportDuration(TICK_STEP);
                    tag(spawned);
                });
            }
            case GLOAM_MOTH -> {
                ItemStack berries = new ItemStack(Material.GLOW_BERRIES);
                critter.body = world.spawn(at, ItemDisplay.class, spawned -> {
                    spawned.setItemStack(berries);
                    spawned.setBillboard(Display.Billboard.CENTER);
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(Color.fromRGB(120, 255, 230));
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setTransformation(scale(0.55f, 0.55f, 0.55f, 0.0f));
                    spawned.setTeleportDuration(TICK_STEP);
                    tag(spawned);
                });
            }
            case STONEJAW -> {
                critter.body = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(Material.DEEPSLATE_BRICKS.createBlockData());
                    spawned.setTransformation(scale(1.7f, 1.2f, 1.7f, 0.0f));
                    spawned.setTeleportDuration(TICK_STEP);
                    tag(spawned);
                });
                critter.jaw = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(Material.DEEPSLATE_DIAMOND_ORE.createBlockData());
                    spawned.setTransformation(scale(1.5f, 0.45f, 1.5f, 1.25f));
                    spawned.setTeleportDuration(TICK_STEP);
                    tag(spawned);
                });
                critter.parts.add(critter.jaw);
                for (int side = -1; side <= 1; side += 2) {
                    float offset = side * 0.45f;
                    BlockDisplay eye = world.spawn(at, BlockDisplay.class, spawned -> {
                        spawned.setBlock(Material.SHROOMLIGHT.createBlockData());
                        spawned.setTransformation(new Transformation(new Vector3f(offset - 0.1f, 0.85f, -0.9f), new Quaternionf(),
                                new Vector3f(0.22f, 0.18f, 0.08f), new Quaternionf()));
                        spawned.setGlowing(true);
                        spawned.setGlowColorOverride(Color.fromRGB(255, 60, 30));
                        spawned.setBrightness(new Display.Brightness(15, 15));
                        spawned.setTeleportDuration(TICK_STEP);
                        tag(spawned);
                    });
                    critter.parts.add(eye);
                }
            }
            case SHARDLING -> {
                ItemStack cluster = new ItemStack(Material.AMETHYST_CLUSTER);
                critter.body = world.spawn(at, ItemDisplay.class, spawned -> {
                    spawned.setItemStack(cluster);
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(Color.fromRGB(200, 110, 255));
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setTransformation(scale(0.9f, 0.9f, 0.9f, 0.0f));
                    spawned.setTeleportDuration(TICK_STEP);
                    tag(spawned);
                });
            }
        }
        critter.parts.add(0, critter.body);
        double labelLift = kind == Kind.STONEJAW ? 2.2d : kind.flies ? 0.9d : 0.7d;
        critter.label = world.spawn(at.clone().add(0, labelLift, 0), TextDisplay.class, text -> {
            text.text(MineText.legacy(labelText(critter)));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setTeleportDuration(TICK_STEP);
            text.setViewRange(0.25f);
            tag(text);
        });
        critter.parts.add(critter.label);
        float width = kind == Kind.STONEJAW ? 1.9f : kind == Kind.SHARDLING ? 0.9f : 0.7f;
        float height = kind == Kind.STONEJAW ? 1.8f : kind == Kind.SHARDLING ? 0.9f : 0.5f;
        critter.hitbox = world.spawn(at, Interaction.class, spawned -> {
            spawned.setInteractionWidth(width);
            spawned.setInteractionHeight(height);
            spawned.setResponsive(true);
            tag(spawned);
        });
        critter.parts.add(critter.hitbox);
        all.put(critter.id, critter);
        for (Entity part : critter.parts) {
            byPart.put(part.getUniqueId(), critter);
        }
        switch (kind) {
            case CINDER_MITE -> {
                world.playSound(at, Sound.ENTITY_SILVERFISH_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.6f);
                world.spawnParticle(Particle.FLAME, at, 8, 0.2, 0.1, 0.2, 0.02);
            }
            case GLOAM_MOTH -> world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.7f, 1.8f);
            case STONEJAW -> {
                world.playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.HOSTILE, 1.0f, 0.5f);
                world.playSound(at, Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
            case SHARDLING -> {
                world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.HOSTILE, 1.0f, 0.7f);
                world.spawnParticle(Particle.END_ROD, at, 10, 0.3, 0.3, 0.3, 0.02);
            }
        }
        return critter;
    }

    private static Transformation scale(float x, float y, float z, float lift) {
        return new Transformation(new Vector3f(-x / 2.0f, lift, -z / 2.0f), new AxisAngle4f(),
                new Vector3f(x, y, z), new AxisAngle4f());
    }

    private String labelText(Critter critter) {
        int filled = (int) Math.ceil(10.0d * critter.hp / critter.kind.health);
        String bar = critter.kind.health <= 3
                ? "§c" + "❤".repeat(Math.max(0, critter.hp)) + "§8" + "❤".repeat(Math.max(0, critter.kind.health - critter.hp))
                : "§c" + "▮".repeat(Math.max(0, filled)) + "§8" + "▯".repeat(Math.max(0, 10 - filled));
        String prefix = critter.kind == Kind.STONEJAW ? "§4☠ " : "";
        return prefix + critter.kind.colored() + "\n" + bar;
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(partKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void remove(Critter critter) {
        critter.dead = true;
        all.remove(critter.id);
        for (Entity part : critter.parts) {
            byPart.remove(part.getUniqueId());
            if (part.isValid()) {
                part.remove();
            }
        }
    }

    // ------------------------------------------------------------------ movement and attacks

    private void tick() {
        if (all.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (Critter critter : List.copyOf(all.values())) {
            if (critter.dead) {
                continue;
            }
            if (critter.body == null || !critter.body.isValid() || now - critter.bornTick > critter.kind.life) {
                fizzle(critter);
                continue;
            }
            Player target = critter.target == null ? null : Bukkit.getPlayer(critter.target);
            if (target == null || !target.isOnline() || target.isDead() || !target.getWorld().equals(critter.at.getWorld())
                    || target.getLocation().distanceSquared(critter.at) > 40.0d * 40.0d) {
                target = nearestPlayer(critter.at, 24.0d);
                critter.target = target == null ? null : target.getUniqueId();
            }
            critter.phase += 0.35d;
            move(critter, target);
            if (target != null) {
                attack(critter, target, now);
            }
        }
    }

    private void move(Critter critter, Player target) {
        Location from = critter.at;
        Vector heading;
        if (target == null) {
            heading = new Vector(Math.cos(critter.phase * 0.3d), 0, Math.sin(critter.phase * 0.3d)).multiply(0.3d);
        } else {
            Location goal = target.getLocation();
            if (critter.kind.flies) {
                goal = goal.clone().add(0, 1.6d + Math.sin(critter.phase) * 0.3d, 0);
            }
            heading = goal.toVector().subtract(from.toVector());
            double distance = heading.length();
            double keep = switch (critter.kind) {
                case SHARDLING -> 4.5d;
                case STONEJAW -> 1.6d;
                case CINDER_MITE -> 0.8d;
                case GLOAM_MOTH -> 0.0d;
            };
            if (critter.kind == Kind.GLOAM_MOTH) {
                heading.multiply(-1.0d);
                if (distance > 10.0d) {
                    heading = new Vector(Math.cos(critter.phase), 0.2d, Math.sin(critter.phase));
                }
            } else if (distance <= keep) {
                heading = new Vector();
            }
            if (critter.telegraphUntil > Bukkit.getCurrentTick()) {
                heading = new Vector();
            }
        }
        if (heading.lengthSquared() > 1.0E-4) {
            heading.normalize().multiply(critter.kind.speed);
        }
        Location next = from.clone().add(heading);
        if (critter.kind.flies) {
            next.setY(next.getY() + Math.sin(critter.phase) * 0.04d);
            if (!next.getBlock().isPassable()) {
                next = from.clone().add(0, 0.3d, 0);
                if (!next.getBlock().isPassable()) {
                    next = from.clone();
                }
            }
        } else {
            Location floor = floorNear(next, 2);
            next = floor != null && floor.distanceSquared(from) < 9.0d ? floor : from.clone();
        }
        if (heading.lengthSquared() > 1.0E-4) {
            next.setYaw((float) Math.toDegrees(Math.atan2(-heading.getX(), heading.getZ())));
        } else {
            next.setYaw(from.getYaw());
        }
        critter.at = next;
        float spin = critter.kind == Kind.SHARDLING || critter.kind == Kind.GLOAM_MOTH ? (float) (critter.phase * 0.8d) : 0.0f;
        for (Entity part : critter.parts) {
            if (!part.isValid()) {
                continue;
            }
            double lift = part == critter.label ? (critter.kind == Kind.STONEJAW ? 2.2d : critter.kind.flies ? 0.9d : 0.7d) : 0.0d;
            Location dest = next.clone().add(0, lift, 0);
            if (part instanceof Interaction) {
                dest = next.clone();
            }
            part.teleport(dest);
        }
        if (spin != 0.0f && critter.body instanceof ItemDisplay item) {
            float s = critter.kind == Kind.SHARDLING ? 0.9f : 0.55f;
            item.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(spin), new Vector3f(s, s, s),
                    new Quaternionf()));
        }
        if (critter.kind == Kind.CINDER_MITE && ThreadLocalRandom.current().nextInt(4) == 0) {
            next.getWorld().spawnParticle(Particle.SMALL_FLAME, next.clone().add(0, 0.2, 0), 1, 0.1, 0.0, 0.1, 0.0);
        }
        if (critter.kind == Kind.GLOAM_MOTH && ThreadLocalRandom.current().nextInt(3) == 0) {
            next.getWorld().spawnParticle(Particle.GLOW, next, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void attack(Critter critter, Player target, int now) {
        double distance = target.getLocation().distance(critter.at);
        switch (critter.kind) {
            case CINDER_MITE -> {
                if (distance <= 1.3d && now >= critter.nextAttack) {
                    critter.nextAttack = now + 25;
                    hurt(target, 0.05d);
                    target.setFireTicks(Math.max(target.getFireTicks(), (int) Math.round(40 * isle.forge().hazardFactor(target))));
                    target.playSound(target.getLocation(), Sound.ENTITY_SILVERFISH_HURT, SoundCategory.HOSTILE, 0.8f, 0.6f);
                }
            }
            case SHARDLING -> {
                if (distance > 7.5d) {
                    critter.telegraphUntil = 0;
                    return;
                }
                if (critter.telegraphUntil == 0 && now >= critter.nextAttack) {
                    critter.telegraphUntil = now + 16;
                    target.playSound(critter.at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.0f, 1.6f);
                    if (isle.forge().canary(target)) {
                        MineText.bar(target, "§e♪ The canary chirps §8· §dShardling locking on!");
                    }
                }
                if (critter.telegraphUntil > 0 && now < critter.telegraphUntil) {
                    beam(critter.at, target.getEyeLocation().add(0, -0.3, 0), Particle.END_ROD);
                    return;
                }
                if (critter.telegraphUntil > 0) {
                    critter.telegraphUntil = 0;
                    critter.nextAttack = now + 60;
                    if (target.hasLineOfSight(critter.hitbox)) {
                        beam(critter.at, target.getEyeLocation(), Particle.ELECTRIC_SPARK);
                        hurt(target, 0.07d);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false, false));
                        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.0f, 1.3f);
                    }
                }
            }
            case STONEJAW -> {
                if (critter.telegraphUntil == 0 && now >= critter.nextAttack && distance <= 7.0d) {
                    critter.telegraphUntil = now + 24;
                    critter.telegraphAt = critter.at.clone();
                    critter.at.getWorld().playSound(critter.at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 0.9f, 0.6f);
                    if (critter.jaw != null && critter.jaw.isValid()) {
                        critter.jaw.setInterpolationDelay(0);
                        critter.jaw.setInterpolationDuration(20);
                        critter.jaw.setTransformation(scale(1.5f, 0.45f, 1.5f, 2.0f));
                    }
                }
                if (critter.telegraphUntil > 0 && now < critter.telegraphUntil) {
                    ring(critter.telegraphAt, 3.5d, Particle.CRIT);
                    return;
                }
                if (critter.telegraphUntil > 0) {
                    critter.telegraphUntil = 0;
                    critter.nextAttack = now + 100;
                    slam(critter);
                }
            }
            case GLOAM_MOTH -> {
            }
        }
    }

    private void slam(Critter critter) {
        Location at = critter.telegraphAt == null ? critter.at : critter.telegraphAt;
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.8f, 0.6f);
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.0f, 0.5f);
        world.spawnParticle(Particle.BLOCK, at, 60, 1.6, 0.2, 1.6, 0.1, Material.DEEPSLATE.createBlockData());
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.4, 0), 2, 0.6, 0.1, 0.6, 0.0);
        if (critter.jaw != null && critter.jaw.isValid()) {
            critter.jaw.setInterpolationDelay(0);
            critter.jaw.setInterpolationDuration(3);
            critter.jaw.setTransformation(scale(1.5f, 0.45f, 1.5f, 1.25f));
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 3.5d * 3.5d) {
                hurt(player, 0.18d);
                Vector push = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (push.lengthSquared() > 1.0E-4) {
                    push.normalize();
                }
                player.setVelocity(push.multiply(0.9d).setY(0.45d));
            }
        }
    }

    private void hurt(Player player, double fraction) {
        double amount = player.getMaxHealth() * fraction * isle.forge().hazardFactor(player);
        player.damage(Math.max(0.5d, amount));
    }

    private static void beam(Location from, Location to, Particle particle) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1d) {
            return;
        }
        step.normalize().multiply(0.5d);
        Location cursor = from.clone().add(0, 0.4, 0);
        for (double walked = 0; walked < length; walked += 0.5d) {
            from.getWorld().spawnParticle(particle, cursor, 1, 0.0, 0.0, 0.0, 0.0);
            cursor.add(step);
        }
    }

    private static void ring(Location at, double radius, Particle particle) {
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2.0d * i / 20.0d;
            at.getWorld().spawnParticle(particle, at.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ getting hit

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(EntityDamageByEntityEvent event) {
        Critter critter = byPart.get(event.getEntity().getUniqueId());
        if (critter == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            hit(player, critter);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        if (byPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (byPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void hit(Player player, Critter critter) {
        if (critter.dead) {
            return;
        }
        critter.hp--;
        critter.target = player.getUniqueId();
        World world = critter.at.getWorld();
        Sound sound = switch (critter.kind) {
            case CINDER_MITE -> Sound.ENTITY_SILVERFISH_HURT;
            case GLOAM_MOTH -> Sound.BLOCK_AMETHYST_BLOCK_HIT;
            case STONEJAW -> Sound.BLOCK_DEEPSLATE_HIT;
            case SHARDLING -> Sound.BLOCK_AMETHYST_CLUSTER_HIT;
        };
        world.playSound(critter.at, sound, SoundCategory.HOSTILE, 1.0f, critter.kind == Kind.STONEJAW ? 0.6f : 1.2f);
        world.spawnParticle(Particle.CRIT, critter.at.clone().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.1);
        if (critter.hp <= 0) {
            die(player, critter);
            return;
        }
        if (critter.label != null && critter.label.isValid()) {
            critter.label.text(MineText.legacy(labelText(critter)));
        }
        if (critter.kind == Kind.GLOAM_MOTH) {
            critter.phase += Math.PI;
        }
    }

    private void fizzle(Critter critter) {
        if (critter.body != null && critter.body.isValid() && critter.at != null && critter.at.getWorld() != null) {
            critter.at.getWorld().spawnParticle(Particle.SMOKE, critter.at.clone().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.02);
        }
        remove(critter);
    }

    private void die(Player killer, Critter critter) {
        Location at = critter.at.clone();
        World world = at.getWorld();
        remove(critter);
        MineProfiles.Profile profile = isle.profiles().of(killer);
        profile.felled.merge(critter.kind.id(), 1, Integer::sum);
        isle.profiles().markDirty();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        switch (critter.kind) {
            case CINDER_MITE -> {
                world.spawnParticle(Particle.LAVA, at, 6, 0.2, 0.1, 0.2, 0.0);
                world.playSound(at, Sound.ENTITY_SILVERFISH_DEATH, SoundCategory.HOSTILE, 1.0f, 0.7f);
                MineSkills.give(killer, new ItemStack(Material.COAL, 1 + rng.nextInt(3)));
                if (rng.nextInt(3) == 0) {
                    MineSkills.give(killer, new ItemStack(Material.REDSTONE, 1 + rng.nextInt(4)));
                }
                MineSkills.bonus(killer, 6);
            }
            case GLOAM_MOTH -> {
                profile.gloamUntil = System.currentTimeMillis() + 60_000L;
                world.spawnParticle(Particle.GLOW, at, 30, 0.4, 0.4, 0.4, 0.05);
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 2.0f);
                MineSkills.give(killer, new ItemStack(Material.GLOW_BERRIES, 2 + rng.nextInt(3)));
                MineSkills.bonus(killer, 10);
                MineText.bar(killer, "§b✧ Gloam-lit! §7Crystal Finds §d×1.5 §7for a minute. §8The moth dust makes the rock whisper.");
            }
            case SHARDLING -> {
                world.spawnParticle(Particle.END_ROD, at, 16, 0.3, 0.3, 0.3, 0.05);
                world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.0f, 1.0f);
                int shards = 1 + rng.nextInt(3);
                profile.shards += shards;
                MineSkills.give(killer, new ItemStack(Material.AMETHYST_SHARD, shards));
                MineSkills.bonus(killer, 8);
            }
            case STONEJAW -> stonejawDown(killer, at);
        }
    }

    private void stonejawDown(Player killer, Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_DEATH, SoundCategory.HOSTILE, 1.0f, 0.5f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.7f, 0.8f);
        world.spawnParticle(Particle.BLOCK, at, 120, 1.2, 0.8, 1.2, 0.2, Material.DEEPSLATE_BRICKS.createBlockData());
        world.spawnParticle(Particle.FIREWORK, at.clone().add(0, 1.0, 0), 40, 0.6, 0.6, 0.6, 0.1);
        MineDistricts.Band band = isle.districts().band(at);
        IsleOre burst = switch (band) {
            case UNDERCROFT -> IsleOre.DIAMOND;
            case DEEP_WORKS -> IsleOre.GOLD;
            default -> IsleOre.IRON;
        };
        MineSkills.give(killer, new ItemStack(burst.resource(), 6 + ThreadLocalRandom.current().nextInt(6)));
        MineSkills.give(killer, jaw());
        MineSkills.coins(killer, 800L);
        MineSkills.bonus(killer, 250);
        isle.forge().addRep(killer, 50L, "Stonejaw felled");
        Grade grade = ThreadLocalRandom.current().nextDouble() < 0.3d ? Grade.PERFECT : Grade.FLAWLESS;
        IsleOre[] families = {IsleOre.DIAMOND, IsleOre.EMERALD, IsleOre.GOLD, IsleOre.LAPIS, IsleOre.REDSTONE};
        IsleOre crystal = families[ThreadLocalRandom.current().nextInt(families.length)];
        double carats = Math.round((crystal.minCarat() + (crystal.maxCarat() - crystal.minCarat())
                * ThreadLocalRandom.current().nextDouble(0.4d, 1.0d)) * 1.35d * 100.0d) / 100.0d;
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (killer.isOnline()) {
                isle.crystals().spawn(killer, crystal, grade, carats, at.getBlock().getLocation(), false);
            }
        }, 20L);
        var line = MineText.legacy("§4☠ §f" + killer.getName() + " §7broke a §4Stonejaw§7's jaw. §8Something glitters where it fell.");
        for (Player visitor : MineWorld.visitors(isle.plugin())) {
            visitor.sendMessage(line);
        }
    }

    /** The Stonejaw trophy; Hollis at the Last Lamp pays for it. */
    public ItemStack jaw() {
        ItemStack item = new ItemStack(Material.BONE);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4Stonejaw's Jaw");
            meta.setLore(List.of("§7Heavier than it looks. Warmer, too.", "", "§eHollis Underhill §7at the Last Lamp",
                    "§7pays §62,500 coins §7and Forge Reputation.", "§8Mining Eldervale · trophy"));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(new NamespacedKey(isle.plugin(), "mine_jaw"), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isJaw(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(isle.plugin(), "mine_jaw"), PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ spots

    /** A standable spot near {@code at}: solid below, two passable above. */
    static Location floorNear(Location at, int radius) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        int bx = at.getBlockX();
        int by = at.getBlockY();
        int bz = at.getBlockZ();
        for (int r = 0; r <= radius; r++) {
            for (int dy = 1; dy >= -3; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        Block feet = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                                && feet.getRelative(0, -1, 0).getType().isSolid()) {
                            if (r == 0 && dy == 0) {
                                return new Location(world, at.getX(), feet.getY(), at.getZ(), at.getYaw(), 0);
                            }
                            return feet.getLocation().add(0.5, 0.0, 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Location airNear(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Block block = world.getBlockAt(at.getBlockX() + rng.nextInt(-2, 3), at.getBlockY() + rng.nextInt(0, 3),
                    at.getBlockZ() + rng.nextInt(-2, 3));
            if (block.isPassable() && !block.isLiquid() && block.getRelative(0, 1, 0).isPassable()) {
                return block.getLocation().add(0.5, 0.3, 0.5);
            }
        }
        return null;
    }

    private static Player nearestPlayer(Location at, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : at.getWorld().getPlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }
}
