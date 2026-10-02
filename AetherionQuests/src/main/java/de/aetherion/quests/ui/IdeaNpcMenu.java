package de.aetherion.quests.ui;

import de.aetherion.quests.listener.NpcAnchorListener;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /questnpc extras} — a small page of invented ambient NPCs (none placed by
 * default). Click one to get its anchor; place the anchor to spawn it. Additive: the
 * NPC studio / existing anchors are untouched.
 */
public final class IdeaNpcMenu implements Listener {

    private static final String TITLE = "§8NPC Extras · Idea NPCs";

    private static final Map<String, String[]> BLURB = Map.of(
            "town_crier", new String[] {"§7Rings a bell, shouts real tips.", "§7Loud. Useful. Loud."},
            "street_sweeper", new String[] {"§7Sweeps the square on a tiny route.", "§7Gossip included."},
            "lamp_lighter", new String[] {"§7Changes lines with the clock.", "§7Lights up at dusk."}
    );

    public IdeaNpcMenu(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(4, item(Material.WRITABLE_BOOK, "§6Idea NPCs",
                "§7Invented ambient cast. Not placed.",
                "§7Click one → anchor → place it.",
                "§8Anonymous cast skin · voice · routine · banter"));
        int slot = 11;
        for (String id : QuestNPCRegistry.ideaNpcIds()) {
            QuestNPC npc = QuestNPCRegistry.getNPC(id);
            LivingNpcProfile profile = LivingNpcProfile.of(id);
            if (npc == null || profile == null) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("§8" + profile.subtitle());
            for (String line : BLURB.getOrDefault(id, new String[0])) {
                lore.add(line);
            }
            lore.add("");
            lore.add("§eClick §7→ get anchor");
            ItemStack icon = profile.handItem() != null ? profile.handItem() : new ItemStack(Material.PLAYER_HEAD);
            inv.setItem(slot, item(icon.getType(), profile.chatPrefix() + "§l" + npc.getName(), lore.toArray(String[]::new)));
            slot += 2;
        }
        inv.setItem(22, item(Material.BARRIER, "§cClose"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == 22) {
            player.closeInventory();
            return;
        }
        List<String> ids = QuestNPCRegistry.ideaNpcIds();
        int index = (raw - 11) / 2;
        if (raw < 11 || (raw - 11) % 2 != 0 || index < 0 || index >= ids.size()) {
            return;
        }
        String id = ids.get(index);
        ItemStack anchor = NpcAnchorListener.create(id);
        if (anchor == null) {
            player.sendMessage("§cNo anchor for " + id + ".");
            return;
        }
        player.getInventory().addItem(anchor);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        player.sendMessage("§aAnchor for §f" + id + "§a — place it where they should stand.");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
