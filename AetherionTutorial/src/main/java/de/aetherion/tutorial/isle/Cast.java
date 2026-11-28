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
     */
    record Member(String id, String name, String color, String mini, String voice, String mark, Material hand,
                  String roleEn, String roleDe) {

        String fancyName() {
            return "mb_" + id;
        }

        String speaker() {
            return "morningbell_" + id;
        }
    }

    static final Map<String, Member> ALL = new LinkedHashMap<>();

    static {
        add(new Member(TOLLY, "Tolly", "§b", "<aqua>", "isle_hand", "tolly", Material.LANTERN,
                "Ferry hand", "Fährjunge"));
        add(new Member(ODA, "Oda", "§e", "<yellow>", "town_crier", "oda", Material.BOOK,
                "Bellkeeper", "Glöcknerin"));
        add(new Member(BURR, "Burr", "§6", "<gold>", "craftsman", "burr", Material.IRON_AXE,
                "Charcoal burner", "Köhler"));
        add(new Member(GRETT, "Grett", "§7", "<gray>", "foreman", "grett", Material.IRON_PICKAXE,
                "Quarry master", "Steinbrecherin"));
        add(new Member(ANSEL, "Ansel", "§c", "<red>", "booster_tutor", "ansel", Material.MACE,
                "Smith", "Schmied"));
        add(new Member(HILDE, "Hilde", "§a", "<green>", "vex", "hilde", Material.IRON_SWORD,
                "Watch sergeant", "Wachtmeisterin"));
        add(new Member(WIM, "Captain Wim", "§3", "<dark_aqua>", "fisher", "captain", Material.SPYGLASS,
                "Ferry captain", "Fährkapitän"));
    }

    private static void add(Member member) {
        ALL.put(member.id(), member);
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
            // older island builds have no spot for him: beside the Landing, looking at whoever arrives
            Location landing = isle.marks().at(isle.world(), "arrival");
            if (landing != null) {
                at = landing.clone().add(2.0, 0.0, 2.5);
                at.setYaw(110f);
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
            plugin.getLogger().warning("No spot '" + member.mark() + "' on the island for " + member.name());
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
            isle.skins().apply(data, member.id());
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

    /** A skin was signed after the body went up: dress it now. */
    void redress(String id) {
        Object npc = bodies.get(id);
        if (npc == null) {
            return;
        }
        try {
            Object data = FancyNpcFacade.data(npc);
            if (isle.skins().apply(data, id)) {
                FancyNpcFacade.removeForAll(npc);
                FancyNpcFacade.create(npc);
                FancyNpcFacade.spawnForAll(npc);
            }
        } catch (Throwable ignored) {
            // stays in the old skin until the next restart
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
