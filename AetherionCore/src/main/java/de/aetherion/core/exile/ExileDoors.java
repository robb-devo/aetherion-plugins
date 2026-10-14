package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BossSpawnAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.StoryAccess;
import de.aetherion.core.api.TalkAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Exile's doors (mmo-c, Runde 7 coordinates). Runde 7 pasted every district gate shut; nothing ever opened
 * them. Here they open per player, softly (the story order is recommended, never forced):
 *
 * <ul>
 *   <li><b>Gates</b> (crater, observatory, theatre, garden): right-click the bars, step through to the other side.</li>
 *   <li><b>Rituals</b>: Caedmon's sword wakes the Hollow Sun in its crater; the music box on Elin's stage starts
 *   Seraphine; the star ring in the observatory opens the Helios instance; the Maw takes you down to the Last
 *   Seed (World Eater site). Each is a short talk with the place itself, then the existing fight.</li>
 *   <li><b>The Great Doors</b>: Aetherion is not ready. The doors stay shut, the bell tolls four times.</li>
 *   <li><b>District tells</b>: the first time you walk into a district, it says its name.</li>
 * </ul>
 */
public final class ExileDoors implements Listener {

    public static final String TAG = "aetherion_exile_door";

    /** Iron Ring bars / observatory door / Little Theatre grille / garden gate: block box, outside, inside. */
    private record Gate(String id, int[] box, double[] outside, double[] inside, String nameEn, String nameDe,
                        String tellEn, String tellDe, String softQuest, String softEn, String softDe) {
    }

    private static final List<Gate> GATES = List.of(
            new Gate("crater", new int[] {71, 117, -72, 75, 121, -72},
                    new double[] {73.5, 120.0, -69.5, 0}, new double[] {73.5, 115.0, -73.5, 180},
                    "The Iron Ring", "Der Eisenring",
                    "The crater of the fallen captain. The Hollow Sun.", "Der Krater des gefallenen Hauptmanns. Die Hollow Sun.",
                    null, null, null),
            new Gate("observatory", new int[] {-96, 157, -168, -94, 159, -165},
                    new double[] {-91.5, 157.0, -165.5, 90}, new double[] {-97.5, 157.0, -165.5, 90},
                    "The Observatory", "Das Observatorium",
                    "Orrin keeps the dying star beating. Helios.", "Orrin hält den sterbenden Stern am Schlagen. Helios.",
                    "lullaby_captain", "Orrin waits for the captain's report. You may go in early.",
                    "Orrin wartet auf den Bericht des Hauptmanns. Du darfst trotzdem früher hinein."),
            new Gate("theater", new int[] {10, 143, 65, 11, 156, 73},
                    new double[] {13.5, 143.0, 69.5, 90}, new double[] {8.5, 143.0, 69.5, 90},
                    "The Little Theatre", "Das kleine Theater",
                    "Elin's stage. Seraphine still performs.", "Elins Bühne. Seraphine spielt noch immer.",
                    "lullaby_heart", "This door answers to Maelis' seal. It lets you in anyway.",
                    "Diese Tür hört auf Maelis' Siegel. Sie lässt dich trotzdem herein."),
            new Gate("garden", new int[] {-58, 135, 118, -58, 139, 122},
                    new double[] {-54.5, 135.0, 120.5, 90}, new double[] {-60.5, 135.0, 120.5, 90},
                    "The Root-Ground", "Der Wurzelgrund",
                    "The Unbroken's garden. Below it, the Last Seed.", "Der Garten des Unbroken. Darunter der Letzte Same.",
                    "lullaby_doll", "The gardener's key would open this. The roots don't care.",
                    "Der Schlüssel des Gärtners würde hier passen. Den Wurzeln ist das egal.")
    );

    // rituals ------------------------------------------------------------------------------------------
    private static final double[] SWORD = {96.5, 102.0, -111.5};
    private static final double[] HOLLOW_SUN_SPAWN = {96.5, 112.0, -111.5};
    private static final double[] MUSIC_BOX = {-35.5, 146.0, 69.5};
    private static final double[] SAINT_SPAWN = {-43.5, 146.0, 69.5};
    private static final double[] STAR_RING = {-126.5, 157.0, -165.5};
    private static final double[] MAW = {-127.5, 135.0, 86.5};
    private static final double[] GREAT_DOORS = {-1.5, 143.0, 0.5};

