package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterType;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hypixel-style booster sack: one slot per booster type, stacked counts up to 64.
 */
public final class BoosterSackMenu {

    public static final int SIZE = 27;
    public static final int MAX_PER_TYPE = 64;
    public static final int INSERT_ALL_SLOT = 22;

    private static final BoosterType[] ORDER = BoosterType.values();

    private final JavaPlugin plugin;
    private final ItemManager items;
    private final File folder;

    public BoosterSackMenu(JavaPlugin plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.folder = new File(plugin.getDataFolder(), "sacks");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public void open(Player player, UUID sackId) {
        if (player == null || sackId == null) {
            return;
        }
        Map<BoosterType, Integer> counts = load(sackId);
        Holder holder = new Holder(sackId, counts);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, "§dBooster Sack");
        holder.bind(inventory);
        paint(holder);
        player.openInventory(inventory);
    }

    /**
     * Deposit boosters into the first booster sack in the player's inventory.
     * @return amount that did not fit (or full amount if no sack / not a booster)
     */
    public int tryDeposit(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir() || items == null) {
            return stack == null ? 0 : Math.max(1, stack.getAmount());
        }
        BoosterType type = items.getBoosterType(stack);
        if (type == null) {
            return Math.max(1, stack.getAmount());
        }
        return tryDeposit(player, type, Math.max(1, stack.getAmount()));
    }

    public int tryDeposit(Player player, BoosterType type, int amount) {
        if (player == null || type == null || amount <= 0) {
            return Math.max(0, amount);
        }
        UUID sackId = findBoosterSackId(player);
        if (sackId == null) {
            return amount;
        }
        Map<BoosterType, Integer> counts = load(sackId);
        int room = MAX_PER_TYPE - counts.getOrDefault(type, 0);
        if (room <= 0) {
            return amount;
        }
        int take = Math.min(room, amount);
        counts.put(type, clamp(counts.getOrDefault(type, 0) + take));
        Holder tmp = new Holder(sackId, counts);
        save(tmp);
        return amount - take;
    }

    public static UUID findBoosterSackId(Player player) {
        if (player == null) {
            return null;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (SackItems.typeOf(stack) != SackType.BOOSTER) {
                continue;
            }
            SackItems.ensureId(stack);
            UUID id = SackItems.idOf(stack);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    public static boolean hasBoosterSack(Player player) {
        return findBoosterSackId(player) != null;
    }

    public void paint(Holder holder) {
        if (holder == null || holder.inventory == null) {
            return;
        }
        Inventory inventory = holder.inventory;
        inventory.clear();
        for (int i = 0; i < ORDER.length && i < INSERT_ALL_SLOT; i++) {
            inventory.setItem(i, icon(ORDER[i], holder.count(ORDER[i])));
        }
        for (int i = ORDER.length; i < SIZE; i++) {
            if (i == INSERT_ALL_SLOT) {
                continue;
            }
            inventory.setItem(i, filler());
        }
        inventory.setItem(INSERT_ALL_SLOT, insertAllButton());
    }

    public void save(Holder holder) {
        if (holder == null || holder.sackId == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("type", SackType.BOOSTER.name());
        yaml.set("mode", "counts");
        for (Map.Entry<BoosterType, Integer> entry : holder.counts.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                yaml.set("counts." + entry.getKey().name(), entry.getValue());
            }
        }
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file(holder.sackId), plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save booster sack " + holder.sackId + ": " + exception.getMessage());
        }
    }

    public boolean handleClick(Player player, Holder holder, int rawSlot, boolean topInventory) {
        if (player == null || holder == null) {
            return true;
        }
        if (!topInventory) {
            // Player inventory clicks: ignore (no shift-dump into chest slots).
            return true;
        }
        if (rawSlot == INSERT_ALL_SLOT) {
            int moved = insertAll(player, holder);
            paint(holder);
            save(holder);
            if (moved > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.35f);
                player.sendActionBar("§dInserted §f" + moved + " §dbooster" + (moved == 1 ? "" : "s") + ".");
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 0.7f);
                player.sendActionBar("§7No boosters to insert.");
            }
            return true;
        }
        if (rawSlot < 0 || rawSlot >= ORDER.length) {
            return true;
        }
        BoosterType type = ORDER[rawSlot];
        if (holder.count(type) <= 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.55f);
            return true;
        }
        ItemStack give = createBooster(type);
        if (give == null || give.getType().isAir()) {
            return true;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        if (!leftover.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.35f, 1.0f);
            player.sendActionBar("§7Inventory full.");
            return true;
        }
        holder.add(type, -1);
        paint(holder);
        save(holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.15f);
        player.sendActionBar("§7" + pretty(type) + " §f" + holder.count(type) + "§8/§f" + MAX_PER_TYPE);
        return true;
    }

    private int insertAll(Player player, Holder holder) {
        int moved = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            BoosterType type = items.getBoosterType(stack);
            if (type == null) {
                continue;
            }
            int room = MAX_PER_TYPE - holder.count(type);
            if (room <= 0) {
                continue;
            }
            int take = Math.min(room, Math.max(1, stack.getAmount()));
            holder.add(type, take);
            int left = stack.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(left);
                player.getInventory().setItem(i, stack);
            }
            moved += take;
        }
        return moved;
    }

    private Map<BoosterType, Integer> load(UUID sackId) {
        Map<BoosterType, Integer> counts = new EnumMap<>(BoosterType.class);
        for (BoosterType type : BoosterType.values()) {
            counts.put(type, 0);
        }
        File file = file(sackId);
        if (!file.exists()) {
            return counts;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("counts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                BoosterType type = parseBooster(key);
                if (type != null) {
                    counts.put(type, clamp(section.getInt(key, 0)));
                }
            }
            return counts;
        }
        // Migrate older chest-style contents into counts once.
        List<?> list = yaml.getList("contents");
        if (list != null) {
            for (Object value : list) {
                if (!(value instanceof ItemStack stack)) {
                    continue;
                }
                BoosterType type = items.getBoosterType(stack);
                if (type == null) {
                    continue;
                }
                counts.put(type, clamp(counts.getOrDefault(type, 0) + Math.max(1, stack.getAmount())));
            }
            Holder tmp = new Holder(sackId, counts);
            save(tmp);
        }
        return counts;
    }

    private static BoosterType parseBooster(String key) {
        try {
            return BoosterType.valueOf(key.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemStack icon(BoosterType type, int amount) {
        ItemStack stack = createBooster(type);
        if (stack == null) {
            stack = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName()
                    : "§f" + pretty(type);
            meta.setDisplayName(name);
            meta.setLore(List.of(
                    "§7Stored: §f" + amount + "§8/§f" + MAX_PER_TYPE,
                    "",
                    amount > 0 ? "§eClick §7to take 1" : "§8Empty",
                    "§8Same type stacks in your inventory."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        stack.setAmount(1);
        return stack;
    }

    private static ItemStack insertAllButton() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aInsert All");
            meta.setLore(List.of(
                    "§7Moves every booster from your",
                    "§7inventory into this sack.",
                    "§8Max 64 of each type."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBooster(BoosterType type) {
        CustomItem custom = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getCustomItem();
        if (custom == null || type == null) {
            return null;
        }
        return custom.booster(type);
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_PER_TYPE, value));
    }

    private static String pretty(BoosterType type) {
        String raw = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1) + " Booster";
    }

    public static final class Holder implements InventoryHolder {
        private final UUID sackId;
        private final Map<BoosterType, Integer> counts;
        private Inventory inventory;

        Holder(UUID sackId, Map<BoosterType, Integer> counts) {
            this.sackId = sackId;
            this.counts = counts;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID sackId() {
            return sackId;
        }

        int count(BoosterType type) {
            return counts.getOrDefault(type, 0);
        }

        void add(BoosterType type, int delta) {
            counts.put(type, clamp(count(type) + delta));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
