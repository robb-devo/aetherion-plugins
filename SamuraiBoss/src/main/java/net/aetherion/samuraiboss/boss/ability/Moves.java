package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.SamuraiSkins;
import net.aetherion.samuraiboss.render.fx.Afterimage;
import net.aetherion.samuraiboss.render.fx.CutSheet;
import net.aetherion.samuraiboss.render.fx.FxContext;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Wiederverwendbare Bewegungs-Effekte (Nachbild-Spuren, Kreuzschnitte …). */
public final class Moves {

    private Moves() {
    }

    /** Nachbilder entlang einer Strecke – die aktuelle Pose, an Zwischenpunkte versetzt. */
    public static void trail(Fight f, Location from, Location to, int count, int hold, int fade, boolean shadow) {
        List<ModelInstance.PartState> parts = f.boss().model().snapshot(c -> c.lod <= 1);
        if (parts.isEmpty()) return;
        Location anchor = parts.get(0).origin();
        for (int i = 0; i < count; i++) {
            double u = count == 1 ? 0 : i / (double) count;
            Location p = from.clone().add(to.clone().subtract(from).toVector().multiply(u));
            Vector off = p.toVector().subtract(anchor.toVector());
            List<ModelInstance.PartState> moved = new ArrayList<>(parts.size());
            for (ModelInstance.PartState ps : parts) {
                Location o = ps.origin().clone().add(off);
                moved.add(new ModelInstance.PartState(ps.def(), o, ps.transform(), ps.block(), ps.item()));
            }
            Vector d = to.toVector().subtract(from.toVector()).normalize().multiply(-0.4);
            f.fx().fx.add(new Afterimage(f.fx(), moved,
                    shadow ? Palette.BLACK_GLASS : Palette.PINK_GLASS,
                    shadow ? Palette.PURPLE_GLASS : Palette.WHITE_GLASS,
                    shadow ? SamuraiSkins.SHADOW_GLOW : null,
                    hold + i * 2, fade, new Vector3f((float) d.getX(), 0.15f, (float) d.getZ())));
        }
    }

    /** Ein einzelnes Nachbild der aktuellen Pose (bleibt 'hold' Ticks stehen). */
    public static Afterimage ghost(Fight f, int hold, int fade, Color glow) {
        List<ModelInstance.PartState> parts = f.boss().model().snapshot(c -> c.lod <= 1);
        return f.fx().fx.add(new Afterimage(f.fx(), parts, Palette.PINK_GLASS, Palette.WHITE_GLASS, glow, hold, fade,
                new Vector3f(0, 0.3f, 0)));
    }

    /** Kreuzschnitt (X) auf Brusthöhe an einem Opfer – der "verzögerte" Iai-Treffer. */
    public static void crossCut(Fight f, Location at) {
        FxContext c = f.fx();
        Location base = at.clone().add(0, 0.2, 0);
        float yaw = Rand.f() * 360f;
        Vector d1 = MathUtil.dirOfYaw(yaw).multiply(1.3).setY(1.3);
        Vector d2 = MathUtil.dirOfYaw(yaw + 90).multiply(1.3).setY(-1.3);
        c.fx.add(new CutSheet(c, base.clone().subtract(d1.clone().multiply(0.5)).add(0, 0.7, 0),
                base.clone().add(d1.clone().multiply(0.5)).add(0, 0.7, 0), 0.25f, 9, Palette.LIGHT, Palette.PINK_GLASS, false));
        c.fx.add(new CutSheet(c, base.clone().subtract(d2.clone().multiply(0.5)).add(0, 0.9, 0),
                base.clone().add(d2.clone().multiply(0.5)).add(0, 0.9, 0), 0.25f, 9, Palette.LIGHT, Palette.PINK_GLASS, false));
        c.fx.add(new PetalBurst(c, at.clone().add(0, 1.1, 0), 9, 1.4f, 0.3f, 22));
    }
}
