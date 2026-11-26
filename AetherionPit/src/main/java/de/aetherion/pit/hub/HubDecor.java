package de.aetherion.pit.hub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Display props on the hub (2026-11-26): slim lamp posts with a swinging lantern, the signpost with arrow boards.
 * Everything is spawned non-persistent while its chunk is loaded and checked every two seconds.
 *
 * <pre>
 * decor:
 * - {type: lamp, x: 1.5, y: 65, z: 2.5, yaw: 90}
 * - {type: signpost, x: 21.5, y: 65, z: 4.5, boards: [{text: AETHERION, x: -20, z: 0, y: 2.0}, ...]}
 * </pre>
 * The light itself is a vanilla light block the world generator puts next to each lantern.
 */
public final class HubDecor {

    public static final String TAG = "ae_hub_decor";
    private static final TextColor INK = TextColor.color(0xF6E9C9);

    private final Plugin plugin;
    private final List<Prop> props = new ArrayList<>();
    private int step;

    private abstract static class Prop {
        final Location at;
        final List<Display> parts = new ArrayList<>();

        Prop(Location at) {
            this.at = at;
        }

        boolean alive() {
            if (parts.isEmpty()) {
                return false;
            }
            for (Display d : parts) {
                if (d == null || !d.isValid()) {
                    return false;
                }
            }
            return true;
        }

        void remove() {
            for (Display d : parts) {
                if (d != null && d.isValid()) {
                    d.remove();
                }
            }
            parts.clear();
        }

        abstract void spawn();

        void animate(int step) {
        }
    }

    private final class Lamp extends Prop {
        final float yaw;
        BlockDisplay lantern;
        static final float S = 1.35f;
        static final float SL = 0.62f;

        Lamp(Location at, float yaw) {
            super(at);
            this.yaw = yaw;
        }

        @Override
        void spawn() {
            remove();
            World w = at.getWorld();
            Quaternionf q = yawQ(yaw);
            ItemDisplay post = itemDisplay(w, at, HubModels.LAMP_POST);
            post.setTransformation(new Transformation(new Vector3f(0, 0.5f * S, 0), q,
                    new Vector3f(S, S, S), new Quaternionf()));
            parts.add(post);
            lantern = w.spawn(at, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
                d.setBlock(Bukkit.createBlockData("minecraft:lantern[hanging=true]"));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(1.2f);
            });
            parts.add(lantern);
            swing(0f, 0);
        }

        /** hook of the arm in block units relative to the post's foot */
        Vector3f hook() {
            Vector3f local = new Vector3f(14.2f / 16f - 0.5f, 25.2f / 16f - 0.5f, 0f).mul(S);
            return local.rotate(yawQ(yaw)).add(0, 0.5f * S, 0);
        }

        void swing(float deg, int duration) {
            if (lantern == null || !lantern.isValid()) {
                return;
            }
            Quaternionf q = yawQ(yaw).rotateX((float) Math.toRadians(deg));
            Vector3f attach = new Vector3f(0.5f, 1.0f, 0.5f).mul(SL).rotate(q);
            Vector3f t = hook().sub(attach);
            lantern.setInterpolationDelay(0);
            lantern.setInterpolationDuration(duration);
            lantern.setTransformation(new Transformation(t, q, new Vector3f(SL, SL, SL), new Quaternionf()));
        }

