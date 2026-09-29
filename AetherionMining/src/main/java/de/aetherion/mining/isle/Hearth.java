package de.aetherion.mining.isle;

import org.bukkit.Color;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Hearth — Nan Coalbright cooks for miners, and she takes payment in ore. One ration at a
 * time; eating another replaces it. Every buff is mining-only (ore Fortune, Spread, break speed,
 * Strike Rhythm, Crystal Find luck), so a pasty never leaks into the fields.
 */
public final class Hearth implements Listener {

    public enum Ration {
        MINERS_PASTY("Miner's Pasty", Material.BAKED_POTATO, 10,
                "Folded thick so it survives a cave-in. Allegedly.",
                recipe(IsleOre.COAL, 64, IsleOre.COPPER, 32), 15, 0, 0, 0, 1.0, 1.0),
        LAMPLIGHT_STEW("Lamplight Stew", Material.MUSHROOM_STEW, 10,
                "Smells of lamp oil and good decisions.",
                recipe(IsleOre.COAL, 64, IsleOre.REDSTONE, 32), 0, 0, 22, 0, 1.2, 1.0),
        PICKMANS_BROTH("Pickman's Broth", Material.BEETROOT_SOUP, 10,
                "Iron-rich. Literally. Nan grates it in.",
                recipe(IsleOre.IRON, 64, IsleOre.LAPIS, 32), 5, 8, 0, 0, 1.0, 1.0),
        TIN_CUP_COFFEE("Tin-Cup Coffee", Material.POTION, 10,
                "Brewed on a shovel. Keeps the whole crew in time.",
                recipe(IsleOre.COPPER, 64, IsleOre.GOLD, 16), 5, 0, 6, 1.0, 1.6, 1.0),
        GEODE_BROTH("Geode Broth", Material.RABBIT_STEW, 15,
                "Simmered around a specimen. Nan won't say which.",
                recipe(IsleOre.GOLD, 32, IsleOre.DIAMOND, 8), 25, 10, 12, 0.5, 1.25, 1.5);

        final String display;
        final Material material;
        final int minutes;
        final String flavor;
        final Map<IsleOre, Integer> costs;
        final double fortune;
        final double spread;
        final double speedPower;
        final double rhythmGain;
        final double rhythmHold;
        final double crystalMult;

        Ration(String display, Material material, int minutes, String flavor, Map<IsleOre, Integer> costs,
               double fortune, double spread, double speedPower, double rhythmGain, double rhythmHold, double crystalMult) {
            this.display = display;
            this.material = material;
            this.minutes = minutes;
            this.flavor = flavor;
            this.costs = costs;
            this.fortune = fortune;
            this.spread = spread;
            this.speedPower = speedPower;
            this.rhythmGain = rhythmGain;
            this.rhythmHold = rhythmHold;
            this.crystalMult = crystalMult;
        }

        public String display() {
            return display;
        }

        public Material material() {
            return material;
        }

        public int minutes() {
            return minutes;
        }

        public Map<IsleOre, Integer> costs() {
            return costs;
        }

