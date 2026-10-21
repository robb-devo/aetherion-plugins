package de.aetherion.items.hud;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ForageAccess;
import de.aetherion.core.api.ForageWeatherView;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.combat.AbilityCooldownHud;
import de.aetherion.items.combat.HudGlyphs;
import de.aetherion.items.combat.PlayerHud;
import de.aetherion.items.combat.StaminaHud;
import de.aetherion.items.skill.AetherionLevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * The HUD: a status frame top left (level crest, HP, stamina, XP, DEF and purse, cooldowns) and the radar
 * top right with the place name and forage weather under it. Painted into {@link HudCarrier}.
 *
 * <p>Geometry is in art pixels and mirrors {@code tools/gen_carrier.py}: y comes from the colour code
 * ({@link PanelPalette#frame}: 2 px steps) plus the glyph's phase (0 or 1 px), x from the pen. Rows never
 * move: stamina shows full when idle and DEF shows 0, so nothing jumps while you play.
 */
public final class StatusPanel implements PlayerHud.PanelPainter {

    private static final int CREST_X = 4;
    private static final int CREST_Y = 4;
    private static final int BAR_X = 33;
    private static final int SOCKET = 9;
    private static final int HP_Y = 4;
    private static final int ST_Y = 16;
    private static final int XP_Y = 24;
    private static final int INFO_Y = 32;
    private static final int CD_X = 4;
    private static final int CD_Y = 44;
    private static final int CD_STEP = 12;
    private static final int MAX_CDS = 3;
    /** Fill widths: HP and cooldowns leave room for their icon socket. */
    private static final int HP_WELL = HudPen.Frame.HP.width - 2 - SOCKET;
    private static final int BAR_WELL = HudPen.Frame.STAMINA.width - 2;
    private static final int CD_WELL = HudPen.Frame.CD.width - 2 - SOCKET;
    /** Place plate: widest name it shows (wider ones are cut). */
    private static final int PLACE_MAX = 150;

    private final AetherionItems plugin;
    private final RadarHud radar;
    private final HudCarrier carrier;

    public StatusPanel(AetherionItems plugin, RadarHud radar, HudCarrier carrier) {
        this.plugin = plugin;
        this.radar = radar;
        this.carrier = carrier;
    }

    @Override
    public void paint(Player player, PlayerHud.Snapshot hud, long tick) {
        HudPen pen = new HudPen();
        int cds = Math.min(MAX_CDS, hud.cooldowns().size());
        pen.at(0).glyph(HudPen.PLATE + cds, HudPen.PLATE_W + 1, PanelPalette.frame(0, PanelPalette.WHITE));
        level(pen, player);
        health(pen, hud);
        stamina(pen, hud.stamina());
        info(pen, player, hud.defense());
        for (int i = 0; i < cds; i++) {
            cooldown(pen, CD_Y + CD_STEP * i, hud.cooldowns().get(i));
        }
        if (radar != null && plugin.getConfig().getBoolean("hud.radar", true)) {
            int state = RadarHud.effectRows(player);
            radar.draw(pen, player, tick, state);
            place(pen, player, state);
        }
        carrier.show(player, pen.build());
    }

    @Override
    public void clear(Player player) {
        carrier.hide(player);
    }

    @Override
    public void blank(Player player) {
        carrier.show(player, Component.empty());
    }

    @Override
    public void forget(UUID playerId) {
        carrier.forget(playerId);
        if (radar != null) {
            radar.forget(playerId);
        }
    }

    /* ------------------------------------------------------------------ rows */

    private void level(HudPen pen, Player player) {
        int level = 1;
        double fraction = 0.0;
        if (plugin.getSkills() != null) {
            level = plugin.getSkills().accountLevel(player);
            long xp = plugin.getSkills().accountXp(player);
            long need = AetherionLevel.xpToNext(level);
            fraction = need <= 0L ? 1.0 : Math.max(0.0, Math.min(1.0, AetherionLevel.intoLevel(xp) / (double) need));
        }
        pen.at(CREST_X).glyph(HudPen.CREST, HudPen.CREST_W + 1, at(CREST_Y, PanelPalette.WHITE));
        String text = Integer.toString(level);
        int big = HudPen.bigWidth(text);
        if (big <= HudPen.CREST_W - 2) {
            pen.at(CREST_X + (HudPen.CREST_W - big) / 2 + 1).big(text, 0, at(12, PanelPalette.LEVEL));
        } else {
            int small = HudPen.smallWidth(text);
            pen.at(CREST_X + (HudPen.CREST_W - small) / 2 + 1).small(text, 0, at(14, PanelPalette.LEVEL));
        }
        pen.at(BAR_X).frame(HudPen.Frame.XP, at(XP_Y, PanelPalette.WHITE));
        int px = (int) Math.floor(fraction * BAR_WELL);
        if (px > 0) {
            pen.at(BAR_X + 1).fill(HudPen.Fill.XP, px, at(XP_Y, PanelPalette.XP_FILL));
        }
    }

    private static void health(HudPen pen, PlayerHud.Snapshot hud) {
        int well = BAR_X + SOCKET + 1;
        int px = fillPx(hud.current(), hud.max(), HP_WELL);
        int ghost = Math.min(HP_WELL, hud.ghostPx() * HP_WELL / HudGlyphs.WELL);
        pen.at(BAR_X).frame(HudPen.Frame.HP, at(HP_Y, PanelPalette.WHITE));
        if (ghost > px) {
            pen.at(well).fill(HudPen.Fill.HP, ghost, at(HP_Y, PanelPalette.HP_GHOST));
        }
        int tint = switch (hud.tone()) {
            case POISON -> PanelPalette.HP_POISON;
            case WITHER -> PanelPalette.HP_WITHER;
            case FROZEN -> PanelPalette.HP_FROZEN;
            case NORMAL -> PanelPalette.HP_FILL;
        };
        if (px > 0) {
            pen.at(well).fill(HudPen.Fill.HP, px, at(HP_Y, tint));
        }
        boolean absorb = hud.absorb() > 0.05;
        HudPen.Icon heart = absorb ? HudPen.Icon.HEART_GOLD : switch (hud.tone()) {
            case POISON -> HudPen.Icon.HEART_POISON;
            case WITHER -> HudPen.Icon.HEART_WITHER;
            case FROZEN -> HudPen.Icon.HEART_FROZEN;
            case NORMAL -> HudPen.Icon.HEART;
        };
        pen.at(BAR_X + 1).icon(heart, 0, at(HP_Y + 2, PanelPalette.WHITE));

        String cur = PlayerHud.number(hud.current());
        String max = PlayerHud.number(hud.max());
        String extra = absorb ? "+" + PlayerHud.number(hud.absorb()) : "";
        int textW = HudGlyphs.width(cur + "/" + max);
        int tx = well + (HP_WELL - textW) / 2;
        int ex = well + HP_WELL - 1 - HudGlyphs.width(extra);
        if (!extra.isEmpty()) {
            tx = Math.max(well + 1, Math.min(tx, ex - 3 - textW));
        }
        int y = HP_Y + 2;
        pen.at(tx).small(cur, 0, at(y, PanelPalette.WHITE)).small("/", 0, at(y, PanelPalette.HP_SLASH))
                .small(max, 0, at(y, PanelPalette.HP_MAX));
        if (!extra.isEmpty()) {
            pen.at(ex).small(extra, 0, at(y, PanelPalette.ABSORB));
        }
    }

    /** Stamina never hides: idle and full it rests in a calm gold, so the frame keeps its shape. */
    private static void stamina(HudPen pen, StaminaHud.Bar bar) {
        pen.at(BAR_X).frame(HudPen.Frame.STAMINA, at(ST_Y, PanelPalette.WHITE));
        if (bar == null) {
            pen.at(BAR_X + 1).fill(HudPen.Fill.STAMINA, BAR_WELL, at(ST_Y, PanelPalette.ST_IDLE));
            return;
        }
        int tint = switch (bar.state()) {
            case GUARD -> PanelPalette.ST_GUARD;
            case PERFECT -> PanelPalette.ST_PERFECT;
            case DENIED -> PanelPalette.DENY;
            case WINDED -> bar.flash() ? PanelPalette.DENY : PanelPalette.DENY_DIM;
            case NORMAL -> bar.flash() ? PanelPalette.WHITE : bar.fraction() < 0.25 ? PanelPalette.ST_LOW : PanelPalette.ST_FILL;
        };
        int px = (int) Math.round(bar.fraction() * BAR_WELL);
        if (bar.fraction() > 0.0 && px == 0) {
            px = 1;
        }
        if (bar.state() == StaminaHud.State.WINDED && px == 0) {
            px = BAR_WELL;
        }
        if (px > 0) {
            pen.at(BAR_X + 1).fill(HudPen.Fill.STAMINA, px, at(ST_Y, tint));
        }
        if (bar.state() != StaminaHud.State.NORMAL) {
            boolean bad = bar.state() == StaminaHud.State.DENIED || bar.state() == StaminaHud.State.WINDED;
            String label = HudGlyphs.fit(HudGlyphs.label(bar.label()), BAR_WELL - 4);
            pen.at(BAR_X + BAR_WELL - HudGlyphs.width(label)).small(label, 0,
                    at(ST_Y, bad ? PanelPalette.LABEL_DENY : PanelPalette.LABEL));
        }
    }

    private void info(HudPen pen, Player player, double defense) {
        long coins = plugin.getCoins() == null ? 0L : plugin.getCoins().get(player);
        long shards = plugin.getShards() == null ? 0L : plugin.getShards().get(player);
        pen.at(5).icon(HudPen.Icon.SHIELD, 0, at(INFO_Y, PanelPalette.WHITE));
        pen.at(13).small(HudGlyphs.fit(PlayerHud.number(Math.max(0.0, defense)), 28), 0, at(INFO_Y, PanelPalette.DEF));
        pen.at(44).icon(HudPen.Icon.COIN, 0, at(INFO_Y, PanelPalette.WHITE));
        pen.at(52).small(compact(coins), 0, at(INFO_Y, PanelPalette.COIN));
        pen.at(86).icon(HudPen.Icon.SHARD, 0, at(INFO_Y, PanelPalette.WHITE));
        pen.at(94).small(compact(shards), 0, at(INFO_Y, PanelPalette.SHARD));
    }

    private static void cooldown(HudPen pen, int y, AbilityCooldownHud.Row cd) {
        boolean ready = cd.state() == AbilityCooldownHud.State.READY || cd.state() == AbilityCooldownHud.State.READY_DIM;
        boolean denied = cd.state() == AbilityCooldownHud.State.DENIED;
        HudPen.Frame frame = ready ? HudPen.Frame.CD_READY : denied ? HudPen.Frame.CD_DENY : HudPen.Frame.CD;
        int tint = switch (cd.state()) {
            case COOLING -> PanelPalette.CD_FILL;
            case DENIED -> PanelPalette.DENY;
            case READY -> PanelPalette.CD_READY;
            case READY_DIM -> PanelPalette.CD_READY_DIM;
        };
        int well = CD_X + SOCKET + 1;
        int px = ready ? CD_WELL : (int) Math.floor(Math.max(0.0, Math.min(1.0, cd.progress())) * CD_WELL);
        pen.at(CD_X).frame(frame, at(y, PanelPalette.WHITE));
        if (px > 0) {
            pen.at(well).fill(HudPen.Fill.CD, px, at(y, tint));
        }
        HudPen.Icon icon = ready ? HudPen.Icon.CHECK : denied ? HudPen.Icon.CROSS
                : cd.pinned() ? HudPen.Icon.STAR : HudPen.Icon.HOURGLASS;
        pen.at(CD_X + 1).icon(icon, 1, at(y, PanelPalette.WHITE));

        String label = HudGlyphs.label(cd.label());
        int labelTint = denied ? PanelPalette.LABEL_DENY : PanelPalette.CD_LABEL;
        if (ready) {
            String withWord = label.isEmpty() ? "READY" : label + " READY";
            label = HudGlyphs.width(withWord) <= CD_WELL - 2 ? withWord : label;
            pen.at(well + 1).small(HudGlyphs.fit(label, CD_WELL - 2), 1, at(y, labelTint));
        } else {
            String left = AbilityCooldownHud.formatLeft(cd.leftTicks());
            int leftW = HudGlyphs.width(left);
            pen.at(well + 1).small(HudGlyphs.fit(label, CD_WELL - 2 - leftW - 3), 1, at(y, labelTint));
            pen.at(well + CD_WELL - 1 - leftW).small(left, 1, at(y, denied ? PanelPalette.LABEL_DENY : PanelPalette.WHITE));
        }
    }

    /** Place name on a plate under the radar, forage weather on a chip under that. */
    private void place(HudPen pen, Player player, int state) {
        String area = area(player);
        if (!area.isEmpty()) {
            String name = HudPen.bigFit(area, PLACE_MAX - 2 * HudPen.LOC_CAP - 2);
            int width = HudPen.bigWidth(name) + 2 * HudPen.LOC_CAP + 2;
            int x0 = right(width);
            pen.at(x0).plate(width, PanelPalette.radar(state, PanelPalette.PART_LABEL, PanelPalette.WHITE));
            pen.at(x0 + HudPen.LOC_CAP + 1).big(name, 0, PanelPalette.radar(state, PanelPalette.PART_LABEL + 1, PanelPalette.PLACE));
        }
        String weather = weather(player);
        if (!weather.isEmpty()) {
            int width = HudPen.CHIP_CAP + HudPen.ICON_W + 2 + HudGlyphs.width(weather) + 2 + HudPen.CHIP_CAP;
            int x0 = right(width);
            pen.at(x0).chip(width, PanelPalette.radar(state, PanelPalette.PART_LABEL + 4, PanelPalette.WHITE));
            TextColor row = PanelPalette.radar(state, PanelPalette.PART_LABEL + 5, PanelPalette.WHITE);
            pen.at(x0 + HudPen.CHIP_CAP).icon(HudPen.Icon.CLOUD, 0, row);
            pen.at(x0 + HudPen.CHIP_CAP + HudPen.ICON_W + 2).small(weather, 0,
                    PanelPalette.radar(state, PanelPalette.PART_LABEL + 5, PanelPalette.WEATHER));
        }
    }

    /** Centred under the radar, but never past the screen's right margin. */
    private static int right(int width) {
        return Math.min(HudPen.RADAR_W / 2 - width / 2, HudPen.RADAR_W - width);
    }

    private String area(Player player) {
        if (plugin.getAreas() == null) {
            return "";
        }
        return HudPen.bigLabel(plugin.getAreas().nameAt(player.getLocation()));
    }

    /** Forage-isle weather only, like the sidebar line; empty elsewhere. */
    private static String weather(Player player) {
        ForageAccess foraging = AetherServices.foraging();
        if (foraging == null) {
            return "";
        }
        ForageWeatherView state = foraging.weather(player);
        if (state == null || state.habitat() == null || "none".equals(String.valueOf(state.habitat()))) {
            return "";
        }
        String kind = state.kind() == null ? "CLEAR" : state.kind().toUpperCase(Locale.ROOT);
        boolean de = AetherServices.german(player);
        String label = switch (kind) {
            case "CLEAR" -> de ? "KLAR" : "CLEAR";
            case "FOG" -> de ? "NEBEL" : "FOG";
            case "RAIN" -> de ? "REGEN" : "RAIN";
            case "DRIZZLE" -> de ? "NIESEL" : "DRIZZLE";
            case "SNOW" -> de ? "SCHNEE" : "SNOW";
            case "WINDY" -> de ? "WIND" : "WINDY";
            default -> kind.replace('_', ' ');
        };
        if (state.source() != null && "RITUAL".equalsIgnoreCase(String.valueOf(state.source()))) {
            label += de ? " (RITUS)" : " (RITE)";
        }
        return HudGlyphs.label(label);
    }

    /* ------------------------------------------------------------------ helpers */

    /** Colour for a status-frame glyph whose top sits at {@code y} (even; odd y uses a phase-1 glyph). */
    private static TextColor at(int y, int tint) {
        return PanelPalette.frame(y / 2, tint);
    }

    /** Never full while hurt, never empty while alive. */
    private static int fillPx(double current, double max, int well) {
        if (current <= 0.0) {
            return 0;
        }
        int px = (int) Math.round(current / max * well);
        if (current < max - 0.01) {
            px = Math.min(px, well - 1);
        }
        return Math.max(1, px);
    }

    /** 950, 12.3k, 4.1m. */
    static String compact(long value) {
        if (value >= 1_000_000L) {
            String m = String.format(Locale.ROOT, "%.1f", value / 1_000_000.0);
            return (m.endsWith(".0") ? m.substring(0, m.length() - 2) : m) + "m";
        }
        if (value >= 10_000L) {
            return PlayerHud.number(value);
        }
        return Long.toString(Math.max(0L, value));
    }
}
