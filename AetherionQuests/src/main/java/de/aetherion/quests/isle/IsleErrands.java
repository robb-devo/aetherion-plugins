package de.aetherion.quests.isle;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.talk.TalkUx;
import de.aetherion.quests.util.QuestStoryGate;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Isle errands: small, map-smart favours the Origin townsfolk ask — walk somewhere specific, look up
 * at the right moment, ring a bell, sit still in the woods, light a lamp after dusk.
 * <p>
 * Deliberately <b>not</b> quests: players hold one quest at a time and an errand must never cancel
 * the tutorial or the story. Errands run beside the quest log, several at once, each once per
 * player. They surface as one extra reply chip in the NPC's normal small talk (TalkUx API only — the
 * shell is untouched), check progress on a 10-tick clock and pay coins through the quest reward path.
 * Errands open after the tutorial; before that the NPCs only chat.
 * <p>
 * English text lives here; German in {@code lang/de.yml → talk.isle_<errand>_*}.
 */
public final class IsleErrands implements Listener {

    // ------------------------------------------------------------------ model

    enum Kind { VISIT, LINGER, TOUCH, LOOK }

    /** One errand step. {@code beacon}: where the hint shimmer shows (defaults to the spot). */
    record Spot(String id, Kind kind, double x, double y, double z, double radius, int seconds, boolean night,
                double[] target, double[] beacon, String found, String hint) {
    }

    record Errand(String id, String npcId, String title, int reward, int limitSeconds, List<Spot> spots,
                  String chip, String[] pitch, String acceptLabel, String[] accepted, String declineLabel,
                  String[] declined, String activeChip, String doneChip, String[] done, String[] fail) {
    }

    private enum State { ACTIVE, READY, DONE }

    private static final class Progress {
        State state = State.ACTIVE;
        final Set<String> found = new LinkedHashSet<>();
        long started;
        final Map<String, Integer> linger = new HashMap<>();
        Location still;
    }

    private static final class Builder {
        private final String id;
        private final String npc;
        private final String title;
        private final int reward;
        private int limit;
        private final List<Spot> spots = new ArrayList<>();
        private String chip = "Need a hand?";
        private String[] pitch = new String[0];
        private String acceptLabel = "I'll do it.";
        private String[] accepted = new String[0];
        private String declineLabel = "Not now.";
        private String[] declined = new String[0];
        private String activeChip = "About that errand…";
        private String doneChip = "It's done.";
        private String[] done = new String[0];
        private String[] fail = new String[0];

        Builder(String id, String npc, String title, int reward) {
            this.id = id;
            this.npc = npc;
            this.title = title;
            this.reward = reward;
        }

        Builder limit(int seconds) {
            this.limit = seconds;
            return this;
        }

        Builder chip(String label) {
            this.chip = label;
            return this;
        }

        Builder pitch(String... lines) {
            this.pitch = lines;
            return this;
        }

        Builder accept(String label, String... lines) {
            this.acceptLabel = label;
            this.accepted = lines;
            return this;
        }

        Builder decline(String label, String... lines) {
            this.declineLabel = label;
            this.declined = lines;
            return this;
        }

        Builder active(String label) {
            this.activeChip = label;
            return this;
        }

        Builder done(String label, String... lines) {
            this.doneChip = label;
            this.done = lines;
            return this;
        }

        Builder fail(String... lines) {
            this.fail = lines;
            return this;
        }

        Builder visit(String spot, double x, double y, double z, double r, String found, String hint) {
            spots.add(new Spot(spot, Kind.VISIT, x, y, z, r, 0, false, null, null, found, hint));
            return this;
        }

        Builder visitNight(String spot, double x, double y, double z, double r, double[] beacon, String found, String hint) {
            spots.add(new Spot(spot, Kind.VISIT, x, y, z, r, 0, true, null, beacon, found, hint));
            return this;
        }

        Builder linger(String spot, double x, double y, double z, double r, int seconds, boolean night, String found, String hint) {
            spots.add(new Spot(spot, Kind.LINGER, x, y, z, r, seconds, night, null, null, found, hint));
            return this;
        }

        Builder touch(String spot, int bx, int by, int bz, boolean night, String found, String hint) {
            spots.add(new Spot(spot, Kind.TOUCH, bx + 0.5d, by + 0.5d, bz + 0.5d, 1.6d, 0, night, null, null, found, hint));
            return this;
        }

        Builder look(String spot, double x, double y, double z, double maxDistance, double[] target, String found, String hint) {
            spots.add(new Spot(spot, Kind.LOOK, x, y, z, maxDistance, 0, false, target, target, found, hint));
            return this;
        }

