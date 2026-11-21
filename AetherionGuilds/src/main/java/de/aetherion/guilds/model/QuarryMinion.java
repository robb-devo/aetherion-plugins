package de.aetherion.guilds.model;

import java.util.Locale;
import java.util.UUID;

public final class QuarryMinion {

    public static final String COBBLE = QuarryType.COBBLESTONE.id();
    public static final int MAX_LEVEL = QuarryType.MAX_LEVEL;
    /** Same 128/128 chain as EconomyCurve / Craft / Pocket Forge. */
    public static final int COMPRESS_UNIT = 128;
    public static final int COMPACT_UNIT = 128 * 128;

    public enum Processor {
        NONE,
        COMPRESSED,
        COMPACTED;

        public static Processor parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }

    public record StorageView(int raw, int compressed, int compacted, int cap) {
        public boolean isEmpty() {
            return raw <= 0 && compressed <= 0 && compacted <= 0;
        }

        public int rawEquivalent() {
            return raw + compressed * COMPRESS_UNIT + compacted * COMPACT_UNIT;
        }
    }

    private final UUID id;
    private final String type;
    private int x;
    private int y;
    private int z;
    private long lastTick;
    private int stored;
    private int storedCompressed;
    private int storedCompacted;
    private int level;
    private Processor processor;
    private UUID visualId;

    public QuarryMinion(UUID id, String type, int x, int y, int z, long lastTick, int stored, int level) {
        this(id, type, x, y, z, lastTick, stored, level, Processor.NONE, 0, 0);
    }

    public QuarryMinion(
            UUID id,
            String type,
            int x,
            int y,
            int z,
            long lastTick,
            int stored,
            int level,
            Processor processor
    ) {
        this(id, type, x, y, z, lastTick, stored, level, processor, 0, 0);
    }

    public QuarryMinion(
            UUID id,
            String type,
            int x,
            int y,
            int z,
            long lastTick,
            int stored,
            int level,
            Processor processor,
            int storedCompressed,
            int storedCompacted
    ) {
        this.id = id;
        this.type = type == null || type.isBlank() ? COBBLE : type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastTick = lastTick;
        this.stored = Math.max(0, stored);
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
        this.processor = processor == null ? Processor.NONE : processor;
        this.storedCompressed = Math.max(0, storedCompressed);
        this.storedCompacted = Math.max(0, storedCompacted);
    }

    public UUID id() {
        return id;
    }

    public String type() {
        return type;
    }

    public QuarryType quarryType() {
        return QuarryType.fromId(type);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public long lastTick() {
        return lastTick;
    }

    public int stored() {
        return stored;
    }

    public int storedCompressed() {
        return storedCompressed;
    }

    public int storedCompacted() {
        return storedCompacted;
    }

    public int level() {
        return level;
    }

    public Processor processor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor == null ? Processor.NONE : processor;
    }

    public UUID visualId() {
        return visualId;
    }

    public void setVisualId(UUID visualId) {
        this.visualId = visualId;
    }

    /** Session-only speed factor (stage 0 of a personal island: the first quarry digs faster while you learn). */
    private transient int boost = 1;

    public int boost() {
        return boost;
    }

    public void setBoost(int boost) {
        this.boost = Math.max(1, Math.min(50, boost));
    }

    public int perTick() {
        return quarryType().perTick(level) * boost;
    }

    /** Session-only bonus in percent on top (the guild's Harbour Beacon: +10 % on members' own islands). */
    private transient int bonusPct;
    /** The bonus's fractions, carried so small quarries get their share too. */
    private transient double bonusCarry;

    public int bonusPct() {
        return bonusPct;
    }

    public void setBonusPct(int bonusPct) {
        this.bonusPct = Math.max(0, Math.min(100, bonusPct));
    }

    /** Per tick with the bonus, for rates on menus. */
    public double ratePerTick() {
        return perTick() * (1.0 + bonusPct / 100.0);
    }

    /** What {@code steps} dig steps produce, bonus included (the bonus's fractions carry over). */
    public long produced(long steps) {
        long base = steps * (long) perTick();
        if (bonusPct <= 0 || base <= 0L) {
            return base;
        }
        double extra = base * (bonusPct / 100.0) + bonusCarry;
        long whole = (long) extra;
        bonusCarry = extra - whole;
        return base + whole;
    }

    public int cap() {
        return quarryType().cap(level);
    }

    public int rawEquivalent() {
        return stored + storedCompressed * COMPRESS_UNIT + storedCompacted * COMPACT_UNIT;
    }

    public boolean isEmpty() {
        return stored <= 0 && storedCompressed <= 0 && storedCompacted <= 0;
    }

    public void setLastTick(long lastTick) {
        this.lastTick = lastTick;
    }

    public void setStored(int stored) {
        this.stored = Math.max(0, stored);
    }

    public void setStoredCompressed(int storedCompressed) {
        this.storedCompressed = Math.max(0, storedCompressed);
    }

    public void setStoredCompacted(int storedCompacted) {
        this.storedCompacted = Math.max(0, storedCompacted);
    }

    public void addStored(int amount, int cap) {
        int room = Math.max(0, cap - rawEquivalent());
        this.stored += Math.min(room, Math.max(0, amount));
    }

    public void processStorage() {
        if (processor == Processor.COMPACTED) {
            int packed = stored / COMPACT_UNIT;
            storedCompacted += packed;
            stored -= packed * COMPACT_UNIT;
            int compressed = stored / COMPRESS_UNIT;
            storedCompressed += compressed;
            stored -= compressed * COMPRESS_UNIT;
        } else if (processor == Processor.COMPRESSED) {
            int compressed = stored / COMPRESS_UNIT;
            storedCompressed += compressed;
            stored -= compressed * COMPRESS_UNIT;
        }
    }

    public StorageView view() {
        return new StorageView(stored, storedCompressed, storedCompacted, cap());
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
    }

    public boolean canUpgrade() {
        return level < MAX_LEVEL;
    }

    public QuarryType.UpgradeCost nextUpgradeCost() {
        return quarryType().upgradeCost(level);
    }
}
