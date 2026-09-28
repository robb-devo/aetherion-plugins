package de.aetherion.bossengine.instance;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Template id → script factory. A module registers its scripts on enable; every {@link BossInstance}
 * built from that template afterwards is driven by a fresh script.
 */
public final class BossScripts {

    private static final Map<String, Function<BossInstance, BossScript>> FACTORIES = new ConcurrentHashMap<>();

    private BossScripts() {
    }

    public static void register(String templateId, Function<BossInstance, BossScript> factory) {
        if (templateId != null && factory != null) {
            FACTORIES.put(templateId.toLowerCase(Locale.ROOT), factory);
        }
    }

    public static void unregister(String templateId) {
        if (templateId != null) {
            FACTORIES.remove(templateId.toLowerCase(Locale.ROOT));
        }
    }

    static BossScript create(BossInstance instance) {
        if (instance.getTemplate() == null || instance.getTemplate().getId() == null) {
            return null;
        }
        Function<BossInstance, BossScript> factory = FACTORIES.get(instance.getTemplate().getId().toLowerCase(Locale.ROOT));
        return factory == null ? null : factory.apply(instance);
    }
}
