package de.aetherion.items.menu.dev;

import de.aetherion.items.menu.dev.DevMenu.Page;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * DEV // AETHERION chrome: category identity (colour, glass, icon, sound), the page tree
 * (parent + title per page) that drives breadcrumbs and Go Back, and the frame painter.
 *
 * <p>Frame rule (kept from the glass-ring pass): content lives in the inner 7×4 only.
 * Row 0 is the category bar (header at 4, page arrows at 0 / 8). Row 5 is navigation:
 * 45 Go Back · 49 Close · 53 Search. Set grids also use 46–48 / 50–52 for weapons.
 */
final class DevTheme {

    /** Inner 7×4 content slots — never the glass ring. */
    static final int[] INNER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    static final int HEADER = 4;
    static final int PREV = 0;
    static final int NEXT = 8;
    static final int BACK = 45;
    static final int CLOSE = 49;
    static final int SEARCH = 53;

    enum Cat {
        DASH("DEV", "§5", "§5", Material.PURPLE_STAINED_GLASS_PANE, Material.NETHER_STAR,
                "Command center"),
        CONTENT("CONTENT", "§a", "§2", Material.LIME_STAINED_GLASS_PANE, Material.CHEST,
                "Craft · loot · resources · pets"),
        COMBAT("COMBAT LAB", "§d", "§5", Material.MAGENTA_STAINED_GLASS_PANE, Material.NETHERITE_SWORD,
                "Weapons · sets · flagships · arena · bosses"),
        WORLDS("WORLDS", "§b", "§3", Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.COMPASS,
                "Travel · islands · anchors · world builder"),
        PROGRESS("PROGRESS", "§e", "§6", Material.YELLOW_STAINED_GLASS_PANE, Material.EXPERIENCE_BOTTLE,
                "Skills · quests · unlocks · codex"),
        ADMIN("ADMIN", "§9", "§1", Material.BLUE_STAINED_GLASS_PANE, Material.COMMAND_BLOCK,
                "Ranks · shards · bots · NPC studio · status"),
        DANGER("DANGER", "§c", "§4", Material.RED_STAINED_GLASS_PANE, Material.TNT,
                "Wipes & resets — everything asks twice"),
        KIT("CONTENT KIT", "§a", "§2", Material.GREEN_STAINED_GLASS_PANE, Material.JUNGLE_SAPLING,
                "Monkey / Homie tools");

        final String label;
        final String accent;
        final String titleColor;
        final Material glass;
        final Material icon;
        final String tagline;

        Cat(String label, String accent, String titleColor, Material glass, Material icon, String tagline) {
            this.label = label;
            this.accent = accent;
            this.titleColor = titleColor;
            this.glass = glass;
            this.icon = icon;
            this.tagline = tagline;
        }

        String pretty() {
            return switch (this) {
                case COMBAT -> "Combat Lab";
                case KIT -> "Content Kit";
                case DASH -> "Dashboard";
                default -> label.charAt(0) + label.substring(1).toLowerCase(java.util.Locale.ROOT);
            };
        }
    }

    record Info(Cat cat, String title, Page parent) {
    }

    private DevTheme() {
    }

    static Page hub(Cat cat) {
        return switch (cat) {
            case CONTENT -> Page.CAT_CONTENT;
            case COMBAT -> Page.CAT_COMBAT;
            case WORLDS -> Page.CAT_WORLDS;
            case PROGRESS -> Page.CAT_PROGRESS;
            case ADMIN -> Page.CAT_ADMIN;
            case DANGER -> Page.CAT_DANGER;
            case KIT -> Page.CONTENT_KIT;
            case DASH -> Page.ROOT;
        };
    }

