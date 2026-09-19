package de.aetherion.dungeons.npc;

import de.aetherion.core.AetherKeys;
import de.aetherion.dungeons.AetherionDungeons;
import de.aetherion.dungeons.menu.DungeonGuideGUI;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

/**
 * Placeable briefing NPC — short line, then {@link DungeonGuideGUI}.
 */
public final class DungeonGuideService {

    public static final String NPC_ID = "dungeon_guide";
    public static final String DISPLAY_NAME = "Dungeon Scribe";
    public static final String FANCY_NAME = "ae_dungeon_guide";

    private final JavaPlugin plugin;
    private final File file;
    private Location saved;

    public DungeonGuideService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guide-npc.yml");
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return AetherKeys.DUNGEON_NPC;
    }

    public static boolean isGuide(JavaPlugin plugin, Entity entity) {
        if (entity == null || plugin == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        if (NPC_ID.equals(id) || entity.getScoreboardTags().contains(NPC_ID)) {
            return true;
        }
        String name = entity.getCustomName();
        return name != null && name.contains(DISPLAY_NAME);
    }

    public static boolean isFancyGuideName(String fancyName) {
        return FANCY_NAME.equalsIgnoreCase(fancyName);
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5NPC Anchor §7(" + DISPLAY_NAME + ")");
            meta.setLore(List.of(
                    "§7Right-click a block to place",
                    "§f" + DISPLAY_NAME + "§7 there.",
                    "§eSneak + right-click §7despawns them.",
                    "",
                    "§8Players get a short tip, then a",
                    "§8dungeon briefing menu."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(AetherionDungeons.getInstance(), "dungeon_guide_anchor"),
                    PersistentDataType.STRING,
                    NPC_ID
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        if (item == null || !item.hasItemMeta() || AetherionDungeons.getInstance() == null) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(AetherionDungeons.getInstance(), "dungeon_guide_anchor"),
                PersistentDataType.STRING
        );
    }

    public void openFor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§5" + DISPLAY_NAME + " §8» §fQuick briefing. Don't lose the clipboard.");
        player.sendMessage("");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                DungeonGuideGUI.open(player, DISPLAY_NAME);
            }
        }, 25L);
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString("guide.world");
        if (worldName == null) {
            return;
        }
        trySpawn(worldName, config, 0);
    }

    private void trySpawn(String worldName, FileConfiguration config, int attempt) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || !fancyReady()) {
            if (attempt < 40) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> trySpawn(worldName, config, attempt + 1), 20L);
            } else if (world != null) {
                saved = locationFrom(config, world);
                ensureAt(saved);
            }
            return;
        }
        saved = locationFrom(config, world);
        ensureAt(saved);
    }

    private static Location locationFrom(FileConfiguration config, World world) {
        return new Location(
                world,
                config.getDouble("guide.x"),
                config.getDouble("guide.y"),
                config.getDouble("guide.z"),
                (float) config.getDouble("guide.yaw"),
                (float) config.getDouble("guide.pitch")
        );
    }

    private static boolean fancyReady() {
        Plugin fancy = Bukkit.getPluginManager().getPlugin("FancyNpcs");
        if (fancy == null || !fancy.isEnabled()) {
            return true;
        }
        try {
            Object api = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin").getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getNpcManager").invoke(api);
            return Boolean.TRUE.equals(manager.getClass().getMethod("isLoaded").invoke(manager));
        } catch (Throwable ignored) {
            return true;
        }
    }

    public void save(Location location) {
        saved = location.clone();
        FileConfiguration config = new YamlConfiguration();
        config.set("guide.world", location.getWorld().getName());
        config.set("guide.x", location.getX());
        config.set("guide.y", location.getY());
        config.set("guide.z", location.getZ());
        config.set("guide.yaw", location.getYaw());
        config.set("guide.pitch", location.getPitch());
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save dungeon guide NPC: " + ex.getMessage());
        }
    }

    /** Boot restore — do not wipe FancyNPC (that deleted him from Fancy's save). */
    public void ensureAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        saved = location.clone();
        location.getWorld().getChunkAt(location).load();
        removeEntityGuidesOnly();
        if (spawnFancy(location)) {
            plugin.getLogger().info("Dungeon Scribe ready as FancyNPC at "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return;
        }
        spawnVillagerFallback(location);
    }

    public void spawnAt(Location location) {
        despawnEntitiesOnly(false);
        location.getWorld().getChunkAt(location).load();
        save(location);
        if (spawnFancy(location)) {
            plugin.getLogger().info("Dungeon Scribe spawned as FancyNPC at "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return;
        }
        spawnVillagerFallback(location);
    }

    private void spawnVillagerFallback(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setCustomName("§5" + DISPLAY_NAME);
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setGravity(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.setAdult();
        villager.setAgeLock(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setRecipes(List.of());
        villager.addScoreboardTag(NPC_ID);
        villager.getEquipment().setItemInMainHand(new ItemStack(Material.BOOK));
        tag(villager);
        Interaction hitbox = location.getWorld().spawn(location, Interaction.class);
        hitbox.setInteractionWidth(1.2f);
        hitbox.setInteractionHeight(2.2f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(true);
        hitbox.addScoreboardTag(NPC_ID);
        tag(hitbox);
    }

    private boolean spawnFancy(Location location) {
        Plugin fancy = Bukkit.getPluginManager().getPlugin("FancyNpcs");
        if (fancy == null || !fancy.isEnabled()) {
            return false;
        }
        try {
            Object api = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin").getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getNpcManager").invoke(api);
            Object existing = getFancy(manager, FANCY_NAME);
            if (existing != null) {
                Object data = existing.getClass().getMethod("getData").invoke(existing);
                if (data != null) {
                    try {
                        data.getClass().getMethod("setLocation", Location.class).invoke(data, location.clone());
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                try {
                    existing.getClass().getMethod("setSaveToFile", boolean.class).invoke(existing, true);
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    existing.getClass().getMethod("moveForAll").invoke(existing);
                } catch (NoSuchMethodException ignored) {
                }
                existing.getClass().getMethod("spawnForAll").invoke(existing);
                saveFancy(manager);
                return true;
            }
            Class<?> dataClass = Class.forName("de.oliver.fancynpcs.api.NpcData");
            Constructor<?> ctor = dataClass.getConstructor(String.class, UUID.class, Location.class);
            Object data = ctor.newInstance(FANCY_NAME, new UUID(0L, 1L), location.clone());
            dataClass.getMethod("setDisplayName", String.class).invoke(data, "§5§l" + DISPLAY_NAME);
            dataClass.getMethod("setType", EntityType.class).invoke(data, EntityType.PLAYER);
            dataClass.getMethod("setShowInTab", boolean.class).invoke(data, false);
            dataClass.getMethod("setCollidable", boolean.class).invoke(data, false);
            dataClass.getMethod("setTurnToPlayer", boolean.class).invoke(data, true);
            try {
                dataClass.getMethod("setSpawnEntity", boolean.class).invoke(data, true);
            } catch (NoSuchMethodException ignored) {
            }
            @SuppressWarnings("unchecked")
            java.util.function.Function<Object, Object> adapter =
                    (java.util.function.Function<Object, Object>) api.getClass()
                            .getMethod("getNpcAdapter").invoke(api);
            Object npc = adapter.apply(data);
            try {
                npc.getClass().getMethod("setSaveToFile", boolean.class).invoke(npc, true);
            } catch (NoSuchMethodException ignored) {
            }
            npc.getClass().getMethod("create").invoke(npc);
            Class<?> npcIface = Class.forName("de.oliver.fancynpcs.api.Npc");
            manager.getClass().getMethod("registerNpc", npcIface).invoke(manager, npc);
            npc.getClass().getMethod("spawnForAll").invoke(npc);
            saveFancy(manager);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("FancyNPC Dungeon Scribe failed: " + ex.getMessage());
            return false;
        }
    }

    private static void saveFancy(Object manager) {
        try {
            manager.getClass().getMethod("saveNpcs", boolean.class).invoke(manager, true);
        } catch (ReflectiveOperationException ignored) {
            try {
                manager.getClass().getMethod("saveNpcs").invoke(manager);
            } catch (ReflectiveOperationException ignored2) {
            }
        }
    }

    private static Object getFancy(Object manager, String name) {
        // String overload only — getNpc(int) throws argument type mismatch.
        for (String method : List.of("getNpc", "getNpcById")) {
            try {
                Object npc = manager.getClass().getMethod(method, String.class).invoke(manager, name);
                if (npc != null) {
                    return npc;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    public void despawnAll() {
        despawnEntitiesOnly(true);
        saved = null;
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete dungeon guide save.");
        }
    }

    private void despawnEntitiesOnly() {
        despawnEntitiesOnly(true);
    }

    private void despawnEntitiesOnly(boolean persistFancyRemoval) {
        removeFancyQuiet(persistFancyRemoval);
        Location previous = saved != null ? saved : readSaved();
        if (previous != null && previous.getWorld() != null) {
            previous.getWorld().getChunkAt(previous).load();
            for (Entity entity : previous.getWorld().getNearbyEntities(previous, 16, 16, 16)) {
                if (isGuide(plugin, entity)) {
                    entity.remove();
                }
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (isGuide(plugin, entity)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeEntityGuidesOnly() {
        Location previous = saved != null ? saved : readSaved();
        if (previous != null && previous.getWorld() != null) {
            previous.getWorld().getChunkAt(previous).load();
            for (Entity entity : previous.getWorld().getNearbyEntities(previous, 16, 16, 16)) {
                if (isGuide(plugin, entity)) {
                    entity.remove();
                }
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (isGuide(plugin, entity)) {
                    entity.remove();
                }
            }
        }
    }

    private Location readSaved() {
        if (!file.exists()) {
            return null;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString("guide.world");
        if (worldName == null) {
            return null;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                config.getDouble("guide.x"),
                config.getDouble("guide.y"),
                config.getDouble("guide.z"),
                (float) config.getDouble("guide.yaw"),
                (float) config.getDouble("guide.pitch"));
    }

    private void removeFancyQuiet(boolean persist) {
        try {
            Plugin fancy = Bukkit.getPluginManager().getPlugin("FancyNpcs");
            if (fancy == null || !fancy.isEnabled()) {
                return;
            }
            Object api = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin").getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getNpcManager").invoke(api);
            Object npc = getFancy(manager, FANCY_NAME);
            if (npc == null) {
                return;
            }
            try {
                npc.getClass().getMethod("removeForAll").invoke(npc);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Class<?> npcIface = Class.forName("de.oliver.fancynpcs.api.Npc");
                manager.getClass().getMethod("removeNpc", npcIface).invoke(manager, npc);
            } catch (ReflectiveOperationException ignored) {
            }
            if (persist) {
                saveFancy(manager);
            }
        } catch (Throwable ignored) {
        }
    }

    private void tag(Entity entity) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, NPC_ID);
    }
}
