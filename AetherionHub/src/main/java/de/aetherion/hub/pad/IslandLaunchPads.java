package de.aetherion.hub.pad;

import de.aetherion.core.entity.DisplayEntities;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hypixel-style directional slime pads: high arc toward a target, with short
 * multi-tick horizontal boost so air-drag does not kill the jump mid-flight.
 */
public final class IslandLaunchPads implements Listener {

    private static final String LABEL_TAG = "aetherion_jump_pad";

    private final JavaPlugin plugin;
    private final List<Pad> pads = new ArrayList<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    /** Epoch ms — fall damage cancelled while now < value. */
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    /** Players mid-pad-flight — no WASD air-steer. */
    private final Set<UUID> flightLocked = new HashSet<>();
    private final Map<UUID, Float> savedWalkSpeed = new HashMap<>();
    private static final long LANDING_GRACE_MS = 3000L;
    private static final String PAD_LOCK_MOD = "pad_flight_lock";

    public IslandLaunchPads(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        // Wipe old pad zones BEFORE clearing the list (orphans live in those chunks).
        clearLabels();
        pads.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("island-pads");
        if (root == null || !root.getBoolean("enabled", true)) {
            return;
        }
        ConfigurationSection list = root.getConfigurationSection("pads");
        if (list == null) {
            return;
        }
        for (String id : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Double tx = null;
            Double ty = null;
            Double tz = null;
            ConfigurationSection target = s.getConfigurationSection("target");
            if (target != null) {
                if (target.isSet("x")) {
                    tx = target.getDouble("x");
                }
                if (target.isSet("y")) {
                    ty = target.getDouble("y");
                }
                if (target.isSet("z")) {
                    tz = target.getDouble("z");
                }
            }
            Pad pad = new Pad(
                    id,
                    s.getString("world", "world"),
                    intAt(s, "min.x", "min", "x", 0),
                    intAt(s, "min.y", "min", "y", 0),
                    intAt(s, "min.z", "min", "z", 0),
                    intAt(s, "max.x", "max", "x", 0),
                    intAt(s, "max.y", "max", "y", 0),
                    intAt(s, "max.z", "max", "z", 0),
                    s.getDouble("velocity.x", 0.0),
                    s.getDouble("velocity.y", 1.2),
                    s.getDouble("velocity.z", 0.0),
                    tx,
                    ty,
                    tz,
                    s.getDouble("arc-height", 48.0),
                    s.getInt("boost-ticks", 55),
                    s.getDouble("horiz-speed", 0.0),
                    s.getLong("cooldown-ms", 1200L),
                    s.getBoolean("require-stamped-blueprint",
                            root.getBoolean("require-stamped-blueprint", false)),
                    s.getStringList("require-blueprint-ids").isEmpty()
                            ? root.getStringList("require-blueprint-ids")
                            : s.getStringList("require-blueprint-ids")
            );
            pads.add(pad);
            spawnLabel(pad);
            plugin.getLogger().info("  pad " + id + " @ " + pad.minX() + "," + pad.minY() + "," + pad.minZ()
                    + " -> target "
                    + (tx == null ? "-" : tx + "," + ty + "," + tz)
                    + (pad.requireStamped() ? " [locked]" : ""));
        }
        plugin.getLogger().info("Island launch pads loaded: " + pads.size());
    }

    public int padCount() {
        return pads.size();
    }

    private static int intAt(ConfigurationSection s, String dotted, String section, String key, int def) {
        if (s.isSet(dotted)) {
            return s.getInt(dotted, def);
        }
        ConfigurationSection child = s.getConfigurationSection(section);
        if (child != null && child.isSet(key)) {
            return child.getInt(key, def);
        }
        Object raw = s.get(section);
        if (raw instanceof Map<?, ?> map) {
            Object v = map.get(key);
            if (v instanceof Number n) {
                return n.intValue();
            }
        }
        return def;
    }

    private void clearLabels() {
        for (Pad pad : pads) {
            World world = Bukkit.getWorld(pad.world());
            if (world != null) {
                wipePadLabelZone(world, pad);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) {
                if (isJumpPadLabel(display)) {
                    display.remove();
                }
            }
        }
    }

