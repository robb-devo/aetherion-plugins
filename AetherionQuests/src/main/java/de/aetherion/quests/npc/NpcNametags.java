package de.aetherion.quests.npc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Hides the vanilla entity nametag so quest NPCs keep a single
 * TextDisplay hologram. Looking at a villager still shows the
 * vanilla name unless the entity is on a NEVER team.
 */
public final class NpcNametags {

    private static final String TEAM = "ae_hide_npc";

    private NpcNametags() {
    }

    public static void hide(Entity entity) {
        if (entity == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM);
        if (team == null) {
            team = board.registerNewTeam(TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
        } else if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.NEVER) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        try {
            team.addEntity(entity);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
    }
}
