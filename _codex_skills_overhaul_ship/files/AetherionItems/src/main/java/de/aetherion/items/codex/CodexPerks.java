package de.aetherion.items.codex;

import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.entity.Player;

/**
 * Permanent milestone perks — the reason a full ledger feels like power, not just paperwork.
 *
 * <ul>
 *   <li>Collection: every 10 levels → +1 Fortune.</li>
 *   <li>Bestiary: every 10 levels → +1 Damage and +2 Health.</li>
 * </ul>
 * Levels are tiers reached, so perks arrive by playing (claiming only pays the tier rewards).
 * Turn off with {@code codex-perks: false} in the Items {@code config.yml}.
 */
public final class CodexPerks implements StatProvider {

    public static final double FORTUNE_PER_MILESTONE = 1.0d;
    public static final double DAMAGE_PER_MILESTONE = 1.0d;
    public static final double HEALTH_PER_MILESTONE = 2.0d;

    private static volatile boolean enabled = true;

    private final CodexService service;

    public CodexPerks(CodexService service, boolean enabled) {
        this.service = service;
        CodexPerks.enabled = enabled;
    }

    public static boolean enabled() {
        return enabled;
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (!enabled || player == null || capability == null) {
            return 0.0d;
        }
        return switch (capability) {
            case FORTUNE -> milestones(player, CodexBook.Ledger.COLLECTION) * FORTUNE_PER_MILESTONE;
            case DAMAGE -> milestones(player, CodexBook.Ledger.BESTIARY) * DAMAGE_PER_MILESTONE;
            case HEALTH -> milestones(player, CodexBook.Ledger.BESTIARY) * HEALTH_PER_MILESTONE;
            default -> 0.0d;
        };
    }

    private int milestones(Player player, CodexBook.Ledger ledger) {
        int level = CodexBook.primary()
                ? CodexBook.summary(service, player, ledger).level()
                : CodexBook.cachedLevel(player.getUniqueId(), ledger);
        return level / CodexTiers.MILESTONE_EVERY;
    }

    /** {@code +2 Fortune} / {@code +1 Damage · +2 Health} for {@code steps} milestones. */
    public static String perkLine(CodexBook.Ledger ledger, int steps) {
        if (!enabled) {
            return "§7perks are switched off on this server";
        }
        if (ledger == CodexBook.Ledger.COLLECTION) {
            return "§6+" + fmt(steps * FORTUNE_PER_MILESTONE) + " Fortune";
        }
        return "§c+" + fmt(steps * DAMAGE_PER_MILESTONE) + " Damage §8· §c+" + fmt(steps * HEALTH_PER_MILESTONE) + " Health";
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.valueOf(value);
    }
}
