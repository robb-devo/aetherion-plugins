package de.aetherion.gnomon.fx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Sound, lens effects and words. Everything here is per player or spatial, and nothing persists: shakes are hurt
 * tilts, flashes are short hidden potion pulses.
 */
public final class Fx {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final World world;
    private final Supplier<Collection<Player>> audience;
    private final Map<UUID, int[]> shakes = new HashMap<>();

    public Fx(World world, Supplier<Collection<Player>> audience) {
        this.world = world;
        this.audience = audience;
    }

    public Collection<Player> audience() {
        return audience.get();
    }

    /* ------------------------------------------------------------------ sound */

    public void sound(double x, double y, double z, Sound sound, float volume, float pitch) {
        world.playSound(new Location(world, x, y, z), sound, SoundCategory.HOSTILE, volume, pitch);
    }

    public void sound(Location at, Sound sound, float volume, float pitch) {
        world.playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    /** Private sound at a position (only this player hears it). */
    public void soundTo(Player p, double x, double y, double z, Sound sound, float volume, float pitch) {
        if (p != null && p.isOnline()) {
            p.playSound(new Location(world, x, y, z), sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /** Private sound in the player's head. */
    public void soundIn(Player p, Sound sound, float volume, float pitch) {
        if (p != null && p.isOnline()) {
            p.playSound(p.getLocation(), sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /* ------------------------------------------------------------------ lens */

    /** Rumble: hurt tilts on a decaying schedule. */
    public void shake(Player p, int ticks, int every) {
        if (p == null) {
            return;
        }
        int[] cur = shakes.get(p.getUniqueId());
        if (cur != null && cur[0] - cur[2] > ticks) {
            return;
        }
        shakes.put(p.getUniqueId(), new int[]{Math.max(1, ticks), Math.max(2, every), 0});
    }

    /** Shake everyone within {@code radius}, strongest near the source. */
    public void shakeNear(double x, double z, double radius, int ticks) {
        for (Player p : audience()) {
            double d = Math.hypot(p.getLocation().getX() - x, p.getLocation().getZ() - z);
            if (d > radius) {
                continue;
            }
            double f = 1 - d / radius;
            shake(p, Math.max(2, (int) Math.round(ticks * f)), f > 0.6 ? 2 : 4);
        }
    }

    public void shakeAll(int ticks, int every) {
        for (Player p : audience()) {
            shake(p, ticks, every);
        }
    }

    public void kick(Player p) {
        if (p != null && p.isOnline()) {
            p.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
        }
    }

    public void tick() {
        for (Iterator<Map.Entry<UUID, int[]>> it = shakes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, int[]> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            int[] s = e.getValue();
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            int gap = s[1] + (int) (s[1] * 1.5f * (s[2] / (float) s[0]));
            if (s[2] % Math.max(2, gap) == 0) {
                p.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
            }
            s[2]++;
            if (s[2] >= s[0]) {
                it.remove();
            }
        }
    }

    /** Light floods in (night vision pulse, hidden). */
    public void flash(Player p, int ticks) {
        if (p != null && p.isOnline()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Math.max(2, ticks), 0, false, false, false));
        }
    }

    public void darkness(Player p, int ticks) {
        if (p != null && p.isOnline()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.max(2, ticks), 0, false, false, false));
        }
    }

    /** Removes only the short effects this plugin adds (hidden-icon ones). */
    public static void clearEffects(Player p) {
        for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.NIGHT_VISION, PotionEffectType.DARKNESS}) {
            PotionEffect e = p.getPotionEffect(t);
            if (e != null && !e.hasIcon() && e.getDuration() < 20 * 30) {
                p.removePotionEffect(t);
            }
        }
    }

    /* ------------------------------------------------------------------ words */

    public void subtitle(Player p, String legacy, int stayTicks) {
        if (p == null || !p.isOnline()) {
            return;
        }
        p.showTitle(Title.title(Component.empty(), LEGACY.deserialize(legacy),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(500))));
    }

    public void title(Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (p == null || !p.isOnline()) {
            return;
        }
        p.showTitle(Title.title(LEGACY.deserialize(title), LEGACY.deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    public void actionbar(Player p, String legacy) {
        if (p != null && p.isOnline()) {
            p.sendActionBar(LEGACY.deserialize(legacy));
        }
    }

    /* ------------------------------------------------------------------ particles (accents only) */

    public void dust(double x, double y, double z, BlockData floor, int count, double spread) {
        try {
            world.spawnParticle(Particle.BLOCK, x, y, z, count, spread, 0.15, spread, 0.12, floor);
        } catch (IllegalArgumentException ignored) {
            // block particle needs block data of a real block; fine to skip
        }
    }

    public void particle(Particle particle, double x, double y, double z, int count, double spread, double speed) {
        world.spawnParticle(particle, x, y, z, count, spread, spread, spread, speed);
    }

    public void ring(Particle particle, double x, double y, double z, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            world.spawnParticle(particle, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }
}
