package de.aetherion.mining.isle;

import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.MiningBlocks;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Mining Eldervale: the mining destination. It owns every isle loop and routes each strike through
 * all of them, the same way the Farm Isle router routes a harvest:
 * <pre>
 *   strike ─ Strike Rhythm (work song, Seam Chain, Seam Burst)
 *          ─ Crystal Finds (geode pushes out of the rock → crack it → specimen)
 *          ─ Ore Mastery (per family, I–VII, permanent ore-only Fortune / Power)
 *          ─ Foreman Contracts (hand-ins + deep / Amethyst shifts)
 *          ─ Mine events (Rich Vein · Ember Hour · Tremor · Troll Run)
 *          ─ Depth (four bands → Fortune, Crystal luck, XP) · Shift streak
 *          ─ Critters (Cinder Mites, Gloam Moths, Stonejaw) · Hazards (strain → cave-in, heat, dark)
 *   payout ─ Items HarvestListener asks back: ore Fortune · ore Power · speed · Rich Vein extras
 * </pre>
 * The Amethyst Mine (/amethyst, The Veins dig world) runs its own layer on top: Resonance,
 * Shardlings, Geode Hearts and Heartstone specimens. Off the isle and outside the Amethyst Mine,
 * only mastery keeps counting (at half weight). Everything else answers with the default.
 */
public final class MineIsle implements Listener, StatProvider {

    private final AetherionMining plugin;
    private final MineProfiles profiles;
    private final MineDistricts districts;
    private final StrikeRhythm rhythm;
    private final CrystalFinds crystals;
    private final OreMastery mastery;
    private final AssayLedger ledger;
    private final Hearth hearth;
    private final ForemanContracts contracts;
    private final MineEvents events;
    private final MineCast cast;
    private final MineCompass compass;
    private final MineMenus menus;
    private final ForgeWorks forge;
    private final MineProps props;
    private final MineCritters critters;
    private final MineHazards hazards;
    private final AmethystMine amethyst;
    private final MineDev dev;
    private boolean itemsHooked;
    /** Ore / crystal blocks a player placed: breaking them again feeds no loop (no place-break farming). */
    private final java.util.Map<UUID, java.util.Set<Long>> placedBlocks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int PLACED_CAP = 200_000;

    public MineIsle(AetherionMining plugin) {
        this.plugin = plugin;
        MineWorld.refresh(plugin);
        this.profiles = new MineProfiles(plugin);
        this.districts = new MineDistricts(plugin);
        this.rhythm = new StrikeRhythm(this);
        this.crystals = new CrystalFinds(this);
        this.mastery = new OreMastery(this);
        this.ledger = new AssayLedger(this);
        this.hearth = new Hearth(this);
        this.contracts = new ForemanContracts(this);
        this.events = new MineEvents(this);
        this.cast = new MineCast(this);
        this.compass = new MineCompass(this);
        this.menus = new MineMenus(this);
        this.forge = new ForgeWorks(this);
        this.props = new MineProps(this);
        this.critters = new MineCritters(this);
        this.hazards = new MineHazards(this);
        this.amethyst = new AmethystMine(this);
        this.dev = new MineDev(this);
    }

    /**
     * Must run BEFORE {@code MiningListener} is registered: both watch the break at MONITOR, and the
     * seal cancels the event, so the isle router has to see it first.
     */
    public void start() {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(this, plugin);
        pm.registerEvents(crystals, plugin);
        pm.registerEvents(hearth, plugin);
        pm.registerEvents(events, plugin);
        pm.registerEvents(cast, plugin);
        pm.registerEvents(menus, plugin);
        pm.registerEvents(forge, plugin);
        pm.registerEvents(props, plugin);
        pm.registerEvents(critters, plugin);
        pm.registerEvents(hazards, plugin);
        pm.registerEvents(amethyst, plugin);
        crystals.purgeStrays();
        forge.purgeStrays();
        rhythm.start();
        events.start();
        cast.start();
        compass.start();
        props.start();
        critters.start();
        hazards.start();
        amethyst.start();
        try {
            ActiveEquipmentStats.registerProvider(this);
            itemsHooked = true;
        } catch (LinkageError error) {
            plugin.getLogger().warning("Mining Eldervale: AetherionItems stat hook unavailable. Rhythm/ration Spread is off.");
        }
        plugin.getLogger().info("Mining Eldervale: " + districts.size() + " districts, " + cast.placedCount()
                + " NPCs placed, " + MineWorld.describe(plugin).replaceAll("§.", ""));
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (itemsHooked) {
            try {
                ActiveEquipmentStats.unregisterProvider(this);
            } catch (LinkageError ignored) {
            }
            itemsHooked = false;
        }
        amethyst.shutdown();
        hazards.shutdown();
        critters.shutdown();
        props.shutdown();
        compass.shutdown();
        cast.shutdown();
        events.shutdown();
        rhythm.shutdown();
        crystals.shutdown();
        profiles.shutdown();
    }

