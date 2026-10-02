package de.aetherion.guilds.island;

import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * SkyBlock-style "buy land": islands own 16x16 parcels on a grid around the origin (parcel 0,0 covers origin
 * -8..+7). Buying a parcel next to your land extends where you may build, and (optionally) raises new ground
 * there, layer by layer, in the island's own look. The classic tier radius square stays valid on top.
 */
public final class LandService {

    public static final int SIZE = 16;
    /** Parcels reach up to 4 away from the origin parcel (edge at origin ±71; wipe clears ±80). */
    public static final int RING = 4;

    private final JavaPlugin plugin;
    private final HostService hosts;

    public LandService(JavaPlugin plugin, HostService hosts) {
        this.plugin = plugin;
        this.hosts = hosts;
    }

    // ------------------------------------------------------------------------------------------------
    // parcel math
    // ------------------------------------------------------------------------------------------------

    public static int parcelOf(int rel) {
        return Math.floorDiv(rel + 8, SIZE);
    }

    public static long key(int px, int pz) {
        return ((long) px << 32) ^ (pz & 0xFFFFFFFFL);
    }

    public static int px(long key) {
        return (int) (key >> 32);
    }

    public static int pz(long key) {
        return (int) key;
    }

    public static int minRel(int p) {
        return p * SIZE - 8;
    }

    public static int maxRel(int p) {
        return p * SIZE + 7;
    }

