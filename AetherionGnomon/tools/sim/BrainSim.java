import de.aetherion.gnomon.ai.*;
import de.aetherion.gnomon.geom.Zone;
import java.util.*;

public class BrainSim {
    static long t = 0;
    public static void main(String[] a) {
        Brain b = new Brain(new BrainConfig(40,70,0.35,0.55,0.25, 20, 4,6,6,4,20, 0.45, 0.0,0.45, 5,14, 8, 4, 5.5,13, 8,1.6, 42L));
        UUID p = UUID.randomUUID();
        b.join(p, "Robb");
        b.start(0, 0, 0);
        b.setHub(0, 0);
        double px = 0, pz = 10; // south of hub
        int dodgeSide = -1; // always left
        List<MoveSpec> moves = Moves.defaults();
        for (int round = 0; round < 40; round++) {
            if (round == 22) { dodgeSide = +1; System.out.println("---- player switches to RIGHT ----"); }
            // decide
            Brain.Context ctx = new Brain.Context(10, 0, 0, s -> true);
            Decision d = b.decide(moves, ctx);
            b.commit(d);
            // threat: stomp circle; if exploit SIDE, the shadow is placed at predicted side
            double cx = px, cz = pz;
            if (d.exploit() == Read.SIDE) {
                // facing hub from player: f=(0,-1) (north); right = (-fz, fx) = (1,0) east
                double fx = 0 - px, fz = 0 - pz; double len = Math.hypot(fx,fz); fx/=len; fz/=len;
                double rx = -fz, rz = fx;
                cx = px + d.sign() * 3.0 * rx; cz = pz + d.sign() * 3.0 * rz;
            }
            Zone z = new Zone.Circle(cx, cz, 2.5);
            b.beginThreat("s"+round, z, t + 24, d.exploit(), d.sign(), d.exploit()!=null? p : null);
            // player sim: dodges at ~60% of windup
            double sx = px, sz = pz;
            for (int k = 0; k < 24; k += 2) {
                t += 2; b.tick(t);
                if (k >= 14) {
                    double fx = 0 - sx, fz = 0 - sz; double len = Math.hypot(fx,fz); fx/=len; fz/=len;
                    double rx = -fz, rz = fx;
                    px = sx + dodgeSide * 4.0 * rx * Math.min(1,(k-12)/8.0);
                    pz = sz + dodgeSide * 4.0 * rz * Math.min(1,(k-12)/8.0);
                }
                b.observe(p, px, 64, pz, true);
            }
            boolean hit = z.contains(px, pz);
            Set<UUID> out = b.resolveThreat("s"+round, hit ? Set.of(p) : Set.of());
            for (Tell tl : b.drainTells()) System.out.println("   TELL " + tl);
            System.out.printf("r%02d t=%d %-12s exploit=%s sign=%d hit=%s outplayed=%s posture=%s | %s%n", round, t, d.id(), d.exploit(), d.sign(), hit, !out.isEmpty(), b.posture(), b.summary(p));
            // walk back to start spot slowly (not orbiting much)
            for (int k=0;k<20;k+=2){ t+=2; b.tick(t); px += (0 - px)*0.25; pz += (10 - pz)*0.25; b.observe(p, px, 64, pz, true);} 
            px = 0; pz = 10; t+=2; b.tick(t); b.observe(p, px, 64, pz, true);
        }
        System.out.println("LOG:"); b.log().forEach(System.out::println);
    }
}
