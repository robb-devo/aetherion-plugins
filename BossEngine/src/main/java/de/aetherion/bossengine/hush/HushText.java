package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.util.BossTitlesDe;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;

/**
 * Every player-facing line of The Hush, English (canon) and German (lockeres du, same sentence count).
 * The client language decides; {@link BossTitlesDe#german} is the shared test.
 */
final class HushText {

    /** One line in both languages ({@code &} colour codes). */
    record T(String en, String de) {

        String of(Player p) {
            return BossTitlesDe.german(p) ? de : en;
        }

        T with(Object... args) {
            String e = en;
            String d = de;
            for (int i = 0; i < args.length; i++) {
                String v = String.valueOf(args[i]);
                e = e.replace("{" + i + "}", v);
                d = d.replace("{" + i + "}", v);
            }
            return new T(e, d);
        }
    }

    private HushText() {
    }

    /* ------------------------------------------------------------------ names */

    static final T NAME = new T("&8&lThe Hush", "&8&lDie Stille");
    static final T DREAD = new T("&5Dread", "&5Furcht");

    /* ------------------------------------------------------------------ entry and exit */

    static final T FORMING = new T("&8✦ &7The dusk toll is ringing. &8Somewhere, one stroke is missing…",
            "&8✦ &7Die Abendglocke läutet. &8Irgendwo fehlt ein Schlag…");
    static final T FORMING_BAR = new T("&7The belfry is forming…", "&7Der Glockenstuhl entsteht…");
    static final T E_DISABLED = new T("&cThe Hush is not here tonight.", "&cDie Stille ist heute Nacht nicht hier.");
    static final T E_PERMISSION = new T("&cYou cannot hear the missing toll yet.", "&cDu hörst den fehlenden Schlag noch nicht.");
    static final T E_ALREADY = new T("&cYou are already inside.", "&cDu bist schon drin.");
    static final T E_COOLDOWN = new T("&cThe belfry is still ringing out. &7Try again in &f{0}s&7.",
            "&cDer Glockenstuhl klingt noch nach. &7Versuch es in &f{0}s &7nochmal.");
    static final T E_LEADER = new T("&cOnly your party leader can lead you in.", "&cNur deine Party-Leitung kann euch hineinführen.");
    static final T E_MIN = new T("&cThe Hush needs at least &f{0} &cof you.", "&cFür die Stille braucht ihr mindestens &f{0} &cLeute.");
    static final T E_FULL = new T("&cEvery belfry is taken. &7Try again in a minute.", "&cAlle Glockenstühle sind belegt. &7Versuch es gleich nochmal.");
    static final T LEFT = new T("&8✦ &7You step out of the dark.", "&8✦ &7Du trittst aus der Dunkelheit.");
    static final T BROUGHT_BACK = new T("&8✦ &7The dark let you go.", "&8✦ &7Die Dunkelheit hat dich gehen lassen.");
    static final T WIPE = new T("&8✦ &7The lamps are out. &8The Hush keeps the belfry tonight.",
            "&8✦ &7Die Lampen sind aus. &8Die Stille behält den Glockenstuhl heute Nacht.");
    static final T VICTORY_HOME = new T("&6✦ &eDawn. &7The bells carry you home.", "&6✦ &eMorgengrauen. &7Die Glocken tragen dich heim.");

    /* ------------------------------------------------------------------ the fight */

    static final T TITLE_SUB = new T("&7What grows where the fifth toll should be", "&7Was dort wächst, wo der fünfte Schlag fehlt");
    static final T HINT_LIGHT = new T("&6☀ &eFight it in the lamplight. &7In the dark it barely feels you.",
            "&6☀ &eKämpf im Laternenlicht. &7Im Dunkeln spürt sie dich kaum.");
    static final T HINT_RELIGHT = new T("&6☀ &7Right-click a snuffed lamp to light it again.",
            "&6☀ &7Rechtsklick auf eine erloschene Laterne zündet sie wieder an.");
    static final T SLIPS = new T("&8It slips through the dark. &7Drive it into the light.",
            "&8Sie gleitet durchs Dunkel. &7Treib sie ins Licht.");
    static final T REVEALED = new T("&e&lIN THE LIGHT &7- strike now!", "&e&lIM LICHT &7- jetzt zuschlagen!");
    static final T STAGGERED = new T("&e&lSTAGGERED &7- the lamp holds!", "&e&lTAUMELT &7- die Laterne hält!");
    static final T RELIT = new T("&6☀ &e{0} &7lit a lamp.", "&6☀ &e{0} &7hat eine Laterne angezündet.");
    static final T SNUFFED = new T("&8A lamp goes out.", "&8Eine Laterne erlischt.");
    static final T HUSH_WARN = new T("&8&lHUSH… &7get into the light!", "&8&lPSST… &7ab ins Licht!");
    static final T GRASP_WARN = new T("&8Something reaches up. &7Light keeps it down.", "&8Etwas greift nach oben. &7Licht hält es unten.");
    static final T FOUND_YOU = new T("&5The Hush finds you in the dark.", "&5Die Stille findet dich im Dunkeln.");
    static final T ECHO_POP = new T("&8Only an echo. &7The real one makes no sound.", "&8Nur ein Echo. &7Die echte macht keinen Laut.");
    static final T ENRAGE = new T("&4&lThe night does not end.", "&4&lDie Nacht hört nicht auf.");

