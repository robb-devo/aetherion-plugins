package de.aetherion.items.listener;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;

import de.aetherion.items.blueprint.BlueprintUpgrade;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterApplier;
import de.aetherion.items.model.BoosterType;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;

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

        if (!itemManager.isAetherionItem(left)) {
            return;
        }

        BoosterType boosterType = itemManager.getBoosterType(right);

        // Stacks are allowed. The anvil result consumes one booster from the right slot.
        if (boosterType == null || right.getAmount() < 1) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return;
        }

        ItemStack result = left.clone();
        if (BoosterApplier.apply(itemManager, result, boosterType) != BoosterApplier.Status.APPLIED) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return;
        }
        event.setResult(result);
        setRepairCost(anvil, anvilView);
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
        if (!DungeonCore.isCore(core) || !itemManager.isAetherionItem(weapon)) {
            return false;
        }
        if (core.getAmount() != 1) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return true;
        }

        String itemId = itemManager.getItemId(weapon);
        if (!DungeonCore.canInfuse(itemId)) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return true;
        }

        int nextTier = DungeonCore.tier(weapon) + 1;
        if (nextTier > DungeonCore.MAX_TIER) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return true;
        }
        if (DungeonCore.coreGrade(core) != nextTier) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return true;
        }

        ItemStack result = weapon.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            event.setResult(null);
            setRepairCost(anvil, anvilView);
            return true;
        }

        DungeonCore.writeTier(meta, nextTier);
        DungeonCore.applyInfusionRarity(meta, nextTier);
        if (itemId != null && itemId.toLowerCase().contains("shortbow")) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.shortbowInterval(),
                    PersistentDataType.INTEGER,
                    DungeonCore.shortbowIntervalTicks(nextTier)
            );
        }
        if (itemId != null && itemId.toLowerCase().contains("longbow")) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.longbowCharge(),
                    PersistentDataType.INTEGER,
                    DungeonCore.longbowChargeTicks(nextTier)
            );
        }
        DungeonCore.patchLore(meta, itemId, nextTier);
        result.setItemMeta(meta);
        de.aetherion.items.dungeon.DungeonGearProgress.ensure(result, itemManager);
        ItemMeta polished = result.getItemMeta();
        if (polished != null) {
            de.aetherion.items.item.ItemPresentation.polish(polished);
            result.setItemMeta(polished);
        }
        de.aetherion.items.item.GearTooltip.finish(result, itemManager, true);
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
