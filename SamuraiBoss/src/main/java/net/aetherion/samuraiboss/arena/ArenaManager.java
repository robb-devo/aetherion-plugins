package net.aetherion.samuraiboss.arena;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.arena.dojo.BuildPlan;
import net.aetherion.samuraiboss.arena.dojo.DojoBlueprint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ExperienceOrb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Verwaltet alle Dojos: Laden/Speichern, Bauen, Abreißen, Wiederherstellen nach Absturz,
 * Deko und laufende Aufgaben ("Jobs").
 *
 * <p>Dateien: plugins/SamuraiBoss/arenas/&lt;id&gt;/arena.yml, prebuild.snap (Gelände vor dem
 * Bau), fight.snap (Zustand zu Kampfbeginn).</p>
 */
public final class ArenaManager {

    private final SamuraiBossPlugin plugin;
    private final File root;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<Arena, Job> jobs = new LinkedHashMap<>();
    /** Einmal erzeugt – der Bauplan ist für alle Dojos gleich (nur verschoben). */
    private BuildPlan planCache;

    public ArenaManager(SamuraiBossPlugin plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "arenas");
    }

    public BuildPlan plan() {
        if (planCache == null) planCache = DojoBlueprint.generate();
        return planCache;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public Arena get(String id) {
        return arenas.get(id.toLowerCase());
    }

    /** Arena, in deren Bereich die Position liegt (oder null). */
    public Arena at(Location l) {
        for (Arena a : arenas.values()) if (a.inRegion(l)) return a;
        return null;
    }

    public Arena nearest(Location l) {
        Arena best = null;
        double bd = Double.MAX_VALUE;
        for (Arena a : arenas.values()) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(a.worldName)) continue;
            double d = a.center().distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = a;
            }
        }
        return best;
    }

    /** Prüft, ob ein neues Dojo an dieser Stelle mit einem bestehenden überlappen würde. */
    public Arena overlapping(Location origin) {
        BuildPlan p = plan();
        int ox = origin.getBlockX(), oz = origin.getBlockZ();
        for (Arena a : arenas.values()) {
            if (!a.worldName.equals(origin.getWorld().getName())) continue;
            boolean sepX = ox + p.maxX < a.minX() || ox + p.minX > a.maxX();
            boolean sepZ = oz + p.maxZ < a.minZ() || oz + p.minZ > a.maxZ();
            if (!sepX && !sepZ) return a;
        }
        return null;
    }

    // ------------------------------------------------------------------ Laden & Speichern

    public void load() {
        arenas.clear();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File d : dirs) {
            File f = new File(d, "arena.yml");
            if (!f.exists()) continue;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String id = y.getString("id", d.getName()).toLowerCase();
            Arena a = new Arena(id, y.getString("world", "world"), y.getInt("x"), y.getInt("y"), y.getInt("z"), plan(), d);
            a.built = y.getBoolean("built", false);
            boolean fightActive = y.getBoolean("fight-active", false);
            a.deco = new DecoManager(a, plugin.settings());
            arenas.put(id, a);
            plugin.getLogger().info("Dojo geladen: " + a);
            if (fightActive) {
                plugin.getLogger().warning("Dojo '" + id + "': Ein Kampf wurde nicht sauber beendet (Absturz?) – stelle den Zustand vor dem Kampf wieder her.");
                recoverAfterCrash(a);
            } else if (!a.built) {
                plugin.getLogger().warning("Dojo '" + id + "' war nicht fertig gebaut – baue es jetzt fertig.");
                World w = a.world();
                if (w != null) {
                    a.state = Arena.State.BUILDING;
                    runJob(a, new BuildJob(a, false, 20000, 1, 20, null, () -> {
                        a.built = true;
                        a.state = Arena.State.IDLE;
                        save(a);
                        a.deco.ensure();
                    }));
                }
            }
        }
    }

    public void save(Arena a) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("id", a.id);
        y.set("world", a.worldName);
        y.set("x", a.ox);
        y.set("y", a.oy);
        y.set("z", a.oz);
        y.set("built", a.built);
        y.set("fight-active", a.state == Arena.State.FIGHT || a.state == Arena.State.REBUILDING);
        y.set("version", 1);
        try {
            a.dir().mkdirs();
            y.save(new File(a.dir(), "arena.yml"));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Dojo " + a.id + " nicht speichern", ex);
        }
    }

    /** Neues Dojo anlegen (noch nicht gebaut). */
    public Arena create(String id, Location origin) {
        id = id.toLowerCase();
        File dir = new File(root, id);
        Arena a = new Arena(id, origin.getWorld().getName(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), plan(), dir);
        a.deco = new DecoManager(a, plugin.settings());
        arenas.put(id, a);
        return a;
    }

    /** Arena vergessen und Dateien löschen. */
    public void forget(Arena a) {
        a.deco.despawnAll();
        arenas.remove(a.id);
        jobs.remove(a);
        File[] files = a.dir().listFiles();
        if (files != null) for (File f : files) f.delete();
        a.dir().delete();
    }

    // ------------------------------------------------------------------ Absturz-Wiederherstellung

    private void recoverAfterCrash(Arena a) {
        World w = a.world();
        if (w == null) {
            plugin.getLogger().warning("Welt '" + a.worldName + "' fehlt – Wiederherstellung von " + a.id + " beim nächsten Start.");
            return;
        }
        try {
            BlockSnapshot snap = BlockSnapshot.load(a.fightFile());
            a.state = Arena.State.REBUILDING;
            addTickets(a);
            runJob(a, new RestoreJob(w, snap, plugin.settings().restorePerTick, () -> {
                a.state = Arena.State.IDLE;
                save(a);
                removeTickets(a);
                a.deco.showAll();
                plugin.getLogger().info("Dojo '" + a.id + "' wiederhergestellt.");
            }));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Snapshot von " + a.id + " nicht lesbar – Arena bleibt wie sie ist.", ex);
            a.state = Arena.State.IDLE;
            save(a);
        }
    }

    // ------------------------------------------------------------------ Jobs & Ticks

    public void runJob(Arena a, Job job) {
        Job old = jobs.put(a, job);
        if (old != null) old.finishNow();
    }

    public Job job(Arena a) {
        return jobs.get(a);
    }

    public void tick() {
        for (Map.Entry<Arena, Job> e : new ArrayList<>(jobs.entrySet())) {
            Arena a = e.getKey();
            Job j = e.getValue();
            boolean done;
            try {
                done = j.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Fehler in Aufgabe für " + a.id + " – beende sie sofort.", ex);
                try {
                    j.finishNow();
                } catch (RuntimeException ignored) {
                }
                done = true;
            }
            if (done && jobs.get(a) == j) jobs.remove(a);
        }
        for (Arena a : arenas.values()) {
            a.deco.tick();
            a.deco.postTick();
        }
    }

    /** Beim Abschalten: alle Aufgaben sofort fertig machen. */
    public void finishAllJobs() {
        for (Job j : new ArrayList<>(jobs.values())) {
            try {
                j.finishNow();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Aufgabe konnte nicht beendet werden", ex);
            }
        }
        jobs.clear();
    }

    public void despawnAllDeco() {
        for (Arena a : arenas.values()) a.deco.despawnAll();
    }

    // ------------------------------------------------------------------ Chunks & Aufräumen

    public void addTickets(Arena a) {
        World w = a.world();
        if (w == null) return;
        for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++)
            for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++)
                w.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
    }

    public void removeTickets(Arena a) {
        World w = a.world();
        if (w == null) return;
        for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++)
            for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++)
                if (w.isChunkLoaded(cx, cz)) w.getChunkAt(cx, cz).removePluginChunkTicket(plugin);
    }

    /** Liegengebliebene Items, Pfeile und XP in der Arena entfernen. */
    public void clearDrops(Arena a) {
        World w = a.world();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(a.center(), 30, 25, 34)) {
            if (!a.inRegion(e.getLocation())) continue;
            if (e instanceof Item || e instanceof AbstractArrow || e instanceof ExperienceOrb) e.remove();
        }
    }

    public boolean isBusy(Arena a) {
        return jobs.containsKey(a) || a.state != Arena.State.IDLE;
    }

    public static boolean worldExists(String name) {
        return Bukkit.getWorld(name) != null;
    }
}
