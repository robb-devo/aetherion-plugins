package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.CompactBonusSource;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.WorldLight;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ProgressionEffects implements Listener, StatProvider {

    private static final Set<UUID> reflecting = new HashSet<>();

    private static CompactBonusSource compactBonusSource;

    private final ItemManager items;
    private final HealthListener health;

    public ProgressionEffects(AetherionItems plugin, ItemManager items, HealthListener health) {
        this.items = items;
        this.health = health;
        ActiveEquipmentStats.registerProvider(this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRings, 20L, 20L);
    }

    public static void registerCompactBonus(CompactBonusSource source) {
        compactBonusSource = source;
    }

    public static double cooldownFactor(Player player, ItemManager items) {
        double factor = wearing(player, items, "redstone_infused_boots") ? 0.85d : 1.0d;
        factor *= de.aetherion.items.dungeon.DungeonArmor.assassinCooldownFactor(player, items);
        SkillService skills = skills();
        if (skills != null && skills.hasFlag(player, AetherSkill.Flag.QUICK_HANDS)) {
            factor *= skills.quickHandsFactor(player);
        }
        return factor;
    }

    public static int cooldownTicks(Player player, ItemManager items, int base) {
        return Math.max(1, (int) Math.round(base * cooldownFactor(player, items)));
    }

    public static long cooldownMs(Player player, ItemManager items, long base) {
        return Math.max(1L, Math.round(base * cooldownFactor(player, items)));
    }

    public static ItemStack maybeCompress(Player player, ItemManager items, ItemStack drop) {
        if (player == null || drop == null || drop.getType().isAir()) {
            return null;
        }
        CompressedResource resource = CompressedResource.fromDrop(drop.getType());
        if (resource == null) {
            return null;
        }
        String id = items.getItemId(player.getInventory().getItemInMainHand());
        double chance = compactChance(id, resource);
        SkillService skills = skills();
        if (skills != null) {
            chance += skills.compactBonus(
                    player,
                    resource.isMiningDrop(),
                    resource.isForagingDrop(),
                    resource.isFarmingDrop(),
                    resource.isFishingDrop()
            );
        }
        if (compactBonusSource != null) {
            chance += compactBonusSource.extraCompactChance(player, drop.getType());
        }
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        double upgrade = skills == null ? 0.0d : skills.compactedUpgradeChance(
                player,
                resource.isMiningDrop(),
                resource.isForagingDrop(),
                resource.isFarmingDrop(),
                resource.isFishingDrop()
        );
        if (compactBonusSource != null) {
            upgrade = Math.max(
                    upgrade,
                    compactBonusSource.extraCompactedUpgradeChance(player, drop.getType())
            );
        }
        if ("voided_455".equals(id)) {
            upgrade = Math.max(upgrade, 0.28);
        }
        if (upgrade > 0.0d && ThreadLocalRandom.current().nextDouble() < upgrade) {
            return resource.compacted();
        }
        return resource.compressed();
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (capability != ItemCapability.SPEED || player == null) {
            return 0.0;
        }
        if (!holdingOrWorn(player, "compressed_coal_ring")) {
            return 0.0;
        }
        return WorldLight.isDark(player) ? 10.0 : 0.0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (de.aetherion.items.combat.ScriptedHits.isActive()) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker != null && event.getEntity() instanceof LivingEntity victim && !(victim instanceof Player)) {
            String weapon = items.getItemId(attacker.getInventory().getItemInMainHand());
            SkillService skills = skills();
            if ("compacted_midas_dagger".equals(weapon)) {
                var coins = AetherionItems.getInstance().getCoins();
                long cost = skills == null ? 10L : skills.midasCost(attacker);
                if (coins == null || !coins.take(attacker, cost)) {
                    event.setDamage(event.getDamage() * 0.65);
                    attacker.sendMessage("§7The dagger wants a receipt. §c" + cost + " coins.");
                }
            }
            double boss = 1.0d;
            if ("compacted_diamond_sword".equals(weapon) && isBoss(victim)) {
                boss *= 1.25d;
            }
            if (skills != null && isBoss(victim)) {
                boss *= skills.bossBonus(attacker);
            }
            if (boss != 1.0d) {
                event.setDamage(event.getDamage() * boss);
            }
            if ("compacted_emerald_scythe".equals(weapon) && ThreadLocalRandom.current().nextDouble() < 0.05) {
                explodeCoins(attacker, victim);
            }
            // Life Absorb runs at MONITOR on final damage — see onHitLifeAbsorb.
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (holdingOrWorn(victim, "lapis_pendant") && event.getDamage() > 0) {
            double converted = event.getDamage() * 0.10;
            event.setDamage(Math.max(0.0, event.getDamage() - converted));
            victim.setAbsorptionAmount(Math.min(8.0, victim.getAbsorptionAmount() + converted));
        }
        double reflect = holdingOrWorn(victim, "compacted_diamond_chestplate") ? 0.10d : 0.0d;
        SkillService skills = skills();
        if (skills != null) {
            reflect += skills.reflectPercent(victim);
        }
        if (reflect <= 0) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile) {
            return;
        }
        if (!(source instanceof LivingEntity living) || living.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!reflecting.add(victim.getUniqueId())) {
            return;
        }
        try {
            living.damage(event.getDamage() * reflect, victim);
        } finally {
            reflecting.remove(victim.getUniqueId());
        }
    }

    /**
     * After Aetherion damage is applied (HIGHEST). Heal from final dealt damage,
     * not the pre-override vanilla hit (which made Life Absorb feel dead).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitLifeAbsorb(EntityDamageByEntityEvent event) {
        if (de.aetherion.items.combat.ScriptedHits.isActive()) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || !(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
            return;
        }
        SkillService skills = skills();
        if (skills == null || health == null) {
            return;
        }
        double absorb = skills.lifeAbsorbFactor(attacker);
        if (absorb <= 0) {
            return;
        }
        double dealt = event.getFinalDamage();
        if (dealt <= 0) {
            return;
        }
        health.heal(attacker, Math.max(0.5, dealt * absorb));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            org.bukkit.event.entity.EntityDamageEvent cause = entity.getLastDamageCause();
            if (cause != null) {
                Entity causing = cause.getDamageSource().getCausingEntity();
                if (causing instanceof Player player) {
                    killer = player;
                }
            }
        }
        if (killer == null && entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage) {
            killer = attacker(damage);
        }
        if (killer == null || entity instanceof Player) {
            return;
        }
        SkillService skills = skills();
        if (skills != null) {
            skills.grantFromKill(killer, entity, isBoss(entity));
        }
        boolean goldSword = "compressed_gold_sword".equals(items.getItemId(killer.getInventory().getItemInMainHand()));
        boolean bloodTax = skills != null && skills.hasFlag(killer, AetherSkill.Flag.BLOOD_TAX);
        if (!goldSword && !bloodTax) {
            return;
        }
        var coins = AetherionItems.getInstance().getCoins();
        if (coins == null) {
            return;
        }
        long pay = 0L;
        if (goldSword) {
            pay += Math.max(2L, Math.round(entity.getMaxHealth() * 0.24));
        }
        if (bloodTax) {
            pay += Math.max(1L, Math.round(entity.getMaxHealth() * skills.bloodTaxFactor(killer)));
        }
        if (skills != null) {
            pay = Math.round(pay * skills.coinMultiplier(killer));
        }
        if (pay <= 0L) {
            return;
        }
        coins.add(killer, pay);
        killer.sendMessage("§6+" + pay + " coins");
    }

    private void explodeCoins(Player player, LivingEntity center) {
        var coins = AetherionItems.getInstance().getCoins();
        if (coins != null) {
            long pay = 25L;
            SkillService skills = skills();
            if (skills != null) {
                pay = Math.round(pay * skills.coinMultiplier(player));
            }
            coins.add(player, pay);
        }
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.getLocation().add(0, 1, 0), 18, 0.6, 0.4, 0.6, 0.02);
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        for (Entity nearby : center.getNearbyEntities(4, 3, 4)) {
            if (nearby instanceof LivingEntity living && !living.getUniqueId().equals(player.getUniqueId())) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, true, true));
            }
        }
        player.sendMessage("§aThe coins applaud.");
    }

    private void tickRings() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (holdingOrWorn(player, "compressed_coal_ring") && health != null) {
                health.refreshHealth(player);
            }
        }
    }

    private boolean holdingOrWorn(Player player, String itemId) {
        if (player == null || itemId == null) {
            return false;
        }
        return itemId.equals(items.getItemId(player.getInventory().getItemInMainHand()))
                || itemId.equals(items.getItemId(player.getInventory().getItemInOffHand()))
                || itemId.equals(items.getItemId(player.getInventory().getHelmet()))
                || itemId.equals(items.getItemId(player.getInventory().getChestplate()))
                || itemId.equals(items.getItemId(player.getInventory().getLeggings()))
                || itemId.equals(items.getItemId(player.getInventory().getBoots()));
    }

    private static boolean wearing(Player player, ItemManager items, String itemId) {
        if (player == null) {
            return false;
        }
        return itemId.equals(items.getItemId(player.getInventory().getBoots()))
                || itemId.equals(items.getItemId(player.getInventory().getChestplate()))
                || itemId.equals(items.getItemId(player.getInventory().getHelmet()))
                || itemId.equals(items.getItemId(player.getInventory().getLeggings()));
    }

    private static SkillService skills() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getSkills();
    }

    private static boolean isBoss(Entity entity) {
        return AetherEntities.isBoss(entity) || AetherEntities.isSetMinion(entity);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * Tool compact chance — domain-scoped and kept gentle (~1–4%).
     * Mythic voided pick remains a guaranteed mining compact.
     */
    private static double compactChance(String itemId, CompressedResource resource) {
        if (itemId == null || resource == null) {
            return 0;
        }
        return switch (itemId) {
            case "compressed_stone_pickaxe" -> resource == CompressedResource.COBBLESTONE ? 0.01 : 0;
            case "compacted_cobble_hammer" -> resource == CompressedResource.COBBLESTONE ? 0.035 : 0;
            case "compacted_iron_pickaxe" -> resource.isMiningDrop() ? 0.02 : 0;
            case "compacted_diamond_pickaxe" -> resource.isMiningDrop() ? 0.04 : 0;
            case "voided_455" -> resource.isMiningDrop() ? 1.0 : 0;
            case "compacted_timber_axe" -> resource.isForagingDrop() ? 0.025 : 0;
            default -> 0;
        };
    }
}
