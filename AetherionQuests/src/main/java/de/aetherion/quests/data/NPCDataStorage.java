package de.aetherion.quests.data;


import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;


public class NPCDataStorage {


    private final File file;

    private final YamlConfiguration config;



    public NPCDataStorage(
            File dataFolder
    ) {


        if (!dataFolder.exists()) {

            dataFolder.mkdirs();

        }



        file = new File(
                dataFolder,
                "npcs.yml"
        );



        if (!file.exists()) {

            try {

                file.createNewFile();

            } catch (IOException e) {

                e.printStackTrace();

            }

        }



        config =
                YamlConfiguration.loadConfiguration(
                        file
                );

    }



    /*
     * =============================================================
     * NPC POSITION SPEICHERN
     * =============================================================
     *
     * Die NPC-ID ist die stabile Identität.
     *
     * Die Entity-UUID wird NICHT persistent gespeichert.
     *
     * Gespeichert werden:
     *
     * - Name
     * - Quest-ID
     * - Dialog-ID
     * - Service-ID
     * - World
     * - X
     * - Y
     * - Z
     * - Yaw
     * - Pitch
     *
     * Die Position wird direkt übergeben.
     *
     * Dadurch ist das Speichern unabhängig davon, ob Bukkit
     * die Entity über ihre UUID bereits auflösen kann.
     * =============================================================
     */

    public void saveNPC(
            QuestNPC npc,
            Location location
    ) {


        if (npc == null) {

            return;

        }


        if (location == null
                || location.getWorld() == null) {

            return;

        }



        String npcId =
                npc.getId();


        if (npcId == null
                || npcId.isEmpty()) {

            return;

        }

        // Re-read disk so we never clobber other NPCs saved by another process/session.
        reloadFromDisk();

        String path =
                "npcs." + npcId;



        /*
         * =========================================================
         * GRUNDDATEN
         * =========================================================
         */

        config.set(
                path + ".name",
                npc.getName()
        );


        config.set(
                path + ".questId",
                npc.getQuestId()
        );


        config.set(
                path + ".dialogId",
                npc.getDialogId()
        );


        config.set(
                path + ".serviceId",
                npc.getServiceId()
        );



        /*
         * =========================================================
         * POSITION
         * =========================================================
         */

        config.set(
                path + ".world",
                location.getWorld().getName()
        );


        config.set(
                path + ".x",
                location.getX()
        );


        config.set(
                path + ".y",
                location.getY()
        );


        config.set(
                path + ".z",
                location.getZ()
        );


        config.set(
                path + ".yaw",
                location.getYaw()
        );


        config.set(
                path + ".pitch",
                location.getPitch()
        );



        /*
         * =========================================================
         * ALTE UUID-DATEN ENTFERNEN
         * =========================================================
         *
         * Falls aus einer älteren Version noch eine Entity-UUID
         * vorhanden ist, wird sie nicht mehr verwendet.
         */

        config.set(
                path + ".entity",
                null
        );



        save();

    }



    /*
     * =============================================================
     * NPC POSITION AUS RUNTIME-ENTITY SPEICHERN
     * =============================================================
     *
     * Komfortmethode für bestehende Aufrufer.
     *
     * Die Entity wird nur einmalig aufgelöst und anschließend
     * die Position über saveNPC(npc, location) gespeichert.
     * =============================================================
     */

    public void saveNPC(
            QuestNPC npc
    ) {


        if (npc == null
                || npc.getEntityId() == null) {

            return;

        }



        org.bukkit.entity.Entity entity =
                org.bukkit.Bukkit.getEntity(
                        npc.getEntityId()
                );


        if (entity == null
                || entity.isDead()) {

            return;

        }



        saveNPC(
                npc,
                entity.getLocation()
        );

    }



    /*
     * =============================================================
     * GESPEICHERTE NPC-POSITION LADEN
     * =============================================================
     */

    public Location getSavedLocation(
            String npcId
    ) {


        if (npcId == null
                || npcId.isEmpty()) {

            return null;

        }



        String path =
                "npcs." + npcId;



        if (!config.contains(
                path + ".world"
        )) {

            return null;

        }



        String worldName =
                config.getString(
                        path + ".world"
                );


        if (worldName == null
                || worldName.isEmpty()) {

            return null;

        }



        World world =
                org.bukkit.Bukkit.getWorld(
                        worldName
                );


        if (world == null) {

            return null;

        }



        double x =
                config.getDouble(
                        path + ".x"
                );


        double y =
                config.getDouble(
                        path + ".y"
                );


        double z =
                config.getDouble(
                        path + ".z"
                );


        float yaw =
                (float) config.getDouble(
                        path + ".yaw"
                );


        float pitch =
                (float) config.getDouble(
                        path + ".pitch"
                );



        return new Location(
                world,
                x,
                y,
                z,
                yaw,
                pitch
        );

    }



    /*
     * =============================================================
     * GESPEICHERTE NPCs LADEN
     * =============================================================
     *
     * Wichtig:
     *
     * Hier wird ausschließlich geprüft, welche NPC-IDs
     * persistent vorhanden sind.
     *
     * Es wird KEINE alte Entity-UUID übernommen.
     *
     * Der tatsächliche Entity-Spawn erfolgt im
     * QuestNPCSpawnService.
     * =============================================================
     */

    public void loadNPCs() {


        ConfigurationSection section =
                config.getConfigurationSection(
                        "npcs"
                );


        if (section == null) {

            return;

        }



        for (String id :
                section.getKeys(false)) {


            QuestNPC npc =
                    QuestNPCRegistry.getNPC(
                            id
                    );


            if (npc == null) {

                continue;

            }



            /*
             * =====================================================
             * RUNTIME STATE ZURÜCKSETZEN
             * =====================================================
             */

            npc.setEntityId(
                    null
            );


            QuestNPCRegistry.registerNPC(
                    npc
            );

        }

    }



    /*
     * =============================================================
     * NPC GESPEICHERT?
     * =============================================================
     */

    public boolean hasSavedNPC(
            String npcId
    ) {


        if (npcId == null
                || npcId.isEmpty()) {

            return false;

        }



        return config.contains(
                "npcs." + npcId
        );

    }



    /*
     * =============================================================
     * NPC DATEN LÖSCHEN
     * =============================================================
     *
     * Entfernt die persistente Definition.
     *
     * Wird NICHT vom normalen Nearby-Remove verwendet.
     * =============================================================
     */

    public void deleteNPC(
            String npcId
    ) {


        if (npcId == null
                || npcId.isEmpty()) {

            return;

        }

        reloadFromDisk();

        config.set(
                "npcs." + npcId,
                null
        );


        save();

    }



    /*
     * =============================================================
     * SPEICHERN
     * =============================================================
     */

    private void save() {


        try {

            config.save(
                    file
            );

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

    /** Drop in-memory state and load {@code npcs.yml} from disk again. */
    public void reloadFromDisk() {
        try {
            config.load(file);
        } catch (Exception e) {
            // Keep current memory map if disk is briefly unreadable mid-write.
        }
    }

}