package de.aetherion.items.item;

import de.aetherion.items.model.Rarity;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds each rarity to {@code aetherion:<rarity>} tooltip sprites in the resource pack.
 * Requires Minecraft/Paper 1.21.2+ ({@code tooltip_style}); no-ops on older APIs.
 */
public final class TooltipStyle {

    private static final Logger LOGGER = Logger.getLogger("AetherionItems");
    private static final String NAMESPACE = "aetherion";

    private static final Method SET_TOOLTIP_STYLE;
    private static final boolean SUPPORTED;
    private static boolean loggedMissing;

    static {
        Method method = null;
        try {
            method = ItemMeta.class.getMethod("setTooltipStyle", NamespacedKey.class);
        } catch (NoSuchMethodException ignored) {
            method = null;
        }
        SET_TOOLTIP_STYLE = method;
        SUPPORTED = method != null;
    }

    private TooltipStyle() {
    }

    public static void apply(ItemMeta meta, Rarity rarity) {
        if (meta == null || rarity == null || !SUPPORTED) {
            return;
        }
        NamespacedKey key = new NamespacedKey(NAMESPACE, rarity.name().toLowerCase(Locale.ROOT));
        try {
            SET_TOOLTIP_STYLE.invoke(meta, key);
        } catch (ReflectiveOperationException ex) {
            if (!loggedMissing) {
                loggedMissing = true;
                LOGGER.log(Level.FINE, "Could not apply tooltip style " + key, ex);
            }
        }
    }

    public static void clear(ItemMeta meta) {
        if (meta == null || !SUPPORTED) {
            return;
        }
        try {
            SET_TOOLTIP_STYLE.invoke(meta, new Object[]{null});
        } catch (ReflectiveOperationException ignored) {
            // older / unsupported
        }
    }
}
