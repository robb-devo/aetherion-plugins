package de.aetherion.stressbots;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BotProvisioner {

    private final AetherionStressBots plugin;
    private final String combatPrefix;
    private final String miningPrefix;

    public BotProvisioner(AetherionStressBots plugin) {
        this.plugin = plugin;
        this.combatPrefix = plugin.getConfig().getString("prefixes.combat", "StressC").toLowerCase(Locale.ROOT);
        this.miningPrefix = plugin.getConfig().getString("prefixes.mining", "StressM").toLowerCase(Locale.ROOT);
    }

    public BotRole roleOf(Player player) {
        if (player == null) {
            return null;
        }
        String name = player.getName().toLowerCase(Locale.ROOT);
        if (name.startsWith(combatPrefix)) {
            return BotRole.COMBAT;
        }
        if (name.startsWith(miningPrefix)) {
            return BotRole.MINING;
        }
        return null;
    }

    public boolean isStressBot(Player player) {
        return roleOf(player) != null;
    }

    public void scheduleSetup(Player player) {
        BotRole role = roleOf(player);
        if (role == null) {
            return;
        }
        int delay = Math.max(1, plugin.getConfig().getInt("setup-delay-ticks", 40));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            setup(player, role);
        }, delay);
    }

    public void setup(Player player, BotRole role) {
        if (player == null || role == null || !player.isOnline()) {
            return;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCustomItem() == null) {
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

        CustomItem custom = items.getCustomItem();
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);

        if (role == BotRole.COMBAT) {
            inv.setHelmet(custom.createCombatHelmet());
            inv.setChestplate(custom.createCombatChestplate());
            inv.setLeggings(custom.createCombatLeggings());
            inv.setBoots(custom.createCombatBoots());
            inv.setItemInMainHand(custom.createCombatSword());
            giveExtras(inv, custom.createCombatSword());
        } else {
            inv.setHelmet(custom.createMiningHelmet());
            inv.setChestplate(custom.createMiningChestplate());
            inv.setLeggings(custom.createMiningLeggings());
            inv.setBoots(custom.createMiningBoots());
            inv.setItemInMainHand(custom.createMiningPickaxe());
            giveExtras(inv, custom.createMiningPickaxe());
        }
        player.updateInventory();

        Location destination = destinationFor(player, role);
        if (destination != null) {
            player.teleport(destination);
        }
        plugin.getLogger().info("Provisioned " + role.name().toLowerCase(Locale.ROOT)
                + " bot " + player.getName()
                + " @ " + format(destination));
    }

    private void giveExtras(PlayerInventory inv, ItemStack spare) {
        if (spare != null) {
            inv.addItem(spare.clone());
        }
    }

    private Location destinationFor(Player player, BotRole role) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (role == BotRole.COMBAT) {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("combat");
            if (section == null) {
                return null;
            }
            return scatter(readPoint(section), section.getDouble("scatter-radius", 18), rng);
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mining");
        if (section == null) {
            return null;
        }
        List<Location> anchors = readAnchors(section);
        if (anchors.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(stableIndex(player.getName()), anchors.size());
        Location base = anchors.get(index);
        return scatter(base, section.getDouble("scatter-radius", 10), rng);
    }

    private List<Location> readAnchors(ConfigurationSection section) {
        List<Location> out = new ArrayList<>();
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return out;
        }
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        List<?> raw = section.getList("anchors");
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            if (!(entry instanceof ConfigurationSection point) && !(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            double x;
            double y;
            double z;
            if (entry instanceof ConfigurationSection point) {
                x = point.getDouble("x");
                y = point.getDouble("y");
                z = point.getDouble("z");
            } else {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) entry;
                x = toDouble(map.get("x"));
                y = toDouble(map.get("y"));
                z = toDouble(map.get("z"));
            }
            out.add(new Location(world, x, y, z, yaw, pitch));
        }
        return out;
    }

    private Location readPoint(ConfigurationSection section) {
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    private Location scatter(Location base, double radius, ThreadLocalRandom rng) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        if (radius <= 0) {
            return base.clone();
        }
        double angle = rng.nextDouble(0, Math.PI * 2);
        double dist = rng.nextDouble(0, radius);
        Location out = base.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        out.setYaw(base.getYaw());
        out.setPitch(base.getPitch());
        return safeStanding(out);
    }

    private Location safeStanding(Location location) {
        if (location == null || location.getWorld() == null) {
            return location;
        }
        // Stay near the intended Y. Mines are underground — getHighestBlockYAt would
        // dump bots on the surface above the shaft.
        Location best = location.clone();
        best.setX(Math.floor(best.getX()) + 0.5);
        best.setZ(Math.floor(best.getZ()) + 0.5);
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{0, 1, -1}) {
                if (dy == 0 && sign != 0) {
                    continue;
                }
                Location candidate = best.clone().add(0, sign * dy, 0);
                if (!candidate.getBlock().isPassable()) {
                    continue;
                }
                if (!candidate.clone().add(0, 1, 0).getBlock().isPassable()) {
                    continue;
                }
                if (candidate.clone().add(0, -1, 0).getBlock().isPassable()) {
                    continue; // need floor
                }
                return candidate;
            }
        }
        return best;
    }

    private static int stableIndex(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = 31 * hash + name.charAt(i);
        }
        return Math.abs(hash);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "nowhere";
        }
        return location.getWorld().getName()
                + " "
                + String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    public String combatPrefix() {
        return combatPrefix;
    }

    public String miningPrefix() {
        return miningPrefix;
    }
}
