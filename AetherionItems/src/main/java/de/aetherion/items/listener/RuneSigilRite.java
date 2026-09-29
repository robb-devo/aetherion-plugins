package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rune Sigil (rune_sigil) — Azure Rite.
 * Right-click plants an azure seal underfoot. Blue flame rises through carved runes as the circle
 * builds, holds, then fires an absolute judgment pillar skyward — annihilating hostiles while
 * fully restoring every nearby player (plus absorption + regeneration). Sandbox showcase.
 */
public final class RuneSigilRite {

    private static final int INVOKE = 10;
    private static final int BUILD = INVOKE + 26;
    private static final int HOLD = BUILD + 18;
    private static final int BEAM = HOLD + 4;
    private static final int AFTER = BEAM + 36;
    private static final int TOTAL = AFTER + 22;

    private static final double RING_R = 7.5;
    private static final double JUDGE_R = 14.0;
    private static final float BEAM_H = 42f;
    private static final int RING_SEGS = 26;
    private static final int RING_LAYERS = 3;
    private static final int RUNES = 10;
    private static final int FLAMES = 16;
    private static final int MAX_LIVE = 260;

    private static final Color AZURE = Color.fromRGB(70, 170, 255);
    private static final Color CYAN = Color.fromRGB(120, 240, 255);
    private static final Color ICE = Color.fromRGB(200, 240, 255);
    private static final Color ROYAL = Color.fromRGB(40, 80, 255);
    private static final Color VOID = Color.fromRGB(10, 20, 50);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final Material[] RUNE_MATS = {
            Material.LAPIS_BLOCK,
            Material.BLUE_ICE,
            Material.CYAN_CONCRETE,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.SEA_LANTERN,
            Material.PRISMARINE_BRICKS
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player caster;
    private final World world;
    private final double damage;
    private final Runnable done;

    private final List<SealTile> ring = new ArrayList<>();
    private final List<BlockDisplay> runes = new ArrayList<>();
    private final List<BlockDisplay> flames = new ArrayList<>();
    private final BlockDisplay[] pillar = new BlockDisplay[4];
    private final List<Ejecta> sparks = new ArrayList<>();

    private Location focus;
    private BlockDisplay seed;
    private BlockDisplay coreDisc;
    private float spin;
    private int tick;
    private int phaseTick;
    private Phase phase = Phase.INVOKE;
    private boolean released;
    private boolean judged;

    private enum Phase { INVOKE, BUILD, HOLD, BEAM, AFTER, DONE }

    private static final class SealTile {
        final BlockDisplay display;
        final int layer;
        final double angle;
        final float length;

        SealTile(BlockDisplay display, int layer, double angle, float length) {
            this.display = display;
            this.layer = layer;
            this.angle = angle;
            this.length = length;
        }
    }

    private final class Ejecta {
        final BlockDisplay display;
        final Location at;
        final Vector velocity;
        final Color glow;
        final float size;
        final int life;
        final Quaternionf rot = new Quaternionf();
        int age;

        Ejecta(BlockDisplay display, Location start, Vector velocity, Color glow, float size, int life) {
            this.display = display;
            this.at = level(start.clone());
            this.velocity = velocity;
            this.glow = glow;
            this.size = size;
            this.life = life;
            if (display != null) {
                display.setTeleportDuration(1);
            }
        }

        boolean step() {
            age++;
            if (display == null || !display.isValid() || age > life) {
                return false;
            }
            at.add(velocity);
            velocity.setY(velocity.getY() - 0.05);
            velocity.multiply(0.98);
            rot.rotateY(0.2f);
            display.teleport(at);
            ease(display, centered(size * Math.max(0.2f, 1f - age / (float) life), rot), 1);
            if (age % 2 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0.04, 0.04, 0.04, 0.01);
            }
            return true;
        }
    }

    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    static void cast(JavaPlugin plugin, Player player, double damage, Runnable done) {
        new RuneSigilRite(plugin, player, damage, done).start();
    }

