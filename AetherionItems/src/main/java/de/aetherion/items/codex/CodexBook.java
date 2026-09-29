package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the raw ledger into what the pages show: cards (one per entry), their tier state, and the
 * per-ledger level/summary. Summaries are cached per player on the ledger's version counter, so the
 * stat provider and the Manager can ask every tick without re-walking ~150 entries.
 */
public final class CodexBook {

    public enum Ledger {
        COLLECTION("Collection", "§a", "c:"),
        BESTIARY("Bestiary", "§6", "b:");

        private final String title;
        private final String color;
        private final String prefix;

        Ledger(String title, String color, String prefix) {
            this.title = title;
            this.color = color;
            this.prefix = prefix;
        }

        public String title() {
            return title;
        }

        public String color() {
            return color;
        }

        public String prefix() {
            return prefix;
        }

        public List<String> tabs() {
            return this == COLLECTION ? CodexCatalog.COLLECTION_TABS : CodexCatalog.BESTIARY_TABS;
        }
    }

    /** One entry as a page shows it. {@code key} is the claim key ({@code c:coal}, {@code b:boss:mcnugget}). */
    public record Card(String key, String name, Material icon, String category, CodexTiers.Scale scale,
                       String hint, Ledger ledger, CodexCatalog.Boss boss) {

        public boolean isBoss() {
            return boss != null;
        }
    }

    /** A card plus one player's standing on it. */
    public record State(Card card, long count, int tier, int claimed, long next, double fill) {

        public boolean found() {
            return count > 0L;
        }

        public boolean maxed() {
            return tier >= CodexTiers.MAX_TIER;
        }

        public int claimable() {
            return Math.max(0, tier - claimed);
        }

        public CodexTiers.Reward pending() {
            CodexTiers.Reward total = CodexTiers.Reward.NONE;
            for (int t = claimed + 1; t <= tier; t++) {
                total = total.plus(CodexTiers.reward(card.scale(), t));
            }
            return total;
        }
    }

    public record Summary(int level, int found, int total, int maxed, int claimableTiers,
                          CodexTiers.Reward pending, int tiersPossible) {

        public int milestone() {
            return level / CodexTiers.MILESTONE_EVERY;
        }

        /** 0–1 toward the next milestone. */
        public double milestoneFill() {
            return (level % CodexTiers.MILESTONE_EVERY) / (double) CodexTiers.MILESTONE_EVERY;
        }
    }

    private record Cached(long version, int bosses, Summary summary) {
    }

    private static final Map<UUID, Cached> COLLECTION_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Cached> BESTIARY_CACHE = new ConcurrentHashMap<>();

    private CodexBook() {
    }

    // ------------------------------------------------------------------ cards

    public static List<Card> cards(Ledger ledger, String category) {
        List<Card> cards = new ArrayList<>();
        List<String> categories = CodexCatalog.ALL.equals(category)
                ? (ledger == Ledger.COLLECTION ? CodexCatalog.BLOCK_CATEGORIES : CodexCatalog.BESTIARY_TABS)
                : List.of(category);
        for (String cat : categories) {
            if (CodexCatalog.ALL.equals(cat)) {
                continue;
            }
            if (ledger == Ledger.COLLECTION) {
                for (CodexCatalog.Entry entry : CodexCatalog.blocks(cat)) {
                    cards.add(card(entry, Ledger.COLLECTION));
                }
                continue;
            }
            for (CodexCatalog.Entry entry : CodexCatalog.mobs(cat)) {
                cards.add(card(entry, Ledger.BESTIARY));
            }
            if (CodexCatalog.MOB_BOSSES.equals(cat) || CodexCatalog.MOB_DUNGEON.equals(cat)) {
                boolean dungeon = CodexCatalog.MOB_DUNGEON.equals(cat);
                for (CodexCatalog.Boss boss : CodexCatalog.bosses()) {
                    if (boss.dungeon() == dungeon) {
                        cards.add(bossCard(boss));
                    }
                }
            }
        }
        return cards;
    }

