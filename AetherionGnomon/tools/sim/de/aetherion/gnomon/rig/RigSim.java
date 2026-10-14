package de.aetherion.gnomon.rig;

import de.aetherion.gnomon.render.Part;
import de.aetherion.gnomon.render.Parts;
import org.bukkit.Location;
import org.joml.*;
import java.lang.Math;

public class RigSim {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }
    public static void main(String[] a) {
        // 1) Bone transform: unit cube bottom-center -> A, top-center -> B
        Vector3d anchor = new Vector3d(100.5, 64, 200.5);
        Vector3d A = new Vector3d(103, 70, 198), B = new Vector3d(96, 64.2, 205);
        double len = A.distance(B);
        Vector3d d = new Vector3d(B).sub(A).normalize();
        Quaternionf q = new Quaternionf().rotationTo(0,1,0,(float)d.x,(float)d.y,(float)d.z);
        Vector3d c = new Vector3d(A).add(B).mul(0.5);
        Vector3f T = Part.translation(anchor, c, q, 0.5, len, 0.5);
        Vector3f bottom = new Vector3f(0.25f, 0, 0.25f); q.transform(bottom); bottom.add(T);
        Vector3f top = new Vector3f(0.25f, (float)len, 0.25f); q.transform(top); top.add(T);
        Vector3d wb = new Vector3d(bottom.x + anchor.x, bottom.y + anchor.y, bottom.z + anchor.z);
        Vector3d wt = new Vector3d(top.x + anchor.x, top.y + anchor.y, top.z + anchor.z);
        check(wb.distance(A) < 1e-3, "bone bottom " + wb + " vs " + A);
        check(wt.distance(B) < 1e-3, "bone top " + wt + " vs " + B);
        // plate rotation: local +X maps to (cos a, 0, sin a)
        double ang = 1.1;
        Vector3f x = new Vector3f(1,0,0); new Quaternionf().rotationY((float)-ang).transform(x);
        check(Math.abs(x.x - Math.cos(ang)) < 1e-5 && Math.abs(x.z - Math.sin(ang)) < 1e-5, "plate axis " + x);

        // 2) IK: knee keeps both bone lengths when reachable
        Vector3d hip = new Vector3d(0, 12, 0), foot = new Vector3d(9, 1.5, 3), knee = new Vector3d();
        Leg.solve(hip, foot, 8.5, 9.5, new Vector3d(0.9, 0.85, 0.3), knee);
        check(Math.abs(knee.distance(hip) - 8.5) < 1e-6, "thigh len " + knee.distance(hip));
        check(Math.abs(knee.distance(foot) - 9.5) < 1e-6, "shin len " + knee.distance(foot));
        check(knee.y > Math.min(hip.y, foot.y), "knee bends upward/outward " + knee);

        // 3) Headless tripod: walk across a 30-block dial, turn, check reach, no NaN, feet planted on floor
        Parts parts = new Parts(new Location(null, 0, 64, 0), 1.6f);
        Tripod.Spec spec = new Tripod.Spec(13, 1.3, 8.5, 9.5, 6.5, 3.2, 12, 3.5, 0.17, 2.2, 3.0, 0.075, 0.985, 1);
        Tripod t = new Tripod(spec, parts, 64, 0, 0, -Math.PI/2);
        int steps = 0;
        double maxOver = 0;
        t.onStepStart((to, dur) -> {});
        int[] plants = {0};
        t.onAutoPlant(f -> plants[0]++);
        t.moveTo(14, 9);
        for (int i = 0; i < 400; i++) {
            if (i == 200) t.moveTo(-10, -12);
            t.face(0, 30);
            t.tick();
            for (int k = 0; k < 3; k++) {
                Vector3d[] j = t.legJoints(k);
                double reach = j[0].distance(j[2]);
                maxOver = Math.max(maxOver, reach - (spec.thigh() + spec.shin()));
                check(Double.isFinite(j[1].x) && Double.isFinite(j[1].y), "knee NaN at " + i);
                if (t.leg(k).planted()) check(Math.abs(t.leg(k).foot().y - 64) < 1e-6, "planted foot off floor");
            }
            Vector3d b = t.bob();
            check(Double.isFinite(b.x) && b.y >= 64.74, "bob below floor/NaN " + b + " at " + i);
        }
        System.out.printf("hub end=(%.2f,%.2f) goal=(-10,-12) plants=%d maxOverstretch=%.2f bobY=%.2f%n", t.hubX(), t.hubZ(), plants[0], maxOver, t.bob().y);
        check(Math.hypot(t.hubX() + 10, t.hubZ() + 12) < 1.0, "hub reached goal");
        check(plants[0] >= 6, "gait stepped (" + plants[0] + ")");
        check(maxOver < 0.8, "legs overstretched by " + maxOver);
        // rest bob height ~ floor + 3
        for (int i = 0; i < 200; i++) t.tick();
        System.out.printf("rest bob y-floor=%.2f (want ~3.0)%n", t.bob().y - 64);
        check(Math.abs(t.bob().y - 64 - 3.0) < 0.6, "bob rest height");

        // 4) Plumb drop lands under the hub (within the 4.5 telegraph)
        t.crouch(2.2);
        t.pendulum().reel(t.chainRest() - 2.6, 0.14);
        for (int i = 0; i < 30; i++) t.tick();
        Vector3d h = t.hub();
        boolean[] landed = {false};
        double[] at = new double[2];
        t.pendulum().drop(h.y - 0.8 - 64 + 1.0, -0.9, () -> { landed[0] = true; at[0] = t.bob().x; at[1] = t.bob().z; });
        int ticks = 0;
        while (!landed[0] && ticks < 60) { t.tick(); ticks++; }
        System.out.printf("drop landed=%s after %d ticks, offset=%.2f from hub%n", landed[0], ticks, Math.hypot(at[0]-h.x, at[1]-h.z));
        check(landed[0] && ticks <= 20, "drop lands fast");
        check(Math.hypot(at[0]-h.x, at[1]-h.z) < 2.0, "drop lands inside shadow");

        // 5) Reach clamp: a far stomp target gets pulled inside reach
        Vector3d far = t.clampToReach(t.hubX() + 40, t.hubZ());
        check(Math.hypot(far.x - t.hubX(), far.z - t.hubZ()) <= t.reach(), "clamp");
        System.out.printf("reach=%.2f%n", t.reach());
        System.out.println(fails == 0 ? "ALL RIG CHECKS PASSED" : (fails + " FAILURES"));
    }
}
