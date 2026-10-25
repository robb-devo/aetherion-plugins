package de.aetherion.bossengine.combat;

import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * What a boss tells AetherionItems' combat system, through entity metadata (the two plugins do not depend on
 * each other):
 * <ul>
 *   <li>{@link #OPENING}: tick until which the boss is open after a big move. Charged swings deal the combat
 *       system's opening bonus while it lasts.</li>
 *   <li>{@link #HIT}: the damage a player hit really did to the boss's own HP pool. The vanilla event is set to
 *       0.01, so the damage numbers read this instead.</li>
 * </ul>
 */
public final class Openings {

    public static final String OPENING = "aetherion_opening";
    public static final String HIT = "aetherion_boss_hit";

    private Openings() {
    }

    /** Opens the boss for {@code ticks} and tells everyone fighting it. */
    public static void open(LivingEntity boss, int ticks) {
        if (boss == null || !boss.isValid() || ticks <= 0) {
            return;
        }
        boss.setMetadata(OPENING, new FixedMetadataValue(plugin(), (long) Bukkit.getCurrentTick() + ticks));
        boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.6f);
        for (Player player : boss.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= 30 * 30) {
                player.sendActionBar(TextUtil.component("&e&lOpening &7· charged hits deal extra damage"));
            }
        }
    }

    public static boolean isOpen(Entity boss) {
        for (MetadataValue value : boss.getMetadata(OPENING)) {
            if (value.asLong() > Bukkit.getCurrentTick()) {
                return true;
            }
        }
        return false;
    }

    /** Records what this tick's player hit did to the boss's pool. */
    public static void markHit(Entity damaged, double amount) {
        damaged.setMetadata(HIT, new FixedMetadataValue(plugin(), new double[]{Bukkit.getCurrentTick(), amount}));
    }

    private static JavaPlugin plugin() {
        return JavaPlugin.getProvidingPlugin(Openings.class);
    }
}
