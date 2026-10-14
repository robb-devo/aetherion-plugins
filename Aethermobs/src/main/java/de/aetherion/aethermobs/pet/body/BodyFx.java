package de.aetherion.aethermobs.pet.body;

import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.items.model.Rarity;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Soft ambient presence: a glint for rares, a mote for epics, a halo pulse for legendaries and up, an element
 * whisper per species, and the dragon kit (wing trails, breath, flap). Everything is gated on a viewer within
 * ~20 blocks and a global per-tick particle budget, so a crowded hub never turns into particle soup.
 */
final class BodyFx {

    private static final int PARTICLE_BUDGET_PER_TICK = 70;
    private static long budgetTick = -1L;
    private static int budgetUsed;

    private static final BlockData SAND = Material.SAND.createBlockData();

    private final PetBody body;
    private final String id;
    private final Rarity rarity;
    private final Color tint;
    private final Element element;
    private int clock;

    enum Element {
        NONE, FROST, FLAME, SPORE, LEAF, BLOOM, GLOW, SPARK, TIDE, SOUL, GEM, AETHER, SAND, WITHER, SLIME, GLITCH, NECTAR
    }

    BodyFx(PetBody body, PetInstance pet) {
        this.body = body;
        this.id = pet == null || pet.getDefinition() == null ? "" : pet.getDefinition().getId().toLowerCase(Locale.ROOT);
        this.rarity = pet == null || pet.getRarity() == null ? Rarity.COMMON : pet.getRarity();
        this.tint = rarityColor(rarity);
        this.element = elementOf(id);
        this.clock = ThreadLocalRandom.current().nextInt(40);
    }

    static Element elementOf(String id) {
        return switch (id) {
            case "ice_dragon", "yeti", "snowflake", "polar_bear" -> Element.FROST;
            case "fire_dragon", "blaze" -> Element.FLAME;
            case "shroomling", "mycelord", "mooshroom" -> Element.SPORE;
            case "forest_spirit", "forest_dragon", "nature_dragon", "moss_sprite" -> Element.LEAF;
            case "bloom_fairy", "butterfly" -> Element.BLOOM;
            case "glow_squid", "allay", "lush_oracle" -> Element.GLOW;
            case "lightning_dragon" -> Element.SPARK;
            case "water_dragon" -> Element.TIDE;
            case "dungeon_dragon", "dungeon_zombie", "dungeon_skeleton", "swamp_hag" -> Element.SOUL;
            case "mining_dragon", "poison_dragon" -> Element.GEM;
            case "aetherion" -> Element.AETHER;
            case "sand_wraith" -> Element.SAND;
            case "wither" -> Element.WITHER;
            case "slime_minion" -> Element.SLIME;
            case "hacker" -> Element.GLITCH;
            case "bee" -> Element.NECTAR;
            default -> Element.NONE;
        };
    }

