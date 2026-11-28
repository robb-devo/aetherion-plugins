package de.aetherion.guilds.world;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

import java.util.function.Predicate;

/**
 * On the islands (and the Isle Dock) the game's own pop-ups stay away: no "Advancement Made!" and no "New Recipes
 * Unlocked!" in the corner. The Build Tool takes the shape of the part it holds (a lectern, a barrel, an iron
 * pickaxe), and a factory hands out stone and ore all day: without this, every one of those is a vanilla toast
 * over the island's own cards. Nothing is taken away: it is simply not counted here. Other plugins' advancements
 * and recipes (any namespace but {@code minecraft}) pass.
 */
public final class QuietVanilla implements Listener {

    private final Predicate<World> islands;

    /** {@code islands}: true for the worlds this applies to. */
    public QuietVanilla(Predicate<World> islands) {
        this.islands = islands;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        if (NamespacedKey.MINECRAFT.equals(event.getAdvancement().getKey().getNamespace()) && here(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRecipe(PlayerRecipeDiscoverEvent event) {
        if (NamespacedKey.MINECRAFT.equals(event.getRecipe().getNamespace()) && here(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean here(Player player) {
        return player != null && islands.test(player.getWorld());
    }
}
