package de.aetherion.bossengine.helios.encounter;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.helios.HeliosConfig;
import de.aetherion.bossengine.helios.HeliosModule;
import de.aetherion.bossengine.helios.core.Camera;
import de.aetherion.bossengine.helios.core.DisplayBudget;
import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.SkyControl;
import de.aetherion.bossengine.helios.core.Tempo;
import de.aetherion.bossengine.helios.reward.StarVault;
import de.aetherion.bossengine.helios.reward.StarseedReliquary;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaBuilder;
import de.aetherion.bossengine.helios.world.ArenaLayout;
import de.aetherion.bossengine.helios.world.ArenaSlots;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One group's run through Helios Requiem, in its own slot of the Helios world.
 *
 * <pre>
 *   PREPARING  the slot is cleared and the arena is built from its layout (a few ticks)
 *   HERALD     Act I   (intro, fight, the Herald is pulled into the star)
 *   HELIOS     Act II  (the star tears, Helios; decay, singularity, requiem, supernova, restore)
 *   REWARD     a point of light; a starseed capsule glides down for each participant
 *   CLOSING    everyone is sent home; the slot is cleared and freed
 * </pre>
 *
 * The encounter owns everything the two acts share: the stage, the star, the arena, the score and its
 * tempo, the camera, the sky, the bars, and the people. The acts ({@link ActScript}) own their bodies
 * and attacks. Every exit path (victory, wipe, quit, admin abort, plugin disable) ends in
 * {@link #close}, which sends everyone home and clears the slot; a crash is handled by the journal.
 */
public final class HeliosEncounter {

    public static final String HERALD_ID = "helios_herald";
    public static final String HELIOS_ID = "helios_requiem";

    public enum Act { PREPARING, HERALD, HELIOS, REWARD, CLOSING, CLOSED }

    private final HeliosModule module;
    private final HeliosConfig cfg;
    private final int slot;
    private final World world;
    private final int cx;
    private final int cy;
    private final int cz;
    private final Participants party = new Participants();

    private DisplayBudget budget;
    private HeliosStage stage;
    private Score score;
    private Tempo tempo;
    private Camera camera;
    private SkyControl sky;
    private Arena arena;
    private DyingStar star;
    private final HeliosBars bars = new HeliosBars();
    private StarseedReliquary reliquary;
    private StarVault vault;

    private BossInstance herald;
    private BossInstance helios;
    private ActScript heraldScript;
    private ActScript heliosScript;

    private Act act = Act.PREPARING;
    private int clock;
    private int actClock;
    private boolean cinematic = true;
    private int closeAt = -1;
    private int rewardDeadline = -1;
    private boolean victory;
    private final Map<String, Integer> iframes = new HashMap<>();
    private final Map<UUID, Double> heraldDamage = new HashMap<>();
    private int builtAt = -1;
    private int allDownTicks;

    private HeliosEncounter(HeliosModule module, int slot) {
        this.module = module;
        this.cfg = module.config();
        this.slot = slot;
        this.world = module.world();
        this.cx = slot * cfg.slotSpacing();
        this.cy = cfg.arenaY();
        this.cz = 0;
    }

    /**
     * Opens an instance for {@code players}.
     *
     * @return the encounter, or empty when no slot is free
     */
    public static Optional<HeliosEncounter> open(HeliosModule module, List<Player> players) {
        List<UUID> ids = new ArrayList<>();
        for (Player p : players) {
            ids.add(p.getUniqueId());
        }
        int slot = module.slots().claim(module.config().maxInstances(), ids);
        if (slot < 0) {
            return Optional.empty();
        }
        HeliosEncounter e = new HeliosEncounter(module, slot);
        for (Player p : players) {
            e.party.add(p);
        }
        e.prepare();
        return Optional.of(e);
    }

    /* ================================================================== accessors */

    public int slot() {
        return slot;
    }

    public HeliosConfig config() {
        return cfg;
    }

    public HeliosModule module() {
        return module;
    }

    public HeliosStage stage() {
        return stage;
    }

    public Score score() {
        return score;
    }

    public Tempo tempo() {
        return tempo;
    }

    public Camera camera() {
        return camera;
    }

    public SkyControl sky() {
        return sky;
    }

    public Arena arena() {
        return arena;
    }

    public DyingStar star() {
        return star;
    }

    public HeliosBars bars() {
        return bars;
    }

    /** The reliquary's vault (born out of the dying star during the supernova), or null. */
    public StarVault vault() {
        return vault;
    }

    /** The supernova gives birth to the vault at {@code at}; it then descends and builds its stairs itself. */
    public StarVault birthVault(Vector3f at) {
        if (act == Act.CLOSED || stage == null) {
            return null;
        }
        if (vault == null) {
            vault = new StarVault(this);
        }
        vault.birth(at);
        return vault;
    }

    public Participants party() {
        return party;
    }

    public Act act() {
        return act;
    }

    public boolean closed() {
        return act == Act.CLOSED;
    }

    public boolean cinematic() {
        return cinematic;
    }

    public void cinematic(boolean on) {
        this.cinematic = on;
    }

    public World world() {
        return world;
    }

    public Location center() {
        return new Location(world, cx + 0.5, cy, cz + 0.5);
    }

    /** Is this location inside this instance's slot box? */
    public boolean contains(Location l) {
        return l != null && l.getWorld() == world
                && Math.abs(l.getX() - (cx + 0.5)) <= ArenaLayout.BOX_R + 24
                && Math.abs(l.getZ() - (cz + 0.5)) <= ArenaLayout.BOX_R + 24;
    }

    public ActScript heraldScript() {
        return heraldScript;
    }

    public ActScript heliosScript() {
        return heliosScript;
    }

    /** The act currently on stage. */
    public ActScript currentScript() {
        return act == Act.HELIOS ? heliosScript : heraldScript;
    }

    public BossInstance currentBoss() {
        return act == Act.HELIOS ? helios : herald;
    }

    /** Participants who can be hit right now. */
    public List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        if (cinematic) {
            return out;
        }
        for (Player p : party.alive()) {
            GameMode m = p.getGameMode();
            if ((m == GameMode.SURVIVAL || m == GameMode.ADVENTURE) && p.getWorld() == world) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everyone who watches (living and echoes). */
    public List<Player> audience() {
        List<Player> out = new ArrayList<>();
        for (Player p : party.present()) {
            if (p.getWorld() == world) {
                out.add(p);
            }
        }
        return out;
    }

    /* ================================================================== lifecycle */

    private void prepare() {
        for (Player p : party.present()) {
            p.sendMessage(TextUtil.component("&6✦ &eThe dying star calls. &7The arena is forming…"));
        }
        ArenaBuilder builder = module.builder();
        builder.clear(world, cx, cy, cz, null);
        builder.build(world, cx, cy, cz, ArenaLayout.cells(), false, this::onBuilt);
    }

    private void onBuilt() {
        if (act != Act.PREPARING) {
            return;
        }
        budget = new DisplayBudget(cfg.displayCap(), cfg.spawnsPerTick(), cfg.quality(), cfg.degradeMspt());
        stage = new HeliosStage(center(), module.keys(), UUID.randomUUID(), budget).audience(this::audience, this::fighters);
        tempo = new Tempo(cfg.bpm("herald", 90));
        score = new Score(stage, cfg.soundOverrides(), cfg.masterVolume());
        camera = new Camera(module.plugin(), stage);
        camera.flashGlyph(cfg.s("resourcepack.flash-glyph", ""));
        sky = new SkyControl(stage);
        arena = new Arena(world, cx, cy, cz, stage);
        star = new DyingStar(stage, tempo);
        star.scaleNow(0f);
        builtAt = clock;
        arrive();
    }

    private void arrive() {
        List<Player> present = party.present();
        if (present.isEmpty()) {
            close(false, "empty");
            return;
        }
        module.slots().set(slot, ArenaSlots.State.ACTIVE);
        Vector3f spawn = Arena.spawnPoint();
        int i = 0;
        for (Player p : present) {
            float a = HMath.HALF_PI + (i - (present.size() - 1) * 0.5f) * 0.09f;
            Vector3f at = HMath.ring(27.5f, a, 0f);
            float yaw = (float) Math.toDegrees(HMath.yawToward(-at.x, -at.z));
            Location to = stage.at(at.x, 0.05f, at.z);
            to.setYaw(yaw);
            to.setPitch(-8f);
            camera.blackout(p, 60);
            module.teleport(p, to);
            hushHud(p);
            p.setFallDistance(0f);
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                // Staff testers keep their mode; everyone else fights in survival.
            } else {
                p.setGameMode(GameMode.SURVIVAL);
            }
            p.setAllowFlight(false);
            p.setFlying(false);
            Participants.Member m = party.get(p);
            if (m != null) {
                m.lastGround.set(at.x, 0f, at.z);
            }
            i++;
        }
        sky.now(SkyControl.DUSK);
        act = Act.HERALD;
        actClock = 0;
        cinematic = true;
        spawnHerald();
    }

    private void spawnHerald() {
        Location at = stage.at(new Vector3f(0f, 0f, -12f));
        Player leader = party.present().isEmpty() ? null : party.present().get(0);
        Optional<BossInstance> spawned = BossEngine.getInstance().getBossManager()
                .spawn(HERALD_ID, at, SpawnCause.API, leader, "helios_" + slot + "_herald", null);
        if (spawned.isEmpty()) {
            module.plugin().getLogger().warning("[Helios] Could not spawn " + HERALD_ID + " (template missing?). Aborting slot " + slot);
            close(false, "spawn");
            return;
        }
        herald = spawned.get();
        scale(herald);
    }

    private void scale(BossInstance boss) {
        int players = Math.min(cfg.maxScaledPlayers(), Math.max(1, party.count()));
        int extra = players - 1;
        boss.applyCustomScale(players, 1.0 + cfg.healthPerExtra() * extra, 1.0 + cfg.damagePerExtra() * extra);
    }

    /** Called by the script right after it bound to its instance. */
    void attach(ActScript script) {
        String id = script.instance().getTemplate().getId();
        if (HERALD_ID.equalsIgnoreCase(id)) {
            heraldScript = script;
        } else if (HELIOS_ID.equalsIgnoreCase(id)) {
            heliosScript = script;
        }
    }

    /** The Herald's death cinematic is over: the star tears and Helios comes. */
    public void heraldFallen() {
        if (act != Act.HERALD) {
            return;
        }
        if (herald != null) {
            heraldDamage.putAll(herald.getDamageTracker().snapshot());
        }
        act = Act.HELIOS;
        actClock = 0;
        cinematic = true;
        Location at = stage.at(DyingStar.CENTER);
        Player leader = party.present().isEmpty() ? null : party.present().get(0);
        Optional<BossInstance> spawned = BossEngine.getInstance().getBossManager()
                .spawn(HELIOS_ID, at, SpawnCause.API, leader, "helios_" + slot + "_helios", null);
        if (spawned.isEmpty()) {
            module.plugin().getLogger().warning("[Helios] Could not spawn " + HELIOS_ID + ". Aborting slot " + slot);
            close(false, "spawn");
            return;
        }
        helios = spawned.get();
        scale(helios);
        // Act I counts toward Act II's loot share, so the Herald fight matters for the reward.
        double ratio = helios.getCombatMaxHealth() / Math.max(1.0, herald == null ? 1.0 : herald.getCombatMaxHealth());
        heraldDamage.forEach((id, dmg) -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                helios.getDamageTracker().add(p, dmg * ratio * 0.3);
            }
        });
    }

    /** Helios's death cinematic (including the restore) is over. */
    public void heliosFallen() {
        if (act != Act.HELIOS) {
            return;
        }
        victory = true;
        act = Act.REWARD;
        actClock = 0;
        cinematic = true;
        bars.visible(false);
        rewardDeadline = clock + 40;
        // Echoes come back for the reward: everyone who fought walks up to their own capsule.
        for (Participants.Member m : party.all()) {
            if (m.gone || !m.echo) {
                continue;
            }
            m.echo = false;
            Player p = Bukkit.getPlayer(m.id);
            if (p != null && p.isOnline()) {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
                Location to = stage.at(HMath.ring(16.5f, HMath.HALF_PI, 0.1f));
                to.setYaw(180f);
                module.teleport(p, to);
            }
        }
    }

    /** Loot bundles from the engine (XP and the recap are already paid). */
    public boolean reward(Map<UUID, List<ItemStack>> bundles) {
        if (act != Act.REWARD && act != Act.HELIOS) {
            return false;
        }
        if (reliquary != null) {
            return true;
        }
        reliquary = new StarseedReliquary(this, bundles, cfg.claimSeconds() * 20);
        return true;
    }

    /** A right-click on something in the arena (the reliquary's capsules). */
    public boolean claim(Player p, org.bukkit.entity.Entity clicked) {
        return reliquary != null && reliquary.click(p, clicked);
    }

    /* ================================================================== tick */

    public void tick() {
        clock++;
        if (act == Act.PREPARING) {
            if (clock % 20 == 0) {
                for (Player p : party.present()) {
                    p.sendActionBar(TextUtil.component("&7The arena is forming…"));
                }
            }
            if (clock > 20 * 60) {
                close(false, "build-timeout");
            }
            return;
        }
        if (act == Act.CLOSED) {
            return;
        }
        actClock++;
        budget.beginTick();
        try {
            tempo.tick();
            star.tick();
            score.tick();
            sky.tick();
            camera.tick();
            arena.tick();
            guardPlayers();
            tickBars();
            if (vault != null) {
                vault.tick();
            }
            if (reliquary != null) {
                reliquary.tick();
                if (reliquary.finished() && closeAt < 0) {
                    beginClosing(true);
                }
            } else if (act == Act.REWARD && rewardDeadline >= 0 && clock > rewardDeadline) {
                beginClosing(true);
            }
            if ((act == Act.HERALD || act == Act.HELIOS) && !victory && builtAt >= 0 && clock - builtAt > 60) {
                boolean anyoneStanding = false;
                for (Player p : party.present()) {
                    Participants.Member m = party.get(p);
                    anyoneStanding |= !p.isDead() && m != null && !m.echo;
                }
                allDownTicks = anyoneStanding ? 0 : allDownTicks + 1;
                if (allDownTicks > 20 * 8) {
                    fail();
                }
            }
            if (closeAt >= 0 && clock >= closeAt) {
                close(victory, victory ? "victory" : "wipe");
            }
            if (clock % 40 == 0) {
                stage.purge();
            }
        } finally {
            budget.endTick();
        }
    }

    private void tickBars() {
        BossInstance boss = currentBoss();
        ActScript script = currentScript();
        if (boss == null || script == null || act == Act.REWARD || act == Act.CLOSING) {
            bars.sync(List.of());
            return;
        }
        bars.update(boss.healthPercent(), script.blocksDamage(),
                act == Act.HELIOS ? BossBar.Color.RED : BossBar.Color.YELLOW, sky.darken(), sky.fog());
        bars.sync(audience());
        int limit = act == Act.HELIOS ? cfg.heliosEnrageTicks() : cfg.heraldEnrageTicks();
        int fightTicks = fightClock();
        if (fightTicks > limit - 20 * 60 && act == Act.HELIOS) {
            int left = Math.max(0, limit - fightTicks) / 20;
            bars.movement(left > 0 ? "&c&lSUPERNOVA &7in &f" + left + "s" : "&4&lSUPERNOVA", left / 60f, BossBar.Color.RED);
        }
    }

    /* ================================================================== enrage */

    private int fightStart = -1;

    /** The scripts call this when the act's actual fight begins (after the intro). */
    public void fightStarted() {
        fightStart = clock;
    }

    public int fightClock() {
        return fightStart < 0 ? 0 : clock - fightStart;
    }

    public boolean enraged() {
        int limit = act == Act.HELIOS ? cfg.heliosEnrageTicks() : cfg.heraldEnrageTicks();
        return fightStart >= 0 && fightClock() > limit;
    }

    /** Damage multiplier that grows every 10 s after the enrage timer ran out. */
    public double enrageMultiplier() {
        if (!enraged()) {
            return 1.0;
        }
        int limit = act == Act.HELIOS ? cfg.heliosEnrageTicks() : cfg.heraldEnrageTicks();
        int over = fightClock() - limit;
        return 1.0 + cfg.enrageRamp() * (1 + over / 200);
    }

    /* ================================================================== hits */

    /**
     * Hits a player once per {@code key} per {@code iframes} ticks.
     *
     * @param power BossHits power (already from config)
     * @param knock velocity to add (may be null)
     * @return true if the hit landed
     */
    public boolean hit(Player p, double power, String key, int iframes, Vector knock) {
        if (p == null || cinematic || !party.contains(p) || p.isDead()) {
            return false;
        }
        Participants.Member m = party.get(p);
        if (m == null || m.echo || clock < m.graceUntil) {
            return false;
        }
        GameMode mode = p.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }
        String k = p.getUniqueId() + ":" + key;
        Integer until = iframes == 0 ? null : this.iframes.get(k);
        if (until != null && clock < until) {
            return false;
        }
        this.iframes.put(k, clock + Math.max(1, iframes));
        BossInstance boss = currentBoss();
        double scaled = boss == null ? power : boss.scaleDamage(power);
        scaled *= enrageMultiplier();
        LivingEntity source = boss == null ? null : boss.getEntity();
        BossHits.hurt(p, source, scaled);
        if (knock != null && knock.lengthSquared() > 0.0001) {
            p.setVelocity(p.getVelocity().multiply(0.3).add(tameKnock(p, knock)));
        }
        camera.kick(p);
        return true;
    }

    /**
     * Knockback that keeps people on the platform: scaled and capped by config, and with no horizontal
     * shove at all when it would carry the player over an edge (the floor ahead is gone).
     */
    public Vector tameKnock(Player p, Vector knock) {
        double scale = cfg.d("knockback.scale", 0.4);
        double cap = cfg.d("knockback.max-horizontal", 0.4);
        Vector flat = new Vector(knock.getX(), 0, knock.getZ()).multiply(scale);
        if (flat.length() > cap) {
            flat.normalize().multiply(cap);
        }
        double up = Math.min(knock.getY() * scale + 0.08, cfg.d("knockback.max-vertical", 0.3));
        if (flat.lengthSquared() > 1e-4 && !groundAhead(p, flat)) {
            flat.zero();
        }
        return flat.setY(Math.max(0.0, up));
    }

    /** Is there floor about three blocks along {@code dir} from the player? */
    public boolean groundAhead(Player p, Vector dir) {
        if (arena == null || dir.lengthSquared() < 1e-6) {
            return true;
        }
        Vector3f f = stage.feet(p);
        Vector d = dir.clone().setY(0).normalize();
        for (float step = 1.5f; step <= 3.5f; step += 1f) {
            if (!arena.solidAt(f.x + (float) d.getX() * step, f.z + (float) d.getZ() * step)) {
                return false;
            }
        }
        return true;
    }

    /* ================================================================== guard */

    private void guardPlayers() {
        boolean hush = clock % 20 == 0;
        for (Player p : party.present()) {
            if (hush && p.getWorld() == world) {
                hushHud(p);
            }
            Participants.Member m = party.get(p);
            if (m == null) {
                continue;
            }
            if (p.getWorld() != world) {
                // Left the world by some other means: they are out.
                leave(p, false);
                continue;
            }
            if (m.echo) {
                keepEchoInside(p);
                continue;
            }
            GameMode mode = p.getGameMode();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                if (p.getAllowFlight()) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                }
                if (p.isGliding()) {
                    p.setGliding(false);
                }
            }
            Vector3f feet = stage.feet(p);
            if (((org.bukkit.entity.Entity) p).isOnGround() && feet.y > -1.5f) {
                m.lastGround.set(feet.x, 0f, feet.z);
                m.airTicks = 0;
            } else {
                m.airTicks++;
            }
            if (feet.y < -cfg.killPlane()) {
                fellOff(p, m);
            } else if (Math.abs(feet.x) > ArenaLayout.BOX_R + 12 || Math.abs(feet.z) > ArenaLayout.BOX_R + 12) {
                fellOff(p, m);
            }
        }
    }

    /**
     * The star catches whoever falls: a gold beam reaches down, lifts them above the nearest solid
     * ground and lets them float down onto it (slow falling), with a short grace. It hurts a little,
     * it never chains into another hit.
     */
    private void fellOff(Player p, Participants.Member m) {
        if (cfg.voidMode() == HeliosConfig.VoidMode.DEATH && !cinematic) {
            p.setHealth(0.0);
            return;
        }
        Vector3f safe = arena.safeSpot(m.lastGround);
        Location to = stage.at(safe.x, 5.5f, safe.z);
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(35f);
        module.teleport(p, to);
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0.15, 0));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 45, 0, false, false, false));
        catchBeam(new Vector3f(safe.x, 5.5f, safe.z));
        score.to(p, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        score.to(p, Sound.ITEM_TRIDENT_RETURN, 1f, 0.8f);
        if (!cinematic) {
            hit(p, cfg.rescuePower(), "void", 20, null);
        }
        grace(p, cfg.rescueGraceTicks());
        p.sendActionBar(TextUtil.component("&6The star catches you."));
    }

    /** A brief gold tether from the star down to a rescued player. */
    private void catchBeam(Vector3f to) {
        HeliosStage.Group g = stage.group();
        de.aetherion.bossengine.helios.core.Shapes.Beam beam = new de.aetherion.bossengine.helios.core.Shapes.Beam(
                stage, g, org.bukkit.Material.WHITE_CONCRETE, org.bukkit.Material.YELLOW_STAINED_GLASS, DyingStar.GOLD);
        Vector3f from = star.center();
        beam.set(from, to, 0.05f, 0);
        beam.set(from, to, 0.5f, 3);
        Bukkit.getScheduler().runTaskLater(module.plugin(), () -> beam.thin(6), 6L);
        Bukkit.getScheduler().runTaskLater(module.plugin(), g::clear, 16L);
    }

    private void keepEchoInside(Player p) {
        Vector3f feet = stage.feet(p);
        if (feet.length() > 52f || feet.y < -20f || feet.y > 50f) {
            module.teleport(p, overlook());
        }
    }

    /** Where echoes appear: high above the Corona, looking down at the star. */
    public Location overlook() {
        Location l = stage.at(0f, 22f, 34f);
        l.setYaw(180f);
        l.setPitch(35f);
        return l;
    }

    /* ================================================================== people */

    /** A participant died in the arena. */
    public void died(Player p) {
        Participants.Member m = party.get(p);
        if (m == null) {
            return;
        }
        m.deaths++;
        if (cfg.echoes()) {
            m.echo = true;
        }
        for (Player o : audience()) {
            o.sendMessage(TextUtil.component("&6✦ &7" + p.getName() + " &8is scattered into light."));
        }
    }

    /**
     * Where a dead participant comes back: on the arena floor, in survival, with a few seconds of grace
     * (or the overlook as an echo, if echoes are enabled). Never out of the instance.
     */
    public Location respawn(Player p) {
        Participants.Member m = party.get(p);
        if (m == null) {
            return null;
        }
        if (m.echo) {
            Bukkit.getScheduler().runTask(module.plugin(), () -> {
                if (p.isOnline() && party.contains(p)) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, TextUtil.component("&7Echo"));
                    p.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, TextUtil.component("&8You watch until the light fades."));
                }
            });
            return overlook();
        }
        Vector3f safe = arena == null ? Arena.spawnPoint() : arena.safeSpot(HMath.ring(24f, HMath.HALF_PI, 0f));
        Location to = stage.at(safe.x, 0.1f, safe.z);
        to.setYaw((float) Math.toDegrees(HMath.yawToward(-safe.x, -safe.z)));
        grace(p, cfg.respawnGraceTicks());
        Bukkit.getScheduler().runTask(module.plugin(), () -> {
            if (p.isOnline() && party.contains(p)) {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE,
                        cfg.respawnGraceTicks(), 4, false, false, false));
                hushHud(p);
                p.sendActionBar(TextUtil.component("&6The star lets you back in."));
            }
        });
        return to;
    }

    /** No hits for {@code ticks} (respawn, rescue). */
    public void grace(Player p, int ticks) {
        Participants.Member m = party.get(p);
        if (m != null) {
            m.graceUntil = Math.max(m.graceUntil, clock + ticks);
        }
    }

    /** Quest boss bar and NPC hints off while inside Helios (same as the World Eater site). */
    static void hushHud(Player p) {
        if (p == null) {
            return;
        }
        de.aetherion.core.api.QuestBars.suppress(p);
        try {
            Class<?> hint = Class.forName("de.aetherion.quests.ui.QuestHint");
            hint.getMethod("clear", Player.class).invoke(null, p);
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            // Quests offline
        }
    }

    /** A participant leaves (command, quit, other world). */
    public void leave(Player p, boolean teleport) {
        Participants.Member m = party.get(p);
        if (m == null || m.gone) {
            return;
        }
        m.gone = true;
        if (camera != null) {
            camera.release(p);
        }
        if (sky != null) {
            sky.release(p);
        }
        bars.hide(p);
        de.aetherion.core.api.QuestBars.unsuppress(p);
        Participants.sendHome(p, m, module.fallbackReturn(), teleport);
        module.slots().players(slot, activeIds());
        if (party.present().isEmpty() && act != Act.CLOSED) {
            close(victory, "empty");
        }
    }

    private List<UUID> activeIds() {
        List<UUID> ids = new ArrayList<>();
        for (Participants.Member m : party.all()) {
            if (!m.gone) {
                ids.add(m.id);
            }
        }
        return ids;
    }

    /* ================================================================== endings */

    private void fail() {
        if (closeAt >= 0) {
            return;
        }
        cinematic = true;
        ActScript s = currentScript();
        if (s != null) {
            s.endAttacks();
        }
        score.silence(30);
        score.play(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f, true);
        score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.5f, true);
        star.scale(1.6f, 0.02f);
        for (Player p : audience()) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                    TextUtil.component("&4The light goes out"),
                    TextUtil.component("&8No one was left to listen.")));
        }
        closeAt = clock + 100;
    }

    private void beginClosing(boolean success) {
        if (closeAt >= 0) {
            return;
        }
        act = Act.CLOSING;
        cinematic = true;
        camera.blackoutAll(50);
        closeAt = clock + 40;
        victory = success;
    }

    /**
     * Ends the instance: bosses gone, everyone home, the slot cleared and freed. Idempotent.
     */
    public void close(boolean success, String reason) {
        if (act == Act.CLOSED) {
            return;
        }
        Act was = act;
        act = Act.CLOSED;
        module.plugin().getLogger().info("[Helios] Slot " + slot + " closed (" + reason + ", was " + was + ").");
        var manager = BossEngine.getInstance() == null ? null : BossEngine.getInstance().getBossManager();
        for (BossInstance boss : new BossInstance[]{herald, helios}) {
            if (boss != null && manager != null && boss.isEncounterActive()) {
                manager.despawn(boss, BossDespawnEvent.Reason.COMMAND, null);
            }
        }
        if (heraldScript != null) {
            heraldScript.abort();
        }
        if (heliosScript != null) {
            heliosScript.abort();
        }
        if (reliquary != null) {
            reliquary.deliverAll();
            reliquary.clear();
        }
        if (vault != null) {
            // Stairs, dais and the floating vault go on every exit (the slot wipe would catch them too).
            vault.clear();
            vault = null;
        }
        bars.hideAll();
        for (Participants.Member m : party.all()) {
            if (m.gone) {
                continue;
            }
            m.gone = true;
            Player p = Bukkit.getPlayer(m.id);
            if (p != null) {
                if (camera != null) {
                    camera.release(p);
                }
                if (sky != null) {
                    sky.release(p);
                }
                de.aetherion.core.api.QuestBars.unsuppress(p);
                Participants.sendHome(p, m, module.fallbackReturn(), true);
                if (success) {
                    module.markCooldown(p);
                }
            }
        }
        if (star != null) {
            star.clear();
        }
        if (arena != null) {
            arena.cracks().clearAll();
        }
        if (stage != null) {
            stage.clear();
        }
        if (camera != null) {
            camera.releaseAll();
        }
        if (sky != null) {
            sky.releaseAll();
        }
        module.slots().set(slot, ArenaSlots.State.RESTORING);
        module.builder().clear(world, cx, cy, cz, () -> {
            ArenaBuilder.release(module.plugin(), world, cx, cz);
            module.slots().set(slot, ArenaSlots.State.FREE);
        });
        module.forget(this);
    }

    /* ================================================================== admin */

    public String debug() {
        StringBuilder sb = new StringBuilder();
        sb.append("&6Slot ").append(slot).append(" &7act &f").append(act)
                .append(" &7clock &f").append(clock).append(" &7cinematic &f").append(cinematic)
                .append(" &7players &f").append(party.alive().size()).append('/').append(party.count());
        if (budget != null) {
            sb.append("\n&7 ").append(budget.report());
            sb.append("\n&7 tracked displays ").append(stage.count())
                    .append(" | cracks ").append(arena.cracks().size())
                    .append(" | bpm ").append(String.format(java.util.Locale.ROOT, "%.0f", tempo.bpm()))
                    .append(" | enrage x").append(String.format(java.util.Locale.ROOT, "%.2f", enrageMultiplier()));
        }
        ActScript s = currentScript();
        if (s != null) {
            sb.append("\n&7 script ").append(s.mode()).append(" | attacks ");
            for (Attack a : s.running()) {
                sb.append(a.id()).append('(').append(a.age()).append(") ");
            }
        }
        return sb.toString();
    }

    /** Admin: jump straight into Act II. */
    public boolean skipToHelios() {
        if (act != Act.HERALD || heraldScript == null || herald == null) {
            return false;
        }
        herald.setCombatHealthPercent(0.2);
        heraldScript.beginDeath();
        return true;
    }

    public void adminHealth(double percent) {
        BossInstance boss = currentBoss();
        if (boss != null) {
            boss.setCombatHealthPercent(percent);
        }
    }
}
