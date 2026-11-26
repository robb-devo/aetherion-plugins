package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seven lost ducklings (2026-11-26): tiny rubber ducks hidden around the hub. Click one to bring it home; it then
 * disappears for you. Find all of them and you are the hub's Duck Rescuer. Progress lives in fun.yml.
 */
public final class HubSecrets implements Listener {

    public static final String TAG = "ae_hub_duckling";
    private static final TextColor DUCK = TextColor.color(0xFFD83B);
    private static final TextColor SOFT = TextColor.color(0xC9C2D6);

    private final AetherionPit plugin;
    private final List<Location> spots = new ArrayList<>();
    private final List<Float> yaws = new ArrayList<>();
    private final ItemDisplay[] bodies;
    private final Interaction[] hits;
    private final File file;
    private final YamlConfiguration data;
    private int step;

    public HubSecrets(AetherionPit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ducklings.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : plugin.getConfig().getMapList("ducklings")) {
            World w = Bukkit.getWorld(String.valueOf(m.containsKey("world") ? m.get("world") : "world"));
            if (w == null) {
                continue;
            }
            spots.add(new Location(w, HubDecor.num(m, "x", 0), HubDecor.num(m, "y", 64), HubDecor.num(m, "z", 0)));
            yaws.add((float) HubDecor.num(m, "yaw", 0));
        }
        bodies = new ItemDisplay[spots.size()];
        hits = new Interaction[spots.size()];
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 30L);
    }

    public int total() {
        return spots.size();
    }

    public int found(Player player) {
        return foundSet(player.getUniqueId()).size();
    }

    private Set<Integer> foundSet(UUID id) {
        return new HashSet<>(data.getIntegerList("found." + id));
    }

    private void tick() {
        step++;
        for (int i = 0; i < spots.size(); i++) {
            Location at = spots.get(i);
            World w = at.getWorld();
            if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                continue;
            }
            if (bodies[i] == null || !bodies[i].isValid() || hits[i] == null || !hits[i].isValid()) {
                spawn(i);
            }
            float bob = (step % 2 == 0) ? 0.05f : -0.03f;
            float wobble = (step % 2 == 0) ? 8f : -8f;
            Quaternionf q = HubDecor.yawQ(yaws.get(i)).rotateX((float) Math.toRadians(wobble * 0.4f));
            ItemDisplay d = bodies[i];
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(30);
            d.setTransformation(new Transformation(new Vector3f(0, 0.28f + bob, 0), q, new Vector3f(0.55f, 0.55f, 0.55f),
                    HubModels.flip()));
        }
    }

    private void spawn(int i) {
        if (bodies[i] != null && bodies[i].isValid()) {
            bodies[i].remove();
        }
        if (hits[i] != null && hits[i].isValid()) {
            hits[i].remove();
        }
        Location at = spots.get(i);
        World w = at.getWorld();
        bodies[i] = w.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(HubModels.item(HubModels.DUCKLING));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(13, 15));
            d.setViewRange(0.5f);
        });
        hits[i] = w.spawn(at, Interaction.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.addScoreboardTag(TAG + "_" + i);
            d.setInteractionWidth(0.6f);
            d.setInteractionHeight(0.55f);
            d.setResponsive(true);
        });
        for (Player p : w.getPlayers()) {
            if (foundSet(p.getUniqueId()).contains(i)) {
                p.hideEntity(plugin, bodies[i]);
                p.hideEntity(plugin, hits[i]);
            }
        }
    }

    private int indexOf(Entity e) {
        for (int i = 0; i < hits.length; i++) {
            if (hits[i] != null && hits[i].equals(e)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        int i = indexOf(event.getRightClicked());
        if (i >= 0) {
            event.setCancelled(true);
            collect(event.getPlayer(), i);
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        int i = indexOf(event.getAttacked());
        if (i >= 0) {
            event.setCancelled(true);
            collect(event.getPlayer(), i);
        }
    }

    private void collect(Player player, int i) {
        UUID id = player.getUniqueId();
        Set<Integer> found = foundSet(id);
        if (!found.add(i)) {
            return;
        }
        List<Integer> list = new ArrayList<>(found);
        list.sort(Integer::compareTo);
        data.set("found." + id, list);
        data.set("names." + id, player.getName());
        save();
        Location at = spots.get(i);
        World w = at.getWorld();
        player.playSound(at, Sound.ENTITY_CHICKEN_EGG, 1f, 1.6f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.5f + 0.06f * found.size());
        player.spawnParticle(Particle.HEART, at.clone().add(0, 0.5, 0), 4, 0.25, 0.2, 0.25, 0);
        player.spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.3, 0), 14, 0.3, 0.3, 0.3, 0.5);
        if (bodies[i] != null) {
            player.hideEntity(plugin, bodies[i]);
        }
        if (hits[i] != null) {
            player.hideEntity(plugin, hits[i]);
        }
        boolean de = HubHolograms.german(player);
        int n = found.size();
        int all = spots.size();
        if (n < all) {
            player.showTitle(Title.title(Component.empty(),
                    Component.text((de ? "Küken gefunden! " : "Duckling found! ") + n + "/" + all, DUCK),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(400))));
            player.sendMessage(Component.text(" ✦ ", DUCK).append(Component.text(de
                    ? "Ein Küken ist wieder bei Mama. Noch " + (all - n) + " fehlen."
                    : "A duckling is back with mum. " + (all - n) + " still missing.", SOFT)));
        } else {
            player.showTitle(Title.title(Component.text(de ? "ENTENRETTER" : "DUCK RESCUER", DUCK, TextDecoration.BOLD),
                    Component.text(de ? "Alle Küken sind wieder zu Hause!" : "Every duckling is home again!", SOFT),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(3000), Duration.ofMillis(700))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
            for (Player p : player.getWorld().getPlayers()) {
                boolean pde = HubHolograms.german(p);
                p.sendMessage(Component.text(" ✦ ", DUCK).append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(pde ? " hat alle Entenküken gefunden. Quack!" : " found every lost duckling. Quack!",
                                DUCK)));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Set<Integer> found = foundSet(player.getUniqueId());
            for (int i = 0; i < spots.size(); i++) {
                if (found.contains(i)) {
                    if (bodies[i] != null && bodies[i].isValid()) {
                        player.hideEntity(plugin, bodies[i]);
                    }
                    if (hits[i] != null && hits[i].isValid()) {
                        player.hideEntity(plugin, hits[i]);
                    }
                }
            }
        }, 10L);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save ducklings.yml: " + ex.getMessage());
        }
    }

    public void removeAll() {
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i] != null && bodies[i].isValid()) {
                bodies[i].remove();
            }
            if (hits[i] != null && hits[i].isValid()) {
                hits[i].remove();
            }
        }
    }
}
