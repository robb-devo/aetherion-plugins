package de.aetherion.items.farm;

import de.aetherion.items.economy.CompressedResource;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Put one compacted farm crop in, click Mill. Back → Root Cellar. */
public final class MillstoneGUI implements InventoryHolder {

    public static final int INPUT_SLOT = 10;
    public static final int MILL_SLOT = 13;
    public static final int OUT_WHEAT = 15;
    public static final int OUT_CARROT = 16;
    public static final int OUT_POTATO = 17;
    public static final int BACK_SLOT = 18;
    public static final int SIZE = 27;

    private final Inventory inventory;
    private final MillstoneRitual ritual;
    private final UUID stationId;
    private final Location stationAt;
    private boolean consumed;

    public MillstoneGUI(MillstoneRitual ritual, UUID stationId, Location stationAt) {
        this.ritual = ritual;
        this.stationId = stationId;
        this.stationAt = stationAt == null ? null : stationAt.clone();
        this.inventory = Bukkit.createInventory(this, SIZE, "§eFarm Millstone");
        paint();
    }

    public static void open(org.bukkit.entity.Player player, MillstoneRitual ritual, UUID stationId) {
        if (player == null || ritual == null) {
            return;
        }
        Location at = player.getLocation();
        var hit = MillstoneCabinet.findNearest(player.getLocation(), 6.0);
        if (hit != null) {
            at = hit.getLocation();
        }
        player.openInventory(new MillstoneGUI(ritual, stationId, at).getInventory());
    }

    private void paint() {
        ItemStack glass = pane(Material.LIME_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass);
        }
        inventory.setItem(4, button(
                Material.WHEAT,
                "§6Millstone",
                "§7Put §bCompacted§7 crop left.",
                "§7Outputs §dRefined§7 pantry crops right.",
                "§71 Compacted → 1 Refined · " + MillstoneRitual.SECONDS + "s"
        ));
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(MILL_SLOT, button(
                Material.GRINDSTONE,
                "§aMill Compacted Crop",
                "§7Accepts Compacted Wheat / Carrot / Potato.",
                "§7Gives matching Refined pantry crop."
        ));
        inventory.setItem(OUT_WHEAT, preview(CompressedResource.WHEAT.refined()));
        inventory.setItem(OUT_CARROT, preview(CompressedResource.CARROT.refined()));
        inventory.setItem(OUT_POTATO, preview(CompressedResource.POTATO.refined()));
        inventory.setItem(BACK_SLOT, button(
                Material.ARROW,
                "§eBack",
                "§7Root Cellar shelves."
        ));
    }

    private static ItemStack preview(ItemStack sample) {
        ItemStack item = sample == null ? new ItemStack(Material.BARRIER) : sample.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add("");
            lore.add("§8Possible mill output");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public MillstoneRitual ritual() {
        return ritual;
    }

    public UUID stationId() {
        return stationId;
    }

    public Location stationAt() {
        return stationAt;
    }

    public void markConsumed() {
        consumed = true;
    }

    public boolean consumed() {
        return consumed;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
