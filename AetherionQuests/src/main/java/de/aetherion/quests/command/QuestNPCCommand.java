package de.aetherion.quests.command;


import de.aetherion.core.AetherKeys;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.service.QuestNPCSpawnService;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;


public class QuestNPCCommand implements CommandExecutor {


    private static final double REMOVE_RADIUS = 3.0;

    private static final double CLEANUP_RADIUS = 18.0;


    private final QuestNPCSpawnService spawnService;


    public QuestNPCCommand() {

        this.spawnService =
                new QuestNPCSpawnService();

    }


    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {


        /*
         * =========================================================
         * PLAYER CHECK
         * =========================================================
         */

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "This command can only be used by a player."
            );

            return true;

        }


        /*
         * =========================================================
         * PERMISSION
         * =========================================================
         */

        if (!player.hasPermission(
                "aetherionquests.admin"
        )) {

            player.sendMessage(
                    ChatColor.RED
                            + "You do not have permission to use this command."
            );

            return true;

        }


        /*
         * =========================================================
         * NO ARGUMENTS
         * =========================================================
         */

        if (args.length == 0) {

            sendUsage(player);

            return true;

        }


        String action =
                args[0].toLowerCase();


        /*
         * =========================================================
         * SPAWN
         * =========================================================
         *
         * /questnpc spawn <npc>
         */

        if (action.equals("spawn")) {

            handleSpawn(
                    player,
                    args
            );

            return true;

        }


        if (action.equals("give")) {

            handleGive(
                    player,
                    args
            );

            return true;

        }


        /*
         * =========================================================
         * REMOVE
         * =========================================================
         *
         * /questnpc remove
         *
         * Entfernt registrierte Aetherion Quest NPCs
         * im direkten Umfeld.
         */

        if (action.equals("remove")) {

            handleRemove(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * CLEANUP
         * =========================================================
         *
         * /questnpc cleanup
         *
         * Entfernt alte / unbekannte Villager-NPCs
         * im Umfeld.
         *
         * Neu markierte Aetherion NPCs werden geschützt.
         *
         * Registry und NPCDataStorage werden NICHT verändert.
         */

        if (action.equals("cleanup")) {

            handleCleanup(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * LIST
         * =========================================================
         *
         * /questnpc list
         */

        if (action.equals("list")) {

            handleList(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * REMOVE ALL
         * =========================================================
         *
         * /questnpc removeall
         *
         * Entfernt alle aktuell bekannten und gespawnten
         * Aetherion Quest NPCs.
         */

        if (action.equals("removeall")) {

            handleRemoveAll(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * DEBUG
         * =========================================================
         *
         * /questnpc debug
         *
         * Durchsucht die GESAMTE aktuelle Welt (nicht nur einen
         * Radius) nach allen Entities mit unserem quest_npc-Tag.
         *
         * Dient ausschließlich der Diagnose von Ghost-/Duplikat-
         * Problemen: zeigt jede getaggte Entity mit npcId, UUID
         * und Position, unabhängig davon, ob die Registry sie
         * aktuell kennt oder nicht.
         *
         * Verändert nichts an Registry oder NPCDataStorage.
         */

        if (action.equals("debug")) {

            handleDebug(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * FIX DUPLICATES
         * =========================================================
         *
         * /questnpc fixduplicates
         */

        if (action.equals("fixduplicates")) {

            handleFixDuplicates(
                    player
            );

            return true;

        }


        /*
         * =========================================================
         * FORGET (wipe saved position)
         * =========================================================
         *
         * /questnpc forget <npc>
         *
         * Unloads the entity AND deletes npcs.yml entry.
         * Normal remove/despawn keeps the saved position.
         */

        if (action.equals("forget")) {

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /questnpc forget <npc>");
                return true;
            }
            String npcId = args[1].toLowerCase();
            QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
            if (npc == null) {
                player.sendMessage(ChatColor.RED + "Unknown quest NPC: " + ChatColor.WHITE + npcId);
                return true;
            }
            spawnService.forget(npcId);
            player.sendMessage(ChatColor.GREEN + "Forgot saved position for §f" + npc.getName()
                    + ChatColor.GREEN + ". Will not restore on reboot until you spawn them again.");
            return true;

        }


        /*
         * =========================================================
         * UNKNOWN COMMAND
         * =========================================================
         */

        sendUsage(player);

        return true;

    }


    /*
     * =============================================================
     * SPAWN NPC
     * =============================================================
     */

    private void handleSpawn(
            Player player,
            String[] args
    ) {


        if (args.length < 2) {

            player.sendMessage(
                    ChatColor.RED
                            + "Usage: /questnpc spawn <npc>"
            );

            return;

        }


        String npcId =
                args[1].toLowerCase();


        QuestNPC npc =
                QuestNPCRegistry.getNPC(
                        npcId
                );


        if (npc == null) {

            player.sendMessage(
                    ChatColor.RED
                            + "Unknown quest NPC: "
                            + ChatColor.WHITE
                            + npcId
            );

            return;

        }


        /*
         * =========================================================
         * ALREADY SPAWNED
         * =========================================================
         */

        if (npc.getEntityId() != null) {

            AetherionQuests plugin = AetherionQuests.getInstance();
            if (de.aetherion.quests.npc.LivingNpcService.isLiving(npcId)
                    && plugin != null
                    && plugin.getLivingNpcService() != null
                    && plugin.getLivingNpcService().isSpawned(npcId)) {
                player.sendMessage(
                        ChatColor.YELLOW
                                + npc.getName()
                                + ChatColor.GRAY
                                + " is already spawned."
                );
                return;
            }

            Entity entity =
                    player.getServer().getEntity(
                            npc.getEntityId()
                    );


            if (entity != null
                    && !entity.isDead()) {

                player.sendMessage(
                        ChatColor.YELLOW
                                + npc.getName()
                                + ChatColor.GRAY
                                + " is already spawned."
                );

                return;

            }


            npc.setEntityId(
                    null
            );

        }


        /*
         * =========================================================
         * CURRENT LOCATION
         * =========================================================
         */

        Location location =
                player.getLocation();


        /*
         * =========================================================
         * SPAWN SERVICE
         * =========================================================
         */

        QuestNPC spawnedNPC =
                spawnService.spawnNPC(
                        npcId,
                        location
                );


        if (spawnedNPC == null) {

            if (de.aetherion.quests.npc.LivingNpcService.isLiving(npcId)) {
                AetherionQuests plugin = AetherionQuests.getInstance();
                if (plugin == null
                        || plugin.getLivingNpcService() == null
                        || !plugin.getLivingNpcService().available()) {
                    player.sendMessage(
                            ChatColor.RED
                                    + "FancyNpcs is not loaded. Restart the server after adding FancyNpcs."
                    );
                    return;
                }
            }

            player.sendMessage(
                    ChatColor.RED
                            + "Could not spawn quest NPC: "
                            + ChatColor.WHITE
                            + npcId
            );

            return;

        }


        player.sendMessage(
                ChatColor.GREEN
                        + "Spawned "
                        + ChatColor.WHITE
                        + spawnedNPC.getName()
                        + ChatColor.GREEN
                        + " at your location."
        );

    }


    private void handleGive(
            Player player,
            String[] args
    ) {

        if (args.length < 2) {

            player.sendMessage(
                    ChatColor.RED
                            + "Usage: /questnpc give <npc>"
            );

            return;

        }

        String npcId = args[1].toLowerCase();
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);

        if (npc == null) {

            player.sendMessage(
                    ChatColor.RED
                            + "Unknown quest NPC: "
                            + ChatColor.WHITE
                            + npcId
            );

            return;

        }

        player.getInventory().addItem(
                de.aetherion.quests.listener.NpcAnchorListener.create(npcId)
        );

        player.sendMessage(
                ChatColor.GREEN
                        + "Gave NPC anchor for "
                        + ChatColor.WHITE
                        + npc.getName()
                        + ChatColor.GREEN
                        + ". Right-click a block to place him."
        );

    }


    /*
     * =============================================================
     * REMOVE NEARBY NPC
     * =============================================================
     *
     * /questnpc remove
     *
     * Entfernt ausschließlich NPCs, die aktuell in unserer
     * Registry bekannt sind.
     *
     * Die persistente NPC-Definition bleibt erhalten.
     */

    private void handleRemove(
            Player player
    ) {


        Location playerLocation =
                player.getLocation();


        int removed = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        AetherionQuests plugin = AetherionQuests.getInstance();
        NamespacedKey tag = plugin == null ? null : AetherKeys.QUEST_NPC;

        for (Entity nearby : player.getNearbyEntities(REMOVE_RADIUS, REMOVE_RADIUS, REMOVE_RADIUS)) {
            if (tag == null) {
                break;
            }
            String tagged = nearby.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (tagged != null && !tagged.isBlank()) {
                ids.add(tagged);
            }
        }

        for (QuestNPC npc : QuestNPCRegistry.getAll().values()) {
            if (npc == null || npc.getEntityId() == null) {
                continue;
            }
            Entity entity = player.getServer().getEntity(npc.getEntityId());
            if (entity == null || entity.isDead()) {
                continue;
            }
            if (!entity.getWorld().equals(playerLocation.getWorld())) {
                continue;
            }
            if (entity.getLocation().distance(playerLocation) > REMOVE_RADIUS) {
                continue;
            }
            ids.add(npc.getId());
        }

        for (String npcId : ids) {
            if (spawnService.despawn(npcId)) {
                removed++;
            }
        }


        if (removed == 0) {

            player.sendMessage(
                    ChatColor.YELLOW
                            + "No quest NPC found within "
                            + ChatColor.WHITE
                            + (int) REMOVE_RADIUS
                            + ChatColor.YELLOW
                            + " blocks."
            );

            return;

        }


        player.sendMessage(
                ChatColor.GREEN
                        + "Removed "
                        + ChatColor.WHITE
                        + removed
                        + ChatColor.GREEN
                        + " quest NPC(s) nearby."
        );

    }


    /*
     * =============================================================
     * CLEANUP OLD NPCs
     * =============================================================
     *
     * /questnpc cleanup
     *
     * Entfernt alte Villager-Entities im Umfeld, sofern sie
     * NICHT die neue Aetherion Quest NPC Kennzeichnung besitzen.
     *
     * Dadurch können alte / kaputte NPCs aus früheren Versionen
     * entfernt werden.
     *
     * Unsere neuen NPCs bleiben unangetastet.
     */

    private void handleCleanup(
            Player player
    ) {


        Location playerLocation =
                player.getLocation();


        NamespacedKey npcKey = AetherKeys.QUEST_NPC;


        int removed = 0;


        for (Entity entity :
                playerLocation.getWorld().getNearbyEntities(
                        playerLocation,
                        CLEANUP_RADIUS,
                        CLEANUP_RADIUS,
                        CLEANUP_RADIUS
                )) {


            if (entity == null
                    || entity.isDead()) {

                continue;

            }


            /*
             * Nur Villager bereinigen.
             */

            if (!(entity instanceof Villager)) {

                continue;

            }


            /*
             * =====================================================
             * NEUE AETHERION NPCs SCHÜTZEN
             * =====================================================
             */

            String npcId =
                    entity.getPersistentDataContainer().get(
                            npcKey,
                            PersistentDataType.STRING
                    );


            if (npcId != null
                    && !npcId.isEmpty()) {

                continue;

            }


            /*
             * =====================================================
             * ALTES NPC ENTFERNEN
             * =====================================================
             */

            entity.remove();

            removed++;

        }


        if (removed == 0) {

            player.sendMessage(
                    ChatColor.YELLOW
                            + "No old/unmarked NPCs found within "
                            + ChatColor.WHITE
                            + (int) CLEANUP_RADIUS
                            + ChatColor.YELLOW
                            + " blocks."
            );

            return;

        }


        player.sendMessage(
                ChatColor.GREEN
                        + "Cleaned up "
                        + ChatColor.WHITE
                        + removed
                        + ChatColor.GREEN
                        + " old/unmarked NPC(s)."
        );

    }


    /*
     * =============================================================
     * LIST NPCS
     * =============================================================
     */

    private void handleList(
            Player player
    ) {


        player.sendMessage(
                ChatColor.GOLD
                        + "=== Aetherion Quest NPCs ==="
        );


        for (QuestNPC npc :
                QuestNPCRegistry.getAll().values()) {


            if (npc == null) {

                continue;

            }


            boolean spawned =
                    false;


            if (npc.getEntityId() != null) {

                Entity entity =
                        player.getServer().getEntity(
                                npc.getEntityId()
                        );


                spawned =
                        entity != null
                                && !entity.isDead();

            }


            player.sendMessage(
                    ChatColor.GRAY
                            + "- "
                            + ChatColor.WHITE
                            + npc.getId()
                            + ChatColor.GRAY
                            + " ("
                            + npc.getName()
                            + ") "
                            + (
                            spawned
                                    ? ChatColor.GREEN + "[SPAWNED]"
                                    : ChatColor.RED + "[NOT SPAWNED]"
                    )
            );

        }

    }


    /*
     * =============================================================
     * REMOVE ALL
     * =============================================================
     *
     * Entfernt alle aktuell bekannten Aetherion NPC Entities.
     *
     * Registry und persistente Definitionen bleiben erhalten.
     */

    private void handleRemoveAll(
            Player player
    ) {


        int removed = 0;

        for (QuestNPC npc : new java.util.ArrayList<>(QuestNPCRegistry.getAll().values())) {
            if (npc == null) {
                continue;
            }
            if (spawnService.despawn(npc.getId())) {
                removed++;
            }
        }


        player.sendMessage(
                ChatColor.GREEN
                        + "Removed "
                        + ChatColor.WHITE
                        + removed
                        + ChatColor.GREEN
                        + " quest NPC(s)."
        );

    }


    /*
     * =============================================================
     * DEBUG: ALLE GETAGGTEN ENTITIES DER WELT
     * =============================================================
     *
     * Durchsucht die komplette aktuelle Welt (nicht nur einen
     * Radius um den Spieler) nach jeder Entity mit dem
     * quest_npc-PersistentData-Tag.
     *
     * Zeigt npcId, Entity-UUID und Position.
     *
     * Dient ausschließlich der Fehlersuche bei Ghost-/
     * Duplikat-NPCs. Verändert nichts.
     */

    private void handleDebug(
            Player player
    ) {


        NamespacedKey npcKey = AetherKeys.QUEST_NPC;


        player.sendMessage(
                ChatColor.GOLD
                        + "=== Debug: alle quest_npc Entities in der Welt ==="
        );


        int total = 0;


        for (Entity entity :
                player.getWorld().getEntities()) {


            if (entity == null
                    || entity.isDead()) {

                continue;

            }


            String npcId =
                    entity.getPersistentDataContainer().get(
                            npcKey,
                            PersistentDataType.STRING
                    );


            if (npcId == null
                    || npcId.isEmpty()) {

                continue;

            }


            total++;


            Location loc =
                    entity.getLocation();


            player.sendMessage(
                    ChatColor.YELLOW
                            + "npcId="
                            + ChatColor.WHITE
                            + npcId
                            + ChatColor.GRAY
                            + " type="
                            + entity.getType().name()
                            + " uuid="
                            + entity.getUniqueId()
                            + " pos=("
                            + loc.getBlockX() + ", "
                            + loc.getBlockY() + ", "
                            + loc.getBlockZ()
                            + ")"
            );

        }


        player.sendMessage(
                ChatColor.GOLD
                        + "Gefunden: "
                        + total
        );

    }


    /*
     * =============================================================
     * FIX DUPLICATES
     * =============================================================
     *
     * Gruppiert alle getaggten Entities der Welt nach npcId
     * und entfernt pro npcId alle bis auf eine.
     *
     * Behalten wird bevorzugt die Entity, deren UUID die
     * Registry aktuell kennt. Ist keine bekannt, wird die
     * erste gefundene Entity behalten.
     */

    private void handleFixDuplicates(Player player) {
        int kept = spawnService.ensureAllUnique();
        player.sendMessage(
                ChatColor.GREEN
                        + "NPC uniqueness pass done. Living keepers: "
                        + ChatColor.WHITE
                        + kept
        );
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getMarkerManager() != null) {
            plugin.getMarkerManager().refreshAll();
        }
    }


    /*
     * =============================================================
     * USAGE
     * =============================================================
     */

    private void sendUsage(
            Player player
    ) {


        player.sendMessage(
                ChatColor.GOLD
                        + "=== Quest NPC Commands ==="
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc spawn <npc>"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc give <npc>"
                        + ChatColor.GRAY
                        + " - get an anchor item"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc remove"
                        + ChatColor.GRAY
                        + " - unload nearby NPCs (keeps saved spot)"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc forget <npc>"
                        + ChatColor.GRAY
                        + " - unload + wipe saved spot"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc cleanup"
                        + ChatColor.GRAY
                        + " - remove unmarked villagers within 6 blocks"
        );
        player.sendMessage(
                ChatColor.DARK_GRAY
                        + "  (stand near old head-shakers by McNugget)"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc list"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc removeall"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc debug"
                        + ChatColor.GRAY
                        + " - list all tagged entities in this world"
        );


        player.sendMessage(
                ChatColor.YELLOW
                        + "/questnpc fixduplicates"
                        + ChatColor.GRAY
                        + " - remove duplicate tagged entities per npc id"
        );

    }

}