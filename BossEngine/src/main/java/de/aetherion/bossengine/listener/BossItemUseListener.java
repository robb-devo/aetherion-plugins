package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.item.BossSpawnItemService;
import de.aetherion.bossengine.item.SpawnItemDefinition;
import de.aetherion.bossengine.item.SpawnItemMode;
import de.aetherion.bossengine.manager.BossManager;
import de.aetherion.bossengine.manager.SpawnerManager;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossItemUseListener implements Listener {

    private final BossManager bossManager;
    private final SpawnerManager spawnerManager;
    private final BossSpawnItemService items;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public BossItemUseListener(
            BossManager bossManager,
            SpawnerManager spawnerManager,
            BossSpawnItemService items
    ) {
        this.bossManager = bossManager;
        this.spawnerManager = spawnerManager;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (PINNACLE_KEY.equals(aetherionItemId(item))) {
            usePinnacleKey(event, item);
            return;
        }
        Optional<SpawnItemDefinition> definitionOpt = items.fromItem(item);
        if (definitionOpt.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        SpawnItemDefinition definition = definitionOpt.get();

        if (!player.hasPermission("bossengine.spawn") && !player.hasPermission("bossengine.admin")) {
            player.sendMessage(TextUtil.component("&cYou cannot summon bosses."));
            return;
        }

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < definition.getCooldownSeconds() * 1000L) {
            player.sendMessage(TextUtil.component("&cSpawn item is still cooling down."));
            return;
        }

        Location useLocation = event.getClickedBlock() == null
                ? player.getLocation()
                : event.getClickedBlock().getLocation().add(0.5, 1, 0.5);

        if (definition.getMode() == SpawnItemMode.SET_SPAWN) {
            if (event.getClickedBlock() == null) {
                player.sendMessage(TextUtil.component("&cRight-click a block to set the cave spawn."));
                return;
            }
            boolean saved = spawnerManager.setStationarySpawn(definition.getBossId(), useLocation);
            if (!saved) {
                player.sendMessage(TextUtil.component("&cCould not save that spawn point."));
                return;
            }
            int surface = useLocation.getWorld().getHighestBlockYAt(
                    useLocation.getBlockX(),
                    useLocation.getBlockZ(),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES
            );
            if (useLocation.getBlockY() + 5 >= surface) {
                player.sendMessage(TextUtil.component("&eLooks like the surface. This boss is meant for caves – the point is still saved."));
            }
            player.sendMessage(TextUtil.component(
                    "&aSpawn locked for &f" + definition.getBossId()
                            + " &aat &f"
                            + useLocation.getBlockX() + " "
                            + useLocation.getBlockY() + " "
                            + useLocation.getBlockZ()
                            + "&a. He stays leashed here."
            ));
            cooldowns.put(player.getUniqueId(), now);
            return;
        }

        boolean spawned;
        if (definition.isRequireAltar()) {
            Optional<BossSpawner> altar = spawnerManager.findAltar(definition.getId(), useLocation);
            if (altar.isEmpty()) {
                player.sendMessage(TextUtil.component("&cUse this core at the matching altar."));
                return;
            }
            spawned = altar.get().trySpawn(SpawnCause.ITEM, player);
        } else {
            spawned = bossManager.spawn(
                    definition.getBossId(),
                    useLocation,
                    SpawnCause.ITEM,
                    player
            ).isPresent();
        }

        if (!spawned) {
            player.sendMessage(TextUtil.component("&cThe boss could not spawn (already alive, missing world, or cancelled)."));
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        if (definition.isConsume() && item != null) {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendMessage(TextUtil.component("&aSummoned &r" + definition.getBossId() + "&a."));
    }

    /*
     * P6 pinnacle key — the Items-crafted "aetherion_summons". A player item, so no bossengine.spawn
     * permission: it calls the open-world Aetherion raid at its anchor (spawner "aetherion_home", set
     * with /boss give aetherion_anchor) and nowhere else. Consumed only when the raid actually starts.
     */
    private static final String PINNACLE_KEY = "aetherion_summons";
    private static final String PINNACLE_BOSS = "aetherion";
    private static final String PINNACLE_ANCHOR = "aetherion_home";
    private static final double PINNACLE_RANGE = 64.0;

    private static String aetherionItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(de.aetherion.core.AetherKeys.ITEM_ID, PersistentDataType.STRING);
    }

    private void usePinnacleKey(PlayerInteractEvent event, ItemStack item) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < 3_000L) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        Location anchor = spawnerManager.get(PINNACLE_ANCHOR)
                .map(spawner -> spawner.getDefinition().toLocation())
                .orElse(null);
        if (anchor == null || anchor.getWorld() == null) {
            player.sendMessage(TextUtil.component("&5The Summons hums. Nothing answers. "
                    + "&8(No Aetherion anchor on this server yet.)"));
            return;
        }
        if (!anchor.getWorld().equals(player.getWorld())
                || anchor.distanceSquared(player.getLocation()) > PINNACLE_RANGE * PINNACLE_RANGE) {
            player.sendMessage(TextUtil.component("&7Aetherion answers only at the Veil &8("
                    + anchor.getWorld().getName() + " " + anchor.getBlockX() + " " + anchor.getBlockY()
                    + " " + anchor.getBlockZ() + ")&7."));
            return;
        }
        if (!bossManager.getByTemplate(PINNACLE_BOSS).isEmpty()) {
            player.sendMessage(TextUtil.component("&cAetherion is already awake."));
            return;
        }
        boolean spawned = bossManager.spawn(PINNACLE_BOSS, anchor, SpawnCause.ITEM, player).isPresent();
        if (!spawned) {
            player.sendMessage(TextUtil.component("&cThe Veil holds. &7(The raid could not start; nothing was used.)"));
            return;
        }
        if (item != null) {
            item.setAmount(item.getAmount() - 1);
        }
        for (Player nearby : anchor.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(anchor) <= 160.0 * 160.0) {
                nearby.sendMessage(TextUtil.component("&5&l✦ &f" + player.getName()
                        + " &7tore open the Veil. &5Aetherion &7answers."));
            }
        }
    }
}
