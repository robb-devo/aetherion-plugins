package de.aetherion.guilds.island;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.core.exile.Say;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The isle hand (Corin by default, {@code guide.name}): a FancyNPC on every personal starter island who explains
 * the island step by step, in the TalkUx speech bubble. He never walks: he greets the owner where they land, and
 * once the Hub stands he keeps his post beside it.
 *
 * <p><b>How the tour moves.</b> Every step is one short talk and one task, and nothing happens behind the player's
 * back: he explains a step and hands over its part <i>when the player talks to him</i>, or as his reaction the
 * moment the task before it is done while they stand by him. Is the player further away, the card at the top says
 * the task is done, a gold mark stands over his head and the bar says "talk to him"; what he has to say waits until
 * they come over (never a speech into chat, never a tool that switches on its own). A talk is never cut off by a
 * timer. The steps:
 * <ol>
 *   <li><b>Your base</b>: he hands over the Build Tool (what it is, that the Hub and he hand it back any time),
 *   the Hub, then the Storage Hut beside it (the one Build Tool lesson: sneak and scroll),</li>
 *   <li><b>Goods flow</b>: he hands over the Cobble Quarry and free belt tiles; then the two colours of every belt
 *   (orange = goods come out, green = goods go in) and the first belt, quarry into hut,</li>
 *   <li><b>Split and refine</b>: he hands over the Mill and the Splitter; the Mill beside the belt, the Splitter cut
 *   into the running belt and its side belted into the Mill (he tips a starter load in), the Mill belted into the
 *   hut: raw stone builds, pressed stone ships,</li>
 *   <li><b>Ship it</b>: the Dock, unlocked at the Hub lectern, and the First Shipment: stage 1,</li>
 *   <li><b>Build a quarry</b>: the shipment pays the parts of a second quarry; he explains how every quarry is
 *   made and the player crafts this one themselves, once,</li>
 *   <li><b>Grow</b>: the first piece of land, a gift.</li>
 * </ol>
 * There are no pads, rings or marked spots: the player picks every place; the Build Tool's board beside each
 * hologram says what the part does and where it fits best. Every step is detected from the island itself, so the
 * tour never blocks building: do things in any order, walk away, skip it, pick it up again by talking to him.
 * The Forge and everything after it are factory goals. He speaks the player's language (English or German).
 *
 * <p>Without the bubble (TalkUx off, player on classic chat talk) the same lines and choices go to chat.
 * Without FancyNpcs there's no guide: the classic "first steps" card is used instead.
 */
public final class IslandGuide implements Listener {

    /**
     * The tour is stage 0 of the island: Hub → hut → (his quarry) quarry → belt into the hut → (his Mill and
     * Splitter) Mill → Splitter cut into the belt → its side into the Mill → the Mill into the hut → the Dock,
     * unlocked at the Hub → the first ship (stage 1) → the second quarry, crafted by hand → the first land. PRESS,
     * FORGE and COMPACT stay only so old saves still load: they, and every step saved by an older tour, start
     * again from the island itself.
     */
    public enum Step {
        WELCOME,
        HUB,
        HUT,
        QUARRY,
        BELT,
        MILL,
        SPLIT,
        FEED,
        OUTPUT,
        PRESS,
        FORGE,
        COMPACT,
        DOCK,
        SHIP,
        /** After the first ship: the parts of a second quarry, which the player crafts and sets down themselves. */
        CRAFT,
        /** Then the first piece of land, a gift, so you know where land is bought. */
        LAND,
        DONE,
        SKIPPED;

        public boolean touring() {
            return ordinal() >= HUB.ordinal() && ordinal() <= LAND.ordinal();
        }

        /** The Dock half of the tour (the first chain stands; now the Dock, the first ship and the first land). */
        public boolean actTwo() {
            return ordinal() >= DOCK.ordinal() && ordinal() <= LAND.ordinal();
        }

        /** Steps only old saves still carry: the island itself says where those players go on. */
        public boolean legacy() {
            return this == PRESS || this == FORGE || this == COMPACT;
        }

        public boolean finished() {
            return this == DONE || this == SKIPPED;
        }
    }

    /**
     * island_guide.yml format: 4 = the talk-and-task tour (it remembers which step he has explained); 3 = the
     * chapter tour, read as it is; the steps of older files mean an older tour's order.
     */
    private static final int SAVE_VERSION = 4;

