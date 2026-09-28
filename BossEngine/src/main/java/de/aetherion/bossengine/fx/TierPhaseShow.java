package de.aetherion.bossengine.fx;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase cinematics for the four rite bosses. A handful of block displays, posed each tick.
 * The generic flame/soul sphere stays off while one of these is running.
 */
public final class TierPhaseShow {

    private static final Map<UUID, Rig> LIVE = new ConcurrentHashMap<>();

    private TierPhaseShow() {
    }

    public static boolean owns(BossInstance instance) {
        return switch (CombatTheatrics.id(instance)) {
            case "hollow_lurker", "skuldugery", "mcnugget", "bridge_troll" -> true;
            default -> false;
        };
    }

    public static void tick(BossInstance instance, Location at, int tick, int duration) {
        if (!owns(instance) || at == null || at.getWorld() == null) {
            return;
        }
        Rig rig = LIVE.computeIfAbsent(instance.getInstanceId(), ignored -> Rig.open(CombatTheatrics.id(instance), at));
        if (rig == null) {
            return;
        }
        rig.pose(at, tick, Math.max(1, duration));
    }

    public static void clear(BossInstance instance) {
        if (instance == null) {
            return;
        }
        Rig rig = LIVE.remove(instance.getInstanceId());
        if (rig != null) {
            rig.remove();
        }
    }

    private static final class Rig {
        private final String boss;
        private final List<BlockDisplay> pieces = new ArrayList<>();

        private static Rig open(String boss, Location at) {
            Rig rig = new Rig(boss);
            World world = at.getWorld();
            switch (boss) {
                case "skuldugery" -> {
                    for (int i = 0; i < 6; i++) {
                        rig.pieces.add(spawn(world, at, Material.BONE_BLOCK, Color.fromRGB(255, 220, 180)));
                    }
                    rig.pieces.add(spawn(world, at, Material.MAGMA_BLOCK, Color.fromRGB(255, 120, 40)));
                    for (int i = 0; i < 8; i++) {
                        rig.pieces.add(spawn(world, at, Material.ORANGE_CONCRETE, null));
                    }
                }
                case "hollow_lurker" -> {
                    for (int i = 0; i < 4; i++) {
                        rig.pieces.add(spawn(world, at, Material.SCULK, Color.fromRGB(40, 180, 190)));
                        rig.pieces.add(spawn(world, at, Material.SCULK_CATALYST, Color.fromRGB(80, 230, 220)));
                    }
                }
                case "mcnugget" -> {
                    for (int i = 0; i < 6; i++) {
                        rig.pieces.add(spawn(world, at, Material.GOLD_BLOCK, Color.fromRGB(255, 196, 64)));
                    }
                    rig.pieces.add(spawn(world, at, Material.HONEYCOMB_BLOCK, Color.fromRGB(255, 160, 40)));
                }
                case "bridge_troll" -> {
                    for (int i = 0; i < 4; i++) {
                        rig.pieces.add(spawn(world, at, Material.CHAIN, Color.fromRGB(214, 168, 72)));
                    }
                    rig.pieces.add(spawn(world, at, Material.GOLD_BLOCK, Color.fromRGB(255, 210, 80)));
                }
                default -> {
                    return null;
                }
            }
            return rig;
        }

        private Rig(String boss) {
            this.boss = boss;
        }

        private void pose(Location at, int tick, int duration) {
            double u = Math.min(1.0, tick / (double) duration);
            switch (boss) {
                case "skuldugery" -> poseSkull(at, tick, u);
                case "hollow_lurker" -> poseLurker(at, tick, u);
                case "mcnugget" -> poseNugget(at, tick, u);
                case "bridge_troll" -> poseTroll(at, tick, u);
                default -> {
                }
            }
        }

