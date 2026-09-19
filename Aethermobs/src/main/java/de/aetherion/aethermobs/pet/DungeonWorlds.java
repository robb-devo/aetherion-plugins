package de.aetherion.aethermobs.pet;

import org.bukkit.World;

import java.util.Locale;

public final class DungeonWorlds {

    private DungeonWorlds() {
    }

    public static boolean isDungeon(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }

    public static boolean isGuildIsland(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_guilds")
                || name.startsWith("aether_guild")
                || name.equals("aether_islands")
                || name.startsWith("aether_island");
    }

    /** Dev sandbox — no wild pets. */
    public static boolean isTestArena(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_test") || name.startsWith("aether_test_");
    }

    public static boolean blocksWildPets(World world) {
        return isGuildIsland(world) || isTestArena(world) || isDungeonHub(world);
    }

    /** mmo-d overworld — dungeon entry only, no companion / wild pets. */
    public static boolean isDungeonHub(World world) {
        if (world == null || isDungeon(world)) {
            return false;
        }
        try {
            org.bukkit.plugin.Plugin dungeons = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionDungeons");
            if (dungeons == null || !dungeons.isEnabled()) {
                return false;
            }
            if (!"dungeon".equalsIgnoreCase(dungeons.getConfig().getString("role", "hub"))) {
                return false;
            }
            String hub = dungeons.getConfig().getString("return-portal.world", "world");
            return world.getName().equalsIgnoreCase(hub);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
