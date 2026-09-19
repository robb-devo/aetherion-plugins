package de.aetherion.core.api;

/**
 * Soft-dep service registry. Plugins register on enable and clear on disable.
 * Prefer this over reflection bridges for cross-plugin calls.
 */
public final class AetherServices {

    private static volatile PartyAccess party;
    private static volatile BossSpawnAccess bosses;
    private static volatile ItemFactoryAccess items;

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

    public static PartyAccess party() {
        return party;
    }

    public static BossSpawnAccess bosses() {
        return bosses;
    }

    public static ItemFactoryAccess items() {
        return items;
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
}
