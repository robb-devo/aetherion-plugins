package de.aetherion.fishing.isle;

import de.aetherion.fishing.AetherionFishing;
import de.aetherion.fishing.CastHooks;
import de.aetherion.fishing.CastSession;
import de.aetherion.fishing.FishingStats;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fishing Eldervale — the fishing destination. Owns every isle loop and routes one cast through
 * all of them:
 * <pre>
 *   bobber settles ─ shoal / Silver Run / bait shorten the wait
 *   bite ─────────── TheLine rolls the species (water, time, rain, heat, shoal, bait, skills)
 *                    and reshapes the strike bar for rare and legendary fish
 *   landed ───────── weigh → Angler's Log (+ PB, records, rank) → trophy → shoal stock
 *                    → Silver Run tally / Eldermaw heave → Wishing Fountain coin
 *   missed ───────── second chance on rares · the run forgives one · Steady Line
 * </pre>
 * Off the isle nothing changes: every hook returns the harbour default.
 */
public final class FishIsle implements CastHooks, Listener, StatProvider {

    private final AetherionFishing plugin;
    private final FishingStats stats = new FishingStats();
    private final Waters waters;
    private final AnglerProfiles profiles;
    private final Trophies trophies;
    private final AnglerLog log;
    private final TheLine line;
    private final Shoals shoals;
    private final LakeEvents events;
    private final BaitShack bait;
    private final LakeCompass compass;
    private final LakeCast cast;
    private final LakeMenus menus;
    private final LakeDev dev;
    private final List<Location> bells = new ArrayList<>();
    private boolean itemsHooked;

    public FishIsle(AetherionFishing plugin) {
        this.plugin = plugin;
        LakeWorld.refresh(plugin);
        this.waters = new Waters(plugin);
        this.profiles = new AnglerProfiles(plugin);
        this.trophies = new Trophies(plugin);
        this.log = new AnglerLog(this);
        this.line = new TheLine(this);
        this.shoals = new Shoals(this);
        this.events = new LakeEvents(this);
        this.bait = new BaitShack(this);
        this.compass = new LakeCompass(this);
        this.cast = new LakeCast(this);
        this.menus = new LakeMenus(this);
        this.dev = new LakeDev(this);
        loadBells();
    }

    public void start() {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(this, plugin);
        pm.registerEvents(trophies, plugin);
        pm.registerEvents(events, plugin);
        pm.registerEvents(cast, plugin);
        pm.registerEvents(menus, plugin);
        shoals.start();
        events.start();
        compass.start();
        cast.start();
        try {
            ActiveEquipmentStats.registerProvider(this);
            itemsHooked = true;
        } catch (LinkageError error) {
            plugin.getLogger().warning("Fishing Eldervale: AetherionItems stat hook unavailable — Angler Rank bonuses off.");
        }
        plugin.getLogger().info("Fishing Eldervale: " + waters.size() + " waters, " + shoals.spots().size()
                + " shoal spots, " + cast.placedCount() + " NPCs placed, footprint " + LakeWorld.describe());
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (itemsHooked) {
            ActiveEquipmentStats.unregisterProvider(this);
            itemsHooked = false;
        }
        events.shutdown();
        shoals.shutdown();
        compass.shutdown();
        cast.shutdown();
        profiles.shutdown();
    }

    /** Reload config-driven parts (footprint, waters, shoal spots, bells) without touching player data. */
    public String reload() {
        plugin.reloadConfig();
        LakeWorld.refresh(plugin);
        waters.reload();
        shoals.reload();
        loadBells();
        return "§aFishing config reloaded §8· §7" + waters.size() + " waters · " + shoals.spots().size() + " shoal spots";
    }

    private void loadBells() {
        bells.clear();
        World world = LakeWorld.world();
        if (world == null) {
            return;
        }
        for (String raw : plugin.getConfig().getStringList("lake-events.bells")) {
            double[] v = LakeText.numbers(raw, 3);
            if (v != null) {
                bells.add(new Location(world, v[0], v[1], v[2]));
            }
        }
    }

    List<Location> bells() {
        return bells;
    }

    // ------------------------------------------------------------------ CastHooks

    @Override
    public double settle(Player player, CastSession session, Location hook) {
        return LakeWorld.onIsle(hook) ? line.settle(player, hook) : 1.0d;
    }

    @Override
    public int extraLures(Player player, CastSession session, Location hook) {
        return LakeWorld.onIsle(hook) ? line.extraLures(hook) : 0;
    }

    @Override
    public String waitTag(Player player, CastSession session, Location hook, int streak) {
        return LakeWorld.onIsle(hook) ? line.waitTag(player, hook, streak) : null;
    }

    @Override
    public BiteCue bite(Player player, CastSession session, Location hook, int streak) {
        return LakeWorld.onIsle(hook) ? line.bite(player, session, hook, streak) : null;
    }

    @Override
    public String landed(Player player, CastSession session, Location hook, boolean perfect, int streak) {
        return LakeWorld.onIsle(hook) ? line.landed(player, session, hook, perfect, streak) : null;
    }

    @Override
    public boolean missed(Player player, CastSession session, Location hook, boolean timeout, int streak) {
        return LakeWorld.onIsle(hook) && line.missed(player, session, hook, timeout, streak);
    }

    @Override
    public void forget(UUID playerId) {
        line.forget(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        compass.forget(id);
        events.onQuit(id);
        profiles.unload(id);
    }

    // ------------------------------------------------------------------ Items-facing reads

    /** Angler Rank: isle-only Fish Catch / Fish Speed. Fishing-only capabilities, safe as a global provider. */
    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || (capability != ItemCapability.FISHING_CATCH && capability != ItemCapability.FISHING_SPEED)) {
            return 0.0d;
        }
        if (!LakeWorld.onIsle(player)) {
            return 0.0d;
        }
        return capability == ItemCapability.FISHING_CATCH ? log.catchBonus(player) : log.speedBonus(player);
    }

    // ------------------------------------------------------------------ controller bridge

    boolean casting(Player player) {
        return plugin.controller() != null && plugin.controller().casting(player.getUniqueId());
    }

    int streak(Player player) {
        return plugin.controller() == null ? 0 : plugin.controller().streak(player.getUniqueId());
    }

    void setStreak(Player player, int streak) {
        if (plugin.controller() != null) {
            plugin.controller().setStreak(player.getUniqueId(), streak);
        }
    }

    // ------------------------------------------------------------------ accessors

    public AetherionFishing plugin() {
        return plugin;
    }

    FishingStats stats() {
        return stats;
    }

    public Waters waters() {
        return waters;
    }

    public AnglerProfiles profiles() {
        return profiles;
    }

    Trophies trophies() {
        return trophies;
    }

    AnglerLog log() {
        return log;
    }

    TheLine line() {
        return line;
    }

    Shoals shoals() {
        return shoals;
    }

    LakeEvents events() {
        return events;
    }

    BaitShack bait() {
        return bait;
    }

    LakeCompass compass() {
        return compass;
    }

    LakeCast cast() {
        return cast;
    }

    LakeMenus menus() {
        return menus;
    }

    public LakeDev dev() {
        return dev;
    }
}
