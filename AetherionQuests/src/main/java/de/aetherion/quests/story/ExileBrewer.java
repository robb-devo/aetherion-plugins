package de.aetherion.quests.story;

import de.aetherion.core.exile.ExileVials;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.economy.Signatures;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.talk.NpcDesk;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The exile loop (A-53, decision E-8). The Keeper of The Last Bell brews the boss vials: a few hand-earned finds
 * from the matching skill isle plus coins wake one of the four bosses (Hollow Sun · Mining Crystal Finds, Seraphine ·
 * Farming Prize Crops, Helios · Foraging Heartwood, Nihil · Fishing Trophy Fish). Each of the four leaves an Echo
 * with everyone who fought it; one Echo of each brews the vial for the Great Doors (Aetherion). Set pieces drop
 * without doubles (BossEngine {@code SetPieceLedger}).
 * <p>
 * Amounts live in {@code exile-loop.*} of the Quests config (defaults below when the live file lacks them).
 */
public final class ExileBrewer {

    /** One brew: the boss it wakes and which isle find it takes. */
    enum Brew {
        HOLLOW_SUN("hollow_sun", "Crystal Finds", "Kristallfunde", "Mining", NamedTextColor.GOLD,
                id -> Signatures.CRYSTAL_FIND.equals(id)),
        SERAPHINE("hanging_saint", "Prize Crops", "Preis-Ernten", "Farming", NamedTextColor.LIGHT_PURPLE,
                id -> Signatures.PRIZE_CROP.equals(id)),
        HELIOS("helios", "Heartwood", "Kernholz", "Foraging", NamedTextColor.YELLOW,
                id -> id.startsWith(Signatures.HEARTWOOD_PREFIX)),
        NIHIL("world_eater", "Trophy Fish", "Trophäenfische", "Fishing", NamedTextColor.DARK_GREEN,
                id -> Signatures.FISH_TROPHY.equals(id));

        final String boss;
        final String findEn;
        final String findDe;
        final String isle;
        final NamedTextColor color;
        final Predicate<String> find;

        Brew(String boss, String findEn, String findDe, String isle, NamedTextColor color, Predicate<String> find) {
            this.boss = boss;
            this.findEn = findEn;
            this.findDe = findDe;
            this.isle = isle;
            this.color = color;
            this.find = find;
        }

        ExileVials.Vial vial() {
            return ExileVials.byBoss(boss);
        }
    }

    /** BossEngine template ids whose fall leaves an Echo, by the vial boss they belong to. */
    private static final Map<String, String> ECHO_FROM = Map.of(
            "hollow_sun", "hollow_sun",
            "hanging_saint", "hanging_saint",
            "helios_requiem", "helios",
            "world_eater", "world_eater");

    private ExileBrewer() {
    }

