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
    private static volatile MiningAccess mining;
    private static volatile DungeonAccess dungeons;

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

    public static void registerMining(MiningAccess access) {
        mining = access;
    }

    public static void registerDungeons(DungeonAccess access) {
        dungeons = access;
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

    public static MiningAccess mining() {
        return mining;
    }

    public static DungeonAccess dungeons() {
        return dungeons;
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

    public static void clearMining(MiningAccess access) {
        if (mining == access) {
            mining = null;
        }
    }

    public static void clearDungeons(DungeonAccess access) {
        if (dungeons == access) {
            dungeons = null;
        }
    }
}
