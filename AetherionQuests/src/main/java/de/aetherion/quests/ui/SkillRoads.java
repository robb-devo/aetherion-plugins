package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.talk.TalkUx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Red thread after orientation: Miss Ledger hands the player the four skill isles (Acts IV–VII, see
 * {@code docs/ONBOARDING_ACT_MAP.md}). Each road points straight at its Origin slime pad; the isle itself
 * runs the lesson (its teacher, its loop) and reports back with {@code ISLE_LESSON_<ISLE>}, which ticks the
 * road off here.
 * <p>
 * Soft by design: the player picks one road (reply chips) or wanders. Exactly one soft compass target is
 * set at a time. No quest state is touched and nothing is locked.
 */
public final class SkillRoads {

    /** Set once the Forage road was pointed at, so the harbour pad crumb doesn't repeat it. */
    public static final String FORAGE_POINTED_KEY = "forage_harbour_pad_hint";

    public enum Road {
        FORAGE("forage", "forage_pad", "ISLE_LESSON_FORAGE", NamedTextColor.GREEN),
        FARM("farm", "farm_pad", "ISLE_LESSON_FARM", NamedTextColor.YELLOW),
        FISH("fish", "fish_pad", "ISLE_LESSON_FISH", NamedTextColor.AQUA),
        MINE("mine", "mine_pad", "ISLE_LESSON_MINE", NamedTextColor.GOLD);

        final String key;
        final String spot;
        final String note;
        final NamedTextColor color;

        Road(String key, String spot, String note, NamedTextColor color) {
            this.key = key;
            this.spot = spot;
            this.note = note;
            this.color = color;
        }

        /** Starter-kit flag set when this isle's lesson is done. */
        String doneFlag() {
            return "isle_lesson_" + key;
        }
    }

    private SkillRoads() {
    }

    /* ------------------------------------------------------------------ graduation */

    /**
     * Graduation beat (first stamp only): two lines from Ledger, a quiet default compass
     * target, then the road picker. Classic mode / no TalkUx: one summary line + default.
     */
    public static void graduation(Player player, QuestNPC ledger) {
        if (player == null || ledger == null || !player.isOnline()) {
            return;
        }
        Road fallback = defaultRoad(player);
        // Default target so the bossbar never goes blank (and drops Ledger's own pin) — a chip replaces it.
        QuestHint.clearPending(player);
        QuestHint.showSpot(player, fallback.spot, hintLabel(player, fallback));
        doorOpens(player);

        say(player, ledger, LangPack.say(player, "ledger.isles.intro",
                "Skills grow on the isles, {player}. Each one teaches its own trade — by doing it."));

        if (!talkReady(player)) {
            say(player, ledger, LangPack.say(player, "ledger.isles.list",
                    "Four slime pads on Origin: §aForage§f, §eFarm§f, §bFishing§f, §6Mining§f. The yellow arrow takes you to one."));
            point(player, ledger, fallback, false);
            return;
        }
        say(player, ledger, LangPack.say(player, "ledger.isles.menu",
                "Four isles, four trades. Pick one — the arrow takes you to its pad."));
        later(player, DialogPace.untilReplies(player), () -> {
            if (!offer(player, ledger)) {
                point(player, ledger, fallback, true);
            }
        });
    }

