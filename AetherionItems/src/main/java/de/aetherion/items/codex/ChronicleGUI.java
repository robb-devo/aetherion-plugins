package de.aetherion.items.codex;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.StoryAccess;
import de.aetherion.items.lore.StoryLore;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chronicle — one page per story find ("Das Schlaflied"). Pages unlock when AetherionQuests
 * hands you the find; this page only reads {@link StoryAccess#chroniclePages}. Readable finds
 * (the storybook, the letters, the writ) open as a book from here.
 */
public final class ChronicleGUI {

    public static final String TITLE = "§8Chronicle";

    private ChronicleGUI() {
    }

    public static void open(Player player) {
        if (player != null) {
            new Holder().open(player);
        }
    }

    /** Unlocked page ids for this player (empty when Quests is offline). */
    public static Set<String> pages(Player player) {
        StoryAccess story = AetherServices.story();
        if (story == null || player == null) {
            return Set.of();
        }
        Set<String> pages = story.chroniclePages(player);
        return pages == null ? Set.of() : pages;
    }

    public static final class Holder extends CodexView {

        private final List<StoryLore> order = List.of(StoryLore.values());

        @Override
        protected String title() {
            return TITLE;
        }

        @Override
        public void render(Player player) {
            ItemStack header = CodexChrome.pane(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack body = CodexChrome.pane(Material.GRAY_STAINED_GLASS_PANE);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, (slot < 9 || slot >= inventory.getSize() - 9 ? header : body).clone());
            }
            Set<String> unlocked = pages(player);
            int found = 0;
            for (StoryLore lore : order) {
                if (unlocked.contains(lore.findId())) {
                    found++;
                }
            }
            inventory.setItem(CodexChrome.HEADER, GuiItems.named(Material.LECTERN,
                    "§6Chronicle §8· §f" + found + "§8/§7" + order.size(),
                    "§7The old lullaby, as you found it.",
                    "§7One page per find.",
                    "",
                    CodexText.bar(order.isEmpty() ? 0.0d : found / (double) order.size(), "§6"),
                    "",
                    found == 0 ? "§8Nothing yet. Keep your ears open." : "§8Books and letters open on click."));
            for (int i = 0; i < order.size() && i < CodexChrome.BODY_SLOTS.length; i++) {
                StoryLore lore = order.get(i);
                inventory.setItem(CodexChrome.BODY_SLOTS[i], unlocked.contains(lore.findId())
                        ? page(lore)
                        : GuiItems.named(Material.GRAY_DYE, "§8???", "§7Not found yet."));
            }
            inventory.setItem(CodexChrome.BACK, CodexChrome.back("the Journal"));
            inventory.setItem(CodexChrome.CLOSE, GuiItems.named(Material.BARRIER, "§cClose", "§8Esc works too."));
        }

        private static ItemStack page(StoryLore lore) {
            List<String> text = new ArrayList<>(lore.chronicle());
            text.add("");
            text.add("§8" + lore.foundAt());
            if (lore.readable()) {
                text.add("");
                text.add("§eClick §7to read.");
            }
            return GuiItems.named(lore.material(), lore.rarity().getChatColor() + lore.displayName(), text);
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (slot == CodexChrome.BACK) {
                CodexChrome.click(player);
                JournalHubGUI.open(player);
                return;
            }
            Set<String> unlocked = pages(player);
            for (int i = 0; i < order.size() && i < CodexChrome.BODY_SLOTS.length; i++) {
                if (CodexChrome.BODY_SLOTS[i] != slot) {
                    continue;
                }
                StoryLore lore = order.get(i);
                if (!unlocked.contains(lore.findId())) {
                    CodexChrome.deny(player);
                    return;
                }
                if (lore.readable()) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
                    player.closeInventory();
                    player.openBook(lore.create());
                }
                return;
            }
        }
    }
}
