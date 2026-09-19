package de.aetherion.beta;

import java.util.List;

public final class Texts {

    private Texts() {
    }

    public static String t(BetaLang lang, String en, String de) {
        return lang == BetaLang.DE ? de : en;
    }

    public static String npcGreeting(BetaLang lang, String name) {
        return t(lang,
                "§d✦ §fHey §e" + name + "§f. I'm the beta guide — here's your checklist book.",
                "§d✦ §fHey §e" + name + "§f. Ich bin der Beta-Guide — hier ist dein Checklisten-Buch.");
    }

    public static String npcGreeting2(BetaLang lang) {
        return t(lang,
                "§7The list fills itself when you try things — quests, gathering, pets, bosses, and more.",
                "§7Die Liste hakt sich selbst ab, wenn du Dinge ausprobierst — Quests, Gathering, Pets, Bosse und mehr.");
    }

    public static String npcFarewell(BetaLang lang) {
        return t(lang,
                "§7I'm off. Lose the book? Use §f/checklist §7anytime to open beta feedback.",
                "§7Ich bin weg. Buch weg? Mit §f/checklist §7öffnet sich das Beta-Feedback jederzeit.");
    }

    public static String bookGiven(BetaLang lang) {
        return t(lang,
                "§aBeta checklist book added to your inventory.",
                "§aBeta-Checklisten-Buch liegt in deinem Inventar.");
    }

    public static String alreadyHaveBook(BetaLang lang) {
        return t(lang,
                "§7You already carry the checklist book.",
                "§7Du hast das Checklisten-Buch schon dabei.");
    }

    public static String openHint(BetaLang lang) {
        return t(lang,
                "§7Use §f/checklist §7or right-click the book.",
                "§7Nutze §f/checklist §7oder Rechtsklick aufs Buch.");
    }

    public static String langTitle() {
        return "§8Beta · Language";
    }

    public static String checklistTitle(BetaLang lang) {
        return t(lang, "§8Beta · Checklist", "§8Beta · Checkliste");
    }

    public static String ratingTitle(BetaLang lang) {
        return t(lang, "§8Beta · Feedback", "§8Beta · Feedback");
    }

    public static String adminTitle() {
        return "§8Beta · Analysis";
    }

    public static String milestoneName(BetaLang lang, Milestone m) {
        return switch (m) {
            case MEET_GUIDE -> t(lang, "Meet the guide", "Guide treffen");
            case QUESTS -> t(lang, "Try a quest", "Quest ausprobieren");
            case GATHER -> t(lang, "Try gathering", "Gathering ausprobieren");
            case PET -> t(lang, "Catch or equip a pet", "Pet fangen / ausrüsten");
            case BOSS -> t(lang, "Fight a world boss", "Welt-Boss kämpfen");
            case DUNGEON -> t(lang, "Enter a dungeon", "Dungeon betreten");
            case ISLAND -> t(lang, "Visit an island", "Insel anschauen");
        };
    }

    public static List<String> milestoneLore(BetaLang lang, Milestone m, boolean done, boolean auto) {
        String status = done
                ? t(lang, "§a✔ Done", "§a✔ Erledigt")
                : t(lang, "§8○ Not yet", "§8○ Noch offen");
        String hint = switch (m) {
            case MEET_GUIDE -> t(lang, "§7Talk to the Patch Courier.", "§7Sprich mit dem Patch-Kurier.");
            case QUESTS -> t(lang, "§7Auto when you talk to a quest NPC.", "§7Auto beim Gespräch mit einem Quest-NPC.");
            case GATHER -> t(lang, "§7Auto on mining, farming, foraging, fishing.", "§7Auto bei Mining, Farming, Foraging, Fishing.");
            case PET -> t(lang, "§7Auto when you interact with a pet.", "§7Auto bei Interaktion mit einem Pet.");
            case BOSS -> t(lang, "§7Auto when you help kill a boss.", "§7Auto wenn du einen Boss mitkillst.");
            case DUNGEON -> t(lang, "§7Auto when you enter a dungeon world.", "§7Auto beim Betreten einer Dungeon-Welt.");
            case ISLAND -> t(lang, "§7Auto on guild / personal islands.", "§7Auto auf Gilden- / Privatinseln.");
        };
        String mode = auto
                ? t(lang, "§8Tracks automatically", "§8Wird automatisch erkannt")
                : t(lang, "§8Click to toggle", "§8Klicken zum Umschalten");
        return List.of(status, "", hint, mode);
    }

    public static String rateButton(BetaLang lang) {
        return t(lang, "§eRate this session", "§eSession bewerten");
    }

    public static String rateButtonLore(BetaLang lang) {
        return t(lang, "§7Multiple choice · glass picks", "§7Multiple Choice · Glas-Auswahl");
    }

    public static String langButton(BetaLang lang) {
        return t(lang, "§bLanguage / Sprache", "§bSprache / Language");
    }

    public static String submitted(BetaLang lang) {
        return t(lang,
                "§aThanks — feedback saved for the beta board.",
                "§aDanke — Feedback liegt auf dem Beta-Board.");
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
