package de.aetherion.items.listener;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;

import de.aetherion.items.blueprint.BlueprintUpgrade;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.util.QuestProgressHook;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public class AnvilBoosterListener implements Listener {

    private final ItemManager itemManager;

    public AnvilBoosterListener(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilDamaged(AnvilDamagedEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeBoosterResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        ItemStack right = event.getInventory().getItem(1);
        if (right == null || itemManager.getBoosterType(right) == null) {
            return;
        }
        QuestProgressHook.noteUsed(player, "APPLY_BOOSTER");
        QuestProgressHook.noteUsed(player, "AETHER_ANVIL");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }

        AnvilInventory anvil = event.getInventory();
        ItemStack left = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);

        if (left == null || left.getType().isAir() || right == null || right.getType().isAir()) {
            return;
        }

        if (tryDungeonCoreInfuse(event, anvil, anvilView, left, right)) {
            return;
        }

        // Blueprint tools upgrade only at Eldervale Forgehand — not on the anvil.
        if (isBlueprintUpgradeAttempt(left, right)) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return;
        }

        // Boosters socket through Manager → Anvil. The vanilla result slot no longer consumes them.
        if (itemManager.getBoosterType(left) != null || itemManager.getBoosterType(right) != null) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return;
        }

        // Vanilla repair/combine builds a new result and drops socket PDC.
        // Rename (empty right slot) returns above and is left alone.
        if (itemManager.isAetherionItem(left) || itemManager.isAetherionItem(right)) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
        }
    }

    private boolean tryDungeonCoreInfuse(
            PrepareAnvilEvent event,
            AnvilInventory anvil,
            AnvilView anvilView,
            ItemStack left,
            ItemStack right
    ) {
        ItemStack weapon = left;
        ItemStack core = right;
        if (DungeonCore.isCore(left) && DungeonCore.canInfuse(itemManager.getItemId(right))) {
            weapon = right;
            core = left;
        }
        ItemStack result = de.aetherion.items.item.DungeonCoreInfusion.infuse(itemManager, weapon, core);
        if (result == null) {
            if (DungeonCore.isCore(core) || DungeonCore.isCore(left)) {
                event.setResult(null);
                setRepairCost(anvil, anvilView);
                return true;
            }
            return false;
        }
        event.setResult(result);
        setRepairCost(anvil, anvilView);
        return true;
    }

    private boolean isBlueprintUpgradeAttempt(ItemStack left, ItemStack right) {
        return (BlueprintUpgrade.isStone(left) && BlueprintUpgrade.isBlueprintTool(itemManager.getItemId(right)))
                || (BlueprintUpgrade.isStone(right) && BlueprintUpgrade.isBlueprintTool(itemManager.getItemId(left)));
    }

    private void setRepairCost(AnvilInventory anvil, AnvilView anvilView) {
        anvilView.setRepairCost(0);

        try {
            anvil.setRepairCost(0);
        } catch (Exception ignored) {
        }
    }
}
