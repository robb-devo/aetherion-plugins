package de.aetherion.items.social;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeMenu {

    public static final String TITLE = "§8Trade";
    private static final int[] SELF_SLOTS = {
            0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30
    };
    private static final int[] OTHER_SLOTS = {
            5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35
    };
    private static final int CONFIRM_SELF = 39;
    private static final int CONFIRM_OTHER = 41;
    private static final int CANCEL = 45;

    private static final long REQUEST_MS = 30_000L;

    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

    public TradeMenu(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isTrading(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public void request(Player player, Player other) {
        if (player == null || other == null || player.getUniqueId().equals(other.getUniqueId())) {
            return;
        }
        if (de.aetherion.items.farming.Crops.isDungeonWorld(player.getWorld())
                || de.aetherion.items.farming.Crops.isDungeonWorld(other.getWorld())) {
            player.sendMessage("§cTrading is disabled inside dungeons.");
            return;
        }
        if (!other.isOnline() || other.getLocation().distanceSquared(player.getLocation()) > 36) {
            player.sendMessage("§cGet closer to trade.");
            return;
        }
        if (isTrading(player) || isTrading(other)) {
            player.sendMessage("§cOne of you is already trading.");
            return;
        }
        Request incoming = requests.get(player.getUniqueId());
        if (incoming != null && incoming.from.equals(other.getUniqueId()) && incoming.expiresAt > System.currentTimeMillis()) {
            requests.remove(player.getUniqueId());
            requests.remove(other.getUniqueId());
            start(player, other);
            return;
        }
        clearOutgoing(player.getUniqueId());
        requests.put(other.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis() + REQUEST_MS));
        player.sendMessage("§eTrade request sent to §f" + other.getName() + "§e.");
        other.sendMessage("§e" + player.getName() + " §ewants to trade. Sneak + right-click them to accept.");
        other.playSound(other.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }

    private void start(Player player, Player other) {
        Session session = new Session(player.getUniqueId(), other.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        sessions.put(other.getUniqueId(), session);
        open(player, session);
        open(other, session);
        player.sendMessage("§aTrading with §f" + other.getName() + "§a.");
        other.sendMessage("§aTrading with §f" + player.getName() + "§a.");
    }

    private void clearOutgoing(UUID from) {
        requests.entrySet().removeIf(entry -> entry.getValue().from.equals(from));
    }

    public void handle(Player player, InventoryClickEvent event) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.completing) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CANCEL) {
            event.setCancelled(true);
            cancel(session, "§7Trade cancelled.");
            return;
        }
        if (raw == CONFIRM_SELF) {
            event.setCancelled(true);
            toggleReady(player, session);
            return;
        }
        if (raw >= 54) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) {
                    return;
                }
                if (deposit(player, session, clicked.clone())) {
                    event.setCurrentItem(null);
                    refresh(session);
                }
            }
            return;
        }
        event.setCancelled(true);
        int offerIndex = indexOf(SELF_SLOTS, raw);
        if (offerIndex < 0) {
            return;
        }
        ItemStack[] offer = offerOf(player, session);
        ItemStack cursor = event.getCursor();
        ItemStack sitting = offer[offerIndex];
        if (cursor != null && !cursor.getType().isAir()) {
            offer[offerIndex] = cursor.clone();
            player.setItemOnCursor(sitting);
            session.leftReady = false;
            session.rightReady = false;
            refresh(session);
            return;
        }
        if (sitting != null) {
            offer[offerIndex] = null;
            player.setItemOnCursor(sitting);
            session.leftReady = false;
            session.rightReady = false;
            refresh(session);
        }
    }

    public void onClose(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.completing) {
            return;
        }
        cancel(session, "§7Trade cancelled.");
    }

    private void toggleReady(Player player, Session session) {
        if (session.left.equals(player.getUniqueId())) {
            session.leftReady = !session.leftReady;
        } else {
            session.rightReady = !session.rightReady;
        }
        if (session.leftReady && session.rightReady) {
            complete(session);
            return;
        }
        refresh(session);
    }

    private void complete(Session session) {
        session.completing = true;
        Player left = Bukkit.getPlayer(session.left);
        Player right = Bukkit.getPlayer(session.right);
        give(left, session.rightOffer);
        give(right, session.leftOffer);
        sessions.remove(session.left);
        sessions.remove(session.right);
        if (left != null) {
            left.closeInventory();
            left.playSound(left.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            left.sendMessage("§aTrade complete.");
        }
        if (right != null) {
            right.closeInventory();
            right.playSound(right.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            right.sendMessage("§aTrade complete.");
        }
    }

    private void cancel(Session session, String message) {
        if (session.completing) {
            return;
        }
        session.completing = true;
        Player left = Bukkit.getPlayer(session.left);
        Player right = Bukkit.getPlayer(session.right);
        give(left, session.leftOffer);
        give(right, session.rightOffer);
        sessions.remove(session.left);
        sessions.remove(session.right);
        if (left != null) {
            left.closeInventory();
            left.sendMessage(message);
        }
        if (right != null) {
            right.closeInventory();
            right.sendMessage(message);
        }
    }

    private void give(Player player, ItemStack[] offer) {
        if (player == null) {
            return;
        }
        for (ItemStack item : offer) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private boolean deposit(Player player, Session session, ItemStack item) {
        ItemStack[] offer = offerOf(player, session);
        for (int i = 0; i < offer.length; i++) {
            if (offer[i] == null || offer[i].getType().isAir()) {
                offer[i] = item;
                session.leftReady = false;
                session.rightReady = false;
                return true;
            }
        }
        player.sendMessage("§cYour trade window is full.");
        return false;
    }

    private void open(Player player, Session session) {
        Inventory inventory = Bukkit.createInventory(new Holder(player.getUniqueId()), 54, TITLE);
        draw(inventory, player, session);
        player.openInventory(inventory);
    }

    private void refresh(Session session) {
        Player left = Bukkit.getPlayer(session.left);
        Player right = Bukkit.getPlayer(session.right);
        if (left != null && left.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
            draw(left.getOpenInventory().getTopInventory(), left, session);
        }
        if (right != null && right.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
            draw(right.getOpenInventory().getTopInventory(), right, session);
        }
    }

    private void draw(Inventory inventory, Player viewer, Session session) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        ItemStack[] mine = offerOf(viewer, session);
        ItemStack[] theirs = viewer.getUniqueId().equals(session.left) ? session.rightOffer : session.leftOffer;
        for (int i = 0; i < SELF_SLOTS.length; i++) {
            inventory.setItem(SELF_SLOTS[i], mine[i]);
            inventory.setItem(OTHER_SLOTS[i], theirs[i]);
        }
        boolean meReady = viewer.getUniqueId().equals(session.left) ? session.leftReady : session.rightReady;
        boolean themReady = viewer.getUniqueId().equals(session.left) ? session.rightReady : session.leftReady;
        inventory.setItem(CONFIRM_SELF, named(
                meReady ? Material.LIME_TERRACOTTA : Material.GREEN_TERRACOTTA,
                meReady ? "§aReady" : "§eConfirm",
                "§7Click when the offer looks good."
        ));
        inventory.setItem(CONFIRM_OTHER, named(
                themReady ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA,
                themReady ? "§aThey are ready." : "§cWaiting...",
                "§7Both sides must confirm."
        ));
        inventory.setItem(CANCEL, named(Material.BARRIER, "§cCancel"));
        Player other = Bukkit.getPlayer(viewer.getUniqueId().equals(session.left) ? session.right : session.left);
        inventory.setItem(4, named(
                Material.PLAYER_HEAD,
                "§f" + (other == null ? "Player" : other.getName()),
                "§7Sneak + right-click trade."
        ));
    }

    private ItemStack[] offerOf(Player player, Session session) {
        return player.getUniqueId().equals(session.left) ? session.leftOffer : session.rightOffer;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> pending = requests.entrySet().iterator();
        while (pending.hasNext()) {
            if (pending.next().getValue().expiresAt <= now) {
                pending.remove();
            }
        }
        for (Session session : new HashSet<>(sessions.values())) {
            Player left = Bukkit.getPlayer(session.left);
            Player right = Bukkit.getPlayer(session.right);
            if (left == null || right == null || !left.isOnline() || !right.isOnline()) {
                cancel(session, "§cTrade cancelled.");
                continue;
            }
            if (left.getLocation().distanceSquared(right.getLocation()) > 64) {
                cancel(session, "§cYou moved too far apart.");
            }
        }
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class Request {
        private final UUID from;
        private final long expiresAt;

        private Request(UUID from, long expiresAt) {
            this.from = from;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Session {
        private final UUID left;
        private final UUID right;
        private final ItemStack[] leftOffer = new ItemStack[16];
        private final ItemStack[] rightOffer = new ItemStack[16];
        private boolean leftReady;
        private boolean rightReady;
        private boolean completing;

        private Session(UUID left, UUID right) {
            this.left = left;
            this.right = right;
        }
    }

    public static final class Holder implements InventoryHolder {
        private final UUID viewerId;

        public Holder(UUID viewerId) {
            this.viewerId = viewerId;
        }

        public UUID viewerId() {
            return viewerId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
