package de.aetherion.items.codex;

import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Collection / Bestiary list page.
 *
 * <pre>
 * row 0  tabs · header (ledger level) · How it works · Claim ledger
 * row 1  category tabs (Everything first)
 * row 2–4  21 entry cards, side columns tinted in the category color
 * row 5  Back · Prev · Sort · Close · Filter · Next
 * </pre>
 * Sort and filter are remembered per player per ledger for the session.
 */
public class CodexBrowser extends CodexView {

    public enum Sort {
        CATALOG("Catalog order"),
        PROGRESS("Closest to next tier"),
        TIER("Highest tier"),
        COUNT("Most counted"),
        NAME("A–Z");

        private final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    public enum Filter {
        ALL("Everything"),
        FOUND("Found only"),
        MISSING("Not found yet"),
        CLAIMABLE("Ready to claim"),
        UNMAXED("Not maxed");

        private final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private record Prefs(Sort sort, Filter filter) {
    }

    private static final Map<UUID, Map<CodexBook.Ledger, Prefs>> PREFS = new ConcurrentHashMap<>();
    private static final int[] CATEGORY_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private final CodexBook.Ledger ledger;
    private String category;
    private int page;
    private Sort sort = Sort.CATALOG;
    private Filter filter = Filter.ALL;
    /** Keys in the order last drawn — the detail page walks these with ◄ ►. */
    private List<String> order = List.of();

    public CodexBrowser(CodexBook.Ledger ledger, String category, int page) {
        this.ledger = ledger;
        // List.of(...).contains(null) throws — null means "the default shelf".
        this.category = category != null && ledger.tabs().contains(category) ? category : defaultCategory(ledger);
        this.page = Math.max(1, page);
    }

    static CodexBrowser forLedger(Player player, CodexBook.Ledger ledger, String category) {
        CodexBrowser browser = ledger == CodexBook.Ledger.COLLECTION
                ? new CollectionGUI.Holder(category, 1)
                : new BestiaryGUI.Holder(category, 1);
        Prefs prefs = PREFS.getOrDefault(player.getUniqueId(), Map.of()).get(ledger);
        if (prefs != null) {
            browser.sort = prefs.sort();
            browser.filter = prefs.filter();
        }
        return browser;
    }

    static void forget(UUID playerId) {
        PREFS.remove(playerId);
    }

    private static String defaultCategory(CodexBook.Ledger ledger) {
        return ledger == CodexBook.Ledger.COLLECTION ? CodexCatalog.BLOCK_ORES : CodexCatalog.MOB_HOSTILE;
    }

    public CodexBook.Ledger ledger() {
        return ledger;
    }

    public String category() {
        return category;
    }

    public int page() {
        return page;
    }

    List<String> order() {
        return order;
    }

    @Override
    protected String title() {
        return "§8" + ledger.title() + " §8» " + CodexCatalog.categoryTitle(category);
    }

    private CodexChrome.Tab tab() {
        return ledger == CodexBook.Ledger.COLLECTION ? CodexChrome.Tab.COLLECTION : CodexChrome.Tab.BESTIARY;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(Player player) {
        CodexService service = CodexRewards.service();
        Inventory inv = inventory;
        CodexChrome.frame(inv, player, tab());
        if (service == null) {
            inv.setItem(22, GuiItems.named(Material.BARRIER, "§cCodex is not loaded"));
            return;
        }
        CodexBook.Summary summary = CodexBook.summary(service, player, ledger);
        inv.setItem(CodexChrome.HEADER, header(summary));
        inv.setItem(CodexChrome.TOOL_A, howItWorks());
        inv.setItem(CodexChrome.TOOL_B, claimButton(summary));

        List<String> tabs = ledger.tabs();
        for (int i = 0; i < tabs.size() && i < CATEGORY_SLOTS.length; i++) {
            inv.setItem(CATEGORY_SLOTS[i], categoryTab(service, player, tabs.get(i)));
        }
        CodexChrome.sides(inv, CodexCatalog.ALL.equals(category)
                ? Material.WHITE_STAINED_GLASS_PANE
                : CodexCatalog.categoryPane(category));

        List<CodexBook.State> states = visible(service, player);
        List<String> keys = new ArrayList<>();
        for (CodexBook.State state : states) {
            keys.add(state.card().key());
        }
        order = keys;
        int per = CodexChrome.BODY_SLOTS.length;
        int pages = Math.max(1, (int) Math.ceil(states.size() / (double) per));
        page = Math.min(Math.max(page, 1), pages);
        int start = (page - 1) * per;
        for (int i = 0; i < per; i++) {
            int index = start + i;
            if (index >= states.size()) {
                break;
            }
            inv.setItem(CodexChrome.BODY_SLOTS[i], CodexCards.card(service, player, states.get(index), true));
        }
        if (states.isEmpty()) {
            inv.setItem(31, emptyState());
        }

        inv.setItem(CodexChrome.BACK, de.aetherion.items.util.ManagerNav.button());
        if (page > 1) {
            inv.setItem(CodexChrome.PREV, CodexChrome.prev(page, pages));
        }
        if (page < pages) {
            inv.setItem(CodexChrome.NEXT, CodexChrome.next(page, pages));
        }
        inv.setItem(CodexChrome.SORT, sortItem());
        inv.setItem(CodexChrome.FILTER, filterItem(states.size()));
    }

    private List<CodexBook.State> visible(CodexService service, Player player) {
        List<CodexBook.State> states = new ArrayList<>();
        for (CodexBook.State state : CodexBook.states(service, player, ledger, category)) {
            boolean keep = switch (filter) {
                case ALL -> true;
                case FOUND -> state.found();
                case MISSING -> !state.found();
                case CLAIMABLE -> state.claimable() > 0;
                case UNMAXED -> !state.maxed();
            };
            if (keep) {
                states.add(state);
            }
        }
        Comparator<CodexBook.State> byName = Comparator.comparing(state -> state.card().name(), String.CASE_INSENSITIVE_ORDER);
        Comparator<CodexBook.State> foundFirst = Comparator.comparing(state -> !state.found());
        switch (sort) {
            case PROGRESS -> states.sort(foundFirst
                    .thenComparing(CodexBook.State::maxed)
                    .thenComparing(Comparator.comparingDouble(CodexBook.State::fill).reversed()));
            case TIER -> states.sort(Comparator.comparingInt(CodexBook.State::tier).reversed()
                    .thenComparing(Comparator.comparingLong(CodexBook.State::count).reversed()));
            case COUNT -> states.sort(Comparator.comparingLong(CodexBook.State::count).reversed().thenComparing(byName));
            case NAME -> states.sort(foundFirst.thenComparing(byName));
            default -> {
            }
        }
        return states;
    }

    private ItemStack header(CodexBook.Summary summary) {
        List<String> lore = new ArrayList<>();
        lore.add("§8" + (ledger == CodexBook.Ledger.COLLECTION
                ? "Everything you gathered, one ladder each."
                : "Everything you fought, one ladder each."));
        lore.add("");
        lore.add("§7Level §f" + summary.level() + " §8/ §7" + summary.tiersPossible()
                + " §8(every tier reached is a level)");
        lore.add(CodexText.bar(summary.milestoneFill(), ledger.color()) + " §7→ milestone §f" + (summary.milestone() + 1));
        lore.add("§7Found §f" + summary.found() + "§8/§7" + summary.total()
                + "  §7Maxed §f" + summary.maxed());
        lore.add("");
        lore.add("§7Milestone perks: " + (summary.milestone() > 0
                ? CodexPerks.perkLine(ledger, summary.milestone())
                : "§8none yet"));
        lore.add("§8Next: " + CodexPerks.perkLine(ledger, 1) + " §8at Level "
                + ((summary.milestone() + 1) * CodexTiers.MILESTONE_EVERY));
        if (summary.claimableTiers() > 0) {
            lore.add("");
            lore.add("§e✦ " + summary.claimableTiers() + " tier" + (summary.claimableTiers() == 1 ? "" : "s")
                    + " ready §8— Claim is top right");
        }
        Material icon = ledger == CodexBook.Ledger.COLLECTION ? Material.IRON_PICKAXE : Material.BONE;
        return GuiItems.named(icon, ledger.color() + ledger.title() + " §8· §fLevel " + summary.level(), lore);
    }

    private ItemStack howItWorks() {
        List<String> lore = new ArrayList<>();
        if (ledger == CodexBook.Ledger.COLLECTION) {
            lore.add("§7Mine, chop, dig, harvest and fish.");
            lore.add("§7Each entry climbs nine tiers, §fI§7–§fIX§7.");
            lore.add("§7Crops count only when fully grown.");
            lore.add("§7Blocks you placed yourself don't count.");
        } else {
            lore.add("§7Kill it and it's on file.");
            lore.add("§7Each entry climbs nine tiers, §fI§7–§fIX§7.");
            lore.add("§7Bosses credit everyone who hit them.");
            lore.add("§7Borderlands variants are tallied too.");
        }
        lore.add("");
        lore.add("§6Tiers pay out when you claim them:");
        lore.add("§8 coins · Aetherion XP · Shards (V, VII, IX)");
        lore.add("§6Every 10 levels is a milestone:");
        lore.add("§8 " + CodexText.strip(CodexPerks.perkLine(ledger, 1)) + ", permanent");
        lore.add("");
        lore.add("§8Undiscovered entries show as §7???§8.");
        return GuiItems.named(Material.BOOK, "§eHow it works", lore);
    }

    private ItemStack claimButton(CodexBook.Summary summary) {
        if (summary.claimableTiers() <= 0) {
            return GuiItems.named(Material.MINECART, "§7Nothing to claim",
                    "§8Reach a tier and it lands here.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("§7" + summary.claimableTiers() + " tier" + (summary.claimableTiers() == 1 ? "" : "s")
                + " waiting in the " + ledger.title() + ".");
        lore.add(" " + summary.pending().line());
        lore.add("");
        lore.add("§e§lClick to claim them all");
        return CodexChrome.glint(GuiItems.named(Material.CHEST_MINECART, "§6§lClaim " + ledger.title(), lore));
    }

    private ItemStack categoryTab(CodexService service, Player player, String cat) {
        boolean active = cat.equals(category);
        int found = 0;
        int total = 0;
        int ready = 0;
        int tiers = 0;
        for (CodexBook.State state : CodexBook.states(service, player, ledger, cat)) {
            total++;
            tiers += state.tier();
            if (state.found()) {
                found++;
            }
            ready += state.claimable();
        }
        List<String> lore = new ArrayList<>();
        lore.add("§8" + CodexCatalog.categoryBlurb(cat));
        lore.add("");
        double fill = total == 0 ? 0.0d : tiers / (double) (total * CodexTiers.MAX_TIER);
        lore.add("§7Found §f" + found + "§8/§7" + total + "  §7Tiers §f" + tiers + "§8/§7" + (total * CodexTiers.MAX_TIER));
        lore.add(CodexText.bar(fill, CodexCatalog.ALL.equals(cat) ? "§f" : CodexCatalog.categoryColor(cat), 15)
                + " §f" + CodexText.percent(fill));
        if (ready > 0) {
            lore.add("§e✦ " + ready + " to claim");
        }
        lore.add("");
        lore.add(active ? "§a▶ Showing below" : "§eClick to browse");
        String color = CodexCatalog.ALL.equals(cat) ? "§f" : CodexCatalog.categoryColor(cat);
        ItemStack item = GuiItems.named(CodexCatalog.categoryIcon(cat),
                (active ? "§a▶ " : color) + CodexCatalog.categoryTitle(cat), lore);
        return active || ready > 0 ? CodexChrome.glint(item) : item;
    }

    private ItemStack sortItem() {
        List<String> lore = new ArrayList<>();
        for (Sort mode : Sort.values()) {
            lore.add(mode == sort ? "§a▶ " + mode.label : "§8  " + mode.label);
        }
        lore.add("");
        lore.add("§eClick §7next · §eRight-click §7previous");
        return GuiItems.named(Material.HOPPER, "§eSort: §f" + sort.label, lore);
    }

    private ItemStack filterItem(int shown) {
        List<String> lore = new ArrayList<>();
        for (Filter mode : Filter.values()) {
            lore.add(mode == filter ? "§a▶ " + mode.label : "§8  " + mode.label);
        }
        lore.add("");
        lore.add("§7Showing §f" + shown);
        lore.add("§eClick §7next · §eRight-click §7previous");
        return GuiItems.named(Material.SPYGLASS, "§eShow: §f" + filter.label, lore);
    }

    private ItemStack emptyState() {
        String line = switch (filter) {
            case CLAIMABLE -> "No tiers waiting here. Go earn some.";
            case MISSING -> "Nothing left to discover here. Show-off.";
            case FOUND -> "Nothing found here yet. The ??? cards are hints.";
            case UNMAXED -> "Everything here is maxed. Frame it.";
            default -> "This shelf is empty.";
        };
        return GuiItems.named(Material.STRUCTURE_VOID, "§7Nothing to show", "§8" + line, "",
                "§eClick the spyglass §7to change the filter.");
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void click(Player player, int slot, ClickType click) {
        if (CodexChrome.clickTab(player, slot, tab())) {
            return;
        }
        if (slot == CodexChrome.BACK) {
            CodexChrome.click(player);
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (slot == CodexChrome.TOOL_B) {
            claimLedger(player);
            render(player);
            return;
        }
        if (slot == CodexChrome.PREV) {
            page = Math.max(1, page - 1);
            CodexChrome.click(player);
            render(player);
            return;
        }
        if (slot == CodexChrome.NEXT) {
            page++;
            CodexChrome.click(player);
            render(player);
            return;
        }
        if (slot == CodexChrome.SORT) {
            sort = cycle(Sort.values(), sort, click.isRightClick());
            page = 1;
            remember(player);
            CodexChrome.click(player);
            render(player);
            return;
        }
        if (slot == CodexChrome.FILTER) {
            filter = cycle(Filter.values(), filter, click.isRightClick());
            page = 1;
            remember(player);
            CodexChrome.click(player);
            render(player);
            return;
        }
        List<String> tabs = ledger.tabs();
        for (int i = 0; i < CATEGORY_SLOTS.length && i < tabs.size(); i++) {
            if (CATEGORY_SLOTS[i] == slot) {
                if (!tabs.get(i).equals(category)) {
                    category = tabs.get(i);
                    page = 1;
                    player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.1f);
                    inventory = null;
                    open(player);
                }
                return;
            }
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
            if (click.isShiftClick()) {
                if (CodexRewards.claim(player, key) > 0) {
                    render(player);
                }
                return;
            }
            CodexService service = CodexRewards.service();
            CodexBook.Card card = CodexBook.card(key);
            if (service != null && card != null && service.count(player, key) <= 0L) {
                player.sendMessage("§8Not found yet. " + (card.hint() == null ? "" : "§7" + card.hint()));
                CodexChrome.deny(player);
                return;
            }
            CodexChrome.click(player);
            CodexMenus.openDetail(player, key, this);
            return;
        }
    }

    private void claimLedger(Player player) {
        CodexService service = CodexRewards.service();
        if (service == null) {
            return;
        }
        int total = 0;
        CodexTiers.Reward reward = CodexTiers.Reward.NONE;
        var skills = CodexRewards.skills();
        boolean batch = skills != null && skills.beginRewardBatch(player);
        try {
            for (CodexBook.State state : CodexBook.states(service, player, ledger, CodexCatalog.ALL)) {
                if (state.claimable() <= 0) {
                    continue;
                }
                CodexTiers.Reward pending = state.pending();
                int paid = claimQuiet(player, state.card().key());
                if (paid > 0) {
                    total += paid;
                    reward = reward.plus(pending);
                }
            }
        } finally {
            if (batch) {
                skills.endRewardBatch(player);
            }
        }
        if (total <= 0) {
            player.sendMessage("§7Nothing to claim in the " + ledger.title() + " yet.");
            CodexChrome.deny(player);
            return;
        }
        player.sendMessage("§a✔ Claimed §f" + total + " §7" + ledger.title() + " tier" + (total == 1 ? "" : "s")
                + " §8» " + reward.line());
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    /** Claim without the per-entry chat line (the caller prints one summary). */
    private static int claimQuiet(Player player, String key) {
        CodexService service = CodexRewards.service();
        CodexBook.Card card = CodexBook.card(key);
        if (service == null || card == null) {
            return 0;
        }
        CodexBook.State state = CodexBook.state(service, player, card);
        if (state.claimable() <= 0) {
            return 0;
        }
        CodexTiers.Reward reward = state.pending();
        service.setClaimed(player, key, state.tier());
        CodexRewards.pay(player, reward);
        return state.claimable();
    }

    private void remember(Player player) {
        PREFS.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(CodexBook.Ledger.class))
                .put(ledger, new Prefs(sort, filter));
    }

    private static <T extends Enum<T>> T cycle(T[] values, T current, boolean back) {
        int index = current.ordinal() + (back ? values.length - 1 : 1);
        return values[index % values.length];
    }
}
