package de.aetherion.items.casino;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.util.GuiItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class CasinoGames {

    private static final long[] BETS = {10L, 50L, 100L, 250L, 500L, 1_000L, 2_500L, 5_000L, 10_000L, 25_000L, 50_000L};
    private static final int[] REDS = {
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    };

    /** Oval ring, clockwise from top-center. */
    private static final int[] RING_SLOTS = {
            4, 5, 6, 16, 25, 34, 42, 41, 40, 39, 38, 28, 19, 10, 2, 3
    };
    private static final int[] RING_NUMBERS = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30
    };
    private static final int BALL = 22;

    private static final int SLOTS_PAYOUTS = 1;
    private static final int SLOTS_LOGO = 4;
    private static final int SLOTS_PURSE = 7;
    private static final int SLOTS_RIGGER = 8;
    private static final int SLOTS_BACK = 45;
    private static final int SLOTS_BET = 47;
    private static final int SLOTS_LINE_BTN = 49;
    private static final int SLOTS_SPIN = 51;
    private static final int SLOTS_AUTO = 53;
    private static final int REEL_COLS = 7;
    private static final int REEL_ROWS = 4;
    private static final int[] REEL_STOP = {10, 18, 26, 34, 42, 50, 58};
    private static final String[] LOSS_LINES = {
            "Dead lines. House keeps the heat.",
            "That's a donation. I'll send a card.",
            "The reels looked. They declined.",
            "Zero. Beautiful. Tragic. Mine.",
            "You spun. The machine yawned.",
            "I've seen worse. I've also seen better. Mostly better.",
            "Coins came in. Coins stayed. That's the product.",
            "The pictures didn't like you today.",
            "Almost. That's a word people say before they leave.",
            "House wins. I win. Same person, honestly."
    };

    private static final int ROULETTE_BACK = 45;
    private static final int ROULETTE_RED = 46;
    private static final int ROULETTE_BLACK = 47;
    private static final int ROULETTE_GREEN = 48;
    private static final int ROULETTE_BET = 49;
    private static final int ROULETTE_SPIN = 51;
    private static final int ROULETTE_PURSE = 53;

    private final AetherionItems plugin;
    private final CoinService coins;
    private final CasinoService casino;
    private final Map<UUID, Long> lastBet = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastLines = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lossTick = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> riggedOn = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bankedFreeSpins = new ConcurrentHashMap<>();
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    CasinoGames(AetherionItems plugin, CoinService coins, CasinoService casino) {
        this.plugin = plugin;
        this.coins = coins;
        this.casino = casino;
    }

    boolean isTable(InventoryHolder holder) {
        return holder instanceof SlotsHolder || holder instanceof RouletteHolder;
    }

    void clear(UUID playerId) {
        lastBet.remove(playerId);
        lastLines.remove(playerId);
        lossTick.remove(playerId);
        riggedOn.remove(playerId);
        bankedFreeSpins.remove(playerId);
        spinning.remove(playerId);
    }

    void openSlots(Player player) {
        SlotsHolder holder = new SlotsHolder(betFor(player), linesFor(player));
        holder.rigged = isDev(player) && riggedOn.getOrDefault(player.getUniqueId(), false);
        Integer banked = bankedFreeSpins.remove(player.getUniqueId());
        if (banked != null && banked > 0) {
            holder.freeSpinsLeft = banked;
        }
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Slots");
        paintSlots(player, inventory, holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.35f);
        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.25f, 1.6f);
        if (holder.freeSpinsLeft > 0) {
            CasinoService.say(player, "§d" + holder.freeSpinsLeft + " free spins§f still on the glass. Don't waste them.");
        }
    }

    void openRoulette(Player player) {
        RouletteHolder holder = new RouletteHolder(betFor(player));
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Roulette");
        paintRoulette(player, inventory, holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 0.7f);
    }

    /**
     * ESC / close safety: keep live spins on the table, bank free spins when leaving idle.
     */
    void handleClose(Player player, InventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (holder instanceof SlotsHolder slots) {
            if (slots.spinning || spinning.contains(id)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> reopenSlots(player, slots));
                return;
            }
            if (slots.freeSpinsLeft > 0) {
                int left = slots.freeSpinsLeft;
                bankFreeSpins(player, slots);
                slots.freeSpinsLeft = 0;
                CasinoService.say(player, "§d" + left
                        + " free spins§f banked. Open Slots again — they wait on the glass.");
            }
            return;
        }
        if (holder instanceof RouletteHolder roulette) {
            if (roulette.spinning || spinning.contains(id)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> reopenRoulette(player, roulette));
            }
        }
    }

    private void reopenSlots(Player player, SlotsHolder holder) {
        if (!player.isOnline()) {
            return;
        }
        if (!holder.spinning && !spinning.contains(player.getUniqueId())) {
            return;
        }
        if (stillSlots(player, holder)) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Slots");
        paintSlots(player, inventory, holder);
        player.openInventory(inventory);
        CasinoService.say(player, "Spin's live. ESC doesn't void the table — wait it out, then Back.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.8f);
    }

    private void reopenRoulette(Player player, RouletteHolder holder) {
        if (!player.isOnline()) {
            return;
        }
        if (!holder.spinning && !spinning.contains(player.getUniqueId())) {
            return;
        }
        if (stillRoulette(player, holder)) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Roulette");
        paintRoulette(player, inventory, holder);
        player.openInventory(inventory);
        CasinoService.say(player, "Ball's still rolling. ESC doesn't void the spin.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.8f);
    }

    private void bankFreeSpins(Player player, SlotsHolder holder) {
        if (holder.freeSpinsLeft > 0) {
            bankedFreeSpins.put(player.getUniqueId(), holder.freeSpinsLeft);
        } else {
            bankedFreeSpins.remove(player.getUniqueId());
        }
    }

    void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof SlotsHolder) && !(holder instanceof RouletteHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (holder instanceof SlotsHolder slots) {
            clickSlots(player, event.getView().getTopInventory(), slots, event.getRawSlot(), event.getClick());
            return;
        }
        clickRoulette(player, event.getView().getTopInventory(), (RouletteHolder) holder, event.getRawSlot(), event.getClick());
    }

    private void clickSlots(Player player, Inventory inventory, SlotsHolder holder, int slot, ClickType click) {
        if (slot == SLOTS_BACK) {
            if (holder.spinning || spinning.contains(player.getUniqueId())) {
                CasinoService.say(player, "Reels are live. Wait it out — ESC won't void the spin.");
                return;
            }
            bankFreeSpins(player, holder);
            holder.freeSpinsLeft = 0;
            holder.autoSpin = false;
            casino.openLobby(player);
            return;
        }
        if (slot == SLOTS_AUTO) {
            holder.autoSpin = !holder.autoSpin;
            holder.stopAuto = false;
            paintSlots(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, holder.autoSpin ? 1.45f : 0.7f);
            CasinoService.say(player, holder.autoSpin
                    ? "Auto-spin's on. It stops when the floor actually pays."
                    : "Auto-spin off. Manual suffering resumed.");
            if (holder.autoSpin && !holder.spinning && !spinning.contains(player.getUniqueId())) {
                spinSlots(player, inventory, holder);
            }
            return;
        }
        if (slot == SLOTS_RIGGER && isDev(player)) {
            holder.rigged = !holder.rigged;
            riggedOn.put(player.getUniqueId(), holder.rigged);
            paintSlots(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, holder.rigged ? 1.6f : 0.7f);
            player.sendMessage(holder.rigged
                    ? "§8Vince cracked his knuckle. Every spin's 50% loaded until you click off."
                    : "§8Thumb off the glass.");
            return;
        }
        if (holder.spinning || spinning.contains(player.getUniqueId())) {
            return;
        }
        if (slot == SLOTS_BET) {
            boolean lower = click.isRightClick();
            holder.bet = lower ? prevBet(holder.bet) : nextBet(holder.bet);
            lastBet.put(player.getUniqueId(), holder.bet);
            paintSlots(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, lower ? 0.85f : 1.35f);
            return;
        }
        if (slot == SLOTS_LINE_BTN) {
            holder.lines = holder.lines >= Payline.values().length ? 1 : holder.lines + 1;
            lastLines.put(player.getUniqueId(), holder.lines);
            paintSlots(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 0.9f + holder.lines * 0.12f);
            return;
        }
        if (slot != SLOTS_SPIN) {
            return;
        }
        spinSlots(player, inventory, holder);
    }

    private void clickRoulette(Player player, Inventory inventory, RouletteHolder holder, int slot, ClickType click) {
        if (slot == ROULETTE_BACK) {
            if (holder.spinning || spinning.contains(player.getUniqueId())) {
                CasinoService.say(player, "Ball's rolling. Sit still — ESC won't void the spin.");
                return;
            }
            casino.openLobby(player);
            return;
        }
        if (holder.spinning || spinning.contains(player.getUniqueId())) {
            return;
        }
        if (slot == ROULETTE_BET) {
            boolean lower = click.isRightClick();
            holder.bet = lower ? prevBet(holder.bet) : nextBet(holder.bet);
            lastBet.put(player.getUniqueId(), holder.bet);
            paintRoulette(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, lower ? 0.85f : 1.35f);
            return;
        }
        WheelColor picked = colorFromSlot(slot);
        if (picked != null) {
            holder.color = picked;
            paintRoulette(player, inventory, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, picked == WheelColor.GREEN ? 1.6f : 1.05f);
            return;
        }
        if (slot != ROULETTE_SPIN) {
            return;
        }
        spinRoulette(player, inventory, holder);
    }

    private void spinSlots(Player player, Inventory inventory, SlotsHolder holder) {
        boolean free = holder.freeSpinsLeft > 0;
        holder.usingFree = free;
        if (!free) {
            long cost = holder.cost();
            if (!coins.take(player, cost)) {
                boolean wasAuto = holder.autoSpin;
                holder.spinning = false;
                spinning.remove(player.getUniqueId());
                holder.autoSpin = false;
                player.sendMessage("§cNeed §f" + format(cost) + " coins §cfor " + holder.lines + " lines. Purse: §f"
                        + coins.formatted(player) + "§c.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.65f);
                if (stillSlots(player, holder)) {
                    paintSlots(player, inventory, holder);
                    if (wasAuto) {
                        CasinoService.say(player, "Purse died. Auto-spin clocked out.");
                    }
                }
                return;
            }
        }
        holder.spinning = true;
        spinning.add(player.getUniqueId());
        holder.window = randomWindow();
        holder.locked = new boolean[REEL_COLS];
        holder.glow = new boolean[REEL_ROWS][REEL_COLS];
        paintSlots(player, inventory, holder);
        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.15f, 1.8f);
        if (free) {
            player.sendActionBar(Component.text(
                    "FREE SPIN  ·  " + holder.freeSpinsLeft + " LEFT",
                    NamedTextColor.LIGHT_PURPLE,
                    TextDecoration.BOLD
            ));
        } else {
            player.sendActionBar(Component.text(
                    "SPINNING  ·  " + holder.lines + " LINES",
                    NamedTextColor.GOLD,
                    TextDecoration.BOLD
            ));
        }

        SlotSymbol[][] rolled = randomWindow();
        if (holder.rigged && ThreadLocalRandom.current().nextBoolean()) {
            rolled = rigMainWin(rolled);
            CasinoService.say(player, "§8The reels already know.");
        }
        SlotSymbol[][] result = rolled;
        int lastStop = REEL_STOP[REEL_STOP.length - 1];
        for (int tick = 0; tick <= lastStop; tick++) {
            int frame = tick;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !stillSlots(player, holder)) {
                    return;
                }
                Inventory open = player.getOpenInventory().getTopInventory();
                boolean lockedAny = false;
                for (int col = 0; col < REEL_COLS; col++) {
                    if (!holder.locked[col] && frame >= REEL_STOP[col]) {
                        holder.locked[col] = true;
                        for (int row = 0; row < REEL_ROWS; row++) {
                            holder.window[row][col] = result[row][col];
                        }
                        drawReel(open, holder, col);
                        lockFx(player, col);
                        lockedAny = true;
                    } else if (!holder.locked[col]) {
                        for (int row = 0; row < REEL_ROWS; row++) {
                            holder.window[row][col] = SlotSymbol.pick();
                        }
                        drawReel(open, holder, col);
                    }
                }
                if (!lockedAny && frame % 2 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.28f, 0.85f + (frame * 0.012f));
                }
            }, frame);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishSlots(player, holder, result), lastStop + 8L);
    }

    private void finishSlots(Player player, SlotsHolder holder, SlotSymbol[][] result) {
        holder.window = result;
        holder.locked = new boolean[REEL_COLS];
        java.util.Arrays.fill(holder.locked, true);
        holder.glow = new boolean[REEL_ROWS][REEL_COLS];
        List<String> hits = new ArrayList<>();
        long win = 0L;
        int bestStreak = 0;
        SlotSymbol bestSymbol = SlotSymbol.CHERRY;
        for (int i = 0; i < holder.lines; i++) {
            Payline line = Payline.values()[i];
            SlotSymbol[] path = line.read(result);
            int streak = streak(path);
            if (streak < 3) {
                continue;
            }
            long payout = holder.bet * path[0].payout(streak);
            if (payout <= 0L) {
                continue;
            }
            win += payout;
            if (streak > bestStreak || (streak == bestStreak && path[0].ordinal() > bestSymbol.ordinal())) {
                bestStreak = streak;
                bestSymbol = path[0];
            }
            hits.add(line.chat + streak + "x " + path[0].label + " §8(+" + format(payout) + ")");
            for (int col = 0; col < streak; col++) {
                holder.glow[line.rows[col]][col] = true;
            }
        }
        int books = countBooks(result);
        if (books >= 3) {
            for (int row = 0; row < REEL_ROWS; row++) {
                for (int col = 0; col < REEL_COLS; col++) {
                    if (result[row][col] == SlotSymbol.BOOK) {
                        holder.glow[row][col] = true;
                    }
                }
            }
        }
        if (win > 0L) {
            coins.credit(player, win);
        }
        if (holder.usingFree) {
            holder.freeSpinsLeft = Math.max(0, holder.freeSpinsLeft - 1);
        }
        int awarded = 0;
        if (books >= 3) {
            awarded = 7 + (books - 3) * 2;
            holder.freeSpinsLeft += awarded;
        }
        boolean big = isBigWin(bestStreak, bestSymbol, hits.size());
        if (big) {
            holder.stopAuto = true;
        }

        boolean online = player.isOnline();
        if (!online || !stillSlots(player, holder)) {
            holder.autoSpin = false;
            bankFreeSpins(player, holder);
            releaseSpin(player, holder);
            return;
        }
        if (win > 0L || books < 3) {
            bang(player, bestStreak, bestSymbol, win, hits);
        }
        if (books >= 3) {
            celebrateBooks(player, books, awarded);
            bookFlash(player, holder, 0);
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        paintSlots(player, inventory, holder);
        if (win > 0L && holder.freeSpinsLeft <= 0 && !holder.autoSpin) {
            flashEdges(player, holder, 0);
        }
        queueNext(player, holder);
    }

    private void bookFlash(Player player, SlotsHolder holder, int step) {
        if (!player.isOnline() || !stillSlots(player, holder)) {
            bankFreeSpins(player, holder);
            releaseSpin(player, holder);
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (step > 9) {
            paintSlots(player, inventory, holder);
            queueNext(player, holder);
            return;
        }
        boolean book = step % 2 == 0;
        ItemStack icon = book
                ? GuiItems.named(Material.BOOK, "§f§lBOOK", "§dThe library just opened.")
                : GuiItems.named(Material.BLAZE_POWDER, "§6§lFIRE", "§ePages catching.");
        if (book) {
            glow(icon);
        }
        for (int row = 0; row < REEL_ROWS; row++) {
            for (int col = 0; col < REEL_COLS; col++) {
                inventory.setItem(reelSlot(row, col), icon.clone());
            }
        }
        player.playSound(
                player.getLocation(),
                book ? Sound.ITEM_BOOK_PAGE_TURN : Sound.ENTITY_BLAZE_SHOOT,
                0.55f,
                book ? 1.35f : 0.85f + step * 0.05f
        );
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> bookFlash(player, holder, step + 1), 2L);
    }

    private void celebrateBooks(Player player, int books, int awarded) {
        CasinoService.say(player, bookLine(books, awarded));
        player.sendActionBar(Component.text(
                "FREE SPINS  +" + awarded,
                NamedTextColor.LIGHT_PURPLE,
                TextDecoration.BOLD
        ));
        player.showTitle(Title.title(
                Component.text("FREE SPINS", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text(books + " books  ·  +" + awarded, NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1600), Duration.ofMillis(280))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.55f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.2, 0), 40, 0.5, 0.6, 0.5, 0.8);
        fireworksBooks(player);
    }

    private void queueNext(Player player, SlotsHolder holder) {
        boolean moreFree = holder.freeSpinsLeft > 0;
        boolean moreAuto = holder.autoSpin && !holder.stopAuto;
        if (!moreFree && !moreAuto) {
            holder.autoSpin = false;
            holder.stopAuto = false;
            releaseSpin(player, holder);
            if (player.isOnline() && stillSlots(player, holder)) {
                paintSlots(player, player.getOpenInventory().getTopInventory(), holder);
            }
            return;
        }
        long delay = moreFree ? 24L : 18L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !stillSlots(player, holder)) {
                bankFreeSpins(player, holder);
                releaseSpin(player, holder);
                return;
            }
            spinSlots(player, player.getOpenInventory().getTopInventory(), holder);
        }, delay);
    }

    private void releaseSpin(Player player, SlotsHolder holder) {
        holder.spinning = false;
        spinning.remove(player.getUniqueId());
    }

    private void flashEdges(Player player, SlotsHolder holder, int step) {
        if (step > 7 || !player.isOnline() || !stillSlots(player, holder)) {
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        boolean gold = step % 2 == 0;
        for (int i = 0; i < holder.lines; i++) {
            Payline line = Payline.values()[i];
            if (line == Payline.MAGENTA) {
                continue;
            }
            Material material = gold ? Material.GOLD_BLOCK : line.pane;
            ItemStack pane = GuiItems.named(material, gold ? line.chat + "§lWIN" : line.chat + line.hint);
            inventory.setItem(edgeSlot(line.edgeRow, true), pane.clone());
            inventory.setItem(edgeSlot(line.edgeRow, false), pane.clone());
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> flashEdges(player, holder, step + 1), 3L);
    }

    private void bang(Player player, int streak, SlotSymbol symbol, long win, List<String> hits) {
        if (win <= 0L) {
            CasinoService.say(player, loseLine(player));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.2f, 0.6f);
            player.sendActionBar(Component.text("HOUSE WINS", NamedTextColor.DARK_GRAY));
            return;
        }
        for (String hit : hits) {
            CasinoService.say(player, hit);
        }
        CasinoService.say(player, "§a+" + format(win) + " coins");
        player.sendActionBar(Component.text("+" + format(win) + " COINS", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1.2, 0), 18, 0.4, 0.4, 0.4, 0.15);
        if ((symbol == SlotSymbol.CROWN && streak >= 4) || streak >= 6) {
            Bukkit.broadcastMessage("§6Lucky Vince §8» §e" + player.getName() + " just made the floor sweat.");
            CasinoService.razzBigWin(player);
            player.showTitle(Title.title(
                    Component.text("JACKPOT", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("+" + format(win) + " coins", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(2200), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.05f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.4f);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.4f, 1.3f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.1, 0), 40, 0.5, 0.7, 0.5, 0.15);
            fireworks(player, 5, true);
            return;
        }
        if (streak >= 4 || hits.size() >= 2) {
            CasinoService.razzBigWin(player);
            player.showTitle(Title.title(
                    Component.text("BIG WIN", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text("+" + format(win), NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(60), Duration.ofMillis(1400), Duration.ofMillis(250))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.5f, 1.4f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.1, 0), 20, 0.4, 0.5, 0.4, 0);
            fireworks(player, 2, false);
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.45f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.6f);
    }

    private void lockFx(Player player, int col) {
        float pitch = 0.7f + col * 0.12f;
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, pitch);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 0.8f + col * 0.16f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.05);
    }

    private void spinRoulette(Player player, Inventory inventory, RouletteHolder holder) {
        if (!coins.take(player, holder.bet)) {
            player.sendMessage("§cNot enough coins. Purse: §f" + coins.formatted(player) + "§c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.65f);
            return;
        }
        holder.spinning = true;
        spinning.add(player.getUniqueId());
        holder.landed = -1;
        paintRoulette(player, inventory, holder);
        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.45f, 1.4f);
        player.sendActionBar(Component.text("BALL'S ROLLING", NamedTextColor.RED, TextDecoration.BOLD));

        int landIndex = ThreadLocalRandom.current().nextInt(RING_SLOTS.length);
        int steps = RING_SLOTS.length * 2 + landIndex;
        long tick = 0L;
        int previous = -1;
        for (int step = 0; step <= steps; step++) {
            int index = step % RING_SLOTS.length;
            int from = previous;
            int frame = step;
            long delay = tick;
            boolean last = step == steps;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !stillRoulette(player, holder)) {
                    return;
                }
                Inventory open = player.getOpenInventory().getTopInventory();
                if (from >= 0) {
                    open.setItem(RING_SLOTS[from], pocketIcon(from, false, false));
                }
                holder.highlight = index;
                open.setItem(RING_SLOTS[index], pocketIcon(index, true, last));
                open.setItem(BALL, ballIcon(RING_NUMBERS[index], last));
                player.playSound(
                        player.getLocation(),
                        last ? Sound.BLOCK_BELL_USE : Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,
                        last ? 0.85f : 0.25f,
                        last ? 1.55f : 0.75f + frame * 0.02f
                );
            }, delay);
            previous = index;
            tick += 1L + step / 8L;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishRoulette(player, holder, landIndex), tick + 6L);
    }

    private void finishRoulette(Player player, RouletteHolder holder, int landIndex) {
        holder.spinning = false;
        spinning.remove(player.getUniqueId());
        holder.highlight = landIndex;
        holder.landed = landIndex;
        int number = RING_NUMBERS[landIndex];
        WheelColor hit = colorOf(number);
        boolean won = hit == holder.color;
        long payout = won ? holder.bet * holder.color.payout : 0L;
        if (payout > 0L) {
            coins.credit(player, payout);
        }
        if (!player.isOnline()) {
            return;
        }
        if (stillRoulette(player, holder)) {
            paintRoulette(player, player.getOpenInventory().getTopInventory(), holder);
        }
        if (won) {
            CasinoService.say(player, hit.winLine(number) + " §a+" + format(payout) + " coins§f.");
            player.sendActionBar(Component.text("+" + format(payout) + " COINS", NamedTextColor.GREEN, TextDecoration.BOLD));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.1, 0), 22, 0.45, 0.5, 0.45, 0);
            if (hit == WheelColor.GREEN) {
                CasinoService.razzBigWin(player);
                player.showTitle(Title.title(
                        Component.text("ZERO", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text("You maniac. +" + format(payout), NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1800), Duration.ofMillis(300))
                ));
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.45f, 1.25f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.7f);
                Bukkit.broadcastMessage("§6Lucky Vince §8» §a" + player.getName() + " hit zero. The felt is furious.");
                fireworks(player, 4, true);
            } else {
                if (payout >= holder.bet * 2L && payout >= 2_000L) {
                    CasinoService.razzBigWin(player);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.25f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.55f, 1.3f);
            }
        } else {
            CasinoService.say(player, hit.loseLine(number));
            player.sendActionBar(Component.text(number + "  ·  HOUSE"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.55f);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.15f, 0.7f);
        }
    }

    private void paintSlots(Player player, Inventory inventory, SlotsHolder holder) {
        ItemStack black = GuiItems.named(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack frame = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, black.clone());
        }
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, frame.clone());
        }
        for (int row = 0; row < REEL_ROWS; row++) {
            Payline line = lineForRow(row);
            boolean on = line != null && line.ordinal() < holder.lines;
            ItemStack edge = on
                    ? GuiItems.named(line.pane, line.chat + line.hint, "§7Active payline.")
                    : GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§8Locked line", "§7Add lines to light this.");
            inventory.setItem(edgeSlot(row, true), edge.clone());
            inventory.setItem(edgeSlot(row, false), edge.clone());
        }
        inventory.setItem(SLOTS_PAYOUTS, GuiItems.named(
                Material.KNOWLEDGE_BOOK,
                "§ePayouts §8· §73+ from the left",
                "§cCherry     §f5x §810x §818x §828x §845x",
                "§6Gold       §f8x §815x §830x §850x §880x",
                "§aEmerald    §f12x §828x §855x §890x §8160x",
                "§bDiamond    §f22x §855x §8110x §8220x §8420x",
                "§8Netherite  §f45x §8110x §8260x §8520x §8950x",
                "§eStar       §f90x §8280x §8650x §81.3k §82.2k",
                "§6§lCrown    §f220x §8850x §82.2k §84.2k §88.5k",
                "§fBook       §dscatter · anywhere",
                "§83 books = 7 free spins. Each extra +2.",
                "§83 / 4 / 5 / 6 / 7 of a kind."
        ));
        inventory.setItem(SLOTS_LOGO, GuiItems.named(
                holder.freeSpinsLeft > 0 ? Material.BOOK : Material.JUKEBOX,
                holder.freeSpinsLeft > 0 ? "§d§lFREE SPINS §f" + holder.freeSpinsLeft : "§6§lSLOTS",
                "§7Seven reels. Four rows.",
                "§7Books scatter. 3+ starts free spins.",
                holder.spinning ? "§eSpinning..." : "§8Pull it."
        ));
        inventory.setItem(SLOTS_PURSE, purse(player));
        inventory.setItem(SLOTS_BET, betButton(holder.bet));
        inventory.setItem(SLOTS_LINE_BTN, linesButton(holder));
        inventory.setItem(SLOTS_SPIN, GuiItems.named(
                Material.LEVER,
                holder.spinning
                        ? (holder.usingFree || holder.freeSpinsLeft > 0 ? "§d§lFREE SPIN" : "§7Spinning...")
                        : "§a§lSPIN",
                holder.freeSpinsLeft > 0 ? "§dFree spins left: §f" + holder.freeSpinsLeft : "§7Stake: §e" + format(holder.bet) + " §7× §f" + holder.lines + " lines",
                "§7Total: §6" + format(holder.cost()) + " coins",
                "",
                holder.spinning ? "§8Hands off the glass." : "§eClick to pull."
        ));
        inventory.setItem(SLOTS_BACK, GuiItems.named(Material.ARROW, "§eBack", "§7Return to the floor."));
        inventory.setItem(SLOTS_AUTO, autoButton(holder));
        if (isDev(player)) {
            inventory.setItem(SLOTS_RIGGER, holder.rigged
                    ? GuiItems.named(
                            Material.GOLD_NUGGET,
                            "§6§oLOADED",
                            "§8Vince's thumb stays on the glass.",
                            "§8Every spin: 50% a real hit.",
                            "§7DEV. Click to disarm.")
                    : GuiItems.named(
                            Material.TRIPWIRE_HOOK,
                            "§8§o…",
                            "§8Don't let the homies see this.",
                            "§8Stays on until you click off.",
                            "§8Every spin: 50% a Hauptgewinn.",
                            "§7DEV. Click to load."));
        }
        if (holder.window == null) {
            for (int col = 0; col < REEL_COLS; col++) {
                for (int row = 0; row < REEL_ROWS; row++) {
                    inventory.setItem(reelSlot(row, col), GuiItems.named(Material.GRAY_DYE, "§8?", "§7Pull the lever."));
                }
            }
            return;
        }
        for (int col = 0; col < REEL_COLS; col++) {
            drawReel(inventory, holder, col);
        }
    }

    private ItemStack linesButton(SlotsHolder holder) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to add a payline.");
        lore.add("§7Each line costs another stake.");
        lore.add("");
        for (Payline line : Payline.values()) {
            boolean on = line.ordinal() < holder.lines;
            lore.add((on ? line.chat : "§8") + (on ? "● " : "○ ") + line.hint);
        }
        lore.add("");
        lore.add(holder.lines >= Payline.values().length ? "§eClick to reset to 1." : "§eClick to add one.");
        return GuiItems.named(
                Material.MAP,
                "§ePaylines §8· §f" + holder.lines + "/" + Payline.values().length,
                lore
        );
    }

    private void drawReel(Inventory inventory, SlotsHolder holder, int col) {
        if (holder.window == null) {
            return;
        }
        for (int row = 0; row < REEL_ROWS; row++) {
            boolean glow = holder.glow != null && holder.glow[row][col];
            inventory.setItem(reelSlot(row, col), holder.window[row][col].icon(glow));
        }
    }

    private void paintRoulette(Player player, Inventory inventory, RouletteHolder holder) {
        ItemStack black = GuiItems.named(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack felt = GuiItems.named(Material.GREEN_STAINED_GLASS_PANE, "§2 ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, black.clone());
        }
        for (int inner : new int[]{12, 13, 14, 21, 23, 30, 31, 32}) {
            inventory.setItem(inner, felt.clone());
        }
        for (int i = 0; i < RING_SLOTS.length; i++) {
            boolean hot = i == holder.highlight || i == holder.landed;
            boolean locked = holder.landed >= 0 && i == holder.landed;
            inventory.setItem(RING_SLOTS[i], pocketIcon(i, hot, locked));
        }
        int shown = holder.highlight >= 0 ? RING_NUMBERS[holder.highlight] : -1;
        inventory.setItem(BALL, ballIcon(shown, holder.landed >= 0));
        inventory.setItem(ROULETTE_BACK, GuiItems.named(Material.ARROW, "§eBack", "§7Return to the floor."));
        inventory.setItem(ROULETTE_RED, GuiItems.named(
                Material.RED_WOOL,
                holder.color == WheelColor.RED ? "§c§lRED §8· §aSelected" : "§cRed",
                "§7Pays §f2x",
                holder.color == WheelColor.RED ? "§aYour color." : "§eClick to bet red."
        ));
        inventory.setItem(ROULETTE_BLACK, GuiItems.named(
                Material.BLACK_WOOL,
                holder.color == WheelColor.BLACK ? "§8§lBLACK §8· §aSelected" : "§8Black",
                "§7Pays §f2x",
                holder.color == WheelColor.BLACK ? "§aYour color." : "§eClick to bet black."
        ));
        inventory.setItem(ROULETTE_GREEN, GuiItems.named(
                Material.LIME_WOOL,
                holder.color == WheelColor.GREEN ? "§a§lGREEN §8· §aSelected" : "§aGreen",
                "§7Pays §a14x",
                "§7One pocket. Zero.",
                holder.color == WheelColor.GREEN ? "§aYour color." : "§eClick to bet green."
        ));
        inventory.setItem(ROULETTE_BET, betButton(holder.bet));
        inventory.setItem(ROULETTE_SPIN, GuiItems.named(
                Material.LEVER,
                holder.spinning ? "§7Spinning..." : "§a§lSPIN",
                "§7Bet: §e" + format(holder.bet) + " coins",
                "§7On: " + holder.color.chat,
                "",
                holder.spinning ? "§8Ball's running." : "§eClick to spin."
        ));
        inventory.setItem(ROULETTE_PURSE, purse(player));
    }

    private ItemStack purse(Player player) {
        return GuiItems.named(
                Material.SUNFLOWER,
                "§6Purse",
                "§7Balance: §e" + coins.formatted(player) + " coins"
        );
    }

    private ItemStack betButton(long bet) {
        return GuiItems.named(
                Material.GOLD_NUGGET,
                "§eBet §8· §f" + format(bet),
                "§eLeft click §7raises. §eRight click §7lowers.",
                "§8Left also cycles back around.",
                "§810 · 50 · 100 · 250 · 500",
                "§81,000 · 2,500 · 5,000",
                "§810,000 · 25,000 · 50,000"
        );
    }

    private static ItemStack pocketIcon(int index, boolean highlight, boolean landed) {
        int number = RING_NUMBERS[index];
        WheelColor color = colorOf(number);
        if (highlight) {
            ItemStack ball = GuiItems.named(
                    Material.SNOWBALL,
                    color.chat + "§l● " + number,
                    landed ? "§7Ball rests here." : "§7Ball."
            );
            glow(ball);
            return ball;
        }
        return GuiItems.named(color.wool, color.chat + number, "§7Click to bet " + color.chat + "§7.");
    }

    private static ItemStack ballIcon(int number, boolean locked) {
        if (number < 0) {
            return GuiItems.named(Material.ENDER_PEARL, "§fBall", "§7Spin. It runs the ring.", "§8Then it sits.");
        }
        WheelColor color = colorOf(number);
        ItemStack item = GuiItems.named(
                locked ? color.wool : Material.SNOWBALL,
                locked ? color.chat + "§l● " + number : "§f● " + color.chat + number,
                locked ? "§7Rests here." : "§7Rolling..."
        );
        glow(item);
        return item;
    }

    private static WheelColor colorFromSlot(int slot) {
        if (slot == ROULETTE_RED) {
            return WheelColor.RED;
        }
        if (slot == ROULETTE_BLACK) {
            return WheelColor.BLACK;
        }
        if (slot == ROULETTE_GREEN) {
            return WheelColor.GREEN;
        }
        int index = ringIndex(slot);
        if (index >= 0) {
            return colorOf(RING_NUMBERS[index]);
        }
        return null;
    }

    private static int ringIndex(int slot) {
        for (int i = 0; i < RING_SLOTS.length; i++) {
            if (RING_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static int reelSlot(int row, int col) {
        return (row + 1) * 9 + (col + 1);
    }

    private static int edgeSlot(int row, boolean left) {
        return (row + 1) * 9 + (left ? 0 : 8);
    }

    private static Payline lineForRow(int row) {
        return switch (row) {
            case 0 -> Payline.RED;
            case 1 -> Payline.YELLOW;
            case 2 -> Payline.AQUA;
            case 3 -> Payline.LIME;
            default -> null;
        };
    }

    private static SlotSymbol[][] randomWindow() {
        SlotSymbol[][] window = new SlotSymbol[REEL_ROWS][REEL_COLS];
        for (int row = 0; row < REEL_ROWS; row++) {
            for (int col = 0; col < REEL_COLS; col++) {
                window[row][col] = SlotSymbol.pick();
            }
        }
        return window;
    }

    private static int streak(SlotSymbol[] payline) {
        int count = 1;
        for (int i = 1; i < payline.length; i++) {
            if (payline[i] != payline[0]) {
                break;
            }
            count++;
        }
        return count;
    }

    private static SlotSymbol[][] rigMainWin(SlotSymbol[][] window) {
        SlotSymbol[] pool = {
                SlotSymbol.DIAMOND, SlotSymbol.NETHERITE, SlotSymbol.STAR, SlotSymbol.CROWN
        };
        int roll = ThreadLocalRandom.current().nextInt(100);
        SlotSymbol hit = roll < 40 ? pool[0] : roll < 70 ? pool[1] : roll < 90 ? pool[2] : pool[3];
        int len = roll < 55 ? 3 : roll < 85 ? 4 : 5;
        int row = Payline.YELLOW.rows[0];
        for (int col = 0; col < len && col < REEL_COLS; col++) {
            window[row][col] = hit;
        }
        return window;
    }

    private void fireworks(Player player, int count, boolean jackpot) {
        org.bukkit.Location base = player.getLocation().add(0, 2.4, 0);
        org.bukkit.FireworkEffect.Type type = jackpot
                ? org.bukkit.FireworkEffect.Type.STAR
                : org.bukkit.FireworkEffect.Type.BALL;
        org.bukkit.Color[] colors = jackpot
                ? new org.bukkit.Color[]{org.bukkit.Color.YELLOW, org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.ORANGE}
                : new org.bukkit.Color[]{org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE, org.bukkit.Color.RED};
        for (int i = 0; i < count; i++) {
            org.bukkit.Location at = base.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.8,
                    0.15 * i,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.8
            );
            org.bukkit.entity.Firework firework = player.getWorld().spawn(at, org.bukkit.entity.Firework.class);
            firework.getPersistentDataContainer().set(
                    de.aetherion.items.core.ItemKeys.casinoFirework(),
                    org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 1
            );
            org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(type)
                    .withColor(colors)
                    .withFade(org.bukkit.Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.detonate();
        }
    }

    private void fireworksBooks(Player player) {
        org.bukkit.Location base = player.getLocation().add(0, 2.6, 0);
        org.bukkit.Color[] colors = {
                org.bukkit.Color.PURPLE,
                org.bukkit.Color.FUCHSIA,
                org.bukkit.Color.AQUA,
                org.bukkit.Color.WHITE,
                org.bukkit.Color.YELLOW
        };
        for (int i = 0; i < 6; i++) {
            org.bukkit.Location at = base.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.2,
                    0.18 * i,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.2
            );
            org.bukkit.entity.Firework firework = player.getWorld().spawn(at, org.bukkit.entity.Firework.class);
            firework.getPersistentDataContainer().set(
                    de.aetherion.items.core.ItemKeys.casinoFirework(),
                    org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 1
            );
            org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(i % 2 == 0 ? org.bukkit.FireworkEffect.Type.BURST : org.bukkit.FireworkEffect.Type.STAR)
                    .withColor(colors)
                    .withFade(org.bukkit.Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.detonate();
        }
    }

    private ItemStack autoButton(SlotsHolder holder) {
        if (holder.autoSpin) {
            return GuiItems.named(
                    Material.REPEATER,
                    "§a§lAUTO-SPIN ON",
                    "§7Keeps pulling until a big win.",
                    holder.stopAuto ? "§eBig win. Winding down." : "§eClick to kill it.",
                    "§8Vince is taking notes."
            );
        }
        return GuiItems.named(
                Material.COMPARATOR,
                "§eAuto-Spin",
                "§7Runs until a big win.",
                "§8Then it stops. That's the joke.",
                "§eClick to start."
        );
    }

    private static int countBooks(SlotSymbol[][] window) {
        int books = 0;
        for (int row = 0; row < REEL_ROWS; row++) {
            for (int col = 0; col < REEL_COLS; col++) {
                if (window[row][col] == SlotSymbol.BOOK) {
                    books++;
                }
            }
        }
        return books;
    }

    private static boolean isBigWin(int streak, SlotSymbol symbol, int hitLines) {
        return (symbol == SlotSymbol.CROWN && streak >= 4) || streak >= 4 || hitLines >= 2;
    }

    private static String bookLine(int books, int awarded) {
        if (books >= 6) {
            return "§d" + books + " books. +" + awarded + " free spins. Unplug this thing.";
        }
        if (books >= 5) {
            return "§d" + books + " books. +" + awarded + " free spins. The librarian is screaming.";
        }
        if (books == 4) {
            return "Four books. §d+" + awarded + " free spins§f. That's rude.";
        }
        return "Three books. §d+" + awarded + " free spins§f. Don't get comfortable.";
    }

    private String loseLine(Player player) {
        int tick = lossTick.merge(player.getUniqueId(), 1, Integer::sum);
        return LOSS_LINES[Math.floorMod(tick - 1, LOSS_LINES.length)];
    }

    private static boolean isDev(Player player) {
        return player.isOp() || player.hasPermission("aetherion.dev");
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private boolean stillSlots(Player player, SlotsHolder holder) {
        return player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private boolean stillRoulette(Player player, RouletteHolder holder) {
        return player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private long betFor(Player player) {
        return lastBet.getOrDefault(player.getUniqueId(), 100L);
    }

    private int linesFor(Player player) {
        return lastLines.getOrDefault(player.getUniqueId(), 1);
    }

    private static long nextBet(long current) {
        for (int i = 0; i < BETS.length; i++) {
            if (BETS[i] == current) {
                return BETS[(i + 1) % BETS.length];
            }
        }
        return BETS[0];
    }

    private static long prevBet(long current) {
        for (int i = 0; i < BETS.length; i++) {
            if (BETS[i] == current) {
                return BETS[(i - 1 + BETS.length) % BETS.length];
            }
        }
        return BETS[0];
    }

    private static WheelColor colorOf(int number) {
        if (number == 0) {
            return WheelColor.GREEN;
        }
        for (int red : REDS) {
            if (red == number) {
                return WheelColor.RED;
            }
        }
        return WheelColor.BLACK;
    }

    static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private enum Payline {
        YELLOW(Material.YELLOW_STAINED_GLASS_PANE, "§e", "Yellow · upper middle", 1, new int[]{1, 1, 1, 1, 1, 1, 1}),
        AQUA(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b", "Aqua · lower middle", 2, new int[]{2, 2, 2, 2, 2, 2, 2}),
        RED(Material.RED_STAINED_GLASS_PANE, "§c", "Red · top", 0, new int[]{0, 0, 0, 0, 0, 0, 0}),
        LIME(Material.LIME_STAINED_GLASS_PANE, "§a", "Lime · bottom", 3, new int[]{3, 3, 3, 3, 3, 3, 3}),
        MAGENTA(Material.MAGENTA_STAINED_GLASS_PANE, "§d", "Magenta · zigzag", 1, new int[]{0, 1, 2, 3, 2, 1, 0});

        final Material pane;
        final String chat;
        final String hint;
        final int edgeRow;
        final int[] rows;

        Payline(Material pane, String chat, String hint, int edgeRow, int[] rows) {
            this.pane = pane;
            this.chat = chat;
            this.hint = hint;
            this.edgeRow = edgeRow;
            this.rows = rows;
        }

        SlotSymbol[] read(SlotSymbol[][] window) {
            SlotSymbol[] path = new SlotSymbol[rows.length];
            for (int col = 0; col < rows.length; col++) {
                path[col] = window[rows[col]][col];
            }
            return path;
        }
    }

    private enum SlotSymbol {
        CHERRY(Material.SWEET_BERRIES, "§cCherry", 36, 5L, 10L, 18L, 28L, 45L),
        GOLD(Material.GOLD_INGOT, "§6Gold", 25, 8L, 15L, 30L, 50L, 80L),
        EMERALD(Material.EMERALD, "§aEmerald", 16, 12L, 28L, 55L, 90L, 160L),
        DIAMOND(Material.DIAMOND, "§bDiamond", 10, 22L, 55L, 110L, 220L, 420L),
        NETHERITE(Material.NETHERITE_INGOT, "§8Netherite", 6, 45L, 110L, 260L, 520L, 950L),
        STAR(Material.NETHER_STAR, "§eStar", 5, 90L, 280L, 650L, 1_300L, 2_200L),
        CROWN(Material.ENCHANTED_GOLDEN_APPLE, "§6§lCrown", 2, 220L, 850L, 2_200L, 4_200L, 8_500L),
        BOOK(Material.BOOK, "§fBook", 3, 0L, 0L, 0L, 0L, 0L);

        private static final int TOTAL;

        static {
            int sum = 0;
            for (SlotSymbol symbol : values()) {
                sum += symbol.weight;
            }
            TOTAL = sum;
        }

        final Material material;
        final String label;
        final int weight;
        final long pay3;
        final long pay4;
        final long pay5;
        final long pay6;
        final long pay7;

        SlotSymbol(Material material, String label, int weight, long pay3, long pay4, long pay5, long pay6, long pay7) {
            this.material = material;
            this.label = label;
            this.weight = weight;
            this.pay3 = pay3;
            this.pay4 = pay4;
            this.pay5 = pay5;
            this.pay6 = pay6;
            this.pay7 = pay7;
        }

        long payout(int count) {
            return switch (count) {
                case 3 -> pay3;
                case 4 -> pay4;
                case 5 -> pay5;
                case 6 -> pay6;
                case 7 -> pay7;
                default -> 0L;
            };
        }

        ItemStack icon(boolean glow) {
            ItemStack item = GuiItems.named(material, label);
            if (glow) {
                glow(item);
            }
            return item;
        }

        static SlotSymbol pick() {
            int roll = ThreadLocalRandom.current().nextInt(TOTAL);
            int cursor = 0;
            for (SlotSymbol symbol : values()) {
                cursor += symbol.weight;
                if (roll < cursor) {
                    return symbol;
                }
            }
            return CHERRY;
        }
    }

    private enum WheelColor {
        RED(Material.RED_CONCRETE, "§cRed", 2L),
        BLACK(Material.BLACK_CONCRETE, "§8Black", 2L),
        GREEN(Material.LIME_CONCRETE, "§aGreen", 14L);

        final Material wool;
        final String chat;
        final long payout;

        WheelColor(Material wool, String chat, long payout) {
            this.wool = wool;
            this.chat = chat;
            this.payout = payout;
        }

        String winLine(int number) {
            if (this == GREEN) {
                return "Zero. You absolute maniac.";
            }
            return chat + " " + number + "§f. The wheel liked you.";
        }

        String loseLine(int number) {
            return colorOf(number).chat + " " + number + "§f. Felt stays hungry.";
        }
    }

    static final class SlotsHolder implements InventoryHolder {
        long bet;
        int lines;
        boolean spinning;
        boolean rigged;
        boolean autoSpin;
        boolean stopAuto;
        boolean usingFree;
        int freeSpinsLeft;
        SlotSymbol[][] window;
        boolean[] locked;
        boolean[][] glow;

        SlotsHolder(long bet, int lines) {
            this.bet = bet;
            this.lines = Math.max(1, Math.min(Payline.values().length, lines));
        }

        long cost() {
            return bet * lines;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    static final class RouletteHolder implements InventoryHolder {
        long bet;
        WheelColor color = WheelColor.RED;
        boolean spinning;
        int highlight = -1;
        int landed = -1;

        RouletteHolder(long bet) {
            this.bet = bet;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
