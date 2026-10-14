package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Glowcap waystones. The map grows six giant glowing mushrooms across the island (Hearthcap, Skyreach, Ringside,
 * Northwild, the Twins, Bonewatch); Origin plants two young ones where there are none (the harbour quay and
 * Eastwood). Walk up to one to attune it. Right-click any glowcap to travel to another you have attuned.
 * Attuning all of them closes the <b>Glowcap Circle</b>.
 */
public final class OriginWaystones implements Listener {

    private static final double ATTUNE_RANGE = 3.4d;
    private static final long TRAVEL_COOLDOWN_MS = 8_000L;

    private final OriginIsle isle;
    private final NamespacedKey key;
    private final Map<String, List<UUID>> live = new HashMap<>();
    private final Map<UUID, Long> travelCool = new HashMap<>();
    private final Map<String, Long> nearCool = new HashMap<>();
    private int ensureTick;

    OriginWaystones(OriginIsle isle) {
        this.isle = isle;
        this.key = new NamespacedKey(isle.plugin(), "origin_waystone");
    }

    void start() {
        purgeStrays();
        for (OriginConfig.Waystone stone : isle.config().waystones().values()) {
            ensure(stone);
        }
    }

    void shutdown() {
        for (String id : new ArrayList<>(live.keySet())) {
            despawn(id);
        }
    }

    // ------------------------------------------------------------------ entities

    private boolean alive(String id) {
        List<UUID> ids = live.get(id);
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Entity first = Bukkit.getEntity(ids.get(0));
        return first != null && first.isValid();
    }

