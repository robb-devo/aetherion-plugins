package de.aetherion.guilds.menu;

import de.aetherion.core.api.ChromePage;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.island.UnlockService;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The claim moment: three authored starters side by side. Before Level 20 the same screen opens as a locked
 * preview (foreshadow), so {@code /island} always shows what's waiting.
 */
public final class StarterSelectMenu implements MenuKit.Menu {

    private static final int[] SLOTS = {11, 13, 15};
    private static final int CLOSE = 22;

    private final PersonalIslandService personal;
    private final UnlockService unlock;

    public StarterSelectMenu(PersonalIslandService personal, UnlockService unlock) {
        this.personal = personal;
        this.unlock = unlock;
    }

    public void open(Player player) {
        if (personal.byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§7Your island is already claimed. §f/island home");
            return;
        }
        boolean open = AetherionItemsAccess.islandUnlocked(player);
        Inventory inventory = MenuKit.create(this, open, 27, open ? "§8✦ Choose your Starter" : "§8Your Island §7(locked)");
        inventory.setItem(4, open
                ? MenuKit.glow(MenuKit.named(Material.NETHER_STAR, "§6✦ Your Island Awaits ✦",
                "§7Pick one of three starters.",
                "§7It's yours to grow: buy land,",
                "§7build huts, run quarries on belts.",
                "",
                "§8The starter can't be swapped later."))
                : MenuKit.named(Material.CLOCK, "§7Your Island §8(locked)",
                AetherionItemsAccess.islandHint(),
                "",
                "§7This is what waits for you."));
        List<StarterLayout> choices = StarterLayout.personalChoices();
        for (int i = 0; i < choices.size(); i++) {
            StarterLayout starter = choices.get(i);
            List<String> lore = new ArrayList<>(starter.lore());
            lore.add("");
            lore.add(open ? "§a▶ Click to claim" : "§cUnlocks at Aetherion Level 20");
            inventory.setItem(SLOTS[i], open
                    ? MenuKit.glow(MenuKit.named(starter.icon(), "§e" + starter.display(), lore))
                    : MenuKit.named(starter.icon(), "§7" + starter.display(), lore));
        }
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        ChromePage.open(player, inventory, ChromePage.Theme.ISLAND);
        player.playSound(player.getLocation(), open ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private static final int GATE_CLAIM = 11;
    private static final int GATE_INFO = 13;
    private static final int GATE_BACK = 15;

    /**
     * Walked through a starter's gate on the Isle Dock: just that one, with a plain yes / not this one (the
     * choice is for good, so it always asks once). Locked before Level 20: the same screen says when it opens.
     */
    public void openGate(Player player, StarterLayout starter) {
        if (personal.byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§7Your island is already claimed. §f/island home");
            return;
        }
        boolean open = AetherionItemsAccess.islandUnlocked(player);
        Inventory inventory = MenuKit.create(this, starter, 27, "§8✦ " + starter.display());
        List<String> lore = new ArrayList<>(starter.lore());
        lore.add("");
        lore.add("§8The starter can't be swapped later.");
        inventory.setItem(GATE_INFO, MenuKit.glow(MenuKit.named(starter.icon(), "§e§l" + starter.display(), lore)));
        if (open) {
            inventory.setItem(GATE_CLAIM, MenuKit.glow(MenuKit.named(Material.LIME_STAINED_GLASS_PANE,
                    "§a§l✔ Make this my island", "§7Your " + starter.display() + " rises now", "§7and you land on it.")));
        } else {
            inventory.setItem(GATE_CLAIM, MenuKit.named(Material.GRAY_STAINED_GLASS_PANE, "§7Not open yet",
                    AetherionItemsAccess.islandHint(), "", "§7This is what waits for you."));
        }
        inventory.setItem(GATE_BACK, MenuKit.named(Material.RED_STAINED_GLASS_PANE, "§c✖ Not this one",
                "§7Step back and look at the others."));
        ChromePage.open(player, inventory, ChromePage.Theme.ISLAND);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        if (holder.context() instanceof StarterLayout starter) {
            if (slot == GATE_BACK) {
                player.closeInventory();
                return;
            }
            if (slot != GATE_CLAIM) {
                return;
            }
            if (!AetherionItemsAccess.islandUnlocked(player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                player.sendMessage(AetherionItemsAccess.islandHint());
                return;
            }
            player.closeInventory();
            PersonalIsland island = personal.createStarter(player, starter);
            if (island != null) {
                unlock.claim(player, island, starter);
            }
            return;
        }
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        int index = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        StarterLayout starter = StarterLayout.personalChoices().get(index);
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            player.sendMessage(AetherionItemsAccess.islandHint());
            return;
        }
        player.closeInventory();
        PersonalIsland island = personal.createStarter(player, starter);
        if (island != null) {
            unlock.claim(player, island, starter);
        }
    }
}
