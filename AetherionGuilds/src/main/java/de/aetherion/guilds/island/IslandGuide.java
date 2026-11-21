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
 * The isle hand (Corin by default, {@code guide.name}): a FancyNPC on every personal starter island who walks over
 * to the owner and explains the island one piece at a time, in the TalkUx speech bubble:
 * <ol>
 *   <li>the <b>Hub</b> first, free, wherever the player likes,</li>
 *   <li>a <b>Storage Hut</b> (the first one is free), wherever they like,</li>
 *   <li>a <b>Cobble Quarry</b>, on the house, straight into the hand,</li>
 *   <li>a short <b>belt</b> from its chute into the hut (the first tiles are free),</li>
 *   <li>the <b>Mill</b>: unlocked at the Hub lectern and built, both free on the tour, belted quarry → Mill → hut,</li>
 *   <li>the first <b>Compressed Cobble</b> in the hut (he tips a starter load into the Mill so it comes at once).</li>
 * </ol>
 * There are no pads, rings or marked spots: the player picks every place. Belt ports glow only when asked for.
 * Every step is detected from the island itself, so the tour never blocks building: do things in any order, walk
 * away, skip it, pick it up again from {@code /island}. The Forge and everything after it are factory goals.
 *
 * <p>Without the bubble (TalkUx off, player on classic chat talk) the same lines and choices go to chat.
 * Without FancyNpcs there's no guide: the classic "first steps" card is used instead.
 */
public final class IslandGuide implements Listener {

    /**
     * The tour, Hub first: Hub → Storage Hut → quarry → belt → Mill → first Compressed Cobble in the hut. No gold
     * pads or rings: you put every building where you like, the guide walks over to you and explains it.
     * FORGE stays only so old saves still load (it skips straight on); the Forge is a later goal, not the tour.
     */
    public enum Step {
        WELCOME,
        HUB,
        HUT,
        QUARRY,
        BELT,
        MILL,
        FORGE,
        COMPACT,
        DONE,
        SKIPPED;

        public boolean touring() {
            return ordinal() >= HUB.ordinal() && ordinal() <= COMPACT.ordinal();
        }

        /** The machine half of the tour (the first chain runs; now the Mill and its first Compressed Cobble). */
        public boolean actTwo() {
            return ordinal() >= MILL.ordinal() && ordinal() <= COMPACT.ordinal();
        }

        public boolean finished() {
            return this == DONE || this == SKIPPED;
        }
    }

