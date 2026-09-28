package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Ashen Sheath: sheathed wither-skeleton samurai.
 * Quiet steel, then falling blossoms, then ashen moon. Each phase reads differently.
 * Quiet steel sheathes between cuts; Iaido is one straight line of falling leaves.
 * Falling blossoms keeps the blade out, adds a still honor ring and traveling moon arcs.
 * Ashen moon turns the blade blood-red and answers with three aimed lines.
 * Nothing orbits like the Lobby Cleaner's beams or portal spiral. Death keeps the
 * vertical leaf column, and a cherry sphere grows in the chest until it bursts.
 */
final class AshenSheathDirector {

    private static final Color PETAL = Color.fromRGB(255, 170, 200);
    private static final Color ASH = Color.fromRGB(90, 70, 95);
    private static final Color MOON = Color.fromRGB(210, 230, 255);
    private static final Color BLOOD = Color.fromRGB(160, 30, 50);

    /*
     * Tell language. Pink/crimson lane = a cut travels here, step off. White = it fires now.
     * Blue = counter stance, do not strike. Green = opening, strike now.
     */
    private static final Color LANE = Color.fromRGB(255, 105, 180);
    private static final Color LANE_MOON = Color.fromRGB(235, 45, 70);
    private static final Color SPINE = Color.fromRGB(255, 215, 232);
    private static final Color HOT = Color.fromRGB(255, 255, 255);
    private static final Color GUARD = Color.fromRGB(90, 190, 255);
    private static final Color GUARD_DIM = Color.fromRGB(55, 85, 125);
    private static final Color OPENING = Color.fromRGB(110, 255, 140);
    private static final String[] CUT_NUMERALS = {"I", "II", "III"};

    private static final int IAIDO_CD_P1 = 160;
    private static final int IAIDO_CD_P2 = 120;
    private static final int IAIDO_CD_P3 = 90;
    private static final int PARRY_CD = 200;
    private static final int AFTERIMAGE_CD = 240;
    private static final int CRESCENT_CD = 190;
    private static final int HONOR_CD = 400;
    private static final int DEATH_TICKS = 150;
    private static final int IAIDO_WINDUP = 48;
    private static final int PARRY_OPEN = 18;
    private static final int PARRY_CLOSE = 62;
    private static final int PARRY_RECOVER = 12;
    private static final double PARRY_REACH = 5.2;
    private static final int LEAF_COUNT = 24;

    private enum Move {
        NONE,
        IAIDO,
        PARRY,
        AFTERIMAGE,
        CRESCENT,
        HONOR,
        FRENZY
    }

    private final BossInstance instance;
    private final List<Afterimage> afterimages = new ArrayList<>();
    private final List<Crescent> crescents = new ArrayList<>();
    private final List<ItemDisplay> props = new ArrayList<>();
    private final List<BlockDisplay> leaves = new ArrayList<>();

    private ItemDisplay katana;
    private ItemDisplay hipSheath;
    private ItemDisplay lineBlade;
    private boolean openingCut = true;
    private boolean drawn;
    private int silhouette = -1;
    private Move move = Move.NONE;
    private int actionTick;
    private int iaidoCd;
    private int parryCd;
    private int afterimageCd;
    private int crescentCd;
    private int honorCd;
    private int meleeCd;
    private int breath;
    private boolean honorUsed;
    private boolean honorCut;
    private boolean frenzyArmed;
    private Location iaidoFrom;
    private Location iaidoTo;
    private Vector iaidoDir;
    private boolean frenzyAimed;
    private boolean iaidoTaught;
    private Location lastCutFrom;
    private Location lastCutTo;
    private UUID honorTarget;
    private Location honorCenter;
    private int honorTicks;
    private int deathTicks = -1;
    private Location deathFocus;
    private ItemDisplay deathBlade;
    private BlockDisplay deathSphere;
    private final List<BlockDisplay> honorLeaves = new ArrayList<>();

    AshenSheathDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isAshen() {
        return instance.getTemplate() != null
                && "ashen_sheath".equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    /** Scripted cut or death. Keeps vanilla AI from walking through the tell. */
    boolean holdsBody() {
        return isAshen() && (move != Move.NONE || isDying());
    }

    boolean parrying() {
        return isAshen() && move == Move.PARRY && actionTick >= PARRY_OPEN && actionTick <= PARRY_CLOSE;
    }

    void onBind() {
        if (!isAshen()) {
            return;
        }
        clearCombatFx();
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        dressSensei(entity);
        silhouette = -1;
        refreshSilhouette(entity);
        spawnKatana(entity);
        drawn = false;
        openingCut = true;
        move = Move.NONE;
        actionTick = 0;
        iaidoCd = 80;
        parryCd = 110;
        afterimageCd = 140;
        crescentCd = 160;
        honorCd = 200;
        meleeCd = 30;
        breath = 50;
        honorUsed = false;
        honorCut = false;
        frenzyArmed = false;
        iaidoTaught = false;
        lastCutFrom = null;
        lastCutTo = null;
        honorTicks = 0;
        deathTicks = -1;
        instance.resetAshenParries();
    }

    void abort() {
        clearCombatFx();
        removeKatana();
        if (deathBlade != null && deathBlade.isValid()) {
            deathBlade.remove();
        }
        deathBlade = null;
        clearDeathSphere();
        deathTicks = -1;
        move = Move.NONE;
    }

    boolean beginDeath() {
        if (!isAshen() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        clearCombatFx();
        deathTicks = 0;
        deathFocus = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : instance.getSpawnLocation().clone();
        if (deathFocus.getWorld() == null) {
            deathTicks = -1;
            return false;
        }
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setGlowing(false);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
        }
        World world = deathFocus.getWorld();
        world.playSound(deathFocus, Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.55f);
        world.playSound(deathFocus, Sound.BLOCK_CHERRY_WOOD_STEP, 0.9f, 0.55f);
        world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.5f);
        shout("&d&lAshen Sheath&7: &f…the cut was always meant for me.");
        titleNear(deathFocus, "&dSHEATHED", "&7The grove takes the blade back.");
        plantDeathBlade(deathFocus);
        plantDeathSphere(deathFocus);
        if (deathFocus.getWorld() != null) {
            ensureLeaves(deathFocus.getWorld(), deathFocus, LEAF_COUNT);
        }
        return true;
    }

