package de.aetherion.mining.isle;

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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Deep Forge. Brann Emberlock turns mining output into <b>Forge Marks</b>: permanent upgrades
 * that live on the miner, not on a tool, so they survive every pickaxe swap. There are five lines:
 * Tempered Head (break speed), Assay Plate (ore Fortune), Lamp Core (Crystal Finds), Seam Hook
 * (shorter Seam Chain) and Heat Ward (hazards).
 *
 * <p>Every mark is paid in compressed ore, coins and, higher up, specimens. The top Assay Plate
 * wants a Heartstone, which only grows in the Amethyst Mine. There is no second currency.
 * <b>Forge Reputation</b> is earned by forging here, by blueprint upgrades at the Forgehand upstairs,
 * by contracts and by Stonejaw jaws. It gates the higher tiers.
 *
 * <p>The forge also turns out two consumables: the Prospector's Flare (every ore within 10 blocks
 * glows through the rock, only for you) and the Canary Cage (warnings, hazard shield, no dark).
 */
public final class ForgeWorks implements Listener {

    public enum Rank {
        VISITOR("Visitor", "§7", 0L),
        APPRENTICE("Apprentice", "§f", 100L),
        JOURNEYMAN("Journeyman", "§a", 500L),
        SMITH("Smith", "§e", 1_500L),
        MASTER("Master Smith", "§6", 4_000L),
        FORGELORD("Forgelord", "§c", 10_000L);

        final String display;
        final String color;
        final long from;

        Rank(String display, String color, long from) {
            this.display = display;
            this.color = color;
            this.from = from;
        }

        public String colored() {
            return color + display;
        }

        public long from() {
            return from;
        }

        static Rank of(long rep) {
            Rank best = VISITOR;
            for (Rank rank : values()) {
                if (rep >= rank.from) {
                    best = rank;
                }
            }
            return best;
        }
    }

    /** One tier of a mark: item costs by Items id (compressed/compacted) or plain material, coins, specimens. */
    public record Cost(Map<String, Integer> items, Map<Material, Integer> plain, long coins, Grade specimen, int specimens,
                       Rank rank) {
    }

    public enum Mark {
        TEMPERED_HEAD("Tempered Head", Material.IRON_PICKAXE, "§f", "break speed on ore (never opens a gate)", 5),
        ASSAY_PLATE("Assay Plate", Material.GOLD_INGOT, "§6", "ore Fortune on Eldervale and in the Amethyst Mine", 5),
        LAMP_CORE("Lamp Core", Material.REDSTONE_LAMP, "§c", "Crystal Find chance", 3),
        SEAM_HOOK("Seam Hook", Material.CHAIN, "§a", "shorter Seam Chain (bursts sooner)", 3),
        HEAT_WARD("Heat Ward", Material.MAGMA_CREAM, "§e", "less hazard damage: heat, cave-ins, Shardling zaps", 3);

        final String display;
        final Material icon;
        final String color;
        final String blurb;
        final int max;