    private static final String NPC_PREFIX = "aeg_hand_";
    private static final String SPEAKER_PREFIX = "isle_hand_";
    private static final String HOLO_TAG = "aeg_hand_holo";
    private static final double WALK_STEP = 0.27;
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 80), 1.3f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 150, 40), 1.4f);

    private static final class State {
        Step step = Step.WELCOME;
        boolean introduced;
        /** Session-only: full welcome lines already played — later clicks get chips, not a line-1 restart. */
        boolean welcomeSpoken;
        boolean quarryGiven;
        int freeTiles;
        /** Act two: belt kits handed out (bit 1 Mill) and the Mill primed for the finale. */
        int kits;
        boolean primed;
        /** Session-only: last sub-step seen in the Mill step (locked, unlocked, built), -1 = none yet. */
        int lastSub = -1;
    }

    private static final class Hand {
        final UUID owner;
        final String npcName;
        final String speakerId;
        Object npc;
        Location at;
        Location home;
        TextDisplay holo;
        String holoText = "";
        Location walkTo;
        Runnable onArrive;
        long lastSeen;
        boolean spawning;
        /** "This way!": what he says once the owner catches up with him at the next spot. */
        Runnable waiting;
        long waitingUntil;
        /** He came over to the owner once since he (re)appeared on the island. */
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
        boolean waitingForWalk;

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
        logistics.setBeltPass(new LogisticsService.BeltPass() {
            @Override
            public boolean noWorkshopNeeded(Player player, IslandHost host) {
                if (host != null && !host.isGuild() && !available()) {
                    // no Hollis on this server: the first chain's belt still needs no Workshop
                    for (LogisticsService.Route route : logistics.routes(host)) {
                        if (route.to() != null && route.from().type() == StructureType.QUARRY_HOUSING) {
                            return false;
                        }
                    }
                    return true;
                }
                State state = host == null || host.isGuild() ? null : states.get(host.id());
                return state != null && state.step == Step.BELT && state.freeTiles > 0;
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

    public void attachGoals(FactoryGoals goals) {
        this.goals = goals;
    }

    /** The Hub lectern menu (where the tour's Mill and Forge unlock). */
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

    /** Hollis can stand on islands (FancyNpcs is there and the guide is on). */
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
        return switch (step) {
            case WELCOME -> "Talk to " + name();
            case HUB -> {
                PlacedStructure hub = structures.hub(IslandHost.personal(owner));
                yield hub != null && hub.building() ? "Hub going up…" : "Place your Hub anywhere §7(free)";
            }
            case HUT -> "Place a Storage Hut anywhere §7(free)";
            case QUARRY -> state != null && state.quarryGiven
                    ? "Set the quarry down: right-click flat ground"
                    : "Talk to " + name() + " — get a quarry";
            case BELT -> "Belt the quarry into the hut";
            case MILL, FORGE, COMPACT -> factoryObjective(IslandHost.personal(owner), step);
            default -> null;
        };
    }

    /** Steps in the whole tour (for "step n/6"). */
    public int total() {
        return 6;
    }

    /** 0..6 steps done (for progress bars). */
    public int progress(UUID owner) {
        return switch (step(owner)) {
            case HUT -> 1;
            case QUARRY -> 2;
            case BELT -> 3;
            case MILL, FORGE -> 4;
            case COMPACT -> 5;
            case DONE, SKIPPED -> 6;
            default -> 0;
        };
    }

    /** True while the guide still runs the first chain himself (the factory goals wait until then). */
    public boolean firstAct(UUID owner) {
        Step step = step(owner);
        return available() && objective(owner) != null && !step.actTwo();
    }

    /** The part the tour wants unlocked at the Hub right now (the lectern shows it glowing and free), or null. */
    public StructureType tourUnlock(IslandHost host) {
        if (host == null || host.isGuild() || !available()) {
            return null;
        }
        Step step = step(host.id());
        return step == Step.MILL && !structures.unlocked(host, StructureType.MILL) ? StructureType.MILL : null;
    }

    /** While the tour runs, the first Hub and Mill are on the house (the first hut is always free). */
    public boolean freeFirst(IslandHost host, StructureType type) {
        if (host == null || host.isGuild() || !available()) {
            return false;
        }
        if (type != StructureType.WORKSHOP && type != StructureType.MILL) {
            return false;
        }
        State state = states.get(host.id());
        return state != null && state.step.touring();
    }

    /** Is the tour running on this island (the Hub lectern then only lights the tour's part)? */
    public boolean inFactoryTour(IslandHost host) {
        return host != null && !host.isGuild() && available() && step(host.id()).touring();
    }

    /** The tour stages its own finale for the Mill goal (no second title on top). */
    public boolean ownsTitle(IslandHost host, String goalId) {
        if (host == null || host.isGuild() || !available() || !"mill_line".equals(goalId)) {
            return false;
        }
        State state = states.get(host.id());
        return state != null && (state.step == Step.COMPACT || (state.step == Step.DONE && state.primed));
    }

    private String factoryObjective(IslandHost host, Step step) {
        return switch (step) {
            case MILL, FORGE -> {
                StructureType type = StructureType.MILL;
                String name = type.display();
                yield switch (sub(host, type)) {
                    case 0 -> "Unlock the " + name + " at the Hub lectern §7(free)";
                    case 1 -> "Place a " + name + " anywhere §7(free)";
                    default -> {
                        PlacedStructure[] leg = missingLeg(host, type);
                        yield leg == null ? "Belt " + name + " into the line"
                                : "Belt " + label(leg[0]) + " → " + label(leg[1]);
                    }
                };
            }
            case COMPACT -> "Watch Compressed Cobble land in the hut";
            default -> null;

        };
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

    /** Right after the claim ceremony: Hollis walks up and says hello. False = no guide (caller shows the card). */
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
        prompt(player, hand, false);
    }

    /** Whatever the current step wants said (the one dispatcher for clicks, resumes and step changes). */
    private void prompt(Player player, Hand hand, boolean fresh) {
        State state = state(hand.owner);
        switch (state.step) {
            case WELCOME -> greet(player, hand);
            case HUT -> hutPrompt(player, hand, fresh);
            case QUARRY -> quarryPrompt(player, hand, fresh);
            case BELT -> beltPrompt(player, hand, fresh);
            case HUB -> hubPrompt(player, hand, fresh);
            case MILL, FORGE -> machinePrompt(player, hand, StructureType.MILL, fresh);
            case COMPACT -> compactPrompt(player, hand);
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
                walkHome(hand);
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
                        "Right. I'll be in the yard. Give me a shout when you're ready."), List.of())));
        if (state.welcomeSpoken) {
            // Already said the two lines this session — offer choices, don't replay line 1.
            speak(player, hand, List.of("Still with me? Hub first, then I'll show you the rest, one piece at a time."),
                    welcomeReplies);
            return;
        }
        state.welcomeSpoken = true;
        speak(player, hand, List.of(
                "Oi, welcome! I'm " + name() + ", I keep isles like this one running.",
                "This island is your factory. Quarries dig, belts carry, machines refine. It all lands in your storage.",
                "We start with the heart of it: your Hub. Put it wherever you like. I'll explain the rest piece by piece."),
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
        state.step = Step.SKIPPED;
        dirty = true;
        if (!state.quarryGiven && firstHousing(IslandHost.personal(hand.owner)) == null
                && hosts.minions(IslandHost.personal(hand.owner)).isEmpty()) {
            // skipping must never cost the starter quarry (without it there is no first chain)
            state.quarryGiven = true;
            giveQuarry(player);
        }
        speak(player, hand, List.of(state.step == Step.SKIPPED && tourAlreadyDone(IslandHost.personal(hand.owner))
                        ? "Fair enough. The Mill compresses, the Hub unlocks the rest. Change your mind? /island → " + name() + "."
                        : "Fair enough. Hub, hut, quarry, belt: you've got this. Change your mind? /island → " + name() + "."),
                List.of());
        walkHome(hand);
    }

    /** No pad, no marker: he comes over to wherever you are and you put the hut where you like. */
    private void hutPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        placement.openTool(player, host, PlacementService.Part.STORAGE_HUT);
        speak(player, hand, fresh
                        ? List.of("Next, a Storage Hut. Free. Whatever your belts bring ends up in there.",
                        "Put it wherever you like. Close to the Hub keeps the belts short.")
                        : List.of("Still the hut. Pick a spot and right-click."),
                List.of(reply("Place the hut", NamedTextColor.GREEN, () ->
                                placement.openTool(player, host, PlacementService.Part.STORAGE_HUT)),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    private void quarryPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        boolean gift = !state.quarryGiven;
        List<String> lines = fresh || gift
                ? List.of("A Cobble Quarry, free. It's in your hand. It digs stone on its own, day and night.",
                "Set it down on flat ground, wherever you like. Right-click.")
                : List.of("Quarry's in your hand. Right-click flat ground where you want it.");
        lead(player, hand, null, () -> {
            if (gift && !state.quarryGiven) {
                state.quarryGiven = true;
                dirty = true;
                giveQuarry(player);
            } else {
                handQuarry(player, false);
            }
            speak(player, hand, lines, List.of(
                reply("Place the quarry", NamedTextColor.GREEN, () -> {
                    // lost it (dropped, despawned)? Hollis has one spare, once; never a dead end
                    boolean spare = firstHousing(host) == null && (state.kits & SPARE_QUARRY) == 0
                            && !hasQuarryItem(player);
                    if (spare) {
                        state.kits |= SPARE_QUARRY;
                        dirty = true;
                        player.sendMessage("§7" + name() + " hands you his spare quarry.");
                    }
                    handQuarry(player, spare);
                }),
                reply("Later", NamedTextColor.GRAY, null),
                reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
        });
    }

    /** The quarry line once the hut line is through (retries while a bubble still runs; gives up quietly). */
    private void quarryPromptSoon(Player owner, Hand hand, UUID ownerId, long delay, int tries) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            State now = state(ownerId);
            if (!owner.isOnline() || now.step != Step.QUARRY) {
                return;
            }
            if (chats.containsKey(owner.getUniqueId())) {
                if (tries > 0) {
                    quarryPromptSoon(owner, hand, ownerId, 40L, tries - 1);
                }
                return;
            }
            quarryPrompt(owner, hand, true);
        }, delay);
    }

    private void beltPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        PlacedStructure housing = firstHousing(host);
        PlacedStructure hut = firstHut(host);
        Location stand = null;
        if (housing != null && hut != null) {
            int mx = (housing.x() + hut.x()) / 2;
            int mz = (housing.z() + hut.z()) / 2;
            stand = spotNear(host, mx + 2, mz + 2, mx, mz);
        }
        int freeNow = logistics.freeTiles(host);
        String free = freeNow > 0 ? " The first " + freeNow + " tiles are on me." : "";
        lead(player, hand, stand, () -> {
            placement.startBelts(player, host);
            speak(player, hand, fresh
                            ? List.of("Now connect them. A belt carries whatever the quarry digs.",
                            "Right-click the quarry's chute, then right-click the hut. It snaps in." + free)
                            : List.of("Belt: right-click the quarry's chute, then the hut." + free),
                    List.of(reply("Place the belt", NamedTextColor.GREEN, () -> placement.startBelts(player, host)),
                            reply("Where's the chute?", NamedTextColor.GOLD, () -> {
                                // only on request: a short glow on the chute and the hut, never a spot to build on
                                outlineChute(player, host, 4);
                                speak(player, hand, List.of("That glow is the chute. Start there, end on the hut."),
                                        List.of());
                            }),
                            reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
        });
    }

    /** End of act one (the first chain runs): the beat, then on to the Mill. */
    private void finishTour(Player player, Hand hand) {
        State state = state(hand.owner);
        dirty = true;
        player.sendTitle("§6✦ First Chain Running ✦", "§7quarry §8→ §7belt §8→ §7hut", 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            }
        }, 8L);
        if (hand.at != null && hand.at.getWorld() != null) {
            de.aetherion.core.api.Vfx.puff(hand.at.clone().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 40, Math.max(0.5, 0.5), null);
        }
        if (state.step.actTwo()) {
            speak(player, hand, List.of("Chain's alive. Stone flows into your hut on its own now.",
                            "Raw cobble is cheap, though. Next we make it worth more: the Mill."),
                    List.of(reply("Show me", NamedTextColor.GREEN, () -> prompt(player, hand, true)),
                            reply("Later", NamedTextColor.GRAY, null)));
            return;
        }
        speak(player, hand, List.of("Chain's alive. You're set."),
                List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                        reply("/island", NamedTextColor.GOLD, () -> openHub.accept(player))));
        walkHome(hand);
    }

    // ------------------------------------------------------------------------------------------------
    // the Hub first, then (act two) the Mill and its first Compressed Cobble
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
                        ? List.of("The Hub. Free. Every machine you build later unlocks at its lectern.",
                        "The Build Tool shows it as a hologram. Walk to a flat spot you like and right-click. Left-click turns it.")
                        : List.of("Still needs a Hub. Pick any flat spot and right-click."),
                List.of(reply("Place the Hub", NamedTextColor.GREEN, () -> placement.openTool(player, host,
                                PlacementService.Part.HUB)),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
    }

    /** The Mill: unlock it at the lectern, place it, belt it in. Whichever bit the island is at. */
    private void machinePrompt(Player player, Hand hand, StructureType type, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        String name = type.display();
        int stage = stage(host, type);
        state.lastSub = stage;
        giveKit(state, type);
        Runnable skip = () -> skip(player, hand);
        switch (stage) {
            case 0 -> {
                PlacedStructure hub = structures.hub(host);
                Location stand = hub == null ? null : spotNear(host, hub.x() + 3, hub.z() + 3, hub.x(), hub.z());
                List<String> lines = fresh
                        ? List.of("Machines unlock at the Hub. Open its lectern and unlock the " + name + ". Free on the tour.")
                        : List.of("Unlock the " + name + " at the Hub lectern first.");
                Runnable talk = () -> speak(player, hand, lines, List.of(
                        reply("Open the lectern", NamedTextColor.GREEN, () -> openLectern.accept(player, host)),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, skip)));
                if (fresh) {
                    lead(player, hand, stand, talk);
                } else {
                    talk.run();
                }
            }
            case 1 -> {
                List<String> lines = fresh
                        ? List.of("The " + name + " presses 128 raw cobble into one Compressed Cobble. Free on the tour.",
                        "Put it between the quarry and the hut, wherever it fits. Right-click.")
                        : List.of("Still the " + name + ". Pick a spot and right-click.");
                placement.openTool(player, host, PlacementService.Part.of(type));
                speak(player, hand, lines, List.of(
                        reply("Place the " + name, NamedTextColor.GREEN, () ->
                                placement.openTool(player, host, PlacementService.Part.of(type))),
                        reply("Later", NamedTextColor.GRAY, null),
                        reply("Skip tour", NamedTextColor.DARK_GRAY, skip)));
            }
            default -> {
                PlacedStructure[] leg = missingLeg(host, type);
                PlacedStructure old = pullable(host, type);
                int freeNow = logistics.freeTiles(host);
                String free = freeNow > 0 ? " " + freeNow + " tiles free." : "";
                String line;
                if (old != null) {
                    line = "The old belt runs past the " + name + ". I'll pull it up, then you belt the new line.";
                } else if (leg != null) {
                    line = "Belt " + label(leg[0]) + " → " + label(leg[1]) + "." + free;
                } else {
                    line = "Belt quarry → " + name + " → hut.";
                }
                List<TalkAccess.Reply> chips = new ArrayList<>();
                if (old != null) {
                    chips.add(reply("Pull up old belt", NamedTextColor.GREEN, () -> pullUp(player, hand, type, old)));
                }
                chips.add(reply("Place the belt", old == null ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                        () -> placement.startBelts(player, host)));
                chips.add(reply("Show me", NamedTextColor.GOLD, () -> {
                    outlineLeg(player, host, type, 4);
                    speak(player, hand, List.of("Start at the orange glow, end on the gold one. It snaps in."), List.of());
                }));
                chips.add(reply("Skip tour", NamedTextColor.DARK_GRAY, skip));
                Location stand = null;
                if (leg != null) {
                    int mx = (leg[0].x() + leg[1].x()) / 2;
                    int mz = (leg[0].z() + leg[1].z()) / 2;
                    stand = spotNear(host, mx + 2, mz + 2, mx, mz);
                }
                Runnable talk = () -> {
                    if (old == null) {
                        placement.startBelts(player, host);
                    }
                    speak(player, hand, List.of(line), chips);
                };
                if (fresh) {
                    lead(player, hand, stand, talk);
                } else {
                    talk.run();
                }
            }
        }
    }

    private void pullUp(Player player, Hand hand, StructureType type, PlacedStructure from) {
        IslandHost host = IslandHost.personal(hand.owner);
        int tiles = logistics.pullUp(player, host, from);
        PlacedStructure[] leg = missingLeg(host, type);
        State state = state(hand.owner);
        int freeNow = logistics.freeTiles(host);
        String free = freeNow > 0 ? " Next " + freeNow + " tiles are on me." : "";
        placement.startBelts(player, host);
        speak(player, hand, List.of((tiles > 0 ? "Up. " : "") + (leg == null ? "Belt it in."
                        : label(leg[0]) + " → " + label(leg[1]) + "." + free)),
                List.of(reply("Place the belt", NamedTextColor.GREEN, () -> placement.startBelts(player, host)),
                        reply("Show me", NamedTextColor.GOLD, () -> outlineLeg(player, host, type, 4))));
    }

    /** The last step: the Mill needs 128 raw for one Compressed. The guide tips in a starter load. */
    private void compactPrompt(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        boolean primedNow = false;
        if (!state.primed && prime(host)) {
            state.primed = true;
            primedNow = true;
            dirty = true;
        }
        String line = primedNow || state.primed
                ? "I tipped a starter load into the Mill. Watch the hut: your first Compressed Cobble is on its way."
                : "Keep the line running. Compressed Cobble lands in the hut.";
        speak(player, hand, List.of(line), List.of(reply("Watching", NamedTextColor.GREEN, null),
                reply("/island", NamedTextColor.GOLD, () -> openHub.accept(player))));
    }

    /** Tip a starter load into the Mill that's in the line, so the first Compressed Cobble comes out at once. */
    private boolean prime(IslandHost host) {
        PlacedStructure mill = null;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() != StructureType.MILL || structure.building() || logistics.feeding(structure) == 0) {
                continue;
            }
            LogisticsService.Route out = logistics.routeFrom(structure);
            if (out != null && out.to() != null) {
                mill = structure;
                break;
            }
        }
        if (mill == null) {
            return false;
        }
        long room = mill.capacity() - PlacedStructure.total(mill.input());
        long load = Math.min(2L * Res.UNIT, room);
        if (load < Res.UNIT) {
            return false; // already full: it presses one any second anyway
        }
        PlacedStructure.add(mill.input(), Res.of(QuarryType.COBBLESTONE, Res.Form.RAW), load);
        puff(hosts.world(host), mill);
        structures.markDirty();
        return true;
    }

    private long compressedInStorage(IslandHost host) {
        return structures.stock(host, Res.of(QuarryType.COBBLESTONE, Res.Form.COMPRESSED));
    }

    private static void puff(World world, PlacedStructure structure) {
        if (world == null || structure == null) {
            return;
        }
        Location top = new Location(world, structure.x() + 0.5, structure.maxY() + 0.8, structure.z() + 0.5);
        de.aetherion.core.api.Vfx.puff(top, Particle.BLOCK, 30, Math.max(0.6, 0.6), Material.COBBLESTONE.createBlockData());
        de.aetherion.core.api.Vfx.puff(top, Particle.CLOUD, 10, Math.max(0.5, 0.5), null);
        world.playSound(top, Sound.BLOCK_GRAVEL_BREAK, 1f, 0.7f);
        world.playSound(top, Sound.BLOCK_BARREL_OPEN, 0.8f, 0.9f);
    }

    /** The first Compressed Cobble in the hut: the tour's last beat. */
    private void factoryFinale(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        player.sendTitle("§b✦ Compressed Cobble ✦", "§7quarry §8→ §7mill §8→ §7hut", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            }
        }, 10L);
        PlacedStructure hut = firstHut(host);
        World world = hosts.world(host);
        if (hut != null && world != null) {
            Location top = new Location(world, hut.x() + 0.5, hut.maxY() + 1.0, hut.z() + 0.5);
            de.aetherion.core.api.Vfx.puff(top, Particle.TOTEM_OF_UNDYING, 50, Math.max(0.8, 0.8), null);
            de.aetherion.core.api.Vfx.puff(top, Particle.END_ROD, 20, Math.max(0.6, 0.6), null);
        }
        boolean compressed = compressedInStorage(host) > 0L;
        speak(player, hand, List.of(
                compressed ? "There it is. One Compressed Cobble: 128 stones in a single block."
                        : "There. The whole line runs, quarry to hut.",
                "That's the whole idea. Quarries dig, belts carry, machines refine, and the Hub unlocks the next step.",
                "Next up: the Forge, then a second quarry. The bar up top always shows what's next."),

                List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                        reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                        reply("Thanks, " + name(), NamedTextColor.GRAY, () -> speak(player, hand,
                                List.of("Any time. I'll be around the yard."), List.of()))));
        walkHome(hand);
    }

    private void giveKit(State state, StructureType type) {
        int bit = type == StructureType.MILL ? 1 : 2;
        if ((state.kits & bit) != 0) {
            return;
        }
        state.kits |= bit;
        state.freeTiles = Math.max(state.freeTiles, Math.max(0, plugin.getConfig().getInt("guide.factory-belt-tiles", 24)));
        dirty = true;
    }

    /** 0 locked, 1 unlocked (build it), 2 built but nothing feeds it, 3 fed but its chute goes nowhere, 4 in line. */
    private int stage(IslandHost host, StructureType type) {
        int sub = sub(host, type);
        if (sub < 2) {
            return sub;
        }
        PlacedStructure machine = lineMachine(host, type);
        if (machine == null) {
            return 2;
        }
        if (logistics.feeding(machine) == 0) {
            return 2;
        }
        LogisticsService.Route out = logistics.routeFrom(machine);
        return out == null || out.to() == null ? 3 : 4;
    }

    /** The machine of this type the tour is about: one that's fed if there is one, else the first. */
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

    /** The belt still missing for this machine: {from, to}, or null when it's in the line. */
    private PlacedStructure[] missingLeg(IslandHost host, StructureType type) {
        PlacedStructure machine = lineMachine(host, type);
        if (machine == null) {
            return null;
        }
        if (logistics.feeding(machine) == 0) {
            PlacedStructure from = type == StructureType.FORGE ? lineMachine(host, StructureType.MILL) : null;
            if (from == null) {
                from = firstHousing(host);
            }
            return from == null ? null : new PlacedStructure[]{from, machine};
        }
        LogisticsService.Route out = logistics.routeFrom(machine);
        if (out == null || out.to() == null) {
            PlacedStructure hut = firstHut(host);
            return hut == null ? null : new PlacedStructure[]{machine, hut};
        }
        return null;
    }

    /** What feeds this machine-to-be still runs straight past it (quarry → hut, Mill → hut): that belt can go. */
    private PlacedStructure pullable(IslandHost host, StructureType type) {
        PlacedStructure machine = lineMachine(host, type);
        if (machine == null || logistics.feeding(machine) > 0) {
            return null;
        }
        PlacedStructure from = type == StructureType.FORGE ? lineMachine(host, StructureType.MILL) : firstHousing(host);
        LogisticsService.Route out = from == null ? null : logistics.routeFrom(from);
        if (out == null || out.to() == null || out.to() == machine || out.length() == 0) {
            return null;
        }
        return from;
    }

    /** Orange on the chute the missing belt starts at, gold round what it runs into. */
    private void outlineLeg(Player player, IslandHost host, StructureType type, int pulses) {
        PlacedStructure[] leg = missingLeg(host, type);
        if (leg == null) {
            return;
        }
        int[] port = structures.outputPort(leg[0]);
        if (port != null) {
            square(player, port[0], port[1] + 0.1, port[2], 0, ORANGE, pulses);
            player.spawnParticle(Particle.DUST, port[0] + 0.5, port[1] + 0.6, port[2] + 0.5, 6, 0.1, 0.25, 0.1, 0, ORANGE);
        } else {
            outlineStructure(player, leg[0], ORANGE, pulses);
        }
        outlineStructure(player, leg[1], GOLD, pulses);
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
        boolean factoryLeft = goals != null && (!goals.done(host, "hub") || !goals.done(host, "mill_line"));
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
            speak(player, hand, List.of(goal.title() + " — " + goal.how()),
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
        player.sendMessage("§a+ §6Cobble Quarry §8(from " + name() + ") §7· in your hand: right-click flat ground where you want it");
    }

    /** Kits bit: the one spare quarry Hollis hands out if the first went missing. */
    private static final int SPARE_QUARRY = 1 << 4;

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

    /**
     * Walk to the next spot first ("This way!"), then talk once the owner is close. Already there (or no spot):
     * talk now. The owner can always just click him; that talks right away.
     */
    private void lead(Player player, Hand hand, Location spot, Runnable then) {
        hand.waiting = null;
        if (spot == null || hand.at == null || hand.at.distanceSquared(spot) < 2.5 * 2.5) {
            then.run();
            return;
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null && talk.bubbles(player)) {
            registerSpeaker(talk, hand);
            talk.end(player);
            talk.bark(hand.speakerId, pick("This way!", "Follow me, over here.", "Come on, I'll show you."),
                    List.of(player), 60);
        } else {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name() + " §8» §fThis way!"));
        }
        walk(hand, spot, null);
        hand.waiting = then;
        hand.waitingUntil = System.currentTimeMillis() + 90_000L;
    }

    private void registerSpeaker(TalkAccess talk, Hand hand) {
        talk.registerSpeaker(new TalkAccess.Speaker(hand.speakerId, name(), "§e", "isle_hand", () -> hand.at));
    }

    /** Next paced line (or the chips). Walks Hollis over first when the player stands too far for the bubble. */
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
        if (player.getLocation().distanceSquared(hand.at) > reach * reach && hand.waiting == null) {
            if (!chat.waitingForWalk) {
                chat.waitingForWalk = true;
                Location target = besidePlayer(hand, player);
                walk(hand, target, () -> {
                    chat.waitingForWalk = false;
                    chat.nextAt = clock;
                });
                if (hand.walkTo == null) {
                    // can't walk there (void): say it in chat instead
                    chats.remove(player.getUniqueId());
                    chatFallback(player, new ArrayList<>(chat.lines), chat.replies);
                }
            }
            chat.nextAt = clock + 10;
            return;
        }
        String line = chat.lines.poll();
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
        if (state.step.touring() && state.step != Step.HUB && structures.hub(host) == null) {
            state.step = Step.HUB; // an old save from the hut-first tour, or the Hub came down: Hub first again
        }
        if (state.step == Step.HUB) {
            PlacedStructure hub = structures.hub(host);
            if (hub != null && !hub.building()) {
                state.step = Step.HUT;
            }
        }
        if (state.step == Step.HUT) {
            PlacedStructure hut = firstHut(host);
            if (hut != null && !hut.building()) {
                state.step = Step.QUARRY;
            }
        }
        if (state.step == Step.QUARRY && !hosts.minions(host).isEmpty()) {
            state.step = Step.BELT;
        }
        if (state.step == Step.BELT && tourAlreadyDone(host)) {
            state.step = Step.MILL;
        }
        if ((state.step == Step.MILL && inLine(host, StructureType.MILL)) || state.step == Step.FORGE) {
            state.step = Step.COMPACT;
        }
        if (state.step == Step.COMPACT && (!AetherionItemsAccess.available() || compressedInStorage(host) > 0L)) {
            state.step = Step.DONE;
        }
        if (state.step != before) {
            state.lastSub = -1;
            dirty = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------------------------------------

    /** Every 2 ticks: walking and paced lines. Every second: presence, tour progress, markers. */
    public void tick() {
        clock += 2;
        for (Hand hand : hands.values()) {
            stepWalk(hand);
        }
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
            for (Player player : entry.getValue()) {
                if (player.getUniqueId().equals(island.ownerId())) {
                    owner = player;
                }
                if (talk != null && !talk.talkingWith(player, hand.speakerId) && !chats.containsKey(player.getUniqueId())) {
                    showHolo(player, hand);
                }
            }
            if (owner != null) {
                ownerSecond(owner, hand, island);
            }
            updateHolo(hand);
        }
        // nobody around for a minute: Hollis goes off shift (no NPC, no packets)
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
            // an island claimed before Hollis existed: he says hello the first time the owner is home
            state.introduced = true;
            dirty = true;
            greet(owner, hand);
            return;
        }
        if (hand.waiting != null && hand.walkTo == null) {
            TalkAccess talk = AetherServices.talk();
            double reach = talk == null ? 8.0 : Math.max(4.0, talk.reach() - 2.0);
            if (owner.getLocation().distanceSquared(hand.at) <= reach * reach) {
                Runnable then = hand.waiting;
                hand.waiting = null;
                then.run();
                return;
            }
            if (System.currentTimeMillis() > hand.waitingUntil) {
                hand.waiting = null; // the bar + markers keep the step; clicking him picks it up again
            }
        }
        if (!state.step.touring() || chats.containsKey(owner.getUniqueId())) {
            return;
        }
        Step before = state.step;
        if (advance(host, state)) {
            hand.waiting = null;
            TalkAccess talk = AetherServices.talk();
            if (talk != null) {
                talk.end(owner);
            }
            owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
            switch (state.step) {
                case HUB -> hubPrompt(owner, hand, false);
                case HUT -> {
                    if (before == Step.HUB) {
                        // the Hub ritual plays first (bells, beam, title), then he carries on
                        Step want = state.step;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (owner.isOnline() && state(island.ownerId()).step == want
                                    && !chats.containsKey(owner.getUniqueId())) {
                                prompt(owner, hand, true);
                            }
                        }, 110L);
                    } else {
                        prompt(owner, hand, true);
                    }
                }
                case QUARRY -> {
                    // the quarry goes into the hand right away (never waits on walking or a bubble)
                    if (!state.quarryGiven) {
                        state.quarryGiven = true;
                        dirty = true;
                        giveQuarry(owner);
                    }
                    speak(owner, hand, List.of("Hut's up. Whatever the belts bring ends up in there."), List.of());
                    quarryPromptSoon(owner, hand, island.ownerId(), 90L, 6);
                }
                case BELT -> beltPrompt(owner, hand, before == Step.QUARRY);
                case MILL, FORGE, COMPACT -> {
                    if (!before.actTwo()) {
                        finishTour(owner, hand);
                    } else {
                        prompt(owner, hand, true);
                    }
                }
                case DONE -> {
                    if (before.actTwo()) {
                        factoryFinale(owner, hand);
                    } else {
                        finishTour(owner, hand);
                    }
                }
                default -> {
                }
            }
            return;
        }
        // inside the Mill step: unlocked, built, belted in. Each bit gets its own next line
        if (state.step == Step.MILL) {
            int stage = stage(host, StructureType.MILL);
            if (state.lastSub >= 0 && stage != state.lastSub && hand.waiting == null) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                // a fresh stage (unlocked, placed) gets the full explanation, not the short reminder
                machinePrompt(owner, hand, StructureType.MILL, stage > state.lastSub);
                return;
            }
            if (state.lastSub < 0) {
                state.lastSub = stage;
            }
        }
        // the owner is home mid-tour and he hasn't talked to them since he showed up: he walks over (no markers)
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

    /** Only when asked ("Where's the chute?"): a short glow on the quarry chute and the hut. */
    private void outlineChute(Player player, IslandHost host, int pulses) {
        PlacedStructure housing = firstHousing(host);
        int[] port = housing == null ? null : structures.outputPort(housing);
        if (port != null) {
            square(player, port[0], port[1] + 0.1, port[2], 0, ORANGE, pulses);
            player.spawnParticle(Particle.DUST, port[0] + 0.5, port[1] + 0.6, port[2] + 0.5, 6, 0.1, 0.25, 0.1, 0, ORANGE);
        }
        PlacedStructure hut = firstHut(host);
        if (hut != null) {
            int r = Math.max(hut.maxX() - hut.x(), hut.maxZ() - hut.z());
            square(player, hut.x(), hut.y() + 1.1, hut.z(), r, GOLD, pulses);
        }
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
    // walking
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

    private Location besidePlayer(Hand hand, Player player) {
        IslandHost host = IslandHost.personal(hand.owner);
        Location p = player.getLocation();
        double dx = hand.at.getX() - p.getX();
        double dz = hand.at.getZ() - p.getZ();
        double len = Math.max(0.001, Math.hypot(dx, dz));
        int tx = (int) Math.floor(p.getX() + dx / len * 2.5);
        int tz = (int) Math.floor(p.getZ() + dz / len * 2.5);
        return spotNear(host, tx, tz, p.getBlockX(), p.getBlockZ());
    }

    private void walk(Hand hand, Location target, Runnable onArrive) {
        if (target == null || hand.at == null || target.getWorld() == null || !target.getWorld().equals(hand.at.getWorld())) {
            hand.walkTo = null;
            if (onArrive != null && target != null) {
                onArrive.run();
            }
            return;
        }
        hand.walkTo = target.clone();
        hand.onArrive = onArrive;
    }

    private void walkHome(Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        Location home = homeSpot(host);
        if (home != null) {
            hand.home = home;
            walk(hand, home, null);
        } else if (hand.home != null) {
            walk(hand, hand.home, null);
        }
    }

    private void stepWalk(Hand hand) {
        Location to = hand.walkTo;
        if (to == null || hand.at == null || hand.npc == null) {
            return;
        }
        double dx = to.getX() - hand.at.getX();
        double dz = to.getZ() - hand.at.getZ();
        double dist = Math.hypot(dx, dz);
        Location next;
        if (dist <= WALK_STEP) {
            next = to.clone();
        } else {
            double nx = hand.at.getX() + dx / dist * WALK_STEP;
            double nz = hand.at.getZ() + dz / dist * WALK_STEP;
            World world = hand.at.getWorld();
            int y = ground(world, (int) Math.floor(nx), (int) Math.floor(nz));
            double ny = y == Integer.MIN_VALUE ? hand.at.getY() : y + 1;
            if (Math.abs(ny - hand.at.getY()) > 1.2) {
                // a ledge or a gap: hop straight to the goal instead of walking off the island
                next = to.clone();
            } else {
                next = new Location(world, nx, ny, nz);
                next.setYaw(yawTo(hand.at.getX(), hand.at.getZ(), to.getX(), to.getZ()));
            }
        }
        move(hand, next);
        if (next.distanceSquared(to) < 0.01) {
            hand.walkTo = null;
            Runnable arrive = hand.onArrive;
            hand.onArrive = null;
            if (arrive != null) {
                arrive.run();
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
            hand.home = homeSpot(host);
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
     * After the tour: seat by the campfire if the starter has one; otherwise a few blocks from spawn toward the
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
        String status = switch (step) {
            case WELCOME -> "§6✦ §eRight-click me";
            case HUT, QUARRY, BELT, HUB, MILL, FORGE, COMPACT -> "§6✦ §eTour · step " + (progress(hand.owner) + 1)
                    + "/" + total();
            default -> "§7Right-click for tips";
        };
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
