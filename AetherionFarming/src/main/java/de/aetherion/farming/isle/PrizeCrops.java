package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;
import de.aetherion.items.util.InventoryDrops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prize Crops — the rare find of Farming. Now and then a harvest on Eldervale throws out a
 * giant, glowing crop. The finder gets a few seconds of first dibs, then it is anyone's —
 * grab it before it wilts. Prizes carry a weight; heavier ones sell for more at the Market
 * Barn, fill prize orders, and bake the Golden Harvest Pie. The heaviest of each crop is an
 * isle record everyone hears about.
 */
public final class PrizeCrops implements Listener {

    private static final int GROW_TICKS = 10;
    private static final int FINDER_TICKS = 20 * 4;
    private static final int LIFE_TICKS = 20 * 8;
    private static final long BASE_VALUE = 320L;

    private final FarmIsle isle;
    private final NamespacedKey prizeKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey partKey;
    private final Map<UUID, Pop> popsByPart = new ConcurrentHashMap<>();
    private final Map<UUID, Pop> popsByFinder = new ConcurrentHashMap<>();

    private final class Pop {
        final UUID finder;
        final IsleCrop crop;
        final double kg;
        final Location at;
        final List<Entity> parts = new ArrayList<>();
        final int bornTick;
        BukkitTask task;
        boolean done;

        Pop(UUID finder, IsleCrop crop, double kg, Location at) {
            this.finder = finder;
            this.crop = crop;
            this.kg = kg;
            this.at = at;
            this.bornTick = Bukkit.getCurrentTick();
        }
    }

    PrizeCrops(FarmIsle isle) {
        this.isle = isle;
        this.prizeKey = new NamespacedKey(isle.plugin(), "farm_prize");
        this.weightKey = new NamespacedKey(isle.plugin(), "farm_prize_kg");
        this.partKey = new NamespacedKey(isle.plugin(), "farm_prize_part");
    }

    void shutdown() {
        for (Pop pop : List.copyOf(popsByFinder.values())) {
            remove(pop);
        }
    }

    // ------------------------------------------------------------------ rolls

    /** Chance per mature harvest on the isle, before multipliers. */
    private double baseChance() {
        return Math.max(0.0d, isle.plugin().getConfig().getDouble("prize-crops.chance", 1.0d / 450.0d));
    }

    public double chance(Player player, IsleCrop crop, boolean spread) {
        double chance = baseChance() * (spread ? 0.5d : 1.0d);
        chance *= 1.0d + 0.5d * FarmingSkills.scale(player, FarmingSkills.BLUE_RIBBON);
        chance *= isle.events().prizeMultiplier();
        chance *= isle.bakehouse().prizeMultiplier(player);
        chance *= 1.0d + 0.06d * isle.mastery().tier(player, crop);
        return Math.min(0.25d, chance);
    }

    void roll(Player player, IsleCrop crop, Location block, boolean spread) {
        if (crop == null || popsByFinder.containsKey(player.getUniqueId())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance(player, crop, spread)) {
            return;
        }
        spawn(player, crop, rollKg(player, crop), block);
    }

