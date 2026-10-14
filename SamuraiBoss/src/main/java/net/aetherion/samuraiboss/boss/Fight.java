package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.api.SamuraiBossDefeatEvent;
import net.aetherion.samuraiboss.api.SamuraiFightEndEvent;
import net.aetherion.samuraiboss.api.SamuraiFightStartEvent;
import net.aetherion.samuraiboss.api.SamuraiPhaseChangeEvent;
import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.arena.BlockSnapshot;
import net.aetherion.samuraiboss.arena.RestoreJob;
import net.aetherion.samuraiboss.arena.destruction.DestructionDirector;
import net.aetherion.samuraiboss.arena.destruction.RebuildJob;
import net.aetherion.samuraiboss.atmosphere.Atmosphere;
import net.aetherion.samuraiboss.audio.Music;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.render.DisplayPool;
import net.aetherion.samuraiboss.render.fx.FxContext;
import net.aetherion.samuraiboss.render.fx.FxManager;
import net.aetherion.samuraiboss.render.fx.MoonRing;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalField;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import de.aetherion.core.api.BarChrome;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Ein laufender Kampf gegen Sakuragi Jinsai in einem Dojo.
 *
 * <p>Der Kampf ist eine Zustandsmaschine:</p>
 * <pre>
 * INTRO → COMBAT ⇄ TRANSITION (嵐, 散華) → ULTIMATE → EXHAUSTED → COMBAT (Ruinen) → DYING → VICTORY → REBUILD → ENDED
 *                                         └──────────── alle weg / tot ───────────→ DEFEAT → REBUILD → ENDED
 * </pre>
 *
 * <p>Lebenspunkte sind virtuell. Schaden kann eine Phasengrenze nie "überspringen": die HP
 * bleiben an der Grenze stehen, bis die Zwischensequenz gelaufen ist.</p>
 *
 * <p>Pro Tick: KI/Zwischensequenz → Boss (Modell & Animation) → Effekte → Zerstörung →
 * Musik/Stimmung → Bossleiste.</p>
 */
public final class Fight {

    public enum State {INTRO, COMBAT, TRANSITION, ULTIMATE, EXHAUSTED, DYING, VICTORY, DEFEAT, REBUILD, ENDED}

    private final SamuraiBossPlugin plugin;
    private final Settings s;
    private final Arena arena;
    private final World world;
    private final DisplayPool pool;
    private final FxManager fxm;
    private final FxContext fx;
    private final Sfx sfx;
    private final Music music;
    private final Atmosphere atmo;
    private final DestructionDirector destruction;
    private final Cinematics cine;
    private SamuraiBoss boss;
    private BossBrain brain;
    private PetalField petals;
    MoonRing moon;
    private final BossBar bar;
    private final Map<UUID, Player> participants = new LinkedHashMap<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<ModelInstance> extraModels = new ArrayList<>();
    private final Map<UUID, Integer> clangAt = new HashMap<>();
    private State state = State.INTRO;
    private Phase phase = Phase.SEI;
    private boolean ghostForm, ruin, ultimateDone, ending;
    private double maxHp, hp;
    private float barShown;
    private float barOverride = -1f;
    private int tick, phaseStart, emptyTicks, exhaustedLeft;
    /** Tick, zu dem nach dem Sieg der Wiederaufbau beginnt (-1 = keiner geplant). */
    private int rebuildAt = -1;
    private Sequence cinematic;
    private final Vector3f zoneMin, zoneMax;
    private List<LivingEntity> targetsCache = Collections.emptyList();
    private int targetsTick = -1;

