package net.aetherion.samuraiboss.boss;

import com.destroystokyo.paper.entity.Pathfinder;
import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.render.anim.Animator;
import net.aetherion.samuraiboss.render.anim.Clip;
import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.render.model.Pose;
import net.aetherion.samuraiboss.render.model.Skin;
import net.aetherion.samuraiboss.util.Keys;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Sakuragi Jinsai als Wesen in der Welt:
 * <ul>
 *   <li>eine unsichtbare <b>Hitbox</b> (Mob) – trägt Position, Pfadfindung und echten
 *       Vanilla-Schaden (Schwert-Verzauberungen, Krits, Bögen funktionieren normal)</li>
 *   <li>das sichtbare <b>Display-Modell</b>, das der Hitbox folgt</li>
 *   <li>der <b>Animator</b> + prozedurale Ebenen (Atmen, Wind in Haar/Bart/Banner, Kopf schaut
 *       zum Ziel, Hüftplatten flattern beim Laufen)</li>
 * </ul>
 * Lebenspunkte sind virtuell und werden von {@link Fight} verwaltet.
 */
public final class SamuraiBoss {

    private final Fight fight;
    private final Settings s;
    private final ModelDef def;
    public final SamuraiAnimations anims;
    public final SamuraiSkins skins;
    private ModelInstance model;
    private final Animator animator;
    private final Pose pose;
    private Mob hitbox;
    private Location loc;
    private Location prevLoc;
    private float yaw;
    private float targetYaw;
    private float turnSpeed = 16f;
    private boolean drawn;
    private boolean invulnerable = true;
    private float hover, hoverTarget;
    private Animator.Listener listener;
    private int tick;
    private int lastModelUpdate;
    private int flashCooldown;
    private float wind = 0.35f;
    private float speed;
    private float headYaw;
    private LivingEntity lookTarget;
    private boolean autoBase = true;
    private Location pendingMove;
    private int pendingSmooth = 2;
    private int restoreSmoothAt = -1;
    private boolean pathing;

    public SamuraiBoss(Fight fight) {
        this.fight = fight;
        this.s = fight.settings();
        this.def = SamuraiModel.def();
        this.anims = fight.plugin().animations();
        this.skins = new SamuraiSkins(s);
        this.animator = new Animator(def);
        this.pose = new Pose(def.boneCount());
    }

    // ================================================================== Lebenszyklus

    public void spawn(Location at, float facing, Clip base) {
        loc = at.clone();
        prevLoc = loc.clone();
        yaw = facing;
        targetYaw = facing;
        model = new ModelInstance(def, skins.normal, fight.arena().id, s.packEnabled, 2);
        model.spawn(loc);
        model.setScale(SamuraiModel.BASE_SCALE * s.scale);
        model.setYaw(yaw);
        model.setViewRange(3f);
        model.setWorldBrightness(11, 13);
        setDrawn(false);
        spawnHitbox(loc);
        animator.playBase(base, 0);
        animator.sample(pose);
        procedural();
        model.update(pose, 0);
    }