    static int finds() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? 2 : Math.max(1, plugin.getConfig().getInt("exile-loop.vial-finds", 2));
    }

    static long coins() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? 5000L : Math.max(0L, plugin.getConfig().getLong("exile-loop.vial-coins", 5000L));
    }

    // ------------------------------------------------------------------ the Keeper's desk

    /** The Keeper's two brewing chips: what he brews, and the brewing itself. */
    static void desk(Player p, QuestNPC npc, NpcDesk d) {
        d.topic(LangPack.say(p, "exile_keeper.desk.vials.label", "Vials?"), NamedTextColor.GOLD,
                        LangPack.say(p, "exile_keeper.desk.vials.echo", "You brew the vials?"), () -> howLines(p))
                .action(LangPack.say(p, "exile_keeper.desk.brew.label", "Brew a vial"), NamedTextColor.LIGHT_PURPLE,
                        LangPack.say(p, "exile_keeper.desk.brew.echo", "Brew me a vial."), () -> brewMenu(p, npc));
    }

    private static String[] howLines(Player p) {
        return new String[] {
                LangPack.say(p, "exile_keeper.desk.vials.how",
                        "Finds from the right isle and some coin. Then I brew the vial.")
                        + " §8(" + finds() + "× · " + money(p, coins()) + ")",
                LangPack.say(p, "exile_keeper.desk.vials.list",
                        "Hollow Sun: Crystal Finds. Seraphine: Prize Crops. Helios: Heartwood. Nihil: Trophy Fish."),
                LangPack.say(p, "exile_keeper.desk.vials.echoes",
                        "Each of them leaves an Echo. Four different ones, and I brew the vial for the Great Doors.")
        };
    }

    /** Five chips: the four bosses and Aetherion. Each tells exactly what is still missing. */
    private static void brewMenu(Player p, QuestNPC npc) {
        NpcDesk d = NpcDesk.of(npc);
        for (Brew brew : Brew.values()) {
            ExileVials.Vial vial = brew.vial();
            if (vial == null) {
                continue;
            }
            String name = LangPack.german(p) ? vial.nameDe() : vial.nameEn();
            d.action(name, brew.color, LangPack.say(p, "exile_keeper.desk.brew.one", "That one."),
                    () -> brew(p, npc, brew));
        }
        d.action("Aetherion", NamedTextColor.DARK_PURPLE, LangPack.say(p, "exile_keeper.desk.brew.doors", "The Great Doors."),
                () -> brewFinale(p, npc));
        d.open(p, LangPack.say(p, "exile_keeper.desk.brew.which", "Which one? Mind, it's not cheap."));
    }

    private static void brew(Player p, QuestNPC npc, Brew brew) {
        ExileVials.Vial vial = brew.vial();
        CoinService wallet = wallet();
        if (vial == null || wallet == null) {
            LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.closed", "The kettle's cold today. Come back later."));
            return;
        }
        boolean de = LangPack.german(p);
        int need = finds();
        long cost = coins();
        int have = count(p.getInventory(), brew);
        long purse = wallet.get(p);
        if (have < need || purse < cost) {
            List<String> missing = new ArrayList<>();
            if (have < need) {
                missing.add((need - have) + "× " + (de ? brew.findDe : brew.findEn) + " §8(" + brew.isle + ")§f");
            }
            if (purse < cost) {
                missing.add(money(p, cost - purse));
            }
            LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.missing", "Not yet. Still short:")
                    + " §f" + String.join(" + ", missing));
            p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 0.6f);
            return;
        }
        if (!wallet.take(p, cost)) {
            LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.missing", "Not yet. Still short:")
                    + " §f" + money(p, cost));
            return;
        }
        take(p.getInventory(), brew, need);
        give(p, ExileVials.create(vial, 1, de));
        LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.done",
                "There. Don't drink it. Hold it at the site and right-click."));
        p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    private static void brewFinale(Player p, QuestNPC npc) {
        ExileVials.Vial doors = ExileVials.byBoss("aetherion");
        if (doors == null) {
            return;
        }
        boolean de = LangPack.german(p);
        List<String> missing = new ArrayList<>();
        for (ExileVials.Vial vial : ExileVials.echoBosses()) {
            if (countEchoes(p.getInventory(), vial.boss()) < 1) {
                missing.add(vial.color() + (de ? vial.nameDe() : vial.nameEn()) + "§f");
            }
        }
        if (!missing.isEmpty()) {
            LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.echoes_missing",
                    "Four different Echoes. You're missing:") + " §f" + String.join(", ", missing));
            p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 0.6f);
            return;
        }
        for (ExileVials.Vial vial : ExileVials.echoBosses()) {
            takeEcho(p.getInventory(), vial.boss());
        }
        give(p, ExileVials.create(doors, 1, de));
        LivingNpcProfile.say(p, npc, LangPack.say(p, "exile_keeper.brew.finale",
                "Four echoes, one vial. The Great Doors will hear this one. Top of the stair."));
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 0.8f);
        p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.9f);
    }

    // ------------------------------------------------------------------ echoes

    /**
     * BossEngine death (from {@code BossKillCredit}): everyone who hit one of the four exile bosses, and its killer,
     * gets that boss's Echo.
     */
    public static void onBossDeath(String bossId, Player killer, Set<UUID> damagers) {
        String boss = bossId == null ? null : ECHO_FROM.get(bossId.toLowerCase(Locale.ROOT));
        ExileVials.Vial vial = boss == null ? null : ExileVials.byBoss(boss);
        if (vial == null) {
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
        for (Player player : fighters) {
            boolean de = LangPack.german(player);
            give(player, ExileVials.createEcho(vial, 1, de));
            String name = vial.color() + (de ? vial.nameDe() : vial.nameEn());
            player.sendMessage(LangPack.ui(player, "exile_loop.echo", "§8Exile §8» §7An Echo of")
                    + " " + name + " §7" + LangPack.ui(player, "exile_loop.echo_tail",
                    "stays with you. Four different ones: the Keeper of The Last Bell brews the Great Doors' vial."));
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.35f, 1.6f);
        }
    }

    // ------------------------------------------------------------------ inventory

    private static boolean matches(ItemStack stack, Brew brew) {
        String id = Signatures.idOf(stack);
        return id != null && brew.find.test(id.toLowerCase(Locale.ROOT));
    }

    private static int count(PlayerInventory inventory, Brew brew) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, brew)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void take(PlayerInventory inventory, Brew brew, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack, brew)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    private static int countEchoes(PlayerInventory inventory, String boss) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (boss.equals(ExileVials.echoOf(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void takeEcho(PlayerInventory inventory, String boss) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (boss.equals(ExileVials.echoOf(stack))) {
                stack.setAmount(stack.getAmount() - 1);
                contents[slot] = stack.getAmount() <= 0 ? null : stack;
                inventory.setStorageContents(contents);
                return;
            }
        }
    }

    private static void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private static CoinService wallet() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null ? null : items.getCoins();
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static String money(Player p, long coins) {
        Locale locale = LangPack.german(p) ? Locale.GERMANY : Locale.ENGLISH;
        return String.format(locale, "%,d", coins) + (LangPack.german(p) ? " Münzen" : " coins");
    }
}
