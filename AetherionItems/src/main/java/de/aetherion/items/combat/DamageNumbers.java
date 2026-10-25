package de.aetherion.items.combat;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
    /** Per attacker: how the combat kit read this tick's swing on one victim (light, heavy, finisher...). */
    private static final Map<UUID, Marked> styles = new ConcurrentHashMap<>();

    /** Number look. NORMAL is the old look; the rest come from the combat kit's swing read. */
    public enum Style {
        NORMAL(TextColor.color(0xFFFFFF), "", 0.85f, false),
        LIGHT(TextColor.color(0x9A9A9A), "", 0.62f, false),
        HEAVY(TextColor.color(0xFFF1D0), "", 1.0f, true),
        AERIAL(TextColor.color(0x9FE3FF), "▼ ", 1.05f, true),
        FINISHER(TextColor.color(0xFF9A3C), "✸ ", 1.25f, true),
        RIPOSTE(TextColor.color(0xE58BFF), "» ", 1.2f, true);

        final TextColor color;
        final String prefix;
        final float scale;
        final boolean bold;

        Style(TextColor color, String prefix, float scale, boolean bold) {
            this.color = color;
            this.prefix = prefix;
            this.scale = scale;
            this.bold = bold;
        }
    }

    private record Marked(int tick, UUID victim, Style style) {
    }

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

    /** Tags the number this attacker's hit on {@code victim} will show this tick. */
    public static void markStyle(Player attacker, Entity victim, Style style) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (attacker == null || victim == null || style == null || plugin == null) {
            return;
        }
        styles.put(attacker.getUniqueId(), new Marked(plugin.getServer().getCurrentTick(), victim.getUniqueId(), style));
    }

    /** Short floating word over an entity (PARRY, STAGGER), same motion as a number. */
    public static void word(Entity over, String text, TextColor color, float scale) {
        if (over == null || text == null || text.isBlank()) {
            return;
        }
        Location at = over.getLocation().add(0, over.getHeight() + 0.55, 0);
        floatLabel(at, Component.text(text, color).decorate(TextDecoration.BOLD), scale);
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
                Math.min(victim.getHeight(), 3.5d) + 0.35d, // a giant boss: numbers at eye level, not 12 blocks up
                (ThreadLocalRandom.current().nextDouble() - 0.5d) * 0.55d
        );
        spawn(at, amount, crit, Style.NORMAL, 0.0d);
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
            de.aetherion.core.api.Vfx.puff(at, Particle.CRIT, 18, Math.max(0.22, 0.22), null);
            de.aetherion.core.api.Vfx.puff(at, Particle.ENCHANTED_HIT, 10, Math.max(0.18, 0.18), null);
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
        double amount = bossHit(victim, event.getFinalDamage());
        if (amount <= 0.05d) {
            return;
        }
        boolean crit = isCrit(event.getDamager()) || wasCritThisTick(attacker);
        Style style = styleFor(attacker, victim);
        if (style == Style.NORMAL) {
            show(attacker, victim, amount, crit);
            return;
        }
        Location at = victim.getLocation().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5d) * 0.55d,
                Math.min(victim.getHeight(), 3.5d) + 0.35d, // a giant boss: numbers at eye level, not 12 blocks up
                (ThreadLocalRandom.current().nextDouble() - 0.5d) * 0.55d
        );
        spawn(at, amount, crit, style, amount / maxHealth((LivingEntity) victim));
        if (crit) {
            playCrit(attacker, victim.getLocation().add(0, victim.getHeight() * 0.6d, 0));
        }
    }

    /**
     * BossEngine keeps its own HP pool and sets the vanilla hit to 0.01; it leaves what the hit really did in
     * metadata for this tick.
     */
    private static double bossHit(Entity victim, double fallback) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return fallback;
        }
        for (org.bukkit.metadata.MetadataValue value : victim.getMetadata("aetherion_boss_hit")) {
            if (value.value() instanceof double[] hit && hit.length == 2
                    && (int) hit[0] == plugin.getServer().getCurrentTick()) {
                return hit[1];
            }
        }
        return fallback;
    }

    private static Style styleFor(Player attacker, Entity victim) {
        Marked marked = styles.get(attacker.getUniqueId());
        AetherionItems plugin = AetherionItems.getInstance();
        if (marked == null || plugin == null || marked.tick() != plugin.getServer().getCurrentTick()
                || !marked.victim().equals(victim.getUniqueId())) {
            return Style.NORMAL;
        }
        return marked.style();
    }

    private static double maxHealth(LivingEntity victim) {
        AttributeInstance max = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return max == null ? 20.0d : Math.max(1.0d, max.getValue());
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

    private static void spawn(Location at, double amount, boolean crit, Style style, double share) {
        String text = format(amount);
        TextColor color = crit ? NamedTextColor.GOLD : style.color;
        String prefix = crit ? "✦ " : style.prefix;
        Component component = Component.text(prefix + text, color);
        if (crit || style.bold) {
            component = component.decorate(TextDecoration.BOLD);
        }
        // Chunks of the victim's health read bigger: +0..50% for a 0..50% bite.
        float scale = style.scale * (float) (1.0d + Math.max(0.0d, Math.min(0.5d, share)));
        floatLabel(at, component, scale);
    }

    /**
     * Spawns one floating label and animates it client-side: a short pop (scale), then a rise.
     * Two transformation updates and a removal instead of a teleport every tick.
     */
    private static void floatLabel(Location at, Component component, float scale) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        float pop = scale * 1.35f;
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
                    new Vector3f(pop, pop, pop),
                    new Quaternionf()
            ));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(3);
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.08f, 0.0f),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) {
                return;
            }
            float end = scale * 0.8f;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(13);
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.72f, 0.0f),
                    new Quaternionf(),
                    new Vector3f(end, end, end),
                    new Quaternionf()
            ));
        }, 4L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
        }, 18L);
    }

    private static String format(double amount) {
        if (amount >= 10.0d) {
            return String.valueOf(Math.round(amount));
        }
        return String.format(Locale.US, "%.1f", amount);
    }
    public static boolean critThisTick(Player player) {
        return player != null && wasCritThisTick(player);
    }
}
