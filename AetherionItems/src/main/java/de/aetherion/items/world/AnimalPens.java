package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animal pens (2026-11-27, Robb: "Tiere an passenden Orten, z. B. ein kleines Gehege auf der Farm"). Each pen in
 * {@code animal-pens.yml} keeps a fixed number of one animal inside a small circle: missing ones come back one at
 * a time, extras go, pen animals never breed, and one that wandered far is put back. Pens only work while their
 * chunk is loaded, so nothing piles up. Built fences are the pen; this only keeps the count.
 * <pre>
 * pens:
 *   farm_cows: {world: world, x: -215.5, y: 64, z: 190.5, radius: 5, animal: COW, count: 3}
 * </pre>
 * {@code /aepen add <id> <animal> <count> [radius]} at your feet, {@code /aepen remove <id>}, {@code /aepen list}.
 */
public final class AnimalPens implements Listener, Runnable, CommandExecutor {

    private static final int MAX_COUNT = 8;
    private final JavaPlugin plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<String, Pen> pens = new LinkedHashMap<>();

    private record Pen(String id, String world, double x, double y, double z, double radius, EntityType animal, int count) {
    }

    public AnimalPens(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "animal-pens.yml");
        this.tag = new NamespacedKey(plugin, "pen");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 200L, 600L);
    }

    @Override
    public void run() {
        for (Pen pen : pens.values()) {
            World world = Bukkit.getWorld(pen.world());
            if (world == null || !world.isChunkLoaded(((int) Math.floor(pen.x())) >> 4, ((int) Math.floor(pen.z())) >> 4)) {
                continue;
            }
            Location centre = new Location(world, pen.x(), pen.y(), pen.z());
            List<Entity> mine = new ArrayList<>();
            double reach = Math.max(16.0d, pen.radius() * 4.0d);
            for (Entity entity : world.getNearbyEntities(centre, reach, 12.0d, reach)) {
                String id = entity.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
                if (pen.id().equals(id) && entity.isValid()) {
                    mine.add(entity);
                }
            }
            // Extras go (old saves, a lost tag race), wanderers come back.
            for (int i = pen.count(); i < mine.size(); i++) {
                mine.get(i).remove();
            }
            for (int i = 0; i < Math.min(pen.count(), mine.size()); i++) {
                Entity entity = mine.get(i);
                if (entity.getLocation().distanceSquared(centre) > pen.radius() * pen.radius() * 4.0d) {
                    entity.teleport(centre);
                }
            }
            if (mine.size() < pen.count()) {
                spawn(pen, centre);
            }
        }
    }

    private void spawn(Pen pen, Location centre) {
        double angle = Math.random() * Math.PI * 2.0d;
        double distance = Math.random() * Math.max(0.5d, pen.radius() - 1.0d);
        Location at = centre.clone().add(Math.cos(angle) * distance, 0.0d, Math.sin(angle) * distance);
        at.setY(centre.getWorld().getHighestBlockYAt(at.getBlockX(), at.getBlockZ()) + 1.0d);
        if (Math.abs(at.getY() - centre.getY()) > 4.0d) {
            at = centre.clone();
        }
        centre.getWorld().spawnEntity(at, pen.animal(), CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            entity.getPersistentDataContainer().set(tag, PersistentDataType.STRING, pen.id());
            if (entity instanceof Ageable ageable) {
                ageable.setAdult();
            }
            entity.setPersistent(true);
        });
    }

    /** Pen animals never breed: the count stays what the pen says. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getMother().getPersistentDataContainer().has(tag, PersistentDataType.STRING)
                || event.getFather().getPersistentDataContainer().has(tag, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ /aepen (op)

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cOps only.");
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    sender.sendMessage("§7/aepen add <id> <animal> <count> [radius] §8(at your feet)");
                    return true;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("§cUnknown animal: " + args[2]);
                    return true;
                }
                if (type.getEntityClass() == null || !Animals.class.isAssignableFrom(type.getEntityClass())) {
                    sender.sendMessage("§cNot an animal: " + args[2]);
                    return true;
                }
                int count = Math.max(1, Math.min(MAX_COUNT, parse(args[3], 3)));
                double radius = Math.max(2.0d, Math.min(16.0d, args.length > 4 ? parse(args[4], 5) : 5));
                Location at = player.getLocation();
                pens.put(args[1], new Pen(args[1], at.getWorld().getName(), at.getBlockX() + 0.5d, at.getY(),
                        at.getBlockZ() + 0.5d, radius, type, count));
                save();
                sender.sendMessage("§aPen §f" + args[1] + " §a· " + count + "× " + type.name().toLowerCase(Locale.ROOT)
                        + " §8· radius " + (int) radius);
            }
            case "remove" -> {
                if (args.length < 2 || pens.remove(args[1]) == null) {
                    sender.sendMessage("§7/aepen remove <id>");
                    return true;
                }
                save();
                sender.sendMessage("§7Pen removed (its animals go at the next check).");
            }
            default -> {
                sender.sendMessage("§7Pens: §f" + pens.size());
                for (Pen pen : pens.values()) {
                    sender.sendMessage("§8· §f" + pen.id() + " §7" + pen.count() + "× " + pen.animal().name().toLowerCase(Locale.ROOT)
                            + " §8@ " + pen.world() + " " + (int) pen.x() + " " + (int) pen.y() + " " + (int) pen.z()
                            + " r" + (int) pen.radius());
                }
            }
        }
        return true;
    }

    /** Animals of a removed pen still carry its tag: they go at the next check of any pen near them. */
    public boolean isPenAnimal(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private void load() {
        pens.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("pens");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(s.getString("animal", "COW").toUpperCase(Locale.ROOT));
                pens.put(id, new Pen(id, s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        Math.max(2.0d, s.getDouble("radius", 5.0d)), type,
                        Math.max(1, Math.min(MAX_COUNT, s.getInt("count", 3)))));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("animal-pens.yml: unknown animal in pen " + id);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Pen pen : pens.values()) {
            String path = "pens." + pen.id();
            yaml.set(path + ".world", pen.world());
            yaml.set(path + ".x", pen.x());
            yaml.set(path + ".y", pen.y());
            yaml.set(path + ".z", pen.z());
            yaml.set(path + ".radius", pen.radius());
            yaml.set(path + ".animal", pen.animal().name());
            yaml.set(path + ".count", pen.count());
        }
        try {
            plugin.getDataFolder().mkdirs();
            de.aetherion.core.persist.AtomicYaml.save(yaml, file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not save animal-pens.yml: " + ex.getMessage());
        }
    }
}
