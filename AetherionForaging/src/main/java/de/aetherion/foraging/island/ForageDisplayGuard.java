package de.aetherion.foraging.island;

import de.aetherion.core.entity.DisplayEntities;
import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.habitat.ForageHabitatService;
import de.aetherion.foraging.npc.IsleGuideNpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caps forage-isle {@link TextDisplay}/{@link ItemDisplay} growth and despawns
 * ephemeral holograms when nobody is on the isle. Never touches armor stands
 * (jump pads / forge / area markers), Hub pad labels, or FancyNPC-owned
 * Aetherion holograms.
 */
public final class ForageDisplayGuard implements Listener, Runnable {

    private static final int MAX_TEXT = 64;
    private static final int MAX_ITEM = 32;
    private static final int MAX_PER_BLOCK = 3;
    private static final int MAX_SAME_TAG = 1;
    private static final long SWEEP_TICKS = 100L;

    private final AetherionForaging plugin;
    private final Set<UUID> onIsle = new HashSet<>();
    private BukkitTask task;
    private volatile boolean helperMissing;
    private volatile boolean helperLogged;
    private volatile boolean sweepLogged;

    public ForageDisplayGuard(AetherionForaging plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        probeCoreHelper();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 80L, SWEEP_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!helperMissing) {
            sweep(true);
        }
    }

    @Override
    public void run() {
        try {
            boolean wasEmpty = onIsle.isEmpty();
            refreshOccupancy();
            if (helperMissing) {
                if (!wasEmpty && onIsle.isEmpty()) {
                    return;
                }
                if (wasEmpty && !onIsle.isEmpty()) {
                    reensureGuide();
                }
                return;
            }
            sweep(onIsle.isEmpty());
            if (wasEmpty && !onIsle.isEmpty()) {
                reensureGuide();
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
        } catch (Throwable thrown) {
            if (sweepLogged) {
                return;
            }
            sweepLogged = true;
            plugin.getLogger().warning("Forage display sweep failed (further errors suppressed): "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        trackIsle(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        trackIsle(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void trackIsle(Player player, Location from, Location to) {
        if (player == null || to == null || from == null) {
            return;
        }
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        boolean was = ForageHabitatService.inIsleFootprint(from);
        boolean now = ForageHabitatService.inIsleFootprint(to);
        if (was == now) {
            return;
        }
        UUID id = player.getUniqueId();
        if (now) {
            boolean first = onIsle.isEmpty();
            onIsle.add(id);
            if (first) {
                Bukkit.getScheduler().runTask(plugin, this::reensureGuide);
            }
        } else {
            onIsle.remove(id);
            if (onIsle.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!helperMissing) {
                        sweep(true);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onIsle.remove(event.getPlayer().getUniqueId());
        if (onIsle.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!helperMissing) {
                    sweep(true);
                }
            });
        }
    }

    private void reensureGuide() {
        IsleGuideNpc guide = plugin.guide();
        if (guide != null) {
            guide.reensure();
        }
    }

    private void refreshOccupancy() {
        onIsle.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ForageHabitatService.inIsleFootprint(player.getLocation())) {
                onIsle.add(player.getUniqueId());
            }
        }
    }

    private void sweep(boolean emptyIsle) {
        World world = isleWorld();
        if (world == null || helperMissing) {
            return;
        }
        List<TextDisplay> texts = new ArrayList<>();
        List<ItemDisplay> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (!ForageHabitatService.inIsleFootprint(entity.getLocation())) {
                continue;
            }
            if (entity instanceof ArmorStand) {
                continue;
            }
            if (entity instanceof TextDisplay text) {
                texts.add(text);
            } else if (entity instanceof ItemDisplay item) {
                items.add(item);
            }
        }
        cullTaggedDuplicates(texts, IsleGuideNpc.HOLO_TAG, MAX_SAME_TAG);
        cullPerBlock(texts);
        enforceCap(texts, MAX_TEXT, emptyIsle);
        enforceCap(items, MAX_ITEM, emptyIsle);
    }

    private World isleWorld() {
        String name = plugin.getConfig().getString("forage-isle.world", "world");
        return Bukkit.getWorld(name);
    }

    private void cullTaggedDuplicates(List<TextDisplay> texts, String tag, int keep) {
        List<TextDisplay> tagged = new ArrayList<>();
        for (TextDisplay display : texts) {
            if (display.getScoreboardTags().contains(tag)) {
                tagged.add(display);
            }
        }
        if (tagged.size() <= keep) {
            return;
        }
        tagged.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        for (int i = 0; i < tagged.size() - keep; i++) {
            discardQuiet(tagged.get(i));
            texts.remove(tagged.get(i));
        }
    }

    private void cullPerBlock(List<TextDisplay> texts) {
        Map<Long, List<TextDisplay>> byBlock = new HashMap<>();
        for (TextDisplay display : List.copyOf(texts)) {
            if (!display.isValid() || protectedDisplay(display)) {
                continue;
            }
            Location loc = display.getLocation();
            long key = (((long) loc.getBlockX()) << 42)
                    ^ (((long) loc.getBlockY()) << 21)
                    ^ (loc.getBlockZ() & 0x1fffffL);
            byBlock.computeIfAbsent(key, ignored -> new ArrayList<>()).add(display);
        }
        for (List<TextDisplay> stack : byBlock.values()) {
            if (stack.size() <= MAX_PER_BLOCK) {
                continue;
            }
            stack.sort(Comparator.comparingInt(Entity::getTicksLived));
            for (int i = MAX_PER_BLOCK; i < stack.size(); i++) {
                discardQuiet(stack.get(i));
                texts.remove(stack.get(i));
            }
        }
    }

    private void enforceCap(List<? extends Entity> displays, int cap, boolean emptyIsle) {
        List<Entity> victims = new ArrayList<>();
        for (Entity entity : displays) {
            if (!entity.isValid() || protectedDisplay(entity)) {
                continue;
            }
            if (entity.getVehicle() != null) {
                continue;
            }
            if (emptyIsle && isEphemeral(entity)) {
                victims.add(entity);
                continue;
            }
            victims.add(entity);
        }
        if (!emptyIsle) {
            int kept = 0;
            for (Entity entity : displays) {
                if (entity.isValid() && (protectedDisplay(entity) || entity.getVehicle() != null)) {
                    kept++;
                }
            }
            int allowed = Math.max(0, cap - kept);
            victims.sort(Comparator.comparingInt(Entity::getTicksLived));
            if (victims.size() <= allowed) {
                return;
            }
            for (int i = allowed; i < victims.size(); i++) {
                discardQuiet(victims.get(i));
            }
            return;
        }
        for (Entity entity : victims) {
            if (isEphemeral(entity)) {
                discardQuiet(entity);
            }
        }
    }

    private boolean isEphemeral(Entity entity) {
        if (protectedDisplay(entity) || entity.getVehicle() != null) {
            return false;
        }
        if (!entity.getScoreboardTags().isEmpty()) {
            // Named holograms stay if they are the single copy; duplicates already culled.
            return false;
        }
        return entity.getTicksLived() > 40;
    }

    private void discardQuiet(Entity entity) {
        if (helperMissing || entity instanceof ArmorStand) {
            return;
        }
        try {
            DisplayEntities.discard(entity);
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
        }
    }

    private boolean protectedDisplay(Entity entity) {
        if (entity == null || entity instanceof ArmorStand) {
            return true;
        }
        if (helperMissing) {
            return localJumpPad(entity);
        }
        try {
            return DisplayEntities.isProtected(entity);
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
            return localJumpPad(entity);
        }
    }

    private static boolean localJumpPad(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return false;
        }
        for (String tag : display.getScoreboardTags()) {
            if (tag != null && tag.startsWith("aetherion_jump_pad")) {
                return true;
            }
        }
        return false;
    }

    private void probeCoreHelper() {
        try {
            Class.forName(
                    "de.aetherion.core.entity.DisplayEntities",
                    true,
                    plugin.getClass().getClassLoader()
            );
        } catch (ClassNotFoundException | LinkageError error) {
            markHelperMissing(error);
        }
    }

    private void markHelperMissing(Throwable error) {
        helperMissing = true;
        if (helperLogged) {
            return;
        }
        helperLogged = true;
        plugin.getLogger().severe(
                "AetherionCore is missing de.aetherion.core.entity.DisplayEntities. "
                        + "Forage display janitor disabled; holograms use a local fallback. "
                        + "Update AetherionCore. ("
                        + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage())
                        + ")"
        );
    }
}