    private RuneSigilRite(JavaPlugin plugin, Player player, double damage, Runnable done) {
        this.plugin = plugin;
        this.caster = player;
        this.world = player.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        focus = floorNear(caster.getLocation()).add(0, 0.14, 0);
        seed = spawn(focus, Material.BLUE_ICE.createBlockData(), LIT, CYAN, centered(0.2f, new Quaternionf()));
        world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        world.playSound(focus, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.1f, 0.7f);
        world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        caster.sendMessage("§b✦ Rune Sigil §7— the azure rite begins…");
        caster.sendActionBar(net.kyori.adventure.text.Component.text("§b§l◈ AZURE RITE"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if ((!caster.isOnline() || caster.isDead() || caster.getWorld() != world)
                        && phase.ordinal() < Phase.BEAM.ordinal()) {
                    release();
                    clearAll();
                    cancel();
                    return;
                }
                if (step()) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean step() {
        tick++;
        return switch (phase) {
            case INVOKE -> {
                invoke();
                yield false;
            }
            case BUILD -> {
                build();
                yield false;
            }
            case HOLD -> {
                hold();
                yield false;
            }
            case BEAM -> {
                beam();
                yield false;
            }
            case AFTER -> {
                after();
                yield false;
            }
            case DONE -> true;
        };
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    // ------------------------------------------------------------------ invoke

    private void invoke() {
        phaseTick++;
        double u = phaseTick / (double) INVOKE;
        if (seed != null && seed.isValid()) {
            float size = 0.15f + (float) u * 0.55f;
            seed.teleport(level(focus));
            ease(seed, centered(size, new Quaternionf().rotateY(phaseTick * 0.55f).rotateX(0.4f)), 2);
        }
        if (phaseTick % 2 == 0) {
            drawRing(focus, 0.6 + 2.2 * u, 32, new Particle.DustOptions(mix(CYAN, AZURE, u), 1.3f), phaseTick * 0.1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, focus.clone().add(0, 0.3, 0), 4, 0.4, 0.2, 0.4, 0.01);
        }
        if (phaseTick == 1 || phaseTick == 5 || phaseTick == 9) {
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f + phaseTick * 0.08f);
            world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 0.55f, 1.4f);
        }
        if (phaseTick >= INVOKE) {
            discard(seed);
            seed = null;
            stageSeal();
            phase = Phase.BUILD;
            phaseTick = 0;
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f);
            world.playSound(focus, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.2f);
        }
    }

    private void stageSeal() {
        boolean crowded = LIVE.size() > MAX_LIVE - 100;
        int segs = crowded ? RING_SEGS / 2 : RING_SEGS;
        Material[] mats = {
                Material.LIGHT_BLUE_STAINED_GLASS,
                Material.CYAN_STAINED_GLASS,
                Material.BLUE_STAINED_GLASS
        };
        Color[] glows = {ICE, CYAN, AZURE};
        double[] scales = {1.0, 0.72, 0.44};
        for (int layer = 0; layer < RING_LAYERS; layer++) {
            for (int i = 0; i < segs; i++) {
                double a = Math.PI * 2 * i / segs;
                float len = (float) (Math.PI * 2 * (RING_R * scales[layer]) / segs * 1.08);
                BlockDisplay tile = spawn(focus, mats[layer].createBlockData(), LIT, glows[layer], tiny());
                ring.add(new SealTile(tile, layer, a, len));
            }
        }
        int runeN = crowded ? RUNES / 2 : RUNES;
        for (int i = 0; i < runeN; i++) {
            Material mat = RUNE_MATS[i % RUNE_MATS.length];
            Color glow = i % 2 == 0 ? CYAN : AZURE;
            runes.add(spawn(focus, mat.createBlockData(), LIT, glow, tiny()));
        }
        int flameN = crowded ? FLAMES / 2 : FLAMES;
        for (int i = 0; i < flameN; i++) {
            flames.add(spawn(focus, Material.SOUL_LANTERN.createBlockData(), LIT, CYAN, tiny()));
        }
        coreDisc = spawn(focus, Material.SEA_LANTERN.createBlockData(), LIT, ICE, tiny());
        pillar[0] = spawn(focus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, ICE, tiny());
        pillar[1] = spawn(focus, Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), LIT, CYAN, tiny());
        pillar[2] = spawn(focus, Material.CYAN_STAINED_GLASS.createBlockData(), LIT, AZURE, tiny());
        pillar[3] = spawn(focus, Material.BLUE_STAINED_GLASS.createBlockData(), LIT, ROYAL, tiny());
    }

