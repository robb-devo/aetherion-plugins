package de.aetherion.mining.titan;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ChromePage;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.ItemFactoryAccess;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Heartforge in Jawcamp (The Grafter): Bonegrafts open up to four more booster sockets on a piece of gear, Titan
 * Runes carve one of Aetherion's own enchantments into it. Paid in what Ossarath's body gives, plus coins.
 */
public final class Heartforge implements Listener {

    private static final int GEAR = 13;
    private static final int GRAFT = 10;
    private static final int INFO = 16;
    private static final int[] RUNES = {20, 21, 22, 23, 24};
    private static final int RUNE_BONE = 16, RUNE_MARROW = 4, RUNE_EMBERS = 2;
    private static final long RUNE_COINS = 2500;

    public void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27,
                LegacyComponentSerializer.legacySection().deserialize("§6The Heartforge"));
        paint(holder, player);
        ChromePage.open(player, holder.inventory, ChromePage.Theme.FORGE);
        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1f, 0.7f);
    }

    private static int[] graftCost(int next) {
        int scale = 1 << (next - 1);
        return new int[]{32 * scale, 8 * scale, scale, 5000 * next};
    }

    private void paint(Holder holder, Player player) {
        Inventory inv = holder.inventory;
        ItemStack gear = inv.getItem(GEAR);
        for (int i = 0; i < 27; i++) {
            if (i != GEAR) {
                inv.setItem(i, ChromePage.spacer());
            }
        }
        if (gear == null || gear.getType().isAir()) {
            inv.setItem(GEAR, null);
        }
        ItemFactoryAccess items = AetherServices.items();
        int grafts = items == null || gear == null ? 0 : items.boneGrafts(gear);
        List<String> graft = new ArrayList<>();
        graft.add("§7One more booster socket per graft, up to four.");
        graft.add("§7Boosters already socketed stay where they are.");
        graft.add("");
        if (gear == null || gear.getType().isAir()) {
            graft.add("§8Place a piece of gear in the middle.");
        } else if (grafts >= 4) {
            graft.add("§aFully grafted §8(18 sockets)");
        } else {
            int[] cost = graftCost(grafts + 1);
            graft.add("§7Graft §f" + (grafts + 1) + "§8/§f4 §7costs:");
            graft.addAll(costLines(player, cost[0], cost[1], cost[2], cost[3]));
            graft.add("");
            graft.add("§eClick to graft.");
        }
        inv.setItem(GRAFT, icon(Material.BONE, "§6Bonegraft", graft.toArray(String[]::new)));
        inv.setItem(INFO, icon(Material.BLAST_FURNACE, "§6The Heartforge",
                "§7Fed by Heart Embers from the stone heart.",
                "§7Titan Bone: ribs and vertebrae.",
                "§7Titan Marrow: the Marrowdeep.",
                "§7Heart Embers: the Heartbeat, every 30 minutes.",
                "",
                "§7You carry: §f" + TitanItems.count(player, TitanItems.Kind.BONE) + " §7bone, §f"
                        + TitanItems.count(player, TitanItems.Kind.MARROW) + " §7marrow, §f"
                        + TitanItems.count(player, TitanItems.Kind.EMBER) + " §7embers"));
        TitanRunes.Rune current = TitanRunes.runeOf(gear);
        TitanRunes.Rune[] runes = TitanRunes.Rune.values();
        for (int i = 0; i < RUNES.length && i < runes.length; i++) {
            TitanRunes.Rune rune = runes[i];
            boolean fits = rune.fits(gear);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + rune.effect());
            lore.add("§8For " + switch (rune.ordinal()) {
                case 0, 1, 2 -> "pickaxes";
                case 3 -> "boots";
                default -> "chestplates";
            } + " · one rune per item");
            lore.add("");
            if (rune == current) {
                lore.add("§aCarved into this item.");
            } else if (fits) {
                lore.addAll(costLines(player, RUNE_BONE, RUNE_MARROW, RUNE_EMBERS, RUNE_COINS));
                lore.add("");
                lore.add(current == null ? "§eClick to carve." : "§eClick to carve §7(replaces " + current.display() + ")§e.");
            } else {
                lore.add("§8Does not fit the item in the middle.");
            }
            inv.setItem(RUNES[i], icon(fits || rune == current ? Material.ECHO_SHARD : Material.FLINT,
                    "§6Titan Rune: §f" + rune.display(), lore.toArray(String[]::new)));
        }
    }

    private static List<String> costLines(Player player, int bone, int marrow, int embers, long coins) {
        CoinAccess wallet = AetherServices.coins();
        long have = wallet == null ? 0 : wallet.get(player);
        return List.of(
                mark(TitanItems.count(player, TitanItems.Kind.BONE) >= bone) + bone + " §7Titan Bone",
                mark(TitanItems.count(player, TitanItems.Kind.MARROW) >= marrow) + marrow + " §7Titan Marrow",
                mark(TitanItems.count(player, TitanItems.Kind.EMBER) >= embers) + embers + " §7Heart Embers",
                mark(have >= coins) + coins + " §7coins");
    }

    private static String mark(boolean ok) {
        return ok ? "§a✔ §f" : "§c✘ §f";
    }

    private static boolean pay(Player player, int bone, int marrow, int embers, long coins) {
        CoinAccess wallet = AetherServices.coins();
        if (TitanItems.count(player, TitanItems.Kind.BONE) < bone
                || TitanItems.count(player, TitanItems.Kind.MARROW) < marrow
                || TitanItems.count(player, TitanItems.Kind.EMBER) < embers
                || wallet == null || wallet.get(player) < coins) {
            player.sendMessage("§6The Grafter§f: Not enough. Bring what the forge asks for.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.6f, 0.8f);
            return false;
        }
        if (!wallet.take(player, coins)) {
            return false;
        }
        TitanItems.take(player, TitanItems.Kind.BONE, bone);
        TitanItems.take(player, TitanItems.Kind.MARROW, marrow);
        TitanItems.take(player, TitanItems.Kind.EMBER, embers);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != holder.inventory) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        ItemStack gear = holder.inventory.getItem(GEAR);
        if (slot == GEAR) {
            ItemStack cursor = event.getCursor();
            ItemStack placed = cursor == null || cursor.getType().isAir() ? null : cursor.clone();
            if (placed != null && placed.getAmount() > 1) {
                player.sendMessage("§7One item at a time.");
                return;
            }
            holder.inventory.setItem(GEAR, placed);
            player.setItemOnCursor(gear);
            paint(holder, player);
            return;
        }
        if (gear == null || gear.getType().isAir()) {
            return;
        }
        if (slot == GRAFT) {
            ItemFactoryAccess items = AetherServices.items();
            int grafts = items == null ? 4 : items.boneGrafts(gear);
            if (items == null || grafts >= 4) {
                return;
            }
            int[] cost = graftCost(grafts + 1);
            ItemStack probe = gear.clone();
            if (!items.addBoneGraft(probe)) {
                player.sendMessage("§6The Grafter§f: Only Aetherion gear takes a graft.");
                return;
            }
            if (!pay(player, cost[0], cost[1], cost[2], cost[3])) {
                return;
            }
            holder.inventory.setItem(GEAR, probe);
            forged(player, "§6Bonegraft " + (grafts + 1) + "§8/§64 §7· one more booster socket.");
            paint(holder, player);
            return;
        }
        for (int i = 0; i < RUNES.length; i++) {
            if (RUNES[i] != slot || i >= TitanRunes.Rune.values().length) {
                continue;
            }
            TitanRunes.Rune rune = TitanRunes.Rune.values()[i];
            if (!rune.fits(gear) || TitanRunes.runeOf(gear) == rune) {
                return;
            }
            if (!pay(player, RUNE_BONE, RUNE_MARROW, RUNE_EMBERS, RUNE_COINS)) {
                return;
            }
            ItemStack carved = gear.clone();
            TitanRunes.carve(carved, rune);
            holder.inventory.setItem(GEAR, carved);
            forged(player, "§6Titan Rune §f" + rune.display() + " §7carved.");
            paint(holder, player);
        }
    }

    private static void forged(Player player, String line) {
        player.sendMessage(line);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 0.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 1f, 0.7f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.02);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack gear = holder.inventory.getItem(GEAR);
        holder.inventory.setItem(GEAR, null);
        if (gear != null && !gear.getType().isAir()) {
            player.getInventory().addItem(gear).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(List.of(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
