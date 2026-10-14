package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.audio.Music;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.render.fx.Afterimage;
import net.aetherion.samuraiboss.render.fx.CutPlane;
import net.aetherion.samuraiboss.render.fx.FxContext;
import net.aetherion.samuraiboss.render.fx.GhostBlade;
import net.aetherion.samuraiboss.render.fx.KanjiFx;
import net.aetherion.samuraiboss.render.fx.MoonRing;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.PetalField;
import net.aetherion.samuraiboss.render.fx.SealCircle;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.Shockwave;
import net.aetherion.samuraiboss.render.fx.SlashArc;
import net.aetherion.samuraiboss.render.fx.SpiralConverge;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.util.Hits;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import net.aetherion.samuraiboss.util.Sequence;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Zwischensequenzen – jede ist eine {@link Sequence} (Tick → Aktion), die der Kampf
 * abspielt. Alle Zeiten in Ticks.
 *
 * <ul>
 *   <li><b>intro</b> – Glocke, Blütenspirale, Siegel, Lichtsäule, er erscheint in Seiza,
 *       verneigt sich, erhebt sich; Titel; Leiste füllt sich (~12 s)</li>
 *   <li><b>toArashi</b> – DIE ZEIT STEHT STILL: Blüten erstarren in der Luft, nur er bewegt
 *       sich und zieht langsam; 嵐 erscheint und zerspringt – Sturm, Regen, Helm und Banner
 *       fliegen davon, die Shoji zerreißen</li>
 *   <li><b>toSange</b> – Stille. Er kniet nieder, die Blüten sinken zu Boden, Rüstungsplatten
 *       fallen ab, der Hof zerbricht; er erhebt sich als Geist, ein roter Mond geht auf</li>
 *   <li><b>ultimate</b> – 天地一閃: Sprung in den Himmel, Zeitstillstand, absurd langsames
 *       Ziehen, die Schnittebene über dem ganzen Dojo – wer sich verneigt (schleicht), überlebt</li>
 *   <li><b>death</b> – Todesgedicht, Auflösung in Blüten, der Baum erblüht neu</li>
 * </ul>
 */
final class Cinematics {

    private final Fight f;

    Cinematics(Fight f) {
        this.f = f;
    }

    private SamuraiBoss boss() {
        return f.boss();
    }

    private FxContext fx() {
        return f.fx();
    }

    private Sfx sfx() {
        return f.sfx();
    }

    private List<Player> ps() {
        return new ArrayList<>(f.participants());
    }

    private void title(String main, String sub, int in, int stay, int out) {
        f.atmo().title(ps(), Fight.mm(main), Fight.mm(sub), in, stay, out);
    }

    /** Dialogzeile: Japanisch groß als Untertitel, die Übersetzung darunter in der Actionbar. */
    private void line(String jp, String de) {
        title("", "<#ffe3ea>" + jp + "</#ffe3ea>", 8, 50, 12);
        if (!de.isEmpty()) f.atmo().actionBar(ps(), Fight.mm("<gray>" + de + "</gray>"));
    }

    private void heartbeat() {
        for (Player p : ps()) sfx().playFor(p, Sfx.Id.HEARTBEAT, 1f, 1f);
    }

    private Location center() {
        return f.arena().center();
    }

    // =================================================================== Einleitung