    /** The IA: every page's category, short title and parent (for Go Back + breadcrumbs). */
    static Info info(Page page) {
        return switch (page) {
            case ROOT -> new Info(Cat.DASH, null, null);
            case SEARCH -> new Info(Cat.DASH, "Search", Page.ROOT);
            case CAT_CONTENT -> new Info(Cat.CONTENT, null, Page.ROOT);
            case CAT_COMBAT -> new Info(Cat.COMBAT, null, Page.ROOT);
            case CAT_WORLDS -> new Info(Cat.WORLDS, null, Page.ROOT);
            case CAT_PROGRESS -> new Info(Cat.PROGRESS, null, Page.ROOT);
            case CAT_ADMIN -> new Info(Cat.ADMIN, null, Page.ROOT);
            case CAT_DANGER -> new Info(Cat.DANGER, null, Page.ROOT);
            case CONTENT_KIT -> new Info(Cat.KIT, null, Page.CAT_ADMIN);

            // CONTENT — craft / loot
            case RESOURCES -> new Info(Cat.CONTENT, "Resources", Page.CAT_CONTENT);
            case TOOLS -> new Info(Cat.CONTENT, "Tools & Storage", Page.CAT_CONTENT);
            case BOOSTERS -> new Info(Cat.CONTENT, "Boosters", Page.CAT_CONTENT);
            case BOOSTER_LAB -> new Info(Cat.CONTENT, "Booster Lab", Page.CAT_CONTENT);
            case CHARMS -> new Info(Cat.CONTENT, "Charms", Page.CAT_CONTENT);
            case BLUEPRINTS -> new Info(Cat.CONTENT, "Blueprints", Page.CAT_CONTENT);
            case PETS -> new Info(Cat.CONTENT, "Pets", Page.CAT_CONTENT);
            case SPHERES -> new Info(Cat.CONTENT, "Catch Spheres", Page.CAT_CONTENT);
            case MILLSTONE -> new Info(Cat.CONTENT, "Millstone & Pantry", Page.CAT_CONTENT);
            case BORDERLANDS_SPIRITS -> new Info(Cat.CONTENT, "Spirit Vials", Page.CAT_CONTENT);
            case SKILL_GEAR -> new Info(Cat.CONTENT, "Skill Gear", Page.CAT_CONTENT);
            case MINING -> new Info(Cat.CONTENT, "Mining Sets", Page.SKILL_GEAR);
            case FARMING -> new Info(Cat.CONTENT, "Farming Sets", Page.SKILL_GEAR);
            case FORAGING -> new Info(Cat.CONTENT, "Foraging Sets", Page.SKILL_GEAR);
            case FISHING -> new Info(Cat.CONTENT, "Fishing Sets", Page.SKILL_GEAR);
            case CATCHER -> new Info(Cat.CONTENT, "Catcher Sets", Page.SKILL_GEAR);

            // COMBAT LAB — weapons / test
            case WEAPONS -> new Info(Cat.COMBAT, "Weapons", Page.CAT_COMBAT);
            case WEAPONS_STARTER -> new Info(Cat.COMBAT, "Starter", Page.WEAPONS);
            case WEAPONS_BOWS -> new Info(Cat.COMBAT, "Bows", Page.WEAPONS);
            case WEAPONS_PROGRESSION -> new Info(Cat.COMBAT, "Progression", Page.WEAPONS);
            case WEAPONS_T1 -> new Info(Cat.COMBAT, "T1 Uniques", Page.WEAPONS);
            case WEAPONS_T2 -> new Info(Cat.COMBAT, "T2 Uniques", Page.WEAPONS);
            case WEAPONS_DUNGEON -> new Info(Cat.COMBAT, "Dungeon Relics", Page.WEAPONS);
            case WEAPONS_SPECIAL -> new Info(Cat.COMBAT, "Special Weapons", Page.WEAPONS);
            case WEAPONS_GOD -> new Info(Cat.COMBAT, "Test Extras", Page.WEAPONS);
            case COMBAT -> new Info(Cat.COMBAT, "Combat Sets", Page.CAT_COMBAT);
            case SETS -> new Info(Cat.COMBAT, "Special Sets", Page.CAT_COMBAT);
            case LOADOUTS -> new Info(Cat.COMBAT, "Loadouts", Page.CAT_COMBAT);
            case TEST_ARENA -> new Info(Cat.COMBAT, "Test Arena", Page.CAT_COMBAT);
            case TEST_GEAR -> new Info(Cat.COMBAT, "Flagships & Test Gear", Page.CAT_COMBAT);
            case BOSS_CORES -> new Info(Cat.COMBAT, "Boss Cores", Page.CAT_COMBAT);
            case DUNGEONS -> new Info(Cat.COMBAT, "Dungeons", Page.CAT_COMBAT);

            // WORLDS — travel / world builder
            case FARM_ISLE -> new Info(Cat.WORLDS, "Farming Island", Page.CAT_WORLDS);
            case FARM_ISLE_SPOTS -> new Info(Cat.WORLDS, "Farm · Teleports", Page.FARM_ISLE);
            case FARM_ISLE_NPCS -> new Info(Cat.WORLDS, "Farm · NPC Cast", Page.FARM_ISLE);
            case FARM_ISLE_PROPS -> new Info(Cat.WORLDS, "Farm · Props", Page.FARM_ISLE);
            case FARM_ISLE_EVENTS -> new Info(Cat.WORLDS, "Farm · Events", Page.FARM_ISLE);
            case FARM_ISLE_PROGRESS -> new Info(Cat.WORLDS, "Farm · Progression", Page.FARM_ISLE);
            case PORTALS -> new Info(Cat.WORLDS, "Legacy Portals", Page.FARM_ISLE);
            case FISH_ISLE -> new Info(Cat.WORLDS, "Fishing Island", Page.CAT_WORLDS);
            case FISH_ISLE_SPOTS -> new Info(Cat.WORLDS, "Fish · Teleports", Page.FISH_ISLE);
            case FISH_ISLE_NPCS -> new Info(Cat.WORLDS, "Fish · NPC Cast", Page.FISH_ISLE);
            case FISH_ISLE_EVENTS -> new Info(Cat.WORLDS, "Fish · Events", Page.FISH_ISLE);
            case FISH_ISLE_PROGRESS -> new Info(Cat.WORLDS, "Fish · Progression", Page.FISH_ISLE);
            case MINE_ISLE -> new Info(Cat.WORLDS, "Mining Island", Page.CAT_WORLDS);
            case MINE_ISLE_SPOTS -> new Info(Cat.WORLDS, "Mine · Teleports", Page.MINE_ISLE);
            case MINE_ISLE_NPCS -> new Info(Cat.WORLDS, "Mine · NPC Cast", Page.MINE_ISLE);
            case MINE_ISLE_EVENTS -> new Info(Cat.WORLDS, "Mine · Events & Critters", Page.MINE_ISLE);
            case MINE_ISLE_PROGRESS -> new Info(Cat.WORLDS, "Mine · Progression", Page.MINE_ISLE);
            case ISLE_WEATHER -> new Info(Cat.WORLDS, "Isle Weather", Page.CAT_WORLDS);
            case SPAWN_MARKERS -> new Info(Cat.WORLDS, "Spawn Anchors", Page.CAT_WORLDS);
            case NPCS -> new Info(Cat.WORLDS, "NPC Anchors", Page.CAT_WORLDS);
            case NPCS_STARTER -> new Info(Cat.WORLDS, "Starter NPCs", Page.NPCS);
            case NPCS_BOSSES -> new Info(Cat.WORLDS, "Boss Givers", Page.NPCS);
            case NPCS_WORLD -> new Info(Cat.WORLDS, "World NPCs", Page.NPCS);
            case NPCS_SERVICES -> new Info(Cat.WORLDS, "Services", Page.NPCS);
            case BOSS_ANCHORS -> new Info(Cat.WORLDS, "Boss Anchors", Page.CAT_WORLDS);
            case AREAS -> new Info(Cat.WORLDS, "Area Tools", Page.CAT_WORLDS);
            case PET_HABITATS -> new Info(Cat.WORLDS, "Pet Habitats", Page.CAT_WORLDS);
            case AMBIENT -> new Info(Cat.WORLDS, "Ambient Props", Page.CAT_WORLDS);

            // ADMIN
            case RANKS -> new Info(Cat.ADMIN, "Ranks", Page.CAT_ADMIN);
            case SHARDS -> new Info(Cat.ADMIN, "Aether Shards", Page.CAT_ADMIN);
            case TESTBOTS -> new Info(Cat.ADMIN, "Testbots", Page.CAT_ADMIN);
            case TESTBOTS_LIST -> new Info(Cat.ADMIN, "Bot List", Page.TESTBOTS);
            case NPC_EDITOR -> new Info(Cat.ADMIN, "NPC Studio", Page.CAT_ADMIN);
            case STATUS -> new Info(Cat.ADMIN, "System Status", Page.CAT_ADMIN);

            // DANGER
            case PLAYER_WIPE -> new Info(Cat.DANGER, "Full Player Wipe", Page.CAT_DANGER);
            case CONFIRM -> new Info(Cat.DANGER, "Confirm", Page.CAT_DANGER);
        };
    }

