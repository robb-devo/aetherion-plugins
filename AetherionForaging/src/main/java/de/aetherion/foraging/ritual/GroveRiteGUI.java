package de.aetherion.foraging.ritual;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.IsleHeartwood;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchant-table style grove GUI: 2 heartwood inputs left, 3 rotating rites right.
 */
public final class GroveRiteGUI implements InventoryHolder, Listener {

    public static final int SIZE = 27;
    public static final int SLOT_A = 10;
    public static final int SLOT_B = 12;
    public static final int SLOT_OFFER_0 = 14;
    public static final int SLOT_OFFER_1 = 15;
    public static final int SLOT_OFFER_2 = 16;
    public static final int SLOT_REFRESH = 22;
    public static final long REFRESH_MS = 8L * 60L * 1000L;

    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private final AetherionForaging plugin;
    private final GroveRitualService ritual;
    private final Inventory inventory;
    private final Player player;
    private final Session session;
    private boolean returning;

    private GroveRiteGUI(AetherionForaging plugin, GroveRitualService ritual, Player player, Session session) {
        this.plugin = plugin;
        this.ritual = ritual;
        this.player = player;
        this.session = session;
        this.inventory = Bukkit.createInventory(this, SIZE, "§2Grove Enchanting");
        paint();
    }

    public static void open(AetherionForaging plugin, GroveRitualService ritual, Player player) {
        if (plugin == null || ritual == null || player == null) {
            return;
        }
        Session session = SESSIONS.compute(player.getUniqueId(), (id, prev) -> {
            long now = System.currentTimeMillis();
            if (prev != null && prev.refreshAt > now && prev.offers != null && !prev.offers.isEmpty()) {
                return prev;
            }
            return new Session(GroveRiteCatalog.rollOffers(3), now + REFRESH_MS);
        });
        GroveRiteGUI gui = new GroveRiteGUI(plugin, ritual, player, session);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.1f);
    }

    private void paint() {
        ItemStack glass = pane(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass);
        }
        inventory.setItem(4, button(Material.ENCHANTING_TABLE, "§a§lGrove Table",
                "§7Place §dtwo heartwoods§7 left.",
                "§7Pick a rite on the right.",
                "§8Offers refresh every 8 minutes."));
        inventory.setItem(SLOT_A, session.slotA);
        inventory.setItem(SLOT_B, session.slotB);
        paintOffers();
        long left = Math.max(0L, session.refreshAt - System.currentTimeMillis());
        int mins = (int) Math.ceil(left / 60000.0);
        inventory.setItem(SLOT_REFRESH, button(Material.CLOCK, "§eRefresh Offers",
                left <= 0L ? "§aClick to roll three new rites." : "§7New rites in §f" + mins + "m§7.",
                "§8Or wait — opening keeps current set."));
    }

    private void paintOffers() {
        int[] slots = {SLOT_OFFER_0, SLOT_OFFER_1, SLOT_OFFER_2};
        for (int i = 0; i < slots.length; i++) {
            if (i >= session.offers.size()) {
                inventory.setItem(slots[i], pane(Material.GRAY_STAINED_GLASS_PANE, "§8Empty"));
                continue;
            }
            GroveRiteCatalog.Rite rite = session.offers.get(i);
            boolean can = canAfford(rite);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + rite.blurb());
            lore.add("");
            lore.addAll(rite.costLore());
            lore.add("");
            lore.add("§7Buff §f" + effectName(rite.primary())
                    + (rite.primaryAmp() > 0 ? " " + (rite.primaryAmp() + 1) : ""));
            if (rite.secondary() != null) {
                lore.add("§7Also §f" + effectName(rite.secondary()));
            }
            if (rite.weather() != null) {
                lore.add("§7Weather §b" + pretty(rite.weather().name()));
            }
            lore.add("");
            lore.add(can ? "§aClick to enact" : "§cMissing heartwoods");
            ItemStack icon = new ItemStack(rite.icon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((can ? "§a" : "§c") + rite.title());
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slots[i], icon);
        }
    }

    private boolean canAfford(GroveRiteCatalog.Rite rite) {
        return count(rite.costA().heart()) >= rite.costA().amount()
                && count(rite.costB().heart()) >= rite.costB().amount();
    }

    private int count(IsleHeartwood heart) {
        int n = 0;
        n += countIn(session.slotA, heart);
        n += countIn(session.slotB, heart);
        return n;
    }

    private static int countIn(ItemStack stack, IsleHeartwood heart) {
        IsleHeartwood found = heartwoodOf(stack);
        if (found != heart || stack == null) {
            return 0;
        }
        return stack.getAmount();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GroveRiteGUI gui) || gui != this) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.equals(player)) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw < SIZE) {
            if (raw == SLOT_A || raw == SLOT_B) {
                // Allow placing / taking heartwoods only.
                Bukkit.getScheduler().runTask(plugin, this::syncInputsAndRepaint);
                return;
            }
            event.setCancelled(true);
            if (raw == SLOT_REFRESH) {
                tryRefresh();
                return;
            }
            int offerIndex = offerIndex(raw);
            if (offerIndex >= 0) {
                tryEnact(offerIndex);
            }
            return;
        }
        // Bottom inventory — sync after shift-clicks into inputs.
        Bukkit.getScheduler().runTask(plugin, this::syncInputsAndRepaint);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < SIZE && slot != SLOT_A && slot != SLOT_B) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, this::syncInputsAndRepaint);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }
        syncInputs();
        if (!returning) {
            returnInputsToPlayer();
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    private void syncInputsAndRepaint() {
        syncInputs();
        // Reject non-heartwoods from input slots.
        if (!isHeartwoodOrEmpty(session.slotA)) {
            give(player, session.slotA);
            session.slotA = null;
            inventory.setItem(SLOT_A, null);
        }
        if (!isHeartwoodOrEmpty(session.slotB)) {
            give(player, session.slotB);
            session.slotB = null;
            inventory.setItem(SLOT_B, null);
        }
        paintOffers();
        inventory.setItem(SLOT_A, session.slotA);
        inventory.setItem(SLOT_B, session.slotB);
    }

    private void syncInputs() {
        session.slotA = cloneOrNull(inventory.getItem(SLOT_A));
        session.slotB = cloneOrNull(inventory.getItem(SLOT_B));
    }

    private void tryRefresh() {
        long now = System.currentTimeMillis();
        if (session.refreshAt > now) {
            long left = session.refreshAt - now;
            player.sendMessage("§7Offers refresh in §f" + Math.max(1, (int) Math.ceil(left / 60000.0)) + "m§7.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.6f);
            return;
        }
        session.offers = GroveRiteCatalog.rollOffers(3);
        session.refreshAt = now + REFRESH_MS;
        SESSIONS.put(player.getUniqueId(), session);
        paint();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        player.sendMessage("§aGrove offers refreshed.");
    }

    private void tryEnact(int index) {
        syncInputs();
        if (index < 0 || index >= session.offers.size()) {
            return;
        }
        GroveRiteCatalog.Rite rite = session.offers.get(index);
        if (!canAfford(rite)) {
            player.sendMessage("§cNeed " + rite.costA().label() + " §cand §f" + rite.costB().label() + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            paintOffers();
            return;
        }
        if (!consume(rite.costA()) || !consume(rite.costB())) {
            player.sendMessage("§cCould not pull heartwoods from the table.");
            return;
        }
        inventory.setItem(SLOT_A, session.slotA);
        inventory.setItem(SLOT_B, session.slotB);
        returning = true;
        player.closeInventory();
        ritual.enact(player, rite);
        // Roll fresh offers after a successful rite.
        session.offers = GroveRiteCatalog.rollOffers(3);
        session.refreshAt = System.currentTimeMillis() + REFRESH_MS;
        SESSIONS.put(player.getUniqueId(), session);
    }

    private boolean consume(GroveRiteCatalog.Cost cost) {
        int need = cost.amount();
        need = takeFrom(true, cost.heart(), need);
        if (need > 0) {
            need = takeFrom(false, cost.heart(), need);
        }
        return need <= 0;
    }

    private int takeFrom(boolean slotA, IsleHeartwood heart, int need) {
        ItemStack stack = slotA ? session.slotA : session.slotB;
        if (heartwoodOf(stack) != heart || need <= 0) {
            return need;
        }
        int have = stack.getAmount();
        int take = Math.min(have, need);
        int left = have - take;
        if (left <= 0) {
            if (slotA) {
                session.slotA = null;
            } else {
                session.slotB = null;
            }
        } else {
            stack.setAmount(left);
            if (slotA) {
                session.slotA = stack;
            } else {
                session.slotB = stack;
            }
        }
        return need - take;
    }

    private void returnInputsToPlayer() {
        give(player, session.slotA);
        give(player, session.slotB);
        session.slotA = null;
        session.slotB = null;
    }

    private static int offerIndex(int slot) {
        return switch (slot) {
            case SLOT_OFFER_0 -> 0;
            case SLOT_OFFER_1 -> 1;
            case SLOT_OFFER_2 -> 2;
            default -> -1;
        };
    }

    private static boolean isHeartwoodOrEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || heartwoodOf(stack) != null;
    }

    static IsleHeartwood heartwoodOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        return IsleHeartwood.fromId(id);
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    private static void give(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String effectName(org.bukkit.potion.PotionEffectType type) {
        if (type == null) {
            return "?";
        }
        String key = type.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static String pretty(String raw) {
        String lower = raw.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static final class Session {
        List<GroveRiteCatalog.Rite> offers;
        long refreshAt;
        ItemStack slotA;
        ItemStack slotB;

        Session(List<GroveRiteCatalog.Rite> offers, long refreshAt) {
            this.offers = new ArrayList<>(offers);
            this.refreshAt = refreshAt;
        }
    }
}
