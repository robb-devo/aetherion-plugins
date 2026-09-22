package de.aetherion.quests.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.service.QuestNPCSpawnService;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class NpcAnchorListener implements Listener {

    private final QuestNPCSpawnService spawnService = new QuestNPCSpawnService();

    public static NamespacedKey key() {
        return new NamespacedKey(AetherionQuests.getInstance(), "npc_anchor");
    }

    public static ItemStack create(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        String name = npc == null ? npcId : npc.getName();
        String boss = QuestNPCRegistry.linkedBoss(npcId);

        Material material = anchorMaterial(npcId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(boss == null
                    ? "§bNPC Anchor §7(" + name + ")"
                    : "§b" + name + " §8· §7" + boss);
            java.util.ArrayList<String> lore = new java.util.ArrayList<>();
            lore.add("§7Right-click a block to place");
            lore.add("§f" + name + "§7 there.");
            if ("amethyst_mines_guide".equalsIgnoreCase(npcId)) {
                lore.add("§8FancyNPC · Elder Vale Mining Island");
                lore.add("§7Mining skill gate → Amethyst Area spawn");
                lore.add("§8Uses the planted §f/amethyst §8anchor");
            }
            if (boss != null) {
                lore.add("§8Boss hunt: §f" + boss);
            }
            lore.add("§eSneak + right-click §7despawns him.");
            lore.add("§eSneak + click the NPC §7also removes him.");
            lore.add("");
            lore.add("§8Admin tool — not consumed");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, npcId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material anchorMaterial(String npcId) {
        if (npcId == null) {
            return Material.IRON_PICKAXE;
        }
        return switch (npcId.toLowerCase(java.util.Locale.ROOT)) {
            case "amethyst_mines_guide" -> Material.AMETHYST_CLUSTER;
            case "farm_isle_guide" -> Material.WHEAT;
            case "forage_pad_guide" -> Material.SPRUCE_SAPLING;
            case "eldervale_welcome" -> Material.IRON_PICKAXE;
            case "eldervale_upgrade" -> Material.ANVIL;
            default -> Material.IRON_PICKAXE;
        };
    }

    public static String npcId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("aetherionquests.admin")) {
            return;
        }

        String npcId = npcId(player.getInventory().getItemInMainHand());
        if (npcId == null) {
            return;
        }

        event.setCancelled(true);

        if (player.isSneaking()) {
            despawn(player, npcId);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            player.sendMessage(ChatColor.RED + "Right-click a block to place him, or sneak to despawn.");
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Location location = block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0f);

        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc == null) {
            player.sendMessage(ChatColor.RED + "Unknown quest NPC: " + npcId);
            return;
        }

        QuestNPC spawned = spawnService.spawnNPC(npcId, location);
        if (spawned == null) {
            player.sendMessage(ChatColor.RED + "Could not spawn " + npcId + ".");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Anchored " + ChatColor.WHITE + spawned.getName() + ChatColor.GREEN + ".");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("aetherionquests.admin")) {
            return;
        }
        String npcId = npcId(player.getInventory().getItemInMainHand());
        if (npcId == null) {
            return;
        }
        Entity clicked = event.getRightClicked();
        boolean match = false;
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null && npc.getEntityId() != null && npc.getEntityId().equals(clicked.getUniqueId())) {
            match = true;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null) {
            String tagged = clicked.getPersistentDataContainer().get(
                    AetherKeys.QUEST_NPC,
                    PersistentDataType.STRING
            );
            if (npcId.equalsIgnoreCase(tagged)) {
                match = true;
            }
            if (plugin.getMarkerManager() != null && npcId.equalsIgnoreCase(plugin.getMarkerManager().getNpcId(clicked))) {
                match = true;
            }
        }
        if (!match) {
            return;
        }
        event.setCancelled(true);
        despawn(player, npcId);
    }

    private void despawn(Player player, String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        String name = npc == null ? npcId : npc.getName();
        // Forget saved coords so NpcGuard / ensureAllUnique cannot instantly restore.
        if (spawnService.forget(npcId)) {
            player.sendMessage(ChatColor.YELLOW + "Despawned " + ChatColor.WHITE + name
                    + ChatColor.YELLOW + ". Place the anchor again to set a new spot.");
            return;
        }
        player.sendMessage(ChatColor.RED + "No spawned NPC found for " + ChatColor.WHITE + name + ChatColor.RED + ".");
    }
}
