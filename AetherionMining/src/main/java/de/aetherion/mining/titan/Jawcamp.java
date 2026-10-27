package de.aetherion.mining.titan;

import de.aetherion.mining.AetherionMining;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jawcamp: the hub of the mine server, a camp on the lower jaw inside Ossarath's skull (the main world of mmo-m,
 * never reset). Fangs stand like pillars along the open mouth, light falls through the eye sockets, the Gullet
 * waits at the back of the throat and the Veins (the Amethyst Mine) open from a side alcove.
 */
public final class Jawcamp implements Listener {

    /** Bump when the skull is redrawn: the next start clears the box and builds it again. */
    private static final int BUILD = 1;
    private static final int OY = 100;
    private static final int GX = -30;
    private static final double GULLET_R = 5.2;

    private final AetherionMining plugin;
    private final TitanWorld titan;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final List<Entity> placed = new ArrayList<>();
    private World world;
    private TextDisplay board;
    private boolean built;

    public Jawcamp(AetherionMining plugin, TitanWorld titan) {
        this.plugin = plugin;
        this.titan = titan;
        this.npcKey = new NamespacedKey(plugin, "jawcamp_npc");
    }

    public World world() {
        return world;
    }

    public boolean ready() {
        return built && world != null;
    }

    public Location spawn() {
        if (world == null) {
            return null;
        }
        return new Location(world, 14.5, OY, 0.5, 90f, 0f);
    }

    public boolean isCamp(World check) {
        return check != null && world != null && check.getUID().equals(world.getUID());
    }

    // ------------------------------------------------------------------ start

