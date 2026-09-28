package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nihil's loot.
 *
 * <p><b>Worldbite, Devour.</b> Right-click: an F3+G chunk border (yellow posts, cyan rails) snaps
 * down five blocks ahead, everything inside is pulled to its center, then bitten and drained:
 * the World Eater's signature move in your hand. Players, pets and bosses are never touched.
 *
 * <p><b>Worldhide, full set: Last Seed.</b> Standing on living ground (grass, moss, podzol) with
 * all four pieces, the world grows back around you: Regeneration I and Absorption I, with a
 * little bloom of green at your feet.
 */
public class WorldEaterGearListener implements Listener {

    private static final long COOLDOWN_MS = 8000L;
    private static final double HALF = 4.0;
    private static final double BITE = 60.0;
    private static final Color CYAN = Color.fromRGB(70, 200, 255);
    private static final Color YELLOW = Color.fromRGB(255, 235, 60);
    private static final Color VOID = Color.fromRGB(150, 60, 255);
    private static final String[] SET = {"worldhide_helmet", "worldhide_chestplate", "worldhide_leggings", "worldhide_boots"};

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> lastDevour = new ConcurrentHashMap<>();

    public WorldEaterGearListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::lastSeed, 40L, 40L);
    }

    /* ================================================================== Worldbite */

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
        if (!"worldbite".equalsIgnoreCase(itemManager.getItemId(player.getInventory().getItemInMainHand()))) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastDevour.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long left = (COOLDOWN_MS - (now - last) + 999) / 1000;
            player.sendActionBar(net.kyori.adventure.text.Component.text("Devour: " + left + "s",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            return;
        }
        lastDevour.put(player.getUniqueId(), now);
        devour(player);
    }

    private void devour(Player player) {
        Vector ahead = player.getLocation().getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-4) {
            ahead = new Vector(0, 0, 1);
        }
        Location center = player.getLocation().add(ahead.normalize().multiply(5.0));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1f, 0.6f);
        world.playSound(center, Sound.ENTITY_WARDEN_SNIFF, SoundCategory.PLAYERS, 0.8f, 0.7f);
        List<LivingEntity> caught = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, HALF, 3.0, HALF)) {
            if (e instanceof LivingEntity living && !e.equals(player) && canDevour(living)) {
                caught.add(living);
            }
        }
        // The border snaps down, everything inside slides to the middle, then the bite.
        for (int k = 0; k <= 10; k++) {
            final int t = k;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                border(world, center, t);
                for (LivingEntity living : caught) {
                    if (!living.isValid()) {
                        continue;
                    }
                    Vector to = center.toVector().subtract(living.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 0.25) {
                        living.setVelocity(living.getVelocity().multiply(0.5).add(to.multiply(0.12)));
                    }
                    if (t % 2 == 0) {
                        world.spawnParticle(Particle.REVERSE_PORTAL, living.getLocation().add(0, 0.6, 0), 3, 0.2, 0.3, 0.2, 0.02);
                    }
                }
                if (t == 10) {
                    bite(player, world, center, caught);
                }
            }, k);
        }
    }

    private void border(World world, Location c, int t) {
        double y = c.getY() + 0.1 + Math.max(0, 1.6 - t * 0.16);
        double[][] corners = {{-HALF, -HALF}, {HALF, -HALF}, {HALF, HALF}, {-HALF, HALF}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            for (int s = 0; s <= 8; s++) {
                double u = s / 8.0;
                Location p = c.clone().add(a[0] + (b[0] - a[0]) * u, 0, a[1] + (b[1] - a[1]) * u);
                p.setY(y);
                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(CYAN, 1.1f), true);
            }
            if (t % 2 == 0) {
                for (int h = 0; h < 4; h++) {
                    Location post = c.clone().add(a[0], 0, a[1]);
                    post.setY(y + h * 0.6);
                    world.spawnParticle(Particle.DUST, post, 1, 0, 0, 0, 0, new Particle.DustOptions(YELLOW, 1.3f), true);
                }
            }
        }
    }

    private void bite(Player player, World world, Location center, List<LivingEntity> caught) {
        world.playSound(center, Sound.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.PLAYERS, 1.2f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.2f, 0.5f);
        world.playSound(center, Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 0.6f);
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.8, 0), 80, 1.8, 0.6, 1.8, 0.2);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 0.6, 0), 30, 1.2, 0.4, 1.2, 0, new Particle.DustOptions(VOID, 1.6f), true);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.2, 0), 40, 1.5, 0.2, 1.5, 0, Material.GRASS_BLOCK.createBlockData());
        double drained = 0;
        DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build();
        for (LivingEntity living : caught) {
            if (!living.isValid() || living.getLocation().distanceSquared(center) > (HALF + 1) * (HALF + 1)) {
                continue;
            }
            living.damage(BITE, source);
            drained += BITE;
        }
        HealthListener health = AetherionItems.getInstance().getHealthListener();
        if (health != null && drained > 0) {
            health.heal(player, Math.min(drained * 0.15, 40.0));
        }
    }

    private boolean canDevour(LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !(AetherEntities.isPet(target)
                || AetherEntities.isBoss(target)
                || AetherEntities.isBossMinion(target)
                || AetherEntities.isSetMinion(target));
    }

    /* ================================================================== Worldhide: Last Seed */

    private void lastSeed() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!wearsSet(player)) {
                continue;
            }
            Material ground = player.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType();
            if (ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK && ground != Material.PODZOL) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 0, true, false, true));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 0.1, 0), 4, 0.4, 0.05, 0.4, 0);
        }
    }

    private boolean wearsSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null || armor.length < 4) {
            return false;
        }
        // Armor contents run boots -> helmet.
        for (int i = 0; i < 4; i++) {
            if (!SET[3 - i].equalsIgnoreCase(itemManager.getItemId(armor[i]))) {
                return false;
            }
        }
        return true;
    }
}
