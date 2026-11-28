package de.aetherion.tutorial.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.core.api.ProgressAccess;
import de.aetherion.core.api.QuestBars;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.TutorialStore;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the lessons. One {@link Session} per player, one {@link Step} at a time.
 *
 * <p>Two rules shape every step (Robb, 2026-11-28):
 * <ul>
 *     <li>a conversation only moves on when the player talks to the person, and the person reacts when the task is
 *     done; nothing is said into the back of somebody walking away;</li>
 *     <li>what a step hands out (the axe, the coal, the boosters) is handed out the moment the player talks to the
 *     teacher, never after a line was read: whoever clicks every bubble away still holds what the next step needs,
 *     and the bar at the top says what to do with it.</li>
 * </ul>
 */
public final class Director {

    private static final String LEASE = "morningbell";
    private static final int LOGS_GOAL = 4;
    private static final int STONE_GOAL = 12;
    private static final int COAL_GOAL = 5;
    private static final int COBBLE_FOR_ARMOUR = 24;
    private static final String[] ARMOUR = {"combat_helmet", "combat_chestplate", "combat_leggings", "combat_boots"};
    /** Facts of a run that outlive a relog (the booster set is handed over once, a card is shown once). */
    private static final java.util.Set<String> PERSISTENT = java.util.Set.of("boosters", "boosters-auto", "overlook", "skills-card");

    private final TutorialPlugin plugin;
    private final Isle isle;
    private final Map<UUID, Session> sessions = new HashMap<>();

    Director(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
    }

    // ------------------------------------------------------------------ sessions

