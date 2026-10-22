package de.aetherion.items.world;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The altar rite, one choreography per boss: the ten seconds of the bossbar build that boss's own world around the
 * altar and hand straight into BossEngine's arrival.
 *
 * <ul>
 *   <li>Hollow Lurker: the floor goes black, sculk crawls out, a heartbeat quickens, shriekers grow and scream.</li>
 *   <li>Bridge Troll: chains drop in at the corners, coins orbit faster and faster, a toll heap grows, heavy steps.</li>
 *   <li>McNugget: a feeding call. Grain lands in a ring, feathers whirl up, the clucking builds, a fryer bell counts.</li>
 *   <li>Skuldugery: arrows rain down and plant a ring, bones climb out and circle, a war drum, the ring catches fire.</li>
 * </ul>
 *
 * Display entities only, never saved, all removed when the show ends or the rite is abandoned.
 */
final class RiteSummonShow extends BukkitRunnable {

    private static final int OUTRO = 14;
    private static final Color SCULK = Color.fromRGB(40, 200, 210);

    private final List<Display> owned = new ArrayList<>();
    private final List<BlockDisplay> blocks = new ArrayList<>();
    private final List<BlockDisplay> extra = new ArrayList<>();
    private final List<ItemDisplay> items = new ArrayList<>();
    private final List<ItemDisplay> swirl = new ArrayList<>();
    private final Location anchor;
    private final World world;
    private final String bossId;
    private final int length;
    private double orbit;
    private int t;

    private RiteSummonShow(String bossId, Location altar, int length) {
        this.bossId = bossId == null ? "" : bossId;
        this.anchor = altar.clone();
        this.anchor.setYaw(0f);
        this.anchor.setPitch(0f);
        this.world = altar.getWorld();
        this.length = length;
    }

    static RiteSummonShow start(JavaPlugin plugin, String bossId, Location altar, int length) {
        RiteSummonShow show = new RiteSummonShow(bossId, altar, length);
        if (show.world != null) {
            show.runTaskTimer(plugin, 1L, 1L);
        }
        return show;
    }

    void stop() {
        for (Display d : owned) {
            if (d != null && d.isValid()) {
                d.remove();
            }
        }
        owned.clear();
        blocks.clear();
        extra.clear();
        items.clear();
        swirl.clear();
        try {
            cancel();
        } catch (IllegalStateException ignored) {
            // never scheduled
        }
    }

