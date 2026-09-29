package de.aetherion.items.listener;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.MiningAccess;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.mining.HarvestRules;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items harvest payouts: fortune, skill XP, break-speed, tool gates.
 * World/WG/ore-seal regen is {@code de.aetherion.mining.MiningListener}.
 */
public class HarvestListener implements Listener {

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final NamespacedKey miningModifierKey = new NamespacedKey("aetherion", "aetherion_mining_power");
    private final NamespacedKey breakSpeedKey = new NamespacedKey("aetherion", "aetherion_break_speed");
    private final Map<UUID, Long> lastPowerHint = new ConcurrentHashMap<>();

    public HarvestListener(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearBreakSpeed(event.getPlayer());
        stripLegacyEfficiency(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            clearBreakSpeed(player);
            return;
        }
        Block block = event.getBlock();
        Material material = block.getType();
        if (!allowsHarvestTool(player, material)) {
            event.setCancelled(true);
            clearBreakSpeed(player);
            // Swords/weapons: silent cancel (walking+swing spam). Other wrong tools still hint.
            if (!holdingWeapon(player)) {
                hintWrongTool(player, material);
            }
            return;
        }
        if (HarvestRules.openMine(block.getWorld())) {
            applyOpenMineSpeed(player, block);
            return;
        }
        if (!HarvestRules.tracked(material)) {
            clearBreakSpeed(player);
            return;
        }
        double miningPower = equipmentStats.getStat(player, ItemCapability.MINING_POWER);
        if (!HarvestRules.canHarvest(material, miningPower)) {
            event.setCancelled(true);
            clearBreakSpeed(player);
            hintPower(player, material);
            return;
        }
        event.setInstaBreak(false);
        applyBreakSpeed(player, block);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        clearBreakSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        clearBreakSpeed(player);
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getBlock();
        Material material = block.getType();
        if (!allowsHarvestTool(player, material)) {
            event.setCancelled(true);
            if (!holdingWeapon(player)) {
                hintWrongTool(player, material);
            }
            return;
        }
        boolean open = HarvestRules.openMine(block.getWorld());
        if (!HarvestRules.tracked(material)) {
            return;
        }
        double miningPower = equipmentStats.getStat(player, ItemCapability.MINING_POWER);
        if (!open && !HarvestRules.canHarvest(material, miningPower)) {
            event.setCancelled(true);
            event.setDropItems(false);
            hintPower(player, material);
            return;
        }
        if (HarvestRules.plainIslandDrop(block)) {
            event.setDropItems(false);
            InventoryDrops.give(player, new ItemStack(material, 1));
            return;
        }
        event.setDropItems(false);
        var skills = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().getSkills();
        if (skills != null) {
            skills.grantFromBlock(player, material);
        }
        if (HarvestRules.log(material)) {
            grantForagingAxe(player);
        }
        InventoryDrops.setDropAt(block.getLocation());
        try {
            payOut(player, block, material);
            if (de.aetherion.items.mining.OreTrollListener.canSpawnFrom(material, block.getLocation())) {
                var items = de.aetherion.items.AetherionItems.getInstance();
                if (items != null && items.getOreTrollListener() != null) {
                    items.getOreTrollListener().maybeSpawn(player, block.getLocation());
                }
            }
        } finally {
            InventoryDrops.clearDropAt();
        }
    }

