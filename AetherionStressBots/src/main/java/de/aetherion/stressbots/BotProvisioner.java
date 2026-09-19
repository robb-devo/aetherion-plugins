package de.aetherion.stressbots;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.role.BotLocations;
import de.aetherion.stressbots.role.BotPlaystyle;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BotProvisioner {

    private final AetherionStressBots plugin;

    public BotProvisioner(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public BotRole roleOf(Player player) {
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        return handler == null ? null : handler.role();
    }

    public boolean isStressBot(Player player) {
        return roleOf(player) != null;
    }

    public void scheduleSetup(Player player) {
        int delay = Math.max(1, plugin.getConfig().getInt("setup-delay-ticks", 40));
        scheduleSetup(player, delay);
    }

    public void scheduleSetup(Player player, int delayTicks) {
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler == null) {
            return;
        }
        int delay = Math.max(1, delayTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            setup(player, handler);
        }, delay);
    }

    public void setup(Player player, BotRole role) {
        BotRoleHandler handler = plugin.getRegistry().handler(role);
        setup(player, handler);
    }

    public void setup(Player player, BotRoleHandler handler) {
        if (player == null || handler == null || !player.isOnline()) {
            return;
        }
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.getCustomItem() == null) {
            plugin.getLogger().warning("AetherionItems unavailable — cannot kit " + player.getName());
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        if (plugin.getConfig().getBoolean("full-heal-on-setup", true)) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
        }

        CustomItem custom = itemsPlugin.getCustomItem();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        handler.kit(player, custom);
        BotPlaystyle.enrich(plugin, player, handler.role(), custom);
        player.updateInventory();
        plugin.getNicknames().applyLater(player, handler.role());

        Location destination = handler.destination(player);
        if (destination != null) {
            player.teleport(destination);
            plugin.getActivity().markAction(player, "teleport " + BotLocations.format(destination));
        } else {
            plugin.getLogger().warning("No destination for " + handler.role().id()
                    + " bot " + player.getName() + " (world unloaded or missing YAML anchors)");
            plugin.getActivity().markError(player, "no destination");
        }
        plugin.getLogger().info("Provisioned " + handler.role().id()
                + " bot " + player.getName()
                + " nick=" + plugin.getNicknames().plain(player, handler.role())
                + " prefix=" + handler.prefix()
                + " @ " + BotLocations.format(destination));
    }

    public String combatPrefix() {
        BotRoleHandler handler = plugin.getRegistry().handler(BotRole.COMBAT);
        return handler == null ? "stressc" : handler.prefix();
    }

    public String miningPrefix() {
        BotRoleHandler handler = plugin.getRegistry().handler(BotRole.MINING);
        return handler == null ? "stressm" : handler.prefix();
    }
}