    Sequence intro() {
        SamuraiBoss b = boss();
        Location at = b.location();
        b.setAutoBase(false);
        b.base(b.anims.seizaIdle, 0);
        b.model().setAllHidden(true);
        final SealCircle[] seal = new SealCircle[1];
        return new Sequence()
                .at(0, () -> {
                    f.arena().deco.strikeBell();
                    sfx().play(f.arena().point("bell"), Sfx.Id.BELL, 1.4f, 1f);
                    f.atmo().timeTo(ps(), 12800, 120);
                    f.setBarName(Fight.mm("<gray>…</gray>"), BossBar.Color.PINK);
                })
                .at(18, () -> sfx().play(f.arena().point("bell"), Sfx.Id.BELL, 0.7f, 0.94f))
                .at(24, () -> {
                    fx().fx.add(new SpiralConverge(fx(), at, 46, 7.5f, 2.6f, 50, false));
                    seal[0] = fx().fx.add(new SealCircle(fx(), f.ground(at), 4.6f, "桜木刃斎散華剣聖千年", 0xFF9EC0, 0.02f));
                    sfx().play(at, Sfx.Id.SUMMON);
                })
                .every(24, 70, 20, rel -> heartbeat())
                .at(46, () -> fx().fx.add(new SpiralConverge.Pillar(fx(), f.ground(at), 14f, 1.3f, 6, 22, 14)))
                .at(76, () -> {
                    b.model().setAllHidden(false);
                    b.flash();
                    fx().fx.add(new PetalBurst(fx(), at.clone().add(0, 1.2, 0), 30, 2.6f, 0.4f, 34));
                    fx().fx.add(new Shockwave(fx(), f.ground(at), 0.5f, 7f, 12, 0.3f, Palette.LIGHT, Palette.PINK_GLASS));
                    sfx().play(at, Sfx.Id.FLASH);
                    sfx().play(at, Sfx.Id.CHIME, 1f, 0.8f);
                    f.atmo().shakeNear(ps(), at, 0.4f, 6, 30);
                })
                .at(92, () -> line("千の春を、この樹の下で待った。", "„Tausend Frühlinge habe ich unter diesem Baum gewacht …“"))
                .at(140, () -> b.play(b.anims.seizaBow, 6, 6, 1f, ev -> {
                }))
                .at(150, () -> line("刃を抜け。", "„Zieht eure Klingen – zeigt mir, ob ihr der Blüten würdig seid.“"))
                .at(190, () -> {
                    b.play(b.anims.riseFromSeiza, 6, 8, 1f, ev -> {
                    });
                    if (seal[0] != null) seal[0].fadeOut(20);
                })
                .at(212, () -> {
                    b.base(b.anims.idleSheathed, 10);
                    title(f.settings().msg("boss-name", "Sakuragi Jinsai"),
                            f.settings().msg("boss-title", "Sword Saint of Falling Blossoms"),
                            10, 60, 20);
                    sfx().play(at, Sfx.Id.TAIKO, 1.4f, 1f);
                    f.music().play(Music.SEI);
                    f.refreshBarName();
                })
                .during(212, 252, rel -> f.setBarOverride(Math.min(1f, rel / 40f)))
                .at(236, () -> fx().fx.add(new KanjiFx(fx(), b.eye().add(MathUtil.dirOfYaw(b.yaw()).multiply(-1.5)).add(0, 1.6, 0),
                        "静", 0xFFFFFF, 2.6f, null, true, 24, KanjiFx.Ending.FADE)))
                .at(254, () -> f.beginCombat(24));
    }

    // =================================================================== 1 → 2: 嵐

    Sequence toArashi() {
        SamuraiBoss b = boss();
        final KanjiFx[] kanji = new KanjiFx[1];
        final boolean[] resumed = {false};
        Runnable resume = () -> {
            if (resumed[0]) return;
            resumed[0] = true;
            Location at = b.location();
            if (kanji[0] != null) kanji[0].end(KanjiFx.Ending.SHATTER);
            fx().fx.add(new SlashArc(fx(), at.clone().add(0, 1.3, 0), SlashArc.plane(b.yaw(), 0), 9f, 0f, 360f, 0.9f, 3, 10,
                    Palette.LIGHT, Palette.PINK_GLASS));
            fx().fx.add(new Shockwave(fx(), f.ground(at), 1f, 14f, 14, 0.5f, Palette.LIGHT, Palette.PINK_GLASS));
            sfx().play(at, Sfx.Id.TIMERESUME);
            sfx().play(at, Sfx.Id.THUNDER);
            sfx().play(at, Sfx.Id.WIND, 1.5f, 0.8f);
            f.atmo().shake(ps(), 0.8f, 12);
            PetalField pf = f.petals();
            if (pf != null) {
                pf.burst(f.rel(at), 7f, PetalField.Mode.DRIFT);
                pf.storm(f.rel(center()), 13f);
            }
            // Helm und Rückenbanner reißt der Sturm davon
            Vector back = MathUtil.dirOfYaw(b.yaw() + 180f);
            for (String tag : new String[]{SamuraiModel.T_HELMET, SamuraiModel.T_BANNER}) {
                for (String id : b.model().partIdsWithTag(tag)) {
                    ModelInstance.PartState ps = b.model().detach(id);
                    Vector3f v = new Vector3f((float) back.getX(), 0, (float) back.getZ()).mul(2.5f + Rand.f() * 2f)
                            .add(Rand.signed(), 1.2f + Rand.f(), Rand.signed());
                    f.destruction().flyOff(ps, v, true, 1_000_000);
                }
            }
            b.setSkin(b.skins.storm);
            b.model().reattachAll();
            f.destruction().stage1();
            f.atmo().weather(ps(), WeatherType.DOWNFALL);
            f.atmo().timeTo(ps(), 13600, 60);
        };
        return new Sequence()
                .at(0, () -> {
                    b.stopWalking();
                    b.setAutoBase(false);
                    b.animator().setTimeScale(0f);
                    if (f.petals() != null) f.petals().freeze();
                    for (Player p : ps()) sfx().playFor(p, Sfx.Id.TIMESTOP, 1f, 1f);
                    f.atmo().slow(ps(), 54, 3);
                    f.music().stop(0);
                    f.setBarName(Fight.mm("<gray>…</gray>"), null);
                })
                .at(20, () -> {
                    b.animator().setTimeScale(1f);
                    b.play(b.anims.drawSlow, 6, 6, 0.85f, ev -> {
                        if (ev.equals("cut")) resume.run();
                    });
                    heartbeat();
                })
                .at(26, () -> {
                    Location k = b.eye().add(MathUtil.dirOfYaw(b.yaw()).multiply(2.4)).add(0, 1.2, 0);
                    kanji[0] = fx().fx.add(new KanjiFx(fx(), k, "嵐", 0xFFFFFF, 4.5f, null, true, -1, KanjiFx.Ending.SHATTER));
                    sfx().play(k, Sfx.Id.KANJI);
                })
                .at(42, this::heartbeat)
                .at(66, resume)
                .at(70, () -> {
                    f.enterPhase(Phase.ARASHI);
                    title("<#ff6f91>嵐</#ff6f91>", "<gray>" + f.phaseLabel() + "</gray>", 6, 40, 14);
                    f.music().play(Music.ARASHI);
                    b.setWind(0.8f);
                })
                .at(96, () -> {
                    if (f.petals() != null && f.petals().mode() == PetalField.Mode.STORM) f.petals().setMode(PetalField.Mode.DRIFT);
                    f.beginCombat(26);
                });
    }

