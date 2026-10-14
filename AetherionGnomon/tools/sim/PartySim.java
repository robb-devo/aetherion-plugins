import de.aetherion.gnomon.ai.*;
import de.aetherion.gnomon.geom.Zone;
import java.util.*;
public class PartySim {
  public static void main(String[] a){
    Brain b = new Brain(new BrainConfig(40,70,0.35,0.55,0.25, 20, 4,6,6,4,20, 0.45, 0.12,0.45, 5,14, 8, 4, 5.5,13, 8,1.6, 7L));
    UUID robb = UUID.randomUUID(), mate = UUID.randomUUID();
    b.join(robb,"Robb"); b.join(mate,"Mate"); b.start(0,0,0); b.setHub(0,0);
    long t=0; double ang=0; Map<String,Integer> focusTime=new HashMap<>(); Map<String,Integer> picks=new TreeMap<>();
    for (int i=0;i<3000;i++){ t++; b.tick(t);
      if (t%2==0){
        // Robb: melee hugger under the hub, hits a lot
        b.observe(robb, 2*Math.cos(t*0.01), 64, 2*Math.sin(t*0.01), true);
        // Mate: kiter orbiting CCW at r=15
        ang -= 0.012; if (t < 2200) b.observe(mate, 15*Math.cos(ang), 64, 15*Math.sin(ang), true);
      }
      if (t%15==0) b.onPlayerHit(robb, true, Brain.Opening.NONE, 0, 40);
      if (t%60==0) b.onPlayerHit(mate, false, Brain.Opening.NONE, 0, 15);
      if (t==2200) b.leave(mate);
      if (t%40==0){
        Decision d = b.decide(Moves.defaults(), new Brain.Context(5, 0.5, 0.5, s->true)); b.commit(d);
        picks.merge(d.id()+(d.exploit()!=null?"["+d.exploit()+"]":""),1,Integer::sum);
      }
      UUID f=b.focus(); if (f!=null) focusTime.merge(b.profile(f).name(),1,Integer::sum);
      b.drainTells();
    }
    System.out.println("focus ticks: "+focusTime);
    System.out.println("Robb: "+b.summary(robb)+"  | Mate: "+b.summary(mate));
    System.out.println("picks: "+picks);
    System.out.println("final posture "+b.posture()+" focus "+(b.focus()==null?"-":b.profile(b.focus()).name()));
  }
}
