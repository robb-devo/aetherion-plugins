package de.aetherion.items.menu.dev;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
        if (de.aetherion.core.api.AetherServices.quests() != null) {
            for (de.aetherion.core.api.QuestNpcInfo npc : de.aetherion.core.api.AetherServices.quests().npcs()) {
                String id = npc.id();
                String name = npc.name();
                String questId = npc.questId();
                String type = npc.type();
                ItemStack icon = npc.icon();
                if (icon == null) {
                    icon = new ItemStack(Material.VILLAGER_SPAWN_EGG);
                }
                String entityId = npc.entityId();
                boolean flavor = (questId == null || questId.isBlank() || "null".equalsIgnoreCase(questId))
                        || (type != null && "FLAVOR".equalsIgnoreCase(type));
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
        de.aetherion.core.api.DungeonAccess dungeons = de.aetherion.core.api.AetherServices.dungeons();
        if (dungeons != null) {
            ItemStack icon = dungeons.dungeonKeeperAnchor();
            if (icon != null) {
                items.add(new NamedItem("dungeon_keeper", "§5Dungeon Keeper", icon));
            }
        }
        de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
        if (mining != null) {
            ItemStack icon = mining.veinsForemanAnchor();
            if (icon != null) {
                items.add(new NamedItem("veins_foreman", "§6Foreman §8· §7The Veins", icon));
            }
        }
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (foraging != null) {
            ItemStack guide = foraging.isleGuideAnchor();
            if (guide != null) {
                items.add(new NamedItem("miss_canopy", "§aMiss Canopy §8· §7Foraging Teacher", guide));
            }
            ItemStack grove = foraging.groveAnchor();
            if (grove != null) {
                items.add(new NamedItem("grove_table", "§2Grove Enchanting Table", grove));
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
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        return quests == null ? null : quests.merchantChest();
    }

    static ItemStack exploreChest(String kind) {
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        return quests == null ? null : quests.exploreChest(kind);
    }

    static List<NamedItem> pets() {
        List<NamedItem> items = new ArrayList<>();
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        if (pets == null) {
            return items;
        }
        for (de.aetherion.core.api.PetCatalogItem pet : pets.pets()) {
            items.add(new NamedItem(pet.id(), pet.displayName(), pet.icon()));
        }
        return items;
    }

    static boolean spawnPet(Player player, String petId) {
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        return pets != null && pets.spawnDevPet(player, petId);
    }

    static boolean givePet(Player player, String petId) {
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        return pets != null && pets.giveDevPet(player, petId);
    }

    static int unlockAllPets(Player player) {
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        if (pets == null || player == null) {
            return -1;
        }
        return pets.unlockAllPetsDev(player);
    }

    static ItemStack catchSphere(String id) {
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        return pets == null ? null : pets.catchSphere(id);
    }

    static ItemStack petExpTreat(int tier) {
        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        return pets == null ? null : pets.petExpTreat(tier);
    }

    static ItemStack homestead() {
        return homestead("harbour");
    }

    static ItemStack spawnAnchor(String spawnId) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        String id = spawnId == null || spawnId.isBlank() ? "harbour" : spawnId;
        if (hub == null) {
            return null;
        }
        ItemStack anchor = hub.createAnchor(id);
        return anchor != null ? anchor : homestead(id);
    }

    static ItemStack homestead(String spawnId) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            return null;
        }
        String id = spawnId == null || spawnId.isBlank() ? "harbour" : spawnId;
        return hub.createUnlockItem(id);
    }

    /** Origin-map teleports only — no old megamap camps. */
    private static final List<String> ORIGIN_SPAWN_IDS = List.of(
            "harbour",
            "ore_ridge",
            "mines",
            "capital",
            "forage_isle",
            "farm",
            "farm_isle",
            "borderlands",
            "colosseum",
            "eldervale",
            "fishing"
    );

    static List<NamedItem> spawnMarkers() {
        List<NamedItem> items = new ArrayList<>();
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            return items;
        }
        java.util.Map<String, String> names = new java.util.HashMap<>();
        java.util.Map<String, ItemStack> icons = new java.util.HashMap<>();
        for (de.aetherion.core.api.HubSpawnInfo spawn : hub.spawns()) {
            if (spawn.id() != null && !spawn.id().isBlank()) {
                names.put(spawn.id().toLowerCase(java.util.Locale.ROOT), spawn.displayName());
                icons.put(spawn.id().toLowerCase(java.util.Locale.ROOT), spawn.anchor());
            }
        }
        for (String id : ORIGIN_SPAWN_IDS) {
            String name = names.getOrDefault(id, id.replace('_', ' '));
            ItemStack icon = icons.get(id);
            if (icon == null) {
                icon = hub.createAnchor(id);
            }
            if (icon == null) {
                icon = new ItemStack(Material.LODESTONE);
            }
            items.add(new NamedItem(id, spawnLabel(id, name), icon));
        }
        return items;
    }

    public static int unlockAllSpawns(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null || player == null) {
            return -1;
        }
        return hub.unlockAll(player);
    }

    static boolean startDungeon(Player player, boolean bossOnly) {
        return startDungeon(player, bossOnly, 1);
    }

    static boolean startDungeon(Player player, boolean bossOnly, int floor) {
        de.aetherion.core.api.DungeonAccess dungeons = de.aetherion.core.api.AetherServices.dungeons();
        return dungeons != null && dungeons.startTest(player, bossOnly, floor);
    }

    public static boolean despawnDungeonKeeper(Entity entity) {
        de.aetherion.core.api.DungeonAccess dungeons = de.aetherion.core.api.AetherServices.dungeons();
        return dungeons != null && dungeons.despawnKeeper(entity);
    }

    public static String despawnQuestNpc(Entity entity) {
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        return quests == null ? null : quests.despawnNpc(entity);
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
            case "farm_isle" -> "Shared fields · Millstone pantry";
            case "fishing" -> "Fishing Eldervale · north pad";
            default -> null;
        };
        if (extra == null) {
            return "§6" + name;
        }
        return "§6" + name + " §8· §7" + extra;
    }

    static boolean farmPortalAvailable() {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        return farming != null && farming.available();
    }

    static String farmPortalEnsure(boolean forceRebuild) {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        if (farming == null) {
            return "§cAetherionFarming portal API missing.";
        }
        String result = farming.ensureIsland(forceRebuild);
        return result == null ? "§cNo response." : result;
    }

    static ItemStack farmPortalTool() {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        return farming == null ? null : farming.hubPortalTool();
    }

    static void farmPortalSetIslandExit(Player player) {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        if (farming == null) {
            player.sendMessage("§cAetherionFarming portal API missing.");
            return;
        }
        farming.setIslandExitHere(player);
    }

    static void farmPortalTeleport(Player player) {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        if (farming == null) {
            player.sendMessage("§cAetherionFarming portal API missing.");
            return;
        }
        farming.teleportToIsland(player);
    }

    static String farmPortalRefreshAmbience() {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        if (farming == null) {
            return "§cAetherionFarming portal API missing.";
        }
        String result = farming.refreshAmbience();
        return result == null ? "§cNo response." : result;
    }

    static String farmPortalStatus() {
        de.aetherion.core.api.FarmAccess farming = de.aetherion.core.api.AetherServices.farming();
        if (farming == null) {
            return "§cAetherionFarming offline";
        }
        String result = farming.statusLine();
        return result == null ? "§7No status." : result;
    }

    /** Current forage-isle weather line for DEV header. */
    static String isleWeatherStatus(Player player) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (foraging == null || player == null) {
            return "§cAetherionForaging offline";
        }
        de.aetherion.core.api.ForageWeatherView state = foraging.weather(player);
        if (state == null) {
            return "§7No weather state";
        }
        return "§7Now §f" + state.kind()
                + " §8· §7Area §f" + state.habitat()
                + " §8· §7" + state.source()
                + " §8· §7" + state.phase();
    }

    /**
     * Temporary player-local weather override (~seconds), then ambient cycle resumes.
     * @return true if applied
     */
    static boolean isleWeatherForce(Player player, String kind, int seconds) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (foraging == null || player == null || kind == null) {
            return false;
        }
        return foraging.forceWeather(player, kind, seconds);
    }

    static boolean isleWeatherClear(Player player) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (foraging == null || player == null) {
            return false;
        }
        return foraging.clearWeather(player);
    }

    static de.aetherion.core.api.TestBotsAccess testBots() {
        return de.aetherion.core.api.AetherServices.testBots();
    }

    static String testBotsStart(String role, int count) {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        if (access == null) {
            return "§cAetherionStressBots is not loaded.";
        }
        return access.start(role, count);
    }

    static String testBotsStop(String role) {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        if (access == null) {
            return "§cAetherionStressBots is not loaded.";
        }
        return access.stop(role);
    }

    static String testBotsStopAll() {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        if (access == null) {
            return "§cAetherionStressBots is not loaded.";
        }
        return access.stopAll();
    }

    static int testBotsAdjust(String role, int delta) {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        if (access == null) {
            return 0;
        }
        return access.adjustDesired(role, delta);
    }

    static de.aetherion.core.api.TestBotReport testBotsReport() {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        return access == null ? null : access.report();
    }

    static String testBotsReportText() {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        if (access == null) {
            return "AetherionStressBots is not loaded.";
        }
        return access.reportText();
    }

    static de.aetherion.core.api.TestBotView testBot(String name) {
        de.aetherion.core.api.TestBotsAccess access = testBots();
        return access == null ? null : access.bot(name);
    }
}
