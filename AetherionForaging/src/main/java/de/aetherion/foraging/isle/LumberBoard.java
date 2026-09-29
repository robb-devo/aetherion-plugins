package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.isle.ForageItems.FindData;
import de.aetherion.foraging.isle.ForageItems.Grade;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Lumber Board at the Landing — Pell's three rotating orders per forager, paid well above what the
 * wood is worth, plus Foraging XP and Warden Standing. Two kinds are hand-ins (typed logs, a graded
 * Crown Find); the rest fill while you work: fells in a named district, Perfect fells, a Titan, Crown
 * Finds caught, critters caught, deadfalls dodged. A finished slot restocks after a short break; the
 * whole board rerolls free every half hour (or for coins). Board Rates pays more.
 */
public final class LumberBoard {

    public static final int SLOTS = 3;
    public static final long RESTOCK_MS = 150_000L;
    public static final long REROLL_MS = 30L * 60_000L;
    public static final long REROLL_COST = 250L;

    public enum Kind {
        LOGS, FIND, FELLS, PERFECTS, TITAN, CATCHES, CRITTERS, DEADFALLS;

        boolean handIn() {
            return this == LOGS || this == FIND;
        }
    }

    /** {@code target}: wood key (LOGS), grove id (FELLS), "find:GRADE" (FIND), "" otherwise. */
    public static final class Order {
        public final Kind kind;
        public final String target;
        public final int amount;
        public int progress;
        public final long coins;
        public final int standing;

        public Order(Kind kind, String target, int amount, int progress, long coins, int standing) {
            this.kind = kind;
            this.target = target == null ? "" : target;
            this.amount = amount;
            this.progress = progress;
            this.coins = coins;
            this.standing = standing;
        }

        public boolean done() {
            return !kind.handIn() && progress >= amount;
        }

        String encode() {
            return kind.name() + "|" + target + "|" + amount + "|" + progress + "|" + coins + "|" + standing;
        }

