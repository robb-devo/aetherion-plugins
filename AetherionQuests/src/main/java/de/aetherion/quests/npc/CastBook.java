package de.aetherion.quests.npc;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Voice sheet for the placed Aetherion cast: return-visit intros, reply labels,
 * reactions, small-talk topics, approach greetings (by story stage), idle barks and
 * NPC-to-NPC banter. Quest facts live in {@code DialogManager}; this is personality.
 * <p>
 * Lines may use {@code {player}} — filled at render time. Keep them short. Dry. No essays.
 */
public final class CastBook {

    private CastBook() {
    }

    public record Topic(String label, String[] lines) {
    }

    public record Beat(String npcId, String line) {
    }

    /** Coarse first-hour stage, used for greetings / moods. */
    public enum Stage {
        ARRIVAL,      // hasn't finished Egon's wood
        HARBOUR,      // wood done, coal/mine/temper running
        SKILLS,       // at Ledger / Fields
        GRADUATE      // tutorial filed
    }

    private static final class Voice {
        String accept = "I'm in.";
        String decline = "Not now.";
        String[] onAccept = {};
        String[] onDecline = {};
        String[] returning;          // replaces the full intro on repeat visits (quest still AVAILABLE)
        String[] greetNew;
        String[] greet;
        String[] greetGrad;
        String[] idle = {};
        String[] farewell = {"Right. Off you go."};
        final List<Topic> topics = new ArrayList<>();
        // Main story ("Das Schlaflied"), per player and only after the tutorial:
        String[] greetLegend;        // mixed into greetings (Act I, public knowledge)
        String[] greetDoubt;         // mixed in from doubtStage on (Act II)
        int doubtStage = 99;
        boolean doubtOnly;           // greetDoubt replaces every greeting (Berit after she fades)
        String storyLabel;           // one stage-aware small-talk topic
        String[] storyLegend;
        String[] storyDoubt;
    }

    private static final Map<String, Voice> VOICES = new HashMap<>();
    private static final List<List<Beat>> BANTER = new ArrayList<>();

