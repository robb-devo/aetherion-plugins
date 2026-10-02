package de.aetherion.farming.dev;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * DEV placeable district markers — oak signs with prewritten titles.
 * You place them where the Bezirke should be.
 */
public final class FarmDistrictMarker implements Listener {

    public enum District {
        WHEAT_VALE("Wheat Vale", "§eWheat Vale", "§7Grain fields", Material.WHEAT),
        ROOT_PATCH("Root Patch", "§6Root Patch", "§7Carrot · Potato · Beet", Material.CARROT),
        CANE_SHORE("Cane Shore", "§aCane Shore", "§7Sugar cane banks", Material.SUGAR_CANE),
        MILL_YARD("Mill Yard", "§6Mill Yard", "§7Pantry · Millstone", Material.GRINDSTONE);

        final String id;
        final String title;
        final String subtitle;
        final Material icon;

        District(String id, String title, String subtitle, Material icon) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    private final AetherionFarming plugin;
    private final NamespacedKey key;

    public FarmDistrictMarker(AetherionFarming plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "farm_district");
    }

    public ItemStack create(District district) {
        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(district.title + " §8(Marker)");
            meta.setLore(List.of(
                    district.subtitle,
                    "",
                    "§7Place on Farm Isle to mark the Bezirk.",
                    "§8DEV — not consumed from creative invent"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, district.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public District districtOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return District.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        District district = districtOf(event.getItemInHand());
        if (district == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        plugin.getServer().getScheduler().runTask(plugin, () -> applySign(block, district));
        event.getPlayer().sendMessage("§aBezirk marked §8· " + district.title);
    }

    private void applySign(Block block, District district) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        front.setLine(0, "§8§m----------");
        front.setLine(1, district.title);
        front.setLine(2, district.subtitle);
        front.setLine(3, "§8§m----------");
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