    /** Tagged labels + leftover arrow / Jump-Pad text (incl. old multiline). */
    private static boolean isJumpPadLabel(TextDisplay display) {
        for (String tag : display.getScoreboardTags()) {
            if (tag != null && tag.startsWith(LABEL_TAG)) {
                return true;
            }
        }
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(display.text())
                .toLowerCase(Locale.ROOT)
                .replace('\n', ' ')
                .trim();
        if (plain.isEmpty()) {
            return false;
        }
        boolean arrow = plain.contains("→") || plain.contains("➜") || plain.contains("->");
        return plain.contains("jump pad")
                || plain.contains("junp pad")
                || plain.contains("forage isle")
                || (arrow && (plain.contains("harbour") || plain.contains("harbor")
                || plain.contains("eldervale") || plain.contains("forage") || plain.contains("shabby")))
                || plain.contains("forage island jump")
                || plain.contains("eldervale jump")
                || plain.contains("shabby mine jump")
                || plain.contains("harbour jump")
                || plain.contains("harbor jump");
    }

    private static Location labelLocation(World world, Pad pad) {
        double x = (pad.minX() + pad.maxX()) / 2.0 + 0.5;
        double y = Math.max(pad.minY(), pad.maxY()) + 1.35;
        double z = (pad.minZ() + pad.maxZ()) / 2.0 + 0.5;
        return new Location(world, x, y, z);
    }

