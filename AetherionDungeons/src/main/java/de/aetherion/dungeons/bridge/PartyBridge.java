package de.aetherion.dungeons.bridge;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PartyAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class PartyBridge {

    private PartyBridge() {
    }

    public static boolean inParty(Player player) {
        PartyAccess access = AetherServices.party();
        if (access != null) {
            return access.inParty(player);
        }
        Object service = service();
        if (service == null || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(service.getClass().getMethod("inParty", Player.class).invoke(service, player));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isLeader(Player player) {
        PartyAccess access = AetherServices.party();
        if (access != null) {
            return access.isLeader(player);
        }
        Object service = service();
        if (service == null || player == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(service.getClass().getMethod("isLeader", Player.class).invoke(service, player));
        } catch (Exception ignored) {
            return true;
        }
    }

    public static List<Player> onlineMembers(Player player) {
        if (player == null) {
            return List.of();
        }
        PartyAccess access = AetherServices.party();
        if (access != null) {
            return access.onlineMembers(player);
        }
        Object service = service();
        if (service == null) {
            return List.of(player);
        }
        try {
            Object result = service.getClass().getMethod("onlineMembers", Player.class).invoke(service, player);
            if (result instanceof List<?> list) {
                List<Player> members = new ArrayList<>();
                for (Object entry : list) {
                    if (entry instanceof Player member && member.isOnline()) {
                        members.add(member);
                    }
                }
                if (members.isEmpty()) {
                    members.add(player);
                }
                return members;
            }
        } catch (Exception ignored) {
        }
        return List.of(player);
    }

    private static Object service() {
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return null;
        }
        try {
            Object plugin = items.getClass().getMethod("getInstance").invoke(null);
            return plugin.getClass().getMethod("party").invoke(plugin);
        } catch (Exception ignored) {
            return null;
        }
    }
}
