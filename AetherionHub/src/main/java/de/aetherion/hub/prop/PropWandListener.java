package de.aetherion.hub.prop;

import de.aetherion.hub.island.FaweIslandPaste;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PropWandListener implements Listener {

    private static final String MENU_TITLE = "§8Prop Wand";
    private static final long COOLDOWN_MS = 400L;

    private final Plugin plugin;
    private final PropWand wand;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public PropWandListener(Plugin plugin, PropWand wand) {
        this.plugin = plugin;
        this.wand = wand;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!wand.isWand(item)) {
            return;
        }
        if (!player.hasPermission(PropWand.PERMISSION)) {
            player.sendMessage("§cNo permission.");
            event.setCancelled(true);
            return;
        }
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }
        event.setCancelled(true);

        if (left && player.isSneaking()) {
            wand.cycleRotate(item);
            int rot = wand.rotateOverride(item);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7Rotate: " + (rot < 0 ? "§aauto (face look)" : "§e" + rot + "°")));
            return;
        }
        if (left) {
            wand.cycle(item, 1);
            PropCatalog.Prop prop = wand.selected(item);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§6Prop §f" + prop.display() + " §8· §7" + prop.zone()));
            return;
        }
        if (right && player.isSneaking()) {
            openMenu(player);
            return;
        }
        if (right) {
            if (!cooled(player)) {
                return;
            }
            pasteAtLook(player, item);
        }
    }

    @EventHandler
    public void onMenu(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getType().isAir()) {
            return;
        }
        String name = clicked.getItemMeta().getDisplayName();
        if (name == null) {
            return;
        }
        // Match by lore id line
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) {
            return;
        }
        String idLine = meta.getLore().get(0);
        String id = idLine == null ? "" : idLine.replace("§8", "").trim();
        PropCatalog.Prop prop = PropCatalog.get(id);
        if (prop == null) {
            return;
        }
        ItemStack wandItem = player.getInventory().getItemInMainHand();
        if (!wand.isWand(wandItem)) {
            player.sendMessage("§cHold the Prop Wand in your main hand.");
            return;
        }
        wand.select(wandItem, prop);
        player.closeInventory();
        player.sendMessage("§aSelected §f" + prop.display() + "§a. Look + right-click to paste.");
    }

    private void openMenu(Player player) {
        /* 54 slots = full catalog (40) without truncating the second pass. */
        Inventory inventory = Bukkit.createInventory(new MenuHolder(), 54, MENU_TITLE);
        int slot = 0;
        for (PropCatalog.Prop prop : PropCatalog.all()) {
            if (slot >= 54) {
                break;
            }
            ItemStack icon = new ItemStack(prop.icon() == null ? Material.PAPER : prop.icon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + prop.display());
                meta.setLore(java.util.List.of(
                        "§8" + prop.id(),
                        "§7" + prop.zone(),
                        "§7" + prop.tip(),
                        "",
                        "§eClick to select"
                ));
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot++, icon);
        }
        player.openInventory(inventory);
    }

    private void pasteAtLook(Player player, ItemStack item) {
        if (!FaweIslandPaste.fawePresent()) {
            player.sendMessage("§cFastAsyncWorldEdit is required.");
            return;
        }
        PropCatalog.Prop prop = wand.selected(item);
        File schem = PropCatalog.resolve(plugin, prop.id());
        if (schem == null || !schem.isFile()) {
            player.sendMessage("§cSchematic missing: §f" + prop.id() + ".schem");
            player.sendMessage("§7Expected under §fplugins/AetherionHub/props/ §7or FAWE schematics.");
            return;
        }
        Location target = lookAnchor(player);
        if (target == null || target.getWorld() == null) {
            player.sendMessage("§cLook at a block within 64 blocks.");
            return;
        }
        int rotate = wand.rotateOverride(item);
        if (rotate < 0) {
            rotate = facingDegrees(player);
        }
        if (!FaweIslandPaste.tryBegin()) {
            player.sendMessage("§cAnother paste is already running. Wait a moment.");
            return;
        }
        World world = target.getWorld();
        int x = target.getBlockX();
        int y = target.getBlockY();
        int z = target.getBlockZ();
        player.sendMessage("§7Pasting §f" + prop.display()
                + " §7at §f" + x + " " + y + " " + z
                + " §7rot §f" + rotate + "° §8(ignore-air)");
        int rot = rotate;
        FaweIslandPaste.runAsync(plugin, () -> FaweIslandPaste.paste(
                plugin,
                player,
                prop.id(),
                schem,
                world,
                x,
                y,
                z,
                true,
                rot
        ));
    }

    /** Block under the crosshair — ground layer replaces that block (INDEX paste rule). */
    private static Location lookAnchor(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                64.0,
                FluidCollisionMode.NEVER,
                true
        );
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }
        Block block = hit.getHitBlock();
        return block.getLocation();
    }

    /**
     * Degrees so the schem's authored south front faces the direction the player is looking.
     * Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east.
     */
    static int facingDegrees(Player player) {
        float yaw = player.getLocation().getYaw();
        while (yaw < 0.0f) {
            yaw += 360.0f;
        }
        while (yaw >= 360.0f) {
            yaw -= 360.0f;
        }
        int sector = Math.round(yaw / 90.0f) % 4;
        return sector * 90;
    }

    private boolean cooled(Player player) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        cooldown.put(player.getUniqueId(), now);
        return true;
    }

    public static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
