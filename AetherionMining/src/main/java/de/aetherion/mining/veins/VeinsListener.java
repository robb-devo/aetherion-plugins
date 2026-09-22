package de.aetherion.mining.veins;

import de.aetherion.mining.AetherionMining;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinsListener implements Listener {

    private static final long DIALOG_INTERVAL_TICKS = 60L;

    private final Map<UUID, BukkitRunnable> activeDialogs = new ConcurrentHashMap<>();

    private final AetherionMining plugin;
    private final VeinsWorld veins;
    private final VeinsNpcs npcs;

    public VeinsListener(AetherionMining plugin, VeinsWorld veins, VeinsNpcs npcs) {
        this.plugin = plugin;
        this.veins = veins;
        this.npcs = npcs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpc(PlayerInteractEntityEvent event) {
        if (!VeinsNpcs.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleNpc(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcAt(PlayerInteractAtEntityEvent event) {
        if (!VeinsNpcs.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleNpc(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!VeinsNpcs.isAnchor(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!player.hasPermission("aetherion.mines.admin")) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            npcs.removeEntrance();
            player.sendMessage("§7Foreman removed.");
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Location location = block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0f);
        npcs.spawnEntrance(location);
        int minLevel = veinsMinLevel();
        player.sendMessage("§aAnchored §fForeman§a (legacy admin lantern).");
        player.sendMessage("§7Players enter via §f/amethyst §7or the §dCrystal Guide§7 (Mining "
                + minLevel + "+).");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (!veins.isVeins(event.getBlock().getWorld())) {
            return;
        }
        if (veins.isProtected(event.getBlock().getLocation())) {
            if (!admin(event.getPlayer())) {
                event.setCancelled(true);
                event.setDropItems(false);
            }
            return;
        }
        // Outside spawn protect: mine everything (incl. amethyst). No per-block regen here.
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!veins.isVeins(event.getBlock().getWorld())) {
            return;
        }
        if (admin(event.getPlayer())) {
            return;
        }
        // Place denied everywhere in aether_veins (spawn protect + digs).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (!veins.isVeins(event.getLocation().getWorld())) {
            return;
        }
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        // Vanilla monsters/animals off world-wide.
        if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            event.setCancelled(true);
            return;
        }
        // Custom/plugin pets: allow in digs (dark), block on island surface (open sky).
        if (reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT) {
            if (event.getLocation().getBlock().getLightFromSky() >= 8) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (veins.isVeins(event.getEntity().getWorld())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (veins.isVeins(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent event) {
        if (veins.isVeins(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!veins.isVeins(player.getWorld()) && !veins.isVeins(event.getRespawnLocation().getWorld())) {
            return;
        }
        Location spawn = veins.hubSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            keepSurvival(player);
            veins.rescueIfBuried(player);
        });
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        keepSurvival(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.ADVENTURE) {
            return;
        }
        if (!veins.isVeins(event.getPlayer().getWorld())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().setGameMode(GameMode.SURVIVAL);
    }

    private void keepSurvival(Player player) {
        if (player != null && veins.isVeins(player.getWorld()) && player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void handleNpc(Player player, Entity entity) {
        if (player.isSneaking() && player.hasPermission("aetherion.mines.admin")) {
            if (npcs.tryRemoveClicked(entity)) {
                player.sendMessage("§7Foreman removed.");
            }
            return;
        }
        if (veins.isVeins(player.getWorld())) {
            playForemanDialog(player, List.of(
                    "Done already? Fine. Back to daylight."
            ), () -> veins.leave(player));
            return;
        }

        // Player entry into the Amethyst Area is /amethyst or the Crystal Guide — not this lantern.
        int minLevel = veinsMinLevel();
        playForemanDialog(player, List.of(
                "Name's the Foreman. I don't dig. I point.",
                "The Amethyst Area is the dig now — not the old Deep Mines door.",
                "Talk to the §dCrystal Guide §fon Elder Vale, or use §f/amethyst§f.",
                "Door policy still stands: §aMining Skill " + minLevel + "§f."
        ), null);
    }

    private void playForemanDialog(Player player, List<String> lines, Runnable after) {
        cancelDialog(player.getUniqueId());

        List<String> copy = new ArrayList<>(lines);
        BukkitRunnable task = new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelDialog(player.getUniqueId());
                    return;
                }

                if (index >= copy.size()) {
                    cancelDialog(player.getUniqueId());
                    if (after != null) {
                        after.run();
                    }
                    return;
                }

                String line = copy.get(index);
                if (line != null && !line.isEmpty()) {
                    player.sendMessage("§6Foreman§7: §f" + line);
                }
                index++;
            }
        };

        activeDialogs.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, DIALOG_INTERVAL_TICKS);
    }

    private void cancelDialog(UUID playerId) {
        BukkitRunnable running = activeDialogs.remove(playerId);
        if (running != null) {
            running.cancel();
        }
    }

    private int veinsMinLevel() {
        de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
        if (mining != null) {
            return mining.veinsMinMiningLevel();
        }
        return Math.max(1, plugin.getConfig().getInt("veins.min-mining-level", 30));
    }

    private static boolean admin(Player player) {
        return player != null
                && player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission("aetherion.mines.admin");
    }
}
