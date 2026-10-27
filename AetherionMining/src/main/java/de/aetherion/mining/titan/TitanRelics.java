package de.aetherion.mining.titan;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.mining.AetherionMining;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Relic Chests on the swallowed isles: each player empties each isle's chest once per day (per churn), into a private
 * view, so nobody races anyone for them. Loot follows the isle: its ores, coins, a compressed resource and the isle's
 * page of lore.
 */
public final class TitanRelics implements Listener {

    private final AetherionMining plugin;
    private final TitanWorld titan;
    private final File file;
    private final Set<String> opened = new HashSet<>();
    private int generation = -1;

    public TitanRelics(AetherionMining plugin, TitanWorld titan) {
        this.plugin = plugin;
        this.titan = titan;
        this.file = new File(plugin.getDataFolder(), "titan-relics.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        generation = yaml.getInt("generation", -1);
        opened.addAll(yaml.getStringList("opened"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || block.getType() != Material.CHEST || !titan.isTitan(block.getWorld()) || titan.shape() == null) {
            return;
        }
        int[] relic = null;
        for (int[] r : titan.shape().relics) {
            if (r[0] == block.getX() && r[1] == block.getY() && r[2] == block.getZ()) {
                relic = r;
                break;
            }
        }
        if (relic == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (generation != titan.generation()) {
            generation = titan.generation();
            opened.clear();
        }
        String id = player.getUniqueId() + "@" + relic[0] + "," + relic[1] + "," + relic[2];
        String name = IsleBuilder.NAMES[relic[3]];
        if (!opened.add(id)) {
            player.sendMessage("§6Relic Chest §8· §f" + name + " §7· you already emptied it today. Ossarath refills it at the Churn.");
            return;
        }
        save();
        Inventory view = Bukkit.createInventory(null, 27,
                LegacyComponentSerializer.legacySection().deserialize("§6Relic Chest §8· §f" + name));
        Random random = new Random(titan.shape().seed ^ id.hashCode());
        for (ItemStack item : loot(relic[3], random)) {
            view.addItem(item);
        }
        long coins = 250 + random.nextInt(501);
        CoinAccess wallet = AetherServices.coins();
        if (wallet != null) {
            wallet.add(player, coins);
        }
        player.openInventory(view);
        player.playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1f, 0.7f);
        player.playSound(block.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
        player.sendMessage("§6Relic Chest §8· §f" + name + (wallet != null ? " §7· §e+" + coins + " coins" : ""));
    }

    private List<ItemStack> loot(int theme, Random random) {
        List<ItemStack> out = new ArrayList<>();
        switch (theme) {
            case 0 -> {
                out.add(stack(Material.DIAMOND, 2, 5, random));
                out.add(stack(Material.LAPIS_LAZULI, 8, 16, random));
                out.add(stack(Material.PACKED_ICE, 8, 16, random));
            }
            case 1 -> {
                out.add(stack(Material.GOLD_INGOT, 4, 8, random));
                out.add(stack(Material.EMERALD, 2, 5, random));
                out.add(stack(Material.COPPER_INGOT, 8, 16, random));
                out.add(stack(Material.PRISMARINE_SHARD, 4, 10, random));
            }
            case 2 -> {
                out.add(stack(Material.IRON_INGOT, 8, 16, random));
                out.add(stack(Material.COAL_BLOCK, 2, 4, random));
                if (random.nextInt(4) == 0) {
                    out.add(new ItemStack(Material.NETHERITE_SCRAP));
                }
            }
            case 3 -> {
                out.add(stack(Material.EMERALD, 3, 6, random));
                out.add(stack(Material.LAPIS_LAZULI, 8, 16, random));
                out.add(stack(Material.GOLD_NUGGET, 9, 27, random));
            }
            case 4 -> {
                out.add(stack(Material.REDSTONE, 16, 32, random));
                out.add(stack(Material.GOLD_INGOT, 3, 6, random));
                out.add(stack(Material.COPPER_INGOT, 8, 16, random));
                out.add(new ItemStack(Material.CLOCK));
            }
            default -> {
                out.add(stack(Material.COAL, 16, 32, random));
                out.add(stack(Material.IRON_INGOT, 4, 8, random));
                out.add(stack(Material.QUARTZ, 8, 16, random));
                out.add(stack(Material.SHROOMLIGHT, 1, 3, random));
            }
        }
        ItemFactoryAccess items = AetherServices.items();
        if (items != null) {
            try {
                ItemStack compressed = items.randomCompressed(false);
                if (compressed != null) {
                    out.add(compressed);
                }
            } catch (RuntimeException ignored) {
            }
        }
        out.add(page(theme));
        return out;
    }

    private static ItemStack stack(Material material, int min, int max, Random random) {
        return new ItemStack(material, min + random.nextInt(max - min + 1));
    }

    private static ItemStack page(int theme) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(IsleBuilder.NAMES[theme]);
        meta.setAuthor("Ossarath's belly");
        meta.addPages(Component.text(IsleBuilder.NAMES[theme] + "\n\n" + IsleBuilder.LORE[theme]),
                Component.text("Ossarath, the Isle-Eater, swallowed whole islands before it fell. "
                        + "Every morning it churns, and what it ate drifts up from its belly again."));
        book.setItemMeta(meta);
        return book;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("generation", generation);
        yaml.set("opened", new ArrayList<>(opened));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Titan] Could not save relic chests: " + ex.getMessage());
        }
    }
}
