package de.aetherion.items.listener;

import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aetherblade right-click: blink to the 5 nearest enemies and strike each.
 */
public class AetherbladeListener implements Listener {

    private static final int COOLDOWN_TICKS = 200;
    private static final int MAX_TARGETS = 5;
    private static final double RANGE = 16.0;
    private static final long HOP_DELAY_TICKS = 8L;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public AetherbladeListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
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

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAetherblade(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§5Rift Dance §7recharging… §f" + left + "s"
            ));
            return;
        }

        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        List<LivingEntity> targets = findTargets(player, item);
        if (targets.isEmpty()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7No enemies close enough."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }

        int cooldown = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                de.aetherion.items.item.DungeonCore.aetherbladeCooldownTicks(tier)
        );
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
        player.setCooldown(item.getType(), cooldown);

        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                strike(player, target);
            }, i * HOP_DELAY_TICKS);
        }
    }

    private void strike(Player player, LivingEntity target) {
        if (target == null || !target.isValid() || target.isDead()) {
            return;
        }

        Vector away = player.getLocation().toVector().subtract(target.getLocation().toVector());
        if (away.lengthSquared() < 0.01) {
            away = target.getLocation().getDirection().multiply(-1);
        }
        away.normalize().multiply(0.85);
        Location dest = target.getLocation().clone().add(away);
        dest.setY(target.getLocation().getY());
        Vector look = target.getLocation().toVector().subtract(dest.toVector());
        look.setY(0);
        if (look.lengthSquared() < 0.0001) {
            look = target.getLocation().getDirection();
            look.setY(0);
        }
        if (look.lengthSquared() > 0.0001) {
            dest.setDirection(look);
        }
        dest.setPitch(0f);

        Location from = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 18, 0.3, 0.5, 0.3, 0.2);
        player.teleport(dest);
        player.setFallDistance(0);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 8));

        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.35f);
        player.getWorld().playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dest.clone().add(0, 1, 0), 1);
        player.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.15);

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 12.0;
        }
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            de.aetherion.items.combat.DamageNumbers.markCrit(player);
            player.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 16, 0.25, 0.25, 0.25, 0.3);
        }

        DamageSource source = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build();
        target.damage(damage, source);
    }

    private List<LivingEntity> findTargets(Player player, ItemStack item) {
        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        double range = de.aetherion.items.item.DungeonCore.aetherbladeRange(tier);
        int max = de.aetherion.items.item.DungeonCore.aetherbladeTargets(tier);
        List<LivingEntity> found = new ArrayList<>();
        for (var entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living, player)) {
                continue;
            }
            found.add(living);
        }
        found.sort(Comparator.comparingDouble(target ->
                target.getLocation().distanceSquared(player.getLocation())
        ));
        if (found.size() > max) {
            return new ArrayList<>(found.subList(0, max));
        }
        return found;
    }

    private boolean isValidTarget(LivingEntity target, Player player) {
        if (target == null || target.equals(player) || !target.isValid() || target.isDead()) {
            return false;
        }
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        return true;
    }

    private boolean isAetherblade(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) {
            return false;
        }
        String id = itemManager.getItemId(item);
        return "aetherblade".equalsIgnoreCase(id);
    }
}
