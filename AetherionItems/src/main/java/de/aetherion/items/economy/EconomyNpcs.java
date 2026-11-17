package de.aetherion.items.economy;

import de.aetherion.core.npc.CastTroupe;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.world.NpcRemoverListener;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The traders as real people: Trader, Gear Trader, Silas Markup, the Bazaar and the Auction House stand on a
 * Core {@link CastTroupe} (player models with their own painted skins) instead of being villagers.
 *
 * <p>The old traders were persistent villagers with nothing written down but the entity itself. Each one is
 * moved over the first time the server sees it: its spot goes into {@code economy-npcs.yml}, the person takes
 * the spot, the villager leaves. From then on the file is the truth. On a server without FancyNpcs nothing
 * changes: the villagers stay and the services keep spawning them as before.
 */
public final class EconomyNpcs implements Listener {

    public enum Kind {
        TRADER("Trader", "§6Trader", "shop_trader", Material.EMERALD, 0x7a2a3a, Villager.Profession.NITWIT,
                CastTroupe.Habit.work(8, 16)),
        GEAR("Gear Trader", "§6Gear Trader", "shop_gear", Material.IRON_SWORD, 0x5a3a22,
                Villager.Profession.WEAPONSMITH, CastTroupe.Habit.work(7, 14, Material.IRON_SWORD, Material.IRON_HELMET)),
        FENCE("Silas Markup", "§6Silas Markup", "shop_fence", Material.GOLD_NUGGET, 0x2a2e30,
                Villager.Profession.LIBRARIAN, CastTroupe.Habit.watch(9, 18)),
        BAZAAR("Bazaar", "§6Bazaar", "shop_bazaar", Material.WRITABLE_BOOK, 0x2a8a80, Villager.Profession.FARMER,
                CastTroupe.Habit.work(8, 16, Material.FEATHER)),
        AUCTION("Auction House", "§eAuction House", "shop_auction", Material.CLOCK, 0x2a2a34,
                Villager.Profession.WEAPONSMITH, CastTroupe.Habit.work(7, 15));

        private final String title;
        private final String label;
        private final String skin;
        private final Material hand;
        private final int outfit;
        private final Villager.Profession standIn;
        private final CastTroupe.Habit habit;

        Kind(String title, String label, String skin, Material hand, int outfit, Villager.Profession standIn,
             CastTroupe.Habit habit) {
            this.title = title;
            this.label = label;
            this.skin = skin;
            this.hand = hand;
            this.outfit = outfit;
            this.standIn = standIn;
            this.habit = habit;
        }

