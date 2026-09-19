package de.aetherion.dungeons.api;

import de.aetherion.core.api.DungeonAccess;
import de.aetherion.dungeons.AetherionDungeons;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class DungeonAccessImpl implements DungeonAccess {

    private final AetherionDungeons plugin;

    public DungeonAccessImpl(AetherionDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack dungeonKeeperAnchor() {
        return plugin.createDungeonKeeperAnchor();
    }

    @Override
    public boolean startTest(Player player, boolean bossOnly, int floor) {
        if (player == null) {
            return false;
        }
        plugin.startTest(player, bossOnly, floor);
        return true;
    }

    @Override
    public boolean despawnKeeper(Entity entity) {
        if (entity == null || plugin == null) {
            return false;
        }
        boolean keeper = entity.getScoreboardTags().contains("dungeon_keeper");
        String name = entity.getCustomName();
        if (name != null && name.contains("Dungeon Keeper")) {
            keeper = true;
        }
        String tagged = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_npc"),
                PersistentDataType.STRING
        );
        if ("dungeon_keeper".equals(tagged)) {
            keeper = true;
        }
        if (!keeper) {
            return false;
        }
        plugin.despawnDungeonKeeper();
        return true;
    }
}
