package de.aetherion.quests.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Presentation for living (FancyNpcs) hosts — gear, nametag, chat, optional skin.
 */
public final class LivingNpcProfile {

    private static final Map<String, LivingNpcProfile> BY_ID = new HashMap<>();

    /*
     * Skins: every living host wears an original cast skin painted for Aetherion
     * (skins/cast/<id>.png, see LivingNpcSkins). The old auto-assigned list of real
     * creators' Mojang accounts is gone on purpose — players must never recognise a
     * famous face on a town NPC. skinUsername(...) stays for a deliberate, explicit
     * override only; nothing is assigned by default.
     */

    static {
        // Hub starters
        put(p("egon", "Equipper", NamedTextColor.GREEN, "§a")
                .hand(Material.IRON_CHESTPLATE).leather(Color.fromRGB(72, 96, 110)).fullLeather()
                .skinFile("worker.png"));
        put(p("quartermaster", "Supply Desk", NamedTextColor.AQUA, "§b")
                .hand(Material.FILLED_MAP).leather(Color.fromRGB(36, 58, 92)).fullLeather()
                .skinFile("guard.png"));
        put(p("ledger", "Lesson Clerk", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.WRITABLE_BOOK).leather(Color.fromRGB(92, 48, 64))
                .chest().boots().slim().skinFile("alex_slim.png"));

        // Starter trainers
        put(p("hunter", "Hunt Lead", NamedTextColor.DARK_GREEN, "§2")
                .hand(Material.BOW).leather(Color.fromRGB(78, 58, 40)).fullLeather()
                .skinFile("scout.png"));
        put(p("lumberjack", "Timber", NamedTextColor.GOLD, "§6")
                .hand(Material.IRON_AXE).leather(Color.fromRGB(110, 78, 42)).chest().boots()
                .skinFile("worker.png"));
        put(p("farmer", "Fields", NamedTextColor.YELLOW, "§e")
                .hand(Material.IRON_HOE).leather(Color.fromRGB(120, 140, 60)).chest().boots()
                .skinFile("farmer.png"));
        put(p("craftsman", "Market Forge", NamedTextColor.GRAY, "§7")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(90, 90, 95)).chest().legs().boots()
                .skinFile("miner.png"));
        put(p("collector", "Shinies", NamedTextColor.GOLD, "§6")
                .hand(Material.GOLD_NUGGET).leather(Color.fromRGB(160, 120, 40)).chest().boots()
                .skinFile("rogue.png"));
        put(p("fisher", "Dock Tackle", NamedTextColor.BLUE, "§9")
                .hand(Material.FISHING_ROD).leather(Color.fromRGB(40, 70, 110)).chest().boots()
                .skinFile("sailor.png"));
        put(p("fishmonger", "Dock Stall", NamedTextColor.AQUA, "§b")
                .hand(Material.COD).leather(Color.fromRGB(35, 85, 100)).fullLeather()
                .skinFile("sailor.png"));
        put(p("foreman", "Shaft Desk", NamedTextColor.GRAY, "§7")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(70, 70, 75)).fullLeather()
                .skinFile("miner.png"));
        put(p("surveyor", "Blueprint Desk", NamedTextColor.AQUA, "§b")
                .hand(Material.FILLED_MAP).leather(Color.fromRGB(55, 85, 95)).fullLeather()
                .skinFile("scholar.png"));
        put(p("merchant", "Coin Desk", NamedTextColor.GREEN, "§a")
                .hand(Material.EMERALD).leather(Color.fromRGB(30, 90, 50)).fullLeather()
                .skinFile("scholar.png"));
        put(p("lark", "Pet Brief", NamedTextColor.AQUA, "§b")
                .hand(Material.SNOWBALL).leather(Color.fromRGB(90, 140, 150)).chest().boots()
                .skinFile("scout.png"));

        // Boss quest givers
        put(p("miner", "Deep Claims", NamedTextColor.DARK_GRAY, "§8")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(55, 55, 55)).fullLeather()
                .skinFile("miner.png"));
        put(p("chicken_keeper", "Coop Desk", NamedTextColor.YELLOW, "§e")
                .hand(Material.EGG).leather(Color.fromRGB(200, 180, 120)).chest().boots()
                .skinFile("farmer.png"));
        put(p("tollkeeper", "Bridge Cut", NamedTextColor.GOLD, "§6")
                .hand(Material.GOLDEN_AXE).leather(Color.fromRGB(120, 90, 40)).chest().legs().boots()
                .skinFile("guard.png"));
        put(p("dockhand", "Ink Dock", NamedTextColor.DARK_AQUA, "§3")
                .hand(Material.INK_SAC).leather(Color.fromRGB(35, 55, 70)).chest().boots()
                .skinFile("sailor.png"));
        put(p("ash_scout", "Ash Range", NamedTextColor.RED, "§c")
                .hand(Material.BOW).leather(Color.fromRGB(90, 45, 35)).fullLeather()
                .skinFile("rogue.png"));
        put(p("colossus_scholar", "Stone Theory", NamedTextColor.WHITE, "§f")
                .hand(Material.BOOK).leather(Color.fromRGB(200, 200, 210)).chest().boots()
                .skinFile("scholar.png"));
        put(p("veil_priest", "Veil Rites", NamedTextColor.DARK_PURPLE, "§5")
                .hand(Material.ENDER_PEARL).leather(Color.fromRGB(40, 20, 55))
                .fullLeather().slim().skinFile("mystic.png"));
        put(p("patch_intern", "Ticket Desk", NamedTextColor.AQUA, "§b")
                .hand(Material.WRITTEN_BOOK).leather(Color.fromRGB(80, 100, 120)).chest().boots()
                .skinFile("worker.png"));
        put(p("void_janitor", "Lost & Found", NamedTextColor.GRAY, "§7")
                .hand(Material.BRUSH).leather(Color.fromRGB(90, 95, 100)).boots()
                .skinFile("testskin.png"));
        put(p("fuse", "Overtime", NamedTextColor.GOLD, "§6")
                .hand(Material.FLINT_AND_STEEL).leather(Color.fromRGB(150, 70, 30)).chest().boots()
                .skinFile("miner.png"));
        put(p("claims_adjuster", "Denied Desk", NamedTextColor.YELLOW, "§e")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(130, 110, 70)).chest().legs().boots()
                .skinFile("scholar.png"));
        put(p("repo_agent", "Collections", NamedTextColor.DARK_GRAY, "§8")
                .hand(Material.BOOK).leather(Color.fromRGB(40, 40, 48)).fullLeather()
                .skinFile("guard.png"));

        // Teaching cast
        put(p("vex", "Drill Sergeant", NamedTextColor.RED, "§c")
                .hand(Material.IRON_SWORD).leather(Color.fromRGB(90, 40, 40)).fullLeather()
                .skinFile("guard.png"));
        put(p("booster_tutor", "Booster Tutor", NamedTextColor.GOLD, "§6")
                .hand(Material.ANVIL).leather(Color.fromRGB(160, 110, 48)).fullLeather()
                .skinFile("worker.png"));
        put(p("rite_keeper", "Border Rites", NamedTextColor.RED, "§c")
                .hand(Material.POTION).leather(Color.fromRGB(70, 40, 45)).fullLeather()
                .skinFile("guard.png"));
        put(p("arena_proctor", "Arena Rites", NamedTextColor.GOLD, "§6")
                .hand(Material.GLASS_BOTTLE).leather(Color.fromRGB(120, 90, 50)).fullLeather()
                .skinFile("scholar.png"));
        put(p("rook", "Bone Lesson", NamedTextColor.WHITE, "§f")
                .hand(Material.BONE).leather(Color.fromRGB(180, 180, 185)).chest().boots()
                .skinFile("scholar.png"));

        // World cast
        put(p("gate_warden", "Closed Road", NamedTextColor.DARK_AQUA, "§3")
                .hand(Material.IRON_DOOR).leather(Color.fromRGB(50, 60, 70)).fullLeather()
                .skinFile("guard.png"));
        put(p("bench_cynic", "Sidewalk", NamedTextColor.GRAY, "§7")
                .hand(Material.STICK).leather(Color.fromRGB(85, 75, 65)).chest().boots()
                .skinFile("worker.png"));
        put(p("dust", "Gravel Beat", NamedTextColor.YELLOW, "§e")
                .hand(Material.GRAVEL).leather(Color.fromRGB(140, 130, 110)).chest().boots()
                .skinFile("farmer.png"));

        // Flavor / loops
        put(p("fry_gossip", "Grease Wire", NamedTextColor.GOLD, "§6")
                .hand(Material.COOKED_CHICKEN).leather(Color.fromRGB(170, 110, 50)).chest().boots()
                .slim().skinFile("alex_slim.png"));
        put(p("dock_whisper", "Tide Mutter", NamedTextColor.DARK_AQUA, "§3")
                .hand(Material.NAUTILUS_SHELL).leather(Color.fromRGB(30, 60, 80)).chest().boots()
                .skinFile("sailor.png"));
        put(p("larder", "Pantry", NamedTextColor.YELLOW, "§e")
                .hand(Material.BREAD).leather(Color.fromRGB(150, 120, 70)).chest().boots()
                .skinFile("farmer.png"));
        put(p("pet_scout", "Catch Brief", NamedTextColor.GREEN, "§a")
                .hand(Material.LEAD).leather(Color.fromRGB(70, 110, 60)).chest().boots()
                .skinFile("scout.png"));
        put(p("ore_ledger", "Coal Audit", NamedTextColor.DARK_GRAY, "§8")
                .hand(Material.COAL).leather(Color.fromRGB(45, 45, 50)).chest().legs().boots()
                .skinFile("miner.png"));
        put(p("timber_clerk", "Stump Census", NamedTextColor.GOLD, "§6")
                .hand(Material.OAK_LOG).leather(Color.fromRGB(100, 75, 45)).chest().boots()
                .skinFile("worker.png"));
        put(p("canopy_clerk", "Canopy Sample", NamedTextColor.DARK_GREEN, "§2")
                .hand(Material.SPRUCE_LOG).leather(Color.fromRGB(55, 90, 50)).chest().boots()
                .skinFile("worker.png"));
        put(p("dock_scaler", "Scale Desk", NamedTextColor.AQUA, "§b")
                .hand(Material.COD).leather(Color.fromRGB(50, 85, 105)).chest().boots()
                .skinFile("sailor.png"));
        put(p("root_cellar", "Mill Keeper", NamedTextColor.GREEN, "§a")
                .hand(Material.CARROT).leather(Color.fromRGB(90, 110, 50)).chest().boots()
                .skinFile("farmer.png"));
        put(p("sphere_proctor", "Sphere Rules", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.SNOWBALL).leather(Color.fromRGB(120, 90, 140)).chest().boots()
                .skinFile("mystic.png"));
        put(p("quarry_broker", "Guild Brick", NamedTextColor.GRAY, "§7")
                .hand(Material.COBBLESTONE).leather(Color.fromRGB(95, 95, 90)).fullLeather()
                .skinFile("miner.png"));
        put(p("slag_poet", "Vein Verse", NamedTextColor.RED, "§c")
                .hand(Material.RAW_IRON).leather(Color.fromRGB(110, 60, 50)).chest().boots()
                .skinFile("rogue.png"));
        put(p("bait_theory", "Bait Philosophy", NamedTextColor.BLUE, "§9")
                .hand(Material.FISHING_ROD).leather(Color.fromRGB(40, 75, 95)).chest().boots()
                .skinFile("sailor.png"));

        // Tiny marketplace ambience (Anker square) — dialog only
        put(p("stall_crumb", "Bread Stall", NamedTextColor.YELLOW, "§e")
                .hand(Material.BREAD).leather(Color.fromRGB(160, 120, 70)).chest().boots()
                .skinFile("farmer.png"));
        put(p("stall_tack", "Odds & Ends", NamedTextColor.GRAY, "§7")
                .hand(Material.IRON_NUGGET).leather(Color.fromRGB(90, 90, 95)).chest().boots()
                .skinFile("worker.png"));
        put(p("stall_brine", "Salt Jar", NamedTextColor.AQUA, "§b")
                .hand(Material.KELP).leather(Color.fromRGB(50, 90, 100)).chest().boots()
                .slim().skinFile("alex_slim.png"));

        // Harlow — hub Farm Isle guide, far end of the barn (FancyNpc)
        put(p("farm_isle_guide", "Farm Isle Road", NamedTextColor.GREEN, "§a")
                .hand(Material.WHEAT).leather(Color.fromRGB(110, 130, 55)).chest().boots()
                .skinFile("farmer.png"));

        // Harbour → Forage Isle pad guide
        put(p("forage_pad_guide", "Forage Pad", NamedTextColor.DARK_GREEN, "§2")
                .hand(Material.SPRUCE_SAPLING).leather(Color.fromRGB(70, 100, 55)).chest().boots()
                .skinFile("worker.png"));

        // Eldervale mining island welcome
        put(p("eldervale_welcome", "Eldervale Host", NamedTextColor.AQUA, "§b")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(70, 85, 95)).chest().boots()
                .skinFile("worker.png"));
        put(p("eldervale_upgrade", "Blueprint Forge", NamedTextColor.GOLD, "§6")
                .hand(Material.ANVIL).leather(Color.fromRGB(90, 70, 45)).fullLeather()
                .skinFile("worker.png"));
        put(p("isle_clerk", "Island Deed", NamedTextColor.GREEN, "§a")
                .hand(Material.OAK_SAPLING).leather(Color.fromRGB(70, 120, 55)).chest().boots()
                .skinFile("farmer.png"));
        put(p("dungeon_gate", "Dungeon Gate", NamedTextColor.DARK_PURPLE, "§5")
                .hand(Material.ENDER_PEARL).leather(Color.fromRGB(55, 30, 80)).fullLeather()
                .skinFile("mystic.png"));

        // Prototypes
        put(p("living_test", "Prototype", NamedTextColor.AQUA, "§b")
                .hand(Material.STICK).leather(Color.fromRGB(80, 180, 200)).fullLeather()
                .skinFile("testskin.png"));
        put(p("rivet", "Dock Riveter", NamedTextColor.GOLD, "§6")
                .hand(Material.IRON_PICKAXE).leather(Color.fromRGB(184, 115, 51)).fullLeather()
                .skinFile("worker.png"));
        put(p("bar_whisper", "Aether Meter", NamedTextColor.AQUA, "§b")
                .hand(Material.EXPERIENCE_BOTTLE).leather(Color.fromRGB(55, 90, 120)).chest().boots()
                .skinFile("scholar.png"));
        put(p("vince", "House Floor", NamedTextColor.GOLD, "§6")
                .hand(Material.CLOCK).leather(Color.fromRGB(28, 24, 32)).fullLeather()
                .skinFile("scholar.png"));
        put(p("liquidator", "Crystal Desk", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.AMETHYST_SHARD).leather(Color.fromRGB(72, 36, 110))
                .fullLeather().slim().skinFile("mystic.png"));
        put(p("amethyst_mines_guide", "Amethyst Mines", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.AMETHYST_CLUSTER).leather(Color.fromRGB(90, 40, 140))
                .fullLeather().slim().skinFile("mystic.png"));
    }

    static {
        // Idea NPCs (/questnpc extras) — cast skins carry the look, no leather needed.
        put(p("town_crier", "Town Crier", NamedTextColor.GOLD, "§6")
                .hand(Material.BELL).leather(Color.fromRGB(122, 36, 48)).chest().boots()
                .skinFile("scholar.png"));
        put(p("street_sweeper", "Keeps It Clean", NamedTextColor.GRAY, "§7")
                .hand(Material.BRUSH).leather(Color.fromRGB(138, 122, 90)).chest().boots()
                .skinFile("worker.png"));
        put(p("lamp_lighter", "Lamp Round", NamedTextColor.YELLOW, "§e")
                .hand(Material.LANTERN).leather(Color.fromRGB(30, 42, 58)).chest().boots()
                .skinFile("scout.png"));
    }

    static {
        // Origin island cast (SAITAMA pass) — cast skins carry the look; leather is the pre-sign fallback.
        put(p("harbourmaster", "Tallyman", NamedTextColor.DARK_AQUA, "§3")
                .hand(Material.WRITABLE_BOOK).leather(Color.fromRGB(30, 44, 78)).chest().legs().boots()
                .skinFile("guard.png"));
        put(p("crane_hand", "Crane Hand", NamedTextColor.GOLD, "§6")
                .hand(Material.CHAIN).leather(Color.fromRGB(110, 74, 40)).chest().legs().boots()
                .skinFile("worker.png"));
        put(p("lighthouse_keeper", "Lighthouse Keeper", NamedTextColor.YELLOW, "§e")
                .hand(Material.SPYGLASS).leather(Color.fromRGB(214, 176, 40)).chest().boots().slim()
                .skinFile("sailor.png"));
        put(p("shanty_busker", "Shanty Busker", NamedTextColor.AQUA, "§b")
                .hand(Material.GOAT_HORN).leather(Color.fromRGB(40, 70, 120)).chest().boots()
                .skinFile("sailor.png"));
        put(p("beekeeper", "Old Mead House", NamedTextColor.GOLD, "§6")
                .hand(Material.HONEYCOMB).leather(Color.fromRGB(226, 196, 96)).chest().boots().slim()
                .skinFile("farmer.png"));
        put(p("painter", "Plein-Air Painter", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.PAINTING).leather(Color.fromRGB(110, 60, 120)).chest().boots().slim()
                .skinFile("scholar.png"));
        put(p("hermit", "Whisperwood Hermit", NamedTextColor.DARK_GREEN, "§2")
                .hand(Material.STICK).leather(Color.fromRGB(70, 92, 52)).chest().legs().boots()
                .skinFile("mystic.png"));
        put(p("bookie", "Wagers on Legs", NamedTextColor.GREEN, "§a")
                .hand(Material.PAPER).leather(Color.fromRGB(60, 52, 44)).chest().legs().boots()
                .skinFile("rogue.png"));
        put(p("mushroom_forager", "Mushroom Forager", NamedTextColor.RED, "§c")
                .hand(Material.RED_MUSHROOM).leather(Color.fromRGB(90, 110, 60)).chest().boots()
                .skinFile("scout.png"));
        put(p("lost_tourist", "Lost Tourist", NamedTextColor.YELLOW, "§e")
                .hand(Material.MAP).leather(Color.fromRGB(200, 170, 120)).chest().boots()
                .skinFile("alex.png"));
    }

    static {
        // Main story "Das Schlaflied" (/questnpc story) — not placed by default.
        put(p("ysolde", "Archive Desk", NamedTextColor.DARK_AQUA, "§3")
                .hand(Material.WRITTEN_BOOK).leather(Color.fromRGB(46, 62, 70)).fullLeather()
                .slim().skinFile("alex_slim.png"));
        put(p("berit", "Edge of Town", NamedTextColor.GOLD, "§6")
                .hand(Material.CANDLE).leather(Color.fromRGB(122, 92, 74)).chest().boots()
                .slim().skinFile("alex_slim.png"));
        put(p("nell", "Harbour Lane", NamedTextColor.YELLOW, "§e")
                .hand(Material.LANTERN).leather(Color.fromRGB(196, 150, 90)).chest().boots()
                .slim().skinFile("alex_slim.png"));
        put(p("hesper", "Anchor Camp", NamedTextColor.GRAY, "§7")
                .hand(Material.CHAIN).leather(Color.fromRGB(58, 58, 64)).fullLeather()
                .skinFile("guard.png"));
        put(p("ferryman", "Veil Quay", NamedTextColor.DARK_GRAY, "§8")
                .hand(Material.OAK_BOAT).leather(Color.fromRGB(150, 156, 160)).fullLeather()
                .skinFile("sailor.png"));
        // Act III — the exile island. The Faded wear washed-out cast skins (skins/cast/exile_*.png).
        put(p("orrin", "Observatory of the Sun", NamedTextColor.GOLD, "§6")
                .hand(Material.CLOCK).leather(Color.fromRGB(92, 70, 48)).fullLeather()
                .skinFile("scholar.png"));
        put(p("exile_lamplighter", "Faded · The Court", NamedTextColor.GRAY, "§7")
                .hand(Material.LANTERN).leather(Color.fromRGB(170, 166, 160)).chest().boots()
                .skinFile("worker.png"));
        put(p("exile_sentry", "Faded · Sun Guard", NamedTextColor.GRAY, "§7")
                .hand(Material.GOLDEN_SWORD).leather(Color.fromRGB(200, 186, 150)).fullLeather()
                .skinFile("guard.png"));
        put(p("exile_usher", "Faded · Little Theatre", NamedTextColor.GRAY, "§7")
                .hand(Material.CANDLE).leather(Color.fromRGB(150, 110, 120)).chest().boots()
                .skinFile("rogue.png"));
        put(p("exile_gardener", "Faded · Root-Ground", NamedTextColor.GRAY, "§7")
                .hand(Material.BONE_MEAL).leather(Color.fromRGB(150, 168, 140)).chest().boots()
                .skinFile("farmer.png"));
        put(p("exile_mother", "Faded · The Court", NamedTextColor.GRAY, "§7")
                .hand(Material.LEATHER_BOOTS).leather(Color.fromRGB(176, 160, 168)).chest().boots()
                .slim().skinFile("alex_slim.png"));
        put(p("exile_keeper", "Faded · The Last Bell", NamedTextColor.GRAY, "§7")
                .hand(Material.GLASS_BOTTLE).leather(Color.fromRGB(150, 140, 126)).chest().boots()
                .skinFile("worker.png"));
    }

    private static Builder p(String id, String subtitle, NamedTextColor color, String chat) {
        return new Builder(id, subtitle, color, chat);
    }

    private static void put(Builder builder) {
        BY_ID.put(builder.id, builder.build());
    }

    private final String id;
    private final String subtitle;
    private final NamedTextColor nameColor;
    private final String chatPrefix;
    private final Material hand;
    private final Color leather;
    private final boolean chest;
    private final boolean legs;
    private final boolean boots;
    private final boolean slim;
    private final String skinFile;
    private final String skinUsername;

    private LivingNpcProfile(Builder b) {
        this.id = b.id;
        this.subtitle = b.subtitle;
        this.nameColor = b.nameColor;
        this.chatPrefix = b.chatPrefix;
        this.hand = b.hand;
        this.leather = b.leather;
        this.chest = b.chest;
        this.legs = b.legs;
        this.boots = b.boots;
        this.slim = b.slim;
        this.skinFile = b.skinFile;
        this.skinUsername = b.skinUsername;
    }

    public static boolean isLiving(String npcId) {
        return npcId != null && BY_ID.containsKey(npcId.toLowerCase(Locale.ROOT).trim());
    }

    public static Set<String> livingIds() {
        return Set.copyOf(BY_ID.keySet());
    }

    public static LivingNpcProfile of(String npcId) {
        if (npcId == null) {
            return null;
        }
        return BY_ID.get(npcId.toLowerCase(Locale.ROOT).trim());
    }

    public String id() {
        return id;
    }

    public String subtitle() {
        return subtitle;
    }

    public NamedTextColor nameColor() {
        return nameColor;
    }

    public String chatPrefix() {
        return chatPrefix;
    }

    public boolean slim() {
        return slim;
    }

    public String skinFile() {
        return skinFile;
    }

    /** Explicit Mojang username override (null by default — cast skins are used instead). */
    public String skinUsername() {
        return skinUsername;
    }

    /** Name colour refined for in-world chrome (plate, bubble rim, focused reply, quest card). */
    public net.kyori.adventure.text.format.TextColor accent() {
        return de.aetherion.quests.talk.TalkChrome.accent(nameColor);
    }

    /**
     * Two quiet lines over the head: name in the accent, role in small caps. The talk bubble
     * floats right above it, so the plate also names the speaker while they talk.
     */
    public Component nametag(String displayName) {
        String name = displayName == null ? id : displayName;
        return de.aetherion.quests.talk.TalkChrome.nameplate(name, accent(), subtitle);
    }

    public String formatChatLine(String displayName, String line) {
        String name = displayName == null ? id : displayName;
        return chatPrefix + name + " §8⟫ §f" + line;
    }

    /** Speak as this living NPC (colored name); falls back to gold for unknown ids. */
    public static void say(org.bukkit.entity.Player player, QuestNPC npc, String line) {
        if (player == null || npc == null || line == null) {
            return;
        }
        enqueueSay(player, npc.getId(), npc.getName(), line);
    }

    public static void say(org.bukkit.entity.Player player, String npcId, String displayName, String line) {
        if (player == null || line == null) {
            return;
        }
        enqueueSay(player, npcId, displayName, line);
    }

    /**
     * Queue a line on the player's speech clock ({@link de.aetherion.quests.dialog.DialogPace}):
     * it starts once whatever is being said has typed out and had its read gap — same rhythm
     * at the start of a conversation and in the middle of it.
     */
    private static void enqueueSay(
            org.bukkit.entity.Player player,
            String npcId,
            String displayName,
            String line
    ) {
        de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
        if (plugin == null) {
            sayNow(player, npcId, displayName, line);
            return;
        }
        String filled = de.aetherion.quests.talk.TalkText.fill(player, line);
        long delay = de.aetherion.quests.dialog.DialogPace.reserve(player, filled);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sayNow(player, npcId, displayName, line);
            }
        }, delay);
    }

    private static void sayNow(
            org.bukkit.entity.Player player,
            String npcId,
            String displayName,
            String line
    ) {
        String filled = de.aetherion.quests.talk.TalkText.fill(player, line);
        LivingNpcProfile profile = of(npcId);
        if (profile != null) {
            player.sendMessage(profile.formatChatLine(displayName, filled));
            NpcPresence.cue(player, npcId);
            // Same line, spoken in-world: bubble above the NPC for this player only.
            de.aetherion.quests.talk.TalkUx talk = de.aetherion.quests.talk.TalkUx.get();
            if (talk != null) {
                talk.line(player, npcId, displayName, filled);
            }
            return;
        }
        String name = displayName == null || displayName.isBlank() ? "NPC" : displayName;
        player.sendMessage("§6" + name + " §8⟫ §f" + filled);
    }

    public ItemStack handItem() {
        return hand == null ? null : new ItemStack(hand);
    }

    public ItemStack chestItem() {
        return chest ? dyed(Material.LEATHER_CHESTPLATE) : null;
    }

    public ItemStack legsItem() {
        return legs ? dyed(Material.LEATHER_LEGGINGS) : null;
    }

    public ItemStack bootsItem() {
        return boots ? dyed(Material.LEATHER_BOOTS) : null;
    }

    private ItemStack dyed(Material piece) {
        if (leather == null) {
            return new ItemStack(piece);
        }
        ItemStack stack = new ItemStack(piece);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(leather);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Builder {
        private final String id;
        private final String subtitle;
        private final NamedTextColor nameColor;
        private final String chatPrefix;
        private Material hand;
        private Color leather;
        private boolean chest;
        private boolean legs;
        private boolean boots;
        private boolean slim;
        private String skinFile;
        private String skinUsername;

        private Builder(String id, String subtitle, NamedTextColor nameColor, String chatPrefix) {
            this.id = id;
            this.subtitle = subtitle;
            this.nameColor = nameColor;
            this.chatPrefix = chatPrefix;
        }

        private Builder hand(Material material) {
            this.hand = material;
            return this;
        }

        private Builder leather(Color color) {
            this.leather = color;
            return this;
        }

        private Builder chest() {
            this.chest = true;
            return this;
        }

        private Builder legs() {
            this.legs = true;
            return this;
        }

        private Builder boots() {
            this.boots = true;
            return this;
        }

        private Builder fullLeather() {
            this.chest = true;
            this.legs = true;
            this.boots = true;
            return this;
        }

        private Builder slim() {
            this.slim = true;
            return this;
        }

        private Builder skinFile(String file) {
            this.skinFile = file;
            return this;
        }

        private Builder skinUsername(String username) {
            this.skinUsername = username;
            return this;
        }

        private LivingNpcProfile build() {
            return new LivingNpcProfile(this);
        }
    }
}
