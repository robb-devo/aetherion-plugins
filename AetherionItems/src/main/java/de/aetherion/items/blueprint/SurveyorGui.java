package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Surveyor desk — blueprint collection + craft (blueprint → tool).
 */
public final class SurveyorGui implements Listener {

    public static final String TITLE = "§8Surveyor Desk";
    private static final int ANVIL_SLOT = 38;
    private static final int CRAFT_SLOT = 29;
    private static final int RESULT_SLOT = 31;
    private static final int CRAFT_BUTTON = 40;
    private static final int CLOSE_SLOT = 49;
    private static final int[] COLLECTION_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final AetherionItems plugin;

    public SurveyorGui(AetherionItems plugin) {
        this.plugin = plugin;
    }

    public static void openFor(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getSurveyorGui() == null) {
            player.sendMessage("§cSurveyor desk is offline.");
            return;
        }
        items.getSurveyorGui().open(player);
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        fill(inventory);
        drawCollection(inventory, player);
        inventory.setItem(4, button(Material.FILLED_MAP, "§bBlueprint Collection",
                "§7Known schematics. Locked = not found yet.",
                "§7Ore trolls drop pages — any vein in the Shabby Mine."));
        inventory.setItem(ANVIL_SLOT, button(Material.ANVIL, "§eCraft",
                "§7Put a blueprint in the slot above.",
                "§7Stamp it into the tool."));
        inventory.setItem(CRAFT_SLOT, null);
        inventory.setItem(RESULT_SLOT, button(Material.GRAY_STAINED_GLASS_PANE, "§8Result",
                "§7Appears after a valid stamp."));
        inventory.setItem(CRAFT_BUTTON, button(Material.EMERALD, "§aStamp Blueprint",
                "§7Converts the blueprint into its tool.",
                "§7Adds it to your collection."));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.45f, 1.35f);
    }

    private void drawCollection(Inventory inventory, Player player) {
        BlueprintUnlockService unlocks = plugin.blueprintUnlocks();
        CustomItem customItem = plugin.getCustomItem();
        BlueprintKind[] kinds = BlueprintKind.values();
        for (int i = 0; i < COLLECTION_SLOTS.length; i++) {
            int slot = COLLECTION_SLOTS[i];
            if (i >= kinds.length) {
                inventory.setItem(slot, pane());
                continue;
            }
            BlueprintKind kind = kinds[i];
            boolean owned = unlocks != null && unlocks.hasCollected(player, kind.id());
            if (owned && customItem != null) {
                ItemStack shown = kind.createBlueprint(customItem).clone();
                ItemMeta meta = shown.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() && meta.getLore() != null
                            ? new ArrayList<>(meta.getLore())
                            : new ArrayList<>();
                    lore.add("");
                    lore.add("§a✔ Found");
                    meta.setLore(lore);
                    shown.setItemMeta(meta);
                }
                inventory.setItem(slot, shown);
            } else {
                inventory.setItem(slot, button(Material.GRAY_DYE, "§8???",
                        "§7" + kind.display(),
                        "§8Not found yet.",
                        "§7Ore trolls may drop this page."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();

        // Allow placing / taking from craft slot only
        if (raw == CRAFT_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshPreview(top, player));
            return;
        }
        if (raw >= top.getSize()) {
            // Bottom inventory — allow shift-click into craft if blueprint
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (isBlueprint(current) && isEmpty(top.getItem(CRAFT_SLOT))) {
                    event.setCancelled(true);
                    top.setItem(CRAFT_SLOT, current.clone());
                    event.setCurrentItem(null);
                    refreshPreview(top, player);
                    return;
                }
            }
            return;
        }

        event.setCancelled(true);
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == CRAFT_BUTTON) {
            stamp(player, top);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && slot != CRAFT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    refreshPreview(event.getView().getTopInventory(), player));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // ESC / close must never eat a blueprint — only stamp consumes.
        ItemStack cursor = player.getItemOnCursor();
        if (isBlueprint(cursor)) {
            player.setItemOnCursor(null);
            player.getInventory().addItem(cursor).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        ItemStack leftover = event.getInventory().getItem(CRAFT_SLOT);
        if (leftover != null && !leftover.getType().isAir()) {
            player.getInventory().addItem(leftover).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            event.getInventory().setItem(CRAFT_SLOT, null);
        }
    }

    private void refreshPreview(Inventory top, Player player) {
        ItemStack in = top.getItem(CRAFT_SLOT);
        BlueprintKind kind = kindOf(in);
        if (kind == null || plugin.getCustomItem() == null) {
            top.setItem(RESULT_SLOT, button(Material.GRAY_STAINED_GLASS_PANE, "§8Result",
                    "§7Place a blueprint to preview."));
            return;
        }
        ItemStack preview = kind.createTool(plugin.getCustomItem()).clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add("");
            lore.add("§ePreview · stamp to craft");
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        top.setItem(RESULT_SLOT, preview);
    }

    private void stamp(Player player, Inventory top) {
        ItemStack in = top.getItem(CRAFT_SLOT);
        BlueprintKind kind = kindOf(in);
        if (kind == null) {
            player.sendMessage("§cPut a blueprint in the craft slot.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
            return;
        }
        CustomItem customItem = plugin.getCustomItem();
        if (customItem == null) {
            return;
        }
        // Consume one
        if (in.getAmount() <= 1) {
            top.setItem(CRAFT_SLOT, null);
        } else {
            in.setAmount(in.getAmount() - 1);
        }
        ItemStack tool = kind.createTool(customItem);
        player.getInventory().addItem(tool).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (plugin.blueprintUnlocks() != null) {
            plugin.blueprintUnlocks().markStamped(player, kind.id());
        }
        noteQuestProgress(player);
        drawCollection(top, player);
        refreshPreview(top, player);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.25f);
        player.sendMessage("§bSurveyor §8» §f" + kind.display() + " §7stamped. Tool's yours.");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                kind.display() + " crafted",
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
        clearSurveyorHint(player);
    }

    private static void clearSurveyorHint(Player player) {
        try {
            Class.forName("de.aetherion.quests.ui.QuestHint")
                    .getMethod("clearPending", Player.class)
                    .invoke(null, player);
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
        }
    }

    private void noteQuestProgress(Player player) {
        // Surveyor desk has no quest — collection/stamp only.
    }

    private BlueprintKind kindOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemManager manager = plugin.getItemManager();
        if (manager == null) {
            return null;
        }
        return BlueprintKind.fromItemId(manager.getItemId(stack));
    }

    private boolean isBlueprint(ItemStack stack) {
        return kindOf(stack) != null;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private void fill(Inventory inventory) {
        ItemStack glass = pane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass.clone());
        }
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
