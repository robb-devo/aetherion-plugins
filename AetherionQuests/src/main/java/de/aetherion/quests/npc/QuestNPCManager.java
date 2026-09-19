package de.aetherion.quests.npc;

import java.util.HashMap;
import java.util.Map;

public class QuestNPCManager {

    private final Map<String, QuestNPC> npcs = new HashMap<>();


    public void registerNPC(QuestNPC npc) {

        npcs.put(npc.getId(), npc);

    }


    public QuestNPC getNPC(String id) {

        return npcs.get(id);

    }


    public boolean exists(String id) {

        return npcs.containsKey(id);

    }

}
