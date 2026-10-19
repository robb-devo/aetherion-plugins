package de.aetherion.items.combat.system;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code combat-system.yml}, read once per reload into plain fields. Keys missing from the server copy
 * fall back to the jar defaults, so new knobs ship without touching the live file.
 */
public final class CombatConfig {

    static final String FILE = "combat-system.yml";

    public enum BossParry { NEGATE, GUARD, OFF }

    final boolean enabled;
    final Set<String> disabledWorlds;
    final boolean requireWeapon;

    final double staminaMax;
    final double regenPerTick;
    final int regenDelay;
    final int exhaustedTicks;
    final int hideAfterFull;

    final boolean dash;
    final double dashCost;
    final int dashCooldown;
    final double dashSpeed;
    final double dashLift;
    final int sprintGrace;
    final int strikeWindow;
    final double strikeMult;

    final boolean roll;
    final double rollCost;
    final int rollIframes;
    final int rollRecovery;
    final double rollSpeed;
    final double rollLift;
    final int rollChainWindow;
    final int rollFatigueIframes;
    final double rollFatigueCostMult;
    final double perfectDodgeRefund;
    final boolean rollBosses;

    final boolean parry;
    final double parryCost;
    final int perfectTicks;
    final int guardTicks;
    final double guardTaken;
    final int whiffLockout;
    final double parryRefund;
    final int riposteWindow;
    final double riposteMult;
    final int staggerTicks;
    final int vulnerableTicks;
    final double vulnerableMult;
    final boolean reflectProjectiles;
    final BossParry bossParry;

    final boolean melee;
    final double timedCharge;
    final double lightFloor;
    final double aerialMult;
    final int comboWindow;
    final int finisherHits;
    final double finisherMult;
    final double maxMult;
    final double bossBonusScale;
    final List<String> exemptItems;

    final double intensity;
    final int hitstopTicks;
    final boolean callouts;
    final boolean hurtCam;
    final boolean screenKick;
    final boolean screenGlyphs;

    final boolean tutorial;

    final List<String> dummyPattern;
    final int dummyWindup;
    final int dummySlamWindup;
    final int dummyGap;
    final double dummyReach;
    final double dummyHitShare;
    final int dummyLifetimeSeconds;