    // =================================================================== 2 → 3: 散華

    Sequence toSange() {
        SamuraiBoss b = boss();
        return new Sequence()
                .at(0, () -> {
                    b.stopWalking();
                    b.setAutoBase(false);
                    f.music().stop(30);
                    f.atmo().weather(ps(), null);
                    b.setWind(0.05f);
                    if (f.petals() != null) f.petals().setMode(PetalField.Mode.SETTLE);
                    if (b.drawn()) b.play(b.anims.sheatheFromKamae, 6, 8, 0.9f, ev -> {
                    });
                    f.setBarName(Fight.mm("<gray>…</gray>"), null);
                })
                .at(34, () -> {
                    b.stopAction(16);
                    b.base(b.anims.seizaIdle, 20);
                    f.atmo().timeTo(ps(), 18000, 90);
                })
                .every(36, 120, 26, rel -> heartbeat())
                .at(48, () -> {
                    // Rüstungsplatten fallen ab
                    for (String id : b.model().partIdsWithTag(SamuraiModel.T_PLATE)) {
                        ModelInstance.PartState ps = b.model().detach(id);
                        Vector3f v = new Vector3f(Rand.signed() * 1.4f, 0.2f + Rand.f() * 0.4f, Rand.signed() * 1.4f);
                        f.destruction().flyOff(ps, v, true, 1_000_000);
                    }
                    sfx().play(b.location(), Sfx.Id.HURT, 1.2f, 0.7f);
                    sfx().play(b.location(), Sfx.Id.CLANG, 1.2f, 0.8f);
                })
                .at(58, () -> {
                    f.destruction().stage2();
                    f.atmo().shake(ps(), 0.5f, 10);
                })
                .at(74, () -> f.atmo().shake(ps(), 0.7f, 10))
                .at(100, () -> line("花は桜木、人は武士。", "„Unter den Blumen die Kirschblüte, unter den Menschen der Krieger.“"))
                .at(140, () -> b.play(b.anims.ghostRise, 8, 8, 1f, ev -> {
                }))
                .at(146, () -> {
                    b.model().reattachAll();
                    b.setSkin(b.skins.ghost);
                    b.flash();
                    b.setHover(0.6f);
                    sfx().play(b.location(), Sfx.Id.GHOST);
                    sfx().play(b.location(), Sfx.Id.RISE);
                    fx().fx.add(new PetalBurst(fx(), b.location().add(0, 1.2, 0), 26, 2f, 0.6f, 30));
                    f.moon = fx().fx.add(new MoonRing(fx(), f.arena().point("moon"), 5.5f, 30));
                    f.atmo().tint(0.35f);
                })
                .at(176, () -> {
                    fx().fx.add(new KanjiFx(fx(), b.eye().add(0, 2.6, 0), "散華", 0xFFB7C9, 3.4f, null, true, 26, KanjiFx.Ending.SHATTER));
                    sfx().play(b.location(), Sfx.Id.KANJI);
                    if (f.petals() != null) f.petals().setMode(PetalField.Mode.RISE);
                })
                .at(186, () -> {
                    f.enterPhase(Phase.SANGE);
                    title("<#ff8fab>散華</#ff8fab>", "<gray>" + f.phaseLabel() + "</gray>", 6, 40, 14);
                    f.music().play(Music.SANGE);
                })
                .at(200, () -> {
                    b.setAutoBase(true);
                    f.beginCombat(26);
                });
    }

