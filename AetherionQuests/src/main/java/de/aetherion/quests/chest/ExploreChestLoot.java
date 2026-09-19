package de.aetherion.quests.chest;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.storage.BoosterDelivery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Weighted exploration-chest tables. Coins are paid to the wallet; items go to inventory.
 */
final class ExploreChestLoot {

    static final NamespacedKey COIN_KEY = new NamespacedKey("aetherionquests", "explore_coins");

    private ExploreChestLoot() {
    }

    static Drop roll(ExploreChestKind kind) {
        List<Entry> table = table(kind);
        int total = 0;
        for (Entry entry : table) {
            total += entry.weight;
        }
        if (total <= 0) {
            return Drop.coins(50);
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (Entry entry : table) {
            cursor += entry.weight;
            if (pick < cursor) {
                return entry.roll.get();
            }
        }
        return table.get(table.size() - 1).roll.get();
    }

    static List<ItemStack> showcase(ExploreChestKind kind) {
        List<ItemStack> shown = new ArrayList<>();
        CustomItem custom = custom();
        switch (kind) {
            case RARE -> {
                shown.add(coins(80));
                add(shown, compressed(CompressedResource.COAL));
                add(shown, compressed(CompressedResource.RAW_COPPER));
                add(shown, booster());
                if (custom != null) {
                    shown.add(custom.createCombatSword());
                }
            }
            case EPIC -> {
                shown.add(coins(300));
                add(shown, compacted(CompressedResource.COAL));
                add(shown, compacted(CompressedResource.RAW_IRON));
                add(shown, booster());
                if (custom != null) {
                    shown.add(custom.createCombatSword2());
                    shown.add(custom.createMiningPickaxe2());
                }
            }
            case LEGENDARY -> {
                shown.add(coins(1200));
                add(shown, compacted(CompressedResource.DIAMOND));
                add(shown, compacted(CompressedResource.EMERALD));
                if (custom != null) {
                    shown.add(custom.createCombatSword4());
                    shown.add(custom.createMiningPickaxe4());
                    shown.add(custom.createBlueprintUpgradeStone2());
                }
            }
            case MYTHIC -> {
                shown.add(coins(4000));
                add(shown, compacted(CompressedResource.DIAMOND));
                if (custom != null) {
                    shown.add(custom.createCombatSword5());
                    shown.add(custom.createMiningPickaxe5());
                    shown.add(custom.createCombatHelmet5());
                    shown.add(custom.createBlueprintUpgradeStone3());
                }
            }
        }
        if (shown.isEmpty()) {
            shown.add(new ItemStack(kind.block()));
        }
        return shown;
    }

    static void give(Player player, Drop drop) {
        if (player == null || drop == null) {
            return;
        }
        if (drop.coins > 0) {
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getCoins() != null) {
                items.getCoins().add(player, drop.coins);
            }
        }
        if (drop.item != null && !drop.item.getType().isAir()) {
            try {
                BoosterDelivery.giveQuestReward(player, drop.item.clone());
            } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
                player.getInventory().addItem(drop.item.clone()).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
    }

    static String nameOf(Drop drop) {
        if (drop == null) {
            return "nothing";
        }
        if (drop.coins > 0) {
            return "§6" + drop.coins + " coins";
        }
        ItemStack item = drop.display != null ? drop.display : drop.item;
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        if (item != null) {
            return item.getType().name().toLowerCase().replace('_', ' ');
        }
        return "nothing";
    }

    private static List<Entry> table(ExploreChestKind kind) {
        return switch (kind) {
            case RARE -> List.of(
                    e(28, () -> Drop.coins(range(50, 150))),
                    e(16, () -> Drop.item(compressed(CompressedResource.COAL))),
                    e(14, () -> Drop.item(compressed(CompressedResource.RAW_COPPER))),
                    e(12, () -> Drop.item(compressed(CompressedResource.WHEAT))),
                    e(12, () -> Drop.item(booster())),
                    e(8, () -> Drop.item(sword(1))),
                    e(6, () -> Drop.item(pick(1))),
                    e(4, () -> Drop.item(compressed(CompressedResource.RAW_IRON)))
            );
            case EPIC -> List.of(
                    e(22, () -> Drop.coins(range(200, 500))),
                    e(14, () -> Drop.item(compacted(CompressedResource.COAL))),
                    e(12, () -> Drop.item(compacted(CompressedResource.RAW_IRON))),
                    e(12, () -> Drop.item(booster())),
                    e(10, () -> Drop.item(sword(2))),
                    e(8, () -> Drop.item(pick(2))),
                    e(8, () -> Drop.item(compacted(CompressedResource.RAW_COPPER))),
                    e(6, () -> Drop.item(sword(3))),
                    e(5, () -> Drop.item(pick(3))),
                    e(3, () -> Drop.item(compacted(CompressedResource.DIAMOND)))
            );
            case LEGENDARY -> List.of(
                    e(20, () -> Drop.coins(range(800, 2000))),
                    e(14, () -> Drop.item(compacted(CompressedResource.DIAMOND))),
                    e(10, () -> Drop.item(compacted(CompressedResource.EMERALD))),
                    e(10, () -> Drop.item(sword(4))),
                    e(8, () -> Drop.item(pick(4))),
                    e(8, () -> Drop.item(booster())),
                    e(8, () -> Drop.item(stone(2))),
                    e(6, () -> Drop.item(frost())),
                    e(6, () -> Drop.item(sword(3))),
                    e(5, () -> Drop.item(compacted(CompressedResource.RAW_GOLD))),
                    e(5, () -> Drop.item(helmet(4)))
            );
            case MYTHIC -> List.of(
                    e(16, () -> Drop.coins(range(2500, 8000))),
                    e(10, () -> Drop.item(compacted(CompressedResource.DIAMOND))),
                    e(8, () -> Drop.item(sword(5))),
                    e(8, () -> Drop.item(pick(5))),
                    e(7, () -> Drop.item(helmet(5))),
                    e(6, () -> Drop.item(miningHelm5())),
                    e(5, () -> Drop.item(warped())),
                    e(4, () -> Drop.item(hollowBow())),
                    e(4, () -> Drop.item(squidBoot())),
                    e(4, () -> Drop.item(siphon())),
                    e(4, () -> Drop.item(stone(3))),
                    e(3, () -> Drop.item(booster())),
                    e(2, () -> Drop.item(stone(4))),
                    e(9, () -> Drop.item(compacted(CompressedResource.EMERALD)))
            );
        };
    }

    private static Entry e(int weight, Supplier<Drop> roll) {
        return new Entry(weight, roll);
    }

    private static int range(int min, int max) {
        return min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
    }

    private static CustomItem custom() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getCustomItem();
    }