    static {
        Voice v;

        // ------------------------------------------------------------ Harbour spine
        v = voice("egon");
        v.accept = "Point me at the oak.";
        v.decline = "Later, Egon.";
        v.onAccept = new String[] {"Good. Lumberjack's up the hill. Don't let him hug you."};
        v.onDecline = new String[] {"Pier's not going anywhere. Neither am I."};
        v.returning = new String[] {
                "Back, {player}. Still no oak. Bold strategy.",
                "§eLumberjack§f, up the hill. §eYellow arrow§f knows the way."
        };
        v.greetNew = new String[] {"Oi, {player}! Over here — pier's got work.", "New face. Pier. Me. Now."};
        v.greet = new String[] {"Kit holding up, {player}?", "{player}. Still in one piece. Good kit."};
        v.greetGrad = new String[] {"Look at you, {player}. Almost dangerous.", "Kit's earned its keep. Nice."};
        v.idle = new String[] {"Who keeps leaving fish on my crates?", "Oak. Always oak. Nobody brings birch.",
                "Thirty years of rookies. Still counting."};
        v.farewell = new String[] {"Mind the edge. Water's cold and I'm not diving."};
        topic(v, "Who are you, exactly?",
                "Egon. I equip people. It's in the name.",
                "Thirty years on this pier. Rookies come in wet, leave armoured.");
        topic(v, "What is this place?",
                "Anker Harbour. Boats in, rookies out.",
                "Capital's up the road. Mines past that. Don't rush it.");
        topic(v, "Any tips?",
                "Watch the §eyellow arrow§f. It's smarter than most people I've kitted.",
                "And talk to folk. Half this harbour gives free advice. The other half charges.");
        v.greetLegend = lines("Weather's fine, {player}. Just don't sail toward the Veil.");
        v.greetDoubt = lines("{player}. See a ferry with no rower? Don't wave.");
        story(v, 4, "The Veil?",
                lines("Fog wall past the outer buoys. Never sail past it.",
                        "Old sailor's rule. Nobody knows who made it. Everybody keeps it."),
                lines("Fishers swear there's a ferry by the old royal quay. Nobody rowing.",
                        "I don't sail that way. Neither should you."));

        v = voice("lumberjack");
        v.accept = "Hand me the axe.";
        v.decline = "Maybe later.";
        v.onAccept = new String[] {"Timber! …Sorry. Habit. Glowing trunks only."};
        v.onDecline = new String[] {"Trees'll wait. They're good at that."};
        v.returning = new String[] {
                "{player}! Back for the axe lesson? Glowing trunk, green §aCHOP§f.",
                "Ten oak logs → §eEgon on the pier§f. Not me. I've got plenty."
        };
        v.greetNew = new String[] {"Axe-curious, {player}? Egon sent you, didn't he."};
        v.greet = new String[] {"{player}! Heard any trees fall lately?", "Arms sore yet, {player}? Good."};
        v.greetGrad = new String[] {"There's the chopper. Trees still whisper about you, {player}."};
        v.idle = new String[] {"Timber!… sorry. Habit.", "That oak's looking at me funny.",
                "Birch is just oak that gave up.", "Call me Forager. It sounds kinder."};
        v.farewell = new String[] {"Mind your toes. Axes don't."};
        topic(v, "Lumberjack or Forager?",
                "Sign says Lumberjack. I say Forager.",
                "Lumberjack sounds violent. Forager sounds like I care. I do care. I also chop.");
        topic(v, "Tree tips?",
                "Bigger trees, bigger bars. Don't get greedy.",
                "Glowing trunks only. The rest are decorative. Emotionally.");

        v = voice("quartermaster");
        v.accept = "Twenty lumps. On it.";
        v.decline = "Not today.";
        v.onAccept = new String[] {"Logged. You have a job now, {player}."};
        v.onDecline = new String[] {"Noted. With disappointment."};
        v.returning = new String[] {
                "Coal count still zero, {player}. I checked. Twice.",
                "§eOre Ridge§f. Hill past the market. Twenty lumps."
        };
        v.greetNew = new String[] {"Ah. {player}. You're on my list."};
        v.greet = new String[] {"{player}. Inventory looks healthy. Suspiciously.", "{player}. Still on my list. Good list."};
        v.greetGrad = new String[] {"{player}. Page twelve of my ledger. The good page."};
        v.idle = new String[] {"Forty-two crates. It was forty-three this morning.", "Who signed for this anchor?",
                "Nope. Map's still wrong."};
        v.farewell = new String[] {"Dismissed. Politely."};
        topic(v, "What do you supply?",
                "Everything. Eventually. Paperwork first.",
                "Right now: a forge that eats coal and a harbour that eats forges.");
        topic(v, "How do teleports work?",
                "Walk somewhere once — it's unlocked. Or plant a Homestead Marker.",
                "Then Manager → Teleports, or type the place. §e/harbour§f, §e/mines§f. Like that.");

        v = voice("craftsman");
        v.greetNew = new String[] {"Hm. {player}. Recipe Book's in your Manager."};
        v.greet = new String[] {"Hm. {player}.", "Pick's holding? Hm. Good."};
        v.greetGrad = new String[] {"Hm. Good work lately, {player}. Don't let it go to your head."};
        v.idle = new String[] {
                "This hammer was my father's. The handle was my grandfather's. The head is new, though.",
                "Iron gets moody when it's cold. So do I, so we understand each other.",
                "Bring me coal and patience. I'll supply the noise.",
                "Every pickaxe on this harbour has been through my hands at least once."
        };
        v.farewell = new String[] {"Hm."};
        topic(v, "What makes good gear?",
                "Old piece in the middle. Resources around it. That's the whole secret.",
                "Rest is sweat. Mostly yours.");
        topic(v, "Where's the Recipe Book?",
                "Nether Star, hotbar §e9§f. Green book. Every blueprint's in there.");

        v = voice("foreman");
        v.accept = "Clocking in.";
        v.decline = "Not yet.";
        v.onAccept = new String[] {"Mine's behind me. Helmet on. Oh — no helmet. Brave."};
        v.onDecline = new String[] {"Mine'll wait. Been waiting since forever."};
        v.returning = new String[] {
                "Thirty-two ores, {player}. Cave's behind me. It doesn't bite. Much.",
                "Coal, copper, iron. All count."
        };
        v.greetNew = new String[] {"Hard hats save lives, {player}. I'm told."};
        v.greet = new String[] {"{player}! Back for another shift?", "Dust suits you, {player}."};
        v.greetGrad = new String[] {"Look who's a miner now. Proud of you. Don't tell anyone."};
        v.idle = new String[] {
                "Twenty years down in the shafts, and I still hate spiders.",
                "Quiet in the tunnels today. In a mine, quiet is a good sign.",
                "First rule down there: never dig straight up. Second rule: see the first rule.",
                "My pipe went out an hour ago. I keep it in my mouth for courage."
        };
        v.farewell = new String[] {"Watch the drops. Watch the dark. Watch yourself."};
        topic(v, "Is it safe down there?",
                "Safe? It's a mine, {player}.",
                "Mostly. Watch the drops. Watch the dark. Watch me not going in.");
        topic(v, "The pipe?",
                "Keeps the dust out. Doctor says otherwise. Doctor doesn't mine.");

        v = voice("booster_tutor");
        v.accept = "Let's fuse something.";
        v.decline = "Maybe later.";
        v.onAccept = new String[] {"Yes! Emerald's in your bag. Anvil's in the Manager. Don't lick it."};
        v.onDecline = new String[] {"Your gear stays boring, then. Your call."};
        v.returning = new String[] {
                "Still no fuse, {player}? The Emerald's waiting on your yes.",
                "Then Manager → §eAnvil§f. Gear left, booster right."
        };
        v.greetNew = new String[] {"{player}! Got anything I can set on fire? Upgrade. I meant upgrade."};
        v.greet = new String[] {"Smell that, {player}? Progress. Also mild burning."};
        v.greetGrad = new String[] {"{player}! Fused anything spicy lately?"};
        v.idle = new String[] {
                "That spark? Completely intentional. Mostly intentional.",
                "Goggles on, always. I learned that the bright way.",
                "A booster on the Anvil is power. A booster in your pocket is just a pretty rock.",
                "I've blown up three workbenches. The fourth one is doing great."
        };
        v.farewell = new String[] {"Stay flammable. Wait. No. Don't."};
        topic(v, "Is there a limit?",
                "There's a cap. Don't ask me to raise it.",
                "I tried once. There was a fire. A good fire, but still.");
        topic(v, "Best booster?", "The one you actually fuse, {player}.");

        v = voice("ledger");
        v.accept = "Stamp me in.";
        v.decline = "Later.";
        v.onAccept = new String[] {"Noted. Skills tab. Slot nine. It blinks."};
        v.onDecline = new String[] {"Your file stays open. It judges you."};
        v.returning = new String[] {
                "{player}. The stamp's waiting. So am I.",
                "Say yes, equip §eany one§f skill, come back."
        };
        v.greetNew = new String[] {"{player}. Present. Good."};
        v.greet = new String[] {"{player}. On time. Almost.", "Posture, {player}."};
        v.greetGrad = new String[] {"Graduate {player}. Try not to embarrass the stamp."};
        v.idle = new String[] {
                "Everything gets a stamp. Mistakes get two.",
                "Late, late, early, late. My calendar has opinions about people.",
                "Who writes a nine like that? It looks like a lost snail.",
                "Every page in here is somebody's first day on Aetherion. I keep them all."
        };
        v.farewell = new String[] {"Dismissed."};
        v.greetDoubt = lines("{player}. No records before the Silent Year. That's no accident.");
        v.doubtStage = 3;
        topic(v, "What are skills?",
                "Equipped trees. Not homework.",
                "One slot free at start. More unlock with coin milestones.");
        topic(v, "What's left for me?",
                "Fields first. Then my stamp.",
                "After that, every skill has an isle. I'll point.");

        v = voice("farmer");
        v.accept = "Let's harvest.";
        v.decline = "Not now.";
        v.onAccept = new String[] {"Watch the sky, {player}. They're planning something."};
        v.onDecline = new String[] {"Birds win another round."};
        v.returning = new String[] {
                "Birds are winning, {player}. §e48§f wheat. And shoo them.",
                "They land — you click. Bossbar counts."
        };
        v.greetNew = new String[] {"{player}! Seen any birds? Don't trust them."};
        v.greet = new String[] {"Wheat's growing, {player}. So's the bird problem."};
        v.greetGrad = new String[] {"{player}! The birds remember you. Good."};
        v.idle = new String[] {
                "Shoo, crow! That's my wheat, not your breakfast!",
                "That crow has been staring at me for an hour. I think it wants my hat.",
                "It'll rain soon. Or the birds are lying again. Probably the birds.",
                "Good soil is like a good friend. Feed it, and it remembers you."
        };
        v.farewell = new String[] {"Eyes up. They're always up there."};
        topic(v, "Why the birds?", "They know what they did.", "Every season. Same birds. I recognise that one.");
        topic(v, "Farm Isle?", "Harlow, far end of the barn. He knows the way down.", "Farming ten. Bigger fields. Fewer birds.");

        v = voice("lark");
        v.accept = "Show me the spheres.";
        v.decline = "Later.";
        v.onAccept = new String[] {"Aim low, {player}. Pigs are short."};
        v.onDecline = new String[] {"The animals will be relieved."};
        v.returning = new String[] {
                "Sphere in hand, look at a critter, §eright-click§f. One catch, {player}.",
                "Then come back. Pistachio wants to meet it."
        };
        v.greetNew = new String[] {"{player}! Mind the parrot. She's judging your shoes."};
        v.greet = new String[] {"{player}! Pistachio remembers you. That's not a compliment."};
        v.greetGrad = new String[] {"{player}! Your pet's famous around here. Well. To me."};
        v.idle = new String[] {
                "Pistachio, no. That's not a snack, that's a stranger's hat.",
                "Who's a good bird? Not you, Pistachio. You bit the mayor.",
                "A pet sphere is a promise. You catch something, you look after it.",
                "Every pet on this island has a name. Most of them chose it themselves."
        };
        v.farewell = new String[] {"Pistachio says bye. She doesn't. But imagine."};
        topic(v, "Your parrot?",
                "Pistachio. Don't make eye contact.",
                "She takes it as a challenge.");
        topic(v, "Best pet?", "The one that follows you.", "Dragons get dramatic about levels. Worth it.");

        v = voice("fisher");
        v.accept = "I'll cast.";
        v.decline = "Not today.";
        v.onAccept = new String[] {"Patience. Fish can smell hurry."};
        v.onDecline = new String[] {"Sea'll wait. Always does."};
        v.returning = new String[] {
                "Five fish, {player}. Cast. Wait. §eREEL§f on green.",
                "Miss the window, the fish leaves. So would I."
        };
        v.greetNew = new String[] {"Tackle. Harbour practice first, {player}. Then the west pad."};
        v.greet = new String[] {"Line's calling, {player}.", "Wind's right. Fish aren't."};
        v.greetGrad = new String[] {"{player}. Real water's on the west pad — Fishing Eldervale."};
        v.idle = new String[] {
                "East wind today. The fish sulk when it blows from the east.",
                "Something nibbled, then it changed its mind. That's my whole life in one sentence.",
                "The trick is patience. The other trick is better bait.",
                "My grandfather fished this harbour. He caught a boot once and wore it to his wedding."
        };
        v.farewell = new String[] {"Fair winds."};
        topic(v, "Fishing tips?", "Patience, {player}.", "And green. Reel on green. The rest is weather.");
        topic(v, "Seen anything big?", "Once.", "Didn't reel it. Still think about it.");

        v = voice("fishmonger");
        v.greetNew = new String[] {"Fresh today! Mostly!"};
        v.greet = new String[] {"{player}! Cod? Salmon? Regret?", "Rods, armour, fish. Coins welcome."};
        v.idle = new String[] {"Fresh cod! Freshish!", "Salmon! Barely used!", "Don't poke the fish."};

        v = voice("vex");
        v.accept = "Sir, yes sir.";
        v.decline = "Not ready.";
        v.onAccept = new String[] {"MOVE, {player}! Ten! Go!"};
        v.onDecline = new String[] {"Then get ready. Clock's running."};
        v.returning = new String[] {
                "Ten hostiles, {player}. Borderlands. They won't kill themselves.",
                "Husk, stray, crawler. I don't care which."
        };
        v.greetNew = new String[] {"Eyes front, {player}!"};
        v.greet = new String[] {"{player}. Still breathing. Adequate.", "Chin up, {player}. Blade up."};
        v.greetGrad = new String[] {"{player}. Soldier. …Don't let that go to your head."};
        v.idle = new String[] {
                "Left, right, left! Even the gulls march better than my recruits.",
                "A sharp blade and a sharp mind. Only one of those can be bought.",
                "Up, down, breathe, repeat. That's the whole secret of fighting. And don't get hit.",
                "I sharpen this sword every morning. It has never once thanked me."
        };
        v.farewell = new String[] {"Dismissed! Walk like you mean it."};
        topic(v, "Any advice?", "Swing first. Think second. Survive third.", "Actually — think first. Write that down.");
        topic(v, "The scar?", "Training accident.", "The training won.");

        v = voice("rite_keeper");
        v.accept = "I'll light it.";
        v.decline = "Not yet.";
        v.onAccept = new String[] {"Good. Light the powder at the pillar, and be ready when the thunder comes."};
        v.onDecline = new String[] {"Spirits are patient. I am less so."};
        v.returning = new String[] {
                "You have the vial, {player}. Pour the powder out at the pillar.",
                "Then wait about ten seconds. You'll hear thunder, and then the spirit steps out to fight."
        };
        v.greetNew = new String[] {"Welcome to the Borderlands, {player}. The wastes keep an eye on everyone who comes here."};
        v.greet = new String[] {"The spirits stir when you walk past, {player}. They remember you."};
        v.greetGrad = new String[] {"The wastes are watching you, {player}. I think they are impressed."};
        v.idle = new String[] {
                "A storm is coming in from the wastes. You can smell it in the dust.",
                "The old rites keep the Borderlands calm. Mostly, they keep me busy.",
                "Mind the circle. It's older than the road you came in on."
        };
        v.farewell = new String[] {"Walk softly out there. Things are listening."};
        topic(v, "What are spirits?", "Old souls that never quite finished dying. They linger out in the wastes.",
                "The vial holds them, the powder wakes them, and you put them to rest.");

        v = voice("arena_proctor");
        v.greetNew = new String[] {"Ticket, {player}? No? Then don't loiter."};
        v.greet = new String[] {"Oh. {player}. Still alive. Noted."};
        v.idle = new String[] {
                "Nobody has passed the trial today. Nobody tried, either.",
                "The Colosseum remembers every fight. Some of them it would rather forget.",
                "Bring a vial, bring courage, and bring a spare pair of boots."
        };
        v.farewell = new String[] {"Mind the ring. It's older than your excuses."};
        topic(v, "What happened here?", "Exams, monsters and paperwork, in that order.", "These days it's mostly paperwork, sadly.");

        v = voice("farm_isle_guide");
        v.greetNew = new String[] {"Harlow. Farm Isle pad's down the road, {player}. Farming §e10§f."};
        v.greet = new String[] {"Pad's that way, {player}. Arrow knows.", "Soil's good today, {player}."};
        v.idle = new String[] {
                "The soil on the Farm Isle is better than here. Don't tell the farmer I said that.",
                "The pad thumps all day long. I stopped hearing it years ago.",
                "Chewing on wheat helps me think. It doesn't really help, but I keep trying."
        };
        v.farewell = new String[] {"Knees bent on the pad. Don't get clever."};
        topic(v, "What's on the isle?", "Bigger fields. Fewer birds. Better soil.", "Don't tell the Farmer.");
        topic(v, "You and the Farmer?", "He thinks he's the real farmer.", "Cute.");

        v = voice("surveyor");
        v.greetNew = new String[] {"{player}! Seen any trolls? Big ones? Tell me everything."};
        v.greet = new String[] {"Got a page for me, {player}?"};
        v.idle = new String[] {
                "North is that way. Probably. My compass and I aren't speaking today.",
                "Trolls are fascinating. Terrible, but fascinating. I've measured three footprints so far.",
                "Every map I draw is out of date the moment someone builds a shed."
        };
        v.greetLegend = lines("{player}! Seen a troll with chain? Tell me everything.");
        v.greetDoubt = lines("{player}. Royal forge-work in a troll's pocket. I can't sleep.");
        story(v, 5, "Troll chain?",
                lines("Bridge trolls hoard chain. Links the size of my head.",
                        "Where do they find it? Nobody forges links like that anymore."),
                lines("I measured a link. Royal forge-work. From the old moorings.",
                        "Clean cuts on both ends. Somebody did that on purpose."));

        v = voice("vince");
        v.greetNew = new String[] {"{player}! Feeling lucky? You look lucky."};
        v.greet = new String[] {"{player}! My favourite customer. Today."};
        v.idle = new String[] {
                "Somebody's going to win big tonight. Statistically, it's me.",
                "Red, always red. My lucky colour, and my bank's least favourite.",
                "The machines don't care how lucky you feel. That's what makes them honest."
        };
        v.farewell = new String[] {"Come back richer. Or poorer. I'm not picky."};
        topic(v, "Any tips?", "House always wins.", "I'm the house's cousin. I do alright.");
        topic(v, "Are you actually lucky?", "Lucky Vince. It's in the name.", "Names lie, {player}.");
        v.greetLegend = lines("{player}! A hundred says the mad king's a bedtime story. In?");
        v.greetDoubt = lines("{player}. That bet about the king? Off. Don't ask.");
        story(v, 5, "The mad king?",
                lines("Bet you a hundred the mad king's just a story.",
                        "Kings don't break skies. Kings break banks. Different job."),
                lines("Bet's off. Bet's very off.",
                        "House rule: never bet against something that already happened."));

        v = voice("bar_whisper");
        v.greetNew = new String[] {"Psst. {player}. Look down. The bar."};
        v.greet = new String[] {"your bar moved, {player}… i felt it."};
        v.idle = new String[] {
                "Every fight, every swing, every catch adds a little to your level. The bar remembers.",
                "I can always tell who's close to a level up. They walk a little taller.",
                "Rest when you need to. Experience waits for you, it doesn't run away."
        };
        v.farewell = new String[] {"keep playing. it notices."};
        topic(v, "How do I fill it?", "Play. Quest. Mine. Fish.", "Everything whispers to it, {player}.");
        v.greetLegend = lines("the bar remembers you, {player}…");
        v.greetDoubt = lines("some bars stop remembering… then the people stop too…");
        story(v, 6, "Remembers?",
                lines("Everything you do, the bar keeps. Every swing. Every catch.",
                        "It remembers you, {player}. That's nice. Isn't it?"),
                lines("Lately some bars just… go quiet. Mid-word.",
                        "Then the people go quiet too. Keep playing. Please."));

        v = voice("eldervale_welcome");
        v.greetNew = new String[] {"Maren. Eldervale landing, {player}. Ask before you dig deep."};
        v.greet = new String[] {"Back on the mountain, {player}? Good lungs."};
        v.idle = new String[] {
                "Wrap up warm. Snow comes to Eldervale by nightfall, every single night.",
                "Mind the ledge, love. The view is lovely, the fall less so.",
                "Welcome to Eldervale. It's quieter up here, and the tea is better."
        };
        v.farewell = new String[] {"Stay off the edge. I mean it."};
        topic(v, "What's here?", "Ore the mainland forgot.", "And a forge that remembers everything.");
        topic(v, "How far is the drop?", "Long.", "Nobody's reported back. That's the joke.");

        v = voice("eldervale_upgrade");
        v.greetNew = new String[] {"Heat's up, {player}."};
        v.greet = new String[] {"Bring me steel, {player}. And stones."};
        v.idle = new String[] {
                "Hotter, always hotter. Stone only listens when it's angry.",
                "Every blueprint in here started as a scribble on a napkin.",
                "Hear that ring? That's good stone. Bad stone just thuds."
        };

        v = voice("merchant");
        v.greetNew = new String[] {"Ah, {player}! A customer! Or a browser. Both welcome."};
        v.greet = new String[] {"{player}! Found any good chests?"};
        v.idle = new String[] {
                "Chests full of adventure! Some of it is even legal!",
                "I polish my emeralds every morning. They shine brighter when customers are near.",
                "Everything must go eventually. Except me. I'm staying."
        };
        v.farewell = new String[] {"Open everything. Trust nothing. Especially mimics."};
        topic(v, "What do you sell?", "Opportunity. Wrapped in wood.", "The chests, {player}. I sell the idea of chests.");

        v = voice("isle_clerk");
        // Lullaby step 5 (the old seal) — chips/reactions only show on that offer.
        v.accept = "Archive it is.";
        v.decline = "I'll ignore it.";
        v.onAccept = new String[] {"Good. Less paper on my desk."};
        v.onDecline = new String[] {"Ignoring things is my job. Don't take it."};
        v.returning = new String[] {
                "The seal, {player}. §eYsolde§f at the Archive. Not me.",
                "I still don't do paperwork. Especially old paperwork."
        };
        v.greetNew = new String[] {"Island dreams, {player}? Level twenty. Then dream."};
        v.greet = new String[] {"Paperwork? No. Never. Next."};
        v.idle = new String[] {
                "Everyone wants their own island. Nobody wants the paperwork.",
                "Form 20-B doesn't exist. People still ask me for it every day.",
                "Your island is waiting for you. It's patient. I'm less patient."
        };
        v.farewell = new String[] {"Go level. I'll be here. Not doing paperwork."};
        topic(v, "A clerk who hates paperwork?", "Yes.", "I know.");
        v.greetLegend = lines("Old seals on old deeds, {player}. Ignore them. I do.");
        v.greetDoubt = lines("{player}. We don't talk about the seal. Next.");
        story(v, 3, "That old seal?",
                lines("Paperwork from before paperwork. Some seals are older than the desk.",
                        "I stamp nothing. I ask nothing. Next."),
                lines("Kingdom of Eldervale. I looked it up. Then someone told me to stop.",
                        "So I stopped. I'm very good at stopping."));

        v = voice("forage_pad_guide");
        v.greetNew = new String[] {"Twig! Slime pad → Forage Isle, {player}. Jump. Arrow's on it."};
        v.greet = new String[] {"Pad's right there, {player}. Knees bent!", "Back from the isle, {player}? Smell that spruce!"};
        v.idle = new String[] {
                "The pad is a bit sticky today. That's the good kind of sticky, I promise.",
                "Hop on, fly over, land soft. That's the plan every single time.",
                "The forage isle grows things you won't find on the main island."
        };
        v.farewell = new String[] {"Jump with your knees!"};
        topic(v, "Is the pad safe?", "Totally.", "Ninety percent totally.");

        v = voice("liquidator");
        v.greetNew = new String[] {"{player}. Crystals whisper about you. Mostly numbers."};
        v.greet = new String[] {"Buying or melting, {player}?"};
        v.idle = new String[] {
                "Amethyst comes in raw and leaves as coin. That's my whole job, and I love it.",
                "Purple isn't a colour around here. It's a price.",
                "Melt it, mint it, count it twice. I've never lost a single shard."
        };

        v = voice("amethyst_mines_guide");
        v.greetNew = new String[] {"I'm the crystal guide. Talk to me or use §e/amethyst§f to get to the Amethyst Mine."};
        v.greet = new String[] {"Welcome back, {player}! The Amethyst Mine is ready. Click me or use §e/amethyst§f."};
        v.idle = new String[] {
                "The geodes hum when you get close. Listen, and they'll show you the way.",
                "Take your time in the mines. Crystals don't like to be rushed.",
                "A purple glow ahead means amethyst. A purple glow behind you means run."
        };
        v.farewell = new String[] {"Have fun in the mines, and don't wander too deep on your own."};

        v = voice("canopy_clerk");
        v.accept = "Samples coming.";
        v.decline = "Not now.";
        v.onAccept = new String[] {"Compressed, {player}. I will check."};
        v.onDecline = new String[] {"The canopy can wait. It's patient."};
        v.returning = new String[] {
                "Compressed Oak, Birch, Spruce, {player}. Isle wood.",
                "Harbour leftovers don't count. I can smell them."
        };
        v.greetNew = new String[] {"{player}. Any bark on you? Samples, I mean."};
        v.greet = new String[] {"Ring count's up, {player}. Nice season."};
        v.idle = new String[] {
                "This log smells like spruce. Definitely spruce. I'd bet my lunch on it.",
                "Forty rings on this one. Older than me, and in better shape.",
                "Every tree I write down gets a number. The old ones get names."
        };
        v.farewell = new String[] {"Chop responsibly."};
        topic(v, "Why samples?", "Proof the isle grows real wood.", "The canopy keeps a ledger. So do I.");

        v = voice("root_cellar");
        v.greetNew = new String[] {"Hungry, {player}? Everyone's hungry."};
        v.greet = new String[] {"Fresh flour, {player}. Mind your boots."};
        v.idle = new String[] {
                "There's flour everywhere. In my hair, in my shoes, in my dreams.",
                "Carrots don't pickle themselves, sadly. I've asked them nicely.",
                "The mill wheel turns all day. Some days I turn with it."
        };

        // ------------------------------------------------------------ Idea NPCs (extras)
        v = voice("town_crier");
        // Lullaby step 1 (the old tale) — chips/reactions only show on that offer.
        v.accept = "I'll go hear it.";
        v.decline = "Not tonight, Hollis.";
        v.onAccept = new String[] {"Hear ye! {player} goes to learn some history! Rare!"};
        v.onDecline = new String[] {"The tale's a thousand years old. It'll keep."};
        v.returning = new String[] {
                "Hear ye! {player} still hasn't heard the tale!",
                "§eMother Berit§f. Edge of the Capital. Compass knows."
        };
        v.greetNew = new String[] {"Hear ye! {player} walks among us!"};
        v.greet = new String[] {"Oyez, {player}! News at eleven. Also now."};
        v.idle = new String[] {
                "Hear ye! Your Manager lives in hotbar slot nine!",
                "Hear ye! Boosters go on the Anvil, not in your mouth!",
                "Hear ye! Talk to strangers! They have quests!",
                "Hear ye! Mind your children, or Aetherion comes for them!",
                "Hear ye! Chests spawn in the wild! Go open things!"
        };
        v.farewell = new String[] {"Go forth! Loudly!"};
        topic(v, "Any news?", "The harbour stands. The mines are dusty. The birds are winning.", "That's the news.");
        topic(v, "Why shout?", "Whispering doesn't scale, {player}.");
        v.greetLegend = lines("Hear ye! Mind your children, {player}! …None? Mind yourself, then.");
        v.greetDoubt = lines("Hear ye… {player}. You remember my name? Good. Just checking.");
        story(v, 5, "The mad king?",
                lines("Not shouting this one. King went mad, broke the sky, got sent away.",
                        "Kids' story. §eMother Berit§f tells it with the scary voice."),
                lines("People keep forgetting things lately. Names. Doors. Kettles.",
                        "The old tale had a word for that. I'm not shouting it."));

        v = voice("street_sweeper");
        v.greetNew = new String[] {"Wipe your boots, {player}."};
        v.greet = new String[] {"Back again, {player}? Bring any gravel? Don't."};
        v.idle = new String[] {
                "Who drops a whole cobblestone in the middle of the street? Who does that?",
                "Cleanest square on the island. You're welcome, by the way.",
                "I sweep the same street every day, and every day it finds new ways to get dirty.",
                "Found a gold nugget in the gutter once. Bought new bristles. Living the dream."
        };
        v.farewell = new String[] {"Mind the pile."};
        topic(v, "Seen anything odd?", "A troll's footprint by the mines. Size of a boat.", "I swept it. Evidence gone. Shouldn't have.");

        v = voice("lamp_lighter");
        v.greetNew = new String[] {"Evening's coming, {player}. I can feel it."};
        v.greet = new String[] {"Stay in the light, {player}."};
        v.idle = new String[] {
                "I polish every lantern on my round. Bright lamps get people home safely.",
                "Dusk in a few hours. I'm already ready. I'm always ready.",
                "Every lamp on this harbour has a name. That one over there is Gerald.",
                "One more wick, always one more. The night is long and the island is big."
        };
        v.farewell = new String[] {"Keep a torch handy."};
        topic(v, "Why lamps?", "Hostiles hate light. I love it. We have a truce.", "Mostly they just avoid me.");
        v.greetLegend = lines("Every lamp lit till dawn, {player}. Old law. I just keep it.");
        v.greetDoubt = lines("{player}. Lamps are lit. Every one. I checked twice.");
        story(v, 4, "The old law?",
                lines("Lanterns stay lit till dawn. Every one. Old law.",
                        "Nobody knows whose. I don't need to know. I just keep it."),
                lines("My gran said a queen wrote it. For the children.",
                        "So they'd never wake up in the dark."));

        // ------------------------------------------------------------ Hub townsfolk (AetherionHub owns the NPC)
        // Sister Aurel and Stellan Voss speak through the Hub's own bubble; the Hub asks for their
        // lines via StoryAccess.bark so the Act II layer and German come from one place.
        v = voice("bellkeeper");
        v.idle = new String[] {
                "Seven bells, seven notes. Ring them all and the old hymn plays.",
                "The high bell in the wharf tower? Arrows, dear. Nobody climbs that.",
                "Dawn gets three strokes, dusk gets five. I count. Someone has to.",
                "Every bell here was cast for someone. Most have forgotten who.",
                "Hear that hum after dark? Starbells. They answer the big ones."
        };
        v.greetDoubt = lines("The dusk toll was short again. Four. I counted twice.",
                "Four strokes at dusk. The bells don't miss. Something does.");
        v.doubtStage = 2;

        v = voice("stargazer");
        v.idle = new String[] {
                "Stand somewhere high and the island names itself. Five spots. Find them.",
                "Falling stars are rare here. When one comes, sneak and look up.",
                "Shooting stars over Skyreach. Make a wish, they say.",
                "The aurora comes when it likes. Mostly when you're indoors.",
                "From here you can see the Borderlands. From there, nothing nice.",
                "The glide down is quicker than the stairs. Also louder."
        };
        v.greetDoubt = lines("Those aren't stars. They're too warm. Something up there is coming apart.",
                "I used to make wishes on them. Now I just count them.");
        v.doubtStage = 3;

        // ------------------------------------------------------------ Main story cast (Das Schlaflied)
        v = voice("ysolde");
        v.accept = "I'm on it.";
        v.decline = "Not yet.";
        v.onAccept = new String[] {"Good. Quietly, please.", "Noted. I'll be here. I'm always here."};
        v.onDecline = new String[] {"The past isn't going anywhere. It's the one thing that can't."};
        v.greetNew = new String[] {"Quiet, please. The books are older than your grandparents."};
        v.greet = new String[] {"{player}. Wipe your boots. The Archive notices.", "Back, {player}? Gloves first. Then talk."};
        v.greetGrad = v.greet;
        v.greetLegend = lines("Legends are for children, {player}. Records are for adults.");
        v.greetDoubt = lines("{player}. I've been reading what we burned. Slowly.");
        v.idle = new String[] {"*turns a page very carefully*", "Gloves. Always gloves.", "Shh.",
                "Misfiled. Again. By me. Unacceptable."};
        v.farewell = new String[] {"Mind the stacks on your way out."};
        topic(v, "What is this place?",
                "The Archive. Everything the Capital wrote down and kept.",
                "And a few things it wrote down and wishes it hadn't.");
        topic(v, "Why 'Thorne'?", "Old Capital family. Councillors, mostly.", "Very proud of their signatures.");
        story(v, 3, "The mad king?",
                lines("A children's story. The Registry approved it. That alone worries me.",
                        "Approved stories are the ones someone needed you to believe."),
                lines("He had a name: Corvin. A scholar. Lit this city with lamps, they say.",
                        "Somewhere between the lamps and the bells, he became the monster."));

        v = voice("berit");
        v.accept = "I'll go and look.";
        v.decline = "Another time, Berit.";
        v.onAccept = new String[] {"Bring whatever's humming to §eYsolde§f at the Archive. Mind the dark."};
        v.onDecline = new String[] {"It's hummed for a thousand years, love. It'll hum till Tuesday."};
        v.greetNew = new String[] {"Come in, love. Mind the step."};
        v.greet = new String[] {"There you are, {player}. Kettle's on. Always is.", "Come in, love. Mind the step."};
        v.greetGrad = v.greet;
        v.greetDoubt = lines("Hello, dear. Have we met?", "Oh — a visitor. How lovely. Do I know you?");
        v.doubtStage = 6;
        v.doubtOnly = true;
        v.idle = new String[] {"*hums a tune with no ending*", "My knees say rain.", "Kettle's on. Kettle's always on."};
        v.farewell = new String[] {"Mind the step, love."};
        topic(v, "Your knees?", "They predict rain, bad news and visitors.", "Today they said visitors. Hello.");
        story(v, 6, "The old song?",
                lines("My gran taught me. It never had an ending.",
                        "So I hum the gap. You get used to a gap."),
                lines("Song? I used to know a song, I think.",
                        "Isn't that silly. Would you like some tea?"));

        v = voice("nell");
        v.accept = "I'll check on her.";
        v.decline = "Not right now, Nell.";
        v.onAccept = new String[] {"Thank you! I'll keep my lamp on till you're back."};
        v.onDecline = new String[] {"Okay. I'll ask again later. I'm good at asking."};
        v.greetNew = new String[] {"Hi! Are you new? I'm Nell."};
        v.greet = new String[] {"Hi, {player}! I learned a new note. It's the same note."};
        v.greetGrad = v.greet;
        v.greetDoubt = lines("Mother Berit called me 'little miss' again.");
        v.doubtStage = 6;
        v.idle = new String[] {"*hums the bell tune*", "One, two, three bells. That's morning.",
                "Lamps on soon. Good.", "I'm not scared. I'm careful."};
        v.farewell = new String[] {"Bye! Don't walk in the dark!"};
        topic(v, "Afraid of the dark?", "No! …Only when the lamps go out.", "Wick says they never go out. Wick doesn't lie.");
        topic(v, "The bell song?", "Seven notes. Then it stops. Like it forgot.",
                "Mother Berit says we hum the gap. So I hum the gap.");

        v = voice("hesper");
        v.greetNew = new String[] {"Down from the Capital, {player}? Mind the edge. It's ours."};
        v.greet = v.greetNew;
        v.greetGrad = v.greetNew;
        v.greetDoubt = lines("{player}. You came back. That's more than most.");
        v.idle = new String[] {"*watches the torn mooring*", "Tourists. Again.", "We remember. That's the whole job."};
        v.farewell = new String[] {"Go carefully. The edge doesn't care who you are."};
        topic(v, "The camp?", "Descendants of people who came from elsewhere. Long ago.", "We keep the names others dropped.");
        story(v, 5, "The Anchor?",
                lines("The third mooring. 'Old', they say up top. Old is a convenient word."),
                lines("Torn, not broken. Broken things fail. Torn things were pulled."));

        v = voice("ferryman");
        v.greetNew = new String[] {"Passenger? No? Just looking? Look all you like."};
        v.greet = v.greetNew;
        v.greetGrad = v.greetNew;
        v.idle = new String[] {"Ferry! …No? No one.", "*polishes an oar he never uses*", "Fog's thick. Fog's always thick."};
        v.farewell = new String[] {"Mind the planks. Some of them are only habit."};
        topic(v, "Your name?", "It was… something with an H? Or a boat. No, that's the boat.",
                "Nobody calls me anything. So it hardly matters.");
        topic(v, "Where does it go?", "Across. Everything goes across eventually.",
                "Past the Veil. Where nobody sails. Except me. Allegedly.");

        // ------------------------------------------------------------ Act III · the exile island
        v = voice("orrin");
        v.greetNew = new String[] {"Mind the gears. Some of them are older than your kingdom."};
        v.greet = new String[] {"Back, {player}. The star noticed.", "{player}. Don't touch anything warm."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"*makes a mark in a thick logbook*", "Tick. Tick. Still.", "Calibrated to a heartbeat. Hers."};
        v.farewell = new String[] {"Close the door gently. It's load-bearing."};
        topic(v, "Your chair?", "Three hundred and five tally marks on the wall. One per century, roughly.", "I lost count of the days. Not of her.");

        v = voice("exile_lamplighter");
        v.greetNew = new String[] {"A new face. Haven't lit for one of those in ages."};
        v.greet = new String[] {"Evening, {player}. It's always evening."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"Two hundred and eleven... two hundred and twelve.", "*squints at a lamp that is already lit*", "Late. Always late."};
        v.farewell = new String[] {"Keep to the lit side of the street."};
        topic(v, "Your round?", "Lift to the colossi, colossi to the great stair, stair to the castle.", "Then back. Every evening. Nobody told me to stop.");

        v = voice("exile_sentry");
        v.greetNew = new String[] {"Halt. ...Or don't. Nobody does anymore."};
        v.greet = new String[] {"You again, {player}. Still not seeing you."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"*stands at attention for no one*", "Captain's orders.", "Polished my helmet. Again."};
        v.farewell = new String[] {"Dismissed. That's what they say, isn't it?"};
        topic(v, "Your post?", "The Iron Ring. Nobody in, nobody out.", "I got the order the night everything stopped. Nobody cancelled it.");

        v = voice("exile_usher");
        v.greetNew = new String[] {"Welcome to the Little Theatre. Every seat is taken. Please stand."};
        v.greet = new String[] {"Back for the second act, {player}? It's the same as the first."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"Shh. The performance.", "*straightens a velvet rope*", "No flash photography. Whatever that is."};
        v.farewell = new String[] {"Mind the strings on your way out."};
        topic(v, "The audience?", "Three hundred and twenty-three. All puppets. All facing the stage.", "One seat is empty. Small. Front row.");

        v = voice("exile_gardener");
        v.greetNew = new String[] {"Mind the beds! ...Sorry. Hello."};
        v.greet = new String[] {"{player}! He watered the stones again."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"*pulls a weed that isn't there*", "He'll want water.", "Don't step on the flowers. They're people. Well. Pictures of people."};
        v.farewell = new String[] {"Mind the roots. They move when nobody looks."};
        topic(v, "The flowers?", "He planted a picture into the parterre. Three people and a doll.", "You can only see it from the bell tower. Nobody goes up there.");

        v = voice("exile_mother");
        v.greetNew = new String[] {"Oh! Are you from the ferry? Is it leaving? We're nearly packed."};
        v.greet = new String[] {"Nearly packed, {player}. Nearly."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"*folds a small dress, unfolds it, folds it*", "One shoe. Where's the other one.", "Before the dusk toll. Before the dusk toll."};
        v.farewell = new String[] {"If you see a little red shoe..."};
        topic(v, "The trunk?", "Her winter things. And the doll. She won't sleep without the doll.", "...The doll isn't here. Why isn't the doll here?");

        v = voice("exile_keeper");
        v.greetNew = new String[] {"Pull up a chair. They remember people."};
        v.greet = new String[] {"Your usual, {player}? We only have the usual."};
        v.greetGrad = v.greet;
        v.idle = new String[] {"*polishes a clean glass*", "Last orders. Always last orders.", "The bell's over the door. It hasn't rung in a long while."};
        v.farewell = new String[] {"Mind the step. The Last Bell's a bit sunk."};
        topic(v, "The name?", "The Last Bell. Named for the last toll before the gates shut.", "Folk drank to it every night. Then one night it didn't end.");

        // ------------------------------------------------------------ NPC-to-NPC banter
        banter(new Beat("egon", "That cod's been out since Tuesday."),
                new Beat("fishmonger", "It's aged, Egon. Like you."),
                new Beat("egon", "I'm seasoned."),
                new Beat("fishmonger", "So's the cod."));
        banter(new Beat("fishmonger", "Egon! Want a fish?"),
                new Beat("egon", "I want my crate back."),
                new Beat("fishmonger", "The fish is IN the crate."));
        banter(new Beat("forage_pad_guide", "Lumberjack! Try the pad!"),
                new Beat("lumberjack", "Forager. And no. Trees don't bounce."),
                new Beat("forage_pad_guide", "Isle trees kind of do."));
        banter(new Beat("lumberjack", "Twig. Bark in your hair again."),
                new Beat("forage_pad_guide", "It's a look."));
        banter(new Beat("farmer", "Lark. Your parrot's eating my wheat."),
                new Beat("lark", "She's sampling."),
                new Beat("farmer", "She's STEALING."),
                new Beat("lark", "Sampling!"));
        banter(new Beat("lark", "Seen any wild pigs?"),
                new Beat("farmer", "Only the ones in my wheat."));
        banter(new Beat("bar_whisper", "the bar says you're behind on paperwork…"),
                new Beat("quartermaster", "The bar can file a complaint."));
        banter(new Beat("eldervale_welcome", "Forgehand, you'll melt the snow."),
                new Beat("eldervale_upgrade", "Good. Snow's slippery."),
                new Beat("eldervale_welcome", "It's a mountain."));
        banter(new Beat("liquidator", "Miss Ledger. Crystal for your thoughts?"),
                new Beat("ledger", "My thoughts aren't for sale."),
                new Beat("liquidator", "Rent, then?"));
        banter(new Beat("town_crier", "Hear ye! Egon's cod is FRESH!"),
                new Beat("egon", "It's not my cod."),
                new Beat("town_crier", "Hear ye! Egon disowns the cod!"));
        banter(new Beat("street_sweeper", "Hollis. You're standing on my pile."),
                new Beat("town_crier", "Hear ye! Bram has a pile!"));
        banter(new Beat("vince", "Deed! Bet you an island I win tonight."),
                new Beat("isle_clerk", "Islands aren't wagers."),
                new Beat("vince", "Everything's a wager, Deed."));
        // Main story cast — public knowledge only (banter plays to everyone in range).
        banter(new Beat("nell", "Mother Berit, how does the song end?"),
                new Beat("berit", "It doesn't, love. We hum the gap."),
                new Beat("nell", "Hmm… hmmm…"),
                new Beat("berit", "Perfect. That's the gap."));
        banter(new Beat("vince", "Egon. Bet you won't sail past the Veil."),
                new Beat("egon", "Nobody sails past the Veil."),
                new Beat("vince", "So that's a no?"),
                new Beat("egon", "That's a rule."));
        banter(new Beat("town_crier", "Hear ye! The Archive is OPEN!"),
                new Beat("ysolde", "Quietly open, Hollis."),
                new Beat("town_crier", "Hear ye… quietly…"));
        banter(new Beat("nell", "Wick, will the lamps stay on tonight?"),
                new Beat("lamp_lighter", "Every one. Till dawn."),
                new Beat("nell", "Promise?"),
                new Beat("lamp_lighter", "Old law. I just keep it."));

        // ------------------------------------------------------------ Origin island cast (SAITAMA pass)
        // Errands for these voices live in isle.IsleErrands; German in cast.<id> / talk.isle_*.
        v = voice("harbourmaster");
        v.greetNew = new String[] {"Name and cargo, {player}. Kidding. Welcome to Anker Harbour."};
        v.greet = new String[] {"{player}. Nothing's sunk today. Yet.", "Back on my water, {player}? Mind the ropes."};
        v.idle = new String[] {
                "The Halcyon keeps her own hours. I keep the book and pretend I planned it.",
                "Four crates came in, three went out. Somebody owes me a crate, or an explanation.",
                "If it floats, it's on my list. If it flies, it's on my list too. The list is getting long.",
                "Keep clear of the crane when it swings. Haul is good, but he's never early."
        };
        v.farewell = new String[] {"Fair winds. Or at least dry ones."};
        topic(v, "What's the Halcyon?", "Supply skyship. Comes in over the north rim every few bells.",
                "Lowers a crate on a rope, a skiff catches it. Most days.");
        topic(v, "Where do I start?", "§eEgon§f on the pier kits out the green ones. Then follow the yellow arrow.",
                "After that? This harbour has more stories than barnacles.");

        v = voice("crane_hand");
        v.greetNew = new String[] {"Mind your head, {player}. The crane doesn't."};
        v.greet = new String[] {"{player}! Stand clear of the hook."};
        v.idle = new String[] {
                "Up she goes! Nice and slow, just like my grandmother taught me.",
                "This crane is older than the harbour. I keep it happy with oil and compliments.",
                "Crate onto the punt, punt to the market, market into your pockets. That's the chain.",
                "One day I'll lift the whole Halcyon out of the sky. The crane disagrees."
        };
        v.farewell = new String[] {"Clear the swing!"};
        topic(v, "How does the crane work?", "Rope, hook, me shouting. Mostly the shouting.");
        topic(v, "Seen anything strange?", "A barrel came up empty but heavier than full. I put it back.",
                "Some things want to stay in the water.");

        v = voice("lighthouse_keeper");
        v.greetNew = new String[] {"Ahoy, {player}. You've got sea legs, or you will."};
        v.greet = new String[] {"{player}. The light's lit. You're welcome."};
        v.idle = new String[] {
                "I can't see a thing without my spectacles. That gull is going to regret this.",
                "I count every ship that comes home. Forty years, and I've never lost count.",
                "At night the sea talks to the pier. I talk back, mostly about the weather.",
                "Forty years on this pier, and the gulls still don't respect me."
        };
        v.farewell = new String[] {"Keep the light on your left."};
        topic(v, "A lighthouse, on a sky island?", "Ships fly lower than you'd think in fog.",
                "And the fishermen need something to swear at.");
        topic(v, "The gull?", "Big. Grey. Thief. Took my spectacles right off my nose.", "It perches high. Always high. Showing off.");

        v = voice("shanty_busker");
        v.greetNew = new String[] {"A new face! Every new face gets a verse, {player}."};
        v.greet = new String[] {"{player}! Tap your foot. It's free."};
        v.idle = new String[] {
                "♪ Oh the Halcyon rides on the morning blue… ♪",
                "Coins in the hat are applause you can spend.",
                "I know forty shanties. Thirty-nine are about fish.",
                "♪ Haul away, haul away, the crane's got the crate… ♪"
        };
        v.farewell = new String[] {"Hum it on the road!"};
        topic(v, "Play me something.", "♪ Three bells at the wharf and the tide coming in, the gulls take your bread and the cod takes your grin… ♪",
                "That one's new. Needs a chorus.");
        topic(v, "Why busk here?", "Best acoustics on the island. Water, wood, and people too polite to leave.");

        v = voice("beekeeper");
        v.greetNew = new String[] {"Slow movements, {player}. The girls are watching."};
        v.greet = new String[] {"{player}! Smell that? That's mead, ageing."};
        v.idle = new String[] {
                "Listen to the hives. That humming means they're happy. Mostly.",
                "Bees don't sting good people. Well, they sting everyone, but they feel sorry afterwards.",
                "The Old Mead House was here before the roads, and the bees were here before the house.",
                "Three hives this year. Four, if my runaway queen ever comes home."
        };
        v.farewell = new String[] {"Bee-have! …I say it every time."};
        topic(v, "Mead?", "Honey, water, patience. And one secret I'll take to the hive.");
        topic(v, "This house?", "Older than the Capital's paving. Built round the hives, not the other way.");

        v = voice("painter");
        v.greetNew = new String[] {"Don't move — the light's perfect on you, {player}. …Now it's gone."};
        v.greet = new String[] {"{player}! Come to be painted?"};
        v.idle = new String[] {
                "Green, more green. No, not that green. The other one.",
                "The windmill won't hold still for me. I blame the wind, obviously.",
                "Every view on this island is a painting nobody has finished yet.",
                "I've painted this mill eleven times. It gets a little more stubborn every time."
        };
        v.farewell = new String[] {"Go find me a view!"};
        topic(v, "What are you painting?", "The mill. Again. It's my eleventh mill.", "The other ten were practice.");
        topic(v, "Best view?", "Ask Stellan, up on the summit. He'll say the stars.", "I say: the one you walked to.");

        v = voice("hermit");
        v.greetNew = new String[] {"Oh, hello there, {player}. Sorry, I didn't hear you over the birds."};
        v.greet = new String[] {"You came back! The trees said you might, and they're rarely wrong."};
        v.idle = new String[] {
                "I used to copy books in the Capital. Too loud. Out here I can hear myself think.",
                "The woodpecker over there starts work at dawn. I've never seen him take a break.",
                "People think the forest is quiet. It isn't quiet, it's just polite.",
                "I came out here for one afternoon. That was nine years ago, and I don't regret a day."
        };
        v.farewell = new String[] {"Walk softly out there. The wood notices."};
        topic(v, "Why do you live out here?", "I copied books in the Capital for thirty years. Bells, carts and shouting, every single day.", "One afternoon I walked out here to rest my ears. I just never walked back.");
        topic(v, "What do you listen to?", "Wind in the leaves, the stream, animals going about their day.", "And sometimes the bells of the Capital, very faint. Out here they sound gentler, almost sad.");

        v = voice("bookie");
        v.greetNew = new String[] {"Fresh legs! I'll give you four to one, {player}."};
        v.greet = new String[] {"{player}! Feeling fast today?"};
        v.idle = new String[] {
                "Odds on the next Halcyon? Six minutes. It's always six.",
                "Vince runs games of chance. I run games of legs.",
                "I count my coins twice. Once for me, and once for the taxman.",
                "Never bet against a sprinter in good boots."
        };
        v.farewell = new String[] {"Don't run off. Unless I paid you to."};
        topic(v, "What's the bet?", "Touch the Mountain Gate and get back to me inside three minutes. Easy coin.",
                "Skyways count. Teleports don't. There's a difference, legally.");
        topic(v, "And Vince?", "Lovely man. Terrible odds. Mine are worse, but I smile more.");

        v = voice("mushroom_forager");
        v.greetNew = new String[] {"Careful where you step, {player}. Things grow here."};
        v.greet = new String[] {"{player}! Smell the air? Spores. Lovely."};
        v.idle = new String[] {
                "This one smells edible. I said that last week too, and I was sick for two days.",
                "The big caps glow at night. Look, but please don't lick them.",
                "Eastwood's mushrooms are older than the trees around them.",
                "Rule of the forest: if a mushroom looks friendly, don't trust it."
        };
        v.farewell = new String[] {"Spore you later."};
        topic(v, "They glow?", "Three of the giants do, after dark. Pale blue at the gills.",
                "Find them and I'll tell you which ones are safe to eat. None.");
        topic(v, "Why so big?", "Aether in the soil. Same stuff that keeps the island up.", "Too much of it and a mushroom gets ideas.");

        v = voice("lost_tourist");
        v.greetNew = new String[] {"Oh! A local! Hello, {player}! Is this the summit?"};
        v.greet = new String[] {"{player}! I've moved four blocks today. Progress!"};
        v.idle = new String[] {
                "I think this map is upside down. Or the island is. Hard to tell.",
                "The brochure said 'a short stroll to the summit'. I've been strolling for three days.",
                "Is that the big mountain, or is the big one hiding behind it?",
                "I've sketched this fountain ninety times. It's a very nice fountain."
        };
        v.farewell = new String[] {"If you see the summit, say hello from me!"};
        topic(v, "Where are you from?", "Another island. A flat one. Very flat. That's why I'm here.");
        topic(v, "How do I get up Skyreach?", "The brochure says 'the Mountain Gate, then the wind'.",
                "I assumed that was poetry. Is it not poetry?");

        v = voice("dock_whisper");
        v.greetNew = new String[] {"Shh, listen. Can you hear the tide turning, {player}?"};
        v.greet = new String[] {"Hello again, {player}. The water is calm today."};
        v.idle = new String[] {
                "The water has been restless all morning. Something big is swimming far below.",
                "I can feel a storm two days before it arrives. My knees can feel it three days before.",
                "Tackle laughs at me, but the fish come when I call them. Some of them, anyway.",
                "My mother fished this pier, and her mother before her. The water knows our family."
        };
        v.farewell = new String[] {"Mind the edge of the pier. The water likes to borrow things."};
        topic(v, "What does the water say?", "Mostly it complains about the boats. Sometimes it warns me about the weather.", "Once it told me where a lost ring was. I found it the next morning, right where it said.");
        topic(v, "The purple line?", "When your fishing line glows purple, hold on tight and don't rush.", "That's a Mythic Water Dragon. It only shows itself to one fisher at a time.");

        v = voice("bait_theory");
        v.greetNew = new String[] {"Ah, a new face! Do you fish, {player}? Everyone should fish."};
        v.greet = new String[] {"Back again, {player}? The fish were asking about you."};
        v.idle = new String[] {
                "Bread works in the morning, worms at noon, and nothing at all when the gulls are watching.",
                "I once caught three salmon with a single piece of cheese. Nobody believes me.",
                "The secret of fishing is mostly sitting still and being fine with it.",
                "Every fish is curious. You just have to be more interesting than the other bait."
        };
        v.farewell = new String[] {"Good luck out there. May the fish be hungry!"};
        topic(v, "What's the best bait?", "It depends on the time of day and the mood of the fish.", "But if you want one answer: worms. Worms never let you down.");
        topic(v, "Why bait?", "My father said the fish don't care about your rod. They care about what's on the hook.", "He was right. I've spent eleven years proving it.");

        v = voice("dock_scaler");
        v.greetNew = new String[] {"Hello! Got anything for the scales, {player}?"};
        v.greet = new String[] {"{player}! Fish to weigh, or just passing by?"};
        v.idle = new String[] {
                "This morning's biggest catch was a salmon as long as my arm. Tackle is very proud.",
                "I write down every fish. One day someone will want to know what we caught here.",
                "My clipboard is always wet. I've made my peace with that.",
                "A fish that refuses to be weighed is still a fish. I just estimate."
        };
        v.farewell = new String[] {"Bring me something heavy next time!"};
        topic(v, "Why weigh the fish?", "If the fish get smaller every year, something is wrong out there.", "My numbers tell the harbour when to fish more and when to leave the water alone.");

        v = voice("fry_gossip");
        v.greetNew = new String[] {"New in town? Hungry? Curious? I can help with all three, {player}."};
        v.greet = new String[] {"{player}! Got any news for me? I've got plenty for you."};
        v.idle = new String[] {
                "Did you hear? Quill the tallyman lost a crate again. Third time this month.",
                "Fried fish, fried bread, fried potatoes. If it fits in the pan, I'll fry it.",
                "I don't spread rumours. I just repeat them very clearly.",
                "Old Moll from the lighthouse lost her spectacles to a gull. Poor woman can't see a thing."
        };
        v.farewell = new String[] {"Come back hungry!"};
        topic(v, "Any gossip?", "Hettie's bees swarmed this morning, Tobin is writing a new song, and Wick named another lamp.", "Busy day for such a small harbour.");
        topic(v, "What's good today?", "The cod is fresh, the bread is crispy, and the oil is only a little bit old.", "My advice? Get the cod.");

        v = voice("rivet");
        v.greetNew = new String[] {"Watch your step, {player}! Fresh paint."};
        v.greet = new String[] {"{player}! Come to help, or just to watch me work?"};
        v.idle = new String[] {
                "This old ship has more patches than original wood. I'm proud of every single one.",
                "Somebody keeps stealing my rivets. I suspect the gulls.",
                "Salt water eats iron for breakfast. That's why I'm never out of work.",
                "When I hammer, the whole harbour knows I'm here. That's the idea."
        };
        v.farewell = new String[] {"Don't lean on the railing, it isn't dry yet!"};
        topic(v, "Whose ship is this?", "She belongs to the harbour now. Her captain left years ago and never came back.", "So I look after her. Somebody has to.");

        v = voice("stall_crumb");
        v.greetNew = new String[] {"Good morning, {player}! Fresh bread, still warm."};
        v.greet = new String[] {"Hello again, {player}! Hungry?"};
        v.idle = new String[] {
                "Fresh loaves, still warm from the oven! Get them before the gulls do!",
                "I've been baking since before dawn. My oven never sleeps, and neither do I.",
                "Egon buys two loaves every morning. He says one of them is for a friend.",
                "Bread with a little salt and butter. Nothing in the world beats it."
        };
        v.farewell = new String[] {"Don't forget to eat!"};
        topic(v, "Why bake here?", "The harbour wakes up early and works hard. Somebody has to feed it.", "My mother ran this stall before me. I still use her recipe.");

        v = voice("stall_tack");
        v.greetNew = new String[] {"Hello, {player}! Need a nail? A rope? A little bit of everything?"};
        v.greet = new String[] {"Back again, {player}? Forgot something, didn't you?"};
        v.idle = new String[] {
                "Rope, nails, buckles, hooks! Everything a busy islander forgets!",
                "I sorted all my nails by size this morning. A customer mixed them up in five minutes.",
                "The miners say Ore Ridge eats a pickaxe a week. Good for business, bad for pickaxes.",
                "Every little thing on this table has saved somebody's day at least once."
        };
        v.farewell = new String[] {"Come back when something breaks!"};
        topic(v, "What sells best?", "Rope. Everybody needs rope, and nobody ever has enough of it.");

        v = voice("stall_brine");
        v.greetNew = new String[] {"Hello, {player}! Have you ever tasted fish with proper salt?"};
        v.greet = new String[] {"{player}! Salt? Spices? A friendly chat?"};
        v.idle = new String[] {
                "Sea salt, fresh from the pans! The best salt on the whole island!",
                "I dry my salt in the sun out on the pier. The gulls are very interested, but they hate the taste.",
                "A fish without salt is just a sad, wet thing. Trust me.",
                "My jars travel all the way to the Capital. The cooks there fight over them."
        };
        v.farewell = new String[] {"A pinch of salt never hurt anybody!"};
        topic(v, "Where does your salt come from?", "I let seawater dry in shallow pans out on the pier.", "The sun does most of the work. I just do the selling.");

        v = voice("dockhand");
        v.greetNew = new String[] {"Careful near the water, {player}. Something down there bites."};
        v.greet = new String[] {"{player}. Any ink on your boots today?"};
        v.idle = new String[] {
                "I had to scrub ink off the pier again this morning. Black, sticky, and it smells like old pens.",
                "Whatever lives under the harbour, it doesn't like boats. Or people. Or me.",
                "I tie every boat twice now. Once for the wind, and once for the thing in the water.",
                "Some nights you can see something big moving out past the buoys."
        };
        v.farewell = new String[] {"Keep your feet dry."};
        topic(v, "What lives down there?", "Nobody has seen all of it. Just arms, ink and a lot of angry bubbles.", "If you're strong enough one day, maybe you'll be the one to find out.");

        v = voice("slag_poet");
        v.greetNew = new String[] {
                "Ah, a visitor! Would you like to hear a poem, {player}? It's a short one."
        };
        v.greet = new String[] {"{player}! I finished a new verse. It's about gravel."};
        v.idle = new String[] {
                "'Oh copper vein, you shone so bright, then you were gone before the night.' I'm still working on it.",
                "The Ore Ledger wants numbers. I want rhymes. We get along surprisingly well.",
                "Up here the wind always smells of iron and stone.",
                "Every miner on this ridge has lost a vein they loved. Poetry helps."
        };
        v.farewell = new String[] {"May your veins run deep!"};
        topic(v, "Read me a poem.", "'The pickaxe sings, the mountain sighs, the coal runs out before my eyes.'", "It's not finished yet. Nothing up here ever is.");

        v = voice("bench_cynic");
        v.greetNew = new String[] {"Oh. Another tourist. Hello, {player}."};
        v.greet = new String[] {"{player}. You again. The bench is still taken."};
        v.idle = new String[] {
                "Everybody loves this fountain. I've been looking at it for fifteen years. It's fine.",
                "The pigeons here get bolder every year. One stole my sandwich yesterday.",
                "People walk past me all day, and not one of them has ever asked how I am. I'm fine, by the way.",
                "This was a quiet square before somebody built a fountain in the middle of it."
        };
        v.farewell = new String[] {"Mind the pigeons on your way out."};
        topic(v, "Why this bench?", "Best view of the fountain, shade in the afternoon, and nobody bothers me.", "Well. Usually nobody bothers me.");
        topic(v, "Rotten flesh?", "It's for science. Probably. I'll decide what kind once I have it.");

        v = voice("dust");
        v.greetNew = new String[] {"Hello! Watch your step, {player}, this path isn't finished yet."};
        v.greet = new String[] {"{player}! Got any cobblestone for me today?"};
        v.idle = new String[] {
                "Every path in the Capital started with somebody carrying stones. Usually me.",
                "I've mapped this square three times. Somebody keeps moving the benches.",
                "A good road makes people visit each other more often. That's why I build them.",
                "One day there'll be a stone path from the harbour to the summit. I'll be the first to walk it."
        };
        v.farewell = new String[] {"Mind the loose stones!"};
        topic(v, "Why roads?", "When I was little, the walk to my grandma's house was all mud and nettles.", "Now there's a path. I built it. She still complains, but she visits more.");

        v = voice("rook");
        v.greetNew = new String[] {"Another brave one! Ready to learn something, {player}?"};
        v.greet = new String[] {"{player}. Any bones for me today?"};
        v.idle = new String[] {
                "Most adventurers get into trouble down there because they forgot torches. Bring torches.",
                "I've been down below more times than I can count. I still get nervous every time.",
                "Skeletons always aim for your head. Wear a helmet. It's not optional.",
                "First rule of a dungeon: know where the exit is before you need it."
        };
        v.farewell = new String[] {"Come back alive. That's the lesson."};
        topic(v, "What's down there?", "Tunnels, traps, treasure and a lot of things with too many teeth.", "And at the bottom there's usually something big that doesn't like visitors.");

        v = voice("larder");
        v.greetNew = new String[] {"Hello, {player}! You don't happen to have any wheat on you?"};
        v.greet = new String[] {"{player}! The pantry is still hungry."};
        v.idle = new String[] {
                "The Market Hall eats more bread every week. I don't know where all these people come from.",
                "An empty shelf is the saddest sight in the whole Capital.",
                "My grandmother always said: a full pantry means a calm city. She was right.",
                "I count the sacks every evening. The numbers never make me happy."
        };
        v.farewell = new String[] {"Bring wheat! Lots of wheat!"};
        topic(v, "Compressed Wheat?", "Farm a lot of wheat and press it together into one dense bundle.", "It keeps well, it travels well, and it makes the best bread.");

        v = voice("quarry_broker");
        v.greetNew = new String[] {"Hello, {player}. Thinking about joining the Guild?"};
        v.greet = new String[] {"{player}. Brought me a brick yet?"};
        v.idle = new String[] {
                "Half the houses in the Capital were built with stone from this quarry.",
                "Anyone can pile up rocks. A real builder knows which rock goes where.",
                "The Guild has strict standards. That's why our roofs don't leak.",
                "I check every brick that comes through here. Every single one."
        };
        v.farewell = new String[] {"Build something that lasts."};
        topic(v, "Compacted or compressed?", "Compressed cobblestone is nine stones squeezed into one. Compacted is nine compressed ones squeezed again.", "It's a lot of work. That's exactly the point.");

        v = voice("pet_scout");
        v.greetNew = new String[] {"Hello, {player}! Do you like animals? I love animals."};
        v.greet = new String[] {"{player}! Caught anything cute today?"};
        v.idle = new String[] {
                "Pigs are smarter than people think. That's why they're so hard to catch.",
                "A good catcher is patient and quiet, and never chases. Let them come to you.",
                "Lark's parrot bit me again. I still think he likes me.",
                "Every animal on this island is a friendship waiting to happen."
        };
        v.farewell = new String[] {"Be gentle with your catches!"};
        topic(v, "How do I catch a pig?", "Get close slowly, aim for the middle and throw the sphere while it's calm.", "If it runs, don't chase it. Wait. It'll come back for the grass.");

        v = voice("sphere_proctor");
        v.greetNew = new String[] {"Hello, {player}! Ready for your cow exam?"};
        v.greet = new String[] {"{player}! How is the catching going?"};
        v.idle = new String[] {
                "A cow is bigger than a pig and slower than a pig, and somehow much harder to catch.",
                "I keep notes on every catcher who passes my test. The list is short.",
                "Never stand right behind a cow. That's not part of the test, it's just good advice.",
                "The Colosseum crowd loves a good catch. Almost as much as a good fight."
        };
        v.farewell = new String[] {"Good luck with the cow!"};
        topic(v, "Why cows?", "Catching a cow takes patience, good aim and a strong arm.", "If you can do that, you can catch almost anything on this island.");

        v = voice("ore_ledger");
        v.greetNew = new String[] {"Hello, {player}. Here to mine, or here to help?"};
        v.greet = new String[] {"{player}! Any coal for my ledger today?"};
        v.idle = new String[] {
                "Coal has come out of this ridge for a hundred years. My books cover ninety of them.",
                "The miners bring the stone. I bring the numbers. We both get dirty hands.",
                "Every page in this ledger is somebody's hard day in the tunnels.",
                "Ore Ridge gives a lot, but it never gives anything for free."
        };
        v.farewell = new String[] {"Mind the loose rocks up here!"};
        topic(v, "Why keep records?", "If we know what the mountain gives, we know how long it can keep giving.", "Mines that dig blindly end up empty.");

        v = voice("timber_clerk");
        v.greetNew = new String[] {"Hello, {player}! Mind the stumps, they're everywhere."};
        v.greet = new String[] {"{player}! Got any oak for me today?"};
        v.idle = new String[] {
                "For every tree we cut, we plant two. That's the rule here, and I make sure of it.",
                "This stump was a hundred years old. I wrote its name down before it was cut.",
                "The Forager cuts, I count, and the forest keeps growing. Good teamwork.",
                "Eastwood smells best right after the rain. You should come back then."
        };
        v.farewell = new String[] {"Plant a tree sometime!"};
        topic(v, "Why count trees?", "Because forests disappear quietly if nobody is counting.", "I want my grandchildren to see Eastwood as green as it is today.");

        v = voice("gate_warden");
        v.greetNew = new String[] {"Halt! Oh, sorry, {player}. Habit. Welcome to the gate."};
        v.greet = new String[] {"{player}. The road is still closed."};
        v.idle = new String[] {
                "I've stood at this gate for six years. The Pathwarden has stood on that road for longer.",
                "Every week somebody tries to get past him. Every week I patch someone up.",
                "The Borderlands are beautiful from a distance. Up close, they bite.",
                "Someday that road will open again. I'd like to be standing here when it does."
        };
        v.farewell = new String[] {"Stay safe out there."};
        topic(v, "Who is the Pathwarden?", "A huge guardian who walks the old road and doesn't let anyone pass.", "Nobody knows who put him there. He just keeps doing his job.");

        v = voice("tollkeeper");
        v.greetNew = new String[] {"Hello, traveller! Crossing today, {player}?"};
        v.greet = new String[] {"{player}! Still in one piece? Good."};
        v.idle = new String[] {
                "I used to collect a coin from every traveller. Now I just count how many come back.",
                "The troll lives under the old crossings, not under this bridge. Probably. Almost certainly.",
                "Whisperwood is lovely this time of year. Just stay on the path.",
                "My ledger has a column for 'grabbed by the troll'. It's longer than I'd like."
        };
        v.farewell = new String[] {"Mind the water under the bridge."};
        topic(v, "The troll?", "Big, grumpy and very good at hiding under bridges.", "If you're strong enough one day, maybe you can teach him some manners.");

        v = voice("chicken_keeper");
        v.greetNew = new String[] {"Hello, {player}! Watch your feet, the chicks are everywhere."};
        v.greet = new String[] {"{player}! Seen a giant chicken anywhere?"};
        v.idle = new String[] {
                "McNugget was such a small chick. I don't know what happened. I fed him normal corn.",
                "These hens lay the best eggs on the island. I tell them so every morning.",
                "A fox came by last night. The chickens chased it away. I'm very proud of them.",
                "Clucksworth used to be quiet. Then McNugget learned how to crow."
        };
        v.farewell = new String[] {"Mind the chickens on your way out!"};
        topic(v, "Who is McNugget?", "My biggest chicken. Bigger than a horse, louder than a bell, and he hates fences.", "He escapes every few days. I've stopped building fences and started building courage.");

        v = voice("ash_scout");
        v.greetNew = new String[] {"Get down! Oh, it's just you, {player}. Hello."};
        v.greet = new String[] {"{player}. Mind the smoke today."};
        v.idle = new String[] {
                "I count every arrow that comes back to camp. Fewer come back every week.",
                "The wastes used to be farmland, long ago. Now only ash grows out there.",
                "If you see a flash on the horizon, duck first and ask questions later.",
                "My bow is older than I am. It still shoots straighter than my opinions."
        };
        v.farewell = new String[] {"Stay low out there."};
        topic(v, "Who is Skuldugery?", "A skeleton archer who lives out in the wastes and shoots burning arrows.", "He's fast, he's clever and he loves to show off.");

        // Quill and Haul work the pier-head crane together (harbour elevate): their banter stays at the crane,
        // so no bubble pops up behind Egon while a newcomer is talking to him.
        banter(new Beat("harbourmaster", "Haul, that was four crates. My book says five."),
                new Beat("crane_hand", "Then your book can come up here and lift the fifth one itself."),
                new Beat("harbourmaster", "The book doesn't lift, Haul. The book remembers."));
        banter(new Beat("crane_hand", "Quill! Need anything lifted today? Crates, barrels, your spirits?"),
                new Beat("harbourmaster", "Lift the crate that went missing on Tuesday. That would lift my spirits."),
                new Beat("crane_hand", "Sorry. The crane is rated for heavy loads, not lost ones."));
        banter(new Beat("lighthouse_keeper", "You're whispering at the water again. Does it ever answer you?"),
                new Beat("dock_whisper", "Sometimes. Mostly it complains about the boats."),
                new Beat("lighthouse_keeper", "Fair enough. I'd complain too if people kept parking on me."));
        banter(new Beat("shanty_busker", "♪ The merchant counts coins by the light of the moon… ♪"),
                new Beat("merchant", "Tobin, please stop putting my accounts into songs. People can hear you."),
                new Beat("shanty_busker", "♪ …and he'd rather you didn't know how many he's got ♪"));
        banter(new Beat("bookie", "Vince! A man lost his hat on your machines today."),
                new Beat("vince", "And a man lost his breath in your races, Odds."),
                new Beat("bookie", "Only for a minute. He got it back at the finish line!"));
        banter(new Beat("lost_tourist", "Excuse me, is this the summit? The brochure said it would be windy."),
                new Beat("bench_cynic", "This is a bench in the Capital. The mountain is that big pointy thing behind you."),
                new Beat("lost_tourist", "Oh! Well, it's a lovely bench. I'll put it on my postcard."));
        banter(new Beat("mushroom_forager", "Forager, there's a mushroom growing out of your woodpile. A big one."),
                new Beat("lumberjack", "Please don't eat it. The last time you ate something from my woodpile, you saw colours for a week."),
                new Beat("mushroom_forager", "Too late, I already had a bite. It tastes like adventure."));
    }

