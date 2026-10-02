package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Grade;
import de.aetherion.foraging.isle.ForageItems.Tonic;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The Woodwright's bench at the Bell Lodge. Tamsin turns forage into <b>Grove Marks</b>: permanent
 * upgrades that live on the forager, not on an axe, so they survive every tool swap. Five lines:
 * <ul>
 *   <li><b>Keen Edge</b> — the fell marker moves slower (+5% time per tier); III and V widen the CHOP window.</li>
 *   <li><b>Deep Roots</b> — +1 wood cap per tier on every isle tree.</li>
 *   <li><b>Canopy Eye</b> — Crown Finds +1.2% per tier and better grades.</li>
 *   <li><b>Sure Grip</b> — the miss cooldown shrinks 12% per tier; V keeps your streak through one miss
 *       every three minutes.</li>
 *   <li><b>Wind Step</b> — softer falls on the isle, slow-fall after an updraft (III), the canopy always
 *       catches you (V), and Widowmaker limbs hurt less.</li>
 * </ul>
 * Every mark is paid in district wood, heartwood, Crown Finds (top tiers) and coins. Warden Standing
 * gates tiers III–V. The bench also makes three consumables: Sap Lure (the isle's bait), Crown Shaker
 * and Heartwood Incense.
 */
public final class Woodwright implements Listener {

    public static final String KEEN_EDGE = "keen_edge";
    public static final String DEEP_ROOTS = "deep_roots";
    public static final String CANOPY_EYE = "canopy_eye";
    public static final String SURE_GRIP = "sure_grip";
    public static final String WIND_STEP = "wind_step";
    public static final String CHARGE_SHAKER = "crown_shaker";
    public static final int MAX_TIER = 5;
    private static final long SECOND_WIND_MS = 180_000L;

    public record Line(String id, String display, Material icon, String color, Wood[] woods, String[] perTier) {
    }

    public record Cost(Wood wood, int logs, int heartwood, Grade findGrade, long coins, int standingLevel) {
        public List<String> lore(Player player) {
            PlayerInventory inv = player.getInventory();
            List<String> out = new ArrayList<>();
            int haveLogs = ForageItems.countPlain(inv, wood.log());
            out.add(tick(haveLogs >= logs) + " §f" + logs + "× " + wood.colored() + " Log §8(" + haveLogs + ")");
            if (heartwood > 0) {
                int have = ForageItems.countHeartwood(inv, null);
                out.add(tick(have >= heartwood) + " §f" + heartwood + "× §dheartwood §7(any) §8(" + have + ")");
            }
            if (findGrade != null) {
                int have = ForageItems.countFinds(inv, null, findGrade);
                out.add(tick(have >= 1) + " §f1× " + findGrade.colored() + "§7+ Crown Find §8(" + have + ")");
            }
            long bal = ForageBridge.balance(player);
            out.add(tick(bal >= coins) + " §6" + ForageText.coins(coins) + " coins");
            return out;
        }

        private static String tick(boolean ok) {
            return ok ? "§a✔" : "§c✘";
        }
    }

    public static final List<Line> LINES = List.of(
            new Line(KEEN_EDGE, "Keen Edge", Material.IRON_AXE, "§e",
                    new Wood[] {Wood.OAK, Wood.BIRCH, Wood.SPRUCE, Wood.CHERRY, Wood.JUNGLE},
                    new String[] {"Marker +5% slower", "Marker +10% slower", "CHOP window +1", "Marker +20% slower",
                            "CHOP window +2"}),
            new Line(DEEP_ROOTS, "Deep Roots", Material.ROOTED_DIRT, "§6",
                    new Wood[] {Wood.DARK_OAK, Wood.MANGROVE, Wood.JUNGLE, Wood.DARK_OAK, Wood.MANGROVE},
                    new String[] {"+1 wood cap", "+2 wood cap", "+3 wood cap", "+4 wood cap", "+5 wood cap"}),
            new Line(CANOPY_EYE, "Canopy Eye", Material.HONEYCOMB, "§d",
                    new Wood[] {Wood.ACACIA, Wood.CHERRY, Wood.MANGROVE, Wood.ACACIA, Wood.CHERRY},
                    new String[] {"Crown Finds +1.2%", "+2.4% · better grades", "+3.6%", "+4.8%", "+6.0% · best grades"}),
            new Line(SURE_GRIP, "Sure Grip", Material.LEATHER, "§b",
                    new Wood[] {Wood.SPRUCE, Wood.DARK_OAK, Wood.ACACIA, Wood.SPRUCE, Wood.BIRCH},
                    new String[] {"Miss cooldown −12%", "−24%", "−36%", "−48%", "−60% · Second Wind"}),
            new Line(WIND_STEP, "Wind Step", Material.FEATHER, "§f",
                    new Wood[] {Wood.CHERRY, Wood.JUNGLE, Wood.MANGROVE, Wood.CHERRY, Wood.JUNGLE},
                    new String[] {"Half fall damage on the isle", "Limbs hurt 20% less", "Slow-fall after updrafts",
                            "Limbs hurt 40% less", "The canopy always catches you"})
    );

    private static final int[] LOGS = {64, 96, 128, 192, 256};
    private static final int[] HEART = {0, 1, 2, 3, 5};
    private static final Grade[] FIND = {null, null, Grade.ROUGH, Grade.FINE, Grade.PRISTINE};
    private static final long[] COINS = {500L, 1500L, 4000L, 10000L, 25000L};
    private static final int[] STANDING = {0, 0, 2, 4, 6};

    private final ForageIsle isle;
    private final java.util.Map<java.util.UUID, Long> secondWindAt = new java.util.concurrent.ConcurrentHashMap<>();

    Woodwright(ForageIsle isle) {
        this.isle = isle;
    }

    public static Line line(String id) {
        for (Line line : LINES) {
            if (line.id().equals(id)) {
                return line;
            }
        }
        return null;
    }

    public static Cost cost(Line line, int tier) {
        int t = Math.max(1, Math.min(MAX_TIER, tier)) - 1;
        return new Cost(line.woods()[t], LOGS[t], HEART[t], FIND[t], COINS[t], STANDING[t]);
    }

    public static boolean affordable(Player player, Cost cost) {
        PlayerInventory inv = player.getInventory();
        return ForageItems.countPlain(inv, cost.wood().log()) >= cost.logs()
                && ForageItems.countHeartwood(inv, null) >= cost.heartwood()
                && (cost.findGrade() == null || ForageItems.countFinds(inv, null, cost.findGrade()) >= 1)
                && ForageBridge.balance(player) >= cost.coins();
    }

    /** Forges the next tier of {@code lineId}. Returns the chat line to show. */
    public String forge(Player player, String lineId) {
        Line line = line(lineId);
        if (line == null) {
            return "§cNo such mark.";
        }
        ForageProfile profile = isle.profiles().get(player);
        int next = profile.mark(line.id()) + 1;
        if (next > MAX_TIER) {
            return "§7" + line.display() + " is already at V.";
        }
        Cost cost = cost(line, next);
        if (WardenStanding.level(profile.standing) < cost.standingLevel()) {
            return "§cTamsin shakes her head §8· §7" + line.display() + " " + ForageText.roman(next) + " needs Warden Standing §f"
                    + WardenStanding.title(cost.standingLevel()) + "§7.";
        }
        if (!affordable(player, cost)) {
            return "§cNot enough for " + line.display() + " " + ForageText.roman(next) + " §8— check the list.";
        }
        if (!ForageBridge.takeCoins(player, cost.coins())) {
            return "§cCoins could not be taken.";
        }
        PlayerInventory inv = player.getInventory();
        ForageItems.takePlain(inv, cost.wood().log(), cost.logs());
        if (cost.heartwood() > 0) {
            ForageItems.takeHeartwood(inv, null, cost.heartwood());
        }
        if (cost.findGrade() != null) {
            ForageItems.takeFinds(inv, null, cost.findGrade(), 1);
        }
        profile.marks.put(line.id(), next);
        profile.dirty = true;
        isle.standing().add(player, profile, 1, "grove mark");
        ForageBridge.bonus(player, 30 * next);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1.2, 0), 18, 0.4, 0.4, 0.4, 0.0);
        return "§6✦ Grove Mark §8· " + line.color() + line.display() + " " + ForageText.roman(next) + " §8· §7"
                + line.perTier()[next - 1];
    }

    public String craft(Player player, Tonic tonic) {
        PlayerInventory inv = player.getInventory();
        switch (tonic) {
            case SAP_LURE -> {
                if (!isle.plugin().getConfig().getBoolean("bait.enabled", true)) {
                    return "§7Sap Lures are switched off on this server.";
                }
                if (ForageItems.countPlain(inv, Material.ACACIA_LOG) < 32 || ForageBridge.balance(player) < 150L) {
                    return "§cSap Lure needs §f32 Acacia Logs §cand §6150 coins§c.";
                }
                ForageBridge.takeCoins(player, 150L);
                ForageItems.takePlain(inv, Material.ACACIA_LOG, 32);
            }
            case CROWN_SHAKER -> {
                if (ForageItems.countHeartwood(inv, null) < 1 || ForageBridge.balance(player) < 400L) {
                    return "§cCrown Shaker needs §f1 heartwood §cand §6400 coins§c.";
                }
                ForageBridge.takeCoins(player, 400L);
                ForageItems.takeHeartwood(inv, null, 1);
            }
            case HEARTWOOD_INCENSE -> {
                if (ForageItems.countFinds(inv, null, Grade.ROUGH) < 2 || ForageBridge.balance(player) < 600L) {
                    return "§cHeartwood Incense needs §f2 Crown Finds §cand §6600 coins§c.";
                }
                ForageBridge.takeCoins(player, 600L);
                ForageItems.takeFinds(inv, null, Grade.ROUGH, 2);
            }
        }
        ForageBridge.give(player, ForageItems.tonic(tonic, 1));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.2f);
        return "§aTamsin hands you a " + tonic.display + "§a.";
    }

    // ------------------------------------------------------------------ effects

    public static int zoneBonus(ForageProfile profile) {
        int tier = profile.mark(KEEN_EDGE);
        return tier >= 5 ? 2 : tier >= 3 ? 1 : 0;
    }

    public static double strikeFactor(ForageProfile profile) {
        int tier = profile.mark(KEEN_EDGE);
        return 1.0d + switch (tier) {
            case 1 -> 0.05d;
            case 2, 3 -> 0.10d;
            case 4, 5 -> 0.20d;
            default -> 0.0d;
        };
    }

    public static int woodCapBonus(ForageProfile profile) {
        return profile.mark(DEEP_ROOTS);
    }

    public static double missFactor(ForageProfile profile) {
        return Math.max(0.3d, 1.0d - 0.12d * profile.mark(SURE_GRIP));
    }

    /** Sure Grip V: one miss every three minutes keeps the streak. */
    public boolean secondWind(Player player, ForageProfile profile) {
        if (profile.mark(SURE_GRIP) < MAX_TIER) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = secondWindAt.get(player.getUniqueId());
        if (last != null && now - last < SECOND_WIND_MS) {
            return false;
        }
        secondWindAt.put(player.getUniqueId(), now);
        return true;
    }

    public static double fallFactor(ForageProfile profile) {
        return profile.mark(WIND_STEP) >= 1 ? 0.5d : 1.0d;
    }

    public static boolean slowFallAfterLift(ForageProfile profile) {
        return profile.mark(WIND_STEP) >= 3;
    }

    public static boolean alwaysCaught(ForageProfile profile) {
        return profile.mark(WIND_STEP) >= 5;
    }

    public double hazardFactor(ForageProfile profile) {
        int tier = profile.mark(WIND_STEP);
        return tier >= 4 ? 0.6d : tier >= 2 ? 0.8d : 1.0d;
    }

    void forget(java.util.UUID id) {
        secondWindAt.remove(id);
    }

    // ------------------------------------------------------------------ using consumables + item guards

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            ItemStack off = event.getItem();
            if (off != null && ForageItems.isOurs(off)) {
                event.setCancelled(true);
            }
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || !ForageItems.isOurs(hand)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Tonic tonic = ForageItems.readTonic(hand);
        if (tonic == null) {
            return;
        }
        Player player = event.getPlayer();
        ForageProfile profile = isle.profiles().get(player);
        switch (tonic) {
            case CROWN_SHAKER -> {
                profile.addCharge(CHARGE_SHAKER, 1);
                consume(player, hand);
                ForageText.bar(player, "§e✦ Crown Shaker loaded §8· §7charges: §f" + profile.charge(CHARGE_SHAKER));
                player.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_WOOD_HIT, 1.0f, 1.6f);
            }
            case HEARTWOOD_INCENSE -> {
                long now = System.currentTimeMillis();
                profile.incenseUntil = Math.max(profile.incenseUntil, now) + 600_000L;
                profile.dirty = true;
                consume(player, hand);
                ForageText.bar(player, "§d✦ Heartwood Incense §8· §7heartwood ×2 for §f"
                        + ForageText.clock((profile.incenseUntil - now) / 1000L));
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1.8, 0), 3, 0.1, 0.1, 0.1, 0.01);
            }
            case SAP_LURE -> {
                Block block = event.getClickedBlock();
                if (action != Action.RIGHT_CLICK_BLOCK || block == null || !isle.isLivingLog(block)) {
                    ForageText.bar(player, "§7Right-click the trunk of a §fliving tree §7to hang the lure.");
                    return;
                }
                if (!isle.onIsle(block.getLocation())) {
                    ForageText.bar(player, "§7Sap Lures only work on Foraging Eldervale.");
                    return;
                }
                isle.lure(block.getLocation().add(0.5, 0.5, 0.5), 180_000L);
                consume(player, hand);
                ForageText.bar(player, "§6✦ Sap Lure hung §8· §7fells within 6 blocks: Crown Finds ×3 for 3:00");
                player.playSound(block.getLocation(), Sound.BLOCK_HONEY_BLOCK_PLACE, 1.0f, 0.9f);
                block.getWorld().spawnParticle(Particle.DRIPPING_HONEY, block.getLocation().add(0.5, 0.9, 0.5), 8, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    private static void consume(Player player, ItemStack hand) {
        hand.setAmount(hand.getAmount() - 1);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (ForageItems.isOurs(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (ForageItems.isOurs(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
