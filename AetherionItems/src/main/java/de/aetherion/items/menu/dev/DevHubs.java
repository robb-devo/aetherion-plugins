package de.aetherion.items.menu.dev;

import de.aetherion.items.menu.dev.DevMenu.Page;
import de.aetherion.items.menu.dev.DevTheme.Cat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * DEV // AETHERION hubs: the dashboard, the six category hubs, and the power pages
 * (search results, loadouts, system status, confirm modal). Every tile routes to an
 * action that {@link DevMenu} already handles — hubs add structure, never new side effects.
 */
final class DevHubs {

    static final int SEARCH_BATCH_LIMIT = 36;
    private static final int[] FAVORITE_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int[] RECENT_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    /** Plugins the DEV menu reaches into, and what for (System Status + offline stubs). */
    private static final String[][] PLUGINS = {
            {"AetherionCore", "Service bridges for every other tile"},
            {"BossEngine", "Boss anchors · boss cores · arena spawns"},
            {"AetherionHub", "Spawn anchors · camps · wipe hub unlocks"},
            {"AetherionQuests", "NPC anchors · chests · /npc studio · quest resets"},
            {"AetherMobs", "Pets · spheres · treats · Aetherlex"},
            {"AetherionDungeons", "Dungeon runs · Dungeon Keeper"},
            {"AetherionFarming", "Farming Island hub · farm tools · portals"},
            {"AetherionFishing", "Fishing Island hub · the line · shoal"},
            {"AetherionForaging", "Isle weather · Miss Canopy · grove"},
            {"AetherionMining", "Mining Island hub · Deep Forge · Amethyst Mine"},
            {"AetherionStressBots", "Testbots"},
            {"FancyNpcs", "NPC studio backing"},
            {"LuckPerms", "Rank groups"},
    };

    private DevHubs() {
    }

    private static ItemStack tile(Material material, String name, String action, String... lore) {
        return DevItems.button(material, name, action, lore);
    }

    private static boolean online(String plugin) {
        Plugin found = Bukkit.getPluginManager().getPlugin(plugin);
        return found != null && found.isEnabled();
    }

    private static String dot(String plugin) {
        return online(plugin) ? "§a● §7" + plugin : "§c● §7" + plugin + " §coffline §8— tile shows a stub";
    }

    private static String tps() {
        double tps = Bukkit.getTPS()[0];
        String color = tps >= 19.0 ? "§a" : tps >= 16.0 ? "§e" : "§c";
        return color + String.format("%.1f", Math.min(20.0, tps));
    }

    private static List<String> offlinePlugins() {
        List<String> offline = new ArrayList<>();
        for (String[] plugin : PLUGINS) {
            if (!online(plugin[0])) {
                offline.add(plugin[0]);
            }
        }
        return offline;
    }

    // ------------------------------------------------------------------ dashboard

