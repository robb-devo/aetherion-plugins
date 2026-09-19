package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.integration.worldguard.WorldGuardSpawnGuard;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.SkeletonUtil;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

public class MinionSpawnSkill extends AbstractBossSkill {

    private final EntityType entityType;
    private final int amount;
    private final int maxAlive;
    private final int lifespanTicks;
    private final double spread;
    private final double health;
    private final double scale;
    private final double damage;
    private final double speed;
    private final String customName;
    private final Material helmet;
    private final Material chestplate;
    private final Material leggings;
    private final Material boots;
    private final Material mainHand;
    private final Material offHand;
    private final Color leatherColor;
    private final double yOffset;
    private final String scoreboardTag;

    public MinionSpawnSkill(ConfigurationSection section) {
        super("MINION_SPAWN", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.entityType = parseType(section == null ? "ZOMBIE" : section.getString("entity-type", "ZOMBIE"));
        this.amount = section == null ? 2 : Math.max(1, section.getInt("amount", 2));
        this.maxAlive = section == null ? 8 : Math.max(1, section.getInt("max-alive", 8));
        int lifespan = section == null ? 400 : section.getInt("lifespan-ticks", 400);
        this.lifespanTicks = Math.max(0, lifespan);
        this.spread = section == null ? 2.5 : section.getDouble("spread", 2.5);
        this.health = section == null ? 0 : section.getDouble("health", 0);
        this.scale = section == null ? 0 : section.getDouble("scale", 0);
        this.damage = section == null ? 0 : section.getDouble("damage", 0);
        this.speed = section == null ? 0 : section.getDouble("speed", 0);
        this.customName = section == null ? null : section.getString("custom-name");
        this.helmet = material(section, "helmet");
        this.chestplate = material(section, "chestplate");
        this.leggings = material(section, "leggings");
        this.boots = material(section, "boots");
        this.mainHand = material(section, "main-hand");
        this.offHand = material(section, "off-hand");
        this.leatherColor = parseColor(section == null ? null : section.getString("leather-color"));
        this.yOffset = section == null ? 0 : section.getDouble("y-offset", 0);
        this.scoreboardTag = section == null ? null : section.getString("scoreboard-tag");
    }

    @Override
    public void execute(SkillContext context) {
        BossInstance instance = context.getInstance();
        LivingEntity boss = context.getEntity();
        World world = boss.getWorld();
        Plugin plugin = instance.getPlugin();
        BossKeys keys = instance.getKeys();
        Class<? extends Entity> entityClass = entityType.getEntityClass();

        if (entityClass == null) {
            return;
        }

        instance.cleanupInvalidMinions();
        int room = Math.max(0, maxAlive - instance.getMinions().size());
        int spawnCount = Math.min(amount, room);
        if (spawnCount <= 0) {
            return;
        }

        java.util.List<java.util.UUID> wave = new java.util.ArrayList<>();

        for (int i = 0; i < spawnCount; i++) {
            Location location = offset(boss.getLocation());
            WorldGuardSpawnGuard.runGuarded(() -> world.spawn(
                    location,
                    entityClass,
                    CreatureSpawnEvent.SpawnReason.CUSTOM,
                    spawned -> {
                        keys.tagMinion(spawned, instance.getInstanceId());
                        if (spawned instanceof LivingEntity living) {
                            living.setRemoveWhenFarAway(true);
                            living.setPersistent(false);
                            applyMinion(living, boss, instance);
                            instance.addMinion(living);
                            wave.add(living.getUniqueId());
                        }
                    }
            ));
        }

        if (lifespanTicks > 0) {
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> instance.despawnMinions(wave),
                    lifespanTicks
            );
        }
    }

    private void applyMinion(LivingEntity living, LivingEntity boss, BossInstance instance) {
        SkeletonUtil.prepareMinion(living);

        if (customName != null && !customName.isBlank()) {
            living.customName(TextUtil.component(customName));
            living.setCustomNameVisible(true);
        }

        if (health > 0) {
            double scaledHealth = instance.scaleHealth(health);
            AttributeUtil.setMaxHealth(living, scaledHealth);
            living.setHealth(Math.min(scaledHealth, AttributeUtil.vanillaMaxHealth(living)));
        }
        if (scale > 0) {
            AttributeUtil.setBase(living, AttributeUtil.scale(), scale);
        }
        if (damage > 0) {
            AttributeUtil.setBase(living, AttributeUtil.attackDamage(), instance.scaleDamage(damage));
        }
        if (speed > 0) {
            AttributeUtil.setBase(living, AttributeUtil.movementSpeed(), speed);
        }

        EntityEquipment equipment = living.getEquipment();
        if (equipment != null) {
            setSlot(equipment::setHelmet, equipment::setHelmetDropChance, helmet);
            setSlot(equipment::setChestplate, equipment::setChestplateDropChance, chestplate);
            setSlot(equipment::setLeggings, equipment::setLeggingsDropChance, leggings);
            setSlot(equipment::setBoots, equipment::setBootsDropChance, boots);
            setSlot(equipment::setItemInMainHand, equipment::setItemInMainHandDropChance, mainHand);
            setSlot(equipment::setItemInOffHand, equipment::setItemInOffHandDropChance, offHand);
        }

        if (living instanceof Mob minion && boss instanceof Mob owner && owner.getTarget() != null) {
            minion.setTarget(owner.getTarget());
        }
        if (scoreboardTag != null && !scoreboardTag.isBlank()) {
            living.addScoreboardTag(scoreboardTag);
        }
    }

    private void setSlot(
            java.util.function.Consumer<ItemStack> setter,
            java.util.function.Consumer<Float> chance,
            Material material
    ) {
        if (material == null || material.isAir()) {
            return;
        }
        ItemStack stack = new ItemStack(material);
        if (leatherColor != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(leatherColor);
            stack.setItemMeta(meta);
        }
        setter.accept(stack);
        chance.accept(0f);
    }

    private Location offset(Location origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double lift = yOffset <= 0 ? 0 : yOffset + random.nextDouble(0, Math.max(0.5, yOffset * 0.25));
        return origin.clone().add(
                random.nextDouble(-spread, spread),
                lift,
                random.nextDouble(-spread, spread)
        );
    }

    private static EntityType parseType(String raw) {
        try {
            return EntityType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return EntityType.ZOMBIE;
        }
    }

    private static Material material(ConfigurationSection section, String path) {
        if (section == null) {
            return Material.AIR;
        }
        String raw = section.getString(path);
        if (raw == null || raw.isBlank()) {
            return Material.AIR;
        }
        Material material = Material.matchMaterial(raw);
        return material == null ? Material.AIR : material;
    }

    private static Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String hex = raw.trim().replace("#", "");
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
