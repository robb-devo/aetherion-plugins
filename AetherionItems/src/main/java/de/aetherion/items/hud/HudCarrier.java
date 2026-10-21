package de.aetherion.items.hud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * One invisible boss bar per player whose title is the whole HUD (status frame + radar).
 *
 * <p>Why a boss bar: the client fades an ActionBar two seconds after the last packet and any other
 * ActionBar replaces it, so a HUD on it blinks on lag and whenever something else talks. A boss bar title
 * stays until it is changed. The bar is BLUE with no progress; the pack draws nothing for a BLUE background
 * in the top slot (position_tex.fsh) and moves the title's glyphs into place (rendertype_text.vsh). It has to
 * be the player's first bar, so it is shown on join before anything else can add one.
 */
public final class HudCarrier implements Listener {

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Component> shown = new ConcurrentHashMap<>();
    private final BooleanSupplier enabled;

    public HudCarrier(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    /** Puts {@code title} on the player's carrier (creating it if needed); a repeat title sends nothing. */
    public void show(Player player, Component title) {
        UUID id = player.getUniqueId();
        BossBar bar = bars.computeIfAbsent(id, ignored -> create(player));
        Component last = shown.put(id, title);
        if (!title.equals(last)) {
            bar.name(title);
        }
    }

    public void hide(Player player) {
        UUID id = player.getUniqueId();
        shown.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void forget(UUID playerId) {
        bars.remove(playerId);
        shown.remove(playerId);
    }

    /** Plugin disable: take every carrier off its player so a reload starts clean. */
    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
        shown.clear();
    }

    /** First thing on join, so the carrier is the top bar and every other bar stacks below it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (enabled.getAsBoolean()) {
            show(event.getPlayer(), Component.empty());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    private static BossBar create(Player player) {
        BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        return bar;
    }
}
