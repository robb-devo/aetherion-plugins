package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.ItemPresentation;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nether is open. You keep your stuff. You build with what the Nether
 * actually gave you — not the cobble suitcase from spawn.
 */
public final class NetherRealm implements Listener {

    public static boolean isNether(World world) {
        return world != null && world.getEnvironment() == World.Environment.NETHER;
    }

    public static boolean isNether(Player player) {
        return player != null && isNether(player.getWorld());
    }

    public static boolean isNether(Block block) {
        return block != null && isNether(block.getWorld());
    }

    public static void markMined(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || isMarked(item)) {
            return;
        }
        meta.getPersistentDataContainer().set(ItemKeys.netherMined(), PersistentDataType.BYTE, (byte) 1);
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        boolean present = false;
        for (String line : lore) {
            if (line != null && line.contains("Mined in the Nether")) {
                present = true;
                break;
            }
        }
        if (!present) {
            lore.add("§8Mined in the Nether");
            meta.setLore(lore);
        }
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
    }

    public static boolean isMarked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte tagged = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.netherMined(), PersistentDataType.BYTE);
        return tagged != null && tagged == 1;
    }

    public static boolean canPlace(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (isMarked(item)) {
            return true;
        }
        return isNetherBorn(item.getType().name());
    }

    static boolean isNetherBorn(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.contains("nether")
                || key.contains("crimson")
                || key.contains("warped")
                || key.contains("blackstone")
                || key.contains("basalt")
                || key.contains("soul")
                || key.contains("magma")
                || key.contains("glowstone")
                || key.contains("quartz")
                || key.contains("shroomlight")
                || key.contains("nylium")
                || key.contains("weeping")
                || key.contains("twisting")
                || key.contains("ancient_debris")
                || key.contains("netherite")) {
            return true;
        }
        return switch (name) {
            case "OBSIDIAN", "CRYING_OBSIDIAN", "FIRE", "SOUL_FIRE", "LAVA",
                 "ENDER_CHEST", "RESPAWN_ANCHOR", "BONE_BLOCK", "GOLD_BLOCK",
                 "GILDED_BLACKSTONE", "GRAVEL", "CHAIN", "LANTERN", "SOUL_LANTERN",
                 "IRON_BARS", "SPAWNER" -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNetherDrop(BlockDropItemEvent event) {
        if (!isNether(event.getBlock())) {
            return;
        }
        for (Item dropped : event.getItems()) {
            markMined(dropped.getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherPlace(BlockPlaceEvent event) {
        if (!isNether(event.getBlock())) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (canPlace(item)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cThe Nether keeps overworld cobble at the door.");
        event.getPlayer().sendMessage("§7Build with what you mined here.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNetherDeath(PlayerDeathEvent event) {
        if (!isNether(event.getEntity())) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.getEntity().sendMessage("§6The Nether does not take souvenirs. Gear stays.");
    }
}
