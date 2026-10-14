package de.aetherion.hub.origin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Origin's GUIs: the Origin Journal ({@code /origin}), its pages (landmarks, glowcaps, bells, vistas, skyways,
 * townsfolk, settings), the glowcap travel menu, one board per townsperson and the DEV hub.
 * Pure Hub-side inventories with their own holder: nothing here touches the Quests talk stack.
 */
public final class OriginMenus implements Listener {

    /** Our inventory holder: page id + slot → action. */
    public static final class Holder implements InventoryHolder {
        private final String page;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        Holder(String page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public String page() {
            return page;
        }
    }

    private static final int[] DISTRICT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final String[][] TITLES = {
            {"wayfarer", "§6Wayfarer", "every district"},
            {"cartographer", "§6Origin Cartographer", "every landmark"},
            {"glowcap_circle", "§9Glowcap Circle", "every glowcap"},
            {"bellwright", "§eBellwright", "all seven bells"},
            {"stargazer", "§dStargazer", "every vista"},
            {"skyrider", "§bSkyrider", "every skyway, glide and updraft"},
            {"tour_done", "§6Tour complete", "Orla's tour"},
            {"wishmaker", "§dWishmaker", "seven wishes on falling stars"},
            {"curious", "§dCurious", "10 curiosities"},
            {"keen_eyed", "§dKeen-Eyed", "25 curiosities"},
            {"lorekeeper", "§5Lorekeeper", "45 curiosities"},
            {"small_wonders", "§d§lKeeper of Small Wonders", "every curiosity"},
            {"taleteller", "§6Taleteller", "every storytelling chest"}
    };
    private static final int PAGE = 45;

    private final OriginIsle isle;

