package de.aetherion.dungeons;

import de.aetherion.dungeons.bridge.DungeonTpsProbe;
import de.aetherion.dungeons.bridge.HubPortalBridge;
import de.aetherion.dungeons.bridge.RemoteServerBridge;
import de.aetherion.dungeons.bridge.TransferSnapshotStore;
import de.aetherion.dungeons.command.DungeonCommand;
import de.aetherion.dungeons.instance.DungeonProgressHud;
import de.aetherion.dungeons.instance.DungeonSession;
import de.aetherion.dungeons.instance.EndlessSchemBuilder;
import de.aetherion.dungeons.instance.InstanceManager;
import de.aetherion.dungeons.instance.LerfingTestBuilder;
import de.aetherion.dungeons.listener.DungeonListener;
import de.aetherion.dungeons.map.DungeonFloorMapRenderer;
import de.aetherion.dungeons.hub.DungeonHubService;
import de.aetherion.dungeons.npc.DungeonKeeperFancyListener;
import de.aetherion.dungeons.npc.DungeonKeeperService;
import de.aetherion.dungeons.npc.DungeonGuideService;
import de.aetherion.dungeons.menu.DungeonGuideGUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class AetherionDungeons extends JavaPlugin {

    private static AetherionDungeons instance;

    private InstanceManager instances;
    private DungeonKeeperService keeper;
    private DungeonGuideService guide;
    private RemoteServerBridge remote;
    private TransferSnapshotStore snapshots;
    private HubPortalBridge hubPortalBridge;
    private DungeonTpsProbe tpsProbe;
    private DungeonHubService hubService;
    private de.aetherion.core.api.DungeonAccess dungeonAccess;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        snapshots = new TransferSnapshotStore(this);
        remote = new RemoteServerBridge(this, snapshots);
        tpsProbe = new DungeonTpsProbe(this);
        instances = new InstanceManager(this);
        keeper = new DungeonKeeperService(this);
        guide = new DungeonGuideService(this);

        instances.purgeLeftoverWorlds();
        LerfingTestBuilder.ensureStructures(this);
        EndlessSchemBuilder.ensureSchematic(this);
        instances.startWarmPool();
        tpsProbe.start();

        getServer().getPluginManager().registerEvents(new DungeonListener(this, instances, keeper, guide), this);
        hubPortalBridge = new HubPortalBridge(this, remote, snapshots);
        getServer().getPluginManager().registerEvents(hubPortalBridge, this);
        hubService = new DungeonHubService(this, remote);
        hubService.start();
        DungeonKeeperFancyListener.register(this);
        new DungeonGuideGUI(this);
        DungeonProgressHud.start(this, instances);

        DungeonCommand command = new DungeonCommand(instances, keeper, guide, remote);
        var dungeon = getCommand("dungeon");
        if (dungeon != null) {
            dungeon.setExecutor(command);
            dungeon.setTabCompleter(command);
        }
        var dhub = getCommand("dhub");
        if (dhub != null) {
            dhub.setExecutor((sender, cmd, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!sendToDungeonHub(player)) {
                    player.sendMessage("§cCould not reach the dungeon hub.");
                }
                return true;
            });
        }

        getServer().getScheduler().runTaskLater(this, keeper::load, 40L);
        getServer().getScheduler().runTaskLater(this, guide::load, 45L);
        getLogger().info("AetherionDungeons enabled. Temporary instances only.");
        dungeonAccess = new de.aetherion.dungeons.api.DungeonAccessImpl(this);
        de.aetherion.core.api.AetherServices.registerDungeons(dungeonAccess);
    }

    @Override
    public void onDisable() {
        if (tpsProbe != null) {
            tpsProbe.stop();
        }
        DungeonProgressHud.stop();
        if (instances != null) {
            instances.shutdown();
        }
        if (dungeonAccess != null) {
            de.aetherion.core.api.AetherServices.clearDungeons(dungeonAccess);
            dungeonAccess = null;
        }
        instance = null;
        getLogger().info("AetherionDungeons disabled.");
    }

    public static AetherionDungeons getInstance() {
        return instance;
    }

    public InstanceManager getInstances() {
        return instances;
    }

    public DungeonKeeperService getKeeper() {
        return keeper;
    }

    public DungeonGuideService getGuide() {
        return guide;
    }

    public de.aetherion.dungeons.hub.DungeonHubService getHubService() {
        return hubService;
    }

    public RemoteServerBridge getRemote() {
        return remote;
    }

    public void openDungeonMap(Player player) {
        if (player == null || instances == null) {
            return;
        }
        var session = instances.sessionOf(player);
        if (session == null) {
            return;
        }
        de.aetherion.dungeons.menu.DungeonMapGUI.open(player, session);
    }

    public ItemStack createDungeonMap(Player player) {
        if (player == null || instances == null) {
            return null;
        }
        DungeonSession session = instances.sessionOf(player);
        if (session == null || session.world() == null) {
            return null;
        }
        MapView view = session.mapView();
        if (view == null) {
            view = Bukkit.createMap(session.world());
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new DungeonFloorMapRenderer(session));
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            session.setMapView(view);
        }
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        if (!(item.getItemMeta() instanceof MapMeta meta)) {
            return item;
        }
        meta.setMapView(view);
        meta.setDisplayName("§5§lDungeon Map");
        meta.setLore(List.of(
                "§7Hold it to read the floor.",
                "§aGreen §7cleared  §cRed §7not yet",
                "§5Purple §7boss  §fPointer §7you",
                "",
                "§8Sneak + click for the list view"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(
                new NamespacedKey("aetherionitems", "manager"),
                PersistentDataType.BYTE,
                (byte) 1
        );
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDungeonKeeperAnchor() {
        return DungeonKeeperService.createAnchor();
    }

    public void despawnDungeonKeeper() {
        if (keeper != null) {
            keeper.despawnAll();
        }
    }

    public void startTest(Player player, boolean bossOnly) {
        startTest(player, bossOnly, 1);
    }

    public void startTest(Player player, boolean bossOnly, int floor) {
        if (player == null) {
            return;
        }
        // Dev Menu on mmo-r: snapshot gear + pending floor, then Velocity to mmo-d which auto-enters.
        if (remote != null && remote.remoteDungeonsEnabled()) {
            remote.transferToDungeon(player, floor, bossOnly);
            return;
        }
        if (instances != null) {
            instances.enterPrototype(player, bossOnly, floor);
        }
    }

    public boolean sendToDungeonHub(Player player) {
        if (hubPortalBridge != null) {
            return hubPortalBridge.sendToDungeonHub(player);
        }
        return false;
    }

    public org.bukkit.Location dungeonHubLocation() {
        return hubPortalBridge == null ? null : hubPortalBridge.dungeonHubLocation();
    }
}
