package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vistas: five high places (Skyreach Summit, Crag Peak, Ridge Lookout, Mine Hill, Wildwatch Bluff). Stand on one
 * and the island names itself — a floating label drifts out toward every district on the horizon, only for you,
 * with its distance. First visit pays; every vista makes you a <b>Stargazer</b>.
 */
public final class OriginVistas implements Listener {

    private static final double LABEL_DISTANCE = 22.0d;
    private static final int LABEL_TICKS = 20 * 12;
    private static final long REPEAT_MS = 60_000L;

    private final OriginIsle isle;
    private final NamespacedKey key;
    private final Map<UUID, List<UUID>> shown = new HashMap<>();
    private final Map<String, Long> cool = new HashMap<>();
    private final Map<UUID, String> standing = new HashMap<>();

    OriginVistas(OriginIsle isle) {
        this.isle = isle;
        this.key = new NamespacedKey(isle.plugin(), "origin_vista_label");
        Bukkit.getScheduler().runTaskLater(isle.plugin(), this::purgeStrays, 20L);
    }

    /** Every 10 ticks. */
    void tick(List<Player> onIsle) {
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            OriginConfig.Vista here = null;
            Location at = player.getLocation();
            for (OriginConfig.Vista vista : isle.config().vistas().values()) {
                double[] v = vista.at();
                double dx = at.getX() - v[0];
                double dz = at.getZ() - v[2];
                double dy = at.getY() - v[1];
                if (dx * dx + dz * dz <= vista.radius() * vista.radius() && Math.abs(dy) < 4.0d) {
                    here = vista;
                    break;
                }
            }
            String prev = here == null ? standing.remove(player.getUniqueId()) : standing.put(player.getUniqueId(), here.id());
            if (here == null || here.id().equals(prev) || isle.flight().flying(player)) {
                continue;
            }
            String k = player.getUniqueId() + ":" + here.id();
            Long c = cool.get(k);
            if (c != null && now < c) {
                continue;
            }
            cool.put(k, now + REPEAT_MS);
            reveal(player, here);
        }
    }

    public void reveal(Player player, OriginConfig.Vista vista) {
        OriginProfile profile = isle.profiles().get(player);
        boolean first = profile.vistas.add(vista.id());
        if (first) {
            profile.dirty = true;
            long paid = isle.pay(player, "vista", 120L);
            OriginText.card(player, "§f§l" + vista.name(), "§7Vista · look around", 50);
            player.sendMessage("§f✦ Vista §8· §f" + vista.name() + " §8· §7the island names itself" + (paid > 0 ? " §8· §6+" + paid : ""));
            int total = isle.config().vistas().size();
            long found = isle.config().vistas().keySet().stream().filter(profile.vistas::contains).count();
            if (found >= total && profile.mark("stargazer")) {
                OriginText.card(player, "§d§lStargazer", "§7Every vista on Origin", 60);
                long bonus = isle.pay(player, "stargazer", 750L);
                player.sendMessage("§d✦ Stargazer §8· §6+" + OriginText.coins(bonus) + " coins");
            }
        } else {
            OriginText.bar(player, "§f✦ " + vista.name() + " §8· §7look around");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 0.7f);
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        labels(player);
    }

    /** Per-player floating district names toward every district anchor. */
    public void labels(Player player) {
        clearFor(player.getUniqueId());
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        OriginConfig.District here = isle.config().districtAt(player.getLocation());
        List<UUID> ids = new ArrayList<>();
        for (OriginConfig.District district : isle.config().districts().values()) {
            if (district.anchor() == null || (here != null && here.id().equals(district.id()))) {
                continue;
            }
            double dx = district.anchor()[0] - eye.getX();
            double dz = district.anchor()[2] - eye.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 30.0d) {
                continue;
            }
            double ux = dx / dist;
            double uz = dz / dist;
            double lift = Math.max(-6.0d, Math.min(8.0d, (district.anchor()[1] - eye.getY()) * 0.08d)) + 2.0d;
            Location at = new Location(world, eye.getX() + ux * LABEL_DISTANCE, eye.getY() + lift, eye.getZ() + uz * LABEL_DISTANCE);
            boolean known = isle.profiles().get(player).districts.contains(district.id());
            String name = known ? district.colored() : "§7???";
            String sub = "§7" + OriginText.distance(dist) + (known ? "" : " §8· §7undiscovered");
            TextDisplay label = world.spawn(at, TextDisplay.class, text -> {
                text.setVisibleByDefault(false);
                text.text(OriginText.legacy("§l" + name + "\n" + sub));
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setShadowed(true);
                text.setSeeThrough(true);
                text.setDefaultBackground(false);
                text.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                text.setPersistent(false);
                text.setBrightness(new Display.Brightness(15, 15));
                text.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.1f, 0.1f, 0.1f), new AxisAngle4f()));
                text.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            });
            player.showEntity(isle.plugin(), label);
            ids.add(label.getUniqueId());
            // Grow in: interpolate from 0.1 to 1.8 scale.
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (label.isValid()) {
                    label.setInterpolationDelay(0);
                    label.setInterpolationDuration(14);
                    label.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.8f, 1.8f, 1.8f), new AxisAngle4f()));
                }
            }, 2L + ids.size());
        }
        shown.put(player.getUniqueId(), ids);
        UUID owner = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            List<UUID> current = shown.get(owner);
            if (current == ids) {
                clearFor(owner);
            }
        }, LABEL_TICKS);
    }

    private void clearFor(UUID id) {
        List<UUID> ids = shown.remove(id);
        if (ids == null) {
            return;
        }
        for (UUID uuid : ids) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    void forget(UUID id) {
        clearFor(id);
        standing.remove(id);
        String prefix = id + ":";
        cool.keySet().removeIf(k -> k.startsWith(prefix));
    }

    void clear() {
        for (UUID id : new ArrayList<>(shown.keySet())) {
            clearFor(id);
        }
    }
}