        Errand build() {
            return new Errand(id, npc, title, reward, limit, List.copyOf(spots), chip, pitch, acceptLabel, accepted,
                    declineLabel, declined, activeChip, doneChip, done, fail);
        }
    }

    // ------------------------------------------------------------------ the errands (EN)

    static final Map<String, Errand> ERRANDS = new LinkedHashMap<>();

    private static void add(Builder b) {
        Errand e = b.build();
        ERRANDS.put(e.id(), e);
    }

    static {
        add(new Builder("skyward_manifest", "harbourmaster", "Skyward Manifest", 150)
                .chip("Need a hand?")
                .pitch("The Halcyon dropped her manifest in the drink. Three slips survived.",
                        "Each one belongs somewhere on my harbour. Read it where it belongs — you'll know.",
                        "Lighthouse, forge, bells. Whatever order your feet like.")
                .accept("I'll deliver them.", "Good. Mind the ink, it runs.")
                .decline("Not now.", "The sea's patient. I'm less so.")
                .active("About those slips…")
                .visit("moll", 266.5, 63, -461.5, 4.5,
                        "Slip one: \"Lamp oil, two casks, for O. Moll.\" Moll sniffs it. \"Right oil. Wrong year.\"",
                        "First slip's for Old Moll. Beacon Pier, by the big ship. She'll be squinting.")
                .visit("forge", 172.5, 64, -364.5, 7,
                        "Slip two: \"Horseshoes, twelve.\" Nobody on this island owns a horse. The forge files it under 'hope'.",
                        "Second slip smells of coal. West quay — follow the anvils.")
                .visit("wharf", 293.5, 64, -292.5, 7,
                        "Slip three: \"One bell clapper, slightly used.\" Somebody thinks the Wharf Bell isn't loud enough.",
                        "Third one's for the bells. South end of the harbour, past the boats.")
                .done("All three read.", "Lamp oil, horseshoes, a spare clapper. Proper harbour business.",
                        "Good feet, {player}. The Halcyon pays in coin. So do I."));

        add(new Builder("lantern_round", "lamp_lighter", "The Lantern Round", 140)
                .chip("Need a hand tonight?")
                .pitch("Four lamps on the quay won't take from my pole. Stubborn wicks.",
                        "Light them by hand after dusk — right-click the lantern, say something kind.",
                        "Market square, the stall pier, the fish market, the west quay. Before morning.")
                .accept("I'll light them.", "Mind your sleeves. The fish market lamp bites.")
                .decline("Not tonight.", "The dark doesn't mind. I do.")
                .active("About the lamps…")
                .touch("market", 219, 65, -415, true,
                        "The market lamp gutters, then holds. Somewhere behind you, Wick nods.",
                        "One's by the Market Hall — the little square with four posts.")
                .touch("pier", 252, 64, -445, true,
                        "The stall-pier lamp takes. A gull complains about the light.",
                        "One's out on the stall pier, where the striped awnings flap.")
                .touch("fish", 277, 64, -369, true,
                        "The fish market lamp spits once — it does bite — then glows.",
                        "One's at the fish market. Follow your nose.")
                .touch("west", 186, 65, -383, true,
                        "The west quay lamp lights the cobbles. A cat on a barrel looks unimpressed.",
                        "And the west quay, on the forge road.")
                .done("Lamps are lit.", "Four for four. The quay hasn't been this bright since the festival.",
                        "Oil money. Don't spend it on oil."));

        add(new Builder("gull_thief", "lighthouse_keeper", "The Gull Thief", 140)
                .chip("Lost something?")
                .pitch("Spectacles. Brass rims. Snatched off my nose by a gull the size of a dog.",
                        "It perches where the fish are, then where the wind is, then wherever's highest.",
                        "Follow the feathers. Bring them back unpecked, if you can.")
                .accept("I'll find them.", "Bless you. I'd go myself, but I'd walk into the sea.")
                .decline("Not today.", "Then I'll keep squinting. At you, mostly.")
                .active("About the gull…")
                .visit("fishmarket", 270.5, 63, -369.5, 6,
                        "Feathers. Fish heads. A gull's idea of a picnic. No spectacles.",
                        "Start at the fish market. Gulls never skip lunch.")
                .visit("rocks", 342.5, 74, -371.5, 5,
                        "On the lookout rocks: a feather, a button, and a very smug absence of gull.",
                        "Then the windy rocks above the east beach.")
                .visit("hill", 160.5, 100, -279.5, 6,
                        "In a nest of rope and string on the high hill: brass rims. Moll's spectacles. Barely pecked.",
                        "Last, the highest ground over the harbour. South-west, up the hill.")
                .done("Your spectacles.", "Oh! There you are. …The spectacles, I mean. You're fine too.",
                        "Now I can see how short you are. Take this, with my thanks."));

        add(new Builder("high_notes", "shanty_busker", "High Notes", 120)
                .chip("Writing a song?")
                .pitch("The Harbour Shanty's missing its key. Every good shanty steals one from a bell.",
                        "Ring the three bells at the south wharf — the low one, the tower, the high one.",
                        "I'll be listening from here. Somehow.")
                .accept("I'll ring them.", "Ring 'em like you mean it!")
                .decline("Maybe later.", "The song can wait. Songs are good at that.")
                .active("About the bells…")
                .touch("wharf", 284, 65, -296, false,
                        "Bong. Low and round — that's the bass line.",
                        "The Wharf Bell hangs by the water at the south end.")
                .touch("low", 302, 65, -292, false,
                        "A middle note, a little cracked. Perfect for a chorus.",
                        "The tower bell — bottom of the bell tower.")
                .touch("high", 302, 70, -292, false,
                        "A bright, high ping. Somewhere on the quay, Tobin is crying.",
                        "And the high bell, up the tower. Nobody climbs that — try an arrow.")
                .done("I rang them.", "Bass, chorus, ping! ♪ Three bells at the wharf and the tide coming in… ♪ It's finished!",
                        "First royalties. Very first. Don't tell the other buskers."));

        add(new Builder("missing_view", "painter", "The Missing View", 240)
                .chip("Can I help?")
                .pitch("I painted the perfect view and forgot where I sat. Three sketches, one painting.",
                        "A bridge over slow water. A lake crowded with lilies. A shrine under old trees.",
                        "All west, I think. Whisperwood. Stand where I stood and you'll feel it.")
                .accept("I'll find them.", "Look for places that look like someone was painting them.")
                .decline("Not now.", "Of course. The views aren't going anywhere. Hopefully.")
                .active("About the views…")
                .visit("bridge", -269.5, 57, -261.5, 9,
                        "The Crossing Bridge — yes, the first sketch: water so slow it's thinking.",
                        "The bridge is where the road crosses into the wilds.")
                .visit("lilies", -419.5, 60, -359.5, 14,
                        "Lily Lake. So many lilies you could walk on them. Please don't.",
                        "The lilies are far west — a lake the frogs think they own.")
                .visit("shrine", -284.5, 69, -499.5, 12,
                        "The Old Shrine. There's a smudge of green paint on a stone. Fenna sat right here.",
                        "The shrine is north, deep in Whisperwood, under the oldest trees.")
                .done("I found your views.", "All three? Then I wasn't dreaming. That's a relief — sometimes I am.",
                        "When the painting's done, you're in it. Small. Very small. Waving."));

        add(new Builder("swarm_season", "beekeeper", "Swarm Season", 170)
                .chip("Lost a swarm?")
                .pitch("My queen took a swarm out at dawn. Bees follow her like gossip follows Fry.",
                        "She always visits the giant golden flowers first — you can't climb up there, but you can spot the swarm.",
                        "Then she checks the old hive yards, north and south of the house. Find her trail.")
                .accept("I'll track her.", "Hear buzzing, walk toward it. Hear angry buzzing, walk faster.")
                .decline("Not now.", "She'll come home. Queens always do. Eventually.")
                .active("About the swarm…")
                .look("flowers", 378.0, 89.0, -104.0, 95.0, new double[]{378.0, 122.0, -104.0},
                        "There — a gold-and-black haze around the giant flower heads. The swarm, showing off.",
                        "The giant golden flowers stand south-east of the house. Look up at them.")
                .visit("north", 290.5, 61, -86.5, 6,
                        "The north hive yard hums. A few stragglers say she passed through.",
                        "The north hive yard — up the road from the house.")
                .visit("south", 327.5, 61, 14.5, 6,
                        "The south hives buzz with gossip. The queen was here — and she's heading home.",
                        "The south hive yard, past the house toward the wastes.")
                .done("She's coming home.", "I can hear her! That's the 'I'm home, don't make a fuss' buzz.",
                        "Take this. And a jar, when the mead's ready. Ask in a month. Ask nicely."));

        add(new Builder("summit_souvenir", "lost_tourist", "A Souvenir from the Summit", 200)
                .chip("Still lost?")
                .pitch("I'll be honest. I'm not going up there. My legs have filed a complaint.",
                        "But could you? Stand on the very top of Skyreach — then the golden willows near it.",
                        "Then come tell me what it was like. Lie a little if it was boring.")
                .accept("I'll go up.", "The brochure says 'the Mountain Gate, then the wind'. I still think that's poetry.")
                .decline("Not today.", "That's fine. I'll photograph the fountain. Again.")
                .active("About the summit…")
                .visit("summit", -5.5, 200, -268.5, 6,
                        "The top of Skyreach. The whole island below you, small as a map.",
                        "The summit — through the Mountain Gate, then let the updraft carry you.")
                .visit("willows", -81.5, 193, -258.5, 12,
                        "The Golden Willows. The leaves sound like coins. Pim would love this.",
                        "The golden willows grow on the high shoulder west of the summit.")
                .done("I made it up.", "You did? What's it like? No — don't tell me. Yes — tell me. It's windy, isn't it.",
                        "For you. I was saving it for a postcard, but you're better than a postcard."));

        add(new Builder("hermits_minute", "hermit", "The Hermit's Minute", 120)
                .chip("Can I sit with you?")
                .pitch("Sit here with me for a little while. No talking, no jumping around, no rummaging in your bag.",
                        "Give it about forty breaths. Then the forest starts to show you what it's doing.",
                        "And come back once it's dark. The night sounds completely different, I promise.")
                .accept("I'll sit with you.", "Good. Get comfortable. We start now.")
                .decline("Not right now.", "That's alright. The forest isn't going anywhere.")
                .active("About listening…")
                .linger("sit", -409.5, 58, -238.5, 3.5, 40, false,
                        "You stop fidgeting. Leaves rustle, a woodpecker knocks, and far away you can hear the sea.",
                        "Just stay near me and stand still for a while. Breathe slowly.")
                .linger("night", -409.5, 58, -238.5, 3.5, 20, true,
                        "At night the wood creaks like an old ship. Owls call to each other, and Moss smiles in the dark.",
                        "Come back after dark, same spot, and stand still again.")
                .done("I listened.", "You heard it, didn't you? Most people only ever hear themselves.",
                        "Take this. I know, coins are noisy. Spend them on something quiet."));

        add(new Builder("night_light", "mushroom_forager", "Night Light", 150)
                .chip("Glowing mushrooms?")
                .pitch("After dark, three of the giant caps around here glow. Pale blue at the gills.",
                        "Stand under all three before morning. Don't touch, don't lick, don't name them.",
                        "One by my patch, one over the north path, one down south past the camp.")
                .accept("I'll find them.", "Bring a torch. Not for light — for courage.")
                .decline("Not tonight.", "Fair. Mushrooms are patient. That's their whole thing.")
                .active("About the caps…")
                .visitNight("west_cap", 347.5, 68, -366.5, 6, new double[]{347.5, 81.5, -366.5},
                        "The first cap glows like a lamp in fog. You feel slightly smarter. You aren't.",
                        "The first one's just west of my patch — the tall red one.")
                .visitNight("north_cap", 361.5, 69, -378.5, 6, new double[]{361.5, 82.5, -378.5},
                        "The second one hums. Mushrooms don't hum. This one does.",
                        "The second leans over the path to the north-east.")
                .visitNight("south_cap", 396.5, 62, -279.5, 7, new double[]{396.5, 74.5, -279.5},
                        "The third throws blue light across the grass. Fireflies arrive to argue with it.",
                        "The third is down south, past the lumber camp — a giant among giants.")
                .done("I saw all three.", "All three! You've got the eyes for it. Or the luck. Same thing out here.",
                        "And some advice: the blue ones are safe. The other blue ones aren't."));

        add(new Builder("long_shot", "bookie", "Odds Mulligan's Long Shot", 250)
                .limit(180)
                .chip("What's the wager?")
                .pitch("Three minutes. You run to the Mountain Gate, slap the stone, and run back to me.",
                        "Skyways allowed. Pads allowed. Teleports — I'll know.",
                        "Win, I pay double. Lose, I tell everyone. Clock starts when you say yes.")
                .accept("You're on.", "Clock's running! Why are you still here?!")
                .decline("No bet.", "Smart. Boring, but smart.")
                .active("Still running!")
                .visit("gate", 0.5, 67, -202.5, 6,
                        "You slap the Mountain Gate. Now back to Odds — run!",
                        "The Mountain Gate — north, past Fountain Square and up the Grand Stair.")
                .fail("Time! I've seen faster snails. Come back when you've stretched.")
                .done("Back! Pay up.", "Under three! I'd check your boots for springs if I weren't paying.",
                        "Double, as promised. Don't tell Vince."));
    }

