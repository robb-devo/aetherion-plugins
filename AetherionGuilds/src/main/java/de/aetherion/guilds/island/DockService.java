package de.aetherion.guilds.island;

import de.aetherion.core.api.AetherServices;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.structure.BuildCosts;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * The Dock and the stage ladder of a personal island (package 3 of the island plan).
 *
 * <p><b>Stages.</b> Every personal island has a stage, 0 to 3 ({@link Stages}). Stage 0 is the tour; each stage
 * after that is reached by shipping that stage's <b>phase contract</b> at the Dock. Reaching a stage raises the
 * Hub and the Island Tier for free (personal islands have no coin upgrade for either any more), lifts the quarry
 * level cap, opens the next parts on the Hub board and plays a short beat.
 *
 * <p><b>Contracts.</b> The Dock shows three kinds: the phase contract (the stage gate), two regular contracts
 * and one daily contract. Goods get in two ways: belts that end on the Dock (they go straight into the open
 * contracts, in that order, never into storage) or "Load from storage" in the Dock menu. A full contract is
 * collected by the airship (on its own while the owner is home, or from the menu). Rewards: coins, Dock Marks
 * (spent only on cosmetics: airship paint and harbour decoration), a second quarry at stage 1 and Quarry Cores
 * later on.
 *
 * <p><b>Ramp-up.</b> On stage 0 every quarry of the island digs {@code stages.ramp-multiplier} times as fast, so
 * the tour never waits on stone. From stage 1 on the quarry digs at its own pace and keeps digging while the
 * owner is away; coming home prints what reached storage meanwhile.
 *
 * <p>Saved in the island's own file, section {@code dock}.
 */
public final class DockService implements Listener, LogisticsService.Export {

    public enum Kind {
        PHASE,
        DAILY,
        REGULAR
    }

    /** One line of a contract: a good and how many. */
    public record Need(Res res, long amount) {
    }

    /** A contract at the Dock. {@code extra}: "quarry:OAK_LOG", "core:2" or null. */
    public static final class Contract {
        final Kind kind;
        final String title;
        final String client;
        final List<Need> needs;
        final long coins;
        final int marks;
        final String extra;
        final Map<Res, Long> loaded = new LinkedHashMap<>();

        Contract(Kind kind, String title, String client, List<Need> needs, long coins, int marks, String extra) {
            this.kind = kind;
            this.title = title;
            this.client = client;
            this.needs = List.copyOf(needs);
            this.coins = coins;
            this.marks = marks;
            this.extra = extra;
        }

        public Kind kind() {
            return kind;
        }

        public String title() {
            return title;
        }

        public String client() {
            return client;
        }

        public List<Need> needs() {
            return needs;
        }

        public long coins() {
            return coins;
        }

        public int marks() {
            return marks;
        }

        public String extra() {
            return extra;
        }

        public long loaded(Res res) {
            return loaded.getOrDefault(res, 0L);
        }

        public long missing(Res res) {
            long need = 0L;
            for (Need line : needs) {
                if (line.res() == res) {
                    need += line.amount();
                }
            }
            return Math.max(0L, need - loaded(res));
        }

        public boolean full() {
            for (Need line : needs) {
                if (missing(line.res()) > 0L) {
                    return false;
                }
            }
            return !needs.isEmpty();
        }

        /** 0..1 over all lines (raw-equivalent). */
        public double fill() {
            long want = 0L;
            long have = 0L;
            for (Need line : needs) {
                want += line.res().rawEquivalent(line.amount());
                have += line.res().rawEquivalent(Math.min(line.amount(), loaded(line.res())));
            }
            return want <= 0L ? 0.0 : Math.min(1.0, have / (double) want);
        }
    }

    private static final class State {
        int stage;
        int marks;
        int pressed;
        int shipped;
        String paint = "white";
        final Set<String> paints = new HashSet<>(Set.of("white"));
        /** Raw-equivalent in storage when the owner last left (-1: nothing to report). */
        long away = -1L;
        long dailyDay;
        boolean dailyDone;
        long nextRegularAt;
        Contract phase;
        Contract daily;
        final List<Contract> regular = new ArrayList<>();
    }

    private static final String AIRSHIP_TAG = "aeg_airship";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final PersonalIslandService personal;
    private final StructureService structures;
    private final MinionService minions;
    private final IslandFiles files;
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();
    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private final Set<Entity> airshipParts = new HashSet<>();
    private BiConsumer<Player, List<String>> say = (player, lines) -> lines.forEach(line -> player.sendMessage("§7" + line));
    private BiConsumer<Player, Integer> stageListener = (player, stage) -> {
    };
    private boolean dirty;
    private long clock;

