package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.project.GuildProjectType;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The project board (lectern on the Guild Harbour plaza, or /guild project). */
public final class GuildProjectMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int[] STAGE_SLOTS = {19, 20, 21};
    private static final int[] CHOICE_SLOTS = {20, 22, 24};
    private static final int FROM_PACK = 23;
    private static final int FROM_STORAGE = 24;
    private static final int COINS = 25;
    private static final int FROM_BANK = 34;
    private static final int VISIT = 32;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final GuildService guilds;
    private final GuildProjectService projects;

    public GuildProjectMenu(GuildService guilds, GuildProjectService projects) {
        this.guilds = guilds;
        this.projects = projects;
    }

    public void open(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cJoin or found a guild first.");
            return;
        }
        Inventory inventory = MenuKit.create(this, guild.id(), 54, "§8Guild Projects · " + guild.name());
        List<String> header = new ArrayList<>();
        header.add("§7Renown: §f" + projects.renown(guild));
        List<GuildProjectType> done = projects.completedList(guild);
        header.add("§7Built: " + (done.isEmpty() ? "§8nothing yet" : "§f" + String.join(", ",
                done.stream().map(GuildProjectType::display).toList())));
        header.add("");
        header.add("§8Mayors start a project, everyone");
        header.add("§8chips in. Each paid stage rises.");
        inventory.setItem(HEADER, MenuKit.named(Material.LECTERN, "§6⚑ " + guild.name(), header));
        GuildProjectService.Active active = projects.active(guild);
        if (active == null) {
            GuildRank rank = guild.rank(player.getUniqueId());
            boolean mayor = rank != null && rank.canUpgradeIsland();
            GuildProjectType[] types = GuildProjectType.values();
            for (int i = 0; i < types.length && i < CHOICE_SLOTS.length; i++) {
                GuildProjectType type = types[i];
                List<String> lore = new ArrayList<>(type.blurb());
                lore.add("");
                for (int s = 0; s < type.stages().size(); s++) {
                    lore.add("§7Stage " + (s + 1) + ": §f" + type.stages().get(s).name());
                }
                lore.add("");
                if (projects.completed(guild, type)) {
                    lore.add("§a✔ Built");
                    inventory.setItem(CHOICE_SLOTS[i], MenuKit.named(type.icon(), "§a" + type.display(), lore));
                } else {
                    lore.add(mayor ? "§a▶ Click to start" : "§8A Mayor+ can start it");
                    inventory.setItem(CHOICE_SLOTS[i], mayor
                            ? MenuKit.glow(MenuKit.named(type.icon(), "§e" + type.display(), lore))
                            : MenuKit.named(type.icon(), "§7" + type.display(), lore));
                }
            }
        } else {
            GuildProjectType type = active.type();
            for (int s = 0; s < type.stages().size() && s < STAGE_SLOTS.length; s++) {
                GuildProjectType.Stage stage = type.stages().get(s);
                List<String> lore = new ArrayList<>();
                Material icon;
                String name;
                if (s < active.stage()) {
                    icon = Material.LIME_CONCRETE;
                    name = "§a✔ Stage " + (s + 1) + ": " + stage.name();
                    lore.add("§7Standing.");
                } else if (s == active.stage()) {
                    icon = Material.YELLOW_CONCRETE;
                    name = "§e▶ Stage " + (s + 1) + ": " + stage.name();
                    for (GuildProjectType.Req req : stage.reqs()) {
                        long paid = active.paid(req.key());
                        lore.add("§f" + req.label() + " §7" + GuildFormat.compact(Math.min(paid, req.amount())) + "§8/§7"
                                + GuildFormat.compact(req.amount()));
                        lore.add("  " + GuildProjectService.bar(paid, req.amount()));
                    }
                } else {
                    icon = Material.GRAY_CONCRETE;
                    name = "§8Stage " + (s + 1) + ": " + stage.name();
                    for (GuildProjectType.Req req : stage.reqs()) {
                        lore.add("§8" + GuildFormat.compact(req.amount()) + " " + req.label());
                    }
                }
                inventory.setItem(STAGE_SLOTS[s], MenuKit.named(icon, name, lore));
            }
            inventory.setItem(22, MenuKit.glow(MenuKit.named(type.icon(), "§6" + type.display(),
                    "§7Still needed: " + projects.needs(active))));
            inventory.setItem(FROM_PACK, MenuKit.named(Material.CHEST, "§aGive from your pack",
                    "§7Takes what this stage still needs",
                    "§7from your inventory.", "§eClick"));
            inventory.setItem(FROM_STORAGE, MenuKit.named(Material.BARREL, "§aGive from guild storage",
                    "§7Pulls from the guild's Storage Huts",
                    "§7and Depots (what the belts filled).", "§eClick"));
            inventory.setItem(COINS, MenuKit.named(Material.GOLD_INGOT, "§6Give coins",
                    "§eLeft §71,000 · §eRight §710,000", "§eShift §7100,000", "§8From your own purse."));
            inventory.setItem(FROM_BANK, MenuKit.named(Material.ENDER_CHEST, "§6Pay from the guild bank",
                    "§eClick §710,000 · §eShift §7100,000", "§8Soldier+."));
            inventory.setItem(VISIT, MenuKit.named(Material.COMPASS, "§eGo to the site"));
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBack"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Guild guild = guilds.byId((UUID) holder.context());
        if (guild == null || guild.rank(player.getUniqueId()) == null) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == BACK) {
            player.closeInventory();
            player.performCommand("guild");
            return;
        }
        GuildProjectService.Active active = projects.active(guild);
        if (active == null) {
            GuildProjectType[] types = GuildProjectType.values();
            for (int i = 0; i < types.length && i < CHOICE_SLOTS.length; i++) {
                if (CHOICE_SLOTS[i] == slot && !projects.completed(guild, types[i])) {
                    player.closeInventory();
                    projects.start(player, guild, types[i]);
                    return;
                }
            }
            return;
        }
        switch (slot) {
            case FROM_PACK -> {
                projects.contributeInventory(player, guild);
                reopen(player);
            }
            case FROM_STORAGE -> {
                projects.contributeStorage(player, guild);
                reopen(player);
            }
            case COINS -> {
                long amount = click.isShiftClick() ? 100_000L : click.isRightClick() ? 10_000L : 1_000L;
                projects.contributeCoins(player, guild, amount, false);
                reopen(player);
            }
            case FROM_BANK -> {
                projects.contributeCoins(player, guild, click.isShiftClick() ? 100_000L : 10_000L, true);
                reopen(player);
            }
            case VISIT -> {
                Location site = active.site();
                if (site != null) {
                    player.closeInventory();
                    Location view = site.clone().add(0, 1, 11);
                    view.setYaw(180f);
                    player.teleport(view);
                }
            }
            default -> {
            }
        }
    }

    private void reopen(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuKit.Holder) {
            open(player);
        }
    }
}