        Mark(String display, Material icon, String color, String blurb, int max) {
            this.display = display;
            this.icon = icon;
            this.color = color;
            this.blurb = blurb;
            this.max = max;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String display() {
            return display;
        }

        public String colored() {
            return color + display;
        }

        public Material icon() {
            return icon;
        }

        public int max() {
            return max;
        }

        public String blurb() {
            return blurb;
        }

        /** Effect line for a level (0 = none). */
        public String effect(int level) {
            if (level <= 0) {
                return "§8no effect yet";
            }
            return switch (this) {
                case TEMPERED_HEAD -> "§e+" + (4 * level) + " break-speed power";
                case ASSAY_PLATE -> "§e+" + (4 * level) + " ore Fortune";
                case LAMP_CORE -> "§dCrystal Finds ×" + MineText.num(1.0d + 0.08d * level + (level >= 3 ? 0.01d : 0.0d));
                case SEAM_HOOK -> "§aSeam Chain −" + level;
                case HEAT_WARD -> "§6−" + (20 * level) + "% hazard damage";
            };
        }

        public static Mark byId(String id) {
            if (id == null) {
                return null;
            }
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public enum Tool {
        FLARE("Prospector's Flare", Material.GLOWSTONE_DUST, "§e", Rank.APPRENTICE, Map.of(Material.COAL, 32, Material.REDSTONE, 8),
                List.of("§7Snap it and every ore within §f10 blocks", "§7glows through the rock for §f15s§7.",
                        "§8Only you see the glow. Works on Eldervale", "§8and in the Amethyst Mine.")),
        CANARY("Canary Cage", Material.YELLOW_DYE, "§6", Rank.VISITOR, Map.of(Material.COPPER_INGOT, 16, Material.GOLD_INGOT, 4),
                List.of("§7For §f10 minutes§7: early cave-in warnings,", "§7§f−50% §7hazard damage and no Undercroft dark.",
                        "§8He sings when the rock is about to go."));

        final String display;
        final Material icon;
        final String color;
        final Rank rank;
        final Map<Material, Integer> cost;
        final List<String> lore;

        Tool(String display, Material icon, String color, Rank rank, Map<Material, Integer> cost, List<String> lore) {
            this.display = display;
            this.icon = icon;
            this.color = color;
            this.rank = rank;
            this.cost = cost;
            this.lore = lore;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String colored() {
            return color + display;
        }

        public Material material() {
            return icon;
        }

        public Rank rank() {
            return rank;
        }

        public Map<Material, Integer> cost() {
            return cost;
        }

        public static Tool byId(String id) {
            if (id == null) {
                return null;
            }
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static final double FLARE_RADIUS = 10.0d;
    private static final int FLARE_TICKS = 20 * 15;
    private static final int FLARE_CAP = 160;
    private static final long CANARY_MS = 10L * 60_000L;

    private final MineIsle isle;
    private final NamespacedKey toolKey;
    private final NamespacedKey flareKey;
    private final Map<UUID, Long> flareCool = new ConcurrentHashMap<>();

    ForgeWorks(MineIsle isle) {
        this.isle = isle;
        this.toolKey = new NamespacedKey(isle.plugin(), "mine_tool");
        this.flareKey = new NamespacedKey(isle.plugin(), "mine_flare_glow");
    }

    // ------------------------------------------------------------------ costs

    public Cost cost(Mark mark, int level) {
        Map<String, Integer> items = new LinkedHashMap<>();
        Map<Material, Integer> plain = new LinkedHashMap<>();
        long coins;
        Grade specimen = null;
        int specimens = 0;
        Rank rank = switch (level) {
            case 1 -> Rank.VISITOR;
            case 2 -> Rank.APPRENTICE;
            case 3 -> Rank.JOURNEYMAN;
            case 4 -> Rank.SMITH;
            default -> Rank.MASTER;
        };
        switch (mark) {
            case TEMPERED_HEAD -> {
                switch (level) {
                    case 1 -> { items.put("compressed_raw_iron", 4); coins = 2_000L; }
                    case 2 -> { items.put("compressed_raw_iron", 8); items.put("compressed_raw_gold", 4); coins = 6_000L; }
                    case 3 -> { items.put("compressed_raw_iron", 16); items.put("compressed_raw_gold", 8); items.put("compressed_diamond", 2); coins = 15_000L; }
                    case 4 -> { items.put("compacted_raw_iron", 1); items.put("compressed_raw_gold", 16); items.put("compressed_diamond", 8); coins = 40_000L; }
                    default -> { items.put("compacted_raw_iron", 2); items.put("compacted_raw_gold", 1); items.put("compressed_diamond", 24);
                        coins = 120_000L; specimen = Grade.PERFECT; specimens = 1; }
                }
            }
            case ASSAY_PLATE -> {
                switch (level) {
                    case 1 -> { items.put("compressed_raw_copper", 4); items.put("compressed_lapis", 2); coins = 2_500L; }
                    case 2 -> { items.put("compressed_raw_copper", 8); items.put("compressed_lapis", 6); items.put("compressed_redstone", 2);
                        coins = 7_500L; specimen = Grade.ROUGH; specimens = 1; }
                    case 3 -> { items.put("compressed_lapis", 16); items.put("compressed_redstone", 8); items.put("compressed_raw_gold", 4);
                        coins = 20_000L; specimen = Grade.FLAWLESS; specimens = 1; }
                    case 4 -> { items.put("compacted_lapis", 1); items.put("compressed_raw_gold", 16); items.put("compressed_emerald", 4);
                        coins = 50_000L; specimen = Grade.PERFECT; specimens = 1; }
                    default -> { items.put("compacted_raw_gold", 1); items.put("compressed_emerald", 16);
                        coins = 150_000L; specimen = Grade.HEARTSTONE; specimens = 1; }
                }
            }
            case LAMP_CORE -> {
                rank = level == 1 ? Rank.APPRENTICE : level == 2 ? Rank.JOURNEYMAN : Rank.SMITH;
                switch (level) {
                    case 1 -> { items.put("compressed_redstone", 6); coins = 3_000L; }
                    case 2 -> { items.put("compressed_redstone", 12); items.put("compressed_lapis", 6); coins = 12_000L;
                        specimen = Grade.FLAWLESS; specimens = 1; }
                    default -> { items.put("compacted_redstone", 1); items.put("compressed_lapis", 16); coins = 40_000L;
                        specimen = Grade.PERFECT; specimens = 2; }
                }
            }
            case SEAM_HOOK -> {
                rank = level == 1 ? Rank.APPRENTICE : level == 2 ? Rank.JOURNEYMAN : Rank.SMITH;
                switch (level) {
                    case 1 -> { items.put("compressed_raw_copper", 8); items.put("compressed_emerald", 2); coins = 4_000L; }
                    case 2 -> { items.put("compressed_raw_copper", 16); items.put("compressed_emerald", 6); coins = 12_000L; }
                    default -> { items.put("compacted_raw_copper", 1); items.put("compressed_emerald", 12); coins = 35_000L; }
                }
            }
            default -> {
                rank = level == 1 ? Rank.VISITOR : level == 2 ? Rank.APPRENTICE : Rank.JOURNEYMAN;
                switch (level) {
                    case 1 -> { items.put("compressed_coal", 8); coins = 2_000L; }
                    case 2 -> { items.put("compressed_coal", 24); plain.put(Material.OBSIDIAN, 16); coins = 6_000L; }
                    default -> { items.put("compacted_coal", 1); plain.put(Material.OBSIDIAN, 64); coins = 20_000L; }
                }
            }
        }
        return new Cost(items, plain, coins, specimen, specimens, rank);
    }

    /** Human lines for a cost, with have/need colouring. */
    public List<String> costLines(Player player, Cost cost) {
        List<String> lines = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        cost.items().forEach((id, amount) -> {
            int have = MineItems.countById(inventory, id);
            lines.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + "× " + pretty(id) + " §8(" + have + ")");
        });
        cost.plain().forEach((material, amount) -> {
            int have = MineItems.countPlain(inventory, material);
            lines.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + "× " + pretty(material.name()) + " §8(" + have + ")");
        });
        if (cost.specimen() != null) {
            int have = MineItems.countSpecimensAtLeast(inventory, isle.crystals(), cost.specimen());
            lines.add((have >= cost.specimens() ? "§a✔ " : "§c✘ ") + "§f" + cost.specimens() + "× " + cost.specimen().colored()
                    + "§f+ specimen §8(any ore · " + have + ")");
        }
        long balance = MineSkills.balance(player);
        lines.add((balance >= cost.coins() ? "§a✔ " : "§c✘ ") + "§6" + MineText.coins(cost.coins()) + " coins");
        long rep = isle.profiles().of(player).forgeRep;
        lines.add((rep >= cost.rank().from ? "§a✔ " : "§c✘ ") + "§7Forge rank " + cost.rank().colored());
        return lines;
    }

    private boolean affordable(Player player, Cost cost) {
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<String, Integer> entry : cost.items().entrySet()) {
            if (MineItems.countById(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<Material, Integer> entry : cost.plain().entrySet()) {
            if (MineItems.countPlain(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        if (cost.specimen() != null
                && MineItems.countSpecimensAtLeast(inventory, isle.crystals(), cost.specimen()) < cost.specimens()) {
            return false;
        }
        return MineSkills.balance(player) >= cost.coins()
                && isle.profiles().of(player).forgeRep >= cost.rank().from;
    }

    // ------------------------------------------------------------------ forging marks

    public int level(Player player, Mark mark) {
        return player == null ? 0 : isle.profiles().of(player).mark(mark.id());
    }

    /** Forge the next level of {@code mark}. Returns false with a reason on the action bar. */
    public boolean forge(Player player, Mark mark) {
        int level = level(player, mark);
        if (level >= mark.max) {
            MineText.bar(player, mark.colored() + " §7is already at its best.");
            return false;
        }
        Cost cost = cost(mark, level + 1);
        if (!affordable(player, cost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            MineText.bar(player, "§cBrann shakes his head §8· §7check the list, something's short.");
            return false;
        }
        if (!MineSkills.takeCoins(player, cost.coins())) {
            MineText.bar(player, "§cCoins didn't clear.");
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        cost.items().forEach((id, amount) -> MineItems.takeById(inventory, id, amount));
        cost.plain().forEach((material, amount) -> MineItems.takePlain(inventory, material, amount));
        if (cost.specimen() != null) {
            MineItems.takeSpecimensAtLeast(inventory, isle.crystals(), cost.specimen(), cost.specimens());
        }
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.marks.put(mark.id(), level + 1);
        isle.profiles().markDirty();
        long rep = 25L * (level + 1) + cost.coins() / 400L;
        addRep(player, rep, mark.display() + " " + MineText.roman(level + 1));
        MineSkills.bonus(player, 80 * (level + 1));
        celebrate(player, mark, level + 1);
        return true;
    }

    private void celebrate(Player player, Mark mark, int level) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                MineText.legacy(mark.colored() + " " + MineText.roman(level)),
                MineText.legacy(mark.effect(level) + " §8· §7stamped into you, not the steel"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150),
                        java.time.Duration.ofMillis(2400), java.time.Duration.ofMillis(500))));
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.9f, 0.8f);
        player.playSound(at, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.PLAYERS, 1.0f, 0.9f);
        player.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 0.8f);
        player.spawnParticle(Particle.LAVA, at.clone().add(0, 1.2, 0), 10, 0.4, 0.3, 0.4, 0.0);
        player.spawnParticle(Particle.FLAME, at.clone().add(0, 1.0, 0), 30, 0.5, 0.5, 0.5, 0.03);
        isle.props().forgeBurst(level >= 4);
        isle.cast().say(player, MineRole.FORGEMASTER, switch (level) {
            case 1 -> "There. First mark's always the loudest. Wear it well.";
            case 2 -> "Better. Your swing's starting to sound like mine.";
            case 3 -> "Now we're talking. The rock can feel that one coming.";
            case 4 -> "That's a mark most miners never earn. Don't waste it on cobble.";
            default -> "Finished. Nothing left for me to teach this line. Go frighten a mountain.";
        });
    }

    // ------------------------------------------------------------------ reputation

    public long rep(Player player) {
        return isle.profiles().of(player).forgeRep;
    }

    public Rank rank(Player player) {
        return Rank.of(rep(player));
    }

    public void addRep(Player player, long amount, String reason) {
        if (player == null || amount <= 0L) {
            return;
        }
        MineProfiles.Profile profile = isle.profiles().of(player);
        Rank before = Rank.of(profile.forgeRep);
        profile.forgeRep += amount;
        isle.profiles().markDirty();
        Rank after = Rank.of(profile.forgeRep);
        MineText.bar(player, "§6⚒ +" + amount + " Forge Reputation §8· §7" + reason);
        if (after.ordinal() > before.ordinal()) {
            player.sendMessage("§6⚒ Forge rank up: " + after.colored() + " §8· §7Brann has more for you at the Deep Forge.");
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.8f, 0.7f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 0.9f);
            MineSkills.bonus(player, 100 * after.ordinal());
        }
    }

    /** A blueprint upgrade finished at the Forgehand (Items ritual). */
    void onForged(Player player, int tier) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.forged++;
        isle.profiles().markDirty();
        addRep(player, 40L * Math.max(1, tier), "Blueprint Tier " + MineText.roman(tier) + " at the Forgehand");
        if (tier >= 4) {
            var line = MineText.legacy("§6⚒ " + player.getName() + " §7forged a §6Tier IV §7blueprint tool at the Eldervale forge."
                    + " §8The whole mountain heard it.");
            for (Player visitor : MineWorld.visitors(isle.plugin())) {
                visitor.sendMessage(line);
            }
        }
    }

    // ------------------------------------------------------------------ effects read by the router

    double oreFortune(Player player) {
        return 4.0d * level(player, Mark.ASSAY_PLATE);
    }

    double speedPower(Player player) {
        return 4.0d * level(player, Mark.TEMPERED_HEAD);
    }

    double crystalMultiplier(Player player) {
        int level = level(player, Mark.LAMP_CORE);
        return level <= 0 ? 1.0d : 1.0d + 0.08d * level + (level >= 3 ? 0.01d : 0.0d);
    }

    int seamReduction(Player player) {
        return level(player, Mark.SEAM_HOOK);
    }

    /** Multiplier for hazard damage: Heat Ward and an active Canary stack. */
    double hazardFactor(Player player) {
        double factor = 1.0d - 0.2d * level(player, Mark.HEAT_WARD);
        if (canary(player)) {
            factor *= 0.5d;
        }
        return Math.max(0.1d, factor);
    }

    boolean canary(Player player) {
        return player != null && isle.profiles().of(player).canaryUntil > System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ consumables

    public ItemStack tool(Tool tool, int amount) {
        ItemStack item = new ItemStack(tool.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(tool.colored());
        List<String> lore = new ArrayList<>(tool.lore);
        lore.add("");
        lore.add("§eRight-click to use.");
        lore.add("§8The Deep Forge · Mining Eldervale");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.id());
        item.setItemMeta(meta);
        return item;
    }

    public Tool toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Tool.byId(item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING));
    }

