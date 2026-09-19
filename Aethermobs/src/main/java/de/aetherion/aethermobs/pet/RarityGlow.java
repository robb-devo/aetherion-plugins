package de.aetherion.aethermobs.pet;

import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Colored glow outline by pet rarity (scoreboard teams).
 *
 * <p>Paper stores entity team members as {@code $uuid}. Plain UUID strings
 * never match on remove, so stale members used to pile up in
 * {@code scoreboard.dat} until join packets exceeded the size limit.
 */
public final class RarityGlow {

    private static final String TEAM_PREFIX = "ae_pet_r_";

    private RarityGlow() {
    }

    public static void apply(Entity entity, Rarity rarity) {
        if (entity == null) {
            return;
        }
        Team team = teamFor(rarity == null ? Rarity.COMMON : rarity);
        clear(entity);
        addEntityEntry(team, entity);
    }

    public static void clear(Entity entity) {
        if (entity == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                removeEntityEntry(team, entity);
            }
        }
    }

    /**
     * Drop every member from rarity-glow teams. Safe to run on enable/disable
     * so {@code scoreboard.dat} cannot accumulate hundreds of thousands of
     * despawned pet UUIDs again.
     */
    public static void purgeAll() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith(TEAM_PREFIX)) {
                continue;
            }
            for (String entry : team.getEntries()) {
                team.removeEntry(entry);
            }
        }
    }

    private static void addEntityEntry(Team team, Entity entity) {
        try {
            team.addEntity(entity);
            return;
        } catch (Throwable ignored) {
        }
        String id = entity.getUniqueId().toString();
        if (!team.hasEntry(id)) {
            team.addEntry(id);
        }
        String dollar = "$" + id;
        if (!team.hasEntry(dollar)) {
            team.addEntry(dollar);
        }
    }

    private static void removeEntityEntry(Team team, Entity entity) {
        try {
            team.removeEntity(entity);
        } catch (Throwable ignored) {
        }
        String id = entity.getUniqueId().toString();
        team.removeEntry(id);
        team.removeEntry("$" + id);
    }

    private static Team teamFor(Rarity rarity) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = TEAM_PREFIX + rarity.name().toLowerCase();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.color(colorOf(rarity));
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
        }
        return team;
    }

    private static NamedTextColor colorOf(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.DARK_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
            case MYTHIC -> NamedTextColor.LIGHT_PURPLE;
            case AETHERED -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.WHITE;
        };
    }
}
