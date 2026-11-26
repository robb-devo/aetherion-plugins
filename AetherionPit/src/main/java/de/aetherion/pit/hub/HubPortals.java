package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.menu.PitShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sky-isle hub (11-26): walk-through star gates instead of NPCs, a void that throws you back, and the duck.
 *
 * <pre>
 * portals:
 *   enabled: true
 *   aetherion: {world: world, min: [x,y,z], max: [x,y,z], label: [x,y,z]}   # keys = actions
 * void-catch: {enabled: true, y: 0.0}
 * duck: {enabled: true, world: world, min: [..], max: [..], label: [..]}
 * </pre>
 * Actions: aetherion (proxy transfer), pit (to the arena), home (back to spawn), discord, modpack, shop.
 */
public final class HubPortals implements Listener {

    public record Gate(String key, World world, int[] min, int[] max, Location label) {
        boolean contains(Location l) {
            if (l.getWorld() != world) {
                return false;
            }
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            return x >= min[0] && x <= max[0] && y >= min[1] - 1 && y <= max[1] && z >= min[2] && z <= max[2];
        }

        Location center() {
            return new Location(world, (min[0] + max[0] + 1) / 2.0, (min[1] + max[1] + 1) / 2.0,
                    (min[2] + max[2] + 1) / 2.0);
        }

        /** 0 = x, 2 = z: the thin axis of the box is the gate's normal. */
        int normalAxis() {
            return (max[0] - min[0]) <= (max[2] - min[2]) ? 0 : 2;
        }
    }

    private static final TextColor PURPLE = TextColor.color(0xB06CFF);
    private static final TextColor PINK = TextColor.color(0xF08CFF);
    private static final TextColor DUCK = TextColor.color(0xFFD83B);
    private static final TextColor SOFT = TextColor.color(0xC9C2D6);

    private final AetherionPit plugin;
    private final List<Gate> gates = new ArrayList<>();
    private Gate duck;
    private final Map<String, Long> cooldown = new HashMap<>();
    private final Map<UUID, Integer> falls = new HashMap<>();
    private final File funFile;
    private final YamlConfiguration fun;
    private long quacks;
    private boolean voidCatch;
    private double voidY;

