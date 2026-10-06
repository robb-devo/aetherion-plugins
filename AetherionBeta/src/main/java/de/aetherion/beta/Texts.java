package de.aetherion.beta;

import java.util.ArrayList;
import java.util.List;

public final class Texts {

    /** Chat prefix for everything the journal says. */
    public static final String JOURNAL = "§d✦ §5Journal §8» ";

    private Texts() {
    }

    public static String t(BetaLang lang, String en, String de) {
        return lang == BetaLang.DE ? de : en;
    }

    /* ------------------------------------------------------------------ courier */

    /** Legacy chat fallback (bubble off). Keep short. */
    public static String npcGreeting(BetaLang lang, String name) {
        return t(lang,
                "§d✦ Patch Courier §8» §fHey §e" + name + "§f — thanks for playing the beta. Here's your §dBeta Journal§f.",
                "§d✦ Patch-Kurier §8» §fHey §e" + name + "§f — danke, dass du die Beta zockst. Hier ist dein §dBeta-Journal§f.");
    }

    public static String npcFarewell(BetaLang lang) {
        return t(lang,
                "§7Lost it? §f/checklist",
                "§7Verloren? §f/checklist");
    }

    /** TalkUx bubble lines — no chat prefix, short. */
    public static String bubbleThanks(BetaLang lang, String name) {
        return t(lang,
                "Hey " + name + " — thanks for jumping into the beta.",
                "Hey " + name + " — danke, dass du die Beta zockst.");
    }

    public static String bubbleBook(BetaLang lang) {
        return t(lang,
                "Here's your Beta Journal. It ticks itself off while you play — right-click it or /checklist.",
                "Hier ist dein Beta-Journal. Es hakt sich beim Spielen selbst ab — Rechtsklick oder /checklist.");
    }

    public static String bubbleChip(BetaLang lang) {
        return t(lang, "Got it.", "Alles klar.");
    }

    public static String bookGiven(BetaLang lang) {
        return t(lang, "§8+ §dBeta Journal", "§8+ §dBeta-Journal");
    }

    public static String alreadyHaveBook(BetaLang lang) {
        return t(lang, "§7You already carry your journal.", "§7Du hast dein Journal schon dabei.");
    }

    public static String openHint(BetaLang lang) {
        return t(lang,
                "§7Right-click the journal or use §f/checklist§7.",
                "§7Rechtsklick aufs Journal oder §f/checklist§7.");
    }

    /* ------------------------------------------------------------------ titles */

    public static String langTitle() {
        return "§5■ §0Language · Sprache";
    }

    public static String checklistTitle(BetaLang lang) {
        return t(lang, "§5■ §0Beta Journal", "§5■ §0Beta-Journal");
    }

    public static String ratingTitle(BetaLang lang) {
        return t(lang, "§5■ §0Beta Feedback", "§5■ §0Beta-Feedback");
    }

    public static String adminTitle() {
        return "§5■ §0Beta · Analysis";
    }

    /* ------------------------------------------------------------------ milestones */

    public static String milestoneName(BetaLang lang, Milestone m) {
        return switch (m) {
            case MEET_GUIDE -> t(lang, "Meet the Patch Courier", "Den Patch-Kurier treffen");
            case QUESTS -> t(lang, "Talk to a quest giver", "Mit einem Questgeber reden");
            case GATHER -> t(lang, "Gather something", "Etwas sammeln");
            case PET -> t(lang, "Meet a pet", "Ein Pet kennenlernen");
            case BOSS -> t(lang, "Bring down a world boss", "Einen Welt-Boss legen");
            case DUNGEON -> t(lang, "Step into a dungeon", "Einen Dungeon betreten");
            case ISLAND -> t(lang, "Visit an island", "Eine Insel besuchen");
        };
    }

    public static String milestoneHint(BetaLang lang, Milestone m) {
        return switch (m) {
            case MEET_GUIDE -> t(lang, "§7Talk to the courier at the harbour.", "§7Sprich mit dem Kurier am Hafen.");
            case QUESTS -> t(lang, "§7Right-click any quest NPC.", "§7Rechtsklick auf einen Quest-NPC.");
            case GATHER -> t(lang, "§7Mine, farm, chop or fish.", "§7Minen, farmen, holzen oder angeln.");
            case PET -> t(lang, "§7Catch, equip or pat a pet.", "§7Ein Pet fangen, ausrüsten oder streicheln.");
            case BOSS -> t(lang, "§7Be there when a boss falls.", "§7Sei dabei, wenn ein Boss fällt.");
            case DUNGEON -> t(lang, "§7Enter any dungeon run.", "§7Starte einen beliebigen Dungeon-Run.");
            case ISLAND -> t(lang, "§7Your own or your guild's island.", "§7Deine eigene oder die Gilden-Insel.");
        };
    }