    static void drawDashboard(DevMenu menu, Inventory inventory, Player player) {
        List<String> favorites = DevPrefs.favorites(player);
        List<String> recents = DevPrefs.recents(player);
        List<String> offline = offlinePlugins();
        List<String> header = new ArrayList<>();
        header.add("§7Live MMO command center.");
        header.add("§7TPS " + tps() + " §8· §7MSPT §f" + String.format("%.1f", Bukkit.getAverageTickTime())
                + " §8· §7Online §f" + Bukkit.getOnlinePlayers().size() + "§7/§f" + Bukkit.getMaxPlayers());
        header.add("§7Plugins §a" + (PLUGINS.length - offline.size()) + "§8/§f" + PLUGINS.length + " online"
                + (offline.isEmpty() ? " §a✔" : ""));
        if (!offline.isEmpty()) {
            header.add("§8Offline: §c" + String.join("§8, §c", offline));
        }
        header.add("");
        header.add("§7Hi §f" + player.getName() + "§7 — §e" + favorites.size() + " ★ §7pinned · §b"
                + recents.size() + " ⟲ §7recent");
        header.add("");
        header.addAll(DevTheme.controls());
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.NETHER_STAR,
                "§5§l✦ §d§lDEV §8§l// §5§lAETHERION §d§l✦", "noop", header.toArray(String[]::new))));
        inventory.setItem(DevTheme.PREV, tile(Material.KNOWLEDGE_BOOK, "§e✎ How this works", "noop",
                "§7Six categories. Everything ≤ 2 clicks.",
                "§7Your favorites and recents: 1 click.",
                "",
                "§fClick §8· §7give / open",
                "§fShift-click §8· §7a full stack of anything stackable",
                "§fF §7(over a tile) §8· §7★ pin / unpin to this dashboard",
                "§fSearch §8· §7bottom right, or §f/devmenu <words>",
                "",
                "§cDANGER §7asks twice. Special ranks are never wiped."));
        inventory.setItem(DevTheme.NEXT, tile(Material.COMPARATOR, "§9⚙ System Status", "page:STATUS",
                "§7Plugin health · TPS · search index.",
                offline.isEmpty() ? "§a✔ everything online" : "§c" + offline.size() + " plugin(s) offline"));

        // Row 1 — six categories around search.
        inventory.setItem(10, categoryTile(Cat.CONTENT, "page:CAT_CONTENT",
                "Resources · Tools · Boosters · Lab", "Charms · Blueprints · Skill Gear", "Pets · Spheres · Millstone · Spirits"));
        inventory.setItem(11, categoryTile(Cat.COMBAT, "page:CAT_COMBAT",
                "Weapons · Combat & Special Sets", "Flagships · Loadouts · Test Arena", "Boss Cores · Dungeons"));
        inventory.setItem(12, categoryTile(Cat.WORLDS, "page:CAT_WORLDS",
                "Farming & Fishing Island hubs", "Spawn anchors · weather · portals", "World builder: NPCs · areas · props"));
        inventory.setItem(13, DevItems.glow(tile(Material.SPYGLASS, "§f§lSEARCH", "search",
                "§7Type a word, get every match:",
                "§7items, sets, pages, NPCs, tools.",
                "",
                "§8Try: §fkatana §8· §fdawnbearer §8· §fanchor §8· §fweather",
                "§e▶ Click, then type in chat")));
        inventory.setItem(14, categoryTile(Cat.PROGRESS, "page:CAT_PROGRESS",
                "Max skills · tutorial skip", "Unlock recipes · blueprints · camps", "Aetherlex · isle progression"));
        inventory.setItem(15, categoryTile(Cat.ADMIN, "page:CAT_ADMIN",
                "Ranks · shards · testbots", "NPC studio · /flight · Content Kit", "System status"));
        inventory.setItem(16, categoryTile(Cat.DANGER, "page:CAT_DANGER",
                "Full player wipe (arm + confirm)", "Self resets · isle profile wipes", "Everything asks twice"));

        // Row 2 — one-click launch pad.
        inventory.setItem(19, tile(Material.ENDER_PEARL, "§d⚡ Enter Test Arena", "test:goto",
                "§7Void sandbox · spawns the pad if needed.",
                "§8Arena controls: Combat Lab › Test Arena"));
        inventory.setItem(20, tile(Material.HAY_BLOCK, "§a§lFarming Island", "page:FARM_ISLE",
                "§7Eldervale hub: teleports · cast ·",
                "§7props · events · skills · resets.", dot("AetherionFarming")));
        inventory.setItem(21, tile(Material.FISHING_ROD, "§b§lFishing Island", "page:FISH_ISLE",
                "§7Eldervale lake: teleports · cast ·",
                "§7shoal · lake events · the line · Log.", dot("AetherionFishing")));
        inventory.setItem(22, tile(Material.ARMOR_STAND, "§6⚔ Loadouts", "page:LOADOUTS",
                "§7Whole builds in one click:",
                "§7Endgame · Hollow Sun · T1/T2 racks · Gatherer T5…"));
        inventory.setItem(23, tile(Material.NETHER_STAR, "§d✦ Flagships & Test Gear", "page:TEST_GEAR",
                "§7Terminus + every sandbox prototype."));
        inventory.setItem(24, tile(Material.NETHERITE_HELMET, "§5Special Sets", "page:SETS",
                "§7Dungeon · Webweave · Aetherion · Worldhide ·", "§7Hollow Sun · Dawnbearer · God kits."));
        inventory.setItem(25, tile(Material.DIAMOND_SWORD, "§cWeapons", "page:WEAPONS",
                "§7Starter → T2 uniques → specials."));

        // Row 3 — ★ favorites · Row 4 — ⟲ recent.
        inventory.setItem(27, DevTheme.pane(Material.YELLOW_STAINED_GLASS_PANE, "§e§l★ FAVORITES",
                "§7Press §eF §7over any tile or item", "§7anywhere in DEV to pin it here.", "§8F again unpins."));
        inventory.setItem(35, DevTheme.pane(Material.YELLOW_STAINED_GLASS_PANE, "§e§l★",
                "§8" + favorites.size() + "/" + DevPrefs.SLOTS + " pinned"));
        inventory.setItem(36, tile(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§l⟲ RECENT", "recents:clear",
                "§7Your last gives & teleports.", "§8Click to clear the row."));
        inventory.setItem(44, DevTheme.pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§l⟲",
                "§8last " + recents.size()));
        List<String> all = new ArrayList<>(favorites);
        all.addAll(recents);
        DevIndex.ensureIcons(menu, player, all);
        drawRow(inventory, FAVORITE_SLOTS, favorites, "fav",
                List.of("", "§e★ Pinned §8· §7F to unpin"),
                "§8☆ empty", "§7Press §eF §7over anything to pin it.");
        drawRow(inventory, RECENT_SLOTS, recents, "recent",
                List.of("", "§b⟲ Recent §8· §7click to repeat", "§8F to pin"),
                "§8⟲ nothing yet", "§7Grab something — it shows up here.");

        // Bottom row extras (45 / 49 / 53 are chrome).
        inventory.setItem(47, tile(Material.FEATHER, "§aToggle /flight", "flight-toggle",
                "§7Same as EssentialsX fly."));
        inventory.setItem(51, tile(Material.WRITABLE_BOOK, "§b§lNPC Studio", "page:NPC_EDITOR",
                "§7/npc · create · nearby · wand · list · /aethernpc"));
    }

    private static ItemStack categoryTile(Cat cat, String action, String... lines) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + cat.tagline);
        lore.add("");
        for (String line : lines) {
            lore.add("§8▪ §f" + line);
        }
        lore.add("");
        lore.add(cat.accent + "▶ Open " + cat.pretty());
        return tile(cat.icon, cat.accent + "§l" + cat.label, action, lore.toArray(String[]::new));
    }

    private static void drawRow(Inventory inventory, int[] slots, List<String> actions, String via,
                                List<String> extra, String emptyName, String emptyHint) {
        for (int i = 0; i < slots.length; i++) {
            if (i >= actions.size()) {
                inventory.setItem(slots[i], DevTheme.pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, emptyName,
                        i == actions.size() ? new String[]{emptyHint} : new String[0]));
                continue;
            }
            String action = actions.get(i);
            ItemStack icon = DevIndex.icon(action);
            if (icon == null) {
                icon = DevItems.stub(Material.PAPER, "§7" + action, action,
                        "§8Not available right now —", "§8plugin offline or item renamed.");
            }
            inventory.setItem(slots[i], DevItems.decorate(icon, via, extra));
        }
    }

    // ------------------------------------------------------------------ category hubs

    static void drawCategory(DevMenu menu, Inventory inventory, Player player, Cat cat) {
        List<String> header = new ArrayList<>();
        header.add("§7" + cat.tagline);
        header.add("");
        header.addAll(DevTheme.controls());
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(cat.icon, cat.accent + "§l" + cat.label, "noop",
                header.toArray(String[]::new))));
        switch (cat) {
            case CONTENT -> drawContent(menu, inventory);
            case COMBAT -> drawCombat(inventory);
            case WORLDS -> drawWorlds(inventory);
            case PROGRESS -> drawProgress(inventory);
            case ADMIN -> drawAdmin(inventory);
            case DANGER -> drawDanger(inventory);
            default -> {
            }
        }
    }

    private static void drawContent(DevMenu menu, Inventory inventory) {
        inventory.setItem(10, tile(Material.IRON_BLOCK, "§fResources", "page:RESOURCES",
                "§7Compressed · compacted · quarries · heartwood."));
        inventory.setItem(11, tile(Material.CRAFTING_TABLE, "§eTools & Storage", "page:TOOLS",
                "§7Starter + progression tools, storage,", "§7sacks, recipe book, anchors, NPC remover."));
        inventory.setItem(12, tile(Material.EMERALD, "§2Boosters", "page:BOOSTERS", "§7All booster types."));
        inventory.setItem(13, tile(Material.ANVIL, "§dBooster Lab", "page:BOOSTER_LAB",
                "§7Drop gear in the well, click boosters.", "§8Shift-click applies ×5."));
        inventory.setItem(14, tile(Material.MAGMA_CREAM, "§dCharms", "page:CHARMS", "§7Off-hand accessories · every tier."));
        inventory.setItem(15, tile(Material.FILLED_MAP, "§bBlueprints", "page:BLUEPRINTS",
                "§7Stones · blueprints · finished tools ·", "§7Blueprint Forge · Ore Troll spawn."));
        inventory.setItem(16, tile(Material.GOLDEN_HOE, "§6Skill Gear", "page:SKILL_GEAR",
                "§7Mining · Farming · Foraging ·", "§7Fishing · Catcher sets I – V."));
        inventory.setItem(19, tile(Material.LEAD, "§dPets", "page:PETS",
                "§7Spawn (left) / collect (right) every pet.", "§7Fill Aetherlex.", dot("AetherMobs")));
        inventory.setItem(20, tile(Material.ENDER_EYE, "§dCatch Spheres", "page:SPHERES",
                "§7Common → Beta.", dot("AetherMobs")));
        inventory.setItem(21, tile(Material.GRINDSTONE, "§6Millstone & Pantry", "page:MILLSTONE",
                "§7Mill anchors · crop tiers · cane tools ·", "§7district signs · treats · pantry NPCs."));
        inventory.setItem(22, tile(Material.POTION, "§cSpirit Vials", "page:BORDERLANDS_SPIRITS",
                "§7T1 Borderlands altar vials +", "§7T2 Crypt / Colosseum vials."));
        // Row 3 — quick grabs (the real items; also on their shelves).
        var custom = menu.customItem();
        inventory.setItem(28, DevItems.tag(custom.createRecipeBook().clone(), "item:recipe_book"));
        inventory.setItem(29, DevItems.tag(de.aetherion.items.storage.SackItems
                .create(de.aetherion.items.storage.SackType.RESOURCE).clone(), "item:resource_sack"));
        inventory.setItem(30, DevItems.tag(de.aetherion.items.storage.SackItems
                .create(de.aetherion.items.storage.SackType.BOOSTER).clone(), "item:booster_sack"));
        inventory.setItem(31, DevItems.tag(de.aetherion.items.shop.AetherBloodVial.create().clone(),
                "item:" + de.aetherion.items.shop.AetherBloodVial.ID));
        inventory.setItem(32, DevItems.tag(custom.createDragonAscensionVial().clone(), "item:dragon_ascension_vial"));
        inventory.setItem(34, tile(Material.KNOWLEDGE_BOOK, "§aUnlock all recipes", "unlock:recipes-all",
                "§7Also in Progress."));
    }

    private static void drawCombat(Inventory inventory) {
        inventory.setItem(10, tile(Material.DIAMOND_SWORD, "§cWeapons", "page:WEAPONS",
                "§7Starter · Bows · Progression · T1 · T2 ·", "§7Dungeon · Special · Test Extras."));
        inventory.setItem(11, tile(Material.DIAMOND_CHESTPLATE, "§bCombat Sets", "page:COMBAT", "§7I – V + swords."));
        inventory.setItem(12, tile(Material.NETHERITE_HELMET, "§5Special Sets", "page:SETS",
                "§7Rotten · Bone · Webweave · Ironhide · Healer ·", "§7Dungeon relics · Aetherion · Worldhide ·",
                "§7Hollow Sun · Dawnbearer · God kits."));
        inventory.setItem(13, tile(Material.NETHER_STAR, "§d✦ Flagships & Test Gear", "page:TEST_GEAR",
                "§7Terminus + every sandbox prototype:", "§7Cataclysm · Vesper Bell · Deepsong · Portal Gun…"));
        inventory.setItem(14, tile(Material.ARMOR_STAND, "§6⚔ Loadouts", "page:LOADOUTS",
                "§7Batch kits — a whole build per click."));
        inventory.setItem(15, tile(Material.END_CRYSTAL, "§5Boss Cores", "page:BOSS_CORES",
                "§7Summon items for tests.", dot("BossEngine")));
        inventory.setItem(16, tile(Material.DEEPSLATE_BRICKS, "§5Dungeons", "page:DUNGEONS",
                "§7Start a run or skip to the boss.", dot("AetherionDungeons")));
        inventory.setItem(19, tile(Material.STRUCTURE_BLOCK, "§d✦ Test Arena", "page:TEST_ARENA",
                "§7Void sandbox · controls · spawn any boss."));
        inventory.setItem(20, tile(Material.ENDER_PEARL, "§d⚡ Enter Test Arena", "test:goto",
                "§7Straight to the pad."));
        inventory.setItem(22, tile(Material.WITHER_SKELETON_SKULL, "§5T1 Uniques", "page:WEAPONS_T1",
                "§7World boss toys."));
        inventory.setItem(23, tile(Material.END_CRYSTAL, "§dT2 Uniques", "page:WEAPONS_T2",
                "§7Seraphine · Gravwell · cores · ledger."));
        inventory.setItem(24, tile(Material.CHERRY_LEAVES, "§dSpecial Weapons", "page:WEAPONS_SPECIAL",
                "§7Boss-drop showpieces · Ashen Katana."));
        inventory.setItem(25, tile(Material.HEART_OF_THE_SEA, "§3Dungeon Relics", "page:WEAPONS_DUNGEON",
                "§7Cores and relic weapons."));
    }

    private static void drawWorlds(Inventory inventory) {
        inventory.setItem(10, tile(Material.HAY_BLOCK, "§a§lFarming Island", "page:FARM_ISLE",
                "§7Teleports · cast · props · events ·", "§7progression · legacy portals.", dot("AetherionFarming")));
        inventory.setItem(11, tile(Material.FISHING_ROD, "§b§lFishing Island", "page:FISH_ISLE",
                "§7Teleports · cast · events & the line ·", "§7progression · landing.", dot("AetherionFishing")));
        inventory.setItem(12, tile(Material.ENDER_PEARL, "§d⚡ Enter Test Arena", "test:goto",
                "§7Void sandbox."));
        inventory.setItem(13, tile(Material.LODESTONE, "§6Spawn Anchors & Camps", "page:SPAWN_MARKERS",
                "§7Every origin teleport anchor.", "§7Unlock every camp for yourself.", dot("AetherionHub")));
        inventory.setItem(14, tile(Material.WHITE_BANNER, "§bIsle Weather", "page:ISLE_WEATHER",
                "§7Force fog / rain / snow for ~75s.", dot("AetherionForaging")));
        inventory.setItem(15, tile(Material.END_PORTAL_FRAME, "§8Legacy Portals", "page:PORTALS",
                "§7Old aether_farm_island void world.", "§8Kept for reference."));
        inventory.setItem(16, tile(Material.FILLED_MAP, "§eArea Tools", "page:AREAS",
                "§7World map · crypt hologram ·", "§7building banners · area markers."));
        // Row 2 — world builder (exact previous slots — do not shift).
        inventory.setItem(19, tile(Material.VILLAGER_SPAWN_EGG, "§bNPC Anchors", "page:NPCS",
                "§7Starter · boss givers · world · services.", dot("AetherionQuests")));
        inventory.setItem(20, tile(Material.RECOVERY_COMPASS, "§8Boss Anchors", "page:BOSS_ANCHORS",
                "§7Place boss spawn points.", dot("BossEngine")));
        inventory.setItem(21, tile(Material.MOSS_BLOCK, "§aPet Habitats", "page:PET_HABITATS",
                "§7Paint wild pet biotopes."));
        inventory.setItem(22, tile(Material.ARMOR_STAND, "§dAmbient Props", "page:AMBIENT",
                "§7BlockDisplay scenery — no tick,", "§7safe to place many. Scarecrow · wagon."));
        inventory.setItem(23, tile(Material.BONE, "§cNPC Remover", "give:npcremover",
                "§7Right-click an Aetherion NPC."));
        inventory.setItem(24, tile(Material.SANDSTONE, "§6Colosseum Spawn", "give:colosseum",
                "§7Hub ring teleport anchor."));
        inventory.setItem(25, tile(Material.AMETHYST_CLUSTER, "§dAmethyst Mines Anchor", "give:homestead:amethyst",
                "§7Stand in the Amethyst Area,", "§7right-click to save §f/amethyst§7."));
        // Row 3 — zone markers (exact previous slots).
        inventory.setItem(28, tile(Material.HAY_BLOCK, "§aAnimal Anchor", "give:animal", "§7Place an animal zone."));
        inventory.setItem(29, tile(Material.ROTTEN_FLESH, "§cMob Anchor", "give:mob", "§7Place a combat zone."));
        inventory.setItem(30, tile(Material.COARSE_DIRT, "§6Borderlands Marker", "give:borderlands",
                "§7Waste combat zone + TAB name.", "§7Left-click cycles radius."));
        inventory.setItem(31, tile(Material.DEEPSLATE_IRON_ORE, "§bEldervale Deep Marker", "give:eldervale-mobs",
                "§7Optional · zone already in mob-zones.yml.", "§8Y≤24 deep pack."));
        inventory.setItem(32, tile(Material.CHEST, "§aEvery Spawn Anchor", "give:homestead-all",
                "§7One lodestone per camp.", dot("AetherionHub")));
        inventory.setItem(33, tile(Material.FILLED_MAP, "§6World Map", "give:worldmap",
                "§7~1000 blocks around spawn."));
        inventory.setItem(34, tile(Material.AMETHYST_SHARD, "§5Crypt Hologram", "give:crypt-holo",
                "§7Floating Crypt warning text."));
        // ADDITIVE only: previously empty slot under the zone row — nothing moved.
        inventory.setItem(37, tile(Material.DIAMOND_PICKAXE, "§6§lMining Island", "page:MINE_ISLE",
                "§7Teleports · cast · events · Deep Forge ·", "§7critters · Amethyst Mine · progression.",
                dot("AetherionMining")));
        // ADDITIVE only: the next empty slot beside Mining Island — nothing moved.
        inventory.setItem(38, tile(Material.IRON_AXE, "§2§lForage Island", "forageisle:open",
                "§7Seven forests · cast · updrafts · events ·", "§7critters · finds · marks · progression.",
                dot("AetherionForaging")));
    }

    private static void drawProgress(Inventory inventory) {
        inventory.setItem(10, tile(Material.EXPERIENCE_BOTTLE, "§aMax All Skills", "skills:max",
                "§7Every skill → Lv. 100 + every slot.", "§8You only."));
        inventory.setItem(11, tile(Material.BOOK, "§aTutorial Done", "quests:tutorial-done",
                "§7Complete orientation quests.", "§7Unlocks Workbench · Skills · Anvil · Pets."));
        inventory.setItem(12, tile(Material.KNOWLEDGE_BOOK, "§aUnlock All Recipes", "unlock:recipes-all",
                "§7Every recipe crafted, every resource", "§7obtained. Gates ignored while on."));
        inventory.setItem(13, tile(Material.FILLED_MAP, "§bUnlock All Blueprints", "unlock:blueprint-all",
                "§7Every skill blueprint marked found."));
        inventory.setItem(14, tile(Material.LODESTONE, "§6Unlock Every Camp", "unlock:spawns-all",
                "§7Test the spawn menu without", "§7running every boss quest.", dot("AetherionHub")));
        inventory.setItem(15, tile(Material.NAME_TAG, "§dFill Aetherlex", "pets:unlock-all",
                "§7One of every pet + first-catch XP.", dot("AetherMobs")));
        inventory.setItem(16, tile(Material.ANVIL, "§bBlueprint Forge", "open:blueprint-forge",
                "§7Same GUI as the Eldervale Forgehand."));
        inventory.setItem(19, tile(Material.GOLDEN_HOE, "§aFarming Progression", "page:FARM_ISLE_PROGRESS",
                "§7Farming skill levels · Eldervale loadout ·", "§7mastery · prizes · foods · plot discovery."));
        inventory.setItem(20, tile(Material.NAUTILUS_SHELL, "§bFishing Progression", "page:FISH_ISLE_PROGRESS",
                "§7Fishing skill levels · angler loadout ·", "§7Log · rank · waters · bait · trophies."));
        inventory.setItem(21, tile(Material.FILLED_MAP, "§bBlueprints Shelf", "page:BLUEPRINTS",
                "§7Stones · blueprints · finished tools."));
        inventory.setItem(22, tile(Material.DIAMOND_PICKAXE, "§6Mining Progression", "page:MINE_ISLE_PROGRESS",
                "§7Mining skills · Eldervale loadout · mastery ·", "§7cabinet · Forge Marks · depth · resets."));
        inventory.setItem(25, tile(Material.TNT, "§cResets live in DANGER →", "page:CAT_DANGER",
                "§7Wipe skills · reset quests · profiles.", "§8Each asks twice."));
    }

    private static void drawAdmin(Inventory inventory) {
        inventory.setItem(10, tile(Material.NAME_TAG, "§6Ranks", "page:RANKS",
                "§7Level ranks + ultras (MVP++ / Admin", "§7/ Homie ranks when the backend has them)."));
        inventory.setItem(11, tile(Material.AMETHYST_SHARD, "§bAether Shards", "page:SHARDS",
                "§7Give shards to any online player."));
        inventory.setItem(12, tile(Material.PLAYER_HEAD, "§bTestbots", "page:TESTBOTS",
                "§7QA bots · start / stop / count per role.", dot("AetherionStressBots")));
        inventory.setItem(13, tile(Material.WRITABLE_BOOK, "§b§lNPC Studio", "page:NPC_EDITOR",
                "§7/npc editor · create · nearby · wand ·", "§7list · help · /aethernpc.", dot("AetherionQuests")));
        inventory.setItem(14, tile(Material.BLAZE_ROD, "§6NPC Editor §8(/npc)", "npc-wand",
                "§7FancyNPC + quest creator in one click.", "§8Permission: aetherion.npc.editor"));
        inventory.setItem(15, tile(Material.WRITABLE_BOOK, "§dNPC & Quest Editor", "open:aethernpc",
                "§7Talking NPC in under a minute.", "§eOpens /aethernpc"));
        inventory.setItem(16, tile(Material.FEATHER, "§aToggle /flight", "flight-toggle",
                "§7Same as EssentialsX fly.", "§8essentials.fly · aetherion.flight"));
        inventory.setItem(19, tile(Material.COMPARATOR, "§9⚙ System Status", "page:STATUS",
                "§7Plugin health · TPS · search index."));
        inventory.setItem(20, tile(Material.JUNGLE_SAPLING, "§a§lContent Kit Preview", "page:CONTENT_KIT",
                "§7Exactly what Monkey / Homie ranks see", "§7(aetherion.dev.content)."));
        inventory.setItem(21, tile(Material.ANVIL, "§dBooster Lab", "page:BOOSTER_LAB",
                "§7Apply boosters at an item's rarity."));
        inventory.setItem(22, tile(Material.WRITTEN_BOOK, "§e/botreport", "testbot:report",
                "§7Chat dump + book copy."));
    }

    private static void drawDanger(Inventory inventory) {
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.TNT, "§4§l☠ DANGER ZONE", "noop",
                "§7Everything here asks twice.",
                "§6Special ranks are never touched by any wipe.",
                "",
                "§8Full wipe: pick → review → arm → confirm.",
                "§8Everything else: a confirm screen.")));
        inventory.setItem(11, DevItems.glow(tile(Material.LAVA_BUCKET, "§4§l☠ Full Player Wipe", "page:PLAYER_WIPE",
                "§7Inventory · pads · skills · coins · quests ·",
                "§7codex · recipes · pets · hub unlocks → §f0",
                "§eKick if online · §ano restart needed.",
                "§6Special ranks kept.",
                "",
                "§c▶ Pick a player (nothing happens yet)")));
        inventory.setItem(13, tile(Material.TNT, "§cWipe MY Skills", "confirm:skills:wipe",
                "§7All your skills → Lv. 1, loadout cleared.", "§8Asks to confirm."));
        inventory.setItem(15, tile(Material.WRITABLE_BOOK, "§cReset MY Quests", "confirm:quests:reset-all",
                "§7Wipes your quest progress —", "§7harbour onboarding starts fresh.", "§8Asks to confirm."));
        inventory.setItem(20, tile(Material.HAY_BLOCK, "§cWipe My Farm Eldervale Profile", "confirm:farmisle:profile:reset",
                "§7Plots, mastery, prizes, orders, food.", "§8Asks to confirm."));
        inventory.setItem(21, tile(Material.FISHING_ROD, "§cWipe My Fishing Eldervale Profile", "confirm:fishisle:profile:reset",
                "§7Log, waters, trophies, rank, bait.", "§8Asks to confirm."));
        inventory.setItem(22, tile(Material.IRON_PICKAXE, "§cWipe My Mining Eldervale Profile", "confirm:mineisle:profile:reset",
                "§7Districts, mastery, cabinet, contracts, marks, rep.", "§8Asks to confirm."));
        inventory.setItem(23, tile(Material.VILLAGER_SPAWN_EGG, "§cRemove Farm Isle Cast", "confirm:farmisle:npcall:remove",
                "§7Despawns all eight Farm Eldervale NPCs.", "§8Asks to confirm."));
        inventory.setItem(24, tile(Material.VILLAGER_SPAWN_EGG, "§cRemove Fishing Isle Cast", "confirm:fishisle:npcall:remove",
                "§7Despawns all four Fishing Eldervale NPCs.", "§8Asks to confirm."));
        inventory.setItem(25, tile(Material.VILLAGER_SPAWN_EGG, "§cRemove Mining Isle Cast", "confirm:mineisle:npcall:remove",
                "§7Despawns all six Mining Eldervale NPCs.", "§8Asks to confirm."));
        inventory.setItem(29, tile(Material.END_PORTAL_FRAME, "§cRebuild Legacy Farm Island", "confirm:farmportal:rebuild",
                "§7Re-pastes the old void-world schematic.", "§8Asks to confirm."));
        inventory.setItem(31, tile(Material.BARRIER, "§cStop ALL Testbots", "confirm:testbot:stopall",
                "§7Every QA / stress bot this runner owns.", "§8Asks to confirm."));
        inventory.setItem(33, tile(Material.WITHER_SKELETON_SKULL, "§eClear Arena Bosses §8(sandbox)", "test:clear-bosses",
                "§7Test world only — instant."));
        inventory.setItem(34, tile(Material.BARRIER, "§eClear Arena Mobs §8(sandbox)", "test:clear-mobs",
                "§7Test world only — instant."));
    }

    // ------------------------------------------------------------------ power pages

    static void drawSkillGear(Inventory inventory) {
        inventory.setItem(11, tile(Material.IRON_PICKAXE, "§aMining Sets", "page:MINING", "§7I – V + pickaxes + compressed tools."));
        inventory.setItem(12, tile(Material.GOLDEN_HOE, "§eFarming Sets", "page:FARMING", "§7Armor + levelable hoe."));
        inventory.setItem(13, tile(Material.IRON_AXE, "§2Foraging Sets", "page:FORAGING", "§7Armor + axe."));
        inventory.setItem(14, tile(Material.FISHING_ROD, "§bFishing Sets", "page:FISHING", "§7Armor + rod + Tideglass."));
        inventory.setItem(15, tile(Material.IRON_HOE, "§dCatcher Sets", "page:CATCHER", "§7Armor + gaff."));
        inventory.setItem(29, tile(Material.NETHERITE_PICKAXE, "§aGatherer T5 §8(loadout)", "loadout:gatherer",
                "§7Top tool of every skill in one click."));
        inventory.setItem(31, tile(Material.FILLED_MAP, "§bBlueprint Tools §8(loadout)", "loadout:blueprint-tools",
                "§7All six finished blueprint tools."));
        inventory.setItem(33, tile(Material.FILLED_MAP, "§bBlueprints Shelf", "page:BLUEPRINTS",
                "§7Stones · blueprints · Forge · Ore Troll."));
    }

    static void drawLoadouts(DevMenu menu, Inventory inventory, List<DevMenu.Loadout> loadouts) {
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.ARMOR_STAND, "§6§l⚔ Loadouts", "noop",
                "§7Whole builds in one click.",
                "§7Built from the same factories as the shelves.",
                "§8Overflow drops at your feet.",
                "",
                "§8Press §eF §8on a loadout to pin it.")));
        for (int i = 0; i < loadouts.size() && i < DevTheme.INNER.length; i++) {
            DevMenu.Loadout loadout = loadouts.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + loadout.blurb());
            lore.add("");
            List<ItemStack> items;
            try {
                items = loadout.items().get();
            } catch (RuntimeException error) {
                items = List.of();
                lore.add("§c✖ could not build (" + error.getClass().getSimpleName() + ")");
            }
            int shown = 0;
            for (ItemStack item : items) {
                if (item == null) {
                    continue;
                }
                if (shown++ < 9) {
                    lore.add("§8▪ §f" + DevIndex.plainName(item));
                }
            }
            if (shown > 9) {
                lore.add("§8… +" + (shown - 9) + " more");
            }
            lore.add("");
            lore.add("§a▶ Click §7to receive all §f" + shown + " §7items");
            inventory.setItem(DevTheme.INNER[i], tile(loadout.icon(), loadout.name(), "loadout:" + loadout.id(),
                    lore.toArray(String[]::new)));
        }
    }

    static void drawSearch(DevMenu menu, Inventory inventory, Player player, DevMenu.Holder spec) {
        String query = spec.query() == null ? "" : spec.query();
        List<DevIndex.Entry> results = DevIndex.search(menu, player, query);
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.SPYGLASS, "§d§lSearch §8» §f\"" + query + "\"",
                "search",
                "§7" + results.size() + " match" + (results.size() == 1 ? "" : "es")
                        + " §8across §f" + DevIndex.size() + " §8indexed entries",
                "",
                "§e▶ Click §7to search again")));
        int perPage = DevTheme.INNER.length;
        int pages = Math.max(1, (results.size() + perPage - 1) / perPage);
        int index = Math.min(Math.max(0, spec.index()), pages - 1);
        int start = index * perPage;
        int end = Math.min(results.size(), start + perPage);
        int gives = 0;
        for (int i = start; i < end; i++) {
            DevIndex.Entry entry = results.get(i);
            inventory.setItem(DevTheme.INNER[i - start], DevItems.decorate(entry.icon(), "search", List.of(
                    "",
                    "§8◆ " + entry.path(),
                    entry.page() ? "§e▶ Click to open §8· §7F to pin" : "§e▶ Click to use §8· §7F to pin")));
        }
        for (DevIndex.Entry entry : results) {
            if (DevMenu.isBatchGive(entry.action())) {
                gives++;
            }
        }
        if (results.isEmpty()) {
            inventory.setItem(22, tile(Material.BARRIER, "§cNothing for \"" + query + "\"", "search",
                    "§7Try a shorter word, or one of:",
                    "§fkatana §8· §fdawnbearer §8· §fanchor §8· §fweather",
                    "§fwipe §8· §fsphere §8· §fterminus §8· §fisland",
                    "",
                    "§e▶ Click to search again"));
        }
        menu.drawPager(inventory, Page.SEARCH, index, pages, results.size() - end);
        if (gives > 1 && gives <= SEARCH_BATCH_LIMIT) {
            inventory.setItem(47, tile(Material.HOPPER, "§a⇊ Give every result", "give-page",
                    "§f" + gives + " §7giveable matches in one click.",
                    "§8Pages / tools / NPC anchors are skipped."));
        }
    }

    static void drawStatus(Inventory inventory, Player player) {
        double[] tps = Bukkit.getTPS();
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.COMPARATOR, "§9§l⚙ System Status", "noop",
                "§7TPS §f" + String.format("%.1f §8/ §f%.1f §8/ §f%.1f", tps[0], tps[1], tps[2]) + " §8(1m / 5m / 15m)",
                "§7MSPT §f" + String.format("%.1f", Bukkit.getAverageTickTime()),
                "§7Online §f" + Bukkit.getOnlinePlayers().size() + "§7/§f" + Bukkit.getMaxPlayers()
                        + " §8· §7worlds §f" + Bukkit.getWorlds().size())));
        for (int i = 0; i < PLUGINS.length && i < DevTheme.INNER.length; i++) {
            String name = PLUGINS[i][0];
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            boolean enabled = plugin != null && plugin.isEnabled();
            Material icon = enabled ? Material.LIME_DYE : plugin == null ? Material.GRAY_DYE : Material.RED_DYE;
            String state = enabled ? "§a● online" : plugin == null ? "§8● not installed" : "§c● disabled";
            inventory.setItem(DevTheme.INNER[i], tile(icon, (enabled ? "§a" : "§7") + name, "noop",
                    state + (plugin == null ? "" : " §8· v" + plugin.getDescription().getVersion()),
                    "§7DEV uses it for:",
                    "§f" + PLUGINS[i][1]));
        }
        long age = DevIndex.ageSeconds();
        inventory.setItem(40, tile(Material.BOOKSHELF, "§dSearch Index", "index:rebuild",
                "§7Entries §f" + DevIndex.size(),
                age < 0 ? "§8Not built yet this run." : "§7Built §f" + age + "s §7ago · refreshes every 10 min",
                "",
                "§e▶ Click to rebuild now"));
        inventory.setItem(42, tile(Material.NETHER_STAR, "§eYour dashboard", "page:ROOT",
                "§7" + DevPrefs.favorites(player).size() + " ★ pinned · "
                        + DevPrefs.recents(player).size() + " ⟲ recent"));
    }

    static void drawConfirm(Inventory inventory, DevMenu.Holder spec) {
        String action = DevMenu.confirmAction(spec);
        String label = confirmLabel(action);
        List<String> header = new ArrayList<>();
        header.add("§7You are about to:");
        header.add("§c" + label);
        header.addAll(confirmDetails(action));
        header.add("");
        header.add("§7Nothing happens until you press §cCONFIRM§7.");
        inventory.setItem(DevTheme.HEADER, DevItems.glow(tile(Material.TNT, "§4§l⚠ Confirm", "noop",
                header.toArray(String[]::new))));
        ItemStack keep = tile(Material.LIME_CONCRETE, "§a§l✔ KEEP §7— cancel", "confirm-no",
                "§7Back to §f" + DevTheme.pageName(DevMenu.confirmReturn(spec)) + "§7.", "§7Nothing is touched.");
        ItemStack go = tile(Material.RED_CONCRETE, "§c§l✖ CONFIRM §7— " + label, "confirm-yes",
                confirmDetails(action).toArray(String[]::new));
        for (int slot : new int[]{19, 20, 21, 28, 29, 30}) {
            inventory.setItem(slot, keep.clone());
        }
        for (int slot : new int[]{23, 24, 25, 32, 33, 34}) {
            inventory.setItem(slot, go.clone());
        }
        ItemStack icon = DevIndex.icon("confirm:" + action);
        inventory.setItem(22, icon != null
                ? DevItems.decorate(icon, "confirm", List.of())
                : tile(Material.TNT, "§c" + label, "noop"));
        inventory.setItem(31, tile(Material.NAME_TAG, "§6Special ranks are never touched", "noop",
                "§7No DEV wipe or reset strips Monkey /", "§7Citrus / Beta / MVP++ / Admin."));
    }

    static String confirmLabel(String action) {
        if (action == null) {
            return "nothing";
        }
        return switch (action) {
            case "skills:wipe" -> "Wipe MY skills";
            case "quests:reset-all" -> "Reset MY quests";
            case "testbot:stopall" -> "Stop ALL testbots";
            case "farmportal:rebuild" -> "Rebuild legacy farm island";
            case "farmisle:profile:reset" -> "Wipe my Farm Eldervale profile";
            case "farmisle:npcall:remove" -> "Remove the Farm Isle cast";
            case "fishisle:profile:reset" -> "Wipe my Fishing Eldervale profile";
            case "fishisle:npcall:remove" -> "Remove the Fishing Isle cast";
            case "mineisle:profile:reset" -> "Wipe my Mining Eldervale profile";
            case "mineisle:npcall:remove" -> "Remove the Mining Isle cast";
            default -> action;
        };
    }

    private static List<String> confirmDetails(String action) {
        if (action == null) {
            return List.of();
        }
        return switch (action) {
            case "skills:wipe" -> List.of("§7All your skill levels → 1.", "§7Skill loadout cleared.");
            case "quests:reset-all" -> List.of("§7Every quest you started resets.", "§7Harbour onboarding starts fresh.");
            case "testbot:stopall" -> List.of("§7Every QA / stress bot this runner owns quits.");
            case "farmportal:rebuild" -> List.of("§7Re-pastes aether_farm_island from the schematic.");
            case "farmisle:profile:reset" -> List.of("§7Your plots, mastery, prizes, orders, food.");
            case "farmisle:npcall:remove" -> List.of("§7Despawns the whole Farm Eldervale cast.", "§8Presets can re-place them.");
            case "fishisle:profile:reset" -> List.of("§7Your Log, waters, trophies, rank, bait.");
            case "fishisle:npcall:remove" -> List.of("§7Despawns the whole Fishing Eldervale cast.", "§8Presets can re-place them.");
            case "mineisle:profile:reset" -> List.of("§7Districts, mastery, cabinet, contracts, marks, rep.");
            case "mineisle:npcall:remove" -> List.of("§7Despawns the whole Mining Eldervale cast.", "§8Presets can re-place them.");
            default -> List.of();
        };
    }
}
