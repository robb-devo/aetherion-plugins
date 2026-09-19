package de.aetherion.items.shop;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.ItemPresentation;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class AetherBloodVial implements Listener {

    public static final String ID = "aetherion_blood_vial";
    public static final String LESSER_ID = "aetherion_blood_vial_lesser";
    public static final long PRICE = 500L;
    public static final long LESSER_PRICE = 120L;
    public static final long LESSER_DURATION_MS = 3L * 60L * 60L * 1000L;

    public static ItemStack create() {
        return createPhial(ID, "§5Phial of Aetherion's Blood", "§7✦ §dEPIC",
                "§7A polite sip. Then everything levels faster.",
                XpBoosterService.DURATION_MS);
    }

    public static ItemStack createLesser() {
        return createPhial(LESSER_ID, "§dLesser Phial of Aetherion's Blood", "§7✦ §9RARE",
                "§7A smaller sip. Same bad idea, shorter shift.",
                LESSER_DURATION_MS);
    }

    private static ItemStack createPhial(String id, String name, String rarityLine, String flavor, long durationMs) {
        ItemStack item = new ItemStack(Material.POTION);
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return item;
        }
        long hours = Math.max(1L, durationMs / (60L * 60L * 1000L));
        meta.setDisplayName(name);
        meta.setLore(java.util.List.of(
                rarityLine,
                "",
                flavor,
                "§d+5% XP §7on skills, Aetherion Level, vanilla XP.",
                "§d+4% damage§7, §d+3% defense§7, §d+5% coins§7.",
                "§8No potion icons. No night vision pulse.",
                "",
                "§7Duration: §f" + hours + " hours of playtime",
                "§8Not real time. AFK in the lobby does not count.",
                "",
                "§eRight-click to drink."
        ));
        meta.setColor(Color.fromRGB(128, 12, 36));
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        GuiItems.hideVanilla(meta);
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isVial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        return ID.equals(id) || LESSER_ID.equals(id);
    }

    public static boolean isLesser(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        return LESSER_ID.equals(id);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isVial(item)) {
            return;
        }
        event.setCancelled(true);
        drink(event.getPlayer(), isLesser(item));
        consumeOne(event.getPlayer(), item);
    }

    private static void drink(Player player, boolean lesser) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.xpBoost() == null) {
            return;
        }
        long duration = lesser ? LESSER_DURATION_MS : XpBoosterService.DURATION_MS;
        plugin.xpBoost().addPlaytime(player, duration);
        bless(player);
        player.sendMessage(lesser
                ? "§dThe lesser phial tastes like a shorter bad idea."
                : "§5The phial tastes like a bad idea that worked.");
        player.sendMessage(XpBoosterService.buffSummary() + " §7for §f" + plugin.xpBoost().formatted(player) + "§7.");
        player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1.0f, 0.7f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f, 1.6f);
    }

    private static final PotionEffectType[] STRIP = {
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.CONDUIT_POWER,
            PotionEffectType.DOLPHINS_GRACE,
            PotionEffectType.HERO_OF_THE_VILLAGE
    };

    public static void bless(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (PotionEffectType type : STRIP) {
            if (type == null) {
                continue;
            }
            PotionEffect existing = player.getPotionEffect(type);
            if (existing != null && existing.getAmplifier() <= 0) {
                player.removePotionEffect(type);
            }
        }
    }

    private static void consumeOne(Player player, ItemStack item) {
        int amount = item.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }
        player.updateInventory();
    }
}
