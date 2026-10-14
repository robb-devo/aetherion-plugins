package de.aetherion.mining.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
 * Act VII · the Mining Eldervale lesson. A first visit is a short spine, not a desk:
 * <pre>
 *   land   ─ card + lesson bar (arrow, metres) to Old Wick by the landing lamp
 *   hook   ─ his bubble reads YOUR pick: ⛏ Mining Power vs coal 8 / copper 14 / iron 24 (a spare ⛏14 if you're short)
 *   do     ─ Strike Rhythm to Steady Pick — the lesson bar mirrors the meter, so idling visibly drains it
 *   find   ─ the next strike grows a geode out of the rock; crack it (another grows if it sinks)
 *   close  ─ back to Wick: the skill in one sentence, coins + XP, Otto's contracts / way home → his plain desk
 * </pre>
 * The stage lives on the mine profile ({@code lesson}). Miners who already worked the isle skip it. Done sends
 * {@code ISLE_LESSON_MINE} to Quests (the road picker ticks the road off).
 */
public final class MineLesson {

    static final int NEW = 0;
    static final int MEET = 1;
    static final int STRIKE = 2;
    static final int FIND = 3;
    static final int RETURN = 4;
    static final int DONE = 5;

    /** The spare pick's power: enough for coal (8) and copper (14). */
    private static final double SPARE_POWER = 14.0d;
    private static final long REWARD_COINS = 400L;
    private static final int REWARD_XP = 120;
    private static final int STEADY_XP = 60;
    private static final String DONE_NOTE = "ISLE_LESSON_MINE";

    private final MineIsle isle;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> armed = new HashSet<>();
    private BukkitTask task;

    MineLesson(MineIsle isle) {
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
        for (Player player : MineWorld.visitors(isle.plugin())) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            here.add(player.getUniqueId());
            MineProfiles.Profile profile = isle.profiles().of(player);
            if (profile.lesson == NEW) {
                begin(player, profile);
            }
            if (profile.lesson == FIND && !isle.crystals().hasGeode(player.getUniqueId())) {
                // After a relog / restart (or a lost geode): the next strike grows one.
                armed.add(player.getUniqueId());
            }
            paint(player, profile);
        }
        for (UUID id : List.copyOf(bars.keySet())) {
            if (!here.contains(id)) {
                hide(id);
            }
        }
    }

    /** Miners who already worked the isle before the lesson existed. */
    private static boolean veteran(MineProfiles.Profile profile) {
        return profile.districts.size() >= 4 || profile.contractsDone > 0 || profile.forgeRep > 0 || profile.crystals > 0;
    }

    private void begin(Player player, MineProfiles.Profile profile) {
        if (veteran(profile)) {
            profile.lesson = DONE;
            isle.profiles().markDirty();
            return;
        }
        land(player, profile);
    }

    private void land(Player player, MineProfiles.Profile profile) {
        profile.lesson = MEET;
        isle.profiles().markDirty();
        // After the isle's own first-discovery card, not over it.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            player.showTitle(Title.title(MineText.legacy("§6§lMINING ELDERVALE"),
                    MineText.legacy(t(player, "§7Deep rock. It answers to power and rhythm.",
                            "§7Tiefer Fels. Er hört auf Kraft und Rhythmus.")),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 0.7f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.sendMessage(t(player,
                    "§6⚒ §fIsle lesson §8· §eOld Wick §7keeps the lamp by the landing — follow the bar up top.",
                    "§6⚒ §fInsel-Lektion §8· §eOld Wick §7hütet die Lampe am Anleger — folg der Leiste oben."));
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

    /** Old Wick was clicked. True when the lesson took the click (his desk stays out of it). */
    boolean onTeacher(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson == NEW && MineWorld.onIsle(isle.plugin(), player)) {
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
            case STRIKE -> {
                remind(player, t(player,
                        "Any rock, {player}. Swing, swing, swing — don't stop till the bar says Steady Pick.",
                        "Jeder Fels, {player}. Schlag, schlag, schlag — nicht aufhören, bis die Leiste Steady Pick sagt."));
                pointQuarry(player);
            }
            case FIND -> remind(player, t(player,
                    "One more strike. The rock's about to give you something that glitters.",
                    "Noch ein Schlag. Der Fels hat gleich was Glitzerndes für dich."));
            case RETURN -> close(player);
            default -> {
            }
        }
        return true;
    }

    private void hook(Player player) {
        List<String> lines = powerLines(player);
        List<TalkAccess.Reply> chips = List.of(
                new TalkAccess.Reply(t(player, "Show me.", "Zeig her."), NamedTextColor.GOLD,
                        () -> startStrike(player), t(player, "Show me.", "Zeig her.")),
                new TalkAccess.Reply(t(player, "Why rhythm?", "Wozu Rhythmus?"), NamedTextColor.GRAY,
                        () -> stakes(player), t(player, "Why does the rhythm matter?", "Wozu der Rhythmus?")));
        if (!speak(player, lines, chips)) {
            hookInChat(player);
        }
    }

    /** The hook reads the player's own pick: the number on the tooltip stops being mysticism. */
    private List<String> powerLines(Player player) {
        double power = bestPickPower(player);
        String reads = power <= 0.0d
                ? t(player, "No pick on you at all.", "Du hast nicht mal eine Spitzhacke.")
                : t(player, "Yours reads ⛏ " + (int) Math.round(power) + ".", "Deine zeigt ⛏ " + (int) Math.round(power) + ".");
        String verdict = power < SPARE_POWER
                ? t(player, "Coal wants 8, copper 14, iron 24. Take my spare — ⛏ 14.",
                        "Kohle will 8, Kupfer 14, Eisen 24. Nimm meine Ersatzhacke — ⛏ 14.")
                : t(player, "Coal wants 8, copper 14, iron 24. You'll do.",
                        "Kohle will 8, Kupfer 14, Eisen 24. Das reicht.");
        return List.of(
                t(player, "Rock here has a price, {player}. Your pick pays it — that's ⛏ Mining Power.",
                        "Fels hat hier seinen Preis, {player}. Deine Spitzhacke zahlt ihn — das ist ⛏ Abbaukraft."),
                reads + " " + verdict,
                t(player, "Now keep it singing: strike without stopping till the bar says Steady Pick.",
                        "Jetzt lass sie singen: schlag ohne Pause, bis die Leiste Steady Pick sagt."));
    }

    private void stakes(Player player) {
        List<String> lines = List.of(
                t(player, "Rhythm is ore Fortune and speed. Steady +10, In the Seam +25, Anvil Chorus +50. Stop and it drains.",
                        "Rhythmus ist Erz-Glück und Tempo. Steady +10, In the Seam +25, Anvil Chorus +50. Pause, und er fließt ab."),
                t(player, "Any rock feeds it. Ore feeds it double. Same ore eight in a row and the seam bursts.",
                        "Jeder Fels nährt ihn. Erz doppelt. Achtmal dasselbe Erz hintereinander, und die Ader platzt."));
        List<TalkAccess.Reply> chips = List.of(new TalkAccess.Reply(t(player, "Show me.", "Zeig her."),
                NamedTextColor.GOLD, () -> startStrike(player), t(player, "Show me.", "Zeig her.")));
        if (!speak(player, lines, chips)) {
            startStrike(player);
        }
    }

    private void hookInChat(Player player) {
        for (String line : powerLines(player)) {
            chat(player, line);
        }
        startStrike(player);
    }

    private void startStrike(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson != MEET) {
            return;
        }
        if (bestPickPower(player) < SPARE_POWER) {
            ItemStack spare = sparePick();
            if (spare != null) {
                MineSkills.give(player, spare);
                player.sendMessage(t(player, "§eOld Wick §7handed you a §aMining Pickaxe §8(⛏ 14)§7. Hold it to dig.",
                        "§eOld Wick §7gibt dir eine §aBergbau-Spitzhacke §8(⛏ 14)§7. Halt sie zum Graben."));
            }
        }
        profile.lesson = STRIKE;
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.5f, 1.4f);
        pointQuarry(player);
        paint(player, profile);
    }

    /** The compass on the nearest quarry at roughly this height (any rock counts, a quarry just has ore). */
    private void pointQuarry(Player player) {
        MineDistricts.District best = null;
        double bestD = Double.MAX_VALUE;
        for (MineDistricts.District district : isle.districts().all()) {
            if (district.kind() != MineDistricts.Kind.QUARRY) {
                continue;
            }
            Location center = district.center(player.getWorld());
            if (center == null) {
                continue;
            }
            double d = center.distanceSquared(player.getLocation())
                    + 4.0d * Math.pow(center.getY() - player.getLocation().getY(), 2);
            if (d < bestD) {
                bestD = d;
                best = district;
            }
        }
        if (best != null) {
            isle.compass().guide(player, best);
        }
    }

    private void remind(Player player, String line) {
        if (!speak(player, List.of(line), List.of())) {
            chat(player, line);
        }
    }

    private void close(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        forget(player.getUniqueId());
        MineSkills.coins(player, REWARD_COINS);
        MineSkills.bonus(player, REWARD_XP);
        player.showTitle(Title.title(MineText.legacy("§6§l" + t(player, "LESSON DONE", "LEKTION GESCHAFFT")),
                MineText.legacy(t(player, "§7Mining §8· §6+" + MineText.coins(REWARD_COINS) + " coins §8· §a+" + REWARD_XP + " XP",
                        "§7Bergbau §8· §6+" + MineText.coins(REWARD_COINS) + " Münzen §8· §a+" + REWARD_XP + " XP")),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteUsed(player, DONE_NOTE);
        }
        List<String> lines = List.of(
                t(player, "That's mining here, {player}: read the rock, keep the beat. Power opens the rock — rhythm empties it.",
                        "So geht Bergbau hier, {player}: Fels lesen, Takt halten. Kraft öffnet den Fels — Rhythmus leert ihn."),
                t(player, "Otto hands out contracts — that's the steady money. Brann's Deep Forge turns ore into marks for keeps. The pad home's by my lamp.",
                        "Otto vergibt Aufträge — das ist das sichere Geld. Branns Tiefe Schmiede macht aus Erz bleibende Marken. Das Pad nach Hause ist an meiner Lampe."));
        List<TalkAccess.Reply> chips = new ArrayList<>();
        chips.add(new TalkAccess.Reply(t(player, "Otto's contracts", "Ottos Aufträge"), NamedTextColor.GOLD,
                () -> pointRole(player, MineRole.CLERK), t(player, "Where's Otto?", "Wo ist Otto?")));
        chips.add(new TalkAccess.Reply(t(player, "Way home?", "Weg nach Hause?"), NamedTextColor.AQUA,
                () -> pointHome(player), t(player, "How do I get home?", "Wie komm ich heim?")));
        chips.add(new TalkAccess.Reply(t(player, "Bye.", "Tschüss."), NamedTextColor.DARK_GRAY,
                () -> say(player, t(player, "Mind your lamp.", "Pass auf deine Lampe auf.")), t(player, "Bye.", "Tschüss.")));
        if (!speak(player, lines, chips)) {
            for (String line : lines) {
                chat(player, line);
            }
            pointRole(player, MineRole.CLERK);
        }
    }

    private void pointRole(Player player, MineRole role) {
        isle.compass().guide(player, role);
        say(player, t(player, "Arrow's on " + role.shortName() + ".", "Der Pfeil zeigt auf " + role.shortName() + "."));
    }

    private void pointHome(Player player) {
        Location at = MineWorld.landing(isle.plugin());
        if (at != null) {
            isle.compass().guide(player, t(player, "§bSlime pad · home", "§bSprungpad · heim"), at);
        }
        say(player, t(player, "Same pad you landed on. Or §e/capital§f from anywhere.",
                "Dasselbe Pad, auf dem du gelandet bist. Oder §e/capital§f von überall."));
    }

    // ------------------------------------------------------------------ the loop (hooks)

    /** One hand strike on the isle, after the rhythm took it. */
    void onStrike(Player player, IsleOre ore, Location at) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        UUID id = player.getUniqueId();
        if (profile.lesson == STRIKE) {
            if (isle.rhythm().tier(player).ordinal() >= StrikeRhythm.Tier.STEADY.ordinal()) {
                steady(player, profile);
            }
            return;
        }
        if (profile.lesson == FIND && armed.contains(id)
                && isle.crystals().lessonPop(player, ore != null && ore.hasCrystal() ? ore : IsleOre.COAL, at)) {
            armed.remove(id);
        }
    }

    private void steady(Player player, MineProfiles.Profile profile) {
        profile.lesson = FIND;
        isle.profiles().markDirty();
        MineSkills.bonus(player, STEADY_XP);
        armed.add(player.getUniqueId());
        player.showTitle(Title.title(MineText.legacy("§a§l" + t(player, "STEADY PICK", "STEADY PICK")),
                MineText.legacy(t(player, "§e+10 ore Fortune §7while you keep the beat §8· §fone more strike",
                        "§e+10 Erz-Glück §7solange du im Takt bleibst §8· §fnoch ein Schlag")),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(600))));
        paint(player, profile);
    }

    /** A geode was cracked open. */
    void onGeode(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson != FIND) {
            return;
        }
        armed.remove(player.getUniqueId());
        profile.lesson = RETURN;
        isle.profiles().markDirty();
        player.showTitle(Title.title(MineText.legacy("§d§l✦ " + t(player, "CRYSTAL FIND", "KRISTALLFUND")),
                MineText.legacy(t(player, "§7Ilse buys specimens §8· §fback to Old Wick",
                        "§7Ilse kauft Fundstücke §8· §fzurück zu Old Wick")),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2400), Duration.ofMillis(600))));
        paint(player, profile);
    }

    /** The finder's geode sank or got cracked by someone else: the next strike grows another. */
    void onLost(UUID finder) {
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        if (player == null || isle.profiles().of(player).lesson != FIND) {
            return;
        }
        armed.add(finder);
        player.sendMessage(t(player, "§7Gone. §fThe next strike grows another — crack it this time.",
                "§7Weg. §fDer nächste Schlag lässt noch einen wachsen — diesmal knacken."));
    }

    /** The rest of the cast send lesson players back to Wick first. */
    boolean deferring(Player player) {
        int stage = isle.profiles().of(player).lesson;
        return stage >= MEET && stage < DONE;
    }

    // ------------------------------------------------------------------ the bar

    private void paint(Player player, MineProfiles.Profile profile) {
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            hide(player.getUniqueId());
            return;
        }
        String head = t(player, "§6Isle lesson §8· ", "§6Insel-Lektion §8· ");
        String text;
        float progress;
        BossBar.Color color = BossBar.Color.YELLOW;
        switch (profile.lesson) {
            case MEET, RETURN -> {
                Location at = teacher();
                String verb = profile.lesson == MEET ? t(player, "Meet", "Triff") : t(player, "Back to", "Zurück zu");
                if (at == null || at.getWorld() != player.getWorld()) {
                    text = head + "§f" + verb + " §eOld Wick";
                    progress = 0.1f;
                } else {
                    double d = player.getLocation().distance(at);
                    text = head + "§f" + verb + " §eOld Wick §8· §e" + MineText.arrow(player, at) + " §7" + (int) d + "m"
                            + MineText.vertical(player, at);
                    progress = (float) Math.max(0.05d, Math.min(1.0d, 1.0d - d / 120.0d));
                }
            }
            case STRIKE -> {
                double stacks = Math.min(StrikeRhythm.Tier.STEADY.from, isle.rhythm().stacks(player));
                text = head + t(player, "§fStrike without stopping §8· §aSteady Pick §7", "§fSchlag ohne Pause §8· §aSteady Pick §7")
                        + (int) stacks + "§8/§7" + StrikeRhythm.Tier.STEADY.from;
                progress = (float) Math.max(0.02d, stacks / StrikeRhythm.Tier.STEADY.from);
                color = BossBar.Color.GREEN;
            }
            case FIND -> {
                boolean up = isle.crystals().hasGeode(player.getUniqueId());
                text = head + (up ? t(player, "§dA geode! §fStrike it open", "§dEine Geode! §fSchlag sie auf")
                        : t(player, "§fOne more strike §8· §dsomething glitters in there", "§fNoch ein Schlag §8· §dda glitzert was"));
                progress = up ? 0.9f : 0.7f;
                color = BossBar.Color.PURPLE;
            }
            default -> {
                hide(player.getUniqueId());
                return;
            }
        }
        String title = BarChrome.legacy(BarChrome.Family.QUEST, BarChrome.Icon.PICK, text);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(MineText.legacy(title), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(MineText.legacy(title));
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
        armed.remove(id);
    }

    /** DEV: run the lesson again from the landing beat (no veteran skip). */
    public void replay(Player player) {
        forget(player.getUniqueId());
        land(player, isle.profiles().of(player));
    }

    /** DEV: mark it done without the walk. */
    public void skip(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        forget(player.getUniqueId());
    }

    // ------------------------------------------------------------------ plumbing

    private Location teacher() {
        return isle.cast().isPlaced(MineRole.LAMPWARDEN) ? isle.cast().whereabouts(MineRole.LAMPWARDEN) : null;
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
        player.sendMessage(MineRole.LAMPWARDEN.color() + MineRole.LAMPWARDEN.shortName() + " §8» §f"
                + line.replace("{player}", player.getName()));
    }

    /** The best ⛏ Mining Power printed on any pickaxe this player carries (what the tooltip shows). */
    private static double bestPickPower(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        double best = 0.0d;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.getType().name().endsWith("_PICKAXE")) {
                continue;
            }
            double power = 0.0d;
            try {
                power = items == null || items.getItemManager() == null ? 0.0d
                        : items.getItemManager().getItemStats(stack).getMiningPower();
            } catch (LinkageError | RuntimeException ignored) {
            }
            best = Math.max(best, power);
        }
        return best;
    }

    private static ItemStack sparePick() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null || items.getItemManager() == null ? null
                    : new CustomItem(items.getItemManager()).createMiningPickaxe();
        } catch (LinkageError | RuntimeException ex) {
            return null;
        }
    }

    static String t(Player player, String en, String de) {
        return AetherServices.german(player) ? de : en;
    }
}
