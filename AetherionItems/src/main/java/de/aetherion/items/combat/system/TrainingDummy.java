package de.aetherion.items.combat.system;

import de.aetherion.core.AetherKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /combat dummy}: an iron training golem that fights back on a loop from {@code dummy.pattern}.
 * <ul>
 *   <li>jab: yellow "!" over its head, single target. Parry it (F).</li>
 *   <li>slam: red "!!" and a ring on the ground at its reach. Cannot be parried; roll through it.</li>
 * </ul>
 * It never moves (no AI), never dies, never kills (its hits stop at 1 HP and never trigger set death
 * saves), drops nothing, and is not saved with the chunk. The name shows the damage dealt to it over the
 * last five seconds. Turning the kit off removes every golem.
 */
final class TrainingDummy {

    private static final int MAX_DUMMIES = 12;
    private static final double MAX_HP = 1024.0;
    private static final int DPS_WINDOW = 100;
    private static final TextColor JAB = TextColor.color(0xFFD24A);
    private static final TextColor SLAM = TextColor.color(0xFF5A4A);
    private static final Particle.DustOptions JAB_DUST = new Particle.DustOptions(Color.fromRGB(0xFFD24A), 1.0f);
    private static final Particle.DustOptions SLAM_DUST = new Particle.DustOptions(Color.fromRGB(0xFF5A4A), 1.6f);
    private static final Particle.DustOptions SLAM_CORE = new Particle.DustOptions(Color.fromRGB(0xB0201A), 1.1f);

    enum Move { JAB, SLAM }

    private final CombatSystem system;
    private final NamespacedKey key;
    private final Map<UUID, Dummy> dummies = new ConcurrentHashMap<>();
    /** Set while a dummy's own hit is being dealt, so the guard can tell a slam from a jab. */
    private Move striking;

    TrainingDummy(CombatSystem system) {
        this.system = system;
        this.key = new NamespacedKey(system.plugin(), "training_dummy");
    }

    /* =========================================================
     * SPAWN / REMOVE
     * ========================================================= */

    /** Spawns the owner's golem a few blocks ahead (replacing their old one). Returns false if there is no room. */
    boolean spawn(Player owner) {
        clear(owner);
        return canSpawn() && place(owner, CombatSystem.facing(owner));
    }

    /**
     * Sergeant Vex's drill: the owner usually faces the NPC who sent them, so the golem goes beside them
     * first, then behind, then ahead. False when there is no open ground or the spawn was refused.
     */
    boolean spawnNear(Player owner) {
        clear(owner);
        if (!canSpawn()) {
            return false;
        }
        Vector ahead = CombatSystem.facing(owner);
        Vector side = new Vector(-ahead.getZ(), 0.0, ahead.getX());
        for (Vector dir : List.of(side, side.clone().multiply(-1), ahead.clone().multiply(-1), ahead)) {
            if (place(owner, dir)) {
                return true;
            }
        }
        return false;
    }

    /** True while the owner's golem stands within {@code range} blocks of them. */
    boolean hasNear(Player owner, double range) {
        for (Dummy dummy : dummies.values()) {
            if (dummy.owner.equals(owner.getUniqueId()) && dummy.golem.isValid()
                    && dummy.golem.getWorld() == owner.getWorld()
                    && dummy.golem.getLocation().distanceSquared(owner.getLocation()) <= range * range) {
                return true;
            }
        }
        return false;
    }

    private boolean canSpawn() {
        return system.config().enabled && dummies.size() < MAX_DUMMIES;
    }

