package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unique abilities for Test Arena sandbox gear.
 */
public final class TestGearListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final TestPrototypeAbilities prototypes;

    private final Map<UUID, Long> echoReady = new ConcurrentHashMap<>();
    private final Map<UUID, SyncHit> paritySync = new ConcurrentHashMap<>();
    private final Map<UUID, Long> charmCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> contractCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nullstepCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> plateReflectCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> metroNext = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stormCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sonicCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> judgmentCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dashCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cycloneCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> prismCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cascadeCd = new ConcurrentHashMap<>();

    public TestGearListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
        this.prototypes = new TestPrototypeAbilities(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (resolveId(item) != null) {
            event.setCancelled(true);
            return;
        }
        if (item != null && item.hasItemMeta() && item.getItemMeta().isUnbreakable()
                && itemManager.isAetherionItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        String id = resolveId(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }

        if ("echo_blade".equalsIgnoreCase(id)) {
            scheduleEcho(player, victim, event.getFinalDamage());
            return;
        }
        if ("parity_gauntlets".equalsIgnoreCase(id)) {
            applyParity(player, victim, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!"softlock_plate".equalsIgnoreCase(resolveId(player.getInventory().getChestplate()))) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        Long next = plateReflectCd.get(player.getUniqueId());
        if (next != null && tick < next) {
            return;
        }
        if (Math.random() > 0.10) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        plateReflectCd.put(player.getUniqueId(), tick + 30L);
        de.aetherion.items.combat.ScriptedHits.run(() -> attacker.damage(8.0, player));
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVanillaBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!"metronome_bow".equalsIgnoreCase(resolveId(event.getBow()))) {
            return;
        }
        event.setCancelled(true);
        event.setConsumeItem(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        Player player = event.getPlayer();
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        String id = resolveId(stack);
        if (id == null) {
            return;
        }

        if ("metronome_bow".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            fireMetronome(player);
            return;
        }

        if ("rulebreaker_charm".equalsIgnoreCase(id)) {
            event.setCancelled(true);
            long tick = Bukkit.getCurrentTick();
            Long next = charmCd.get(player.getUniqueId());
            if (next != null && tick < next) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§6Writ §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                return;
            }
            charmCd.put(player.getUniqueId(), tick + 240L);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, true, true, true));
            player.sendMessage("§6Writ of Exception §7cleared hindrances.");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.3f);
            return;
        }

        if ("broker_contract".equalsIgnoreCase(id)) {
            event.setCancelled(true);
            long tick = Bukkit.getCurrentTick();
            Long next = contractCd.get(player.getUniqueId());
            if (next != null && tick < next) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§eContract §7cooling… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                return;
            }
            contractCd.put(player.getUniqueId(), tick + 280L);
            double hp = player.getHealth();
            player.setHealth(Math.max(1.0, hp * 0.90));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1, true, true, true));
            player.sendMessage("§eBargain sealed. §7−10% HP · Strength II 8s.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.9f, 1.1f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
            return;
        }

        if ("stormcaller_maul".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            long tick = Bukkit.getCurrentTick();
            if (prototypes.isStormBusy(player.getUniqueId())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§eStorm §7still raging…"));
                return;
            }
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = stormCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§eStorm §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                stormCd.put(player.getUniqueId(), tick + 480L);
            }
            double dmg = Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
            prototypes.castStorm(player, dmg);
            return;
        }

        if ("resonance_scythe".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = sonicCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§3Resonance §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                int tier = de.aetherion.items.blueprint.BlueprintUpgrade.tier(stack);
                sonicCd.put(player.getUniqueId(), tick
                        + de.aetherion.items.blueprint.BlueprintUpgrade.resonanceCooldownTicks(tier));
            }
            double dmg = Math.max(28.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
            prototypes.castSonic(player, dmg);
            return;
        }

        if ("judgment_staff".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (prototypes.isBeamBusy(player.getUniqueId())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§aBeam §7still locking…"));
                return;
            }
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = judgmentCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§aJudgment §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                judgmentCd.put(player.getUniqueId(), tick + 500L);
            }
            prototypes.castJudgmentBeam(player, Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE)));
            return;
        }

        if ("dash_dagger".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (prototypes.isDashBusy(player.getUniqueId())) {
                return;
            }
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = dashCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§fDash §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                dashCd.put(player.getUniqueId(), tick + 80L);
            }
            prototypes.castDash(player);
            return;
        }

        if ("cyclone_rod".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (prototypes.isTornadoBusy(player.getUniqueId())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§fCyclone §7already spinning…"));
                return;
            }
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = cycloneCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§fCyclone §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                cycloneCd.put(player.getUniqueId(), tick + 400L);
            }
            prototypes.castTornado(player, Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE)));
            return;
        }

        if ("prism_staff".equalsIgnoreCase(id)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (prototypes.isPrismBusy(player.getUniqueId())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§dPrisms §7already orbiting…"));
                return;
            }
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = prismCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§dPrism §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                prismCd.put(player.getUniqueId(), tick + 450L);
            }
            prototypes.castPrismBang(player, Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE)));
            return;
        }

        if ("cascade_shortbow".equalsIgnoreCase(id) && player.isSneaking()) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (prototypes.isCascadeBusy(player.getUniqueId())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§6Cascade §7in flight…"));
                return;
            }
            long tick = Bukkit.getCurrentTick();
            if (!TestPrototypeAbilities.freeCd(player)) {
                Long next = cascadeCd.get(player.getUniqueId());
                if (next != null && tick < next) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§6Cascade §7recharging… §f" + Math.max(1, (next - tick + 19) / 20) + "s"));
                    return;
                }
                cascadeCd.put(player.getUniqueId(), tick + 200L);
            }
            prototypes.castCascadeBolt(player, Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE)));
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!"nullstep_boots".equalsIgnoreCase(resolveId(player.getInventory().getBoots()))) {
            return;
        }
        if (player.isOnGround()) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        Long next = nullstepCd.get(player.getUniqueId());
        if (next != null && tick < next) {
            return;
        }
        nullstepCd.put(player.getUniqueId(), tick + 120L);
        player.setVelocity(player.getVelocity().multiply(0.2).setY(0.08));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 45, 0, true, false, true));
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 16, 0.3, 0.2, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§3Void Tread"));
    }

    private void fireMetronome(Player player) {
        long tick = Bukkit.getCurrentTick();
        Long next = metroNext.get(player.getUniqueId());
        if (next != null && tick < next) {
            return;
        }
        metroNext.put(player.getUniqueId(), tick + 12L);

        boolean onBeat = (tick % 40L) >= 28L;
        double damage = Math.max(8.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        damage *= onBeat ? 1.40 : 0.55;
        boolean crit = onBeat && critChance > 0.0 && Math.random() * 100.0 < critChance;
        if (crit) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
        }

        Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(onBeat ? 3.2 : 2.4);
        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(onBeat || crit);
        arrow.setDamage(1.0);
        arrow.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(ItemKeys.damage(), PersistentDataType.DOUBLE, damage);
        if (crit) {
            de.aetherion.items.combat.DamageNumbers.tagCrit(arrow);
        }

        player.getWorld().playSound(player.getLocation(),
                onBeat ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS,
                0.8f, onBeat ? 1.6f : 0.6f);
        if (onBeat) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c§lON BEAT"));
            player.getWorld().spawnParticle(Particle.NOTE, player.getEyeLocation(), 4, 0.2, 0.2, 0.2, 0);
        } else {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§8off-beat"));
        }
    }

    private void scheduleEcho(Player player, LivingEntity victim, double damage) {
        long tick = Bukkit.getCurrentTick();
        Long gate = echoReady.get(player.getUniqueId());
        if (gate != null && tick < gate) {
            return;
        }
        echoReady.put(player.getUniqueId(), tick + 8L);
        Location origin = victim.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isValid() || victim.isDead() || !player.isOnline()) {
                return;
            }
            Location ghost = origin.clone().add(0, 1.0, 0);
            player.getWorld().spawnParticle(Particle.SOUL, ghost, 18, 0.35, 0.5, 0.35, 0.01);
            player.getWorld().playSound(ghost, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.5f);
            de.aetherion.items.combat.ScriptedHits.run(
                    () -> victim.damage(Math.max(2.0, damage * 0.40), player));
        }, 8L);
    }

    private void applyParity(Player player, LivingEntity victim, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        SyncHit prev = paritySync.get(player.getUniqueId());
        if (prev != null && now - prev.timeMs <= 900L && !prev.target.equals(victim.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.55);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§b§lTWIN SYNC"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
            player.getWorld().spawnParticle(Particle.END_ROD, victim.getLocation().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0.02);
            paritySync.remove(player.getUniqueId());
            return;
        }
        paritySync.put(player.getUniqueId(), new SyncHit(victim.getUniqueId(), now));
        player.sendActionBar(net.kyori.adventure.text.Component.text("§7Tag another target within §f0.9s"));
    }

    private String resolveId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String stamped = item.getItemMeta().getPersistentDataContainer().get(
                AetherKeys.namespaced("aetherion", "test_gear"),
                PersistentDataType.STRING
        );
        if (stamped != null && !stamped.isBlank()) {
            return stamped;
        }
        return itemManager.getItemId(item);
    }

    private record SyncHit(UUID target, long timeMs) {
    }
}
