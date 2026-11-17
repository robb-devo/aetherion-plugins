package de.aetherion.fishing.isle;

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
 * Spawns and runs the Fishing Eldervale cast. Placements live in {@code isle-cast.yml}. The cast are real
 * people: player models with their own painted skins on a Core {@link CastTroupe}, each busy with their
 * own work until somebody walks up. Nothing is saved with the world, so restarts and reloads never stack
 * duplicates. Right-click opens the NPC's board; walking past gets you a line.
 */
public final class LakeCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private final FishIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<LakeRole, Location> placed = new EnumMap<>(LakeRole.class);
    private CastTroupe troupe;
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private final TalkDesk harbourDesk;
    private BukkitTask task;

    LakeCast(FishIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "lake_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "lake_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "isle-cast.yml");
        this.harbourDesk = harbourDesk();
        load();
    }

    /**
     * Maren at the Harbourmaster's Hall: the lake's front desk in the talk bubble — the waters,
     * heat and streaks, shoal and bells, who's who; her angler card stays one chip away.
     */
    private TalkDesk harbourDesk() {
        LakeRole maren = LakeRole.HARBOURMASTER;
        return new TalkDesk(isle.plugin(), "fish_isle_harbourmaster", maren.display(), maren.color(), "fishmonger",
                () -> liveLocation(maren), () -> liveHologram(maren),
                player -> LakeLesson.t(player, "Back on the water, {player}? Every water here keeps its own fish.",
                        "Wieder am Wasser, {player}? Jedes Gewässer hier hat seine eigenen Fische."),
                List.of(
                        TalkDesk.Topic.of("The waters?", net.kyori.adventure.text.format.NamedTextColor.AQUA,
                                "Where do I fish?",
                                "Twelve named waters — the lake, the high tarns, even the plaza fountain.",
                                "Walk up to one and it goes on your card. Find all twelve and I pay for it."),
                        TalkDesk.Topic.of("Heat & streaks?", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                                "What's this heat?",
                                "Clean catches warm the water: Warm, Hot, Boiling, Whirlpool.",
                                "Hotter water, rarer fish. One miss and it's cold again."),
                        TalkDesk.Topic.of("Shoal & bells?", net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                                "What are the bells for?",
                                "Water boiling with leaping fish? That's the shoal. Cast inside — it moves on.",
                                "Bells mean a Silver Run, or the Eldermaw. When it's the Eldermaw, everyone pulls."),
                        TalkDesk.Topic.of("Who's who?", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE,
                                "Who else is here?",
                                "§eTilly§f at the Bait Shack turns fish into bait. §dOdile§f keeps the Log and buys trophies.",
                                "§3Old Finn§f watches the lake from the south pier. Ask him about the pale one.")
                ),
                "Card…", player -> isle.menus().openHarbour(player),
                "Tight lines, love.");
    }

    /** Where the NPC actually stands right now (it may be a few steps into its round), else its placed spot. */
    private Location liveLocation(LakeRole role) {
        Location at = troupe == null ? null : troupe.location(role.id());
        return at != null ? at : location(role);
    }

    private Entity liveHologram(LakeRole role) {
        return troupe == null ? null : troupe.label(role.id());
    }

    /** Body, name plate and stand-in dress of one role. */
    private static CastTroupe.Look look(LakeRole role) {
        String skin;
        Color outfit;
        switch (role) {
            case HARBOURMASTER -> {
                skin = "lake_maren";
                outfit = Color.fromRGB(0x24406a);
            }
            case BAIT -> {
                skin = "lake_tilly";
                outfit = Color.fromRGB(0x5a6a3a);
            }
            case TAXIDERMIST -> {
                skin = "lake_odile";
                outfit = Color.fromRGB(0x2a2e30);
            }
            default -> {
                skin = "lake_finn";
                outfit = Color.fromRGB(0xd8b020);
            }
        }
        return CastTroupe.Look.of(role.display(), skin, role.icon(), outfit)
                .stand(role.profession(), role.villagerType())
                .labelled(role.color() + "§l" + role.display() + "\n§7" + role.title() + "\n§e▸ §7" + role.role());
    }

    /** What each of them is busy with while nobody is asking. */
    private static CastTroupe.Habit habit(LakeRole role) {
        return switch (role) {
            case HARBOURMASTER -> CastTroupe.Habit.work(9, 18);
            case BAIT -> CastTroupe.Habit.work(5, 11, Material.KELP, Material.BUCKET).strolling(3.0d);
            case TAXIDERMIST -> CastTroupe.Habit.work(7, 14, Material.SHEARS, Material.TROPICAL_FISH);
            default -> CastTroupe.Habit.watch(10, 20);
        };
    }

    void start() {
        purgeStrays();
        troupe = CastTroupe.open(isle.plugin(), "lake", this::clicked);
        ensureAll();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        harbourDesk.shutdown();
        if (troupe != null) {
            troupe.close();
            troupe = null;
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(LakeRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(LakeRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    /** Configured preset spot for {@code role} (config {@code isle-cast.presets.<role>}). */
    public Location preset(LakeRole role) {
        return LakeWorld.location(isle.plugin().getConfig().getConfigurationSection("isle-cast.presets." + role.id()));
    }

    /** Placed spot, else preset — where the wayfinder points for this NPC. */
    public Location whereabouts(LakeRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(LakeRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(LakeRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (isle-cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String remove(LakeRole role) {
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    // ------------------------------------------------------------------ anchors

    public ItemStack anchor(LakeRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Fishing Eldervale NPC anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8Presets: DEV → Fishing Island → NPC Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private LakeRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return LakeRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        LakeRole role = anchorRole(player.getInventory().getItemInMainHand());
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
        for (LakeRole role : placed.keySet()) {
            ensure(role);
        }
    }

    private void ensure(LakeRole role) {
        Location at = placed.get(role);
        if (at == null || at.getWorld() == null || troupe == null) {
            return;
        }
        troupe.spawn(role.id(), at, look(role));
        troupe.habit(role.id(), habit(role));
    }

    private void despawn(LakeRole role) {
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
        LakeRole role = LakeRole.byId(key);
        if (role == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = clickCool.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        clickCool.put(player.getUniqueId(), now + 400L);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.7f, 1.1f);
        // First visit: Maren runs the isle lesson; the rest of the cast send you back to her.
        if (role == LakeRole.HARBOURMASTER && isle.lesson().onTeacher(player)) {
            return;
        }
        if (role != LakeRole.HARBOURMASTER && isle.lesson().deferring(player)) {
            say(player, role, LakeLesson.t(player,
                    "Maren's lesson first, love — the bar up top shows the way. Then come see me.",
                    "Erst Marens Lektion — die Leiste oben zeigt den Weg. Dann komm zu mir."));
            return;
        }
        // Maren talks first (bubble + chips); her angler card sits behind "Card…".
        if (role == LakeRole.HARBOURMASTER && harbourDesk.click(player)) {
            return;
        }
        isle.menus().open(player, role);
    }

    /** Lesson click pre-check on Maren's bubble (see {@link TalkDesk#busy}). */
    boolean teacherBusy(Player player) {
        return harbourDesk.busy(player);
    }

    /** A scripted lesson beat in Maren's bubble. False when it can't open (classic chat, too far). */
    boolean teacherScript(Player player, List<String> lines, List<de.aetherion.core.api.TalkAccess.Reply> replies) {
        return harbourDesk.script(player, lines, replies);
    }

    void say(Player player, LakeRole role, String line) {
        player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
    }

    private void tick() {
        harbourDesk.tick();
        if (troupe == null || placed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!LakeWorld.onIsle(player)) {
                continue;
            }
            for (LakeRole role : placed.keySet()) {
                Location at = troupe.location(role.id());
                if (at == null || !player.getWorld().equals(at.getWorld())) {
                    continue;
                }
                double distance = at.distance(player.getLocation());
                if (distance <= TURN_RANGE) {
                    troupe.face(role.id(), player); // player models turn by themselves
                }
                if (distance <= BARK_RANGE && !isle.casting(player)) {
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
            LakeRole role = LakeRole.byId(key);
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
