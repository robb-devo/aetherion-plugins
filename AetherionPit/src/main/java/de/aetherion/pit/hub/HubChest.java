package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modpack on the hub (2026-11-27): the tinkerer's chest. Its lid opens when somebody comes close, and what the
 * modpack brings floats up and circles above it (a spell book for the mods, an amethyst for the shader, a compass for
 * the launcher). Click it for the links.
 *
 * <pre>
 * chest: {world: world, x: 8.5, y: 65.0, z: -17.5, yaw: 0.0}     # standing point, the lock faces yaw
 * </pre>
 */
public final class HubChest implements Listener {

    public static final String TAG = "ae_hub_chest";
    private static final float S = 1.6f;
    private static final double OPEN_RADIUS = 6.0;
    private static final Material[] ORBIT = {Material.ENCHANTED_BOOK, Material.AMETHYST_SHARD, Material.COMPASS,
            Material.NETHER_STAR};

    private final AetherionPit plugin;
    private Location foot;
    private float yaw;
    private ItemDisplay base;
    private ItemDisplay lid;
    private final ItemDisplay[] items = new ItemDisplay[ORBIT.length];
    private Interaction hit;
    private boolean open;
    private int tick;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public HubChest(AetherionPit plugin) {
        this.plugin = plugin;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("chest");
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 4L);
    }

    public boolean enabled() {
        return foot != null;
    }

    private Quaternionf face() {
        return new Quaternionf().rotateY((float) Math.toRadians(-yaw));
    }

    private boolean alive() {
        if (!valid(base) || !valid(lid) || !valid(hit)) {
            return false;
        }
        for (ItemDisplay d : items) {
            if (!valid(d)) {
                return false;
            }
        }
        return true;
    }

    private static boolean valid(Entity e) {
        return e != null && e.isValid();
    }

    private void spawn() {
        remove();
        World w = foot.getWorld();
        base = display(w, HubModels.item(HubModels.CHEST_BASE), true);
        base.setTransformation(new Transformation(new Vector3f(0, 0.5f * S, 0), face(), new Vector3f(S, S, S),
                HubModels.flip()));
        lid = display(w, HubModels.item(HubModels.CHEST_LID), true);
        for (int i = 0; i < items.length; i++) {
            items[i] = display(w, new ItemStack(ORBIT[i]), false);
            items[i].setBrightness(new Display.Brightness(15, 15));
        }
        hit = w.spawn(foot, Interaction.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setInteractionWidth(1.6f);
            d.setInteractionHeight(1.4f);
            d.setResponsive(true);
        });
        open = false;
        lid(0f, 0);
        orbit(0f, 0);
    }

    public void remove() {
        for (Entity e : new Entity[]{base, lid, hit}) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].isValid()) {
                items[i].remove();
            }
            items[i] = null;
        }
        base = lid = null;
        hit = null;
    }

    private ItemDisplay display(World w, ItemStack stack, boolean model) {
        return w.spawn(foot, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(stack);
            d.setItemDisplayTransform(model ? ItemDisplay.ItemDisplayTransform.NONE : ItemDisplay.ItemDisplayTransform.FIXED);
            d.setViewRange(1.0f);
            if (model) {
                d.setBrightness(new Display.Brightness(13, 15));
            }
        });
    }

    /** angle 0 = shut, 1 = wide open (about 105 degrees) */
    private void lid(float k, int duration) {
        if (!valid(lid)) {
            return;
        }
        Vector3f hinge = new Vector3f(0, 0.5f * S + 1f / 16f * S, -5f / 16f * S).rotate(face());
        Quaternionf q = face().rotateX((float) Math.toRadians(-105f * k));
        lid.setInterpolationDelay(0);
        lid.setInterpolationDuration(duration);
        lid.setTransformation(new Transformation(hinge, q, new Vector3f(S, S, S), HubModels.flip()));
    }

    /** k: 0 = tucked inside the chest, 1 = circling above it */
    private void orbit(float k, int duration) {
        for (int i = 0; i < items.length; i++) {
            ItemDisplay d = items[i];
            if (!valid(d)) {
                continue;
            }
            double a = Math.toRadians(tick * 6 + i * 90);
            float r = 0.72f * k;
            float y = 0.55f + (0.95f + 0.14f * (float) Math.sin(Math.toRadians(tick * 9 + i * 70))) * k;
            Vector3f t = new Vector3f((float) Math.cos(a) * r, y, (float) Math.sin(a) * r);
            float s = 0.05f + 0.5f * k;
            Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-tick * 12 - i * 40));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformation(new Transformation(t, q, new Vector3f(s, s, s), new Quaternionf()));
        }
    }

    private void tick() {
        tick += 4;
        World w = foot.getWorld();
        if (!w.isChunkLoaded(foot.getBlockX() >> 4, foot.getBlockZ() >> 4)) {
            return;
        }
        if (!alive()) {
            spawn();
            return;
        }
        boolean near = false;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(foot) < OPEN_RADIUS * OPEN_RADIUS) {
                near = true;
                break;
            }
        }
        if (near && !open) {
            open = true;
            lid(1f, 10);
            w.playSound(foot, Sound.BLOCK_CHEST_OPEN, 0.6f, 1.1f);
            w.playSound(foot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
            w.spawnParticle(Particle.WAX_ON, foot.clone().add(0, 0.9, 0), 12, 0.35, 0.2, 0.35, 0.4);
        } else if (!near && open) {
            open = false;
            lid(0f, 12);
            orbit(0f, 10);
            w.playSound(foot, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.1f);
        }
        if (open) {
            orbit(1f, 4);
            if (tick % 16 == 0) {
                w.spawnParticle(Particle.END_ROD, foot.clone().add(0, 1.1, 0), 1, 0.4, 0.3, 0.4, 0.01);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        if (hit != null && event.getRightClicked().equals(hit)) {
            event.setCancelled(true);
            use(event.getPlayer());
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (hit != null && event.getAttacked().equals(hit)) {
            event.setCancelled(true);
            use(event.getPlayer());
        }
    }

    public void use(Player player) {
        long now = System.currentTimeMillis();
        Long until = cooldown.get(player.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        cooldown.put(player.getUniqueId(), now + 1500L);
        World w = foot.getWorld();
        Location top = foot.clone().add(0, 1.2, 0);
        w.playSound(top, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.2f);
        w.playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
        w.spawnParticle(Particle.ENCHANT, top, 50, 0.5, 0.4, 0.5, 0.8);
        w.spawnParticle(Particle.DUST, top, 16, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.fromRGB(0x57F287), 1.0f));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.guide().modpackBook(player);
            }
        }, 6L);
    }
}
