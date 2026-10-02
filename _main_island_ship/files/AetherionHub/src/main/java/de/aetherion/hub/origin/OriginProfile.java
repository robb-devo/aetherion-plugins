package de.aetherion.hub.origin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One player's Origin Isle record: what they found, rang, rode and attuned. */
public final class OriginProfile {

    public final UUID id;
    public final Set<String> districts = new LinkedHashSet<>();
    public final Set<String> landmarks = new LinkedHashSet<>();
    public final Set<String> waystones = new LinkedHashSet<>();
    public final Set<String> bells = new LinkedHashSet<>();
    public final Set<String> vistas = new LinkedHashSet<>();
    public final Set<String> rides = new LinkedHashSet<>();
    public final Set<String> met = new LinkedHashSet<>();
    public final Set<String> flags = new LinkedHashSet<>();
    /** -1 = never started; 0..n = current step; >= steps = done. */
    public int tourStep = -1;
    public int wishes;
    public long lastWishEvent;
    public long coinsEarned;
    public boolean ambience = true;
    public boolean particles = true;
    public boolean dirty;

    public OriginProfile(UUID id) {
        this.id = id;
    }

    void read(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        districts.addAll(s.getStringList("districts"));
        landmarks.addAll(s.getStringList("landmarks"));
        waystones.addAll(s.getStringList("waystones"));
        bells.addAll(s.getStringList("bells"));
        vistas.addAll(s.getStringList("vistas"));
        rides.addAll(s.getStringList("rides"));
        met.addAll(s.getStringList("met"));
        flags.addAll(s.getStringList("flags"));
        tourStep = s.getInt("tour-step", -1);
        wishes = s.getInt("wishes", 0);
        lastWishEvent = s.getLong("last-wish-event", 0L);
        coinsEarned = s.getLong("coins-earned", 0L);
        ambience = s.getBoolean("settings.ambience", true);
        particles = s.getBoolean("settings.particles", true);
    }

    YamlConfiguration write() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("districts", new ArrayList<>(districts));
        y.set("landmarks", new ArrayList<>(landmarks));
        y.set("waystones", new ArrayList<>(waystones));
        y.set("bells", new ArrayList<>(bells));
        y.set("vistas", new ArrayList<>(vistas));
        y.set("rides", new ArrayList<>(rides));
        y.set("met", new ArrayList<>(met));
        y.set("flags", new ArrayList<>(flags));
        y.set("tour-step", tourStep);
        y.set("wishes", wishes);
        y.set("last-wish-event", lastWishEvent);
        y.set("coins-earned", coinsEarned);
        y.set("settings.ambience", ambience);
        y.set("settings.particles", particles);
        return y;
    }

    public boolean flag(String flag) {
        return flags.contains(flag);
    }

    /** True if the flag was newly set. */
    public boolean mark(String flag) {
        if (flags.add(flag)) {
            dirty = true;
            return true;
        }
        return false;
    }
}