    private static final String NPC_PREFIX = "aeg_hand_";
    private static final String SPEAKER_PREFIX = "isle_hand_";
    private static final String HOLO_TAG = "aeg_hand_holo";
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 80), 1.3f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 150, 40), 1.4f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(90, 230, 120), 1.4f);
    /** Kits bit: the starter kit (quarry, Mill unlock, free tiles) was handed over. */
    private static final int KIT = 1 << 5;
    /** Kits bit: the first chain's beat (the first Compressed Cobble in the hut) was played. */
    private static final int CHAIN_BEAT = 1 << 6;
    /** Kits bit: the one spare quarry Corin hands out if the first went missing. */
    private static final int SPARE_QUARRY = 1 << 4;
    /** Kits bit: the Build Tool lesson is over (the player switched to the Quarry, or let him do it). */
    private static final int TOOL_TAUGHT = 1 << 7;
    /** Kits bit: the tour's free parcel was taken. */
    private static final int LAND_GIFT = 1 << 8;
    /** Kits bit: chapter 3's parts (the Mill and the Splitter) were handed over. */
    private static final int REFINE_KIT = 1 << 9;
    /** Kits bit: the card with what the first ship brings was shown. */
    private static final int GOAL_CARD = 1 << 10;
    /** Kits bit: the Build Tool was handed over (its card shown: what it is, where it comes back from). */
    private static final int TOOL_GIVEN = 1 << 11;
    /** Kits bit: the parts of the second quarry were handed over (the first shipment's pay). */
    private static final int CRAFT_KIT = 1 << 12;
    /** Kits bit: the two colours of a belt (orange out, green in) were shown as a card. */
    private static final int COLOURS = 1 << 13;

    private static final class State {
        Step step = Step.WELCOME;
        boolean introduced;
        /** Session-only: full welcome lines already played — later clicks get chips, not a line-1 restart. */
        boolean welcomeSpoken;
        boolean quarryGiven;
        int freeTiles;
        /** What was handed out and played once: {@link #KIT}, {@link #CHAIN_BEAT}, {@link #SPARE_QUARRY}. */
        int kits;
        /** The starter load went into the Mill (once per island). */
        boolean primed;
        /** The step he has explained and handed the part for; any other step still waits for a talk with him. */
        Step briefed;
        /** Session-only: last sub-step seen in the Dock step (locked, unlocked, built), -1 = none yet. */
        int lastSub = -1;
        /** Session-only: when the first chain closed (its beat waits for the first block, 12 s at most). */
        long beatSince;
        /** Session-only: the craft step's last sub-step (0 = nothing crafted yet, 1 = the quarry is in the pack). */
        int craftSub = -1;
    }

    private static final class Hand {
        final UUID owner;
        final String npcName;
        final String speakerId;
        Object npc;
        Location at;
        /** His post: beside the Hub once it stands, else where the owner lands. He only ever stands there. */
        Location home;
        TextDisplay holo;
        String holoText = "";
        /** The gold mark over his head while he has something to say (shown to the owner only). */
        TextDisplay mark;
        boolean markShown;
        long lastSeen;
        boolean spawning;
        /** What he says once the owner comes over to him (his reaction to a task done further away). */
        Runnable waiting;
        /** When he last rang for the owner (one soft bell, never chat). */
        long rang;
        /** He already spoke up for this visit of the owner (reset once they have gone off again). */
        boolean offered;

        Hand(UUID owner) {
            this.owner = owner;
            String hex = owner.toString().replace("-", "").substring(0, 12);
            this.npcName = NPC_PREFIX + hex;
            this.speakerId = SPEAKER_PREFIX + hex;
        }
    }

    private static final class Chat {
        final Hand hand;
        final Deque<String> lines = new ArrayDeque<>();
        List<TalkAccess.Reply> replies = List.of();
        long nextAt;

        Chat(Hand hand) {
            this.hand = hand;
        }
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final PersonalIslandService personal;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final MinionService minions;
    private final File file;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Hand> hands = new HashMap<>();
    private final Map<UUID, Chat> chats = new HashMap<>();
    private final Map<UUID, List<Runnable>> chatReplies = new HashMap<>();
    private final Map<UUID, Long> clickCool = new HashMap<>();
    private Consumer<Player> openHub = player -> player.performCommand("island");
    private BiConsumer<Player, IslandHost> openBuild = (player, host) -> player.performCommand("island build");
    private BiConsumer<Player, IslandHost> openLectern = (player, host) -> player.performCommand("island build");
    private long clock;
    private boolean dirty;
    private boolean fancyHooked;

    public IslandGuide(JavaPlugin plugin, HostService hosts, StructureService structures, LogisticsService logistics,
                       PlacementService placement, MinionService minions) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.personal = hosts.personalService();
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
        this.minions = minions;
        this.file = new File(plugin.getDataFolder(), "island_guide.yml");
        load();
        placement.attachSwitched(this::switched);
        placement.attachCalm(player -> {
            State state = available() ? states.get(player.getUniqueId()) : null;
            return state != null && state.step.touring();
        });
        logistics.setBeltPass(new LogisticsService.BeltPass() {
            @Override
            public boolean noWorkshopNeeded(Player player, IslandHost host) {
                if (host != null && !host.isGuild() && !available()) {
                    // no guide on this server: the first chain's belt still needs no Workshop
                    for (LogisticsService.Route route : logistics.routes(host)) {
                        if (route.to() != null && route.from().type() == StructureType.QUARRY_HOUSING) {
                            return false;
                        }
                    }
                    return true;
                }
                State state = host == null || host.isGuild() ? null : states.get(host.id());
                return state != null && state.step.touring() && state.freeTiles > 0;
            }

            @Override
            public int freeTiles(IslandHost host) {
                State state = host == null || host.isGuild() ? null : states.get(host.id());
                return state == null || state.step.finished() ? 0 : Math.max(0, state.freeTiles);
            }

            @Override
            public void useFree(IslandHost host, int tiles) {
                State state = host == null || host.isGuild() ? null : states.get(host.id());
                if (state != null) {
                    state.freeTiles = Math.max(0, state.freeTiles - tiles);
                    dirty = true;
                }
            }

            @Override
            public void returnFree(IslandHost host, int tiles) {
                State state = host == null || host.isGuild() ? null : states.get(host.id());
                if (state != null && !state.step.finished()) {
                    state.freeTiles = Math.min(64, state.freeTiles + tiles);
                    dirty = true;
                }
            }
        });
    }

    private FactoryGoals goals;
    private BiConsumer<IslandHost, StructureType> unlocker = (host, type) -> {
    };

    public void attachGoals(FactoryGoals goals) {
        this.goals = goals;
    }

    /** Unlocks a part for an island without the Hub price (the starter kit's Mill). */
    public void attachUnlocker(BiConsumer<IslandHost, StructureType> unlocker) {
        if (unlocker != null) {
            this.unlocker = unlocker;
        }
    }

    /** The Hub lectern menu (where the tour's Dock unlocks). */
    public void attachLectern(BiConsumer<Player, IslandHost> openLectern) {
        if (openLectern != null) {
            this.openLectern = openLectern;
        }
    }

    public void attachMenus(Consumer<Player> openHub, BiConsumer<Player, IslandHost> openBuild) {
        if (openHub != null) {
            this.openHub = openHub;
        }
        if (openBuild != null) {
            this.openBuild = openBuild;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // config
    // ------------------------------------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("guide.enabled", true);
    }

    /** The guide can stand on islands (FancyNpcs is there and the guide is on). */
    public boolean available() {
        return enabled() && FancyNpcFacade.isAvailable();
    }

    public String name() {
        return plugin.getConfig().getString("guide.name", "Corin");
    }

    private String title() {
        return plugin.getConfig().getString("guide.title", "Isle Hand");
    }

    private int freeTilesGranted() {
        return Math.max(0, plugin.getConfig().getInt("guide.free-belt-tiles", 16));
    }

    /** Free belt tiles in the starter kit: enough for quarry → Mill → hut with room to spare. */
    private int kitTiles() {
        return Math.max(0, Math.min(64, plugin.getConfig().getInt("guide.kit-belt-tiles", 32)));
    }

    // ------------------------------------------------------------------------------------------------
    // state (what the menus and the island bar read)
    // ------------------------------------------------------------------------------------------------

    private State state(UUID owner) {
        return states.computeIfAbsent(owner, id -> {
            State fresh = new State();
            fresh.freeTiles = freeTilesGranted();
            dirty = true;
            return fresh;
        });
    }

    public Step step(UUID owner) {
        State state = owner == null ? null : states.get(owner);
        return state == null ? Step.WELCOME : state.step;
    }

    private static String t(Player player, String en, String de) {
        return Say.t(player, en, de);
    }

    private static List<String> lines(Player player, List<String> en, List<String> de) {
        return Say.german(player) ? de : en;
    }

    /** He still has to explain the current step and hand its part over: the tour waits for a talk with him. */
    private boolean needsTalk(State state) {
        if (state == null || state.step == Step.WELCOME || state.step.legacy()) {
            return true;
        }
        return state.step.touring() && state.briefed != state.step;
    }

    private void briefed(State state) {
        if (state.briefed != state.step) {
            state.briefed = state.step;
            dirty = true;
        }
    }

    /** Short objective for the island bar / hub while the tour runs, else null (English). */
    public String objective(UUID owner) {
        return objective(owner, null);
    }

    /** The same in the reader's language: "Talk to Corin" while he still has the step to explain, else the task. */
    public String objective(UUID owner, Player viewer) {
        if (!available()) {
            return null;
        }
        State state = states.get(owner);
        Step step = state == null ? Step.WELCOME : state.step;
        if (step.finished()) {
            return null;
        }
        IslandHost host = IslandHost.personal(owner);
        if (needsTalk(state)) {
            return step == Step.WELCOME || step.legacy()
                    ? t(viewer, "Talk to " + name(), "Sprich mit " + name())
                    : t(viewer, "Talk to " + name() + " §7(he has your next step)",
                    "Sprich mit " + name() + " §7(er hat deinen nächsten Schritt)");
        }
        return switch (step) {
            case HUB -> {
                PlacedStructure hub = structures.hub(host);
                yield hub != null && hub.building() ? t(viewer, "Hub going up…", "Der Hub entsteht…")
                        : t(viewer, "Place your Hub: aim at flat ground, right-click §7(free)",
                        "Setz deinen Hub: auf ebenen Boden zielen, Rechtsklick §7(gratis)");
            }
            case HUT -> lessonPending(state)
                    ? t(viewer, "Switch the Build Tool to the Storage Hut §7(hold sneak, scroll)",
                    "Stell das Bauwerkzeug auf Storage Hut §7(Schleichen halten, Mausrad)")
                    : t(viewer, "Set your Storage Hut next to the Hub §7(free)",
                    "Setz deine Lagerhütte (Storage Hut) neben den Hub §7(gratis)");
            case QUARRY -> t(viewer, "Set your quarry down §7(Build Tool, flat ground)",
                    "Stell deinen Steinbruch auf §7(Bauwerkzeug, ebener Boden)");
            case BELT -> t(viewer, "Belt: right-click the quarry's §6orange chute§f, then the hut's §agreen pad",
                    "Band: Rechtsklick auf die §6orange Rutsche§f am Steinbruch, dann aufs §agrüne Feld§f der Hütte");
            case MILL -> t(viewer, "Set the Mill down beside your belt §7(free)",
                    "Stell die Mühle (Mill) neben dein Band §7(gratis)");
            case SPLIT -> t(viewer, "Cut the Splitter into your belt §7(right-click the belt)",
                    "Setz den Splitter in dein Band §7(Rechtsklick aufs Band)");
            case FEED -> t(viewer, "Belt an §6orange side§f of the Splitter into the Mill's §agreen pad",
                    "Band von einer §6orangen Seite§f des Splitters ins §agrüne Feld§f der Mühle");
            case OUTPUT -> t(viewer, "Belt the Mill's §6orange chute§f into the hut",
                    "Band von der §6orangen Rutsche§f der Mühle in die Hütte");
            case DOCK, SHIP -> factoryObjective(host, step, viewer);
            case CRAFT -> {
                String quarry = craftType(host).display();
                Player player = Bukkit.getPlayer(owner);
                yield player != null && hasQuarryItem(player)
                        ? t(viewer, "Set your " + quarry + " down §7(Build Tool › Quarry)",
                        "Stell deinen " + quarry + " auf §7(Bauwerkzeug › Quarry)")
                        : t(viewer, "Build your " + quarry + " §7(Journal › Workshop › Recipes › Ready to craft)",
                        "Bau deinen " + quarry + " §7(Journal › Werkstatt › Rezepte › Bereit)");
            }
            case LAND -> t(viewer, "Take your first land, a gift §7(Hub › Land)",
                    "Hol dir dein erstes Land, geschenkt §7(Hub › Land)");
            default -> null;
        };
    }

    /**
     * A tour step just got done: a small screen card at the top ("✔ Mill set down", step n/12, what's next).
     * Steps with their own big moment (Hub, first chain, the ship) get none. False = no card (cards off).
     */
    private boolean stepToast(Player owner, Step done, boolean primed) {
        String title = switch (done) {
            case HUT -> t(owner, "Storage Hut built", "Lagerhütte steht");
            case QUARRY -> t(owner, "Quarry set down", "Steinbruch steht");
            case BELT -> t(owner, "Goods are flowing", "Die Ware läuft");
            case MILL -> t(owner, "Mill set down", "Mühle steht");
            case SPLIT -> t(owner, "Splitter cut in", "Splitter sitzt im Band");
            case FEED -> t(owner, "Mill fed", "Die Mühle bekommt Stein");
            case DOCK -> t(owner, "Dock built", "Dock steht");
            case CRAFT -> t(owner, "Second quarry set down", "Zweiter Steinbruch steht");
            case LAND -> t(owner, "Your first land", "Dein erstes Land");
            default -> null;
        };
        if (title == null) {
            return false;
        }
        String next = objective(owner.getUniqueId(), owner);
        String line = done == Step.FEED && primed
                ? t(owner, "§7" + name() + " tipped a starter load in: it presses at once",
                "§7" + name() + " hat eine Startladung eingekippt: sie presst sofort")
                : done == Step.BELT ? t(owner, "§7Watch your hut's count climb", "§7Sieh zu, wie deine Hütte zählt")
                : t(owner, "§7Island tour · step ", "§7Insel-Tour · Schritt ") + progress(owner.getUniqueId()) + "/" + total();
        return IslandUi.toast(owner, title, line, next == null ? null : t(owner, "§8Next: §7", "§8Weiter: §7") + next);
    }

    /** Steps in the whole tour (for "step n/12"). */
    public int total() {
        return 12;
    }

    /** 0..12 steps done (for progress bars). */
    public int progress(UUID owner) {
        return switch (step(owner)) {
            case HUT -> 1;
            case QUARRY -> 2;
            case BELT -> 3;
            case MILL -> 4;
            case SPLIT -> 5;
            case FEED -> 6;
            case OUTPUT -> 7;
            case DOCK -> 8;
            case SHIP -> 9;
            case CRAFT -> 10;
            case LAND -> 11;
            case DONE, SKIPPED -> 12;
            default -> 0;
        };
    }

    /** True while the guide still runs the first chain himself (the factory goals wait until then). */
    public boolean firstAct(UUID owner) {
        Step step = step(owner);
        return available() && objective(owner) != null && !step.actTwo();
    }

    /** The part the tour wants unlocked at the Hub right now (the lectern shows it glowing), or null. */
    public StructureType tourUnlock(IslandHost host) {
        if (host == null || host.isGuild() || !available()) {
            return null;
        }
        Step step = step(host.id());
        return step == Step.DOCK && !structures.unlocked(host, StructureType.DOCK) ? StructureType.DOCK : null;
    }

    /** While the tour runs, the first Hub, Mill, Splitter and Dock are on the house (the first hut is always free). */
    public boolean freeFirst(IslandHost host, StructureType type) {
        if (host == null || host.isGuild() || !available()) {
            return false;
        }
        if (type != StructureType.WORKSHOP && type != StructureType.MILL && type != StructureType.DOCK
                && type != StructureType.SPLITTER) {
            return false;
        }
        State state = states.get(host.id());
        return state != null && state.step.touring();
    }

    /** Is the tour running on this island (the Hub lectern then only lights the tour's part)? */
    public boolean inFactoryTour(IslandHost host) {
        return host != null && !host.isGuild() && available() && step(host.id()).touring();
    }

    /** Stage goals get the Dock's own beat (the airship and the stage title), never a second title on top. */
    public boolean ownsTitle(IslandHost host, String goalId) {
        if (host == null || host.isGuild()) {
            return false;
        }
        return "first_ship".equals(goalId) || "stage2".equals(goalId) || "stage3".equals(goalId);
    }

    /**
     * The tour's line on the Build Tool board for the part the player holds: where it fits best in the first
     * chain ("a few blocks in front of the quarry's chute"), or null when the tour has nothing to add.
     */
    public String boardHint(Player player, PlacementService.Part part) {
        if (player == null || part == null || !available()) {
            return null;
        }
        Step step = step(player.getUniqueId());
        IslandHost host = hosts.at(player.getLocation());
        if (host == null || host.isGuild() || !host.id().equals(player.getUniqueId())) {
            return null;
        }
        return switch (part) {
            case HUB -> step == Step.HUB ? "Anywhere flat, machines go round it" : null;
            case STORAGE_HUT -> step == Step.HUT ? "Right next to your Hub" : null;
            case QUARRY -> step == Step.QUARRY ? "A few blocks from the hut, chute toward it"
                    : step == Step.CRAFT ? "Anywhere flat, then belt its chute in" : null;
            case MILL -> step == Step.MILL ? "Beside your belt, a few blocks off it" : null;
            case SPLITTER -> step == Step.SPLIT ? "Right-click your running belt" : null;
            case DOCK -> step == Step.DOCK ? "Near the island's edge looks best" : null;
            case BELT -> step == Step.BELT ? "Quarry chute → hut"
                    : step == Step.FEED ? "Splitter's side → Mill"
                    : step == Step.OUTPUT ? "Mill chute → hut" : null;
            default -> null;
        };
    }

    private DockService dock;
    private BiConsumer<Player, IslandHost> openDock = (player, host) -> player.performCommand("island");

    /** The Dock: the first contract and the ship are the end of the tour. */
    public void attachDock(DockService dock, BiConsumer<Player, IslandHost> openDock) {
        this.dock = dock;
        if (openDock != null) {
            this.openDock = openDock;
        }
    }

    /** What the Dock unlock costs in Compressed Cobble on this island (the tour says "the one you just made"). */
    private long dockPrice(IslandHost host) {
        de.aetherion.guilds.structure.BuildCosts.Unlock unlock =
                de.aetherion.guilds.structure.BuildCosts.unlock(host, StructureType.DOCK);
        long need = 0L;
        if (unlock != null) {
            for (de.aetherion.guilds.structure.BuildCosts.Line line : unlock.price()) {
                if (de.aetherion.guilds.structure.BuildCosts.COMPRESSED.equals(line.res())) {
                    need += line.amount();
                }
            }
        }
        return need;
    }

    private String factoryObjective(IslandHost host, Step step, Player viewer) {
        return switch (step) {
            case DOCK -> switch (sub(host, StructureType.DOCK)) {
                case 0 -> t(viewer, "Unlock the Dock at the Hub lectern", "Schalte das Dock am Lesepult des Hubs frei")
                        + " §7(" + Math.min(compressedInStorage(host), dockPrice(host)) + "/" + dockPrice(host)
                        + " Compressed Cobble)";
                case 1 -> t(viewer, "Place your Dock anywhere §7(free)", "Setz dein Dock, wohin du willst §7(gratis)");
                default -> t(viewer, "Your Dock is going up…", "Dein Dock entsteht…");
            };
            case SHIP -> {
                DockService.Contract first = dock == null ? null : dock.phase(host);
                if (first == null || first.needs().isEmpty()) {
                    yield t(viewer, "Send your first ship from the Dock", "Schick dein erstes Schiff vom Dock los");
                }
                if (first.full()) {
                    yield t(viewer, "The airship is coming for your first shipment",
                            "Das Luftschiff holt deine erste Lieferung");
                }
                StringBuilder have = new StringBuilder();
                for (DockService.Need need : first.needs()) {
                    have.append(have.length() == 0 ? "" : ", ").append(Math.min(need.amount(), first.loaded(need.res())))
                            .append("/").append(need.amount()).append(need.res().form() == Res.Form.RAW ? " Cobble" : " Compressed");
                }
                yield t(viewer, "Fill the First Shipment at the Dock", "Fülle die erste Lieferung am Dock")
                        + " §7(" + have + ")";
            }
            default -> null;
        };
    }

    /**
     * The Dock's stage beats: he says a few lines to the owner (in chat when he isn't around). The first ship is
     * his own moment (the second quarry's parts and how a quarry is made, then the first land): there he speaks
     * for himself instead.
     */
    public void say(Player player, List<String> lines) {
        if (player == null || lines == null || lines.isEmpty()) {
            return;
        }
        Hand hand = hands.get(player.getUniqueId());
        if (hand == null || hand.at == null || !available()) {
            for (String line : lines) {
                player.sendMessage("§e" + name() + "§7: §f" + line);
            }
            return;
        }
        State state = state(hand.owner);
        advance(IslandHost.personal(hand.owner), state);
        if (state.step == Step.CRAFT || state.step == Step.LAND) {
            react(player, hand, state.step, 0L);
            return;
        }
        speak(player, hand, lines, List.of(
                reply(t(player, "What's next?", "Was kommt als Nächstes?"), NamedTextColor.GREEN, () -> tip(player, hand)),
                reply(t(player, "Thanks, " + name(), "Danke, " + name()), NamedTextColor.GRAY, null)));
    }

    private String label(PlacedStructure structure) {
        if (structure == null) {
            return "?";
        }
        return structure.type() == StructureType.QUARRY_HOUSING ? "Quarry" : structure.type().display();
    }

    /** Mill / Forge step: 0 = still locked, 1 = unlocked but none built, 2 = built (now belt it in). */
    private int sub(IslandHost host, StructureType type) {
        if (!structures.unlocked(host, type)) {
            return 0;
        }
        return structures.count(host, type) == 0 ? 1 : 2;
    }

    private boolean eligible(PersonalIsland island) {
        return island != null && hosts.starter(IslandHost.personal(island.ownerId())) != null;
    }

    // ------------------------------------------------------------------------------------------------
    // entry points
    // ------------------------------------------------------------------------------------------------

    /** Right after the claim ceremony: he says hello where the owner lands. False = no guide (caller shows the card). */
    public boolean introduce(Player player) {
        PersonalIsland island = personal.byOwner(player.getUniqueId());
        if (!available() || !eligible(island)) {
            return false;
        }
        Hand hand = ensureHand(island);
        if (hand == null) {
            return false;
        }
        State state = state(island.ownerId());
        // Mark introduced before the delay so ownerSecond / early clicks can't fire a second greet.
        state.introduced = true;
        dirty = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            State now = state(island.ownerId());
            // Already mid-welcome (clicked him during the land delay): don't restart line 1.
            if (now.step != Step.WELCOME || chats.containsKey(player.getUniqueId())) {
                return;
            }
            TalkAccess talk = AetherServices.talk();
            if (talk != null && talk.talkingWith(player, hand.speakerId)) {
                return;
            }
            greet(player, hand);
        }, 30L);
        return true;
    }

    /** From the island hub: resume (or restart) the tour. */
    public void resume(Player player) {
        PersonalIsland island = personal.byOwner(player.getUniqueId());
        if (!available() || !eligible(island)) {
            player.sendMessage("§7" + name() + " only works starter islands (and needs FancyNpcs on the server).");
            return;
        }
        IslandHost host = IslandHost.personal(island.ownerId());
        if (!host.equals(hosts.at(player.getLocation()))) {
            // take them home first; he's waiting in the yard
            personal.goHome(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && host.equals(hosts.at(player.getLocation()))) {
                    resume(player);
                }
            }, 40L);
            return;
        }
        State state = state(island.ownerId());
        if (state.step == Step.SKIPPED) {
            state.step = Step.WELCOME;
            dirty = true;
        }
        Hand hand = ensureHand(island);
        if (hand == null) {
            player.sendMessage("§7" + name() + " is on his way… try again in a second.");
            return;
        }
        state.briefed = null; // asked for him by name: he explains the step again
        talkTo(player, hand);
    }

    // ------------------------------------------------------------------------------------------------
    // staff test rig (/island admin puppet): the console clicks him, reads on and answers for a player
    // ------------------------------------------------------------------------------------------------

    /** The answers he offered each player last (what {@code puppet pick} chooses from). */
    private final Map<UUID, List<TalkAccess.Reply>> lastReplies = new HashMap<>();

    private Hand handAt(Player player) {
        IslandHost host = hosts.at(player.getLocation());
        return host == null || host.isGuild() ? null : hands.get(host.id());
    }

    public String puppetClick(Player player) {
        Hand hand = handAt(player);
        if (hand == null) {
            return "no isle hand here";
        }
        clickCool.remove(player.getUniqueId());
        clicked(player, hand.npcName);
        return puppetState(player);
    }

    public String puppetNext(Player player) {
        Hand hand = handAt(player);
        return hand == null ? "no isle hand here" : "next line: " + IslandUi.advance(player, hand.speakerId);
    }

    public String puppetPick(Player player, int index) {
        List<TalkAccess.Reply> replies = lastReplies.remove(player.getUniqueId());
        if (replies == null || index < 0 || index >= replies.size()) {
            return "no such answer (" + (replies == null ? 0 : replies.size()) + " offered)";
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(player);
        }
        chats.remove(player.getUniqueId());
        TalkAccess.Reply reply = replies.get(index);
        reply.action().run();
        return "picked: " + reply.label();
    }

    public String puppetState(Player player) {
        IslandHost host = hosts.at(player.getLocation());
        UUID owner = host == null || host.isGuild() ? player.getUniqueId() : host.id();
        State state = states.get(owner);
        Hand hand = hands.get(owner);
        TalkAccess talk = AetherServices.talk();
        StringBuilder out = new StringBuilder("step=").append(state == null ? "WELCOME" : state.step)
                .append(" briefed=").append(state == null ? null : state.briefed)
                .append(" kits=").append(state == null ? 0 : state.kits)
                .append(" talk=").append(needsTalk(state))
                .append(" waiting=").append(hand != null && hand.waiting != null)
                .append(" talking=").append(hand != null && (chats.containsKey(player.getUniqueId())
                        || (talk != null && talk.talkingWith(player, hand.speakerId))))
                .append(" bar=\"").append(String.valueOf(objective(owner, player)).replaceAll("§.", "")).append('"');
        List<TalkAccess.Reply> replies = lastReplies.get(player.getUniqueId());
        if (replies != null && !replies.isEmpty()) {
            out.append(" answers=");
            for (TalkAccess.Reply reply : replies) {
                out.append('[').append(reply.label()).append(']');
            }
        }
        return out.toString();
    }

    /** {@code /island guide <n>}: chat buttons for players without the bubble. */
    public void chatPick(Player player, String raw) {
        List<Runnable> options = chatReplies.remove(player.getUniqueId());
        int index;
        try {
            index = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            index = -1;
        }
        if (options == null || index < 0 || index >= options.size()) {
            player.sendMessage("§7That conversation moved on. Click " + name() + " again.");
            return;
        }
        options.get(index).run();
    }

    // ------------------------------------------------------------------------------------------------
    // clicks
    // ------------------------------------------------------------------------------------------------

    /** Hook FancyNpcs' interact event (reflection, like every other FancyNPC in Aetherion). */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (fancyHooked || !FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.player() == null || click.name() == null || !click.name().startsWith(NPC_PREFIX)) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Player player = click.player();
                    String name = click.name();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            clicked(player, name);
                        }
                    });
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Guide click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.NORMAL, executor, plugin, true);
            fancyHooked = true;
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs interact event missing: the island guide can't be clicked.");
        }
    }

    private void clicked(Player player, String npcName) {
        Hand hand = null;
        for (Hand candidate : hands.values()) {
            if (candidate.npcName.equals(npcName)) {
                hand = candidate;
                break;
            }
        }
        if (hand == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = clickCool.get(player.getUniqueId());
        if (cool != null && now - cool < 600L) {
            return;
        }
        clickCool.put(player.getUniqueId(), now);
        if (IslandUi.advance(player, hand.speakerId)) {
            return; // paged talk with him: the click goes on to his next line
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            if (talk.recentlyPicked(player)) {
                return;
            }
            if (talk.hasReplies(player, hand.speakerId)) {
                talk.pulse(player);
                return;
            }
        }
        if (!hand.owner.equals(player.getUniqueId())) {
            String owner = Bukkit.getOfflinePlayer(hand.owner).getName();
            speak(player, hand, List.of(Say.german(player)
                    ? pick("Tag. Ich hüte diese Insel für " + (owner == null ? "ihren Besitzer" : owner) + ". Pass am Rand auf.",
                    "Schöne Insel, was? Die Hütte gerade zu kriegen hat gedauert.",
                    "Zu Besuch? Fass die Bänder nicht an, die beißen.")
                    : pick("Afternoon. I keep this isle for " + (owner == null ? "its owner" : owner) + ". Mind the edge.",
                    "Nice isle, eh? Took us a while to get the hut straight.",
                    "Visiting? Don't touch the belts, they bite.")), List.of());
            return;
        }
        talkTo(player, hand);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        chats.remove(id);
        chatReplies.remove(id);
        clickCool.remove(id);
        landOffered.remove(id);
        wrongIsland.remove(id);
        lastReplies.remove(id);
    }

    // ------------------------------------------------------------------------------------------------
    // conversations
    // ------------------------------------------------------------------------------------------------

    /** The owner clicked him. */
    private void talkTo(Player player, Hand hand) {
        hand.waiting = null;
        hand.offered = true;
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        advance(host, state);
        if (state.step.actTwo() && (state.kits & CHAIN_BEAT) == 0) {
            chainBeat(player, hand); // clicked him before the first block landed: the beat first
            return;
        }
        if (needsTalk(state)) {
            brief(player, hand, false);
        } else if (state.step.touring()) {
            remind(player, hand);
        } else {
            yardTalk(player, hand);
        }
    }

    /**
     * He explains the current step and hands over its part: on the owner's click, or as his reaction right after the
     * task before it ({@code reaction}: he opens with a word on what was just done). Too far for his bubble, nothing
     * is explained and nothing handed over: it waits until the owner comes over.
     */
    private void brief(Player player, Hand hand, boolean reaction) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        TalkAccess talk = AetherServices.talk();
        if (talk != null && talk.bubbles(player) && tooFar(player, hand, talk)) {
            ring(player, hand, () -> brief(player, hand, reaction));
            return;
        }
        switch (state.step) {
            case WELCOME -> greet(player, hand);
            case HUB -> hubBrief(player, hand, host, state);
            case HUT -> hutBrief(player, hand, host, state, reaction);
            case QUARRY -> quarryBrief(player, hand, host, state, reaction);
            case BELT -> beltBrief(player, hand, host, state, reaction);
            case MILL -> millBrief(player, hand, host, state, reaction);
            case SPLIT -> splitBrief(player, hand, host, state, reaction);
            case FEED -> feedBrief(player, hand, host, state);
            case OUTPUT -> outputBrief(player, hand, host, state, reaction);
            case DOCK -> dockBrief(player, hand, host, state, reaction);
            case SHIP -> shipBrief(player, hand, host, state);
            case CRAFT -> craftBrief(player, hand, host, state, reaction);
            case LAND -> landBrief(player, hand, host, state, reaction);
            default -> yardTalk(player, hand);
        }
    }

    /**
     * A step was just reached: he reacts. Standing by him, the owner hears it at once (after {@code delay} ticks, so
     * the moment before has room); further away it waits, marked over his head, until they come over.
     */
    private void react(Player owner, Hand hand, Step step, long delay) {
        Runnable go = () -> {
            if (!owner.isOnline()) {
                return;
            }
            State state = state(hand.owner);
            if (state.step != step || !needsTalk(state)) {
                return;
            }
            TalkAccess talk = AetherServices.talk();
            if (talk != null && talk.bubbles(owner) && tooFar(owner, hand, talk)) {
                ring(owner, hand, () -> brief(owner, hand, true));
                return;
            }
            if (talk != null && talk.talkingWith(owner, hand.speakerId)) {
                talk.end(owner); // what he was saying about the step before is done: his reaction takes its place
            }
            chats.remove(owner.getUniqueId());
            hand.offered = true;
            brief(owner, hand, true);
        };
        if (delay <= 0L) {
            go.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, go, delay);
        }
    }

    /**
     * He has something to say but the owner is too far for his bubble: one soft bell, the gold mark over his head and
     * the bar's "talk to him" (never a line in chat). What he has to say waits until they come over or click him.
     */
    private void ring(Player player, Hand hand, Runnable then) {
        hand.waiting = then;
        hand.offered = false;
        long now = System.currentTimeMillis();
        if (now - hand.rang < 45_000L) {
            return;
        }
        hand.rang = now;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.5f);
    }

    /** The owner clicks him again while a task is open: the task in one line, its part back in the tool, the ways out. */
    private void remind(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        retool(player, host, state);
        String task = objective(hand.owner, player);
        List<TalkAccess.Reply> replies = new ArrayList<>();
        replies.add(reply(t(player, "Explain it again", "Erklär's nochmal"), NamedTextColor.GOLD, () -> {
            state(hand.owner).briefed = null;
            brief(player, hand, false);
        }));
        if (state.step == Step.CRAFT) {
            replies.add(reply(t(player, "Skip this step", "Diesen Schritt überspringen"), NamedTextColor.GRAY, () -> {
                State now = state(hand.owner);
                if (now.step == Step.CRAFT) {
                    now.step = land == null ? Step.DONE : Step.LAND;
                    now.briefed = null;
                    dirty = true;
                    talkTo(player, hand);
                }
            }));
        }
        replies.add(reply(t(player, "Skip tour", "Tour überspringen"), NamedTextColor.DARK_GRAY, () -> skip(player, hand)));
        speak(player, hand, List.of(t(player, "Still open: ", "Noch offen: ")
                + (task == null ? "" : task.replaceAll("§.", "")) + "."), replies);
    }

    /** The current step's part back into the Build Tool (the owner put it away, rejoined, or switched to something else). */
    private void retool(Player player, IslandHost host, State state) {
        if (state.step == Step.HUT && lessonPending(state)) {
            if (!placement.holdingTool(player) || placement.part(player) == null) {
                placement.openToolQuiet(player, host, PlacementService.Part.BELT);
            }
            placement.coach(player, coach(player));
            return;
        }
        PlacementService.Part want = switch (state.step) {
            case HUB -> PlacementService.Part.HUB;
            case HUT -> PlacementService.Part.STORAGE_HUT;
            case QUARRY -> PlacementService.Part.QUARRY;
            case BELT, FEED, OUTPUT -> PlacementService.Part.BELT;
            case MILL -> PlacementService.Part.MILL;
            case SPLIT -> PlacementService.Part.SPLITTER;
            case DOCK -> sub(host, StructureType.DOCK) == 1 ? PlacementService.Part.DOCK : null;
            case CRAFT -> hasQuarryItem(player) ? PlacementService.Part.QUARRY : null;
            default -> null;
        };
        if (want == null) {
            return;
        }
        if (state.step == Step.QUARRY) {
            spareQuarry(player, host, state);
        }
        if (want == PlacementService.Part.QUARRY) {
            handQuarry(player, false);
        }
        if (!placement.holdingTool(player) || placement.part(player) != want) {
            placement.openTool(player, host, want);
        }
    }

    private void greet(Player player, Hand hand) {
        // Idempotent: never restart welcome mid-bubble (that re-queued line 1 and felt like a double dialog).
        if (chats.containsKey(player.getUniqueId())) {
            return;
        }
        TalkAccess existing = AetherServices.talk();
        if (existing != null && existing.talkingWith(player, hand.speakerId)) {
            return;
        }
        welcome(player, hand);
    }

    private void welcome(Player player, Hand hand) {
        State state = state(hand.owner);
        state.introduced = true;
        dirty = true;
        IslandHost host = IslandHost.personal(hand.owner);
        if (tourAlreadyDone(host)) {
            state.step = Step.HUB;
            advance(host, state);
            if (state.step == Step.DONE) {
                speak(player, hand, List.of(t(player,
                                "Oi! I'm " + name() + ". Hub, quarry, Mill, the lot: you don't need me. Nice work.",
                                "Moin! Ich bin " + name() + ". Hub, Steinbruch, Mühle, alles da: mich brauchst du nicht."
                                        + " Saubere Arbeit.")),
                        List.of(reply(t(player, "What's next?", "Was kommt als Nächstes?"), NamedTextColor.GREEN,
                                        () -> tip(player, hand)),
                                reply(t(player, "Carry on", "Weiter so"), NamedTextColor.GRAY, null)));
                return;
            }
            speak(player, hand, List.of(t(player,
                            "Oi! I'm " + name() + ". Your quarry's already running. I'll show you the rest.",
                            "Moin! Ich bin " + name() + ". Dein Steinbruch läuft ja schon. Ich zeig dir den Rest.")),
                    List.of(reply(t(player, "Show me", "Zeig's mir"), NamedTextColor.GREEN, () -> brief(player, hand, false)),
                            reply(t(player, "I know the drill", "Kenn ich schon"), NamedTextColor.GRAY,
                                    () -> skip(player, hand))));
            return;
        }
        List<TalkAccess.Reply> replies = new ArrayList<>();
        replies.add(reply(t(player, "Let's go", "Los geht's"), NamedTextColor.GREEN, () -> startTour(player, hand)));
        replies.add(reply(t(player, "I know the drill", "Kenn ich schon"), NamedTextColor.GRAY, () -> skip(player, hand)));
        if (untouched(host) && wrongIsland.getOrDefault(player.getUniqueId(), 0) < 2) {
            // the gate is the whole choice now, so a wrong step can still be taken back while nothing stands
            replies.add(reply(t(player, "Wrong island?", "Falsche Insel?"), NamedTextColor.DARK_GRAY,
                    () -> wrongIsland(player, hand)));
        }
        if (state.welcomeSpoken) {
            // Already said the lines this session: offer the choices, don't replay line 1.
            speak(player, hand, List.of(t(player, "Ready when you are. Shall we start?",
                    "Bereit, wenn du es bist. Fangen wir an?")), replies);
            return;
        }
        state.welcomeSpoken = true;
        speak(player, hand, lines(player,
                List.of("Oi, welcome! I'm " + name() + ", your isle hand. This isle is your factory now.",
                        "Quarries dig, belts carry, machines press. I'll show you the loop, one step at a time."),
                List.of("Moin, willkommen! Ich bin " + name() + ", dein Inselhelfer. Diese Insel ist jetzt deine Fabrik.",
                        "Steinbrüche graben, Bänder tragen, Maschinen pressen. Ich zeig dir den Kreislauf, Schritt für"
                                + " Schritt.")), replies);
    }

    private void startTour(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.step = Step.HUB;
        state.briefed = null;
        dirty = true;
        advance(host, state);
        brief(player, hand, false);
    }

    /** How often each player went back to the gates this session (a courtesy, not a carousel). */
    private final Map<UUID, Integer> wrongIsland = new HashMap<>();

    /** Nothing built, nothing dug, no belt: the island is still exactly as it rose. */
    private boolean untouched(IslandHost host) {
        return structures.of(host).isEmpty() && hosts.minions(host).isEmpty() && logistics.count(host) == 0;
    }

    private void wrongIsland(Player player, Hand hand) {
        speak(player, hand, List.of(t(player,
                        "No harm done, nothing's built yet. I can take you back to the three gates. Sure?",
                        "Kein Problem, noch steht ja nichts. Ich bring dich zurück zu den drei Toren. Sicher?")),
                List.of(reply(t(player, "Yes, back to the gates", "Ja, zurück zu den Toren"), NamedTextColor.GOLD,
                                () -> backToGates(player)),
                        reply(t(player, "No, I'll stay", "Nein, ich bleibe"), NamedTextColor.GREEN,
                                () -> welcome(player, hand))));
    }

    /** The untouched island is given back and the player stands in front of the three gates again. */
    private void backToGates(Player player) {
        UUID id = player.getUniqueId();
        if (!untouched(IslandHost.personal(id))) {
            return;
        }
        wrongIsland.merge(id, 1, Integer::sum);
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(player);
        }
        chats.remove(id);
        placement.cancel(player, null);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && personal.wipe(id, true)) {
                player.sendTitle(t(player, "§6✦ Back at the gates ✦", "§6✦ Zurück an den Toren ✦"),
                        t(player, "§7Walk through the one you want", "§7Geh durch das, das du willst"), 10, 60, 20);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f);
            }
        });
    }

    private void skip(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.step = Step.SKIPPED;
        state.kits |= TOOL_GIVEN;
        dirty = true;
        placement.coach(player, null);
        if (!state.quarryGiven && firstHousing(host) == null && hosts.minions(host).isEmpty()) {
            // skipping must never cost the starter quarry (without it there is no first chain)
            state.quarryGiven = true;
            giveQuarry(player);
        }
        if ((state.kits & KIT) == 0) {
            state.kits |= KIT;
            state.freeTiles = Math.max(state.freeTiles, kitTiles());
        }
        if ((state.kits & REFINE_KIT) == 0) {
            // ...nor the Mill and the Splitter the tour would have handed over
            state.kits |= REFINE_KIT;
            unlocker.accept(host, StructureType.MILL);
            unlocker.accept(host, StructureType.SPLITTER);
        }
        placement.openTool(player, host, null);
        speak(player, hand, List.of(t(player,
                "Fair enough. Here's your Build Tool, and the bar up top shows what's next. Changed your mind? Talk to me.",
                "Alles klar. Hier ist dein Bauwerkzeug, und die Leiste oben zeigt, was dran ist. Anders überlegt?"
                        + " Sprich mich an.")), List.of());
    }

    // ------------------------------------------------------------------------------------------------
    // step 1, your base: the Build Tool, the Hub, then the hut beside it (the one Build Tool lesson)
    // ------------------------------------------------------------------------------------------------

    private void hubBrief(Player player, Hand hand, IslandHost host, State state) {
        PlacedStructure hub = structures.hub(host);
        if (hub != null && hub.building()) {
            speak(player, hand, List.of(t(player, "Hub's going up. Wait a beat.", "Der Hub entsteht. Einen Moment.")),
                    List.of());
            return;
        }
        briefed(state);
        boolean first = (state.kits & TOOL_GIVEN) == 0;
        if (first) {
            state.kits |= TOOL_GIVEN;
            dirty = true;
            toolCard(player);
        }
        placement.openTool(player, host, PlacementService.Part.HUB);
        speak(player, hand, first
                ? lines(player,
                List.of("That's your Build Tool. Everything on this isle is built with it, and right now it holds your Hub.",
                        "Aim at flat ground and right-click to build. Left-click turns it. The Hub is the heart of"
                                + " your isle."),
                List.of("Das ist dein Bauwerkzeug (Build Tool). Alles auf dieser Insel baust du damit, gerade hält es"
                                + " deinen Hub.",
                        "Ziel auf ebenen Boden, Rechtsklick baut. Linksklick dreht. Der Hub ist das Herz deiner Insel."))
                : List.of(t(player, "Your Hub: aim at flat ground and right-click. Left-click turns it.",
                "Dein Hub: auf ebenen Boden zielen, Rechtsklick. Linksklick dreht.")), List.of());
    }

    /** The Build Tool, handed over: what it does, how it is put away, and that it can never get lost. */
    private void toolCard(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        String summary = t(player, "§6✦ §eFrom " + name() + " §8· §7your Build Tool §8(§fQ §7puts it away, the Hub hands it back§8)",
                "§6✦ §eVon " + name() + " §8· §7dein Bauwerkzeug §8(§fQ §7legt es weg, der Hub gibt es wieder§8)");
        boolean card = IslandUi.big(player, t(player, "From " + name(), "Von " + name()),
                t(player, "Your Build Tool", "Dein Bauwerkzeug"),
                lines(player,
                        List.of("§fRight-click §7builds, §fleft-click §7turns",
                                "§fSneak + scroll §7switches the part, §fF §7shows all",
                                "§fQ §7puts it away",
                                "§7Gone? Your §fHub §7and §f" + name() + " §7hand it back"),
                        List.of("§fRechtsklick §7baut, §fLinksklick §7dreht",
                                "§fSchleichen + Mausrad §7wechselt das Teil, §fF §7zeigt alle",
                                "§fQ §7legt es weg",
                                "§7Weg? Dein §fHub §7und §f" + name() + " §7geben es wieder")),
                Arrays.asList(new ItemStack(Material.BRICKS), new ItemStack(Material.COMPASS),
                        new ItemStack(Material.CHEST), new ItemStack(Material.LECTERN)),
                t(player, "§7It holds your Hub right now", "§7Gerade hält es deinen Hub"), "§6", summary,
                new ItemStack(Material.NETHERITE_PICKAXE));
        if (!card) {
            player.sendMessage(summary);
        }
    }

    private void hutBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(t(player, "There she stands! I'll keep my post right here, beside the lectern.",
                    "Da steht er! Ich bleib ab jetzt hier, gleich neben dem Lesepult."));
            out.add(t(player, "Ever lose your Build Tool? The lectern hands it back, and so do I.",
                    "Bauwerkzeug mal weg? Das Lesepult gibt es dir wieder, und ich auch."));
        }
        if (lessonPending(state) && placement.holdingTool(player)
                && placement.part(player) == PlacementService.Part.STORAGE_HUT) {
            taught(player, state); // got there on their own before he asked
        }
        if (!lessonPending(state)) {
            placement.openTool(player, host, PlacementService.Part.STORAGE_HUT);
            out.add(t(player, "Next: storage. Set your Storage Hut right next to the Hub: everything you make lands in it.",
                    "Als Nächstes: Lager. Setz deine Lagerhütte (Storage Hut) direkt neben den Hub: alles, was du"
                            + " herstellst, landet darin."));
            speak(player, hand, out, List.of());
            return;
        }
        if (!placement.holdingTool(player) || placement.part(player) == null) {
            placement.openToolQuiet(player, host, PlacementService.Part.BELT);
        }
        placement.coach(player, coach(player));
        out.add(t(player, "Next: storage. Everything your isle makes lands in a Storage Hut.",
                "Als Nächstes: Lager. Alles, was deine Insel herstellt, landet in einer Lagerhütte (Storage Hut)."));
        out.add(t(player, "Your tool holds one part at a time. Hold sneak and scroll the mouse wheel: switch it to the"
                        + " Storage Hut.",
                "Dein Werkzeug hält immer ein Teil. Halte Schleichen und dreh das Mausrad: stell es auf Storage Hut."));
        speak(player, hand, out, List.of(reply(t(player, "Switch it for me", "Stell es für mich um"),
                NamedTextColor.GOLD, () -> switchForThem(player, hand))));
    }

    /** The tool's action bar while the lesson waits for the switch. */
    private static String coach(Player player) {
        return t(player, "§e⇅ §fHold sneak §7and §fscroll the wheel §8→ §7switch to the §eStorage Hut",
                "§e⇅ §fSchleichen halten §7und §fMausrad drehen §8→ §7auf §eStorage Hut §7stellen");
    }

    /** The lesson waits: the Hub stands, the hut not yet, and the player never switched parts themselves. */
    private boolean lessonPending(State state) {
        return state != null && state.step == Step.HUT && (state.kits & TOOL_TAUGHT) == 0;
    }

    private void taught(Player player, State state) {
        if ((state.kits & TOOL_TAUGHT) == 0) {
            state.kits |= TOOL_TAUGHT;
            dirty = true;
        }
        placement.coach(player, null);
    }

    /** "Switch it for me": he does it, and still says how it goes next time. */
    private void switchForThem(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        taught(player, state(hand.owner));
        placement.openTool(player, host, PlacementService.Part.STORAGE_HUT);
        speak(player, hand, List.of(t(player,
                "There. Next time it's yours: sneak and scroll, or F for every part. Now set it next to your Hub.",
                "Bitte. Nächstes Mal machst du es: Schleichen und Mausrad, oder F für alle Teile. Jetzt setz sie"
                        + " neben deinen Hub.")), List.of());
    }

    /** The player switched the tool's part by hand (sneak + scroll, the picker): the lesson's moment. */
    private void switched(Player player, PlacementService.Part part) {
        if (player == null || part != PlacementService.Part.STORAGE_HUT || !available()) {
            return;
        }
        State state = states.get(player.getUniqueId());
        IslandHost host = hosts.at(player.getLocation());
        if (!lessonPending(state) || host == null || host.isGuild() || !host.id().equals(player.getUniqueId())) {
            return;
        }
        taught(player, state);
        Hand hand = hands.get(player.getUniqueId());
        TalkAccess talk = AetherServices.talk();
        if (talk != null && hand != null && talk.talkingWith(player, hand.speakerId)) {
            talk.end(player); // "switch it to the Storage Hut" is done: the card says so
        }
        chats.remove(player.getUniqueId());
        if (!IslandUi.toast(player, t(player, "Switched to the Storage Hut", "Auf Storage Hut gestellt"),
                t(player, "§7Sneak + scroll switches · §fF §7shows all · §fQ §7puts away",
                        "§7Schleichen + Mausrad wechselt · §fF §7zeigt alle · §fQ §7legt weg"),
                t(player, "§8Next: §7set it next to your Hub", "§8Weiter: §7setz sie neben deinen Hub"))) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
            player.sendMessage(t(player,
                    "§a✔ §7That's it: §fsneak + scroll §7switches parts, §fF §7shows them all, §fQ §7puts the tool away.",
                    "§a✔ §7Genau so: §fSchleichen + Mausrad §7wechselt Teile, §fF §7zeigt alle, §fQ §7legt das Werkzeug weg."));
        }
    }

    /** "How does it all work?": the whole game in two lines. */
    private void explain(Player player, Hand hand) {
        speak(player, hand, lines(player,
                        List.of("Quarries dig. Machines press 128 of a good into one block worth far more.",
                                "Belts carry it from an orange chute to a green pad. Raw stone builds, pressed stone ships."),
                        List.of("Steinbrüche graben. Maschinen pressen 128 einer Ware zu einem Block, der viel mehr wert ist.",
                                "Bänder tragen sie von einer orangen Rutsche zu einem grünen Feld. Roher Stein baut,"
                                        + " gepresster wird verschifft.")),
                List.of(reply(t(player, "Got it", "Verstanden"), NamedTextColor.GRAY, null)));
    }

    // ------------------------------------------------------------------------------------------------
    // step 2, goods flow: his quarry, set down; the two colours; the first belt, quarry into hut
    // ------------------------------------------------------------------------------------------------

    /**
     * The quarry's kit, once per island: the quarry into the hand and free belt tiles for the whole first chain.
     * True when it was handed over just now.
     */
    private boolean ensureKit(Player player, IslandHost host, State state) {
        if ((state.kits & KIT) != 0) {
            return false;
        }
        state.kits |= KIT;
        state.freeTiles = Math.max(state.freeTiles, kitTiles());
        if (!state.quarryGiven && firstHousing(host) == null) {
            state.quarryGiven = true;
            handQuarry(player, true);
        }
        dirty = true;
        return true;
    }

    /** Lost it (dropped, despawned)? He has one spare, once. Never a dead end. */
    private void spareQuarry(Player player, IslandHost host, State state) {
        if (firstHousing(host) != null || (state.kits & SPARE_QUARRY) != 0 || hasQuarryItem(player)) {
            return;
        }
        state.kits |= SPARE_QUARRY;
        dirty = true;
        handQuarry(player, true);
        player.sendMessage(t(player, "§7" + name() + " hands you his spare quarry.",
                "§7" + name() + " gibt dir seinen Ersatz-Steinbruch."));
    }

    private void quarryBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        boolean fresh = ensureKit(player, host, state);
        if (fresh) {
            kitCard(player);
        } else {
            spareQuarry(player, host, state);
        }
        handQuarry(player, false);
        placement.openTool(player, host, PlacementService.Part.QUARRY);
        List<String> out = new ArrayList<>();
        if (fresh) {
            out.add(reaction
                    ? t(player, "Good spot. Now something to fill it: your first quarry.",
                    "Guter Platz. Jetzt was zum Füllen: dein erster Steinbruch.")
                    : t(player, "Here: your first quarry.", "Hier: dein erster Steinbruch."));
            out.add(t(player, "It digs Cobblestone on its own, day and night, and the belts for it are on me.",
                    "Er gräbt von allein Bruchstein, Tag und Nacht, und die Bänder dazu gehen auf mich."));
        }
        out.add(t(player, "Set it down a few blocks from the hut. The orange mark on its hologram is its chute:"
                        + " left-click turns it toward the hut.",
                "Stell ihn ein paar Blöcke neben die Hütte. Die orange Marke am Hologramm ist seine Rutsche:"
                        + " Linksklick dreht sie zur Hütte."));
        speak(player, hand, out, List.of());
    }

    /** The quarry's kit as a card. */
    private void kitCard(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        String summary = t(player, "§6✦ §eFrom " + name() + " §8· §7a Cobble Quarry and " + kitTiles() + " belt tiles",
                "§6✦ §eVon " + name() + " §8· §7ein Cobble Quarry und " + kitTiles() + " Bandstücke");
        boolean card = IslandUi.big(player, t(player, "From " + name(), "Von " + name()),
                t(player, "Your first quarry", "Dein erster Steinbruch"),
                lines(player,
                        List.of("§fCobble Quarry §7digs stone on its own",
                                "§f" + kitTiles() + " belt tiles §7carry it off"),
                        List.of("§fCobble Quarry §7gräbt von allein Stein",
                                "§f" + kitTiles() + " Bandstücke §7tragen ihn weg")),
                Arrays.asList(new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.IRON_TRAPDOOR)),
                t(player, "§7Quarries are built from goods. This one is on me.",
                        "§7Steinbrüche baut man aus Waren. Der hier geht auf mich."), "§6", summary, null);
        if (!card) {
            player.sendMessage(summary);
        }
    }

    private void beltBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        if ((state.kits & COLOURS) == 0) {
            state.kits |= COLOURS;
            dirty = true;
            coloursCard(player);
        }
        placement.startBelts(player, host);
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(t(player, "It's digging! But the stone stays inside until a belt carries it off.",
                    "Er gräbt! Aber der Stein bleibt drin, bis ein Band ihn abholt."));
        }
        out.add(t(player, "Two colours, that's all: orange is where goods come OUT, green is where they go IN.",
                "Zwei Farben, mehr nicht: Orange heißt, hier kommen Waren RAUS. Grün heißt, hier gehen sie REIN."));
        out.add(t(player, "Right-click the quarry's orange chute, then the hut's green pad. The belt lays itself in"
                        + " between.",
                "Rechtsklick auf die orange Rutsche am Steinbruch, dann aufs grüne Feld der Hütte. Das Band legt"
                        + " sich dazwischen."));
        speak(player, hand, out, List.of(reply(t(player, "Show me", "Zeig's mir"), NamedTextColor.GOLD,
                () -> outline(player, firstHousing(host), firstHut(host), 6))));
    }

    /** The one rule of every belt, as a card: orange = out, green = in. */
    private void coloursCard(Player player) {
        String summary = t(player, "§6✦ §eBelts §8· §6orange §7= goods come out, §agreen §7= goods go in",
                "§6✦ §eBänder §8· §6Orange §7= Waren kommen raus, §aGrün §7= Waren gehen rein");
        boolean card = IslandUi.big(player, t(player, "How belts work", "So laufen Bänder"),
                t(player, "Orange out, green in", "Orange raus, Grün rein"),
                lines(player,
                        List.of("§6Orange §7= goods come §fOUT §7(chutes)",
                                "§aGreen §7= goods go §fIN §7(huts, machines)",
                                "§7A belt runs from §6orange §7to §agreen"),
                        List.of("§6Orange §7= Waren kommen §fRAUS §7(Rutschen)",
                                "§aGrün §7= Waren gehen §fREIN §7(Hütten, Maschinen)",
                                "§7Ein Band läuft von §6Orange §7nach §aGrün")),
                Arrays.asList(new ItemStack(Material.ORANGE_CONCRETE), new ItemStack(Material.LIME_CONCRETE),
                        new ItemStack(Material.IRON_TRAPDOOR)),
                t(player, "§7Right-click the orange chute, then the green pad",
                        "§7Rechtsklick auf die orange Rutsche, dann aufs grüne Feld"), "§6", summary,
                new ItemStack(Material.ORANGE_CONCRETE));
        if (!card) {
            player.sendMessage(summary);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // step 3, split and refine: his Mill and Splitter; the Mill beside the belt, the Splitter cut into it and its
    // side belted into the Mill, the Mill belted into the hut
    // ------------------------------------------------------------------------------------------------

    private void millBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        boolean fresh = (state.kits & REFINE_KIT) == 0;
        if (fresh) {
            state.kits |= REFINE_KIT;
            dirty = true;
            unlocker.accept(host, StructureType.MILL);
            unlocker.accept(host, StructureType.SPLITTER);
            refineCard(player);
        }
        placement.openTool(player, host, PlacementService.Part.MILL);
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(t(player, "There it goes: stone on the belt, and your hut counts it. That Cobble pays for"
                            + " everything you build.",
                    "Läuft: Stein auf dem Band, und deine Hütte zählt mit. Mit dem Bruchstein bezahlst du alles, was"
                            + " du baust."));
        }
        if (fresh) {
            out.add(t(player, "Pressed, it's worth far more: a Mill turns 128 stones into one Compressed block. Mill"
                            + " and Splitter, both on me.",
                    "Gepresst ist er viel mehr wert: eine Mühle (Mill) macht aus 128 Steinen einen Compressed-Block."
                            + " Mühle und Splitter, beides geschenkt."));
        }
        out.add(t(player, "Set the Mill down beside your belt, a few blocks off it.",
                "Stell die Mühle neben dein Band, ein paar Blöcke daneben."));
        speak(player, hand, out, List.of());
    }

    /** The Mill and the Splitter as a card. */
    private void refineCard(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        String summary = t(player, "§6✦ §eFrom " + name() + " §8· §7a Mill and a Splitter",
                "§6✦ §eVon " + name() + " §8· §7eine Mühle (Mill) und ein Splitter");
        boolean card = IslandUi.big(player, t(player, "From " + name(), "Von " + name()),
                t(player, "Mill and Splitter", "Mühle und Splitter"),
                lines(player,
                        List.of("§fMill §7presses 128 Cobble into 1 Compressed",
                                "§fSplitter §7shares one belt between two takers"),
                        List.of("§fMill §7presst 128 Cobble zu 1 Compressed",
                                "§fSplitter §7teilt ein Band auf zwei Abnehmer")),
                Arrays.asList(new ItemStack(Material.GRINDSTONE), new ItemStack(Material.WAXED_COPPER_GRATE)),
                t(player, "§7Raw stone builds, pressed stone ships", "§7Roher Stein baut, gepresster wird verschifft"),
                "§6", summary, null);
        if (!card) {
            player.sendMessage(summary);
        }
    }

    private void splitBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        placement.openTool(player, host, PlacementService.Part.SPLITTER);
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(t(player, "Now the Mill needs stone, and the hut needs stone too. One belt, two takers: that's the"
                            + " Splitter's job.",
                    "Jetzt braucht die Mühle Stein, die Hütte aber auch. Ein Band, zwei Abnehmer: dafür ist der"
                            + " Splitter da."));
        }
        out.add(t(player, "Right-click your running belt and the Splitter cuts in. Nothing gets torn down.",
                "Rechtsklick auf dein laufendes Band, und der Splitter setzt sich hinein. Nichts wird abgerissen."));
        out.add(t(player, "Then its free sides glow orange: belt one of them into the Mill's green pad.",
                "Danach leuchten seine freien Seiten orange: leg von einer ein Band ins grüne Feld der Mühle."));
        speak(player, hand, out, List.of());
    }

    private void feedBrief(Player player, Hand hand, IslandHost host, State state) {
        briefed(state);
        PlacedStructure splitter = firstOf(host, StructureType.SPLITTER);
        placement.startBelts(player, host);
        speak(player, hand, List.of(t(player,
                        "Belt an orange side of your Splitter into the Mill's green pad. It shares the stone evenly.",
                        "Leg ein Band von einer orangen Seite deines Splitters ins grüne Feld der Mühle. Er teilt den"
                                + " Stein gleichmäßig.")),
                List.of(reply(t(player, "Show me", "Zeig's mir"), NamedTextColor.GOLD,
                        () -> outline(player, splitter, lineMachine(host, StructureType.MILL), 6))));
    }

    private void outputBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        placement.startBelts(player, host);
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(state.primed
                    ? t(player, "It's grinding, and I tipped a starter load in so you don't wait.",
                    "Sie mahlt, und ich hab eine Startladung reingekippt, damit du nicht wartest.")
                    : t(player, "It's grinding!", "Sie mahlt!"));
        }
        out.add(t(player, "Last belt: from the Mill's orange chute into your hut. Then the first Compressed block rolls in.",
                "Letztes Band: von der orangen Rutsche der Mühle in deine Hütte. Dann rollt der erste Compressed-Block"
                        + " ein."));
        speak(player, hand, out, List.of(reply(t(player, "Show me", "Zeig's mir"), NamedTextColor.GOLD,
                () -> outline(player, lineMachine(host, StructureType.MILL), firstHut(host), 6))));
    }

    private PlacedStructure firstOf(IslandHost host, StructureType type) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == type) {
                return structure;
            }
        }
        return null;
    }

    /**
     * The first chain runs and the first Compressed Cobble lies in the hut: the beat (card, sound, a puff on the
     * hut). Then he has the next step: the Dock, at the Hub's lectern.
     */
    private void chainBeat(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.kits |= CHAIN_BEAT;
        dirty = true;
        boolean card = IslandUi.big(player, t(player, "Island milestone", "Insel-Meilenstein"),
                t(player, "First chain running", "Die erste Kette läuft"),
                lines(player,
                        List.of("§fQuarry §7digs the stone",
                                "§fSplitter §7shares it: raw into the hut, the rest to the Mill",
                                "§fMill §7presses 128 into one block",
                                "§7Wrong spot? §fPick up §7gives back everything"),
                        List.of("§fSteinbruch §7gräbt den Stein",
                                "§fSplitter §7teilt ihn: roh in die Hütte, der Rest zur Mühle",
                                "§fMühle §7presst 128 zu einem Block",
                                "§7Falscher Platz? §fPick up §7gibt alles zurück")),
                Arrays.asList(new ItemStack(Material.COBBLESTONE), new ItemStack(Material.WAXED_COPPER_GRATE),
                        new ItemStack(Material.GRINDSTONE), new ItemStack(Material.BARREL)),
                t(player, "§7Next: the Dock, from your Hub", "§7Weiter: das Dock, an deinem Hub"), "§b",
                t(player, "§b✦ §fFirst chain running §8· §7quarry → Splitter → Mill → hut",
                        "§b✦ §fDie erste Kette läuft §8· §7Steinbruch → Splitter → Mühle → Hütte"), null);
        if (!card) {
            player.sendTitle("§b✦ First Chain Running ✦", "§7quarry §8→ §7Splitter §8→ §7Mill §8→ §7hut", 10, 70, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            }
        }, 10L);
        PlacedStructure hut = firstHut(host);
        World world = hosts.world(host);
        if (hut != null && world != null) {
            Location top = new Location(world, hut.centerX(), hut.maxY() + 1.0, hut.centerZ());
            de.aetherion.core.api.Vfx.puff(top, Particle.TOTEM_OF_UNDYING, 50, Math.max(0.8, 0.8), null);
            de.aetherion.core.api.Vfx.puff(top, Particle.END_ROD, 20, Math.max(0.6, 0.6), null);
        }
        react(player, hand, state.step, 0L);
    }

    // ------------------------------------------------------------------------------------------------
    // step 4, ship it: the Dock (the first unlock at the Hub), then the First Shipment and the airship
    // ------------------------------------------------------------------------------------------------

    /** The Dock: the lectern and how new parts come (it costs the Compressed Cobble just made), then build it. */
    private void dockBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        int sub = sub(host, StructureType.DOCK);
        state.lastSub = sub;
        if (sub == 0) {
            long have = compressedInStorage(host);
            long price = Math.max(1L, dockPrice(host));
            List<String> out = new ArrayList<>();
            if (reaction) {
                out.add(have > 0L
                        ? t(player, "There it is: your first Compressed Cobble, 128 stones in one block.",
                        "Da ist er: dein erster Compressed Cobble, 128 Steine in einem Block.")
                        : t(player, "There. The whole line runs.", "So. Die ganze Kette läuft."));
            }
            out.add(t(player, "Raw stone builds, pressed stone ships. For shipping you need a Dock, and new parts come"
                            + " from your Hub.",
                    "Roher Stein baut, gepresster wird verschifft. Dafür brauchst du ein Dock, und neue Teile gibt es"
                            + " an deinem Hub."));
            String cost = price == 1L ? t(player, "one Compressed block", "einen Compressed-Block")
                    : price + t(player, " Compressed blocks", " Compressed-Blöcke");
            out.add(t(player, "Open the lectern and unlock the Dock: it costs " + cost
                            + (have >= price ? ", the one you just made" : ", your Mill is on it") + ". Building it is on me.",
                    "Öffne das Lesepult und schalte das Dock frei: es kostet " + cost
                            + (have >= price ? ", genau den von eben" : ", deine Mühle ist dran") + ". Das Bauen geht auf mich."));
            speak(player, hand, out, List.of(reply(t(player, "Open the lectern", "Lesepult öffnen"),
                    NamedTextColor.GREEN, () -> openLectern.accept(player, host))));
            return;
        }
        if (sub == 1) {
            placement.openTool(player, host, PlacementService.Part.DOCK);
            speak(player, hand, List.of(t(player, "Unlocked, and building it is on me. Near the island's edge looks best.",
                    "Freigeschaltet, und das Bauen geht auf mich. Am Inselrand sieht es am besten aus.")), List.of());
            return;
        }
        speak(player, hand, List.of(t(player, "Your Dock's going up. Give it a moment.",
                "Dein Dock entsteht. Einen Moment.")), List.of());
    }

    /** The first contract and the airship: the end of stage 0. */
    private void shipBrief(Player player, Hand hand, IslandHost host, State state) {
        briefed(state);
        DockService.Contract first = dock == null ? null : dock.phase(host);
        String order = orderText(player, first);
        goalCard(player, hand);
        speak(player, hand, lines(player,
                        List.of("Your Dock stands. On its board hangs your first order: " + order + ".",
                                "Load it from your hut at the Dock. When it's full, the airship comes, and your isle"
                                        + " reaches the Werkhof."),
                        List.of("Dein Dock steht. An seiner Tafel hängt dein erster Auftrag: " + order + ".",
                                "Belade ihn am Dock aus deiner Hütte. Ist er voll, kommt das Luftschiff, und deine Insel"
                                        + " wird zum Werkhof.")),
                List.of(reply(t(player, "Open the Dock", "Dock öffnen"), NamedTextColor.GREEN,
                                () -> openDock.accept(player, host)),
                        reply(t(player, "What comes later?", "Was kommt später?"), NamedTextColor.GOLD,
                                () -> later(player, hand))));
    }

    /** "1 Compressed Cobble and 64 Cobble" from the contract's needs. */
    private static String orderText(Player player, DockService.Contract contract) {
        if (contract == null || contract.needs().isEmpty()) {
            return t(player, "1 Compressed Cobble and 64 Cobble", "1 Compressed Cobble und 64 Cobble");
        }
        StringBuilder text = new StringBuilder();
        List<DockService.Need> needs = contract.needs();
        for (int i = 0; i < needs.size(); i++) {
            DockService.Need need = needs.get(i);
            text.append(i == 0 ? "" : i == needs.size() - 1 ? t(player, " and ", " und ") : ", ").append(need.amount())
                    .append(" ").append(need.res().form() == Res.Form.RAW ? "Cobble" : need.res().display());
        }
        return text.toString();
    }

    /** The goal you can see: once, when the first order opens, what shipping it brings (the Werkhof). */
    private void goalCard(Player player, Hand hand) {
        State state = state(hand.owner);
        if ((state.kits & GOAL_CARD) != 0) {
            return;
        }
        state.kits |= GOAL_CARD;
        dirty = true;
        String summary = t(player, "§6✦ §eShip the First Shipment §8→ §7Werkhof: the parts of a second quarry, quarries up to Lv3",
                "§6✦ §eErste Lieferung verschiffen §8→ §7Werkhof: Teile für einen zweiten Steinbruch, Steinbrüche bis Lv3");
        boolean card = IslandUi.big(player, t(player, "Your next stage", "Deine nächste Stufe"),
                t(player, "Ship it, get the Werkhof", "Verschiffen, Werkhof werden"),
                lines(player,
                        List.of("§fThe parts of a second quarry §7(you build it)",
                                "§7Quarries up to §fLv3§7, a bigger hut",
                                "§7More belts, one more of every machine"),
                        List.of("§fTeile für einen zweiten Steinbruch §7(du baust ihn)",
                                "§7Steinbrüche bis §fLv3§7, eine größere Hütte",
                                "§7Mehr Bänder, von jeder Maschine eine mehr")),
                Arrays.asList(new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.EXPERIENCE_BOTTLE),
                        new ItemStack(Material.IRON_TRAPDOOR)),
                t(player, "§7Later: the Forge, the Sorter, land of your own",
                        "§7Später: Forge, Sorter, eigenes Land"), "§6", summary, null);
        if (!card) {
            player.sendMessage(summary);
        }
    }

    /** The road ahead, while the first shipment fills: what each stage opens and how you get there. */
    private void later(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        speak(player, hand, lines(player,
                        List.of("Each stage, the broker sends a bigger order. Ship it and the Hub opens the next parts.",
                                "Werkhof: a second quarry and bigger machines. Foreman's Hall: the Forge and the Sorter.",
                                "Grand Hub: every machine twice as fast, and land of your own."),
                        List.of("Jede Stufe schickt der Makler einen größeren Auftrag. Verschiff ihn, und der Hub öffnet"
                                        + " die nächsten Teile.",
                                "Werkhof: ein zweiter Steinbruch und größere Maschinen. Foreman's Hall: Forge und Sorter.",
                                "Grand Hub: jede Maschine doppelt so schnell, und eigenes Land.")),
                List.of(reply(t(player, "Open the Dock", "Dock öffnen"), NamedTextColor.GREEN,
                                () -> openDock.accept(player, host)),
                        reply(t(player, "Got it", "Verstanden"), NamedTextColor.GRAY, null)));
    }

    // ------------------------------------------------------------------------------------------------
    // step 5, build a quarry: the first shipment pays the parts of a second one; the player crafts it, once
    // ------------------------------------------------------------------------------------------------

    /** The trade of this island's second quarry (its starter's: coal, oak or cod). */
    private QuarryType craftType(IslandHost host) {
        return dock == null ? QuarryType.COAL : dock.trade(host);
    }

    /** The Dock handed the second quarry's parts over (a Quarry Core and eight Compressed of the trade's good). */
    public void craftKitGiven(IslandHost host) {
        if (host == null || host.isGuild()) {
            return;
        }
        State state = state(host.id());
        if ((state.kits & CRAFT_KIT) == 0) {
            state.kits |= CRAFT_KIT;
            dirty = true;
        }
    }

    private void craftBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        QuarryType type = craftType(host);
        state.craftSub = hasQuarryItem(player) ? 1 : 0;
        if (state.craftSub == 1) {
            handQuarry(player, false);
            placement.openTool(player, host, PlacementService.Part.QUARRY);
            speak(player, hand, List.of(t(player,
                    "There it is, built by your own hands. Set it down like the first one, then belt it in.",
                    "Da ist er, mit eigenen Händen gebaut. Stell ihn auf wie den ersten und schließ ihn an.")), List.of());
            return;
        }
        List<String> out = new ArrayList<>();
        if (reaction) {
            out.add(t(player, "There she goes! From now on your quarry digs at its own pace, and it keeps digging while"
                            + " you're away.",
                    "Da fliegt es! Ab jetzt gräbt dein Steinbruch in seinem eigenen Tempo, auch wenn du weg bist."));
        }
        out.add(t(player, "The broker paid in parts: a Quarry Core and eight Compressed " + type.productName()
                        + ". That's a quarry, not built yet.",
                "Der Makler hat in Teilen bezahlt: ein Quarry Core und acht Compressed " + type.productName()
                        + ". Das ist ein Steinbruch, nur noch nicht gebaut."));
        out.add(t(player, "Every quarry is made like that: eight Compressed of what it should dig, around a Quarry Core.",
                "So entsteht jeder Steinbruch: acht Compressed von dem, was er graben soll, um einen Quarry Core."));
        out.add(t(player, "It's all in your bag. Open your recipe book and build your " + type.display()
                        + " yourself, this once.",
                "Alles liegt in deiner Tasche. Öffne dein Rezeptbuch und bau deinen " + type.display()
                        + " dieses eine Mal selbst."));
        speak(player, hand, out, List.of(
                reply(t(player, "Open the recipe book", "Rezeptbuch öffnen"), NamedTextColor.GREEN,
                        () -> AetherionItemsAccess.openReadyRecipes(player)),
                reply(t(player, "Later", "Später"), NamedTextColor.GRAY, null)));
    }

    // ------------------------------------------------------------------------------------------------
    // step 6, grow: the first piece of land, a gift
    // ------------------------------------------------------------------------------------------------

    private LandService land;
    private BiConsumer<Player, IslandHost> openLand = (player, host) -> player.performCommand("island land");

    /**
     * Land: the tour ends with one free parcel (before land opens on its own at the Grand Hub), so a player knows
     * where land is bought and sees the ground rise and the border move out with it.
     */
    public void attachLand(LandService land, BiConsumer<Player, IslandHost> openLand) {
        this.land = land;
        if (openLand != null) {
            this.openLand = openLand;
        }
        if (land != null) {
            land.attachGift(host -> host != null && !host.isGuild() && available() && step(host.id()) == Step.LAND
                            && (state(host.id()).kits & LAND_GIFT) == 0,
                    host -> {
                        state(host.id()).kits |= LAND_GIFT;
                        dirty = true;
                    });
        }
    }

    /** Players he has told about the land this session (so a late reminder never repeats the whole speech). */
    private final Set<UUID> landOffered = new HashSet<>();

    private void landBrief(Player player, Hand hand, IslandHost host, State state, boolean reaction) {
        briefed(state);
        boolean first = landOffered.add(player.getUniqueId());
        List<String> out = new ArrayList<>();
        if (reaction && (state.kits & CRAFT_KIT) != 0) {
            out.add(t(player, "That's how every quarry is made: eight Compressed of what it should dig, and a Quarry Core.",
                    "So entsteht jeder Steinbruch: acht Compressed von dem, was er graben soll, und ein Quarry Core."));
            out.add(t(player, "Cores come from Dock orders and from world bosses. Belt this one in like the first.",
                    "Cores gibt es für Dock-Aufträge und von Weltbossen. Schließ ihn an wie den ersten."));
        } else if (reaction) {
            out.add(t(player, "There she goes! From now on your quarry digs at its own pace, and it keeps digging while"
                            + " you're away.",
                    "Da fliegt es! Ab jetzt gräbt dein Steinbruch in seinem eigenen Tempo, auch wenn du weg bist."));
        }
        if (first) {
            out.add(t(player, "One more thing, and it's the best bit: your island can grow. The first piece of land is"
                            + " on me.",
                    "Und noch was, das Beste: deine Insel kann wachsen. Das erste Stück Land geht auf mich."));
            out.add(t(player, "Open your Hub, pick Land, click a green square.",
                    "Öffne deinen Hub, wähle Land, klick ein grünes Feld."));
        } else {
            out.add(t(player, "Your free land: Hub, then Land, a green square next to yours.",
                    "Dein Gratis-Land: Hub, dann Land, ein grünes Feld neben deinem."));
        }
        speak(player, hand, out, List.of(
                reply(t(player, "Show me the land", "Zeig mir das Land"), NamedTextColor.GREEN,
                        () -> openLand.accept(player, host)),
                reply(t(player, "Later", "Später"), NamedTextColor.GRAY, null)));
    }

    /** The gift is taken: the ground is rising, and the tour is over. */
    private void landTaken(Player player, Hand hand) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Runnable talk = () -> speak(player, hand, lines(player,
                            List.of("There it rises, and your border moved out with it. More land opens at the Grand Hub.",
                                    "That's the tour! The bar up top always shows what's next. I'm here if you need a hand."),
                            List.of("Da steigt es auf, und deine Grenze ist mitgewandert. Mehr Land gibt es ab dem Grand Hub.",
                                    "Das war die Tour! Die Leiste oben zeigt immer, was dran ist. Ich bin hier, wenn du"
                                            + " Hilfe brauchst.")),
                    List.of(reply(t(player, "What's next?", "Was kommt als Nächstes?"), NamedTextColor.GREEN,
                                    () -> tip(player, hand)),
                            reply(t(player, "Thanks, " + name(), "Danke, " + name()), NamedTextColor.GRAY, null)));
            TalkAccess access = AetherServices.talk();
            if (access != null && access.bubbles(player) && tooFar(player, hand, access)) {
                ring(player, hand, talk);
            } else {
                talk.run();
            }
        }, 50L);
    }

    /** The tour ended some other way than the ship (an admin stage, an old save): a short goodbye. */
    private void finishTalk(Player player, Hand hand) {
        speak(player, hand, List.of(t(player,
                        "You're set. Quarries dig, machines refine, the Dock ships. The bar up top shows what's next.",
                        "Du bist startklar. Steinbrüche graben, Maschinen veredeln, das Dock verschifft. Die Leiste oben"
                                + " zeigt, was dran ist.")),
                List.of(reply(t(player, "What's next?", "Was kommt als Nächstes?"), NamedTextColor.GREEN,
                        () -> tip(player, hand))));
    }

    private long compressedInStorage(IslandHost host) {
        return structures.stock(host, Res.of(QuarryType.COBBLESTONE, Res.Form.COMPRESSED));
    }

    /** The Mill the tour is about: one that's fed if there is one, else the first. */
    private PlacedStructure lineMachine(IslandHost host, StructureType type) {
        PlacedStructure first = null;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() != type) {
                continue;
            }
            if (first == null) {
                first = structure;
            }
            if (logistics.feeding(structure) > 0) {
                return structure;
            }
        }
        return first;
    }

    /** Orange on {@code from}'s chute, green round {@code to} (either may be null), a few pulses, only on request. */
    private void outline(Player player, PlacedStructure from, PlacedStructure to, int pulses) {
        int[] port = from == null ? null : structures.outputPort(from);
        if (port != null) {
            square(player, port[0], port[1] + 0.1, port[2], 0, ORANGE, pulses);
            player.spawnParticle(Particle.DUST, port[0] + 0.5, port[1] + 0.6, port[2] + 0.5, 6, 0.1, 0.25, 0.1, 0, ORANGE);
        } else if (from != null) {
            outlineStructure(player, from, ORANGE, pulses);
        }
        outlineStructure(player, to, GREEN, pulses);
    }

    private void outlineStructure(Player player, PlacedStructure structure, Particle.DustOptions dust, int pulses) {
        if (structure == null) {
            return;
        }
        box(player, structure.minX(), structure.maxX() + 1, structure.minZ(), structure.maxZ() + 1, structure.y() + 1.1,
                dust, pulses);
    }

    private void yardTalk(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        boolean factoryLeft = goals != null && (!goals.done(host, "hub") || !goals.done(host, "first_chain")
                || !goals.done(host, "first_ship"));
        speak(player, hand, List.of(Say.german(player)
                        ? pick("Brauchst du eine Hand?", "Die Bänder summen, die Hütte füllt sich. Was kann ich tun?",
                        "Guter Tag zum Steineschleppen. Was gibt's?")
                        : pick("Need a hand?", "Belts humming, hut filling. What can I do for you?",
                        "Good day for hauling stone. What's up?")),
                List.of(reply(t(player, "What's next?", "Was kommt als Nächstes?"), NamedTextColor.GREEN,
                                () -> tip(player, hand)),
                        reply(t(player, "My Build Tool", "Mein Bauwerkzeug"), NamedTextColor.GOLD,
                                () -> placement.openTool(player, host, null)),
                        factoryLeft
                                ? reply(t(player, "Walk me through the factory", "Führ mich durch die Fabrik"),
                                NamedTextColor.YELLOW, () -> {
                                    State state = state(hand.owner);
                                    state.step = Step.HUB;
                                    state.briefed = null;
                                    dirty = true;
                                    advance(host, state);
                                    talkTo(player, hand);
                                })
                                : reply(t(player, "How does it all work?", "Wie läuft das alles?"), NamedTextColor.GRAY,
                                () -> explain(player, hand)),
                        reply(t(player, "Bye", "Tschüss"), NamedTextColor.DARK_GRAY, null)));
    }

    private void tip(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        if (state(hand.owner).step.touring()) {
            talkTo(player, hand);
            return;
        }
        FactoryGoals.Goal goal = goals == null ? null : goals.next(host);
        if (goal != null) {
            speak(player, hand, List.of(goal.title() + ": " + goals.howNow(host, goal)),
                    List.of(reply(t(player, "My Build Tool", "Mein Bauwerkzeug"), NamedTextColor.GOLD,
                                    () -> placement.openTool(player, host, null)),
                            reply(t(player, "Got it", "Verstanden"), NamedTextColor.GRAY, null)));
            return;
        }
        String line;
        PlacedStructure hut = firstHut(host);
        if (!structures.hasWorkshop(host)) {
            line = t(player, "Build a Hub next. Its lectern unlocks machines.",
                    "Bau als Nächstes einen Hub. Sein Lesepult schaltet Maschinen frei.");
        } else if (structures.count(host, StructureType.MILL) == 0) {
            line = t(player, "Unlock the Mill at the Hub, then belt quarry, Mill, hut.",
                    "Schalte die Mill am Hub frei, dann Band: Steinbruch, Mill, Hütte.");
        } else if (structures.count(host, StructureType.FORGE) == 0) {
            line = t(player, "Unlock the Forge at the Hub, then belt Mill, Forge, hut.",
                    "Schalte die Forge am Hub frei, dann Band: Mill, Forge, Hütte.");
        } else if (hut != null && hut.level() < 2) {
            line = t(player, "Upgrade your hut for a loft: more storage.", "Bau deine Hütte zum Loft aus: mehr Lager.");
        } else if (hosts.parcels(host).size() <= 9) {
            line = t(player, "Buy land next to yours for more yard.", "Kauf Land neben deinem für mehr Platz.");
        } else {
            line = t(player, "You're past the tour. Keep the line running.",
                    "Die Tour hast du hinter dir. Halt die Kette am Laufen.");
        }
        speak(player, hand, List.of(line), List.of(reply(t(player, "Thanks", "Danke"), NamedTextColor.GRAY, null)));
    }

    private void giveQuarry(Player player) {
        handQuarry(player, true);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        if (!IslandUi.toast(player, "Cobble Quarry from " + name(), "§7It's in your bag.",
                "§8Next: §7Build Tool shows its rig, right-click flat ground")) {
            player.sendMessage("§a+ §6Cobble Quarry §8(from " + name() + ") §7· the Build Tool shows where its rig goes: right-click flat ground");
        }
    }

    private boolean hasQuarryItem(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (minions.isQuarryItem(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a quarry into the main hand: the one in the inventory if there is one, else (when {@code create}) a new
     * one. The held item moves to a free slot; nothing is ever deleted.
     */
    private void handQuarry(Player player, boolean create) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        if (minions.isQuarryItem(inventory.getItemInMainHand())) {
            return;
        }
        int held = inventory.getHeldItemSlot();
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            if (!minions.isQuarryItem(inventory.getItem(slot))) {
                continue;
            }
            if (slot < 9) {
                inventory.setHeldItemSlot(slot);
            } else {
                ItemStack current = inventory.getItem(held);
                inventory.setItem(held, inventory.getItem(slot));
                inventory.setItem(slot, current);
            }
            return;
        }
        if (!create) {
            return;
        }
        ItemStack quarry = minions.createQuarryItem(QuarryType.COBBLESTONE);
        ItemStack current = inventory.getItem(held);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(held, quarry);
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                inventory.setItem(slot, quarry);
                inventory.setHeldItemSlot(slot);
                return;
            }
        }
        int free = inventory.firstEmpty();
        if (free >= 0) {
            inventory.setItem(free, current);
            inventory.setItem(held, quarry);
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), quarry);
        player.sendMessage("§eInventory full: your quarry is on the ground at your feet.");
    }

    private static TalkAccess.Reply reply(String label, NamedTextColor color, Runnable action) {
        return new TalkAccess.Reply(label, color, action == null ? () -> {
        } : action, label);
    }

    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    /** Queue lines (paced) and then reply chips. Bubble first; chat when the player has no bubble. */
    private void speak(Player player, Hand hand, List<String> lines, List<TalkAccess.Reply> replies) {
        boolean owner = player.getUniqueId().equals(hand.owner);
        if (owner) {
            hand.offered = true; // he has spoken up for this visit
        }
        lastReplies.put(player.getUniqueId(), replies);
        TalkAccess talk = AetherServices.talk();
        if (talk == null || !talk.bubbles(player)) {
            chatFallback(player, lines, replies);
            return;
        }
        if (owner && !lines.isEmpty() && tooFar(player, hand, talk)) {
            // never a speech into chat: it waits until the owner stands by him
            ring(player, hand, () -> speak(player, hand, lines, replies));
            return;
        }
        registerSpeaker(talk, hand);
        Chat chat = new Chat(hand);

        chat.lines.addAll(lines);
        chat.replies = replies;
        chat.nextAt = clock;
        chats.put(player.getUniqueId(), chat);
        hideHolo(player, hand);
        pump(player, chat, talk);
    }

    /** Too far for his speech bubble (or not on his island at all). */
    private boolean tooFar(Player player, Hand hand, TalkAccess talk) {
        if (hand.at == null || !player.getWorld().equals(hand.at.getWorld())) {
            return true;
        }
        double reach = Math.max(4.0, talk.reach() - 1.5);
        return player.getLocation().distanceSquared(hand.at) > reach * reach;
    }

    /**
     * Is this click meant for the isle hand rather than for the Build Tool in the player's hand? Only ever while the
     * player is in a talk with the hand of the island they stand on: then a click the talk has already used
     * ({@code used}: the next line, a reply chip) or one aimed straight at him belongs to the talk, and the tool
     * must not build, turn or lay anything with it.
     */
    public boolean clickIsTalk(Player player, boolean used) {
        if (player == null || hands.isEmpty()) {
            return false;
        }
        IslandHost host = hosts.at(player.getLocation());
        Hand hand = host == null || host.isGuild() ? null : hands.get(host.id());
        if (hand == null || hand.at == null || !player.getWorld().equals(hand.at.getWorld())) {
            return false;
        }
        TalkAccess talk = AetherServices.talk();
        boolean talking = chats.containsKey(player.getUniqueId())
                || (talk != null && talk.talkingWith(player, hand.speakerId));
        if (!talking) {
            return false;
        }
        if (used || (talk != null && talk.recentlyPicked(player))) {
            return true;
        }
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector to = hand.at.toVector().add(new org.bukkit.util.Vector(0, 1.6, 0)).subtract(eye.toVector());
        double distance = to.length();
        if (distance > 13.0) {
            return false;
        }
        if (distance < 1.5) {
            return true;
        }
        // his body and the bubble over his head both lie inside this cone
        return eye.getDirection().normalize().dot(to.multiply(1.0 / distance)) > 0.92;
    }

    private void registerSpeaker(TalkAccess talk, Hand hand) {
        talk.registerSpeaker(new TalkAccess.Speaker(hand.speakerId, name(), "§e", "isle_hand", () -> hand.at));
    }

    /** Next paced line (or the chips). Too far for the bubble: the rest goes to chat (he never walks over). */
    private void pump(Player player, Chat chat, TalkAccess talk) {
        if (!player.isOnline()) {
            chats.remove(player.getUniqueId());
            return;
        }
        Hand hand = chat.hand;
        if (hand.at == null || !player.getWorld().equals(hand.at.getWorld())) {
            chats.remove(player.getUniqueId());
            return;
        }
        if (tooFar(player, hand, talk)) {
            // walked off mid-talk: the rest waits until they're back by him (nothing goes to chat)
            chats.remove(player.getUniqueId());
            List<String> rest = new ArrayList<>(chat.lines);
            List<TalkAccess.Reply> replies = chat.replies;
            if (player.getUniqueId().equals(hand.owner) && (!rest.isEmpty() || !replies.isEmpty())) {
                hand.waiting = () -> speak(player, hand, rest.isEmpty()
                        ? List.of(t(player, "Where were we?", "Wo waren wir?")) : rest, replies);
                hand.offered = false;
            }
            return;
        }
        String line = chat.lines.poll();
        if (line != null && IslandUi.paged(player)) {
            // Paged talk: the bubble takes every line now and the player clicks from one to the next; the
            // replies wait in it until the last line is read. Nothing goes to chat but the log line.
            for (String next = line; next != null; next = chat.lines.poll()) {
                if (!talk.say(player, hand.speakerId, next)) {
                    player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name() + " §8» §f" + next));
                }
            }
            chats.remove(player.getUniqueId());
            if (!chat.replies.isEmpty() && !talk.replies(player, hand.speakerId, chat.replies)) {
                chatFallback(player, List.of(), chat.replies);
            }
            return;
        }
        if (line != null) {
            if (!talk.say(player, hand.speakerId, line)) {
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name() + " §8» §f" + line));
            }
            chat.nextAt = clock + Math.max(46, Math.min(110, 34 + line.length()));
            if (chat.lines.isEmpty() && !chat.replies.isEmpty()) {
                chat.nextAt = clock + 30; // chips appear as the last line finishes typing
            }
            return;
        }
        chats.remove(player.getUniqueId());
        if (!chat.replies.isEmpty() && !talk.replies(player, hand.speakerId, chat.replies)) {
            chatFallback(player, List.of(), chat.replies);
        }
    }

    private void chatFallback(Player player, List<String> lines, List<TalkAccess.Reply> replies) {
        for (String line : lines) {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name() + " §8» §f" + line));
        }
        if (replies.isEmpty()) {
            return;
        }
        List<Runnable> actions = new ArrayList<>();
        Component row = Component.text("  » ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < replies.size(); i++) {
            TalkAccess.Reply reply = replies.get(i);
            actions.add(reply.action());
            if (i > 0) {
                row = row.append(Component.text("  "));
            }
            row = row.append(Component.text("[" + reply.label() + "]", reply.color())
                    .clickEvent(ClickEvent.runCommand("/island guide " + i))
                    .hoverEvent(HoverEvent.showText(Component.text(reply.label(), NamedTextColor.GRAY))));
        }
        chatReplies.put(player.getUniqueId(), actions);
        player.sendMessage(row);
    }

    // ------------------------------------------------------------------------------------------------
    // the tour follows the island
    // ------------------------------------------------------------------------------------------------

    private static boolean tourAlreadyDone(LogisticsService logistics, IslandHost host) {
        for (LogisticsService.Route route : logistics.routes(host)) {
            if (route.from().type() == StructureType.QUARRY_HOUSING && route.to() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean tourAlreadyDone(IslandHost host) {
        return tourAlreadyDone(logistics, host);
    }

    private boolean inLine(IslandHost host, StructureType type) {
        return goals == null || goals.inLine(host, type);
    }

    private boolean builtDock(IslandHost host) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == StructureType.DOCK && !structure.building()) {
                return true;
            }
        }
        return false;
    }

    private PlacedStructure firstHut(IslandHost host) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == StructureType.STORAGE_HUT) {
                return structure;
            }
        }
        return null;
    }

    private PlacedStructure firstHousing(IslandHost host) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == StructureType.QUARRY_HOUSING) {
                return structure;
            }
        }
        return null;
    }

    /** Skip ahead past steps the island already shows. Returns true when the step changed. */
    private boolean advance(IslandHost host, State state) {
        Step before = state.step;
        if (state.step.legacy()) {
            state.step = Step.HUB; // an old tour's press / Forge steps: the island itself says where we are
        }
        if (state.step.touring() && state.step != Step.HUB && structures.hub(host) == null) {
            state.step = Step.HUB; // the Hub came down (or never stood): Hub first again
            if ((state.kits & TOOL_GIVEN) != 0) {
                state.briefed = Step.HUB; // picked up to move it: they know what to do with it
            }
        }
        if (state.step == Step.HUB && stands(structures.hub(host))) {
            state.step = Step.HUT;
        }
        if (state.step == Step.HUT && stands(firstHut(host))) {
            state.step = Step.QUARRY;
        }
        if (state.step == Step.QUARRY && (stands(firstHousing(host)) || !hosts.minions(host).isEmpty())) {
            state.step = Step.BELT;
        }
        boolean chained = inLine(host, StructureType.MILL);
        if (state.step.ordinal() >= Step.BELT.ordinal() && state.step.ordinal() <= Step.OUTPUT.ordinal() && chained) {
            // the whole chain already runs (an island from an older tour, or built ahead): straight to the Dock
            state.step = Step.DOCK;
        }
        if (state.step == Step.BELT && quarryBelted(host)) {
            state.step = Step.MILL;
        }
        if (state.step == Step.MILL && stands(lineMachine(host, StructureType.MILL))) {
            state.step = Step.SPLIT;
        }
        if (state.step == Step.SPLIT && structures.count(host, StructureType.SPLITTER) > 0) {
            state.step = Step.FEED;
        }
        if (state.step == Step.FEED && millFed(host)) {
            state.step = Step.OUTPUT;
        }
        if (state.step == Step.OUTPUT && chained) {
            state.step = Step.DOCK;
        }
        if (state.step == Step.DOCK && (dock == null || dock.stage(host) >= 1 || builtDock(host))) {
            state.step = Step.SHIP;
        }
        if (state.step == Step.SHIP && (dock == null || dock.stage(host) >= 1)) {
            // the first shipment paid a second quarry's parts: building it is the next step
            state.step = (state.kits & CRAFT_KIT) != 0 && hosts.minions(host).size() < 2 ? Step.CRAFT
                    : land == null ? Step.DONE : Step.LAND;
        }
        if (state.step == Step.CRAFT && hosts.minions(host).size() >= 2) {
            state.step = land == null ? Step.DONE : Step.LAND;
        }
        if (state.step == Step.LAND && (land == null || (state.kits & LAND_GIFT) != 0)) {
            state.step = Step.DONE;
        }
        if (state.step.touring() && state.step.ordinal() >= Step.SPLIT.ordinal() && (state.kits & REFINE_KIT) == 0) {
            // an island that got past the Mill some other way (an older tour): the Splitter is theirs too
            state.kits |= REFINE_KIT;
            unlocker.accept(host, StructureType.MILL);
            unlocker.accept(host, StructureType.SPLITTER);
            dirty = true;
        }
        if (state.step != before) {
            state.lastSub = -1;
            state.craftSub = -1;
            dirty = true;
            return true;
        }
        return false;
    }

    /** The first quarry's chute is belted into something (the hut, or already a Splitter on the way to it). */
    private boolean quarryBelted(IslandHost host) {
        PlacedStructure housing = firstHousing(host);
        LogisticsService.Route out = housing == null ? null : logistics.routeFrom(housing);
        return out != null && out.to() != null;
    }

    private static boolean stands(PlacedStructure structure) {
        return structure != null && !structure.building();
    }

    private boolean millFed(IslandHost host) {
        PlacedStructure mill = lineMachine(host, StructureType.MILL);
        return mill != null && logistics.feeding(mill) > 0;
    }

    /**
     * The first time a belt feeds the Mill on the tour, he tips a starter load in (the input topped up to one
     * batch), so the first Compressed Cobble comes at once instead of after two minutes of waiting. Once per
     * island; true when it went in just now.
     */
    private boolean primeOnce(IslandHost host, State state) {
        if (state.primed || !state.step.touring()) {
            return false;
        }
        PlacedStructure mill = lineMachine(host, StructureType.MILL);
        if (mill == null || logistics.feeding(mill) == 0) {
            return false;
        }
        state.primed = true;
        dirty = true;
        long added = logistics.prime(mill, Res.of(QuarryType.COBBLESTONE, Res.Form.RAW), Res.UNIT);
        World world = hosts.world(host);
        if (added > 0L && world != null) {
            Location top = new Location(world, mill.centerX(), mill.maxY() + 0.9, mill.centerZ());
            de.aetherion.core.api.Vfx.puff(top, Particle.BLOCK, 24, Math.max(0.35, 0.35),
                    Material.COBBLESTONE.createBlockData());
            world.playSound(top, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.8f);
            world.playSound(top, Sound.BLOCK_STONE_PLACE, 0.8f, 0.7f);
        }
        return added > 0L;
    }

    // ------------------------------------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------------------------------------

    /** Every 2 ticks: paced lines. Every second: presence, tour progress, his post. */
    public void tick() {
        clock += 2;
        if (!chats.isEmpty()) {
            TalkAccess talk = AetherServices.talk();
            for (Map.Entry<UUID, Chat> entry : new ArrayList<>(chats.entrySet())) {
                Chat chat = entry.getValue();
                if (clock < chat.nextAt) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || talk == null) {
                    chats.remove(entry.getKey());
                    continue;
                }
                pump(player, chat, talk);
            }
        }
        if (clock % 20 == 0) {
            second();
        }
        if (clock % 1200 == 0) {
            saveIfDirty();
        }
    }

    private void second() {
        if (!available()) {
            if (!hands.isEmpty()) {
                removeAll();
            }
            return;
        }
        long now = System.currentTimeMillis();
        TalkAccess talk = AetherServices.talk();
        Map<UUID, List<Player>> present = new HashMap<>();
        World world = personal.world();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                IslandHost host = hosts.at(player.getLocation());
                if (host != null && !host.isGuild()
                        && Math.abs(player.getLocation().getBlockX() - hosts.originX(host)) <= 96) {
                    present.computeIfAbsent(host.id(), k -> new ArrayList<>()).add(player);
                }
            }
        }
        for (Map.Entry<UUID, List<Player>> entry : present.entrySet()) {
            PersonalIsland island = personal.byOwner(entry.getKey());
            if (!eligible(island)) {
                continue;
            }
            Hand hand = ensureHand(island);
            if (hand == null) {
                continue;
            }
            hand.lastSeen = now;
            Player owner = null;
            boolean busy = false;
            for (Player player : entry.getValue()) {
                if (player.getUniqueId().equals(island.ownerId())) {
                    owner = player;
                }
                boolean talking = chats.containsKey(player.getUniqueId())
                        || (talk != null && talk.talkingWith(player, hand.speakerId));
                busy |= talking;
                if (!talking) {
                    showHolo(player, hand);
                }
            }
            if (!busy) {
                settle(hand); // the Hub went up (or came down): he takes his post there
            }
            if (owner != null) {
                ownerSecond(owner, hand, island);
            }
            updateHolo(hand);
            State current = states.get(island.ownerId());
            boolean talking = owner != null && (chats.containsKey(owner.getUniqueId())
                    || (talk != null && talk.talkingWith(owner, hand.speakerId)));
            updateMark(hand, owner, owner != null && !talking && needsTalk(current)
                    && (current == null || !current.step.finished()));
        }
        // nobody around for a minute: he goes off shift (no NPC, no packets)
        for (Hand hand : new ArrayList<>(hands.values())) {
            if (now - hand.lastSeen > 60_000L) {
                despawn(hand);
                hands.remove(hand.owner);
            }
        }
    }

    private void ownerSecond(Player owner, Hand hand, PersonalIsland island) {
        IslandHost host = IslandHost.personal(island.ownerId());
        State state = state(island.ownerId());
        if (state.step == Step.WELCOME && !state.introduced && !chats.containsKey(owner.getUniqueId())) {
            // an island claimed before he existed: he says hello the first time the owner is home
            state.introduced = true;
            dirty = true;
            greet(owner, hand);
            return;
        }
        TalkAccess talk = AetherServices.talk();
        boolean busy = chats.containsKey(owner.getUniqueId()) || (talk != null && talk.talkingWith(owner, hand.speakerId));
        if (lessonPending(state) && state.briefed == Step.HUT) {
            // the Build Tool lesson: on the Storage Hut by any road counts; else the tool's bar keeps saying how
            if (placement.holdingTool(owner) && placement.part(owner) == PlacementService.Part.STORAGE_HUT) {
                switched(owner, PlacementService.Part.STORAGE_HUT);
                return;
            }
            placement.coach(owner, coach(owner));
        } else {
            placement.coach(owner, null);
        }
        boolean sameWorld = hand.at != null && owner.getWorld().equals(hand.at.getWorld());
        double away = sameWorld ? owner.getLocation().distanceSquared(hand.at) : Double.MAX_VALUE;
        if (away > 14.0 * 14.0) {
            hand.offered = false; // they went off: when they come back to him, he speaks up once
        }
        if (!busy && !hand.offered && away <= 6.0 * 6.0 && owner.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            if (hand.waiting != null) {
                // his reaction to a task done further away: he says it now that the owner stands by him
                Runnable then = hand.waiting;
                hand.waiting = null;
                hand.offered = true;
                then.run();
                return;
            }
            if (needsTalk(state) && (state.step.touring() || state.step == Step.WELCOME)) {
                // the owner walks up to him and he still has the step to explain (after a rejoin, a "later")
                hand.offered = true;
                if (state.step == Step.WELCOME) {
                    greet(owner, hand);
                } else {
                    brief(owner, hand, false);
                }
                return;
            }
        }
        if (!state.step.touring()) {
            return;
        }
        // the first chain's beat waits for the first Compressed Cobble in the hut (a few seconds, at most 12)
        if (state.step.actTwo() && (state.kits & CHAIN_BEAT) == 0) {
            if (compressedInStorage(host) > 0L || System.currentTimeMillis() - state.beatSince > 12_000L) {
                chainBeat(owner, hand);
            }
            return;
        }
        Step before = state.step;
        if (advance(host, state)) {
            hand.waiting = null;
            hand.offered = false;
            boolean primedNow = before.ordinal() <= Step.FEED.ordinal() && primeOnce(host, state);
            Step reached = state.step;
            if (before == Step.SPLIT && reached == Step.FEED) {
                // he explained both in one go, and the tool turned to Belt when the Splitter went in
                state.briefed = Step.FEED;
            }
            if (!stepToast(owner, before, primedNow)) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
            }
            long pause = Math.max(0L, plugin.getConfig().getLong("guide.step-pause-ticks", 40L));
            switch (reached) {
                case HUT -> {
                    if (before == Step.HUB) {
                        // the Hub ritual plays first (bells, beam, card); he takes his post by the Hub meanwhile
                        settle(hand);
                        react(owner, hand, reached, 110L);
                    } else {
                        react(owner, hand, reached, pause);
                    }
                }
                case FEED -> {
                    if (state.briefed != Step.FEED) {
                        react(owner, hand, reached, pause);
                    }
                }
                case DOCK, SHIP -> {
                    if ((state.kits & CHAIN_BEAT) == 0) {
                        state.beatSince = System.currentTimeMillis(); // the beat comes with the first block
                    } else {
                        react(owner, hand, reached, pause);
                    }
                }
                case CRAFT, LAND -> {
                    if (before == Step.SHIP) {
                        // the Dock plays the first ship's beat (airship, stage card) and calls on him when it is
                        // over; if that never comes (an admin stage, a beat cut short) he speaks up a little later
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            State now = state(island.ownerId());
                            if (owner.isOnline() && now.step == reached && needsTalk(now) && hand.waiting == null
                                    && !chats.containsKey(owner.getUniqueId())) {
                                react(owner, hand, reached, 0L);
                            }
                        }, 400L);
                    } else {
                        react(owner, hand, reached, pause);
                    }
                }
                case DONE -> {
                    if (before == Step.LAND) {
                        landTaken(owner, hand);
                    } else if (before != Step.SHIP && before != Step.CRAFT) {
                        finishTalk(owner, hand);
                    }
                }
                default -> react(owner, hand, reached, pause);
            }
            return;
        }
        // inside the Dock step: unlocked at the lectern (the Hub hands the Dock into the tool), then built
        if (state.step == Step.DOCK && state.briefed == Step.DOCK) {
            int sub = sub(host, StructureType.DOCK);
            if (state.lastSub == 0 && sub == 1) {
                String next = objective(island.ownerId(), owner);
                if (!IslandUi.toast(owner, t(owner, "Dock unlocked", "Dock freigeschaltet"),
                        t(owner, "§7It is in your Build Tool now", "§7Es ist jetzt in deinem Bauwerkzeug"),
                        next == null ? null : t(owner, "§8Next: §7", "§8Weiter: §7") + next)) {
                    owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                }
            }
            state.lastSub = sub;
        }
        // inside the craft step: the quarry got built (it lies in the pack now)
        if (state.step == Step.CRAFT && state.briefed == Step.CRAFT) {
            int sub = hasQuarryItem(owner) ? 1 : 0;
            if (state.craftSub == 0 && sub == 1) {
                String next = objective(island.ownerId(), owner);
                if (!IslandUi.toast(owner, craftType(host).display() + t(owner, " built", " gebaut"),
                        t(owner, "§7Built by your own hands", "§7Mit eigenen Händen gebaut"),
                        next == null ? null : t(owner, "§8Next: §7", "§8Weiter: §7") + next)) {
                    owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                }
            }
            state.craftSub = sub;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // markers
    // ------------------------------------------------------------------------------------------------

    private int[] hutPad(IslandHost host) {
        StarterLayout starter = hosts.starter(host);
        if (starter == null) {
            return null;
        }
        return new int[]{hosts.originX(host) + starter.hutPadX(), HostService.SURFACE_Y,
                hosts.originZ(host) + starter.hutPadZ()};
    }

    /** A square of dust around (x, z) with half-size r, shown {@code pulses} times, a second apart. */
    private void square(Player player, int cx, double y, int cz, int r, Particle.DustOptions dust, int pulses) {
        box(player, cx - r, cx + r + 1, cz - r, cz + r + 1, y, dust, pulses);
    }

    /** A rectangle of dust from (x0, z0) to (x1, z1), shown {@code pulses} times, a second apart. */
    private void box(Player player, double x0, double x1, double z0, double z1, double y, Particle.DustOptions dust,
                     int pulses) {
        for (int pulse = 0; pulse < pulses; pulse++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                for (double t = 0; t <= (x1 - x0); t += 0.5) {
                    player.spawnParticle(Particle.DUST, x0 + t, y, z0, 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, x0 + t, y, z1, 1, 0, 0, 0, 0, dust);
                }
                for (double t = 0; t <= (z1 - z0); t += 0.5) {
                    player.spawnParticle(Particle.DUST, x0, y, z0 + t, 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, x1, y, z0 + t, 1, 0, 0, 0, 0, dust);
                }
            }, pulse * 20L);
        }
    }

    /** Top solid block near the island surface, or MIN_VALUE over void. Signs/banners never count. */
    private static int ground(World world, int x, int z) {
        for (int y = HostService.SURFACE_Y + 6; y >= HostService.SURFACE_Y - 8; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!standable(block.getType())) {
                continue;
            }
            if (PasteService.replaceable(block.getRelative(0, 1, 0))
                    && PasteService.replaceable(block.getRelative(0, 2, 0))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean standable(Material type) {
        if (type == null || !type.isSolid()) {
            return false;
        }
        if (Tag.ALL_SIGNS.isTagged(type) || Tag.BANNERS.isTagged(type) || Tag.WOOL_CARPETS.isTagged(type)) {
            return false;
        }
        return type != Material.SNOW && type != Material.MOSS_CARPET;
    }

    // ------------------------------------------------------------------------------------------------
    // his post (he never walks)
    // ------------------------------------------------------------------------------------------------

    /** A standing spot near (x, z) on solid ground, facing (lookX, lookZ); null over void. */
    private Location spotNear(IslandHost host, int x, int z, int lookX, int lookZ) {
        // on the ground the owner stands on first (never up on a log seat or a bench: his answer chips sit at
        // his head, and from below they are awkward to click), anything standable only if there is no such spot
        Location spawn = hosts.spawn(host);
        int floor = spawn == null ? HostService.SURFACE_Y : spawn.getBlockY() - 1;
        Location level = spotNear(host, x, z, lookX, lookZ, floor);
        return level != null ? level : spotNear(host, x, z, lookX, lookZ, Integer.MIN_VALUE);
    }

    /** {@code floor} = the ground's block y a spot must have, or {@link Integer#MIN_VALUE} for any. */
    private Location spotNear(IslandHost host, int x, int z, int lookX, int lookZ, int floor) {
        World world = hosts.world(host);
        if (world == null) {
            return null;
        }
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int bx = x + dx;
                    int bz = z + dz;
                    int y = ground(world, bx, bz);
                    if (y == Integer.MIN_VALUE || (floor != Integer.MIN_VALUE && y != floor)
                            || structures.atColumn(world, bx, bz) != null || logistics.isBelt(world, bx, y + 1, bz)) {
                        continue;
                    }
                    Location at = new Location(world, bx + 0.5, y + 1, bz + 0.5);
                    at.setYaw(yawTo(at.getX(), at.getZ(), lookX + 0.5, lookZ + 0.5));
                    return at;
                }
            }
        }
        return null;
    }

    /**
     * Where he stands: once the Hub stands, two steps to the side of the spot in front of its lectern, looking
     * out the way the owner comes from (so he is never in the way of the lectern, and always at the Hub);
     * before that by the campfire or the landing spot.
     */
    private Location postSpot(IslandHost host) {
        PlacedStructure hub = structures.hub(host);
        Location front = stands(hub) ? structures.hubSpot(host) : null;
        if (front != null) {
            int[] side = de.aetherion.guilds.template.StateRotator.rotateXZ(2, 0, hub.rot());
            int[] out = de.aetherion.guilds.template.StateRotator.rotateXZ(0, 4, hub.rot());
            int x = front.getBlockX() + side[0];
            int z = front.getBlockZ() + side[1];
            Location spot = spotNear(host, x, z, x + out[0], z + out[1]);
            if (spot != null) {
                return spot;
            }
        }
        return homeSpot(host);
    }

    /**
     * He takes his post if he isn't there: a puff where he stood, a puff and a wave where he lands. No walking
     * across belts and machines, ever.
     */
    private void settle(Hand hand) {
        Location post = postSpot(IslandHost.personal(hand.owner));
        if (post == null || post.getWorld() == null) {
            return;
        }
        hand.home = post;
        if (hand.at != null && post.getWorld().equals(hand.at.getWorld()) && hand.at.distanceSquared(post) < 1.0) {
            return;
        }
        if (hand.at != null && hand.at.getWorld() != null) {
            de.aetherion.core.api.Vfx.puff(hand.at.clone().add(0, 1.0, 0), Particle.CLOUD, 10, Math.max(0.3, 0.3), null);
        }
        move(hand, post);
        de.aetherion.core.api.Vfx.puff(post.clone().add(0, 1.0, 0), Particle.CLOUD, 10, Math.max(0.3, 0.3), null);
        post.getWorld().playSound(post, Sound.BLOCK_WOOL_STEP, 0.8f, 1.1f);
        if (hand.npc != null) {
            try {
                FancyNpcFacade.moveForAll(hand.npc, true); // a wave as he gets there
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static float yawTo(double fx, double fz, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
    }

    // ------------------------------------------------------------------------------------------------
    // the NPC (FancyNpcs via the Core facade) + its name card
    // ------------------------------------------------------------------------------------------------

    private Hand ensureHand(PersonalIsland island) {
        Hand hand = hands.get(island.ownerId());
        if (hand != null && hand.npc != null) {
            return hand;
        }
        if (hand == null) {
            hand = new Hand(island.ownerId());
            hands.put(island.ownerId(), hand);
        }
        hand.lastSeen = System.currentTimeMillis();
        if (hand.spawning) {
            return null;
        }
        IslandHost host = IslandHost.personal(island.ownerId());
        if (hand.home == null) {
            hand.home = postSpot(host);
        }
        if (hand.home == null) {
            return null;
        }
        if (hand.at == null) {
            hand.at = hand.home.clone();
        }
        spawn(hand);
        return hand.npc == null ? null : hand;
    }

    /**
     * Before the Hub stands: by the campfire if the starter has one; otherwise a few blocks from spawn toward the
     * hut pad. Never the quarry.
     */
    private Location homeSpot(IslandHost host) {
        Location fire = findCampfire(host);
        if (fire != null) {
            Location seat = spotNear(host, fire.getBlockX() + 2, fire.getBlockZ(), fire.getBlockX(), fire.getBlockZ());
            if (seat != null) {
                return seat;
            }
            seat = spotNear(host, fire.getBlockX() - 2, fire.getBlockZ(), fire.getBlockX(), fire.getBlockZ());
            if (seat != null) {
                return seat;
            }
        }
        Location spawn = hosts.spawn(host);
        int[] pad = hutPad(host);
        if (spawn == null) {
            return null;
        }
        int sx = spawn.getBlockX();
        int sz = spawn.getBlockZ();
        int tx = sx;
        int tz = sz;
        if (pad != null) {
            double dx = pad[0] - sx;
            double dz = pad[2] - sz;
            double len = Math.max(0.001, Math.hypot(dx, dz));
            tx = (int) Math.round(sx + dx / len * 3);
            tz = (int) Math.round(sz + dz / len * 3);
        } else {
            tx += 2;
        }
        return spotNear(host, tx, tz, sx, sz);
    }

    private Location findCampfire(IslandHost host) {
        World world = hosts.world(host);
        Location spawn = hosts.spawn(host);
        if (world == null || spawn == null) {
            return null;
        }
        int ox = spawn.getBlockX();
        int oz = spawn.getBlockZ();
        int y0 = HostService.SURFACE_Y;
        Location best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    Block block = world.getBlockAt(ox + dx, y0 + dy, oz + dz);
                    Material type = block.getType();
                    if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
                        continue;
                    }
                    int dist = Math.abs(dx) + Math.abs(dz);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = block.getLocation().add(0.5, 0, 0.5);
                    }
                }
            }
        }
        return best;
    }

    private void spawn(Hand hand) {
        if (!FancyNpcFacade.isAvailable() || hand.at == null) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                return;
            }
            Object existing = FancyNpcFacade.getNpc(manager, hand.npcName);
            if (existing != null) {
                hand.npc = existing;
                move(hand, hand.at);
                return;
            }
            Object data = FancyNpcFacade.createNpcData(hand.npcName, hand.owner, hand.at);
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invokeQuiet(data, "setType", org.bukkit.entity.EntityType.class, org.bukkit.entity.EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.applyVisibility(data, 48);
            String skin = plugin.getConfig().getString("guide.skin", "MHF_Villager");
            if (skin != null && !skin.isBlank()) {
                FancyNpcFacade.invokeQuiet(data, "setSkin", String.class, skin);
            }
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.invokeQuiet(npc, "setSaveToFile", boolean.class, false);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            hand.npc = npc;
        } catch (Throwable t) {
            plugin.getLogger().warning(name() + " (island guide) spawn failed: " + t.getMessage());
        }
    }

    private void move(Hand hand, Location to) {
        hand.at = to.clone();
        if (hand.npc != null) {
            try {
                Object data = FancyNpcFacade.data(hand.npc);
                FancyNpcFacade.setLocation(data, hand.at);
                // moveForAll(false): no arm swing on every step (same as the living NPCs' walks)
                FancyNpcFacade.moveForAll(hand.npc, false);
            } catch (ReflectiveOperationException ignored) {
                FancyNpcFacade.moveForAll(hand.npc);
            }
        }
        if (hand.holo != null && hand.holo.isValid()) {
            hand.holo.teleport(hand.at.clone().add(0, 2.12, 0));
        }
        if (hand.mark != null && hand.mark.isValid()) {
            hand.mark.teleport(hand.at.clone().add(0, 3.0, 0));
        }
    }

    private void despawn(Hand hand) {
        if (hand.holo != null && hand.holo.isValid()) {
            hand.holo.remove();
        }
        hand.holo = null;
        if (hand.mark != null && hand.mark.isValid()) {
            hand.mark.remove();
        }
        hand.mark = null;
        if (hand.npc == null || !FancyNpcFacade.isAvailable()) {
            hand.npc = null;
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            FancyNpcFacade.removeFromPlayersQuiet(hand.npc);
            FancyNpcFacade.unregister(manager, hand.npc);
        } catch (Throwable ignored) {
        }
        hand.npc = null;
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.unregisterSpeaker(hand.speakerId);
        }
    }

    public void removeAll() {
        for (Hand hand : hands.values()) {
            despawn(hand);
        }
        hands.clear();
        chats.clear();
    }

    /** Guide NPCs are never saved; drop any that a crash left in FancyNpcs, and our stray name cards. */
    public void purgeLeftovers() {
        java.util.Set<String> live = new java.util.HashSet<>();
        java.util.Set<UUID> liveHolos = new java.util.HashSet<>();
        for (Hand hand : hands.values()) {
            if (hand.npc != null) {
                live.add(hand.npcName);
            }
            if (hand.holo != null) {
                liveHolos.add(hand.holo.getUniqueId());
            }
        }
        World world = personal.world();
        if (world != null) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(HOLO_TAG) && !liveHolos.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            for (Object npc : FancyNpcFacade.allNpcs(manager)) {
                String name = FancyNpcFacade.nameOf(npc);
                if (name != null && name.startsWith(NPC_PREFIX) && !live.contains(name)) {
                    FancyNpcFacade.removeFromPlayersQuiet(npc);
                    FancyNpcFacade.unregister(manager, npc);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateHolo(Hand hand) {
        if (hand.at == null) {
            return;
        }
        State state = states.get(hand.owner);
        Step step = state == null ? Step.WELCOME : state.step;
        Player reader = Bukkit.getPlayer(hand.owner); // his card speaks the owner's language
        String status = step.finished() ? t(reader, "§7Right-click for tips", "§7Rechtsklick für Tipps")
                : needsTalk(state) ? t(reader, "§6✦ §eTalk to me", "§6✦ §eSprich mich an")
                : "§6✦ §e" + t(reader, "Tour · step ", "Tour · Schritt ") + (progress(hand.owner) + 1) + "/" + total();
        String text = "§e§l" + name() + "\n§7" + (Say.german(reader) && "Isle Hand".equals(title())
                ? "Inselhelfer" : title()) + "\n" + status;
        if (hand.holo == null || !hand.holo.isValid()) {
            Location at = hand.at.clone().add(0, 2.12, 0);
            hand.holo = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false);
                d.setTeleportDuration(2);
                d.setViewRange(0.5f);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f),
                        new Quaternionf()));
                d.addScoreboardTag(HOLO_TAG);
            });
            hand.holoText = "";
        }
        if (!text.equals(hand.holoText)) {
            hand.holoText = text;
            hand.holo.text(LegacyComponentSerializer.legacySection().deserialize(text));
        }
    }

    /** The gold mark over his head: he has something to say to the owner (only the owner sees it). */
    private void updateMark(Hand hand, Player owner, boolean show) {
        if (hand.at == null || hand.at.getWorld() == null) {
            return;
        }
        if (hand.mark == null || !hand.mark.isValid()) {
            if (!show) {
                return;
            }
            Location at = hand.at.clone().add(0, 3.0, 0);
            hand.mark = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.text(LegacyComponentSerializer.legacySection().deserialize("§6§l!"));
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setTeleportDuration(2);
                d.setViewRange(1.2f);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.4f, 2.4f, 2.4f),
                        new Quaternionf()));
                d.addScoreboardTag(HOLO_TAG);
            });
        }
        if (owner == null) {
            return;
        }
        boolean seen = owner.canSee(hand.mark);
        if (show && !seen) {
            owner.showEntity(plugin, hand.mark);
        } else if (!show && seen) {
            owner.hideEntity(plugin, hand.mark);
        }
        if (show) {
            // a slow breath, so the eye catches it from across the yard
            boolean big = (clock / 20) % 2 == 0;
            float size = big ? 2.9f : 2.4f;
            hand.mark.setInterpolationDelay(0);
            hand.mark.setInterpolationDuration(18);
            hand.mark.setTransformation(new Transformation(new Vector3f(0f, big ? 0.1f : 0f, 0f), new Quaternionf(),
                    new Vector3f(size, size, size), new Quaternionf()));
        }
    }

    private void hideHolo(Player player, Hand hand) {
        if (hand.holo != null && hand.holo.isValid()) {
            player.hideEntity(plugin, hand.holo);
        }
    }

    private void showHolo(Player player, Hand hand) {
        if (hand.holo != null && hand.holo.isValid() && !player.canSee(hand.holo)) {
            player.showEntity(plugin, hand.holo);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // persistence (island_guide.yml)
    // ------------------------------------------------------------------------------------------------

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("islands");
        if (root == null) {
            return;
        }
        int version = yaml.getInt("version", 1);
        boolean old = version < 3;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                State state = new State();
                state.step = Step.valueOf(section.getString("step", "WELCOME").toUpperCase(Locale.ROOT));
                state.introduced = section.getBoolean("introduced", false);
                state.quarryGiven = section.getBoolean("quarry-given", false);
                state.freeTiles = section.getInt("free-tiles", freeTilesGranted());
                state.kits = section.getInt("kits", 0);
                state.primed = section.getBoolean("primed", false);
                String briefed = section.getString("briefed", null);
                if (briefed != null && !briefed.isBlank()) {
                    state.briefed = Step.valueOf(briefed.toUpperCase(Locale.ROOT));
                }
                if (old) {
                    // an older tour: its steps meant other things. Past the first chain stays past it; the rest
                    // starts again at the Hub and advance() skips whatever the island already shows
                    if (state.step == Step.DOCK || state.step == Step.SHIP || state.step == Step.LAND) {
                        state.kits |= CHAIN_BEAT;
                    } else if (state.step.touring()) {
                        state.step = Step.HUB;
                    }
                    dirty = true;
                }
                if (version < SAVE_VERSION) {
                    // saves from before he explained every step himself: whoever is mid-task keeps the task
                    // (they were told and hold the part); the Hub and his gift steps start with him again
                    boolean gift = state.step == Step.HUB
                            || (state.step == Step.QUARRY && (state.kits & KIT) == 0)
                            || (state.step == Step.MILL && (state.kits & REFINE_KIT) == 0);
                    state.briefed = state.step.touring() && !state.step.legacy() && !gift ? state.step : null;
                    if (state.step.ordinal() > Step.HUB.ordinal()) {
                        state.kits |= TOOL_GIVEN;
                    }
                    dirty = true;
                }
                states.put(UUID.fromString(key), state);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", SAVE_VERSION);
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            String path = "islands." + entry.getKey();
            State state = entry.getValue();
            yaml.set(path + ".step", state.step.name());
            yaml.set(path + ".introduced", state.introduced);
            yaml.set(path + ".quarry-given", state.quarryGiven);
            yaml.set(path + ".free-tiles", state.freeTiles);
            yaml.set(path + ".kits", state.kits);
            yaml.set(path + ".primed", state.primed);
            yaml.set(path + ".briefed", state.briefed == null ? null : state.briefed.name());
        }
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save island_guide.yml: " + exception.getMessage());
        }
    }

    /** An island was wiped: forget its tour. */
    public void forget(UUID owner) {
        Hand hand = hands.remove(owner);
        if (hand != null) {
            despawn(hand);
        }
        if (states.remove(owner) != null) {
            dirty = true;
        }
    }
}
