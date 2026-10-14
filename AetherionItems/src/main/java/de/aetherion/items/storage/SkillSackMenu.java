package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.ValueLore;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.storage.SkillSack.Skill;
import de.aetherion.items.storage.SkillSack.Slot;
import de.aetherion.items.storage.SkillSack.Tier;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Skill sacks T1–T3: count store, shared open-UI, and auto-absorb from the
 * player's inventory and from Aetherion Storage. Same file folder as the booster sack.
 */
public final class SkillSackMenu {

    private static final long SCAN_TTL_MS = 1_000L;
    private static final long FLUSH_TICKS = 20L * 30L;

    private final JavaPlugin plugin;
    private final ItemManager items;
    private final File folder;
    private final Map<UUID, Data> cache = new HashMap<>();
    private final Map<UUID, Scan> scans = new HashMap<>();

    public SkillSackMenu(JavaPlugin plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.folder = new File(plugin.getDataFolder(), "sacks");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, FLUSH_TICKS, FLUSH_TICKS);
    }

    // ============================================================== open / UI

    public void open(Player player, ItemStack sackItem) {
        Skill skill = SkillSack.skillOf(sackItem);
        Tier tier = SkillSack.tierOf(sackItem);
        UUID id = SackItems.idOf(sackItem);
        if (player == null || skill == null || tier == null || id == null) {
            return;
        }
        invalidate(player);
        Holder holder = new Holder(id, skill, tier, data(id));
        int rows = tier.forms().size() + 2;
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, SkillSack.displayName(skill, tier));
        holder.bind(inventory);
        paint(holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.5f, 1.2f);
    }

    public void paint(Holder holder) {
        if (holder == null || holder.inventory == null) {
            return;
        }
        Inventory inventory = holder.inventory;
        int size = inventory.getSize();
        ItemStack filler = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }
        holder.slots.clear();
        inventory.setItem(4, header(holder));
        List<Integer> columns = columns(holder.skill.resources().size());
        int row = 1;
        for (SkillSack.Form form : holder.tier.forms()) {
            int col = 0;
            for (var resource : holder.skill.resources()) {
                Slot slot = new Slot(resource, form);
                int raw = row * 9 + columns.get(col++);
                holder.slots.put(raw, slot);
                inventory.setItem(raw, icon(slot, holder.data.count(slot), holder.tier.capacity()));
            }
            row++;
        }
        inventory.setItem(size - 5, GuiItems.named(Material.HOPPER, "§aInsert All",
                "§7Moves every matching item from",
                "§7your inventory into this sack."));
    }

    /** Raw-slot click in the top inventory. */
    public void handleClick(Player player, Holder holder, int rawSlot, boolean rightClick) {
        if (player == null || holder == null || holder.inventory == null) {
            return;
        }
        if (rawSlot == holder.inventory.getSize() - 5) {
            int moved = insertAll(player, holder);
            paint(holder);
            if (moved > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.35f);
                player.sendActionBar("§aInserted §f" + format(moved) + " §aitem" + (moved == 1 ? "" : "s") + ".");
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 0.7f);
                player.sendActionBar("§7Nothing to insert.");
            }
            return;
        }
        Slot slot = holder.slots.get(rawSlot);
        if (slot == null) {
            return;
        }
        int stored = holder.data.count(slot);
        if (stored <= 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.55f);
            return;
        }
        ItemStack give = withdrawStack(slot);
        int want = Math.min(stored, rightClick ? 1 : give.getMaxStackSize());
        give.setAmount(want);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        int notGiven = 0;
        for (ItemStack left : leftover.values()) {
            notGiven += left == null ? 0 : left.getAmount();
        }
        int taken = want - notGiven;
        if (taken <= 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.35f, 1.0f);
            player.sendActionBar("§7Inventory full.");
            return;
        }
        holder.data.add(slot, -taken, holder.tier.capacity());
        paint(holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.15f);
    }

    public void save(Holder holder) {
        if (holder != null) {
            flush(holder.sackId);
        }
    }

    private ItemStack header(Holder holder) {
        ItemStack item = SkillSack.create(holder.skill, holder.tier, holder.sackId);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            long total = 0;
            for (Slot slot : SkillSack.slots(holder.skill, holder.tier)) {
                total += holder.data.count(slot);
            }
            List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
            // Swap the "Right-click to open." hint for live totals.
            if (!lore.isEmpty()) {
                lore.remove(lore.size() - 1);
            }
            lore.add("§7Stored: §f" + format(total) + " §7items");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack icon(Slot slot, int stored, int capacity) {
        ItemStack item = slot.sample();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(slot.displayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Stored: §f" + format(stored) + "§8/§f" + format(capacity));
        lore.add(bar(stored, capacity));
        lore.add("");
        if (stored > 0) {
            lore.add("§eLeft-click §7take a stack");
            lore.add("§eRight-click §7take one");
        } else {
            lore.add("§8Empty");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
        GuiItems.hideVanilla(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static String bar(int stored, int capacity) {
        int filled = capacity <= 0 ? 0 : (int) Math.round(20.0 * Math.min(stored, capacity) / capacity);
        if (stored > 0 && filled == 0) {
            filled = 1;
        }
        return "§a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled);
    }

    /** Centered columns: spaced out when the skill has few resources. */
    private static List<Integer> columns(int count) {
        List<Integer> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        int step = count * 2 - 1 <= 9 ? 2 : 1;
        int width = (count - 1) * step + 1;
        int start = Math.max(0, (9 - width) / 2);
        for (int i = 0; i < count && i < 9; i++) {
            out.add(start + i * step);
        }
        return out;
    }

    private ItemStack withdrawStack(Slot slot) {
        ItemStack item = slot.sample();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getItemValues() != null) {
            ValueLore.apply(item, plugin.getItemValues());
        }
        return item;
    }

    private int insertAll(Player player, Holder holder) {
        int moved = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            Slot slot = SkillSack.slotFor(holder.skill, holder.tier, stack, items);
            if (slot == null) {
                continue;
            }
            int take = holder.data.add(slot, stack.getAmount(), holder.tier.capacity());
            if (take <= 0) {
                continue;
            }
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

    // ============================================================== absorb

    /** Static entry for drop paths ({@code InventoryDrops}, auto-pickup). */
    public static int tryAbsorb(Player player, ItemStack stack) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getSackInventory() == null) {
            return 0;
        }
        return plugin.getSackInventory().skills().absorb(player, stack);
    }

    /** Quest / recipe credit for items that went into a sack instead of the inventory. */
    public static void noteAbsorbed(Player player, ItemStack stack, int amount) {
        if (player == null || stack == null || amount <= 0) {
            return;
        }
        de.aetherion.items.util.QuestProgressHook.noteCollected(player, stack.getType(), amount);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().noteObtained(player, stack);
        }
    }

    /**
     * Pushes as much of {@code stack} as fits into the player's skill sacks
     * (inventory first, then Aetherion Storage). Does not modify the stack.
     *
     * @return amount absorbed (0 … stack amount)
     */
    public int absorb(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        List<Ref> sacks = sacksOf(player);
        if (sacks.isEmpty()) {
            return 0;
        }
        int remaining = stack.getAmount();
        for (Ref ref : sacks) {
            Slot slot = SkillSack.slotFor(ref.skill, ref.tier, stack, items);
            if (slot == null) {
                continue;
            }
            remaining -= data(ref.id).add(slot, remaining, ref.tier.capacity());
            if (remaining <= 0) {
                break;
            }
        }
        int absorbed = stack.getAmount() - Math.max(0, remaining);
        if (absorbed > 0 && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open) {
            paint(open);
        }
        return absorbed;
    }

    /** Drop the cached sack scan (sack moved, storage closed, …). */
    public void invalidate(Player player) {
        if (player != null) {
            scans.remove(player.getUniqueId());
        }
    }

    private List<Ref> sacksOf(Player player) {
        long now = System.currentTimeMillis();
        Scan scan = scans.get(player.getUniqueId());
        if (scan != null && now - scan.at < SCAN_TTL_MS) {
            return scan.refs;
        }
        Map<UUID, Ref> found = new LinkedHashMap<>();
        collect(found, player.getInventory().getContents());
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() == null
                && player.getOpenInventory().getTitle().startsWith(StorageGUI.STORAGE_TITLE)) {
            // Live, not-yet-saved storage page.
            collect(found, top.getContents());
        }
        AetherionItems aetherion = AetherionItems.getInstance();
        StorageInventory storage = aetherion == null ? null : aetherion.getStorageInventory();
        if (storage != null) {
            collect(found, storage.storedItems(player.getUniqueId()).toArray(new ItemStack[0]));
        }
        List<Ref> refs = List.copyOf(found.values());
        scans.put(player.getUniqueId(), new Scan(now, refs));
        return refs;
    }

    private static void collect(Map<UUID, Ref> into, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (ItemStack stack : contents) {
            Skill skill = SkillSack.skillOf(stack);
            if (skill == null) {
                continue;
            }
            Tier tier = SkillSack.tierOf(stack);
            UUID id = SackItems.idOf(stack);
            if (tier == null || id == null || into.containsKey(id)) {
                continue;
            }
            into.put(id, new Ref(id, skill, tier));
        }
    }

    // ============================================================== persistence

    private Data data(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    private Data load(UUID id) {
        Data data = new Data();
        File file = file(id);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection counts = yaml.getConfigurationSection("counts");
        if (counts == null) {
            return data;
        }
        for (String form : counts.getKeys(false)) {
            ConfigurationSection section = counts.getConfigurationSection(form);
            if (section == null) {
                continue;
            }
            for (String resource : section.getKeys(false)) {
                int value = section.getInt(resource, 0);
                if (value > 0) {
                    data.counts.put(form.toLowerCase(Locale.ROOT) + "." + resource.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return data;
    }

    public void flushAll() {
        for (UUID id : new ArrayList<>(cache.keySet())) {
            flush(id);
        }
    }

    private void flush(UUID id) {
        Data data = cache.get(id);
        if (data == null || !data.dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("type", SkillSack.TYPE_TAG);
        for (Map.Entry<String, Integer> entry : data.counts.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                yaml.set("counts." + entry.getKey(), entry.getValue());
            }
        }
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file(id), plugin.getLogger());
            data.dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save skill sack " + id + ": " + exception.getMessage());
        }
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    private static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    // ============================================================== types

    private record Ref(UUID id, Skill skill, Tier tier) {
    }

    private record Scan(long at, List<Ref> refs) {
    }

    private static final class Data {
        private final Map<String, Integer> counts = new HashMap<>();
        private boolean dirty;

        int count(Slot slot) {
            return counts.getOrDefault(slot.key(), 0);
        }

        /** @return how much actually changed (clamped to 0 … capacity). */
        int add(Slot slot, int delta, int capacity) {
            int current = count(slot);
            int next = Math.max(0, Math.min(capacity, current + delta));
            if (next == current) {
                return 0;
            }
            counts.put(slot.key(), next);
            dirty = true;
            return Math.abs(next - current);
        }
    }

    public static final class Holder implements InventoryHolder {
        private final UUID sackId;
        private final Skill skill;
        private final Tier tier;
        private final Data data;
        private final Map<Integer, Slot> slots = new HashMap<>();
        private Inventory inventory;

        Holder(UUID sackId, Skill skill, Tier tier, Data data) {
            this.sackId = sackId;
            this.skill = skill;
            this.tier = tier;
            this.data = data;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID sackId() {
            return sackId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
