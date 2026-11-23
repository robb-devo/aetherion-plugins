package de.aetherion.quests.story;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A-54: the story finale pays. Aetherion behind the Great Doors (BossEngine {@code dungeon_aetherion} in the
 * {@code ashen_void} on the exile backend) used to leave only the Dungeons plugin's Reliquary chest, and Dungeons does
 * not run there, so nobody got anything. Now every fighter gets the spoils straight into the bag, a few seconds after
 * the fall, when Aetherion says "Take a piece of me":
 * <ul>
 *   <li>every win: {@link #COINS} coins, one Core III, two boosters and a {@link #ARMOR_CHANCE} roll for a piece of
 *       Aetherion's armor;</li>
 *   <li>the first win (the story's end, before {@link StoryState#FINALE_WON}): that armor piece for sure.</li>
 * </ul>
 * Where AetherionDungeons does run, its Reliquary stays the payout and this does nothing (no double pay).
 */
public final class FinaleSpoils {

    static final String BOSS = "dungeon_aetherion";
    static final String WORLD = "ashen_void";
    static final long COINS = 10_000L;
    static final double ARMOR_CHANCE = 0.08;
    /** Lands with "Fine. Take a piece of me." in the death scene. */
    private static final long DELAY_TICKS = 150L;

    private FinaleSpoils() {
    }

    /** Called from the BossEngine death credit before the story flags are set, so "first win" is still readable. */
    public static void onBossDeath(String bossId, World world, Player killer, Set<UUID> damagers) {
        if (bossId == null || !BOSS.equals(bossId.toLowerCase(Locale.ROOT))
                || world == null || !WORLD.equalsIgnoreCase(world.getName())) {
            return;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("AetherionDungeons")) {
            return;
        }
        Set<Player> fighters = new LinkedHashSet<>();
        if (killer != null) {
            fighters.add(killer);
        }
        for (UUID id : damagers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                fighters.add(player);
            }
        }
        List<UUID> first = new ArrayList<>();
        for (Player player : fighters) {
            if (!StoryState.hasPlayerFlag(player, StoryState.FINALE_WON)) {
                first.add(player.getUniqueId());
            }
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || fighters.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        for (Player player : fighters) {
            ids.add(player.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : ids) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    pay(player, first.contains(id));
                }
            }
        }, DELAY_TICKS);
    }

    private static void pay(Player player, boolean firstWin) {
        boolean de = LangPack.german(player);
        CoinAccess coins = AetherServices.coins();
        if (coins != null) {
            coins.add(player, COINS);
        }
        ItemFactoryAccess items = AetherServices.items();
        boolean armor = firstWin || ThreadLocalRandom.current().nextDouble() < ARMOR_CHANCE;
        if (items != null) {
            give(player, items.create("dungeon_core_3"));
            give(player, items.create("random_booster"));
            give(player, items.create("random_booster"));
            if (armor) {
                give(player, items.create("random_aetherion_armor"));
            }
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
        String coinText = de ? String.format(Locale.GERMANY, "%,d", COINS) + " Coins" : String.format(Locale.US, "%,d", COINS) + " coins";
        player.sendMessage(de
                ? "§5⚜ Aetherions Beute §8» §6" + coinText + "§7, ein §fDungeon-Kern III§7, zwei §fBooster"
                        + (armor ? "§7 und §dein Stück seiner Rüstung§7." : "§7.")
                : "§5⚜ Aetherion's spoils §8» §6" + coinText + "§7, a §fDungeon Core III§7, two §fboosters"
                        + (armor ? "§7 and §da piece of his armor§7." : "§7."));
        if (firstWin) {
            player.sendMessage(de
                    ? "§8» §7Die Großen Tore bleiben jetzt zu. Wieder hinein: mit seiner Phiole oder einem §dAetherion Summons§7."
                    : "§8» §7The Great Doors stay shut now. To go back in: his vial or an §dAetherion Summons§7.");
        }
    }

    private static void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}
