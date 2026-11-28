package de.aetherion.tutorial.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.tutorial.TutorialPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The people of Morningbell. Each is a FancyNpcs body at a named spot of the isle (never saved: they are put up on
 * enable and taken down on disable) and a guest speaker of the talk bubbles.
 *
 * <p>Seven of them teach (the lessons run through them). The rest live here: a baker, a shepherd, a lighthouse
 * keeper. They have no lesson, only a line or two about what waits on the mainland, said when somebody asks.
 */
final class Cast {

    static final String TOLLY = "tolly";
    static final String ODA = "oda";
    static final String BURR = "burr";
    static final String GRETT = "grett";
    static final String ANSEL = "ansel";
    static final String HILDE = "hilde";
    static final String WIM = "wim";

    /**
     * @param color legacy colour of the name in chat and bubble
     * @param mini  the same colour for the name over the head (MiniMessage)
     * @param voice one of the Quests voice blips
     * @param mark  the spot in isle.json
     * @param hand  what they hold (null = nothing)
     * @param skin  which painted skin they wear ({@code skins/<skin>.png})
     * @param talk  what a villager without a lesson says, {EN, DE} pairs (null for the teachers)
     */
    record Member(String id, String name, String color, String mini, String voice, String mark, Material hand,
                  String skin, String[][] talk) {

        String fancyName() {
            return "mb_" + id;
        }

        String speaker() {
            return "morningbell_" + id;
        }

        boolean teacher() {
            return talk == null;
        }
    }

    static final Map<String, Member> ALL = new LinkedHashMap<>();

    static {
        teacher(TOLLY, "Tolly", "§b", "<aqua>", "isle_hand", "tolly", Material.LANTERN, "tolly");
        teacher(ODA, "Oda", "§e", "<yellow>", "town_crier", "oda", Material.BOOK, "oda");
        teacher(BURR, "Burr", "§6", "<gold>", "craftsman", "burr", Material.IRON_AXE, "burr");
        teacher(GRETT, "Grett", "§7", "<gray>", "foreman", "grett", Material.IRON_PICKAXE, "grett");
        teacher(ANSEL, "Ansel", "§c", "<red>", "booster_tutor", "ansel", Material.MACE, "ansel");
        teacher(HILDE, "Hilde", "§a", "<green>", "vex", "hilde", Material.IRON_SWORD, "hilde");
        teacher(WIM, "Captain Wim", "§3", "<dark_aqua>", "fisher", "captain", Material.SPYGLASS, "wim");

        // The islanders. One thought each, and it is always about the other shore: what the newcomer will find there.
        villager("marla", "Marla", "farm_isle_guide", "baker", Material.BREAD, "marla", new String[][] {
                {"Morning! Smell that? Wheat from Downs Farm, ground at Tam's mill.",
                        "Morgen! Riechst du das? Weizen von der Downs Farm, gemahlen in Tams Mühle."},
                {"On the mainland you grow your own. Captain Wim has a hoe for you.",
                        "Auf dem Festland baust du selbst an. Kapitän Wim hat eine Hacke für dich."}});
        villager("osk", "Osk", "merchant", "innkeeper", null, "osk", new String[][] {
                {"Sailing today? Then no bed for you, friend. Nobody sleeps in on Morningbell.",
                        "Du fährst heute? Dann kein Bett für dich. Auf Morningbell schläft niemand aus."},
                {"The Capital over there has a market. Bring coins, they talk louder than I do.",
                        "Die Hauptstadt drüben hat einen Markt. Bring Coins mit, die reden lauter als ich."}});
        villager("pim", "Pim", "fishmonger", "market_woman", Material.APPLE, "pim", new String[][] {
                {"Nothing for sale before the ferry leaves, love. Island rule.",
                        "Vor der Fähre wird nichts verkauft, Schätzchen. Inselregel."},
                {"Keep your coins. The traders on the mainland will want every one of them.",
                        "Behalt deine Coins. Die Händler auf dem Festland wollen jeden einzelnen."}});
        villager("nell", "Nell", "fishmonger", "fishwife", Material.COD, "nell", new String[][] {
                {"Herring! Fresh from the weir on the Flat. If Old Clanger left any.",
                        "Hering! Frisch aus dem Wehr im Watt. Falls Old Clanger welche übrig gelassen hat."},
                {"Fishing is a craft of its own over there. A whole isle for it, they say.",
                        "Fischen ist drüben ein eigenes Handwerk. Eine ganze Insel dafür, sagt man."}});
        villager("hob", "Hob", "farmer", "farmer", Material.WHEAT, "hob", new String[][] {
                {"Mind the furrows! These fields feed the whole island.",
                        "Pass auf die Furchen auf! Diese Felder ernähren die ganze Insel."},
                {"Farming has its own isle on the mainland. Bigger fields than mine, I hear.",
                        "Auf dem Festland hat der Ackerbau eine eigene Insel. Größere Felder als meine, hört man."}});
        villager("wren", "Wren", "lark", "shepherd", Material.SHEARS, "wren", new String[][] {
                {"They are not pets. They are sheep. There is a difference, they tell me.",
                        "Das sind keine Haustiere. Das sind Schafe. Da gibt es einen Unterschied, sagen sie."},
                {"Real pets you catch on the mainland. Ask for Lark, she has a way with them.",
                        "Echte Pets fängst du auf dem Festland. Frag nach Lark, die kann mit ihnen."}});
        villager("tam", "Tam", "lumberjack", "miller", null, "tam", new String[][] {
                {"Good wind today. The sails have not stopped since the bell.",
                        "Guter Wind heute. Die Flügel stehen seit der Glocke nicht still."},
                {"Whatever you do over there trains a skill. Do it often and it gets easy.",
                        "Was immer du drüben tust, trainiert einen Skill. Mach es oft, dann wird es leicht."}});
        villager("brack", "Brack", "rite_keeper", "keeper", Material.LANTERN, "brack", new String[][] {
                {"Forty years I have lit this lamp. Every ferry found its way home.",
                        "Vierzig Jahre zünde ich diese Lampe an. Jede Fähre hat heimgefunden."},
                {"Everyone who sailed from here came back with stories. Bring me yours.",
                        "Jeder, der von hier gesegelt ist, kam mit Geschichten zurück. Bring mir deine."}});
    }

