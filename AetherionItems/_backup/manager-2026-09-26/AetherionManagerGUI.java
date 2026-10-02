package de.aetherion.items.menu;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.storage.StorageInventory;
import de.aetherion.items.util.QuestProgressHook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AetherionManagerGUI {

    /**
     * Manager-only chest overlay via pack font bitmap.
     * {@code \uE005} = -48px nudge, {@code \uE004} = GUI texture (vanilla-aligned).
     * Must be Adventure Component — Paper 1.21 String titles are deprecated and can drop PUA glyphs.
     */
    public static final Component TITLE = Component.text("\uE005\uE004", NamedTextColor.WHITE);

    public static final int SHOP_SLOT = 10;
    public static final int BAZAAR_SLOT = 12;
    public static final int SKILLS_SLOT = 13;
    public static final int AUCTION_SLOT = 14;

    public static final int BESTIARY_SLOT = 20;
    public static final int COLLECTION_SLOT = 21;
    public static final int PETS_SLOT = 22;
    public static final int JOURNAL_SLOT = 23;
    public static final int RECIPE_SLOT = 24;

    public static final int GUILD_SLOT = 28;
    public static final int STATS_SLOT = 29;
    public static final int STORAGE_SLOT = 30;
    public static final int LOADOUT_SLOT = 31;
    public static final int SPAWN_SLOT = 32;
    public static final int CRAFT_SLOT = 33;
    public static final int ANVIL_SLOT = 34;

    public static final int ISLAND_SLOT = 40;
    public static final int DEV_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private static final String SKILLS_QUEST_ID = "lesson_manager";
    private static final String PETS_QUEST_ID = "pocket_zoo";
    private static final String CRAFT_QUEST_ID = "a_simple_craft";
    private static final Map<UUID, BukkitTask> skillsBlinkTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> spawnsBlinkTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> petsBlinkTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> craftBlinkTasks = new ConcurrentHashMap<>();

    /** Every clickable hub tab, in layout order. Drives the header tally and click routing. */
    private static final int[] TAB_SLOTS = {
            SHOP_SLOT, BAZAAR_SLOT, SKILLS_SLOT, AUCTION_SLOT,
            BESTIARY_SLOT, COLLECTION_SLOT, PETS_SLOT, JOURNAL_SLOT, RECIPE_SLOT,
            GUILD_SLOT, STATS_SLOT, STORAGE_SLOT, LOADOUT_SLOT, SPAWN_SLOT, CRAFT_SLOT, ANVIL_SLOT,
            ISLAND_SLOT
    };

    private final AetherionManager manager;
    private final StorageInventory storageInventory;

    public AetherionManagerGUI(AetherionManager manager, StorageInventory storageInventory) {
        this.manager = manager;
        this.storageInventory = storageInventory;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE); // Component title → font overlay
        fill(inventory);
        ProgressionService progress = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().progress();

        inventory.setItem(SHOP_SLOT, button(
                Material.AMETHYST_SHARD,
                "§bAether Shop",
                "§7Spend shards. Not coins.",
                "",
                "§eClick or /shardshop"
        ));
        put(inventory, BAZAAR_SLOT, progress != null && progress.bazaar(player),
                Material.CHEST, "§6Bazaar",
                progress == null ? "§7The trader has a face. Use it once." : progress.hint(ProgressionService.Flag.TRADER),
                "§7Player listings for resources.",
                "§7The trader also buys these.");
        boolean skillsUnlocked = progress == null || progress.skills(player);
        boolean skillsQuest = skillsUnlocked && QuestProgressHook.isQuestActive(player, SKILLS_QUEST_ID);
        if (skillsUnlocked) {
            inventory.setItem(SKILLS_SLOT, skillsButton(skillsQuest, false));
        } else {
            put(inventory, SKILLS_SLOT, false,
                    Material.NETHERITE_SCRAP, "§dSkills",
                    progress == null ? "§7Miss Ledger unlocks this." : progress.hint(ProgressionService.Flag.SKILLS),
                    "§7Seven slots. One to start.");
        }
        put(inventory, AUCTION_SLOT, progress != null && progress.auction(player),
                Material.GOLD_BLOCK, "§eAuction House",
                progress == null ? "§7The trader has a face. Use it once." : progress.hint(ProgressionService.Flag.TRADER),
                "§7Gear and tools only.",
                "§7Buyout listings, no instant trader.");

        put(inventory, BESTIARY_SLOT, progress != null && progress.bestiary(player),
                Material.BONE, "§6Bestiary",
                progress == null ? "§7Make your first kill. The mobs are keeping score." : progress.bestiaryHint(),
                "§7Every mob you have killed,",
                "§7plus the top 3 hunters.");
        put(inventory, COLLECTION_SLOT, progress != null && progress.collection(player),
                Material.IRON_PICKAXE, "§aCollection",
                progress == null ? "§7Break one block. Paperwork follows." : progress.collectionHint(),
                "§7Blocks you mined, grouped",
                "§7by ores, wood, stone and more.");
        boolean petsUnlocked = manager.hasPetMenu() && (progress == null || progress.pets(player));
            boolean petsQuest = petsUnlocked && QuestProgressHook.shouldGuidePetsEquip(player);
        if (!manager.hasPetMenu()) {
            inventory.setItem(PETS_SLOT, button(
                    Material.BARRIER,
                    "§cPet Collection",
                    "§7Requires AetherMobs."
            ));
        } else if (petsUnlocked) {
            inventory.setItem(PETS_SLOT, petsButton(petsQuest, false));
        } else {
            put(inventory, PETS_SLOT, false,
                    Material.LEAD, "§dPet Collection",
                    progress == null ? "§7Lark unlocks this with Catch Spheres." : progress.hint(ProgressionService.Flag.PETS),
                    "§7Open your caught pets.");
        }
        put(inventory, JOURNAL_SLOT, progress != null && progress.journal(player),
                Material.WRITABLE_BOOK, "§5Dungeon Journal",
                progress == null ? "§7A dungeon boss. Then the receipts." : progress.journalHint(),
                "§7Boss kills and what they",
                "§7can drop. One page per boss.");
        put(inventory, RECIPE_SLOT, progress != null && progress.recipeBook(player),
                Material.KNOWLEDGE_BOOK, "§6Recipe Book",
                progress == null ? "§7Craftsman unlocks this with Crafting." : progress.hint(ProgressionService.Flag.WORKBENCH),
                "§7Browse every Aetherion recipe.");

        boolean guildPlugin = Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds");
        put(inventory, ISLAND_SLOT, guildPlugin && (progress == null || progress.island(player)),
                Material.GRASS_BLOCK, "§aIsland",
                !guildPlugin ? "§7Requires AetherionGuilds."
                        : (progress == null ? "§7Level 20. Your private plot." : progress.islandHint()),
                "§7Your personal island, quarries",
                "§7and friend visits.");
        put(inventory, GUILD_SLOT, guildPlugin && (progress == null || progress.guild(player)),
                Material.YELLOW_BANNER, "§6Guild",
                !guildPlugin ? "§7Requires AetherionGuilds."
                        : (progress == null ? "§7Level 75. Mid–late game club." : progress.guildHint()),
                "§7Shared island, invites, ranks",
                "§7and guild quarries.");
        inventory.setItem(STATS_SLOT, button(
                Material.EXPERIENCE_BOTTLE,
                "§bStat Overview",
                "§7See your currently active",
                "§7Aetherion stats."
        ));
        inventory.setItem(STORAGE_SLOT, button(
                Material.ENDER_CHEST,
                "§5Aetherion Storage",
                "§7Open your personal storage."
        ));
        inventory.setItem(LOADOUT_SLOT, button(
                Material.ARMOR_STAND,
                "§bLoadouts",
                "§7Save and swap armor sets."
        ));
        boolean hubPlugin = Bukkit.getPluginManager().isPluginEnabled("AetherionHub");
        boolean spawnUnlocked = hubPlugin && (progress == null || progress.spawns(player));
        boolean spawnTip = spawnUnlocked && hasHomesteadMarker(player);
        if (spawnTip) {
            inventory.setItem(SPAWN_SLOT, spawnsButton(true, true));
        } else {
            put(inventory, SPAWN_SLOT, spawnUnlocked,
                    Material.COMPASS, "§6Spawns",
                    !hubPlugin ? "§7Requires AetherionHub."
                            : (progress == null ? "§7Your first spawn unlocker. Then the map has opinions." : progress.hint(ProgressionService.Flag.SPAWN_UNLOCKER)),
                    "§7World camps. Boss quests unlock them.",
                    "§7Locked spots stay visible.");
        }
        boolean craftUnlocked = progress != null && progress.craftingTable(player);
        put(inventory, CRAFT_SLOT, craftUnlocked,
                Material.CRAFTING_TABLE, "§eCrafting Table",
                progress == null ? "§7Craftsman unlocks this." : progress.hint(ProgressionService.Flag.WORKBENCH),
                "§7Open a portable workbench.");
        put(inventory, ANVIL_SLOT, progress != null && progress.anvil(player),
                Material.ANVIL, "§eAnvil",
                progress == null ? "§7Talk to Temper first." : progress.hint(ProgressionService.Flag.ANVIL),
                "§7Open booster sockets.",
                "§714 slots. Swap anytime.");
        // Craft quest points at exactly one tab: the Recipe Book. No second highlight to chase.
        boolean craftQuest = craftUnlocked && QuestProgressHook.isQuestActive(player, CRAFT_QUEST_ID);
        if (craftQuest) {
            inventory.setItem(RECIPE_SLOT, recipeQuestButton(true));
        }

        if (player.isOp() || player.hasPermission("aetherion.dev")) {
            inventory.setItem(DEV_SLOT, button(
                    Material.COMMAND_BLOCK,
                    "§cDEV Menu",
                    "§7Sets, items, bosses, animals,",
                    "§7pets and NPCs. One click."
            ));
        }

        inventory.setItem(CLOSE_SLOT, button(
                Material.BARRIER,
                "§cClose",
                "§7Close this menu."
        ));

        inventory.setItem(4, header(inventory));

        player.openInventory(inventory);
        if (skillsQuest) {
            startSkillsBlink(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Click the blinking Skills tab",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
        } else if (petsQuest) {
            startPetsBlink(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Pets tab blinking — open & equip your catch",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
        } else if (craftQuest) {
            startCraftBlink(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Green Recipe Book — find Mining Pickaxe (2nd recipe)",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
        } else if (spawnTip) {
            startSpawnsBlink(player);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Spawns tab blinking — set your camp after unlocking",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
        }
    }

    /** Built last so it can count the tabs that ended up unlocked. */
    private ItemStack header(Inventory inventory) {
        int open = 0;
        for (int slot : TAB_SLOTS) {
            ItemStack tab = inventory.getItem(slot);
            Material type = tab == null ? Material.AIR : tab.getType();
            if (type != Material.AIR && type != Material.GRAY_DYE && type != Material.BARRIER) {
                open++;
            }
        }
        return button(
                Material.NETHER_STAR,
                "§6Aetherion Manager",
                "§7Your hub. It grows as you do.",
                "",
                "§7Tabs open: §a" + open + "§7/§f" + TAB_SLOTS.length,
                "§8Grey tabs name who unlocks them."
        );
    }

    /** True for slots the hub actually reacts to — everything else is filler glass. */
    public static boolean isActionSlot(int slot) {
        if (slot == DEV_SLOT || slot == CLOSE_SLOT) {
            return true;
        }
        for (int tab : TAB_SLOTS) {
            if (tab == slot) {
                return true;
            }
        }
        return false;
    }

    public static void stopSkillsBlink(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = skillsBlinkTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public static void stopSpawnsBlink(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = spawnsBlinkTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private static boolean hasHomesteadMarker(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        return hub != null && hub.hasUnlockItem(player);
    }

    private void startSpawnsBlink(Player player) {
        stopSpawnsBlink(player);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        final boolean[] bright = {true};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopSpawnsBlink(player);
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                stopSpawnsBlink(player);
                return;
            }
            if (!hasHomesteadMarker(player)) {
                player.getOpenInventory().getTopInventory().setItem(SPAWN_SLOT, spawnsButton(false, false));
                stopSpawnsBlink(player);
                return;
            }
            bright[0] = !bright[0];
            player.getOpenInventory().getTopInventory().setItem(SPAWN_SLOT, spawnsButton(true, bright[0]));
        }, 8L, 8L);
        spawnsBlinkTasks.put(player.getUniqueId(), task);
    }

    private ItemStack spawnsButton(boolean highlight, boolean pulseBright) {
        if (!highlight) {
            return button(
                    Material.COMPASS,
                    "§6Spawns",
                    "§7World camps. Boss quests unlock them.",
                    "§7Locked spots stay visible.",
                    "",
                    "§eClick to open"
            );
        }
        Material material = pulseBright ? Material.SUNFLOWER : Material.COMPASS;
        String name = pulseBright ? "§6§l★ Spawns ★" : "§6§lSpawns";
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(
                    "§6§lQUEST TIP",
                    "§fRight-click the blinking campfire first,",
                    "§fthen open Spawns to set /spawn.",
                    "",
                    "§eClick to open"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void stopPetsBlink(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = petsBlinkTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public static void stopCraftBlink(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = craftBlinkTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /** Cancel every hub blink for this player — call on close and on quit. */
    public static void stopAllBlinks(Player player) {
        stopSkillsBlink(player);
        stopPetsBlink(player);
        stopCraftBlink(player);
        stopSpawnsBlink(player);
    }

    private void startPetsBlink(Player player) {
        stopPetsBlink(player);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        final boolean[] bright = {true};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopPetsBlink(player);
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                stopPetsBlink(player);
                return;
            }
            if (!QuestProgressHook.shouldGuidePetsEquip(player)) {
                player.getOpenInventory().getTopInventory().setItem(PETS_SLOT, petsButton(false, false));
                stopPetsBlink(player);
                return;
            }
            bright[0] = !bright[0];
            player.getOpenInventory().getTopInventory().setItem(PETS_SLOT, petsButton(true, bright[0]));
        }, 8L, 8L);
        petsBlinkTasks.put(player.getUniqueId(), task);
    }

    private void startCraftBlink(Player player) {
        stopCraftBlink(player);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        final boolean[] bright = {true};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopCraftBlink(player);
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                stopCraftBlink(player);
                return;
            }
            ProgressionService progress = plugin.progress();
            if (!QuestProgressHook.isQuestActive(player, CRAFT_QUEST_ID)) {
                if (progress != null && progress.recipeBook(player)) {
                    player.getOpenInventory().getTopInventory().setItem(RECIPE_SLOT, button(
                            Material.KNOWLEDGE_BOOK, "§6Recipe Book",
                            "§7Browse every Aetherion recipe."
                    ));
                }
                stopCraftBlink(player);
                return;
            }
            bright[0] = !bright[0];
            player.getOpenInventory().getTopInventory().setItem(RECIPE_SLOT, recipeQuestButton(bright[0]));
        }, 8L, 8L);
        craftBlinkTasks.put(player.getUniqueId(), task);
    }

    private ItemStack recipeQuestButton(boolean pulseBright) {
        Material material = pulseBright ? Material.KNOWLEDGE_BOOK : Material.LIME_DYE;
        String name = pulseBright ? "§a§l★ Recipe Book ★" : "§a§lRecipe Book";
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(
                    "§e§lQUEST TIP",
                    "§fGreen Recipe Book in the Manager →",
                    "§fMining Pickaxe (Simple Pickaxe + coal).",
                    "",
                    "§eClick to open"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack petsButton(boolean highlight, boolean pulseBright) {
        if (!highlight) {
            return button(
                    Material.LEAD,
                    "§dPet Collection",
                    "§7Open your caught pets.",
                    "",
                    "§eClick to open"
            );
        }
        Material material = pulseBright ? Material.PINK_DYE : Material.LEAD;
        String name = pulseBright ? "§d§l★ Pets ★" : "§d§lPets";
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(
                    "§d§lQUEST TIP",
                    "§fOpen Pets → select your catch → Equip.",
                    "§7Lark wants proof it's in your pocket.",
                    "",
                    "§eClick to open"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startSkillsBlink(Player player) {
        stopSkillsBlink(player);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        final boolean[] bright = {true};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopSkillsBlink(player);
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                stopSkillsBlink(player);
                return;
            }
            if (!QuestProgressHook.isQuestActive(player, SKILLS_QUEST_ID)) {
                player.getOpenInventory().getTopInventory().setItem(SKILLS_SLOT, skillsButton(false, false));
                stopSkillsBlink(player);
                return;
            }
            bright[0] = !bright[0];
            player.getOpenInventory().getTopInventory().setItem(SKILLS_SLOT, skillsButton(true, bright[0]));
        }, 8L, 8L);
        skillsBlinkTasks.put(player.getUniqueId(), task);
    }

    private ItemStack skillsButton(boolean highlight, boolean pulseBright) {
        if (!highlight) {
            return button(
                    Material.NETHERITE_SCRAP,
                    "§dSkills",
                    "§7Seven slots. One to start.",
                    "§7Skills level with you. Swap freely.",
                    "",
                    "§eClick to open"
            );
        }
        Material material = pulseBright ? Material.AMETHYST_SHARD : Material.NETHERITE_SCRAP;
        String name = pulseBright ? "§d§l★ Skills ★" : "§d§lSkills";
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(
                    "§d§lQUEST TIP",
                    "§fMiss Ledger wants one skill equipped.",
                    "§7Click here → pick any skill → free slot.",
                    "",
                    "§eClick to open"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void put(
            Inventory inventory,
            int slot,
            boolean unlocked,
            Material material,
            String name,
            String lockedHint,
            String... unlockedLore
    ) {
        if (unlocked) {
            inventory.setItem(slot, button(material, name, unlockedLore));
            return;
        }
        inventory.setItem(slot, button(
                Material.GRAY_DYE,
                "§8" + strip(name),
                "§7Locked",
                "",
                lockedHint,
                "§8It will make sense later."
        ));
    }

    private static String strip(String name) {
        return name == null ? "" : name.replaceAll("§.", "");
    }

    private void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private ItemStack button(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
