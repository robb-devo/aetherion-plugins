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

import java.util.List;
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
    /** Slots of the lock that opens next — set per open() (main thread only). */
    private final java.util.Set<Integer> nextSlots = new java.util.HashSet<>();

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

        // Early on most of this is locked. Say how far along you are, light up the one
        // lock that opens next, and let the far-future ones (journal, island, guild) recede.
        List<Gate> gates = gates(player, progress);
        nextSlots.clear();
        int open = 0;
        Gate next = null;
        for (Gate gate : gates) {
            if (gate.open()) {
                open++;
            } else if (next == null) {
                next = gate;
            }
        }
        if (next != null) {
            for (Gate gate : gates) {
                if (!gate.open() && gate.group().equals(next.group())) {
                    nextSlots.add(gate.slot());
                }
            }
        }
        List<String> headerLore = new java.util.ArrayList<>();
        headerLore.add("§7Your hub. It grows as you do.");
        if (!gates.isEmpty()) {
            headerLore.add("");
            headerLore.add("§7Open: §f" + open + "§7/§f" + gates.size() + "  " + bar(open, gates.size()));
            if (next != null) {
                headerLore.add("§7Next: §e" + next.name());
                headerLore.add(next.hint());
            }
        }
        inventory.setItem(4, button(
                Material.NETHER_STAR,
                "§6Aetherion Manager",
                headerLore.toArray(String[]::new)
        ));

        inventory.setItem(SHOP_SLOT, button(
                Material.AMETHYST_SHARD,
                "§bAether Shop",
                "§7Always open.",
                "§7Spend Aether Crystals.",
                "",
                "§eClick to open"
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
                "§7nine tiers each, plus leaderboards.",
                codexLine(player, de.aetherion.items.codex.CodexBook.Ledger.BESTIARY),
                "§8/bestiary");
        put(inventory, COLLECTION_SLOT, progress != null && progress.collection(player),
                Material.IRON_PICKAXE, "§aCollection",
                progress == null ? "§7Break one block. Paperwork follows." : progress.collectionHint(),
                "§7Ores, wood, crops, catches and",
                "§7more — nine tiers each.",
                codexLine(player, de.aetherion.items.codex.CodexBook.Ledger.COLLECTION),
                "§8/collection · /codex");
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
                "§7can drop. One page per boss.",
                "§8/codex journal");
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
                "§7and friend visits.",
                "§8Opens in chat.");
        put(inventory, GUILD_SLOT, guildPlugin && (progress == null || progress.guild(player)),
                Material.YELLOW_BANNER, "§6Guild",
                !guildPlugin ? "§7Requires AetherionGuilds."
                        : (progress == null ? "§7Level 75. Mid–late game club." : progress.guildHint()),
                "§7Shared island, invites, ranks",
                "§7and guild quarries.",
                "§8Opens in chat.");
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

        inventory.setItem(4, header(player, inventory));

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
    private ItemStack header(Player player, Inventory inventory) {
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
                "§6" + player.getName(),
                profileLines(player, open)
        );
    }

    private String[] profileLines(Player player, int open) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getSkills() != null) {
            var skills = plugin.getSkills();
            int level = skills.accountLevel(player);
            lines.add(de.aetherion.items.skill.AetherionLevel.coloredTitle(level)
                    + " §8· " + de.aetherion.items.skill.AetherionLevel.tag(level));
            lines.add(de.aetherion.items.skill.AetherionLevel.bar(skills.accountXp(player)));
            java.util.List<de.aetherion.items.skill.AetherSkill> equipped = skills.equipped(player);
            if (equipped.isEmpty()) {
                lines.add("§7Skills: §8none equipped");
            } else if (equipped.size() == 1) {
                lines.add("§7Skill: §f" + equipped.get(0).displayName());
            } else {
                lines.add("§7Skills: §f" + equipped.size() + " equipped");
            }
        }
        if (plugin != null && plugin.getShards() != null) {
            lines.add("§7Crystals: §b" + plugin.getShards().formatted(player));
        }
        lines.add("§7Pet: §f" + petLine(player));
        lines.add("");
        lines.add("§7Tabs open: §a" + open + "§7/§f" + TAB_SLOTS.length);
        return lines.toArray(String[]::new);
    }

    private static String petLine(Player player) {
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin == null || !plugin.isEnabled()) {
                return "unavailable";
            }
            Object manager = plugin.getClass().getMethod("getActivePetManager").invoke(plugin);
            if (manager == null) {
                return "none";
            }
            Object pet = manager.getClass().getMethod("getActivePet", Player.class).invoke(manager, player);
            if (pet == null) {
                return "none";
            }
            Object instance = pet.getClass().getMethod("getPetInstance").invoke(pet);
            if (instance == null) {
                return "equipped";
            }
            Object definition = instance.getClass().getMethod("getDefinition").invoke(instance);
            if (definition == null) {
                return "equipped";
            }
            Object name = definition.getClass().getMethod("getDisplayName").invoke(definition);
            int level = (int) instance.getClass().getMethod("getLevel").invoke(instance);
            return name + " §8Lv " + level;
        } catch (ReflectiveOperationException ignored) {
            return "none";
        }
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
        Material material = Material.COMPASS;
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
            if (pulseBright) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
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
        Material material = Material.KNOWLEDGE_BOOK;
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
            if (pulseBright) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
        Material material = Material.LEAD;
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
            if (pulseBright) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
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
        Material material = Material.NETHERITE_SCRAP;
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
            if (pulseBright) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** "Level 12 · 3 to claim" for a Codex ledger button. */
    private static String codexLine(Player player, de.aetherion.items.codex.CodexBook.Ledger ledger) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCodex() == null) {
            return "";
        }
        var summary = de.aetherion.items.codex.CodexBook.summary(plugin.getCodex(), player, ledger);
        return "§7Level §f" + summary.level()
                + (summary.claimableTiers() > 0 ? " §8· §e✦ " + summary.claimableTiers() + " to claim" : "");
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
        if (nextSlots.contains(slot)) {
            // The one lock worth reading right now.
            ItemStack next = button(
                    Material.LIME_DYE,
                    "§e" + strip(name) + " §8· next",
                    "§e➜ Opens next",
                    "",
                    lockedHint
            );
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                next.setItemMeta(meta);
            }
            inventory.setItem(slot, next);
            return;
        }
        if (slot == JOURNAL_SLOT || slot == ISLAND_SLOT || slot == GUILD_SLOT) {
            // Far future: a quiet silhouette, not another grey "nope".
            inventory.setItem(slot, button(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "§8" + strip(name) + " · later",
                    lockedHint
            ));
            return;
        }
        inventory.setItem(slot, button(
                Material.GRAY_DYE,
                "§8" + strip(name),
                "§7Locked",
                "",
                lockedHint
        ));
    }

    /** One lockable tab: its slot, whether it's open, the unlock it belongs to, and how to open it. */
    private record Gate(int slot, boolean open, String group, String name, String hint) {
    }

    /**
     * Lockable tabs in the order a new player actually opens them (the harbour spine first,
     * mid–late game last). Tabs whose plugin is missing are left out, not counted as locked.
     */
    private List<Gate> gates(Player player, ProgressionService progress) {
        List<Gate> gates = new java.util.ArrayList<>();
        if (progress == null || player == null) {
            return gates;
        }
        String workbench = progress.hint(ProgressionService.Flag.WORKBENCH);
        gates.add(new Gate(CRAFT_SLOT, progress.craftingTable(player), "workbench", "Crafting + Recipes", workbench));
        gates.add(new Gate(RECIPE_SLOT, progress.recipeBook(player), "workbench", "Crafting + Recipes", workbench));
        gates.add(new Gate(ANVIL_SLOT, progress.anvil(player), "anvil", "Anvil",
                progress.hint(ProgressionService.Flag.ANVIL)));
        gates.add(new Gate(SKILLS_SLOT, progress.skills(player), "skills", "Skills",
                progress.hint(ProgressionService.Flag.SKILLS)));
        if (manager.hasPetMenu()) {
            gates.add(new Gate(PETS_SLOT, progress.pets(player), "pets", "Pets",
                    progress.hint(ProgressionService.Flag.PETS)));
        }
        if (Bukkit.getPluginManager().isPluginEnabled("AetherionHub")) {
            gates.add(new Gate(SPAWN_SLOT, progress.spawns(player), "spawns", "Spawns",
                    progress.hint(ProgressionService.Flag.SPAWN_UNLOCKER)));
        }
        String trader = progress.hint(ProgressionService.Flag.TRADER);
        gates.add(new Gate(BAZAAR_SLOT, progress.bazaar(player), "trader", "Bazaar + Auction House", trader));
        gates.add(new Gate(AUCTION_SLOT, progress.auction(player), "trader", "Bazaar + Auction House", trader));
        gates.add(new Gate(COLLECTION_SLOT, progress.collection(player), "collection", "Collection",
                progress.collectionHint()));
        gates.add(new Gate(BESTIARY_SLOT, progress.bestiary(player), "bestiary", "Bestiary",
                progress.bestiaryHint()));
        gates.add(new Gate(JOURNAL_SLOT, progress.journal(player), "journal", "Dungeon Journal",
                progress.journalHint()));
        if (Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
            gates.add(new Gate(ISLAND_SLOT, progress.island(player), "island", "Island", progress.islandHint()));
            gates.add(new Gate(GUILD_SLOT, progress.guild(player), "guild", "Guild", progress.guildHint()));
        }
        return gates;
    }

    private static String bar(int have, int of) {
        int cells = 10;
        int filled = of <= 0 ? 0 : (int) Math.round(cells * (have / (double) of));
        StringBuilder out = new StringBuilder("§a");
        for (int i = 0; i < cells; i++) {
            if (i == filled) {
                out.append("§8");
            }
            out.append('▮');
        }
        return out.toString();
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
        // Dark top and bottom rails frame the tabs so the page reads as a panel, not a pile of glass.
        ItemStack rail = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta railMeta = rail.getItemMeta();
        if (railMeta != null) {
            railMeta.setDisplayName(" ");
            rail.setItemMeta(railMeta);
        }

        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            boolean edge = slot < 9 || slot >= size - 9;
            inventory.setItem(slot, edge ? rail.clone() : pane.clone());
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
