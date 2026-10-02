package de.aetherion.guilds.island;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.model.IslandBiome;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One view over personal and guild islands, so structures, belts, land and projects are written once.
 * Permission mapping (guild ranks unchanged):
 * <ul>
 *   <li>build: owner / Soldier+ (same as block building)</li>
 *   <li>place structures and belts: owner / Soldier+ (same as placing quarries)</li>
 *   <li>admin (land, tier, start projects): owner / Mayor+ (same as island upgrades)</li>
 *   <li>collect from storage, contribute: owner / Footman+ (same as quarry collect)</li>
 * </ul>
 */
public final class HostService {

    public static final int SURFACE_Y = 64;

    private final PersonalIslandService personal;
    private final GuildService guilds;
    private final IslandService islands;

    public HostService(PersonalIslandService personal, GuildService guilds, IslandService islands) {
        this.personal = personal;
        this.guilds = guilds;
        this.islands = islands;
    }

    public PersonalIslandService personalService() {
        return personal;
    }

    public GuildService guildService() {
        return guilds;
    }

    public IslandService guildIslands() {
        return islands;
    }

    public World world(IslandHost host) {
        if (host == null) {
            return null;
        }
        return host.isGuild() ? islands.world() : personal.world();
    }

    public boolean isIslandWorld(World world) {
        return world != null && (personal.isPersonalWorld(world) || islands.isGuildWorld(world));
    }

    public boolean exists(IslandHost host) {
        return host != null && (host.isGuild() ? guilds.byId(host.id()) != null : personal.byOwner(host.id()) != null);
    }

    public PersonalIsland island(IslandHost host) {
        return host == null || host.isGuild() ? null : personal.byOwner(host.id());
    }

    public Guild guild(IslandHost host) {
        return host == null || !host.isGuild() ? null : guilds.byId(host.id());
    }

    public int originX(IslandHost host) {
        if (host == null) {
            return 0;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? 0 : islands.originX(guild);
        }
        PersonalIsland island = island(host);
        return island == null ? 0 : personal.originX(island);
    }

    public int originZ(IslandHost host) {
        return 0;
    }

    public IslandHost at(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        if (personal.isPersonalWorld(world)) {
            PersonalIsland island = personal.byPlot(personal.plotAt(location));
            return island == null ? null : IslandHost.personal(island.ownerId());
        }
        if (islands.isGuildWorld(world)) {
            Guild guild = guilds.byPlot(islands.plotAt(location));
            return guild == null ? null : IslandHost.guild(guild.id());
        }
        return null;
    }

    public IslandHost at(World world, int x, int z) {
        return world == null ? null : at(new Location(world, x + 0.5, SURFACE_Y, z + 0.5));
    }

    public StarterLayout starter(IslandHost host) {
        if (host == null) {
            return null;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? null : StarterLayout.byId(guild.starter());
        }
        PersonalIsland island = island(host);
        return island == null ? null : StarterLayout.byId(island.starter());
    }

    public StarterLayout.Land land(IslandHost host) {
        StarterLayout starter = starter(host);
        if (starter != null) {
            return starter.land();
        }
        return StarterLayout.landFor(biome(host));
    }

    public IslandBiome biome(IslandHost host) {
        PersonalIsland island = island(host);
        return island == null ? IslandBiome.PLAINS : island.biome();
    }

    public int tier(IslandHost host) {
        if (host == null) {
            return 1;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? 1 : guild.islandLevel();
        }
        PersonalIsland island = island(host);
        return island == null ? 1 : island.islandLevel();
    }

    /** The classic square build radius (tier based) that every island keeps. */
    public int legacyRadius(IslandHost host) {
        if (host == null) {
            return 0;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? 0 : islands.buildRadius(guild);
        }
        PersonalIsland island = island(host);
        return island == null ? 0 : personal.buildRadius(island);
    }

    /** Owned parcels; seeded (3x3 + whatever the classic radius covers) the first time they are needed. */
    public Set<Long> parcels(IslandHost host) {
        Set<Long> parcels;
        if (host != null && host.isGuild()) {
            Guild guild = guild(host);
            if (guild == null) {
                return Set.of();
            }
            parcels = guild.parcels();
        } else {
            PersonalIsland island = island(host);
            if (island == null) {
                return Set.of();
            }
            parcels = island.parcels();
        }
        if (parcels.isEmpty()) {
            parcels.addAll(LandService.initialParcels());
            LandService.seedLegacy(parcels, legacyRadius(host));
            save(host);
        }
        return parcels;
    }

    public boolean inBuildZone(IslandHost host, int x, int z) {
        if (host == null) {
            return false;
        }
        return LandService.inZone(originX(host), originZ(host), legacyRadius(host), parcels(host), x, z);
    }

    public boolean isAdminBypass(Player player) {
        return player != null && player.getGameMode() == GameMode.CREATIVE && player.hasPermission("aetherion.guild.admin");
    }

    private GuildRank rank(Player player, IslandHost host) {
        Guild guild = guild(host);
        return guild == null || player == null ? null : guild.rank(player.getUniqueId());
    }

    public boolean isMember(Player player, IslandHost host) {
        if (player == null || host == null) {
            return false;
        }
        if (host.isGuild()) {
            return rank(player, host) != null;
        }
        return host.id().equals(player.getUniqueId());
    }

