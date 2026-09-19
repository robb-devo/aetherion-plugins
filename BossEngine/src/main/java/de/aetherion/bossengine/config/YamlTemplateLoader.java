package de.aetherion.bossengine.config;

import de.aetherion.bossengine.manager.SkillManager;
import de.aetherion.bossengine.model.BossAttributes;
import de.aetherion.bossengine.model.BossEquipment;
import de.aetherion.bossengine.model.BossLootTable;
import de.aetherion.bossengine.model.BossOptions;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.model.BossTemplate;
import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.LootEntry;
import de.aetherion.bossengine.model.LootSource;
import de.aetherion.bossengine.model.PhaseTransition;
import de.aetherion.bossengine.model.SpawnCondition;
import de.aetherion.bossengine.model.TransitionShape;
import de.aetherion.bossengine.skill.AbstractBossSkill;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class YamlTemplateLoader {

    private final SkillManager skillManager;
    private final Logger logger;

    public YamlTemplateLoader(SkillManager skillManager, Logger logger) {
        this.skillManager = skillManager;
        this.logger = logger;
    }

    public BossTemplate load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String fallbackId = stripExtension(file.getName());
        return load(yaml, fallbackId);
    }

    public BossTemplate load(YamlConfiguration yaml, String fallbackId) {
        String id = yaml.getString("id", fallbackId);
        String displayName = yaml.getString("display-name", id);
        EntityType entityType = parseEntity(yaml.getString("entity-type", "ZOMBIE"));
        String modelEngineId = yaml.getString("model-engine-id", "");

        BossAttributes attributes = readAttributes(yaml.getConfigurationSection("attributes"), BossAttributes.defaults());
        BossEquipment equipment = readEquipment(yaml.getConfigurationSection("equipment"));
        BossOptions options = readOptions(yaml.getConfigurationSection("options"));
        SpawnCondition conditions = readConditions(yaml.getConfigurationSection("conditions"));
        List<BossPhase> phases = readPhases(yaml);
        List<AbstractBossSkill> skills = skillManager.parseList(yaml.getMapList("skills"));
        BossLootTable loot = readLoot(yaml.getConfigurationSection("loot"));

        return new BossTemplate(
                id,
                displayName,
                entityType,
                modelEngineId,
                attributes,
                equipment,
                options,
                conditions,
                phases,
                skills,
                loot
        );
    }

    private List<BossPhase> readPhases(YamlConfiguration yaml) {
        List<BossPhase> phases = new ArrayList<>();
        List<Map<?, ?>> rawPhases = yaml.getMapList("phases");
        int index = 0;
        for (Map<?, ?> raw : rawPhases) {
            Object idRaw = raw.get("id");
            String id = idRaw == null ? "phase-" + (index++) : String.valueOf(idRaw);
            double health = asDouble(raw.get("health-percent"), 100);
            String displayName = raw.get("display-name") == null ? null : String.valueOf(raw.get("display-name"));
            BossAttributes attributes = null;
            Object attributesRaw = raw.get("attributes");
            if (attributesRaw instanceof Map<?, ?> attributeMap) {
                attributes = readAttributes(mapSection(attributeMap, "attributes"), null);
            }
            List<AbstractBossSkill> skills = List.of();
            Object skillsRaw = raw.get("skills");
            if (skillsRaw instanceof List<?> skillList) {
                skills = skillManager.parseList(castMapList(skillList));
            }
            PhaseTransition transition = readTransition(raw.get("transition"));
            phases.add(new BossPhase(id, health, displayName, attributes, skills, transition));
        }
        return phases;
    }

    @SuppressWarnings("unchecked")
    private PhaseTransition readTransition(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Particle particle = Particle.SOUL_FIRE_FLAME;
        Object particleRaw = map.get("particle");
        if (particleRaw != null) {
            try {
                particle = Particle.valueOf(String.valueOf(particleRaw).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Particle extraParticle = Particle.SOUL;
        Object extraRaw = map.get("extra-particle");
        if (extraRaw != null) {
            String extraName = String.valueOf(extraRaw).toUpperCase(Locale.ROOT);
            if (extraName.equals("NONE") || extraName.equals("FALSE") || extraName.equals("OFF")) {
                extraParticle = null;
            } else {
                try {
                    extraParticle = Particle.valueOf(extraName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        TransitionShape shape = TransitionShape.CIRCLE;
        Object shapeRaw = map.get("shape");
        if (shapeRaw != null) {
            try {
                shape = TransitionShape.valueOf(String.valueOf(shapeRaw).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        boolean explode = map.get("explode") instanceof Boolean flag && flag;
        return new PhaseTransition(
                asInt(map.get("duration-ticks"), 80),
                map.get("invulnerable") instanceof Boolean inv && inv || map.get("invulnerable") == null,
                map.get("freeze-ai") instanceof Boolean freeze && freeze || map.get("freeze-ai") == null,
                asDouble(map.get("start-radius"), 6.0),
                asDouble(map.get("end-radius"), 1.2),
                particle,
                extraParticle,
                asInt(map.get("points"), 48),
                asDouble(map.get("height"), 0.2),
                shape,
                explode,
                asDouble(map.get("explode-damage"), 0),
                asDouble(map.get("explode-radius"), 0),
                asDouble(map.get("explode-knockback"), 0),
                asDouble(map.get("hover-height"), 0),
                asInt(map.get("lightning-count"), 0),
                asDouble(map.get("under-damage"), 0),
                asDouble(map.get("under-radius"), 2.8)
        );
    }

    private BossAttributes readAttributes(ConfigurationSection section, BossAttributes fallback) {
        if (section == null) {
            return fallback;
        }
        BossAttributes base = fallback == null ? new BossAttributes(0, 0, 0, 0, 0, -1) : fallback;
        return new BossAttributes(
                section.getDouble("max-health", base.getMaxHealth()),
                section.getDouble("movement-speed", base.getMovementSpeed()),
                section.getDouble("attack-damage", base.getAttackDamage()),
                section.getDouble("scale", base.getScale()),
                section.getDouble("follow-range", base.getFollowRange()),
                section.getDouble("knockback-resistance", base.getKnockbackResistance())
        );
    }

    private BossEquipment readEquipment(ConfigurationSection section) {
        if (section == null) {
            return BossEquipment.empty();
        }
        return new BossEquipment(
                material(section.getString("helmet"), Material.AIR),
                material(section.getString("chestplate"), Material.AIR),
                material(section.getString("leggings"), Material.AIR),
                material(section.getString("boots"), Material.AIR),
                material(section.getString("main-hand"), Material.AIR),
                material(section.getString("off-hand"), Material.AIR),
                (float) section.getDouble("drop-chance", 0.0),
                parseColor(section.getString("leather-color"))
        );
    }

    private BossOptions readOptions(ConfigurationSection section) {
        BossOptions defaults = BossOptions.defaults();
        if (section == null) {
            return defaults;
        }
        return new BossOptions(
                section.getBoolean("glowing", defaults.isGlowing()),
                section.getBoolean("custom-name-visible", defaults.isCustomNameVisible()),
                section.getBoolean("silent", defaults.isSilent()),
                section.getInt("invulnerable-spawn-ticks", defaults.getInvulnerableSpawnTicks()),
                section.getBoolean("persistent", defaults.isPersistent()),
                section.getBoolean("remove-when-far-away", defaults.isRemoveWhenFarAway()),
                section.getBoolean("prevent-item-pickup", defaults.isPreventItemPickup())
        );
    }

    private SpawnCondition readConditions(ConfigurationSection section) {
        SpawnCondition defaults = SpawnCondition.defaults();
        if (section == null) {
            return defaults;
        }
        return new SpawnCondition(
                section.getInt("max-instances", defaults.getMaxInstances()),
                section.getDouble("leash-radius", defaults.getLeashRadius()),
                leash(section.getString("leash-action"))
        );
    }

    private BossLootTable readLoot(ConfigurationSection section) {
        if (section == null) {
            return BossLootTable.empty();
        }
        return new BossLootTable(
                section.getBoolean("damage-based", true),
                section.getDouble("participation-threshold-percent", 0),
                readLootList(section, "killer-bonus"),
                readLootList(section, "shared"),
                readLootList(section, "per-damager"),
                readTopDamagerItems(section),
                readTopDamagerCount(section),
                section.getInt("experience", 0),
                readRankLoot(section)
        );
    }

    private Map<Integer, List<LootEntry>> readRankLoot(ConfigurationSection section) {
        Map<Integer, List<LootEntry>> ranks = new java.util.HashMap<>();
        ConfigurationSection root = section.getConfigurationSection("rank-loot");
        if (root == null) {
            return ranks;
        }
        for (String key : root.getKeys(false)) {
            try {
                int rank = Integer.parseInt(key);
                ranks.put(rank, readLootList(root, key));
            } catch (NumberFormatException ignored) {
            }
        }
        return ranks;
    }

    private int readTopDamagerCount(ConfigurationSection section) {
        ConfigurationSection top = section.getConfigurationSection("top-damagers");
        if (top != null) {
            return Math.max(0, top.getInt("count", 3));
        }
        return 0;
    }

    private List<LootEntry> readTopDamagerItems(ConfigurationSection section) {
        ConfigurationSection top = section.getConfigurationSection("top-damagers");
        if (top != null) {
            return readLootList(top, "items");
        }
        return readLootList(section, "top-damagers");
    }

    private List<LootEntry> readLootList(ConfigurationSection parent, String path) {
        List<LootEntry> entries = new ArrayList<>();
        List<Map<?, ?>> rawList = parent.getMapList(path);
        for (Map<?, ?> raw : rawList) {
            Object sourceRaw = raw.get("source");
            LootSource source = lootSource(sourceRaw == null ? "VANILLA" : String.valueOf(sourceRaw));
            String itemId = raw.get("item-id") == null ? null : String.valueOf(raw.get("item-id"));
            Material material = material(raw.get("material") == null ? null : String.valueOf(raw.get("material")), Material.STONE);
            int amount = asInt(raw.get("amount"), 1);
            int minAmount = asInt(raw.get("min-amount"), amount);
            int maxAmount = asInt(raw.get("max-amount"), amount);
            double chance = asDouble(raw.get("chance"), 1.0);
            boolean scale = raw.get("scale-with-damage") instanceof Boolean flag && flag;
            entries.add(new LootEntry(source, itemId, material, minAmount, maxAmount, chance, scale));
        }
        return entries;
    }

    private ConfigurationSection mapSection(Map<?, ?> raw, String name) {
        org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
        ConfigurationSection section = memory.createSection(name);
        raw.forEach((key, value) -> {
            if (key != null) {
                section.set(String.valueOf(key), value);
            }
        });
        return section;
    }

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> castMapList(List<?> raw) {
        List<Map<?, ?>> list = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map) {
                list.add(map);
            }
        }
        return list;
    }

    private static EntityType parseEntity(String raw) {
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return EntityType.ZOMBIE;
        }
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private static LeashAction leash(String raw) {
        try {
            return LeashAction.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return LeashAction.TELEPORT;
        }
    }

    private static LootSource lootSource(String raw) {
        try {
            return LootSource.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return LootSource.VANILLA;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int asInt(Object value, int fallback) {
        return (int) Math.round(asDouble(value, fallback));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static org.bukkit.Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String hex = raw.trim().replace("#", "");
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
