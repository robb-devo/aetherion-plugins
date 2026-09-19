package de.aetherion.dungeons.npc;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.dungeons.AetherionDungeons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class DungeonKeeperService {

    public static final String NPC_ID = "dungeon_keeper";
    public static final String DISPLAY_NAME = "Dungeon Keeper";
    public static final String FANCY_NAME = "ae_dungeon_keeper";

    private final JavaPlugin plugin;
    private final File file;
    private Location saved;

    public DungeonKeeperService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return AetherKeys.DUNGEON_NPC;
    }

    public static boolean isKeeper(JavaPlugin plugin, Entity entity) {
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

    public static boolean isFancyKeeperName(String fancyName) {
        return FANCY_NAME.equalsIgnoreCase(fancyName);
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5NPC Anchor §7(" + DISPLAY_NAME + ")");
            meta.setLore(List.of(
                    "§7Right-click a block to place",
                    "§f" + DISPLAY_NAME + "§7 there.",
                    "§eSneak + right-click §7despawns him.",
                    "§eSneak + click the NPC §7also removes him.",
                    "",
                    "§8Players click him to open the",
                    "§8dungeon selection menu.",
                    "",
                    "§8Admin tool — not consumed"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(AetherionDungeons.getInstance(), "dungeon_npc_anchor"),
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
                new NamespacedKey(AetherionDungeons.getInstance(), "dungeon_npc_anchor"),
                PersistentDataType.STRING
        );
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString("keeper.world");
        if (worldName == null) {
            return;
        }
        // Retry — worlds / FancyNpcs can lag a tick behind enable.
        trySpawnSaved(worldName, config, 0);
    }

    private void trySpawnSaved(String worldName, FileConfiguration config, int attempt) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || !fancyReady()) {
            if (attempt < 40) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> trySpawnSaved(worldName, config, attempt + 1), 20L);
            } else if (world == null) {
                plugin.getLogger().warning("Dungeon Keeper world not loaded: " + worldName);
            } else {
                // Fancy still not ready — soft-ensure anyway (villager fallback ok).
                saved = locationFrom(config, world);
                ensureAt(saved);
            }
            return;
        }
        saved = locationFrom(config, world);
        // Soft ensure — never wipe Fancy's saved NPC on boot (that was despawning him).
        ensureAt(saved);
    }

    private static Location locationFrom(FileConfiguration config, World world) {
        return new Location(
                world,
                config.getDouble("keeper.x"),
                config.getDouble("keeper.y"),
                config.getDouble("keeper.z"),
                (float) config.getDouble("keeper.yaw"),
                (float) config.getDouble("keeper.pitch")
        );
    }

    private static boolean fancyReady() {
        if (!FancyNpcFacade.isAvailable()) {
            return true; // skip wait — fall through to villager
        }
        try {
            return FancyNpcFacade.isManagerLoaded(FancyNpcFacade.manager());
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    /**
     * Boot / restore path: keep existing FancyNPC if present, only create if missing.
     */
    public void ensureAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        saved = location.clone();
        location.getWorld().getChunkAt(location).load();
        removeEntityKeepersOnly();
        if (spawnFancy(location)) {
            plugin.getLogger().info("Dungeon Keeper ready as FancyNPC at "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return;
        }
        spawnVillagerFallback(location);
    }

    public void save(Location location) {
        saved = location.clone();
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        FileConfiguration config = new YamlConfiguration();
        config.set("keeper.world", location.getWorld().getName());
        config.set("keeper.x", location.getX());
        config.set("keeper.y", location.getY());
        config.set("keeper.z", location.getZ());
        config.set("keeper.yaw", location.getYaw());
        config.set("keeper.pitch", location.getPitch());
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save dungeon NPC: " + exception.getMessage());
        }
    }

    public void spawnAt(Location location) {
        // Admin place / relocate — replace everything, then persist.
        despawnEntitiesOnly(false);
        location.getWorld().getChunkAt(location).load();
        save(location);

        if (spawnFancy(location)) {
            plugin.getLogger().info("Dungeon Keeper spawned as FancyNPC at "
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
        villager.setCollidable(true);
        villager.setSilent(true);
        villager.setGravity(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.setAdult();
        villager.setAgeLock(true);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setVillagerLevel(1);
        villager.setVillagerExperience(0);
        villager.setRecipes(List.of());
        villager.addScoreboardTag(NPC_ID);
        villager.getEquipment().setItemInMainHand(new ItemStack(Material.LANTERN));
        tag(villager);

        Interaction hitbox = location.getWorld().spawn(location, Interaction.class);
        hitbox.setInteractionWidth(1.2f);
        hitbox.setInteractionHeight(2.2f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(true);
        hitbox.addScoreboardTag(NPC_ID);
        tag(hitbox);
        plugin.getLogger().info("Dungeon Keeper spawned as villager (FancyNpcs unavailable).");
    }

    private boolean spawnFancy(Location location) {
        if (!FancyNpcFacade.isAvailable()) {
            return false;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            Object existing = FancyNpcFacade.getNpc(manager, FANCY_NAME);
            if (existing != null) {
                try {
                    Object data = FancyNpcFacade.data(existing);
                    if (data != null) {
                        FancyNpcFacade.setLocation(data, location.clone());
                    }
                    FancyNpcFacade.setSaveToFile(existing, true);
                    FancyNpcFacade.moveForAll(existing);
                } catch (Throwable ignored) {
                }
                FancyNpcFacade.spawnForAll(existing);
                FancyNpcFacade.saveNpcs(manager, true);
                return true;
            }

            // Same path as LivingNpcService — String getNpc only (int overload throws).
            Object data = FancyNpcFacade.createNpcData(FANCY_NAME, new UUID(0L, 0L), location.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "§5§l" + DISPLAY_NAME);
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.invokeQuiet(data, "setSpawnEntity", boolean.class, true);

            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(npc, true);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            FancyNpcFacade.saveNpcs(manager, true);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("FancyNPC Dungeon Keeper failed: " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
            if (ex.getCause() != null) {
                plugin.getLogger().warning("  cause: " + ex.getCause());
            }
            // Do NOT removeFancyQuiet here — that wiped Fancy's npcs.yml on boot failures.
            return false;
        }
    }

    private void removeFancyQuiet(boolean persist) {
        try {
            if (!FancyNpcFacade.isAvailable()) {
                return;
            }
            Object manager = FancyNpcFacade.manager();
            Object npc = FancyNpcFacade.getNpc(manager, FANCY_NAME);
            if (npc == null) {
                return;
            }
            FancyNpcFacade.removeFromPlayersQuiet(npc);
            try {
                FancyNpcFacade.unregister(manager, npc);
            } catch (ReflectiveOperationException ignored) {
            }
            if (persist) {
                FancyNpcFacade.saveNpcs(manager, true);
            }
        } catch (Throwable ignored) {
        }
    }

    public Location getSaved() {
        return saved == null ? null : saved.clone();
    }

    public void despawnAll() {
        despawnEntitiesOnly(true);
        saved = null;
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete dungeon NPC save file.");
        }
    }

    private void despawnEntitiesOnly() {
        despawnEntitiesOnly(true);
    }

    private void despawnEntitiesOnly(boolean persistFancyRemoval) {
        Location previous = saved;
        if (previous == null) {
            previous = readSaved();
        }
        removeFancyQuiet(persistFancyRemoval);
        removeAround(previous);
        removeLoaded();
    }

    /** Boot path: strip leftover villagers/hitboxes only — leave FancyNPC alone. */
    private void removeEntityKeepersOnly() {
        Location previous = saved != null ? saved : readSaved();
        removeAround(previous);
        removeLoaded();
    }

    public void removeExisting() {
        despawnAll();
    }

    private Location readSaved() {
        if (!file.exists()) {
            return null;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString("keeper.world");
        if (worldName == null) {
            return null;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble("keeper.x"),
                config.getDouble("keeper.y"),
                config.getDouble("keeper.z"),
                (float) config.getDouble("keeper.yaw"),
                (float) config.getDouble("keeper.pitch")
        );
    }

    private void removeAround(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().getChunkAt(location).load();
        for (Entity entity : location.getWorld().getNearbyEntities(location, 16, 16, 16)) {
            if (isKeeper(plugin, entity)) {
                entity.remove();
            }
        }
    }

    private void removeLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (isKeeper(plugin, entity)) {
                    entity.remove();
                }
            }
        }
    }

    private void tag(Entity entity) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, NPC_ID);
    }
}