    /**
     * Force-load the pad chunk, then delete every TextDisplay in the pad column.
     * Old persistent orphans only appear after chunk load — wipe must run after that.
     */
    private void wipePadLabelZone(World world, Pad pad) {
        Location at = labelLocation(world, pad);
        world.getChunkAt(at);
        double minX = Math.min(pad.minX(), pad.maxX()) - 4.0;
        double maxX = Math.max(pad.minX(), pad.maxX()) + 5.0;
        double minZ = Math.min(pad.minZ(), pad.maxZ()) - 4.0;
        double maxZ = Math.max(pad.minZ(), pad.maxZ()) + 5.0;
        double minY = Math.min(pad.minY(), pad.maxY()) - 1.0;
        double maxY = Math.max(pad.minY(), pad.maxY()) + 6.0;

        Set<Chunk> chunks = new HashSet<>();
        chunks.add(world.getChunkAt((int) Math.floor(minX) >> 4, (int) Math.floor(minZ) >> 4));
        chunks.add(world.getChunkAt((int) Math.floor(maxX) >> 4, (int) Math.floor(minZ) >> 4));
        chunks.add(world.getChunkAt((int) Math.floor(minX) >> 4, (int) Math.floor(maxZ) >> 4));
        chunks.add(world.getChunkAt((int) Math.floor(maxX) >> 4, (int) Math.floor(maxZ) >> 4));
        chunks.add(world.getChunkAt(at));

        for (Chunk chunk : chunks) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof TextDisplay display)) {
                    continue;
                }
                Location loc = display.getLocation();
                if (loc.getX() < minX || loc.getX() > maxX
                        || loc.getY() < minY || loc.getY() > maxY
                        || loc.getZ() < minZ || loc.getZ() > maxZ) {
                    continue;
                }
                if (isJumpPadLabel(display) || DisplayEntities.isJumpPadLabel(display)) {
                    display.remove();
                }
            }
        }
    }

    private void spawnLabel(Pad pad) {
        World world = Bukkit.getWorld(pad.world());
        if (world == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pads.stream().noneMatch(p -> p.id().equals(pad.id()))) {
                    return;
                }
                World late = Bukkit.getWorld(pad.world());
                if (late != null) {
                    ensureSingleLabel(late, pad);
                }
            }, 40L);
            return;
        }
        ensureSingleLabel(world, pad);
        // Catch persistent orphans that finish loading a tick later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pads.stream().anyMatch(p -> p.id().equals(pad.id()))) {
                World w = Bukkit.getWorld(pad.world());
                if (w != null) {
                    ensureSingleLabel(w, pad);
                }
            }
        }, 20L);
    }

    private void ensureSingleLabel(World world, Pad pad) {
        Location at = labelLocation(world, pad);
        TextDisplay kept = null;
        List<TextDisplay> extras = new ArrayList<>();
        double minX = Math.min(pad.minX(), pad.maxX()) - 4.0;
        double maxX = Math.max(pad.minX(), pad.maxX()) + 5.0;
        double minZ = Math.min(pad.minZ(), pad.maxZ()) - 4.0;
        double maxZ = Math.max(pad.minZ(), pad.maxZ()) + 5.0;
        double minY = Math.min(pad.minY(), pad.maxY()) - 1.0;
        double maxY = Math.max(pad.minY(), pad.maxY()) + 6.0;
        for (Entity entity : world.getNearbyEntities(at, 8, 6, 8)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            Location loc = display.getLocation();
            if (loc.getX() < minX || loc.getX() > maxX
                    || loc.getY() < minY || loc.getY() > maxY
                    || loc.getZ() < minZ || loc.getZ() > maxZ) {
                continue;
            }
            if (!isJumpPadLabel(display)) {
                continue;
            }
            if (kept == null) {
                kept = display;
            } else {
                extras.add(display);
            }
        }
        for (TextDisplay extra : extras) {
            extra.remove();
        }
        String title = labelTitle(pad.id());
        if (kept != null && kept.isValid()) {
            kept.text(Component.text(title, NamedTextColor.GREEN, TextDecoration.BOLD));
            kept.setPersistent(false);
            kept.addScoreboardTag(LABEL_TAG);
            kept.addScoreboardTag(LABEL_TAG + "_" + pad.id().toLowerCase(Locale.ROOT));
            if (kept.getLocation().distanceSquared(at) > 0.05) {
                kept.teleport(at);
            }
            return;
        }
        world.spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(title, NamedTextColor.GREEN, TextDecoration.BOLD));
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            // Not saved with the chunk — plugin respawns on load so orphans can't stack.
            display.setPersistent(false);
            display.addScoreboardTag(LABEL_TAG);
            display.addScoreboardTag(LABEL_TAG + "_" + pad.id().toLowerCase(Locale.ROOT));
        });
    }

    private static String labelTitle(String id) {
        if (id == null || id.isBlank()) {
            return "Jump pad";
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (key.contains("origin_to_forage")
                || (key.contains("origin") && key.contains("forage") && key.indexOf("origin") < key.indexOf("forage"))) {
            return "Forage Island jump pad";
        }
        if (key.contains("forage_to_origin") || key.startsWith("forage")) {
            return "Harbour jump pad";
        }
        if (key.contains("origin_to_mining")
                || (key.contains("origin") && key.contains("mining") && key.indexOf("origin") < key.indexOf("mining"))) {
            return "Eldervale jump pad";
        }
        if (key.contains("mining_to_origin") || key.startsWith("mining")) {
            return "Shabby Mine jump pad";
        }
        return "Jump pad";
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (pads.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        for (Pad pad : pads) {
            if (!world.getName().equalsIgnoreCase(pad.world())) {
                continue;
            }
            if (!chunkTouchesPad(chunk, pad)) {
                continue;
            }
            Bukkit.getScheduler().runTask(plugin, () -> ensureSingleLabel(world, pad));
        }
    }

    private static boolean chunkTouchesPad(Chunk chunk, Pad pad) {
        int minCx = Math.min(pad.minX(), pad.maxX()) >> 4;
        int maxCx = Math.max(pad.minX(), pad.maxX()) >> 4;
        int minCz = Math.min(pad.minZ(), pad.maxZ()) >> 4;
        int maxCz = Math.max(pad.minZ(), pad.maxZ()) >> 4;
        int cx = chunk.getX();
        int cz = chunk.getZ();
        return cx >= minCx - 1 && cx <= maxCx + 1 && cz >= minCz - 1 && cz <= maxCz + 1;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (pads.isEmpty()) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() || player.isGliding()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block below = to.clone().subtract(0, 0.2, 0).getBlock();
        if (below.getType() != Material.SLIME_BLOCK) {
            below = to.getBlock().getRelative(0, -1, 0);
            if (below.getType() != Material.SLIME_BLOCK) {
                Block atFeet = to.getBlock();
                if (atFeet.getType() != Material.SLIME_BLOCK) {
                    return;
                }
                below = atFeet;
            }
        }
        Pad pad = find(below.getLocation());
        if (pad == null) {
            return;
        }
        if (!mayUsePad(player, pad)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        cooldownUntil.put(player.getUniqueId(), now + Math.max(pad.cooldownMs, 10_000L));
        // Leave flight mode so the jump impulse actually applies (Creative fly eats velocity).
        if (player.isFlying()) {
            player.setFlying(false);
        }
        launch(player, pad, below.getLocation().add(0.5, 1.0, 0.5));
    }

    private boolean mayUsePad(Player player, Pad pad) {
        if (!pad.requireStamped) {
            return true;
        }
        if (player.hasPermission("aetherionhub.admin") || player.hasPermission("aetherion.mines.admin")) {
            return true;
        }
        if (hasStampedBlueprint(player, pad.requireIds)) {
            return true;
        }
        player.sendMessage("§cJump pad sealed.");
        player.sendMessage("§7Stamp a §eSurveyor blueprint §7into a tool first — then this pad opens.");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f, 0.6f);
        nudgeOffPad(player, pad);
        // Longer cooldown so the locked message doesn't spam while standing on the pad.
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
        return false;
    }

    /** Soft shove 2–4 blocks back so standing on a locked pad feels blocked. */
    private static void nudgeOffPad(Player player, Pad pad) {
        Location at = player.getLocation();
        double cx = (pad.minX() + pad.maxX()) / 2.0 + 0.5;
        double cz = (pad.minZ() + pad.maxZ()) / 2.0 + 0.5;
        org.bukkit.util.Vector away = at.toVector().subtract(new org.bukkit.util.Vector(cx, at.getY(), cz));
        if (away.lengthSquared() < 0.01) {
            away = at.getDirection().setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new org.bukkit.util.Vector(0, 0, -1);
            }
        }
        away.setY(0).normalize().multiply(2.0 + java.util.concurrent.ThreadLocalRandom.current().nextDouble(2.0));
        away.setY(0.28);
        player.setVelocity(away);
    }

    private boolean hasStampedBlueprint(Player player, List<String> requireIds) {
        try {
            Object items = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance")
                    .invoke(null);
            if (items == null) {
                return false;
            }
            Object unlocks = items.getClass().getMethod("blueprintUnlocks").invoke(items);
            if (unlocks == null) {
                return false;
            }
            if (requireIds == null || requireIds.isEmpty()) {
                Object any = unlocks.getClass().getMethod("hasStampedAny", Player.class).invoke(unlocks, player);
                return any instanceof Boolean ok && ok;
            }
            for (String id : requireIds) {
                Object hit = unlocks.getClass()
                        .getMethod("hasStamped", Player.class, String.class)
                        .invoke(unlocks, player, id);
                if (hit instanceof Boolean ok && ok) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return false;
    }

    private void launch(Player player, Pad pad, Location from) {
        Vector impulse = computeImpulse(from, pad);
        player.setFallDistance(0f);
        beginFlightLock(player);
        player.setVelocity(impulse);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_SLIME_JUMP_SMALL, 1f, 0.55f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.45f, 1.15f);
        grantNoFall(player, Math.max(8000L, (pad.boostTicks + 80L) * 50L));

        final double holdX = impulse.getX();
        final double holdZ = impulse.getZ();
        final double initialVy = impulse.getY();
        final int boostTicks = Math.max(0, pad.boostTicks);
        final Double tx = pad.targetX;
        final Double tz = pad.targetZ;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    endFlightLock(player);
                    cancel();
                    return;
                }
                tick++;
                player.setFallDistance(0f);
                if (tick > boostTicks) {
                    if (player.isOnGround() && tick > boostTicks + 5) {
                        grantNoFall(player, LANDING_GRACE_MS);
                        endFlightLock(player);
                        cancel();
                    } else if (tick > boostTicks + 200) {
                        grantNoFall(player, LANDING_GRACE_MS);
                        endFlightLock(player);
                        cancel();
                    }
                    return;
                }

                Vector v = player.getVelocity();
                double hx = holdX;
                double hz = holdZ;
                if (tx != null && tz != null) {
                    Location loc = player.getLocation();
                    double dx = tx - loc.getX();
                    double dz = tz - loc.getZ();
                    double horiz = Math.sqrt(dx * dx + dz * dz);
                    if (horiz < 3.5) {
                        player.setVelocity(new Vector(dx * 0.15, Math.min(v.getY(), -0.15), dz * 0.15));
                        grantNoFall(player, LANDING_GRACE_MS);
                        endFlightLock(player);
                        cancel();
                        return;
                    }
                    double speed = Math.sqrt(holdX * holdX + holdZ * holdZ);
                    if (speed < 0.05) {
                        speed = pad.horizSpeed > 0 ? pad.horizSpeed : 2.8;
                    }
                    hx = (dx / horiz) * speed;
                    hz = (dz / horiz) * speed;
                }
                double y = v.getY();
                if (tick < Math.max(1, boostTicks / 3) && y < initialVy) {
                    y = Math.max(y, initialVy * 0.55);
                }
                // Re-assert scripted velocity every tick so WASD cannot reshape the arc.
                player.setVelocity(new Vector(hx, y, hz));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void beginFlightLock(Player player) {
        UUID id = player.getUniqueId();
        if (flightLocked.add(id)) {
            savedWalkSpeed.put(id, player.getWalkSpeed());
        }
        player.setWalkSpeed(0f);
        player.setSprinting(false);
        AttributeInstance move = movementAttribute(player);
        if (move != null) {
            NamespacedKey key = new NamespacedKey(plugin, PAD_LOCK_MOD);
            move.removeModifier(key);
            move.addModifier(new AttributeModifier(
                    key,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }
    }

    private void endFlightLock(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!flightLocked.remove(id)) {
            return;
        }
        AttributeInstance move = movementAttribute(player);
        if (move != null) {
            move.removeModifier(new NamespacedKey(plugin, PAD_LOCK_MOD));
        }
        Float walk = savedWalkSpeed.remove(id);
        player.setWalkSpeed(walk != null ? walk : 0.2f);
    }

    private static AttributeInstance movementAttribute(Player player) {
        AttributeInstance move = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (move != null) {
            return move;
        }
        try {
            return player.getAttribute(Attribute.valueOf("MOVEMENT_SPEED"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprintDuringFlight(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && flightLocked.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    private void grantNoFall(Player player, long durationMs) {
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        noFallUntil.merge(player.getUniqueId(), until, Math::max);
    }

    private boolean hasNoFall(Player player) {
        Long until = noFallUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** True if feet / block below sit on a configured launch pad (incl. a few blocks above). */
    private boolean isLandingOnPad(Player player) {
        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.2, 0).getBlock();
        if (find(below.getLocation()) != null || find(feet.getRelative(0, -1, 0).getLocation()) != null) {
            return true;
        }
        // Also accept being in the column just above the pad after a hard landing.
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (Pad pad : pads) {
            if (!world.getName().equalsIgnoreCase(pad.world)) {
                continue;
            }
            if (x >= pad.minX && x <= pad.maxX
                    && z >= pad.minZ && z <= pad.maxZ
                    && y >= pad.minY
                    && y <= pad.maxY + 4) {
                return true;
            }
        }
        return false;
    }

    private double impulseY(Pad pad, double startY) {
        if (pad.targetY == null) {
            return pad.vy;
        }
        double peak = Math.max(startY, pad.targetY) + pad.arcHeight;
        double up = Math.max(10.0, peak - startY);
        // g ≈ 0.08; slight overshoot for drag
        return Math.sqrt(2.0 * 0.08 * up) * 1.25;
    }

    private Vector computeImpulse(Location from, Pad pad) {
        if (pad.targetX == null || pad.targetY == null || pad.targetZ == null) {
            return new Vector(pad.vx, pad.vy, pad.vz);
        }
        double dx = pad.targetX - from.getX();
        double dz = pad.targetZ - from.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.01) {
            return new Vector(0, impulseY(pad, from.getY()), 0);
        }

        double vy = impulseY(pad, from.getY());
        double peak = Math.max(from.getY(), pad.targetY) + pad.arcHeight;
        double tPeak = vy / 0.08;
        double tDown = Math.sqrt(Math.max(1.0, 2.0 * (peak - pad.targetY) / 0.08));
        double flightTicks = (tPeak + tDown) * 1.15;

        double speed = pad.horizSpeed > 0.05
                ? pad.horizSpeed
                : Math.max(1.6, horiz / Math.max(25.0, flightTicks));
        // Cap a bit to reduce rubberbanding, boost-ticks make up the distance.
        speed = Math.min(speed, 3.6);

        return new Vector((dx / horiz) * speed, vy, (dz / horiz) * speed);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (hasNoFall(player) || isLandingOnPad(player)) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            if (isLandingOnPad(player)) {
                grantNoFall(player, LANDING_GRACE_MS);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        endFlightLock(player);
        noFallUntil.remove(id);
        cooldownUntil.remove(id);
    }

    private Pad find(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (Pad pad : pads) {
            if (!world.getName().equalsIgnoreCase(pad.world)) {
                continue;
            }
            if (x >= pad.minX && x <= pad.maxX
                    && y >= pad.minY && y <= pad.maxY
                    && z >= pad.minZ && z <= pad.maxZ) {
                return pad;
            }
        }
        return null;
    }

    /** Build the return pad south of the Origin pad (floating platform). */
    public void placeReturnPad(World world) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("island-pads.return-platform");
        if (s == null) {
            return;
        }
        int minX = s.getInt("min.x");
        int minY = s.getInt("min.y");
        int minZ = s.getInt("min.z");
        int maxX = s.getInt("max.x");
        int maxY = s.getInt("max.y");
        int maxZ = s.getInt("max.z");
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                world.getBlockAt(x, minY - 1, z).setType(Material.STONE, false);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.SLIME_BLOCK, false);
                }
            }
        }
        plugin.getLogger().info("Return slime pad placed at "
                + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
    }

    private record Pad(
            String id,
            String world,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            double vx,
            double vy,
            double vz,
            Double targetX,
            Double targetY,
            Double targetZ,
            double arcHeight,
            int boostTicks,
            double horizSpeed,
            long cooldownMs,
            boolean requireStamped,
            List<String> requireIds
    ) {
    }
}