    /** Reload config-driven parts (footprint, districts, presets, props) without touching player data. */
    public String reload() {
        plugin.reloadConfig();
        MineWorld.refresh(plugin);
        districts.reload();
        props.rebuild();
        return "§aMining Eldervale reloaded §8· §7" + districts.size() + " districts · " + MineWorld.describe(plugin);
    }

    // ------------------------------------------------------------------ the strike

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material material = event.getBlockPlaced().getType();
        if (!MiningBlocks.allows(material) && !AmethystMine.counts(material)) {
            return;
        }
        java.util.Set<Long> set = placedBlocks.computeIfAbsent(event.getBlockPlaced().getWorld().getUID(),
                ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (set.size() >= PLACED_CAP) {
            set.clear();
        }
        set.add(blockKey(event.getBlockPlaced()));
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        placedBlocks.remove(event.getWorld().getUID());
    }

    private static long blockKey(Block block) {
        return ((long) block.getX() & 0x3FFFFFFL) << 38 | ((long) block.getZ() & 0x3FFFFFFL) << 12 | ((block.getY() + 2048L) & 0xFFFL);
    }

    /** True (and forgotten) when a player placed this block: mined again, it counts for nothing. */
    private boolean wasPlaced(Block block) {
        java.util.Set<Long> set = placedBlocks.get(block.getWorld().getUID());
        return set != null && set.remove(blockKey(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        Block block = event.getBlock();
        Material material = block.getType();
        if (!MiningBlocks.allows(material) && !AmethystMine.counts(material)) {
            return;
        }
        IsleOre ore = IsleOre.fromBlock(material);
        if (ore == null && (material == Material.STONE || material == Material.DEEPSLATE)) {
            ore = IsleOre.STONE;
        }
        if (ore == null) {
            ore = AmethystMine.oreOf(material);
        }
        if (ore == null || wasPlaced(block) || de.aetherion.mining.SharedWorldGuard.isBuildWorld(block.getWorld())) {
            return;
        }
        Location at = block.getLocation();
        boolean spread = block.hasMetadata("aetherion_spread_break");
        boolean veins = plugin.getVeins() != null && plugin.getVeins().isVeins(block.getWorld());
        boolean isle = !veins && MineWorld.onIsle(plugin, at);

        if (!isle && !veins) {
            mastery.onMined(player, ore, 1);
            return;
        }
        mastery.onMined(player, ore, 2);
        if (veins) {
            onVeinsStrike(player, ore, at, spread);
            return;
        }
        MineDistricts.Band band = districts.band(at);
        noteDepth(player, at, band);
        contracts.onMined(player, ore, false, band);
        events.onMined(player, ore, at);
        noteShift(player);
        if (spread) {
            return;
        }
        rhythm.onStrike(player, ore, at);
        if (ore != IsleOre.STONE) {
            crystals.roll(player, ore, at, false);
        }
        critters.onMined(player, ore, at, band);
        hazards.onMined(player, ore, at, band);
        if (band.bonusXp() > 0 && ore != IsleOre.STONE) {
            MineSkills.bonus(player, (int) Math.round(band.bonusXp() * (1.0d + 0.5d * MineSkills.scale(player, MineSkills.DEPTH_GAUGE))));
        }
    }

    private void onVeinsStrike(Player player, IsleOre ore, Location at, boolean spread) {
        MineProfiles.Profile profile = profiles.of(player);
        int generation = plugin.getVeins().generation();
        if (profile.veinsGeneration != generation) {
            profile.veinsGeneration = generation;
            profile.veinsMined = 0L;
        }
        if (ore != IsleOre.STONE) {
            profile.veinsMined++;
            profiles.markDirty();
        }
        contracts.onMined(player, ore, true, MineDistricts.Band.SURFACE);
        if (spread) {
            return;
        }
        amethyst.onMined(player, ore, at);
        if (ore != IsleOre.STONE) {
            crystals.roll(player, ore, at, true);
        }
    }

    /** Deepest point + first time in each band (title card and a little XP). */
    private void noteDepth(Player player, Location at, MineDistricts.Band band) {
        MineProfiles.Profile profile = profiles.of(player);
        int y = at.getBlockY();
        if (y < profile.deepest) {
            profile.deepest = y;
            profiles.markDirty();
        }
        if (band != MineDistricts.Band.SURFACE && profile.bands.add(band.name())) {
            profiles.markDirty();
            int xp = 50 * (band.ordinal() + 1);
            MineSkills.bonus(player, xp);
            player.showTitle(net.kyori.adventure.title.Title.title(
                    MineText.legacy(band.colored()),
                    MineText.legacy("§7First strike this deep §8· §a+" + xp + " Mining XP §8· §e+"
                            + MineText.num(band.fortune()) + " ore Fortune down here"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2400), java.time.Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.5f, 0.6f);
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.PLAYERS, 0.6f, 0.8f);
        }
    }

    /** A shift = N isle blocks in one day. Consecutive shifts build a streak (+1 ore Fortune per day, max 7). */
    private void noteShift(Player player) {
        MineProfiles.Profile profile = profiles.of(player);
        long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        if (profile.streakDay == today) {
            return;
        }
        if (profile.blocksDay != today) {
            profile.blocksDay = today;
            profile.dayBlocks = 0;
        }
        profile.dayBlocks++;
        if (profile.dayBlocks % 25 == 0) {
            profiles.markDirty();
        }
        int need = Math.max(20, plugin.getConfig().getInt("mine-isle.shift-blocks", 250));
        if (profile.dayBlocks < need) {
            if (profile.dayBlocks == need / 2) {
                MineText.bar(player, "§7Shift §f" + profile.dayBlocks + "§7/§f" + need + " §8· §7log a shift today to keep your streak");
            }
            return;
        }
        profile.streak = profile.streakDay == today - 1 ? profile.streak + 1 : 1;
        profile.bestStreak = Math.max(profile.bestStreak, profile.streak);
        profile.streakDay = today;
        profile.dayBlocks = 0;
        profiles.markDirty();
        int days = Math.min(7, profile.streak);
        long coins = 150L * days;
        int xp = 60 + 20 * days;
        MineSkills.coins(player, coins);
        MineSkills.bonus(player, xp);
        player.sendMessage("§6⚒ Shift logged! §7Streak §f" + profile.streak + " day" + (profile.streak == 1 ? "" : "s")
                + " §8· §e+" + days + " ore Fortune on the isle §8· §6+" + MineText.coins(coins) + " coins §8· §a+" + xp + " Mining XP");
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        player.spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1.6, 0), 12, 0.4, 0.3, 0.4, 0.0);
    }

