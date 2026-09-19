package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoidStickListener implements Listener {

    private static final double RANGE = 25.0;
    private static final long PULL_INTERVAL_MS = 180L;
    private final ItemManager itemManager;
    private final Map<UUID, Long> lastPull = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pulse = new ConcurrentHashMap<>();

    public VoidStickListener(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isVoidStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
        pull(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isVoidStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        pull(player);
    }

    private void pull(Player player) {
        ItemStack stick = player.getInventory().getItemInMainHand();
        int tier = de.aetherion.items.item.DungeonCore.tier(stick);
        long now = System.currentTimeMillis();
        Long last = lastPull.get(player.getUniqueId());
        if (last != null && now - last < de.aetherion.items.item.DungeonCore.voidIntervalMs(tier)) {
            return;
        }
        lastPull.put(player.getUniqueId(), now);
        int beat = pulse.merge(player.getUniqueId(), 1, Integer::sum);

        boolean drained = false;
        double range = de.aetherion.items.item.DungeonCore.voidRange(tier);
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity.equals(player) || !entity.isValid()) {
                continue;
            }
            Vector to = player.getLocation().toVector().subtract(entity.getLocation().toVector());
            double dist = to.length();
            if (dist < 0.8 || dist > range) {
                continue;
            }
            to.normalize().multiply(0.04 + (1.0 - dist / range) * 0.045);
            entity.setVelocity(entity.getVelocity().multiply(0.9).add(to));

            if (beat % 2 == 0) {
                entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation().add(0, 0.4, 0), 2, 0.1, 0.1, 0.1, 0.01);
            }

            if (entity instanceof Item) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !canDrain(living)) {
                continue;
            }
            if (beat % 3 != 0) {
                continue;
            }
            double amount = de.aetherion.items.item.DungeonCore.voidDrain(tier);
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(player)
                    .build();
            living.damage(amount, source);
            HealthListener health = AetherionItems.getInstance().getHealthListener();
            if (health != null) {
                health.heal(player, amount * 0.3);
            }
            drained = true;
        }
        if (drained) {
            player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private boolean canDrain(LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (AetherEntities.isPet(target)
                || AetherEntities.isBoss(target)
                || AetherEntities.isBossMinion(target)
                || AetherEntities.isSetMinion(target)) {
            return false;
        }
        return true;
    }

    private boolean isVoidStick(ItemStack item) {
        return "aetherion_void_stick".equalsIgnoreCase(itemManager.getItemId(item));
    }
}
