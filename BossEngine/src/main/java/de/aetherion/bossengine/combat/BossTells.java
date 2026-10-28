package de.aetherion.bossengine.combat;

import de.aetherion.bossengine.util.BossKeys;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The combat kit's attack tells, on a boss. The training golem (AetherionItems TrainingDummy) teaches the same
 * language: a yellow "!" over the attacker = parry it (F as it lands, {@link BossHits#hurt}); a red "!!" = it cannot
 * be parried, roll through it or get out ({@link BossHits#peril}). The mark turns white on the beat to act, a few
 * ticks before the hit, and pops until it lands. One board per fight; the director moves each tell with the part
 * that strikes, and a tell removes itself a few ticks after it landed.
 */
public final class BossTells {

    public enum Kind { PARRY, ROLL }

    private static final TextColor YELLOW = TextColor.color(0xFFD24A);
    private static final TextColor RED = TextColor.color(0xFF5A4A);
    private static final Key COMBAT_FONT = Key.key("aetherion", "combat");
    /** Pack font glyphs (resourcepack gen_combat_glyphs.py): the red 危 and the finisher slash. */
    private static final char PERIL = '';
    private static final char FINISHER = '';
    /** White this many ticks before the hit: the golem's jab and slam beats, one tick earlier for a boss's reach. */
    private static final int PARRY_BEAT = 5;
    private static final int ROLL_BEAT = 8;

    private final BossKeys keys;
    private final UUID owner;
    private final double reach;
    private final List<Tell> live = new ArrayList<>();

    /** @param reach who hears the cues and sees the screen glyphs, in blocks around the mark */
    public BossTells(BossKeys keys, UUID owner, double reach) {
        this.keys = keys;
        this.owner = owner;
        this.reach = reach;
    }

    /** Yellow "!": lands in {@code ticks}. */
    public Tell parry(Location at, int ticks, float size) {
        return show(Kind.PARRY, at, ticks, size);
    }

    /** Red "!!": lands in {@code ticks}. Everyone close sees the 危 on screen. */
    public Tell roll(Location at, int ticks, float size) {
        return show(Kind.ROLL, at, ticks, size);
    }

    private Tell show(Kind kind, Location at, int ticks, float size) {
        Tell tell = new Tell(kind, at, ticks, size);
        live.add(tell);
        World world = at.getWorld();
        if (world == null) {
            return tell;
        }
        if (kind == Kind.PARRY) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f, 1.6f);
            world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.4f, 1.2f);
        } else {
            world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, 0.7f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
            glyph(at, PERIL, 0, 10, 4);
        }
        return tell;
    }

    public void tick() {
        live.removeIf(tell -> !tell.tick());
    }

    public void clear() {
        for (Tell tell : live) {
            tell.remove();
        }
        live.clear();
    }

    /** The finisher slash on everyone's screen close to {@code at}: its guard is broken. */
    public void finisher(Location at) {
        glyph(at, FINISHER, 0, 6, 8);
    }

    private void glyph(Location at, char glyph, int in, int stay, int out) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Title title = Title.title(Component.text(String.valueOf(glyph)).font(COMBAT_FONT), Component.empty(),
                Title.Times.times(Ticks.duration(in), Ticks.duration(stay), Ticks.duration(out)));
        double r2 = reach * reach;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= r2) {
                player.showTitle(title);
            }
        }
    }

    /** One mark over one attack. */
    public final class Tell {

        private final Kind kind;
        private final float size;
        private final TextDisplay marker;
        private long landAt;
        private boolean white;
        private Location at;

        private Tell(Kind kind, Location at, int ticks, float size) {
            this.kind = kind;
            this.size = size;
            this.at = at.clone();
            this.landAt = Bukkit.getCurrentTick() + Math.max(1, ticks);
            World world = at.getWorld();
            this.marker = world == null ? null : world.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.text(Component.text(kind == Kind.PARRY ? "!" : "!!", kind == Kind.PARRY ? YELLOW : RED)
                        .decorate(TextDecoration.BOLD));
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setTeleportDuration(2);
                d.setTransformation(scale(size));
                if (keys != null) {
                    keys.tagBeamFx(d, owner);
                }
            });
        }

        public Kind kind() {
            return kind;
        }

        /** Keeps the mark over the part that strikes. */
        public void move(Location to) {
            if (marker == null || !marker.isValid() || to == null || to.getWorld() != marker.getWorld()) {
                return;
            }
            at = to.clone();
            if (marker.getLocation().distanceSquared(to) > 0.01) {
                marker.teleport(to);
            }
        }

        /** The hit is now {@code ticks} away (a lunge that lands when it arrives, not on a clock). */
        public void landIn(int ticks) {
            landAt = Bukkit.getCurrentTick() + Math.max(0, ticks);
        }

        /** @return false once it is gone */
        private boolean tick() {
            if (marker == null || !marker.isValid()) {
                return false;
            }
            long left = landAt - Bukkit.getCurrentTick();
            if (!white && left <= (kind == Kind.PARRY ? PARRY_BEAT : ROLL_BEAT)) {
                white = true;
                marker.text(marker.text().color(NamedTextColor.WHITE));
                World world = marker.getWorld();
                if (kind == Kind.PARRY) {
                    world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.9f);
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f, 2.0f);
                } else {
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.8f, 0.7f);
                    world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 1.6f, 1.4f);
                }
            }
            if (left <= 6 && left >= 0 && left % 2 == 0) {
                marker.setTransformation(scale(left % 4 < 2 ? size * 1.25f : size));
            }
            if (left < -3) {
                remove();
                return false;
            }
            return true;
        }

        public void remove() {
            if (marker != null && marker.isValid()) {
                marker.remove();
            }
        }
    }

    private static Transformation scale(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }
}
