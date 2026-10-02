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
            {"wishmaker", "§dWishmaker", "seven wishes on falling stars"}
    };

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

        put(h, 37, item(Material.MAP, "§e§lLandmarks", List.of("§7" + count(c.landmarks().keySet(), p.landmarks) + "/" + c.landmarks().size() + " found", "§e▸ Open"), false), "page:landmarks");
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

    private void openLandmarks(Player player) {
        OriginProfile p = isle.profiles().get(player);
        OriginConfig c = isle.config();
        Holder h = holder("landmarks", 54, "§e✧ Landmarks §8· §7" + count(c.landmarks().keySet(), p.landmarks) + "/" + c.landmarks().size());
        int slot = 0;
        for (OriginConfig.Landmark l : c.landmarks().values()) {
            if (slot > 44) {
                break;
            }
            OriginConfig.District d = c.district(l.district());
            String dn = d == null ? "" : d.colored();
            if (p.landmarks.contains(l.id())) {
                put(h, slot, item(l.icon(), (d == null ? "§e" : d.color()) + l.name(),
                        List.of("§7" + l.blurb(), dn, where(player, l.at()), "", "§e▸ Click §7to point the way"), false), "go:landmark:" + l.id());
            } else {
                boolean districtKnown = d != null && p.districts.contains(d.id());
                put(h, slot, item(Material.GRAY_DYE, "§8???", List.of(districtKnown ? "§7Somewhere in " + dn : "§8In a district you haven't found.",
                        "§e▸ Click §7for a hint"), false), "lhint:" + l.id());
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
        handle(player, holder, action, event.isRightClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void handle(Player player, Holder holder, String action, boolean right) {
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
                        || arg.startsWith("softlight")) {
                    player.closeInventory();
                } else if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open && open.page().equals("dev")) {
                    openDev(player);
                }
            }
            default -> {
            }
        }
    }

    public void openPage(Player player, String page) {
        switch (page) {
            case "journal" -> openJournal(player);
            case "landmarks" -> openLandmarks(player);
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
            default -> openJournal(player);
        }
    }
}
