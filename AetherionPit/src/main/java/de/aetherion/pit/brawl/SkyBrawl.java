package de.aetherion.pit.brawl;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.hub.HubHolograms;
import de.aetherion.pit.hub.HubModels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sky Brawl (2026-11-27, replaces The Pit): a storm platform in the clouds. Step off the lobby ledge and you get a
 * gust rod and three wind charges; nobody takes damage, every hit just pushes. Push somebody over the edge and you
 * score a knock-off (and shards for the outfitter). Falling puts you back on the ledge. A storm orb shows up in the
 * middle now and then: whoever grabs it hits twice as hard for a while.
 *
 * <pre>
 * brawl:
 *   world: world
 *   center: [152.5, 61.0, 0.5]   radius: 11.0   floor: 61.0   catch_y: 48.0
 *   ledge: {min: [122, 54, -7], max: [134, 72, 7]}
 *   arrive: [130.5, 61.0, 0.5, -90.0]   orb: [..]   spire: [..]   board: [..]
 * </pre>
 */
public final class SkyBrawl implements Listener {

    public static final String TAG = "ae_brawl";
    static final TextColor STORM = TextColor.color(0x4FC3F7);
    static final TextColor SOFT = TextColor.color(0xC9C2D6);
    static final TextColor GOLD = TextColor.color(0xFFC857);
    private static final int SHARDS_PER_KO = 5;
    private static final long CREDIT_MS = 10_000L;
    private static final long CHARGE_MS = 12_000L;
    private static final int MAX_CHARGES = 3;

    private final AetherionPit plugin;
    private final BrawlStore store;
    private final NamespacedKey kitKey;
    private World world;
    private Location center;
    private double catchY;
    private int[] ledgeMin;
    private int[] ledgeMax;
    private Location arrive;
    private Location orbAt;
    private Location spireAt;
    private Location boardAt;

    private record Hit(UUID by, long at) {
    }

    private final Set<UUID> fighting = new HashSet<>();
    private final Set<UUID> greeted = new HashSet<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Location> lastGround = new HashMap<>();
    private final Map<UUID, Long> charged = new HashMap<>();
    private final Map<UUID, Long> lastRefill = new HashMap<>();
    private ItemDisplay spire;
    private ItemDisplay orb;
    private long orbExpires;
    private long nextOrb;
    private int tick;
    private boolean boardDirty = true;

    public SkyBrawl(AetherionPit plugin) {
        this.plugin = plugin;
        this.store = new BrawlStore(plugin);
        this.kitKey = new NamespacedKey(plugin, "brawl_kit");
        load();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (enabled()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
            Bukkit.getScheduler().runTaskTimer(plugin, store::saveIfDirty, 600L, 600L);
        }
    }

