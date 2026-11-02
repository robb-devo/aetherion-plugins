package de.aetherion.quests.tutorial;

import de.aetherion.core.api.ChromePage;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Temper's first booster (lesson {@code lesson_boost}): the player picks one of three from a small chest, each
 * with one plain line on what it does, instead of being handed an Emerald Booster nobody could explain. Closing
 * the chest without a pick hands over the Iron one, so nobody leaves without a booster.
 */
public final class BoosterPick implements Listener {

    private static final int[] SLOTS = {11, 13, 15};
    private static final String[] IDS = {"iron_booster", "lapis_booster", "emerald_booster"};
    private static final String DEFAULT_ID = "iron_booster";
    private static final long OPEN_DELAY = 40L;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean picked;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Opens the pick a moment after Temper's offer (his line types out first). */
    public static void offer(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (player == null || plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        }, OPEN_DELAY);
    }

    private static void open(Player player) {
        boolean german = de.aetherion.core.exile.Say.german(player);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                net.kyori.adventure.text.Component.text(german ? "Wähl deinen ersten Booster" : "Pick your first booster"));
        holder.inventory = inventory;
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack shown = booster(IDS[i]);
            if (shown == null) {
                // Items missing: no pick, the old gift
                give(player, DEFAULT_ID);
                return;
            }
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(what(player, IDS[i]));
                lore.add("");
                lore.add(german ? "§eKlick: der gehört dir" : "§eClick: it's yours");
                if (meta.getLore() != null) {
                    lore.add("");
                    lore.addAll(meta.getLore());
                }
                meta.setLore(lore);
                shown.setItemMeta(meta);
            }
            inventory.setItem(SLOTS[i], shown);
        }
        ChromePage.open(player, inventory, ChromePage.Theme.FORGE);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.6f);
    }

    /** One line on what the booster does, in words a new player knows. */
    private static String what(Player player, String id) {
        return switch (id) {
            case "lapis_booster" -> de.aetherion.core.exile.Say.t(player,
                    "§f+Health §7on whatever you socket it into.",
                    "§f+Leben §7auf dem Teil, in das du ihn steckst.");
            case "emerald_booster" -> de.aetherion.core.exile.Say.t(player,
                    "§fSpread: §7mining or chopping also breaks the blocks next to it.",
                    "§fSpread: §7Abbauen oder Hacken bricht auch die Blöcke daneben.");
            default -> de.aetherion.core.exile.Say.t(player,
                    "§fMore of what the piece does: §7Damage on a sword, Defense on armour, Mining Power on a pick.",
                    "§fMehr von dem, was das Teil kann: §7Schaden am Schwert, Verteidigung an Rüstung, Mining Power an der Spitzhacke.");
        };
    }

    private static ItemStack booster(String id) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getItemManager() == null) {
                return null;
            }
            de.aetherion.items.item.CustomItem custom = new de.aetherion.items.item.CustomItem(items.getItemManager());
            return switch (id) {
                case "lapis_booster" -> custom.createLapisBooster();
                case "emerald_booster" -> custom.createEmeraldBooster();
                default -> custom.createIronBooster();
            };
        } catch (LinkageError ex) {
            return null;
        }
    }

    private static void give(Player player, String id) {
        ItemStack booster = booster(id);
        if (booster == null) {
            return;
        }
        try {
            de.aetherion.items.storage.BoosterDelivery.giveQuestReward(player, booster);
        } catch (LinkageError ex) {
            player.getInventory().addItem(booster).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || holder.picked
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == event.getSlot()) {
                holder.picked = true;
                give(player, IDS[i]);
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.2f);
                player.closeInventory();
                temper(player, LangPack.say(player, "booster_tutor.picked",
                        "Good pick. Nether Star → §eAnvil§f: gear left, booster right."));
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || holder.picked
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        holder.picked = true;
        give(player, DEFAULT_ID);
        temper(player, LangPack.say(player, "booster_tutor.picked_default",
                "No pick? Iron, then: more of what the piece does. Nether Star → §eAnvil§f: gear left, booster right."));
    }

    private static void temper(Player player, String line) {
        LivingNpcProfile.say(player, "booster_tutor", "Temper", line);
    }
}