    private static final float[] HYMN = {0.749f, 0.841f, 0.944f, 1.122f, 1.26f, 1.498f, 1.682f};

    private record Ritual(String id, double[] at, float w, float h, String nameEn, String nameDe, String color,
                          String labelEn, String labelDe) {
    }

    private static final List<Ritual> RITUALS = List.of(
            new Ritual("caedmon_sword", SWORD, 2.4f, 3.6f, "Caedmon's Sword", "Caedmons Schwert", "§6",
                    "§6✦ Caedmon's Sword §7· the Hollow Sun", "§6✦ Caedmons Schwert §7· die Hollow Sun"),
            new Ritual("music_box", MUSIC_BOX, 1.2f, 1.2f, "The Music Box", "Die Spieluhr", "§d",
                    "§d♪ The Music Box §7· Seraphine", "§d♪ Die Spieluhr §7· Seraphine"),
            new Ritual("star_ring", STAR_RING, 3.0f, 7.0f, "The Star Ring", "Der Sternenring", "§e",
                    "§e☀ The Star Ring §7· Helios", "§e☀ Der Sternenring §7· Helios"),
            new Ritual("the_maw", MAW, 1.6f, 2.2f, "The Maw", "Der Schlund", "§2",
                    "§2❦ The Maw §7· Nihil & the Unbroken", "§2❦ Der Schlund §7· Nihil & der Unbroken"),
            new Ritual("great_doors", GREAT_DOORS, 4.0f, 8.0f, "The Great Doors", "Die Großen Tore", "§f",
                    "§f⚜ The Great Doors §8· sealed", "§f⚜ Die Großen Tore §8· versiegelt")
    );

    private record District(String id, double cx, double cz, double r, double y0, double y1, int[] box,
                            String titleEn, String titleDe, String subEn, String subDe) {
    }

    private static final List<District> DISTRICTS = List.of(
            new District("court", 0, 0, 0, 100, 136, new int[] {20, 100, -62, 132, 136, 146},
                    "THE COURT", "DIE HOFSTADT", "stopped mid-evening, a thousand years ago", "mitten im Abend stehen geblieben, vor tausend Jahren"),
            new District("crater", 96, -112, 50, 95, 132, null,
                    "THE KNIGHT'S FALL", "DER STURZ DES RITTERS", "where the Sun Guard's captain came down", "wo der Hauptmann der Sonnengarde herabkam"),
            new District("observatory", -114, -166, 24, 150, 178, null,
                    "THE OBSERVATORY OF THE SUN", "DAS OBSERVATORIUM DER SONNE", "something under everything is still beating", "unter allem schlägt noch etwas"),
            new District("theater", 0, 0, 0, 142, 188, new int[] {-55, 142, 43, 9, 188, 95},
                    "THE LITTLE THEATRE", "DAS KLEINE THEATER", "every seat taken, nobody breathing", "jeder Platz besetzt, keiner atmet"),
            new District("garden", -124, 44, 38, 128, 150, null,
                    "THE ROOT-GROUND", "DER WURZELGRUND", "a garden someone kept anyway", "ein Garten, den trotzdem jemand gepflegt hat"),
            new District("forecourt", 0, 0, 0, 142, 156, new int[] {13, 142, -28, 35, 156, 28},
                    "THE FORECOURT", "DER VORHOF", "the doors have not opened in a thousand years", "die Tore sind seit tausend Jahren zu")
    );

    private final AetherionCore plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> gateCool = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> seenDistricts = new ConcurrentHashMap<>();
    private final Set<String> busyRituals = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> interactionRitual = new HashMap<>();
    private World world;

    private record Pending(String ritual, long until) {
    }