    // =================================================================== Ultimate 天地一閃

    Sequence ultimate() {
        SamuraiBoss b = boss();
        Location ult = f.arena().point("ult");
        Location ground = f.ground(center());
        double cutY = f.arena().oy + 1.55;
        final CutPlane[] plane = new CutPlane[1];
        final int cutAt = 24 + 61;
        return new Sequence()
                .at(0, () -> {
                    b.stopWalking();
                    b.setAutoBase(false);
                    f.setBarName(Fight.mm(f.settings().msg("ultimate-bar", "天地一閃")), BossBar.Color.WHITE);
                    f.setBarOverride(1f);
                    f.music().stop(10);
                    b.face(ult);
                    b.play(b.anims.crouchLeap, 3, 3, 1f, ev -> {
                    });
                })
                .at(10, () -> {
                    b.setGravity(false);
                    b.setDrawn(false); // im Sprung zurück in die Saya – der langsame Zug beginnt aus der Scheide
                    b.stopAction(6);
                    b.base(b.anims.air, 6);
                    b.moveTo(ult, 12);
                    sfx().play(b.location(), Sfx.Id.WHOOSH, 1.4f, 0.8f);
                    sfx().play(b.location(), Sfx.Id.WIND, 1.2f, 0.7f);
                    fx().fx.add(new Shockwave(fx(), f.ground(b.location()), 0.5f, 6f, 10, 0.4f, Palette.LIGHT, Palette.PINK_GLASS));
                })
                .at(24, () -> {
                    if (f.petals() != null) f.petals().freeze();
                    for (Player p : ps()) sfx().playFor(p, Sfx.Id.TIMESTOP, 1.2f, 0.85f);
                    f.atmo().timeTo(ps(), 18000, 24);
                    f.atmo().tint(0.6f);
                    title("<white>天地一閃</white>", "<gray>Verneigt euch –</gray> <white>schleicht!</white>", 8, 70, 10);
                    Location pc = new Location(f.world(), f.arena().ox + 0.5, cutY, f.arena().oz + (net.aetherion.samuraiboss.arena.dojo.DojoBlueprint.WALL_N
                            + net.aetherion.samuraiboss.arena.dojo.DojoBlueprint.WALL_S) / 2.0 + 0.5);
                    float sx = net.aetherion.samuraiboss.arena.dojo.DojoBlueprint.WALL_X * 2 + 3;
                    float sz = net.aetherion.samuraiboss.arena.dojo.DojoBlueprint.WALL_S - net.aetherion.samuraiboss.arena.dojo.DojoBlueprint.WALL_N + 3;
                    plane[0] = fx().fx.add(new CutPlane(fx(), pc, sx, sz));
                    b.face(center());
                    b.play(b.anims.ultDraw, 6, 6, 1f, ev -> {
                    });
                })
                .every(28, cutAt - 4, 14, rel -> {
                    if (plane[0] != null) plane[0].pulse(0.4f + rel / 60f);
                    heartbeat();
                    int left = Math.max(0, (cutAt - 28 - rel) / 20 + 1);
                    f.atmo().actionBar(ps(), Fight.mm("<white>⚠</white> <gray>Schleichen!</gray> <white>" + left + "</white>"));
                })
                .during(24, cutAt, rel -> f.setBarOverride(1f - rel / (float) (cutAt - 24)))
                .at(cutAt - 3, () -> {
                    for (Player p : ps()) p.stopAllSounds();
                })
                .at(cutAt, () -> cut(plane[0], cutY, ult))
                .at(cutAt + 12, () -> {
                    if (f.petals() != null) f.petals().burst(f.rel(center()), 8f, PetalField.Mode.DRIFT);
                    sfx().play(center(), Sfx.Id.TIMERESUME);
                    f.atmo().timeTo(ps(), 17500, 40);
                })
                .at(cutAt + 26, () -> {
                    b.moveTo(ground.clone().add(0, 0.05, 0), 14);
                    sfx().play(b.location(), Sfx.Id.WHOOSH, 1f, 0.6f);
                })
                .at(cutAt + 40, () -> {
                    b.setGravity(true);
                    sfx().play(ground, Sfx.Id.LAND, 1.5f, 0.7f);
                    fx().fx.add(new Shockwave(fx(), ground, 0.5f, 9f, 14, 0.5f, Palette.LIGHT, Palette.PINK_GLASS));
                    f.atmo().shake(ps(), 0.6f, 8);
                    b.play(b.anims.kneelExhausted, 4, 6, 1f, ev -> {
                        if (ev.equals("plant")) sfx().play(b.location(), Sfx.Id.BLADE_HIT, 1.2f, 0.7f);
                    });
                })
                .at(cutAt + 48, () -> {
                    f.setBarOverride(-1f);
                    f.beginExhaustion();
                    f.music().play(Music.SANGE);
                    f.atmo().tint(0.25f);
                    title("", "<gray>Er ist erschöpft –</gray> <white>jetzt!</white>", 4, 40, 10);
                });
    }