    private static void teacher(String id, String name, String color, String mini, String voice, String mark,
                                Material hand, String skin) {
        ALL.put(id, new Member(id, name, color, mini, voice, mark, hand, skin, null));
    }

    private static void villager(String id, String name, String voice, String mark, Material hand, String skin,
                                 String[][] talk) {
        ALL.put(id, new Member(id, name, "§f", "<white>", voice, mark, hand, skin, talk));
    }

    private final TutorialPlugin plugin;
    private final Isle isle;
    private final Map<String, Object> bodies = new HashMap<>();
    private final Map<String, Location> spots = new HashMap<>();
    private final Map<String, String> byFancyName = new HashMap<>();
    private final List<String> speakers = new ArrayList<>();
    private boolean clicksHooked;

    Cast(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
        for (Member member : ALL.values()) {
            byFancyName.put(member.fancyName(), member.id());
        }
    }

    // ------------------------------------------------------------------ places

    /** Where this cast member stands (feet), or null when the island has no such spot. */
    Location spot(String id) {
        Location known = spots.get(id);
        if (known != null) {
            return known.clone();
        }
        Member member = ALL.get(id);
        if (member == null) {
            return null;
        }
        Location at = isle.marks().at(isle.world(), member.mark());
        if (at == null && TOLLY.equals(id)) {
            // an island built before he had a spot of his own: beside the Landing, turned to whoever wakes there
            Location landing = isle.marks().at(isle.world(), "arrival");
            if (landing != null) {
                at = landing.clone().add(3.0, 0.0, -2.0);
                at.setYaw(56f);
            }
        }
        if (at != null) {
            spots.put(id, at);
            return at.clone();
        }
        return null;
    }

    // ------------------------------------------------------------------ bodies

