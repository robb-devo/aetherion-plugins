package de.aetherion.guilds.island;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The isle hand (Corin by default, {@code guide.name}): a FancyNPC on every personal starter island who explains
 * the island one piece at a time, in the TalkUx speech bubble. He never walks: he greets the owner where they
 * land, and once the Hub stands he keeps his post beside it (a straight walk over player-built belts and machines
 * always looked lost). From further away his lines come as chat lines; the island bar always shows the step.
 * <ol>
 *   <li>the <b>Hub</b> first, free, wherever the player likes,</li>
 *   <li>the <b>starter kit</b>, on the house: a Cobble Quarry (into the hand), the Mill (unlocked for them), the
 *   first Storage Hut and enough free belt tiles for the whole first chain,</li>
 *   <li>built the way the stone flows, every piece once: the <b>quarry</b>, the <b>Mill</b> in front of its chute
 *   and a belt into it (he tips a starter load in, so it presses at once), the <b>hut</b> behind it and the last
 *   belt: the first <b>Compressed Cobble</b> lands in the hut a minute or so later,</li>
 *   <li>at the Hub lectern, how new parts come: the <b>Dock</b> costs the one Compressed Cobble just made,</li>
 *   <li>the <b>First Shipment</b> (2 Compressed Cobble) and the airship: stage 1, and a second quarry.</li>
 * </ol>
 * There are no pads, rings or marked spots: the player picks every place; the Build Tool's board beside each
 * hologram says what the part does and where it fits best. Every step is detected from the island itself, so the
 * tour never blocks building: do things in any order, walk away, skip it, pick it up again from {@code /island}.
 * The Forge and everything after it are factory goals.
 *
 * <p>Without the bubble (TalkUx off, player on classic chat talk) the same lines and choices go to chat.
 * Without FancyNpcs there's no guide: the classic "first steps" card is used instead.
 */
public final class IslandGuide implements Listener {

    /**
     * The tour is stage 0 of the island, in the order the stone flows: Hub → starter kit → quarry → Mill → belt
     * into the Mill → hut → belt into the hut (the first Compressed Cobble) → the Dock, unlocked at the Hub → the
     * first ship (which lifts the island to stage 1). PRESS, FORGE and COMPACT stay only so old saves still load:
     * they, and every step saved by the old hut-first tour, start again from the island itself.
     */
    public enum Step {
        WELCOME,
        HUB,
        QUARRY,
        MILL,
        FEED,
        HUT,
        BELT,
        PRESS,
        FORGE,
        COMPACT,
        DOCK,
        SHIP,
        DONE,
        SKIPPED;

        public boolean touring() {
            return ordinal() >= HUB.ordinal() && ordinal() <= SHIP.ordinal();
        }

        /** The Dock half of the tour (the first chain stands; now the Dock and the first ship). */
        public boolean actTwo() {
            return ordinal() >= DOCK.ordinal() && ordinal() <= SHIP.ordinal();
        }

        /** Steps only old saves still carry: the island itself says where those players go on. */
        public boolean legacy() {
            return this == PRESS || this == FORGE || this == COMPACT;
        }

        public boolean finished() {
            return this == DONE || this == SKIPPED;
        }
    }

    /** island_guide.yml format: 2 = the flow-order tour (the steps of older files mean the hut-first tour). */
    private static final int SAVE_VERSION = 2;

