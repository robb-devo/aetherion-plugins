package de.aetherion.mining;

import de.aetherion.mining.veins.VeinsWorld;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static org.bukkit.event.Event.Result.ALLOW;

public class MiningListener implements Listener {

    /*
     * =========================================================
     * WORLDGUARD-ÜBERNAHME
     * =========================================================
     *
     * Nur unsere Mining-Blöcke werden von WorldGuard erlaubt.
     * Alle anderen Blöcke bleiben ganz normal geschützt.
     */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardBreak(BreakBlockEvent event) {
        if (veinsEvent(event)) {
            event.setResult(ALLOW);
            return;
        }
        if (allowsMining(event.getCause().getFirstBlock())) {
            event.setResult(ALLOW);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (allowsMining(block)) {
                event.setResult(ALLOW);
                return;
            }
        }
    }


    /*
     * =========================================================
     * NORMALES MINECRAFT-MINING
     * =========================================================
     */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onBlockBreak(
            org.bukkit.event.block.BlockBreakEvent event
    ) {
        Block block = event.getBlock();
        if (isFarmIsland(block)) {
            if (allowsMining(block)) {
                event.setCancelled(true);
                event.setDropItems(false);
                event.getPlayer().sendMessage("§7Mining is off on the Farm Isle.");
            }
            return;
        }
        // Personal/guild islands: normal break, no seal-regen.
        if (isBuildWorld(block) || isDungeon(block) || veins(block) || !allowsMining(block)) {
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMiningSeal(org.bukkit.event.block.BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isFarmIsland(block) || isDungeon(block) || isBuildWorld(block)) {
            return;
        }
        Material material = block.getType();
        if (!MiningBlocks.allows(material)) {
            return;
        }
        if (veins(block) || block.hasMetadata("aetherion_spread_break")) {
            return;
        }
        // Seal cancels the break for regen — notify quests explicitly so First Shift still counts.
        try {
            Class.forName("de.aetherion.quests.bridge.QuestProgressBridge")
                    .getMethod("noteBroken", org.bukkit.entity.Player.class, Material.class, int.class)
                    .invoke(null, event.getPlayer(), material, 1);
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
        }
        BlockData original = block.getBlockData().clone();
        seal(event.getPlayer(), block, original, getRespawnTime(material));
        event.setCancelled(true);
    }


    /*
     * Ore becomes bedrock in this tick. Cancelling stops vanilla AIR.
     * If Paper still writes air, the next-tick fallback covers it.
     */
    private static void seal(org.bukkit.entity.Player player, Block block, BlockData original, long respawnSeconds) {
        org.bukkit.block.data.BlockData bedrock = Material.BEDROCK.createBlockData();
        block.setBlockData(bedrock, false);
        if (player != null && player.isOnline()) {
            player.sendBlockChange(block.getLocation(), bedrock);
        }
        Bukkit.getScheduler().runTask(AetherionMining.getInstance(), () -> {
            Material now = block.getType();
            if (now == Material.AIR || now == original.getMaterial()) {
                block.setBlockData(bedrock, false);
            }
        });
        Bukkit.getScheduler().runTaskLater(
                AetherionMining.getInstance(),
                () -> {
                    if (block.getType() == Material.BEDROCK) {
                        block.setBlockData(original, false);
                    }
                },
                20L * Math.max(1L, respawnSeconds)
        );
    }

    /**
     * External harvest (Vein Siphon vacuum, etc.): seal + schedule restore.
     * Do not fire {@link org.bukkit.event.block.BlockBreakEvent} (would double-payout).
     * Open-mine / veins callers should set AIR themselves instead of calling this.
     */
    public static void sealForVacuum(Block block, BlockData original) {
        sealForVacuum(null, block, original);
    }

    public static void sealForVacuum(org.bukkit.entity.Player player, Block block, BlockData original) {
        if (block == null || original == null) {
            return;
        }
        if (SharedWorldGuard.isBuildWorld(block.getWorld())) {
            return;
        }
        if (AetherionMining.getInstance() == null) {
            return;
        }
        seal(player, block, original.clone(), getRespawnTime(original.getMaterial()));
    }


    /*
     * =========================================================
     * RESPAWNZEITEN
     * =========================================================
     *
     * Kohle       = 10 Sekunden
     * Kupfer      = 15 Sekunden
     * Eisen       = 20 Sekunden
     * Gold        = 25 Sekunden
     * Redstone    = 15 Sekunden
     * Lapis       = 20 Sekunden
     * Diamant     = 40 Sekunden
     * Emerald     = 45 Sekunden
     * Ancient     = 60 Sekunden
     *
     * Stone       = 10 Sekunden
     * =========================================================
     */

    private static boolean veinsEvent(BreakBlockEvent event) {
        if (veins(event.getCause().getFirstBlock())) {
            return true;
        }
        for (Block block : event.getBlocks()) {
            if (veins(block)) {
                return true;
            }
        }
        return false;
    }

    private static boolean veins(Block block) {
        if (block == null) {
            return false;
        }
        AetherionMining plugin = AetherionMining.getInstance();
        VeinsWorld veins = plugin == null ? null : plugin.getVeins();
        return veins != null && veins.isVeins(block.getWorld());
    }

    private static boolean allowsMining(Block block) {
        return block != null && MiningBlocks.allows(block.getType());
    }

    private static boolean isFarmIsland(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        String name = block.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return name.equals("aether_farm_island") || name.startsWith("aether_farm_");
    }

    private static boolean isDungeon(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        String name = block.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }

    private static boolean isBuildWorld(Block block) {
        return block != null && SharedWorldGuard.isBuildWorld(block.getWorld());
    }

    private static long getRespawnTime(Material material) {

        switch (material) {

            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                return 10;


            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
                return 15;


            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                return 20;


            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                return 25;


            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return 15;


            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                return 20;


            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return 40;


            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return 45;


            case ANCIENT_DEBRIS:
                return 60;

            case NETHER_QUARTZ_ORE:
            case NETHER_GOLD_ORE:
                return 20;

            case AMETHYST_CLUSTER:
                return 30;

            case COAL_BLOCK:
                return 25;

            case RAW_COPPER_BLOCK:
            case COPPER_BLOCK:
                return 30;

            case RAW_IRON_BLOCK:
            case IRON_BLOCK:
                return 35;

            case REDSTONE_BLOCK:
                return 30;

            case RAW_GOLD_BLOCK:
            case GOLD_BLOCK:
            case QUARTZ_BLOCK:
                return 40;

            case LAPIS_BLOCK:
                return 40;

            case DIAMOND_BLOCK:
                return 70;

            case EMERALD_BLOCK:
                return 80;

            case NETHERITE_BLOCK:
                return 100;

            case DEEPSLATE:
            case STONE:
            default:
                return 10;
        }
    }
}