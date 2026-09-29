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
 * Boss Journal — every live BossEngine boss, kills, ladder tier and a loot preview. Loot stays
 * hidden until your first kill; click a boss for its full tier ladder and drop table.
 */
public final class DungeonJournalGUI {

    public static final String TITLE = "§8Boss Journal";
    public static final int BACK_SLOT = CodexGui.BACK_SLOT;

    private static final int ALL_SLOT = 11;
    private static final int WORLD_SLOT = 13;
    private static final int DUNGEON_SLOT = 15;

    private final CodexService service;

    public DungeonJournalGUI(CodexService service) {
        this.service = service;
    }

    public void open(Player player) {
        open(player, 1);
    }

    public void open(Player player, int page) {
        if (player != null && service != null) {
            new Holder(page).open(player);
        }
    }

    /** Legacy entry point: clicks on the page now route through {@link CodexMenus}. */
    public void handleClick(Player player, int slot) {
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            holder.click(player, slot, ClickType.LEFT);
        }
    }

    public static final class Holder extends CodexView {

        private int page;
        /** 0 = all, 1 = world, 2 = dungeon. */
        private int shelf;
        private List<String> order = List.of();

        public Holder(int page) {
            this.page = Math.max(1, page);
        }

        @Override
        protected String title() {
            return TITLE;
        }

        @Override
        public void render(Player player) {
            CodexService service = CodexRewards.service();
            Inventory inv = inventory;
            CodexChrome.frame(inv, player, CodexChrome.Tab.JOURNAL);
            CodexChrome.row(inv, 1, Material.PURPLE_STAINED_GLASS_PANE);
            if (service == null) {
                return;
            }
            List<CodexBook.State> states = new ArrayList<>();
            int slain = 0;
            int total = 0;
            long kills = 0L;
            for (CodexCatalog.Boss boss : CodexCatalog.bosses()) {
                CodexBook.State state = CodexBook.state(service, player, CodexBook.card("b:boss:" + boss.templateId()));
                total++;
                if (state.found()) {
                    slain++;
                    kills += state.count();
                }
                if (shelf == 1 && boss.dungeon() || shelf == 2 && !boss.dungeon()) {
                    continue;
                }
                states.add(state);
            }
            // Slain first (most kills), then the unknowns.
            states.sort((a, b) -> {
                if (a.found() != b.found()) {
                    return a.found() ? -1 : 1;
                }
                if (a.count() != b.count()) {
                    return Long.compare(b.count(), a.count());
                }
                return a.card().name().compareToIgnoreCase(b.card().name());
            });

            inv.setItem(CodexChrome.HEADER, GuiItems.named(Material.WRITABLE_BOOK,
                    "§5Boss Journal §8· §f" + slain + "§8/§7" + total,
                    "§8Every boss BossEngine knows.",
                    "",
                    "§7Bosses slain §f" + slain + "§8/§7" + total,
                    CodexText.bar(total == 0 ? 0.0d : slain / (double) total, "§5"),
                    "§7Boss kills §f" + CodexText.number(kills),
                    "",
                    "§8Everyone who hits a boss gets the kill.",
                    "§8Loot tables unlock on your first kill."));
            inv.setItem(ALL_SLOT, shelfTab(0, "§fAll bosses", Material.NETHER_STAR));
            inv.setItem(WORLD_SLOT, shelfTab(1, "§6World bosses", Material.GRASS_BLOCK));
            inv.setItem(DUNGEON_SLOT, shelfTab(2, "§dDungeon bosses", Material.DEEPSLATE_BRICKS));

            List<String> keys = new ArrayList<>();
            for (CodexBook.State state : states) {
                keys.add(state.card().key());
            }
            order = keys;
            int per = CodexChrome.BODY_SLOTS.length;
            int pages = Math.max(1, (int) Math.ceil(states.size() / (double) per));
            page = Math.min(Math.max(page, 1), pages);
            int start = (page - 1) * per;
            for (int i = 0; i < per && start + i < states.size(); i++) {
                inv.setItem(CodexChrome.BODY_SLOTS[i], bossCard(states.get(start + i)));
            }
            if (states.isEmpty()) {
                inv.setItem(31, GuiItems.named(Material.BARRIER, "§cNo bosses loaded",
                        "§7BossEngine is missing", "§7or has no templates."));
            }
            inv.setItem(CodexChrome.BACK, de.aetherion.items.util.ManagerNav.button());
            if (page > 1) {
                inv.setItem(CodexChrome.PREV, CodexChrome.prev(page, pages));
            }
            if (page < pages) {
                inv.setItem(CodexChrome.NEXT, CodexChrome.next(page, pages));
            }
        }

        private ItemStack shelfTab(int index, String name, Material icon) {
            boolean active = shelf == index;
            ItemStack item = GuiItems.named(icon, (active ? "§a▶ " : "") + name,
                    active ? "§a▶ Showing below" : "§eClick to filter");
            return active ? CodexChrome.glint(item) : item;
        }

        private ItemStack bossCard(CodexBook.State state) {
            CodexBook.Card card = state.card();
            if (!state.found()) {
                return GuiItems.named(Material.GRAY_DYE, "§8???",
                        "§8" + (card.boss() != null && card.boss().dungeon() ? "Dungeon boss" : "World boss") + " · not slain yet",
                        "",
                        "§7Its loot table stays blank",
                        "§7until you land the first hit",
                        "§7on a kill.");
            }
            List<String> lore = new ArrayList<>();
            lore.add("§8" + (card.boss() != null && card.boss().dungeon() ? "Dungeon boss" : "World boss"));
            lore.add("");
            lore.addAll(CodexCards.progressBlock(state));
            BossJournal.Entry loot = card.boss() == null ? null : BossJournal.entry(card.boss().templateId());
            if (loot != null) {
                lore.add("");
                if (loot.experience() > 0) {
                    lore.add("§7Boss XP §e" + loot.experience());
                }
                lore.add("§6Drops");
                lore.addAll(BossJournal.dropLines(loot, 6));
            }
            lore.add("");
            if (state.claimable() > 0) {
                lore.add("§e§l✦ " + state.claimable() + " to claim §8· §eShift-click");
            }
            lore.add("§eClick §7for the ladder and full loot");
            ItemStack item = GuiItems.named(card.icon(),
                    CodexCards.tierColor(state.tier()) + card.name() + " §7" + CodexTiers.roman(state.tier()), lore);
            return state.claimable() > 0 || state.maxed() ? CodexChrome.glint(item) : item;
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (CodexChrome.clickTab(player, slot, CodexChrome.Tab.JOURNAL)) {
                return;
            }
            if (slot == CodexChrome.BACK) {
                CodexChrome.click(player);
                de.aetherion.items.util.ManagerNav.openManager(player);
                return;
            }
            if (slot == ALL_SLOT || slot == WORLD_SLOT || slot == DUNGEON_SLOT) {
                shelf = slot == ALL_SLOT ? 0 : slot == WORLD_SLOT ? 1 : 2;
                page = 1;
                CodexChrome.click(player);
                render(player);
                return;
            }
            if (slot == CodexChrome.PREV) {
                page = Math.max(1, page - 1);
                render(player);
                return;
            }
            if (slot == CodexChrome.NEXT) {
                page++;
                render(player);
                return;
            }
            for (int i = 0; i < CodexChrome.BODY_SLOTS.length; i++) {
                if (CodexChrome.BODY_SLOTS[i] != slot) {
                    continue;
                }
                int index = (page - 1) * CodexChrome.BODY_SLOTS.length + i;
                if (index >= order.size()) {
                    return;
                }
                String key = order.get(index);
                CodexService service = CodexRewards.service();
                if (service == null || service.count(player, key) <= 0L) {
                    CodexChrome.deny(player);
                    return;
                }
                if (click.isShiftClick()) {
                    if (CodexRewards.claim(player, key) > 0) {
                        render(player);
                    }
                    return;
                }
                CodexChrome.click(player);
                CodexMenus.openDetail(player, key, this);
                return;
            }
        }
    }
}
