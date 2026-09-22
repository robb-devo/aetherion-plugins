package de.aetherion.quests.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

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

    /**
     * Distinct Mojang accounts — FancyNpcs loads these without a MineSkin API key.
     * Custom PNGs need mineskin_api_key; usernames use Mojang and stay unique per NPC.
     */
    private static int SKIN_SEQ = 0;
    private static final String[] SKIN_USERNAMES = {
            "Grian", "MumboJumbo", "GoodTimesWithScar", "PearlescentMoon", "GeminiTay",
            "impulseSV", "BdoubleO100", "EthosLab", "VintageBeef", "Docm77",
            "Xisuma", "Cubfan135", "TangoTek", "ZombieCleo", "FalseSymmetry",
            "iJevin", "Keralis", "xBCrafted", "Welsknight", "Stressmonster101",
            "Iskall85", "Rendog", "JoeHills", "Hypnotizd", "ZedaphPlays",
            "CaptainSparklez", "DanTDM", "SSundee", "JeromeASF", "SkyDoesMinecraft",
            "PopularMMOs", "SmallishBeans", "SolidarityGaming", "InTheLittleWood",
            "Smajor1995", "Pixlriffs", "fWhip", "HBomb94", "Shubble",
            "Punz", "Purpled", "Sapnap", "GeorgeNotFound", "BadBoyHalo",
            "Antfrost", "FoolishG", "TinaKitten", "Michaelmcchill", "Awesamdude",
            "TommyInnit", "Tubbo", "Ranboo", "WilburSoot", "Ph1LzA",
            "Technoblade", "Quackity", "KarlJacobs", "Fundy", "Nihachu",
            "jeb_", "Dinnerbone", "Grumm", "Notch", "Seapeekay",
            "Sneegsnag", "ConnorEatsPants", "Slimecicle", "KaraCorvus", "KatherineElizabeth",
            "Sykkuno", "Ludwig", "QTCinderella", "Valkyrae", "Pokimane"
    };

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
                .hand(Material.IRON_INGOT).leather(Color.fromRGB(90, 90, 95)).chest().legs().boots()
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

        // Hub farm portal guide (FancyNpc)
        put(p("farm_isle_guide", "Isle Gate", NamedTextColor.GREEN, "§a")
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
        // Amethyst Mines (aether_veins) guide — place on Elder Vale Mining Island (FancyNPC).
        put(p("amethyst_mines_guide", "Amethyst Mines", NamedTextColor.LIGHT_PURPLE, "§d")
                .hand(Material.AMETHYST_CLUSTER).leather(Color.fromRGB(90, 40, 130)).fullLeather()
                .slim().skinFile("mystic.png"));
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
                .fullLeather().slim().skinFile("mystic.png")
                .skinUsername("PearlescentMoon"));
    }

    private static Builder p(String id, String subtitle, NamedTextColor color, String chat) {
        return new Builder(id, subtitle, color, chat);
    }

    private static void put(Builder builder) {
        if (builder.skinUsername == null || builder.skinUsername.isBlank()) {
            builder.skinUsername = SKIN_USERNAMES[SKIN_SEQ++ % SKIN_USERNAMES.length];
        }
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

    /** Mojang username for FancyNpcs — unique look without MineSkin file upload. */
    public String skinUsername() {
        return skinUsername;
    }

    public Component nametag(String displayName) {
        String name = displayName == null ? id : displayName;
        return Component.text(name, nameColor, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(subtitle, NamedTextColor.GRAY));
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

    private static final java.util.Map<java.util.UUID, Integer> SAY_QUEUES = new java.util.concurrent.ConcurrentHashMap<>();

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
        java.util.UUID id = player.getUniqueId();
        int slot = SAY_QUEUES.merge(id, 1, Integer::sum) - 1;
        long delay = slot * de.aetherion.quests.dialog.DialogPace.LINE_GAP_TICKS;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                SAY_QUEUES.remove(id);
                return;
            }
            sayNow(player, npcId, displayName, line);
            SAY_QUEUES.compute(id, (k, v) -> {
                if (v == null || v <= 1) {
                    return null;
                }
                return v - 1;
            });
        }, delay);
    }

    private static void sayNow(
            org.bukkit.entity.Player player,
            String npcId,
            String displayName,
            String line
    ) {
        LivingNpcProfile profile = of(npcId);
        if (profile != null) {
            player.sendMessage(profile.formatChatLine(displayName, line));
            return;
        }
        String name = displayName == null || displayName.isBlank() ? "NPC" : displayName;
        player.sendMessage("§6" + name + " §8⟫ §f" + line);
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
