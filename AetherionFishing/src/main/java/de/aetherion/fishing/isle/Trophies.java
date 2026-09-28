package de.aetherion.fishing.isle;

import de.aetherion.fishing.AetherionFishing;
import de.aetherion.fishing.LureHead;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Trophy fish — the heavy ones (and every rare or legendary) come home as a mounted head with the
 * weight on the tag. Odile at the Trophy House buys them by weight. Heads never place: a placed
 * head would drop its tag.
 */
public final class Trophies implements Listener {

    /** Commons, uncommons and run fish become trophies from this share of their weight range. */
    public static final double HEAVY_SHARE = 0.9d;
    public static final long SCALE_VALUE = 750L;

    private final NamespacedKey speciesKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey scaleKey;

    Trophies(AetherionFishing plugin) {
        this.speciesKey = new NamespacedKey(plugin, "trophy_species");
        this.weightKey = new NamespacedKey(plugin, "trophy_kg");
        this.scaleKey = new NamespacedKey(plugin, "eldermaw_scale");
    }

    /** True when a catch of this weight comes home as a trophy. */
    public static boolean isTrophy(Species species, double kg) {
        return species.rarity().alwaysTrophy() || species.share(kg) >= HEAVY_SHARE;
    }

    public ItemStack item(Species species, double kg) {
        ItemStack item = LureHead.of(species.look());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        double rounded = Math.round(kg * 100.0d) / 100.0d;
        meta.setDisplayName("§6✦ " + species.colored() + " §6Trophy");
        List<String> lore = new ArrayList<>();
        lore.add("§8" + species.rarity().label() + " · Fishing Eldervale");
        lore.add("§7Weight: §f" + LakeText.kg(rounded) + " §8(" + band(species, rounded) + "§8)");
        lore.add("");
        lore.add("§8\"" + species.line() + "\"");
        lore.add("");
        lore.add("§7Sells for §6" + LakeText.coins(value(species, rounded)) + " coins");
        lore.add("§7at the §dTrophy House §7(Odile).");
        lore.add("");
        lore.add("§8Trophy Fish");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(speciesKey, PersistentDataType.STRING, species.id());
        pdc.set(weightKey, PersistentDataType.DOUBLE, rounded);
        item.setItemMeta(meta);
        return item;
    }

    /** The Eldermaw's scale — the top hauler's keepsake. */
    public ItemStack scale() {
        ItemStack item = new ItemStack(org.bukkit.Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName("§5✦ Eldermaw Scale");
        meta.setLore(List.of(
                "§8Hauled out of Fishing Eldervale",
                "",
                "§7Big as a door, cold as the deep.",
                "§7The whole lake pulled — you pulled hardest.",
                "",
                "§7Sells for §6" + LakeText.coins(SCALE_VALUE) + " coins §7at the §dTrophy House§7.",
                "",
                "§8Trophy"
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(scaleKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public Species speciesOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Species.byId(item.getItemMeta().getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING));
    }

    public double kgOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0d;
        }
        Double kg = item.getItemMeta().getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
        return kg == null ? 0.0d : kg;
    }

    public boolean isScale(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(scaleKey, PersistentDataType.BYTE);
    }

    /** Coins for one trophy: rarity base × (0.6 + weight share). */
    public static long value(Species species, double kg) {
        return Math.round(species.rarity().trophyBase() * (0.6d + species.share(kg)));
    }

    /** Coins this stack fetches at the Trophy House (0 for anything else). */
    public long valueOf(ItemStack item) {
        if (isScale(item)) {
            return SCALE_VALUE * item.getAmount();
        }
        Species species = speciesOf(item);
        return species == null ? 0L : value(species, kgOf(item)) * item.getAmount();
    }

    /** Sell every trophy the player carries. Returns {count, coins}. */
    public long[] sellAll(Player player) {
        long coins = 0L;
        long count = 0L;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            long value = valueOf(contents[slot]);
            if (value <= 0L) {
                continue;
            }
            coins += value;
            count += contents[slot].getAmount();
            contents[slot] = null;
        }
        if (count > 0L) {
            player.getInventory().setStorageContents(contents);
        }
        return new long[]{count, coins};
    }

    private static String band(Species species, double kg) {
        double share = species.share(kg);
        if (share >= 0.9d) {
            return "§6record class";
        }
        if (share >= 0.75d) {
            return "§egold weight";
        }
        if (share >= 0.5d) {
            return "§fsilver weight";
        }
        return "§7bronze weight";
    }

    /** Weight tier 0–3 (bronze / silver / gold / record class) — Log points come from these. */
    public static int tier(Species species, double kg) {
        double share = species.share(kg);
        return share >= 0.9d ? 3 : share >= 0.75d ? 2 : share >= 0.5d ? 1 : 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (speciesOf(hand) != null) {
            event.setCancelled(true);
            LakeText.bar(event.getPlayer(), "§7Trophies go on the wall at the §dTrophy House§7, not on the floor.");
        }
    }
}
