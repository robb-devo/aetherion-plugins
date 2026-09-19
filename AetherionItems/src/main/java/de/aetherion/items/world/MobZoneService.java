package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobZoneService implements Runnable {

    public static final int RADIUS = 40;
    public static final int TARGET = 14;
    public static final int BORDERLANDS_DEFAULT_RADIUS = 80;
    public static final int[] BORDERLANDS_RADII = {40, 60, 80, 110, 150};
    /** Y ≤ this inside Borderlands = Crypt (T4 only). */
    public static final int CRYPT_CEILING_Y = WildlifeLooks.CRYPT_CEILING_Y;
    /** Surface hostiles never spawn / linger above this Y in Borderlands. */
    public static final int SURFACE_MAX_Y = 67;
    /** Eldervale deep hostiles only at/below this Y. */
    public static final int ELDERVALE_CEILING_Y = 24;
    public static final int ELDERVALE_DEFAULT_RADIUS = 200;

    /**
     * Surface hostiles across the whole disk (not clustered on the player).
     * Target band ~200–280 for typical large Borderlands (r≈140).
     */
    private static final double SURFACE_DENSITY = 0.0041;
    private static final int SURFACE_MIN = 200;
    private static final int SURFACE_MAX = 280;
    /** Crypt hostiles — own budget, never counted toward surface. */
    private static final double CRYPT_DENSITY = 0.00115;
    private static final int CRYPT_MIN = 10;
    private static final int CRYPT_MAX = 55;
    /** Eldervale deep pack — active when players are on the island. */
    private static final double ELDERVALE_DENSITY = 0.0012;
    private static final int ELDERVALE_MIN = 14;
    private static final int ELDERVALE_MAX = 40;
    /** Soft entrance around Vex — max surface mobs (T1 only) outside the taboo. */
    private static final int VEX_SURFACE_CAP = 10;
    /** Absolute no-mob bubble at Vex — spawn + walk-in forbidden. */
    private static final double VEX_TABOO_RADIUS = 15.0;
    private static final double VEX_TABOO_RADIUS_SQ = VEX_TABOO_RADIUS * VEX_TABOO_RADIUS;

    private static final Map<EntityType, Integer> MIX = new EnumMap<>(EntityType.class);
    private static final Map<EntityType, Integer> BORDERLANDS_MIX = new EnumMap<>(EntityType.class);
    private static final Map<EntityType, Integer> ELDERVALE_MIX = new EnumMap<>(EntityType.class);

    static {
        MIX.put(EntityType.ZOMBIE, 5);
        MIX.put(EntityType.SKELETON, 4);
        MIX.put(EntityType.SPIDER, 3);
        MIX.put(EntityType.CREEPER, 2);
        MIX.put(EntityType.ENDERMAN, 1);
        MIX.put(EntityType.HUSK, 3);
        MIX.put(EntityType.STRAY, 3);
        MIX.put(EntityType.DROWNED, 2);

        BORDERLANDS_MIX.put(EntityType.HUSK, 6);
        BORDERLANDS_MIX.put(EntityType.STRAY, 5);
        BORDERLANDS_MIX.put(EntityType.SKELETON, 4);
        BORDERLANDS_MIX.put(EntityType.ZOMBIE, 3);
        BORDERLANDS_MIX.put(EntityType.SPIDER, 3);
        BORDERLANDS_MIX.put(EntityType.CREEPER, 2);
        BORDERLANDS_MIX.put(EntityType.WITCH, 2);
        BORDERLANDS_MIX.put(EntityType.PILLAGER, 2);
        BORDERLANDS_MIX.put(EntityType.VINDICATOR, 1);

        ELDERVALE_MIX.put(EntityType.ZOMBIE, 5);
        ELDERVALE_MIX.put(EntityType.SKELETON, 4);
        ELDERVALE_MIX.put(EntityType.HUSK, 3);
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, AnimalZone> zones = new ConcurrentHashMap<>();

    public MobZoneService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mob-zones.yml");
        load();
        ensureBorderlandsZone();
        ensureEldervaleZone();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 60L);
    }

    /** Borderlands is placed with the Dev Menu anchor — overlaps are consolidated to one. */
    private void ensureBorderlandsZone() {
        consolidateBorderlandsZones();
    }

    /** Always keep one Eldervale deep disk (invalid YAML UUIDs used to drop it silently). */
    private void ensureEldervaleZone() {
        for (AnimalZone zone : zones.values()) {
            if (isEldervale(zone)) {
                return;
            }
        }
        AnimalZone zone = new AnimalZone(
                UUID.fromString("e1d3e7a1-e1de-4a1e-8e55-e1de00000002"),
                "world",
                47.5,
                89.5,
                563.5,
                ELDERVALE_DEFAULT_RADIUS,
                "eldervale"
        );
        zones.put(zone.getId(), zone);
        save();
        plugin.getLogger().info("Created default Eldervale deep mob zone (r="
                + ELDERVALE_DEFAULT_RADIUS + ", Y≤" + ELDERVALE_CEILING_Y + ").");
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world != null) {
            seed(zone, 12, true);
        }
    }

    /** Keep a single Borderlands disk covering every placed marker. */
    private void consolidateBorderlandsZones() {
        List<AnimalZone> borderlands = new ArrayList<>();
        for (AnimalZone zone : zones.values()) {
            if (isBorderlands(zone)) {
                borderlands.add(zone);
            }
        }
        if (borderlands.size() <= 1) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double ySum = 0;
        String worldName = borderlands.get(0).getWorldName();
        for (AnimalZone zone : borderlands) {
            minX = Math.min(minX, zone.getX() - zone.getRadius());
            maxX = Math.max(maxX, zone.getX() + zone.getRadius());
            minZ = Math.min(minZ, zone.getZ() - zone.getRadius());
            maxZ = Math.max(maxZ, zone.getZ() + zone.getRadius());
            ySum += zone.getY();
            removeMarker(zone);
            despawnTagged(zone);
            zones.remove(zone.getId());
        }
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        double cy = ySum / borderlands.size();
        double need = 0;
        for (AnimalZone zone : borderlands) {
            double dx = zone.getX() - cx;
            double dz = zone.getZ() - cz;
            need = Math.max(need, Math.sqrt(dx * dx + dz * dz) + zone.getRadius());
        }
        int radius = (int) Math.ceil(need);
        radius = Math.max(40, Math.min(250, radius));
        AnimalZone merged = new AnimalZone(
                UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde"),
                worldName,
                cx,
                cy,
                cz,
                radius,
                "borderlands"
        );
        zones.put(merged.getId(), merged);
        save();
        plugin.getLogger().info("Consolidated " + borderlands.size()
                + " Borderlands zones into one (" + radius + "m @ "
                + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", cx, cy, cz) + ").");
    }

    public ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.ROTTEN_FLESH);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lMob Anchor");
            meta.setLore(List.of(
                    "§7DEV · populate a region with hostiles.",
                    "§7Radius §f" + RADIUS + " §7blocks.",
                    "§8Zombies, skeletons, spiders,",
                    "§8creepers and the occasional enderman.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7to remove nearby."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.mobAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBorderlandsAnchor() {
        return createBorderlandsAnchor(BORDERLANDS_DEFAULT_RADIUS);
    }

    public ItemStack createBorderlandsAnchor(int radius) {
        int use = normalizeBorderlandsRadius(radius);
        ItemStack item = new ItemStack(Material.COARSE_DIRT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lBorderlands Marker");
            meta.setLore(borderlandsLore(use));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.borderlandsAnchor(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.borderlandsRadius(), PersistentDataType.INTEGER, use);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createEldervaleAnchor() {
        return createEldervaleAnchor(ELDERVALE_DEFAULT_RADIUS);
    }

    public ItemStack createEldervaleAnchor(int radius) {
        int use = Math.max(40, Math.min(250, radius));
        ItemStack item = new ItemStack(Material.DEEPSLATE_IRON_ORE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lEldervale Deep Marker");
            meta.setLore(List.of(
                    "§7DEV · Eldervale deep hostiles.",
                    "§7Radius §f" + use + " §7blocks.",
                    "§8Spawns only at/below Y §f" + ELDERVALE_CEILING_Y + "§8.",
                    "§8Rotten Miner / Cave Scrapper / Dust Digger.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7to remove nearby."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.eldervaleMobAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.mobAnchor(), PersistentDataType.BYTE);
    }

    public boolean isBorderlandsAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.borderlandsAnchor(), PersistentDataType.BYTE);
    }

    public boolean isEldervaleAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.eldervaleMobAnchor(), PersistentDataType.BYTE);
    }

    public int borderlandsRadiusOf(ItemStack item) {
        if (!isBorderlandsAnchor(item)) {
            return BORDERLANDS_DEFAULT_RADIUS;
        }
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.borderlandsRadius(),
                PersistentDataType.INTEGER
        );
        return normalizeBorderlandsRadius(stored == null ? BORDERLANDS_DEFAULT_RADIUS : stored);
    }

    public int cycleBorderlandsRadius(ItemStack item) {
        if (!isBorderlandsAnchor(item) || !item.hasItemMeta()) {
            return BORDERLANDS_DEFAULT_RADIUS;
        }
        int current = borderlandsRadiusOf(item);
        int next = BORDERLANDS_RADII[0];
        for (int i = 0; i < BORDERLANDS_RADII.length; i++) {
            if (BORDERLANDS_RADII[i] == current) {
                next = BORDERLANDS_RADII[(i + 1) % BORDERLANDS_RADII.length];
                break;
            }
        }
        ItemMeta meta = item.getItemMeta();
        meta.setLore(borderlandsLore(next));
        meta.getPersistentDataContainer().set(ItemKeys.borderlandsRadius(), PersistentDataType.INTEGER, next);
        item.setItemMeta(meta);
        return next;
    }

    private static List<String> borderlandsLore(int radius) {
        return List.of(
                "§7DEV · Borderlands combat waste.",
                "§7Radius §f" + radius + " §7blocks.",
                "§8Husk / Stray / Skeleton mix + TAB name.",
                "",
                "§eRight-click a block §7to place.",
                "§eLeft-click §7to cycle radius.",
                "§eSneak + right-click §7to remove nearby."
        );
    }

    private static int normalizeBorderlandsRadius(int radius) {
        for (int option : BORDERLANDS_RADII) {
            if (option == radius) {
                return option;
            }
        }
        return BORDERLANDS_DEFAULT_RADIUS;
    }

    public AnimalZone place(Location location, Player player) {
        return place(location, player, RADIUS, null);
    }

    public AnimalZone placeBorderlands(Location location, Player player, int radius) {
        clearBorderlands();
        return place(location, player, normalizeBorderlandsRadius(radius), "borderlands");
    }

    public AnimalZone placeEldervale(Location location, Player player, int radius) {
        clearEldervale();
        return place(location, player, Math.max(40, Math.min(250, radius)), "eldervale");
    }

    /** Drop every Borderlands combat disk (markers + tagged mobs). */
    public void clearBorderlands() {
        List<AnimalZone> remove = new ArrayList<>();
        for (AnimalZone zone : zones.values()) {
            if (isBorderlands(zone)) {
                remove.add(zone);
            }
        }
        for (AnimalZone zone : remove) {
            removeMarker(zone);
            despawnTagged(zone);
            zones.remove(zone.getId());
        }
        if (!remove.isEmpty()) {
            save();
        }
    }

    public void clearEldervale() {
        List<AnimalZone> remove = new ArrayList<>();
        for (AnimalZone zone : zones.values()) {
            if (isEldervale(zone)) {
                remove.add(zone);
            }
        }
        for (AnimalZone zone : remove) {
            removeMarker(zone);
            despawnTagged(zone);
            zones.remove(zone.getId());
        }
        if (!remove.isEmpty()) {
            save();
        }
    }

    public boolean containsBorderlands(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (AnimalZone zone : zones.values()) {
            if (!isBorderlands(zone)) {
                continue;
            }
            if (!zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            // Horizontal disk — Crypt below the marker still counts as Borderlands.
            double dx = zone.getX() - location.getX();
            double dz = zone.getZ() - location.getZ();
            double r = zone.getRadius();
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    public boolean containsEldervale(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (AnimalZone zone : zones.values()) {
            if (!isEldervale(zone)) {
                continue;
            }
            if (!zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = zone.getX() - location.getX();
            double dz = zone.getZ() - location.getZ();
            double r = zone.getRadius();
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    public boolean inCrypt(Location location) {
        return location != null
                && containsBorderlands(location)
                && location.getY() <= CRYPT_CEILING_Y;
    }

    public AnimalZone place(Location location, Player player, int radius, String profile) {
        if (location.getWorld() == null) {
            return null;
        }
        if (isDungeonWorld(location.getWorld())) {
            if (player != null) {
                player.sendMessage("§cMob zones stay out of dungeon worlds.");
            }
            return null;
        }
        boolean borderlands = profile != null && profile.equalsIgnoreCase("borderlands");
        boolean eldervale = profile != null && profile.equalsIgnoreCase("eldervale");
        if (!borderlands && !eldervale) {
            AnimalZone existing = nearest(location, 24);
            if (existing != null) {
                if (player != null) {
                    player.sendMessage("§cToo close to another mob zone. §7Sneak-click to remove first.");
                }
                return null;
            }
        }
        AnimalZone zone = new AnimalZone(
                UUID.randomUUID(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                Math.max(8, radius),
                profile
        );
        spawnMarker(zone);
        zones.put(zone.getId(), zone);
        save();
        if (borderlands) {
            seed(zone, 12, false);
            seed(zone, 6, true);
        } else if (eldervale) {
            seed(zone, 10, true);
        } else {
            seed(zone, 8, false);
        }
        if (player != null) {
            player.playSound(location, Sound.ENTITY_ZOMBIE_AMBIENT, 0.6f, 0.8f);
            if (borderlands) {
                player.sendMessage("§6Borderlands set. §7One zone only · hostiles + TAB · §f" + zone.getRadius() + "m§7.");
            } else if (eldervale) {
                player.sendMessage("§bEldervale deep set. §7Y≤" + ELDERVALE_CEILING_Y
                        + " miners · §f" + zone.getRadius() + "m§7.");
            } else {
                player.sendMessage("§cMob zone set. §7Hostiles will keep this area lively.");
            }
        }
        return zone;
    }

    public boolean removeNearest(Location location, Player player) {
        AnimalZone zone = nearest(location, 100);
        if (zone == null) {
            if (player != null) {
                player.sendMessage("§cNo mob zone nearby.");
            }
            return false;
        }
        removeMarker(zone);
        despawnTagged(zone);
        zones.remove(zone.getId());
        save();
        if (player != null) {
            player.playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 0.7f);
            player.sendMessage("§eRemoved mob zone.");
        }
        return true;
    }

    @Override
    public void run() {
        for (AnimalZone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null || isDungeonWorld(world)) {
                continue;
            }
            if (!playersNearZone(zone, world)) {
                continue;
            }
            ensureMarker(zone);
            enforce(zone, world);
            Location center = zone.center(world);
            if (ThreadLocalRandom.current().nextInt(5) == 0 && center.getChunk().isLoaded()) {
                world.spawnParticle(Particle.SOUL, center.clone().add(0, 1.2, 0), 5, 0.4, 0.3, 0.4, 0);
            }
            if (ShabbyMineGuard.inShabbyMine(center)) {
                continue;
            }
            if (isBorderlands(zone)) {
                int surfaceHave = countSurface(zone, world);
                int surfaceWant = surfaceTargetFor(zone);
                if (surfaceHave < surfaceWant) {
                    // Bigger refill batches so the disk stays warm, not just the player bubble.
                    seed(zone, Math.min(36, surfaceWant - surfaceHave), false);
                }
                int cryptHave = countCrypt(zone, world);
                int cryptWant = cryptTargetFor(zone);
                if (cryptHave < cryptWant) {
                    seed(zone, Math.min(10, cryptWant - cryptHave), true);
                }
                continue;
            }
            if (isEldervale(zone)) {
                int have = countEldervaleDeep(zone, world);
                int want = eldervaleTargetFor(zone);
                if (have < want) {
                    seed(zone, Math.min(12, want - have), true);
                }
                continue;
            }
            int living = count(zone, world);
            int target = targetFor(zone);
            if (living >= target) {
                continue;
            }
            int need = Math.min(3, target - living);
            seed(zone, need, false);
        }
    }

    /** Huge Borderlands / Eldervale disks must tick when a player is inside — horizontal only. */
    private boolean playersNearZone(AnimalZone zone, World world) {
        Location center = zone.center(world);
        double reach = zone.getRadius() + 40;
        double reachSq = reach * reach;
        boolean horizontal = isBorderlands(zone) || isEldervale(zone);
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            double distSq = horizontal
                    ? dx * dx + dz * dz
                    : at.distanceSquared(center);
            if (distSq <= reachSq) {
                return true;
            }
        }
        return false;
    }

    private int targetFor(AnimalZone zone) {
        if (isBorderlands(zone)) {
            // Combined only for legacy callers — live tick uses surface/crypt separately.
            return surfaceTargetFor(zone) + cryptTargetFor(zone);
        }
        if (isEldervale(zone)) {
            return eldervaleTargetFor(zone);
        }
        int radius = Math.max(RADIUS, zone.getRadius());
        return Math.max(TARGET, radius / 3);
    }

    private int eldervaleTargetFor(AnimalZone zone) {
        int radius = Math.max(RADIUS, zone.getRadius());
        double area = Math.PI * (double) radius * (double) radius;
        int raw = (int) Math.round(area * ELDERVALE_DENSITY);
        return Math.min(ELDERVALE_MAX, Math.max(ELDERVALE_MIN, raw));
    }

    private int countEldervaleDeep(AnimalZone zone, World world) {
        int count = 0;
        for (LivingEntity entity : tagged(zone, world)) {
            if (entity.getLocation().getY() <= ELDERVALE_CEILING_Y) {
                count++;
            }
        }
        return count;
    }

    private boolean canClaimIntoBand(AnimalZone zone, LivingEntity living) {
        Location at = living.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return false;
        }
        if (!isBorderlands(zone)) {
            return tagged(zone, world).size() < targetFor(zone);
        }
        boolean crypt = at.getY() <= CRYPT_CEILING_Y;
        int have = crypt ? countCrypt(zone, world) : countSurface(zone, world);
        int cap = crypt ? cryptTargetFor(zone) : surfaceTargetFor(zone);
        return have < cap;
    }

    /** Surface (Y > 55) budget from disk area — crypt never eats into this. */
    private int surfaceTargetFor(AnimalZone zone) {
        int radius = Math.max(RADIUS, zone.getRadius());
        double area = Math.PI * (double) radius * (double) radius;
        int raw = (int) Math.round(area * SURFACE_DENSITY);
        return Math.min(SURFACE_MAX, Math.max(SURFACE_MIN, raw));
    }

    /** Crypt (Y ≤ 55) own budget. */
    private int cryptTargetFor(AnimalZone zone) {
        int radius = Math.max(RADIUS, zone.getRadius());
        double area = Math.PI * (double) radius * (double) radius;
        int raw = (int) Math.round(area * CRYPT_DENSITY);
        return Math.min(CRYPT_MAX, Math.max(CRYPT_MIN, raw));
    }

    private boolean isBorderlands(AnimalZone zone) {
        String profile = zone.getProfile();
        return profile != null && profile.equalsIgnoreCase("borderlands");
    }

    private boolean isEldervale(AnimalZone zone) {
        String profile = zone.getProfile();
        return profile != null && profile.equalsIgnoreCase("eldervale");
    }

    private Map<EntityType, Integer> mixFor(AnimalZone zone) {
        if (isBorderlands(zone)) {
            return BORDERLANDS_MIX;
        }
        if (isEldervale(zone)) {
            return ELDERVALE_MIX;
        }
        return MIX;
    }

    private void seed(AnimalZone zone, int amount, boolean preferCrypt) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null || isDungeonWorld(world) || amount <= 0) {
            return;
        }
        int spawned = 0;
        int attempts = 0;
        boolean borderlands = isBorderlands(zone);
        boolean eldervale = isEldervale(zone);
        int attemptBudget = amount * (borderlands || eldervale ? 22 : 14);
        while (spawned < amount && attempts < attemptBudget) {
            attempts++;
            Location spot = findSpot(zone, world, (borderlands && preferCrypt) || eldervale);
            if (spot == null) {
                continue;
            }
            if (ShabbyMineGuard.inShabbyMine(spot)) {
                continue;
            }
            if (BorderlandsRiteService.isMobSafeZone(spot)) {
                continue;
            }
            // Refuse wrong band so surface seed never fills crypt slots and vice versa.
            if (borderlands) {
                boolean cryptSpot = spot.getY() <= CRYPT_CEILING_Y;
                if (preferCrypt != cryptSpot) {
                    continue;
                }
            }
            if (eldervale && spot.getY() > ELDERVALE_CEILING_Y) {
                continue;
            }
            EntityType type = pickType(zone, world, preferCrypt || eldervale);
            if (type == null) {
                type = WildlifeLooks.pickMob(world, spot);
            }
            if (type == null) {
                continue;
            }
            LivingEntity entity = (LivingEntity) world.spawnEntity(spot, type);
            decorate(entity, zone);
            if (borderlands) {
                WildlifeLooks.disableSunBurn(entity);
                WildlifeLooks.applyBorderlandsTier(entity, spot);
            } else if (eldervale) {
                WildlifeLooks.disableSunBurn(entity);
                WildlifeLooks.applyEldervaleMiner(entity);
            }
            spawned++;
        }
    }

    private int countSurface(AnimalZone zone, World world) {
        int count = 0;
        for (LivingEntity entity : tagged(zone, world)) {
            if (entity.getLocation().getY() > CRYPT_CEILING_Y) {
                count++;
            }
        }
        return count;
    }

    private int countCrypt(AnimalZone zone, World world) {
        int count = 0;
        for (LivingEntity entity : tagged(zone, world)) {
            if (entity.getLocation().getY() <= CRYPT_CEILING_Y) {
                count++;
            }
        }
        return count;
    }

    private EntityType pickType(AnimalZone zone, World world) {
        return pickType(zone, world, false);
    }

    private EntityType pickType(AnimalZone zone, World world, boolean cryptBand) {
        Map<EntityType, Integer> mix = mixFor(zone);
        int mixTotal = 0;
        for (int weight : mix.values()) {
            mixTotal += Math.max(0, weight);
        }
        if (mixTotal <= 0) {
            return null;
        }
        int target = isBorderlands(zone)
                ? (cryptBand ? cryptTargetFor(zone) : surfaceTargetFor(zone))
                : targetFor(zone);
        List<LivingEntity> members = tagged(zone, world);
        List<EntityType> open = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> entry : mix.entrySet()) {
            int want = isBorderlands(zone)
                    ? Math.max(1, (entry.getValue() * target) / mixTotal)
                    : entry.getValue();
            int have = 0;
            for (LivingEntity entity : members) {
                if (entity.getType() != entry.getKey()) {
                    continue;
                }
                if (isBorderlands(zone)) {
                    boolean inCrypt = entity.getLocation().getY() <= CRYPT_CEILING_Y;
                    if (inCrypt != cryptBand) {
                        continue;
                    }
                }
                have++;
            }
            if (have < want) {
                open.add(entry.getKey());
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        return open.get(ThreadLocalRandom.current().nextInt(open.size()));
    }

    private int count(AnimalZone zone, World world) {
        return tagged(zone, world).size();
    }

    private void decorate(LivingEntity entity, AnimalZone zone) {
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        tag(entity, zone.getId());
    }

    private void tag(LivingEntity entity, UUID zoneId) {
        entity.getPersistentDataContainer().set(
                ItemKeys.zoneSpawn(),
                PersistentDataType.STRING,
                zoneId.toString()
        );
    }

    private boolean belongs(Entity entity, UUID zoneId) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        String tagged = entity.getPersistentDataContainer().get(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
        return zoneId.toString().equals(tagged);
    }

    private boolean isManaged(Entity entity) {
        if (!(entity instanceof Monster) || !entity.isValid()) {
            return false;
        }
        EntityType type = entity.getType();
        if (!MIX.containsKey(type) && !BORDERLANDS_MIX.containsKey(type) && !ELDERVALE_MIX.containsKey(type)) {
            return false;
        }
        // Never claim or cull BossEngine / pets / dungeon mobs / ore trolls.
        if (de.aetherion.core.AetherEntities.isSystemOwned(entity)) {
            return false;
        }
        if (de.aetherion.items.mining.OreTrollListener.isOreTroll(entity)) {
            return false;
        }
        return true;
    }

    private List<LivingEntity> tagged(AnimalZone zone, World world) {
        List<LivingEntity> found = new ArrayList<>();
        Location center = zone.center(world);
        if (center == null) {
            return found;
        }
        double scan = zone.getRadius() + 8;
        // Eldervale deep miners sit far below the surface marker — need full column scan.
        double scanY = isEldervale(zone) || isBorderlands(zone) ? 320.0 : 24.0;
        for (Entity entity : world.getNearbyEntities(center, scan, scanY, scan)) {
            if (entity instanceof LivingEntity living
                    && isManaged(living)
                    && belongs(living, zone.getId())) {
                found.add(living);
            }
        }
        return found;
    }

    private void enforce(AnimalZone zone, World world) {
        Location center = zone.center(world);
        List<LivingEntity> members = tagged(zone, world);
        double claimRadius = zone.getRadius() + 8;
        double claimY = isEldervale(zone) || isBorderlands(zone) ? 320.0 : 24.0;
        for (Entity entity : world.getNearbyEntities(center, claimRadius, claimY, claimRadius)) {
            if (!isManaged(entity) || belongs(entity, zone.getId())) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                continue;
            }
            if (canClaimIntoBand(zone, living) && !BorderlandsRiteService.isMobSafeZone(living.getLocation())) {
                tag(living, zone.getId());
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
                members.add(living);
            } else if (!BorderlandsRiteService.isMobSafeZone(living.getLocation())) {
                living.remove();
            } else {
                living.remove();
            }
        }
        members.removeIf(de.aetherion.core.AetherEntities::isSystemOwned);
        // Keep the Rite Warden bubble clear — despawn tagged hostiles that wander in.
        members.removeIf(living -> {
            if (!BorderlandsRiteService.isMobSafeZone(living.getLocation())) {
                return false;
            }
            if (!de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                living.remove();
            }
            return true;
        });
        boolean borderlands = isBorderlands(zone);
        boolean eldervale = isEldervale(zone);
        if (borderlands) {
            trimBand(members, zone, world, false, surfaceTargetFor(zone));
            trimBand(members, zone, world, true, cryptTargetFor(zone));
            clearVexTaboo(members, zone, world);
            trimVexRing(members, zone, world);
            unclumpDarkSurface(members, zone, world);
            cullAboveSurfaceMax(members);
        } else if (eldervale) {
            trimEldervale(members, zone, world);
        } else {
            members.sort((a, b) -> Double.compare(
                    b.getLocation().distanceSquared(zone.center(world)),
                    a.getLocation().distanceSquared(zone.center(world))
            ));
            int target = targetFor(zone);
            while (members.size() > target) {
                LivingEntity extra = members.remove(0);
                if (de.aetherion.core.AetherEntities.isSystemOwned(extra)) {
                    continue;
                }
                extra.remove();
            }
        }
        double maxDistSq = (zone.getRadius() + 6) * (zone.getRadius() + 6);
        for (LivingEntity living : members) {
            if (!living.isValid()) {
                continue;
            }
            Location at = living.getLocation();
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            double distSq = borderlands || eldervale ? dx * dx + dz * dz : at.distanceSquared(center);
            if (distSq <= maxDistSq) {
                // Height-band only (surface vs crypt). Vex T1 ring no longer hard-kills wander-ins.
                if (borderlands && mismatchedTier(living, at)) {
                    living.remove();
                }
                if (eldervale && at.getY() > ELDERVALE_CEILING_Y) {
                    living.remove();
                }
                continue;
            }
            Location spot = findSpot(zone, world, eldervale || at.getY() <= CRYPT_CEILING_Y);
            living.teleport(spot == null ? center.clone().add(0.5, 1, 0.5) : spot);
        }
    }

    private void trimEldervale(List<LivingEntity> members, AnimalZone zone, World world) {
        Location center = zone.center(world);
        List<LivingEntity> deep = new ArrayList<>();
        for (LivingEntity living : List.copyOf(members)) {
            if (!living.isValid()) {
                members.remove(living);
                continue;
            }
            if (living.getLocation().getY() > ELDERVALE_CEILING_Y) {
                if (!de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                    living.remove();
                }
                members.remove(living);
                continue;
            }
            deep.add(living);
        }
        deep.sort((a, b) -> Double.compare(
                horizontalDistSq(b.getLocation(), center),
                horizontalDistSq(a.getLocation(), center)
        ));
        int cap = eldervaleTargetFor(zone);
        while (deep.size() > cap) {
            LivingEntity extra = deep.remove(0);
            members.remove(extra);
            if (!de.aetherion.core.AetherEntities.isSystemOwned(extra)) {
                extra.remove();
            }
        }
    }

    private static double horizontalDistSq(Location at, Location center) {
        double dx = at.getX() - center.getX();
        double dz = at.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    /** Cap one height band without letting crypt steal surface slots (or reverse). */
    private void trimBand(
            List<LivingEntity> members,
            AnimalZone zone,
            World world,
            boolean crypt,
            int cap
    ) {
        Location center = zone.center(world);
        List<LivingEntity> band = new ArrayList<>();
        for (LivingEntity living : members) {
            if (!living.isValid()) {
                continue;
            }
            boolean inCrypt = living.getLocation().getY() <= CRYPT_CEILING_Y;
            if (inCrypt == crypt) {
                band.add(living);
            }
        }
        band.sort((a, b) -> Double.compare(
                b.getLocation().distanceSquared(center),
                a.getLocation().distanceSquared(center)
        ));
        while (band.size() > cap) {
            LivingEntity extra = band.remove(0);
            members.remove(extra);
            if (!de.aetherion.core.AetherEntities.isSystemOwned(extra)) {
                extra.remove();
            }
        }
    }

    /**
     * Soft entrance around Vex: spawn rules stay T1 + cap.
     * Wander-in T2/T3 are pushed out of the ~60 block ring.
     */
    private void trimVexRing(List<LivingEntity> members, AnimalZone zone, World world) {
        List<LivingEntity> ringT1 = new ArrayList<>();
        for (LivingEntity living : List.copyOf(members)) {
            if (!living.isValid()) {
                continue;
            }
            Location at = living.getLocation();
            if (at.getY() <= CRYPT_CEILING_Y) {
                continue;
            }
            if (WildlifeLooks.nearVex(at, VEX_TABOO_RADIUS_SQ)) {
                continue;
            }
            if (!WildlifeLooks.nearVexStarter(at)) {
                continue;
            }
            byte tier = WildlifeLooks.tierOf(living);
            if (tier != WildlifeLooks.TIER_NORMAL) {
                members.remove(living);
                if (!de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                    if (!WildlifeLooks.ejectFromVexRadius(living, WildlifeLooks.VEX_T1_RADIUS)) {
                        living.remove();
                    }
                }
                continue;
            }
            ringT1.add(living);
        }
        while (ringT1.size() > VEX_SURFACE_CAP) {
            LivingEntity extra = ringT1.remove(ringT1.size() - 1);
            members.remove(extra);
            if (!de.aetherion.core.AetherEntities.isSystemOwned(extra)) {
                // Excess T1: push outward past the ring instead of deleting.
                if (!WildlifeLooks.ejectFromVexRadius(extra, WildlifeLooks.VEX_T1_RADIUS)) {
                    extra.remove();
                }
            }
        }
    }

    private static boolean mismatchedTier(LivingEntity living, Location at) {
        byte tier = WildlifeLooks.tierOf(living);
        double y = at.getY();
        if (y <= CRYPT_CEILING_Y) {
            return tier != WildlifeLooks.TIER_CRYPT;
        }
        // Soft T1-only combat around Vex — higher tiers don't belong in the entrance ring.
        if (WildlifeLooks.nearVexStarter(at) && tier != WildlifeLooks.TIER_NORMAL) {
            return true;
        }
        // Crypt mobs on surface still wrong — Vex ring wander-ins are handled softly elsewhere.
        return tier == WildlifeLooks.TIER_CRYPT;
    }

    /** Absolute no-mob bubble at Vex — push out (no hard despawn). */
    private void clearVexTaboo(List<LivingEntity> members, AnimalZone zone, World world) {
        for (LivingEntity living : world.getLivingEntities()) {
            if (!(living instanceof Monster) || living instanceof Player) {
                continue;
            }
            if (de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                continue;
            }
            Location at = living.getLocation();
            if (at.getY() <= CRYPT_CEILING_Y) {
                continue;
            }
            if (!WildlifeLooks.nearVex(at, VEX_TABOO_RADIUS_SQ)) {
                continue;
            }
            WildlifeLooks.ejectFromVexRadius(living, VEX_TABOO_RADIUS);
        }
    }

    /** Pull surface packs out of dark pockets onto open sky. */
    private void unclumpDarkSurface(List<LivingEntity> members, AnimalZone zone, World world) {
        int moved = 0;
        for (LivingEntity living : List.copyOf(members)) {
            if (moved >= 8 || !living.isValid() || de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                continue;
            }
            Location at = living.getLocation();
            if (at.getY() <= CRYPT_CEILING_Y) {
                continue;
            }
            if (at.getBlock().getLightFromSky() >= 12) {
                continue;
            }
            Location spot = findSpot(zone, world, false);
            if (spot == null) {
                continue;
            }
            living.teleport(spot);
            if (living instanceof Mob mob) {
                mob.setTarget(null);
            }
            moved++;
        }
    }

    /** Despawn surface hostiles parked above the Borderlands height cap. */
    private void cullAboveSurfaceMax(List<LivingEntity> members) {
        for (LivingEntity living : List.copyOf(members)) {
            if (!living.isValid() || de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                continue;
            }
            double y = living.getLocation().getY();
            if (y > SURFACE_MAX_Y) {
                living.remove();
                members.remove(living);
            }
        }
    }

    private void despawnTagged(AnimalZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        for (LivingEntity entity : tagged(zone, world)) {
            entity.remove();
        }
    }

    public void revealToLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revealTo(player), 15L);
    }

    public void revealTo(Player player) {
        if (player == null) {
            return;
        }
        for (AnimalZone zone : zones.values()) {
            if (zone.getMarkerId() == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, player, entity);
            }
        }
    }

    private Location findSpot(AnimalZone zone, World world) {
        return findSpot(zone, world, false);
    }

    private Location findSpot(AnimalZone zone, World world, boolean preferCrypt) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = zone.center(world);
        boolean borderlands = isBorderlands(zone);
        boolean eldervale = isEldervale(zone);
        double reach = zone.getRadius();
        double reachSq = reach * reach;
        int vexHave = borderlands && !preferCrypt ? countNearVex(zone, world) : 0;
        int tries = borderlands || eldervale ? 40 : 24;
        double playerClearSq = borderlands || eldervale ? 9.0 : 36.0; // 3m vs 6m
        double crowdRadius = borderlands || eldervale ? 4.5 : 4.0;
        for (int i = 0; i < tries; i++) {
            Location anchor;
            double dist;
            if (eldervale) {
                // Prefer columns near players who are already deep-mining.
                List<Player> deep = new ArrayList<>();
                for (Player player : world.getPlayers()) {
                    if (player.getWorld() != world) {
                        continue;
                    }
                    Location at = player.getLocation();
                    double pdx = at.getX() - center.getX();
                    double pdz = at.getZ() - center.getZ();
                    if (pdx * pdx + pdz * pdz > reachSq) {
                        continue;
                    }
                    if (at.getY() <= ELDERVALE_CEILING_Y + 8) {
                        deep.add(player);
                    }
                }
                if (!deep.isEmpty() && random.nextDouble() < 0.7) {
                    anchor = deep.get(random.nextInt(deep.size())).getLocation();
                    dist = 4 + random.nextDouble() * 18;
                } else {
                    anchor = center;
                    dist = 8 + random.nextDouble() * Math.max(10, zone.getRadius() - 8);
                }
            } else if (borderlands && !preferCrypt) {
                // Full-disk only — never bias toward the player.
                anchor = center;
                dist = 10 + random.nextDouble() * Math.max(12, zone.getRadius() - 10);
            } else if (borderlands && preferCrypt) {
                // Crypt: random disk samples, search downward — not player-anchored.
                anchor = center;
                dist = 8 + random.nextDouble() * Math.max(10, zone.getRadius() - 8);
            } else {
                List<Player> nearby = new ArrayList<>();
                for (Player player : world.getPlayers()) {
                    if (player.getWorld() != world) {
                        continue;
                    }
                    Location at = player.getLocation();
                    double pdx = at.getX() - center.getX();
                    double pdz = at.getZ() - center.getZ();
                    if (pdx * pdx + pdz * pdz <= reachSq) {
                        nearby.add(player);
                    }
                }
                if (!nearby.isEmpty()) {
                    anchor = nearby.get(random.nextInt(nearby.size())).getLocation();
                    dist = 8 + random.nextDouble() * 28;
                } else {
                    anchor = center;
                    dist = 6 + random.nextDouble() * Math.max(8, zone.getRadius() - 8);
                }
            }
            double angle = random.nextDouble() * Math.PI * 2;
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            double dx = x + 0.5 - center.getX();
            double dz = z + 0.5 - center.getZ();
            if (dx * dx + dz * dz > reachSq) {
                continue;
            }
            world.getChunkAt(x >> 4, z >> 4);
            Location ground;
            if (eldervale) {
                int originY = Math.min(center.getBlockY(), ELDERVALE_CEILING_Y - 2);
                ground = standOnEldervaleDeep(world, x, originY, z);
            } else if (preferCrypt && borderlands) {
                int originY = Math.min(center.getBlockY(), CRYPT_CEILING_Y - 2);
                ground = standOnCrypt(world, x, originY, z);
            } else if (borderlands) {
                ground = standOnSurface(world, x, z);
            } else {
                ground = standOn(world, x, anchor.getBlockY(), z);
            }
            if (ground == null) {
                continue;
            }
            if (eldervale) {
                if (ground.getY() > ELDERVALE_CEILING_Y) {
                    continue;
                }
            } else if (borderlands) {
                if (preferCrypt && ground.getY() > CRYPT_CEILING_Y) {
                    continue;
                }
                if (!preferCrypt && ground.getY() <= CRYPT_CEILING_Y) {
                    continue;
                }
                if (!preferCrypt && ground.getY() > SURFACE_MAX_Y) {
                    continue;
                }
                // Absolute taboo at Vex — never spawn in the 15m bubble.
                if (!preferCrypt && WildlifeLooks.nearVex(ground, VEX_TABOO_RADIUS_SQ)) {
                    continue;
                }
                // Soft entrance beyond taboo: T1-only ring capped.
                if (!preferCrypt && WildlifeLooks.nearVexStarter(ground)) {
                    if (vexHave >= VEX_SURFACE_CAP) {
                        continue;
                    }
                }
            }
            if (playerTooClose(world, ground, playerClearSq)) {
                continue;
            }
            if (BorderlandsRiteService.isMobSafeZone(ground)) {
                continue;
            }
            boolean crowded = false;
            for (Entity nearbyEntity : world.getNearbyEntities(ground, crowdRadius, 3, crowdRadius)) {
                if (nearbyEntity instanceof Monster) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                return ground;
            }
        }
        return null;
    }

    private int countNearVex(AnimalZone zone, World world) {
        int count = 0;
        for (LivingEntity entity : tagged(zone, world)) {
            Location at = entity.getLocation();
            if (at.getY() <= CRYPT_CEILING_Y) {
                continue;
            }
            if (WildlifeLooks.nearVex(at, VEX_TABOO_RADIUS_SQ)) {
                continue;
            }
            if (WildlifeLooks.nearVexStarter(at)) {
                count++;
            }
        }
        return count;
    }

    /** Surface stand on open sky — avoids dark cave/overhang clumps. Cap: Y ≤ 67. */
    private Location standOnSurface(World world, int x, int z) {
        int top = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int start = Math.min(top + 1, SURFACE_MAX_Y);
        int floor = Math.max(world.getMinHeight() + 2, CRYPT_CEILING_Y + 1);
        for (int y = start; y >= floor; y--) {
            Location ground = standAt(world, x, y, z);
            if (ground == null || ground.getY() <= CRYPT_CEILING_Y || ground.getY() > SURFACE_MAX_Y) {
                continue;
            }
            // Require open sky so packs don't pool in dark dips/caves.
            if (ground.getBlock().getLightFromSky() < 12
                    && ground.clone().add(0, 1, 0).getBlock().getLightFromSky() < 12) {
                continue;
            }
            return ground;
        }
        return null;
    }

    private boolean playerTooClose(World world, Location ground) {
        return playerTooClose(world, ground, 36.0);
    }

    private boolean playerTooClose(World world, Location ground, double clearSq) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(ground) <= clearSq) {
                return true;
            }
        }
        return false;
    }

    private Location standOn(World world, int x, int originY, int z) {
        for (int y = originY + 4; y >= originY - 8; y--) {
            Location ground = standAt(world, x, y, z);
            if (ground != null) {
                return ground;
            }
        }
        return null;
    }

    /** Search deeper for Eldervale miners (Y ≤ 24). */
    private Location standOnEldervaleDeep(World world, int x, int originY, int z) {
        int start = Math.min(Math.max(originY + 4, ELDERVALE_CEILING_Y), ELDERVALE_CEILING_Y);
        int minY = Math.max(world.getMinHeight() + 2, ELDERVALE_CEILING_Y - 64);
        for (int y = start; y >= minY; y--) {
            Location ground = standAt(world, x, y, z);
            if (ground != null && ground.getY() <= ELDERVALE_CEILING_Y) {
                return ground;
            }
        }
        return null;
    }

    /** Search deeper for Crypt floor (Y ≤ 55). */
    private Location standOnCrypt(World world, int x, int originY, int z) {
        int start = Math.min(originY + 2, CRYPT_CEILING_Y);
        int minY = Math.max(world.getMinHeight() + 2, CRYPT_CEILING_Y - 40);
        for (int y = start; y >= minY; y--) {
            Location ground = standAt(world, x, y, z);
            if (ground != null && ground.getY() <= CRYPT_CEILING_Y) {
                return ground;
            }
        }
        return null;
    }

    private Location standAt(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Block above = block.getRelative(0, 1, 0);
        Block head = block.getRelative(0, 2, 0);
        if (!block.getType().isSolid() || block.isLiquid()) {
            return null;
        }
        if (block.getType() == Material.OAK_LEAVES
                || block.getType().name().endsWith("_LEAVES")
                || block.getType() == Material.BARRIER) {
            return null;
        }
        if (!above.getType().isAir() || !head.getType().isAir()) {
            return null;
        }
        if (above.isLiquid() || head.isLiquid()) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    private boolean isDungeonWorld(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_")
                || name.startsWith("ae_dun")
                || name.equals("aether_test")
                || name.startsWith("aether_test_");
    }

    private AnimalZone nearest(Location location, double max) {
        AnimalZone best = null;
        double bestDist = max * max;
        for (AnimalZone zone : zones.values()) {
            if (!zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = zone.getX() - location.getX();
            double dy = zone.getY() - location.getY();
            double dz = zone.getZ() - location.getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best;
    }

    private void spawnMarker(AnimalZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        Location at = zone.center(world).add(0, 0.2, 0);
        ArmorStand stand = world.spawn(at, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSmall(true);
            String label = isBorderlands(zone)
                    ? "§6Borderlands §8(" + zone.getRadius() + "m)"
                    : isEldervale(zone)
                    ? "§bEldervale Deep §8(" + zone.getRadius() + "m · Y≤" + ELDERVALE_CEILING_Y + ")"
                    : "§cMob Zone §8(" + zone.getRadius() + "m)";
            spawned.setCustomName(label);
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(
                    ItemKeys.mobZone(),
                    PersistentDataType.STRING,
                    zone.getId().toString()
            );
        });
        zone.setMarkerId(stand.getUniqueId());
        StaffVisibility.apply(plugin, stand);
    }

    private void ensureMarker(AnimalZone zone) {
        if (zone.getMarkerId() != null) {
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, entity);
                return;
            }
        }
        spawnMarker(zone);
        save();
    }

    private void removeMarker(AnimalZone zone) {
        if (zone.getMarkerId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(zone.getMarkerId());
        if (entity != null) {
            entity.remove();
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                AnimalZone zone = new AnimalZone(
                        id,
                        section.getString("world", "world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        section.getInt("radius", RADIUS),
                        section.getString("profile")
                );
                String marker = section.getString("marker");
                if (marker != null && !marker.isBlank()) {
                    zone.setMarkerId(UUID.fromString(marker));
                }
                zones.put(id, zone);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("Loaded " + zones.size() + " mob zone(s).");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AnimalZone zone : zones.values()) {
            String path = "zones." + zone.getId();
            yaml.set(path + ".world", zone.getWorldName());
            yaml.set(path + ".x", zone.getX());
            yaml.set(path + ".y", zone.getY());
            yaml.set(path + ".z", zone.getZ());
            yaml.set(path + ".radius", zone.getRadius());
            if (zone.getProfile() != null && !zone.getProfile().isBlank()) {
                yaml.set(path + ".profile", zone.getProfile());
            }
            if (zone.getMarkerId() != null) {
                yaml.set(path + ".marker", zone.getMarkerId().toString());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save mob zones: " + exception.getMessage());
        }
    }
}
