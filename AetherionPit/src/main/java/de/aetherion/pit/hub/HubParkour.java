package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Sky Path (Himmelspfad): a jump and run spiralling up the elven tower (2026-11-26).
 * Start on the gold plate by the tower, iron plates are checkpoints, the gold plate on the terrace at the top ends
 * the run. Falling more than a few blocks below the last checkpoint puts you back there; the time keeps running.
 * Best times live in plugins/AetherionPit/parkour.yml and on the scroll at the start.
 */
public final class HubParkour implements Listener {

    private static final TextColor TEAL = TextColor.color(0x4DD0E1);
    private static final TextColor SOFT = TextColor.color(0xC9C2D6);

    private final AetherionPit plugin;
    private final NamespacedKey itemKey;
    private World world;
    private int[] start;
    private final List<int[]> checkpoints = new ArrayList<>();
    private int[] finish;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final File file;
    private final YamlConfiguration data;

    private static final class Run {
        long started;
        int checkpoint = -1;
        Location respawn;
        boolean teleporting;
    }

    public record Best(String name, long millis) {
    }

    public HubParkour(AetherionPit plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "parkour_item");
        this.file = new File(plugin.getDataFolder(), "parkour.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::hud, 20L, 2L);
    }

    public boolean enabled() {
        return world != null && start != null && finish != null;
    }