    public static Set<Long> initialParcels() {
        Set<Long> out = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                out.add(key(x, z));
            }
        }
        return out;
    }

    /** Parcels that overlap the classic square by at least half a parcel on both axes. */
    public static void seedLegacy(Set<Long> parcels, int radius) {
        for (int x = -RING; x <= RING; x++) {
            for (int z = -RING; z <= RING; z++) {
                if (overlap(x, radius) >= 8 && overlap(z, radius) >= 8) {
                    parcels.add(key(x, z));
                }
            }
        }
    }

    private static int overlap(int p, int radius) {
        int lo = Math.max(minRel(p), -radius);
        int hi = Math.min(maxRel(p), radius);
        return Math.max(0, hi - lo + 1);
    }

    public static boolean inZone(int originX, int originZ, int radius, Set<Long> parcels, int x, int z) {
        int rx = x - originX;
        int rz = z - originZ;
        if (Math.abs(rx) <= radius && Math.abs(rz) <= radius) {
            return true;
        }
        return parcels.contains(key(parcelOf(rx), parcelOf(rz)));
    }

    public static boolean adjacentToOwned(Set<Long> parcels, int px, int pz) {
        return parcels.contains(key(px + 1, pz)) || parcels.contains(key(px - 1, pz))
                || parcels.contains(key(px, pz + 1)) || parcels.contains(key(px, pz - 1));
    }

    // ------------------------------------------------------------------------------------------------
    // buying
    // ------------------------------------------------------------------------------------------------

    public long price(IslandHost host) {
        int owned = hosts.parcels(host).size();
        long base = host != null && host.isGuild()
                ? Math.max(1L, plugin.getConfig().getLong("land.guild-parcel-base", 10_000L))
                : Math.max(1L, plugin.getConfig().getLong("land.parcel-base", 2_500L));
        return base * Math.max(1, owned - 8);
    }

    public enum Status {
        OWNED,
        BUYABLE,
        OUT_OF_REACH,
        TOO_FAR
    }

    public Status status(IslandHost host, int px, int pz) {
        if (Math.abs(px) > RING || Math.abs(pz) > RING) {
            return Status.TOO_FAR;
        }
        Set<Long> parcels = hosts.parcels(host);
        if (parcels.contains(key(px, pz))) {
            return Status.OWNED;
        }
        return adjacentToOwned(parcels, px, pz) ? Status.BUYABLE : Status.OUT_OF_REACH;
    }

    public boolean buy(Player player, IslandHost host, int px, int pz, boolean raiseLand) {
        if (!hosts.canAdmin(player, host)) {
            player.sendMessage(hosts.rankHint(host, "buy land (Mayor+)"));
            return false;
        }
        Status status = status(host, px, pz);
        if (status == Status.OWNED) {
            player.sendMessage("§7You already own that land.");
            return false;
        }
        if (status != Status.BUYABLE) {
            player.sendMessage("§cLand must touch land you already own.");
            return false;
        }
        long cost = price(host);
        if (!hosts.charge(player, host, cost)) {
            return false;
        }
        hosts.parcels(host).add(key(px, pz));
        hosts.save(host);
        player.sendMessage("§a✦ Land bought §8(" + px + ", " + pz + ")§a for §6" + GuildFormat.compact(cost)
                + " " + hosts.fundsLabel(host) + "§a." + (raiseLand ? " §7The ground is rising…" : ""));
        World world = hosts.world(host);
        if (world != null) {
            world.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        }
        if (raiseLand) {
            raise(host, px, pz, player);
        } else {
            flashParcel(player, host, px, pz, Color.fromRGB(120, 230, 120));
        }
        for (Player member : hosts.onlineMembers(host)) {
            if (!member.equals(player) && host.isGuild()) {
                member.sendMessage("§a" + player.getName() + " §7bought new guild land §8(" + px + ", " + pz + ")§7.");
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // land rising
    // ------------------------------------------------------------------------------------------------

    /** Raises a lens of new ground in the parcel (air only), bottom layer first, over ~2 seconds. */
    public void raise(IslandHost host, int px, int pz, Player viewer) {
        World world = hosts.world(host);
        if (world == null) {
            return;
        }
        int ox = hosts.originX(host);
        int oz = hosts.originZ(host);
        int y0 = HostService.SURFACE_Y;
        StarterLayout.Land land = hosts.land(host);
        long seed = host.id().getLeastSignificantBits() ^ key(px, pz);
        Random rng = new Random(seed);
        int x1 = ox + minRel(px);
        int z1 = oz + minRel(pz);
        List<int[]> columns = new ArrayList<>();
        int deepest = 0;
        for (int dx = 0; dx < SIZE; dx++) {
            for (int dz = 0; dz < SIZE; dz++) {
                int x = x1 + dx;
                int z = z1 + dz;
                if (!world.getBlockAt(x, y0, z).getType().isAir()) {
                    continue;
                }
                double ex = Math.min(dx, SIZE - 1 - dx) / 7.5;
                double ez = Math.min(dz, SIZE - 1 - dz) / 7.5;
                double edge = Math.min(1.0, Math.min(ex, ez) * 1.2 + 0.15);
                boolean touching = touchesLand(world, x, y0, z);
                int depth = 2 + (int) Math.round(edge * 6) + rng.nextInt(3) + (touching ? 1 : 0);
                columns.add(new int[]{x, z, depth});
                deepest = Math.max(deepest, depth);
            }
        }
        if (columns.isEmpty()) {
            if (viewer != null) {
                flashParcel(viewer, host, px, pz, Color.fromRGB(120, 230, 120));
            }
            return;
        }
        final int maxDepth = deepest;
        new BukkitRunnable() {
            int layer = maxDepth;

            @Override
            public void run() {
                if (layer < 0) {
                    decorate(world, columns, y0, land, rng);
                    world.playSound(new Location(world, x1 + 8, y0 + 1, z1 + 8), Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.7f);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, x1 + 8, y0 + 1.5, z1 + 8, 40, 5, 0.4, 5, 0);
                    if (viewer != null && viewer.isOnline()) {
                        flashParcel(viewer, host, px, pz, Color.fromRGB(120, 230, 120));
                    }
                    cancel();
                    return;
                }
                int y = y0 - layer;
                int placed = 0;
                for (int[] column : columns) {
                    if (column[2] < layer) {
                        continue;
                    }
                    Block block = world.getBlockAt(column[0], y, column[1]);
                    if (!block.getType().isAir()) {
                        continue;
                    }
                    block.setType(material(land, layer, rng), false);
                    placed++;
                    if (placed % 24 == 1) {
                        world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.2, 0.5), 3, 0.3, 0.1, 0.3,
                                0.02, block.getBlockData());
                    }
                }
                if (placed > 0) {
                    world.playSound(new Location(world, x1 + 8, y, z1 + 8),
                            layer == 0 ? Sound.BLOCK_GRASS_PLACE : Sound.BLOCK_GRAVEL_PLACE, 0.8f, 0.6f + layer * 0.05f);
                }
                layer--;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private static boolean touchesLand(World world, int x, int y, int z) {
        return !world.getBlockAt(x + 1, y, z).getType().isAir() || !world.getBlockAt(x - 1, y, z).getType().isAir()
                || !world.getBlockAt(x, y, z + 1).getType().isAir() || !world.getBlockAt(x, y, z - 1).getType().isAir();
    }

    private static Material material(StarterLayout.Land land, int layer, Random rng) {
        if (layer == 0) {
            return switch (land) {
                case SANDY -> Material.SAND;
                case ROCKY -> pickOf(rng, Material.STONE, Material.ANDESITE, Material.GRAVEL, Material.COARSE_DIRT,
                        Material.GRASS_BLOCK);
                default -> rng.nextInt(7) == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
            };
        }
        if (layer <= 2) {
            return switch (land) {
                case SANDY -> Material.SANDSTONE;
                case ROCKY -> pickOf(rng, Material.STONE, Material.GRAVEL, Material.DIRT);
                default -> Material.DIRT;
            };
        }
        return pickOf(rng, Material.STONE, Material.STONE, Material.ANDESITE, Material.TUFF, Material.COBBLESTONE);
    }

    private static Material pickOf(Random rng, Material... options) {
        return options[rng.nextInt(options.length)];
    }

    private static void decorate(World world, List<int[]> columns, int y0, StarterLayout.Land land, Random rng) {
        for (int[] column : columns) {
            Block top = world.getBlockAt(column[0], y0, column[1]);
            Block above = top.getRelative(0, 1, 0);
            if (!above.getType().isAir()) {
                continue;
            }
            if (top.getType() == Material.GRASS_BLOCK || top.getType() == Material.MOSS_BLOCK) {
                int roll = rng.nextInt(100);
                if (roll < 22) {
                    above.setType(Material.SHORT_GRASS, false);
                } else if (roll < 25) {
                    above.setType(rng.nextBoolean() ? Material.DANDELION : Material.AZURE_BLUET, false);
                }
            } else if (land == StarterLayout.Land.SANDY && top.getType() == Material.SAND && rng.nextInt(40) == 0) {
                above.setType(Material.DEAD_BUSH, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // borders
    // ------------------------------------------------------------------------------------------------

    public void flashParcel(Player viewer, IslandHost host, int px, int pz, Color color) {
        int ox = hosts.originX(host) + minRel(px);
        int oz = hosts.originZ(host) + minRel(pz);
        new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!viewer.isOnline() || runs++ > 12) {
                    cancel();
                    return;
                }
                Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
                double y = HostService.SURFACE_Y + 1.2;
                for (int i = 0; i <= SIZE; i += 1) {
                    viewer.spawnParticle(Particle.DUST, ox + i, y, oz, 1, 0, 0, 0, 0, dust);
                    viewer.spawnParticle(Particle.DUST, ox + i, y, oz + SIZE, 1, 0, 0, 0, 0, dust);
                    viewer.spawnParticle(Particle.DUST, ox, y, oz + i, 1, 0, 0, 0, 0, dust);
                    viewer.spawnParticle(Particle.DUST, ox + SIZE, y, oz + i, 1, 0, 0, 0, 0, dust);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** Shows the whole buildable outline (tier square + parcels) for a few seconds. */
    public void flashBorder(Player viewer, IslandHost host) {
        Set<Long> parcels = hosts.parcels(host);
        int ox = hosts.originX(host);
        int oz = hosts.originZ(host);
        int radius = hosts.legacyRadius(host);
        new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!viewer.isOnline() || runs++ > 12) {
                    cancel();
                    return;
                }
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 214, 90), 1.3f);
                double y = viewer.getLocation().getY() + 0.2;
                int reach = RING * SIZE + 8;
                for (int rx = -reach; rx <= reach; rx += 2) {
                    for (int rz = -reach; rz <= reach; rz += 2) {
                        boolean inside = inZone(0, 0, radius, parcels, rx, rz);
                        if (!inside) {
                            continue;
                        }
                        boolean edge = !inZone(0, 0, radius, parcels, rx + 2, rz)
                                || !inZone(0, 0, radius, parcels, rx - 2, rz)
                                || !inZone(0, 0, radius, parcels, rx, rz + 2)
                                || !inZone(0, 0, radius, parcels, rx, rz - 2);
                        if (edge) {
                            viewer.spawnParticle(Particle.DUST, ox + rx + 0.5, y, oz + rz + 0.5, 1, 0, 0, 0, 0, dust);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void sendBorderMessage(Player player) {
        player.sendMessage("§eYour build border is shown in gold for a few seconds.");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }
}
