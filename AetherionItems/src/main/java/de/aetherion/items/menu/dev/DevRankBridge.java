package de.aetherion.items.menu.dev;

import de.aetherion.core.api.TestBotView;
import de.aetherion.items.rank.RankBadgeService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DEV-side bridges to APIs that only exist in some jar lineages: the special-rank backend
 * (Monkey / Citrus / Beta, {@code isRobb}, {@code clearExtra}) and the richer testbot view
 * (hp · food · profile · target · inventory). Reflection keeps the menu compiling on every
 * lineage and lights the buttons up when the backend is present — never a silent fallback.
 */
final class DevRankBridge {

    /** Homie special ranks the ready jar offered. Only actionable when RankBadgeService knows them. */
    static final List<String> HOMIE_EXTRAS = List.of("monkey", "citrus", "beta");

    private DevRankBridge() {
    }

    static boolean isHomieExtra(String group) {
        return group != null && HOMIE_EXTRAS.contains(group.toLowerCase(Locale.ROOT));
    }

    static boolean isRobb(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        try {
            Method method = RankBadgeService.class.getMethod("isRobb", UUID.class);
            return Boolean.TRUE.equals(method.invoke(null, playerId));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** @return true when the backend removed the ultra. */
    static boolean clearExtra(RankBadgeService ranks, UUID playerId, String group) {
        if (ranks == null || playerId == null) {
            return false;
        }
        try {
            Method method = ranks.getClass().getMethod("clearExtra", UUID.class, String.class);
            Object result = method.invoke(ranks, playerId, group);
            return !(result instanceof Boolean ok) || ok;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Optional testbot fields (Core WIP) — blank when this Core predates them. */
    static String botText(TestBotView bot, String accessor) {
        Object value = invoke(bot, accessor);
        return value == null ? "" : String.valueOf(value);
    }

    static double botNumber(TestBotView bot, String accessor) {
        Object value = invoke(bot, accessor);
        return value instanceof Number number ? number.doubleValue() : -1;
    }

    private static Object invoke(Object target, String accessor) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
