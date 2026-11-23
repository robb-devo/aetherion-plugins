package de.aetherion.core.exile;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;

/**
 * The Exile's boss vials: one per boss site. Handed in at the site's altar (sword, music box, star ring, Maw,
 * Great Doors) it is used up and the boss wakes. Since A-53 the Keeper of The Last Bell brews them (Quests
 * {@code ExileBrewer}): isle finds plus coins for the four bosses, one Echo of each for Aetherion. Each of the four
 * leaves an Echo when it falls. Staff still hand vials out with {@code /exile vial}. Not the Borderlands spirit
 * vials: own key, own look, never accepted by that rite.
 * <p>
 * The keys are {@code aetherion:exile_vial} and {@code aetherion:exile_echo} (boss id as string) so other plugins can
 * make or read one without Core.
 */
public final class ExileVials implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("aetherion", "exile_vial");
    public static final NamespacedKey ECHO_KEY = new NamespacedKey("aetherion", "exile_echo");

    /** {@code site} is the ExileDoors ritual id the vial is handed in at. */
    public record Vial(String boss, String site, String nameEn, String nameDe, String color, Color tint,
                       String placeEn, String placeDe) {
    }

    public static final List<Vial> ALL = List.of(
            new Vial("hollow_sun", "caedmon_sword", "The Hollow Sun", "Die Hollow Sun", "§6", Color.fromRGB(255, 170, 40),
                    "Caedmon's sword in the crater", "Caedmons Schwert im Krater"),
            new Vial("hanging_saint", "music_box", "Seraphine", "Seraphine", "§d", Color.fromRGB(230, 120, 220),
                    "the music box on Elin's stage", "die Spieluhr auf Elins Bühne"),
            new Vial("helios", "star_ring", "Helios", "Helios", "§e", Color.fromRGB(255, 240, 120),
                    "the star ring in the observatory", "den Sternenring im Observatorium"),
            new Vial("world_eater", "the_maw", "Nihil & the Unbroken", "Nihil & der Unbroken", "§2", Color.fromRGB(70, 160, 70),
                    "the Maw in the Root-Ground", "den Schlund im Wurzelgrund"),
            new Vial("aetherion", "great_doors", "Aetherion", "Aetherion", "§5", Color.fromRGB(150, 70, 220),
                    "the Great Doors", "die Großen Tore")
    );

    public static Vial byBoss(String boss) {
        if (boss == null) {
            return null;
        }
        String id = boss.toLowerCase(Locale.ROOT);
        for (Vial v : ALL) {
            if (v.boss().equals(id) || v.site().equals(id)) {
                return v;
            }
        }
        return switch (id) {
            case "seraphine", "saint" -> byBoss("hanging_saint");
            case "nihil", "unbroken", "last_seed" -> byBoss("world_eater");
            case "dungeon_aetherion", "finale" -> byBoss("aetherion");
            default -> null;
        };
    }

    public static Vial bySite(String site) {
        for (Vial v : ALL) {
            if (v.site().equals(site)) {
                return v;
            }
        }
        return null;
    }

    public static ItemStack create(Vial vial, int amount, boolean german) {
        ItemStack item = new ItemStack(Material.POTION, Math.max(1, Math.min(16, amount)));
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = german ? vial.nameDe() : vial.nameEn();
        meta.setDisplayName(vial.color() + "§l" + (german ? "Phiole" : "Vial") + " §8· §f" + name);
        meta.setLore(List.of(
                german ? "§7Eine Phiole aus dem Exil. Sie weckt" : "§7A vial from the Exile. It wakes",
                vial.color() + name + (german ? "§7." : "§7."),
                "",
                german ? "§eAbgeben: §7" + vial.placeDe() + "." : "§eHand in at §7" + vial.placeEn() + ".",
                german ? "§8Wird verbraucht. Nicht trinkbar." : "§8Used up. Not drinkable."));
        meta.setColor(vial.tint());
        try {
            meta.setBasePotionType(PotionType.WATER);
        } catch (Throwable ignored) {
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(16);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, vial.boss());
        item.setItemMeta(meta);
        return item;
    }

    /** The four bosses that leave an Echo; Aetherion's vial is brewed from one Echo of each. */
    public static List<Vial> echoBosses() {
        return ALL.subList(0, 4);
    }

    /** What is left of a fallen exile boss. Four different ones make the vial for the Great Doors. */
    public static ItemStack createEcho(Vial vial, int amount, boolean german) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = german ? vial.nameDe() : vial.nameEn();
        meta.setDisplayName(vial.color() + "§lEcho §8· §f" + name);
        meta.setLore(List.of(
                german ? "§7Was von " + vial.color() + name + " §7übrig blieb." : "§7What was left of " + vial.color() + name + "§7.",
                "",
                german ? "§eVier verschiedene Echos: §7der Wirt der" : "§eFour different Echoes: §7the Keeper",
                german ? "§7Letzten Glocke braut daraus die Phiole" : "§7of The Last Bell brews the vial",
                german ? "§7für die Großen Tore." : "§7for the Great Doors from them."));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(ECHO_KEY, PersistentDataType.STRING, vial.boss());
        item.setItemMeta(meta);
        return item;
    }

    /** The boss an Echo came from, or null for anything else. */
    public static String echoOf(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ECHO_KEY, PersistentDataType.STRING);
    }

    /** The boss id a vial wakes, or null for anything else. */
    public static String bossOf(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /** Takes one vial for {@code boss} from the main hand; false (and nothing taken) when it is not there. */
    public static boolean takeFromHand(Player player, String boss) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (boss == null || !boss.equals(bossOf(hand))) {
            return false;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        return true;
    }

    // ------------------------------------------------------------------ not a drink, not a compass

    /** Echoes are echo shards underneath: never let them vanish into a recovery compass. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (echoOf(stack) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (bossOf(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    /** No drinking wind-up in the air or against a wall; the altars are Interaction hitboxes and still answer. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (bossOf(event.getItem()) == null) {
            return;
        }
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }
}
