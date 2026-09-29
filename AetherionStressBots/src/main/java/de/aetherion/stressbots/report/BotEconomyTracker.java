package de.aetherion.stressbots.report;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.stressbots.role.BotRole;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wallet sampling for {@code /botreport}: sources (starter, AH collect, bazaar sell)
 * vs sinks (AH/Bazaar buy). Coins have no Bukkit event, so we diff balances.
 */
public final class BotEconomyTracker {

    private final Map<UUID, Long> lastWallet = new ConcurrentHashMap<>();
    private final AtomicLong wallets = new AtomicLong();
    private final AtomicLong sources = new AtomicLong();
    private final AtomicLong sinks = new AtomicLong();
    private final AtomicLong starterGranted = new AtomicLong();
    private final AtomicInteger ahClicks = new AtomicInteger();
    private final AtomicInteger bazaarClicks = new AtomicInteger();
    private final AtomicInteger listClicks = new AtomicInteger();
    private final AtomicInteger buyClicks = new AtomicInteger();

    public void grantStarter(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        starterGranted.addAndGet(amount);
        sources.addAndGet(amount);
        sample(player, "starter", true);
    }

    public void noteMarketClick(String activity, int slot) {
        if (activity == null) {
            return;
        }
        if (BotActivityTracker.AH.equals(activity) || activity.contains("auction")) {
            ahClicks.incrementAndGet();
        } else if (BotActivityTracker.BAZAAR.equals(activity) || activity.contains("bazaar")) {
            bazaarClicks.incrementAndGet();
        }
        if (slot == 49) {
            listClicks.incrementAndGet();
        }
        if (slot == 11) {
            buyClicks.incrementAndGet();
        }
    }

    public void sample(Player player, String activity, boolean force) {
        if (player == null) {
            return;
        }
        CoinAccess coins = AetherServices.coins();
        if (coins == null) {
            return;
        }
        long now = coins.get(player);
        Long previous = lastWallet.put(player.getUniqueId(), now);
        if (previous == null) {
            return;
        }
        long delta = now - previous;
        if (delta > 0) {
            sources.addAndGet(delta);
        } else if (delta < 0) {
            sinks.addAndGet(-delta);
        }
    }

    public void forget(UUID id) {
        if (id != null) {
            lastWallet.remove(id);
        }
    }

    public Snapshot snapshot(int deaths, int stuck, Map<String, Integer> mix, int online) {
        long total = 0L;
        CoinAccess coins = AetherServices.coins();
        if (coins != null) {
            for (Long value : lastWallet.values()) {
                if (value != null) {
                    total += value;
                }
            }
        }
        wallets.set(total);
        return new Snapshot(
                online,
                total,
                sources.get(),
                sinks.get(),
                starterGranted.get(),
                ahClicks.get(),
                bazaarClicks.get(),
                listClicks.get(),
                buyClicks.get(),
                deaths,
                stuck,
                mix == null ? Map.of() : Map.copyOf(mix)
        );
    }

    public List<String> lore(Snapshot snap) {
        List<String> lines = new ArrayList<>();
        lines.add("§7deaths §f" + snap.deaths() + " §8· §7stuck §f" + snap.stuck());
        lines.add("§7wallets §6" + snap.wallets() + "c §8· §7net §"
                + (snap.sources() >= snap.sinks() ? "a+" : "c")
                + (snap.sources() - snap.sinks()));
        lines.add("§7AH/BZ clicks §f" + snap.ahClicks() + "/" + snap.bazaarClicks()
                + " §8· §7list/buy §f" + snap.listClicks() + "/" + snap.buyClicks());
        if (!snap.mix().isEmpty()) {
            lines.add("§7mix §f" + formatMix(snap.mix(), 4));
        }
        return lines;
    }

    public String format(Snapshot snap) {
        StringBuilder out = new StringBuilder();
        out.append("Activity mix: ").append(formatMix(snap.mix(), 12)).append('\n');
        out.append("Deaths=").append(snap.deaths())
                .append(" stuck=").append(snap.stuck())
                .append(" online=").append(snap.online()).append('\n');
        out.append("Economy:\n");
        out.append("  wallets=").append(snap.wallets()).append(" coins across ")
                .append(snap.online()).append(" bots\n");
        out.append("  sources=").append(snap.sources())
                .append(" (starter grants=").append(snap.starterGranted()).append(")\n");
        out.append("  sinks=").append(snap.sinks())
                .append(" net=").append(snap.sources() - snap.sinks()).append('\n');
        out.append("  AH clicks=").append(snap.ahClicks())
                .append(" Bazaar clicks=").append(snap.bazaarClicks())
                .append(" list=").append(snap.listClicks())
                .append(" buy-confirm=").append(snap.buyClicks()).append('\n');
        return out.toString();
    }

    static String formatMix(Map<String, Integer> mix, int limit) {
        if (mix == null || mix.isEmpty()) {
            return "(none)";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(mix.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (n++ >= limit) {
                break;
            }
            parts.add(entry.getValue() + " " + entry.getKey());
        }
        return String.join(", ", parts);
    }

    public record Snapshot(
            int online,
            long wallets,
            long sources,
            long sinks,
            long starterGranted,
            int ahClicks,
            int bazaarClicks,
            int listClicks,
            int buyClicks,
            int deaths,
            int stuck,
            Map<String, Integer> mix
    ) {
    }

    public static boolean isCropName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("wheat") || n.contains("carrot") || n.contains("potato")
                || n.contains("beet") || n.contains("nether_wart") || n.contains("cocoa")
                || n.contains("melon") || n.contains("pumpkin") || n.contains("sugar_cane")
                || n.contains("berry");
    }

    public static boolean isFarmRole(BotRole role) {
        return role == BotRole.FARM;
    }
}
