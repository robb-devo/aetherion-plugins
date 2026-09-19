package de.aetherion.quests.api;

import de.aetherion.core.api.QuestNpcInfo;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.bridge.QuestProgressBridge;
import de.aetherion.quests.listener.ExploreChestListener;
import de.aetherion.quests.listener.MerchantChestListener;
import de.aetherion.quests.listener.NpcAnchorListener;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.ui.QuestProgressDisplay;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Typed Quests surface. Delegates to existing bridge/display/registry methods.
 */
public final class QuestProgressAccessImpl implements QuestProgressAccess {

    private final AetherionQuests plugin;

    public QuestProgressAccessImpl(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public void noteBroken(Player player, Material material, int amount) {
        QuestProgressBridge.noteBroken(player, material, amount);
    }

    @Override
    public void noteCollected(Player player, Material material, int amount) {
        QuestProgressBridge.noteCollected(player, material, amount);
    }

    @Override
    public void noteInventoryGain(Player player) {
        QuestProgressBridge.noteInventoryGain(player);
    }

    @Override
    public void noteUsed(Player player, String target) {
        QuestProgressBridge.noteUsed(player, target);
    }

    @Override
    public void noteCrafted(Player player, String target, int amount) {
        QuestProgressBridge.noteCrafted(player, target, amount);
    }

    @Override
    public boolean isQuestActive(Player player, String questId) {
        return QuestProgressBridge.isQuestActive(player, questId);
    }

    @Override
    public boolean isQuestCompleted(Player player, String questId) {
        return QuestProgressBridge.isQuestCompleted(player, questId);
    }

    @Override
    public boolean shouldGuidePetsEquip(Player player) {
        return QuestProgressBridge.shouldGuidePetsEquip(player);
    }

    @Override
    public boolean canChopTrees(Player player) {
        return QuestProgressBridge.canChopTrees(player);
    }

    @Override
    public void unlockForagerChop(Player player) {
        QuestProgressBridge.unlockForagerChop(player);
    }

    @Override
    public void suppress(Player player) {
        QuestProgressDisplay.suppress(player);
    }

    @Override
    public void unsuppress(Player player) {
        QuestProgressDisplay.unsuppress(player);
    }

    @Override
    public void unsuppress(UUID playerId) {
        QuestProgressDisplay.unsuppress(playerId);
    }

    @Override
    public void flushPlayer(Player player) {
        if (player == null || plugin == null || plugin.getPlayerQuestStorage() == null) {
            return;
        }
        plugin.getPlayerQuestStorage().flushPlayer(player.getUniqueId());
    }

    @Override
    public List<QuestNpcInfo> npcs() {
        List<QuestNpcInfo> out = new ArrayList<>();
        Map<String, QuestNPC> all = QuestNPCRegistry.getAll();
        if (all == null || all.isEmpty()) {
            return out;
        }
        for (QuestNPC npc : all.values()) {
            if (npc == null) {
                continue;
            }
            String id = npc.getId();
            ItemStack icon = NpcAnchorListener.create(id);
            UUID entityId = npc.getEntityId();
            out.add(new QuestNpcInfo(
                    id,
                    npc.getName(),
                    npc.getQuestId(),
                    npc.getType() == null ? null : npc.getType().toString(),
                    entityId == null ? null : entityId.toString(),
                    icon
            ));
        }
        return out;
    }

    @Override
    public ItemStack npcAnchor(String npcId) {
        return NpcAnchorListener.create(npcId);
    }

    @Override
    public ItemStack merchantChest() {
        return MerchantChestListener.create();
    }

    @Override
    public ItemStack exploreChest(String kind) {
        return ExploreChestListener.create(kind);
    }

    @Override
    public String despawnNpc(Entity entity) {
        if (entity == null || plugin == null) {
            return null;
        }
        String npcId = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "quest_npc"),
                PersistentDataType.STRING
        );
        if (npcId == null || npcId.isBlank()) {
            npcId = entity.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "quest_npc_name"),
                    PersistentDataType.STRING
            );
        }
        if (npcId == null || npcId.isBlank()) {
            npcId = entity.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "quest_marker_npc"),
                    PersistentDataType.STRING
            );
        }
        if (npcId == null || npcId.isBlank()) {
            QuestNPC byEntity = QuestNPCRegistry.getNPCByEntityId(entity.getUniqueId().toString());
            if (byEntity != null) {
                npcId = byEntity.getId();
            }
        }
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String name = npcId;
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null) {
            name = npc.getName();
        }
        if (!plugin.despawnQuestNpc(npcId)) {
            return null;
        }
        return name;
    }

    @Override
    public boolean openNpcEditor(Player player) {
        if (player == null || plugin == null || plugin.getNpcEditor() == null) {
            return false;
        }
        if (!de.aetherion.quests.editor.NpcEditor.allowed(player)) {
            return false;
        }
        plugin.getNpcEditor().openMain(player);
        return true;
    }
}
