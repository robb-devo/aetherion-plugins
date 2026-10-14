package net.aetherion.samuraiboss;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Typisierte Sicht auf die config.yml. Ein Kampf bekommt beim Start eine Referenz auf die
 * aktuelle Instanz – ein /reload ändert laufende Kämpfe also nicht mittendrin.
 */
public final class Settings {

    // boss
    public final double health;
    public final double healthPerExtraPlayer;
    public final float scale;
    public final EntityType hitboxEntity;
    public final double hitboxScale;
    public final double walkSpeed;
    public final double movementAttribute;
    public final boolean ignoreCreative;
    public final double damageReduction;

    // phases
    public final double arashiAt;
    public final double sangeAt;
    public final double ultimateAt;
    public final int ultimateTimeout;
    public final int exhaustedTicks;
    public final double exhaustedDamageMultiplier;

    public final boolean introEnabled;

    // damage
    private final Map<String, Double> damage = new HashMap<>();
    public final int tornadoBlindTicks;
    public final boolean ultimateLethal;
    public final double damageMultiplier;

    // cooldowns
    public final int globalCooldownMin;
    public final int globalCooldownMax;
    private final Map<String, Integer> cooldowns = new HashMap<>();

    // arena
    public final boolean protectDuringFight;
    public final int ruinSeconds;
    public final int rebuildPerTick;
    public final int rebuildFlightTicks;
    public final boolean buildAnimated;
    public final int buildPerTick;
    public final int emptyResetSeconds;
    public final boolean clearDropsOnReset;
    public final boolean bellChallenge;
    public final int restorePerTick;
    public final boolean keepInventory;

    // performance
    public final int modelUpdateInterval;
    public final int maxEffectDisplays;
    public final int maxDebrisDisplays;
    public final int ambientPetals;
    public final float effectDensity;
    public final boolean floatingDebris;

    // effects
    public final boolean screenShake;
    public final boolean sky;
    public final boolean redTint;
    public final boolean music;
    public final float musicVolume;
    public final float soundVolume;
    public final boolean particles;
    public final boolean titles;

    // resource pack
    public final boolean packEnabled;
    public final Material packBaseMaterial;
    private final Map<String, Integer> modelData = new HashMap<>();
    public final String packFont;
    public final boolean customSounds;

    // rewards
    public final List<String> rewardCommands;
    public final boolean broadcastVictory;

    // messages — English default; German only when player chose /language de
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, String> messagesDe = new HashMap<>();

