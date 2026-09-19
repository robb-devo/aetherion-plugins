package de.aetherion.items.combat;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageNumbers implements Listener {

    private static final Map<UUID, Integer> critTicks = new ConcurrentHashMap<>();

    private DamageNumbers() {
    }

    public static DamageNumbers create() {
        return new DamageNumbers();
    }

    public static void markCrit(Player player) {
        if (player == null) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        critTicks.put(player.getUniqueId(), plugin.getServer().getCurrentTick());
    }

    public static void tagCrit(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(ItemKeys.critHit(), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isCrit(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte tagged = entity.getPersistentDataContainer().get(ItemKeys.critHit(), PersistentDataType.BYTE);
        return tagged != null && tagged == 1;
    }

    public static void show(Player attacker, Entity victim, double amount, boolean crit) {
        if (attacker == null || victim == null || amount <= 0.05d) {
            return;
        }
        Location at = victim.getLocation().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5d) * 0.55d,
                victim.getHeight() + 0.35d,
                (ThreadLocalRandom.current().nextDouble() - 0.5d) * 0.55d
        );
        spawn(at, amount, crit);
        if (crit) {
            playCrit(attacker, victim.getLocation().add(0, victim.getHeight() * 0.6d, 0));
        }
    }

    public static void playCrit(Player player, Location at) {
        if (player == null) {
            return;
        }
        player.playSound(player, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.12f);
        player.playSound(player, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.85f, 0.92f);
        player.playSound(player, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.72f);
        if (at != null && at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.CRIT, at, 18, 0.22, 0.28, 0.22, 0.18);
            at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 10, 0.18, 0.22, 0.18, 0.12);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event);
        if (attacker == null) {
            return;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity) || victim instanceof ArmorStand || victim instanceof Display) {
            return;
        }
        double amount = event.getFinalDamage();
        if (amount <= 0.05d) {
            return;
        }
        boolean crit = isCrit(event.getDamager()) || wasCritThisTick(attacker);
        show(attacker, victim, amount, crit);
    }

    private static boolean wasCritThisTick(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return false;
        }
        Integer tick = critTicks.get(player.getUniqueId());
        return tick != null && tick == plugin.getServer().getCurrentTick();
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static void spawn(Location at, double amount, boolean crit) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        String text = format(amount);
        NamedTextColor color = crit ? NamedTextColor.GOLD : NamedTextColor.WHITE;
        Component component = crit
                ? Component.text("✦ " + text, color).decorate(TextDecoration.BOLD)
                : Component.text(text, color);
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.text(component);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(false);
            spawned.setShadowed(true);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(0.85f, 0.85f, 0.85f),
                    new Quaternionf()
            ));
        });
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!display.isValid()) {
                task.cancel();
                return;
            }
            int age = display.getTicksLived();
            if (age >= 18) {
                display.remove();
                task.cancel();
                return;
            }
            display.teleport(display.getLocation().add(0.0, 0.045, 0.0));
        }, 1L, 1L);
    }

    private static String format(double amount) {
        if (amount >= 10.0d) {
            return String.valueOf(Math.round(amount));
        }
        return String.format(Locale.US, "%.1f", amount);
    }
}