    private static final String NPC_PREFIX = "aeg_hand_";
    private static final String SPEAKER_PREFIX = "isle_hand_";
    private static final String HOLO_TAG = "aeg_hand_holo";
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 80), 1.3f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 150, 40), 1.4f);
    /** Kits bit: the starter kit (quarry, Mill unlock, free tiles) was handed over. */
    private static final int KIT = 1 << 5;
    /** Kits bit: the first chain's beat (the first Compressed Cobble in the hut) was played. */
    private static final int CHAIN_BEAT = 1 << 6;
    /** Kits bit: the one spare quarry Corin hands out if the first went missing. */
    private static final int SPARE_QUARRY = 1 << 4;
    /** Kits bit: the Build Tool lesson is over (the player switched to the Quarry, or let him do it). */
    private static final int TOOL_TAUGHT = 1 << 7;
    /** The tool's action bar while the lesson waits for the switch. */
    private static final String COACH = "§e⇅ §fHold sneak §7and §fscroll the wheel §8→ §7switch to the §eQuarry";

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
        /** Session-only: last sub-step seen in the Dock step (locked, unlocked, built), -1 = none yet. */
        int lastSub = -1;
        /** Session-only: when the first chain closed (its beat waits for the first block, 12 s at most). */
        long beatSince;
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
        long lastSeen;
        boolean spawning;
        /** What he says once the owner comes over to him (the Hub beat after the first chain). */
        Runnable waiting;
        long waitingUntil;
        /** He spoke to the owner once since he (re)appeared on the island. */
        boolean metOwner;

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

    /** Short objective for the island bar / hub while the tour runs, else null. */
    public String objective(UUID owner) {
        if (!available()) {
            return null;
        }
        State state = states.get(owner);
        Step step = state == null ? Step.WELCOME : state.step;
        IslandHost host = IslandHost.personal(owner);
        return switch (step) {
            case WELCOME, PRESS, FORGE, COMPACT -> "Talk to " + name();
            case HUB -> {
                PlacedStructure hub = structures.hub(host);
                yield hub != null && hub.building() ? "Hub going up…" : "Place your Hub anywhere §7(free)";
            }
            case QUARRY -> state != null && lessonPending(state)
                    ? "Switch the Build Tool to the Quarry §7(hold sneak, scroll)"
                    : state != null && state.quarryGiven
                    ? "Set your quarry down §7(Build Tool, flat ground)"
                    : "Talk to " + name() + " §7(your starter kit)";
            case MILL -> "Set the Mill down near the quarry's chute §7(free)";
            case FEED -> "Belt the quarry's chute into the Mill";
            case HUT -> "Set the Storage Hut down behind the Mill §7(free)";
            case BELT -> "Belt the Mill's chute into the hut";
            case DOCK, SHIP -> factoryObjective(host, step);
            default -> null;
        };
    }

    /**
     * A tour step just got done: a small screen card at the top ("✔ Mill set down", step n/8, what's next).
     * Steps with their own big moment (Hub, first chain, the ship) get none. False = no card (cards off).
     */
    private boolean stepToast(Player owner, Step done) {
        String title = switch (done) {
            case QUARRY -> "Quarry set down";
            case MILL -> "Mill set down";
            case FEED -> "Mill fed";
            case HUT -> "Storage Hut built";
            case DOCK -> "Dock built";
            default -> null;
        };
        if (title == null) {
            return false;
        }
        String next = objective(owner.getUniqueId());
        return IslandUi.toast(owner, title, "§7Island tour · step " + progress(owner.getUniqueId()) + "/" + total(),
                next == null ? null : "§8Next: §7" + next);
    }

    /** Steps in the whole tour (for "step n/8"). */
    public int total() {
        return 8;
    }

    /** 0..8 steps done (for progress bars). */
    public int progress(UUID owner) {
        return switch (step(owner)) {
            case QUARRY -> 1;
            case MILL -> 2;
            case FEED -> 3;
            case HUT -> 4;
            case BELT -> 5;
            case DOCK -> 6;
            case SHIP -> 7;
            case DONE, SKIPPED -> 8;
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

    /** While the tour runs, the first Hub, Mill and Dock are on the house (the first hut is always free). */
    public boolean freeFirst(IslandHost host, StructureType type) {
        if (host == null || host.isGuild() || !available()) {
            return false;
        }
        if (type != StructureType.WORKSHOP && type != StructureType.MILL && type != StructureType.DOCK) {
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
            case QUARRY -> step == Step.QUARRY ? "Leave room in front of its chute" : null;
            case MILL -> step == Step.MILL ? "In front of the quarry's chute" : null;
            case STORAGE_HUT -> step == Step.HUT ? "Behind the Mill, by its chute" : null;
            case DOCK -> step == Step.DOCK ? "Near the island's edge looks best" : null;
            case BELT -> step == Step.FEED ? "Quarry chute → Mill"
                    : step == Step.BELT ? "Mill chute → hut" : null;
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

    private String factoryObjective(IslandHost host, Step step) {
        return switch (step) {
            case DOCK -> switch (sub(host, StructureType.DOCK)) {
                case 0 -> "Unlock the Dock at the Hub lectern §7(" + Math.min(compressedInStorage(host), dockPrice(host))
                        + "/" + dockPrice(host) + " Compressed Cobble)";
                case 1 -> "Place your Dock anywhere §7(free)";
                default -> "Your Dock is going up…";
            };
            case SHIP -> {
                DockService.Contract first = dock == null ? null : dock.phase(host);
                if (first == null || first.needs().isEmpty()) {
                    yield "Send your first ship from the Dock";
                }
                DockService.Need need = first.needs().get(0);
                yield first.full() ? "The airship is coming for your first shipment"
                        : "Fill the First Shipment at the Dock §7(" + Math.min(need.amount(), first.loaded(need.res()))
                        + "/" + need.amount() + " Compressed)";
            }
            default -> null;
        };
    }

    /** The guide says a few lines to the owner (the Dock's stage beats); in chat when he isn't around. */
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
        speak(player, hand, lines, List.of(
                reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                reply("Thanks, " + name(), NamedTextColor.GRAY, null)));
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
        talkTo(player, hand);
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
            speak(player, hand, List.of(pick(
                    "Afternoon. I keep this isle for " + (owner == null ? "its owner" : owner) + ". Mind the edge.",
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
    }

    // ------------------------------------------------------------------------------------------------
    // conversations
    // ------------------------------------------------------------------------------------------------

    private void talkTo(Player player, Hand hand) {
        hand.waiting = null;
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        advance(host, state);
        if (state.step.actTwo() && (state.kits & CHAIN_BEAT) == 0) {
            chainBeat(player, hand); // clicked him before the first block landed: the beat first
            return;
        }
        prompt(player, hand, false);
    }

    /** Whatever the current step wants said (the one dispatcher for clicks, resumes and step changes). */
    private void prompt(Player player, Hand hand, boolean fresh) {
        State state = state(hand.owner);
        switch (state.step) {
            case WELCOME -> greet(player, hand);
            case HUB -> hubPrompt(player, hand, fresh);
            case QUARRY -> quarryPrompt(player, hand, fresh);
            case MILL -> millPrompt(player, hand, fresh);
            case FEED -> feedPrompt(player, hand, fresh);
            case HUT -> hutPrompt(player, hand, fresh, false);
            case BELT -> beltPrompt(player, hand, fresh);
            case DOCK -> dockPrompt(player, hand, fresh);
            case SHIP -> shipPrompt(player, hand, fresh);
            default -> yardTalk(player, hand);
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
        State state = state(hand.owner);
        state.introduced = true;
        dirty = true;
        IslandHost host = IslandHost.personal(hand.owner);
        if (tourAlreadyDone(host)) {
            state.step = Step.HUB;
            advance(host, state);
            if (state.step == Step.DONE) {
                speak(player, hand, List.of(
                        "Oi! I'm " + name() + ", I keep isles like this one ticking.",
                        "Hub, quarry, Mill, the lot… you don't need me. Nice work."),
                        List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                                reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                                reply("Carry on", NamedTextColor.GRAY, null)));
                return;
            }
            speak(player, hand, List.of(
                    "Oi! I'm " + name() + ", I keep isles like this one ticking.",
                    "Your quarry's already running. I'll show you the rest, one piece at a time."),
                    List.of(reply("Show me", NamedTextColor.GREEN, () -> prompt(player, hand, true)),
                            reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                            reply("I know the drill", NamedTextColor.GRAY, () -> skip(player, hand))));
            return;
        }
        List<TalkAccess.Reply> welcomeReplies = List.of(
                reply("Let's go", NamedTextColor.GREEN, () -> startTour(player, hand)),
                reply("I know the drill", NamedTextColor.GRAY, () -> skip(player, hand)),
                reply("Later", NamedTextColor.DARK_GRAY, () -> speak(player, hand, List.of(
                        "Right. I'll be right here. Give me a shout when you're ready."), List.of())));
        if (state.welcomeSpoken) {
            // Already said the lines this session — offer choices, don't replay line 1.
            speak(player, hand, List.of("Still with me? Hub first, then your starter kit."), welcomeReplies);
            return;
        }
        state.welcomeSpoken = true;
        speak(player, hand, List.of(
                "Oi, welcome! I'm " + name() + ", I keep isles like this one running.",
                "This island is your factory. Quarries dig, machines refine, belts carry it all into your storage.",
                "First the heart of it: your Hub. Put it wherever you like, then I'll hand you your starter kit."),
                welcomeReplies);
    }

    private void startTour(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.step = Step.HUB;
        dirty = true;
        advance(host, state);
        prompt(player, hand, true);
    }

    private void skip(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.step = Step.SKIPPED;
        dirty = true;
        if (!state.quarryGiven && firstHousing(host) == null && hosts.minions(host).isEmpty()) {
            // skipping must never cost the starter quarry (without it there is no first chain)
            state.quarryGiven = true;
            giveQuarry(player);
        }
        if ((state.kits & KIT) == 0) {
            // ...nor the Mill the tour would have handed over
            state.kits |= KIT;
            unlocker.accept(host, StructureType.MILL);
        }
        speak(player, hand, List.of(tourAlreadyDone(host)
                        ? "Fair enough. Hub board, Mill, Dock: the bar up top shows what's next. Change your mind? /island → " + name() + "."
                        : "Fair enough. Quarry, Mill, hut, in the order the stone flows. The Mill's unlocked for you. Change your mind? /island → " + name() + "."),
                List.of());
    }

    // ------------------------------------------------------------------------------------------------
    // the first chain, in the order the stone flows: Hub → kit → quarry → Mill → belt → hut → belt
    // ------------------------------------------------------------------------------------------------

    private void hubPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        PlacedStructure hub = structures.hub(host);
        if (hub != null && hub.building()) {
            speak(player, hand, List.of("Hub's going up. Wait a beat."), List.of());
            return;
        }
        placement.openTool(player, host, PlacementService.Part.HUB);
        speak(player, hand, fresh
                        ? List.of("The Hub first. It's free, and every new part unlocks at its lectern.",
                        "The Build Tool shows it as a hologram, with a sign beside it. Pick a flat spot and right-click."
                                + " Left-click turns it.")
                        : List.of("Still needs a Hub. Pick any flat spot and right-click."),
                List.of(reply("Place the Hub", NamedTextColor.GREEN, () -> placement.openTool(player, host,
                                PlacementService.Part.HUB)),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    /**
     * The starter kit, once per island: the quarry into the hand, the Mill unlocked, free belt tiles for the whole
     * first chain (the first hut is free anyway). True when it was handed over just now.
     */
    private boolean ensureKit(Player player, IslandHost host, State state) {
        if ((state.kits & KIT) != 0) {
            return false;
        }
        state.kits |= KIT;
        state.freeTiles = Math.max(state.freeTiles, kitTiles());
        unlocker.accept(host, StructureType.MILL);
        if (!state.quarryGiven && firstHousing(host) == null) {
            state.quarryGiven = true;
            handQuarry(player, true);
        }
        dirty = true;
        return true;
    }

    /**
     * Right after the Hub: Corin hands over the starter kit, then the one Build Tool lesson of the tour. He doesn't
     * switch the tool for the player: they hold sneak and scroll to the Quarry themselves, once, so they know how
     * once nobody does it for them (the Splitter, every part after the tour). "Switch it for me" is always there.
     */
    private void handKit(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        boolean fresh = ensureKit(player, host, state);
        if (fresh) {
            kitCard(player);
        }
        handQuarry(player, false);
        if (toolLesson(player, hand, fresh)) {
            return;
        }
        placement.openTool(player, host, PlacementService.Part.QUARRY);
        speak(player, hand, fresh
                        ? List.of("Hub's up. That's the heart of your island: new parts unlock at its lectern.",
                        "Here's your starter kit, on me: a Cobble Quarry, a Mill, a Storage Hut and the belts to join them.",
                        "Build them the way the stone flows. Quarry first: it digs on its own, day and night.")
                        : List.of("Quarry first. It's in your bag, and the Build Tool shows its rig."),
                List.of(reply("Place the quarry", NamedTextColor.GREEN, () -> placeQuarry(player, hand)),
                        reply("How does it all work?", NamedTextColor.GOLD, () -> explain(player, hand)),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    /** The kit as a card: the three machines in the order they go, and the belts that join them. */
    private void kitCard(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        boolean card = IslandUi.big(player, "From " + name(), "Your starter kit", List.of(
                        "§fCobble Quarry §7digs stone on its own",
                        "§fMill §7presses 128 stones into one block",
                        "§fStorage Hut §7keeps what the belts bring",
                        "§f" + kitTiles() + " belt tiles §7join them up"),
                Arrays.asList(new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.GRINDSTONE),
                        new ItemStack(Material.BARREL), new ItemStack(Material.IRON_TRAPDOOR)),
                "§7Build them the way the stone flows: quarry, Mill, hut", "§6",
                "§6✦ §eStarter kit from " + name() + " §8· §7quarry → Mill → hut");
        if (!card) {
            player.sendMessage("§6✦ §eStarter kit from " + name() + "§7: a §fCobble Quarry §7(in your bag), a §fMill§7, a"
                    + " §fStorage Hut §7and §f" + kitTiles() + " belt tiles§7.");
            player.sendMessage("§7Build them the way the stone flows: §fquarry §8→ §fMill §8→ §fhut§7.");
        }
    }

    /** "How does it all work?": the whole game in three lines. */
    private void explain(Player player, Hand hand) {
        State state = state(hand.owner);
        speak(player, hand, List.of(
                "Quarries dig raw goods. Machines press 128 of them into one block worth far more.",
                "Belts carry it all, from an orange chute into a green pad. Your huts keep it.",
                "The Hub builds new parts from what's in your huts and your pack, and every stage you ship at the Dock"
                        + " opens more."),
                lessonPending(state)
                        ? List.of(reply("Back to the tool", NamedTextColor.GREEN, () -> toolLesson(player, hand, false)),
                        reply("Switch it for me", NamedTextColor.GOLD, () -> switchForThem(player, hand)))
                        : List.of(reply("Place the quarry", NamedTextColor.GREEN, () -> placeQuarry(player, hand)),
                        reply("Got it", NamedTextColor.GRAY, null)));
    }

    /** The lesson waits: kit handed over, the quarry not down yet, the player never switched parts themselves. */
    private boolean lessonPending(State state) {
        return state != null && state.step == Step.QUARRY && (state.kits & KIT) != 0 && (state.kits & TOOL_TAUGHT) == 0;
    }

    private void taught(Player player, State state) {
        if ((state.kits & TOOL_TAUGHT) == 0) {
            state.kits |= TOOL_TAUGHT;
            dirty = true;
        }
        placement.coach(player, null);
    }

    /**
     * The Build Tool lesson, calm: one tool, one part at a time, and the player switches to the Quarry themselves.
     * The tool stays on whatever it holds (the Belt after the Hub), the action bar says what to do. False when
     * there's nothing to teach (done, or already on the Quarry), so the caller carries on with the quarry.
     */
    private boolean toolLesson(Player player, Hand hand, boolean full) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        if (!lessonPending(state)) {
            return false;
        }
        if (placement.part(player) == PlacementService.Part.QUARRY && placement.holdingTool(player)) {
            taught(player, state); // got there on their own before he asked
            return false;
        }
        if (!placement.holdingTool(player) || placement.part(player) == null) {
            placement.openToolQuiet(player, host, PlacementService.Part.BELT);
        }
        placement.coach(player, COACH);
        speak(player, hand, full
                        ? List.of("Hub's up. That's the heart of your island: new parts unlock at its lectern.",
                        "Here's your starter kit, on me: a Cobble Quarry, a Mill, a Storage Hut and the belts to join them.",
                        "All of it goes down with one tool, the Build Tool in your hand. It holds one part at a time.",
                        "Hold sneak and scroll the mouse wheel to switch the part. Go on, switch it to the Quarry.")
                        : List.of("Hold sneak and scroll the mouse wheel: that switches the Build Tool's part. Switch it to the Quarry."),
                List.of(reply("Switch it for me", NamedTextColor.GOLD, () -> switchForThem(player, hand)),
                        reply("How does it all work?", NamedTextColor.GRAY, () -> explain(player, hand)),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
        return true;
    }

    /** "Switch it for me": he does it, and still says how it goes next time. */
    private void switchForThem(Player player, Hand hand) {
        taught(player, state(hand.owner));
        placeQuarry(player, hand);
        speak(player, hand, List.of("There. Next time it's yours: sneak and scroll, or F for every part at once.",
                        "Now set it down: flat ground, right-click. Leave a few blocks in front of its orange chute."),
                List.of());
    }

    /** The player switched the tool's part by hand (sneak + scroll, the picker): the lesson's moment. */
    private void switched(Player player, PlacementService.Part part) {
        if (player == null || part != PlacementService.Part.QUARRY || !available()) {
            return;
        }
        State state = states.get(player.getUniqueId());
        IslandHost host = hosts.at(player.getLocation());
        if (!lessonPending(state) || host == null || host.isGuild() || !host.id().equals(player.getUniqueId())) {
            return;
        }
        taught(player, state);
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(player);
        }
        chats.remove(player.getUniqueId());
        if (!IslandUi.toast(player, "Switched to the Quarry", "§7Build Tool · sneak + scroll",
                "§8Next: §7set it down on flat ground")) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
        }
        Hand hand = hands.get(player.getUniqueId());
        if (hand == null) {
            player.sendMessage("§a✔ §7That's it: §fsneak + scroll §7switches parts, §fF §7shows them all, §fQ §7puts the tool away.");
            return;
        }
        speak(player, hand, List.of(
                        "That's it. Sneak and scroll walks through your parts, F shows them all at once, Q puts the tool away.",
                        "Now set the quarry down: flat ground, right-click. Leave a few blocks in front of its orange chute for the Mill."),
                List.of(reply("Got it", NamedTextColor.GREEN, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    private void quarryPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        if ((state.kits & KIT) == 0) {
            handKit(player, hand);
            return;
        }
        handQuarry(player, false);
        if (toolLesson(player, hand, false)) {
            return;
        }
        placement.openTool(player, host, PlacementService.Part.QUARRY);
        speak(player, hand, fresh
                        ? List.of("Quarry first. It digs Cobble on its own and drops it out of its orange chute.",
                        "Set it on flat ground, and leave a few blocks in front of the chute for the Mill.")
                        : List.of("Quarry's in the Build Tool. Look at flat ground and right-click."),
                List.of(reply("Place the quarry", NamedTextColor.GREEN, () -> placeQuarry(player, hand)),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    /** Into the hand and the Build Tool; lost it (dropped, despawned)? He has one spare, once. Never a dead end. */
    private void placeQuarry(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        taught(player, state);
        boolean spare = firstHousing(host) == null && (state.kits & SPARE_QUARRY) == 0 && !hasQuarryItem(player);
        if (spare) {
            state.kits |= SPARE_QUARRY;
            dirty = true;
            player.sendMessage("§7" + name() + " hands you his spare quarry.");
        }
        handQuarry(player, spare);
        placement.openTool(player, host, PlacementService.Part.QUARRY);
    }

    private void millPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        ensureKit(player, host, state(hand.owner)); // old saves: the Mill is theirs too
        placement.openTool(player, host, PlacementService.Part.MILL);
        speak(player, hand, fresh
                        ? List.of("It's digging. Now the Mill: it presses 128 Cobble into one Compressed Cobble, worth far more.",
                        "It's in your Build Tool now. Set it a few blocks in front of the quarry's orange chute. It's on me.")
                        : List.of("The Mill next: a few blocks in front of the quarry's chute, then right-click."),
                List.of(reply("Place the Mill", NamedTextColor.GREEN, () ->
                                placement.openTool(player, host, PlacementService.Part.MILL)),
                        reply("Where's the chute?", NamedTextColor.GOLD, () -> {
                            outline(player, firstHousing(host), null, 4);
                            speak(player, hand, List.of("That orange glow is the quarry's chute. The Mill goes in front of it."),
                                    List.of());
                        }),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    private void feedPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        PlacedStructure housing = firstHousing(host);
        PlacedStructure mill = lineMachine(host, StructureType.MILL);
        PlacedStructure old = pullable(host);
        List<TalkAccess.Reply> chips = new ArrayList<>();
        List<String> lines;
        if (old != null) {
            // an island from the old hut-first tour: its quarry still belts straight past the Mill
            lines = List.of("Your quarry's belt still runs past the Mill. I'll pull it up, then you belt it into the Mill.");
            chips.add(reply("Pull up old belt", NamedTextColor.GREEN, () -> pullUp(player, hand, old)));
        } else {
            placement.startBelts(player, host);
            String free = freeNote(host);
            lines = fresh
                    ? List.of("Now join them with a belt. Your Build Tool holds the Belt now.",
                    "Right-click the quarry's orange chute, then the Mill's green pad. The belt lays itself and snaps in." + free)
                    : List.of("Belt: the quarry's orange chute, then the Mill's green pad." + free);
        }
        chips.add(reply("Place the belt", old == null ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                () -> placement.startBelts(player, host)));
        chips.add(reply("Show me", NamedTextColor.GOLD, () -> {
            outline(player, housing, mill, 4);
            speak(player, hand, List.of("Start at the orange glow, end on the Mill. It snaps in."), List.of());
        }));
        chips.add(reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand)));
        speak(player, hand, lines, chips);
    }

    private void hutPrompt(Player player, Hand hand, boolean fresh, boolean primedNow) {
        IslandHost host = IslandHost.personal(hand.owner);
        placement.openTool(player, host, PlacementService.Part.STORAGE_HUT);
        List<String> lines = new ArrayList<>();
        if (primedNow) {
            lines.add("Fed. I tipped a starter load in, so it presses right away. Watch the Mill.");
        }
        if (fresh || primedNow) {
            lines.add("Last piece: a Storage Hut, behind the Mill. It's in your tool, and free. Everything your belts bring lands there.");
            lines.add("The Hub builds new parts from what's in it, so it's your bank too.");
        } else {
            lines.add("Still the hut: behind the Mill, then right-click.");
        }
        speak(player, hand, lines, List.of(
                reply("Place the hut", NamedTextColor.GREEN, () ->
                        placement.openTool(player, host, PlacementService.Part.STORAGE_HUT)),
                reply("Later", NamedTextColor.GRAY, null),
                reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    private void beltPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        PlacedStructure mill = lineMachine(host, StructureType.MILL);
        PlacedStructure hut = firstHut(host);
        placement.startBelts(player, host);
        String free = freeNote(host);
        speak(player, hand, fresh
                        ? List.of("Last belt: the Mill's orange chute into the hut's green pad. Then watch your first block come in." + free)
                        : List.of("Belt: the Mill's orange chute, then the hut's green pad." + free),
                List.of(reply("Place the belt", NamedTextColor.GREEN, () -> placement.startBelts(player, host)),
                        reply("Show me", NamedTextColor.GOLD, () -> {
                            outline(player, mill, hut, 4);
                            speak(player, hand, List.of("Start at the orange glow, end on the hut. It snaps in."), List.of());
                        }),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    /** " The first 32 tiles are on me." while the kit's free tiles last. */
    private String freeNote(IslandHost host) {
        int freeNow = logistics.freeTiles(host);
        return freeNow > 0 ? " The next " + freeNow + " tiles are on me." : "";
    }

    /**
     * The first chain runs and the first Compressed Cobble lies in the hut: the beat (card, sound, a puff on the
     * hut), then he asks the owner over to the Hub, where the next part comes from.
     */
    private void chainBeat(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.kits |= CHAIN_BEAT;
        dirty = true;
        boolean card = IslandUi.big(player, "Island milestone", "First chain running", List.of(
                        "§fQuarry §7digs the stone",
                        "§fMill §7presses 128 into one block",
                        "§fStorage Hut §7keeps it"),
                Arrays.asList(new ItemStack(Material.COBBLESTONE), new ItemStack(Material.GRINDSTONE),
                        new ItemStack(Material.BARREL)),
                "§7Next: the Dock, from your Hub", "§b",
                "§b✦ §fFirst chain running §8· §7quarry → Mill → hut");
        if (!card) {
            player.sendTitle("§b✦ First Chain Running ✦", "§7quarry §8→ §7Mill §8→ §7hut", 10, 70, 20);
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
        boolean compressed = compressedInStorage(host) > 0L;
        hand.waiting = () -> prompt(player, hand, true);
        hand.waitingUntil = System.currentTimeMillis() + 900_000L;
        speak(player, hand, List.of(
                compressed ? "There it is: your first Compressed Cobble. 128 stones in one block."
                        : "There. The whole line runs: quarry, Mill, hut.",
                "That's the whole game, just bigger. Quarries dig, machines refine, belts carry, huts keep.",
                "Come over to the Hub. I'll show you where new parts come from."),
                List.of(reply("On my way", NamedTextColor.GREEN, null),
                        reply("Tell me here", NamedTextColor.GOLD, () -> prompt(player, hand, true))));
    }

    // ------------------------------------------------------------------------------------------------
    // at the Hub: the Dock (the first unlock), then the First Shipment and the ship
    // ------------------------------------------------------------------------------------------------

    /** The Dock: the lectern and how new parts come (it costs the Compressed Cobble just made), then build it. */
    private void dockPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        hand.waiting = null;
        int sub = sub(host, StructureType.DOCK);
        state.lastSub = sub;
        Runnable skip = () -> skip(player, hand);
        if (sub == 0) {
            long have = compressedInStorage(host);
            long price = Math.max(1L, dockPrice(host));
            String cost = price == 1L ? "one Compressed Cobble" : price + " Compressed Cobble";
            List<String> lines = fresh
                    ? List.of("This lectern is where new parts come from. Each one costs goods from your huts, and every stage opens more.",
                    "Next is the Dock: contracts come in there, and an airship takes your goods to the mainland.",
                    "It costs " + cost + (have >= price ? ", and you've just made it. Unlock it." : ". Your Mill's on it: you've got " + have + "."))
                    : List.of("Dock: unlock it at the Hub lectern for " + cost + ". You've got " + have + ".");
            speak(player, hand, lines, List.of(
                    reply("Open the lectern", NamedTextColor.GREEN, () -> openLectern.accept(player, host)),
                    reply("Later", NamedTextColor.GRAY, null),
                    reply("Skip tour", NamedTextColor.DARK_GRAY, skip)));
            return;
        }
        if (sub == 1) {
            placement.openTool(player, host, PlacementService.Part.DOCK);
            speak(player, hand, fresh
                            ? List.of("Unlocked. Building it is on me.", "Put it anywhere. Near the island's edge looks the part.")
                            : List.of("Still the Dock. Pick a spot and right-click."),
                    List.of(reply("Place the Dock", NamedTextColor.GREEN, () ->
                                    placement.openTool(player, host, PlacementService.Part.DOCK)),
                            reply("Later", NamedTextColor.GRAY, null),
                            reply("Skip tour", NamedTextColor.DARK_GRAY, skip)));
            return;
        }
        speak(player, hand, List.of("Your Dock's going up. Give it a moment."), List.of());
    }

    /** The first contract and the airship: the end of stage 0. */
    private void shipPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        DockService.Contract first = dock == null ? null : dock.phase(host);
        long need = first == null || first.needs().isEmpty() ? 2L : first.needs().get(0).amount();
        List<String> lines = fresh
                ? List.of("Your first contract is on the Dock board: " + need + " Compressed Cobble for the broker in Southwood.",
                "Open the Dock and load it from your hut, or belt the Mill straight in. When it's full, the airship comes.",
                "Ship it and your island climbs to stage 1. The broker pays with a second quarry.")
                : List.of("First Shipment: " + need + " Compressed Cobble at the Dock. Load it from your hut or belt it in.");
        speak(player, hand, lines, List.of(
                reply("Open the Dock", NamedTextColor.GREEN, () -> openDock.accept(player, host)),
                reply("What comes later?", NamedTextColor.GOLD, () -> later(player, hand)),
                reply("Later", NamedTextColor.GRAY, null)));
    }

    /** The road ahead, while the first shipment fills: what each stage opens and how you get there. */
    private void later(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        speak(player, hand, List.of(
                "Each stage, the broker sends a bigger order. Ship it at the Dock and the Hub opens the next parts.",
                "Stage 1, the Werkhof: a second quarry, the Splitter, quarries up to level 3.",
                "Stage 2, the Foreman's Hall: the Forge, 128 Compressed into one Compacted, plus the Sorter and the Overflow Gate.",
                "Stage 3, the Grand Hub: every Mill and Forge runs twice as fast, and you can buy land."),
                List.of(reply("Open the Dock", NamedTextColor.GREEN, () -> openDock.accept(player, host)),
                        reply("Got it", NamedTextColor.GRAY, null)));
    }

    /** The tour ended some other way than the ship (an admin stage, an old save): a short goodbye. */
    private void finishTalk(Player player, Hand hand) {
        speak(player, hand, List.of("You're set. Quarries dig, machines refine, the Dock ships. The bar up top shows what's next."),
                List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                        reply("/island", NamedTextColor.GOLD, () -> openHub.accept(player))));
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

    /** Old hut-first islands: the quarry still belts straight past the Mill-to-be (that belt can go). */
    private PlacedStructure pullable(IslandHost host) {
        PlacedStructure mill = lineMachine(host, StructureType.MILL);
        if (mill == null || logistics.feeding(mill) > 0) {
            return null;
        }
        PlacedStructure from = firstHousing(host);
        LogisticsService.Route out = from == null ? null : logistics.routeFrom(from);
        if (out == null || out.to() == null || out.to() == mill || out.length() == 0) {
            return null;
        }
        return from;
    }

    private void pullUp(Player player, Hand hand, PlacedStructure from) {
        IslandHost host = IslandHost.personal(hand.owner);
        int tiles = logistics.pullUp(player, host, from);
        placement.startBelts(player, host);
        speak(player, hand, List.of((tiles > 0 ? "Up. " : "") + "Now the quarry's chute into the Mill." + freeNote(host)),
                List.of(reply("Place the belt", NamedTextColor.GREEN, () -> placement.startBelts(player, host)),
                        reply("Show me", NamedTextColor.GOLD, () -> outline(player, firstHousing(host),
                                lineMachine(host, StructureType.MILL), 4))));
    }

    /** Orange on {@code from}'s chute, gold round {@code to} (either may be null), a few pulses, only on request. */
    private void outline(Player player, PlacedStructure from, PlacedStructure to, int pulses) {
        int[] port = from == null ? null : structures.outputPort(from);
        if (port != null) {
            square(player, port[0], port[1] + 0.1, port[2], 0, ORANGE, pulses);
            player.spawnParticle(Particle.DUST, port[0] + 0.5, port[1] + 0.6, port[2] + 0.5, 6, 0.1, 0.25, 0.1, 0, ORANGE);
        } else if (from != null) {
            outlineStructure(player, from, ORANGE, pulses);
        }
        outlineStructure(player, to, GOLD, pulses);
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
        speak(player, hand, List.of(pick(
                        "Need a hand?",
                        "Belts humming, hut filling. What can I do for you?",
                        "Good day for hauling stone. What's up?")),
                List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                        reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                        factoryLeft
                                ? reply("Walk me through the factory", NamedTextColor.YELLOW, () -> {
                            State state = state(hand.owner);
                            state.step = Step.HUB;
                            dirty = true;
                            advance(host, state);
                            prompt(player, hand, true);
                        })
                                : reply("Run the tour again", NamedTextColor.GRAY, () -> startTour(player, hand)),
                        reply("Bye", NamedTextColor.DARK_GRAY, () -> speak(player, hand, List.of("Mind the edge."), List.of()))));
    }

    private void tip(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        if (state(hand.owner).step.touring()) {
            prompt(player, hand, false);
            return;
        }
        FactoryGoals.Goal goal = goals == null ? null : goals.next(host);
        if (goal != null) {
            speak(player, hand, List.of(goal.title() + " — " + goals.howNow(host, goal)),
                    List.of(reply("Build Tool", NamedTextColor.GOLD, () -> placement.openTool(player, host, null)),
                            reply("Got it", NamedTextColor.GRAY, null)));
            return;
        }
        String line;
        PlacedStructure hut = firstHut(host);
        if (!structures.hasWorkshop(host)) {
            line = "Build a Hub next. Lectern unlocks machines.";
        } else if (structures.count(host, StructureType.MILL) == 0) {
            line = "Unlock Mill at Hub → belt quarry → Mill → hut.";
        } else if (structures.count(host, StructureType.FORGE) == 0) {
            line = "Unlock Forge at Hub → belt Mill → Forge → hut.";
        } else if (hut != null && hut.level() < 2) {
            line = "Hut → Upgrade for a loft (more storage).";
        } else if (hosts.parcels(host).size() <= 9) {
            line = "Buy land next to yours for more yard.";
        } else if (hosts.tier(host) < 2) {
            line = "Raise Island Tier for more belts and machines.";
        } else {
            line = "You're past the tour. Keep the line running.";
        }
        speak(player, hand, List.of(line), List.of(reply("/island", NamedTextColor.GOLD, () -> openHub.accept(player)),
                reply("Thanks", NamedTextColor.GRAY, null)));
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
        if (player.getUniqueId().equals(hand.owner)) {
            hand.metOwner = true;
        }
        TalkAccess talk = AetherServices.talk();
        if (talk == null || !talk.bubbles(player)) {
            chatFallback(player, lines, replies);
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
        double reach = Math.max(4.0, talk.reach() - 1.5);
        if (player.getLocation().distanceSquared(hand.at) > reach * reach) {
            // he never walks over: from further away the rest comes as chat lines (with the choices as buttons)
            chats.remove(player.getUniqueId());
            chatFallback(player, new ArrayList<>(chat.lines), chat.replies);
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
            state.step = Step.QUARRY; // the hut-first tour's press / Forge steps: the island itself says where we are
        }
        if (state.step.touring() && state.step != Step.HUB && structures.hub(host) == null) {
            state.step = Step.HUB; // the Hub came down (or never stood): Hub first again
        }
        if (state.step == Step.HUB && stands(structures.hub(host))) {
            state.step = Step.QUARRY;
        }
        if (state.step == Step.QUARRY && (stands(firstHousing(host)) || !hosts.minions(host).isEmpty())) {
            state.step = Step.MILL;
        }
        if (state.step == Step.MILL && stands(lineMachine(host, StructureType.MILL))) {
            state.step = Step.FEED;
        }
        if (state.step == Step.FEED && millFed(host)) {
            state.step = Step.HUT;
        }
        if (state.step == Step.HUT && stands(firstHut(host))) {
            state.step = Step.BELT;
        }
        if (state.step == Step.BELT && inLine(host, StructureType.MILL)) {
            state.step = Step.DOCK;
        }
        if (state.step == Step.DOCK && (dock == null || dock.stage(host) >= 1 || builtDock(host))) {
            state.step = Step.SHIP;
        }
        if (state.step == Step.SHIP && (dock == null || dock.stage(host) >= 1)) {
            state.step = Step.DONE;
        }
        if (state.step != before) {
            state.lastSub = -1;
            dirty = true;
            return true;
        }
        return false;
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
        if (lessonPending(state)) {
            // the Build Tool lesson: on the Quarry by any road counts; else the tool's bar keeps saying how
            if (placement.holdingTool(owner) && placement.part(owner) == PlacementService.Part.QUARRY) {
                switched(owner, PlacementService.Part.QUARRY);
                return;
            }
            placement.coach(owner, COACH);
        } else {
            placement.coach(owner, null);
        }
        if (hand.waiting != null && !busy && hand.at != null && owner.getWorld().equals(hand.at.getWorld())) {
            // "come over to the Hub": he picks it up once the owner stands by him
            double reach = talk == null ? 8.0 : Math.max(4.0, talk.reach() - 2.0);
            if (owner.getLocation().distanceSquared(hand.at) <= reach * reach) {
                Runnable then = hand.waiting;
                hand.waiting = null;
                then.run();
                return;
            }
            if (System.currentTimeMillis() > hand.waitingUntil) {
                hand.waiting = null; // the bar keeps the step; clicking him picks it up again
            }
        }
        if (!state.step.touring() || busy) {
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
            if (talk != null) {
                talk.end(owner);
            }
            if (!stepToast(owner, before)) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
            }
            boolean primedNow = before.ordinal() <= Step.FEED.ordinal() && primeOnce(host, state);
            Step reached = state.step;
            if (reached == Step.MILL || reached == Step.FEED || reached == Step.HUT || reached == Step.BELT) {
                // a breath between steps: the "done" card and the new part settle first, then he says what's next
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (owner.isOnline() && state(island.ownerId()).step == reached
                            && !chats.containsKey(owner.getUniqueId())) {
                        switch (reached) {
                            case MILL -> millPrompt(owner, hand, true);
                            case FEED -> feedPrompt(owner, hand, true);
                            case HUT -> hutPrompt(owner, hand, true, primedNow);
                            default -> beltPrompt(owner, hand, true);
                        }
                    }
                }, Math.max(0L, plugin.getConfig().getLong("guide.step-pause-ticks", 40L)));
                return;
            }
            switch (state.step) {
                case HUB -> hubPrompt(owner, hand, false);
                case QUARRY -> {
                    if (before == Step.HUB) {
                        // the Hub ritual plays first (bells, beam, title); he takes his post by the Hub, then the kit
                        settle(hand);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (owner.isOnline() && state(island.ownerId()).step == Step.QUARRY
                                    && !chats.containsKey(owner.getUniqueId())) {
                                handKit(owner, hand);
                            }
                        }, 110L);
                    } else {
                        quarryPrompt(owner, hand, true);
                    }
                }
                case MILL -> millPrompt(owner, hand, true);
                case FEED -> feedPrompt(owner, hand, true);
                case HUT -> hutPrompt(owner, hand, true, primedNow);
                case BELT -> beltPrompt(owner, hand, true);
                case DOCK, SHIP -> {
                    if ((state.kits & CHAIN_BEAT) == 0) {
                        state.beatSince = System.currentTimeMillis(); // the beat comes with the first block
                    } else {
                        prompt(owner, hand, true);
                    }
                }
                case DONE -> {
                    if (before != Step.SHIP) {
                        finishTalk(owner, hand); // the Dock plays the first ship's beat itself and he speaks there
                    }
                }
                default -> {
                }
            }
            return;
        }
        // inside the Dock step: unlocked, built. Each bit gets its own next line
        if (state.step == Step.DOCK && hand.waiting == null) {
            int sub = sub(host, StructureType.DOCK);
            if (state.lastSub >= 0 && sub != state.lastSub) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                dockPrompt(owner, hand, sub > state.lastSub);
                return;
            }
            if (state.lastSub < 0) {
                state.lastSub = sub;
            }
        }
        // the owner is home mid-tour and he hasn't spoken to them since he showed up: the current step, once
        if (!hand.metOwner && hand.waiting == null) {
            hand.metOwner = true;
            prompt(owner, hand, false);
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
                    if (y == Integer.MIN_VALUE || structures.atColumn(world, bx, bz) != null
                            || logistics.isBelt(world, bx, y + 1, bz)) {
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
    }

    private void despawn(Hand hand) {
        if (hand.holo != null && hand.holo.isValid()) {
            hand.holo.remove();
        }
        hand.holo = null;
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
        String status = step == Step.WELCOME ? "§6✦ §eRight-click me"
                : step.touring() ? "§6✦ §eTour · step " + (progress(hand.owner) + 1) + "/" + total()
                : "§7Right-click for tips";
        String text = "§e§l" + name() + "\n§7" + title() + "\n" + status;
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
        boolean old = yaml.getInt("version", 1) < SAVE_VERSION;
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
                if (old) {
                    // the hut-first tour: its steps meant other things. Past the first chain stays past it; the rest
                    // starts again at the Hub and advance() skips whatever the island already shows
                    if (state.step == Step.DOCK || state.step == Step.SHIP) {
                        state.kits |= CHAIN_BEAT;
                    } else if (state.step.touring()) {
                        state.step = Step.HUB;
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
