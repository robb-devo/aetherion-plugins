package de.aetherion.items.world;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;

/**
 * Block vanilla station / breeding paths that bypass Aetherion progression.
 * Creative / op bypass stays available for building.
 */
public final class VanillaBlockListener implements Listener {

    private static final Set<Material> BLOCKED_STATIONS = EnumSet.of(
            Material.CRAFTING_TABLE,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BREWING_STAND,
            Material.ENCHANTING_TABLE,
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.GRINDSTONE,
            Material.STONECUTTER,
            Material.LOOM,
            Material.CARTOGRAPHY_TABLE,
            Material.SMITHING_TABLE,
            Material.FLETCHING_TABLE
    );

    private final JavaPlugin plugin;

    public VanillaBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStation(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (!BLOCKED_STATIONS.contains(type)) {
            return;
        }
        Player player = event.getPlayer();
        if (bypass(player)) {
            return;
        }
        // Lectern: allow reading books, block taking them (sneak) — still block open for now.
        event.setCancelled(true);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Use Aetherion stations — not vanilla " + pretty(type),
                net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player && bypass(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeedAnimal(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Animals)) {
            return;
        }
        Player player = event.getPlayer();
        if (bypass(player)) {
            return;
        }
        // Block feeding that starts love mode / breeding.
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (!hand.isEdible() && hand != Material.WHEAT && hand != Material.CARROT
                && hand != Material.POTATO && hand != Material.BEETROOT
                && hand != Material.GOLDEN_CARROT && hand != Material.WHEAT_SEEDS
                && hand != Material.PUMPKIN_SEEDS && hand != Material.MELON_SEEDS
                && hand != Material.BEETROOT_SEEDS && hand != Material.SWEET_BERRIES
                && hand != Material.GLOW_BERRIES && hand != Material.COD
                && hand != Material.SALMON && hand != Material.TROPICAL_FISH
                && hand != Material.PUFFERFISH && hand != Material.DANDELION
                && hand != Material.SEAGRASS && hand != Material.KELP
                && hand != Material.CRIMSON_FUNGUS && hand != Material.WARPED_FUNGUS
                && hand != Material.BONE_MEAL) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "No vanilla breeding here",
                net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
    }

    private static boolean bypass(Player player) {
        return player != null && (player.getGameMode() == GameMode.CREATIVE || player.isOp());
    }

    private static String pretty(Material type) {
        return type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
