package de.aetherion.foraging.habitat;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nested Eldervale habitats for TAB (%aetherion_area%) + future rituals/hotspots.
 * Prefer live material sensing on the isle footprint; optional AABB boxes are fallback only.
 */
public final class ForageHabitatService {

    private final AetherionForaging plugin;
    private final List<ForageHabitat> habitats = new ArrayList<>();
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private ForageHabitatSense sense;

    public ForageHabitatService(AetherionForaging plugin) {
        this.plugin = plugin;
        this.sense = new ForageHabitatSense(plugin);
        reload();
    }

    public ForageHabitatSense sense() {
        return sense;
    }

    public void reload() {
        habitats.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("habitats");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ForageHabitat habitat = ForageHabitat.fromConfig(key, root.getConfigurationSection(key));
                if (habitat != null) {
                    habitats.add(habitat);
                }
            }
            habitats.sort(Comparator.comparingInt(ForageHabitat::priority).reversed());
        }
        if (sense == null) {
            sense = new ForageHabitatSense(plugin);
        } else {
            sense.reload();
        }
        plugin.getLogger().info("Forage habitats (boxes): " + habitats.size()
                + " · sense=" + (sense.enabled() ? "on" : "off"));
    }

    public List<ForageHabitat> all() {
        return List.copyOf(habitats);
    }

    /** Highest-priority habitat containing the location, or null. */
    public ForageHabitat at(Location location) {
        if (location == null) {
            return null;
        }
        for (ForageHabitat habitat : habitats) {
            if (habitat.contains(location)) {
                return habitat;
            }
        }
        return null;
    }

    public String displayAt(Location location) {
        if (location == null) {
            return null;
        }
        // Material / snow / mushroom sensing first (only inside forage footprint).
        if (sense != null && sense.enabled()) {
            String sensed = sense.detectDisplay(location);
            if (sensed != null && !sensed.isBlank()) {
                return sensed;
            }
        }
        ForageHabitat habitat = at(location);
        return habitat == null ? null : habitat.display();
    }

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc.clone());
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc.clone());
    }

    public boolean saveFromSelection(Player player, String id, String display, int priority) {
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            player.sendMessage("§cSet both corners first: §e/forage habitat pos1 §7+ §epos2");
            return false;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            player.sendMessage("§cCorners must be in the same world.");
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT).replace(' ', '_');
        String path = "habitats." + key;
        plugin.getConfig().set(path + ".display", display == null || display.isBlank() ? pretty(key) : display);
        plugin.getConfig().set(path + ".priority", priority);
        plugin.getConfig().set(path + ".world", a.getWorld().getName());
        plugin.getConfig().set(path + ".min.x", Math.min(a.getX(), b.getX()));
        plugin.getConfig().set(path + ".min.y", Math.min(a.getY(), b.getY()));
        plugin.getConfig().set(path + ".min.z", Math.min(a.getZ(), b.getZ()));
        plugin.getConfig().set(path + ".max.x", Math.max(a.getX(), b.getX()));
        plugin.getConfig().set(path + ".max.y", Math.max(a.getY(), b.getY()));
        plugin.getConfig().set(path + ".max.z", Math.max(a.getZ(), b.getZ()));
        plugin.saveConfig();
        reload();
        player.sendMessage("§aHabitat §f" + key + " §asaved · " + displayAtMid(a, b));
        return true;
    }

    private static String displayAtMid(Location a, Location b) {
        return String.format("§7(%.0f,%.0f,%.0f)→(%.0f,%.0f,%.0f)",
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private static String pretty(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Ensure the parent FORAGE_ISLE Area Tool disk exists (gameplay + TAB parent).
     */
    public void ensureParentIsleArea() {
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            plugin.getLogger().warning("AetherionItems missing — cannot ensure FORAGE_ISLE area.");
            return;
        }
        try {
            Object areas = items.getClass().getMethod("getAreas").invoke(items);
            if (areas == null) {
                return;
            }
            // Skip if already present.
            @SuppressWarnings("unchecked")
            java.util.Collection<?> zones =
                    (java.util.Collection<?>) areas.getClass().getMethod("zones").invoke(areas);
            for (Object zone : zones) {
                Object type = zone.getClass().getMethod("getType").invoke(zone);
                if (type != null && "FORAGE_ISLE".equals(String.valueOf(type))) {
                    plugin.getLogger().info("FORAGE_ISLE area already present.");
                    return;
                }
            }
            String worldName = plugin.getConfig().getString("forage-isle.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null && !Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().getFirst();
            }
            if (world == null) {
                return;
            }
            double x = plugin.getConfig().getDouble("forage-isle.paste.x", 677);
            double y = plugin.getConfig().getDouble("forage-isle.paste.y", 90);
            double z = plugin.getConfig().getDouble("forage-isle.paste.z", -116);
            int radius = plugin.getConfig().getInt("forage-isle.area-radius", 400);
            Location center = new Location(world, x, y, z);
            Class<?> areaType = Class.forName("de.aetherion.items.world.AreaType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object forageIsle = Enum.valueOf((Class) areaType, "FORAGE_ISLE");
            areas.getClass()
                    .getMethod("place", Location.class, areaType, Player.class, int.class)
                    .invoke(areas, center, forageIsle, null, radius);
            plugin.getLogger().info("Placed FORAGE_ISLE area at "
                    + (int) x + "," + (int) y + "," + (int) z + " r=" + radius);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not ensure FORAGE_ISLE area: " + ex.getMessage());
        }
    }

    /** Called from AetherionItems AreaService.nameAt via soft bridge. */
    public static String resolveDisplay(Location location) {
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin == null || plugin.habitats() == null) {
            return null;
        }
        return plugin.habitats().displayAt(location);
    }

    /** Soft bridge: forage-isle light AABB (copied island footprint only). */
    public static boolean inIsleFootprint(Location location) {
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin == null || location == null || location.getWorld() == null) {
            return false;
        }
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        double minX = plugin.getConfig().getDouble("forage-isle.light.min-x", 500);
        double maxX = plugin.getConfig().getDouble("forage-isle.light.max-x", 1070);
        double minY = plugin.getConfig().getDouble("forage-isle.light.min-y", 50);
        double maxY = plugin.getConfig().getDouble("forage-isle.light.max-y", 220);
        double minZ = plugin.getConfig().getDouble("forage-isle.light.min-z", -500);
        double maxZ = plugin.getConfig().getDouble("forage-isle.light.max-z", 30);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
