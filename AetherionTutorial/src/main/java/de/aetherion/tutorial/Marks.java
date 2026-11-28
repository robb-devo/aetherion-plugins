package de.aetherion.tutorial;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The named spots of the isle: where the bell hangs, where Burr stands, where the coal seam is. The world generator
 * ({@code tools/isle}) writes them to {@code isle.json} in the world folder, so the lessons never carry a coordinate
 * of their own and a rebuilt island moves its lessons with it.
 */
public final class Marks {

    private record Spot(double x, double y, double z, float yaw, double radius) {
    }

    private final Map<String, Spot> spots = new HashMap<>();

    private Marks() {
    }

    public static Marks load(File file) throws IOException {
        Marks marks = new Marks();
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        JsonObject all = root.getAsJsonObject("marks");
        if (all == null) {
            return marks;
        }
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            double x = o.get("x").getAsDouble();
            double y = o.get("y").getAsDouble();
            double z = o.get("z").getAsDouble();
            float yaw = o.has("yaw") ? o.get("yaw").getAsFloat() : Float.NaN;
            double radius = o.has("radius") ? o.get("radius").getAsDouble() : 0.0;
            marks.spots.put(e.getKey(), new Spot(x, y, z, yaw, radius));
        }
        return marks;
    }

    public boolean has(String key) {
        return key != null && spots.containsKey(key);
    }

    public Set<String> keys() {
        return spots.keySet();
    }

    /** The middle of the marked block, feet height; yaw from the mark when it has one. Null for an unknown key. */
    public Location at(World world, String key) {
        Spot s = key == null ? null : spots.get(key);
        if (s == null || world == null) {
            return null;
        }
        return new Location(world, s.x() + 0.5, s.y(), s.z() + 0.5, Float.isNaN(s.yaw()) ? 0f : s.yaw(), 0f);
    }

    /** The marked block itself (integer coordinates as written). */
    public Location block(World world, String key) {
        Spot s = key == null ? null : spots.get(key);
        if (s == null || world == null) {
            return null;
        }
        return new Location(world, s.x(), s.y(), s.z());
    }

    public double radius(String key, double fallback) {
        Spot s = key == null ? null : spots.get(key);
        return s == null || s.radius() <= 0.0 ? fallback : s.radius();
    }
}
