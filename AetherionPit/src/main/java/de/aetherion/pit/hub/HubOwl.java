package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Discord on the hub (2026-11-27): a blurple messenger owl on a falconer's perch. It follows whoever comes near with
 * its head (owls turn far), blinks, ruffles its wings now and then. Click it: it hoots, flaps, and a letter with a
 * blurple seal flies over to you; then the Discord invite opens.
 *
 * <pre>
 * owl: {world: world, x: 19.5, y: 65.0, z: -12.5, yaw: 0.0}     # foot of the perch, body faces yaw
 * </pre>
 */
public final class HubOwl implements Listener {

    public static final String TAG = "ae_hub_owl";
    private static final float S = 0.85f;
    private static final float O = 1.45f;
    private static final float BAR_TOP = 27.8f / 16f * S;
    private static final Color BLURPLE = Color.fromRGB(0x5865F2);

    private final AetherionPit plugin;
    private Location foot;
    private float yaw;
    private ItemDisplay perch;
    private ItemDisplay body;
    private ItemDisplay head;
    private ItemDisplay wingL;
    private ItemDisplay wingR;
    private Interaction hit;
    private int tick;
    private float headYaw;
    private float headTilt;
    private int busyUntil;
    private int blinkAt = 50;
    private int ruffleAt = 160;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public HubOwl(AetherionPit plugin) {
        this.plugin = plugin;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("owl");
        if (c != null) {
            World w = Bukkit.getWorld(c.getString("world", "world"));
            if (w != null) {
                foot = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
                yaw = (float) c.getDouble("yaw", 0);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        if (foot == null) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 2L);
    }

    public boolean enabled() {
        return foot != null;
    }

    public Location foot() {
        return foot;
    }

    // ------------------------------------------------------------------ parts

    private Quaternionf face() {
        return new Quaternionf().rotateY((float) Math.toRadians(-yaw));
    }

    private Vector3f bodyT() {
        return new Vector3f(0, BAR_TOP + 0.5f * O - 0.03f, 0);
    }

    private boolean alive() {
        return valid(perch) && valid(body) && valid(head) && valid(wingL) && valid(wingR) && valid(hit);
    }

    private static boolean valid(Entity e) {
        return e != null && e.isValid();
    }

    private void spawn() {
        remove();
        World w = foot.getWorld();
        perch = part(w, HubModels.OWL_PERCH);
        set(perch, new Vector3f(0, 0.5f * S, 0), face(), S, 0);
        body = part(w, HubModels.OWL_BODY);
        set(body, bodyT(), face(), O, 0);
        head = part(w, HubModels.OWL_HEAD);
        wingL = part(w, HubModels.OWL_WING);
        wingR = part(w, HubModels.OWL_WING);
        pose(0, 0, 8f, 0);
        hit = w.spawn(foot, Interaction.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setInteractionWidth(1.3f);
            d.setInteractionHeight(2.85f);
            d.setResponsive(true);
        });
    }

    public void remove() {
        for (Entity e : new Entity[]{perch, body, head, wingL, wingR, hit}) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        perch = body = head = wingL = wingR = null;
        hit = null;
    }

