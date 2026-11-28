package de.aetherion.tutorial.isle;

import de.aetherion.core.api.ChromePage;
import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.text.Component;

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
 * Ansel's gift: one booster of every kind, laid out on one page with a line each on what it does. The page is the
 * harbour's (Temper's) page in Ansel's hands: same twelve, same words, his voice at the end.
 */
final class BoosterGift implements Listener {

    /** Twelve kinds: the core four on the top row, the eight specials below. */
    private static final int[] SLOTS = {10, 11, 12, 13, 18, 19, 20, 21, 22, 23, 24, 25};
    private static final int TAKE_SLOT = 16;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean given;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TutorialPlugin plugin;
    private final Isle isle;

    BoosterGift(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
    }

    /** Opens the page; the set is handed over when it closes, however it closes. */
    boolean open(Player player) {
        List<ItemStack> boosters = all();
        if (boosters.isEmpty()) {
            return false;
        }
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 36, Component.text(
                Txt.t(player, "Ansel's boosters: one of each", "Ansels Booster: einer von jeder Sorte")));
        holder.inventory = inventory;
        de.aetherion.items.model.BoosterType[] types = de.aetherion.items.model.BoosterType.values();
        for (int i = 0; i < types.length && i < SLOTS.length && i < boosters.size(); i++) {
            ItemStack shown = boosters.get(i).clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Txt.legacy(what(player, types[i])));
                lore.add(Component.empty());
                lore.add(Txt.legacy(Txt.t(player, "§8All twelve are yours.", "§8Alle zwölf gehören dir.")));
                meta.lore(lore);
                shown.setItemMeta(meta);
            }
            inventory.setItem(SLOTS[i], shown);
        }
        ItemStack take = new ItemStack(Material.CHEST);
        ItemMeta takeMeta = take.getItemMeta();
        if (takeMeta != null) {
            takeMeta.displayName(Txt.legacy(Txt.t(player, "§aTake all twelve", "§aAlle zwölf nehmen")));
            takeMeta.lore(List.of(
                    Txt.legacy(Txt.t(player, "§7Put them in at the §eanvil§7: gear on the seal,", "§7Einsetzen am §eAmboss§7: Teil aufs Siegel,")),
                    Txt.legacy(Txt.t(player, "§7booster into a free slot below it.", "§7Booster in einen freien Platz darunter.")),
                    Txt.legacy(Txt.t(player, "§7They come out again with one click.", "§7Mit einem Klick kommen sie wieder raus."))));
            take.setItemMeta(takeMeta);
        }
        inventory.setItem(TAKE_SLOT, take);
        try {
            ChromePage.open(player, inventory, ChromePage.Theme.FORGE);
        } catch (LinkageError | RuntimeException ex) {
            player.openInventory(inventory);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.6f);
        return true;
    }

    /** The set without the page (the short way for those who have seen it). */
    void give(Player player) {
        for (ItemStack booster : all()) {
            try {
                de.aetherion.items.storage.BoosterDelivery.giveQuestReward(player, booster);
            } catch (LinkageError | RuntimeException ex) {
                player.getInventory().addItem(booster).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
    }

    /** One line on what each kind does, in words a newcomer knows. */
    private static String what(Player player, de.aetherion.items.model.BoosterType type) {
        return switch (type) {
            case COAL, IRON, GOLD, DIAMOND -> Txt.t(player,
                    "§fMore of what the piece does §7(Damage on a sword, Defense on armour, Mining Power on a pickaxe). "
                            + "§8Coal < Iron < Gold < Diamond.",
                    "§fMehr von dem, was das Teil kann §7(Schaden am Schwert, Verteidigung an Rüstung, Mining Power an der "
                            + "Spitzhacke). §8Kohle < Eisen < Gold < Diamant.");
            case EMERALD -> Txt.t(player, "§fSpread: §7mining or chopping also breaks the blocks next to it.",
                    "§fSpread: §7Abbauen oder Hacken bricht auch die Blöcke daneben.");
            case REDSTONE -> Txt.t(player, "§fSweep: §7your hits reach more enemies at once.",
                    "§fSweep: §7deine Schläge treffen mehrere Gegner auf einmal.");
            case LAPIS -> Txt.t(player, "§f+Health §7on whatever you put it in.", "§f+Leben §7auf dem Teil, in das du ihn steckst.");
            case GLOWSTONE -> Txt.t(player, "§f+Speed §7(boots and gear).", "§f+Tempo §7(Stiefel und Ausrüstung).");
            case WHEAT -> Txt.t(player, "§fPet catching: §7wild pets are easier to catch.",
                    "§fPets fangen: §7wilde Pets lassen sich leichter fangen.");
            case CARROT -> Txt.t(player, "§fHarvest: §7a swing on a field takes more ripe crops.",
                    "§fErnte: §7ein Schlag aufs Feld erntet mehr reife Pflanzen.");
            case OAK -> Txt.t(player, "§fCrit Damage: §7critical hits hit harder.", "§fKrit-Schaden: §7kritische Treffer treffen härter.");
            case BIRCH -> Txt.t(player, "§fCrit Chance: §7more of your hits are critical.", "§fKrit-Chance: §7mehr Treffer werden kritisch.");
        };
    }

    private static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getCustomItem() == null) {
                return out;
            }
            for (de.aetherion.items.model.BoosterType type : de.aetherion.items.model.BoosterType.values()) {
                ItemStack booster = items.getCustomItem().booster(type);
                if (booster != null) {
                    ItemStack one = booster.clone();
                    one.setAmount(1);
                    out.add(one);
                }
            }
        } catch (LinkageError ex) {
            return new ArrayList<>();
        }
        return out;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || holder.given
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        boolean booster = false;
        for (int s : SLOTS) {
            booster |= s == slot;
        }
        if (booster || slot == TAKE_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder) || holder.given
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        holder.given = true;
        give(player);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.2f);
        // one tick on: the page is really shut before Ansel speaks again
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                isle.director().boostersTaken(player);
            }
        });
    }
}
