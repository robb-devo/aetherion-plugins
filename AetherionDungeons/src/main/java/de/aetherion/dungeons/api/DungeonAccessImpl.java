package de.aetherion.dungeons.api;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.DungeonAccess;
import de.aetherion.core.network.MainWorldLink;
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

    @Override
    public boolean needsMainWorld(Player player) {
        MainWorldLink link = link();
        return link != null && !link.isMainWorld();
    }

    @Override
    public void leaveInstance(Player player) {
        if (player == null || plugin == null || plugin.getInstances() == null) {
            return;
        }
        var instances = plugin.getInstances();
        if (instances.sessionOf(player) != null || instances.isDungeonWorld(player.getWorld())) {
            instances.leave(player, false);
        }
    }

    @Override
    public boolean transferToMainSpawn(Player player, String spawnId) {
        MainWorldLink link = link();
        if (link == null || player == null) {
            return false;
        }
        return link.handoff(player, spawnId);
    }

    private static MainWorldLink link() {
        AetherionCore core = AetherionCore.get();
        return core == null ? null : core.link();
    }
}
