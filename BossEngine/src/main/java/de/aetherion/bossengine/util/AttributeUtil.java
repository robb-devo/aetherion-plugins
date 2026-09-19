package de.aetherion.bossengine.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/**
 * Paper 1.21 renamed GENERIC_* attributes. Resolve by enum name and key,
 * and never throw when a value sits above vanilla's 1024 max-health cap.
 */
public final class AttributeUtil {

    private AttributeUtil() {
    }

    public static Attribute maxHealth() {
        return first("MAX_HEALTH", "GENERIC_MAX_HEALTH");
    }

    public static Attribute movementSpeed() {
        return first("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
    }

    public static Attribute attackDamage() {
        return first("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
    }

    public static Attribute followRange() {
        return first("FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE");
    }

    public static Attribute knockbackResistance() {
        return first("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE");
    }

    public static Attribute scale() {
        return first("SCALE", "GENERIC_SCALE");
    }

    public static void setBase(LivingEntity entity, Attribute attribute, double value) {
        if (entity == null || attribute == null) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double clamped = value;
        try {
            instance.setBaseValue(clamped);
            return;
        } catch (IllegalArgumentException first) {
            clamped = Math.min(value, 1024.0);
        }
        try {
            instance.setBaseValue(clamped);
        } catch (IllegalArgumentException ignored) {
            // attribute exists but this entity will not accept a write
        }
    }

    public static double getValue(LivingEntity entity, Attribute attribute, double fallback) {
        if (entity == null || attribute == null) {
            return fallback;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return fallback;
        }
        return instance.getValue();
    }

    public static double getBase(LivingEntity entity, Attribute attribute, double fallback) {
        if (entity == null || attribute == null) {
            return fallback;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return fallback;
        }
        return instance.getBaseValue();
    }

    public static double vanillaMaxHealth(LivingEntity entity) {
        return getValue(entity, maxHealth(), entity.getHealth());
    }

    public static void setMaxHealth(LivingEntity entity, double maxHealth) {
        setBase(entity, maxHealth(), maxHealth);
        double allowed = vanillaMaxHealth(entity);
        if (allowed > 0 && entity.getHealth() > allowed) {
            entity.setHealth(allowed);
        }
    }

    public static Attribute first(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Attribute attribute : Attribute.values()) {
            String key = attribute.getKey().getKey();
            String enumName = attribute.name();
            for (String name : names) {
                String needle = name.toLowerCase().replace("generic_", "").replace('.', '_');
                if (enumName.equalsIgnoreCase(name)
                        || key.equalsIgnoreCase(name)
                        || key.equalsIgnoreCase("generic." + needle)
                        || key.endsWith(needle.replace('_', '.'))
                        || key.replace("generic.", "").replace('.', '_').equalsIgnoreCase(needle)) {
                    return attribute;
                }
            }
        }
        return null;
    }
}
