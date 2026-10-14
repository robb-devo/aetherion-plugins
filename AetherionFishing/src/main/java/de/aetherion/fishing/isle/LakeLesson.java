package de.aetherion.fishing.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.fishing.FishingSkills;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.util.InventoryDrops;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Act VI · the Fishing Eldervale lesson. A first visit is a short spine, not a desk:
 * <pre>
 *   land   ─ card + lesson bar (arrow, metres) to Maren at the Harbourmaster's Hall
 *   hook   ─ her bubble: same bar as the harbour, but the water keeps count · "Show me." / "What's the count for?"
 *   do     ─ three isle catches in a row (Warm water) — a lost fish resets the count, never the lesson
 *   find   ─ the next bite is "Something heavy!": a rare with a narrow window, forced again until it's landed
 *   close  ─ back to Maren: the skill in one sentence, coins + XP, Odile's Log / way home chips
 * </pre>
 * No rod? Maren lends one. The stage lives on the angler profile ({@code lesson}); anglers who already fished
 * the isle skip it. Done sends {@code ISLE_LESSON_FISH} to Quests (the road picker ticks the road off).
 */
public final class LakeLesson {

    static final int NEW = 0;
    static final int MEET = 1;
    static final int CATCH = 2;
    static final int FIND = 3;
    static final int RETURN = 4;
    static final int DONE = 5;

    private static final int STREAK_GOAL = 3;
    private static final long VETERAN_CATCHES = 5L;
    private static final long REWARD_COINS = 400L;
    private static final int REWARD_XP = 120;
    private static final int WARM_XP = 60;
    private static final String DONE_NOTE = "ISLE_LESSON_FISH";

    private final FishIsle isle;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Long> brokeUntil = new HashMap<>();
    /** The rare each lesson angler is hunting (forced again after every loss). */
    private final Map<UUID, Species> quarry = new HashMap<>();
    private BukkitTask task;

