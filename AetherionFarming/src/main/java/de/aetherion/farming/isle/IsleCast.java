package de.aetherion.farming.isle;

import de.aetherion.core.npc.CastTroupe;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns and runs the Eldervale cast. Placements live in {@code isle-cast.yml}. The cast are real people:
 * player models with their own painted skins on a Core {@link CastTroupe}, each busy with their own work
 * until somebody walks up. Nothing is saved with the world, so restarts and reloads never stack
 * duplicates. Right-click opens the NPC's board; walking past gets you a line.
 */
public final class IsleCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private final FarmIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<IsleRole, Location> placed = new EnumMap<>(IsleRole.class);
    private CastTroupe troupe;
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private final TalkDesk wardenDesk;
    private BukkitTask task;

    IsleCast(FarmIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "isle_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "isle_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "isle-cast.yml");
        this.wardenDesk = wardenDesk();
        load();
    }

    /**
     * Elsie at the Harvest Hall: the isle's front desk in the talk bubble — what the isle is, the
     * harvest rhythm, where to sell and eat, how to get home; her full board stays one chip away.
     */
    private TalkDesk wardenDesk() {
        IsleRole warden = IsleRole.WARDEN;
        return new TalkDesk(isle.plugin(), "farm_isle_warden", warden.display(), warden.color(), "isle_hand",
                () -> liveLocation(warden), () -> liveHologram(warden),
                player -> FieldLesson.t(player, "Back on the rows, {player}? Ask away — or keep the rhythm going.",
                        "Wieder auf den Feldern, {player}? Frag ruhig — oder halt den Rhythmus am Laufen."),
                List.of(
                        TalkDesk.Topic.of("What is this place?", net.kyori.adventure.text.format.NamedTextColor.GREEN,
                                "What's on Eldervale?",
                                "Fifteen named plots, one big harvest. Find them all — 2,500 coins and a title.",
                                "Walk into one and it names itself. Arrow's on the nearest you haven't seen.")
                                .then(this::pointAtUnknownPlot),
                        TalkDesk.Topic.of("Harvest rhythm?", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE,
                                "How does the rhythm work?",
                                "Harvest without stopping and the field sings. Stand around and it drains.",
                                "Twenty, fifty, a hundred in a row — every step adds crop Fortune."),
                        TalkDesk.Topic.of("Shops & pantry?", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                                "Where do I sell and eat?",
                                "§6Hattie§f's orders pay better than the trader. §eBram§f bakes crops into farming food.",
                                "§bGus§f keeps your Crop Mastery ledger. That one pays forever."),
                        TalkDesk.Topic.of("How do I leave?", net.kyori.adventure.text.format.NamedTextColor.AQUA,
                                "How do I get home?",
                                "§e/capital§f takes you back to town.",
                                "The fields will keep. They're patient. Unlike the crows.")
                ),
                "Board…", player -> isle.menus().openWarden(player),
                "Mind the crows. They mind you.");
    }

    private void pointAtUnknownPlot(Player player) {
        IslePlots.Plot next = isle.menus().nearestUnknown(player, isle.profiles().of(player));
        if (next != null) {
            isle.compass().guide(player, "an undiscovered area", next.center(player.getWorld()));
        }
    }

    /** Where the NPC actually stands right now (it may be a few steps into its round), else its placed spot. */
    private Location liveLocation(IsleRole role) {
        Location at = troupe == null ? null : troupe.location(role.id());
        return at != null ? at : location(role);
    }

    private Entity liveHologram(IsleRole role) {
        return troupe == null ? null : troupe.label(role.id());
    }

    /** Body, name plate and stand-in dress of one role. */
    private static CastTroupe.Look look(IsleRole role) {
        String skin;
        Color outfit;
        switch (role) {
            case WARDEN -> {
                skin = "farm_elsie";
                outfit = Color.fromRGB(0x5a7a4a);
            }
            case CLERK -> {
                skin = "farm_hattie";
                outfit = Color.fromRGB(0x8a5a3a);
            }
            case BAKER -> {
                skin = "farm_bram";
                outfit = Color.fromRGB(0xf6f3ea);
            }
            case GRANARY -> {
                skin = "farm_gus";
                outfit = Color.fromRGB(0x5a4a2e);
            }
            case BEEKEEPER -> {
                skin = "farm_wren";
                outfit = Color.fromRGB(0xf0e6c0);
            }
            case PIP -> {
                skin = "farm_pip";
                outfit = Color.fromRGB(0x4a6a8a);
            }
            case MARTA -> {
                skin = "farm_marta";
                outfit = Color.fromRGB(0x7a3a4a);
            }
            default -> {
                skin = "farm_tobias";
                outfit = Color.fromRGB(0x6a4a2e);
            }
        }
        return CastTroupe.Look.of(role.display(), skin, role.icon(), outfit)
                .stand(role.profession(), role.villagerType())
                .labelled(role.color() + "§l" + role.display() + "\n§7" + role.title()
                        + (role.flavorOnly() ? "" : "\n§e▸ §7" + role.role()));
    }

    /** What each of them is busy with while nobody is asking. */
    private static CastTroupe.Habit habit(IsleRole role) {
        return switch (role) {
            case WARDEN -> CastTroupe.Habit.work(8, 16);
            case CLERK, GRANARY -> CastTroupe.Habit.work(7, 14, Material.FEATHER);
            case BAKER -> CastTroupe.Habit.work(6, 12, Material.WOODEN_SHOVEL, Material.BREAD).strolling(2.5d);
            case BEEKEEPER -> CastTroupe.Habit.work(9, 18, Material.GLASS_BOTTLE).strolling(3.0d);
            case PIP -> CastTroupe.Habit.work(4, 9).strolling(4.0d);
            case MARTA -> CastTroupe.Habit.work(6, 12, Material.WHEAT).strolling(3.0d);
            default -> CastTroupe.Habit.work(6, 13, Material.STICK, Material.SHEARS);
        };
    }

    void start() {
        purgeStrays();
        troupe = CastTroupe.open(isle.plugin(), "farm", this::clicked);
        ensureAll();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        wardenDesk.shutdown();
        if (troupe != null) {
            troupe.close();
            troupe = null;
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(IsleRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(IsleRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    /** Configured preset spot for {@code role} (config {@code isle-cast.presets.<role>}). */
    public Location preset(IsleRole role) {
        ConfigurationSection section = isle.plugin().getConfig().getConfigurationSection("isle-cast.presets." + role.id());
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0d), 0.0f);
    }

    /** Placed spot, else preset — where the wayfinder should point for this NPC. */
    public Location whereabouts(IsleRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(IsleRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(IsleRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (isle-cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String remove(IsleRole role) {
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    // ------------------------------------------------------------------ anchors

    public ItemStack anchor(IsleRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Eldervale NPC anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8Presets: DEV → Farming Island → NPCs"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private IsleRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return IsleRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        IsleRole role = anchorRole(player.getInventory().getItemInMainHand());
        if (role == null || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("aetherion.dev") && !player.isOp()) {
            return;
        }
        if (player.isSneaking()) {
            player.sendMessage(remove(role));
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getRelative(BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
        at.setYaw(player.getLocation().getYaw() + 180.0f);
        player.sendMessage(place(role, at));
        player.playSound(at, Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.1f);
    }

    // ------------------------------------------------------------------ entities

    private void ensureAll() {
        for (IsleRole role : placed.keySet()) {
            ensure(role);
        }
    }

    private void ensure(IsleRole role) {
        Location at = placed.get(role);
        if (at == null || at.getWorld() == null || troupe == null) {
            return;
        }
        troupe.spawn(role.id(), at, look(role));
        troupe.habit(role.id(), habit(role));
    }

    private void despawn(IsleRole role) {
        if (troupe != null) {
            troupe.remove(role.id());
        }
    }

    /** Tagged villagers and name plates of builds before the troupe, in loaded chunks. */
    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    /** A click on a member of the cast (from the troupe: player model or stand-in alike). */
    private void clicked(Player player, String key) {
        IsleRole role = IsleRole.byId(key);
        if (role == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = clickCool.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        clickCool.put(player.getUniqueId(), now + 400L);
        talk(player, role);
    }

    private void talk(Player player, IsleRole role) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.7f, 1.1f);
        if (role.flavorOnly()) {
            List<String> barks = role.barks();
            say(player, role, barks.get(ThreadLocalRandom.current().nextInt(barks.size())));
            return;
        }
        // First visit: Elsie runs the isle lesson; the rest of the cast send you back to her.
        if (role == IsleRole.WARDEN && isle.lesson().onTeacher(player)) {
            return;
        }
        if (role != IsleRole.WARDEN && isle.lesson().deferring(player)) {
            say(player, role, FieldLesson.t(player,
                    "Elsie's lesson first — the bar up top shows the way. Then come see me.",
                    "Erst Elsies Lektion — die Leiste oben zeigt den Weg. Dann komm zu mir."));
            return;
        }
        // The Warden talks first (bubble + chips); her board sits behind "Board…".
        if (role == IsleRole.WARDEN && wardenDesk.click(player)) {
            return;
        }
        isle.menus().open(player, role);
    }

    /** Lesson click pre-check on Elsie's bubble (see {@link TalkDesk#busy}). */
    boolean teacherBusy(Player player) {
        return wardenDesk.busy(player);
    }

    /** A scripted lesson beat in Elsie's bubble. False when it can't open (classic chat, too far). */
    boolean teacherScript(Player player, List<String> lines, List<de.aetherion.core.api.TalkAccess.Reply> replies) {
        return wardenDesk.script(player, lines, replies);
    }

    void say(Player player, IsleRole role, String line) {
        player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
    }

    private void tick() {
        wardenDesk.tick();
        if (troupe == null || placed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!IsleWorld.onIsle(isle.plugin(), player)) {
                continue;
            }
            for (IsleRole role : placed.keySet()) {
                Location at = troupe.location(role.id());
                if (at == null || !player.getWorld().equals(at.getWorld())) {
                    continue;
                }
                double distance = at.distance(player.getLocation());
                if (distance <= TURN_RANGE) {
                    troupe.face(role.id(), player); // player models turn by themselves
                }
                if (distance <= BARK_RANGE) {
                    String key = player.getUniqueId() + ":" + role.id();
                    Long last = barked.get(key);
                    if (last == null || now - last > BARK_COOLDOWN_MS) {
                        barked.put(key, now);
                        List<String> barks = role.barks();
                        say(player, role, barks.get(ThreadLocalRandom.current().nextInt(barks.size())));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            IsleRole role = IsleRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", "world"));
            if (world == null) {
                continue;
            }
            placed.put(role, new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw", 0.0d), 0.0f));
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        placed.forEach((role, at) -> {
            String path = "roles." + role.id();
            config.set(path + ".world", at.getWorld() == null ? "world" : at.getWorld().getName());
            config.set(path + ".x", at.getX());
            config.set(path + ".y", at.getY());
            config.set(path + ".z", at.getZ());
            config.set(path + ".yaw", at.getYaw());
        });
        try {
            if (!isle.plugin().getDataFolder().exists()) {
                isle.plugin().getDataFolder().mkdirs();
            }
            de.aetherion.core.persist.AtomicYaml.save(config, file);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Could not save isle-cast.yml: " + exception.getMessage());
        }
    }
}