    public boolean canBuild(Player player, IslandHost host) {
        if (isAdminBypass(player)) {
            return true;
        }
        if (host == null || player == null) {
            return false;
        }
        if (host.isGuild()) {
            GuildRank rank = rank(player, host);
            return rank != null && rank.canBuild();
        }
        return host.id().equals(player.getUniqueId());
    }

    public boolean canPlace(Player player, IslandHost host) {
        if (isAdminBypass(player)) {
            return true;
        }
        if (host == null || player == null) {
            return false;
        }
        if (host.isGuild()) {
            GuildRank rank = rank(player, host);
            return rank != null && rank.canPlaceQuarry();
        }
        return host.id().equals(player.getUniqueId());
    }

    public boolean canAdmin(Player player, IslandHost host) {
        if (isAdminBypass(player)) {
            return true;
        }
        if (host == null || player == null) {
            return false;
        }
        if (host.isGuild()) {
            GuildRank rank = rank(player, host);
            return rank != null && rank.canUpgradeIsland();
        }
        return host.id().equals(player.getUniqueId());
    }

    public boolean canCollect(Player player, IslandHost host) {
        if (host == null || player == null) {
            return false;
        }
        if (host.isGuild()) {
            GuildRank rank = rank(player, host);
            return rank != null && rank.canCollectQuarry();
        }
        return host.id().equals(player.getUniqueId());
    }

    public String rankHint(IslandHost host, String action) {
        return host != null && host.isGuild()
                ? "§cYour guild rank cannot " + action + "."
                : "§cOnly the island owner can " + action + ".";
    }

    public List<QuarryMinion> minions(IslandHost host) {
        if (host == null) {
            return List.of();
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? List.of() : guild.minions();
        }
        PersonalIsland island = island(host);
        return island == null ? List.of() : island.minions();
    }

    public QuarryMinion minion(IslandHost host, UUID minionId) {
        if (minionId == null) {
            return null;
        }
        for (QuarryMinion minion : minions(host)) {
            if (minion.id().equals(minionId)) {
                return minion;
            }
        }
        return null;
    }

    public void save(IslandHost host) {
        if (host == null) {
            return;
        }
        if (host.isGuild()) {
            guilds.save();
        } else {
            personal.save();
        }
    }

    public Collection<IslandHost> all() {
        List<IslandHost> out = new ArrayList<>();
        for (PersonalIsland island : personal.all()) {
            out.add(IslandHost.personal(island.ownerId()));
        }
        for (Guild guild : guilds.all()) {
            out.add(IslandHost.guild(guild.id()));
        }
        return out;
    }

    public List<Player> onlineMembers(IslandHost host) {
        List<Player> out = new ArrayList<>();
        if (host == null) {
            return out;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            if (guild != null) {
                for (UUID id : guild.members().keySet()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && player.isOnline()) {
                        out.add(player);
                    }
                }
            }
            return out;
        }
        Player owner = Bukkit.getPlayer(host.id());
        if (owner != null && owner.isOnline()) {
            out.add(owner);
        }
        return out;
    }

    public String title(IslandHost host) {
        if (host == null) {
            return "Island";
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? "Guild Island" : GuildFormat.islandTitle(guild.name());
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(host.id());
        return (owner.getName() == null ? "Your" : owner.getName() + "'s") + " Island";
    }

    public Location spawn(IslandHost host) {
        if (host == null) {
            return null;
        }
        if (host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? null : islands.spawn(guild);
        }
        PersonalIsland island = island(host);
        return island == null ? null : personal.spawn(island);
    }

    // ------------------------------------------------------------------------------------------------
    // money: personal = the player's coins, guild = the guild bank
    // ------------------------------------------------------------------------------------------------

    public String fundsLabel(IslandHost host) {
        return host != null && host.isGuild() ? "guild bank" : "coins";
    }

    public long funds(Player player, IslandHost host) {
        if (host != null && host.isGuild()) {
            Guild guild = guild(host);
            return guild == null ? 0L : guild.bankCoins();
        }
        if (de.aetherion.core.api.AetherServices.coins() == null) {
            return Long.MAX_VALUE;
        }
        return AetherionItemsAccess.coins(player);
    }

    /** Takes coins; free when the economy plugin is not loaded. Messages the player on failure. */
    public boolean charge(Player player, IslandHost host, long coins) {
        if (coins <= 0L) {
            return true;
        }
        if (host != null && host.isGuild()) {
            Guild guild = guild(host);
            if (guild == null || guild.bankCoins() < coins) {
                player.sendMessage("§cThe guild bank needs §6" + GuildFormat.compact(coins) + " coins§c. Deposit via §f/guild§c → Bank.");
                return false;
            }
            guild.setBankCoins(guild.bankCoins() - coins);
            guilds.save();
            return true;
        }
        if (de.aetherion.core.api.AetherServices.coins() == null) {
            return true;
        }
        if (AetherionItemsAccess.coins(player) < coins) {
            player.sendMessage("§cYou need §6" + GuildFormat.compact(coins) + " coins§c.");
            return false;
        }
        if (!AetherionItemsAccess.takeCoins(player, coins)) {
            player.sendMessage("§cCould not take coins.");
            return false;
        }
        return true;
    }

    public void refund(Player player, IslandHost host, long coins) {
        if (coins <= 0L) {
            return;
        }
        if (host != null && host.isGuild()) {
            Guild guild = guild(host);
            if (guild != null) {
                guild.setBankCoins(guild.bankCoins() + coins);
                guilds.save();
            }
            return;
        }
        AetherionItemsAccess.addCoins(player, coins);
    }
}
