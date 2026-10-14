package de.aetherion.quests.story;

import de.aetherion.core.api.ExileArrivalEvent;
import de.aetherion.core.api.ExilePlaces;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.service.QuestNPCSpawnService;
import de.aetherion.quests.talk.NpcDesk;
import de.aetherion.quests.ui.QuestHint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Act III on the exile island: who lives there, where they stand, what they say when no story beat is theirs.
 *
 * <p>The island froze one evening a thousand years ago. Its people are the Faded: they still do the one thing
 * they were doing (packing, lighting lamps, guarding a ring, ushering an audience) and remember half of it.
 * Each stands at a door and is that door's voice. The doors themselves (gates, rituals, the sealed castle)
 * are Core's {@code ExileDoors}; the Ferryman's crossing is Core's {@code ExileQuay}/{@code ExileArrival}.
 *
 * <p>Placement: on the exile backend the cast stands itself up at its Runde-7 spots the first time (no
 * {@code /questnpc} needed); {@code /questnpc exile} re-places everyone. On the main world the Ferryman walks to
 * the Veil Quay as soon as Core reports it placed.
 */
public final class ExileStory implements Listener {

    /** Feet + yaw on {@code aetherion_exile} (checked against the Runde 7 schematic: air, air, solid). */
    public static final Map<String, double[]> SPOTS = new LinkedHashMap<>();

    static {
        SPOTS.put("ferryman", new double[] {160.5, 73.0, 7.5, -45});
        SPOTS.put("exile_lamplighter", new double[] {120.5, 105.0, 6.5, -20});
        SPOTS.put("exile_sentry", new double[] {71.5, 121.0, -66.5, 0});
        SPOTS.put("orrin", new double[] {-110.5, 157.0, -175.5, -90});
        SPOTS.put("exile_usher", new double[] {14.5, 143.0, 65.5, -90});
        SPOTS.put("exile_gardener", new double[] {-53.5, 135.0, 119.5, -90});
        SPOTS.put("exile_mother", new double[] {98.5, 105.0, -40.5, 0});
        SPOTS.put("exile_keeper", new double[] {84.5, 105.0, 109.5, 180});
    }

    private static ExileStory instance;

    private final AetherionQuests plugin;

    public ExileStory(AetherionQuests plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getScheduler().runTaskLater(plugin, this::placeExileCast, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::followQuay, 240L, 200L);
    }

    public static ExileStory get() {
        return instance;
    }

    // ------------------------------------------------------------------ placement

    private static World exileWorld() {
        return Bukkit.getWorld(LullabyQuests.EXILE_WORLD);
    }

    /** First boot on mmo-c: stand everyone up where nobody stands yet. */
    private void placeExileCast() {
        World w = exileWorld();
        if (w == null || !plugin.getConfig().getBoolean("exile-cast.auto-place", true)) {
            return;
        }
        NPCDataStorage store = plugin.getNpcDataStorage();
        int placed = 0;
        for (Map.Entry<String, double[]> e : SPOTS.entrySet()) {
            Location saved = store == null ? null : store.getSavedLocation(e.getKey());
            if (saved != null && saved.getWorld() != null && saved.getWorld().equals(w)) {
                continue;
            }
            if (place(e.getKey(), w)) {
                placed++;
            }
        }
        if (placed > 0) {
            plugin.getLogger().info("Exile cast: placed " + placed + " NPC(s) at their Runde 7 spots.");
        }
    }

    /** {@code /questnpc exile}: everyone back to their spot (exile backend), or the Ferryman to the quay. */
    public int replaceAll() {
        World w = exileWorld();
        int n = 0;
        if (w != null) {
            for (String id : SPOTS.keySet()) {
                new QuestNPCSpawnService().forget(id);
                if (place(id, w)) {
                    n++;
                }
            }
            return n;
        }
        Location quay = ExilePlaces.quayFerryman();
        if (quay != null) {
            new QuestNPCSpawnService().forget("ferryman");
            if (new QuestNPCSpawnService().spawnNPC("ferryman", quay) != null) {
                n++;
            }
        }
        return n;
    }