    private double rollKg(Player player, IsleCrop crop) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double roll = rng.nextDouble();
        // Blue Ribbon rolls twice and keeps the heavier; a skewed curve keeps records rare.
        if (FarmingSkills.has(player, FarmingSkills.BLUE_RIBBON)) {
            roll = Math.max(roll, rng.nextDouble());
        }
        double shaped = Math.pow(roll, 1.8d);
        return crop.minKg() + (crop.maxKg() - crop.minKg()) * shaped;
    }

    // ------------------------------------------------------------------ the pop

    /** Pops a prize out of {@code block} for {@code finder}. Public for the DEV menu. */
    public void spawn(Player finder, IsleCrop crop, double kg, Location block) {
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Location at = block.getBlock().getLocation().add(0.5, 0.6, 0.5);
        Pop pop = new Pop(finder.getUniqueId(), crop, kg, at);

        ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(crop.yield()));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(Color.fromRGB(255, 200, 40));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(scaled(0.2f, 0.0f));
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        TextDisplay label = world.spawn(at.clone().add(0, 1.9, 0), TextDisplay.class, spawned -> {
            spawned.text(Component.text(crop.prizeName().toUpperCase() + "!", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(IsleText.kg(kg) + " · click to grab", NamedTextColor.YELLOW)));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(90, 40, 24, 0));
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        Interaction hitbox = world.spawn(at.clone().add(0, -0.1, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.4f);
            spawned.setInteractionHeight(1.9f);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        pop.parts.add(display);
        pop.parts.add(label);
        pop.parts.add(hitbox);
        popsByFinder.put(pop.finder, pop);
        for (Entity part : pop.parts) {
            popsByPart.put(part.getUniqueId(), pop);
        }

        // Grow: interpolate from a sprout to a showpiece.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!display.isValid()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(GROW_TICKS);
            display.setTransformation(scaled(2.3f, 0.35f));
        }, 2L);

        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.8f);
        world.playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.4, 0.6, 0.4, 0.35);
        world.spawnParticle(Particle.BLOCK, at, 30, 0.4, 0.2, 0.4, 0.1, Material.ROOTED_DIRT.createBlockData());
        finder.sendMessage("§6✦ Prize find! §fA " + crop.prizeName() + " §7(" + IsleText.kg(kg)
                + ") burst out of the soil. §eClick it before it wilts.");
        IsleText.bar(finder, "§6§l✦ " + crop.prizeName() + " §e" + IsleText.kg(kg) + " §7— grab it!");

        pop.task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), () -> tickPop(pop), 1L, 2L);
    }

    private void tickPop(Pop pop) {
        if (pop.done) {
            return;
        }
        int age = Bukkit.getCurrentTick() - pop.bornTick;
        Entity display = pop.parts.isEmpty() ? null : pop.parts.get(0);
        if (display instanceof ItemDisplay item && item.isValid() && age > GROW_TICKS + 2) {
            // Slow spin + bob once grown.
            float spin = (age * 0.08f) % ((float) Math.PI * 2.0f);
            float bob = 0.35f + (float) Math.sin(age * 0.2d) * 0.08f;
            item.setInterpolationDelay(0);
            item.setInterpolationDuration(2);
            item.setTransformation(new Transformation(
                    new Vector3f(0.0f, bob, 0.0f),
                    new AxisAngle4f(spin, 0.0f, 1.0f, 0.0f),
                    new Vector3f(2.3f, 2.3f, 2.3f),
                    new AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
            ));
        }
        if (age % 10 == 0 && pop.at.getWorld() != null) {
            pop.at.getWorld().spawnParticle(Particle.WAX_ON, pop.at.clone().add(0, 0.8, 0), 3, 0.5, 0.6, 0.5, 0.0);
        }
        if (age == FINDER_TICKS) {
            Player finder = Bukkit.getPlayer(pop.finder);
            if (finder != null) {
                IsleText.bar(finder, "§e" + pop.crop.prizeName() + " §7is up for grabs — anyone can take it now!");
            }
        }
        if (age >= LIFE_TICKS) {
            wilt(pop);
        }
    }

    private void wilt(Pop pop) {
        if (pop.done) {
            return;
        }
        World world = pop.at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SMOKE, pop.at.clone().add(0, 0.8, 0), 20, 0.4, 0.5, 0.4, 0.02);
            world.playSound(pop.at, Sound.BLOCK_COMPOSTER_FILL, SoundCategory.PLAYERS, 0.9f, 0.6f);
        }
        Player finder = Bukkit.getPlayer(pop.finder);
        if (finder != null) {
            IsleText.bar(finder, "§7The " + pop.crop.prizeName() + " wilted. §8Faster next time.");
        }
        remove(pop);
    }

    private void remove(Pop pop) {
        pop.done = true;
        if (pop.task != null) {
            pop.task.cancel();
        }
        for (Entity part : pop.parts) {
            popsByPart.remove(part.getUniqueId());
            if (part.isValid()) {
                part.remove();
            }
        }
        popsByFinder.remove(pop.finder, pop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Pop pop = popsByPart.get(event.getRightClicked().getUniqueId());
        if (pop == null) {
            return;
        }
        event.setCancelled(true);
        claim(event.getPlayer(), pop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (popsByPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPunch(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Pop pop = popsByPart.get(event.getEntity().getUniqueId());
        if (pop == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            claim(player, pop);
        }
    }

    private void claim(Player player, Pop pop) {
        if (pop.done || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int age = Bukkit.getCurrentTick() - pop.bornTick;
        boolean finder = player.getUniqueId().equals(pop.finder);
        if (!finder && age < FINDER_TICKS) {
            IsleText.bar(player, "§7Finder's dibs for a moment — §e" + ((FINDER_TICKS - age) / 20 + 1) + "s§7.");
            return;
        }
        remove(pop);
        ItemStack prize = item(pop.crop, pop.kg);
        InventoryDrops.give(player, prize);
        FarmingSkills.bonus(player, 40);
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.prizes++;
        if (pop.kg > profile.bestKg) {
            profile.bestKg = pop.kg;
            profile.bestCrop = pop.crop;
        }
        isle.profiles().markDirty();

        World world = pop.at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FIREWORK, pop.at.clone().add(0, 1.0, 0), 30, 0.4, 0.5, 0.4, 0.08);
            world.playSound(pop.at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        String steal = finder ? "" : " §8(snatched!)";
        player.sendMessage("§6✦ " + pop.crop.prizeName() + " §7(" + IsleText.kg(pop.kg) + ")" + steal
                + " §8· §7worth ~§6" + IsleText.coins(value(pop.crop, pop.kg)) + " coins §7at the Market Barn.");
        if (!finder) {
            Player original = Bukkit.getPlayer(pop.finder);
            if (original != null) {
                original.sendMessage("§7" + player.getName() + " grabbed your " + pop.crop.prizeName() + ". §8Farming is a contact sport.");
            }
        }
        if (isle.profiles().offerRecord(pop.crop, player, pop.kg)) {
            String line = "§6✦ Isle record! §f" + player.getName() + " §7grabbed a §6" + IsleText.kg(pop.kg)
                    + " " + pop.crop.prizeName() + "§7 — the heaviest on Eldervale.";
            for (Player visitor : IsleWorld.visitors(isle.plugin())) {
                visitor.sendMessage(line);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 0.9f);
        }
    }

    // ------------------------------------------------------------------ items

    public ItemStack item(IsleCrop crop, double kg) {
        ItemStack item = new ItemStack(crop.yield());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        double rounded = Math.round(kg * 100.0d) / 100.0d;
        meta.setDisplayName("§6✦ " + crop.prizeName());
        meta.setLore(List.of(
                "§7Blue-ribbon produce from Eldervale.",
                "§7Weight: §f" + IsleText.kg(rounded) + " §8(" + band(crop, rounded) + ")",
                "",
                "§7Sells for §6" + IsleText.coins(value(crop, rounded)) + " coins §7at the §eMarket Barn§7.",
                "§7Fills prize Harvest Orders.",
                "§7Bakes the §6Golden Harvest Pie§7.",
                "",
                "§8Prize Crop"
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(prizeKey, PersistentDataType.STRING, crop.id());
        pdc.set(weightKey, PersistentDataType.DOUBLE, rounded);
        item.setItemMeta(meta);
        return item;
    }

    public IsleCrop cropOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return IsleCrop.byId(item.getItemMeta().getPersistentDataContainer().get(prizeKey, PersistentDataType.STRING));
    }

    public double kgOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0d;
        }
        Double kg = item.getItemMeta().getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
        return kg == null ? 0.0d : kg;
    }

    public boolean isPrize(ItemStack item) {
        return cropOf(item) != null;
    }

    /** 192 – 512 coins by how close the prize sits to its crop's top weight. */
    public static long value(IsleCrop crop, double kg) {
        double span = Math.max(0.01d, crop.maxKg() - crop.minKg());
        double share = Math.max(0.0d, Math.min(1.0d, (kg - crop.minKg()) / span));
        return Math.round(BASE_VALUE * (0.6d + share));
    }

    public long valueOf(ItemStack item) {
        IsleCrop crop = cropOf(item);
        return crop == null ? 0L : value(crop, kgOf(item)) * item.getAmount();
    }

    private static String band(IsleCrop crop, double kg) {
        double share = (kg - crop.minKg()) / Math.max(0.01d, crop.maxKg() - crop.minKg());
        if (share >= 0.9d) {
            return "§6champion";
        }
        if (share >= 0.6d) {
            return "§ehefty";
        }
        if (share >= 0.3d) {
            return "§fsolid";
        }
        return "§7modest";
    }

    private static Transformation scaled(float scale, float lift) {
        return new Transformation(
                new Vector3f(0.0f, lift, 0.0f),
                new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
        );
    }
}
