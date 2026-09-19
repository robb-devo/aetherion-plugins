package de.aetherion.items.mining;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.blueprint.BlueprintKind;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tiny Ore Troll — ~1% from any Shabby Mine ore (T1) after Surveyor speak + tutorial.
 * Soft ores (coal / iron / copper) can still spawn them outside the mine.
 * Crawls out of the vein toward the player. Pickaxe-only; ~50% blueprint drop.
 */
public final class OreTrollListener implements Listener {

    public static final String DISPLAY = "Tiny Ore Troll";
    private static final double MAX_HP = 25.0;
    /** Soft slap — not a real fight threat. */
    private static final double TROLL_ATTACK = 2.0d;
    private static final double TROLL_HIT_CAP = 2.5d;
    private static final double SPAWN_CHANCE = 0.01d;
    private static final double BLUEPRINT_CHANCE = 0.50d;
    private static final double KEEP_RANGE = 48.0d;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final NamespacedKey tag;
    /** Live trolls — kept even if their target dies / walks off briefly. */
    private final Map<UUID, UUID> liveTrolls = new ConcurrentHashMap<>();

    public OreTrollListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.tag = ItemKeys.key("ore_troll");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickKeepAlive, 40L, 40L);
    }

    public static boolean canSpawnFrom(Material material, Location at) {
        if (isTrollOre(material)) {
            return true;
        }
        return HarvestRules.ore(material) && inShabbyMine(at);
    }

    public static boolean isTrollOre(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE -> true;
            default -> false;
        };
    }

    private static boolean inShabbyMine(Location at) {
        if (at == null) {
            return false;
        }
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items != null
                    && items.getAreas() != null
                    && items.getAreas().isType(at, de.aetherion.items.world.AreaType.SHABBY_MINE);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public void maybeSpawn(Player player, Location at) {
        if (player == null || at == null || at.getWorld() == null) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!tutorialDone(player)) {
            return;
        }
        if (!BlueprintHunt.unlocked(player)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= SPAWN_CHANCE) {
            return;
        }
        Location ore = at.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location spot = crawlOutSpot(ore, player);
            LivingEntity troll = spawn(spot, player);
            if (troll == null || !troll.isValid()) {
                player.sendMessage("§cOre Troll failed to crawl out — tell DEV.");
            }
        });
    }

    private static boolean tutorialDone(Player player) {
        try {
            Object quests = Class.forName("de.aetherion.quests.AetherionQuests")
                    .getMethod("getInstance")
                    .invoke(null);
            if (quests == null) {
                return false;
            }
            Object qm = quests.getClass().getMethod("getQuestManager").invoke(quests);
            Object done = Class.forName("de.aetherion.quests.util.QuestStoryGate")
                    .getMethod("tutorialDone", Player.class, Class.forName("de.aetherion.quests.manager.QuestManager"))
                    .invoke(null, player, qm);
            return done instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public LivingEntity spawn(Location at, Player aggro) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        World world = at.getWorld();
        Location spot = at.clone();
        if (aggro != null) {
            Vector look = aggro.getLocation().toVector().subtract(spot.toVector());
            if (look.lengthSquared() > 0.001) {
                spot.setDirection(look);
            }
        }
        Zombie troll = world.spawn(spot, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, zombie -> {
            zombie.setBaby(true);
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            zombie.setSilent(false);
            zombie.customName(net.kyori.adventure.text.Component.text(
                    DISPLAY,
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            zombie.setCustomNameVisible(true);
            zombie.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            if (zombie.getEquipment() != null) {
                zombie.getEquipment().clear();
            }
            setAttr(zombie, Attribute.GENERIC_MAX_HEALTH, MAX_HP);
            setAttr(zombie, Attribute.GENERIC_ATTACK_DAMAGE, TROLL_ATTACK);
            zombie.setHealth(MAX_HP);
        });
        if (troll == null || !troll.isValid()) {
            return null;
        }
        UUID owner = aggro != null ? aggro.getUniqueId() : null;
        liveTrolls.put(troll.getUniqueId(), owner);
        if (aggro != null) {
            troll.setTarget(aggro);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (troll.isValid() && !troll.isDead()) {
                de.aetherion.items.world.WildlifeLooks.attachOreTroll(troll);
            }
        });
        world.playSound(spot, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.55f, 1.6f);
        world.playSound(spot, Sound.BLOCK_STONE_BREAK, 0.45f, 0.7f);
        if (aggro != null) {
            aggro.sendMessage("§6✦ §eTiny Ore Troll §7crawls out of the vein!");
            aggro.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Pickaxe only · rarity = damage",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
        }
        return troll;
    }

    private static Location crawlOutSpot(Location ore, Player player) {
        Location vein = ore.clone().add(0.5, 0.0, 0.5);
        Vector toward = player.getLocation().toVector().subtract(vein.toVector());
        toward.setY(0);
        if (toward.lengthSquared() < 0.0001) {
            toward = player.getLocation().getDirection();
            toward.setY(0);
        }
        if (toward.lengthSquared() < 0.0001) {
            toward = new Vector(1, 0, 0);
        }
        toward.normalize();

        for (double dist = 0.85; dist <= 2.4; dist += 0.35) {
            Location candidate = vein.clone().add(toward.clone().multiply(dist));
            candidate.setY(ore.getY());
            if (isFreeStanding(candidate)) {
                return candidate;
            }
            Location up = candidate.clone().add(0, 1, 0);
            if (isFreeStanding(up)) {
                return up;
            }
        }

        int[][] offsets = {
                {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}
        };
        for (int[] o : offsets) {
            Location candidate = ore.clone().add(o[0] + 0.5, o[1], o[2] + 0.5);
            if (isFreeStanding(candidate)) {
                return candidate;
            }
        }
        return player.getLocation().clone();
    }

    private static boolean isFreeStanding(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        World world = at.getWorld();
        Block feet = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        Block head = world.getBlockAt(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ());
        return feet.isPassable() && head.isPassable();
    }

    public boolean isOreTroll(LivingEntity entity) {
        return isOreTroll((Entity) entity);
    }

    public static boolean isOreTroll(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return living.getPersistentDataContainer().has(
                ItemKeys.key("ore_troll"),
                PersistentDataType.BYTE
        );
    }

    /**
     * Player → troll: flat pickaxe damage via vanilla apply (snappy hits).
     * Non-pickaxe: cancel + chat.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerHitTroll(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !isOreTroll(target)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null) {
            event.setCancelled(true);
            return;
        }
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isPickaxe(hand)) {
            event.setCancelled(true);
            attacker.sendMessage("§c✦ §7Ore Trolls only take damage from a §epickaxe§7.");
            attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Needs a pickaxe",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            return;
        }
        int dmg = miningBossDamage(hand);
        if (dmg <= 0) {
            event.setCancelled(true);
            return;
        }
        // Let vanilla apply — no cancel/setHealth (that felt delayed).
        event.setCancelled(false);
        event.setDamage(dmg);
        target.setNoDamageTicks(0);
    }

    /** Troll → player: soft tap only. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrollHitPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!isOreTroll(damager) || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setDamage(Math.min(event.getDamage(), TROLL_HIT_CAP));
    }

    /** Don't vanish when your target dies — retarget or hold the spot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        for (Map.Entry<UUID, UUID> entry : liveTrolls.entrySet()) {
            Entity raw = Bukkit.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                continue;
            }
            if (!isOreTroll(mob)) {
                continue;
            }
            LivingEntity current = mob.getTarget();
            if (current != null && current.getUniqueId().equals(dead.getUniqueId())) {
                mob.setTarget(null);
            }
            if (entry.getValue() != null && entry.getValue().equals(dead.getUniqueId())) {
                Player next = nearestPlayer(mob.getLocation(), dead.getUniqueId());
                if (next != null) {
                    mob.setTarget(next);
                    liveTrolls.put(mob.getUniqueId(), next.getUniqueId());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!isOreTroll(event.getEntity())) {
            return;
        }
        liveTrolls.remove(event.getEntity().getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
        if (ThreadLocalRandom.current().nextDouble() >= BLUEPRINT_CHANCE) {
            killer.sendMessage("§6Tiny Ore Troll §7crumbles — no schematic this time.");
            return;
        }
        BlueprintKind kind = BlueprintKind.random();
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCustomItem() == null) {
            return;
        }
        ItemStack blueprint = kind.createBlueprint(items.getCustomItem());
        InventoryDrops.setDropAt(event.getEntity().getLocation());
        try {
            InventoryDrops.give(killer, blueprint);
        } finally {
            InventoryDrops.clearDropAt();
        }
        if (items.blueprintUnlocks() != null) {
            items.blueprintUnlocks().markCollected(killer, kind.id());
        }
        killer.sendMessage("§b✦ §fBlueprint: " + kind.display() + " §7— take it to the §eSurveyor§7.");
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75f, 1.35f);
    }

    private void tickKeepAlive() {
        Iterator<Map.Entry<UUID, UUID>> it = liveTrolls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            Entity raw = Bukkit.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isValid() || mob.isDead() || !isOreTroll(mob)) {
                it.remove();
                continue;
            }
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);
            LivingEntity target = mob.getTarget();
            if (target instanceof Player player && player.isOnline() && !player.isDead()) {
                continue;
            }
            Player next = nearestPlayer(mob.getLocation(), null);
            if (next != null) {
                mob.setTarget(next);
                entry.setValue(next.getUniqueId());
            }
        }
    }

    private static Player nearestPlayer(Location at, UUID exclude) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = KEEP_RANGE * KEEP_RANGE;
        for (Player player : at.getWorld().getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (exclude != null && player.getUniqueId().equals(exclude)) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(at);
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    public int miningBossDamage(ItemStack tool) {
        if (!isPickaxe(tool)) {
            return 0;
        }
        Rarity rarity = itemManager.getRarity(tool);
        if (rarity == null) {
            rarity = Rarity.COMMON;
        }
        int tier = rarity.ordinal() + 1;
        String id = itemManager.getItemId(tool);
        if (id != null && "vein_siphon".equalsIgnoreCase(id)) {
            return 10 + tier;
        }
        return tier;
    }

    private static boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material type = stack.getType();
        String name = type.name();
        return name.endsWith("_PICKAXE") || org.bukkit.Tag.ITEMS_PICKAXES.isTagged(type);
    }

    private static void setAttr(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) {
            try {
                String modern = attribute.name().replace("GENERIC_", "");
                attr = entity.getAttribute(Attribute.valueOf(modern));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (attr != null) {
            attr.setBaseValue(value);
        }
    }
}