    private void load() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("parkour");
        Map<?, ?> m = null;
        if (s == null) {
            Object raw = plugin.getConfig().get("parkour");
            if (raw instanceof Map<?, ?> map) {
                m = map;
            }
        } else {
            m = s.getValues(false);
        }
        if (m == null) {
            return;
        }
        world = Bukkit.getWorld(String.valueOf(m.containsKey("world") ? m.get("world") : "world"));
        start = ints(m.get("start"));
        finish = ints(m.get("finish"));
        Object cps = m.get("checkpoints");
        if (cps instanceof List<?> list) {
            for (Object o : list) {
                int[] c = ints(o);
                if (c != null) {
                    checkpoints.add(c);
                }
            }
        }
    }

    private static int[] ints(Object o) {
        if (o instanceof List<?> l && l.size() >= 3) {
            return new int[]{((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue()};
        }
        return null;
    }

    public Location board() {
        Object raw = plugin.getConfig().get("parkour");
        Map<?, ?> m = raw instanceof ConfigurationSection cs ? cs.getValues(false) : raw instanceof Map<?, ?> mm ? mm : null;
        if (m == null || world == null || !(m.get("board") instanceof List<?> l) || l.size() < 3) {
            return null;
        }
        return new Location(world, ((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(),
                ((Number) l.get(2)).doubleValue());
    }

    public boolean running(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled()) {
            return;
        }
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (to == null || to.getWorld() != world) {
            return;
        }
        Run run = runs.get(player.getUniqueId());
        if (run != null && run.respawn != null && to.getY() < run.respawn.getY() - 5) {
            back(player, run, true);
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();
        if (at(start, x, y, z)) {
            begin(player);
            return;
        }
        if (run == null) {
            return;
        }
        for (int i = 0; i < checkpoints.size(); i++) {
            if (at(checkpoints.get(i), x, y, z) && i > run.checkpoint) {
                run.checkpoint = i;
                run.respawn = center(checkpoints.get(i), player.getLocation().getYaw());
                player.playSound(to, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f + 0.15f * i);
                player.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
                world.spawnParticle(Particle.HAPPY_VILLAGER, to.clone().add(0, 0.6, 0), 12, 0.4, 0.4, 0.4, 0);
                boolean de = HubHolograms.german(player);
                player.showTitle(Title.title(Component.empty(),
                        Component.text((de ? "Checkpoint " : "Checkpoint ") + (i + 1) + "/" + checkpoints.size(), TEAL),
                        Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(700), Duration.ofMillis(300))));
                return;
            }
        }
        if (at(finish, x, y, z)) {
            done(player, run);
        }
    }

    private static boolean at(int[] p, int x, int y, int z) {
        return p != null && p[0] == x && p[2] == z && (p[1] == y || p[1] == y + 1 || p[1] == y - 1);
    }

    private Location center(int[] p, float yaw) {
        return new Location(world, p[0] + 0.5, p[1], p[2] + 0.5, yaw, 10f);
    }

    private void begin(Player player) {
        Run run = runs.get(player.getUniqueId());
        boolean restart = run != null;
        if (run == null) {
            run = new Run();
            runs.put(player.getUniqueId(), run);
            giveItems(player);
        }
        run.started = System.currentTimeMillis();
        run.checkpoint = -1;
        run.respawn = center(start, player.getLocation().getYaw());
        boolean de = HubHolograms.german(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, restart ? 1.4f : 1.0f);
        if (!restart) {
            player.showTitle(Title.title(Component.text(de ? "Himmelspfad" : "Sky Path", TEAL, TextDecoration.BOLD),
                    Component.text(de ? "Los geht's! Nach oben, um den Turm herum." : "Go! Up and around the tower.", SOFT),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400))));
        }
    }

    private void done(Player player, Run run) {
        long ms = System.currentTimeMillis() - run.started;
        stop(player);
        boolean de = HubHolograms.german(player);
        String time = format(ms);
        String path = "best." + player.getUniqueId();
        long old = data.getLong(path + ".ms", Long.MAX_VALUE);
        boolean record = ms < old;
        if (record) {
            data.set(path + ".ms", ms);
            data.set(path + ".name", player.getName());
            save();
        }
        player.showTitle(Title.title(Component.text(time, TEAL, TextDecoration.BOLD),
                Component.text(record ? (de ? "Neue Bestzeit!" : "New personal best!")
                        : (de ? "Geschafft! Bestzeit: " : "Done! Best: ") + format(old), SOFT),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2600), Duration.ofMillis(600))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        player.sendMessage(Component.text(" ✦ ", TEAL).append(Component.text(de ? "Himmelspfad geschafft in " : "Sky Path done in ", SOFT))
                .append(Component.text(time, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(record ? (de ? "  (neue Bestzeit)" : "  (new best)") : "", TEAL)));
        Location at = player.getLocation().add(0, 1.5, 0);
        Firework fw = world.spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.fromRGB(0x4DD0E1),
                    Color.fromRGB(0xF8BBD0)).withFade(Color.WHITE).trail(true).build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
        });
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
        plugin.guide().refreshParkour();
    }

    private void back(Player player, Run run, boolean fell) {
        if (run.teleporting) {
            return;
        }
        run.teleporting = true;
        Location to = run.respawn != null ? run.respawn : center(start, player.getLocation().getYaw());
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setFallDistance(0f);
            player.setVelocity(new org.bukkit.util.Vector());
            player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
            run.teleporting = false;
            if (fell) {
                player.playSound(to, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.3f);
            }
        });
    }

    /** Called by the void catch while a run is active. */
    public boolean catchFall(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return false;
        }
        back(player, run, true);
        return true;
    }

    public void stop(Player player) {
        if (runs.remove(player.getUniqueId()) != null) {
            takeItems(player);
            player.sendActionBar(Component.empty());
        }
    }

    // ------------------------------------------------------------------ hud + items

    private void hud() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Run> e : runs.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                continue;
            }
            Run run = e.getValue();
            Location l = p.getLocation();
            if (l.getWorld() != world || Math.hypot(l.getX() - start[0], l.getZ() - start[2]) > 40) {
                Bukkit.getScheduler().runTask(plugin, () -> stop(p));
                continue;
            }
            p.sendActionBar(Component.text("⏱ ", TEAL).append(Component.text(format(now - run.started), NamedTextColor.WHITE))
                    .append(Component.text("   ⚑ " + (run.checkpoint + 1) + "/" + checkpoints.size(), SOFT)));
        }
    }

    private ItemStack tool(Material m, String en, String de, String id, Player player) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        boolean g = HubHolograms.german(player);
        meta.displayName(Component.text(g ? de : en, TEAL).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private void giveItems(Player player) {
        player.getInventory().setItem(6, tool(Material.FEATHER, "Back to checkpoint", "Zurück zum Checkpoint", "back", player));
        player.getInventory().setItem(7, tool(Material.CLOCK, "Restart", "Neu starten", "restart", player));
        player.getInventory().setItem(8, tool(Material.BARRIER, "Leave the Sky Path", "Himmelspfad verlassen", "quit", player));
    }

    private void takeItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /** Stepping on the course plates is ours: no WorldGuard "you can't use that here" in the chat. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE")
                && event.getClickedBlock().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND || event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }
        String id = event.getItem().getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            takeItems(player);
            return;
        }
        switch (id) {
            case "back" -> back(player, run, false);
            case "restart" -> {
                run.respawn = center(start, player.getLocation().getYaw());
                back(player, run, false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> begin(player), 2L);
            }
            default -> {
                stop(player);
                Location spawn = plugin.safeZone().spawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    /** Left-over course items (crash mid-run) never stay in an inventory. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        takeItems(event.getPlayer());
    }

    // ------------------------------------------------------------------ best times

    public List<Best> top(int n) {
        List<Best> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("best");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.add(new Best(s.getString(k + ".name", "?"), s.getLong(k + ".ms", Long.MAX_VALUE)));
            }
        }
        out.sort((a, b) -> Long.compare(a.millis(), b.millis()));
        return out.size() > n ? out.subList(0, n) : out;
    }

    public static String format(long ms) {
        if (ms == Long.MAX_VALUE || ms < 0) {
            return "--";
        }
        return String.format(Locale.ROOT, "%d.%02d s", ms / 1000, (ms % 1000) / 10);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save parkour.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                stop(p);
            }
        }
        save();
    }
}
