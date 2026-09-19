package de.aetherion.foraging.ritual;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.ForageKeys;
import de.aetherion.foraging.habitat.ForageHabitatService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grove enchanting table — place only via DEV anchor (no auto-place).
 */
public final class GroveRitualService implements Listener {

    private final AetherionForaging plugin;
    private final Map<UUID, Long> busyUntil = new ConcurrentHashMap<>();
    private Location grove;
    private boolean placed;

    public GroveRitualService(AetherionForaging plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        var cfg = plugin.getConfig();
        placed = cfg.getBoolean("rituals.placed", false);
        String worldName = cfg.getString("rituals.grove.world", cfg.getString("forage-isle.world", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        double x = cfg.getDouble("rituals.grove.x", 560.5);
        double y = cfg.getDouble("rituals.grove.y", 91.0);
        double z = cfg.getDouble("rituals.grove.z", -200.5);
        grove = world == null ? null : new Location(world, x, y, z);
        if (!cfg.getBoolean("rituals.enabled", true)) {
            plugin.getLogger().info("Grove rituals disabled in config.");
        } else if (placed && grove != null) {
            plugin.getLogger().info("Grove table registered at "
                    + grove.getBlockX() + "," + grove.getBlockY() + "," + grove.getBlockZ()
                    + " (DEV-placed, no auto-build)");
        } else {
            plugin.getLogger().info("Grove table not placed yet — use DEV menu anchor.");
        }
    }

    public Location grove() {
        return grove == null ? null : grove.clone();
    }

    public boolean isPlaced() {
        return placed && grove != null;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2Grove Enchanting Table");
            meta.setLore(List.of(
                    "§7DEV · place the forage grove rite table.",
                    "§7Heartwood pairs → 3 rotating rites.",
                    "",
                    "§eRight-click a block to place.",
                    "§eSneak + right-click §7removes it."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ForageKeys.groveTableAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(ForageKeys.groveTableAnchor(), PersistentDataType.BYTE);
    }

    /** Place table on the clicked block (top). No floating auto-spawn. */
    public void placeAt(Block against, Player player) {
        if (against == null || against.getWorld() == null) {
            return;
        }
        Block target = against.getRelative(org.bukkit.block.BlockFace.UP);
        // Prefer replacing air above; if against is replaceable use against.
        if (!target.getType().isAir() && against.getType().isAir()) {
            target = against;
        }
        if (!target.getType().isAir() && target.getType() != Material.ENCHANTING_TABLE) {
            // Place replacing against top face by using against itself if air-like grass
            if (against.getType() == Material.SHORT_GRASS
                    || against.getType() == Material.TALL_GRASS
                    || against.getType().name().contains("CARPET")) {
                target = against;
            } else {
                player.sendMessage("§cNeed clear space above that block.");
                return;
            }
        }
        removeCurrentTableBlock();
        target.setType(Material.ENCHANTING_TABLE, false);
        Location at = target.getLocation().add(0.5, 0, 0.5);
        setGrove(at, false);
        plugin.getConfig().set("rituals.placed", true);
        plugin.saveConfig();
        placed = true;
        player.sendMessage("§aGrove table placed §7at §f"
                + target.getX() + " " + target.getY() + " " + target.getZ());
        player.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
    }

    public void packUp(Player player) {
        removeCurrentTableBlock();
        placed = false;
        plugin.getConfig().set("rituals.placed", false);
        plugin.saveConfig();
        if (player != null) {
            player.sendMessage("§eGrove table packed. §7DEV anchor still works to re-place.");
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.6f, 0.9f);
        }
    }

    private void removeCurrentTableBlock() {
        if (grove == null || grove.getWorld() == null) {
            return;
        }
        Block block = grove.getBlock();
        if (block.getType() == Material.ENCHANTING_TABLE) {
            block.setType(Material.AIR, false);
        }
    }

    public void setGrove(Location at, boolean placeTable) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        grove = at.clone();
        plugin.getConfig().set("rituals.enabled", true);
        plugin.getConfig().set("rituals.grove.world", at.getWorld().getName());
        plugin.getConfig().set("rituals.grove.x", at.getX());
        plugin.getConfig().set("rituals.grove.y", at.getY());
        plugin.getConfig().set("rituals.grove.z", at.getZ());
        plugin.saveConfig();
        if (placeTable) {
            Block block = at.getBlock();
            block.setType(Material.ENCHANTING_TABLE, false);
            plugin.getConfig().set("rituals.placed", true);
            plugin.saveConfig();
            placed = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isAnchor(hand)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            packUp(player);
            return;
        }
        placeAt(event.getClickedBlock(), player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (isAnchor(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("rituals.enabled", true) || !placed) {
            return;
        }
        if (grove == null || grove.getWorld() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        if (!ForageHabitatService.inIsleFootprint(clicked.getLocation())) {
            return;
        }
        if (!nearGrove(clicked.getLocation().add(0.5, 0.5, 0.5))) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Long busy = busyUntil.get(player.getUniqueId());
        if (busy != null && busy > System.currentTimeMillis()) {
            player.sendMessage("§7The grove is still settling…");
            return;
        }
        GroveRiteGUI.open(plugin, this, player);
    }

    private boolean nearGrove(Location at) {
        if (at.getWorld() == null || !at.getWorld().equals(grove.getWorld())) {
            return false;
        }
        double reach = plugin.getConfig().getDouble("rituals.grove.reach", 4.5);
        return at.distanceSquared(grove) <= reach * reach;
    }

    public void enact(Player player, GroveRiteCatalog.Rite rite) {
        if (player == null || rite == null) {
            return;
        }
        int buffSeconds = plugin.getConfig().getInt("rituals.buff-seconds", 240);
        int weatherSeconds = plugin.getConfig().getInt("rituals.weather-seconds", 300);
        busyUntil.put(player.getUniqueId(), System.currentTimeMillis() + 4000L);

        PotionEffectType primary = rite.primary();
        if (primary != null) {
            player.addPotionEffect(new PotionEffect(
                    primary, buffSeconds * 20, Math.max(0, rite.primaryAmp()), false, true, true));
        }
        if (rite.secondary() != null) {
            player.addPotionEffect(new PotionEffect(
                    rite.secondary(), buffSeconds * 20, 0, false, true, true));
        }
        if (rite.weather() != null && plugin.weather() != null) {
            plugin.weather().setRitualOverride(player, rite.weather(), weatherSeconds);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        player.sendMessage("§a✦ " + rite.title() + " §8· §7" + rite.blurb());
        Location fx = grove != null ? grove.clone().add(0, 1.1, 0) : player.getLocation();
        if (fx.getWorld() != null) {
            fx.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, fx, 28, 0.45, 0.55, 0.45, 0.15);
            fx.getWorld().spawnParticle(Particle.ENCHANT, fx, 40, 0.6, 0.5, 0.6, 0.5);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busyUntil.remove(event.getPlayer().getUniqueId());
    }
}
