package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import de.aetherion.items.farming.Crops;
import de.aetherion.items.mining.HarvestRules;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class EmeraldSpreadListener implements Listener {

    private static final String SPREAD_BREAK_METADATA =
            "aetherion_spread_break";

    private final ActiveEquipmentStats equipmentStats;

    private final Set<String> spreadProcessing =
            new HashSet<>();


    public EmeraldSpreadListener(
            ItemManager itemManager
    ) {

        this.equipmentStats =
                new ActiveEquipmentStats(
                        itemManager
                );
    }


    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event
    ) {

        Player player =
                event.getPlayer();


        String playerId =
                player.getUniqueId().toString();


        if (spreadProcessing.contains(playerId)) {
            return;
        }


        Block source =
                event.getBlock();


        Material sourceMaterial =
                source.getType();


        if (
                sourceMaterial.isAir()
                        || source.isLiquid()
                        || Crops.isCrop(sourceMaterial)
                        || HarvestRules.log(sourceMaterial)
        ) {

            return;
        }


        double spread =
                equipmentStats.getStat(
                        player,
                        ItemCapability.SPREAD
                );


        if (spread <= 0) {
            return;
        }


        ItemStack tool =
                player.getInventory()
                        .getItemInMainHand();


        int guaranteedBlocks =
                (int) Math.floor(
                        spread / 100.0
                );


        double remainingChance =
                spread
                        - guaranteedBlocks * 100.0;


        int additionalBlocks =
                guaranteedBlocks;


        if (
                remainingChance > 0
                        && Math.random() * 100.0
                        < remainingChance
        ) {

            additionalBlocks++;
        }


        if (additionalBlocks <= 0) {
            return;
        }


        /*
         * =====================================================
         * SPREAD TARGETS
         * =====================================================
         *
         * Der Spread darf sich über zusammenhängende Blöcke
         * weiterbewegen.
         *
         * Jeder Schritt prüft das 3x3x3 Umfeld.
         *
         * Dadurch funktionieren:
         *
         * - direkt angrenzend
         * - diagonal
         * - oben / unten
         * - über weitere zusammenhängende Erzblöcke
         *
         * Die Anzahl der tatsächlich gebrochenen Blöcke wird
         * weiterhin durch den vorhandenen Spread-Wert bestimmt.
         */

        Map<Block, Integer> targets =
                findSpreadTargets(
                        source,
                        sourceMaterial,
                        additionalBlocks
                );


        if (targets.isEmpty()) {
            return;
        }


        spreadProcessing.add(playerId);


        try {

            for (
                    Map.Entry<Block, Integer> entry
                    : targets.entrySet()
            ) {

                Block target =
                        entry.getKey();


                int distance =
                        entry.getValue();


                if (
                        target == null
                                || target.getType()
                                != sourceMaterial
                ) {

                    continue;
                }


                /*
                 * =================================================
                 * SPREAD BREAK MARKIEREN
                 * =================================================
                 *
                 * Der MiningListener des Mining-Plugins erkennt
                 * dieses Metadata und startet für diesen künstlich
                 * ausgelösten Break KEINEN normalen Respawn-Timer.
                 *
                 * Der Spread-Listener übernimmt den Respawn selbst.
                 */

                target.setMetadata(
                        SPREAD_BREAK_METADATA,
                        new FixedMetadataValue(
                                AetherionItems.getInstance(),
                                true
                        )
                );


                BlockBreakEvent spreadEvent =
                        new BlockBreakEvent(
                                target,
                                player
                        );


                try {

                    Bukkit.getPluginManager()
                            .callEvent(
                                    spreadEvent
                            );

                } finally {

                    target.removeMetadata(
                            SPREAD_BREAK_METADATA,
                            AetherionItems.getInstance()
                    );
                }


                if (spreadEvent.isCancelled()) {
                    continue;
                }


                if (
                        target.getType()
                                != sourceMaterial
                ) {

                    continue;
                }


                /*
                 * =================================================
                 * DROPS MANUELL ERZEUGEN
                 * =================================================
                 */

                Collection<ItemStack> drops = HarvestRules.tracked(sourceMaterial)
                        ? java.util.List.of()
                        : target.getDrops(tool, player);

                long baseRespawnSeconds =
                        getBaseRespawnTime(
                                sourceMaterial
                        );
                boolean openMine = HarvestRules.openMine(target.getWorld());
                if (!openMine && baseRespawnSeconds < 0) {
                    continue;
                }

                if (openMine) {
                    target.setType(Material.AIR, false);
                    if (player.isOnline()) {
                        player.sendBlockChange(target.getLocation(), Material.AIR.createBlockData());
                    }
                } else {
                    target.setType(Material.BEDROCK, false);
                    if (player.isOnline()) {
                        player.sendBlockChange(target.getLocation(), Material.BEDROCK.createBlockData());
                    }
                }

                for (ItemStack drop : drops) {

                    if (
                            drop == null
                                    || drop.getType().isAir()
                    ) {

                        continue;
                    }


                    Material convertedMaterial =
                            convertOreDrop(
                                    drop.getType()
                            );


                    if (
                            convertedMaterial != null
                    ) {

                        drop.setType(
                                convertedMaterial
                        );
                    }


                    InventoryDrops.give(player, drop);
                }


                if (openMine) {
                    continue;
                }

                long spreadRespawnSeconds =
                        Math.round(
                                baseRespawnSeconds
                                        * (
                                        1.0
                                                + (
                                                distance
                                                        * 0.10
                                        )
                                )
                        );


                scheduleSpreadRespawn(
                        target,
                        sourceMaterial,
                        spreadRespawnSeconds
                );
            }

        } finally {

            spreadProcessing.remove(
                    playerId
            );
        }
    }


    /*
     * =========================================================
     * SPREAD TARGETS SUCHEN
     * =========================================================
     *
     * BFS über zusammenhängende Blöcke.
     *
     * Jeder Block prüft seine direkten 1-Block-Nachbarn
     * in alle drei Richtungen.
     *
     * Das bedeutet:
     *
     * X = -1 bis +1
     * Y = -1 bis +1
     * Z = -1 bis +1
     *
     * Der Spread kann sich dadurch frei durch einen Erz-Haufen
     * bewegen.
     */

    private Map<Block, Integer> findSpreadTargets(
            Block source,
            Material sourceMaterial,
            int requiredTargets
    ) {

        Map<Block, Integer> targets =
                new HashMap<>();


        Set<String> visited =
                new HashSet<>();


        Queue<BlockDistance> queue =
                new ArrayDeque<>();


        queue.add(
                new BlockDistance(
                        source,
                        0
                )
        );


        visited.add(
                getBlockKey(source)
        );


        while (
                !queue.isEmpty()
                        && targets.size()
                        < requiredTargets
        ) {

            BlockDistance current =
                    queue.poll();


            Block currentBlock =
                    current.block;


            int currentDistance =
                    current.distance;


            /*
             * =====================================================
             * 3x3x3 NACHBARSCHAFT
             * =====================================================
             */

            for (int x = -1; x <= 1; x++) {

                for (int y = -1; y <= 1; y++) {

                    for (int z = -1; z <= 1; z++) {

                        if (
                                x == 0
                                        && y == 0
                                        && z == 0
                        ) {

                            continue;
                        }


                        if (
                                targets.size()
                                        >= requiredTargets
                        ) {

                            break;
                        }


                        Block neighbour =
                                currentBlock.getRelative(
                                        x,
                                        y,
                                        z
                                );


                        String blockKey =
                                getBlockKey(
                                        neighbour
                                );


                        if (!visited.add(blockKey)) {
                            continue;
                        }


                        if (
                                !isValidSpreadBlock(
                                        neighbour,
                                        sourceMaterial
                                )
                        ) {

                            continue;
                        }


                        int distance =
                                Math.max(
                                        Math.max(
                                                Math.abs(
                                                        neighbour.getX()
                                                                - source.getX()
                                                ),
                                                Math.abs(
                                                        neighbour.getY()
                                                                - source.getY()
                                                )
                                        ),
                                        Math.abs(
                                                neighbour.getZ()
                                                        - source.getZ()
                                        )
                                );


                        targets.put(
                                neighbour,
                                distance
                        );


                        queue.add(
                                new BlockDistance(
                                        neighbour,
                                        distance
                                )
                        );
                    }


                    if (
                            targets.size()
                                    >= requiredTargets
                    ) {

                        break;
                    }
                }


                if (
                        targets.size()
                                >= requiredTargets
                ) {

                    break;
                }
            }
        }


        return targets;
    }


    /*
     * =========================================================
     * BLOCK DISTANCE
     * =========================================================
     */

    private static class BlockDistance {

        private final Block block;

        private final int distance;


        private BlockDistance(
                Block block,
                int distance
        ) {

            this.block =
                    block;

            this.distance =
                    distance;
        }
    }


    /*
     * =========================================================
     * BLOCK KEY
     * =========================================================
     */

    private String getBlockKey(
            Block block
    ) {

        return block.getWorld()
                .getUID()
                + ":"
                + block.getX()
                + ":"
                + block.getY()
                + ":"
                + block.getZ();
    }


    /*
     * =========================================================
     * VALID SPREAD BLOCK
     * =========================================================
     */

    private boolean isValidSpreadBlock(
            Block block,
            Material sourceMaterial
    ) {

        if (block == null) {
            return false;
        }


        if (
                block.getType()
                        != sourceMaterial
        ) {

            return false;
        }


        if (block.getType().isAir()) {
            return false;
        }


        if (block.isLiquid()) {
            return false;
        }


        if (
                block.getState()
                        instanceof org.bukkit.block.Container
        ) {

            return false;
        }


        if (
                block.getType()
                        == Material.BEDROCK
        ) {

            return false;
        }


        return block.getType()
                .isSolid();
    }


    /*
     * =========================================================
     * BASIS-RESPAWNZEIT
     * =========================================================
     */

    private long getBaseRespawnTime(
            Material material
    ) {
        if (material != null) {
            String name = material.name();
            if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.contains("HYPHAE") || name.endsWith("_WOOD")) {
                return -1;
            }
        }

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

            case STONE:
            default:
                return 10;
        }
    }


    /*
     * =========================================================
     * SPREAD-RESPAWN
     * =========================================================
     */

    private void scheduleSpreadRespawn(
            Block block,
            Material originalMaterial,
            long respawnSeconds
    ) {
        if (block.getType() != Material.BEDROCK) {
            block.setType(Material.BEDROCK, false);
        }
        Bukkit.getScheduler().runTaskLater(
                AetherionItems.getInstance(),
                () -> {
                    if (block.getType() == Material.BEDROCK) {
                        block.setType(originalMaterial, false);
                    }
                },
                20L * Math.max(1L, respawnSeconds)
        );
    }


    /*
     * =========================================================
     * ORE DROP CONVERSION
     * =========================================================
     *
     * Nur rohe Erze / Debris werden umgewandelt (Öfen aus).
     */

    private Material convertOreDrop(
            Material material
    ) {

        switch (material) {

            case RAW_IRON:
                return Material.IRON_INGOT;


            case RAW_GOLD:
                return Material.GOLD_INGOT;


            case RAW_COPPER:
                return Material.COPPER_INGOT;


            case ANCIENT_DEBRIS:
            case NETHERITE_SCRAP:
                return Material.NETHERITE_INGOT;


            default:
                return null;
        }
    }
}