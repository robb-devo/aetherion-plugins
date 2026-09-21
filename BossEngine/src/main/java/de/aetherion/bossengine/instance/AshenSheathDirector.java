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
 * Quiet steel, then falling blossoms, then ashen moon.
 * Windups stay on the ground long enough to read. Moon phase spins real cherry leaves.
 */
final class AshenSheathDirector {

    private static final Color PETAL = Color.fromRGB(255, 170, 200);
    private static final Color ASH = Color.fromRGB(90, 70, 95);
    private static final Color MOON = Color.fromRGB(210, 230, 255);
    private static final Color BLOOD = Color.fromRGB(160, 30, 50);
    private static final Color STEEL = Color.fromRGB(220, 225, 235);

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
    private boolean drawn;
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
    private UUID honorTarget;
    private Location honorCenter;
    private int honorTicks;
    private int deathTicks = -1;
    private Location deathFocus;
    private ItemDisplay deathBlade;

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
        spawnKatana(entity);
        drawn = false;
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
            entity.setGlowing(true);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
        }
        World world = deathFocus.getWorld();
        world.playSound(deathFocus, Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.55f);
        world.playSound(deathFocus, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.7f);
        world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.5f);
        shout("&d&lAshen Sheath&7: &f…the cut was always meant for me.");
        titleNear(deathFocus, "&dSHEATHED", "&7The grove takes the blade back.");
        plantDeathBlade(deathFocus);
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
        world.spawnParticle(Particle.FLASH, entity.getEyeLocation(), 1, 0, 0, 0, 0);
        petalBurst(entity.getEyeLocation(), 22);
        shout("&d&lAshen Sheath&7: &cParried.");
        striker.showTitle(Title.title(
                TextUtil.component("&cPARRIED"),
                TextUtil.component("&7The sheath was the answer."),
                titleTimes()
        ));
        Location behind = striker.getLocation().clone().add(striker.getLocation().getDirection().multiply(-1.4));
        behind.setY(striker.getLocation().getY());
        Vector look = striker.getLocation().toVector().subtract(behind.toVector());
        if (look.lengthSquared() > 0.01) {
            behind.setDirection(look);
        }
        drawn = true;
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(behind);
            }
        });
        syncKatana(entity);
        slashLine(entity.getEyeLocation(), striker.getEyeLocation(), BLOOD, true);
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
     * Real cherry-leaf column. Caller must {@link #clearLeafTornado()} when the beat ends.
     */
    void tickCherryTornado(Location focus, int tick, int duration) {
        if (focus == null || focus.getWorld() == null) {
            clearLeafTornado();
            return;
        }
        ensureLeaves(focus.getWorld(), focus, LEAF_COUNT);
        double progress = tick / (double) Math.max(1, duration);
        double height = 2.6 + progress * 3.6;
        spinLeaves(focus, tick, height, 0.22);
        World world = focus.getWorld();
        if (tick % 10 == 0) {
            world.playSound(focus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.65f, 0.55f + (float) progress * 0.5f);
            world.playSound(focus, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.28f, 1.25f + (float) progress * 0.3f);
        }
        if (tick % 6 == 0) {
            world.spawnParticle(Particle.CHERRY_LEAVES, focus.clone().add(0, 1.6, 0), 3, 0.7, 1.1, 0.7, 0.01);
            world.spawnParticle(Particle.END_ROD, focus.clone().add(0, 0.4 + progress * 2.2, 0), 1, 0.15, 0.2, 0.15, 0.001);
        }
    }

    void clearLeafTornado() {
        for (BlockDisplay leaf : leaves) {
            if (leaf != null && leaf.isValid()) {
                leaf.remove();
            }
        }
        leaves.clear();
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
            syncKatana(entity);
            return false;
        }
        tickCooldowns();
        syncKatana(entity);
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
        titleNear(entity.getLocation(), "&dIAIDO", "&7The line is the cut. Step off it.");
    }

    private void tickIaido(LivingEntity entity) {
        actionTick++;
        World world = entity.getWorld();
        if (actionTick <= IAIDO_WINDUP) {
            drawn = false;
            if (iaidoFrom != null && iaidoTo != null) {
                paintIaidoLine(iaidoFrom, iaidoTo, actionTick / (double) IAIDO_WINDUP);
            }
            if (iaidoDir != null) {
                Location look = entity.getLocation().clone();
                look.setDirection(iaidoDir);
                entity.setRotation(look.getYaw(), 8.0f);
            }
            if (actionTick % 8 == 0) {
                world.playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.32f, 0.5f + actionTick * 0.018f);
            }
            if (actionTick == IAIDO_WINDUP) {
                world.playSound(entity.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.25f, 1.65f);
                world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.4f);
                world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.4f);
            }
            return;
        }
        if (actionTick == IAIDO_WINDUP + 1) {
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
            if (iaidoFrom != null) {
                world.spawnParticle(Particle.FLASH, iaidoFrom.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
                slashLine(iaidoFrom.clone().add(0, 1.15, 0), land.clone().add(0, 1.15, 0), STEEL, true);
                hitLine(entity, iaidoFrom, land, 1.45, powerForPhase(48, 58, 70));
                petalBurst(iaidoFrom.clone().add(0, 1, 0), 28);
            }
            world.spawnParticle(Particle.FLASH, land.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
            petalBurst(land.clone().add(0, 1, 0), 28);
            return;
        }
        if (actionTick < IAIDO_WINDUP + 20) {
            drawn = true;
            return;
        }
        endAction(entity);
        iaidoCd = iaidoCooldown();
    }

    private void beginParry(LivingEntity entity) {
        move = Move.PARRY;
        actionTick = 0;
        drawn = true;
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.8f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.8f);
        shout("&d&lAshen Sheath&7: &fCome.");
        titleNear(entity.getLocation(), "&fCOME", "&7The sheath is listening.");
    }

    private void tickParry(LivingEntity entity) {
        actionTick++;
        World world = entity.getWorld();
        Location at = entity.getEyeLocation();
        if (actionTick % 3 == 0) {
            world.spawnParticle(Particle.DUST, at, 3, 0.32, 0.35, 0.32, new Particle.DustOptions(MOON, 1.15f));
            world.spawnParticle(Particle.END_ROD, at, 1, 0.15, 0.2, 0.15, 0.001);
        }
        ringPetals(entity.getLocation(), 1.7 + Math.sin(actionTick * 0.2) * 0.18, 12);
        if (actionTick == PARRY_OPEN) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 0.85f, 1.9f);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.7f);
            titleNear(entity.getLocation(), "&fSTRIKE", "&7The window is open.");
        }
        if (parrying() && actionTick % 10 == 0) {
            actionBarNear(entity, 14, "&dStrike the sheath &7— &fthe window is open");
        }
        if (actionTick == PARRY_CLOSE + 1) {
            Player near = nearest(entity, 5.2);
            if (near != null && vulnerable(near)) {
                slashArc(entity, near.getLocation(), BLOOD);
                BossHits.hurt(near, entity, powerForPhase(36, 42, 50));
                world.playSound(near.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, 0.55f);
                near.sendActionBar(TextUtil.component("&cThe sheath answered itself."));
            } else {
                world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.4f);
            }
        }
        if (actionTick >= PARRY_CLOSE + 12) {
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
        world.playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.55f);
        world.playSound(entity.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.7f);
        shout("&d&lAshen Sheath&7 splits into &fblossom shadows&7.");
        titleNear(entity.getLocation(), "&dBLOSSOM SHADOW", "&7The copies draw first.");
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
            afterimages.add(new Afterimage(spot, spawnGhostKatana(spot), 36 + i * 16));
        }
    }

    private void tickAfterimage(LivingEntity entity) {
        actionTick++;
        if (actionTick < 16) {
            if (actionTick % 4 == 0) {
                petalBurst(entity.getLocation().add(0, 1, 0), 6);
            }
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
            if (ghost.blade != null && ghost.blade.isValid()) {
                float yaw = ghost.origin.getYaw() + ghost.tick * 10.0f;
                ghost.blade.teleport(ghost.origin.clone().add(0, 1.15, 0));
                ghost.blade.setRotation(yaw, -10.0f);
                if (ghost.tick % 2 == 0) {
                    ghost.origin.getWorld().spawnParticle(
                            Particle.DUST,
                            ghost.origin.clone().add(0, 1.2, 0),
                            1,
                            0.12,
                            0.25,
                            0.12,
                            new Particle.DustOptions(PETAL, 1.0f)
                    );
                }
            }
            if (ghost.tick == ghost.fireAt) {
                Player prey = nearest(entity, ghost.origin, 14.0);
                Location tip = prey != null
                        ? prey.getEyeLocation()
                        : ghost.origin.clone().add(ghost.origin.getDirection().multiply(6)).add(0, 1.2, 0);
                slashLine(ghost.origin.clone().add(0, 1.2, 0), tip, PETAL, false);
                if (prey != null && vulnerable(prey) && prey.getLocation().distanceSquared(ghost.origin) < 64.0) {
                    BossHits.hurt(prey, entity, powerForPhase(28, 34, 40));
                }
                ghost.origin.getWorld().playSound(ghost.origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.85f, 1.35f);
                petalBurst(ghost.origin.clone().add(0, 1, 0), 12);
            }
            if (ghost.tick <= ghost.fireAt + 10) {
                continue;
            }
            if (ghost.blade != null && ghost.blade.isValid()) {
                ghost.blade.remove();
            }
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
        titleNear(entity.getLocation(), "&dMOON CUT", "&7Sidestep the arc.");
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
            crescents.add(new Crescent(
                    entity.getLocation().clone().add(0, 1.1, 0),
                    dir.clone(),
                    18 + i * 14,
                    0.42 + i * 0.08
            ));
        }
    }

    private void tickCrescent(LivingEntity entity) {
        actionTick++;
        if (actionTick >= 70 && crescents.isEmpty()) {
            endAction(entity);
            crescentCd = CRESCENT_CD;
        }
    }

    private void tickCrescents(LivingEntity entity) {
        if (crescents.isEmpty()) {
            return;
        }
        Iterator<Crescent> it = crescents.iterator();
        while (it.hasNext()) {
            Crescent crescent = it.next();
            if (crescent.delay > 0) {
                crescent.delay--;
                if (crescent.delay == 0 && crescent.origin.getWorld() != null) {
                    crescent.origin.getWorld().playSound(crescent.origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 1.7f);
                }
                continue;
            }
            crescent.travel += crescent.speed;
            Location tip = crescent.origin.clone().add(crescent.dir.clone().multiply(crescent.travel));
            World world = tip.getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            for (int a = -5; a <= 5; a++) {
                double ang = Math.toRadians(a * 14);
                Vector side = new Vector(-crescent.dir.getZ(), 0, crescent.dir.getX()).multiply(Math.sin(ang) * 1.7);
                Location point = tip.clone().add(side).add(0, Math.cos(ang) * 0.5, 0);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(MOON, 1.3f));
                if (a % 3 == 0) {
                    world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.02, 0.02, 0.02, 0);
                }
            }
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
        world.playSound(honorCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 1.35f);
        world.playSound(honorCenter, Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.7f);
        world.playSound(honorCenter, Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.9f, 0.8f);
        shout("&d&lAshen Sheath&7: &fHonor circle. &cStep in — or burn outside.");
        titleNear(honorCenter, "&dHONOR CIRCLE", "&7Inside the ring. Outside, the grove bites.");
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
        for (int i = 0; i < 28; i++) {
            double ang = Math.PI * 2 * i / 28.0 + honorTicks * 0.035;
            Location point = honorCenter.clone().add(Math.cos(ang) * radius, 0.15, Math.sin(ang) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(ASH, 1.25f));
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.04, 0.08, 0.04, 0);
            }
        }
        Player focus = honorTarget == null ? null : Bukkit.getPlayer(honorTarget);
        for (Player player : world.getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            double distance = horizontal(player.getLocation(), honorCenter);
            if (distance > radius) {
                if (honorTicks % 14 != 0) {
                    continue;
                }
                BossHits.hurt(player, entity, 14.0);
                player.sendActionBar(TextUtil.component("&cOutside the honor circle"));
                petalBurst(player.getLocation().add(0, 1, 0), 4);
                continue;
            }
            if (player.getUniqueId().equals(honorTarget) && honorTicks % 20 == 0) {
                player.sendActionBar(TextUtil.component("&dHonor circle &7— &fhold the ring"));
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
            world.playSound(honorCenter, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.8f);
            world.playSound(honorCenter, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.7f, 1.1f);
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
        world.playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.45f, 1.7f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
        shout("&5&lAshen Sheath&7: &4Ashen Moon — &fno hesitation.");
        titleNear(entity.getLocation(), "&5ASHEN MOON", "&7Three cuts. Watch the line.");
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
            Player prey = nearest(entity, 28.0);
            frenzyAimed = prey != null && aimLine(entity, prey, 7.5, 2.5);
            if (frenzyAimed) {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 0.7f + wave * 0.15f);
            }
            return;
        }
        if (cycle < 14) {
            if (frenzyAimed && iaidoFrom != null && iaidoTo != null) {
                paintIaidoLine(iaidoFrom, iaidoTo, cycle / 14.0);
                if (iaidoDir != null) {
                    Location look = entity.getLocation().clone();
                    look.setDirection(iaidoDir);
                    entity.setRotation(look.getYaw(), 6.0f);
                }
            }
            return;
        }
        if (cycle == 14 && frenzyAimed && iaidoFrom != null && iaidoTo != null) {
            Location land = iaidoTo.clone();
            if (iaidoDir != null) {
                land.setDirection(iaidoDir);
            }
            instance.runInternalTeleport(() -> {
                if (entity.isValid()) {
                    entity.teleport(land);
                }
            });
            slashLine(iaidoFrom.clone().add(0, 1.2, 0), land.clone().add(0, 1.2, 0), BLOOD, true);
            hitLine(entity, iaidoFrom, land, 1.55, 62.0);
            entity.getWorld().spawnParticle(Particle.FLASH, land.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
            entity.getWorld().playSound(land, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.05f, 0.4f);
            entity.getWorld().playSound(land, Sound.ITEM_TRIDENT_THROW, 0.85f, 1.75f);
            petalBurst(land.clone().add(0, 1, 0), 22);
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
        move = Move.NONE;
        actionTick = 0;
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
            slashArc(entity, target.getLocation(), STEEL);
            if (vulnerable(target)) {
                BossHits.hurt(target, entity, powerForPhase(32, 38, 45));
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.75f, 0.75f);
            petalBurst(target.getLocation().add(0, 1, 0), 6);
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
            ringPetals(deathFocus, 2.0 + t * 3.2, 16);
            if (deathTicks % 6 == 0) {
                world.playSound(deathFocus, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.7f, 0.6f + (float) t);
            }
            return false;
        }
        if (deathTicks < 71) {
            double t = (deathTicks - 25) / 45.0;
            if (entity != null && entity.isValid()) {
                entity.setInvisible(t > 0.35);
                entity.setGlowing(t < 0.7);
                entity.teleport(deathFocus.clone().add(0, -0.35 * t, 0));
            }
            if (leaves.isEmpty()) {
                ensureLeaves(world, deathFocus, 16);
            }
            spinLeaves(deathFocus, deathTicks, 1.4 + t * 2.8, 0.18);
            if (deathTicks % 4 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, deathFocus.clone().add(0, 1.2, 0), 4, 0.6, 0.8, 0.6, 0.01);
            }
            if (deathTicks == 40) {
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_RETURN, 1.15f, 0.45f);
                world.playSound(deathFocus, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.55f);
            }
            return false;
        }
        if (deathTicks == 71) {
            clearLeafTornado();
        }
        if (deathTicks < 111) {
            double t = (deathTicks - 71) / 39.0;
            if (deathBlade != null && deathBlade.isValid()) {
                float shake = (float) (Math.sin(deathTicks * 1.4) * 0.06 * t);
                Location bladeAt = deathFocus.clone().add(shake, 1.55, shake * 0.6);
                deathBlade.teleport(bladeAt);
                world.spawnParticle(Particle.DUST, bladeAt, 4, 0.06, 0.35, 0.06, 0, new Particle.DustOptions(MOON, 1.35f));
                if (deathTicks % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, bladeAt, 1, 0.04, 0.2, 0.04, 0.001);
                }
            }
            if (deathTicks % 5 == 0) {
                world.playSound(deathFocus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.35f + (float) t);
            }
            if (deathTicks == 110) {
                world.playSound(deathFocus, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.5f);
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.8f);
                world.spawnParticle(Particle.FLASH, deathFocus.clone().add(0, 1.6, 0), 1, 0, 0, 0, 0);
                petalBurst(deathFocus.clone().add(0, 1.4, 0), 48);
            }
            return false;
        }
        if (deathTicks < DEATH_TICKS) {
            if (deathBlade != null && deathBlade.isValid() && deathTicks == 112) {
                deathBlade.remove();
                deathBlade = null;
            }
            if (deathTicks % 4 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, deathFocus.clone().add(0, 0.3, 0), 4, 1.0, 0.15, 1.0, 0.01);
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
    }

    private ItemDisplay spawnGhostKatana(Location at) {
        ItemDisplay ghost = at.getWorld().spawn(at.clone().add(0, 1.1, 0), ItemDisplay.class, display -> {
            display.setItemStack(katanaItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(katanaTransform(0.55f, false));
            display.setGlowColorOverride(ASH);
            display.setGlowing(true);
            display.setBrightness(new Display.Brightness(8, 8));
            display.setPersistent(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        props.add(ghost);
        return ghost;
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
            display.setGlowColorOverride(MOON);
            display.setGlowing(true);
            display.setPersistent(false);
            instance.getKeys().tagBeamFx(display, instance.getInstanceId());
        });
        if (katana != null && katana.isValid()) {
            katana.remove();
        }
        katana = null;
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
        katana.setGlowColorOverride(drawn ? BLOOD : PETAL);
    }

    private void removeKatana() {
        if (katana != null && katana.isValid()) {
            katana.remove();
        }
        katana = null;
    }

    private void ensureLeaves(World world, Location focus, int count) {
        if (!leaves.isEmpty() || world == null || focus == null) {
            return;
        }
        float size = 0.46f;
        for (int i = 0; i < count; i++) {
            int index = i;
            BlockDisplay display = world.spawn(focus, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.CHERRY_LEAVES.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-size / 2f, -size / 2f, -size / 2f),
                        new AxisAngle4f(),
                        new Vector3f(size, size, size),
                        new AxisAngle4f()
                ));
                spawned.setBrightness(new Display.Brightness(12, 12));
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(2);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(index % 3 == 0 ? PETAL : MOON);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
            });
            leaves.add(display);
        }
        world.playSound(focus, Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.9f, 0.6f);
        world.playSound(focus, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.45f, 1.35f);
    }

    private void spinLeaves(Location focus, int tick, double height, double spin) {
        int count = leaves.size();
        if (count == 0 || focus == null) {
            return;
        }
        double column = Math.max(1.2, height);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double along = i / (double) count;
            double y = (along * column + tick * 0.055) % column;
            double arm = (i % 4) * (Math.PI / 2.0);
            double yaw = tick * spin + arm + y * 0.85;
            double radius = 0.7 + y * 0.36;
            leaf.teleport(focus.clone().add(Math.cos(yaw) * radius, 0.2 + y, Math.sin(yaw) * radius));
        }
    }

    private void petalIdle(LivingEntity entity) {
        if (instance.getTicksAlive() % 8L != 0L) {
            return;
        }
        World world = entity.getWorld();
        Location at = entity.getLocation().add(0, 1.1, 0);
        world.spawnParticle(Particle.CHERRY_LEAVES, at, 1, 0.3, 0.45, 0.3, 0.008);
        if (drawn) {
            world.spawnParticle(Particle.DUST, at, 1, 0.15, 0.25, 0.15, 0, new Particle.DustOptions(BLOOD, 0.85f));
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

    private void ringPetals(Location center, double radius, int points) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double ang = Math.PI * 2 * i / points;
            Location point = center.clone().add(Math.cos(ang) * radius, 0.2, Math.sin(ang) * radius);
            world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.02, 0.04, 0.02, 0);
        }
    }

    private void paintIaidoLine(Location from, Location to, double charge) {
        World world = from.getWorld();
        if (world == null || to == null) {
            return;
        }
        int steps = 24;
        Color color = mix(ASH, BLOOD, charge);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = lerp(from, to, t).add(0, 0.12, 0);
            world.spawnParticle(Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(color, 1.2f));
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.04, 0.04, 0.04, 0);
            }
        }
        world.spawnParticle(Particle.END_ROD, from.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, to.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
    }

    private void slashLine(Location from, Location to, Color color, boolean heavy) {
        World world = from.getWorld();
        if (world == null || to == null) {
            return;
        }
        int steps = heavy ? 28 : 16;
        for (int i = 0; i <= steps; i++) {
            Location point = lerp(from, to, i / (double) steps);
            world.spawnParticle(
                    Particle.DUST,
                    point,
                    heavy ? 2 : 1,
                    0.02,
                    0.04,
                    0.02,
                    0,
                    new Particle.DustOptions(color, heavy ? 1.55f : 1.1f)
            );
            if (heavy && i % 3 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            }
        }
    }

    private void slashArc(LivingEntity entity, Location toward, Color color) {
        Location from = entity.getEyeLocation();
        Vector dir = toward.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.01) {
            dir = entity.getLocation().getDirection();
        }
        dir.normalize();
        slashLine(from, from.clone().add(dir.multiply(3.2)), color, false);
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
            if (ghost.blade != null && ghost.blade.isValid()) {
                ghost.blade.remove();
            }
        }
        afterimages.clear();
        crescents.clear();
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

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    private static final class Afterimage {
        private final Location origin;
        private final ItemDisplay blade;
        private final int fireAt;
        private int tick;

        private Afterimage(Location origin, ItemDisplay blade, int fireAt) {
            this.origin = origin;
            this.blade = blade;
            this.fireAt = fireAt;
        }
    }

    private static final class Crescent {
        private final Location origin;
        private final Vector dir;
        private final double speed;
        private final Set<UUID> struck = new HashSet<>();
        private int delay;
        private double travel;

        private Crescent(Location origin, Vector dir, int delay, double speed) {
            this.origin = origin;
            this.dir = dir;
            this.delay = delay;
            this.speed = speed;
        }
    }
}
