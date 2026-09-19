package de.aetherion.quests.npc;

import java.util.UUID;

public class QuestNPC {


    private final String id;

    private final String name;

    private final NPCType type;


    private final String questId;

    private final String dialogId;

    private final String serviceId;


    private UUID entityId;



    public QuestNPC(
            String id,
            String name,
            NPCType type,
            String questId,
            String dialogId,
            String serviceId
    ) {


        this.id = id;

        this.name = name;

        this.type = type;

        this.questId = questId;

        this.dialogId = dialogId;

        this.serviceId = serviceId;

    }



    public String getId() {

        return id;

    }



    public String getName() {

        return name;

    }



    public NPCType getType() {

        return type;

    }



    public String getQuestId() {

        return questId;

    }



    public String getDialogId() {

        return dialogId;

    }



    public String getServiceId() {

        return serviceId;

    }



    public UUID getEntityId() {

        return entityId;

    }



    public void setEntityId(UUID entityId) {

        this.entityId = entityId;

    }

}
