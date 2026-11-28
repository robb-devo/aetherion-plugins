package de.aetherion.tutorial.isle;

import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.TutorialStore;
import de.aetherion.tutorial.Txt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The house rules of the isle. A lesson island must be a place where nothing can go wrong: nobody starves, nobody
 * dies, nobody loses a tool, nobody digs up the fields, and nobody is swept off to another corner of the network by
 * a command they do not know yet.
 */
final class IsleRules implements Listener {

    /** What a newcomer may type. Everything else belongs to the mainland. */
    private static final Set<String> ALLOWED = Set.of(
            "language", "lang", "sprache", "sprachewahl",
            "npctalk", "talkui",
            "handbook", "handbuch",
            "aetherion", "amanager", "aethermanager", "journal",
            "skills", "skill", "aetherskills", "aetherskill",
            "atrecipes", "atrecepies", "aetherionrecipes", "recipebook",
            "report", "bug", "bugreport", "combat",
            "tutorial", "morningbell", "mbell",
            "msg", "tell", "w", "r", "me", "help");

    /** Ground the quarry is made of: Mining decides what breaks and puts it back. */
    private static final Set<Material> QUARRY = Set.of(
            Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE);

    private final TutorialPlugin plugin;
    private final Isle isle;
    private Location centre;
    private double seaLimit;

    IsleRules(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
    }

