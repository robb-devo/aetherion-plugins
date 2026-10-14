package de.aetherion.core.api;

/**
 * Soft-dep service registry. Plugins register on enable and clear on disable.
 * Prefer this over reflection bridges for first-party cross-plugin calls.
 *
 * <p>FancyNpcs (and WorldEdit / DiscordSRV / TAB) stay on reflection — they are
 * external plugins without a Core contract.
 */
public final class AetherServices {

    private static volatile PartyAccess party;
    private static volatile BossSpawnAccess bosses;
    private static volatile ItemFactoryAccess items;
    private static volatile CoinAccess coins;
    private static volatile ProgressAccess progress;
    private static volatile QuestProgressAccess quests;
    private static volatile HubAccess hub;
    private static volatile PetAccess pets;
    private static volatile ForageAccess foraging;
    private static volatile FarmAccess farming;
    private static volatile FishAccess fishing;
    private static volatile MiningAccess mining;
    private static volatile HarvestAccess harvest;
    private static volatile DungeonAccess dungeons;
    private static volatile TestBotsAccess testBots;
    private static volatile TalkAccess talk;
    private static volatile StoryAccess story;
    private static volatile LangAccess lang;
    private static volatile CharacterTransport transport;
    private static volatile IslandAccess islands;
    private static final java.util.List<java.util.function.Consumer<org.bukkit.entity.Player>> LANG_LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private AetherServices() {
    }

    public static void registerParty(PartyAccess access) {
        party = access;
    }

    public static void registerBosses(BossSpawnAccess access) {
        bosses = access;
    }

    public static void registerItems(ItemFactoryAccess access) {
        items = access;
    }

    public static void registerCoins(CoinAccess access) {
        coins = access;
    }

    public static void registerProgress(ProgressAccess access) {
        progress = access;
    }

    public static void registerQuests(QuestProgressAccess access) {
        quests = access;
    }

    public static void registerHub(HubAccess access) {
        hub = access;
    }

    public static void registerPets(PetAccess access) {
        pets = access;
    }

    public static void registerForaging(ForageAccess access) {
        foraging = access;
    }

    public static void registerFarming(FarmAccess access) {
        farming = access;
    }

    public static void registerFishing(FishAccess access) {
        fishing = access;
    }

    public static void registerMining(MiningAccess access) {
        mining = access;
    }

    public static void registerHarvest(HarvestAccess access) {
        harvest = access;
    }

    public static void registerDungeons(DungeonAccess access) {
        dungeons = access;
    }

    public static void registerTestBots(TestBotsAccess access) {
        testBots = access;
    }

    public static void registerTalk(TalkAccess access) {
        talk = access;
    }

    public static void registerStory(StoryAccess access) {
        story = access;
    }

    public static void registerLang(LangAccess access) {
        lang = access;
    }

    /** AetherionDungeons on mmo-r / mmo-d. Core serves mmo-c / mmo-i itself when nothing registered. */
    public static void registerTransport(CharacterTransport access) {
        transport = access;
    }

    public static void registerIslands(IslandAccess access) {
        islands = access;
    }

    public static PartyAccess party() {
        return party;
    }

    public static BossSpawnAccess bosses() {
        return bosses;
    }

    public static ItemFactoryAccess items() {
        return items;
    }

    public static CoinAccess coins() {
        return coins;
    }

    public static ProgressAccess progress() {
        return progress;
    }

    public static QuestProgressAccess quests() {
        return quests;
    }

    public static HubAccess hub() {
        return hub;
    }

    public static PetAccess pets() {
        return pets;
    }

    public static ForageAccess foraging() {
        return foraging;
    }

    public static FarmAccess farming() {
        return farming;
    }

    public static FishAccess fishing() {
        return fishing;
    }

    public static MiningAccess mining() {
        return mining;
    }

    public static HarvestAccess harvest() {
        return harvest;
    }

