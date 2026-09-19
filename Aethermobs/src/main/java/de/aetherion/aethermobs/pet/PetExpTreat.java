package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Farm Millstone pet EXP treats — right-click to feed active pet. */
public final class PetExpTreat implements Listener {

    public static final String ID_T1 = "pet_exp_treat_1";
    public static final String ID_T2 = "pet_exp_treat_2";
    public static final String ID_T3 = "pet_exp_treat_3";

    private static final NamespacedKey XP_KEY = new NamespacedKey("aethermobs", "pet_exp_amount");

    public static ItemStack create(int tier) {
        int use = Math.max(1, Math.min(3, tier));
        long xp = switch (use) {
            case 2 -> 850L;
            case 3 -> 4200L;
            default -> 180L;
        };
        Rarity rarity = switch (use) {
            case 2 -> Rarity.RARE;
            case 3 -> Rarity.EPIC;
            default -> Rarity.UNCOMMON;
        };
        Material mat = switch (use) {
            case 2 -> Material.GOLDEN_CARROT;
            case 3 -> Material.ENCHANTED_GOLDEN_APPLE;
            default -> Material.APPLE;
        };
        String name = switch (use) {
            case 2 -> "§bPet EXP Treat II";
            case 3 -> "§dPet EXP Treat III";
            default -> "§aPet EXP Treat I";
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(
                    "§7✦ §" + (use == 3 ? "5EPIC" : use == 2 ? "bRARE" : "aUNCOMMON"),
                    "",
                    "§7Feed your active pet §f+" + xp + " EXP§7.",
                    "§8Crafted from Farm Isle pantry stock.",
                    "",
                    "§eRight-click to use"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, "pet_exp_treat_" + use);
            meta.getPersistentDataContainer().set(ItemKeys.rarity(), PersistentDataType.STRING, rarity.name());
            meta.getPersistentDataContainer().set(XP_KEY, PersistentDataType.LONG, xp);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static int tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        if (id == null) {
            return 0;
        }
        return switch (id.toLowerCase()) {
            case ID_T1 -> 1;
            case ID_T2 -> 2;
            case ID_T3 -> 3;
            default -> 0;
        };
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        int tier = tierOf(hand);
        if (tier <= 0) {
            return;
        }
        event.setCancelled(true);
        AetherMobs plugin = AetherMobs.getInstance();
        if (plugin == null || plugin.getActivePetManager() == null) {
            player.sendMessage("§cNo pet system.");
            return;
        }
        PetEntity entity = plugin.getActivePetManager().getActivePet(player);
        if (entity == null || entity.getPetInstance() == null) {
            player.sendMessage("§cEquip a pet first.");
            return;
        }
        PetInstance pet = entity.getPetInstance();
        Long amount = hand.getItemMeta().getPersistentDataContainer().get(XP_KEY, PersistentDataType.LONG);
        long xp = amount == null ? 180L : amount;
        int previousLevel = pet.getLevel();
        pet.addExperience(xp);
        hand.setAmount(hand.getAmount() - 1);
        String label = pet.getDefinition() == null ? "Pet" : pet.getDefinition().getDisplayName();
        player.sendMessage("§a✦ §f" + label + " §7gained §a+" + xp + " EXP§7.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.5f);
        if (pet.getLevel() > previousLevel) {
            entity.updateDisplayName();
            var collection = plugin.getPetCollection(player);
            if (collection != null) {
                plugin.getPetDataManager().save(collection);
            }
            player.sendMessage("§d✦ §f" + label + " §7reached §eLevel " + pet.getLevel() + "§7!");
        } else {
            plugin.markPetsDirty(player.getUniqueId());
        }
    }
}
