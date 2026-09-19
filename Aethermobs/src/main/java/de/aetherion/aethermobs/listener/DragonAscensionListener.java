package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.ActivePetManager;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.items.model.Rarity;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Provisional DEV ritual: Mythic dragon → Aethered (max level 200).
 */
public final class DragonAscensionListener implements Listener {

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("aetherion", "item");
    public static final String ITEM_ID = "dragon_ascension_vial";

    private final AetherMobs plugin;

    public DragonAscensionListener(AetherMobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isVial(hand)) {
            return;
        }
        event.setCancelled(true);

        ActivePetManager activePets = plugin.getActivePetManager();
        if (activePets == null) {
            player.sendMessage("§cNo active pet.");
            return;
        }
        PetEntity active = activePets.getActivePet(player);
        if (active == null) {
            player.sendMessage("§cEquip a Mythic dragon first.");
            return;
        }
        PetInstance pet = active.getPetInstance();
        if (pet == null || !pet.isDragon()) {
            player.sendMessage("§cOnly dragons can be Aethered.");
            return;
        }
        if (pet.getRarity() == Rarity.AETHERED) {
            player.sendMessage("§cAlready Aethered.");
            return;
        }
        if (pet.getRarity() != Rarity.MYTHIC) {
            player.sendMessage("§cDragons must be Mythic before ascension.");
            return;
        }
        if (!pet.ascendToAethered()) {
            player.sendMessage("§cAscension failed.");
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        plugin.markPetsDirty(player.getUniqueId());
        active.updateDisplayName();
        player.sendMessage("§4§lAETHERED §7— " + pet.getDefinition().getDisplayName()
                + " §7can now reach level §f" + pet.getMaxLevel() + "§7.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.45f, 1.4f);
        if (active.isSpawned()) {
            active.getEntity().getWorld().spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    active.getEntity().getLocation().add(0, 1.0, 0),
                    40,
                    0.45,
                    0.6,
                    0.45,
                    0.02
            );
        }
    }

    private static boolean isVial(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        return ITEM_ID.equalsIgnoreCase(id);
    }
}