    private void load() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("brawl");
        if (c == null) {
            return;
        }
        world = Bukkit.getWorld(c.getString("world", "world"));
        if (world == null) {
            return;
        }
        center = loc(c.getDoubleList("center"));
        arrive = loc(c.getDoubleList("arrive"));
        orbAt = loc(c.getDoubleList("orb"));
        spireAt = loc(c.getDoubleList("spire"));
        boardAt = loc(c.getDoubleList("board"));
        catchY = c.getDouble("catch_y", center == null ? 0 : center.getY() - 12);
        ConfigurationSection l = c.getConfigurationSection("ledge");
        if (l != null) {
            ledgeMin = ints(l.getDoubleList("min"));
            ledgeMax = ints(l.getDoubleList("max"));
        }
    }

    private Location loc(List<Double> v) {
        if (v == null || v.size() < 3) {
            return null;
        }
        Location l = new Location(world, v.get(0), v.get(1), v.get(2));
        if (v.size() >= 4) {
            l.setYaw(v.get(3).floatValue());
        }
        return l;
    }

    private static int[] ints(List<Double> v) {
        if (v == null || v.size() < 3) {
            return null;
        }
        return new int[]{(int) Math.floor(v.get(0)), (int) Math.floor(v.get(1)), (int) Math.floor(v.get(2))};
    }

    public boolean enabled() {
        return world != null && center != null && arrive != null && ledgeMin != null && ledgeMax != null;
    }

    public BrawlStore store() {
        return store;
    }

    public Location arrive() {
        return arrive;
    }

    public Location board() {
        return boardAt;
    }

    public boolean boardDirty() {
        boolean d = boardDirty;
        boardDirty = false;
        return d;
    }

    // ------------------------------------------------------------------ zones

    /** the whole Sky Brawl area: ledge, bridge, disc, stepping stones and the air below them */
    public boolean inRegion(Location l) {
        return enabled() && l.getWorld() == world && l.getX() >= ledgeMin[0] - 3
                && Math.abs(l.getX() - center.getX()) <= 36 && Math.abs(l.getZ() - center.getZ()) <= 36;
    }

    /** the lobby ledge and the first steps onto the bridge: no kit, no hits */
    public boolean onLedge(Location l) {
        return l.getX() < ledgeMax[0] + 2.0 && l.getZ() >= ledgeMin[2] - 1 && l.getZ() <= ledgeMax[2] + 1
                && l.getY() > catchY;
    }

    public boolean fighting(Player p) {
        return fighting.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------ entering and leaving

    /** the hub gate and /pit enter: onto the ledge, facing the platform */
    public void enter(Player player) {
        if (!enabled()) {
            return;
        }
        boolean de = HubHolograms.german(player);
        player.teleport(arrive);
        player.setFallDistance(0f);
        player.playSound(arrive, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 0.8f);
        player.playSound(arrive, Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.2f);
        player.showTitle(Title.title(Component.text("SKY BRAWL", STORM, TextDecoration.BOLD),
                Component.text(de ? "Schubs die anderen in die Wolken" : "Push the others into the clouds", SOFT),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1900), Duration.ofMillis(600))));
    }

    private void join(Player player) {
        fighting.add(player.getUniqueId());
        giveKit(player);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 0.8f, 1.3f);
        if (greeted.add(player.getUniqueId())) {
            boolean de = HubHolograms.german(player);
            player.sendMessage(Component.text(" ✦ ", STORM).append(Component.text(de
                    ? "Böenstab = schubsen, Windkugel = werfen oder unter dir zünden zum Hochspringen."
                    : "Gust rod = push, wind charge = throw it, or blast it below you to jump high.", SOFT)));
        }
    }

    private void toLedge(Player player) {
        fighting.remove(player.getUniqueId());
        takeKit(player);
    }

    private void leave(Player player) {
        fighting.remove(player.getUniqueId());
        takeKit(player);
        lastHit.remove(player.getUniqueId());
        charged.remove(player.getUniqueId());
        lastGround.remove(player.getUniqueId());
        int s = streak.getOrDefault(player.getUniqueId(), 0);
        streak.remove(player.getUniqueId());
        if (s >= 3) {
            announce("&7" + player.getName() + " &7left with a &b" + s + "&7 knock-off streak.",
                    "&7" + player.getName() + " &7geht mit einer &b" + s + "er&7-Serie.");
        }
    }

    // ------------------------------------------------------------------ kit

    private ItemStack kitItem(Material m, int amount, String en, String de, String loreEn, String loreDe, boolean german) {
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(german ? de : en, STORM, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(german ? loreDe : loreEn, SOFT).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        if (m == Material.BREEZE_ROD) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    private void giveKit(Player player) {
        boolean de = HubHolograms.german(player);
        takeKit(player);
        player.getInventory().setItem(0, kitItem(Material.BREEZE_ROD, 1, "Gust Rod", "Böenstab",
                "Hit someone: off they fly.", "Triff jemanden: weg ist er.", de));
        player.getInventory().setItem(1, kitItem(Material.WIND_CHARGE, MAX_CHARGES, "Wind Charge", "Windkugel",
                "Throw it. Below you: jump high. Refills by itself.", "Werfen. Unter dir: hoher Sprung. Lädt sich nach.", de));
        player.getInventory().setHeldItemSlot(0);
        lastRefill.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void takeKit(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isKit(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void refill(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastRefill.getOrDefault(player.getUniqueId(), 0L) < 2500L) {
            return;
        }
        int have = 0;
        int slot = -1;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == Material.WIND_CHARGE && isKit(it)) {
                have += it.getAmount();
                slot = i;
            }
        }
        if (have >= MAX_CHARGES) {
            lastRefill.put(player.getUniqueId(), now);
            return;
        }
        boolean de = HubHolograms.german(player);
        if (slot >= 0) {
            contents[slot].setAmount(have + 1);
        } else {
            ItemStack fresh = kitItem(Material.WIND_CHARGE, 1, "Wind Charge", "Windkugel",
                    "Throw it. Below you: jump high. Refills by itself.", "Werfen. Unter dir: hoher Sprung. Lädt sich nach.", de);
            ItemStack in1 = player.getInventory().getItem(1);
            if (in1 == null || in1.getType() == Material.AIR) {
                player.getInventory().setItem(1, fresh);
            } else {
                player.getInventory().addItem(fresh);
            }
        }
        lastRefill.put(player.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isKit(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ movement: zones, falls, the orb

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled()) {
            return;
        }
        Location to = event.getTo();
        Player player = event.getPlayer();
        boolean in = inRegion(to);
        UUID id = player.getUniqueId();
        if (!in) {
            if (fighting.contains(id)) {
                leave(player);
            }
            return;
        }
        if (to.getY() < catchY) {
            knockOff(player);
            return;
        }
        if (((Entity) player).isOnGround()) {
            lastGround.put(id, to.clone());
        }
        boolean fight = !onLedge(to);
        if (fight && !fighting.contains(id)) {
            join(player);
        } else if (!fight && fighting.contains(id)) {
            toLedge(player);
        }
        if (fight && orb != null && orb.isValid() && to.distanceSquared(orb.getLocation()) < 1.8 * 1.8) {
            grabOrb(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (enabled() && fighting.contains(event.getPlayer().getUniqueId()) && !inRegion(event.getTo())) {
            leave(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (fighting.contains(p.getUniqueId())) {
            leave(p);
        }
        greeted.remove(p.getUniqueId());
        lastRefill.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        takeKit(event.getPlayer());
    }

    // ------------------------------------------------------------------ hits

    private static Player shooter(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getDamager() instanceof Firework && event.getEntity().getWorld() == world) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || !inRegion(victim.getLocation())) {
            return;
        }
        Player attacker = shooter(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (attacker.equals(victim)) {
            event.setDamage(0);
            return;
        }
        if (!fighting.contains(victim.getUniqueId()) || !fighting.contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        event.setDamage(0);
        long now = System.currentTimeMillis();
        lastHit.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), now));
        if (event.getDamager() instanceof Player) {
            boolean rod = isKit(attacker.getInventory().getItemInMainHand());
            Long ch = charged.get(attacker.getUniqueId());
            boolean storm = ch != null && ch > now;
            if (storm) {
                charged.remove(attacker.getUniqueId());
            }
            double power = storm ? 2.5 : (rod ? 1.15 : 0.55);
            double up = storm ? 0.85 : (rod ? 0.42 : 0.3);
            Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 1e-4) {
                dir = attacker.getLocation().getDirection().setY(0);
            }
            Vector v = dir.normalize().multiply(power).setY(up);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline()) {
                    victim.setVelocity(v);
                }
            });
            if (storm) {
                Location at = victim.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 0.5, 0.6, 0.5, 0.4);
                world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
                world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.3f);
                world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.7f);
            } else if (rod) {
                world.playSound(victim.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.7f, 1.4f);
                world.spawnParticle(Particle.GUST, victim.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    /** No other damage inside the brawl (wind charges, fireworks of the effects, anything). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent event) {
        if (enabled() && event.getEntity() instanceof Player p && inRegion(p.getLocation())) {
            event.setDamage(0);
        }
    }

    @EventHandler
    public void onWindCharge(ProjectileHitEvent event) {
        if (!enabled() || !(event.getEntity() instanceof AbstractWindCharge charge)) {
            return;
        }
        if (!(charge.getShooter() instanceof Player shooter) || !fighting.contains(shooter.getUniqueId())) {
            return;
        }
        Location at = charge.getLocation();
        long now = System.currentTimeMillis();
        for (Player p : world.getPlayers()) {
            if (!p.equals(shooter) && fighting.contains(p.getUniqueId()) && p.getLocation().distanceSquared(at) < 3.2 * 3.2) {
                lastHit.put(p.getUniqueId(), new Hit(shooter.getUniqueId(), now));
            }
        }
    }

    // ------------------------------------------------------------------ knock-offs

    private static final String[] FUN_EN = {"%s went cloud surfing.", "%s took the express lane down.",
            "%s met the clouds up close.", "%s forgot how to stand.", "%s is now a cloud inspector."};
    private static final String[] FUN_DE = {"%s geht Wolkensurfen.", "%s nimmt den Expresslift nach unten.",
            "%s hat die Wolken von nah gesehen.", "%s hat vergessen, wie Stehen geht.", "%s ist jetzt Wolkenprüfer."};
    private static final String[] SELF_EN = {"You slipped. It happens.", "The clouds say hi.", "Nobody saw that. Probably."};
    private static final String[] SELF_DE = {"Ausgerutscht. Passiert.", "Die Wolken lassen grüßen.",
            "Hat keiner gesehen. Wahrscheinlich."};

    private void knockOff(Player victim) {
        UUID vid = victim.getUniqueId();
        Hit h = lastHit.remove(vid);
        long now = System.currentTimeMillis();
        Player by = h != null && now - h.at() < CREDIT_MS ? Bukkit.getPlayer(h.by()) : null;
        if (by != null && (!by.isOnline() || !inRegion(by.getLocation()))) {
            by = null;
        }
        Location edge = lastGround.getOrDefault(vid, center);
        charged.remove(vid);
        BrawlStore.Stats vs = store.of(victim);
        vs.falls++;
        int ended = streak.getOrDefault(vid, 0);
        streak.remove(vid);
        boolean vde = HubHolograms.german(victim);
        int pick = ThreadLocalRandom.current().nextInt(FUN_EN.length);
        if (by != null) {
            BrawlStore.Stats bs = store.of(by);
            bs.knockoffs++;
            bs.shards += SHARDS_PER_KO;
            int s = streak.merge(by.getUniqueId(), 1, Integer::sum);
            if (s > bs.best) {
                bs.best = s;
            }
            bs.effect().play(plugin, edge);
            boolean bde = HubHolograms.german(by);
            by.sendActionBar(Component.text("+1 ", GOLD, TextDecoration.BOLD)
                    .append(Component.text(String.format(bde ? FUN_DE[pick] : FUN_EN[pick], victim.getName()), SOFT))
                    .append(Component.text("  +" + SHARDS_PER_KO + " ✦", STORM)));
            by.playSound(by.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            if (s == 3 || s == 5 || (s >= 10 && s % 5 == 0)) {
                announce("&b" + by.getName() + " &7is on a &b" + s + " knock-off streak&7!",
                        "&b" + by.getName() + " &7hat eine &b" + s + "er-Serie&7!");
            }
            victim.showTitle(Title.title(Component.text(vde ? "PUFF!" : "POOF!", STORM, TextDecoration.BOLD),
                    Component.text(vde ? by.getName() + " hat dich in die Wolken geschubst" : by.getName() + " pushed you into the clouds",
                            SOFT), Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1400), Duration.ofMillis(400))));
            if (ended >= 3) {
                announce("&b" + by.getName() + " &7ended " + victim.getName() + "'s &b" + ended + " streak&7.",
                        "&b" + by.getName() + " &7beendet die &b" + ended + "er-Serie &7von " + victim.getName() + ".");
            }
        } else {
            BrawlEffect.PUFF.play(plugin, edge);
            int i = ThreadLocalRandom.current().nextInt(SELF_EN.length);
            victim.showTitle(Title.title(Component.text(vde ? "PUFF!" : "POOF!", STORM, TextDecoration.BOLD),
                    Component.text(vde ? SELF_DE[i] : SELF_EN[i], SOFT),
                    Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1400), Duration.ofMillis(400))));
        }
        store.touch();
        boardDirty = true;
        fighting.remove(vid);
        takeKit(victim);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) {
                return;
            }
            victim.setFallDistance(0f);
            victim.setVelocity(new Vector());
            victim.teleport(arrive);
            victim.playSound(arrive, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 1.2f);
        });
    }

    private void announce(String en, String german) {
        for (Player p : world.getPlayers()) {
            if (inRegion(p.getLocation())) {
                de.aetherion.pit.util.Msg.send(p, HubHolograms.german(p) ? german : en);
            }
        }
    }

    // ------------------------------------------------------------------ the spire, the storm orb, refills, auras

    private void tick() {
        tick++;
        if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (spireAt != null) {
            if (spire == null || !spire.isValid()) {
                spire = world.spawn(spireAt, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(TAG);
                    d.setItemStack(HubModels.item(HubModels.STORM_ORB));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setViewRange(2.0f);
                });
            }
            float a = tick * 0.35f;
            spire.setInterpolationDelay(0);
            spire.setInterpolationDuration(10);
            spire.setTransformation(new Transformation(new Vector3f(0, (float) Math.sin(tick * 0.4) * 0.25f, 0),
                    new Quaternionf().rotateY(a).rotateX(0.35f), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, spireAt, 6, 0.7, 0.7, 0.7, 0.15);
            }
        }
        int inArena = 0;
        for (UUID id : new HashSet<>(fighting)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                fighting.remove(id);
                continue;
            }
            inArena++;
            refill(p);
            Long ch = charged.get(id);
            if (ch != null) {
                if (ch < now) {
                    charged.remove(id);
                } else {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 5, 0.35, 0.6, 0.35, 0.05);
                }
            }
        }
        // the storm orb: every 30 s while somebody fights, gone after 25 s if nobody takes it
        if (orb != null && orb.isValid()) {
            if (now > orbExpires) {
                orb.remove();
                orb = null;
                nextOrb = now + 15_000L;
            } else {
                orb.setInterpolationDelay(0);
                orb.setInterpolationDuration(10);
                orb.setTransformation(new Transformation(new Vector3f(0, 0.35f + (tick % 2 == 0 ? 0.12f : 0f), 0),
                        new Quaternionf().rotateY(tick * 0.6f), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
                world.spawnParticle(Particle.END_ROD, orb.getLocation().add(0, 0.4, 0), 2, 0.25, 0.25, 0.25, 0.02);
            }
        } else if (orbAt != null && inArena > 0 && now > nextOrb) {
            if (nextOrb == 0) {
                nextOrb = now + 20_000L;
            } else {
                spawnOrb(now);
            }
        }
    }

    private void spawnOrb(long now) {
        orb = world.spawn(orbAt, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(HubModels.item(HubModels.STORM_ORB));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
        });
        orbExpires = now + 25_000L;
        world.playSound(orbAt, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, orbAt.clone().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.3);
        for (UUID id : fighting) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                boolean de = HubHolograms.german(p);
                p.sendActionBar(Component.text("⚡ ", GOLD).append(Component.text(de
                        ? "Eine Sturmkugel in der Mitte! Wer sie holt, schubst doppelt."
                        : "A storm orb in the middle! Grab it to push twice as hard.", STORM)));
            }
        }
    }

    private void grabOrb(Player player) {
        if (orb == null) {
            return;
        }
        Location at = orb.getLocation();
        orb.remove();
        orb = null;
        long now = System.currentTimeMillis();
        nextOrb = now + 30_000L;
        charged.put(player.getUniqueId(), now + CHARGE_MS);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at.add(0, 0.6, 0), 50, 0.6, 0.6, 0.6, 0.4);
        boolean de = HubHolograms.german(player);
        player.showTitle(Title.title(Component.empty(), Component.text(de ? "⚡ Sturmgeladen: dein nächster Treffer schleudert!"
                        : "⚡ Storm charged: your next hit launches!", GOLD),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1600), Duration.ofMillis(400))));
        for (Player p : world.getPlayers()) {
            if (!p.equals(player) && fighting.contains(p.getUniqueId())) {
                boolean pde = HubHolograms.german(p);
                p.sendActionBar(Component.text("⚡ " + player.getName() + (pde ? " ist sturmgeladen. Pass auf!"
                        : " is storm charged. Watch out!"), GOLD));
            }
        }
    }

    // ------------------------------------------------------------------ outside

    public int fightingCount() {
        return fighting.size();
    }

    public void shutdown() {
        for (UUID id : new HashSet<>(fighting)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                takeKit(p);
            }
        }
        fighting.clear();
        if (spire != null && spire.isValid()) {
            spire.remove();
        }
        if (orb != null && orb.isValid()) {
            orb.remove();
        }
        store.save();
    }

    static Component c(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