    private void cut(CutPlane plane, double cutY, Location ult) {
        SamuraiBoss b = boss();
        Location c = center();
        if (plane != null) plane.flash();
        sfx().play(c, Sfx.Id.FLASH, 2f, 0.8f);
        sfx().play(c, Sfx.Id.BOOM, 2f, 0.7f);
        sfx().play(c, Sfx.Id.HEAVY, 2f, 0.6f);
        fx().fx.add(new KanjiFx(fx(), ult.clone().add(0, 3.5, 0), "一閃", 0xFFFFFF, 6f, null, true, 18, KanjiFx.Ending.SHATTER));
        // Wer steht, wird getroffen – wer sich verneigt (schleicht), bleibt unter der Ebene
        for (LivingEntity e : new ArrayList<>(f.targets())) {
            if (Hits.below(e, cutY)) {
                if (e instanceof Player p) {
                    p.sendMessage(Fight.mm(f.settings().msg("prefix", "") + f.settings().msg("bow-saved", "")));
                    sfx().playFor(p, Sfx.Id.CHIME, 1f, 1.2f);
                }
            } else {
                f.hurt(e, f.settings().damage("ultimate"), c, 1.3, 0.7, f.settings().ultimateLethal);
            }
        }
        Vector3f slide = new Vector3f(b.forward()).normalize();
        f.destruction().ultimate(f.rel(c), new Vector3f(-slide.z, 0, slide.x));
        f.atmo().shake(ps(), 1f, 24);
        f.setBarOverride(0f);
    }

    Sequence getUp() {
        SamuraiBoss b = boss();
        return new Sequence()
                .at(0, () -> {
                    b.play(b.anims.getUp, 4, 6, 1f, ev -> {
                    });
                    sfx().play(b.location(), Sfx.Id.GHOST, 1f, 0.8f);
                    f.refreshBarName();
                })
                .at(24, () -> {
                    b.setAutoBase(true);
                    fx().fx.add(new KanjiFx(fx(), b.eye().add(0, 2.4, 0), "無", 0xFFFFFF, 3f, null, true, 18, KanjiFx.Ending.SHATTER));
                    sfx().play(b.location(), Sfx.Id.KANJI);
                    f.beginCombat(20);
                });
    }

    // =================================================================== Tod