    LakeLesson(FishIsle isle) {
        this.isle = isle;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 25L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : List.copyOf(bars.keySet())) {
            hide(id);
        }
    }

    // ------------------------------------------------------------------ heartbeat (every 10 ticks)

    private void tick() {
        Set<UUID> here = new HashSet<>();
        for (Player player : LakeWorld.visitors()) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            here.add(player.getUniqueId());
            AnglerProfiles.Profile profile = isle.profiles().of(player);
            if (profile.lesson == NEW) {
                begin(player, profile);
            }
            if (profile.lesson == FIND && !isle.line().forced(player.getUniqueId())) {
                // After a relog / restart: the hunted rare is on the next bite again.
                isle.line().force(player, quarry.computeIfAbsent(player.getUniqueId(), id -> pickRare(null)));
            }
            paint(player, profile);
        }
        for (UUID id : List.copyOf(bars.keySet())) {
            if (!here.contains(id)) {
                hide(id);
            }
        }
    }

    private void begin(Player player, AnglerProfiles.Profile profile) {
        if (profile.catches >= VETERAN_CATCHES) {
            profile.lesson = DONE;
            isle.profiles().markDirty();
            return;
        }
        land(player, profile);
    }

    private void land(Player player, AnglerProfiles.Profile profile) {
        profile.lesson = MEET;
        isle.profiles().markDirty();
        // After the isle's own first-discovery card, not over it.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            player.showTitle(Title.title(LakeText.legacy("§b§lFISHING ELDERVALE"),
                    LakeText.legacy(t(player, "§7Every water keeps its own fish — and its own count.",
                            "§7Jedes Gewässer hat seine eigenen Fische — und seine eigene Zählung.")),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
            player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, SoundCategory.PLAYERS, 0.9f, 1.2f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.sendMessage(t(player,
                    "§b✦ §fIsle lesson §8· §bMaren §7waits at the Harbourmaster's Hall — follow the bar up top.",
                    "§b✦ §fInsel-Lektion §8· §bMaren §7wartet in der Hafenmeisterei — folg der Leiste oben."));
        }, 50L);
        if (teacher() == null) {
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    hookInChat(player);
                }
            }, 70L);
        }
    }

    // ------------------------------------------------------------------ the teacher

    /** Maren was clicked. True when the lesson took the click (her desk stays out of it). */
    boolean onTeacher(Player player) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson == NEW && LakeWorld.onIsle(player)) {
            begin(player, profile);
        }
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            return false;
        }
        if (isle.cast().teacherBusy(player)) {
            return true;
        }
        switch (profile.lesson) {
            case MEET -> hook(player);
            case CATCH -> {
                remind(player, t(player,
                        "Three in a row, {player}. Reel on green — gold's perfect. Lose one and we count again.",
                        "Drei hintereinander, {player}. Auf Grün einholen — Gold ist perfekt. Einer weg, und wir zählen neu."));
                pointWater(player);
            }
            case FIND -> remind(player, t(player,
                    "Something heavy's circling. Cast again — narrow window, so watch the bar.",
                    "Da kreist was Schweres. Wirf nochmal — enges Fenster, also achte auf die Leiste."));
            case RETURN -> close(player);
            default -> {
            }
        }
        return true;
    }

    private void hook(Player player) {
        List<String> lines = List.of(
                t(player, "Same bar as the harbour, {player} — but out here the water keeps count.",
                        "Dieselbe Leiste wie am Hafen, {player} — aber hier draußen zählt das Wasser mit."),
                t(player, "Land three in a row without losing one. Reel on green. Gold is perfect.",
                        "Hol drei hintereinander raus, ohne einen zu verlieren. Auf Grün einholen. Gold ist perfekt."));
        List<TalkAccess.Reply> chips = List.of(
                new TalkAccess.Reply(t(player, "Show me.", "Zeig her."), NamedTextColor.AQUA,
                        () -> startCatch(player), t(player, "Show me.", "Zeig her.")),
                new TalkAccess.Reply(t(player, "What's the count for?", "Wozu die Zählung?"), NamedTextColor.GRAY,
                        () -> stakes(player), t(player, "What's the count for?", "Wozu zählt das Wasser?")));
        if (!speak(player, lines, chips)) {
            hookInChat(player);
        }
    }

    private void stakes(Player player) {
        List<String> lines = List.of(
                t(player, "Three clean is Warm water — rarer fish start biting. Five is Hot, ten is Boiling.",
                        "Drei saubere sind Warmes Wasser — seltenere Fische beißen. Fünf ist Heiß, zehn ist Kochend."),
                t(player, "One lost fish and it's cold again. Patience pays out here.",
                        "Ein verlorener Fisch, und es ist wieder kalt. Geduld zahlt sich hier aus."));
        List<TalkAccess.Reply> chips = List.of(new TalkAccess.Reply(t(player, "Show me.", "Zeig her."),
                NamedTextColor.AQUA, () -> startCatch(player), t(player, "Show me.", "Zeig her.")));
        if (!speak(player, lines, chips)) {
            startCatch(player);
        }
    }

    private void hookInChat(Player player) {
        chat(player, t(player, "Same bar as the harbour, {player} — but out here the water keeps count.",
                "Dieselbe Leiste wie am Hafen, {player} — aber hier draußen zählt das Wasser mit."));
        chat(player, t(player, "Land three in a row without losing one. Reel on green. Gold is perfect.",
                "Hol drei hintereinander raus, ohne einen zu verlieren. Auf Grün einholen. Gold ist perfekt."));
        startCatch(player);
    }

    private void startCatch(Player player) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson != MEET) {
            return;
        }
        if (!hasRod(player)) {
            ItemStack rod = starterRod();
            if (rod != null) {
                InventoryDrops.give(player, rod);
                say(player, t(player, "No rod? Take this one. Bring it back wet.",
                        "Keine Angel? Nimm die hier. Bring sie nass zurück."));
            }
        }
        profile.lesson = CATCH;
        isle.profiles().markDirty();
        streak.put(player.getUniqueId(), 0);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.8f, 1.2f);
        pointWater(player);
        paint(player, profile);
    }

    /** The isle compass on the nearest lake water. */
    private void pointWater(Player player) {
        Waters.Water best = null;
        double bestD = Double.MAX_VALUE;
        for (Waters.Water water : isle.waters().all()) {
            if (water.kind() != Waters.Kind.LAKE) {
                continue;
            }
            Location center = water.center(player.getWorld());
            if (center == null) {
                continue;
            }
            double d = center.distanceSquared(player.getLocation());
            if (d < bestD) {
                bestD = d;
                best = water;
            }
        }
        if (best != null) {
            isle.compass().guide(player, best.colored(), best.center(player.getWorld()));
        }
    }

    private void remind(Player player, String line) {
        if (!speak(player, List.of(line), List.of())) {
            chat(player, line);
        }
    }

    private void close(Player player) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        forget(player.getUniqueId());
        FishingSkills.coins(player, REWARD_COINS);
        FishingSkills.bonus(player, REWARD_XP);
        player.showTitle(Title.title(LakeText.legacy("§b§l" + t(player, "LESSON DONE", "LEKTION GESCHAFFT")),
                LakeText.legacy(t(player, "§7Fishing §8· §6+" + LakeText.coins(REWARD_COINS) + " coins §8· §a+" + REWARD_XP + " XP",
                        "§7Angeln §8· §6+" + LakeText.coins(REWARD_COINS) + " Münzen §8· §a+" + REWARD_XP + " XP")),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteUsed(player, DONE_NOTE);
        }
        List<String> lines = List.of(
                t(player, "That's fishing here, {player}: patience, then one honest pull. Keep it clean and the water warms.",
                        "So geht Angeln hier, {player}: Geduld, dann ein ehrlicher Zug. Bleib sauber, und das Wasser wird warm."),
                t(player, "Odile keeps the Angler's Log — every species, every weight. Tilly's bait changes what bites. The pad home's where you landed.",
                        "Odile führt das Anglerbuch — jede Art, jedes Gewicht. Tillys Köder ändern, was beißt. Das Pad nach Hause ist, wo du gelandet bist."));
        List<TalkAccess.Reply> chips = new ArrayList<>();
        chips.add(new TalkAccess.Reply(t(player, "Odile's Log", "Odiles Buch"), NamedTextColor.LIGHT_PURPLE,
                () -> pointRole(player, LakeRole.TAXIDERMIST), t(player, "Where's Odile?", "Wo ist Odile?")));
        chips.add(new TalkAccess.Reply(t(player, "Way home?", "Weg nach Hause?"), NamedTextColor.AQUA,
                () -> pointHome(player), t(player, "How do I get home?", "Wie komm ich heim?")));
        chips.add(new TalkAccess.Reply(t(player, "Bye.", "Tschüss."), NamedTextColor.DARK_GRAY,
                () -> say(player, t(player, "Tight lines, love.", "Petri Heil.")), t(player, "Bye.", "Tschüss.")));
        if (!speak(player, lines, chips)) {
            for (String line : lines) {
                chat(player, line);
            }
            pointRole(player, LakeRole.TAXIDERMIST);
        }
    }

    private void pointRole(Player player, LakeRole role) {
        Location at = isle.cast().whereabouts(role);
        if (at != null) {
            isle.compass().guide(player, role.color() + role.display(), at);
            say(player, t(player, "Arrow's on " + role.shortName() + ".", "Der Pfeil zeigt auf " + role.shortName() + "."));
        }
    }

    private void pointHome(Player player) {
        Location at = LakeWorld.landing(isle.plugin());
        if (at != null) {
            isle.compass().guide(player, t(player, "§bSlime pad · home", "§bSprungpad · heim"), at);
        }
        say(player, t(player, "Same pad you landed on. Or §e/capital§f from anywhere.",
                "Dasselbe Pad, auf dem du gelandet bist. Oder §e/capital§f von überall."));
    }

    // ------------------------------------------------------------------ the loop (hooks)

    /** One fish landed on the isle (The Line). */
    void onCatch(Player player, Species species, Waters.Water water) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        UUID id = player.getUniqueId();
        if (profile.lesson == CATCH) {
            int count = streak.merge(id, 1, Integer::sum);
            brokeUntil.remove(id);
            if (count < STREAK_GOAL) {
                paint(player, profile);
                return;
            }
            profile.lesson = FIND;
            isle.profiles().markDirty();
            FishingSkills.bonus(player, WARM_XP);
            isle.compass().stopGuide(player);
            Species rare = pickRare(water);
            quarry.put(id, rare);
            isle.line().force(player, rare);
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.showTitle(Title.title(LakeText.legacy("§e§l" + t(player, "WARM WATER", "WARMES WASSER")),
                        LakeText.legacy(t(player, "§7Three clean §8· §fsomething heavy just noticed you",
                                "§7Drei saubere §8· §fetwas Schweres hat dich bemerkt")),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2400), Duration.ofMillis(600))));
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.6f, 0.7f);
                paint(player, profile);
            }, 30L);
            return;
        }
        if (profile.lesson == FIND && species != null && species.rarity() == Species.Rarity.RARE) {
            quarry.remove(id);
            profile.lesson = RETURN;
            isle.profiles().markDirty();
            player.showTitle(Title.title(LakeText.legacy("§9§l" + t(player, "LANDED IT", "GELANDET")),
                    LakeText.legacy(species.colored() + t(player, " §8· §7in your Angler's Log §8· §fback to Maren",
                            " §8· §7im Anglerbuch §8· §fzurück zu Maren")),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2600), Duration.ofMillis(600))));
            paint(player, profile);
        }
    }

    /** A fish got away on the isle. */
    void onMiss(Player player, Species species) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        UUID id = player.getUniqueId();
        if (profile.lesson == CATCH) {
            Integer before = streak.put(id, 0);
            if (before != null && before > 0) {
                brokeUntil.put(id, System.currentTimeMillis() + 3500L);
            }
            paint(player, profile);
            return;
        }
        if (profile.lesson == FIND) {
            // Lost it: it's still circling, and on the next cast it bites again.
            isle.line().force(player, quarry.computeIfAbsent(id, k -> species != null ? species : pickRare(null)));
            player.sendMessage(t(player, "§7Lost it — §fit's still circling. §7Cast again, watch the narrow green.",
                    "§7Verloren — §fer kreist noch. §7Wirf nochmal, achte auf das schmale Grün."));
        }
    }

    /** The rare that lives in this water (or the lake's big one). */
    private static Species pickRare(Waters.Water water) {
        Species best = null;
        double bestHome = 0.0d;
        if (water != null) {
            for (Species species : Species.values()) {
                if (species.rarity() != Species.Rarity.RARE) {
                    continue;
                }
                double home = species.home(water);
                if (home > bestHome) {
                    bestHome = home;
                    best = species;
                }
            }
        }
        return best != null ? best : Species.OLD_WHISKERS;
    }

    /** The rest of the cast send lesson players back to Maren first. */
    boolean deferring(Player player) {
        int stage = isle.profiles().of(player).lesson;
        return stage >= MEET && stage < DONE;
    }

    // ------------------------------------------------------------------ the bar

    private void paint(Player player, AnglerProfiles.Profile profile) {
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            hide(player.getUniqueId());
            return;
        }
        String head = t(player, "§bIsle lesson §8· ", "§bInsel-Lektion §8· ");
        String text;
        float progress;
        BossBar.Color color = BossBar.Color.BLUE;
        switch (profile.lesson) {
            case MEET, RETURN -> {
                Location at = teacher();
                String verb = profile.lesson == MEET ? t(player, "Meet", "Triff") : t(player, "Back to", "Zurück zu");
                if (at == null || at.getWorld() != player.getWorld()) {
                    text = head + "§f" + verb + " §bMaren";
                    progress = 0.1f;
                } else {
                    double d = player.getLocation().distance(at);
                    text = head + "§f" + verb + " §bMaren §8· §e" + LakeText.arrow(player, at) + " §7" + (int) d + "m";
                    progress = (float) Math.max(0.05d, Math.min(1.0d, 1.0d - d / 120.0d));
                }
            }
            case CATCH -> {
                int count = streak.getOrDefault(player.getUniqueId(), 0);
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < STREAK_GOAL; i++) {
                    dots.append(i < count ? "§b●" : "§8○");
                }
                Long broke = brokeUntil.get(player.getUniqueId());
                if (broke != null && broke > System.currentTimeMillis()) {
                    text = head + t(player, "§cLost one §8· ", "§cEiner weg §8· ") + dots + t(player, " §7cold again", " §7wieder kalt");
                    color = BossBar.Color.RED;
                } else {
                    text = head + t(player, "§fLand 3 in a row §8· ", "§fHol 3 am Stück raus §8· ") + dots;
                }
                progress = Math.max(0.02f, count / (float) STREAK_GOAL);
            }
            case FIND -> {
                text = head + t(player, "§9Something heavy is circling §8· §fland it", "§9Etwas Schweres kreist §8· §fhol es raus");
                progress = 0.75f;
                color = BossBar.Color.PURPLE;
            }
            default -> {
                hide(player.getUniqueId());
                return;
            }
        }
        String title = BarChrome.legacy(BarChrome.Family.QUEST, BarChrome.Icon.FISH, text);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(LakeText.legacy(title), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(LakeText.legacy(title));
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
        quarry.remove(id);
    }

    /** DEV: run the lesson again from the landing beat (no veteran skip). */
    public void replay(Player player) {
        forget(player.getUniqueId());
        land(player, isle.profiles().of(player));
    }

    /** DEV: mark it done without the walk. */
    public void skip(Player player) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        forget(player.getUniqueId());
    }

    // ------------------------------------------------------------------ plumbing

    private Location teacher() {
        return isle.cast().isPlaced(LakeRole.HARBOURMASTER) ? isle.cast().whereabouts(LakeRole.HARBOURMASTER) : null;
    }

    private boolean speak(Player player, List<String> lines, List<TalkAccess.Reply> chips) {
        return isle.cast().teacherScript(player, lines, chips);
    }

    private void say(Player player, String line) {
        if (!speak(player, List.of(line), List.of())) {
            chat(player, line);
        }
    }

    private static void chat(Player player, String line) {
        player.sendMessage(LakeRole.HARBOURMASTER.color() + LakeRole.HARBOURMASTER.shortName() + " §8» §f"
                + line.replace("{player}", player.getName()));
    }

    private static boolean hasRod(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.FISHING_ROD) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack starterRod() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null || items.getItemManager() == null ? new ItemStack(Material.FISHING_ROD)
                    : new CustomItem(items.getItemManager()).fishing().rod(1);
        } catch (LinkageError | RuntimeException ex) {
            return new ItemStack(Material.FISHING_ROD);
        }
    }

    static String t(Player player, String en, String de) {
        return AetherServices.german(player) ? de : en;
    }
}
