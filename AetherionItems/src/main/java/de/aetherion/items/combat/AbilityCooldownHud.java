package de.aetherion.items.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Per-weapon ability cooldown for the shared ActionBar (HP + CD).
 *
 * <p>{@link PlayerHud} owns the bar (checked every 2 ticks, sent on change); this keeps an
 * independent gate per item id so swapping weapons preserves each tool's remaining
 * cooldown. Glyph HUD: a filling bar per gate ({@link #rows}). Text HUD:
 * {@code Label ▮▮▮▮▮▯▯▯ 3.4s} ({@link #fragment}). Both show a short red pulse when the
 * player presses during CD, and a green ready flash (with one soft ping) when it comes back.
 *
 * <p>Pinned gates (set-armor / off-hand abilities) show regardless of the
 * held item, after the held weapon's gate.
 */
public final class AbilityCooldownHud {

    private static final Map<UUID, Map<String, Gate>> GATES = new ConcurrentHashMap<>();
    /** Which gate last painted the vanilla hotbar overlay for a material. */
    private static final Map<UUID, Map<Material, String>> OVERLAY_OWNER = new ConcurrentHashMap<>();

    private static final long READY_FLASH_TICKS = 40L;
    private static final long DENY_PULSE_TICKS = 10L;
    private static final int BAR_SEGMENTS = 8;
    private static final int MAX_PINNED_SHOWN = 2;

    private static final TextColor LABEL = TextColor.color(0xE8C27A);   // warm bronze
    private static final TextColor BAR_ON = TextColor.color(0xD9A441);
    private static final TextColor BAR_OFF = TextColor.color(0x4A4038);
    private static final TextColor DENY = TextColor.color(0xFF6A5A);
    private static final TextColor READY = TextColor.color(0x7BEA8A);
    private static final TextColor READY_DIM = TextColor.color(0x58B868);

    private AbilityCooldownHud() {
    }

    /* ------------------------------------------------------------------ arming */

    public static void arm(Player player, String itemId, String label, long readyTick) {
        arm(player, itemId, label, readyTick, null);
    }

    /**
     * Start / refresh a cooldown for one weapon. Pass {@code stack} so the
     * vanilla hotbar material cooldown overlay stays in sync.
     */
    public static void arm(Player player, String itemId, String label, long readyTick, ItemStack stack) {
        put(player, itemId, label, readyTick, false);
        if (player != null && itemId != null && !itemId.isBlank()) {
            applyHotbarCooldown(player, key(itemId), stack, Math.max(Bukkit.getCurrentTick(), readyTick));
        }
    }

    public static void armMs(Player player, String itemId, String label, long durationMs) {
        armMs(player, itemId, label, durationMs, null);
    }

    public static void armMs(Player player, String itemId, String label, long durationMs, ItemStack stack) {
        if (durationMs <= 0L) {
            return;
        }
        arm(player, itemId, label, Bukkit.getCurrentTick() + msToTicks(durationMs), stack);
    }

    /** Set-armor / off-hand ability: shown on the bar whatever the player holds. */
    public static void armPinned(Player player, String key, String label, long readyTick) {
        put(player, key, label, readyTick, true);
    }

    public static void armPinnedMs(Player player, String key, String label, long durationMs) {
        if (durationMs <= 0L) {
            return;
        }
        armPinned(player, key, label, Bukkit.getCurrentTick() + msToTicks(durationMs));
    }

    /**
     * Player pressed the ability while it is still cooling — pulse the label
     * red for half a second instead of a separate ActionBar line (which the
     * HP bar would overwrite two ticks later anyway).
     */
    public static void denied(Player player, String itemId) {
        if (player == null || itemId == null) {
            return;
        }
        Gate gate = gate(player, itemId);
        if (gate != null) {
            gate.deniedUntil = Bukkit.getCurrentTick() + DENY_PULSE_TICKS;
        }
    }

    public static void clear(Player player) {
        if (player != null) {
            GATES.remove(player.getUniqueId());
            OVERLAY_OWNER.remove(player.getUniqueId());
        }
    }

    public static void clearIf(Player player, Predicate<String> itemIdMatch) {
        if (player == null || itemIdMatch == null) {
            return;
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null) {
            return;
        }
        byItem.entrySet().removeIf(entry -> itemIdMatch.test(entry.getKey()));
        if (byItem.isEmpty()) {
            GATES.remove(player.getUniqueId(), byItem);
        }
    }

    /* ------------------------------------------------------------------ hotbar */

    /** Re-apply vanilla hotbar overlay when swapping back onto a cooling weapon. */
    public static void syncHotbar(Player player, String heldItemId, ItemStack stack) {
        if (player == null || heldItemId == null || stack == null) {
            return;
        }
        Gate gate = gate(player, heldItemId);
        if (gate != null && !gate.pinned && Bukkit.getCurrentTick() < gate.readyTick) {
            applyHotbarCooldown(player, gate.itemId, stack, gate.readyTick);
        }
    }

    /**
     * Held-slot change. Vanilla cooldowns are per material, so two weapons on
     * the same base material would share one overlay. Hand the overlay to the
     * newly held weapon's gate, or lift ours if the new weapon is not cooling.
     * Overlays we did not set (pearls, shields…) are never touched.
     */
    public static void onHeldChange(Player player, String heldItemId, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        Material type = stack.getType();
        String held = heldItemId == null || heldItemId.isBlank() ? null : key(heldItemId);
        Gate gate = held == null ? null : gate(player, held);
        if (gate != null && !gate.pinned && Bukkit.getCurrentTick() < gate.readyTick) {
            applyHotbarCooldown(player, gate.itemId, stack, gate.readyTick);
            return;
        }
        Map<Material, String> owners = OVERLAY_OWNER.get(player.getUniqueId());
        String owner = owners == null ? null : owners.get(type);
        if (owner != null && !owner.equals(held)) {
            player.setCooldown(type, 0);
            owners.remove(type);
        }
    }

    /* ------------------------------------------------------------------ render */

    /** True when the held weapon (or any pinned ability) has a CD / ready flash to show. */
    public static boolean showing(Player player, String heldItemId) {
        if (player == null) {
            return false;
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null || byItem.isEmpty()) {
            return false;
        }
        long tick = Bukkit.getCurrentTick();
        String held = heldItemId == null || heldItemId.isBlank() ? null : key(heldItemId);
        for (Gate gate : byItem.values()) {
            if (tick >= gate.readyTick + READY_FLASH_TICKS) {
                continue;
            }
            if (gate.pinned || gate.itemId.equals(held)) {
                return true;
            }
        }
        return false;
    }

    /** Appended by {@link PlayerHud}'s text HUD. */
    public static Component fragment(Player player, String heldItemId) {
        return fragment(player, heldItemId, true);
    }

    /**
     * @param withSeparator leading {@code ·} for the no-defense (HP-adjacent) layout
     */
    public static Component fragment(Player player, String heldItemId, boolean withSeparator) {
        List<Gate> shown = shown(player, heldItemId);
        if (shown.isEmpty()) {
            return Component.empty();
        }
        long tick = Bukkit.getCurrentTick();

        TextComponent.Builder out = Component.text();
        boolean first = true;
        for (Gate gate : shown) {
            if (!first || withSeparator) {
                out.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY));
            }
            out.append(render(player, gate, tick));
            first = false;
        }
        return out.build().decoration(TextDecoration.ITALIC, false);
    }

    /** Cooldown as the glyph HUD draws it: one bar per shown gate, held weapon first. */
    public record Row(String label, double progress, long leftTicks, State state, boolean pinned) {
    }

    public enum State {
        COOLING, DENIED, READY, READY_DIM
    }

    /** Same gates, order and ready ping as {@link #fragment}, for {@link PlayerHud}'s glyph bars. */
    public static List<Row> rows(Player player, String heldItemId) {
        List<Gate> shown = shown(player, heldItemId);
        if (shown.isEmpty()) {
            return List.of();
        }
        long tick = Bukkit.getCurrentTick();
        List<Row> rows = new ArrayList<>(shown.size());
        for (Gate gate : shown) {
            if (tick >= gate.readyTick) {
                ping(player, gate);
                boolean bright = tick - gate.readyTick < READY_FLASH_TICKS / 2;
                rows.add(new Row(gate.label, 1.0, 0L, bright ? State.READY : State.READY_DIM, gate.pinned));
                continue;
            }
            long left = gate.readyTick - tick;
            long total = Math.max(1L, gate.readyTick - gate.startTick);
            double done = 1.0 - Math.min(1.0, (double) left / (double) total);
            rows.add(new Row(gate.label, done, left, tick < gate.deniedUntil ? State.DENIED : State.COOLING, gate.pinned));
        }
        return rows;
    }

    /** Held weapon's gate first, then up to {@link #MAX_PINNED_SHOWN} pinned gates, soonest first. */
    private static List<Gate> shown(Player player, String heldItemId) {
        if (player == null) {
            return List.of();
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null || byItem.isEmpty()) {
            return List.of();
        }
        prune(byItem);
        if (byItem.isEmpty()) {
            GATES.remove(player.getUniqueId(), byItem);
            return List.of();
        }
        String held = heldItemId == null || heldItemId.isBlank() ? null : key(heldItemId);
        List<Gate> shown = new ArrayList<>(1 + MAX_PINNED_SHOWN);
        Gate heldGate = held == null ? null : byItem.get(held);
        if (heldGate != null && !heldGate.pinned) {
            shown.add(heldGate);
        }
        byItem.values().stream()
                .filter(g -> g.pinned)
                .sorted(Comparator.comparingLong(g -> g.readyTick))
                .limit(MAX_PINNED_SHOWN)
                .forEach(shown::add);
        return shown;
    }

    /** One soft chime when a gate comes back, whichever renderer sees it first. */
    private static void ping(Player player, Gate gate) {
        if (!gate.pinged) {
            gate.pinged = true;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.45f, 1.6f);
        }
    }

    private static Component render(Player player, Gate gate, long tick) {
        if (tick >= gate.readyTick) {
            long since = tick - gate.readyTick;
            ping(player, gate);
            boolean bright = since < READY_FLASH_TICKS / 2;
            return Component.text()
                    .append(Component.text("✔ ", bright ? READY : READY_DIM).decoration(TextDecoration.BOLD, bright))
                    .append(Component.text(gate.label, READY))
                    .append(Component.text(" ready", bright ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                    .build();
        }

        long leftTicks = gate.readyTick - tick;
        long total = Math.max(1L, gate.readyTick - gate.startTick);
        double done = 1.0 - Math.min(1.0, (double) leftTicks / (double) total);
        int filled = (int) Math.floor(done * BAR_SEGMENTS);
        boolean denied = tick < gate.deniedUntil;

        StringBuilder on = new StringBuilder();
        StringBuilder off = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            (i < filled ? on : off).append('▮');
        }
        TextComponent.Builder bar = Component.text();
        bar.append(Component.text(gate.label + " ", denied ? DENY : LABEL));
        if (on.length() > 0) {
            bar.append(Component.text(on.toString(), denied ? DENY : BAR_ON));
        }
        if (off.length() > 0) {
            bar.append(Component.text(off.toString(), BAR_OFF));
        }
        bar.append(Component.text(" " + formatLeft(leftTicks), denied ? DENY : NamedTextColor.WHITE));
        return bar.build();
    }

    /** 3.4s under ten seconds, 12s under a minute, 1m05s beyond. */
    static String formatLeft(long ticks) {
        if (ticks < 200L) {
            long tenths = Math.max(1L, (ticks + 1L) / 2L);
            return (tenths / 10L) + "." + (tenths % 10L) + "s";
        }
        long secs = (ticks + 19L) / 20L;
        if (secs < 60L) {
            return secs + "s";
        }
        return (secs / 60L) + "m" + String.format(Locale.ROOT, "%02d", secs % 60L) + "s";
    }

    /* ------------------------------------------------------------------ internals */

    private static void put(Player player, String itemId, String label, long readyTick, boolean pinned) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return;
        }
        String key = key(itemId);
        String name = label == null || label.isBlank() ? prettify(itemId) : label;
        long now = Bukkit.getCurrentTick();
        long ready = Math.max(now, readyTick);
        GATES.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(key, new Gate(key, name, now, ready, pinned));
    }

    private static Gate gate(Player player, String itemId) {
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        return byItem == null ? null : byItem.get(key(itemId));
    }

    private static void applyHotbarCooldown(Player player, String owner, ItemStack stack, long readyTick) {
        if (stack == null) {
            return;
        }
        Material type = stack.getType();
        if (type == null || type.isAir()) {
            return;
        }
        int left = (int) Math.max(0L, readyTick - Bukkit.getCurrentTick());
        if (left > 0) {
            player.setCooldown(type, left);
            OVERLAY_OWNER.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(type, owner);
        }
    }

    private static void prune(Map<String, Gate> byItem) {
        long tick = Bukkit.getCurrentTick();
        Iterator<Map.Entry<String, Gate>> it = byItem.entrySet().iterator();
        while (it.hasNext()) {
            Gate gate = it.next().getValue();
            if (tick >= gate.readyTick + READY_FLASH_TICKS) {
                it.remove();
            }
        }
    }

    private static long msToTicks(long ms) {
        return Math.max(1L, (ms + 49L) / 50L);
    }

    private static String key(String itemId) {
        return itemId.toLowerCase(Locale.ROOT);
    }

    private static String prettify(String itemId) {
        String[] parts = itemId.replace(':', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? itemId : out.toString();
    }

    private static final class Gate {
        final String itemId;
        final String label;
        final long startTick;
        final long readyTick;
        final boolean pinned;
        volatile long deniedUntil;
        volatile boolean pinged;

        Gate(String itemId, String label, long startTick, long readyTick, boolean pinned) {
            this.itemId = itemId;
            this.label = label;
            this.startTick = startTick;
            this.readyTick = readyTick;
            this.pinned = pinned;
        }
    }
}
