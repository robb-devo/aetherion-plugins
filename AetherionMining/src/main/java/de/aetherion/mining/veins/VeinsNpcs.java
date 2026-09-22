package de.aetherion.mining.veins;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class VeinsNpcs {

    public static final String ID = "veins_foreman";
    public static final String DISPLAY = "Foreman";

    private final AetherionMining plugin;
    private final File file;
    private Location entrance;

    public VeinsNpcs(AetherionMining plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public static NamespacedKey key(AetherionMining plugin) {
        return new NamespacedKey(plugin, "veins_npc");
    }

    public static boolean isNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        AetherionMining plugin = AetherionMining.getInstance();
        if (plugin != null && entity.getPersistentDataContainer().has(key(plugin), PersistentDataType.STRING)) {
            return true;
        }
        return entity.getScoreboardTags().contains(ID);
    }

    public static ItemStack anchor() {
        ItemStack item = new ItemStack(Material.LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6NPC Anchor §7(" + DISPLAY + ")");
            meta.setLore(List.of(
                    "§7Right-click a block to place",
                    "§f" + DISPLAY + "§7 there.",
                    "§eSneak + right-click §7removes him.",
                    "",
                    "§7Players talk to him for",
                    "§eThe Veins §7teleport.",
                    "§8Requires Mining Skill 30+",
                    "",
                    "§8Admin tool — not consumed"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            AetherionMining plugin = AetherionMining.getInstance();
            if (plugin != null) {
                meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "anchor");
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        AetherionMining plugin = AetherionMining.getInstance();
        if (plugin == null || item == null || !item.hasItemMeta()) {
            return false;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        return "anchor".equals(tag);
    }

    /**
     * Restore optional admin Foreman elsewhere — never in Amethyst Mines ({@code aether_veins}).
     * If the saved entrance was in the veins world, purge it and drop the save.
     */
    public void loadEntrance() {
        World veinsWorld = veinsWorld();
        if (veinsWorld != null) {
            clearAllInWorld(veinsWorld);
        }
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString("entrance.world");
        if (worldName == null) {
            return;
        }
        if (isVeinsWorldName(worldName)) {
            // Legacy save pointed at Amethyst — zero NPCs there.
            if (veinsWorld != null) {
                clearAllInWorld(veinsWorld);
            }
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete Amethyst Foreman save in npcs.yml.");
            }
            entrance = null;
            plugin.getLogger().info("Cleared Foreman from aether_veins (Amethyst has zero NPCs).");
            return;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return;
        }
        entrance = new Location(
                world,
                config.getDouble("entrance.x"),
                config.getDouble("entrance.y"),
                config.getDouble("entrance.z"),
                (float) config.getDouble("entrance.yaw"),
                (float) config.getDouble("entrance.pitch")
        );
        removeAllEntrances(world);
        spawnAt(entrance, false);
    }

    /**
     * Admin placement only — refused in Amethyst Mines.
     *
     * @return false if the world is aether_veins
     */
    public boolean spawnEntrance(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (isVeinsWorld(location.getWorld())) {
            clearAllInWorld(location.getWorld());
            return false;
        }
        removeAround(entrance);
        entrance = location.clone();
        spawnAt(entrance, false);
        save();
        return true;
    }

    /** Remove every Foreman / veins NPC in this world — Amethyst Mines has no NPCs. */
    public void clearAllInWorld(World world) {
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (isNpc(entity) || isOrphanForeman(entity)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " Foreman/veins NPC entit(y/ies) from " + world.getName() + ".");
        }
        if (entrance != null && entrance.getWorld() != null
                && entrance.getWorld().getUID().equals(world.getUID())) {
            entrance = null;
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Could not delete veins NPC save after Amethyst purge.");
            }
        }
    }

    /** Match leftover persistent villagers named Foreman even if PDC was lost. */
    private static boolean isOrphanForeman(Entity entity) {
        if (!(entity instanceof Villager) && !(entity instanceof Interaction)) {
            return false;
        }
        if (entity.getScoreboardTags().contains(ID)) {
            return true;
        }
        String name = entity.getCustomName();
        return name != null && name.contains(DISPLAY);
    }

    private World veinsWorld() {
        AetherionMining mining = AetherionMining.getInstance();
        if (mining == null || mining.getVeins() == null) {
            String name = plugin.getConfig().getString("veins.world", "aether_veins");
            return plugin.getServer().getWorld(name);
        }
        return mining.getVeins().world() != null
                ? mining.getVeins().world()
                : plugin.getServer().getWorld(mining.getVeins().worldName());
    }

    private boolean isVeinsWorld(World world) {
        return world != null && isVeinsWorldName(world.getName());
    }

    private boolean isVeinsWorldName(String worldName) {
        if (worldName == null) {
            return false;
        }
        String configured = plugin.getConfig().getString("veins.world", "aether_veins");
        return worldName.equalsIgnoreCase(configured) || worldName.equalsIgnoreCase("aether_veins");
    }

    /** @deprecated Amethyst Mines has no exit NPC — use {@link #clearAllInWorld(World)}. */
    @Deprecated
    public void spawnExit(World world, Location spawn) {
        clearAllInWorld(world);
    }

    /** @deprecated Amethyst Mines has no exit NPC. */
    @Deprecated
    public void spawnExit(World world, int hubY) {
        clearAllInWorld(world);
    }

    public void removeEntrance() {
        removeAround(entrance);
        entrance = null;
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete veins NPC save.");
        }
    }

    public boolean tryRemoveClicked(Entity entity) {
        if (!isNpc(entity)) {
            return false;
        }
        Location at = entity.getLocation();
        if (entrance != null && at.getWorld() != null && at.getWorld().equals(entrance.getWorld())
                && at.distanceSquared(entrance) < 16) {
            removeEntrance();
            return true;
        }
        entity.remove();
        return true;
    }

    private void spawnAt(Location location, boolean exit) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!exit) {
            removeAround(location);
        }
        location.getWorld().getChunkAt(location).load();
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setCustomName("§6" + DISPLAY);
        // false = name only when looking at him, not through walls
        villager.setCustomNameVisible(false);
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
        villager.addScoreboardTag(ID);
        villager.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        tag(villager, exit ? "exit" : "entrance");

        Interaction hitbox = location.getWorld().spawn(location, Interaction.class);
        hitbox.setInteractionWidth(1.2f);
        hitbox.setInteractionHeight(2.2f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(true);
        hitbox.addScoreboardTag(ID);
        tag(hitbox, exit ? "exit" : "entrance");
    }

    private void tag(Entity entity, String kind) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, kind);
    }

    private void save() {
        if (entrance == null || entrance.getWorld() == null) {
            return;
        }
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        FileConfiguration config = new YamlConfiguration();
        config.set("entrance.world", entrance.getWorld().getName());
        config.set("entrance.x", entrance.getX());
        config.set("entrance.y", entrance.getY());
        config.set("entrance.z", entrance.getZ());
        config.set("entrance.yaw", entrance.getYaw());
        config.set("entrance.pitch", entrance.getPitch());
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save veins NPC: " + exception.getMessage());
        }
    }

    private void removeAround(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().getChunkAt(location).load();
        for (Entity entity : location.getWorld().getNearbyEntities(location, 24, 24, 24)) {
            if (isNpc(entity)) {
                entity.remove();
            }
        }
    }

    private void removeAllEntrances(World world) {
        if (world == null) {
            return;
        }
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (!isNpc(entity)) {
                continue;
            }
            String kind = entity.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
            if (kind == null || "entrance".equals(kind)) {
                entity.remove();
            }
        }
    }

    private void removeHub(World world) {
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (!isNpc(entity)) {
                continue;
            }
            String kind = entity.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
            if ("exit".equals(kind)) {
                entity.remove();
            }
        }
    }
}
