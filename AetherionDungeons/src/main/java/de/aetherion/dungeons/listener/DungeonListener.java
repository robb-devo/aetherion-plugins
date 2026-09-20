package de.aetherion.dungeons.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.dungeons.AetherionDungeons;
import de.aetherion.dungeons.bridge.BossEngineBridge;
import de.aetherion.dungeons.instance.AshesEncounter;
import de.aetherion.dungeons.instance.DungeonLootFx;
import de.aetherion.dungeons.instance.DungeonProgressHud;
import de.aetherion.dungeons.instance.EndlessEncounter;
import de.aetherion.dungeons.instance.InstanceManager;
import de.aetherion.dungeons.instance.PrototypeDungeonBuilder;
import de.aetherion.dungeons.menu.DungeonMapGUI;
import de.aetherion.dungeons.menu.DungeonMenu;
import de.aetherion.dungeons.npc.DungeonGuideService;
import de.aetherion.dungeons.npc.DungeonKeeperService;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonListener implements Listener {

    private final AetherionDungeons plugin;
    private final InstanceManager instances;
    private final DungeonKeeperService keeper;
    private final DungeonGuideService guide;
    private final Map<UUID, Long> menuCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> readyCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gateCooldown = new ConcurrentHashMap<>();

    public DungeonListener(AetherionDungeons plugin, InstanceManager instances, DungeonKeeperService keeper) {
        this(plugin, instances, keeper, null);
    }

    public DungeonListener(AetherionDungeons plugin, InstanceManager instances,
                           DungeonKeeperService keeper, DungeonGuideService guide) {
        this.plugin = plugin;
        this.instances = instances;
        this.keeper = keeper;
        this.guide = guide;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("aetherion.dungeon.admin")) {
            return;
        }
        if (DungeonGuideService.isAnchor(player.getInventory().getItemInMainHand()) && guide != null) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                guide.despawnAll();
                player.sendMessage("§eDespawned §f" + DungeonGuideService.DISPLAY_NAME + "§e.");
                return;
            }
            if (action != Action.RIGHT_CLICK_BLOCK) {
                player.sendMessage("§cRight-click a block to place the Scribe.");
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            var location = block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            location.setYaw(player.getLocation().getYaw());
            location.setPitch(0f);
            guide.spawnAt(location);
            player.sendMessage("§aAnchored §f" + DungeonGuideService.DISPLAY_NAME + "§a.");
            return;
        }
        if (!DungeonKeeperService.isAnchor(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            keeper.despawnAll();
            player.sendMessage("§eDespawned §f" + DungeonKeeperService.DISPLAY_NAME + "§e. Place the anchor again to move him.");
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            player.sendMessage("§cRight-click a block to place him, or sneak to despawn.");
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        var location = block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0f);
        keeper.spawnAt(location);
        player.sendMessage("§aAnchored §f" + DungeonKeeperService.DISPLAY_NAME + "§a. Players can click him to open a dungeon.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (guide != null && DungeonGuideService.isGuide(plugin, event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("aetherion.dungeon.admin")
                    && DungeonGuideService.isAnchor(event.getPlayer().getInventory().getItemInMainHand())) {
                guide.despawnAll();
                event.getPlayer().sendMessage("§eDespawned §f" + DungeonGuideService.DISPLAY_NAME + "§e.");
                return;
            }
            guide.openFor(event.getPlayer());
            return;
        }
        if (!DungeonKeeperService.isKeeper(plugin, event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (tryDespawnKeeper(event.getPlayer())) {
            return;
        }
        openKeeperMenu(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcClickAt(PlayerInteractAtEntityEvent event) {
        if (guide != null && DungeonGuideService.isGuide(plugin, event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                guide.openFor(event.getPlayer());
            }
            return;
        }
        if (!DungeonKeeperService.isKeeper(plugin, event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (tryDespawnKeeper(event.getPlayer())) {
            return;
        }
        openKeeperMenu(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onReadyClick(PlayerInteractEntityEvent event) {
        if (!PrototypeDungeonBuilder.isReadyNpc(plugin, event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        markReady(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onReadyClickAt(PlayerInteractAtEntityEvent event) {
        if (!PrototypeDungeonBuilder.isReadyNpc(plugin, event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        markReady(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (PrototypeDungeonBuilder.isReadyNpc(plugin, event.getEntity())) {
            event.setCancelled(true);
            markReady(player);
            return;
        }
        if (!DungeonKeeperService.isKeeper(plugin, event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (tryDespawnKeeper(player)) {
            return;
        }
        openKeeperMenu(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLookClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (DungeonKeeperService.isAnchor(event.getItem())) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && DungeonLootFx.isLootChest(event.getClickedBlock())) {
            event.setCancelled(true);
            instances.tryLootChest(event.getPlayer(), event.getClickedBlock());
            return;
        }
        Entity target = event.getPlayer().getTargetEntity(5);
        if (PrototypeDungeonBuilder.isReadyNpc(plugin, target)) {
            event.setCancelled(true);
            markReady(event.getPlayer());
            return;
        }
        if (!DungeonKeeperService.isKeeper(plugin, target)) {
            return;
        }
        event.setCancelled(true);
        if (tryDespawnKeeper(event.getPlayer())) {
            return;
        }
        openKeeperMenu(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTradeOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.CHEST
                && event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest
                && DungeonLootFx.isLootChest(chest.getBlock())) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player) {
                instances.tryLootChest(player, chest.getBlock());
            }
            return;
        }
        if (event.getInventory().getType() != InventoryType.MERCHANT) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Entity trader = null;
        if (event.getInventory().getHolder() instanceof Entity entity) {
            trader = entity;
        }
        if (event.getInventory() instanceof MerchantInventory merchantInventory) {
            Merchant merchant = merchantInventory.getMerchant();
            if (merchant instanceof Entity entity) {
                trader = entity;
            }
        }
        if (PrototypeDungeonBuilder.isReadyNpc(plugin, trader)) {
            event.setCancelled(true);
            markReady(player);
            return;
        }
        if (!DungeonKeeperService.isKeeper(plugin, trader)) {
            return;
        }
        event.setCancelled(true);
        openKeeperMenu(player);
    }

    private boolean tryDespawnKeeper(Player player) {
        if (player == null || !player.isSneaking()) {
            return false;
        }
        if (!player.hasPermission("aetherion.dungeon.admin")) {
            return false;
        }
        if (!DungeonKeeperService.isAnchor(player.getInventory().getItemInMainHand())) {
            return false;
        }
        keeper.despawnAll();
        player.sendMessage("§eDespawned §f" + DungeonKeeperService.DISPLAY_NAME + "§e. Place the anchor again to move him.");
        return true;
    }

    private void markReady(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = readyCooldown.get(player.getUniqueId());
        if (last != null && now - last < 250L) {
            return;
        }
        readyCooldown.put(player.getUniqueId(), now);
        instances.markReady(player);
    }

    private void openKeeperMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!player.hasPermission("aetherion.dungeon.use")) {
            player.sendMessage("§cYou cannot enter dungeons.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = menuCooldown.get(player.getUniqueId());
        if (last != null && now - last < 250L) {
            return;
        }
        menuCooldown.put(player.getUniqueId(), now);
        player.sendMessage("§5Dungeon Keeper§7: The void's hungry. Pick a floor.");
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                DungeonMenu.open(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        boolean dungeonMenu = event.getView().getTopInventory().getHolder() instanceof DungeonMenu.Holder;
        boolean dungeonMap = event.getView().getTopInventory().getHolder() instanceof DungeonMapGUI.Holder;
        if (!dungeonMenu && !dungeonMap) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (dungeonMap) {
            if (event.getRawSlot() == DungeonMapGUI.CLOSE_SLOT) {
                player.closeInventory();
            }
            return;
        }
        if (event.getRawSlot() == DungeonMenu.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == DungeonMenu.ENTER_SLOT) {
            player.closeInventory();
            var remote = plugin.getRemote();
            if (remote != null && remote.remoteDungeonsEnabled()) {
                remote.transferToDungeon(player);
                return;
            }
            instances.enterPrototype(player, false, 1);
            return;
        }
        if (event.getRawSlot() == DungeonMenu.LOCKED_SLOT) {
            player.closeInventory();
            var remote = plugin.getRemote();
            if (remote != null && remote.remoteDungeonsEnabled()) {
                remote.transferToDungeon(player);
                return;
            }
            instances.enterPrototype(player, false, 2);
            return;
        }
        if (event.getRawSlot() == DungeonMenu.FLOOR3_SLOT) {
            player.closeInventory();
            var remote = plugin.getRemote();
            if (remote != null && remote.remoteDungeonsEnabled()) {
                remote.transferToDungeon(player);
                return;
            }
            instances.enterPrototype(player, false, 3);
            return;
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DungeonMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof DungeonMapGUI.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortalTravel(PlayerPortalEvent event) {
        if (!instances.isDungeonWorld(event.getPlayer().getWorld())) {
            return;
        }
        event.setCancelled(true);
        instances.tryPortalExit(event.getPlayer());
    }

    @EventHandler
    public void onDungeonMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!instances.isDungeonWorld(entity.getWorld())) {
            return;
        }
        if (BossEngineBridge.isEngineBoss(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            instances.onBossKilled(entity.getWorld());
            return;
        }
        EndlessEncounter.onMobDeath(plugin, entity);
        AshesEncounter.onMobDeath(plugin, entity);
        if (PrototypeDungeonBuilder.isHelperEntity(entity)) {
            instances.scanCombatRooms(entity.getWorld());
            return;
        }
        String id = entity.getPersistentDataContainer().get(
                AetherKeys.DUNGEON_MOB,
                PersistentDataType.STRING
        );
        if (id == null) {
            instances.scanCombatRooms(entity.getWorld());
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if ("dungeon_sentinel".equals(id) || "dungeon_frostbound".equals(id) || "dungeon_aetherion".equals(id)) {
            instances.onBossKilled(entity.getWorld());
            return;
        }
        instances.scanCombatRooms(entity.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonMobHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (!instances.isDungeonWorld(entity.getWorld())) {
            return;
        }
        if (BossEngineBridge.isEngineBoss(entity)) {
            return;
        }
        if (PrototypeDungeonBuilder.isHelperEntity(entity)) {
            return;
        }
        Double hp = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_hp"),
                PersistentDataType.DOUBLE
        );
        if (hp == null) {
            return;
        }
        Double max = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_max_hp"),
                PersistentDataType.DOUBLE
        );
        if (max == null || max <= 0.0) {
            max = hp;
        }
        double incoming = event.getDamage();
        if (incoming <= 0.0) {
            return;
        }
        double next = hp - incoming;
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_hp"),
                PersistentDataType.DOUBLE,
                next
        );
        if (next <= 0.0) {
            event.setDamage(Math.max(entity.getHealth() + 1.0, 50.0));
            plugin.getServer().getScheduler().runTask(plugin, () -> finishDungeonKill(entity));
            return;
        }
        event.setDamage(0);
        double shown = Math.max(1.0, Math.min(1024.0, 1024.0 * (next / max)));
        try {
            entity.setHealth(shown);
        } catch (IllegalArgumentException ignored) {
        }
        PrototypeDungeonBuilder.refreshDungeonNameplate(plugin, entity);
    }

    private void finishDungeonKill(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        World world = entity.getWorld();
        if (entity.isValid() && !entity.isDead()) {
            try {
                entity.setNoDamageTicks(0);
                entity.setHealth(0);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (entity.isValid() && !entity.isDead()) {
            entity.remove();
        }
        if (world != null) {
            instances.scanCombatRooms(world);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        org.bukkit.Location saved = keeper.getSaved();
        for (Entity entity : event.getChunk().getEntities()) {
            if (!DungeonKeeperService.isKeeper(plugin, entity)) {
                continue;
            }
            if (saved == null || saved.getWorld() == null || !saved.getWorld().equals(entity.getWorld())) {
                entity.remove();
                continue;
            }
            if (entity.getLocation().distanceSquared(saved) > 16) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var session = instances.sessionOf(event.getPlayer());
        if (session != null) {
            java.util.UUID id = event.getPlayer().getUniqueId();
            plugin.getServer().getScheduler().runTask(plugin, () -> instances.leaveSession(id, session, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!instances.isDungeonWorld(event.getFrom())) {
            return;
        }
        if (instances.isDungeonWorld(event.getPlayer().getWorld())) {
            return;
        }
        DungeonProgressHud.leaveDungeon(event.getPlayer());
        var session = instances.sessionOf(event.getPlayer());
        if (session != null) {
            instances.leave(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!instances.isDungeonWorld(event.getEntity().getWorld())) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        var session = instances.sessionOf(event.getPlayer());
        if (session == null || session.world() == null) {
            return;
        }
        event.setRespawnLocation(session.world().getSpawnLocation());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!instances.isDungeonWorld(player.getWorld())) {
            return;
        }
        var to = event.getTo();
        if (to == null) {
            return;
        }
        if (to.getY() <= 40) {
            var session = instances.sessionOf(player);
            if (session != null && EndlessEncounter.isEndless(session)) {
                if (!EndlessEncounter.tryWaterRescue(player)) {
                    player.teleport(player.getWorld().getSpawnLocation());
                    player.sendMessage("§7The void spat you back to the entrance.");
                }
                return;
            }
            if (session != null && session.bossReleased() && session.layout() != null) {
                var boss = session.layout().boss();
                player.teleport(new org.bukkit.Location(
                        player.getWorld(),
                        boss.centerX() + 0.5,
                        PrototypeDungeonBuilder.FLOOR_Y + 1,
                        boss.centerZ() + 0.5
                ));
                player.sendMessage("§7The void spat you back onto the arena.");
                return;
            }
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§7The void spat you back to the entrance.");
            return;
        }
        var endlessSession = instances.sessionOf(player);
        if (endlessSession != null && EndlessEncounter.isEndless(endlessSession)) {
            if (EndlessEncounter.tryWaterRescue(player)) {
                return;
            }
        }
        if (to.getBlock().getType() == Material.NETHER_PORTAL
                || to.clone().add(0, 1, 0).getBlock().getType() == Material.NETHER_PORTAL) {
            instances.tryPortalExit(player);
            return;
        }
        var session = instances.sessionOf(player);
        if (session == null) {
            return;
        }
        // Endless / template floors: physical walls only — no soft room bounce.
        if (EndlessEncounter.isEndless(session)
                || (session.layout() != null && session.layout().templateFloor())) {
            return;
        }
        if (!session.started()) {
            int gateZ = session.layout() != null ? session.layout().gateZ() : PrototypeDungeonBuilder.GATE_Z;
            if (to.getZ() <= gateZ + 0.45 && Math.abs(to.getX()) <= 6) {
                return;
            }
            bounceToSpawn(event, player, to, "§7The gate is sealed. Talk to the §eGate Warden §7to ready up.");
            return;
        }
        if (session.bossReleased() || session.layout() == null) {
            return;
        }
        int x = to.getBlockX();
        int z = to.getBlockZ();
        if (session.layout().allows(x, z, session.unlockedRooms(), session.clearedRooms(), true, false)) {
            return;
        }
        org.bukkit.Location back = session.layout().bounceTarget(
                to.getWorld(),
                x,
                z,
                session.unlockedRooms(),
                session.clearedRooms(),
                true
        );
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
        long now = System.currentTimeMillis();
        Long last = gateCooldown.get(player.getUniqueId());
        if (last != null && now - last < 1500L) {
            return;
        }
        gateCooldown.put(player.getUniqueId(), now);
        player.sendMessage("§7Clear the open chambers before you push deeper.");
    }

    private void bounceToSpawn(org.bukkit.event.player.PlayerMoveEvent event, Player player, org.bukkit.Location to, String message) {
        var spawn = player.getWorld().getSpawnLocation();
        event.setTo(new org.bukkit.Location(to.getWorld(), spawn.getX(), spawn.getY(), spawn.getZ(), to.getYaw(), to.getPitch()));
        long now = System.currentTimeMillis();
        Long last = gateCooldown.get(player.getUniqueId());
        if (last != null && now - last < 1500L) {
            return;
        }
        gateCooldown.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (instances.isDungeonWorld(event.getBlock().getWorld()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (instances.isDungeonWorld(event.getBlock().getWorld()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }
}
