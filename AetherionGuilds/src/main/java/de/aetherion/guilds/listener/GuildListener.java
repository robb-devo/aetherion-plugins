package de.aetherion.guilds.listener;

import de.aetherion.guilds.menu.BankMenu;
import de.aetherion.guilds.menu.BiomeSelectMenu;
import de.aetherion.guilds.menu.FriendMenu;
import de.aetherion.guilds.menu.GuildMenu;
import de.aetherion.guilds.menu.IslandMenu;
import de.aetherion.guilds.menu.QuarryMenu;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.FriendService;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GuildListener implements Listener {

    private final GuildService guilds;
    private final IslandService islands;
    private final PersonalIslandService personal;
    private final MinionService minions;
    private final GuildMenu menu;
    private final IslandMenu islandMenu;
    private final BiomeSelectMenu biomeMenu;
    private final QuarryMenu quarryMenu;
    private final FriendMenu friendMenu;
    private final FriendService friends;
    private final BankMenu bankMenu;

    public GuildListener(
            GuildService guilds,
            IslandService islands,
            PersonalIslandService personal,
            MinionService minions,
            GuildMenu menu,
            IslandMenu islandMenu,
            BiomeSelectMenu biomeMenu,
            QuarryMenu quarryMenu,
            FriendMenu friendMenu,
            FriendService friends,
            BankMenu bankMenu
    ) {
        this.guilds = guilds;
        this.islands = islands;
        this.personal = personal;
        this.minions = minions;
        this.menu = menu;
        this.islandMenu = islandMenu;
        this.biomeMenu = biomeMenu;
        this.quarryMenu = quarryMenu;
        this.friendMenu = friendMenu;
        this.friends = friends;
        this.bankMenu = bankMenu;
    }

    @EventHandler
    public void onMenu(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuildMenu.Holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                menu.handle(player, event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof IslandMenu.Holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                islandMenu.handle(player, event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BiomeSelectMenu.Holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                biomeMenu.handle(player, event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof QuarryMenu.Holder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                quarryMenu.handle(player, holder, event.getRawSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof FriendMenu.Holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                friendMenu.handle(player, event.getRawSlot(), event.getCurrentItem(), event.isShiftClick());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BankMenu.Holder holder) {
            if (event.getWhoClicked() instanceof Player player) {
                bankMenu.handle(player, holder, event);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuildMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof IslandMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof BiomeSelectMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof QuarryMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof FriendMenu.Holder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof BankMenu.Holder) {
            bankMenu.handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankMenu.Holder holder) {
            Guild guild = guilds.byId(holder.guildId());
            if (guild != null) {
                bankMenu.save(guild, event.getView().getTopInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (personal != null && personal.isPersonalWorld(event.getBlock().getWorld())) {
            if (!canBuildPersonal(event.getPlayer(), event.getBlock().getX(), event.getBlock().getZ())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!islands.isGuildWorld(event.getBlock().getWorld())) {
            return;
        }
        if (!canBuildGuild(event.getPlayer(), event.getBlock().getLocation().getBlockX(), event.getBlock().getLocation().getBlockZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (minions.isQuarryItem(event.getItemInHand())) {
            event.setCancelled(true);
            boolean guildWorld = islands.isGuildWorld(event.getBlock().getWorld());
            boolean personalWorld = personal != null && personal.isPersonalWorld(event.getBlock().getWorld());
            if (!guildWorld && !personalWorld) {
                player.sendMessage("§cQuarries can only be placed on your private or guild island.");
                return;
            }
            if (guildWorld && !canBuildGuild(player, event.getBlock().getX(), event.getBlock().getZ())) {
                return;
            }
            if (personalWorld && !canBuildPersonal(player, event.getBlock().getX(), event.getBlock().getZ())) {
                return;
            }
            if (minions.place(player, event.getBlockAgainst(), event.getItemInHand()) != null
                    && event.getItemInHand().getAmount() > 0) {
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
            }
            return;
        }
        if (personal != null && personal.isPersonalWorld(event.getBlock().getWorld())) {
            if (!canBuildPersonal(player, event.getBlock().getX(), event.getBlock().getZ())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!islands.isGuildWorld(event.getBlock().getWorld())) {
            return;
        }
        if (!canBuildGuild(player, event.getBlock().getX(), event.getBlock().getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMinionClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getRightClicked().getPersistentDataContainer().has(minions.standKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        MinionService.QuarryRef ref = minions.resolve(event.getRightClicked());
        if (ref != null) {
            if (minions.installProcessor(event.getPlayer(), ref.minion(), event.getPlayer().getInventory().getItemInMainHand())) {
                return;
            }
            quarryMenu.open(event.getPlayer(), ref);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMinionClickAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getRightClicked().getPersistentDataContainer().has(minions.standKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        MinionService.QuarryRef ref = minions.resolve(event.getRightClicked());
        if (ref != null) {
            if (minions.installProcessor(event.getPlayer(), ref.minion(), event.getPlayer().getInventory().getItemInMainHand())) {
                return;
            }
            quarryMenu.open(event.getPlayer(), ref);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMinionHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!event.getEntity().getPersistentDataContainer().has(minions.standKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        MinionService.QuarryRef ref = minions.resolve(event.getEntity());
        if (ref != null) {
            quarryMenu.open(player, ref);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (friends != null) {
            friends.notifyJoin(event.getPlayer());
        }
        if (personal != null) {
            Bukkit.getScheduler().runTask(personalWorldPlugin(), () -> personal.refreshFlight(event.getPlayer()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (personal != null) {
            personal.clearFlight(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (personal != null) {
            Bukkit.getScheduler().runTask(personalWorldPlugin(), () -> personal.refreshFlight(event.getPlayer()));
        }
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (personal != null) {
            Bukkit.getScheduler().runTask(personalWorldPlugin(), () -> personal.refreshFlight(event.getPlayer()));
        }
    }

    @EventHandler
    public void onVoid(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (personal != null
                && event.getTo() != null
                && event.getFrom().getWorld() != null
                && event.getTo().getWorld() != null
                && personal.isPersonalWorld(event.getTo().getWorld())
                && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld()))) {
            personal.refreshFlight(player);
        }
        if (event.getTo() == null || event.getTo().getY() > 40) {
            return;
        }
        if (personal != null && personal.isPersonalWorld(player.getWorld())) {
            PersonalIsland island = personal.byPlot(personal.plotAt(player.getLocation()));
            if (island != null) {
                player.teleport(personal.spawn(island));
                Bukkit.getScheduler().runTask(personalWorldPlugin(), () -> personal.refreshFlight(player));
                return;
            }
            islands.teleportHome(player, Bukkit.getWorlds().isEmpty() ? player.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }
        if (!islands.isGuildWorld(player.getWorld())) {
            return;
        }
        Guild guild = guilds.byPlot(islands.plotAt(player.getLocation()));
        if (guild != null) {
            player.teleport(islands.spawn(guild));
            return;
        }
        islands.teleportHome(player, Bukkit.getWorlds().isEmpty() ? player.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    private org.bukkit.plugin.Plugin personalWorldPlugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(GuildListener.class);
    }

    private boolean canBuildGuild(Player player, int x, int z) {
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("aetherion.guild.admin")) {
            return true;
        }
        Guild guild = guilds.byPlot(islands.plotAt(player.getLocation()));
        if (guild == null) {
            return false;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null) {
            player.sendMessage("§cThis is not your island.");
            return false;
        }
        if (!rank.canBuild()) {
            player.sendMessage("§cFootmen cannot build. Ask for Soldier or higher.");
            return false;
        }
        int radius = islands.buildRadius(guild);
        return Math.abs(x - islands.originX(guild)) <= radius && Math.abs(z - islands.originZ(guild)) <= radius;
    }

    private boolean canBuildPersonal(Player player, int x, int z) {
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("aetherion.guild.admin")) {
            return true;
        }
        PersonalIsland island = personal.byPlot(personal.plotAt(player.getLocation()));
        if (island == null || !island.ownerId().equals(player.getUniqueId())) {
            player.sendMessage("§cThis is not your island.");
            return false;
        }
        int radius = personal.buildRadius(island);
        return Math.abs(x - personal.originX(island)) <= radius && Math.abs(z - personal.originZ(island)) <= radius;
    }
}