    /**
     * @return true when the striker was actually answered
     */
    boolean tryParry(Player striker) {
        if (!parrying() || striker == null || !striker.isValid() || striker.isDead()) {
            return false;
        }
        if (striker.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.3f, 1.7f);
        world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.1f, 0.55f);
        world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BELL_USE, 0.7f, 1.35f);
        world.spawnParticle(Particle.FLASH, entity.getEyeLocation(), 1, 0, 0, 0, 0);
        guardShatter(entity.getLocation());
        petalBurst(entity.getEyeLocation(), 22);
        shout("&d&lAshen Sheath&7: &cCountered. &8(never strike the blue stance)");
        striker.showTitle(Title.title(
                TextUtil.component("&cCOUNTERED"),
                TextUtil.component("&7Blue stance = hands off. Wait for &agreen&7."),
                titleTimes()
        ));
        Location behind = striker.getLocation().clone().add(striker.getLocation().getDirection().multiply(-1.4));
        behind.setY(striker.getLocation().getY());
        Vector look = striker.getLocation().toVector().subtract(behind.toVector());
        if (look.lengthSquared() > 0.01) {
            behind.setDirection(look);
        }
        drawn = true;
        clearLeafTornado();
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(behind);
            }
        });
        syncKatana(entity);
        slashSweep(behind.clone().add(0, 1.5, 0), striker.getEyeLocation(), BLOOD);
        if (vulnerable(striker)) {
            BossHits.crush(striker, entity, 58.0);
            Vector knock = striker.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (knock.lengthSquared() < 0.01) {
                knock = new Vector(0, 0.4, 0);
            } else {
                knock.normalize().multiply(1.35).setY(0.48);
            }
            striker.setVelocity(knock);
            striker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 36, 1, false, true, true));
        }
        move = Move.NONE;
        actionTick = 0;
        parryCd = PARRY_CD;
        if (instance.healthPercent() > 66.0) {
            drawn = false;
        }
        breath = breathForPhase();
        if (entity instanceof Mob mob && !instance.isTransitioning()) {
            mob.setAI(true);
        }
        if (instance.ashenParries() >= 2) {
            shout("&d&lAshen Sheath&7: &fYou keep offering the same cut.");
        }
        return true;
    }

    /**
     * Ashen Moon's phase change: a vertical column of cherry leaves around the body.
     * It is not the ground draw, and it does not widen into a portal tornado.
     * Caller must {@link #clearLeafTornado()}.
     */
    void tickCherryTornado(Location focus, int tick, int duration) {
        if (focus == null || focus.getWorld() == null) {
            clearLeafTornado();
            return;
        }
        ensureLeaves(focus.getWorld(), focus, 16);
        double progress = tick / (double) Math.max(1, duration);
        double height = 2.2 + progress * 2.4;
        spinLeaves(focus, tick, height, 0.12);
        World world = focus.getWorld();
        if (tick % 12 == 0) {
            world.playSound(focus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.55f, 0.5f + (float) progress * 0.4f);
            world.playSound(focus, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.22f, 1.2f);
        }
    }

    /**
     * Falling Blossoms. The sheath stays on the back. One blade slides a straight line.
     * Flat cherry leaves fall onto the ground that line has already crossed.
     * Nothing orbits, and nothing draws an end-rod or portal beam.
     */
    void tickPetalDraw(Location focus, int tick, int duration) {
        if (focus == null || focus.getWorld() == null) {
            clearLeafTornado();
            return;
        }
        World world = focus.getWorld();
        if (tick <= 1) {
            clearLeafTornado();
        }
        LivingEntity entity = instance.getEntity();
        Vector facing = entity != null && entity.isValid()
                ? entity.getLocation().getDirection().clone().setY(0)
                : new Vector(0, 0, 1);
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        double progress = tick / (double) Math.max(1, duration);
        double length = 7.4;
        ensureLeaves(world, focus, 10);
        layPetals(focus, facing, progress, length, tick);
        slideDrawLine(focus, facing, progress, length);
        if (entity != null && entity.isValid()) {
            poseDraw(entity, progress);
            Location look = entity.getLocation().clone();
            look.setDirection(facing);
            entity.setRotation(look.getYaw(), 6.0f);
        }
        if (tick % 8 == 0) {
            world.playSound(focus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.45f, 0.6f + (float) progress * 0.5f);
            world.playSound(focus, Sound.BLOCK_CHERRY_WOOD_STEP, 0.35f, 0.8f + (float) progress * 0.3f);
        }
        if (tick == Math.max(2, duration - 6)) {
            world.playSound(focus, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.45f);
            world.playSound(focus, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.55f);
        }
    }

    void clearLeafTornado() {
        clearLeavesOnly();
        clearDrawLine();
    }

    private void clearLeavesOnly() {
        clearTrail(leaves);
    }

    private void clearTrail(List<BlockDisplay> trail) {
        if (trail == null) {
            return;
        }
        for (BlockDisplay leaf : trail) {
            if (leaf != null && leaf.isValid()) {
                leaf.remove();
            }
        }
        trail.clear();
    }

    /**
     * @return true when the death cinematic finished and the body should be removed
     */
    boolean tick() {
        if (!isAshen()) {
            return false;
        }
        if (isDying()) {
            return tickDeath();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        if (instance.isTransitioning()) {
            if (move != Move.NONE) {
                clearLeafTornado();
                move = Move.NONE;
                actionTick = 0;
            }
            syncKatana(entity);
            return false;
        }
        tickCooldowns();
        refreshSilhouette(entity);
        if (move != Move.IAIDO && move != Move.FRENZY) {
            syncKatana(entity);
        }
        petalIdle(entity);
        tickAfterimages(entity);
        tickCrescents(entity);
        tickHonor(entity);
        if (move != Move.NONE) {
            tickAction(entity);
            return false;
        }
        maybeStartAction(entity);
        if (move == Move.NONE) {
            approach(entity);
        }
        return false;
    }

    private void tickCooldowns() {
        if (iaidoCd > 0) {
            iaidoCd--;
        }
        if (parryCd > 0) {
            parryCd--;
        }
        if (afterimageCd > 0) {
            afterimageCd--;
        }
        if (crescentCd > 0) {
            crescentCd--;
        }
        if (honorCd > 0) {
            honorCd--;
        }
        if (meleeCd > 0) {
            meleeCd--;
        }
        if (breath > 0 && move == Move.NONE) {
            breath--;
        }
    }

    private void maybeStartAction(LivingEntity entity) {
        if (breath > 0) {
            return;
        }
        Player target = nearest(entity, 32.0);
        if (target == null) {
            return;
        }
        if (openingCut) {
            openingCut = false;
            beginIaido(entity, target);
            return;
        }
        double pct = instance.healthPercent();
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (pct <= 33.0 && !frenzyArmed && iaidoCd <= 0) {
            frenzyArmed = true;
            beginFrenzy(entity, target);
            return;
        }
        if (pct <= 66.0 && !honorUsed && honorCd <= 0 && roll < 12) {
            beginHonor(entity, target);
            return;
        }
        if (parryCd <= 0 && roll < 14) {
            beginParry(entity);
            return;
        }
        if (pct <= 66.0 && crescentCd <= 0 && roll < 16) {
            beginCrescent(entity, target);
            return;
        }
        if (afterimageCd <= 0 && roll < 16) {
            beginAfterimage(entity, target);
            return;
        }
        if (iaidoCd <= 0 && roll < 22) {
            beginIaido(entity, target);
        }
    }

    private void beginIaido(LivingEntity entity, Player target) {
        move = Move.IAIDO;
        actionTick = 0;
        drawn = false;
        lastCutFrom = null;
        lastCutTo = null;
        clearLeafTornado();
        aimLine(entity, target, 8.0, 4.0);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(target);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.55f);
        world.playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.5f);
        world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_WOOD_STEP, 0.6f, 0.7f);
        shout("&d&lAshen Sheath&7 draws…");
        if (!iaidoTaught) {
            iaidoTaught = true;
            titleNear(entity.getLocation(), "&dIAIDO", "&7The petal lane is the cut. Step off it.");
        } else {
            titleNear(entity.getLocation(), "", "&d⚔ Iaido &7— step off the lane");
        }
    }

    private void tickIaido(LivingEntity entity) {
        actionTick++;
        World world = entity.getWorld();
        if (actionTick <= IAIDO_WINDUP) {
            drawn = false;
            double charge = actionTick / (double) IAIDO_WINDUP;
            if (actionTick == 1) {
                clearLeafTornado();
            }
            int remaining = IAIDO_WINDUP - actionTick;
            if (iaidoFrom != null && iaidoTo != null && iaidoDir != null) {
                double length = Math.max(3.5, iaidoFrom.distance(iaidoTo));
                ensureLeaves(world, iaidoFrom, 9);
                layPetals(iaidoFrom, iaidoDir, charge, length, actionTick);
                slideDrawLine(iaidoFrom, iaidoDir, charge, length);
                drawLane(iaidoFrom, iaidoDir, length, 1.1, charge, actionTick, remaining);
            }
            if (iaidoDir != null) {
                Location look = entity.getLocation().clone();
                look.setDirection(iaidoDir);
                entity.setRotation(look.getYaw(), 8.0f);
            }
            poseDraw(entity, charge);
            if (actionTick % 4 == 1) {
                actionBarNear(entity, 22, remaining <= 8
                        ? "&f&l⚠ NOW &8| &fget off the lane!"
                        : "&d⚔ IAIDO &8| &fstep off the petal lane &8| " + meter(remaining, IAIDO_WINDUP, "&d"));
            }
            fuseTick(entity.getLocation(), remaining);
            if (actionTick % 8 == 0) {
                world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.28f, 0.7f + actionTick * 0.012f);
                world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_WOOD_STEP, 0.22f, 0.8f + actionTick * 0.01f);
            }
            if (actionTick == IAIDO_WINDUP) {
                world.playSound(entity.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.25f, 1.65f);
                world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.4f);
                world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.4f);
            }
            return;
        }
        if (actionTick == IAIDO_WINDUP + 1) {
            clearDrawLine();
            drawn = true;
            Location land = iaidoTo == null ? entity.getLocation() : iaidoTo.clone();
            if (iaidoDir != null) {
                land.setDirection(iaidoDir);
            }
            instance.runInternalTeleport(() -> {
                if (entity.isValid()) {
                    entity.teleport(land);
                }
            });
            poseDraw(entity, 1.0);
            if (iaidoFrom != null && iaidoDir != null) {
                double length = iaidoTo == null ? 8.0 : Math.max(3.5, iaidoFrom.distance(iaidoTo));
                layPetals(iaidoFrom, iaidoDir, 1.0, length, actionTick);
                cutLine(iaidoFrom.clone().add(0, 1.15, 0), land.clone().add(0, 1.15, 0), laneColor());
                hitLine(entity, iaidoFrom, land, 1.45, powerForPhase(48, 58, 70));
                lastCutFrom = iaidoFrom.clone().add(0, 1.15, 0);
                lastCutTo = land.clone().add(0, 1.15, 0);
            }
            world.spawnParticle(Particle.FLASH, land.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
            return;
        }
        if (actionTick < IAIDO_WINDUP + 16) {
            drawn = true;
            syncKatana(entity);
            if (actionTick == IAIDO_WINDUP + 8) {
                afterCut(lastCutFrom, lastCutTo);
            }
            if (iaidoFrom != null && iaidoDir != null) {
                double length = iaidoTo == null ? 8.0 : Math.max(3.5, iaidoFrom.distance(iaidoTo));
                layPetals(iaidoFrom, iaidoDir, 1.0, length, actionTick);
            }
            return;
        }
        endAction(entity);
        iaidoCd = iaidoCooldown();
    }

    private void beginParry(LivingEntity entity) {
        move = Move.PARRY;
        actionTick = 0;
        drawn = true;
        clearLeafTornado();
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.8f);
        world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BELL_USE, 0.9f, 0.6f);
        shout("&d&lAshen Sheath&7: &fCome. &8…strike me, if you dare.");
        titleNear(entity.getLocation(), "&b◆ COUNTER STANCE", "&7Don't strike. Step out of the ring.");
    }

    /*
     * Counter stance reads in three beats on one floor ring at the release reach:
     * blue arc fills (stop attacking), blue clock drains then flashes white (hands off, get out),
     * green opening after the release (strike now).
     */
    private void tickParry(LivingEntity entity) {
        actionTick++;
        World world = entity.getWorld();
        Location at = entity.getEyeLocation();
        Location feet = entity.getLocation();
        boolean open = actionTick >= PARRY_OPEN && actionTick <= PARRY_CLOSE;
        double openT = actionTick <= PARRY_OPEN ? actionTick / (double) PARRY_OPEN : (open ? 1.0 : 0.0);
        double facing = facingAngle(entity);
        ensureLeaves(world, feet, 8);
        if (actionTick <= PARRY_CLOSE) {
            double radius = open ? 1.35 : 2.15 - openT * 0.7;
            double height = open ? 1.05 : 0.28 + openT * 0.45;
            layLeafRing(feet, radius, height, open);
            poseGuard(entity, open ? 1.0 : openT);
        } else {
            layLeafRing(feet, 1.5, 0.12, false);
            if (actionTick > PARRY_CLOSE + 1 && katana != null && katana.isValid()) {
                katana.setGlowColorOverride(OPENING);
            }
        }
        if (actionTick < PARRY_OPEN) {
            if (actionTick % 2 == 0) {
                ringArc(feet, PARRY_REACH, facing, 0.0, openT, new Particle.DustOptions(GUARD, 1.1f), 0.12);
            }
            if (actionTick % 6 == 0) {
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.9f + (float) openT * 0.8f);
            }
            if (actionTick % 4 == 1) {
                actionBarNear(entity, 22, "&b◆ COUNTER STANCE &8| &fstop attacking &8| "
                        + meter(PARRY_OPEN - actionTick, PARRY_OPEN, "&b"));
            }
        }
        if (actionTick == PARRY_OPEN) {
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.5f);
            world.playSound(at, Sound.BLOCK_BELL_RESONATE, 0.5f, 1.4f);
            ringArc(feet, PARRY_REACH, facing, 0.0, 1.0, new Particle.DustOptions(GUARD, 1.5f), 0.12);
        }
        if (open) {
            int window = PARRY_CLOSE - PARRY_OPEN;
            int left = PARRY_CLOSE - actionTick;
            boolean imminent = left <= 10;
            if (imminent) {
                if (actionTick % 2 == 0) {
                    ringArc(feet, PARRY_REACH, facing, 0.0, 1.0, new Particle.DustOptions(HOT, 1.3f), 0.12);
                }
                if (left == 10 || left == 6 || left == 3 || left == 1) {
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 1.6f);
                }
            } else {
                double lit = left / (double) window;
                if (actionTick % 3 == 0) {
                    ringArc(feet, PARRY_REACH, facing, 0.0, lit, new Particle.DustOptions(GUARD, 1.2f), 0.12);
                }
                if (actionTick % 6 == 0) {
                    ringArc(feet, PARRY_REACH, facing, lit, 1.0, new Particle.DustOptions(GUARD_DIM, 0.7f), 0.12);
                }
            }
            if (actionTick % 4 == 0) {
                ringArc(feet, 0.85, facing + actionTick * 0.05, 0.0, 1.0, new Particle.DustOptions(GUARD, 0.8f), 1.55);
            }
            if (actionTick % 10 == 0) {
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 1.4f);
            }
            if (actionTick % 4 == 0) {
                actionBarNear(entity, 22, imminent
                        ? "&f&l⚠ RELEASE &8| &fget out of the ring!"
                        : "&b✋ DON'T STRIKE &8| &fstep out of the ring &8| " + meter(left, window, "&b"));
            }
        }
        if (actionTick == PARRY_CLOSE + 1) {
            ringArc(feet, PARRY_REACH, facing, 0.0, 1.0, new Particle.DustOptions(HOT, 1.6f), 0.15);
            for (int i = 0; i < 8; i++) {
                double ang = facing + Math.PI * 2 * i / 8.0;
                world.spawnParticle(Particle.SWEEP_ATTACK, feet.clone().add(Math.cos(ang) * 2.6, 1.1, Math.sin(ang) * 2.6), 1, 0, 0, 0, 0);
            }
            world.playSound(at, Sound.ITEM_TRIDENT_THROW, 0.9f, 1.55f);
            Player near = nearest(entity, PARRY_REACH);
            if (near != null && vulnerable(near)) {
                slashArc(entity, near.getLocation(), BLOOD);
                BossHits.hurt(near, entity, powerForPhase(36, 42, 50));
                world.playSound(near.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, 0.55f);
                near.sendActionBar(TextUtil.component("&cCaught in the ring. &7Leave it before the white flash."));
            } else {
                world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.4f);
            }
        }
        if (actionTick == PARRY_CLOSE + 2) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
            titleNear(entity.getLocation(), "&a⚔ OPENING", "&7Strike now.");
        }
        if (actionTick > PARRY_CLOSE + 1 && actionTick < PARRY_CLOSE + PARRY_RECOVER) {
            if ((actionTick - PARRY_CLOSE) % 3 == 2) {
                ringArc(feet, 1.1, facing, 0.0, 1.0, new Particle.DustOptions(OPENING, 1.1f), 0.12);
                world.spawnParticle(Particle.HAPPY_VILLAGER, at, 3, 0.35, 0.35, 0.35, 0);
                actionBarNear(entity, 22, "&a⚔ STRIKE NOW &8| "
                        + meter(PARRY_CLOSE + PARRY_RECOVER - actionTick, PARRY_RECOVER, "&a"));
            }
        }
        if (actionTick >= PARRY_CLOSE + PARRY_RECOVER) {
            endAction(entity);
            parryCd = PARRY_CD;
        }
    }

    private void beginAfterimage(LivingEntity entity, Player target) {
        move = Move.AFTERIMAGE;
        actionTick = 0;
        drawn = true;
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(target);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.85f, 0.7f);
        world.playSound(entity.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.35f);
        world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.7f);
        shout("&d&lAshen Sheath&7 splits into &fblossom shadows&7.");
        titleNear(entity.getLocation(), "&dBLOSSOM SHADOW", "&7A tether means a shadow picked you. Back away.");
        clearLeafTornado();
        Vector side = entity.getLocation().getDirection().clone().setY(0);
        if (side.lengthSquared() < 0.01) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector right = new Vector(-side.getZ(), 0, side.getX());
        int ghosts = instance.healthPercent() <= 33.0 ? 3 : 2;
        for (int i = 0; i < ghosts; i++) {
            double ang = (i - (ghosts - 1) / 2.0) * 1.1;
            Location spot = ground(entity.getLocation().clone()
                    .add(right.clone().multiply(Math.sin(ang) * 4.2))
                    .add(side.clone().multiply(Math.cos(ang) * 2.4 - 1.2)));
            spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
            afterimages.add(new Afterimage(spot, spawnGhostKatana(spot), spawnGhostSheath(spot), spawnGhostHead(spot), 36 + i * 16));
            if (spot.getWorld() != null) {
                spot.getWorld().spawnParticle(Particle.CHERRY_LEAVES, spot.clone().add(0, 1.3, 0), 10, 0.3, 0.7, 0.3, 0.02);
                spot.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, spot.clone().add(0, 1.3, 0), 8, 0.25, 0.6, 0.25, 0,
                        new Particle.DustTransition(PETAL, ASH, 1.2f));
            }
        }
    }

    private void tickAfterimage(LivingEntity entity) {
        actionTick++;
        if (actionTick < 16) {
            poseGuard(entity, actionTick / 16.0);
            return;
        }
        if (actionTick == 16 && !afterimages.isEmpty()) {
            Afterimage last = afterimages.get(afterimages.size() - 1);
            Location land = last.origin.clone();
            instance.runInternalTeleport(() -> {
                if (entity.isValid()) {
                    entity.teleport(land);
                }
            });
            entity.getWorld().spawnParticle(Particle.FLASH, land.clone().add(0, 1.1, 0), 1, 0, 0, 0, 0);
        }
        if (actionTick >= 80 && afterimages.isEmpty()) {
            endAction(entity);
            afterimageCd = AFTERIMAGE_CD;
        }
    }

    private void tickAfterimages(LivingEntity entity) {
        if (afterimages.isEmpty()) {
            return;
        }
        Iterator<Afterimage> it = afterimages.iterator();
        while (it.hasNext()) {
            Afterimage ghost = it.next();
            ghost.tick++;
            Vector dir = ghost.origin.getDirection().clone().setY(0);
            if (dir.lengthSquared() < 0.01) {
                dir = new Vector(0, 0, 1);
            }
            dir.normalize();
            int wind = Math.max(6, ghost.fireAt - 10);
            double charge = ghost.tick >= ghost.fireAt
                    ? 1.0
                    : ghost.tick <= wind
                    ? 0.0
                    : (ghost.tick - wind) / (double) Math.max(1, ghost.fireAt - wind);
            if (ghost.blade != null && ghost.blade.isValid()) {
                Location rest = ghost.origin.clone().add(0, 0.95, 0);
                Location extended = ghost.origin.clone().add(dir.clone().multiply(1.35)).add(0, 1.2, 0);
                ghost.blade.teleport(lerp(rest, extended, charge));
                ghost.blade.setRotation(yawOf(dir), (float) (-74.0 + charge * 58.0));
                ghost.blade.setTransformation(katanaTransform(0.58f, charge > 0.9));
                ghost.blade.setGlowColorOverride(bladeGlow(charge > 0.85));
            }
            if (ghost.sheath != null && ghost.sheath.isValid()) {
                ghost.sheath.teleport(ghost.origin.clone().add(0, 0.95, 0));
                ghost.sheath.setRotation(yawOf(dir) - 18.0f, -74.0f);
                ghost.sheath.setTransformation(sheathTransform());
            }
            boolean locking = ghost.tick > wind && ghost.tick < ghost.fireAt;
            Player locked = locking ? nearest(entity, ghost.origin, 14.0) : null;
            if (ghost.head != null && ghost.head.isValid()) {
                float headYaw = locked != null
                        ? yawOf(locked.getLocation().toVector().subtract(ghost.origin.toVector()).setY(0))
                        : yawOf(dir);
                ghost.head.teleport(ghost.origin.clone().add(0, 2.45, 0));
                ghost.head.setRotation(headYaw, 0.0f);
                ghost.head.setGlowColorOverride(ghost.fireAt - ghost.tick <= 4 && ghost.tick < ghost.fireAt ? HOT : PETAL);
            }
            if (ghost.tick <= ghost.fireAt && ghost.tick % 3 == 0) {
                ghostBody(ghost.origin, dir);
            }
            if (locked != null) {
                if (ghost.tick == wind + 1 && ghost.origin.getWorld() != null) {
                    ghost.origin.getWorld().playSound(ghost.origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f + afterimages.indexOf(ghost) * 0.2f);
                    ghost.origin.getWorld().playSound(ghost.origin, Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.6f, 1.3f);
                }
                boolean inReach = locked.getLocation().distanceSquared(ghost.origin) < 64.0;
                if (ghost.tick % 2 == 0) {
                    tether(ghost.origin.clone().add(0, 1.4, 0), locked.getLocation().add(0, 1.0, 0), charge, inReach);
                }
                if (ghost.tick % 4 == 0) {
                    locked.sendActionBar(TextUtil.component(inReach
                            ? "&d☍ SHADOW LOCKED &8| &fget 8 blocks away from it"
                            : "&7☍ Shadow locked &8| &aout of reach"));
                }
            }
            if (ghost.tick == ghost.fireAt) {
                Player prey = nearest(entity, ghost.origin, 14.0);
                Location tip = prey != null
                        ? prey.getEyeLocation()
                        : ghost.origin.clone().add(dir.clone().multiply(6)).add(0, 1.2, 0);
                slashSweep(ghost.origin.clone().add(0, 1.2, 0), tip, bladeGlow(true));
                if (prey != null && vulnerable(prey) && prey.getLocation().distanceSquared(ghost.origin) < 64.0) {
                    BossHits.hurt(prey, entity, powerForPhase(28, 34, 40));
                }
                if (ghost.origin.getWorld() != null) {
                    ghost.origin.getWorld().playSound(ghost.origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.25f);
                    ghost.origin.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, ghost.origin.clone().add(0, 1.3, 0), 14, 0.3, 0.7, 0.3, 0,
                            new Particle.DustTransition(PETAL, ASH, 1.3f));
                }
                dropProp(ghost.head);
            }
            if (ghost.tick <= ghost.fireAt + 8) {
                continue;
            }
            dropProp(ghost.blade);
            dropProp(ghost.sheath);
            dropProp(ghost.head);
            it.remove();
        }
    }

    private void beginCrescent(LivingEntity entity, Player target) {
        move = Move.CRESCENT;
        actionTick = 0;
        drawn = true;
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(target);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.75f, 1.35f);
        world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f);
        shout("&d&lAshen Sheath&7: &fMoon cuts.");
        titleNear(entity.getLocation(), "&dMOON CUT", "&7Arcs ride the lane. Step off it.");
        clearLeafTornado();
        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = entity.getLocation().getDirection().setY(0);
        }
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        int waves = instance.healthPercent() <= 33.0 ? 3 : 2;
        for (int i = 0; i < waves; i++) {
            Crescent crescent = new Crescent(
                    entity.getLocation().clone().add(0, 1.05, 0),
                    dir.clone(),
                    16 + i * 12,
                    0.42 + i * 0.08
            );
            armCrescent(crescent);
            crescents.add(crescent);
        }
    }

    private void tickCrescent(LivingEntity entity) {
        actionTick++;
        poseDraw(entity, Math.min(1.0, actionTick / 16.0));
        if (actionTick >= 64 && crescents.isEmpty()) {
            endAction(entity);
            crescentCd = CRESCENT_CD;
        }
    }

    private void tickCrescents(LivingEntity entity) {
        if (crescents.isEmpty()) {
            return;
        }
        Crescent pending = null;
        for (Crescent crescent : crescents) {
            if (crescent.delay > 0 && (pending == null || crescent.delay < pending.delay)) {
                pending = crescent;
            }
        }
        if (pending != null) {
            int remaining = pending.delay - 1;
            drawLane(pending.origin.clone().subtract(0, 1.05, 0), pending.dir, 13.5, 1.9,
                    1.0 - Math.min(1.0, remaining / 16.0), (int) instance.getTicksAlive(), remaining);
            fuseTick(pending.origin, remaining);
        }
        Iterator<Crescent> it = crescents.iterator();
        while (it.hasNext()) {
            Crescent crescent = it.next();
            if (crescent.delay > 0) {
                crescent.delay--;
                poseCrescent(crescent, 0.35, true);
                if (crescent.delay == 0 && crescent.origin.getWorld() != null) {
                    crescent.origin.getWorld().playSound(crescent.origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 1.7f);
                    crescent.origin.getWorld().playSound(crescent.origin, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.4f, 1.2f);
                    crescent.origin.getWorld().playSound(crescent.origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 1.6f);
                }
                continue;
            }
            crescent.age++;
            crescent.travel += crescent.speed;
            Location tip = crescent.origin.clone().add(crescent.dir.clone().multiply(crescent.travel));
            World world = tip.getWorld();
            if (world == null) {
                retireCrescent(crescent);
                it.remove();
                continue;
            }
            poseCrescent(crescent, crescent.travel, false);
            double reach = 2.05 * 2.05;
            for (Player player : world.getPlayers()) {
                if (!vulnerable(player) || crescent.struck.contains(player.getUniqueId())) {
                    continue;
                }
                if (player.getLocation().add(0, 1, 0).distanceSquared(tip) > reach) {
                    continue;
                }
                crescent.struck.add(player.getUniqueId());
                BossHits.hurt(player, entity, powerForPhase(26, 32, 38));
                player.sendActionBar(TextUtil.component("&dMoon cut"));
            }
            if (crescent.travel >= 13.5) {
                retireCrescent(crescent);
                it.remove();
            }
        }
    }

    private void beginHonor(LivingEntity entity, Player target) {
        move = Move.HONOR;
        actionTick = 0;
        honorUsed = true;
        honorCut = false;
        honorTarget = target.getUniqueId();
        honorCenter = ground(entity.getLocation());
        honorTicks = 180;
        drawn = true;
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(target);
        }
        World world = entity.getWorld();
        world.playSound(honorCenter, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        world.playSound(honorCenter, Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.7f);
        world.playSound(honorCenter, Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.9f, 0.8f);
        shout("&d&lAshen Sheath&7: &fHonor circle. &cStep in — or burn outside.");
        titleNear(honorCenter, "&dHONOR CIRCLE", "&7Stay inside the petal ring. Outside burns.");
        clearLeafTornado();
        Vector inward = target.getLocation().toVector().subtract(honorCenter.toVector()).setY(0);
        if (inward.lengthSquared() < 0.04) {
            inward = new Vector(1, 0, 0);
        }
        Location inside = honorCenter.clone().add(inward.normalize().multiply(2.2));
        inside.setY(honorCenter.getY());
        inside.setYaw(target.getLocation().getYaw());
        inside.setPitch(target.getLocation().getPitch());
        target.teleport(inside);
    }

    private void tickHonorAction(LivingEntity entity) {
        actionTick++;
        if (actionTick >= 24) {
            endAction(entity);
        }
    }

    private void tickHonor(LivingEntity entity) {
        if (honorTicks <= 0 || honorCenter == null || honorCenter.getWorld() == null) {
            return;
        }
        honorTicks--;
        World world = honorCenter.getWorld();
        double radius = 7.5;
        ensureHonorRing(world);
        layHonorRing();
        if (honorTicks % 4 == 0) {
            ringArc(honorCenter, radius, honorTicks * 0.01, 0.0, 1.0, new Particle.DustOptions(LANE, 1.15f), 0.15);
        }
        if (honorTicks % 8 == 0) {
            ringArc(honorCenter, radius, 0.0, 0.0, 1.0, new Particle.DustOptions(SPINE, 0.7f), 1.1);
        }
        boolean closing = honorTicks <= 30;
        Player focus = honorTarget == null ? null : Bukkit.getPlayer(honorTarget);
        for (Player player : world.getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            double distance = horizontal(player.getLocation(), honorCenter);
            if (distance > radius) {
                if (honorTicks % 4 == 0 && distance < radius + 12.0) {
                    honorWall(player.getLocation());
                }
                if (honorTicks % 14 != 0) {
                    continue;
                }
                BossHits.hurt(player, entity, 14.0);
                player.sendActionBar(TextUtil.component("&c✖ OUTSIDE THE HONOR CIRCLE &8| &fstep back inside"));
                continue;
            }
            if (honorTicks % 10 == 0) {
                player.sendActionBar(TextUtil.component("&d◯ HONOR CIRCLE &8| &f"
                        + (closing ? "almost over" : "hold the ring") + " &8| " + meter(honorTicks, 180, "&d")));
            }
        }
        if (!honorCut && honorTicks == 90 && focus != null && move == Move.NONE && vulnerable(focus)) {
            double distance = horizontal(focus.getLocation(), honorCenter);
            if (distance <= radius) {
                honorCut = true;
                beginIaido(entity, focus);
            }
        }
        if (honorTicks == 0) {
            world.playSound(honorCenter, Sound.BLOCK_CHERRY_WOOD_STEP, 0.8f, 0.7f);
            world.playSound(honorCenter, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.7f, 1.1f);
            world.playSound(honorCenter, Sound.BLOCK_BELL_USE, 0.6f, 1.1f);
            for (BlockDisplay leaf : honorLeaves) {
                if (leaf != null && leaf.isValid()) {
                    world.spawnParticle(Particle.CHERRY_LEAVES, leaf.getLocation().add(0, 0.3, 0), 3, 0.2, 0.3, 0.2, 0.03);
                }
            }
            clearHonorRing();
            honorCenter = null;
            honorTarget = null;
            honorCd = HONOR_CD;
        }
    }

    private void beginFrenzy(LivingEntity entity, Player target) {
        move = Move.FRENZY;
        actionTick = 0;
        frenzyAimed = false;
        drawn = true;
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(target);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.35f);
        world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.5f);
        shout("&5&lAshen Sheath&7: &4Ashen Moon — &fno hesitation.");
        titleNear(entity.getLocation(), "&5ASHEN MOON", "&7Three cuts. Watch the line.");
        clearLeafTornado();
    }

    private void tickFrenzy(LivingEntity entity) {
        actionTick++;
        if (actionTick > 90) {
            endAction(entity);
            iaidoCd = iaidoCooldown();
            frenzyArmed = false;
            return;
        }
        int cycle = (actionTick - 1) % 30;
        int wave = (actionTick - 1) / 30;
        if (wave >= 3) {
            return;
        }
        if (cycle == 0) {
            clearLeafTornado();
            lastCutFrom = null;
            lastCutTo = null;
            Player prey = nearest(entity, 28.0);
            frenzyAimed = prey != null && aimLine(entity, prey, 7.5, 2.5);
            if (frenzyAimed) {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 0.7f + wave * 0.15f);
            }
            return;
        }
        if (cycle < 14) {
            if (frenzyAimed && iaidoFrom != null && iaidoTo != null && iaidoDir != null) {
                double charge = cycle / 14.0;
                int remaining = 14 - cycle;
                double length = Math.max(3.5, iaidoFrom.distance(iaidoTo));
                ensureLeaves(entity.getWorld(), iaidoFrom, 8);
                layPetals(iaidoFrom, iaidoDir, charge, length, cycle);
                slideDrawLine(iaidoFrom, iaidoDir, charge, length);
                drawLane(iaidoFrom, iaidoDir, length, 1.2, charge, cycle, remaining);
                fuseTick(entity.getLocation(), remaining);
                poseDraw(entity, charge);
                Location look = entity.getLocation().clone();
                look.setDirection(iaidoDir);
                entity.setRotation(look.getYaw(), 6.0f);
                if (cycle % 3 == 1) {
                    actionBarNear(entity, 26, "&4☾ ASHEN MOON &8| &cCut " + CUT_NUMERALS[wave] + "&8/&cIII &8| "
                            + (remaining <= 8 ? "&f&lOFF THE LANE!" : "&fstep off the lane"));
                }
            }
            return;
        }
        if (cycle == 22 && lastCutFrom != null && lastCutTo != null) {
            afterCut(lastCutFrom, lastCutTo);
            return;
        }
        if (cycle == 14 && frenzyAimed && iaidoFrom != null && iaidoTo != null) {
            clearDrawLine();
            Location land = iaidoTo.clone();
            if (iaidoDir != null) {
                land.setDirection(iaidoDir);
            }
            instance.runInternalTeleport(() -> {
                if (entity.isValid()) {
                    entity.teleport(land);
                }
            });
            poseDraw(entity, 1.0);
            if (iaidoDir != null) {
                double length = Math.max(3.5, iaidoFrom.distance(iaidoTo));
                layPetals(iaidoFrom, iaidoDir, 1.0, length, cycle);
            }
            cutLine(iaidoFrom.clone().add(0, 1.2, 0), land.clone().add(0, 1.2, 0), LANE_MOON);
            hitLine(entity, iaidoFrom, land, 1.55, 62.0);
            lastCutFrom = iaidoFrom.clone().add(0, 1.2, 0);
            lastCutTo = land.clone().add(0, 1.2, 0);
            entity.getWorld().spawnParticle(Particle.FLASH, land.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
            entity.getWorld().playSound(land, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.05f, 0.4f);
            entity.getWorld().playSound(land, Sound.ITEM_TRIDENT_THROW, 0.85f, 1.75f);
            frenzyAimed = false;
        }
    }

    private boolean aimLine(LivingEntity entity, Player target, double minimum, double extra) {
        iaidoFrom = ground(entity.getLocation());
        iaidoTo = ground(target.getLocation());
        Vector dir = iaidoTo.toVector().subtract(iaidoFrom.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0) {
            dir = entity.getLocation().getDirection().clone().setY(0);
            if (dir.lengthSquared() < 0.01) {
                dir = new Vector(1, 0, 0);
            }
            dir.normalize().multiply(minimum);
            iaidoTo = iaidoFrom.clone().add(dir);
        } else {
            double reach = Math.max(minimum, Math.min(14.0, iaidoFrom.distance(iaidoTo) + extra));
            dir.normalize();
            iaidoTo = iaidoFrom.clone().add(dir.clone().multiply(reach));
        }
        iaidoDir = iaidoTo.toVector().subtract(iaidoFrom.toVector());
        iaidoDir.setY(0);
        if (iaidoDir.lengthSquared() < 0.01) {
            iaidoDir = new Vector(1, 0, 0);
        }
        iaidoDir.normalize();
        return true;
    }

    private void tickAction(LivingEntity entity) {
        switch (move) {
            case IAIDO -> tickIaido(entity);
            case PARRY -> tickParry(entity);
            case AFTERIMAGE -> tickAfterimage(entity);
            case CRESCENT -> tickCrescent(entity);
            case HONOR -> tickHonorAction(entity);
            case FRENZY -> tickFrenzy(entity);
            default -> endAction(entity);
        }
    }

    private void endAction(LivingEntity entity) {
        clearLeafTornado();
        move = Move.NONE;
        actionTick = 0;
        if (!isDying() && instance.healthPercent() > 66.0) {
            drawn = false;
        }
        breath = breathForPhase();
        if (entity instanceof Mob mob && !instance.isTransitioning() && !isDying()) {
            mob.setAI(true);
        }
    }

    private int breathForPhase() {
        double pct = instance.healthPercent();
        if (pct <= 33.0) {
            return 16;
        }
        if (pct <= 66.0) {
            return 26;
        }
        return 38;
    }

    private void approach(LivingEntity entity) {
        Player target = nearest(entity, 30.0);
        if (target == null) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(target);
            mob.setAI(true);
        }
        Vector to = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        to.setY(0);
        double dist = Math.sqrt(Math.max(1.0E-4, to.lengthSquared()));
        Location look = entity.getLocation().clone();
        look.setDirection(to);
        entity.setRotation(look.getYaw(), 0.0f);
        if (dist < 2.8 && meleeCd <= 0) {
            meleeCd = 40;
            drawn = true;
            syncKatana(entity);
            slashArc(entity, target.getLocation(), bladeGlow(true));
            if (vulnerable(target)) {
                BossHits.hurt(target, entity, powerForPhase(32, 38, 45));
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.75f, 0.75f);
        }
    }

    private boolean tickDeath() {
        deathTicks++;
        LivingEntity entity = instance.getEntity();
        World world = deathFocus.getWorld();
        if (world == null) {
            abort();
            return true;
        }
        if (deathTicks < 110) {
            double grown = Math.min(1.0, deathTicks / 100.0);
            double scale = deathTicks >= 104 ? 2.2 : 0.22 + grown * grown * 1.55;
            growDeathSphere(scale);
        }
        if (deathTicks < 25) {
            double t = deathTicks / 24.0;
            if (entity != null && entity.isValid()) {
                entity.teleport(deathFocus.clone().add(0, -0.15 * t, 0));
            }
            if (deathBlade != null && deathBlade.isValid()) {
                Location bladeAt = deathFocus.clone().add(0, 0.2 + t * 1.4, 0);
                deathBlade.teleport(bladeAt);
                deathBlade.setTransformation(katanaTransform(0.55f + (float) t * 0.35f, true));
            }
            if (leaves.isEmpty()) {
                ensureLeaves(world, deathFocus, LEAF_COUNT);
            }
            spinLeaves(deathFocus, deathTicks, 1.1 + t * 2.6, 0.16);
            if (deathTicks % 8 == 0) {
                world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.55f, 0.6f + (float) t);
            }
            return false;
        }
        if (deathTicks < 71) {
            double t = (deathTicks - 25) / 45.0;
            if (entity != null && entity.isValid()) {
                entity.setInvisible(t > 0.35);
                entity.setGlowing(false);
                entity.teleport(deathFocus.clone().add(0, -0.35 * t, 0));
            }
            if (leaves.isEmpty()) {
                ensureLeaves(world, deathFocus, LEAF_COUNT);
            }
            spinLeaves(deathFocus, deathTicks, 1.4 + t * 2.8, 0.18);
            if (deathTicks % 10 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, deathFocus.clone().add(0, 1.2, 0), 3, 0.35, 0.45, 0.35, 0.01);
            }
            if (deathTicks == 40) {
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_RETURN, 1.15f, 0.45f);
                world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.55f);
            }
            return false;
        }
        if (deathTicks < 111) {
            double t = (deathTicks - 71) / 39.0;
            if (deathBlade != null && deathBlade.isValid()) {
                float shake = (float) (Math.sin(deathTicks * 1.4) * 0.06 * t);
                Location bladeAt = deathFocus.clone().add(shake, 1.55, shake * 0.6);
                deathBlade.teleport(bladeAt);
                if (deathTicks % 6 == 0) {
                    world.spawnParticle(Particle.DUST, bladeAt, 1, 0.04, 0.15, 0.04, 0, new Particle.DustOptions(PETAL, 1.0f));
                }
            }
            if (leaves.isEmpty()) {
                ensureLeaves(world, deathFocus, LEAF_COUNT);
            }
            spinLeaves(deathFocus, deathTicks, 2.6 + t * 2.4, 0.14);
            if (deathTicks % 5 == 0 && deathTicks < 100) {
                world.playSound(deathFocus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.35f + (float) t);
                world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.35f, 1.1f);
            }
            if (deathTicks == 100) {
                Location core = deathSphere != null && deathSphere.isValid()
                        ? deathSphere.getLocation()
                        : deathFocus.clone().add(0, 1.05, 0);
                double yaw = Math.toRadians(deathFocus.getYaw());
                Vector across = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
                cutLine(core.clone().subtract(across.clone().multiply(2.8)).add(0, 0.45, 0),
                        core.clone().add(across.clone().multiply(2.8)).subtract(0, 0.45, 0), PETAL);
                world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_THROW, 1.1f, 1.8f);
                world.playSound(deathFocus, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
            }
            if (deathTicks == 110) {
                clearDeathSphere();
                world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.2f, 0.45f);
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.8f);
                world.playSound(deathFocus, Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
                world.spawnParticle(Particle.FLASH, deathFocus.clone().add(0, 1.4, 0), 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CHERRY_LEAVES, deathFocus.clone().add(0, 1.4, 0), 22, 1.1, 0.7, 1.1, 0.05);
                petalBurst(deathFocus.clone().add(0, 1.4, 0), 14);
                petalCrest(deathFocus, 3.8, deathFocus.getYaw(), new Particle.DustOptions(PETAL, 1.3f));
                scatterLeaves(deathFocus);
            }
            return false;
        }
        if (deathTicks < DEATH_TICKS) {
            if (deathBlade != null && deathBlade.isValid() && deathTicks == 112) {
                deathBlade.remove();
                deathBlade = null;
            }
            if (deathTicks <= 134 && deathTicks % 6 == 0) {
                float fade = (float) (1.2 - (deathTicks - 110) / 24.0 * 0.6);
                petalCrest(deathFocus, 3.8, deathFocus.getYaw(), new Particle.DustOptions(PETAL, fade));
            }
            if (deathTicks % 4 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, deathFocus.clone().add(0, 4.5, 0), 4, 2.4, 0.3, 2.4, 0.0);
            }
            return false;
        }
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        if (deathBlade != null && deathBlade.isValid()) {
            deathBlade.remove();
        }
        deathBlade = null;
        clearDeathSphere();
        removeKatana();
        clearLeafTornado();
        return true;
    }

    private void dressSensei(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(trimmed(Material.NETHERITE_HELMET, TrimMaterial.AMETHYST, TrimPattern.SILENCE));
        equipment.setChestplate(trimmed(Material.NETHERITE_CHESTPLATE, TrimMaterial.AMETHYST, TrimPattern.WARD));
        equipment.setLeggings(trimmed(Material.NETHERITE_LEGGINGS, TrimMaterial.AMETHYST, TrimPattern.DUNE));
        equipment.setBoots(trimmed(Material.NETHERITE_BOOTS, TrimMaterial.AMETHYST, TrimPattern.SENTRY));
        equipment.setItemInMainHand(new ItemStack(Material.AIR));
        equipment.setItemInOffHand(new ItemStack(Material.AIR));
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private static ItemStack trimmed(Material material, TrimMaterial trim, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        ItemMeta itemMeta = stack.getItemMeta();
        if (itemMeta instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(trim, pattern));
            meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void spawnKatana(LivingEntity entity) {
        removeKatana();
        World world = entity.getWorld();
        katana = world.spawn(katanaAnchor(entity), ItemDisplay.class, display -> {
            display.setItemStack(katanaItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            display.setBrightness(new Display.Brightness(12, 12));
            display.setTransformation(katanaTransform(0.7f, true));
            display.setGlowColorOverride(PETAL);
            display.setGlowing(true);
            display.setPersistent(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        props.add(katana);
        spawnHipSheath(entity);
    }

    private ItemDisplay spawnGhostKatana(Location at) {
        ItemDisplay ghost = at.getWorld().spawn(at.clone().add(0, 1.1, 0), ItemDisplay.class, display -> {
            display.setItemStack(katanaItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(katanaTransform(0.55f, false));
            display.setGlowColorOverride(PETAL);
            display.setGlowing(false);
            display.setBrightness(new Display.Brightness(8, 8));
            display.setPersistent(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        props.add(ghost);
        return ghost;
    }

    private ItemDisplay spawnGhostSheath(Location at) {
        if (at.getWorld() == null) {
            return null;
        }
        ItemDisplay sheath = at.getWorld().spawn(at.clone().add(0, 0.95, 0), ItemDisplay.class, display -> prepareProp(display, sheathItem(), false));
        props.add(sheath);
        return sheath;
    }

    /** A skull over the blade so each shadow reads as a copy of the boss, not a floating sword. */
    private ItemDisplay spawnGhostHead(Location at) {
        if (at.getWorld() == null) {
            return null;
        }
        float yaw = at.getYaw();
        ItemDisplay head = at.getWorld().spawn(at.clone().add(0, 2.45, 0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.WITHER_SKELETON_SKULL));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            display.setBrightness(new Display.Brightness(7, 7));
            display.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(1.35f, 1.35f, 1.35f),
                    new AxisAngle4f()
            ));
            display.setRotation(yaw, 0.0f);
            display.setGlowing(true);
            display.setGlowColorOverride(PETAL);
            display.setPersistent(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        props.add(head);
        return head;
    }

    private void plantDeathBlade(Location at) {
        if (deathBlade != null && deathBlade.isValid()) {
            deathBlade.remove();
        }
        deathBlade = at.getWorld().spawn(at.clone().add(0, 0.3, 0), ItemDisplay.class, display -> {
            display.setItemStack(katanaItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(katanaTransform(0.55f, true));
            display.setPersistent(false);
            display.setGlowing(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        removeKatana();
    }

    private void plantDeathSphere(Location at) {
        clearDeathSphere();
        if (at == null || at.getWorld() == null) {
            return;
        }
        deathSphere = at.getWorld().spawn(at.clone().add(0, 1.05, 0), BlockDisplay.class, spawned -> {
            spawned.setBlock(cherryLeafBlock());
            spawned.setTransformation(sphereTransform(0.22f, 0));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(2);
            spawned.setInterpolationDuration(3);
            spawned.setInterpolationDelay(0);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(PETAL);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
    }

    private void growDeathSphere(double scale) {
        if (deathSphere == null || !deathSphere.isValid() || deathFocus == null) {
            return;
        }
        float size = (float) Math.max(0.15, scale);
        LivingEntity entity = instance.getEntity();
        Location at;
        if (entity != null && entity.isValid() && !entity.isInvisible()) {
            at = entity.getLocation().clone().add(0, Math.max(0.9, entity.getHeight() * 0.55), 0);
        } else {
            at = deathFocus.clone().add(0, 1.05, 0);
        }
        deathSphere.teleport(at);
        deathSphere.setInterpolationDuration(2);
        deathSphere.setInterpolationDelay(0);
        deathSphere.setTransformation(sphereTransform(size, deathTicks));
    }

    private void clearDeathSphere() {
        if (deathSphere != null && deathSphere.isValid()) {
            deathSphere.remove();
        }
        deathSphere = null;
    }

    private static Transformation sphereTransform(float scale, int tick) {
        return new Transformation(
                new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f),
                new AxisAngle4f(tick * 0.08f, 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        );
    }

    private static ItemStack katanaItem() {
        ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dAshen Katana");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Transformation katanaTransform(float scale, boolean upright) {
        float sx = 0.28f * scale;
        float sy = 1.85f * scale;
        float sz = 0.28f * scale;
        AxisAngle4f left = upright
                ? new AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
                : new AxisAngle4f((float) Math.toRadians(-55.0), 0.0f, 0.0f, 1.0f);
        return new Transformation(
                new Vector3f(0.0f, upright ? 0.1f : 0.0f, 0.0f),
                left,
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)
        );
    }

    private Location katanaAnchor(LivingEntity entity) {
        Location base = entity.getLocation().clone().add(0, entity.getHeight() * 0.55, 0);
        float yaw = base.getYaw();
        double rad = Math.toRadians(yaw);
        Vector right = new Vector(-Math.cos(rad), 0, -Math.sin(rad));
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        if (right.lengthSquared() > 0.01) {
            right.normalize();
        }
        if (forward.lengthSquared() > 0.01) {
            forward.normalize();
        }
        if (drawn) {
            return base.clone().add(right.multiply(0.45)).add(forward.multiply(0.55)).add(0, 0.15, 0);
        }
        return base.clone().add(right.multiply(-0.35)).add(forward.multiply(-0.25)).add(0, 0.35, 0);
    }

    private void syncKatana(LivingEntity entity) {
        if (katana == null || !katana.isValid()) {
            spawnKatana(entity);
            return;
        }
        Location anchor = katanaAnchor(entity);
        katana.teleport(anchor);
        katana.setRotation(entity.getLocation().getYaw() + (drawn ? 95.0f : -25.0f), drawn ? -25.0f : -70.0f);
        katana.setTransformation(katanaTransform(drawn ? 0.78f : 0.62f, !drawn));
        katana.setGlowColorOverride(bladeGlow(drawn));
        syncHipSheath(entity);
    }

    private void removeKatana() {
        dropProp(katana);
        katana = null;
        dropProp(hipSheath);
        hipSheath = null;
    }

    private void spawnHipSheath(LivingEntity entity) {
        dropProp(hipSheath);
        hipSheath = null;
        World world = entity.getWorld();
        hipSheath = world.spawn(sheathAnchor(entity), ItemDisplay.class, display -> prepareProp(display, sheathItem(), false));
        props.add(hipSheath);
    }

    private void syncHipSheath(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (hipSheath == null || !hipSheath.isValid()) {
            spawnHipSheath(entity);
        }
        hipSheath.teleport(sheathAnchor(entity));
        hipSheath.setRotation(entity.getLocation().getYaw() - 16.0f, -74.0f);
        hipSheath.setTransformation(sheathTransform());
    }

    private Location sheathAnchor(LivingEntity entity) {
        Location base = entity.getLocation().clone().add(0, entity.getHeight() * 0.52, 0);
        float yaw = base.getYaw();
        double rad = Math.toRadians(yaw);
        Vector right = new Vector(-Math.cos(rad), 0, -Math.sin(rad));
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        if (right.lengthSquared() > 0.01) {
            right.normalize();
        }
        if (forward.lengthSquared() > 0.01) {
            forward.normalize();
        }
        return base.clone().add(right.multiply(-0.42)).add(forward.multiply(-0.18)).add(0, 0.15, 0);
    }

    private static ItemStack sheathItem() {
        ItemStack stack = new ItemStack(Material.BAMBOO);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dSheath");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Transformation sheathTransform() {
        return new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                new Vector3f(0.42f, 1.35f, 0.42f),
                new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)
        );
    }

    private void prepareProp(ItemDisplay display, ItemStack item, boolean blade) {
        display.setItemStack(item);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
        display.setBrightness(new Display.Brightness(10, 10));
        display.setGlowing(false);
        display.setPersistent(false);
        display.setTransformation(blade ? katanaTransform(0.7f, true) : sheathTransform());
        instance.getKeys().tagBeamFx(display, instance.getInstanceId());
    }

    private void slideDrawLine(Location origin, Vector facing, double progress, double length) {
        if (origin == null || origin.getWorld() == null || facing == null) {
            return;
        }
        Vector dir = facing.clone().setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        progress = Math.max(0.0, Math.min(1.0, progress));
        World world = origin.getWorld();
        if (lineBlade == null || !lineBlade.isValid()) {
            lineBlade = world.spawn(origin.clone().add(0, 0.9, 0), ItemDisplay.class, display -> prepareProp(display, katanaItem(), true));
            props.add(lineBlade);
        }
        float yaw = yawOf(dir);
        double reach = 0.45 + progress * Math.max(2.5, length);
        Location bladeAt = origin.clone().add(dir.multiply(reach)).add(0, 0.4 + (1.0 - progress) * 0.7, 0);
        lineBlade.teleport(bladeAt);
        float pitch = (float) (-78.0 + progress * 68.0);
        lineBlade.setRotation(yaw, pitch);
        lineBlade.setTransformation(katanaTransform(0.74f, progress < 0.22));
    }

    private void clearDrawLine() {
        dropProp(lineBlade);
        lineBlade = null;
    }

    private void dropProp(ItemDisplay display) {
        if (display == null) {
            return;
        }
        if (display.isValid()) {
            display.remove();
        }
        props.remove(display);
    }

    private static float yawOf(Vector facing) {
        return (float) Math.toDegrees(Math.atan2(-facing.getX(), facing.getZ()));
    }

    /**
     * Default leaf data is distance 7 and not persistent, so clients skip the model on a display.
     */
    private static org.bukkit.block.data.BlockData cherryLeafBlock() {
        org.bukkit.block.data.type.Leaves leaves =
                (org.bukkit.block.data.type.Leaves) Material.CHERRY_LEAVES.createBlockData();
        leaves.setPersistent(true);
        leaves.setDistance(1);
        return leaves;
    }

    private void ensureLeaves(World world, Location focus, int count) {
        if (world == null || focus == null) {
            return;
        }
        if (leaves.size() == count) {
            boolean alive = true;
            for (BlockDisplay leaf : leaves) {
                if (leaf == null || !leaf.isValid()) {
                    alive = false;
                    break;
                }
            }
            if (alive) {
                return;
            }
        }
        clearLeavesOnly();
        for (int i = 0; i < count; i++) {
            leaves.add(spawnLeaf(world, focus));
        }
        world.playSound(focus, Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.55f, 0.7f);
    }

    private BlockDisplay spawnLeaf(World world, Location at) {
        float wide = 0.78f;
        float thick = 0.12f;
        return world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(cherryLeafBlock());
            spawned.setTransformation(new Transformation(
                    new Vector3f(-wide / 2f, -thick / 2f, -wide / 2f),
                    new AxisAngle4f(0.4f, 1f, 0.15f, 0f),
                    new Vector3f(wide, thick, wide * 0.72f),
                    new AxisAngle4f()
            ));
            spawned.setBrightness(new Display.Brightness(15, 12));
            spawned.setTeleportDuration(2);
            spawned.setInterpolationDuration(2);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setGlowing(false);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
    }

    /**
     * One straight cut. Leaves ahead of the blade hang, then sit on the ground it has crossed.
     * Offsets are fixed so the line stays readable and does not weave.
     */
    private void layPetals(Location origin, Vector facing, double progress, double length, int tick) {
        layPetals(leaves, origin, facing, progress, length, tick);
    }

    private void layPetals(List<BlockDisplay> petals, Location origin, Vector facing, double progress, double length, int tick) {
        int count = petals == null ? 0 : petals.size();
        if (count == 0 || origin == null || facing == null) {
            return;
        }
        Vector dir = facing.clone().setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        progress = Math.max(0.0, Math.min(1.0, progress));
        double span = Math.max(2.5, length);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = petals.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double slot = (i + 0.5) / count;
            double along = 0.65 + slot * span;
            boolean passed = slot <= progress;
            double hang = passed ? 0.0 : (slot - progress);
            double sideOff = (i % 2 == 0) ? 0.22 : -0.22;
            double y = passed ? 0.08 : 0.4 + hang * 2.35;
            Location at = origin.clone()
                    .add(dir.clone().multiply(along))
                    .add(side.clone().multiply(sideOff))
                    .add(0, y, 0);
            posePetal(leaf, at, i, tick, passed);
        }
    }

    /** Still ring around the body. It lifts when the parry window opens. It does not spin. */
    private void layLeafRing(Location center, double radius, double height, boolean lifted) {
        int count = leaves.size();
        if (count == 0 || center == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double ang = (Math.PI * 2 * i) / count;
            posePetal(leaf, center.clone().add(Math.cos(ang) * radius, height, Math.sin(ang) * radius), i, 0, !lifted);
        }
    }

    /** Vertical column with a small sway. Not a widening helix and not a spinning ring. */
    private void spinLeaves(Location focus, int tick, double height, double sway) {
        int count = leaves.size();
        if (count == 0 || focus == null) {
            return;
        }
        double column = Math.max(1.4, height);
        double drift = 0.18 + Math.max(0.0, sway);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double along = i / (double) count;
            double y = (along * column + tick * 0.035) % column;
            double x = Math.sin(tick * 0.07 + i * 0.8) * drift;
            double z = Math.cos(tick * 0.06 + i * 0.55) * drift;
            posePetal(leaf, focus.clone().add(x, 0.2 + y, z), i, tick, false);
        }
    }

    private void posePetal(BlockDisplay leaf, Location at, int index, int tick, boolean settled) {
        leaf.teleport(at);
        float tumble = settled
                ? (float) (index * 0.35)
                : (float) (0.35 + Math.sin(tick * 0.12 + index) * 0.25);
        float wide = 0.8f;
        float thick = settled ? 0.1f : 0.12f;
        leaf.setInterpolationDuration(2);
        leaf.setInterpolationDelay(0);
        leaf.setTransformation(new Transformation(
                new Vector3f(-wide / 2f, -thick / 2f, -wide / 2f),
                new AxisAngle4f(tumble, 1f, 0.18f, 0.04f),
                new Vector3f(wide, thick, wide * 0.7f),
                new AxisAngle4f()
        ));
    }

    private void scatterLeaves(Location focus) {
        int count = leaves.size();
        if (focus == null || count == 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double ang = (Math.PI * 2 * i) / count;
            double radius = 2.2 + (i % 5) * 0.45;
            posePetal(
                    leaf,
                    focus.clone().add(Math.cos(ang) * radius, 0.2 + (i % 4) * 0.18, Math.sin(ang) * radius),
                    i,
                    0,
                    true
            );
        }
    }

    private void petalIdle(LivingEntity entity) {
        double pct = instance.healthPercent();
        long every = pct <= 33.0 ? 14L : pct <= 66.0 ? 12L : 20L;
        if (instance.getTicksAlive() % every != 0L) {
            return;
        }
        World world = entity.getWorld();
        Location at = entity.getLocation().add(0, 1.15, 0);
        int count = pct <= 66.0 && pct > 33.0 ? 2 : 1;
        world.spawnParticle(Particle.CHERRY_LEAVES, at, count, 0.22, 0.3, 0.22, 0.004);
        if (pct <= 33.0) {
            world.spawnParticle(Particle.DUST, at, 1, 0.1, 0.16, 0.1, 0, new Particle.DustOptions(BLOOD, 0.7f));
        }
    }

    private void petalBurst(Location at, int count) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, Math.min(count, 36), 0.5, 0.4, 0.5, 0.03);
        at.getWorld().spawnParticle(
                Particle.DUST,
                at,
                Math.max(3, count / 4),
                0.3,
                0.3,
                0.3,
                0,
                new Particle.DustOptions(PETAL, 1.15f)
        );
    }

    private void poseGuard(LivingEntity entity, double openness) {
        if (entity == null || katana == null || !katana.isValid()) {
            return;
        }
        openness = Math.max(0.0, Math.min(1.0, openness));
        syncHipSheath(entity);
        Location anchor = katanaAnchor(entity);
        katana.teleport(anchor.add(0, 0.1 + openness * 0.12, 0));
        float yaw = entity.getLocation().getYaw() + (float) (38.0 + openness * 32.0);
        float pitch = (float) (-32.0 - openness * 8.0);
        katana.setRotation(yaw, pitch);
        katana.setTransformation(katanaTransform(0.7f, false));
        katana.setGlowColorOverride(openness > 0.85
                ? (move == Move.PARRY ? GUARD : instance.healthPercent() <= 33.0 ? BLOOD : MOON)
                : PETAL);
    }

    private void armCrescent(Crescent crescent) {
        World world = crescent.origin.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < 7; i++) {
            crescent.petals.add(spawnLeaf(world, crescent.origin));
        }
        crescent.edge = world.spawn(
                crescent.origin.clone().add(0, 0.9, 0),
                ItemDisplay.class,
                display -> prepareProp(display, katanaItem(), true)
        );
        props.add(crescent.edge);
    }

    /** The moon cut is the leaf snake bent into an arc, with the blade on the crest. */
    private void poseCrescent(Crescent crescent, double travel, boolean tell) {
        Vector dir = crescent.dir.clone();
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location tip = crescent.origin.clone().add(dir.clone().multiply(tell ? 0.6 : travel));
        int count = crescent.petals.size();
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = crescent.petals.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double u = count == 1 ? 0.0 : (i / (double) (count - 1)) - 0.5;
            double ang = u * Math.toRadians(tell ? 48.0 : 78.0);
            double sideOff = Math.sin(ang) * (tell ? 1.15 : 1.75);
            double lift = Math.cos(ang) * (tell ? 0.35 : 0.55);
            Location at = tip.clone().add(side.clone().multiply(sideOff)).add(0, lift, 0);
            posePetal(leaf, at, i, 0, true);
        }
        World world = tip.getWorld();
        if (!tell && world != null && crescent.age % 2 == 0) {
            Color moon = instance.healthPercent() <= 33.0 ? LANE_MOON : MOON;
            Particle.DustOptions rim = new Particle.DustOptions(moon, 1.15f);
            Particle.DustOptions core = new Particle.DustOptions(HOT, 0.6f);
            for (int k = 0; k <= 10; k++) {
                double ang = (k / 10.0 - 0.5) * Math.toRadians(78.0);
                Location arc = tip.clone()
                        .add(side.clone().multiply(Math.sin(ang) * 1.75))
                        .add(dir.clone().multiply(0.15))
                        .add(0, Math.cos(ang) * 0.55, 0);
                spawnDust(world, arc, rim);
                if (k % 2 == 0) {
                    spawnDust(world, arc.clone().subtract(dir.clone().multiply(0.2)), core);
                }
            }
            if (crescent.age % 4 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK, tip.clone().add(0, 0.45, 0), 1, 0, 0, 0, 0);
            }
            world.spawnParticle(Particle.CHERRY_LEAVES, tip.clone().subtract(dir.clone().multiply(0.8)).add(0, 0.4, 0),
                    1, 0.5, 0.15, 0.5, 0.01);
        }
        if (crescent.edge != null && crescent.edge.isValid()) {
            crescent.edge.teleport(tip.clone().add(0, 0.2, 0));
            crescent.edge.setRotation(yawOf(dir), tell ? -48.0f : -12.0f);
            crescent.edge.setTransformation(katanaTransform(0.6f, false));
            crescent.edge.setGlowColorOverride(instance.healthPercent() <= 33.0 ? BLOOD : MOON);
        }
    }

    private void retireCrescent(Crescent crescent) {
        clearTrail(crescent.petals);
        dropProp(crescent.edge);
        crescent.edge = null;
    }

    private void ensureHonorRing(World world) {
        if (honorCenter == null || world == null || honorLeaves.size() == 16) {
            return;
        }
        clearHonorRing();
        for (int i = 0; i < 16; i++) {
            honorLeaves.add(spawnLeaf(world, honorCenter));
        }
    }

    /** Floor boundary. The leaves do not travel. Inside is safe, outside is not. */
    private void layHonorRing() {
        int count = honorLeaves.size();
        if (count == 0 || honorCenter == null) {
            return;
        }
        double radius = 7.5;
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = honorLeaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double ang = (Math.PI * 2 * i) / count;
            Location at = honorCenter.clone().add(Math.cos(ang) * radius, 0.1, Math.sin(ang) * radius);
            posePetal(leaf, at, i, 0, true);
        }
    }

    private void clearHonorRing() {
        clearTrail(honorLeaves);
    }

    private void poseDraw(LivingEntity entity, double charge) {
        if (entity == null || katana == null || !katana.isValid()) {
            return;
        }
        charge = Math.max(0.0, Math.min(1.0, charge));
        syncHipSheath(entity);
        Location anchor = katanaAnchor(entity).add(0, 0.18 * charge, 0);
        katana.teleport(anchor);
        float yaw = entity.getLocation().getYaw() + (float) (-25.0 + charge * 120.0);
        float pitch = (float) (-70.0 + charge * 46.0);
        katana.setRotation(yaw, pitch);
        katana.setTransformation(katanaTransform(0.62f + (float) charge * 0.16f, charge > 0.78));
        katana.setGlowColorOverride(bladeGlow(charge > 0.72));
    }

    /** One clean crescent, not a portal beam. A thin white core rides inside the colored edge. */
    private void slashSweep(Location from, Location to, Color color) {
        World world = from.getWorld();
        if (world == null || to == null) {
            return;
        }
        Vector along = to.toVector().subtract(from.toVector());
        along.setY(0);
        if (along.lengthSquared() < 0.01) {
            along = new Vector(0, 0, 1);
        }
        along.normalize();
        Vector side = new Vector(-along.getZ(), 0, along.getX());
        Particle.DustOptions edge = new Particle.DustOptions(color, 1.35f);
        Particle.DustOptions core = new Particle.DustOptions(HOT, 0.6f);
        int steps = 20;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double hump = Math.sin(t * Math.PI) * 0.7;
            Location point = lerp(from, to, t).add(side.clone().multiply(hump)).add(0, 0.15 + Math.sin(t * Math.PI) * 0.3, 0);
            spawnDust(world, point, edge);
            if (i % 2 == 0) {
                spawnDust(world, point.clone().subtract(side.clone().multiply(0.12)), core);
            }
            if (i % 4 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            }
            if (i % 5 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.04, 0.05, 0.04, 0.01);
            }
        }
    }

    /** The released straight cut, drawn on the exact hit line: white core, colored blade edges, sparse sweeps. */
    private void cutLine(Location from, Location to, Color color) {
        World world = from == null ? null : from.getWorld();
        if (world == null || to == null) {
            return;
        }
        Vector span = to.toVector().subtract(from.toVector());
        double len = span.length();
        if (len < 0.1) {
            return;
        }
        Vector unit = span.clone().multiply(1.0 / len);
        Particle.DustOptions core = new Particle.DustOptions(HOT, 0.75f);
        Particle.DustOptions edge = new Particle.DustOptions(color, 1.45f);
        int index = 0;
        for (double d = 0.0; d <= len; d += 0.25, index++) {
            Location point = from.clone().add(unit.clone().multiply(d));
            spawnDust(world, point, core);
            if (index % 2 == 0) {
                spawnDust(world, point.clone().add(0, index % 4 == 0 ? 0.14 : -0.14, 0), edge);
            }
        }
        for (double d = 0.9; d < len; d += 2.2) {
            world.spawnParticle(Particle.SWEEP_ATTACK, from.clone().add(unit.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.35f);
    }

    /** A beat after the cut: petals burst along the line it already crossed and the blade clicks home. */
    private void afterCut(Location from, Location to) {
        World world = from == null ? null : from.getWorld();
        if (world == null || to == null) {
            return;
        }
        Vector span = to.toVector().subtract(from.toVector());
        double len = span.length();
        if (len < 0.1) {
            return;
        }
        Vector unit = span.clone().multiply(1.0 / len);
        Particle.DustTransition fade = new Particle.DustTransition(PETAL, ASH, 1.1f);
        for (double d = 0.4; d <= len; d += 0.9) {
            Location point = from.clone().add(unit.clone().multiply(d));
            world.spawnParticle(Particle.CHERRY_LEAVES, point, 2, 0.18, 0.14, 0.18, 0.02);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 2, 0.12, 0.1, 0.12, 0, fade);
        }
        world.playSound(to, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.9f, 1.7f);
        world.playSound(to, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.8f, 1.25f);
    }

    /**
     * Ground tell for an aimed cut. Edges sit at the hit width, the spine fills with the draw,
     * and the edges strobe white for the last beats before release.
     */
    private void drawLane(Location from, Vector dir, double length, double halfWidth, double fill, int tick, int remaining) {
        World world = from == null ? null : from.getWorld();
        if (world == null || dir == null) {
            return;
        }
        Vector unit = dir.clone().setY(0);
        if (unit.lengthSquared() < 0.01) {
            return;
        }
        unit.normalize();
        boolean hot = remaining <= 8;
        if (!hot && tick % 2 != 0) {
            return;
        }
        Vector side = new Vector(-unit.getZ(), 0, unit.getX());
        Location base = from.clone().add(0, 0.12, 0);
        Color edgeColor = hot && remaining % 2 == 0 ? HOT : laneColor();
        Particle.DustOptions edge = new Particle.DustOptions(edgeColor, hot ? 1.25f : 1.0f);
        Particle.DustOptions spine = new Particle.DustOptions(SPINE, 0.8f);
        for (double d = 0.0; d <= length + 1.0E-4; d += 0.7) {
            Location on = base.clone().add(unit.clone().multiply(d));
            spawnDust(world, on.clone().add(side.clone().multiply(halfWidth)), edge);
            spawnDust(world, on.clone().subtract(side.clone().multiply(halfWidth)), edge);
        }
        Location cap = base.clone().add(unit.clone().multiply(length));
        for (double s = -halfWidth; s <= halfWidth + 1.0E-4; s += 0.4) {
            spawnDust(world, cap.clone().add(side.clone().multiply(s)), edge);
        }
        double reach = Math.max(0.0, Math.min(1.0, fill)) * length;
        for (double d = 0.0; d <= reach; d += 0.5) {
            spawnDust(world, base.clone().add(unit.clone().multiply(d)), spine);
        }
    }

    /** Audible fuse: tick… tick.. tick. tick — then the draw sound lands on release. */
    private void fuseTick(Location at, int remaining) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        if (remaining == 12 || remaining == 8 || remaining == 5 || remaining == 3 || remaining == 1) {
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.85f, remaining <= 3 ? 1.9f : 1.4f);
        }
    }

    private Color laneColor() {
        return instance.healthPercent() <= 33.0 ? LANE_MOON : LANE;
    }

    /**
     * Dotted lock-on line from a shadow to its prey. Dots tighten and whiten as the shadow draws;
     * out of hit reach it stays a thin ash line.
     */
    private void tether(Location from, Location to, double charge, boolean inReach) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) {
            return;
        }
        Particle.DustOptions dust = inReach
                ? new Particle.DustOptions(charge > 0.7 ? HOT : LANE, 0.7f + (float) charge * 0.5f)
                : new Particle.DustOptions(ASH, 0.6f);
        int dots = 8 + (int) Math.round(charge * 6);
        for (int i = 1; i < dots; i++) {
            spawnDust(world, lerp(from, to, i / (double) dots), dust);
        }
    }

    /** Sparse ash-and-petal body under the ghost head: spine, shoulders, arms, hips, legs. */
    private void ghostBody(Location origin, Vector facing) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Vector side = new Vector(-facing.getZ(), 0, facing.getX());
        Particle.DustTransition ash = new Particle.DustTransition(PETAL, ASH, 1.0f);
        double[][] joints = {
                {0.0, 2.15}, {0.0, 1.8}, {0.0, 1.45},
                {0.45, 2.1}, {-0.45, 2.1},
                {0.55, 1.65}, {-0.55, 1.65},
                {0.22, 1.15}, {-0.22, 1.15},
                {0.24, 0.62}, {-0.24, 0.62},
                {0.26, 0.12}, {-0.26, 0.12}
        };
        for (double[] joint : joints) {
            Location at = origin.clone().add(side.clone().multiply(joint[0])).add(0, joint[1], 0);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.03, 0.05, 0.03, 0, ash);
        }
    }

    private void guardShatter(Location feet) {
        World world = feet.getWorld();
        if (world == null) {
            return;
        }
        ringArc(feet, 1.3, 0.0, 0.0, 1.0, new Particle.DustOptions(GUARD, 1.4f), 1.2);
        ringArc(feet, 2.0, 0.3, 0.0, 1.0, new Particle.DustOptions(HOT, 0.9f), 0.9);
    }

    /** Red section of the honor boundary facing a player who is outside it. */
    private void honorWall(Location outside) {
        if (honorCenter == null || honorCenter.getWorld() == null) {
            return;
        }
        double toward = Math.atan2(outside.getZ() - honorCenter.getZ(), outside.getX() - honorCenter.getX());
        double radius = 7.5;
        double span = 0.35;
        Particle.DustOptions dust = new Particle.DustOptions(LANE_MOON, 1.1f);
        for (int layer = 0; layer < 3; layer++) {
            ringArc(honorCenter, radius, toward - span, 0.0, span / Math.PI, dust, 0.3 + layer * 0.7);
        }
    }

    /** Five-petal blossom outline on the floor, notched at each tip like a real sakura petal. */
    private void petalCrest(Location center, double radius, float yawDeg, Particle.DustOptions dust) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double turn = Math.toRadians(yawDeg);
        int points = 120;
        for (int i = 0; i < points; i++) {
            double theta = Math.PI * 2 * i / points;
            double r = radius * blossom(theta);
            double ang = theta + turn;
            spawnDust(world, center.clone().add(Math.cos(ang) * r, 0.12, Math.sin(ang) * r), dust);
        }
    }

    private static double blossom(double theta) {
        double lobe = Math.abs(Math.cos(theta * 2.5));
        double phase = (theta * 2.5) % Math.PI;
        double fromTip = Math.min(phase, Math.PI - phase);
        double notch = 0.2 * Math.exp(-(fromTip * fromTip) / 0.03);
        return Math.max(0.1, Math.pow(lobe, 0.6) - notch);
    }

    private static void ringArc(Location center, double radius, double angle0, double from, double to,
                                Particle.DustOptions dust, double y) {
        World world = center.getWorld();
        if (world == null || to <= from) {
            return;
        }
        int points = Math.max(8, (int) Math.ceil(Math.PI * 2 * radius / 0.55));
        int first = (int) Math.floor(points * Math.max(0.0, from));
        int last = (int) Math.ceil(points * Math.min(1.0, to));
        for (int i = first; i < last; i++) {
            double ang = angle0 + Math.PI * 2 * i / points;
            spawnDust(world, center.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius), dust);
        }
    }

    private static double facingAngle(LivingEntity entity) {
        Vector facing = entity.getLocation().getDirection().setY(0);
        return facing.lengthSquared() < 0.01 ? 0.0 : Math.atan2(facing.getZ(), facing.getX());
    }

    private static void spawnDust(World world, Location at, Particle.DustOptions dust) {
        world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
    }

    private static String meter(int left, int total, String color) {
        int cells = 10;
        int lit = (int) Math.ceil(cells * Math.max(0, left) / (double) Math.max(1, total));
        StringBuilder out = new StringBuilder(color);
        for (int i = 0; i < cells; i++) {
            if (i == lit) {
                out.append("&8");
            }
            out.append('■');
        }
        return out.toString();
    }

    private void slashArc(LivingEntity entity, Location toward, Color color) {
        Location from = entity.getEyeLocation();
        Vector dir = toward.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = entity.getLocation().getDirection().clone().setY(0);
        }
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        for (int deg = -70; deg <= 70; deg += 10) {
            double rad = Math.toRadians(deg);
            Vector offset = dir.clone().multiply(Math.cos(rad) * 2.5).add(side.clone().multiply(Math.sin(rad) * 2.5));
            Location point = from.clone().add(offset);
            world.spawnParticle(Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(color, 1.15f));
            if (deg % 20 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.03, 0.04, 0.03, 0.01);
            }
        }
    }

    private void hitLine(LivingEntity source, Location from, Location to, double thickness, double power) {
        World world = from.getWorld();
        if (world == null || to == null) {
            return;
        }
        Vector span = to.toVector().subtract(from.toVector());
        double len = span.length();
        if (len < 0.1) {
            return;
        }
        Vector unit = span.clone().normalize();
        double thick2 = thickness * thickness;
        for (Player player : world.getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            Vector toPlayer = player.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            double along = toPlayer.dot(unit);
            if (along < -0.4 || along > len + 0.4) {
                continue;
            }
            Vector closest = from.toVector().add(unit.clone().multiply(Math.max(0.0, Math.min(len, along))));
            if (player.getLocation().add(0, 1, 0).toVector().distanceSquared(closest) > thick2) {
                continue;
            }
            BossHits.crush(player, source, power);
            player.setVelocity(unit.clone().multiply(1.05).setY(0.32));
        }
    }

    private void clearCombatFx() {
        for (Afterimage ghost : afterimages) {
            dropProp(ghost.blade);
            dropProp(ghost.sheath);
            dropProp(ghost.head);
        }
        afterimages.clear();
        for (Crescent crescent : crescents) {
            retireCrescent(crescent);
        }
        crescents.clear();
        clearHonorRing();
        clearDeathSphere();
        for (ItemDisplay prop : props) {
            if (prop == null || !prop.isValid() || prop == katana) {
                continue;
            }
            prop.remove();
        }
        props.removeIf(prop -> prop == null || !prop.isValid() || prop == katana);
        clearLeafTornado();
        honorTicks = 0;
        honorCenter = null;
        honorTarget = null;
        move = Move.NONE;
        actionTick = 0;
    }

    private Color bladeGlow(boolean live) {
        if (!live) {
            return PETAL;
        }
        return instance.healthPercent() <= 33.0 ? BLOOD : PETAL;
    }

    /** Armor trim is the phase tell: amethyst, then copper, then redstone. */
    private void refreshSilhouette(LivingEntity entity) {
        int band = instance.healthPercent() <= 33.0 ? 2 : instance.healthPercent() <= 66.0 ? 1 : 0;
        if (band == silhouette || entity == null) {
            return;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        silhouette = band;
        TrimMaterial metal = switch (band) {
            case 2 -> TrimMaterial.REDSTONE;
            case 1 -> TrimMaterial.COPPER;
            default -> TrimMaterial.AMETHYST;
        };
        equipment.setHelmet(trimmed(Material.NETHERITE_HELMET, metal, TrimPattern.SILENCE));
        equipment.setChestplate(trimmed(Material.NETHERITE_CHESTPLATE, metal, TrimPattern.WARD));
        equipment.setLeggings(trimmed(Material.NETHERITE_LEGGINGS, metal, TrimPattern.DUNE));
        equipment.setBoots(trimmed(Material.NETHERITE_BOOTS, metal, TrimPattern.SENTRY));
    }

    private int iaidoCooldown() {
        double pct = instance.healthPercent();
        if (pct <= 33.0) {
            return IAIDO_CD_P3;
        }
        if (pct <= 66.0) {
            return IAIDO_CD_P2;
        }
        return IAIDO_CD_P1;
    }

    private double powerForPhase(double quiet, double blossoms, double moon) {
        double pct = instance.healthPercent();
        if (pct <= 33.0) {
            return moon;
        }
        if (pct <= 66.0) {
            return blossoms;
        }
        return quiet;
    }

    private void shout(String message) {
        LivingEntity entity = instance.getEntity();
        Location origin = entity != null && entity.isValid() ? entity.getLocation() : deathFocus;
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) < 64 * 64) {
                player.sendMessage(TextUtil.component(message));
            }
        }
    }

    private void titleNear(Location origin, String main, String sub) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        Title title = Title.title(TextUtil.component(main), TextUtil.component(sub), titleTimes());
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= 48 * 48) {
                player.showTitle(title);
            }
        }
    }

    private void actionBarNear(LivingEntity entity, double range, String message) {
        double r2 = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= r2) {
                player.sendActionBar(TextUtil.component(message));
            }
        }
    }

    private static Title.Times titleTimes() {
        return Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(900), Duration.ofMillis(280));
    }

    private Player nearest(LivingEntity entity, double range) {
        return nearest(entity, entity.getLocation(), range);
    }

    private Player nearest(LivingEntity entity, Location from, double range) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = range * range;
        for (Player player : from.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDist) {
                bestDist = distance;
                best = player;
            }
        }
        return best;
    }

    private static boolean vulnerable(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static Location ground(Location loc) {
        Location at = loc.clone();
        World world = at.getWorld();
        if (world == null) {
            return at;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int start = at.getBlockY();
        int min = Math.max(world.getMinHeight() + 1, start - 8);
        for (int y = start; y >= min; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && below.getType().isSolid()) {
                at.setY(y);
                return at;
            }
        }
        return at;
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Location lerp(Location a, Location b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return a.clone().add((b.getX() - a.getX()) * t, (b.getY() - a.getY()) * t, (b.getZ() - a.getZ()) * t);
    }

    private static final class Afterimage {
        private final Location origin;
        private final ItemDisplay blade;
        private final ItemDisplay sheath;
        private final ItemDisplay head;
        private final int fireAt;
        private int tick;

        private Afterimage(Location origin, ItemDisplay blade, ItemDisplay sheath, ItemDisplay head, int fireAt) {
            this.origin = origin;
            this.blade = blade;
            this.sheath = sheath;
            this.head = head;
            this.fireAt = fireAt;
        }
    }

    private static final class Crescent {
        private final Location origin;
        private final Vector dir;
        private final double speed;
        private final List<BlockDisplay> petals = new ArrayList<>();
        private final Set<UUID> struck = new HashSet<>();
        private ItemDisplay edge;
        private int delay;
        private int age;
        private double travel;

        private Crescent(Location origin, Vector dir, int delay, double speed) {
            this.origin = origin;
            this.dir = dir;
            this.delay = delay;
            this.speed = speed;
        }
    }
}
