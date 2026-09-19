package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class SackInventory {

    public static final int SIZE = 27;

    private final JavaPlugin plugin;
    private final File folder;
    private final BoosterSackMenu boosters;

    public SackInventory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "sacks");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        ItemManager items = plugin instanceof AetherionItems aetherion
                ? aetherion.getItemManager()
                : AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getItemManager();
        this.boosters = new BoosterSackMenu(plugin, items);
    }

    public BoosterSackMenu boosters() {
        return boosters;
    }

    public void open(Player player, ItemStack sackItem) {
        if (player == null || sackItem == null) {
            return;
        }
        SackItems.ensureId(sackItem);
        SackType type = SackItems.typeOf(sackItem);
        UUID id = SackItems.idOf(sackItem);
        if (type == null || id == null) {
            return;
        }
        if (type == SackType.BOOSTER) {
            boosters.open(player, id);
            return;
        }
        Holder holder = new Holder(id, type);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, type.displayName());
        holder.bind(inventory);
        ItemStack[] contents = load(id);
        if (contents != null) {
            inventory.setContents(trim(contents));
        }
        player.openInventory(inventory);
    }

    public void save(Holder holder) {
        if (holder == null || holder.inventory == null || holder.sackId == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("type", holder.type.name());
        yaml.set("contents", holder.inventory.getContents());
        File file = file(holder.sackId);
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save sack " + holder.sackId + ": " + exception.getMessage());
        }
    }

    private ItemStack[] load(UUID id) {
        File file = file(id);
        if (!file.exists()) {
            return new ItemStack[SIZE];
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        java.util.List<?> list = yaml.getList("contents");
        if (list == null || list.isEmpty()) {
            return new ItemStack[SIZE];
        }
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < Math.min(SIZE, list.size()); i++) {
            Object value = list.get(i);
            if (value instanceof ItemStack stack) {
                out[i] = stack;
            }
        }
        return out;
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    private static ItemStack[] trim(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[SIZE];
        if (contents == null) {
            return out;
        }
        System.arraycopy(contents, 0, out, 0, Math.min(SIZE, contents.length));
        return out;
    }

    public static final class Holder implements InventoryHolder {
        private final UUID sackId;
        private final SackType type;
        private Inventory inventory;

        Holder(UUID sackId, SackType type) {
            this.sackId = sackId;
            this.type = type;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID sackId() {
            return sackId;
        }

        public SackType type() {
            return type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
