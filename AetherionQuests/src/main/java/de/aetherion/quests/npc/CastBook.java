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
        v.idle = new String[] {"*clang*", "Hm.", "Iron's moody today.", "Coal. More coal."};
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
        v.idle = new String[] {"*puff*", "Hear that? Neither do I. Good sign.", "Twenty years down there. Still hate spiders."};
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
        v.idle = new String[] {"*sizzle*", "That spark was intentional.", "Goggles on. Always goggles on."};
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
        v.idle = new String[] {"*stamp*", "Late. Late. Early. Late.", "Who writes a nine like that?"};
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
        v.idle = new String[] {"Shoo!", "That crow's been staring for an hour.", "Rain soon. Or birds. Probably birds."};
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
        v.idle = new String[] {"Who's a good bird? Not you.", "*whistles*", "Pistachio, no."};
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
        v.greetNew = new String[] {"Tide's good, {player}."};
        v.greet = new String[] {"Line's calling, {player}.", "Wind's right. Fish aren't."};
        v.greetGrad = new String[] {"{player}. The sea asked about you."};
        v.idle = new String[] {"*hums a sea shanty*", "Wind's from the east. Fish sulk.", "Hm. Nibble."};
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
        v.idle = new String[] {"Hup!", "Left! Right! Left!", "*sharpens sword*"};
        v.farewell = new String[] {"Dismissed! Walk like you mean it."};
        topic(v, "Any advice?", "Swing first. Think second. Survive third.", "Actually — think first. Write that down.");
        topic(v, "The scar?", "Training accident.", "The training won.");

        v = voice("rite_keeper");
        v.accept = "I'll light it.";
        v.decline = "Not yet.";
        v.onAccept = new String[] {"The powder remembers. So will you."};
        v.onDecline = new String[] {"Spirits are patient. I am less so."};
        v.returning = new String[] {
                "The vial, {player}. The powder. The pillar.",
                "Ten seconds. Thunder. Then it walks."
        };
        v.greetNew = new String[] {"The waste watches, {player}."};
        v.greet = new String[] {"Spirits stir when you pass, {player}."};
        v.greetGrad = new String[] {"The waste watches, {player}. It seems… impressed."};
        v.idle = new String[] {"*murmurs*", "Thunder soon.", "The powder is restless."};
        v.farewell = new String[] {"Walk softly. Things listen out there."};
        topic(v, "What are spirits?", "Old things that didn't finish dying.",
                "The vial holds them. The powder wakes them. You end them.");

        v = voice("arena_proctor");
        v.greetNew = new String[] {"Ticket, {player}? No? Then don't loiter."};
        v.greet = new String[] {"Oh. {player}. Still alive. Noted."};
        v.idle = new String[] {"*checks clipboard*", "Nobody's passed today. Nobody took it either.", "Hm."};
        v.farewell = new String[] {"Mind the ring. It's older than your excuses."};
        topic(v, "What happened here?", "Exams. Monsters. Paperwork. In that order.", "Now it's mostly paperwork.");

        v = voice("farm_isle_guide");
        v.greetNew = new String[] {"Isle's open, {player}. If your hands are."};
        v.greet = new String[] {"Soil's good today, {player}."};
        v.idle = new String[] {"*chews wheat*", "Soil's good today.", "Pad thumps all day. That's normal. I think."};
        v.farewell = new String[] {"Knees bent on the pad. Don't get clever."};
        topic(v, "What's on the isle?", "Bigger fields. Fewer birds. Better soil.", "Don't tell the Farmer.");
        topic(v, "You and the Farmer?", "He thinks he's the real farmer.", "Cute.");

        v = voice("surveyor");
        v.greetNew = new String[] {"{player}! Seen any trolls? Big ones? Tell me everything."};
        v.greet = new String[] {"Got a page for me, {player}?"};
        v.idle = new String[] {"*scribbles a map*", "North is… that way. Probably.", "Trolls. Fascinating. Terrible. Fascinating."};
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
        v.idle = new String[] {"*flips a coin*", "Red. Always red.", "Somebody's winning tonight. Statistically."};
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
        v.idle = new String[] {"…", "*listens to the air*", "level up… level up…"};
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
        v.greetNew = new String[] {"Air's thin up here, {player}. Breathe slow."};
        v.greet = new String[] {"Back on the mountain, {player}? Good lungs."};
        v.idle = new String[] {"*wraps scarf tighter*", "Snow by nightfall.", "Mind the ledge, love."};
        v.farewell = new String[] {"Stay off the edge. I mean it."};
        topic(v, "What's here?", "Ore the mainland forgot.", "And a forge that remembers everything.");
        topic(v, "How far is the drop?", "Long.", "Nobody's reported back. That's the joke.");

        v = voice("eldervale_upgrade");
        v.greetNew = new String[] {"Heat's up, {player}."};
        v.greet = new String[] {"Bring me steel, {player}. And stones."};
        v.idle = new String[] {"*hammer strike*", "Hotter.", "Stone's singing."};

        v = voice("merchant");
        v.greetNew = new String[] {"Ah, {player}! A customer! Or a browser. Both welcome."};
        v.greet = new String[] {"{player}! Found any good chests?"};
        v.idle = new String[] {"Chests! Adventure in a box!", "*polishes emerald*", "Everything must go. Eventually."};
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
        v.idle = new String[] {"*stamps nothing*", "Form 20-B. Doesn't exist.", "Islands. Everyone wants islands."};
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
        v.greetNew = new String[] {"Jump the pad, {player}! It's fine! Mostly fine!"};
        v.greet = new String[] {"Back from the isle, {player}? Smell that spruce!"};
        v.idle = new String[] {"Boing.", "*bounces on heels*", "The pad's sticky today. Good sticky."};
        v.farewell = new String[] {"Jump with your knees!"};
        topic(v, "Is the pad safe?", "Totally.", "Ninety percent totally.");

        v = voice("liquidator");
        v.greetNew = new String[] {"{player}. Crystals whisper about you. Mostly numbers."};
        v.greet = new String[] {"Buying or melting, {player}?"};
        v.idle = new String[] {"*crystal hum*", "Purple is a price, not a colour.", "Melt. Mint. Repeat."};

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
        v.idle = new String[] {"*sniffs a log*", "Spruce. Definitely spruce.", "Ring count: forty. Respectable."};
        v.farewell = new String[] {"Chop responsibly."};
        topic(v, "Why samples?", "Proof the isle grows real wood.", "The canopy keeps a ledger. So do I.");

        v = voice("root_cellar");
        v.greetNew = new String[] {"Hungry, {player}? Everyone's hungry."};
        v.greet = new String[] {"Fresh flour, {player}. Mind your boots."};
        v.idle = new String[] {"*grinds grain*", "Flour everywhere. Everywhere.", "Carrots don't pickle themselves."};

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
        v.idle = new String[] {"*sweep sweep*", "Who drops a whole cobblestone?", "Cleanest square on the island. You're welcome."};
        v.farewell = new String[] {"Mind the pile."};
        topic(v, "Seen anything odd?", "A troll's footprint by the mines. Size of a boat.", "I swept it. Evidence gone. Shouldn't have.");

        v = voice("lamp_lighter");
        v.greetNew = new String[] {"Evening's coming, {player}. I can feel it."};
        v.greet = new String[] {"Stay in the light, {player}."};
        v.idle = new String[] {"*polishes a lantern*", "Dusk in a few hours. Ready.",
                "Every lamp has a name. That one's Gerald.", "One more wick. Always one more."};
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
        topic(v, "Your name?", "It was… something with an R? Or a boat. No, that's the boat.",
                "Nobody calls me anything. So it hardly matters.");
        topic(v, "Where does it go?", "Across. Everything goes across eventually.",
                "Past the Veil. Where nobody sails. Except me. Allegedly.");

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
