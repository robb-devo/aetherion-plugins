package de.aetherion.foraging.isle;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One forager's Foraging Eldervale progress. Mutated on the main thread only. */
public final class ForageProfile {

    public final UUID id;
    public String name = "";

    /** Discovered districts (grove ids) and places (landmark ids). */
    public final Set<String> groves = new HashSet<>();
    public final Set<String> places = new HashSet<>();
    /** Isle fells per wood key — Grove Mastery reads this. */
    public final Map<String, Integer> fells = new HashMap<>();
    /** Mastery tiers already paid per wood key. */
    public final Map<String, Integer> masteryPaid = new HashMap<>();
    /** Grove Marks: line id → tier (0–5). */
    public final Map<String, Integer> marks = new HashMap<>();
    /** Collections milestones claimed per wood key. */
    public final Map<String, Integer> ledgerPaid = new HashMap<>();
    /** Find Cabinet: "find:grade" entries ever filed, plus claimed rows/columns. */
    public final Set<String> cabinet = new HashSet<>();
    public final Set<String> cabinetPaid = new HashSet<>();
    /** One-time flags (grove_walker, cartographer, first_titan…). */
    public final Set<String> flags = new HashSet<>();
    /** Consumable charges (crown_shaker…). */
    public final Map<String, Integer> charges = new HashMap<>();
    /** Largest fell per district (logs). */
    public final Map<String, Integer> bestFell = new HashMap<>();
    public final List<LumberBoard.Order> orders = new ArrayList<>();
    public final Map<Integer, Long> restockAt = new HashMap<>();

    public int standing;
    public long ordersRerollAt;
    public long incenseUntil;
    public long frostlitUntil;
    public long tailwindUntil;
    public int tourStep;

    public int totalFells;
    public int perfects;
    public int titans;
    public int finds;
    public int critters;
    public int deadfalls;
    public int ordersDone;

    public boolean dirty;

    public ForageProfile(UUID id) {
        this.id = id;
    }

    public int fells(Wood wood) {
        return fells.getOrDefault(wood.key(), 0);
    }

    public int mark(String line) {
        return marks.getOrDefault(line, 0);
    }

    public int charge(String key) {
        return charges.getOrDefault(key, 0);
    }

    public void addCharge(String key, int delta) {
        int next = Math.max(0, charge(key) + delta);
        if (next == 0) {
            charges.remove(key);
        } else {
            charges.put(key, next);
        }
        dirty = true;
    }

    void write(ConfigurationSection sec) {
        sec.set("name", name);
        sec.set("groves", new ArrayList<>(groves));
        sec.set("places", new ArrayList<>(places));
        sec.set("fells", fells.isEmpty() ? null : new HashMap<>(fells));
        sec.set("mastery-paid", masteryPaid.isEmpty() ? null : new HashMap<>(masteryPaid));
        sec.set("marks", marks.isEmpty() ? null : new HashMap<>(marks));
        sec.set("ledger-paid", ledgerPaid.isEmpty() ? null : new HashMap<>(ledgerPaid));
        sec.set("cabinet", new ArrayList<>(cabinet));
        sec.set("cabinet-paid", new ArrayList<>(cabinetPaid));
        sec.set("flags", new ArrayList<>(flags));
        sec.set("charges", charges.isEmpty() ? null : new HashMap<>(charges));
        sec.set("best-fell", bestFell.isEmpty() ? null : new HashMap<>(bestFell));
        List<String> encoded = new ArrayList<>();
        for (LumberBoard.Order order : orders) {
            encoded.add(order == null ? "-" : order.encode());
        }
        sec.set("orders", encoded);
        Map<String, Long> restock = new HashMap<>();
        restockAt.forEach((slot, at) -> restock.put(String.valueOf(slot), at));
        sec.set("restock", restock.isEmpty() ? null : restock);
        sec.set("standing", standing);
        sec.set("orders-reroll-at", ordersRerollAt);
        sec.set("incense-until", incenseUntil > System.currentTimeMillis() ? incenseUntil : null);
        sec.set("tour-step", tourStep);
        sec.set("stats.fells", totalFells);
        sec.set("stats.perfects", perfects);
        sec.set("stats.titans", titans);
        sec.set("stats.finds", finds);
        sec.set("stats.critters", critters);
        sec.set("stats.deadfalls", deadfalls);
        sec.set("stats.orders", ordersDone);
    }

    static ForageProfile read(UUID id, ConfigurationSection sec) {
        ForageProfile p = new ForageProfile(id);
        if (sec == null) {
            return p;
        }
        p.name = sec.getString("name", "");
        p.groves.addAll(sec.getStringList("groves"));
        p.places.addAll(sec.getStringList("places"));
        readInts(sec.getConfigurationSection("fells"), p.fells);
        readInts(sec.getConfigurationSection("mastery-paid"), p.masteryPaid);
        readInts(sec.getConfigurationSection("marks"), p.marks);
        readInts(sec.getConfigurationSection("ledger-paid"), p.ledgerPaid);
        p.cabinet.addAll(sec.getStringList("cabinet"));
        p.cabinetPaid.addAll(sec.getStringList("cabinet-paid"));
        p.flags.addAll(sec.getStringList("flags"));
        readInts(sec.getConfigurationSection("charges"), p.charges);
        readInts(sec.getConfigurationSection("best-fell"), p.bestFell);
        for (String raw : sec.getStringList("orders")) {
            p.orders.add(LumberBoard.Order.decode(raw));
        }
        ConfigurationSection restock = sec.getConfigurationSection("restock");
        if (restock != null) {
            for (String key : restock.getKeys(false)) {
                try {
                    p.restockAt.put(Integer.parseInt(key), restock.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        p.standing = sec.getInt("standing", 0);
        p.ordersRerollAt = sec.getLong("orders-reroll-at", 0L);
        p.incenseUntil = sec.getLong("incense-until", 0L);
        p.tourStep = sec.getInt("tour-step", 0);
        p.totalFells = sec.getInt("stats.fells", 0);
        p.perfects = sec.getInt("stats.perfects", 0);
        p.titans = sec.getInt("stats.titans", 0);
        p.finds = sec.getInt("stats.finds", 0);
        p.critters = sec.getInt("stats.critters", 0);
        p.deadfalls = sec.getInt("stats.deadfalls", 0);
        p.ordersDone = sec.getInt("stats.orders", 0);
        return p;
    }

    private static void readInts(ConfigurationSection sec, Map<String, Integer> into) {
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            into.put(key, sec.getInt(key));
        }
    }
}
