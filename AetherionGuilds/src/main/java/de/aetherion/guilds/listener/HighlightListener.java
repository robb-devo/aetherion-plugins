package de.aetherion.guilds.listener;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.island.UnlockService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.menu.BuildMenu;
import de.aetherion.guilds.menu.GuildProjectMenu;
import de.aetherion.guilds.menu.MachineMenu;
import de.aetherion.guilds.menu.MenuKit;
import de.aetherion.guilds.menu.QuarryMenu;
import de.aetherion.guilds.menu.StorageMenu;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Events for the island highlight: menus, build modes, structure/belt protection, arrival beats. */
public final class HighlightListener implements Listener {

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final UnlockService unlock;
    private final GuildProjectService projects;
    private final BuildMenu build;
    private final StorageMenu storage;
    private final MachineMenu machine;
    private final QuarryMenu quarry;
    private final GuildProjectMenu projectMenu;
    private de.aetherion.guilds.menu.PieceMenu pieceMenu;
    private de.aetherion.guilds.menu.HubMenu hubMenu;

    public void attachHub(de.aetherion.guilds.menu.HubMenu hubMenu) {
        this.hubMenu = hubMenu;
    }

    public HighlightListener(JavaPlugin plugin, HostService hosts, StructureService structures,
                             LogisticsService logistics, PlacementService placement, UnlockService unlock,
                             GuildProjectService projects, MinionService minions, BuildMenu build,
                             StorageMenu storage, MachineMenu machine, QuarryMenu quarry,
                             GuildProjectMenu projectMenu) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
        this.unlock = unlock;
        this.projects = projects;
        this.build = build;
        this.storage = storage;
        this.machine = machine;
        this.quarry = quarry;
        this.projectMenu = projectMenu;
    }

    public void attachPieces(de.aetherion.guilds.menu.PieceMenu pieceMenu) {
        this.pieceMenu = pieceMenu;
    }

    // ------------------------------------------------------------------------------------------------
    // menus
    // ------------------------------------------------------------------------------------------------

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuKit.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        holder.menu().click(player, holder, slot, event.getClick());
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuKit.Holder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // build modes + structure interaction
    // ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            // the client falls through to the off hand after a tool click: never place/use that too
            if (placement.holdingTool(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (placement.holdingTool(player)) {
            event.setCancelled(true);
            placement.onInteract(player, event.getAction().isRightClick(), event.getClickedBlock(), player.isSneaking());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        World world = block.getWorld();
        if (!hosts.isIslandWorld(world)) {
            return;
        }
        ItemStack hand = event.getItem();
        if (player.isSneaking() && hand != null && !hand.getType().isAir()) {
            return;
        }
        // exact: only blocks the structure itself placed are its controls (a player's own barrel next to a
        // quarry stays the player's barrel)
        PlacedStructure structure = structures.owner(block);
        if (structure == null) {
            guildBoard(event, player, block);
            return;
        }
        if (structure.building()) {
            event.setCancelled(true);
            player.sendMessage("§7Still going up…");
            return;
        }
        Material type = block.getType();
        switch (structure.type()) {
            case STORAGE_HUT, DEPOT -> {
                if (type == Material.BARREL || type == Material.CHEST || type == Material.TRAPPED_CHEST
                        || type == Material.COMPOSTER || type == Material.BARRIER || type == Material.HOPPER) {
                    event.setCancelled(true);
                    storage.open(player, structure);
                }
            }
            case MILL, FORGE -> {
                if (type == Material.HOPPER || type == Material.BLAST_FURNACE || type == Material.GRINDSTONE
                        || type == Material.SMITHING_TABLE || type == Material.SMOOTH_STONE || type.name().endsWith("ANVIL")
                        || type == Material.BARRIER) {
                    event.setCancelled(true);
                    machine.open(player, structure);
                }
            }
            case WORKSHOP -> {
                if (type == Material.LECTERN) {
                    event.setCancelled(true);
                    if (hubMenu != null) {
                        hubMenu.open(player, structure.host());
                    } else {
                        build.open(player, structure.host());
                    }
                }
            }
            case QUARRY_HOUSING -> {
                if (type == Material.HOPPER || type == Material.BARREL || type == Material.BARRIER) {
                    event.setCancelled(true);
                    MinionService.QuarryRef ref = quarryRef(structure);
                    if (ref != null) {
                        quarry.open(player, ref);
                    }
                }
            }
            case PROJECT -> {
                if (type == Material.LECTERN) {
                    event.setCancelled(true);
                    projectMenu.open(player);
                }
            }
            case SPLITTER, OVERFLOW, SORTER -> {
                if (pieceMenu != null) {
                    event.setCancelled(true);
                    pieceMenu.open(player, structure);
                }
            }
            default -> {
            }
        }
    }

    private void guildBoard(PlayerInteractEvent event, Player player, Block block) {
        if (block.getType() != Material.LECTERN || !hosts.guildIslands().isGuildWorld(block.getWorld())) {
            return;
        }
        IslandHost host = hosts.at(block.getLocation());
        if (host == null || hosts.starter(host) != StarterLayout.GUILD) {
            return;
        }
        int[] board = StarterLayout.GUILD_BOARD;
        if (block.getX() == hosts.originX(host) + board[0] && block.getY() == HostService.SURFACE_Y + board[1]
                && block.getZ() == hosts.originZ(host) + board[2]) {
            event.setCancelled(true);
            if (hosts.isMember(player, host)) {
                projectMenu.open(player);
            } else {
                player.sendMessage("§7The board lists this guild's projects. Members only.");
            }
        }
    }

    private MinionService.QuarryRef quarryRef(PlacedStructure housing) {
        IslandHost host = housing.host();
        QuarryMinion minion = hosts.minion(host, housing.minionId());
        if (minion == null) {
            return null;
        }
        if (host.isGuild()) {
            Guild guild = hosts.guild(host);
            return guild == null ? null : new MinionService.QuarryRef(guild, null, minion);
        }
        PersonalIsland island = hosts.island(host);
        return island == null ? null : new MinionService.QuarryRef(null, island, minion);
    }

    // ------------------------------------------------------------------------------------------------
    // protection
    // ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!hosts.isIslandWorld(world)) {
            return;
        }
        Player player = event.getPlayer();
        if (placement.holdingTool(player)) {
            event.setCancelled(true);
            return;
        }
        if (logistics.isBelt(world, block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            player.sendMessage("§7That's a conveyor. Take it up with the Belt Layer (left-click it while holding the tool).");
            return;
        }
        if (logistics.isBeltSupport(world, block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            player.sendMessage("§7A conveyor runs on this block.");
            return;
        }
        PlacedStructure structure = structures.owner(block);
        if (structure != null) {
            event.setCancelled(true);
            String label = structure == null ? "structure" : structure.label();
            player.sendMessage(structure != null && structure.type() == StructureType.PROJECT
                    ? "§7That's part of the guild's build. It stays."
                    : "§7Part of your " + label + ". Right-click it, then \"Take it down\" to move it.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!hosts.isIslandWorld(block.getWorld())) {
            return;
        }
        if (placement.holdingTool(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        PlacedStructure structure = structures.at(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (structure != null && structure.building()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.IRON_TRAPDOOR && type != Material.RAIL) {
            return;
        }
        World world = block.getWorld();
        if (hosts.isIslandWorld(world) && logistics.isBelt(world, block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (hosts.isIslandWorld(event.getLocation().getWorld())) {
            event.blockList().removeIf(this::isProtected);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (hosts.isIslandWorld(event.getBlock().getWorld())) {
            event.blockList().removeIf(this::isProtected);
        }
    }

    private boolean touchesProtected(List<Block> blocks) {
        for (Block block : blocks) {
            if (!hosts.isIslandWorld(block.getWorld())) {
                return false;
            }
            if (isProtected(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtected(Block block) {
        World world = block.getWorld();
        return logistics.isBelt(world, block.getX(), block.getY(), block.getZ())
                || logistics.isBeltSupport(world, block.getX(), block.getY(), block.getZ())
                || structures.isStructureBlock(block);
    }

    // ------------------------------------------------------------------------------------------------
    // build tools (Blueprint / Belt Layer): they never leave the player's inventory
    // ------------------------------------------------------------------------------------------------

    @EventHandler
    public void onDropTool(PlayerDropItemEvent event) {
        if (!placement.isTool(event.getItemDrop().getItemStack())) {
            return;
        }
        event.getItemDrop().remove();
        placement.onDropTool(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToolClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean tool = placement.isTool(event.getCurrentItem()) || placement.isTool(event.getCursor());
        if (!tool && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            tool = placement.isTool(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (!tool && event.getClick() == ClickType.SWAP_OFFHAND) {
            tool = placement.isTool(player.getInventory().getItemInOffHand());
        }
        if (tool) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && placement.isTool(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (placement.isTool(event.getMainHandItem()) || placement.isTool(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onToolEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && placement.holdingTool(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolStand(PlayerArmorStandManipulateEvent event) {
        if (placement.isTool(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onToolCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (placement.isTool(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(placement::isTool);
        placement.cancel(event.getEntity(), null);
    }

    // ------------------------------------------------------------------------------------------------
    // players
    // ------------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        placement.stripTools(event.getPlayer());
        unlock.onJoin(event.getPlayer());
        arrivalCheck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placement.cancel(event.getPlayer(), null);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        placement.cancel(event.getPlayer(), "§7Build mode off.");
        arrivalCheck(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && hosts.guildIslands().isGuildWorld(event.getTo().getWorld())) {
            arrivalCheck(event.getPlayer());
        }
    }

    private void arrivalCheck(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                projects.onArrive(player);
            }
        }, 40L);
    }
}