    public void start() {
        world = Bukkit.getWorlds().getFirst();
        YamlConfiguration state = YamlConfiguration.loadConfiguration(titan.stateFile());
        int have = state.getInt("jawcamp-built", 0);
        world.setSpawnLocation(spawn());
        for (int cx = -4; cx <= 3; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
        }
        if (have >= BUILD) {
            built = true;
            populate();
            return;
        }
        List<Object[]> blocks = design();
        plugin.getLogger().info("[Jawcamp] Building the skull camp (" + blocks.size() + " blocks)…");
        int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(blocks.size(), index[0] + 6000);
            for (int i = index[0]; i < end; i++) {
                Object[] b = blocks.get(i);
                world.getBlockAt((int) b[0], (int) b[1], (int) b[2]).setBlockData((BlockData) b[3], false);
            }
            index[0] = end;
            if (end >= blocks.size()) {
                task.cancel();
                built = true;
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(titan.stateFile());
                yaml.set("jawcamp-built", BUILD);
                try {
                    yaml.save(titan.stateFile());
                } catch (IOException ex) {
                    plugin.getLogger().warning("[Jawcamp] Could not save build flag: " + ex.getMessage());
                }
                plugin.getLogger().info("[Jawcamp] Skull camp built.");
                populate();
            }
        }, 1L, 1L);
    }

    public void shutdown() {
        for (Entity entity : placed) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        placed.clear();
    }

    // ------------------------------------------------------------------ the skull

    private static double ell(double x, double y, double z, double cx, double cy, double cz,
                              double rx, double ry, double rz) {
        double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean outer(double x, double y, double z) {
        return ell(x, y, z, -16, 14, 0, 32, 24, 28) < 1
                || ell(x, y, z, 18, 8, 0, 34, 13, 21) < 1
                || ell(x, y, z, 14, -4, 0, 40, 7, 23) < 1;
    }

    private static boolean inner(double x, double y, double z) {
        return y >= 0 && (ell(x, y, z, -16, 14, 0, 28, 20, 24) < 1 || ell(x, y, z, 18, 7, 0, 34, 9, 17) < 1);
    }

    private static boolean mouthGap(double x, double y, double z) {
        return x > 38 && y >= 0 && y <= 9 && Math.abs(z) < 19;
    }

    private static boolean socket(double x, double y, double z) {
        return ell(x, y, z, -6, 24, 22, 7, 7, 7) < 1 || ell(x, y, z, -6, 24, -22, 7, 7, 7) < 1
                || ell(x, y, z, 48, 14, 5, 2.2, 2.2, 2.2) < 1 || ell(x, y, z, 48, 14, -5, 2.2, 2.2, 2.2) < 1;
    }

    private static boolean tooth(double x, double y, double z) {
        for (int i = 0; i < 9; i++) {
            double t = -1.1 + i * 0.275;
            double tx = 18 + 33 * Math.cos(t), tz = 16.5 * Math.sin(t);
            if (y >= 2 && y <= 10) {
                double r = 2.0 * (y - 2) / 8.0 + 0.4;
                if ((x - tx) * (x - tx) + (z - tz) * (z - tz) < r * r) {
                    return true;
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double t = -0.9625 + i * 0.275;
            double tx = 18 + 32 * Math.cos(t), tz = 15.5 * Math.sin(t);
            if (y >= -1 && y <= 5) {
                double r = 1.7 * (5 - y) / 6.0 + 0.3;
                if ((x - tx) * (x - tx) + (z - tz) * (z - tz) < r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double hash(int x, int y, int z, int salt) {
        long h = x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Every block of the camp, in local coordinates around the jaw floor (y 0 = where people stand). */
    private List<Object[]> design() {
        BlockData bone = Bukkit.createBlockData("minecraft:bone_block[axis=y]");
        BlockData calcite = Material.CALCITE.createBlockData();
        BlockData dripstone = Material.DRIPSTONE_BLOCK.createBlockData();
        BlockData tuff = Material.TUFF.createBlockData();
        BlockData barrier = Material.BARRIER.createBlockData();
        BlockData pink = Material.PINK_TERRACOTTA.createBlockData();
        BlockData red = Material.RED_TERRACOTTA.createBlockData();
        BlockData wart = Material.NETHER_WART_BLOCK.createBlockData();
        BlockData shroom = Material.SHROOMLIGHT.createBlockData();
        BlockData planks = Material.SPRUCE_PLANKS.createBlockData();
        BlockData amethyst = Material.AMETHYST_BLOCK.createBlockData();
        BlockData light = Bukkit.createBlockData("minecraft:light[level=13]");
        BlockData air = Material.AIR.createBlockData();
        List<Object[]> out = new ArrayList<>();
        Map<Long, BlockData> set = new java.util.LinkedHashMap<>();

        for (int x = -50; x <= 56; x++) {
            for (int y = -14; y <= 40; y++) {
                for (int z = -28; z <= 28; z++) {
                    double gx = x - GX, gz = z;
                    double g = Math.sqrt(gx * gx + gz * gz);
                    BlockData block = null;
                    if (g < 7.5 && y >= -13 && y <= -1) {
                        // The gullet: a flesh-walled throat under a membrane you can stand on.
                        if (g < GULLET_R + 0.3) {
                            block = y == -1 ? barrier : (y == -13 ? (hash(x, y, z, 5) < 0.2 ? shroom : wart) : null);
                        } else {
                            block = y == -1 ? red : (hash(x, y, z, 6) < 0.5 ? pink : red);
                        }
                    } else if (Math.abs(x - GX) < 1.6 && Math.abs(z) < 1.6 && y >= 11 && y <= 31) {
                        block = y < 13 ? wart : pink;
                    } else if (tooth(x, y, z)) {
                        block = calcite;
                    } else if (outer(x, y, z) && !inner(x, y, z) && !mouthGap(x, y, z) && !socket(x, y, z)) {
                        double h = hash(x, y, z, 1);
                        block = h < 0.68 ? bone : h < 0.88 ? calcite : h < 0.95 ? dripstone : tuff;
                    } else if (mouthGap(x, y, z) && !inner(x, y, z)) {
                        block = barrier;
                    } else if (inner(x, y, z) && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0
                            && (y == 4 || y == 12 || y == 20)) {
                        block = light;
                    }
                    if (block != null) {
                        set.put(key(x, y, z), block);
                    }
                }
            }
        }
        // Walkway from the mouth to the throat.
        for (int x = -23; x <= 46; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.hypot(x - GX, z) > 7.5 && set.containsKey(key(x, -1, z))) {
                    set.put(key(x, -1, z), planks);
                }
            }
        }
        // The Veins alcove on the left wall, framed in amethyst.
        for (int x = 4; x <= 12; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -16; z <= -11; z++) {
                    boolean frame = (x == 4 || x == 12 || y == 5) && z >= -12;
                    boolean hollow = x > 4 && x < 12 && y < 5;
                    if (frame) {
                        set.put(key(x, y, z), amethyst);
                    } else if (hollow) {
                        set.put(key(x, y, z), y == 2 && z == -14 && x == 8 ? light : air);
                    }
                }
            }
            set.put(key(x, -1, -14), amethyst);
            set.put(key(x, -1, -15), amethyst);
            set.put(key(x, 5, -15), amethyst);
        }
        // Camp: fire, seats, tents, crates, hay, a crane at the mouth and lanterns from the palate.
        set.put(key(8, 0, -3), Bukkit.createBlockData("minecraft:campfire[lit=true]"));
        set.put(key(6, 0, -6), Bukkit.createBlockData("minecraft:stripped_spruce_log[axis=x]"));
        set.put(key(10, 0, -6), Bukkit.createBlockData("minecraft:stripped_spruce_log[axis=x]"));
        set.put(key(5, 0, -1), Bukkit.createBlockData("minecraft:stripped_spruce_log[axis=z]"));
        set.put(key(11, 0, -1), Bukkit.createBlockData("minecraft:stripped_spruce_log[axis=z]"));
        tent(set, 16, 6, 5, Material.WHITE_WOOL);
        tent(set, -10, 8, 5, Material.BROWN_WOOL);
        tent(set, 20, -8, 4, Material.LIGHT_GRAY_WOOL);
        BlockData lid = Bukkit.createBlockData("minecraft:spruce_trapdoor[half=top,open=false]");
        for (int[] c : new int[][]{{27, 6}, {28, 6}, {27, 7}, {-4, -9}, {-3, -9}}) {
            set.put(key(c[0], 0, c[1]), planks);
            set.put(key(c[0], 1, c[1]), lid);
        }
        BlockData hay = Bukkit.createBlockData("minecraft:hay_block[axis=y]");
        set.put(key(-6, 0, 11), hay);
        set.put(key(-6, 1, 11), hay);
        set.put(key(-5, 0, 11), hay);
        BlockData fence = Material.SPRUCE_FENCE.createBlockData();
        BlockData chain = Bukkit.createBlockData("minecraft:chain[axis=y]");
        BlockData hanging = Bukkit.createBlockData("minecraft:lantern[hanging=true]");
        for (int y = 0; y <= 9; y++) {
            set.put(key(40, y, -10), fence);
        }
        for (int x = 41; x <= 45; x++) {
            set.put(key(x, 9, -10), fence);
        }
        for (int y = 5; y <= 8; y++) {
            set.put(key(45, y, -10), chain);
        }
        set.put(key(45, 4, -10), hanging);
        for (int[] c : new int[][]{{-8, 5}, {12, -4}, {24, 3}, {-20, -5}, {0, 9}}) {
            int top = 0;
            while (top < 40 && inner(c[0], top + 1, c[1])) {
                top++;
            }
            for (int y = top; y >= 8; y--) {
                set.put(key(c[0], y, c[1]), chain);
            }
            set.put(key(c[0], 7, c[1]), hanging);
        }
        for (Map.Entry<Long, BlockData> entry : set.entrySet()) {
            long k = entry.getKey();
            out.add(new Object[]{(int) (k >> 40) - 1024, (int) ((k >> 20) & 0xFFFFF) - 1024 + OY,
                    (int) (k & 0xFFFFF) - 1024, entry.getValue()});
        }
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 1024) << 40) | ((long) (y + 1024) << 20) | (z + 1024);
    }

    /** A small A-frame tent along x: two walls, two slopes, a ridge, open at both ends. */
    private static void tent(Map<Long, BlockData> set, int x0, int z0, int length, Material wool) {
        BlockData cloth = wool.createBlockData();
        for (int dx = 0; dx < length; dx++) {
            int x = x0 + dx;
            set.put(key(x, 0, z0 - 2), cloth);
            set.put(key(x, 0, z0 + 2), cloth);
            set.put(key(x, 1, z0 - 1), cloth);
            set.put(key(x, 1, z0 + 1), cloth);
            set.put(key(x, 2, z0), cloth);
        }
    }

    // ------------------------------------------------------------------ NPCs and boards

    private void populate() {
        for (Entity entity : placed) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        placed.clear();
        world.getChunkAt(2, 0);
        world.getChunkAt(-2, 0);
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
        placed.add(npc(new Location(world, 34.5, OY, 7.5, 90f, 0f), "brann", "§6Foreman Brann",
                Villager.Profession.TOOLSMITH));
        placed.add(npc(new Location(world, -23.5, OY, 8.5, -90f, 0f), "keeper", "§cGullet Keeper",
                Villager.Profession.CLERIC));
        placed.add(label(new Location(world, GX + 0.5, OY + 4.5, 0.5), "§4§lThe Gullet\n§7Mining 40 · step onto the throat"));
        placed.add(label(new Location(world, 8.5, OY + 6.6, -11.5), "§d§lThe Veins\n§7Amethyst Mine · Mining 30"));
        board = label(new Location(world, 34.5, OY + 3.4, 10.0), boardText());
        placed.add(board);
        Bukkit.getScheduler().runTaskTimer(plugin, this::ambience, 40L, 10L);
    }

    private Villager npc(Location at, String id, String name, Villager.Profession profession) {
        Villager villager = world.spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
            v.setProfession(profession);
            v.setVillagerType(Villager.Type.TAIGA);
            v.setVillagerLevel(5);
            v.customName(LegacyComponentSerializer.legacySection().deserialize(name));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
        });
        villager.setRotation(at.getYaw(), 0f);
        return villager;
    }

    private TextDisplay label(Location at, String text) {
        return world.spawn(at, TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacySection().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "label");
        });
    }

    private String boardText() {
        int hour = plugin.getConfig().getInt("titan.churn-hour", 5);
        String state = titan.open() ? "§aThe Gullet is open" : "§eOssarath is churning";
        return "§6§lJawcamp\n" + state + "\n§7Three swallowed isles drift in the belly\n§8The Churn: "
                + String.format("%02d", hour) + ":00";
    }

    public void refreshBoard() {
        if (board != null && board.isValid()) {
            board.text(LegacyComponentSerializer.legacySection().deserialize(boardText()));
        }
    }

    public void announce(String line) {
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(line);
        }
        refreshBoard();
    }

    private void ambience() {
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        world.spawnParticle(Particle.PORTAL, 8.5, OY + 1.5, -13.5, 12, 2.5, 1.5, 1.2, 0.6);
        world.spawnParticle(Particle.CRIMSON_SPORE, GX + 0.5, OY + 0.5, 0.5, 6, 3.5, 0.3, 3.5, 0.0);
    }

    // ------------------------------------------------------------------ the two ways down

    private boolean cooled(Player player) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < 3000L) {
            return false;
        }
        cooldown.put(player.getUniqueId(), now);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !isCamp(event.getTo().getWorld()) || !built) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        double lx = to.getX(), ly = to.getY() - OY, lz = to.getZ();
        if (ly >= -0.5 && ly <= 2.5 && Math.hypot(lx - (GX + 0.5), lz - 0.5) < GULLET_R && cooled(player)) {
            gullet(player);
        } else if (lx >= 5 && lx <= 12 && ly >= -0.5 && ly <= 4 && lz >= -16 && lz <= -12.5 && cooled(player)) {
            veins(player);
        }
    }

    private void gullet(Player player) {
        int need = plugin.getConfig().getInt("titan.gullet-mining-level", 40);
        if (!MineGate.reaches(player, need)) {
            player.setVelocity(new Vector(0.7, 0.35, 0));
            player.sendMessage("§cGullet Keeper§f: Not with Mining under " + need + ". It would keep you.");
            return;
        }
        titan.swallow(player);
    }

    private void veins(Player player) {
        int need = plugin.getConfig().getInt("titan.veins-mining-level", 30);
        if (!MineGate.reaches(player, need)) {
            player.setVelocity(new Vector(0, 0.3, 0.6));
            player.sendMessage("§dThe Veins §7open at Mining " + need + ".");
            return;
        }
        if (plugin.getVeins() != null) {
            plugin.getVeins().enter(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpc(PlayerInteractEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcAt(PlayerInteractAtEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    private void handle(Player player, Entity entity, EquipmentSlot hand, org.bukkit.event.Cancellable event) {
        String id = entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (hand != EquipmentSlot.HAND || !cooled(player)) {
            return;
        }
        switch (id) {
            case "brann" -> {
                player.sendMessage("§6Foreman Brann§f: Up top already? Fine. The cage hauls you back to the main island.");
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1f, 0.7f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.getVeins() != null) {
                        plugin.getVeins().exitToMain(player);
                    }
                }, 30L);
            }
            case "keeper" -> {
                player.sendMessage("§cGullet Keeper§f: Step onto the throat and it swallows you. Mining "
                        + plugin.getConfig().getInt("titan.gullet-mining-level", 40) + ", and mind the fall.");
                player.sendMessage("§cGullet Keeper§f: Back up? Touch the lodestone where you land.");
            }
            default -> {
            }
        }
    }
}
