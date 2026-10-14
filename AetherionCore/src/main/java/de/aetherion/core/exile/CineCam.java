package de.aetherion.core.exile;

import io.papermc.paper.entity.TeleportFlag;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's film camera: an invisible display the player spectates. Moving it every tick with a short
 * {@code teleport_duration} gives one continuous, sub-tick-smooth shot (no armor-stand hops).
 *
 * <p>Also carries the "white": billboarded mist sheets riding the camera, stacked to fade the screen into the
 * Veil (departure) or peeled away to fade out of it (arrival).
 */
public final class CineCam {

    private final Plugin plugin;
    private final Player viewer;
    private final ItemDisplay cam;
    private final List<TextDisplay> white = new ArrayList<>();

    private CineCam(Plugin plugin, Player viewer, ItemDisplay cam) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.cam = cam;
    }

    public static CineCam start(Plugin plugin, Player viewer, Location at, int teleportTicks) {
        NamespacedKey key = BlockRig.key(plugin);
        ItemDisplay cam = at.getWorld().spawn(at.clone(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setSilent(true);
            d.addScoreboardTag(BlockRig.TAG);
            d.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            d.setTeleportDuration(teleportTicks);
            d.setViewRange(2.0f);
            d.setRotation(at.getYaw(), at.getPitch());
        });
        viewer.showEntity(plugin, cam);
        CineCam c = new CineCam(plugin, viewer, cam);
        viewer.closeInventory();
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleport(at);
        viewer.setSpectatorTarget(cam);
        return c;
    }

    public ItemDisplay entity() {
        return cam;
    }

    public boolean valid() {
        return cam.isValid() && viewer.isOnline();
    }

    /** Next frame (position + yaw/pitch). */
    public void frame(Location at) {
        if (!cam.isValid()) {
            return;
        }
        cam.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Keeps the player locked to the lens (another plugin may have reset game mode or target). */
    public void hold() {
        if (!viewer.isOnline() || !cam.isValid()) {
            return;
        }
        if (viewer.getGameMode() != GameMode.SPECTATOR) {
            viewer.setGameMode(GameMode.SPECTATOR);
        }
        Entity target = viewer.getSpectatorTarget();
        if (target == null || !target.getUniqueId().equals(cam.getUniqueId())) {
            if (viewer.getWorld() != cam.getWorld() || viewer.getLocation().distanceSquared(cam.getLocation()) > 64 * 64) {
                viewer.teleport(cam.getLocation());
            }
            viewer.setSpectatorTarget(cam);
        }
    }

    /**
     * Adds {@code layers} pairs of white sheets around the lens. Each layer is a little more opaque screen.
     */
    public void addWhite(int layers, int alpha) {
        for (int i = 0; i < layers; i++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                final float z = dir * (1.1f + i * 0.05f);
                TextDisplay t = cam.getWorld().spawn(cam.getLocation(), TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setVisibleByDefault(false);
                    d.addScoreboardTag(BlockRig.TAG);
                    d.getPersistentDataContainer().set(BlockRig.key(plugin), PersistentDataType.BYTE, (byte) 1);
                    d.text(Component.text(" "));
                    d.setDefaultBackground(false);
                    d.setBackgroundColor(Color.fromARGB(alpha, 232, 233, 240));
                    d.setShadowed(false);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(0f, -3f, z), new Quaternionf(),
                            new Vector3f(96f, 24f, 1f), new Quaternionf()));
                });
                viewer.showEntity(plugin, t);
                cam.addPassenger(t);
                white.add(t);
            }
        }
    }

    /** Peels the white away over {@code ticks} (shrinks every layer to nothing), then drops it. */
    public void clearWhite(int ticks) {
        List<TextDisplay> going = new ArrayList<>(white);
        white.clear();
        int k = 0;
        for (TextDisplay t : going) {
            if (!t.isValid()) {
                continue;
            }
            Transformation now = t.getTransformation();
            t.setInterpolationDelay(k++ % 3);
            t.setInterpolationDuration(ticks);
            t.setTransformation(new Transformation(now.getTranslation(), now.getLeftRotation(),
                    new Vector3f(0.01f, 0.01f, 1f), now.getRightRotation()));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> going.forEach(TextDisplay::remove), ticks + 4L);
    }

    /**
     * Ends the shot: the player gets their body back at {@code feet} in {@code mode}. Put {@code feet} one eye
     * height below the last frame and the cut is invisible.
     */
    public void end(Location feet, GameMode mode) {
        try {
            viewer.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        if (viewer.isOnline()) {
            if (mode != null) {
                viewer.setGameMode(mode);
            }
            if (feet != null) {
                viewer.teleport(feet);
                viewer.setFallDistance(0f);
            }
        }
        remove();
    }

    public void remove() {
        for (TextDisplay t : white) {
            try {
                t.remove();
            } catch (Throwable ignored) {
            }
        }
        white.clear();
        try {
            for (Entity p : cam.getPassengers()) {
                p.remove();
            }
            cam.remove();
        } catch (Throwable ignored) {
        }
    }
}
