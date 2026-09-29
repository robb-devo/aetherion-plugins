package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.isle.ForageItems.FindData;
import de.aetherion.foraging.isle.ForageItems.Grade;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The Forest Ledger — where Foraging's collections live, kept by the Archivist in the Blossom Pagoda.
 * <ul>
 *   <li><b>Collections</b> read the real Codex counts (every log you ever chopped, anywhere — the Codex
 *   now hears chopped wood, see {@link ForageBridge#codexWood}). Nothing here keeps a second tally.
 *   Milestones pay once, at the Archivist.</li>
 *   <li><b>Collector rank</b>: every six claimed milestones, +0.2% heartwood chance on the isle.</li>
 *   <li><b>Find Cabinet</b>: one slot per find × grade, filled the first time you catch it. A full row
 *   (all four grades of one find) adds +1 wood cap to that district's wood; a full column (one grade
 *   across all seven finds) pays out big.</li>
 *   <li><b>Records</b>: the largest tree felled per district and the heaviest find per kind, live on a
 *   board beside the Archivist.</li>
 * </ul>
 */
public final class ForestLedger {

    public static final int[] MILESTONES = {100, 500, 2000, 6000, 15000, 40000};
    private static final long[] MILESTONE_COINS = {200L, 800L, 2500L, 6000L, 14000L, 30000L};
    private static final long[] COLUMN_COINS = {2000L, 6000L, 20000L, 60000L};
    private static final int[] COLUMN_STANDING = {1, 2, 3, 5};
    private static final String BOARD_TAG = "ae_grove_records";

    private final ForageIsle isle;
    private UUID boardId;

    ForestLedger(ForageIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ collections

    public static long codex(Player player, Wood wood) {
        return ForageBridge.codexBlocks(player, wood.key());
    }

    /** Milestones reached (by Codex count) for a wood. */
    public static int reached(Player player, Wood wood) {
        long have = codex(player, wood);
        int n = 0;
        while (n < MILESTONES.length && have >= MILESTONES[n]) {
            n++;
        }
        return n;
    }

    public static int claimed(ForageProfile profile, Wood wood) {
        return profile.ledgerPaid.getOrDefault(wood.key(), 0);
    }

    public static int collectorRank(ForageProfile profile) {
        int total = 0;
        for (int v : profile.ledgerPaid.values()) {
            total += v;
        }
        return total / 6;
    }

    public String claimCollection(Player player, Wood wood) {
        ForageProfile profile = isle.profiles().get(player);
        int reached = reached(player, wood);
        int claimed = claimed(profile, wood);
        if (claimed >= reached) {
            long next = claimed < MILESTONES.length ? MILESTONES[claimed] : -1;
            return next < 0 ? "§7" + wood.display() + " is fully collected." : "§7Next " + wood.display() + " milestone at §f"
                    + ForageText.coins(next) + " §7logs (you have §f" + ForageText.coins(codex(player, wood)) + "§7).";
        }
        int rankBefore = collectorRank(profile);
        long coins = 0;
        while (claimed < reached) {
            coins += MILESTONE_COINS[claimed];
            if (claimed >= 2) {
                isle.standing().add(player, profile, claimed >= 5 ? 2 : 1, "collection");
            }
            claimed++;
        }
        profile.ledgerPaid.put(wood.key(), claimed);
        profile.dirty = true;
        ForageBridge.coins(player, coins);
        ForageBridge.bonus(player, (int) (coins / 10L));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        String rank = collectorRank(profile) > rankBefore
                ? " §8· §dCollector rank " + ForageText.roman(collectorRank(profile)) : "";
        return "§2✔ Collection §8· " + wood.colored() + " " + ForageText.roman(claimed) + " §8· §6+" + ForageText.coins(coins)
                + " coins" + rank;
    }

    // ------------------------------------------------------------------ the cabinet

    void noteFind(Player player, ForageProfile profile, FindData data) {
        String key = data.find().id() + ":" + data.grade().name();
        if (profile.cabinet.add(key)) {
            profile.dirty = true;
            player.sendMessage("§d❖ Find Cabinet §8· §7new slot: " + data.grade().colored() + " " + data.find().colored());
        }
    }

    public static boolean has(ForageProfile profile, Find find, Grade grade) {
        return profile.cabinet.contains(find.id() + ":" + grade.name());
    }

    public static boolean rowFull(ForageProfile profile, Find find) {
        for (Grade grade : Grade.values()) {
            if (!has(profile, find, grade)) {
                return false;
            }
        }
        return true;
    }

    public static boolean columnFull(ForageProfile profile, Grade grade) {
        for (Find find : Find.values()) {
            if (!has(profile, find, grade)) {
                return false;
            }
        }
        return true;
    }

    /** +1 wood cap for every paid cabinet row whose district owns this wood. */
    public static int woodCapBonus(ForageProfile profile, Wood wood) {
        if (wood == null) {
            return 0;
        }
        Find find = Find.of(wood.grove());
        return profile.cabinetPaid.contains("row:" + find.id()) ? 1 : 0;
    }

    public String claimRow(Player player, Find find) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.cabinetPaid.contains("row:" + find.id())) {
            return "§7That row is already framed.";
        }
        if (!rowFull(profile, find)) {
            return "§7Catch every grade of " + find.colored() + " §7first.";
        }
        profile.cabinetPaid.add("row:" + find.id());
        profile.dirty = true;
        ForageBridge.coins(player, 3000L);
        isle.standing().add(player, profile, 2, "cabinet row");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        return "§d❖ Row framed §8· " + find.colored() + " §8· §6+3,000 coins §8· §a+1 wood cap in " + find.grove.colored();
    }

    public String claimColumn(Player player, Grade grade) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.cabinetPaid.contains("col:" + grade.name())) {
            return "§7That column is already framed.";
        }
        if (!columnFull(profile, grade)) {
            return "§7Catch a " + grade.colored() + " §7of every find first.";
        }
        profile.cabinetPaid.add("col:" + grade.name());
        profile.dirty = true;
        long coins = COLUMN_COINS[grade.ordinal()];
        ForageBridge.coins(player, coins);
        isle.standing().add(player, profile, COLUMN_STANDING[grade.ordinal()], "cabinet column");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        if (grade == Grade.HEARTSONG) {
            ForageText.card(player, "§6Heartsong Collector", "§7Every find, at its finest", 60);
        }
        return "§d❖ Column framed §8· " + grade.colored() + " §8· §6+" + ForageText.coins(coins) + " coins";
    }

    // ------------------------------------------------------------------ selling

    /** Sells every find in the inventory (or only one kind). Returns the chat line. */
    public String sellFinds(Player player, Find onlyKind, boolean keepBestOfEach) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        long total = 0;
        int sold = 0;
        java.util.Map<Find, Integer> bestSlot = new java.util.EnumMap<>(Find.class);
        if (keepBestOfEach) {
            for (int i = 0; i < contents.length; i++) {
                FindData data = ForageItems.readFind(contents[i]);
                if (data == null) {
                    continue;
                }
                Integer cur = bestSlot.get(data.find());
                FindData best = cur == null ? null : ForageItems.readFind(contents[cur]);
                if (best == null || data.value() > best.value()) {
                    bestSlot.put(data.find(), i);
                }
            }
        }
        for (int i = 0; i < contents.length; i++) {
            FindData data = ForageItems.readFind(contents[i]);
            if (data == null || (onlyKind != null && data.find() != onlyKind)) {
                continue;
            }
            if (keepBestOfEach && Integer.valueOf(i).equals(bestSlot.get(data.find()))) {
                continue;
            }
            total += (long) data.value() * contents[i].getAmount();
            sold += contents[i].getAmount();
            contents[i] = null;
        }
        if (sold == 0) {
            return "§7No Crown Finds to sell" + (keepBestOfEach ? " §8(your best of each kind is kept)" : "") + ".";
        }
        player.getInventory().setStorageContents(contents);
        ForageBridge.coins(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.1f);
        return "§6✔ Sold " + ForageText.plural(sold, "find") + " §8· §6+" + ForageText.coins(total) + " coins";
    }

    // ------------------------------------------------------------------ record board

    /** Every ~30 s: keep a live TextDisplay board beside the Archivist while someone's near. */
    void tickBoard() {
        double[] anchor = isle.config().castAnchor(ForageCast.ARCHIVIST);
        World world = isle.isleWorld();
        if (anchor == null || world == null) {
            return;
        }
        Location at = new Location(world, anchor[0] + 2.2d, anchor[1] + 1.8d, anchor[2]);
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            boardId = null;
            return;
        }
        boolean someone = false;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) < 40 * 40) {
                someone = true;
                break;
            }
        }
        TextDisplay board = board(at);
        if (!someone) {
            if (board != null) {
                board.remove();
                boardId = null;
            }
            return;
        }
        if (board == null) {
            board = world.spawn(at, TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setAlignment(TextDisplay.TextAlignment.LEFT);
                t.setShadowed(true);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(110, 12, 24, 10));
                t.setLineWidth(220);
                t.setPersistent(false);
                t.addScoreboardTag(BOARD_TAG);
            });
            boardId = board.getUniqueId();
        }
        board.text(ForageText.legacy(boardText()));
    }

    private TextDisplay board(Location at) {
        if (boardId != null) {
            Entity entity = Bukkit.getEntity(boardId);
            if (entity instanceof TextDisplay text && text.isValid()) {
                return text;
            }
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at, 3, 3, 3)) {
            if (entity instanceof TextDisplay text && text.getScoreboardTags().contains(BOARD_TAG)) {
                boardId = text.getUniqueId();
                return text;
            }
        }
        return null;
    }

    private String boardText() {
        StringBuilder out = new StringBuilder("§6§lIsle Records\n§7Largest fell per district\n");
        for (Grove grove : Grove.values()) {
            ForageProfiles.Record rec = isle.profiles().record("fell." + grove.id());
            out.append(grove.color()).append(grove.display()).append(" §8· ")
                    .append(rec == null ? "§8—" : "§f" + rec.name() + " §7" + rec.value() + " logs").append('\n');
        }
        out.append("\n§7Heaviest Crown Find\n");
        for (Find find : Find.values()) {
            ForageProfiles.Record rec = isle.profiles().record("find." + find.id());
            out.append(find.color).append(find.display).append(" §8· ")
                    .append(rec == null ? "§8—" : "§f" + rec.name() + " §7" + ForageText.grams(rec.value())).append('\n');
        }
        return out.toString().trim();
    }

    void removeBoard() {
        if (boardId != null) {
            Entity entity = Bukkit.getEntity(boardId);
            if (entity != null) {
                entity.remove();
            }
            boardId = null;
        }
    }

    static boolean isOurs(Entity entity) {
        return entity.getScoreboardTags().contains(BOARD_TAG);
    }
}
