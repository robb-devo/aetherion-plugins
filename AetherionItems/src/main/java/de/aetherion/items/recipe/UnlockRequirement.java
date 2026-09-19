package de.aetherion.items.recipe;

import org.bukkit.entity.Player;

/*
 * =========================================================
 * UNLOCK REQUIREMENT
 * =========================================================
 *
 * Definiert, wann ein Rezept für einen Spieler freigeschaltet
 * ist.
 *
 * Das System ist bewusst generisch gehalten.
 *
 * Später möglich:
 *
 * - Quest abgeschlossen
 * - Level erreicht
 * - Achievement
 * - mehrere Bedingungen
 * - Kombinationen von Bedingungen
 *
 * =========================================================
 */

public interface UnlockRequirement {


    /*
     * =========================================================
     * UNLOCK CHECK
     * =========================================================
     *
     * true:
     * -> Spieler darf das Rezept ansehen / benutzen.
     *
     * false:
     * -> Rezept bleibt gesperrt.
     *
     * =========================================================
     */

    boolean isUnlocked(
            Player player
    );


    /*
     * =========================================================
     * DISPLAY TEXT
     * =========================================================
     *
     * Text, der im Recipe Book bei einem gesperrten Rezept
     * angezeigt werden kann.
     *
     * Beispiel:
     *
     * "Complete Smith Quest II"
     *
     * =========================================================
     */

    String getDisplayText();


    /*
     * =========================================================
     * ALWAYS UNLOCKED
     * =========================================================
     *
     * Standard-Anforderung für Rezepte, die von Anfang an
     * verfügbar sind.
     * =========================================================
     */

    UnlockRequirement ALWAYS_UNLOCKED =
            new UnlockRequirement() {

                @Override
                public boolean isUnlocked(
                        Player player
                ) {

                    return true;
                }


                @Override
                public String getDisplayText() {

                    return "";
                }
            };
}