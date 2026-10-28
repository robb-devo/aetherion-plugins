package de.aetherion.bossengine.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * German screen titles of the story bosses (Hollow Sun, Aetherion, Nihil, Seraphine). The directors keep writing
 * English; the title helpers pass each line through {@link #title} and German clients read the German one.
 */
public final class BossTitlesDe {

    private static final Map<String, String> DE = Map.ofEntries(
            // Hollow Sun
            Map.entry("&6&lTHE HOLLOW SUN", "&6&lDIE HOHLE SONNE"),
            Map.entry("&7A star fell here. It wore the forge home.", "&7Hier stürzte ein Stern. Die Schmiede trägt er als Rüstung."),
            Map.entry("&f&lLAST LIGHT", "&f&lLETZTES LICHT"),
            Map.entry("&aBreak the core before it ignites!", "&aZerbrich den Kern, bevor er zündet!"),
            Map.entry("&a&lCORE FRACTURED", "&a&lKERN GEBORSTEN"),
            Map.entry("&7He is open — hit him!", "&7Er ist offen – schlag zu!"),
            Map.entry("&c&lRED GIANT", "&c&lROTER RIESE"),
            Map.entry("&7It swells. It does not stop swelling.", "&7Er schwillt an. Und hört nicht auf."),
            Map.entry("&cThe star is failing.", "&cDer Stern erlischt."),
            Map.entry("&4&l☄ STARFALL", "&4&l☄ STERNENFALL"),
            Map.entry("&fGet out of the red. &7Then jump the rings.", "&fRaus aus dem Roten. &7Dann über die Ringe springen."),
            Map.entry("&5&lCOLLAPSE", "&5&lKOLLAPS"),
            Map.entry("&7What is left of me is heavier than light.", "&7Was von mir bleibt, ist schwerer als Licht."),
            Map.entry("&6The Hollow Sun &7has returned to the sky", "&6Die Hohle Sonne &7ist an den Himmel zurückgekehrt"),
            Map.entry("&7Crack the core faster next time.", "&7Knack den Kern nächstes Mal schneller."),
            Map.entry("&4CRUSHED", "&4ZERMALMT"),
            Map.entry("&7Starfall: leave the red disc.", "&7Sternenfall: raus aus der roten Scheibe."),
            Map.entry("&6GRAVITY MACE", "&6SCHWERKRAFT-KEULE"),
            Map.entry("&7Leave the lit cone before it lands.", "&7Raus aus dem leuchtenden Kegel, bevor er einschlägt."),
            Map.entry("&eCORONA", "&eKORONA"),
            Map.entry("&7Back out of the ring. Then punish.", "&7Raus aus dem Ring. Dann bestrafen."),
            Map.entry("&cSOLAR FLARE", "&cSONNENERUPTION"),
            Map.entry("&7Run ahead of the beams — or jump them.", "&7Lauf den Strahlen davon – oder spring drüber."),
            Map.entry("&cPROMINENCE", "&cPROTUBERANZ"),
            Map.entry("&7The lava throws where the line points.", "&7Die Lava fliegt dorthin, wo die Linie zeigt."),
            Map.entry("&5EVENT HORIZON", "&5EREIGNISHORIZONT"),
            Map.entry("&7Walk away from the dark. Never into the red.", "&7Weg vom Dunkel. Nie ins Rote."),
            Map.entry("&5FOLD", "&5FALTUNG"),
            Map.entry("&7The violet sigil is where he lands. Leave it.", "&7Wo das violette Siegel ist, landet er. Geh da weg."),
            // Aetherion, Sovereign of the Ashen Sky
            Map.entry("&7Sovereign of the Ashen Sky", "&7Herrscher des Aschehimmels"),
            Map.entry("&5&lTHE THRONE CLAIMS HIM", "&5&lDER THRON FORDERT IHN"),
            Map.entry("&7Chained. Furious. &fWithin reach of your blades.", "&7Angekettet. Rasend. &fIn Reichweite eurer Klingen."),
            Map.entry("&4&l☀ THE LAST SKY", "&4&l☀ DER LETZTE HIMMEL"),
            Map.entry("&bShelter in the soul wards — &fnow.", "&bIn die Seelenschilde – &fjetzt."),
            Map.entry("&6&lAETHERION UNCHAINED", "&6&lAETHERION ENTFESSELT"),
            Map.entry("&7The last sky burns inside him now.", "&7Der letzte Himmel brennt jetzt in ihm."),
            Map.entry("&5&lTHRONE ECLIPSE", "&5&lTHRONFINSTERNIS"),
            Map.entry("&8The last ash remembers the sky", "&8Die letzte Asche erinnert sich an den Himmel"),
            Map.entry("&8&lTHE LAST SKY", "&8&lDER LETZTE HIMMEL"),
            Map.entry("&7…is shut.", "&7…ist verschlossen."),
            Map.entry("&8&l✦ ASH NOVA ✦", "&8&l✦ ASCHENOVA ✦"),
            Map.entry("&5The Throne takes the sky back.", "&5Der Thron holt sich den Himmel zurück."),
            Map.entry("&bSHELTERED", "&bGESCHÜTZT"),
            Map.entry("&7The Throne kept you.", "&7Der Thron hat dich bewahrt."),
            Map.entry("&4BURNED", "&4VERBRANNT"),
            Map.entry("&7The Last Sky: shelter in a soul ward.", "&7Der Letzte Himmel: in einen Seelenschild."),
            Map.entry("&6&lASHEN WAKE", "&6&lASCHESPUR"),
            Map.entry("&7He burns a lane across the Throne. &fLeave the lane.", "&7Er brennt eine Bahn über den Thron. &fRaus aus der Bahn."),
            Map.entry("&6&lCINDERFALL", "&6&lGLUTREGEN"),
            Map.entry("&7He spits the Throne's ash. &fLeave the ember rings.", "&7Er spuckt die Asche des Throns. &fRaus aus den Glutringen."),
            Map.entry("&c&lTALON DIVE", "&c&lKRALLENSTURZ"),
            Map.entry("&7You are marked. &fKeep moving, then leave the ring.", "&7Du bist markiert. &fBleib in Bewegung, dann raus aus dem Ring."),
            Map.entry("&6&lSKYFIRE", "&6&lHIMMELSFEUER"),
            Map.entry("&7Orbs land where the ember rings bloom. &fSidestep.", "&7Kugeln schlagen ein, wo Glutringe aufblühen. &fAusweichen."),
            Map.entry("&4&lSOVEREIGN'S FALL", "&4&lSTURZ DES HERRSCHERS"),
            Map.entry("&7He falls like a star. &fLeave the circle — then stand between the spokes.",
                    "&7Er fällt wie ein Stern. &fRaus aus dem Kreis – dann zwischen die Speichen."),
            Map.entry("&c&lTAIL SWEEP", "&c&lSCHWANZHIEB"),
            Map.entry("&7The rear arc is about to be swept. &fGet in front of him — or far away.",
                    "&7Gleich fegt er den Bogen hinter sich. &fVor ihn – oder weit weg."),
            Map.entry("&6&lASHFIRE BREATH", "&6&lASCHEFEUER-ATEM"),
            Map.entry("&7It sweeps the way the arrows point. &fGet behind him.", "&7Er fegt in Pfeilrichtung. &fHinter ihn."),
            Map.entry("&6&lTHRONE ERUPTION", "&6&lTHRONAUSBRUCH"),
            Map.entry("&7Three rings of fire. &fStand in the blue gaps.", "&7Drei Feuerringe. &fStell dich in die blauen Lücken."),
            Map.entry("&c&lLUNGE", "&c&lSTURMANGRIFF"),
            Map.entry("&7He hurls himself down the lane. &fSidestep.", "&7Er wirft sich die Bahn hinunter. &fAusweichen."),
            Map.entry("&e&lCLAW", "&e&lKLAUE"),
            Map.entry("&7Yellow &e! &7over his head: &fparry it as it lands.", "&7Gelbes &e! &7über seinem Kopf: &fparieren, wenn es trifft."),
            Map.entry("&5&lCHAIN LASH", "&5&lKETTENPEITSCHE"),
            Map.entry("&7He thrashes a chain. &fGet off its glowing line.", "&7Er peitscht mit einer Kette. &fRunter von der leuchtenden Linie."),
            // Nihil, the World Eater
            Map.entry("&7the World Eater", "&7der Weltenfresser"),
            Map.entry("&8It ate the stars.", "&8Es hat die Sterne gefressen."),
            Map.entry("&8Can't keep up! Is the server overloaded?", "&8Kommt nicht hinterher! Ist der Server überlastet?"),
            Map.entry("&dIt is eating the rules.", "&dEs frisst die Regeln."),
            Map.entry("&5It has become the edge of the world.", "&5Es ist zum Rand der Welt geworden."),
            Map.entry("&4It ate the world.", "&4Es hat die Welt gefressen."),
            Map.entry("&8It is still hungry.", "&8Es hat immer noch Hunger."),
            Map.entry("&7Generating world", "&7Welt wird generiert"),
            Map.entry("&f&lWORLD SAVED", "&f&lWELT GERETTET"),
            Map.entry("&7Nihil ate itself. &8The Last Seed grows back.", "&7Nihil hat sich selbst gefressen. &8Der Letzte Samen wächst nach."),
            // Seraphine, the Hanging Saint
            Map.entry("&6the Hanging Saint", "&6die Gehängte Heilige"),
            Map.entry("&7&oThe Hand descends.", "&7&oDie Hand senkt sich."),
            Map.entry("&4&oUnstrung.", "&4&oDie Fäden sind durch."),
            Map.entry("&f&oCurtain.", "&f&oVorhang."),
            Map.entry("&7Seraphine, the Hanging Saint", "&7Seraphine, die Gehängte Heilige")
    );

    private static final String SPAT = "&7It spat out &f";

    private BossTitlesDe() {
    }

    public static boolean german(Player player) {
        Locale locale = player == null ? null : player.locale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    /** The line for this player: German for German clients when there is one, else as written. */
    public static String line(Player player, String english) {
        if (english == null || english.isEmpty() || !german(player)) {
            return english;
        }
        String de = DE.get(english);
        if (de != null) {
            return de;
        }
        if (english.startsWith(SPAT) && english.endsWith("&7.")) {
            return "&7Es spuckte &f" + english.substring(SPAT.length(), english.length() - 3) + "&7 wieder aus.";
        }
        return english;
    }

    public static Title title(Player player, String main, String sub, Title.Times times) {
        Component a = TextUtil.component(line(player, main));
        Component b = TextUtil.component(line(player, sub));
        return Title.title(a, b, times);
    }
}
