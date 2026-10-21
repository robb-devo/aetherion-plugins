package de.aetherion.items.combat.system;

import de.aetherion.core.api.ActionBarLane;
import de.aetherion.items.combat.DamageNumbers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sounds, glyphs, callouts and hitstop for the kit. Everything scales with {@code feedback.intensity};
 * at 0 the kit is silent and only the stamina gauge still pulses. Particles are small accents at the contact
 * point (the display slash, ground ring and screen glyph do the talking). No flashes, no screen effects.
 */
final class HitFeedback {

    static final TextColor GOLD = TextColor.color(0xFFC94A);
    static final TextColor STEEL = TextColor.color(0xCFE3F2);
    static final TextColor DODGE = TextColor.color(0x9FE3FF);
    static final TextColor FINISH = TextColor.color(0xFF9A3C);
    static final TextColor RIPOSTE = TextColor.color(0xE58BFF);
    static final TextColor WARN = TextColor.color(0xFF6B5A);


    private final CombatSystem system;
    /** Mobs held for a hitstop: knockback is parked for a couple of ticks, then given back. */
    private final Map<UUID, Hold> holds = new ConcurrentHashMap<>();

    HitFeedback(CombatSystem system) {
        this.system = system;
    }

    private double intensity() {
        return system.config().intensity;
    }

    /* =========================================================
     * RESOURCE
     * ========================================================= */

