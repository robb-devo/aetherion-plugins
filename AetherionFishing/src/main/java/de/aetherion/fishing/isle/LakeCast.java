package de.aetherion.fishing.isle;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns and runs the Fishing Eldervale cast. Placements live in {@code isle-cast.yml}; the entities
 * are NOT persistent — they are rebuilt whenever their chunk loads, so restarts and reloads never
 * stack duplicate villagers. Right-click opens the NPC's board; walking past gets you a line.
 */
public final class LakeCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private record Spawned(UUID villager, UUID hologram) {
    }

    private final FishIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<LakeRole, Location> placed = new EnumMap<>(LakeRole.class);
    private final Map<LakeRole, Spawned> live = new EnumMap<>(LakeRole.class);
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private BukkitTask task;

    LakeCast(FishIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "lake_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "lake_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "isle-cast.yml");
        load();
    }

    void start() {
        purgeStrays();
        ensureAll();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (LakeRole role : LakeRole.values()) {
            despawn(role);
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(LakeRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(LakeRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    /** Configured preset spot for {@code role} (config {@code isle-cast.presets.<role>}). */
    public Location preset(LakeRole role) {
        return LakeWorld.location(isle.plugin().getConfig().getConfigurationSection("isle-cast.presets." + role.id()));
    }

    /** Placed spot, else preset — where the wayfinder points for this NPC. */
    public Location whereabouts(LakeRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(LakeRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(LakeRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (isle-cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String remove(LakeRole role) {
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    // ------------------------------------------------------------------ anchors

    public ItemStack anchor(LakeRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Fishing Eldervale NPC anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8Presets: DEV → Fishing Island → NPC Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private LakeRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return LakeRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        LakeRole role = anchorRole(player.getInventory().getItemInMainHand());
        if (role == null || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("aetherion.dev") && !player.isOp()) {
            return;
        }
        if (player.isSneaking()) {
            player.sendMessage(remove(role));
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getRelative(BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
        at.setYaw(player.getLocation().getYaw() + 180.0f);
        player.sendMessage(place(role, at));
        player.playSound(at, Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.1f);
    }

    // ------------------------------------------------------------------ entities

    private void ensureAll() {
        for (LakeRole role : placed.keySet()) {
            ensure(role);
        }
    }

    private boolean alive(LakeRole role) {
        Spawned spawned = live.get(role);
        if (spawned == null) {
            return false;
        }
        Entity villager = Bukkit.getEntity(spawned.villager());
        return villager != null && villager.isValid();
    }

    private void ensure(LakeRole role) {
        Location at = placed.get(role);
        if (at == null || at.getWorld() == null || alive(role)) {
            return;
        }
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        despawn(role);
        Villager villager = at.getWorld().spawn(at, Villager.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setCollidable(false);
            spawned.setGravity(false);
            spawned.setAdult();
            spawned.setAgeLock(true);
            spawned.setProfession(role.profession());
            spawned.setVillagerType(role.villagerType());
            spawned.setVillagerLevel(5);
            spawned.setRecipes(new ArrayList<MerchantRecipe>());
            spawned.customName(Component.text(role.display()));
            spawned.setCustomNameVisible(false);
            spawned.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role.id());
            if (spawned.getEquipment() != null) {
                spawned.getEquipment().setItemInMainHand(new ItemStack(role.icon()));
            }
        });
        TextDisplay hologram = at.getWorld().spawn(at.clone().add(0, 2.35, 0), TextDisplay.class, text -> {
            text.text(LakeText.legacy(role.color() + "§l" + role.display() + "\n§7" + role.title()
                    + "\n§e▸ §7" + role.role()));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setViewRange(0.35f);
            text.setPersistent(false);
            text.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role.id());
        });
        live.put(role, new Spawned(villager.getUniqueId(), hologram.getUniqueId()));
    }

    private void despawn(LakeRole role) {
        Spawned spawned = live.remove(role);
        if (spawned == null) {
            return;
        }
        for (UUID id : List.of(spawned.villager(), spawned.hologram())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Leftover tagged entities (older builds, crashes) in loaded chunks. */
    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        live.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Map.Entry<LakeRole, Location> entry : placed.entrySet()) {
            Location at = entry.getValue();
            if (at.getWorld() != null && at.getWorld().equals(chunk.getWorld())
                    && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                LakeRole role = entry.getKey();
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(role));
            }
        }
    }

    private LakeRole roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return LakeRole.byId(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        LakeRole role = roleOf(event.getRightClicked());
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long cool = clickCool.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        clickCool.put(player.getUniqueId(), now + 400L);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.7f, 1.1f);
        isle.menus().open(player, role);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (roleOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    void say(Player player, LakeRole role, String line) {
        player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
    }

    private void tick() {
        if (Bukkit.getCurrentTick() % 100 < 10) {
            ensureAll();
        }
        Map<LakeRole, Villager> villagers = new HashMap<>();
        for (Map.Entry<LakeRole, Spawned> entry : live.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getValue().villager());
            if (entity instanceof Villager villager && villager.isValid()) {
                villagers.put(entry.getKey(), villager);
            }
        }
        if (villagers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!LakeWorld.onIsle(player)) {
                continue;
            }
            for (Map.Entry<LakeRole, Villager> entry : villagers.entrySet()) {
                Villager villager = entry.getValue();
                if (!villager.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distance = villager.getLocation().distance(player.getLocation());
                if (distance <= TURN_RANGE) {
                    face(villager, player);
                }
                if (distance <= BARK_RANGE && !isle.casting(player)) {
                    String key = player.getUniqueId() + ":" + entry.getKey().id();
                    Long last = barked.get(key);
                    if (last == null || now - last > BARK_COOLDOWN_MS) {
                        barked.put(key, now);
                        List<String> barks = entry.getKey().barks();
                        say(player, entry.getKey(), barks.get(ThreadLocalRandom.current().nextInt(barks.size())));
                    }
                }
            }
        }
    }

    private static void face(Villager villager, Player player) {
        Location from = villager.getLocation();
        double dx = player.getX() - from.getX();
        double dz = player.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (Math.abs(((yaw - from.getYaw()) % 360 + 540) % 360 - 180) > 4.0f) {
            villager.setRotation(yaw, 0.0f);
        }
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            LakeRole role = LakeRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", "world"));
            if (world == null) {
                continue;
            }
            placed.put(role, new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw", 0.0d), 0.0f));
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        placed.forEach((role, at) -> {
            String path = "roles." + role.id();
            config.set(path + ".world", at.getWorld() == null ? "world" : at.getWorld().getName());
            config.set(path + ".x", at.getX());
            config.set(path + ".y", at.getY());
            config.set(path + ".z", at.getZ());
            config.set(path + ".yaw", at.getYaw());
        });
        try {
            if (!isle.plugin().getDataFolder().exists()) {
                isle.plugin().getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Could not save isle-cast.yml: " + exception.getMessage());
        }
    }
}
