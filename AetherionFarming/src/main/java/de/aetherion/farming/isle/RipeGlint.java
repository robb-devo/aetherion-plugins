package de.aetherion.farming.isle;

import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.Crops;
import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ripe Glint — Farming's own mechanic (A-51). While you harvest on the isle, a ripe crop near you
 * shines gold for a moment: a golden outline only you can see, a chime from its direction and
 * sparkles. Harvest it while it shines and it pays an extra crop and Farming XP, and your glint
 * streak grows. A shine that fades costs one step; standing around lets the streak fade too.
 * <pre>
 *   harvest ─► glints start near you (ahead of you first) ─► harvest one in the shine
 *                                                           ├─ +1 crop (+2 at Harvest Gleam), XP
 *                                                           └─ streak: 3 Keen Eye · 10 Golden Hands · 20 Harvest Gleam
 * </pre>
 * The streak tiers pay crop-only Fortune and Harvest, exactly like the old rhythm meter did, so
 * Items' hooks and the Bakehouse stay as they were. Mining keeps the rhythm; this is the field's.
 */
public final class RipeGlint {

    static final String LEASE = "ripe-glint";
    private static final String TAG = "ae_ripe_glint";
    private static final double STREAK_MAX = 30.0d;
    /** A glint that fades unharvested costs this much streak. */
    private static final double MISS_COST = 1.0d;
    /** Streak lost per 5-tick step once you stopped harvesting. */
    private static final double IDLE_DRAIN = 0.25d;
    /** Glints keep coming while your last harvest is at most this old. */
    private static final int ACTIVE_TICKS = 8 * 20;
    private static final int BASE_WINDOW_TICKS = 50;
    private static final int SPAWN_EVERY_TICKS = 26;
    private static final int MAX_LIVE = 2;
    private static final int RADIUS = 6;
    private static final int HIDE_AFTER_TICKS = 60;
    private static final Color GOLD = Color.fromRGB(255, 205, 64);
    private static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(GOLD, 1.0f);
    /** Semitone offsets of a major pentatonic run — each pick climbs one note. */
    private static final int[] SCALE = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};

    public enum Tier {
        NONE(0, "", 0, 0, 1, BossBar.Color.WHITE, "§7"),
        KEEN(3, "Keen Eye", 10, 0, 1, BossBar.Color.GREEN, "§a"),
        GOLDEN(10, "Golden Hands", 25, 10, 1, BossBar.Color.YELLOW, "§e"),
        GLEAM(20, "Harvest Gleam", 50, 25, 2, BossBar.Color.PURPLE, "§d");

        final int from;
        final String label;
        final double fortune;
        final double harvest;
        /** Whole crops a glint pick adds on top of the normal yield. */
        final int extra;
        final BossBar.Color color;
        final String chat;

        Tier(int from, String label, double fortune, double harvest, int extra, BossBar.Color color, String chat) {
            this.from = from;
            this.label = label;
            this.fortune = fortune;
            this.harvest = harvest;
            this.extra = extra;
            this.color = color;
            this.chat = chat;
        }

        static Tier of(double streak) {
            Tier best = NONE;
            for (Tier tier : values()) {
                if (streak >= tier.from) {
                    best = tier;
                }
            }
            return best;
        }

        public String label() {
            return label;
        }

        public int from() {
            return from;
        }
    }

    private static final class Glint {
        final Block block;
        final long key;
        final int until;
        final BlockDisplay display;

        Glint(Block block, int until, BlockDisplay display) {
            this.block = block;
            this.key = key(block.getX(), block.getY(), block.getZ());
            this.until = until;
            this.display = display;
        }

        Location center() {
            return block.getLocation().add(0.5d, 0.5d, 0.5d);
        }
    }

    private static final class State {
        double streak;
        double best;
        int lastHarvest;
        int nextSpawn;
        int noteStep;
        int direction = 1;
        int hideAt;
        int picks;
        Tier tier = Tier.NONE;
        BossBar bar;
        final List<Glint> live = new ArrayList<>(MAX_LIVE);
        /** Glint picks of the current tick, read back by Items' drop hook in the same tick. */
        int markTick = -1;
        final Map<Long, Integer> marks = new HashMap<>(4);

        Glint at(Block block) {
            long key = key(block.getX(), block.getY(), block.getZ());
            for (Glint glint : live) {
                if (glint.key == key) {
                    return glint;
                }
            }
            return null;
        }
    }

    private final FarmIsle isle;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    RipeGlint(FarmIsle isle) {
        this.isle = isle;
    }

    void start() {
        sweepStrays();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 5L, 5L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : Map.copyOf(states).keySet()) {
            clear(id);
        }
    }

    boolean enabled() {
        var config = isle.plugin().getConfig();
        return config.getBoolean("ripe-glint.enabled", config.getBoolean("harvest-rhythm.enabled", true));
    }

    // ------------------------------------------------------------------ harvest hook

    /**
     * One mature crop broken on the isle. {@code spread} = broken by Items' Harvest spread: it can
     * still catch a glint, but only a hand harvest keeps the glints coming.
     */
    void onHarvest(Player player, Block block, boolean spread) {
        if (!enabled()) {
            return;
        }
        State state = spread ? states.get(player.getUniqueId())
                : states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        if (state == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (!spread) {
            boolean waking = now - state.lastHarvest > ACTIVE_TICKS;
            state.lastHarvest = now;
            state.hideAt = 0;
            if (waking) {
                // The first harvest after a pause lights the field up almost at once.
                state.nextSpawn = now + 8;
            }
        }
        Glint hit = state.at(block);
        if (hit != null) {
            state.live.remove(hit);
            remove(hit);
            pick(player, state, hit, now);
        }
        paint(player, state);
    }

    private void pick(Player player, State state, Glint glint, int now) {
        double gain = 1.0d + 0.5d * FarmingSkills.scale(player, FarmingSkills.ROW_RHYTHM)
                + isle.bakehouse().glintGain(player);
        Tier before = state.tier;
        state.streak = Math.min(STREAK_MAX, state.streak + gain);
        state.best = Math.max(state.best, state.streak);
        state.tier = Tier.of(state.streak);
        state.picks++;
        if (state.markTick != now) {
            state.markTick = now;
            state.marks.clear();
        }
        state.marks.put(glint.key, state.tier.extra);
        FarmingSkills.bonus(player, 1 + state.tier.ordinal());
        Location at = glint.center();
        playNote(player, state, at);
        player.spawnParticle(Particle.WAX_ON, at, 10, 0.3, 0.3, 0.3, 0.0);
        player.spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 8, 0.25, 0.25, 0.25, 0.0, GOLD_DUST);
        if (state.tier.ordinal() > before.ordinal()) {
            Tier reached = state.tier;
            // After Items' "+1" line for this pick, so neither line hides the other.
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    tierUp(player, reached);
                }
            }, 14L);
        }
    }

    // ------------------------------------------------------------------ Items-facing reads

    /** Extra whole crops for this block, when the same tick's harvest of it caught a glint. */
    int extraAt(Player player, Location at) {
        State state = player == null || at == null ? null : states.get(player.getUniqueId());
        if (state == null || state.markTick != Bukkit.getCurrentTick()) {
            return 0;
        }
        return state.marks.getOrDefault(key(at.getBlockX(), at.getBlockY(), at.getBlockZ()), 0);
    }

    boolean pickedAt(Player player, Location at) {
        return extraAt(player, at) > 0;
    }

    public Tier tier(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? Tier.NONE : state.tier;
    }

    public double streak(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0.0d : state.streak;
    }

    /** Best streak this session. */
    public double best(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0.0d : state.best;
    }

    double cropFortune(Player player) {
        return tier(player).fortune;
    }

    double harvestSpread(Player player) {
        return tier(player).harvest;
    }

    /** DEV: jump straight to Harvest Gleam. */
    void max(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        int now = Bukkit.getCurrentTick();
        state.streak = STREAK_MAX;
        state.best = STREAK_MAX;
        state.lastHarvest = now;
        state.nextSpawn = now;
        state.tier = Tier.GLEAM;
        tierUp(player, Tier.GLEAM);
        paint(player, state);
    }

    void clear(UUID id) {
        State state = states.remove(id);
        if (state != null) {
            for (Glint glint : state.live) {
                remove(glint);
            }
            state.live.clear();
            if (state.bar != null) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.hideBossBar(state.bar);
                }
            }
        }
        QuestBars.release(id, LEASE);
    }

    // ------------------------------------------------------------------ heartbeat (every 5 ticks)

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, State> entry : Map.copyOf(states).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null || !player.isOnline() || !IsleWorld.onIsle(isle.plugin(), player)) {
                clear(entry.getKey());
                continue;
            }
            boolean changed = shine(player, state, now);
            boolean active = now - state.lastHarvest <= ACTIVE_TICKS;
            if (active && state.live.size() < MAX_LIVE && now >= state.nextSpawn) {
                spawn(player, state, now);
            }
            if (!active && state.streak > 0.0d) {
                state.streak = Math.max(0.0d, state.streak - IDLE_DRAIN);
                changed = true;
            }
            if (changed) {
                Tier before = state.tier;
                state.tier = Tier.of(state.streak);
                if (state.tier.ordinal() < before.ordinal() && state.tier == Tier.NONE) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.35f, 0.6f);
                }
            }
            if (!active && state.streak <= 0.0d && state.live.isEmpty()) {
                if (state.hideAt == 0) {
                    state.hideAt = now + HIDE_AFTER_TICKS;
                } else if (now >= state.hideAt) {
                    clear(entry.getKey());
                    continue;
                }
            }
            paint(player, state);
        }
    }

    /** Sparkle every live glint; drop the ones that faded or were taken. True when the streak moved. */
    private boolean shine(Player player, State state, int now) {
        boolean changed = false;
        Iterator<Glint> it = state.live.iterator();
        while (it.hasNext()) {
            Glint glint = it.next();
            if (!Crops.isCrop(glint.block.getType()) || !Crops.isMature(glint.block)) {
                // Someone else harvested it, or the row reset: no fault of yours.
                it.remove();
                remove(glint);
                continue;
            }
            if (now >= glint.until) {
                it.remove();
                remove(glint);
                state.streak = Math.max(0.0d, state.streak - MISS_COST);
                changed = true;
                Location at = glint.center();
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_STEP, SoundCategory.PLAYERS, 0.5f, 0.6f);
                player.spawnParticle(Particle.SMOKE, at, 3, 0.2, 0.2, 0.2, 0.0);
                continue;
            }
            Location at = glint.center();
            boolean fading = glint.until - now <= 15;
            player.spawnParticle(Particle.WAX_ON, at, fading ? 1 : 3, 0.3, 0.3, 0.3, 0.0);
            if (!fading) {
                player.spawnParticle(Particle.DUST, at.clone().add(0, 0.35, 0), 2, 0.2, 0.2, 0.2, 0.0, GOLD_DUST);
            }
        }
        return changed;
    }

    private void spawn(Player player, State state, int now) {
        Block block = choose(player, state);
        if (block == null) {
            state.nextSpawn = now + 10;
            return;
        }
        int window = windowTicks(player);
        Glint glint = new Glint(block, now + window, outline(player, block));
        state.live.add(glint);
        state.nextSpawn = now + SPAWN_EVERY_TICKS + ThreadLocalRandom.current().nextInt(16);
        Location at = glint.center();
        float pitch = 1.4f + ThreadLocalRandom.current().nextFloat() * 0.35f;
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, pitch);
        player.spawnParticle(Particle.WAX_ON, at, 8, 0.35, 0.35, 0.35, 0.0);
    }

    /** A ripe crop within reach, ahead of the player when there is one. */
    private Block choose(Player player, State state) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        int bx = eye.getBlockX();
        int by = player.getLocation().getBlockY();
        int bz = eye.getBlockZ();
        List<Block> ahead = new ArrayList<>();
        List<Block> around = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = world.getBlockAt(bx + dx, by + dy, bz + dz);
                    Material type = block.getType();
                    if (!Crops.isCrop(type) || !Crops.isMature(block) || state.at(block) != null) {
                        continue;
                    }
                    if (type == Material.SUGAR_CANE && block.getRelative(BlockFace.DOWN).getType() != Material.SUGAR_CANE) {
                        continue; // the root stays; only cane above it is a harvest
                    }
                    Vector to = block.getLocation().add(0.5d, 0.5d, 0.5d).toVector().subtract(eye.toVector());
                    double length = to.length();
                    if (length < 0.5d) {
                        continue;
                    }
                    (to.dot(look) / length > 0.35d ? ahead : around).add(block);
                }
            }
        }
        List<Block> pool = ahead.isEmpty() ? around : ahead;
        if (pool.isEmpty()) {
            return null;
        }
        Block block = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return IsleWorld.onIsle(isle.plugin(), block.getLocation()) ? block : null;
    }

    private int windowTicks(Player player) {
        double skill = 0.4d * FarmingSkills.scale(player, FarmingSkills.ROW_RHYTHM);
        return (int) Math.round(BASE_WINDOW_TICKS * (1.0d + skill) * isle.bakehouse().glintHold(player));
    }

    /** A golden outline of the crop, shown to this player only. */
    private BlockDisplay outline(Player player, Block block) {
        try {
            BlockDisplay display = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setSilent(true);
                d.addScoreboardTag(TAG);
                d.setBlock(block.getBlockData());
                d.setGlowing(true);
                d.setGlowColorOverride(GOLD);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.5f);
                d.setTransformation(new Transformation(new Vector3f(-0.01f, -0.01f, -0.01f), new Quaternionf(),
                        new Vector3f(1.02f, 1.02f, 1.02f), new Quaternionf()));
            });
            player.showEntity(isle.plugin(), display);
            return display;
        } catch (RuntimeException | LinkageError failed) {
            return null; // sparkles and the chime still mark it
        }
    }

    private static void remove(Glint glint) {
        if (glint.display != null && glint.display.isValid()) {
            glint.display.remove();
        }
    }

    /** Outlines are never saved, but a hard stop mid-shine can still leave one in a loaded chunk. */
    private void sweepStrays() {
        World world = IsleWorld.world(isle.plugin());
        if (world == null) {
            return;
        }
        for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
            if (display.getScoreboardTags().contains(TAG)) {
                display.remove();
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    // ------------------------------------------------------------------ feedback

    private void playNote(Player player, State state, Location at) {
        state.noteStep += state.direction;
        if (state.noteStep >= SCALE.length - 1 || state.noteStep <= 0) {
            state.direction = -state.direction;
            state.noteStep = Math.max(0, Math.min(SCALE.length - 1, state.noteStep));
        }
        float pitch = (float) Math.pow(2.0d, (SCALE[state.noteStep] - 12) / 12.0d);
        Sound sound = switch (state.tier) {
            case GLEAM -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case GOLDEN -> Sound.BLOCK_NOTE_BLOCK_BELL;
            default -> Sound.BLOCK_NOTE_BLOCK_PLING;
        };
        player.playSound(at, sound, SoundCategory.PLAYERS, 0.5f, pitch);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.45f, 1.6f);
    }

    private void tierUp(Player player, Tier tier) {
        String perk = tier.harvest > 0
                ? "+" + (int) tier.fortune + " crop Fortune · +" + (int) tier.harvest + " Harvest"
                : "+" + (int) tier.fortune + " crop Fortune";
        if (tier.extra > 1) {
            perk += " · 2 crops per glint";
        }
        IsleText.bar(player, tier.chat + "✦ " + tier.label + " §8· §7" + perk);
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.0f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.45f, 1.26f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4f, 1.5f);
        player.spawnParticle(Particle.WAX_ON, at.clone().add(0, 1.4, 0), 14, 0.5, 0.5, 0.5, 0.0);
        if (tier == Tier.GLEAM) {
            player.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 12, 0.5, 0.6, 0.5, 0.02);
        }
    }

    private void paint(Player player, State state) {
        float progress = (float) Math.max(0.0d, Math.min(1.0d, state.streak / Tier.GLEAM.from));
        String title = state.tier == Tier.NONE
                ? "§6✦ Ripe Glint §8· §7harvest the crops that shine gold §f" + (int) state.streak + "§8/§7" + Tier.KEEN.from
                : state.tier.chat + "✦ " + state.tier.label + " §f" + (int) state.streak
                + " §8· §7+" + (int) state.tier.fortune + " crop Fortune";
        if (state.bar == null) {
            state.bar = BossBar.bossBar(IsleText.legacy(BarChrome.legacy(BarChrome.Family.GATHER, BarChrome.Icon.WHEAT,
                    title)), progress, state.tier.color, BossBar.Overlay.NOTCHED_20);
            QuestBars.suppress(player, LEASE);
            player.showBossBar(state.bar);
            return;
        }
        state.bar.name(IsleText.legacy(BarChrome.legacy(BarChrome.Family.GATHER, BarChrome.Icon.WHEAT, title)));
        state.bar.progress(progress);
        state.bar.color(state.tier.color);
    }
}