        @Override
        void animate(int step) {
            int phase = (int) ((step + Math.abs(at.getBlockX() * 7 + at.getBlockZ() * 3)) % 4);
            float deg = phase < 2 ? 3.5f : -3.5f;
            swing(deg, 40);
        }
    }

    private final class SignPost extends Prop {
        final List<Map<?, ?>> boards;

        SignPost(Location at, List<Map<?, ?>> boards) {
            super(at);
            this.boards = boards;
        }

        @Override
        void spawn() {
            remove();
            World w = at.getWorld();
            float s = 1.3f;
            ItemDisplay post = itemDisplay(w, at, HubModels.SIGN_POST);
            post.setTransformation(new Transformation(new Vector3f(0, 0.5f * s, 0), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
            parts.add(post);
            float sb = 1.25f;
            for (Map<?, ?> b : boards) {
                double tx = num(b, "x", at.getX()) - at.getX();
                double tz = num(b, "z", at.getZ()) - at.getZ();
                float hb = (float) num(b, "y", 2.0);
                float theta = (float) Math.atan2(-tz, tx);
                Quaternionf q = new Quaternionf().rotateY(theta);
                // board: model x=0 at the post axis, mid height 2.4 px
                Vector3f pivot = new Vector3f(-0.5f, 2.4f / 16f - 0.5f, 0f).mul(sb).rotate(new Quaternionf(q));
                Vector3f t = new Vector3f(0, hb, 0).sub(pivot);
                ItemDisplay board = itemDisplay(w, at, HubModels.SIGN_BOARD);
                board.setTransformation(new Transformation(t, q, new Vector3f(sb, sb, sb), new Quaternionf()));
                parts.add(board);
                String label = String.valueOf(b.get("text"));
                Component c = Component.text(label, INK).decorate(TextDecoration.BOLD);
                float ts = 0.5f;
                float textH = 0.25f * ts;
                for (int side = 0; side < 2; side++) {
                    Quaternionf tq = new Quaternionf().rotateY(theta + (side == 0 ? 0f : (float) Math.PI));
                    // centre of the board face: 6.5 px along, mid height, just outside the face
                    Vector3f face = new Vector3f(6.5f / 16f * sb, 0f, (side == 0 ? 0.6f : -0.6f) / 16f * sb + (side == 0 ? 0.012f : -0.012f));
                    face.rotate(new Quaternionf(q));
                    Vector3f pos = new Vector3f(0, hb - textH / 2f, 0).add(face);
                    TextDisplay td = w.spawn(at, TextDisplay.class, d -> {
                        d.setPersistent(false);
                        d.addScoreboardTag(TAG);
                        d.text(c);
                        d.setBillboard(Display.Billboard.FIXED);
                        d.setAlignment(TextDisplay.TextAlignment.CENTER);
                        d.setShadowed(true);
                        d.setDefaultBackground(false);
                        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        d.setLineWidth(400);
                        d.setViewRange(0.5f);
                    });
                    td.setTransformation(new Transformation(pos, tq, new Vector3f(ts, ts, ts), new Quaternionf()));
                    parts.add(td);
                }
            }
        }
    }

    /** A real rowing boat on the duck pond: anyone can hop in. Reset when missing (non-persistent, invulnerable). */
    private final class Rowboat extends Prop {
        final float yaw;
        org.bukkit.entity.Boat boat;

        Rowboat(Location at, float yaw) {
            super(at);
            this.yaw = yaw;
        }

        @Override
        boolean alive() {
            return boat != null && boat.isValid();
        }

        @Override
        void remove() {
            if (boat != null && boat.isValid() && boat.getPassengers().isEmpty()) {
                boat.remove();
            }
            boat = null;
        }

        @Override
        void spawn() {
            Location l = at.clone();
            l.setYaw(yaw);
            boat = at.getWorld().spawn(l, org.bukkit.entity.Boat.class, b -> {
                b.setPersistent(false);
                b.setInvulnerable(true);
                b.setBoatType(org.bukkit.entity.Boat.Type.CHERRY);
                b.addScoreboardTag(TAG);
            });
        }

        @Override
        void animate(int step) {
            // drifted far off (somebody rowed it away and left): bring it home when nobody sits in it
            if (boat != null && boat.isValid() && boat.getPassengers().isEmpty()
                    && boat.getLocation().distanceSquared(at) > 9 * 9) {
                boat.teleport(at);
            }
        }
    }

    public HubDecor(Plugin plugin) {
        this.plugin = plugin;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void load() {
        for (Map<?, ?> m : plugin.getConfig().getMapList("decor")) {
            World w = Bukkit.getWorld(String.valueOf(m.containsKey("world") ? m.get("world") : "world"));
            if (w == null) {
                continue;
            }
            Location at = new Location(w, num(m, "x", 0), num(m, "y", 64), num(m, "z", 0));
            String type = String.valueOf(m.get("type"));
            switch (type) {
                case "lamp" -> props.add(new Lamp(at, (float) num(m, "yaw", 0)));
                case "boat" -> props.add(new Rowboat(at, (float) num(m, "yaw", 0)));
                case "signpost" -> {
                    List<Map<?, ?>> boards = new ArrayList<>();
                    Object bs = m.get("boards");
                    if (bs instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> bm) {
                                boards.add(bm);
                            }
                        }
                    }
                    props.add(new SignPost(at, boards));
                }
                default -> plugin.getLogger().warning("Unknown decor type " + type);
            }
        }
    }

    private void tick() {
        step++;
        for (Prop p : props) {
            World w = p.at.getWorld();
            if (!w.isChunkLoaded(p.at.getBlockX() >> 4, p.at.getBlockZ() >> 4)) {
                continue;
            }
            if (!p.alive()) {
                p.spawn();
            } else {
                p.animate(step);
            }
        }
    }

    public void removeAll() {
        props.forEach(Prop::remove);
    }

    private ItemDisplay itemDisplay(World w, Location at, int code) {
        return w.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(HubModels.item(code));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setViewRange(1.2f);
        });
    }

    static Quaternionf yawQ(float yawDeg) {
        // yaw like Minecraft: 0 = +z (south), 90 = -x (west). The models point their arm/board along +x.
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg - 90));
    }

    static double num(Map<?, ?> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    @SuppressWarnings("unused")
    private static BlockData data(String s) {
        return Bukkit.createBlockData(s);
    }

    @SuppressWarnings("unused")
    private static Material m(String s) {
        return Material.matchMaterial(s);
    }
}