    static String pageName(Page page) {
        Info info = info(page);
        return info.title() != null ? info.title() : info.cat().pretty();
    }

    /** "Combat Lab › Weapons › T2 Uniques" — hubs and dashboard excluded from the chain start. */
    static String breadcrumb(Page page) {
        List<String> chain = new ArrayList<>();
        Page cursor = page;
        int guard = 0;
        while (cursor != null && cursor != Page.ROOT && guard++ < 8) {
            chain.add(0, pageName(cursor));
            cursor = info(cursor).parent();
        }
        return chain.isEmpty() ? "Dashboard" : String.join(" › ", chain);
    }

    /**
     * Inventory titles stay inside the ~170px chest title bar: hubs read {@code DEV » COMBAT LAB},
     * shelves read {@code ■ T2 Uniques} with the ■ in the category colour. The full breadcrumb
     * lives in the header lore. Dark colours only — the title bar is light grey.
     */
    static String title(Page page, String suffix) {
        Info info = info(page);
        Cat cat = info.cat();
        String tail = suffix == null || suffix.isBlank() ? "" : " §8" + suffix;
        if (page == Page.ROOT) {
            return "§0§lDEV §8// §5§lAETHERION";
        }
        if (info.title() == null) {
            return "§0DEV §8» " + cat.titleColor + "§l" + cat.label + tail;
        }
        return cat.titleColor + "■ §0" + info.title() + tail;
    }

