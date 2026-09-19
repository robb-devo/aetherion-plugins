package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.skill.AetherSkill;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AccessoryItems {

    public static final int STAT_REV = 3;
    public static final String SHINY_ID = "charm_shiny";
    public static final String FORGE_ID = "charm_forge";
    public static final String ESTATE_ID = "charm_estate";
    public static final double BASE_SHINY_CHANCE = 1.0d / 4096.0d;
    public static final double CHARM_SHINY_CHANCE = 1.0d / 256.0d;
    public static final double SHINY_RADIUS = 48.0d;

    public enum Charm {
        COMBAT(
                "charm_combat",
                AetherSkill.Category.COMBAT,
                Material.MAGMA_CREAM,
                Material.BLAZE_POWDER,
                Material.TOTEM_OF_UNDYING,
                "Grudge Marble",
                "Grudge Charm",
                "Grudge Relic"
        ),
        MINING(
                "charm_mining",
                AetherSkill.Category.MINING,
                Material.COMPASS,
                Material.CLOCK,
                Material.RECOVERY_COMPASS,
                "Survey Token",
                "Survey Charm",
                "Survey Relic"
        ),
        FORAGING(
                "charm_foraging",
                AetherSkill.Category.FORAGING,
                Material.OAK_SAPLING,
                Material.APPLE,
                Material.HONEYCOMB,
                "Grove Token",
                "Grove Charm",
                "Grove Relic"
        ),
        FARMING(
                "charm_farming",
                AetherSkill.Category.FARMING,
                Material.WHEAT_SEEDS,
                Material.BOWL,
                Material.FLOWER_POT,
                "Furrow Token",
                "Furrow Charm",
                "Furrow Relic"
        ),
        FISHING(
                "charm_fishing",
                AetherSkill.Category.FISHING,
                Material.NAUTILUS_SHELL,
                Material.HEART_OF_THE_SEA,
                Material.PRISMARINE_CRYSTALS,
                "Tide Token",
                "Tide Charm",
                "Tide Relic"
        ),
        UTILITY(
                "charm_utility",
                AetherSkill.Category.UTILITY,
                Material.AMETHYST_SHARD,
                Material.ENDER_EYE,
                Material.SUNFLOWER,
                "Wayfinder Token",
                "Wayfinder Charm",
                "Wayfinder Relic"
        );

        private final String baseId;
        private final AetherSkill.Category category;
        private final Material t1;
        private final Material t2;
        private final Material t3;
        private final String n1;
        private final String n2;
        private final String n3;

        Charm(
                String baseId,
                AetherSkill.Category category,
                Material t1,
                Material t2,
                Material t3,
                String n1,
                String n2,
                String n3
        ) {
            this.baseId = baseId;
            this.category = category;
            this.t1 = t1;
            this.t2 = t2;
            this.t3 = t3;
            this.n1 = n1;
            this.n2 = n2;
            this.n3 = n3;
        }

        public String baseId() {
            return baseId;
        }

        public AetherSkill.Category category() {
            return category;
        }

        public String itemId(int tier) {
            return tier <= 1 ? baseId : baseId + "_" + tier;
        }

        public Material material(int tier) {
            return switch (tier) {
                case 3 -> t3;
                case 2 -> t2;
                default -> t1;
            };
        }

        public String display(int tier) {
            return switch (tier) {
                case 3 -> n3;
                case 2 -> n2;
                default -> n1;
            };
        }
    }

    private final ItemManager items;

    public AccessoryItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (SHINY_ID.equals(key)) {
            return shinyCharm();
        }
        if (FORGE_ID.equals(key)) {
            return forgeCharm();
        }
        if (ESTATE_ID.equals(key)) {
            return estateCharm();
        }
        for (Charm charm : Charm.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                if (charm.itemId(tier).equals(key)) {
                    return charm(charm, tier);
                }
            }
        }
        return null;
    }

    public static boolean migrate(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null || meta == null) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("charm_")) {
            return false;
        }
        Integer revision = meta.getPersistentDataContainer().get(ItemKeys.statRev(), PersistentDataType.INTEGER);
        if (revision != null && revision >= STAT_REV) {
            return false;
        }
        for (Charm charm : Charm.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                if (charm.itemId(tier).equals(lower)) {
                    ItemStats fresh = statsFor(charm, tier);
                    stats.setDamage(fresh.getDamage());
                    stats.setDefense(fresh.getDefense());
                    stats.setHealth(fresh.getHealth());
                    stats.setMiningPower(fresh.getMiningPower());
                    stats.setFortune(fresh.getFortune());
                    stats.setSpread(fresh.getSpread());
                    stats.setCritChance(fresh.getCritChance());
                    stats.setCritDamage(fresh.getCritDamage());
                    stats.setSpeed(fresh.getSpeed());
                    stats.setHarvestSpread(fresh.getHarvestSpread());
                    stats.setFishingSpeed(fresh.getFishingSpeed());
                    stats.setFishingCatch(fresh.getFishingCatch());
                    stats.setCatchRate(fresh.getCatchRate());
                    meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
                    return true;
                }
            }
        }
        return false;
    }

    public ItemStack charm(Charm charm, int tier) {
        int clamped = Math.max(1, Math.min(3, tier));
        ItemStats stats = statsFor(charm, clamped);
        ItemStack item = new ItemStack(charm.material(clamped));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String id = charm.itemId(clamped);
        Rarity rarity = rarity(clamped);
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        items.applyItemData(meta, rarity, stats);
        meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.setDisplayName(rarity.getChatColor() + charm.display(clamped));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor(charm, clamped));
        lore.add("");
        lore.add("§eOff-hand accessory");
        lore.add(charm.category().title() + " §8· §7hold in the off hand");
        lore.add("");
        lore.add("§8Aetherion Charm");
        ItemProfile profile = ItemProfile.fromItemId(id);
        ItemLore.ensureStatLines(lore, stats, profile);
        ItemLore.appendBoosterSections(lore, stats, profile);
        meta.setLore(lore);
        meta.setCustomModelData(3100 + charm.ordinal() * 3 + clamped);
        meta.setMaxStackSize(1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        try {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            ItemKeys.key("hidden_attribute"),
                            0,
                            AttributeModifier.Operation.ADD_NUMBER
                    )
            );
        } catch (IllegalArgumentException ignored) {
        }
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        if (charm == Charm.FISHING) {
            FishingRodProgress.init(item);
        }
        return item;
    }

    public ItemStack shinyCharm() {
        ItemStats stats = new ItemStats();
        stats.setCatchRate(5);
        return special(
                SHINY_ID,
                Material.GOLD_NUGGET,
                Rarity.RARE,
                "Shiny Charm",
                List.of(
                        "§7A cheap homage to a very expensive hobby.",
                        "§7Nearby wild pets feel luckier. Allegedly."
                ),
                "§6Shiny Charm §8· §7hold in the off hand",
                "§7Wild pets near you spawn shiny far more often.",
                stats,
                3200
        );
    }

    public ItemStack forgeCharm() {
        return special(
                FORGE_ID,
                Material.BLAST_FURNACE,
                Rarity.RARE,
                "Pocket Forge",
                List.of(
                        "§7A quarry mill that pays rent in your pocket.",
                        "§7Inventory clutter, meet the shredder."
                ),
                "§6Pocket Forge §8· §7hold in the off hand",
                "§7Auto-compresses and compacts resources while held.",
                new ItemStats(),
                3201
        );
    }

    public ItemStack estateCharm() {
        return special(
                ESTATE_ID,
                Material.CLOCK,
                Rarity.RARE,
                "Estate Liquidator",
                List.of(
                        "§7Livestock. Bones. They call it loot.",
                        "§7I call it an eyesore. I'll take it off their hands."
                ),
                "§6Estate Liquidator §8· §7hold in the off hand",
                "§7Auto-sells vanilla mob and animal drops for coins.",
                new ItemStats(),
                3202
        );
    }

    public static boolean isCharm(String itemId) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("charm_");
    }

    public static boolean holdingOffhand(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin == null || plugin.getItemManager() == null) {
            return false;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return itemId.equalsIgnoreCase(plugin.getItemManager().getItemId(offhand));
    }

    public static double shinyChanceAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return BASE_SHINY_CHANCE;
        }
        try {
            double radiusSq = SHINY_RADIUS * SHINY_RADIUS;
            for (Player player : location.getWorld().getPlayers()) {
                if (!player.isOnline()) {
                    continue;
                }
                if (player.getLocation().distanceSquared(location) > radiusSq) {
                    continue;
                }
                if (holdingOffhand(player, SHINY_ID)) {
                    return CHARM_SHINY_CHANCE;
                }
            }
        } catch (Exception ignored) {
        }
        return BASE_SHINY_CHANCE;
    }

    private ItemStack special(
            String id,
            Material material,
            Rarity rarity,
            String name,
            List<String> flavor,
            String handLine,
            String effectLine,
            ItemStats stats,
            int model
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        items.applyItemData(meta, rarity, stats);
        meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.setDisplayName(rarity.getChatColor() + name);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor);
        lore.add("");
        lore.add("§eOff-hand accessory");
        lore.add(handLine);
        lore.add(effectLine);
        lore.add("");
        lore.add("§8Aetherion Charm");
        ItemProfile profile = ItemProfile.fromItemId(id);
        ItemLore.ensureStatLines(lore, stats, profile);
        ItemLore.appendBoosterSections(lore, stats, profile);
        meta.setLore(lore);
        meta.setCustomModelData(model);
        meta.setMaxStackSize(1);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        try {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            ItemKeys.key("hidden_attribute"),
                            0,
                            AttributeModifier.Operation.ADD_NUMBER
                    )
            );
        } catch (IllegalArgumentException ignored) {
        }
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStats statsFor(Charm charm, int tier) {
        ItemStats stats = new ItemStats();
        switch (charm) {
            case COMBAT -> {
                stats.setDamage(BalanceTargets.at(BalanceTargets.CHARM_COMBAT, tier, 0));
                stats.setCritChance(BalanceTargets.at(BalanceTargets.CHARM_COMBAT, tier, 1));
                stats.setCritDamage(BalanceTargets.at(BalanceTargets.CHARM_COMBAT, tier, 2));
            }
            case MINING -> {
                stats.setMiningPower(BalanceTargets.at(BalanceTargets.CHARM_MINING, tier, 0));
                stats.setFortune(BalanceTargets.at(BalanceTargets.CHARM_MINING, tier, 1));
                stats.setSpread(BalanceTargets.at(BalanceTargets.CHARM_MINING, tier, 2));
            }
            case FORAGING -> {
                stats.setFortune(BalanceTargets.at(BalanceTargets.CHARM_FORAGING, tier, 0));
                stats.setSpeed(BalanceTargets.at(BalanceTargets.CHARM_FORAGING, tier, 1));
                stats.setSpread(BalanceTargets.at(BalanceTargets.CHARM_FORAGING, tier, 2));
            }
            case FARMING -> {
                stats.setFortune(BalanceTargets.at(BalanceTargets.CHARM_FARMING, tier, 0));
                stats.setHarvestSpread(BalanceTargets.at(BalanceTargets.CHARM_FARMING, tier, 1));
            }
            case FISHING -> {
                stats.setFortune(BalanceTargets.at(BalanceTargets.CHARM_FISHING, tier, 0));
                stats.setFishingSpeed(BalanceTargets.at(BalanceTargets.CHARM_FISHING, tier, 1));
                stats.setFishingCatch(BalanceTargets.at(BalanceTargets.CHARM_FISHING, tier, 2));
            }
            case UTILITY -> {
                stats.setSpeed(BalanceTargets.at(BalanceTargets.CHARM_UTILITY, tier, 0));
                stats.setCatchRate(BalanceTargets.at(BalanceTargets.CHARM_UTILITY, tier, 1));
                stats.setHealth(BalanceTargets.at(BalanceTargets.CHARM_UTILITY, tier, 2));
            }
        }
        return stats;
    }

    private static List<String> flavor(Charm charm, int tier) {
        return switch (charm) {
            case COMBAT -> switch (tier) {
                case 3 -> List.of("§7A grudge with a setting.", "§7Off-hand diplomacy, but armed.");
                case 2 -> List.of("§7The second marble. The first one sued.", "§7Crits, now with a pocket.");
                default -> List.of("§7A warm rock that holds a complaint.", "§7Off-hand. The sword can share.");
            };
            case MINING -> switch (tier) {
                case 3 -> List.of("§7It points at profit. Rude, accurate.", "§7The quarry sent a thank-you note.");
                case 2 -> List.of("§7Time, direction, and extra cobble.", "§7A spreadsheet that fits in a fist.");
                default -> List.of("§7North is a suggestion. Ore is not.", "§7Hold it like a rumor about stone.");
            };
            case FORAGING -> switch (tier) {
                case 3 -> List.of("§7The forest filed it under 'problem'.", "§7Leaves clock in faster now.");
                case 2 -> List.of("§7A snack with opinions about trees.", "§7Oak has started taking it personally.");
                default -> List.of("§7A sapling that pays rent in fortune.", "§7Off-hand greenery. Professionally.");
            };
            case FARMING -> switch (tier) {
                case 3 -> List.of("§7The field nominated it for treasurer.", "§7Harvest, now with a pocket veto.");
                case 2 -> List.of("§7A bowl that itemizes wheat.", "§7Rows, columns, dirt.");
                default -> List.of("§7Seeds that gossip in the off hand.", "§7The hoe can keep the heavy lifting.");
            };
            case FISHING -> switch (tier) {
                case 3 -> List.of("§7The tide lost the appeal.", "§7Bites arrive pre-scheduled.");
                case 2 -> List.of("§7A sea with a heart, and a clipboard.", "§7Wait times filed for a reduction.");
                default -> List.of("§7A shell that remembers every nibble.", "§7Off-hand. The rod does the talking.");
            };
            case UTILITY -> switch (tier) {
                case 3 -> List.of("§7It finds the shortcut and the pet.", "§7The sun is now a scheduling conflict.");
                case 2 -> List.of("§7An eye for exits. And eggs.", "§7Utility, but make it jewelry.");
                default -> List.of("§7A shard that keeps the extras handy.", "§7Speed, catch rate, and a pulse.");
            };
        };
    }

    private static Rarity rarity(int tier) {
        return switch (tier) {
            case 3 -> Rarity.EPIC;
            case 2 -> Rarity.RARE;
            default -> Rarity.UNCOMMON;
        };
    }

    private static double stat(int tier, double a, double b, double c) {
        return switch (tier) {
            case 3 -> c;
            case 2 -> b;
            default -> a;
        };
    }
}
