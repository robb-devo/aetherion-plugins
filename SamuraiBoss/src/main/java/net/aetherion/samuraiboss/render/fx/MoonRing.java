package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Der rote Mond aus Blüten (Phase 3): eine riesige, karmesinrote Scheibe hoch über dem Hof, die
 * immer zum Betrachter zeigt (Billboard), umkreist von einem Ring aus Blütenblättern.
 */
public final class MoonRing extends BaseFx {

    private static final class Orb {
        Display d;
        float a, r, y, s;
    }

    private final FxContext c;
    private final List<Orb> orbs = new ArrayList<>();
    private Display moon, halo;
    private int endAt = Integer.MAX_VALUE;
    private final float radius;

    public MoonRing(FxContext c, Location center, float radius, int petals) {
        super(c.fx);
        this.c = c;
        this.radius = radius;
        if (c.pack) {
            moon = item(center, PackItems.item(c.settings, "moon"));
            if (moon != null) {
                moon.setBillboard(Display.Billboard.CENTER);
                moon.setBrightness(new Display.Brightness(15, 15));
                moon.setViewRange(8f);
                DisplayFactory.snap(moon, Tf.centered(new Vector3f(), new Quaternionf(), 0.1f));
                lerpAt(1, moon, Tf.centered(new Vector3f(), new Quaternionf(), radius * 0.9f), 60);
            }
        } else {
            halo = disc(center, 0xE07090, (byte) 70, radius * 3.4f);
            moon = disc(center, 0xB3122E, (byte) -1, radius * 2.6f);
        }
        int n = c.count(petals);
        for (int i = 0; i < n; i++) {
            Orb o = new Orb();
            o.d = c.petal(this, center, i);
            if (o.d == null) break;
            o.d.setViewRange(6f);
            o.d.setBrightness(new Display.Brightness(15, 15));
            o.a = (float) (i * Math.PI * 2 / n) + Rand.signed() * 0.05f;
            o.r = radius * (0.9f + Rand.f() * 0.3f);
            o.y = Rand.signed() * 1.2f;
            o.s = 0.35f + Rand.f() * 0.3f;
            DisplayFactory.snap(o.d, c.petalTf(new Vector3f(0, -radius * 0.8f, 0), FxContext.randomRot(), 0f));
            orbs.add(o);
        }
    }

    private Display disc(Location center, int rgb, byte opacity, float scale) {
        TextDisplay d = text(center);
        if (d == null) return null;
        d.text(Component.text("●").color(TextColor.color(rgb)));
        d.setTextOpacity(opacity);
        d.setBillboard(Display.Billboard.CENTER);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(8f);
        DisplayFactory.snap(d, Tf.centered(new Vector3f(0, -0.12f * scale * 0.1f, 0), new Quaternionf(), 0.1f));
        lerpAt(1, d, Tf.centered(new Vector3f(0, -0.12f * scale, 0), new Quaternionf(), scale), 60);
        return d;
    }

    public void fadeOut() {
        if (moon != null) lerpAt(age, moon, Tf.centered(new Vector3f(), new Quaternionf(), 0f), 30);
        if (halo != null) lerpAt(age, halo, Tf.centered(new Vector3f(), new Quaternionf(), 0f), 30);
        for (Orb o : orbs) {
            Vector3f down = new Vector3f((float) Math.cos(o.a) * o.r * 1.4f, -radius * 2f, (float) Math.sin(o.a) * o.r * 1.4f);
            lerpAt(age, o.d, c.petalTf(down, FxContext.randomRot(), 0f), 30 + Rand.range(0, 20));
        }
        endAt = age + 52;
    }

    @Override
    protected boolean update(int age) {
        if (age >= endAt) return false;
        if (endAt != Integer.MAX_VALUE) return true;
        if (age % 20 == 1) {
            for (Orb o : orbs) {
                o.a += 0.16f;
                Vector3f p = new Vector3f((float) Math.cos(o.a) * o.r, o.y + (float) Math.sin(o.a * 3 + age * 0.02f) * 0.6f,
                        (float) Math.sin(o.a) * o.r);
                DisplayFactory.lerp(o.d, c.petalTf(p, FxContext.randomRot(), o.s), age < 5 ? 40 : 20);
            }
        }
        return true;
    }
}