    // ------------------------------------------------------------------ runtime

    private static IsleErrands instance;

    private final AetherionQuests plugin;
    private final File file;
    private final Map<UUID, Map<String, Progress>> players = new HashMap<>();
    private boolean dirty;
    private BukkitTask task;
    private long clock;

    private IsleErrands(AetherionQuests plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "isle-errands.yml");
        load();
    }

    public static IsleErrands start(AetherionQuests plugin) {
        if (instance != null) {
            instance.stop();
        }
        instance = new IsleErrands(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, instance::tick, 40L, 10L);
        return instance;
    }

    public static IsleErrands get() {
        return instance;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("isle-errands.enabled", true);
    }

    private static Errand errandFor(String npcId) {
        if (npcId == null) {
            return null;
        }
        for (Errand e : ERRANDS.values()) {
            if (e.npcId().equalsIgnoreCase(npcId)) {
                return e;
            }
        }
        return null;
    }

    private Map<String, Progress> of(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
    }

    // ------------------------------------------------------------------ text (EN here, DE in talk.isle_*)

    private static String key(Errand e, String field) {
        return "isle_" + e.id() + "_" + field;
    }

    private static String text(Player p, Errand e, String field, String en) {
        return LangPack.say(p, key(e, field), en);
    }

    private static String[] lines(Player p, Errand e, String field, String[] en) {
        return LangPack.sayLines(p, key(e, field), en);
    }

    private static String spotText(Player p, Errand e, Spot s, String field, String en) {
        String k = key(e, s.id() + "_" + field);
        return LangPack.say(p, k, en);
    }

    private static String npcName(Errand e) {
        QuestNPC npc = QuestNPCRegistry.getNPC(e.npcId());
        return npc == null ? e.npcId() : npc.getName();
    }

    // ------------------------------------------------------------------ the chip (DialogManager#offerTopics)

    /**
     * The errand reply chip for this NPC right now, or null. {@code reoffer} brings the small-talk chips back
     * once the errand lines have played.
     */
    public static TalkUx.Choice chip(Player player, QuestNPC npc, Runnable reoffer) {
        IsleErrands self = instance;
        if (self == null || player == null || npc == null || !self.enabled()) {
            return null;
        }
        Errand e = errandFor(npc.getId());
        if (e == null) {
            return null;
        }
        AetherionQuests plugin = self.plugin;
        if (!QuestStoryGate.tutorialDone(player, plugin.getQuestManager())) {
            return null;
        }
        Progress p = self.of(player).get(e.id());
        if (p != null && p.state == State.DONE) {
            return null;
        }
        if (p == null) {
            String label = text(player, e, "chip", e.chip());
            return new TalkUx.Choice(label, NamedTextColor.GOLD, () -> self.pitch(player, npc, e, reoffer), false, label);
        }
        if (p.state == State.READY) {
            String label = text(player, e, "done_chip", e.doneChip());
            return new TalkUx.Choice(label, NamedTextColor.GREEN, () -> self.turnIn(player, npc, e, reoffer), false, label);
        }
        String label = text(player, e, "active_chip", e.activeChip());
        return new TalkUx.Choice(label, NamedTextColor.YELLOW, () -> self.nudge(player, npc, e, reoffer), false, label);
    }

    private void say(Player player, QuestNPC npc, String[] lines) {
        for (String line : lines) {
            LivingNpcProfile.say(player, npc, de.aetherion.quests.talk.TalkText.fill(player, line));
        }
    }

    private static void bar(Player player, String legacy) {
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    private void after(Player player, Runnable then) {
        long wait = Math.max(5L, DialogPace.untilReplies(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                then.run();
            }
        }, wait);
    }

    private void pitch(Player player, QuestNPC npc, Errand e, Runnable reoffer) {
        say(player, npc, lines(player, e, "pitch", e.pitch()));
        after(player, () -> {
            TalkUx talk = TalkUx.get();
            if (talk == null) {
                return;
            }
            String yes = text(player, e, "accept", e.acceptLabel());
            String no = text(player, e, "decline", e.declineLabel());
            List<TalkUx.Choice> choices = new ArrayList<>();
            choices.add(new TalkUx.Choice(yes, NamedTextColor.GREEN, () -> accept(player, npc, e, reoffer), false, yes));
            choices.add(new TalkUx.Choice(no, NamedTextColor.GRAY, () -> {
                say(player, npc, lines(player, e, "declined", e.declined()));
                after(player, reoffer);
            }, false, no));
            talk.choices(player, npc.getId(), npc.getName(), choices);
        });
    }

    private void accept(Player player, QuestNPC npc, Errand e, Runnable reoffer) {
        Progress p = new Progress();
        p.started = System.currentTimeMillis();
        of(player).put(e.id(), p);
        dirty = true;
        say(player, npc, lines(player, e, "accepted", e.accepted()));
        String title = text(player, e, "title", e.title());
        player.sendMessage("§6✦ " + LangPack.say(player, "isle_errand", "Errand") + " §8· §e" + title + " §8· §70/" + e.spots().size()
                + (e.limitSeconds() > 0 ? " §8· §c" + e.limitSeconds() / 60 + ":" + String.format("%02d", e.limitSeconds() % 60) : ""));
        Spot next = nextSpot(e, p);
        if (next != null) {
            player.sendMessage("§8  ➥ §7" + spotText(player, e, next, "hint", next.hint()));
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.2f);
        after(player, reoffer);
    }

    private void nudge(Player player, QuestNPC npc, Errand e, Runnable reoffer) {
        Progress p = of(player).get(e.id());
        Spot next = p == null ? null : nextSpot(e, p);
        if (next != null) {
            LivingNpcProfile.say(player, npc, de.aetherion.quests.talk.TalkText.fill(player, spotText(player, e, next, "hint", next.hint())));
        }
        if (p != null && e.limitSeconds() > 0) {
            long left = e.limitSeconds() - (System.currentTimeMillis() - p.started) / 1000L;
            bar(player, "§c⏱ " + Math.max(0, left) + "s");
        }
        after(player, reoffer);
    }

    private void turnIn(Player player, QuestNPC npc, Errand e, Runnable reoffer) {
        Progress p = of(player).get(e.id());
        if (p == null || p.state != State.READY) {
            return;
        }
        if (e.limitSeconds() > 0 && expired(e, p)) {
            fail(player, e, p, npc);
            after(player, reoffer);
            return;
        }
        p.state = State.DONE;
        dirty = true;
        say(player, npc, lines(player, e, "done", e.done()));
        after(player, () -> {
            plugin.getQuestManager().giftCoins(player, e.reward());
            String title = text(player, e, "title", e.title());
            player.sendMessage("§a✔ " + LangPack.say(player, "isle_errand", "Errand") + " §8· §f" + title + " §8· §7"
                    + doneCount(player) + "/" + ERRANDS.size());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            reoffer.run();
        });
    }

    private int doneCount(Player player) {
        int n = 0;
        for (Progress p : of(player).values()) {
            if (p.state == State.DONE) {
                n++;
            }
        }
        return n;
    }

    private static Spot nextSpot(Errand e, Progress p) {
        for (Spot s : e.spots()) {
            if (!p.found.contains(s.id())) {
                return s;
            }
        }
        return null;
    }

    private static boolean expired(Errand e, Progress p) {
        return e.limitSeconds() > 0 && System.currentTimeMillis() - p.started > e.limitSeconds() * 1000L;
    }

    private void fail(Player player, Errand e, Progress p, QuestNPC npc) {
        of(player).remove(e.id());
        dirty = true;
        String[] fail = lines(player, e, "fail", e.fail());
        if (npc != null) {
            say(player, npc, fail);
        } else if (fail.length > 0) {
            player.sendMessage("§c✖ " + text(player, e, "title", e.title()) + " §8· §7" + de.aetherion.quests.talk.TalkText.fill(player, fail[0]));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------ progress (every 10 ticks)

    private static boolean night(World world) {
        long t = world.getTime() % 24000L;
        return t >= 12800L && t <= 23200L;
    }

    private void tick() {
        clock++;
        if (!enabled() || players.isEmpty()) {
            return;
        }
        String worldName = plugin.getConfig().getString("island-cast.world", "world");
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Progress> mine = players.get(player.getUniqueId());
            if (mine == null || mine.isEmpty() || !player.getWorld().getName().equals(worldName)) {
                continue;
            }
            for (Map.Entry<String, Progress> entry : new ArrayList<>(mine.entrySet())) {
                Errand e = ERRANDS.get(entry.getKey());
                Progress p = entry.getValue();
                if (e == null || p.state == State.DONE) {
                    continue;
                }
                if (e.limitSeconds() > 0 && expired(e, p)) {
                    fail(player, e, p, null);
                    continue;
                }
                if (p.state != State.ACTIVE) {
                    continue;
                }
                for (Spot s : e.spots()) {
                    if (!p.found.contains(s.id())) {
                        check(player, e, p, s);
                    }
                }
            }
        }
        if (dirty && clock % 60 == 0) {
            save();
        }
    }

    private void check(Player player, Errand e, Progress p, Spot s) {
        Location at = player.getLocation();
        World world = player.getWorld();
        double dx = at.getX() - s.x();
        double dz = at.getZ() - s.z();
        double flat = Math.sqrt(dx * dx + dz * dz);
        boolean dark = night(world);
        // Hint shimmer within 28 blocks (not for LOOK: the target is the hint).
        if (clock % 4 == 0 && flat < 28.0d && s.kind() != Kind.LOOK && (!s.night() || dark)) {
            double[] b = s.beacon() != null ? s.beacon() : new double[]{s.x(), s.y() + (s.kind() == Kind.TOUCH ? 0.6d : 1.2d), s.z()};
            Location shimmer = new Location(world, b[0], b[1], b[2]);
            if (s.night()) {
                player.spawnParticle(Particle.DUST, shimmer, 6, 0.6, 0.4, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.6f));
            } else {
                player.spawnParticle(Particle.WAX_ON, shimmer, 3, 0.3, 0.3, 0.3, 0.0);
            }
        }
        switch (s.kind()) {
            case VISIT -> {
                if (flat <= s.radius() && Math.abs(at.getY() - s.y()) <= s.radius() + 4.0d && (!s.night() || dark)) {
                    found(player, e, p, s);
                } else if (s.night() && !dark && flat <= s.radius() && clock % 20 == 0) {
                    bar(player, "§7" + LangPack.say(player, "isle_needs_night", "Nothing glows yet. Come back after dusk."));
                }
            }
            case LINGER -> {
                boolean inside = flat <= s.radius() && Math.abs(at.getY() - s.y()) <= 3.0d && (!s.night() || dark);
                if (!inside) {
                    p.linger.remove(s.id());
                    p.still = null;
                    return;
                }
                boolean moved = p.still != null && p.still.getWorld() == world && p.still.distanceSquared(at) > 0.36d;
                p.still = at.clone();
                int ticks = moved ? 0 : p.linger.getOrDefault(s.id(), 0) + 10;
                p.linger.put(s.id(), ticks);
                int need = s.seconds() * 20;
                if (ticks >= need) {
                    p.linger.remove(s.id());
                    found(player, e, p, s);
                } else if (clock % 2 == 0) {
                    int pct = (int) (ticks * 10L / Math.max(1, need));
                    bar(player, "§2" + "▮".repeat(pct) + "§8" + "▮".repeat(10 - pct) + " §7" + (moved
                            ? LangPack.say(player, "isle_still", "stand still…") : LangPack.say(player, "isle_listen", "listening…")));
                }
            }
            case LOOK -> {
                if (flat > s.radius() || s.target() == null) {
                    return;
                }
                Location eye = player.getEyeLocation();
                Vector to = new Vector(s.target()[0] - eye.getX(), s.target()[1] - eye.getY(), s.target()[2] - eye.getZ());
                double dist = to.length();
                if (dist < 1.0e-3 || eye.getDirection().angle(to) > Math.toRadians(11.0d)) {
                    return;
                }
                RayTraceResult hit = world.rayTraceBlocks(eye, to.clone().normalize(), Math.max(1.0d, dist - 4.0d));
                if (hit == null || hit.getHitBlock() == null) {
                    found(player, e, p, s);
                }
            }
            case TOUCH -> {
                // handled by onTouch
            }
        }
    }

    private void found(Player player, Errand e, Progress p, Spot s) {
        if (!p.found.add(s.id())) {
            return;
        }
        dirty = true;
        int n = p.found.size();
        int total = e.spots().size();
        String title = text(player, e, "title", e.title());
        player.sendMessage("§e✧ " + title + " §8· §f" + n + "/" + total + " §8· §7"
                + de.aetherion.quests.talk.TalkText.fill(player, spotText(player, e, s, "found", s.found())));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f + n * 0.1f);
        Location fx = s.beacon() != null ? new Location(player.getWorld(), s.beacon()[0], s.beacon()[1], s.beacon()[2])
                : player.getLocation().add(0, 1.0, 0);
        player.spawnParticle(Particle.HAPPY_VILLAGER, fx, 12, 0.6, 0.6, 0.6, 0.0);
        if (n >= total) {
            p.state = State.READY;
            String who = npcName(e);
            player.sendMessage("§6  ➥ " + LangPack.format(player, "talk.isle_return", "Back to {0}.", "§e" + who + "§6"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.4f);
        } else {
            Spot next = nextSpot(e, p);
            if (next != null) {
                player.sendMessage("§8  ➥ §7" + spotText(player, e, next, "hint", next.hint()));
            }
        }
    }

    // ------------------------------------------------------------------ touch / teleport / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTouch(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() == org.bukkit.Material.BELL) {
            return; // bells answer through BellRingEvent (clicks and arrows alike)
        }
        touched(event.getPlayer(), event.getClickedBlock());
    }

    /** Bells count when rung by hand or by the player's arrow ("nobody climbs that"). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRing(org.bukkit.event.block.BellRingEvent event) {
        org.bukkit.entity.Entity by = event.getEntity();
        Player player = null;
        if (by instanceof Player p) {
            player = p;
        } else if (by instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) {
            player = p;
        }
        if (player != null) {
            touched(player, event.getBlock());
        }
    }

    private void touched(Player player, Block block) {
        Map<String, Progress> mine = players.get(player.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Progress> entry : mine.entrySet()) {
            Errand e = ERRANDS.get(entry.getKey());
            Progress p = entry.getValue();
            if (e == null || p.state != State.ACTIVE) {
                continue;
            }
            for (Spot s : e.spots()) {
                if (s.kind() != Kind.TOUCH || p.found.contains(s.id())) {
                    continue;
                }
                double dx = block.getX() + 0.5d - s.x();
                double dy = block.getY() + 0.5d - s.y();
                double dz = block.getZ() + 0.5d - s.z();
                if (dx * dx + dy * dy + dz * dz > s.radius() * s.radius()) {
                    continue;
                }
                if (s.night() && !night(block.getWorld())) {
                    bar(player, "§7" + LangPack.say(player, "isle_lamp_day", "Broad daylight. The lamps wake after dusk."));
                    return;
                }
                Location fx = block.getLocation().add(0.5, 0.6, 0.5);
                player.spawnParticle(Particle.FLAME, fx, 6, 0.15, 0.15, 0.15, 0.01);
                found(player, e, p, s);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        if (event.getFrom().getWorld() == event.getTo().getWorld() && event.getFrom().distanceSquared(event.getTo()) < 400.0d) {
            return; // small corrections (seat snaps, talk pulls) aren't cheating
        }
        Player player = event.getPlayer();
        Map<String, Progress> mine = players.get(player.getUniqueId());
        if (mine == null) {
            return;
        }
        for (Map.Entry<String, Progress> entry : new ArrayList<>(mine.entrySet())) {
            Errand e = ERRANDS.get(entry.getKey());
            if (e != null && e.limitSeconds() > 0 && entry.getValue().state != State.DONE) {
                fail(player, e, entry.getValue(), null);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Map<String, Progress> mine = players.get(event.getPlayer().getUniqueId());
        if (mine == null) {
            return;
        }
        // A race can't survive logging out; everything else waits.
        mine.entrySet().removeIf(en -> {
            Errand e = ERRANDS.get(en.getKey());
            boolean drop = e != null && e.limitSeconds() > 0 && en.getValue().state != State.DONE;
            if (drop) {
                dirty = true;
            }
            return drop;
        });
        for (Progress p : mine.values()) {
            p.linger.clear();
            p.still = null;
        }
    }

    // ------------------------------------------------------------------ DEV / status

    public String status(Player player) {
        StringBuilder out = new StringBuilder("§6Errands §8· §7" + doneCount(player) + "/" + ERRANDS.size() + " done");
        for (Errand e : ERRANDS.values()) {
            Progress p = of(player).get(e.id());
            String mark = p == null ? "§8○" : switch (p.state) {
                case ACTIVE -> "§e◐ " + p.found.size() + "/" + e.spots().size();
                case READY -> "§a◑ → " + npcName(e);
                case DONE -> "§a●";
            };
            out.append("\n ").append(mark).append(" §f").append(text(player, e, "title", e.title())).append(" §8(").append(npcName(e)).append(")");
        }
        return out.toString();
    }

    public String reset(Player player) {
        players.remove(player.getUniqueId());
        dirty = true;
        save();
        return "§eErrands reset for " + player.getName() + ".";
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String uuid : root.getKeys(false)) {
            ConfigurationSection ps = root.getConfigurationSection(uuid);
            if (ps == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(uuid);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            Map<String, Progress> mine = new HashMap<>();
            for (String errand : ps.getKeys(false)) {
                ConfigurationSection es = ps.getConfigurationSection(errand);
                if (es == null || !ERRANDS.containsKey(errand)) {
                    continue;
                }
                Progress p = new Progress();
                try {
                    p.state = State.valueOf(es.getString("state", "ACTIVE").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    p.state = State.ACTIVE;
                }
                p.found.addAll(es.getStringList("found"));
                p.started = es.getLong("started", System.currentTimeMillis());
                if (ERRANDS.get(errand).limitSeconds() > 0 && p.state != State.DONE) {
                    continue; // races don't survive restarts
                }
                mine.put(errand, p);
            }
            if (!mine.isEmpty()) {
                players.put(id, mine);
            }
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Progress>> entry : players.entrySet()) {
            for (Map.Entry<String, Progress> e : entry.getValue().entrySet()) {
                String base = "players." + entry.getKey() + "." + e.getKey();
                y.set(base + ".state", e.getValue().state.name());
                y.set(base + ".found", new ArrayList<>(e.getValue().found));
                y.set(base + ".started", e.getValue().started);
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("isle-errands.yml: " + exception.getMessage());
        }
    }
}
