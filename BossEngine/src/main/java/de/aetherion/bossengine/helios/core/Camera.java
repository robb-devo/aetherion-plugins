package de.aetherion.bossengine.helios.core;

import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything that happens "to the lens" rather than in the world, all per player and all undone:
 *
 * <ul>
 *   <li><b>Shake.</b> The client's own hurt tilt ({@code sendHurtAnimation}) without damage, re-fired
 *       on a decaying schedule. This is the only camera shake vanilla allows.</li>
 *   <li><b>Flash.</b> A block display cube with <i>negative</i> scale riding the player's head and shown
 *       only to them: inverted faces are visible from inside, so the whole view turns to that
 *       material. White concrete at full brightness is the white flash; it then thins into a
 *       translucent veil and collapses. (A resource pack full-screen glyph is cleaner; see docs.)</li>
 *   <li><b>Blackout / Darkness.</b> Vanilla blindness and darkness effects, short and ambient-free.</li>
 *   <li><b>Vignette.</b> A private world border whose warning distance tints the screen edges red.</li>
 * </ul>
 */
public final class Camera {

    private static final float CUBE = 3.2f;
    /** A rider sits on top of the player's head; the eye is this far below that. */
    private static final float EYE_BELOW_MOUNT = 0.2f;

    private final Plugin plugin;
    private final HeliosStage stage;
    private final Map<UUID, Shake> shakes = new HashMap<>();
    private final List<Veil> veils = new ArrayList<>();
    private final Map<UUID, WorldBorder> borders = new HashMap<>();

    public Camera(Plugin plugin, HeliosStage stage) {
        this.plugin = plugin;
        this.stage = stage;
    }

