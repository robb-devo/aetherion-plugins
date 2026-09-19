package de.aetherion.items.menu.dev;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DevBridges {

    record NamedItem(String id, String name, ItemStack icon, String bossId) {
        NamedItem(String id, String name, ItemStack icon) {
            this(id, name, icon, id);
        }
    }

    static List<NamedItem> bossItems(boolean anchors) {
        List<NamedItem> items = new ArrayList<>();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (plugin == null || !plugin.isEnabled()) {
            return items;
        }
        try {
            Object service = plugin.getClass().getMethod("getSpawnItemService").invoke(plugin);
            Collection<?> all = (Collection<?>) service.getClass().getMethod("getAll").invoke(service);
            for (Object definition : all) {
                Object mode = definition.getClass().getMethod("getMode").invoke(definition);
                boolean spawn = mode != null && mode.toString().contains("SET_SPAWN");
                if (anchors != spawn) {
                    continue;
                }
                String id = String.valueOf(definition.getClass().getMethod("getId").invoke(definition));
                String bossId = String.valueOf(definition.getClass().getMethod("getBossId").invoke(definition));
                ItemStack stack = null;
                for (java.lang.reflect.Method method : service.getClass().getMethods()) {
                    if ("create".equals(method.getName()) && method.getParameterCount() == 1) {
                        stack = (ItemStack) method.invoke(service, definition);
                        break;
                    }
                }
                if (stack == null) {
                    continue;
                }
                String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                        ? stack.getItemMeta().getDisplayName()
                        : id;
                items.add(new NamedItem(id, name, stack, bossId));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        items.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return items;
    }

    static List<NamedItem> npcs() {
        List<NamedItem> items = new ArrayList<>();
        items.add(new NamedItem("trader", "§6Trader", de.aetherion.items.economy.TraderService.createAnchor()));
        items.add(new NamedItem("gear_trader", "§6Gear Trader", de.aetherion.items.economy.GearTraderService.createAnchor()));
        items.add(new NamedItem("fence", "§6Silas Markup", de.aetherion.items.economy.FenceService.createAnchor()));
        items.add(new NamedItem("liquidator", "§bCrystal Liquidator", de.aetherion.items.economy.LiquidatorService.createAnchor()));
        items.add(new NamedItem("bazaar", "§6Bazaar", de.aetherion.items.economy.MarketService.createBazaarAnchor()));
        items.add(new NamedItem("auction", "§eAuction House", de.aetherion.items.economy.MarketService.createAuctionAnchor()));
        items.add(new NamedItem("casino", "§6Lucky Vince", de.aetherion.items.casino.CasinoService.createAnchor()));
        items.add(new NamedItem("slot_machine", "§eSlot Machine", de.aetherion.items.casino.CasinoCabinet.createAnchor()));
        items.add(new NamedItem("roulette_table", "§cRoulette Table", de.aetherion.items.casino.RouletteCabinet.createAnchor()));
        items.add(new NamedItem("millstone", "§eMillstone", de.aetherion.items.farm.MillstoneCabinet.createAnchor()));
        items.add(new NamedItem("millstone_v2", "§6Millstone 2.0", de.aetherion.items.farm.MillstoneWindmill.createAnchor()));
        if (Bukkit.getPluginManager().getPlugin("AetherionQuests") != null) {
            try {
                Class<?> registry = Class.forName("de.aetherion.quests.npc.QuestNPCRegistry");
                Class<?> anchor = Class.forName("de.aetherion.quests.listener.NpcAnchorListener");
                Object map = registry.getMethod("getAll").invoke(null);
                if (map instanceof java.util.Map<?, ?> npcs) {
                    Method create = anchor.getMethod("create", String.class);
                    for (Object npc : npcs.values()) {
                        String id = String.valueOf(npc.getClass().getMethod("getId").invoke(npc));
                        String name = String.valueOf(npc.getClass().getMethod("getName").invoke(npc));
                        String questId = String.valueOf(npc.getClass().getMethod("getQuestId").invoke(npc));
                        Object type = npc.getClass().getMethod("getType").invoke(npc);
                        ItemStack icon = (ItemStack) create.invoke(null, id);
                        if (icon == null) {
                            icon = new ItemStack(Material.VILLAGER_SPAWN_EGG);
                        }
                        Object entityId = npc.getClass().getMethod("getEntityId").invoke(npc);
                        boolean flavor = (questId == null || questId.isBlank() || "null".equalsIgnoreCase(questId))
                                || (type != null && "FLAVOR".equalsIgnoreCase(type.toString()));
                        String boss = linkedBoss(id);
                        String online = entityId == null ? " §8(offline)" : " §a●";
                        String label;
                        if (flavor) {
                            label = "§7" + name + " §8· flavor" + online;
                        } else if (boss == null) {
                            label = "§b" + name + online;
                        } else {
                            label = "§b" + name + " §8· §7" + boss + online;
                        }
                        items.add(new NamedItem(id, label, icon));
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (dungeons != null && dungeons.isEnabled()) {
            try {
                ItemStack icon = (ItemStack) dungeons.getClass()
                        .getMethod("createDungeonKeeperAnchor")
                        .invoke(dungeons);
                if (icon != null) {
                    items.add(new NamedItem("dungeon_keeper", "§5Dungeon Keeper", icon));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Plugin mining = Bukkit.getPluginManager().getPlugin("AetherionMining");
        if (mining != null && mining.isEnabled()) {
            try {
                Class<?> veins = Class.forName("de.aetherion.mining.veins.VeinsNpcs");
                ItemStack icon = (ItemStack) veins.getMethod("anchor").invoke(null);
                if (icon != null) {
                    items.add(new NamedItem("veins_foreman", "§6Foreman §8· §7The Veins", icon));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Plugin foraging = Bukkit.getPluginManager().getPlugin("AetherionForaging");
        if (foraging != null && foraging.isEnabled()) {
            try {
                Class<?> guide = Class.forName("de.aetherion.foraging.npc.IsleGuideNpc");
                ItemStack icon = (ItemStack) guide.getMethod("createAnchor").invoke(null);
                if (icon != null) {
                    items.add(new NamedItem("miss_canopy", "§aMiss Canopy §8· §7Foraging Teacher", icon));
                }
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Class<?> grove = Class.forName("de.aetherion.foraging.ritual.GroveRitualService");
                ItemStack icon = (ItemStack) grove.getMethod("createAnchor").invoke(null);
                if (icon != null) {
                    items.add(new NamedItem("grove_table", "§2Grove Enchanting Table", icon));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return items;
    }


    /** Dev menu buckets — unknown quest NPCs default to WORLD so new ones always show. */
    enum NpcBucket {
        SERVICE,
        STARTER,
        BOSS,
        WORLD
    }


    static NpcBucket npcBucket(String id) {
        if (id == null || id.isBlank()) {
            return NpcBucket.WORLD;
        }
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "trader", "gear_trader", "fence", "liquidator", "bazaar", "auction", "casino", "slot_machine",
                 "roulette_table", "dungeon_keeper", "veins_foreman", "grove_table" -> NpcBucket.SERVICE;
            case "quartermaster", "egon", "hunter", "lumberjack", "farmer",
                 "craftsman", "blacksmith", "collector", "fisher", "fisherman",
                 "merchant", "lark", "vex", "booster_tutor", "rite_keeper", "ledger", "rook",
                 "bar_whisper", "vince" -> NpcBucket.STARTER;
            case "miner", "chicken_keeper", "tollkeeper", "dockhand", "ash_scout",
                 "colossus_scholar", "veil_priest", "patch_intern", "void_janitor",
                 "fuse", "claims_adjuster", "repo_agent", "arena_proctor" -> NpcBucket.BOSS;
            case "foreman", "surveyor", "ore_ledger", "eldervale_welcome", "eldervale_upgrade",
                 "farm_isle_guide", "forage_pad_guide", "canopy_clerk", "isle_clerk", "dungeon_gate",
                 "miss_canopy" -> NpcBucket.WORLD;
            default -> NpcBucket.WORLD;
        };
    }


    static List<NamedItem> npcs(NpcBucket bucket) {
        List<NamedItem> filtered = new ArrayList<>();
        for (NamedItem entry : npcs()) {
            if (npcBucket(entry.id()) == bucket) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    static ItemStack merchantChest() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.listener.MerchantChestListener");
            return (ItemStack) type.getMethod("create").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static ItemStack exploreChest(String kind) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.listener.ExploreChestListener");
            return (ItemStack) type.getMethod("create", String.class).invoke(null, kind);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static List<NamedItem> pets() {
        List<NamedItem> items = new ArrayList<>();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            return items;
        }
        try {
            Object registry = plugin.getClass().getMethod("getPetRegistry").invoke(plugin);
            Collection<?> all = (Collection<?>) registry.getClass().getMethod("getAll").invoke(registry);
            Method headCreate = Class.forName("de.aetherion.aethermobs.pet.PetHead")
                    .getMethod("create", String.class);
            for (Object definition : all) {
                String id = String.valueOf(definition.getClass().getMethod("getId").invoke(definition));
                String name = String.valueOf(definition.getClass().getMethod("getDisplayName").invoke(definition));
                ItemStack icon;
                try {
                    icon = (ItemStack) headCreate.invoke(null, id);
                } catch (ReflectiveOperationException ignored) {
                    icon = new ItemStack(eggFor(id));
                }
                if (icon == null || icon.getType().isAir()) {
                    icon = new ItemStack(eggFor(id));
                }
                org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§d" + name);
                    meta.setLore(List.of(
                            "§7Left-click to spawn beside you.",
                            "§8Anywhere. Habitat ignored.",
                            "§7Right-click to add to collection."
                    ));
                    icon.setItemMeta(meta);
                }
                items.add(new NamedItem(id, "§d" + name, icon));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return items;
    }

    static boolean spawnPet(Player player, String petId) {
        return invokePet(player, petId, "spawnDevPet");
    }

    static boolean givePet(Player player, String petId) {
        return invokePet(player, petId, "giveDevPet");
    }

    static int unlockAllPets(Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled() || player == null) {
            return -1;
        }
        try {
            Object result = plugin.getClass()
                    .getMethod("unlockAllPetsDev", Player.class)
                    .invoke(plugin, player);
            if (result instanceof Integer count) {
                return count;
            }
            return 0;
        } catch (ReflectiveOperationException exception) {
            return -1;
        }
    }

    private static boolean invokePet(Player player, String petId, String method) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(plugin.getClass()
                    .getMethod(method, Player.class, String.class)
                    .invoke(plugin, player, petId));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    static ItemStack catchSphere(String id) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            return (ItemStack) plugin.getClass()
                    .getMethod("createDevCatchSphere", String.class)
                    .invoke(plugin, id);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    static ItemStack petExpTreat(int tier) {
        try {
            Class<?> type = Class.forName("de.aetherion.aethermobs.pet.PetExpTreat");
            return (ItemStack) type.getMethod("create", int.class).invoke(null, tier);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    static ItemStack homestead() {
        return homestead("harbour");
    }

    static ItemStack spawnAnchor(String spawnId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        String id = spawnId == null || spawnId.isBlank() ? "harbour" : spawnId;
        try {
            Object marker = plugin.getClass().getMethod("getHomesteadMarker").invoke(plugin);
            return (ItemStack) marker.getClass().getMethod("createAnchor", String.class).invoke(marker, id);
        } catch (ReflectiveOperationException exception) {
            return homestead(id);
        }
    }

    static ItemStack homestead(String spawnId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        String id = spawnId == null || spawnId.isBlank() ? "harbour" : spawnId;
        try {
            Object marker = plugin.getClass().getMethod("getHomesteadMarker").invoke(plugin);
            return (ItemStack) marker.getClass().getMethod("create", String.class).invoke(marker, id);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /** Origin-map teleports only — no old megamap camps. */
    private static final List<String> ORIGIN_SPAWN_IDS = List.of(
            "harbour",
            "ore_ridge",
            "mines",
            "capital",
            "forage_isle",
            "farm",
            "borderlands",
            "colosseum",
            "eldervale"
    );

    static List<NamedItem> spawnMarkers() {
        List<NamedItem> items = new ArrayList<>();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (plugin == null || !plugin.isEnabled()) {
            return items;
        }
        try {
            Object hub = plugin.getClass().getMethod("getHub").invoke(plugin);
            Object marker = plugin.getClass().getMethod("getHomesteadMarker").invoke(plugin);
            Method create = marker.getClass().getMethod("createAnchor", String.class);
            java.util.Map<String, String> names = new java.util.HashMap<>();
            Collection<?> spawns = (Collection<?>) hub.getClass().getMethod("spawns").invoke(hub);
            for (Object spawn : spawns) {
                String id = String.valueOf(spawn.getClass().getMethod("id").invoke(spawn));
                String name = String.valueOf(spawn.getClass().getMethod("displayName").invoke(spawn));
                if (id != null && !id.isBlank()) {
                    names.put(id.toLowerCase(java.util.Locale.ROOT), name);
                }
            }
            for (String id : ORIGIN_SPAWN_IDS) {
                String name = names.getOrDefault(id, id.replace('_', ' '));
                ItemStack icon = (ItemStack) create.invoke(marker, id);
                if (icon == null) {
                    icon = new ItemStack(Material.LODESTONE);
                }
                items.add(new NamedItem(id, spawnLabel(id, name), icon));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return items;
    }

    public static int unlockAllSpawns(Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (plugin == null || !plugin.isEnabled() || player == null) {
            return -1;
        }
        try {
            Object result = plugin.getClass()
                    .getMethod("unlockAllSpawns", Player.class)
                    .invoke(plugin, player);
            if (result instanceof Number number) {
                return number.intValue();
            }
            return 0;
        } catch (ReflectiveOperationException exception) {
            return -1;
        }
    }

    static boolean startDungeon(Player player, boolean bossOnly) {
        return startDungeon(player, bossOnly, 1);
    }

    static boolean startDungeon(Player player, boolean bossOnly, int floor) {
        Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (dungeons == null || !dungeons.isEnabled() || player == null) {
            return false;
        }
        try {
            dungeons.getClass()
                    .getMethod("startTest", Player.class, boolean.class, int.class)
                    .invoke(dungeons, player, bossOnly, floor);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            dungeons.getClass()
                    .getMethod("startTest", Player.class, boolean.class)
                    .invoke(dungeons, player, bossOnly);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static boolean despawnDungeonKeeper(Entity entity) {
        if (entity == null) {
            return false;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        boolean keeper = entity.getScoreboardTags().contains("dungeon_keeper");
        String name = entity.getCustomName();
        if (name != null && name.contains("Dungeon Keeper")) {
            keeper = true;
        }
        String tagged = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_npc"),
                PersistentDataType.STRING
        );
        if ("dungeon_keeper".equals(tagged)) {
            keeper = true;
        }
        if (!keeper) {
            return false;
        }
        try {
            plugin.getClass().getMethod("despawnDungeonKeeper").invoke(plugin);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static String despawnQuestNpc(Entity entity) {
        if (entity == null) {
            return null;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        String npcId = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "quest_npc"),
                PersistentDataType.STRING
        );
        if (npcId == null || npcId.isBlank()) {
            npcId = entity.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "quest_npc_name"),
                    PersistentDataType.STRING
            );
        }
        if (npcId == null || npcId.isBlank()) {
            npcId = entity.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "quest_marker_npc"),
                    PersistentDataType.STRING
            );
        }
        if (npcId == null || npcId.isBlank()) {
            try {
                Class<?> registry = Class.forName("de.aetherion.quests.npc.QuestNPCRegistry");
                Object npc = registry.getMethod("getNPCByEntityId", String.class)
                        .invoke(null, entity.getUniqueId().toString());
                if (npc != null) {
                    npcId = String.valueOf(npc.getClass().getMethod("getId").invoke(npc));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String name = npcId;
        try {
            Class<?> registry = Class.forName("de.aetherion.quests.npc.QuestNPCRegistry");
            Object npc = registry.getMethod("getNPC", String.class).invoke(null, npcId);
            if (npc != null) {
                name = String.valueOf(npc.getClass().getMethod("getName").invoke(npc));
            }
            Boolean ok = (Boolean) plugin.getClass()
                    .getMethod("despawnQuestNpc", String.class)
                    .invoke(plugin, npcId);
            if (!Boolean.TRUE.equals(ok)) {
                return null;
            }
            return name;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Material eggFor(String petId) {
        return switch (petId.toLowerCase()) {
            case "wolf" -> Material.WOLF_SPAWN_EGG;
            case "pig" -> Material.PIG_SPAWN_EGG;
            case "cow" -> Material.COW_SPAWN_EGG;
            case "bat" -> Material.BAT_SPAWN_EGG;
            case "cavespider", "cave_spider" -> Material.CAVE_SPIDER_SPAWN_EGG;
            case "creeper" -> Material.CREEPER_SPAWN_EGG;
            case "zombie" -> Material.ZOMBIE_SPAWN_EGG;
            case "skeleton" -> Material.SKELETON_SPAWN_EGG;
            case "squid" -> Material.SQUID_SPAWN_EGG;
            case "glowsquid", "glow_squid" -> Material.GLOW_SQUID_SPAWN_EGG;
            case "axolotl" -> Material.AXOLOTL_SPAWN_EGG;
            case "guardian" -> Material.GUARDIAN_SPAWN_EGG;
            case "dolphin" -> Material.DOLPHIN_SPAWN_EGG;
            case "cod" -> Material.COD_SPAWN_EGG;
            case "salmon" -> Material.SALMON_SPAWN_EGG;
            case "pufferfish" -> Material.PUFFERFISH_SPAWN_EGG;
            case "tropical_fish", "tropicalfish" -> Material.TROPICAL_FISH_SPAWN_EGG;
            case "ocelot" -> Material.OCELOT_SPAWN_EGG;
            case "parrot" -> Material.PARROT_SPAWN_EGG;
            case "wither" -> Material.WITHER_SKELETON_SPAWN_EGG;
            case "hawk", "pigeon", "bee", "owl", "butterfly", "bloom_fairy" -> Material.PARROT_SPAWN_EGG;
            case "frog" -> Material.FROG_SPAWN_EGG;
            case "cat" -> Material.CAT_SPAWN_EGG;
            case "sheep" -> Material.SHEEP_SPAWN_EGG;
            case "mooshroom", "mycelord" -> Material.MOOSHROOM_SPAWN_EGG;
            case "sniffer" -> Material.SNIFFER_SPAWN_EGG;
            case "iron_golem" -> Material.IRON_GOLEM_SPAWN_EGG;
            case "witch", "swamp_hag" -> Material.WITCH_SPAWN_EGG;
            case "sand_wraith" -> Material.HUSK_SPAWN_EGG;
            case "forest_spirit", "lush_oracle" -> Material.ALLAY_SPAWN_EGG;
            case "aetherion" -> Material.DRAGON_EGG;
            case "dungeon_dragon" -> Material.PHANTOM_SPAWN_EGG;
            case "dungeon_zombie" -> Material.ZOMBIE_SPAWN_EGG;
            case "dungeon_skeleton" -> Material.SKELETON_SPAWN_EGG;
            default -> Material.GHAST_SPAWN_EGG;
        };
    }

    private static String linkedBoss(String npcId) {
        if (npcId == null) {
            return null;
        }
        return switch (npcId.toLowerCase(java.util.Locale.ROOT)) {
            case "miner" -> "Hollow Lurker";
            case "chicken_keeper" -> "McNugget";
            case "tollkeeper" -> "Bridge Troll";
            case "dockhand" -> "Squidward";
            case "ash_scout" -> "Skuldugery";
            case "colossus_scholar" -> "Aether Colossus";
            case "veil_priest" -> "Aetherion";
            case "patch_intern" -> "Sir Balthazar";
            case "void_janitor" -> "The Lobby Cleaner";
            case "fuse" -> "Sparky";
            case "claims_adjuster" -> "Baron von Wurm";
            case "repo_agent" -> "The Insolvent Wither";
            default -> null;
        };
    }

    private static String spawnLabel(String id, String name) {
        String extra = switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "farm" -> "Clucksworth · McNugget";
            case "mines" -> "Mine approach · Ore Ridge beyond";
            case "forage_isle" -> "Chop loops · small wooded island";
            case "ore_ridge" -> "Surface coal hill near harbour";
            case "harbour" -> "Brine · Squidward";
            case "capital" -> "Cinder · Skuldugery";
            case "colosseum" -> "Proctor · Crypt T2 ring";
            case "borderlands" -> "Beyond Vex's gate";
            case "eldervale" -> "Mining island · slime jump";
            default -> null;
        };
        if (extra == null) {
            return "§6" + name;
        }
        return "§6" + name + " §8· §7" + extra;
    }

    static boolean farmPortalAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionFarming");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            Object ok = api.getMethod("available").invoke(null);
            return ok instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static String farmPortalEnsure(boolean forceRebuild) {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            Object result = api.getMethod("ensureIsland", boolean.class).invoke(null, forceRebuild);
            return result == null ? "§cNo response." : String.valueOf(result);
        } catch (ReflectiveOperationException exception) {
            return "§cAetherionFarming portal API missing.";
        }
    }

    static ItemStack farmPortalTool() {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            return (ItemStack) api.getMethod("createHubPortalTool").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static void farmPortalSetIslandExit(Player player) {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            api.getMethod("setIslandExitHere", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException exception) {
            player.sendMessage("§cAetherionFarming portal API missing.");
        }
    }

    static void farmPortalTeleport(Player player) {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            api.getMethod("teleportToIsland", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException exception) {
            player.sendMessage("§cAetherionFarming portal API missing.");
        }
    }

    static String farmPortalRefreshAmbience() {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            Object result = api.getMethod("refreshAmbience").invoke(null);
            return result == null ? "§cNo response." : String.valueOf(result);
        } catch (ReflectiveOperationException exception) {
            return "§cAetherionFarming portal API missing.";
        }
    }

    static String farmPortalStatus() {
        try {
            Class<?> api = Class.forName("de.aetherion.farming.portal.FarmPortalAPI");
            Object result = api.getMethod("statusLine").invoke(null);
            return result == null ? "§7No status." : String.valueOf(result);
        } catch (ReflectiveOperationException ignored) {
            return "§cAetherionFarming offline";
        }
    }

    /** Current forage-isle weather line for DEV header. */
    static String isleWeatherStatus(Player player) {
        Plugin foraging = Bukkit.getPluginManager().getPlugin("AetherionForaging");
        if (foraging == null || !foraging.isEnabled() || player == null) {
            return "§cAetherionForaging offline";
        }
        try {
            Class<?> hook = Class.forName("de.aetherion.foraging.weather.FishingWeatherHook");
            Object state = hook.getMethod("state", Player.class).invoke(null, player);
            if (state == null) {
                return "§7No weather state";
            }
            Object kind = state.getClass().getMethod("kind").invoke(state);
            Object habitat = state.getClass().getMethod("habitat").invoke(state);
            Object source = state.getClass().getMethod("source").invoke(state);
            Object phase = state.getClass().getMethod("phase").invoke(state);
            return "§7Now §f" + kind
                    + " §8· §7Area §f" + habitat
                    + " §8· §7" + source
                    + " §8· §7" + phase;
        } catch (ReflectiveOperationException exception) {
            return "§cWeather bridge failed";
        }
    }

    /**
     * Temporary player-local weather override (~seconds), then ambient cycle resumes.
     * @return true if applied
     */
    static boolean isleWeatherForce(Player player, String kind, int seconds) {
        Plugin foraging = Bukkit.getPluginManager().getPlugin("AetherionForaging");
        if (foraging == null || !foraging.isEnabled() || player == null || kind == null) {
            return false;
        }
        try {
            Object plugin = foraging;
            Object weather = plugin.getClass().getMethod("weather").invoke(plugin);
            if (weather == null) {
                return false;
            }
            Class<?> kindClass = Class.forName("de.aetherion.foraging.weather.WeatherKind");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object kindEnum = Enum.valueOf((Class<? extends Enum>) kindClass, kind.trim().toUpperCase(java.util.Locale.ROOT));
            weather.getClass()
                    .getMethod("setRitualOverride", Player.class, kindClass, int.class)
                    .invoke(weather, player, kindEnum, Math.max(10, seconds));
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isleWeatherClear(Player player) {
        Plugin foraging = Bukkit.getPluginManager().getPlugin("AetherionForaging");
        if (foraging == null || !foraging.isEnabled() || player == null) {
            return false;
        }
        try {
            Object weather = foraging.getClass().getMethod("weather").invoke(foraging);
            if (weather == null) {
                return false;
            }
            weather.getClass().getMethod("clearRitualOverride", Player.class).invoke(weather, player);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
