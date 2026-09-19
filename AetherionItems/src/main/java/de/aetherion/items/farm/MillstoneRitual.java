package de.aetherion.items.farm;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Millstone grind: compacted farm crop → refined pantry crop.
 */
public final class MillstoneRitual {

    public static final int SECONDS = 8;

    private final AetherionItems plugin;
    private final Map<UUID, Session> byStation = new ConcurrentHashMap<>();
    private final Map<UUID, Session> byPlayer = new ConcurrentHashMap<>();

    public MillstoneRitual(AetherionItems plugin) {
        this.plugin = plugin;
    }

    public boolean isBusy(UUID stationId) {
        return stationId != null && byStation.containsKey(stationId);
    }

    public boolean isPlayerBusy(UUID playerId) {
        return playerId != null && byPlayer.containsKey(playerId);
    }

    public boolean start(Player player, UUID stationId, Location stationAt, CompressedResource crop) {
        if (player == null || stationId == null || crop == null || !crop.canRefine()) {
            return false;
        }
        if (isBusy(stationId) || isPlayerBusy(player.getUniqueId())) {
            player.sendMessage("§eMillstone is busy.");
            return false;
        }
        ItemStack result = crop.refined();
        BlockDisplay arm = MillstoneCabinet.findArm(stationId, stationAt);
        BossBar bar = BossBar.bossBar(
                Component.text("Milling " + strip(crop.refinedName()), NamedTextColor.GOLD, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        long endTick = Bukkit.getCurrentTick() + SECONDS * 20L;
        Session session = new Session(player.getUniqueId(), stationId, result, arm, bar, stationAt, endTick);
        byStation.put(stationId, session);
        byPlayer.put(player.getUniqueId(), session);
        player.closeInventory();
        rootSay(player, "Milling…");
        if (stationAt != null && stationAt.getWorld() != null) {
            stationAt.getWorld().playSound(stationAt, Sound.BLOCK_GRINDSTONE_USE, 0.85f, 0.85f);
        }
        session.animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickAnim(session), 0L, 10L);
        session.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTimer(session), 0L, 10L);
        return true;
    }

    private void tickAnim(Session session) {
        if (session.done || session.arm == null || !session.arm.isValid()) {
            return;
        }
        session.spin += 0.55f;
        session.arm.setInterpolationDuration(8);
        session.arm.setTransformation(new Transformation(
                new Vector3f(-0.45f, -0.12f, -0.45f),
                new AxisAngle4f(session.spin, 0f, 1f, 0f),
                new Vector3f(0.9f, 0.24f, 0.9f),
                new AxisAngle4f(0f, 0f, 1f, 0f)
        ));
        Location at = session.stationAt;
        if (at != null && at.getWorld() != null) {
            World world = at.getWorld();
            world.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.2, 0), 3, 0.25, 0.1, 0.25, 0.01);
            world.spawnParticle(Particle.CRIT, at.clone().add(0, 1.15, 0), 2, 0.2, 0.05, 0.2, 0.02);
            if (Bukkit.getCurrentTick() % 20L == 0L) {
                world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, 0.35f, 1.1f);
            }
        }
    }

    private void tickTimer(Session session) {
        if (session.done) {
            return;
        }
        long leftTicks = Math.max(0L, session.endTick - Bukkit.getCurrentTick());
        int leftSec = (int) Math.ceil(leftTicks / 20.0);
        float progress = Math.max(0f, Math.min(1f, leftTicks / (float) (SECONDS * 20L)));
        session.bar.progress(progress);
        session.bar.name(Component.text(
                "Milling · " + Math.max(0, leftSec) + "s",
                NamedTextColor.GOLD,
                TextDecoration.BOLD
        ));
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.showBossBar(session.bar);
        }
        if (leftTicks <= 0) {
            complete(session);
        }
    }

    private void complete(Session session) {
        if (session.done) {
            return;
        }
        session.done = true;
        cancel(session);
        byStation.remove(session.stationId);
        byPlayer.remove(session.playerId);
        if (session.arm != null && session.arm.isValid()) {
            session.arm.setTransformation(new Transformation(
                    new Vector3f(-0.45f, -0.12f, -0.45f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.9f, 0.24f, 0.9f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
        }
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.hideBossBar(session.bar);
            give(player, session.result);
            rootSay(player, "Done. Refined pantry crop ready.");
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.9f, 1.35f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.4f);
        } else {
            // Drop at station if offline.
            if (session.stationAt != null && session.stationAt.getWorld() != null) {
                session.stationAt.getWorld().dropItemNaturally(session.stationAt.clone().add(0, 1, 0), session.result);
            }
        }
        if (session.stationAt != null && session.stationAt.getWorld() != null) {
            session.stationAt.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    session.stationAt.clone().add(0, 1.3, 0),
                    16, 0.35, 0.35, 0.35, 0.02
            );
        }
    }

    public void shutdown() {
        for (Session session : byStation.values()) {
            cancel(session);
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null) {
                player.hideBossBar(session.bar);
                give(player, session.result);
            }
        }
        byStation.clear();
        byPlayer.clear();
    }

    private static void cancel(Session session) {
        if (session.animTask != null) {
            session.animTask.cancel();
        }
        if (session.timerTask != null) {
            session.timerTask.cancel();
        }
    }

    private static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static void rootSay(Player player, String line) {
        try {
            Class.forName("de.aetherion.quests.npc.LivingNpcProfile")
                    .getMethod("say", Player.class, String.class, String.class, String.class)
                    .invoke(null, player, "root_cellar", "Root Cellar", line);
        } catch (Throwable ignored) {
            player.sendMessage("§aRoot Cellar §8⟫ §f" + line);
        }
    }

    private static String strip(String colored) {
        return colored == null ? "crop" : colored.replaceAll("§.", "");
    }

    private static final class Session {
        final UUID playerId;
        final UUID stationId;
        final ItemStack result;
        final BlockDisplay arm;
        final BossBar bar;
        final Location stationAt;
        final long endTick;
        float spin;
        boolean done;
        BukkitTask animTask;
        BukkitTask timerTask;

        Session(
                UUID playerId,
                UUID stationId,
                ItemStack result,
                BlockDisplay arm,
                BossBar bar,
                Location stationAt,
                long endTick
        ) {
            this.playerId = playerId;
            this.stationId = stationId;
            this.result = result;
            this.arm = arm;
            this.bar = bar;
            this.stationAt = stationAt == null ? null : stationAt.clone();
            this.endTick = endTick;
        }
    }
}
