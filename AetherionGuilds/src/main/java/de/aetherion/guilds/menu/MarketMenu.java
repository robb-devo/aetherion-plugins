package de.aetherion.guilds.menu;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coin Shortcuts: everything the factory needs that is normally crafted, for coins instead. Same item as the
 * craft (a bought quarry is a normal quarry), deliberately brutal prices, no shame either way. Prices are
 * config keys ({@code market.*}); a purchase takes two clicks so a misclick never costs millions.
 */
public final class MarketMenu implements MenuKit.Menu {

    private record Offer(int slot, String key, String name, Material icon, List<String> blurb, long defaultPrice,
                         QuarryType quarry, String itemId, int amount) {
    }

    private static final int HEADER = 4;
    private static final int BACK = 45;
    private static final int CLOSE = 49;
    private static final long ARM_MS = 6000L;

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final MinionService minions;
    private final List<Offer> offers = new ArrayList<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();
    private BuildMenu build;

    public MarketMenu(JavaPlugin plugin, HostService hosts, MinionService minions) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.minions = minions;
        // machines' parts
        offers.add(new Offer(10, "quarry_compressor", "Quarry Mill", Material.GRINDSTONE,
                List.of("§7The part a Mill is built around."), 2_500_000L, null, "quarry_compressor", 1));
        offers.add(new Offer(11, "quarry_compactor", "Quarry Forge", Material.BLAST_FURNACE,
                List.of("§7The part a Forge is built around."), 7_500_000L, null, "quarry_compactor", 1));
        offers.add(new Offer(12, "quarry_core", "Quarry Core", Material.HEART_OF_THE_SEA,
                List.of("§7Quarry upgrades, the Warehouse,", "§7the Bellows Forge."), 15_000_000L, null, "quarry_core", 1));
        offers.add(new Offer(13, "compressed_cobblestone_32", "32 Compressed Cobblestone", Material.COBBLESTONE,
                List.of("§7What a Mill makes from 4,096", "§7cobble. Upgrades eat it."), 6_000_000L, null,
                "compressed_cobblestone", 32));
        offers.add(new Offer(14, "compacted_cobblestone", "Compacted Cobblestone", Material.STONE,
                List.of("§7What a Forge makes from 128", "§7Compressed. Big upgrades eat it."), 40_000_000L, null,
                "compacted_cobblestone", 1));
        // quarries
        QuarryType[] quarries = {QuarryType.COBBLESTONE, QuarryType.COAL, QuarryType.RAW_COPPER, QuarryType.RAW_IRON,
                QuarryType.REDSTONE, QuarryType.LAPIS, QuarryType.RAW_GOLD, QuarryType.DIAMOND, QuarryType.EMERALD,
                QuarryType.OAK_LOG, QuarryType.WHEAT, QuarryType.BONE, QuarryType.STRING, QuarryType.COD};
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < quarries.length && i < slots.length; i++) {
            QuarryType type = quarries[i];
            long price = Math.round(1_500_000.0 / (type.rate() * type.rate()) / 100_000.0) * 100_000L;
            offers.add(new Offer(slots[i], "quarry_" + type.name().toLowerCase(java.util.Locale.ROOT), type.display(),
                    type.icon(), List.of("§7A level 1 " + type.display() + ".", "§7Makes " + type.productName() + "."),
                    price, type, null, 1));
        }
    }

    private de.aetherion.guilds.structure.StructureService structures;

    public void attach(BuildMenu build) {
        this.build = build;
    }

    public void attachHub(de.aetherion.guilds.structure.StructureService structures) {
        this.structures = structures;
    }

    private long price(Offer offer) {
        return Math.max(1L, plugin.getConfig().getLong("market." + offer.key(), offer.defaultPrice()));
    }

    public void open(Player player, IslandHost host) {
        if (structures != null && host != null) {
            if (structures.hub(host) == null) {
                player.sendMessage("§7Coin Shortcuts open at your §6Hub§7: build the Workshop first.");
                return;
            }
            if (!structures.atDesk(player, host)) {
                player.sendMessage("§7Coin Shortcuts are at your §6Hub §7(the Workshop lectern).");
                return;
            }
        }
        Inventory inventory = MenuKit.framed(this, host, 54, "§8Coin Shortcuts");
        long coins = AetherionItemsAccess.coins(player);
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.GOLD_INGOT, "§6§lCoin Shortcuts",
                "§7Normally crafted. Here, bought.",
                "§7The very same item either way.",
                "",
                "§7Your coins: §6" + GuildFormat.compact(coins),
                "",
                "§8Top: machine parts and materials.",
                "§8Below: quarries, ready to place.",
                "§8Click twice to buy (no misclicks).")));
        String pending = armed.get(player.getUniqueId());
        boolean pendingLive = pending != null && System.currentTimeMillis() - armedAt.getOrDefault(player.getUniqueId(), 0L) < ARM_MS;
        for (Offer offer : offers) {
            long price = price(offer);
            boolean afford = coins >= price;
            boolean confirm = pendingLive && offer.key().equals(pending);
            List<String> lore = new ArrayList<>(offer.blurb());
            lore.add("");
            lore.add("§7Price §6" + GuildFormat.compact(price) + " coins");
            if (offer.itemId() != null && !AetherionItemsAccess.available()) {
                lore.add("§c✖ Needs AetherionItems on this server");
            } else if (confirm) {
                lore.add("§e⚠ Click again to pay " + GuildFormat.compact(price));
            } else {
                lore.add(afford ? "§a▶ Click to buy" : "§c✖ Need " + GuildFormat.compact(price - coins) + " more coins");
            }
            ItemStack icon = MenuKit.named(offer.icon(), (afford ? "§f" : "§7") + offer.name()
                    + (offer.amount() > 1 && !offer.name().startsWith(String.valueOf(offer.amount())) ? " ×" + offer.amount() : ""), lore);
            inventory.setItem(offer.slot(), confirm ? MenuKit.glow(icon) : icon);
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBuild"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        IslandHost host = (IslandHost) holder.context();
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == BACK) {
            if (build != null && host != null) {
                build.openDesk(player, host);
            } else {
                player.closeInventory();
            }
            return;
        }
        Offer offer = null;
        for (Offer candidate : offers) {
            if (candidate.slot() == slot) {
                offer = candidate;
                break;
            }
        }
        if (offer == null) {
            return;
        }
        if (offer.itemId() != null && !AetherionItemsAccess.available()) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        boolean confirmed = offer.key().equals(armed.get(id)) && now - armedAt.getOrDefault(id, 0L) < ARM_MS;
        long price = price(offer);
        if (!confirmed) {
            if (AetherionItemsAccess.coins(player) < price) {
                player.sendMessage("§cThat costs §6" + GuildFormat.compact(price) + " coins§c.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                return;
            }
            armed.put(id, offer.key());
            armedAt.put(id, now);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f);
            open(player, host);
            return;
        }
        armed.remove(id);
        armedAt.remove(id);
        ItemStack item = make(offer);
        if (item == null) {
            player.sendMessage("§cThat item can't be made on this server right now.");
            return;
        }
        if (!AetherionItemsAccess.takeCoins(player, price)) {
            player.sendMessage("§cYou need §6" + GuildFormat.compact(price) + " coins§c.");
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        player.sendMessage("§6✦ Bought §f" + offer.name() + " §8(§6-" + GuildFormat.compact(price) + " coins§8)"
                + (offer.quarry() != null ? "§7. Place it on your land; its belt starts at the orange chute." : "§7."));
        open(player, host);
    }

    private ItemStack make(Offer offer) {
        if (offer.quarry() != null) {
            return minions.createQuarryItem(offer.quarry());
        }
        ItemFactoryAccess items = AetherServices.items();
        ItemStack made = items == null ? null : items.create(offer.itemId());
        if (made == null || made.getType().isAir()) {
            return null;
        }
        made = made.clone();
        made.setAmount(Math.max(1, Math.min(offer.amount(), made.getMaxStackSize())));
        return made;
    }
}
