package de.aetherion.items.dungeon;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.listener.HealthListener;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;

public final class DungeonGearListener implements Listener, StatProvider, Runnable {

    private final AetherionItems plugin;
    private final ItemManager items;
    private final DungeonAttuneMenu attune;
    private final DungeonIdentifyMenu identify;
    private final SetWeaponIdentifyMenu schematic;
    private int pulse;

    public DungeonGearListener(AetherionItems plugin, ItemManager items, CustomItem customItem) {
        this.plugin = plugin;
        this.items = items;
        this.attune = new DungeonAttuneMenu(items, customItem);
        this.identify = new DungeonIdentifyMenu(items, customItem);
        this.schematic = new SetWeaponIdentifyMenu(items, customItem);
        ActiveEquipmentStats.registerProvider(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(attune, plugin);
        plugin.getServer().getPluginManager().registerEvents(identify, plugin);
        plugin.getServer().getPluginManager().registerEvents(schematic, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Player player = event.getPlayer();
        if (hand == EquipmentSlot.OFF_HAND
                && (DungeonArmor.isVestige(items.getItemId(player.getInventory().getItemInMainHand()))
                || DungeonRelic.isUnidentified(items.getItemId(player.getInventory().getItemInMainHand()))
                || DungeonWeaponKind.isSchematic(items.getItemId(player.getInventory().getItemInMainHand()))
                || SetWeaponKind.isSchematic(items.getItemId(player.getInventory().getItemInMainHand())))) {
            return;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        String id = items.getItemId(item);
        if (DungeonWeaponKind.isSchematic(id)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            identify.openSchematic(player, hand, DungeonWeaponKind.schematicTier(item));
            return;
        }
        if (SetWeaponKind.isSchematic(id)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            schematic.open(player, hand);
            return;
        }
        if (DungeonRelic.isUnidentified(id)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            identify.open(player, id, hand);
            return;
        }
        if (!DungeonArmor.isVestige(id)) {
            return;
        }
        DungeonPiece piece = DungeonPiece.fromItemId(id);
        if (piece == null) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        attune.open(player, piece, hand);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshLoadout(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onDungeonKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!DungeonArmor.inDungeon(victim.getWorld())
                || victim instanceof Player
                || victim instanceof org.bukkit.entity.Villager
                || victim instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        boolean boss = victim.getMaxHealth() >= 2500.0
                || (victim.getCustomName() != null && victim.getCustomName().toLowerCase(java.util.Locale.ROOT).contains("sentinel"));
        int xp = DungeonGearProgress.killXp(victim, boss);
        var pluginSkills = plugin.getSkills();
        for (Player player : victim.getWorld().getPlayers()) {
            int grant = xp;
            if (pluginSkills != null) {
                grant = Math.max(1, (int) Math.round(xp * pluginSkills.dungeonGearXpFactor(player)));
            }
            DungeonGearProgress.grantToLoadout(player, items, grant);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        refreshLoadout(player);
        if (!DungeonArmor.isAttuned(items.getItemId(player.getInventory().getHelmet()))
                && !DungeonArmor.isAttuned(items.getItemId(player.getInventory().getChestplate()))
                && !DungeonArmor.isAttuned(items.getItemId(player.getInventory().getLeggings()))
                && !DungeonArmor.isAttuned(items.getItemId(player.getInventory().getBoots()))) {
            return;
        }
        HealthListener health = plugin.getHealthListener();
        if (health != null) {
            health.refreshHealth(player);
        }
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 0.0;
        }
        boolean dungeon = DungeonArmor.inDungeon(player);
        double gear = dungeon ? DungeonGearProgress.wornLevelMultiplier(player, items) : 1.0d;
        double total = 0.0;
        if (DungeonArmor.fullSet(player, items, DungeonCalling.TANK)) {
            if (capability == ItemCapability.DEFENSE) {
                total += dungeon ? 32.0 * gear : 12.0;
            }
        }
        if (DungeonArmor.fullSet(player, items, DungeonCalling.ASSASSIN)) {
            if (capability == ItemCapability.DAMAGE) {
                total += dungeon ? 14.0 * gear : 5.0;
            }
            if (capability == ItemCapability.CRIT_CHANCE) {
                total += dungeon ? 10.0 * gear : 4.0;
            }
            if (capability == ItemCapability.CRIT_DAMAGE && dungeon) {
                total += 18.0 * gear;
            }
        }
        if (DungeonArmor.fullSet(player, items, DungeonCalling.SOLDIER)) {
            if (capability == ItemCapability.ATTACK_SPREAD) {
                total += dungeon ? 11.0 * gear : 4.0;
            }
            if (capability == ItemCapability.DAMAGE && dungeon) {
                total += 8.0 * gear;
            }
        }
        if (DungeonArmor.fullSet(player, items, DungeonCalling.SHAMAN)) {
            if (capability == ItemCapability.ATTACK_SPREAD) {
                total += 3.0;
            }
        }
        return total;
    }

    @Override
    public void run() {
        pulse++;
        boolean shamanPulse = pulse % 2 == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            heal(player);
            if (shamanPulse) {
                spirit(player);
            }
        }
    }

    private void heal(Player player) {
        int pieces = DungeonArmor.wornPieces(player, items, DungeonCalling.HEALER);
        if (pieces <= 0 || player.isDead()) {
            return;
        }
        double amount = 1.25d * pieces;
        if (DungeonArmor.inDungeon(player)) {
            amount *= 1.55d * DungeonGearProgress.wornLevelMultiplier(player, items);
        }
        double radius = pieces >= 4 ? 7.0 : pieces >= 2 ? 4.0 : 0.0;
        restore(player, amount);
        if (radius <= 0) {
            return;
        }
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player nearby && !nearby.isDead()) {
                restore(nearby, amount * 0.75d);
            }
        }
    }

    private void spirit(Player player) {
        int pieces = DungeonArmor.wornPieces(player, items, DungeonCalling.SHAMAN);
        if (pieces <= 0 || player.isDead()) {
            return;
        }
        double shield = Math.min(6.0, 1.0 * pieces);
        double radius = pieces >= 4 ? 6.0 : 0.0;
        absorb(player, shield);
        if (radius <= 0) {
            return;
        }
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player nearby && !nearby.isDead()) {
                absorb(nearby, shield * 0.6d);
            }
        }
    }

    private void refreshLoadout(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var inventory = player.getInventory();
        patchCoreAndLevel(player, inventory.getItemInMainHand());
        patchCoreAndLevel(player, inventory.getItemInOffHand());
        patchCoreAndLevel(player, inventory.getHelmet());
        patchCoreAndLevel(player, inventory.getChestplate());
        patchCoreAndLevel(player, inventory.getLeggings());
        patchCoreAndLevel(player, inventory.getBoots());
    }

    private void patchCoreAndLevel(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        String id = items.getItemId(item);
        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        if (tier > 0 && de.aetherion.items.item.DungeonCore.canInfuse(id)) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                de.aetherion.items.item.DungeonCore.applyInfusionRarity(meta, tier);
                item.setItemMeta(meta);
            }
        }
        // Full lore rebuild so dungeon preview suffixes stay correct after world changes.
        items.refreshItemLore(item, de.aetherion.items.item.GearTooltip.expanding(player));
        DungeonArmor.refreshAssassinLore(item, player, items);
        de.aetherion.items.item.GearTooltip.finish(item, items, de.aetherion.items.item.GearTooltip.expanding(player));
    }

    private static void restore(Player player, double amount) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        double max = player.getMaxHealth();
        double current = player.getHealth();
        if (current >= max || amount <= 0) {
            return;
        }
        player.setHealth(Math.min(max, current + amount));
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0),
                5,
                0.25,
                0.35,
                0.25,
                0.01
        );
    }

    private static void absorb(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        player.setAbsorptionAmount(Math.min(8.0, player.getAbsorptionAmount() + amount));
        player.getWorld().spawnParticle(
                Particle.ENCHANT,
                player.getLocation().add(0, 1, 0),
                8,
                0.3,
                0.4,
                0.3,
                0.4
        );
    }
}