    /* ------------------------------------------------------------------ the lullaby */

    static final T LULLABY_LISTEN = new T("&e&lLISTEN &7- the bells remember the song.", "&e&lHÖR ZU &7- die Glocken kennen das Lied.");
    static final T LULLABY_ANSWER = new T("&e&lRING THEM &7- the same order. &8({0}s)", "&e&lLÄUTE SIE &7- in derselben Reihenfolge. &8({0}s)");
    static final T LULLABY_FIFTH = new T("&e&lRING THEM &7- five notes. &8The dark hides the last one.",
            "&e&lLÄUTE SIE &7- fünf Töne. &8Den letzten versteckt das Dunkel.");
    static final T LULLABY_RIGHT = new T("&6♪ &e{0}&7/&e{1}", "&6♪ &e{0}&7/&e{1}");
    static final T LULLABY_WRONG = new T("&8The dark swallows the note. &7Start again.", "&8Das Dunkel verschluckt den Ton. &7Von vorn.");
    static final T LULLABY_DONE = new T("&6&lTHE SONG HOLDS &7- it is lulled. Strike!", "&6&lDAS LIED HÄLT &7- sie ist eingelullt. Zuschlagen!");
    static final T LULLABY_FAIL = new T("&4&lNIGHT FALLS", "&4&lDIE NACHT FÄLLT");
    static final T BAR_LULLABY = new T("&eLullaby &8· &f{0}&7/&f{1}", "&eSchlaflied &8· &f{0}&7/&f{1}");
    static final T BAR_PHASE_VESPERS = new T("&7I · Vespers", "&7I · Vesper");
    static final T BAR_PHASE_NIGHT = new T("&7II · The Long Night", "&7II · Die lange Nacht");
    static final T BAR_PHASE_COMPLINE = new T("&7III · Compline", "&7III · Komplet");
    static final T BAR_ENRAGE = new T("&4The night does not end &7in &f{0}s", "&4Die Nacht hört nicht auf &7in &f{0}s");

    /* ------------------------------------------------------------------ The Hush (it only knows the song) */

    static final T SAY_INTRO = new T("Hush now. The lamps are tired.", "Pst. Die Lampen sind müde.");
    static final T SAY_FOUR = new T("Four bells. Then nothing. Then me.", "Vier Glocken. Dann nichts. Dann ich.");
    static final T SAY_LULLABY = new T("Sing it, then. Sing it to the end. ...You can't.",
            "Dann sing es. Sing es zu Ende. ...Kannst du nicht.");
    static final T SAY_FAIL = new T("Shh.", "Pst.");
    static final T SAY_LULLED = new T("...that was almost it.", "...das war fast richtig.");
    static final T SAY_COMPLINE = new T("Every night he forgets a little more. Every night I grow a little more.",
            "Jede Nacht vergisst er ein bisschen mehr. Jede Nacht wachse ich ein bisschen mehr.");
    static final T SAY_FIFTH = new T("No. Not that one.", "Nein. Nicht den.");

    /* ------------------------------------------------------------------ the fifth toll */

    static final T FIVE = new T("&f&lFive.", "&f&lFünf.");
    static final T FIVE_SUB = new T("&7The dusk toll is whole again. &8For tonight.", "&7Die Abendglocke ist wieder ganz. &8Für heute Nacht.");
    static final T NIGHT_LIGHT = new T("&8In the middle of the belfry, a small night light is burning.",
            "&8Mitten im Glockenstuhl brennt ein kleines Nachtlicht.");

    /* ------------------------------------------------------------------ helpers */

    static void send(Collection<Player> to, T line) {
        for (Player p : to) {
            p.sendMessage(TextUtil.component(line.of(p)));
        }
    }

    static void bar(Collection<Player> to, T line) {
        for (Player p : to) {
            p.sendActionBar(TextUtil.component(line.of(p)));
        }
    }

    /** The Hush speaks: grey italics, no name tag in front of the line (it has none). */
    static void say(Collection<Player> to, T line) {
        for (Player p : to) {
            p.sendMessage(TextUtil.component("&8» &7&o" + line.of(p)));
        }
    }

    static void title(Collection<Player> to, T main, T sub, int in, int stay, int out) {
        for (Player p : to) {
            Component m = TextUtil.component(main == null ? "" : main.of(p));
            Component s = TextUtil.component(sub == null ? "" : sub.of(p));
            p.showTitle(Title.title(m, s, Title.Times.times(
                    Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
        }
    }
}
