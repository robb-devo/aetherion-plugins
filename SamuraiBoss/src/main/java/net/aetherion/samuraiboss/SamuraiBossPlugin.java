package net.aetherion.samuraiboss;

import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.arena.ArenaManager;
import net.aetherion.samuraiboss.arena.DecoManager;
import net.aetherion.samuraiboss.boss.FightManager;
import net.aetherion.samuraiboss.boss.SamuraiAnimations;
import net.aetherion.samuraiboss.boss.SamuraiModel;
import net.aetherion.samuraiboss.command.SamuraiCommand;
import net.aetherion.samuraiboss.listener.FightListener;
import net.aetherion.samuraiboss.listener.ProtectionListener;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * SamuraiBoss – 桜木 刃斎 (Sakuragi Jinsai), 散華の剣聖.
 *
 * <p>Einstiegspunkt des Plugins. Verdrahtet Konfiguration, Dojos, Kämpfe, Befehle und Listener
 * und treibt alles über EINEN globalen Tick-Task an (Reihenfolge: Kämpfe → Dojo-Aufgaben/Deko).</p>
 *
 * <p>Abhängigkeiten: nur die Paper-API 1.21.1. Kein ModelEngine, kein MythicMobs,
 * kein PacketEvents – alles läuft über Display-Entities.</p>
 */
public final class SamuraiBossPlugin extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenas;
    private FightManager fights;
    private SamuraiAnimations animations;
    private BukkitTask task;

    @Override
    public void onEnable() {
        long t0 = System.currentTimeMillis();
        Keys.init(this);
        Palette.init();
        saveDefaultConfig();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        // Modell & Animationen einmal vorberechnen (für alle Kämpfe gleich)
        animations = new SamuraiAnimations(SamuraiModel.def());

        sweepOrphans();
        arenas = new ArenaManager(this);
        fights = new FightManager(this);
        arenas.load();

        getServer().getPluginManager().registerEvents(new FightListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        SamuraiCommand cmd = new SamuraiCommand(this);
        PluginCommand pc = getCommand("samuraiboss");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        // Deko in bereits geladenen Chunks erscheinen lassen
        Bukkit.getScheduler().runTask(this, () -> {
            for (Arena a : arenas.all()) a.deco.ensure();
        });
        getLogger().info("桜木 刃斎 erwacht – " + arenas.all().size() + " Dojo(s) geladen ("
                + (System.currentTimeMillis() - t0) + " ms). Resource Pack: " + (settings.packEnabled ? "an" : "aus"));
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        try {
            if (fights != null) fights.shutdown();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Fehler beim Beenden der Kämpfe", ex);
        }
        if (arenas != null) {
            arenas.finishAllJobs();
            arenas.despawnAllDeco();
            for (Arena a : arenas.all()) {
                if (a.state == Arena.State.FIGHT || a.state == Arena.State.REBUILDING) a.state = Arena.State.IDLE;
                arenas.save(a);
                arenas.removeTickets(a);
            }
        }
        sweepOrphans();
    }

    private void tick() {
        try {
            fights.tick();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Fehler im Kampf-Tick", ex);
        }
        try {
            arenas.tick();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Fehler im Dojo-Tick", ex);
        }
    }

    /**
     * Entfernt alle Entities, die dieses Plugin markiert hat, aus geladenen Chunks. Unsere
     * Entities sind nicht persistent – nach einem Neustart gibt es sie ohnehin nicht mehr; das
     * hier fängt nur Sonderfälle wie Plugin-Reloads ab.
     */
    private void sweepOrphans() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (Keys.isOwned(e)) {
                    e.remove();
                    n++;
                }
            }
        }
        if (n > 0) getLogger().info(n + " verwaiste Boss-/Effekt-Entities entfernt.");
    }

    /** Konfiguration neu laden. Laufende Kämpfe behalten ihre Werte. */
    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        for (Arena a : arenas.all()) {
            if (a.state != Arena.State.IDLE) continue;
            a.deco.despawnAll();
            a.deco = new DecoManager(a, settings);
            a.deco.ensure();
        }
    }

    // ------------------------------------------------------------------ Zugriff

    public Settings settings() {
        return settings;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public FightManager fights() {
        return fights;
    }

    public SamuraiAnimations animations() {
        return animations;
    }

    // ------------------------------------------------------------------ Texte

    public Component msg(String key, String def) {
        return MiniMessage.miniMessage().deserialize(settings.msg("prefix", "") + settings.msg(key, def));
    }

    public Component prefixed(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(settings.msg("prefix", "") + miniMessage);
    }

    public Component msgPlain(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