    private ItemDisplay part(World w, int code) {
        return w.spawn(foot, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(HubModels.item(code));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setViewRange(1.0f);
            d.setBrightness(new Display.Brightness(13, 15));
        });
    }

    private static void set(Display d, Vector3f t, Quaternionf q, float s, int duration) {
        if (!valid(d)) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(duration);
        d.setTransformation(new Transformation(t, q, new Vector3f(s, s, s), HubModels.flip()));
    }

    /** head yaw/tilt relative to the body (degrees), wing spread (degrees outward), how fast */
    private void pose(float hYaw, float hTilt, float wing, int duration) {
        Vector3f bt = bodyT();
        Quaternionf hq = face().rotateY((float) Math.toRadians(hYaw)).rotateZ((float) Math.toRadians(hTilt));
        set(head, new Vector3f(bt).add(0, 2.8f / 16f * O, 0), hq, O, duration);
        for (int side = 0; side < 2; side++) {
            ItemDisplay part = side == 0 ? wingL : wingR;
            float sx = side == 0 ? -4.6f : 4.6f;
            Vector3f shoulder = new Vector3f(sx / 16f * O, 1.5f / 16f * O, 0).rotate(face());
            Quaternionf wq = face();
            if (side == 1) {
                wq.rotateY((float) Math.PI);
            }
            wq.rotateZ((float) Math.toRadians(-wing));
            set(part, new Vector3f(bt).add(shoulder), wq, O, duration);
        }
    }

    // ------------------------------------------------------------------ life

    private void tick() {
        tick += 2;
        World w = foot.getWorld();
        if (!w.isChunkLoaded(foot.getBlockX() >> 4, foot.getBlockZ() >> 4)) {
            return;
        }
        if (!alive()) {
            spawn();
            return;
        }
        if (tick < busyUntil) {
            return;
        }
        // look at the nearest player within 12 blocks (the head turns up to 150 degrees, like a real owl)
        Player near = null;
        double best = 12 * 12;
        Location eye = foot.clone().add(0, BAR_TOP + 0.75, 0);
        for (Player p : w.getPlayers()) {
            double d = p.getEyeLocation().distanceSquared(eye);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        float targetYaw = 0f;
        float targetTilt = 0f;
        if (near != null) {
            Vector to = near.getEyeLocation().toVector().subtract(eye.toVector());
            float worldYaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            float rel = wrap(worldYaw - yaw);
            targetYaw = Math.max(-150f, Math.min(150f, -rel));
            if (best < 4.5 * 4.5) {
                targetTilt = (tick / 40) % 2 == 0 ? 14f : -10f;    // the curious owl tilt
            }
        } else if (tick % 120 < 2) {
            targetYaw = ThreadLocalRandom.current().nextFloat() * 120f - 60f;
        } else {
            targetYaw = headYaw;
        }
        boolean moved = Math.abs(targetYaw - headYaw) > 2f || Math.abs(targetTilt - headTilt) > 1f;
        headYaw = targetYaw;
        headTilt = targetTilt;
        if (tick >= blinkAt) {
            head.setItemStack(HubModels.item(HubModels.OWL_HEAD_BLINK));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (valid(head)) {
                    head.setItemStack(HubModels.item(HubModels.OWL_HEAD));
                }
            }, 3L);
            blinkAt = tick + 50 + ThreadLocalRandom.current().nextInt(90);
        }
        if (tick >= ruffleAt) {
            ruffleAt = tick + 200 + ThreadLocalRandom.current().nextInt(200);
            pose(headYaw, headTilt, 34f, 4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> pose(headYaw, headTilt, 8f, 6), 5L);
            busyUntil = tick + 12;
            return;
        }
        if (moved) {
            pose(headYaw, headTilt, 8f, 6);
        }
    }

    private static float wrap(float a) {
        a = a % 360f;
        if (a > 180f) {
            a -= 360f;
        }
        if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    // ------------------------------------------------------------------ click: hoot, flap, a letter flies over

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        if (hit != null && event.getRightClicked().equals(hit)) {
            event.setCancelled(true);
            greet(event.getPlayer());
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (hit != null && event.getAttacked().equals(hit)) {
            event.setCancelled(true);
            greet(event.getPlayer());
        }
    }

    public void greet(Player player) {
        long now = System.currentTimeMillis();
        Long until = cooldown.get(player.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        cooldown.put(player.getUniqueId(), now + 2500L);
        World w = foot.getWorld();
        Location eye = foot.clone().add(0, BAR_TOP + 0.75, 0);
        // hoo ... hoooo: two low flute notes
        w.playSound(eye, Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.0f, 0.71f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> w.playSound(eye, Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.0f, 0.6f), 7L);
        w.playSound(eye, Sound.ENTITY_PARROT_FLY, 0.8f, 0.8f);
        busyUntil = tick + 26;
        for (int k = 0; k < 3; k++) {
            int open = k * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> pose(headYaw, 0f, 78f, 3), open);
            Bukkit.getScheduler().runTaskLater(plugin, () -> pose(headYaw, 0f, 10f, 3), open + 4L);
        }
        w.spawnParticle(Particle.DUST, eye, 14, 0.4, 0.3, 0.4, 0, new Particle.DustOptions(BLURPLE, 1.1f));
        w.spawnParticle(Particle.NOTE, eye.clone().add(0, 0.6, 0), 2, 0.2, 0.1, 0.2, 1.0);
        flyLetter(player, eye);
    }

    private void flyLetter(Player player, Location from) {
        World w = from.getWorld();
        ItemDisplay letter = w.spawn(from, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(HubModels.item(HubModels.ENVELOPE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(3);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f),
                    HubModels.flip()));
        });
        int steps = 14;
        for (int i = 1; i <= steps; i++) {
            int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!letter.isValid() || !player.isOnline()) {
                    letter.remove();
                    return;
                }
                double f = k / (double) steps;
                Location to = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.6)).add(0, -0.35, 0);
                Location p = from.clone().add(to.toVector().subtract(from.toVector()).multiply(f));
                p.add(0, Math.sin(f * Math.PI) * 1.1, 0);
                letter.teleport(p);
                w.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(BLURPLE, 0.7f));
                if (k == steps) {
                    letter.remove();
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                    plugin.guide().discordBook(player);
                }
            }, i * 2L);
        }
    }
}
