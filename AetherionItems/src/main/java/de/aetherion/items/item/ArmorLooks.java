package de.aetherion.items.item;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Worn armor that looks like its icon. Every custom armor set has a trim pattern ({@code aetherion:<set>}, datapack
 * {@code aetherion-armor}) whose texture paints the whole piece; this puts that trim on a piece when it is worn.
 * The table (material, custom model data -> set) is {@code armor-looks.yml}, written by tools/gen_armor_trims.py.
 * The trim line in the tooltip is hidden, and the trim material's model index sits below every vanilla one, so
 * icons stay as they are. Without the datapack the registries have no entries and nothing happens.
 */
public final class ArmorLooks implements Listener {

    private static final NamespacedKey MATERIAL = new NamespacedKey("aetherion", "aether");

    private final JavaPlugin plugin;
    private final Map<Material, Map<Integer, String>> sets = new EnumMap<>(Material.class);

    public ArmorLooks(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
        if (!sets.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    private void load() {
        try (InputStream in = plugin.getResource("armor-looks.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String key : yaml.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (material == null || section == null) {
                    continue;
                }
                Map<Integer, String> byModel = new HashMap<>();
                for (String cmd : section.getKeys(false)) {
                    try {
                        byModel.put(Integer.parseInt(cmd), section.getString(cmd));
                    } catch (NumberFormatException ignored) {
                        // not a model number
                    }
                }
                sets.put(material, byModel);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("armor-looks.yml: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> dress(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> dress(player), 20L);
    }

    /** Puts the set's trim on every worn piece that lacks it. */
    private void dress(Player player) {
        if (!player.isOnline()) {
            return;
        }
        EntityEquipment equipment = player.getEquipment();
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            if (apply(armor[i])) {
                changed = true;
            }
        }
        if (changed) {
            equipment.setArmorContents(armor);
        }
    }

    /** @return true when the item got its look now */
    public boolean apply(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Map<Integer, String> byModel = sets.get(item.getType());
        if (byModel == null || !(item.getItemMeta() instanceof ArmorMeta meta) || !meta.hasCustomModelData()) {
            return false;
        }
        String set = byModel.get(meta.getCustomModelData());
        if (set == null) {
            return false;
        }
        TrimPattern pattern = Registry.TRIM_PATTERN.get(new NamespacedKey("aetherion", set));
        TrimMaterial material = Registry.TRIM_MATERIAL.get(MATERIAL);
        if (pattern == null || material == null) {
            return false;
        }
        ArmorTrim current = meta.getTrim();
        if (current != null && current.getPattern().equals(pattern) && current.getMaterial().equals(material)) {
            return false;
        }
        meta.setTrim(new ArmorTrim(material, pattern));
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        item.setItemMeta(meta);
        return true;
    }
}