    public static DungeonAccess dungeons() {
        return dungeons;
    }

    public static TestBotsAccess testBots() {
        return testBots;
    }

    /** In-world talk bubbles (AetherionQuests), or null. */
    public static TalkAccess talk() {
        return talk;
    }

    /** Main-story stage + flags (AetherionQuests), or null. */
    public static StoryAccess story() {
        return story;
    }

    /** Player-language preference (AetherionQuests), or null = everyone English. */
    public static LangAccess lang() {
        return lang;
    }

    /** Cross-backend character hop registered by a plugin (Dungeons), or null. */
    public static CharacterTransport transport() {
        return transport;
    }

    /** Island homes (AetherionGuilds, island backend only), or null. */
    public static IslandAccess islands() {
        return islands;
    }

    /** True when the player chose German and a language provider is registered. */
    public static boolean german(org.bukkit.entity.Player player) {
        LangAccess access = lang;
        return access != null && player != null && access.german(player);
    }

    /** Called by the language provider after a player's language changed (items re-localize). */
    public static void addLangListener(java.util.function.Consumer<org.bukkit.entity.Player> listener) {
        if (listener != null) {
            LANG_LISTENERS.add(listener);
        }
    }

    public static void removeLangListener(java.util.function.Consumer<org.bukkit.entity.Player> listener) {
        LANG_LISTENERS.remove(listener);
    }

    public static void notifyLangChanged(org.bukkit.entity.Player player) {
        for (java.util.function.Consumer<org.bukkit.entity.Player> listener : LANG_LISTENERS) {
            try {
                listener.accept(player);
            } catch (RuntimeException ignored) {
                // a listener must never break the language switch
            }
        }
    }

    public static void clearParty(PartyAccess access) {
        if (party == access) {
            party = null;
        }
    }

    public static void clearBosses(BossSpawnAccess access) {
        if (bosses == access) {
            bosses = null;
        }
    }

    public static void clearItems(ItemFactoryAccess access) {
        if (items == access) {
            items = null;
        }
    }

    public static void clearCoins(CoinAccess access) {
        if (coins == access) {
            coins = null;
        }
    }

    public static void clearProgress(ProgressAccess access) {
        if (progress == access) {
            progress = null;
        }
    }

    public static void clearQuests(QuestProgressAccess access) {
        if (quests == access) {
            quests = null;
        }
    }

    public static void clearHub(HubAccess access) {
        if (hub == access) {
            hub = null;
        }
    }

    public static void clearPets(PetAccess access) {
        if (pets == access) {
            pets = null;
        }
    }

    public static void clearForaging(ForageAccess access) {
        if (foraging == access) {
            foraging = null;
        }
    }

    public static void clearFarming(FarmAccess access) {
        if (farming == access) {
            farming = null;
        }
    }

    public static void clearFishing(FishAccess access) {
        if (fishing == access) {
            fishing = null;
        }
    }

    public static void clearMining(MiningAccess access) {
        if (mining == access) {
            mining = null;
        }
    }

    public static void clearHarvest(HarvestAccess access) {
        if (harvest == access) {
            harvest = null;
        }
    }

    public static void clearDungeons(DungeonAccess access) {
        if (dungeons == access) {
            dungeons = null;
        }
    }

    public static void clearTestBots(TestBotsAccess access) {
        if (testBots == access) {
            testBots = null;
        }
    }

    public static void clearTalk(TalkAccess access) {
        if (talk == access) {
            talk = null;
        }
    }

    public static void clearLang(LangAccess access) {
        if (lang == access) {
            lang = null;
        }
    }

    public static void clearStory(StoryAccess access) {
        if (story == access) {
            story = null;
        }
    }

    public static void clearTransport(CharacterTransport access) {
        if (transport == access) {
            transport = null;
        }
    }

    public static void clearIslands(IslandAccess access) {
        if (islands == access) {
            islands = null;
        }
    }
}