    public ExileDoors(AetherionCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "exile_door");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        AetherServices.addLangListener(this::refreshLabels);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshLabels(p), 20L);
    }

    @EventHandler
    public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        refreshLabels(p);
        if (p.getWorld().getName().equalsIgnoreCase("world_eater")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getWorld().getName().equalsIgnoreCase("world_eater")) {
                    p.sendMessage(Say.t(p, "§2❦ The Last Seed §8» §7The roots will carry you back up: §f/exile return",
                            "§2❦ Der Letzte Same §8» §7Die Wurzeln tragen dich zurück nach oben: §f/exile return"));
                }
            }, 60L);
        }
    }

    /** Where someone comes back to from a boss world that lives beside the island. */
    public Location returnPoint(String fromWorld) {
        if (world == null) {
            return null;
        }
        if (fromWorld != null && fromWorld.equalsIgnoreCase("world_eater")) {
            return new Location(world, -127.5, 135.0, 84.5, 0f, 10f);
        }
        if (fromWorld != null && fromWorld.equalsIgnoreCase("helios_requiem")) {
            return new Location(world, -122.5, 157.0, -165.5, 90f, 0f);
        }
        return null;
    }

    // ------------------------------------------------------------------ boot: labels + hitboxes

    /**
     * Arms the doors on the exile world. Props (labels + hitboxes) are not persistent: they are rebuilt whenever
     * their chunk loads, so an unload never leaves doubles and a crash never leaves orphans.
     */
    public void build(World exile) {
        this.world = exile;
        if (exile == null) {
            return;
        }
        for (org.bukkit.Chunk c : exile.getLoadedChunks()) {
            refreshChunk(c);
        }
        for (Ritual r : RITUALS) {
            Location at = loc(r.at());
            if (!exile.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                exile.getChunkAt(at);
            }
        }
        if (ambient == null) {
            ambient = Bukkit.getScheduler().runTaskTimer(plugin, this::ambience, 60L, 20L);
        }
        plugin.getLogger().info("Exile doors armed: " + GATES.size() + " gates, " + RITUALS.size() + " rituals.");
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (world == null || !event.getWorld().equals(world)) {
            return;
        }
        org.bukkit.Chunk c = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (c.isLoaded()) {
                refreshChunk(c);
            }
        });
    }

    private static boolean in(org.bukkit.Chunk c, double x, double z) {
        return ((int) Math.floor(x) >> 4) == c.getX() && ((int) Math.floor(z) >> 4) == c.getZ();
    }

    private void refreshChunk(org.bukkit.Chunk c) {
        boolean any = false;
        for (Ritual r : RITUALS) {
            any |= in(c, r.at()[0], r.at()[2]);
        }
        for (Gate g : GATES) {
            int[] b = g.box();
            any |= in(c, (b[0] + b[3]) / 2.0 + 0.5, (b[2] + b[5]) / 2.0 + 0.5);
        }
        if (!any) {
            return;
        }
        for (Entity e : c.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                interactionRitual.remove(e.getUniqueId());
                e.remove();
            }
        }
        for (Ritual r : RITUALS) {
            if (!in(c, r.at()[0], r.at()[2])) {
                continue;
            }
            Location at = loc(r.at());
            Interaction hit = world.spawn(at, Interaction.class, i -> {
                mark(i);
                i.setInteractionWidth(r.w());
                i.setInteractionHeight(r.h());
                i.setResponsive(true);
            });
            interactionRitual.put(hit.getUniqueId(), r.id());
            label(at.clone().add(0, r.h() + 0.6, 0), r.labelEn(), r.labelDe());
        }
        for (Gate g : GATES) {
            int[] b = g.box();
            double cx = (b[0] + b[3]) / 2.0 + 0.5;
            double cz = (b[2] + b[5]) / 2.0 + 0.5;
            if (!in(c, cx, cz)) {
                continue;
            }
            Location top = new Location(world, cx, b[4] + 1.6, cz);
            label(top, "§7⛓ " + g.nameEn() + " §8· §7right-click to pass", "§7⛓ " + g.nameDe() + " §8· §7Rechtsklick zum Durchgehen");
        }
    }

    private org.bukkit.scheduler.BukkitTask ambient;

    private void mark(Entity e) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    /** One bilingual label: two text displays, each shown only to readers of its language. */
    private void label(Location at, String en, String de) {
        for (int lang = 0; lang < 2; lang++) {
            final boolean german = lang == 1;
            TextDisplay t = at.getWorld().spawn(at, TextDisplay.class, d -> {
                mark(d);
                d.text(Component.text(german ? de : en));
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0x60, 10, 8, 14));
                d.setViewRange(0.45f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                d.getPersistentDataContainer().set(new NamespacedKey(plugin, "exile_label_de"), PersistentDataType.BYTE, (byte) (german ? 1 : 0));
                d.setVisibleByDefault(false);
            });
            for (Player p : at.getWorld().getPlayers()) {
                if (Say.german(p) == german) {
                    p.showEntity(plugin, t);
                }
            }
        }
    }

    /** Language-split labels: show each player the ones in their language (join / language switch). */
    public void refreshLabels(Player p) {
        if (world == null || p == null || !p.getWorld().equals(world)) {
            return;
        }
        NamespacedKey de = new NamespacedKey(plugin, "exile_label_de");
        boolean german = Say.german(p);
        for (TextDisplay t : world.getEntitiesByClass(TextDisplay.class)) {
            Byte flag = t.getPersistentDataContainer().get(de, PersistentDataType.BYTE);
            if (flag == null) {
                continue;
            }
            if ((flag == 1) == german) {
                p.showEntity(plugin, t);
            } else {
                p.hideEntity(plugin, t);
            }
        }
    }

    private void ambience() {
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (dist2(l, SWORD) < 60 * 60) {
                p.spawnParticle(Particle.FLAME, loc(SWORD).add(0, 2.5, 0), 3, 0.3, 1.2, 0.3, 0.01);
                p.spawnParticle(Particle.SMALL_FLAME, loc(SWORD).add(0, 0.5, 0), 6, 1.6, 0.2, 1.6, 0.01);
            }
            if (dist2(l, MUSIC_BOX) < 40 * 40) {
                p.spawnParticle(Particle.NOTE, loc(MUSIC_BOX).add(0, 1.2, 0), 1, 0.2, 0.1, 0.2, 0.6);
            }
            if (dist2(l, STAR_RING) < 40 * 40) {
                Location ring = loc(new double[] {-127.0, 163.0, -165.5});
                for (int i = 0; i < 10; i++) {
                    double a = Math.random() * Math.PI * 2;
                    p.spawnParticle(Particle.END_ROD, ring.clone().add(0, Math.sin(a) * 4.5, Math.cos(a) * 4.5), 1, 0, 0, 0, 0);
                }
            }
            if (dist2(l, MAW) < 40 * 40) {
                p.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, loc(MAW).add(0, 3, 6), 4, 3, 1, 3, 0);
            }
        }
    }

    // ------------------------------------------------------------------ gates

    @EventHandler(priority = EventPriority.HIGH)
    public void onGateClick(PlayerInteractEvent event) {
        if (world == null || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = event.getClickedBlock();
        if (b == null || !b.getWorld().equals(world)) {
            return;
        }
        Gate gate = gateAt(b);
        if (gate == null) {
            return;
        }
        event.setCancelled(true);
        pass(event.getPlayer(), gate);
    }

    private static Gate gateAt(Block b) {
        for (Gate g : GATES) {
            int[] x = g.box();
            if (b.getX() >= x[0] - 1 && b.getX() <= x[3] + 1 && b.getY() >= x[1] && b.getY() <= x[4] + 1
                    && b.getZ() >= x[2] - 1 && b.getZ() <= x[5] + 1) {
                return g;
            }
        }
        return null;
    }

    private void pass(Player p, Gate g) {
        long now = System.currentTimeMillis();
        Long cool = gateCool.get(p.getUniqueId());
        if (cool != null && now < cool) {
            return;
        }
        gateCool.put(p.getUniqueId(), now + 1500L);
        Location out = loc(g.outside());
        Location in = loc(g.inside());
        boolean goingIn = p.getLocation().distanceSquared(out) <= p.getLocation().distanceSquared(in);
        Location to = goingIn ? in : out;
        if (!goingIn) {
            to.setYaw(to.getYaw() + 180f);
        }
        if (goingIn) {
            p.showTitle(Title.title(Component.text(Say.t(p, g.nameEn(), g.nameDe()), NamedTextColor.GOLD),
                    Component.text(Say.t(p, g.tellEn(), g.tellDe()), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(700))));
            if (g.softQuest() != null && !questDone(p, g.softQuest())) {
                p.sendMessage("§8» §7" + Say.t(p, g.softEn(), g.softDe()));
            }
        }
        int[] b = g.box();
        Location mid = new Location(world, (b[0] + b[3]) / 2.0 + 0.5, (b[1] + b[4]) / 2.0 + 0.5, (b[2] + b[5]) / 2.0 + 0.5);
        world.spawnParticle(Particle.END_ROD, mid, 18, 0.6, 1.4, 0.6, 0.02);
        p.playSound(mid, Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.9f, 0.6f);
        p.playSound(mid, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7f, 0.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false, false));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.teleport(to);
                p.playSound(to, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.7f, 0.6f);
            }
        }, 8L);
    }

    // ------------------------------------------------------------------ rituals

    @EventHandler(priority = EventPriority.HIGH)
    public void onRitualClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction hit)) {
            return;
        }
        String id = interactionRitual.get(hit.getUniqueId());
        if (id == null) {
            if (!hit.getScoreboardTags().contains(TAG)) {
                return;
            }
            id = nearestRitual(hit.getLocation());
            if (id == null) {
                return;
            }
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        TalkAccess talk = AetherServices.talk();
        if (talk != null && talk.recentlyPicked(p)) {
            return;
        }
        ritual(p, id);
    }

    private static String nearestRitual(Location at) {
        for (Ritual r : RITUALS) {
            if (dist2(at, r.at()) < 1.0) {
                return r.id();
            }
        }
        return null;
    }

    private Ritual ritualById(String id) {
        for (Ritual r : RITUALS) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** Public so the Quests NPCs (Orrin, the Usher …) could hand a player straight to a door. */
    public void ritual(Player p, String id) {
        Ritual r = ritualById(id);
        if (r == null) {
            return;
        }
        // second click inside the chat fallback window = begin
        Pending pend = pending.get(p.getUniqueId());
        if (pend != null && pend.ritual().equals(id) && pend.until() > System.currentTimeMillis()) {
            pending.remove(p.getUniqueId());
            begin(p, r);
            return;
        }
        switch (id) {
            case "caedmon_sword" -> offer(p, r,
                    List.of(Say.t(p, "The blade is still warm. A thousand years, and still warm.",
                                    "Die Klinge ist noch warm. Tausend Jahre, und immer noch warm."),
                            Say.t(p, "Something above the crater is waiting for an order.",
                                    "Über dem Krater wartet etwas auf einen Befehl.")),
                    Say.t(p, "Give the order", "Befehl geben"), Say.t(p, "Let him rest", "Lass ihn ruhen"));
            case "music_box" -> offer(p, r,
                    List.of(Say.t(p, "A music box, run down in the middle of a song.",
                                    "Eine Spieluhr, mitten im Lied abgelaufen."),
                            Say.t(p, "Every puppet in the hall is facing the stage. Waiting.",
                                    "Jede Puppe im Saal schaut zur Bühne. Wartet.")),
                    Say.t(p, "Wind it", "Aufziehen"), Say.t(p, "Leave it", "Lass sie"));
            case "star_ring" -> {
                if (!orrinHeard(p)) {
                    tell(p, r, Say.t(p, "The ring is cold. Orrin is watching you from his chair. Speak to him first.",
                            "Der Ring ist kalt. Orrin beobachtet dich von seinem Stuhl aus. Sprich zuerst mit ihm."));
                    return;
                }
                offer(p, r,
                        List.of(Say.t(p, "The ring hums at the pace of a heartbeat. Hers.",
                                        "Der Ring summt im Takt eines Herzschlags. Ihres."),
                                Say.t(p, "Step through, and the star will know what you came for.",
                                        "Tritt hindurch, und der Stern weiß, weshalb du gekommen bist.")),
                        Say.t(p, "Step through", "Hindurchtreten"), Say.t(p, "Not yet", "Noch nicht"));
            }
            case "the_maw" -> offer(p, r,
                    List.of(Say.t(p, "Roots, all the way down. Further down than the island goes.",
                                    "Wurzeln, bis ganz nach unten. Weiter hinab, als die Insel reicht."),
                            Say.t(p, "Something down there is still eating. You can hear it chew.",
                                    "Da unten frisst noch etwas. Man hört es kauen.")),
                    Say.t(p, "Go down", "Hinabsteigen"), Say.t(p, "Not yet", "Noch nicht"));
            case "great_doors" -> sealed(p, r);
            default -> {
            }
        }
    }

    private void offer(Player p, Ritual r, List<String> lines, String yes, String no) {
        TalkAccess talk = AetherServices.talk();
        String speaker = "exile_" + r.id();
        if (talk != null && talk.bubbles(p)) {
            talk.registerSpeaker(new TalkAccess.Speaker(speaker, Say.t(p, r.nameEn(), r.nameDe()), r.color(), null,
                    () -> loc(r.at()).add(0, r.h() - 0.4, 0)));
            boolean ok = true;
            for (String line : lines) {
                ok &= talk.say(p, speaker, line);
            }
            if (ok && talk.replies(p, speaker, List.of(
                    TalkAccess.Reply.of(yes, NamedTextColor.GOLD, () -> begin(p, r)),
                    TalkAccess.Reply.of(no, NamedTextColor.DARK_GRAY, () -> talk.end(p))))) {
                return;
            }
        }
        for (String line : lines) {
            p.sendMessage(r.color() + Say.t(p, r.nameEn(), r.nameDe()) + " §8» §7" + line);
        }
        p.sendMessage(Say.t(p, "§8(right-click again to " + yes.toLowerCase() + ")", "§8(noch einmal Rechtsklick: " + yes + ")"));
        pending.put(p.getUniqueId(), new Pending(r.id(), System.currentTimeMillis() + 10_000L));
    }

    private void tell(Player p, Ritual r, String line) {
        TalkAccess talk = AetherServices.talk();
        String speaker = "exile_" + r.id();
        if (talk != null && talk.bubbles(p)) {
            talk.registerSpeaker(new TalkAccess.Speaker(speaker, Say.t(p, r.nameEn(), r.nameDe()), r.color(), null,
                    () -> loc(r.at()).add(0, r.h() - 0.4, 0)));
            if (talk.say(p, speaker, line)) {
                return;
            }
        }
        p.sendMessage(r.color() + Say.t(p, r.nameEn(), r.nameDe()) + " §8» §7" + line);
    }

    private void begin(Player p, Ritual r) {
        if (!p.isOnline()) {
            return;
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(p);
        }
        switch (r.id()) {
            case "caedmon_sword" -> summon(p, r, "hollow_sun", HOLLOW_SUN_SPAWN, 70,
                    Say.t(p, "§6Ser Caedmon §8» §7Captain of the Sun Guard. State your order.",
                            "§6Ser Caedmon §8» §7Hauptmann der Sonnengarde. Nenn deinen Befehl."));
            case "music_box" -> summon(p, r, "hanging_saint", SAINT_SPAWN, 90, null);
            case "star_ring" -> portal(p, r, "helios");
            case "the_maw" -> portal(p, r, "world_eater");
            default -> {
            }
        }
    }

    private void summon(Player p, Ritual r, String template, double[] spawnAt, int ticks, String line) {
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses == null) {
            p.sendMessage("§cBossEngine is not running on this backend.");
            return;
        }
        if (bosses.alive(template)) {
            tell(p, r, Say.t(p, "Not now. It is already awake. Look up.", "Nicht jetzt. Es ist schon wach. Schau nach oben."));
            return;
        }
        if (!busyRituals.add(r.id())) {
            return;
        }
        Location at = loc(r.at());
        Location spawn = loc(spawnAt);
        boolean music = template.equals("hanging_saint");
        for (int i = 0; i < ticks; i += 5) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (music) {
                    if (k % 20 == 0 && k / 20 < 4) {
                        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.9f, HYMN[k / 20]);
                    }
                    world.spawnParticle(Particle.NOTE, at.clone().add(0, 1.3, 0), 1, 0.3, 0.2, 0.3, 0.8);
                    world.spawnParticle(Particle.WHITE_ASH, spawn.clone().add(0, 6, 0), 12, 4, 3, 4, 0.0);
                } else {
                    world.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 8, 1.4, 0.4, 1.4, 0.06);
                    world.spawnParticle(Particle.LAVA, spawn, 2, 2, 2, 2, 0);
                    if (k % 20 == 0) {
                        world.playSound(at, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.5f + k / 200f);
                    }
                }
            }, i);
        }
        if (line != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player near : world.getPlayers()) {
                    if (near.getLocation().distanceSquared(at) < 80 * 80) {
                        near.sendMessage(line);
                    }
                }
            }, ticks / 2L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            busyRituals.remove(r.id());
            boolean ok;
            try {
                ok = bosses.spawn(template, spawn, p.isOnline() ? p : null);
            } catch (RuntimeException ex) {
                ok = false;
                plugin.getLogger().warning("Exile ritual " + r.id() + " could not spawn " + template + ": " + ex.getMessage());
            }
            if (!ok && p.isOnline()) {
                p.sendMessage(Say.t(p, "§8» §7Nothing answers. (The boss could not wake — ask staff.)",
                        "§8» §7Nichts antwortet. (Der Boss konnte nicht erwachen — melde dich beim Team.)"));
            } else {
                world.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5f, music ? 1.6f : 0.6f);
            }
        }, ticks);
    }

    private void portal(Player p, Ritual r, String arena) {
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses == null) {
            p.sendMessage("§cBossEngine is not running on this backend.");
            return;
        }
        Location at = loc(r.at());
        boolean helios = arena.equals("helios");
        Location center = helios ? loc(new double[] {-127.0, 163.0, -165.5}) : at.clone().add(0, 1.0, 1.5);
        for (int i = 0; i < 40; i += 2) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                double rad = helios ? 4.5 : 1.6 + k * 0.03;
                for (int s = 0; s < 18; s++) {
                    double a = (s / 18.0) * Math.PI * 2 + k * 0.15;
                    Location pt = center.clone().add(helios ? 0 : Math.cos(a) * rad, Math.sin(a) * rad, helios ? Math.cos(a) * rad : Math.sin(a) * rad * 0.2);
                    p.spawnParticle(helios ? Particle.END_ROD : Particle.SPORE_BLOSSOM_AIR, pt, 1, 0, 0, 0, 0);
                }
                if (k % 10 == 0) {
                    p.playSound(center, helios ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_ROOTS_BREAK,
                            SoundCategory.AMBIENT, 0.8f, helios ? 0.6f + k / 80f : 0.5f);
                }
            }, i);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            String result;
            try {
                result = bosses.enterArena(p, arena);
            } catch (RuntimeException ex) {
                result = "error";
                plugin.getLogger().warning("Exile portal " + arena + " failed for " + p.getName() + ": " + ex.getMessage());
            }
            if (result == null || result.equals("ok")) {
                return;
            }
            String why = switch (result.split(":")[0]) {
                case "full" -> Say.t(p, "Every stage of the star is taken. Try again shortly.", "Jede Bühne des Sterns ist belegt. Versuch's gleich wieder.");
                case "cooldown" -> Say.t(p, "The star rests a little longer.", "Der Stern ruht noch ein wenig.");
                case "not-leader" -> Say.t(p, "Only your party leader can open it.", "Nur deine Gruppenleitung kann ihn öffnen.");
                case "already" -> Say.t(p, "You are already inside.", "Du bist schon drin.");
                case "not-built", "disabled", "unsupported" -> Say.t(p, "It will not open yet. (Staff: the boss site isn't set up on this backend.)",
                        "Er öffnet sich noch nicht. (Team: der Boss-Ort ist auf diesem Backend nicht eingerichtet.)");
                default -> Say.t(p, "It will not open right now.", "Er öffnet sich gerade nicht.");
            };
            tell(p, r, why);
        }, 40L);
    }

    private void sealed(Player p, Ritual r) {
        if (!busyRituals.add("doors:" + p.getUniqueId())) {
            return;
        }
        tell(p, r, Say.t(p, "Bronze, taller than the colossi. No handle on this side.",
                "Bronze, höher als die Kolosse. Auf dieser Seite gibt es keinen Griff."));
        Location at = loc(r.at());
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.playSound(at.clone().add(-40, 60, 0), Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 1.0f, 0.5f);
                    if (k == 3) {
                        p.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 0.6f, 0.5f);
                    }
                }
            }, 40L + i * 26L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            busyRituals.remove("doors:" + p.getUniqueId());
            if (!p.isOnline()) {
                return;
            }
            p.showTitle(Title.title(Component.text(Say.t(p, "Not yet.", "Noch nicht."), NamedTextColor.WHITE),
                    Component.text(Say.t(p, "Four strokes. It should have been five.", "Vier Schläge. Es hätten fünf sein sollen."), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))));
            Map<String, String[]> finds = new LinkedHashMap<>();
            finds.put("lullaby_captain", new String[] {"Captain's Last Report", "Bericht des Hauptmanns"});
            finds.put("lullaby_heart", new String[] {"Maintenance Log, last page", "Wartungsbuch, letzte Seite"});
            finds.put("lullaby_doll", new String[] {"Elin's Doll", "Elins Puppe"});
            finds.put("lullaby_seed", new String[] {"Empty Seed Husk", "Leere Samenhülle"});
            p.sendMessage(Say.t(p, "§f⚜ The Great Doors §8» §7The castle waits for the end of a song nobody remembers.",
                    "§f⚜ Die Großen Tore §8» §7Das Schloss wartet auf das Ende eines Liedes, an das sich keiner erinnert."));
            for (Map.Entry<String, String[]> e : finds.entrySet()) {
                boolean done = questDone(p, e.getKey());
                p.sendMessage("  " + (done ? "§a✔ §f" : "§8✘ §7") + Say.t(p, e.getValue()[0], e.getValue()[1]));
            }
            p.sendMessage(Say.t(p, "§8(Aetherion himself is still on his way. The doors stay shut for now.)",
                    "§8(Aetherion selbst ist noch unterwegs. Die Tore bleiben vorerst zu.)"));
        }, 40L + 4 * 26L + 10L);
    }

    // ------------------------------------------------------------------ district tells

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (world == null || event.getTo() == null || !event.getTo().getWorld().equals(world)) {
            return;
        }
        if ((event.getFrom().getBlockX() >> 2) == (event.getTo().getBlockX() >> 2)
                && (event.getFrom().getBlockZ() >> 2) == (event.getTo().getBlockZ() >> 2)
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }
        Player p = event.getPlayer();
        Location l = event.getTo();
        for (District d : DISTRICTS) {
            if (!inside(d, l)) {
                continue;
            }
            Set<String> seen = seenDistricts.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
            if (seen.add(d.id())) {
                p.showTitle(Title.title(Component.text(Say.t(p, d.titleEn(), d.titleDe()), NamedTextColor.WHITE),
                        Component.text(Say.t(p, d.subEn(), d.subDe()), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(700), Duration.ofMillis(2600), Duration.ofMillis(900))));
                p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.4f, 0.6f);
            }
            return;
        }
    }

    private static boolean inside(District d, Location l) {
        if (l.getY() < d.y0() || l.getY() > d.y1()) {
            return false;
        }
        if (d.box() != null) {
            int[] b = d.box();
            return l.getX() >= b[0] && l.getX() <= b[3] + 1 && l.getZ() >= b[2] && l.getZ() <= b[5] + 1;
        }
        double dx = l.getX() - d.cx();
        double dz = l.getZ() - d.cz();
        return dx * dx + dz * dz <= d.r() * d.r();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        seenDistricts.remove(id);
        pending.remove(id);
        gateCool.remove(id);
    }

    // ------------------------------------------------------------------ helpers

    private boolean orrinHeard(Player p) {
        if (p.hasPermission("aetherion.core.admin")) {
            return true;
        }
        StoryAccess story = AetherServices.story();
        if (story == null) {
            return true;
        }
        try {
            if (story.playerFlag(p, "orrin_warned")) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        QuestProgressAccess quests = AetherServices.quests();
        return quests != null && (quests.isQuestActive(p, "lullaby_heart") || quests.isQuestCompleted(p, "lullaby_heart"));
    }

    private static boolean questDone(Player p, String questId) {
        QuestProgressAccess quests = AetherServices.quests();
        try {
            return quests != null && quests.isQuestCompleted(p, questId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Location loc(double[] a) {
        Location l = new Location(world, a[0], a[1], a[2]);
        if (a.length > 3) {
            l.setYaw((float) a[3]);
        }
        return l;
    }

    private static double dist2(Location l, double[] a) {
        double dx = l.getX() - a[0];
        double dy = l.getY() - a[1];
        double dz = l.getZ() - a[2];
        return dx * dx + dy * dy + dz * dz;
    }

    public void shutdown() {
        if (ambient != null) {
            ambient.cancel();
            ambient = null;
        }
    }
}
