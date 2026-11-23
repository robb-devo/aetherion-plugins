package de.aetherion.farming.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Act V · the Farm Isle lesson. A first visit is a short spine, not a desk:
 * <pre>
 *   land   ─ card + lesson bar (arrow, metres) to Elsie at the Harvest Hall
 *   hook   ─ her bubble: ripe crops shine gold · chips "Show me." / "What's it worth?"
 *   do     ─ catch three shining crops (Ripe Glint → Keen Eye) — the lesson bar counts them
 *   find   ─ the next ripe harvest pops a Prize Crop for them; grab it (another pops if it wilts)
 *   close  ─ back to Elsie: the skill in one sentence, coins + XP + the Farm Isle teleport, next / way home
 * </pre>
 * The stage lives on the isle profile ({@code lesson}). Farmers who already worked the isle skip it.
 * Done sends {@code ISLE_LESSON_FARM} to Quests (the road picker ticks the road off).
 */
public final class FieldLesson {

    static final int NEW = 0;
    static final int MEET = 1;
    /** Stored as 2 in old profiles (it was the rhythm step); same slot, now the glint step. */
    static final int GLINT = 2;
    static final int FIND = 3;
    static final int RETURN = 4;
    static final int DONE = 5;

    private static final long REWARD_COINS = 400L;
    private static final int REWARD_XP = 120;
    private static final int STEADY_XP = 60;
    private static final String DONE_NOTE = "ISLE_LESSON_FARM";
    private static final String TELEPORT = "farm_isle";

    private final FarmIsle isle;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> armed = new HashSet<>();
    private BukkitTask task;

    FieldLesson(FarmIsle isle) {
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
        for (Player player : IsleWorld.visitors(isle.plugin())) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                continue;
            }
            here.add(player.getUniqueId());
            IsleProfiles.Profile profile = isle.profiles().of(player);
            if (profile.lesson == NEW) {
                begin(player, profile);
            }
            if (profile.lesson == FIND && !isle.prizes().popping(player.getUniqueId())) {
                // After a relog / restart (or a lost prize): the next ripe harvest pops one.
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

    /** Farmers who already worked the isle before the lesson existed. */
    private static boolean veteran(IsleProfiles.Profile profile) {
        return profile.plots.size() >= 4 || profile.prizes > 0 || profile.ordersDone > 0;
    }

    private void begin(Player player, IsleProfiles.Profile profile) {
        if (veteran(profile)) {
            profile.lesson = DONE;
            isle.profiles().markDirty();
            return;
        }
        land(player, profile);
    }

    private void land(Player player, IsleProfiles.Profile profile) {
        profile.lesson = MEET;
        isle.profiles().markDirty();
        // After the isle's own first-discovery card, not over it.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            player.showTitle(net.kyori.adventure.title.Title.title(
                    IsleText.legacy("§e§lFARM ISLE · ELDERVALE"),
                    IsleText.legacy(t(player, "§7Ripe crops shine gold. Harvest them in the shine.",
                            "§7Reife Pflanzen glänzen golden. Ernte sie im Glanz.")),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400),
                            java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(800))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.sendMessage(t(player,
                    "§e✦ §fIsle lesson §8· §aElsie §7waits at the Harvest Hall — follow the bar up top.",
                    "§e✦ §fInsel-Lektion §8· §aElsie §7wartet in der Erntehalle — folg der Leiste oben."));
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

