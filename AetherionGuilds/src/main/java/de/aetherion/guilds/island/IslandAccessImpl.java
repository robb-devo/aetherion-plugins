package de.aetherion.guilds.island;

import de.aetherion.core.api.IslandAccess;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.PersonalIslandService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Island homes for Core on the island backend: arrivals land on their island, deaths respawn there.
 * Read-only — never claims, never pastes (an island that is still rising returns null).
 */
public final class IslandAccessImpl implements IslandAccess {

    private final PersonalIslandService personal;
    private final GuildService guilds;
    private final IslandService islands;

    public IslandAccessImpl(PersonalIslandService personal, GuildService guilds, IslandService islands) {
        this.personal = personal;
        this.guilds = guilds;
        this.islands = islands;
    }

    @Override
    public Location home(UUID owner) {
        PersonalIsland island = personal == null ? null : personal.byOwner(owner);
        if (island == null) {
            return null;
        }
        // Still rising (claim interrupted by a restart, or the paste is mid-way): never hand out a spawn over
        // empty void — the arrival would fall, respawn here and fall again. Land them on the Isle Dock (null),
        // finish the paste, and carry them home the moment it stands. No soft-lock either way.
        if (!island.islandBuilt()) {
            personal.whenBuilt(island, () -> {
                Player player = Bukkit.getPlayer(owner);
                if (player != null && player.isOnline() && !personal.onOwnIsland(player, island)) {
                    personal.goHome(player);
                }
            });
            return null;
        }
        return personal.spawn(island);
    }

    @Override
    public Location guildHome(UUID member) {
        Guild guild = guilds == null ? null : guilds.byPlayer(member);
        if (guild == null || islands == null) {
            return null;
        }
        if (!guild.islandBuilt()) {
            // same as a personal island still rising: land on the dock (null), finish the paste, carry them over
            islands.whenBuilt(guild, () -> {
                Player player = Bukkit.getPlayer(member);
                if (player != null && player.isOnline() && !islands.onOwnIsland(player, guild)
                        && (personal == null || !personal.isPersonalWorld(player.getWorld()))) {
                    guilds.goHome(player);
                }
            });
            return null;
        }
        return islands.spawn(guild);
    }

    @Override
    public boolean hasIsland(UUID owner) {
        return personal != null && personal.byOwner(owner) != null;
    }

    @Override
    public boolean isIslandWorld(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return (personal != null && personal.isPersonalWorld(location.getWorld()))
                || (islands != null && islands.isGuildWorld(location.getWorld()));
    }
}
