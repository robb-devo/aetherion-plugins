package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.AttributeUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hollow Lurker, "What Waits Below". Everything comes from underneath: a pool of dark that follows him, sculk
 * that spreads where he walks, and a maw of black fangs that snaps shut under whoever he marks. Ink black and
 * sculk cyan; no fire, no gold, no runes.
 */
final class LurkerKit extends RiteKit {

    private static final Color CYAN = Color.fromRGB(40, 214, 204);
    private static final Color PALE = Color.fromRGB(160, 250, 244);
    private static final Color INK = Color.fromRGB(10, 18, 22);
    private static final Color DEEP = Color.fromRGB(18, 70, 76);
    private static final double MAW = 1.8;

    private final List<Location> marks = new ArrayList<>();
    private final List<Player> marked = new ArrayList<>();
    private final List<Maw> maws = new ArrayList<>();
    private final List<BlockDisplay> trail = new ArrayList<>();
    private Location lastTrail;
    private boolean pool;

    LurkerKit(BossInstance instance) {
        super(instance);
    }

    /* ================================================================== look */

    @Override
    protected void dress(LivingEntity boss) {
        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.setHelmet(hollow(Material.LEATHER_HELMET, TrimPattern.EYE));
            eq.setChestplate(hollow(Material.LEATHER_CHESTPLATE, TrimPattern.SILENCE));
            eq.setLeggings(hollow(Material.LEATHER_LEGGINGS, TrimPattern.WARD));
            eq.setBoots(hollow(Material.LEATHER_BOOTS, TrimPattern.SILENCE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
        }
        if (!pool) {
            pool = true;
            Location at = boss.getLocation();
            flat(wear(stage.block(at, Material.BLACK_CONCRETE, null, null, 0),
                    Stage.v(0, 0.03, 0), new Quaternionf(), 1f, true), 1.9f);
            flat(wear(stage.block(at, Material.BLACK_CONCRETE, null, null, 0),
                    Stage.v(0, 0.035, 0), Stage.yaw(Math.PI / 4), 1f, true), 1.9f);
            flat(wear(stage.block(at, Material.SCULK, null, null, 7),
                    Stage.v(0, 0.045, 0), Stage.yaw(Math.PI / 8), 1f, true), 1.05f);
        }
    }

    private static void flat(Follow f, float width) {
        if (f != null) {
            f.sx = width;
            f.sz = width;
            f.sy = 0.02f;
        }
    }

    private static ItemStack hollow(Material material, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0x121619));
            stack.setItemMeta(meta);
        }
        if (stack.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(TrimMaterial.DIAMOND, pattern));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    protected void ambient(LivingEntity boss, int age) {
        Location feet = boss.getLocation();
        double h = boss.getHeight();
        if (age % 3 == 0) {
            Stage.dust(feet.clone().add(0, h * 0.25, 0), INK, 1.7f, 2, 0.35);
        }
        if (age % 9 == 0) {
            Stage.fx(feet.clone().add(0, 0.2, 0), Particle.SCULK_SOUL, 1, 0.5, 0.01);
        }
        if (age % 70 == 0 && !near(boss, 18).isEmpty()) {
            Stage.sound(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.75f);
        }
        if (age % 10 == 0 && (lastTrail == null || lastTrail.getWorld() != feet.getWorld()
                || lastTrail.distanceSquared(feet) > 0.8)) {
            lastTrail = feet.clone();
            dropVein(Stage.ground(feet));
        }
    }

    /** Sculk creeping where he walked, shrinking back over three seconds. */
    private void dropVein(Location at) {
        trail.removeIf(d -> !Stage.alive(d));
        if (trail.size() > 7) {
            return;
        }
        double yaw = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        float size = (float) ThreadLocalRandom.current().nextDouble(0.8, 1.3);
        Vector3f c = Stage.v(0, 0.01, 0);
        BlockDisplay d = stage.block(at, Material.SCULK, Stage.cube(c, Stage.yaw(yaw), 0.05f, 0.02f, 0.05f), null, 6);
        if (d == null) {
            return;
        }
        trail.add(d);
        stage.play(t -> {
            if (t == 2) {
                Stage.push(d, Stage.cube(c, Stage.yaw(yaw), size, 0.02f, size * 0.6f), 6);
            } else if (t == 14) {
                Stage.push(d, Stage.cube(c, Stage.yaw(yaw + 0.3), 0.02f, 0.02f, 0.02f), 50);
            } else if (t >= 66) {
                stage.remove(d);
                return true;
            }
            return false;
        });
    }

    /* ================================================================== arrival: he climbs out of the dark */

    @Override
    protected int arrivalTicks() {
        return 58;
    }

    @Override
    protected void arrivalStart(LivingEntity boss, Location at) {
        Location deep = at.clone().add(0, -2.8, 0);
        place(boss, deep);
        Location floor = Stage.ground(at);
        for (Player p : near(boss, 22)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, false));
        }
        Stage.sound(floor, Sound.BLOCK_SCULK_SPREAD, 1.4f, 0.5f);
        Stage.sound(floor, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.2f, 0.6f);
        stage.play(new PoolOpen(floor, 3.4, 58));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 11; i++) {
            double yaw = i * Math.PI * 2 / 11 + rng.nextDouble(-0.2, 0.2);
            stage.play(new Vein(floor, yaw, rng.nextDouble(3.6, 5.6), 3 + i, 58));
        }
    }

    @Override
    protected void arrivalTick(LivingEntity boss, Location at, int t) {
        Location floor = Stage.ground(at);
        if (t == 12) {
            Stage.sound(floor, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.3f, 0.55f);
            Stage.fx(floor.clone().add(0, 0.4, 0), Particle.SHRIEK, 1, 0, 0, 0);
        }
        if (t == 20) {
            Stage.sound(floor, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.9f);
        }
        if (t >= 20 && t <= 46) {
            double u = Stage.ease((t - 20) / 26.0);
            Location body = at.clone().add(0, -2.8 * (1.0 - u), 0);
            place(boss, body);
            Stage.fx(floor.clone().add(0, 0.2, 0), Particle.BLOCK, 6, 0.6, 0.05, 0.6, 0,
                    Material.SCULK.createBlockData());
            Stage.fx(floor.clone().add(0, 0.3, 0), Particle.SCULK_SOUL, 2, 0.8, 0.03);
        }
        if (t % 2 == 0 && t < 50) {
            Stage.ring(floor, 3.2, 0.55, Particle.SCULK_CHARGE_POP, null, 0.1);
        }
        if (t == 46) {
            place(boss, at);
            Stage.fx(at.clone().add(0, boss.getHeight() * 0.6, 0), Particle.SONIC_BOOM, 1, 0, 0);
            Stage.sound(at, Sound.ENTITY_WARDEN_ROAR, 1.1f, 1.15f);
            Stage.dustRing(floor, 4.0, PALE, 1.6f, 0.4);
        }
    }

    @Override
    protected void arrivalEnd(LivingEntity boss, Location at) {
        titleCard(boss, "&8&lHollow Lurker", "What Waits Below", "Was unten wartet");
        Stage.sound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.6f);
    }

    /* ================================================================== The Maw */

    @Override
    protected int cooldown(boolean late) {
        return late ? 190 : 260;
    }

    @Override
    protected int begin(LivingEntity boss, List<Player> near, boolean late) {
        marks.clear();
        marked.clear();
        maws.clear();
        List<Player> pool = new ArrayList<>(near);
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(late ? 4 : 2, pool.size()); i++) {
            Player p = pool.get(i);
            Location m = Stage.ground(p.getLocation());
            marked.add(p);
            marks.add(m);
            Maw maw = new Maw(m, 22);
            maws.add(maw);
            stage.play(maw);
        }
        Location at = boss.getLocation();
        Stage.sound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);
        Stage.sound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.8f);
        cue(marked, "&3The Maw &7· get off the dark", "&3Der Schlund &7· runter vom Dunkel");
        return 22;
    }

    @Override
    protected void charge(LivingEntity boss, int left, boolean late) {
        if (left % 2 != 0) {
            return;
        }
        for (int i = 0; i < marked.size() && i < marks.size(); i++) {
            Player p = marked.get(i);
            if (!p.isValid() || p.getWorld() != marks.get(i).getWorld()) {
                continue;
            }
            // the dark tugs at whoever it marked: souls drain off them into the pool
            Location from = p.getLocation().add(0, 0.3, 0);
            Stage.fx(from, Particle.SCULK_SOUL, 1, 0.25, 0.02);
        }
    }

    @Override
    protected void flash(LivingEntity boss) {
        for (Maw maw : maws) {
            maw.flash();
        }
        Stage.sound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.9f);
    }

    @Override
    protected void strike(LivingEntity boss, boolean late) {
        for (Maw maw : maws) {
            maw.snap();
            for (Player p : hitCircle(boss, maw.centre, MAW, late ? 24 : 20, 0.0)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, true));
            }
        }
        marks.clear();
        marked.clear();
    }

    @Override
    protected void cancelMove() {
        for (Maw maw : maws) {
            maw.abort();
        }
        maws.clear();
        marks.clear();
        marked.clear();
    }

    /** A pool of dark with eight fangs under it; opens over the windup, snaps on {@link #snap()}. */
    private final class Maw implements Stage.Anim {
        final Location centre;
        final int windup;
        final List<BlockDisplay> plates = new ArrayList<>();
        final List<BlockDisplay[]> fangs = new ArrayList<>();
        int snapAt = -1;
        int t;
        boolean white;
        boolean dead;

        Maw(Location centre, int windup) {
            this.centre = centre.clone();
            this.windup = windup;
        }

        void flash() {
            white = true;
            for (BlockDisplay[] fang : fangs) {
                for (BlockDisplay d : fang) {
                    Stage.glow(d, WHITE);
                }
            }
        }

        void snap() {
            snapAt = t;
        }

        void abort() {
            dead = true;
        }

        @Override
        public boolean tick(int tick) {
            t = tick;
            if (dead) {
                return true;
            }
            if (tick == 1) {
                plates.add(stage.block(centre, Material.BLACK_CONCRETE, null, null, 0));
                plates.add(stage.block(centre, Material.BLACK_CONCRETE, null, null, 0));
                plates.add(stage.block(centre, Material.SCULK, null, null, 6));
                for (int i = 0; i < 8; i++) {
                    fangs.add(new BlockDisplay[]{
                            stage.block(centre, Material.BLACK_CONCRETE, null, null, 1),
                            stage.block(centre, Material.BLACK_CONCRETE, null, null, 1)
                    });
                }
                return false;
            }
            if (tick == 2) {
                float r = (float) (MAW * 2.1);
                Stage.push(plates.get(0), Stage.cube(Stage.v(0, 0.015, 0), new Quaternionf(), r, 0.02f, r), windup);
                Stage.push(plates.get(1), Stage.cube(Stage.v(0, 0.02, 0), Stage.yaw(Math.PI / 4), r, 0.02f, r), windup);
                Stage.push(plates.get(2), Stage.cube(Stage.v(0, 0.03, 0), Stage.yaw(Math.PI / 8), r * 0.62f, 0.02f, r * 0.62f), windup);
                pose(-2.9f, 0.15f, 0);
            }
            if (snapAt < 0) {
                if (tick % 2 == 0) {
                    Stage.dustRing(centre, MAW, white ? WHITE : CYAN, white ? 1.8f : 1.2f, 0.45);
                    Stage.ring(centre, MAW * 0.5, 0.9, Particle.SCULK_CHARGE_POP, null, 0.1);
                }
                if (tick == windup - 10) {
                    // the tips break the surface: last chance to read it
                    pose(-1.9f, 0.2f, 6);
                    Stage.sound(centre, Sound.BLOCK_SCULK_CHARGE, 1.0f, 0.6f);
                }
                if (tick > windup + 30) {
                    return true;
                }
                return false;
            }
            int s = tick - snapAt;
            if (s == 0) {
                for (BlockDisplay[] fang : fangs) {
                    for (BlockDisplay d : fang) {
                        Stage.glow(d, CYAN);
                    }
                }
                pose(0f, 0.32f, 2);
                Stage.sound(centre, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.4f, 0.55f);
                Stage.sound(centre, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.8f, 1.1f);
                Stage.fx(centre.clone().add(0, 0.4, 0), Particle.BLOCK, 24, 0.9, 0.1, 0.9, 0,
                        Material.SCULK.createBlockData());
            } else if (s == 3) {
                pose(0f, 0.95f, 2);
                Stage.sound(centre, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.3f, 0.6f);
                Stage.fx(centre.clone().add(0, 1.2, 0), Particle.SCULK_SOUL, 12, 0.5, 0.06);
                Stage.dust(centre.clone().add(0, 1.0, 0), PALE, 2.0f, 10, 0.6);
            } else if (s == 10) {
                pose(-3.2f, 0.6f, 8);
                for (BlockDisplay d : plates) {
                    Stage.push(d, Stage.gone(Stage.v(0, 0.02, 0)), 10);
                }
            } else if (s >= 20) {
                return true;
            }
            return false;
        }

        /** Fangs standing on the rim: {@code sink} below the floor, {@code lean} radians toward the middle. */
        private void pose(float sink, float lean, int interp) {
            for (int i = 0; i < fangs.size(); i++) {
                double a = Math.PI * 2 * i / fangs.size() + 0.2;
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                Vector3f base = Stage.v(ca * (MAW - 0.05), sink, sa * (MAW - 0.05));
                Vector3f dir = Stage.v(-ca * Math.sin(lean), Math.cos(lean), -sa * Math.sin(lean)).normalize();
                Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x, dir.y, dir.z).rotateY((float) (Math.PI / 4));
                Stage.push(fangs.get(i)[0], Stage.post(base, q, 0.42f, 1.7f, 0.42f), interp);
                Vector3f up = new Vector3f(dir).mul(1.6f).add(base);
                Stage.push(fangs.get(i)[1], Stage.post(up, q, 0.22f, 1.1f, 0.22f), interp);
            }
        }

        @Override
        public void end() {
            for (BlockDisplay d : plates) {
                stage.remove(d);
            }
            for (BlockDisplay[] fang : fangs) {
                for (BlockDisplay d : fang) {
                    stage.remove(d);
                }
            }
            plates.clear();
            fangs.clear();
        }
    }

    /* ================================================================== RING_BURST: echoes through the floor */

    @Override
    public void ringPreview(Location centre, double step, int waves, int windupTicks) {
        Location floor = Stage.ground(centre);
        Stage.sound(floor, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.6f);
        Stage.sound(floor, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.75f);
        stage.play(t -> {
            for (int i = 1; i <= waves; i++) {
                if (t <= windupTicks + (i - 1) * 6 && t % 3 == 0) {
                    Stage.dustRing(floor, i * step, DEEP, 1.0f, 0.75);
                }
            }
            return t >= windupTicks + waves * 6;
        });
    }

    @Override
    public void ringWave(Location centre, double radius, int index, boolean last) {
        Location floor = Stage.ground(centre);
        stage.play(t -> {
            double r = radius - 1.0 + t * 0.4;
            Stage.dustRing(floor, r, t < 3 ? PALE : CYAN, 1.7f, 0.5);
            if (t == 1) {
                Stage.ring(floor, radius, 0.8, Particle.SCULK_CHARGE_POP, null, 0.15);
            }
            return t >= 4;
        });
        Stage.sound(floor, Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 1.0f, 0.5f + index * 0.12f);
        if (last) {
            Stage.fx(floor.clone().add(0, 1.0, 0), Particle.SONIC_BOOM, 1, 0, 0);
            Stage.sound(floor, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.3f);
        }
    }

    @Override
    public void slam(Location centre, double radius) {
        Location floor = Stage.ground(centre);
        Stage.fx(floor.clone().add(0, 0.3, 0), Particle.BLOCK, 30, radius * 0.4, 0.1, radius * 0.4, 0,
                Material.SCULK.createBlockData());
        Stage.dustRing(floor, radius, CYAN, 1.6f, 0.5);
        Stage.sound(floor, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 0.8f);
    }

    @Override
    public void meteor(Location ground) {
        Stage.fx(ground.clone().add(0, 0.4, 0), Particle.SCULK_SOUL, 10, 0.6, 0.04);
        Stage.dustRing(ground, 1.6, CYAN, 1.4f, 0.4);
    }

    /* ================================================================== frenzy: the dark rises */

    private final List<BlockDisplay> shriekers = new ArrayList<>();
    private PoolOpen frenzyPool;

    @Override
    public void transitionStart(Location at) {
        Location floor = Stage.ground(at);
        LivingEntity boss = instance.getEntity();
        if (boss != null) {
            for (Player p : near(boss, 24)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 90, 0, false, false, false));
            }
        }
        Stage.sound(floor, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.7f);
        Stage.sound(floor, Sound.BLOCK_SCULK_SPREAD, 1.4f, 0.45f);
        frenzyPool = new PoolOpen(floor, 5.6, 9999);
        stage.play(frenzyPool);
        shriekers.clear();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + 0.3;
            Location spot = floor.clone().add(Math.cos(a) * 4.4, 0, Math.sin(a) * 4.4);
            BlockDisplay d = stage.block(spot, Material.SCULK_SHRIEKER, Stage.post(Stage.v(0, -0.9, 0), Stage.yaw(a), 0.9f, 0.9f, 0.9f), null, 8);
            shriekers.add(d);
            int delay = 4 + i * 5;
            stage.play(t -> {
                if (t == delay) {
                    Stage.push(d, Stage.post(Stage.v(0, 0, 0), Stage.yaw(a), 1.15f, 1.15f, 1.15f), 6);
                    Stage.sound(spot, Sound.BLOCK_SCULK_SHRIEKER_PLACE, 1.0f, 0.6f);
                    Stage.fx(spot.clone().add(0, 0.3, 0), Particle.BLOCK, 10, 0.4, 0.1, 0.4, 0,
                            Material.SCULK.createBlockData());
                }
                if (t == delay + 12) {
                    Stage.fx(spot.clone().add(0, 1.1, 0), Particle.SHRIEK, 1, 0, 0, 0);
                    Stage.sound(spot, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.9f, 0.6f + delay * 0.01f);
                }
                return t >= delay + 12;
            });
        }
    }

    @Override
    public void transitionTick(Location at, int tick, int duration) {
        double t = tick / (double) Math.max(1, duration);
        Location floor = Stage.ground(at);
        if (tick % 2 == 0) {
            Stage.dustRing(floor, 5.6 - t * 4.4, INK, 2.2f, 0.45);
            Stage.dustRing(floor, 5.7 - t * 4.4, CYAN, 1.0f, 0.9);
        }
        if (tick % 3 == 0) {
            Stage.fx(floor.clone().add(0, 0.3, 0), Particle.SCULK_SOUL, 2, 2.2, 0.03);
        }
        int beat = t < 0.5 ? 16 : t < 0.8 ? 10 : 5;
        if (tick % beat == 0) {
            Stage.sound(floor, Sound.ENTITY_WARDEN_HEARTBEAT, 1.1f, 0.6f + (float) t * 0.4f);
        }
    }

    @Override
    public void transitionEnd(Location at) {
        Location floor = Stage.ground(at);
        for (BlockDisplay d : shriekers) {
            if (Stage.alive(d)) {
                Stage.fx(d.getLocation().add(0, 0.5, 0), Particle.BLOCK, 18, 0.4, 0.3, 0.4, 0,
                        Material.SCULK_SHRIEKER.createBlockData());
            }
            stage.remove(d);
        }
        shriekers.clear();
        if (frenzyPool != null) {
            frenzyPool.close(6);
            frenzyPool = null;
        }
        Stage.sound(floor, Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.75f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Stage.fx(floor.clone().add(Math.cos(a) * 2.2, 1.0, Math.sin(a) * 2.2), Particle.SONIC_BOOM, 1, 0, 0);
        }
        Stage.dustRing(floor, 4.5, PALE, 2.0f, 0.4);
    }

    /* ================================================================== death: swallowed by its own dark */

    private final List<BlockDisplay[]> deathFangs = new ArrayList<>();
    private final List<BlockDisplay> eyes = new ArrayList<>();
    private Location deathAt;
    private PoolOpen deathPool;

    @Override
    protected void deathStart(Location core, LivingEntity body) {
        deathAt = Stage.ground(body != null ? body.getLocation() : core);
        deathPool = new PoolOpen(deathAt, 3.4, 9999);
        stage.play(deathPool);
        for (int i = 0; i < 10; i++) {
            deathFangs.add(new BlockDisplay[]{
                    stage.block(deathAt, Material.BLACK_CONCRETE, null, null, 1),
                    stage.block(deathAt, Material.BLACK_CONCRETE, null, null, 1)
            });
        }
        deathFangPose(-3.4f, -0.2f, 0);
    }

    @Override
    protected void deathTick(Location core, LivingEntity body, int tick, int duration, double startScale) {
        if (deathAt == null) {
            return;
        }
        if (tick == 6) {
            for (BlockDisplay[] fang : deathFangs) {
                Stage.glow(fang[0], CYAN);
                Stage.glow(fang[1], CYAN);
            }
            deathFangPose(-0.4f, -0.55f, 30);
            Stage.sound(deathAt, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);
        }
        if (tick % 10 == 0 && tick < 70) {
            Stage.sound(deathAt, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.75f - tick * 0.004f);
        }
        if (tick % 2 == 0 && tick < 76) {
            Stage.fx(core, Particle.SCULK_SOUL, 2, 0.5, 0.03);
            Stage.dust(core, INK, 1.8f, 3, 0.6);
        }
        if (tick == 64) {
            deathFangPose(0.3f, 1.05f, 4);
            Stage.sound(deathAt, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.5f);
            Stage.sound(deathAt, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.4f, 0.5f);
            Stage.fx(deathAt.clone().add(0, 1.5, 0), Particle.BLOCK, 40, 1.2, 0.8, 1.2, 0,
                    Material.SCULK.createBlockData());
        }
        if (tick == 70) {
            for (int i = 0; i < 2; i++) {
                eyes.add(stage.block(deathAt, Material.SEA_LANTERN, Stage.cube(Stage.v(i == 0 ? -0.22 : 0.22, 2.4, 0),
                        new Quaternionf(), 0.12f, 0.06f, 0.06f), PALE, 15));
            }
        }
        if (tick > 70 && tick < 92 && tick % 7 == 0) {
            // a blink: the eyes close, then open again a bit lower
            float y = (float) (2.4 - (tick - 70) * 0.04);
            for (int i = 0; i < eyes.size(); i++) {
                Stage.push(eyes.get(i), Stage.cube(Stage.v(i == 0 ? -0.22 : 0.22, y, 0), new Quaternionf(),
                        0.12f, tick % 14 == 0 ? 0.01f : 0.06f, 0.06f), 2);
            }
        }
        if (tick == 92) {
            for (BlockDisplay d : eyes) {
                stage.remove(d);
            }
            eyes.clear();
            deathFangPose(-3.6f, 0.9f, 10);
            if (deathPool != null) {
                deathPool.close(12);
            }
        }
        if (body != null && body.isValid() && !body.isDead()) {
            double u = Math.min(1.0, tick / (double) Math.max(1, duration - 20));
            AttributeUtil.setBase(body, AttributeUtil.scale(), Math.max(0.35, startScale * (1.0 - u * 0.75)));
        }
    }

    private void deathFangPose(float sink, float lean, int interp) {
        double r = 2.5;
        for (int i = 0; i < deathFangs.size(); i++) {
            double a = Math.PI * 2 * i / deathFangs.size();
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            Vector3f base = Stage.v(ca * r, sink, sa * r);
            Vector3f dir = Stage.v(-ca * Math.sin(lean), Math.cos(lean), -sa * Math.sin(lean)).normalize();
            Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x, dir.y, dir.z).rotateY((float) (Math.PI / 4));
            Stage.push(deathFangs.get(i)[0], Stage.post(base, q, 0.55f, 2.4f, 0.55f), interp);
            Vector3f up = new Vector3f(dir).mul(2.3f).add(base);
            Stage.push(deathFangs.get(i)[1], Stage.post(up, q, 0.28f, 1.5f, 0.28f), interp);
        }
    }

    /* ================================================================== shared pieces of this kit */

    /** A spreading octagon of dark with a sculk heart; holds until {@link #close(int)} or its lifetime ends. */
    private final class PoolOpen implements Stage.Anim {
        final Location at;
        final double radius;
        final int life;
        final List<BlockDisplay> parts = new ArrayList<>();
        int closeAt = -1;
        int closeTicks;
        int t;

        PoolOpen(Location at, double radius, int life) {
            this.at = at.clone();
            this.radius = radius;
            this.life = life;
        }

        void close(int ticks) {
            closeAt = t;
            closeTicks = ticks;
            for (BlockDisplay d : parts) {
                Stage.push(d, Stage.gone(Stage.v(0, 0.02, 0)), ticks);
            }
        }

        @Override
        public boolean tick(int tick) {
            t = tick;
            if (tick == 1) {
                parts.add(stage.block(at, Material.BLACK_CONCRETE, null, null, 0));
                parts.add(stage.block(at, Material.BLACK_CONCRETE, null, null, 0));
                parts.add(stage.block(at, Material.SCULK, null, null, 7));
                parts.add(stage.block(at, Material.SCULK, null, null, 7));
                return false;
            }
            if (tick == 2) {
                float d = (float) (radius * 2);
                Stage.push(parts.get(0), Stage.cube(Stage.v(0, 0.012, 0), new Quaternionf(), d, 0.02f, d), 18);
                Stage.push(parts.get(1), Stage.cube(Stage.v(0, 0.016, 0), Stage.yaw(Math.PI / 4), d, 0.02f, d), 18);
                Stage.push(parts.get(2), Stage.cube(Stage.v(0, 0.022, 0), Stage.yaw(Math.PI / 8), d * 0.55f, 0.02f, d * 0.55f), 22);
                Stage.push(parts.get(3), Stage.cube(Stage.v(0, 0.024, 0), Stage.yaw(Math.PI * 3 / 8), d * 0.4f, 0.02f, d * 0.4f), 26);
            }
            if (closeAt >= 0) {
                return tick - closeAt >= closeTicks + 1;
            }
            return tick >= life;
        }

        @Override
        public void end() {
            for (BlockDisplay d : parts) {
                stage.remove(d);
            }
            parts.clear();
        }
    }

    /** A sculk vein racing out from the centre, then drawing back. */
    private final class Vein implements Stage.Anim {
        final Location at;
        final double yaw;
        final double length;
        final int start;
        final int life;
        BlockDisplay d;

        Vein(Location at, double yaw, double length, int start, int life) {
            this.at = at.clone();
            this.yaw = yaw;
            this.length = length;
            this.start = start;
            this.life = life;
        }

        @Override
        public boolean tick(int t) {
            if (t == start) {
                d = stage.block(at, Material.SCULK, strip(0.05), null, 8);
            } else if (t == start + 2) {
                Stage.push(d, strip(length), 12);
            } else if (t == life - 10) {
                Stage.push(d, strip(0.02), 10);
            }
            return t >= life;
        }

        private org.joml.Matrix4f strip(double len) {
            Quaternionf q = Stage.yaw(-yaw + Math.PI / 2);
            return new org.joml.Matrix4f().translation(0f, 0.03f, 0f).rotate(q)
                    .scale(0.22f, 0.02f, (float) len).translate(-0.5f, -0.5f, 0f);
        }

        @Override
        public void end() {
            stage.remove(d);
        }
    }
}