    private void spawnHitbox(Location at) {
        Class<? extends Entity> cls = s.hitboxEntity.getEntityClass();
        if (cls == null || !Mob.class.isAssignableFrom(cls)) cls = IronGolem.class;
        @SuppressWarnings("unchecked")
        Class<? extends Mob> mobCls = (Class<? extends Mob>) cls;
        Location l = at.clone();
        l.setYaw(yaw);
        hitbox = l.getWorld().spawn(l, mobCls, CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
            m.setPersistent(false);
            Keys.mark(m, "hitbox", fight.arena().id);
            m.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, fight.arena().id);
            // Same key BossEngine / AetherionItems use — Items reclaim + custom weapon damage apply.
            m.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("bossengine", "boss_id"),
                    PersistentDataType.STRING,
                    "sakuragi_jinsai");
            m.setInvisible(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.customName(Component.text("Sakuragi Jinsai"));
            m.setCustomNameVisible(false);
            setAttr(m, Attribute.GENERIC_MAX_HEALTH, 1000.0);
            m.setHealth(1000.0);
            // Zero armor so Aetherion stamped damage is not crushed before we read it.
            setAttr(m, Attribute.GENERIC_ARMOR, 0.0);
            setAttr(m, Attribute.GENERIC_ARMOR_TOUGHNESS, 0.0);
            setAttr(m, Attribute.GENERIC_SCALE, s.hitboxScale);
            setAttr(m, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
            setAttr(m, Attribute.GENERIC_MOVEMENT_SPEED, s.movementAttribute);
            setAttr(m, Attribute.GENERIC_STEP_HEIGHT, 1.1);
            setAttr(m, Attribute.GENERIC_FOLLOW_RANGE, 64.0);
            if (m instanceof IronGolem g) g.setPlayerCreated(true);
        });
        Bukkit.getMobGoals().removeAllGoals(hitbox);
        Pathfinder pf = hitbox.getPathfinder();
        pf.setCanOpenDoors(false);
        pf.setCanPassDoors(true);
    }

    private static void setAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    public void remove() {
        if (model != null) model.remove();
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
    }

    // ================================================================== Zugriff

    public Mob hitbox() {
        return hitbox;
    }

    public boolean isHitbox(Entity e) {
        return hitbox != null && e.getUniqueId().equals(hitbox.getUniqueId());
    }

    public ModelInstance model() {
        return model;
    }

    public Animator animator() {
        return animator;
    }

    /** Die zuletzt berechnete Pose inkl. prozeduraler Ebenen (für Schattenklone). */
    public Pose currentPose() {
        return pose;
    }

    /** Tick-Zähler des Bosses. */
    public int age() {
        return tick;
    }

    public Location location() {
        return loc.clone();
    }

    /** Augenhöhe (für Linien, Kanji über dem Kopf …). */
    public Location eye() {
        return loc.clone().add(0, 2.2 * s.scale + hover, 0);
    }

    public float yaw() {
        return yaw;
    }

    public boolean drawn() {
        return drawn;
    }

    public boolean invulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean v) {
        invulnerable = v;
    }

    public void setWind(float w) {
        wind = w;
    }

    public void setTurnSpeed(float degPerTick) {
        turnSpeed = degPerTick;
    }

    // ================================================================== Aussehen

    public void setDrawn(boolean d) {
        drawn = d;
        if (model != null) {
            model.setTagHidden(SamuraiModel.T_KATANA_HAND, !d);
            model.setTagHidden(SamuraiModel.T_KATANA_SHEATHED, d);
        }
    }

    public void setSkin(Skin skin) {
        model.setSkin(skin);
    }

    public void setAmbient(int block, int sky) {
        model.setWorldBrightness(block, sky);
    }

    public void setHover(float h) {
        hoverTarget = h;
    }

    public void flash() {
        if (flashCooldown > 0) return;
        flashCooldown = 6;
        model.flash(2);
    }

    /** Spielt eine Aktion. Eingebaute Ereignisse (draw/sheathe/click) werden hier erledigt. */
    public void play(Clip clip, Animator.Listener l) {
        play(clip, 3, 4, 1f, l);
    }

    public void play(Clip clip, int blendIn, int blendOut, float speed, Animator.Listener l) {
        this.listener = l;
        animator.play(clip, blendIn, blendOut, speed, ev -> {
            switch (ev) {
                case "draw" -> {
                    setDrawn(true);
                    fight.sfx().play(handLocation(), Sfx.Id.DRAW);
                }
                case "sheathe" -> setDrawn(false);
                case "click" -> fight.sfx().play(loc.clone().add(0, 1.2, 0), Sfx.Id.CLICK);
                case "open" -> fight.sfx().play(loc.clone().add(0, 1.1, 0), Sfx.Id.OPEN);
                case "flick" -> fight.sfx().play(handLocation(), Sfx.Id.WHOOSH, 0.6f, 1.4f);
                default -> {
                }
            }
            Animator.Listener cur = listener;
            if (cur != null) cur.onEvent(ev);
        });
    }

    /** Basis-Schleife festlegen (autoBase=false: KI wählt nicht mehr selbst idle/walk). */
    public void base(Clip clip, int blend) {
        animator.playBase(clip, blend);
    }

    public void setAutoBase(boolean auto) {
        autoBase = auto;
    }

    public void stopAction(int blend) {
        animator.stopAction(blend);
        listener = null;
    }

    public boolean busyAnimating() {
        return animator.isActionPlaying();
    }

    public Location handLocation() {
        try {
            return model.bonePoint("katana", 0, 0, 6);
        } catch (RuntimeException ex) {
            return eye();
        }
    }

    /** Spitze der Klinge in Weltkoordinaten. */
    public Location bladeTip() {
        return model.bonePoint("katana", -5.1f + 5.1f, 0, 17.5f);
    }

    // ================================================================== Bewegung

    public void face(Location target) {
        targetYaw = MathUtil.yawTo(loc, target);
    }

    public void faceYaw(float y) {
        targetYaw = y;
    }

    public void snapYaw(float y) {
        yaw = y;
        targetYaw = y;
    }

    public void lookAt(LivingEntity e) {
        lookTarget = e;
    }

    /** Läuft per Pfadfindung zu einer Stelle. */
    public void walkTo(Location target, double speedMul) {
        if (hitbox == null) return;
        pathing = hitbox.getPathfinder().moveTo(target, s.walkSpeed * speedMul);
    }

    public void stopWalking() {
        if (hitbox != null) hitbox.getPathfinder().stopPathfinding();
        pathing = false;
    }

    /**
     * Versetzt den Boss. smoothTicks = Dauer der sichtbaren Gleitbewegung des Modells
     * (0 = Sprung, 2 = blitzschneller Dash, 10 = Schweben). Wird im nächsten Tick ausgeführt,
     * damit die Gleit-Dauer beim Client rechtzeitig ankommt.
     */
    public void moveTo(Location target, int smoothTicks) {
        stopWalking();
        model.setMoveSmoothing(smoothTicks);
        pendingMove = target.clone();
        pendingSmooth = smoothTicks;
        // Hitbox sofort (unsichtbar) – Schaden/Trefferabfrage nutzen die neue Lage
        Location h = target.clone();
        h.setYaw(yaw);
        hitbox.teleport(h);
    }

    public void setGravity(boolean g) {
        if (hitbox != null) {
            hitbox.setGravity(g);
            if (!g) hitbox.setVelocity(new org.bukkit.util.Vector());
        }
    }

    public float speed() {
        return speed;
    }

    // ================================================================== Tick

    public void tick() {
        tick++;
        if (flashCooldown > 0) flashCooldown--;
        ensureHitbox();
        // Position: der Hitbox folgen (oder ausstehende Bewegung ausführen)
        if (pendingMove != null) {
            loc = pendingMove;
            pendingMove = null;
            model.moveTo(loc);
            restoreSmoothAt = tick + Math.max(1, pendingSmooth) + 1;
        } else if (hitbox != null && hitbox.isValid()) {
            Location h = hitbox.getLocation();
            if (h.getWorld() == loc.getWorld()) {
                loc.setX(h.getX());
                loc.setY(h.getY());
                loc.setZ(h.getZ());
            }
            model.moveTo(loc);
        }
        if (restoreSmoothAt == tick) model.setMoveSmoothing(2);
        speed = (float) Math.sqrt(sq(loc.getX() - prevLoc.getX()) + sq(loc.getZ() - prevLoc.getZ()));
        prevLoc = loc.clone();

        // Drehen
        yaw = MathUtil.approachAngle(yaw, targetYaw, turnSpeed);
        model.setYaw(yaw);

        // Schweben (Geist)
        if (Math.abs(hover - hoverTarget) > 0.001f) hover += (hoverTarget - hover) * 0.12f;
        float bob = hoverTarget > 0.01f ? (float) Math.sin(tick * 0.08) * 0.12f : 0f;
        model.setRootOffset(0, hover + bob, 0);

        // automatische Basis-Animation
        if (autoBase && !animator.hasAction()) {
            Clip want;
            boolean moving = speed > 0.035f;
            if (fight.phase() == Phase.SANGE && fight.ghostForm()) want = moving ? anims.glideGhost : anims.idleGhost;
            else if (drawn) want = moving ? anims.walkDrawn : anims.idleDrawn;
            else want = moving ? anims.walkSheathed : anims.idleSheathed;
            animator.playBase(want, 6, moving ? MathUtil.clamp(speed / 0.13f, 0.6f, 1.6f) : 1f);
        }

        animator.tick();
        animator.sample(pose);
        procedural();

        int interval = animator.recommendedInterval();
        if (interval > 1) {
            boolean active = speed > 0.02f || Math.abs(MathUtil.wrapDeg(targetYaw - yaw)) > 1f || animator.isActionPlaying();
            interval = active ? s.modelUpdateInterval : Math.max(3, s.modelUpdateInterval);
        }
        if (tick - lastModelUpdate >= interval) {
            model.update(pose, interval);
            lastModelUpdate = tick;
        }
        model.tick();
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Wind in Haar, Bart, Banner; Hüftplatten; Kopf schaut zum Ziel. */
    private void procedural() {
        float t = tick / 20f;
        float w = wind + Math.min(1f, speed * 6f);
        addRot("hair1", (float) Math.sin(t * 2.1) * 4f * w + 6f * w, 0, (float) Math.sin(t * 1.3) * 2f * w);
        addRot("hair2", (float) Math.sin(t * 2.1 - 0.7) * 6f * w + 8f * w, 0, (float) Math.sin(t * 1.3 - 0.5) * 3f * w);
        addRot("hair3", (float) Math.sin(t * 2.1 - 1.4) * 8f * w + 10f * w, 0, (float) Math.sin(t * 1.3 - 1f) * 4f * w);
        addRot("beard1", (float) Math.sin(t * 1.7) * 2f * w, 0, (float) Math.sin(t * 1.1) * 3f * w);
        addRot("beard2", (float) Math.sin(t * 1.7 - 0.6) * 4f * w, 0, (float) Math.sin(t * 1.1 - 0.4) * 5f * w);
        addRot("beard3", (float) Math.sin(t * 1.7 - 1.2) * 6f * w, 0, (float) Math.sin(t * 1.1 - 0.8) * 7f * w);
        addRot("flag", 0, (float) Math.sin(t * 2.6) * 14f * w + 10f * w, (float) Math.sin(t * 3.7) * 4f * w);
        float flap = Math.min(18f, speed * 90f);
        addRot("kusazuri_f", -flap * 0.8f + (float) Math.sin(t * 9) * flap * 0.15f, 0, 0);
        addRot("kusazuri_b", flap * 0.6f, 0, 0);
        addRot("kusazuri_l", 0, 0, flap * 0.4f);
        addRot("kusazuri_r", 0, 0, -flap * 0.4f);
        // Kopf zum Ziel drehen (begrenzt)
        float want = 0f;
        if (lookTarget != null && lookTarget.isValid() && lookTarget.getWorld() == loc.getWorld()) {
            want = MathUtil.clamp(MathUtil.wrapDeg(MathUtil.yawTo(loc, lookTarget.getLocation()) - yaw), -55f, 55f);
        }
        headYaw += (want - headYaw) * 0.25f;
        // Minecraft-Yaw positiv = nach rechts drehen; Modell-Y positiv = nach links -> Vorzeichen umkehren
        addRot("neck", 0, -headYaw * 0.35f, 0);
        addRot("head", 0, -headYaw * 0.65f, 0);
    }

    private void addRot(String bone, float x, float y, float z) {
        if (!def.hasBone(bone)) return;
        int i = def.bone(bone);
        Quaternionf extra = MathUtil.euler(x, y, z);
        pose.rot[i].mul(extra);
    }

    private void ensureHitbox() {
        if (hitbox != null && hitbox.isValid()) return;
        if (fight.isEnding()) return;
        fight.plugin().getLogger().warning("Boss-Hitbox verschwunden – wird neu erzeugt.");
        spawnHitbox(loc);
    }

    // ================================================================== Kampf-Rückmeldung

    /** Optisches/akustisches Treffer-Feedback (Schaden selbst rechnet der Fight). */
    public void hurtFeedback(Location hitPoint) {
        flash();
        if (Rand.chance(0.6)) fight.sfx().play(loc.clone().add(0, 1.4, 0), Sfx.Id.HURT);
        if (s.particles) {
            loc.getWorld().spawnParticle(Particle.CRIT, hitPoint, 6, 0.3, 0.4, 0.3, 0.15);
            if (Rand.chance(0.4)) loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, hitPoint, 3, 0.3, 0.3, 0.3, 0);
        }
    }

    public Vector3f forward() {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    public boolean isPathing() {
        return pathing && hitbox != null && hitbox.getPathfinder().hasPath();
    }

    /** Hitbox kurz unverwundbar machen (Vanilla-Unverwundbarkeit, z. B. während Teleports). */
    public void noDamageTicks(int t) {
        if (hitbox != null) hitbox.setNoDamageTicks(t);
    }
}
