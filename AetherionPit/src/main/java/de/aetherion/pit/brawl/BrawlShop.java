package de.aetherion.pit.brawl;

import de.aetherion.core.api.ChromePage;
import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.hub.HubHolograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The outfitter in the elven house (2026-11-27, replaces the Pit shop): your Sky Brawl record and the knock-off
 * effects, bought with the shards every knock-off brings. One page, both languages.
 */
public final class BrawlShop implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int STATS = 4;
    private static final int HOWTO = 22;
    private static final int CLOSE = 26;

    private final AetherionPit plugin;

    public record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public BrawlShop(AetherionPit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        SkyBrawl brawl = plugin.brawl();
        if (brawl == null) {
            return;
        }
        boolean de = HubHolograms.german(player);
        BrawlStore.Stats s = brawl.store().of(player);
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Component.text(de ? "Ausstatter" : "Outfitter"));
        ItemStack pane = ChromePage.spacer();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane.clone());
        }
        inv.setItem(STATS, item(Material.NETHER_STAR, Component.text(de ? "Dein Sky Brawl" : "Your Sky Brawl", SkyBrawl.STORM,
                TextDecoration.BOLD), List.of(
                line((de ? "Geschubst: " : "Knock-offs: ") + s.knockoffs, NamedTextColor.WHITE),
                line((de ? "Selbst gefallen: " : "Falls: ") + s.falls, SkyBrawl.SOFT),
                line((de ? "Beste Serie: " : "Best streak: ") + s.best, SkyBrawl.SOFT),
                Component.empty(),
                line("✦ " + s.shards + (de ? " Splitter" : " shards"), SkyBrawl.GOLD),
                line(de ? "Jeder Schubs bringt 5 Splitter." : "Every knock-off brings 5 shards.", SkyBrawl.SOFT)), false));
        BrawlEffect[] all = BrawlEffect.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            BrawlEffect e = all[i];
            boolean owned = s.owned.contains(e.name());
            boolean chosen = s.effect().equals(e);
            List<Component> lore = new ArrayList<>();
            lore.add(line(de ? e.loreDe : e.loreEn, SkyBrawl.SOFT));
            lore.add(Component.empty());
            if (chosen) {
                lore.add(line(de ? "✔ Ausgewählt" : "✔ Selected", TextColor.color(0x7EE07E)));
            } else if (owned) {
                lore.add(line(de ? "▸ Klick zum Auswählen" : "▸ Click to select", SkyBrawl.STORM));
            } else {
                boolean afford = s.shards >= e.price;
                lore.add(line("✦ " + e.price + (de ? " Splitter" : " shards"), afford ? SkyBrawl.GOLD : TextColor.color(0x9A8F86)));
                lore.add(line(afford ? (de ? "▸ Klick zum Kaufen" : "▸ Click to buy")
                        : (de ? "Noch " + (e.price - s.shards) + " Splitter" : (e.price - s.shards) + " shards to go"),
                        afford ? SkyBrawl.STORM : TextColor.color(0x9A8F86)));
            }
            TextColor title = chosen ? TextColor.color(0x7EE07E) : owned ? NamedTextColor.WHITE : SkyBrawl.SOFT;
            inv.setItem(SLOTS[i], item(e.icon, Component.text(e.name(de), title, TextDecoration.BOLD), lore, chosen));
        }
        inv.setItem(HOWTO, item(Material.BREEZE_ROD, Component.text(de ? "So geht Sky Brawl" : "How Sky Brawl works",
                SkyBrawl.STORM, TextDecoration.BOLD), List.of(
                line(de ? "Durch das Sturmtor am Ostende der Insel." : "Through the storm gate at the east end.", SkyBrawl.SOFT),
                line(de ? "Böenstab schubst, keiner nimmt Schaden." : "The gust rod pushes, nobody takes damage.", SkyBrawl.SOFT),
                line(de ? "Windkugel werfen, unter dir: hoher Sprung." : "Throw wind charges; below you: a high jump.",
                        SkyBrawl.SOFT),
                line(de ? "Schubs andere vom Rand: +1 und Splitter." : "Push others off the edge: +1 and shards.", SkyBrawl.SOFT),
                line(de ? "Die Sturmkugel in der Mitte: doppelt schubsen." : "The storm orb in the middle: push twice as hard.",
                        SkyBrawl.SOFT)), false));
        inv.setItem(CLOSE, item(Material.BARRIER, Component.text(de ? "Schließen" : "Close", NamedTextColor.WHITE), List.of(), false));
        ChromePage.open(player, inv, ChromePage.Theme.TRADE, de ? "AUSSTATTER" : "OUTFITTER");
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, Component name, List<Component> lore, boolean glint) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                idx = i;
            }
        }
        BrawlEffect[] all = BrawlEffect.values();
        if (idx < 0 || idx >= all.length) {
            return;
        }
        BrawlEffect e = all[idx];
        BrawlStore store = plugin.brawl().store();
        BrawlStore.Stats s = store.of(player);
        boolean de = HubHolograms.german(player);
        if (!s.owned.contains(e.name())) {
            if (s.shards < e.price) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
                return;
            }
            s.shards -= e.price;
            s.owned.add(e.name());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);
            player.sendMessage(Component.text(" ✦ ", SkyBrawl.STORM).append(Component.text(
                    (de ? "Gekauft: " : "Bought: ") + e.name(de), SkyBrawl.SOFT)));
        }
        s.effect = e.name();
        store.touch();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        e.play(plugin, player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.6)));
        open(player);
    }
}
