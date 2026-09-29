package de.aetherion.mining.isle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Every Mining Eldervale board: Old Wick (map, depth, tour), the Contract Office, the Hearth, the
 * Assay Office (collections, mastery, cabinet, specimen sales), the Deep Forge (marks, rep,
 * consumables), the Last Lamp (depth, jaws, deep trade) and the player's Mining Journal
 * ({@code /mineisle}). Content stays in the inner columns, and a single holder type carries the
 * per-slot click actions.
 */
public final class MineMenus implements Listener {

    private static final int[] INNER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    static final class Holder implements InventoryHolder {
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final MineIsle isle;

    MineMenus(MineIsle isle) {
        this.isle = isle;
    }

    public void open(Player player, MineRole role) {
        switch (role) {
            case LAMPWARDEN -> openWick(player);
            case CLERK -> openContracts(player);
            case COOK -> openHearth(player);
            case ASSAYER -> openAssay(player, 0);
            case FORGEMASTER -> openForge(player);
            case HERMIT -> openLastLamp(player);
        }
    }

    // ------------------------------------------------------------------ Old Wick

    public void openWick(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Old Wick §7· The Lay of the Rock");
        MineProfiles.Profile profile = isle.profiles().of(player);
        List<MineDistricts.District> districts = isle.districts().all();
        int found = isle.compass().countKnown(profile);
        set(holder, 4, Material.FILLED_MAP, "§eMining Eldervale §7· " + found + "/" + districts.size() + " places", lines(
                "§7Halls on top, quarries in the open,",
                "§7shafts in the dark, water where it pools.",
                "",
                profile.surveyor() ? "§6✦ Cartographer §7— you've walked it all." : "§7Walk every place for §6Cartographer§7.",
                "",
                "§eClick a place §7to point your compass at it."), null);
        int index = 0;
        for (MineDistricts.District district : districts) {
            if (index >= INNER.length) {
                break;
            }
            boolean known = profile.districts().contains(district.id());
            Material icon = switch (district.kind()) {
                case HALL -> Material.LANTERN;
                case SHAFT -> Material.CHAIN;
                case WATER -> Material.WATER_BUCKET;
                case QUARRY -> Material.IRON_PICKAXE;
            };
            List<String> lore = new ArrayList<>();
            lore.add("§8" + district.kind().noun());
            if (known && !district.blurb().isBlank()) {
                lore.add("§7" + district.blurb());
            }
            if (district.maxY() < 300 || district.minY() > -64) {
                lore.add("§8Y " + (int) Math.max(-64, district.minY()) + " to " + (int) Math.min(320, district.maxY()));
            }
            lore.add("");
            lore.add(known ? "§a✔ Discovered" : "§8??? §7— not found yet");
            lore.add("§eClick §7to set the compass.");
            ItemStack item = set(holder, INNER[index++], known ? icon : Material.GRAY_DYE,
                    known ? district.colored() : "§8Unknown " + district.kind().noun(), lore,
                    (viewer, click) -> {
                        viewer.closeInventory();
                        isle.compass().guide(viewer, district);
                    });
            if (known && district.kind() == MineDistricts.Kind.SHAFT) {
                glow(item);
            }
        }
        MineDistricts.Band band = isle.districts().band(player.getLocation());
        List<String> depth = new ArrayList<>();
        depth.add("§7You're in: " + band.colored() + " §8(Y " + player.getLocation().getBlockY() + ")");
        depth.add("§7Deepest strike: §f" + (profile.deepest() == Integer.MAX_VALUE ? "—" : "Y " + profile.deepest()));
        depth.add("");
        for (MineDistricts.Band each : MineDistricts.Band.values()) {
            boolean reached = each == MineDistricts.Band.SURFACE || profile.bands().contains(each.name());
            double top = isle.districts().bandTop(each);
            depth.add((reached ? "§a✔ " : "§8✘ ") + each.colored() + " §8" + (Double.isInfinite(top) ? "" : "below Y " + (int) top)
                    + " §7+" + MineText.num(each.fortune()) + " Fortune · ×" + MineText.num(each.crystalMult()) + " finds");
        }
        depth.add("");
        depth.add("§8Depth Gauge boosts every line. The Undercroft is dark:");
        depth.add("§8take a lantern, a Canary, or Cave Sense.");
        set(holder, 48, Material.RECOVERY_COMPASS, "§3Depth Card", depth, null);
        set(holder, 49, Material.COMPASS, "§eTour the Crew", lines(
                "§7Old Wick → Otto → Nan → Ilse → Brann → Hollis.",
                "§7Meet everyone for a little XP, and to learn",
                "§7where every loop lives.",
                "",
                "§7Met: §f" + profile.met.size() + "§7/6",
                "§eClick §7for the next stop."), (viewer, click) -> {
            viewer.closeInventory();
            isle.compass().tour(viewer);
        });
        set(holder, 50, Material.BARRIER, "§cClear Compass", lines("§7Stop pointing anywhere."), (viewer, click) -> {
            viewer.closeInventory();
            isle.compass().stop(viewer);
        });
        set(holder, 45, Material.BOOK, "§6Mining Journal", lines("§7Everything you've done down here.", "§8/mineisle"),
                (viewer, click) -> openJournal(viewer));
        crewRow(holder, 46);
        player.openInventory(inventory);
    }

    private void crewRow(Holder holder, int slot) {
        set(holder, slot, Material.PLAYER_HEAD, "§6The Crew", lines(
                "§eOld Wick §8· §7Lampwarden · compass & depth",
                "§6Otto §8· §7Contract Office",
                "§cNan §8· §7The Hearth · rations",
                "§dIlse §8· §7Assay Office · collections & cabinet",
                "§6Brann §8· §7The Deep Forge · marks",
                "§3Hollis §8· §7Last Lamp · the deep",
                "",
                "§eLeft-click §7cycles the compass through them."), (viewer, click) -> {
            MineRole[] crew = MineRole.values();
            MineRole next = crew[(int) ((System.currentTimeMillis() / 1500L) % crew.length)];
            viewer.closeInventory();
            isle.compass().guide(viewer, next);
        });
    }

    // ------------------------------------------------------------------ Contract Office

    public void openContracts(Player player) {
        ForemanContracts contracts = isle.contracts();
        contracts.refresh(player);
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8Contract Office §7· Otto Brassbuckle");
        MineProfiles.Profile profile = isle.profiles().of(player);
        double union = Math.min(0.6d, 0.15d * MineSkills.scale(player, MineSkills.UNION_CARD));
        set(holder, 4, Material.WRITABLE_BOOK, "§6Foreman Contracts", lines(
                "§7Three jobs, pinned up for you alone.",
                "§7Hand-ins take goods from your bag.",
                "§7Shifts count what you mine: deep ones",
                "§7below the Deep Works line, Amethyst ones in /amethyst.",
                "",
                "§7Contracts stamped: §f" + profile.contractsDone(),
                union > 0 ? "§7Union Card: §a+" + Math.round(union * 100) + "% pay" : "§8Union Card skill adds pay.",
                "§7Every stamp earns §6Forge Reputation§7."), null);
        int[] slots = {20, 22, 24};
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            ForemanContracts.Contract contract = contracts.contract(player, slot);
            int index = slot;
            if (contract == null) {
                set(holder, slots[slot], Material.CLOCK, "§7Restocking…", lines(
                        "§7New contract in §f" + MineText.clock(contracts.restockSeconds(player, slot)),
                        "§8Otto's still sharpening a pencil."), null);
                continue;
            }
            int have = contracts.have(player, slot);
            boolean ready = have >= contract.amount();
            List<String> lore = new ArrayList<>();
            lore.add("§8Client: §7" + contract.client());
            lore.add("");
            lore.add("§7Wants: §f" + contract.want());
            lore.add("§7Progress: " + (ready ? "§a" : "§e") + Math.min(have, contract.amount()) + "§7/§f" + contract.amount()
                    + " " + MineText.cells(have / (double) Math.max(1, contract.amount()), ready ? "§a" : "§e"));
            lore.add("");
            lore.add("§7Pays: §6" + MineText.coins(contracts.payout(player, contract)) + " coins §8· §a+" + contract.xp() + " Mining XP");
            lore.add("");
            lore.add(ready ? "§a▶ Click to hand in" : contract.form().shift() ? "§8Go mine it — the ledger counts." : "§8Bring it and click.");
            ItemStack icon = set(holder, slots[slot], contract.icon(), (ready ? "§a" : "§6") + "Contract " + (slot + 1)
                    + " §8· " + contract.form().name().charAt(0) + contract.form().name().substring(1).toLowerCase(), lore,
                    (viewer, click) -> {
                        if (contracts.deliver(viewer, index)) {
                            openContracts(viewer);
                        }
                    });
            if (ready) {
                glow(icon);
            }
        }
        long free = contracts.freeRerollSeconds(player);
        set(holder, 40, Material.PAPER, "§eFresh Board", lines(
                free <= 0 ? "§aFree reroll ready." : "§7Free again in §f" + MineText.clock(free) + "§7, or §6400 coins§7.",
                "§8Replaces all three contracts."), (viewer, click) -> {
            if (contracts.reroll(viewer)) {
                openContracts(viewer);
            }
        });
        set(holder, 36, Material.LANTERN, "§8« Old Wick", lines("§7Map, depth and the crew."), (viewer, click) -> openWick(viewer));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ The Hearth

    public void openHearth(Player player) {
        Hearth hearth = isle.hearth();
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8The Hearth §7· Nan Coalbright");
        Hearth.Ration active = hearth.active(player);
        set(holder, 4, Material.CAMPFIRE, "§cThe Hearth", lines(
                "§7Nan cooks for miners and takes ore, not coin.",
                "§7One ration at a time. A new one replaces the old.",
                "§7Every buff is mining-only.",
                "",
                active == null ? "§8No ration active." : "§7Eating: §6" + active.display() + " §8· §f"
                        + MineText.clock(hearth.secondsLeft(player)) + " §7left"), null);
        int[] slots = {20, 21, 22, 23, 24};
        Hearth.Ration[] rations = Hearth.Ration.values();
        for (int i = 0; i < rations.length && i < slots.length; i++) {
            Hearth.Ration ration = rations[i];
            List<String> missing = hearth.missing(player, ration);
            List<String> lore = new ArrayList<>();
            for (String effect : ration.effectLines()) {
                lore.add("§8• " + effect);
            }
            lore.add("§7Lasts §f" + ration.minutes() + " min");
            lore.add("");
            lore.add("§7Nan wants:");
            ration.costs().forEach((ore, amount) -> lore.add("§8 - §f" + amount + " " + ore.display()));
            if (ration.needsSpecimen()) {
                lore.add("§8 - §dany Crystal Find specimen");
            }
            lore.add("");
            lore.add(missing.isEmpty() ? "§a▶ Click to cook" : "§cShort: " + String.join(", ", missing));
            ItemStack icon = set(holder, slots[i], ration.material(), "§6" + ration.display(), lore, (viewer, click) -> {
                if (hearth.cook(viewer, ration)) {
                    openHearth(viewer);
                }
            });
            if (active == ration) {
                glow(icon);
            }
        }
        set(holder, 36, Material.LANTERN, "§8« Old Wick", lines("§7Map, depth and the crew."), (viewer, click) -> openWick(viewer));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Assay Office

    public void openAssay(Player player, int page) {
        AssayLedger ledger = isle.ledger();
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, page == 0 ? "§8Assay Office §7· Collections & Mastery" : "§8Assay Office §7· Specimen Cabinet");
        MineProfiles.Profile profile = isle.profiles().of(player);
        int rank = ledger.rank(player);
        set(holder, 4, Material.AMETHYST_CLUSTER, "§dIlse Veyne's Ledger", lines(
                "§7Collector Rank §f" + MineText.roman(Math.min(10, rank)) + " §8· §7" + ledger.stars(player) + "/"
                        + AssayLedger.maxStars() + " stars",
                "§7+" + (int) ledger.collectorFortune(player) + " ore Fortune · Crystal Finds ×"
                        + MineText.num(ledger.crystalMultiplier(player)),
                "",
                "§7Cabinet: §f" + ledger.cabinetFilled(player) + "§7/§f" + AssayLedger.cabinetSize() + " specimens",
                "§7Crystal Finds cracked: §f" + profile.crystals(),
                profile.bestOre() == null ? "§8No specimen yet." : "§7Best: " + profile.bestGrade().colored() + " "
                        + profile.bestOre().color() + profile.bestOre().crystalName() + " §f" + MineText.carats(profile.bestCarat())),
                null);
        if (page == 0) {
            int index = 0;
            for (IsleOre ore : IsleOre.values()) {
                if (index >= INNER.length) {
                    break;
                }
                long mined = profile.mined(ore);
                int tier = profile.tier(ore);
                int reached = ledger.reached(player, ore);
                int claimed = profile.claimed(ore);
                int claimable = ledger.claimable(player, ore);
                List<String> lore = new ArrayList<>();
                lore.add("§6Mastery " + MineText.roman(tier) + " §8· §7" + MineText.coins(mined) + " mined here");
                lore.add(MineText.cells(OreMastery.fill(ore, mined), "§6") + (tier >= OreMastery.MAX_TIER ? " §6MAX"
                        : " §8next " + MineText.coins(OreMastery.threshold(ore, tier + 1))));
                if (ore != IsleOre.STONE) {
                    lore.add("§7+" + (int) OreMastery.fortuneAt(tier) + " " + ore.display() + " Fortune"
                            + (OreMastery.powerAt(tier) > 0 ? " · +" + (int) OreMastery.powerAt(tier) + " Power" : ""));
                }
                lore.add("");
                long collected = ledger.collected(player, ore);
                lore.add("§dCollection " + MineText.roman(reached) + " §8· §7" + MineText.coins(collected) + " lifetime");
                lore.add(reached >= AssayLedger.MAX_MILESTONE ? "§dComplete." : "§8Next at " + MineText.coins(AssayLedger.milestone(ore, reached + 1)));
                lore.add("§7Stars claimed: §f" + claimed + "§7/§f" + AssayLedger.MAX_MILESTONE);
                lore.add("");
                lore.add(claimable > 0 ? "§a▶ Click to claim " + claimable + " milestone" + (claimable == 1 ? "" : "s") : "§8Nothing to claim.");
                ItemStack icon = set(holder, INNER[index++], ore.resource() == null ? Material.STONE : ore.resource(),
                        ore.colored(), lore, (viewer, click) -> {
                            if (ledger.claim(viewer, ore) > 0) {
                                openAssay(viewer, 0);
                            }
                        });
                if (claimable > 0) {
                    glow(icon);
                }
            }
            int total = ledger.claimableTotal(player);
            set(holder, 49, total > 0 ? Material.EMERALD : Material.GRAY_DYE, total > 0 ? "§aClaim Everything (" + total + ")" : "§8Nothing to claim",
                    lines("§7Pays every milestone you've reached."), (viewer, click) -> {
                        if (ledger.claimAll(viewer) > 0) {
                            openAssay(viewer, 0);
                        }
                    });
            set(holder, 50, Material.GLASS, "§dSpecimen Cabinet »", lines("§7Every ore × every grade.", "§7Rows, crowns and columns pay."),
                    (viewer, click) -> openAssay(viewer, 1));
        } else {
            int index = 0;
            for (IsleOre ore : IsleOre.crystals()) {
                if (index >= INNER.length) {
                    break;
                }
                List<String> lore = new ArrayList<>();
                for (Grade grade : Grade.values()) {
                    lore.add((profile.hasSpecimen(ore, grade) ? "§a✔ " : "§8✘ ") + grade.colored()
                            + (grade == Grade.HEARTSTONE ? " §8(Amethyst Mine only)" : ""));
                }
                lore.add("");
                lore.add(ledger.rowComplete(player, ore) ? "§a✔ Row complete §7(+5 " + ore.display() + " Fortune)"
                        : "§7Rough + Flawless + Perfect completes the row.");
                lore.add(ledger.crowned(player, ore) ? "§6✦ Crowned §7(+3 " + ore.display() + " Mining Power)" : "§8A Heartstone crowns it.");
                MineProfiles.SpecimenRecord record = isle.profiles().records().get(ore);
                lore.add("");
                lore.add(record == null ? "§8Isle record: unclaimed" : "§7Isle record: §f" + MineText.carats(record.carats()) + " §8· §7" + record.name());
                ItemStack icon = set(holder, INNER[index++], ore.showcase(), ore.color() + ore.crystalName(), lore, null);
                if (ledger.crowned(player, ore)) {
                    glow(icon);
                }
            }
            set(holder, 48, Material.ARROW, "§7« Collections", lines(), (viewer, click) -> openAssay(viewer, 0));
            int count = MineItems.countSpecimens(player.getInventory(), isle.crystals(), null);
            set(holder, 50, Material.GOLD_INGOT, count > 0 ? "§6Sell " + count + " Specimen" + (count == 1 ? "" : "s") : "§8No specimens to sell",
                    lines("§7The cabinet keeps its record either way.", "§8Contracts may want one — check Otto first."),
                    (viewer, click) -> {
                        if (isle.contracts().sellSpecimens(viewer) > 0) {
                            openAssay(viewer, 1);
                        }
                    });
        }
        set(holder, 45, Material.LANTERN, "§8« Old Wick", lines("§7Map, depth and the crew."), (viewer, click) -> openWick(viewer));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ The Deep Forge

    public void openForge(Player player) {
        ForgeWorks forge = isle.forge();
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8The Deep Forge §7· Brann Emberlock");
        MineProfiles.Profile profile = isle.profiles().of(player);
        ForgeWorks.Rank rank = forge.rank(player);
        ForgeWorks.Rank next = rank.ordinal() + 1 < ForgeWorks.Rank.values().length ? ForgeWorks.Rank.values()[rank.ordinal() + 1] : null;
        long rep = forge.rep(player);
        double fill = next == null ? 1.0d : (rep - rank.from()) / (double) Math.max(1L, next.from() - rank.from());
        set(holder, 4, Material.ANVIL, "§6Forge Reputation §8· " + rank.colored(), lines(
                MineText.cells(fill, "§6") + " §7" + MineText.coins(rep) + (next == null ? " §6MAX" : " §8/ " + MineText.coins(next.from())
                        + " for " + next.colored()),
                "",
                "§7Earn it by forging Marks, stamping contracts,",
                "§7breaking Stonejaws and upgrading blueprint",
                "§7tools at the §6Forgehand §7upstairs.",
                "",
                "§7Blueprint upgrades here: §f" + profile.forged()), null);
        int[] slots = {19, 20, 21, 22, 23};
        ForgeWorks.Mark[] marks = ForgeWorks.Mark.values();
        for (int i = 0; i < marks.length; i++) {
            ForgeWorks.Mark mark = marks[i];
            int level = forge.level(player, mark);
            List<String> lore = new ArrayList<>();
            lore.add("§8" + mark.blurb());
            lore.add("§7Level §f" + MineText.roman(level) + "§7/§f" + MineText.roman(mark.max()) + " §8· " + mark.effect(level));
            lore.add("");
            if (level >= mark.max()) {
                lore.add("§6✦ Finished line.");
            } else {
                lore.add("§7Next: " + mark.effect(level + 1));
                lore.add("§7Brann needs:");
                lore.addAll(forge.costLines(player, forge.cost(mark, level + 1)));
                lore.add("");
                lore.add("§e▶ Click to forge " + MineText.roman(level + 1));
            }
            ItemStack icon = set(holder, slots[i], mark.icon(), mark.colored() + " " + MineText.roman(Math.max(level, 0)), lore,
                    (viewer, click) -> {
                        if (forge.forge(viewer, mark)) {
                            openForge(viewer);
                        }
                    });
            if (level > 0) {
                glow(icon);
            }
        }
        int[] toolSlots = {30, 32};
        ForgeWorks.Tool[] tools = ForgeWorks.Tool.values();
        for (int i = 0; i < tools.length; i++) {
            ForgeWorks.Tool tool = tools[i];
            ItemStack preview = forge.tool(tool, 1);
            ItemMeta meta = preview.getItemMeta();
            List<String> lore = new ArrayList<>(meta == null || meta.getLore() == null ? List.of() : meta.getLore());
            lore.add("");
            lore.add("§7Cost:");
            PlayerInventory bag = player.getInventory();
            tool.cost().forEach((material, amount) -> {
                int have = MineItems.countPlain(bag, material);
                lore.add((have >= amount ? "§a✔ " : "§c✘ ") + "§f" + amount + "× " + ForgeWorks.pretty(material.name()) + " §8(" + have + ")");
            });
            lore.add((rep >= tool.rank().from() ? "§a✔ " : "§c✘ ") + "§7Forge rank " + tool.rank().colored());
            lore.add("");
            lore.add("§e▶ Click to forge one");
            set(holder, toolSlots[i], tool.material(), tool.colored(), lore, (viewer, click) -> {
                if (forge.craft(viewer, tool)) {
                    openForge(viewer);
                }
            });
        }
        set(holder, 40, Material.SMITHING_TABLE, "§6The Forgehand §8(upstairs)", lines(
                "§7Blueprint tools (Vein Siphon and friends)",
                "§7climb Tier I → IV at the Eldervale Forgehand",
                "§7with Upgrade Stones II, III and IV.",
                "",
                "§7Each finished ritual: §6+40 rep per tier§7.",
                "§eClick §7to point your compass at the Landing."), (viewer, click) -> {
            viewer.closeInventory();
            MineDistricts.District landing = isle.districts().byId("landing");
            if (landing != null) {
                isle.compass().guide(viewer, landing);
            }
        });
        set(holder, 45, Material.LANTERN, "§8« Old Wick", lines("§7Map, depth and the crew."), (viewer, click) -> openWick(viewer));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ The Last Lamp

    public void openLastLamp(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 45, "§8Last Lamp §7· Hollis Underhill");
        MineProfiles.Profile profile = isle.profiles().of(player);
        List<String> board = new ArrayList<>();
        board.add("§7Your deepest strike: §f" + (profile.deepest() == Integer.MAX_VALUE ? "—" : "Y " + profile.deepest()));
        board.add("");
        board.add("§7Deepest on the isle right now:");
        List<Map.Entry<String, Integer>> deep = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            int y = isle.profiles().of(online).deepest();
            if (y != Integer.MAX_VALUE) {
                deep.add(Map.entry(online.getName(), y));
            }
        }
        deep.sort(Map.Entry.comparingByValue());
        for (int i = 0; i < Math.min(5, deep.size()); i++) {
            board.add("§e" + (i + 1) + ". §f" + deep.get(i).getKey() + " §8· §7Y " + deep.get(i).getValue());
        }
        if (deep.isEmpty()) {
            board.add("§8Nobody's been down. Yet.");
        }
        set(holder, 4, Material.SOUL_LANTERN, "§3The Last Lamp", board, null);
        List<String> critters = new ArrayList<>();
        for (MineCritters.Kind kind : MineCritters.Kind.values()) {
            critters.add(kind.colored() + " §8· §f" + profile.felled(kind.id()));
        }
        critters.add("");
        critters.add("§8Mites like heat. Moths like the grotto.");
        critters.add("§8Stonejaws wake after deep Seam Bursts.");
        critters.add("§8Shardlings live in the Amethyst Mine.");
        set(holder, 20, Material.SPIDER_EYE, "§7What lives down here", critters, null);
        int jaws = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isle.critters().isJaw(stack)) {
                jaws += stack.getAmount();
            }
        }
        int jawCount = jaws;
        ItemStack bounty = set(holder, 22, Material.BONE, "§4Stonejaw Bounty", lines(
                "§7Bring me a Stonejaw's Jaw.",
                "§7Each one: §62,500 coins §8· §6+60 Forge Reputation",
                "",
                jawCount > 0 ? "§a▶ Click to hand in " + jawCount : "§8You're not carrying a jaw."), (viewer, click) -> {
            int handed = 0;
            ItemStack[] contents = viewer.getInventory().getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (isle.critters().isJaw(contents[slot])) {
                    handed += contents[slot].getAmount();
                    contents[slot] = null;
                }
            }
            if (handed == 0) {
                return;
            }
            viewer.getInventory().setStorageContents(contents);
            MineSkills.coins(viewer, 2_500L * handed);
            isle.forge().addRep(viewer, 60L * handed, "Stonejaw bounty");
            isle.cast().say(viewer, MineRole.HERMIT, "That's the one. Heavy as a sin. §6+" + MineText.coins(2_500L * handed) + " coins");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 0.8f);
            openLastLamp(viewer);
        });
        if (jawCount > 0) {
            glow(bounty);
        }
        int[] toolSlots = {24, 25};
        ForgeWorks.Tool[] tools = ForgeWorks.Tool.values();
        for (int i = 0; i < tools.length; i++) {
            ForgeWorks.Tool tool = tools[i];
            List<String> lore = new ArrayList<>();
            lore.add("§7Hollis trades the forge's consumables");
            lore.add("§7for the same ore, no rank needed.");
            lore.add("");
            tool.cost().forEach((material, amount) -> lore.add("§8 - §f" + amount + "× " + ForgeWorks.pretty(material.name())));
            lore.add("");
            lore.add("§e▶ Click to trade");
            set(holder, toolSlots[i], tool.material(), tool.colored(), lore, (viewer, click) -> {
                PlayerInventory bag = viewer.getInventory();
                for (Map.Entry<Material, Integer> entry : tool.cost().entrySet()) {
                    if (MineItems.countPlain(bag, entry.getKey()) < entry.getValue()) {
                        MineText.bar(viewer, "§cShort on " + ForgeWorks.pretty(entry.getKey().name()) + ".");
                        return;
                    }
                }
                tool.cost().forEach((material, amount) -> MineItems.takePlain(bag, material, amount));
                MineSkills.give(viewer, isle.forge().tool(tool, 1));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 0.7f, 1.0f);
            });
        }
        set(holder, 36, Material.LANTERN, "§8« Old Wick", lines("§7Map, depth and the crew."), (viewer, click) -> openWick(viewer));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Journal

    public void openJournal(Player player) {
        Holder holder = new Holder();
        Inventory inventory = create(holder, 54, "§8Mining Journal §7· " + player.getName());
        MineProfiles.Profile profile = isle.profiles().of(player);
        List<String> card = new ArrayList<>();
        card.add("§7Best Mining skill level: §f" + MineSkills.level(player));
        String credit = MineSkills.credit(player);
        card.add(credit == null ? "§8No Mining skill equipped." : "§7Focus: " + credit);
        card.add("");
        card.add("§7Places found: §f" + isle.compass().countKnown(profile) + "§7/§f" + isle.districts().size()
                + (profile.surveyor() ? " §6✦ Cartographer" : ""));
        card.add("§7Deepest strike: §f" + (profile.deepest() == Integer.MAX_VALUE ? "—" : "Y " + profile.deepest()));
        card.add("§7Shift streak: §f" + profile.streak() + " §8(best " + profile.bestStreak() + ") §8· §e+"
                + isle.streakFortune(player) + " Fortune");
        card.add("§7Contracts stamped: §f" + profile.contractsDone());
        card.add("§7Crystal Finds: §f" + profile.crystals() + " §8· §7cabinet " + isle.ledger().cabinetFilled(player)
                + "/" + AssayLedger.cabinetSize());
        card.add("§7Forge: " + isle.forge().rank(player).colored() + " §8(" + MineText.coins(profile.forgeRep()) + " rep)");
        card.add("§7Critters felled: §f" + profile.felledTotal());
        card.add("§7Amethyst Mine: §dbest Resonance " + (int) profile.resonanceBest() + " §8· §7Geode Hearts " + profile.geodeHearts());
        card.add("");
        card.add("§eClick §7for your Mining skills.");
        set(holder, 4, Material.PLAYER_HEAD, "§6" + player.getName() + " §7· Miner's Card", card, (viewer, click) -> {
            viewer.closeInventory();
            viewer.performCommand("skills mining");
        });
        inventory.setItem(4, head(player, inventory.getItem(4)));
        set(holder, 19, Material.NOTE_BLOCK, "§6⚒ Strike Rhythm", lines(
                "§7Keep mining without a pause. Rarer ore",
                "§7adds more. Idling drains it.",
                "§aSteady Pick §8· §eIn the Seam §8· §6Anvil Chorus",
                "§7+ore Fortune, Spread and faster breaks.",
                "",
                "§7Same ore back to back builds a §aSeam Chain§7.",
                "§7Fill it and the seam bursts (extra ore).",
                "§8Deep bursts can wake a Stonejaw."), null);
        set(holder, 20, Material.AMETHYST_CLUSTER, "§d✦ Crystal Finds", lines(
                "§7Any ore strike can push a geode out of",
                "§7the rock. Hit it with a pickaxe to crack it.",
                "§7Rough · Flawless · Perfect · §6Heartstone",
                "",
                "§7Depth, Geode Nose, the Lamp Core, Ember Hour",
                "§7and the moths all improve your odds."), null);
        set(holder, 21, Material.EXPERIENCE_BOTTLE, "§6Ore Mastery", lines(
                "§7Every ore family levels on its own, I to VII.",
                "§7Each tier adds permanent Fortune for that ore.",
                "§7IV and VII add Mining Power for that ore.",
                "",
                "§7Eldervale and the Amethyst Mine count double.",
                "§eClick §7for the Assay Office ledger."), (viewer, click) -> openAssay(viewer, 0));
        set(holder, 22, Material.BELL, "§eMine Events", lines(
                isle.events().statusLine(),
                "",
                "§eRich Vein §8· §7one district pays double",
                "§cEmber Hour §8· §7Crystal Finds ×4",
                "§fTremor §8· §7rubble to clear, shore it up",
                "§aTroll Run §8· §7Ore Trolls crawl out"), null);
        set(holder, 23, Material.ANVIL, "§6The Deep Forge", lines(
                "§7Forge Marks: permanent upgrades on you.",
                "§7Rank: " + isle.forge().rank(player).colored(),
                "",
                "§eClick §7to open Brann's board."), (viewer, click) -> openForge(viewer));
        set(holder, 24, Material.WRITABLE_BOOK, "§6Contracts", lines(
                "§7Three jobs at a time from the Contract Office.",
                "§eClick §7to open Otto's board."), (viewer, click) -> openContracts(viewer));
        set(holder, 25, Material.BAKED_POTATO, "§cThe Hearth", lines(
                "§7Mining-only rations, paid in ore.",
                "§eClick §7to open Nan's kitchen."), (viewer, click) -> openHearth(viewer));
        set(holder, 29, Material.RECOVERY_COMPASS, "§3Depth", lines(
                "§7Four bands under the isle, each richer:",
                "§aSurface §8· §eUpper Galleries §8· §6Deep Works §8· §cThe Undercroft",
                "",
                "§7Down there: cave-ins (spread your digging),",
                "§7heat in the Emberseam, the dark in the Undercroft."), (viewer, click) -> openWick(viewer));
        set(holder, 30, Material.SPIDER_EYE, "§7Critters", lines(
                "§6Cinder Mites §8· §bGloam Moths §8· §4Stonejaw §8· §dShardlings",
                "§8Hollis keeps the tally at the Last Lamp."), (viewer, click) -> openLastLamp(viewer));
        set(holder, 31, Material.AMETHYST_SHARD, "§d◆ The Amethyst Mine", lines(
                "§7/amethyst · resets daily · geodes grow deeper",
                "§7Build §dResonance §7for Fortune and finds.",
                "§7Let it hit 100 and the geode shatters,",
                "§7sometimes leaving a §dGeode Heart§7.",
                "§6Heartstones §7grow only here."), null);
        set(holder, 32, Material.CLOCK, "§6Shift Streak", lines(
                "§7Mine §f" + isle.plugin().getConfig().getInt("mine-isle.shift-blocks", 250) + " §7blocks on the isle in a day",
                "§7to log a shift. Consecutive days stack",
                "§7+1 ore Fortune each (max +7).",
                "",
                "§7Current: §f" + profile.streak() + " §8· §e+" + isle.streakFortune(player) + " Fortune"), null);
        set(holder, 33, Material.LANTERN, "§eOld Wick", lines("§7Map, compass and the crew tour."), (viewer, click) -> openWick(viewer));
        set(holder, 49, Material.COMPASS, profile.compassOff() ? "§7Compass hints: §cOFF" : "§7Compass hints: §aON",
                lines("§7First-visit tour hints and depth cards."), (viewer, click) -> {
                    profile.compassOff = !profile.compassOff;
                    isle.profiles().markDirty();
                    openJournal(viewer);
                });
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ helpers

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

    private static ItemStack head(Player player, ItemStack template) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skull && template != null && template.getItemMeta() != null) {
            skull.setOwningPlayer(player);
            skull.setDisplayName(template.getItemMeta().getDisplayName());
            skull.setLore(template.getItemMeta().getLore());
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
