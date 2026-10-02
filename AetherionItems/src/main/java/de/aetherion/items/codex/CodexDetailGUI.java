package de.aetherion.items.codex;

import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry, all nine tiers.
 *
 * <pre>
 * row 0  tabs · header (the entry) · tools
 * row 1  category-tinted rule
 * row 2  Tier I … Tier IX (claimed green · ready chest · next yellow · locked red)
 * row 3  Your record · Leaderboard · Field notes (boss loot once you've killed it)
 * row 4  Rewards so far / still ahead
 * row 5  Back · ◄ entry · Claim/Close · entry ►
 * </pre>
 */
public final class CodexDetailGUI {

    private static final int LADDER_START = 18;
    private static final int RECORD = 29;
    private static final int BOARD = 31;
    private static final int NOTES = 33;
    private static final int REWARDS = 40;
    private static final int PREV_ENTRY = 47;
    private static final int NEXT_ENTRY = 51;

    private CodexDetailGUI() {
    }

    public static final class Holder extends CodexView {

        private String key;
        private final CodexView back;

        public Holder(String key, CodexView back) {
            this.key = key;
            this.back = back;
        }

        public String key() {
            return key;
        }

        @Override
        protected String title() {
            CodexBook.Card card = CodexBook.card(key);
            return "§8" + (card == null ? "Codex" : card.ledger().title() + " §8» " + card.name());
        }

        private CodexChrome.Tab tab(CodexBook.Card card) {
            return card != null && card.ledger() == CodexBook.Ledger.COLLECTION
                    ? CodexChrome.Tab.COLLECTION
                    : CodexChrome.Tab.BESTIARY;
        }

        @Override
        public void render(Player player) {
            CodexService service = CodexRewards.service();
            CodexBook.Card card = CodexBook.card(key);
            Inventory inv = inventory;
            CodexChrome.frame(inv, player, tab(card));
            if (service == null || card == null) {
                return;
            }
            CodexBook.State state = CodexBook.state(service, player, card);
            CodexChrome.row(inv, 1, CodexCatalog.categoryPane(card.category()));
            inv.setItem(CodexChrome.HEADER, header(service, player, state));
            for (int tier = 1; tier <= CodexTiers.MAX_TIER; tier++) {
                inv.setItem(LADDER_START + tier - 1, CodexCards.rung(state, tier));
            }
            inv.setItem(RECORD, record(service, player, state));
            inv.setItem(BOARD, board(service, player, state));
            inv.setItem(NOTES, notes(state));
            inv.setItem(REWARDS, rewards(state));

            inv.setItem(CodexChrome.BACK, back == null
                    ? CodexChrome.back("the Codex")
                    : CodexChrome.back(back instanceof CodexBrowser browser
                            ? browser.ledger().title() + " · " + CodexCatalog.categoryTitle(browser.category())
                            : "the previous page"));
            List<String> order = order();
            int index = order.indexOf(key);
            if (index > 0) {
                inv.setItem(PREV_ENTRY, neighbour(service, player, order.get(index - 1), "§e◄ Previous"));
            }
            if (index >= 0 && index < order.size() - 1) {
                inv.setItem(NEXT_ENTRY, neighbour(service, player, order.get(index + 1), "§eNext ►"));
            }
            if (state.claimable() > 0) {
                inv.setItem(CodexChrome.CLOSE, CodexChrome.glint(GuiItems.named(Material.CHEST_MINECART,
                        "§6§lClaim " + state.claimable() + (state.claimable() == 1 ? " tier" : " tiers"),
                        " " + state.pending().line(), "", "§eClick to claim")));
            }
        }

        private List<String> order() {
            return back instanceof CodexBrowser browser ? browser.order() : List.of();
        }

        private ItemStack neighbour(CodexService service, Player player, String neighbourKey, String label) {
            CodexBook.Card card = CodexBook.card(neighbourKey);
            if (card == null) {
                return GuiItems.named(Material.ARROW, label);
            }
            boolean found = service.count(player, neighbourKey) > 0L;
            return GuiItems.named(Material.ARROW, label, "§7" + (found ? card.name() : "???"));
        }

        private ItemStack header(CodexService service, Player player, CodexBook.State state) {
            CodexBook.Card card = state.card();
            List<String> lore = new ArrayList<>();
            lore.add("§8" + CodexCatalog.categoryTitle(card.category()) + " · " + card.scale().label() + " ladder");
            lore.add("");
            lore.addAll(CodexCards.progressBlock(state));
            lore.add("§7Ladder " + CodexText.pips(state.tier(), state.claimed(), CodexTiers.MAX_TIER));
            ItemStack item = GuiItems.named(card.icon(),
                    CodexCards.tierColor(state.tier()) + card.name()
                            + (state.tier() > 0 ? " §7" + CodexTiers.roman(state.tier()) : ""), lore);
            return state.maxed() ? CodexChrome.glint(item) : item;
        }

        private ItemStack record(CodexService service, Player player, CodexBook.State state) {
            CodexBook.Card card = state.card();
            List<String> lore = new ArrayList<>();
            lore.add("§7" + CodexCards.verb(card) + " §f" + CodexText.number(state.count()));
            int place = service.place(player, card.key());
            lore.add("§7Server rank §f" + (place <= 0 ? "—" : "#" + place)
                    + " §8of " + service.trackedPlayers() + " on file");
            lore.add(CodexCards.foundLine(service, player, card));
            List<String> variants = CodexCards.variantLines(service, player, card);
            if (!variants.isEmpty()) {
                lore.add("");
                lore.addAll(variants);
                lore.add("§8Sturdy, Brute and Crypt are Borderlands tiers.");
            }
            if (!state.maxed()) {
                lore.add("");
                lore.add("§7To Tier " + CodexTiers.roman(state.tier() + 1) + ": §f"
                        + CodexText.number(Math.max(0L, state.next() - state.count())) + " §7more");
            }
            return GuiItems.named(Material.PLAYER_HEAD, "§aYour record", lore);
        }

        private ItemStack board(CodexService service, Player player, CodexBook.State state) {
            List<String> lore = new ArrayList<>();
            List<CodexService.Rank> top = service.top(state.card().key(), 10);
            if (top.isEmpty()) {
                lore.add("§8Nobody on the board yet.");
            }
            for (CodexService.Rank rank : top) {
                String medal = switch (rank.place()) {
                    case 1 -> "§6§l#1";
                    case 2 -> "§f§l#2";
                    case 3 -> "§c§l#3";
                    default -> "§7#" + rank.place();
                };
                boolean you = rank.name().equalsIgnoreCase(player.getName())
                        || rank.name().contains(player.getName());
                lore.add(medal + " " + (you ? "§a" : "§f") + rank.name() + " §8- §e" + CodexText.number(rank.amount()));
            }
            return GuiItems.named(Material.GOLDEN_HELMET, "§6Leaderboard §8· top 10", lore);
        }

        private ItemStack notes(CodexBook.State state) {
            CodexBook.Card card = state.card();
            List<String> lore = new ArrayList<>();
            if (card.hint() != null) {
                lore.addAll(CodexText.wrap(card.hint(), "§7"));
            }
            lore.add("§8Ladder: " + card.scale().label() + " · Tier IX at "
                    + CodexText.number(card.scale().rung(CodexTiers.MAX_TIER)));
            if (card.isBoss()) {
                lore.add("");
                BossJournal.Entry loot = BossJournal.entry(card.boss().templateId());
                if (loot == null) {
                    lore.add("§8BossEngine has no template loaded for it.");
                } else if (!state.found()) {
                    lore.add("§8Loot table unlocks on your first kill.");
                } else {
                    if (loot.experience() > 0) {
                        lore.add("§7Boss XP §e" + loot.experience());
                    }
                    lore.add("§6Drops");
                    lore.addAll(BossJournal.dropLines(loot, 12));
                }
            }
            return GuiItems.named(card.isBoss() ? Material.WRITABLE_BOOK : Material.MAP, "§eField notes", lore);
        }

        private ItemStack rewards(CodexBook.State state) {
            CodexBook.Card card = state.card();
            CodexTiers.Reward paid = CodexTiers.Reward.NONE;
            CodexTiers.Reward ahead = CodexTiers.Reward.NONE;
            for (int tier = 1; tier <= CodexTiers.MAX_TIER; tier++) {
                CodexTiers.Reward reward = CodexTiers.reward(card.scale(), tier);
                if (tier <= state.claimed()) {
                    paid = paid.plus(reward);
                } else {
                    ahead = ahead.plus(reward);
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("§7Claimed so far");
            lore.add(" " + paid.line());
            lore.add("§7Still on this ladder");
            lore.add(" " + ahead.line());
            lore.add("");
            lore.add("§8Every tier also adds 1 " + card.ledger().title() + " Level.");
            return GuiItems.named(Material.GOLD_INGOT, "§6Rewards", lore);
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            CodexBook.Card card = CodexBook.card(key);
            if (CodexChrome.clickTab(player, slot, tab(card))) {
                return;
            }
            if (slot == CodexChrome.BACK) {
                CodexChrome.click(player);
                if (back != null) {
                    back.open(player);
                } else {
                    CodexMenus.openHub(player);
                }
                return;
            }
            if (slot == CodexChrome.CLOSE) {
                CodexService service = CodexRewards.service();
                if (service != null && card != null && CodexBook.state(service, player, card).claimable() > 0) {
                    CodexRewards.claim(player, key);
                    render(player);
                } else {
                    player.closeInventory();
                }
                return;
            }
            if (slot >= LADDER_START && slot < LADDER_START + CodexTiers.MAX_TIER) {
                CodexService service = CodexRewards.service();
                if (service == null || card == null) {
                    return;
                }
                int tier = slot - LADDER_START + 1;
                CodexBook.State state = CodexBook.state(service, player, card);
                if (tier > state.claimed() && tier <= state.tier()) {
                    CodexRewards.claim(player, key);
                    render(player);
                } else {
                    CodexChrome.deny(player);
                }
                return;
            }
            List<String> order = order();
            int index = order.indexOf(key);
            if (slot == PREV_ENTRY && index > 0) {
                step(player, order.get(index - 1));
            } else if (slot == NEXT_ENTRY && index >= 0 && index < order.size() - 1) {
                step(player, order.get(index + 1));
            }
        }

        private void step(Player player, String target) {
            CodexService service = CodexRewards.service();
            if (service != null && service.count(player, target) <= 0L) {
                // Skip over the ??? — walk to the next found one in that direction.
                List<String> order = order();
                int from = order.indexOf(key);
                int to = order.indexOf(target);
                int direction = Integer.signum(to - from);
                int i = to;
                while (i >= 0 && i < order.size() && service.count(player, order.get(i)) <= 0L) {
                    i += direction;
                }
                if (i < 0 || i >= order.size()) {
                    CodexChrome.deny(player);
                    return;
                }
                target = order.get(i);
            }
            key = target;
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
            inventory = null;
            open(player);
        }
    }
}
