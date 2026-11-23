package de.aetherion.bossengine.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exile set pieces without doubles (A-53, decision E-8). A {@code random_<set>_armor} loot roll for a known player
 * gives a piece of that set the player hasn't had in the current round; once all four came, the next round starts.
 * The round is a bit mask in the player's own data ({@code aetherion:set_round_<set>}), so it survives restarts.
 */
final class SetPieceLedger {

    /** Loot id → the four piece ids AetherionItems builds ({@code create<Piece>} on CustomItem). */
    private static final Map<String, String[]> SETS = Map.of(
            "random_hollow_sun_armor", new String[] {
                    "hollow_sun_helmet", "hollow_sun_chestplate", "hollow_sun_leggings", "hollow_sun_boots"},
            "random_seraphine_armor", new String[] {
                    "seraphine_veil", "seraphine_bodice", "seraphine_bell_skirt", "seraphine_pointe_slippers"},
            "random_helios_armor", new String[] {
                    "helios_crown", "helios_heartplate", "helios_orbit_greaves", "helios_dawn_treads"},
            "random_worldhide_armor", new String[] {
                    "worldhide_helmet", "worldhide_chestplate", "worldhide_leggings", "worldhide_boots"});

    /** A piece for this player, or null when the loot id is no tracked set (or nobody to track). */
    record Pick(String lootId, int index, String pieceId) {
    }

    private SetPieceLedger() {
    }

    static Pick pick(Player player, String lootId) {
        if (player == null || lootId == null) {
            return null;
        }
        String id = lootId.toLowerCase(Locale.ROOT);
        String[] pieces = SETS.get(id);
        if (pieces == null) {
            return null;
        }
        int mask = round(player, id, pieces.length);
        List<Integer> open = new ArrayList<>(pieces.length);
        for (int i = 0; i < pieces.length; i++) {
            if ((mask & (1 << i)) == 0) {
                open.add(i);
            }
        }
        int index = open.get(ThreadLocalRandom.current().nextInt(open.size()));
        return new Pick(id, index, pieces[index]);
    }

    /** The piece really dropped: tick it off this round. */
    static void mark(Player player, Pick pick) {
        if (player == null || pick == null) {
            return;
        }
        String[] pieces = SETS.get(pick.lootId());
        int mask = round(player, pick.lootId(), pieces.length) | (1 << pick.index());
        player.getPersistentDataContainer().set(key(pick.lootId()), PersistentDataType.INTEGER, mask);
    }

    /** This round's mask; a full round starts over. */
    private static int round(Player player, String lootId, int size) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer stored = pdc.get(key(lootId), PersistentDataType.INTEGER);
        int full = (1 << size) - 1;
        int mask = stored == null ? 0 : stored & full;
        return mask == full ? 0 : mask;
    }

    private static NamespacedKey key(String lootId) {
        String set = lootId.replace("random_", "").replace("_armor", "");
        return new NamespacedKey("aetherion", "set_round_" + set);
    }
}
