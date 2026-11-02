package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BossSpawnAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.api.TalkAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
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
 *   <li><b>Altars</b>: each boss site takes the boss's vial ({@link ExileVials}) and wakes it, no talk, no story
 *   gate, and every fight happens on the island itself. Caedmon's sword (a prop, {@link HollowSword}) climbs into
 *   the sky and comes down as the Hollow Sun in its crater; the music box in the Little Theatre starts Seraphine's
 *   show on her own stage, built into the hall ({@link ExileTidy}); the altar at the Star Court's gate rings the
 *   bell five times and Aetherion lands on the court. The star ring and the Maw still open Helios and the Last
 *   Seed. Without the vial a site says, in one line, what it wants.</li>
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
                    new double[] {73.5, 117.0, -69.5, 0}, new double[] {73.5, 115.0, -73.5, 180},
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
    /** The blade's ground point in the middle of the crater (the old block sword stood here, see ExileTidy). */
    private static final double[] SWORD = {96.5, 99.0, -111.5};
    /** Crater floor under the sword: the Hollow Sun measures its arena from here. */
    private static final double[] HOLLOW_SUN_SPAWN = {96.5, 99.0, -111.5};
    /** On the front of Seraphine's stage, facing the stalls. */
    private static final double[] MUSIC_BOX = {-23.5, 144.0, 81.5};
    /**
     * The middle of Seraphine's own stage, built for good into the Little Theatre's hall ({@link ExileTidy#THEATRE_STAGE}).
     * Her fights reuse a marked stage in their spawn chunk, so she opens on it instead of building one in the sky.
     */
    private static final double[] SAINT_SPAWN = {-23.5, 144.0, 66.5};
    /** Just inside the Star Court's south gate, where the road from the castle comes in. */
    private static final double[] AETHERION_ALTAR = {-15.5, 143.0, -61.5};
    /** The Star Court's open middle (south of the dais): Aetherion measures his arena from here. */
    private static final double[] AETHERION_SPAWN = {-22.5, 143.0, -90.5};
    private static final double[] STAR_RING = {-126.5, 157.0, -165.5};
    private static final double[] MAW = {-127.5, 135.0, 86.5};

    private static final float[] HYMN = {0.749f, 0.841f, 0.944f, 1.122f, 1.26f, 1.498f, 1.682f};

    private record Ritual(String id, double[] at, float w, float h, String nameEn, String nameDe, String color,
                          String labelEn, String labelDe) {
    }

    private static final List<Ritual> RITUALS = List.of(
            new Ritual("caedmon_sword", SWORD, 1.8f, HollowSword.HEIGHT, "Caedmon's Sword", "Caedmons Schwert", "§6",
                    "§6✦ The Hollow Sun §8· §7hand in its vial", "§6✦ Die Hollow Sun §8· §7Phiole abgeben"),
            new Ritual("music_box", MUSIC_BOX, 1.2f, 1.2f, "The Music Box", "Die Spieluhr", "§d",
                    "§d♪ Seraphine §8· §7hand in her vial", "§d♪ Seraphine §8· §7Phiole abgeben"),
            new Ritual("star_ring", STAR_RING, 3.0f, 7.0f, "The Star Ring", "Der Sternenring", "§e",
                    "§e☀ Helios §8· §7hand in the vial", "§e☀ Helios §8· §7Phiole abgeben"),
            new Ritual("the_maw", MAW, 1.6f, 2.2f, "The Maw", "Der Schlund", "§2",
                    "§2❦ Nihil & the Unbroken §8· §7hand in the vial", "§2❦ Nihil & der Unbroken §8· §7Phiole abgeben"),
            new Ritual("great_doors", AETHERION_ALTAR, 1.4f, 2.0f, "The Sovereign's Altar", "Der Altar des Souveräns", "§5",
                    "§5⚜ Aetherion §8· §7hand in the vial", "§5⚜ Aetherion §8· §7Phiole abgeben")
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
                    "THE FORECOURT", "DER VORHOF", "the doors have not opened in a thousand years", "die Tore sind seit tausend Jahren zu"),
            new District("starcourt", 0, 0, 0, 140, 170, new int[] {-74, 140, -164, 28, 170, -56},
                    "THE STAR COURT", "DER STERNPLATZ", "where the Sovereign comes down", "wo der Souverän herabkommt")
    );

    private final AetherionCore plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> gateCool = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hintCool = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> seenDistricts = new ConcurrentHashMap<>();
    private final Set<String> busyRituals = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> interactionRitual = new HashMap<>();
    private World world;
    private HollowSword sword;

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
        if (sword == null) {
            sword = new HollowSword(plugin, loc(SWORD), this::mark);
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
            if (r.id().equals("caedmon_sword") && sword != null) {
                sword.build();
            } else if (r.id().equals("music_box")) {
                // a small gilded music box on the front of the stage
                piece(at, Material.DARK_OAK_PLANKS, -0.32f, 0f, -0.24f, 0.64f, 0.42f, 0.48f);
                piece(at, Material.GOLD_BLOCK, -0.36f, 0.42f, -0.28f, 0.72f, 0.08f, 0.56f);
                piece(at, Material.NOTE_BLOCK, -0.18f, 0.5f, -0.14f, 0.36f, 0.28f, 0.28f);
            } else if (r.id().equals("great_doors")) {
                // the Sovereign's altar: a blackstone plinth with a crying-obsidian bowl
                piece(at, Material.POLISHED_BLACKSTONE_BRICKS, -0.5f, 0f, -0.5f, 1.0f, 0.9f, 1.0f);
                piece(at, Material.CHISELED_POLISHED_BLACKSTONE, -0.6f, 0.9f, -0.6f, 1.2f, 0.25f, 1.2f);
                piece(at, Material.CRYING_OBSIDIAN, -0.35f, 1.15f, -0.35f, 0.7f, 0.12f, 0.7f);
            }
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

    /** One block-display piece of an altar prop, offset and sized in blocks from {@code at}; rebuilt with the chunk. */
    private void piece(Location at, Material m, float x, float y, float z, float sx, float sy, float sz) {
        world.spawn(at, org.bukkit.entity.BlockDisplay.class, d -> {
            mark(d);
            d.setBlock(m.createBlockData());
            d.setViewRange(0.8f);
            d.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf()));
        });
    }

    private void label(Location at, String en, String de) {
        label(plugin, at, en, de, this::mark);
    }

    /**
     * One bilingual label: two text displays, each shown only to readers of its language ({@link #refreshLabels}
     * keeps that right for every label made here). {@code mark} tags them for the caller's own cleanup.
     */
    static void label(org.bukkit.plugin.Plugin plugin, Location at, String en, String de,
                      java.util.function.Consumer<Entity> mark) {
        for (int lang = 0; lang < 2; lang++) {
            final boolean german = lang == 1;
            TextDisplay t = at.getWorld().spawn(at, TextDisplay.class, d -> {
                mark.accept(d);
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
        if (sword != null) {
            BossSpawnAccess bosses = AetherServices.bosses();
            sword.tick(() -> bosses != null && bosses.alive("hollow_sun"));
        }
        boolean planted = sword != null && sword.state() == HollowSword.State.PLANTED;
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (planted && dist2(l, SWORD) < 60 * 60) {
                p.spawnParticle(Particle.FLAME, loc(SWORD).add(0, 3.0, 0), 3, 0.3, 1.4, 0.3, 0.01);
                p.spawnParticle(Particle.SMALL_FLAME, loc(SWORD).add(0, 0.3, 0), 6, 1.6, 0.1, 1.6, 0.01);
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
        Location to = standable(goingIn ? in : out);
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
        de.aetherion.core.api.Vfx.puff(mid, Particle.END_ROD, 18, Math.max(0.6, 0.6), null);
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

    /**
     * A boss site was clicked. With the site's vial in the main hand the vial is used up and the boss wakes;
     * without it the site says in one line what it wants. Public so the Quests NPCs could hand a player to a door.
     */
    public void ritual(Player p, String id) {
        Ritual r = ritualById(id);
        ExileVials.Vial vial = ExileVials.bySite(id);
        if (r == null || vial == null) {
            return;
        }
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses == null) {
            p.sendMessage("§cBossEngine is not running on this backend.");
            return;
        }
        switch (id) {
            case "caedmon_sword" -> {
                if (busyRituals.contains(id) || bosses.alive("hollow_sun")
                        || (sword != null && sword.state() != HollowSword.State.PLANTED)) {
                    bar(p, Say.t(p, "§6The blade is gone from the crater. §7The Hollow Sun is awake: look up.",
                            "§6Die Klinge ist fort. §7Die Hollow Sun ist wach: schau nach oben."));
                    return;
                }
            }
            case "music_box" -> {
                if (busyRituals.contains(id) || bosses.alive("hanging_saint")) {
                    bar(p, Say.t(p, "§dThe music box is already playing. §7Seraphine is on her stage.",
                            "§dDie Spieluhr spielt schon. §7Seraphine steht auf ihrer Bühne."));
                    return;
                }
            }
            case "great_doors" -> {
                if (busyRituals.contains(id) || bosses.alive("dungeon_aetherion")) {
                    bar(p, Say.t(p, "§5The bell has rung. §7Aetherion is on the Star Court.",
                            "§5Die Glocke hat geschlagen. §7Aetherion ist auf dem Sternplatz."));
                    return;
                }
            }
            default -> {
            }
        }
        if (!ExileVials.takeFromHand(p, vial.boss())) {
            hint(p, vial);
            return;
        }
        Location at = loc(r.at());
        world.playSound(at, Sound.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(at, Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.8f, 0.6f);
        world.spawnParticle(Particle.ENTITY_EFFECT, at.clone().add(0, Math.min(r.h(), 2.0), 0), 24, 0.4, 0.4, 0.4, 0,
                vial.tint());
        begin(p, r, vial);
    }

    /** No vial in hand: one line in the action bar, at most every few seconds, and the site hums. */
    private void hint(Player p, ExileVials.Vial vial) {
        long now = System.currentTimeMillis();
        Long cool = hintCool.get(p.getUniqueId());
        if (cool != null && now < cool) {
            return;
        }
        hintCool.put(p.getUniqueId(), now + 2500L);
        String name = Say.t(p, vial.nameEn(), vial.nameDe());
        bar(p, Say.t(p, "§7The vial of " + vial.color() + name + " §7is handed in here. §8Hold it and right-click.",
                "§7Hier gibst du die Phiole für " + vial.color() + name + " §7ab. §8In die Hand nehmen, Rechtsklick."));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.6f, 0.7f);
    }

    private static void bar(Player p, String line) {
        p.sendActionBar(Component.text(line));
    }

    private void begin(Player p, Ritual r, ExileVials.Vial vial) {
        if (!p.isOnline()) {
            return;
        }
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(p);
        }
        switch (r.id()) {
            case "caedmon_sword" -> {
                busyRituals.add(r.id());
                sword.rise(() -> {
                    busyRituals.remove(r.id());
                    wake(p, r, vial, "hollow_sun", loc(HOLLOW_SUN_SPAWN));
                });
            }
            case "music_box" -> windMusicBox(p, r, vial);
            case "star_ring" -> portal(p, r, vial, "helios");
            case "the_maw" -> portal(p, r, vial, "world_eater");
            case "great_doors" -> sovereign(p, r, vial);
            default -> {
            }
        }
    }

    /**
     * The music box plays the first bars of the hymn while golden threads climb from it into the rafters, then
     * the house lights die and Seraphine's own show starts on her stage (her director runs the curtain).
     */
    private void windMusicBox(Player p, Ritual r, ExileVials.Vial vial) {
        busyRituals.add(r.id());
        Location at = loc(r.at());
        int ticks = 80;
        for (int i = 0; i < ticks; i += 4) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (k % 20 == 0 && k / 20 < HYMN.length) {
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.9f, HYMN[k / 20]);
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 0.5f, HYMN[k / 20] * 0.5f);
                }
                world.spawnParticle(Particle.NOTE, at.clone().add(0, 1.3, 0), 1, 0.25, 0.1, 0.25, 0.6);
                // the threads: three gold lines from the box up into the rafters, a little longer every beat
                double reach = Math.min(26, 3 + k * 0.4);
                for (int s = 0; s < 3; s++) {
                    double a = s * (Math.PI * 2 / 3) + k * 0.05;
                    for (double y = 1.0; y < reach; y += 1.6) {
                        world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 0.6, y, Math.sin(a) * 0.6),
                                1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 205, 90), 0.9f));
                    }
                }
            }, i);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            busyRituals.remove(r.id());
            wake(p, r, vial, "hanging_saint", loc(SAINT_SPAWN));
        }, ticks);
    }

    /** Calls the boss; if BossEngine refuses, the vial comes back and staff find the reason in the log. */
    private void wake(Player p, Ritual r, ExileVials.Vial vial, String template, Location spawn) {
        BossSpawnAccess bosses = AetherServices.bosses();
        de.aetherion.core.network.ExileHost.unpeaceful(plugin, world);
        boolean ok;
        try {
            ok = bosses != null && bosses.spawn(template, spawn, p.isOnline() ? p : null);
        } catch (RuntimeException ex) {
            ok = false;
            plugin.getLogger().warning("Exile altar " + r.id() + " could not spawn " + template + ": " + ex.getMessage());
        }
        if (ok) {
            return;
        }
        plugin.getLogger().warning("Exile altar " + r.id() + ": BossEngine refused " + template + " at "
                + spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ() + " (awake "
                + (bosses != null && bosses.alive(template)) + ", difficulty " + world.getDifficulty() + ")");
        refund(p, vial);
        if (p.isOnline()) {
            p.sendMessage(Say.t(p, "§8» §7Nothing answers. Your vial is back in your bag. (Staff: see the log.)",
                    "§8» §7Nichts antwortet. Deine Phiole ist zurück im Inventar. (Team: siehe Log.)"));
        }
    }

    private void refund(Player p, ExileVials.Vial vial) {
        if (!p.isOnline()) {
            return;
        }
        for (ItemStack rest : p.getInventory().addItem(ExileVials.create(vial, 1, Say.german(p))).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private void portal(Player p, Ritual r, ExileVials.Vial vial, String arena) {
        BossSpawnAccess bosses = AetherServices.bosses();
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
                result = bosses == null ? "disabled" : bosses.enterArena(p, arena);
            } catch (RuntimeException ex) {
                result = "error";
                plugin.getLogger().warning("Exile portal " + arena + " failed for " + p.getName() + ": " + ex.getMessage());
            }
            if (result == null || result.equals("ok")) {
                return;
            }
            refund(p, vial);
            String why = switch (result.split(":")[0]) {
                case "full" -> Say.t(p, "Every stage of the star is taken. Try again shortly.", "Jede Bühne des Sterns ist belegt. Versuch's gleich wieder.");
                case "cooldown" -> Say.t(p, "The star rests a little longer.", "Der Stern ruht noch ein wenig.");
                case "not-leader" -> Say.t(p, "Only your party leader can open it.", "Nur deine Gruppenleitung kann ihn öffnen.");
                case "already" -> Say.t(p, "You are already inside.", "Du bist schon drin.");
                case "not-built", "disabled", "unsupported" -> Say.t(p, "It will not open yet. (Staff: the boss site isn't set up on this backend.)",
                        "Er öffnet sich noch nicht. (Team: der Boss-Ort ist auf diesem Backend nicht eingerichtet.)");
                default -> Say.t(p, "It will not open right now.", "Er öffnet sich gerade nicht.");
            };
            bar(p, r.color() + Say.t(p, r.nameEn(), r.nameDe()) + " §8» §7" + why + Say.t(p, " §8(vial returned)", " §8(Phiole zurück)"));
        }, 40L);
    }

    /** The castle's bell tower, high over the Great Doors: where the five strokes ring from. */
    private static final double[] BELL = {-41.5, 203.0, 0.5};

    /**
     * Aetherion's vial at the Star Court's gate: the castle bell strikes five times while ash starts falling over
     * the court, the fifth stroke cracks the sky, and the Sovereign comes down onto the court (his own arrival).
     */
    private void sovereign(Player p, Ritual r, ExileVials.Vial vial) {
        busyRituals.add(r.id());
        Location bell = loc(BELL);
        Location court = loc(AETHERION_SPAWN);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player near : world.getPlayers()) {
                    if (near.getLocation().distanceSquared(court) < 140 * 140) {
                        near.playSound(bell, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 2.0f, k == 4 ? 0.7f : 0.5f);
                        if (k == 4) {
                            near.playSound(bell, Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 1.0f, 0.5f);
                        }
                    }
                }
                world.spawnParticle(Particle.WHITE_ASH, court.clone().add(0, 18, 0), 120 + k * 80, 26, 6, 26, 0);
                world.spawnParticle(Particle.ASH, court.clone().add(0, 14, 0), 60 + k * 40, 22, 4, 22, 0);
            }, 10L + i * 24L);
        }
        long fifth = 10L + 4 * 24L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player near : world.getPlayers()) {
                if (near.getLocation().distanceSquared(court) < 140 * 140) {
                    near.showTitle(Title.title(Component.text(Say.t(near, "The fifth stroke.", "Der fünfte Schlag."), NamedTextColor.WHITE),
                            Component.text(Say.t(near, "The Sovereign comes down.", "Der Souverän kommt herab."), NamedTextColor.DARK_PURPLE),
                            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2200), Duration.ofMillis(800))));
                    near.playSound(court, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.4f, 0.6f);
                }
            }
            world.spawnParticle(Particle.FLASH, court.clone().add(0, 40, 0), 3, 4, 2, 4, 0);
        }, fifth);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            busyRituals.remove(r.id());
            wake(p, r, vial, "dungeon_aetherion", court);
        }, fifth + 30L);
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
        hintCool.remove(id);
        gateCool.remove(id);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean questDone(Player p, String questId) {
        QuestProgressAccess quests = AetherServices.quests();
        try {
            return quests != null && quests.isQuestCompleted(p, questId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The nearest feet cell (feet and head free, solid below) in the column, a few blocks up or down. */
    private static Location standable(Location at) {
        World w = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, 4, 5, 6}) {
            int y = at.getBlockY() + dy;
            if (w.getBlockAt(x, y, z).isPassable() && w.getBlockAt(x, y + 1, z).isPassable()
                    && !w.getBlockAt(x, y - 1, z).isPassable()) {
                Location l = at.clone();
                l.setY(y);
                return l;
            }
        }
        return at;
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
        if (sword != null) {
            sword.clear();
        }
    }
}