    /** Craft one consumable from plain materials. */
    public boolean craft(Player player, Tool tool) {
        if (rep(player) < tool.rank.from) {
            MineText.bar(player, "§cNeeds Forge rank " + tool.rank.colored() + "§c.");
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<Material, Integer> entry : tool.cost.entrySet()) {
            if (MineItems.countPlain(inventory, entry.getKey()) < entry.getValue()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
                MineText.bar(player, "§cShort on " + pretty(entry.getKey().name()) + ".");
                return false;
            }
        }
        tool.cost.forEach((material, amount) -> MineItems.takePlain(inventory, material, amount));
        MineSkills.give(player, tool(tool, 1));
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        addRep(player, 3L, tool.display);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Tool tool = toolOf(hand);
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        boolean used = switch (tool) {
            case FLARE -> flare(player);
            case CANARY -> canaryOn(player);
        };
        if (used) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (toolOf(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean flare(Player player) {
        Location center = player.getLocation();
        boolean veins = isle.plugin().getVeins() != null && isle.plugin().getVeins().isVeins(center.getWorld());
        if (!veins && !MineWorld.onIsle(isle.plugin(), center)) {
            MineText.bar(player, "§7The flare fizzles. §8Only works on Mining Eldervale and in the Amethyst Mine.");
            return false;
        }
        Long cool = flareCool.get(player.getUniqueId());
        if (cool != null && cool > System.currentTimeMillis()) {
            MineText.bar(player, "§7Your eyes are still full of the last one.");
            return false;
        }
        flareCool.put(player.getUniqueId(), System.currentTimeMillis() + 5_000L);
        World world = center.getWorld();
        List<BlockDisplay> glows = new ArrayList<>();
        int radius = (int) Math.ceil(FLARE_RADIUS);
        int found = 0;
        for (int dx = -radius; dx <= radius && glows.size() < FLARE_CAP; dx++) {
            for (int dy = -radius; dy <= radius && glows.size() < FLARE_CAP; dy++) {
                for (int dz = -radius; dz <= radius && glows.size() < FLARE_CAP; dz++) {
                    if (dx * dx + dy * dy + dz * dz > FLARE_RADIUS * FLARE_RADIUS) {
                        continue;
                    }
                    Block block = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                    IsleOre ore = IsleOre.fromBlock(block.getType());
                    if (ore == null) {
                        ore = AmethystMine.oreOf(block.getType());
                    }
                    if (ore == null || ore == IsleOre.STONE) {
                        continue;
                    }
                    found++;
                    Color glow = ore.glow();
                    BlockDisplay display = world.spawn(block.getLocation(), BlockDisplay.class, spawned -> {
                        spawned.setBlock(block.getBlockData());
                        spawned.setGlowing(true);
                        spawned.setGlowColorOverride(glow);
                        spawned.setBrightness(new Display.Brightness(15, 15));
                        spawned.setTransformation(new Transformation(new Vector3f(-0.01f, -0.01f, -0.01f),
                                new AxisAngle4f(), new Vector3f(1.02f, 1.02f, 1.02f), new AxisAngle4f()));
                        spawned.setPersistent(false);
                        spawned.setVisibleByDefault(false);
                        spawned.getPersistentDataContainer().set(flareKey, PersistentDataType.BYTE, (byte) 1);
                    });
                    player.showEntity(isle.plugin(), display);
                    glows.add(display);
                }
            }
        }
        player.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f);
        player.spawnParticle(Particle.FLASH, center.clone().add(0, 1.5, 0), 1);
        player.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 40, 3.0, 2.0, 3.0, 0.02);
        MineText.bar(player, "§e✦ Flare! §f" + found + " §7ore" + (found == 1 ? "" : "s") + " light up through the rock"
                + (found >= FLARE_CAP ? " §8(the brightest " + FLARE_CAP + ")" : "") + " §8· 15s");
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> glows.forEach(display -> {
            if (display.isValid()) {
                display.remove();
            }
        }), FLARE_TICKS);
        return true;
    }

    private boolean canaryOn(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        long now = System.currentTimeMillis();
        profile.canaryUntil = Math.max(now, profile.canaryUntil) + CANARY_MS;
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.ENTITY_PARROT_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        MineText.bar(player, "§6♪ Canary on duty §8· §7" + MineText.clock((profile.canaryUntil - now) / 1000L)
                + " §8· §7warnings, −50% hazard damage, no dark");
        return true;
    }

    void forget(UUID id) {
        flareCool.remove(id);
    }

    /** Leftover flare glows from a crash. */
    void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(BlockDisplay.class).forEach(display -> {
                if (display.getPersistentDataContainer().has(flareKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            });
        }
    }

    static String pretty(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replace("raw_", "");
        StringBuilder out = new StringBuilder();
        for (String part : cleaned.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
