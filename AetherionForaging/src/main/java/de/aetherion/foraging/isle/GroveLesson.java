package de.aetherion.foraging.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.npc.IsleGuideNpc;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Act IV · the Forage Isle lesson. A first visit is a short spine, not a desk:
 * <pre>
 *   land   ─ card + lesson bar (arrow, metres) to Miss Canopy
 *   hook   ─ her bubble: isle trees keep score · chips "Show me." / "What's in it?"
 *   do     ─ three isle fells in a row on the green — a miss resets the count, never the lesson
 *   find   ─ a Crown Shaker is armed: the next fell drops a Crown Find, catch it (re-armed if it wilts)
 *   close  ─ back to her: the skill in one sentence, coins + XP, "next" / "way home" chips → her plain desk
 * </pre>
 * The stage lives on the profile ({@code lesson}). Foragers who already worked the isle skip it. Done sends
 * {@code ISLE_LESSON_FORAGE} to Quests (the road picker ticks the road off).
 */
public final class GroveLesson {

    static final int NEW = 0;
    static final int MEET = 1;
    static final int FELL = 2;
    static final int FIND = 3;
    static final int RETURN = 4;
    static final int DONE = 5;

    private static final int STREAK_GOAL = 3;
    /** Isle fells that mark a forager who learned the isle before the lesson existed. */
    private static final int VETERAN_FELLS = 5;
    private static final long REWARD_COINS = 400L;
    private static final int REWARD_XP = 120;
    private static final int STREAK_XP = 60;
    private static final String DONE_NOTE = "ISLE_LESSON_FORAGE";

    private final ForageIsle isle;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Long> brokeUntil = new HashMap<>();
    private final Set<UUID> armed = new HashSet<>();

