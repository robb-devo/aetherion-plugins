package de.aetherion.mining.titan;

import de.aetherion.mining.isle.IsleOre;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Titan Runes: Aetherion's own enchantments, carved at the Heartforge. One rune per item. They work wherever Mining
 * runs (main world isles, the mines); the item only remembers the rune id, the line in its lore is put back if gear
 * refreshes rewrite it.
 */
public final class TitanRunes implements Listener {

    public enum Rune {
        MARROWBITE("marrowbite", "Marrowbite", "12% chance: an ore drops twice", Slot.PICKAXE),
        BONE_SENSE("bone_sense", "Bone Sense", "Breaking ore can reveal the veins around you", Slot.PICKAXE),
        EMBER_RUSH("ember_rush", "Ember Rush", "12 ores in 20 s: Haste II for 8 s", Slot.PICKAXE),
        GULLETGLIDE("gulletglide", "Gulletglide", "No fall damage up to 24 blocks, half beyond", Slot.BOOTS),
        RIBCAGE("ribcage", "Ribcage", "-15% damage taken below 40% health", Slot.CHEST);

        final String id;
        final String display;
        final String effect;
        final Slot slot;

        Rune(String id, String display, String effect, Slot slot) {
            this.id = id;
            this.display = display;
            this.effect = effect;
            this.slot = slot;
        }

        public String display() {
            return display;
        }

        public String effect() {
            return effect;
        }

        public boolean fits(ItemStack item) {
            return item != null && slot.fits(item.getType());
        }
    }

    enum Slot {
        PICKAXE, BOOTS, CHEST;

        boolean fits(Material type) {
            String name = type.name();
            return switch (this) {
                case PICKAXE -> name.endsWith("_PICKAXE");
                case BOOTS -> name.endsWith("_BOOTS");
                case CHEST -> name.endsWith("_CHESTPLATE");
            };
        }
    }

    private static final String LORE_MARK = "§6✦ Titan Rune: ";
    private static NamespacedKey key;

    private final Plugin plugin;
    private final Map<UUID, Deque<Long>> rush = new ConcurrentHashMap<>();
    private final Map<UUID, Long> senseCooldown = new ConcurrentHashMap<>();

    public TitanRunes(Plugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "titan_rune");
    }

    public static Rune runeOf(ItemStack item) {
        if (item == null || key == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (Rune rune : Rune.values()) {
            if (rune.id.equals(id)) {
                return rune;
            }
        }
        return null;
    }

    /** Carves the rune (replacing an older one) and writes its lore line. */
    public static void carve(ItemStack item, Rune rune) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, rune.id);
        item.setItemMeta(meta);
        ensureLore(item);
    }

    static void ensureLore(ItemStack item) {
        Rune rune = runeOf(item);
        if (rune == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String line = LORE_MARK + "§f" + rune.display;
        String effect = "§7  " + rune.effect;
        int at = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith(LORE_MARK)) {
                at = i;
                break;
            }
        }
        if (at >= 0) {
            if (lore.get(at).equals(line) && at + 1 < lore.size() && lore.get(at + 1).equals(effect)) {
                return;
            }
            lore.remove(at);
            if (at < lore.size() && lore.get(at).startsWith("§7  ")) {
                lore.remove(at);
            }
        }
        lore.add(line);
        lore.add(effect);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (runeOf(item) != null) {
            ensureLore(item);
        }
    }

    // ------------------------------------------------------------------ pickaxe runes

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Rune rune = runeOf(tool);
        if (rune == null || rune.slot != Slot.PICKAXE) {
            return;
        }
        Block block = event.getBlock();
        IsleOre ore = IsleOre.fromBlock(block.getType());
        if (ore == null || ore == IsleOre.STONE) {
            return;
        }
        switch (rune) {
            case MARROWBITE -> {
                if (Math.random() < 0.12 && event.isDropItems()) {
                    Location at = block.getLocation().add(0.5, 0.5, 0.5);
                    for (ItemStack drop : block.getDrops(tool, player)) {
                        block.getWorld().dropItemNaturally(at, drop);
                    }
                    block.getWorld().spawnParticle(Particle.DUST, at, 10, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 60, 40), 1.2f));
                    player.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.6f, 0.6f);
                }
            }
            case BONE_SENSE -> sense(player, block);
            case EMBER_RUSH -> {
                long now = System.currentTimeMillis();
                Deque<Long> hits = rush.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
                hits.addLast(now);
                while (!hits.isEmpty() && now - hits.peekFirst() > 20_000L) {
                    hits.removeFirst();
                }
                if (hits.size() >= 12) {
                    hits.clear();
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, 1, false, true, true));
                    player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.6f, 1.4f);
                }
            }
            default -> {
            }
        }
    }

    /** Ores within 6 blocks glow for the miner for a few seconds (only they see it). */
    private void sense(Player player, Block origin) {
        long now = System.currentTimeMillis();
        Long last = senseCooldown.get(player.getUniqueId());
        if (last != null && now - last < 15_000L || Math.random() > 0.25) {
            return;
        }
        senseCooldown.put(player.getUniqueId(), now);
        List<Location> found = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    Block block = origin.getRelative(dx, dy, dz);
                    IsleOre ore = IsleOre.fromBlock(block.getType());
                    if (ore != null && ore != IsleOre.STONE) {
                        found.add(block.getLocation().add(0.5, 0.5, 0.5));
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 0.5f);
        for (int pulse = 0; pulse < 5; pulse++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                for (Location at : found) {
                    if (at.getWorld() == player.getWorld()) {
                        player.spawnParticle(Particle.DUST, at, 4, 0.25, 0.25, 0.25, 0,
                                new Particle.DustOptions(Color.fromRGB(240, 230, 200), 1.4f));
                    }
                }
            }, pulse * 16L);
        }
    }

    // ------------------------------------------------------------------ armour runes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && runeOf(player.getInventory().getBoots()) == Rune.GULLETGLIDE) {
            if (player.getFallDistance() <= 24f) {
                event.setCancelled(true);
            } else {
                event.setDamage(event.getDamage() * 0.5);
            }
            return;
        }
        if (runeOf(player.getInventory().getChestplate()) == Rune.RIBCAGE) {
            var max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double cap = max == null ? 20.0 : max.getValue();
            if (player.getHealth() / cap < 0.4) {
                event.setDamage(event.getDamage() * 0.85);
            }
        }
    }
}