    // ------------------------------------------------------------------ coming and going

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        admit(event.getPlayer(), true);
    }

    /**
     * Decides what this player is here for. Newcomers (the hub marked them) and visitors get their lesson; anybody
     * else carries a character that lives on the main world and is walked there.
     */
    void admit(Player player, boolean joining) {
        if (isle.director().of(player) != null) {
            return;
        }
        UUID id = player.getUniqueId();
        TutorialStore.Entry entry = plugin.store().load(id);
        boolean open = plugin.getConfig().getBoolean("isle.open", false);
        boolean expected = entry.state == TutorialStore.State.IN_PROGRESS || entry.state == TutorialStore.State.GRADUATING;
        if (!expected && !open) {
            if (player.hasPermission(TutorialPlugin.ADMIN)) {
                player.sendMessage("§e✦ §7Morningbell: no lesson is running for you here. "
                        + "§f/tutorial §7in the lobby starts a visit that leaves your character alone.");
                return;
            }
            // Not a newcomer, not a visitor: this character belongs on the main world.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isle.director().of(player) == null) {
                    plugin.toMain(player, Txt.t(player, "§e✦ §7The island is for first mornings. Taking you to the mainland…",
                            "§e✦ §7Die Insel ist für den ersten Morgen. Es geht aufs Festland…"));
                }
            }, joining ? 40L : 5L);
            return;
        }
        speak(player);
        if (player.getGameMode() != GameMode.CREATIVE || !player.hasPermission(TutorialPlugin.ADMIN)) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        heal(player);
        isle.director().join(player);
    }

    /** The lessons speak the player's language from the first line: the client's own, until they pick another. */
    private void speak(Player player) {
        UUID id = player.getUniqueId();
        try {
            boolean picked = de.aetherion.quests.lang.PlayerLang.hasChosen(player);
            if (!picked) {
                Locale locale = player.locale();
                boolean german = locale != null && "de".equalsIgnoreCase(locale.getLanguage());
                de.aetherion.quests.lang.PlayerLang.set(player,
                        german ? de.aetherion.quests.lang.LangCode.DE : de.aetherion.quests.lang.LangCode.EN);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(german
                                ? "§8Sprache: §7Deutsch§8. Ändern: §7/sprache"
                                : "§8Language: §7English§8. Change it: §7/language");
                    }
                }, 100L);
            }
            // The harbour's own welcome (Egon, the orientation desk) must not fire here: the island is the welcome.
            de.aetherion.quests.AetherionQuests quests = de.aetherion.quests.AetherionQuests.getInstance();
            if (quests != null && quests.getPlayerQuestStorage() != null
                    && !quests.getPlayerQuestStorage().hasStarterKit(id, "tutorial_graduation")) {
                quests.getPlayerQuestStorage().markStarterKit(id, "tutorial_graduation");
            }
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Could not prepare the Quests side for " + player.getName() + ": " + ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitFirst(PlayerQuitEvent event) {
        // before Core packs the character: nobody is saved as a camera or inside a boss fight
        Player player = event.getPlayer();
        Session session = isle.director().of(player);
        if (session == null) {
            return;
        }
        isle.scenes().abort(player, session);
        isle.boss().abort(player);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.resetPlayerTime();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        isle.director().quit(event.getPlayer());
    }

    // ------------------------------------------------------------------ the sky

    /** Before the bell it is the hour before sunrise for this player; after it, morning for good. */
    void applyTime(Player player, Session session) {
        long dawn = plugin.getConfig().getLong("isle.dawn-time", 22900L);
        long morning = plugin.getConfig().getLong("isle.morning-time", 450L);
        player.setPlayerTime(session.morning ? morning : dawn, false);
        player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (event.getWorld() == isle.world() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ nobody gets hurt for good

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && player.getWorld() == isle.world()) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.getWorld() != isle.world()) {
            return;
        }
        Session session = isle.director().of(player);
        if (session == null) {
            return;
        }
        if (session.scene) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            rescue(player, session, false);
            return;
        }
        double left = player.getHealth() - event.getFinalDamage();
        if (left > 0.5) {
            return;
        }
        // It would have been the end. On Morningbell it never is.
        event.setCancelled(true);
        if (isle.boss().fighting(player)) {
            isle.boss().breather(player);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            // a long jump down: sore feet, nothing more
            player.setHealth(Math.min(maxHealth(player), Math.max(2.0, player.getHealth())));
            return;
        }
        rescue(player, session, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity().getWorld() != isle.world()) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Session session = isle.director().of(event.getPlayer());
        if (session == null) {
            return;
        }
        Location back = safe(session);
        if (back != null) {
            event.setRespawnLocation(back);
        }
    }

    /** Back on safe ground with a full heart bar. */
    void rescue(Player player, Session session, boolean count) {
        Location back = safe(session);
        heal(player);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        if (back != null) {
            player.teleport(back);
        }
        if (count) {
            TutorialStore.Entry entry = plugin.store().get(player.getUniqueId());
            entry.deaths++;
            plugin.store().save(player.getUniqueId(), entry);
            player.sendMessage(Txt.t(player, "§e✦ §7That was close. §fBack on your feet§7: nothing is lost on Morningbell.",
                    "§e✦ §7Das war knapp. §fWieder auf den Beinen§7: Auf Morningbell geht nichts verloren."));
        }
    }

    private Location safe(Session session) {
        if (session.ground != null) {
            return session.ground.clone();
        }
        if (session.rescue != null) {
            return session.rescue.clone();
        }
        return isle.marks().at(isle.world(), "arrival");
    }

    static void heal(Player player) {
        player.setHealth(maxHealth(player));
    }

    private static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return max == null ? 20.0 : Math.max(1.0, max.getValue());
    }

    /**
     * Twice a second, from the director's heartbeat: remembers the last solid ground under the player and turns
     * around whoever swims too far out.
     */
    void patrol(Player player, Session session) {
        Location at = player.getLocation();
        if (at.getY() < isle.world().getMinHeight() - 4) {
            rescue(player, session, false);
            return;
        }
        Block feet = at.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        boolean solid = below.getType().isSolid() && !feet.isLiquid() && player.getFallDistance() < 0.5f;
        if (solid && !isle.boss().fighting(player)) {
            session.ground = at.clone();
        }
        if (centre == null) {
            measure();
        }
        double dx = at.getX() - centre.getX();
        double dz = at.getZ() - centre.getZ();
        if (dx * dx + dz * dz > seaLimit * seaLimit) {
            rescue(player, session, false);
            player.sendMessage(Txt.t(player, "§3Captain Wim§8: §fNot by swimming, {player}! The ferry leaves from the pier, once you are ready.",
                    "§3Kapitän Wim§8: §fNicht schwimmend, {player}! Die Fähre legt am Steg ab, sobald du so weit bist."));
        }
    }

    /** The island's middle and how far out the sea belongs to it (from the named spots themselves). */
    private void measure() {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (String key : isle.marks().keys()) {
            Location spot = isle.marks().at(isle.world(), key);
            if (spot == null) {
                continue;
            }
            minX = Math.min(minX, spot.getX());
            maxX = Math.max(maxX, spot.getX());
            minZ = Math.min(minZ, spot.getZ());
            maxZ = Math.max(maxZ, spot.getZ());
        }
        if (minX > maxX) {
            centre = isle.world().getSpawnLocation();
            seaLimit = plugin.getConfig().getDouble("isle.sea-limit", 210.0);
            return;
        }
        centre = new Location(isle.world(), (minX + maxX) / 2.0, 64.0, (minZ + maxZ) / 2.0);
        double half = Math.hypot(maxX - minX, maxZ - minZ) / 2.0;
        seaLimit = Math.min(plugin.getConfig().getDouble("isle.sea-limit", 210.0), half + 55.0);
    }

    // ------------------------------------------------------------------ nothing is lost, nothing is dug up

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isle.director().of(player) == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        event.setCancelled(true);
        Txt.actionBar(player, Txt.t(player, "§7You will need that. Nothing is thrown away on the island.",
                "§7Das brauchst du noch. Auf der Insel wird nichts weggeworfen."));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != isle.world() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Material type = event.getBlock().getType();
        if (QUARRY.contains(type) || trunk(type)) {
            // Mining and Foraging own these: what breaks, what it pays, when it grows back
            return;
        }
        event.setCancelled(true);
    }

    private static boolean trunk(Material type) {
        if (Tag.LOGS.isTagged(type)) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == isle.world() && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.getPlayer().getWorld() == isle.world() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().getWorld() == isle.world() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (event.getPlayer().getWorld() == isle.world() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ what the lessons watch for

    @EventHandler(priority = EventPriority.LOW)
    public void onTrample(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && player.getWorld() == isle.world() && block != null
                && block.getType() == Material.FARMLAND && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != isle.world() || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (block.getType() == Material.BELL && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Location bell = isle.marks().at(isle.world(), "bell");
            if (bell != null && bell.distanceSquared(block.getLocation().add(0.5, 0.0, 0.5)) < 16.0) {
                isle.director().onBell(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != isle.world()) {
            return;
        }
        Material type = event.getBlock().getType();
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE) {
            // a moment later, so Mining's own "too soft" line is read first
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    isle.director().onIron(player);
                }
            }, 12L);
        }
    }

    // ------------------------------------------------------------------ commands

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != isle.world() || player.hasPermission(TutorialPlugin.ADMIN)) {
            return;
        }
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (ALLOWED.contains(label)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Txt.t(player, "§e✦ §7That works on the mainland. §fThe island first§7: the bar at the top shows your next step.",
                "§e✦ §7Das geht auf dem Festland. §fErst die Insel§7: Die Leiste oben zeigt deinen nächsten Schritt."));
    }
}