        static Order decode(String raw) {
            if (raw == null || raw.equals("-") || raw.isBlank()) {
                return null;
            }
            String[] p = raw.split("\\|", -1);
            if (p.length < 6) {
                return null;
            }
            try {
                return new Order(Kind.valueOf(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        Long.parseLong(p[4]), Integer.parseInt(p[5]));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        public String title() {
            return switch (kind) {
                case LOGS -> {
                    Wood wood = Wood.byKey(target);
                    yield "Deliver " + amount + " " + (wood == null ? target : wood.colored()) + " §fLogs";
                }
                case FIND -> {
                    String[] parts = target.split(":");
                    Find find = Find.byId(parts[0]);
                    Grade grade = parts.length > 1 ? Grade.byName(parts[1]) : Grade.ROUGH;
                    yield "Bring a " + (grade == null ? "" : grade.colored() + "§f+ ") + (find == null ? parts[0] : find.colored());
                }
                case FELLS -> {
                    Grove grove = Grove.byId(target);
                    yield "Fell " + amount + " trees in " + (grove == null ? target : grove.colored());
                }
                case PERFECTS -> "Land " + amount + " §6Perfect §ffells";
                case TITAN -> "Bring down a §6Titan";
                case CATCHES -> "Catch " + amount + " §eCrown Finds";
                case CRITTERS -> "Catch " + amount + " isle critters";
                case DEADFALLS -> "Dodge " + amount + " §cWidowmakers §f(deadfalls)";
            };
        }

        public Material icon() {
            return switch (kind) {
                case LOGS -> {
                    Wood wood = Wood.byKey(target);
                    yield wood == null ? Material.OAK_LOG : wood.log();
                }
                case FIND -> {
                    Find find = Find.byId(target.split(":")[0]);
                    yield find == null ? Material.GOLD_NUGGET : find.icon;
                }
                case FELLS -> {
                    Grove grove = Grove.byId(target);
                    yield grove == null ? Material.IRON_AXE : grove.icon();
                }
                case PERFECTS -> Material.GOLDEN_AXE;
                case TITAN -> Material.NETHERITE_AXE;
                case CATCHES -> Material.GLOW_BERRIES;
                case CRITTERS -> Material.LEAD;
                case DEADFALLS -> Material.STRIPPED_OAK_LOG;
            };
        }
    }

    private final ForageIsle isle;

    LumberBoard(ForageIsle isle) {
        this.isle = isle;
    }

    /** Fill empty / restocked slots and run the free half-hourly reroll. */
    public void refresh(Player player, ForageProfile profile) {
        long now = System.currentTimeMillis();
        while (profile.orders.size() < SLOTS) {
            profile.orders.add(null);
        }
        if (profile.ordersRerollAt <= 0L || now >= profile.ordersRerollAt) {
            for (int i = 0; i < SLOTS; i++) {
                profile.orders.set(i, roll(profile, i));
            }
            profile.restockAt.clear();
            profile.ordersRerollAt = now + REROLL_MS;
            profile.dirty = true;
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (profile.orders.get(i) == null) {
                Long at = profile.restockAt.get(i);
                if (at == null || now >= at) {
                    profile.orders.set(i, roll(profile, i));
                    profile.restockAt.remove(i);
                    profile.dirty = true;
                }
            }
        }
    }

    public boolean paidReroll(Player player, ForageProfile profile) {
        if (!ForageBridge.takeCoins(player, REROLL_COST)) {
            return false;
        }
        profile.ordersRerollAt = 0L;
        refresh(player, profile);
        return true;
    }

    private Order roll(ForageProfile profile, int slot) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int level = WardenStanding.level(profile.standing);
        double pay = 1.0d + 0.08d * level;
        List<Kind> pool = level >= 2
                ? List.of(Kind.LOGS, Kind.LOGS, Kind.FELLS, Kind.FELLS, Kind.PERFECTS, Kind.FIND, Kind.CATCHES, Kind.CRITTERS,
                Kind.DEADFALLS, Kind.TITAN)
                : List.of(Kind.LOGS, Kind.LOGS, Kind.FELLS, Kind.FELLS, Kind.PERFECTS, Kind.CATCHES, Kind.FIND);
        Kind kind = pool.get(r.nextInt(pool.size()));
        // Slot 0 is always something you can do from the Landing with an axe.
        if (slot == 0 && (kind == Kind.TITAN || kind == Kind.FIND || kind == Kind.CRITTERS)) {
            kind = Kind.FELLS;
        }
        Grove[] groves = Grove.values();
        return switch (kind) {
            case LOGS -> {
                Wood[] woods = {Wood.OAK, Wood.BIRCH, Wood.SPRUCE, Wood.CHERRY, Wood.ACACIA, Wood.DARK_OAK, Wood.MANGROVE, Wood.JUNGLE};
                Wood wood = woods[r.nextInt(woods.length)];
                int amount = (int) Math.round((48 + r.nextInt(5) * 16) * (1.0d + 0.15d * level));
                yield new Order(kind, wood.key(), amount, 0, Math.round(amount * 3.2d * pay / Math.max(0.6d, wood.scale())), 1);
            }
            case FIND -> {
                Find find = Find.values()[r.nextInt(Find.values().length)];
                Grade grade = level >= 5 && r.nextInt(3) == 0 ? Grade.PRISTINE : level >= 3 && r.nextBoolean() ? Grade.FINE : Grade.ROUGH;
                long coins = Math.round(find.baseValue * grade.valueMult * 1.9d * pay) + 120L;
                yield new Order(kind, find.id() + ":" + grade.name(), 1, 0, coins, grade == Grade.PRISTINE ? 3 : grade == Grade.FINE ? 2 : 1);
            }
            case FELLS -> {
                Grove grove = groves[r.nextInt(groves.length)];
                int amount = 6 + r.nextInt(5) + level;
                yield new Order(kind, grove.id(), amount, 0, Math.round(amount * 55L * pay), 1);
            }
            case PERFECTS -> {
                int amount = 3 + r.nextInt(3) + level / 2;
                yield new Order(kind, "", amount, 0, Math.round(amount * 70L * pay), 1);
            }
            case TITAN -> new Order(kind, "", 1, 0, Math.round(900L * pay), 2);
            case CATCHES -> {
                int amount = 2 + r.nextInt(2);
                yield new Order(kind, "", amount, 0, Math.round(amount * 260L * pay), 1);
            }
            case CRITTERS -> {
                int amount = 2 + r.nextInt(2);
                yield new Order(kind, "", amount, 0, Math.round(amount * 140L * pay), 1);
            }
            case DEADFALLS -> {
                int amount = 2;
                yield new Order(kind, "", amount, 0, Math.round(amount * 160L * pay), 1);
            }
        };
    }

    // ------------------------------------------------------------------ progress while working

    void noteFell(Player player, ForageProfile profile, FellContext ctx, Grove grove) {
        for (int i = 0; i < profile.orders.size(); i++) {
            Order order = profile.orders.get(i);
            if (order == null || order.done()) {
                continue;
            }
            boolean hit = switch (order.kind) {
                case FELLS -> grove != null && grove.id().equals(order.target);
                case PERFECTS -> ctx.perfect() && !ctx.cleaver();
                case TITAN -> ctx.titan();
                default -> false;
            };
            if (hit) {
                advance(player, profile, i, order, 1);
            }
        }
    }

    void noteFind(Player player, ForageProfile profile, FindData data) {
        bump(player, profile, Kind.CATCHES);
    }

    void noteCritter(Player player, ForageProfile profile) {
        bump(player, profile, Kind.CRITTERS);
    }

    void noteDeadfall(Player player, ForageProfile profile) {
        bump(player, profile, Kind.DEADFALLS);
    }

    private void bump(Player player, ForageProfile profile, Kind kind) {
        for (int i = 0; i < profile.orders.size(); i++) {
            Order order = profile.orders.get(i);
            if (order != null && order.kind == kind && !order.done()) {
                advance(player, profile, i, order, 1);
            }
        }
    }

    private void advance(Player player, ForageProfile profile, int slot, Order order, int by) {
        order.progress = Math.min(order.amount, order.progress + by);
        profile.dirty = true;
        if (order.done()) {
            ForageText.bar(player, "§2✔ Board order ready §8· §f" + order.title() + " §8· §7collect at the Lumber Board");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.6f);
        } else {
            ForageText.bar(player, "§2Board §8· §f" + order.title() + " §7" + order.progress + "§8/§7" + order.amount);
        }
    }

