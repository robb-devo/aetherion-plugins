package de.aetherion.hub.origin;

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
import org.bukkit.event.entity.EntityTransformEvent;
import com.destroystokyo.paper.event.entity.EntityZapEvent;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the Origin cast (see {@link OriginRole}). Placements live in {@code plugins/AetherionHub/origin-cast.yml}.
 * The villagers are not persistent: they are rebuilt whenever their chunk loads, so restarts never stack
 * duplicates, and strays from older builds are purged on start. On a fresh server every role with a preset is
 * placed once (origin.yml {@code cast.auto-place}).
 */
public final class OriginCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private record Spawned(UUID villager, UUID hologram) {
    }

    private final OriginIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<OriginRole, Location> placed = new EnumMap<>(OriginRole.class);
    private final Map<OriginRole, ConfigurationSection> pending = new EnumMap<>(OriginRole.class);
    private final Map<OriginRole, Spawned> live = new EnumMap<>(OriginRole.class);
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private boolean seeded;
    private int ensureTick;

    OriginCast(OriginIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "origin_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "origin_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "origin-cast.yml");
        load();
    }

    void start() {
        purgeStrays();
        for (OriginRole role : List.copyOf(pending.keySet())) {
            Location at = fromSection(pending.get(role));
            if (at != null) {
                placed.put(role, at);
                pending.remove(role);
            }
        }
        if (!seeded && isle.config().castAutoPlace()) {
            for (OriginRole role : OriginRole.values()) {
                if (!placed.containsKey(role) && !pending.containsKey(role) && preset(role) != null) {
                    placed.put(role, preset(role));
                }
            }
            seeded = true;
            save();
        }
        for (OriginRole role : placed.keySet()) {
            ensure(role);
        }
    }

    void shutdown() {
        for (OriginRole role : OriginRole.values()) {
            despawn(role);
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(OriginRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(OriginRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    public Location preset(OriginRole role) {
        double[] p = isle.config().castPresets().get(role.id());
        return p == null ? null : isle.config().location(p);
    }

    /** Placed spot, else preset: where the compass points. */
    public Location whereabouts(OriginRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(OriginRole role, Location at) {
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

    public String placePreset(OriginRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (origin.yml cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String placeAllPresets() {
        int count = 0;
        for (OriginRole role : OriginRole.values()) {
            if (preset(role) != null) {
                placePreset(role);
                count++;
            }
        }
        return "§aPlaced §f" + count + "§a Origin townsfolk on their presets.";
    }

    public String remove(OriginRole role) {
        pending.remove(role);
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    public String removeAll() {
        for (OriginRole role : OriginRole.values()) {
            despawn(role);
        }
        int count = placed.size();
        placed.clear();
        pending.clear();
        save();
        return "§eRemoved §f" + count + "§e Origin townsfolk.";
    }

    // ------------------------------------------------------------------ DEV anchors

    public ItemStack anchor(OriginRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Origin townsfolk anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8/origin dev → Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private OriginRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return OriginRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        OriginRole role = anchorRole(player.getInventory().getItemInMainHand());
        if (role == null || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (!OriginCommand.dev(player)) {
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

    private boolean alive(OriginRole role) {
        Spawned spawned = live.get(role);
        if (spawned == null) {
            return false;
        }
        Entity villager = Bukkit.getEntity(spawned.villager());
        return villager != null && villager.isValid();
    }

    private void ensure(OriginRole role) {
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
            text.text(OriginText.legacy(role.color() + "§l" + role.display() + "\n§7" + role.title() + "\n§e▸ §7" + role.role()));
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

    private void despawn(OriginRole role) {
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
        if (!isle.running()) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (Map.Entry<OriginRole, Location> entry : placed.entrySet()) {
            Location at = entry.getValue();
            if (at.getWorld() != null && at.getWorld().equals(chunk.getWorld())
                    && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                OriginRole role = entry.getKey();
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(role));
            }
        }
    }

    public OriginRole roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return OriginRole.byId(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        OriginRole role = roleOf(event.getRightClicked());
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
        boolean first = !isle.profiles().get(player).met.contains(role.id());
        if (first) {
            say(player, role, role.barks().get(0));
        }
        isle.compass().talked(player, role);
        isle.menus().openBoard(player, role);
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

    /** Lightning would turn a townsperson into an untagged, persistent witch. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onZap(EntityZapEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    public void say(Player player, OriginRole role, String line) {
        if (player != null) {
            player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
        }
    }

    /** A little flourish at an NPC for menu moments. */
    void flourish(OriginRole role, Particle particle, int count) {
        Location at = location(role);
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(particle, at.clone().add(0, 1.2, 0), count, 0.3, 0.4, 0.3, 0.02);
    }

    /** Every 20 ticks from the Origin loop. */
    void tick(List<Player> onIsle) {
        if (++ensureTick % 5 == 0) {
            for (OriginRole role : placed.keySet()) {
                ensure(role);
            }
        }
        if (onIsle.isEmpty() || live.isEmpty()) {
            return;
        }
        Map<OriginRole, Villager> villagers = new EnumMap<>(OriginRole.class);
        for (Map.Entry<OriginRole, Spawned> entry : live.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getValue().villager());
            if (entity instanceof Villager villager && villager.isValid()) {
                villagers.put(entry.getKey(), villager);
            }
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            boolean quiet = isle.quiet(player);
            for (Map.Entry<OriginRole, Villager> entry : villagers.entrySet()) {
                Villager villager = entry.getValue();
                if (!villager.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distance = villager.getLocation().distance(player.getLocation());
                if (distance <= TURN_RANGE) {
                    face(villager, player);
                }
                if (!quiet && distance <= BARK_RANGE) {
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
        // The Stargazer's scope glints at night; the Tender's glowberries shimmer.
        if (isle.night()) {
            Villager stellan = villagers.get(OriginRole.STARGAZER);
            if (stellan != null) {
                stellan.getWorld().spawnParticle(Particle.END_ROD, stellan.getLocation().add(0, 2.1, 0), 1, 0.15, 0.1, 0.15, 0.0);
            }
            Villager fen = villagers.get(OriginRole.TENDER);
            if (fen != null) {
                fen.getWorld().spawnParticle(Particle.GLOW, fen.getLocation().add(0, 1.0, 0), 2, 0.4, 0.5, 0.4, 0.0);
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

    void forget(UUID id) {
        clickCool.remove(id);
        String prefix = id + ":";
        barked.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ------------------------------------------------------------------ persistence

    private static Location fromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        Location at = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
        at.setYaw((float) section.getDouble("yaw", 0.0d));
        return at;
    }

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
            OriginRole role = OriginRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            Location at = fromSection(section);
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
            isle.plugin().getLogger().warning("Could not save origin-cast.yml: " + exception.getMessage());
        }
    }
}
