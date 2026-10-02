package de.aetherion.mining.isle;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
 * Spawns and runs the Mining Eldervale cast. Placements live in {@code mine-cast.yml}. The villagers
 * are NOT persistent: they are rebuilt whenever their chunk loads, so restarts and reloads never
 * stack duplicates. Right-click opens the NPC's board. Walk past one and you get a line. On first
 * start, every role with a preset is placed automatically, so a fresh server has a cast.
 */
public final class MineCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private record Spawned(UUID villager, UUID hologram) {
    }

    private final MineIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<MineRole, Location> placed = new EnumMap<>(MineRole.class);
    /** Saved spots whose world wasn't loaded yet: kept (and re-saved) until it is. */
    private final Map<MineRole, ConfigurationSection> pending = new EnumMap<>(MineRole.class);
    private final Map<MineRole, Spawned> live = new EnumMap<>(MineRole.class);
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private boolean seeded;
    private BukkitTask task;

    MineCast(MineIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "mine_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "mine_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "mine-cast.yml");
        load();
    }

    void start() {
        purgeStrays();
        for (MineRole role : List.copyOf(pending.keySet())) {
            Location at = MineWorld.location(pending.get(role));
            if (at != null) {
                placed.put(role, at);
                pending.remove(role);
            }
        }
        if (!seeded && isle.plugin().getConfig().getBoolean("mine-cast.auto-place", true)) {
            for (MineRole role : MineRole.values()) {
                if (!placed.containsKey(role) && preset(role) != null) {
                    placed.put(role, preset(role));
                }
            }
            seeded = true;
            save();
        }
        ensureAll();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (MineRole role : MineRole.values()) {
            despawn(role);
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(MineRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(MineRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    /** Configured preset spot for {@code role} (config {@code mine-cast.presets.<role>}: "x y z yaw"). */
    public Location preset(MineRole role) {
        String raw = isle.plugin().getConfig().getString("mine-cast.presets." + role.id());
        return raw == null ? null : MineWorld.point(isle.plugin(), raw);
    }

    /** Placed spot, else preset: where the compass points for this NPC. */
    public Location whereabouts(MineRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(MineRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        pending.remove(role);
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(MineRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (mine-cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String placeAllPresets() {
        int count = 0;
        for (MineRole role : MineRole.values()) {
            if (preset(role) != null) {
                placePreset(role);
                count++;
            }
        }
        return "§aPlaced §f" + count + "§a Mining Eldervale NPCs on their presets.";
    }

    public String remove(MineRole role) {
        pending.remove(role);
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    public String removeAll() {
        for (MineRole role : MineRole.values()) {
            despawn(role);
        }
        int count = placed.size();
        placed.clear();
        pending.clear();
        save();
        return "§eRemoved §f" + count + "§e Mining Eldervale NPCs.";
    }

    // ------------------------------------------------------------------ anchors

    public ItemStack anchor(MineRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Mining Eldervale NPC anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8Presets: DEV → Mining Island → NPC Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private MineRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return MineRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        MineRole role = anchorRole(player.getInventory().getItemInMainHand());
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
        for (MineRole role : placed.keySet()) {
            ensure(role);
        }
    }

    private boolean alive(MineRole role) {
        Spawned spawned = live.get(role);
        if (spawned == null) {
            return false;
        }
        Entity villager = Bukkit.getEntity(spawned.villager());
        return villager != null && villager.isValid();
    }

    private void ensure(MineRole role) {
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
            text.text(MineText.legacy(role.color() + "§l" + role.display() + "\n§7" + role.title()
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

    private void despawn(MineRole role) {
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
        for (Map.Entry<MineRole, Location> entry : placed.entrySet()) {
            Location at = entry.getValue();
            if (at.getWorld() != null && at.getWorld().equals(chunk.getWorld())
                    && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                MineRole role = entry.getKey();
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(role));
            }
        }
    }

    public MineRole roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return MineRole.byId(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        MineRole role = roleOf(event.getRightClicked());
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
        isle.compass().met(player, role);
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

    void forget(UUID id) {
        clickCool.remove(id);
        String prefix = id + ":";
        barked.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void say(Player player, MineRole role, String line) {
        if (player != null) {
            player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
        }
    }

    /** Little flourish at an NPC (forge sparks, hearth smoke) for menu moments. */
    void flourish(MineRole role, Particle particle, int count) {
        Location at = location(role);
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(particle, at.clone().add(0, 1.2, 0), count, 0.3, 0.4, 0.3, 0.02);
    }

    private void tick() {
        if (Bukkit.getCurrentTick() % 100 < 10) {
            ensureAll();
        }
        Map<MineRole, Villager> villagers = new HashMap<>();
        for (Map.Entry<MineRole, Spawned> entry : live.entrySet()) {
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
            if (!MineWorld.onIsle(isle.plugin(), player)) {
                continue;
            }
            for (Map.Entry<MineRole, Villager> entry : villagers.entrySet()) {
                Villager villager = entry.getValue();
                if (!villager.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distance = villager.getLocation().distance(player.getLocation());
                if (distance <= TURN_RANGE) {
                    face(villager, player);
                }
                if (distance <= BARK_RANGE) {
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
        seeded = config.getBoolean("seeded", false);
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            MineRole role = MineRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            Location at = MineWorld.location(section);
            if (at != null) {
                placed.put(role, at);
            } else {
                pending.put(role, section);
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("seeded", seeded);
        pending.forEach((role, section) -> {
            if (!placed.containsKey(role)) {
                config.createSection("roles." + role.id(), section.getValues(false));
            }
        });
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
            isle.plugin().getLogger().warning("Could not save mine-cast.yml: " + exception.getMessage());
        }
    }
}
