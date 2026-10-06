package de.aetherion.items.lore;

import java.util.List;

/**
 * Page text for the readable story finds (English only). A written-book page fits roughly
 * fourteen short lines; keep each page to one beat.
 */
public final class StoryBooks {

    private StoryBooks() {
    }

    public static List<String> pages(String findId) {
        if (findId == null) {
            return List.of("");
        }
        return switch (findId) {
            case "book_mad_king" -> List.of(
                    "§lTHE MAD KING§r\n\nA tale for children who will not go to sleep.\n\n\n§8Approved by the Registry.",
                    "Once there was a king who wanted the whole sky for himself.\n\nHe was not kind.\nHe was not wise.\nHe was mad.",
                    "He had no name.\n\nMad kings do not get to keep one.",
                    "When the sky would not obey him, he broke it.\n\nLike a plate.\nLike a toy.\nLike nothing mattered.",
                    "So the brave people rang the bells, and the bells sent him far away, past the fog, where he can never come back.",
                    "If you hear the bells at night, close your eyes.\n\nThey are only making sure.\n\n\n§lThe End.§r");
            case "maelis_letter_1" -> List.of(
                    "C —\n\nElin won't sleep again. She says the dark has corners.\n\nI wrote her a song tonight. Seven notes, one for each bell.",
                    "She was asleep by the fourth.\n\nYou hum it wrong, by the way. Every single time.\n\nCome home and hum it wrong at her. She likes that best.\n\n— M");
            case "maelis_letter_2" -> List.of(
                    "C —\n\nYou lit the whole harbour with your lamps tonight. The city cheered like you'd caught the moon.",
                    "Elin waved from the window until she fell asleep against the glass.\n\nCome home before the last bell. The workshop will still be there in the morning.\n\n— M");
            case "maelis_letter_3" -> List.of(
                    "C —\n\nThe outer isles are quiet in the wrong way. People forget their doors. Their names.\n\nDon't make that face. I know you're already reading about it.",
                    "Promise me one thing.\n\nFinish the last verse with me. You never do. You always stop at the seventh bell and look at me like the song is over.\n\nIt isn't.\n\n— M");
            case "sealed_writ_silent_year" -> List.of(
                    "§lWRIT OF THE SILENT YEAR§r\n\nBy order of the Council of Eldervale:\n\nLet no one walk to him.\nLet no one walk from him.",
                    "Let his name be struck from every book,\nand theirs with his.\n\nThe King's Road is closed. The Warden stands until relieved.",
                    "Signed for the Council:\n\nA. Morrow\nT. Thorne\nE. Vasko\n\n§8(the rest of the names are scratched out)");
            default -> List.of("");
        };
    }
}