    /** Current streak bonus (0 when the streak lapsed: yesterday is the latest shift that still counts). */
    public int streakFortune(Player player) {
        MineProfiles.Profile profile = profiles.of(player);
        long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        if (profile.streakDay < today - 1) {
            return 0;
        }
        return Math.min(7, profile.streak);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rhythm.clear(id);
        events.onQuit(id);
        compass.forget(id);
        hazards.forget(id);
        amethyst.forget(id);
        crystals.forget(id);
        cast.forget(id);
        forge.forget(id);
        critters.forget(id);
        profiles.unload(id);
    }

    // ------------------------------------------------------------------ Items-facing reads (MiningAccess)

    boolean onIsle(Location at) {
        return MineWorld.onIsle(plugin, at);
    }

    private boolean inVeins(Location at) {
        return at != null && plugin.getVeins() != null && plugin.getVeins().isVeins(at.getWorld());
    }

    /** Ore-only Fortune from every Mining loop. Isle: everything. Amethyst Mine: mastery, ledger, marks, resonance. */
    public double oreFortune(Player player, Material material, Location at) {
        IsleOre ore = oreFor(material);
        if (player == null || ore == null || ore == IsleOre.STONE) {
            return 0.0d;
        }
        Location where = at != null ? at : player.getLocation();
        if (inVeins(where)) {
            return mastery.oreFortune(player, ore) + ledger.oreFortune(player, ore) + forge.oreFortune(player)
                    + amethyst.oreFortune(player, ore);
        }
        if (!onIsle(where)) {
            return 0.0d;
        }
        MineDistricts.Band band = districts.band(where);
        double depth = band.fortune() * (1.0d + 0.5d * MineSkills.scale(player, MineSkills.DEPTH_GAUGE));
        return mastery.oreFortune(player, ore)
                + ledger.oreFortune(player, ore)
                + rhythm.oreFortune(player)
                + hearth.oreFortune(player)
                + depth
                + streakFortune(player)
                + forge.oreFortune(player);
    }

    /** Ore-specific Mining Power (mastery + crowns). Only on the isle and in the Amethyst Mine. */
    public double orePower(Player player, Material material) {
        IsleOre ore = oreFor(material);
        if (player == null || ore == null) {
            return 0.0d;
        }
        Location at = player.getLocation();
        if (!onIsle(at) && !inVeins(at)) {
            return 0.0d;
        }
        return mastery.orePower(player, ore) + ledger.orePower(player, ore);
    }

