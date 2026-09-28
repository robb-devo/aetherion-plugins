package de.aetherion.bossengine.helios.encounter;

import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The encounter's own boss bars (the engine HUD stays away from scripted bodies).
 *
 * <ul>
 *   <li><b>Act I</b>: one bar, the Herald. 5 % notches; the title carries a marker for each phase gate
 *       (◇ ahead, ◆ passed), so the 50 % mirror phase is visible before it happens.</li>
 *   <li><b>Act II</b>: two stacked bars read as one bigger bar: Helios on top, and a thin "movement"
 *       bar under it that names the current phase (I · Korona …), fills with its progress and turns
 *       into the enrage countdown when that starts.</li>
 * </ul>
 *
 * The bars also carry the sky flags ({@code DARKEN_SCREEN}, {@code CREATE_WORLD_FOG}) per phase.
 * The bar fills up on arrival instead of popping in.
 */
public final class HeliosBars {

    private final BossBar main;
    private BossBar movement;
    private final Set<UUID> viewers = new HashSet<>();
    private String name = "";
    private double[] gates = new double[0];
    private float shown;
    private float fillRate = 1f;
    private boolean visible;

    public HeliosBars() {
        this.main = BossBar.bossBar(TextUtil.component(""), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);
    }

    /** Configure the bar for one boss: its display name and the HP percents of its phase gates. */
    public void boss(String name, BossBar.Color color, double... gates) {
        this.name = name;
        this.gates = gates == null ? new double[0] : gates;
        main.color(color);
        shown = 0f;
    }

    /** Animate the fill from empty over {@code ticks}. */
    public void fillOver(int ticks) {
        shown = 0f;
        fillRate = 1f / Math.max(1, ticks);
    }

    public void movement(boolean on) {
        if (on && movement == null) {
            movement = BossBar.bossBar(TextUtil.component(""), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            for (UUID id : viewers) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) {
                    p.showBossBar(movement);
                }
            }
        } else if (!on && movement != null) {
            for (UUID id : viewers) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(movement);
                }
            }
            movement = null;
        }
    }

    public void visible(boolean on) {
        this.visible = on;
    }

    /**
     * @param percent current HP percent (0..100)
     * @param blocked the boss is untouchable right now (bar turns white)
     * @param color   the bar color otherwise
     */
    public void update(double percent, boolean blocked, BossBar.Color color, boolean darken, boolean fog) {
        float target = (float) Math.max(0.0, Math.min(1.0, percent / 100.0));
        shown = Math.min(target, shown + fillRate);
        if (shown < target && fillRate >= 1f) {
            shown = target;
        }
        main.progress(shown);
        StringBuilder title = new StringBuilder(name);
        if (gates.length > 0) {
            title.append("  ");
            for (double g : gates) {
                title.append(percent <= g ? "&6◆" : "&8◇");
            }
        }
        title.append("  &8| &f").append((int) Math.ceil(percent)).append('%');
        main.name(TextUtil.component(title.toString()));
        main.color(blocked ? BossBar.Color.WHITE : color);
        flag(main, BossBar.Flag.DARKEN_SCREEN, darken);
        flag(main, BossBar.Flag.CREATE_WORLD_FOG, fog);
    }

    public void color(BossBar.Color color) {
        main.color(color);
    }

    /** The movement bar: phase name and progress through it (or the enrage countdown). */
    public void movement(String title, float progress, BossBar.Color color) {
        if (movement == null) {
            return;
        }
        movement.name(TextUtil.component(title));
        movement.progress(Math.max(0f, Math.min(1f, progress)));
        movement.color(color);
    }

    private static void flag(BossBar bar, BossBar.Flag flag, boolean on) {
        if (on) {
            bar.addFlag(flag);
        } else {
            bar.removeFlag(flag);
        }
    }

    /** Show to exactly {@code players} (and hide from anyone else who had it). */
    public void sync(List<Player> players) {
        Set<UUID> now = new HashSet<>();
        if (visible) {
            for (Player p : players) {
                now.add(p.getUniqueId());
                if (viewers.add(p.getUniqueId())) {
                    p.showBossBar(main);
                    if (movement != null) {
                        p.showBossBar(movement);
                    }
                }
            }
        }
        for (UUID id : new ArrayList<>(viewers)) {
            if (!now.contains(id)) {
                hide(id);
            }
        }
    }

    private void hide(UUID id) {
        viewers.remove(id);
        Player p = org.bukkit.Bukkit.getPlayer(id);
        if (p != null) {
            p.hideBossBar(main);
            if (movement != null) {
                p.hideBossBar(movement);
            }
        }
    }

    public void hide(Player p) {
        if (p != null) {
            hide(p.getUniqueId());
        }
    }

    public void hideAll() {
        for (UUID id : new ArrayList<>(viewers)) {
            hide(id);
        }
    }
}
