package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.isle.ForageItems.Grade;
import de.aetherion.foraging.isle.ForageItems.Tonic;
import de.aetherion.foraging.weather.WeatherKind;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every Foraging Eldervale board: the Grove Journal ({@code /grove}) with mastery and places, Pell's
 * Lumber Board, Tamsin's bench, Juniper's Forest Ledger and the Find Cabinet. Content stays in the
 * inner columns; one holder type carries the per-slot click actions.
 */
public final class ForageMenus implements Listener {

    private static final int[] INNER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38,
            39, 40, 41, 42, 43};

    private final ForageIsle isle;

    ForageMenus(ForageIsle isle) {
        this.isle = isle;
    }

    static final class Menu implements InventoryHolder {
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private Menu menu(int rows, String title) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, rows * 9, ForageText.legacy(title));
        ItemStack edge = pane(Material.GREEN_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < rows * 9; i++) {
            int col = i % 9;
            boolean border = i < 9 || i >= (rows - 1) * 9 || col == 0 || col == 8;
            menu.inventory.setItem(i, border ? edge : fill);
        }
        return menu;
    }

    private static void set(Menu menu, int slot, ItemStack item, Consumer<ClickType> action) {
        menu.inventory.setItem(slot, item);
        if (action != null) {
            menu.actions.put(slot, action);
        } else {
            menu.actions.remove(slot);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Consumer<ClickType> action = menu.actions.get(event.getRawSlot());
        if (action != null && event.getWhoClicked() instanceof Player player) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
            action.accept(event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ the journal

    public void openJournal(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(6, "§2❦ Grove Journal");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.setDisplayName("§a" + player.getName() + " §8· §2Foraging Eldervale");
            skull.setLore(List.of(
                    "§7Warden Standing",
                    WardenStanding.line(profile),
                    "",
                    "§7Isle fells §f" + profile.totalFells + " §8· §6Perfect §f" + profile.perfects + " §8· §6Titans §f" + profile.titans,
                    "§7Crown Finds §f" + profile.finds + " §8· §7Deadfalls §f" + profile.deadfalls + " §8· §7Critters §f" + profile.critters,
                    "§7Board orders §f" + profile.ordersDone,
                    "",
                    "§7Forests §f" + profile.groves.size() + "/" + Grove.values().length
                            + " §8· §7Places §f" + countPlaces(profile) + "/" + isle.config().landmarks().size(),
                    "§7Isle event §8· " + isle.events().status()));
            head.setItemMeta(skull);
        }
        set(menu, 4, head, null);
        int slot = 10;
        for (Grove grove : Grove.values()) {
            set(menu, slot++, groveTile(player, profile, grove), click -> {
                Location at = isle.compass().resolve(grove.id(), isle.isleWorld());
                if (at != null) {
                    player.closeInventory();
                    isle.compass().point(player, grove.display(), at);
                }
            });
        }
        set(menu, 28, button(Material.GOLDEN_AXE, "§6Grove Mastery", "§7Nine woods, seven tiers each.", "§eClick to open."),
                click -> openMastery(player));
        set(menu, 29, marksSummary(profile), null);
        set(menu, 30, button(Material.CHISELED_BOOKSHELF, "§dFind Cabinet",
                "§7" + profile.cabinet.size() + "/28 slots filled.", "§eClick to look."), click -> openCabinet(player, false));
        set(menu, 31, ordersSummary(profile), click -> {
            Location at = isle.compass().resolve(ForageCast.BOARD_CLERK, isle.isleWorld());
            if (at != null) {
                player.closeInventory();
                isle.compass().point(player, "Pell · Lumber Board", at);
            }
        });
        set(menu, 32, recordsItem(), null);
        set(menu, 33, button(Material.FILLED_MAP, "§fPlaces & Updrafts", "§7Every named place and wind shaft.",
                "§eClick to pick a destination."), click -> openPlaces(player));
        set(menu, 34, button(Material.COMPASS, "§aTour",
                profile.tourStep >= 4 ? "§7Done. Click to walk it again." : "§7Step " + (profile.tourStep + 1) + "/4.",
                "§eClick to point the way."), click -> {
            player.closeInventory();
            isle.compass().startTour(player, profile);
        });
        set(menu, 40, charmsItem(player, profile), null);
        set(menu, 49, button(Material.BARRIER, "§cClose"), click -> player.closeInventory());
        player.openInventory(menu.inventory);
    }

    private int countPlaces(ForageProfile profile) {
        int n = 0;
        for (String id : isle.config().landmarks().keySet()) {
            if (profile.places.contains(id)) {
                n++;
            }
        }
        return n;
    }

    private ItemStack groveTile(Player player, ForageProfile profile, Grove grove) {
        boolean known = profile.groves.contains(grove.id());
        if (!known) {
            return button(Material.GRAY_DYE, "§8??? §7(undiscovered forest)", "§7Somewhere on the island.",
                    "§eClick to be pointed there.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("§7" + grove.tagline());
        lore.add("");
        for (Wood wood : Wood.values()) {
            if (wood.grove() == grove) {
                lore.add(GroveMastery.progress(profile, wood));
            }
        }
        int found = 0;
        List<Landmark> places = isle.config().landmarksOf(grove);
        for (Landmark landmark : places) {
            if (profile.places.contains(landmark.id())) {
                found++;
            }
        }
        lore.add("§7Places §f" + found + "/" + places.size());
        Find find = Find.of(grove);
        lore.add("§7Crown Find §8· " + find.colored());
        if (isle.events().hotspot() == grove) {
            lore.add("§6✦ Golden Sap is running here!");
        }
        lore.add("");
        lore.add("§eClick to be pointed there.");
        ItemStack item = button(grove.icon(), grove.colored(), lore.toArray(new String[0]));
        if (isle.events().hotspot() == grove) {
            glint(item);
        }
        return item;
    }

    private ItemStack marksSummary(ForageProfile profile) {
        List<String> lore = new ArrayList<>();
        for (Woodwright.Line line : Woodwright.LINES) {
            int tier = profile.mark(line.id());
            lore.add(line.color() + line.display() + " §f" + (tier == 0 ? "§8—" : ForageText.roman(tier))
                    + (tier > 0 ? " §8· §7" + line.perTier()[tier - 1] : ""));
        }
        lore.add("");
        lore.add("§8Forge them with Tamsin at the Bell Lodge.");
        return button(Material.SMITHING_TABLE, "§6Grove Marks", lore.toArray(new String[0]));
    }

    private ItemStack ordersSummary(ForageProfile profile) {
        List<String> lore = new ArrayList<>();
        for (LumberBoard.Order order : profile.orders) {
            lore.add(order == null ? "§8· restocking…" : "§8· §f" + order.title()
                    + (order.kind == LumberBoard.Kind.LOGS || order.kind == LumberBoard.Kind.FIND ? " §8(hand-in)"
                    : " §7" + order.progress + "§8/§7" + order.amount));
        }
        if (lore.isEmpty()) {
            lore.add("§7Visit Pell for your first orders.");
        }
        lore.add("");
        lore.add("§eClick to be pointed to Pell.");
        return button(Material.WRITABLE_BOOK, "§2Lumber Board", lore.toArray(new String[0]));
    }

    private ItemStack recordsItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Largest fell per forest:");
        for (Grove grove : Grove.values()) {
            ForageProfiles.Record rec = isle.profiles().record("fell." + grove.id());
            lore.add(grove.color() + grove.display() + " §8· " + (rec == null ? "§8—" : "§f" + rec.name() + " §7" + rec.value()));
        }
        lore.add("");
        lore.add("§7Heaviest find:");
        for (Find find : Find.values()) {
            ForageProfiles.Record rec = isle.profiles().record("find." + find.id());
            lore.add(find.colored() + " §8· " + (rec == null ? "§8—" : "§f" + rec.name() + " §7" + ForageText.grams(rec.value())));
        }
        return button(Material.OAK_HANGING_SIGN, "§6Isle Records", lore.toArray(new String[0]));
    }

    private ItemStack charmsItem(Player player, ForageProfile profile) {
        long now = System.currentTimeMillis();
        List<String> lore = new ArrayList<>();
        lore.add("§7Crown Shaker charges §f" + profile.charge(Woodwright.CHARGE_SHAKER));
        lore.add("§7Heartwood Incense §f" + (profile.incenseUntil > now ? ForageText.clock((profile.incenseUntil - now) / 1000) : "§8—"));
        lore.add("§7Frostlit §f" + (profile.frostlitUntil > now ? ForageText.clock((profile.frostlitUntil - now) / 1000) : "§8—"));
        lore.add("§7Tailwind §f" + (profile.tailwindUntil > now ? ForageText.clock((profile.tailwindUntil - now) / 1000) : "§8—"));
        WeatherKind weather = isle.weatherKind(player);
        lore.add("§7Weather here §f" + (weather == null ? "—" : ForageText.pretty(weather.name())));
        lore.add("");
        lore.add("§8Skills: /skills foraging" + (ForageBridge.skillsInstalled() ? "" : " §8(Eldervale skills need the new Items jar)"));
        return button(Material.BREWING_STAND, "§dActive now", lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------------ mastery

    public void openMastery(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(5, "§2❦ Grove Mastery");
        int i = 0;
        for (Wood wood : Wood.values()) {
            int tier = GroveMastery.tier(profile, wood);
            List<String> lore = new ArrayList<>();
            lore.add(GroveMastery.progress(profile, wood));
            lore.add("");
            lore.add("§7Per tier: §a+1 wood cap §7· §d+0.25% heartwood");
            lore.add((tier >= 4 ? "§a✔" : "§8·") + " §7IV: CHOP window +1 on " + wood.display());
            lore.add((tier >= 7 ? "§a✔" : "§8·") + " §7VII: Crown Finds ×1.5 · Warden of " + wood.display());
            if (tier < GroveMastery.MAX_TIER) {
                lore.add("");
                lore.add("§7Next tier pays §6" + ForageText.coins(GroveMastery.coins(tier + 1)) + " coins");
            }
            lore.add("");
            lore.add("§8Counts trees felled on the isle" + (wood.grove() == null ? "" : " · " + wood.grove().display()));
            ItemStack tile = button(wood.log(), wood.colored() + " §7" + ForageText.roman(tier), lore.toArray(new String[0]));
            if (tier >= GroveMastery.MAX_TIER) {
                glint(tile);
            }
            set(menu, INNER[i++], tile, null);
        }
        set(menu, 40, button(Material.ARROW, "§eBack"), click -> openJournal(player));
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ places

    public void openPlaces(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(6, "§2❦ Places & Updrafts");
        int i = 0;
        for (Landmark landmark : isle.config().landmarks().values()) {
            if (i >= INNER.length) {
                break;
            }
            boolean found = profile.places.contains(landmark.id());
            Material icon = landmark.grove() == null ? Material.MAP : landmark.grove().icon();
            ItemStack tile = found
                    ? button(icon, landmark.colored(), "§7" + landmark.blurb(), "", "§eClick to be pointed there.")
                    : button(Material.GRAY_DYE, "§8??? §7(unfound place)", "§7" + (landmark.grove() == null ? "" : "In " + landmark.grove().display()),
                    "", "§eClick to be pointed there.");
            set(menu, INNER[i++], tile, click -> {
                player.closeInventory();
                isle.compass().point(player, found ? landmark.display() : "unfound place", landmark.anchor(isle.isleWorld()));
            });
        }
        for (ForageConfig.Updraft draft : isle.config().updrafts().values()) {
            if (i >= INNER.length) {
                break;
            }
            set(menu, INNER[i++], button(Material.FEATHER, "§f≋ " + draft.display(),
                    "§7Rises " + (int) Math.round(draft.ty() - draft.fy()) + " blocks.", "", "§eClick to be pointed to the vent."), click -> {
                player.closeInventory();
                isle.compass().point(player, draft.display(), new Location(isle.isleWorld(), draft.fx(), draft.fy(), draft.fz()));
            });
        }
        set(menu, 49, button(Material.ARROW, "§eBack"), click -> openJournal(player));
        set(menu, 50, button(Material.BARRIER, "§cStop pointing"), click -> {
            isle.compass().stop(player);
            player.closeInventory();
        });
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ Pell: the Lumber Board

    public void openBoard(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        isle.board().refresh(player, profile);
        Menu menu = menu(3, "§2❦ Lumber Board §8· Pell");
        long now = System.currentTimeMillis();
        int[] slots = {11, 13, 15};
        for (int i = 0; i < LumberBoard.SLOTS; i++) {
            LumberBoard.Order order = i < profile.orders.size() ? profile.orders.get(i) : null;
            int index = i;
            if (order == null) {
                Long at = profile.restockAt.get(i);
                set(menu, slots[i], button(Material.PAPER, "§8Restocking…",
                        "§7New order in §f" + ForageText.clock(Math.max(0L, (at == null ? 0L : at - now) / 1000L))), null);
                continue;
            }
            List<String> lore = new ArrayList<>();
            if (order.kind == LumberBoard.Kind.LOGS || order.kind == LumberBoard.Kind.FIND) {
                lore.add("§7Hand-in. Bring it, click here.");
            } else {
                lore.add("§7Progress §f" + order.progress + "§8/§f" + order.amount + " " + ForageText.cells(order.progress / (double) order.amount, "§a"));
            }
            lore.add("");
            lore.add("§7Pays §6" + ForageText.coins(order.coins) + " coins §8· §2+" + order.standing + " Standing");
            if (ForageBridge.has(player, ForageBridge.BOARD_RATES)) {
                lore.add("§8(Board Rates adds on top)");
            }
            lore.add("");
            lore.add(order.done() || order.kind == LumberBoard.Kind.LOGS || order.kind == LumberBoard.Kind.FIND
                    ? "§eClick to " + (order.done() ? "collect" : "hand in") + "." : "§8Keep working — it fills as you go.");
            ItemStack tile = button(order.icon(), "§f" + order.title(), lore.toArray(new String[0]));
            if (order.done()) {
                glint(tile);
            }
            set(menu, slots[i], tile, click -> {
                String line = isle.board().work(player, index);
                if (line != null) {
                    player.sendMessage(line);
                }
                openBoard(player);
            });
        }
        long free = profile.ordersRerollAt - now;
        set(menu, 22, button(Material.CLOCK, "§eReroll the board",
                "§7Free reroll in §f" + ForageText.clock(Math.max(0L, free / 1000L)),
                "§7Or now for §6" + LumberBoard.REROLL_COST + " coins§7.", "", "§eClick to pay and reroll."), click -> {
            if (isle.board().paidReroll(player, profile)) {
                player.sendMessage("§2Pell tears the sheet down §8· §7fresh orders.");
            } else {
                player.sendMessage("§cNot enough coins.");
            }
            openBoard(player);
        });
        set(menu, 18, button(Material.BOOK, "§7Grove Journal"), click -> openJournal(player));
        set(menu, 26, button(Material.BARRIER, "§cClose"), click -> player.closeInventory());
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ Tamsin: the bench

    public void openBench(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(5, "§6❦ Woodwright's Bench §8· Tamsin");
        int standingLevel = WardenStanding.level(profile.standing);
        int[] lineSlots = {11, 12, 13, 14, 15};
        int next = 0;
        for (Woodwright.Line line : Woodwright.LINES) {
            int slot = lineSlots[next++];
            int tier = profile.mark(line.id());
            List<String> lore = new ArrayList<>();
            for (int t = 1; t <= Woodwright.MAX_TIER; t++) {
                lore.add((t <= tier ? "§a✔ " : "§8· ") + "§7" + ForageText.roman(t) + " §8· §f" + line.perTier()[t - 1]);
            }
            lore.add("");
            if (tier >= Woodwright.MAX_TIER) {
                lore.add("§6✦ Complete.");
            } else {
                Woodwright.Cost cost = Woodwright.cost(line, tier + 1);
                lore.add("§7Next: §f" + ForageText.roman(tier + 1));
                lore.addAll(cost.lore(player));
                if (cost.standingLevel() > 0) {
                    lore.add((standingLevel >= cost.standingLevel() ? "§a✔" : "§c✘") + " §7Standing §f"
                            + WardenStanding.title(cost.standingLevel()));
                }
                lore.add("");
                lore.add("§eClick to forge.");
            }
            ItemStack tile = button(line.icon(), line.color() + line.display() + " §7" + (tier == 0 ? "—" : ForageText.roman(tier)),
                    lore.toArray(new String[0]));
            if (tier >= Woodwright.MAX_TIER) {
                glint(tile);
            }
            set(menu, slot, tile, click -> {
                player.sendMessage(isle.woodwright().forge(player, line.id()));
                openBench(player);
            });
        }
        int[] tonicSlots = {29, 31, 33};
        Tonic[] tonics = Tonic.values();
        String[] costs = {"§f32 Acacia Logs §8· §6150 coins", "§f1 heartwood §8· §6400 coins", "§f2 Crown Finds §8· §6600 coins"};
        for (int i = 0; i < tonics.length; i++) {
            Tonic tonic = tonics[i];
            List<String> lore = new ArrayList<>(tonic.lore);
            lore.add("");
            lore.add("§7Costs " + costs[i]);
            lore.add("");
            lore.add("§eClick to have one made.");
            set(menu, tonicSlots[i], button(tonic.icon, tonic.display, lore.toArray(new String[0])), click -> {
                player.sendMessage(isle.woodwright().craft(player, tonic));
                openBench(player);
            });
        }
        set(menu, 4, button(Material.NAME_TAG, "§2Warden Standing", WardenStanding.line(profile),
                "§7III needs " + WardenStanding.title(2) + ", IV " + WardenStanding.title(4) + ", V " + WardenStanding.title(6) + "."), null);
        set(menu, 36, button(Material.BOOK, "§7Grove Journal"), click -> openJournal(player));
        set(menu, 44, button(Material.BARRIER, "§cClose"), click -> player.closeInventory());
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ Juniper: the ledger

    public void openLedger(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(5, "§d❦ Forest Ledger §8· Juniper");
        int i = 0;
        for (Wood wood : Wood.values()) {
            long have = ForestLedger.codex(player, wood);
            int reached = ForestLedger.reached(player, wood);
            int claimed = ForestLedger.claimed(profile, wood);
            List<String> lore = new ArrayList<>();
            lore.add("§7Codex §f" + ForageText.coins(have) + " §7logs");
            for (int m = 0; m < ForestLedger.MILESTONES.length; m++) {
                String mark = m < claimed ? "§a✔" : m < reached ? "§e✦" : "§8·";
                lore.add(mark + " §7" + ForageText.roman(m + 1) + " §8· §f" + ForageText.coins(ForestLedger.MILESTONES[m]));
            }
            lore.add("");
            lore.add(reached > claimed ? "§eClick to claim!" : "§8Nothing to claim yet.");
            ItemStack tile = button(wood.log(), wood.colored() + " §7collection", lore.toArray(new String[0]));
            if (reached > claimed) {
                glint(tile);
            }
            set(menu, INNER[i++], tile, click -> {
                player.sendMessage(isle.ledger().claimCollection(player, wood));
                openLedger(player);
            });
        }
        set(menu, 4, button(Material.KNOWLEDGE_BOOK, "§dCollector rank §f" + ForageText.roman(ForestLedger.collectorRank(profile)),
                "§7Every 6 claimed milestones: §d+0.2% heartwood §7on the isle."), null);
        set(menu, 29, button(Material.CHISELED_BOOKSHELF, "§dFind Cabinet", "§7" + profile.cabinet.size() + "/28 · rows and columns pay.",
                "§eClick to open."), click -> openCabinet(player, true));
        set(menu, 31, button(Material.EMERALD, "§6Sell Crown Finds", "§7Sells every find you carry",
                "§7except your best of each kind.", "", "§eLeft: keep best · §cShift-right: sell all"), click -> {
            player.sendMessage(isle.ledger().sellFinds(player, null, click != ClickType.SHIFT_RIGHT));
            openLedger(player);
        });
        set(menu, 33, recordsItem(), null);
        set(menu, 36, button(Material.BOOK, "§7Grove Journal"), click -> openJournal(player));
        set(menu, 44, button(Material.BARRIER, "§cClose"), click -> player.closeInventory());
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ the Find Cabinet

    public void openCabinet(Player player, boolean atArchivist) {
        ForageProfile profile = isle.profiles().get(player);
        Menu menu = menu(6, "§d❦ Find Cabinet");
        Find[] finds = Find.values();
        Grade[] grades = Grade.values();
        for (int c = 0; c < finds.length; c++) {
            Find find = finds[c];
            boolean full = ForestLedger.rowFull(profile, find);
            boolean paid = profile.cabinetPaid.contains("row:" + find.id());
            ItemStack head = button(find.icon, find.colored(), "§7" + find.grove.colored(),
                    "", paid ? "§a✔ Framed §8· +1 wood cap here" : full ? "§eAll grades! " + (atArchivist ? "Click to frame." : "Frame it at Juniper.")
                            : "§7Catch all four grades to frame it.");
            if (full && !paid) {
                glint(head);
            }
            set(menu, 1 + c, head, atArchivist ? click -> {
                player.sendMessage(isle.ledger().claimRow(player, find));
                openCabinet(player, true);
            } : null);
            for (int r = 0; r < grades.length; r++) {
                Grade grade = grades[r];
                boolean has = ForestLedger.has(profile, find, grade);
                Material pane = !has ? Material.GRAY_STAINED_GLASS_PANE : switch (grade) {
                    case ROUGH -> Material.WHITE_STAINED_GLASS_PANE;
                    case FINE -> Material.LIME_STAINED_GLASS_PANE;
                    case PRISTINE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                    case HEARTSONG -> Material.YELLOW_STAINED_GLASS_PANE;
                };
                set(menu, 10 + r * 9 + c, button(pane, has ? grade.colored() + " " + find.colored() : "§8? " + grade.display + " " + find.display),
                        null);
            }
        }
        for (int r = 0; r < grades.length; r++) {
            Grade grade = grades[r];
            boolean full = ForestLedger.columnFull(profile, grade);
            boolean paid = profile.cabinetPaid.contains("col:" + grade.name());
            ItemStack tile = button(Material.ITEM_FRAME, grade.colored() + " §7row",
                    paid ? "§a✔ Framed" : full ? "§eEvery find at " + grade.display + "! " + (atArchivist ? "Click to frame." : "Frame it at Juniper.")
                            : "§7One " + grade.display + " of every find.");
            if (full && !paid) {
                glint(tile);
            }
            set(menu, 9 + r * 9, tile, atArchivist ? click -> {
                player.sendMessage(isle.ledger().claimColumn(player, grade));
                openCabinet(player, true);
            } : null);
        }
        set(menu, 49, button(Material.ARROW, "§eBack"), click -> {
            if (atArchivist) {
                openLedger(player);
            } else {
                openJournal(player);
            }
        });
        player.openInventory(menu.inventory);
    }

    // ------------------------------------------------------------------ bits

    static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(new ArrayList<>(Arrays.asList(lore)));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
    }
}
