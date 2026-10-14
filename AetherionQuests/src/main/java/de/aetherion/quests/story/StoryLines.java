package de.aetherion.quests.story;

import de.aetherion.quests.lang.LangPack;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * English lines for the main story beats (dialog ids {@code story_<npc>.<beat>}). German lives in
 * {@code lang/de.yml} under {@code dialogs.story_<npc>.<beat>} with the same beat count.
 * <p>
 * Ids may be chained with {@code +} ("done" lines, then the next offer); each part is localized
 * on its own. Keep beats near 70 characters. Show, don't lecture.
 */
public final class StoryLines {

    private static final Map<String, String[]> EN = new HashMap<>();

    static {
        // ------------------------------------------------------------ Hollis (town_crier) · quest 1
        en("story_town_crier.offer_old_tale",
                "Hear ye! Tonight's tale: the mad king who broke the sky in half!",
                "Not from me. I do headlines. §eMother Berit§f tells it properly.",
                "Her cottage sits at the edge of the Capital. Go before her nap.");
        en("story_town_crier.remind_old_tale",
                "Hear ye! {player} still hasn't heard the tale! Shameful!",
                "§eMother Berit§f. Edge of the Capital. Compass knows.");

        // ------------------------------------------------------------ Mother Berit
        en("story_berit.before",
                "Busy day, love? Finish your errands first.",
                "I'll be right here. My knees insist.");
        en("story_berit.default",
                "Sit, sit. Mother Berit. Oldest woman in town, and proud of it.",
                "I hum at dusk. My gran's song. Old habit.",
                "Hollis the crier sends folk to me for the old tale. Ask him.");
        en("story_berit.old_tale_done",
                "Sit, love. The mad king? He broke the sky in half, they say.",
                "Then the bells sent him away. Here — my gran's old storybook.",
                "She sang me a lullaby with it. That song never had an ending.");
        en("story_berit.gear_hook",
                "Odd thing: the deep tunnels of the §eShabby Mine§f hum it at night.",
                "Whatever the Hollow Lurker guards, take it to §eYsolde§f at the Archive.");
        en("story_berit.offer_gear",
                "The miners say the deep tunnels hum my tune at night. Wrong and slow.",
                "Something down in the §eShabby Mine§f. The Hollow Lurker, they call it.",
                "Whatever it guards, take it to §eYsolde§f at the Archive. Not me.");
        en("story_berit.remind_gear",
                "The humming, love. §eShabby Mine§f, deep tunnels. The Hollow Lurker.",
                "Whatever it guards goes to §eYsolde§f at the Archive.");
        en("story_berit.after_gear",
                "There you are. Ysolde says the humming was a music box. Fancy.",
                "Gran always said the song came out of a little box. I thought she was teasing.");
        en("story_berit.forgotten_done",
                "Oh — hello, dear. Have we met? You have a kind face.",
                "I used to hum something at dusk. How did it go…?",
                "Never mind me. Mind the step on your way out, stranger.");
        en("story_berit.stranger",
                "Hello, dear. New in town?",
                "I keep a lamp by the window. I can't remember why.");

        // ------------------------------------------------------------ Ysolde Thorne (archive)
        en("story_ysolde.before",
                "The Archive doesn't open to people still learning the harbour.",
                "Finish your rounds with §eMiss Ledger§f. Then come back. Quietly.");
        en("story_ysolde.default",
                "Ysolde Thorne. Archivist. Gloves on if you touch anything.",
                "Legends are for the crier. I keep records. Dull and true.",
                "Find something older than the Registry? Bring it to me.");
        en("story_ysolde.remind_gear",
                "Berit sent you? Then bring me whatever the Lurker guards.",
                "§eShabby Mine§f, deep tunnels. Follow the humming.");
        en("story_ysolde.gear_done",
                "A music box gear. Brass, hand-cut. Nobody cuts gears like this now.",
                "Court work. Older than the Registry's first page. Interesting.");
        en("story_ysolde.offer_insignia",
                "Say there was a king. Kings keep guards. Guards wear badges.",
                "§cSkuldugery§f, the burning archer, wears one under the soot.",
                "Bring me that insignia. Your compass finds him while he's up.");
        en("story_ysolde.remind_insignia",
                "The insignia, {player}. §cSkuldugery§f. Burning arrows, loud ego.",
                "Compass finds him while he's up. Bring the badge, not the archer.");
        en("story_ysolde.insignia_done",
                "Sun Guard. Real. Polished for centuries by someone with nothing else.",
                "So: a king, a guard, and at least one man who ran.");
        en("story_ysolde.offer_short_toll",
                "The Registry calls the bell tolls 'custom'. Customs come from somewhere.",
                "§eSister Aurel§f keeps the Seven Bells, out in Scholars' Hall.",
                "Ask her what she's been counting. I want numbers, not poems.");
        en("story_ysolde.remind_short_toll",
                "§eSister Aurel§f, Scholars' Hall. Ask what she's been counting.",
                "Numbers, {player}. Bring me numbers.");
        en("story_ysolde.after_toll",
                "Four strokes at dusk, she says? I've written it down. Twice.",
                "Bring me anything older than the Registry. Seals. Letters. Scraps.",
                "Your island deed comes at Aetherion Level §e20§f. Read the corner.");
        en("story_ysolde.old_seal_done",
                "Eldervale. That was this kingdom's name. The Registry swears it never was.",
                "One page survived. A king named §fCorvin§f. Scholar, not soldier.",
                "Someone burned a name. You don't burn a name unless it hurts to read.");
        en("story_ysolde.offer_letters",
                "Letters keep turning up in old crates. Folded small, signed 'M'.",
                "Open the world chests on your travels. Three letters. Bring all three.",
                "I want to know who 'M' was. The Registry pretends not to.");
        en("story_ysolde.letters_hook",
                "Letters turn up in old crates, signed 'M'. Three, at least.",
                "Open the world chests on your travels. Bring me all three.");
        en("story_ysolde.musing_seal",
                "Eldervale. Corvin. I keep saying them to see if they get lighter.");
        en("story_ysolde.letters_gate",
                "Next lead takes a stronger traveller. Aetherion Level §e30§f.");
        en("story_ysolde.remind_letters",
                "Three letters, {player}. World chests. Open everything you pass.",
                "Sealed, signed 'M'. Don't read them on the road. …You'll read them.");
        en("story_ysolde.letters_done",
                "The seal on the wax is a queen's. Her name was §fMaelis§f.",
                "And a daughter. Look — little stars in the margins. The mad king had a family.");
        en("story_ysolde.offer_third_anchor",
                "There's a camp under the §eFallen Anchor§f. They keep their own records.",
                "Their speaker is §eHesper Kael§f. He won't like you. Go anyway.",
                "Ask about the third anchor. Then listen more than you talk.");
        en("story_ysolde.third_anchor_hook",
                "Camp under the §eFallen Anchor§f. Their speaker is §eHesper Kael§f.",
                "Ask him about the third anchor. He won't like you. Go anyway.");
        en("story_ysolde.musing_letters",
                "A wife. A daughter. The storybook forgot both. I doubt that was an accident.");
        en("story_ysolde.anchor_gate",
                "Next lead is rough country. Come back at Aetherion Level §e40§f.");
        en("story_ysolde.remind_third_anchor",
                "§eHesper Kael§f. The camp under the §eFallen Anchor§f, out west.",
                "Ask about the third anchor. Then let him talk.");
        en("story_ysolde.after_anchor",
                "You spoke to Hesper. Then you know what the Registry left out.",
                "Sixteen moorings in the old harbour plans. I counted them myself.",
                "People in the harbour lanes are forgetting things. Keep your ears open.");
        en("story_ysolde.offer_silent_road",
                "Berit doesn't know you? Then it's back. The old sickness: the Fading.",
                "The Council sealed the King's Road. The §cPathwarden§f still guards it.",
                "Kill the warden. Bring me the writ he carries. Compass finds him.");
        en("story_ysolde.musing_fading",
                "People forgetting their own doors. The old books had a word for it.");
        en("story_ysolde.road_gate",
                "Get stronger, {player}. Aetherion Level §e60§f. Then we open the road.");
        en("story_ysolde.remind_silent_road",
                "The §cPathwarden§f, {player}. The King's Road. Compass finds him.",
                "Bring the writ. Don't break the wax. I want to.");
        en("story_ysolde.silent_road_done",
                "'Let no one walk to him. Let no one walk from him.' Signed, the Council.",
                "One name is Thorne. My great-grandmother signed him out of the books.",
                "The road ends at an old quay into the fog. §fThe Veil Quay§f. It's open now.");
        en("story_ysolde.after_road",
                "I'm keeping the writ. The Registry can ask me for it. Politely.",
                "The Veil Quay is yours to walk. The ferry is another matter. Level §e75§f.");

        // ------------------------------------------------------------ Deed (isle_clerk) · quest 5
        en("story_isle_clerk.offer_old_seal",
                "Your island papers came in. Old seal on the corner. Ignore it.",
                "…You're reading it. Fine. 'Kingdom of Eldervale.' Never heard of it.",
                "Take it to §eYsolde§f at the Archive. She collects old ink. I collect nothing.");
        en("story_isle_clerk.remind_old_seal",
                "The seal, {player}. §eYsolde§f, Archive. Not me. Never me.");

        // ------------------------------------------------------------ Sister Aurel (Hub) · quest 4
        en("story_bellkeeper.short_toll_done",
                "Dawn gets three strokes. Dusk gets five. Always has, dear.",
                "Last night dusk got four. I counted twice. Then a third time.",
                "Nobody else noticed. Tell your archivist. She'll want the number.");

        // ------------------------------------------------------------ Hesper Kael (Fallen Anchor) · quest 7
        en("story_hesper.before",
                "The camp doesn't take visitors still learning the harbour.",
                "Finish your orientation. Then come down, if you must.");
        en("story_hesper.default",
                "Hesper Kael. I speak for the camp under the Anchor.",
                "Tourists come to stare at the broken mooring. You're staring at me.",
                "Stare at the mooring. It's easier.");
        en("story_hesper.third_anchor_done",
                "You've come about the king. Everyone up there comes about the king.",
                "Sixteen worlds were moored to this harbour. He cut the chains. Mine fell.",
                "You've read his wife's letters? Good. Grief is not a pardon.");
        en("story_hesper.after",
                "You listened. Most don't.",
                "It changes nothing about him. Remember that when the story turns sad.");

        // ------------------------------------------------------------ Nell · quest 8
        en("story_nell.before",
                "Hi! Mum says I can't bother people who are still busy.",
                "Are you still busy? You look busy.");
        en("story_nell.default",
                "Hi. I'm Nell. I know all seven bells. Listen: hmm, hmm, hmmm…",
                "Mother Berit taught me. The song stops in the middle. That's normal.",
                "I'm not scared of the dark. I just like lamps. A lot.");
        en("story_nell.offer_forgotten",
                "Mother Berit didn't light her lamp last night. She always does.",
                "This morning she called me 'little miss'. She never calls me that.",
                "Can you check on her? Her cottage, edge of the Capital. Please?");
        en("story_nell.wait",
                "Mother Berit's lamp is acting funny. Mum says it's nothing.",
                "Mum also says only really strong grown-ups help. Like, level §e50§f strong.");
        en("story_nell.remind_forgotten",
                "Did you see her? The cottage at the edge of the Capital.",
                "Her lamp's still dark. I counted the windows twice.");
        en("story_nell.after",
                "Is she okay? She said I have a pretty name. She knows my name.",
                "I lit her lamp for her. Wick showed me how.");

        // ------------------------------------------------------------ Ferryman (Veil Quay)
        en("story_ferryman.before",
                "No passengers today. Finish your business in the harbour first.");
        en("story_ferryman.default",
                "Ferry! Ferry to the — hm. Where does it go again?",
                "Road's been shut a long while. You walked it anyway?",
                "No passengers today. The fog says no. The fog always says no.");
        en("story_ferryman.waiting",
                "A passenger? First in… a while. A long while.",
                "The fog isn't ready for you. Neither is the boat, frankly.",
                "Come back at Aetherion Level §e75§f. The fog likes round numbers.");
        en("story_ferryman.soon",
                "Fog's thinner today. Not thin enough.",
                "Soon. The oar keeps wanting to be used.",
                "Come back soon. I'll have remembered the way by then.");
        en("story_ferryman.offer_ferry",
                "A passenger. With a writ, even. The fog's thin enough today.",
                "Ring the bell by the gangway. Once. It counts. Or step aboard and crouch.",
                "I'll see you on the other side. I'm always on the other side.");
        en("story_ferryman.remind_ferry",
                "Aboard, {player}. Ring the bell once, or stand on the deck and crouch.",
                "The fog doesn't wait. Well. It does. I don't.");
        en("story_ferryman.ferry_done",
                "Told you. Always on this side, somehow.",
                "The King's Quay. Nobody's tied up here since the Silent Year.",
                "The lift on the quay wall goes up to the court. Pull the lever. It bites.");
        en("story_ferryman.captain_hook",
                "Up past the court there's a crater, ringed in iron. Something fell in, burning.",
                "A sentry still stands at the ring. He'll want to hear it's out.");
        en("story_ferryman.offer_captain",
                "Something fell into the crater past the court. Still burning.",
                "There's an iron ring around it, and a sentry who never left. Put the fire out.");
        en("story_ferryman.remind_captain",
                "The crater, {player}. Up the lift, past the court, south. The iron ring.",
                "His sword stands in the middle. It's waiting for an order.");
        en("story_ferryman.exile_default",
                "The lift's on the quay wall. The court's up top. I stay with the boat.",
                "Want to go back across? Just say so. The fog likes a round trip.");

        // ------------------------------------------------------------ Act III · the Exile
        en("story_exile_sentry.default",
                "Nobody through. Captain's orders.",
                "...The Captain's in there. Has been for a while.",
                "Right-click the bars if you must. I didn't see you. I never see anyone.");
        en("story_exile_sentry.remind_captain",
                "Still burning. I feel it through my boots.",
                "His sword's in the middle of the crater. Give it the order. He'll answer.");
        en("story_exile_sentry.captain_done",
                "Quiet. First time in... I don't know how long.",
                "He told the King no, you know. At the third anchor. I heard it.",
                "Relieved. That's the word. Relieved of duty.");

        en("story_orrin.default",
                "Orrin. Court engineer. Retired, technically. The star disagrees.",
                "Please don't touch the ring. It's calibrated to a heartbeat.");
        en("story_orrin.offer_heart",
                "You came to stop her heart. I know. I've known for a thousand years someone would.",
                "I promised the Queen I'd keep it beating until he came back. He hasn't.",
                "Leave. Please. ...No? Then the ring will take you. I'll be on the other side of it.");
        en("story_orrin.remind_heart",
                "The ring is open for you now. I'd rather it weren't.",
                "Go, then. I'll be waiting where the light is.");

        en("story_exile_usher.default",
                "Tickets? Nobody has tickets anymore. Every seat is taken.",
                "The performance never ends. Sir. Madam. Whoever you are.");
        en("story_exile_usher.remind_heart",
                "You'll want the Observatory first, I think. The Queen's seal opens this door properly.");
        en("story_exile_usher.heart_done",
                "You're carrying a letter. Sealed with her mark. I'd know it anywhere.",
                "The doors remember the Queen. So do I. A little.");
        en("story_exile_usher.doll_hook",
                "There's a music box on the stage. Wind it, and she'll begin.",
                "Stay for the whole performance. Then come tell me how it ends.");
        en("story_exile_usher.offer_doll",
                "There's a music box on the stage. Wind it, and she'll begin.",
                "Sit through all of it. Then tell me how it ends. It never does.");
        en("story_exile_usher.remind_doll",
                "The music box, front of the stage. Mind the strings. They aren't hers anymore.");
        en("story_exile_usher.doll_done",
                "It ended? It actually ended.",
                "She held the child. Didn't she. Nobody else was there.",
                "The gardener's boy waits by the roots. He'll want to know the theatre's quiet.");

        en("story_exile_gardener.default",
                "Don't step on the beds. He gets upset.",
                "He's not a he. He's a golem. He waters everything. Even the stones.");
        en("story_exile_gardener.offer_seed",
                "Below the garden there's a hole. The Maw. Something in it eats.",
                "He's down there, guarding it. He never stopped. Please make it stop eating.");
        en("story_exile_gardener.remind_seed",
                "The Maw's at the end of the path. Listen at the gate. You'll hear it chew.");
        en("story_exile_gardener.seed_done",
                "It stopped. The ground's... just ground now.",
                "He came back up. He's watering the stones again.",
                "The castle doors are the last thing still shut. Maybe they'll listen now.");

        en("story_exile_lamplighter.default",
                "Two hundred and twelve lamps. Every evening. I'm late.",
                "Lift's behind you, court's ahead. Doors everywhere. Ask, if you like.");
        en("story_exile_mother.default",
                "We have to be gone before the dusk toll. Have you seen her other shoe?",
                "She's always hiding. Under the stairs, usually. Not today.");
        en("story_exile_keeper.default",
                "The Last Bell's open. Always open. Nobody drinks.",
                "Sit if you like. The chairs remember people.");
    }

    private StoryLines() {
    }

    private static void en(String id, String... lines) {
        EN.put(id, lines);
    }

    public static boolean has(String id) {
        if (id == null) {
            return false;
        }
        for (String part : id.split("\\+")) {
            if (!EN.containsKey(part)) {
                return false;
            }
        }
        return true;
    }

    /** English lines for {@code id} (chained ids concatenated); null when unknown. */
    public static String[] english(String id) {
        if (!has(id)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : id.split("\\+")) {
            out.addAll(List.of(EN.get(part)));
        }
        return out.toArray(String[]::new);
    }

    /** Lines in the reader's language (German from {@code dialogs.<id>}, part by part). */
    public static String[] lines(Player player, String id) {
        if (!has(id)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : id.split("\\+")) {
            out.addAll(List.of(LangPack.dialogs(player, part, EN.get(part))));
        }
        return out.toArray(String[]::new);
    }
}
