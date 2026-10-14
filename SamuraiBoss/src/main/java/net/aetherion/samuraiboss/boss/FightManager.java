package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Hält alle laufenden Kämpfe (höchstens einer pro Dojo) und tickt sie. */
public final class FightManager {

    private final SamuraiBossPlugin plugin;
    private final Map<String, Fight> fights = new LinkedHashMap<>();

    public FightManager(SamuraiBossPlugin plugin) {
        this.plugin = plugin;
    }

    /** Startet einen Kampf. @return Fehlermeldung oder null. */
    public String start(Arena a) {
        if (fights.containsKey(a.id)) return "In diesem Dojo läuft bereits ein Kampf.";
        if (!a.built) return "Das Dojo ist noch nicht fertig gebaut.";
        if (plugin.arenas().isBusy(a)) return "Das Dojo ist gerade beschäftigt (" + a.state + ").";
        if (a.world() == null) return "Die Welt '" + a.worldName + "' ist nicht geladen.";
        Fight f = new Fight(plugin, a);
        String err;
        try {
            err = f.start();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kampf konnte nicht gestartet werden", ex);
            try {
                f.abort(true);
            } catch (RuntimeException ignored) {
            }
            return "Interner Fehler beim Start (siehe Konsole).";
        }
        if (err != null) return err;
        fights.put(a.id, f);
        return null;
    }

    public void tick() {
        if (fights.isEmpty()) return;
        for (Fight f : new ArrayList<>(fights.values())) {
            try {
                f.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Kampf in " + f.arena().id + " abgestürzt – wird zurückgesetzt", ex);
                try {
                    f.abort(true);
                } catch (RuntimeException ignored) {
                }
            }
            if (f.state() == Fight.State.ENDED) fights.remove(f.arena().id);
        }
    }

    public Fight get(Arena a) {
        return fights.get(a.id);
    }

    public Collection<Fight> all() {
        return fights.values();
    }

    /** Kampf, zu dem eine Boss-Hitbox gehört. */
    public Fight byHitbox(Entity e) {
        String id = Keys.arena(e);
        if (id == null) return null;
        Fight f = fights.get(id);
        if (f == null || f.boss() == null || !f.boss().isHitbox(e)) return null;
        return f;
    }

    public Fight byParticipant(Player p) {
        for (Fight f : fights.values()) if (f.isParticipant(p)) return f;
        return null;
    }

    /** Server-Stopp/Reload: alles sofort beenden und Dojos exakt wiederherstellen. */
    public void shutdown() {
        for (Fight f : new ArrayList<>(fights.values())) {
            try {
                f.abort(true);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Beenden des Kampfes in " + f.arena().id, ex);
            }
        }
        fights.clear();
    }
}
