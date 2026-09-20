package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeable Crypt entrance hologram (DEV tool) — warning text at the cave mouth.
 */
public final class CryptHologramService {

    private static final String LINE_1 = "§5§lCrypt";
    private static final String LINE_2 = "§cDanger";

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlacedHolo> placed = new ConcurrentHashMap<>();

    public CryptHologramService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crypt-holograms.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ensureDisplays, 80L, 200L);
    }

    public ItemStack createTool() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5§lCrypt Hologram");
            meta.setLore(List.of(
                    "§7DEV · floating Crypt warning.",
                    "§7Place at the cave entrance.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7removes nearby."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.cryptHoloTool(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.cryptHoloTool(), PersistentDataType.BYTE);
    }

    public PlacedHolo place(Location location, Player player) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Location at = location.clone().add(0.5, 1.85, 0.5);
        PlacedHolo holo = new PlacedHolo(
                UUID.randomUUID(),
                at.getWorld().getName(),
                at.getX(),
                at.getY(),
                at.getZ()
        );
        spawnDisplay(holo);
        placed.put(holo.id(), holo);
        save();
        if (player != null) {
            player.spawnParticle(Particle.SOUL, at, 16, 0.35, 0.4, 0.35, 0.01);
            player.playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.35f, 1.4f);
            player.sendMessage("§5Crypt hologram placed. §7Sneak-click with the tool to remove.");
        }
        return holo;
    }

    public boolean removeNearest(Location location, Player player) {
        PlacedHolo holo = nearest(location, 10);
        if (holo == null) {
            if (player != null) {
                player.sendMessage("§cNo Crypt hologram nearby.");
            }
            return false;
        }
        removeDisplay(holo);
        placed.remove(holo.id());
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_SCULK_CATALYST_BREAK, 0.7f, 0.8f);
            player.sendMessage("§eRemoved Crypt hologram.");
        }
        return true;
    }

    private void spawnDisplay(PlacedHolo holo) {
        World world = Bukkit.getWorld(holo.world());
        if (world == null) {
            return;
        }
        // Drop any leftover displays for this id / spot (prevents stacked text).
        removeDisplay(holo);
        Location at = holo.location(world);
        TextDisplay display = world.spawn(at, TextDisplay.class, spawned -> {
            spawned.text(LegacyComponentSerializer.legacySection().deserialize(
                    LINE_1 + "\n" + LINE_2
            ));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(false);
            spawned.setShadowed(true);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setBackgroundColor(Color.fromARGB(140, 18, 8, 28));
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(1.15f, 1.15f, 1.15f),
                    new Quaternionf()
            ));
            spawned.getPersistentDataContainer().set(
                    ItemKeys.cryptHoloDisplay(),
                    PersistentDataType.STRING,
                    holo.id().toString()
            );
            spawned.addScoreboardTag("aetherion_crypt_holo");
        });
        holo.displayId(display.getUniqueId());
    }

    private void removeDisplay(PlacedHolo holo) {
        if (holo.displayId() != null) {
            Entity entity = Bukkit.getEntity(holo.displayId());
            if (entity != null) {
                entity.remove();
            }
            holo.displayId(null);
        }
        World world = Bukkit.getWorld(holo.world());
        if (world == null) {
            return;
        }
        Location at = holo.location(world);
        for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
            String id = entity.getPersistentDataContainer().get(
                    ItemKeys.cryptHoloDisplay(),
                    PersistentDataType.STRING
            );
            boolean ours = holo.id().toString().equals(id)
                    || entity.getScoreboardTags().contains("aetherion_crypt_holo");
            if (!ours) {
                continue;
            }
            // Same hologram id, or orphan crypt holo stacked on this spot.
            if (holo.id().toString().equals(id) || entity.getLocation().distanceSquared(at) <= 2.25) {
                entity.remove();
            }
        }
    }

    private void ensureDisplays() {
        for (PlacedHolo holo : placed.values()) {
            World world = Bukkit.getWorld(holo.world());
            if (world == null) {
                continue;
            }
            Location at = holo.location(world);
            if (!at.getChunk().isLoaded()) {
                continue;
            }
            Entity entity = holo.displayId() == null ? null : Bukkit.getEntity(holo.displayId());
            TextDisplay display = entity instanceof TextDisplay text && entity.isValid() ? text : null;
            if (display == null) {
                display = reclaim(holo, world, at);
            }
            if (display != null && display.isValid()) {
                cullDuplicates(holo, display.getUniqueId());
                display.text(LegacyComponentSerializer.legacySection().deserialize(
                        LINE_1 + "\n" + LINE_2
                ));
                continue;
            }
            spawnDisplay(holo);
        }
    }

    private TextDisplay reclaim(PlacedHolo holo, World world, Location at) {
        TextDisplay found = null;
        for (Entity entity : world.getNearbyEntities(at, 3, 3, 3)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            String id = display.getPersistentDataContainer().get(
                    ItemKeys.cryptHoloDisplay(), PersistentDataType.STRING);
            boolean ours = holo.id().toString().equals(id)
                    || display.getScoreboardTags().contains("aetherion_crypt_holo");
            if (!ours) {
                continue;
            }
            if (found == null) {
                found = display;
            } else {
                display.remove();
            }
        }
        if (found != null) {
            holo.displayId(found.getUniqueId());
        }
        return found;
    }

    private void cullDuplicates(PlacedHolo holo, UUID keepId) {
        World world = Bukkit.getWorld(holo.world());
        if (world == null) {
            return;
        }
        Location at = holo.location(world);
        for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
            if (entity.getUniqueId().equals(keepId)) {
                continue;
            }
            String id = entity.getPersistentDataContainer().get(
                    ItemKeys.cryptHoloDisplay(),
                    PersistentDataType.STRING
            );
            boolean ours = holo.id().toString().equals(id)
                    || entity.getScoreboardTags().contains("aetherion_crypt_holo");
            if (ours && entity.getLocation().distanceSquared(at) <= 2.25) {
                entity.remove();
            }
        }
    }

    private PlacedHolo nearest(Location location, double max) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        PlacedHolo best = null;
        double bestDist = max * max;
        for (PlacedHolo holo : placed.values()) {
            if (!holo.world().equals(location.getWorld().getName())) {
                continue;
            }
            double dist = holo.location(location.getWorld()).distanceSquared(location);
            if (dist <= bestDist) {
                bestDist = dist;
                best = holo;
            }
        }
        return best;
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlacedHolo holo : placed.values()) {
            list.add(Map.of(
                    "id", holo.id().toString(),
                    "world", holo.world(),
                    "x", holo.x(),
                    "y", holo.y(),
                    "z", holo.z()
            ));
        }
        config.set("holograms", list);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save crypt-holograms.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> raw = config.getList("holograms");
        if (raw == null) {
            return;
        }
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            try {
                UUID id = UUID.fromString(String.valueOf(map.get("id")));
                String world = String.valueOf(map.get("world"));
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                placed.put(id, new PlacedHolo(id, world, x, y, z));
            } catch (RuntimeException ignored) {
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureDisplays, 40L);
    }

    public static final class PlacedHolo {
        private final UUID id;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private UUID displayId;

        public PlacedHolo(UUID id, String world, double x, double y, double z) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public UUID id() {
            return id;
        }

        public String world() {
            return world;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public UUID displayId() {
            return displayId;
        }

        public void displayId(UUID displayId) {
            this.displayId = displayId;
        }

        public Location location(World world) {
            return new Location(world, x, y, z);
        }
    }
}
