package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Oven House — Bram bakes Eldervale crops into food that makes you a better farmer for a while.
 * One food buff at a time; eating another replaces it. Everything it grants is farming-only
 * (crop Fortune, Harvest, Rhythm, Prize chance), so a loaf never leaks into mining.
 */
public final class Bakehouse implements Listener {

    public enum Food {
        FARMHANDS_LOAF("Farmhand's Loaf", Material.BREAD, 10,
                "A loaf the size of a toolbox. Crumbs everywhere.",
                recipe(IsleCrop.WHEAT, 64), 15, 0, 0, 1.0, 1.0, 0),
        CARROT_CRUMBLE("Carrot Crumble", Material.COOKIE, 10,
                "Orange, buttery, faintly smug.",
                recipe(IsleCrop.CARROT, 64, IsleCrop.WHEAT, 16), 0, 20, 0, 1.0, 1.0, 0),
        HEARTH_HASH("Hearth Hash", Material.BAKED_POTATO, 10,
                "Potatoes and beets fried in someone's good pan.",
                recipe(IsleCrop.POTATO, 64, IsleCrop.BEETROOT, 32), 10, 10, 0, 1.0, 1.0, 1),
        SWEETCANE_CORDIAL("Sweetcane Cordial", Material.HONEY_BOTTLE, 10,
                "Tastes like a metronome. Keeps you in time.",
                recipe(IsleCrop.SUGAR_CANE, 64, IsleCrop.BEETROOT, 32), 5, 0, 1.0, 1.6, 1.0, 0),
        GOLDEN_HARVEST_PIE("Golden Harvest Pie", Material.PUMPKIN_PIE, 15,
                "Baked around a Prize Crop. The crust has a ribbon.",
                recipe(IsleCrop.WHEAT, 64, IsleCrop.CARROT, 64), 30, 25, 0.5, 1.25, 1.5, 0);

        final String display;
        final Material material;
        final int minutes;
        final String flavor;
        final Map<IsleCrop, Integer> costs;
        final double fortune;
        final double harvest;
        final double rhythmGain;
        final double rhythmHold;
        final double prizeMult;
        final int xpPerHarvest;

        Food(String display, Material material, int minutes, String flavor, Map<IsleCrop, Integer> costs,
             double fortune, double harvest, double rhythmGain, double rhythmHold, double prizeMult, int xpPerHarvest) {
            this.display = display;
            this.material = material;
            this.minutes = minutes;
            this.flavor = flavor;
            this.costs = costs;
            this.fortune = fortune;
            this.harvest = harvest;
            this.rhythmGain = rhythmGain;
            this.rhythmHold = rhythmHold;
            this.prizeMult = prizeMult;
            this.xpPerHarvest = xpPerHarvest;
        }

        public String display() {
            return display;
        }

        public Material material() {
            return material;
        }

        public Map<IsleCrop, Integer> costs() {
            return costs;
        }

        public boolean needsPrize() {
            return this == GOLDEN_HARVEST_PIE;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public List<String> effectLines() {
            List<String> lines = new ArrayList<>();
            if (fortune > 0) {
                lines.add("§a+" + (int) fortune + " crop Fortune");
            }
            if (harvest > 0) {
                lines.add("§a+" + (int) harvest + " Harvest");
            }
            if (rhythmGain > 0) {
                lines.add("§d+" + trim(rhythmGain) + " Rhythm per harvest");
            }
            if (rhythmHold > 1.0) {
                lines.add("§dRhythm holds " + Math.round((rhythmHold - 1.0) * 100) + "% longer");
            }
            if (prizeMult > 1.0) {
                lines.add("§6Prize Crops ×" + trim(prizeMult));
            }
            if (xpPerHarvest > 0) {
                lines.add("§b+" + xpPerHarvest + " Farming XP per harvest");
            }
            return lines;
        }

        public static Food byId(String id) {
            if (id == null) {
                return null;
            }
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static String trim(double value) {
            return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
        }
    }

    private final FarmIsle isle;
    private final NamespacedKey foodKey;

    Bakehouse(FarmIsle isle) {
        this.isle = isle;
        this.foodKey = new NamespacedKey(isle.plugin(), "farm_food");
    }

    // ------------------------------------------------------------------ buff reads

    public Food active(Player player) {
        if (player == null) {
            return null;
        }
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.food == null || profile.foodUntil <= System.currentTimeMillis()) {
            return null;
        }
        return Food.byId(profile.food);
    }

