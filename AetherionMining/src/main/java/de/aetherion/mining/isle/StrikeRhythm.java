package de.aetherion.mining.isle;

import de.aetherion.core.api.QuestBars;

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
 * Strike Rhythm — the miner's work song. Keep breaking rock without a pause and the mountain
 * starts to answer. Every strike adds rhythm (rarer ore adds more), idling drains it. Tiers pay
 * ore-only Fortune, Spread and break speed, and each strike plays the next beat of a four-count
 * work song: a hammer on the one, a minor-pentatonic ring on the rest.
 *
 * <p>On top of the meter runs the <b>Seam Chain</b>: break the same ore family back to back and
 * the chain grows; at 8 (Seam Reader: fewer) the seam bursts — a handful of extra ore, a rhythm
 * kick and a spray of sparks in the ore's colour. Stone between strikes doesn't break a chain.
 */
public final class StrikeRhythm {

    static final String LEASE = "strike-rhythm";
    private static final double MAX = 100.0d;
    /** Ticks between strikes that still count as "in time" — ore takes longer than wheat. */
    private static final int BASE_HOLD_TICKS = 45;
    private static final double DRAIN_PER_STEP = 6.0d;
    private static final int HIDE_AFTER_TICKS = 50;
    /** Minor pentatonic, two octaves — the ring of a pick on good rock. */
    private static final int[] SCALE = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24};
    private static final int BASE_SEAM = 8;

    public enum Tier {
        NONE(0, "", 0, 0, 0, BossBar.Color.WHITE, "§7"),
        STEADY(20, "Steady Pick", 10, 0, 6, BossBar.Color.GREEN, "§a"),
        SEAM(50, "In the Seam", 25, 6, 14, BossBar.Color.YELLOW, "§e"),
        CHORUS(100, "Anvil Chorus", 50, 12, 25, BossBar.Color.RED, "§6");

        final int from;
        final String label;
        final double fortune;
        final double spread;
        final double speedPower;
        final BossBar.Color color;
        final String chat;

        Tier(int from, String label, double fortune, double spread, double speedPower, BossBar.Color color, String chat) {
            this.from = from;
            this.label = label;
            this.fortune = fortune;
            this.spread = spread;
            this.speedPower = speedPower;
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

        public String chat() {
            return chat;
        }

        public double fortune() {
            return fortune;
        }
    }

    private static final class State {
        double stacks;
        int lastTick;
        int beat;
        int noteStep;
        int direction = 1;
        Tier tier = Tier.NONE;
        BossBar bar;
        int hideAt;
        double best;
        IsleOre seamOre;
        int seam;
        int bursts;
    }

    private final MineIsle isle;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    StrikeRhythm(MineIsle isle) {
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
        return isle.plugin().getConfig().getBoolean("strike-rhythm.enabled", true);
    }

    /** One hand-mined stone / ore block on the isle (never the Emerald Spread neighbours). */
    void onStrike(Player player, IsleOre ore, Location at) {
        if (!enabled()) {
            return;
        }
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        int now = Bukkit.getCurrentTick();
        double weight = ore == IsleOre.STONE ? 0.5d : ore.rare() ? 2.0d : 1.0d;
        double gain = weight * (1.0d + 0.5d * MineSkills.scale(player, MineSkills.WORK_SONG))
                + isle.hearth().rhythmGain(player);
        state.stacks = Math.min(MAX, state.stacks + gain);
        state.lastTick = now;
        state.hideAt = 0;
        Tier before = state.tier;
        state.tier = Tier.of(state.stacks);
        playBeat(player, state, at);
        if (ore != IsleOre.STONE) {
            seam(player, state, ore, at);
        }
        state.best = Math.max(state.best, state.stacks);
        if (state.tier.ordinal() > before.ordinal()) {
            tierUp(player, state.tier);
        }
        if (state.tier == Tier.CHORUS && state.beat % 4 == 0) {
            MineSkills.bonus(player, 1);
        }
        paint(player, state);
    }

    // ------------------------------------------------------------------ reads

    public Tier tier(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? Tier.NONE : state.tier;
    }

    public double stacks(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0.0d : state.stacks;
    }

    public double best(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0.0d : state.best;
    }

    public int seam(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0 : state.seam;
    }

    public int bursts(Player player) {
        State state = player == null ? null : states.get(player.getUniqueId());
        return state == null ? 0 : state.bursts;
    }

    double oreFortune(Player player) {
        return tier(player).fortune;
    }

    double spread(Player player) {
        return tier(player).spread;
    }

    double speedPower(Player player) {
        return tier(player).speedPower;
    }

    /** Strikes in one family that burst the seam (Seam Reader and the Seam Hook mark lower it, floor 4). */
    int seamLength(Player player) {
        double scale = MineSkills.scale(player, MineSkills.SEAM_READER);
        return (int) Math.max(4.0d, BASE_SEAM - Math.floor(scale * 1.5d) - isle.forge().seamReduction(player));
    }

    /** DEV: straight to Anvil Chorus. */
    void max(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.stacks = MAX;
        state.lastTick = Bukkit.getCurrentTick() + 20 * 20;
        state.tier = Tier.CHORUS;
        state.best = MAX;
        tierUp(player, Tier.CHORUS);
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

    // ------------------------------------------------------------------ seam chain

    private void seam(Player player, State state, IsleOre ore, Location at) {
        if (state.seamOre != ore) {
            state.seamOre = ore;
            state.seam = 0;
        }
        state.seam++;
        int need = seamLength(player);
        if (state.seam < need) {
            return;
        }
        state.seam = 0;
        state.bursts++;
        int extra = 1 + (ore.rare() ? 0 : Math.min(3, MineSkills.boostTier(player) / 2 + 1));
        MineSkills.give(player, new org.bukkit.inventory.ItemStack(ore.resource(), extra));
        state.stacks = Math.min(MAX, state.stacks + 4.0d);
        MineSkills.bonus(player, 6);
        Location spot = at == null ? player.getLocation() : at.clone().add(0.5, 0.5, 0.5);
        player.spawnParticle(Particle.DUST, spot, 18, 0.35, 0.35, 0.35, 0.0,
                new Particle.DustOptions(ore.glow(), 1.3f));
        player.spawnParticle(Particle.ELECTRIC_SPARK, spot, 10, 0.3, 0.3, 0.3, 0.05);
        player.playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.playSound(spot, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.18f, 1.8f);
        MineText.bar(player, ore.color() + "⛏ Seam Burst! §f+" + extra + " " + ore.display()
                + " §8· §7+4 rhythm · +6 Mining XP");
        isle.events().onSeamBurst(player);
        isle.critters().onSeamBurst(player, at);
    }

    // ------------------------------------------------------------------ ticking

    private int holdTicks(Player player) {
        double skill = 0.4d * MineSkills.scale(player, MineSkills.WORK_SONG);
        return (int) Math.round(BASE_HOLD_TICKS * (1.0d + skill) * isle.hearth().rhythmHold(player));
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, State> entry : Map.copyOf(states).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null || !player.isOnline() || !MineWorld.onIsle(isle.plugin(), player)) {
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
                state.seam = 0;
                if (before != Tier.NONE) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.35f, 0.5f);
                }
            }
            paint(player, state);
        }
    }

    /** Four-count: the one is a hammer, the other three ring up and down the scale. */
    private void playBeat(Player player, State state, Location at) {
        state.beat++;
        if (state.stacks < 5.0d) {
            return;
        }
        Location ear = at == null ? player.getLocation() : at.clone().add(0.5, 0.5, 0.5);
        if (state.beat % 4 == 1) {
            player.playSound(ear, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.PLAYERS, 0.45f, 0.7f);
            if (state.tier.ordinal() >= Tier.SEAM.ordinal()) {
                player.playSound(ear, Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.08f, 1.6f);
            }
            return;
        }
        state.noteStep += state.direction;
        if (state.noteStep >= SCALE.length - 1 || state.noteStep <= 0) {
            state.direction = -state.direction;
            state.noteStep = Math.max(0, Math.min(SCALE.length - 1, state.noteStep));
        }
        float pitch = (float) Math.pow(2.0d, (SCALE[state.noteStep] - 12) / 12.0d);
        Sound sound = switch (state.tier) {
            case CHORUS -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case SEAM -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            default -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
        };
        player.playSound(ear, sound, SoundCategory.PLAYERS, state.tier == Tier.CHORUS ? 0.4f : 0.3f, pitch);
        if (state.tier.ordinal() >= Tier.SEAM.ordinal()) {
            player.spawnParticle(Particle.WAX_OFF, ear.clone().add(0, 0.4, 0), 1, 0.15, 0.15, 0.15, 0.0);
        }
    }

    private void tierUp(Player player, Tier tier) {
        String perk = "+" + (int) tier.fortune + " ore Fortune";
        if (tier.spread > 0) {
            perk += " · +" + (int) tier.spread + " Spread";
        }
        perk += " · faster breaks";
        if (tier == Tier.CHORUS) {
            perk += " · bonus Mining XP";
        }
        MineText.bar(player, tier.chat + "⚒ " + tier.label + " §8· §7" + perk);
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.25f, 1.4f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.5f, 0.84f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.45f, 1.0f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.4f, 1.26f);
        player.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1.6, 0), 14, 0.5, 0.3, 0.5, 0.08);
        if (tier == Tier.CHORUS) {
            player.spawnParticle(Particle.LAVA, at.clone().add(0, 1.2, 0), 6, 0.4, 0.3, 0.4, 0.0);
        }
    }

    private void paint(Player player, State state) {
        float progress = (float) Math.max(0.0d, Math.min(1.0d, state.stacks / MAX));
        String seam = state.seamOre != null && state.seam > 0
                ? " §8· " + state.seamOre.color() + "Seam " + state.seam + "/" + seamLength(player)
                : "";
        String title = state.tier == Tier.NONE
                ? "§7⚒ Strike Rhythm §f" + (int) state.stacks + seam
                : state.tier.chat + "⚒ " + state.tier.label + " §f" + (int) state.stacks
                + " §8· §7+" + (int) state.tier.fortune + " ore Fortune" + seam;
        if (state.bar == null) {
            state.bar = BossBar.bossBar(MineText.legacy(title), progress, state.tier.color, BossBar.Overlay.NOTCHED_10);
            QuestBars.suppress(player, LEASE);
            player.showBossBar(state.bar);
            return;
        }
        state.bar.name(MineText.legacy(title));
        state.bar.progress(progress);
        state.bar.color(state.tier.color);
    }
}
