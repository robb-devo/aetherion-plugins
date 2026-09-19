package de.aetherion.bossengine.combat;

import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * SkyBlock-style boss hits: a chunk of your HP bar, not a vanilla 7-heart tickle.
 * Defense applies to the whole hit. A small floor keeps bosses dangerous.
 */
public final class BossHits {

    private static final NamespacedKey APPLYING = new NamespacedKey("bossengine", "applying_hit");
    private static final NamespacedKey TRUE_DAMAGE = AetherKeys.TRUE_DAMAGE;

    private BossHits() {
    }

    public static boolean isApplying(Player player) {
        if (player == null) {
            return false;
        }
        Byte flag = player.getPersistentDataContainer().get(APPLYING, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public static void hurt(Player player, Entity source, double power) {
        if (player == null || !player.isValid() || player.isDead() || isApplying(player)) {
            return;
        }
        double max = maxHealth(player);
        double defense = defenseOf(player);
        // High-power bosses (Pathwarden-class) must not softcap into a tickle.
        double cappedPower = Math.min(Math.max(power, 5.0), 320.0);
        double percent = 22.0 + cappedPower * 0.42;
        double mitigation = 100.0 / (100.0 + Math.max(0.0, defense) * 0.45);
        double taken = max * (percent / 100.0) * mitigation;
        taken = Math.max(max * 0.08, taken);
        taken = Math.max(6.0, taken);
        double maxFrac = power >= 200 ? 0.72 : power >= 120 ? 0.58 : 0.45;
        taken = Math.min(max * maxFrac, taken);

        player.getPersistentDataContainer().set(APPLYING, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(TRUE_DAMAGE, PersistentDataType.BYTE, (byte) 1);
        beginRawHit();
        try {
            player.damage(taken, source);
        } finally {
            endRawHit();
            player.getPersistentDataContainer().remove(APPLYING);
            player.getPersistentDataContainer().remove(TRUE_DAMAGE);
        }
        player.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                player.getLocation().add(0, 1.05, 0),
                5,
                0.25,
                0.35,
                0.25,
                0
        );
    }

    /**
     * Huge hit that cannot kill. Leaves the player at 1 HP minimum.
     */
    public static void crush(Player player, Entity source, double power) {
        if (player == null || !player.isValid() || player.isDead() || isApplying(player)) {
            return;
        }
        double max = maxHealth(player);
        double defense = defenseOf(player);
        double percent = 35.0 + Math.min(Math.max(power, 10.0), 120.0) * 0.45;
        double mitigation = 100.0 / (100.0 + Math.max(0.0, defense) * 0.4);
        double taken = max * (percent / 100.0) * mitigation;
        taken = Math.max(max * 0.25, taken);
        taken = Math.min(max * 0.92, taken);
        double leave = Math.max(1.0, player.getHealth() - taken);
        taken = Math.max(0.0, player.getHealth() - leave);
        if (taken <= 0.05) {
            player.setHealth(1.0);
            return;
        }

        player.getPersistentDataContainer().set(APPLYING, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(TRUE_DAMAGE, PersistentDataType.BYTE, (byte) 1);
        beginRawHit();
        try {
            player.damage(Math.max(0.5, taken), source);
            if (player.getHealth() < 1.0 && !player.isDead()) {
                player.setHealth(1.0);
            }
        } finally {
            endRawHit();
            player.getPersistentDataContainer().remove(APPLYING);
            player.getPersistentDataContainer().remove(TRUE_DAMAGE);
        }
        if (!player.isDead() && player.getHealth() < 1.0) {
            player.setHealth(1.0);
        }
        player.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                player.getLocation().add(0, 1.05, 0),
                12,
                0.35,
                0.45,
                0.35,
                0
        );
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(maxHealthAttribute());
        return attribute == null ? Math.max(20.0, player.getHealth()) : Math.max(1.0, attribute.getValue());
    }

    private static Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException ignored) {
            return Attribute.valueOf("MAX_HEALTH");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static double defenseOf(Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (plugin == null || !plugin.isEnabled()) {
            return 0.0;
        }
        try {
            Object items = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance")
                    .invoke(null);
            Object manager = items.getClass().getMethod("getItemManager").invoke(items);
            Class<?> statsClass = Class.forName("de.aetherion.items.manager.ActiveEquipmentStats");
            Class<?> itemManagerClass = Class.forName("de.aetherion.items.manager.ItemManager");
            Object stats = statsClass.getConstructor(itemManagerClass).newInstance(manager);
            Class<?> capability = Class.forName("de.aetherion.items.model.ItemCapability");
            Object defense = Enum.valueOf((Class) capability, "DEFENSE");
            Object value = statsClass.getMethod("getStat", Player.class, capability).invoke(stats, player, defense);
            return value instanceof Number number ? number.doubleValue() : 0.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static void beginRawHit() {
        try {
            Class.forName("de.aetherion.items.combat.IncomingHits").getMethod("beginRaw").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static void endRawHit() {
        try {
            Class.forName("de.aetherion.items.combat.IncomingHits").getMethod("endRaw").invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
