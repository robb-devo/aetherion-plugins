package de.aetherion.bossengine.item;

import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BossSpawnItemService {

    private final JavaPlugin plugin;
    private final BossKeys keys;
    private final Map<String, SpawnItemDefinition> items = new ConcurrentHashMap<>();

    public BossSpawnItemService(JavaPlugin plugin, BossKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public void reload() {
        items.clear();
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = ensureDefaultItems(yaml);
        if (changed) {
            try {
                yaml.save(file);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not update items.yml: " + exception.getMessage());
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("spawn-items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", "NETHER_STAR"));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(TextUtil.color(line));
            }
            SpawnItemDefinition definition = new SpawnItemDefinition(
                    id,
                    section.getString("boss", id),
                    material == null ? Material.NETHER_STAR : material,
                    section.getString("display-name", id),
                    lore,
                    section.getBoolean("glowing", true),
                    section.getBoolean("consume", true),
                    section.getInt("cooldown-seconds", 3),
                    section.getBoolean("require-altar", false),
                    parseMode(section.getString("mode"))
            );
            items.put(id.toLowerCase(Locale.ROOT), definition);
        }
    }

    public Optional<SpawnItemDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<SpawnItemDefinition> fromItem(ItemStack item) {
        return keys.spawnItemId(item).flatMap(this::get);
    }

    public Collection<SpawnItemDefinition> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public ItemStack create(SpawnItemDefinition definition) {
        ItemStack item = new ItemStack(definition.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.component(definition.getDisplayName()));
            meta.lore(definition.getLore().stream().map(TextUtil::component).toList());
            keys.tagSpawnItem(meta, definition.getId());
            if (definition.isGlowing()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private SpawnItemMode parseMode(String raw) {
        if (raw == null) {
            return SpawnItemMode.SUMMON;
        }
        try {
            return SpawnItemMode.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return SpawnItemMode.SUMMON;
        }
    }

    private boolean ensureDefaultItems(FileConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("spawn-items");
        if (root == null) {
            root = yaml.createSection("spawn-items");
        }
        boolean changed = false;
        if (!root.isConfigurationSection("hollow_lurker_anchor")) {
            ConfigurationSection section = root.createSection("hollow_lurker_anchor");
            section.set("boss", "hollow_lurker");
            section.set("mode", "SET_SPAWN");
            section.set("material", "RECOVERY_COMPASS");
            section.set("display-name", "&8&lHollow Anchor");
            section.set("lore", List.of(
                    "&7Right-click a cave block to lock his home.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("hollow_lurker_core")) {
            ConfigurationSection section = root.createSection("hollow_lurker_core");
            section.set("boss", "hollow_lurker");
            section.set("mode", "SUMMON");
            section.set("material", "ROTTEN_FLESH");
            section.set("display-name", "&8Hollow Lurker Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("skuldugery_anchor")) {
            ConfigurationSection section = root.createSection("skuldugery_anchor");
            section.set("boss", "skuldugery");
            section.set("mode", "SET_SPAWN");
            section.set("material", "BLAZE_ROD");
            section.set("display-name", "&6&lSkuldugery Anchor");
            section.set("lore", List.of(
                    "&7Right-click a block to lock his home.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("skuldugery_core")) {
            ConfigurationSection section = root.createSection("skuldugery_core");
            section.set("boss", "skuldugery");
            section.set("mode", "SUMMON");
            section.set("material", "SKELETON_SKULL");
            section.set("display-name", "&6Skuldugery Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("mcnugget_anchor")) {
            ConfigurationSection section = root.createSection("mcnugget_anchor");
            section.set("boss", "mcnugget");
            section.set("mode", "SET_SPAWN");
            section.set("material", "FEATHER");
            section.set("display-name", "&6&lMcNugget Anchor");
            section.set("lore", List.of(
                    "&7Right-click a block to lock his coop.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("mcnugget_core")) {
            ConfigurationSection section = root.createSection("mcnugget_core");
            section.set("boss", "mcnugget");
            section.set("mode", "SUMMON");
            section.set("material", "EGG");
            section.set("display-name", "&6McNugget Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("bridge_troll_anchor")) {
            ConfigurationSection section = root.createSection("bridge_troll_anchor");
            section.set("boss", "bridge_troll");
            section.set("mode", "SET_SPAWN");
            section.set("material", "ANVIL");
            section.set("display-name", "&6&lBridge Troll Anchor");
            section.set("lore", List.of(
                    "&7Right-click a block to lock his bridge.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("bridge_troll_core")) {
            ConfigurationSection section = root.createSection("bridge_troll_core");
            section.set("boss", "bridge_troll");
            section.set("mode", "SUMMON");
            section.set("material", "GOLDEN_AXE");
            section.set("display-name", "&6Bridge Troll Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("squidward_anchor")) {
            ConfigurationSection section = root.createSection("squidward_anchor");
            section.set("boss", "squidward");
            section.set("mode", "SET_SPAWN");
            section.set("material", "HEART_OF_THE_SEA");
            section.set("display-name", "&8&lSquidward Anchor");
            section.set("lore", List.of(
                    "&7Right-click a water block to lock his den.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("squidward_core")) {
            ConfigurationSection section = root.createSection("squidward_core");
            section.set("boss", "squidward");
            section.set("mode", "SUMMON");
            section.set("material", "INK_SAC");
            section.set("display-name", "&8Squidward Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("aetherion_anchor")) {
            ConfigurationSection section = root.createSection("aetherion_anchor");
            section.set("boss", "aetherion");
            section.set("mode", "SET_SPAWN");
            section.set("material", "DRAGON_EGG");
            section.set("display-name", "&5&lAetherion Anchor");
            section.set("lore", List.of(
                    "&7Right-click a block to lock his arena.",
                    "&7He spawns there, stays leashed, and returns after 5m.",
                    "&8Admin · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", false);
            section.set("cooldown-seconds", 1);
            section.set("require-altar", false);
            changed = true;
        }
        if (!root.isConfigurationSection("aetherion_core")) {
            ConfigurationSection section = root.createSection("aetherion_core");
            section.set("boss", "aetherion");
            section.set("mode", "SUMMON");
            section.set("material", "END_CRYSTAL");
            section.set("display-name", "&5Aetherion Core");
            section.set("lore", List.of(
                    "&7Right-click to summon him here (test).",
                    "&8Test-Item · BossEngine"
            ));
            section.set("glowing", true);
            section.set("consume", true);
            section.set("cooldown-seconds", 3);
            section.set("require-altar", false);
            changed = true;
        }
        changed |= ensureItem(root, "sir_balthazar_anchor", "sir_balthazar", "SET_SPAWN",
                "BLAZE_ROD", "&5&lBalthazar Anchor",
                List.of("&7Right-click a block to lock his lecture hall.", "&8Admin · BossEngine · T2"), false);
        changed |= ensureItem(root, "sir_balthazar_core", "sir_balthazar", "SUMMON",
                "TOTEM_OF_UNDYING", "&5Balthazar Core",
                List.of("&7Right-click to summon him here (test).", "&8Test-Item · BossEngine · T2"), true);
        changed |= ensureItem(root, "lobby_cleaner_anchor", "lobby_cleaner", "SET_SPAWN",
                "ENDER_EYE", "&5&lLobby Cleaner Anchor",
                List.of("&7Right-click a block to lock his closet.", "&8Admin · BossEngine · T2"), false);
        changed |= ensureItem(root, "lobby_cleaner_core", "lobby_cleaner", "SUMMON",
                "HOPPER", "&5Lobby Cleaner Core",
                List.of("&7Right-click to summon him here (test).", "&8Test-Item · BossEngine · T2"), true);
        changed |= ensureItem(root, "sparky_anchor", "sparky", "SET_SPAWN",
                "MAGMA_CREAM", "&6&lSparky Anchor",
                List.of("&7Right-click a block to lock the quarry accident.", "&8Admin · BossEngine · T2"), false);
        changed |= ensureItem(root, "sparky_core", "sparky", "SUMMON",
                "FIRE_CHARGE", "&6Sparky Core",
                List.of("&7Right-click to summon him here (test).", "&8Test-Item · BossEngine · T2"), true);
        changed |= ensureItem(root, "baron_von_wurm_anchor", "baron_von_wurm", "SET_SPAWN",
                "IRON_PICKAXE", "&8&lBaron von Wurm Anchor",
                List.of("&7Right-click a block to lock his hole.", "&8Admin · BossEngine · T2"), false);
        changed |= ensureItem(root, "baron_von_wurm_core", "baron_von_wurm", "SUMMON",
                "STONE", "&8Baron von Wurm Core",
                List.of("&7Right-click to summon him here (test).", "&8Test-Item · BossEngine · T2"), true);
        changed |= ensureItem(root, "insolvent_wither_anchor", "insolvent_wither", "SET_SPAWN",
                "NETHER_STAR", "&8&lInsolvent Wither Anchor",
                List.of("&7Right-click a block to lock the foreclosure.", "&8Admin · BossEngine · T2"), false);
        changed |= ensureItem(root, "insolvent_wither_core", "insolvent_wither", "SUMMON",
                "GOLD_INGOT", "&8Insolvent Wither Core",
                List.of("&7Right-click to summon him here (test).", "&8Test-Item · BossEngine · T2"), true);

        // Sandbox / Test Arena prototypes (no live anchors)
        changed |= ensureItem(root, "test_echo_core", "test_echo", "SUMMON",
                "AMETHYST_SHARD", "&dEcho Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_parity_core", "test_parity", "SUMMON",
                "PRISMARINE_CRYSTALS", "&bParity Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_curator_core", "test_curator", "SUMMON",
                "BOOK", "&6Curator Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_nullspace_core", "test_nullspace", "SUMMON",
                "ENDER_EYE", "&5Nullspace Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_loadbearing_core", "test_loadbearing", "SUMMON",
                "LODESTONE", "&7Load-Bearing Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_softlock_core", "test_softlock", "SUMMON",
                "SHULKER_SHELL", "&cSoftlock Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_heartbeat_core", "test_heartbeat", "SUMMON",
                "REDSTONE", "&4Heartbeat Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_broker_core", "test_broker", "SUMMON",
                "GOLD_NUGGET", "&eBroker Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_afterimage_core", "test_afterimage", "SUMMON",
                "PHANTOM_MEMBRANE", "&8Afterimage Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_gravity_core", "test_gravity", "SUMMON",
                "MAGMA_CREAM", "&3Gravity Clerk Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_quiet_core", "test_quiet", "SUMMON",
                "SCULK_SENSOR", "&fQuiet Room Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        changed |= ensureItem(root, "test_petjury_core", "test_petjury", "SUMMON",
                "BONE", "&dPet Jury Core", List.of("&7Sandbox boss. Test Arena only.", "&8No drops."), true);
        return changed;
    }

    private boolean ensureItem(
            ConfigurationSection root,
            String id,
            String boss,
            String mode,
            String material,
            String displayName,
            List<String> lore,
            boolean consume
    ) {
        if (root.isConfigurationSection(id)) {
            return false;
        }
        ConfigurationSection section = root.createSection(id);
        section.set("boss", boss);
        section.set("mode", mode);
        section.set("material", material);
        section.set("display-name", displayName);
        section.set("lore", lore);
        section.set("glowing", true);
        section.set("consume", consume);
        section.set("cooldown-seconds", consume ? 3 : 1);
        section.set("require-altar", false);
        return true;
    }
}
