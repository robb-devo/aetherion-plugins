package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridged Axe: throw every 15s and steal a cut of damage dealt.
 */
public class BridgedAxeListener implements Listener {

    private static final int COOLDOWN_TICKS = 300;
    private static final double THROW_SPEED = 1.7;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public BridgedAxeListener(JavaPlugin plugin, ItemManager itemManager) {
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
        if (!isBridgedAxe(item)) {
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
                    "§6Bridged Axe §7recharging… §f" + left + "s"
            ));
            return;
        }

        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        int cooldown = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                de.aetherion.items.item.DungeonCore.bridgedCooldownTicks(tier)
        );
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
        player.setCooldown(item.getType(), cooldown);
        throwAxe(player, item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        Byte tagged = snowball.getPersistentDataContainer().get(
                ItemKeys.thrownAxe(),
                PersistentDataType.BYTE
        );
        if (tagged == null || tagged != 1) {
            return;
        }

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living
                && living.isValid()
                && !living.isDead()
                && !(living instanceof Player)
                && !(living instanceof ArmorStand)
                && snowball.getShooter() instanceof Player player) {

            Double stored = snowball.getPersistentDataContainer().get(
                    ItemKeys.damage(),
                    PersistentDataType.DOUBLE
            );
            double damage = stored == null ? 12.0 : stored;
            if (de.aetherion.items.combat.DamageNumbers.isCrit(snowball)) {
                de.aetherion.items.combat.DamageNumbers.markCrit(player);
            }
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(snowball)
                    .build();
            living.damage(damage, source);

            Double steal = snowball.getPersistentDataContainer().get(
                    ItemKeys.lifesteal(),
                    PersistentDataType.DOUBLE
            );
            if (steal != null && steal > 0) {
                heal(player, damage * steal);
            }

            living.getWorld().playSound(living.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.05f, 0.7f);
            living.getWorld().playSound(living.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.55f, 1.35f);
            living.getWorld().spawnParticle(Particle.CRIT, living.getEyeLocation(), 14, 0.25, 0.25, 0.25, 0.2);
            living.getWorld().spawnParticle(Particle.SWEEP_ATTACK, living.getLocation().add(0, 1, 0), 1);
        } else {
            snowball.getWorld().playSound(snowball.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.4f);
            snowball.getWorld().spawnParticle(Particle.CRIT, snowball.getLocation(), 8, 0.15, 0.15, 0.15, 0.05);
        }

        event.setCancelled(true);
        snowball.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        if (!isBridgedAxe(player.getInventory().getItemInMainHand())) {
            return;
        }
        double percent = lifestealOf(player.getInventory().getItemInMainHand());
        if (percent <= 0) {
            return;
        }
        heal(player, event.getFinalDamage() * percent);
    }

    private void throwAxe(Player player, ItemStack item) {
        Vector velocity = player.getEyeLocation().getDirection().multiply(
                de.aetherion.items.item.DungeonCore.bridgedThrowSpeed(de.aetherion.items.item.DungeonCore.tier(item))
        );
        Snowball snowball = player.launchProjectile(Snowball.class, velocity);
        snowball.setShooter(player);
        snowball.setGravity(true);
        snowball.setItem(item.clone());

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 12.0;
        }
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            de.aetherion.items.combat.DamageNumbers.tagCrit(snowball);
        }

        snowball.getPersistentDataContainer().set(ItemKeys.thrownAxe(), PersistentDataType.BYTE, (byte) 1);
        snowball.getPersistentDataContainer().set(ItemKeys.damage(), PersistentDataType.DOUBLE, damage);
        snowball.getPersistentDataContainer().set(ItemKeys.lifesteal(), PersistentDataType.DOUBLE, lifestealOf(item));

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!snowball.isValid() || snowball.isDead()) {
                task.cancel();
                return;
            }
            snowball.getWorld().spawnParticle(Particle.CRIT, snowball.getLocation(), 2, 0.04, 0.04, 0.04, 0.01);
            snowball.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, snowball.getLocation(), 1, 0.02, 0.02, 0.02, 0);
        }, 1L, 1L);

        player.getWorld().playSound(player.getEyeLocation(), Sound.ITEM_TRIDENT_THROW, 1.05f, 0.75f);
        player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
        player.getWorld().spawnParticle(
                Particle.CRIT,
                player.getEyeLocation().add(player.getEyeLocation().getDirection()),
                8,
                0.1,
                0.1,
                0.1,
                0.05
        );
    }

    private boolean isBridgedAxe(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.GOLDEN_AXE) {
            return false;
        }
        return "bridged_axe".equalsIgnoreCase(itemManager.getItemId(item));
    }

    private double lifestealOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Double value = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.lifesteal(),
                PersistentDataType.DOUBLE
        );
        return value == null ? 0 : Math.max(0, value) * de.aetherion.items.item.DungeonCore.skillEffect(
                de.aetherion.items.item.DungeonCore.tier(item)
        );
    }

    private void heal(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        HealthListener health = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().getHealthListener();
        if (health != null) {
            health.heal(player, amount);
        }
        player.getWorld().spawnParticle(
                Particle.HEART,
                player.getLocation().add(0, 1.25, 0),
                2,
                0.2,
                0.15,
                0.2,
                0
        );
    }
}
