package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.ui.QuestHint;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harbour funnel soft-walls — velocity shove only (no {@code setTo} teleports).
 * <ul>
 *   <li>Pier cage until Egon is spoken to</li>
 *   <li>Water near the pier → silent rescue to /harbour spawn (pre-Egon only)</li>
 *   <li>Full-map market plane at {@code x=292} until gather_wood is turned in at Egon</li>
 * </ul>
 */
public final class HarbourOnboardingGate implements Listener {

    private static final double PIER_X_MIN = 294.0;
    private static final double PIER_X_MAX = 303.0;
    private static final double PIER_Z_MIN = -407.0;
    private static final double PIER_Z_MAX = -378.0;
    private static final double PIER_Y_MIN = 60.0;
    private static final double PIER_Y_MAX = 72.0;

    /** Your two points marked the plane; wall runs the full harbour world on this X. */
    private static final double MARKET_X = 292.0;

    private static final long HINT_COOLDOWN_MS = 3500L;
    private static final long PUSH_COOLDOWN_MS = 50L;
    private static final long WATER_RESCUE_COOLDOWN_MS = 1500L;

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPush = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWaterRescue = new ConcurrentHashMap<>();

    public HarbourOnboardingGate(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null
                || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.hasPermission("aetherion.quests.bypass")
                || player.hasPermission("aetherion.items.dev")) {
            return;
        }
        if (!isHarbourWorld(to.getWorld())) {
            return;
        }
        if (QuestStoryGate.tutorialDone(player, questManager)) {
            return;
        }

        // Before Egon: falling off the pier into water = soft wipe → /harbour spawn.
        if (!spokeToEgon(player)) {
            if (inPierBand(to) && inWater(player, to)) {
                rescueToHarbour(player);
                return;
            }
            if (inPierBand(to) && !insidePier(to)) {
                Vector toward = towardPierInside(to);
                shove(player, toward, depthOutsidePier(to));
                hint(player, "egon", "Egon",
                        "§eEgon §7is right here on the pier — talk to him first.",
                        "→ Talk to Egon");
            }
            return;
        }