    Sequence death() {
        SamuraiBoss b = boss();
        final GhostBlade[] planted = new GhostBlade[1];
        return new Sequence()
                .at(0, () -> {
                    b.stopWalking();
                    b.setAutoBase(false);
                    b.setHover(0f);
                    b.setGravity(true);
                    f.music().play(Music.JISEI);
                    f.setBarName(Fight.mm("<gray>…</gray>"), null);
                    f.setBarOverride(0f);
                    f.atmo().timeTo(ps(), 23300, 220);
                    f.atmo().weather(ps(), null);
                    f.atmo().tint(0f);
                    if (f.petals() != null) f.petals().setMode(PetalField.Mode.DRIFT);
                    sfx().play(b.location(), Sfx.Id.DEATH);
                    f.atmo().shake(ps(), 0.5f, 8);
                    b.play(b.anims.death, 4, 8, 1f, ev -> {
                        switch (ev) {
                            case "crack" -> {
                                fx().fx.add(new Shards(fx(), b.location().add(0, 1.5, 0), 10, 0.5f, 0.12f, 18,
                                        Palette.WHITE_GLASS, Palette.PINK_GLASS));
                                sfx().play(b.location(), Sfx.Id.CRACK);
                            }
                            case "plant" -> sfx().play(b.location(), Sfx.Id.BLADE_HIT, 1f, 0.7f);
                            default -> {
                            }
                        }
                    });
                })
                .at(30, () -> line("花は散り", "Die Blüten fallen,"))
                .at(66, () -> line("刃は鞘へ", "die Klinge kehrt in die Scheide –"))
                .at(102, () -> line("春の夢", "ein Frühlingstraum."))
                .at(128, () -> {
                    Location at = b.location();
                    List<ModelInstance.PartState> parts = b.model().snapshot(c -> true);
                    fx().fx.add(new Afterimage(fx(), parts, Palette.PINK_GLASS, Palette.WHITE_GLASS, SamuraiSkins.SAKURA, 3, 40,
                            new Vector3f(0, 2.6f, 0)));
                    b.model().setAllHidden(true);
                    fx().fx.add(new SpiralConverge(fx(), at, 50, 6f, 3f, 40, true));
                    sfx().play(at, Sfx.Id.GHOST, 1f, 0.6f);
                    sfx().play(at, Sfx.Id.CHIME, 1f, 0.7f);
                    // Das Katana bleibt im Boden stecken
                    planted[0] = fx().fx.add(new GhostBlade(fx(), f.ground(at), 1.2f, false));
                    Quaternionf down = MathUtil.lookAlongZ(new Vector3f(0.08f, -1f, 0.05f));
                    planted[0].pose(new Vector3f(0, 1.05f, 0), down, 1f, 0);
                    if (f.moon != null) f.moon.fadeOut();
                })
                .at(156, () -> {
                    Location top = f.arena().point("tree_top");
                    fx().fx.add(new PetalBurst(fx(), top, 70, 3.2f, 0.5f, 60));
                    fx().fx.add(new PetalBurst(fx(), top.clone().add(0, -3, 0), 40, 2.4f, 0.2f, 50));
                    sfx().play(top, Sfx.Id.CHIME, 1.5f, 1.1f);
                    sfx().play(top, Sfx.Id.PETALS, 1.5f, 0.9f);
                })
                .at(170, () -> {
                    title("<gradient:#ffd1dc:#ff6f91>見事</gradient>", "<gray>Sakuragi Jinsai ruht wieder unter der Kirsche.</gray>", 10, 70, 20);
                    for (Player p : ps()) sfx().playFor(p, Sfx.Id.VICTORY, 1f, 1f);
                    f.music().play(Music.HARU);
                    f.victory();
                })
                .at(200, () -> {
                    if (planted[0] != null && !planted[0].isDead()) {
                        fx().fx.add(new PetalBurst(fx(), b.location().add(0, 1, 0), 12, 1.2f, 0.6f, 24));
                        planted[0].dissolve(14);
                    }
                });
    }

    // =================================================================== Niederlage

    Sequence defeat() {
        SamuraiBoss b = boss();
        return new Sequence()
                .at(0, () -> {
                    b.stopWalking();
                    b.setAutoBase(false);
                    b.setHover(0f);
                    b.setGravity(true);
                    f.music().stop(40);
                    if (b.drawn()) b.play(b.anims.sheatheFromKamae, 4, 6, 1f, ev -> {
                    });
                })
                .at(32, () -> {
                    b.play(b.anims.bow, 6, 6, 1f, ev -> {
                    });
                    f.broadcastToArena("defeat");
                })
                .at(74, () -> {
                    Location at = b.location();
                    fx().fx.add(new SpiralConverge(fx(), at, 40, 5f, 2.8f, 36, true));
                    b.model().setAllHidden(true);
                    sfx().play(at, Sfx.Id.PETALS, 1.2f, 0.8f);
                    if (f.moon != null) f.moon.fadeOut();
                })
                .at(100, () -> f.startRebuild(true));
    }

    @SuppressWarnings("unused")
    private static Component empty() {
        return Component.empty();
    }
}
