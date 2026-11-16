package de.aetherion.items.manager;

import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player has equipped, read once per item instead of once per question.
 *
 * <p>Every read off an {@link ItemStack} copies its whole meta, and stats, set bonuses and the HUD ask about the
 * same six items many times a second. The answers are kept per slot and dropped the moment the item in that slot
 * no longer equals the one they were read from: a swap, an upgrade, a new booster, a durability change. Nothing
 * here is timed and nothing waits for an event, so an answer is never out of date; an unchanged item simply
 * costs one comparison instead of a meta copy.
 *
 * <p>Only facts that depend on the item alone belong here. Whatever depends on the player or the world (dungeon
 * or overworld, buffs, pets) stays with the caller.
 */
public final class EquippedGear {

    /** The slots that count as equipped, in the order stats have always been summed. */
    public enum Slot {
        MAIN_HAND,
        OFF_HAND,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS;

        private ItemStack read(PlayerInventory inventory) {
            return switch (this) {
                case MAIN_HAND -> inventory.getItemInMainHand();
                case OFF_HAND -> inventory.getItemInOffHand();
                case HELMET -> inventory.getHelmet();
                case CHESTPLATE -> inventory.getChestplate();
                case LEGGINGS -> inventory.getLeggings();
                case BOOTS -> inventory.getBoots();
            };
        }
    }

    /** Helmet, chestplate, leggings, boots. */
    public static final List<Slot> ARMOR = List.of(Slot.HELMET, Slot.CHESTPLATE, Slot.LEGGINGS, Slot.BOOTS);

    /** Every equipped slot. */
    public static final List<Slot> ALL = List.of(Slot.values());

    /**
     * Something read off one item that only changes when the item does. Keep each fact in a constant: answers are
     * remembered per instance.
     */
    @FunctionalInterface
    public interface Fact<T> {
        T read(ItemStack item, ItemManager items);
    }

    /** One stat of one item, in the dungeon or in the overworld. */
    @FunctionalInterface
    interface StatReader {
        double read(ItemStack item, ItemCapability capability, boolean dungeon);
    }

    private static final int CAPABILITIES = ItemCapability.values().length;
    private static final Object NOTHING = new Object();
    private static final Map<UUID, EquippedGear> PLAYERS = new ConcurrentHashMap<>();

    private final Worn[] worn = new Worn[Slot.values().length];

    private EquippedGear() {
        for (int i = 0; i < worn.length; i++) {
            worn[i] = new Worn();
        }
    }

    /** Drops a player's entry once they have left. */
    public static void register(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new QuitCleanup(plugin), plugin);
    }

    /** Aetherion item id in that slot; null for an empty slot or a plain item. */
    public static String itemId(Player player, Slot slot, ItemManager items) {
        if (player == null || slot == null || items == null) {
            return null;
        }
        EquippedGear gear = of(player);
        synchronized (gear) {
            Worn piece = gear.current(player, slot);
            if (piece.item == null) {
                return null;
            }
            if (!piece.idRead) {
                piece.itemId = items.getItemId(piece.item);
                piece.idRead = true;
            }
            return piece.itemId;
        }
    }

    /** A fact about the item in that slot; null for an empty slot. */
    @SuppressWarnings("unchecked")
    public static <T> T fact(Player player, Slot slot, ItemManager items, Fact<T> fact) {
        if (player == null || slot == null || items == null || fact == null) {
            return null;
        }
        EquippedGear gear = of(player);
        synchronized (gear) {
            Worn piece = gear.current(player, slot);
            if (piece.item == null) {
                return null;
            }
            if (piece.facts == null) {
                piece.facts = new IdentityHashMap<>(4);
            }
            Object known = piece.facts.get(fact);
            if (known == null) {
                T read = fact.read(piece.item, items);
                piece.facts.put(fact, read == null ? NOTHING : read);
                return read;
            }
            return known == NOTHING ? null : (T) known;
        }
    }

    /**
     * One stat of the item in that slot. Every caller reads stats the same way, so the answer is kept per slot,
     * capability and dungeon flag, whoever asked first.
     */
    static double stat(Player player, Slot slot, ItemCapability capability, boolean dungeon, StatReader reader) {
        EquippedGear gear = of(player);
        synchronized (gear) {
            Worn piece = gear.current(player, slot);
            if (piece.item == null) {
                return 0.0;
            }
            double[] known = dungeon ? piece.dungeonStats : piece.overworldStats;
            if (known == null) {
                known = new double[CAPABILITIES];
                Arrays.fill(known, Double.NaN);
                if (dungeon) {
                    piece.dungeonStats = known;
                } else {
                    piece.overworldStats = known;
                }
            }
            double value = known[capability.ordinal()];
            if (Double.isNaN(value)) {
                value = reader.read(piece.item, capability, dungeon);
                known[capability.ordinal()] = value;
            }
            return value;
        }
    }

    /** Forgets one player. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            PLAYERS.remove(playerId);
        }
    }

    /** Forgets everyone (plugin disable, item definitions reloaded). */
    public static void clear() {
        PLAYERS.clear();
    }

    private static EquippedGear of(Player player) {
        UUID id = player.getUniqueId();
        EquippedGear gear = PLAYERS.get(id);
        if (gear == null) {
            if (!player.isOnline()) {
                // Not kept: nothing would drop it again once the quit cleanup has run.
                return new EquippedGear();
            }
            gear = PLAYERS.computeIfAbsent(id, key -> new EquippedGear());
        }
        return gear;
    }

    /** The slot's entry, emptied first if the item in the slot is not the one it was read from. */
    private Worn current(Player player, Slot slot) {
        Worn piece = worn[slot.ordinal()];
        ItemStack now = slot.read(player.getInventory());
        if (now == null || now.getType().isAir()) {
            if (piece.item != null) {
                piece.reset(null);
            }
            return piece;
        }
        if (piece.item == null || !piece.item.equals(now)) {
            // A private copy: the stack in the inventory can be edited in place, this one never is.
            piece.reset(now.clone());
        }
        return piece;
    }

    private static final class Worn {
        private ItemStack item;
        private boolean idRead;
        private String itemId;
        private double[] overworldStats;
        private double[] dungeonStats;
        private Map<Fact<?>, Object> facts;

        private void reset(ItemStack next) {
            item = next;
            idRead = false;
            itemId = null;
            overworldStats = null;
            dungeonStats = null;
            facts = null;
        }
    }

    /** Public so the event system can reach the handler. */
    public static final class QuitCleanup implements Listener {
        private final JavaPlugin plugin;

        private QuitCleanup(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            if (!plugin.isEnabled()) {
                forget(id);
                return;
            }
            // Other quit handlers may still ask about this player's gear, so drop the entry after them.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player back = Bukkit.getPlayer(id);
                if (back == null || !back.isOnline()) {
                    forget(id);
                }
            });
        }
    }
}