        public String title() {
            return title;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equalsIgnoreCase(id)) {
                    return kind;
                }
            }
            return null;
        }

        CastTroupe.Look look() {
            return CastTroupe.Look.of(title, skin, hand, Color.fromRGB(outfit))
                    .stand(standIn, Villager.Type.PLAINS)
                    .labelled(label);
        }
    }

    private record Seat(Kind kind, Location at) {
    }

    private final AetherionItems plugin;
    private final File file;
    private final Map<String, Seat> seats = new LinkedHashMap<>();
    /** Saved seats whose world is not loaded (yet): kept as they are and written back untouched. */
    private final Map<String, Map<String, Object>> parked = new LinkedHashMap<>();
    private final Map<Kind, Consumer<Player>> handlers = new EnumMap<>(Kind.class);
    private CastTroupe troupe;

    public EconomyNpcs(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy-npcs.yml");
    }

    /** What a click on a trader of this kind opens. */
    public void handler(Kind kind, Consumer<Player> open) {
        handlers.put(kind, open);
    }

    public void start() {
        troupe = CastTroupe.open(plugin, "shop", this::clicked);
        load();
        for (Map.Entry<String, Seat> entry : seats.entrySet()) {
            show(entry.getKey(), entry.getValue());
        }
        for (World world : Bukkit.getWorlds()) {
            adopt(world.getEntities());
        }
    }

    public void shutdown() {
        if (troupe != null) {
            troupe.close();
            troupe = null;
        }
    }

    /** True where the traders are people. False = no FancyNpcs here: the villagers stay as they were. */
    public boolean playerModels() {
        return troupe != null && troupe.playerModels();
    }

    /** A new trader of this kind at {@code at}; one of the same kind standing on that spot is replaced. */
    public void place(Kind kind, Location at) {
        if (troupe == null || at == null || at.getWorld() == null) {
            return;
        }
        String old = nearest(kind, at, 1.5d);
        if (old != null) {
            drop(old);
        }
        String key = freeKey(kind);
        Seat seat = new Seat(kind, at.clone());
        seats.put(key, seat);
        save();
        show(key, seat);
    }

    /** Sends the nearest trader of this kind within {@code radius} away. False when there is none. */
    public boolean removeNearest(Kind kind, Location near, double radius) {
        String key = nearest(kind, near, radius);
        if (key == null) {
            return false;
        }
        drop(key);
        save();
        return true;
    }

    // ------------------------------------------------------------------ moving the villagers over

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> loaded = List.copyOf(event.getEntities());
        if (loaded.stream().noneMatch(entity -> entity instanceof Villager)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> adopt(loaded));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (parked.isEmpty() || troupe == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String key : List.copyOf(parked.keySet())) {
                Seat seat = seat(parked.get(key));
                if (seat != null) {
                    parked.remove(key);
                    seats.put(key, seat);
                    show(key, seat);
                }
            }
        });
    }

    /** Every trader villager among {@code entities} hands its spot to a person and leaves. */
    private void adopt(Collection<Entity> entities) {
        if (!playerModels()) {
            return;
        }
        boolean changed = false;
        for (Entity entity : entities) {
            if (!(entity instanceof Villager) || !entity.isValid()) {
                continue;
            }
            Kind kind = kindOf(entity);
            if (kind == null) {
                continue;
            }
            Location at = entity.getLocation();
            if (nearest(kind, at, 1.5d) == null) {
                String key = freeKey(kind);
                Seat seat = new Seat(kind, at);
                seats.put(key, seat);
                show(key, seat);
                changed = true;
                plugin.getLogger().info("Traders: " + kind.title() + " at " + at.getBlockX() + " " + at.getBlockY()
                        + " " + at.getBlockZ() + " (" + at.getWorld().getName() + ") is a person now.");
            }
            entity.remove();
        }
        if (changed) {
            save();
        }
    }

    private static Kind kindOf(Entity entity) {
        String trader = entity.getPersistentDataContainer().get(ItemKeys.traderNpc(), PersistentDataType.STRING);
        if ("trader".equals(trader)) {
            return Kind.TRADER;
        }
        if (GearTraderService.NPC_ID.equals(trader)) {
            return Kind.GEAR;
        }
        if (FenceService.NPC_ID.equals(trader)) {
            return Kind.FENCE;
        }
        if (entity.getPersistentDataContainer().has(ItemKeys.bazaarNpc(), PersistentDataType.STRING)) {
            return Kind.BAZAAR;
        }
        if (entity.getPersistentDataContainer().has(ItemKeys.auctionNpc(), PersistentDataType.STRING)) {
            return Kind.AUCTION;
        }
        return null;
    }

    // ------------------------------------------------------------------ seats

    private void show(String key, Seat seat) {
        if (troupe == null) {
            return;
        }
        troupe.spawn(key, seat.at(), seat.kind().look());
        troupe.habit(key, seat.kind().habit);
    }

    private void drop(String key) {
        seats.remove(key);
        if (troupe != null) {
            troupe.remove(key);
        }
    }

    private String nearest(Kind kind, Location near, double radius) {
        String best = null;
        double bestSquared = radius * radius;
        for (Map.Entry<String, Seat> entry : seats.entrySet()) {
            Seat seat = entry.getValue();
            if (seat.kind() != kind || near.getWorld() == null || !near.getWorld().equals(seat.at().getWorld())) {
                continue;
            }
            double squared = seat.at().distanceSquared(near);
            if (squared <= bestSquared) {
                bestSquared = squared;
                best = entry.getKey();
            }
        }
        return best;
    }

    private String freeKey(Kind kind) {
        for (int n = 1; ; n++) {
            String key = kind.id() + "_" + n;
            if (!seats.containsKey(key) && !parked.containsKey(key)) {
                return key;
            }
        }
    }

    private void clicked(Player player, String key) {
        Seat seat = seats.get(key);
        if (seat == null) {
            return;
        }
        if (NpcRemoverListener.isRemover(player.getInventory().getItemInMainHand())) {
            if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
                player.sendMessage("§cDEV only.");
                return;
            }
            drop(key);
            save();
            player.sendMessage("§eDespawned §f" + seat.kind().title() + "§e. Place the anchor again to move them.");
            return;
        }
        Consumer<Player> open = handlers.get(seat.kind());
        if (open != null) {
            open.accept(player);
        }
    }

    // ------------------------------------------------------------------ file

    private void load() {
        seats.clear();
        parked.clear();
        if (!file.isFile()) {
            return;
        }
        ConfigurationSection saved = YamlConfiguration.loadConfiguration(file).getConfigurationSection("seats");
        if (saved == null) {
            return;
        }
        for (String key : saved.getKeys(false)) {
            ConfigurationSection section = saved.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Map<String, Object> raw = section.getValues(false);
            Seat seat = seat(raw);
            if (seat != null) {
                seats.put(key, seat);
            } else if (Kind.byId(String.valueOf(raw.get("kind"))) != null) {
                parked.put(key, raw);
            }
        }
    }

    private static Seat seat(Map<String, Object> raw) {
        Kind kind = Kind.byId(String.valueOf(raw.get("kind")));
        World world = Bukkit.getWorld(String.valueOf(raw.get("world")));
        if (kind == null || world == null) {
            return null;
        }
        return new Seat(kind, new Location(world, number(raw.get("x")), number(raw.get("y")), number(raw.get("z")),
                (float) number(raw.get("yaw")), 0.0f));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> keys = new ArrayList<>(parked.keySet());
        for (String key : keys) {
            out.createSection("seats." + key, parked.get(key));
        }
        for (Map.Entry<String, Seat> entry : seats.entrySet()) {
            Location at = entry.getValue().at();
            String path = "seats." + entry.getKey();
            out.set(path + ".kind", entry.getValue().kind().id());
            out.set(path + ".world", at.getWorld() == null ? "world" : at.getWorld().getName());
            out.set(path + ".x", at.getX());
            out.set(path + ".y", at.getY());
            out.set(path + ".z", at.getZ());
            out.set(path + ".yaw", at.getYaw());
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("no data folder");
            }
            de.aetherion.core.persist.AtomicYaml.save(out, file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save economy-npcs.yml: " + exception.getMessage());
        }
    }
}
