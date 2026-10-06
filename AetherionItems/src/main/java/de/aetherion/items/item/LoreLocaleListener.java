package de.aetherion.items.item;

import de.aetherion.core.api.AetherServices;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Keeps every Aetherion item in the language of the player holding it (see {@link LoreLocale}).
 * <ul>
 *   <li><b>Language switch</b> ({@code /sprache}, {@code /language}): the whole inventory is rewritten at once
 *       (Quests tells Core, Core tells this listener).</li>
 *   <li><b>Join</b>: {@link GearTooltip} already refreshes the inventory 1 s after login.</li>
 *   <li><b>Anything that arrives later</b> (DevMenu, shops, drops, trades, crafting): healed one tick after the
 *       player picks it up, switches to it, clicks in an inventory or closes one. Only items whose tag differs
 *       from the player's language are touched, so this costs a tag read per slot.</li>
 * </ul>
 */
public final class LoreLocaleListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemManager items;
    private final Set<UUID> pending = new HashSet<>();
    private final Consumer<Player> langHook = this::onLanguageChanged;

    public LoreLocaleListener(JavaPlugin plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        AetherServices.addLangListener(langHook);
    }

    public void shutdown() {
        AetherServices.removeLangListener(langHook);
        pending.clear();
    }

    private void onLanguageChanged(Player player) {
        if (player == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            items.refreshPlayerItems(player, false);
            player.updateInventory();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            healSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        healSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            healSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            healSoon(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void healSoon(Player player) {
        if (!LoreLocale.ready() || !pending.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (player.isOnline()) {
                heal(player);
            }
        });
    }

    /** Rewrites the stacks whose language tag differs from the player's language. */
    public void heal(Player player) {
        boolean german = LoreLocale.german(player);
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        try (LoreLocale.Scope ignored = LoreLocale.viewing(player)) {
            for (ItemStack stack : inventory.getContents()) {
                changed |= healOne(stack, german);
            }
            for (ItemStack stack : inventory.getArmorContents()) {
                changed |= healOne(stack, german);
            }
            changed |= healOne(inventory.getItemInOffHand(), german);
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private boolean healOne(ItemStack stack, boolean german) {
        if (stack == null || stack.getType().isAir() || !items.isAetherionItem(stack)) {
            return false;
        }
        if (LoreLocale.tagged(stack) == german) {
            return false;
        }
        items.refreshItemLore(stack, false);
        return true;
    }
}