    private static Voice voice(String id) {
        return VOICES.computeIfAbsent(id, k -> new Voice());
    }

    private static void topic(Voice v, String label, String... lines) {
        v.topics.add(new Topic(label, lines));
    }

    /** Act I / Act II layer for this voice ({@code doubtStage} = story stage where doubt starts). */
    private static void story(Voice v, int doubtStage, String label, String[] legend, String[] doubt) {
        v.doubtStage = doubtStage;
        v.storyLabel = label;
        v.storyLegend = legend;
        v.storyDoubt = doubt;
    }

    private static String[] lines(String... lines) {
        return lines;
    }

    private static void banter(Beat... beats) {
        BANTER.add(List.of(beats));
    }

    private static String pick(String[] pool) {
        if (pool == null || pool.length == 0) {
            return null;
        }
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private static Voice of(String npcId) {
        return npcId == null ? null : VOICES.get(npcId.toLowerCase(Locale.ROOT));
    }

    /* ------------------------------------------------------------------ API */
    // English is canonical. German readers get cast.<npc>.* from lang/de.yml; when the
    // overlay has no entry the NPC stays quiet instead of barking English at them.

    private static boolean german(Player player) {
        return player != null && LangPack.german(player);
    }

    private static String pickDe(String npcId, String field) {
        return pick(LangPack.castLines(npcId, field));
    }

    public static String acceptLabel(String npcId, String questId) {
        Voice v = of(npcId);
        return v == null ? "I'm in." : v.accept;
    }

    public static String acceptLabel(String npcId, String questId, Player player) {
        if (!german(player)) {
            return acceptLabel(npcId, questId);
        }
        String de = LangPack.castText(npcId, "accept");
        return de != null ? de : LangPack.ui(player, "talk_ux.accept_default", "I'm in.");
    }

    public static String declineLabel(String npcId) {
        Voice v = of(npcId);
        return v == null ? "Not now." : v.decline;
    }

    public static String declineLabel(String npcId, Player player) {
        if (!german(player)) {
            return declineLabel(npcId);
        }
        String de = LangPack.castText(npcId, "decline");
        return de != null ? de : LangPack.ui(player, "talk_ux.decline_default", "Not now.");
    }

    public static String acceptReaction(String npcId) {
        Voice v = of(npcId);
        return v == null ? null : pick(v.onAccept);
    }

    public static String acceptReaction(String npcId, Player player) {
        return german(player) ? pickDe(npcId, "on_accept") : acceptReaction(npcId);
    }

    public static String declineReaction(String npcId) {
        Voice v = of(npcId);
        return v == null ? null : pick(v.onDecline);
    }

    public static String declineReaction(String npcId, Player player) {
        return german(player) ? pickDe(npcId, "on_decline") : declineReaction(npcId);
    }

    public static String farewell(String npcId) {
        Voice v = of(npcId);
        return v == null ? null : pick(v.farewell);
    }

    public static String farewell(String npcId, Player player) {
        return german(player) ? pickDe(npcId, "farewell") : farewell(npcId);
    }

    public static List<Topic> topics(String npcId) {
        Voice v = of(npcId);
        return v == null ? List.of() : List.copyOf(v.topics);
    }

    /** Small-talk topics in the reader's language (German: overlay only, may be empty). */
    public static List<Topic> topics(String npcId, Player player) {
        Topic story = storyTopic(npcId, player);
        if (!german(player)) {
            List<Topic> out = new ArrayList<>(topics(npcId));
            if (story != null) {
                out.add(0, story);
            }
            return out;
        }
        List<Topic> out = new ArrayList<>();
        if (story != null) {
            out.add(story);
        }
        for (Map<?, ?> entry : LangPack.castTopics(npcId)) {
            Object label = entry.get("label");
            Object lines = entry.get("lines");
            if (label == null || !(lines instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            String[] said = new String[list.size()];
            for (int i = 0; i < said.length; i++) {
                said[i] = String.valueOf(list.get(i));
            }
            out.add(new Topic(String.valueOf(label), said));
        }
        return out;
    }

    public static String idle(String npcId) {
        Voice v = of(npcId);
        return v == null ? null : pick(v.idle);
    }

    /** Idle bark for an audience that reads German ({@code german}) or English. */
    public static String idle(String npcId, boolean german) {
        if (!german) {
            return idle(npcId);
        }
        return of(npcId) == null ? null : pickDe(npcId, "idle");
    }

    public static List<List<Beat>> banter() {
        return BANTER;
    }

    /**
     * German version of banter script {@code index}: same speakers, overlay lines.
     * Null when the overlay has no complete translation for it.
     */
    public static List<Beat> banterGerman(int index) {
        if (index < 0 || index >= BANTER.size()) {
            return null;
        }
        List<Beat> english = BANTER.get(index);
        List<String> lines = LangPack.banterLines(index);
        if (lines == null || lines.size() != english.size()) {
            return null;
        }
        List<Beat> out = new ArrayList<>(english.size());
        for (int i = 0; i < english.size(); i++) {
            out.add(new Beat(english.get(i).npcId(), lines.get(i)));
        }
        return out;
    }

    /**
     * Intro for a repeat visit while the NPC's quest is still on offer — a shorter,
     * name-aware nudge instead of the full speech. Null = play the full intro.
     */
    public static String[] returningIntro(String npcId, Player player) {
        Voice v = of(npcId);
        if (v == null || v.returning == null || player == null) {
            return null;
        }
        NpcMemory memory = NpcMemory.get();
        if (memory == null || !memory.flag(player.getUniqueId(), introFlag(npcId))) {
            return null;
        }
        if (german(player)) {
            return LangPack.castLines(npcId, "returning");
        }
        return v.returning.clone();
    }

    /** Set once the full intro of this NPC has been played to the player. */
    public static void markIntroHeard(Player player, String npcId) {
        NpcMemory memory = NpcMemory.get();
        if (memory != null && player != null && npcId != null) {
            memory.setFlag(player.getUniqueId(), introFlag(npcId), true);
        }
    }

    private static String introFlag(String npcId) {
        return "intro_" + npcId.toLowerCase(Locale.ROOT);
    }

    /** Approach greeting for this player's stage (null = stay quiet). */
    public static String greeting(String npcId, Player player) {
        Voice v = of(npcId);
        if (v == null || player == null) {
            return null;
        }
        Stage stage = stage(player);
        NpcMemory memory = NpcMemory.get();
        boolean met = memory != null && memory.talks(player.getUniqueId(), npcId) > 0;
        boolean de = german(player);
        if (stage == Stage.GRADUATE) {
            String told = storyGreeting(npcId, v, player, de);
            if (told != null) {
                return told;
            }
        }
        String[] grad = de ? LangPack.castLines(npcId, "greet_grad") : v.greetGrad;
        String[] fresh = de ? LangPack.castLines(npcId, "greet_new") : v.greetNew;
        String[] known = de ? LangPack.castLines(npcId, "greet") : v.greet;
        if (stage == Stage.GRADUATE && grad != null) {
            return pick(grad);
        }
        // First hour: "kit holding up?" makes no sense before there is a kit.
        if ((stage == Stage.ARRIVAL || !met) && fresh != null) {
            return pick(fresh);
        }
        return pick(known != null ? known : fresh);
    }

    // ------------------------------------------------------------------ main story layer

    private static int storyStage(Player player) {
        return de.aetherion.quests.story.StoryState.stageOf(player);
    }

    /** Story greeting for a graduate, or null to fall back to the normal pool. */
    private static String storyGreeting(String npcId, Voice v, Player player, boolean de) {
        if (v.greetLegend == null && v.greetDoubt == null) {
            return null;
        }
        int stage = storyStage(player);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (v.greetDoubt != null && stage >= v.doubtStage) {
            if (v.doubtOnly || random.nextBoolean()) {
                return pick(de ? LangPack.castLines(npcId, "greet_doubt") : v.greetDoubt);
            }
            return null;
        }
        if (v.greetLegend != null && random.nextBoolean()) {
            return pick(de ? LangPack.castLines(npcId, "greet_legend") : v.greetLegend);
        }
        return null;
    }

    /** The stage-aware small-talk topic (after the tutorial), or null. */
    private static Topic storyTopic(String npcId, Player player) {
        Voice v = of(npcId);
        if (v == null || v.storyLabel == null || player == null || stage(player) != Stage.GRADUATE) {
            return null;
        }
        boolean doubt = v.storyDoubt != null && storyStage(player) >= v.doubtStage;
        if (german(player)) {
            String label = LangPack.castText(npcId, "story.label");
            String[] said = LangPack.castLines(npcId, doubt ? "story.doubt" : "story.legend");
            return label == null || said == null ? null : new Topic(label, said);
        }
        String[] said = doubt ? v.storyDoubt : v.storyLegend;
        return said == null ? null : new Topic(v.storyLabel, said);
    }

    /**
     * Ambient line for an NPC another plugin owns (the Hub's Sister Aurel / Stellan): their
     * usual barks, plus the Act II line once the player is far enough. Null before the
     * tutorial is done or when this voice has nothing (the owner then uses its own lines).
     */
    public static String storyBark(String npcId, Player player) {
        Voice v = of(npcId);
        if (v == null || player == null || stage(player) != Stage.GRADUATE) {
            return null;
        }
        boolean de = german(player);
        if (v.greetDoubt != null && storyStage(player) >= v.doubtStage
                && ThreadLocalRandom.current().nextInt(3) == 0) {
            String doubt = pick(de ? LangPack.castLines(npcId, "greet_doubt") : v.greetDoubt);
            if (doubt != null) {
                return doubt;
            }
        }
        return pick(de ? LangPack.castLines(npcId, "idle") : v.idle);
    }

    public static Stage stage(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager qm = plugin == null ? null : plugin.getQuestManager();
        if (qm == null || player == null) {
            return Stage.ARRIVAL;
        }
        if (QuestStoryGate.tutorialDone(player, qm)) {
            return Stage.GRADUATE;
        }
        if (!QuestStoryGate.questCompleted(player, qm, "gather_wood")) {
            return Stage.ARRIVAL;
        }
        if (QuestStoryGate.questCompleted(player, qm, "lesson_boost")) {
            return Stage.SKILLS;
        }
        return Stage.HARBOUR;
    }

    /** True when this NPC's own quest is currently on offer to the player. */
    public static boolean questAvailable(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager qm = plugin == null ? null : plugin.getQuestManager();
        if (qm == null || npc == null || npc.getQuestId() == null || npc.getQuestId().isBlank()) {
            return false;
        }
        Quest quest = qm.getQuestForNpc(npc);
        return quest != null && qm.getQuestState(player, quest) == QuestState.AVAILABLE;
    }
}
