package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.combat.Parries;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.helios.encounter.HeliosBars;
import de.aetherion.bossengine.helios.world.ArenaSlots;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.BossTitlesDe;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.api.BarChrome;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One group's night in the belfry, in its own slot of the Hush world.
 *
 * <pre>
 *   PREPARING  the belfry is built (one tick)
 *   FIGHT      the boss is on stage: intro, the fight, both lullabies, the fifth toll
 *   DAWN       it is over; the night light burns, loot is paid, people look around
 *   CLOSED     everyone is home, the slot is air again
 * </pre>
 *
 * The encounter owns what outlives a single move: the arena and its lamps, the people and their dread,
 * the bars, and every way out. {@link HushScript} owns the body and the fight. Every exit (victory,
 * wipe, quit, admin abort, plugin disable) ends in {@link #close}; a crash is handled by the slot
 * journal and the record on each player.
 */
final class HushEncounter {

    static final String BOSS_ID = "the_hush";

    enum State { PREPARING, FIGHT, DAWN, CLOSED }

    private static final Particle.DustOptions WARM = new Particle.DustOptions(Color.fromRGB(255, 176, 74), 1.1f);

    private final HushModule module;
    private final HushConfig cfg;
    private final int slot;
    private final World world;
    private final int cx;
    private final int cy;
    private final HushArena arena;
    private final HushParty party = new HushParty();
    private final HeliosBars barsEn = new HeliosBars();
    private final HeliosBars barsDe = new HeliosBars();
    private final Map<UUID, BossBar> dreadBars = new HashMap<>();
    private final Map<String, Integer> iframes = new HashMap<>();

    private BossInstance boss;
    private HushScript script;
    private State state = State.PREPARING;
    private int clock;
    private int closeAt = -1;
    private boolean victory;
    private boolean cinematic = true;
    private int allDownTicks;
    private int lastReviveAt = -10_000;

    private HushEncounter(HushModule module, int slot) {
        this.module = module;
        this.cfg = module.config();
        this.slot = slot;
        this.world = module.world();
        this.cx = slot * cfg.slotSpacing();
        this.cy = cfg.arenaY();
        this.arena = new HushArena(module.plugin(), world, cx, cy, 0);
    }

    /** @return the encounter, or empty when no slot is free */
    static Optional<HushEncounter> open(HushModule module, List<Player> players) {
        List<UUID> ids = new ArrayList<>();
        for (Player p : players) {
            ids.add(p.getUniqueId());
        }
        int slot = module.slots().claim(module.config().maxInstances(), ids);
        if (slot < 0) {
            return Optional.empty();
        }
        HushEncounter e = new HushEncounter(module, slot);
        for (Player p : players) {
            e.party.add(p);
        }
        HushText.send(players, HushText.FORMING);
        return Optional.of(e);
    }

    /* ================================================================== accessors */

    int slot() {
        return slot;
    }

    HushConfig config() {
        return cfg;
    }

    HushModule module() {
        return module;
    }

    HushArena arena() {
        return arena;
    }

    HushParty party() {
        return party;
    }

    State state() {
        return state;
    }

    boolean closed() {
        return state == State.CLOSED;
    }

    int clock() {
        return clock;
    }

    boolean cinematic() {
        return cinematic;
    }

    void cinematic(boolean on) {
        this.cinematic = on;
    }

    BossInstance boss() {
        return boss;
    }

    HushScript script() {
        return script;
    }

    boolean contains(Location l) {
        return arena.contains(l);
    }

    /** Standing participants who can be hit and can act. */
    List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        for (Player p : party.standing()) {
            GameMode m = p.getGameMode();
            if ((m == GameMode.SURVIVAL || m == GameMode.ADVENTURE) && p.getWorld() == world) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everyone who watches (standing and fallen). */
    List<Player> audience() {
        List<Player> out = new ArrayList<>();
        for (Player p : party.present()) {
            if (p.getWorld() == world) {
                out.add(p);
            }
        }
        return out;
    }

    /* ================================================================== lifecycle */

    private void arrive() {
        List<Player> present = party.present();
        if (present.isEmpty()) {
            close(false, "empty");
            return;
        }
        arena.build();
        module.slots().set(slot, ArenaSlots.State.ACTIVE);
        int i = 0;
        for (Player p : present) {
            // Everyone starts beside the first lamps, facing the dark middle.
            double a = (0.5 + (i % HushArena.LAMPS)) * Math.PI * 2 / HushArena.LAMPS;
            double r = HushArena.LAMP_RING + 2.2;
            Location to = arena.at(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            to.setDirection(arena.at(0, 1.5, 0).toVector().subtract(to.toVector()));
            module.teleport(p, to);
            settle(p);
            HushParty.Member m = party.get(p);
            if (m != null) {
                m.groundX = arena.localX(to);
                m.groundZ = arena.localZ(to);
                m.graceUntil = clock + 60;
            }
            i++;
        }
        state = State.FIGHT;
        cinematic = true;
        Player leader = present.get(0);
        Optional<BossInstance> spawned = BossEngine.getInstance().getBossManager()
                .spawn(BOSS_ID, arena.at(0, 0.4, 0), SpawnCause.API, leader, "hush_" + slot, null);
        if (spawned.isEmpty()) {
            module.plugin().getLogger().warning("[Hush] Could not spawn " + BOSS_ID + " (template missing?). Aborting slot " + slot);
            close(false, "spawn");
            return;
        }
        boss = spawned.get();
        int players = Math.min(cfg.maxScaledPlayers(), Math.max(1, party.count()));
        boss.applyCustomScale(players, 1.0 + cfg.healthPerExtra() * (players - 1), 1.0 + cfg.damagePerExtra() * (players - 1));
        for (HeliosBars bars : new HeliosBars[] {barsEn, barsDe}) {
            bars.sigil(BarChrome.Icon.SKULL);
            bars.movement(true);
            bars.fillOver(60);
        }
        barsEn.boss(HushText.NAME.en(), BossBar.Color.PURPLE, 66, 33);
        barsDe.boss(HushText.NAME.de(), BossBar.Color.PURPLE, 66, 33);
    }

    /** The state a participant fights in: survival, no flight, midnight, quest HUD out of the way. */
    private void settle(Player p) {
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        p.setFallDistance(0f);
        p.setPlayerTime(18_000L, false);
        de.aetherion.core.api.QuestBars.suppress(p);
    }

    /** Called by the script right after it bound to its instance. */
    void attach(HushScript script) {
        this.script = script;
    }

    void tick() {
        clock++;
        if (state == State.PREPARING) {
            // One tick after the claim: the players' teleports happen outside the command's own tick.
            arrive();
            return;
        }
        if (state == State.CLOSED) {
            return;
        }
        guardPlayers();
        tickLamps();
        tickBars();
        if (state == State.FIGHT) {
            tickDread();
            if (script != null && script.finished() && !victory) {
                beginDawn();
            } else if (boss != null && !boss.isEncounterActive() && !victory && closeAt < 0 && clock > 40) {
                // Removed from outside (/boss kill, despawn): there is nothing left to fight.
                close(false, "boss-gone");
                return;
            }
            if (!victory && closeAt < 0 && clock > 100) {
                allDownTicks = party.standing().isEmpty() ? allDownTicks + 1 : 0;
                if (allDownTicks > 100) {
                    wipe();
                }
            }
        }
        if (closeAt >= 0 && clock >= closeAt) {
            close(victory, victory ? "victory" : "wipe");
        }
    }

    private void beginDawn() {
        victory = true;
        state = State.DAWN;
        cinematic = true;
        barsEn.visible(false);
        barsDe.visible(false);
        for (HushParty.Member m : party.all()) {
            m.dread = 0;
            if (m.down && !m.gone) {
                Player p = Bukkit.getPlayer(m.id);
                if (p != null) {
                    raise(p, m, arena.nearestLamp(0, 0, true));
                }
            }
        }
        closeAt = clock + Math.max(5, cfg.i("fight.dawn-seconds", 25)) * 20;
    }

    private void wipe() {
        cinematic = true;
        if (script != null) {
            script.stop();
        }
        for (int i = 0; i < HushArena.LAMPS; i++) {
            snuff(i, false);
        }
        HushText.title(audience(), HushText.LULLABY_FAIL, null, 5, 50, 20);
        HushText.send(audience(), HushText.WIPE);
        world.playSound(arena.at(0, 2, 0), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3f, 0.5f);
        closeAt = clock + 80;
    }

    /* ================================================================== lamps */

    /** Puts lamp {@code i} out. @return true if it was burning */
    boolean snuff(int i, boolean announce) {
        if (!arena.setLamp(i, false)) {
            return false;
        }
        Location flame = arena.lampFlame(i);
        world.spawnParticle(Particle.LARGE_SMOKE, flame, 14, 0.15, 0.2, 0.15, 0.02);
        world.playSound(flame, Sound.BLOCK_CANDLE_EXTINGUISH, SoundCategory.HOSTILE, 2.5f, 0.6f);
        world.playSound(flame, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.8f, 0.5f);
        if (announce) {
            HushText.bar(audience(), HushText.SNUFFED);
        }
        return true;
    }

    /** Lights lamp {@code i}. @return true if it was out */
    boolean relight(int i, Player by) {
        if (!arena.setLamp(i, true)) {
            return false;
        }
        Location flame = arena.lampFlame(i);
        world.spawnParticle(Particle.FLAME, flame, 18, 0.2, 0.2, 0.2, 0.03);
        world.spawnParticle(Particle.DUST, flame, 20, 1.2, 0.4, 1.2, 0, WARM);
        world.playSound(flame, Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.4f, 1.3f);
        world.playSound(flame, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.6f, 0.8f);
        if (by != null) {
            HushText.bar(audience(), HushText.RELIT.with(by.getName()));
            reviveOneAt(i);
            if (script != null) {
                script.lampRelit(i);
            }
        }
        return true;
    }

    /** A player used a block in the belfry. @return true if it was a lamp or a bell (the click is ours) */
    boolean use(Player p, Block block) {
        if (state != State.FIGHT || !party.contains(p) || script == null) {
            return false;
        }
        HushParty.Member m = party.get(p);
        if (m == null || m.down) {
            return false;
        }
        int bell = arena.bellAt(block);
        if (bell >= 0) {
            script.ringBell(p, bell);
            return true;
        }
        int lamp = arena.lampAt(block);
        if (lamp >= 0) {
            if (!arena.lit(lamp) && script.lampsCanBeLit() && !script.lampLocked(lamp)) {
                relight(lamp, p);
            }
            return true;
        }
        return false;
    }

    private void tickLamps() {
        if (clock % 10 != 0) {
            return;
        }
        for (int i = 0; i < HushArena.LAMPS; i++) {
            if (arena.lit(i)) {
                // The edge of the lamplight, drawn on the floor so "in the light" is never a guess.
                double phase = clock * 0.01 + i;
                for (double[] at : HushArena.ring(HushArena.LIGHT_RADIUS, 18, phase)) {
                    world.spawnParticle(Particle.DUST, arena.at(HushArena.lampX(i) + at[0], 0.12, HushArena.lampZ(i) + at[1]),
                            1, 0, 0, 0, 0, WARM);
                }
            } else if (clock % 40 == 0) {
                world.spawnParticle(Particle.SMOKE, arena.lampFlame(i), 2, 0.05, 0.1, 0.05, 0.005);
            }
        }
    }

    /* ================================================================== dread */

    void addDread(Player p, double amount) {
        HushParty.Member m = party.get(p);
        if (m != null && !m.down && !m.gone) {
            m.dread = Math.max(0.0, Math.min(100.0, m.dread + amount));
        }
    }

    private void tickDread() {
        if (script == null) {
            return;
        }
        double factor = cinematic ? 0.0 : script.dreadFactor();
        double rise = cfg.d("dread.per-second", 4.0) / 20.0 * factor;
        double fall = cfg.d("dread.fade-per-second", 12.0) / 20.0;
        for (Player p : fighters()) {
            HushParty.Member m = party.get(p);
            if (m == null) {
                continue;
            }
            boolean light = arena.inLight(p.getLocation());
            m.dread = Math.max(0.0, Math.min(100.0, m.dread + (light ? -fall : rise)));
            if (clock % 20 == 0 && m.dread >= 60.0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
                if (m.dread >= 85.0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 0.8f);
                }
            }
            if (m.dread >= 100.0 && !cinematic) {
                m.dread = 60.0;
                p.sendActionBar(TextUtil.component(HushText.FOUND_YOU.of(p)));
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, SoundCategory.HOSTILE, 1f, 0.7f);
                world.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.02);
                hit(p, cfg.d("dread.power", 45.0), "dread", 30, null, false);
            }
        }
    }

    /* ================================================================== bars */

    private void tickBars() {
        if (boss == null || script == null || state != State.FIGHT) {
            barsEn.sync(List.of());
            barsDe.sync(List.of());
            for (UUID id : new ArrayList<>(dreadBars.keySet())) {
                hideDread(id);
            }
            return;
        }
        boolean show = script.barsVisible();
        List<Player> en = new ArrayList<>();
        List<Player> de = new ArrayList<>();
        for (Player p : audience()) {
            (BossTitlesDe.german(p) ? de : en).add(p);
        }
        BossBar.Color color = script.revealed() ? BossBar.Color.YELLOW : BossBar.Color.PURPLE;
        for (HeliosBars bars : new HeliosBars[] {barsEn, barsDe}) {
            boolean german = bars == barsDe;
            bars.visible(show);
            bars.update(boss.healthPercent(), script.blocksDamage(), color, script.darkenScreen(), false);
            HushText.T line = script.barLine();
            bars.movement(german ? line.de() : line.en(), script.barProgress(), script.barColor());
            bars.sync(german ? de : en);
        }
        if (clock % 5 != 0) {
            return;
        }
        for (Player p : audience()) {
            HushParty.Member m = party.get(p);
            if (m == null || m.down || !show) {
                hideDread(p.getUniqueId());
                continue;
            }
            BossBar bar = dreadBars.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(TextUtil.component(""), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
                dreadBars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            }
            bar.progress((float) Math.max(0.0, Math.min(1.0, m.dread / 100.0)));
            bar.color(m.dread >= 60.0 ? BossBar.Color.RED : BossBar.Color.PURPLE);
            bar.name(TextUtil.component(BarChrome.legacy(BarChrome.Family.PHASE, null, TextUtil.color(HushText.DREAD.of(p)))));
        }
    }

    private void hideDread(UUID id) {
        BossBar bar = dreadBars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) {
            p.hideBossBar(bar);
        }
    }

    /* ================================================================== hits */

    /**
     * Hits a player once per {@code key} per {@code iframes} ticks.
     *
     * @param power     BossHits power
     * @param knock     velocity to add (may be null)
     * @param parryable a strike the combat kit can parry (otherwise a hazard only a roll avoids)
     * @return true if the hit landed
     */
    boolean hit(Player p, double power, String key, int iframes, Vector knock, boolean parryable) {
        if (p == null || cinematic || state != State.FIGHT || p.isDead()) {
            return false;
        }
        HushParty.Member m = party.get(p);
        if (m == null || m.gone || m.down || clock < m.graceUntil) {
            return false;
        }
        GameMode mode = p.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }
        String k = p.getUniqueId() + ":" + key;
        Integer until = this.iframes.get(k);
        if (until != null && clock < until) {
            return false;
        }
        this.iframes.put(k, clock + Math.max(1, iframes));
        double scaled = boss == null ? power : boss.scaleDamage(power);
        if (script != null) {
            scaled *= script.damageFactor();
        }
        LivingEntity source = boss == null ? null : boss.getEntity();
        if (parryable) {
            BossHits.hurt(p, source, scaled);
            if (boss != null) {
                boss.guard().parriedBy(p);
            }
        } else {
            BossHits.peril(p, source, scaled);
        }
        if (Parries.avoided(p)) {
            return false;
        }
        if (knock != null && knock.lengthSquared() > 1e-4) {
            p.setVelocity(p.getVelocity().multiply(0.3).add(tame(knock)));
        }
        return true;
    }

    /** Knockback that stays on the floor: capped, and never much upward. */
    private Vector tame(Vector knock) {
        Vector flat = new Vector(knock.getX(), 0, knock.getZ());
        double cap = cfg.d("safety.max-knockback", 0.7);
        if (flat.length() > cap) {
            flat.normalize().multiply(cap);
        }
        return flat.setY(Math.max(0.0, Math.min(0.35, knock.getY())));
    }

    void grace(Player p, int ticks) {
        HushParty.Member m = party.get(p);
        if (m != null) {
            m.graceUntil = Math.max(m.graceUntil, clock + ticks);
        }
    }

    /* ================================================================== guard */

    private void guardPlayers() {
        boolean second = clock % 20 == 0;
        for (Player p : party.present()) {
            HushParty.Member m = party.get(p);
            if (m == null) {
                continue;
            }
            if (p.getWorld() != world) {
                leave(p, false);
                continue;
            }
            if (second) {
                de.aetherion.core.api.QuestBars.suppress(p);
            }
            double x = arena.localX(p.getLocation());
            double y = arena.localY(p.getLocation());
            double z = arena.localZ(p.getLocation());
            if (m.down) {
                if (x * x + z * z > 34 * 34 || y < -10 || y > 40) {
                    module.teleport(p, overlook());
                }
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
            if (((org.bukkit.entity.Entity) p).isOnGround() && y > -0.6 && x * x + z * z < (HushArena.RADIUS - 1.0) * (HushArena.RADIUS - 1.0)) {
                m.groundX = x;
                m.groundZ = z;
            }
            if (y < -cfg.killPlane() || x * x + z * z > 36 * 36) {
                rescue(p, m);
            }
        }
    }

    /** Whoever falls is set back beside the nearest burning lamp (or where they last stood). It stings. */
    private void rescue(Player p, HushParty.Member m) {
        int lamp = arena.nearestLamp(m.groundX, m.groundZ, true);
        Location to = lamp >= 0 ? besideLamp(lamp) : arena.at(m.groundX, 0.1, m.groundZ);
        to.setYaw(p.getLocation().getYaw());
        module.teleport(p, to);
        p.setFallDistance(0f);
        p.setVelocity(new Vector());
        p.playSound(to, Sound.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1f, 0.7f);
        if (!cinematic) {
            hit(p, cfg.rescuePower(), "void", 20, null, false);
        }
        grace(p, 40);
    }

    private Location besideLamp(int lamp) {
        double x = HushArena.lampX(lamp);
        double z = HushArena.lampZ(lamp);
        double r = Math.sqrt(x * x + z * z);
        // One and a half blocks toward the middle of the floor, clear of the post.
        return arena.at(x - x / r * 1.5, 0.1, z - z / r * 1.5);
    }

    /** Where the fallen wait: above the middle, looking down. */
    Location overlook() {
        Location l = arena.at(0, 13, 14);
        l.setYaw(180f);
        l.setPitch(42f);
        return l;
    }

    /* ================================================================== people */

    void died(Player p) {
        HushParty.Member m = party.get(p);
        if (m == null || m.gone) {
            return;
        }
        m.deaths++;
        m.dread = 30.0;
        if (m.deaths > cfg.i("fight.respawns-per-player", 1)) {
            m.down = true;
        }
    }

    /** Where a dead participant comes back: beside a lamp while they have a respawn left, else above as a watcher. */
    Location respawn(Player p) {
        HushParty.Member m = party.get(p);
        if (m == null || m.gone) {
            return null;
        }
        if (m.down) {
            Bukkit.getScheduler().runTask(module.plugin(), () -> {
                if (p.isOnline() && party.contains(p) && m.down) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setPlayerTime(18_000L, false);
                }
            });
            return overlook();
        }
        int lamp = arena.nearestLamp(m.groundX, m.groundZ, true);
        Location to = lamp >= 0 ? besideLamp(lamp) : arena.at(0, 0.1, HushArena.LAMP_RING);
        grace(p, cfg.respawnGraceTicks());
        Bukkit.getScheduler().runTask(module.plugin(), () -> {
            if (p.isOnline() && party.contains(p)) {
                settle(p);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, cfg.respawnGraceTicks(), 4, false, false, false));
            }
        });
        return to;
    }

    /** A lamp was lit by hand: the light brings one fallen player back beside it. */
    private void reviveOneAt(int lamp) {
        if (clock - lastReviveAt < cfg.i("fight.revive-cooldown-seconds", 15) * 20) {
            return;
        }
        for (HushParty.Member m : party.all()) {
            if (!m.down || m.gone) {
                continue;
            }
            Player p = Bukkit.getPlayer(m.id);
            if (p != null && p.isOnline()) {
                raise(p, m, lamp);
                lastReviveAt = clock;
                return;
            }
        }
    }

    /** The song held: everyone who fell stands up again. */
    void reviveAll() {
        for (HushParty.Member m : party.all()) {
            if (m.down && !m.gone) {
                Player p = Bukkit.getPlayer(m.id);
                if (p != null && p.isOnline()) {
                    raise(p, m, arena.nearestLamp(0, 0, true));
                }
            }
        }
    }

    private void raise(Player p, HushParty.Member m, int lamp) {
        m.down = false;
        m.dread = 20.0;
        if (p.isDead()) {
            p.spigot().respawn();
        }
        Location to = lamp >= 0 ? besideLamp(lamp) : arena.at(0, 0.1, HushArena.LAMP_RING);
        module.teleport(p, to);
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        settle(p);
        grace(p, cfg.respawnGraceTicks());
        world.spawnParticle(Particle.END_ROD, to.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.02);
        world.playSound(to, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1f, 1.5f);
    }

    /** A participant leaves (command, quit, other world). */
    void leave(Player p, boolean teleport) {
        HushParty.Member m = party.get(p);
        if (m == null || m.gone) {
            return;
        }
        m.gone = true;
        release(p);
        HushParty.sendHome(p, m, module.fallbackReturn(), teleport);
        module.slots().players(slot, party.activeIds());
        if (party.present().isEmpty() && state != State.CLOSED) {
            close(victory, "empty");
        }
    }

    private void release(Player p) {
        barsEn.hide(p);
        barsDe.hide(p);
        hideDread(p.getUniqueId());
        de.aetherion.core.api.QuestBars.unsuppress(p);
    }

    /** Ends the instance: boss gone, everyone home, the slot air again and free. Idempotent. */
    void close(boolean success, String reason) {
        if (state == State.CLOSED) {
            return;
        }
        State was = state;
        state = State.CLOSED;
        module.plugin().getLogger().info("[Hush] Slot " + slot + " closed (" + reason + ", was " + was + ").");
        var manager = BossEngine.getInstance() == null ? null : BossEngine.getInstance().getBossManager();
        if (boss != null && manager != null && boss.isEncounterActive()) {
            manager.despawn(boss, BossDespawnEvent.Reason.COMMAND, null);
        }
        if (script != null) {
            script.abort();
        }
        barsEn.hideAll();
        barsDe.hideAll();
        for (HushParty.Member m : party.all()) {
            if (m.gone) {
                continue;
            }
            m.gone = true;
            Player p = Bukkit.getPlayer(m.id);
            if (p != null) {
                release(p);
                HushParty.sendHome(p, m, module.fallbackReturn(), true);
                p.sendMessage(TextUtil.component((success ? HushText.VICTORY_HOME : HushText.BROUGHT_BACK).of(p)));
                if (success) {
                    module.markCooldown(p);
                }
            }
        }
        dreadBars.clear();
        module.slots().set(slot, ArenaSlots.State.RESTORING);
        if (was != State.PREPARING) {
            HushArena.clear(module.plugin(), world, cx, cy, 0);
        }
        module.sweep(world, cx);
        module.slots().set(slot, ArenaSlots.State.FREE);
        module.forget(this);
    }

    /* ================================================================== admin */

    String debug() {
        return "slot " + slot + " " + state + " clock " + clock + " players " + party.present().size()
                + " lamps " + arena.litCount() + "/" + HushArena.LAMPS
                + (boss == null ? "" : " hp " + (int) boss.healthPercent() + "%")
                + (script == null ? "" : " " + script.debug());
    }
}