    private static boolean place(String id, World w) {
        double[] s = SPOTS.get(id);
        if (s == null) {
            return false;
        }
        Location at = new Location(w, s[0], s[1], s[2], (float) s[3], 0f);
        w.getChunkAt(at);
        return new QuestNPCSpawnService().spawnNPC(id, at) != null;
    }

    /** Main world: the Ferryman belongs on the Veil Quay once Core says where that is. */
    private void followQuay() {
        if (exileWorld() != null) {
            return;
        }
        Location quay = ExilePlaces.quayFerryman();
        if (quay == null || quay.getWorld() == null) {
            return;
        }
        Location board = ExilePlaces.quayBoard();
        QuestManager qm = plugin.getQuestManager();
        Quest ferry = qm == null ? null : qm.getQuest(LullabyQuests.FERRY);
        if (ferry != null && board != null && !ferry.hasWaypoint()) {
            ferry.setWaypoint(board.getWorld().getName(), board.getX(), board.getY(), board.getZ());
        }
        NPCDataStorage store = plugin.getNpcDataStorage();
        Location saved = store == null ? null : store.getSavedLocation("ferryman");
        if (saved != null && saved.getWorld() != null && saved.getWorld().equals(quay.getWorld())
                && saved.distanceSquared(quay) < 64 * 64) {
            return;
        }
        if (!plugin.getConfig().getBoolean("exile-cast.ferryman-follows-quay", true)) {
            return;
        }
        if (saved != null) {
            new QuestNPCSpawnService().forget("ferryman");
        }
        if (new QuestNPCSpawnService().spawnNPC("ferryman", quay) != null) {
            plugin.getLogger().info("Ferryman moved to the Veil Quay at " + quay.getBlockX() + " " + quay.getBlockY() + " " + quay.getBlockZ());
        }
    }

    // ------------------------------------------------------------------ arrival

