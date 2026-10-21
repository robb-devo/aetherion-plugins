package de.aetherion.pit.hud;

import de.aetherion.pit.AetherionPit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Locale;

/**
 * HP readout on the ActionBar while in the Pit. The network pack hides the vanilla hearts (the Aetherion HUD
 * replaces them on the game servers), and the hub runs no Aetherion HUD, so the Pit says it in text.
 */
public final class PitHealthHud implements Listener {

    private static final TextColor HEART = TextColor.color(0xE0404E);
    private static final TextColor CURRENT = TextColor.color(0xFFFFFF);
    private static final TextColor SLASH = TextColor.color(0x8F86A0);
    private static final TextColor MAX = TextColor.color(0xFFC9CC);

    private final AetherionPit plugin;

    public PitHealthHud(AetherionPit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> show(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> show(player));
        }
    }

    private void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }

    private void show(Player player) {
        if (!player.isOnline() || player.isDead() || !plugin.safeZone().isPitWorld(player.getWorld())) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attribute == null ? 20.0 : attribute.getValue();
        double current = Math.max(0.0, Math.min(player.getHealth(), max));
        player.sendActionBar(Component.text()
                .append(Component.text("❤ ", HEART))
                .append(Component.text(number(current), CURRENT))
                .append(Component.text(" / ", SLASH))
                .append(Component.text(number(max), MAX))
                .build());
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