    /**
     * The door: orientation is filed, the four roads open. One title card + a low gate sound + a chime,
     * listener only. Plays once, on the first stamp (the desk's "Where next?" doesn't repeat it).
     */
    private static void doorOpens(Player player) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(LangPack.ui(player, "roads_open_title", "THE ROADS ARE OPEN"), NamedTextColor.LIGHT_PURPLE,
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        LangPack.ui(player, "isles_open_subtitle", "§aForage §8· §eFarm §8· §bFishing §8· §6Mining")),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400),
                        java.time.Duration.ofMillis(2600), java.time.Duration.ofMillis(800))));
        org.bukkit.Location at = player.getLocation();
        player.playSound(at, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, org.bukkit.SoundCategory.PLAYERS, 0.7f, 0.6f);
        player.playSound(at, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.4f);
        later(player, 10L, () -> player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                org.bukkit.SoundCategory.PLAYERS, 0.9f, 1.2f));
    }

    /** Ledger desk chip "Where next?": same picker, one prompt line (ticks show the isles already learned). */
    public static void deskPrompt(Player player, QuestNPC ledger) {
        if (player == null || ledger == null) {
            return;
        }
        int done = doneCount(player);
        String prompt = done >= Road.values().length
                ? LangPack.say(player, "ledger.isles.menu_all",
                        "All four isles learned. Ticked off, every one. Pick a pad anyway — they don't close.")
                : done > 0
                ? LangPack.say(player, "ledger.isles.menu_more",
                        "Ticked ones you've learned. The rest still have a teacher waiting.")
                : LangPack.say(player, "ledger.isles.menu",
                        "Four isles, four trades. Pick one — the arrow takes you to its pad.");
        say(player, ledger, prompt);
        later(player, DialogPace.untilReplies(player), () -> {
            if (!offer(player, ledger)) {
                point(player, ledger, defaultRoad(player), true);
            }
        });
    }

    /** Road chips beside Ledger (max 5: four isles + "I'll wander."). */
    public static boolean offer(Player player, QuestNPC ledger) {
        TalkUx talk = TalkUx.get();
        if (talk == null || player == null || ledger == null || !talk.enabledFor(player)) {
            return false;
        }
        List<TalkUx.Choice> choices = new ArrayList<>();
        for (Road road : Road.values()) {
            boolean done = done(player, road);
            choices.add(new TalkUx.Choice(
                    (done ? "✓ " : "") + label(player, road),
                    done ? NamedTextColor.GRAY : road.color,
                    () -> point(player, ledger, road, true),
                    false,
                    echo(player, road)
            ));
        }
        choices.add(new TalkUx.Choice(
                LangPack.say(player, "ledger.roads.wander.label", "I'll wander."),
                NamedTextColor.GRAY,
                () -> {
                    // Their call: no compass nag. The desk's "Where next?" brings the roads back.
                    QuestHint.clearPending(player);
                    say(player, ledger, LangPack.say(player, "ledger.isles.wander.line",
                            "Good. The map's yours — Sergeant Vex at the Borderlands gate when you want a fight."));
                },
                false,
                LangPack.say(player, "ledger.roads.wander.echo", "I'll look around.")
        ));
        return talk.choices(player, ledger.getId(), ledger.getName(), choices);
    }

    /**
     * Point at one road: speaker line, one compass target (the isle's pad), one action-bar tip.
     * {@code speak=false} skips the line (already covered by a summary).
     */
    public static void point(Player player, QuestNPC speaker, Road road, boolean speak) {
        if (player == null || road == null || !player.isOnline()) {
            return;
        }
        if (speak && speaker != null) {
            say(player, speaker, done(player, road) ? lineDone(player, road) : line(player, road));
        }
        QuestHint.clearPending(player);
        QuestHint.showSpot(player, road.spot, hintLabel(player, road));
        player.sendActionBar(Component.text(tip(player, road), road.color));
        if (road == Road.FORAGE) {
            markForagePointed(player);
        }
    }

    /** First isle whose lesson isn't done yet (Forage → Farm → Fish → Mine), else Forage. */
    public static Road defaultRoad(Player player) {
        for (Road road : Road.values()) {
            if (!done(player, road)) {
                return road;
            }
        }
        return Road.FORAGE;
    }

    public static void markForagePointed(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (player != null && plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), FORAGE_POINTED_KEY);
        }
    }

    /* ------------------------------------------------------------------ isle lessons */

    /**
     * {@code ISLE_LESSON_<ISLE>} from an isle plugin (via {@code QuestProgressAccess.noteUsed}): file the road as
     * learned. True when the target was an isle lesson note (nothing else should read it).
     */
    public static boolean noteIsleLesson(Player player, String target) {
        if (player == null || target == null || !target.toUpperCase(Locale.ROOT).startsWith("ISLE_LESSON_")) {
            return false;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        for (Road road : Road.values()) {
            if (road.note.equalsIgnoreCase(target) && plugin != null && plugin.getPlayerQuestStorage() != null) {
                plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), road.doneFlag());
            }
        }
        return true;
    }

    /** This isle's lesson is done (the isle reported it). */
    public static boolean done(Player player, Road road) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return player != null && plugin != null && plugin.getPlayerQuestStorage() != null
                && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), road.doneFlag());
    }

    private static int doneCount(Player player) {
        int n = 0;
        for (Road road : Road.values()) {
            if (done(player, road)) {
                n++;
            }
        }
        return n;
    }

    /* ------------------------------------------------------------------ lookups */

    // Explicit keys (no string building) so the DE validator and grep see every one.

    private static String hintLabel(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.ui(player, "hint_label.forage_pad", "Forage Isle Pad");
            case FARM -> LangPack.ui(player, "hint_label.farm_pad", "Farm Isle Pad");
            case FISH -> LangPack.ui(player, "hint_label.fish_pad", "Fishing Eldervale Pad");
            case MINE -> LangPack.ui(player, "hint_label.mine_pad", "Mining Eldervale Pad");
        };
    }

    private static String label(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.say(player, "ledger.isles.forage.label", "Forage Isle");
            case FARM -> LangPack.say(player, "ledger.isles.farm.label", "Farm Isle");
            case FISH -> LangPack.say(player, "ledger.isles.fish.label", "Fishing Isle");
            case MINE -> LangPack.say(player, "ledger.isles.mine.label", "Mining Isle");
        };
    }

    private static String echo(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.say(player, "ledger.roads.forage.echo", "Trees, please.");
            case FARM -> LangPack.say(player, "ledger.roads.farm.echo", "Fields for me.");
            case FISH -> LangPack.say(player, "ledger.roads.fish.echo", "I'll fish.");
            case MINE -> LangPack.say(player, "ledger.isles.mine.echo", "Rock. Deep rock.");
        };
    }

    /** One sentence per isle: what its trade feels like, and who meets you there. */
    private static String line(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.say(player, "ledger.isles.forage.line",
                    "§aForage Isle§f — the Fell bar again, but the trees keep score. Pad's north-east; §aMiss Canopy§f meets you.");
            case FARM -> LangPack.say(player, "ledger.isles.farm.line",
                    "§eFarm Isle§f — harvest in rhythm and the field pays more. Pad's south-west; §eElsie§f meets you.");
            case FISH -> LangPack.say(player, "ledger.isles.fish.line",
                    "§bFishing Eldervale§f — twelve waters, one honest pull. Pad's north-west; §bMaren§f meets you.");
            case MINE -> LangPack.say(player, "ledger.isles.mine.line",
                    "§6Mining Eldervale§f — your pick's power against the rock. Pad's south; §6Old Wick§f meets you.");
        };
    }

    private static String lineDone(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.say(player, "ledger.isles.forage.line_done",
                    "Forage again? Pell's board will have fresh orders. Arrow's on the pad.");
            case FARM -> LangPack.say(player, "ledger.isles.farm.line_done",
                    "Back to the rows? Hattie's orders pay double. Arrow's on the pad.");
            case FISH -> LangPack.say(player, "ledger.isles.fish.line_done",
                    "The lake again? Odile's Log has gaps, I'm told. Arrow's on the pad.");
            case MINE -> LangPack.say(player, "ledger.isles.mine.line_done",
                    "Down the rock again? Otto's contracts don't fill themselves. Arrow's on the pad.");
        };
    }

    private static String tip(Player player, Road road) {
        return switch (road) {
            case FORAGE -> LangPack.ui(player, "tip.isle_forage", "→ Forage Isle pad · Miss Canopy teaches the isle");
            case FARM -> LangPack.ui(player, "tip.isle_farm", "→ Farm Isle pad · Elsie teaches the isle");
            case FISH -> LangPack.ui(player, "tip.isle_fish", "→ Fishing Eldervale pad · Maren teaches the isle");
            case MINE -> LangPack.ui(player, "tip.isle_mine", "→ Mining Eldervale pad · Old Wick teaches the isle");
        };
    }

    /* ------------------------------------------------------------------ plumbing */

    private static boolean talkReady(Player player) {
        TalkUx talk = TalkUx.get();
        return talk != null && talk.enabledFor(player);
    }

    private static void say(Player player, QuestNPC npc, String line) {
        LivingNpcProfile.say(player, npc, line);
    }

    private static void later(Player player, long ticks, Runnable task) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, ticks);
    }
}