    public static List<String> milestoneLore(BetaLang lang, Milestone m, boolean done, boolean tracked) {
        List<String> lore = new ArrayList<>();
        lore.add(milestoneHint(lang, m));
        lore.add("");
        if (done && tracked) {
            lore.add(t(lang, "§a✔ Done §8· tracked", "§a✔ Erledigt §8· erkannt"));
        } else if (done) {
            lore.add(t(lang, "§e✔ Ticked by you", "§e✔ Von dir abgehakt"));
            lore.add(t(lang, "§8Turns green once the game sees it.", "§8Wird grün, sobald das Spiel es erkennt."));
        } else {
            lore.add(t(lang, "§8○ Open §8· ticks itself off", "§8○ Offen §8· hakt sich selbst ab"));
            lore.add(t(lang, "§8Already did it? Click to tick.", "§8Schon gemacht? Klick zum Abhaken."));
        }
        return lore;
    }

    /* ------------------------------------------------------------------ journal */

    public static String progressHeader(BetaLang lang) {
        return t(lang, "§d§lBeta Journal", "§d§lBeta-Journal");
    }

    public static String tracked(BetaLang lang, int done, int total) {
        return t(lang, "§7Progress  ", "§7Fortschritt  ") + "§f" + done + "§8/" + total;
    }

    public static String played(BetaLang lang, String duration) {
        return t(lang, "§7Played    §f", "§7Gespielt  §f") + duration;
    }

    public static String feedbackState(BetaLang lang, boolean submitted) {
        return submitted
                ? t(lang, "§7Feedback  §a✔ sent", "§7Feedback  §a✔ gesendet")
                : t(lang, "§7Feedback  §8not yet", "§7Feedback  §8noch offen");
    }

    public static String progressToast(BetaLang lang, String milestone, int done, int total) {
        return JOURNAL + "§a✔ §f" + milestone + " §8(" + done + "/" + total + ")";
    }

    public static String rewardReadyToast(BetaLang lang, String reward) {
        return JOURNAL + t(lang, "§eReward ready: §f", "§eBelohnung bereit: §f") + reward
                + " §8— §7/checklist";
    }

    public static String rewardClaimed(BetaLang lang, String reward, String what) {
        return JOURNAL + t(lang, "§aClaimed §f", "§aAbgeholt: §f") + reward + " §8· " + what;
    }

    public static String rewardsOffline(BetaLang lang) {
        return JOURNAL + t(lang, "§cRewards are offline right now — try again later.",
                "§cBelohnungen sind gerade offline — später nochmal.");
    }

    public static String bookButton(BetaLang lang) {
        return t(lang, "§dGet a new journal", "§dNeues Journal holen");
    }

    public static String close(BetaLang lang) {
        return t(lang, "§cClose", "§cSchließen");
    }

    /* ------------------------------------------------------------------ feedback */

    public static String rateButton(BetaLang lang) {
        return t(lang, "§eRate this session", "§eSession bewerten");
    }

    public static String rateButtonLore(BetaLang lang) {
        return t(lang, "§7Four quick picks — about 20 seconds.", "§7Vier schnelle Klicks — etwa 20 Sekunden.");
    }

    public static String langButton(BetaLang lang) {
        return t(lang, "§bLanguage / Sprache", "§bSprache / Language");
    }

    public static String submitted(BetaLang lang) {
        return JOURNAL + t(lang,
                "§aThanks — your feedback is on the beta board.",
                "§aDanke — dein Feedback liegt auf dem Beta-Board.");
    }

    public static String qOverall(BetaLang lang) {
        return t(lang, "§fOverall feel?", "§fGesamteindruck?");
    }

    public static String qFavorite(BetaLang lang) {
        return t(lang, "§fFavorite part?", "§fLieblingsteil?");
    }

    public static String qConfusing(BetaLang lang) {
        return t(lang, "§fMost confusing?", "§fAm verwirrendsten?");
    }

    public static String qReturn(BetaLang lang) {
        return t(lang, "§fWould you come back?", "§fWürdest du wiederkommen?");
    }

    public static String choice(BetaLang lang, String key) {
        return switch (key) {
            case "great" -> t(lang, "§aGreat", "§aSuper");
            case "okay" -> t(lang, "§eOkay", "§eOkay");
            case "rough" -> t(lang, "§cRough", "§cHarzig");
            case "quests" -> t(lang, "§fQuests", "§fQuests");
            case "gather" -> t(lang, "§fGathering", "§fGathering");
            case "combat" -> t(lang, "§fCombat / bosses", "§fCombat / Bosse");
            case "pets" -> t(lang, "§fPets", "§fPets");
            case "dungeons" -> t(lang, "§fDungeons", "§fDungeons");
            case "nothing" -> t(lang, "§7Nothing really", "§7Eigentlich nichts");
            case "yes" -> t(lang, "§aYes", "§aJa");
            case "maybe" -> t(lang, "§eMaybe", "§eVielleicht");
            case "no" -> t(lang, "§cNot sure", "§cEher nicht");
            default -> key;
        };
    }

    public static String submit(BetaLang lang) {
        return t(lang, "§a§lSubmit feedback", "§a§lFeedback absenden");
    }

    public static String needAnswers(BetaLang lang) {
        return t(lang, "§cPick an answer for each question.", "§cBitte jede Frage beantworten.");
    }

    public static String back(BetaLang lang) {
        return t(lang, "§eBack", "§eZurück");
    }
}
