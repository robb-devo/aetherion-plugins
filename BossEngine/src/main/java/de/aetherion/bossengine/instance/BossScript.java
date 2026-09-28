package de.aetherion.bossengine.instance;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A fully scripted encounter body. When a template has a script registered in {@link BossScripts},
 * {@link BossInstance} hands the whole fight to it: the engine keeps combat HP, phase gates, the damage
 * tracker and loot, and leaves the body alone (no leash, no unstick, no AI polish, no YAML transition
 * visuals, no timer skills).
 *
 * <p>Phase changes still happen in the engine: when HP crosses a gate the new phase is applied at once
 * (no YAML transition) and the script sees it through {@link BossInstance#getCurrentPhase()}. The script
 * is expected to stage its own interlude and block damage meanwhile via {@link #blocksDamage()}.
 */
public interface BossScript {

    /** A body was bound (spawn) or re-bound (chunk reload). */
    void onBind();

    /**
     * Called every engine tick while the encounter is active, including while dying.
     *
     * @return true once the death cinematic finished and loot should be paid
     */
    boolean tick();

    /** HP reached zero. @return true if the script plays its own death (it usually does) */
    boolean beginDeath();

    boolean isDying();

    /** Untouchable right now (cutscene, interlude, death). */
    boolean blocksDamage();

    /** Despawn, abort, plugin disable: remove everything the script spawned. Must be idempotent. */
    void abort();

    /**
     * Lets the script reshape a player's hit before it reaches the HP pool.
     *
     * @return the damage to apply, or a negative value to cancel the hit
     */
    default double incoming(Player player, double amount) {
        return amount;
    }

    /** The script shows its own boss bars; the engine HUD stays away. */
    default boolean ownsBossBar() {
        return false;
    }

    /**
     * The script pays the item bundles itself (a loot chest). XP and the recap are already granted.
     *
     * @return false to fall back to the engine's direct payout
     */
    default boolean handLoot(Map<UUID, List<ItemStack>> bundles) {
        return false;
    }

    /** False when the encounter wants no loot at all for this body (an act that is not the finale). */
    default boolean paysLoot() {
        return true;
    }
}