    private void ensure(OriginConfig.Waystone stone) {
        Location at = isle.config().location(stone.stand());
        if (at == null || at.getWorld() == null || alive(stone.id())) {
            return;
        }
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        despawn(stone.id());
        World world = at.getWorld();
        List<UUID> ids = new ArrayList<>();
        Interaction box = world.spawn(at.clone(), Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.6f);
            spawned.setInteractionHeight(2.4f);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            tag(spawned, stone.id());
        });
        ids.add(box.getUniqueId());
        TextDisplay label = world.spawn(at.clone().add(0, 2.7, 0), TextDisplay.class, text -> {
            text.text(OriginText.legacy("§9§l" + stone.name() + "\n§7Glowcap waystone\n§e▸ §7right-click to travel"));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setViewRange(0.4f);
            text.setPersistent(false);
            tag(text, stone.id());
        });
        ids.add(label.getUniqueId());
        if (stone.sprout()) {
            ids.addAll(sprout(world, at, stone.id()));
        }
        live.put(stone.id(), ids);
    }

    /** A young glowcap, 2.6 blocks tall, built from block displays (no world edits). */
    private List<UUID> sprout(World world, Location at, String id) {
        List<UUID> out = new ArrayList<>();
        Location base = at.clone().add(0.9, 0, 0.9);
        out.add(part(world, base, Material.BIRCH_PLANKS, -0.18f, 0f, -0.18f, 0.36f, 1.6f, 0.36f, id));
        out.add(part(world, base, Material.BLUE_WOOL, -0.85f, 1.45f, -0.85f, 1.7f, 0.45f, 1.7f, id));
        out.add(part(world, base, Material.LIGHT_BLUE_STAINED_GLASS, -0.6f, 1.85f, -0.6f, 1.2f, 0.35f, 1.2f, id));
        out.add(part(world, base, Material.CYAN_WOOL, -1.0f, 1.3f, -1.0f, 2.0f, 0.18f, 2.0f, id));
        out.add(part(world, base, Material.SEA_LANTERN, -0.62f, 1.62f, 0.45f, 0.22f, 0.22f, 0.22f, id));
        out.add(part(world, base, Material.SEA_LANTERN, 0.4f, 1.7f, -0.55f, 0.2f, 0.2f, 0.2f, id));
        out.add(part(world, base, Material.SEA_LANTERN, 0.35f, 1.95f, 0.3f, 0.18f, 0.18f, 0.18f, id));
        return out;
    }

    private UUID part(World world, Location base, Material material, float ox, float oy, float oz,
                      float sx, float sy, float sz, String id) {
        BlockDisplay display = world.spawn(base, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0f);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setTransformation(new Transformation(new Vector3f(ox, oy, oz), new AxisAngle4f(),
                    new Vector3f(sx, sy, sz), new AxisAngle4f()));
            tag(spawned, id);
        });
        return display.getUniqueId();
    }

    private void tag(Entity entity, String id) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    private void despawn(String id) {
        List<UUID> ids = live.remove(id);
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
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        live.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isle.running()) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (OriginConfig.Waystone stone : isle.config().waystones().values()) {
            double[] s = stone.stand();
            if (((int) Math.floor(s[0]) >> 4) == chunk.getX() && ((int) Math.floor(s[2]) >> 4) == chunk.getZ()
                    && chunk.getWorld().getName().equalsIgnoreCase(isle.config().worldName())) {
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(stone));
            }
        }
    }

    // ------------------------------------------------------------------ attune

    /** Every 10 ticks. */
    void tick(List<Player> onIsle) {
        if (++ensureTick % 10 == 0) {
            for (OriginConfig.Waystone stone : isle.config().waystones().values()) {
                ensure(stone);
            }
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            Location at = player.getLocation();
            for (OriginConfig.Waystone stone : isle.config().waystones().values()) {
                double[] s = stone.stand();
                double dx = at.getX() - s[0];
                double dy = at.getY() - s[1];
                double dz = at.getZ() - s[2];
                if (dx * dx + dz * dz > ATTUNE_RANGE * ATTUNE_RANGE || Math.abs(dy) > 3.0d) {
                    continue;
                }
                attune(player, stone);
                String k = player.getUniqueId() + ":" + stone.id();
                Long cool = nearCool.get(k);
                if (cool == null || now >= cool) {
                    nearCool.put(k, now + 10_000L);
                    isle.compass().attuned(player, stone.id());
                }
            }
        }
    }

    public boolean attune(Player player, OriginConfig.Waystone stone) {
        OriginProfile profile = isle.profiles().get(player);
        if (!profile.waystones.add(stone.id())) {
            return false;
        }
        profile.dirty = true;
        long paid = isle.pay(player, "waystone", 100L);
        int total = isle.config().waystones().size();
        long found = isle.config().waystones().keySet().stream().filter(profile.waystones::contains).count();
        Location at = player.getLocation();
        player.spawnParticle(Particle.GLOW, at.clone().add(0, 1.2, 0), 30, 0.6, 0.8, 0.6, 0.02);
        player.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.0, 0), 14, 0.4, 0.6, 0.4, 0.05);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.9f, 0.8f);
        player.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.5f, 1.6f);
        if (!isle.quiet(player)) {
            OriginText.card(player, "§9" + stone.name(), "§7Glowcap attuned · " + found + "/" + total, 40);
        }
        player.sendMessage("§9✦ Attuned §8· §9" + stone.name() + " §8· §7" + stone.blurb()
                + (paid > 0 ? " §8· §6+" + paid : "") + " §8· §7" + found + "/" + total + " glowcaps");
        player.sendMessage("§7Right-click any glowcap to travel between the ones you've attuned.");
        if (found >= total && profile.mark("glowcap_circle")) {
            OriginText.card(player, "§9§lGlowcap Circle", "§7Every glowcap on Origin answers you", 60);
            long bonus = isle.pay(player, "glowcap-circle", 1500L);
            player.sendMessage("§9✦ Glowcap Circle §8· §6+" + OriginText.coins(bonus) + " coins");
            player.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        }
        return true;
    }

    // ------------------------------------------------------------------ travel

    public OriginConfig.Waystone of(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : isle.config().waystones().get(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        OriginConfig.Waystone stone = of(event.getRightClicked());
        if (stone == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !isle.running()) {
            return;
        }
        Player player = event.getPlayer();
        attune(player, stone);
        isle.menus().openWaystones(player, stone.id());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (of(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    public String travel(Player player, String id, boolean free) {
        OriginConfig.Waystone stone = isle.config().waystones().get(id);
        if (stone == null) {
            return "§cNo such glowcap.";
        }
        OriginProfile profile = isle.profiles().get(player);
        if (!free && !profile.waystones.contains(id)) {
            return "§7You haven't attuned §9" + stone.name() + " §7yet — walk up to it once.";
        }
        long now = System.currentTimeMillis();
        Long cool = travelCool.get(player.getUniqueId());
        if (!free && cool != null && now < cool) {
            return "§7The glowcaps are still humming — try again in §f" + ((cool - now) / 1000L + 1) + "s§7.";
        }
        Location to = isle.config().location(stone.stand());
        if (to == null) {
            return "§cThat glowcap's world isn't loaded.";
        }
        double[] cap = stone.cap();
        to.setYaw((float) Math.toDegrees(Math.atan2(-(cap[0] - to.getX()), cap[2] - to.getZ())));
        to.add(stone.sprout() ? -0.9 : 0.0, 0.0, stone.sprout() ? -0.9 : 0.0);
        Location from = player.getLocation();
        if (from.getWorld() != null) {
            from.getWorld().spawnParticle(Particle.GLOW, from.clone().add(0, 1, 0), 40, 0.4, 0.9, 0.4, 0.05);
            from.getWorld().playSound(from, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.7f, 1.3f);
        }
        travelCool.put(player.getUniqueId(), now + TRAVEL_COOLDOWN_MS);
        isle.traversal().grace(player, 4_000L);
        player.teleport(to);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, true));
        World world = to.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.GLOW, to.clone().add(0, 1, 0), 40, 0.4, 0.9, 0.4, 0.05);
            world.spawnParticle(Particle.END_ROD, to.clone().add(0, 1.2, 0), 16, 0.3, 0.6, 0.3, 0.04);
            world.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.9f, 1.1f);
        }
        OriginText.bar(player, "§9✦ " + stone.name() + " §8· §7" + stone.blurb());
        return null;
    }

    void forget(UUID id) {
        travelCool.remove(id);
        String prefix = id + ":";
        nearCool.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