    public long secondsLeft(Player player) {
        Food food = active(player);
        return food == null ? 0L : Math.max(0L, (isle.profiles().of(player).foodUntil - System.currentTimeMillis()) / 1000L);
    }

    double cropFortune(Player player) {
        Food food = active(player);
        return food == null ? 0.0d : food.fortune;
    }

    double harvestSpread(Player player) {
        Food food = active(player);
        return food == null ? 0.0d : food.harvest;
    }

    double rhythmGain(Player player) {
        Food food = active(player);
        return food == null ? 0.0d : food.rhythmGain;
    }

    double rhythmHold(Player player) {
        Food food = active(player);
        return food == null ? 1.0d : food.rhythmHold;
    }

    double prizeMultiplier(Player player) {
        Food food = active(player);
        return food == null ? 1.0d : food.prizeMult;
    }

    int xpPerHarvest(Player player) {
        Food food = active(player);
        return food == null ? 0 : food.xpPerHarvest;
    }

    // ------------------------------------------------------------------ baking

    /** Missing-ingredient lines for the GUI (empty = can bake). */
    public List<String> missing(Player player, Food food) {
        List<String> out = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        food.costs.forEach((crop, amount) -> {
            int have = OrderItems.countPlain(inventory, crop.yield());
            if (have < amount) {
                out.add(crop.display() + " " + have + "/" + amount);
            }
        });
        if (food.needsPrize() && OrderItems.firstPrize(inventory, isle.prizes(), null) < 0) {
            out.add("any Prize Crop");
        }
        return out;
    }

    public boolean bake(Player player, Food food) {
        if (!missing(player, food).isEmpty()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        food.costs.forEach((crop, amount) -> OrderItems.takePlain(inventory, crop.yield(), amount));
        if (food.needsPrize()) {
            int slot = OrderItems.firstPrize(inventory, isle.prizes(), null);
            ItemStack prize = inventory.getItem(slot);
            if (prize != null) {
                prize.setAmount(prize.getAmount() - 1);
                inventory.setItem(slot, prize.getAmount() <= 0 ? null : prize);
            }
        }
        de.aetherion.items.util.InventoryDrops.give(player, item(food));
        player.playSound(player.getLocation(), Sound.BLOCK_SMOKER_SMOKE, SoundCategory.PLAYERS, 0.9f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_BUTCHER, SoundCategory.PLAYERS, 0.8f, 1.1f);
        player.sendMessage("§6Bram §8» §fOne " + food.display + ", fresh out. §7Eat it before you start — it lasts "
                + food.minutes + " minutes.");
        return true;
    }

    public ItemStack item(Food food) {
        ItemStack item = new ItemStack(food.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName("§6" + food.display);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + food.flavor);
        lore.add("");
        for (String line : food.effectLines()) {
            lore.add("§8• " + line);
        }
        lore.add("§7Lasts §f" + food.minutes + " min§7. One food buff at a time.");
        lore.add("");
        lore.add("§eEat to start the buff.");
        lore.add("§8Oven House · Eldervale");
        meta.setLore(lore);
        try {
            var component = meta.getFood();
            component.setCanAlwaysEat(true);
            meta.setFood(component);
        } catch (Throwable ignored) {
            // Older API without food components — still edible when hungry.
        }
        if (food == Food.GOLDEN_HARVEST_PIE) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(foodKey, PersistentDataType.STRING, food.id());
        item.setItemMeta(meta);
        return item;
    }

    public Food foodOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Food.byId(item.getItemMeta().getPersistentDataContainer().get(foodKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Food food = foodOf(event.getItem());
        if (food == null) {
            return;
        }
        Player player = event.getPlayer();
        Food before = active(player);
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.food = food.id();
        profile.foodUntil = System.currentTimeMillis() + food.minutes * 60_000L;
        isle.profiles().markDirty();
        String replaced = before != null && before != food ? " §8(replaces " + before.display + ")" : "";
        player.sendMessage("§6" + food.display + " §7— " + String.join("§7, ", food.effectLines()) + " §7for §f"
                + food.minutes + " min§7." + replaced);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.6f, 1.2f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.8, 0), 10, 0.3, 0.2, 0.3, 0.0);
        FarmingSkills.bonus(player, 5);
    }

    private static Map<IsleCrop, Integer> recipe(Object... pairs) {
        Map<IsleCrop, Integer> map = new EnumMap<>(IsleCrop.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((IsleCrop) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