    /** One golem {@code 2..3} blocks from the owner along {@code facing}, turned toward them. */
    private boolean place(Player owner, Vector facing) {
        double room = Mobility.safeReach(owner, facing, 3.0);
        if (room < 2.0) {
            return false;
        }
        Location at = owner.getLocation().add(facing.clone().multiply(room));
        at.setYaw((float) Math.toDegrees(Math.atan2(facing.getX(), -facing.getZ())));
        at.setPitch(0.0f);
        String name = CombatSystem.t(owner, "Training Golem", "Übungsgolem");
        IronGolem golem = owner.getWorld().spawn(at, IronGolem.class, spawned -> {
            spawned.setAI(false);
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPlayerCreated(true);
            spawned.setCanPickupItems(false);
            spawned.customName(Component.text(name, NamedTextColor.GOLD));
            spawned.setCustomNameVisible(true);
            spawned.addScoreboardTag("aetherion_training_dummy");
            spawned.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            AttributeInstance health = spawned.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(MAX_HP);
            }
            spawned.setHealth(MAX_HP);
        });
        if (!golem.isValid()) {
            // A protection plugin cancelled the spawn: nothing stands there.
            golem.remove();
            return false;
        }
        World world = golem.getWorld();
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f, 0.8f);
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.3, 0), 12, 0.4, 0.1, 0.4, 0.02);
        long tick = Bukkit.getCurrentTick();
        Dummy dummy = new Dummy(golem, owner.getUniqueId(), name, tick);
        dummy.nextAt = tick + 30;
        dummies.put(golem.getUniqueId(), dummy);
        return true;
    }

    int clear(Player owner) {
        int removed = 0;
        Iterator<Dummy> it = dummies.values().iterator();
        while (it.hasNext()) {
            Dummy dummy = it.next();
            if (dummy.owner.equals(owner.getUniqueId())) {
                discard(dummy);
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    void removeAll() {
        for (Dummy dummy : dummies.values()) {
            discard(dummy);
        }
        dummies.clear();
    }

    private static void discard(Dummy dummy) {
        dummy.clearMarker();
        if (dummy.golem.isValid()) {
            dummy.golem.remove();
        }
    }

    boolean isDummy(Entity entity) {
        return entity != null && dummies.containsKey(entity.getUniqueId());
    }

    /** True while a slam is being dealt: the guard lets it through, only a roll avoids it. */
    boolean unparryable() {
        return striking == Move.SLAM;
    }

    /** A perfect parry on a jab: the golem reels, skips its sequence and stays open for a riposte. */
    void parried(Entity entity, long tick) {
        Dummy dummy = dummies.get(entity.getUniqueId());
        if (dummy == null) {
            return;
        }
        dummy.staggerUntil = tick + system.config().staggerTicks;
        dummy.move = null;
        dummy.clearMarker();
        dummy.nextAt = dummy.staggerUntil + 10;
        IronGolem golem = dummy.golem;
        World world = golem.getWorld();
        Location head = golem.getLocation().add(0, golem.getHeight() + 0.2, 0);
        golem.playEffect(EntityEffect.HURT);
        world.playSound(head, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.7f);
        world.playSound(head, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.6f, 0.6f);
        world.spawnParticle(Particle.CRIT, head, 20, 0.4, 0.2, 0.4, 0.4);
        world.spawnParticle(Particle.ELECTRIC_SPARK, head, 14, 0.5, 0.3, 0.5, 0.2);
        Vector back = forward(golem).multiply(-0.35).setY(0.15);
        golem.setVelocity(back);
    }

    /* =========================================================
     * TICK
     * ========================================================= */

    void tick(long tick) {
        if (dummies.isEmpty()) {
            return;
        }
        CombatConfig cfg = system.config();
        if (!cfg.enabled) {
            removeAll();
            return;
        }
        Iterator<Dummy> it = dummies.values().iterator();
        while (it.hasNext()) {
            Dummy dummy = it.next();
            Player owner = Bukkit.getPlayer(dummy.owner);
            IronGolem golem = dummy.golem;
            if (!golem.isValid() || owner == null || owner.getWorld() != golem.getWorld()
                    || owner.getLocation().distanceSquared(golem.getLocation()) > 64 * 64
                    || tick - dummy.bornTick > cfg.dummyLifetimeSeconds * 20L) {
                discard(dummy);
                it.remove();
                continue;
            }
            Player focus = focus(golem, owner, cfg.dummyReach + 6.0);
            if (focus != null) {
                face(golem, focus);
            }
            if (tick % 10 == 0) {
                rename(dummy, tick);
            }
            if (tick < dummy.staggerUntil) {
                continue;
            }
            if (dummy.move == null) {
                if (focus == null || horizontal(golem, focus) > cfg.dummyReach + 3.0) {
                    dummy.nextAt = Math.max(dummy.nextAt, tick + 10);
                } else if (tick >= dummy.nextAt) {
                    windup(dummy, cfg, tick);
                }
                continue;
            }
            telegraph(dummy, cfg, tick);
            if (tick == dummy.strikeAt - 3) {
                golem.playEffect(EntityEffect.IRON_GOLEN_ATTACK);
            }
            if (tick >= dummy.strikeAt) {
                strike(dummy, cfg, tick);
            }
        }
    }

    private void windup(Dummy dummy, CombatConfig cfg, long tick) {
        List<String> pattern = cfg.dummyPattern;
        String raw = pattern.get(dummy.step % pattern.size());
        dummy.step++;
        dummy.move = "slam".equals(raw) ? Move.SLAM : Move.JAB;
        dummy.windupTicks = "slam".equals(raw) ? cfg.dummySlamWindup : cfg.dummyWindup;
        dummy.strikeAt = tick + dummy.windupTicks;
        IronGolem golem = dummy.golem;
        Player owner = Bukkit.getPlayer(dummy.owner);
        boolean slam = dummy.move == Move.SLAM;
        String label = slam
                ? (owner == null ? "!! ROLL" : CombatSystem.t(owner, "!! ROLL", "!! ROLLEN"))
                : (owner == null ? "! PARRY" : CombatSystem.t(owner, "! PARRY", "! PARIEREN"));
        Location over = golem.getLocation().add(0, golem.getHeight() + 0.75, 0);
        dummy.marker = golem.getWorld().spawn(over, TextDisplay.class, display -> {
            display.text(Component.text(label, slam ? SLAM : JAB).decorate(TextDecoration.BOLD));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setShadowed(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setSeeThrough(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    slam ? new Vector3f(2.1f, 2.1f, 2.1f) : new Vector3f(1.7f, 1.7f, 1.7f), new Quaternionf()));
        });
        World world = golem.getWorld();
        if (slam) {
            world.playSound(golem.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
            world.playSound(golem.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.7f);
            world.playSound(golem.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
            // Sekiro perilous-attack tell: the red 危 glyph flashes on screen for everyone in the blast zone.
            for (Player near : world.getPlayers()) {
                if (horizontal(golem, near) <= cfg.dummyReach + 3.0) {
                    CombatGlyphs.flash(system, near, CombatGlyphs.Glyph.PERIL);
                }
            }
        } else {
            world.playSound(golem.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
            world.playSound(golem.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.4f);
            world.playSound(golem.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.9f, 1.2f);
        }
    }

    /**
     * Windup tells. Jab: a gold charge gathers at the fist with a rising tick every 4 ticks, then a white
     * flash 4 ticks out (that flash is the "press F now" beat). Slam: a fat red ring at reach, a closing
     * inner ring, a filled danger disc and a bass pulse that speeds up; last 6 ticks the ground cracks.
     */
    private void telegraph(Dummy dummy, CombatConfig cfg, long tick) {
        IronGolem golem = dummy.golem;
        World world = golem.getWorld();
        Location base = golem.getLocation();
        long left = dummy.strikeAt - tick;
        double share = Math.max(0.0, left / (double) Math.max(1, dummy.windupTicks));
        if (dummy.move == Move.SLAM) {
            if (tick % 2 == 0) {
                double inner = cfg.dummyReach * (1.0 - share * 0.85);
                ring(world, base, cfg.dummyReach, 28, SLAM_DUST);
                ring(world, base, cfg.dummyReach - 0.25, 22, SLAM_DUST);
                ring(world, base, inner, 14, SLAM_CORE);
                if (left <= 8) {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.random() * Math.PI * 2, r = Math.random() * cfg.dummyReach;
                        world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * r, 0.12, Math.sin(a) * r),
                                1, 0, 0, 0, 0, SLAM_CORE);
                    }
                }
            }
            int every = left > 12 ? 6 : left > 6 ? 4 : 2;
            if (tick % every == 0) {
                world.playSound(base, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, (float) (0.5 + (1.0 - share) * 0.5));
            }
            if (left <= 6 && tick % 2 == 0) {
                world.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 0.6f, 0.6f);
                world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.2, 0), 4, cfg.dummyReach * 0.4, 0.05,
                        cfg.dummyReach * 0.4, 0.01);
            }
        } else {
            Location fist = base.clone().add(forward(golem).multiply(0.9)).add(0, 1.6, 0);
            if (tick % 2 == 0) {
                world.spawnParticle(Particle.DUST, fist, 6, 0.16, 0.16, 0.16, 0.0, JAB_DUST);
                world.spawnParticle(Particle.ELECTRIC_SPARK, fist, 2, 0.1, 0.1, 0.1, 0.05);
            }
            if (tick % 4 == 0 && left > 4) {
                world.playSound(base, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, (float) (1.0 + (1.0 - share) * 0.9));
            }
            if (left == 4) {
                world.spawnParticle(Particle.FLASH, fist, 1, 0, 0, 0, 0);
                world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.9f);
                world.playSound(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 2.0f);
            }
        }
        if (dummy.marker != null && dummy.marker.isValid() && left <= 6 && tick % 2 == 0) {
            float s = dummy.move == Move.SLAM ? 2.1f : 1.7f;
            float pop = (left % 4 < 2) ? s * 1.25f : s;
            dummy.marker.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(pop, pop, pop), new Quaternionf()));
        }
    }

    private void strike(Dummy dummy, CombatConfig cfg, long tick) {
        Move move = dummy.move;
        dummy.move = null;
        dummy.nextAt = tick + cfg.dummyGap;
        dummy.clearMarker();
        IronGolem golem = dummy.golem;
        World world = golem.getWorld();
        Location base = golem.getLocation();

        List<Player> targets = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!player.isDead() && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && Math.abs(player.getLocation().getY() - base.getY()) <= 3.0
                    && horizontal(golem, player) <= cfg.dummyReach) {
                targets.add(player);
            }
        }
        if (move == Move.JAB && targets.size() > 1) {
            targets.sort((a, b) -> Double.compare(horizontal(golem, a), horizontal(golem, b)));
            targets = List.of(targets.get(0));
        }

        if (move == Move.SLAM) {
            world.playSound(base, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.8f);
            world.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            world.playSound(base, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.8f, 0.7f);
            world.spawnParticle(Particle.GUST_EMITTER_SMALL, base.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0);
            BlockData ground = world.getBlockAt(base.getBlockX(), base.getBlockY() - 1, base.getBlockZ()).getBlockData();
            if (ground.getMaterial().isAir()) {
                ground = org.bukkit.Material.STONE.createBlockData();
            }
            for (int i = 0; i < 20; i++) {
                double angle = Math.PI * 2 * i / 20.0;
                Location at = base.clone().add(Math.cos(angle) * cfg.dummyReach, 0.15, Math.sin(angle) * cfg.dummyReach);
                world.spawnParticle(Particle.BLOCK, at, 6, 0.15, 0.1, 0.15, 0.0, ground);
            }
            world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        } else {
            world.playSound(base, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.1f);
            Location fist = base.clone().add(forward(golem).multiply(1.4)).add(0, 1.3, 0);
            world.spawnParticle(Particle.SWEEP_ATTACK, fist, 1, 0, 0, 0, 0);
        }

        for (Player target : targets) {
            double before = target.getHealth() + target.getAbsorptionAmount();
            // Never lethal before mitigation either, so the set death saves never see a killing blow.
            double amount = Math.min(cfg.dummyHitShare * system.maxHealth(target), Math.max(0.0, before - 1.0));
            if (amount <= 0.05) {
                continue;
            }
            PersistentDataContainer data = target.getPersistentDataContainer();
            boolean flagged = data.has(AetherKeys.NO_SET_SAVE, PersistentDataType.BYTE);
            if (!flagged) {
                data.set(AetherKeys.NO_SET_SAVE, PersistentDataType.BYTE, (byte) 1);
            }
            striking = move;
            try {
                target.damage(amount, golem);
            } finally {
                striking = null;
                if (!flagged) {
                    data.remove(AetherKeys.NO_SET_SAVE);
                }
            }
            boolean landed = target.getHealth() + target.getAbsorptionAmount() < before - 1.0E-3;
            if (move == Move.SLAM && landed && target.isValid()) {
                Vector away = target.getLocation().toVector().subtract(base.toVector()).setY(0);
                if (away.lengthSquared() > 1.0E-4) {
                    target.setVelocity(away.normalize().multiply(0.5).setY(0.35));
                }
            }
        }
    }

    /* =========================================================
     * DAMAGE (called from CombatListener)
     * ========================================================= */

    /** HIGHEST, registered last: the golem never dies and the player never dies to it. */
    void clampLethal(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof IronGolem golem && isDummy(golem)) {
            double fin = event.getFinalDamage();
            double max = maxHp(golem);
            if (fin >= golem.getHealth() - 1.0) {
                golem.setHealth(max);
                if (fin >= max - 1.0) {
                    event.setDamage(event.getDamage() * (max - 2.0) / fin);
                }
            }
            return;
        }
        if (victim instanceof Player player && event instanceof EntityDamageByEntityEvent byEntity
                && isDummy(byEntity.getDamager())) {
            double fin = event.getFinalDamage();
            double room = player.getHealth() - 1.0;
            if (fin > room && fin > 0.0) {
                event.setDamage(event.getDamage() * Math.max(0.0, room) / fin);
            }
        }
    }

    /** MONITOR: tally for the name readout, refill next tick. */
    void recordHit(EntityDamageEvent event) {
        Dummy dummy = dummies.get(event.getEntity().getUniqueId());
        if (dummy == null) {
            return;
        }
        double fin = event.getFinalDamage();
        if (fin <= 0.0) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        dummy.hitTicks.addLast(tick);
        dummy.hitAmounts.addLast(fin);
        if (!dummy.healQueued) {
            dummy.healQueued = true;
            Bukkit.getScheduler().runTask(system.plugin(), () -> {
                dummy.healQueued = false;
                if (dummy.golem.isValid() && !dummy.golem.isDead()) {
                    dummy.golem.setHealth(maxHp(dummy.golem));
                }
            });
        }
        rename(dummy, tick);
    }

    /** LOWEST: belt and braces. A golem that still reaches 0 HP is revived; no drops, no XP. */
    void preventDeath(EntityDeathEvent event) {
        if (!isDummy(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.setReviveHealth(event.getEntity() instanceof IronGolem golem ? maxHp(golem) : MAX_HP);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /* =========================================================
     * HELPERS
     * ========================================================= */

    private void rename(Dummy dummy, long tick) {
        while (!dummy.hitTicks.isEmpty() && tick - dummy.hitTicks.peekFirst() > DPS_WINDOW) {
            dummy.hitTicks.pollFirst();
            dummy.hitAmounts.pollFirst();
        }
        double total = 0.0;
        for (double amount : dummy.hitAmounts) {
            total += amount;
        }
        String label = total <= 0.0 ? dummy.name : dummy.name + " · 5s " + format(total);
        if (!label.equals(dummy.lastLabel)) {
            dummy.lastLabel = label;
            dummy.golem.customName(Component.text(label, NamedTextColor.GOLD));
        }
    }

    private static double maxHp(IronGolem golem) {
        AttributeInstance max = golem.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return max == null ? 100.0 : Math.max(2.0, max.getValue());
    }

    private static String format(double value) {
        return value >= 100.0 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private Player focus(IronGolem golem, Player owner, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : golem.getWorld().getPlayers()) {
            if (player.isDead() || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(golem.getLocation());
            if (player.getUniqueId().equals(owner.getUniqueId())) {
                dist *= 0.8;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static void face(IronGolem golem, Player target) {
        Vector to = target.getLocation().toVector().subtract(golem.getLocation().toVector());
        if (to.lengthSquared() < 1.0E-4) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float delta = Math.abs(((yaw - golem.getLocation().getYaw()) % 360.0f + 540.0f) % 360.0f - 180.0f);
        if (delta < 3.0f) {
            return;
        }
        golem.setRotation(yaw, 0.0f);
        golem.setBodyYaw(yaw);
    }

    private static Vector forward(Entity entity) {
        double yaw = Math.toRadians(entity.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getLocation().getX() - b.getLocation().getX();
        double dz = a.getLocation().getZ() - b.getLocation().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void ring(World world, Location centre, double radius, int points, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location at = centre.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
        }
    }

    private static final class Dummy {
        final IronGolem golem;
        final UUID owner;
        final String name;
        final long bornTick;
        final ArrayDeque<Long> hitTicks = new ArrayDeque<>();
        final ArrayDeque<Double> hitAmounts = new ArrayDeque<>();
        int step;
        long nextAt;
        long strikeAt;
        long staggerUntil;
        Move move;
        int windupTicks;
        TextDisplay marker;
        String lastLabel;
        boolean healQueued;

        Dummy(IronGolem golem, UUID owner, String name, long bornTick) {
            this.golem = golem;
            this.owner = owner;
            this.name = name;
            this.bornTick = bornTick;
        }

        void clearMarker() {
            if (marker != null && marker.isValid()) {
                marker.remove();
            }
            marker = null;
        }
    }
}