    @Override
    public void run() {
        t++;
        if (t > length + OUTRO) {
            stop();
            return;
        }
        try {
            switch (bossId) {
                case "hollow_lurker" -> lurker();
                case "bridge_troll" -> troll();
                case "mcnugget" -> nugget();
                case "skuldugery" -> skull();
                default -> {
                    if (t % 20 == 1) {
                        sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.8f);
                    }
                }
            }
        } catch (RuntimeException ex) {
            stop();
            throw ex;
        }
    }

    /* ================================================================== Hollow Lurker */

    private void lurker() {
        double p = Math.min(1.0, t / (double) length);
        if (t == 1) {
            blocks.add(block(Material.BLACK_CONCRETE.createBlockData(), plate(0.1f, 0), null, 0));
            blocks.add(block(Material.BLACK_CONCRETE.createBlockData(), plate(0.1f, Math.PI / 4), null, 0));
            for (int i = 0; i < 12; i++) {
                blocks.add(block(Material.SCULK.createBlockData(), vein(i, 0.05f), null, -1));
            }
            sound(Sound.BLOCK_SCULK_SPREAD, 1.4f, 0.5f);
            sound(Sound.AMBIENT_CAVE, 1.0f, 0.6f);
        }
        if (t % 10 == 2 && t < length - 8) {
            float s = (float) (0.6 + 5.0 * p);
            push(blocks.get(0), plate(s, 0), 10);
            push(blocks.get(1), plate(s, Math.PI / 4), 10);
            for (int i = 0; i < 12; i++) {
                push(blocks.get(2 + i), vein(i, (float) (0.6 + 4.2 * p)), 10);
            }
            if (t % 30 == 2) {
                sound(Sound.BLOCK_SCULK_SPREAD, 0.9f, 0.6f + (float) p * 0.4f);
            }
        }
        int beat = t < 100 ? 20 : t < 160 ? 12 : 6;
        if (t % beat == 0 && t < length) {
            sound(Sound.ENTITY_WARDEN_HEARTBEAT, 1.3f, 0.7f + (float) p * 0.3f);
            dustRing(0.6 + 2.4 * p, SCULK, 1.0f);
        }
        if (t % 3 == 0 && t < length) {
            fx(Particle.SCULK_SOUL, around(0.4 + 2.4 * p, 0.2), 1, 0.1, 0.03);
            fx(Particle.SQUID_INK, around(0.6 + 2.6 * p, 0.1), 1, 0.05, 0.0);
        }
        // four shriekers sprout on the rim, one after another
        if (t >= 90 && t <= 132 && (t - 90) % 14 == 0) {
            int k = (t - 90) / 14;
            extra.add(block(Material.SCULK_SHRIEKER.createBlockData(), shrieker(k, 0.01f), null, -1));
            sound(Sound.BLOCK_SCULK_SHRIEKER_PLACE, 1.0f, 0.6f);
        }
        if (t >= 91 && t <= 133 && (t - 91) % 14 == 0) {
            int k = (t - 91) / 14;
            if (k < extra.size()) {
                push(extra.get(k), shrieker(k, 0.95f), 10);
            }
        }
        if (t == 160) {
            sound(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.6f, 0.7f);
            sound(Sound.ENTITY_WARDEN_ANGRY, 0.8f, 0.6f);
            for (int k = 0; k < 4; k++) {
                fx(Particle.SCULK_SOUL, rim(k, 2.6, 1.2), 12, 0.3, 0.06);
            }
            for (Player player : near(16)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
            }
        }
        if (t == length - 6) {
            // the pool swallows its own veins; the Lurker's arrival opens the real one
            for (BlockDisplay d : blocks) {
                push(d, gone(0, 0.02, 0), 8);
            }
            for (BlockDisplay d : extra) {
                push(d, gone(0, -0.5, 0), 8);
            }
            sound(Sound.ENTITY_WARDEN_DIG, 1.4f, 0.8f);
            fx(Particle.SQUID_INK, anchor.clone().add(0, 0.3, 0), 40, 1.6, 0.05);
        }
    }

    private static Matrix4f plate(float size, double rot) {
        return new Matrix4f().translation(0f, 0.02f, 0f).rotateY((float) rot)
                .scale(size, 0.02f, size).translate(-0.5f, -0.5f, -0.5f);
    }

    /** Six veins, each with one branch off its middle, flat on the floor. */
    private static Matrix4f vein(int i, float len) {
        int main = i % 6;
        double a = main * Math.PI / 3 + 0.26;
        float x = 0f;
        float z = 0f;
        if (i >= 6) {
            x = (float) (Math.cos(a) * len * 0.45);
            z = (float) (Math.sin(a) * len * 0.45);
            a += main % 2 == 0 ? 0.6 : -0.6;
            len *= 0.55f;
        }
        return new Matrix4f().translation(x, 0.05f, z).rotateY((float) (Math.PI / 2 - a))
                .scale(i >= 6 ? 0.22f : 0.34f, 0.03f, len).translate(-0.5f, -0.5f, 0f);
    }

    private static Matrix4f shrieker(int k, float size) {
        double a = k * Math.PI / 2 + Math.PI / 4;
        return new Matrix4f().translation((float) (Math.cos(a) * 2.6), 0f, (float) (Math.sin(a) * 2.6))
                .rotateY((float) (-a)).scale(size).translate(-0.5f, 0f, -0.5f);
    }

    /* ================================================================== Bridge Troll */

    private static final int LINKS = 4;
    private static final int COINS = 12;

    private void troll() {
        if (t == 1) {
            BlockData chain = Material.CHAIN.createBlockData();
            for (int c = 0; c < 4; c++) {
                for (int k = 0; k < LINKS; k++) {
                    blocks.add(block(chain, link(c, k, 12f), null, -1));
                }
            }
            for (int i = 0; i < COINS; i++) {
                blocks.add(block(Material.GOLD_BLOCK.createBlockData(), gone(0, 1.0, 0), null, 15));
            }
            extra.add(block(Material.RAW_GOLD_BLOCK.createBlockData(), heap(0, 0.01f), null, -1));
            extra.add(block(Material.GOLD_BLOCK.createBlockData(), heap(1, 0.01f), null, -1));
            extra.add(block(Material.GOLD_BLOCK.createBlockData(), heap(2, 0.01f), null, -1));
            sound(Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.5f);
        }
        // chains drop in one corner at a time
        for (int c = 0; c < 4; c++) {
            if (t == 2 + c * 10) {
                for (int k = 0; k < LINKS; k++) {
                    push(blocks.get(c * LINKS + k), link(c, k, 0f), 12);
                }
            }
            if (t == 14 + c * 10) {
                sound(Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.5f);
                sound(Sound.BLOCK_ANVIL_LAND, 0.35f, 1.4f);
            }
        }
        int coins = 4 * LINKS;
        if (t >= 10 && t < length - 6) {
            double spin = Math.min(1.0, (t - 10) / 150.0);
            orbit += 0.03 + 0.14 * spin;
            for (int i = 0; i < COINS; i++) {
                double a = orbit + i * Math.PI * 2 / COINS;
                double r = 2.2 - 0.8 * spin;
                double y = 0.6 + 1.6 * spin + 0.25 * Math.sin(t * 0.2 + i);
                Matrix4f m = new Matrix4f().translation((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r))
                        .rotateY((float) (t * 0.5 + i)).scale(0.55f, 0.55f, 0.06f).translate(-0.5f, -0.5f, -0.5f);
                push(blocks.get(coins + i), m, t == 10 ? 6 : 2);
            }
            int clink = spin < 0.5 ? 8 : 4;
            if (t % clink == 0) {
                sound(Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.3f + ThreadLocalRandom.current().nextFloat() * 0.6f);
            }
            if (t % 24 == 0) {
                sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.6f + (float) spin * 0.4f);
            }
            if (t % 4 == 0) {
                fx(Particle.WAX_ON, around(r(spin), 1.0 + 1.6 * spin), 2, 0.3, 0.0);
                fx(Particle.ITEM, around(1.6, 3.6), 1, 0.2, 0.02, new ItemStack(Material.GOLD_NUGGET));
            }
        }
        // the toll heap grows in steps
        if (t >= 60 && t <= 180 && t % 20 == 0) {
            float g = (t - 40) / 140f;
            for (int h = 0; h < extra.size(); h++) {
                push(extra.get(h), heap(h, g), 10);
            }
            sound(Sound.BLOCK_METAL_PLACE, 0.9f, 0.6f + g * 0.4f);
        }
        // something heavy comes up the bridge
        if (t == 100 || t == 130 || t == 155 || t == 175 || t == 190) {
            float closer = (t - 80) / 110f;
            sound(Sound.ENTITY_RAVAGER_STEP, 0.6f + closer, 0.5f);
            fx(Particle.BLOCK, anchor.clone().add(0, 0.2, 0), 10, 2.0, 0.0, Material.GRAVEL.createBlockData());
        }
        if (t == length - 14) {
            sound(Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.6f);
        }
        if (t == length - 6) {
            for (int i = 0; i < COINS; i++) {
                push(blocks.get(coins + i), new Matrix4f().translation(0f, 0.8f, 0f).rotateY(i)
                        .scale(0.9f, 0.9f, 0.1f).translate(-0.5f, -0.5f, -0.5f), 5);
            }
            for (int c = 0; c < 4; c++) {
                for (int k = 0; k < LINKS; k++) {
                    push(blocks.get(c * LINKS + k), link(c, k, 14f), 8);
                }
            }
            sound(Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
            sound(Sound.BLOCK_CHAIN_BREAK, 1.4f, 0.5f);
        }
        if (t == length + 2) {
            for (BlockDisplay d : blocks) {
                push(d, gone(0, 0.8, 0), 8);
            }
            for (BlockDisplay d : extra) {
                push(d, gone(0, 0.1, 0), 10);
            }
        }
    }

    private static double r(double spin) {
        return 2.2 - 0.8 * spin;
    }

    private static Matrix4f link(int corner, int k, float drop) {
        float x = corner % 2 == 0 ? 2.4f : -2.4f;
        float z = corner < 2 ? 2.4f : -2.4f;
        return new Matrix4f().translation(x, drop + k + 0.5f, z).translate(-0.5f, -0.5f, -0.5f);
    }

    private static Matrix4f heap(int part, float g) {
        return switch (part) {
            case 0 -> new Matrix4f().translation(0f, 0f, 0f).rotateY(0.4f).scale(1.1f * g, 0.7f * g, 1.1f * g)
                    .translate(-0.5f, 0f, -0.5f);
            case 1 -> new Matrix4f().translation(0.55f * g, 0f, 0.3f * g).rotateY(1.1f).rotateZ(0.25f)
                    .scale(0.5f * g).translate(-0.5f, 0f, -0.5f);
            default -> new Matrix4f().translation(-0.5f * g, 0f, -0.4f * g).rotateY(-0.6f).rotateX(0.3f)
                    .scale(0.45f * g).translate(-0.5f, 0f, -0.5f);
        };
    }

    /* ================================================================== McNugget */

    private static final int GRAIN = 16;
    private static final int FEATHERS = 10;

    private void nugget() {
        if (t == 1) {
            sound(Sound.ENTITY_CHICKEN_AMBIENT, 1.2f, 0.6f);
        }
        // grain lands in a ring, one handful at a time
        if (t >= 4 && t < 4 + GRAIN * 5 && (t - 4) % 5 == 0) {
            int i = (t - 4) / 5;
            ItemStack stack = new ItemStack(i % 2 == 0 ? Material.WHEAT : Material.WHEAT_SEEDS);
            items.add(item(stack, grain(i, 3.0f, 0.1f), null, -1));
            sound(Sound.BLOCK_COMPOSTER_FILL, 0.8f, 0.9f + i * 0.04f);
        }
        if (t >= 5 && t < 5 + GRAIN * 5 && (t - 5) % 5 == 0) {
            int i = (t - 5) / 5;
            if (i < items.size()) {
                push(items.get(i), grain(i, 0.04f, 0.75f), 4);
            }
        }
        // a feather whirl builds up the middle
        if (t == 60) {
            for (int k = 0; k < FEATHERS; k++) {
                swirl.add(item(new ItemStack(Material.FEATHER), gone(0, 0.2, 0), null, -1));
            }
            sound(Sound.ENTITY_PARROT_FLY, 1.0f, 0.8f);
        }
        if (t > 60 && t < length - 6) {
            double lift = Math.min(1.0, (t - 60) / 60.0);
            for (int k = 0; k < swirl.size(); k++) {
                double h = ((k / (double) FEATHERS) + (t - 60) * 0.012) % 1.0 * 3.4 * lift;
                double r = 0.6 + h * 0.45;
                double a = t * 0.25 + k * 0.63;
                Matrix4f m = new Matrix4f().translation((float) (Math.cos(a) * r), (float) (0.2 + h), (float) (Math.sin(a) * r))
                        .rotateY((float) -a).rotateZ((float) (Math.sin(t * 0.3 + k) * 0.6)).scale(0.8f);
                push(swirl.get(k), m, 2);
            }
        }
        int cluck = t < 120 ? 16 : t < 170 ? 8 : 4;
        if (t % cluck == 0 && t < length) {
            sound(Sound.ENTITY_CHICKEN_AMBIENT, 0.9f, 0.7f + ThreadLocalRandom.current().nextFloat() * 0.7f);
        }
        if (t % 5 == 0 && t < length) {
            fx(Particle.ITEM, around(2.6, 3.0), 1, 0.3, 0.02, new ItemStack(Material.FEATHER));
        }
        // the fryer heats up: oil pops, no flames
        if (t >= 120 && t % 10 == 0 && t < length) {
            sound(Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.9f, 0.7f);
            fx(Particle.WHITE_SMOKE, around(1.0, 0.3), 3, 0.2, 0.01);
        }
        if (t == length - 60 || t == length - 40 || t == length - 20) {
            int n = (t - (length - 60)) / 20;
            sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.0f + n * 0.25f);
        }
        if (t == length - 4) {
            sound(Sound.BLOCK_BELL_USE, 1.2f, 1.6f);
        }
        if (t == length - 6) {
            for (ItemDisplay d : items) {
                push(d, new Matrix4f().translation(0f, 0.3f, 0f).scale(0.01f), 6);
            }
            for (int k = 0; k < swirl.size(); k++) {
                double a = k * Math.PI * 2 / FEATHERS;
                push(swirl.get(k), new Matrix4f().translation((float) (Math.cos(a) * 4.5), 2.5f, (float) (Math.sin(a) * 4.5))
                        .scale(0.01f), 8);
            }
            sound(Sound.ENTITY_CHICKEN_EGG, 1.5f, 0.5f);
            fx(Particle.CLOUD, anchor.clone().add(0, 0.6, 0), 20, 0.8, 0.05);
            fx(Particle.ITEM, anchor.clone().add(0, 1.2, 0), 30, 1.2, 0.15, new ItemStack(Material.FEATHER));
        }
    }

    private static Matrix4f grain(int i, float lift, float size) {
        double a = i * Math.PI * 2 / GRAIN;
        return new Matrix4f().translation((float) (Math.cos(a) * 2.3), lift, (float) (Math.sin(a) * 2.3))
                .rotateY((float) -a).rotateX((float) (Math.PI / 2)).scale(size);
    }

    /* ================================================================== Skuldugery */

    private static final int ARROWS = 10;
    private static final int BONES = 8;

    private void skull() {
        // arrows rain in and plant a ring, tip down
        if (t >= 5 && t < 5 + ARROWS * 9 && (t - 5) % 9 == 0) {
            int i = (t - 5) / 9;
            items.add(item(new ItemStack(Material.ARROW), arrow(i, 12f), null, -1));
            sound(Sound.ENTITY_ARROW_SHOOT, 0.7f, 0.6f);
        }
        if (t >= 6 && t < 6 + ARROWS * 9 && (t - 6) % 9 == 0) {
            int i = (t - 6) / 9;
            if (i < items.size()) {
                push(items.get(i), arrow(i, 0f), 3);
            }
        }
        if (t >= 9 && t < 9 + ARROWS * 9 && (t - 9) % 9 == 0) {
            int i = (t - 9) / 9;
            Location at = rim(i, 2.6, 0.1);
            sound(Sound.ENTITY_ARROW_HIT, 1.1f, 0.6f);
            fx(Particle.ASH, at, 8, 0.2, 0.0);
        }
        // bones climb out of the ground and start to circle
        if (t == 60) {
            for (int k = 0; k < BONES; k++) {
                swirl.add(item(new ItemStack(Material.BONE), bone(k, -0.6, 0), null, -1));
            }
            sound(Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.5f);
        }
        if (t == 61) {
            for (int k = 0; k < swirl.size(); k++) {
                push(swirl.get(k), bone(k, 1.0, 0), 20);
            }
            sound(Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, 0.6f);
        }
        if (t >= 82 && t < length - 6) {
            double turn = (t - 82) * 0.06;
            for (int k = 0; k < swirl.size(); k++) {
                push(swirl.get(k), bone(k, 1.0 + 0.2 * Math.sin(t * 0.15 + k), turn), 2);
            }
        }
        int drum = t < 120 ? 20 : t < 170 ? 10 : 5;
        if (t % drum == 0 && t < length) {
            sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.4f, 0.6f);
        }
        if (t == 130) {
            sound(Sound.ENTITY_SKELETON_AMBIENT, 1.2f, 0.4f);
        }
        if (t % 2 == 0 && t < length) {
            fx(Particle.WHITE_ASH, around(3.0, 2.5), 6, 0.4, 0.0);
        }
        // the planted ring catches fire at the base
        if (t == 140) {
            sound(Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);
        }
        if (t >= 140 && t < length) {
            if (t % 3 == 0) {
                for (int i = 0; i < items.size(); i++) {
                    Location at = rim(i, 2.6, 0.15);
                    fx(Particle.SMALL_FLAME, at, 1, 0.08, 0.01);
                    if (i % 2 == 0) {
                        fx(Particle.SMOKE, at.add(0, 0.4, 0), 1, 0.05, 0.01);
                    }
                }
            }
            if (t % 20 == 0) {
                sound(Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.7f);
            }
        }
        if (t == length - 6) {
            for (int i = 0; i < items.size(); i++) {
                push(items.get(i), arrow(i, 14f), 6);
            }
            for (int k = 0; k < swirl.size(); k++) {
                push(swirl.get(k), new Matrix4f().translation(0f, 0.5f, 0f).scale(0.01f), 6);
            }
            sound(Sound.ENTITY_ARROW_SHOOT, 1.4f, 0.6f);
            sound(Sound.ENTITY_ARROW_SHOOT, 1.4f, 0.8f);
            sound(Sound.BLOCK_BONE_BLOCK_BREAK, 1.4f, 0.5f);
            fx(Particle.LARGE_SMOKE, anchor.clone().add(0, 0.5, 0), 30, 1.4, 0.03);
        }
    }

    /** Tip down: the arrow sprite's tip axis is (1, 1) in its plane, turned to point at the floor. */
    private static Matrix4f arrow(int i, float lift) {
        double a = i * Math.PI * 2 / ARROWS + 0.3;
        return new Matrix4f().translation((float) (Math.cos(a) * 2.6), 0.6f + lift, (float) (Math.sin(a) * 2.6))
                .rotateY((float) (-a)).rotateX(0.18f).rotateZ((float) (-Math.PI * 0.75)).scale(2.0f);
    }

    private static Matrix4f bone(int k, double y, double turn) {
        double a = k * Math.PI * 2 / BONES + turn;
        return new Matrix4f().translation((float) (Math.cos(a) * 1.5), (float) y, (float) (Math.sin(a) * 1.5))
                .rotateY((float) (-a)).rotateZ((float) (turn * 2 + k)).scale(1.1f);
    }

    /* ================================================================== helpers */

    private BlockDisplay block(BlockData data, Matrix4f start, Color glow, int light) {
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setBlock(data);
            prepare(d, start, glow, light);
        });
        owned.add(display);
        return display;
    }

    private ItemDisplay item(ItemStack stack, Matrix4f start, Color glow, int light) {
        ItemDisplay display = world.spawn(anchor, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d, start, glow, light);
        });
        owned.add(display);
        return display;
    }

    private static void prepare(Display d, Matrix4f start, Color glow, int light) {
        d.setPersistent(false);
        d.setTransformationMatrix(start);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(0);
        if (glow != null) {
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
        }
        if (light >= 0) {
            d.setBrightness(new Display.Brightness(light, light));
        }
    }

    private static void push(Display d, Matrix4f m, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interp);
        d.setTransformationMatrix(m);
    }

    private static Matrix4f gone(double x, double y, double z) {
        return new Matrix4f().translation((float) x, (float) y, (float) z).scale(0.001f);
    }

    private Location around(double radius, double y) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double a = rng.nextDouble(Math.PI * 2);
        double d = radius * Math.sqrt(rng.nextDouble());
        return anchor.clone().add(Math.cos(a) * d, y, Math.sin(a) * d);
    }

    /** The point on the rim where ring piece {@code i} of {@link #ARROWS} (or a shrieker) sits. */
    private Location rim(int i, double radius, double y) {
        double a = "skuldugery".equals(bossId) ? i * Math.PI * 2 / ARROWS + 0.3 : i * Math.PI / 2 + Math.PI / 4;
        return anchor.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
    }

    private void dustRing(double radius, Color color, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(8, (int) (radius * Math.PI * 2 / 0.5));
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            world.spawnParticle(Particle.DUST, anchor.getX() + Math.cos(a) * radius, anchor.getY() + 0.1,
                    anchor.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private void fx(Particle particle, Location at, int count, double spread, double speed) {
        world.spawnParticle(particle, at, count, spread, spread, spread, speed);
    }

    private <T> void fx(Particle particle, Location at, int count, double spread, double speed, T data) {
        world.spawnParticle(particle, at, count, spread, spread, spread, speed, data);
    }

    private void sound(Sound sound, float volume, float pitch) {
        world.playSound(anchor, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private List<Player> near(double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= r2) {
                out.add(player);
            }
        }
        return out;
    }
}
