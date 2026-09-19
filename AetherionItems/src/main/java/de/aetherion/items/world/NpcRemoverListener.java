package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.menu.dev.DevBridges;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class NpcRemoverListener implements Listener {

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lNPC Remover");
            meta.setLore(List.of(
                    "§7DEV · despawn an Aetherion NPC.",
                    "",
                    "§eRight-click a quest NPC",
                    "§eor the Dungeon Keeper / Trader /",
                    "§eGear Trader / Silas Markup /",
                    "§eCrystal Liquidator /",
                    "§eBazaar / Auction House /",
                    "§eLucky Vince.",
                    "§7Definition stays — place the",
                    "§7anchor again to move them."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.npcRemover(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isRemover(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.npcRemover(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isRemover(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        Entity target = event.getRightClicked();
        if (de.aetherion.items.economy.TraderService.isTrader(target)) {
            target.remove();
            player.sendMessage("§eDespawned §fTrader§e. Place the emerald again to move him.");
            return;
        }
        if (de.aetherion.items.economy.GearTraderService.isGearTrader(target)) {
            target.remove();
            player.sendMessage("§eDespawned §fGear Trader§e. Place the iron sword again to move him.");
            return;
        }
        if (de.aetherion.items.economy.FenceService.isFence(target)) {
            target.remove();
            player.sendMessage("§eDespawned §fSilas Markup§e. Place the gold nugget again to move him.");
            return;
        }
        if (de.aetherion.items.economy.LiquidatorService.isLiquidator(target)) {
            target.remove();
            player.sendMessage("§eDespawned §fCrystal Liquidator§e. Place the amethyst again to move him.");
            return;
        }
        if (de.aetherion.items.economy.MarketService.isMarketNpc(target)) {
            String name = target.getCustomName() == null ? "market NPC" : target.getCustomName();
            target.remove();
            player.sendMessage("§eDespawned " + name + "§e. Place the anchor again to move them.");
            return;
        }
        if (de.aetherion.items.casino.CasinoService.isCasinoNpc(target)) {
            target.remove();
            player.sendMessage("§eDespawned §fLucky Vince§e. Place the gold nugget again to move him.");
            return;
        }
        if (de.aetherion.items.casino.CasinoCabinet.despawn(target)) {
            player.sendMessage("§ePacked up the §fSlot Machine§e.");
            return;
        }
        if (de.aetherion.items.casino.RouletteCabinet.despawn(target)) {
            player.sendMessage("§ePacked up the §fRoulette Table§e.");
            return;
        }
        if (DevBridges.despawnDungeonKeeper(target)) {
            player.sendMessage("§eDespawned §fDungeon Keeper§e. Place the lantern again to move him.");
            return;
        }
        String questName = DevBridges.despawnQuestNpc(target);
        if (questName != null) {
            player.sendMessage("§eDespawned §f" + questName + "§e. Place the anchor again to move him.");
            return;
        }
        player.sendMessage("§cNot an Aetherion NPC.");
    }
}
