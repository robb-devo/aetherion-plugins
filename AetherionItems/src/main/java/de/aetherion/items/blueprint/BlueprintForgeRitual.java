package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blueprint upgrade ritual: bossbar timer + hammer animation on the Eldervale forge frame.
 */
public final class BlueprintForgeRitual implements Listener {

    private static final String PENDING_FILE = "blueprint-forge-pending.yml";

    private final AetherionItems plugin;
    private final BlueprintForgeStation station;
    private final Map<UUID, Session> active = new ConcurrentHashMap<>();
    private volatile boolean busy;

    public BlueprintForgeRitual(AetherionItems plugin, BlueprintForgeStation station) {
        this.plugin = plugin;
        this.station = station;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean isForging(UUID playerId) {
        return playerId != null && active.containsKey(playerId);
    }

    public static int durationSecondsForTier(int resultTier) {
        return switch (Math.max(1, Math.min(4, resultTier))) {
            case 2 -> 10;
            case 3 -> 20;
            case 4 -> 30;
            default -> 10;
        };
    }

    public boolean start(Player player, ItemStack result) {
        if (player == null || result == null || result.getType().isAir()) {
            return false;
        }
        if (busy) {
            player.sendMessage("§cForge is busy — wait for the current strike.");
            return false;
        }
        station.ensureResolved();
        if (!station.isResolved()) {
            player.sendMessage("§cForge frame not found — place a stone-brick frame with an anvil.");
            return false;
        }
        Location itemAt = station.anvilCenter();
        Location rest = station.hammerRest();
        Location slam = station.hammerSlam();
        World world = station.world();
        if (itemAt == null || rest == null || slam == null || world == null) {
            return false;
        }

        ItemStack held = result.clone();
        int tier = BlueprintUpgrade.tier(held);
        int seconds = durationSecondsForTier(tier);

        BlockDisplay hammer = station.hammerEntity();
        BlockDisplay chain = station.chainEntity();
        BlockDisplay chainTop = station.chainTopEntity();
        if (hammer == null || !hammer.isValid()) {
            station.ensureProps();
            hammer = station.hammerEntity();
            chain = station.chainEntity();
            chainTop = station.chainTopEntity();
        }
        if (hammer == null) {
            player.sendMessage("§cForge hammer missing — try again.");
            return false;
        }

        ItemDisplay toolDisplay = world.spawn(itemAt, ItemDisplay.class, display -> {
            display.setItemStack(held.clone());
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0.05f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.85f, 0.85f, 0.85f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            display.setTeleportDuration(4);
            display.getPersistentDataContainer().set(ItemKeys.blueprintForgeProp(), PersistentDataType.BYTE, (byte) 1);
        });

        BossBar bar = BossBar.bossBar(
                Component.text("Forging Tier " + BlueprintUpgrade.roman(tier), NamedTextColor.GOLD, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);

        busy = true;
        long endTick = Bukkit.getCurrentTick() + seconds * 20L;
        Session session = new Session(player.getUniqueId(), held, tier, seconds, endTick, hammer, chain, chainTop, toolDisplay, bar, rest, slam);
        active.put(player.getUniqueId(), session);
        savePending(player.getUniqueId(), held);

        player.closeInventory();
        forgehandSay(player, "Into the frame — keep an eye on it.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 0.9f);
        world.playSound(itemAt, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.85f);

        session.animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickAnim(session), 0L, 28L);
        session.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTimer(session), 0L, 10L);
        return true;
    }

    private void tickAnim(Session session) {
        if (session.done || session.hammer == null || !session.hammer.isValid()) {
            return;
        }
        session.down = !session.down;
        Location target = session.down ? session.slam : session.rest;
        station.moveHammer(session.hammer, session.chain, session.chainTop, target);
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        if (session.down) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session.done) {
                    return;
                }
                world.playSound(session.slam, Sound.BLOCK_ANVIL_LAND, 0.55f, 0.85f);
                world.spawnParticle(Particle.LAVA, session.slam.clone().add(0, -0.2, 0), 4, 0.15, 0.05, 0.15, 0.01);
                world.spawnParticle(Particle.FLAME, session.slam.clone().add(0, -0.15, 0), 8, 0.2, 0.1, 0.2, 0.01);
                world.spawnParticle(Particle.CRIT, session.slam, 10, 0.25, 0.1, 0.25, 0.15);
                if (session.toolDisplay != null && session.toolDisplay.isValid()) {
                    session.toolDisplay.teleport(session.toolDisplay.getLocation().add(0, 0.04, 0));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (session.toolDisplay != null && session.toolDisplay.isValid()) {
                            session.toolDisplay.teleport(session.toolDisplay.getLocation().add(0, -0.04, 0));
                        }
                    }, 3L);
                }
            }, 10L);
        } else {
            world.spawnParticle(Particle.SMOKE, session.rest.clone().add(0, -0.2, 0), 3, 0.1, 0.15, 0.1, 0.01);
        }
    }

    private void tickTimer(Session session) {
        if (session.done) {
            return;
        }
        long leftTicks = Math.max(0L, session.endTick - Bukkit.getCurrentTick());
        int leftSec = (int) Math.ceil(leftTicks / 20.0);
        float progress = session.totalSeconds <= 0
                ? 0f
                : Math.max(0f, Math.min(1f, leftTicks / (float) (session.totalSeconds * 20L)));
        session.bar.progress(progress);
        session.bar.name(Component.text(
                "Forging Tier " + BlueprintUpgrade.roman(session.tier) + " · " + Math.max(0, leftSec) + "s",
                NamedTextColor.GOLD,
                TextDecoration.BOLD
        ));
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.showBossBar(session.bar);
        }
        Location at = station.anvilCenter();
        if (at != null && at.getWorld() != null && leftSec % 2 == 0) {
            at.getWorld().spawnParticle(Particle.FLAME, at, 3, 0.2, 0.15, 0.2, 0.01);
            at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 0.3, 0), 2, 0.15, 0.1, 0.15, 0.01);
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
        cancelTasks(session);
        active.remove(session.playerId);
        busy = false;

        if (session.toolDisplay != null && session.toolDisplay.isValid()) {
            session.toolDisplay.remove();
        }
        if (session.hammer != null && session.hammer.isValid()) {
            station.moveHammer(session.hammer, session.chain, session.chainTop, session.rest);
        }

        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            player.hideBossBar(session.bar);
            give(player, session.result);
            clearPending(session.playerId);
            forgehandSay(player, "Done — Tier " + BlueprintUpgrade.roman(session.tier)
                    + ". Hot off the anvil.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.85f, 1.15f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
            Location at = station.anvilCenter();
            if (at != null && at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 0.4, 0), 18, 0.3, 0.4, 0.3, 0.15);
                at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 0.9f, 1.2f);
            }
        } else {
            // Keep pending YAML for join delivery.
            savePending(session.playerId, session.result);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack pending = loadPending(player.getUniqueId());
        if (pending == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Still forging online session?
            if (active.containsKey(player.getUniqueId())) {
                return;
            }
            give(player, pending);
            clearPending(player.getUniqueId());
            forgehandSay(player, "Caught you — Tier "
                    + BlueprintUpgrade.roman(BlueprintUpgrade.tier(pending))
                    + " finished while you were gone.");
        }, 40L);
    }

    public void shutdown() {
        for (Session session : active.values()) {
            cancelTasks(session);
            if (session.toolDisplay != null && session.toolDisplay.isValid()) {
                session.toolDisplay.remove();
            }
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null) {
                player.hideBossBar(session.bar);
            }
            savePending(session.playerId, session.result);
        }
        active.clear();
        busy = false;
    }

    private static void cancelTasks(Session session) {
        if (session.animTask != null) {
            session.animTask.cancel();
        }
        if (session.timerTask != null) {
            session.timerTask.cancel();
        }
    }

    private static void give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static void forgehandSay(Player player, String line) {
        try {
            Class.forName("de.aetherion.quests.npc.LivingNpcProfile")
                    .getMethod("say", Player.class, String.class, String.class, String.class)
                    .invoke(null, player, "eldervale_upgrade", "Forgehand", line);
        } catch (Throwable ignored) {
            player.sendMessage("§6Forgehand §8⟫ §f" + line);
        }
    }

    private void savePending(UUID id, ItemStack item) {
        File file = new File(plugin.getDataFolder(), PENDING_FILE);
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        config.set("pending." + id, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save forge pending: " + ex.getMessage());
        }
    }

    private ItemStack loadPending(UUID id) {
        File file = new File(plugin.getDataFolder(), PENDING_FILE);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String raw = config.getString("pending." + id);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearPending(UUID id) {
        File file = new File(plugin.getDataFolder(), PENDING_FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("pending." + id, null);
        try {
            config.save(file);
        } catch (IOException ignored) {
        }
    }

    private static final class Session {
        final UUID playerId;
        final ItemStack result;
        final int tier;
        final int totalSeconds;
        final long endTick;
        final BlockDisplay hammer;
        final BlockDisplay chain;
        final BlockDisplay chainTop;
        final ItemDisplay toolDisplay;
        final BossBar bar;
        final Location rest;
        final Location slam;
        boolean down;
        boolean done;
        BukkitTask animTask;
        BukkitTask timerTask;

        Session(
                UUID playerId,
                ItemStack result,
                int tier,
                int seconds,
                long endTick,
                BlockDisplay hammer,
                BlockDisplay chain,
                BlockDisplay chainTop,
                ItemDisplay toolDisplay,
                BossBar bar,
                Location rest,
                Location slam
        ) {
            this.playerId = playerId;
            this.result = result;
            this.tier = tier;
            this.totalSeconds = seconds;
            this.endTick = endTick;
            this.hammer = hammer;
            this.chain = chain;
            this.chainTop = chainTop;
            this.toolDisplay = toolDisplay;
            this.bar = bar;
            this.rest = rest.clone();
            this.slam = slam.clone();
        }
    }
}
