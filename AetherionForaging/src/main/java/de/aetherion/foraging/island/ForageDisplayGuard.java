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
 * (jump pads / forge / area markers).
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

    public ForageDisplayGuard(AetherionForaging plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 80L, SWEEP_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sweep(true);
    }

    @Override
    public void run() {
        refreshOccupancy();
        sweep(onIsle.isEmpty());
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
            onIsle.add(id);
        } else {
            onIsle.remove(id);
            if (onIsle.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> sweep(true));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onIsle.remove(event.getPlayer().getUniqueId());
        if (onIsle.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> sweep(true));
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
        if (world == null) {
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

    private static void cullTaggedDuplicates(List<TextDisplay> texts, String tag, int keep) {
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
            DisplayEntities.discard(tagged.get(i));
            texts.remove(tagged.get(i));
        }
    }

    private static void cullPerBlock(List<TextDisplay> texts) {
        Map<Long, List<TextDisplay>> byBlock = new HashMap<>();
        for (TextDisplay display : List.copyOf(texts)) {
            if (!display.isValid() || DisplayEntities.isProtected(display)) {
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
                DisplayEntities.discard(stack.get(i));
                texts.remove(stack.get(i));
            }
        }
    }

    private static void enforceCap(List<? extends Entity> displays, int cap, boolean emptyIsle) {
        List<Entity> victims = new ArrayList<>();
        for (Entity entity : displays) {
            if (!entity.isValid() || DisplayEntities.isProtected(entity)) {
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
                if (entity.isValid() && (DisplayEntities.isProtected(entity) || entity.getVehicle() != null)) {
                    kept++;
                }
            }
            int allowed = Math.max(0, cap - kept);
            victims.sort(Comparator.comparingInt(Entity::getTicksLived));
            if (victims.size() <= allowed) {
                return;
            }
            for (int i = allowed; i < victims.size(); i++) {
                DisplayEntities.discard(victims.get(i));
            }
            return;
        }
        for (Entity entity : victims) {
            if (isEphemeral(entity)) {
                DisplayEntities.discard(entity);
            }
        }
    }

    private static boolean isEphemeral(Entity entity) {
        if (DisplayEntities.isProtected(entity) || entity.getVehicle() != null) {
            return false;
        }
        if (!entity.getScoreboardTags().isEmpty()) {
            // Named holograms stay if they are the single copy; duplicates already culled.
            return false;
        }
        return entity.getTicksLived() > 40;
    }
}
