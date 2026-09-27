package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.util.AttributeUtil;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
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
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hollow Sun: a star that fell into the Bloodstone forge and now wears its war-plate.
 * The body is an invisible wither skeleton, so only the armor shows, caged around a star core whose
 * light leaks out between the plates. It carries a display-built mace whose head is a caged star.
 *
 * <p><b>Main Sequence</b> (100-66%). A gold star-knight. The core is the heart of the silhouette;
 * the fight is clean and the forge only scorches where he strikes. Swipe, lance, slam, starcall, corona.
 *
 * <p><b>Ignition.</b> The core swells until the star is bigger than the knight: a pulsing red
 * envelope closes around the armor, the Bloodstone splits in glowing seams, the sky slides to dusk.
 *
 * <p><b>Red Giant</b> (66-33%). He floats inside his own photosphere and the platform becomes his
 * surface: flare beams scorch a sundial into the floor, prominences loop in from the forge's rim,
 * the floor under him boils.
 *
 * <p><b>Starfall</b>, the signature. He rises and swells into a red sun overhead, stutters, and
 * implodes: the envelope crushes to a point, every sound stops, the sky cuts to night. One bell.
 * A black star falls. The impact tears the floor open and the plates do not come back down.
 *
 * <p><b>Collapse</b> (33-0%). A black core in a blue-white photon shell, and around it an
 * accretion disk made of the forge itself ({@link HollowAccretion}). The disk throws plates back
 * down; wells pull; he folds through space. At 15% the <b>Last Light</b>: break the core or burn.
 *
 * <p><b>Death.</b> The armor bursts. The plates fall back into their holes and the floor heals.
 * The core climbs, fades, goes silent, and returns to the sky as a supernova.
 *
 * <p>Tell language, identical in every phase ({@link HollowProps}): a sun glyph on the floor =
 * something lands here, and its star touches the rim on the hit. Gold while he burns, flare-red
 * while he swells, blue-white with inward rays once he has collapsed. White = now. Crimson = never
 * stand here. Green ring at his feet = he is spent, hit him; it closes with the window.
 * A glowing ridge rolling over the floor = shockwave, jump it.
 */
final class HollowSunDirector {

    private static final String ID = "hollow_sun";

    private static final Color GOLD = Color.fromRGB(255, 196, 64);
    private static final Color SOLAR = Color.fromRGB(255, 236, 160);
    private static final Color HOT = Color.fromRGB(255, 255, 255);
    private static final Color EMBER = Color.fromRGB(255, 122, 32);
    private static final Color FLARE = Color.fromRGB(255, 64, 24);
    private static final Color CRIMSON = Color.fromRGB(205, 18, 40);
    private static final Color VIOLET = Color.fromRGB(150, 70, 255);
    private static final Color VOID = Color.fromRGB(70, 22, 120);
    private static final Color PHOTON = Color.fromRGB(236, 226, 255);
    private static final Color OPENING = Color.fromRGB(110, 255, 140);
    private static final Color SOOT = Color.fromRGB(70, 58, 56);
    private static final Color BLUESHIFT = HollowProps.BLUESHIFT;
    private static final Color PHOTON_BLUE = Color.fromRGB(200, 226, 255);

    private static final int INTRO_TICKS = 62;
    private static final int INTRO_IMPACT = 34;
    private static final int DEATH_TICKS = 186;
    private static final int NOVA_FADE = 140;
    private static final int NOVA_HUSH = 152;
    private static final int NOVA_BLAST = 158;
    private static final int NOVA_REMNANT = 172;
    private static final int NOVA_CALM = 182;
    private static final int[] NOVA_RING_SEGS = {24, 18, 18};
    private static final double[] NOVA_RING_SCALE = {1.0, 0.56, 0.56};
    private static final double[] NOVA_RING_LIFT = {0.0, 0.42, -0.42};
    private static final Material[] NOVA_SHELL_MATS = {
            Material.WHITE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS};
    private static final Color[] NOVA_SHELL_TINT = {HOT, SOLAR, EMBER, VIOLET};
    private static final double[] NOVA_SHELL_REACH = {0.4, 0.62, 0.88, 1.25};
    private static final int[] NOVA_SHELL_TICKS = {9, 12, 15, 19};
    private static final int IGNITION_BASE = 100;
    private static final int STARFALL_BASE = 240;
    private static final int STARFALL_COLLAPSE = 80;
    private static final int STARFALL_IMPACT = 170;
    private static final double CORONA_R = 5.0;
    private static final double SIGIL_R = 3.2;
    private static final double WELL_PULL_R = 9.0;
    private static final double WELL_CORE_R = 2.3;
    private static final int CROWN_RAYS = 8;
    private static final int LEAKS = 6;
    private static final int STARFALL_IMPLODE_SKY = 18000;
    private static final int DUSK_SKY = 12600;

    private enum Form { MAIN, GIANT, COLLAPSE }

    private enum Move { NONE, SWIPE, LANCE, SLAM, STARCALL, CORONA, FLARE, PROMINENCE, WELL, FOLD, NOVA, EJECTA }

    private enum Cinematic { NONE, IGNITION, STARFALL, HOLD }

    private final BossInstance instance;

    /* Every non-rig display. Cleared as one list so nothing can leak. */
    private final List<Display> fx = new ArrayList<>();
    private final List<Meteor> meteors = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();
    private final List<Lance> lances = new ArrayList<>();
    private final List<Shockwave> waves = new ArrayList<>();
    private final List<Afterglow> afterglows = new ArrayList<>();
    private final List<Shard> shards = new ArrayList<>();
    private final List<BlockDisplay> windupLances = new ArrayList<>();
    private final List<BlockDisplay> flareBeams = new ArrayList<>();
    /** One white sliver on each flare beam's leading side: which way it is coming, at a glance. */
    private final List<BlockDisplay> flareEdges = new ArrayList<>();
    private final List<Husk> husks = new ArrayList<>();
    private final Map<Long, Scorch> scorches = new HashMap<>();
    private final Map<String, Long> hitGate = new HashMap<>();
    private final Set<String> beats = new HashSet<>();
    private final Set<UUID> whooshHeard = new HashSet<>();
    private final EnumSet<Move> taught = EnumSet.noneOf(Move.class);
    private Well well;
    /** Floor telegraphs, shockwave ridges, seams: display props on fixed floor anchors. */
    private final HollowProps props;
    /** Collapse: the forge's own floor plates, orbiting the black star. Reseated on death. */
    private final HollowAccretion accretion;
    /** The sky answers to the star. Released on abort and at the end of death. */
    private final HollowSky sky = new HollowSky();
    /* Live telegraphs owned by the current move: expired when the move is cut short. */
    private final List<HollowProps.Prop> tells = new ArrayList<>();
    private HollowProps.Rod lanceSeek;
    private HollowProps.Glyph foldGlyph;
    private HollowProps.Ring openingRing;
    private HollowProps.Ring novaRing;
    /** Hitstop: after a heavy landing he holds the pose for a few ticks before anything else moves. */
    private int hold;
    private int ejectaCd;

    /* Arena, measured once from the spawn point. */
    private Location center;
    private double floorY;
    private double arenaRadius = 16.0;
    private double skyRise = 16.0;

    /* Rig. */
    private BlockDisplay coreA;
    private BlockDisplay coreB;
    private BlockDisplay shell;
    private BlockDisplay maceShaft;
    private BlockDisplay maceHead;
    private BlockDisplay maceStar;
    private final List<BlockDisplay> crown = new ArrayList<>();
    /** Light leaking out of the armor: pulses outward while he burns, streaks inward once collapsed. */
    private final List<BlockDisplay> leaks = new ArrayList<>();
    /** Red Giant photosphere: two crossed glass hulls around the whole knight, breathing slowly. */
    private final List<BlockDisplay> envelope = new ArrayList<>();
    private double envelopeGrow;
    private double envelopeGoal;
    private int envelopeDie;
    private Form visualForm = Form.MAIN;
    private Form silhouette;
    private float spin;
    private double coreBoost;
    private double coreKick;
    private double rigScale = 1.0;
    private int crownShown = CROWN_RAYS;
    private boolean maceShown = true;
    private Location coreOverride;
    private boolean maceHeld;
    private Location maceGrip;
    private Vector maceDir = new Vector(0, -1, 0);

    /* Body. */
    private float bodyYaw;
    private float bodyPitch;
    private double hover;
    private double hoverGoal;
    private double walked;
    private boolean folded;
    private double foldBase = -1;

    /* Encounter state. */
    private long clock;
    private boolean introDone;
    private int introTick = -1;
    private boolean openingMove = true;
    private Move move = Move.NONE;
    private int actionTick;
    private UUID moveTarget;
    private Vector aim = new Vector(0, 0, 1);
    private int breath;
    private int swipeCd;
    private int lanceCd;
    private int slamCd;
    private int starcallCd;
    private int coronaCd;
    private int flareCd;
    private int prominenceCd;
    private int wellCd;
    private int foldCd;
    private int novaCd;
    private int exposedTicks;
    private double exposedMul = 1.0;

    /* Per-move scratch. */
    private int windup;
    private double lanceLen;
    private double[] laneOffsets = {0.0};
    private double flareAngle;
    private double flarePrev;
    private int flareSpin = 1;
    private int prominenceCount;
    private Location wellSpot;
    private Location foldSigil;
    private Location foldFrom;
    private int foldChain;
    private int novaStage;
    private int novaTick;
    private double novaDamage;
    private double novaNeed;
    private BlockDisplay novaShell;
    private long lastNovaFeedback;

    /* Cinematics. */
    private Cinematic cinematic = Cinematic.NONE;
    private int beatFloor;
    /** Real ticks per authored cinematic tick (the YAML may stretch a transition). */
    private double transitionScale = 1.0;
    /** The black star's fall, drawn as one stretched rod behind the core instead of a particle trail. */
    private BlockDisplay starfallTail;
    private Location cinStart;
    private int deathTick = -1;
    private Location deathFocus;
    private BlockDisplay introStar;
    private Location introPrev;
    private final ItemStack[] deathArmor = new ItemStack[4];
    /* Supernova: purely visual. Nothing here touches a player. Every display is also in fx. */
    private final List<BlockDisplay> deathNovaShells = new ArrayList<>();
    private final List<BlockDisplay> deathNovaRings = new ArrayList<>();
    private final List<BlockDisplay> deathNovaRays = new ArrayList<>();
    private final List<Vector3f> deathNovaRayReach = new ArrayList<>();
    private final List<Ejecta> deathNovaEjecta = new ArrayList<>();
    private final BlockDisplay[] deathNovaBeams = new BlockDisplay[2];
    private BlockDisplay deathNovaSeed;
    private Quaternionf deathNovaTilt = new Quaternionf();
    private float deathNovaSpin;
    private Location deathNovaFocus;

    HollowSunDirector(BossInstance instance) {
        this.instance = instance;
        this.props = new HollowProps(instance);
        this.accretion = new HollowAccretion(instance);
    }

    // ------------------------------------------------------------------ BossInstance hooks

    boolean isMine() {
        return instance.getTemplate() != null && ID.equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTick >= 0;
    }

    /** The director drives the body at all times. Vanilla AI never walks, targets, or punches. */
    boolean holdsBody() {
        return isMine();
    }

    /** Arrival cinematic, and while folded into a point, there is nothing to hit. */
    boolean blocksDamage() {
        return isMine() && (introTick >= 0 || folded);
    }

    double scaleIncoming(double amount) {
        if (!isMine() || exposedTicks <= 0) {
            return amount;
        }
        return amount * exposedMul;
    }

    void onDamaged(double amount) {
        if (!isMine() || amount <= 0) {
            return;
        }
        coreKick = Math.min(0.2, coreKick + 0.07);
        if (move == Move.NOVA && novaStage == 1) {
            novaDamage += amount;
            novaFeedback();
        }
    }

    void onBind() {
        if (!isMine()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        clearCombatFx();
        clearRig();
        accretion.clear();
        if (center == null) {
            measureArena();
        }
        keepHidden(entity, true);
        entity.setGravity(false);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        restoreScale(entity);
        bodyYaw = entity.getLocation().getYaw();
        bodyPitch = 0.0f;
        move = Move.NONE;
        actionTick = 0;
        cinematic = Cinematic.NONE;
        deathTick = -1;
        folded = false;
        exposedTicks = 0;
        silhouette = null;
        if (!introDone) {
            beginIntro(entity);
            return;
        }
        Form form = form();
        hover = form == Form.GIANT ? 1.2 : 0.0;
        hoverGoal = hover;
        refreshSilhouette(entity, form);
        spawnRig(entity);
        resetCooldowns();
        // Body rebuilt mid-fight: the star's state comes back with it, not the moment it was born.
        if (form == Form.GIANT) {
            envelopeGoal = 1.0;
            sky.slide(entity.getWorld(), DUSK_SKY, 400);
        } else if (form == Form.COLLAPSE && center != null) {
            sky.cut(STARFALL_IMPLODE_SKY);
            accretion.start(entity.getWorld(), floorY, corePos(entity));
            accretion.tear(entity.getLocation(), 8, 7.0, 1.8);
        }
    }

    void abort() {
        clearCombatFx();
        clearRig();
        clearHusks();
        accretion.clear();
        revertAllScorch();
        sky.release();
        stopWhoosh();
        LivingEntity entity = instance.getEntity();
        if (isMine() && entity != null && entity.isValid()) {
            restoreScale(entity);
        }
        if (introStar != null && introStar.isValid()) {
            introStar.remove();
        }
        introStar = null;
        clearDeathNova();
        deathNovaFocus = null;
        deathTick = -1;
        move = Move.NONE;
        cinematic = Cinematic.NONE;
        folded = false;
    }

    boolean beginDeath() {
        if (!isMine() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        Location at = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : (center != null ? center.clone() : instance.getSpawnLocation());
        if (at.getWorld() == null) {
            return false;
        }
        clearCombatFx();
        stopWhoosh();
        // The floor heals while he shakes apart: every torn plate falls back into its own hole.
        accretion.release();
        introTick = -1;
        cinematic = Cinematic.NONE;
        exposedTicks = 0;
        hold = 0;
        coreBoost = 0.0;
        crownShown = CROWN_RAYS;
        maceShown = true;
        if (entity != null && entity.isValid()) {
            restoreScale(entity);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setCustomNameVisible(false);
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
            EntityEquipment eq = entity.getEquipment();
            if (eq != null) {
                deathArmor[0] = copy(eq.getHelmet());
                deathArmor[1] = copy(eq.getChestplate());
                deathArmor[2] = copy(eq.getLeggings());
                deathArmor[3] = copy(eq.getBoots());
            }
            if (coreA == null || !coreA.isValid()) {
                spawnRig(entity);
            }
        }
        deathFocus = at;
        deathTick = 0;
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.3f, 0.6f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);
        world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.5f);
        shout("&6&lThe Hollow Sun&7: &f…up. &eTake me back up.");
        return true;
    }