    OriginMenus(OriginIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ building blocks

    private static Component plain(String legacy) {
        return OriginText.legacy(legacy).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack item(Material material, String name, List<String> lore, boolean glint) {
        ItemStack stack = new ItemStack(material == null || !material.isItem() ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(plain(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plain(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Splits a lore line at word boundaries, carrying the leading colour code onto every piece. */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String color = text.startsWith("§") && text.length() > 1 ? text.substring(0, 2) : "§7";
        String body = text.startsWith("§") && text.length() > 1 ? text.substring(2) : text;
        StringBuilder line = new StringBuilder();
        for (String word : body.split(" ")) {
            int visible = line.toString().replaceAll("§.", "").length();
            if (visible > 0 && visible + 1 + word.replaceAll("§.", "").length() > width) {
                out.add(color + line);
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            out.add(color + line);
        }
        return out;
    }

    private static void pager(Holder holder, String page, int current, int pages) {
        if (current > 0) {
            put(holder, 45, item(Material.ARROW, "§7← Page " + current, List.of(), false), "page:" + page + ":" + (current - 1));
        }
        if (current + 1 < pages) {
            put(holder, 53, item(Material.ARROW, "§7Page " + (current + 2) + " →", List.of(), false), "page:" + page + ":" + (current + 1));
        }
        if (pages > 1) {
            put(holder, 47, item(Material.PAPER, "§7Page §f" + (current + 1) + "§8/§f" + pages, List.of(), false), null);
        }
    }

    private static Holder holder(String page, int size, String title) {
        Holder holder = new Holder(page);
        holder.inventory = Bukkit.createInventory(holder, size, plain(title));
        return holder;
    }

    private static void put(Holder holder, int slot, ItemStack stack, String action) {
        holder.inventory.setItem(slot, stack);
        if (action != null) {
            holder.actions.put(slot, action);
        }
    }

    private static void fill(Holder holder, int from, int to) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = from; i <= to; i++) {
            if (holder.inventory.getItem(i) == null) {
                holder.inventory.setItem(i, pane);
            }
        }
    }

    private static void back(Holder holder, int slot) {
        put(holder, slot, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
    }

    private String where(Player player, double[] at) {
        Location to = isle.config().location(at);
        if (to == null || to.getWorld() != player.getWorld()) {
            return "§7far away";
        }
        return "§7" + OriginText.distance(to.distance(player.getLocation())) + " " + OriginText.compass(player.getLocation(), to);
    }

    private static long count(java.util.Set<String> keys, java.util.Set<String> have) {
        return keys.stream().filter(have::contains).count();
    }

    // ------------------------------------------------------------------ journal

    public void openJournal(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder("journal", 54, "§6✦ Origin Journal");

        List<String> head = new ArrayList<>();
        head.add("§7Districts §f" + count(c.districts().keySet(), p.districts) + "§8/§f" + c.districts().size());
        head.add("§7Landmarks §f" + count(c.landmarks().keySet(), p.landmarks) + "§8/§f" + c.landmarks().size());
        head.add("§7Glowcaps §f" + count(c.waystones().keySet(), p.waystones) + "§8/§f" + c.waystones().size());
        head.add("§7Bells §f" + count(c.bells().keySet(), p.bells) + "§8/§f" + c.bells().size());
        head.add("§7Vistas §f" + count(c.vistas().keySet(), p.vistas) + "§8/§f" + c.vistas().size());
        head.add("§7Skyways §f" + isle.pads().ridden(p) + "§8/§f" + isle.pads().allRides().size());
        head.add("§7Curiosities §f" + isle.curiosities().found(p) + "§8/§f" + isle.curiosities().all().size());
        head.add("§7Tales §f" + isle.tales().found(p) + "§8/§f" + isle.tales().all().size());
        head.add("§7Wishes §f" + p.wishes);
        head.add("§7Coins found on Origin §6" + OriginText.coins(p.coinsEarned));
        head.add("");
        int titles = 0;
        for (String[] t : TITLES) {
            if (p.flag(t[0])) {
                head.add("§a✔ " + t[1]);
                titles++;
            }
        }
        if (titles == 0) {
            head.add("§8No titles yet — walk, ring, ride.");
        }
        String district = isle.compass().districtOf(player);
        OriginConfig.District here = c.district(district);
        put(h, 4, item(Material.WRITABLE_BOOK, "§6§lOrigin Journal" + (here == null ? "" : " §8· " + here.colored()), head, titles > 0), null);

        int i = 0;
        for (OriginConfig.District d : c.districts().values()) {
            if (i >= DISTRICT_SLOTS.length) {
                break;
            }
            int slot = DISTRICT_SLOTS[i++];
            if (p.districts.contains(d.id())) {
                long lmTotal = c.landmarks().values().stream().filter(l -> l.district().equals(d.id())).count();
                long lmFound = c.landmarks().values().stream().filter(l -> l.district().equals(d.id()) && p.landmarks.contains(l.id())).count();
                List<String> lore = new ArrayList<>();
                lore.add("§7" + d.tagline());
                lore.add("");
                lore.add("§7Landmarks §f" + lmFound + "§8/§f" + lmTotal);
                long cuTotal = isle.curiosities().inDistrict(d.id());
                if (cuTotal > 0) {
                    lore.add("§7Curiosities §f" + isle.curiosities().foundInDistrict(p, d.id()) + "§8/§f" + cuTotal);
                }
                if (d.anchor() != null) {
                    lore.add(where(player, d.anchor()));
                }
                lore.add("");
                lore.add("§e▸ Click §7to point the way");
                put(h, slot, item(d.icon(), d.color() + "§l" + d.name(), lore, lmTotal > 0 && lmFound == lmTotal), "go:district:" + d.id());
            } else {
                List<String> lore = new ArrayList<>();
                lore.add("§8Undiscovered.");
                if (d.anchor() != null) {
                    lore.add("§7Somewhere " + where(player, d.anchor()).substring(2) + " §7of you.");
                }
                put(h, slot, item(Material.GRAY_STAINED_GLASS_PANE, "§8???", lore, false), "hint:" + d.id());
            }
        }

        put(h, 36, item(Material.MAP, "§e§lLandmarks", List.of("§7" + count(c.landmarks().keySet(), p.landmarks) + "/" + c.landmarks().size() + " found", "§e▸ Open"), false), "page:landmarks");
        int curioFound = isle.curiosities().found(p);
        put(h, 37, item(Material.AMETHYST_SHARD, "§d§lCuriosities", List.of("§7" + curioFound + "/" + isle.curiosities().all().size() + " found",
                "§8Small hidden things. Look up, look", "§8down, listen, touch.", "§e▸ Open"), curioFound > 0), "page:curios");
        put(h, 44, item(Material.CHEST, "§6§lTales", List.of("§7" + isle.tales().found(p) + "/" + isle.tales().all().size() + " opened",
                "§8Dressed chests with a story inside.", "§e▸ Open"), false), "page:tales");
        put(h, 38, item(Material.SHROOMLIGHT, "§9§lGlowcaps", List.of("§7" + count(c.waystones().keySet(), p.waystones) + "/" + c.waystones().size() + " attuned", "§e▸ Open"), false), "page:waystones");
        put(h, 39, item(Material.BELL, "§e§lThe Seven Bells", List.of("§7" + count(c.bells().keySet(), p.bells) + "/" + c.bells().size() + " rung", "§e▸ Open"), false), "page:bells");
        put(h, 40, item(Material.SPYGLASS, "§d§lVistas", List.of("§7" + count(c.vistas().keySet(), p.vistas) + "/" + c.vistas().size() + " seen", "§e▸ Open"), false), "page:vistas");
        put(h, 41, item(Material.FEATHER, "§b§lSkyways", List.of("§7" + isle.pads().ridden(p) + "/" + isle.pads().allRides().size() + " ridden", "§e▸ Open"), false), "page:rides");
        put(h, 42, item(Material.VILLAGER_SPAWN_EGG, "§6§lTownsfolk", List.of("§7" + count(roleIds(), p.met) + "/" + OriginRole.values().length + " met", "§e▸ Open"), false), "page:cast");
        if (isle.compass().touring(player)) {
            put(h, 43, item(Material.COMPASS, "§6§lTour §8(" + (p.tourStep + 1) + "/" + OriginCompass.TOUR.size() + ")",
                    List.of(OriginCompass.TOUR.get(p.tourStep).line(), "", "§e▸ Click §7to point the way again", "§8Right-click: stop the tour"), true), "tour:start");
        } else {
            put(h, 43, item(Material.COMPASS, "§6§lThe Origin Tour",
                    List.of(p.flag("tour_done") ? "§a✔ Done — take it again any time." : "§7Eight stops, the cast and the sky.", "", "§e▸ Click §7to start"), false), "tour:start");
        }
        OriginEvents.Kind moment = isle.events().active();
        if (moment != null) {
            put(h, 47, item(Material.FIREWORK_STAR, moment.title(), List.of(moment.line(), "§7" + isle.events().secondsLeft() + "s left"), true), null);
        } else {
            int next = isle.events().minutesToNext();
            put(h, 47, item(Material.CLOCK, "§7Island moments", List.of("§7Lanterns, aurora, starfall, fog…",
                    next >= 0 ? "§7Next in about §f" + Math.max(1, next) + " min" : "§7Soon."), false), null);
        }
        put(h, 45, item(Material.COMPARATOR, "§7Settings", List.of("§7Ambience " + (p.ambience ? "§aon" : "§coff"),
                "§7Particles " + (p.particles ? "§aon" : "§coff"), "§e▸ Open"), false), "page:settings");
        if (isle.compass().pointing(player)) {
            put(h, 53, item(Material.BARRIER, "§cStop pointing", List.of("§7Now: §f" + isle.compass().targetName(player)), false), "stop");
        }
        put(h, 49, item(Material.OAK_DOOR, "§7Close", List.of(), false), "close");
        fill(h, 0, 53);
        player.openInventory(h.inventory);
    }

    private static java.util.Set<String> roleIds() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (OriginRole role : OriginRole.values()) {
            out.add(role.id());
        }
        return out;
    }

    private void openLandmarks(Player player, int page) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        List<OriginConfig.Landmark> all = new ArrayList<>(c.landmarks().values());
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Holder h = holder("landmarks", 54, "§e✧ Landmarks §8· §7" + count(c.landmarks().keySet(), p.landmarks) + "/" + c.landmarks().size()
                + (pages > 1 ? " §8(" + (current + 1) + "/" + pages + ")" : ""));
        int slot = 0;
        for (OriginConfig.Landmark l : all.subList(current * PAGE, Math.min(all.size(), (current + 1) * PAGE))) {
            OriginConfig.District d = c.district(l.district());
            String dn = d == null ? "" : d.colored();
            if (p.landmarks.contains(l.id())) {
                List<String> lore = new ArrayList<>(wrap("§7" + l.blurb(), 36));
                lore.add(dn);
                lore.add(where(player, l.at()));
                lore.add("");
                lore.add("§e▸ Click §7to point the way");
                put(h, slot, item(l.icon(), (d == null ? "§e" : d.color()) + l.name(), lore, false), "go:landmark:" + l.id());
            } else {
                boolean districtKnown = d != null && p.districts.contains(d.id());
                put(h, slot, item(Material.GRAY_DYE, "§8???", List.of(districtKnown ? "§7Somewhere in " + dn : "§8In a district you haven't found.",
                        "§e▸ Click §7for a hint"), false), "lhint:" + l.id());
            }
            slot++;
        }
        pager(h, "landmarks", current, pages);
        back(h, 49);
        fill(h, 45, 53);
        player.openInventory(h.inventory);
    }

    private void openCurios(Player player, int page) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        OriginCuriosities cu = isle.curiosities();
        List<OriginCuriosities.Curio> all = new ArrayList<>(cu.all().values());
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        int found = cu.found(p);
        Holder h = holder("curios", 54, "§d✧ Curiosities §8· §7" + found + "/" + all.size() + (pages > 1 ? " §8(" + (current + 1) + "/" + pages + ")" : ""));
        int slot = 0;
        for (OriginCuriosities.Curio k : all.subList(current * PAGE, Math.min(all.size(), (current + 1) * PAGE))) {
            OriginConfig.District d = c.district(k.district());
            String dn = d == null ? "§7Origin" : d.colored();
            if (p.curiosities.contains(k.id())) {
                List<String> lore = new ArrayList<>(wrap("§7" + k.note(), 36));
                lore.add(dn);
                lore.add(where(player, k.focus()));
                lore.add("");
                lore.add("§e▸ Click §7to point the way back");
                put(h, slot, item(k.icon(), (d == null ? "§d" : d.color()) + k.name(), lore, false), "go:curio:" + k.id());
            } else if (d != null && p.districts.contains(d.id())) {
                List<String> lore = new ArrayList<>(wrap("§7“" + k.clue() + "”", 36));
                lore.add("§7In " + dn);
                String how = switch (k.kind()) {
                    case STEP -> "Go and stand there.";
                    case LOOK -> "You'll need to look at it.";
                    case TOUCH -> "Touch it.";
                    case LISTEN -> "Sneak, keep still and listen.";
                };
                String when = switch (k.when().toLowerCase(java.util.Locale.ROOT)) {
                    case "night" -> " After dark.";
                    case "day" -> " By day.";
                    case "dusk" -> " Only at dusk.";
                    case "dawn" -> " Only at dawn.";
                    case "rain" -> " Only in the rain.";
                    default -> "";
                };
                lore.add("§8" + how + when);
                lore.add("");
                lore.add("§e▸ Click §7for a direction");
                put(h, slot, item(Material.GRAY_DYE, "§8???", lore, false), "chint:" + k.id());
            } else {
                put(h, slot, item(Material.GRAY_STAINED_GLASS_PANE, "§8???", List.of("§8In a district you haven't found."), false), null);
            }
            slot++;
        }
        List<String> titles = new ArrayList<>();
        OriginCuriosities.Milestone next = null;
        for (OriginCuriosities.Milestone m : cu.milestones()) {
            boolean have = p.flag(m.flag());
            int need = m.count() <= 0 ? all.size() : m.count();
            titles.add((have ? "§a✔ " : "§8○ ") + m.title() + " §8· §7" + need);
            if (!have && next == null) {
                next = m;
            }
        }
        if (next != null) {
            int need = next.count() <= 0 ? all.size() : next.count();
            titles.add("");
            titles.add("§7Next: " + next.title() + " §7in §f" + Math.max(0, need - found));
        }
        put(h, 51, item(Material.AMETHYST_CLUSTER, "§dTitles", titles, next == null && !titles.isEmpty()), null);
        pager(h, "curios", current, pages);
        back(h, 49);
        fill(h, 45, 53);
        player.openInventory(h.inventory);
    }

    private void openTales(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        OriginTales tl = isle.tales();
        Holder h = holder("tales", 54, "§6✉ Tales §8· §7" + tl.found(p) + "/" + tl.all().size());
        int slot = 0;
        for (OriginTales.Tale t : tl.all().values()) {
            if (slot >= PAGE) {
                break;
            }
            OriginConfig.District d = c.district(t.district());
            String dn = d == null ? "§7Origin" : d.colored();
            double[] at = {t.at()[0] + 0.5d, t.at()[1], t.at()[2] + 0.5d};
            if (p.tales.contains(t.id())) {
                List<String> lore = new ArrayList<>();
                lore.add("§7by §f" + t.author());
                lore.add(dn);
                lore.add(where(player, at));
                lore.add("");
                lore.add("§e▸ Click §7to read again");
                lore.add("§8Right-click: point the way");
                put(h, slot, item(Material.WRITTEN_BOOK, (d == null ? "§6" : d.color()) + t.name(), lore, false), "tale:" + t.id());
            } else if (d != null && p.districts.contains(d.id()) && tl.present(t)) {
                List<String> lore = new ArrayList<>(wrap("§7“" + t.clue() + "”", 36));
                lore.add("§7In " + dn);
                put(h, slot, item(Material.GRAY_DYE, "§8???", lore, false), null);
            } else {
                put(h, slot, item(Material.GRAY_STAINED_GLASS_PANE, "§8???",
                        List.of(tl.present(t) ? "§8In a district you haven't found." : "§8Not in the world yet."), false), null);
            }
            slot++;
        }
        back(h, 49);
        fill(h, 45, 53);
        player.openInventory(h.inventory);
    }

    /** Glowcap travel. {@code fromId} = the glowcap you clicked (null when opened from the journal / the Tender). */
    public void openWaystones(Player player, String fromId) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder(fromId == null ? "waystones" : "waystones:" + fromId, 36,
                "§9✦ Glowcaps §8· §7" + count(c.waystones().keySet(), p.waystones) + "/" + c.waystones().size() + " attuned");
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (OriginConfig.Waystone w : c.waystones().values()) {
            if (i >= slots.length) {
                break;
            }
            int slot = slots[i++];
            OriginConfig.District d = c.district(w.district());
            boolean here = w.id().equals(fromId);
            if (p.waystones.contains(w.id())) {
                List<String> lore = new ArrayList<>();
                lore.add("§7" + w.blurb());
                lore.add(d == null ? "" : d.colored());
                lore.add(where(player, w.stand()));
                lore.add("");
                lore.add(here ? "§a● You are here" : (fromId == null ? "§e▸ Click §7to point the way" : "§e▸ Click §7to travel"));
                put(h, slot, item(w.sprout() ? Material.GLOW_LICHEN : Material.SHROOMLIGHT, "§9" + w.name(), lore, here),
                        here ? null : (fromId == null ? "go:waystone:" + w.id() : "travel:" + w.id()));
            } else {
                put(h, slot, item(Material.GRAY_DYE, "§8" + w.name(), List.of("§7Not attuned — walk up to it once.",
                        d == null ? "" : d.colored(), where(player, w.stand()), "", "§e▸ Click §7to point the way"), false), "go:waystone:" + w.id());
            }
        }
        if (fromId == null) {
            put(h, 31, item(Material.ARROW, "§7← Journal", List.of("§8Right-click a glowcap in the world to travel."), false), "page:journal");
        } else {
            put(h, 31, item(Material.WRITABLE_BOOK, "§6Origin Journal", List.of(), false), "page:journal");
        }
        fill(h, 0, 35);
        player.openInventory(h.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private void openBells(Player player, boolean board) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder(board ? "board:bellkeeper" : "bells", 36, "§e♪ The Seven Bells §8· §7" + count(c.bells().keySet(), p.bells) + "/" + c.bells().size());
        int slot = 10;
        for (OriginConfig.Bell b : c.bells().values()) {
            boolean rung = p.bells.contains(b.id());
            double[] at = {b.at()[0] + 0.5, b.at()[1], b.at()[2] + 0.5};
            put(h, slot, item(rung ? Material.BELL : Material.GRAY_DYE, (rung ? "§e" : "§8") + b.name(),
                    List.of(rung ? "§a✔ Rung" : "§7Not rung yet", where(player, at),
                            b.id().equals("tower_high") ? "§8Out of reach. Arrows ring bells too." : "", "", "§e▸ Click §7to point the way"), false),
                    "go:bell:" + b.id());
            slot++;
        }
        if (p.flag("bellwright")) {
            put(h, 22, item(Material.NOTE_BLOCK, "§e§lHear the hymn", List.of("§7All seven notes, in order."), true), "hymn");
        } else {
            put(h, 22, item(Material.NOTE_BLOCK, "§7The old hymn", List.of("§7Ring all seven bells to hear it whole."), false), null);
        }
        put(h, 31, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
        fill(h, 0, 35);
        player.openInventory(h.inventory);
    }

    private void openVistas(Player player, boolean board) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder(board ? "board:stargazer" : "vistas", 36, "§d✦ Vistas §8· §7" + count(c.vistas().keySet(), p.vistas) + "/" + c.vistas().size());
        int slot = 11;
        for (OriginConfig.Vista v : c.vistas().values()) {
            boolean seen = p.vistas.contains(v.id());
            put(h, slot, item(seen ? Material.SPYGLASS : Material.GRAY_DYE, (seen ? "§f" : "§8") + v.name(),
                    List.of(seen ? "§a✔ Seen" : "§7Not yet", "§7Height §f" + (int) v.at()[1], where(player, v.at()), "", "§e▸ Click §7to point the way"), false),
                    "go:vista:" + v.id());
            slot++;
        }
        OriginEvents.Kind moment = isle.events().active();
        put(h, 20, item(Material.NETHER_STAR, "§dWishes §f" + p.wishes, List.of("§7During a Starfall, sneak and look up.",
                moment == OriginEvents.Kind.STARFALL ? "§d§lThe stars are falling now!" : "§7One wish per Starfall."), moment == OriginEvents.Kind.STARFALL), null);
        if (moment != null) {
            put(h, 24, item(Material.FIREWORK_STAR, moment.title(), List.of(moment.line(), "§7" + isle.events().secondsLeft() + "s left"), true), null);
        } else {
            int next = isle.events().minutesToNext();
            put(h, 24, item(Material.CLOCK, "§7The sky tonight", List.of(next >= 0 ? "§7Something in about §f" + Math.max(1, next) + " min" : "§7Keep looking up."), false), null);
        }
        put(h, 31, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
        fill(h, 0, 35);
        player.openInventory(h.inventory);
    }

    private void openRides(Player player, boolean board) {
        OriginProfile p = isle.profiles().get(player);
        Holder h = holder(board ? "board:keeper" : "rides", 45, "§b⇗ Skyways §8· §7" + isle.pads().ridden(p) + "/" + isle.pads().allRides().size());
        int slot = 10;
        java.util.Set<String> counted = isle.pads().allRides();
        for (Object[] r : isle.pads().catalog()) {
            if (slot == 17 || slot == 26) {
                slot += 2;
            }
            if (slot > 34) {
                break;
            }
            String key = (String) r[0];
            boolean ridden = p.rides.contains(key);
            String to = (String) r[2];
            double[] at = {(double) r[3], (double) r[4], (double) r[5]};
            Material icon = key.startsWith("updraft:") ? Material.WIND_CHARGE : (key.startsWith("flight:") ? Material.ELYTRA : Material.SLIME_BLOCK);
            put(h, slot, item(icon, (ridden ? "§b" : "§7") + r[1],
                    List.of(to.equals("up") ? "§7Straight up." : (to.isBlank() ? "" : "§7To §f" + to),
                            ridden ? "§a✔ Ridden" : (counted.contains(key) ? "§7Not ridden yet" : "§8Doesn't count for Skyrider"),
                            where(player, at), "", "§e▸ Click §7to point the way"), false),
                    "ride:" + key);
            slot++;
        }
        put(h, 40, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
        fill(h, 0, 44);
        player.openInventory(h.inventory);
    }

    private void openCast(Player player) {
        OriginProfile p = isle.profiles().get(player);
        Holder h = holder("cast", 27, "§6Townsfolk of Origin");
        int slot = 11;
        for (OriginRole role : OriginRole.values()) {
            boolean met = p.met.contains(role.id());
            Location at = isle.cast().whereabouts(role);
            String w = at == null || at.getWorld() != player.getWorld() ? "§8not placed"
                    : "§7" + OriginText.distance(at.distance(player.getLocation())) + " " + OriginText.compass(player.getLocation(), at);
            put(h, slot, item(role.icon(), role.color() + role.display(), List.of("§7" + role.title(), "§8" + role.role(),
                    met ? "§a✔ Met" : "§7Not met yet", w, "", "§e▸ Click §7to point the way"), false), "go:cast:" + role.id());
            slot++;
        }
        put(h, 22, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
        fill(h, 0, 26);
        player.openInventory(h.inventory);
    }

    private void openSettings(Player player) {
        OriginProfile p = isle.profiles().get(player);
        Holder h = holder("settings", 27, "§7Origin · Settings");
        put(h, 11, item(p.ambience ? Material.NOTE_BLOCK : Material.GRAY_DYE, "§fAmbience " + (p.ambience ? "§aon" : "§coff"),
                List.of("§7District soundscapes, bell tolls,", "§7island moment announcements.", "", "§e▸ Click §7to toggle"), p.ambience), "toggle:ambience");
        put(h, 13, item(p.particles ? Material.GLOWSTONE_DUST : Material.GUNPOWDER, "§fParticles " + (p.particles ? "§aon" : "§coff"),
                List.of("§7Pad columns, fireflies, the aurora,", "§7falling stars and fog.", "", "§e▸ Click §7to toggle"), p.particles), "toggle:particles");
        put(h, 15, item(Material.BARRIER, "§cStop pointing", List.of("§7Clears the wayfinder arrow."), false), "stop");
        put(h, 22, item(Material.ARROW, "§7← Journal", List.of(), false), "page:journal");
        fill(h, 0, 26);
        player.openInventory(h.inventory);
    }

    // ------------------------------------------------------------------ townsfolk boards

    public void openBoard(Player player, OriginRole role) {
        switch (role) {
            case GUIDE -> openGuide(player);
            case KEEPER -> openRides(player, true);
            case BELLKEEPER -> openBells(player, true);
            case TENDER -> openWaystones(player, null);
            case STARGAZER -> openVistas(player, true);
        }
    }

    private void openGuide(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder("board:guide", 27, "§6Orla Vane §8· §7Origin Journal");
        put(h, 11, item(Material.WRITABLE_BOOK, "§6§lOpen the Journal", List.of("§7Everything you've found on Origin.", "§8/origin any time"), true), "page:journal");
        if (isle.compass().touring(player)) {
            put(h, 13, item(Material.COMPASS, "§6Tour §8(" + (p.tourStep + 1) + "/" + OriginCompass.TOUR.size() + ")",
                    List.of(OriginCompass.TOUR.get(p.tourStep).line(), "", "§e▸ Click §7to point the way", "§8Right-click: stop"), true), "tour:start");
        } else {
            put(h, 13, item(Material.COMPASS, "§6§lTake the tour", List.of("§7Eight stops: the cast, the updraft,", "§7the summit and a glide back down.",
                    p.flag("tour_done") ? "§a✔ Done before" : "§6+" + c.reward("tour", 1000L) + " coins at the end"), false), "tour:start");
        }
        put(h, 15, item(Material.FILLED_MAP, "§e§lWhere next?", List.of("§7Orla points you at the nearest", "§7district you haven't walked."), false), "next");
        put(h, 22, item(Material.GOLD_NUGGET, "§b§lThe Wishing Fountain", List.of("§7Fountain Square, north of the plaza.",
                "§7Sneak + right-click the water, empty hand:", "§7one coin, one fortune. Sometimes it gives back.",
                "", "§e▸ Click §7to point the way"), p.flag("fountain_favour")), "go:landmark:fountain_square");
        int curios = isle.curiosities().found(p);
        put(h, 20, item(Material.AMETHYST_SHARD, "§d§lA curious rumour", List.of("§7Orla hears things. Ask her for one", "§7small thing worth finding nearby.",
                "§7Curiosities §f" + curios + "/" + isle.curiosities().all().size()), curios > 0), "rumour");
        long found = count(c.districts().keySet(), p.districts);
        put(h, 4, item(Material.PAPER, "§7Districts §f" + found + "/" + c.districts().size(),
                List.of(found >= c.districts().size() ? "§a✔ Wayfarer" : "§7Walk them all: §6Wayfarer"), false), null);
        fill(h, 0, 26);
        player.openInventory(h.inventory);
    }

    private void whereNext(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig.District best = null;
        double bestD = Double.MAX_VALUE;
        for (OriginConfig.District d : isle.config().districts().values()) {
            if (p.districts.contains(d.id()) || d.anchor() == null) {
                continue;
            }
            Location at = isle.config().location(d.anchor());
            if (at == null || at.getWorld() != player.getWorld()) {
                continue;
            }
            double dist = at.distanceSquared(player.getLocation());
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        if (best == null) {
            isle.cast().say(player, OriginRole.GUIDE, "You've walked every district. Now try the landmarks — the Journal knows which.");
            return;
        }
        isle.cast().say(player, OriginRole.GUIDE, "Try " + best.colored() + "§f. " + best.tagline());
        isle.compass().go(player, "district:" + best.id());
    }

    /** Orla's rumour: the riddle of the nearest unfound curiosity in a district you know, and roughly which way. */
    private void rumour(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginCuriosities.Curio best = null;
        double bestD = Double.MAX_VALUE;
        for (OriginCuriosities.Curio k : isle.curiosities().all().values()) {
            if (p.curiosities.contains(k.id()) || !p.districts.contains(k.district())) {
                continue;
            }
            Location at = isle.config().location(k.focus());
            if (at == null || at.getWorld() != player.getWorld()) {
                continue;
            }
            double d = at.distanceSquared(player.getLocation());
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        if (best == null) {
            isle.cast().say(player, OriginRole.GUIDE, isle.curiosities().found(p) >= isle.curiosities().all().size()
                    ? "You've found every small wonder I ever heard of. Now you tell me one."
                    : "Nothing I'd swear to around here. Walk a new district and come back.");
            return;
        }
        OriginConfig.District d = isle.config().district(best.district());
        Location at = isle.config().location(best.focus());
        isle.cast().say(player, OriginRole.GUIDE, "They say: “" + best.clue() + "” §7Somewhere " + OriginText.compass(player.getLocation(), at)
                + " of here" + (d == null ? "" : ", in " + d.colored()) + "§f.");
    }

    // ------------------------------------------------------------------ DEV hub

    public void openDev(Player player) {
        Holder h = holder("dev", 54, "§c§lDEV §8· §6Origin Isle");
        OriginConfig c = isle.config();
        put(h, 4, item(Material.COMMAND_BLOCK, "§6Origin Isle §8· " + (isle.running() ? "§arunning" : "§cstopped"),
                List.of("§7" + c.districts().size() + " districts · " + c.landmarks().size() + " landmarks",
                        "§7" + c.waystones().size() + " glowcaps · " + c.vistas().size() + " vistas · " + c.bells().size() + " bells",
                        "§7" + c.updrafts().size() + " updrafts · " + c.flights().size() + " flights · " + c.emitters().size() + " emitters",
                        "§7Cast placed §f" + isle.cast().placedCount() + "/" + OriginRole.values().length,
                        "§7On the isle §f" + isle.onIsle().size() + " §7· profiles cached §f" + isle.profiles().cached(),
                        "", "§e▸ Click §7to reload origin.yml"), false), "dev:reload");

        // Row 1: moments
        int slot = 9;
        for (OriginEvents.Kind kind : OriginEvents.Kind.values()) {
            put(h, slot++, item(Material.FIREWORK_ROCKET, kind.title(), List.of(kind.line(), "", "§e▸ Start now"), isle.events().active() == kind), "dev:event " + kind.id());
        }
        put(h, 16, item(Material.BARRIER, "§cStop moment", List.of(), false), "dev:event stop");
        put(h, 17, item(Material.BELL, "§eToll the bells", List.of("§7Dusk toll, now."), false), "dev:toll");

        // Row 2: cast anchors
        slot = 18;
        for (OriginRole role : OriginRole.values()) {
            put(h, slot++, item(role.icon(), role.color() + role.display() + " §8anchor",
                    List.of("§7" + (isle.cast().isPlaced(role) ? "placed" : "§cnot placed"), "§e▸ Take the anchor", "§8Right-click a block to place"), isle.cast().isPlaced(role)), "dev:anchor " + role.id());
        }
        put(h, 24, item(Material.EMERALD, "§aCast → presets", List.of("§7Place all five on origin.yml presets."), false), "dev:cast presets");
        put(h, 25, item(Material.LAVA_BUCKET, "§cRemove cast", List.of(), false), "dev:cast remove");
        put(h, 26, item(Material.NAME_TAG, "§eSeed camps", List.of("§7Summit + Whisperwood locations (only if unset)."), false), "dev:seed");

        // Row 3: softlight
        put(h, 27, item(Material.LIGHT, "§eSoftlight · Capital", List.of("§7Dark walkable spots in the Capital."), false), "dev:softlight capital");
        put(h, 28, item(Material.LIGHT, "§eSoftlight · all", List.of("§7Every district except the excluded.", "§8Heavy: ~1 minute."), false), "dev:softlight all");
        put(h, 29, item(Material.LIGHT, "§eSoftlight · here", List.of("§7Radius 48 around you."), false), "dev:softlight here");
        put(h, 30, item(Material.STRUCTURE_VOID, "§cSoftlight undo", List.of("§7Removes the " + isle.softlight().recorded() + " recorded lights."), false), "dev:softlight undo");
        put(h, 31, item(Material.BARRIER, "§7Softlight cancel", List.of(isle.softlight().busy() ? "§eRunning: " + isle.softlight().runningLabel() : "§8idle"), false), "dev:softlight cancel");

        // Row 3-4: test teleports
        slot = 33;
        for (OriginConfig.Vista v : c.vistas().values()) {
            if (slot > 35) {
                break;
            }
            put(h, slot++, item(Material.SPYGLASS, "§fTP · " + v.name(), List.of("§8vista"), false), "dev:tp " + v.id());
        }
        slot = 36;
        for (OriginConfig.Updraft u : c.updrafts().values()) {
            put(h, slot++, item(Material.WIND_CHARGE, "§bRide · " + u.name(), List.of("§8Starts the updraft on you."), false), "dev:updraft " + u.id());
        }
        for (OriginConfig.Flight f : c.flights().values()) {
            if (slot > 43) {
                break;
            }
            put(h, slot++, item(Material.ELYTRA, "§bFly · " + f.name(), List.of("§7To " + f.to(), "§8Starts the glide on you."), false), "dev:fly " + f.id());
        }

        // Row 5: player tools
        put(h, 45, item(Material.WRITABLE_BOOK, "§6Journal", List.of(), false), "page:journal");
        put(h, 46, item(Material.KNOWLEDGE_BOOK, "§aComplete my Origin", List.of("§7Marks everything found (no coins)."), false), "dev:profile complete");
        put(h, 47, item(Material.TNT, "§cReset my Origin", List.of("§7Wipes your origin-players file."), false), "dev:profile reset");
        put(h, 49, item(Material.OAK_DOOR, "§7Close", List.of(), false), "close");
        put(h, 51, item(Material.SPYGLASS, "§d§lExplore layer", List.of("§7Props §f" + isle.props().pastedCount() + "/" + isle.props().all().size() + " §7pasted",
                "§7Tales §f" + isle.tales().placedCount() + "/" + isle.tales().all().size() + " §7in the world",
                "§7Curiosities §f" + isle.curiosities().all().size(), "§e▸ Open"), false), "page:devx");
        fill(h, 0, 53);
        player.openInventory(h.inventory);
    }

    /** DEV: prop spots (click paste, right-click undo, shift-click TP + outline), tales, curiosities, life. */
    public void openDevExplore(Player player) {
        Holder h = holder("devx", 54, "§c§lDEV §8· §dExplore layer");
        OriginProps props = isle.props();
        int slot = 0;
        for (OriginProps.Spot spot : props.all().values()) {
            if (slot > 35) {
                break;
            }
            boolean down = props.pasted(spot.id());
            de.aetherion.hub.prop.PropCatalog.Prop prop = de.aetherion.hub.prop.PropCatalog.get(spot.prop());
            List<String> lore = new ArrayList<>();
            lore.add("§8" + spot.prop() + " · r" + spot.rotate());
            lore.add("§7" + spot.at()[0] + " " + spot.at()[1] + " " + spot.at()[2] + " §8· §7" + spot.group());
            lore.addAll(wrap("§8" + spot.note(), 34));
            lore.add(down ? "§a✔ pasted" : "§7not pasted");
            lore.add("");
            lore.add(down ? "§e▸ Right-click §7to undo" : "§e▸ Click §7to paste");
            lore.add("§8Shift-click: TP + outline");
            put(h, slot++, item(prop == null ? Material.PAPER : prop.icon(), (down ? "§a" : "§f") + spot.name(), lore, down), "devprop:" + spot.id());
        }
        put(h, 36, item(Material.EMERALD_BLOCK, "§aPaste all", List.of("§7Every spot that isn't down yet.", "§8One at a time through FAWE."), false), "dev:props paste all");
        put(h, 37, item(Material.REDSTONE_BLOCK, "§cUndo all", List.of("§7Newest first. Restores the snapshots."), false), "dev:props undo all");
        put(h, 38, item(Material.CLOCK, "§eUndo last", List.of(), false), "dev:props undo last");
        double[] seat = props.hesperSeat();
        put(h, 40, item(Material.ANVIL, "§6Fallen Anchor §8· §7Hesper's seat", List.of(
                seat == null ? "§cNo seat in origin-explore.yml" : "§7" + (int) Math.floor(seat[0]) + " " + (int) Math.floor(seat[1]) + " " + (int) Math.floor(seat[2]),
                "§7Camp " + (props.groupPasted("fallen_anchor") ? "§apasted" : "§cnot pasted"),
                "§7Hesper stays manual: §f/questnpc story", "§7→ Hesper's anchor → right-click the seat.", "", "§e▸ Click §7to TP there"), false),
                "dev:props tp hesper");
        put(h, 42, item(Material.CHEST, "§6Tales §8· §aplace", List.of("§7" + isle.tales().placedCount() + "/" + isle.tales().all().size() + " in the world",
                "§7Places any missing chest/barrel", "§7(only on an empty, checked spot)."), false), "dev:tales place");
        put(h, 43, item(Material.BARRIER, "§6Tales §8· §cundo", List.of("§7Removes the chests Origin placed."), false), "dev:tales undo");
        put(h, 45, item(Material.ARROW, "§7← DEV", List.of(), false), "page:dev");
        put(h, 46, item(Material.AMETHYST_SHARD, "§dCuriosity §8· §7nearest unfound", List.of("§7Points the wayfinder at it."), false), "dev:curio next");
        put(h, 47, item(Material.TNT, "§dCuriosities §8· §creset mine", List.of(), false), "dev:curio reset");
        put(h, 48, item(Material.WRITABLE_BOOK, "§6Tales §8· §creset mine", List.of(), false), "dev:tales reset");
        List<String> life = new ArrayList<>();
        for (String line : isle.life().status().split("\n")) {
            life.addAll(wrap(line, 40));
        }
        put(h, 50, item(Material.HONEYCOMB, "§eAmbient life", life, false), "dev:life");
        put(h, 49, item(Material.OAK_DOOR, "§7Close", List.of(), false), "close");
        fill(h, 0, 53);
        player.openInventory(h.inventory);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.4f, 1.4f);
        handle(player, holder, action, event.isRightClick(), event.isShiftClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void handle(Player player, Holder holder, String action, boolean right, boolean shift) {
        int colon = action.indexOf(':');
        String verb = colon < 0 ? action : action.substring(0, colon);
        String arg = colon < 0 ? "" : action.substring(colon + 1);
        switch (verb) {
            case "close" -> player.closeInventory();
            case "page" -> openPage(player, arg);
            case "go" -> {
                player.closeInventory();
                if (!isle.compass().go(player, arg)) {
                    player.sendMessage("§7Can't point there right now.");
                }
            }
            case "hint" -> {
                OriginConfig.District d = isle.config().district(arg);
                if (d != null && d.anchor() != null) {
                    player.sendMessage("§7An undiscovered district lies §f" + where(player, d.anchor()).substring(2) + "§7. Go find it.");
                }
            }
            case "lhint" -> {
                OriginConfig.Landmark l = isle.config().landmark(arg);
                if (l != null) {
                    OriginConfig.District d = isle.config().district(l.district());
                    boolean known = d != null && isle.profiles().get(player).districts.contains(d.id());
                    player.sendMessage("§e✧ §7Hint: " + (known ? "in " + d.colored() + "§7, " : "") + "§f" + where(player, l.at()).substring(2)
                            + "§7. §8(" + OriginText.pretty(l.icon().name()) + ")");
                }
            }
            case "rumour" -> {
                player.closeInventory();
                rumour(player);
            }
            case "chint" -> {
                OriginCuriosities.Curio k = isle.curiosities().get(arg);
                if (k != null) {
                    OriginConfig.District d = isle.config().district(k.district());
                    Location to = isle.config().location(k.focus());
                    String dir = to == null || to.getWorld() != player.getWorld() ? "somewhere" : "§f" + OriginText.compass(player.getLocation(), to) + " §7of you";
                    player.sendMessage("§d✧ §7“" + k.clue() + "” §8· §7" + dir + (d == null ? "" : ", in " + d.colored()) + "§7.");
                }
            }
            case "tale" -> {
                OriginTales.Tale t = isle.tales().get(arg);
                if (t != null) {
                    player.closeInventory();
                    if (right) {
                        isle.compass().point(player, "§6" + t.name(), isle.config().location(new double[]{t.at()[0] + 0.5d, t.at()[1], t.at()[2] + 0.5d}));
                    } else {
                        isle.tales().read(player, t);
                    }
                }
            }
            case "devprop" -> {
                if (!OriginCommand.dev(player)) {
                    return;
                }
                String result;
                if (shift) {
                    player.closeInventory();
                    result = isle.props().tp(player, arg);
                } else if (right) {
                    result = isle.props().undo(player, arg);
                } else {
                    result = isle.props().paste(player, arg);
                }
                if (result != null) {
                    player.sendMessage(result);
                }
                if (!shift) {
                    openDevExplore(player);
                }
            }
            case "travel" -> {
                player.closeInventory();
                String error = isle.waystones().travel(player, arg, false);
                if (error != null) {
                    player.sendMessage(error);
                }
            }
            case "ride" -> {
                for (Object[] r : isle.pads().catalog()) {
                    if (r[0].equals(arg)) {
                        player.closeInventory();
                        Location at = new Location(isle.config().world(), (double) r[3], (double) r[4], (double) r[5]);
                        isle.compass().point(player, (String) r[1], at);
                        return;
                    }
                }
            }
            case "tour" -> {
                player.closeInventory();
                if (right && isle.compass().touring(player)) {
                    isle.compass().stopTour(player);
                    player.sendMessage("§7Tour paused. §f/origin tour §7picks it back up.");
                } else {
                    isle.compass().startTour(player);
                }
            }
            case "stop" -> {
                isle.compass().stop(player);
                player.closeInventory();
                player.sendMessage("§7Wayfinder cleared.");
            }
            case "toggle" -> {
                OriginProfile p = isle.profiles().get(player);
                if (arg.equals("ambience")) {
                    p.ambience = !p.ambience;
                } else if (arg.equals("particles")) {
                    p.particles = !p.particles;
                }
                p.dirty = true;
                openSettings(player);
            }
            case "hymn" -> {
                player.closeInventory();
                isle.bells().hymn(player);
                isle.cast().flourish(OriginRole.BELLKEEPER, Particle.NOTE, 6);
            }
            case "next" -> {
                player.closeInventory();
                whereNext(player);
            }
            case "dev" -> {
                if (!OriginCommand.dev(player)) {
                    return;
                }
                String result = isle.dev().run(player, arg);
                if (result != null) {
                    player.sendMessage(result);
                }
                if (arg.startsWith("anchor") || arg.startsWith("tp") || arg.startsWith("updraft") || arg.startsWith("fly")
                        || arg.startsWith("softlight") || arg.startsWith("props tp") || arg.startsWith("curio next")) {
                    player.closeInventory();
                } else if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open && open.page().equals("dev")) {
                    openDev(player);
                } else if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open && open.page().equals("devx")) {
                    openDevExplore(player);
                }
            }
            default -> {
            }
        }
    }

    public void openPage(Player player, String page) {
        String base = page;
        int number = 0;
        int colon = page.indexOf(':');
        if (colon > 0) {
            base = page.substring(0, colon);
            try {
                number = Integer.parseInt(page.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        switch (base) {
            case "journal" -> openJournal(player);
            case "landmarks" -> openLandmarks(player, number);
            case "curios", "curiosities" -> openCurios(player, number);
            case "tales" -> openTales(player);
            case "waystones" -> openWaystones(player, null);
            case "bells" -> openBells(player, false);
            case "vistas" -> openVistas(player, false);
            case "rides" -> openRides(player, false);
            case "cast" -> openCast(player);
            case "settings" -> openSettings(player);
            case "dev" -> {
                if (OriginCommand.dev(player)) {
                    openDev(player);
                }
            }
            case "devx" -> {
                if (OriginCommand.dev(player)) {
                    openDevExplore(player);
                }
            }
            default -> openJournal(player);
        }
    }
}