    private static ItemStack booster() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.EXPERIENCE_BOTTLE) : custom.createRandomBooster();
    }

    private static ItemStack compressed(CompressedResource resource) {
        ItemStack stack = resource.compressed();
        stack.setAmount(1 + ThreadLocalRandom.current().nextInt(2));
        return stack;
    }

    private static ItemStack compacted(CompressedResource resource) {
        return resource.compacted();
    }

    private static ItemStack sword(int tier) {
        CustomItem custom = custom();
        if (custom == null) {
            return new ItemStack(Material.IRON_SWORD);
        }
        return switch (tier) {
            case 2 -> custom.createCombatSword2();
            case 3 -> custom.createCombatSword3();
            case 4 -> custom.createCombatSword4();
            case 5 -> custom.createCombatSword5();
            default -> custom.createCombatSword();
        };
    }

    private static ItemStack pick(int tier) {
        CustomItem custom = custom();
        if (custom == null) {
            return new ItemStack(Material.IRON_PICKAXE);
        }
        return switch (tier) {
            case 2 -> custom.createMiningPickaxe2();
            case 3 -> custom.createMiningPickaxe3();
            case 4 -> custom.createMiningPickaxe4();
            case 5 -> custom.createMiningPickaxe5();
            default -> custom.createMiningPickaxe();
        };
    }

    private static ItemStack helmet(int tier) {
        CustomItem custom = custom();
        if (custom == null) {
            return new ItemStack(Material.IRON_HELMET);
        }
        return tier >= 5 ? custom.createCombatHelmet5() : custom.createCombatHelmet4();
    }

    private static ItemStack miningHelm5() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.NETHERITE_HELMET) : custom.createMiningHelmet5();
    }

    private static ItemStack stone(int tier) {
        CustomItem custom = custom();
        if (custom == null) {
            return new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        }
        return switch (tier) {
            case 3 -> custom.createBlueprintUpgradeStone3();
            case 4 -> custom.createBlueprintUpgradeStone4();
            default -> custom.createBlueprintUpgradeStone2();
        };
    }

    private static ItemStack frost() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.PRISMARINE_SHARD) : custom.createFrostShard();
    }

    private static ItemStack warped() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.WARPED_FUNGUS) : custom.createWarpedBlade();
    }

    private static ItemStack hollowBow() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.BOW) : custom.createHollowLongbow();
    }

    private static ItemStack squidBoot() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.LEATHER_BOOTS) : custom.createSquidsBoot();
    }

    private static ItemStack siphon() {
        CustomItem custom = custom();
        return custom == null ? new ItemStack(Material.IRON_PICKAXE) : custom.createVeinSiphon();
    }

    private static void add(List<ItemStack> list, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            list.add(item);
        }
    }

    static ItemStack coins(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + amount + " coins");
            meta.setLore(List.of("§7Wallet payout."));
            meta.getPersistentDataContainer().set(COIN_KEY, PersistentDataType.INTEGER, amount);
            item.setItemMeta(meta);
        }
        return item;
    }

    private record Entry(int weight, Supplier<Drop> roll) {
    }

    static final class Drop {
        final ItemStack display;
        final ItemStack item;
        final int coins;

        private Drop(ItemStack display, ItemStack item, int coins) {
            this.display = display;
            this.item = item;
            this.coins = coins;
        }

        static Drop coins(int amount) {
            return new Drop(ExploreChestLoot.coins(amount), null, amount);
        }

        static Drop item(ItemStack stack) {
            ItemStack copy = stack == null ? new ItemStack(Material.CHEST) : stack;
            return new Drop(copy, copy, 0);
        }
    }
}
