package de.aetherion.items.codex;

import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Item faces for Codex entries: list cards, the detail header, and the tier ladder. */
public final class CodexCards {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);

    private CodexCards() {
    }

    /** Name color by tier: gray → white → green → blue → purple → gold. */
    public static String tierColor(int tier) {
        if (tier >= CodexTiers.MAX_TIER) {
            return "§6§l";
        }
        if (tier >= 7) {
            return "§6";
        }
        if (tier >= 5) {
            return "§5";
        }
        if (tier >= 3) {
            return "§9";
        }
        if (tier >= 1) {
            return "§a";
        }
        return "§f";
    }

    /** A list card. {@code hint} adds the click line at the bottom. */
    public static ItemStack card(CodexService service, Player player, CodexBook.State state, boolean clickHint) {
        CodexBook.Card card = state.card();
        if (!state.found()) {
            return undiscovered(card);
        }
        List<String> lore = new ArrayList<>();
        lore.add("§8" + CodexCatalog.categoryTitle(card.category()) + " · " + card.scale().label() + " ladder");
        lore.add("");
        lore.addAll(progressBlock(state));
        lore.add("§7Ladder " + CodexText.pips(state.tier(), state.claimed(), CodexTiers.MAX_TIER));
        List<String> variants = variantLines(service, player, card);
        if (!variants.isEmpty()) {
            lore.add("");
            lore.addAll(variants);
        }
        if (!state.maxed()) {
            lore.add("");
            lore.add("§6Tier " + CodexTiers.roman(state.tier() + 1) + " reward");
            lore.add(" " + CodexTiers.reward(card.scale(), state.tier() + 1).line());
        }
        int place = service.place(player, card.key());
        List<CodexService.Rank> top = service.top(card.key(), 1);
        lore.add("");
        String rankLine = "§7Rank §f#" + place;
        if (!top.isEmpty() && top.get(0).place() == 1 && place != 1) {
            rankLine += " §8· §6#1 §f" + top.get(0).name() + " §e" + CodexText.compact(top.get(0).amount());
        } else if (place == 1) {
            rankLine += " §6§l★ top of the server";
        }
        lore.add(rankLine);
        lore.add("");
        if (state.claimable() > 0) {
            lore.add("§e§l✦ " + state.claimable() + (state.claimable() == 1 ? " TIER" : " TIERS") + " TO CLAIM");
            lore.add(" " + state.pending().line());
            if (clickHint) {
                lore.add("§eShift-click §7to claim §8· §eClick §7for the ladder");
            }
        } else if (clickHint) {
            lore.add("§eClick §7for the tier ladder");
        }
        String name = tierColor(state.tier()) + card.name() + (state.tier() > 0 ? " §7" + CodexTiers.roman(state.tier()) : "");
        ItemStack item = GuiItems.named(card.icon(), name, lore);
        if (state.maxed() || state.claimable() > 0) {
            CodexChrome.glint(item);
        }
        return item;
    }

    /** Found-nothing face: the hint, never the name. */
    public static ItemStack undiscovered(CodexBook.Card card) {
        List<String> lore = new ArrayList<>();
        lore.add("§8" + CodexCatalog.categoryTitle(card.category()) + " · not found yet");
        lore.add("");
        if (card.hint() != null) {
            lore.addAll(CodexText.wrap(card.hint(), "§7"));
            lore.add("");
        }
        lore.add("§8First tier at " + CodexText.number(card.scale().rung(1))
                + (card.ledger() == CodexBook.Ledger.BESTIARY ? (card.scale().rung(1) == 1 ? " kill." : " kills.") : "."));
        return GuiItems.named(Material.GRAY_DYE, "§8???", lore);
    }

    /** Tier line + bar + count/next. */
    public static List<String> progressBlock(CodexBook.State state) {
        List<String> lore = new ArrayList<>();
        String verb = verb(state.card());
        if (state.maxed()) {
            lore.add("§7Tier §6§lIX §8· §6MAXED");
            lore.add(CodexText.bar(1.0d, "§6"));
            lore.add("§7" + verb + " §f" + CodexText.number(state.count()));
            return lore;
        }
        lore.add("§7Tier " + (state.tier() == 0 ? "§80" : "§f" + CodexTiers.roman(state.tier()))
                + " §8→ §e" + CodexTiers.roman(state.tier() + 1));
        lore.add(CodexText.bar(state.fill(), "§a") + " §f" + CodexText.percent(state.fill()));
        lore.add("§7" + verb + " §f" + CodexText.number(state.count()) + " §8/ §7" + CodexText.number(state.next()));
        return lore;
    }

    public static String verb(CodexBook.Card card) {
        if (card.ledger() == CodexBook.Ledger.BESTIARY) {
            return "Kills";
        }
        return switch (card.category()) {
            case CodexCatalog.BLOCK_FISHING -> "Caught";
            case CodexCatalog.BLOCK_CROPS -> "Harvested";
            case CodexCatalog.BLOCK_WOOD -> "Chopped";
            case CodexCatalog.BLOCK_DIRT -> "Dug";
            default -> "Mined";
        };
    }

    public static List<String> variantLines(CodexService service, Player player, CodexBook.Card card) {
        List<String> lore = new ArrayList<>();
        if (card.ledger() != CodexBook.Ledger.BESTIARY || !card.key().startsWith("b:") || card.isBoss()) {
            return lore;
        }
        String id = card.key().substring(2);
        long sturdy = service.variant(player, id, "sturdy");
        long brute = service.variant(player, id, "brute");
        long crypt = service.variant(player, id, "crypt");
        if (sturdy + brute + crypt <= 0L) {
            return lore;
        }
        lore.add("§7Variants §eSturdy §f" + CodexText.compact(sturdy) + " §8· §6Brute §f" + CodexText.compact(brute)
                + " §8· §5Crypt §f" + CodexText.compact(crypt));
        return lore;
    }

    public static String foundLine(CodexService service, Player player, CodexBook.Card card) {
        long day = service.foundDay(player, card.key());
        if (day < 0L) {
            return "§7First entry §8before the ledger kept dates";
        }
        return "§7First entry §f" + DATE.format(LocalDate.ofEpochDay(day));
    }

    // ------------------------------------------------------------------ ladder

    /** One rung of the ladder on the detail page. */
    public static ItemStack rung(CodexBook.State state, int tier) {
        CodexBook.Card card = state.card();
        long need = card.scale().rung(tier);
        CodexTiers.Reward reward = CodexTiers.reward(card.scale(), tier);
        String numeral = CodexTiers.roman(tier);
        List<String> lore = new ArrayList<>();
        lore.add("§7Needs §f" + CodexText.number(need) + " §7" + verb(card).toLowerCase(Locale.ROOT));
        lore.add("");
        if (tier <= state.claimed()) {
            lore.add("§7Paid out:");
            lore.add(" " + reward.line());
            lore.add("");
            lore.add("§a✔ Claimed");
            return GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§aTier " + numeral + " §2✔", lore);
        }
        if (tier <= state.tier()) {
            lore.add("§7Reward:");
            lore.add(" " + reward.line());
            lore.add("");
            lore.add("§e§lClick to claim");
            return CodexChrome.glint(GuiItems.named(Material.CHEST, "§6§lTier " + numeral + " §e— ready!", lore));
        }
        if (tier == state.tier() + 1) {
            long from = tier == 1 ? 0L : card.scale().rung(tier - 1);
            double fill = need <= from ? 1.0d : Math.max(0.0d, (state.count() - from) / (double) (need - from));
            lore.add(CodexText.bar(fill, "§e") + " §f" + CodexText.percent(fill));
            lore.add("§7" + CodexText.number(state.count()) + " §8/ §7" + CodexText.number(need)
                    + " §8(" + CodexText.number(Math.max(0L, need - state.count())) + " to go)");
            lore.add("");
            lore.add("§7Reward:");
            lore.add(" " + reward.line());
            return GuiItems.named(Material.YELLOW_STAINED_GLASS_PANE, "§eTier " + numeral + " §8· next", lore);
        }
        lore.add("§7Reward:");
        lore.add(" " + reward.line());
        return GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§cTier " + numeral, lore);
    }
}
