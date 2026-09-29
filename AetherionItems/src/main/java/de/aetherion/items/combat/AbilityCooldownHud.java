package de.aetherion.items.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Per-weapon ability cooldown for the shared ActionBar (HP + CD).
 * HealthListener owns the bar; this keeps an independent gate per item id
 * so swapping weapons preserves each tool's remaining cooldown.
 */
public final class AbilityCooldownHud {

    private static final LegacyComponentSerializer TEXT = LegacyComponentSerializer.legacySection();
    private static final Map<UUID, Map<String, Gate>> GATES = new ConcurrentHashMap<>();
    private static final long READY_FLASH_TICKS = 40L;

    private AbilityCooldownHud() {
    }

    public static void arm(Player player, String itemId, String label, long readyTick) {
        arm(player, itemId, label, readyTick, null);
    }

    /**
     * Start / refresh a cooldown for one weapon. Pass {@code stack} so the
     * vanilla hotbar material cooldown overlay stays in sync.
     */
    public static void arm(Player player, String itemId, String label, long readyTick, ItemStack stack) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        String name = label == null || label.isBlank() ? itemId : label;
        long ready = Math.max(Bukkit.getCurrentTick(), readyTick);
        GATES.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(key, new Gate(key, name, ready));
        applyHotbarCooldown(player, stack, ready);
    }

    public static void armMs(Player player, String itemId, String label, long durationMs) {
        armMs(player, itemId, label, durationMs, null);
    }

    public static void armMs(Player player, String itemId, String label, long durationMs, ItemStack stack) {
        if (durationMs <= 0L) {
            return;
        }
        long ticks = Math.max(1L, (durationMs + 49L) / 50L);
        arm(player, itemId, label, Bukkit.getCurrentTick() + ticks, stack);
    }

    public static void clear(Player player) {
        if (player != null) {
            GATES.remove(player.getUniqueId());
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

    /** Re-apply vanilla hotbar overlay when swapping back onto a cooling weapon. */
    public static void syncHotbar(Player player, String heldItemId, ItemStack stack) {
        if (player == null || heldItemId == null || stack == null) {
            return;
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null) {
            return;
        }
        Gate gate = byItem.get(heldItemId.toLowerCase(Locale.ROOT));
        if (gate == null) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        if (tick < gate.readyTick) {
            applyHotbarCooldown(player, stack, gate.readyTick);
        }
    }

    /** True when the held weapon currently has a CD / ready flash to show. */
    public static boolean showing(Player player, String heldItemId) {
        if (player == null || heldItemId == null || heldItemId.isBlank()) {
            return false;
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null) {
            return false;
        }
        Gate gate = byItem.get(heldItemId.toLowerCase(Locale.ROOT));
        if (gate == null) {
            return false;
        }
        return Bukkit.getCurrentTick() < gate.readyTick + READY_FLASH_TICKS;
    }

    /** Appended by {@link de.aetherion.items.listener.HealthListener} every HUD tick. */
    public static Component fragment(Player player, String heldItemId) {
        return fragment(player, heldItemId, true);
    }

    /**
     * @param withSeparator leading {@code ·} for the no-defense (HP-adjacent) layout
     */
    public static Component fragment(Player player, String heldItemId, boolean withSeparator) {
        if (player == null || heldItemId == null || heldItemId.isBlank()) {
            return Component.empty();
        }
        Map<String, Gate> byItem = GATES.get(player.getUniqueId());
        if (byItem == null || byItem.isEmpty()) {
            return Component.empty();
        }
        prune(byItem);
        if (byItem.isEmpty()) {
            GATES.remove(player.getUniqueId(), byItem);
            return Component.empty();
        }
        Gate gate = byItem.get(heldItemId.toLowerCase(Locale.ROOT));
        if (gate == null) {
            return Component.empty();
        }
        long tick = Bukkit.getCurrentTick();
        Component body;
        if (tick >= gate.readyTick) {
            if (tick >= gate.readyTick + READY_FLASH_TICKS) {
                byItem.remove(gate.itemId, gate);
                return Component.empty();
            }
            body = TEXT.deserialize("§a" + gate.label + " §7ready");
        } else {
            long left = Math.max(1L, (gate.readyTick - tick + 19L) / 20L);
            body = TEXT.deserialize("§e" + gate.label + " §f" + left + "s");
        }
        if (!withSeparator) {
            return body.decoration(TextDecoration.ITALIC, false);
        }
        return Component.text()
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(body)
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static void applyHotbarCooldown(Player player, ItemStack stack, long readyTick) {
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

    private record Gate(String itemId, String label, long readyTick) {
    }
}
