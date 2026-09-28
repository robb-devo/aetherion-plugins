package de.aetherion.farming.isle;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.Crops;
import de.aetherion.farming.FarmingSkills;
import de.aetherion.farming.FeaturedCropService;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Eldervale Farm Isle — the Farming destination. Owns every isle loop and routes one harvest
 * through all of them:
 * <pre>
 *   mature crop broken ─┬─ Crop Mastery    (everywhere; isle counts ×2)
 *                       └─ on the isle ───┬─ Harvest Rhythm   (by hand only)
 *                                         ├─ Prize Crops      (by hand only, rare giant find)
 *                                         ├─ Isle events      (Bee Bloom XP / tally)
 *                                         └─ Bakehouse XP food
 * </pre>
 * Items reads crop Fortune / harvest extras back through {@code FarmAccess}.
 */
public final class FarmIsle implements Listener, StatProvider {

    /** Items' Harvest spread marks neighbour breaks with this metadata key. */
    private static final String SPREAD_METADATA = "aetherion_crop_break";

    private final AetherionFarming plugin;
    private final IslePlots plots;
    private final IsleProfiles profiles;
    private final HarvestRhythm rhythm;
    private final PrizeCrops prizes;
    private final CropMastery mastery;
    private final IsleEvents events;
    private final HarvestOrders orders;
    private final Bakehouse bakehouse;
    private final IsleCompass compass;
    private final IsleCast cast;
    private final IsleMenus menus;
    private final IsleDev dev;
    private boolean itemsHooked;

    public FarmIsle(AetherionFarming plugin) {
        this.plugin = plugin;
        this.plots = new IslePlots(plugin);
        this.profiles = new IsleProfiles(plugin);
        this.rhythm = new HarvestRhythm(this);
        this.prizes = new PrizeCrops(this);
        this.mastery = new CropMastery(this);
        this.events = new IsleEvents(this);
        this.orders = new HarvestOrders(this);
        this.bakehouse = new Bakehouse(this);
        this.compass = new IsleCompass(this);
        this.cast = new IsleCast(this);
        this.menus = new IsleMenus(this);
        this.dev = new IsleDev(this);
    }

    public void start() {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(this, plugin);
        pm.registerEvents(prizes, plugin);
        pm.registerEvents(bakehouse, plugin);
        pm.registerEvents(cast, plugin);
        pm.registerEvents(menus, plugin);
        rhythm.start();
        events.start();
        compass.start();
        cast.start();
        try {
            ActiveEquipmentStats.registerProvider(this);
            itemsHooked = true;
        } catch (LinkageError error) {
            plugin.getLogger().warning("Eldervale: AetherionItems stat hook unavailable — Harvest bonuses off.");
        }
        plugin.getLogger().info("Eldervale Farm Isle: " + plots.size() + " plots, " + cast.placedCount() + " NPCs placed.");
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (itemsHooked) {
            ActiveEquipmentStats.unregisterProvider(this);
            itemsHooked = false;
        }
        events.shutdown();
        rhythm.shutdown();
        prizes.shutdown();
        compass.shutdown();
        cast.shutdown();
        profiles.shutdown();
    }

    /** Reload config-driven parts (plots, presets) without touching player data. */
    public String reload() {
        plugin.reloadConfig();
        IsleWorld.refresh(plugin);
        plots.reload();
        return "§aFarming config reloaded §8· §7" + plots.size() + " plots";
    }

    // ------------------------------------------------------------------ harvest routing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        if (!Crops.isCrop(type) || Crops.isDungeonWorld(block.getWorld()) || Crops.isBuildWorld(block.getWorld())
                || !Crops.isMature(block)) {
            return;
        }
        IsleCrop crop = IsleCrop.fromBlock(type);
        Location at = block.getLocation();
        boolean onIsle = IsleWorld.onIsle(plugin, at);
        mastery.onHarvest(player, crop, onIsle);
        if (!onIsle) {
            return;
        }
        boolean spread = block.hasMetadata(SPREAD_METADATA);
        if (!spread) {
            rhythm.onHarvest(player, at);
            prizes.roll(player, crop, at);
        }
        events.onHarvest(player, at);
        int foodXp = bakehouse.xpPerHarvest(player);
        if (foodXp > 0) {
            FarmingSkills.bonus(player, foodXp);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        rhythm.clear(id);
        compass.forget(id);
        events.onQuit(id);
        profiles.unload(id);
    }

    // ------------------------------------------------------------------ Items-facing reads

    /** Crop-only Fortune: mastery of this crop + rhythm tier + bakehouse food (the latter two isle-bound). */
    public double cropFortune(Player player, Material cropBlock) {
        if (player == null) {
            return 0.0d;
        }
        IsleCrop crop = IsleCrop.from(cropBlock);
        double total = mastery.cropFortune(player, crop) + bakehouse.cropFortune(player);
        if (IsleWorld.onIsle(plugin, player)) {
            total += rhythm.cropFortune(player);
        }
        return total;
    }

    /** Featured crop + Bee Bloom extras for one harvest. */
    public int harvestBonus(Player player, Material yield, Location at) {
        int bonus = 0;
        FeaturedCropService featured = plugin.featuredCrop();
        if (featured != null && featured.isFeatured(yield) && at != null && IsleWorld.onIsle(plugin, at)) {
            bonus += featured.bonusAmount();
        }
        if (player != null) {
            bonus += events.bloomExtra(player, at);
        }
        return bonus;
    }

    public String harvestBonusLabel(Player player, Location at) {
        IslePlots.Plot bloom = events.bloomPlot();
        if (bloom != null && at != null && bloom.contains(at)) {
            return "Bee Bloom";
        }
        FeaturedCropService featured = plugin.featuredCrop();
        return featured == null ? null : "Featured " + featured.prettyName();
    }

    /** Harvest spread from rhythm and food — farming-only capability, safe as a global provider. */
    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability != ItemCapability.HARVEST_SPREAD) {
            return 0.0d;
        }
        double total = bakehouse.harvestSpread(player);
        if (IsleWorld.onIsle(plugin, player)) {
            total += rhythm.harvestSpread(player);
        }
        return total;
    }

    // ------------------------------------------------------------------ accessors

    public AetherionFarming plugin() {
        return plugin;
    }

    public IslePlots plots() {
        return plots;
    }

    public IsleProfiles profiles() {
        return profiles;
    }

    public HarvestRhythm rhythm() {
        return rhythm;
    }

    public PrizeCrops prizes() {
        return prizes;
    }

    public CropMastery mastery() {
        return mastery;
    }

    public IsleEvents events() {
        return events;
    }

    public HarvestOrders orders() {
        return orders;
    }

    public Bakehouse bakehouse() {
        return bakehouse;
    }

    public IsleCompass compass() {
        return compass;
    }

    public IsleCast cast() {
        return cast;
    }

    public IsleMenus menus() {
        return menus;
    }

    public IsleDev dev() {
        return dev;
    }
}