    public HubPortals(AetherionPit plugin) {
        this.plugin = plugin;
        this.funFile = new File(plugin.getDataFolder(), "fun.yml");
        this.fun = YamlConfiguration.loadConfiguration(funFile);
        this.quacks = fun.getLong("duck.quacks", 0L);
        load();
        loadFloaters();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sparkle, 40L, 6L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::animateFloaters, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveFun, 6000L, 6000L);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("portals.enabled", false);
    }

    public List<Gate> gates() {
        return gates;
    }

    public Gate gate(String key) {
        for (Gate g : gates) {
            if (g.key().equalsIgnoreCase(key)) {
                return g;
            }
        }
        return null;
    }

    public Gate duck() {
        return duck;
    }

    public long quacks() {
        return quacks;
    }

    private void load() {
        gates.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("portals");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection g = sec.getConfigurationSection(key);
                Gate gate = read(key.toLowerCase(Locale.ROOT), g);
                if (gate != null) {
                    gates.add(gate);
                }
            }
        }
        duck = plugin.getConfig().getBoolean("duck.enabled", false)
                ? read("duck", plugin.getConfig().getConfigurationSection("duck")) : null;
        voidCatch = plugin.getConfig().getBoolean("void-catch.enabled", false);
        voidY = plugin.getConfig().getDouble("void-catch.y", 0.0);
        props.clear();
        propBlocks.clear();
        ConfigurationSection ps = plugin.getConfig().getConfigurationSection("props");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(key);
                Gate prop = read(key.toLowerCase(Locale.ROOT), p);
                if (prop == null) {
                    continue;
                }
                props.add(prop);
                java.util.Set<Material> mats = new java.util.HashSet<>();
                for (String m : p.getStringList("blocks")) {
                    Material mat = Material.matchMaterial(m);
                    if (mat != null) {
                        mats.add(mat);
                    }
                }
                propBlocks.put(prop.key(), mats);
            }
        }
    }

    private final List<Gate> props = new ArrayList<>();
    private final Map<String, java.util.Set<Material>> propBlocks = new HashMap<>();

    public List<Gate> props() {
        return props;
    }

    // ------------------------------------------------------------------ props: bell, spell book, shop

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPropClick(PlayerInteractEvent event) {
        if (props.isEmpty() || event.getClickedBlock() == null || event.getHand() == EquipmentSlot.OFF_HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block b = event.getClickedBlock();
        for (Gate prop : props) {
            if (!prop.contains(b.getLocation())) {
                continue;
            }
            java.util.Set<Material> mats = propBlocks.get(prop.key());
            if (mats != null && !mats.isEmpty() && !mats.contains(b.getType())) {
                return;
            }
            Player player = event.getPlayer();
            // handled here, before WorldGuard and DeluxeHub would deny it with a chat message
            event.setCancelled(true);
            if (prop.key().equals("discord")) {
                if (!(b.getState() instanceof org.bukkit.block.Bell bell) || !bell.ring(player)) {
                    b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_BELL_USE, 1.2f, 1.0f);
                }
            }
            if (onCooldown(player, "prop:" + prop.key(), 1500L)) {
                return;
            }
            switch (prop.key()) {
                case "discord" -> {
                    b.getWorld().spawnParticle(Particle.NOTE, b.getLocation().add(0.5, 1.2, 0.5), 6, 0.5, 0.3, 0.5, 1);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.guide().discordBook(player), 6L);
                }
                case "modpack" -> {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    b.getWorld().spawnParticle(Particle.ENCHANT, b.getLocation().add(0.5, 1.4, 0.5), 40, 0.6, 0.5, 0.6, 0.6);
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.guide().modpackBook(player));
                }
                case "shop" -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.0f);
                    Bukkit.getScheduler().runTask(plugin, () -> PitShopGUI.open(player));
                }
                default -> {
                }
            }
            return;
        }
    }

    // ------------------------------------------------------------------ floaters: slow animated displays

    private record Floater(boolean item, Material material, Location at, float scale, float bob, float spin, float tilt,
                           int code) {
    }

    private final List<Floater> floaters = new ArrayList<>();
    private final Map<Integer, org.bukkit.entity.Display> floaterEntities = new HashMap<>();
    private int floaterStep;

    private void loadFloaters() {
        floaters.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("floaters")) {
            try {
                World w = Bukkit.getWorld(String.valueOf(m.containsKey("world") ? m.get("world") : "world"));
                String type = String.valueOf(m.get("type"));
                int code = (int) num(m, "code", 0);
                Material mat = "model".equals(type) ? HubModels.BASE : Material.matchMaterial(String.valueOf(m.get("material")));
                if (w == null || mat == null) {
                    continue;
                }
                Location at = new Location(w, num(m, "x", 0), num(m, "y", 64), num(m, "z", 0));
                floaters.add(new Floater("item".equals(type) || "model".equals(type), mat, at,
                        (float) num(m, "scale", 1), (float) num(m, "bob", 0.25), (float) num(m, "spin", 90),
                        (float) num(m, "tilt", 0), code));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Bad floater " + m + ": " + ex.getMessage());
            }
        }
    }

    private static double num(Map<?, ?> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private void animateFloaters() {
        floaterStep++;
        for (int i = 0; i < floaters.size(); i++) {
            Floater f = floaters.get(i);
            World w = f.at().getWorld();
            if (!w.isChunkLoaded(f.at().getBlockX() >> 4, f.at().getBlockZ() >> 4)) {
                continue;
            }
            org.bukkit.entity.Display d = floaterEntities.get(i);
            if (d == null || !d.isValid()) {
                d = f.item()
                        ? w.spawn(f.at(), org.bukkit.entity.ItemDisplay.class, e -> {
                            e.setItemStack(f.code() > 0 ? HubModels.item(f.code())
                                    : new org.bukkit.inventory.ItemStack(f.material()));
                            if (f.code() > 0) {
                                e.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                            }
                            e.setPersistent(false);
                        })
                        : w.spawn(f.at(), org.bukkit.entity.BlockDisplay.class, e -> {
                            e.setBlock(f.material().createBlockData());
                            e.setPersistent(false);
                        });
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setViewRange(1.5f);
                floaterEntities.put(i, d);
            }
            float angle = (float) Math.toRadians(f.spin() * floaterStep + i * 37);
            float y = (floaterStep % 2 == 0 ? 1 : -1) * f.bob();
            org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY(angle).rotateX((float) Math.toRadians(f.tilt()))
                    .rotateZ((float) Math.toRadians(f.tilt()));
            org.joml.Vector3f t;
            if (f.item()) {
                t = new org.joml.Vector3f(0, y, 0);
            } else {
                float h = f.scale() * 0.5f;
                t = new org.joml.Vector3f(h, h, h).rotate(q).negate().add(0, y, 0);
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(40);
            d.setTransformation(new org.bukkit.util.Transformation(t, q,
                    new org.joml.Vector3f(f.scale(), f.scale(), f.scale()), new org.joml.Quaternionf()));
        }
    }

    public void removeFloaters() {
        floaterEntities.values().forEach(e -> {
            if (e.isValid()) {
                e.remove();
            }
        });
        floaterEntities.clear();
    }

    private Gate read(String key, ConfigurationSection g) {
        if (g == null || !g.isList("min") || !g.isList("max")) {
            return null;
        }
        World world = Bukkit.getWorld(g.getString("world", "world"));
        if (world == null) {
            return null;
        }
        int[] a = ints(g.getDoubleList("min"));
        int[] b = ints(g.getDoubleList("max"));
        int[] min = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
        int[] max = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
        Location label = null;
        List<Double> l = g.getDoubleList("label");
        if (l.size() >= 3) {
            label = new Location(world, l.get(0), l.get(1), l.get(2));
        }
        return new Gate(key, world, min, max, label);
    }

    private static int[] ints(List<Double> v) {
        return new int[]{(int) Math.floor(v.get(0)), (int) Math.floor(v.get(1)), (int) Math.floor(v.get(2))};
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (voidCatch && plugin.safeZone().isPitWorld(to.getWorld())) {
            Material under = to.clone().subtract(0, 0.2, 0).getBlock().getType();
            if (to.getY() < voidY || under == Material.WHITE_WOOL || under == Material.SNOW_BLOCK) {
                catchFromVoid(player, to);
                return;
            }
        }
        if (enabled()) {
            for (Gate gate : gates) {
                if (gate.contains(to)) {
                    enter(player, gate, from);
                    return;
                }
            }
        }
        if (duck != null) {
            Location below = to.clone().subtract(0, 0.2, 0);
            if (duck.contains(below) && below.getBlock().getType() == Material.YELLOW_CONCRETE) {
                quack(player, below.getBlock(), true);
            }
        }
    }

    private boolean onCooldown(Player player, String key, long ms) {
        String k = player.getUniqueId() + ":" + key;
        long now = System.currentTimeMillis();
        Long until = cooldown.get(k);
        if (until != null && until > now) {
            return true;
        }
        cooldown.put(k, now + ms);
        return false;
    }

    private void enter(Player player, Gate gate, Location from) {
        if (onCooldown(player, gate.key(), 2500L)) {
            return;
        }
        boolean de = HubHolograms.german(player);
        Location c = gate.center();
        switch (gate.key()) {
            case "aetherion" -> {
                burst(gate, Color.fromRGB(0xC77DFF));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
                player.showTitle(Title.title(
                        Component.text("AETHERION", PURPLE, TextDecoration.BOLD),
                        Component.text(de ? "Das Sternentor öffnet sich ..." : "The star gate opens ...", PINK),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
                plugin.transfer().toAetherion(player);
                // still here after 3 s: the MMO did not take them. Step back out of the gate.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && gate.contains(player.getLocation())) {
                        pushOut(player, gate, from);
                        player.sendMessage(Component.text(de
                                ? "Aetherion antwortet gerade nicht. Versuch es gleich noch mal."
                                : "Aetherion is not answering right now. Try again in a moment.", SOFT));
                    }
                }, 60L);
            }
            case "pit" -> {
                burst(gate, Color.fromRGB(0xFF3B3B));
                player.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.6f);
                plugin.guide().enterPit(player);
            }
            case "home" -> {
                Location spawn = plugin.safeZone().spawn();
                if (spawn != null) {
                    player.teleport(spawn);
                    player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
                }
            }
            case "discord" -> {
                pushOut(player, gate, from);
                player.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                plugin.hubNpcs().openDiscord(player);
            }
            case "modpack" -> {
                pushOut(player, gate, from);
                player.playSound(c, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                plugin.guide().modpackCard(player);
            }
            case "shop" -> {
                pushOut(player, gate, from);
                player.playSound(c, Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.0f);
                Bukkit.getScheduler().runTask(plugin, () -> PitShopGUI.open(player));
            }
            default -> {
            }
        }
    }

    /** Back out of the gate on the side the player came from. */
    private void pushOut(Player player, Gate gate, Location from) {
        Location c = gate.center();
        int axis = gate.normalAxis();
        double side = axis == 0 ? from.getX() - c.getX() : from.getZ() - c.getZ();
        double s = side >= 0 ? 1 : -1;
        Vector v = axis == 0 ? new Vector(s * 0.9, 0.35, 0) : new Vector(0, 0.35, s * 0.9);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(v);
            }
        });
    }

    private void catchFromVoid(Player player, Location at) {
        if (onCooldown(player, "void", 1500L)) {
            return;
        }
        if (plugin.parkour() != null && plugin.parkour().catchFall(player)) {
            return;
        }
        Location target = plugin.safeZone().spawn();
        Location pitEntrance = location("pit-entrance");
        if (pitEntrance != null && !plugin.safeZone().isSafe(at)) {
            target = pitEntrance;
        }
        if (target == null) {
            return;
        }
        int n = falls.merge(player.getUniqueId(), 1, Integer::sum);
        boolean de = HubHolograms.german(player);
        Location dest = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0, 0));
            player.teleport(dest);
            player.playSound(dest, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.8f);
            player.playSound(dest, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.6f);
            String[] en = {"The clouds threw you back.", "Nope. The void is closed today.",
                    "Gravity says hi.", "Flying lessons start next week.", "The sky does not want you yet."};
            String[] dee = {"Die Wolken haben dich zurückgeworfen.", "Nö. Die Leere hat heute zu.",
                    "Die Schwerkraft lässt grüßen.", "Flugstunden gibt's erst nächste Woche.",
                    "Der Himmel will dich noch nicht."};
            int i = ThreadLocalRandom.current().nextInt(en.length);
            player.showTitle(Title.title(
                    Component.text(de ? "Hoppla!" : "Whoops!", NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text((de ? dee[i] : en[i]) + (n > 1 ? "  (#" + n + ")" : ""), SOFT),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(500))));
        });
    }

    private Location location(String path) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", "world"));
        if (w == null) {
            return null;
        }
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    // ------------------------------------------------------------------ vanilla portals off

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (plugin.safeZone().isPitWorld(event.getLocation().getWorld()) && event instanceof Cancellable c) {
            c.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (plugin.safeZone().isPitWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        if (plugin.safeZone().isPitWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ the duck

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (duck == null || event.getClickedBlock() == null || event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block b = event.getClickedBlock();
        if (!duck.contains(b.getLocation())) {
            return;
        }
        Material m = b.getType();
        if (m != Material.YELLOW_CONCRETE && m != Material.ORANGE_CONCRETE && m != Material.BLACK_CONCRETE
                && m != Material.WHITE_CONCRETE && m != Material.YELLOW_TERRACOTTA) {
            return;
        }
        quack(event.getPlayer(), b, false);
    }

    private void quack(Player player, Block at, boolean stepped) {
        if (onCooldown(player, "duck", stepped ? 900L : 350L)) {
            return;
        }
        quacks++;
        Location loc = at.getLocation().add(0.5, 1.1, 0.5);
        World w = loc.getWorld();
        float pitch = 0.75f + ThreadLocalRandom.current().nextFloat() * 0.6f;
        w.playSound(loc, Sound.ENTITY_CHICKEN_HURT, 1.2f, pitch);
        w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 1.6f);
        w.spawnParticle(Particle.NOTE, loc, 3, 0.4, 0.3, 0.4, 1.0);
        if (duck.label() != null) {
            w.spawnParticle(Particle.CLOUD, duck.label().clone().add(0, -3.5, 0), 4, 0.6, 0.3, 0.6, 0.01);
        }
        boolean de = HubHolograms.german(player);
        player.sendActionBar(Component.text(de ? "QUIETSCH!  " : "SQUEAK!  ", DUCK, TextDecoration.BOLD)
                .append(Component.text(String.format(Locale.ROOT, de ? "Die Ente wurde %,d-mal gedrückt"
                        : "The duck has been squeezed %,d times", quacks), SOFT)));
        if (quacks % 1000 == 0) {
            for (Player p : w.getPlayers()) {
                boolean pde = HubHolograms.german(p);
                p.sendMessage(Component.text("  ").append(Component.text(pde ? "QUIETSCH! " : "SQUEAK! ", DUCK,
                        TextDecoration.BOLD)).append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(String.format(Locale.ROOT, pde ? " hat die Ente zum %,d. Mal gedrückt."
                                : " squeezed the duck for the %,dth time.", quacks), SOFT)));
            }
        }
        plugin.guide().refreshDuck();
    }

    public void saveFun() {
        fun.set("duck.quacks", quacks);
        try {
            fun.save(funFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save fun.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String id = event.getPlayer().getUniqueId().toString();
        cooldown.keySet().removeIf(k -> k.startsWith(id));
        falls.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ ambience

    private static final Map<String, Color> TINT = Map.of(
            "aetherion", Color.fromRGB(0xC77DFF), "pit", Color.fromRGB(0xFF3B3B), "home", Color.fromRGB(0xE8F6FF),
            "discord", Color.fromRGB(0x5865F2), "modpack", Color.fromRGB(0x57F287), "shop", Color.fromRGB(0xFFC94A));

    private void sparkle() {
        if (!enabled()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Gate gate : gates) {
            World w = gate.world();
            Location c = gate.center();
            if (w.getPlayers().stream().noneMatch(p -> p.getLocation().distanceSquared(c) < 64 * 64)) {
                continue;
            }
            Color color = TINT.getOrDefault(gate.key(), Color.WHITE);
            Particle.DustOptions dust = new Particle.DustOptions(color, gate.key().equals("aetherion") ? 0.9f : 0.8f);
            int n = gate.key().equals("aetherion") ? 6 : 3;
            for (int i = 0; i < n; i++) {
                double x = gate.min()[0] + r.nextDouble() * (gate.max()[0] - gate.min()[0] + 1);
                double y = gate.min()[1] + r.nextDouble() * (gate.max()[1] - gate.min()[1] + 1);
                double z = gate.min()[2] + r.nextDouble() * (gate.max()[2] - gate.min()[2] + 1);
                Location p = new Location(w, x, y, z);
                if (p.getBlock().getType() != Material.END_GATEWAY) {
                    continue;
                }
                w.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0, dust);
                if (r.nextInt(4) == 0) {
                    w.spawnParticle(Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }
    }

    private void burst(Gate gate, Color color) {
        Location c = gate.center();
        c.getWorld().spawnParticle(Particle.DUST, c, 60, 1.5, 2.5, 1.5, 0, new Particle.DustOptions(color, 2.0f));
        c.getWorld().spawnParticle(Particle.REVERSE_PORTAL, c, 80, 1.0, 2.0, 1.0, 0.1);
    }
}
