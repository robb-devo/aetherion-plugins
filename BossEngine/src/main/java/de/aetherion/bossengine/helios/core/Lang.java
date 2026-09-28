package de.aetherion.bossengine.helios.core;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * German or English per player: AetherionQuests' language choice when present (same probe as
 * {@code LootService}), else the client locale. Cached per player for a minute.
 */
public final class Lang {

    private static final Map<UUID, long[]> CACHE = new ConcurrentHashMap<>();

    private Lang() {
    }

    public static boolean german(Player p) {
        if (p == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        long[] hit = CACHE.get(p.getUniqueId());
        if (hit != null && now - hit[1] < 60_000L) {
            return hit[0] == 1L;
        }
        boolean de = probe(p);
        CACHE.put(p.getUniqueId(), new long[]{de ? 1L : 0L, now});
        return de;
    }

    public static String pick(Player p, String de, String en) {
        return german(p) ? de : en;
    }

    private static boolean probe(Player p) {
        try {
            Object code = Class.forName("de.aetherion.quests.lang.PlayerLang")
                    .getMethod("of", Player.class)
                    .invoke(null, p);
            if (code != null) {
                Object german = code.getClass().getMethod("german").invoke(code);
                if (german instanceof Boolean b) {
                    return b;
                }
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError | RuntimeException ignored) {
            // fall through to the client locale
        }
        Locale locale = p.locale();
        return locale == null || "de".equalsIgnoreCase(locale.getLanguage());
    }
}