    public Fight(SamuraiBossPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.s = plugin.settings();
        this.arena = arena;
        this.world = arena.world();
        this.pool = new DisplayPool(arena.id, s.maxEffectDisplays, 160);
        this.fxm = new FxManager(pool, plugin.getLogger(), s.effectDensity);
        this.sfx = new Sfx(s);
        this.fx = new FxContext(fxm, s, sfx, world);
        this.music = new Music(s);
        this.atmo = new Atmosphere(s, arena.center());
        this.destruction = new DestructionDirector(arena, s, sfx);
        this.cine = new Cinematics(this);
        this.zoneMin = arena.plan.point("zone_min");
        this.zoneMax = arena.plan.point("zone_max");
        // Same textured BOSS frame as BossEngine / Helios (pack glyphs via BarChrome).
        this.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_10);
    }

    // =================================================================== Start

    /** Startet den Kampf. @return Fehlermeldung oder null. */
    public String start() {
        if (world == null) return "Die Welt des Dojos ist nicht geladen.";
        arena.state = Arena.State.FIGHT;
        arena.fight = this;
        plugin.arenas().addTickets(arena);
        // 1) Snapshot des ganzen Bereichs – vor allem anderen (Absturzsicherheit)
        try {
            BlockSnapshot snap = BlockSnapshot.capture(world, arena.minX(), arena.minY(), arena.minZ(),
                    arena.maxX(), arena.maxY(), arena.maxZ());
            arena.fightSnapshot = snap;
            snap.save(arena.fightFile());
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Snapshot für " + arena.id + " fehlgeschlagen – Kampf abgebrochen", ex);
            arena.state = Arena.State.IDLE;
            arena.fight = null;
            plugin.arenas().removeTickets(arena);
            return "Snapshot fehlgeschlagen (siehe Konsole).";
        }
        plugin.arenas().save(arena); // fight-active: true
        arena.deco.ensure();

        // 2) Teilnehmer & Lebenspunkte
        updateParticipants();
        int n = Math.max(1, participants.size());
        maxHp = s.health + s.healthPerExtraPlayer * (n - 1);
        hp = maxHp;

        // 3) Boss, Blüten, Leiste
        boss = new SamuraiBoss(this);
        Location spawn = arena.point("boss");
        boss.spawn(spawn, 0f, boss.anims.idleSheathed);
        boss.setInvulnerable(true);
        brain = new BossBrain(this);
        Vector3f tree = arena.plan.point("tree");
        petals = fxm.add(new PetalField(fx, arena.loc(0, 0, 0), s.ambientPetals, zoneMin.x, zoneMax.x, zoneMin.z, zoneMax.z,
                12f, tree.x, tree.z));
        refreshBarName();
        Bukkit.getPluginManager().callEvent(new SamuraiFightStartEvent(arena.id, new ArrayList<>(participants.values())));

        // 4) Einleitung
        if (s.introEnabled) {
            state = State.INTRO;
            barOverride = 0f;
            cinematic = cine.intro();
        } else {
            barOverride = -1f;
            barShown = 1f;
            music.play(Music.SEI);
            atmo.timeTo(participantsList(), 12800, 40);
            beginCombat(20);
        }
        broadcastToArena("fight-start");
        return null;
    }

    /** Wechsel in den normalen Kampf. */
    void beginCombat(int pause) {
        state = State.COMBAT;
        boss.setInvulnerable(false);
        boss.setAutoBase(true);
        barOverride = -1f;
        brain.delay(pause);
    }

    // =================================================================== Zugriff (für Fähigkeiten)

    public SamuraiBossPlugin plugin() {
        return plugin;
    }

    public Settings settings() {
        return s;
    }

    public Arena arena() {
        return arena;
    }

    public World world() {
        return world;
    }

    public Sfx sfx() {
        return sfx;
    }

    public FxContext fx() {
        return fx;
    }

    public SamuraiBoss boss() {
        return boss;
    }

    public BossBrain brain() {
        return brain;
    }

    public Phase phase() {
        return phase;
    }

    public boolean ghostForm() {
        return ghostForm;
    }

    public boolean isRuin() {
        return ruin;
    }

    public boolean isEnding() {
        return ending;
    }

    public State state() {
        return state;
    }

    public Atmosphere atmo() {
        return atmo;
    }

    public Music music() {
        return music;
    }

    public DestructionDirector destruction() {
        return destruction;
    }

    public PetalField petals() {
        return petals;
    }

    public double hp() {
        return hp;
    }

    public double maxHp() {
        return maxHp;
    }

    public int age() {
        return tick;
    }

    public DisplayPool pool() {
        return pool;
    }

    public FxManager fxManager() {
        return fxm;
    }

    /** Alle Teilnehmer (online), auch tote/zuschauende. */
    public Collection<Player> participants() {
        return participantsList();
    }

    private List<Player> participantsList() {
        List<Player> l = new ArrayList<>(participants.size());
        for (Player p : participants.values()) if (p.isOnline()) l.add(p);
        return l;
    }

    public boolean isParticipant(Player p) {
        return participants.containsKey(p.getUniqueId());
    }

    /** Lebende, angreifbare Teilnehmer innerhalb der Mauern. */
    public List<LivingEntity> targets() {
        if (targetsTick == tick) return targetsCache;
        List<LivingEntity> out = new ArrayList<>();
        for (Player p : participants.values()) {
            if (!p.isOnline() || p.isDead() || !p.isValid()) continue;
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR) continue;
            if (s.ignoreCreative && gm == GameMode.CREATIVE) continue;
            if (!arena.insideWalls(p.getLocation())) continue;
            out.add(p);
        }
        targetsCache = out;
        targetsTick = tick;
        return out;
    }

    // =================================================================== Schaden an Spielern

    public void hurt(LivingEntity victim, double dmg, Location from, double knock, double up) {
        hurt(victim, dmg, from, knock, up, true);
    }

    /**
     * Fügt einem Spieler Schaden zu – als Angriff der (unsichtbaren) Boss-Hitbox, damit Rüstung,
     * Verzauberungen und Todesnachrichten ("... wurde von Sakuragi Jinsai erschlagen") stimmen.
     */
    public void hurt(LivingEntity victim, double dmg, Location from, double knock, double up, boolean lethal) {
        if (dmg <= 0 || !victim.isValid() || victim.isDead()) return;
        if (victim instanceof Player p) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
            if (!lethal) dmg = Math.min(dmg, Math.max(0, p.getHealth() + p.getAbsorptionAmount() - 1.0));
            if (dmg <= 0) return;
        }
        Mob src = boss != null ? boss.hitbox() : null;
        if (src != null && src.isValid()) {
            DamageSource ds = DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(src).withDirectEntity(src).build();
            victim.damage(dmg, ds);
        } else {
            victim.damage(dmg);
        }
        if (knock > 0 || up > 0) {
            Vector kb = victim.getLocation().toVector().subtract(from.toVector()).setY(0);
            if (kb.lengthSquared() < 1e-4) kb = MathUtil.dirOfYaw(boss != null ? boss.yaw() : 0f);
            kb.normalize().multiply(knock).setY(up);
            victim.setVelocity(victim.getVelocity().multiply(0.3).add(kb));
        }
    }

    // =================================================================== Schaden am Boss

    /** Ein Spieler hat die Hitbox getroffen (vom Listener, Vanilla-Schaden bereits berechnet). */
    public void onBossHit(Player p, double raw, Location hitPoint) {
        if (ending) return;
        if ((state != State.COMBAT && state != State.EXHAUSTED) || boss == null || boss.invulnerable()) {
            clang(p, hitPoint);
            return;
        }
        if (!participants.containsKey(p.getUniqueId())) join(p);
        double dmg = raw * (1.0 - s.damageReduction);
        if (state == State.EXHAUSTED) dmg *= s.exhaustedDamageMultiplier;
        if (dmg <= 0) return;
        double before = hp;
        hp = Math.max(floorHp(), hp - dmg);
        double dealt = before - hp;
        if (dealt > 0) damage.merge(p.getUniqueId(), dealt, Double::sum);
        boss.hurtFeedback(hitPoint);
        brain.onHit(p);
        if (dealt <= 0) clang(p, hitPoint);
    }

    /** Treffer ohne Wirkung (unverwundbar): Funken & metallisches Klirren. */
    private void clang(Player p, Location at) {
        Integer last = clangAt.get(p.getUniqueId());
        if (last != null && tick - last < 8) return;
        clangAt.put(p.getUniqueId(), tick);
        sfx.play(at, Sfx.Id.CLANG, 0.8f, 1.3f);
        fxm.add(new Shards(fx, at, 3, 0.25f, 0.06f, 8, Palette.LIGHT, Palette.GOLD));
    }

    private double floorHp() {
        return switch (phase) {
            case SEI -> s.arashiAt * maxHp;
            case ARASHI -> s.sangeAt * maxHp;
            case SANGE -> ultimateDone ? 0 : s.ultimateAt * maxHp;
        };
    }

    // =================================================================== Raum

    /** Bodenhöhe (Oberkante des ersten festen Blocks) unter/an einer Stelle. */
    public Location ground(Location l) {
        return groundFrom(l, l.getY() + 0.5);
    }

    private Location groundFrom(Location l, double fromY) {
        int x = l.getBlockX(), z = l.getBlockZ();
        int top = (int) Math.floor(fromY);
        int bottom = arena.oy - 4;
        for (int y = top; y >= bottom; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.isLiquid()) return new Location(world, l.getX(), y + 0.9, l.getZ());
            if (b.isPassable()) continue;
            BoundingBox bb = b.getBoundingBox();
            double h = bb.getVolume() > 0 ? bb.getMaxY() : y + 1;
            if (h > fromY + 0.01) continue; // Decke über dem Startpunkt
            return new Location(world, l.getX(), h, l.getZ());
        }
        return new Location(world, l.getX(), arena.oy, l.getZ());
    }

    /**
     * Endpunkt eines Sprints von 'from' nach 'to': bleibt im Kampfbereich, läuft nicht durch
     * Wände und springt keine Abgründe hinab.
     */
    public Location clampDash(Location from, Location to) {
        double minX = arena.ox + zoneMin.x, maxX = arena.ox + zoneMax.x + 1;
        double minZ = arena.oz + zoneMin.z, maxZ = arena.oz + zoneMax.z + 1;
        double tx = MathUtil.clamp(to.getX(), minX, maxX), tz = MathUtil.clamp(to.getZ(), minZ, maxZ);
        double fx0 = MathUtil.clamp(from.getX(), minX, maxX), fz0 = MathUtil.clamp(from.getZ(), minZ, maxZ);
        double dx = tx - fx0, dz = tz - fz0;
        double len = Math.sqrt(dx * dx + dz * dz);
        Location last = groundFrom(new Location(world, fx0, from.getY(), fz0), from.getY() + 1.2);
        int steps = Math.max(1, (int) Math.ceil(len / 0.4));
        for (int i = 1; i <= steps; i++) {
            double u = i / (double) steps;
            Location p = new Location(world, fx0 + dx * u, last.getY(), fz0 + dz * u);
            Location g = groundFrom(p, last.getY() + 1.2);
            if (g.getY() - last.getY() > 1.1 || last.getY() - g.getY() > 2.6) break;
            if (blocked(g) || isWater(g)) break;
            last = g;
        }
        last.setYaw(to.getYaw());
        return last;
    }

    /** Steht man hier im Wasser (Teich)? Der Kensei meidet das Wasser – sein Modell würde versinken. */
    public boolean isWater(Location g) {
        Block below = world.getBlockAt(g.getBlockX(), (int) Math.floor(g.getY() - 0.2), g.getBlockZ());
        Block at = world.getBlockAt(g.getBlockX(), (int) Math.floor(g.getY() + 0.1), g.getBlockZ());
        return below.isLiquid() || at.isLiquid();
    }

    private boolean blocked(Location g) {
        int x = g.getBlockX(), z = g.getBlockZ();
        int y0 = (int) Math.floor(g.getY() + 0.05);
        for (int y = y0; y <= y0 + 2; y++) {
            Block b = world.getBlockAt(x, y, z);
            if (!b.isPassable() && !b.isLiquid()) return true;
        }
        return false;
    }

    // =================================================================== Blüten & Modelle

    public void petalStorm(Location center, float radius) {
        if (petals == null) return;
        petals.storm(rel(center), radius);
    }

    public void petalStormCenter(Location center) {
        if (petals != null && petals.mode() == PetalField.Mode.STORM) petals.moveStormCenter(rel(center));
    }

    public void petalDrift() {
        if (petals != null && petals.mode() == PetalField.Mode.STORM) petals.setMode(phase == Phase.SANGE ? PetalField.Mode.RISE : PetalField.Mode.DRIFT);
    }

    Vector3f rel(Location l) {
        return new Vector3f((float) (l.getX() - arena.ox), (float) (l.getY() - arena.oy), (float) (l.getZ() - arena.oz));
    }

    public void trackModel(ModelInstance m) {
        extraModels.add(m);
    }

    public void untrackModel(ModelInstance m) {
        extraModels.remove(m);
    }

    // =================================================================== Teilnehmer

    private void updateParticipants() {
        if (state == State.ENDED) return;
        for (Player p : world.getPlayers()) {
            if (participants.containsKey(p.getUniqueId())) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (state == State.REBUILD) continue;
            if (arena.insideWalls(p.getLocation())) join(p);
        }
        Iterator<Map.Entry<UUID, Player>> it = participants.entrySet().iterator();
        while (it.hasNext()) {
            Player p = it.next().getValue();
            boolean gone = !p.isOnline() || p.getWorld() != world || (!p.isDead() && !arena.insideWalls(p.getLocation()));
            if (gone) {
                it.remove();
                detach(p);
            }
        }
    }

    public void join(Player p) {
        if (participants.containsKey(p.getUniqueId())) return;
        participants.put(p.getUniqueId(), p);
        names.put(p.getUniqueId(), p.getName());
        p.showBossBar(bar);
        atmo.join(p);
    }

    /** Spieler verlässt den Kampf (Logout, Teleport weg …). */
    public void leave(Player p) {
        if (participants.remove(p.getUniqueId()) != null) detach(p);
    }

    private void detach(Player p) {
        if (!p.isOnline()) return;
        p.hideBossBar(bar);
        atmo.leave(p);
        p.stopSound(org.bukkit.SoundCategory.RECORDS);
    }

    // =================================================================== Bossleiste & Text

    String phaseLabel() {
        if (ruin) return s.msg("phase-ruin", "終の型 「無」");
        return s.msg(phase.messageKey, phase.kanji);
    }

    void refreshBarName() {
        String t = s.msg("bossbar", "{phase}")
                .replace("{phase}", phaseLabel())
                .replace("{hp}", String.valueOf(Math.max(0, Math.round(hp))))
                .replace("{max}", String.valueOf(Math.max(0, Math.round(maxHp))));
        bar.name(chrome(mm(t)));
        bar.color(switch (phase) {
            case SEI -> BossBar.Color.PINK;
            case ARASHI -> BossBar.Color.RED;
            case SANGE -> ruin ? BossBar.Color.WHITE : BossBar.Color.PURPLE;
        });
    }

    void setBarName(Component c, BossBar.Color color) {
        bar.name(chrome(c));
        if (color != null) bar.color(color);
    }

    /** Pack boss medallion + rails — falls back cleanly if Core/pack glyphs are off. */
    private static Component chrome(Component title) {
        try {
            return BarChrome.component(BarChrome.Family.BOSS, BarChrome.Icon.SIGIL, title);
        } catch (NoClassDefFoundError | Exception ignored) {
            return title == null ? Component.empty() : title;
        }
    }

    void setBarOverride(float v) {
        barOverride = v;
    }

    private void updateBar() {
        float want = barOverride >= 0 ? barOverride : (float) Math.max(0, Math.min(1, hp / maxHp));
        float d = want - barShown;
        if (Math.abs(d) < 0.002f) barShown = want;
        else barShown += d * 0.2f;
        bar.progress(Math.max(0f, Math.min(1f, barShown)));
        // Refresh HP numbers in the title (progress alone is not enough).
        if (tick % 5 == 0) {
            refreshBarName();
        }
    }

    public static Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(s);
    }

    /** Message to everyone in the dojo (participants + nearby). Key looked up per-player language. */
    void broadcastToArena(String messageKey) {
        if (messageKey == null || messageKey.isEmpty()) return;
        for (Player p : world.getPlayers()) {
            if (!(participants.containsKey(p.getUniqueId()) || arena.inRegion(p.getLocation()))) continue;
            String body = s.msg(p, messageKey, s.msg(messageKey, ""));
            if (body == null || body.isEmpty()) continue;
            p.sendMessage(mm(s.msg(p, "prefix", s.msg("prefix", "")) + body));
        }
    }

    /** Raw MiniMessage already resolved (victory names, etc.) — English prefix. */
    void broadcastRaw(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return;
        Component c = mm(s.msg("prefix", "") + miniMessage);
        for (Player p : world.getPlayers()) {
            if (participants.containsKey(p.getUniqueId()) || arena.inRegion(p.getLocation())) p.sendMessage(c);
        }
    }

    // =================================================================== Tick

    public void tick() {
        if (state == State.ENDED) return;
        tick++;
        if (tick == 1 || tick % 10 == 0) updateParticipants();
        try {
            switch (state) {
                case COMBAT -> {
                    brain.tick();
                    checkThresholds();
                    checkUltimateTimeout();
                    checkEmpty();
                }
                case EXHAUSTED -> {
                    checkThresholds();
                    if (state == State.EXHAUSTED && --exhaustedLeft <= 0) endExhaustion();
                    checkEmpty();
                }
                case INTRO, TRANSITION, ULTIMATE -> checkEmpty();
                default -> {
                }
            }
            Sequence cur = cinematic;
            // Eine Sequenz darf in ihrem eigenen Callback eine neue starten – dann bleibt die neue bestehen.
            if (cur != null && !cur.tick() && cinematic == cur) cinematic = null;
            if (rebuildAt >= 0 && tick >= rebuildAt) {
                rebuildAt = -1;
                startRebuild(false);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Fehler im Kampf-Tick (" + arena.id + ")", ex);
        }
        if (boss != null && state != State.REBUILD) {
            try {
                boss.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Boss-Tick", ex);
            }
        }
        fxm.tick();
        destruction.tick();
        List<Player> listeners = participantsList();
        music.tick(listeners);
        atmo.tick(listeners);
        updateBar();
        if (tick % 200 == 0) pool.trim(96);
    }

    private void checkThresholds() {
        if (state != State.COMBAT && state != State.EXHAUSTED) return;
        if (hp <= 0.0001) {
            startDeath();
            return;
        }
        if (state == State.EXHAUSTED) return;
        double frac = hp / maxHp;
        if (phase == Phase.SEI && frac <= s.arashiAt + 1e-9) startTransition(Phase.ARASHI);
        else if (phase == Phase.ARASHI && frac <= s.sangeAt + 1e-9) startTransition(Phase.SANGE);
        else if (phase == Phase.SANGE && !ultimateDone && frac <= s.ultimateAt + 1e-9) startUltimate();
    }

    private void checkUltimateTimeout() {
        if (phase == Phase.SANGE && !ultimateDone && s.ultimateTimeout > 0 && tick - phaseStart > s.ultimateTimeout) {
            startUltimate();
        }
    }

    private void checkEmpty() {
        if (targets().isEmpty()) {
            emptyTicks++;
            if (emptyTicks >= s.emptyResetSeconds * 20) defeat();
        } else {
            emptyTicks = 0;
        }
    }

    // =================================================================== Phasen & Zwischensequenzen

    private void startTransition(Phase to) {
        brain.cancel();
        state = State.TRANSITION;
        boss.setInvulnerable(true);
        Phase from = phase;
        cinematic = to == Phase.ARASHI ? cine.toArashi() : cine.toSange();
        Bukkit.getPluginManager().callEvent(new SamuraiPhaseChangeEvent(arena.id, from, to));
    }

    /** Von den Zwischensequenzen aufgerufen, wenn die neue Phase tatsächlich beginnt. */
    void enterPhase(Phase p) {
        phase = p;
        phaseStart = tick;
        if (p == Phase.SANGE) ghostForm = true;
        refreshBarName();
    }

    private void startUltimate() {
        brain.cancel();
        ultimateDone = true;
        state = State.ULTIMATE;
        boss.setInvulnerable(true);
        cinematic = cine.ultimate();
    }

    void beginExhaustion() {
        state = State.EXHAUSTED;
        exhaustedLeft = s.exhaustedTicks;
        ruin = true;
        boss.setInvulnerable(false);
        refreshBarName();
    }

    private void endExhaustion() {
        state = State.TRANSITION;
        boss.setInvulnerable(true);
        cinematic = cine.getUp();
    }

    private void startDeath() {
        brain.cancel();
        state = State.DYING;
        hp = 0;
        boss.setInvulnerable(true);
        resetCinematicState();
        cinematic = cine.death();
    }

    /** Nach dem Tod: Belohnungen, Meldungen, dann bleiben die Ruinen kurz liegen. */
    void victory() {
        state = State.VICTORY;
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(damage.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> winners = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : ranking) winners.add(names.getOrDefault(e.getKey(), "?"));
        if (winners.isEmpty()) for (Player p : participantsList()) winners.add(p.getName());
        // Belohnungs-Befehle
        int rank = 0;
        for (Map.Entry<UUID, Double> e : ranking) {
            rank++;
            String name = names.get(e.getKey());
            if (name == null) continue;
            for (String cmd : s.rewardCommands) {
                String c = cmd.replace("%player%", name).replace("%damage%", String.valueOf(Math.round(e.getValue())))
                        .replace("%rank%", String.valueOf(rank));
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.startsWith("/") ? c.substring(1) : c);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Belohnungsbefehl fehlgeschlagen: " + c + " (" + ex.getMessage() + ")");
                }
            }
        }
        if (s.broadcastVictory) {
            String msg = s.msg("victory-broadcast", "{players}").replace("{players}", String.join(", ", winners));
            Bukkit.broadcast(mm(s.msg("prefix", "") + msg));
        }
        Bukkit.getPluginManager().callEvent(new SamuraiBossDefeatEvent(arena.id, new ArrayList<>(participantsList()),
                new HashMap<>(damage)));
        // Die Ruinen bleiben kurz liegen (die Todessequenz läuft dabei zu Ende)
        rebuildAt = tick + Math.max(1, s.ruinSeconds * 20);
    }

    /** Alle Herausforderer sind gefallen oder gegangen. */
    void defeat() {
        if (state == State.DEFEAT || state == State.REBUILD || state == State.ENDED || state == State.DYING
                || state == State.VICTORY) return;
        brain.cancel();
        state = State.DEFEAT;
        boss.setInvulnerable(true);
        resetCinematicState();
        cinematic = cine.defeat();
    }

    /** Falls eine Zwischensequenz unterbrochen wurde: eingefrorene Zeit, Schweben, Unsichtbarkeit aufheben. */
    private void resetCinematicState() {
        if (boss == null) return;
        boss.animator().setTimeScale(1f);
        boss.setGravity(true);
        boss.model().setAllHidden(false);
        if (petals != null && !petals.isStopped() && petals.mode() == PetalField.Mode.FROZEN) petals.setMode(PetalField.Mode.DRIFT);
    }

    void startRebuild(boolean fast) {
        if (state == State.REBUILD || state == State.ENDED) return;
        brain.cancel();
        state = State.REBUILD;
        cinematic = null;
        removeBoss();
        arena.state = Arena.State.REBUILDING;
        plugin.arenas().save(arena);
        atmo.timeTo(participantsList(), world.getTime() % 24000, 80);
        atmo.weather(participantsList(), null);
        atmo.tint(0f);
        if (!destruction.hasDamage()) {
            // nichts zerstört: nur exakter Abgleich
            RestoreJob rj = new RestoreJob(world, arena.fightSnapshot, s.restorePerTick, this::finishEnd);
            if (arena.fightSnapshot == null) finishEnd();
            else plugin.arenas().runJob(arena, rj);
            return;
        }
        int perTick = fast ? s.rebuildPerTick * 2 : s.rebuildPerTick;
        RebuildJob job = destruction.rebuild(arena.fightSnapshot, perTick, s.rebuildFlightTicks, this::finishEnd);
        plugin.arenas().runJob(arena, job);
        if (petals != null && !petals.isStopped()) petals.setMode(PetalField.Mode.RISE);
    }

    /** Endgültiges Aufräumen nach dem Wiederaufbau (oder sofort beim Abbruch). */
    private void finishEnd() {
        if (state == State.ENDED) return;
        boolean victory = hp <= 0;
        state = State.ENDED;
        ending = true;
        cinematic = null;
        if (brain != null) brain.cancel();
        removeBoss();
        music.stop(0);
        List<Player> ps = participantsList();
        for (Player p : ps) {
            p.hideBossBar(bar);
            p.stopSound(org.bukkit.SoundCategory.RECORDS);
        }
        atmo.restoreAll(ps);
        participants.clear();
        fxm.clear();
        pool.clear();
        for (ModelInstance m : new ArrayList<>(extraModels)) m.remove();
        extraModels.clear();
        destruction.clearAll();
        arena.state = Arena.State.IDLE;
        arena.fight = null;
        arena.fightSnapshot = null;
        plugin.arenas().save(arena);
        plugin.arenas().removeTickets(arena);
        if (s.clearDropsOnReset) plugin.arenas().clearDrops(arena);
        arena.deco.showAll();
        Bukkit.getPluginManager().callEvent(new SamuraiFightEndEvent(arena.id, victory));
    }

    private void removeBoss() {
        if (boss != null) {
            boss.remove();
        }
    }

    /**
     * Bricht den Kampf ab.
     *
     * @param instant true = sofort alles wiederherstellen (Server-Stopp, Reset), false = Wiederaufbau-Animation
     */
    public void abort(boolean instant) {
        if (state == State.ENDED) return;
        if (!instant) {
            if (state == State.REBUILD) return;
            broadcastToArena("defeat");
            fxm.clear();
            startRebuild(true);
            return;
        }
        ending = true;
        if (brain != null) brain.cancel();
        cinematic = null;
        // laufenden Wiederaufbau sofort beenden (stellt bereits alles her)
        var job = plugin.arenas().job(arena);
        if (job != null) {
            job.finishNow();
        }
        if (state != State.ENDED) {
            destruction.clearAll();
            if (arena.fightSnapshot != null && world != null) {
                new RestoreJob(world, arena.fightSnapshot, s.restorePerTick, null).finishNow();
            }
            finishEnd();
        }
    }

    // =================================================================== Debug

    public void debugSetHp(double fraction) {
        hp = Math.max(floorHp(), Math.min(maxHp, maxHp * fraction));
        if (fraction <= 0) hp = 0;
    }

    public void debugKill() {
        if (state == State.COMBAT || state == State.EXHAUSTED) {
            ultimateDone = true;
            phase = Phase.SANGE;
            hp = 0;
            checkThresholds();
        }
    }

    public boolean debugAbility(String id) {
        if (state != State.COMBAT) return false;
        return brain.force(id);
    }

    public void debugPhase(int n) {
        if (state != State.COMBAT) return;
        if (n == 2 && phase == Phase.SEI) hp = s.arashiAt * maxHp;
        if (n == 3 && phase != Phase.SANGE) {
            if (phase == Phase.SEI) hp = s.arashiAt * maxHp;
            else hp = s.sangeAt * maxHp;
        }
        if (n == 4 && phase == Phase.SANGE && !ultimateDone) hp = s.ultimateAt * maxHp;
        checkThresholds();
    }

    void setCinematic(Sequence q) {
        cinematic = q;
    }

    void setState(State st) {
        state = st;
    }

    public String describe() {
        return "Phase " + phase.number + (ruin ? " (Ruinen)" : "") + ", " + state + ", HP " + Math.round(hp) + "/" + Math.round(maxHp)
                + ", Spieler " + participants.size() + ", Effekte " + fxm.size() + ", FX-Displays " + pool.activeCount() + "/" + pool.totalCount()
                + ", Trümmer " + destruction.debrisCount()
                + (brain != null && brain.current() != null ? ", Fähigkeit " + brain.current().id : "");
    }
}
