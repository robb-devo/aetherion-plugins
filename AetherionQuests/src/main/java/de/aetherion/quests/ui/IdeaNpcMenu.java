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

    private static final Map<String, String[]> BLURB = Map.ofEntries(
            Map.entry("town_crier", new String[] {"§7Rings a bell, shouts real tips.", "§7Gives Lullaby step 1."}),
            Map.entry("street_sweeper", new String[] {"§7Sweeps the square on a tiny route.", "§7Gossip included."}),
            Map.entry("lamp_lighter", new String[] {"§7Changes lines with the clock.", "§7Lights up at dusk."}),
            Map.entry("ysolde", new String[] {"§7Archivist. Main Lullaby giver.", "§8Spot: Capital Archive"}),
            Map.entry("berit", new String[] {"§7Oldest woman in town. The song.", "§8Spot: cottage, edge of the Capital"}),
            Map.entry("nell", new String[] {"§7A child who hums the bells.", "§8Spot: harbour lane"}),
            Map.entry("hesper", new String[] {"§7Speaks for the Anchor camp.", "§8Spot: under the Fallen Anchor"}),
            Map.entry("ferryman", new String[] {"§7Waits at the end of the road.", "§8Spot: Veil Quay"})
    );

    /** Top row: idea NPCs. Bottom row: the main-story cast. */
    private static final int[] IDEA_SLOTS = {11, 13, 15};
    private static final int[] STORY_SLOTS = {20, 21, 22, 23, 24};
    private static final int CLOSE_SLOT = 31;

    public IdeaNpcMenu(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 36, TITLE);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(4, item(Material.WRITABLE_BOOK, "§6Idea NPCs · Story cast",
                "§7Top: invented ambient cast.",
                "§7Bottom: Das Schlaflied (main story).",
                "§7None are placed. Click → anchor → place it.",
                "§8Story NPCs talk after the tutorial."));
        fill(inv, IDEA_SLOTS, QuestNPCRegistry.ideaNpcIds());
        fill(inv, STORY_SLOTS, QuestNPCRegistry.storyNpcIds());
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, "§cClose"));
        player.openInventory(inv);
    }

    private static void fill(Inventory inv, int[] slots, List<String> ids) {
        for (int i = 0; i < ids.size() && i < slots.length; i++) {
            String id = ids.get(i);
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
            inv.setItem(slots[i], item(icon.getType(), profile.chatPrefix() + "§l" + npc.getName(), lore.toArray(String[]::new)));
        }
    }

    private static String idAt(int raw) {
        List<String> ideas = QuestNPCRegistry.ideaNpcIds();
        for (int i = 0; i < IDEA_SLOTS.length && i < ideas.size(); i++) {
            if (IDEA_SLOTS[i] == raw) {
                return ideas.get(i);
            }
        }
        List<String> story = QuestNPCRegistry.storyNpcIds();
        for (int i = 0; i < STORY_SLOTS.length && i < story.size(); i++) {
            if (STORY_SLOTS[i] == raw) {
                return story.get(i);
            }
        }
        return null;
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
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        String id = idAt(raw);
        if (id == null) {
            return;
        }
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
