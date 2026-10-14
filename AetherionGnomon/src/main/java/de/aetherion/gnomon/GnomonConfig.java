package de.aetherion.gnomon;

import de.aetherion.gnomon.ai.BrainConfig;
import de.aetherion.gnomon.ai.MoveSpec;
import de.aetherion.gnomon.ai.Moves;
import de.aetherion.gnomon.ai.Posture;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.rig.Tripod;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Typed view of config.yml. Read once per fight (an encounter keeps the instance it started with).
 */
public final class GnomonConfig {

    private final FileConfiguration c;
    private final List<MoveSpec> moves;

    public GnomonConfig(FileConfiguration c, Logger log) {
        this.c = c;
        this.moves = loadMoves(log);
    }

    public double d(String path, double def) {
        return c.getDouble(path, def);
    }

    public int i(String path, int def) {
        return c.getInt(path, def);
    }

    public boolean b(String path, boolean def) {
        return c.getBoolean(path, def);
    }

    /* ------------------------------------------------------------------ arena */

    /** The configured arena center, or null when no world is set / loaded. */
    public Location arenaCenter() {
        String name = c.getString("arena.world", "");
        if (name == null || name.isBlank()) {
            return null;
        }
        World w = Bukkit.getWorld(name);
        if (w == null) {
            return null;
        }
        return new Location(w, c.getDouble("arena.x"), c.getDouble("arena.y"), c.getDouble("arena.z"));
    }

    public double arenaRadius() {
        return Math.max(12, c.getDouble("arena.radius", 24));
    }

    public double leaveRadius() {
        return Math.max(arenaRadius() + 6, c.getDouble("arena.leave-radius", 40));
    }

    public boolean seal() {
        return c.getBoolean("arena.seal", true);
    }

    public boolean night() {
        return c.getBoolean("arena.night", true);
    }

    /* ------------------------------------------------------------------ rig */

    public Tripod.Spec rig() {
        return new Tripod.Spec(
                d("rig.hub-height", 13.0),
                d("rig.hip-radius", 1.3),
                d("rig.thigh", 8.5),
                d("rig.shin", 9.5),
                d("rig.stance", 6.5),
                d("rig.step-threshold", 3.2),
                i("rig.step-ticks", 12),
                d("rig.step-lift", 3.5),
                d("rig.max-speed", 0.17),
                d("rig.turn-speed", 2.2),
                d("rig.bob-rest-height", 3.0),
                d("rig.pendulum-gravity", 0.075),
                d("rig.pendulum-damping", 0.985),
                Math.max(1, i("rig.update-interval", 1))
        );
    }

    public float viewRange() {
        return (float) d("rig.view-range", 1.6);
    }

    public double hitRecoil() {
        return d("rig.hit-recoil", 0.10);
    }

    /* ------------------------------------------------------------------ combat */

    public double damage(String key, double def) {
        return d("damage." + key, def);
    }

    public int telegraph(String key, int def) {
        return i("telegraph." + key, def);
    }

    public int minTelegraph() {
        return Math.max(6, i("fairness.min-telegraph-ticks", 16));
    }

    public int minCommit() {
        return Math.max(4, i("fairness.min-commit-ticks", 8));
    }

    public double jumpClearance() {
        return d("fairness.jump-clearance", 1.05);
    }

    /* ------------------------------------------------------------------ ai */

    public BrainConfig brain() {
        return new BrainConfig(
                d("ai.memory-half-life", 40),
                d("ai.memory-half-life-verdict", 70),
                d("ai.notice", 0.35),
                d("ai.exploit", 0.55),
                d("ai.forget", 0.25),
                d("ai.teach-seconds", 35),
                d("ai.min-dodges", 4),
                d("ai.min-orbit-seconds", 6),
                d("ai.min-range-seconds", 6),
                d("ai.min-opening-hits", 4),
                d("ai.min-sector-seconds", 20),
                d("ai.outplayed-forgetting", 0.45),
                d("ai.temperature", 0.12),
                d("ai.repeat-penalty", 0.45),
                d("ai.focus-min-seconds", 5),
                d("ai.focus-max-seconds", 14),
                d("ai.posture-min-seconds", 8),
                i("ai.breath-every", 4),
                5.5,
                13.0,
                i("ledger.sample-ticks", 8),
                d("ledger.min-step", 1.6),
                0L
        );
    }

    public int gap(Posture posture) {
        return switch (posture) {
            case OBSERVE -> i("ai.gap-observe", 30);
            case PRESS -> i("ai.gap-press", 14);
            case DENY -> i("ai.gap-deny", 18);
            case PREY -> i("ai.gap-prey", 20);
        };
    }

    public int breathTicks() {
        return i("ai.breath-ticks", 40);
    }

    public List<MoveSpec> moves() {
        return moves;
    }

    /**
     * Defaults from {@link Moves}, overridden per move by {@code moves.<ID>.base / cooldown / reads.<READ>}.
     */
    private List<MoveSpec> loadMoves(Logger log) {
        List<MoveSpec> out = new ArrayList<>();
        ConfigurationSection root = c.getConfigurationSection("moves");
        for (MoveSpec def : Moves.defaults()) {
            ConfigurationSection s = root == null ? null : root.getConfigurationSection(def.id());
            if (s == null) {
                out.add(def);
                continue;
            }
            Map<Read, Double> reads = new EnumMap<>(Read.class);
            reads.putAll(def.reads());
            ConfigurationSection r = s.getConfigurationSection("reads");
            if (r != null) {
                for (String key : r.getKeys(false)) {
                    try {
                        reads.put(Read.valueOf(key.toUpperCase(Locale.ROOT)), r.getDouble(key));
                    } catch (IllegalArgumentException e) {
                        log.warning("[Gnomon] moves." + def.id() + ".reads." + key + ": unknown read");
                    }
                }
            }
            Map<Posture, Double> postures = new EnumMap<>(Posture.class);
            postures.putAll(def.postures());
            ConfigurationSection p = s.getConfigurationSection("postures");
            if (p != null) {
                for (String key : p.getKeys(false)) {
                    try {
                        postures.put(Posture.valueOf(key.toUpperCase(Locale.ROOT)), p.getDouble(key));
                    } catch (IllegalArgumentException e) {
                        log.warning("[Gnomon] moves." + def.id() + ".postures." + key + ": unknown posture");
                    }
                }
            }
            out.add(new MoveSpec(def.id(), def.family(),
                    s.getDouble("base", def.base()),
                    s.getInt("cooldown", def.cooldownTicks()),
                    def.minPhase(),
                    Map.copyOf(reads),
                    Map.copyOf(postures),
                    def.minDistance(), def.maxDistance(), def.requires(), def.teaching(),
                    def.closeCrowd(), def.farCrowd()));
        }
        return out;
    }

    public boolean logDecisions() {
        return b("debug.log-decisions", false);
    }
}
