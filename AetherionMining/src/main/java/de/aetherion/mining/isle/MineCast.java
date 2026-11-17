package de.aetherion.mining.isle;

import de.aetherion.core.npc.CastTroupe;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
 * Spawns and runs the Mining Eldervale cast. Placements live in {@code mine-cast.yml}. The crew are real
 * people: player models with their own painted skins on a Core {@link CastTroupe}, each busy with their own
 * work until somebody walks up. Nothing is saved with the world, so restarts and reloads never stack
 * duplicates. Right-click opens the NPC's board. Walk past one and you get a line. On first start, every
 * role with a preset is placed automatically, so a fresh server has a cast.
 */
public final class MineCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;

    private final MineIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<MineRole, Location> placed = new EnumMap<>(MineRole.class);
    /** Saved spots whose world wasn't loaded yet: kept (and re-saved) until it is. */
    private final Map<MineRole, ConfigurationSection> pending = new EnumMap<>(MineRole.class);
    private CastTroupe troupe;
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private boolean seeded;
    private BukkitTask task;
    private final TalkDesk wickDesk;

    MineCast(MineIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "mine_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "mine_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "mine-cast.yml");
        this.wickDesk = wickDesk();
        load();
    }

    /**
     * Old Wick by the landing lamp: the isle's front desk in the talk bubble — where to dig, how deep it
     * goes, who's who, what bites; his map board stays one chip away. First visit belongs to {@link MineLesson}.
     */
    private TalkDesk wickDesk() {
        MineRole wick = MineRole.LAMPWARDEN;
        return new TalkDesk(isle.plugin(), "mine_isle_lampwarden", wick.display(), wick.color(), "isle_hand",
                () -> liveLocation(wick), () -> liveHologram(wick),
                player -> MineLesson.t(player, "Lamp's lit, {player}. Ask — or go make the rock sing.",
                        "Die Lampe brennt, {player}. Frag ruhig — oder bring den Fels zum Singen."),
                List.of(
                        TalkDesk.Topic.of("Where do I dig?", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                                "Where should I dig?",
                                "Any rock feeds the rhythm, ore feeds it double. Seven quarries — the Terraces east keep an ore depot.",
                                "Check your pick's ⛏ before you swing at a vein. Too soft and the rock just laughs."),
                        TalkDesk.Topic.of("How deep?", net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                                "How deep does it go?",
                                "Galleries, Deep Works, then the Undercroft. Deeper rock, more Fortune.",
                                "First time in a band you get a card and some XP. Mind the heat in the Emberseam."),
                        TalkDesk.Topic.of("Who's who?", net.kyori.adventure.text.format.NamedTextColor.AQUA,
                                "Who runs this rock?",
                                "§6Otto§f hands out contracts. §6Nan§f at the Hearth feeds you — in ore.",
                                "§dIlse§f buys specimens up at the Assay Office. §6Brann§f runs the Deep Forge on the pit floor."),
                        TalkDesk.Topic.of("Anything bite?", net.kyori.adventure.text.format.NamedTextColor.RED,
                                "What should I watch for?",
                                "Strain the rock and it caves in. You'll hear it first — move.",
                                "Stonejaws guard the rich seams. Sixteen good hits and they pay.")
                ),
                "Map…", player -> isle.menus().open(player, MineRole.LAMPWARDEN),
                "Mind your lamp.");
    }

    /** Where the NPC actually stands right now (it may be a few steps into its round), else its placed spot. */
    private Location liveLocation(MineRole role) {
        Location at = troupe == null ? null : troupe.location(role.id());
        return at != null ? at : location(role);
    }

    private Entity liveHologram(MineRole role) {
        return troupe == null ? null : troupe.label(role.id());
    }

    /** Body, name plate and stand-in dress of one role. */
    private static CastTroupe.Look look(MineRole role) {
        String skin;
        Material hand = role.icon();
        Color outfit;
        switch (role) {
            case LAMPWARDEN -> {
                skin = "mine_wick";
                outfit = Color.fromRGB(0x4a4034);
            }
            case CLERK -> {
                skin = "mine_otto";
                outfit = Color.fromRGB(0x6a3a2a);
            }
            case COOK -> {
                skin = "mine_nan";
                outfit = Color.fromRGB(0x6f7d5a);
            }
            case ASSAYER -> {
                skin = "mine_ilse";
                outfit = Color.fromRGB(0x3a2e52);
            }
            case FORGEMASTER -> {
                skin = "mine_brann";
                hand = Material.MACE; // a smith holds his hammer, not his anvil
                outfit = Color.fromRGB(0x3a2a20);
            }
            default -> {
                skin = "mine_hollis";
                outfit = Color.fromRGB(0x4a5a56);
            }
        }
        return CastTroupe.Look.of(role.display(), skin, hand, outfit)
                .stand(role.profession(), role.villagerType())
                .labelled(role.color() + "§l" + role.display() + "\n§7" + role.title() + "\n§e▸ §7" + role.role());
    }

    /** What each of them is busy with while nobody is asking. */
    private static CastTroupe.Habit habit(MineRole role) {
        return switch (role) {
            case LAMPWARDEN -> CastTroupe.Habit.work(9, 18, Material.FLINT_AND_STEEL);
            case CLERK -> CastTroupe.Habit.work(7, 14, Material.FEATHER);
            case COOK -> CastTroupe.Habit.work(6, 12, Material.BOWL, Material.BAKED_POTATO).strolling(2.5d);
            case ASSAYER -> CastTroupe.Habit.work(8, 16, Material.SPYGLASS);
            case FORGEMASTER -> CastTroupe.Habit.work(4, 9).strolling(2.5d);
            default -> CastTroupe.Habit.watch(12, 24);
        };
    }

    /** Lesson click pre-check on Wick's bubble (see {@link TalkDesk#busy}). */
    boolean teacherBusy(Player player) {
        return wickDesk.busy(player);
    }

    /** A scripted lesson beat in Wick's bubble. False when it can't open (classic chat, too far). */
    boolean teacherScript(Player player, List<String> lines, List<de.aetherion.core.api.TalkAccess.Reply> replies) {
        return wickDesk.script(player, lines, replies);
    }

    void start() {
        purgeStrays();
        troupe = CastTroupe.open(isle.plugin(), "mine", this::clicked);
        for (MineRole role : List.copyOf(pending.keySet())) {
            Location at = MineWorld.location(pending.get(role));
            if (at != null) {
                placed.put(role, at);
                pending.remove(role);
            }
        }
        if (!seeded && isle.plugin().getConfig().getBoolean("mine-cast.auto-place", true)) {
            for (MineRole role : MineRole.values()) {
                if (!placed.containsKey(role) && preset(role) != null) {
                    placed.put(role, preset(role));
                }
            }
            seeded = true;
            save();
        }
        ensureAll();
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        wickDesk.shutdown();
        if (troupe != null) {
            troupe.close();
            troupe = null;
        }
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(MineRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(MineRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    /** Configured preset spot for {@code role} (config {@code mine-cast.presets.<role>}: "x y z yaw"). */
    public Location preset(MineRole role) {
        String raw = isle.plugin().getConfig().getString("mine-cast.presets." + role.id());
        return raw == null ? null : MineWorld.point(isle.plugin(), raw);
    }

    /** Placed spot, else preset: where the compass points for this NPC. */
    public Location whereabouts(MineRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(MineRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        pending.remove(role);
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(MineRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (mine-cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String placeAllPresets() {
        int count = 0;
        for (MineRole role : MineRole.values()) {
            if (preset(role) != null) {
                placePreset(role);
                count++;
            }
        }
        return "§aPlaced §f" + count + "§a Mining Eldervale NPCs on their presets.";
    }

    public String remove(MineRole role) {
        pending.remove(role);
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    public String removeAll() {
        for (MineRole role : MineRole.values()) {
            despawn(role);
        }
        int count = placed.size();
        placed.clear();
        pending.clear();
        save();
        return "§eRemoved §f" + count + "§e Mining Eldervale NPCs.";
    }

    // ------------------------------------------------------------------ anchors

    public ItemStack anchor(MineRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Mining Eldervale NPC anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8Presets: DEV → Mining Island → NPC Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private MineRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return MineRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        MineRole role = anchorRole(player.getInventory().getItemInMainHand());
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
        for (MineRole role : placed.keySet()) {
            ensure(role);
        }
    }

    private void ensure(MineRole role) {
        Location at = placed.get(role);
        if (at == null || at.getWorld() == null || troupe == null) {
            return;
        }
        troupe.spawn(role.id(), at, look(role));
        troupe.habit(role.id(), habit(role));
    }

    private void despawn(MineRole role) {
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

    /** A click on a member of the crew (from the troupe: player model or stand-in alike). */
    private void clicked(Player player, String key) {
        MineRole role = MineRole.byId(key);
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
        // First visit: Old Wick runs the isle lesson; the rest of the crew send you back to him.
        if (role == MineRole.LAMPWARDEN && isle.lesson().onTeacher(player)) {
            return;
        }
        if (role != MineRole.LAMPWARDEN && isle.lesson().deferring(player)) {
            say(player, role, MineLesson.t(player,
                    "Old Wick's lesson first — the bar up top shows the way. Then come see me.",
                    "Erst Old Wicks Lektion — die Leiste oben zeigt den Weg. Dann komm zu mir."));
            return;
        }
        isle.compass().met(player, role);
        // Wick talks first (bubble + chips); his map sits behind "Map…".
        if (role == MineRole.LAMPWARDEN && wickDesk.click(player)) {
            return;
        }
        isle.menus().open(player, role);
    }

    void forget(UUID id) {
        clickCool.remove(id);
        String prefix = id + ":";
        barked.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void say(Player player, MineRole role, String line) {
        if (player != null) {
            player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
        }
    }

    /** Little flourish at an NPC (forge sparks, hearth smoke) for menu moments. */
    void flourish(MineRole role, Particle particle, int count) {
        Location at = location(role);
        if (at == null || at.getWorld() == null) {
            return;
        }
        de.aetherion.core.api.Vfx.puff(at.clone().add(0, 1.2, 0), particle, count, Math.max(0.3, 0.3), null);
    }

    private void tick() {
        wickDesk.tick();
        if (troupe == null || placed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!MineWorld.onIsle(isle.plugin(), player)) {
                continue;
            }
            for (MineRole role : placed.keySet()) {
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
        seeded = config.getBoolean("seeded", false);
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            MineRole role = MineRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            Location at = MineWorld.location(section);
            if (at != null) {
                placed.put(role, at);
            } else {
                pending.put(role, section);
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("seeded", seeded);
        pending.forEach((role, section) -> {
            if (!placed.containsKey(role)) {
                config.createSection("roles." + role.id(), section.getValues(false));
            }
        });
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
            isle.plugin().getLogger().warning("Could not save mine-cast.yml: " + exception.getMessage());
        }
    }
}