    Session of(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    private static long now() {
        return Bukkit.getCurrentTick();
    }

    /** A player set foot on the isle (join). Picks the lesson up where the store says it stopped. */
    void join(Player player) {
        UUID id = player.getUniqueId();
        TutorialStore.Entry entry = plugin.store().load(id);
        Session session = new Session(id);
        session.joinedAtMs = System.currentTimeMillis();
        session.guest = entry.guest;
        session.returning = entry.completedOnce || entry.guest;
        Step resume = entry.state == TutorialStore.State.IN_PROGRESS ? Step.parse(entry.step) : null;
        if (entry.state == TutorialStore.State.GRADUATING) {
            // the ferry cast off but the crossing never happened: Wim is still waiting
            resume = Step.TALK_WIM;
        }
        if (resume == null || resume == Step.DONE) {
            resume = Step.ARRIVE;
        } else if (resume == Step.BOSS) {
            resume = Step.GO_FLAT;
        } else if (resume == Step.DEPART) {
            resume = Step.TALK_WIM;
        }
        session.step = resume;
        session.morning = resume.ordinal() > Step.RING_BELL.ordinal();
        session.stepStarted = now();
        session.lastProgress = now();
        sessions.put(id, session);

        entry.name = player.getName();
        boolean fresh = resume == Step.ARRIVE;
        if (entry.state != TutorialStore.State.IN_PROGRESS || fresh) {
            // a new run (first morning, or a visit starting over)
            entry.state = TutorialStore.State.IN_PROGRESS;
            entry.started = System.currentTimeMillis();
            entry.seconds = 0L;
            entry.deaths = 0;
            entry.skipped = false;
            if (fresh) {
                entry.flags.clear();
            }
        }
        session.seen.addAll(entry.flags);
        entry.step = resume.name();
        plugin.store().save(id, entry);

        QuestBars.suppressAll(player, LEASE);
        isle.rules().applyTime(player, session);
        if (fresh) {
            Location landing = isle.marks().at(isle.world(), "arrival");
            if (landing != null) {
                player.teleport(landing);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && of(player) == session) {
                    arrive(player, session);
                }
            }, 12L);
        } else {
            restore(player, session);
        }
    }

    void quit(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        Guide.hide(player, session);
        QuestBars.release(player, LEASE);
        clearGolem(player);
        TutorialStore.Entry entry = plugin.store().get(player.getUniqueId());
        entry.seconds += Math.max(0L, (System.currentTimeMillis() - session.joinedAtMs) / 1000L);
        remember(entry, session);
        plugin.store().save(player.getUniqueId(), entry);
        plugin.store().forget(player.getUniqueId());
    }

    /** Writes the once-per-run facts of the session into the store entry (the caller saves). */
    private static void remember(TutorialStore.Entry entry, Session session) {
        for (String fact : session.seen) {
            if (PERSISTENT.contains(fact)) {
                entry.flags.add(fact);
            }
        }
    }

    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (of(player) != null) {
                quit(player);
            }
        }
    }

    // ------------------------------------------------------------------ answers for Notes

    boolean drilling(Player player) {
        Session s = of(player);
        return s != null && (s.step == Step.DRILL_PARRY || s.step == Step.DRILL_ROLL || s.step == Step.DRILL_FINISHER);
    }

    boolean mayChop(Player player) {
        Session s = of(player);
        return s != null && s.step.ordinal() >= Step.FELL_TREE.ordinal();
    }

    Location target(Player player) {
        Session s = of(player);
        return s == null || s.scene ? null : targetOf(s);
    }

    QuestProgressAccess.JournalQuest journalEntry(Player player) {
        Session s = of(player);
        if (s == null || s.step == Step.DONE) {
            return null;
        }
        String step = "§f" + objective(player, s);
        String detail = Txt.t(player, "Nobody sails to the mainland unprepared: the island teaches you first.",
                "Niemand segelt unvorbereitet zum Festland: Die Insel bringt dir erst alles bei.");
        Cast.Member teacher = s.step.teacher == null ? null : Cast.ALL.get(s.step.teacher);
        return new QuestProgressAccess.JournalQuest("morningbell", "Morningbell", detail, false, true, step,
                teacher == null ? null : teacher.name(), targetOf(s));
    }

    // ------------------------------------------------------------------ where a step points

    private Location targetOf(Session s) {
        Step step = s.step;
        if (step == Step.TRY_IRON && s.a > 0) {
            return isle.cast().spot(Cast.GRETT);
        }
        if (step.talk() && step.teacher != null) {
            Location at = isle.cast().spot(step.teacher);
            if (at != null) {
                return at;
            }
        }
        return step.mark == null ? null : isle.marks().at(isle.world(), step.mark);
    }

    /** What the bar says: eight words at most, a verb first. */
    String objective(Player p, Session s) {
        return switch (s.step) {
            case ARRIVE -> Txt.t(p, "Look around", "Sieh dich um");
            case RING_BELL -> Txt.t(p, "Ring the bell", "Läute die Glocke");
            case TALK_ODA -> Txt.t(p, "Talk to §eOda", "Sprich mit §eOda");
            case OPEN_JOURNAL -> Txt.t(p, "Open your §eJournal §7(right-click the book)", "Öffne dein §eJournal §7(Rechtsklick aufs Buch)");
            case TALK_BURR -> Txt.t(p, "Follow the lanterns to §6Burr", "Folge den Laternen zu §6Burr");
            case FELL_TREE -> Txt.t(p, "Fell an oak with the axe §7", "Fälle eine Eiche mit der Axt §7")
                    + Math.min(s.a, LOGS_GOAL) + "§8/§7" + LOGS_GOAL;
            case TRADE_BURR -> Txt.t(p, "Bring §6Burr §fthe wood", "Bring §6Burr §fdas Holz");
            case CRAFT_PICKAXE -> Txt.t(p, "Craft a §ePickaxe §fat the workbench", "Bau an der Werkbank eine §eSpitzhacke");
            case TALK_GRETT -> Txt.t(p, "Take the path to §7Grett's §fquarry", "Nimm den Weg zu §7Gretts §fSteinbruch");
            case MINE -> Txt.t(p, "Mine stone §7", "Brich Stein §7") + Math.min(s.a, STONE_GOAL) + "§8/§7" + STONE_GOAL
                    + Txt.t(p, " §fand coal §7", " §fund Kohle §7") + Math.min(s.b, COAL_GOAL) + "§8/§7" + COAL_GOAL;
            case TRY_IRON -> s.a > 0
                    ? Txt.t(p, "Talk to §7Grett", "Sprich mit §7Grett")
                    : Txt.t(p, "Try the iron vein", "Versuch dich an der Eisenader");
            case TALK_ANSEL -> Txt.t(p, "Carry the stone to §cAnsel's §fforge", "Bring den Stein zu §cAnsels §fSchmiede");
            case CRAFT_ARMOR -> Txt.t(p, "Craft the stone armour §7", "Bau die Steinrüstung §7")
                    + Integer.bitCount(s.a) + "§8/§74";
            case WEAR_ARMOR -> Txt.t(p, "Put the armour on", "Zieh die Rüstung an");
            case TALK_ANSEL_AGAIN -> Txt.t(p, "Talk to §cAnsel", "Sprich mit §cAnsel");
            case SOCKET -> Txt.t(p, "Put a booster into your sword §7(anvil)", "Setz einen Booster ins Schwert §7(Amboss)");
            case TALK_HILDE -> Txt.t(p, "Climb to §aHilde §fat the Old Watch", "Steig zu §aHilde §fan der Alten Wacht");
            case TALENT -> Txt.t(p, "Journal: put a §eTalent §finto the free slot", "Journal: Setz ein §eTalent §fin den freien Platz");
            case DRILL_PARRY -> Txt.t(p, "Parry the §eyellow §fjab: press §eF", "Pariere den §egelben §fStoß: drück §eF");
            case DRILL_ROLL -> Txt.t(p, "Roll through the §cred §fslam: §eSneak + Jump", "Rolle durch den §croten §fHieb: §eSchleichen + Springen");
            case DRILL_FINISHER -> Txt.t(p, "Land three fully charged hits", "Lande drei voll aufgeladene Treffer");
            case GO_FLAT -> Txt.t(p, "Go down to the Flat", "Geh hinunter aufs Watt");
            case BOSS -> Txt.t(p, "Get the harbour bell back", "Hol die Hafenglocke zurück");
            case TALK_WIM -> Txt.t(p, "Board the ferry: talk to §3Captain Wim", "An Bord: Sprich mit §3Kapitän Wim");
            case DEPART -> Txt.t(p, "Set sail", "Leinen los");
            case DONE -> "";
        };
    }

    /** 0..1 for steps that count, negative for steps that are about getting somewhere. */
    private float progress(Session s) {
        return switch (s.step) {
            case FELL_TREE -> Math.min(1f, s.a / (float) LOGS_GOAL);
            case MINE -> Math.min(1f, (Math.min(s.a, STONE_GOAL) + Math.min(s.b, COAL_GOAL)) / (float) (STONE_GOAL + COAL_GOAL));
            case CRAFT_ARMOR -> Integer.bitCount(s.a) / 4f;
            case DRILL_PARRY -> 0.05f;
            case DRILL_ROLL -> 0.38f;
            case DRILL_FINISHER -> 0.7f;
            default -> -1f;
        };
    }

    // ------------------------------------------------------------------ heartbeat (every 10 ticks)

    void heartbeat() {
        long tick = now();
        for (Player player : isle.world().getPlayers()) {
            Session s = of(player);
            if (s == null) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if (s.scene || s.step == Step.DONE) {
                continue;
            }
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                Guide.hide(player, s);
                continue;
            }
            isle.rules().patrol(player, s);
            if (s.step.ordinal() >= Step.TALK_WIM.ordinal() && tick % 60L < 10L) {
                hangBell(player);
            }
            watch(player, s, tick);
            Location target = targetOf(s);
            Location aim = Guide.lead(player, isle.marks(), target, tick);
            Guide.bar(player, s, s.step.icon, objective(player, s), progress(s), target, aim);
            if (target != null) {
                Guide.glow(player, target, s.step.talk() || (s.step == Step.TRY_IRON && s.a > 0) ? 2.35 : 1.3, tick);
            }
            hint(player, s, tick);
        }
    }

    /** Things a step notices by looking, not by being told. */
    private void watch(Player p, Session s, long tick) {
        switch (s.step) {
            case TALK_GRETT -> {
                Location overlook = isle.marks().at(isle.world(), "overlook");
                if (overlook != null && !s.seen.contains("overlook") && near(p, overlook, 4.5)) {
                    s.seen.add("overlook");
                    s.progress(tick);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 1.2f);
                    // a small card at the top: the view is the reward here, nothing may stand in front of it
                    Guide.note(p, Txt.t(p, "The Overlook", "Der Ausblick"),
                            List.of(Txt.t(p, "§7All of Morningbell: the forge with its wheel, the village, the harbour.",
                                            "§7Ganz Morningbell: die Schmiede mit dem Rad, das Dorf, der Hafen."),
                                    Txt.t(p, "§7The sail with the red bell is §fyour ferry§7.",
                                            "§7Das Segel mit der roten Glocke ist §fdeine Fähre§7.")),
                            Txt.t(p, "The stair beside you leads down to the quarry", "Die Treppe neben dir führt hinab zum Steinbruch"));
                }
            }
            case WEAR_ARMOR -> {
                if (armourWorn(p) >= 4) {
                    react(p, Cast.ANSEL, Txt.t(p, "Fits like it was made for you. It was. Come here, one more thing.",
                            "Sitzt wie für dich gemacht. Ist sie ja auch. Komm her, eins noch."));
                    taskDone(p, s, Step.TALK_ANSEL_AGAIN);
                }
            }
            case SOCKET -> {
                // nobody leaves the forge without boosters: the page opens by itself if the chip was never clicked
                if (!s.seen.contains("boosters") && tick - s.stepStarted > 20L * 25L && p.getOpenInventory().getTopInventory().getSize() <= 5
                        && s.seen.add("boosters-auto")) {
                    openBoosters(p, s);
                }
            }
            case DRILL_PARRY, DRILL_ROLL, DRILL_FINISHER -> {
                Location ring = isle.marks().at(isle.world(), "drill_center");
                if (ring != null && near(p, ring, 14.0) && !hasGolem(p) && tick - s.lastHint > 60L) {
                    s.lastHint = tick;
                    if (!spawnGolem(p)) {
                        Txt.actionBar(p, Txt.t(p, "§7Step into the ring: the golem needs open ground",
                                "§7Tritt in den Ring: Der Golem braucht freien Boden"));
                    }
                }
            }
            case GO_FLAT -> {
                Location arena = isle.marks().at(isle.world(), "arena");
                if (arena != null && near(p, arena, isle.marks().radius("arena", 13.0) + 1.0)) {
                    taskDone(p, s, Step.BOSS);
                }
            }
            default -> {
            }
        }
    }

    private void hint(Player p, Session s, long tick) {
        long wait = 20L * Math.max(15, plugin.getConfig().getInt("isle.hint-after", 45));
        if (tick - s.lastProgress < wait || tick - s.lastHint < wait) {
            return;
        }
        s.lastHint = tick;
        String line = hintLine(p, s);
        if (line == null) {
            return;
        }
        Cast.Member who = s.step.teacher == null ? null : Cast.ALL.get(s.step.teacher);
        String name = who == null ? "§bTolly" : who.color() + who.name();
        p.sendMessage(name + "§8: §f" + line);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    /** The step said once more, by the person who owns it, in plain words. */
    private String hintLine(Player p, Session s) {
        return switch (s.step) {
            case RING_BELL -> Txt.t(p, "The bell hangs right there, {player}. Right-click it.",
                    "Die Glocke hängt gleich da, {player}. Rechtsklick drauf.");
            case TALK_ODA -> Txt.t(p, "I'm by the red tower. Come and talk to me (right-click).",
                    "Ich stehe am roten Turm. Komm und sprich mich an (Rechtsklick).");
            case OPEN_JOURNAL -> Txt.t(p, "The book in your last slot: take it in your hand and right-click.",
                    "Das Buch in deinem letzten Platz: Nimm es in die Hand und mach Rechtsklick.");
            case TALK_BURR -> Txt.t(p, "Down the stone steps and along the lanterns. Follow the arrow at the top.",
                    "Die Steintreppe hinunter und den Laternen nach. Folge dem Pfeil oben.");
            case FELL_TREE -> Txt.t(p, "Axe in your hand, then hit a trunk until it falls. The gold spot is a double hit.",
                    "Axt in die Hand, dann auf einen Stamm schlagen, bis er fällt. Die goldene Stelle zählt doppelt.");
            case TRADE_BURR -> Txt.t(p, "Bring me the wood. Just talk to me.", "Bring mir das Holz. Sprich mich einfach an.");
            case CRAFT_PICKAXE -> Txt.t(p, "Right-click my workbench. Three coal in the top row, two sticks below the middle one.",
                    "Rechtsklick auf meine Werkbank. Drei Kohle in die obere Reihe, zwei Stöcke unter die mittlere.");
            case TALK_GRETT -> Txt.t(p, "Over the bridge by the waterfall, then down the stone stair. The arrow knows the way.",
                    "Über die Brücke am Wasserfall, dann die Steintreppe hinab. Der Pfeil kennt den Weg.");
            case MINE -> Txt.t(p, "Pickaxe in hand. Grey stone anywhere in the pit, coal has black spots (north wall).",
                    "Spitzhacke in die Hand. Grauer Stein überall in der Grube, Kohle hat schwarze Flecken (Nordwand).");
            case TRY_IRON -> s.a > 0
                    ? Txt.t(p, "Come over here, I'll tell you why it didn't break.", "Komm her, ich sag dir, warum es nicht gebrochen ist.")
                    : Txt.t(p, "The brown-spotted stone on the west wall. Just hit it once.",
                    "Der Stein mit den braunen Flecken an der Westwand. Schlag einfach einmal drauf.");
            case TALK_ANSEL -> Txt.t(p, "The house with the big water wheel. You can see it from the quarry.",
                    "Das Haus mit dem großen Wasserrad. Du siehst es vom Steinbruch aus.");
            case CRAFT_ARMOR -> Txt.t(p, "My workbench, right-click. Not sure about a shape? Journal, Recipe Book.",
                    "Meine Werkbank, Rechtsklick. Bei einer Form unsicher? Journal, Rezeptbuch.");
            case WEAR_ARMOR -> Txt.t(p, "Open your inventory (E) and put all four pieces on.",
                    "Öffne dein Inventar (E) und zieh alle vier Teile an.");
            case TALK_ANSEL_AGAIN -> Txt.t(p, "Come here, I have a blade for you.", "Komm her, ich hab eine Klinge für dich.");
            case SOCKET -> Txt.t(p, "Right-click the anvil. Sword on the seal, a booster into a slot below it.",
                    "Rechtsklick auf den Amboss. Schwert aufs Siegel, einen Booster in einen Platz darunter.");
            case TALK_HILDE -> Txt.t(p, "The broken tower on the hill east of the village. Up the path.",
                    "Der zerbrochene Turm auf dem Hügel östlich vom Dorf. Den Pfad hinauf.");
            case TALENT -> Txt.t(p, "Journal (right-click the book), then Talents. Click one, it goes into your free slot.",
                    "Journal (Rechtsklick aufs Buch), dann Talente. Klick eins an, es wandert in deinen freien Platz.");
            case DRILL_PARRY -> Txt.t(p, "Wait for the yellow '!' over the golem, then press F (swap hands).",
                    "Warte auf das gelbe '!' über dem Golem, dann drück F (Hand wechseln).");
            case DRILL_ROLL -> Txt.t(p, "Red '!!' means roll: hold Sneak and jump when it turns white.",
                    "Rotes '!!' heißt rollen: Schleichen halten und springen, wenn es weiß wird.");
            case DRILL_FINISHER -> Txt.t(p, "Don't hammer the button. Wait for the swing to recharge, three times.",
                    "Nicht auf die Taste hämmern. Warte, bis der Schlag wieder voll ist, dreimal.");
            case GO_FLAT -> Txt.t(p, "Down the path behind the tower, to the sand by the sea.",
                    "Den Pfad hinter dem Turm hinab, zum Sand am Meer.");
            case TALK_WIM -> Txt.t(p, "The pier, where the ship with the red bell lies. I'm waiting by the gangway.",
                    "Der Steg, wo das Schiff mit der roten Glocke liegt. Ich warte an der Planke.");
            default -> null;
        };
    }

    // ------------------------------------------------------------------ step changes

    /**
     * The player did what the step asked (not by talking). An old conversation that still has pages open is put
     * away first: the next click on that person must start what they say now, not turn a page of what they said
     * before the task.
     */
    private void taskDone(Player p, Session s, Step next) {
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            try {
                talk.end(p);
            } catch (RuntimeException ignored) {
                // no bubble was open
            }
        }
        enter(p, s, next);
    }

    /** Moves the player on to {@code step}: counters fresh, the store knows, the bar follows on the next beat. */
    void enter(Player p, Session s, Step step) {
        Step before = s.step;
        s.step = step;
        s.a = 0;
        s.b = 0;
        s.stepStarted = now();
        s.lastProgress = now();
        s.lastHint = now();
        Location rescue = targetOf(s);
        if (rescue != null) {
            s.rescue = rescue;
        }
        TutorialStore.Entry entry = plugin.store().get(p.getUniqueId());
        entry.step = step.name();
        entry.state = TutorialStore.State.IN_PROGRESS;
        remember(entry, s);
        plugin.store().save(p.getUniqueId(), entry);
        if (before != step) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.45f, 1.6f);
        }
        switch (step) {
            case CRAFT_ARMOR -> s.a = armourOwned(p);
            case DRILL_PARRY -> spawnGolem(p);
            case BOSS -> isle.boss().begin(p, s);
            case DEPART -> isle.scenes().depart(p, s);
            default -> {
            }
        }
    }

    /** Back on the isle in the middle of the lessons: nothing is replayed, missing tools are put back. */
    private void restore(Player p, Session s) {
        kit(p, s);
        Location rescue = targetOf(s);
        if (rescue != null) {
            s.rescue = rescue;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && of(p) == s) {
                p.sendMessage(Txt.t(p, "§e✦ §fBack on Morningbell. §7The bar at the top shows where you stopped.",
                        "§e✦ §fZurück auf Morningbell. §7Die Leiste oben zeigt, wo du aufgehört hast."));
            }
        }, 40L);
    }

    /**
     * What a player at this step must hold, put back when it is missing (a visitor's pockets are emptied at the
     * door; staff may have jumped here). Whoever still has their things gets nothing twice.
     */
    private void kit(Player p, Session s) {
        Step step = s.step;
        if (step.ordinal() >= Step.FELL_TREE.ordinal()) {
            ensure(p, "simple_axe");
        }
        if (step == Step.TRADE_BURR) {
            topUp(p, Material.OAK_LOG, LOGS_GOAL);
        }
        if (step == Step.CRAFT_PICKAXE) {
            topUp(p, Material.STICK, 2);
            topUp(p, Material.COAL, 3);
        }
        if (step.ordinal() > Step.CRAFT_PICKAXE.ordinal()) {
            ensure(p, "simple_pickaxe");
        }
        if (step == Step.TALK_ANSEL || step == Step.CRAFT_ARMOR) {
            topUp(p, Material.COBBLESTONE, cobbleStillNeeded(p));
        }
        if (step.ordinal() > Step.CRAFT_ARMOR.ordinal()) {
            wearStone(p, step.ordinal() > Step.WEAR_ARMOR.ordinal());
        }
        if (step.ordinal() > Step.TALK_ANSEL_AGAIN.ordinal()) {
            ensure(p, "simple_sword");
        }
        if (step.ordinal() > Step.SOCKET.ordinal()) {
            giveBoostersQuietly(p, s);
        }
        if (step == Step.CRAFT_ARMOR) {
            s.a = armourOwned(p);
        }
        ProgressAccess progress = AetherServices.progress();
        if (progress != null) {
            boolean changed = false;
            if (step.ordinal() > Step.TRADE_BURR.ordinal()) {
                changed |= progress.unlockSilent(p, "WORKBENCH");
            }
            if (step.ordinal() > Step.TALK_ANSEL_AGAIN.ordinal()) {
                changed |= progress.unlockSilent(p, "ANVIL");
            }
            if (step.ordinal() > Step.TALK_HILDE.ordinal()) {
                changed |= progress.unlockSilent(p, "SKILLS");
            }
            if (changed) {
                progress.refreshManager(p);
            }
        }
    }

    // ------------------------------------------------------------------ staff and visitors

    /** This player is on a visit (has a character elsewhere). */
    public boolean visiting(Player p) {
        Session s = of(p);
        return s != null && s.guest;
    }

    /** A visitor has seen enough: back to the main world, nothing taken along. */
    public void leave(Player p) {
        Session s = of(p);
        if (s == null || !s.guest || s.scene) {
            return;
        }
        isle.boss().abort(p);
        clearGolem(p);
        graduate(p, s);
        if (!plugin.toMain(p, Txt.t(p, "§e✦ §7Back to the mainland…", "§e✦ §7Zurück aufs Festland…"))) {
            stranded(p, s);
        }
    }

    /**
     * Staff and test rigs: do for the player what their own click would have done ("bell", "iron", or the id of a
     * cast member: "oda", "burr", ...). False for an unknown name.
     */
    public boolean poke(Player p, String what) {
        if (of(p) == null || what == null) {
            return false;
        }
        String key = what.toLowerCase(java.util.Locale.ROOT);
        switch (key) {
            case "bell" -> onBell(p);
            case "iron" -> onIron(p);
            case "boosters" -> boostersTaken(p);
            case "castoff" -> {
                Session s = of(p);
                if (s != null && s.step == Step.TALK_WIM && !s.scene) {
                    enter(p, s, Step.DEPART);
                }
            }
            default -> {
                if (!Cast.ALL.containsKey(key)) {
                    return false;
                }
                onNpc(p, key);
            }
        }
        return true;
    }

    /** One line for the staff status. Null when no lesson runs for this player. */
    public String describe(Player p) {
        Session s = of(p);
        if (s == null) {
            return null;
        }
        long seconds = (now() - s.stepStarted) / 20L;
        return "§f" + s.step.name() + " §7(" + s.a + "/" + s.b + "), " + seconds + " s in this step"
                + (s.guest ? ", visit" : "") + (s.scene ? ", in a scene" : "");
    }

    public List<String> stepNames() {
        List<String> names = new ArrayList<>();
        for (Step step : Step.values()) {
            if (step != Step.DONE && step != Step.DEPART && step != Step.BOSS) {
                names.add(step.name());
            }
        }
        return names;
    }

    /** Staff: put the player at {@code wanted} ("+" = the next step), with everything that step needs. */
    public String steer(Player p, String wanted) {
        Session s = of(p);
        if (s == null) {
            return "no lesson is running for this player";
        }
        Step step = "+".equals(wanted) ? s.step.next() : Step.parse(wanted);
        if (step == Step.BOSS) {
            step = Step.GO_FLAT;
        }
        if (step == Step.DEPART || step == Step.DONE) {
            step = Step.TALK_WIM;
        }
        if (step == null) {
            return "unknown step '" + wanted + "'";
        }
        jump(p, s, step);
        return "now at " + step.name();
    }

    private void jump(Player p, Session s, Step step) {
        isle.scenes().abort(p, s);
        isle.boss().abort(p);
        clearGolem(p);
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        s.morning = step.ordinal() > Step.RING_BELL.ordinal();
        isle.rules().applyTime(p, s);
        if (step == Step.ARRIVE) {
            s.step = Step.ARRIVE;
            s.seen.clear();
            TutorialStore.Entry entry = plugin.store().get(p.getUniqueId());
            entry.step = Step.ARRIVE.name();
            entry.flags.clear();
            plugin.store().save(p.getUniqueId(), entry);
            Location landing = isle.marks().at(isle.world(), "arrival");
            if (landing != null) {
                p.teleport(landing);
            }
            arrive(p, s);
            return;
        }
        enter(p, s, step);
        kit(p, s);
        Location target = targetOf(s);
        if (target != null) {
            Location spot = target.clone();
            if (step.talk()) {
                // three steps in front of whoever waits there, looking at them (and at the bubble over their head)
                spot.add(target.getDirection().setY(0).normalize().multiply(3.2));
                spot.setYaw(target.getYaw() + 180f);
                spot.setPitch(-10f);
            } else {
                spot.setPitch(0f);
            }
            p.teleport(spot);
        }
    }

    // ------------------------------------------------------------------ the first minute

    private void arrive(Player p, Session s) {
        isle.scenes().arrive(p, s, () -> {
            if (!p.isOnline() || of(p) != s) {
                return;
            }
            enter(p, s, Step.RING_BELL);
            react(p, Cast.TOLLY, Txt.t(p, "Psst, {player}! Over here. First morning? Then you ring the bell. Everybody does.",
                    "Psst, {player}! Hier drüben. Erster Morgen? Dann läutest du die Glocke. Das macht jeder."));
        });
    }

    /** The bell on the Landing was rung. */
    void onBell(Player p) {
        Session s = of(p);
        if (s == null || s.scene) {
            return;
        }
        if (s.step != Step.RING_BELL) {
            return;
        }
        s.progress(now());
        isle.scenes().sunrise(p, s, () -> {
            if (!p.isOnline() || of(p) != s) {
                return;
            }
            enter(p, s, Step.TALK_ODA);
        });
    }

    // ------------------------------------------------------------------ the cast is clicked

    void onNpc(Player p, String id) {
        Session s = of(p);
        if (s == null || s.scene) {
            return;
        }
        TalkAccess talk = AetherServices.talk();
        Cast.Member member = Cast.ALL.get(id);
        try {
            // a card still up would hold the answer back: talking to somebody puts it away
            de.aetherion.quests.talk.HudCards.dismiss(p);
        } catch (LinkageError | RuntimeException ignored) {
            // no cards on this server
        }
        if (member != null && talk != null) {
            try {
                if (talk.recentlyPicked(p)) {
                    return;
                }
                // paged talk: a click on the speaker turns the page
                if (de.aetherion.quests.api.Cards.advance(p, member.speaker())) {
                    return;
                }
                if (talk.hasReplies(p, member.speaker())) {
                    talk.pulse(p);
                    return;
                }
            } catch (LinkageError | RuntimeException ignored) {
                // an older Quests build: every click simply starts the beat
            }
        }
        s.progress(now());
        switch (id) {
            case Cast.TOLLY -> tolly(p, s);
            case Cast.ODA -> oda(p, s);
            case Cast.BURR -> burr(p, s);
            case Cast.GRETT -> grett(p, s);
            case Cast.ANSEL -> ansel(p, s);
            case Cast.HILDE -> hilde(p, s);
            case Cast.WIM -> wim(p, s);
            default -> {
                if (member != null && !member.teacher()) {
                    smallTalk(p, s, member);
                }
            }
        }
    }

    /** An islander without a lesson: one thought at a time, the next one on the next click. */
    private void smallTalk(Player p, Session s, Cast.Member member) {
        String[][] talk = member.talk();
        if (talk == null || talk.length == 0) {
            return;
        }
        int turn = s.chats.merge(member.id(), 1, Integer::sum) - 1;
        String[] pair = talk[turn % talk.length];
        say(p, member.id(), List.of(Txt.t(p, pair[0], pair.length > 1 ? pair[1] : pair[0])), List.of());
    }

    private void tolly(Player p, Session s) {
        if (s.step == Step.RING_BELL) {
            say(p, Cast.TOLLY, List.of(Txt.t(p, "Go on, {player}. The bell, right there. Give the rope a pull.",
                    "Na los, {player}. Die Glocke, gleich da. Zieh am Seil.")), List.of());
            return;
        }
        say(p, Cast.TOLLY, List.of(Txt.t(p, "You rang it! Now the island wakes for you. The bar at the top says what is next, the lanterns show the way.",
                "Du hast geläutet! Jetzt wacht die Insel für dich auf. Die Leiste oben sagt, was kommt, die Laternen zeigen den Weg.")), List.of());
    }

    private void oda(Player p, Session s) {
        if (s.step == Step.RING_BELL || s.step == Step.ARRIVE) {
            say(p, Cast.ODA, List.of(Txt.t(p, "The bell first, child. Then we talk.", "Erst die Glocke, Kind. Dann reden wir.")), List.of());
            return;
        }
        if (s.step == Step.TALK_ODA) {
            enter(p, s, Step.OPEN_JOURNAL);
            List<TalkAccess.Reply> chips = new ArrayList<>();
            if (s.returning) {
                String label = Txt.t(p, "I've been here before.", "Ich war schon mal hier.");
                chips.add(new TalkAccess.Reply(label, NamedTextColor.GRAY, () -> offerSkip(p, s), label));
            }
            say(p, Cast.ODA, List.of(
                    Txt.t(p, "There she rings. Good morning, {player}. I'm Oda, I keep the bell.",
                            "Da läutet sie. Guten Morgen, {player}. Ich bin Oda, ich hüte die Glocke."),
                    Txt.t(p, "Nobody sails to the mainland unprepared. So the island teaches you first, one thing at a time.",
                            "Niemand segelt unvorbereitet zum Festland. Darum bringt dir die Insel erst alles bei, eins nach dem anderen."),
                    Txt.t(p, "All you learn is written in your Journal. It is the book in your last slot.",
                            "Alles, was du lernst, steht in deinem Journal. Das ist das Buch in deinem letzten Platz."),
                    Txt.t(p, "Open it once: take the book in your hand and right-click.",
                            "Schlag es einmal auf: Nimm das Buch in die Hand und mach Rechtsklick.")), chips);
            return;
        }
        if (s.step == Step.OPEN_JOURNAL) {
            say(p, Cast.ODA, List.of(Txt.t(p, "The book in your last slot, {player}. In your hand, then right-click.",
                    "Das Buch in deinem letzten Platz, {player}. In die Hand, dann Rechtsklick.")), List.of());
            return;
        }
        say(p, Cast.ODA, List.of(Txt.t(p, "The lanterns lead you, the bar at the top tells you what is next. Go well.",
                "Die Laternen führen dich, die Leiste oben sagt dir, was als Nächstes kommt. Geh gut.")), List.of());
    }

    private void burr(Player p, Session s) {
        switch (s.step) {
            case TALK_BURR -> {
                ensure(p, "simple_axe");
                enter(p, s, Step.FELL_TREE);
                say(p, Cast.BURR, List.of(
                        Txt.t(p, "So you're the new one. Burr. I burn charcoal, and for that I need wood.",
                                "Du bist also neu hier. Burr. Ich brenne Holzkohle, und dafür brauche ich Holz."),
                        Txt.t(p, "Take my axe. Hit a trunk until it falls. When a gold spot shows, hit that: it counts double.",
                                "Nimm meine Axt. Schlag auf einen Stamm, bis er fällt. Zeigt sich eine goldene Stelle, triff die: Sie zählt doppelt."),
                        Txt.t(p, "Fell one of the oaks behind me. Then bring me the wood.",
                                "Fäll eine der Eichen hinter mir. Dann bring mir das Holz.")), List.of());
            }
            case FELL_TREE -> {
                ensure(p, "simple_axe");
                say(p, Cast.BURR, List.of(Txt.t(p, "Axe in your hand, hit the trunk, keep hitting. The oaks behind me.",
                        "Axt in die Hand, auf den Stamm schlagen, weiterschlagen. Die Eichen hinter mir.")), List.of());
            }
            case TRADE_BURR -> {
                take(p, Material.OAK_LOG, 4);
                give(p, new ItemStack(Material.STICK, 2));
                give(p, new ItemStack(Material.COAL, 3));
                unlock(p, "WORKBENCH", Txt.t(p, "Workbench", "Werkbank"), Txt.t(p, "You can craft now", "Du kannst jetzt bauen"));
                enter(p, s, Step.CRAFT_PICKAXE);
                say(p, Cast.BURR, List.of(
                        Txt.t(p, "Good wood. That goes into the kiln. You get something better for it.",
                                "Gutes Holz. Das kommt in den Meiler. Du bekommst was Besseres dafür."),
                        Txt.t(p, "Two sticks and three lumps of coal. Together that is a pickaxe.",
                                "Zwei Stöcke und drei Stück Kohle. Zusammen ist das eine Spitzhacke."),
                        Txt.t(p, "My workbench stands under the roof there. Coal across the top, the sticks below the middle.",
                                "Meine Werkbank steht dort unterm Dach. Kohle oben quer, die Stöcke unter die Mitte.")), List.of());
            }
            case CRAFT_PICKAXE -> {
                topUp(p, Material.STICK, 2);
                topUp(p, Material.COAL, 3);
                say(p, Cast.BURR, List.of(Txt.t(p, "The workbench under the roof. Three coal in the top row, two sticks below the middle one.",
                        "Die Werkbank unterm Dach. Drei Kohle in die obere Reihe, zwei Stöcke unter die mittlere.")), List.of());
            }
            default -> say(p, Cast.BURR, List.of(Txt.t(p, "The kiln wants watching. Off you go, {player}.",
                    "Der Meiler will bewacht werden. Ab mit dir, {player}.")), List.of());
        }
    }

    private void grett(Player p, Session s) {
        switch (s.step) {
            case TALK_GRETT -> {
                ensure(p, "simple_pickaxe");
                enter(p, s, Step.MINE);
                say(p, Cast.GRETT, List.of(
                        Txt.t(p, "A brand-new pickaxe! Let's see what it can do. I'm Grett.",
                                "Eine nagelneue Spitzhacke! Mal sehen, was sie kann. Ich bin Grett."),
                        Txt.t(p, "The grey stone in this pit breaks anywhere. It grows back. Don't ask me how.",
                                "Der graue Stein in dieser Grube bricht überall. Er wächst nach. Frag mich nicht, wie."),
                        Txt.t(p, "The stone with black spots is coal. Your pickaxe is strong enough for it.",
                                "Der Stein mit den schwarzen Flecken ist Kohle. Dafür ist deine Spitzhacke stark genug."),
                        Txt.t(p, "Get twelve stone and five coal.", "Hol zwölf Stein und fünf Kohle.")), List.of());
            }
            case MINE -> {
                ensure(p, "simple_pickaxe");
                say(p, Cast.GRETT, List.of(Txt.t(p, "Twelve stone, five coal. Coal is the black-spotted stone on the north wall.",
                        "Zwölf Stein, fünf Kohle. Kohle ist der Stein mit den schwarzen Flecken an der Nordwand.")), List.of());
            }
            case TRY_IRON -> {
                if (s.a == 0) {
                    say(p, Cast.GRETT, List.of(Txt.t(p, "The brown-spotted stone on the west wall. Give it a go.",
                            "Der Stein mit den braunen Flecken an der Westwand. Versuch es.")), List.of());
                    return;
                }
                topUp(p, Material.COBBLESTONE, COBBLE_FOR_ARMOUR);
                enter(p, s, Step.TALK_ANSEL);
                say(p, Cast.GRETT, List.of(
                        Txt.t(p, "Too hard, eh? Every ore needs Mining Power. Coal needs 8, iron needs 24. Your pickaxe has 14.",
                                "Zu hart, was? Jedes Erz braucht Mining Power. Kohle braucht 8, Eisen 24. Deine Spitzhacke hat 14."),
                        Txt.t(p, "A better pickaxe has more. On the mainland you build one from this one and eight coal. So keep your coal.",
                                "Eine bessere Spitzhacke hat mehr. Auf dem Festland baust du eine aus dieser hier und acht Kohle. Heb deine Kohle also auf."),
                        Txt.t(p, "I've filled your stone up to two dozen. Carry it to Ansel at the forge: he makes armour of it.",
                                "Ich hab deinen Stein auf zwei Dutzend aufgefüllt. Bring ihn zu Ansel in die Schmiede: Der macht Rüstung daraus.")), List.of());
            }
            default -> say(p, Cast.GRETT, List.of(Txt.t(p, "The pit is yours whenever you like. Stone grows back.",
                    "Die Grube gehört dir, wann immer du willst. Stein wächst nach.")), List.of());
        }
    }

    private void ansel(Player p, Session s) {
        switch (s.step) {
            case TALK_ANSEL -> {
                topUp(p, Material.COBBLESTONE, COBBLE_FOR_ARMOUR);
                unlock(p, "WORKBENCH", Txt.t(p, "Workbench", "Werkbank"), Txt.t(p, "You can craft now", "Du kannst jetzt bauen"));
                enter(p, s, Step.CRAFT_ARMOR);
                say(p, Cast.ANSEL, List.of(
                        Txt.t(p, "Grett's stone? Good. I'm Ansel. Stone makes the first real armour.",
                                "Gretts Stein? Gut. Ich bin Ansel. Aus Stein wird die erste richtige Rüstung."),
                        Txt.t(p, "Helmet, chestplate, leggings, boots. The shapes are the old ones you may know.",
                                "Helm, Brustplatte, Hose, Stiefel. Die Formen sind die alten, die du vielleicht kennst."),
                        Txt.t(p, "My workbench is yours. Forgot a shape? Your Journal has a Recipe Book that builds it with one click.",
                                "Meine Werkbank gehört dir. Form vergessen? In deinem Journal ist ein Rezeptbuch, das sie mit einem Klick baut.")), List.of());
            }
            case CRAFT_ARMOR -> {
                int missing = 4 - Integer.bitCount(armourOwned(p));
                if (missing > 0) {
                    topUp(p, Material.COBBLESTONE, cobbleStillNeeded(p));
                }
                say(p, Cast.ANSEL, List.of(Txt.t(p, "Four pieces at my workbench. The Recipe Book in your Journal shows each shape.",
                        "Vier Teile an meiner Werkbank. Das Rezeptbuch in deinem Journal zeigt jede Form.")), List.of());
            }
            case WEAR_ARMOR -> say(p, Cast.ANSEL, List.of(Txt.t(p, "Don't carry it, wear it! Inventory, then all four pieces on.",
                    "Nicht tragen, anziehen! Inventar, dann alle vier Teile an.")), List.of());
            case TALK_ANSEL_AGAIN -> {
                ensure(p, "simple_sword");
                unlock(p, "ANVIL", Txt.t(p, "Anvil", "Amboss"), Txt.t(p, "Boosters go into gear here", "Hier kommen Booster in die Ausrüstung"));
                enter(p, s, Step.SOCKET);
                String label = Txt.t(p, "Show me the boosters.", "Zeig mir die Booster.");
                say(p, Cast.ANSEL, List.of(
                        Txt.t(p, "Armour on. Now a blade: here, a Simple Sword.",
                                "Rüstung sitzt. Jetzt eine Klinge: hier, ein Einfaches Schwert."),
                        Txt.t(p, "And boosters. One of each kind, a gift. They make gear better at what it does.",
                                "Und Booster. Von jeder Sorte einer, geschenkt. Sie machen Ausrüstung besser in dem, was sie kann."),
                        Txt.t(p, "Put one into your sword at my anvil: sword on the seal, booster into a slot below.",
                                "Setz einen am Amboss in dein Schwert: Schwert aufs Siegel, Booster in einen Platz darunter.")),
                        List.of(new TalkAccess.Reply(label, NamedTextColor.GOLD, () -> openBoosters(p, s), label)));
            }
            case SOCKET -> {
                if (!s.seen.contains("boosters")) {
                    openBoosters(p, s);
                    return;
                }
                ensure(p, "simple_sword");
                say(p, Cast.ANSEL, List.of(Txt.t(p, "The anvil behind me. Sword on the seal, a booster into a slot below it.",
                        "Der Amboss hinter mir. Schwert aufs Siegel, einen Booster in einen Platz darunter.")), List.of());
            }
            default -> say(p, Cast.ANSEL, List.of(Txt.t(p, "The anvil is always here. Boosters come out again with one click.",
                    "Der Amboss ist immer hier. Booster kommen mit einem Klick wieder raus.")), List.of());
        }
    }

    private void hilde(Player p, Session s) {
        switch (s.step) {
            case TALK_HILDE -> {
                unlock(p, "SKILLS", Txt.t(p, "Talents", "Talente"), Txt.t(p, "A page in your Journal", "Eine Seite in deinem Journal"));
                enter(p, s, Step.TALENT);
                say(p, Cast.HILDE, List.of(
                        Txt.t(p, "Recruit {player}. Armour on, blade at hand. Good. I'm Hilde.",
                                "Rekrut {player}. Rüstung an, Klinge zur Hand. Gut. Ich bin Hilde."),
                        Txt.t(p, "Before we fight: talents. Small tricks you choose for yourself. More open as your skills level up.",
                                "Bevor wir kämpfen: Talente. Kleine Kniffe, die du dir selbst aussuchst. Mit jedem Skill-Level werden es mehr."),
                        Txt.t(p, "Open your Journal, go to Talents, and put one into your free slot.",
                                "Öffne dein Journal, geh zu Talente und setz eins in deinen freien Platz.")), List.of());
            }
            case TALENT -> say(p, Cast.HILDE, List.of(Txt.t(p, "Journal, Talents, pick one. It goes into your free slot.",
                    "Journal, Talente, eins aussuchen. Es wandert in deinen freien Platz.")), List.of());
            case DRILL_PARRY -> {
                if (!hasGolem(p)) {
                    spawnGolem(p);
                }
                say(p, Cast.HILDE, List.of(Txt.t(p, "Yellow '!' over the golem means a jab. Press F at that moment and you turn it aside.",
                        "Gelbes '!' über dem Golem heißt Stoß. Drück in dem Moment F, und du lenkst ihn ab.")), List.of());
            }
            case DRILL_ROLL -> {
                if (!hasGolem(p)) {
                    spawnGolem(p);
                }
                say(p, Cast.HILDE, List.of(Txt.t(p, "Red '!!' cannot be parried. Hold Sneak and jump when it turns white: you roll through.",
                        "Rotes '!!' lässt sich nicht parieren. Halte Schleichen und spring, wenn es weiß wird: Du rollst hindurch.")), List.of());
            }
            case DRILL_FINISHER -> {
                if (!hasGolem(p)) {
                    spawnGolem(p);
                }
                say(p, Cast.HILDE, List.of(Txt.t(p, "Three hits, each with a full swing. Wait for the blade between them.",
                        "Drei Treffer, jeder mit vollem Schwung. Warte zwischen ihnen auf die Klinge.")), List.of());
            }
            case GO_FLAT, BOSS -> say(p, Cast.HILDE, List.of(Txt.t(p, "The Flat, recruit. Down the path behind my tower. Bring that bell home.",
                    "Das Watt, Rekrut. Den Pfad hinter meinem Turm hinab. Bring die Glocke heim.")), List.of());
            default -> say(p, Cast.HILDE, List.of(Txt.t(p, "Not yet, {player}. Come back with armour and a blade.",
                    "Noch nicht, {player}. Komm mit Rüstung und Klinge wieder.")), List.of());
        }
    }

    private void wim(Player p, Session s) {
        if (s.step != Step.TALK_WIM) {
            say(p, Cast.WIM, List.of(Txt.t(p, "We sail when you're ready, and not a lesson sooner. Nobody sails to the mainland unprepared.",
                    "Wir segeln, wenn du so weit bist, und keine Lektion früher. Niemand segelt unvorbereitet zum Festland.")), List.of());
            return;
        }
        ensure(p, "simple_hoe");
        String go = Txt.t(p, "Cast off!", "Leinen los!");
        String stay = Txt.t(p, "One more look around.", "Ich schau mich noch um.");
        say(p, Cast.WIM, List.of(
                Txt.t(p, "So you're the one who got my bell back! Captain Wim.",
                        "Du hast also meine Glocke zurückgeholt! Kapitän Wim."),
                Txt.t(p, "Journal, axe, pickaxe, armour, boosters, a talent, and you can fight. That is what I call prepared.",
                        "Journal, Axt, Spitzhacke, Rüstung, Booster, ein Talent, und kämpfen kannst du auch. Das nenne ich vorbereitet."),
                Txt.t(p, "Here, a hoe for the fields over there. The mainland has work for every bit of it.",
                        "Hier, eine Hacke für die Felder drüben. Auf dem Festland gibt es für alles davon Arbeit."),
                Txt.t(p, "Ready? Once we cast off, the island stays behind.",
                        "Bereit? Wenn wir ablegen, bleibt die Insel zurück.")),
                List.of(new TalkAccess.Reply(go, NamedTextColor.GREEN, () -> {
                    if (of(p) == s && s.step == Step.TALK_WIM) {
                        enter(p, s, Step.DEPART);
                    }
                }, go), new TalkAccess.Reply(stay, NamedTextColor.GRAY, () -> say(p, Cast.WIM, List.of(Txt.t(p,
                        "Take your time. I'm not going anywhere without you.", "Lass dir Zeit. Ohne dich fahre ich nirgendwohin.")), List.of()), stay)));
    }

    // ------------------------------------------------------------------ the short way for those who know it

    private void offerSkip(Player p, Session s) {
        String yes = Txt.t(p, "Straight to the ferry.", "Direkt zur Fähre.");
        String no = Txt.t(p, "I'll walk it again.", "Ich laufe es noch mal.");
        say(p, Cast.ODA, List.of(Txt.t(p, "So you have rung this bell before. Then the island need not hold you: the ferry takes you as you are. Or walk it once more.",
                        "Du hast diese Glocke also schon einmal geläutet. Dann muss die Insel dich nicht aufhalten: Die Fähre nimmt dich, wie du bist. Oder lauf den Weg noch einmal.")),
                List.of(new TalkAccess.Reply(yes, NamedTextColor.GOLD, () -> skip(p, s), yes),
                        new TalkAccess.Reply(no, NamedTextColor.GRAY, () -> say(p, Cast.ODA, List.of(Txt.t(p,
                                "Then open your Journal, and follow the lanterns.", "Dann schlag dein Journal auf und folge den Laternen.")), List.of()), no)));
    }

    /** A returning player takes the short way: everything the lessons hand out, then the pier. */
    void skip(Player p, Session s) {
        if (of(p) != s) {
            return;
        }
        s.skipping = true;
        for (String id : new String[] {"simple_axe", "simple_pickaxe", "simple_sword", "simple_hoe", "simple_helmet",
                "simple_chestplate", "simple_leggings", "simple_boots"}) {
            ensure(p, id);
        }
        ProgressAccess progress = AetherServices.progress();
        if (progress != null) {
            for (String flag : new String[] {"WORKBENCH", "ANVIL", "SKILLS"}) {
                progress.unlockSilent(p, flag);
            }
            progress.refreshManager(p);
        }
        giveBoostersQuietly(p, s);
        TutorialStore.Entry entry = plugin.store().get(p.getUniqueId());
        entry.skipped = true;
        plugin.store().save(p.getUniqueId(), entry);
        enter(p, s, Step.TALK_WIM);
        Location pier = isle.marks().at(isle.world(), "pier_head");
        if (pier != null) {
            p.teleport(pier);
        }
        Guide.toast(p, Txt.t(p, "The short way", "Der kurze Weg"),
                Txt.t(p, "§7Your kit is packed. Captain Wim is waiting.", "§7Deine Ausrüstung ist gepackt. Kapitän Wim wartet."), null);
    }

    // ------------------------------------------------------------------ what other plugins report

    void onUsed(Player p, String target) {
        Session s = of(p);
        if (s == null || target == null) {
            return;
        }
        switch (target.toUpperCase(java.util.Locale.ROOT)) {
            case "AETHERION_MANAGER" -> {
                if (s.step == Step.OPEN_JOURNAL) {
                    s.progress(now());
                    taskDone(p, s, Step.TALK_BURR);
                    Guide.toast(p, Txt.t(p, "Journal", "Journal"),
                            Txt.t(p, "§7Everything you learn is in here.", "§7Hier steht alles, was du lernst."),
                            Txt.t(p, "Close it and follow the lanterns down to Burr", "Schließ es und folge den Laternen hinab zu Burr"));
                }
            }
            case "APPLY_BOOSTER" -> {
                if (s.step == Step.SOCKET) {
                    s.progress(now());
                    taskDone(p, s, Step.TALK_HILDE);
                    react(p, Cast.ANSEL, Txt.t(p, "Now that sword has a bite. Hilde at the Old Watch shows you how to use it: the broken tower on the hill.",
                            "Jetzt hat das Schwert Biss. Hilde an der Alten Wacht zeigt dir, wie man es führt: der zerbrochene Turm auf dem Hügel."));
                    Guide.card(p, Txt.t(p, "LEARNT", "GELERNT"), Txt.t(p, "Boosters", "Booster"),
                            List.of(Txt.t(p, "§7At an anvil: gear on the seal, booster into a slot.", "§7Am Amboss: Teil aufs Siegel, Booster in einen Platz."),
                                    Txt.t(p, "§7They come out again with one click.", "§7Mit einem Klick kommen sie wieder raus.")),
                            null, Txt.t(p, "Next: Hilde at the Old Watch", "Als Nächstes: Hilde an der Alten Wacht"), "§c");
                }
            }
            case "AETHER_SKILL" -> {
                if (s.step == Step.TALENT) {
                    s.progress(now());
                    taskDone(p, s, Step.DRILL_PARRY);
                    react(p, Cast.HILDE, Txt.t(p, "Good pick. Now into the ring: the golem jabs with a yellow '!'. Press F right then.",
                            "Gute Wahl. Jetzt in den Ring: Der Golem stößt mit einem gelben '!'. Drück genau dann F."));
                }
            }
            case "COMBAT_PARRY" -> {
                if (s.step == Step.DRILL_PARRY) {
                    s.progress(now());
                    taskDone(p, s, Step.DRILL_ROLL);
                    react(p, Cast.HILDE, Txt.t(p, "Clean! Now the red '!!'. No blocking that one: hold Sneak and jump when it turns white.",
                            "Sauber! Jetzt das rote '!!'. Das blockst du nicht: Halte Schleichen und spring, wenn es weiß wird."));
                }
            }
            case "COMBAT_DODGE" -> {
                if (s.step == Step.DRILL_ROLL) {
                    s.progress(now());
                    taskDone(p, s, Step.DRILL_FINISHER);
                    react(p, Cast.HILDE, Txt.t(p, "Through! Last one: three hits, each with a full swing. Don't hammer, wait for the blade.",
                            "Durch! Das Letzte: drei Treffer, jeder mit vollem Schwung. Nicht hämmern, warte auf die Klinge."));
                }
            }
            case "COMBAT_FINISHER" -> {
                if (s.step == Step.DRILL_FINISHER) {
                    s.progress(now());
                    clearGolem(p);
                    taskDone(p, s, Step.GO_FLAT);
                    react(p, Cast.HILDE, Txt.t(p, "That is how you fight here. One thing left, recruit: the harbour bell is gone. Something on the Flat is wearing it.",
                            "So kämpft man hier. Eins noch, Rekrut: Die Hafenglocke ist weg. Unten im Watt trägt sie jemand spazieren."));
                    Guide.card(p, Txt.t(p, "LEARNT", "GELERNT"), Txt.t(p, "How you fight here", "So kämpfst du hier"),
                            List.of(Txt.t(p, "§eYellow !§7: parry with §fF", "§eGelbes !§7: parieren mit §fF"),
                                    Txt.t(p, "§cRed !!§7: roll with §fSneak + Jump", "§cRotes !!§7: rollen mit §fSchleichen + Springen"),
                                    Txt.t(p, "§7Full swings hit harder than fast ones", "§7Volle Schläge treffen härter als schnelle")),
                            null, Txt.t(p, "Next: down to the Flat", "Als Nächstes: hinunter aufs Watt"), "§a");
                }
            }
            default -> {
            }
        }
    }

    void onCrafted(Player p, String target, int amount) {
        Session s = of(p);
        if (s == null || target == null) {
            return;
        }
        String id = target.toLowerCase(java.util.Locale.ROOT);
        if (s.step == Step.CRAFT_PICKAXE && id.equals("simple_pickaxe")) {
            s.progress(now());
            taskDone(p, s, Step.TALK_GRETT);
            react(p, Cast.BURR, Txt.t(p, "Not bad for a first one! Grett at the quarry will put it to work: over the bridge by the waterfall, down the stair.",
                    "Nicht schlecht fürs erste Mal! Grett im Steinbruch gibt ihr Arbeit: über die Brücke am Wasserfall, die Treppe hinab."));
            Guide.card(p, Txt.t(p, "LEARNT", "GELERNT"), Txt.t(p, "Crafting", "Bauen"),
                    List.of(Txt.t(p, "§7Any workbench opens the crafting grid.", "§7Jede Werkbank öffnet das Baufeld."),
                            Txt.t(p, "§7The Recipe Book in your Journal knows every shape.", "§7Das Rezeptbuch im Journal kennt jede Form.")),
                    null, Txt.t(p, "Next: Grett at the quarry", "Als Nächstes: Grett im Steinbruch"), "§6");
            return;
        }
        if (s.step == Step.CRAFT_ARMOR) {
            for (int i = 0; i < ARMOUR.length; i++) {
                if (ARMOUR[i].equals(id)) {
                    s.a |= 1 << i;
                    s.progress(now());
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.35f, 1.5f);
                }
            }
            if (Integer.bitCount(s.a) >= 4) {
                taskDone(p, s, Step.WEAR_ARMOR);
                react(p, Cast.ANSEL, Txt.t(p, "A full set! Don't just carry it: put it on.", "Ein ganzer Satz! Trag ihn nicht nur herum: Zieh ihn an."));
            }
        }
    }

    void onBroken(Player p, Material material, int amount) {
        Session s = of(p);
        if (s == null || material == null) {
            return;
        }
        String name = material.name();
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")) {
            if (s.seen.add("skills-card")) {
                // the first skill XP of this character: say what just happened
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && of(p) == s) {
                        Guide.card(p, Txt.t(p, "LEARNT", "GELERNT"), Txt.t(p, "Skills", "Skills"),
                                List.of(Txt.t(p, "§7Everything you do trains its own skill:", "§7Alles, was du tust, trainiert seinen eigenen Skill:"),
                                        Txt.t(p, "§fwood §7→ Foraging, §fstone §7→ Mining, §ffights §7→ Combat.", "§fHolz §7→ Holzsammeln, §fStein §7→ Bergbau, §fKämpfe §7→ Kampf."),
                                        Txt.t(p, "§7Levels make you stronger and open new talents.", "§7Level machen dich stärker und schalten neue Talente frei.")),
                                null, null, "§2");
                    }
                }, 50L);
            }
            if (s.step == Step.FELL_TREE) {
                s.a += Math.max(1, amount);
                s.progress(now());
                if (s.a >= LOGS_GOAL) {
                    taskDone(p, s, Step.TRADE_BURR);
                    react(p, Cast.BURR, Txt.t(p, "That's a tree down! Bring the wood over here.", "Der liegt! Bring das Holz zu mir rüber."));
                }
            }
            return;
        }
        if (s.step == Step.MINE) {
            if (material == Material.STONE) {
                s.a += Math.max(1, amount);
                s.progress(now());
            } else if (material == Material.COAL_ORE) {
                s.b += Math.max(1, amount);
                s.progress(now());
            }
            if (s.a >= STONE_GOAL && s.b >= COAL_GOAL) {
                taskDone(p, s, Step.TRY_IRON);
                react(p, Cast.GRETT, Txt.t(p, "A good haul! Now try the brown-spotted stone on the west wall. That's iron.",
                        "Ordentliche Ausbeute! Jetzt versuch den Stein mit den braunen Flecken an der Westwand. Das ist Eisen."));
            }
        }
    }

    /** The player swung at iron ore (the first pickaxe is too weak for it: that is the lesson). */
    void onIron(Player p) {
        Session s = of(p);
        if (s == null || s.step != Step.TRY_IRON || s.a > 0) {
            return;
        }
        s.a = 1;
        s.progress(now());
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 0.7f);
        react(p, Cast.GRETT, Txt.t(p, "Ha! Didn't budge, did it? Come here, I'll tell you why.",
                "Ha! Hat sich nicht gerührt, was? Komm her, ich sag dir, warum."));
    }

    /** The boss is beaten (or was stood in for): on to the ferry. */
    void bossDone(Player p, Session s) {
        if (of(p) != s) {
            return;
        }
        taskDone(p, s, Step.TALK_WIM);
    }

    /** The player walked off the Flat in the middle of it: the bell waits there for them. */
    void retreat(Player p, Session s) {
        if (of(p) == s && s.step == Step.BOSS) {
            enter(p, s, Step.GO_FLAT);
        }
    }

    /**
     * Once Old Clanger has carried it home, the harbour bell hangs in its frame again. For this player only (the
     * next newcomer still finds the frame empty), so it is shown again whenever they come near.
     */
    private void hangBell(Player p) {
        Location post = isle.marks().block(isle.world(), "bell_post");
        if (post == null || post.getWorld() != p.getWorld() || post.distanceSquared(p.getLocation()) > 80.0 * 80.0) {
            return;
        }
        try {
            p.sendBlockChange(post, Bukkit.createBlockData("minecraft:bell[attachment=ceiling,facing=north]"));
        } catch (IllegalArgumentException ignored) {
            // a server that names the bell's states differently: the frame stays as built
        }
    }

    // ------------------------------------------------------------------ boosters

    private void openBoosters(Player p, Session s) {
        if (!s.seen.add("boosters")) {
            return;
        }
        saveFacts(p, s);
        try {
            if (!isle.boosters().open(p)) {
                plugin.getLogger().warning("No boosters to hand out: is AetherionItems running?");
            }
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Booster page could not open: " + ex);
        }
    }

    private void saveFacts(Player p, Session s) {
        TutorialStore.Entry entry = plugin.store().get(p.getUniqueId());
        remember(entry, s);
        plugin.store().save(p.getUniqueId(), entry);
    }

    /** The page closed and the twelve are in the bag: Ansel points at the anvil. */
    void boostersTaken(Player p) {
        Session s = of(p);
        if (s == null || s.step != Step.SOCKET) {
            return;
        }
        s.progress(now());
        react(p, Cast.ANSEL, Txt.t(p, "All twelve are yours. Now the anvil behind me: sword on the seal, one booster into a slot below.",
                "Alle zwölf gehören dir. Jetzt der Amboss hinter mir: Schwert aufs Siegel, einen Booster in einen Platz darunter."));
    }

    /** The short way: the set without the page. */
    private void giveBoostersQuietly(Player p, Session s) {
        if (!s.seen.add("boosters")) {
            return;
        }
        saveFacts(p, s);
        try {
            isle.boosters().give(p);
        } catch (LinkageError | RuntimeException ignored) {
            // without Items there is no booster set to hand out
        }
    }

    // ------------------------------------------------------------------ the ferry leaves

    private static final String[] SPINE = {"welcome_aboard", "gather_wood", "lesson_manager", "lesson_boost", Notes.DRILL_QUEST};

    /**
     * The ferry has cast off. For a newcomer the island now stands for the harbour's old orientation: its lessons
     * are filed as done, and the stamp that kept the harbour's welcome quiet here is lifted, so Miss Ledger's own
     * stamp is the first thing the mainland gives them. A visitor leaves nothing behind.
     */
    void graduate(Player p, Session s) {
        UUID id = p.getUniqueId();
        TutorialStore.Entry entry = plugin.store().get(id);
        entry.finished = System.currentTimeMillis();
        entry.seconds += Math.max(0L, (entry.finished - s.joinedAtMs) / 1000L);
        s.joinedAtMs = entry.finished;
        entry.step = Step.DONE.name();
        if (s.guest) {
            entry.state = TutorialStore.State.DONE;
            entry.guest = false;
            plugin.store().save(id, entry);
            return;
        }
        entry.state = TutorialStore.State.GRADUATING;
        entry.completedOnce = true;
        plugin.store().save(id, entry);
        try {
            de.aetherion.quests.AetherionQuests quests = de.aetherion.quests.AetherionQuests.getInstance();
            if (quests != null && quests.getPlayerQuestStorage() != null) {
                for (String quest : SPINE) {
                    quests.getPlayerQuestStorage().saveQuest(id, quest, de.aetherion.quests.model.QuestState.COMPLETED);
                }
                quests.getPlayerQuestStorage().clearStarterKit(id, "tutorial_graduation");
                if (quests.getQuestManager() != null) {
                    quests.getQuestManager().forgetPlayer(id);
                }
            }
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Could not file the island as done for " + p.getName() + ": " + ex);
        }
        plugin.getLogger().info("[Morningbell] " + p.getName() + " sails after " + (entry.seconds / 60L) + " min "
                + (entry.seconds % 60L) + " s" + (entry.skipped ? " (the short way)" : "") + ", stumbles: " + entry.deaths);
    }

    /** The crossing never happened (the main world is closed): the player is back on the pier, Wim waits. */
    void stranded(Player p, Session s) {
        UUID id = p.getUniqueId();
        TutorialStore.Entry entry = plugin.store().get(id);
        entry.state = TutorialStore.State.IN_PROGRESS;
        if (s.guest) {
            entry.guest = true;
        } else {
            try {
                de.aetherion.quests.AetherionQuests quests = de.aetherion.quests.AetherionQuests.getInstance();
                if (quests != null && quests.getPlayerQuestStorage() != null) {
                    quests.getPlayerQuestStorage().markStarterKit(id, "tutorial_graduation");
                }
            } catch (LinkageError | RuntimeException ignored) {
                // Quests is not here: nothing to keep quiet
            }
        }
        plugin.store().save(id, entry);
        s.step = Step.TALK_WIM;
        enter(p, s, Step.TALK_WIM);
        p.sendMessage(Txt.t(p, "§3Captain Wim§8: §fFog on the crossing. We wait a moment, then try again: talk to me when you are ready.",
                "§3Kapitän Wim§8: §fNebel auf der Überfahrt. Wir warten kurz und versuchen es dann noch mal: Sprich mich an, wenn du so weit bist."));
    }

    // ------------------------------------------------------------------ the golem

    private boolean spawnGolem(Player p) {
        try {
            return de.aetherion.items.combat.system.CombatSystem.drillGolem(p);
        } catch (LinkageError | RuntimeException ex) {
            return false;
        }
    }

    private boolean hasGolem(Player p) {
        try {
            return de.aetherion.items.combat.system.CombatSystem.hasGolem(p, 16.0);
        } catch (LinkageError | RuntimeException ex) {
            return true;
        }
    }

    private void clearGolem(Player p) {
        try {
            de.aetherion.items.combat.system.CombatSystem.clearGolem(p);
        } catch (LinkageError | RuntimeException ignored) {
            // no combat kit here
        }
    }

    // ------------------------------------------------------------------ talking

    /**
     * A beat in the speaker's bubble (the lines queue, the chips come after the last one). In classic chat mode,
     * or when the player stands too far away, the same lines go to the chat and the chips become their first
     * action only if there is exactly one.
     */
    boolean say(Player p, String castId, List<String> lines, List<TalkAccess.Reply> chips) {
        Cast.Member member = Cast.ALL.get(castId);
        if (member == null || lines.isEmpty()) {
            return false;
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            try {
                if (talk.bubbles(p) && talk.say(p, member.speaker(), lines.get(0))) {
                    for (int i = 1; i < lines.size(); i++) {
                        talk.say(p, member.speaker(), lines.get(i));
                    }
                    if (!chips.isEmpty()) {
                        talk.replies(p, member.speaker(), chips);
                    }
                    return true;
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Talk bubble failed for " + member.name() + ": " + ex);
            }
        }
        for (String line : lines) {
            p.sendMessage(member.color() + member.name() + "§8: §f" + line);
        }
        if (chips.size() == 1 && chips.get(0).action() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, chips.get(0).action(), 30L);
        }
        return false;
    }

    /** A short reaction: one line over the head when the player stands close, in the chat otherwise. */
    void react(Player p, String castId, String line) {
        Cast.Member member = Cast.ALL.get(castId);
        if (member == null || line == null) {
            return;
        }
        Location spot = isle.cast().spot(castId);
        TalkAccess talk = AetherServices.talk();
        if (talk != null && spot != null && spot.getWorld() == p.getWorld() && spot.distanceSquared(p.getLocation()) < 24.0 * 24.0) {
            try {
                talk.bark(member.speaker(), line, List.of(p), 110);
            } catch (RuntimeException ignored) {
                // the line still reaches the chat below
            }
        }
        p.sendMessage(member.color() + member.name() + "§8: §f" + line);
    }

    // ------------------------------------------------------------------ pockets

    private static boolean near(Player p, Location at, double radius) {
        return at.getWorld() == p.getWorld() && at.distanceSquared(p.getLocation()) <= radius * radius;
    }

    /** Makes sure the player owns the Aetherion item {@code id}; hands one over when it is missing. */
    private void ensure(Player p, String id) {
        ItemFactoryAccess items = AetherServices.items();
        if (items == null) {
            return;
        }
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && id.equals(items.itemId(stack))) {
                return;
            }
        }
        ItemStack made = items.create(id);
        if (made != null) {
            give(p, made);
        }
    }

    private static void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(stack);
        for (ItemStack left : rest.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
    }

    private static int count(Player p, Material material) {
        int n = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && stack.getType() == material && !stack.hasItemMeta()) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /** Fills the player's plain {@code material} up to {@code amount}. */
    private static void topUp(Player p, Material material, int amount) {
        int missing = amount - count(p, material);
        if (missing > 0) {
            give(p, new ItemStack(material, missing));
        }
    }

    private static void take(Player p, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || stack.hasItemMeta()) {
                continue;
            }
            int n = Math.min(left, stack.getAmount());
            left -= n;
            if (n == stack.getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - n);
            }
        }
    }

    private static void unlock(Player p, String flag, String title, String subtitle) {
        ProgressAccess progress = AetherServices.progress();
        if (progress != null) {
            progress.unlock(p, flag, title, subtitle);
        }
    }

    /** Makes sure the four stone pieces exist; puts them on (free slots only) when {@code wear}. */
    private void wearStone(Player p, boolean wear) {
        ItemFactoryAccess items = AetherServices.items();
        if (items == null) {
            return;
        }
        int owned = armourOwned(p);
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < ARMOUR.length; i++) {
            if ((owned & (1 << i)) != 0) {
                continue;
            }
            ItemStack piece = items.create(ARMOUR[i]);
            if (piece == null) {
                continue;
            }
            boolean worn = false;
            if (wear) {
                switch (i) {
                    case 0 -> {
                        if (empty(inv.getHelmet())) {
                            inv.setHelmet(piece);
                            worn = true;
                        }
                    }
                    case 1 -> {
                        if (empty(inv.getChestplate())) {
                            inv.setChestplate(piece);
                            worn = true;
                        }
                    }
                    case 2 -> {
                        if (empty(inv.getLeggings())) {
                            inv.setLeggings(piece);
                            worn = true;
                        }
                    }
                    default -> {
                        if (empty(inv.getBoots())) {
                            inv.setBoots(piece);
                            worn = true;
                        }
                    }
                }
            }
            if (!worn) {
                give(p, piece);
            }
        }
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private static int armourWorn(Player p) {
        int worn = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                worn++;
            }
        }
        return worn;
    }

    /** Bit per stone armour piece the player owns (worn or carried). */
    private static int armourOwned(Player p) {
        ItemFactoryAccess items = AetherServices.items();
        if (items == null) {
            return 0;
        }
        int bits = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            String id = items.itemId(stack);
            for (int i = 0; i < ARMOUR.length; i++) {
                if (ARMOUR[i].equals(id)) {
                    bits |= 1 << i;
                }
            }
        }
        return bits;
    }

    /** Cobblestone the pieces still to be made need (helmet 5, chestplate 8, leggings 7, boots 4). */
    private static int cobbleStillNeeded(Player p) {
        int owned = armourOwned(p);
        int[] cost = {5, 8, 7, 4};
        int need = 0;
        for (int i = 0; i < cost.length; i++) {
            if ((owned & (1 << i)) == 0) {
                need += cost[i];
            }
        }
        return need;
    }
}
