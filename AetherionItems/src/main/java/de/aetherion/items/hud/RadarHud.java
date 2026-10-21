package de.aetherion.items.hud;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PartyAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.Vfx;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Round radar top right: quest target, quest NPCs, party, bosses, north. The map turns with the player's
 * view (forward is always up). Out-of-range quest, party and boss markers sit on the rim so they still
 * point the way; NPCs only show inside the range. The quest marker becomes an up or down arrow when the
 * target is well above or below the player. It drops under the vanilla effect icons while they show.
 */
public final class RadarHud {

    private static final int CENTRE = HudPen.RADAR_W / 2;
    /** Marker ring radius in px, and the blocks it stands for. */
    private static final int R = 23;
    private static final double RANGE = 48.0;
    private static final long SCAN_TICKS = 10L;
    /** Height difference (blocks) at which the quest marker shows an up or down arrow. */
    private static final double LEVEL_GAP = 4.0;

    private record Scan(long tick, List<Entity> bosses, List<Location> npcs, Location quest) {
    }

    private final Map<UUID, Scan> scans = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastQuest = new ConcurrentHashMap<>();

    /** Vanilla effect icon rows the radar has to clear: 0 none, 1 beneficial row, 2 the second row too. */
    static int effectRows(Player player) {
        int rows = 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (!effect.hasIcon()) {
                continue;
            }
            if (effect.getType().getEffectCategory() != PotionEffectType.Category.BENEFICIAL) {
                return 2;
            }
            rows = 1;
        }
        return rows;
    }

    void draw(HudPen pen, Player player, long tick, int state) {
        UUID id = player.getUniqueId();
        Scan scan = scans.get(id);
        if (scan == null || tick - scan.tick() >= SCAN_TICKS || tick < scan.tick()) {
            scan = scan(player, tick);
            scans.put(id, scan);
        }
        pen.at(0).glyph(HudPen.RADAR, HudPen.RADAR_W + 1, PanelPalette.radar(state, PanelPalette.PART_DISC, PanelPalette.WHITE));

        Location here = player.getLocation();
        double yaw = Math.toRadians(here.getYaw());
        for (Location npc : scan.npcs()) {
            mark(pen, state, here, yaw, npc, HudPen.Mark.NPC, false);
        }
        PartyAccess party = AetherServices.party();
        if (party != null && party.inParty(player)) {
            for (Player mate : party.onlineMembers(player)) {
                if (!mate.equals(player) && mate.getWorld().equals(player.getWorld())) {
                    mark(pen, state, here, yaw, mate.getLocation(), HudPen.Mark.PARTY, true);
                }
            }
        }
        if (scan.quest() != null) {
            double rise = scan.quest().getY() - here.getY();
            HudPen.Mark glyph = rise > LEVEL_GAP ? HudPen.Mark.QUEST_UP : rise < -LEVEL_GAP ? HudPen.Mark.QUEST_DOWN : HudPen.Mark.QUEST;
            mark(pen, state, here, yaw, scan.quest(), glyph, true);
        }
        for (Entity boss : scan.bosses()) {
            if (boss.isValid()) {
                mark(pen, state, here, yaw, boss.getLocation(), HudPen.Mark.BOSS, true);
            }
        }
        // North on the rim: direction (0, -1) in world x/z.
        Location north = here.clone().add(0, 0, -RANGE * 4);
        mark(pen, state, here, yaw, north, HudPen.Mark.NORTH, true);
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
    private static void mark(HudPen pen, int state, Location here, double yaw, Location at, HudPen.Mark glyph, boolean clamp) {
        int size = glyph.size;
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
        pen.at(x).mark(glyph, PanelPalette.radar(state, PanelPalette.PART_MARK, y + 64));
    }
}
