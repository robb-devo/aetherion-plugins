package de.aetherion.items.hud;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PartyAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.Vfx;
import de.aetherion.items.combat.HudGlyphs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Round radar on the right, below the potion icons: quest target, quest NPCs, party, bosses, north.
 * The map turns with the player's view (forward is always up). Out-of-range quest, party and boss
 * markers sit on the rim so they still point the way; NPCs only show inside the range.
 */
public final class RadarHud {

    private static final int DISC = 0xE420;
    private static final int DOT_QUEST = 0xE421;
    private static final int DOT_NPC = 0xE422;
    private static final int DOT_PARTY = 0xE423;
    private static final int DOT_BOSS = 0xE424;
    private static final int DOT_NORTH = 0xE425;
    private static final int SIZE = 64;
    private static final int CENTRE = 32;
    /** Marker ring radius in px, and the blocks it stands for. */
    private static final int R = 25;
    private static final double RANGE = 48.0;
    private static final long SCAN_TICKS = 10L;

    private record Scan(long tick, List<Entity> bosses, List<Location> npcs, Location quest) {
    }

    private final Map<UUID, Scan> scans = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastQuest = new ConcurrentHashMap<>();

    void draw(HudGlyphs.Pen pen, Player player, long tick) {
        UUID id = player.getUniqueId();
        Scan scan = scans.get(id);
        if (scan == null || tick - scan.tick() >= SCAN_TICKS || tick < scan.tick()) {
            scan = scan(player, tick);
            scans.put(id, scan);
        }
        pen.at(0).raw(StatusPanel.FONT, DISC, SIZE + 1, PanelPalette.radarBody(HudGlyphs.WHITE));

        Location here = player.getLocation();
        double yaw = Math.toRadians(here.getYaw());
        for (Location npc : scan.npcs()) {
            mark(pen, here, yaw, npc, DOT_NPC, 5, false);
        }
        PartyAccess party = AetherServices.party();
        if (party != null && party.inParty(player)) {
            for (Player mate : party.onlineMembers(player)) {
                if (!mate.equals(player) && mate.getWorld().equals(player.getWorld())) {
                    mark(pen, here, yaw, mate.getLocation(), DOT_PARTY, 5, true);
                }
            }
        }
        if (scan.quest() != null) {
            mark(pen, here, yaw, scan.quest(), DOT_QUEST, 7, true);
        }
        for (Entity boss : scan.bosses()) {
            if (boss.isValid()) {
                mark(pen, here, yaw, boss.getLocation(), DOT_BOSS, 7, true);
            }
        }
        // North on the rim: direction (0, -1) in world x/z.
        Location north = here.clone().add(0, 0, -RANGE * 4);
        mark(pen, here, yaw, north, DOT_NORTH, 5, true);
    }

    void forget(UUID playerId) {
        scans.remove(playerId);
        lastQuest.remove(playerId);
    }

    private Scan scan(Player player, long tick) {
        List<Entity> bosses = new ArrayList<>();
        for (Entity e : player.getNearbyEntities(RANGE * 2, 48, RANGE * 2)) {
            if (e instanceof LivingEntity && e.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)) {
                bosses.add(e);
            }
        }
        List<Location> npcs = List.of();
        Location quest = null;
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            try {
                npcs = quests.npcsNear(player, RANGE);
                Location target = quests.questTarget(player);
                if (target != null && target.getWorld() != null && target.getWorld().equals(player.getWorld())) {
                    quest = target;
                }
            } catch (RuntimeException | AbstractMethodError ignored) {
                // An older Quests jar: radar keeps party + bosses.
            }
        }
        pingOnNewTarget(player, quest);
        return new Scan(tick, bosses, npcs, quest);
    }

    /** A soft ping when the quest marker jumps to a new spot. */
    private void pingOnNewTarget(Player player, Location quest) {
        UUID id = player.getUniqueId();
        Location before = quest == null ? lastQuest.remove(id) : lastQuest.put(id, quest.clone());
        if (quest != null && before != null && (before.getWorld() != quest.getWorld() || before.distanceSquared(quest) > 9.0)) {
            Vfx.sound(player, Vfx.UI_QUEST_PING, 0.6f, 1.0f);
        }
    }

    /** Projects {@code at} into the disc and draws {@code glyph} centred on it. */
    private static void mark(HudGlyphs.Pen pen, Location here, double yaw, Location at, int glyph, int size, boolean clamp) {
        double dx = at.getX() - here.getX();
        double dz = at.getZ() - here.getZ();
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // forward = (-sin, cos), right = (-cos, -sin); screen y grows downward.
        double sx = -dx * cos - dz * sin;
        double sy = dx * sin - dz * cos;
        double scale = R / RANGE;
        double px = sx * scale;
        double py = sy * scale;
        double len = Math.hypot(px, py);
        if (len > R) {
            if (!clamp) {
                return;
            }
            px *= R / len;
            py *= R / len;
        }
        int x = CENTRE + (int) Math.round(px) - size / 2;
        int y = CENTRE + (int) Math.round(py) - size / 2;
        pen.at(x).raw(StatusPanel.FONT, glyph, size + 1, PanelPalette.radarMark(y));
    }
}
