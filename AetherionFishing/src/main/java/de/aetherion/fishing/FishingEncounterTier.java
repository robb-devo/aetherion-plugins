package de.aetherion.fishing;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Fishing skill bands (every 10 levels) → weak special mobs with fishing-flavored drops.
 */
public enum FishingEncounterTier {

    DOCK_DREDGER(0, "Dock Dredger", EntityType.DROWNED, 8.0d, 1.5d, 0),
    BRACKISH_WALKER(1, "Brackish Walker", EntityType.DROWNED, 14.0d, 2.2d, 0),
    REEF_SCAVENGER(2, "Reef Scavenger", EntityType.DROWNED, 22.0d, 3.0d, 1),
    TIDE_RANGLER(3, "Tide Rangler", EntityType.DROWNED, 30.0d, 3.8d, 1),
    BRINE_SENTINEL(4, "Brine Sentinel", EntityType.DROWNED, 42.0d, 4.6d, 2),
    CURRENT_WRETCH(5, "Current Wretch", EntityType.GUARDIAN, 52.0d, 5.2d, 2),
    RIPTIDE_REAPER(6, "Riptide Reaper", EntityType.DROWNED, 64.0d, 6.0d, 3),
    ABYSSAL_CLAIMANT(7, "Abyssal Claimant", EntityType.GUARDIAN, 78.0d, 6.8d, 3),
    DEEP_AUCTIONEER(8, "Deep Auctioneer", EntityType.DROWNED, 92.0d, 7.5d, 4),
    LEVIATHAN_BAIT(9, "Leviathan Bait", EntityType.GUARDIAN, 110.0d, 8.5d, 4);

    private final int band;
    private final String displayName;
    private final EntityType type;
    private final double health;
    private final double damage;
    private final int lootQuality;

    FishingEncounterTier(
            int band,
            String displayName,
            EntityType type,
            double health,
            double damage,
            int lootQuality
    ) {
        this.band = band;
        this.displayName = displayName;
        this.type = type;
        this.health = health;
        this.damage = damage;
        this.lootQuality = lootQuality;
    }

    public int band() {
        return band;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType type() {
        return type;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public int lootQuality() {
        return lootQuality;
    }

    public String coloredName() {
        ChatColor color = switch (lootQuality) {
            case 0 -> ChatColor.AQUA;
            case 1 -> ChatColor.DARK_AQUA;
            case 2 -> ChatColor.BLUE;
            case 3 -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.DARK_PURPLE;
        };
        return color + displayName;
    }

    public static FishingEncounterTier forFishingLevel(int fishingLevel) {
        int level = Math.max(1, Math.min(100, fishingLevel));
        int band = Math.min(9, (level - 1) / 10);
        for (FishingEncounterTier tier : values()) {
            if (tier.band == band) {
                return tier;
            }
        }
        return DOCK_DREDGER;
    }

    public void dress(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.customName(net.kyori.adventure.text.Component.text(coloredName()));
        entity.setCustomNameVisible(true);
        AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            entity.setHealth(health);
        }
        AttributeInstance attack = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(damage);
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        if (entity instanceof Drowned) {
            equipment.setHelmet(tinted(Material.LEATHER_HELMET, Color.fromRGB(40, 70, 90)));
            equipment.setHelmetDropChance(0f);
            equipment.setChestplate(tinted(Material.LEATHER_CHESTPLATE, Color.fromRGB(30, 55, 75)));
            equipment.setChestplateDropChance(0f);
            if (lootQuality >= 2) {
                equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
                equipment.setItemInMainHandDropChance(0f);
            } else {
                equipment.setItemInMainHand(new ItemStack(Material.FISHING_ROD));
                equipment.setItemInMainHandDropChance(0f);
            }
        } else if (entity instanceof Guardian) {
            // Guardians keep native look; lightly slower so they stay snackable.
            AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(Math.max(0.2d, speed.getBaseValue() * 0.85d));
            }
        }
    }

    private static ItemStack tinted(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }
}
