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

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MMO status panel, top-left at any resolution: level + XP, HP, stamina, DEF, cooldowns, purse, place.
 *
 * <p>Same ActionBar packet and the same frames as the classic HUD ({@link PlayerHud} still supplies every
 * number); each glyph's colour tells the pack shader which panel row it belongs to, see {@link PanelPalette}.
 * It also carries what the TAB sidebar used to show (level, coins, shards, area, weather), so the sidebar
 * can stay off. {@code hud.layout: classic} brings the old bars back.
 */
public final class StatusPanel implements PlayerHud.PanelPainter {

    static final Key FONT = Key.key("aetherion", "panel");

    private static final int PLATE_TOP = 0xE400;
    private static final int PLATE_MID = 0xE401;
    private static final int PLATE_BOT = 0xE402;
    private static final int PLATE_ADV = 88;
    private static final int ICON_COIN = 0xE410;
    private static final int ICON_SHARD = 0xE411;
    private static final int ICON_PIN = 0xE412;
    private static final int ICON_CLOUD = 0xE413;
    /** Frames sit 3 px inside the plate. */
    private static final int X = 3;

    private enum Kind { LEVEL, HP, STAMINA, DEF, COOLDOWN, PURSE, PLACE, WEATHER }

    private final AetherionItems plugin;
    private final RadarHud radar;

    public StatusPanel(AetherionItems plugin, RadarHud radar) {
        this.plugin = plugin;
        this.radar = radar;
    }

    @Override
    public Component paint(Player player, PlayerHud.Snapshot hud, long tick) {
        HudGlyphs.Pen pen = new HudGlyphs.Pen(PanelPalette::panel);

        List<Kind> rows = new ArrayList<>();
        rows.add(Kind.LEVEL);
        rows.add(Kind.HP);
        if (hud.stamina() != null) {
            rows.add(Kind.STAMINA);
        }
        if (hud.defense() > 0.05) {
            rows.add(Kind.DEF);
        }
        int cds = Math.min(HudGlyphs.ROWS, hud.cooldowns().size());
        for (int i = 0; i < cds; i++) {
            rows.add(Kind.COOLDOWN);
        }
        rows.add(Kind.PURSE);
        String area = area(player);
        if (!area.isEmpty()) {
            rows.add(Kind.PLACE);
        }
        String weather = weather(player);
        if (!weather.isEmpty()) {
            rows.add(Kind.WEATHER);
        }

        for (int r = 0; r < rows.size(); r++) {
            int glyph = r == 0 ? PLATE_TOP : r == rows.size() - 1 ? PLATE_BOT : PLATE_MID;
            pen.at(0).art(FONT, r, glyph, PLATE_ADV, null);
        }

        int cd = 0;
        for (int r = 0; r < rows.size(); r++) {
            switch (rows.get(r)) {
                case LEVEL -> level(pen, r, player);
                case HP -> PlayerHud.health(pen, X, r, hud.current(), hud.max(), hud.absorb(), hud.tone(), hud.ghostPx());
                case STAMINA -> StaminaHud.draw(pen, hud.stamina(), r, X);
                case DEF -> PlayerHud.defense(pen, X, r, hud.defense());
                case COOLDOWN -> PlayerHud.cooldown(pen, X, r, hud.cooldowns().get(cd++));
                case PURSE -> purse(pen, r, player);
                case PLACE -> line(pen, r, ICON_PIN, area, PanelPalette.PLACE);
                case WEATHER -> line(pen, r, ICON_CLOUD, weather, PanelPalette.WEATHER);
            }
        }

        if (radar != null && plugin.getConfig().getBoolean("hud.radar", true)) {
            radar.draw(pen, player, tick);
        }
        return pen.build();
    }

    @Override
    public void forget(UUID playerId) {
        if (radar != null) {
            radar.forget(playerId);
        }
    }

    private void level(HudGlyphs.Pen pen, int row, Player player) {
        int level = 1;
        double fraction = 0.0;
        if (plugin.getSkills() != null) {
            level = plugin.getSkills().accountLevel(player);
            long xp = plugin.getSkills().accountXp(player);
            long need = AetherionLevel.xpToNext(level);
            fraction = need <= 0L ? 1.0 : Math.max(0.0, Math.min(1.0, AetherionLevel.intoLevel(xp) / (double) need));
        }
        int well = X + HudGlyphs.CD_WELL;
        pen.at(X).frame(row, HudGlyphs.Frame.COOLDOWN);
        int px = (int) Math.floor(fraction * HudGlyphs.WELL);
        if (px > 0) {
            pen.at(well).fill(row, px, PanelPalette.XP_FILL);
        }
        pen.at(well + 1).text(row, "LV " + level, PanelPalette.LEVEL);
        String pct = (int) Math.floor(fraction * 100) + "%";
        pen.at(well + HudGlyphs.WELL - 1 - HudGlyphs.width(pct)).text(row, pct, HudGlyphs.WHITE);
        pen.at(X + HudGlyphs.CD_SOCKET + HudGlyphs.ICON_INSET).icon(row, HudGlyphs.Icon.STAR);
    }

    private void purse(HudGlyphs.Pen pen, int row, Player player) {
        long coins = plugin.getCoins() == null ? 0L : plugin.getCoins().get(player);
        long shards = plugin.getShards() == null ? 0L : plugin.getShards().get(player);
        pen.at(X + 1).art(FONT, row, ICON_COIN, HudGlyphs.ICON_ADVANCE, null);
        pen.at(X + 10).text(row, compact(coins), PanelPalette.COIN);
        pen.at(X + 44).art(FONT, row, ICON_SHARD, HudGlyphs.ICON_ADVANCE, null);
        pen.at(X + 53).text(row, compact(shards), PanelPalette.SHARD);
    }

    private static void line(HudGlyphs.Pen pen, int row, int icon, String text, net.kyori.adventure.text.format.TextColor tint) {
        pen.at(X + 1).art(FONT, row, icon, HudGlyphs.ICON_ADVANCE, null);
        pen.at(X + 10).text(row, HudGlyphs.fit(text, HudGlyphs.FRAME_W - 11), tint);
    }

    private String area(Player player) {
        if (plugin.getAreas() == null) {
            return "";
        }
        return HudGlyphs.label(plugin.getAreas().nameAt(player.getLocation()));
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