    /** Break-speed-only power (never gates): rhythm, rations, Tempered Head. */
    public double speedPower(Player player, Material material, Location at) {
        if (player == null) {
            return 0.0d;
        }
        Location where = at != null ? at : player.getLocation();
        if (inVeins(where)) {
            return forge.speedPower(player);
        }
        if (!onIsle(where)) {
            return 0.0d;
        }
        return rhythm.speedPower(player) + hearth.speedPower(player) + forge.speedPower(player);
    }

    public int harvestBonus(Player player, Material material, Location at) {
        if (player == null || at == null || !onIsle(at)) {
            return 0;
        }
        return events.richExtra(player, at);
    }

    public String harvestBonusLabel(Player player, Location at) {
        return at != null && events.richDistrict() != null && events.richDistrict().contains(at) ? "Rich Vein" : null;
    }

    /** Vein Siphon vacuum: mastery + contracts count it. Rhythm and finds stay hand-mined. */
    public void noteVacuum(Player player, Material material, Location at) {
        IsleOre ore = oreFor(material);
        if (player == null || ore == null || at == null || at.getWorld() == null
                || wasPlaced(at.getBlock()) || de.aetherion.mining.SharedWorldGuard.isBuildWorld(at.getWorld())) {
            return;
        }
        boolean veins = inVeins(at);
        boolean isle = !veins && onIsle(at);
        mastery.onMined(player, ore, veins || isle ? 2 : 1);
        if (isle) {
            contracts.onMined(player, ore, false, districts.band(at));
        } else if (veins) {
            contracts.onMined(player, ore, true, MineDistricts.Band.SURFACE);
        }
    }

    /** Crystal Find luck from depth (bands, Depth Gauge) and the Gloam Moth dust buff. */
    double depthCrystalMultiplier(Player player, Location at) {
        MineDistricts.Band band = districts.band(at);
        double mult = band.crystalMult();
        if (band.deep()) {
            mult *= 1.0d + 0.1d * MineSkills.scale(player, MineSkills.DEPTH_GAUGE)
                    + 0.05d * MineSkills.scale(player, MineSkills.CAVE_SENSE);
        }
        if (profiles.of(player).gloamUntil > System.currentTimeMillis()) {
            mult *= 1.5d;
        }
        return mult * forge.crystalMultiplier(player);
    }

    private static IsleOre oreFor(Material material) {
        IsleOre ore = IsleOre.fromBlock(material);
        if (ore == null && (material == Material.STONE || material == Material.DEEPSLATE)) {
            return IsleOre.STONE;
        }
        return ore != null ? ore : AmethystMine.oreOf(material);
    }

    /** Rhythm + ration Spread, isle only. SPREAD is a mining-only stat, so a global provider is safe. */
    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability != ItemCapability.SPREAD || !onIsle(player.getLocation())) {
            return 0.0d;
        }
        return rhythm.spread(player) + hearth.spread(player);
    }

    // ------------------------------------------------------------------ forge hooks (Items → Mining)

    public void onForgeStart(Player player, int tier, Location anvil) {
        props.forgeStart(player, tier, anvil);
    }

    public void onForged(Player player, int tier, Location anvil) {
        forge.onForged(player, tier);
        props.forgeDone(player, tier, anvil);
    }

    // ------------------------------------------------------------------ accessors

    public AetherionMining plugin() {
        return plugin;
    }

    public World world() {
        return MineWorld.world(plugin);
    }

    public MineProfiles profiles() {
        return profiles;
    }

    public MineDistricts districts() {
        return districts;
    }

    StrikeRhythm rhythm() {
        return rhythm;
    }

    CrystalFinds crystals() {
        return crystals;
    }

    OreMastery mastery() {
        return mastery;
    }

    AssayLedger ledger() {
        return ledger;
    }

    Hearth hearth() {
        return hearth;
    }

    ForemanContracts contracts() {
        return contracts;
    }

    MineEvents events() {
        return events;
    }

    MineCast cast() {
        return cast;
    }

    MineCompass compass() {
        return compass;
    }

    MineMenus menus() {
        return menus;
    }

    ForgeWorks forge() {
        return forge;
    }

    MineProps props() {
        return props;
    }

    MineCritters critters() {
        return critters;
    }

    MineHazards hazards() {
        return hazards;
    }

    AmethystMine amethyst() {
        return amethyst;
    }

    public MineDev dev() {
        return dev;
    }
}