    // ------------------------------------------------------------------ build

    private void build() {
        phaseTick++;
        int span = BUILD - INVOKE;
        double u = phaseTick / (double) span;
        double grow = easeOutQuart(Math.min(1.0, u));
        spin += 0.06f;
        putRing(grow, 0.18f + (float) grow * 0.55f, spin);
        putRunes(grow, spin);
        putFlames(grow, spin);
        if (coreDisc != null && coreDisc.isValid()) {
            coreDisc.teleport(level(focus));
            ease(coreDisc, centered(0.4f + (float) grow * 1.6f,
                    new Quaternionf().rotateY(spin * 1.4f).rotateX((float) Math.toRadians(90))), 2);
        }

        if (phaseTick % 2 == 0) {
            drawRing(focus, RING_R * grow, 48, new Particle.DustOptions(mix(CYAN, AZURE, u), 1.6f), spin);
            drawRing(focus.clone().add(0, 0.25, 0), RING_R * 0.72 * grow, 36,
                    new Particle.DustOptions(ICE, 1.2f), -spin);
            for (int i = 0; i < 8; i++) {
                double a = spin + Math.PI * 2 * i / 8;
                Location at = focus.clone().add(Math.cos(a) * RING_R * grow, 0.1, Math.sin(a) * RING_R * grow);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 2, 0.05, 0.35, 0.05, 0.01);
                world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.6 + Math.sin(tick * 0.3 + i) * 0.3, 0),
                        1, 0.02, 0.1, 0.02, 0.01);
            }
        }
        if (phaseTick == 1 || phaseTick == 8 || phaseTick == 16 || phaseTick == 22) {
            world.playSound(focus, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 0.8f + (float) u * 0.7f);
            world.playSound(focus, Sound.BLOCK_BEACON_AMBIENT, 0.55f, 1.2f + (float) u * 0.5f);
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.3f);
        }
        if (phaseTick >= span) {
            phase = Phase.HOLD;
            phaseTick = 0;
            world.playSound(focus, Sound.BLOCK_BELL_RESONATE, 0.9f, 1.5f);
            world.playSound(focus, Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.4f);
            caster.sendActionBar(net.kyori.adventure.text.Component.text("§b◈ Sigil locked — judgment rising…"));
        }
    }

    // ------------------------------------------------------------------ hold

    private void hold() {
        phaseTick++;
        int span = HOLD - BUILD;
        spin += 0.09f;
        putRing(1.0, 0.72f + 0.08f * (float) Math.sin(phaseTick * 0.4), spin);
        putRunes(1.0, spin);
        putFlames(1.0 + 0.08 * Math.sin(phaseTick * 0.5), spin);
        if (coreDisc != null && coreDisc.isValid()) {
            float pulse = 1.9f + 0.25f * (float) Math.sin(phaseTick * 0.55);
            coreDisc.teleport(level(focus));
            ease(coreDisc, centered(pulse, new Quaternionf().rotateY(spin * 2f).rotateX((float) Math.toRadians(90))), 2);
        }
        if (phaseTick % 2 == 0) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, focus.clone().add(0, 0.4, 0), 18, 2.4, 0.6, 2.4, 0.02);
            world.spawnParticle(Particle.DUST, focus, 20, 2.5, 0.4, 2.5, 0, new Particle.DustOptions(AZURE, 1.4f));
            drawRing(focus, RING_R, 56, new Particle.DustOptions(CYAN, 1.5f), spin);
        }
        if (phaseTick % 4 == 0) {
            world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1.5f);
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.8f);
        }
        if (phaseTick == span - 3) {
            for (Player near : world.getPlayers()) {
                if (near.getWorld() == world && near.getLocation().distanceSquared(focus) < 64 * 64) {
                    near.stopAllSounds();
                }
            }
            world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.3f, 1.2f);
            world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.8f);
            caster.sendActionBar(net.kyori.adventure.text.Component.text("§f§l— JUDGMENT —"));
        }
        if (phaseTick >= span) {
            phase = Phase.BEAM;
            phaseTick = 0;
            fireJudgment();
        }
    }

    // ------------------------------------------------------------------ beam / judgment

    private void fireJudgment() {
        world.spawnParticle(Particle.FLASH, focus, 12, 1.8, 0.6, 1.8, 0);
        world.spawnParticle(Particle.FLASH, focus.clone().add(0, 8, 0), 8, 1.2, 4.0, 1.2, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 4, 1.0, 0.4, 1.0, 0);
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18;
            world.spawnParticle(Particle.SONIC_BOOM,
                    focus.clone().add(Math.cos(a) * 2.4, 0.5, Math.sin(a) * 2.4), 1, 0, 0, 0, 0);
        }
        burst(Particle.END_ROD, 180, 1.6);
        burst(Particle.SOUL_FIRE_FLAME, 120, 1.1);
        burst(Particle.FIREWORK, 80, 1.3);
        world.spawnParticle(Particle.DUST, focus, 100, 3.5, 2.0, 3.5, 0, new Particle.DustOptions(ICE, 2.4f));
        world.spawnParticle(Particle.DUST, focus, 80, 4.5, 3.0, 4.5, 0, new Particle.DustOptions(CYAN, 2.0f));
        world.spawnParticle(Particle.DUST, focus, 70, 5.5, 4.0, 5.5, 0, new Particle.DustOptions(AZURE, 1.8f));
        world.spawnParticle(Particle.DUST, focus, 50, 6.5, 5.0, 6.5, 0, new Particle.DustOptions(ROYAL, 1.6f));

        for (Player near : world.getPlayers()) {
            if (near.getWorld() != world || near.getLocation().distanceSquared(focus) > 72 * 72) {
                continue;
            }
            Location eye = near.getEyeLocation();
            near.spawnParticle(Particle.FLASH, eye.clone().add(eye.getDirection().multiply(1.1)), 2, 0, 0, 0, 0);
        }

        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.55f);
        world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.2f, 0.7f);
        world.playSound(focus, Sound.ITEM_TRIDENT_THUNDER, 1.8f, 1.1f);
        world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.7f, 0.85f);
        world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
        world.playSound(focus, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 1.8f);
        world.playSound(focus, Sound.BLOCK_END_PORTAL_SPAWN, 1.1f, 1.4f);
        world.playSound(focus, Sound.ITEM_TOTEM_USE, 1.0f, 1.3f);
        world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.4f, 1.6f);
        world.playSound(focus, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.5f);

        spawnSparks();
        judge();
        release();
        caster.sendActionBar(net.kyori.adventure.text.Component.text("§b§l✦ AZURE JUDGMENT ✦"));
        caster.sendMessage("§b✦ Rune Sigil §7— judgment cast.");
    }

    private void spawnSparks() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int n = LIVE.size() > MAX_LIVE - 30 ? 10 : 20;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Material mat = RUNE_MATS[i % RUNE_MATS.length];
            BlockDisplay chunk = spawn(focus, mat.createBlockData(), LIT, CYAN, centered(0.01f, new Quaternionf()));
            Vector vel = new Vector(Math.cos(a), 0, Math.sin(a))
                    .multiply(random.nextDouble(0.4, 0.95))
                    .setY(0.7 + random.nextDouble(0.6));
            sparks.add(new Ejecta(chunk, focus.clone().add(0, 0.5, 0), vel, i % 2 == 0 ? CYAN : AZURE,
                    (float) random.nextDouble(0.25, 0.55), 22 + random.nextInt(12)));
        }
    }

    private void judge() {
        if (judged) {
            return;
        }
        judged = true;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Entity entity : world.getNearbyEntities(focus, JUDGE_R, 10.0, JUDGE_R)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(focus.toVector());
            double d = Math.hypot(rel.getX(), rel.getZ());
            if (d > JUDGE_R) {
                continue;
            }
            double falloff = d <= 4.0 ? 1.25 : 1.0 - 0.45 * (d - 4.0) / (JUDGE_R - 4.0);
            double amount = damage * falloff;
            ScriptedHits.run(() -> living.damage(amount, caster));
            Vector away = rel.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
            }
            living.setVelocity(away.normalize().multiply(1.55).setY(0.95));
            living.setFallDistance(0f);
        }

        for (Player ally : world.getPlayers()) {
            if (ally.getWorld() != world || ally.getLocation().distanceSquared(focus) > JUDGE_R * JUDGE_R) {
                continue;
            }
            AttributeInstance maxAttr = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxAttr != null ? maxAttr.getValue() : 20.0;
            ally.setHealth(Math.min(max, max));
            ally.setAbsorptionAmount(Math.max(ally.getAbsorptionAmount(), 12.0));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 2, true, true, true));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 12, 2, true, true, true));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 8, 0, true, false, true));
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.2, 0), 10, 0.4, 0.5, 0.4, 0.02);
            ally.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, ally.getLocation().add(0, 1.0, 0), 18, 0.3, 0.6, 0.3, 0.15);
            ally.playSound(ally.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.5f);
            ally.playSound(ally.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
            ally.sendMessage("§b✦ Azure Rite §7— fully restored.");
            ally.sendActionBar(net.kyori.adventure.text.Component.text("§b❤ Full heal · Absorption · Regen"));
        }
    }

    private void beam() {
        phaseTick++;
        spin += 0.14f;
        putRing(1.0 + 0.06 * Math.sin(phaseTick * 0.4), 0.65f, spin);
        putRunes(1.0, spin * 1.2f);
        putFlames(1.1, spin);
        tickPillar(phaseTick);
        tickSparks();

        if (phaseTick <= 10) {
            world.spawnParticle(Particle.END_ROD, focus.clone().add(0, phaseTick * 2.2, 0), 20, 0.35, 1.2, 0.35, 0.05);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, focus.clone().add(0, phaseTick * 1.8, 0), 14, 0.4, 1.0, 0.4, 0.03);
        }
        if (phaseTick == 2 || phaseTick == 6) {
            world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.1f);
            world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.7f);
        }
        if (phaseTick >= (AFTER - BEAM)) {
            phase = Phase.AFTER;
            phaseTick = 0;
        }
    }

    private void after() {
        phaseTick++;
        tickSparks();
        int span = TOTAL - AFTER;
        double k = phaseTick / (double) span;
        spin += 0.04f;
        float shrink = (float) (1.0 - easeOutQuart(k));
        putRing(Math.max(0.02, shrink), Math.max(0.04f, 0.5f * (1f - (float) k)), spin);
        putRunes(Math.max(0.02, shrink), spin);
        putFlames(Math.max(0.0, 1.0 - k * 1.2), spin);
        fadePillar(phaseTick, span);
        if (coreDisc != null && coreDisc.isValid()) {
            if (k > 0.7) {
                discard(coreDisc);
                coreDisc = null;
            } else {
                coreDisc.teleport(level(focus));
                ease(coreDisc, centered(Math.max(0.05f, 1.8f * (1f - (float) k)),
                        new Quaternionf().rotateY(spin).rotateX((float) Math.toRadians(90))), 3);
            }
        }
        if (phaseTick % 2 == 0) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, focus, Math.max(1, 10 - phaseTick / 2), 2.0, 0.4, 2.0, 0.01);
            world.spawnParticle(Particle.END_ROD, focus.clone().add(0, 1.5, 0), 4, 0.6, 1.5, 0.6, 0.01);
        }
        if (phaseTick == 6) {
            world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.4f);
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
        }
        if (phaseTick == 14) {
            caster.sendMessage("§8…the sigil fades.");
        }
        if (phaseTick >= span && sparks.isEmpty()) {
            phase = Phase.DONE;
        }
    }

    // ------------------------------------------------------------------ geometry poses

    private void putRing(double grow, float width, float spin) {
        double[] scales = {1.0, 0.72, 0.44};
        int segs = Math.max(1, ring.size() / RING_LAYERS);
        for (SealTile tile : ring) {
            if (tile.display == null || !tile.display.isValid()) {
                continue;
            }
            tile.display.teleport(level(focus));
            double radius = RING_R * scales[Math.min(tile.layer, 2)] * grow;
            double ang = tile.angle + (tile.layer == 1 ? -spin : spin * (0.7f + tile.layer * 0.15f));
            float len = (float) (Math.PI * 2 * Math.max(0.35, radius) / segs * 1.1);
            if (grow < 0.03) {
                ease(tile.display, tiny(), 2);
            } else {
                ease(tile.display, ringSeg(ang, radius, 0.02 + tile.layer * 0.03, len,
                        Math.max(0.05f, width), 0.1f), 2);
            }
        }
    }

    private void putRunes(double grow, float spin) {
        int n = runes.size();
        if (n == 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            BlockDisplay rune = runes.get(i);
            if (rune == null || !rune.isValid()) {
                continue;
            }
            double a = spin * 0.55 + Math.PI * 2 * i / n;
            double r = RING_R * 0.82 * grow;
            float h = (float) (1.2 + grow * 2.4 + 0.25 * Math.sin(tick * 0.25 + i));
            Location at = focus.clone().add(Math.cos(a) * r, h * 0.5, Math.sin(a) * r);
            rune.teleport(level(at));
            Quaternionf rot = new Quaternionf()
                    .rotateY((float) (a + Math.PI / 2))
                    .rotateX((float) Math.toRadians(8));
            float thick = 0.12f + (i % 3) * 0.04f;
            float wide = 0.55f + (i % 2) * 0.2f;
            if (grow < 0.05) {
                ease(rune, tiny(), 2);
            } else {
                ease(rune, centeredBox(wide * (float) grow, h * (float) grow, thick, rot), 2);
            }
        }
    }

    private void putFlames(double grow, float spin) {
        int n = flames.size();
        if (n == 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            BlockDisplay flame = flames.get(i);
            if (flame == null || !flame.isValid()) {
                continue;
            }
            double a = -spin * 0.8 + Math.PI * 2 * i / n;
            double r = RING_R * (0.55 + 0.35 * (i % 3) / 2.0) * grow;
            float h = (float) (0.8 + grow * (1.6 + (i % 4) * 0.35) + 0.2 * Math.sin(tick * 0.4 + i));
            Location at = focus.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            flame.teleport(level(at));
            if (grow < 0.04) {
                ease(flame, tiny(), 2);
            } else {
                ease(flame, rod(new Vector3f(0, 0, 0), new Vector3f(0, h, 0), 0.18f + (i % 3) * 0.04f), 2);
            }
        }
    }

    private void tickPillar(int age) {
        float h = BEAM_H * (float) easeOutQuart(Math.min(1.0, age / 5.0));
        float[] w = {1.8f, 1.15f, 0.7f, 0.28f};
        for (int i = 0; i < pillar.length; i++) {
            BlockDisplay beam = pillar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            beam.teleport(level(focus));
            float width = age < 8 ? w[i] : Math.max(0.06f, w[i] - (age - 8) * 0.05f);
            ease(beam, rod(new Vector3f(0, 0, 0), new Vector3f(0, h, 0), width), 2);
        }
    }

    private void fadePillar(int age, int span) {
        float h = BEAM_H * (float) (1.0 - age / (double) span);
        for (int i = 0; i < pillar.length; i++) {
            BlockDisplay beam = pillar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            if (h < 1.0f) {
                discard(beam);
                pillar[i] = null;
                continue;
            }
            beam.teleport(level(focus));
            ease(beam, rod(new Vector3f(0, 0, 0), new Vector3f(0, h, 0), Math.max(0.05f, 0.6f - age * 0.02f)), 3);
        }
    }

    private void tickSparks() {
        Iterator<Ejecta> it = sparks.iterator();
        while (it.hasNext()) {
            Ejecta chunk = it.next();
            if (!chunk.step()) {
                discard(chunk.display);
                it.remove();
            }
        }
    }

    private void burst(Particle particle, int count, double speed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit(random);
            world.spawnParticle(particle, focus, 0, dir.getX(), dir.getY(), dir.getZ(), speed);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Location floorNear(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 1.5, 0), new Vector(0, -1, 0), 48.0,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return level(hit.getHitPosition().toLocation(world));
        }
        Location out = at.clone();
        out.setY(world.getHighestBlockYAt(at) + 0.14);
        return level(out);
    }

    private static Location level(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static Vector randomUnit(ThreadLocalRandom random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        double theta = 2 * Math.PI * u;
        double phi = Math.acos(2 * v - 1);
        return new Vector(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    private static double easeOutQuart(double x) {
        return 1.0 - Math.pow(1.0 - Math.max(0, Math.min(1, x)), 4);
    }

    private void drawRing(Location center, double radius, int points, Particle.DustOptions dust, double phase) {
        for (int i = 0; i < points; i++) {
            double a = phase + Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.12, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private static Transformation tiny() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
    }

    private static Transformation centered(float size, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        float s = Math.max(0.001f, size);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(s / 2f, s / 2f, s / 2f));
        return new Transformation(half.negate(), r, new Vector3f(s, s, s), new Quaternionf());
    }

    private static Transformation centeredBox(float x, float y, float z, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(x / 2f, y / 2f, z / 2f));
        return new Transformation(half.negate(), r, new Vector3f(Math.max(0.001f, x), Math.max(0.001f, y), Math.max(0.001f, z)),
                new Quaternionf());
    }

    private static Transformation rod(Vector3f a, Vector3f b, float width) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        float w = Math.max(0.001f, width);
        if (len < 1.0E-3f) {
            return tiny();
        }
        d.div(len);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), d);
        Vector3f start = new Vector3f(a).sub(new Vector3f(d).mul(w * 0.35f));
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, w / 2f));
        return new Transformation(start.sub(off), rot, new Vector3f(w, len + w * 0.7f, w), new Quaternionf());
    }

    private static Transformation ringSeg(double angle, double radius, double lift, float length, float width, float thick) {
        Quaternionf yaw = new Quaternionf().rotateY((float) angle);
        Vector3f radial = new Vector3f((float) Math.sin(angle), 0, (float) Math.cos(angle));
        Vector3f center = new Vector3f(radial).mul((float) radius).add(0, (float) lift, 0);
        Quaternionf rot = new Quaternionf(yaw).rotateX((float) Math.toRadians(90));
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(length / 2f, thick / 2f, width / 2f));
        return new Transformation(center.sub(half), rot, new Vector3f(length, thick, width), new Quaternionf());
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, BlockData data, Display.Brightness light, Color glow,
                                      Transformation initial) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(level(at.clone()), BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                if (light != null) {
                    spawned.setBrightness(light);
                }
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(initial != null ? initial : tiny());
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearAll() {
        discard(seed);
        discard(coreDisc);
        seed = null;
        coreDisc = null;
        for (SealTile tile : ring) {
            discard(tile.display);
        }
        ring.clear();
        for (BlockDisplay rune : runes) {
            discard(rune);
        }
        runes.clear();
        for (BlockDisplay flame : flames) {
            discard(flame);
        }
        flames.clear();
        for (int i = 0; i < pillar.length; i++) {
            discard(pillar[i]);
            pillar[i] = null;
        }
        for (Ejecta spark : sparks) {
            discard(spark.display);
        }
        sparks.clear();
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
