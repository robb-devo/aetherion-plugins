package de.aetherion.items.listener;

import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pathwarden unique — Event Horizon gravity well (5s cooldown).
 */
public final class GravwellCleaverListener implements Listener {

    private static final long COOLDOWN_TICKS = 100L; // 5s

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final TestPrototypeAbilities prototypes;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public GravwellCleaverListener(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
        this.prototypes = new TestPrototypeAbilities(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!isGravwell(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        var player = event.getPlayer();
        if (prototypes.isGravityBusy(player.getUniqueId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5Gravwell §7already open…"));
            return;
        }

        long tick = Bukkit.getCurrentTick();
        int cooldownTicks = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                (int) COOLDOWN_TICKS
        );
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§5Gravwell Cleaver §7recharging… §f" + left + "s"
            ));
            return;
        }

        nextUseTick.put(player.getUniqueId(), tick + cooldownTicks);
        double dmg = Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
        prototypes.castGravwell(player, dmg);
    }

    private boolean isGravwell(ItemStack item) {
        return "gravwell_cleaver".equalsIgnoreCase(itemManager.getItemId(item));
    }
}