    // ------------------------------------------------------------------ at the board

    /** Click on a slot: hand in (LOGS / FIND) or collect a finished order. Returns the chat line. */
    public String work(Player player, int slot) {
        ForageProfile profile = isle.profiles().get(player);
        refresh(player, profile);
        if (slot < 0 || slot >= profile.orders.size()) {
            return null;
        }
        Order order = profile.orders.get(slot);
        if (order == null) {
            return "§7Pell is still writing that one up.";
        }
        if (order.kind == Kind.LOGS) {
            Wood wood = Wood.byKey(order.target);
            if (wood == null) {
                return "§cThat order is unreadable.";
            }
            int have = ForageItems.countPlain(player.getInventory(), wood.log());
            if (have < order.amount) {
                return "§cYou have §f" + have + "§c/§f" + order.amount + " " + wood.display() + " Logs §8(plain logs only)§c.";
            }
            ForageItems.takePlain(player.getInventory(), wood.log(), order.amount);
        } else if (order.kind == Kind.FIND) {
            String[] parts = order.target.split(":");
            Find find = Find.byId(parts[0]);
            Grade grade = parts.length > 1 ? Grade.byName(parts[1]) : Grade.ROUGH;
            if (find == null || grade == null) {
                return "§cThat order is unreadable.";
            }
            if (ForageItems.countFinds(player.getInventory(), find, grade) < 1) {
                return "§cYou need a " + grade.colored() + "§c+ " + find.colored() + "§c.";
            }
            ForageItems.takeFinds(player.getInventory(), find, grade, 1);
        } else if (!order.done()) {
            return "§7" + order.title() + " §8· §f" + order.progress + "§8/§f" + order.amount + " §7— keep at it.";
        }
        return pay(player, profile, slot, order);
    }

    private String pay(Player player, ForageProfile profile, int slot, Order order) {
        double rates = 1.0d + Math.min(0.6d, 0.15d * ForageBridge.scale(player, ForageBridge.BOARD_RATES));
        long coins = Math.round(order.coins * rates);
        ForageBridge.coins(player, coins);
        ForageBridge.bonus(player, (int) Math.max(10L, coins / 8L));
        isle.standing().add(player, profile, order.standing, "board");
        profile.ordersDone++;
        profile.orders.set(slot, null);
        profile.restockAt.put(slot, System.currentTimeMillis() + RESTOCK_MS);
        profile.dirty = true;
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.1f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
        return "§2✔ Order filled §8· §6+" + ForageText.coins(coins) + " coins" + (rates > 1.0d ? " §8(Board Rates)" : "")
                + " §8· §2+" + order.standing + " Standing";
    }
}
