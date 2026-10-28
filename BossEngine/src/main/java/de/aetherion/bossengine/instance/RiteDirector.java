package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.fx.ShapeFx;
import de.aetherion.bossengine.instance.rite.RiteKit;
import de.aetherion.bossengine.skill.t2.T2Mechanics;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.Vfx;

import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encounter layer for the Borderlands rite bosses and the early dungeon floors.
 *
 * <p>The four rite bosses (Hollow Lurker, Bridge Troll, McNugget, Skuldugery) are run entirely by their
 * {@link RiteKit}: arrival, signature move, look. What stays here is the Prototype Sentinel's Overload Lance and
 * the intro card for Pathwarden and the Frostbound, whose own directors run the rest.
 */
final class RiteDirector {

    private static final Color WHITE = Color.fromRGB(0xFFFFFF);
    private static final int FLASH = 8;
    private static final double REACH = 28.0;

    private enum Kind {
        SENTINEL("&5&lPrototype Sentinel", "The Forge's First Draft", "Der erste Entwurf der Schmiede", ShapeFx.VOID, 240, 180),
        WARDEN("&3&lPathwarden", "Warden of the Path", "Wächter des Pfads", ShapeFx.SOUL, 0, 0),
        FROST("&b&lThe Frostbound", "Winter With a Grudge", "Ein Winter mit Groll", ShapeFx.ICE, 0, 0),
        NONE("", "", "", WHITE, 0, 0);

        final String name;
        final String epithetEn;
        final String epithetDe;
        final Color color;
        final int cooldown;
        final int cooldownLate;

        Kind(String name, String epithetEn, String epithetDe, Color color, int cooldown, int cooldownLate) {
            this.name = name;
            this.epithetEn = epithetEn;
            this.epithetDe = epithetDe;
            this.color = color;
            this.cooldown = cooldown;
            this.cooldownLate = cooldownLate;
        }

        static Kind of(String id) {
            return switch (id) {
                case "dungeon_sentinel" -> SENTINEL;
                case "pathwarden" -> WARDEN;
                case "dungeon_frostbound" -> FROST;
                default -> NONE;
            };
        }
    }

    private final BossInstance instance;
    private final List<Player> marked = new ArrayList<>();
    private Kind kind;
    private boolean introduced;
    private int cooldown = 100;
    private int windup = -1;
    private Location lanceFrom;
    private Location lanceTo;

    RiteDirector(BossInstance instance) {
        this.instance = instance;
    }

    /** Body bound; a fresh spawn plays the kit's arrival, an adopted body does not. */
    void onBind(boolean fresh) {
        RiteKit kit = RiteKit.of(instance);
        if (kit != null) {
            kit.bind(instance.getEntity(), fresh && instance.getSpawnCause() != SpawnCause.UNKNOWN);
        }
    }

    boolean isArriving() {
        RiteKit kit = RiteKit.owns(instance) ? RiteKit.of(instance) : null;
        return kit != null && kit.isArriving();
    }

