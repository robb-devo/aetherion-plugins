package de.aetherion.items.combat;

import de.aetherion.core.api.ActionBarLane;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the bottom ActionBar: HP, defense and weapon cooldowns, plus any line another plugin posted on
 * {@link ActionBarLane}.
 *
 * <p>Glyph HUD ({@code hud.pack-glyphs}, default on): the pack hides vanilla hearts, food and armor, and
 * {@link HudGlyphs} draws an HP bar over the heart row, a DEF badge over the armor row and up to three
 * cooldown bars over the food row. Text HUD (off): the old one-line {@code ❤ hp / max  🛡 def  · CD}
 * readout, for a pack that does not have font {@code aetherion:hud} yet.
 *
 * <p>Checked every 2 ticks by {@link de.aetherion.items.listener.HealthListener} and right away on
 * damage / heal / lane posts, but only sent when the picture changed, plus a {@link #HEARTBEAT_TICKS}
 * keep-alive: the client starts fading an ActionBar 40 ticks after the last packet, and a plain
 * {@code sendActionBar} from elsewhere that wiped the HUD has to be painted over.
 */
public final class PlayerHud implements ActionBarLane.Painter {

    /** What the HUD reads. HealthListener owns the numbers. */
    public interface Source {
        double maxHealth(Player player);

        double defense(Player player);

        String heldItemId(Player player);
    }

    static final long HEARTBEAT_TICKS = 20L;
    /** How long the lost chunk of the HP bar lingers pale after a hit. */
    private static final long GHOST_TICKS = 12L;

    private static final TextColor HP_FILL = TextColor.color(0xD93A40);
    private static final TextColor HP_POISON = TextColor.color(0x6DBB3A);
    private static final TextColor HP_WITHER = TextColor.color(0x5A5462);
    private static final TextColor HP_FROZEN = TextColor.color(0x86CDEE);
    private static final TextColor HP_GHOST = TextColor.color(0xF2D9CF);
    private static final TextColor HP_TEXT = TextColor.color(0xFFFFFF);
    private static final TextColor HP_SLASH = TextColor.color(0xB5A9BC);
    private static final TextColor HP_MAX = TextColor.color(0xFFC9CC);
    private static final TextColor ABSORB = TextColor.color(0xF2C94C);
    private static final TextColor DEF_TEXT = TextColor.color(0xCFF5EF);
    private static final TextColor CD_FILL = TextColor.color(0xD9A441);
    private static final TextColor CD_DENY = TextColor.color(0xFF5A4A);
    private static final TextColor CD_READY = TextColor.color(0x62D977);
    private static final TextColor CD_READY_DIM = TextColor.color(0x4FA862);
    private static final TextColor CD_LABEL = TextColor.color(0xFFF1DA);
    private static final TextColor CD_LABEL_DENY = TextColor.color(0xFFC2BA);

    private final JavaPlugin plugin;
    private final Source source;
    private final Map<UUID, Painted> painted = new ConcurrentHashMap<>();
    private final Map<UUID, Ghost> ghosts = new ConcurrentHashMap<>();

    public PlayerHud(JavaPlugin plugin, Source source) {
        this.plugin = plugin;
        this.source = source;
    }

    private boolean glyphs() {
        return plugin.getConfig().getBoolean("hud.pack-glyphs", true);
    }

    /** Repaint if anything changed (or the keep-alive is due). Safe to call every tick. */
    @Override
    public void paint(Player player) {
        if (player == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> paint(player));
            return;
        }
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        Component line = ActionBarLane.line(player);
        Component bar = glyphs() ? glyphBar(player, line, tick) : textBar(player, line);
        boolean blank = bar.equals(Component.empty());
        UUID id = player.getUniqueId();
        Painted last = painted.get(id);
        if (last != null && last.bar().equals(bar) && (blank || tick - last.tick() < HEARTBEAT_TICKS)) {
            return;
        }
        player.sendActionBar(bar);
        painted.put(id, new Painted(bar, tick));
    }

    public void forget(UUID playerId) {
        painted.remove(playerId);
        ghosts.remove(playerId);
    }

    /* ------------------------------------------------------------------ glyph HUD */

    private Component glyphBar(Player player, Component line, long tick) {
        TextComponent.Builder out = Component.text();
        if (line != null) {
            // Centre the line on its own, then walk the pen back so the HUD still starts on the centre.
            int width = HudWidth.of(line);
            int half = width / 2;
            out.append(space(-half)).append(line).append(space(half - width));
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            out.append(hud(player, tick));
        }
        return out.build();
    }

    /** Heart / fill colour of the HP bar, like vanilla's poisoned / withered / frozen hearts. */
    enum Tone {
        NORMAL(HP_FILL, HudGlyphs.Icon.HEART),
        POISON(HP_POISON, HudGlyphs.Icon.HEART_POISON),
        WITHER(HP_WITHER, HudGlyphs.Icon.HEART_WITHER),
        FROZEN(HP_FROZEN, HudGlyphs.Icon.HEART_FROZEN);

        final TextColor fill;
        final HudGlyphs.Icon heart;

        Tone(TextColor fill, HudGlyphs.Icon heart) {
            this.fill = fill;
            this.heart = heart;
        }
    }

    private Component hud(Player player, long tick) {
        double max = Math.max(1.0, source.maxHealth(player));
        double current = Math.max(0.0, Math.min(player.getHealth(), max));
        Tone tone = player.hasPotionEffect(PotionEffectType.WITHER) ? Tone.WITHER
                : player.hasPotionEffect(PotionEffectType.POISON) ? Tone.POISON
                : player.isFrozen() ? Tone.FROZEN : Tone.NORMAL;
        int ghost = ghost(player.getUniqueId(), fillPx(current, max), tick);
        return layout(current, max, player.getAbsorptionAmount(), tone, ghost, source.defense(player),
                AbilityCooldownHud.rows(player, source.heldItemId(player)));
    }

    /** The whole glyph HUD from plain values (net advance 0). */
    static Component layout(double current, double max, double absorb, Tone tone, int ghostPx, double defense,
                            List<AbilityCooldownHud.Row> rows) {
        HudGlyphs.Pen pen = new HudGlyphs.Pen();
        health(pen, current, max, absorb, tone, ghostPx);
        defense(pen, defense);
        for (int i = 0; i < rows.size() && i < HudGlyphs.ROWS; i++) {
            cooldown(pen, i, rows.get(i));
        }
        return pen.build();
    }

    private static void health(HudGlyphs.Pen pen, double current, double max, double absorb, Tone tone, int ghost) {
        int px = fillPx(current, max);
        int well = HudGlyphs.LEFT_X + HudGlyphs.HP_WELL;

        pen.at(HudGlyphs.LEFT_X).frame(0, HudGlyphs.Frame.HP);
        if (ghost > px) {
            pen.at(well).fill(0, ghost, HP_GHOST);
        }
        HudGlyphs.Icon heart = tone.heart;
        if (px > 0) {
            pen.at(well).fill(0, px, tone.fill);
        }

        String cur = number(current);
        String mx = number(max);
        String extra = absorb > 0.05 ? "+" + number(absorb) : "";
        int textW = HudGlyphs.width(cur + "/" + mx);
        int tx = well + (HudGlyphs.WELL - textW) / 2;
        int ex = well + HudGlyphs.WELL - 1 - HudGlyphs.width(extra);
        if (!extra.isEmpty()) {
            tx = Math.max(well + 1, Math.min(tx, ex - 3 - textW));
        }
        pen.at(tx).text(0, cur, HP_TEXT).text(0, "/", HP_SLASH).text(0, mx, HP_MAX);
        if (!extra.isEmpty()) {
            pen.at(ex).text(0, extra, ABSORB);
            heart = HudGlyphs.Icon.HEART_GOLD;
        }
        pen.at(HudGlyphs.LEFT_X + HudGlyphs.ICON_INSET).icon(0, heart);
    }

    private static void defense(HudGlyphs.Pen pen, double defense) {
        if (defense <= 0.05) {
            return;
        }
        String text = HudGlyphs.fit(number(defense), HudGlyphs.DEF_TEXT_W);
        pen.at(HudGlyphs.LEFT_X).frame(1, HudGlyphs.Frame.DEFENSE);
        pen.at(HudGlyphs.LEFT_X + HudGlyphs.DEF_TEXT + (HudGlyphs.DEF_TEXT_W - HudGlyphs.width(text)) / 2)
                .text(1, text, DEF_TEXT);
        pen.at(HudGlyphs.LEFT_X + HudGlyphs.ICON_INSET).icon(1, HudGlyphs.Icon.SHIELD);
    }

    private static void cooldown(HudGlyphs.Pen pen, int row, AbilityCooldownHud.Row cd) {
        boolean ready = cd.state() == AbilityCooldownHud.State.READY || cd.state() == AbilityCooldownHud.State.READY_DIM;
        boolean denied = cd.state() == AbilityCooldownHud.State.DENIED;
        HudGlyphs.Frame frame = ready ? HudGlyphs.Frame.COOLDOWN_READY
                : denied ? HudGlyphs.Frame.COOLDOWN_DENY : HudGlyphs.Frame.COOLDOWN;
        TextColor tint = switch (cd.state()) {
            case COOLING -> CD_FILL;
            case DENIED -> CD_DENY;
            case READY -> CD_READY;
            case READY_DIM -> CD_READY_DIM;
        };
        int well = HudGlyphs.RIGHT_X + HudGlyphs.CD_WELL;
        int px = ready ? HudGlyphs.WELL : (int) Math.floor(Math.max(0.0, Math.min(1.0, cd.progress())) * HudGlyphs.WELL);

        pen.at(HudGlyphs.RIGHT_X).frame(row, frame);
        if (px > 0) {
            pen.at(well).fill(row, px, tint);
        }
        String label = HudGlyphs.label(cd.label());
        if (ready) {
            String withWord = label.isEmpty() ? "READY" : label + " READY";
            label = HudGlyphs.width(withWord) <= HudGlyphs.WELL - 2 ? withWord : label;
            pen.at(well + 1).text(row, HudGlyphs.fit(label, HudGlyphs.WELL - 2), CD_LABEL);
        } else {
            String left = AbilityCooldownHud.formatLeft(cd.leftTicks());
            int leftW = HudGlyphs.width(left);
            pen.at(well + 1).text(row, HudGlyphs.fit(label, HudGlyphs.WELL - 2 - leftW - 3), denied ? CD_LABEL_DENY : CD_LABEL);
            pen.at(well + HudGlyphs.WELL - 1 - leftW).text(row, left, denied ? CD_LABEL_DENY : HP_TEXT);
        }
        HudGlyphs.Icon icon = ready ? HudGlyphs.Icon.CHECK
                : denied ? HudGlyphs.Icon.CROSS
                : cd.pinned() ? HudGlyphs.Icon.STAR : HudGlyphs.Icon.HOURGLASS;
        pen.at(HudGlyphs.RIGHT_X + HudGlyphs.CD_SOCKET + HudGlyphs.ICON_INSET).icon(row, icon);
    }

    /** Never full while hurt, never empty while alive. */
    private static int fillPx(double current, double max) {
        if (current <= 0.0) {
            return 0;
        }
        int px = (int) Math.round(current / max * HudGlyphs.WELL);
        if (current < max - 0.01) {
            px = Math.min(px, HudGlyphs.WELL - 1);
        }
        return Math.max(1, px);
    }

    /** Pale trail of the HP just lost, held for {@link #GHOST_TICKS} after the last hit. */
    private int ghost(UUID id, int px, long tick) {
        Ghost ghost = ghosts.computeIfAbsent(id, ignored -> new Ghost(px));
        if (px < ghost.lastPx) {
            ghost.fromPx = Math.max(ghost.lastPx, tick < ghost.untilTick ? ghost.fromPx : 0);
            ghost.untilTick = tick + GHOST_TICKS;
        }
        ghost.lastPx = px;
        return tick < ghost.untilTick ? ghost.fromPx : 0;
    }

    private static Component space(int px) {
        return Component.text(HudGlyphs.space(px)).font(HudGlyphs.FONT);
    }

    /* ------------------------------------------------------------------ text HUD */

    private Component textBar(Player player, Component line) {
        double max = Math.max(1.0, source.maxHealth(player));
        double current = Math.max(0.0, Math.min(player.getHealth(), max));
        double defense = source.defense(player);

        // Fixed HP slot: current HP is right-aligned to the width of max HP, so 100 → 99 never wiggles the line.
        String currentText = number(current);
        String maxText = number(max);
        String hpPad = HudWidth.padTo(HudWidth.of(currentText, false), HudWidth.of(maxText, false));
        Component health = Component.text()
                .append(Component.text("❤ ", NamedTextColor.RED))
                .append(Component.text(hpPad + currentText, NamedTextColor.WHITE))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(maxText, NamedTextColor.RED))
                .build();
        if (line != null) {
            // A posted line wins the text HUD; HP stays readable beside it.
            return Component.text().append(line).append(Component.text("   ")).append(health).build();
        }

        Component bar = health;
        if (defense > 0.05) {
            Component defenseHud = Component.text()
                    .append(Component.text("🛡 ", NamedTextColor.AQUA))
                    .append(Component.text(number(defense), NamedTextColor.WHITE))
                    .build();
            bar = health.append(Component.text("                      ")).append(defenseHud);
        }

        String heldId = source.heldItemId(player);
        if (AbilityCooldownHud.showing(player, heldId)) {
            Component cooldown = AbilityCooldownHud.fragment(player, heldId, defense <= 0.05);
            Component lane = defense > 0.05
                    ? Component.text("  ·  ", NamedTextColor.DARK_GRAY).append(cooldown)
                    : cooldown;
            /*
             * Reserved CD lane: the ActionBar is centred, so the cooldown on the right would drag hearts and
             * defense left by half its width. Mirror its width as blank padding on the left — the centre
             * (and every HP / defense glyph) stays exactly where it sits without a cooldown.
             */
            bar = HudWidth.balance(HudWidth.of(lane)).append(bar).append(lane);
        }
        return bar;
    }

    /** 271, 135.5, 12.3k. */
    static String number(double value) {
        if (value >= 10_000.0) {
            String k = String.format(Locale.ROOT, "%.1f", value / 1000.0);
            return (k.endsWith(".0") ? k.substring(0, k.length() - 2) : k) + "k";
        }
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record Painted(Component bar, long tick) {
    }

    private static final class Ghost {
        int lastPx;
        int fromPx;
        long untilTick;

        Ghost(int px) {
            this.lastPx = px;
        }
    }
}
