package de.aetherion.pit.listener;

import de.aetherion.pit.AetherionPit;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PitZoneListener implements Listener {

    private final AetherionPit plugin;
    private final Map<UUID, Boolean> wasSafe = new ConcurrentHashMap<>();

    public PitZoneListener(AetherionPit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location spawn = plugin.safeZone().spawn();
        if (spawn != null && plugin.getConfig().getBoolean("force-spawn-on-join", true)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(spawn);
                    // Strip DeluxeHub join Speed if still applied.
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                }
            });
        }
        if (spawn != null && plugin.safeZone().isPitWorld(spawn.getWorld())) {
            wasSafe.put(player.getUniqueId(), true);
        }
        boolean firstJoin = !player.hasPlayedBefore();
        // 40 ticks: the client has sent its language by then, so title and card come in German for German clients.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setLevel(0);
            player.setExp(0f);
            stripPitStarter(player);
            player.removePotionEffect(PotionEffectType.SPEED);
            plugin.guide().welcome(player, firstJoin);
        }, 40L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = plugin.safeZone().spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        wasSafe.put(event.getPlayer().getUniqueId(), true);
    }

    /** The old Pit's unbreakable starter kit (wood sword, leather) has no use on the hub any more (11-27). */
    private static void stripPitStarter(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) {
            ItemStack it = all[i];
            if (it == null || !it.hasItemMeta() || !it.getItemMeta().isUnbreakable() || it.getItemMeta().hasDisplayName()) {
                continue;
            }
            Material m = it.getType();
            if (m == Material.WOODEN_SWORD || m == Material.LEATHER_CHESTPLATE || m == Material.LEATHER_BOOTS) {
                inv.setItem(i, null);
            }
        }
    }

    /**
     * Entering combat zone naked → wood sword (hotbar 1) + leather chest/boots.
     */
    public static void giveCombatStarterIfNaked(Player player) {
        if (player == null || !player.isOnline() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        boolean hasWeapon = hasCombatWeapon(inv);
        boolean hasChest = inv.getChestplate() != null && inv.getChestplate().getType() != Material.AIR;
        boolean hasBoots = inv.getBoots() != null && inv.getBoots().getType() != Material.AIR;
        if (hasWeapon && hasChest && hasBoots) {
            return;
        }
        if (!hasWeapon) {
            ItemStack slot0 = inv.getItem(0);
            if (slot0 == null || slot0.getType() == Material.AIR) {
                inv.setItem(0, unbreakable(new ItemStack(Material.WOODEN_SWORD)));
            } else {
                inv.addItem(unbreakable(new ItemStack(Material.WOODEN_SWORD)));
            }
        }
        if (!hasChest) {
            inv.setChestplate(unbreakable(new ItemStack(Material.LEATHER_CHESTPLATE)));
        }
        if (!hasBoots) {
            inv.setBoots(unbreakable(new ItemStack(Material.LEATHER_BOOTS)));
        }
        player.sendMessage(de.aetherion.pit.util.Msg.amp("&7Starter kit equipped &8(wood sword + leather)."));
    }

    private static boolean hasCombatWeapon(PlayerInventory inv) {
        for (ItemStack stack : inv.getContents()) {
            if (stack == null) {
                continue;
            }
            String n = stack.getType().name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE") || stack.getType() == Material.BOW
                    || stack.getType() == Material.CROSSBOW || stack.getType() == Material.TRIDENT) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack unbreakable(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
