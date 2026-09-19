package de.aetherion.quests.npc;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class QuestNPCSpawner {

    public static QuestNPC spawnNPC(QuestNPC npc, Location location) {
        if (npc == null || location == null || location.getWorld() == null) {
            return null;
        }
        Villager host = QuestNpcAppearance.spawnHost(location, npc);
        if (host == null) {
            return null;
        }
        npc.setEntityId(host.getUniqueId());
        return npc;
    }
}
