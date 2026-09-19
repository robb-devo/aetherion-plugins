package de.aetherion.guilds.service;

import de.aetherion.core.AetherKeys;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MinionService {

    private static final NamespacedKey AETHERION_ITEM = AetherKeys.ITEM_ID;
    private static final String PERSONAL_PREFIX = "p|";

    private final JavaPlugin plugin;
    private final GuildService guilds;
    private final IslandService islands;
    private PersonalIslandService personal;

    public MinionService(JavaPlugin plugin, GuildService guilds, IslandService islands) {
        this.plugin = plugin;
        this.guilds = guilds;
        this.islands = islands;
    }

    public void attachPersonal(PersonalIslandService personal) {
        this.personal = personal;
    }

    public NamespacedKey itemKey() {
        return new NamespacedKey(plugin, "quarry_item");
    }

    public NamespacedKey levelKey() {
        return new NamespacedKey(plugin, "quarry_level");
    }

    public NamespacedKey processorKey() {
        return new NamespacedKey(plugin, "quarry_processor");
    }

    public NamespacedKey storedKey() {
        return new NamespacedKey(plugin, "quarry_stored");
    }

    public NamespacedKey storedCompressedKey() {
        return new NamespacedKey(plugin, "quarry_stored_c");
    }

    public NamespacedKey storedCompactedKey() {
        return new NamespacedKey(plugin, "quarry_stored_k");
    }

    public NamespacedKey standKey() {
        return new NamespacedKey(plugin, "quarry_stand");
    }

    public ItemStack createQuarryItem() {
        return createQuarryItem(QuarryType.COBBLESTONE);
    }

    public ItemStack createQuarryItem(QuarryType type) {
        return createQuarryItem(type, 1, QuarryMinion.Processor.NONE, 0, 0, 0);
    }

    public ItemStack createQuarryItem(
            QuarryType type,
            int level,
            QuarryMinion.Processor processor,
            int stored,
            int storedCompressed,
            int storedCompacted
    ) {
        QuarryType quarry = type == null ? QuarryType.COBBLESTONE : type;
        QuarryMinion.Processor mill = processor == null ? QuarryMinion.Processor.NONE : processor;
        ItemStack item = new ItemStack(quarry.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + quarry.display() + (level > 1 ? " §8Lv." + level : ""));
            List<String> lore = new ArrayList<>();
            lore.add("§7Place on your private or guild island.");
            lore.add("§7Produces §f" + quarry.productName());
            lore.add("§7while the server is online.");
            if (level > 1 || mill != QuarryMinion.Processor.NONE || stored > 0 || storedCompressed > 0 || storedCompacted > 0) {
                lore.add("");
                lore.add("§7Level: §f" + level);
                lore.add("§7Mill: " + millLabel(mill));
                if (storedCompacted > 0) {
                    lore.add("§7Compacted: §b" + storedCompacted);
                }
                if (storedCompressed > 0) {
                    lore.add("§7Compressed: §a" + storedCompressed);
                }
                if (stored > 0) {
                    lore.add("§7Raw: §e" + stored);
                }
            }
            lore.add("");
            lore.add("§7Click it to collect, move or upgrade.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(itemKey(), PersistentDataType.STRING, quarry.id());
            meta.getPersistentDataContainer().set(levelKey(), PersistentDataType.INTEGER, Math.max(1, level));
            meta.getPersistentDataContainer().set(processorKey(), PersistentDataType.STRING, mill.name());
            meta.getPersistentDataContainer().set(storedKey(), PersistentDataType.INTEGER, Math.max(0, stored));
            meta.getPersistentDataContainer().set(storedCompressedKey(), PersistentDataType.INTEGER, Math.max(0, storedCompressed));
            meta.getPersistentDataContainer().set(storedCompactedKey(), PersistentDataType.INTEGER, Math.max(0, storedCompacted));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String millLabel(QuarryMinion.Processor processor) {
        return switch (processor) {
            case COMPRESSED -> "§aMill";
            case COMPACTED -> "§bForge";
            default -> "§8None";
        };
    }

    public boolean isQuarryItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta()
                .getPersistentDataContainer()
                .has(itemKey(), PersistentDataType.STRING);
    }

    public QuarryType typeOf(ItemStack item) {
        if (!isQuarryItem(item)) {
            return QuarryType.COBBLESTONE;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(itemKey(), PersistentDataType.STRING);
        return QuarryType.fromId(id);
    }

    public void catchUpAll() {
        long now = System.currentTimeMillis();
        int interval = Math.max(1, plugin.getConfig().getInt("minion-interval-seconds", 10)) * 1000;
        for (Guild guild : guilds.all()) {
            for (QuarryMinion minion : guild.minions()) {
                catchUp(minion, now, interval);
            }
        }
        if (personal != null) {
            for (PersonalIsland island : personal.all()) {
                for (QuarryMinion minion : island.minions()) {
                    catchUp(minion, now, interval);
                }
            }
        }
    }

    public void catchUp(QuarryMinion minion) {
        catchUp(
                minion,
                System.currentTimeMillis(),
                intervalMillis()
        );
    }

    private int intervalMillis() {
        return Math.max(1, plugin.getConfig().getInt("minion-interval-seconds", 10)) * 1000;
    }

    private void catchUp(QuarryMinion minion, long now, int interval) {
        if (minion.lastTick() <= 0) {
            minion.setLastTick(now);
            return;
        }
        long steps = (now - minion.lastTick()) / interval;
        if (steps <= 0) {
            return;
        }
        int cap = minion.cap();
        minion.addStored((int) Math.min(steps * (long) minion.perTick(), Integer.MAX_VALUE), cap);
        minion.setLastTick(minion.lastTick() + steps * interval);
        if (AetherionItemsAccess.available()) {
            minion.processStorage();
        }
    }

    public QuarryMinion.StorageView preview(QuarryMinion minion) {
        if (minion == null) {
            return new QuarryMinion.StorageView(0, 0, 0, 0);
        }
        int raw = minion.stored();
        int compressed = minion.storedCompressed();
        int compacted = minion.storedCompacted();
        long now = System.currentTimeMillis();
        int interval = intervalMillis();
        if (minion.lastTick() > 0 && interval > 0) {
            long steps = (now - minion.lastTick()) / interval;
            if (steps > 0) {
                int used = raw + compressed * QuarryMinion.COMPRESS_UNIT + compacted * QuarryMinion.COMPACT_UNIT;
                int room = Math.max(0, minion.cap() - used);
                raw += (int) Math.min(steps * (long) minion.perTick(), room);
            }
        }
        if (AetherionItemsAccess.available()) {
            if (minion.processor() == QuarryMinion.Processor.COMPACTED) {
                int packed = raw / QuarryMinion.COMPACT_UNIT;
                compacted += packed;
                raw -= packed * QuarryMinion.COMPACT_UNIT;
                int packs = raw / QuarryMinion.COMPRESS_UNIT;
                compressed += packs;
                raw -= packs * QuarryMinion.COMPRESS_UNIT;
            } else if (minion.processor() == QuarryMinion.Processor.COMPRESSED) {
                int packs = raw / QuarryMinion.COMPRESS_UNIT;
                compressed += packs;
                raw -= packs * QuarryMinion.COMPRESS_UNIT;
            }
        }
        return new QuarryMinion.StorageView(raw, compressed, compacted, minion.cap());
    }

    public int projectedStored(QuarryMinion minion) {
        return preview(minion).rawEquivalent();
    }

    public QuarryMinion place(Player player, Block block) {
        return place(player, block, createQuarryItem());
    }

    public QuarryMinion place(Player player, Block block, ItemStack item) {
        if (personal != null && personal.isPersonalWorld(block.getWorld())) {
            return placePersonal(player, block, item);
        }
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null || !islands.onOwnIsland(player, guild)) {
            player.sendMessage("§cQuarries can only be placed on your private or guild island.");
            return null;
        }
        if (guild.rank(player.getUniqueId()) == null || !guild.rank(player.getUniqueId()).canPlaceQuarry()) {
            player.sendMessage("§cSoldiers and above can place quarries.");
            return null;
        }
        QuarryMinion minion = createFromItem(block, item);
        guild.minions().add(minion);
        spawnVisual(guild, minion);
        guilds.save();
        player.sendMessage("§a" + minion.quarryType().display() + " placed. It runs while the server is online.");
        return minion;
    }

    private QuarryMinion placePersonal(Player player, Block block, ItemStack item) {
        PersonalIsland island = personal.byOwner(player.getUniqueId());
        if (island == null || !personal.onOwnIsland(player, island)) {
            player.sendMessage("§cQuarries can only be placed on your own island.");
            return null;
        }
        QuarryMinion minion = createFromItem(block, item);
        island.minions().add(minion);
        spawnVisual(island, minion);
        personal.save();
        player.sendMessage("§a" + minion.quarryType().display() + " placed. It runs while the server is online.");
        return minion;
    }

    private QuarryMinion createFromItem(Block block, ItemStack item) {
        QuarryType type = typeOf(item);
        var data = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer() : null;
        int level = data == null ? 1 : Math.max(1, data.getOrDefault(levelKey(), PersistentDataType.INTEGER, 1));
        QuarryMinion.Processor processor = data == null
                ? QuarryMinion.Processor.NONE
                : QuarryMinion.Processor.parse(data.get(processorKey(), PersistentDataType.STRING));
        int stored = data == null ? 0 : Math.max(0, data.getOrDefault(storedKey(), PersistentDataType.INTEGER, 0));
        int storedC = data == null ? 0 : Math.max(0, data.getOrDefault(storedCompressedKey(), PersistentDataType.INTEGER, 0));
        int storedK = data == null ? 0 : Math.max(0, data.getOrDefault(storedCompactedKey(), PersistentDataType.INTEGER, 0));
        Location standLoc = block.getLocation().add(0.5, 1, 0.5);
        QuarryMinion minion = new QuarryMinion(
                UUID.randomUUID(),
                type.id(),
                standLoc.getBlockX(),
                standLoc.getBlockY(),
                standLoc.getBlockZ(),
                System.currentTimeMillis(),
                stored,
                level,
                processor,
                storedC,
                storedK
        );
        if (AetherionItemsAccess.available()) {
            minion.processStorage();
        }
        return minion;
    }

    public void spawnVisual(Guild guild, QuarryMinion minion) {
        spawnVisual(islands.world(), guild.id() + "|" + minion.id(), "guild_quarry", minion);
    }

    public void spawnVisual(PersonalIsland island, QuarryMinion minion) {
        if (personal == null) {
            return;
        }
        spawnVisual(personal.world(), PERSONAL_PREFIX + island.ownerId() + "|" + minion.id(), "personal_quarry", minion);
    }

    private void spawnVisual(World world, String standData, String tag, QuarryMinion minion) {
        if (world == null || minion == null) {
            return;
        }
        Location location = new Location(world, minion.x() + 0.5, minion.y(), minion.z() + 0.5);
        world.getChunkAt(location).load();
        removeVisual(minion, world);
        ArmorStand stand = world.spawn(location, ArmorStand.class);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(false);
        stand.setSmall(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setCanPickupItems(false);
        stand.setRemoveWhenFarAway(false);
        stand.setPersistent(true);
        stand.setDisabledSlots(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.HAND,
                EquipmentSlot.OFF_HAND
        );
        stand.getPersistentDataContainer().set(standKey(), PersistentDataType.STRING, standData);
        stand.addScoreboardTag(tag);
        minion.setVisualId(stand.getUniqueId());
        dress(stand, minion);
        applyLook(stand, minion, 0L);
    }

    public void tickVisuals() {
        tickWorldVisuals(islands.world(), true);
        if (personal != null) {
            tickWorldVisuals(personal.world(), false);
        }
    }

    private void tickWorldVisuals(World world, boolean guildWorld) {
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (guildWorld) {
            for (Guild guild : guilds.all()) {
                for (QuarryMinion minion : guild.minions()) {
                    tickOneVisual(world, minion, now);
                }
            }
            return;
        }
        if (personal == null) {
            return;
        }
        for (PersonalIsland island : personal.all()) {
            for (QuarryMinion minion : island.minions()) {
                tickOneVisual(world, minion, now);
            }
        }
    }

    private void tickOneVisual(World world, QuarryMinion minion, long now) {
        ArmorStand stand = findStand(world, minion);
        if (stand == null || !stand.isValid() || !stand.getChunk().isLoaded()) {
            return;
        }
        Location at = stand.getLocation();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 48 * 48) {
                applyLook(stand, minion, now);
                return;
            }
        }
    }

    public void removeVisuals(Guild guild) {
        if (guild == null) {
            return;
        }
        for (QuarryMinion minion : guild.minions()) {
            removeVisual(minion, islands.world());
        }
    }

    public void removePersonalVisuals(PersonalIsland island) {
        if (island == null || personal == null) {
            return;
        }
        for (QuarryMinion minion : island.minions()) {
            removeVisual(minion, personal.world());
        }
    }

    public void purgeAllVisuals() {
        purgeWorldVisuals(islands.world(), "guild_quarry");
        if (personal != null) {
            purgeWorldVisuals(personal.world(), "personal_quarry");
        }
    }

    private void purgeWorldVisuals(World world, String tag) {
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (entity.getPersistentDataContainer().has(standKey(), PersistentDataType.STRING)
                    || entity.getScoreboardTags().contains(tag)) {
                entity.remove();
                removed++;
            }
        }
        plugin.getLogger().info("Removed " + removed + " " + tag + " visuals.");
    }

    public void purgeUnownedVisuals() {
        purgeUnowned(islands.world(), true);
        if (personal != null) {
            purgeUnowned(personal.world(), false);
        }
    }

    private void purgeUnowned(World world, boolean guildWorld) {
        if (world == null) {
            return;
        }
        Set<String> live = new HashSet<>();
        if (guildWorld) {
            for (Guild guild : guilds.all()) {
                for (QuarryMinion minion : guild.minions()) {
                    live.add(guild.id() + "|" + minion.id());
                }
            }
        } else if (personal != null) {
            for (PersonalIsland island : personal.all()) {
                for (QuarryMinion minion : island.minions()) {
                    live.add(PERSONAL_PREFIX + island.ownerId() + "|" + minion.id());
                }
            }
        }
        int removed = 0;
        for (Entity entity : List.copyOf(world.getEntities())) {
            String data = entity.getPersistentDataContainer().get(standKey(), PersistentDataType.STRING);
            boolean tagged = entity.getScoreboardTags().contains("guild_quarry")
                    || entity.getScoreboardTags().contains("personal_quarry");
            if (data == null && !tagged) {
                continue;
            }
            if (data == null || live.isEmpty() || !live.contains(data)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover quarry ghosts.");
        }
    }

    public void removeVisual(QuarryMinion minion) {
        removeVisual(minion, islands.world());
        if (personal != null) {
            removeVisual(minion, personal.world());
        }
    }

    private void removeVisual(QuarryMinion minion, World world) {
        if (world == null || minion == null) {
            return;
        }
        if (minion.visualId() != null) {
            Entity entity = Bukkit.getEntity(minion.visualId());
            if (entity != null) {
                entity.remove();
            }
            minion.setVisualId(null);
        }
        Location location = new Location(world, minion.x() + 0.5, minion.y(), minion.z() + 0.5);
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(location, 1.5, 2, 1.5)) {
            if (entity.getPersistentDataContainer().has(standKey(), PersistentDataType.STRING)) {
                String data = entity.getPersistentDataContainer().get(standKey(), PersistentDataType.STRING);
                if (data != null && data.endsWith(minion.id().toString())) {
                    entity.remove();
                }
            }
        }
    }

    private ArmorStand findStand(World world, QuarryMinion minion) {
        if (minion.visualId() != null) {
            Entity entity = Bukkit.getEntity(minion.visualId());
            if (entity instanceof ArmorStand stand && stand.isValid()) {
                return stand;
            }
        }
        Location location = new Location(world, minion.x() + 0.5, minion.y(), minion.z() + 0.5);
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        for (Entity entity : world.getNearbyEntities(location, 1.5, 2, 1.5)) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }
            String data = stand.getPersistentDataContainer().get(standKey(), PersistentDataType.STRING);
            if (data != null && data.endsWith(minion.id().toString())) {
                minion.setVisualId(stand.getUniqueId());
                return stand;
            }
        }
        return null;
    }

    private void dress(ArmorStand stand, QuarryMinion minion) {
        QuarryType type = minion.quarryType();
        int level = minion.level();
        if (stand.getEquipment() == null) {
            return;
        }
        stand.getEquipment().setHelmet(new ItemStack(visualHelmet(type, level)));
        stand.getEquipment().setItemInMainHand(toolItem(type, level));
        stand.getEquipment().setItemInOffHand(level >= 6 ? new ItemStack(Material.LIGHTNING_ROD) : new ItemStack(Material.AIR));
    }

    private void applyLook(ArmorStand stand, QuarryMinion minion, long now) {
        QuarryType type = minion.quarryType();
        int level = minion.level();
        stand.setCustomName(GuildFormat.nametag(type, level, preview(minion)));
        stand.setCustomNameVisible(true);
        double phase = ((now / 50.0) + Math.floorMod(minion.id().hashCode(), 40)) / 8.0;
        double swing = Math.toRadians(-12 - 52 * Math.abs(Math.sin(phase)));
        stand.setRightArmPose(new EulerAngle(swing, 0, Math.toRadians(8)));
        if (level >= 5) {
            stand.setHeadPose(new EulerAngle(Math.toRadians(18), Math.toRadians((now / 40.0) * (8 + level * 6)), 0));
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(-80), 0, Math.toRadians(-12)));
        } else {
            stand.setHeadPose(new EulerAngle(Math.toRadians(8), 0, 0));
            stand.setLeftArmPose(new EulerAngle(0, 0, 0));
        }
        if (now > 0) {
            spawnWorkParticles(stand.getLocation().add(0, 0.55, 0), type, level, visualHelmet(type, level));
        }
    }

    private void spawnWorkParticles(Location at, QuarryType type, int level, Material helmet) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        int count = 1 + level / 2;
        try {
            world.spawnParticle(Particle.BLOCK, at, count, 0.18, 0.22, 0.18, 0.02, helmet.createBlockData());
        } catch (IllegalArgumentException ignored) {
        }
        if (level >= 3) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.05, 0.12, 0.05, 0.001);
        }
        if (level >= 5) {
            world.spawnParticle(type == QuarryType.OAK_LOG ? Particle.HAPPY_VILLAGER : Particle.CRIT, at, 2, 0.15, 0.2, 0.15, 0.01);
        }
        if (level >= 6) {
            world.spawnParticle(Particle.END_ROD, at, 1 + (level - 5), 0.12, 0.25, 0.12, 0.01);
        }
        if (level >= 7) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, at, 3, 0.2, 0.25, 0.2, 0.01);
        }
    }

    private static Material visualHelmet(QuarryType type, int level) {
        int tier = Math.max(1, Math.min(QuarryType.MAX_LEVEL, level));
        return switch (type) {
            case COAL -> tier <= 2 ? Material.COAL_ORE : Material.COAL_BLOCK;
            case RAW_IRON -> tier <= 2 ? Material.IRON_ORE : tier <= 4 ? Material.RAW_IRON_BLOCK : Material.IRON_BLOCK;
            case RAW_GOLD -> tier <= 2 ? Material.GOLD_ORE : tier <= 4 ? Material.RAW_GOLD_BLOCK : Material.GOLD_BLOCK;
            case RAW_COPPER -> tier <= 2 ? Material.COPPER_ORE : tier <= 4 ? Material.RAW_COPPER_BLOCK : Material.COPPER_BLOCK;
            case REDSTONE -> tier <= 2 ? Material.REDSTONE_ORE : Material.REDSTONE_BLOCK;
            case LAPIS -> tier <= 2 ? Material.LAPIS_ORE : Material.LAPIS_BLOCK;
            case DIAMOND -> tier <= 3 ? Material.DIAMOND_ORE : Material.DIAMOND_BLOCK;
            case EMERALD -> tier <= 3 ? Material.EMERALD_ORE : Material.EMERALD_BLOCK;
            case OAK_LOG -> tier <= 2 ? Material.OAK_LOG : tier <= 5 ? Material.STRIPPED_OAK_LOG : Material.OAK_WOOD;
            case BIRCH_LOG -> tier <= 2 ? Material.BIRCH_LOG : Material.BIRCH_WOOD;
            case SPRUCE_LOG -> tier <= 2 ? Material.SPRUCE_LOG : Material.SPRUCE_WOOD;
            case JUNGLE_LOG -> tier <= 2 ? Material.JUNGLE_LOG : Material.JUNGLE_WOOD;
            case ACACIA_LOG -> tier <= 2 ? Material.ACACIA_LOG : Material.ACACIA_WOOD;
            case DARK_OAK_LOG -> tier <= 2 ? Material.DARK_OAK_LOG : Material.DARK_OAK_WOOD;
            case MANGROVE_LOG -> tier <= 2 ? Material.MANGROVE_LOG : Material.MANGROVE_WOOD;
            case CHERRY_LOG -> tier <= 2 ? Material.CHERRY_LOG : Material.CHERRY_WOOD;
            case BAMBOO_BLOCK -> Material.BAMBOO_BLOCK;
            case CRIMSON_STEM -> tier <= 2 ? Material.CRIMSON_STEM : Material.CRIMSON_HYPHAE;
            case WARPED_STEM -> tier <= 2 ? Material.WARPED_STEM : Material.WARPED_HYPHAE;
            case LEATHER -> Material.BROWN_WOOL;
            case BONE -> Material.BONE_BLOCK;
            case STRING, FEATHER -> Material.WHITE_WOOL;
            case WHEAT -> Material.HAY_BLOCK;
            case CARROT -> Material.ORANGE_WOOL;
            case POTATO -> Material.BROWN_TERRACOTTA;
            case GUNPOWDER -> Material.TNT;
            case ROTTEN_FLESH -> Material.NETHERRACK;
            case COD -> Material.DRIED_KELP_BLOCK;
            case COBBLESTONE -> tier <= 2 ? Material.COBBLESTONE : tier <= 4 ? Material.STONE : Material.SMOOTH_STONE;
        };
    }

    private static ItemStack toolItem(QuarryType type, int level) {
        boolean hoe = type == QuarryType.WHEAT
                || type == QuarryType.CARROT
                || type == QuarryType.POTATO
                || type == QuarryType.LEATHER
                || type == QuarryType.ROTTEN_FLESH
                || type == QuarryType.BONE;
        boolean shears = type == QuarryType.STRING || type == QuarryType.FEATHER;
        boolean axe = type.name().endsWith("_LOG")
                || type == QuarryType.BAMBOO_BLOCK
                || type == QuarryType.CRIMSON_STEM
                || type == QuarryType.WARPED_STEM;
        boolean rod = type == QuarryType.COD;
        Material material;
        if (shears) {
            material = Material.SHEARS;
        } else if (rod) {
            material = Material.FISHING_ROD;
        } else {
            material = switch (Math.max(1, Math.min(QuarryType.MAX_LEVEL, level))) {
                case 1, 2 -> hoe ? Material.WOODEN_HOE : axe ? Material.WOODEN_AXE : Material.WOODEN_PICKAXE;
                case 3 -> hoe ? Material.STONE_HOE : axe ? Material.STONE_AXE : Material.STONE_PICKAXE;
                case 4 -> hoe ? Material.IRON_HOE : axe ? Material.IRON_AXE : Material.IRON_PICKAXE;
                case 5 -> hoe ? Material.DIAMOND_HOE : axe ? Material.DIAMOND_AXE : Material.DIAMOND_PICKAXE;
                default -> hoe ? Material.NETHERITE_HOE : axe ? Material.NETHERITE_AXE : Material.NETHERITE_PICKAXE;
            };
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void collect(Player player, Entity stand) {
        QuarryRef ref = resolve(stand);
        if (ref == null) {
            return;
        }
        collect(player, ref);
    }

    public void collect(Player player, Guild guild, QuarryMinion minion) {
        collect(player, new QuarryRef(guild, null, minion));
    }

    public void collect(Player player, PersonalIsland island, QuarryMinion minion) {
        collect(player, new QuarryRef(null, island, minion));
    }

    public void collect(Player player, QuarryRef ref) {
        CollectResult result = collectInto(player, ref);
        if (result == null) {
            return;
        }
        if (result.empty()) {
            player.sendMessage("§7The quarry is empty. It fills while the server runs.");
            return;
        }
        player.sendMessage(result.message(ref.minion().quarryType()));
    }

    public void collectAll(Player player, Guild guild) {
        if (guild == null || player == null) {
            return;
        }
        if (guild.rank(player.getUniqueId()) == null || !guild.rank(player.getUniqueId()).canCollectQuarry()) {
            player.sendMessage("§cOnly guild members can collect here.");
            return;
        }
        int quarries = 0;
        int compacted = 0;
        int compressed = 0;
        int raw = 0;
        for (QuarryMinion minion : List.copyOf(guild.minions())) {
            CollectResult result = collectInto(player, new QuarryRef(guild, null, minion));
            if (result == null || result.empty()) {
                continue;
            }
            quarries++;
            compacted += result.compacted();
            compressed += result.compressed();
            raw += result.raw();
        }
        guilds.save();
        if (quarries <= 0) {
            player.sendMessage("§7Nothing to collect from your quarries.");
            return;
        }
        player.sendMessage("§aCollected from §f" + quarries + " §aquarries: "
                + (compacted > 0 ? "§b" + compacted + " compacted " : "")
                + (compressed > 0 ? "§a" + compressed + " compressed " : "")
                + (raw > 0 ? "§e" + raw + " raw" : ""));
    }

    public void collectAll(Player player, PersonalIsland island) {
        if (island == null || player == null) {
            return;
        }
        if (!island.ownerId().equals(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can collect here.");
            return;
        }
        int quarries = 0;
        int compacted = 0;
        int compressed = 0;
        int raw = 0;
        for (QuarryMinion minion : List.copyOf(island.minions())) {
            CollectResult result = collectInto(player, new QuarryRef(null, island, minion));
            if (result == null || result.empty()) {
                continue;
            }
            quarries++;
            compacted += result.compacted();
            compressed += result.compressed();
            raw += result.raw();
        }
        if (personal != null) {
            personal.save();
        }
        if (quarries <= 0) {
            player.sendMessage("§7Nothing to collect from your quarries.");
            return;
        }
        player.sendMessage("§aCollected from §f" + quarries + " §aquarries: "
                + (compacted > 0 ? "§b" + compacted + " compacted " : "")
                + (compressed > 0 ? "§a" + compressed + " compressed " : "")
                + (raw > 0 ? "§e" + raw + " raw" : ""));
    }

    public boolean pickup(Player player, Guild guild, QuarryMinion minion) {
        return pickup(player, new QuarryRef(guild, null, minion));
    }

    public boolean pickup(Player player, PersonalIsland island, QuarryMinion minion) {
        return pickup(player, new QuarryRef(null, island, minion));
    }

    public boolean pickup(Player player, QuarryRef ref) {
        if (player == null || ref == null || ref.minion() == null) {
            return false;
        }
        if (!canManage(player, ref)) {
            player.sendMessage(ref.isPersonal()
                    ? "§cOnly the island owner can move quarries."
                    : "§cSoldiers and above can move quarries.");
            return false;
        }
        QuarryMinion minion = ref.minion();
        catchUp(minion);
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage("§cYour inventory is full.");
            return false;
        }
        ItemStack item = createQuarryItem(
                minion.quarryType(),
                minion.level(),
                minion.processor(),
                minion.stored(),
                minion.storedCompressed(),
                minion.storedCompacted()
        );
        player.getInventory().addItem(item);
        removeVisual(minion);
        if (ref.isPersonal()) {
            ref.island().minions().remove(minion);
            if (personal != null) {
                personal.save();
            }
        } else {
            ref.guild().minions().remove(minion);
            guilds.save();
        }
        player.sendMessage("§aPicked up §f" + minion.quarryType().display() + "§a. Place it again to keep level, mill and storage.");
        return true;
    }

    private CollectResult collectInto(Player player, Guild guild, QuarryMinion minion) {
        return collectInto(player, new QuarryRef(guild, null, minion));
    }

    private CollectResult collectInto(Player player, QuarryRef ref) {
        if (!canCollect(player, ref)) {
            player.sendMessage(ref.isPersonal()
                    ? "§cOnly the island owner can collect here."
                    : "§cOnly guild members can collect here.");
            return null;
        }
        QuarryMinion minion = ref.minion();
        catchUp(minion);
        QuarryType type = minion.quarryType();
        if (minion.isEmpty()) {
            return CollectResult.EMPTY;
        }
        int compacted = 0;
        int compressed = 0;
        if (AetherionItemsAccess.available()) {
            if (minion.storedCompacted() > 0) {
                ItemStack packed = processedItem(type, QuarryMinion.Processor.COMPACTED);
                if (packed != null) {
                    compacted = giveAmount(player, packed, minion.storedCompacted());
                    minion.setStoredCompacted(minion.storedCompacted() - compacted);
                }
            }
            if (minion.storedCompressed() > 0) {
                ItemStack packs = processedItem(type, QuarryMinion.Processor.COMPRESSED);
                if (packs != null) {
                    compressed = giveAmount(player, packs, minion.storedCompressed());
                    minion.setStoredCompressed(minion.storedCompressed() - compressed);
                }
            }
        }
        int stored = minion.stored();
        int given = 0;
        while (stored > 0) {
            int stack = Math.min(64, stored);
            ItemStack drop = new ItemStack(type.product(), stack);
            var leftover = player.getInventory().addItem(drop);
            if (leftover.isEmpty()) {
                stored -= stack;
                given += stack;
            } else {
                int left = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                stored -= (stack - left);
                given += (stack - left);
                break;
            }
        }
        minion.setStored(stored);
        saveHost(ref);
        return new CollectResult(compacted, compressed, given);
    }

    public record CollectResult(int compacted, int compressed, int raw) {
        static final CollectResult EMPTY = new CollectResult(0, 0, 0);

        boolean empty() {
            return compacted <= 0 && compressed <= 0 && raw <= 0;
        }

        String message(QuarryType type) {
            List<String> parts = new ArrayList<>();
            if (compacted > 0) {
                parts.add("§b" + compacted + " Compacted " + type.productName());
            }
            if (compressed > 0) {
                parts.add("§a" + compressed + " Compressed " + type.productName());
            }
            if (raw > 0) {
                parts.add("§e" + raw + " " + type.productName());
            }
            return "§aCollected " + String.join("§7, ", parts) + "§a.";
        }
    }

    public boolean installProcessor(Player player, QuarryMinion minion, ItemStack item) {
        String itemId = aetherionId(item);
        QuarryMinion.Processor next;
        if ("quarry_compressor".equals(itemId)) {
            next = QuarryMinion.Processor.COMPRESSED;
        } else if ("quarry_compactor".equals(itemId)) {
            next = QuarryMinion.Processor.COMPACTED;
        } else {
            return false;
        }
        QuarryRef ref = findHost(player, minion);
        if (ref == null || !canManage(player, ref)) {
            player.sendMessage(ref != null && ref.isPersonal()
                    ? "§cOnly the island owner can install quarry mills."
                    : "§cSoldiers and above can install quarry mills.");
            return false;
        }
        if (minion.processor() == next || minion.processor().ordinal() > next.ordinal()) {
            player.sendMessage("§cThis quarry already has that mill, or a better one.");
            return false;
        }
        if (item.getAmount() < 1) {
            return false;
        }
        item.setAmount(item.getAmount() - 1);
        minion.setProcessor(next);
        if (AetherionItemsAccess.available()) {
            minion.processStorage();
        }
        respawnHostVisual(ref);
        saveHost(ref);
        if (next == QuarryMinion.Processor.COMPACTED) {
            player.sendMessage("§aInstalled Quarry Forge§a. It crafts Compacted, leftover becomes Compressed.");
        } else {
            player.sendMessage("§aInstalled Quarry Mill§a. It crafts Compressed while it runs.");
        }
        return true;
    }

    private ItemStack processedItem(QuarryType type, QuarryMinion.Processor processor) {
        try {
            Class<?> resourceClass = Class.forName("de.aetherion.items.economy.CompressedResource");
            Object resource = resourceClass.getMethod("valueOf", String.class).invoke(null, type.name());
            String method = processor == QuarryMinion.Processor.COMPACTED ? "compacted" : "compressed";
            Object created = resourceClass.getMethod(method).invoke(resource);
            return created instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private int giveAmount(Player player, ItemStack template, int amount) {
        int given = 0;
        int left = amount;
        while (left > 0) {
            int stack = Math.min(Math.max(1, template.getMaxStackSize()), left);
            ItemStack drop = template.clone();
            drop.setAmount(stack);
            var leftover = player.getInventory().addItem(drop);
            if (leftover.isEmpty()) {
                left -= stack;
                given += stack;
            } else {
                int remain = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                int accepted = stack - remain;
                left -= accepted;
                given += accepted;
                break;
            }
        }
        return given;
    }

    public record QuarryRef(Guild guild, PersonalIsland island, QuarryMinion minion) {
        public boolean isPersonal() {
            return island != null;
        }
    }

    public QuarryRef resolve(Entity stand) {
        if (stand == null) {
            return null;
        }
        String data = stand.getPersistentDataContainer().get(standKey(), PersistentDataType.STRING);
        if (data == null || !data.contains("|")) {
            return null;
        }
        try {
            if (data.startsWith(PERSONAL_PREFIX)) {
                String[] parts = data.substring(PERSONAL_PREFIX.length()).split("\\|", 2);
                if (parts.length < 2 || personal == null) {
                    return null;
                }
                PersonalIsland island = personal.byOwner(UUID.fromString(parts[0]));
                UUID minionId = UUID.fromString(parts[1]);
                if (island == null) {
                    return null;
                }
                for (QuarryMinion candidate : island.minions()) {
                    if (candidate.id().equals(minionId)) {
                        return new QuarryRef(null, island, candidate);
                    }
                }
                return null;
            }
            String[] parts = data.split("\\|", 2);
            Guild guild = guilds.byId(UUID.fromString(parts[0]));
            UUID minionId = UUID.fromString(parts[1]);
            if (guild == null) {
                return null;
            }
            for (QuarryMinion candidate : guild.minions()) {
                if (candidate.id().equals(minionId)) {
                    return new QuarryRef(guild, null, candidate);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    public boolean upgrade(Player player, QuarryMinion minion) {
        QuarryRef ref = findHost(player, minion);
        if (ref == null || !canUpgrade(player, ref)) {
            player.sendMessage(ref != null && ref.isPersonal()
                    ? "§cOnly the island owner can upgrade quarries."
                    : "§cMayors and above can upgrade quarries.");
            return false;
        }
        if (!minion.canUpgrade()) {
            player.sendMessage("§cThis quarry is already max level.");
            return false;
        }
        QuarryType type = minion.quarryType();
        QuarryType.UpgradeCost cost = minion.nextUpgradeCost();
        if (cost.isEmpty()) {
            player.sendMessage("§cThis quarry cannot be upgraded further.");
            return false;
        }
        List<String> missing = missingCosts(player, type, cost);
        if (!missing.isEmpty()) {
            player.sendMessage("§cNeed " + String.join(" §7and §c", missing) + " §cto upgrade.");
            return false;
        }
        takeAetherion(player, type.compressedId(), cost.compressed());
        takeAetherion(player, type.compactedId(), cost.compacted());
        takeAetherion(player, QuarryType.CORE_ID, cost.cores());
        minion.setLevel(minion.level() + 1);
        respawnHostVisual(ref);
        saveHost(ref);
        player.sendMessage("§a" + type.display() + " upgraded to §fLv." + minion.level()
                + "§a. It now makes §f" + minion.perTick() + " " + type.productName() + " §aper tick.");
        player.sendMessage(QuarryType.upgradeLine(minion.level()));
        return true;
    }

    private QuarryRef findHost(Player player, QuarryMinion minion) {
        if (minion == null) {
            return null;
        }
        if (personal != null) {
            PersonalIsland island = personal.byOwner(player.getUniqueId());
            if (island != null) {
                for (QuarryMinion candidate : island.minions()) {
                    if (candidate.id().equals(minion.id())) {
                        return new QuarryRef(null, island, candidate);
                    }
                }
            }
            for (PersonalIsland other : personal.all()) {
                for (QuarryMinion candidate : other.minions()) {
                    if (candidate.id().equals(minion.id())) {
                        return new QuarryRef(null, other, candidate);
                    }
                }
            }
        }
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild != null) {
            for (QuarryMinion candidate : guild.minions()) {
                if (candidate.id().equals(minion.id())) {
                    return new QuarryRef(guild, null, candidate);
                }
            }
        }
        for (Guild candidateGuild : guilds.all()) {
            for (QuarryMinion candidate : candidateGuild.minions()) {
                if (candidate.id().equals(minion.id())) {
                    return new QuarryRef(candidateGuild, null, candidate);
                }
            }
        }
        return null;
    }

    private boolean canCollect(Player player, QuarryRef ref) {
        if (player == null || ref == null) {
            return false;
        }
        if (ref.isPersonal()) {
            return ref.island().ownerId().equals(player.getUniqueId());
        }
        return ref.guild() != null
                && ref.guild().rank(player.getUniqueId()) != null
                && ref.guild().rank(player.getUniqueId()).canCollectQuarry();
    }

    private boolean canManage(Player player, QuarryRef ref) {
        if (player == null || ref == null) {
            return false;
        }
        if (ref.isPersonal()) {
            return ref.island().ownerId().equals(player.getUniqueId());
        }
        return ref.guild() != null
                && ref.guild().rank(player.getUniqueId()) != null
                && ref.guild().rank(player.getUniqueId()).canPickupQuarry();
    }

    private boolean canUpgrade(Player player, QuarryRef ref) {
        if (player == null || ref == null) {
            return false;
        }
        if (ref.isPersonal()) {
            return ref.island().ownerId().equals(player.getUniqueId());
        }
        return ref.guild() != null
                && ref.guild().rank(player.getUniqueId()) != null
                && ref.guild().rank(player.getUniqueId()).canUpgradeQuarry();
    }

    private void saveHost(QuarryRef ref) {
        if (ref == null) {
            return;
        }
        if (ref.isPersonal()) {
            if (personal != null) {
                personal.save();
            }
            return;
        }
        guilds.save();
    }

    private void respawnHostVisual(QuarryRef ref) {
        if (ref == null) {
            return;
        }
        if (ref.isPersonal()) {
            spawnVisual(ref.island(), ref.minion());
            return;
        }
        spawnVisual(ref.guild(), ref.minion());
    }

    private List<String> missingCosts(Player player, QuarryType type, QuarryType.UpgradeCost cost) {
        List<String> missing = new ArrayList<>();
        if (countAetherion(player, type.compressedId()) < cost.compressed()) {
            missing.add("§f" + cost.compressed() + " Compressed " + type.productName());
        }
        if (countAetherion(player, type.compactedId()) < cost.compacted()) {
            missing.add("§f" + cost.compacted() + " Compacted " + type.productName());
        }
        if (countAetherion(player, QuarryType.CORE_ID) < cost.cores()) {
            missing.add("§f" + cost.cores() + " Quarry Core");
        }
        return missing;
    }

    private int countAetherion(Player player, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        int have = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemId.equals(aetherionId(item))) {
                have += item.getAmount();
            }
        }
        return have;
    }

    private void takeAetherion(Player player, String itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!itemId.equals(aetherionId(item))) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            left -= take;
            if (left <= 0) {
                return;
            }
        }
    }

    private static String aetherionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(AETHERION_ITEM, PersistentDataType.STRING);
    }

    public void respawnVisuals() {
        for (Guild guild : guilds.all()) {
            for (QuarryMinion minion : guild.minions()) {
                spawnVisual(guild, minion);
            }
        }
        if (personal != null) {
            for (PersonalIsland island : personal.all()) {
                for (QuarryMinion minion : island.minions()) {
                    spawnVisual(island, minion);
                }
            }
        }
    }
}
