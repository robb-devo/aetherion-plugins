package de.aetherion.core.exile;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Display-built props for the ferry ride: the giant anchor chain and the Ferryman on the bow. */
public final class ExileProps {

    private ExileProps() {
    }

    /** One box of {@code size} centred on {@code center}, its x axis along {@code u}, y along {@code v}. */
    static Display bar(BlockRig rig, BlockData block, Vector3f center, Vector3f u, Vector3f v, Vector3f size) {
        Vector3f n = new Vector3f(u).cross(v).normalize();
        Matrix3f m = new Matrix3f(u.x, u.y, u.z, v.x, v.y, v.z, n.x, n.y, n.z);
        Quaternionf q = new Quaternionf().setFromNormalized(m);
        Vector3f half = new Vector3f(size).mul(0.5f);
        Vector3f t = new Vector3f(center).sub(q.transform(new Vector3f(half)));
        return rig.addBlock(block, t, size, q);
    }

    /**
     * Giant chain links along a polyline (points relative to the rig origin). Links alternate between the
     * vertical and the horizontal plane like the real chains of the Kettenkueste.
     */
    public static int chain(BlockRig rig, List<Vector> points, double linkLen, double linkW, Material material) {
        BlockData block = material.createBlockData();
        int made = 0;
        int k = 0;
        float t = 0.9f;
        for (int s = 0; s + 1 < points.size(); s++) {
            Vector a = points.get(s);
            Vector b = points.get(s + 1);
            Vector d = b.clone().subtract(a);
            double len = d.length();
            Vector3f u = new Vector3f((float) (d.getX() / len), (float) (d.getY() / len), (float) (d.getZ() / len));
            Vector3f side = new Vector3f(u).cross(new Vector3f(0, 1, 0));
            if (side.lengthSquared() < 1e-6) {
                side.set(0, 0, 1);
            }
            side.normalize();
            Vector3f up = new Vector3f(side).cross(u).normalize();
            double step = linkLen * 0.78;
            for (double along = linkLen / 2; along < len; along += step, k++) {
                Vector3f c = new Vector3f((float) (a.getX() + d.getX() * along / len),
                        (float) (a.getY() + d.getY() * along / len), (float) (a.getZ() + d.getZ() * along / len));
                Vector3f v = (k % 2 == 0) ? new Vector3f(up) : new Vector3f(side);
                float lw = (float) linkW;
                float ll = (float) linkLen;
                // two long sides
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vector3f cc = new Vector3f(c).add(new Vector3f(v).mul(sgn * (lw / 2f - t / 2f)));
                    bar(rig, block, cc, u, v, new Vector3f(ll - t, t, t));
                    made++;
                }
                // two short ends
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vector3f cc = new Vector3f(c).add(new Vector3f(u).mul(sgn * (ll / 2f - t / 2f)));
                    bar(rig, block, cc, u, v, new Vector3f(t, lw, t));
                    made++;
                }
            }
        }
        return made;
    }

    /** The Ferryman as he rides the bow: robe, hood with no face, a long pole and a lantern swinging off it. */
    public static final class Ferryman {
        public final List<Display> body = new ArrayList<>();
        public Display pole;
        public Display lantern;
        public Vector3f poleRest;
        public Quaternionf poleRot;
        public Vector3f lanternRest;
    }

    /**
     * @param feet  his feet, relative to the rig origin
     * @param west  true = faces -x (the travel direction)
     */
    public static Ferryman ferryman(BlockRig rig, Vector3f feet) {
        Ferryman f = new Ferryman();
        Quaternionf none = new Quaternionf();
        BlockData robe = Material.GRAY_WOOL.createBlockData();
        BlockData dark = Material.BLACK_WOOL.createBlockData();
        BlockData pale = Material.LIGHT_GRAY_WOOL.createBlockData();
        BlockData voidFace = Material.BLACK_CONCRETE.createBlockData();
        // hem, robe, cloak, shoulders, hood — boxes centred on his axis (x forward is -x world)
        f.body.add(box(rig, robe, feet, -0.36f, 0.0f, -0.32f, 0.72f, 0.26f, 0.64f));
        f.body.add(box(rig, dark, feet, -0.28f, 0.2f, -0.25f, 0.56f, 0.9f, 0.5f));
        f.body.add(box(rig, robe, feet, -0.31f, 1.0f, -0.25f, 0.62f, 0.56f, 0.5f));
        f.body.add(box(rig, dark, feet, -0.34f, 1.44f, -0.29f, 0.68f, 0.18f, 0.58f));
        f.body.add(box(rig, pale, feet, -0.28f, 1.56f, -0.22f, 0.5f, 0.48f, 0.44f));
        f.body.add(box(rig, pale, feet, -0.33f, 1.6f, -0.2f, 0.06f, 0.4f, 0.4f));      // hood brim
        f.body.add(box(rig, voidFace, feet, -0.345f, 1.64f, -0.13f, 0.02f, 0.28f, 0.26f)); // where a face was
        // arms reaching forward to the pole
        f.body.add(box(rig, robe, feet, -0.62f, 1.18f, 0.12f, 0.42f, 0.16f, 0.16f));
        f.body.add(box(rig, robe, feet, -0.58f, 1.26f, -0.26f, 0.36f, 0.16f, 0.16f));
        // the pole: leaning forward over the bow, lantern hung off its tip
        f.poleRot = new Quaternionf().rotateZ((float) Math.toRadians(38));
        f.poleRest = new Vector3f(feet).add(-0.7f, 0.15f, -0.05f);
        f.pole = rig.addBlock(Material.STRIPPED_DARK_OAK_LOG.createBlockData(), f.poleRest,
                new Vector3f(0.1f, 3.4f, 0.1f), f.poleRot);
        Vector3f tip = new Vector3f(0f, 3.4f, 0f);
        f.poleRot.transform(tip);
        f.lanternRest = new Vector3f(f.poleRest).add(tip).add(-0.25f, -0.95f, -0.25f);
        f.lantern = rig.addBlock(Material.LANTERN.createBlockData("[hanging=true]"), f.lanternRest,
                new Vector3f(0.5f, 0.5f, 0.5f), none);
        return f;
    }

    private static Display box(BlockRig rig, BlockData data, Vector3f feet, float x, float y, float z, float sx, float sy, float sz) {
        return rig.addBlock(data, new Vector3f(feet).add(x, y, z), new Vector3f(sx, sy, sz), new Quaternionf());
    }
}
