package de.aetherion.gnomon.render;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spawns and owns every display of one encounter. All parts share one anchor (the arena center) and never move;
 * only their transformations change. Everything is tagged {@link #TAG}, non-persistent, and swept on enable.
 */
public final class Parts {

    public static final String TAG = "ae_gnomon";
    public static final NamespacedKey KEY = new NamespacedKey("aetheriongnomon", "part");

    private final Location anchor;
    private final float viewRange;
    private final List<Part> live = new ArrayList<>();
    private boolean closed;

    public Parts(Location anchor, float viewRange) {
        this.anchor = anchor.clone();
        this.anchor.setYaw(0);
        this.anchor.setPitch(0);
        this.viewRange = viewRange;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public World world() {
        return anchor.getWorld();
    }

    public Part spawn(Material material) {
        return spawn(material.createBlockData(), -1);
    }

    public Part spawn(Material material, int brightness) {
        return spawn(material.createBlockData(), brightness);
    }

    public Part spawn(BlockData data, int brightness) {
        if (closed || anchor.getWorld() == null) {
            return null;
        }
        BlockDisplay display = anchor.getWorld().spawn(anchor, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            d.setBlock(data);
            d.setViewRange(viewRange);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setBillboard(Display.Billboard.FIXED);
            if (brightness >= 0) {
                d.setBrightness(new Display.Brightness(brightness, brightness));
            }
            // Born invisible; the first pose scales it in.
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf()));
            d.setInterpolationDuration(0);
            d.setInterpolationDelay(0);
            d.setTeleportDuration(0);
        });
        Part part = new Part(display, anchor, data);
        if (brightness >= 0) {
            part.brightness(brightness);
        }
        live.add(part);
        return part;
    }

    public void remove(Part part) {
        if (part != null) {
            part.remove();
            live.remove(part);
        }
    }

    public int count() {
        live.removeIf(p -> !p.valid());
        return live.size();
    }

    /** Removes everything this encounter spawned. Idempotent. */
    public void removeAll() {
        closed = true;
        for (Iterator<Part> it = live.iterator(); it.hasNext(); ) {
            it.next().remove();
            it.remove();
        }
    }

    public boolean isOurs(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    /** Crash / reload safety net: removes every tagged display in the world. Returns how many. */
    public static int sweep(World world) {
        if (world == null) {
            return 0;
        }
        int n = 0;
        for (Entity e : world.getEntitiesByClass(Display.class)) {
            if (e.getScoreboardTags().contains(TAG)
                    || e.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) {
                e.remove();
                n++;
            }
        }
        return n;
    }
}