    void deny(Player player) {
        double k = intensity();
        if (k > 0.0) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, (float) (0.55 * k), 0.62f);
        }
    }

    void winded(Player player, int ticks) {
        if (ticks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0, true, false, false));
        }
        double k = intensity();
        if (k > 0.0) {
            player.playSound(player, Sound.ENTITY_HORSE_BREATHE, (float) (0.9 * k), 1.35f);
            player.playSound(player, Sound.ENTITY_PLAYER_HURT_DROWN, (float) (0.5 * k), 0.7f);
            player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, (float) (0.9 * k), 1.1f);
        }
        CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.WINDED);
        callout(player, "✖ WINDED ✖", "✖ ERSCHÖPFT ✖", WARN, 30);
    }

    /* =========================================================
     * GUARD
     * ========================================================= */

    void guardUp(Player player) {
        double k = intensity();
        if (k <= 0.0) {
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, (float) (0.7 * k), 1.55f);
    }

    void whiff(Player player) {
        double k = intensity();
        if (k > 0.0) {
            player.playSound(player, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, (float) (0.8 * k), 0.6f);
            player.playSound(player, Sound.BLOCK_CHAIN_FALL, (float) (0.6 * k), 0.6f);
        }
        callout(player, "WHIFF · guard locked", "DANEBEN · Block gesperrt", NamedTextColor.GRAY, 16);
    }

    /**
     * A hit caught in the perfect window. Loud on purpose: shield crack + bell + anvil + a high chime, a gold
     * and white spark burst with a flash at the contact point, a ring of dust, and a light camera kick.
     * Bosses get an extra low boom so a negate (no stagger) still reads as the biggest moment in the fight.
     */
    void perfectParry(Player player, Entity attacker, boolean boss) {
        double k = intensity();
        Location contact = contact(player, attacker);
        if (k > 0.0) {
            de.aetherion.core.api.Vfx.ring(player.getLocation(), org.bukkit.Color.fromRGB(0xFFD86B), 1.8, 10);
            de.aetherion.core.api.Vfx.sound(contact, de.aetherion.core.api.Vfx.COMBAT_RING,
                    org.bukkit.SoundCategory.PLAYERS, (float) (0.7 * k), 1.0f);
        }
        if (k > 0.0) {
            World world = player.getWorld();
            float v = (float) k;
            world.playSound(contact, Sound.ITEM_SHIELD_BLOCK, 1.2f * v, 1.5f);
            world.playSound(contact, Sound.BLOCK_BELL_USE, 0.9f * v, 1.9f);
            world.playSound(contact, Sound.BLOCK_ANVIL_LAND, 0.45f * v, 1.7f);
            world.playSound(contact, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f * v, 1.8f);
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f * v, 1.6f);
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f * v, 1.8f);
            if (boss) {
                world.playSound(contact, Sound.ENTITY_GENERIC_EXPLODE, 0.45f * v, 1.6f);
                world.playSound(contact, Sound.ITEM_TRIDENT_THUNDER, 0.35f * v, 1.9f);
            }
            // The gold ring on the ground and the glyph carry it; one small spark at the clash is enough.
            de.aetherion.core.api.Vfx.burst(contact, org.bukkit.Color.fromRGB(0xFFD86B), 1.4, 9);
            kick(player, attacker, 0.6);
            CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.PARRY);
        }
        callout(player, boss ? "⚔ PERFECT PARRY ⚔" : "✦ PERFECT PARRY ✦", boss ? "⚔ PERFEKT PARIERT ⚔" : "✦ PERFEKT PARIERT ✦",
                GOLD, 30);
        if (attacker != null && !(attacker instanceof Player)) {
            DamageNumbers.word(attacker, CombatSystem.t(player, "PARRY!", "PARIERT!"), GOLD, 1.25f);
        }
    }

    /** A hit caught after the perfect window: reduced, not negated. Steel scrape, sparks, "GUARD ½". */
    void guarded(Player player, Entity attacker) {
        double k = intensity();
        if (k <= 0.0) {
            return;
        }
        Location contact = contact(player, attacker);
        World world = player.getWorld();
        world.playSound(contact, Sound.ITEM_SHIELD_BLOCK, (float) (0.9 * k), 0.8f);
        world.playSound(contact, Sound.BLOCK_GRINDSTONE_USE, (float) (0.6 * k), 1.4f);
        de.aetherion.core.api.Vfx.spark(contact, org.bukkit.Color.fromRGB(0xCFE3F2), 0.8);
        CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.GUARD);
        callout(player, "GUARD ½", "GEBLOCKT ½", STEEL, 18);
    }

    void stagger(LivingEntity victim) {
        double k = intensity();
        if (k <= 0.0) {
            return;
        }
        Location head = victim.getLocation().add(0, victim.getHeight() + 0.25, 0);
        victim.getWorld().playSound(head, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, (float) (0.45 * k), 1.65f);
        de.aetherion.core.api.Vfx.spark(head, org.bukkit.Color.WHITE, 0.7);
    }

    /** Small orbit of sparks over a staggered mob, every few ticks while it lasts. */
    void staggerTick(LivingEntity victim, long tick) {
        if (intensity() <= 0.0 || tick % 4 != 0) {
            return;
        }
        double angle = (tick % 40) / 40.0 * Math.PI * 2;
        Location head = victim.getLocation().add(Math.cos(angle) * 0.4, victim.getHeight() + 0.3, Math.sin(angle) * 0.4);
        // dizzy stars: a twinkle walks round the head while the stagger lasts
        de.aetherion.core.api.Vfx.spark(head, org.bukkit.Color.fromRGB(0xFFF1A0), 0.35);
    }

    /* =========================================================
     * MOBILITY
     * ========================================================= */

    void dash(Player player) {
        double k = intensity();
        if (k <= 0.0) {
            return;
        }
        Location feet = player.getLocation().add(0, 0.15, 0);
        player.getWorld().playSound(feet, Sound.ENTITY_BREEZE_WIND_BURST, (float) (0.45 * k), 1.45f);
        Location from = feet.clone().add(0, 0.75, 0);
        de.aetherion.core.api.Vfx.streak(from, from.clone().add(CombatSystem.facing(player).multiply(3.5)), org.bukkit.Color.fromRGB(0xE8F4FF), 0.35, 5);
    }

    void roll(Player player, boolean fatigued) {
        double k = intensity();
        if (k <= 0.0) {
            return;
        }
        Location feet = player.getLocation().add(0, 0.1, 0);
        player.getWorld().playSound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, (float) (0.4 * k), fatigued ? 0.8f : 1.55f);
        player.getWorld().playSound(feet, Sound.ITEM_ARMOR_EQUIP_LEATHER, (float) (0.6 * k), 1.2f);
        de.aetherion.core.api.Vfx.wave(feet, fatigued ? org.bukkit.Color.fromRGB(0x9A8F84) : org.bukkit.Color.fromRGB(0xD9D2C5), 1.1, 8);
        if (fatigued) {
            callout(player, "TIRED ROLL", "MÜDE ROLLE", WARN, 16);
        }
    }

    /** Inside the i-frames when a real hit passed through: cyan/white burst, whoosh, refund ping. */
    void perfectDodge(Player player) {
        double k = intensity();
        if (k > 0.0) {
            World world = player.getWorld();
            Location chest = chest(player);
            player.playSound(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, (float) (0.9 * k), 1.5f);
            world.playSound(chest, Sound.ENTITY_BREEZE_WIND_BURST, (float) (0.7 * k), 1.7f);
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, (float) (0.7 * k), 1.4f);
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, (float) (0.9 * k), 1.9f);
            de.aetherion.core.api.Vfx.burst(chest, org.bukkit.Color.fromRGB(0x9FE3FF), 1.3, 8);
        }
        CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.DODGE);
        callout(player, "≫ PERFECT DODGE ≪", "≫ PERFEKT AUSGEWICHEN ≪", DODGE, 28);
    }

    /* =========================================================
     * STRIKES
     * ========================================================= */

    enum Strike { LIGHT, NORMAL, HEAVY, AERIAL, DASH, FINISHER, RIPOSTE }

    /** Called from the cadence hook (HIGH) once the multiplier is decided. Sounds are local to the hit. */
    /** Damage-number style for this hit; set at HIGH so DamageNumbers (MONITOR) sees it. */
    static void markStrike(Player player, LivingEntity victim, Strike kind) {
        DamageNumbers.markStyle(player, victim, switch (kind) {
            case LIGHT -> DamageNumbers.Style.LIGHT;
            case NORMAL -> DamageNumbers.Style.NORMAL;
            case HEAVY, DASH -> DamageNumbers.Style.HEAVY;
            case AERIAL -> DamageNumbers.Style.AERIAL;
            case FINISHER -> DamageNumbers.Style.FINISHER;
            case RIPOSTE -> DamageNumbers.Style.RIPOSTE;
        });
    }

    /** Sounds, particles, callout and hitstop for a hit that stood (MONITOR). */
    void strike(Player player, LivingEntity victim, Strike kind, boolean boss) {
        double k = intensity();
        if (k <= 0.0 || kind == Strike.LIGHT || kind == Strike.NORMAL) {
            return;
        }
        Location at = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
        World world = victim.getWorld();
        slashVfx(player, at, kind, k);
        switch (kind) {
            case HEAVY, DASH -> {
                player.playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, (float) (0.8 * k), 0.85f);
                player.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, (float) (0.6 * k), 0.7f);
                de.aetherion.core.api.Vfx.spark(at, org.bukkit.Color.fromRGB(0xF2EEE6), 0.9);
            }
            case AERIAL -> {
                player.playSound(at, Sound.ITEM_MACE_SMASH_AIR, (float) (0.7 * k), 1.3f);
                player.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, (float) (0.8 * k), 0.9f);
                de.aetherion.core.api.Vfx.spark(at, org.bukkit.Color.fromRGB(0x9EE6FF), 1.0);
            }
            case FINISHER -> {
                world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, (float) (0.9 * k), 1.2f);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, (float) (1.0 * k), 0.7f);
                world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, (float) (0.5 * k), 1.4f);
                world.playSound(at, Sound.BLOCK_ANVIL_LAND, (float) (0.35 * k), 0.9f);
                de.aetherion.core.api.Vfx.burst(at, org.bukkit.Color.fromRGB(0xFF9A3C), 1.6, 9);
                de.aetherion.core.api.Vfx.wave(victim.getLocation().add(0, 0.05, 0), org.bukkit.Color.fromRGB(0xFF7A3C), 2.2, 10);
                CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.FINISHER);
                callout(player, "✸ FINISHER ✸", "✸ FINISHER ✸", FINISH, 24);
                kick(player, victim, 0.35);
            }
            case RIPOSTE -> {
                world.playSound(at, Sound.ITEM_TRIDENT_HIT, (float) (1.0 * k), 1.4f);
                world.playSound(at, Sound.BLOCK_BELL_USE, (float) (0.6 * k), 1.5f);
                world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, (float) (0.5 * k), 1.6f);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, (float) (0.9 * k), 0.8f);
                de.aetherion.core.api.Vfx.burst(at, org.bukkit.Color.fromRGB(0xE58BFF), 1.5, 9);
                CombatGlyphs.flash(system, player, CombatGlyphs.Glyph.RIPOSTE);
                callout(player, "» RIPOSTE «", "» KONTER «", RIPOSTE, 24);
                kick(player, victim, 0.35);
            }
            default -> {
            }
        }
        if (!boss && (kind == Strike.FINISHER || kind == Strike.RIPOSTE || kind == Strike.HEAVY || kind == Strike.AERIAL
                || kind == Strike.DASH) && victim instanceof Mob mob && !system.protectedEntity(mob)) {
            hitstop(mob, kind == Strike.FINISHER || kind == Strike.RIPOSTE ? 2 : 0);
        }
    }

    /** Big hit taken (share of max HP after mitigation): one low thump, no screen effect. */
    /** Display-entity crescent on heavy / dash / aerial / finisher / riposte hits (presentation only). */
    private static void slashVfx(Player player, Location at, Strike kind, double k) {
        org.bukkit.Color tint = switch (kind) {
            case FINISHER -> org.bukkit.Color.fromRGB(0xFF5A4A);
            case RIPOSTE -> org.bukkit.Color.fromRGB(0xFFD86B);
            case AERIAL -> org.bukkit.Color.fromRGB(0x9EE6FF);
            default -> org.bukkit.Color.fromRGB(0xF2EEE6);
        };
        double size = kind == Strike.FINISHER || kind == Strike.RIPOSTE ? 2.2 : 1.6;
        float roll = (float) (java.util.concurrent.ThreadLocalRandom.current().nextDouble(-35.0, 35.0));
        de.aetherion.core.api.Vfx.slash(at, tint, size, roll);
        de.aetherion.core.api.Vfx.sound(at, kind == Strike.FINISHER ? de.aetherion.core.api.Vfx.COMBAT_HEAVY
                : de.aetherion.core.api.Vfx.COMBAT_SLASH, org.bukkit.SoundCategory.PLAYERS, (float) (0.55 * k), 1.0f);
    }

    void heavyTaken(Player player, double share) {
        double k = intensity();
        if (k <= 0.0 || !system.config().hurtCam || share < 0.2) {
            return;
        }
        player.playSound(player, Sound.ENTITY_GENERIC_BIG_FALL, (float) (0.8 * k), 0.7f);
        player.playSound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, (float) (0.5 * k), 0.6f);
    }

    /**
     * Light camera kick: the client's own hurt tilt, played without damage (Paper playHurtAnimation), from
     * the side the source sits on. Off with feedback.screen-kick.
     */
    private void kick(Player player, Entity from, double strength) {
        if (!system.config().screenKick || intensity() * strength < 0.15) {
            return;
        }
        float yaw = 0f;
        if (from != null && from.getWorld() == player.getWorld()) {
            Vector to = from.getLocation().toVector().subtract(player.getLocation().toVector());
            float abs = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            yaw = abs - player.getLocation().getYaw();
        }
        try {
            player.playHurtAnimation(yaw);
        } catch (Throwable ignored) {
            // Older API: no kick, everything else still plays.
        }
    }

    /* =========================================================
     * HITSTOP
     * ========================================================= */

    private void hitstop(Mob mob, int extra) {
        int ticks = system.config().hitstopTicks;
        if (ticks <= 0 || intensity() < 0.25 || mob.isInsideVehicle() || !mob.isValid()) {
            return;
        }
        int length = ticks + extra;
        long now = org.bukkit.Bukkit.getCurrentTick();
        holds.compute(mob.getUniqueId(), (id, old) -> old != null ? old : new Hold(mob, now + 1, now + 1 + length));
        system.guard().freeze(mob, length + 1, now);
    }

    void tick(long tick) {
        if (holds.isEmpty()) {
            return;
        }
        Iterator<Hold> it = holds.values().iterator();
        while (it.hasNext()) {
            Hold hold = it.next();
            Mob mob = hold.mob;
            if (!mob.isValid()) {
                it.remove();
                continue;
            }
            if (tick == hold.startTick || (hold.parked == null && tick > hold.startTick)) {
                hold.parked = mob.getVelocity();
                mob.setVelocity(hold.parked.clone().multiply(0.1));
            }
            if (tick >= hold.endTick) {
                if (hold.parked != null) {
                    mob.setVelocity(hold.parked);
                }
                it.remove();
            }
        }
    }

    void releaseAll() {
        for (Hold hold : holds.values()) {
            if (hold.parked != null && hold.mob.isValid()) {
                hold.mob.setVelocity(hold.parked);
            }
        }
        holds.clear();
    }

    private static final class Hold {
        final Mob mob;
        final long startTick;
        final long endTick;
        Vector parked;

        Hold(Mob mob, long startTick, long endTick) {
            this.mob = mob;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    /* =========================================================
     * HELPERS
     * ========================================================= */

    /** Centre-lane callout above the HUD. Short, one at a time; a newer one replaces the old. */
    void callout(Player player, String en, String de, TextColor color, int ticks) {
        if (!system.config().callouts) {
            return;
        }
        Component line = Component.text(CombatSystem.t(player, en, de), color).decorate(TextDecoration.BOLD);
        ActionBarLane.show(player, line, ticks);
    }

    void plain(Player player, String en, String de, int ticks) {
        ActionBarLane.show(player, Component.text(CombatSystem.t(player, en, de), NamedTextColor.GRAY), ticks);
    }

    private int count(int base) {
        return Math.max(1, (int) Math.round(base * intensity()));
    }

    private static Location chest(Player player) {
        return player.getLocation().add(0, player.getHeight() * 0.6, 0);
    }

    /** Point between the player and the attacker at chest height, where the clash reads best. */
    private static Location contact(Player player, Entity attacker) {
        Location chest = chest(player);
        if (attacker == null || attacker.getWorld() != player.getWorld()) {
            return chest.add(CombatSystem.facing(player).multiply(0.6));
        }
        Vector toward = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() < 1.0E-4) {
            return chest;
        }
        return chest.add(toward.normalize().multiply(0.6));
    }
}
