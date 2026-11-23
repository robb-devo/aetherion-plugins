package de.aetherion.quests.story;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A-54: the story finale's own share. Aetherion behind the Great Doors (BossEngine {@code dungeon_aetherion} in the
 * {@code ashen_void} on the exile backend) pays his items through BossEngine's rank loot in the live boss file
 * (armor piece, Void Stick, Terminus, ... by damage rank, checked live 2026-11-23). Ranks 4 and lower got nothing
 * there, and nobody was told how to come back. So every fighter also gets {@link #COINS} coins a few seconds after
 * the fall ("Take a piece of me"), and the first win (the story's end, before {@link StoryState#FINALE_WON}) says
 * how to open the doors again: his vial or an Aetherion Summons (A-55). Items stay with BossEngine, so nothing is
 * paid twice. Where AetherionDungeons runs, its Reliquary is the payout and this does nothing.
 */
public final class FinaleSpoils {

    static final String BOSS = "dungeon_aetherion";
    static final String WORLD = "ashen_void";
    static final long COINS = 10_000L;
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
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || fighters.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        List<UUID> first = new ArrayList<>();
        for (Player player : fighters) {
            ids.add(player.getUniqueId());
            if (!StoryState.hasPlayerFlag(player, StoryState.FINALE_WON)) {
                first.add(player.getUniqueId());
            }
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
        boolean german = LangPack.german(player);
        CoinAccess coins = AetherServices.coins();
        if (coins != null) {
            coins.add(player, COINS);
            player.sendMessage(german
                    ? "§5⚜ Aetherion §8» §6+" + String.format(Locale.GERMANY, "%,d", COINS) + " Coins §7für den Sieg."
                    : "§5⚜ Aetherion §8» §6+" + String.format(Locale.US, "%,d", COINS) + " coins §7for the win.");
        }
        if (firstWin) {
            player.sendMessage(german
                    ? "§8» §7Die Großen Tore bleiben jetzt zu. Wieder hinein: mit seiner Phiole oder einem §dAetherion Summons§7."
                    : "§8» §7The Great Doors stay shut now. To go back in: his vial or an §dAetherion Summons§7.");
        }
    }
}
