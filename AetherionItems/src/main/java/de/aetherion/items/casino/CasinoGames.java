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

    static final String SLOTS_TITLE = "§8✦ §6§lSLOTS §8✦";
    static final String ROULETTE_TITLE = "§8✦ §4§lROULETTE §8✦";

    /** Bulbs that chase around the edge of each screen, listed in running order. */
    private static final int[] SLOTS_LIGHTS = {0, 2, 3, 5, 6, 52, 50, 48, 46};
    private static final int[] ROULETTE_LIGHTS = {0, 1, 7, 8, 17, 26, 35, 44, 52, 50, 43, 37, 36, 27, 18, 9};
    private static final int[] ROULETTE_WOOD = {11, 15, 20, 24, 29, 33};
    private static final int[] ROULETTE_FELT = {12, 13, 14, 21, 23, 30, 31, 32};
    static final int[] LOBBY_LIGHTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 25, 24, 23, 21, 20, 19, 18, 9};

    private static final ItemStack BULB_OFF = GuiItems.named(Material.BLACK_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_GOLD = GuiItems.named(Material.YELLOW_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_AMBER = GuiItems.named(Material.ORANGE_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_RED = GuiItems.named(Material.RED_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_WHITE = GuiItems.named(Material.WHITE_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_PINK = GuiItems.named(Material.MAGENTA_STAINED_GLASS_PANE, " ");
    private static final ItemStack BULB_PURPLE = GuiItems.named(Material.PURPLE_STAINED_GLASS_PANE, " ");
    private static final ItemStack[] BULB_RAINBOW = {
            BULB_GOLD,
            GuiItems.named(Material.LIME_STAINED_GLASS_PANE, " "),
            GuiItems.named(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " "),
            BULB_PINK,
            BULB_AMBER
    };

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
        // Idle glass border: ~1.5s cadence (was every 2 ticks ≈ 10/s — needless packet spam).
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickLights, 20L, 30L);
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
        Inventory inventory = Bukkit.createInventory(holder, 54, SLOTS_TITLE);
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
        Inventory inventory = Bukkit.createInventory(holder, 54, ROULETTE_TITLE);
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
        Inventory inventory = Bukkit.createInventory(holder, 54, SLOTS_TITLE);
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
        Inventory inventory = Bukkit.createInventory(holder, 54, ROULETTE_TITLE);
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
        holder.dimmed = false;
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
        setFx(holder, Fx.SPIN, lastStop + 60);
        CasinoCabinet.machineSpin(player, lastStop + 8, machineFace(result, holder.lines));
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
            hits.add(line.chat + "● §f" + streak + "× " + path[0].label);
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
        boolean jackpot = isJackpot(bestStreak, bestSymbol);
        int tier = books >= 3 ? CasinoCabinet.TIER_FREE
                : jackpot ? CasinoCabinet.TIER_JACKPOT
                : big ? CasinoCabinet.TIER_BIG
                : win > 0L ? CasinoCabinet.TIER_WIN
                : CasinoCabinet.TIER_LOSE;
        boolean quiet = holder.usingFree || holder.autoSpin;
        holder.lastWin = win;
        switch (tier) {
            case CasinoCabinet.TIER_FREE -> setFx(holder, Fx.FREE, 70);
            case CasinoCabinet.TIER_JACKPOT -> setFx(holder, Fx.JACKPOT, 100);
            case CasinoCabinet.TIER_BIG -> setFx(holder, Fx.BIG, 60);
            case CasinoCabinet.TIER_WIN -> setFx(holder, Fx.WIN, 34);
            default -> setFx(holder, Fx.LOSE, 16);
        }
        if (win > 0L && books < 3) {
            holder.dimmed = true;
            holder.dimUntil = Bukkit.getCurrentTick() + (big ? 44 : 30);
        }
        if (player.isOnline()) {
            CasinoCabinet.machineResult(player, tier, win);
        }

        boolean online = player.isOnline();
        if (!online || !stillSlots(player, holder)) {
            holder.autoSpin = false;
            bankFreeSpins(player, holder);
            releaseSpin(player, holder);
            return;
        }
        if (win > 0L || books < 3) {
            bang(player, bestStreak, bestSymbol, win, hits, quiet, books < 3);
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

    private void bang(Player player, int streak, SlotSymbol symbol, long win, List<String> hits, boolean quiet, boolean titles) {
        if (win <= 0L) {
            if (!quiet) {
                CasinoService.say(player, loseLine(player));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.2f, 0.6f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.25f, 1.6f);
            player.sendActionBar(Component.text("HOUSE WINS", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
            return;
        }
        CasinoService.say(player, "§a§l+" + format(win) + " coins §8· " + String.join("§8, ", hits));
        player.sendActionBar(Component.text("+" + format(win) + " COINS", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1.2, 0), 18, 0.4, 0.4, 0.4, 0.15);
        player.getWorld().spawnParticle(Particle.ITEM, player.getLocation().add(0, 1.4, 0), 14, 0.25, 0.2, 0.25, 0.12,
                new ItemStack(Material.GOLD_NUGGET));
        if (isJackpot(streak, symbol)) {
            Bukkit.broadcastMessage("§6Lucky Vince §8» §e" + player.getName() + " just made the floor sweat.");
            CasinoService.razzBigWin(player);
            if (titles) {
                countUp(player, "JACKPOT", NamedTextColor.GOLD, win, 18, true);
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.05f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.4f);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.4f, 1.3f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.1, 0), 40, 0.5, 0.7, 0.5, 0.15);
            fireworks(player, 5, true);
            return;
        }
        if (streak >= 4 || hits.size() >= 2) {
            CasinoService.razzBigWin(player);
            if (titles) {
                countUp(player, "BIG WIN", NamedTextColor.YELLOW, win, 12, false);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.5f, 1.4f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.1, 0), 20, 0.4, 0.5, 0.4, 0);
            fireworks(player, 2, false);
            return;
        }
        if (titles) {
            countUp(player, null, NamedTextColor.GOLD, win, 6, false);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.45f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.6f);
    }

    private static boolean isJackpot(int streak, SlotSymbol symbol) {
        return (symbol == SlotSymbol.CROWN && streak >= 4) || streak >= 6;
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
        holder.dimmed = false;
        spinning.add(player.getUniqueId());
        holder.landed = -1;
        setFx(holder, Fx.SPIN, 400);
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
        RouletteCabinet.tableSpin(player, (int) tick, RING_NUMBERS[landIndex]);
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
        holder.lastWon = won;
        holder.dimmed = true;
        holder.dimUntil = Bukkit.getCurrentTick() + (won ? 40 : 26);
        setFx(holder, won ? (hit == WheelColor.GREEN ? Fx.JACKPOT : Fx.WIN) : Fx.LOSE, won ? (hit == WheelColor.GREEN ? 100 : 40) : 18);
        if (!player.isOnline()) {
            return;
        }
        RouletteCabinet.tableResult(player, number, won);
        if (stillRoulette(player, holder)) {
            paintRoulette(player, player.getOpenInventory().getTopInventory(), holder);
        }
        if (won) {
            CasinoService.say(player, hit.winLine(number) + " §a+" + format(payout) + " coins§f.");
            player.sendActionBar(Component.text("+" + format(payout) + " COINS", NamedTextColor.GREEN, TextDecoration.BOLD));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.1, 0), 22, 0.45, 0.5, 0.45, 0);
            player.getWorld().spawnParticle(Particle.ITEM, player.getLocation().add(0, 1.4, 0), 14, 0.25, 0.2, 0.25, 0.12,
                    new ItemStack(Material.GOLD_NUGGET));
            if (hit == WheelColor.GREEN) {
                CasinoService.razzBigWin(player);
                countUp(player, "ZERO", NamedTextColor.GREEN, payout, 16, true);
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.45f, 1.25f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.7f);
                Bukkit.broadcastMessage("§6Lucky Vince §8» §a" + player.getName() + " hit zero. The felt is furious.");
                fireworks(player, 4, true);
            } else {
                if (payout >= holder.bet * 2L && payout >= 2_000L) {
                    CasinoService.razzBigWin(player);
                }
                countUp(player, hit.word + " " + number, hit.title, payout, 8, false);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.25f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.55f, 1.3f);
            }
        } else {
            CasinoService.say(player, hit.loseLine(number));
            player.sendActionBar(Component.text(number + "  ·  HOUSE", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
            player.showTitle(Title.title(
                    Component.empty(),
                    Component.text(hit.word + " " + number, hit.title, TextDecoration.BOLD)
                            .append(Component.text("  ·  no win", NamedTextColor.DARK_GRAY)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(250))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.55f);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.15f, 0.7f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.25f, 1.6f);
        }
    }

    // ── casino-night lights ─────────────────────────────────────────────────

    private void tickLights() {
        int now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            InventoryHolder holder = top.getHolder(false);
            if (holder instanceof SlotsHolder slots) {
                if (slots.fx != Fx.IDLE && slots.fx != Fx.SPIN && now >= slots.fxUntil) {
                    slots.fx = Fx.IDLE;
                }
                if (slots.dimmed && now >= slots.dimUntil && !slots.spinning) {
                    slots.dimmed = false;
                    for (int col = 0; col < REEL_COLS; col++) {
                        drawReel(top, slots, col);
                    }
                }
                int key = lightsKey(slots.fx, now);
                if (key != slots.lightsKey) {
                    slots.lightsKey = key;
                    paintLights(top, slotsLights(player), slots.fx, now, false);
                }
            } else if (holder instanceof RouletteHolder roulette) {
                if (roulette.fx != Fx.IDLE && roulette.fx != Fx.SPIN && now >= roulette.fxUntil) {
                    roulette.fx = Fx.IDLE;
                }
                if (roulette.dimmed && now >= roulette.dimUntil && !roulette.spinning) {
                    roulette.dimmed = false;
                    paintRoulette(player, top, roulette);
                }
                int key = lightsKey(roulette.fx, now);
                if (key != roulette.lightsKey) {
                    roulette.lightsKey = key;
                    paintLights(top, ROULETTE_LIGHTS, roulette.fx, now, true);
                }
            } else if (holder instanceof CasinoService.LobbyHolder lobby) {
                int key = lightsKey(Fx.IDLE, now);
                if (key != lobby.lightsKey) {
                    lobby.lightsKey = key;
                    paintLights(top, LOBBY_LIGHTS, Fx.IDLE, now, false);
                }
            }
        }
    }

    /** Changes only when the picture changes, so idle screens send almost nothing. */
    private static int lightsKey(Fx fx, int now) {
        int step = switch (fx) {
            case IDLE -> now / 30;   // ~1.5s per frame at 20 tps
            case WIN -> now / 10;    // celebrate a bit faster, still calm
            case LOSE -> 0;
            default -> now / 8;      // spin accent — not every tick
        };
        return fx.ordinal() * 1_000_000 + Math.floorMod(step, 999_983);
    }

    void paintLobbyLights(Inventory inventory) {
        paintLights(inventory, LOBBY_LIGHTS, Fx.IDLE, Bukkit.getCurrentTick(), false);
    }

    private static void paintLights(Inventory inventory, int[] slots, Fx fx, int now, boolean roulette) {
        ItemStack accent = roulette ? BULB_RED : BULB_AMBER;
        for (int i = 0; i < slots.length; i++) {
            ItemStack bulb = switch (fx) {
                case SPIN -> chase(i, now / 2, accent);
                case WIN -> (now / 4) % 2 == 0 ? BULB_GOLD : BULB_WHITE;
                case BIG -> (i % 2 == 0) == ((now / 2) % 2 == 0) ? BULB_GOLD : accent;
                case JACKPOT -> BULB_RAINBOW[Math.floorMod(i + now / 2, BULB_RAINBOW.length)];
                case FREE -> (i + now / 2) % 2 == 0 ? BULB_PINK : BULB_PURPLE;
                case LOSE -> BULB_OFF;
                default -> chase(i, now / 6, accent);
            };
            inventory.setItem(slots[i], bulb.clone());
        }
    }

    private static ItemStack chase(int index, int step, ItemStack accent) {
        return switch (Math.floorMod(index - step, 3)) {
            case 0 -> BULB_GOLD;
            case 1 -> accent;
            default -> BULB_OFF;
        };
    }

    private static int[] slotsLights(Player player) {
        if (isDev(player)) {
            return SLOTS_LIGHTS;
        }
        int[] withCorner = java.util.Arrays.copyOf(SLOTS_LIGHTS, SLOTS_LIGHTS.length + 1);
        System.arraycopy(SLOTS_LIGHTS, 5, withCorner, 6, SLOTS_LIGHTS.length - 5);
        withCorner[5] = SLOTS_RIGGER;
        return withCorner;
    }

    private static void setFx(SlotsHolder holder, Fx fx, int ticks) {
        holder.fx = fx;
        holder.fxUntil = Bukkit.getCurrentTick() + ticks;
        holder.lightsKey = -1;
    }

    private static void setFx(RouletteHolder holder, Fx fx, int ticks) {
        holder.fx = fx;
        holder.fxUntil = Bukkit.getCurrentTick() + ticks;
        holder.lightsKey = -1;
    }

    /** Rolling number in the title — the win counts itself up. */
    private void countUp(Player player, String word, NamedTextColor wordColor, long amount, int frames, boolean flicker) {
        for (int f = 1; f <= frames; f++) {
            int frame = f;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                boolean last = frame >= frames;
                double t = frame / (double) frames;
                long shown = last ? amount : Math.max(1L, Math.round(amount * (1 - Math.pow(1 - t, 3))));
                NamedTextColor headColor = flicker && !last && frame % 2 == 0 ? NamedTextColor.WHITE : wordColor;
                Component head = word == null
                        ? Component.empty()
                        : Component.text(word, headColor, TextDecoration.BOLD);
                Component sub = Component.text("+" + format(shown) + " coins",
                        last ? NamedTextColor.GOLD : NamedTextColor.YELLOW, TextDecoration.BOLD);
                player.showTitle(Title.title(head, sub, Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(last ? 1500 : 400),
                        Duration.ofMillis(last ? 350 : 0)
                )));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        last ? 0.6f : 0.3f, last ? 1.9f : 0.75f + 0.9f * (float) t);
            }, (long) (frame - 1) * 2L);
        }
    }

    /** Three pictures for the in-world machine's reels that tell the same story as the GUI. */
    private static Material[] machineFace(SlotSymbol[][] result, int lines) {
        if (countBooks(result) >= 3) {
            return new Material[]{Material.BOOK, Material.BOOK, Material.BOOK};
        }
        SlotSymbol best = null;
        long bestPay = 0L;
        for (int i = 0; i < lines; i++) {
            SlotSymbol[] path = Payline.values()[i].read(result);
            int streak = streak(path);
            long pay = streak >= 3 ? path[0].payout(streak) : 0L;
            if (pay > bestPay) {
                bestPay = pay;
                best = path[0];
            }
        }
        if (best != null) {
            return new Material[]{best.material, best.material, best.material};
        }
        SlotSymbol a = result[1][0];
        SlotSymbol b = result[1][1];
        SlotSymbol c = result[1][2];
        if (a == b && b == c) {
            c = SlotSymbol.values()[(c.ordinal() + 1) % (SlotSymbol.values().length - 1)];
        }
        return new Material[]{a.material, b.material, c.material};
    }

    static boolean isRed(int number) {
        return colorOf(number) == WheelColor.RED;
    }

    private void paintSlots(Player player, Inventory inventory, SlotsHolder holder) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, BULB_OFF.clone());
        }
        for (int row = 0; row < REEL_ROWS; row++) {
            Payline line = lineForRow(row);
            boolean on = line != null && line.ordinal() < holder.lines;
            ItemStack edge = on
                    ? GuiItems.named(line.pane, line.chat + "§l" + line.hint, "§7This line pays.")
                    : GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§8Line off", "§7Add lines to turn it on.");
            inventory.setItem(edgeSlot(row, true), edge.clone());
            inventory.setItem(edgeSlot(row, false), edge.clone());
        }
        inventory.setItem(SLOTS_PAYOUTS, GuiItems.named(
                Material.KNOWLEDGE_BOOK,
                "§e§lPays §8· §73, 5 or 7 in a row",
                "§7Count from the left reel.",
                "",
                "§cCherry      §f5x §8· 18x · 45x",
                "§6Gold        §f8x §8· 30x · 80x",
                "§aEmerald     §f12x §8· 55x · 160x",
                "§bDiamond     §f22x §8· 110x · 420x",
                "§8Netherite   §f45x §8· 260x · 950x",
                "§eStar        §f90x §8· 650x · 2.2k",
                "§6§lCrown       §f220x §8· 2.2k · 8.5k",
                "",
                "§fBook §7anywhere: §d3 = 7 free spins"
        ));
        boolean free = holder.freeSpinsLeft > 0;
        ItemStack logo = GuiItems.named(
                free ? Material.BOOK : Material.JUKEBOX,
                free ? "§d§lFREE SPINS §f" + holder.freeSpinsLeft : "§6§lSLOTS",
                "§7Match pictures from the left.",
                "§73 books anywhere: free spins."
        );
        glow(logo);
        inventory.setItem(SLOTS_LOGO, logo);
        inventory.setItem(SLOTS_PURSE, purse(player, holder.lastWin));
        inventory.setItem(SLOTS_BET, betButton(holder.bet));
        inventory.setItem(SLOTS_LINE_BTN, linesButton(holder));
        ItemStack spin;
        if (holder.spinning) {
            spin = GuiItems.named(
                    Material.TRIPWIRE_HOOK,
                    holder.usingFree || free ? "§d§lFREE SPIN" : "§7Spinning...",
                    "§8Wait for the reels."
            );
        } else {
            spin = GuiItems.named(
                    Material.LEVER,
                    free ? "§d§lFREE SPIN" : "§a§lSPIN",
                    free ? "§dFree spins left: §f" + holder.freeSpinsLeft : "§7Cost: §6" + format(holder.cost()) + " coins",
                    free ? "§8Costs nothing." : "§8" + format(holder.bet) + " × " + holder.lines + (holder.lines == 1 ? " line" : " lines"),
                    "",
                    "§eClick to pull."
            );
            glow(spin);
        }
        inventory.setItem(SLOTS_SPIN, spin);
        inventory.setItem(SLOTS_BACK, GuiItems.named(Material.ARROW, "§eBack", "§7To the casino floor."));
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
        paintLights(inventory, slotsLights(player), holder.fx, Bukkit.getCurrentTick(), false);
        holder.lightsKey = lightsKey(holder.fx, Bukkit.getCurrentTick());
        if (holder.window == null) {
            ItemStack blank = GuiItems.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8?", "§7Pull the lever.");
            for (int col = 0; col < REEL_COLS; col++) {
                for (int row = 0; row < REEL_ROWS; row++) {
                    inventory.setItem(reelSlot(row, col), blank.clone());
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
        lore.add("§7More lines, more ways to win.");
        lore.add("§7Each line costs one bet.");
        lore.add("");
        for (Payline line : Payline.values()) {
            boolean on = line.ordinal() < holder.lines;
            lore.add((on ? line.chat : "§8") + (on ? "● " : "○ ") + line.hint);
        }
        lore.add("");
        lore.add(holder.lines >= Payline.values().length ? "§eClick to reset to 1." : "§eClick to add one.");
        return GuiItems.named(
                Material.MAP,
                "§eLines §8· §f" + holder.lines + "/" + Payline.values().length,
                lore
        );
    }

    private void drawReel(Inventory inventory, SlotsHolder holder, int col) {
        if (holder.window == null) {
            return;
        }
        for (int row = 0; row < REEL_ROWS; row++) {
            boolean glow = holder.glow != null && holder.glow[row][col];
            SlotSymbol symbol = holder.window[row][col];
            inventory.setItem(reelSlot(row, col), holder.dimmed && !glow ? symbol.dimIcon() : symbol.icon(glow));
        }
    }

    private void paintRoulette(Player player, Inventory inventory, RouletteHolder holder) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, BULB_OFF.clone());
        }
        ItemStack wood = GuiItems.named(Material.BROWN_STAINED_GLASS_PANE, " ");
        for (int slot : ROULETTE_WOOD) {
            inventory.setItem(slot, wood.clone());
        }
        Material feltMaterial = Material.GREEN_STAINED_GLASS_PANE;
        if (holder.dimmed) {
            feltMaterial = holder.lastWon ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack felt = GuiItems.named(feltMaterial, " ");
        for (int inner : ROULETTE_FELT) {
            inventory.setItem(inner, felt.clone());
        }
        for (int i = 0; i < RING_SLOTS.length; i++) {
            boolean hot = i == holder.highlight || i == holder.landed;
            boolean locked = holder.landed >= 0 && i == holder.landed;
            if (holder.dimmed && !locked) {
                inventory.setItem(RING_SLOTS[i], GuiItems.named(
                        Material.GRAY_STAINED_GLASS_PANE, "§8" + RING_NUMBERS[i]));
                continue;
            }
            inventory.setItem(RING_SLOTS[i], pocketIcon(i, hot, locked));
        }
        int shown = holder.highlight >= 0 ? RING_NUMBERS[holder.highlight] : -1;
        inventory.setItem(BALL, ballIcon(shown, holder.landed >= 0));
        inventory.setItem(ROULETTE_BACK, GuiItems.named(Material.ARROW, "§eBack", "§7To the casino floor."));
        inventory.setItem(ROULETTE_RED, colorButton(holder, WheelColor.RED, Material.RED_WOOL, "§c", "RED", "§f2x"));
        inventory.setItem(ROULETTE_BLACK, colorButton(holder, WheelColor.BLACK, Material.BLACK_WOOL, "§8", "BLACK", "§f2x"));
        inventory.setItem(ROULETTE_GREEN, colorButton(holder, WheelColor.GREEN, Material.LIME_WOOL, "§a", "GREEN", "§a14x §8· only zero"));
        inventory.setItem(ROULETTE_BET, betButton(holder.bet));
        ItemStack spin;
        if (holder.spinning) {
            spin = GuiItems.named(Material.TRIPWIRE_HOOK, "§7Rolling...", "§8Wait for the ball.");
        } else {
            spin = GuiItems.named(
                    Material.LEVER,
                    "§a§lSPIN",
                    "§7Bet §e" + format(holder.bet) + " §7on " + holder.color.chat,
                    "",
                    "§eClick to spin."
            );
            glow(spin);
        }
        inventory.setItem(ROULETTE_SPIN, spin);
        inventory.setItem(ROULETTE_PURSE, purse(player, 0L));
        paintLights(inventory, ROULETTE_LIGHTS, holder.fx, Bukkit.getCurrentTick(), true);
        holder.lightsKey = lightsKey(holder.fx, Bukkit.getCurrentTick());
    }

    private static ItemStack colorButton(
            RouletteHolder holder, WheelColor color, Material material, String code, String word, String pays
    ) {
        boolean picked = holder.color == color;
        ItemStack item = GuiItems.named(
                material,
                picked ? code + "§l" + word + " §a✔" : code + word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT),
                "§7Pays " + pays,
                picked ? "§aYour pick." : "§eClick to pick."
        );
        if (picked) {
            glow(item);
        }
        return item;
    }

    private ItemStack purse(Player player, long lastWin) {
        if (lastWin > 0L) {
            ItemStack item = GuiItems.named(
                    Material.SUNFLOWER,
                    "§6Purse",
                    "§7Coins: §e" + coins.formatted(player),
                    "§a+" + format(lastWin) + " §7last spin"
            );
            glow(item);
            return item;
        }
        return GuiItems.named(
                Material.SUNFLOWER,
                "§6Purse",
                "§7Coins: §e" + coins.formatted(player)
        );
    }

    private ItemStack betButton(long bet) {
        return GuiItems.named(
                Material.GOLD_NUGGET,
                "§eBet §8· §f" + format(bet),
                "§eLeft click §7· more",
                "§eRight click §7· less"
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

    static void glow(ItemStack item) {
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

        /** Pushed back while a win is on screen, so the winning line pops. */
        ItemStack dimIcon() {
            return GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§8" + org.bukkit.ChatColor.stripColor(label));
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
        RED(Material.RED_CONCRETE, "§cRed", 2L, "RED", NamedTextColor.RED),
        BLACK(Material.BLACK_CONCRETE, "§8Black", 2L, "BLACK", NamedTextColor.GRAY),
        GREEN(Material.LIME_CONCRETE, "§aGreen", 14L, "ZERO", NamedTextColor.GREEN);

        final Material wool;
        final String chat;
        final long payout;
        final String word;
        final NamedTextColor title;

        WheelColor(Material wool, String chat, long payout, String word, NamedTextColor title) {
            this.wool = wool;
            this.chat = chat;
            this.payout = payout;
            this.word = word;
            this.title = title;
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

    enum Fx {
        IDLE, SPIN, WIN, BIG, JACKPOT, FREE, LOSE
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
        Fx fx = Fx.IDLE;
        int fxUntil;
        boolean dimmed;
        int dimUntil;
        long lastWin;
        int lightsKey = -1;

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
        Fx fx = Fx.IDLE;
        int fxUntil;
        boolean dimmed;
        int dimUntil;
        boolean lastWon;
        int lightsKey = -1;

        RouletteHolder(long bet) {
            this.bet = bet;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