    public DockService(JavaPlugin plugin, HostService hosts, PersonalIslandService personal, StructureService structures,
                       MinionService minions, IslandFiles files) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.personal = personal;
        this.structures = structures;
        this.minions = minions;
        this.files = files;
        load();
    }

    /** The isle hand speaks the stage beats (falls back to chat). */
    public void attachVoice(BiConsumer<Player, List<String>> say) {
        if (say != null) {
            this.say = say;
        }
    }

    /** Told after a stage is reached (the tour closes its last step on it). */
    public void onStage(BiConsumer<Player, Integer> listener) {
        if (listener != null) {
            this.stageListener = listener;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the stage
    // ------------------------------------------------------------------------------------------------

    /** The island's stage (0..3); guild islands have none (-1). */
    public int stage(IslandHost host) {
        if (host == null || host.isGuild()) {
            return -1;
        }
        return state(host.id()).stage;
    }

    public boolean personalStages(IslandHost host) {
        return host != null && !host.isGuild();
    }

    public int pressed(IslandHost host) {
        return host == null || host.isGuild() ? 0 : state(host.id()).pressed;
    }

    public int marks(IslandHost host) {
        return host == null || host.isGuild() ? 0 : state(host.id()).marks;
    }

    public int shipped(IslandHost host) {
        return host == null || host.isGuild() ? 0 : state(host.id()).shipped;
    }

    /** Why a building can't grow to {@code toLevel} yet on this island (null when the stage allows it). */
    public String upgradeGate(IslandHost host, StructureType type, int toLevel) {
        if (!personalStages(host)) {
            return null;
        }
        if (type == StructureType.WORKSHOP) {
            return "Your Hub grows with your island's stage: ship the phase contract at your Dock.";
        }
        int need = Stages.upgradeStage(type, toLevel);
        int stage = stage(host);
        return stage >= need ? null : "Needs " + Stages.label(need) + " (ship the phase contract at your Dock).";
    }

    /** Why a quarry can't reach {@code toLevel} yet (null when it can). */
    public String quarryGate(PersonalIsland island, int toLevel) {
        if (island == null) {
            return null;
        }
        int stage = state(island.ownerId()).stage;
        int cap = Stages.quarryCap(stage);
        if (toLevel <= cap) {
            return null;
        }
        int next = stage;
        while (next < Stages.MAX && Stages.quarryCap(next) < toLevel) {
            next++;
        }
        return "Quarries stop at Lv" + cap + " on " + Stages.label(stage) + ". " + Stages.label(next)
                + " lifts that: ship the phase contract at your Dock.";
    }

    /** Land is bought from the Grand Hub on (null when allowed). */
    public String landGate(IslandHost host) {
        if (!personalStages(host) || Stages.landOpen(stage(host))) {
            return null;
        }
        return "Land opens on " + Stages.label(3) + ". Ship your phase contracts at the Dock to get there.";
    }

    /** Admin / testing: put an island on a stage (raises Hub and Island Tier like the real thing). */
    public void setStage(Player player, IslandHost host, int stage) {
        if (!personalStages(host)) {
            return;
        }
        State state = state(host.id());
        state.stage = Stages.clamp(stage);
        state.phase = phaseContract(host, state.stage);
        dirty = true;
        applyStage(host, state.stage);
        if (player != null) {
            player.sendMessage("§a" + hosts.title(host) + " is on " + Stages.label(state.stage) + " now.");
        }
    }

    private void applyStage(IslandHost host, int stage) {
        PersonalIsland island = hosts.island(host);
        if (island != null) {
            int tier = Stages.islandTier(stage);
            if (island.islandLevel() < tier) {
                island.setIslandLevel(tier);
                hosts.save(host);
            }
            for (QuarryMinion minion : island.minions()) {
                minion.setBoost(boostFor(stage));
            }
        }
        PlacedStructure hub = structures.hub(host);
        if (hub != null) {
            structures.raiseFree(hub, Stages.hubLevel(stage));
        }
    }

    private int boostFor(int stage) {
        return stage <= 0 ? Math.max(1, plugin.getConfig().getInt("stages.ramp-multiplier", 10)) : 1;
    }

    // ------------------------------------------------------------------------------------------------
    // the hand press (Hub lectern): 128 Cobblestone from storage into 1 Compressed Cobblestone
    // ------------------------------------------------------------------------------------------------

    public static final long PRESS_RAW = Res.UNIT;

    public boolean press(Player player, IslandHost host) {
        if (host == null) {
            return false;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "use the press"));
            return false;
        }
        if (!structures.pay(player, host, List.of(new BuildCosts.Line(BuildCosts.COBBLE, PRESS_RAW)))) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return false;
        }
        structures.giveGoods(player, host, BuildCosts.COMPRESSED, 1L);
        if (!host.isGuild()) {
            state(host.id()).pressed++;
            dirty = true;
        }
        structures.milestone(host, "hand_press");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.9f);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
        PlacedStructure hub = structures.hub(host);
        World world = hosts.world(host);
        if (hub != null && world != null) {
            Location top = new Location(world, hub.x() + 0.5, hub.y() + 2.2, hub.z() + 0.5);
            de.aetherion.core.api.Vfx.puff(top, Particle.BLOCK, 30, Math.max(0.6, 0.6), Material.COBBLESTONE.createBlockData());
            de.aetherion.core.api.Vfx.puff(top, Particle.CLOUD, 8, Math.max(0.4, 0.4), null);
        }
        player.sendMessage("§a✦ Pressed by hand: §f128 Cobblestone §8→ §a1 Compressed Cobblestone §8(in storage)");
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // contracts
    // ------------------------------------------------------------------------------------------------

    /** The contracts on the Dock board right now: phase (if any), daily (if open), regular. */
    public List<Contract> contracts(IslandHost host) {
        if (!personalStages(host)) {
            return List.of();
        }
        State state = state(host.id());
        refresh(host, state);
        List<Contract> out = new ArrayList<>();
        if (state.phase != null) {
            out.add(state.phase);
        }
        if (state.daily != null && !state.dailyDone) {
            out.add(state.daily);
        }
        out.addAll(state.regular);
        return out;
    }

    public Contract phase(IslandHost host) {
        if (!personalStages(host)) {
            return null;
        }
        State state = state(host.id());
        refresh(host, state);
        return state.phase;
    }

    public Contract daily(IslandHost host) {
        if (!personalStages(host)) {
            return null;
        }
        State state = state(host.id());
        refresh(host, state);
        return state.dailyDone ? null : state.daily;
    }

    public List<Contract> regular(IslandHost host) {
        if (!personalStages(host)) {
            return List.of();
        }
        State state = state(host.id());
        refresh(host, state);
        return List.copyOf(state.regular);
    }

    public boolean flying(IslandHost host) {
        return host != null && flying.contains(host.id());
    }

    public PlacedStructure dock(IslandHost host) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == StructureType.DOCK) {
                return structure;
            }
        }
        return null;
    }

    /** New daily each day, regular contracts back after their short pause, the phase contract for the stage. */
    private void refresh(IslandHost host, State state) {
        if (state.phase == null && state.stage < Stages.MAX) {
            state.phase = phaseContract(host, state.stage);
            dirty = true;
        }
        if (dock(host) == null) {
            return; // the board fills once there is a Dock
        }
        long today = LocalDate.now(ZONE).toEpochDay();
        if (state.dailyDay != today) {
            state.dailyDay = today;
            state.dailyDone = false;
            state.daily = generate(host, state, Kind.DAILY);
            dirty = true;
        }
        long now = System.currentTimeMillis();
        while (state.regular.size() < 2 && now >= state.nextRegularAt) {
            Contract fresh = generate(host, state, Kind.REGULAR);
            if (fresh == null) {
                break;
            }
            state.regular.add(fresh);
            dirty = true;
        }
    }

    private Contract phaseContract(IslandHost host, int stage) {
        QuarryType trade = trade(host);
        return switch (Stages.clamp(stage)) {
            case 0 -> new Contract(Kind.PHASE, "First Shipment", "Quarry Broker, Southwood",
                    List.of(new Need(BuildCosts.COMPRESSED, 4L)), 2_000L, 5, "quarry:" + trade.name());
            case 1 -> new Contract(Kind.PHASE, "Werkhof Order", "Quarry Broker, Southwood",
                    List.of(new Need(BuildCosts.COMPRESSED, 32L), new Need(Res.of(trade, Res.Form.RAW), 128L)),
                    10_000L, 15, "core:1");
            case 2 -> new Contract(Kind.PHASE, "Foreman's Order", "Quarry Broker, Southwood",
                    List.of(new Need(Res.of(QuarryType.COBBLESTONE, Res.Form.COMPACTED), 4L),
                            new Need(Res.of(trade, Res.Form.COMPRESSED), 32L)),
                    30_000L, 40, "core:2");
            default -> null;
        };
    }

    /** The trade of the island's starter: its second quarry and its phase goods. */
    public QuarryType trade(IslandHost host) {
        StarterLayout starter = hosts.starter(host);
        if (starter == null) {
            return QuarryType.COAL;
        }
        return switch (starter) {
            case GROVE -> QuarryType.OAK_LOG;
            case TIDE -> QuarryType.COD;
            default -> QuarryType.COAL;
        };
    }

    private Contract generate(IslandHost host, State state, Kind kind) {
        long[] base = {192L, 768L, 3_072L, 12_288L};
        long target = base[Stages.clamp(state.stage)] * (kind == Kind.DAILY ? 3L : 1L);
        target = Math.round(target * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.75));
        List<Res> pool = new ArrayList<>();
        Set<QuarryType> types = EnumSet.of(QuarryType.COBBLESTONE);
        for (QuarryMinion minion : hosts.minions(host)) {
            types.add(minion.quarryType());
        }
        boolean mill = structures.count(host, StructureType.MILL) > 0;
        boolean forge = structures.count(host, StructureType.FORGE) > 0;
        for (QuarryType type : types) {
            pool.add(Res.of(type, Res.Form.RAW));
            if (mill && Res.UNIT <= target) {
                pool.add(Res.of(type, Res.Form.COMPRESSED));
            }
            if (forge && (long) Res.UNIT * Res.UNIT <= target) {
                pool.add(Res.of(type, Res.Form.COMPACTED));
            }
        }
        // two regular contracts never want the same good
        for (Contract other : state.regular) {
            for (Need need : other.needs()) {
                if (pool.size() > 1) {
                    pool.remove(need.res());
                }
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        Res res = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        double rate = Math.max(0.2, res.type().rate());
        long amount = Math.max(1L, Math.round(target * rate / (double) res.rawEquivalent(1L)));
        if (res.form() == Res.Form.RAW && amount > 32L) {
            amount = Math.max(16L, Math.round(amount / 16.0) * 16L);
        }
        double coinRate = 2.0 + 0.5 * Stages.clamp(state.stage);
        long coins = Math.round(target * coinRate / 50.0) * 50L;
        int marks = (int) Math.max(1L, Math.min(12L, 1L + target / 512L));
        String extra = null;
        String title = client(res.type());
        if (kind == Kind.DAILY) {
            coins *= 2L;
            marks = marks * 2 + 2;
            extra = state.stage >= 2 ? "core:1" : null;
            title = "Daily Order";
        }
        return new Contract(kind, title, client(res.type()), List.of(new Need(res, amount)), coins, marks, extra);
    }

    private static String client(QuarryType type) {
        String id = type.name();
        if (type == QuarryType.COBBLESTONE) {
            return "Southwood Masons";
        }
        if (type == QuarryType.COAL || id.startsWith("RAW_")) {
            return "Harbour Smithy";
        }
        if (id.endsWith("_LOG") || id.endsWith("_STEM") || type == QuarryType.BAMBOO_BLOCK) {
            return "Timberwright's Hall";
        }
        if (type == QuarryType.COD) {
            return "Fishmonger's Row";
        }
        if (type == QuarryType.WHEAT || type == QuarryType.CARROT || type == QuarryType.POTATO) {
            return "Southwood Bakery";
        }
        return "Mainland Traders";
    }

    // ------------------------------------------------------------------------------------------------
    // filling: belts (LogisticsService.Export) and "load from storage"
    // ------------------------------------------------------------------------------------------------

    @Override
    public long accept(PlacedStructure dockStructure, Res res, long amount) {
        IslandHost host = dockStructure.host();
        if (!personalStages(host) || amount <= 0L || dockStructure.building()) {
            return 0L;
        }
        long left = amount;
        for (Contract contract : contracts(host)) {
            long take = Math.min(left, contract.missing(res));
            if (take > 0L) {
                contract.loaded.merge(res, take, Long::sum);
                left -= take;
            }
            if (left <= 0L) {
                break;
            }
        }
        if (left < amount) {
            dirty = true;
        }
        return amount - left;
    }

    @Override
    public String line(PlacedStructure dockStructure) {
        IslandHost host = dockStructure.host();
        if (!personalStages(host)) {
            return "§8Contracts: personal islands";
        }
        if (flying(host)) {
            return "§b✈ The airship is in";
        }
        for (Contract contract : contracts(host)) {
            if (contract.full()) {
                return "§a✔ " + contract.title() + " §7ready to ship";
            }
        }
        for (Contract contract : contracts(host)) {
            Need need = contract.needs().get(0);
            return "§7" + contract.title() + ": §f" + GuildFormat.compact(Math.min(need.amount(), contract.loaded(need.res())))
                    + "§8/§f" + GuildFormat.compact(need.amount()) + " " + need.res().color() + need.res().display();
        }
        return "§8No contract open";
    }

    /** Loads what a contract still misses from the island's storage. Returns how much went in. */
    public long load(Player player, IslandHost host, Contract contract) {
        if (contract == null || !hosts.canPlace(player, host)) {
            return 0L;
        }
        long moved = 0L;
        for (Need need : contract.needs()) {
            long missing = contract.missing(need.res());
            long take = Math.min(missing, structures.stock(host, need.res()));
            if (take > 0L) {
                long got = structures.takeStock(host, need.res(), take);
                contract.loaded.merge(need.res(), got, Long::sum);
                moved += got;
            }
        }
        if (moved > 0L) {
            dirty = true;
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1.0f);
        }
        return moved;
    }

    // ------------------------------------------------------------------------------------------------
    // shipping
    // ------------------------------------------------------------------------------------------------

    /** Sends the airship for a full contract: rewards now, the ship comes in and leaves. */
    public boolean ship(Player player, IslandHost host, Contract contract) {
        if (contract == null || !personalStages(host)) {
            return false;
        }
        if (!contract.full()) {
            player.sendMessage("§7That contract isn't full yet.");
            return false;
        }
        PlacedStructure dock = dock(host);
        if (dock == null || dock.building()) {
            player.sendMessage("§7Build a Dock first: the airship ties up there.");
            return false;
        }
        if (flying.contains(host.id())) {
            player.sendMessage("§7The airship is still out. A moment.");
            return false;
        }
        State state = state(host.id());
        switch (contract.kind()) {
            case PHASE -> {
                if (state.phase != contract) {
                    return false;
                }
                state.phase = null;
            }
            case DAILY -> {
                if (state.daily != contract) {
                    return false;
                }
                state.dailyDone = true;
            }
            default -> {
                if (!state.regular.remove(contract)) {
                    return false;
                }
                state.nextRegularAt = System.currentTimeMillis() + 60_000L;
            }
        }
        state.shipped++;
        state.marks += contract.marks();
        dirty = true;
        hosts.refund(player, host, contract.coins());
        String extra = giveExtra(player, contract.extra());
        structures.milestone(host, "first_ship");
        int stageBefore = state.stage;
        if (contract.kind() == Kind.PHASE) {
            state.stage = Stages.clamp(state.stage + 1);
            state.phase = phaseContract(host, state.stage);
            applyStage(host, state.stage);
            structures.milestone(host, "stage_" + state.stage);
        }
        List<String> rewards = new ArrayList<>();
        rewards.add("§6+" + GuildFormat.compact(contract.coins()) + " coins");
        rewards.add("§b+" + contract.marks() + " Dock Marks");
        if (extra != null) {
            rewards.add(extra);
        }
        player.sendMessage("§a✦ " + contract.title() + " shipped §8(" + contract.client() + ")§a: " + String.join("§7, ", rewards));
        int stageAfter = state.stage;
        flying.add(host.id());
        Airship airship = new Airship(player, host, dock, state.paint, () -> {
            flying.remove(host.id());
            if (stageAfter > stageBefore && player.isOnline()) {
                stageBeat(player, host, stageAfter);
            }
        });
        airship.start();
        return true;
    }

    private String giveExtra(Player player, String extra) {
        if (extra == null || extra.isBlank()) {
            return null;
        }
        int colon = extra.indexOf(':');
        String kind = colon < 0 ? extra : extra.substring(0, colon);
        String value = colon < 0 ? "" : extra.substring(colon + 1);
        switch (kind) {
            case "quarry" -> {
                QuarryType type;
                try {
                    type = QuarryType.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    type = QuarryType.COAL;
                }
                hand(player, minions.createQuarryItem(type));
                return "§e+1 " + type.display();
            }
            case "core" -> {
                int amount = 1;
                try {
                    amount = Math.max(1, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
                ItemStack core = AetherServices.items() == null ? null : AetherServices.items().create(QuarryType.CORE_ID);
                if (core == null) {
                    return null;
                }
                core.setAmount(amount);
                hand(player, core);
                return "§d+" + amount + " Quarry Core";
            }
            default -> {
                return null;
            }
        }
    }

    private static void hand(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /** The stage-up beat: title, what it opened, the isle hand's line. */
    private void stageBeat(Player player, IslandHost host, int stage) {
        player.sendTitle("§6✦ " + Stages.label(stage) + " §6✦", "§7" + stripColor(Stages.perks(stage).get(0)), 10, 70, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.9f, 1.2f);
            }
        }, 10L);
        player.sendMessage("§6✦ " + Stages.label(stage) + "§7. Your Hub is the §f"
                + de.aetherion.guilds.logistics.LogisticsService.hubName(Stages.hubLevel(stage)) + " §7now:");
        for (String perk : Stages.perks(stage)) {
            player.sendMessage(" §8• " + perk);
        }
        PlacedStructure hub = structures.hub(host);
        World world = hosts.world(host);
        if (hub != null && world != null) {
            Location top = new Location(world, hub.x() + 0.5, hub.y() + 3.0, hub.z() + 0.5);
            de.aetherion.core.api.Vfx.puff(top, Particle.TOTEM_OF_UNDYING, 60, Math.max(1.0, 1.0), null);
            de.aetherion.core.api.Vfx.puff(top.clone().add(0, 1.5, 0), Particle.END_ROD, 24, Math.max(0.8, 0.8), null);
        }
        List<String> lines = switch (stage) {
            case 1 -> List.of("There she goes. Your first shipment's on its way to the mainland.",
                    "Your quarry has settled in. It keeps digging while you're away.",
                    "And the broker sent you a second quarry. Belt it in next to the first.");
            case 2 -> List.of("The Foreman's Hall! The Forge, the Sorter and the Overflow Gate are yours to unlock.");
            case 3 -> List.of("The Grand Hub. Every Mill and Forge runs twice as fast now, and you can buy land.");
            default -> List.of();
        };
        if (!lines.isEmpty()) {
            say.accept(player, lines);
        }
        stageListener.accept(player, stage);
    }

    private static String stripColor(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    // ------------------------------------------------------------------------------------------------
    // Dock Marks shop (cosmetic only)
    // ------------------------------------------------------------------------------------------------

    /** Airship paints: id, wool, label. White is free. */
    public static final List<String[]> PAINTS = List.of(
            new String[]{"white", "WHITE_WOOL", "Canvas White"},
            new String[]{"crimson", "RED_WOOL", "Harbour Crimson"},
            new String[]{"azure", "LIGHT_BLUE_WOOL", "Sky Azure"},
            new String[]{"emerald", "LIME_WOOL", "Emerald"},
            new String[]{"gold", "YELLOW_WOOL", "Broker's Gold"},
            new String[]{"night", "BLACK_WOOL", "Night Sail"});

    public static final int PAINT_PRICE = 8;

    /** Decoration for the island: label, material, amount, price in marks. */
    public record Decor(String label, Material material, int amount, int price) {
    }

    public static final List<Decor> DECOR = List.of(
            new Decor("Harbour Lanterns", Material.LANTERN, 8, 3),
            new Decor("Flower Pots", Material.FLOWER_POT, 8, 2),
            new Decor("Dock Banner", Material.RED_BANNER, 1, 4),
            new Decor("Ship's Bell", Material.BELL, 1, 6),
            new Decor("Celebration Rockets", Material.FIREWORK_ROCKET, 16, 5));

    public String paint(IslandHost host) {
        return personalStages(host) ? state(host.id()).paint : "white";
    }

    public boolean ownsPaint(IslandHost host, String id) {
        return personalStages(host) && state(host.id()).paints.contains(id);
    }

    /** Buys (if needed) and picks an airship paint. */
    public boolean paint(Player player, IslandHost host, String id) {
        if (!personalStages(host)) {
            return false;
        }
        State state = state(host.id());
        if (!state.paints.contains(id)) {
            if (state.marks < PAINT_PRICE) {
                player.sendMessage("§cNeeds " + PAINT_PRICE + " Dock Marks (you have " + state.marks + ").");
                return false;
            }
            state.marks -= PAINT_PRICE;
            state.paints.add(id);
        }
        state.paint = id;
        dirty = true;
        player.playSound(player.getLocation(), Sound.ITEM_DYE_USE, 1f, 1f);
        return true;
    }

    public boolean buy(Player player, IslandHost host, Decor decor) {
        if (!personalStages(host) || decor == null) {
            return false;
        }
        State state = state(host.id());
        if (state.marks < decor.price()) {
            player.sendMessage("§cNeeds " + decor.price() + " Dock Marks (you have " + state.marks + ").");
            return false;
        }
        state.marks -= decor.price();
        dirty = true;
        hand(player, new ItemStack(decor.material(), decor.amount()));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        player.sendMessage("§a+" + decor.amount() + " " + decor.label() + " §8(" + decor.price() + " Dock Marks)");
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // tick: quarry ramp-up, the ship comes on its own, "while you were away"
    // ------------------------------------------------------------------------------------------------

    /** Every second. */
    public void tick() {
        clock++;
        if (clock % 5 == 0) {
            for (PersonalIsland island : personal.all()) {
                int boost = boostFor(state(island.ownerId()).stage);
                for (QuarryMinion minion : island.minions()) {
                    if (minion.boost() != boost) {
                        minion.setBoost(boost);
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            IslandHost host = hosts.at(player.getLocation());
            if (host == null || host.isGuild() || !host.id().equals(player.getUniqueId())) {
                continue;
            }
            Long joined = joinedAt.get(player.getUniqueId());
            if (joined != null && now - joined > 10_000L) {
                joinedAt.remove(player.getUniqueId());
                welcomeBack(player, host);
            }
            if (clock % 3 == 0 && !flying.contains(host.id())) {
                for (Contract contract : contracts(host)) {
                    if (contract.full() && dock(host) != null) {
                        ship(player, host, contract);
                        break;
                    }
                }
            }
        }
        if (clock % 60 == 0) {
            saveIfDirty();
        }
    }

    private void welcomeBack(Player player, IslandHost host) {
        State state = state(host.id());
        if (state.away < 0L) {
            return;
        }
        long before = state.away;
        state.away = -1L;
        dirty = true;
        if (state.stage < 1) {
            return;
        }
        long gained = storedRaw(host) - before;
        if (gained > 0L) {
            player.sendMessage("§7While you were away: §a+" + GuildFormat.compact(gained) + " §7goods reached your storage.");
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
        }
    }

    /** Everything in the island's storage, raw-equivalent. */
    private long storedRaw(IslandHost host) {
        long total = 0L;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type().role() == StructureType.Role.SINK) {
                total += PlacedStructure.total(structure.store());
            }
        }
        return total;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        joinedAt.remove(id);
        if (personal.byOwner(id) == null) {
            return;
        }
        state(id).away = storedRaw(IslandHost.personal(id));
        dirty = true;
    }

    // ------------------------------------------------------------------------------------------------
    // the airship: block displays that fly in over the Dock, take the crates and fly off
    // ------------------------------------------------------------------------------------------------

    private final class Airship {
        private final Player player;
        private final World world;
        private final Location hover;
        private final Location from;
        private final Location to;
        private final Location quay;
        private final BlockData paint;
        private final Runnable done;
        private final List<BlockDisplay> parts = new ArrayList<>();
        private final List<BlockDisplay> crates = new ArrayList<>();
        private int tick;

        Airship(Player player, IslandHost host, PlacedStructure dock, String paintId, Runnable done) {
            this.player = player;
            this.world = hosts.world(host);
            this.quay = new Location(world, dock.x() + 0.5, dock.y() + 1.0, dock.z() + 0.5);
            this.hover = quay.clone().add(0, 6.5, 0);
            this.from = hover.clone().add(-34, 16, -6);
            this.to = hover.clone().add(36, 22, 8);
            Material wool = Material.WHITE_WOOL;
            for (String[] paint : PAINTS) {
                if (paint[0].equals(paintId)) {
                    wool = Material.matchMaterial(paint[1]) == null ? Material.WHITE_WOOL : Material.matchMaterial(paint[1]);
                }
            }
            this.paint = wool.createBlockData();
            this.done = done;
        }

        void start() {
            if (world == null) {
                done.run();
                return;
            }
            // the envelope, its ribs and caps
            part(paint, 0, 3.3f, 0, 4.4f, 1.9f, 1.9f);
            part(paint, -2.45f, 3.3f, 0, 0.6f, 1.3f, 1.3f);
            part(paint, 2.45f, 3.3f, 0, 0.6f, 1.3f, 1.3f);
            for (float x = -1.4f; x <= 1.5f; x += 1.4f) {
                part(Material.DARK_OAK_PLANKS.createBlockData(), x, 3.3f, 0, 0.12f, 1.96f, 1.96f);
            }
            part(Material.DARK_OAK_PLANKS.createBlockData(), 2.85f, 3.3f, 0, 0.3f, 0.9f, 0.08f);
            // the gondola, its rail and ropes
            part(Material.SPRUCE_PLANKS.createBlockData(), 0, 1.25f, 0, 3.0f, 0.5f, 1.1f);
            part(Material.STRIPPED_DARK_OAK_LOG.createBlockData(), 0, 1.58f, 0.52f, 3.0f, 0.14f, 0.08f);
            part(Material.STRIPPED_DARK_OAK_LOG.createBlockData(), 0, 1.58f, -0.52f, 3.0f, 0.14f, 0.08f);
            part(Bukkit.createBlockData("minecraft:chain[axis=y]"), -1.1f, 1.95f, 0, 1f, 0.9f, 1f);
            part(Bukkit.createBlockData("minecraft:chain[axis=y]"), 1.1f, 1.95f, 0, 1f, 0.9f, 1f);
            part(Bukkit.createBlockData("minecraft:lantern[hanging=true]"), 1.6f, 0.95f, 0, 0.5f, 0.5f, 0.5f);
            // the propeller at the stern
            part(Material.DARK_OAK_PLANKS.createBlockData(), -1.75f, 1.3f, 0, 0.1f, 0.95f, 0.16f);
            place(from);
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (step()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 2L);
        }

        private void part(BlockData block, float cx, float cy, float cz, float sx, float sy, float sz) {
            Location at = from.clone();
            BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(block);
                d.setTransformation(new Transformation(new Vector3f(cx - sx / 2f, cy - sy / 2f, cz - sz / 2f),
                        new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf()));
                d.setPersistent(false);
                d.setViewRange(1.6f);
                d.setTeleportDuration(2);
                d.addScoreboardTag(AIRSHIP_TAG);
            });
            parts.add(display);
            airshipParts.add(display);
        }

        private void place(Location at) {
            for (BlockDisplay display : parts) {
                if (display.isValid()) {
                    display.teleport(at);
                }
            }
        }

        /** One step (every 2 ticks). True when the flight is over. */
        private boolean step() {
            tick += 2;
            if (tick <= 70) {
                double t = ease(tick / 70.0, true);
                place(lerp(from, hover, t));
                if (tick % 8 == 0) {
                    puff(lerp(from, hover, t).add(-2.2, 1.3, 0));
                }
                if (tick == 70) {
                    world.playSound(hover, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1f, 1.3f);
                    world.playSound(hover, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.8f, 0.8f);
                    lowerCrates();
                }
                return false;
            }
            if (tick <= 120) {
                // crates rise from the quay into the gondola
                double t = Math.min(1.0, (tick - 70) / 36.0);
                for (int i = 0; i < crates.size(); i++) {
                    BlockDisplay crate = crates.get(i);
                    if (crate.isValid()) {
                        crate.teleport(quay.clone().add(-0.6 + i * 0.6, t * 5.5, 0));
                    }
                }
                if (tick == 80 || tick == 96) {
                    world.playSound(quay, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.8f, 0.8f);
                }
                if (tick == 108) {
                    world.playSound(hover, Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.9f, 0.9f);
                    for (BlockDisplay crate : crates) {
                        crate.remove();
                    }
                    crates.clear();
                }
                return false;
            }
            if (tick <= 200) {
                double t = ease((tick - 120) / 80.0, false);
                place(lerp(hover, to, t));
                if (tick % 8 == 0) {
                    puff(lerp(hover, to, t).add(-2.2, 1.3, 0));
                }
                if (tick == 122) {
                    world.playSound(hover, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 0.6f, 0.6f);
                }
                return false;
            }
            for (BlockDisplay display : parts) {
                display.remove();
                airshipParts.remove(display);
            }
            parts.clear();
            done.run();
            return true;
        }

        private void lowerCrates() {
            Material[] looks = {Material.BARREL, Material.SPRUCE_PLANKS, Material.STRIPPED_OAK_WOOD};
            for (int i = 0; i < 3; i++) {
                Material look = looks[i];
                BlockDisplay crate = world.spawn(quay.clone().add(-0.6 + i * 0.6, 0, 0), BlockDisplay.class, d -> {
                    d.setBlock(look.createBlockData());
                    d.setTransformation(new Transformation(new Vector3f(-0.25f, 0f, -0.25f), new Quaternionf(),
                            new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
                    d.setPersistent(false);
                    d.setTeleportDuration(2);
                    d.addScoreboardTag(AIRSHIP_TAG);
                });
                crates.add(crate);
                airshipParts.add(crate);
            }
        }

        private void puff(Location at) {
            world.spawnParticle(Particle.CLOUD, at, 2, 0.15, 0.15, 0.15, 0.01);
        }

        private static double ease(double t, boolean out) {
            t = Math.max(0.0, Math.min(1.0, t));
            return out ? 1 - Math.pow(1 - t, 3) : t * t * t;
        }

        private static Location lerp(Location a, Location b, double t) {
            return new Location(a.getWorld(), a.getX() + (b.getX() - a.getX()) * t, a.getY() + (b.getY() - a.getY()) * t,
                    a.getZ() + (b.getZ() - a.getZ()) * t);
        }
    }

    /** Shutdown: no airship stays behind. */
    public void clearAirships() {
        for (Entity entity : airshipParts) {
            entity.remove();
        }
        airshipParts.clear();
        flying.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // saving (into the island's own file, section "dock")
    // ------------------------------------------------------------------------------------------------

    private State state(UUID owner) {
        return states.computeIfAbsent(owner, id -> {
            State fresh = new State();
            IslandHost host = IslandHost.personal(id);
            // an island that already runs (no wipe): its stage from what stands, never lower than the Hub tier
            PlacedStructure hub = structures.hub(host);
            if (hub != null && hub.level() >= 2) {
                fresh.stage = Stages.clamp(hub.level());
            } else if (structures.count(host, StructureType.MILL) > 0 && structures.count(host, StructureType.DOCK) > 0) {
                fresh.stage = 1;
            }
            dirty = true;
            return fresh;
        });
    }

    private void load() {
        for (String key : files.keys()) {
            IslandHost host = IslandHost.parse(key);
            if (host == null || host.isGuild()) {
                continue;
            }
            YamlConfiguration doc = files.doc(key);
            ConfigurationSection section = doc.getConfigurationSection("dock");
            if (section == null) {
                continue;
            }
            State state = new State();
            state.stage = Stages.clamp(section.getInt("stage", 0));
            state.marks = Math.max(0, section.getInt("marks", 0));
            state.pressed = Math.max(0, section.getInt("pressed", 0));
            state.shipped = Math.max(0, section.getInt("shipped", 0));
            state.paint = section.getString("paint", "white");
            state.paints.addAll(section.getStringList("paints"));
            state.away = section.getLong("away", -1L);
            state.dailyDay = section.getLong("daily-day", 0L);
            state.dailyDone = section.getBoolean("daily-done", false);
            state.nextRegularAt = section.getLong("next-regular", 0L);
            state.phase = phaseContract(host, state.stage);
            if (state.phase != null) {
                readLoaded(section.getStringList("phase-loaded"), state.phase);
            }
            state.daily = readContract(section.getConfigurationSection("daily"), Kind.DAILY);
            ConfigurationSection regular = section.getConfigurationSection("regular");
            if (regular != null) {
                for (String id : regular.getKeys(false)) {
                    Contract contract = readContract(regular.getConfigurationSection(id), Kind.REGULAR);
                    if (contract != null && state.regular.size() < 2) {
                        state.regular.add(contract);
                    }
                }
            }
            states.put(host.id(), state);
        }
    }

    private static Contract readContract(ConfigurationSection section, Kind kind) {
        if (section == null) {
            return null;
        }
        List<Need> needs = new ArrayList<>();
        for (String line : section.getStringList("needs")) {
            int eq = line.lastIndexOf('=');
            Res res = eq < 0 ? null : Res.parse(line.substring(0, eq));
            if (res == null) {
                continue;
            }
            try {
                needs.add(new Need(res, Math.max(1L, Long.parseLong(line.substring(eq + 1).trim()))));
            } catch (NumberFormatException ignored) {
            }
        }
        if (needs.isEmpty()) {
            return null;
        }
        Contract contract = new Contract(kind, section.getString("title", "Order"), section.getString("client", "Mainland Traders"),
                needs, section.getLong("coins", 0L), section.getInt("marks", 1), section.getString("extra"));
        readLoaded(section.getStringList("loaded"), contract);
        return contract;
    }

    private static void readLoaded(List<String> lines, Contract contract) {
        for (String line : lines) {
            int eq = line.lastIndexOf('=');
            Res res = eq < 0 ? null : Res.parse(line.substring(0, eq));
            if (res == null) {
                continue;
            }
            try {
                long amount = Long.parseLong(line.substring(eq + 1).trim());
                if (amount > 0L) {
                    contract.loaded.put(res, amount);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static List<String> lines(Map<Res, Long> map) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Res, Long> entry : map.entrySet()) {
            if (entry.getValue() > 0L) {
                out.add(entry.getKey().key() + "=" + entry.getValue());
            }
        }
        return out;
    }

    private static void writeContract(ConfigurationSection section, Contract contract) {
        section.set("title", contract.title);
        section.set("client", contract.client);
        List<String> needs = new ArrayList<>();
        for (Need need : contract.needs) {
            needs.add(need.res().key() + "=" + need.amount());
        }
        section.set("needs", needs);
        section.set("coins", contract.coins);
        section.set("marks", contract.marks);
        section.set("extra", contract.extra);
        section.set("loaded", lines(contract.loaded));
    }

    public void forget(UUID owner) {
        states.remove(owner);
        flying.remove(owner);
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            String key = IslandHost.personal(entry.getKey()).key();
            if (personal.byOwner(entry.getKey()) == null) {
                continue;
            }
            State state = entry.getValue();
            ConfigurationSection section = files.replace(key, "dock");
            section.set("stage", state.stage);
            section.set("marks", state.marks);
            section.set("pressed", state.pressed);
            section.set("shipped", state.shipped);
            section.set("paint", state.paint);
            section.set("paints", new ArrayList<>(state.paints));
            section.set("away", state.away);
            section.set("daily-day", state.dailyDay);
            section.set("daily-done", state.dailyDone);
            section.set("next-regular", state.nextRegularAt);
            if (state.phase != null) {
                section.set("phase-loaded", lines(state.phase.loaded));
            }
            if (state.daily != null) {
                writeContract(section.createSection("daily"), state.daily);
            }
            ConfigurationSection regular = section.createSection("regular");
            for (int i = 0; i < state.regular.size(); i++) {
                writeContract(regular.createSection("r" + i), state.regular.get(i));
            }
        }
        files.flush();
        dirty = false;
    }
}