    void spawnAll() {
        if (!FancyNpcFacade.isAvailable()) {
            plugin.getLogger().warning("FancyNpcs is missing: the cast has no bodies (the lessons still run in chat).");
            registerSpeakers();
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::spawnAll, 40L);
                return;
            }
            for (Member member : ALL.values()) {
                spawn(manager, member);
            }
            hookClicks();
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not put up the cast: " + t);
        }
        registerSpeakers();
    }

    private void spawn(Object manager, Member member) {
        Location at = spot(member.id());
        if (at == null) {
            if (member.teacher()) {
                plugin.getLogger().warning("No spot '" + member.mark() + "' on the island for " + member.name());
            }
            return;
        }
        try {
            Object old = FancyNpcFacade.getNpc(manager, member.fancyName());
            if (old != null) {
                FancyNpcFacade.removeFromPlayersQuiet(old);
                try {
                    FancyNpcFacade.unregister(manager, old);
                } catch (ReflectiveOperationException ignored) {
                    // an older body that was never registered
                }
            }
            UUID creator = new UUID(0L, Math.abs((long) member.fancyName().hashCode()));
            Object data = FancyNpcFacade.createNpcData(member.fancyName(), creator, at.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, member.mini() + member.name());
            FancyNpcFacade.invokeQuiet(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.applyVisibility(data, 64);
            isle.skins().apply(data, member.skin());
            if (member.hand() != null) {
                try {
                    data.getClass().getMethod("addEquipment", FancyNpcFacade.equipmentSlotClass(), ItemStack.class)
                            .invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), new ItemStack(member.hand()));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // bare hands then
                }
            }
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.invokeQuiet(npc, "setSaveToFile", boolean.class, false);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            bodies.put(member.id(), npc);
        } catch (Throwable t) {
            plugin.getLogger().warning(member.name() + " could not be put up: " + t);
        }
    }

    /** A skin was signed after the bodies went up: dress everybody who wears it. */
    void redress(String skin) {
        for (Member member : ALL.values()) {
            Object npc = member.skin().equals(skin) ? bodies.get(member.id()) : null;
            if (npc == null) {
                continue;
            }
            try {
                Object data = FancyNpcFacade.data(npc);
                if (isle.skins().apply(data, skin)) {
                    FancyNpcFacade.removeForAll(npc);
                    FancyNpcFacade.create(npc);
                    FancyNpcFacade.spawnForAll(npc);
                }
            } catch (Throwable ignored) {
                // stays in the old skin until the next restart
            }
        }
    }

    void removeAll() {
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            for (String id : speakers) {
                talk.unregisterSpeaker(id);
            }
        }
        speakers.clear();
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            for (Object npc : bodies.values()) {
                FancyNpcFacade.removeFromPlayersQuiet(npc);
                try {
                    FancyNpcFacade.unregister(manager, npc);
                } catch (ReflectiveOperationException ignored) {
                    // already gone
                }
            }
        } catch (Throwable ignored) {
            // FancyNpcs went down first
        }
        bodies.clear();
    }

    // ------------------------------------------------------------------ voices

    private void registerSpeakers() {
        TalkAccess talk = AetherServices.talk();
        if (talk == null) {
            return;
        }
        for (Member member : ALL.values()) {
            String id = member.id();
            if (spot(id) == null) {
                continue;
            }
            talk.registerSpeaker(new TalkAccess.Speaker(member.speaker(), member.name(), member.color(), member.voice(),
                    () -> spot(id)));
            if (!speakers.contains(member.speaker())) {
                speakers.add(member.speaker());
            }
        }
    }

    // ------------------------------------------------------------------ clicks

    private void hookClicks() {
        if (clicksHooked) {
            return;
        }
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.player() == null || click.name() == null) {
                        return;
                    }
                    String id = byFancyName.get(click.name());
                    if (id == null) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Player player = click.player();
                    // FancyNpcs reads the click off the network thread on some builds
                    if (Bukkit.isPrimaryThread()) {
                        isle.director().onNpc(player, id);
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> isle.director().onNpc(player, id));
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Cast click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.NORMAL, executor, plugin, false);
            clicksHooked = true;
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs has no interact event: the cast cannot be clicked.");
        }
    }
}
