package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.blueprint.BlueprintUpgrade;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.mining.HarvestRules;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vein Siphon — Ore Vacuum with ghost suction FX. Scales with blueprint tier.
 */
public final class VeinSiphonListener implements Listener {

    public static final String ITEM_ID = "vein_siphon";
    private static final int FX_TICKS = 10;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public VeinSiphonListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = itemManager.getItemId(hand);
        if (id == null || !ITEM_ID.equalsIgnoreCase(id)) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        int tier = BlueprintUpgrade.tier(hand);
        long cooldownTicks = BlueprintUpgrade.siphonCooldownTicks(tier);
        int radius = BlueprintUpgrade.siphonRadius(tier);
        int maxOres = BlueprintUpgrade.siphonMaxOres(tier);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long remain = (next - tick + 19) / 20;
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Ore Vacuum · " + remain + "s",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
            return;
        }

        List<Block> ores = findOres(player, radius, maxOres);
        if (ores.isEmpty()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "No mineable ore in range",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
            return;
        }

        nextUseTick.put(player.getUniqueId(), tick + cooldownTicks);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.55f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getEyeLocation(), 12, 0.35, 0.2, 0.35, 0.02);

        HarvestListener harvest = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().getHarvestListener();

        for (Block block : ores) {
            Material type = block.getType();
            BlockData data = block.getBlockData().clone();
            Location from = block.getLocation().add(0.5, 0.5, 0.5);
            if (harvest != null) {
                harvest.vacuumHarvest(player, block);
            } else {
                block.setType(Material.BEDROCK, false);
            }
            spawnGhost(player, from, data, type);
        }

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Ore Vacuum T" + tier + " · " + ores.size() + " pulled",
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
    }

    private List<Block> findOres(Player player, int radius, int maxOres) {
        List<Block> found = new ArrayList<>();
        Location center = player.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double power = 0;
        if (AetherionItems.getInstance() != null && AetherionItems.getInstance().getHarvestListener() != null) {
            power = new de.aetherion.items.manager.ActiveEquipmentStats(itemManager)
                    .getStat(player, de.aetherion.items.model.ItemCapability.MINING_POWER);
        }
        boolean open = HarvestRules.openMine(center.getWorld());
        int r2 = radius * radius;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz) > r2) {
                        continue;
                    }
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!HarvestRules.ore(type)) {
                        continue;
                    }
                    if (!open && !HarvestRules.canHarvest(type, power)) {
                        continue;
                    }
                    found.add(block);
                }
            }
        }
        found.sort((a, b) -> {
            double da = a.getLocation().distanceSquared(center);
            double db = b.getLocation().distanceSquared(center);
            return Double.compare(da, db);
        });
        if (found.size() > maxOres) {
            return new ArrayList<>(found.subList(0, maxOres));
        }
        return found;
    }

    private void spawnGhost(Player player, Location from, BlockData data, Material type) {
        if (from.getWorld() == null) {
            return;
        }
        BlockDisplay display;
        try {
            display = from.getWorld().spawn(from, BlockDisplay.class, entity -> {
                entity.setBlock(data);
                entity.setGravity(false);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setTransformation(new Transformation(
                        new Vector3f(-0.25f, -0.25f, -0.25f),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setInterpolationDuration(1);
                entity.setTeleportDuration(1);
            });
        } catch (Throwable ignored) {
            return;
        }
        BlockDisplay ghost = display;
        for (int i = 1; i <= FX_TICKS; i++) {
            int step = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!ghost.isValid() || !player.isOnline()) {
                    if (ghost.isValid()) {
                        ghost.remove();
                    }
                    return;
                }
                Location target = player.getEyeLocation().add(0, -0.2, 0);
                Vector delta = target.toVector().subtract(ghost.getLocation().toVector());
                double t = step / (double) FX_TICKS;
                Location next = ghost.getLocation().add(delta.multiply(0.35 + 0.55 * t));
                ghost.teleport(next);
                if (step == FX_TICKS) {
                    ghost.getWorld().spawnParticle(Particle.CRIT, next, 4, 0.1, 0.1, 0.1, 0.01);
                    ghost.remove();
                }
            }, step);
        }
        from.getWorld().playSound(from, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.6f);
        if (type != null && type.isBlock()) {
            from.getWorld().spawnParticle(Particle.BLOCK, from, 6, 0.15, 0.15, 0.15, 0.02, data);
        }
    }
}