    void tick() {
        RiteKit kit = RiteKit.of(instance);
        if (kit != null) {
            kit.tick();
            return;
        }
        if (kind == null) {
            kind = Kind.of(de.aetherion.bossengine.fx.CombatTheatrics.id(instance));
        }
        if (kind == Kind.NONE) {
            return;
        }
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid() || !instance.isAlive()) {
            reset();
            return;
        }
        if (!introduced) {
            introduced = intro(boss);
        }
        if (kind.cooldown == 0) {
            return;
        }
        if (instance.isTransitioning()) {
            reset();
            return;
        }
        if (windup >= 0) {
            charge(boss);
            if (windup-- == 0) {
                strike(boss);
                marked.clear();
                windup = -1;
            }
            return;
        }
        if (--cooldown <= 0) {
            cooldown = late() ? kind.cooldownLate : kind.cooldown;
            begin(boss);
        }
    }

    private void reset() {
        lanceTo = null;
        marked.clear();
        windup = -1;
    }

    private boolean late() {
        de.aetherion.bossengine.model.BossPhase phase = instance.getCurrentPhase();
        return phase != null && phase.getHealthPercent() < 100;
    }

    // intro ---------------------------------------------------------------------------------------------

    private boolean intro(LivingEntity boss) {
        List<Player> near = T2Mechanics.nearby(boss, 40);
        if (near.isEmpty()) {
            return false;
        }
        Location at = boss.getLocation();
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2400), Duration.ofMillis(900));
        for (Player p : near) {
            String epithet = AetherServices.german(p) ? kind.epithetDe : kind.epithetEn;
            p.showTitle(Title.title(TextUtil.component(kind.name), TextUtil.component("&7" + epithet), times));
            if (kind == Kind.WARDEN) {
                p.playSound(at, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.HOSTILE, 1.4f, 0.5f);
                p.playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 1.2f, 0.5f);
            } else {
                p.playSound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.35f, 1.6f);
                p.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.8f, 0.6f);
            }
        }
        if (kind != Kind.WARDEN) {
            Vfx.rune(at.clone().add(0, 0.1, 0), kind.color, 3.2, 50);
            Vfx.wave(at.clone().add(0, 0.15, 0), kind.color, 5.0, 16);
        }
        return true;
    }

    // Overload Lance -------------------------------------------------------------------------------------

    private void begin(LivingEntity boss) {
        List<Player> near = T2Mechanics.nearby(boss, REACH);
        if (near.isEmpty() || instance.isSlamPending()) {
            cooldown = 40;
            return;
        }
        if (kind != Kind.SENTINEL) {
            return;
        }
        Location at = boss.getLocation();
        marked.add(near.get(ThreadLocalRandom.current().nextInt(near.size())));
        lanceFrom = boss.getEyeLocation();
        lanceTo = null;
        boss.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.2f, 0.6f);
        boss.getWorld().playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 0.8f, 1.3f);
        cue(marked, "&5Overload Lance &7· step off the line", "&5Überlast-Lanze &7· runter von der Linie");
        windup = 24;
    }

    private void charge(LivingEntity boss) {
        if (windup == FLASH) {
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.HOSTILE, 0.9f, 1.9f);
        }
        if (windup > FLASH && !marked.isEmpty() && marked.get(0).isValid() && marked.get(0).getWorld() == boss.getWorld()) {
            // tracks until the flash, then the line is locked and only the step off it saves you
            lanceFrom = boss.getEyeLocation();
            Vector aim = marked.get(0).getLocation().add(0, 1.0, 0).toVector().subtract(lanceFrom.toVector());
            if (aim.lengthSquared() > 0.01) {
                lanceTo = lanceFrom.clone().add(aim.normalize().multiply(16));
            }
        }
        if (lanceTo != null) {
            Vfx.line(instance.getInstanceId() + ":lance", lanceFrom, lanceTo, windup <= FLASH ? WHITE : ShapeFx.VOID,
                    windup <= FLASH ? 0.12 : 0.05);
        }
    }

    private void strike(LivingEntity boss) {
        if (lanceTo == null) {
            return;
        }
        boolean late = late();
        World world = boss.getWorld();
        Vfx.beam(lanceFrom, lanceTo, ShapeFx.VOID, 0.6, 8);
        Vfx.burst(lanceTo, ShapeFx.VOID, 1.6, 8);
        world.playSound(lanceFrom, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 0.7f, 1.4f);
        world.playSound(lanceFrom, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.0f, 1.2f);
        Vector a = lanceFrom.toVector();
        Vector ab = lanceTo.toVector().subtract(a);
        double len2 = ab.lengthSquared();
        for (Player p : T2Mechanics.nearby(boss, 20)) {
            Vector ap = p.getLocation().add(0, 1.0, 0).toVector().subtract(a);
            double t = Math.max(0, Math.min(1, ap.dot(ab) / len2));
            if (ap.subtract(ab.clone().multiply(t)).lengthSquared() <= 1.3 * 1.3) {
                BossHits.peril(p, boss, instance.scaleDamage(late ? 30 : 26));
            }
        }
        lanceTo = null;
    }

    private static void cue(List<Player> players, String en, String de) {
        for (Player p : players) {
            p.sendActionBar(TextUtil.component(AetherServices.german(p) ? de : en));
        }
    }
}
