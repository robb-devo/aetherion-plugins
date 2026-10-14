package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.util.Rand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ein riesiges, leuchtendes Kanji in der Luft (z. B. 嵐 beim Phasenwechsel). Es erscheint mit
 * einem Nachschwingen, glüht (zweite, größere halbtransparente Kopie als "Bloom") und
 * <b>zerbricht</b> am Ende. Mit Resource Pack zerfällt die Pinsel-Glyphe in echte Bruchstücke.
 *
 * <p>Die Größe wird in <b>Blöcken</b> angegeben (Höhe des Zeichens); die passende
 * TextDisplay-Skalierung rechnet {@link FxContext#kanjiScale} aus.</p>
 */
public final class KanjiFx extends BaseFx {

    public enum Ending {SHATTER, FADE, RISE}

    /** Kanji, für die das Resource Pack Bruchstück-Glyphen enthält (6 Stück ab U+E100 + i*8). */
    public static final String FRAGMENT_KANJI = "剣嵐散華伏侍斬静閃桜勝死影無";
    public static final int FRAGMENTS = 6;

    private final FxContext c;
    private final Location at;
    private final String text;
    private final int rgb;
    /** Höhe in Blöcken. */
    private final float blocks;
    /** TextDisplay-Skalierung für diese Höhe. */
    private final float ds;
    private final Quaternionf rot;
    private final boolean billboard;
    private TextDisplay main;
    private TextDisplay glow;
    private int endAt;
    private boolean ending;

    /**
     * @param blocks Höhe des Zeichens in Blöcken
     * @param hold   Ticks bis zum automatischen Ende (nach dem Erscheinen); -1 = manuell über {@link #end}
     */
    public KanjiFx(FxContext c, Location at, String text, int rgb, float blocks, Quaternionf rot, boolean billboard,
                   int hold, Ending ending) {
        super(c.fx);
        this.c = c;
        this.at = at;
        this.text = text;
        this.rgb = rgb;
        this.blocks = blocks;
        this.ds = c.kanjiScale(blocks);
        this.rot = rot == null ? new Quaternionf() : rot;
        this.billboard = billboard;
        glow = text(at);
        main = text(at);
        setup(glow, c.kanji(text, 0xFF7FA8), (byte) 110);
        setup(main, c.kanji(text, rgb), (byte) -1);
        snapAt(0, glow, tf(ds * 0.25f, -0.03f));
        snapAt(0, main, tf(ds * 0.2f, 0f));
        lerpAt(1, glow, tf(ds * 1.35f, -0.03f), 4);
        lerpAt(1, main, tf(ds * 1.15f, 0f), 4);
        lerpAt(5, glow, tf(ds * 1.12f, -0.03f), 5);
        lerpAt(5, main, tf(ds, 0f), 5);
        endAt = Integer.MAX_VALUE;
        if (hold >= 0) at(10 + hold, () -> end(ending));
    }

    private void setup(TextDisplay d, Component comp, byte opacity) {
        if (d == null) return;
        d.text(comp);
        d.setTextOpacity(opacity);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(4f);
        if (billboard) d.setBillboard(Display.Billboard.CENTER);
    }

    /** Transformation für TextDisplay-Skalierung s (Zeichen um den Mittelpunkt zentriert). */
    private Transformation tf(float s, float zOff) {
        Vector3f off = new Vector3f(0, c.kanjiCenterOffset(s), zOff);
        rot.transform(off);
        return new Transformation(off, new Quaternionf(rot), new Vector3f(s, s, s), new Quaternionf());
    }

    /** Beenden (falls hold < 0 übergeben wurde, muss das der Aufrufer tun). */
    public void end(Ending how) {
        if (ending) return;
        ending = true;
        int now = age;
        switch (how) {
            case SHATTER -> {
                c.sfx.play(at, net.aetherion.samuraiboss.audio.Sfx.Id.SHATTER);
                int idx = FRAGMENT_KANJI.indexOf(text);
                if (c.pack && idx >= 0 && text.length() == 1) {
                    // echte Bruchstücke der Pinsel-Glyphe
                    for (int i = 0; i < FRAGMENTS; i++) {
                        TextDisplay f = text(at);
                        if (f == null) break;
                        char ch = (char) (0xE100 + idx * 8 + i);
                        setup(f, Component.text(String.valueOf(ch)).color(TextColor.color(rgb)).font(Key.key(c.settings.packFont)), (byte) -1);
                        snapAt(now, f, tf(ds, 0.01f));
                        Vector3f fly = new Vector3f(Rand.signed() * blocks * 0.5f, Rand.signed() * blocks * 0.4f, Rand.signed() * 0.6f);
                        Quaternionf spin = new Quaternionf(rot).rotateZ(Rand.signed() * 1.2f).rotateX(Rand.signed() * 0.8f);
                        Vector3f base = rot.transform(new Vector3f(0, c.kanjiCenterOffset(ds), 0));
                        lerpAt(now + 1, f, new Transformation(new Vector3f(base).add(fly), spin, new Vector3f(ds, ds, ds), new Quaternionf()), 8);
                        lerpAt(now + 9, f, new Transformation(new Vector3f(base).add(fly.mul(2.2f)).add(0, -blocks * 0.4f, 0), spin,
                                new Vector3f(), new Quaternionf()), 10);
                        freeAt(now + 20, f);
                    }
                    snapAt(now, main, tf(0f, 0f));
                    lerpAt(now, glow, tf(ds * 1.8f, -0.03f), 3);
                    lerpAt(now + 3, glow, tf(0f, -0.03f), 4);
                } else {
                    lerpAt(now, main, tf(ds * 1.25f, 0f), 2);
                    lerpAt(now + 2, main, tf(0f, 0f), 3);
                    lerpAt(now, glow, tf(ds * 1.7f, -0.03f), 3);
                    lerpAt(now + 3, glow, tf(0f, -0.03f), 3);
                    c.fx.add(new Shards(c, at.clone(), 10 + Math.round(blocks * 2), 0.25f + blocks * 0.12f,
                            Math.min(0.45f, 0.08f + blocks * 0.05f), 18, Palette.LIGHT, Palette.PINK_GLASS, Palette.WHITE_GLASS));
                }
                endAt = now + 22;
            }
            case FADE -> {
                lerpAt(now, main, tf(ds * 0.6f, 0f), 8);
                lerpAt(now + 8, main, tf(0f, 0f), 4);
                lerpAt(now, glow, tf(0f, -0.03f), 8);
                endAt = now + 14;
            }
            case RISE -> {
                Vector3f up = new Vector3f(0, blocks * 0.6f, 0);
                Transformation m = tf(ds * 0.8f, 0f);
                lerpAt(now, main, new Transformation(new Vector3f(m.getTranslation()).add(up), m.getLeftRotation(), new Vector3f(), new Quaternionf()), 14);
                lerpAt(now, glow, tf(0f, -0.03f), 10);
                endAt = now + 16;
            }
        }
    }

    @Override
    protected boolean update(int age) {
        return age < endAt;
    }
}