    @EventHandler
    public void onArrival(ExileArrivalEvent event) {
        Player p = event.getPlayer();
        QuestManager qm = plugin.getQuestManager();
        if (qm == null) {
            return;
        }
        Quest ferry = qm.getQuest(LullabyQuests.FERRY);
        boolean active = ferry != null && qm.getQuestState(p, ferry) == QuestState.ACTIVE;
        if (active || event.isFirstTime()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                QuestHint.show(p, "ferryman", LangPack.say(p, "ferryman.hint", "The Ferryman"));
                p.sendActionBar(Component.text(LangPack.ui(p, "tip.exile.ferryman",
                        "Step off the barge · talk to the Ferryman"), NamedTextColor.GRAY));
            }, 30L);
        }
    }

    // ------------------------------------------------------------------ next door

    /** spot key, label, line: where this player's story points next on the island. */
    static String[] nextDoor(Player p, QuestManager qm) {
        if (!StoryState.completed(qm, p, LullabyQuests.CAPTAIN)) {
            return new String[] {"exile_crater", LangPack.say(p, "exile.door.crater", "The Iron Ring"),
                    LangPack.say(p, "exile.next.crater", "The Iron Ring, south past the colossi. The Captain went first. So should you.")};
        }
        if (!StoryState.completed(qm, p, LullabyQuests.HEART)) {
            return new String[] {"exile_observatory", LangPack.say(p, "exile.door.observatory", "The Observatory"),
                    LangPack.say(p, "exile.next.observatory", "The Observatory, far out west on its spur. Orrin's star. Talk to him before the ring.")};
        }
        if (!StoryState.completed(qm, p, LullabyQuests.DOLL)) {
            return new String[] {"exile_theater", LangPack.say(p, "exile.door.theater", "The Little Theatre"),
                    LangPack.say(p, "exile.next.theater", "The Little Theatre, off the forecourt. The Usher keeps the door.")};
        }
        if (!StoryState.completed(qm, p, LullabyQuests.SEED)) {
            return new String[] {"exile_garden", LangPack.say(p, "exile.door.garden", "The Root-Ground"),
                    LangPack.say(p, "exile.next.garden", "The Root-Ground, down the gardener's way. Listen for the chewing.")};
        }
        return new String[] {"exile_doors", LangPack.say(p, "exile.door.doors", "The Great Doors"),
                LangPack.say(p, "exile.next.doors", "The Great Doors at the top of the stair. Not yet. But you should see them.")};
    }

    private static Runnable pointNext(Player p, QuestManager qm) {
        return () -> {
            String[] next = nextDoor(p, qm);
            QuestHint.showSpot(p, next[0], next[1]);
            p.sendActionBar(Component.text("→ " + next[1], NamedTextColor.GOLD));
        };
    }

    // ------------------------------------------------------------------ desks

    private static String l(Player p, String key, String en) {
        return LangPack.say(p, key, en);
    }

    private static String[] ls(Player p, String key, String... en) {
        return LangPack.sayLines(p, key, en);
    }

    /**
     * The NPC's own desk when no story beat claimed the click. False = this NPC has none here (or the bubble
     * can't open for this reader) and the caller speaks the stage line instead.
     */
    public static boolean desk(Player p, QuestNPC npc, QuestManager qm) {
        String id = npc.getId().toLowerCase(Locale.ROOT);
        if (NpcDesk.busy(p, id)) {
            return true;
        }
        boolean exile = StoryFlow.onExile(p);
        NpcDesk d = NpcDesk.of(npc);
        String open;
        switch (id) {
            case "ferryman" -> {
                if (!exile) {
                    if (!StoryState.hasPlayerFlag(p, StoryState.CROSSED) && !StoryState.completed(qm, p, LullabyQuests.FERRY)) {
                        return false; // before the first crossing his stage lines carry the gate
                    }
                    open = l(p, "ferryman.desk.open", "Ferry's waiting. It always is.");
                    d.topic(l(p, "ferryman.desk.where.label", "Where does it go?"), NamedTextColor.AQUA,
                                    l(p, "ferryman.desk.where.echo", "Where does the ferry go?"),
                                    ls(p, "ferryman.desk.where.lines",
                                            "Across. Past the Veil, to the old King's Quay.",
                                            "A court over there, stopped mid-evening. Nobody's home. Mostly."))
                            .topic(l(p, "ferryman.desk.name.label", "Your name?"), NamedTextColor.GRAY,
                                    l(p, "ferryman.desk.name.echo", "What's your name?"),
                                    ls(p, "ferryman.desk.name.lines",
                                            "It was... something with an H. Or a boat. No, that's the boat.",
                                            "Nobody calls me anything. So it hardly matters."))
                            .action(l(p, "ferryman.desk.cross.label", "Cross the Veil"), NamedTextColor.GOLD,
                                    l(p, "ferryman.desk.cross.echo", "Take me across."), () -> {
                                        LivingNpcProfile.say(p, npc, l(p, "ferryman.desk.cross.line", "Aboard, then. Hold the rail."));
                                        Bukkit.getScheduler().runTaskLater(AetherionQuests.getInstance(), () -> board(p), 30L);
                                    });
                } else {
                    open = l(p, "ferryman.desk.open_exile", "Still here. I'm always on this side, somehow.");
                    d.topic(l(p, "ferryman.desk.place.label", "What is this place?"), NamedTextColor.AQUA,
                                    l(p, "ferryman.desk.place.echo", "What is this place?"),
                                    ls(p, "ferryman.desk.place.lines",
                                            "The King's Quay. Behind it the court. Above that, the castle.",
                                            "Sixteen worlds were tied up along this coast once. Now it's just us."))
                            .topic(l(p, "ferryman.desk.next.label", "Where now?"), NamedTextColor.GOLD,
                                    l(p, "ferryman.desk.next.echo", "Where should I go?"), () -> {
                                        pointNext(p, qm).run();
                                        return new String[] {l(p, "ferryman.desk.next.lift", "Up the lift first. The lever bites."),
                                                nextDoor(p, qm)[2]};
                                    })
                            .action(l(p, "ferryman.desk.back.label", "Back across"), NamedTextColor.GRAY,
                                    l(p, "ferryman.desk.back.echo", "Take me back."), () -> {
                                        LivingNpcProfile.say(p, npc, l(p, "ferryman.desk.back.line", "Hold the rail. Mind the fog."));
                                        Bukkit.getScheduler().runTaskLater(AetherionQuests.getInstance(), () -> {
                                            if (p.isOnline()) {
                                                p.performCommand("exile return");
                                            }
                                        }, 40L);
                                    });
                }
            }
            case "orrin" -> {
                boolean warned = StoryState.hasPlayerFlag(p, StoryState.ORRIN_WARNED);
                open = warned ? l(p, "orrin.desk.open_warned", "The ring is open for you. I'd rather it weren't.")
                        : l(p, "orrin.desk.open", "Orrin. Court engineer. The star disagrees with my retirement.");
                d.topic(l(p, "orrin.desk.helios.label", "What is Helios?"), NamedTextColor.YELLOW,
                                l(p, "orrin.desk.helios.echo", "What is Helios?"),
                                ls(p, "orrin.desk.helios.lines",
                                        "A sun I built to keep a child alive. It didn't.",
                                        "Someone climbed inside to make it work. It's been beating ever since."))
                        .topic(l(p, "orrin.desk.who.label", "Who climbed inside?"), NamedTextColor.GOLD,
                                l(p, "orrin.desk.who.echo", "Who climbed inside it?"),
                                ls(p, "orrin.desk.who.lines", "...The Queen. Don't make me say it twice."))
                        .topic(l(p, "orrin.desk.log.label", "The logbook?"), NamedTextColor.GRAY,
                                l(p, "orrin.desk.log.echo", "What do you write in there?"),
                                ls(p, "orrin.desk.log.lines",
                                        "Day three hundred and sixty-five thousand. Still beating. Still sorry.",
                                        "One line a day. Most days it's the same line."));
                if (!warned) {
                    d.action(l(p, "orrin.desk.pass.label", "Let me pass"), NamedTextColor.RED,
                            l(p, "orrin.desk.pass.echo", "I'm going through the ring."), () -> {
                                for (String line : ls(p, "orrin.desk.pass.lines",
                                        "Then at least know what you're killing. Her heart is the last rhythm in there.",
                                        "The ring will take you now. Mind the Herald. He's me, mostly.")) {
                                    LivingNpcProfile.say(p, npc, line);
                                }
                                StoryState.setPlayerFlag(p, StoryState.ORRIN_WARNED);
                                QuestHint.showSpot(p, "exile_ring", l(p, "exile.door.ring", "The Star Ring"));
                            });
                }
            }
            case "exile_lamplighter" -> {
                open = l(p, "exile_lamplighter.desk.open", "Two hundred and twelve lamps. Every evening. I'm late. Ask quick.");
                d.topic(l(p, "exile_lamplighter.desk.where.label", "Where am I?"), NamedTextColor.AQUA,
                                l(p, "exile_lamplighter.desk.where.echo", "Where am I?"),
                                ls(p, "exile_lamplighter.desk.where.lines",
                                        "The court of Eldervale. Left exactly as it was the night it stopped.",
                                        "Lift behind you. Court ahead. Castle on top. Shut."))
                        .topic(l(p, "exile_lamplighter.desk.first.label", "Which door first?"), NamedTextColor.GOLD,
                                l(p, "exile_lamplighter.desk.first.echo", "Which door should I try first?"), () -> {
                                    pointNext(p, qm).run();
                                    return new String[] {nextDoor(p, qm)[2]};
                                })
                        .topic(l(p, "exile_lamplighter.desk.doors.label", "All the doors?"), NamedTextColor.YELLOW,
                                l(p, "exile_lamplighter.desk.doors.echo", "What doors are there?"),
                                ls(p, "exile_lamplighter.desk.doors.lines",
                                        "Iron Ring: the fallen captain. Observatory: the old engineer and his star.",
                                        "Little Theatre: a puppet who won't stop. Root-Ground: a golem and a hole that eats.",
                                        "The Great Doors: not yet. Nobody's ready for that one."))
                        .topic(l(p, "exile_lamplighter.desk.lamps.label", "The lamps?"), NamedTextColor.GRAY,
                                l(p, "exile_lamplighter.desk.lamps.echo", "Why light them at all?"),
                                ls(p, "exile_lamplighter.desk.lamps.lines",
                                        "Old law from the Queen. So the children wouldn't be afraid of the dark.",
                                        "We were the first to go, you know. The lamplighters. Nobody wrote that down."));
            }
            case "exile_sentry" -> {
                open = l(p, "exile_sentry.desk.open", "Nobody through. Captain's orders.");
                d.topic(l(p, "exile_sentry.desk.who.label", "Who's in there?"), NamedTextColor.GOLD,
                                l(p, "exile_sentry.desk.who.echo", "Who's behind the ring?"),
                                ls(p, "exile_sentry.desk.who.lines",
                                        "Ser Caedmon. Captain of the Sun Guard. He fell out of the sky.",
                                        "Took him a thousand years. He's still angry about it."))
                        .topic(l(p, "exile_sentry.desk.order.label", "What order?"), NamedTextColor.RED,
                                l(p, "exile_sentry.desk.order.echo", "Which order did he refuse?"),
                                ls(p, "exile_sentry.desk.order.lines",
                                        "The King told him to let a world fall. He said no.",
                                        "Only order he ever refused. Then the King threw him into the sun."))
                        .topic(l(p, "exile_sentry.desk.bars.label", "The bars?"), NamedTextColor.GRAY,
                                l(p, "exile_sentry.desk.bars.echo", "How do I get past the bars?"),
                                ls(p, "exile_sentry.desk.bars.lines",
                                        "They open for the Captain's crest. Or for anyone who pushes.",
                                        "Right-click them. I'll look the other way. I'm good at that."));
            }
            case "exile_usher" -> {
                open = l(p, "exile_usher.desk.open", "Tickets? Nobody has tickets anymore.");
                d.topic(l(p, "exile_usher.desk.playing.label", "What's playing?"), NamedTextColor.LIGHT_PURPLE,
                                l(p, "exile_usher.desk.playing.echo", "What's playing tonight?"),
                                ls(p, "exile_usher.desk.playing.lines",
                                        "The same night. Every night. A little girl, a puppet, a hand on the strings."))
                        .topic(l(p, "exile_usher.desk.who.label", "Who's on stage?"), NamedTextColor.GOLD,
                                l(p, "exile_usher.desk.who.echo", "Who performs?"),
                                ls(p, "exile_usher.desk.who.lines",
                                        "Seraphine. She was a toy. Then one night she was the only one there.",
                                        "She cut her own strings after. I swept them up. I kept one."))
                        .topic(l(p, "exile_usher.desk.in.label", "Can I go in?"), NamedTextColor.GRAY,
                                l(p, "exile_usher.desk.in.echo", "Will the doors let me in?"),
                                ls(p, "exile_usher.desk.in.lines",
                                        "The doors listen for the Queen's seal. They'll let you through anyway.",
                                        "They're lonely. Right-click the grille."));
            }
            case "exile_gardener" -> {
                open = l(p, "exile_gardener.desk.open", "Don't step on the beds. He gets upset.");
                d.topic(l(p, "exile_gardener.desk.he.label", "Who's 'he'?"), NamedTextColor.GREEN,
                                l(p, "exile_gardener.desk.he.echo", "Who gets upset?"),
                                ls(p, "exile_gardener.desk.he.lines",
                                        "The Unbroken. The King's garden golem.",
                                        "He kept the garden after everyone left. Nobody asked him to."))
                        .topic(l(p, "exile_gardener.desk.maw.label", "The Maw?"), NamedTextColor.DARK_GREEN,
                                l(p, "exile_gardener.desk.maw.echo", "What's the Maw?"),
                                ls(p, "exile_gardener.desk.maw.lines",
                                        "A hole at the end of the path. Roots go in. Nothing comes out.",
                                        "Something was planted down there to eat this world and grow a new one."))
                        .topic(l(p, "exile_gardener.desk.swing.label", "The swing?"), NamedTextColor.GRAY,
                                l(p, "exile_gardener.desk.swing.echo", "Who fixes the swing?"),
                                ls(p, "exile_gardener.desk.swing.lines",
                                        "He does. Every year. New board, same rope.",
                                        "Nobody's sat on it in a thousand years. He fixes it anyway."));
            }
            case "exile_mother" -> {
                open = l(p, "exile_mother.desk.open", "We have to be gone before the dusk toll. Have you seen her other shoe?");
                d.topic(l(p, "exile_mother.desk.who.label", "Who are you looking for?"), NamedTextColor.YELLOW,
                                l(p, "exile_mother.desk.who.echo", "Who's missing?"),
                                ls(p, "exile_mother.desk.who.lines",
                                        "My girl. Small, dark hair, hates the dark.",
                                        "...No. That's not my girl. That's the princess. I'm muddling them again."))
                        .topic(l(p, "exile_mother.desk.toll.label", "The dusk toll?"), NamedTextColor.GRAY,
                                l(p, "exile_mother.desk.toll.echo", "What happens at the dusk toll?"),
                                ls(p, "exile_mother.desk.toll.lines",
                                        "Five strokes, and the gates shut. Whoever isn't packed stays.",
                                        "Last night it was four. I counted. Nobody believes me."));
            }
            case "exile_keeper" -> {
                open = l(p, "exile_keeper.desk.open", "The Last Bell's open. Always open. Nobody drinks.");
                d.topic(l(p, "exile_keeper.desk.theatre.label", "Heard anything?"), NamedTextColor.LIGHT_PURPLE,
                                l(p, "exile_keeper.desk.theatre.echo", "Heard any rumours?"),
                                ls(p, "exile_keeper.desk.theatre.lines",
                                        "Music from the theatre at night. The first four notes. Then nothing."))
                        .topic(l(p, "exile_keeper.desk.star.label", "The observatory?"), NamedTextColor.YELLOW,
                                l(p, "exile_keeper.desk.star.echo", "What about the observatory?"),
                                ls(p, "exile_keeper.desk.star.lines",
                                        "The old engineer talks to a star. The star talks back. Ticking."))
                        .topic(l(p, "exile_keeper.desk.garden.label", "The garden?"), NamedTextColor.GREEN,
                                l(p, "exile_keeper.desk.garden.echo", "Anyone in the garden?"),
                                ls(p, "exile_keeper.desk.garden.lines",
                                        "Someone waters the stones out there. Every evening. Big footprints."))
                        .topic(l(p, "exile_keeper.desk.castle.label", "The castle?"), NamedTextColor.WHITE,
                                l(p, "exile_keeper.desk.castle.echo", "And the castle?"),
                                ls(p, "exile_keeper.desk.castle.lines",
                                        "One window's lit in the bell tower. Always the same one.",
                                        "Don't go knocking. Nobody's answered in a thousand years."));
            }
            default -> {
                return false;
            }
        }
        return d.open(p, open);
    }

    /** The Ferryman's "Cross the Veil": Core's quay does the boarding (story gate included). */
    private static void board(Player p) {
        if (!p.isOnline()) {
            return;
        }
        de.aetherion.core.AetherionCore core = de.aetherion.core.AetherionCore.get();
        de.aetherion.core.exile.ExileQuay quay = core == null ? null : core.exileQuay();
        if (quay != null && quay.placed()) {
            quay.board(p, "talk");
        } else {
            p.performCommand("exile go");
        }
    }
}
