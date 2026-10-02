package de.aetherion.guilds.island;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.guilds.logistics.LogisticsService;
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
 * Hollis, the isle hand: a FancyNPC on every personal starter island who walks the owner through the first
 * production chain, in the TalkUx speech bubble (typewriter over his head, reply chips beside him):
 * <ol>
 *   <li><b>Storage Hut</b> on the marked pad (the first one is free),</li>
 *   <li>a <b>Cobble Quarry</b>, on the house, set down a few steps away (he marks a good spot),</li>
 *   <li>a short <b>belt</b> from its chute into the hut (starter kit: no Workshop needed, first tiles free).</li>
 * </ol>
 * He walks to each spot with the player. Every step is detected from the island itself, so the tour never
 * blocks building: do things in any order, walk away, skip it, pick it up again from {@code /island}. After
 * the tour he stays in the yard with tips.
 *
 * <p>Without the bubble (TalkUx off, player on classic chat talk) the same lines and choices go to chat.
 * Without FancyNpcs there's no Hollis: the classic "first steps" card is used instead.
 */
public final class IslandGuide implements Listener {

    public enum Step {
        WELCOME,
        HUT,
        QUARRY,
        BELT,
        DONE,
        SKIPPED;

        public boolean touring() {
            return this == HUT || this == QUARRY || this == BELT;
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
        boolean quarryGiven;
        int freeTiles;
        int[] quarrySpot;
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
        });
    }

    private FactoryGoals goals;

    public void attachGoals(FactoryGoals goals) {
        this.goals = goals;
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
        return plugin.getConfig().getString("guide.name", "Hollis");
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
            case WELCOME -> "Talk to " + name() + " in the yard";
            case HUT -> "Build your Storage Hut on the marked pad §7(free)";
            case QUARRY -> state != null && state.quarryGiven
                    ? "Set down the Cobble Quarry near the hut"
                    : "Talk to " + name() + " about a quarry";
            case BELT -> "Belt the quarry's chute into the hut";
            default -> null;
        };
    }

    /** 0..3 steps done (for progress bars). */
    public int progress(UUID owner) {
        return switch (step(owner)) {
            case QUARRY -> 1;
            case BELT -> 2;
            case DONE, SKIPPED -> 3;
            default -> 0;
        };
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
        state.introduced = true;
        dirty = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                greet(player, hand);
            }
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
        switch (state.step) {
            case WELCOME -> greet(player, hand);
            case HUT -> hutPrompt(player, hand, false);
            case QUARRY -> quarryPrompt(player, hand, false);
            case BELT -> beltPrompt(player, hand, false);
            default -> yardTalk(player, hand);
        }
    }

    private void greet(Player player, Hand hand) {
        State state = state(hand.owner);
        state.introduced = true;
        dirty = true;
        IslandHost host = IslandHost.personal(hand.owner);
        if (tourAlreadyDone(host)) {
            state.step = Step.DONE;
            speak(player, hand, List.of(
                    "Oi! I'm " + name() + ", I keep isles like this one ticking.",
                    "Hut, quarry, belt… you've already got the loop running. Nice work."),
                    List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                            reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                            reply("Carry on", NamedTextColor.GRAY, null)));
            walkHome(hand);
            return;
        }
        speak(player, hand, List.of(
                "Oi, you made it! I'm " + name() + ". I keep isles like this one ticking.",
                "Three pieces and this rock starts paying you: a hut, a quarry, a belt between them."),
                List.of(reply("Show me", NamedTextColor.GREEN, () -> startTour(player, hand)),
                        reply("I know the drill", NamedTextColor.GRAY, () -> skip(player, hand)),
                        reply("Later", NamedTextColor.DARK_GRAY, () -> speak(player, hand, List.of(
                                "Right. I'll be in the yard. Give me a shout when you're ready."), List.of()))));
    }

    private void startTour(Player player, Hand hand) {
        State state = state(hand.owner);
        IslandHost host = IslandHost.personal(hand.owner);
        state.step = Step.HUT;
        dirty = true;
        advance(host, state);
        switch (state.step) {
            case HUT -> hutPrompt(player, hand, true);
            case QUARRY -> quarryPrompt(player, hand, true);
            case BELT -> beltPrompt(player, hand, true);
            default -> finishTour(player, hand);
        }
    }

    private void skip(Player player, Hand hand) {
        State state = state(hand.owner);
        state.step = Step.SKIPPED;
        dirty = true;
        speak(player, hand, List.of("Fair enough. Hut, quarry, belt: you've got this. Holler if you need me."),
                List.of());
        walkHome(hand);
    }

    private void hutPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        int[] pad = hutPad(host);
        Location spot = pad == null ? null : spotNear(host, pad[0], pad[2] + 4, pad[0], pad[2]);
        lead(player, hand, spot, () -> hutLines(player, hand, fresh));
    }

    private void hutLines(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        speak(player, hand, List.of(
                fresh ? "First, somewhere to keep it all. Your first Storage Hut is free." : "Still needs a Storage Hut. The first one's free.",
                "See the gold outline? Island menu, Build, Storage Hut. Aim the blueprint at the pad and click."),
                List.of(reply("Open Build", NamedTextColor.GREEN, () -> openBuild.accept(player, host)),
                        reply("Show me the pad", NamedTextColor.GOLD, () -> {
                            outlinePad(player, host, 6);
                            speak(player, hand, List.of("Right there, gold outline. Front faces you when you place it."), List.of());
                        }),
                        reply("Skip the tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
        outlinePad(player, host, 4);
    }

    private void quarryPrompt(Player player, Hand hand, boolean fresh) {
        IslandHost host = IslandHost.personal(hand.owner);
        State state = state(hand.owner);
        List<String> lines = new ArrayList<>();
        boolean gift = !state.quarryGiven;
        if (gift) {
            lines.add(fresh ? "Now something to fill it. Here: a Cobble Quarry, on the house." : "Here, before I forget: a Cobble Quarry, on the house.");
        } else {
            lines.add("Got the Cobble Quarry I gave you? It wants to be near the hut.");
        }
        int[] spot = quarrySpot(host, state);
        if (spot != null) {
            lines.add("Set it down a few steps from the hut. I marked a good spot in gold. Click the ground there.");
        } else {
            lines.add("Set it down a few steps from the hut, on flat ground. Its chute will face you.");
        }
        Location stand = spot == null ? null : spotNear(host, spot[0] + 2, spot[2] + 2, spot[0], spot[2]);
        lead(player, hand, stand, () -> {
            if (gift && !state.quarryGiven) {
                state.quarryGiven = true;
                dirty = true;
                giveQuarry(player);
            }
            speak(player, hand, lines, List.of(
                reply("Got it", NamedTextColor.GREEN, null),
                reply("Mark the spot again", NamedTextColor.GOLD, () -> {
                    outlineQuarrySpot(player, host, state, 6);
                    speak(player, hand, List.of("Gold ring, right there. Quarry block goes in the middle."), List.of());
                }),
                reply("Skip the tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand))));
        });
        outlineQuarrySpot(player, host, state, 4);
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
        String free = state.freeTiles > 0 ? " First " + state.freeTiles + " tiles are on me." : "";
        lead(player, hand, stand, () -> speak(player, hand, List.of(
                (fresh ? "Hear that? Already digging. " : "") + "Last piece: a belt from its chute into the hut." + free,
                "Click the orange chute mark, then the hut wall. Straight or one bend."),
                List.of(reply("Hand me the Belt Layer", NamedTextColor.GREEN, () -> placement.startBelts(player, host)),
                        reply("Show me again", NamedTextColor.GOLD, () -> {
                            outlineChute(player, host, 6);
                            speak(player, hand, List.of("Orange mark: start there. End against the hut, any wall."), List.of());
                        }),
                        reply("Skip the tour", NamedTextColor.DARK_GRAY, () -> skip(player, hand)))));
        outlineChute(player, host, 4);
    }

    private void finishTour(Player player, Hand hand) {
        State state = state(hand.owner);
        state.step = Step.DONE;
        dirty = true;
        player.sendTitle("§6✦ First Chain Running ✦", "§7quarry §8→ §7belt §8→ §7hut", 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            }
        }, 8L);
        if (hand.at != null && hand.at.getWorld() != null) {
            hand.at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, hand.at.clone().add(0, 1.2, 0), 40, 0.5, 0.8, 0.5, 0.3);
        }
        speak(player, hand, List.of(
                "There it is. Quarry, belt, hut. That's the whole trick.",
                "Next: build a Workshop. That's your Hub, where the whole factory gets run from."),
                List.of(reply("Open /island", NamedTextColor.GREEN, () -> openHub.accept(player)),
                        reply("Thanks, " + name(), NamedTextColor.GRAY, () -> speak(player, hand,
                                List.of("Any time. I'll be around the yard."), List.of()))));
        walkHome(hand);
    }

    private void yardTalk(Player player, Hand hand) {
        speak(player, hand, List.of(pick(
                        "Need a hand?",
                        "Belts humming, hut filling. What can I do for you?",
                        "Good day for hauling stone. What's up?")),
                List.of(reply("What's next?", NamedTextColor.GREEN, () -> tip(player, hand)),
                        reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                        reply("Run the tour again", NamedTextColor.GRAY, () -> startTour(player, hand)),
                        reply("Bye", NamedTextColor.DARK_GRAY, () -> speak(player, hand, List.of("Mind the edge."), List.of()))));
    }

    private void tip(Player player, Hand hand) {
        IslandHost host = IslandHost.personal(hand.owner);
        FactoryGoals.Goal goal = goals == null ? null : goals.next(host);
        if (goal != null) {
            speak(player, hand, List.of(goal.title() + ". " + goal.how()),
                    List.of(reply("Build menu", NamedTextColor.GOLD, () -> openBuild.accept(player, host)),
                            reply("Thanks", NamedTextColor.GRAY, null)));
            return;
        }
        String line;
        PlacedStructure hut = firstHut(host);
        if (!structures.hasWorkshop(host)) {
            line = "Build a Workshop next: that's your Hub. Belts, mills and everything after run from its lectern.";
        } else if (structures.count(host, StructureType.MILL) == 0) {
            line = "Put a Mill on a belt: raw in, Compressed out. Compressed is what upgrades eat.";
        } else if (hut != null && hut.level() < 2) {
            line = "Your hut can grow a loft: four times the room. Production, click the hut, Upgrade.";
        } else if (hosts.parcels(host).size() <= 9) {
            line = "Running out of yard? Buy land touching yours; new ground rises right there.";
        } else if (hosts.tier(host) < 2) {
            line = "Raise the Island Tier: more belts, one more of every machine.";
        } else {
            line = "Forge after the Mill: Compacted Cobble. The Warehouse and the Bellows want it.";
        }
        speak(player, hand, List.of(line), List.of(reply("Island menu", NamedTextColor.GOLD, () -> openHub.accept(player)),
                reply("Thanks", NamedTextColor.GRAY, null)));
    }

    private void giveQuarry(Player player) {
        ItemStack quarry = minions.createQuarryItem(QuarryType.COBBLESTONE);
        player.getInventory().addItem(quarry).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        player.sendMessage("§a+ §6Cobble Quarry §8(from " + name() + ")");
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
            state.step = Step.DONE;
        }
        if (state.step != before) {
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
                case QUARRY -> {
                    speak(owner, hand, List.of("Look at that. Walls, roof, crates. Whatever the belts bring ends up in there."), List.of());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        State now = state(island.ownerId());
                        if (owner.isOnline() && now.step == Step.QUARRY && !now.quarryGiven
                                && !chats.containsKey(owner.getUniqueId())) {
                            quarryPrompt(owner, hand, true);
                        }
                    }, 90L);
                }
                case BELT -> beltPrompt(owner, hand, before == Step.QUARRY);
                case DONE -> finishTour(owner, hand);
                default -> {
                }
            }
            return;
        }
        // gentle markers while the step is open (every 3 s)
        if (clock % 60 == 0) {
            switch (state.step) {
                case HUT -> outlinePad(owner, host, 1);
                case QUARRY -> {
                    if (state.quarryGiven) {
                        outlineQuarrySpot(owner, host, state, 1);
                    }
                }
                case BELT -> outlineChute(owner, host, 1);
                default -> {
                }
            }
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

    private void outlinePad(Player player, IslandHost host, int pulses) {
        int[] pad = hutPad(host);
        if (pad != null) {
            square(player, pad[0], pad[1] + 1.1, pad[2], 2, GOLD, pulses);
        }
    }

    private void outlineQuarrySpot(Player player, IslandHost host, State state, int pulses) {
        int[] spot = quarrySpot(host, state);
        if (spot != null) {
            square(player, spot[0], spot[1] + 1.1, spot[2], 1, GOLD, pulses);
            player.spawnParticle(Particle.END_ROD, spot[0] + 0.5, spot[1] + 1.4, spot[2] + 0.5, 3, 0.05, 0.2, 0.05, 0.01);
        }
    }

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
        for (int pulse = 0; pulse < pulses; pulse++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                double x0 = cx - r;
                double x1 = cx + r + 1;
                double z0 = cz - r;
                double z1 = cz + r + 1;
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

    /**
     * A good quarry spot: 5..7 blocks from the hut, flat solid ground, 3x3 clear for its housing, on your land,
     * nothing else there. Remembered once found.
     */
    private int[] quarrySpot(IslandHost host, State state) {
        World world = hosts.world(host);
        PlacedStructure hut = firstHut(host);
        if (world == null || hut == null) {
            return null;
        }
        if (state.quarrySpot != null && spotFree(host, world, state.quarrySpot[0], state.quarrySpot[1], state.quarrySpot[2])) {
            return state.quarrySpot;
        }
        Location spawn = hosts.spawn(host);
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int d = 5; d <= 7; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    int x = hut.x() + dx;
                    int z = hut.z() + dz;
                    int y = ground(world, x, z);
                    if (y == Integer.MIN_VALUE || !spotFree(host, world, x, y, z)) {
                        continue;
                    }
                    double score = d * 4.0 + (spawn == null ? 0 : Math.hypot(spawn.getX() - x, spawn.getZ() - z));
                    if (score < bestScore) {
                        bestScore = score;
                        best = new int[]{x, y, z};
                    }
                }
            }
        }
        state.quarrySpot = best;
        return best;
    }

    private boolean spotFree(IslandHost host, World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int bx = x + dx;
                int bz = z + dz;
                if (!hosts.inBuildZone(host, bx, bz) || structures.atColumn(world, bx, bz) != null
                        || logistics.isBelt(world, bx, y + 1, bz)) {
                    return false;
                }
                if (!world.getBlockAt(bx, y, bz).getType().isSolid()) {
                    return false;
                }
                for (int up = 1; up <= 4; up++) {
                    if (!PasteService.replaceable(world.getBlockAt(bx, y + up, bz))) {
                        return false;
                    }
                }
            }
        }
        return true;
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
            case HUT, QUARRY, BELT -> "§6✦ §eTour · step " + (progress(hand.owner) + 1) + "/3";
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
