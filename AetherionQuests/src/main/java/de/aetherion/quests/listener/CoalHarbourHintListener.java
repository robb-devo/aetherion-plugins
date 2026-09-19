package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subtle actionbar when Forge Coal is ready to turn in: remind {@code /harbour}.
 */
public final class CoalHarbourHintListener implements Listener {

    private static final long COOLDOWN_MS = 12_000L;
    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();

    public CoalHarbourHintListener(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack.getType() != Material.COAL && stack.getType() != Material.CHARCOAL) {
            return;
        }
        scheduleCheck(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleCheck(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer());
    }

    private void scheduleCheck(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                maybeHint(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    private void maybeHint(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Quest quest = questManager.getQuest("forge_coal");
        if (quest == null) {
            return;
        }
        QuestState state = questManager.getQuestState(player, quest);
        if (state != QuestState.ACTIVE && state != QuestState.READY) {
            return;
        }
        if (countCoal(player) < 20) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHint.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        lastHint.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text()
                .append(Component.text("Ready · ", NamedTextColor.GRAY))
                .append(Component.text("/harbour", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" → Quartermaster", NamedTextColor.GRAY))
                .build());
    }

    private static int countCoal(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if (stack.getType() == Material.COAL || stack.getType() == Material.CHARCOAL) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
