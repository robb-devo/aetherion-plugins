package de.aetherion.core.api;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Change-only setters for Bukkit boss bars.
 *
 * <p>{@code CraftBossBar.setTitle / setColor / setStyle} re-broadcast to every viewer on each call, even when
 * nothing changed, and the per-tick HUDs (fishing cast, fell, pet catch, quest compass, hints) call them every
 * tick. These skip the call when the value is already showing. Adventure bars compare on their own.
 *
 * <p>The last title is remembered per bar, so a bar must get every title through {@link #title}.
 */
public final class BarSync {

    private static final Map<BossBar, String> TITLES = Collections.synchronizedMap(new WeakHashMap<>());

    private BarSync() {
    }

    public static void title(BossBar bar, String title) {
        if (bar == null) {
            return;
        }
        String next = title == null ? "" : title;
        if (!Objects.equals(TITLES.put(bar, next), next)) {
            bar.setTitle(next);
        }
    }

    public static void progress(BossBar bar, double progress) {
        if (bar == null) {
            return;
        }
        double next = Double.isNaN(progress) ? 0.0d : Math.max(0.0d, Math.min(1.0d, progress));
        // The server keeps a float.
        if ((float) bar.getProgress() != (float) next) {
            bar.setProgress(next);
        }
    }

    public static void color(BossBar bar, BarColor color) {
        if (bar != null && color != null && bar.getColor() != color) {
            bar.setColor(color);
        }
    }

    public static void style(BossBar bar, BarStyle style) {
        if (bar != null && style != null && bar.getStyle() != style) {
            bar.setStyle(style);
        }
    }

    public static void visible(BossBar bar, boolean visible) {
        if (bar != null && bar.isVisible() != visible) {
            bar.setVisible(visible);
        }
    }
}
