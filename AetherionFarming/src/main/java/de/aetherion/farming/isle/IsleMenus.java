package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;
import de.aetherion.farming.FeaturedCropService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every Eldervale board: Field Warden, Harvest Orders, Oven House, Crop Mastery ledger, Beekeeper.
 * One holder type carries per-slot click actions, so each board reads top to bottom.
 */
public final class IsleMenus implements Listener {

    private static final int[] ORDER_SLOTS = {11, 13, 15};
    private static final int[] FOOD_SLOTS = {11, 12, 13, 14, 15};

    static final class Holder implements InventoryHolder {
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final FarmIsle isle;

    IsleMenus(FarmIsle isle) {
        this.isle = isle;
    }

    public void open(Player player, IsleRole role) {
        switch (role) {
            case WARDEN -> openWarden(player);
            case CLERK -> openOrders(player);
            case BAKER -> openBakehouse(player);
            case GRANARY -> openLedger(player);
            case BEEKEEPER -> openBeekeeper(player);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Field Warden

    public void openWarden(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Field Warden §7· Eldervale");
        IsleProfiles.Profile profile = isle.profiles().of(player);

        List<String> you = new ArrayList<>();
        you.add("§7Best Farming skill: §fLv. " + FarmingSkills.level(player));
        String credit = FarmingSkills.credit(player);
        you.add(credit == null ? "§8No Farming skill equipped." : "§7Focus: " + credit);
        you.add("§7Prize crops found: §6" + profile.prizes());
        you.add("§7Orders delivered: §6" + profile.ordersDone());
        you.add("");
        you.add("§eClick §7for your Farming skills.");
        inventory.setItem(4, head(player, "§a" + player.getName() + " §7on Eldervale", you));
        holder.actions.put(4, viewer -> {
            viewer.closeInventory();
            viewer.performCommand("skills farming");
        });

        HarvestRhythm.Tier tier = isle.rhythm().tier(player);
        set(holder, 10, Material.NOTE_BLOCK, "§d♪ Harvest Rhythm", lines(
                "§7Harvest mature crops without pausing.",
                "§7Every swing adds rhythm, idling drains it.",
                "",
                "§a20 §7Steady §8· §f+10 crop Fortune",
                "§e50 §7In the Groove §8· §f+25 · +10 Harvest",
                "§d100 §7Harvest Song §8· §f+50 · +25 Harvest · bonus XP",
                "",
                "§7Right now: " + (tier == HarvestRhythm.Tier.NONE ? "§8quiet" : tier.chat + tier.label()),
                "§8Row Rhythm skill + Sweetcane Cordial help."
        ), null);

        double chance = isle.prizes().chance(player, IsleCrop.CARROT, false);
        set(holder, 11, Material.GOLDEN_CARROT, "§6✦ Prize Crops", lines(
                "§7Now and then a harvest throws out a",
                "§7giant glowing crop. §eClick it fast§7 —",
                "§7after a few seconds anyone can grab it.",
                "",
                "§7Your odds per harvest: §f~1 in " + Math.max(1, Math.round(1.0d / Math.max(1.0e-6, chance))),
                "§7Heaviest you've grabbed: §6" + (profile.bestCrop() == null ? "—" : IsleText.kg(profile.bestKg())
                        + " " + profile.bestCrop().display()),
                "",
                "§8Blue Ribbon · Harvest Moon · Golden Pie raise it."
        ), null);

        set(holder, 12, Material.WRITABLE_BOOK, "§bCrop Mastery", masterySummary(player), guideTo(IsleRole.GRANARY));

        List<String> map = new ArrayList<>();
        int known = isle.compass().countKnown(profile);
        map.add("§7Discovered §f" + known + "§7/§f" + isle.plots().size() + " §7areas.");
        map.add("");
        for (IslePlots.Plot plot : isle.plots().all()) {
            map.add(profile.plots().contains(plot.id()) ? "§a✔ " + plot.colored() : "§8✘ ???");
        }
        map.add("");
        map.add(profile.cartographer ? "§6✦ Isle Cartographer" : "§7Find them all: §6+2,500 coins");
        IslePlots.Plot nextPlot = nearestUnknown(player, profile);
        if (nextPlot != null) {
            map.add("§eClick §7to be pointed at the nearest unknown area.");
        }
        set(holder, 13, Material.FILLED_MAP, "§eThe Isle", map, nextPlot == null ? null
                : viewer -> {
                    viewer.closeInventory();
                    isle.compass().guide(viewer, "an undiscovered area", nextPlot.center(viewer.getWorld()));
                });

        FeaturedCropService featured = isle.plugin().featuredCrop();
        if (featured != null && featured.featured() != null) {
            set(holder, 14, featured.featured(), "§eFeatured Crop: §6" + featured.prettyName(), lines(
                    "§7Harvesting it pays a little extra",
                    "§7on Eldervale this hour.",
                    "",
                    "§7Rotates in §f" + IsleText.clock(featured.secondsLeft())
            ), null);
        }

        set(holder, 15, Material.CLOCK, "§eIsle Events", lines(
                isle.events().statusLine(),
                "",
                "§eBee Bloom §7— one field: extra crops + XP.",
                "§bHarvest Moon §7— Prize Crops ×4.",
                "",
                "§eClick §7to find Old Wren at the Hive Lodge."
        ), guideTo(IsleRole.BEEKEEPER));

        Bakehouse.Food food = isle.bakehouse().active(player);
        List<String> foodLines = new ArrayList<>();
        if (food == null) {
            foodLines.add("§7No farming food active.");
            foodLines.add("§7Bram bakes crops into buffs.");
        } else {
            foodLines.add("§6" + food.display() + " §7· §f" + IsleText.clock(isle.bakehouse().secondsLeft(player)) + " §7left");
            foodLines.addAll(food.effectLines());
        }
        foodLines.add("");
        foodLines.add("§eClick §7to find the Oven House.");
        set(holder, 16, Material.BREAD, "§6Oven House", foodLines, guideTo(IsleRole.BAKER));

        List<String> records = new ArrayList<>();
        records.add("§7Heaviest prize of each crop:");
        records.add("");
        for (IsleCrop crop : IsleCrop.values()) {
            IsleProfiles.PrizeRecord record = isle.profiles().records().get(crop);
            if (record != null) {
                records.add(crop.colored() + " §8· §6" + IsleText.kg(record.kg()) + " §7by §f" + record.name());
            }
        }
        if (records.size() == 2) {
            records.add("§8No records yet. Be the first.");
        }
        set(holder, 22, Material.GOLD_BLOCK, "§6Isle Records", records, null);

        int ready = isle.orders().readyCount(player);
        set(holder, 29, Material.PAPER, "§6Harvest Orders", lines(
                "§7Hattie at the Market Barn pays well",
                "§7above trader prices for crops.",
                "",
                ready > 0 ? "§a" + ready + " order" + (ready == 1 ? "" : "s") + " ready to hand in!" : "§7Check the board for new jobs.",
                "",
                "§eClick §7to find the Market Barn."
        ), guideTo(IsleRole.CLERK));

        int slot = 30;
        for (IsleRole role : List.of(IsleRole.WARDEN, IsleRole.BAKER, IsleRole.GRANARY, IsleRole.BEEKEEPER,
                IsleRole.PIP, IsleRole.MARTA, IsleRole.TOBIAS)) {
            if (role == IsleRole.WARDEN) {
                continue;
            }
            set(holder, slot++, role.icon(), role.color() + role.display(), lines(
                    "§7" + role.title(),
                    "§8" + role.role(),
                    "",
                    isle.cast().whereabouts(role) == null ? "§8Not on the isle yet." : "§eClick §7to be pointed there."
            ), guideTo(role));
        }

        set(holder, 49, Material.BARRIER, "§cClose", List.of(), Player::closeInventory);
        player.openInventory(inventory);
        isle.cast().say(player, IsleRole.WARDEN, "Here's how the isle's treating you.");
    }

    private List<String> masterySummary(Player player) {
        List<String> out = new ArrayList<>();
        out.add("§7Lifetime harvests per crop pay");
        out.add("§7permanent crop-only Fortune.");
        out.add("§7Eldervale harvests count double.");
        out.add("");
        for (IsleCrop crop : IsleCrop.values()) {
            if (!crop.onIsle()) {
                continue;
            }
            int tier = isle.mastery().tier(player, crop);
            out.add(crop.colored() + " §8· §f" + IsleText.roman(tier) + " §8" + IsleText.cells(
                    CropMastery.fill(isle.mastery().count(player, crop)), "§a"));
        }
        out.add("");
        out.add("§eClick §7to find Gus at the Old Granary.");
        return out;
    }

    private IslePlots.Plot nearestUnknown(Player player, IsleProfiles.Profile profile) {
        IslePlots.Plot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (IslePlots.Plot plot : isle.plots().all()) {
            if (profile.plots().contains(plot.id())) {
                continue;
            }
            IslePlots.Circle main = plot.circles().get(0);
            double dx = main.x() - player.getLocation().getX();
            double dz = main.z() - player.getLocation().getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = plot;
            }
        }
        return best;
    }

    private Consumer<Player> guideTo(IsleRole role) {
        return viewer -> {
            Location target = isle.cast().whereabouts(role);
            viewer.closeInventory();
            isle.compass().guide(viewer, role.display() + " §7(" + role.title() + ")", target);
        };
    }

    // ------------------------------------------------------------------ Harvest Orders

    public void openOrders(Player player) {
        isle.orders().refresh(player);
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8Harvest Orders §7· Hattie");
        set(holder, 4, Material.PAPER, "§6Harvest Orders", lines(
                "§7Three jobs at a time. Deliver what's",
                "§7asked, get paid well over trader price.",
                "§7Finished slots restock after a short break.",
                "",
                "§8Market Day skill raises the coin payout."
        ), null);
        for (int i = 0; i < ORDER_SLOTS.length; i++) {
            int index = i;
            HarvestOrders.Order order = isle.orders().order(player, i);
            if (order == null) {
                set(holder, ORDER_SLOTS[i], Material.CLOCK, "§7Restocking…", lines(
                        "§7A new order goes up in §f" + IsleText.clock(isle.orders().restockSeconds(player, i)) + "§7."
                ), null);
                continue;
            }
            int have = isle.orders().have(player, order);
            boolean ready = have >= order.amount();
            long payout = isle.orders().payout(player, order);
            List<String> lore = new ArrayList<>();
            lore.add("§8For " + order.client());
            lore.add("");
            lore.add("§7Wants: §f" + order.want());
            lore.add("§7You have: " + (ready ? "§a" : "§c") + Math.min(have, order.amount()) + "§7/§f" + order.amount());
            lore.add("");
            lore.add("§7Pays: §6" + IsleText.coins(payout) + " coins" + (payout > order.coins() ? " §8(Market Day)" : ""));
            lore.add("§7Plus: §a" + order.xp() + " Farming XP");
            lore.add("");
            lore.add(ready ? "§aClick to deliver!" : "§8Bring the goods and click.");
            ItemStack icon = set(holder, ORDER_SLOTS[i], order.icon(), (ready ? "§a" : "§e") + order.want(), lore, viewer -> {
                if (isle.orders().deliver(viewer, index)) {
                    openOrders(viewer);
                }
            });
            if (ready) {
                glow(icon);
                inventory.setItem(ORDER_SLOTS[i], icon);
            }
        }
        long prizeValue = 0L;
        int prizeCount = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            long value = isle.prizes().valueOf(stack);
            if (value > 0L) {
                prizeValue += value;
                prizeCount += stack.getAmount();
            }
        }
        set(holder, 29, Material.GOLD_INGOT, "§6Sell Prize Crops", lines(
                "§7Hattie pays by weight.",
                "",
                prizeCount == 0 ? "§8You carry no prize crops." : "§7You carry §f" + prizeCount + " §7→ §6" + IsleText.coins(prizeValue) + " coins",
                "",
                prizeCount == 0 ? "" : "§eClick to sell them all."
        ), prizeCount == 0 ? null : viewer -> {
            isle.orders().sellPrizes(viewer);
            openOrders(viewer);
        });
        long freeIn = isle.orders().freeRerollSeconds(player);
        set(holder, 31, Material.WRITABLE_BOOK, "§eNew Board", lines(
                "§7Swap all three orders for new ones.",
                "",
                freeIn <= 0 ? "§aFree right now." : "§7Free again in §f" + IsleText.clock(freeIn) + "§7, or §6250 coins§7.",
                "",
                "§eClick to reroll."
        ), viewer -> {
            if (isle.orders().reroll(viewer)) {
                openOrders(viewer);
            }
        });
        IsleProfiles.Profile profile = isle.profiles().of(player);
        set(holder, 33, Material.EMERALD, "§aYour Ledger", lines(
                "§7Orders delivered: §f" + profile.ordersDone(),
                "§7Coins on hand: §6" + IsleText.coins(FarmingSkills.balance(player))
        ), null);
        set(holder, 40, Material.BARRIER, "§cClose", List.of(), Player::closeInventory);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Oven House

    public void openBakehouse(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8Oven House §7· Bram");
        set(holder, 4, Material.SMOKER, "§6The Oven House", lines(
                "§7Bram bakes Eldervale crops into food",
                "§7that makes you a better farmer.",
                "§7One food buff at a time — eat to start.",
                "",
                "§8Every buff is farming-only."
        ), null);
        Bakehouse.Food[] foods = Bakehouse.Food.values();
        for (int i = 0; i < foods.length && i < FOOD_SLOTS.length; i++) {
            Bakehouse.Food food = foods[i];
            List<String> lore = new ArrayList<>();
            lore.add("§8Recipe:");
            food.costs().forEach((crop, amount) -> {
                int have = OrderItems.countPlain(player.getInventory(), crop.yield());
                lore.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + " " + crop.display() + " §8(" + have + ")");
            });
            if (food.needsPrize()) {
                boolean hasPrize = OrderItems.firstPrize(player.getInventory(), isle.prizes(), null) >= 0;
                lore.add((hasPrize ? "§a✔ " : "§c✘ ") + "§f1 Prize Crop §8(any)");
            }
            lore.add("");
            lore.add("§8Effect for " + (food == Bakehouse.Food.GOLDEN_HARVEST_PIE ? "15" : "10") + " min:");
            for (String line : food.effectLines()) {
                lore.add("§8• " + line);
            }
            lore.add("");
            boolean can = isle.bakehouse().missing(player, food).isEmpty();
            lore.add(can ? "§aClick to bake!" : "§8Missing ingredients.");
            ItemStack icon = set(holder, FOOD_SLOTS[i], food.material(), "§6" + food.display(), lore, viewer -> {
                if (isle.bakehouse().bake(viewer, food)) {
                    openBakehouse(viewer);
                }
            });
            if (can) {
                glow(icon);
                inventory.setItem(FOOD_SLOTS[i], icon);
            }
        }
        Bakehouse.Food active = isle.bakehouse().active(player);
        List<String> now = new ArrayList<>();
        if (active == null) {
            now.add("§7No farming food active.");
        } else {
            now.add("§6" + active.display() + " §7· §f" + IsleText.clock(isle.bakehouse().secondsLeft(player)) + " §7left");
            now.addAll(active.effectLines());
        }
        set(holder, 31, Material.CAKE, "§eYour Buff", now, null);
        set(holder, 40, Material.BARRIER, "§cClose", List.of(), Player::closeInventory);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Crop Mastery ledger

    public void openLedger(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Crop Mastery §7· Gus");
        set(holder, 4, Material.WRITABLE_BOOK, "§bThe Ledger", lines(
                "§7Every harvest is written down.",
                "§7Tiers I–VI pay coins, Farming XP and",
                "§7permanent Fortune for that crop only.",
                "",
                "§6Eldervale harvests count double."
        ), null);
        int[] isleSlots = {20, 21, 22, 23, 24};
        int[] otherSlots = {29, 30, 32, 33};
        int isleIndex = 0;
        int otherIndex = 0;
        for (IsleCrop crop : IsleCrop.values()) {
            int slot = crop.onIsle() ? isleSlots[isleIndex++] : otherSlots[otherIndex++];
            long count = isle.mastery().count(player, crop);
            int tier = isle.mastery().tier(player, crop);
            List<String> lore = new ArrayList<>();
            lore.add("§7Harvested: §f" + IsleText.coins(count) + (crop.onIsle() ? "" : " §8(off-isle crop)"));
            lore.add("§7Crop Fortune: §a+" + (int) CropMastery.fortuneAt(tier));
            lore.add("");
            if (tier >= CropMastery.MAX_TIER) {
                lore.add("§6✦ Mastered.");
            } else {
                long next = CropMastery.THRESHOLDS[tier + 1];
                lore.add("§7Next: §f" + IsleText.roman(tier + 1) + " §7at §f" + IsleText.coins(next));
                lore.add(IsleText.cells(CropMastery.fill(count), "§a") + " §7" + IsleText.coins(count) + "§8/§7" + IsleText.coins(next));
                lore.add("");
                lore.add("§7Pays: §a+" + (int) CropMastery.fortuneAt(tier + 1) + " Fortune §8· §6"
                        + IsleText.coins(CropMastery.coinsAt(tier + 1)) + " coins §8· §b" + CropMastery.xpAt(tier + 1) + " XP");
            }
            ItemStack icon = set(holder, slot, crop.yield(), crop.color() + crop.display() + " §8· §f"
                    + (tier == 0 ? "—" : IsleText.roman(tier)), lore, null);
            if (tier >= CropMastery.MAX_TIER) {
                glow(icon);
                inventory.setItem(slot, icon);
            }
        }
        set(holder, 49, Material.BARRIER, "§cClose", List.of(), Player::closeInventory);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Beekeeper

    public void openBeekeeper(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 27, "§8Old Wren §7· Hive Lodge");
        set(holder, 11, Material.HONEYCOMB, "§eBee Bloom", lines(
                "§7The hives swarm one field for 90s.",
                "§7Harvest inside it: §f50% +1 crop§7,",
                "§f+2 Farming XP §7each.",
                "",
                "§8Watch for pollen and a yellow bar."
        ), null);
        set(holder, 13, Material.CLOCK, "§eWhat's next", lines(
                isle.events().statusLine(),
                "",
                "§7Events only roll while someone",
                "§7is farming on Eldervale."
        ), null);
        set(holder, 15, Material.END_ROD, "§bHarvest Moon", lines(
                "§7Two minutes of silver sky.",
                "§7Prize Crops turn up §f4× §7as often.",
                "",
                "§8Pair it with Blue Ribbon and a Golden Pie."
        ), null);
        set(holder, 22, Material.BARRIER, "§cClose", List.of(), Player::closeInventory);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ plumbing

    private static Inventory create(Holder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, pane);
        }
        return inventory;
    }

    private static ItemStack set(Holder holder, int slot, Material material, String name, List<String> lore,
                                 Consumer<Player> action) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> clean = new ArrayList<>();
            for (String line : lore) {
                if (line != null) {
                    clean.add(line);
                }
            }
            meta.setLore(clean);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        holder.inventory.setItem(slot, item);
        if (action != null) {
            holder.actions.put(slot, action);
        }
        return item;
    }

    private static ItemStack head(Player player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.setDisplayName(name);
            skull.setLore(lore);
            item.setItemMeta(skull);
        }
        return item;
    }

    private static void glow(ItemStack icon) {
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
        }
    }

    private static List<String> lines(String... lines) {
        return Arrays.asList(lines);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Consumer<Player> action = holder.actions.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.3f);
        Bukkit.getScheduler().runTask(isle.plugin(), () -> action.accept(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