    static Color rarityColor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Color.fromRGB(235, 235, 235);
            case UNCOMMON -> Color.fromRGB(96, 230, 96);
            case RARE -> Color.fromRGB(96, 140, 255);
            case EPIC -> Color.fromRGB(176, 84, 232);
            case LEGENDARY -> Color.fromRGB(255, 176, 32);
            case MYTHIC -> Color.fromRGB(255, 110, 230);
            case AETHERED -> Color.fromRGB(176, 20, 40);
        };
    }

    private static boolean budget(int cost) {
        long now = Bukkit.getCurrentTick();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (budgetUsed + cost > PARTICLE_BUDGET_PER_TICK) {
            return false;
        }
        budgetUsed += cost;
        return true;
    }

    void tick(int dt, Location anchor, boolean moving, boolean near) {
        clock += dt;
        if (!near || anchor.getWorld() == null || body.mode() == PetBody.Mode.BALL) {
            return;
        }
        double viewer = body.rig().nearestViewerSq();
        if (viewer > 20.0 * 20.0) {
            return;
        }
        World world = anchor.getWorld();
        Rig rig = body.rig();
        Location center = anchor.clone().add(0.0, rig.scaledMiddle(), 0.0);
        float size = rig.size();

        rarityPresence(world, center, size);
        elementWhisper(world, center, size, moving);
        if (rig.isDragon()) {
            dragonKit(world, anchor, moving);
        }
    }

    private void rarityPresence(World world, Location center, float size) {
        int ord = rarity.ordinal();
        if (ord < Rarity.RARE.ordinal()) {
            return;
        }
        int every = switch (rarity) {
            case RARE -> 50;
            case EPIC -> 28;
            case LEGENDARY -> 18;
            default -> 12;
        };
        if (clock % every >= 2 || !budget(ord >= Rarity.LEGENDARY.ordinal() ? 3 : 1)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double spread = 0.35 * size;
        Particle.DustOptions dust = new Particle.DustOptions(tint, ord >= Rarity.LEGENDARY.ordinal() ? 0.9f : 0.65f);
        world.spawnParticle(Particle.DUST, center.clone().add(r.nextGaussian() * spread, r.nextDouble() * 0.3 * size,
                r.nextGaussian() * spread), 1, 0.0, 0.0, 0.0, 0.0, dust);
        if (ord >= Rarity.LEGENDARY.ordinal()) {
            // A slow halo pulse: three motes on a ring around the body.
            double a = clock * 0.21;
            double radius = 0.45 * size + 0.15;
            for (int i = 0; i < 2; i++) {
                double ang = a + i * Math.PI;
                world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(ang) * radius, 0.05, Math.sin(ang) * radius),
                        1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
        if (rarity == Rarity.AETHERED && clock % (every * 3) < 2) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void elementWhisper(World world, Location center, float size, boolean moving) {
        if (element == Element.NONE || clock % 16 >= 2 || !budget(1)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double s = 0.3 * size;
        Location at = center.clone().add(r.nextGaussian() * s, r.nextDouble() * s, r.nextGaussian() * s);
        switch (element) {
            case FROST -> world.spawnParticle(Particle.SNOWFLAKE, at, 1, 0.0, 0.0, 0.0, 0.004);
            case FLAME -> world.spawnParticle(Particle.SMALL_FLAME, at, 1, 0.0, 0.02, 0.0, 0.004);
            case SPORE -> world.spawnParticle(Particle.MYCELIUM, at, 2, 0.1, 0.1, 0.1, 0.0);
            case LEAF -> world.spawnParticle(Particle.COMPOSTER, at, 1, 0.0, 0.0, 0.0, 0.0);
            case BLOOM -> {
                if (moving) {
                    world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0,
                            new Particle.DustOptions(Color.fromRGB(255, 170, 220), 0.6f));
                }
            }
            case GLOW -> world.spawnParticle(Particle.GLOW, at, 1, 0.05, 0.05, 0.05, 0.0);
            case SPARK -> world.spawnParticle(Particle.ELECTRIC_SPARK, at, 2, 0.1, 0.1, 0.1, 0.0);
            case TIDE -> world.spawnParticle(Particle.BUBBLE_POP, at, 1, 0.05, 0.05, 0.05, 0.0);
            case SOUL -> world.spawnParticle(Particle.SOUL, at, 1, 0.0, 0.01, 0.0, 0.004);
            case GEM -> world.spawnParticle(Particle.WAX_OFF, at, 1, 0.05, 0.05, 0.05, 0.0);
            case AETHER -> world.spawnParticle(Particle.REVERSE_PORTAL, at, 2, 0.1, 0.1, 0.1, 0.01);
            case SAND -> world.spawnParticle(Particle.FALLING_DUST, at, 1, 0.1, 0.05, 0.1, 0.0, SAND);
            case WITHER -> world.spawnParticle(Particle.SMOKE, at, 1, 0.05, 0.05, 0.05, 0.0);
            case SLIME -> {
                if (moving && clock % 32 < 2) {
                    world.spawnParticle(Particle.ITEM_SLIME, center.clone().add(0, -0.25 * size, 0), 2, 0.15, 0.0, 0.15, 0.0);
                }
            }
            case GLITCH -> {
                if (r.nextInt(3) == 0) {
                    world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0,
                            new Particle.DustOptions(Color.fromRGB(80, 255, 120), 0.5f));
                }
            }
            case NECTAR -> {
                if (r.nextInt(4) == 0) {
                    world.spawnParticle(Particle.FALLING_NECTAR, at, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            default -> {
            }
        }
    }

    /*
     * =========================================================
     * DRAGON KIT
     * =========================================================
     */

    private void dragonKit(World world, Location anchor, boolean moving) {
        Rig rig = body.rig();
        int[] wings = rig.roles().wings;
        boolean flying = body.isFlying() || rig.skeleton == Skeleton.WYRM;
        if (flying && wings.length >= 4 && moving && body.speed() > 0.06f && clock % 2 == 0 && budget(2)) {
            Location l = rig.worldPoint(wings[2], 0.6f, 0f, -0.1f);
            Location r = rig.worldPoint(wings[3], -0.6f, 0f, -0.1f);
            Particle trail = trailParticle();
            if (l != null) {
                world.spawnParticle(trail, l, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (r != null) {
                world.spawnParticle(trail, r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (rig.skeleton == Skeleton.WYRM && moving && clock % 4 == 0 && budget(1)) {
            int[] segs = rig.roles().segments;
            if (segs.length > 0) {
                Location tail = rig.worldPoint(segs[segs.length - 1], 0f, 0f, -0.2f);
                if (tail != null) {
                    world.spawnParticle(trailParticle(), tail, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }
        // Heavy wing beat thump while flying close to someone.
        if (rig.skeleton == Skeleton.DRAGON && flying && clock % 18 < 2 && rig.nearestViewerSq() < 14 * 14) {
            world.playSound(anchor, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.NEUTRAL, 0.22f, 1.55f);
        }
        // Element aura, rising motes around the body (the old dragon aura, now on the body centre).
        if (clock % 6 < 2 && budget(1)) {
            Location c = anchor.clone().add(0.0, rig.scaledMiddle(), 0.0);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            double s = 0.45 * rig.size();
            Location at = c.add(rnd.nextGaussian() * s, rnd.nextDouble() * s, rnd.nextGaussian() * s);
            world.spawnParticle(trailParticle(), at, 1, 0.0, 0.02, 0.0, 0.003);
        }
    }

    private Particle trailParticle() {
        return switch (element) {
            case FROST -> Particle.SNOWFLAKE;
            case FLAME -> Particle.FLAME;
            case LEAF -> Particle.HAPPY_VILLAGER;
            case SPARK -> Particle.ELECTRIC_SPARK;
            case TIDE -> Particle.DRIPPING_WATER;
            case SOUL -> Particle.SOUL_FIRE_FLAME;
            case GEM -> Particle.WAX_OFF;
            case AETHER -> Particle.END_ROD;
            default -> Particle.END_ROD;
        };
    }

    /** One breath beat, called every tick of the breath envelope. */
    void breath(int left) {
        Rig rig = body.rig();
        if (rig.anchor() == null || rig.roles().jaw < 0 || rig.nearestViewerSq() > 26 * 26) {
            return;
        }
        World world = rig.anchor().getWorld();
        Location mouth = rig.worldPoint(rig.roles().jaw, 0f, 0.02f, 0.36f);
        Location ahead = rig.worldPoint(rig.roles().jaw, 0f, 0.1f, 1.4f);
        if (mouth == null || ahead == null) {
            return;
        }
        if (left == 30) {
            Sound sound = switch (element) {
                case FLAME -> Sound.ENTITY_BLAZE_SHOOT;
                case FROST -> Sound.BLOCK_POWDER_SNOW_STEP;
                case SPARK -> Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
                case TIDE -> Sound.ENTITY_DOLPHIN_SPLASH;
                case LEAF -> Sound.BLOCK_AZALEA_LEAVES_BREAK;
                case GEM -> Sound.BLOCK_AMETHYST_CLUSTER_BREAK;
                case SOUL -> Sound.PARTICLE_SOUL_ESCAPE;
                default -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            };
            world.playSound(mouth, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.NEUTRAL, 0.16f, 1.7f);
            world.playSound(mouth, sound, SoundCategory.NEUTRAL, 0.5f, 1.1f);
        }
        if (left > 26 || left < 6 || left % 2 != 0 || !budget(4)) {
            return;
        }
        Vector dir = ahead.toVector().subtract(mouth.toVector()).normalize();
        Particle core = trailParticle();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Vector v = dir.clone().add(new Vector(r.nextGaussian() * 0.12, r.nextGaussian() * 0.08, r.nextGaussian() * 0.12));
            world.spawnParticle(core == Particle.DRIPPING_WATER ? Particle.BUBBLE : core, mouth, 0,
                    v.getX(), v.getY(), v.getZ(), 0.22);
        }
        if (element == Element.FLAME || element == Element.SOUL) {
            world.spawnParticle(Particle.SMOKE, mouth, 0, dir.getX(), dir.getY() + 0.1, dir.getZ(), 0.08);
        } else if (element == Element.FROST) {
            world.spawnParticle(Particle.CLOUD, mouth, 0, dir.getX(), dir.getY(), dir.getZ(), 0.1);
        }
    }

    /** Pop when an equipped ground pet hops into / out of its sphere. */
    void ballSwap(boolean into) {
        Rig rig = body.rig();
        if (rig.anchor() == null || !rig.isBuilt()) {
            return;
        }
        Location at = rig.anchor().getLocation().add(0.0, 0.1, 0.0);
        World world = at.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(tint, 0.9f);
        world.spawnParticle(Particle.DUST, at, 8, 0.18, 0.18, 0.18, 0.0, dust);
        world.spawnParticle(Particle.END_ROD, at, 3, 0.1, 0.1, 0.1, 0.02);
        world.playSound(at, into ? Sound.BLOCK_IRON_TRAPDOOR_CLOSE : Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                SoundCategory.NEUTRAL, 0.35f, into ? 1.8f : 1.6f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.4f, into ? 1.2f : 1.6f);
    }
}