        // After Egon talk, until timber is turned in: full-map soft wall at x=292 (no walking around).
        if (!egonTimberTurnedIn(player) && pastMarketWall(to)) {
            double depth = Math.max(0.0, MARKET_X - to.getX());
            shove(player, new Vector(1.0, 0.0, 0.0), depth);
            hint(player, "egon", "Egon",
                    "§7Market stays closed until you bring §eEgon §7his oak.",
                    "→ Forager · then turn in at Egon");
        }
    }

    private void rescueToHarbour(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastWaterRescue.get(id);
        if (last != null && now - last < WATER_RESCUE_COOLDOWN_MS) {
            return;
        }
        lastWaterRescue.put(id, now);

        Location harbour = harbourSpawnLocation();
        if (harbour == null) {
            // Fallback: center of the pier cage.
            harbour = new Location(
                    player.getWorld(),
                    (PIER_X_MIN + PIER_X_MAX) * 0.5,
                    63.0,
                    (PIER_Z_MIN + PIER_Z_MAX) * 0.5,
                    player.getLocation().getYaw(),
                    0f
            );
        }
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        player.teleport(harbour);
        player.sendMessage("§7Easy — back on the pier. Talk to §eEgon §7first.");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "→ Talk to Egon",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
        player.playSound(harbour, Sound.ENTITY_PLAYER_SPLASH, 0.35f, 1.2f);
        QuestHint.show(player, "egon", "Egon");
    }

    private Location harbourSpawnLocation() {
        try {
            org.bukkit.plugin.Plugin hubPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionHub");
            if (hubPlugin == null || !hubPlugin.isEnabled()) {
                return null;
            }
            Object hub = hubPlugin.getClass().getMethod("getHub").invoke(hubPlugin);
            if (hub == null) {
                return null;
            }
            Object spawn = hub.getClass().getMethod("spawn", String.class).invoke(hub, "harbour");
            if (spawn == null) {
                return null;
            }
            Object loc = spawn.getClass().getMethod("toLocation").invoke(spawn);
            return loc instanceof Location location ? location : null;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static boolean inWater(Player player, Location at) {
        if (player.isInWater() || player.isSwimming()) {
            return true;
        }
        org.bukkit.block.Block feet = at.getBlock();
        org.bukkit.block.Block below = at.clone().subtract(0, 0.2, 0).getBlock();
        return isWaterish(feet.getType()) || isWaterish(below.getType());
    }

    private static boolean isWaterish(org.bukkit.Material type) {
        if (type == null) {
            return false;
        }
        return type == org.bukkit.Material.WATER
                || type == org.bukkit.Material.BUBBLE_COLUMN
                || type.name().contains("WATER");
    }

    /**
     * Pure knockback-style shove. Never relocates the player via setTo.
     */
    private void shove(Player player, Vector dir, double depth) {
        if (dir == null) {
            return;
        }
        dir = dir.clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastPush.get(id);
        if (last != null && now - last < PUSH_COOLDOWN_MS) {
            return;
        }
        lastPush.put(id, now);

        dir.normalize();
        // Stronger the further they press in — still a shove, not a warp.
        double strength = 0.42 + Math.min(0.55, depth * 0.35);
        Vector current = player.getVelocity();
        // Blend: keep a bit of their motion, dominate with the shove.
        double vx = current.getX() * 0.15 + dir.getX() * strength;
        double vz = current.getZ() * 0.15 + dir.getZ() * strength;
        double vy = Math.min(0.08, Math.max(current.getY(), 0.0));
        player.setVelocity(new Vector(vx, vy, vz));
    }

    private static Vector towardPierInside(Location at) {
        double x = at.getX();
        double z = at.getZ();
        double tx = clamp(x, PIER_X_MIN + 0.5, PIER_X_MAX - 0.5);
        double tz = clamp(z, PIER_Z_MIN + 0.5, PIER_Z_MAX - 0.5);
        return new Vector(tx - x, 0.0, tz - z);
    }

    private static double depthOutsidePier(Location at) {
        double dx = 0.0;
        double dz = 0.0;
        if (at.getX() < PIER_X_MIN) {
            dx = PIER_X_MIN - at.getX();
        } else if (at.getX() > PIER_X_MAX) {
            dx = at.getX() - PIER_X_MAX;
        }
        if (at.getZ() < PIER_Z_MIN) {
            dz = PIER_Z_MIN - at.getZ();
        } else if (at.getZ() > PIER_Z_MAX) {
            dz = at.getZ() - PIER_Z_MAX;
        }
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private boolean spokeToEgon(Player player) {
        Quest welcome = questManager.getQuest("welcome_aboard");
        if (welcome == null) {
            return true;
        }
        return questManager.getQuestState(player, welcome) != QuestState.AVAILABLE;
    }

    /** Market unlocks only after oak is handed to Egon (gather_wood completed). */
    private boolean egonTimberTurnedIn(Player player) {
        Quest timber = questManager.getQuest("gather_wood");
        if (timber == null) {
            return true;
        }
        return questManager.getQuestState(player, timber) == QuestState.COMPLETED;
    }

    private static boolean insidePier(Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= PIER_X_MIN && x <= PIER_X_MAX
                && z >= PIER_Z_MIN && z <= PIER_Z_MAX
                && y >= PIER_Y_MIN && y <= PIER_Y_MAX;
    }

    private static boolean inPierBand(Location loc) {
        double x = loc.getX();
        double z = loc.getZ();
        return x >= PIER_X_MIN - 10 && x <= PIER_X_MAX + 10
                && z >= PIER_Z_MIN - 10 && z <= PIER_Z_MAX + 10
                && loc.getY() >= PIER_Y_MIN - 2 && loc.getY() <= PIER_Y_MAX + 4;
    }

    /** Full vertical plane on MARKET_X — no Z band, so you can't walk around. */
    private static boolean pastMarketWall(Location to) {
        return to.getX() < MARKET_X;
    }

    private Location npcLocation(String id) {
        if (plugin.getNpcDataStorage() == null) {
            return null;
        }
        return plugin.getNpcDataStorage().getSavedLocation(id);
    }

    private boolean isHarbourWorld(World world) {
        if (world == null) {
            return false;
        }
        Location egon = npcLocation("egon");
        if (egon != null && egon.getWorld() != null) {
            return world.equals(egon.getWorld());
        }
        String name = world.getName();
        return "world".equalsIgnoreCase(name) || "harbour".equalsIgnoreCase(name);
    }

    private void hint(Player player, String npcId, String npcName, String chat, String action) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastHint.get(id);
        if (last != null && now - last < HINT_COOLDOWN_MS) {
            return;
        }
        lastHint.put(id, now);
        player.sendMessage(chat);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                action,
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.22f, 0.8f);
        QuestHint.show(player, npcId, npcName);
    }
}
