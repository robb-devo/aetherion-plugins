package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The land map: 9 columns x 5 rows of 16x16 parcels, north up, scrollable north/south. Owned land shows as
 * ground, land you can buy (touching yours) glows green, the rest is out of reach.
 */
public final class LandMenu implements MenuKit.Menu {

    private static final int BACK = 45;
    private static final int UP = 46;
    private static final int DOWN = 47;
    private static final int INFO = 49;
    private static final int RAISE = 50;
    private static final int BORDER = 51;
    private static final int CLOSE = 53;

    private static final class View {
        final IslandHost host;
        int top = -2;
        boolean raise = true;

        View(IslandHost host) {
            this.host = host;
        }
    }

    private final HostService hosts;
    private final LandService land;

    public LandMenu(HostService hosts, LandService land) {
        this.hosts = hosts;
        this.land = land;
    }

    public void open(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            player.sendMessage("§cNo island here.");
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        render(player, new View(host));
    }

    private void render(Player player, View view) {
        IslandHost host = view.host;
        Inventory inventory = MenuKit.create(this, view, 54, "§8Land · " + hosts.title(host));
        int here = -99;
        int herePz = -99;
        IslandHost standing = hosts.at(player.getLocation());
        if (host.equals(standing)) {
            here = LandService.parcelOf(player.getLocation().getBlockX() - hosts.originX(host));
            herePz = LandService.parcelOf(player.getLocation().getBlockZ() - hosts.originZ(host));
        }
        long price = land.price(host);
        StarterLayout starter = hosts.starter(host);
        for (int row = 0; row < 5; row++) {
            int pz = view.top + row;
            for (int col = 0; col < 9; col++) {
                int px = col - 4;
                LandService.Status status = land.status(host, px, pz);
                boolean youAreHere = px == here && pz == herePz;
                inventory.setItem(row * 9 + col, cell(host, px, pz, status, youAreHere, price, view.raise, starter));
            }
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBack"));
        inventory.setItem(UP, MenuKit.named(Material.SPECTRAL_ARROW, view.top > -LandService.RING ? "§eScroll north" : "§8North edge"));
        inventory.setItem(DOWN, MenuKit.named(Material.SPECTRAL_ARROW,
                view.top + 4 < LandService.RING ? "§eScroll south" : "§8South edge"));
        inventory.setItem(INFO, MenuKit.named(Material.FILLED_MAP, "§6Your land",
                "§7Parcels owned: §f" + hosts.parcels(host).size(),
                "§7Each parcel: §f16×16",
                "§7Next parcel: §6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host),
                "§7You have: §6" + (hosts.funds(player, host) == Long.MAX_VALUE ? "∞" : GuildFormat.compact(hosts.funds(player, host))),
                "",
                "§8Buy land touching yours. New ground",
                "§8rises there in your island's look.",
                host.isGuild() ? "§8Guild land: Mayor+, paid from the bank." : "§8Tier upgrades still add radius too."));
        inventory.setItem(RAISE, MenuKit.named(view.raise ? Material.GRASS_BLOCK : Material.GLASS,
                view.raise ? "§aRaise new ground: ON" : "§7Raise new ground: OFF",
                "§7ON: bought land rises from the void.",
                "§7OFF: you just get the build right",
                "§7(bridges, sky docks, floating builds).",
                "§eClick to toggle"));
        inventory.setItem(BORDER, MenuKit.named(Material.SPYGLASS, "§eShow build border",
                "§7Gold particles trace your land."));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private ItemStack cell(IslandHost host, int px, int pz, LandService.Status status, boolean youAreHere, long price,
                           boolean raise, StarterLayout starter) {
        String coords = "§8(" + px + ", " + pz + ")";
        List<String> lore = new ArrayList<>();
        if (youAreHere) {
            lore.add("§b● you are here");
        }
        ItemStack item;
        switch (status) {
            case OWNED -> {
                Material ground = switch (hosts.land(host)) {
                    case SANDY -> Material.SAND;
                    case ROCKY -> Material.STONE;
                    default -> Material.GRASS_BLOCK;
                };
                if (px == 0 && pz == 0) {
                    lore.add(0, "§7The heart of your island.");
                    item = MenuKit.named(starter != null ? starter.icon() : Material.BEACON, "§aIsland heart " + coords, lore);
                } else {
                    lore.add(0, "§7Yours to build on.");
                    item = MenuKit.named(ground, "§aYour land " + coords, lore);
                }
            }
            case BUYABLE -> {
                lore.add("§7Touches your land.");
                lore.add("§7Price: §6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host));
                lore.add(raise ? "§7New ground rises here." : "§7Build rights only (no ground).");
                lore.add("");
                lore.add("§e▶ Click to buy");
                item = MenuKit.glow(MenuKit.named(Material.LIME_STAINED_GLASS_PANE, "§eBuy this land " + coords, lore));
            }
            case OUT_OF_REACH -> {
                lore.add("§7Buy the land next to it first.");
                item = MenuKit.named(Material.BLACK_STAINED_GLASS_PANE, "§8Beyond reach " + coords, lore);
            }
            default -> item = MenuKit.named(Material.GRAY_STAINED_GLASS_PANE, "§8Open sky " + coords);
        }
        if (youAreHere) {
            MenuKit.glow(item);
        }
        return item;
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        View view = (View) holder.context();
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> {
                player.closeInventory();
                player.performCommand(view.host.isGuild() ? "guild" : "island");
            }
            case UP -> {
                view.top = Math.max(-LandService.RING, view.top - 1);
                render(player, view);
            }
            case DOWN -> {
                view.top = Math.min(LandService.RING - 4, view.top + 1);
                render(player, view);
            }
            case RAISE -> {
                view.raise = !view.raise;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                render(player, view);
            }
            case BORDER -> {
                player.closeInventory();
                land.flashBorder(player, view.host);
                land.sendBorderMessage(player);
            }
            default -> {
                if (slot < 0 || slot >= 45) {
                    return;
                }
                int px = slot % 9 - 4;
                int pz = view.top + slot / 9;
                if (land.status(view.host, px, pz) != LandService.Status.BUYABLE) {
                    return;
                }
                if (land.buy(player, view.host, px, pz, view.raise)) {
                    player.closeInventory();
                }
            }
        }
    }
}
