package de.aetherion.quests.isle;

import java.util.Map;

/**
 * First-talk intros for the Origin island cast (English). German: {@code lang/de.yml → dialogs.<id>_intro}.
 * Kept out of DialogManager's long if-chain on purpose; it asks here first.
 */
public final class IsleLines {

    private IsleLines() {
    }

    private static final Map<String, String[]> INTROS = Map.ofEntries(
            Map.entry("harbourmaster_intro", new String[] {
                    "Quill. Tallyman. Every crate off this pier goes in my book. So does anything that flies past.",
                    "Watch the north rim — when the §bHalcyon§f comes in, the whole harbour stops to look.",
                    "New here? §eEgon§f on the pier first. Then come back and I'll find you something useful."
            }),
            Map.entry("crane_hand_intro", new String[] {
                    "Haul. I move what the Halcyon brings and what the boats forget.",
                    "Pile goes to the punt, the punt goes to the market, the market goes to your pockets.",
                    "Want to help? Don't stand under the hook. That's the whole job."
            }),
            Map.entry("lighthouse_keeper_intro", new String[] {
                    "Moll. I keep the Beacon lit — forty years, one gull short of peace.",
                    "That bird stole my spectacles. Snatched them right off my face.",
                    "It likes high places. The higher, the ruder."
            }),
            Map.entry("shanty_busker_intro", new String[] {
                    "Tobin. Busker, liar, occasionally in tune.",
                    "I'm writing the Harbour Shanty and I'm missing the key.",
                    "The harbour bells know it. They just won't hum it for me."
            }),
            Map.entry("beekeeper_intro", new String[] {
                    "Hettie Comb. I keep the hives at the Old Mead House.",
                    "My queen took a swarm out this morning. Off to the giant flowers, the show-off.",
                    "Mind the hives on your way in. They're friendlier than they sound."
            }),
            Map.entry("painter_intro", new String[] {
                    "Fenna. I paint what the island looks like when nobody's looking.",
                    "I lost a sketch — three places, one painting. I can't remember where I sat.",
                    "A bridge over slow water. A lake of lilies. A shrine under old trees. Sound familiar?"
            }),
            Map.entry("hermit_intro", new String[] {
                    "Hello, traveller. I'm Moss. I used to live in the Capital, now I live under these trees.",
                    "Most people hurry through Whisperwood without ever really listening to it.",
                    "If you like, sit with me for a minute sometime. You'd be surprised what you can hear."
            }),
            Map.entry("bookie_intro", new String[] {
                    "Odds Mulligan. Sports. Legs. Wagers on both.",
                    "The Mountain Gate's up north, past the fountain and the Grand Stair.",
                    "Touch it and get back here inside three minutes, I pay double. Fail and… you get nothing. Character, maybe."
            }),
            Map.entry("mushroom_forager_intro", new String[] {
                    "Cap. I forage. Mostly mushrooms, sometimes trouble.",
                    "At night three of the giant caps around here start to glow.",
                    "Find all three before dawn and I'll share my secret stew. You won't want it. You'll want the coins."
            }),
            Map.entry("lost_tourist_intro", new String[] {
                    "Pim Whistledown! Tourist. Professional. I came for the summit of Skyreach.",
                    "I have been at this fountain for three days.",
                    "Everyone says 'just go up'. Up is a very big direction."
            })
    );

    /** English intro lines for an island-cast dialog id, or null. */
    public static String[] intro(String dialogId) {
        return dialogId == null ? null : INTROS.get(dialogId.toLowerCase(java.util.Locale.ROOT));
    }
}