    GroveLesson(ForageIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ heartbeat (every 10 ticks)

    void tick(List<Player> onIsle) {
        Set<UUID> here = new HashSet<>();
        for (Player player : onIsle) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                continue;
            }
            here.add(player.getUniqueId());
            ForageProfile profile = isle.profiles().get(player);
            if (profile.lesson == NEW) {
                begin(player, profile);
            }
            if (profile.lesson == FIND) {
                keepArmed(player, profile);
            }
            paint(player, profile);
        }
        for (UUID id : List.copyOf(bars.keySet())) {
            if (!here.contains(id)) {
                hide(id);
            }
        }
    }

    private void begin(Player player, ForageProfile profile) {
        if (profile.totalFells >= VETERAN_FELLS) {
            profile.lesson = DONE;
            profile.dirty = true;
            return;
        }
        land(player, profile);
    }

    /** The landing beat: card, chat crumb, the bar comes up on the next paint. */
    private void land(Player player, ForageProfile profile) {
        profile.lesson = MEET;
        profile.dirty = true;
        // After the isle's own first-discovery card, not over it.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            ForageText.card(player, "§2§lFORAGING ELDERVALE",
                    t(player, "§7Seven forests. Every fell is a timing call.",
                            "§7Sieben Wälder. Jeder Fall ist eine Frage des Timings."), 60);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.1f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.sendMessage(t(player,
                    "§2❦ §fIsle lesson §8· §aMiss Canopy §7is waiting at the Landing — follow the bar up top.",
                    "§2❦ §fInsel-Lektion §8· §aMiss Canopy §7wartet am Anleger — folg der Leiste oben."));
        }, 50L);
        if (teacher() == null) {
            // No teacher placed: the lesson still runs, in chat.
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    hookInChat(player);
                }
            }, 70L);
        }
    }

    // ------------------------------------------------------------------ the teacher

    /** Miss Canopy was clicked. True when the lesson took the click (her desk stays out of it). */
    public boolean onTeacher(Player player) {
        if (player == null) {
            return false;
        }
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson == NEW && isle.onIsle(player.getLocation())) {
            begin(player, profile);
        }
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            return false;
        }
        IsleGuideNpc guide = guide();
        if (guide != null && guide.busy(player)) {
            return true;
        }
        switch (profile.lesson) {
            case MEET -> hook(player);
            case FELL -> remind(player, t(player,
                    "Three in a row, {player}. Green is clean. Miss and we count again.",
                    "Drei hintereinander, {player}. Grün ist sauber. Daneben, und wir zählen neu."));
            case FIND -> remind(player, t(player,
                    "One more tree. Look up when it goes — the crown drops something.",
                    "Noch ein Baum. Schau hoch, wenn er fällt — die Krone lässt was los."));
            case RETURN -> close(player);
            default -> {
            }
        }
        return true;
    }

    private void hook(Player player) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null && !quests.canChopTrees(player)) {
            speak(player, List.of(t(player,
                    "Harbour first, {player}. The Forager there teaches the bar — then come back to me.",
                    "Erst der Hafen, {player}. Der Holzfäller dort zeigt dir die Leiste — dann komm wieder.")), List.of());
            return;
        }
        List<String> lines = List.of(
                t(player, "Harbour trees forgive you, {player}. Isle trees keep score.",
                        "Hafenbäume verzeihen dir, {player}. Inselbäume zählen mit."),
                t(player, "Fell three in a row on the green. Miss once and the count starts over.",
                        "Fäll drei hintereinander im Grünen. Einmal daneben, und die Zählung beginnt von vorn."));
        List<TalkAccess.Reply> chips = List.of(
                new TalkAccess.Reply(t(player, "Show me.", "Zeig her."), NamedTextColor.GREEN,
                        () -> startFell(player), t(player, "Show me.", "Zeig her.")),
                new TalkAccess.Reply(t(player, "What's in it?", "Was bringt's?"), NamedTextColor.GRAY,
                        () -> stakes(player), t(player, "What's in it for me?", "Was hab ich davon?")));
        if (!speak(player, lines, chips)) {
            hookInChat(player);
        }
    }

    private void stakes(Player player) {
        List<String> lines = List.of(
                t(player, "Every clean fell in a row pays bonus XP. At five, the green gets wider.",
                        "Jeder saubere Fall in Folge bringt Bonus-XP. Ab fünf wird das Grün breiter."),
                t(player, "A miss costs the streak — and that trunk, for a few breaths. Other trees stay free.",
                        "Ein Fehlschlag kostet die Serie — und den Stamm für ein paar Atemzüge. Andere Bäume bleiben frei."));
        List<TalkAccess.Reply> chips = List.of(new TalkAccess.Reply(t(player, "Show me.", "Zeig her."),
                NamedTextColor.GREEN, () -> startFell(player), t(player, "Show me.", "Zeig her.")));
        if (!speak(player, lines, chips)) {
            startFell(player);
        }
    }

    /** Classic chat (or no teacher placed): the same beat as plain lines, then straight into the drill. */
    private void hookInChat(Player player) {
        chat(player, t(player, "Harbour trees forgive you, {player}. Isle trees keep score.",
                "Hafenbäume verzeihen dir, {player}. Inselbäume zählen mit."));
        chat(player, t(player, "Fell three in a row on the green. Miss once and the count starts over.",
                "Fäll drei hintereinander im Grünen. Einmal daneben, und die Zählung beginnt von vorn."));
        startFell(player);
    }

    private void startFell(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson != MEET) {
            return;
        }
        if (!hasAxe(player)) {
            ItemStack axe = simpleAxe();
            if (axe != null) {
                ForageBridge.give(player, axe);
                say(player, t(player, "No axe? Borrow mine. Bring it back sharp.",
                        "Keine Axt? Nimm meine. Bring sie scharf zurück."));
            }
        }
        profile.lesson = FELL;
        profile.dirty = true;
        streak.put(player.getUniqueId(), 0);
        player.playSound(player.getLocation(), Sound.ITEM_AXE_STRIP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        paint(player, profile);
    }

    private void remind(Player player, String line) {
        if (!speak(player, List.of(line), List.of())) {
            chat(player, line);
        }
    }

    private void close(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        profile.lesson = DONE;
        profile.dirty = true;
        armed.remove(player.getUniqueId());
        streak.remove(player.getUniqueId());
        hide(player.getUniqueId());
        ForageBridge.coins(player, REWARD_COINS);
        ForageBridge.bonus(player, REWARD_XP);
        ForageText.card(player, "§2§l" + t(player, "LESSON DONE", "LEKTION GESCHAFFT"),
                t(player, "§7Foraging §8· §6+" + ForageText.coins(REWARD_COINS) + " coins §8· §a+" + REWARD_XP + " XP",
                        "§7Holzsammeln §8· §6+" + ForageText.coins(REWARD_COINS) + " Münzen §8· §a+" + REWARD_XP + " XP"), 60);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteUsed(player, DONE_NOTE);
        }
        List<String> lines = List.of(
                t(player, "That's foraging here, {player}: a drumbeat. Green, green, green — and the forest pays the streak.",
                        "So geht Holzsammeln hier, {player}: ein Trommelschlag. Grün, grün, grün — und der Wald zahlt die Serie."),
                t(player, "Pell at the Landing buys wood by order — that's the daily money. The pad you landed on takes you home.",
                        "Pell am Anleger kauft Holz auf Bestellung — das ist das tägliche Geld. Das Sprungpad, auf dem du gelandet bist, bringt dich heim."));
        List<TalkAccess.Reply> chips = new ArrayList<>();
        chips.add(new TalkAccess.Reply(t(player, "Pell's orders", "Pells Aufträge"), NamedTextColor.GOLD,
                () -> pointPell(player), t(player, "Where's Pell?", "Wo ist Pell?")));
        chips.add(new TalkAccess.Reply(t(player, "Way home?", "Weg nach Hause?"), NamedTextColor.AQUA,
                () -> pointHome(player), t(player, "How do I get home?", "Wie komm ich heim?")));
        chips.add(new TalkAccess.Reply(t(player, "Bye.", "Tschüss."), NamedTextColor.DARK_GRAY,
                () -> say(player, t(player, "Off you go. The trees won't fell themselves.",
                        "Ab mit dir. Die Bäume fällen sich nicht von allein.")), t(player, "Bye.", "Tschüss.")));
        if (!speak(player, lines, chips)) {
            for (String line : lines) {
                chat(player, line);
            }
            pointPell(player);
        }
    }

    private void pointPell(Player player) {
        Location at = isle.compass().resolve(ForageCast.BOARD_CLERK, isle.isleWorld());
        if (at != null) {
            isle.compass().point(player, "Pell · Lumber Board", at);
        }
        say(player, t(player, "Arrow's on him. Orders refresh — check back every visit.",
                "Der Pfeil zeigt auf ihn. Aufträge kommen nach — schau jedes Mal vorbei."));
    }

    private void pointHome(Player player) {
        Landmark landing = isle.config().landmark("landing");
        Location at = landing == null ? null : landing.anchor(isle.isleWorld());
        if (at != null) {
            isle.compass().point(player, t(player, "Slime pad · home", "Sprungpad · heim"), at);
        }
        say(player, t(player, "Same pad you landed on. Or §e/capital§f from anywhere.",
                "Dasselbe Pad, auf dem du gelandet bist. Oder §e/capital§f von überall."));
    }

    // ------------------------------------------------------------------ the loop (hooks)

    /** One clean isle fell (ForageIsle routes it here). */
    void onFell(Player player, FellContext ctx) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson != FELL || ctx.cleaver()) {
            return;
        }
        int count = streak.merge(player.getUniqueId(), 1, Integer::sum);
        brokeUntil.remove(player.getUniqueId());
        float pitch = 1.0f + 0.25f * Math.min(STREAK_GOAL, count);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.7f, pitch);
        if (count < STREAK_GOAL) {
            paint(player, profile);
            return;
        }
        profile.lesson = FIND;
        profile.dirty = true;
        ForageBridge.bonus(player, STREAK_XP);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.8f, 1.5f);
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            ForageText.card(player, "§a§l" + t(player, "CLEAN ×3", "SAUBER ×3"),
                    t(player, "§7That's the stake. §fNext tree: watch the crown.",
                            "§7Das ist der Einsatz. §fNächster Baum: achte auf die Krone."), 50);
            arm(player, profile);
            paint(player, profile);
        }, 30L);
    }

    /** A missed fell bar on the isle. */
    public void onMiss(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson != FELL) {
            return;
        }
        Integer before = streak.put(player.getUniqueId(), 0);
        if (before != null && before > 0) {
            brokeUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3500L);
        }
        paint(player, profile);
    }

    /** A Crown Find was caught (mid-air or off the ground). */
    void onFind(Player player, boolean midAir) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson != FIND) {
            return;
        }
        armed.remove(player.getUniqueId());
        profile.lesson = RETURN;
        profile.dirty = true;
        ForageText.card(player, "§e§l✦ " + t(player, "CROWN FIND", "KRONENFUND"),
                midAir ? t(player, "§7Caught mid-air §8· §f+10% weight", "§7In der Luft gefangen §8· §f+10% Gewicht")
                        : t(player, "§7Juniper buys them · the Ledger files them", "§7Juniper kauft sie · das Register sammelt sie"),
                50);
        paint(player, profile);
    }

    /** The finder let a Crown Find wilt: the next fell drops another. */
    void onWilt(UUID finder) {
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        if (player == null) {
            return;
        }
        ForageProfile profile = isle.profiles().get(player);
        if (profile.lesson != FIND) {
            return;
        }
        armed.remove(finder);
        arm(player, profile);
        player.sendMessage(t(player, "§7It wilted. §fNext fell drops another — grab it this time.",
                "§7Verwelkt. §fDer nächste Fall lässt noch einen fallen — schnapp ihn dir diesmal."));
    }

    private void arm(Player player, ForageProfile profile) {
        if (armed.add(player.getUniqueId()) && profile.charge(Woodwright.CHARGE_SHAKER) <= 0) {
            profile.addCharge(Woodwright.CHARGE_SHAKER, 1);
        }
    }

    /** A charge spent on a fell that dropped nothing (no crown to fall from): arm again. */
    private void keepArmed(Player player, ForageProfile profile) {
        if (profile.charge(Woodwright.CHARGE_SHAKER) > 0 || isle.finds().hasDropFor(player.getUniqueId())) {
            return;
        }
        armed.remove(player.getUniqueId());
        arm(player, profile);
    }

    /** Pell, Tamsin and Juniper send lesson players back to Miss Canopy first. */
    public boolean deferring(Player player) {
        int stage = isle.profiles().get(player).lesson;
        return stage >= MEET && stage < DONE;
    }

    // ------------------------------------------------------------------ the bar

    private void paint(Player player, ForageProfile profile) {
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            hide(player.getUniqueId());
            return;
        }
        String text;
        float progress;
        BossBar.Color color = BossBar.Color.GREEN;
        String head = t(player, "§aIsle lesson §8· ", "§aInsel-Lektion §8· ");
        switch (profile.lesson) {
            case MEET, RETURN -> {
                Location at = teacher();
                String verb = profile.lesson == MEET ? t(player, "Meet", "Triff") : t(player, "Back to", "Zurück zu");
                if (at == null || at.getWorld() != player.getWorld()) {
                    text = head + "§f" + verb + " §aMiss Canopy";
                    progress = 0.1f;
                } else {
                    double d = player.getLocation().distance(at);
                    text = head + "§f" + verb + " §aMiss Canopy §8· §e" + ForageText.arrow(player, at) + " §7" + (int) d + "m";
                    progress = (float) Math.max(0.05d, Math.min(1.0d, 1.0d - d / 120.0d));
                }
            }
            case FELL -> {
                int count = streak.getOrDefault(player.getUniqueId(), 0);
                Long broke = brokeUntil.get(player.getUniqueId());
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < STREAK_GOAL; i++) {
                    dots.append(i < count ? "§a●" : "§8○");
                }
                if (broke != null && broke > System.currentTimeMillis()) {
                    text = head + t(player, "§cStreak broke §8· ", "§cSerie gerissen §8· ") + dots
                            + t(player, " §7other trees are free", " §7andere Bäume sind frei");
                    color = BossBar.Color.RED;
                } else {
                    text = head + t(player, "§fFell 3 isle trees in a row §8· ", "§fFäll 3 Inselbäume am Stück §8· ") + dots;
                }
                progress = Math.max(0.02f, count / (float) STREAK_GOAL);
            }
            case FIND -> {
                boolean falling = isle.finds().hasDropFor(player.getUniqueId());
                text = head + (falling
                        ? t(player, "§eIt's falling — §fcatch it!", "§eEs fällt — §ffang es!")
                        : t(player, "§fFell one more §8· §eWatch the crown", "§fNoch ein Baum §8· §eAchte auf die Krone"));
                progress = falling ? 0.9f : 0.7f;
                color = BossBar.Color.YELLOW;
            }
            default -> {
                hide(player.getUniqueId());
                return;
            }
        }
        String title = BarChrome.legacy(BarChrome.Family.QUEST, BarChrome.Icon.AXE, text);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(ForageText.legacy(title), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(ForageText.legacy(title));
        bar.progress(progress);
        bar.color(color);
    }

    private void hide(UUID id) {
        BossBar bar = bars.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }

    void forget(UUID id) {
        hide(id);
        streak.remove(id);
        brokeUntil.remove(id);
        armed.remove(id);
    }

    void shutdown() {
        for (UUID id : List.copyOf(bars.keySet())) {
            hide(id);
        }
    }

    /** DEV: run the lesson again from the landing beat (no veteran skip). */
    public void replay(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        forget(player.getUniqueId());
        land(player, profile);
    }

    /** DEV: mark it done without the walk. */
    public void skip(Player player) {
        ForageProfile profile = isle.profiles().get(player);
        profile.lesson = DONE;
        profile.dirty = true;
        forget(player.getUniqueId());
    }

    // ------------------------------------------------------------------ plumbing

    private Location teacher() {
        IsleGuideNpc guide = guide();
        return guide == null ? null : guide.location();
    }

    private static IsleGuideNpc guide() {
        AetherionForaging plugin = AetherionForaging.getInstance();
        return plugin == null ? null : plugin.guide();
    }

    /** Lines (and chips) in Miss Canopy's bubble. False when the bubble can't open. */
    private boolean speak(Player player, List<String> lines, List<TalkAccess.Reply> chips) {
        IsleGuideNpc guide = guide();
        return guide != null && guide.script(player, lines, chips);
    }

    /** One line: bubble when it's up, chat otherwise. */
    private void say(Player player, String line) {
        if (!speak(player, List.of(line), List.of())) {
            chat(player, line);
        }
    }

    private static void chat(Player player, String line) {
        player.sendMessage("§a" + IsleGuideNpc.DISPLAY + " §8» §f" + line.replace("{player}", player.getName()));
    }

    private static boolean hasAxe(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType().name().endsWith("_AXE")) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack simpleAxe() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null || items.getItemManager() == null ? null
                    : new CustomItem(items.getItemManager()).createSimpleAxe();
        } catch (LinkageError | RuntimeException ex) {
            return null;
        }
    }

    static String t(Player player, String en, String de) {
        return AetherServices.german(player) ? de : en;
    }
}