    /** Ring + category bar. DANGER gets a full red ring; everything else a black ring with a coloured top bar. */
    static void paintFrame(Inventory inventory, Cat cat) {
        int size = inventory.getSize();
        int rows = size / 9;
        ItemStack ring = pane(cat == Cat.DANGER ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE);
        ItemStack bar = pane(cat.glass);
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (!border) {
                inventory.setItem(slot, null);
            } else if (row == 0) {
                inventory.setItem(slot, bar.clone());
            } else {
                inventory.setItem(slot, ring.clone());
            }
        }
    }

    static ItemStack pane(Material material) {
        return pane(material, " ");
    }

    static ItemStack pane(Material material, String name, String... lore) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            pane.setItemMeta(meta);
        }
        return pane;
    }

    static List<String> controls() {
        return List.of(
                "§8▸ §7Click §8· §fgive / open",
                "§8▸ §7Shift-click §8· §fa full stack",
                "§8▸ §7Press §eF §8· §f★ pin to the dashboard");
    }

    /** Page-change juice. Hubs chime in their own pitch; DANGER hums; shelves turn a page. */
    static void sound(Player player, Page page) {
        Info info = info(page);
        float pitch = switch (info.cat()) {
            case CONTENT -> 1.35f;
            case COMBAT -> 0.9f;
            case WORLDS -> 1.6f;
            case PROGRESS -> 1.2f;
            case ADMIN -> 1.05f;
            case DANGER, KIT, DASH -> 1.0f;
        };
        if (page == Page.ROOT) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.8f);
        } else if (info.cat() == Cat.DANGER) {
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f, 0.7f);
        } else if (info.title() == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, pitch);
        } else {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, pitch);
        }
    }
}