    /**
     * Vein Siphon vacuum: fortune payout + AIR (open mine) or sealed bedrock + restore.
     */
    public void vacuumHarvest(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        Material material = block.getType();
        if (!HarvestRules.ore(material)) {
            return;
        }
        boolean open = HarvestRules.openMine(block.getWorld());
        double miningPower = equipmentStats.getStat(player, ItemCapability.MINING_POWER);
        if (!open && !HarvestRules.canHarvest(material, miningPower)) {
            return;
        }
        if (HarvestRules.plainIslandDrop(block)) {
            InventoryDrops.give(player, new ItemStack(material, 1));
            block.setType(Material.AIR, false);
            if (player.isOnline()) {
                player.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
            }
            return;
        }
        org.bukkit.block.data.BlockData original = block.getBlockData().clone();
        var skills = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().getSkills();
        if (skills != null) {
            skills.grantFromBlock(player, material);
        }
        InventoryDrops.setDropAt(block.getLocation());
        try {
            payOut(player, block, material);
            if (de.aetherion.items.mining.OreTrollListener.canSpawnFrom(material, block.getLocation())) {
                var items = de.aetherion.items.AetherionItems.getInstance();
                if (items != null && items.getOreTrollListener() != null) {
                    items.getOreTrollListener().maybeSpawn(player, block.getLocation());
                }
            }
        } finally {
            InventoryDrops.clearDropAt();
        }
        if (open) {
            block.setType(Material.AIR, false);
            if (player.isOnline()) {
                player.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
            }
            return;
        }
        if (!sealViaMining(player, block, original)) {
            // Fallback if AetherionMining is missing — same timing as EmeraldSpread.
            block.setType(Material.BEDROCK, false);
            if (player.isOnline()) {
                player.sendBlockChange(block.getLocation(), Material.BEDROCK.createBlockData());
            }
            long seconds = vacuumRespawnSeconds(material);
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(HarvestListener.class),
                    () -> {
                        if (block.getType() == Material.BEDROCK) {
                            block.setBlockData(original, false);
                        }
                    },
                    20L * Math.max(1L, seconds)
            );
        }
    }

    private static boolean sealViaMining(Player player, Block block, org.bukkit.block.data.BlockData original) {
        MiningAccess mining = AetherServices.mining();
        if (mining == null) {
            return false;
        }
        mining.sealForVacuum(player, block, original);
        return true;
    }

    private static long vacuumRespawnSeconds(Material material) {
        MiningAccess mining = AetherServices.mining();
        if (mining != null) {
            return mining.respawnSeconds(material);
        }
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 10L;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 15L;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 20L;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> 25L;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 15L;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 20L;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 40L;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 45L;
            case ANCIENT_DEBRIS -> 60L;
            case NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> 20L;
            case AMETHYST_CLUSTER -> 30L;
            default -> 10L;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        // Crops: CropHarvestListener owns payout (1 base + flat Fortune). Harvest only breaks extras.
        if (de.aetherion.items.farming.Crops.isCrop(event.getBlockState().getType())) {
            return;
        }
        if (HarvestRules.tracked(event.getBlockState().getType())) {
            return;
        }
        Player player = event.getPlayer();
        double fortune = Math.max(0, equipmentStats.getStat(player, ItemCapability.FORTUNE));
        List<Item> drops = event.getItems();
        var skills = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().getSkills();
        if (skills != null) {
            skills.grantFromBlock(player, event.getBlockState().getType());
        }
        if (drops.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        InventoryDrops.setDropAt(event.getBlock().getLocation());
        try {
            for (Item dropEntity : drops) {
                if (dropEntity == null) {
                    continue;
                }
                ItemStack drop = dropEntity.getItemStack();
                if (drop == null || drop.getType().isAir()) {
                    continue;
                }
                giveFortuned(player, drop, fortune);
            }
        } finally {
            InventoryDrops.clearDropAt();
        }
    }

    public void refreshMiningPower(Player player) {
        if (player == null) {
            return;
        }
        stripLegacyEfficiency(player);
    }

    private void payOut(Player player, Block block, Material material) {
        if (HarvestRules.plainIslandDrop(block)) {
            // Exactly the placed block. Fortune, compact, and smelt multipliers stay off.
            InventoryDrops.give(player, new ItemStack(material, 1));
            return;
        }
        double fortune = Math.max(0, equipmentStats.getStat(player, ItemCapability.FORTUNE));
        if (material == Material.AMETHYST_CLUSTER) {
            giveFortuned(player, new ItemStack(Material.AMETHYST_CLUSTER), 0);
            giveFortuned(player, new ItemStack(Material.AMETHYST_SHARD, 4), fortune);
            maybeDropVoided455(player, material);
            return;
        }
        if (HarvestRules.fullMineralBlock(material)) {
            Material resource = HarvestRules.fullBlockResource(material);
            int amount = HarvestRules.fullBlockBaseAmount(material);
            if (resource != null && amount > 0) {
                giveFortuned(player, new ItemStack(resource, amount), fortune);
            }
            maybeDropVoided455(player, material);
            return;
        }
        // Copper/debris: 1 smelted drop per block (furnaces off). Extra only from Fortune.
        if (material == Material.COPPER_ORE || material == Material.DEEPSLATE_COPPER_ORE) {
            giveFortuned(player, new ItemStack(Material.COPPER_INGOT, 1), fortune);
            maybeDropVoided455(player, material);
            return;
        }
        if (material == Material.ANCIENT_DEBRIS) {
            giveFortuned(player, new ItemStack(Material.NETHERITE_INGOT, 1), fortune);
            maybeDropVoided455(player, material);
            return;
        }
        ItemStack tool = new ItemStack(HarvestRules.log(material) ? Material.NETHERITE_AXE : Material.NETHERITE_PICKAXE);
        Collection<ItemStack> drops = block.getDrops(tool, player);
        if (drops.isEmpty()) {
            drops = List.of(new ItemStack(material));
        }
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            Material converted = convertOreDrop(drop.getType());
            ItemStack stack = converted == null ? drop.clone() : new ItemStack(converted, drop.getAmount());
            stack = de.aetherion.items.farming.Crops.millSeeds(stack);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            giveFortuned(player, stack, fortune);
        }
        maybeDropVoided455(player, material);
    }

    public void payWood(Player player, Material material) {
        if (player == null || material == null) {
            return;
        }
        Material drop = asLogDrop(material);
        double fortune = Math.max(0, equipmentStats.getStat(player, ItemCapability.FORTUNE));
        giveFortuned(player, new ItemStack(drop, 1), fortune);
        grantForagingAxe(player);
    }

    /** Exposed for tree-fell wood caps in AetherionForaging. */
    public double fortuneOf(Player player) {
        if (player == null) {
            return 0.0d;
        }
        return Math.max(0.0d, equipmentStats.getStat(player, ItemCapability.FORTUNE));
    }

    private void grantForagingAxe(Player player) {
        if (player == null) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (de.aetherion.items.item.ForagingAxeProgress.isAxe(tool)) {
            de.aetherion.items.item.ForagingAxeProgress.grant(player, tool, itemManager, 4);
        }
    }

    /** Bark-all-sides wood/hyphae → matching log/stem (one stack in inventory). */
    private static Material asLogDrop(Material material) {
        String name = material.name().replace("STRIPPED_", "");
        if (name.endsWith("_WOOD")) {
            name = name.substring(0, name.length() - "_WOOD".length()) + "_LOG";
        } else if (name.endsWith("_HYPHAE")) {
            name = name.substring(0, name.length() - "_HYPHAE".length()) + "_STEM";
        }
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return material;
        }
    }

    private void giveFortuned(Player player, ItemStack drop, double fortune) {
        int originalAmount = drop.getAmount();
        if (originalAmount <= 0) {
            return;
        }
        double bonusAmount = originalAmount * (fortune / 100.0);
        int guaranteedBonus = (int) Math.floor(bonusAmount);
        if (Math.random() < bonusAmount - guaranteedBonus) {
            guaranteedBonus++;
        }
        giveAmount(player, drop, originalAmount + guaranteedBonus);
    }

    private void giveAmount(Player player, ItemStack drop, int finalAmount) {
        while (finalAmount > 0) {
            ItemStack one = drop.clone();
            one.setAmount(1);
            ItemStack compressed = ProgressionEffects.maybeCompress(player, itemManager, one);
            InventoryDrops.give(player, compressed != null ? compressed : one);
            finalAmount--;
        }
    }

    private Material convertOreDrop(Material material) {
        return switch (material) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS, NETHERITE_SCRAP -> Material.NETHERITE_INGOT;
            default -> null;
        };
    }

    private void maybeDropVoided455(Player player, Material material) {
        double power = HarvestRules.requiredPower(material);
        if (power <= 0.01 || player == null) {
            return;
        }
        double chance;
        if (de.aetherion.items.item.Voided455Drops.mvpPityOpen(player)) {
            chance = 1.0d / 10_000.0d;
        } else {
            chance = (1.0d / 1_000_000.0d) * (1.0d + power / 40.0d);
        }
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        var plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin == null || plugin.getCustomItem() == null) {
            return;
        }
        ItemStack drill = plugin.getCustomItem().progression().createVoided455();
        if (drill == null || drill.getType().isAir()) {
            return;
        }
        de.aetherion.items.item.Voided455Drops.markDropped(player);
        InventoryDrops.give(player, drill);
        player.sendTitle("§5Voided 455", "§7The last catalyst never arrived", 10, 70, 20);
        player.sendMessage("§5The rock spit out a drill that should not exist.");
        player.sendMessage("§8Not in the book. Not a recipe. You got stupid lucky.");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.6f);
        net.kyori.adventure.text.Component shout = net.kyori.adventure.text.Component.text(
                        "The void coughed. Someone in the mines is about to become insufferable.")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE);
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.sendMessage(shout);
        }
    }

    private void applyOpenMineSpeed(Player player, Block block) {
        clearBreakSpeed(player);
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        float vanilla = block.getBreakSpeed(player);
        if (vanilla <= 0.00001f) {
            vanilla = 0.00001f;
        }
        int vanillaTicks = Math.max(1, (int) Math.ceil(1.0f / vanilla));
        double factor = vanillaTicks / 5.0;
        attribute.addModifier(new AttributeModifier(
                breakSpeedKey,
                factor - 1.0,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void applyBreakSpeed(Player player, Block block) {
        clearBreakSpeed(player);
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        double miningPower = equipmentStats.getStat(player, ItemCapability.MINING_POWER);
        int desired = HarvestRules.canHarvest(block.getType(), miningPower)
                ? HarvestRules.ticks(block.getType(), miningPower)
                : 400;
        float vanilla = block.getBreakSpeed(player);
        if (vanilla <= 0.00001f) {
            vanilla = 0.00001f;
        }
        int vanillaTicks = Math.max(1, (int) Math.ceil(1.0f / vanilla));
        double factor = vanillaTicks / (double) desired;
        attribute.addModifier(new AttributeModifier(
                breakSpeedKey,
                factor - 1.0,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void clearBreakSpeed(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(breakSpeedKey);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }

    private void scheduleUpdate(Player player) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(HarvestListener.class);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            stripLegacyEfficiency(player);
            clearBreakSpeed(player);
        });
    }

    private void stripLegacyEfficiency(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_MINING_EFFICIENCY);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(miningModifierKey);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }

    private void hintPower(Player player, Material material) {
        long now = System.currentTimeMillis();
        Long last = lastPowerHint.get(player.getUniqueId());
        if (last != null && now - last < 2500L) {
            return;
        }
        lastPowerHint.put(player.getUniqueId(), now);
        int need = (int) Math.ceil(HarvestRules.requiredPower(material));
        player.sendMessage("§cToo soft. Need §f" + need + " §cmining power for this ore.");
    }

    /** Sword / bow / etc. — block break is cancelled with no chat spam. */
    private static boolean holdingWeapon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        Material type = hand.getType();
        String name = type.name();
        return name.endsWith("_SWORD")
                || org.bukkit.Tag.ITEMS_SWORDS.isTagged(type)
                || type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.TRIDENT
                || name.endsWith("_MACE")
                || name.equals("MACE");
    }

    /**
     * Tools are locked to their job.
     * Crops/wood: bare hand OK. Ores/mineral blocks: pickaxe required.
     * Sword/weapons never break tracked blocks.
     */
    private static boolean allowsHarvestTool(Player player, Material block) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean bareOrNonTool = hand == null || hand.getType().isAir();
        Material type = bareOrNonTool ? Material.AIR : hand.getType();
        String name = type.name();
        boolean pick = !bareOrNonTool && (name.endsWith("_PICKAXE") || org.bukkit.Tag.ITEMS_PICKAXES.isTagged(type));
        boolean axe = !bareOrNonTool && (name.endsWith("_AXE") || org.bukkit.Tag.ITEMS_AXES.isTagged(type));
        boolean shovel = !bareOrNonTool && (name.endsWith("_SHOVEL") || org.bukkit.Tag.ITEMS_SHOVELS.isTagged(type));
        boolean hoe = !bareOrNonTool && isHoe(type, hand);
        boolean weapon = holdingWeapon(player);

        if (!bareOrNonTool && !pick && !axe && !shovel && !hoe && !weapon) {
            bareOrNonTool = true;
        }
        if (weapon) {
            return false;
        }
        if (de.aetherion.items.farming.Crops.isCrop(block)) {
            return bareOrNonTool || hoe;
        }
        if (HarvestRules.log(block)) {
            return bareOrNonTool || axe;
        }
        if (HarvestRules.requiresPickaxe(block)) {
            return pick;
        }
        // Stone / soft tracked: pickaxe if holding a tool, bare hand still OK.
        if (bareOrNonTool) {
            return true;
        }
        if (pick) {
            return true;
        }
        if (shovel) {
            return isDiggable(block);
        }
        return false;
    }

    private static boolean isDiggable(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.GRASS_BLOCK
                || material == Material.DIRT
                || material == Material.COARSE_DIRT
                || material == Material.ROOTED_DIRT
                || material == Material.PODZOL
                || material == Material.MYCELIUM
                || material == Material.DIRT_PATH
                || material == Material.FARMLAND
                || material == Material.GRAVEL
                || material == Material.CLAY
                || material == Material.SAND
                || material == Material.RED_SAND
                || material == Material.SOUL_SAND
                || material == Material.SOUL_SOIL
                || material == Material.MUD
                || material == Material.MUDDY_MANGROVE_ROOTS
                || material == Material.SNOW
                || material == Material.SNOW_BLOCK
                || material == Material.POWDER_SNOW
                || name.contains("CONCRETE_POWDER");
    }

    private static boolean isHoe(Material type, ItemStack hand) {
        if (type == null) {
            return false;
        }
        if (type.name().endsWith("_HOE")) {
            return true;
        }
        try {
            if (org.bukkit.Tag.ITEMS_HOES.isTagged(type)) {
                return true;
            }
        } catch (NoSuchFieldError | NoSuchMethodError ignored) {
        }
        return de.aetherion.items.item.FarmingHoeProgress.isHoe(hand);
    }

    private void hintWrongTool(Player player, Material block) {
        long now = System.currentTimeMillis();
        Long last = lastPowerHint.get(player.getUniqueId());
        if (last != null && now - last < 2500L) {
            return;
        }
        lastPowerHint.put(player.getUniqueId(), now);
        if (HarvestRules.requiresPickaxe(block)) {
            player.sendMessage("§cNeed a pickaxe for this ore.");
            return;
        }
        player.sendMessage("§cWrong tool. Pickaxe→stone/ore, axe→wood, hoe→crops.");
    }
}
