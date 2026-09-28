package de.aetherion.fishing.isle;

import de.aetherion.fishing.FishingSkills;
import de.aetherion.fishing.LureHead;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
import java.util.function.BiConsumer;

/**
 * Every Fishing Eldervale board: Harbourmaster (your angler card), Bait Shack, Trophy House
 * (Angler's Log + trophy sales) and the Lakewatcher (shoal and events). Content stays in the inner
 * columns; one holder type carries per-slot click actions.
 */
public final class LakeMenus implements Listener {

    private static final int[] LOG_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33};
    private static final int[] BAIT_SLOTS = {11, 12, 13, 14, 15};

    static final class Holder implements InventoryHolder {
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final FishIsle isle;

    LakeMenus(FishIsle isle) {
        this.isle = isle;
    }

    public void open(Player player, LakeRole role) {
        switch (role) {
            case HARBOURMASTER -> openHarbour(player);
            case BAIT -> openBait(player);
            case TAXIDERMIST -> openLog(player);
            case LAKEWATCHER -> openWatch(player);
        }
    }

    // ------------------------------------------------------------------ Harbourmaster

    public void openHarbour(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Harbourmaster §7· Fishing Eldervale");
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        int points = isle.log().points(profile);
        int rank = AnglerLog.rankFor(points);

        List<String> card = new ArrayList<>();
        card.add("§7Angler Rank §f" + LakeText.roman(rank) + " §8· " + AnglerLog.coloredTitle(rank));
        card.add(LakeText.cells(AnglerLog.fill(points), "§b") + " §7" + points + " Log points"
                + (rank >= AnglerLog.MAX_RANK ? "" : " §8(" + AnglerLog.toNext(points) + " to next)"));
        card.add("");
        card.add("§7Species in your Log: §f" + profile.speciesCount() + "§7/§f" + Species.values().length);
        card.add("§7Trophies landed: §6" + profile.trophies());
        card.add("§7Best streak: §e✦" + profile.bestStreak());
        card.add("§7Eldermaw hauls: §5" + profile.eldermaw());
        card.add("§7Isle catches: §f" + LakeText.coins(profile.catches()));
        card.add("");
        card.add("§7Isle bonus: §a+" + (int) (AnglerLog.CATCH_PER_RANK * rank) + " Fish Catch §8· §a+"
                + (int) (AnglerLog.SPEED_PER_RANK * rank) + " Fish Speed");
        String credit = FishingSkills.credit(player);
        card.add(credit == null ? "§8No Fishing skill equipped." : "§7Focus: " + credit);
        card.add("");
        card.add("§eClick §7for your Fishing skills.");
        inventory.setItem(4, head(player, "§b" + player.getName() + " §7· Angler Card", card));
        holder.actions.put(4, (viewer, click) -> {
            viewer.closeInventory();
            viewer.performCommand("skills fishing");
        });

        int streak = isle.streak(player);
        set(holder, 10, Material.FISHING_ROD, "§b≋ The Line", lines(
                "§7Land clean catches in a row and the",
                "§7water heats up. Hotter water, rarer fish.",
                "",
                "§e3 §7Warm §8· §6 5 §7Hot Water §8· §c10 §7Boiling §8· §d20 §7Whirlpool",
                "§7One miss and the heat is gone.",
                "",
                "§6Gold §7reels in a row weigh the fish again",
                "§7— perfect fishing lands heavier fish.",
                "",
                "§7Right now: " + (TheLine.heat(streak) == 0 ? "§8cold §7(✦" + streak + ")" : TheLine.heatName(TheLine.heat(streak)) + " §7(✦" + streak + ")")
        ), null);

        set(holder, 11, Material.WRITABLE_BOOK, "§9Angler's Log", lines(
                "§7Every species you land is written down,",
                "§7with your heaviest. New species and heavier",
                "§7bests earn Log points → Angler Rank.",
                "",
                "§7Your Log: §f" + profile.speciesCount() + "§7/§f" + Species.values().length + " §8· §f" + points + " §7points",
                "",
                "§eClick §7to find Odile at the Trophy House."
        ), guideTo(LakeRole.TAXIDERMIST));

        List<String> waters = new ArrayList<>();
        int known = isle.compass().countKnown(profile);
        waters.add("§7Discovered §f" + known + "§7/§f" + isle.waters().size() + " §7waters.");
        waters.add("");
        for (Waters.Water water : isle.waters().forBoards()) {
            waters.add(profile.waters().contains(water.id()) ? "§a✔ " + water.colored() + " §8" + water.kind().label() : "§8✘ ???");
        }
        waters.add("");
        waters.add(profile.waterfinder ? "§6✦ Waterfinder" : "§7Find them all: §6+2,500 coins");
        Waters.Water next = nearestUnknown(player, profile);
        if (next != null) {
            waters.add("§eClick §7to be pointed at the nearest unknown water.");
        }
        set(holder, 12, Material.FILLED_MAP, "§bThe Waters", waters, next == null ? null
                : (viewer, click) -> {
                    viewer.closeInventory();
                    isle.compass().guide(viewer, "an undiscovered water", next.center(viewer.getWorld()));
                });

        Shoals.Spot shoal = isle.shoals().current();
        set(holder, 13, Material.PRISMARINE_CRYSTALS, "§b≋ The Shoal", lines(
                isle.shoals().statusLine(),
                "",
                "§7Bites in half the time, rare fish crowd in.",
                "§7Fished out after a couple of dozen catches,",
                "§7then it gathers in another water.",
                "",
                shoal == null ? "§8Old Finn will know when it's back." : "§eClick §7to be pointed at it."
        ), shoal == null ? null : (viewer, click) -> {
            viewer.closeInventory();
            Shoals.Spot now = isle.shoals().current();
            if (now != null) {
                isle.compass().guide(viewer, "the shoal §7(" + isle.shoals().waterName() + ")", now.at());
            }
        });

        set(holder, 14, Material.BELL, "§eLake Events", lines(
                isle.events().statusLine(),
                "",
                "§fSilver Run §7— bites ×3, one miss forgiven.",
                "§5The Eldermaw §7— the whole lake hauls it in.",
                "§7The bells ring before either starts.",
                "",
                "§eClick §7to find Old Finn on the south pier."
        ), guideTo(LakeRole.LAKEWATCHER));

        Bait bait = profile.activeBait();
        set(holder, 15, bait == null ? Material.BUCKET : bait.icon(), "§eBait", lines(
                bait == null ? "§7Nothing on the hook." : "§7On the hook: §f" + bait.display() + " §8(" + profile.charges(bait) + ")",
                "",
                "§7Tilly turns cod, salmon, kelp and",
                "§7prismarine into bait that changes",
                "§7what bites.",
                "",
                "§eClick §7to find the Bait Shack."
        ), guideTo(LakeRole.BAIT));

        set(holder, 16, Material.GOLD_BLOCK, "§6Isle Records", recordLines(), null);

        List<String> ranks = new ArrayList<>();
        ranks.add("§7Each rank: §a+" + (int) AnglerLog.CATCH_PER_RANK + " Fish Catch §7and §a+"
                + (int) AnglerLog.SPEED_PER_RANK + " Fish Speed");
        ranks.add("§7on Eldervale — permanently.");
        ranks.add("");
        for (int i = 1; i <= AnglerLog.MAX_RANK; i++) {
            ranks.add((rank >= i ? "§a✔ " : "§8· ") + LakeText.roman(i) + " " + (rank >= i ? AnglerLog.coloredTitle(i) : "§8" + AnglerLog.title(i))
                    + " §8(" + AnglerLog.RANK_POINTS[i] + ")");
        }
        set(holder, 22, Material.NAUTILUS_SHELL, "§bAngler Ranks", ranks, null);

        int slot = 29;
        for (LakeRole role : LakeRole.values()) {
            if (role == LakeRole.HARBOURMASTER) {
                continue;
            }
            set(holder, slot, role.icon(), role.color() + role.display(), lines(
                    "§7" + role.title(),
                    "§8" + role.role(),
                    "",
                    isle.cast().whereabouts(role) == null ? "§8Not on the isle yet." : "§eClick §7to be pointed there."
            ), guideTo(role));
            slot += 2;
        }

        set(holder, 49, Material.BARRIER, "§cClose", List.of(), (viewer, click) -> viewer.closeInventory());
        player.openInventory(inventory);
        isle.cast().say(player, LakeRole.HARBOURMASTER, "Here's how the lake's treating you.");
    }

    private List<String> recordLines() {
        List<String> records = new ArrayList<>();
        records.add("§7Heaviest ever landed on Eldervale:");
        records.add("");
        for (Species species : Species.values()) {
            AnglerProfiles.Record record = isle.profiles().records().get(species);
            if (record != null) {
                records.add(species.colored() + " §8· §f" + LakeText.kg(record.kg()) + " §7by §f" + record.name());
            }
        }
        if (records.size() == 2) {
            records.add("§8No records yet. Be the first.");
        }
        return records;
    }

    private Waters.Water nearestUnknown(Player player, AnglerProfiles.Profile profile) {
        Waters.Water best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Waters.Water water : isle.waters().all()) {
            if (profile.waters().contains(water.id())) {
                continue;
            }
            Waters.Circle main = water.circles().get(0);
            double dx = main.x() - player.getLocation().getX();
            double dz = main.z() - player.getLocation().getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = water;
            }
        }
        return best;
    }

    private BiConsumer<Player, ClickType> guideTo(LakeRole role) {
        return (viewer, click) -> {
            Location target = isle.cast().whereabouts(role);
            viewer.closeInventory();
            isle.compass().guide(viewer, role.display() + " §7(" + role.title() + ")", target);
        };
    }

    // ------------------------------------------------------------------ Bait Shack

    public void openBait(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8Bait Shack §7· Tilly");
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        set(holder, 4, Material.COMPOSTER, "§eThe Bait Shack", lines(
                "§7Tilly turns what the lake gave you into",
                "§7bait that changes what bites.",
                "§7One bait on the hook at a time; a fish",
                "§7that takes it eats one charge — even if",
                "§7it gets away.",
                "",
                "§eLeft §7make a batch  §eRight §7put it on the hook"
        ), null);
        Bait[] baits = Bait.values();
        for (int i = 0; i < baits.length && i < BAIT_SLOTS.length; i++) {
            Bait bait = baits[i];
            List<String> lore = new ArrayList<>();
            lore.add("§8Recipe (makes " + bait.charges() + "):");
            bait.plain().forEach((material, amount) -> {
                int have = BaitShack.countPlain(player.getInventory(), material);
                lore.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + " " + BaitShack.plainName(material) + " §8(" + have + ")");
            });
            bait.custom().forEach((id, amount) -> {
                int have = BaitShack.countCustom(player.getInventory(), id);
                lore.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + " " + BaitShack.customName(id) + " §8(" + have + ")");
            });
            if (bait.coins() > 0L) {
                boolean rich = FishingSkills.balance(player) >= bait.coins();
                lore.add((rich ? "§a✔ " : "§c✘ ") + "§6" + LakeText.coins(bait.coins()) + " coins");
            }
            lore.add("");
            lore.addAll(bait.effect());
            lore.add("");
            int charges = profile.charges(bait);
            lore.add("§7In your tin: §f" + charges + (profile.activeBait() == bait ? " §a(on the hook)" : ""));
            boolean can = isle.bait().missing(player, bait).isEmpty();
            lore.add(can ? "§aLeft-click to make!" : "§8Missing ingredients.");
            if (charges > 0 && profile.activeBait() != bait) {
                lore.add("§eRight-click §7to put it on the hook.");
            }
            ItemStack icon = set(holder, BAIT_SLOTS[i], bait.icon(), "§e" + bait.display(), lore, (viewer, click) -> {
                if (click.isRightClick()) {
                    isle.bait().use(viewer, bait);
                } else {
                    isle.bait().make(viewer, bait);
                }
                openBait(viewer);
            });
            if (can || profile.activeBait() == bait) {
                glow(icon);
                inventory.setItem(BAIT_SLOTS[i], icon);
            }
        }
        set(holder, 29, Material.BUCKET, "§7No Bait", lines(
                "§7Fish plain. Saves your charges",
                "§7for when they count.",
                "",
                profile.activeBait() == null ? "§a(fishing without bait)" : "§eClick §7to take the bait off."
        ), (viewer, click) -> {
            isle.bait().use(viewer, null);
            openBait(viewer);
        });
        List<String> tin = new ArrayList<>();
        for (Bait bait : Bait.values()) {
            int charges = profile.charges(bait);
            if (charges > 0) {
                tin.add("§f" + bait.display() + " §8· §f" + charges);
            }
        }
        if (tin.isEmpty()) {
            tin.add("§8Empty.");
        }
        tin.add("");
        tin.add("§8Max " + BaitShack.MAX_CHARGES + " of each.");
        set(holder, 33, Material.CHEST, "§eYour Tin", tin, null);
        set(holder, 40, Material.BARRIER, "§cClose", List.of(), (viewer, click) -> viewer.closeInventory());
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Trophy House / Angler's Log

    public void openLog(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Angler's Log §7· Odile");
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        int points = isle.log().points(profile);
        int rank = AnglerLog.rankFor(points);
        set(holder, 4, Material.WRITABLE_BOOK, "§9The Angler's Log", lines(
                "§7Every Eldervale species. Land one to",
                "§7fill its page; land it heavier for",
                "§7silver, gold and record-class weight.",
                "",
                "§7Species: §f" + profile.speciesCount() + "§7/§f" + Species.values().length
                        + " §8· §7Points: §f" + points + "§7/§f" + AnglerLog.maxPoints(),
                "§7Rank: §f" + LakeText.roman(rank) + " " + AnglerLog.coloredTitle(rank)
        ), null);
        Species[] all = Species.values();
        for (int i = 0; i < all.length && i < LOG_SLOTS.length; i++) {
            Species species = all[i];
            AnglerProfiles.Entry entry = profile.entry(species);
            boolean caught = entry != null && entry.count() > 0;
            List<String> lore = new ArrayList<>();
            lore.add("§8" + species.rarity().label() + " §8· " + LakeText.kg(species.minKg()) + "–" + LakeText.kg(species.maxKg()));
            if (caught) {
                int tier = Trophies.tier(species, entry.bestKg());
                lore.add("§7Landed: §f" + LakeText.coins(entry.count()));
                lore.add("§7Heaviest: §f" + LakeText.kg(entry.bestKg()) + " §8(" + TheLine.weightWord(tier) + "§8)");
                lore.add("§7Log points: §b" + AnglerLog.points(species, entry) + "§8/" + (species.rarity().points() + 4));
                lore.add("");
                lore.add("§8\"" + species.line() + "\"");
            } else {
                lore.add("§8Not in your Log yet.");
            }
            lore.add("");
            lore.add("§7Lives in: §f" + species.haunts(isle.waters()));
            String when = species.timeHint();
            if (when != null) {
                lore.add("§7When: §f" + when);
            }
            AnglerProfiles.Record record = isle.profiles().records().get(species);
            if (record != null) {
                lore.add("§7Isle record: §6" + LakeText.kg(record.kg()) + " §7by §f" + record.name());
            }
            ItemStack icon;
            if (caught) {
                icon = LureHead.of(species.look());
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(species.colored());
                    meta.setLore(lore);
                    meta.addItemFlags(ItemFlag.values());
                    icon.setItemMeta(meta);
                }
                holder.inventory.setItem(LOG_SLOTS[i], icon);
                if (Trophies.tier(species, entry.bestKg()) >= 3) {
                    glow(icon);
                    inventory.setItem(LOG_SLOTS[i], icon);
                }
            } else {
                set(holder, LOG_SLOTS[i], Material.GRAY_DYE, "§8??? §7(" + species.rarity().color() + species.rarity().label() + "§7)", lore, null);
            }
        }
        long value = 0L;
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            long each = isle.trophies().valueOf(stack);
            if (each > 0L) {
                value += each;
                count += stack.getAmount();
            }
        }
        long total = value;
        int carried = count;
        set(holder, 47, Material.GOLD_INGOT, "§6Sell Trophies", lines(
                "§7Odile pays by weight.",
                "",
                carried == 0 ? "§8You carry no trophies." : "§7You carry §f" + carried + " §7→ §6" + LakeText.coins(total) + " coins",
                "",
                carried == 0 ? "" : "§eClick to sell them all."
        ), carried == 0 ? null : (viewer, click) -> {
            long[] sold = isle.trophies().sellAll(viewer);
            if (sold[0] > 0L) {
                FishingSkills.coins(viewer, sold[1]);
                viewer.sendMessage("§dOdile §8» §fThat's §6" + LakeText.coins(sold[1]) + " coins §ffor " + sold[0]
                        + " trophy fish. They'll look lovely on the wall.");
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
            openLog(viewer);
        });
        set(holder, 51, Material.GOLD_BLOCK, "§6Isle Records", recordLines(), null);
        set(holder, 49, Material.BARRIER, "§cClose", List.of(), (viewer, click) -> viewer.closeInventory());
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Lakewatcher

    public void openWatch(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 27, "§8Old Finn §7· Lakewatcher");
        World world = player.getWorld();
        long time = world.getTime();
        boolean night = time >= 13_000L && time <= 23_000L;
        boolean rain = world.hasStorm();
        List<String> now = new ArrayList<>();
        now.add("§7It's §f" + (night ? "night" : "day") + (rain ? " §7and §fraining" : "") + "§7.");
        now.add("");
        if (night) {
            now.add("§aLantern Eels §7are up in the coves,");
            now.add("§aOld Whiskers §7is prowling the South Deep.");
        } else {
            now.add("§aGlasswing Dace §7flash in Mirror Reach,");
            now.add("§aBluegills §7are showing off.");
        }
        if (rain) {
            now.add("§9Cloudscale Char §7rise in the Skyfall Tarns.");
            if (night) {
                now.add("§6…and something pale is swimming up high.");
            }
        }
        set(holder, 4, Material.SPYGLASS, "§3What's biting", now, null);
        Shoals.Spot shoal = isle.shoals().current();
        set(holder, 11, Material.PRISMARINE_CRYSTALS, "§b≋ The Shoal", lines(
                isle.shoals().statusLine(),
                "",
                "§7Look for boiling water and leaping fish.",
                "§7Cast within a few blocks of it.",
                "",
                shoal == null ? "§8Nothing boiling right now." : "§eClick §7and I'll point you."
        ), shoal == null ? null : (viewer, click) -> {
            viewer.closeInventory();
            Shoals.Spot current = isle.shoals().current();
            if (current != null) {
                isle.compass().guide(viewer, "the shoal §7(" + isle.shoals().waterName() + ")", current.at());
            }
        });
        set(holder, 13, Material.BELL, "§eWhat's next", lines(
                isle.events().statusLine(),
                "",
                "§7Events only come while someone",
                "§7is fishing on Eldervale.",
                "§7The bells ring ten seconds before."
        ), null);
        set(holder, 15, Material.HEART_OF_THE_SEA, "§5The Eldermaw", lines(
                "§7It circles one of the big lakes.",
                "§7Every fish anyone lands on the isle",
                "§7is a heave on its line:",
                "§8• §fperfect reel ×2 §8· §fBoiling streak +1",
                "§8• §ffishing its own water +1",
                "",
                "§7Haul it up before it dives — everyone",
                "§7who pulled gets paid; the strongest arm",
                "§7keeps an §5Eldermaw Scale§7."
        ), null);
        set(holder, 22, Material.BARRIER, "§cClose", List.of(), (viewer, click) -> viewer.closeInventory());
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
                                 BiConsumer<Player, ClickType> action) {
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
        BiConsumer<Player, ClickType> action = holder.actions.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        ClickType click = event.getClick();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.3f);
        Bukkit.getScheduler().runTask(isle.plugin(), () -> action.accept(player, click));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