        public boolean needsSpecimen() {
            return this == GEODE_BROTH;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public List<String> effectLines() {
            List<String> lines = new ArrayList<>();
            if (fortune > 0) {
                lines.add("§a+" + (int) fortune + " ore Fortune");
            }
            if (spread > 0) {
                lines.add("§a+" + (int) spread + " Spread");
            }
            if (speedPower > 0) {
                lines.add("§e+" + (int) speedPower + " break-speed power §8(not gates)");
            }
            if (rhythmGain > 0) {
                lines.add("§6+" + trim(rhythmGain) + " Rhythm per strike");
            }
            if (rhythmHold > 1.0) {
                lines.add("§6Rhythm holds " + Math.round((rhythmHold - 1.0) * 100) + "% longer");
            }
            if (crystalMult > 1.0) {
                lines.add("§dCrystal Finds ×" + trim(crystalMult));
            }
            return lines;
        }

        public static Ration byId(String id) {
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

    private final MineIsle isle;
    private final NamespacedKey rationKey;

    Hearth(MineIsle isle) {
        this.isle = isle;
        this.rationKey = new NamespacedKey(isle.plugin(), "mine_ration");
    }

    // ------------------------------------------------------------------ buff reads

    public Ration active(Player player) {
        if (player == null) {
            return null;
        }
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (profile.ration == null || profile.rationUntil <= System.currentTimeMillis()) {
            return null;
        }
        return Ration.byId(profile.ration);
    }

    public long secondsLeft(Player player) {
        Ration ration = active(player);
        return ration == null ? 0L : Math.max(0L, (isle.profiles().of(player).rationUntil - System.currentTimeMillis()) / 1000L);
    }

    double oreFortune(Player player) {
        Ration ration = active(player);
        return ration == null ? 0.0d : ration.fortune;
    }

    double spread(Player player) {
        Ration ration = active(player);
        return ration == null ? 0.0d : ration.spread;
    }

    double speedPower(Player player) {
        Ration ration = active(player);
        return ration == null ? 0.0d : ration.speedPower;
    }

    double rhythmGain(Player player) {
        Ration ration = active(player);
        return ration == null ? 0.0d : ration.rhythmGain;
    }

    double rhythmHold(Player player) {
        Ration ration = active(player);
        return ration == null ? 1.0d : ration.rhythmHold;
    }

    double crystalMultiplier(Player player) {
        Ration ration = active(player);
        return ration == null ? 1.0d : ration.crystalMult;
    }

    // ------------------------------------------------------------------ cooking

    /** Missing-ingredient lines for the GUI (empty = can cook). */
    public List<String> missing(Player player, Ration ration) {
        List<String> out = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        ration.costs.forEach((ore, amount) -> {
            int have = MineItems.countPlain(inventory, ore.resource());
            if (have < amount) {
                out.add(ore.display() + " " + have + "/" + amount);
            }
        });
        if (ration.needsSpecimen() && MineItems.firstSpecimen(inventory, isle.crystals(), null) < 0) {
            out.add("any Crystal Find specimen");
        }
        return out;
    }

    public boolean cook(Player player, Ration ration) {
        if (!missing(player, ration).isEmpty()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ration.costs.forEach((ore, amount) -> MineItems.takePlain(inventory, ore.resource(), amount));
        if (ration.needsSpecimen()) {
            MineItems.takeSpecimen(inventory, isle.crystals(), null);
        }
        MineSkills.give(player, item(ration));
        player.playSound(player.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        isle.cast().say(player, MineRole.COOK, "One " + ration.display + ". Eat it before you go down — it lasts "
                + ration.minutes + " minutes.");
        return true;
    }

    public ItemStack item(Ration ration) {
        ItemStack item = new ItemStack(ration.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName("§6" + ration.display);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + ration.flavor);
        lore.add("");
        for (String line : ration.effectLines()) {
            lore.add("§8• " + line);
        }
        lore.add("§7Lasts §f" + ration.minutes + " min§7. One ration at a time.");
        lore.add("");
        lore.add("§eEat to start the buff.");
        lore.add("§8The Hearth · Mining Eldervale");
        meta.setLore(lore);
        if (meta instanceof PotionMeta potion) {
            potion.setColor(Color.fromRGB(92, 58, 30));
        } else {
            try {
                var component = meta.getFood();
                component.setCanAlwaysEat(true);
                component.setNutrition(ration.minutes >= 15 ? 8 : 6);
                component.setSaturation(ration.minutes >= 15 ? 9.6f : 7.2f);
                meta.setFood(component);
            } catch (Throwable ignored) {
                // Older API without food components — still edible when hungry.
            }
        }
        if (ration == Ration.GEODE_BROTH) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(rationKey, PersistentDataType.STRING, ration.id());
        item.setItemMeta(meta);
        return item;
    }

    public Ration rationOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Ration.byId(item.getItemMeta().getPersistentDataContainer().get(rationKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Ration ration = rationOf(event.getItem());
        if (ration == null) {
            return;
        }
        Player player = event.getPlayer();
        Ration before = active(player);
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.ration = ration.id();
        profile.rationUntil = System.currentTimeMillis() + ration.minutes * 60_000L;
        isle.profiles().markDirty();
        String replaced = before != null && before != ration ? " §8(replaces " + before.display + ")" : "";
        player.sendMessage("§6" + ration.display + " §7— " + String.join("§7, ", ration.effectLines()) + " §7for §f"
                + ration.minutes + " min§7." + replaced);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.6f, 1.1f);
        player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1.8, 0), 6, 0.2, 0.1, 0.2, 0.01);
        MineSkills.bonus(player, 5);
    }

    private static Map<IsleOre, Integer> recipe(Object... pairs) {
        Map<IsleOre, Integer> map = new EnumMap<>(IsleOre.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((IsleOre) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
