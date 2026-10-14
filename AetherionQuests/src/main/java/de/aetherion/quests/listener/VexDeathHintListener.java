package de.aetherion.quests.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.BoosterItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft nudge during Vex's Borderlands lesson after a death — plus a stingy care package.
 */
public final class VexDeathHintListener implements Listener {

    private static final String QUEST_ID = "lesson_steel";
    private static final long COOLDOWN_MS = 90_000L;
    private static final int CARE_COAL = 4;

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingHint = new ConcurrentHashMap<>();

    public VexDeathHintListener(JavaPlugin plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isOnLessonSteel(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHint.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        // Mark once on death; whisper + care pack only after respawn (avoids double dialog).
        pendingHint.put(player.getUniqueId(), Boolean.TRUE);
        lastHint.put(player.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(pendingHint.remove(player.getUniqueId()))) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> whisper(player), 20L);
    }

    private boolean isOnLessonSteel(Player player) {
        Quest quest = questManager.getQuest(QUEST_ID);
        if (quest == null || player == null) {
            return false;
        }
        QuestState state = questManager.getQuestState(player, quest);
        return state == QuestState.ACTIVE || state == QuestState.READY;
    }

    private void whisper(Player player) {
        if (player == null || !player.isOnline() || !isOnLessonSteel(player)) {
            return;
        }
        boolean gifted = giftCoalBoosters(player);
        player.sendMessage("");
        player.sendMessage("§cSergeant Vex §8» §f" + de.aetherion.quests.lang.LangPack.say(player, "vex.death_up", "You're vertical again."));
        if (gifted) {
            player.sendMessage("§cSergeant Vex §8» §7" + de.aetherion.quests.lang.LangPack.say(player, "vex.death_care",
                    "Four §8Coal Boosters§7. Don't make me write a receipt. Fuse them — or eat dirt again."));
        }
        if (QuestStoryGate.questCompleted(player, questManager, "lesson_boost")) {
            player.sendMessage("§cSergeant Vex §8» §7" + de.aetherion.quests.lang.LangPack.say(player, "vex.death_gear",
                    "Trouble? Combat set + boosters. Recipe Book and anvil. Then finish the ten."));
        } else {
            player.sendMessage("§cSergeant Vex §8» §7" + de.aetherion.quests.lang.LangPack.say(player, "vex.death_temper",
                    "Trouble? §eTemper§7 teaches boosters. Combat set's in the Recipe Book."));
            de.aetherion.quests.ui.QuestHint.show(player, "booster_tutor", "Temper");
        }
        player.sendMessage("");
    }

    /** Once per death-cooldown: 4 coal boosters into inventory (or drop at feet). */
    private boolean giftCoalBoosters(Player player) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getItemManager() == null) {
                return false;
            }
            ItemStack stack = new CustomItem(items.getItemManager()).boosters().coal();
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
            int amount = Math.min(CARE_COAL, BoosterItems.MAX_STACK);
            stack.setAmount(amount);
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            if (!left.isEmpty()) {
                for (ItemStack spill : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), spill);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
