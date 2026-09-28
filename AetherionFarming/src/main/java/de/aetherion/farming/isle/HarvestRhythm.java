package de.aetherion.farming.isle;

import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harvest Rhythm — keep harvesting mature crops without a pause and the field starts to sing.
 * Every swing adds rhythm, idling drains it. Tiers pay crop-only Fortune and Harvest, and every
 * harvest plays the next note of a pentatonic run so a good streak literally sounds like one.
 */
public final class HarvestRhythm {

    static final String LEASE = "harvest-rhythm";
    private static final double MAX = 100.0d;
    /** Ticks between harvests that still count as "in time". */
    private static final int BASE_HOLD_TICKS = 30;
    /** Rhythm lost per 5-tick step once out of time. */
    private static final double DRAIN_PER_STEP = 7.0d;
    private static final int HIDE_AFTER_TICKS = 50;
    /** Semitone offsets of a major pentatonic run over two octaves — note-block pitch space. */
    private static final int[] SCALE = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};

    public enum Tier {
        NONE(0, "", 0, 0, BossBar.Color.WHITE, "§7"),
        STEADY(20, "Steady", 10, 0, BossBar.Color.GREEN, "§a"),
        GROOVE(50, "In the Groove", 25, 10, BossBar.Color.YELLOW, "§e"),
        SONG(100, "Harvest Song", 50, 25, BossBar.Color.PINK, "§d");

        final int from;
        final String label;
        final double fortune;
        final double harvest;
        final BossBar.Color color;
        final String chat;

        Tier(int from, String label, double fortune, double harvest, BossBar.Color color, String chat) {
            this.from = from;
            this.label = label;
            this.fortune = fortune;
            this.harvest = harvest;
            this.color = color;
            this.chat = chat;
        }

        static Tier of(double stacks) {
            Tier best = NONE;
            for (Tier tier : values()) {
                if (stacks >= tier.from) {
                    best = tier;
                }
            }
            return best;
        }

        public String label() {
            return label;
        }
    }

    private static final class State {
        double stacks;
        int lastTick;
        int noteStep;
        int direction = 1;
        Tier tier = Tier.NONE;
        BossBar bar;
        int hideAt;
        double best;
    }

    private final FarmIsle isle;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    HarvestRhythm(FarmIsle isle) {
        this.isle = isle;
    }

    void start() {
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
        return isle.plugin().getConfig().getBoolean("harvest-rhythm.enabled", true);
    }

    /** One mature crop harvested by hand (not the Harvest spread). */
    void onHarvest(Player player, Location at) {
        if (!enabled()) {
            return;
        }
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        int now = Bukkit.getCurrentTick();
        double gain = 1.0d + 0.5d * FarmingSkills.scale(player, FarmingSkills.ROW_RHYTHM)
                + isle.bakehouse().rhythmGain(player);
        state.stacks = Math.min(MAX, state.stacks + gain);
        state.lastTick = now;
        state.hideAt = 0;
        state.best = Math.max(state.best, state.stacks);
        Tier before = state.tier;
        state.tier = Tier.of(state.stacks);
        playNote(player, state, at);
        if (state.tier.ordinal() > before.ordinal()) {
            tierUp(player, state.tier);
        }
        if (state.tier == Tier.SONG) {
            FarmingSkills.bonus(player, 1);
        }
        paint(player, state);
    }

    public Tier tier(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? Tier.NONE : state.tier;
    }

    public double stacks(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0.0d : state.stacks;
    }

    /** Best rhythm this session. */
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

    /** DEV: jump straight to Harvest Song. */
    void max(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.stacks = MAX;
        state.lastTick = Bukkit.getCurrentTick() + 20 * 20;
        state.tier = Tier.SONG;
        state.best = MAX;
        tierUp(player, Tier.SONG);
        paint(player, state);
    }

    void clear(UUID id) {
        State state = states.remove(id);
        if (state != null && state.bar != null) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(state.bar);
            }
        }
        QuestBars.release(id, LEASE);
    }

    private int holdTicks(Player player) {
        double skill = 0.4d * FarmingSkills.scale(player, FarmingSkills.ROW_RHYTHM);
        return (int) Math.round(BASE_HOLD_TICKS * (1.0d + skill) * isle.bakehouse().rhythmHold(player));
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, State> entry : Map.copyOf(states).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null || !player.isOnline() || !IsleWorld.onIsle(isle.plugin(), player)) {
                clear(entry.getKey());
                continue;
            }
            if (state.stacks <= 0.0d) {
                if (state.hideAt > 0 && now >= state.hideAt) {
                    clear(entry.getKey());
                }
                continue;
            }
            if (now - state.lastTick <= holdTicks(player)) {
                continue;
            }
            Tier before = state.tier;
            state.stacks = Math.max(0.0d, state.stacks - DRAIN_PER_STEP);
            state.tier = Tier.of(state.stacks);
            if (state.stacks <= 0.0d) {
                state.hideAt = now + HIDE_AFTER_TICKS;
                if (before != Tier.NONE) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.35f, 0.6f);
                }
            }
            paint(player, state);
        }
    }

    private void playNote(Player player, State state, Location at) {
        if (state.stacks < 5.0d) {
            return;
        }
        state.noteStep += state.direction;
        if (state.noteStep >= SCALE.length - 1 || state.noteStep <= 0) {
            state.direction = -state.direction;
            state.noteStep = Math.max(0, Math.min(SCALE.length - 1, state.noteStep));
        }
        float pitch = (float) Math.pow(2.0d, (SCALE[state.noteStep] - 12) / 12.0d);
        Sound sound = switch (state.tier) {
            case SONG -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case GROOVE -> Sound.BLOCK_NOTE_BLOCK_BELL;
            default -> Sound.BLOCK_NOTE_BLOCK_PLING;
        };
        Location ear = at == null ? player.getLocation() : at.clone().add(0.5, 0.5, 0.5);
        player.playSound(ear, sound, SoundCategory.PLAYERS, state.tier == Tier.SONG ? 0.42f : 0.3f, pitch);
        if (state.tier == Tier.SONG && state.noteStep % 3 == 0) {
            player.playSound(ear, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.PLAYERS, 0.22f, pitch * 0.5f);
        }
        if (state.tier.ordinal() >= Tier.GROOVE.ordinal()) {
            player.spawnParticle(Particle.NOTE, ear.clone().add(0, 0.6, 0), 1, 0.1, 0.1, 0.1, state.noteStep / 24.0d);
        }
    }

    private void tierUp(Player player, Tier tier) {
        String perk = tier.harvest > 0
                ? "+" + (int) tier.fortune + " crop Fortune · +" + (int) tier.harvest + " Harvest"
                : "+" + (int) tier.fortune + " crop Fortune";
        if (tier == Tier.SONG) {
            perk += " · bonus Farming XP";
        }
        IsleText.bar(player, tier.chat + "♪ " + tier.label + " §8· §7" + perk);
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.0f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.45f, 1.26f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4f, 1.5f);
        player.spawnParticle(Particle.NOTE, at.clone().add(0, 2.1, 0), 6, 0.5, 0.2, 0.5, 1.0);
        if (tier == Tier.SONG) {
            player.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 12, 0.5, 0.6, 0.5, 0.02);
        }
    }

    private void paint(Player player, State state) {
        float progress = (float) Math.max(0.0d, Math.min(1.0d, state.stacks / MAX));
        String title = state.tier == Tier.NONE
                ? "§7♪ Harvest Rhythm §f" + (int) state.stacks
                : state.tier.chat + "♪ " + state.tier.label + " §f" + (int) state.stacks
                + " §8· §7+" + (int) state.tier.fortune + " crop Fortune";
        if (state.bar == null) {
            state.bar = BossBar.bossBar(IsleText.legacy(title), progress, state.tier.color, BossBar.Overlay.NOTCHED_10);
            QuestBars.suppress(player, LEASE);
            player.showBossBar(state.bar);
            return;
        }
        state.bar.name(IsleText.legacy(title));
        state.bar.progress(progress);
        state.bar.color(state.tier.color);
    }
}
