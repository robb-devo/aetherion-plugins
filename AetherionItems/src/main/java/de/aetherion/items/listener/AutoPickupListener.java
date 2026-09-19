package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.ValueLore;
import de.aetherion.items.skill.SkillService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AutoPickupListener implements Listener {

    public static final int UNLOCK_LEVEL = 5;
    private static final double RANGE = 8.0d;
    private static final int MAX_PER_TICK = 16;

    private final AetherionItems plugin;
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public AutoPickupListener(AetherionItems plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::vacuum, 10L, 10L);
    }

    public static boolean unlocked(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
            return false;
        }
        SkillService skills = pluginSkills();
        return skills != null && skills.accountLevel(player) >= UNLOCK_LEVEL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (BUSY.get()) {
            return;
        }
        Item item = event.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!item.isValid() || BUSY.get()) {
                return;
            }
            Player owner = owner(item);
            if (owner != null && unlocked(owner) && sameWorld(owner, item)
                    && owner.getLocation().distanceSquared(item.getLocation()) <= RANGE * RANGE) {
                tryCollect(owner, item);
                return;
            }
            Player nearest = nearestUnlocked(item);
            if (nearest != null) {
                tryCollect(nearest, item);
            }
        });
    }

    private void vacuum() {
        if (BUSY.get()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!unlocked(player)) {
                continue;
            }
            int taken = 0;
            for (var entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
                if (taken >= MAX_PER_TICK) {
                    break;
                }
                if (entity instanceof Item item && tryCollect(player, item)) {
                    taken++;
                }
            }
        }
    }

    private static Player owner(Item item) {
        UUID id = item.getOwner();
        if (id == null) {
            id = item.getThrower();
        }
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private static boolean sameWorld(Player player, Item item) {
        return player.getWorld().equals(item.getWorld());
    }

    private static Player nearestUnlocked(Item item) {
        Player best = null;
        double bestDist = RANGE * RANGE;
        for (Player player : item.getWorld().getPlayers()) {
            if (!unlocked(player)) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(item.getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private boolean tryCollect(Player player, Item item) {
        if (BUSY.get() || item == null || !item.isValid() || item.isDead()) {
            return false;
        }
        if (item.getPickupDelay() > 0) {
            return false;
        }
        UUID ownerId = item.getOwner();
        if (ownerId != null && !ownerId.equals(player.getUniqueId())) {
            return false;
        }
        UUID thrower = item.getThrower();
        if (thrower != null && !thrower.equals(player.getUniqueId())) {
            return false;
        }
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (plugin.getItemValues() != null) {
            ValueLore.apply(stack, plugin.getItemValues());
        }
        BUSY.set(true);
        try {
            int before = stack.getAmount();
            var leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                item.remove();
                de.aetherion.items.util.QuestProgressHook.noteCollected(player, stack.getType(), before);
                AetherionItems items = AetherionItems.getInstance();
                if (items != null && items.recipeUnlocks() != null) {
                    items.recipeUnlocks().noteObtained(player, stack);
                }
                return true;
            }
            ItemStack left = leftover.values().iterator().next();
            int kept = Math.max(0, before - left.getAmount());
            if (kept > 0) {
                de.aetherion.items.util.QuestProgressHook.noteCollected(player, stack.getType(), kept);
            }
            item.setItemStack(left);
            item.setPickupDelay(60);
            item.setThrower(player.getUniqueId());
            return false;
        } finally {
            BUSY.set(false);
        }
    }

    private static SkillService pluginSkills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