    private CombatConfig(YamlConfiguration y) {
        enabled = y.getBoolean("enabled", true);
        disabledWorlds = y.getStringList("disabled-worlds").stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        requireWeapon = y.getBoolean("require-weapon", true);

        ConfigurationSection s = section(y, "stamina");
        staminaMax = Math.max(1.0, s.getDouble("max", 100));
        regenPerTick = Math.max(0.0, s.getDouble("regen-per-second", 34)) / 20.0;
        regenDelay = Math.max(0, s.getInt("regen-delay-ticks", 16));
        exhaustedTicks = Math.max(0, s.getInt("exhausted-ticks", 28));
        hideAfterFull = Math.max(0, s.getInt("hide-after-full-ticks", 60));

        ConfigurationSection d = section(y, "dash");
        dash = d.getBoolean("enabled", true);
        dashCost = Math.max(0.0, d.getDouble("cost", 22));
        dashCooldown = Math.max(1, d.getInt("cooldown-ticks", 16));
        dashSpeed = clamp(d.getDouble("speed", 1.15), 0.1, 2.5);
        dashLift = clamp(d.getDouble("lift", 0.12), 0.0, 0.5);
        sprintGrace = Math.max(0, d.getInt("sprint-grace-ticks", 4));
        strikeWindow = Math.max(0, d.getInt("strike-window-ticks", 16));
        strikeMult = Math.max(1.0, d.getDouble("strike-mult", 1.15));

        ConfigurationSection r = section(y, "roll");
        roll = r.getBoolean("enabled", true);
        rollCost = Math.max(0.0, r.getDouble("cost", 26));
        rollIframes = clampInt(r.getInt("iframe-ticks", 7), 0, 20);
        rollRecovery = Math.max(rollIframes, r.getInt("recovery-ticks", 12));
        rollSpeed = clamp(r.getDouble("speed", 0.62), 0.1, 2.0);
        rollLift = clamp(r.getDouble("lift", 0.1), 0.0, 0.5);
        rollChainWindow = Math.max(0, r.getInt("chain-window-ticks", 20));
        rollFatigueIframes = clampInt(r.getInt("fatigue-iframe-ticks", 3), 0, rollIframes);
        rollFatigueCostMult = Math.max(1.0, r.getDouble("fatigue-cost-mult", 1.6));
        perfectDodgeRefund = Math.max(0.0, r.getDouble("perfect-refund", 14));
        rollBosses = r.getBoolean("bosses", true);

        ConfigurationSection p = section(y, "parry");
        parry = p.getBoolean("enabled", true);
        parryCost = Math.max(0.0, p.getDouble("cost", 12));
        perfectTicks = clampInt(p.getInt("perfect-ticks", 6), 1, 20);
        guardTicks = clampInt(p.getInt("guard-ticks", 12), 0, 40);
        guardTaken = clamp(p.getDouble("guard-taken", 0.5), 0.0, 1.0);
        whiffLockout = Math.max(0, p.getInt("whiff-lockout-ticks", 14));
        parryRefund = Math.max(0.0, p.getDouble("success-refund", 22));
        riposteWindow = Math.max(0, p.getInt("riposte-window-ticks", 30));
        riposteMult = Math.max(1.0, p.getDouble("riposte-mult", 1.5));
        staggerTicks = Math.max(0, p.getInt("stagger-ticks", 30));
        vulnerableTicks = Math.max(0, p.getInt("vulnerable-ticks", 50));
        vulnerableMult = Math.max(1.0, p.getDouble("vulnerable-mult", 1.25));
        reflectProjectiles = p.getBoolean("reflect-projectiles", true);
        bossParry = parseBossParry(p.getString("bosses", "negate"));

        ConfigurationSection m = section(y, "melee");
        melee = m.getBoolean("enabled", true);
        timedCharge = clamp(m.getDouble("timed-charge", 0.9), 0.1, 1.0);
        lightFloor = clamp(m.getDouble("light-floor", 0.2), 0.0, 1.0);
        aerialMult = Math.max(1.0, m.getDouble("aerial-mult", 1.25));
        comboWindow = Math.max(1, m.getInt("combo-window-ticks", 34));
        finisherHits = clampInt(m.getInt("finisher-hits", 3), 2, 10);
        finisherMult = Math.max(1.0, m.getDouble("finisher-mult", 1.35));
        maxMult = Math.max(1.0, m.getDouble("max-mult", 2.4));
        bossBonusScale = clamp(m.getDouble("boss-bonus-scale", 0.5), 0.0, 1.0);
        exemptItems = m.getStringList("exempt-items").stream()
                .map(id -> id.toLowerCase(Locale.ROOT)).filter(id -> !id.isBlank()).toList();

        ConfigurationSection f = section(y, "feedback");
        intensity = clamp(f.getDouble("intensity", 1.0), 0.0, 1.0);
        hitstopTicks = clampInt(f.getInt("hitstop-ticks", 2), 0, 4);
        callouts = f.getBoolean("callouts", true);
        hurtCam = f.getBoolean("hurt-cam", true);
        screenKick = f.getBoolean("screen-kick", true);
        screenGlyphs = f.getBoolean("screen-glyphs", true);

        tutorial = section(y, "tutorial").getBoolean("enabled", true);

        ConfigurationSection dm = section(y, "dummy");
        List<String> pattern = dm.getStringList("pattern").stream()
                .map(move -> move.toLowerCase(Locale.ROOT)).filter(move -> move.equals("jab") || move.equals("slam")).toList();
        dummyPattern = pattern.isEmpty() ? List.of("jab", "jab", "slam") : pattern;
        dummyWindup = clampInt(dm.getInt("windup-ticks", 20), 6, 60);
        dummySlamWindup = clampInt(dm.getInt("slam-windup-ticks", dummyWindup + 8), 6, 80);
        dummyGap = clampInt(dm.getInt("gap-ticks", 34), 10, 200);
        dummyReach = clamp(dm.getDouble("reach", 3.6), 1.5, 8.0);
        dummyHitShare = clamp(dm.getDouble("hit-share", 0.12), 0.0, 0.5);
        dummyLifetimeSeconds = clampInt(dm.getInt("lifetime-seconds", 900), 30, 7200);
    }

    /** Server copy merged over the jar defaults; writes the default file on first start. */
    static CombatConfig load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(FILE)) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("combat-system.yml defaults unreadable: " + ex.getMessage());
        }
        return new CombatConfig(yaml);
    }

    boolean worldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    boolean exempt(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        for (String prefix : exemptItems) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static ConfigurationSection section(YamlConfiguration y, String path) {
        ConfigurationSection s = y.getConfigurationSection(path);
        return s != null ? s : y.createSection(path);
    }

    private static BossParry parseBossParry(String raw) {
        try {
            return BossParry.valueOf(raw == null ? "NEGATE" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossParry.NEGATE;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