    /** Elsie was clicked. True when the lesson took the click (her desk stays out of it). */
    boolean onTeacher(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson == NEW && IsleWorld.onIsle(isle.plugin(), player)) {
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
            case GLINT -> {
                remind(player, t(player,
                        "Harvest in the rows, {player}. When a crop shines gold, harvest that one, quick. Three of them.",
                        "Ernte in den Reihen, {player}. Wenn eine Pflanze golden glänzt, ernte genau die, schnell. Drei Stück."));
                pointField(player);
            }
            case FIND -> remind(player, t(player,
                    "Keep pulling. The field's about to give you something big.",
                    "Weiter ernten. Das Feld hat gleich was Großes für dich."));
            case RETURN -> close(player);
            default -> {
            }
        }
        return true;
    }

    private void hook(Player player) {
        List<String> lines = List.of(
                t(player, "Watch the field while you harvest, {player}. Now and then a ripe crop shines gold.",
                        "Schau aufs Feld, während du erntest, {player}. Ab und zu glänzt eine reife Pflanze golden."),
                t(player, "Harvest it before the shine fades. Catch three and the field pays more. It replants itself.",
                        "Ernte sie, bevor der Glanz verblasst. Schnapp dir drei, und das Feld zahlt mehr. Es pflanzt selbst nach."));
        List<TalkAccess.Reply> chips = List.of(
                new TalkAccess.Reply(t(player, "Show me.", "Zeig her."), NamedTextColor.GREEN,
                        () -> startGlint(player), t(player, "Show me.", "Zeig her.")),
                new TalkAccess.Reply(t(player, "What's it worth?", "Was bringt's?"), NamedTextColor.GRAY,
                        () -> stakes(player), t(player, "What's the shine worth?", "Was bringt der Glanz?")));
        if (!speak(player, lines, chips)) {
            hookInChat(player);
        }
    }

    private void stakes(Player player) {
        List<String> lines = List.of(
                t(player, "Every shining crop gives an extra crop and XP. Catch more in a row: +10, +25, then +50 crop Fortune.",
                        "Jede glänzende Pflanze gibt eine Extra-Ernte und XP. Schnapp dir mehr am Stück: +10, +25, dann +50 Ernte-Glück."),
                t(player, "Let a shine fade and you lose a step. Keep your eyes open, not just your hands.",
                        "Lässt du einen Glanz verblassen, verlierst du eine Stufe. Augen auf, nicht nur die Hände."));
        List<TalkAccess.Reply> chips = List.of(new TalkAccess.Reply(t(player, "Show me.", "Zeig her."),
                NamedTextColor.GREEN, () -> startGlint(player), t(player, "Show me.", "Zeig her.")));
        if (!speak(player, lines, chips)) {
            startGlint(player);
        }
    }

    private void hookInChat(Player player) {
        chat(player, t(player, "Watch the field while you harvest, {player}. Now and then a ripe crop shines gold.",
                "Schau aufs Feld, während du erntest, {player}. Ab und zu glänzt eine reife Pflanze golden."));
        chat(player, t(player, "Harvest it before the shine fades. Catch three and the field pays more. It replants itself.",
                "Ernte sie, bevor der Glanz verblasst. Schnapp dir drei, und das Feld zahlt mehr. Es pflanzt selbst nach."));
        startGlint(player);
    }

    private void startGlint(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson != MEET) {
            return;
        }
        profile.lesson = GLINT;
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.BLOCK_CROP_BREAK, SoundCategory.PLAYERS, 0.9f, 1.2f);
        pointField(player);
        paint(player, profile);
    }

    private void pointField(Player player) {
        IslePlots.Plot best = null;
        double bestD = Double.MAX_VALUE;
        for (IslePlots.Plot plot : isle.plots().fields()) {
            Location center = plot.center(player.getWorld());
            if (center == null) {
                continue;
            }
            double d = center.distanceSquared(player.getLocation());
            if (d < bestD) {
                bestD = d;
                best = plot;
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
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        armed.remove(player.getUniqueId());
        hide(player.getUniqueId());
        FarmingSkills.coins(player, REWARD_COINS);
        FarmingSkills.bonus(player, REWARD_XP);
        boolean teleport = unlockTeleport(player);
        player.showTitle(net.kyori.adventure.title.Title.title(
                IsleText.legacy("§e§l" + t(player, "LESSON DONE", "LEKTION GESCHAFFT")),
                IsleText.legacy(t(player, "§7Farming §8· §6+" + IsleText.coins(REWARD_COINS) + " coins §8· §a+" + REWARD_XP + " XP",
                        "§7Landwirtschaft §8· §6+" + IsleText.coins(REWARD_COINS) + " Münzen §8· §a+" + REWARD_XP + " XP")),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400),
                        java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        if (teleport) {
            player.sendMessage(t(player, "§e✦ Farm Isle §7added to your §fTeleports §8(Nether Star)§7.",
                    "§e✦ Farm-Insel §7zu deinen §fTeleports §7hinzugefügt §8(Netherstern)§7."));
        }
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteUsed(player, DONE_NOTE);
        }
        List<String> lines = List.of(
                t(player, "That's farming here, {player}: eyes open, not just hands. Catch the shine and the field pays more.",
                        "So geht Landwirtschaft hier, {player}: Augen auf, nicht nur die Hände. Schnapp dir den Glanz, und das Feld zahlt mehr."),
                t(player, "Hattie buys crops by order — twice the trader. Bram bakes farming buffs. The pad home's at the landing.",
                        "Hattie kauft Ernte auf Bestellung — doppelt so viel wie der Händler. Bram backt Farm-Buffs. Das Pad nach Hause ist am Anleger."));
        List<TalkAccess.Reply> chips = new ArrayList<>();
        chips.add(new TalkAccess.Reply(t(player, "Hattie's orders", "Hatties Aufträge"), NamedTextColor.GOLD,
                () -> pointRole(player, IsleRole.CLERK), t(player, "Where's Hattie?", "Wo ist Hattie?")));
        chips.add(new TalkAccess.Reply(t(player, "Way home?", "Weg nach Hause?"), NamedTextColor.AQUA,
                () -> pointHome(player), t(player, "How do I get home?", "Wie komm ich heim?")));
        chips.add(new TalkAccess.Reply(t(player, "Bye.", "Tschüss."), NamedTextColor.DARK_GRAY,
                () -> say(player, t(player, "Mind the crows. They mind you.", "Pass auf die Krähen auf. Die passen auf dich auf.")),
                t(player, "Bye.", "Tschüss.")));
        if (!speak(player, lines, chips)) {
            for (String line : lines) {
                chat(player, line);
            }
            pointRole(player, IsleRole.CLERK);
        }
    }

    private boolean unlockTeleport(Player player) {
        try {
            HubAccess hub = AetherServices.hub();
            return hub != null && hub.location(TELEPORT) != null && hub.unlockNew(player.getUniqueId(), TELEPORT);
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    private void pointRole(Player player, IsleRole role) {
        Location at = isle.cast().whereabouts(role);
        if (at != null) {
            isle.compass().guide(player, role.color() + role.display(), at);
            say(player, t(player, "Arrow's on " + role.shortName() + ".", "Der Pfeil zeigt auf " + role.shortName() + "."));
        }
    }

    private void pointHome(Player player) {
        Location at = IsleWorld.landing(isle.plugin());
        if (at != null) {
            isle.compass().guide(player, t(player, "§bSlime pad · home", "§bSprungpad · heim"), at);
        }
        say(player, t(player, "Same pad you landed on. Or §e/capital§f from anywhere.",
                "Dasselbe Pad, auf dem du gelandet bist. Oder §e/capital§f von überall."));
    }

    // ------------------------------------------------------------------ the loop (hooks)

    /** One hand harvest on the isle, after Ripe Glint took it. */
    void onHarvest(Player player, IsleCrop crop, Location at) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson == GLINT) {
            if (isle.glint().tier(player).ordinal() >= RipeGlint.Tier.KEEN.ordinal()) {
                steady(player, profile);
            }
            return;
        }
        if (profile.lesson == FIND && crop != null && armed.contains(player.getUniqueId())
                && isle.prizes().lessonPop(player, crop, at)) {
            armed.remove(player.getUniqueId());
        }
    }

    private void steady(Player player, IsleProfiles.Profile profile) {
        profile.lesson = FIND;
        isle.profiles().markDirty();
        FarmingSkills.bonus(player, STEADY_XP);
        isle.compass().stopGuide(player);
        player.showTitle(net.kyori.adventure.title.Title.title(
                IsleText.legacy("§a§l" + t(player, "SHARP EYES", "SCHARFE AUGEN")),
                IsleText.legacy(t(player, "§7Keen Eye §8· §e+10 crop Fortune §7while you keep catching the shine",
                        "§7Keen Eye §8· §e+10 Ernte-Glück §7solange du den Glanz erwischst")),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(600))));
        // The very next ripe harvest pops a Prize Crop for them.
        armed.add(player.getUniqueId());
        paint(player, profile);
    }

    /** A Prize Crop was grabbed. */
    void onPrize(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.lesson != FIND) {
            return;
        }
        armed.remove(player.getUniqueId());
        profile.lesson = RETURN;
        isle.profiles().markDirty();
        player.showTitle(net.kyori.adventure.title.Title.title(
                IsleText.legacy("§6§l✦ " + t(player, "PRIZE CROP", "PREIS-ERNTE")),
                IsleText.legacy(t(player, "§7Sells by weight at the Market Barn §8· §fback to Elsie",
                        "§7Verkauft nach Gewicht in der Marktscheune §8· §fzurück zu Elsie")),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2400), java.time.Duration.ofMillis(600))));
        paint(player, profile);
    }

    /** The finder's Prize Crop wilted or got snatched: the next ripe harvest pops another. */
    void onWilt(UUID finder) {
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        if (player == null || isle.profiles().of(player).lesson != FIND) {
            return;
        }
        armed.add(finder);
        player.sendMessage(t(player, "§7Gone. §fThe next ripe crop pops another — click it this time.",
                "§7Weg. §fDie nächste reife Pflanze bringt noch eine — diesmal anklicken."));
    }

    /** The rest of the cast send lesson players back to Elsie first. */
    boolean deferring(Player player) {
        int stage = isle.profiles().of(player).lesson;
        return stage >= MEET && stage < DONE;
    }

    // ------------------------------------------------------------------ the bar

    private void paint(Player player, IsleProfiles.Profile profile) {
        if (profile.lesson < MEET || profile.lesson >= DONE) {
            hide(player.getUniqueId());
            return;
        }
        String head = t(player, "§eIsle lesson §8· ", "§eInsel-Lektion §8· ");
        String text;
        float progress;
        BossBar.Color color = BossBar.Color.YELLOW;
        switch (profile.lesson) {
            case MEET, RETURN -> {
                Location at = teacher();
                String verb = profile.lesson == MEET ? t(player, "Meet", "Triff") : t(player, "Back to", "Zurück zu");
                if (at == null || at.getWorld() != player.getWorld()) {
                    text = head + "§f" + verb + " §aElsie";
                    progress = 0.1f;
                } else {
                    double d = player.getLocation().distance(at);
                    text = head + "§f" + verb + " §aElsie §8· §e" + IsleText.arrow(player, at) + " §7" + (int) d + "m";
                    progress = (float) Math.max(0.05d, Math.min(1.0d, 1.0d - d / 120.0d));
                }
            }
            case GLINT -> {
                int goal = RipeGlint.Tier.KEEN.from();
                double streak = Math.min(goal, isle.glint().streak(player));
                text = head + t(player, "§fHarvest the crops that §6shine gold §8· §7", "§fErnte die Pflanzen, die §6golden glänzen §8· §7")
                        + (int) streak + "§8/§7" + goal;
                progress = (float) Math.max(0.02d, streak / goal);
                color = BossBar.Color.GREEN;
            }
            case FIND -> {
                boolean up = isle.prizes().popping(player.getUniqueId());
                text = head + (up ? t(player, "§6Grab the Prize Crop — §fbefore it wilts!", "§6Schnapp dir die Preis-Ernte — §fbevor sie welkt!")
                        : t(player, "§fKeep harvesting §8· §6something big is coming", "§fWeiter ernten §8· §6gleich kommt was Großes"));
                progress = up ? 0.9f : 0.7f;
            }
            default -> {
                hide(player.getUniqueId());
                return;
            }
        }
        String title = BarChrome.legacy(BarChrome.Family.QUEST, BarChrome.Icon.WHEAT, text);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(IsleText.legacy(title), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(IsleText.legacy(title));
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
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.lesson = DONE;
        isle.profiles().markDirty();
        forget(player.getUniqueId());
    }

    // ------------------------------------------------------------------ plumbing

    private Location teacher() {
        return isle.cast().isPlaced(IsleRole.WARDEN) ? isle.cast().whereabouts(IsleRole.WARDEN) : null;
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
        player.sendMessage(IsleRole.WARDEN.color() + IsleRole.WARDEN.shortName() + " §8» §f"
                + line.replace("{player}", player.getName()));
    }

    static String t(Player player, String en, String de) {
        return AetherServices.german(player) ? de : en;
    }
}