        /** Six bone ribs close in. Eight embers sit on the floor. One magma heart. */
        private void poseSkull(Location at, int tick, double u) {
            double spin = tick * 0.08;
            double radius = 3.4 - u * 1.8;
            for (int i = 0; i < 6; i++) {
                double a = spin + i * Math.PI * 2 / 6;
                Location p = at.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
                place(pieces.get(i), p, yaw(a), 0.28f, 1.35f, 0.28f);
            }
            place(pieces.get(6), at.clone().add(0, 1.15 + Math.sin(tick * 0.2) * 0.08, 0),
                    yaw(spin * 2), 0.42f, 0.42f, 0.42f);
            double floor = 2.8 - u * 1.2;
            for (int i = 0; i < 8; i++) {
                double a = -spin * 0.6 + i * Math.PI * 2 / 8;
                Location p = at.clone().add(Math.cos(a) * floor, 0.02, Math.sin(a) * floor);
                place(pieces.get(7 + i), p, yaw(a), 0.35f, 0.12f, 0.35f);
            }
        }

        /** Sculk pillars grow out of the floor, then fold back in. */
        private void poseLurker(Location at, int tick, double u) {
            double rise = u < 0.7 ? u / 0.7 : 1.0 - (u - 0.7) / 0.3;
            rise = Math.max(0.05, rise);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + 0.4;
                Location base = at.clone().add(Math.cos(a) * 2.4, 0.02, Math.sin(a) * 2.4);
                place(pieces.get(i * 2), base, yaw(a), 0.7f, (float) (1.6 * rise), 0.7f);
                Location cap = base.clone().add(0, 1.6 * rise, 0);
                place(pieces.get(i * 2 + 1), cap, yaw(tick * 0.05), 0.45f, 0.45f, 0.45f);
            }
        }

        /** A gold fryer ring and one honeycomb lid. */
        private void poseNugget(Location at, int tick, double u) {
            double spin = tick * 0.12;
            for (int i = 0; i < 6; i++) {
                double a = spin + i * Math.PI * 2 / 6;
                Location p = at.clone().add(Math.cos(a) * 2.2, 0.05 + Math.sin(tick * 0.25 + i) * 0.04, Math.sin(a) * 2.2);
                place(pieces.get(i), p, yaw(a), 0.4f, 0.22f, 0.4f);
            }
            double lid = 1.8 - u * 0.9;
            place(pieces.get(6), at.clone().add(0, lid, 0), yaw(spin), 0.9f, 0.18f, 0.9f);
        }

        /** Four chains and a gold plate. The hover itself stays in the storm. */
        private void poseTroll(Location at, int tick, double u) {
            double height = 0.6 + u * 2.4;
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + tick * 0.02;
                Location p = at.clone().add(Math.cos(a) * 2.1, 0.05, Math.sin(a) * 2.1);
                place(pieces.get(i), p, yaw(a), 0.2f, (float) height, 0.2f);
            }
            place(pieces.get(4), at.clone().add(0, 0.02, 0), new Quaternionf(), 1.6f, 0.08f, 1.6f);
        }

        private void remove() {
            for (BlockDisplay display : pieces) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            pieces.clear();
        }
    }

    private static BlockDisplay spawn(World world, Location at, Material material, Color glow) {
        return world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(2);
            display.setInterpolationDuration(2);
            display.setBillboard(Display.Billboard.FIXED);
            display.setViewRange(1.6f);
            display.setShadowRadius(0f);
            display.setPersistent(false);
            display.setGravity(false);
            if (glow != null) {
                display.setGlowing(true);
                display.setGlowColorOverride(glow);
            }
        });
    }

    private static void place(BlockDisplay display, Location at, Quaternionf rotation, float x, float y, float z) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.teleport(at);
        Vector3f scale = new Vector3f(x, y, z);
        Vector3f offset = new Quaternionf(rotation).transform(new Vector3f(-x / 2f, 0f, -z / 2f));
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(offset, rotation, scale, new Quaternionf()));
    }

    private static Quaternionf yaw(double radians) {
        return new Quaternionf().rotateY((float) radians);
    }
}