    public void tick() {
        for (Iterator<Map.Entry<UUID, Shake>> it = shakes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shake> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || e.getValue().tick(p)) {
                it.remove();
            }
        }
        for (Iterator<Veil> it = veils.iterator(); it.hasNext(); ) {
            if (it.next().tick()) {
                it.remove();
            }
        }
    }

    /* ------------------------------------------------------------------ shake */

    /** Rumble for {@code ticks}: hurt tilts every {@code every} ticks, strongest at the start. */
    public void shake(Player p, int ticks, int every) {
        if (p == null) {
            return;
        }
        shakes.put(p.getUniqueId(), new Shake(Math.max(1, ticks), Math.max(2, every)));
    }

    public void shakeAll(int ticks, int every) {
        for (Player p : stage.audience()) {
            shake(p, ticks, every);
        }
    }

    /** Shake scaled by distance: close to {@code at} the full rumble, far away a single tilt. */
    public void shakeFrom(Vector3f at, float radius, int ticks) {
        for (Player p : stage.audience()) {
            float d = stage.feet(p).distance(at);
            if (d > radius) {
                continue;
            }
            float f = 1f - d / radius;
            shake(p, Math.max(2, Math.round(ticks * f)), f > 0.6f ? 2 : 4);
        }
    }

    public void kick(Player p) {
        if (p != null && p.isOnline()) {
            p.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
        }
    }

    private static final class Shake {
        private final int length;
        private final int every;
        private int t;

        Shake(int length, int every) {
            this.length = length;
            this.every = every;
        }

        boolean tick(Player p) {
            // Decaying schedule: the gaps widen as the rumble dies down.
            int gap = every + (int) (every * 1.5f * (t / (float) length));
            if (t % Math.max(2, gap) == 0 && p.isOnline()) {
                p.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
            }
            t++;
            return t >= length;
        }
    }

    /* ------------------------------------------------------------------ flash */

    /** White-out: {@code hold} ticks of solid light, then a {@code fade}-tick veil that collapses. */
    public void flash(int hold, int fade) {
        for (Player p : stage.audience()) {
            flash(p, Material.WHITE_CONCRETE, Material.WHITE_STAINED_GLASS, hold, fade);
        }
    }

    /** A tinted flash (heat: orange, cold light: light blue), veil only. */
    public void tint(Material glass, int hold, int fade) {
        for (Player p : stage.audience()) {
            flash(p, glass, glass, hold, fade);
        }
    }

    public void flash(Player p, Material solid, Material veil, int hold, int fade) {
        if (p == null || !p.isOnline() || p.getWorld() != stage.world()) {
            return;
        }
        Location at = p.getLocation();
        BlockDisplay cube = p.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBlock(solid.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1f);
            d.setShadowRadius(0f);
            d.setTransformation(inverted(CUBE));
        });
        p.showEntity(plugin, cube);
        p.addPassenger(cube);
        veils.add(new Veil(p.getUniqueId(), cube, veil, hold, fade));
    }

    private static Transformation inverted(float size) {
        float h = size * 0.5f;
        return new Transformation(new Vector3f(h, h - EYE_BELOW_MOUNT, h), new Quaternionf(),
                new Vector3f(-size, -size, -size), new Quaternionf());
    }

    private final class Veil {
        private final UUID player;
        private final BlockDisplay cube;
        private final Material veil;
        private final int hold;
        private final int fade;
        private int t;

        Veil(UUID player, BlockDisplay cube, Material veil, int hold, int fade) {
            this.player = player;
            this.cube = cube;
            this.veil = veil;
            this.hold = Math.max(1, hold);
            this.fade = Math.max(1, fade);
        }

        boolean tick() {
            t++;
            Player p = Bukkit.getPlayer(player);
            if (p == null || !cube.isValid() || !p.isOnline()) {
                remove();
                return true;
            }
            if (t == hold) {
                cube.setBlock(veil.createBlockData());
            }
            if (t == hold + fade / 2) {
                // Collapse through the eye: the veil wipes away instead of popping.
                cube.setInterpolationDelay(0);
                cube.setInterpolationDuration(Math.max(1, fade / 2));
                cube.setTransformation(inverted(0.3f));
            }
            if (t >= hold + fade) {
                remove();
                return true;
            }
            return false;
        }

        void remove() {
            if (cube.isValid()) {
                cube.remove();
            }
        }
    }

    /* ------------------------------------------------------------------ dark */

    public void blackout(Player p, int ticks) {
        if (p != null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.max(1, ticks), 0, false, false, false));
        }
    }

    public void blackoutAll(int ticks) {
        for (Player p : stage.audience()) {
            blackout(p, ticks);
        }
    }

    public void darkness(Player p, int ticks) {
        if (p != null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.max(1, ticks), 0, false, false, false));
        }
    }

    public void darknessAll(int ticks) {
        for (Player p : stage.audience()) {
            darkness(p, ticks);
        }
    }

    public void clearDark(Player p) {
        if (p != null) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    /* ------------------------------------------------------------------ vignette */

    /** Red screen edges, 0 = off, 1 = full. Private world border, never solid for the player. */
    public void vignette(Player p, float strength) {
        if (p == null) {
            return;
        }
        if (strength <= 0.01f) {
            if (borders.remove(p.getUniqueId()) != null) {
                p.setWorldBorder(null);
            }
            return;
        }
        WorldBorder border = borders.computeIfAbsent(p.getUniqueId(), id -> {
            WorldBorder b = Bukkit.createWorldBorder();
            b.setDamageAmount(0.0);
            b.setDamageBuffer(1_000_000.0);
            b.setWarningTime(0);
            return b;
        });
        Location l = p.getLocation();
        double half = 2_000.0;
        border.setCenter(l.getX(), l.getZ());
        border.setSize(half * 2.0);
        int warn = (int) Math.min(Integer.MAX_VALUE - 1, Math.round(half / Math.max(0.02, 1.0 - Math.min(0.97, strength))));
        border.setWarningDistance(warn);
        if (p.getWorldBorder() != border) {
            p.setWorldBorder(border);
        }
    }

    public void vignetteAll(float strength) {
        for (Player p : stage.audience()) {
            vignette(p, strength);
        }
    }

    /* ------------------------------------------------------------------ text */

    public void title(String main, String sub, int fadeIn, int stay, int fadeOut) {
        Title title = Title.title(
                TextUtil.component(main),
                TextUtil.component(sub),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L)));
        for (Player p : stage.audience()) {
            p.showTitle(title);
        }
    }

    public void actionBar(String text) {
        for (Player p : stage.audience()) {
            p.sendActionBar(TextUtil.component(text));
        }
    }

    /** Undo everything for one player (leave, quit, end). */
    public void release(Player p) {
        if (p == null) {
            return;
        }
        shakes.remove(p.getUniqueId());
        if (borders.remove(p.getUniqueId()) != null) {
            p.setWorldBorder(null);
        }
        for (Iterator<Veil> it = veils.iterator(); it.hasNext(); ) {
            Veil v = it.next();
            if (v.player.equals(p.getUniqueId())) {
                v.remove();
                it.remove();
            }
        }
        clearDark(p);
    }

    public void releaseAll() {
        for (UUID id : new ArrayList<>(borders.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setWorldBorder(null);
            }
        }
        borders.clear();
        shakes.clear();
        for (Veil v : veils) {
            v.remove();
        }
        veils.clear();
    }
}
