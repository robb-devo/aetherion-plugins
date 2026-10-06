package de.aetherion.quests.dialog;

import de.aetherion.quests.talk.TalkText;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one speech clock for every spoken NPC line — DialogManager intros, topic answers,
 * {@code LivingNpcProfile.say} queues, guest speakers and the talk bubble's typewriter.
 * <p>
 * Rhythm: every line types at the same speed ({@link #charsPerTick()}), then the reader gets
 * the same pause ({@link #readGapTicks()}) before the next line may start, at the start of a
 * conversation and in the middle of it alike. A line's slot is therefore
 * {@code typeTicks(line) + readGap}, not a fixed beat that cuts long lines short and drags
 * short ones.
 * <p>
 * Knobs live under {@code talk-ux:} ({@code chars-per-tick}, {@code read-gap-ticks}); code
 * defaults apply when the live config doesn't set them.
 */
public final class DialogPace {

    /**
     * Nominal beat of one short line (~3.0s). Kept for follow-ups that don't know the text
     * they're waiting on; anything that does should use {@link #beat(String)} / {@link #untilFree(Player)}.
     */
    public static final long LINE_GAP_TICKS = 60L;

    /** Bubble wrap width — must match {@code TalkUx.line} so typing time is exact. */
    public static final int WRAP_PX = 168;

    private static final double DEFAULT_CHARS_PER_TICK = 1.5;
    private static final long DEFAULT_READ_GAP_TICKS = 36L;
    /** Even "Hm." gets a beat to land. */
    private static final long MIN_TYPE_TICKS = 6L;

    private static volatile double charsPerTick = DEFAULT_CHARS_PER_TICK;
    private static volatile long readGapTicks = DEFAULT_READ_GAP_TICKS;

    /** Reply chips land this long after the last line has typed out (the read gap is for lines). */
    public static final long REPLY_LAG_TICKS = 8L;

    /** Per player: when the last booked line is fully typed, and when the clock is free again. */
    private record Clock(long typedAt, long freeAt) {
    }

    private static final Map<UUID, Clock> CLOCKS = new ConcurrentHashMap<>();

    private DialogPace() {
    }

    /** Read the knobs (called from {@code TalkUx.reload}). */
    public static void configure(double cpt, long gap) {
        charsPerTick = Math.max(0.5, Math.min(6.0, cpt));
        readGapTicks = Math.max(10L, Math.min(200L, gap));
    }

    /** Typewriter speed — the same for every NPC and every line. */
    public static double charsPerTick() {
        return charsPerTick;
    }

    /** Pause after a line has finished typing, before the next one may start. */
    public static long readGapTicks() {
        return readGapTicks;
    }

    /** How long the bubble needs to type {@code line} (already filled, legacy colours allowed). */
    public static long typeTicks(String line) {
        if (line == null || line.isBlank()) {
            return MIN_TYPE_TICKS;
        }
        int visible = TalkText.visibleLength(TalkText.wrap("§f" + line, WRAP_PX));
        return Math.max(MIN_TYPE_TICKS, (long) Math.ceil(visible / charsPerTick));
    }

    /** Full slot of one line: type it out, then let it be read. */
    public static long beat(String line) {
        return typeTicks(line) + readGapTicks;
    }

    /** Slot for a line with no in-world text (blank chat spacer etc.). */
    public static long quietBeat() {
        return readGapTicks;
    }

    /**
     * Book the next slot on {@code player}'s clock for {@code line}. Returns the delay in ticks
     * (0 = say it now) after which the line may start; the clock then stays busy until the line
     * has typed out and been read.
     */
    public static long reserve(Player player, String line) {
        if (player == null) {
            return 0L;
        }
        long now = now();
        Clock clock = CLOCKS.get(player.getUniqueId());
        long start = clock == null ? now : Math.max(now, clock.freeAt());
        long typed = start + typeTicks(line);
        CLOCKS.put(player.getUniqueId(), new Clock(typed, typed + readGapTicks));
        return start - now;
    }

    /** A line is being spoken right now (outside {@link #reserve}): hold the clock for its slot. */
    public static void mark(Player player, String line) {
        if (player == null) {
            return;
        }
        long now = now();
        long typed = now + (line == null ? 0L : typeTicks(line));
        long free = typed + readGapTicks;
        CLOCKS.merge(player.getUniqueId(), new Clock(typed, free),
                (a, b) -> a.freeAt() >= b.freeAt() ? a : b);
    }

    /** Ticks until {@code player} has nothing queued or being read (0 = free). */
    public static long untilFree(Player player) {
        if (player == null) {
            return 0L;
        }
        Clock clock = CLOCKS.get(player.getUniqueId());
        return clock == null ? 0L : Math.max(0L, clock.freeAt() - now());
    }

    /** Ticks until reply chips may appear: right after the last booked line has typed out. */
    public static long untilReplies(Player player) {
        if (player == null) {
            return 0L;
        }
        Clock clock = CLOCKS.get(player.getUniqueId());
        if (clock == null) {
            return 0L;
        }
        long now = now();
        return clock.freeAt() <= now ? 0L : Math.max(0L, clock.typedAt() + REPLY_LAG_TICKS - now);
    }

    /**
     * The player answered (picked a reply): they've read what's on screen, so the read gap of a
     * fully typed line is over. Lines still queued keep their slots.
     */
    public static void release(Player player) {
        if (player == null) {
            return;
        }
        long now = now();
        CLOCKS.computeIfPresent(player.getUniqueId(),
                (k, c) -> c.typedAt() <= now ? null : c);
    }

    /** Conversation dropped (walked off, quit, new dialog took over): clear the clock. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            CLOCKS.remove(playerId);
        }
    }

    private static long now() {
        return Bukkit.getCurrentTick();
    }
}
