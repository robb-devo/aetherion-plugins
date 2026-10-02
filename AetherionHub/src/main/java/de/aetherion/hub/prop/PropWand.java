package de.aetherion.hub.prop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Admin stick: look + right-click pastes the selected prop (FAWE, ignore-air). */
public final class PropWand {

    public static final String PERMISSION = "aetherion.propwand";

    private final NamespacedKey wandKey;
    private final NamespacedKey propKey;
    private final NamespacedKey rotateKey;

    public PropWand(Plugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "prop_wand");
        this.propKey = new NamespacedKey(plugin, "prop_id");
        this.rotateKey = new NamespacedKey(plugin, "prop_rotate");
    }

    public ItemStack create() {
        PropCatalog.Prop first = PropCatalog.byIndex(0);
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Prop Wand");
            meta.setLore(lore(first, 0));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(propKey, PersistentDataType.STRING, first.id());
            meta.getPersistentDataContainer().set(rotateKey, PersistentDataType.INTEGER, -1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte mark = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return mark != null && mark == 1;
    }

    public PropCatalog.Prop selected(ItemStack item) {
        if (!isWand(item)) {
            return PropCatalog.byIndex(0);
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(propKey, PersistentDataType.STRING);
        PropCatalog.Prop prop = PropCatalog.get(id);
        return prop != null ? prop : PropCatalog.byIndex(0);
    }

    /**
     * Manual rotation override in degrees (0/90/180/270), or {@code -1} = face the direction the player looks.
     */
    public int rotateOverride(ItemStack item) {
        if (!isWand(item)) {
            return -1;
        }
        Integer value = item.getItemMeta().getPersistentDataContainer().get(rotateKey, PersistentDataType.INTEGER);
        return value == null ? -1 : value;
    }

    public void select(ItemStack item, PropCatalog.Prop prop) {
        if (!isWand(item) || prop == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(propKey, PersistentDataType.STRING, prop.id());
        meta.setLore(lore(prop, rotateOverride(item)));
        meta.setDisplayName("§6Prop Wand §8· §f" + prop.display());
        item.setItemMeta(meta);
    }

    public void cycle(ItemStack item, int delta) {
        PropCatalog.Prop current = selected(item);
        int index = PropCatalog.indexOf(current.id()) + delta;
        select(item, PropCatalog.byIndex(index));
    }

    public void cycleRotate(ItemStack item) {
        if (!isWand(item)) {
            return;
        }
        int current = rotateOverride(item);
        int next;
        if (current < 0) {
            next = 0;
        } else if (current >= 270) {
            next = -1;
        } else {
            next = current + 90;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(rotateKey, PersistentDataType.INTEGER, next);
        PropCatalog.Prop prop = selected(item);
        meta.setLore(lore(prop, next));
        item.setItemMeta(meta);
    }

    public void refreshLore(ItemStack item) {
        if (!isWand(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PropCatalog.Prop prop = selected(item);
        meta.setLore(lore(prop, rotateOverride(item)));
        meta.setDisplayName("§6Prop Wand §8· §f" + prop.display());
        item.setItemMeta(meta);
    }

    private static List<String> lore(PropCatalog.Prop prop, int rotate) {
        String facing = rotate < 0 ? "§aauto (face look)" : "§e" + rotate + "°";
        return List.of(
                "§7Selected: §f" + prop.display(),
                "§8" + prop.id(),
                "§7Zone: §f" + prop.zone(),
                "§7" + prop.tip(),
                "",
                "§eRight-click §7→ paste at look (§a-a§7)",
                "§eLeft-click §7→ next prop",
                "§eSneak + right-click §7→ pick menu",
                "§eSneak + left-click §7→ rotate: " + facing,
                "§8Undo with §f//undo §8if needed"
        );
    }
}
