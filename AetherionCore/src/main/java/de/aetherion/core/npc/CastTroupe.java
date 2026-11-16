package de.aetherion.core.npc;

import com.destroystokyo.paper.event.entity.EntityZapEvent;

import de.aetherion.core.AetherionCore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * The bodies of one cast: a group of NPCs a plugin owns (the mine crew, the lake crew, the market traders).
 * Every member is a real person: a FancyNpcs player model wearing a skin painted for it ({@link CastSkins}),
 * turning its head to whoever walks up. Only where FancyNpcs is not installed does a member fall back to a
 * silent, AI-off villager, so a test server without it still has somebody to click.
 *
 * <p>The troupe owns the body and, if the look asks for one, the name label above it. Boards, barks and menus
 * stay with the cast that opened it; a click on a member comes back through {@link Clicks} with the member's
 * key. Nothing is saved: the cast tells the troupe once who stands where ({@link #spawn}) and the troupe keeps
 * that true by itself, across chunk loads and a FancyNpcs reload, so restarts and reloads cannot stack
 * duplicates.
 *
 * <p>A member can have a {@link Habit}: small things it does by itself while somebody is around to see it and
 * nobody stands close enough to talk (a swing of the tool, a glance to the side, a few steps and back).
 */
public final class CastTroupe implements Listener {

    /**
     * How a member looks. {@code skin} names a {@link CastSkins} PNG (null = default body); {@code outfit} is
     * the dyed leather worn until that skin is signed, and for good when there is none. {@code profession} and
     * {@code villagerType} dress the villager stand-in; {@code label} is an optional name plate.
     */
    public record Look(String name, String skin, ItemStack hand, Color outfit,
                       Villager.Profession profession, Villager.Type villagerType, String label) {

        public static Look of(String name, String skin, Material hand, Color outfit) {
            return new Look(name, skin, hand == null ? null : new ItemStack(hand), outfit, null, null, null);
        }

        /** How the villager stand-in is dressed where FancyNpcs is missing. */
        public Look stand(Villager.Profession fallbackProfession, Villager.Type fallbackType) {
            return new Look(name, skin, hand, outfit, fallbackProfession, fallbackType, label);
        }

        /** A name label above the head ({@code §} colours, lines split by {@code \n}); it follows the body. */
        public Look labelled(String text) {
            return new Look(name, skin, hand, outfit, profession, villagerType, text);
        }
    }

    /**
     * What a member does by itself. {@code tools} are held in turn for a working beat (empty = the usual hand
     * item), {@code gapMin}/{@code gapMax} the seconds between two beats, {@code stroll} how far (blocks) it may
     * wander from its spot on flat ground, 0 = stays put.
     */
    public record Habit(List<Material> tools, int gapMin, int gapMax, double stroll) {

        public static Habit work(int gapMin, int gapMax, Material... tools) {
            return new Habit(List.of(tools), gapMin, gapMax, 0.0d);
        }

        public Habit strolling(double blocks) {
            return new Habit(tools, gapMin, gapMax, blocks);
        }
    }

    @FunctionalInterface
    public interface Clicks {
        void click(Player player, String key);
    }

    private static final double WATCHED = 26.0d;
    private static final double CLOSE = 3.6d;
    private static final double STEP = 0.17d;
    private static final Set<Material> SOFT_GROUND = Set.of(Material.FARMLAND, Material.DIRT_PATH, Material.SOUL_SAND,
            Material.MUD);

    private static final class Body {
        final String key;
        final Look look;
        Location home;
        UUID villager;
        UUID label;
        String fancyName;
        boolean skinned;
        Habit habit;
        long nextBeat;
        int toolIndex;
        /** Stroll: where to, how far along, and whether it is on the way back. */
        Location walkFrom;
        Location walkTo;
        double walked;
        boolean returning;
        long restoreHandAt;
        long lookBackAt;

        Body(String key, Look look, Location home) {
            this.key = key;
            this.look = look;
            this.home = home;
        }
    }

    private final JavaPlugin owner;
    private final String id;
    private final String prefix;
    private final NamespacedKey tag;
    private final Clicks clicks;
    private final Map<String, Body> bodies = new LinkedHashMap<>();
    private final Consumer<String> signedHook = this::onSkinSigned;
    private final Set<String> complained = new java.util.HashSet<>();
    private int visibility = 48;
    private int turnDistance = 7;
    private boolean bridged;
    private boolean closed;
    private long now;
    private org.bukkit.scheduler.BukkitTask task;

    private CastTroupe(JavaPlugin owner, String id, Clicks clicks) {
        this.owner = owner;
        this.id = id.toLowerCase(Locale.ROOT);
        this.prefix = "ae_cast_" + this.id + "_";
        this.tag = new NamespacedKey(owner, "cast_" + this.id);
        this.clicks = clicks;
    }

    /**
     * Opens a troupe for {@code owner}. {@code id} must be unique per plugin (it names the bodies); leftovers of
     * an earlier troupe with the same id (a reload, a crash) are cleared first.
     */
    public static CastTroupe open(JavaPlugin owner, String id, Clicks clicks) {
        CastTroupe troupe = new CastTroupe(owner, id, clicks);
        Bukkit.getPluginManager().registerEvents(troupe, owner);
        troupe.bridge();
        troupe.purge();
        CastSkins skins = skins();
        if (skins != null) {
            skins.onSigned(troupe.signedHook);
        }
        troupe.task = Bukkit.getScheduler().runTaskTimer(owner, troupe::tick, 40L, 2L);
        return troupe;
    }

    /** How far away (blocks) the bodies are sent to a client. Default 48. */
    public CastTroupe visibility(int blocks) {
        this.visibility = Math.max(16, blocks);
        return this;
    }

    /** Removes every body and stops listening. The troupe cannot be used afterwards. */
    public void close() {
        clear();
        closed = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        CastSkins skins = skins();
        if (skins != null) {
            skins.removeListener(signedHook);
        }
    }

    /** True when members are player models (FancyNpcs is installed), false for the villager stand-ins. */
    public boolean playerModels() {
        return FancyNpcFacade.isAvailable();
    }

    // ------------------------------------------------------------------ bodies

    /**
     * Tells the troupe that this member stands at {@code at}. Safe to call again and again: a body that is
     * there is left alone (to move or redress one, {@link #remove} it first). When the body cannot appear yet
     * (FancyNpcs still loading, the chunk of a stand-in not loaded) the troupe keeps trying by itself.
     *
     * @return true when the body is there after the call
     */
    public boolean spawn(String key, Location at, Look look) {
        if (closed || key == null || look == null || at == null || at.getWorld() == null) {
            return false;
        }
        if (alive(key)) {
            return true;
        }
        Body old = bodies.remove(key);
        Body body = new Body(key, look, at.clone());
        if (old != null) {
            despawn(old);
            body.habit = old.habit;
        }
        bodies.put(key, body);
        return materialize(body);
    }

    private boolean materialize(Body body) {
        boolean there;
        if (FancyNpcFacade.isAvailable()) {
            bridge();
            there = spawnFancy(body);
        } else {
            Location at = body.home;
            there = at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
            if (there) {
                spawnVillager(body);
            }
        }
        if (there) {
            label(body);
        }
        return there;
    }

    private boolean alive(Body body) {
        if (body.fancyName != null) {
            return fancy(body) != null;
        }
        Entity villager = body.villager == null ? null : Bukkit.getEntity(body.villager);
        return villager != null && villager.isValid();
    }

    /** Every few seconds: bring back what a FancyNpcs reload or a chunk unload took away. */
    private void upkeep() {
        for (Body body : List.copyOf(bodies.values())) {
            if (!alive(body)) {
                body.fancyName = null;
                body.villager = null;
                body.walkTo = null;
                materialize(body);
            } else {
                label(body);
            }
        }
    }

    /** The name label: a text display over the head, there while its chunk is loaded. */
    private void label(Body body) {
        if (body.look.label() == null || body.look.label().isBlank()) {
            return;
        }
        Entity existing = body.label == null ? null : Bukkit.getEntity(body.label);
        if (existing != null && existing.isValid()) {
            return;
        }
        Location at = labelSpot(body, body.home);
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        String text = body.look.label();
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.text(LegacyComponentSerializer.legacySection().deserialize(text));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setViewRange(0.35f);
            spawned.setTeleportDuration(2);
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(tag, PersistentDataType.STRING, "label:" + body.key);
        });
        body.label = display.getUniqueId();
    }

    private static Location labelSpot(Body body, Location feet) {
        int lines = 1 + (int) body.look.label().chars().filter(c -> c == '\n').count();
        Location at = feet.clone().add(0.0d, 2.1d + 0.125d * lines, 0.0d);
        at.setYaw(0.0f);
        at.setPitch(0.0f);
        return at;
    }

    public boolean alive(String key) {
        Body body = bodies.get(key);
        return body != null && alive(body);
    }

    public void remove(String key) {
        Body body = bodies.remove(key);
        if (body != null) {
            despawn(body);
        }
    }

    public void clear() {
        for (Body body : List.copyOf(bodies.values())) {
            despawn(body);
        }
        bodies.clear();
    }

    public List<String> keys() {
        return new ArrayList<>(bodies.keySet());
    }

    /** Where the member is right now (it may be mid-stroll), or null when it has no body. */
    public Location location(String key) {
        Body body = bodies.get(key);
        if (body == null) {
            return null;
        }
        if (body.fancyName != null) {
            Object npc = fancy(body);
            if (npc == null) {
                return null;
            }
            try {
                return FancyNpcFacade.locationOf(npc);
            } catch (ReflectiveOperationException exception) {
                return body.home.clone();
            }
        }
        Entity villager = body.villager == null ? null : Bukkit.getEntity(body.villager);
        return villager != null && villager.isValid() ? villager.getLocation() : null;
    }

    /** Puts the member somewhere else at once and makes that its spot (walks are a row of small moves). */
    public boolean move(String key, Location to) {
        Body body = bodies.get(key);
        if (body == null || to == null || to.getWorld() == null) {
            return false;
        }
        body.home = to.clone();
        body.walkTo = null;
        return place(body, to);
    }

    /** One swing of the main hand. */
    public void swing(String key) {
        Body body = bodies.get(key);
        if (body != null) {
            swing(body);
        }
    }

    /** Changes what the member holds (null = empty hand). */
    public void hand(String key, ItemStack item) {
        Body body = bodies.get(key);
        if (body != null) {
            hold(body, item);
        }
    }

    /** Player models turn their head to a player nearby by themselves; switch it off for scripted moves. */
    public void turnToPlayers(String key, boolean on) {
        Body body = bodies.get(key);
        Object npc = body == null || body.fancyName == null ? null : fancy(body);
        if (npc == null) {
            return;
        }
        try {
            FancyNpcFacade.invoke(FancyNpcFacade.data(npc), "setTurnToPlayer", boolean.class, on);
            FancyNpcFacade.updateForAll(npc, false);
        } catch (ReflectiveOperationException exception) {
            complain(key, "turn", exception);
        }
    }

    /** Villager stand-ins only: look at the player (player models do it themselves). */
    public void face(String key, Player player) {
        Body body = bodies.get(key);
        Entity entity = body == null || body.villager == null ? null : Bukkit.getEntity(body.villager);
        if (!(entity instanceof Villager villager) || player == null || !villager.getWorld().equals(player.getWorld())) {
            return;
        }
        Location from = villager.getLocation();
        float yaw = yaw(from, player.getLocation());
        if (Math.abs(((yaw - from.getYaw()) % 360 + 540) % 360 - 180) > 4.0f) {
            villager.setRotation(yaw, 0.0f);
        }
    }

    /** The member this stand-in villager belongs to, or null (player models never are entities). */
    public String keyOf(Entity entity) {
        if (!(entity instanceof Villager)) {
            return null;
        }
        return entity.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ habits

    public void habit(String key, Habit habit) {
        Body body = bodies.get(key);
        if (body != null) {
            body.habit = habit;
            body.nextBeat = 0L;
        }
    }

    /** Every 2 ticks: habits; every 5 seconds: upkeep. Cheap when nobody is near. */
    private void tick() {
        if (closed) {
            return;
        }
        now += 2L;
        if (now % 100L == 0L) {
            try {
                upkeep();
            } catch (RuntimeException exception) {
                complain("*", "upkeep", exception);
            }
        }
        for (Body body : bodies.values()) {
            if (body.habit == null || body.fancyName == null) {
                continue;
            }
            try {
                live(body);
            } catch (RuntimeException exception) {
                complain(body.key, "habit", exception);
                body.habit = null;
            }
        }
    }

    private void live(Body body) {
        if (body.restoreHandAt > 0L && now >= body.restoreHandAt) {
            body.restoreHandAt = 0L;
            hold(body, body.look.hand());
        }
        if (body.lookBackAt > 0L && now >= body.lookBackAt) {
            body.lookBackAt = 0L;
            place(body, body.home);
        }
        if (body.walkTo != null) {
            stride(body);
            return;
        }
        if (now < body.nextBeat) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Habit habit = body.habit;
        body.nextBeat = now + 20L * random.nextInt(habit.gapMin(), Math.max(habit.gapMin(), habit.gapMax()) + 1);
        double nearest = nearestPlayer(body.home);
        if (nearest > WATCHED || nearest < CLOSE) {
            return; // nobody to see it, or somebody close enough to talk: stand still
        }
        int roll = random.nextInt(100);
        if (habit.stroll() >= 1.0d && roll < 22 && beginStroll(body, random)) {
            return;
        }
        if (roll < 70) {
            if (!habit.tools().isEmpty()) {
                Material tool = habit.tools().get(body.toolIndex++ % habit.tools().size());
                hold(body, new ItemStack(tool));
                body.restoreHandAt = now + 44L;
            }
            swing(body);
            if (random.nextInt(3) == 0) {
                Bukkit.getScheduler().runTaskLater(owner, () -> swing(body), 9L);
            }
            return;
        }
        if (nearest > turnDistance + 1.0d) {
            // A glance at the work beside them, then back to the front.
            Location glance = body.home.clone();
            glance.setYaw(body.home.getYaw() + (random.nextBoolean() ? 1 : -1) * random.nextInt(35, 71));
            glance.setPitch(random.nextInt(5, 26));
            place(body, glance);
            body.lookBackAt = now + random.nextInt(30, 56);
        }
    }

    private boolean beginStroll(Body body, ThreadLocalRandom random) {
        Location home = body.home;
        World world = home.getWorld();
        if (world == null) {
            return false;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0d);
            double reach = 1.5d + random.nextDouble(Math.max(0.1d, body.habit.stroll() - 1.5d));
            Location to = home.clone().add(Math.cos(angle) * reach, 0.0d, Math.sin(angle) * reach);
            if (!clearWalk(home, to)) {
                continue;
            }
            body.walkFrom = home.clone();
            body.walkTo = to;
            body.walked = 0.0d;
            body.returning = false;
            setTurn(body, false);
            return true;
        }
        return false;
    }

    private void stride(Body body) {
        Location from = body.walkFrom;
        Location to = body.walkTo;
        double length = Math.max(0.01d, Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()));
        body.walked += STEP;
        double u = Math.min(1.0d, body.walked / length);
        Location at = from.clone().add((to.getX() - from.getX()) * u, 0.0d, (to.getZ() - from.getZ()) * u);
        at.setYaw(yaw(from, to));
        at.setPitch(0.0f);
        place(body, at);
        if (u < 1.0d) {
            return;
        }
        if (!body.returning) {
            // At the far point: a working swing, a short stay, then home again.
            swing(body);
            body.returning = true;
            body.walkFrom = to;
            body.walkTo = body.home.clone();
            body.walked = -STEP * 14.0d;
            return;
        }
        body.walkTo = null;
        place(body, body.home);
        setTurn(body, true);
    }

    /** Flat, free ground all the way: same floor height, feet and head clear, no drop, no water. */
    private static boolean clearWalk(Location from, Location to) {
        World world = from.getWorld();
        int y = from.getBlockY();
        double length = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        int samples = Math.max(2, (int) Math.ceil(length / 0.4d));
        for (int i = 1; i <= samples; i++) {
            double u = i / (double) samples;
            int x = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * u);
            int z = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * u);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return false;
            }
            Block feet = world.getBlockAt(x, y, z);
            Block floor = world.getBlockAt(x, y - 1, z);
            if (!feet.isPassable() || feet.isLiquid() || !world.getBlockAt(x, y + 1, z).isPassable()) {
                return false;
            }
            if (!floor.getType().isSolid() && !SOFT_GROUND.contains(floor.getType())) {
                return false;
            }
        }
        return true;
    }

    private static double nearestPlayer(Location at) {
        World world = at.getWorld();
        double best = Double.MAX_VALUE;
        if (world == null) {
            return best;
        }
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(at);
            if (d < best) {
                best = d;
            }
        }
        return best == Double.MAX_VALUE ? best : Math.sqrt(best);
    }

    // ------------------------------------------------------------------ body work

    private boolean place(Body body, Location to) {
        boolean moved;
        if (body.fancyName != null) {
            Object npc = fancy(body);
            if (npc == null) {
                return false;
            }
            try {
                FancyNpcFacade.setLocation(FancyNpcFacade.data(npc), to);
                // Always without the arm swing: a bare moveForAll() would punch the air on every step.
                FancyNpcFacade.moveForAll(npc, false);
                moved = true;
            } catch (ReflectiveOperationException exception) {
                complain(body.key, "move", exception);
                return false;
            }
        } else {
            Entity villager = body.villager == null ? null : Bukkit.getEntity(body.villager);
            moved = villager != null && villager.isValid() && villager.teleport(to);
        }
        Entity label = !moved || body.label == null ? null : Bukkit.getEntity(body.label);
        if (label != null && label.isValid()) {
            Location spot = labelSpot(body, to);
            if (spot.distanceSquared(label.getLocation()) > 0.0004d) {
                label.teleport(spot);
            }
        }
        return moved;
    }

    private void swing(Body body) {
        if (body.fancyName != null) {
            Object npc = fancy(body);
            if (npc != null) {
                try {
                    FancyNpcFacade.moveForAll(npc, true);
                } catch (ReflectiveOperationException exception) {
                    complain(body.key, "swing", exception);
                }
            }
            return;
        }
        Entity entity = body.villager == null ? null : Bukkit.getEntity(body.villager);
        if (entity instanceof Villager villager) {
            villager.swingMainHand();
        }
    }

    private void hold(Body body, ItemStack item) {
        ItemStack use = item == null ? new ItemStack(Material.AIR) : item.clone();
        if (body.fancyName != null) {
            Object npc = fancy(body);
            if (npc == null) {
                return;
            }
            try {
                Object data = FancyNpcFacade.data(npc);
                equip(data, "MAINHAND", use);
                FancyNpcFacade.updateForAll(npc, false);
            } catch (ReflectiveOperationException exception) {
                complain(body.key, "hand", exception);
            }
            return;
        }
        Entity entity = body.villager == null ? null : Bukkit.getEntity(body.villager);
        if (entity instanceof Villager villager && villager.getEquipment() != null) {
            villager.getEquipment().setItemInMainHand(use);
        }
    }

    private void setTurn(Body body, boolean on) {
        Object npc = fancy(body);
        if (npc == null) {
            return;
        }
        try {
            FancyNpcFacade.invoke(FancyNpcFacade.data(npc), "setTurnToPlayer", boolean.class, on);
        } catch (ReflectiveOperationException exception) {
            complain(body.key, "turn", exception);
        }
    }

    private Object fancy(Body body) {
        if (body.fancyName == null || !FancyNpcFacade.isAvailable()) {
            return null;
        }
        try {
            return FancyNpcFacade.getNpc(FancyNpcFacade.manager(), body.fancyName);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private boolean spawnFancy(Body body) {
        String name = prefix + body.key;
        try {
            Object manager = FancyNpcFacade.manager();
            if (manager == null || !FancyNpcFacade.isManagerLoaded(manager)) {
                return false; // FancyNpcs is still reading its own file: the cast asks again in a moment
            }
            Object existing = FancyNpcFacade.getNpc(manager, name);
            if (existing != null) {
                FancyNpcFacade.removeFromPlayersQuiet(existing);
                FancyNpcFacade.unregister(manager, existing);
            }
            Object data = FancyNpcFacade.createNpcData(name, new UUID(0L, 0L), body.home);
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.invoke(data, "setTurnToPlayerDistance", int.class, turnDistance);
            FancyNpcFacade.applyVisibility(data, visibility);
            FancyNpcFacade.invoke(data, "setInteractionCooldown", float.class, 0.5f);
            FancyNpcFacade.invoke(data, "setSpawnEntity", boolean.class, true);
            CastSkins skins = skins();
            body.skinned = skins != null && skins.apply(data, body.look.skin());
            if (body.look.hand() != null) {
                equip(data, "MAINHAND", body.look.hand().clone());
            }
            if (!body.skinned && body.look.outfit() != null) {
                // Until the painted skin is signed: dyed leather on the default body, never somebody else's face.
                equip(data, "CHEST", dyed(Material.LEATHER_CHESTPLATE, body.look.outfit()));
                equip(data, "LEGS", dyed(Material.LEATHER_LEGGINGS, body.look.outfit()));
                equip(data, "FEET", dyed(Material.LEATHER_BOOTS, body.look.outfit()));
            }
            Object fancy = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(fancy, false);
            FancyNpcFacade.create(fancy);
            FancyNpcFacade.register(manager, fancy);
            FancyNpcFacade.spawnForAll(fancy);
            body.fancyName = name;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            complain(body.key, "spawn", exception);
            return false;
        }
    }

    private void spawnVillager(Body body) {
        Look look = body.look;
        Location at = body.home;
        Villager villager = at.getWorld().spawn(at, Villager.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setCollidable(false);
            spawned.setGravity(false);
            spawned.setAdult();
            spawned.setAgeLock(true);
            if (look.profession() != null) {
                spawned.setProfession(look.profession());
            }
            if (look.villagerType() != null) {
                spawned.setVillagerType(look.villagerType());
            }
            spawned.setVillagerLevel(5);
            spawned.setRecipes(new ArrayList<MerchantRecipe>());
            if (look.name() != null) {
                spawned.customName(Component.text(look.name()));
            }
            spawned.setCustomNameVisible(false);
            spawned.getPersistentDataContainer().set(tag, PersistentDataType.STRING, body.key);
            if (look.hand() != null && spawned.getEquipment() != null) {
                spawned.getEquipment().setItemInMainHand(look.hand().clone());
            }
        });
        body.villager = villager.getUniqueId();
    }

    private void despawn(Body body) {
        if (body.label != null) {
            Entity label = Bukkit.getEntity(body.label);
            if (label != null) {
                label.remove();
            }
        }
        if (body.villager != null) {
            Entity villager = Bukkit.getEntity(body.villager);
            if (villager != null) {
                villager.remove();
            }
        }
        if (body.fancyName != null && FancyNpcFacade.isAvailable()) {
            try {
                Object manager = FancyNpcFacade.manager();
                Object fancy = FancyNpcFacade.getNpc(manager, body.fancyName);
                if (fancy != null) {
                    FancyNpcFacade.removeFromPlayersQuiet(fancy);
                    FancyNpcFacade.unregister(manager, fancy);
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                complain(body.key, "remove", exception);
            }
        }
    }

    /** Bodies left by an earlier troupe of this id: tagged stand-ins in loaded chunks and player models by name. */
    private void purge() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(tag, PersistentDataType.STRING)) {
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
                if (name != null && name.startsWith(prefix)) {
                    FancyNpcFacade.removeFromPlayersQuiet(npc);
                    FancyNpcFacade.unregister(manager, npc);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().fine("Cast " + id + ": purge skipped (" + exception.getMessage() + ")");
        }
    }

    private void onSkinSigned(String skinId) {
        if (closed) {
            return;
        }
        CastSkins skins = skins();
        for (Body body : bodies.values()) {
            if (body.skinned || body.fancyName == null || body.look.skin() == null
                    || !body.look.skin().equalsIgnoreCase(skinId)) {
                continue;
            }
            Object npc = fancy(body);
            if (npc == null || skins == null) {
                continue;
            }
            try {
                Object data = FancyNpcFacade.data(npc);
                if (!skins.apply(data, skinId)) {
                    continue;
                }
                body.skinned = true;
                ItemStack air = new ItemStack(Material.AIR);
                equip(data, "CHEST", air);
                equip(data, "LEGS", air);
                equip(data, "FEET", air);
                FancyNpcFacade.removeFromPlayersQuiet(npc);
                FancyNpcFacade.spawnForAll(npc);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                complain(body.key, "skin", exception);
            }
        }
    }

    private static void equip(Object data, String slot, ItemStack item) throws ReflectiveOperationException {
        Method add = data.getClass().getMethod("addEquipment", FancyNpcFacade.equipmentSlotClass(), ItemStack.class);
        add.invoke(data, FancyNpcFacade.equipmentSlot(slot), item);
    }

    private static ItemStack dyed(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            leather.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(leather);
        }
        return item;
    }

    private static float yaw(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static CastSkins skins() {
        AetherionCore core = AetherionCore.get();
        return core == null ? null : core.castSkins();
    }

    /** One warning per member and kind of failure: a broken body must not flood the log every few seconds. */
    private void complain(String key, String what, Throwable throwable) {
        if (!complained.add(key + ":" + what)) {
            return;
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        owner.getLogger().warning("Cast " + id + ": " + what + " failed for " + key + " ("
                + root.getClass().getSimpleName() + ": " + root.getMessage() + ")");
    }

    // ------------------------------------------------------------------ clicks

    /** FancyNpcs fires its own interact event for player models (reflection: no compile link to it). */
    private void bridge() {
        if (bridged || !FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            Listener marker = new Listener() {
            };
            EventExecutor executor = (listener, event) -> {
                if (closed || !eventClass.isInstance(event)) {
                    return;
                }
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    String name = click.name();
                    if (name == null || !name.startsWith(prefix) || click.player() == null) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    String key = name.substring(prefix.length());
                    Player player = click.player();
                    Bukkit.getScheduler().runTask(owner, () -> click(player, key));
                } catch (ReflectiveOperationException exception) {
                    complain("*", "click", exception);
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, marker, EventPriority.NORMAL, executor, owner, true);
            bridged = true;
        } catch (ClassNotFoundException exception) {
            owner.getLogger().warning("Cast " + id + ": FancyNpcs interact event missing, clicks on player models are off.");
        }
    }

    private void click(Player player, String key) {
        if (closed || clicks == null || !player.isOnline() || !bodies.containsKey(key)) {
            return;
        }
        try {
            clicks.click(player, key);
        } catch (RuntimeException exception) {
            complain(key, "click", exception);
        }
    }

    /** Stand-ins and labels are not saved with the chunk: put them back as soon as it loads. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (closed || bodies.isEmpty()) {
            return;
        }
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        World world = event.getWorld();
        for (Body body : bodies.values()) {
            Location home = body.home;
            if (world.equals(home.getWorld()) && (home.getBlockX() >> 4) == cx && (home.getBlockZ() >> 4) == cz) {
                Bukkit.getScheduler().runTask(owner, () -> {
                    if (!closed && bodies.get(body.key) == body) {
                        if (alive(body)) {
                            label(body);
                        } else {
                            materialize(body);
                        }
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStandInClick(PlayerInteractEntityEvent event) {
        String key = keyOf(event.getRightClicked());
        if (key == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            click(event.getPlayer(), key);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStandInClickAt(PlayerInteractAtEntityEvent event) {
        if (keyOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStandInDamage(EntityDamageEvent event) {
        if (keyOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Lightning would turn a stand-in into an untagged, persistent witch. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStandInZap(EntityZapEvent event) {
        if (keyOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStandInTransform(EntityTransformEvent event) {
        if (keyOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
