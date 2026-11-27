package de.aetherion.items.menu;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ChromePage;
import de.aetherion.core.api.IslandAccess;
import de.aetherion.core.api.JournalPage;
import de.aetherion.core.api.JournalPage.Chapter;
import de.aetherion.core.api.JournalPage.Ink;
import de.aetherion.core.api.JournalPage.Look;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexMenus;
import de.aetherion.items.codex.CodexRewards;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.guide.GuideAdvice;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.util.GuiIcons;
import de.aetherion.items.util.GuiItems;
import de.aetherion.items.util.QuestProgressHook;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Aether Journal: the Manager as a book ({@code journal.enabled} in config.yml, off = the old Nether Star
 * Manager). Nine chapters behind silk ribbons in row 0; each chapter is one open spread of at most eight lines
 * (rows 1..4, left and right page), drawn into the inventory title by {@link JournalPage}. Chapters appear along
 * the unlock gates the Manager already had, so the book grows with the player; the one that opens next shows as
 * a pale ribbon. The tiles keep their Manager actions and gate hints ({@link AetherionManagerListener#runTile}).
 *
 * <p>Tutorial texts point here through {@link de.aetherion.core.api.JournalText} ("Journal › Character ›
 * Skills"); the chapter of every tile below must match the chapter there.
 */
public final class JournalMenu {

    /** Back on a sub-menu, or the book opened again soon after, turns to the chapter the player left. */
    private static final long BOOKMARK_MS = 5 * 60_000L;
    private static final int BACK_SLOT = 45;
    private static final int TOOLTIP_WIDTH = 36;

    private final AetherionManager manager;
    private final Map<UUID, Bookmark> bookmarks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pulses = new ConcurrentHashMap<>();

    private record Bookmark(Chapter chapter, long at) {
    }

    JournalMenu(AetherionManager manager) {
        this.manager = manager;
    }

    /* =========================================================
     * Chapters and tiles
     * ========================================================= */

    static String name(Chapter chapter) {
        return switch (chapter) {
            case TODAY -> "Today";
            case CHARACTER -> "Character";
            case JOURNEY -> "Journey";
            case ATLAS -> "Atlas";
            case WORKSHOP -> "Workshop";
            case MARKET -> "Market";
            case CODEX -> "Codex";
            case COMPANY -> "Company";
            case SETTINGS -> "Settings";
        };
    }

    private static String blurb(Chapter chapter) {
        return switch (chapter) {
            case TODAY -> "Your goal, what opens next, the essentials.";
            case CHARACTER -> "Stats, skills, pets, storage, loadouts.";
            case JOURNEY -> "Your quest, turn-ins and the talk log.";
            case ATLAS -> "Teleports, dungeons and the isles' journals.";
            case WORKSHOP -> "Crafting, recipes and the anvil.";
            case MARKET -> "Shop, Bazaar and Auction House.";
            case CODEX -> "Collection, Bestiary, bosses and milestones.";
            case COMPANY -> "Party, friends, island and guild.";
            case SETTINGS -> "Language, talk mode, reports.";
        };
    }

    private static ChromePage.Theme theme(Chapter chapter) {
        return JournalPage.theme(chapter);
    }

    /** Manager tiles by chapter. Labels are the names the tutorial texts use (JournalText's line names). */
    private enum Tile {
        STATS(AetherionManagerGUI.STATS_SLOT, "Stats", Material.EXPERIENCE_BOTTLE, Chapter.CHARACTER,
                "Your level, gear and active stats."),
        SKILLS(AetherionManagerGUI.SKILLS_SLOT, "Skills", Material.NETHERITE_SCRAP, Chapter.CHARACTER,
                "Seven slots, one to start. Skills level with you; swap freely."),
        PETS(AetherionManagerGUI.PETS_SLOT, "Pets", Material.LEAD, Chapter.CHARACTER,
                "Your caught pets. Equip one: they add stats, quietly."),
        STORAGE(AetherionManagerGUI.STORAGE_SLOT, "Storage", Material.ENDER_CHEST, Chapter.CHARACTER,
                "Your personal storage. Extra pages for the pack rat."),
        LOADOUTS(AetherionManagerGUI.LOADOUT_SLOT, "Loadouts", Material.ARMOR_STAND, Chapter.CHARACTER,
                "Save and swap armour sets."),
        SHOP(AetherionManagerGUI.SHOP_SLOT, "Shop", Material.AMETHYST_SHARD, Chapter.MARKET,
                "Spend Aether Crystals. Always open."),
        BAZAAR(AetherionManagerGUI.BAZAAR_SLOT, "Bazaar", Material.CHEST, Chapter.MARKET,
                "Player listings for resources. The trader buys them too."),
        AUCTION(AetherionManagerGUI.AUCTION_SLOT, "Auction", Material.GOLD_BLOCK, Chapter.MARKET,
                "Auction House: gear and tools, buyout listings."),
        TELEPORTS(AetherionManagerGUI.SPAWN_SLOT, "Teleports", Material.COMPASS, Chapter.ATLAS,
                "Every camp you have walked into, one click away. Boss quests unlock more."),
        CODEX(AetherionManagerGUI.CODEX_SLOT, "Codex", Material.WRITABLE_BOOK, Chapter.CODEX,
                "Collection, Bestiary, Boss Journal and milestones in one book."),
        CRAFTING(AetherionManagerGUI.CRAFT_SLOT, "Crafting", Material.CRAFTING_TABLE, Chapter.WORKSHOP,
                "A portable workbench."),
        RECIPES(AetherionManagerGUI.RECIPE_SLOT, "Recipes", Material.KNOWLEDGE_BOOK, Chapter.WORKSHOP,
                "The green Recipe Book: every Aetherion recipe."),
        ANVIL(AetherionManagerGUI.ANVIL_SLOT, "Anvil", Material.ANVIL, Chapter.WORKSHOP,
                "Booster sockets: gear left, booster right. Fourteen slots, swap any time."),
        ISLAND(AetherionManagerGUI.ISLAND_SLOT, "Island", Material.GRASS_BLOCK, Chapter.COMPANY,
                "Your private island: a click takes you home. Without one, the island menu opens."),
        GUILD(AetherionManagerGUI.GUILD_SLOT, "Guild", Material.YELLOW_BANNER, Chapter.COMPANY,
                "Shared island, invites, ranks and guild quarries: the guild menu.");

        final int slot;
        final String label;
        final Material icon;
        final Chapter chapter;
        final String about;

        Tile(int slot, String label, Material icon, Chapter chapter, String about) {
            this.slot = slot;
            this.label = label;
            this.icon = icon;
            this.chapter = chapter;
            this.about = about;
        }

        static Tile bySlot(int slot) {
            for (Tile tile : values()) {
                if (tile.slot == slot) {
                    return tile;
                }
            }
            return null;
        }
    }

    /** How a tile reads today. */
    private enum State { OPEN, NEXT, LOCKED, LATER, HIDDEN }

    /** The one thing a lesson wants opened: its line glows, its ribbon sparks, Today points at it. */
    private record Guide(Tile tile, String tip) {
    }

    /* =========================================================
     * Everything one open needs, read once
     * ========================================================= */

    private final class Read {
        final Player player;
        final ProgressionService progress;
        final Map<Integer, AetherionManagerGUI.Gate> gates = new HashMap<>();
        final AetherionManagerGUI.Gate next;
        final EnumSet<Chapter> visible = EnumSet.noneOf(Chapter.class);
        final Chapter nextChapter;
        final int seen;
        final Guide guide;
        final List<QuestProgressAccess.JournalQuest> quests;

        Read(Player player) {
            this.player = player;
            AetherionItems plugin = AetherionItems.getInstance();
            this.progress = plugin == null ? null : plugin.progress();
            AetherionManagerGUI.Gate first = null;
            for (AetherionManagerGUI.Gate gate : manager.getGui().gates(player, progress)) {
                gates.put(gate.slot(), gate);
                if (!gate.open() && first == null) {
                    first = gate;
                }
            }
            this.next = first;

            visible.add(Chapter.TODAY);
            visible.add(Chapter.CHARACTER);
            visible.add(Chapter.JOURNEY);
            visible.add(Chapter.MARKET);
            visible.add(Chapter.SETTINGS);
            boolean hub = Bukkit.getPluginManager().isPluginEnabled("AetherionHub");
            if (progress == null || (hub ? state(Tile.TELEPORTS) == State.OPEN : anyCommand(ATLAS_LINES))) {
                visible.add(Chapter.ATLAS);
            }
            if (state(Tile.CRAFTING) == State.OPEN || state(Tile.RECIPES) == State.OPEN
                    || state(Tile.ANVIL) == State.OPEN) {
                visible.add(Chapter.WORKSHOP);
            }
            if (AetherionManagerGUI.codexOpen(player, progress)) {
                visible.add(Chapter.CODEX);
            }
            if (companyOpen()) {
                visible.add(Chapter.COMPANY);
            }
            Tile nextTile = next == null ? null : Tile.bySlot(next.slot());
            this.nextChapter = nextTile != null && !visible.contains(nextTile.chapter) ? nextTile.chapter : null;

            PersistentDataContainer data = player.getPersistentDataContainer();
            NamespacedKey key = ItemKeys.key("journal_seen");
            Integer stored = data.get(key, PersistentDataType.INTEGER);
            if (stored == null) {
                // The first open finds the book as it is: only what opens later is new.
                stored = mask(visible);
                data.set(key, PersistentDataType.INTEGER, stored);
            }
            this.seen = stored;
            this.guide = findGuide();
            QuestProgressAccess access = AetherServices.quests();
            List<QuestProgressAccess.JournalQuest> list = access == null ? null : access.journalQuests(player);
            this.quests = list == null ? List.of() : list;
        }

        State state(Tile tile) {
            switch (tile) {
                case PETS -> {
                    if (!manager.hasPetMenu()) {
                        return State.HIDDEN;
                    }
                }
                case TELEPORTS -> {
                    if (!Bukkit.getPluginManager().isPluginEnabled("AetherionHub")) {
                        return State.HIDDEN;
                    }
                }
                case ISLAND, GUILD -> {
                    if (!NetworkHooks.islandsAvailable()) {
                        return State.HIDDEN;
                    }
                }
                default -> {
                }
            }
            AetherionManagerGUI.Gate gate = gates.get(tile.slot);
            if (gate == null || gate.open()) {
                return State.OPEN;
            }
            if (next != null && gate.group().equals(next.group())) {
                return State.NEXT;
            }
            return tile == Tile.ISLAND || tile == Tile.GUILD ? State.LATER : State.LOCKED;
        }

        String hint(Tile tile) {
            AetherionManagerGUI.Gate gate = gates.get(tile.slot);
            return gate == null || gate.hint() == null ? "§7Not open yet." : gate.hint();
        }

        boolean isNew(Chapter chapter) {
            return visible.contains(chapter) && (seen & bit(chapter)) == 0;
        }

        Chapter firstNew() {
            for (Chapter chapter : Chapter.values()) {
                if (isNew(chapter)) {
                    return chapter;
                }
            }
            return null;
        }

        Look look(Chapter chapter, Chapter active) {
            if (chapter == active) {
                return Look.ACTIVE;
            }
            if (visible.contains(chapter)) {
                return isNew(chapter) || (guide != null && guide.tile().chapter == chapter) ? Look.NEW : Look.OPEN;
            }
            return chapter == nextChapter ? Look.NEXT : null;
        }

        private boolean companyOpen() {
            if (progress == null || state(Tile.ISLAND) == State.OPEN || state(Tile.GUILD) == State.OPEN) {
                return true;
            }
            // Party and friends wait until the harbour lets go of the player (orientation stamped).
            QuestProgressAccess quests = AetherServices.quests();
            return quests == null || quests.isQuestCompleted(player, "orientation");
        }

        private Guide findGuide() {
            if (state(Tile.SKILLS) == State.OPEN
                    && QuestProgressHook.isQuestActive(player, AetherionManagerGUI.SKILLS_QUEST_ID)) {
                return new Guide(Tile.SKILLS, "Miss Ledger wants one skill equipped: pick any skill, drop it in a free slot.");
            }
            if (state(Tile.PETS) == State.OPEN && QuestProgressHook.shouldGuidePetsEquip(player)) {
                return new Guide(Tile.PETS, "Lark wants your catch equipped: open Pets and equip it.");
            }
            if (state(Tile.RECIPES) == State.OPEN
                    && QuestProgressHook.isQuestActive(player, AetherionManagerGUI.CRAFT_QUEST_ID)) {
                return new Guide(Tile.RECIPES, "Find the Mining Pickaxe (2nd recipe): Simple Pickaxe in the middle, coal around it.");
            }
            if (state(Tile.TELEPORTS) == State.OPEN && AetherionManagerGUI.hasHomesteadMarker(player)) {
                return new Guide(Tile.TELEPORTS, "Right-click the campfire Marker, then set your camp in Teleports.");
            }
            return null;
        }
    }

    private static int bit(Chapter chapter) {
        return 1 << chapter.ordinal();
    }

    private static int mask(EnumSet<Chapter> chapters) {
        int out = 0;
        for (Chapter chapter : chapters) {
            out |= bit(chapter);
        }
        return out;
    }

    /* =========================================================
     * Opening
     * ========================================================= */

    /** The book as the hotbar item opens it: a new chapter first, then the bookmark, else Today. */
    public void open(Player player) {
        if (player == null) {
            return;
        }
        Read read = new Read(player);
        Chapter fresh = read.firstNew();
        if (fresh != null) {
            open(player, fresh, read);
            return;
        }
        Chapter chapter = Chapter.TODAY;
        Bookmark mark = bookmarks.get(player.getUniqueId());
        if (read.guide == null && mark != null && System.currentTimeMillis() - mark.at() < BOOKMARK_MS
                && read.visible.contains(mark.chapter())) {
            chapter = mark.chapter();
        }
        open(player, chapter, read);
    }

    /** Turns straight to {@code chapter} (Today when it is not in the book yet). */
    public void open(Player player, Chapter chapter) {
        if (player != null) {
            open(player, chapter, new Read(player));
        }
    }

    /**
     * Back from a sub-menu: the chapter the player came from (the bookmark, even while a lesson glows), else the
     * book as the hotbar item opens it.
     */
    public void back(Player player) {
        if (player == null) {
            return;
        }
        Bookmark mark = bookmarks.get(player.getUniqueId());
        if (mark != null && System.currentTimeMillis() - mark.at() < BOOKMARK_MS) {
            open(player, mark.chapter());
            return;
        }
        open(player);
    }

    private void open(Player player, Chapter chapter, Read read) {
        if (chapter == null || !read.visible.contains(chapter)) {
            chapter = Chapter.TODAY;
        }
        boolean fresh = read.isNew(chapter);
        if (fresh) {
            player.getPersistentDataContainer().set(ItemKeys.key("journal_seen"), PersistentDataType.INTEGER,
                    read.seen | bit(chapter));
        }
        Holder holder = new Holder(chapter);
        List<Entry> entries = pack(entries(read, chapter));

        Map<Chapter, Look> ribbons = new EnumMap<>(Chapter.class);
        for (Chapter each : Chapter.values()) {
            Look look = each == chapter ? Look.ACTIVE : read.look(each, chapter);
            if (look != null) {
                ribbons.put(each, look);
            }
        }
        int strong = -1;
        List<JournalPage.Line> lines = new ArrayList<>();
        for (Entry entry : entries) {
            lines.add(new JournalPage.Line(entry.row(), entry.right(), entry.label(), entry.ink()));
            if (entry.strong() && strong < 0) {
                strong = JournalPage.slot(entry.row(), entry.right());
            }
        }
        Inventory inventory = Bukkit.createInventory(holder, 54,
                JournalPage.title(theme(chapter), "Journal · " + name(chapter), ribbons, strong, lines));
        ItemStack spacer = GuiIcons.spacer();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, spacer.clone());
        }
        ribbons(read, chapter, ribbons, inventory, holder);
        int pulse = -1;
        for (Entry entry : entries) {
            int first = JournalPage.slot(entry.row(), entry.right());
            inventory.setItem(first, entry.icon());
            ItemStack carrier = carrier(entry.icon());
            for (int i = 1; i <= 3; i++) {
                inventory.setItem(first + i, carrier.clone());
            }
            if (entry.action() != null) {
                for (int i = 0; i <= 3; i++) {
                    holder.actions.put(first + i, entry.action());
                }
            }
            if (entry.pulse() && pulse < 0) {
                pulse = first;
            }
        }
        boolean today = chapter == Chapter.TODAY;
        List<String> backLore = today ? List.<String>of() : List.of("§8Or pick a ribbon along the top.");
        inventory.setItem(BACK_SLOT, carrier(today ? "§7Close the journal" : "§e‹ Back to Today", backLore));
        Runnable back = today ? () -> {
            JournalSounds.close(player);
            player.closeInventory();
        } : () -> turn(player, Chapter.TODAY);
        holder.actions.put(BACK_SLOT, back);

        stopPulse(player);
        // Turning a ribbon inside the open book, or taking the book out of the hotbar (or back from a sub-menu).
        boolean turning = player.getOpenInventory().getTopInventory().getHolder() instanceof Holder;
        player.openInventory(inventory);
        bookmarks.put(player.getUniqueId(), new Bookmark(chapter, System.currentTimeMillis()));
        if (fresh) {
            JournalSounds.reveal(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "✦ New chapter in your journal: " + name(chapter),
                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
        } else {
            if (turning) {
                JournalSounds.turn(player, chapter.ordinal());
            } else {
                JournalSounds.open(player, coverStage(player));
            }
            if (read.guide != null && (today || chapter == read.guide.tile().chapter)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "→ " + name(read.guide.tile().chapter) + " › " + read.guide.tile().label + " is glowing",
                        net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            }
        }
        if (pulse >= 0) {
            startPulse(player, holder, inventory, pulse);
        }
    }

    /** A ribbon or Back: flip the page in place. */
    private void turn(Player player, Chapter chapter) {
        open(player, chapter, new Read(player));
    }

    private void ribbons(Read read, Chapter active, Map<Chapter, Look> ribbons, Inventory inventory, Holder holder) {
        for (Map.Entry<Chapter, Look> ribbon : ribbons.entrySet()) {
            Chapter chapter = ribbon.getKey();
            int slot = chapter.ordinal();
            List<String> lore = new ArrayList<>();
            String title;
            switch (ribbon.getValue()) {
                case ACTIVE -> title = "§6" + name(chapter) + " §8· this page";
                case NEW -> title = "§6" + name(chapter) + (read.isNew(chapter) ? " §c✦ new" : " §d✦ glowing");
                case NEXT -> title = "§8" + name(chapter) + " · not written yet";
                default -> title = "§6" + name(chapter);
            }
            if (ribbon.getValue() == Look.NEXT) {
                Tile tile = read.next == null ? null : Tile.bySlot(read.next.slot());
                lore.add("§7The next chapter to open.");
                if (tile != null) {
                    lore.add("");
                    lore.addAll(wrap(read.hint(tile), "§7"));
                }
                inventory.setItem(slot, carrier(title, lore));
                holder.actions.put(slot, () -> locked(read.player, tile == null ? null : read.hint(tile)));
                continue;
            }
            lore.addAll(wrap(blurb(chapter), "§7"));
            if (ribbon.getValue() != Look.ACTIVE) {
                lore.add("");
                lore.add("§eClick to turn here");
            }
            inventory.setItem(slot, carrier(title, lore));
            if (chapter != active) {
                holder.actions.put(slot, () -> turn(read.player, chapter));
            }
        }
    }

    /* =========================================================
     * Pages
     * ========================================================= */

    /** One line of a page: where, what it says, its tooltip icon and what a click does. */
    private record Entry(int row, boolean right, String label, Ink ink, ItemStack icon, Runnable action,
                         boolean strong, boolean pulse) {
    }

    /**
     * Commands a chapter lists when this server has them: label, command line, its journal picture (or a Manager
     * icon by material when {@code icon} is null), tooltip.
     */
    private record Cmd(String label, String command, JournalIcon icon, Material material, String about) {
        Cmd(String label, String command, JournalIcon icon, String about) {
            this(label, command, icon, null, about);
        }

        ItemStack item(String name, List<String> lore) {
            return icon != null ? icon.item(name, lore) : ManagerIcons.apply(GuiItems.named(material, name, lore));
        }
    }

    private static final List<Cmd> ATLAS_LINES = List.of(
            new Cmd("Dungeons", "dhub", JournalIcon.DUNGEONS, "The dungeon hub, from anywhere."),
            new Cmd("Exile", "exile", JournalIcon.EXILE, "Cross to the exile island, or come back."),
            new Cmd("Capital", "capital", JournalIcon.CAPITAL, "Back to the Capital, once you have been there."));
    private static final List<Cmd> ISLE_JOURNALS = List.of(
            new Cmd("Grove", "grove", JournalIcon.GROVE, "Foraging Isle: journal, tour and wayfinder."),
            new Cmd("Mine Isle", "mineisle", JournalIcon.MINE_ISLE, "Mining Isle: journal, compass and crew tour."),
            new Cmd("Origin", "origin", JournalIcon.ORIGIN, "Origin Journal: districts, glowcaps, bells, skyways."));

    private List<Entry> entries(Read read, Chapter chapter) {
        List<Entry> out = new ArrayList<>();
        switch (chapter) {
            case TODAY -> today(read, out);
            case CHARACTER -> {
                tile(read, out, 1, false, Tile.STATS);
                tile(read, out, 2, false, Tile.SKILLS);
                tile(read, out, 3, false, Tile.PETS);
                tile(read, out, 1, true, Tile.STORAGE);
                tile(read, out, 2, true, Tile.LOADOUTS);
            }
            case JOURNEY -> journey(read, out);
            case ATLAS -> {
                tile(read, out, 1, false, Tile.TELEPORTS);
                commands(read, out, false, ATLAS_LINES, 2);
                commands(read, out, true, ISLE_JOURNALS, 1);
            }
            case WORKSHOP -> {
                tile(read, out, 1, false, Tile.CRAFTING);
                tile(read, out, 2, false, Tile.RECIPES);
                tile(read, out, 3, false, Tile.ANVIL);
            }
            case MARKET -> {
                tile(read, out, 1, false, Tile.SHOP);
                tile(read, out, 2, false, Tile.BAZAAR);
                tile(read, out, 3, false, Tile.AUCTION);
            }
            case CODEX -> codex(read, out);
            case COMPANY -> {
                commands(read, out, false, List.of(
                        new Cmd("Party", "party", JournalIcon.PARTY, "Make or manage a party."),
                        new Cmd("Friends", "friend", JournalIcon.FRIENDS, "Friends list, visits and guild invites.")), 1);
                tile(read, out, 1, true, Tile.ISLAND);
                if (read.state(Tile.ISLAND) == State.OPEN) {
                    commands(read, out, true, List.of(new Cmd("Isle menu", "island menu", JournalIcon.ISLE_MENU,
                            "Your island's menu: quarries, friends, land and biome.")), 2);
                }
                tile(read, out, 3, true, Tile.GUILD);
            }
            case SETTINGS -> {
                commands(read, out, false, List.of(
                        new Cmd("Language", "language", JournalIcon.LANGUAGE, "Quest and hint language."),
                        new Cmd("Talk mode", "npctalk", JournalIcon.TALK_MODE, "Bubbles and reply chips, or classic chat."),
                        new Cmd("Report", "report", JournalIcon.REPORT, "Send a bug report to the team."),
                        new Cmd("Checklist", "checklist", JournalIcon.CHECKLIST, "The beta checklist.")), 1);
                commands(read, out, true, List.of(
                        new Cmd("Dev menu", "devmenu", null, Material.COMMAND_BLOCK, "Staff: the Aetherion DEV chest."),
                        new Cmd("Raid ban", "raidban", JournalIcon.RAID_BAN, "Staff: the anti bot-raid menu.")), 1);
            }
        }
        return out;
    }

    private void today(Read read, List<Entry> out) {
        Player player = read.player;
        GuideAdvice.Result advice = GuideAdvice.next(player);
        QuestProgressAccess.JournalQuest focus = read.quests.isEmpty() ? null : read.quests.get(0);
        IslandAccess.IslandGoal island = islandGoal(player);
        boolean strong = read.guide == null;

        // Left page, line 1: the one thing to do now. The quest's step, else the island's goal, else a tip.
        if (focus != null) {
            out.add(questLine(read, 1, focus, advice, strong));
        } else if (island != null) {
            out.add(islandLine(player, 1, island, strong));
        } else {
            out.add(tipLine(player, advice, strong));
        }

        // Line 2: what else wants you. A lesson's glowing tile, a new chapter, turn-ins, rewards, the island.
        if (read.guide != null) {
            Tile tile = read.guide.tile();
            List<String> guide = new ArrayList<>();
            guide.add("§d§lQUEST TIP");
            guide.addAll(wrap(read.guide.tip(), "§f"));
            guide.add("");
            guide.add("§7In §f" + name(tile.chapter) + " › " + tile.label);
            guide.add("§eClick to turn there");
            out.add(new Entry(2, false, tile.label, Ink.INK, glow(icon(tile.icon, "§d" + tile.label, guide, true)),
                    () -> turn(player, tile.chapter), true, true));
        } else if (read.firstNew() != null) {
            Chapter fresh = read.firstNew();
            out.add(new Entry(2, false, name(fresh), Ink.FRESH,
                    JournalIcon.CHAPTER.item("§c✦ New chapter: §6" + name(fresh),
                            withClick(wrap(blurb(fresh), "§7"), "§eClick to turn there")),
                    () -> turn(player, fresh), false, false));
        } else {
            List<String> ready = new ArrayList<>();
            for (QuestProgressAccess.JournalQuest quest : read.quests) {
                if (quest.ready() && quest != focus) {
                    ready.add("§a✔ §f" + quest.title() + (quest.where() == null ? "" : " §8· §7" + quest.where()));
                }
            }
            int claims = read.visible.contains(Chapter.CODEX) ? CodexRewards.claimableTotal(player) : 0;
            if (!ready.isEmpty()) {
                int count = ready.size();
                ready.add(0, "§7Done. Bring these back:");
                ready.add("");
                ready.add("§eClick for your Journey");
                out.add(new Entry(2, false, "Turn in " + count, Ink.AMBER,
                        glow(JournalIcon.QUEST_READY.item("§eReady to turn in", ready)),
                        () -> turn(player, Chapter.JOURNEY), false, false));
            } else if (claims > 0) {
                out.add(new Entry(2, false, "Claim " + claims, Ink.AMBER,
                        glow(JournalIcon.CLAIM.item("§6" + claims + (claims == 1 ? " reward" : " rewards") + " to claim",
                                withClick(wrap("Collection, Bestiary and skill seals have tiers waiting for you.",
                                        "§7"), "§eClick to open the Codex"))),
                        () -> AetherionManagerListener.runTile(player, AetherionManagerGUI.CODEX_SLOT), false, false));
            } else if (focus != null && island != null) {
                out.add(islandLine(player, 2, island, false));
            }
        }

        AetherionItems plugin = AetherionItems.getInstance();
        int level = plugin == null || plugin.getSkills() == null ? 0 : plugin.getSkills().accountLevel(player);
        List<String> profile = new ArrayList<>(List.of(AetherionManagerGUI.profileLines(player)));
        profile.add("");
        profile.add("§eClick §7for your active stats");
        out.add(new Entry(3, false, level > 0 ? "Level " + level : "Stats", Ink.INK,
                icon(Material.EXPERIENCE_BOTTLE, "§b" + player.getName(), profile, true),
                () -> AetherionManagerListener.runTile(player, AetherionManagerGUI.STATS_SLOT), false, false));

        Tile nextTile = read.next == null ? null : Tile.bySlot(read.next.slot());
        if (nextTile != null) {
            List<String> next = new ArrayList<>();
            next.add("§e➜ Opens next");
            next.add("");
            next.addAll(wrap(read.hint(nextTile), "§7"));
            next.add("");
            next.add("§8Lives in " + name(nextTile.chapter) + " › " + nextTile.label);
            out.add(new Entry(4, false, nextTile.label, Ink.AMBER,
                    ManagerIcons.next(GuiItems.named(Material.LIME_DYE, "§e" + nextTile.label + " §8· next",
                            next), nextTile.icon),
                    () -> locked(player, read.hint(nextTile)), false, false));
        }

        // Right page: the essentials that are open, most used first.
        int row = 1;
        for (Tile tile : new Tile[] {Tile.TELEPORTS, Tile.SKILLS, Tile.RECIPES, Tile.STORAGE, Tile.LOADOUTS, Tile.SHOP}) {
            if (row > 4) {
                break;
            }
            if (read.state(tile) == State.OPEN && (read.guide == null || read.guide.tile() != tile)) {
                tile(read, out, row++, true, tile);
            }
        }
    }

    /**
     * A quest as one line: what to do now (the quest bar's step), where that is and how far, and a click that shows
     * the way there. {@code advice} adds the tutorial's own tips (Today only).
     */
    private Entry questLine(Read read, int row, QuestProgressAccess.JournalQuest quest, GuideAdvice.Result advice,
                            boolean strong) {
        Player player = read.player;
        List<String> lore = new ArrayList<>();
        lore.add(quest.ready() ? "§a§l✔ READY TO TURN IN" : quest.tracked() ? "§6§lYOUR QUEST" : "§7§lACTIVE");
        lore.add("§f" + quest.title());
        if (quest.step() != null && !quest.step().isBlank()) {
            lore.addAll(wrap("§7Now: " + quest.step(), "§7"));
        }
        String where = whereLine(player, quest.where(), quest.target());
        if (where != null) {
            lore.add(where);
        }
        List<String> detail = wrap(quest.detail(), "§8");
        if (!detail.isEmpty()) {
            lore.add("");
            lore.addAll(detail.subList(0, Math.min(4, detail.size())));
        }
        if (advice != null && advice.tutorial && advice.tips != null && !advice.tips.isEmpty()) {
            lore.add("");
            for (String tip : advice.tips.subList(0, Math.min(2, advice.tips.size()))) {
                lore.addAll(wrap("§e› §7" + tip, "§7"));
            }
        }
        boolean way = canShowWay(player, quest.target());
        lore.add("");
        lore.add(way ? "§eClick §7to show the way" : "§eClick §7for the next step in chat");
        ItemStack icon = (quest.ready() ? JournalIcon.QUEST_READY : JournalIcon.QUEST)
                .item((quest.ready() ? "§a" : "§6") + quest.title(), lore);
        String name = quest.where() != null ? quest.where() : quest.title();
        Runnable click = way ? () -> showWay(player, quest.target(), name) : () -> command(player, "guide");
        return new Entry(row, false, shortTitle(quest.title()), quest.ready() ? Ink.AMBER : Ink.INK,
                quest.ready() ? glow(icon) : icon, click, strong, false);
    }

    /** The island's next goal (tour step or factory goal) as a line; a click opens the island menu. */
    private Entry islandLine(Player player, int row, IslandAccess.IslandGoal goal, boolean strong) {
        List<String> lore = new ArrayList<>();
        lore.add("§a§lISLAND GOAL");
        lore.addAll(wrap("§f" + goal.title(), "§f"));
        if (goal.how() != null && !goal.how().isBlank()) {
            lore.addAll(wrap(goal.how(), "§7"));
        }
        if (goal.total() > 0) {
            lore.add("");
            lore.add("§7Step §f" + Math.min(goal.total(), goal.done() + 1) + " §7of §f" + goal.total());
        }
        boolean menu = available(player, "island");
        lore.add("");
        lore.add(menu ? "§eClick §7for your island menu" : "§7On the island server: §f/island menu");
        return new Entry(row, false, "Island goal", Ink.INK, JournalIcon.ISLAND_GOAL.item("§aIsland goal", lore),
                menu ? () -> command(player, "island menu") : null, strong, false);
    }

    /** No quest and no island goal: the guide's next tips; every tip in chat on a click. */
    private Entry tipLine(Player player, GuideAdvice.Result advice, boolean strong) {
        List<String> lore = new ArrayList<>();
        lore.add("§6§lNEXT STEP");
        if (advice != null && advice.tips != null) {
            for (String tip : advice.tips.subList(0, Math.min(3, advice.tips.size()))) {
                lore.addAll(wrap("§e› §7" + tip, "§7"));
            }
        }
        lore.add("");
        lore.add("§eClick §7for every tip in chat");
        return new Entry(1, false, "Next step", Ink.INK, JournalIcon.GUIDE.item("§6Next step", lore),
                () -> command(player, "guide"), strong, false);
    }

    private static IslandAccess.IslandGoal islandGoal(Player player) {
        IslandAccess islands = AetherServices.islands();
        if (islands == null) {
            return null;
        }
        try {
            return islands.nextGoal(player);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void journey(Read read, List<Entry> out) {
        Player player = read.player;
        int row = 1;
        for (QuestProgressAccess.JournalQuest quest : read.quests) {
            if (row > 4) {
                break;
            }
            out.add(questLine(read, row, quest, null, row == 1 && quest.tracked() && !quest.ready()));
            row++;
        }
        if (read.quests.isEmpty()) {
            out.add(new Entry(1, false, "No quest", Ink.FADED,
                    JournalIcon.QUEST_NONE.item("§7No quest right now",
                            List.of("§7Talk to the people around you.", "§7Some of them have work.")),
                    null, false, false));
        }
        // The talk log answers /aetalk from a chat listener (no command-map entry), so it goes out as chat.
        boolean talkLog = Bukkit.getPluginManager().isPluginEnabled("AetherionQuests");
        if (talkLog) {
            out.add(new Entry(1, true, "Talk log", Ink.INK,
                    JournalIcon.TALK_LOG.item("§fTalk log", withClick(wrap("Your recent conversations, as a book.",
                            "§7"), "§eClick §8· /aetalk log")),
                    () -> chat(player, "/aetalk log"), false, false));
        }
        commands(read, out, true, List.of(
                new Cmd("Guide", "guide", JournalIcon.GUIDE, "Next steps, and where to find a recipe.")), talkLog ? 2 : 1);
    }

    private void codex(Read read, List<Entry> out) {
        Player player = read.player;
        ProgressionService progress = read.progress;
        tile(read, out, 1, false, Tile.CODEX);
        book(out, 2, "Collection", Material.IRON_PICKAXE, null, progress == null || progress.collection(player),
                progress == null ? null : progress.collectionHint(), "Every block you have broken, in tiers.",
                () -> CodexMenus.openCollection(player, null), player);
        book(out, 3, "Bestiary", Material.BONE, null, progress == null || progress.bestiary(player),
                progress == null ? null : progress.bestiaryHint(), "Every mob you have beaten, in tiers.",
                () -> CodexMenus.openBestiary(player, null), player);
        book(out, 4, "Bosses", null, JournalIcon.BOSSES,
                de.aetherion.items.codex.JournalHubGUI.available(player),
                progress == null ? null : progress.journalHint(), "The Boss Journal and the Chronicle.",
                () -> CodexMenus.openJournal(player), player);
        if (AetherionManagerGUI.codexOpen(player, progress)) {
            int claims = CodexRewards.claimableTotal(player);
            List<String> lore = new ArrayList<>(wrap("What the Codex pays out, and what is still to claim.", "§7"));
            if (claims > 0) {
                lore.add("");
                lore.add("§6✦ " + claims + (claims == 1 ? " reward" : " rewards") + " to claim");
            }
            ItemStack icon = JournalIcon.MILESTONES.item("§6Milestones", withClick(lore, "§eClick to open"));
            out.add(new Entry(1, true, "Milestones", claims > 0 ? Ink.AMBER : Ink.INK, claims > 0 ? glow(icon) : icon,
                    () -> CodexMenus.openMilestones(player), false, false));
        }
        commands(read, out, true, List.of(
                new Cmd("Aetherlex", "aetherlex", JournalIcon.AETHERLEX, "The pet encyclopedia.")), 2);
    }

    /** One Codex book: open, or greyed with its unlock hint (a Manager icon by material, else its own picture). */
    private void book(List<Entry> out, int row, String label, Material material, JournalIcon picture, boolean open,
                      String hint, String about, Runnable action, Player player) {
        if (open) {
            List<String> lore = withClick(wrap(about, "§7"), "§eClick to open");
            out.add(new Entry(row, false, label, Ink.INK,
                    picture != null ? picture.item("§d" + label, lore) : icon(material, "§d" + label, lore, true),
                    action, false, false));
            return;
        }
        List<String> lore = new ArrayList<>();
        lore.add("§7Not written yet");
        lore.add("");
        lore.addAll(wrap(hint == null ? "§7Not open yet." : hint, "§7"));
        out.add(new Entry(row, false, label, Ink.FADED,
                picture != null ? picture.locked("§8" + label, lore)
                        : ManagerIcons.locked(icon(Material.GRAY_DYE, "§8" + label, lore, true), material),
                () -> locked(player, hint), false, false));
    }

    /** A Manager tile as a line: open, opens next, locked or far off. */
    private void tile(Read read, List<Entry> out, int row, boolean right, Tile tile) {
        Player player = read.player;
        State state = read.state(tile);
        if (state == State.HIDDEN) {
            return;
        }
        boolean guided = read.guide != null && read.guide.tile() == tile;
        // Shut tiles knock (the Manager's hint follows in chat); open ones do what their Manager tile did.
        Runnable click = state == State.OPEN
                ? () -> AetherionManagerListener.runTile(player, tile.slot)
                : () -> {
                    locked(player, null);
                    AetherionManagerListener.runTile(player, tile.slot);
                };
        switch (state) {
            case OPEN -> {
                List<String> lore = new ArrayList<>();
                if (guided) {
                    lore.add("§d§lQUEST TIP");
                    lore.addAll(wrap(read.guide.tip(), "§f"));
                    lore.add("");
                }
                lore.addAll(wrap(tile.about, "§7"));
                if (tile == Tile.STATS) {
                    lore.add("");
                    lore.addAll(List.of(AetherionManagerGUI.profileLines(player)));
                }
                lore.add("");
                lore.add("§eClick to open");
                ItemStack icon = icon(tile.icon, (guided ? "§d" : "§f") + tile.label, lore, true);
                out.add(new Entry(row, right, tile.label, Ink.INK, guided ? glow(icon) : icon, click, guided, guided));
            }
            case NEXT -> {
                List<String> lore = new ArrayList<>();
                lore.add("§e➜ Opens next");
                lore.add("");
                lore.addAll(wrap(read.hint(tile), "§7"));
                out.add(new Entry(row, right, tile.label, Ink.AMBER,
                        ManagerIcons.next(GuiItems.named(Material.LIME_DYE, "§e" + tile.label + " §8· next", lore),
                                tile.icon),
                        click, false, false));
            }
            case LATER -> out.add(new Entry(row, right, tile.label, Ink.FADED,
                    ManagerIcons.later(GuiItems.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                            "§8" + tile.label + " · later", wrap(read.hint(tile), "§7")), tile.icon),
                    click, false, false));
            default -> {
                List<String> lore = new ArrayList<>();
                lore.add("§7Locked");
                lore.add("");
                lore.addAll(wrap(read.hint(tile), "§7"));
                out.add(new Entry(row, right, tile.label, Ink.FADED,
                        ManagerIcons.locked(GuiItems.named(Material.GRAY_DYE, "§8" + tile.label, lore), tile.icon),
                        click, false, false));
            }
        }
    }

    /** Command lines this server has, from {@code row} down; staff lines only for staff. */
    private void commands(Read read, List<Entry> out, boolean right, List<Cmd> cmds, int row) {
        Player player = read.player;
        for (Cmd cmd : cmds) {
            if (row > 4) {
                return;
            }
            if (!available(player, cmd.command())) {
                continue;
            }
            out.add(new Entry(row++, right, cmd.label(), Ink.INK,
                    cmd.item("§f" + cmd.label(), withClick(wrap(cmd.about(), "§7"), "§eClick §8· /" + cmd.command())),
                    () -> command(player, cmd.command()), false, false));
        }
    }

    private boolean anyCommand(List<Cmd> cmds) {
        for (Cmd cmd : cmds) {
            if (Bukkit.getCommandMap().getCommand(cmd.command().split(" ", 2)[0]) != null) {
                return true;
            }
        }
        return false;
    }

    /** True when this server knows {@code line}'s command and the player may run it. */
    private static boolean available(Player player, String line) {
        Command command = Bukkit.getCommandMap().getCommand(line.split(" ", 2)[0]);
        if (command == null) {
            return false;
        }
        if (command instanceof PluginCommand plugin && !plugin.getPlugin().isEnabled()) {
            return false;
        }
        return command.testPermissionSilent(player);
    }

    /* =========================================================
     * Clicks
     * ========================================================= */

    void click(Player player, Holder holder, int rawSlot) {
        if (player == null || holder == null || rawSlot < 0 || rawSlot >= 54) {
            return;
        }
        Runnable action = holder.actions.get(rawSlot);
        if (action != null) {
            action.run();
        }
    }

    /** Closes the book and runs {@code line} next tick, so chat answers are not hidden behind the page. */
    private static void command(Player player, String line) {
        player.closeInventory();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.performCommand(line);
            }
        });
    }

    /** Like {@link #command}, for commands only a chat listener answers. */
    private static void chat(Player player, String line) {
        player.closeInventory();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.chat(line);
            }
        });
    }

    private static void locked(Player player, String hint) {
        JournalSounds.locked(player);
        if (hint != null && !hint.isBlank()) {
            player.sendMessage(hint);
        }
    }

    /* =========================================================
     * Showing the way
     * ========================================================= */

    /** Beyond this the light stops short; the compass carries the rest. */
    private static final double WAY_REACH = 24.0;

    /** True when {@code target} is in the player's world and further than a step away. */
    private static boolean canShowWay(Player player, Location target) {
        return target != null && target.getWorld() != null && target.getWorld().equals(player.getWorld())
                && target.distanceSquared(player.getLocation()) > 9.0;
    }

    /**
     * Closes the book and points the way: a comet of light runs out from the player's eyes toward {@code target}
     * (only they see it), with a chime and the distance on the action bar. Close targets get a ring where they stand.
     */
    private static void showWay(Player player, Location target, String name) {
        player.closeInventory();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || !canShowWay(player, target)) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector to = target.clone().add(0, 1.2, 0).toVector().subtract(eye.toVector());
        double length = to.length();
        Vector dir = to.clone().normalize();
        double reach = Math.min(length - 0.8, WAY_REACH);
        Location feet = player.getLocation();
        double dx = target.getX() - feet.getX();
        double dz = target.getZ() - feet.getZ();
        long metres = Math.round(Math.sqrt(dx * dx + dz * dz));
        String plain = name == null ? "" : name.replaceAll("§.", "").trim();
        JournalSounds.way(player);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "➜ " + (plain.isEmpty() ? "" : plain + " · ") + metres + " m " + compassWord(dx, dz),
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        Particle.DustOptions tail = new Particle.DustOptions(Color.fromRGB(255, 206, 96), 0.9f);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || !eye.getWorld().equals(player.getWorld()) || tick > 20) {
                    cancel();
                    return;
                }
                double head = 1.6 + tick * 1.2;
                for (int k = 0; k < 4; k++) {
                    double d = head - k * 0.5;
                    if (d < 1.2 || d > reach) {
                        continue;
                    }
                    Location at = eye.clone().add(dir.clone().multiply(d)).subtract(0, 0.3, 0);
                    if (k == 0) {
                        player.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
                    } else {
                        player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, tail);
                    }
                }
                if (head - 1.5 > reach && length <= WAY_REACH + 1.0) {
                    // the comet arrived: a quiet ring where the target stands and a chime from there, then done
                    JournalSounds.arrive(player, target);
                    for (int i = 0; i < 12; i++) {
                        double angle = Math.PI * 2 * i / 12;
                        player.spawnParticle(Particle.END_ROD, target.clone().add(Math.cos(angle) * 0.8, 0.2,
                                Math.sin(angle) * 0.8), 1, 0, 0, 0, 0);
                    }
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    /** "Where: Corin · 42 m north-east" for a tooltip, or null when the quest names no place. */
    private static String whereLine(Player player, String where, Location target) {
        String name = where == null || where.isBlank() ? null : where;
        if (target == null || target.getWorld() == null) {
            return name == null ? null : "§7Where: §f" + name;
        }
        if (!target.getWorld().equals(player.getWorld())) {
            return "§7Where: §f" + (name == null ? "elsewhere" : name) + " §8· §7another area";
        }
        Location feet = player.getLocation();
        double dx = target.getX() - feet.getX();
        double dz = target.getZ() - feet.getZ();
        long metres = Math.round(Math.sqrt(dx * dx + dz * dz));
        String lead = "§7Where: " + (name == null ? "" : "§f" + name + " §8· ");
        return metres < 4 ? lead + "§aright here" : lead + "§e" + metres + " m §7" + compassWord(dx, dz);
    }

    /** One of eight compass words for a horizontal offset (north is -Z). */
    static String compassWord(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int octant = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return switch (octant) {
            case 0 -> "north";
            case 1 -> "north-east";
            case 2 -> "east";
            case 3 -> "south-east";
            case 4 -> "south";
            case 5 -> "south-west";
            case 6 -> "west";
            default -> "north-west";
        };
    }

    /**
     * Each page fills from the top in the order its lines were asked for, so a line this server or player does
     * not have leaves no gap (Atlas without Teleports, Today without a second line). Never more than four a page.
     */
    private static List<Entry> pack(List<Entry> entries) {
        List<Entry> out = new ArrayList<>(entries.size());
        for (boolean right : new boolean[] {false, true}) {
            List<Entry> side = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.right() == right) {
                    side.add(entry);
                }
            }
            side.sort(Comparator.comparingInt(Entry::row));
            int row = 1;
            for (Entry entry : side) {
                if (row > 4) {
                    break;
                }
                out.add(new Entry(row++, right, entry.label(), entry.ink(), entry.icon(), entry.action(),
                        entry.strong(), entry.pulse()));
            }
        }
        return out;
    }

    /* =========================================================
     * The glow trail
     * ========================================================= */

    private void startPulse(Player player, Holder holder, Inventory inventory, int slot) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        final boolean[] on = {true};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                stopPulse(player);
                return;
            }
            ItemStack item = inventory.getItem(slot);
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta == null) {
                stopPulse(player);
                return;
            }
            on[0] = !on[0];
            meta.setEnchantmentGlintOverride(on[0]);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }, 8L, 8L);
        BukkitTask old = pulses.put(player.getUniqueId(), task);
        if (old != null) {
            old.cancel();
        }
    }

    void stopPulse(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = pulses.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    void forget(Player player) {
        if (player != null) {
            stopPulse(player);
            bookmarks.remove(player.getUniqueId());
        }
    }

    /* =========================================================
     * The hotbar book
     * ========================================================= */

    /** Cover by how far the player is: worn, then bound (skills and the market open), then Aether at level 75. */
    static String coverName(Player player) {
        return switch (coverStage(player)) {
            case 2 -> "§d§lAether Journal";
            case 1 -> "§6§lBound Journal";
            default -> "§6§lWorn Journal";
        };
    }

    /** CustomModelData of the hotbar book's cover: {@code COVER_CMD + coverStage} on BOOK (tools/gen_journal_cover.py). */
    static final int COVER_CMD = 7900;

    /** 0 Worn (new), 1 Bound (Skills and the Bazaar open), 2 Aether (account level 75). */
    static int coverStage(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        int level = plugin == null || plugin.getSkills() == null || player == null
                ? 0 : plugin.getSkills().accountLevel(player);
        if (level >= 75) {
            return 2;
        }
        ProgressionService progress = plugin == null ? null : plugin.progress();
        if (progress != null && player != null && progress.skills(player) && progress.bazaar(player)) {
            return 1;
        }
        return 0;
    }

    /** The hotbar book's quest tip, or null: the same four lessons the glow trail follows. */
    static String itemTip(Player player) {
        if (player == null) {
            return null;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ProgressionService progress = plugin == null ? null : plugin.progress();
        if ((progress == null || progress.skills(player))
                && QuestProgressHook.isQuestActive(player, AetherionManagerGUI.SKILLS_QUEST_ID)) {
            return "§d✦ Character › Skills is glowing";
        }
        if ((progress == null || progress.pets(player)) && QuestProgressHook.shouldGuidePetsEquip(player)) {
            return "§d✦ Character › Pets is glowing";
        }
        if ((progress == null || progress.recipeBook(player))
                && QuestProgressHook.isQuestActive(player, AetherionManagerGUI.CRAFT_QUEST_ID)) {
            return "§e✦ Workshop › Recipes is glowing";
        }
        if ((progress == null || progress.spawns(player)) && AetherionManagerGUI.hasHomesteadMarker(player)) {
            return "§6✦ Atlas › Teleports: set your camp";
        }
        return null;
    }

    /* =========================================================
     * Items
     * ========================================================= */

    /** An icon with its tooltip; Manager icons where the material has one. */
    private static ItemStack icon(Material material, String name, List<String> lore, boolean managerIcon) {
        ItemStack item = GuiItems.named(material, name, lore);
        return managerIcon ? ManagerIcons.apply(item) : item;
    }

    private static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The invisible slot over a line's text (or a ribbon): no picture, the same tooltip as its icon. */
    private static ItemStack carrier(ItemStack icon) {
        ItemMeta source = icon == null ? null : icon.getItemMeta();
        return carrier(source == null ? " " : source.getDisplayName(),
                source == null || source.getLore() == null ? List.of() : source.getLore());
    }

    private static ItemStack carrier(String name, List<String> lore) {
        ItemStack item = GuiIcons.spacer();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(false);
            meta.setDisplayName(name);
            meta.setLore(lore == null || lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<String> withClick(List<String> lines, String click) {
        List<String> out = new ArrayList<>(lines);
        out.add("");
        out.add(click);
        return out;
    }

    /**
     * A quest title as one left-page line: "Lesson: Steel" reads "Steel", a title too wide keeps its last words
     * ("Your Loadout" reads "Loadout"). The tooltip keeps the whole title.
     */
    static String shortTitle(String title) {
        if (title == null) {
            return "";
        }
        String plain = title.replaceAll("§.", "").trim();
        if (plain.startsWith("Lesson: ")) {
            plain = plain.substring("Lesson: ".length()).trim();
        }
        if (JournalPage.fits(plain, false)) {
            return plain;
        }
        String[] words = plain.split("\\s+");
        for (int from = 1; from < words.length; from++) {
            String tail = String.join(" ", java.util.Arrays.copyOfRange(words, from, words.length));
            if (JournalPage.fits(tail, false)) {
                return tail;
            }
        }
        return plain;
    }

    /** Word-wraps {@code text} for a tooltip, each line in {@code colour} (legacy codes in the text survive). */
    static List<String> wrap(String text, String colour) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String lead = text.startsWith("§") ? "" : colour;
        StringBuilder line = new StringBuilder();
        int visible = 0;
        for (String word : text.trim().split("\\s+")) {
            int length = word.replaceAll("§.", "").length();
            if (visible > 0 && visible + 1 + length > TOOLTIP_WIDTH) {
                out.add(lead + line);
                lead = colour + lastColours(line.toString());
                line.setLength(0);
                visible = 0;
            }
            if (visible > 0) {
                line.append(' ');
                visible++;
            }
            line.append(word);
            visible += length;
        }
        if (!line.isEmpty()) {
            out.add(lead + line);
        }
        return out;
    }

    /** The colour codes still in force at the end of {@code text}, so a wrapped line keeps its colour. */
    private static String lastColours(String text) {
        int at = text.lastIndexOf('§');
        while (at >= 0 && at + 1 < text.length()) {
            char code = Character.toLowerCase(text.charAt(at + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
                return "§" + code;
            }
            at = text.lastIndexOf('§', at - 1);
        }
        return "";
    }

    public static final class Holder implements InventoryHolder {
        private final Chapter chapter;
        private final Map<Integer, Runnable> actions = new HashMap<>();

        Holder(Chapter chapter) {
            this.chapter = chapter;
        }

        public Chapter chapter() {
            return chapter;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
