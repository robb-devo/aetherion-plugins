package de.aetherion.core.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti bot-raid menu ({@code /raidban}, staff only, not part of /devmenu). Every player on the network as their
 * head, in join order. A click turns a head into a barrier (selected); Confirm asks once more, then IP-bans every
 * selected player on every backend with {@link RaidBans#MESSAGE}. Staff and the moderator themself can't be picked.
 */
public final class RaidMenu implements TabExecutor, Listener {

    private static final int PER_PAGE = 45;
    private static final TextColor ROSE = TextColor.color(0xE0404E);
    private static final TextColor GOLD = TextColor.color(0xF2C94C);
    private static final TextColor MUTED = TextColor.color(0x8A8496);
    private static final TextColor SOFT = TextColor.color(0xCFC7E6);
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss");

    private final JavaPlugin plugin;
    private final RaidRoster roster;
    private final RaidBans bans;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /**
     * One moderator's open menu. {@code order} only grows while the menu is open: a head keeps its slot when others
     * join or leave, so a click always lands on the player that was drawn there. Players who left stay pickable.
     */
    private static final class Session {
        final Set<UUID> picked = new LinkedHashSet<>();
        final Map<UUID, RaidRoster.Seat> seen = new HashMap<>();
        final List<UUID> order = new ArrayList<>();
        final Set<UUID> online = new HashSet<>();
        int page;
        BukkitTask refresh;
    }

    /** Marks our inventories; {@code confirm} = the second, are-you-sure screen. */
    private record Holder(UUID viewer, boolean confirm) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public RaidMenu(JavaPlugin plugin, RaidRoster roster, RaidBans bans) {
        this.plugin = plugin;
        this.roster = roster;
        this.bans = bans;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(RaidRoster.PERMISSION)) {
            sender.sendMessage(Component.text("You don't have access to the raid menu.", ROSE));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("pardon")) {
            int n = bans.pardon(args[1], sender.getName());
            sender.sendMessage(n == 0
                    ? Component.text("No raid ban matches " + args[1] + ".", MUTED)
                    : Component.text("Lifted " + n + " raid ban" + (n == 1 ? "" : "s") + " for " + args[1] + " (all servers).", GOLD));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            List<RaidBans.Entry> all = bans.entries();
            sender.sendMessage(Component.text(all.size() + " raid ban" + (all.size() == 1 ? "" : "s") + ":", GOLD));
            for (RaidBans.Entry e : all) {
                sender.sendMessage(Component.text(" " + e.name() + "  " + e.ip() + "  by " + e.by()
                        + "  " + CLOCK.format(new Date(e.at())), SOFT));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /raidban [list | pardon <name|ip>] (the menu needs a player)");
            return true;
        }
        // A fresh command starts a fresh list (the confirm screen's Back keeps the current one).
        Session old = sessions.remove(player.getUniqueId());
        if (old != null && old.refresh != null) {
            old.refresh.cancel();
        }
        open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "pardon");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pardon")) {
            return bans.entries().stream().map(RaidBans.Entry::name).toList();
        }
        return List.of();
    }

    public void open(Player viewer) {
        Session s = sessions.computeIfAbsent(viewer.getUniqueId(), id -> new Session());
        Inventory inv = Bukkit.createInventory(new Holder(viewer.getUniqueId(), false), 54,
                Component.text("Raid Control", ROSE, TextDecoration.BOLD)
                        .append(Component.text("  · network join order", MUTED).decoration(TextDecoration.BOLD, false)));
        fill(viewer, s, inv);
        viewer.openInventory(inv);
        if (s.refresh == null) {
            // New bots keep joining during a raid: redraw every 2 s while the menu is open.
            s.refresh = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && !h.confirm()) {
                    fill(viewer, s, viewer.getOpenInventory().getTopInventory());
                }
            }, 40L, 40L);
        }
    }

    private void fill(Player viewer, Session s, Inventory inv) {
        List<RaidRoster.Seat> all = roster.network();
        s.online.clear();
        for (RaidRoster.Seat seat : all) {
            s.seen.put(seat.uuid(), seat);
            s.online.add(seat.uuid());
            if (!s.order.contains(seat.uuid())) {
                s.order.add(seat.uuid());
            }
        }
        int pages = Math.max(1, (s.order.size() + PER_PAGE - 1) / PER_PAGE);
        s.page = Math.max(0, Math.min(s.page, pages - 1));
        inv.clear();
        int from = s.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < s.order.size(); i++) {
            int index = from + i;
            RaidRoster.Seat seat = s.seen.get(s.order.get(index));
            inv.setItem(i, card(viewer, seat, index + 1, s.picked.contains(seat.uuid()), s.online.contains(seat.uuid())));
        }
        ItemStack bar = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, bar);
        }
        if (s.page > 0) {
            inv.setItem(45, named(Material.ARROW, Component.text("Previous page", SOFT), List.of()));
        }
        inv.setItem(46, named(Material.LIME_DYE, Component.text("Pick everyone on this page", SOFT),
                List.of(Component.text("Staff and you stay out.", MUTED))));
        inv.setItem(47, named(Material.GRAY_DYE, Component.text("Clear picks", SOFT), List.of()));
        int left = s.order.size() - s.online.size();
        inv.setItem(49, named(Material.CLOCK, Component.text(s.online.size() + " online"
                        + (left > 0 ? " · " + left + " left" : ""), GOLD),
                List.of(Component.text(s.picked.size() + " picked", s.picked.isEmpty() ? MUTED : ROSE),
                        Component.text("Page " + (s.page + 1) + " / " + pages, MUTED),
                        Component.text("Oldest join first, newest last.", MUTED))));
        if (s.page < pages - 1) {
            inv.setItem(52, named(Material.ARROW, Component.text("Next page", SOFT), List.of()));
        }
        inv.setItem(53, s.picked.isEmpty()
                ? named(Material.GRAY_CONCRETE, Component.text("Confirm", MUTED), List.of(Component.text("Pick players first.", MUTED)))
                : named(Material.RED_CONCRETE, Component.text("Confirm · ban " + s.picked.size() + " IP" + (s.picked.size() == 1 ? "" : "s"), ROSE, TextDecoration.BOLD),
                        List.of(Component.text("Asks once more before banning.", MUTED))));
    }

    private ItemStack card(Player viewer, RaidRoster.Seat seat, int number, boolean picked, boolean online) {
        boolean locked = seat.staff() || seat.uuid().equals(viewer.getUniqueId());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("#" + number + " · joined " + CLOCK.format(new Date(seat.joined())), MUTED));
        lore.add(Component.text(online ? "on " + seat.server() : "left the network (still pickable)", MUTED));
        if (locked) {
            lore.add(Component.text(seat.uuid().equals(viewer.getUniqueId()) ? "That's you." : "Staff · protected", GOLD));
        } else if (picked) {
            lore.add(Component.text("Picked for the raid ban. Click to undo.", ROSE));
        } else {
            lore.add(Component.text("Click to pick.", SOFT));
        }
        if (picked) {
            return named(Material.BARRIER, Component.text(seat.name(), ROSE, TextDecoration.BOLD), lore);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(seat.uuid(), seat.name());
        if (!seat.skin().isBlank()) {
            profile.setProperty(new ProfileProperty("textures", seat.skin(),
                    seat.skinSignature().isBlank() ? null : seat.skinSignature()));
        }
        meta.setPlayerProfile(profile);
        meta.displayName(Component.text(seat.name(), locked ? GOLD : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void openConfirm(Player viewer, Session s) {
        Inventory inv = Bukkit.createInventory(new Holder(viewer.getUniqueId(), true), 27,
                Component.text("Ban " + s.picked.size() + " IP" + (s.picked.size() == 1 ? "" : "s") + "?", ROSE, TextDecoration.BOLD));
        ItemStack bar = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bar);
        }
        List<Component> names = new ArrayList<>();
        int shown = 0;
        for (UUID id : s.picked) {
            RaidRoster.Seat seat = s.seen.get(id);
            if (seat != null && shown++ < 12) {
                names.add(Component.text("· " + seat.name() + "  " + seat.server(), SOFT));
            }
        }
        if (s.picked.size() > 12) {
            names.add(Component.text("… and " + (s.picked.size() - 12) + " more", MUTED));
        }
        names.add(Component.text(" "));
        names.add(Component.text("Kick message:", MUTED));
        names.add(Component.text(RaidBans.MESSAGE, MUTED));
        inv.setItem(11, named(Material.RED_CONCRETE, Component.text("Ban them all", ROSE, TextDecoration.BOLD),
                List.of(Component.text("IP ban on every server, kicks them now.", MUTED))));
        inv.setItem(13, named(Material.PAPER, Component.text(s.picked.size() + " picked", GOLD), names));
        inv.setItem(15, named(Material.LIME_CONCRETE, Component.text("Back", SOFT), List.of(Component.text("Nothing is banned.", MUTED))));
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || !viewer.hasPermission(RaidRoster.PERMISSION)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Session s = sessions.computeIfAbsent(viewer.getUniqueId(), id -> new Session());
        int slot = event.getRawSlot();
        if (holder.confirm()) {
            if (slot == 11) {
                execute(viewer, s);
            } else if (slot == 15) {
                open(viewer);
            }
            return;
        }
        Inventory inv = event.getView().getTopInventory();
        if (slot < PER_PAGE) {
            int index = s.page * PER_PAGE + slot;
            if (index >= s.order.size()) {
                return;
            }
            RaidRoster.Seat seat = s.seen.get(s.order.get(index));
            if (seat.staff() || seat.uuid().equals(viewer.getUniqueId())) {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                return;
            }
            if (!s.picked.remove(seat.uuid())) {
                s.picked.add(seat.uuid());
            }
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, s.picked.contains(seat.uuid()) ? 0.8f : 1.2f);
        } else if (slot == 45 && s.page > 0) {
            s.page--;
        } else if (slot == 52) {
            s.page++;
        } else if (slot == 46) {
            int from = s.page * PER_PAGE;
            for (int i = from; i < Math.min(s.order.size(), from + PER_PAGE); i++) {
                RaidRoster.Seat seat = s.seen.get(s.order.get(i));
                if (!seat.staff() && !seat.uuid().equals(viewer.getUniqueId())) {
                    s.picked.add(seat.uuid());
                }
            }
        } else if (slot == 47) {
            s.picked.clear();
        } else if (slot == 53 && !s.picked.isEmpty()) {
            openConfirm(viewer, s);
            return;
        } else {
            return;
        }
        fill(viewer, s, inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        // Switching between the list and the confirm screen also closes one: only stop once nothing of ours is open.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                return;
            }
            Session s = sessions.get(id);
            if (s != null && s.refresh != null) {
                s.refresh.cancel();
                s.refresh = null;
            }
        });
    }

    private void execute(Player viewer, Session s) {
        List<RaidBans.Entry> entries = new ArrayList<>();
        for (UUID id : s.picked) {
            RaidRoster.Seat seat = s.seen.get(id);
            if (seat == null || seat.staff() || seat.uuid().equals(viewer.getUniqueId()) || seat.ip().isBlank()) {
                continue;
            }
            entries.add(new RaidBans.Entry(seat.ip(), seat.name(), seat.uuid().toString(), viewer.getName(), 0L));
        }
        // Never ban an IP that staff or the moderator use, even when a picked account shares it, and never a
        // loopback or LAN address (that is the proxy's address when forwarding is off: banning it bans everyone).
        Set<String> protectedIps = new HashSet<>();
        if (viewer.getAddress() != null && viewer.getAddress().getAddress() != null) {
            protectedIps.add(viewer.getAddress().getAddress().getHostAddress());
        }
        for (RaidRoster.Seat seat : s.seen.values()) {
            if (seat.staff() && !seat.ip().isBlank()) {
                protectedIps.add(seat.ip());
            }
        }
        for (RaidRoster.Seat seat : roster.network()) {
            if (seat.staff() && !seat.ip().isBlank()) {
                protectedIps.add(seat.ip());
            }
        }
        entries.removeIf(e -> protectedIps.contains(e.ip()) || localAddress(e.ip()));
        viewer.closeInventory();
        s.picked.clear();
        if (entries.isEmpty()) {
            viewer.sendMessage(Component.text("Nobody left to ban (staff, you, IPs staff use and LAN/proxy addresses are skipped).", MUTED));
            return;
        }
        bans.ban(entries, viewer.getName());
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
        viewer.sendMessage(Component.text("Raid ban: " + entries.size() + " player" + (entries.size() == 1 ? "" : "s")
                + " IP-banned on every server.", ROSE)
                .append(Component.text("  Undo: /raidban pardon <name>", MUTED)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff != viewer && RaidRoster.isStaff(staff)) {
                staff.sendMessage(Component.text("[Raid] " + viewer.getName() + " IP-banned " + entries.size() + " player"
                        + (entries.size() == 1 ? "" : "s") + ".", MUTED));
            }
        }
    }

    private static boolean localAddress(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.%]+")) {
            return true;
        }
        try {
            // A literal never triggers a DNS lookup.
            java.net.InetAddress a = java.net.InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress();
        } catch (java.net.UnknownHostException ex) {
            return true;
        }
    }
}