    /**
     * @return true when the death cinematic finished and the body should be removed
     */
    boolean tick() {
        if (!isMine()) {
            return false;
        }
        if (isDying()) {
            return tickDeath();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        clock++;
        if (center == null) {
            measureArena();
        }
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        tickScorches();
        sky.tick(center);
        if (introTick >= 0) {
            tickIntro(entity);
            tickHazards(entity);
            return false;
        }
        if (instance.isTransitioning()) {
            tickHazards(entity);
            return false;
        }
        Form form = form();
        refreshSilhouette(entity, form);
        keepHidden(entity, false);
        tickCooldowns();
        tickHazards(entity);
        if (hold > 0) {
            // Hitstop: the pose lands, the world keeps moving, he does not.
            hold--;
        } else if (move != Move.NONE) {
            tickMove(entity, form);
        } else if (exposedTicks > 0) {
            // Spent: he stands in his own green ring and takes it.
            if (form == Form.GIANT) {
                settleHover(entity);
            }
        } else {
            maybeStartMove(entity, form);
            if (move == Move.NONE) {
                locomote(entity, form);
            }
        }
        ambient(entity, form);
        syncRig(entity);
        return false;
    }

    void beginTransition(BossPhase next) {
        if (!isMine() || next == null) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        cancelMove(entity);
        fizzleHazards();
        exposedTicks = 0;
        if (openingRing != null) {
            openingRing.expire();
            openingRing = null;
        }
        hold = 0;
        beats.clear();
        beatFloor = 0;
        cinStart = entity != null && entity.isValid() ? entity.getLocation().clone() : center.clone();
        String id = next.getId();
        if ("collapse".equalsIgnoreCase(id)) {
            cinematic = Cinematic.STARFALL;
        } else if ("red_giant".equalsIgnoreCase(id)) {
            cinematic = Cinematic.IGNITION;
        } else {
            cinematic = Cinematic.HOLD;
        }
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.setGravity(false);
        World world = entity.getWorld();
        Location at = entity.getLocation();
        if (cinematic == Cinematic.IGNITION) {
            world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.6f);
            world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, 1.4f, 0.5f);
            shout("&c&lThe Hollow Sun&7: &fFeel that? &cI am getting bigger.");
        } else if (cinematic == Cinematic.STARFALL) {
            world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.6f);
            world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.6f);
            shout("&c&lThe Hollow Sun&7: &fNo — not like this — &4I am going out —");
        }
    }

    void tickTransition(int tick, int duration) {
        if (!isMine()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (cinematic == Cinematic.NONE) {
            // Body was rebuilt mid-transition: resume without replaying beats already past.
            beginTransition(instance.getPendingPhase());
            int base = cinematic == Cinematic.STARFALL ? STARFALL_BASE : IGNITION_BASE;
            beatFloor = scaled(tick, duration, base) + 1;
        }
        int base = cinematic == Cinematic.STARFALL ? STARFALL_BASE : IGNITION_BASE;
        transitionScale = duration / (double) Math.max(1, base);
        switch (cinematic) {
            case IGNITION -> tickIgnition(entity, scaled(tick, duration, IGNITION_BASE));
            case STARFALL -> tickStarfall(entity, scaled(tick, duration, STARFALL_BASE));
            default -> syncRig(entity);
        }
    }

    void finishTransition() {
        if (!isMine()) {
            return;
        }
        stopWhoosh();
        Cinematic done = cinematic;
        cinematic = Cinematic.NONE;
        coreOverride = null;
        coreBoost = 0.0;
        maceHeld = false;
        crownShown = CROWN_RAYS;
        maceShown = true;
        bodyPitch = 0.0f;
        LivingEntity entity = instance.getEntity();
        if (done == Cinematic.IGNITION) {
            hover = 1.2;
            hoverGoal = 1.2;
            envelopeGoal = 1.0;
            flareCd = 60;
            prominenceCd = 150;
            starcallCd = 210;
            lanceCd = 100;
            coronaCd = 160;
            swipeCd = 20;
            breath = 20;
        } else if (done == Cinematic.STARFALL) {
            hover = 0.0;
            hoverGoal = 0.0;
            wellCd = 60;
            foldCd = 130;
            slamCd = 70;
            starcallCd = 240;
            lanceCd = 150;
            coronaCd = 9999;
            novaCd = 0;
            ejectaCd = 110;
            breath = 30;
            envelopeGoal = 0.0;
            if (entity != null && entity.isValid() && center != null) {
                Location stand = entity.getLocation().clone();
                stand.setY(floorY);
                teleportBody(entity, stand);
            }
            if (center != null && entity != null && entity.isValid() && !accretion.isActive()) {
                // The impact beat was skipped (body rebuilt mid-cinematic): tear the disk up now.
                accretion.start(center.getWorld(), floorY, corePos(entity));
                accretion.tear(center, 10, clamp(arenaRadius * 0.42, 4.5, 8.5) + 1.5, 1.5);
            }
            sky.cut(STARFALL_IMPLODE_SKY);
        }
        if (entity != null && entity.isValid()) {
            entity.setRotation(bodyYaw, 0.0f);
        }
    }

    // ------------------------------------------------------------------ arena

    private void measureArena() {
        Location spawn = instance.getSpawnLocation();
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        Double floor = floorAt(world, spawn.getX(), spawn.getZ(), spawn.getY());
        floorY = floor == null ? spawn.getY() : floor;
        center = new Location(world, spawn.getX(), floorY, spawn.getZ());
        List<Double> edges = new ArrayList<>();
        int open = 0;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            double edge = -1;
            for (double r = 1.5; r <= 32.0; r += 0.75) {
                double x = center.getX() + Math.cos(a) * r;
                double z = center.getZ() + Math.sin(a) * r;
                Double y = floorAt(world, x, z, floorY);
                if (y == null || Math.abs(y - floorY) > 2.6) {
                    edge = r - 0.75;
                    break;
                }
            }
            if (edge < 0) {
                open++;
            } else {
                edges.add(edge);
            }
        }
        if (open >= 17 || edges.isEmpty()) {
            arenaRadius = 16.0;
        } else {
            edges.sort(Double::compare);
            arenaRadius = clamp(edges.get(edges.size() / 4), 6.0, 26.0);
        }
        int top = -1;
        for (int dy = 3; dy <= 40; dy++) {
            if (!world.getBlockAt(center.getBlockX(), (int) Math.floor(floorY) + dy, center.getBlockZ()).isPassable()) {
                top = dy;
                break;
            }
        }
        skyRise = top < 0 ? 20.0 : clamp(top - 5.0, 6.0, 20.0);
    }

    // ------------------------------------------------------------------ intro

    private void beginIntro(LivingEntity entity) {
        introTick = 0;
        coreBoost = 0.0;
        crownShown = 0;
        maceShown = false;
        visualForm = Form.MAIN;
        entity.setCustomNameVisible(false);
        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            eq.setHelmet(null);
            eq.setChestplate(null);
            eq.setLeggings(null);
            eq.setBoots(null);
            eq.setItemInMainHand(null);
            eq.setItemInOffHand(null);
        }
        if (center != null) {
            Location stand = center.clone();
            stand.setYaw(bodyYaw);
            teleportBody(entity, stand);
        }
    }

    /*
     * Arrival: a star streaks in from the sky, the whoosh cuts on impact, and the armor
     * assembles itself around the core piece by piece: boots, legs, chest, helm, crown, mace.
     */
    private void tickIntro(LivingEntity entity) {
        introTick++;
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location land = center.clone();
        Vector from = new Vector(0.55, 1.0, 0.35).normalize();
        Location sky = land.clone().add(from.clone().multiply(34.0));
        if (introTick == 1) {
            startWhoosh(land, 1.0f, 0.75f);
            world.playSound(land, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
            introStar = spawnBlock(sky, Material.SHROOMLIGHT.createBlockData(), GOLD, 1);
            introPrev = sky.clone();
            // The fight's first sun glyph is the landing itself: the language is taught before a blow is thrown.
            props.add(new HollowProps.Glyph(props, land, 4.5f, INTRO_IMPACT, HollowProps.Palette.SUN, false));
        }
        if (introTick <= INTRO_IMPACT) {
            double p = introTick / (double) INTRO_IMPACT;
            Location at = lerp(sky, land.clone().add(0, 1.2, 0), Math.pow(p, 2.2));
            if (introStar != null && introStar.isValid()) {
                introStar.teleport(flat(at));
                ease(introStar, centered(new Quaternionf().rotateY(introTick * 0.5f).rotateX(0.6f), 1.2f), 1);
            }
            streak(world, introPrev, at, SOLAR, true);
            if (introTick % 3 == 0) {
                world.spawnParticle(Particle.FLAME, at, 2, 0.15, 0.15, 0.15, 0.01, null, true);
            }
            introPrev = at.clone();
            if (introTick == INTRO_IMPACT - 8) {
                world.playSound(land, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
            }
        }
        if (introTick == INTRO_IMPACT) {
            stopWhoosh();
            if (introStar != null && introStar.isValid()) {
                introStar.remove();
            }
            introStar = null;
            Location mid = land.clone().add(0, 0.6, 0);
            world.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0, null, true);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0, null, true);
            floorBurst(world, land, 30, 1.4);
            world.playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.55f);
            world.playSound(land, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.6f);
            world.playSound(land, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.7f);
            waves.add(new Shockwave(land.clone(), 0.8, 0.8, arenaRadius + 1.0, GOLD, 0.0, 0));
            props.add(new HollowProps.Seams(props, land, 0f, HollowProps.TAU, 7, (float) Math.min(6.5, arenaRadius * 0.45),
                    0.2f, false, 110));
            crater(land, 2.6, Material.MAGMA_BLOCK, Material.BLACKSTONE, 90);
            afterglows.add(new Afterglow(Afterglow.SMOKE, land.clone(), null, 50));
            for (Player player : fighters()) {
                Vector push = player.getLocation().toVector().subtract(land.toVector()).setY(0);
                double d = push.length();
                if (d < 5.0 && d > 0.01) {
                    player.setVelocity(push.normalize().multiply(0.75).setY(0.38));
                }
            }
            spawnCore(entity);
            coreBoost = 0.6;
        }
        if (introTick > INTRO_IMPACT) {
            coreBoost *= 0.9;
            int[] at = {38, 42, 46, 50};
            for (int i = 0; i < at.length; i++) {
                if (introTick == at[i]) {
                    dressPiece(entity, 3 - i, Form.MAIN);
                    double y = entity.getHeight() * (0.1 + 0.28 * i);
                    Location piece = entity.getLocation().clone().add(0, y, 0);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, piece, 14, 0.35, 0.15, 0.35, 0.05);
                    world.spawnParticle(Particle.FLAME, piece, 6, 0.3, 0.1, 0.3, 0.01);
                    world.playSound(piece, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.6f + i * 0.08f);
                    world.playSound(piece, Sound.BLOCK_ANVIL_PLACE, 0.35f, 1.4f + i * 0.1f);
                }
            }
            if (introTick == 53) {
                spawnCrown(entity);
                crownShown = 0;
            }
            if (introTick >= 53 && introTick < 53 + CROWN_RAYS) {
                crownShown = introTick - 52;
                world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.9f + crownShown * 0.12f);
            }
            if (introTick == 58) {
                spawnMace(entity);
                maceShown = true;
                world.playSound(entity.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1.1f, 0.6f);
                world.spawnParticle(Particle.ELECTRIC_SPARK, corePos(entity), 20, 0.6, 0.6, 0.6, 0.1);
            }
            syncRig(entity);
        }
        if (introTick >= INTRO_TICKS) {
            introTick = -1;
            introDone = true;
            crownShown = CROWN_RAYS;
            maceShown = true;
            silhouette = Form.MAIN;
            entity.setCustomNameVisible(instance.getTemplate().getOptions().isCustomNameVisible());
            world.playSound(land, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.7f);
            world.playSound(land, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.6f);
            titleNear("&6&lTHE HOLLOW SUN", "&7A star fell here. It wore the forge home.");
            shout("&6&lThe Hollow Sun&7: &fI fell for a thousand years. &eYou will not keep me down here.");
            resetCooldowns();
            breath = 24;
        }
    }

    // ------------------------------------------------------------------ core loop

    private void resetCooldowns() {
        swipeCd = 20;
        lanceCd = 60;
        slamCd = 70;
        starcallCd = 150;
        coronaCd = 160;
        flareCd = 80;
        prominenceCd = 120;
        wellCd = 60;
        foldCd = 90;
        novaCd = 0;
        ejectaCd = 110;
        breath = 30;
    }

    private void tickCooldowns() {
        swipeCd = Math.max(0, swipeCd - 1);
        lanceCd = Math.max(0, lanceCd - 1);
        slamCd = Math.max(0, slamCd - 1);
        starcallCd = Math.max(0, starcallCd - 1);
        coronaCd = Math.max(0, coronaCd - 1);
        flareCd = Math.max(0, flareCd - 1);
        prominenceCd = Math.max(0, prominenceCd - 1);
        wellCd = Math.max(0, wellCd - 1);
        foldCd = Math.max(0, foldCd - 1);
        novaCd = Math.max(0, novaCd - 1);
        ejectaCd = Math.max(0, ejectaCd - 1);
        if (move == Move.NONE && breath > 0) {
            breath--;
        }
        if (exposedTicks > 0) {
            exposedTicks--;
        }
        coreKick *= 0.82;
        if (move == Move.NONE) {
            coreBoost *= 0.9;
        }
    }

    private int breathFor(Form form) {
        return switch (form) {
            case MAIN -> 34;
            case GIANT -> 28;
            case COLLAPSE -> 20;
        };
    }

    private void maybeStartMove(LivingEntity entity, Form form) {
        if (breath > 0) {
            return;
        }
        List<Player> fighters = fighters();
        if (fighters.isEmpty()) {
            return;
        }
        Location at = entity.getLocation();
        Player near = nearest(at, fighters);
        double nd = near == null ? 99.0 : horizontal(near.getLocation(), at);
        int crowd = 0;
        for (Player player : fighters) {
            if (horizontal(player.getLocation(), at) <= 4.6) {
                crowd++;
            }
        }
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (openingMove) {
            openingMove = false;
            beginLance(entity, fighters);
            return;
        }
        // One readable idea per beat: while meteors, prominences or a thrown plate are still coming
        // down, the floor belongs to them. He only fights hand to hand until they have landed.
        if (groundBusy()) {
            if (form == Form.COLLAPSE && novaCd <= 0 && instance.healthPercent() <= 15.0) {
                beginNova(entity);
            } else if (nd <= 3.6 && swipeCd <= 0) {
                beginSwipe(entity, near);
            }
            return;
        }
        switch (form) {
            case MAIN -> {
                if (crowd >= 2 && coronaCd <= 0) {
                    beginCorona(entity);
                } else if (nd <= 3.6 && swipeCd <= 0 && roll < 50) {
                    beginSwipe(entity, near);
                } else if (nd <= 6.2 && slamCd <= 0 && roll < 70) {
                    beginSlam(entity, near);
                } else if (nd <= 3.2 && coronaCd <= 0 && roll < 30) {
                    beginCorona(entity);
                } else if (starcallCd <= 0 && roll < 45) {
                    beginStarcall(entity, fighters);
                } else if (lanceCd <= 0 && (nd > 5.0 || roll < 40)) {
                    beginLance(entity, fighters);
                } else if (nd <= 3.6 && swipeCd <= 0) {
                    beginSwipe(entity, near);
                }
            }
            case GIANT -> {
                if (flareCd <= 0 && roll < 35) {
                    beginFlare(entity);
                } else if (crowd >= 2 && coronaCd <= 0) {
                    beginCorona(entity);
                } else if (prominenceCd <= 0 && roll < 50) {
                    beginProminence(entity, fighters);
                } else if (starcallCd <= 0 && roll < 45) {
                    beginStarcall(entity, fighters);
                } else if (nd <= 3.8 && swipeCd <= 0 && roll < 60) {
                    beginSwipe(entity, near);
                } else if (lanceCd <= 0) {
                    beginLance(entity, fighters);
                } else if (nd <= 3.8 && swipeCd <= 0) {
                    beginSwipe(entity, near);
                }
            }
            case COLLAPSE -> {
                if (novaCd <= 0 && instance.healthPercent() <= 15.0) {
                    beginNova(entity);
                } else if (well == null && wellCd <= 0 && roll < 40) {
                    beginWell(entity, fighters);
                } else if (well == null && ejectaCd <= 0 && accretion.orbiting() >= 2 && roll < 55) {
                    // Never a thrown plate on top of a pull: the well already owns the floor.
                    beginEjecta(entity, fighters);
                } else if (foldCd <= 0 && (nd > 6.0 || roll < 35)) {
                    beginFold(entity, fighters);
                } else if (nd <= 6.2 && slamCd <= 0 && roll < 60) {
                    beginSlam(entity, near);
                } else if (well == null && starcallCd <= 0 && roll < 40) {
                    beginStarcall(entity, fighters);
                } else if (lanceCd <= 0 && roll < 60) {
                    beginLance(entity, fighters);
                } else if (nd <= 3.6 && swipeCd <= 0) {
                    beginSwipe(entity, near);
                }
            }
        }
    }

    private void tickMove(LivingEntity entity, Form form) {
        if (form == Form.GIANT && move != Move.FLARE) {
            settleHover(entity);
        }
        switch (move) {
            case SWIPE -> tickSwipe(entity, form);
            case LANCE -> tickLance(entity, form);
            case SLAM -> tickSlam(entity, form);
            case STARCALL -> tickStarcall(entity, form);
            case CORONA -> tickCorona(entity, form);
            case FLARE -> tickFlare(entity);
            case PROMINENCE -> tickProminence(entity);
            case WELL -> tickWellCast(entity);
            case FOLD -> tickFold(entity, form);
            case NOVA -> tickNova(entity);
            case EJECTA -> tickEjecta(entity);
            default -> endMove();
        }
    }

    /** Ground hazards still in flight: meteors, prominences, a plate the disk has thrown. */
    private boolean groundBusy() {
        return !meteors.isEmpty() || !arcs.isEmpty() || accretion.hasThrown();
    }

    private void start(Move next, Player target) {
        move = next;
        actionTick = 0;
        moveTarget = target == null ? null : target.getUniqueId();
    }

    private void endMove() {
        move = Move.NONE;
        actionTick = 0;
        maceHeld = false;
        coreOverride = null;
        bodyPitch = 0.0f;
        for (BlockDisplay rod : windupLances) {
            discard(rod);
        }
        windupLances.clear();
        dropSeek();
        tells.clear();
        breath = breathFor(form());
    }

    /** Expire whatever floor telegraph the current move still owns (cut short by a transition or death). */
    private void expireTells() {
        for (HollowProps.Prop tell : tells) {
            if (tell instanceof HollowProps.Glyph glyph) {
                glyph.expire();
            } else if (tell instanceof HollowProps.Lane lane) {
                lane.expire();
            } else if (tell instanceof HollowProps.Sector sector) {
                sector.expire();
            }
        }
        tells.clear();
        if (foldGlyph != null) {
            foldGlyph.expire();
            foldGlyph = null;
        }
        if (novaRing != null) {
            novaRing.expire();
            novaRing = null;
        }
        dropSeek();
    }

    private void dropSeek() {
        if (lanceSeek != null) {
            lanceSeek.expire();
            lanceSeek = null;
        }
    }

    /** Transition or death interrupts: nothing half-drawn survives, and fold never leaves him tiny. */
    private void cancelMove(LivingEntity entity) {
        if (entity != null && entity.isValid()) {
            restoreScale(entity);
        }
        folded = false;
        rigScale = 1.0;
        clearFlare();
        discard(novaShell);
        novaShell = null;
        novaStage = 0;
        move = Move.NONE;
        actionTick = 0;
        maceHeld = false;
        coreOverride = null;
        bodyPitch = 0.0f;
        for (BlockDisplay rod : windupLances) {
            discard(rod);
        }
        windupLances.clear();
        expireTells();
    }

    private Player target() {
        if (moveTarget == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(moveTarget);
        return vulnerable(player) && center != null && player.getWorld().equals(center.getWorld()) ? player : null;
    }

    private void teach(Move which, String title, String sub) {
        if (taught.add(which)) {
            titleNear(title, sub);
        } else {
            actionBar(sub);
        }
    }

    private Form form() {
        BossPhase phase = instance.getCurrentPhase();
        String id = phase == null ? "" : phase.getId();
        if ("collapse".equalsIgnoreCase(id)) {
            return Form.COLLAPSE;
        }
        if ("red_giant".equalsIgnoreCase(id)) {
            return Form.GIANT;
        }
        return Form.MAIN;
    }

    private double power(double main, double giant, double collapse) {
        return switch (form()) {
            case MAIN -> main;
            case GIANT -> giant;
            case COLLAPSE -> collapse;
        };
    }

    private int cd(int main, int giant, int collapse) {
        return switch (form()) {
            case MAIN -> main;
            case GIANT -> giant;
            case COLLAPSE -> collapse;
        };
    }

    private Color tellColor(Form form) {
        return switch (form) {
            case MAIN -> GOLD;
            case GIANT -> FLARE;
            case COLLAPSE -> BLUESHIFT;
        };
    }

    /** Chat color code that matches the form's tell color. */
    private static String code(Form form) {
        return switch (form) {
            case MAIN -> "&6";
            case GIANT -> "&c";
            case COLLAPSE -> "&b";
        };
    }

    private HollowProps.Palette palette(Form form) {
        return switch (form) {
            case MAIN -> HollowProps.Palette.SUN;
            case GIANT -> HollowProps.Palette.GIANT;
            case COLLAPSE -> HollowProps.Palette.INFALL;
        };
    }

    /** Sun glyph for the current form: rays out while he burns, in once he has collapsed. */
    private HollowProps.Glyph glyph(Location at, double radius, int life, Form form) {
        return props.add(new HollowProps.Glyph(props, at, (float) radius, life, palette(form), form == Form.COLLAPSE));
    }

    private void expose(int ticks, double mul) {
        exposedTicks = Math.max(exposedTicks, ticks);
        exposedMul = mul;
        actionBar("&a✦ OPENING &8| &fhe is spent — hit him now");
        if (center != null && center.getWorld() != null) {
            LivingEntity entity = instance.getEntity();
            Location at = entity != null && entity.isValid() ? entity.getLocation() : center;
            center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f, 1.8f);
            center.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
            // The window is a green ring at his feet that closes as the opening does.
            if (openingRing != null) {
                openingRing.expire();
            }
            openingRing = props.add(new HollowProps.Ring(props, ground(at), 1.9f, 0.05f, 14, Material.LIME_CONCRETE,
                    OPENING, 0.14f, 0.05f, ticks).closeTo(0.25f, ticks));
        }
    }

    // ------------------------------------------------------------------ locomotion

    private void locomote(LivingEntity entity, Form form) {
        List<Player> fighters = fighters();
        Player target = nearest(entity.getLocation(), fighters);
        Location pos = entity.getLocation();
        if (target == null) {
            if (form == Form.GIANT) {
                settleHover(entity);
            }
            return;
        }
        Vector to = target.getLocation().toVector().subtract(pos.toVector()).setY(0);
        double dist = to.length();
        face(entity, to, form == Form.COLLAPSE ? 7.0f : 10.0f);
        if (form == Form.GIANT) {
            Location next = pos.clone();
            if (dist > 3.2) {
                next.add(to.clone().normalize().multiply(Math.min(0.1, dist - 3.2)));
            }
            clampToArena(next, 1.0);
            Double floor = floorAt(pos.getWorld(), next.getX(), next.getZ(), floorY + 1.0);
            double base = floor == null ? floorY : floor;
            hover = approach(hover, hoverGoal, 0.06);
            double wanted = base + hover + Math.sin(clock * 0.12) * 0.08;
            next.setY(approach(pos.getY(), wanted, 0.14));
            next.setYaw(bodyYaw);
            teleportBody(entity, next);
            return;
        }
        if (dist <= 2.3) {
            entity.setRotation(bodyYaw, bodyPitch);
            return;
        }
        double speed = form == Form.COLLAPSE ? 0.095 : 0.145;
        Vector step = to.clone().normalize().multiply(Math.min(speed, dist - 2.2));
        Location next = stepTo(entity, pos, step);
        if (next == null) {
            next = stepTo(entity, pos, new Vector(step.getX(), 0, 0));
        }
        if (next == null) {
            next = stepTo(entity, pos, new Vector(0, 0, step.getZ()));
        }
        if (next == null) {
            entity.setRotation(bodyYaw, bodyPitch);
            return;
        }
        next.setYaw(bodyYaw);
        teleportBody(entity, next);
        walked += step.length();
        double stride = form == Form.COLLAPSE ? 1.6 : 1.25;
        if (walked >= stride) {
            walked = 0;
            footfall(entity, form);
        }
    }

    private Location stepTo(LivingEntity entity, Location pos, Vector step) {
        Location next = pos.clone().add(step);
        clampToArena(next, 1.2);
        World world = pos.getWorld();
        Double floor = floorAt(world, next.getX(), next.getZ(), pos.getY());
        if (floor == null || floor - pos.getY() > 1.1 || pos.getY() - floor > 2.2) {
            return null;
        }
        int head = (int) Math.ceil(entity.getHeight());
        for (int dy = 2; dy < head; dy++) {
            if (!world.getBlockAt(next.getBlockX(), (int) Math.floor(floor) + dy, next.getBlockZ()).isPassable()) {
                return null;
            }
        }
        next.setY(approach(pos.getY(), floor, 0.4));
        return next;
    }

    private void settleHover(LivingEntity entity) {
        Location pos = entity.getLocation();
        Double floor = floorAt(pos.getWorld(), pos.getX(), pos.getZ(), floorY + 1.0);
        double base = floor == null ? floorY : floor;
        hover = approach(hover, hoverGoal, 0.08);
        double wanted = base + hover + Math.sin(clock * 0.12) * 0.08;
        if (Math.abs(pos.getY() - wanted) < 0.01 && Math.abs(pos.getYaw() - bodyYaw) < 0.5) {
            return;
        }
        Location next = pos.clone();
        next.setY(approach(pos.getY(), wanted, 0.16));
        next.setYaw(bodyYaw);
        teleportBody(entity, next);
    }

    private void glideTo(LivingEntity entity, Location goal, double speed, double lift) {
        Location pos = entity.getLocation();
        Vector to = goal.toVector().subtract(pos.toVector()).setY(0);
        Location next = pos.clone();
        double d = to.length();
        if (d > 0.02) {
            next.add(to.normalize().multiply(Math.min(speed, d)));
        }
        Double floor = floorAt(pos.getWorld(), next.getX(), next.getZ(), floorY + 1.0);
        double base = floor == null ? floorY : floor;
        hover = approach(hover, lift, 0.12);
        next.setY(approach(pos.getY(), base + hover, 0.2));
        next.setYaw(bodyYaw);
        next.setPitch(bodyPitch);
        teleportBody(entity, next);
    }

    private void face(LivingEntity entity, Vector dir, float maxDeg) {
        if (dir == null || dir.lengthSquared() < 1.0E-4) {
            return;
        }
        float wanted = yawOf(dir);
        float delta = wrapDeg(wanted - bodyYaw);
        delta = Math.max(-maxDeg, Math.min(maxDeg, delta));
        bodyYaw = wrapDeg(bodyYaw + delta);
        entity.setRotation(bodyYaw, bodyPitch);
    }

    private void footfall(LivingEntity entity, Form form) {
        Location feet = entity.getLocation();
        World world = feet.getWorld();
        if (form == Form.COLLAPSE) {
            world.playSound(feet, Sound.ENTITY_WARDEN_STEP, 0.9f, 0.55f);
            world.playSound(feet, Sound.BLOCK_NETHERITE_BLOCK_STEP, 0.8f, 0.5f);
            world.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.1, 0), 6, 0.35, 0.02, 0.35, 0, floorData(feet));
        } else {
            world.playSound(feet, Sound.BLOCK_NETHERITE_BLOCK_STEP, 0.75f, 0.65f);
            world.spawnParticle(Particle.SMALL_FLAME, feet.clone().add(0, 0.05, 0), 3, 0.25, 0.02, 0.25, 0.005);
        }
    }

    // ------------------------------------------------------------------ SWIPE

    private void beginSwipe(LivingEntity entity, Player target) {
        start(Move.SWIPE, target);
        aim = flatDir(entity.getLocation(), target.getLocation(), fwd());
        windup = form() == Form.COLLAPSE ? 9 : 11;
        maceHeld = true;
        entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 0.8f);
    }

    private void tickSwipe(LivingEntity entity, Form form) {
        actionTick++;
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        Player target = target();
        // The body turns first; the floor commits once he has picked his swing.
        if (target != null && actionTick < 3) {
            aim = steer(aim, flatDir(feet, target.getLocation(), aim), 0.35);
        }
        face(entity, aim, 14.0f);
        Vector f = aim.clone();
        Vector r = right(f);
        if (actionTick == 3) {
            tells.add(props.add(new HollowProps.Sector(props, ground(feet), f, 3.9f, 72f, windup - 2, palette(form))));
        }
        if (actionTick <= windup) {
            double p = actionTick / (double) windup;
            maceGrip = feet.clone().add(r.clone().multiply(1.0)).add(f.clone().multiply(-0.3 * p)).add(0, h * 0.5 + 0.2 * p, 0);
            maceDir = blend(new Vector(0, -1, 0), r.clone().multiply(0.75).add(f.clone().multiply(-0.55)).add(new Vector(0, 0.25, 0)), easeInOut(p));
            if (actionTick == windup) {
                feet.getWorld().playSound(feet, Sound.ITEM_MACE_SMASH_AIR, 1.0f, 0.8f);
            }
            return;
        }
        if (actionTick == windup + 1) {
            maceGrip = feet.clone().add(f.clone().multiply(1.2)).add(0, h * 0.45, 0);
            maceDir = r.clone().multiply(-0.8).add(f.clone().multiply(0.6)).normalize();
            props.add(new HollowProps.Slash(props, feet.clone().add(0, h * 0.42, 0), f, 3.3f, 80f, 7,
                    tellColor(form)));
            World world = feet.getWorld();
            world.playSound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, 0.55f);
            world.playSound(feet, Sound.ITEM_MACE_SMASH_AIR, 1.0f, 1.3f);
            world.playSound(feet, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.6f);
            double power = power(30, 34, 38);
            for (Player player : fighters()) {
                if (!inCone(feet, f, player.getLocation(), 3.9, 72.0)
                        || Math.abs(player.getLocation().getY() - feet.getY()) > 2.8) {
                    continue;
                }
                BossHits.hurt(player, entity, power);
                player.setVelocity(f.clone().multiply(0.65).add(r.clone().multiply(-0.3)).setY(0.32));
            }
            return;
        }
        if (actionTick < windup + 11) {
            maceDir = steer(maceDir, new Vector(0, -1, 0), 0.2);
            return;
        }
        endMove();
        swipeCd = form == Form.COLLAPSE ? 26 : 34;
    }

    // ------------------------------------------------------------------ SOLAR LANCE

    private void beginLance(LivingEntity entity, List<Player> fighters) {
        Player target = farthest(entity.getLocation(), fighters, 22.0);
        if (target == null) {
            target = nearest(entity.getLocation(), fighters);
        }
        if (target == null) {
            return;
        }
        Form form = form();
        start(Move.LANCE, target);
        windup = switch (form) {
            case MAIN -> 36;
            case GIANT -> 32;
            case COLLAPSE -> 28;
        };
        aim = flatDir(entity.getLocation(), target.getLocation(), fwd());
        laneOffsets = form == Form.COLLAPSE ? new double[]{0.0, -22.0, 22.0} : new double[]{0.0};
        lanceLen = form == Form.GIANT ? 20.0 : form == Form.COLLAPSE ? 17.0 : 18.0;
        maceHeld = false;
        for (BlockDisplay rod : windupLances) {
            discard(rod);
        }
        windupLances.clear();
        Location core = corePos(entity);
        for (int i = 0; i < laneOffsets.length; i++) {
            windupLances.add(spawnBlock(core, Material.END_ROD.createBlockData(), tellColor(form), 2));
        }
        dropSeek();
        lanceSeek = props.add(new HollowProps.Rod(props, ground(entity.getLocation()), Material.WHITE_CONCRETE, tellColor(form)));
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.8f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
        teach(Move.LANCE, form == Form.COLLAPSE ? "&bTRI-LANCE" : "&6SOLAR LANCE",
                "&7The thin light is him aiming. When it locks, step off the lane.");
    }

    /** Tick on which the lance stops searching and the lanes commit. */
    private int lanceLock() {
        return Math.max(8, (int) Math.round(windup * 0.5));
    }

    private void tickLance(LivingEntity entity, Form form) {
        actionTick++;
        Location feet = entity.getLocation();
        World world = feet.getWorld();
        Player target = target();
        int w = windup;
        int lock = lanceLock();
        if (actionTick <= w) {
            double p = actionTick / (double) w;
            if (target != null && actionTick < lock) {
                aim = steer(aim, flatDir(feet, target.getLocation(), aim), 0.16);
            }
            face(entity, aim, 10.0f);
            int remaining = w - actionTick;
            Location from = ground(feet);
            coreBoost = 0.25 * p;
            Color edge = tellColor(form);
            if (actionTick < lock && lanceSeek != null) {
                // Searching: one thin light sweeps with his aim.
                Vector a = aim.clone().multiply(lanceLen * (0.4 + 0.6 * actionTick / (double) lock));
                lanceSeek.set(new Vector3f(0f, 0.06f, 0f), new Vector3f((float) a.getX(), 0.06f, (float) a.getZ()), 0.08f, 2);
            }
            if (actionTick == lock) {
                // Locked: the searching light snaps off and the lanes are drawn in full.
                dropSeek();
                for (int i = 0; i < laneOffsets.length; i++) {
                    Vector dir = rotateY(aim, Math.toRadians(laneOffsets[i]));
                    // Tri-lance fires its side lances three ticks apart: each lane lives until its own thrust.
                    tells.add(props.add(new HollowProps.Lane(props, from, dir, (float) lanceLen, 1.1f,
                            w - lock + 1 + i * 3, palette(form))));
                }
                world.playSound(feet, Sound.ITEM_CROSSBOW_LOADING_END, 1.2f, 0.6f);
                world.playSound(feet, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.3f);
            }
            for (int i = 0; i < laneOffsets.length; i++) {
                Vector dir = rotateY(aim, Math.toRadians(laneOffsets[i]));
                BlockDisplay rod = i < windupLances.size() ? windupLances.get(i) : null;
                if (rod == null || !rod.isValid()) {
                    continue;
                }
                double side = (i == 0 ? 0 : (i == 1 ? -1 : 1)) * 0.9;
                Location hold = feet.clone().add(0, entity.getHeight() * 0.95 + 0.25 * p, 0)
                        .add(right(aim).multiply(side)).add(dir.clone().multiply(-0.4));
                rod.teleport(flat(hold));
                Vector3f axis = new Vector3f((float) dir.getX(), -0.06f, (float) dir.getZ()).normalize();
                ease(rod, rod(axis, -1.6f, (float) (1.4 + 1.8 * p), 1.7f), 2);
                rod.setGlowColorOverride(remaining <= 8 ? HOT : edge);
                if (actionTick % 3 == 0) {
                    Location tip = hold.clone().add(dir.clone().multiply(1.6 + 1.8 * p));
                    converge(world, tip, 1.2, Particle.END_ROD, 2);
                }
            }
            fuse(feet, remaining);
            if (actionTick % 6 == 0) {
                world.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.7f + (float) p);
            }
            if (actionTick % 4 == 1) {
                actionBar(remaining <= 8
                        ? "&f&l⚠ NOW &8| &fget off the lane!"
                        : actionTick < lock
                        ? code(form) + "☀ SOLAR LANCE &8| &fhe is aiming"
                        : code(form) + "☀ SOLAR LANCE &8| &fstep off the lane &8| " + meter(remaining, w - lock, code(form)));
            }
            if (actionTick == w) {
                world.playSound(feet, Sound.ITEM_TRIDENT_THROW, 1.3f, 0.6f);
                world.playSound(feet, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
            }
            return;
        }
        if (actionTick == w + 1) {
            Location from = ground(feet).add(0, 1.15, 0);
            double power = power(44, 50, 52);
            for (int i = 0; i < laneOffsets.length; i++) {
                Vector dir = rotateY(aim, Math.toRadians(laneOffsets[i]));
                BlockDisplay rod = i < windupLances.size() ? windupLances.get(i) : null;
                if (rod != null && rod.isValid()) {
                    rod.setTeleportDuration(1);
                }
                lances.add(new Lance(from.clone(), dir, lanceLen, 2.3, i * 3, rod, power, tellColor(form)));
            }
            windupLances.clear();
            coreBoost = -0.1;
            world.spawnParticle(Particle.FLASH, corePos(entity), 1, 0, 0, 0, 0);
            return;
        }
        if (actionTick >= w + 14) {
            endMove();
            lanceCd = cd(150, 130, 120);
        }
    }

    private void tickLances(LivingEntity entity) {
        Iterator<Lance> it = lances.iterator();
        while (it.hasNext()) {
            Lance lance = it.next();
            World world = lance.origin.getWorld();
            if (world == null) {
                discard(lance.rod);
                it.remove();
                continue;
            }
            if (lance.delay > 0) {
                lance.delay--;
                if (lance.delay == 0) {
                    world.playSound(lance.origin, Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);
                }
                poseLance(lance);
                continue;
            }
            double prev = lance.traveled;
            lance.traveled = Math.min(lance.length, lance.traveled + lance.speed);
            Location a = lance.origin.clone().add(lance.dir.clone().multiply(prev));
            Location b = lance.origin.clone().add(lance.dir.clone().multiply(lance.traveled));
            poseLance(lance);
            streak(world, a, b, lance.color, false);
            // The thrust burns its line into the forge floor as it passes.
            for (double d = Math.floor(prev) + 1.0; d <= lance.traveled; d += 1.0) {
                paintSurface(lance.origin.clone().add(lance.dir.clone().multiply(d)).subtract(0, 1.1, 0),
                        lance.color == BLUESHIFT ? Material.BLACK_CONCRETE : Material.MAGMA_BLOCK,
                        50 + (int) (d * 2), viewers(lance.origin));
            }
            for (Player player : fighters()) {
                if (lance.struck.contains(player.getUniqueId())) {
                    continue;
                }
                if (segmentDistance(player.getLocation().add(0, 0.9, 0), a, b) > 1.3) {
                    continue;
                }
                lance.struck.add(player.getUniqueId());
                BossHits.hurt(player, entity, lance.power);
                player.setVelocity(lance.dir.clone().multiply(1.0).setY(0.35));
            }
            if (lance.traveled >= lance.length) {
                world.spawnParticle(Particle.FLASH, b, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, b, 8, 0.3, 0.3, 0.3, 0.08);
                world.playSound(b, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
                world.playSound(b, Sound.BLOCK_BASALT_BREAK, 0.9f, 0.7f);
                props.add(new HollowProps.RingWall(props, b.clone().subtract(0, 1.1, 0), 0.3f, 2.4f, 0.4f, 0.02f, 9,
                        Material.WHITE_STAINED_GLASS, lance.color, 12));
                discard(lance.rod);
                it.remove();
            }
        }
    }

    private void poseLance(Lance lance) {
        if (lance.rod == null || !lance.rod.isValid()) {
            return;
        }
        Location tip = lance.origin.clone().add(lance.dir.clone().multiply(lance.traveled));
        lance.rod.teleport(flat(tip));
        Vector3f axis = new Vector3f((float) lance.dir.getX(), 0f, (float) lance.dir.getZ()).normalize();
        ease(lance.rod, rod(axis, -2.8f, 3.0f, 1.8f), 1);
    }

    // ------------------------------------------------------------------ GRAVITY MACE

    private void beginSlam(LivingEntity entity, Player target) {
        start(Move.SLAM, target);
        aim = flatDir(entity.getLocation(), target.getLocation(), fwd());
        windup = form() == Form.COLLAPSE ? 20 : 24;
        maceHeld = true;
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.55f);
        world.playSound(entity.getLocation(), Sound.BLOCK_NETHERITE_BLOCK_STEP, 0.8f, 0.5f);
        teach(Move.SLAM, "&6GRAVITY MACE", "&7Leave the lit cone before it lands.");
    }

    private void tickSlam(LivingEntity entity, Form form) {
        actionTick++;
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        int w = windup;
        Player target = target();
        // Wind-up first (he turns, the mace climbs), then the cone commits for the last sixteen ticks.
        int commit = Math.max(4, w - 16);
        if (actionTick <= w) {
            double p = actionTick / (double) w;
            if (target != null && actionTick < commit) {
                aim = steer(aim, flatDir(feet, target.getLocation(), aim), 0.2);
            }
            face(entity, aim, 12.0f);
            Vector f = aim.clone();
            Vector r = right(f);
            double lift = easeInOut(p);
            maceGrip = feet.clone().add(0, h * (0.55 + 0.5 * lift), 0)
                    .add(r.clone().multiply(0.5 - 0.3 * lift)).add(f.clone().multiply(-0.25 * lift));
            maceDir = blend(new Vector(0, -1, 0), f.clone().multiply(-0.5).add(new Vector(0, 0.87, 0)), lift);
            bodyPitch = (float) (-12.0 * lift);
            int remaining = w - actionTick;
            if (actionTick == commit) {
                tells.add(props.add(new HollowProps.Sector(props, ground(feet), f, 6.2f, 56f, w - commit + 1, palette(form))));
                feet.getWorld().playSound(feet, Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.5f);
            }
            fuse(feet, remaining);
            if (actionTick % 5 == 0) {
                feet.getWorld().playSound(feet, Sound.BLOCK_NETHERITE_BLOCK_STEP, 0.5f, 0.5f + (float) p * 0.5f);
            }
            if (actionTick % 4 == 1 && actionTick >= commit) {
                actionBar(code(form) + "⬇ GRAVITY MACE &8| &fleave the cone &8| " + meter(remaining, w - commit, code(form)));
            }
            if (remaining == 3) {
                feet.getWorld().playSound(feet, Sound.ITEM_MACE_SMASH_AIR, 1.2f, 0.6f);
            }
            coreBoost = 0.15 * p;
            return;
        }
        if (actionTick == w + 1) {
            Vector f = aim.clone();
            Location impact = ground(feet.clone().add(f.clone().multiply(3.0)));
            maceGrip = feet.clone().add(f.clone().multiply(1.6)).add(0, 1.2, 0);
            maceDir = f.clone().multiply(0.55).add(new Vector(0, -0.84, 0)).normalize();
            bodyPitch = 18.0f;
            slamImpact(entity, feet, impact, f, form);
            return;
        }
        if (actionTick < w + 10) {
            return;
        }
        if (actionTick < w + 20) {
            double t = (actionTick - (w + 10)) / 10.0;
            Location idleGrip = idleMaceGrip(entity);
            maceGrip = lerp(maceGrip, idleGrip, 0.25 + t * 0.3);
            maceDir = steer(maceDir, idleMaceDir(), 0.2);
            bodyPitch = (float) (18.0 * (1.0 - t));
            return;
        }
        endMove();
        slamCd = cd(110, 100, 90);
    }

    private void slamImpact(LivingEntity entity, Location feet, Location impact, Vector f, Form form) {
        World world = impact.getWorld();
        world.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.7f);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
        world.playSound(impact, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
        world.playSound(impact, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLASH, impact.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
        floorBurst(world, impact, 24, 1.0);
        // The forge cracks in front of him: seams race outward, glow, and cool.
        boolean cold = form == Form.COLLAPSE;
        props.add(new HollowProps.Seams(props, impact, HollowProps.yawOf((float) f.getX(), (float) f.getZ()), 1.7f, 5,
                4.6f, 0.2f, cold, 80));
        props.add(new HollowProps.RingWall(props, impact, 0.4f, 3.4f, 0.5f, 0.02f, 8,
                cold ? Material.LIGHT_BLUE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS, tellColor(form), 16));
        crater(impact, 1.4, cold ? Material.BLACK_CONCRETE : Material.MAGMA_BLOCK, Material.BLACKSTONE, 70);
        floorJolt(impact, 7.0, 0.26);
        hold = 3;
        double power = power(44, 48, 50);
        for (Player player : fighters()) {
            if (!inCone(feet, f, player.getLocation(), 6.4, 58.0)
                    || Math.abs(player.getLocation().getY() - feet.getY()) > 3.0) {
                continue;
            }
            BossHits.hurt(player, entity, power);
            player.setVelocity(f.clone().multiply(0.7).setY(0.62));
        }
        if (form == Form.COLLAPSE) {
            waves.add(new Shockwave(impact.clone(), 0.6, 0.45, 9.5, BLUESHIFT, 30.0, 0));
            teachWave();
        }
    }

    /** The floor jumps under everyone standing near a heavy landing. */
    private void floorJolt(Location at, double radius, double lift) {
        for (Player player : fighters()) {
            if (!player.isOnGround() || horizontal(player.getLocation(), at) > radius
                    || Math.abs(player.getLocation().getY() - at.getY()) > 2.0) {
                continue;
            }
            Vector v = player.getVelocity();
            player.setVelocity(new Vector(v.getX(), Math.max(v.getY(), lift), v.getZ()));
        }
    }

    // ------------------------------------------------------------------ STARCALL

    private void beginStarcall(LivingEntity entity, List<Player> fighters) {
        Form form = form();
        start(Move.STARCALL, null);
        maceHeld = true;
        int count = switch (form) {
            case MAIN -> 3;
            case GIANT -> 5;
            case COLLAPSE -> 6;
        };
        count += Math.min(3, Math.max(0, fighters.size() - 1));
        int spacing = form == Form.MAIN ? 9 : form == Form.GIANT ? 7 : 5;
        int fall = form == Form.MAIN ? 36 : form == Form.GIANT ? 32 : 28;
        for (int i = 0; i < count; i++) {
            meteors.add(new Meteor(8 + i * spacing, fall, form == Form.COLLAPSE, form == Form.GIANT && i % 2 == 1, i));
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 0.6f);
        world.playSound(entity.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 0.5f);
        world.playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 0.7f);
        teach(Move.STARCALL, form == Form.COLLAPSE ? "&bDARK STARCALL" : "&6STARCALL",
                "&7Leave each sun before its star touches the rim.");
    }

    private void tickStarcall(LivingEntity entity, Form form) {
        actionTick++;
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        double p = Math.min(1.0, actionTick / 10.0);
        maceGrip = feet.clone().add(0, h * (0.6 + 0.55 * easeInOut(p)), 0).add(right(fwd()).multiply(0.45));
        maceDir = blend(new Vector(0, -1, 0), new Vector(0, 1, 0), easeInOut(p));
        Location head = maceGrip.clone().add(maceDir.clone().multiply(1.75));
        if (actionTick % 3 == 0) {
            head.getWorld().spawnParticle(Particle.END_ROD, head, 0, 0, 1, 0, 0.6);
        }
        coreBoost = 0.15 + 0.1 * Math.sin(actionTick * 0.5);
        Player near = nearest(feet, fighters());
        if (near != null) {
            face(entity, near.getLocation().toVector().subtract(feet.toVector()).setY(0), 6.0f);
        }
        if (actionTick >= 26) {
            endMove();
            starcallCd = cd(260, 220, 160);
        }
    }

    private void tickMeteors(LivingEntity entity) {
        Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            m.age++;
            if (!m.locked) {
                if (m.age < m.delay) {
                    continue;
                }
                if (!lockMeteor(m)) {
                    it.remove();
                    continue;
                }
                continue;
            }
            World world = m.ground.getWorld();
            double p = m.age / (double) m.fall;
            int remaining = m.fall - m.age;
            if (remaining == 8 || remaining == 4 || remaining == 1) {
                world.playSound(m.ground, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, remaining <= 1 ? 1.9f : 1.4f);
            }
            if (p >= 0.3) {
                if (m.rock == null) {
                    m.rock = spawnBlock(m.sky, (m.dark ? Material.BLACK_CONCRETE : Material.MAGMA_BLOCK).createBlockData(),
                            m.dark ? PHOTON_BLUE : EMBER, 1);
                    m.crust = spawnBlock(m.sky, (m.dark ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLACKSTONE).createBlockData(), null, 1);
                    m.tail = spawnBlock(m.sky, (m.dark ? Material.WHITE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS).createBlockData(),
                            m.dark ? BLUESHIFT : GOLD, 1);
                    m.prev = m.sky.clone();
                }
                double q = (p - 0.3) / 0.7;
                Location at = lerp(m.sky, m.ground.clone().add(0, 0.6, 0), q * q);
                float turn = m.age * 0.35f;
                place(m.rock, at, centered(new Quaternionf().rotateY(turn).rotateX(turn * 0.7f), 1.3f), 1);
                place(m.crust, at, centered(new Quaternionf().rotateZ(turn * 0.8f).rotateY(0.6f), m.dark ? 1.7f : 1.0f), 1);
                // A comet tail, not a particle trail: one glowing rod stretched back along the fall.
                Vector back = m.sky.toVector().subtract(m.ground.toVector()).normalize();
                float stretch = (float) (1.4 + 3.2 * q);
                place(m.tail, at, rod(new Vector3f((float) back.getX(), (float) back.getY(), (float) back.getZ()),
                        0.3f, stretch, (float) (0.55 - 0.2 * q)), 1);
                if (m.age % 3 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, at, 1, 0.2, 0.2, 0.2, 0.01, null, true);
                }
                m.prev = at;
            }
            if (m.age >= m.fall) {
                meteorImpact(entity, m);
                discard(m.rock);
                discard(m.crust);
                discard(m.tail);
                it.remove();
            }
        }
    }

    private boolean lockMeteor(Meteor m) {
        List<Player> fighters = fighters();
        Location spot;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (fighters.isEmpty()) {
            if (center == null) {
                return false;
            }
            double a = random.nextDouble(Math.PI * 2);
            double r = random.nextDouble(arenaRadius * 0.8);
            spot = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
        } else {
            spot = fighters.get(m.index % fighters.size()).getLocation().clone();
            if (m.scatter) {
                double a = random.nextDouble(Math.PI * 2);
                double r = 2.0 + random.nextDouble(1.5);
                spot.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
        }
        clampToArena(spot, 1.0);
        m.ground = ground(spot);
        Vector out = m.ground.toVector().subtract(center.toVector()).setY(0);
        if (out.lengthSquared() < 1.0) {
            double a = random.nextDouble(Math.PI * 2);
            out = new Vector(Math.cos(a), 0, Math.sin(a));
        }
        out.normalize();
        m.sky = m.ground.clone().add(out.multiply(9.0)).add(0, 24.0, 0);
        m.locked = true;
        m.age = 0;
        m.glyph = glyph(m.ground, m.radius, m.fall, m.dark ? Form.COLLAPSE : form());
        m.ground.getWorld().playSound(m.ground, Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, m.dark ? 0.5f : 1.3f);
        return true;
    }

    private void meteorImpact(LivingEntity entity, Meteor m) {
        World world = m.ground.getWorld();
        Location at = m.ground.clone().add(0, 0.3, 0);
        world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        floorBurst(world, m.ground, 16, 0.8);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.8f);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.6f);
        world.playSound(at, Sound.BLOCK_BASALT_BREAK, 1.0f, 0.6f);
        if (m.dark) {
            world.spawnParticle(Particle.SONIC_BOOM, at.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
            afterglows.add(new Afterglow(Afterglow.VOID_PULL, m.ground.clone(), null, 18));
        }
        props.add(new HollowProps.RingWall(props, m.ground, 0.4f, (float) m.radius + 0.6f, 0.45f, 0.02f, 7,
                m.dark ? Material.LIGHT_BLUE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS, m.dark ? BLUESHIFT : EMBER, 12));
        crater(m.ground, 1.3, m.dark ? Material.BLACK_CONCRETE : Material.MAGMA_BLOCK, Material.BLACKSTONE, 70);
        afterglows.add(new Afterglow(Afterglow.SMOKE, m.ground.clone(), null, 28));
        double power = m.dark ? 46 : power(40, 44, 46);
        double r2 = (m.radius + 0.2) * (m.radius + 0.2);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontalSq(pl, m.ground) > r2 || Math.abs(pl.getY() - m.ground.getY()) > 2.5) {
                continue;
            }
            BossHits.hurt(player, entity, power);
            Vector out = pl.toVector().subtract(m.ground.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = new Vector(0.01, 0, 0);
            }
            player.setVelocity(out.normalize().multiply(0.7).setY(0.5));
        }
    }

    // ------------------------------------------------------------------ CORONA

    private void beginCorona(LivingEntity entity) {
        start(Move.CORONA, null);
        Form form = form();
        windup = form == Form.GIANT ? 22 : 26;
        maceHeld = false;
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.5f);
        // He is the sun at the center of his own glyph.
        tells.add(glyph(ground(entity.getLocation()), CORONA_R, windup + 1, form));
        teach(Move.CORONA, "&eCORONA", "&7Back out of the sun around him. Then punish.");
    }

    private void tickCorona(LivingEntity entity, Form form) {
        actionTick++;
        Location feet = entity.getLocation();
        World world = feet.getWorld();
        int w = windup;
        if (actionTick <= w) {
            double p = actionTick / (double) w;
            int remaining = w - actionTick;
            // The core draws itself in, then swells: the inhale before the flare.
            coreBoost = p < 0.35 ? -0.12 * (p / 0.35) : 0.7 * ((p - 0.35) / 0.65);
            if (actionTick % 5 == 0) {
                world.playSound(feet, Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.6f + (float) p);
            }
            fuse(feet, remaining);
            if (actionTick % 4 == 1) {
                actionBar("&e✹ CORONA &8| &fback out of the sun around him &8| " + meter(remaining, w, "&e"));
            }
            return;
        }
        if (actionTick == w + 1) {
            Location core = corePos(entity);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
            props.add(new HollowProps.Rays(props, core, 14, (float) CORONA_R + 1.2f, 0.45f, Material.SHROOMLIGHT, tellColor(form)));
            world.playSound(core, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.5f);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            world.playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.1f, 0.8f);
            waves.add(new Shockwave(ground(feet), 0.8, 0.9, CORONA_R + 1.2, tellColor(form), 0.0, 0));
            hold = 2;
            double power = power(36, 40, 40);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontal(pl, feet) > CORONA_R + 0.3 || Math.abs(pl.getY() - feet.getY()) > 3.5) {
                    continue;
                }
                BossHits.hurt(player, entity, power);
                Vector out = pl.toVector().subtract(feet.toVector()).setY(0);
                if (out.lengthSquared() < 0.01) {
                    out = fwd();
                }
                player.setVelocity(out.normalize().multiply(1.25).setY(0.45));
                if (form == Form.GIANT) {
                    player.setFireTicks(Math.max(player.getFireTicks(), 40));
                }
            }
            coreBoost = -0.3;
            expose(36, 1.25);
            return;
        }
        if (actionTick >= w + 12) {
            endMove();
            coronaCd = cd(220, 200, 200);
        }
    }

    // ------------------------------------------------------------------ SOLAR FLARE (Red Giant)

    private void beginFlare(LivingEntity entity) {
        start(Move.FLARE, null);
        flareAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        flarePrev = flareAngle;
        flareSpin = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        hoverGoal = 3.4;
        maceHeld = false;
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
        world.playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1f, 0.6f);
        teach(Move.FLARE, "&cSOLAR FLARE", "&7The white edge leads. Run ahead of it — or jump the beam.");
    }

    /*
     * Two beams sweep the platform like clock hands. Thin = tell, thick and bright = live.
     * Chevrons show travel. Mid-sweep the beams stop, strobe white, and reverse.
     * The beam is 0.9 tall: a clean jump clears it.
     */
    private void tickFlare(LivingEntity entity) {
        actionTick++;
        int t = actionTick;
        World world = entity.getWorld();
        double reach = arenaRadius + 0.6;
        glideTo(entity, center, t <= 22 ? 0.35 : 0.5, t < 152 ? 3.4 : 1.2);
        coreBoost = Math.min(0.45, coreBoost + 0.02);
        Vector beamDir = new Vector(Math.cos(flareAngle), 0, Math.sin(flareAngle));
        face(entity, beamDir, 20.0f);
        Location base = center.clone().add(0, 0.05, 0);
        if (t == 24) {
            for (int k = 0; k < 2; k++) {
                flareBeams.add(spawnBlock(base, Material.ORANGE_STAINED_GLASS.createBlockData(), FLARE, 2));
                flareEdges.add(spawnBlock(base, Material.WHITE_CONCRETE.createBlockData(), HOT, 2));
            }
        }
        if (t >= 24 && t < 50) {
            double grow = Math.min(1.0, (t - 24) / 18.0);
            for (int k = 0; k < flareBeams.size(); k++) {
                double a = flareAngle + k * Math.PI;
                poseBeam(flareBeams.get(k), base, a, 0.8, (reach - 0.8) * grow, 0.2f, 0.16f);
                poseEdge(k, base, a, (reach - 0.8) * grow, 0.26f, 0.16f);
            }
            if (t % 4 == 0) {
                actionBar("&c☀ SOLAR FLARE &8| &frun ahead of the white edge, or jump the beam &8| " + meter(50 - t, 26, "&c"));
            }
            fuse(center, 50 - t);
            return;
        }
        if (t == 50) {
            for (BlockDisplay beam : flareBeams) {
                if (beam.isValid()) {
                    beam.setBlock(Material.SHROOMLIGHT.createBlockData());
                    beam.setGlowing(true);
                }
            }
            world.spawnParticle(Particle.FLASH, corePos(entity), 1, 0, 0, 0, 0);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.3f, 0.5f);
            world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
            world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
        }
        if (t >= 50 && t < 152) {
            int u = t - 50;
            double speed;
            if (u < 30) {
                speed = 1.2 + 1.8 * (u / 30.0);
            } else if (u < 42) {
                speed = 3.0;
            } else if (u < 52) {
                speed = 3.0 * (1.0 - (u - 42) / 10.0);
            } else if (u < 62) {
                speed = 0.0;
            } else if (u < 82) {
                speed = 3.0 * ((u - 62) / 20.0);
            } else if (u < 92) {
                speed = 3.0;
            } else {
                speed = 3.0 * Math.max(0.0, 1.0 - (u - 92) / 10.0);
            }
            boolean pausing = u >= 52 && u < 62;
            if (u == 52) {
                flareSpin = -flareSpin;
                world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.4f);
                world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 1.1f, 0.8f);
                actionBar("&f&l⟲ REVERSING &8| &fthe beams turn back");
            }
            flarePrev = flareAngle;
            flareAngle += flareSpin * Math.toRadians(speed);
            for (int k = 0; k < flareBeams.size(); k++) {
                BlockDisplay beam = flareBeams.get(k);
                double a = flareAngle + k * Math.PI;
                poseBeam(beam, base, a, 0.8, reach - 0.8, 0.9f, 0.55f);
                poseEdge(k, base, a, reach - 0.8, 1.0f, 0.55f);
                if (beam.isValid()) {
                    beam.setGlowColorOverride(pausing && u % 2 == 0 ? HOT : FLARE);
                }
            }
            // The beams burn a short wake into the floor: the dial shows where the light has been.
            if (t % 2 == 0 && speed > 0.1) {
                List<Player> viewers = viewers(center);
                for (int k = 0; k < 2; k++) {
                    double a = flarePrev + k * Math.PI - flareSpin * Math.toRadians(4.0);
                    for (double r = 1.6; r < reach; r += 1.4) {
                        paintSurface(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), Material.MAGMA_BLOCK, 18, viewers);
                    }
                }
            }
            if (t % 10 == 0) {
                world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.7f);
            }
            flareHits(entity, reach);
            return;
        }
        if (t >= 152 && t < 170) {
            double shrink = 1.0 - (t - 152) / 18.0;
            for (int k = 0; k < flareBeams.size(); k++) {
                double a = flareAngle + k * Math.PI;
                double len = Math.max(0.05, (reach - 0.8) * shrink);
                poseBeam(flareBeams.get(k), base, a, 0.8, len, 0.9f, 0.55f);
                poseEdge(k, base, a, len, 1.0f, 0.55f);
            }
            hoverGoal = 1.2;
            return;
        }
        clearFlare();
        coreBoost = -0.25;
        world.playSound(entity.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.1f, 0.6f);
        expose(50, 1.25);
        endMove();
        flareCd = 380;
        hoverGoal = 1.2;
    }

    private void flareHits(LivingEntity entity, double reach) {
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (pl.getY() - floorY > 0.95) {
                continue;
            }
            double dx = pl.getX() - center.getX();
            double dz = pl.getZ() - center.getZ();
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < 0.9 || r > reach + 0.6) {
                continue;
            }
            double pa = Math.atan2(dz, dx);
            double halfW = 0.6 / r;
            for (int k = 0; k < 2; k++) {
                if (!angleWithin(pa, flarePrev + k * Math.PI, flareAngle + k * Math.PI, halfW)) {
                    continue;
                }
                if (!gate("flare", player, 16)) {
                    break;
                }
                BossHits.hurt(player, entity, 38);
                Vector tangent = new Vector(-Math.sin(pa), 0, Math.cos(pa)).multiply(flareSpin * 0.8);
                player.setVelocity(tangent.setY(0.45));
                player.setFireTicks(Math.max(player.getFireTicks(), 40));
                break;
            }
        }
    }

    /** The white sliver on the side each beam is moving toward. Flips the instant the sweep reverses. */
    private void poseEdge(int k, Location base, double angle, double len, float height, float width) {
        BlockDisplay edge = k < flareEdges.size() ? flareEdges.get(k) : null;
        if (edge == null || !edge.isValid()) {
            return;
        }
        edge.teleport(flat(base));
        Quaternionf rot = new Quaternionf().rotationY((float) -angle);
        float z = flareSpin > 0 ? width / 2f : -width / 2f - 0.1f;
        Vector3f offset = rot.transform(new Vector3f(0.8f, -0.02f, z));
        ease(edge, new Transformation(offset, rot, new Vector3f((float) Math.max(0.01, len), height + 0.06f, 0.1f),
                new Quaternionf()), 2);
    }

    private void clearFlare() {
        for (BlockDisplay beam : flareBeams) {
            discard(beam);
        }
        flareBeams.clear();
        for (BlockDisplay edge : flareEdges) {
            discard(edge);
        }
        flareEdges.clear();
    }

    private void poseBeam(BlockDisplay beam, Location base, double angle, double r0, double len, float height, float width) {
        if (beam == null || !beam.isValid()) {
            return;
        }
        beam.teleport(flat(base));
        Quaternionf rot = new Quaternionf().rotationY((float) -angle);
        Vector3f offset = rot.transform(new Vector3f((float) r0, 0f, -width / 2f));
        ease(beam, new Transformation(offset, rot, new Vector3f((float) len, height, width), new Quaternionf()), 2);
    }

    // ------------------------------------------------------------------ PROMINENCE (Red Giant)

    private void beginProminence(LivingEntity entity, List<Player> fighters) {
        start(Move.PROMINENCE, null);
        hoverGoal = 2.2;
        prominenceCount = 4 + Math.min(2, Math.max(0, fighters.size() - 1));
        for (int i = 0; i < prominenceCount; i++) {
            arcs.add(new Arc(i * 7, i));
        }
        World world = entity.getWorld();
        world.playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.5f);
        teach(Move.PROMINENCE, "&cPROMINENCE", "&7A loop of plasma reaches for you. Leave the sun where it lands.");
    }

    private void tickProminence(LivingEntity entity) {
        actionTick++;
        Player near = nearest(entity.getLocation(), fighters());
        if (near != null) {
            face(entity, near.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0), 6.0f);
        }
        coreBoost = 0.2 + 0.12 * Math.sin(actionTick * 0.45);
        if (actionTick >= prominenceCount * 7 + 36) {
            endMove();
            prominenceCd = 260;
            hoverGoal = 1.2;
        }
    }

    private void tickArcs(LivingEntity entity) {
        Iterator<Arc> it = arcs.iterator();
        while (it.hasNext()) {
            Arc arc = it.next();
            arc.age++;
            if (!arc.locked) {
                if (arc.age < arc.delay) {
                    continue;
                }
                if (!lockArc(arc)) {
                    it.remove();
                }
                continue;
            }
            World world = arc.to.getWorld();
            int total = arc.tell + arc.flight;
            if (arc.age <= arc.tell) {
                if (arc.age % 4 == 0) {
                    world.spawnParticle(Particle.LAVA, arc.from, 1, 0.3, 0.1, 0.3, 0);
                }
                if (arc.age % 5 == 0) {
                    world.playSound(arc.from, Sound.BLOCK_LAVA_POP, 1.2f, 0.7f);
                }
                if (arc.age == arc.tell) {
                    arc.blob = spawnBlock(arc.from, Material.MAGMA_BLOCK.createBlockData(), EMBER, 1);
                    world.playSound(arc.from, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
                    world.playSound(arc.from, Sound.ITEM_BUCKET_EMPTY_LAVA, 1.2f, 0.7f);
                    world.spawnParticle(Particle.LAVA, arc.from, 10, 0.5, 0.2, 0.5, 0);
                    coreKick = 0.2;
                }
                continue;
            }
            // The plasma rides down the loop it drew.
            double q = (arc.age - arc.tell) / (double) arc.flight;
            Location at = lerp(arc.from, arc.to, q).add(0, Math.sin(q * Math.PI) * arc.apex, 0);
            if (arc.blob != null) {
                place(arc.blob, at, centered(new Quaternionf().rotateY(arc.age * 0.4f).rotateX(arc.age * 0.3f), 1.0f), 1);
            }
            if (arc.age % 3 == 0) {
                world.spawnParticle(Particle.FALLING_LAVA, at, 1, 0.1, 0.1, 0.1, 0, null, true);
            }
            if (arc.age >= total) {
                arcImpact(entity, arc);
                discard(arc.blob);
                if (arc.loop != null) {
                    arc.loop.fade(8);
                }
                it.remove();
            }
        }
    }

    private boolean lockArc(Arc arc) {
        if (center == null) {
            return false;
        }
        List<Player> fighters = fighters();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spot;
        if (fighters.isEmpty()) {
            double a = random.nextDouble(Math.PI * 2);
            spot = center.clone().add(Math.cos(a) * arenaRadius * 0.5, 0, Math.sin(a) * arenaRadius * 0.5);
        } else {
            spot = fighters.get(arc.index % fighters.size()).getLocation().clone()
                    .add(random.nextDouble(-1.0, 1.0), 0, random.nextDouble(-1.0, 1.0));
        }
        clampToArena(spot, 1.2);
        arc.to = ground(spot);
        double landA = Math.atan2(arc.to.getZ() - center.getZ(), arc.to.getX() - center.getX());
        double edgeA = landA + Math.PI + random.nextDouble(-0.9, 0.9);
        arc.from = center.clone().add(Math.cos(edgeA) * (arenaRadius + 0.8), -0.6, Math.sin(edgeA) * (arenaRadius + 0.8));
        arc.apex = 6.5 + arc.from.distance(arc.to) * 0.12;
        arc.locked = true;
        arc.age = 0;
        arc.glyph = glyph(arc.to, arc.radius, arc.tell + arc.flight, Form.GIANT);
        // A magnetic loop reaches in from the forge's rim; when it touches down at you, the plasma comes.
        arc.loop = props.add(new HollowProps.Loop(props, arc.from, arc.to, arc.apex, 9, arc.tell, 0.42f,
                Material.MAGMA_BLOCK, EMBER));
        arc.from.getWorld().playSound(arc.from, Sound.BLOCK_LAVA_AMBIENT, 1.4f, 0.8f);
        return true;
    }

    private void arcImpact(LivingEntity entity, Arc arc) {
        World world = arc.to.getWorld();
        Location at = arc.to.clone().add(0, 0.3, 0);
        world.spawnParticle(Particle.LAVA, at, 6, 0.6, 0.2, 0.6, 0);
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        world.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.7f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        props.add(new HollowProps.RingWall(props, arc.to, 0.4f, (float) arc.radius + 0.8f, 0.5f, 0.02f, 8,
                Material.ORANGE_STAINED_GLASS, FLARE, 12));
        crater(arc.to, 1.3, Material.MAGMA_BLOCK, Material.MAGMA_BLOCK, 70);
        double r2 = (arc.radius + 0.2) * (arc.radius + 0.2);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontalSq(pl, arc.to) > r2 || Math.abs(pl.getY() - arc.to.getY()) > 2.5) {
                continue;
            }
            BossHits.hurt(player, entity, 36);
            player.setFireTicks(Math.max(player.getFireTicks(), 60));
            Vector out = pl.toVector().subtract(arc.to.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = new Vector(0.01, 0, 0);
            }
            player.setVelocity(out.normalize().multiply(0.6).setY(0.45));
        }
    }

    // ------------------------------------------------------------------ EVENT HORIZON (Collapse)

    private void beginWell(LivingEntity entity, List<Player> fighters) {
        Player target = fighters.get(ThreadLocalRandom.current().nextInt(fighters.size()));
        start(Move.WELL, target);
        Location spot = target.getLocation().clone();
        Vector in = center.toVector().subtract(spot.toVector()).setY(0);
        if (in.lengthSquared() > 1.0) {
            spot.add(in.normalize().multiply(1.5));
        }
        clampToArena(spot, 3.0);
        wellSpot = ground(spot);
        maceHeld = true;
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.5f);
        world.playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.1f, 0.6f);
        teach(Move.WELL, "&bEVENT HORIZON", "&7Walk out of the blue ring. Never into the red.");
    }

    private void tickWellCast(LivingEntity entity) {
        actionTick++;
        Location feet = entity.getLocation();
        Vector to = wellSpot.toVector().subtract(feet.toVector()).setY(0);
        face(entity, to, 12.0f);
        double h = entity.getHeight();
        maceGrip = feet.clone().add(0, h * 0.7, 0).add(right(fwd()).multiply(0.5));
        Vector point = to.lengthSquared() > 0.01 ? to.clone().normalize().setY(0.35).normalize() : fwd();
        maceDir = steer(maceDir, point, 0.3);
        coreBoost = -0.15;
        if (actionTick == 8 && well == null) {
            well = new Well(wellSpot.clone());
            World world = wellSpot.getWorld();
            well.core = spawnBlock(wellSpot.clone().add(0, 1.3, 0), Material.BLACK_CONCRETE.createBlockData(), PHOTON_BLUE, 2);
            for (int i = 0; i < 10; i++) {
                Material m = i % 3 == 0 ? Material.SHROOMLIGHT : Material.LIGHT_BLUE_STAINED_GLASS;
                well.disk.add(spawnBlock(wellSpot.clone().add(0, 1.3, 0), m.createBlockData(), null, 2));
            }
            // The floor tells the whole story: blue = you are being pulled, crimson = the one place you never stand.
            well.pull = props.add(new HollowProps.Ring(props, wellSpot, (float) WELL_PULL_R, 0.04f, 22,
                    Material.LIGHT_BLUE_CONCRETE, BLUESHIFT, 0.12f, 0.035f, 0));
            well.lethal = props.add(new HollowProps.Ring(props, wellSpot, (float) WELL_CORE_R, 0.05f, 12,
                    Material.RED_CONCRETE, CRIMSON, 0.2f, 0.04f, 0));
            world.playSound(wellSpot, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
            world.playSound(wellSpot, Sound.ENTITY_WARDEN_HEARTBEAT, 1.3f, 0.6f);
        }
        if (actionTick >= 18) {
            endMove();
            wellCd = 340;
        }
    }

    private void tickWell(LivingEntity entity) {
        if (well == null) {
            return;
        }
        Well w = well;
        w.age++;
        World world = w.center.getWorld();
        Location heart = w.center.clone().add(0, 1.3, 0);
        double grown = Math.min(1.0, w.age / 30.0);
        w.spin += w.age < 30 ? 0.08 : 0.14;
        double closing = w.age >= 130 ? Math.max(0.0, 1.0 - (w.age - 130) / 10.0) : 1.0;
        place(w.core, heart, centered(new Quaternionf().rotateY(w.spin).rotateX(0.5f), (float) (1.1 * easeInOut(grown) * closing + 0.02)), 2);
        double diskR = (1.9 + (1.0 - grown) * 3.2) * closing;
        for (int i = 0; i < w.disk.size(); i++) {
            double a = w.spin + i * Math.PI * 2 / w.disk.size();
            Location at = heart.clone().add(Math.cos(a) * diskR, Math.sin(a) * 0.35, Math.sin(a) * diskR);
            place(w.disk.get(i), at, centered(new Quaternionf().rotateY((float) a).rotateX(0.7f), (float) (0.3 * closing + 0.01)), 2);
        }
        if (w.age % 10 == 2 && w.age < 130) {
            // Both rings turn, the rim slow and the core fast: the floor itself shows the swirl.
            if (w.pull != null) {
                w.pull.spin(-0.3f, 10);
            }
            if (w.lethal != null) {
                w.lethal.spin(0.7f, 10);
            }
        }
        if (w.age == 130) {
            if (w.pull != null) {
                w.pull.resize(0.4f, 10);
            }
            if (w.lethal != null) {
                w.lethal.resize(0.2f, 10);
            }
        }
        if (w.age >= 30 && w.age < 130) {
            if (w.age % 4 == 0) {
                converge(world, heart, 4.5, Particle.END_ROD, 3);
            }
            if (w.age % 16 == 0) {
                world.playSound(w.center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.55f);
            }
            if (w.age % 4 == 0) {
                for (Player player : fighters()) {
                    if (horizontal(player.getLocation(), w.center) <= WELL_PULL_R + 1.0) {
                        player.sendActionBar(TextUtil.component("&b● EVENT HORIZON &8| &fwalk out of the blue ring"));
                    }
                }
            }
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                double d = horizontal(pl, w.center);
                if (d > WELL_PULL_R || Math.abs(pl.getY() - w.center.getY()) > 4.0) {
                    continue;
                }
                Vector pull = w.center.toVector().subtract(pl.toVector()).setY(0);
                if (pull.lengthSquared() > 0.01) {
                    pull.normalize().multiply(0.012 + (1.0 - d / WELL_PULL_R) * 0.045);
                    player.setVelocity(player.getVelocity().multiply(0.93).add(pull));
                }
                if (d <= WELL_CORE_R && gate("well", player, 16)) {
                    BossHits.hurt(player, entity, 34);
                    world.spawnParticle(Particle.SONIC_BOOM, pl.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                }
            }
        }
        if (w.age == 140) {
            world.spawnParticle(Particle.FLASH, heart, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SONIC_BOOM, heart, 1, 0, 0, 0, 0);
            props.add(new HollowProps.RingWall(props, w.center, 0.5f, 7.0f, 0.5f, 0.02f, 9,
                    Material.LIGHT_BLUE_STAINED_GLASS, BLUESHIFT, 18));
            world.playSound(heart, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.5f);
            world.playSound(heart, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.7f);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                double d = horizontal(pl, w.center);
                if (d > 7.0 || Math.abs(pl.getY() - w.center.getY()) > 4.0) {
                    continue;
                }
                Vector out = pl.toVector().subtract(w.center.toVector()).setY(0);
                if (out.lengthSquared() < 0.01) {
                    out = new Vector(0.01, 0, 0);
                }
                player.setVelocity(out.normalize().multiply(0.9).setY(0.35));
            }
            retireWell();
        }
    }

    private void retireWell() {
        if (well == null) {
            return;
        }
        if (well.pull != null) {
            well.pull.expire();
        }
        if (well.lethal != null) {
            well.lethal.expire();
        }
        discard(well.core);
        for (BlockDisplay piece : well.disk) {
            discard(piece);
        }
        well = null;
    }

    // ------------------------------------------------------------------ FOLD (Collapse)

    private void beginFold(LivingEntity entity, List<Player> fighters) {
        Player target = farthest(entity.getLocation(), fighters, 22.0);
        if (target == null) {
            target = fighters.get(ThreadLocalRandom.current().nextInt(fighters.size()));
        }
        start(Move.FOLD, target);
        foldBase = AttributeUtil.getBase(entity, AttributeUtil.scale(), instance.getTemplate().getAttributes().getScale());
        foldChain = instance.healthPercent() <= 20.0 ? 1 : 0;
        startFold(entity);
        teach(Move.FOLD, "&bFOLD", "&7The sun that follows you is where he lands. When it stops, leave it.");
    }

    private void startFold(LivingEntity entity) {
        actionTick = 0;
        foldSigil = null;
        foldFrom = entity.getLocation().clone();
        maceHeld = false;
        if (foldGlyph != null) {
            foldGlyph.expire();
        }
        Player target = target();
        Location spot = target != null ? target.getLocation().clone() : entity.getLocation().clone();
        clampToArena(spot, 1.5);
        // Held (no countdown) while it tracks; it starts filling the tick it plants.
        foldGlyph = props.add(new HollowProps.Glyph(props, ground(spot), (float) SIGIL_R, 22,
                HollowProps.Palette.INFALL, true).hold());
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.4f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 1.8f);
    }

    /*
     * He shrinks into a point (real entity scale), the sigil tracks the target then plants,
     * he unfolds on the sigil and slams. Tracking 8 ticks, plant-to-impact 22 ticks.
     */
    private void tickFold(LivingEntity entity, Form form) {
        actionTick++;
        World world = entity.getWorld();
        Player target = target();
        if (actionTick <= 8 && target != null) {
            Location spot = target.getLocation().clone();
            clampToArena(spot, 1.5);
            foldSigil = ground(spot);
            if (foldGlyph != null) {
                foldGlyph.follow(foldSigil);
            }
        }
        if (foldSigil == null) {
            foldSigil = ground(entity.getLocation());
        }
        if (actionTick == 8 && foldGlyph != null) {
            foldGlyph.start();
            world.playSound(foldSigil, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.4f);
        }
        double scale = foldBase > 0 ? foldBase : 1.4;
        if (actionTick <= 8) {
            double p = easeInOut(actionTick / 8.0);
            double now = scale * (1.0 - 0.95 * p);
            AttributeUtil.setBase(entity, AttributeUtil.scale(), now);
            rigScale = now / scale;
            folded = actionTick >= 5;
            if (actionTick % 2 == 0) {
                converge(world, corePos(entity), 1.6, Particle.END_ROD, 3);
            }
        }
        if (actionTick == 9) {
            folded = true;
            world.playSound(foldFrom, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            afterglows.add(new Afterglow(Afterglow.VOID_MOTE, foldFrom.clone().add(0, 1.4, 0), null, 14));
        }
        if (actionTick <= 30) {
            int remaining = 30 - actionTick;
            fuse(foldSigil, remaining);
            if (actionTick % 4 == 1) {
                actionBar(actionTick <= 8
                        ? "&b◈ FOLD &8| &fthe sun is following you"
                        : "&b◈ FOLD &8| &fit stopped — leave it &8| " + meter(remaining, 22, "&b"));
            }
        }
        if (actionTick == 22) {
            Location arrive = foldSigil.clone();
            Vector look = target != null ? target.getLocation().toVector().subtract(arrive.toVector()).setY(0) : fwd();
            if (look.lengthSquared() > 0.01) {
                bodyYaw = yawOf(look);
            }
            arrive.setYaw(bodyYaw);
            teleportBody(entity, arrive);
        }
        if (actionTick >= 22 && actionTick <= 30) {
            double p = easeInOut((actionTick - 22) / 8.0);
            double now = scale * (0.05 + 0.95 * p);
            AttributeUtil.setBase(entity, AttributeUtil.scale(), now);
            rigScale = now / scale;
            folded = actionTick < 27;
            if (actionTick % 2 == 0) {
                converge(world, foldSigil.clone().add(0, 1.2, 0), 2.4, Particle.END_ROD, 4);
            }
            if (actionTick == 22) {
                world.playSound(foldSigil, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.6f);
            }
        }
        if (actionTick == 30) {
            AttributeUtil.setBase(entity, AttributeUtil.scale(), scale);
            rigScale = 1.0;
            folded = false;
            Location at = foldSigil.clone().add(0, 0.4, 0);
            world.spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
            floorBurst(world, foldSigil, 16, 1.0);
            world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.9f);
            world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.3f, 0.6f);
            world.playSound(at, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.5f);
            waves.add(new Shockwave(foldSigil.clone(), 0.6, 0.9, SIGIL_R + 1.4, BLUESHIFT, 0.0, 0));
            props.add(new HollowProps.Seams(props, foldSigil, 0f, HollowProps.TAU, 6, 4.2f, 0.18f, true, 70));
            crater(foldSigil, 1.6, Material.BLACK_CONCRETE, Material.BLACKSTONE, 60);
            floorJolt(foldSigil, 6.5, 0.24);
            hold = 4;
            foldGlyph = null;
            maceHeld = true;
            Vector f = fwd();
            maceGrip = foldSigil.clone().add(f.clone().multiply(1.3)).add(0, 1.1, 0);
            maceDir = f.clone().multiply(0.5).add(new Vector(0, -0.86, 0)).normalize();
            double r2 = (SIGIL_R + 0.2) * (SIGIL_R + 0.2);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontalSq(pl, foldSigil) > r2 || Math.abs(pl.getY() - foldSigil.getY()) > 3.0) {
                    continue;
                }
                BossHits.hurt(player, entity, 46);
                Vector out = pl.toVector().subtract(foldSigil.toVector()).setY(0);
                if (out.lengthSquared() < 0.01) {
                    out = f.clone();
                }
                player.setVelocity(out.normalize().multiply(0.6).setY(0.7));
            }
            return;
        }
        if (actionTick < 44) {
            return;
        }
        List<Player> fighters = fighters();
        if (foldChain > 0 && !fighters.isEmpty()) {
            foldChain--;
            Player next = fighters.get(ThreadLocalRandom.current().nextInt(fighters.size()));
            moveTarget = next.getUniqueId();
            startFold(entity);
            return;
        }
        endMove();
        foldCd = 150;
    }

    // ------------------------------------------------------------------ EJECTA (Collapse)

    private final List<UUID> ejectaTargets = new ArrayList<>();

    private void beginEjecta(LivingEntity entity, List<Player> fighters) {
        start(Move.EJECTA, null);
        maceHeld = true;
        ejectaTargets.clear();
        List<Player> pool = new ArrayList<>(fighters);
        java.util.Collections.shuffle(pool);
        for (int i = 0; i < pool.size() && i < 3; i++) {
            ejectaTargets.add(pool.get(i).getUniqueId());
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.1f, 0.5f);
        world.playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.7f);
        teach(Move.EJECTA, "&bEJECTA", "&7The disk throws back what it cannot swallow. Leave the sun where it lands.");
    }

    /*
     * He lifts the mace to the disk and points: one plate per target leaves orbit seven ticks apart,
     * swings out to the rim, and comes down on a blue sun glyph thirty ticks after it was called.
     */
    private void tickEjecta(LivingEntity entity) {
        actionTick++;
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        double p = Math.min(1.0, actionTick / 8.0);
        maceGrip = feet.clone().add(0, h * (0.6 + 0.45 * easeInOut(p)), 0).add(right(fwd()).multiply(0.5));
        maceDir = blend(new Vector(0, -1, 0), new Vector(0, 1, 0), easeInOut(p));
        coreBoost = -0.1;
        for (int i = 0; i < ejectaTargets.size(); i++) {
            if (actionTick != 4 + i * 7) {
                continue;
            }
            Player target = Bukkit.getPlayer(ejectaTargets.get(i));
            if (!vulnerable(target) || !target.getWorld().equals(center.getWorld())) {
                continue;
            }
            face(entity, target.getLocation().toVector().subtract(feet.toVector()).setY(0), 30.0f);
            Location spot = target.getLocation().clone();
            clampToArena(spot, 1.2);
            Location landing = accretion.fling(ground(spot), 30);
            if (landing != null) {
                glyph(landing, 2.4, 30, Form.COLLAPSE);
                actionBar("&b◉ EJECTA &8| &fa plate of the forge is coming back down — leave the sun");
            }
        }
        if (actionTick >= 4 + ejectaTargets.size() * 7 + 6) {
            endMove();
            ejectaCd = instance.healthPercent() <= 20.0 ? 130 : 170;
        }
    }

    /** A thrown plate lands: the forge hits back with its own stone. */
    private void landPlate(Location at, BlockData plate) {
        World world = at.getWorld();
        LivingEntity entity = instance.getEntity();
        if (world == null) {
            return;
        }
        Location mid = at.clone().add(0, 0.4, 0);
        world.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.BLOCK, mid, 30, 0.8, 0.3, 0.8, 0.2, plate);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.6f);
        world.playSound(at, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.5f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        props.add(new HollowProps.RingWall(props, at, 0.4f, 3.2f, 0.5f, 0.02f, 8,
                Material.LIGHT_BLUE_STAINED_GLASS, BLUESHIFT, 14));
        de.aetherion.bossengine.fx.FakeDestruction.spawnDebris(at.clone().add(0, 0.3, 0), instance, 6, 0.38, 0.7, 26,
                new Material[]{plate.getMaterial(), Material.BLACKSTONE});
        crater(at, 1.2, Material.BLACK_CONCRETE, Material.BLACKSTONE, 60);
        floorJolt(at, 5.5, 0.22);
        if (entity == null || !entity.isValid()) {
            return;
        }
        double r2 = 2.6 * 2.6;
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontalSq(pl, at) > r2 || Math.abs(pl.getY() - at.getY()) > 2.5) {
                continue;
            }
            BossHits.hurt(player, entity, 40);
            Vector out = pl.toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = new Vector(0.01, 0, 0);
            }
            player.setVelocity(out.normalize().multiply(0.7).setY(0.5));
        }
    }

    // ------------------------------------------------------------------ LAST LIGHT (Collapse ≤15%)

    private void beginNova(LivingEntity entity) {
        start(Move.NOVA, null);
        novaStage = 0;
        novaTick = 0;
        novaDamage = 0.0;
        novaNeed = Math.max(1.0, instance.getCombatMaxHealth() * 0.04);
        maceHeld = false;
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
        world.playSound(entity.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.6f);
        shout("&f&lThe Hollow Sun&7: &fIf I go dark, &lI take the sky with me.");
        titleNear("&f&lLAST LIGHT", "&aBreak the core before it ignites!");
        taught.add(Move.NOVA);
    }

    /*
     * Stage 0 walks to center. Stage 1 charges for 7s with the core over his head and every hit
     * counts toward cracking it. Stage 2 = cracked: he kneels, takes +50%. Stage 3 = it went nova.
     */
    private void tickNova(LivingEntity entity) {
        actionTick++;
        novaTick++;
        World world = entity.getWorld();
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        switch (novaStage) {
            case 0 -> {
                Vector to = center.toVector().subtract(feet.toVector()).setY(0);
                if (to.lengthSquared() > 0.04) {
                    face(entity, to, 12.0f);
                    Location next = feet.clone().add(to.clone().normalize().multiply(Math.min(0.45, to.length())));
                    next.setY(floorY);
                    next.setYaw(bodyYaw);
                    teleportBody(entity, next);
                }
                if (novaTick >= 18) {
                    novaStage = 1;
                    novaTick = 0;
                    novaShell = spawnBlock(corePos(entity), Material.WHITE_STAINED_GLASS.createBlockData(), HOT, 2);
                    world.playSound(feet, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3f, 0.5f);
                    // Integrity is a green ring on the floor around him: every hit eats a piece of it.
                    if (novaRing != null) {
                        novaRing.expire();
                    }
                    novaRing = props.add(new HollowProps.Ring(props, ground(feet), 2.8f, 0.05f, 20, Material.LIME_CONCRETE,
                            OPENING, 0.22f, 0.06f, 0));
                    // The dead star relights: the night pales toward dusk while it charges.
                    sky.slide(world, 13400, 34);
                }
            }
            case 1 -> {
                int charge = 140;
                double p = novaTick / (double) charge;
                Location over = feet.clone().add(0, h + 1.6, 0);
                coreOverride = over;
                coreBoost = 1.4 * p;
                bodyPitch = (float) (-35.0 * Math.min(1.0, novaTick / 20.0));
                double integrity = Math.max(0.0, 1.0 - novaDamage / novaNeed);
                float shellSize = (float) (1.2 + 2.2 * p);
                place(novaShell, over, centered(new Quaternionf().rotateY(clock * 0.07f).rotateX(0.4f), shellSize), 2);
                if (novaShell != null && novaShell.isValid()) {
                    novaShell.setGlowColorOverride(novaTick > charge - 20 && novaTick % 2 == 0 ? HOT : SOLAR);
                }
                if (novaRing != null) {
                    novaRing.showFraction((float) integrity);
                }
                if (novaTick % 4 == 0) {
                    converge(world, over, 5.0, Particle.END_ROD, 3);
                    int secs = (int) Math.ceil((charge - novaTick) / 20.0);
                    actionBar("&f☀ LAST LIGHT &8| &aBREAK THE CORE &8| " + meter((int) Math.ceil(integrity * 100), 100, "&a")
                            + " &8| &f" + secs + "s");
                }
                int beat = (int) Math.round(20 - 15 * p);
                if (novaTick % Math.max(4, beat) == 0) {
                    world.playSound(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 1.3f, 0.6f + (float) p * 0.4f);
                }
                if (novaDamage >= novaNeed) {
                    novaBreak(entity, over);
                    novaStage = 2;
                    novaTick = 0;
                    dropNovaRing();
                    sky.slide(world, STARFALL_IMPLODE_SKY, 260);
                } else if (novaTick >= charge) {
                    novaDetonate(entity, over);
                    novaStage = 3;
                    novaTick = 0;
                    dropNovaRing();
                    sky.flash(6000, 4, STARFALL_IMPLODE_SKY);
                }
            }
            case 2 -> {
                coreOverride = null;
                coreBoost = -0.2 + 0.08 * Math.sin(novaTick * 0.8);
                bodyPitch = 40.0f;
                entity.setRotation(bodyYaw, bodyPitch);
                if (novaTick % 6 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, corePos(entity), 4, 0.3, 0.3, 0.3, 0.05);
                }
                if (novaTick >= 90) {
                    endMove();
                    novaCd = 900;
                }
            }
            default -> {
                coreOverride = null;
                bodyPitch = 0.0f;
                if (novaTick == 16) {
                    discard(novaShell);
                    novaShell = null;
                }
                if (novaTick >= 34) {
                    discard(novaShell);
                    novaShell = null;
                    endMove();
                    novaCd = 900;
                }
            }
        }
    }

    private void dropNovaRing() {
        if (novaRing != null) {
            novaRing.expire();
            novaRing = null;
        }
    }

    private void novaFeedback() {
        if (clock - lastNovaFeedback < 3) {
            return;
        }
        lastNovaFeedback = clock;
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || coreOverride == null) {
            return;
        }
        World world = entity.getWorld();
        double lost = Math.min(1.0, novaDamage / novaNeed);
        world.spawnParticle(Particle.ELECTRIC_SPARK, coreOverride, 10, 0.4, 0.4, 0.4, 0.12);
        world.spawnParticle(Particle.CRIT, coreOverride, 6, 0.4, 0.4, 0.4, 0.2);
        world.playSound(coreOverride, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.1f, 0.8f + (float) lost * 1.1f);
        world.playSound(coreOverride, Sound.BLOCK_GLASS_HIT, 0.8f, 0.6f + (float) lost);
    }

    private void novaBreak(LivingEntity entity, Location over) {
        World world = entity.getWorld();
        discard(novaShell);
        novaShell = null;
        world.spawnParticle(Particle.FLASH, over, 1, 0, 0, 0, 0);
        world.playSound(over, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.6f);
        world.playSound(over, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.6f);
        world.playSound(over, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            Material m = i % 2 == 0 ? Material.WHITE_STAINED_GLASS : Material.BLACK_STAINED_GLASS;
            BlockDisplay piece = spawnBlock(over, m.createBlockData(), null, 1);
            Vector v = new Vector(random.nextDouble(-1, 1), random.nextDouble(0.2, 1.0), random.nextDouble(-1, 1)).normalize().multiply(0.45);
            shards.add(new Shard(piece, over.clone(), v, 30));
        }
        titleNear("&a&lCORE FRACTURED", "&7He is open — hit him!");
        shout("&b&lThe Hollow Sun&7: &7…you cracked a star.");
        expose(90, 1.5);
    }

    private void novaDetonate(LivingEntity entity, Location over) {
        World world = entity.getWorld();
        world.spawnParticle(Particle.FLASH, over, 2, 0.5, 0.5, 0.5, 0, null, true);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, over, 1, 0, 0, 0, 0, null, true);
        props.add(new HollowProps.Rays(props, over, 20, (float) arenaRadius + 4f, 0.7f, Material.PEARLESCENT_FROGLIGHT, HOT));
        props.add(new HollowProps.RingWall(props, ground(entity.getLocation()), 1.0f, (float) arenaRadius + 3f, 1.2f, 0.02f, 14,
                Material.WHITE_STAINED_GLASS, HOT, 32));
        world.playSound(over, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(over, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.5f);
        world.playSound(over, Sound.ITEM_TOTEM_USE, 0.8f, 0.6f);
        float grown = (float) ((arenaRadius + 8.0) * 2.0);
        if (novaShell != null && novaShell.isValid()) {
            novaShell.setInterpolationDelay(0);
            novaShell.setInterpolationDuration(12);
            novaShell.setTransformation(centered(new Quaternionf(), grown));
        }
        for (Player player : fighters()) {
            world.spawnParticle(Particle.FLASH, player.getEyeLocation().add(player.getLocation().getDirection()), 1, 0, 0, 0, 0);
            BossHits.crush(player, entity, 100);
            Vector out = player.getLocation().toVector().subtract(over.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = new Vector(0.01, 0, 0);
            }
            player.setVelocity(out.normalize().multiply(0.9).setY(0.5));
            player.showTitle(Title.title(TextUtil.component("&f&lNOVA"), TextUtil.component("&7Crack the core faster next time."), titleTimes()));
        }
        coreBoost = -0.3;
    }

    // ------------------------------------------------------------------ hazards

    private void tickHazards(LivingEntity entity) {
        // Props first: a telegraph made this tick starts counting next tick, in step with its hazard.
        props.tick();
        tickMeteors(entity);
        tickArcs(entity);
        tickLances(entity);
        tickWaves(entity);
        tickWell(entity);
        tickAfterglows();
        tickShards();
        if (accretion.isActive()) {
            accretion.tick(corePos(entity), this::landPlate, () -> coreKick = Math.min(0.25, coreKick + 0.18));
        }
    }

    private void tickWaves(LivingEntity entity) {
        Iterator<Shockwave> it = waves.iterator();
        while (it.hasNext()) {
            Shockwave wave = it.next();
            if (wave.delay > 0) {
                wave.delay--;
                continue;
            }
            World world = wave.center.getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            if (!wave.shown) {
                // One ring wall, pushed once, expanding in step with the damage band two ticks from now.
                wave.shown = true;
                wave.warm = 2;
                int life = (int) Math.ceil((wave.maxRadius - wave.radius) / Math.max(0.05, wave.speed)) + 2;
                boolean live = wave.power > 0;
                int segs = (int) clamp(Math.PI * 2 * wave.maxRadius / 1.3, 12, 40);
                props.add(new HollowProps.RingWall(props, wave.center, (float) wave.radius, (float) wave.maxRadius,
                        live ? 0.8f : 0.4f, live ? 0.55f : 0.02f, life,
                        live ? Material.WHITE_STAINED_GLASS : waveGlass(wave.color), wave.color, segs));
            }
            if (wave.warm > 0) {
                wave.warm--;
                continue;
            }
            wave.radius += wave.speed;
            if (wave.power > 0) {
                for (Player player : fighters()) {
                    if (wave.struck.contains(player.getUniqueId())) {
                        continue;
                    }
                    Location pl = player.getLocation();
                    double r = horizontal(pl, wave.center);
                    if (Math.abs(r - wave.radius) > 0.75 || pl.getY() - wave.center.getY() > 0.6
                            || wave.center.getY() - pl.getY() > 2.0) {
                        continue;
                    }
                    wave.struck.add(player.getUniqueId());
                    BossHits.hurt(player, entity, wave.power);
                    Vector out = pl.toVector().subtract(wave.center.toVector()).setY(0);
                    if (out.lengthSquared() < 0.01) {
                        out = new Vector(0.01, 0, 0);
                    }
                    player.setVelocity(out.normalize().multiply(0.6).setY(0.45));
                }
            }
            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private static Material waveGlass(Color color) {
        if (color == BLUESHIFT) {
            return Material.LIGHT_BLUE_STAINED_GLASS;
        }
        if (color == FLARE || color == CRIMSON) {
            return Material.RED_STAINED_GLASS;
        }
        if (color == EMBER) {
            return Material.ORANGE_STAINED_GLASS;
        }
        return Material.YELLOW_STAINED_GLASS;
    }

    private void teachWave() {
        if (taught.add(Move.NONE)) {
            actionBar("&b◯ SHOCKWAVE &8| &fJUMP the glowing ridge as it passes");
        }
    }

    private void tickAfterglows() {
        Iterator<Afterglow> it = afterglows.iterator();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (it.hasNext()) {
            Afterglow glow = it.next();
            glow.life--;
            World world = glow.at.getWorld();
            if (world == null || glow.life <= 0) {
                it.remove();
                continue;
            }
            switch (glow.kind) {
                case Afterglow.SMOKE -> {
                    if (glow.life % 4 == 0) {
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, glow.at.clone().add(0, 0.3, 0), 1, 0.3, 0.1, 0.3, 0.02);
                        world.spawnParticle(Particle.SMALL_FLAME, glow.at.clone().add(0, 0.1, 0), 1, 0.5, 0.05, 0.5, 0.005);
                    }
                }
                case Afterglow.SCORCH, Afterglow.CRACK -> {
                    if (glow.life % 3 == 0 && glow.to != null) {
                        double t = random.nextDouble();
                        Location p = lerp(glow.at, glow.to, t).add(0, 0.15, 0);
                        world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0.1, 0.02, 0.1, 0.005);
                        world.spawnParticle(Particle.SMOKE, p, 1, 0.1, 0.05, 0.1, 0.01);
                        if (glow.kind == Afterglow.CRACK) {
                            world.spawnParticle(Particle.DUST, p, 1, 0.05, 0, 0.05, 0, new Particle.DustOptions(EMBER, 0.8f));
                        }
                    }
                }
                case Afterglow.VOID_PULL -> {
                    if (glow.life % 2 == 0) {
                        converge(world, glow.at.clone().add(0, 0.6, 0), 3.0, Particle.END_ROD, 2);
                    }
                    for (Player player : fighters()) {
                        Location pl = player.getLocation();
                        double d = horizontal(pl, glow.at);
                        if (d > 6.0 || d < 0.6) {
                            continue;
                        }
                        Vector pull = glow.at.toVector().subtract(pl.toVector()).setY(0).normalize().multiply(0.04);
                        player.setVelocity(player.getVelocity().multiply(0.93).add(pull));
                    }
                }
                case Afterglow.VOID_MOTE -> world.spawnParticle(Particle.DUST, glow.at, 2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(BLUESHIFT, 1.0f));
                default -> {
                }
            }
        }
    }

    private void tickShards() {
        Iterator<Shard> it = shards.iterator();
        while (it.hasNext()) {
            Shard shard = it.next();
            shard.life--;
            if (shard.life <= 0 || shard.display == null || !shard.display.isValid()) {
                discard(shard.display);
                it.remove();
                continue;
            }
            shard.pos.add(shard.vel);
            shard.vel.setY(shard.vel.getY() - 0.035);
            shard.vel.multiply(0.96);
            float turn = shard.life * 0.4f;
            float size = 0.28f * Math.min(1.0f, shard.life / 10.0f);
            place(shard.display, shard.pos, centered(new Quaternionf().rotateY(turn).rotateX(turn * 0.8f), size), 1);
        }
    }

    /** Transition interrupts: pending threats fizzle into smoke instead of landing mid-cinematic. */
    private void fizzleHazards() {
        for (Meteor m : meteors) {
            if (m.locked && m.ground != null && m.ground.getWorld() != null) {
                m.ground.getWorld().spawnParticle(Particle.LARGE_SMOKE, m.ground.clone().add(0, 0.3, 0), 6, 0.6, 0.2, 0.6, 0.02);
            }
            discard(m.rock);
            discard(m.crust);
            discard(m.tail);
            if (m.glyph != null) {
                m.glyph.expire();
            }
        }
        meteors.clear();
        for (Arc arc : arcs) {
            discard(arc.blob);
            if (arc.glyph != null) {
                arc.glyph.expire();
            }
            if (arc.loop != null) {
                arc.loop.fade(6);
            }
        }
        arcs.clear();
        for (Lance lance : lances) {
            discard(lance.rod);
        }
        lances.clear();
        if (well != null && well.center.getWorld() != null) {
            well.center.getWorld().spawnParticle(Particle.END_ROD, well.center.clone().add(0, 1.3, 0), 12, 0.3, 0.3, 0.3, 0.06);
        }
        retireWell();
    }

    // ------------------------------------------------------------------ IGNITION (Main Sequence → Red Giant)

    private void tickIgnition(LivingEntity entity, int t) {
        World world = entity.getWorld();
        Location base = cinStart.clone();
        Double floor = floorAt(world, base.getX(), base.getZ(), floorY + 1.0);
        double fy = floor == null ? floorY : floor;
        double lift = t < 20 ? 0.6 * easeInOut(t / 20.0) : t < 70 ? 0.6 : 0.6 + 0.6 * easeInOut(Math.min(1.0, (t - 70) / 20.0));
        Location hold = base.clone();
        hold.setY(fy + lift);
        Player near = nearest(hold, fighters());
        if (near != null) {
            face(entity, near.getLocation().toVector().subtract(hold.toVector()).setY(0), 4.0f);
        }
        hold.setYaw(bodyYaw);
        teleportBody(entity, hold);
        Location feet = hold;
        if (t < 20) {
            coreBoost = (t / 3) % 2 == 0 ? 0.12 : -0.08;
            if (beat(t, 6) || beat(t, 13)) {
                world.playSound(feet, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1f, t < 10 ? 0.8f : 1.0f);
            }
        } else if (t < 90) {
            coreBoost = 1.0 * easeInOut(Math.min(1.0, (t - 20) / 50.0));
            if (beat(t, 20)) {
                // The red giant's color arrives with the swell: its first glyph is the ignition itself.
                glyph(ground(feet), 6.5, real(70), Form.GIANT);
                sky.slide(world, DUSK_SKY, 160);
            }
            if (beat(t, 32)) {
                // The Bloodstone splits under a star that no longer fits inside its armor.
                props.add(new HollowProps.Seams(props, ground(feet), (float) Math.toRadians(-bodyYaw), HollowProps.TAU, 9,
                        (float) Math.min(14.0, arenaRadius * 0.95), 0.26f, false, real(230)));
                world.playSound(feet, Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.5f);
                world.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
            }
            if (t % 10 == 0) {
                eruptEdge(world);
            }
            int remaining = 90 - t;
            fuse(feet, remaining);
            if (t % 4 == 0) {
                actionBar("&c✹ IGNITION &8| &fget out of the ring &8| " + meter(remaining, 70, "&c"));
            }
            if (t % 10 == 0) {
                world.playSound(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f + (t - 20) * 0.006f);
            }
            if (beat(t, 45)) {
                visualForm = Form.GIANT;
                applyRigMaterials(Form.GIANT);
                dress(entity, Form.GIANT);
                silhouette = Form.GIANT;
                // The photosphere blooms out of the core and closes around the knight.
                envelopeGoal = 1.0;
                world.spawnParticle(Particle.FLASH, corePos(entity), 1, 0, 0, 0, 0);
                world.playSound(feet, Sound.ITEM_FIRECHARGE_USE, 1.3f, 0.5f);
                world.playSound(feet, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
            }
        }
        if (beat(t, 90)) {
            Location core = corePos(entity);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0, null, true);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 1, 0, 0, 0, 0, null, true);
            props.add(new HollowProps.Rays(props, core, 16, 7.5f, 0.3f, Material.SHROOMLIGHT, FLARE));
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
            world.playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.3f, 0.6f);
            world.playSound(core, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.5f);
            Location ground = ground(feet);
            waves.add(new Shockwave(ground, 1.0, 0.9, 7.5, FLARE, 0.0, 0));
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontal(pl, feet) > 6.5 || Math.abs(pl.getY() - ground.getY()) > 4.0) {
                    continue;
                }
                BossHits.hurt(player, entity, 34);
                Vector out = pl.toVector().subtract(feet.toVector()).setY(0);
                if (out.lengthSquared() < 0.01) {
                    out = new Vector(0.01, 0, 0);
                }
                player.setVelocity(out.normalize().multiply(1.2).setY(0.5));
                player.setFireTicks(Math.max(player.getFireTicks(), 40));
            }
            titleNear("&c&lRED GIANT", "&7It swells. It does not stop swelling.");
        }
        if (t > 90) {
            coreBoost *= 0.85;
            if (t % 4 == 0) {
                world.spawnParticle(Particle.FALLING_LAVA, corePos(entity), 1, 0.5, 0.3, 0.5, 0);
            }
        }
        syncRig(entity);
    }

    private void eruptEdge(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double a = random.nextDouble(Math.PI * 2);
        Location edge = center.clone().add(Math.cos(a) * (arenaRadius + 1.0), -0.5, Math.sin(a) * (arenaRadius + 1.0));
        world.spawnParticle(Particle.LAVA, edge, 4, 0.4, 0.2, 0.4, 0, null, true);
        world.playSound(edge, Sound.BLOCK_LAVA_POP, 1.3f, 0.6f);
    }

    // ------------------------------------------------------------------ STARFALL (Red Giant → Collapse)

    /*
     * 0-24 glide to center · 24-60 rise and swell into a red sun overhead, the sky deepens ·
     * 60-80 unstable · 80 IMPLOSION: the envelope crushes to a point, the sky cuts to night, the floor's
     * glow goes out · 86 every sound stops · 104 one bell · 108-170 the crimson glyph and the fall ·
     * 170 impact: crater, cold seams, the floor tears up into the disk, three ridges to jump ·
     * 190+ he stands up, crown and mace re-form.
     */
    private void tickStarfall(LivingEntity entity, int t) {
        World world = entity.getWorld();
        double skyY = floorY + skyRise;
        double impactR = clamp(arenaRadius * 0.42, 4.5, 8.5);
        Location body;
        if (t <= 24) {
            double p = easeInOut(t / 24.0);
            body = lerp(cinStart, center.clone().add(0, 2.5, 0), p);
        } else if (t <= 60) {
            double p = easeInOut((t - 24) / 36.0);
            body = center.clone().add(0, 2.5 + (skyY - floorY - 2.5) * p, 0);
        } else if (t < 120) {
            double jitter = t >= 60 && t < STARFALL_COLLAPSE ? 0.12 : 0.0;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            body = center.clone().add(random.nextDouble(-jitter, jitter + 1.0E-6), skyY - floorY + Math.sin(t * 0.1) * 0.15,
                    random.nextDouble(-jitter, jitter + 1.0E-6));
        } else if (t < STARFALL_IMPACT) {
            double u = (t - 120) / (double) (STARFALL_IMPACT - 120);
            body = center.clone().add(0, (skyY - floorY) * (1.0 - u * u * u), 0);
        } else {
            body = center.clone();
        }
        Player near = nearest(center, fighters());
        if (near != null) {
            face(entity, near.getLocation().toVector().subtract(center.toVector()).setY(0), 3.0f);
        }
        if (t >= STARFALL_IMPACT && t < 200) {
            bodyPitch = (float) (55.0 * (1.0 - (t - STARFALL_IMPACT) / 30.0));
        } else if (t < STARFALL_IMPACT) {
            bodyPitch = 0.0f;
        }
        body.setYaw(bodyYaw);
        body.setPitch(bodyPitch);
        teleportBody(entity, body);

        // swell, heartbeat, instability
        if (t >= 24 && t < STARFALL_COLLAPSE) {
            coreBoost = 3.0 * easeInOut(Math.min(1.0, (t - 24) / 36.0));
            int beat = (int) Math.round(18 - 12 * Math.min(1.0, (t - 24) / 56.0));
            if (t % Math.max(5, beat) == 0) {
                world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.55f);
            }
            if (t % 9 == 0) {
                eruptEdge(world);
            }
        }
        if (beat(t, 24)) {
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
            sky.slide(world, 13600, 40);
        }
        if (beat(t, 40)) {
            titleNear("", "&cThe star is failing.");
        }
        if (t >= 60 && t < STARFALL_COLLAPSE) {
            coreBoost += ThreadLocalRandom.current().nextDouble(-0.25, 0.25);
            if (shell != null && shell.isValid()) {
                shell.setBlock((t % 4 < 2 ? Material.WHITE_STAINED_GLASS : Material.RED_STAINED_GLASS).createBlockData());
            }
            if (t % 4 == 0) {
                world.playSound(center, Sound.BLOCK_BASALT_BREAK, 1.2f, 0.5f);
                world.playSound(corePos(entity), Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 0.5f);
            }
            for (Player player : fighters()) {
                Vector pull = center.toVector().subtract(player.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > 1.0) {
                    player.setVelocity(player.getVelocity().multiply(0.95).add(pull.normalize().multiply(0.03)));
                }
            }
        }
        if (beat(t, STARFALL_COLLAPSE)) {
            Location core = corePos(entity);
            coreBoost = 0.0;
            visualForm = Form.COLLAPSE;
            applyRigMaterials(Form.COLLAPSE);
            crownShown = 0;
            maceShown = false;
            if (coreA != null && coreA.isValid()) {
                coreA.setInterpolationDuration(3);
            }
            // The implosion: a red sun the size of a house crushed to a point in three ticks.
            implodeEnvelope(core);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0, null, true);
            for (int i = 0; i < 24; i++) {
                Vector dir = randomUnit();
                Location from = core.clone().add(dir.clone().multiply(5.0));
                world.spawnParticle(Particle.END_ROD, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), 0.45, null, true);
            }
            world.playSound(core, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.5f);
            world.playSound(core, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 0.5f);
            world.playSound(core, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.8f);
            // The light goes out everywhere at once: the sky, and every glowing scar on the floor.
            sky.cut(STARFALL_IMPLODE_SKY);
            revertAllScorch();
        }
        if (beat(t, 86)) {
            // True silence. Not a quiet beat: every sound anyone near the forge can hear, stopped.
            for (Player player : viewers(center)) {
                player.stopAllSounds();
            }
        }
        if (beat(t, 104)) {
            world.playSound(center, Sound.BLOCK_BELL_USE, 1.6f, 0.5f);
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.5f);
            shout("&8…the light goes out.");
        }
        if (beat(t, 108)) {
            titleNear("&4&l☄ STARFALL", "&fGet out of the red. &7Then jump the ridges.");
            // Crimson, inward rays: the one place you never stand, drawn by a star that is falling in.
            props.add(new HollowProps.Glyph(props, center, (float) impactR, real(STARFALL_IMPACT - 108),
                    HollowProps.Palette.LETHAL, true));
        }
        if (t >= 108 && t < STARFALL_IMPACT) {
            int remaining = STARFALL_IMPACT - t;
            boolean hot = remaining <= 10;
            fuse(center, remaining);
            if (t % 4 == 0) {
                actionBar(hot
                        ? "&f&l⚠ IMPACT &8| &fOUT OF THE RED!"
                        : "&4☄ STARFALL &8| &fget out of the red &8| " + meter(remaining, 62, "&4"));
            }
        }
        if (beat(t, 118)) {
            startWhoosh(center, 1.3f, 0.6f);
        }
        if (t >= 120 && t < STARFALL_IMPACT) {
            Location core = corePos(entity);
            if (starfallTail == null || !starfallTail.isValid()) {
                starfallTail = spawnBlock(core, Material.WHITE_STAINED_GLASS.createBlockData(), BLUESHIFT, 1);
            }
            // One stretched rod of light above the falling star: longer the faster it drops.
            double u = (t - 120) / (double) (STARFALL_IMPACT - 120);
            place(starfallTail, core, rod(new Vector3f(0f, 1f, 0f), 0.4f, (float) (1.0 + 7.0 * u * u), (float) (0.9 - 0.4 * u)), 1);
            if (t % 3 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE, core.clone().add(0, 0.8, 0), 2, 0.4, 0.6, 0.4, 0.02, null, true);
            }
            if (beat(t, 158)) {
                world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
            }
        }
        if (beat(t, STARFALL_IMPACT)) {
            starfallImpact(entity, impactR);
        }
        if (t > STARFALL_IMPACT) {
            if (t % 3 == 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(impactR);
                world.spawnParticle(Particle.WHITE_ASH, center.clone().add(Math.cos(a) * r, 1.5, Math.sin(a) * r), 2, 0.5, 0.5, 0.5, 0.01);
            }
            if (beat(t, 190)) {
                titleNear("&b&lCOLLAPSE", "&7What is left of me is heavier than light.");
                shout("&b&lThe Hollow Sun&7: &fWhat is left of me is heavier than light.");
                world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f);
            }
            if (t >= 196 && t < 196 + CROWN_RAYS * 3) {
                int shown = (t - 196) / 3 + 1;
                if (shown != crownShown) {
                    crownShown = shown;
                    world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.6f + shown * 0.1f);
                }
            }
            if (beat(t, 222)) {
                maceShown = true;
                world.playSound(entity.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1.2f, 0.5f);
                world.spawnParticle(Particle.END_ROD, idleMaceGrip(entity), 12, 0.3, 0.6, 0.3, 0.04);
            }
        }
        syncRig(entity);
    }

    private void starfallImpact(LivingEntity entity, double impactR) {
        stopWhoosh();
        discard(starfallTail);
        starfallTail = null;
        World world = center.getWorld();
        Location mid = center.clone().add(0, 0.5, 0);
        world.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.SONIC_BOOM, mid, 1, 0, 0, 0, 0, null, true);
        floorBurst(world, center, 40, impactR * 0.4);
        // The forge breaks open: cold seams race out to the rim, and the floor plates tear loose and rise.
        props.add(new HollowProps.Seams(props, center, 0f, HollowProps.TAU, 10, (float) (impactR + 3.0), 0.3f, true, 170));
        accretion.start(world, floorY, center.clone().add(0, 2.0, 0));
        accretion.tear(center, 10, impactR + 1.5, 1.5);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        starCrater(impactR);
        dress(entity, Form.COLLAPSE);
        silhouette = Form.COLLAPSE;
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            double r = horizontal(pl, center);
            Vector out = pl.toVector().subtract(center.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = new Vector(0.01, 0, 0);
            }
            if (r <= impactR && Math.abs(pl.getY() - floorY) < 6.0) {
                BossHits.crush(player, entity, 90);
                player.setVelocity(out.normalize().multiply(0.9).setY(1.05));
                player.showTitle(Title.title(TextUtil.component("&4CRUSHED"),
                        TextUtil.component("&7Starfall: leave the red disc."), titleTimes()));
            } else {
                player.setVelocity(player.getVelocity().add(new Vector(0, 0.28, 0)));
            }
        }
        double maxR = arenaRadius + 2.0;
        for (int i = 0; i < 3; i++) {
            waves.add(new Shockwave(center.clone(), 1.0, 0.5, maxR, BLUESHIFT, 30.0, i * 14));
        }
        teachWave();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 2 / 4 + 0.3;
            afterglows.add(new Afterglow(Afterglow.SMOKE, center.clone().add(Math.cos(a) * impactR * 0.8, 0, Math.sin(a) * impactR * 0.8), null, 70));
        }
    }

    // ------------------------------------------------------------------ death: return to the sky

    private boolean tickDeath() {
        deathTick++;
        clock++;
        LivingEntity entity = instance.getEntity();
        World world = deathFocus.getWorld();
        if (world == null) {
            abort();
            return true;
        }
        tickScorches();
        tickShards();
        tickAfterglows();
        props.tick();
        sky.tick(center != null ? center : deathFocus);
        if (accretion.isActive()) {
            // The plates fall back into their holes while he shakes apart.
            accretion.tick(null, null, null);
        }
        double h = entity != null && entity.isValid() ? entity.getHeight() : 3.3;
        Location chest = deathFocus.clone().add(0, h * 0.6, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (deathTick < 40) {
            double t = deathTick / 40.0;
            if (entity != null && entity.isValid()) {
                Location shake = deathFocus.clone().add(random.nextDouble(-0.05, 0.05) * t, 0, random.nextDouble(-0.05, 0.05) * t);
                shake.setYaw(bodyYaw);
                teleportBody(entity, shake);
            }
            coreBoost = (deathTick % 4 < 2 ? 0.15 : -0.1) * (1.0 + t);
            int leaks = 1 + (int) (t * 4);
            for (int i = 0; i < leaks; i++) {
                Vector dir = randomUnit();
                world.spawnParticle(Particle.END_ROD, chest.clone().add(dir.clone().multiply(0.4)), 0, dir.getX(), dir.getY(), dir.getZ(), 0.35);
            }
            if (deathTick % 8 == 0) {
                world.playSound(chest, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.6f + (float) t * 0.8f);
            }
            if (deathTick == 20) {
                world.playSound(chest, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);
            }
            if (entity != null && entity.isValid()) {
                syncRig(entity);
            }
            return false;
        }
        if (deathTick == 40) {
            world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, null, true);
            burst(world, chest, Particle.END_ROD, 40, 0.5);
            world.playSound(chest, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
            world.playSound(chest, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            world.playSound(chest, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.3f, 0.5f);
            if (entity != null && entity.isValid()) {
                EntityEquipment eq = entity.getEquipment();
                if (eq != null) {
                    eq.setHelmet(null);
                    eq.setChestplate(null);
                    eq.setLeggings(null);
                    eq.setBoots(null);
                }
            }
            spawnHusks();
            for (BlockDisplay ray : crown) {
                if (ray != null && ray.isValid()) {
                    Vector v = randomUnit().multiply(0.3).setY(0.35);
                    fx.add(ray);
                    shards.add(new Shard(ray, ray.getLocation().clone(), v, 26));
                }
            }
            crown.clear();
            dropMace();
            coreOverride = chest.clone();
            visualForm = Form.MAIN;
            applyDeathMaterials();
            coreBoost = 0.2;
        }
        tickHusks();
        if (deathTick < 72) {
            coreBoost *= 0.92;
            coreOverride = chest.clone().add(0, Math.sin(deathTick * 0.15) * 0.08, 0);
            if (deathTick == 46 || deathTick == 58 || deathTick == 70) {
                world.playSound(chest, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
            }
            if (deathTick % 5 == 0) {
                world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
            }
        } else if (deathTick < 140) {
            double u = easeInOut((deathTick - 72) / 68.0);
            double ascend = Math.min(26.0, skyRise + 6.0);
            coreOverride = chest.clone().add(0, ascend * u, 0);
            rigScale = 1.0 - 0.65 * u;
            world.spawnParticle(Particle.END_ROD, coreOverride, 2, 0.1, 0.1, 0.1, 0.01, null, true);
            if (deathTick % 2 == 0) {
                world.spawnParticle(Particle.FIREWORK, coreOverride, 1, 0.15, 0.15, 0.15, 0.01, null, true);
            }
            if ((deathTick - 72) % 5 == 0) {
                float pitch = 0.6f + (deathTick - 72) / 68.0f * 1.4f;
                world.playSound(coreOverride, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, pitch);
            }
        } else if (deathTick < NOVA_HUSH) {
            fadeStar(world, chest);
        } else if (deathTick < NOVA_BLAST) {
            hushStar(world, chest);
        } else if (deathTick < DEATH_TICKS) {
            tickSupernova(world, deathTick - NOVA_BLAST);
            if (deathTick == NOVA_REMNANT) {
                for (Husk husk : husks) {
                    if (husk.stand != null && husk.stand.isValid()) {
                        world.spawnParticle(Particle.SMOKE, husk.stand.getLocation().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.01);
                    }
                }
                clearHusks();
            }
        }
        if (deathTick < DEATH_TICKS) {
            if (entity != null && entity.isValid()) {
                syncRig(entity);
            }
            return false;
        }
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        clearCombatFx();
        clearRig();
        clearHusks();
        clearDeathNova();
        accretion.clear();
        revertAllScorch();
        sky.release();
        rigScale = 1.0;
        return true;
    }

    // ------------------------------------------------------------------ death: supernova (visual only)

    /*
     * 140-151 the last light: the rise song winds down, the star cools white → gold → ember → crimson and inhales.
     * 152-157 true silence: every sound cut, a black pinpoint where the sun was, the whole blast staged unseen.
     * 158 detonation · 158-171 shells, SN-1987A ring hourglass, light spikes, polar jets, burning ejecta,
     * the shock sweeps the arena floor · 172-181 pulsar remnant sweeps its beams through a violet nebula,
     * stardust ash falls · 182 the pulsar winks out · 182-185 calm.
     * Nothing here damages, pushes, or otherwise touches a player.
     */
    private void fadeStar(World world, Location chest) {
        int t = deathTick - NOVA_FADE;
        double f = t / (double) (NOVA_HUSH - NOVA_FADE - 1);
        Location focus = novaApex(chest);
        deathNovaFocus = focus;
        coreOverride = focus.clone();
        coreBoost = 0.0;
        boolean flicker = t >= 4 && t % (t >= 8 ? 2 : 3) == 0;
        rigScale = (0.35 - 0.23 * easeInOut(f)) * (flicker ? 0.62 : 1.0);
        switch (t) {
            case 0 -> {
                stopWhoosh();
                setBlock(shell, Material.YELLOW_STAINED_GLASS, GOLD);
                skySound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.7f);
                skySound(focus, Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 0.6f);
                skySound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.5f);
            }
            case 4 -> {
                setBlock(coreB, Material.SHROOMLIGHT, null);
                setBlock(shell, Material.ORANGE_STAINED_GLASS, EMBER);
            }
            case 7 -> skySound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.42f);
            case 8 -> {
                setBlock(coreA, Material.OCHRE_FROGLIGHT, null);
                setBlock(coreB, Material.MAGMA_BLOCK, null);
                setBlock(shell, Material.RED_STAINED_GLASS, CRIMSON);
            }
            default -> {
            }
        }
        if (t == 1 || t == 5 || t == 9) {
            float step = (t - 1) / 8.0f;
            skySound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f - step * 0.6f, 1.9f - step * 1.1f);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int inhale = 2 + t / 3;
        for (int i = 0; i < inhale; i++) {
            Vector dir = randomUnit();
            double r = 4.5 + random.nextDouble(3.5);
            Location from = focus.clone().add(dir.clone().multiply(r));
            world.spawnParticle(Particle.END_ROD, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), r * 0.1, null, true);
        }
        if (t % 2 == 0) {
            ringForce(world, focus, 0.4 + 2.6 * (1.0 - f), f < 0.5 ? EMBER : CRIMSON, 0.9f);
        }
        if (t % 3 == 0) {
            world.spawnParticle(Particle.DUST, focus, 3, 0.12, 0.12, 0.12, 0,
                    new Particle.DustOptions(f < 0.5 ? GOLD : CRIMSON, 1.1f), true);
        }
    }

    private void hushStar(World world, Location chest) {
        int t = deathTick - NOVA_HUSH;
        Location focus = deathNovaFocus != null ? deathNovaFocus : novaApex(chest);
        if (t == 0) {
            for (Player player : viewers(focus)) {
                player.stopAllSounds();
            }
            clearRig();
            rigScale = 0.01;
            coreOverride = null;
            stageSupernova(focus);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = focus.clone();
        if (t >= 3) {
            at.add(random.nextDouble(-0.06, 0.06), random.nextDouble(-0.06, 0.06), random.nextDouble(-0.06, 0.06));
        }
        float size = t < 5 ? 0.24f : 0.05f;
        place(deathNovaSeed, at, centered(new Quaternionf().rotateY(t * 0.9f).rotateX(0.6f), size), 1);
        if (t == 4) {
            tiltedRing(world, focus, deathNovaTilt, 3.6, 0.0, 40, new Particle.DustOptions(PHOTON, 0.7f));
        }
        if (t == 5) {
            tiltedRing(world, focus, deathNovaTilt, 1.2, 0.0, 20, new Particle.DustOptions(HOT, 0.9f));
        }
    }

    /** Spawned invisible during the silence so every piece interpolates from the first frame of the blast. */
    private void stageSupernova(Location focus) {
        clearDeathNova();
        deathNovaFocus = focus.clone();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        deathNovaTilt = new Quaternionf().rotateY((float) random.nextDouble(Math.PI * 2)).rotateX(0.28f);
        deathNovaSpin = 0.0f;
        deathNovaSeed = spawnBlock(focus, Material.BLACK_CONCRETE.createBlockData(), VOID, 1);

        for (int i = 0; i < 2; i++) {
            deathNovaShells.add(spawnBlock(focus, Material.PEARLESCENT_FROGLIGHT.createBlockData(), null, 0));
        }
        for (Material mat : NOVA_SHELL_MATS) {
            for (int k = 0; k < 2; k++) {
                deathNovaShells.add(spawnBlock(focus, mat.createBlockData(), null, 0));
            }
        }

        for (int ring = 0; ring < NOVA_RING_SEGS.length; ring++) {
            Material mat = ring == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.PURPLE_STAINED_GLASS;
            Color glow = ring == 0 ? SOLAR : VIOLET;
            for (int seg = 0; seg < NOVA_RING_SEGS[ring]; seg++) {
                deathNovaRings.add(spawnBlock(focus, mat.createBlockData(), glow, 0));
            }
        }

        double height = Math.max(6.0, focus.getY() - floorY);
        double reach = height + 4.0;
        int spikes = 16;
        for (int i = 0; i < spikes; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / spikes;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = i * 2.39996 + random.nextDouble(0.4);
            double len = reach * random.nextDouble(0.45, 0.85);
            if (y < 0) {
                len = Math.min(len, (height - 3.0) / -y);
            }
            deathNovaRayReach.add(new Vector3f((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r)).mul((float) len));
            deathNovaRays.add(spawnBlock(focus, Material.PEARLESCENT_FROGLIGHT.createBlockData(), HOT, 0));
        }
        deathNovaRayReach.add(deathNovaTilt.transform(new Vector3f(0, 1, 0)).mul((float) (reach * 1.6)));
        deathNovaRays.add(spawnBlock(focus, Material.WHITE_STAINED_GLASS.createBlockData(), PHOTON, 0));
        deathNovaRayReach.add(deathNovaTilt.transform(new Vector3f(0, -1, 0)).mul((float) (height * 0.6)));
        deathNovaRays.add(spawnBlock(focus, Material.WHITE_STAINED_GLASS.createBlockData(), PHOTON, 0));

        for (int i = 0; i < 2; i++) {
            deathNovaBeams[i] = spawnBlock(focus, Material.WHITE_STAINED_GLASS.createBlockData(), PHOTON, 0);
        }

        Material[] rock = {Material.MAGMA_BLOCK, Material.SHROOMLIGHT, Material.OCHRE_FROGLIGHT,
                Material.CRYING_OBSIDIAN, Material.PEARLESCENT_FROGLIGHT};
        Color[] heat = {EMBER, GOLD, SOLAR, VIOLET, HOT};
        for (int i = 0; i < 24; i++) {
            int kind = i % rock.length;
            BlockDisplay chunk = spawnBlock(focus, rock[kind].createBlockData(), heat[kind], 1);
            Vector vel = randomUnit().multiply(random.nextDouble(1.5, 2.4));
            deathNovaEjecta.add(new Ejecta(chunk, focus.clone(), vel, heat[kind],
                    (float) random.nextDouble(0.5, 1.0), random.nextInt(16, 27)));
        }
    }

    private void tickSupernova(World world, int age) {
        Location focus = deathNovaFocus;
        if (focus == null) {
            return;
        }
        double height = Math.max(6.0, focus.getY() - floorY);
        double reach = height + 4.0;
        int remnant = NOVA_REMNANT - NOVA_BLAST;
        int calm = NOVA_CALM - NOVA_BLAST;
        if (age == 0) {
            detonate(world, focus);
        }
        if (age < calm) {
            tickNovaShells(world, focus, reach, age);
            tickNovaRings(reach, age, remnant, calm);
            tickNovaRays(age);
            tickNovaEjecta(world);
        }
        if (age > 0 && age < remnant) {
            double r = reach * 1.3 * easeOutQuart((age + 1) / (double) remnant);
            tiltedRing(world, focus, deathNovaTilt, r, 0.0, 64, new Particle.DustOptions(age % 2 == 0 ? HOT : SOLAR, 1.6f));
            if (age % 2 == 0) {
                Particle.DustOptions violet = new Particle.DustOptions(VIOLET, 1.3f);
                tiltedRing(world, focus, deathNovaTilt, r * NOVA_RING_SCALE[1], r * NOVA_RING_LIFT[1], 24, violet);
                tiltedRing(world, focus, deathNovaTilt, r * NOVA_RING_SCALE[2], r * NOVA_RING_LIFT[2], 24, violet);
                double cloud = 1.0 + r * 0.4;
                world.spawnParticle(Particle.FIREWORK, focus, 12, cloud, cloud, cloud, 0.05, null, true);
                world.spawnParticle(Particle.END_ROD, focus, 6, cloud, cloud, cloud, 0.03, null, true);
            }
            if (age == 2) {
                world.spawnParticle(Particle.FLASH, focus, 4, 3.0, 3.0, 3.0, 0, null, true);
                skySound(focus, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 1.6f, 0.7f);
                skySound(focus, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            }
            if (age == 3) {
                titleNear("&f&l✦ SUPERNOVA ✦", "&6The Hollow Sun &7has returned to the sky");
            }
            if (age == 4) {
                shout("&6&lThe Hollow Sun &fwent supernova. &7The sky took it back.");
            }
            if (age == 5) {
                skySound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.55f);
            }
            if (age == 10) {
                skySound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 0.45f);
            }
            if (age >= 5) {
                sweepFloor(world, (age - 5) / (double) (remnant - 6));
            }
        }
        if (age >= remnant && age < calm) {
            tickPulsar(world, focus, (age - remnant) / (double) (calm - remnant - 1), age - remnant);
            stardust(world, 14);
        }
        if (age == calm) {
            world.spawnParticle(Particle.END_ROD, focus, 12, 0.1, 0.1, 0.1, 0.05, null, true);
            world.spawnParticle(Particle.FIREWORK, focus, 6, 0.1, 0.1, 0.1, 0.02, null, true);
            skySound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
            skySound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
            clearDeathNova();
        }
        if (age == calm + 1) {
            Location ground = deathFocus != null ? deathFocus : focus;
            world.playSound(ground, Sound.BLOCK_BELL_RESONATE, 0.7f, 1.3f);
            shout("&8…and the sky is quiet again.");
        }
        if (age > calm) {
            stardust(world, Math.max(1, 8 - (age - calm) * 2));
        }
    }

    private void detonate(World world, Location focus) {
        place(deathNovaSeed, focus, centered(new Quaternionf(), 0.001f), 0);
        // The geometry carries the blast (shells, rings, rays, ejecta). Particles are only the spray.
        world.spawnParticle(Particle.FLASH, focus, 4, 1.5, 1.5, 1.5, 0, null, true);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 2, 1.0, 1.0, 1.0, 0, null, true);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Vector3f p = deathNovaTilt.transform(new Vector3f((float) (Math.cos(a) * 2.5), 0, (float) (Math.sin(a) * 2.5)));
            world.spawnParticle(Particle.SONIC_BOOM, focus.clone().add(p.x, p.y, p.z), 1, 0, 0, 0, 0, null, true);
        }
        burst(world, focus, Particle.END_ROD, 50, 1.7);
        burst(world, focus, Particle.FIREWORK, 30, 1.3);
        burst(world, focus, Particle.ELECTRIC_SPARK, 16, 1.2);
        world.spawnParticle(Particle.DUST, focus, 30, 2.0, 2.0, 2.0, 0, new Particle.DustOptions(HOT, 2.4f), true);
        world.spawnParticle(Particle.DUST, focus, 24, 3.0, 3.0, 3.0, 0, new Particle.DustOptions(SOLAR, 2.0f), true);
        tiltedRing(world, focus, deathNovaTilt, 4.0, 0.0, 48, new Particle.DustOptions(HOT, 2.0f));
        // For five ticks the supernova is brighter than day; then the night comes back, one star richer.
        sky.flash(6000, 5, STARFALL_IMPLODE_SKY);
        for (Player player : viewers(focus)) {
            Location eye = player.getEyeLocation();
            player.spawnParticle(Particle.FLASH, eye.add(eye.getDirection().multiply(1.2)), 2, 0, 0, 0, 0);
        }
        skySound(focus, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        skySound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        skySound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.5f);
        skySound(focus, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 0.6f);
        skySound(focus, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 0.5f);
        skySound(focus, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.4f, 0.6f);
        skySound(focus, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.6f, 0.6f);
        skySound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.8f);
        skySound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.5f);
        skySound(focus, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.2f);
        skySound(focus, Sound.ITEM_TOTEM_USE, 0.9f, 0.6f);
    }

    /* Indices 0-1: the white-hot core flash. Then two cubes per shell layer, crossed so the pair reads round. */
    private void tickNovaShells(World world, Location focus, double reach, int age) {
        for (int i = 0; i < 2 && i < deathNovaShells.size(); i++) {
            BlockDisplay flash = deathNovaShells.get(i);
            if (flash == null || !flash.isValid()) {
                continue;
            }
            if (age >= 5) {
                discard(flash);
                continue;
            }
            if (age == 3) {
                setBlock(flash, Material.WHITE_STAINED_GLASS, null);
            }
            float size = age < 2 ? 6.5f : 11.0f;
            Quaternionf rot = i == 0 ? new Quaternionf().rotateY(age * 0.2f)
                    : new Quaternionf().rotateY(0.785f - age * 0.2f).rotateX(0.615f).rotateZ(0.785f);
            place(flash, focus, centered(rot, size), 2);
        }
        for (int layer = 0; layer < NOVA_SHELL_MATS.length; layer++) {
            int ticks = NOVA_SHELL_TICKS[layer];
            double max = reach * NOVA_SHELL_REACH[layer];
            for (int k = 0; k < 2; k++) {
                int index = 2 + layer * 2 + k;
                if (index >= deathNovaShells.size()) {
                    continue;
                }
                BlockDisplay layerShell = deathNovaShells.get(index);
                if (layerShell == null || !layerShell.isValid()) {
                    continue;
                }
                if (age > ticks) {
                    if (k == 0) {
                        dissolve(world, focus, max, NOVA_SHELL_TINT[layer]);
                    }
                    discard(layerShell);
                    continue;
                }
                double r = max * easeOutQuart((age + 1) / (double) ticks);
                float turn = age * 0.03f * (layer + 1);
                Quaternionf rot = k == 0 ? new Quaternionf().rotateY(turn).rotateX(0.615f).rotateZ(0.785f)
                        : new Quaternionf().rotateY(0.4f - turn * 0.7f);
                place(layerShell, focus, centered(rot, (float) (r * 2.0)), 2);
            }
        }
    }

    /* Equatorial ring plus the two coaxial outer rings: an hourglass that expands, cools, and thins to nothing. */
    private void tickNovaRings(double reach, int age, int remnant, int calm) {
        double peak = reach * 1.3;
        double radius;
        float thick;
        float width;
        if (age < remnant) {
            double u = easeOutQuart((age + 1) / (double) remnant);
            radius = peak * u;
            thick = (float) (0.9 - 0.6 * u);
            width = (float) (1.4 + 2.0 * u);
        } else {
            double u = Math.min(1.0, (age - remnant + 1) / (double) (calm - remnant));
            radius = peak + reach * 0.25 * u;
            thick = (float) Math.max(0.01, 0.3 * (1.0 - u));
            width = (float) (3.4 - 2.4 * u);
        }
        int index = 0;
        for (int ring = 0; ring < NOVA_RING_SEGS.length; ring++) {
            int segs = NOVA_RING_SEGS[ring];
            double r = radius * NOVA_RING_SCALE[ring];
            double lift = radius * NOVA_RING_LIFT[ring];
            float w = ring == 0 ? width : width * 0.6f;
            for (int seg = 0; seg < segs; seg++, index++) {
                if (index >= deathNovaRings.size()) {
                    return;
                }
                BlockDisplay piece = deathNovaRings.get(index);
                if (piece == null || !piece.isValid()) {
                    continue;
                }
                if (ring == 0 && age == 6) {
                    setBlock(piece, Material.OCHRE_FROGLIGHT, GOLD);
                } else if (ring == 0 && age == remnant) {
                    setBlock(piece, Material.ORANGE_STAINED_GLASS, EMBER);
                } else if (age == remnant + 6) {
                    piece.setGlowing(false);
                }
                place(piece, deathNovaFocus, novaSegment(seg, segs, r, lift, thick, w), 2);
            }
        }
    }

    /* Sixteen light spikes and two polar jets: they lance out, stretch, and thin to threads. */
    private void tickNovaRays(int age) {
        int spikes = deathNovaRays.size() - 2;
        for (int i = 0; i < deathNovaRays.size() && i < deathNovaRayReach.size(); i++) {
            BlockDisplay ray = deathNovaRays.get(i);
            if (ray == null || !ray.isValid()) {
                continue;
            }
            boolean jet = i >= spikes;
            int life = jet ? 14 : 10;
            if (age > life) {
                discard(ray);
                continue;
            }
            Vector3f full = deathNovaRayReach.get(i);
            float max = full.length();
            float len;
            float width;
            if (age < 3) {
                len = max * (0.55f + 0.225f * age);
                width = jet ? 1.6f : 0.9f;
            } else {
                float p = (age - 2) / (float) (life - 2);
                len = max * (1.0f + 0.4f * p);
                width = Math.max(0.01f, (jet ? 1.6f : 0.9f) * (1.0f - p));
            }
            place(ray, deathNovaFocus, rod(full, 0.2f, len, width), 2);
        }
    }

    private void tickNovaEjecta(World world) {
        Iterator<Ejecta> it = deathNovaEjecta.iterator();
        while (it.hasNext()) {
            Ejecta chunk = it.next();
            chunk.life--;
            if (chunk.life <= 0 || chunk.display == null || !chunk.display.isValid() || chunk.pos.getY() < floorY + 3.0) {
                world.spawnParticle(Particle.END_ROD, chunk.pos, 4, 0.1, 0.1, 0.1, 0.04, null, true);
                world.spawnParticle(Particle.SMOKE, chunk.pos, 3, 0.1, 0.1, 0.1, 0.01, null, true);
                discard(chunk.display);
                it.remove();
                continue;
            }
            Location prev = chunk.pos.clone();
            chunk.pos.add(chunk.vel);
            chunk.vel.multiply(0.9);
            chunk.vel.setY(chunk.vel.getY() - 0.012);
            float turn = chunk.life * 0.5f;
            float size = chunk.size * Math.min(1.0f, chunk.life / 8.0f);
            place(chunk.display, chunk.pos, centered(new Quaternionf().rotateY(turn).rotateX(turn * 0.7f), size), 1);
            streak(world, prev, chunk.pos, chunk.color, true);
        }
    }

    /* The neutron-star remnant: a spinning white point sweeping two beams, spinning down while its chime fades. */
    private void tickPulsar(World world, Location focus, double p, int t) {
        if (t == 0) {
            setBlock(deathNovaSeed, Material.PEARLESCENT_FROGLIGHT, HOT);
            world.spawnParticle(Particle.FLASH, focus, 1, 0, 0, 0, 0, null, true);
            skySound(focus, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.6f);
        }
        deathNovaSpin += (float) (0.9 - 0.6 * p);
        float core = (float) ((t % 2 == 0 ? 0.42 : 0.3) * (1.0 - 0.6 * p));
        place(deathNovaSeed, focus, centered(new Quaternionf().rotateY(deathNovaSpin * 1.7f).rotateX(0.6f), core), 1);
        double tilt = 0.5;
        Vector3f axis = deathNovaTilt.transform(new Vector3f(
                (float) (Math.sin(tilt) * Math.cos(deathNovaSpin)), (float) Math.cos(tilt), (float) (Math.sin(tilt) * Math.sin(deathNovaSpin))));
        float len = (float) (18.0 * (1.0 - 0.5 * p));
        float width = (float) (0.18 * (1.0 - p) + 0.02);
        place(deathNovaBeams[0], focus, rod(axis, 0.3f, len, width), 1);
        place(deathNovaBeams[1], focus, rod(new Vector3f(axis).negate(), 0.3f, len, width), 1);
        if (t % 3 == 0) {
            skySound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, (float) (0.8 * (1.0 - p) + 0.15), (float) (2.0 - 0.3 * p));
        }
        if (t % 2 == 0) {
            double spread = 6.0 + 4.0 * p;
            world.spawnParticle(Particle.DUST, focus, 10, spread, spread * 0.6, spread, 0, new Particle.DustOptions(VIOLET, 1.6f), true);
            world.spawnParticle(Particle.DUST, focus, 6, spread, spread * 0.6, spread, 0, new Particle.DustOptions(CRIMSON, 1.4f), true);
            world.spawnParticle(Particle.DUST, focus, 5, spread * 0.5, spread * 0.4, spread * 0.5, 0, new Particle.DustOptions(SOLAR, 1.2f), true);
        }
        world.spawnParticle(Particle.END_ROD, focus.clone().add(axis.x * len, axis.y * len, axis.z * len), 1, 0, 0, 0, 0, null, true);
    }

    /** The shockwave reaching the Bloodstone: a ring of light washing out across the floor. Visual only. */
    private void sweepFloor(World world, double g) {
        Location mid = center != null ? center : deathFocus;
        if (mid == null) {
            return;
        }
        double r = 1.0 + (arenaRadius + 6.0) * easeOut(g);
        Location floor = mid.clone();
        floor.setY(floorY + 0.15);
        ringForce(world, floor, r, g < 0.5 ? HOT : SOLAR, 1.4f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            double a = random.nextDouble(Math.PI * 2);
            Location at = floor.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            world.spawnParticle(Particle.WHITE_ASH, at, 2, 0.2, 0.3, 0.2, 0.02, null, true);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 0, 0, 1, 0, 0.12, null, true);
            }
        }
    }

    private void stardust(World world, int count) {
        Location mid = center != null ? center : deathFocus;
        if (mid == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = Math.sqrt(random.nextDouble()) * (arenaRadius + 4.0);
            Location at = mid.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            at.setY(floorY + random.nextDouble(6.0, 16.0));
            world.spawnParticle(Particle.WHITE_ASH, at, 2, 0.5, 0.5, 0.5, 0.01, null, true);
            if (i % 5 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 0, 0, -1, 0, 0.03, null, true);
            }
        }
    }

    private static void dissolve(World world, Location focus, double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        for (int i = 0; i < 48; i++) {
            Vector dir = randomUnit();
            Location at = focus.clone().add(dir.clone().multiply(radius));
            world.spawnParticle(Particle.FIREWORK, at, 0, dir.getX(), dir.getY(), dir.getZ(), 0.12, null, true);
            world.spawnParticle(Particle.DUST, at, 1, 0.3, 0.3, 0.3, 0, dust, true);
        }
    }

    private Location novaApex(Location chest) {
        return chest.clone().add(0, Math.min(26.0, skyRise + 6.0), 0);
    }

    /** One tangential slab of a ring lying in {@link #deathNovaTilt}, anchored at the nova focus. */
    private Transformation novaSegment(int seg, int segs, double radius, double lift, float thick, float width) {
        double a = Math.PI * 2 * seg / segs;
        float len = (float) (Math.PI * 2 * radius / segs * 1.1) + 0.05f;
        Quaternionf rot = new Quaternionf(deathNovaTilt).rotateY((float) -(a + Math.PI / 2));
        Vector3f mid = deathNovaTilt.transform(new Vector3f((float) (Math.cos(a) * radius), (float) lift, (float) (Math.sin(a) * radius)));
        Vector3f offset = rot.transform(new Vector3f(-len / 2f, -thick / 2f, -width / 2f)).add(mid);
        return new Transformation(offset, rot, new Vector3f(len, thick, width), new Quaternionf());
    }

    private static void tiltedRing(World world, Location at, Quaternionf plane, double radius, double lift, int points,
                                   Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Vector3f p = plane.transform(new Vector3f((float) (Math.cos(a) * radius), (float) lift, (float) (Math.sin(a) * radius)));
            world.spawnParticle(Particle.DUST, at.clone().add(p.x, p.y, p.z), 1, 0, 0, 0, 0, dust, true);
        }
    }

    /** Played beside each viewer, from the star's direction: the apex is too high for world sounds to carry. */
    private void skySound(Location from, Sound sound, float volume, float pitch) {
        for (Player player : viewers(from)) {
            Location ear = player.getEyeLocation();
            Vector to = from.toVector().subtract(ear.toVector());
            double d = to.length();
            Location at = d > 6.0 ? ear.add(to.multiply(6.0 / d)) : from;
            player.playSound(at, sound, volume, pitch);
        }
    }

    private void clearDeathNova() {
        for (BlockDisplay display : deathNovaShells) {
            discard(display);
        }
        for (BlockDisplay display : deathNovaRings) {
            discard(display);
        }
        for (BlockDisplay display : deathNovaRays) {
            discard(display);
        }
        for (Ejecta chunk : deathNovaEjecta) {
            discard(chunk.display);
        }
        for (int i = 0; i < deathNovaBeams.length; i++) {
            discard(deathNovaBeams[i]);
            deathNovaBeams[i] = null;
        }
        discard(deathNovaSeed);
        deathNovaSeed = null;
        deathNovaShells.clear();
        deathNovaRings.clear();
        deathNovaRays.clear();
        deathNovaRayReach.clear();
        deathNovaEjecta.clear();
    }

    private void spawnHusks() {
        clearHusks();
        World world = deathFocus.getWorld();
        double scale = foldBase > 0 ? foldBase : instance.getTemplate().getAttributes().getScale();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            ItemStack piece = deathArmor[i];
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            Location at = deathFocus.clone();
            at.setYaw(bodyYaw);
            ArmorStand stand = world.spawn(at, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setBasePlate(false);
                s.setPersistent(false);
                s.setSilent(true);
                s.setInvulnerable(true);
                instance.getKeys().tagBeamFx(s, instance.getInstanceId());
            });
            AttributeUtil.setBase(stand, AttributeUtil.scale(), scale);
            EntityEquipment eq = stand.getEquipment();
            switch (i) {
                case 0 -> eq.setHelmet(piece);
                case 1 -> eq.setChestplate(piece);
                case 2 -> eq.setLeggings(piece);
                default -> eq.setBoots(piece);
            }
            double a = Math.toRadians(bodyYaw) + i * Math.PI / 2 + random.nextDouble(-0.4, 0.4);
            Vector v = new Vector(Math.cos(a) * 0.14, 0.28 - i * 0.04, Math.sin(a) * 0.14);
            // Marker stands report no height; use the unscaled stand height so each piece lands on the floor.
            double drop = 1.975 * scale * (0.78 - i * 0.24);
            husks.add(new Husk(stand, at.clone(), v, i, drop));
        }
    }

    private void tickHusks() {
        World world = deathFocus.getWorld();
        for (Husk husk : husks) {
            if (husk.stand == null || !husk.stand.isValid() || husk.landed) {
                continue;
            }
            husk.age++;
            husk.pos.add(husk.vel);
            husk.vel.setY(husk.vel.getY() - 0.045);
            double floor = deathFocus.getY() - husk.drop;
            if (husk.pos.getY() <= floor && husk.vel.getY() < 0) {
                husk.pos.setY(floor);
                husk.landed = true;
                world.playSound(husk.pos, Sound.BLOCK_NETHERITE_BLOCK_FALL, 1.1f, 0.6f);
                world.playSound(husk.pos, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 0.5f);
                world.spawnParticle(Particle.SMOKE, husk.pos.clone().add(0, husk.drop, 0), 6, 0.2, 0.05, 0.2, 0.01);
            }
            husk.stand.teleport(husk.pos);
            double tumble = husk.age * 0.22;
            switch (husk.slot) {
                case 0 -> husk.stand.setHeadPose(new EulerAngle(tumble, tumble * 0.4, husk.landed ? 1.2 : tumble * 0.7));
                case 1 -> husk.stand.setBodyPose(new EulerAngle(Math.min(1.4, tumble * 0.5), 0, 0));
                default -> {
                    husk.stand.setLeftLegPose(new EulerAngle(Math.min(1.2, tumble * 0.4), 0, -Math.min(0.5, tumble * 0.2)));
                    husk.stand.setRightLegPose(new EulerAngle(-Math.min(1.2, tumble * 0.4), 0, Math.min(0.5, tumble * 0.2)));
                }
            }
        }
    }

    private void clearHusks() {
        for (Husk husk : husks) {
            if (husk.stand != null && husk.stand.isValid()) {
                husk.stand.remove();
            }
        }
        husks.clear();
    }

    private void dropMace() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (BlockDisplay part : new BlockDisplay[]{maceShaft, maceHead, maceStar}) {
            if (part != null && part.isValid()) {
                Vector v = new Vector(random.nextDouble(-0.15, 0.15), 0.25, random.nextDouble(-0.15, 0.15));
                fx.add(part);
                shards.add(new Shard(part, part.getLocation().clone(), v, 34));
            }
        }
        maceShaft = null;
        maceHead = null;
        maceStar = null;
    }

    // ------------------------------------------------------------------ rig

    private void spawnRig(LivingEntity entity) {
        spawnCore(entity);
        spawnCrown(entity);
        spawnMace(entity);
        crownShown = CROWN_RAYS;
        maceShown = true;
    }

    private void spawnLeaks(Location at) {
        for (BlockDisplay leak : leaks) {
            discardRig(leak);
        }
        leaks.clear();
        for (int i = 0; i < LEAKS; i++) {
            leaks.add(spawnRigBlock(at, Material.SHROOMLIGHT.createBlockData()));
        }
        applyRigMaterials(visualForm);
    }

    private void spawnCore(LivingEntity entity) {
        discardRig(coreA);
        discardRig(coreB);
        discardRig(shell);
        Location at = corePos(entity);
        coreA = spawnRigBlock(at, Material.OCHRE_FROGLIGHT.createBlockData());
        coreB = spawnRigBlock(at, Material.SHROOMLIGHT.createBlockData());
        shell = spawnRigBlock(at, Material.ORANGE_STAINED_GLASS.createBlockData());
        spawnLeaks(at);
        applyRigMaterials(visualForm);
    }

    private void spawnCrown(LivingEntity entity) {
        for (BlockDisplay ray : crown) {
            discardRig(ray);
        }
        crown.clear();
        Location at = entity.getLocation().clone().add(0, entity.getHeight(), 0);
        for (int i = 0; i < CROWN_RAYS; i++) {
            crown.add(spawnRigBlock(at, Material.END_ROD.createBlockData()));
        }
        applyRigMaterials(visualForm);
    }

    private void spawnMace(LivingEntity entity) {
        discardRig(maceShaft);
        discardRig(maceHead);
        discardRig(maceStar);
        Location at = idleMaceGrip(entity);
        maceShaft = spawnRigBlock(at, Material.POLISHED_BASALT.createBlockData());
        maceHead = spawnRigBlock(at, Material.GILDED_BLACKSTONE.createBlockData());
        maceStar = spawnRigBlock(at, Material.SHROOMLIGHT.createBlockData());
        applyRigMaterials(visualForm);
    }

    private void applyRigMaterials(Form form) {
        Material a;
        Material b;
        Material glass;
        Material leak;
        Material star;
        Color glow;
        switch (form) {
            case GIANT -> {
                a = Material.SHROOMLIGHT;
                b = Material.MAGMA_BLOCK;
                glass = Material.RED_STAINED_GLASS;
                leak = Material.OCHRE_FROGLIGHT;
                star = Material.MAGMA_BLOCK;
                glow = FLARE;
            }
            case COLLAPSE -> {
                // A black core in a blue-white photon shell: heavier than light, not "void".
                a = Material.BLACK_CONCRETE;
                b = Material.COAL_BLOCK;
                glass = Material.LIGHT_BLUE_STAINED_GLASS;
                leak = Material.WHITE_CONCRETE;
                star = Material.BLACK_CONCRETE;
                glow = PHOTON_BLUE;
            }
            default -> {
                a = Material.OCHRE_FROGLIGHT;
                b = Material.SHROOMLIGHT;
                glass = Material.ORANGE_STAINED_GLASS;
                leak = Material.SHROOMLIGHT;
                star = Material.SHROOMLIGHT;
                glow = GOLD;
            }
        }
        setBlock(coreA, a, form == Form.COLLAPSE ? PHOTON_BLUE : null);
        setBlock(coreB, b, null);
        setBlock(shell, glass, glow);
        for (BlockDisplay r : crown) {
            setBlock(r, Material.END_ROD, form == Form.COLLAPSE ? PHOTON_BLUE : null);
        }
        for (BlockDisplay l : leaks) {
            setBlock(l, leak, form == Form.COLLAPSE ? BLUESHIFT : form == Form.GIANT ? FLARE : GOLD);
        }
        setBlock(maceStar, star, glow);
    }

    private void applyDeathMaterials() {
        setBlock(coreA, Material.PEARLESCENT_FROGLIGHT, null);
        setBlock(coreB, Material.OCHRE_FROGLIGHT, null);
        setBlock(shell, Material.WHITE_STAINED_GLASS, SOLAR);
        for (BlockDisplay l : leaks) {
            setBlock(l, Material.PEARLESCENT_FROGLIGHT, SOLAR);
        }
    }

    private static void setBlock(BlockDisplay display, Material material, Color glow) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setBlock(material.createBlockData());
        display.setGlowing(glow != null);
        if (glow != null) {
            display.setGlowColorOverride(glow);
        }
    }

    private void syncRig(LivingEntity entity) {
        Form form = visualForm;
        Location feet = entity.getLocation();
        double h = entity.getHeight();
        spin += switch (form) {
            case MAIN -> 0.09f;
            case GIANT -> 0.05f;
            case COLLAPSE -> 0.16f;
        };
        Location corePos = coreOverride != null ? coreOverride : feet.clone().add(0, h * 0.6, 0);
        double base = switch (form) {
            case MAIN -> 0.5;
            case GIANT -> 0.95;
            case COLLAPSE -> 0.42;
        };
        double pulse = switch (form) {
            case MAIN -> 0.04 * Math.sin(clock * 0.18);
            case GIANT -> 0.07 * Math.sin(clock * 0.1);
            case COLLAPSE -> 0.05 * Math.sin(clock * 0.5) - (clock % 23 == 0 ? 0.1 : 0.0);
        };
        double shellRatio = form == Form.COLLAPSE ? 1.35 : 1.55;
        float s = (float) Math.max(0.02, (base + coreBoost + coreKick + pulse) * rigScale);
        place(coreA, corePos, centered(new Quaternionf().rotateY(spin).rotateX(0.62f), s), 2);
        place(coreB, corePos, centered(new Quaternionf().rotateY(-spin * 1.3f).rotateZ(0.785f).rotateX(0.615f), s * 0.9f), 2);
        place(shell, corePos, centered(new Quaternionf().rotateY(spin * 0.4f).rotateX(0.3f), (float) (s * shellRatio)), 2);
        syncLeaks(corePos, form, s);
        syncEnvelope(entity, feet, h);

        Location crownPos = feet.clone().add(0, h * 1.03, 0);
        double tilt = form == Form.COLLAPSE ? 0.95 : 0.35;
        float rayLen = (float) ((form == Form.GIANT ? 0.8 : 0.55) * rigScale);
        for (int i = 0; i < crown.size(); i++) {
            double a = spin * 0.3 + i * Math.PI * 2 / crown.size();
            Vector3f dir = new Vector3f((float) (Math.cos(a) * Math.cos(tilt)), (float) Math.sin(tilt), (float) (Math.sin(a) * Math.cos(tilt)));
            float shown = i < crownShown ? 1.0f : 0.0f;
            place(crown.get(i), crownPos, rod(dir, (float) (0.28 * rigScale), Math.max(0.001f, rayLen * shown),
                    (float) (1.2 * rigScale * shown + 0.001)), 2);
        }

        Location grip = maceHeld && maceGrip != null ? maceGrip : idleMaceGrip(entity);
        Vector dir = maceHeld ? maceDir : idleMaceDir();
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, -1, 0);
        }
        dir = dir.clone().normalize();
        float ms = (float) (rigScale * (maceShown ? 1.0 : 0.0) + 0.001);
        Vector3f axis = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
        place(maceShaft, grip, rod(axis, -0.4f * ms, 2.0f * ms, 0.16f * ms), 2);
        Location head = grip.clone().add(dir.clone().multiply(1.75 * ms));
        Quaternionf along = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
        place(maceHead, head, centered(along, 0.6f * ms), 2);
        place(maceStar, head, centered(new Quaternionf().rotateY(spin * 2.0f).rotateX(0.6f), 0.36f * ms), 2);
    }

    /*
     * Light leaking out between the plates. While he burns the rays pulse outward from the core; once
     * he has collapsed they fall inward, each streak sliding into the core and starting again outside.
     */
    private void syncLeaks(Location corePos, Form form, float coreSize) {
        if (leaks.isEmpty()) {
            return;
        }
        for (int i = 0; i < leaks.size(); i++) {
            double a = spin * 0.35 + i * Math.PI * 2 / leaks.size();
            double tilt = ((i % 3) - 1) * 0.45;
            Vector3f dir = new Vector3f((float) (Math.cos(a) * Math.cos(tilt)), (float) Math.sin(tilt),
                    (float) (Math.sin(a) * Math.cos(tilt)));
            float start;
            float len;
            float width;
            int interp = 2;
            if (form == Form.COLLAPSE) {
                double u = (clock * 0.055 + i * 0.37) % 1.0;
                double prevU = ((clock - 1) * 0.055 + i * 0.37) % 1.0;
                start = (float) (0.3 + 1.8 * (1.0 - u));
                len = (float) (0.08 + 0.6 * (1.0 - u));
                width = 0.05f;
                if (u < prevU) {
                    interp = 0;
                }
            } else {
                double pulse = Math.sin(clock * 0.18 + i * 1.7);
                double reach = form == Form.GIANT ? 1.7 : 0.75;
                start = coreSize * 0.45f;
                len = (float) (reach + 0.25 * pulse + coreBoost * (form == Form.GIANT ? 1.0 : 0.8));
                width = form == Form.GIANT ? 0.09f : 0.07f;
            }
            float r = (float) rigScale;
            place(leaks.get(i), corePos, rod(dir, start * r, Math.max(0.01f, len * r), width * r), interp);
        }
    }

    /*
     * Red Giant: the star is bigger than the knight. Two crossed glass hulls centred on his body,
     * breathing slowly like a pulsating variable star, swelling with the core and crushed on implosion.
     */
    private void syncEnvelope(LivingEntity entity, Location feet, double h) {
        if (envelopeDie > 0) {
            if (--envelopeDie == 0) {
                for (BlockDisplay hull : envelope) {
                    discardRig(hull);
                }
                envelope.clear();
            }
            return;
        }
        envelopeGrow = approach(envelopeGrow, envelopeGoal, 0.05);
        if (envelopeGrow <= 0.001 && envelopeGoal <= 0.0) {
            if (!envelope.isEmpty()) {
                for (BlockDisplay hull : envelope) {
                    discardRig(hull);
                }
                envelope.clear();
            }
            return;
        }
        Location mid = coreOverride != null ? coreOverride : feet.clone().add(0, h * 0.5, 0);
        if (envelope.isEmpty()) {
            envelope.add(spawnRigBlock(mid, Material.RED_STAINED_GLASS.createBlockData()));
            envelope.add(spawnRigBlock(mid, Material.ORANGE_STAINED_GLASS.createBlockData()));
            HollowProps.glow(envelope.get(0), FLARE);
        }
        double breathe = 1.0 + 0.05 * Math.sin(clock * 0.09);
        double swell = 1.0 + 0.55 * Math.max(0.0, coreBoost);
        float size = (float) (3.4 * easeInOut(envelopeGrow) * breathe * swell * rigScale);
        place(envelope.get(0), mid, centered(new Quaternionf().rotateY(spin * 0.25f).rotateX(0.35f), size), 2);
        place(envelope.get(1), mid, centered(new Quaternionf().rotateY(-spin * 0.18f + 0.785f).rotateZ(0.5f), size * 0.93f), 2);
        if (clock % 60 == 0) {
            entity.getWorld().playSound(mid, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 0.45f);
        }
    }

    /** Crush the photosphere to a point on the core in three ticks, then let it go. */
    private void implodeEnvelope(Location core) {
        envelopeGoal = 0.0;
        envelopeGrow = 0.0;
        for (BlockDisplay hull : envelope) {
            place(hull, core, centered(new Quaternionf(), 0.001f), 3);
        }
        envelopeDie = envelope.isEmpty() ? 0 : 4;
    }

    private Location idleMaceGrip(LivingEntity entity) {
        Vector f = fwd();
        return entity.getLocation().clone().add(right(f).multiply(1.05)).add(f.clone().multiply(0.15))
                .add(0, entity.getHeight() * 0.42 + Math.sin(clock * 0.1) * 0.08, 0);
    }

    private Vector idleMaceDir() {
        return fwd().multiply(0.25).add(new Vector(Math.sin(clock * 0.05) * 0.08, -1, 0)).normalize();
    }

    private Location corePos(LivingEntity entity) {
        if (coreOverride != null) {
            return coreOverride.clone();
        }
        return entity.getLocation().clone().add(0, entity.getHeight() * 0.6, 0);
    }

    private void clearRig() {
        discardRig(coreA);
        discardRig(coreB);
        discardRig(shell);
        discardRig(maceShaft);
        discardRig(maceHead);
        discardRig(maceStar);
        coreA = null;
        coreB = null;
        shell = null;
        maceShaft = null;
        maceHead = null;
        maceStar = null;
        for (BlockDisplay ray : crown) {
            discardRig(ray);
        }
        crown.clear();
        for (BlockDisplay leak : leaks) {
            discardRig(leak);
        }
        leaks.clear();
        for (BlockDisplay hull : envelope) {
            discardRig(hull);
        }
        envelope.clear();
        envelopeGrow = 0.0;
        envelopeDie = 0;
    }

    private void refreshSilhouette(LivingEntity entity, Form form) {
        if (silhouette == form) {
            return;
        }
        silhouette = form;
        visualForm = form;
        dress(entity, form);
        applyRigMaterials(form);
    }

    private void dress(LivingEntity entity, Form form) {
        for (int slot = 0; slot < 4; slot++) {
            dressPiece(entity, slot, form);
        }
        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            eq.setItemInMainHand(null);
            eq.setItemInOffHand(null);
            eq.setItemInMainHandDropChance(0.0f);
            eq.setItemInOffHandDropChance(0.0f);
        }
    }

    /** 0 helmet, 1 chest, 2 legs, 3 boots. Trim color is the phase tell: gold, redstone, diamond. */
    private void dressPiece(LivingEntity entity, int slot, Form form) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) {
            return;
        }
        TrimMaterial metal = switch (form) {
            case MAIN -> TrimMaterial.GOLD;
            case GIANT -> TrimMaterial.REDSTONE;
            case COLLAPSE -> TrimMaterial.DIAMOND;
        };
        switch (slot) {
            case 0 -> {
                eq.setHelmet(trimmed(Material.NETHERITE_HELMET, metal, TrimPattern.EYE));
                eq.setHelmetDropChance(0.0f);
            }
            case 1 -> {
                eq.setChestplate(trimmed(Material.NETHERITE_CHESTPLATE, metal, TrimPattern.RIB));
                eq.setChestplateDropChance(0.0f);
            }
            case 2 -> {
                eq.setLeggings(trimmed(Material.NETHERITE_LEGGINGS, metal, TrimPattern.SILENCE));
                eq.setLeggingsDropChance(0.0f);
            }
            default -> {
                eq.setBoots(trimmed(Material.NETHERITE_BOOTS, metal, TrimPattern.SPIRE));
                eq.setBootsDropChance(0.0f);
            }
        }
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

    private void keepHidden(LivingEntity entity, boolean force) {
        if (!force && clock % 100 != 0) {
            return;
        }
        if (force || !entity.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
    }

    private void restoreScale(LivingEntity entity) {
        if (foldBase > 0) {
            AttributeUtil.setBase(entity, AttributeUtil.scale(), foldBase);
        }
        rigScale = 1.0;
        folded = false;
    }

    private void ambient(LivingEntity entity, Form form) {
        World world = entity.getWorld();
        Location core = corePos(entity);
        switch (form) {
            case MAIN -> {
                if (clock % 5 == 0) {
                    world.spawnParticle(Particle.SMALL_FLAME, core, 1, 0.3, 0.3, 0.3, 0.005);
                }
                if (clock % 13 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, core, 2, 0.25, 0.25, 0.25, 0.04);
                }
            }
            case GIANT -> {
                if (clock % 5 == 0) {
                    world.spawnParticle(Particle.DRIPPING_LAVA, core.clone().subtract(0, 0.5, 0), 1, 0.4, 0.1, 0.4, 0);
                }
                // The floor under a red giant boils: convection cells flare and fade around him.
                if (clock % 9 == 0) {
                    boilFloor(entity.getLocation());
                }
            }
            case COLLAPSE -> {
                if (clock % 60 == 0) {
                    world.playSound(core, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.5f);
                }
            }
        }
        if (exposedTicks > 0 && clock % 6 == 0) {
            world.spawnParticle(Particle.DUST, core, 2, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(OPENING, 1.0f));
        }
    }

    private void boilFloor(Location feet) {
        if (center == null) {
            return;
        }
        List<Player> viewers = viewers(center);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = 1.5 + random.nextDouble(4.5);
            Location cell = feet.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            paintSurface(cell, i == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK, 10 + random.nextInt(12), viewers);
        }
    }

    // ------------------------------------------------------------------ scorched floor (client-side only)

    private void crater(Location center, double radius, Material hot, Material rim, int life) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        List<Player> viewers = viewers(center);
        int r = (int) Math.ceil(radius);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                if (d > radius * 0.6 && random.nextInt(3) == 0) {
                    continue;
                }
                Material m = d <= radius * 0.5 ? hot : rim;
                paintSurface(center.clone().add(dx, 0, dz), m, life + random.nextInt(20), viewers);
            }
        }
    }

    /** Starfall crater: molten heart, crying-obsidian cracks radiating out, and it cools from the rim inward. */
    private void starCrater(double radius) {
        World world = center.getWorld();
        List<Player> viewers = viewers(center);
        int r = (int) Math.ceil(radius);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double[] rays = new double[7];
        for (int i = 0; i < rays.length; i++) {
            rays[i] = i * Math.PI * 2 / rays.length + random.nextDouble(-0.25, 0.25);
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                double a = Math.atan2(dz, dx);
                boolean crack = false;
                for (double ray : rays) {
                    if (Math.abs(wrap(a - ray)) * Math.max(1.0, d) < 0.7) {
                        crack = true;
                        break;
                    }
                }
                Material m;
                if (d < 2.0) {
                    m = Material.MAGMA_BLOCK;
                } else if (crack) {
                    m = d < radius * 0.6 ? Material.BLACK_CONCRETE : Material.MAGMA_BLOCK;
                } else {
                    int roll = random.nextInt(100);
                    if (roll < 55) {
                        m = Material.BLACKSTONE;
                    } else if (roll < 75) {
                        m = Material.BASALT;
                    } else {
                        continue;
                    }
                }
                int life = 110 + (int) ((1.0 - d / radius) * 70);
                paintSurface(center.clone().add(dx, 0, dz), m, life, viewers);
            }
        }
        if (world != null) {
            world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.5f);
        }
    }

    private void paintSurface(Location at, Material material, int life, List<Player> viewers) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Double floor = floorAt(world, at.getX(), at.getZ(), at.getY() + 0.5);
        if (floor == null) {
            return;
        }
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int by = (int) Math.ceil(floor - 1.0E-3) - 1;
        Block block = world.getBlockAt(bx, by, bz);
        if (!block.getType().isOccluding() || block.getType() == Material.BEDROCK || accretion.owns(block.getLocation())) {
            return;
        }
        long key = (((long) bx & 0x3FFFFFFL) << 38) | (((long) bz & 0x3FFFFFFL) << 12) | ((long) by & 0xFFFL);
        Scorch scorch = scorches.get(key);
        if (scorch == null) {
            scorch = new Scorch(block.getLocation(), clock + life);
            scorches.put(key, scorch);
        } else {
            scorch.revertAt = Math.max(scorch.revertAt, clock + life);
        }
        BlockData data = material.createBlockData();
        for (Player player : viewers) {
            player.sendBlockChange(scorch.loc, data);
        }
    }

    private void tickScorches() {
        if (scorches.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, Scorch>> it = scorches.entrySet().iterator();
        List<Player> viewers = null;
        while (it.hasNext()) {
            Scorch scorch = it.next().getValue();
            if (scorch.revertAt > clock) {
                continue;
            }
            if (viewers == null) {
                viewers = viewers(scorch.loc);
            }
            if (!accretion.owns(scorch.loc)) {
                BlockData real = scorch.loc.getBlock().getBlockData();
                for (Player player : viewers) {
                    player.sendBlockChange(scorch.loc, real);
                }
            }
            it.remove();
        }
    }

    private void revertAllScorch() {
        for (Scorch scorch : scorches.values()) {
            World world = scorch.loc.getWorld();
            if (world == null || accretion.owns(scorch.loc)) {
                continue;
            }
            BlockData real = scorch.loc.getBlock().getBlockData();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(scorch.loc) <= 128 * 128) {
                    player.sendBlockChange(scorch.loc, real);
                }
            }
        }
        scorches.clear();
    }

    private List<Player> viewers(Location at) {
        List<Player> out = new ArrayList<>();
        if (at == null || at.getWorld() == null) {
            return out;
        }
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 96 * 96) {
                out.add(player);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ tells & fx

    private void fuse(Location at, int remaining) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        if (remaining == 12 || remaining == 8 || remaining == 5 || remaining == 3 || remaining == 1) {
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.85f, remaining <= 3 ? 1.9f : 1.4f);
        }
    }

    private static void ringForce(World world, Location center, double radius, Color color, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(10, (int) Math.ceil(Math.PI * 2 * radius / 0.4));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust, true);
        }
    }

    /** White-hot core with a colored sheath: the look of anything moving at star speed. */
    private static void streak(World world, Location a, Location b, Color color, boolean force) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) {
            return;
        }
        Particle.DustOptions core = new Particle.DustOptions(HOT, 0.8f);
        Particle.DustOptions sheath = new Particle.DustOptions(color, 1.4f);
        double len = a.distance(b);
        int index = 0;
        for (double d = 0.0; d <= len; d += 0.3, index++) {
            Location p = lerp(a, b, len < 0.01 ? 0 : d / len);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, core, force);
            if (index % 2 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0.08, 0.08, 0.08, 0, sheath, force);
            }
        }
    }

    private static void burst(World world, Location at, Particle particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit();
            world.spawnParticle(particle, at, 0, dir.getX(), dir.getY(), dir.getZ(), speed, null, true);
        }
    }

    private static void converge(World world, Location at, double radius, Particle particle, int count) {
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit();
            Location from = at.clone().add(dir.clone().multiply(radius));
            world.spawnParticle(particle, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), radius * 0.09);
        }
    }

    private static void floorBurst(World world, Location at, int count, double spread) {
        BlockData data = floorData(at);
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), count, spread, 0.3, spread, 0.2, data);
    }

    private static BlockData floorData(Location at) {
        Block below = at.clone().subtract(0, 0.5, 0).getBlock();
        Material type = below.getType();
        return type.isSolid() ? below.getBlockData() : Material.BLACKSTONE.createBlockData();
    }

    private void startWhoosh(Location at, float volume, float pitch) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Player player : viewers(at)) {
            player.playSound(at, Sound.ITEM_ELYTRA_FLYING, volume, pitch);
            whooshHeard.add(player.getUniqueId());
        }
    }

    private void stopWhoosh() {
        for (UUID id : whooshHeard) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.stopSound(Sound.ITEM_ELYTRA_FLYING);
            }
        }
        whooshHeard.clear();
    }

    // ------------------------------------------------------------------ displays

    private BlockDisplay spawnBlock(Location at, BlockData data, Color glow, int teleportTicks) {
        BlockDisplay display = at.getWorld().spawn(flat(at), BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(teleportTicks);
            spawned.setInterpolationDuration(2);
            spawned.setTransformation(centered(new Quaternionf(), 0.001f));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setViewRange(2.0f);
            spawned.setShadowRadius(0.0f);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            if (glow != null) {
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glow);
            }
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
        fx.add(display);
        return display;
    }

    private BlockDisplay spawnRigBlock(Location at, BlockData data) {
        return at.getWorld().spawn(flat(at), BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(2);
            spawned.setInterpolationDuration(2);
            spawned.setTransformation(centered(new Quaternionf(), 0.001f));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setViewRange(2.0f);
            spawned.setShadowRadius(0.0f);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
    }

    private static void place(BlockDisplay display, Location at, Transformation transformation, int ticks) {
        if (display == null || !display.isValid() || at == null) {
            return;
        }
        display.teleport(flat(at));
        ease(display, transformation, ticks);
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void discard(Display display) {
        if (display == null) {
            return;
        }
        if (display.isValid()) {
            display.remove();
        }
        fx.remove(display);
    }

    private static void discardRig(Display display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void clearCombatFx() {
        LivingEntity entity = instance.getEntity();
        cancelMove(entity);
        for (Display display : fx) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        fx.clear();
        meteors.clear();
        arcs.clear();
        lances.clear();
        waves.clear();
        afterglows.clear();
        shards.clear();
        windupLances.clear();
        flareBeams.clear();
        flareEdges.clear();
        well = null;
        props.clear();
        tells.clear();
        lanceSeek = null;
        foldGlyph = null;
        openingRing = null;
        novaRing = null;
        starfallTail = null;
        hold = 0;
        hitGate.clear();
        coreBoost = 0.0;
        coreOverride = null;
        deathNovaShells.clear();
        deathNovaRings.clear();
        deathNovaRays.clear();
        deathNovaRayReach.clear();
        deathNovaEjecta.clear();
        deathNovaBeams[0] = null;
        deathNovaBeams[1] = null;
        deathNovaSeed = null;
        deathNovaFocus = null;
    }

    private static Transformation centered(Quaternionf rotation, float size) {
        Vector3f offset = rotation.transform(new Vector3f(-size / 2f, -size / 2f, -size / 2f));
        return new Transformation(offset, new Quaternionf(rotation), new Vector3f(size, size, size), new Quaternionf());
    }

    /** A block stretched along {@code dir}, starting {@code start} blocks from the anchor. */
    private static Transformation rod(Vector3f dir, float start, float length, float width) {
        Vector3f axis = new Vector3f(dir);
        if (axis.lengthSquared() < 1.0E-6f) {
            axis.set(0, 1, 0);
        }
        axis.normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
        Vector3f offset = rotation.transform(new Vector3f(-width / 2f, start, -width / 2f));
        return new Transformation(offset, rotation, new Vector3f(width, length, width), new Quaternionf());
    }

    // ------------------------------------------------------------------ helpers

    private boolean beat(int t, int at) {
        if (t < at || at < beatFloor) {
            return false;
        }
        return beats.add(cinematic.name() + at);
    }

    /** Authored cinematic ticks to real ticks for props that animate on their own clock. */
    private int real(int authored) {
        return Math.max(1, (int) Math.round(authored * transitionScale));
    }

    private static int scaled(int tick, int duration, int base) {
        return (int) Math.round(tick * (double) base / Math.max(1, duration));
    }

    private boolean gate(String kind, Player player, int ticks) {
        String key = kind + player.getUniqueId();
        Long next = hitGate.get(key);
        if (next != null && next > clock) {
            return false;
        }
        hitGate.put(key, clock + ticks);
        return true;
    }

    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return out;
        }
        double r = arenaRadius + 16.0;
        double r2 = r * r;
        for (Player player : center.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            Location at = player.getLocation();
            if (Math.abs(at.getY() - floorY) > 26.0 || horizontalSq(at, center) > r2) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    private static Player nearest(Location from, List<Player> players) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : players) {
            double d = horizontalSq(player.getLocation(), from);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private static Player farthest(Location from, List<Player> players, double cap) {
        Player best = null;
        double bestDist = -1;
        double cap2 = cap * cap;
        for (Player player : players) {
            double d = horizontalSq(player.getLocation(), from);
            if (d <= cap2 && d > bestDist) {
                bestDist = d;
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

    private void shout(String message) {
        Location origin = center != null ? center : instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) < 72 * 72) {
                player.sendMessage(TextUtil.component(message));
            }
        }
    }

    private void titleNear(String main, String sub) {
        Location origin = center != null ? center : instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        Title title = Title.title(TextUtil.component(main), TextUtil.component(sub), titleTimes());
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= 56 * 56) {
                player.showTitle(title);
            }
        }
    }

    private void actionBar(String message) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        double r = arenaRadius + 18.0;
        for (Player player : center.getWorld().getPlayers()) {
            if (horizontalSq(player.getLocation(), center) <= r * r) {
                player.sendActionBar(TextUtil.component(message));
            }
        }
    }

    private static Title.Times titleTimes() {
        return Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(1100), Duration.ofMillis(300));
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

    private void teleportBody(LivingEntity entity, Location to) {
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(to);
            }
        });
    }

    private Vector fwd() {
        double rad = Math.toRadians(bodyYaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vector right(Vector f) {
        return new Vector(-f.getZ(), 0, f.getX());
    }

    private static Vector flatDir(Location from, Location to, Vector fallback) {
        Vector dir = to.toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            return fallback.clone().setY(0).normalize();
        }
        return dir.normalize();
    }

    private static Vector steer(Vector current, Vector wanted, double rate) {
        Vector out = current.clone().multiply(1.0 - rate).add(wanted.clone().multiply(rate));
        if (out.lengthSquared() < 1.0E-4) {
            return wanted.clone();
        }
        return out.normalize();
    }

    private static Vector blend(Vector a, Vector b, double t) {
        Vector out = a.clone().multiply(1.0 - t).add(b.clone().multiply(t));
        if (out.lengthSquared() < 1.0E-4) {
            return b.clone().normalize();
        }
        return out.normalize();
    }

    private static Vector rotateY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }

    private static Vector randomUnit() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double z = random.nextDouble(-1.0, 1.0);
        double a = random.nextDouble(Math.PI * 2);
        double r = Math.sqrt(1.0 - z * z);
        return new Vector(r * Math.cos(a), z, r * Math.sin(a));
    }

    private static boolean inCone(Location origin, Vector dir, Location point, double radius, double halfDeg) {
        Vector to = point.toVector().subtract(origin.toVector()).setY(0);
        double len = to.length();
        if (len > radius) {
            return false;
        }
        if (len < 0.8) {
            return true;
        }
        return to.multiply(1.0 / len).dot(dir) >= Math.cos(Math.toRadians(halfDeg));
    }

    private static boolean angleWithin(double angle, double from, double to, double pad) {
        double d = wrap(angle - from);
        double sweep = to - from;
        if (sweep >= 0) {
            return d >= -pad && d <= sweep + pad;
        }
        return d <= pad && d >= sweep - pad;
    }

    private static double wrap(double radians) {
        double r = radians % (Math.PI * 2);
        if (r > Math.PI) {
            r -= Math.PI * 2;
        } else if (r <= -Math.PI) {
            r += Math.PI * 2;
        }
        return r;
    }

    private static float wrapDeg(float deg) {
        float d = deg % 360.0f;
        if (d > 180.0f) {
            d -= 360.0f;
        } else if (d <= -180.0f) {
            d += 360.0f;
        }
        return d;
    }

    private static float yawOf(Vector dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    private static double segmentDistance(Location p, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector());
        Vector ap = p.toVector().subtract(a.toVector());
        double len2 = ab.lengthSquared();
        double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ap.dot(ab) / len2));
        return a.toVector().add(ab.multiply(t)).distance(p.toVector());
    }

    private void clampToArena(Location loc, double margin) {
        if (center == null) {
            return;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double max = Math.max(1.0, arenaRadius - margin);
        if (d > max && d > 0.01) {
            loc.setX(center.getX() + dx / d * max);
            loc.setZ(center.getZ() + dz / d * max);
        }
    }

    private Location ground(Location loc) {
        Location at = loc.clone();
        Double floor = floorAt(at.getWorld(), at.getX(), at.getZ(), at.getY() + 0.5);
        at.setY(floor == null ? floorY : floor);
        at.setPitch(0);
        return at;
    }

    /** Standing surface near {@code nearY}, or null over lava, void, or when the column is walled. */
    private static Double floorAt(World world, double x, double z, double nearY) {
        if (world == null) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = (int) Math.floor(nearY) + 2;
        int min = Math.max(world.getMinHeight() + 1, start - 6);
        for (int y = start; y >= min; y--) {
            Block feet = world.getBlockAt(bx, y, bz);
            if (feet.isLiquid()) {
                continue;
            }
            Block below = world.getBlockAt(bx, y - 1, bz);
            if (!below.getType().isSolid() || below.isLiquid()) {
                continue;
            }
            if (!feet.isPassable() || !world.getBlockAt(bx, y + 1, bz).isPassable()) {
                continue;
            }
            double top = below.getBoundingBox().getMaxY();
            return top > y - 1 && top <= y ? top : (double) y;
        }
        return null;
    }

    private static Location flat(Location at) {
        Location out = at.clone();
        out.setYaw(0.0f);
        out.setPitch(0.0f);
        return out;
    }

    private static Location lerp(Location a, Location b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return a.clone().add((b.getX() - a.getX()) * t, (b.getY() - a.getY()) * t, (b.getZ() - a.getZ()) * t);
    }

    private static double horizontal(Location a, Location b) {
        return Math.sqrt(horizontalSq(a, b));
    }

    private static double horizontalSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static double approach(double from, double to, double step) {
        if (Math.abs(to - from) <= step) {
            return to;
        }
        return from + Math.signum(to - from) * step;
    }

    private static double easeInOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double easeOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    private static double easeOutQuart(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        double inv = 1.0 - t;
        return 1.0 - inv * inv * inv * inv;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    // ------------------------------------------------------------------ hazard records

    private static final class Meteor {
        private final int delay;
        private final int fall;
        private final boolean dark;
        private final boolean scatter;
        private final int index;
        private final double radius = 2.8;
        private boolean locked;
        private int age;
        private Location ground;
        private Location sky;
        private Location prev;
        private BlockDisplay rock;
        private BlockDisplay crust;
        private BlockDisplay tail;
        private HollowProps.Glyph glyph;

        private Meteor(int delay, int fall, boolean dark, boolean scatter, int index) {
            this.delay = delay;
            this.fall = fall;
            this.dark = dark;
            this.scatter = scatter;
            this.index = index;
        }
    }

    private static final class Arc {
        private final int delay;
        private final int index;
        private final int tell = 26;
        private final int flight = 20;
        private final double radius = 2.3;
        private boolean locked;
        private int age;
        private Location from;
        private Location to;
        private double apex;
        private BlockDisplay blob;
        private HollowProps.Glyph glyph;
        private HollowProps.Loop loop;

        private Arc(int delay, int index) {
            this.delay = delay;
            this.index = index;
        }
    }

    private static final class Lance {
        private final Location origin;
        private final Vector dir;
        private final double length;
        private final double speed;
        private final BlockDisplay rod;
        private final double power;
        private final Color color;
        private final Set<UUID> struck = new HashSet<>();
        private int delay;
        private double traveled;

        private Lance(Location origin, Vector dir, double length, double speed, int delay, BlockDisplay rod, double power, Color color) {
            this.origin = origin;
            this.dir = dir;
            this.length = length;
            this.speed = speed;
            this.delay = delay;
            this.rod = rod;
            this.power = power;
            this.color = color;
        }
    }

    private static final class Shockwave {
        private final Location center;
        private final double speed;
        private final double maxRadius;
        private final Color color;
        private final double power;
        private final Set<UUID> struck = new HashSet<>();
        private double radius;
        private int delay;
        private boolean shown;
        private int warm;

        private Shockwave(Location center, double radius, double speed, double maxRadius, Color color, double power, int delay) {
            this.center = center;
            this.radius = radius;
            this.speed = speed;
            this.maxRadius = maxRadius;
            this.color = color;
            this.power = power;
            this.delay = delay;
        }
    }

    private static final class Well {
        private final Location center;
        private final List<BlockDisplay> disk = new ArrayList<>();
        private BlockDisplay core;
        private HollowProps.Ring pull;
        private HollowProps.Ring lethal;
        private int age;
        private float spin;

        private Well(Location center) {
            this.center = center;
        }
    }

    private static final class Afterglow {
        private static final int SMOKE = 0;
        private static final int SCORCH = 1;
        private static final int CRACK = 2;
        private static final int VOID_PULL = 3;
        private static final int VOID_MOTE = 4;

        private final int kind;
        private final Location at;
        private final Location to;
        private int life;

        private Afterglow(int kind, Location at, Location to, int life) {
            this.kind = kind;
            this.at = at;
            this.to = to;
            this.life = life;
        }
    }

    private static final class Shard {
        private final BlockDisplay display;
        private final Location pos;
        private final Vector vel;
        private int life;

        private Shard(BlockDisplay display, Location pos, Vector vel, int life) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.life = life;
        }
    }

    private static final class Ejecta {
        private final BlockDisplay display;
        private final Location pos;
        private final Vector vel;
        private final Color color;
        private final float size;
        private int life;

        private Ejecta(BlockDisplay display, Location pos, Vector vel, Color color, float size, int life) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.color = color;
            this.size = size;
            this.life = life;
        }
    }

    private static final class Husk {
        private final ArmorStand stand;
        private final Location pos;
        private final Vector vel;
        private final int slot;
        private final double drop;
        private int age;
        private boolean landed;

        private Husk(ArmorStand stand, Location pos, Vector vel, int slot, double drop) {
            this.stand = stand;
            this.pos = pos;
            this.vel = vel;
            this.slot = slot;
            this.drop = drop;
        }
    }

    private static final class Scorch {
        private final Location loc;
        private long revertAt;

        private Scorch(Location loc, long revertAt) {
            this.loc = loc;
            this.revertAt = revertAt;
        }
    }
}