    /** The card behind a claim key, or null. */
    public static Card card(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith("c:")) {
            CodexCatalog.Entry entry = CodexCatalog.block(key.substring(2));
            return entry == null ? null : card(entry, Ledger.COLLECTION);
        }
        if (key.startsWith("b:boss:")) {
            String template = key.substring(7);
            CodexCatalog.Boss boss = CodexCatalog.boss(template);
            if (boss == null) {
                boss = new CodexCatalog.Boss(template, CodexText.pretty(template), Material.WITHER_SKELETON_SKULL,
                        template.startsWith("dungeon"));
            }
            return bossCard(boss);
        }
        if (key.startsWith("b:")) {
            String id = key.substring(2);
            CodexCatalog.Entry entry = CodexCatalog.mob(id);
            if (entry != null) {
                return card(entry, Ledger.BESTIARY);
            }
            if (id.startsWith("dungeon:")) {
                String raw = id.substring(8).replaceFirst("^dungeon_", "");
                return new Card(key, CodexText.pretty(raw), Material.ZOMBIE_HEAD, CodexCatalog.MOB_DUNGEON,
                        CodexTiers.Scale.MOB_COMMON, "Seen on a dungeon floor.", Ledger.BESTIARY, null);
            }
        }
        return null;
    }

    private static Card card(CodexCatalog.Entry entry, Ledger ledger) {
        CodexCatalog.Info info = CodexCatalog.info(entry.id());
        return new Card(ledger.prefix() + entry.id(), entry.name(), entry.icon(), entry.category(),
                info.scale(), info.hint(), ledger, null);
    }

    private static Card bossCard(CodexCatalog.Boss boss) {
        String hint = boss.dungeon()
                ? "Waits behind a dungeon floor's last door."
                : "A BossEngine boss. Everyone who dealt damage gets the kill.";
        return new Card("b:boss:" + boss.templateId(), boss.name(), boss.icon(),
                boss.dungeon() ? CodexCatalog.MOB_DUNGEON : CodexCatalog.MOB_BOSSES,
                CodexTiers.Scale.BOSS, hint, Ledger.BESTIARY, boss);
    }

    // ------------------------------------------------------------------ states

    public static State state(CodexService service, Player player, Card card) {
        long count = service.count(player, card.key());
        int tier = CodexTiers.tier(card.scale(), count);
        int claimed = Math.min(tier, service.claimed(player, card.key()));
        return new State(card, count, tier, claimed, CodexTiers.next(card.scale(), count),
                CodexTiers.fill(card.scale(), count));
    }

    public static List<State> states(CodexService service, Player player, Ledger ledger, String category) {
        List<State> states = new ArrayList<>();
        for (Card card : cards(ledger, category)) {
            states.add(state(service, player, card));
        }
        if (ledger == Ledger.BESTIARY
                && (CodexCatalog.ALL.equals(category) || CodexCatalog.MOB_DUNGEON.equals(category))) {
            // Dungeon mobs the catalog doesn't know yet still get a card once you've met them.
            for (String id : service.killIds(player)) {
                if (!id.startsWith("dungeon:") || CodexCatalog.mob(id) != null
                        || CodexCatalog.boss(id.substring(8)) != null) {
                    continue;
                }
                Card card = card("b:" + id);
                if (card != null) {
                    states.add(state(service, player, card));
                }
            }
        }
        return states;
    }

    // ------------------------------------------------------------------ summaries

    /** Level, discovery and claim totals for one ledger (cached on the ledger version). */
    public static Summary summary(CodexService service, Player player, Ledger ledger) {
        if (service == null || player == null) {
            return new Summary(0, 0, 0, 0, 0, CodexTiers.Reward.NONE, 0);
        }
        Map<UUID, Cached> cache = ledger == Ledger.COLLECTION ? COLLECTION_CACHE : BESTIARY_CACHE;
        long version = service.version(player);
        Cached cached = cache.get(player.getUniqueId());
        // Off the main thread (TAB / PlaceholderAPI) a slightly stale answer beats walking BossEngine async.
        if (cached != null && !Bukkit.isPrimaryThread()) {
            return cached.summary();
        }
        int bosses = CodexCatalog.bosses().size();
        if (cached != null && cached.version() == version && cached.bosses() == bosses) {
            return cached.summary();
        }
        Summary summary = compute(service, player, ledger);
        cache.put(player.getUniqueId(), new Cached(version, bosses, summary));
        return summary;
    }

    /**
     * Level from the last computed summary only — safe off the main thread (stat reads from TAB /
     * placeholders). Returns 0 until a main-thread read has filled the cache.
     */
    public static int cachedLevel(UUID playerId, Ledger ledger) {
        Cached cached = (ledger == Ledger.COLLECTION ? COLLECTION_CACHE : BESTIARY_CACHE).get(playerId);
        return cached == null ? 0 : cached.summary().level();
    }

    public static void forget(UUID playerId) {
        COLLECTION_CACHE.remove(playerId);
        BESTIARY_CACHE.remove(playerId);
    }

    private static Summary compute(CodexService service, Player player, Ledger ledger) {
        int level = 0;
        int found = 0;
        int total = 0;
        int maxed = 0;
        int claimable = 0;
        CodexTiers.Reward pending = CodexTiers.Reward.NONE;
        for (State state : states(service, player, ledger, CodexCatalog.ALL)) {
            total++;
            level += state.tier();
            if (state.found()) {
                found++;
            }
            if (state.maxed()) {
                maxed++;
            }
            if (state.claimable() > 0) {
                claimable += state.claimable();
                pending = pending.plus(state.pending());
            }
        }
        return new Summary(level, found, total, maxed, claimable, pending, total * CodexTiers.MAX_TIER);
    }

    /** Cheap-enough main-thread check used by the Manager button and the hub. */
    public static boolean primary() {
        return Bukkit.isPrimaryThread();
    }

    // ------------------------------------------------------------------ goals

    /**
     * The entries closest to their next tier across both ledgers — the hub's "Next up" row.
     * Found and not maxed only; ties broken by the smaller remaining amount.
     */
    public static List<State> nextUp(CodexService service, Player player, int limit) {
        List<State> candidates = new ArrayList<>();
        for (Ledger ledger : Ledger.values()) {
            for (State state : states(service, player, ledger, CodexCatalog.ALL)) {
                if (state.found() && !state.maxed()) {
                    candidates.add(state);
                }
            }
        }
        candidates.sort(Comparator
                .comparingDouble(State::fill).reversed()
                .thenComparingLong(state -> state.next() - state.count()));
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    /** Every state with unclaimed tiers, both ledgers. */
    public static List<State> claimables(CodexService service, Player player) {
        List<State> out = new ArrayList<>();
        for (Ledger ledger : Ledger.values()) {
            for (State state : states(service, player, ledger, CodexCatalog.ALL)) {
                if (state.claimable() > 0) {
                    out.add(state);
                }
            }
        }
        return out;
    }
}