    public Settings(FileConfiguration c, Logger log) {
        health = Math.max(1, c.getDouble("boss.health", 18000));
        healthPerExtraPlayer = Math.max(0, c.getDouble("boss.health-per-extra-player", 7000));
        scale = (float) clamp(c.getDouble("boss.scale", 1.0), 0.4, 3.0);
        hitboxEntity = parseEntity(c.getString("boss.hitbox-entity", "IRON_GOLEM"), log);
        hitboxScale = clamp(c.getDouble("boss.hitbox-scale", 0.92), 0.2, 4.0);
        walkSpeed = clamp(c.getDouble("boss.walk-speed", 1.0), 0.1, 3.0);
        movementAttribute = clamp(c.getDouble("boss.movement-attribute", 0.27), 0.05, 1.0);
        ignoreCreative = c.getBoolean("boss.ignore-creative", true);
        damageReduction = clamp(c.getDouble("boss.damage-reduction", 0.35), 0.0, 0.95);

        arashiAt = clamp(c.getDouble("phases.arashi-at", 0.66), 0.05, 0.99);
        sangeAt = clamp(c.getDouble("phases.sange-at", 0.33), 0.04, arashiAt - 0.01);
        ultimateAt = clamp(c.getDouble("phases.ultimate-at", 0.12), 0.01, sangeAt - 0.01);
        ultimateTimeout = Math.max(0, c.getInt("phases.ultimate-timeout", 1400));
        exhaustedTicks = Math.max(20, c.getInt("phases.exhausted-ticks", 120));
        exhaustedDamageMultiplier = clamp(c.getDouble("phases.exhausted-damage-multiplier", 1.5), 0.1, 10);

        introEnabled = c.getBoolean("intro.enabled", true);

        String[] dmgKeys = {"issen", "kesagiri", "kazekiri", "utsusemi", "tsubame", "thousand-blades", "tornado",
                "shadow-clone", "star", "blade-rain", "afterimage", "ultimate"};
        double[] dmgDefaults = {8, 7, 6, 6, 6.5, 4, 2.5, 5, 7, 5, 6, 16};
        for (int i = 0; i < dmgKeys.length; i++) {
            damage.put(dmgKeys[i], Math.max(0, c.getDouble("damage." + dmgKeys[i], dmgDefaults[i])));
        }
        tornadoBlindTicks = Math.max(0, c.getInt("damage.tornado-blind-ticks", 50));
        ultimateLethal = c.getBoolean("damage.ultimate-lethal", false);
        damageMultiplier = clamp(c.getDouble("damage.multiplier", 1.0), 0.0, 20.0);

        globalCooldownMin = Math.max(5, c.getInt("cooldowns.global-min", 28));
        globalCooldownMax = Math.max(globalCooldownMin, c.getInt("cooldowns.global-max", 55));
        String[] cdKeys = {"issen", "kesagiri", "kazekiri", "utsusemi", "tsubame", "thousand-blades", "tornado",
                "shadow-clones", "triple-issen", "star", "blade-rain", "afterimage"};
        int[] cdDefaults = {150, 50, 110, 220, 90, 320, 380, 420, 260, 360, 320, 300};
        for (int i = 0; i < cdKeys.length; i++) {
            cooldowns.put(cdKeys[i], Math.max(0, c.getInt("cooldowns." + cdKeys[i], cdDefaults[i])));
        }

        protectDuringFight = c.getBoolean("arena.protect-during-fight", true);
        ruinSeconds = Math.max(0, c.getInt("arena.ruin-seconds", 6));
        rebuildPerTick = Math.max(1, c.getInt("arena.rebuild-per-tick", 18));
        rebuildFlightTicks = Math.max(4, Math.min(59, c.getInt("arena.rebuild-flight-ticks", 26)));
        buildAnimated = c.getBoolean("arena.build-animated", true);
        buildPerTick = Math.max(1, c.getInt("arena.build-per-tick", 14));
        emptyResetSeconds = Math.max(5, c.getInt("arena.empty-reset-seconds", 30));
        clearDropsOnReset = c.getBoolean("arena.clear-drops-on-reset", true);
        bellChallenge = c.getBoolean("arena.bell-challenge", false);
        restorePerTick = Math.max(200, c.getInt("arena.restore-per-tick", 4000));
        keepInventory = c.getBoolean("arena.keep-inventory", true);

        modelUpdateInterval = Math.max(1, Math.min(4, c.getInt("performance.model-update-interval", 2)));
        maxEffectDisplays = Math.max(100, c.getInt("performance.max-effect-displays", 420));
        maxDebrisDisplays = Math.max(0, c.getInt("performance.max-debris-displays", 520));
        ambientPetals = Math.max(0, Math.min(200, c.getInt("performance.ambient-petals", 28)));
        effectDensity = (float) clamp(c.getDouble("performance.effect-density", 0.55), 0.3, 1.0);
        floatingDebris = c.getBoolean("performance.floating-debris", true);

        screenShake = c.getBoolean("effects.screen-shake", true);
        sky = c.getBoolean("effects.sky", true);
        redTint = c.getBoolean("effects.red-tint", true);
        music = c.getBoolean("effects.music", true);
        musicVolume = (float) clamp(c.getDouble("effects.music-volume", 0.85), 0.0, 2.0);
        soundVolume = (float) clamp(c.getDouble("effects.sound-volume", 1.0), 0.0, 2.0);
        particles = c.getBoolean("effects.particles", true);
        titles = c.getBoolean("effects.titles", true);

        packEnabled = c.getBoolean("resource-pack.enabled", false);
        Material base = Material.matchMaterial(c.getString("resource-pack.base-material", "PINK_DYE"));
        if (base == null || !base.isItem()) {
            log.warning("resource-pack.base-material ungültig – verwende PINK_DYE");
            base = Material.PINK_DYE;
        }
        packBaseMaterial = base;
        String[] md = {"katana", "katana-ghost", "saya", "petal", "petal-white", "slash", "slash-pink", "seal", "seal-ring",
                "mon", "crest", "menpo", "moon", "hilt"};
        for (int i = 0; i < md.length; i++) {
            modelData.put(md[i], c.getInt("resource-pack.model-data." + md[i], 7310001 + i));
        }
        packFont = c.getString("resource-pack.font", "samurai:kanji");
        customSounds = c.getBoolean("resource-pack.custom-sounds", true);

        rewardCommands = new ArrayList<>(c.getStringList("rewards.commands"));
        broadcastVictory = c.getBoolean("rewards.broadcast", true);

        ConfigurationSection ms = c.getConfigurationSection("messages");
        if (ms != null) {
            for (String k : ms.getKeys(false)) messages.put(k, ms.getString(k, ""));
        }
        ConfigurationSection msDe = c.getConfigurationSection("messages-de");
        if (msDe != null) {
            for (String k : msDe.getKeys(false)) messagesDe.put(k, msDe.getString(k, ""));
        }
    }

    public double damage(String key) {
        return damage.getOrDefault(key, 5.0) * damageMultiplier;
    }

    public int cooldown(String key) {
        return cooldowns.getOrDefault(key, 200);
    }

    public int modelData(String key) {
        return modelData.getOrDefault(key, 0);
    }

    /** English / server default. */
    public String msg(String key, String def) {
        return messages.getOrDefault(key, def);
    }

    /** Per-player: German only when Aetherion language preference is DE. */
    public String msg(Player player, String key, String def) {
        if (player != null && Lang.isGerman(player)) {
            String de = messagesDe.get(key);
            if (de != null && !de.isEmpty()) {
                return de;
            }
        }
        return msg(key, def);
    }

    private static EntityType parseEntity(String s, Logger log) {
        try {
            EntityType t = EntityType.valueOf(s.toUpperCase(Locale.ROOT));
            if (t.getEntityClass() != null && org.bukkit.entity.Mob.class.isAssignableFrom(t.getEntityClass())) return t;
            log.warning("boss.hitbox-entity muss ein Mob sein – verwende IRON_GOLEM");
        } catch (IllegalArgumentException ex) {
            log.warning("boss.hitbox-entity unbekannt: " + s + " – verwende IRON_GOLEM");
        }
        return EntityType.IRON_GOLEM;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
