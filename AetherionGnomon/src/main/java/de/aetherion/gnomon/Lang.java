package de.aetherion.gnomon;

import de.aetherion.core.api.AetherServices;
import de.aetherion.gnomon.ai.Read;

import org.bukkit.entity.Player;

/**
 * The Gnomon's voice. Short, dry, personal. English / German via Core's language preference.
 */
public final class Lang {

    private Lang() {
    }

    private static String pick(Player p, String en, String de) {
        return AetherServices.german(p) ? de : en;
    }

    private static String voice(String line) {
        return "§8› §7" + line;
    }

    public static String noticed(Player p, Read read, int sign) {
        return voice(switch (read) {
            case SIDE -> sign < 0
                    ? pick(p, "…left. §fAlways left.", "…links. §fImmer links.")
                    : pick(p, "…right. §fAlways right.", "…rechts. §fImmer rechts.");
            case ORBIT -> sign > 0
                    ? pick(p, "…with the hour. §fLike a hand.", "…mit der Stunde. §fWie ein Zeiger.")
                    : pick(p, "…against the hour. §fNoted.", "…gegen die Stunde. §fNotiert.");
            case HUGGER -> pick(p, "…you hide beneath me.", "…du versteckst dich unter mir.");
            case KITER -> pick(p, "…you keep your distance. §fI measure it.", "…du hältst Abstand. §fIch messe ihn.");
            case GREEDY -> pick(p, "…you never leave an opening alone.", "…du lässt keine Lücke aus.");
            case TIMING -> sign < 0
                    ? pick(p, "…you flinch before I move.", "…du zuckst, bevor ich mich bewege.")
                    : pick(p, "…always the last moment.", "…immer im letzten Moment.");
            case CAMPER -> pick(p, "…this is your corner.", "…das ist deine Ecke.");
        });
    }

    public static String forgotten(Player p) {
        return voice(pick(p, "…changed. §fInteresting.", "…verändert. §fInteressant."));
    }

    public static String outplayed(Player p) {
        return voice(pick(p, "…you wanted me to see that.", "…du wolltest, dass ich das sehe."));
    }

    public static String watching(Player p) {
        return pick(p, "§c◉ §7It is watching you.", "§c◉ §7Es beobachtet dich.");
    }

    public static String intro(Player p) {
        return voice(pick(p, "Hold still. §fI am measuring.", "Halt still. §fIch vermesse."));
    }

    public static String verdict(Player p) {
        return voice(pick(p, "I have read enough.", "Ich habe genug gelesen."));
    }

    public static String recital(Player p) {
        return voice(pick(p, "…again. §fYou will do it again.", "…noch einmal. §fDu tust es wieder."));
    }

    public static String deathKnown(Player p) {
        return voice(pick(p, "…I knew you.", "…ich kannte dich."));
    }

    public static String deathUnknown(Player p) {
        return voice(pick(p, "…unmeasurable.", "…unermesslich."));
    }

    public static String falseOpening(Player p) {
        return voice(pick(p, "…I saw you reach.", "…ich sah dich greifen."));
    }

    public static String title() {
        return "§f§lGNOMON";
    }

    public static String subtitleIntro(Player p) {
        return pick(p, "§7the one who knows", "§7der Wissende");
    }
}
