package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.isle.ForageItems.Grade;
import de.aetherion.foraging.isle.ForageItems.Tonic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DEV backend for "Forage Island". The DEV hub ({@code /grove dev}, and the WORLDS tile in the Items DEV
 * menu) renders buttons; every button is an action string {@code group:verb[:arg]} that also works as
 * {@code /grove dev <action>}. Every reply is a chat line for whoever ran it.
 */
public final class ForageDev {

    private final ForageIsle isle;

    ForageDev(ForageIsle isle) {
        this.isle = isle;
    }

    public String run(Player player, String action) {
        String[] parts = action.toLowerCase(Locale.ROOT).split(":");
        String group = parts[0];
        String verb = parts.length > 1 ? parts[1] : "";
        String arg = parts.length > 2 ? parts[2] : "";
        World world = isle.isleWorld();
        switch (group) {
            case "tp" -> {
                Location at = isle.compass().resolve(verb, world);
                if (at == null) {
                    return "§cUnknown place §f" + verb;
                }
                player.teleport(at);
                return "§aTeleported §8· §f" + verb;
            }
            case "updraft" -> {
                ForageConfig.Updraft draft = isle.config().updrafts().get(arg);
                switch (verb) {
                    case "from", "to" -> {
                        if (draft == null) {
                            return "§cUnknown updraft " + arg;
                        }
                        player.teleport(verb.equals("from")
                                ? new Location(world, draft.fx(), draft.fy(), draft.fz())
                                : new Location(world, draft.tx(), draft.ty(), draft.tz()));
                        return "§aAt the " + verb + " of §f" + draft.display();
                    }
                    case "setfrom", "setto" -> {
                        String id = arg.isBlank() ? "custom_" + (isle.config().updrafts().size() + 1) : arg;
                        Location at = player.getLocation();
                        isle.config().moveUpdraft(id, verb.equals("setfrom"), at.getX(), at.getY(), at.getZ());
                        return "§aUpdraft §f" + id + " §7" + verb.substring(3) + " set here. §8(forage-isle.yml)";
                    }
                    case "remove" -> {
                        isle.config().removeUpdraft(arg);
                        return "§eUpdraft §f" + arg + " §eremoved.";
                    }
                    case "ride" -> {
                        if (draft == null) {
                            return "§cUnknown updraft " + arg;
                        }
                        player.teleport(new Location(world, draft.fx(), draft.fy(), draft.fz(), player.getLocation().getYaw(), 0f));
                        isle.traversal().begin(player, draft);
                        return "§aRiding §f" + draft.display();
                    }
                    default -> {
                        return "§7updraft:from|to|ride|setfrom|setto|remove:<id>";
                    }
                }
            }
            case "place" -> {
                if (verb.equals("move") && isle.config().landmark(arg) != null) {
                    Location at = player.getLocation();
                    isle.config().moveLandmark(arg, at.getX(), at.getY(), at.getZ());
                    return "§aPlace §f" + arg + " §aanchor moved here.";
                }
                return "§7place:move:<id>";
            }
            case "cast" -> {
                switch (verb) {
                    case "placeall" -> {
                        isle.config().yaml().set("cast.auto-place", true);
                        for (String role : ForageCast.ROLES) {
                            isle.config().yaml().set("cast." + role + ".placed", false);
                        }
                        isle.config().save();
                        isle.cast().ensureAll();
                        return "§aGrove cast placed at their measured spots.";
                    }
                    case "removeall" -> {
                        for (String role : ForageCast.ROLES) {
                            isle.cast().remove(role);
                        }
                        return "§eGrove cast removed. §8(cast:placeall puts them back)";
                    }
                    case "anchor" -> {
                        if (ForageCast.member(arg) == null) {
                            return "§cUnknown role " + arg;
                        }
                        ForageBridge.give(player, isle.cast().anchor(arg));
                        return "§aAnchor for §f" + ForageCast.member(arg).name();
                    }
                    case "here" -> {
                        if (ForageCast.member(arg) == null) {
                            return "§cUnknown role " + arg;
                        }
                        isle.cast().place(arg, player.getLocation());
                        return "§a" + ForageCast.member(arg).name() + " §7placed where you stand.";
                    }
                    case "talk" -> {
                        isle.cast().talk(player, arg);
                        return "§7(opened " + arg + ")";
                    }
                    default -> {
                        return "§7cast:placeall|removeall|anchor:<role>|here:<role>|talk:<role>";
                    }
                }
            }
            case "event" -> {
                if (verb.equals("stop")) {
                    isle.events().end(false);
                    return "§eEvent stopped.";
                }
                try {
                    GroveEvents.Kind kind = GroveEvents.Kind.valueOf(verb.toUpperCase(Locale.ROOT));
                    isle.events().start(kind);
                    return "§aStarting " + kind.title + " §7in 3s.";
                } catch (IllegalArgumentException ex) {
                    return "§7event:golden_sap|windfall|blossom_storm|bark_blight|stop";
                }
            }
            case "critter" -> {
                try {
                    GroveCritters.Kind kind = GroveCritters.Kind.valueOf(verb.toUpperCase(Locale.ROOT));
                    Location at = GroveEvents.groundNear(player.getLocation(), 2, 4);
                    if (at == null) {
                        at = player.getLocation().add(2, 0, 0);
                    }
                    if (kind == GroveCritters.Kind.FROST_MOTH || kind == GroveCritters.Kind.WISP) {
                        at.add(0, 1.5, 0);
                    }
                    isle.critters().spawn(kind, at, player);
                    return "§aSpawned " + kind.display;
                } catch (IllegalArgumentException ex) {
                    return "§7critter:squirrel|frost_moth|wisp|beetle";
                }
            }
            case "find" -> {
                Grade grade = Grade.byName(arg.isBlank() ? "rough" : arg);
                if (verb.equals("all")) {
                    for (Find find : Find.values()) {
                        ForageBridge.give(player, ForageItems.find(ForageItems.roll(find, grade == null ? Grade.ROUGH : grade)));
                    }
                    return "§aOne of every find §8(" + (grade == null ? "Rough" : grade.display) + ")";
                }
                if (verb.equals("drop")) {
                    Location at = player.getLocation();
                    isle.finds().drop(player, ForageItems.roll(Find.of(isle.grove(at)), grade == null ? Grade.FINE : grade),
                            at.clone().add(0, 12, 0), at);
                    return "§aA Crown Find is falling on you.";
                }
                Find find = Find.byId(verb);
                if (find == null) {
                    return "§7find:all[:grade] | find:drop[:grade] | find:<kind>[:grade]";
                }
                ForageBridge.give(player, ForageItems.find(ForageItems.roll(find, grade == null ? Grade.ROUGH : grade)));
                return "§aGave " + find.colored();
            }
            case "tonic" -> {
                Tonic tonic = Tonic.byId(verb);
                if (tonic == null) {
                    for (Tonic t : Tonic.values()) {
                        ForageBridge.give(player, ForageItems.tonic(t, 3));
                    }
                    return "§aThree of each consumable.";
                }
                ForageBridge.give(player, ForageItems.tonic(tonic, 3));
                return "§aGave 3× " + tonic.display;
            }
            case "heartwood" -> {
                int n = 0;
                for (Wood wood : Wood.values()) {
                    ItemStack heart = ForageBridge.heartwood(wood.key());
                    if (heart != null) {
                        heart.setAmount(verb.isBlank() ? 1 : Math.max(1, parse(verb, 1)));
                        ForageBridge.give(player, heart);
                        n++;
                    }
                }
                return n == 0 ? "§cItems can't make heartwoods right now." : "§aOne of each heartwood.";
            }
            case "logs" -> {
                for (Wood wood : Wood.values()) {
                    ForageBridge.give(player, new ItemStack(wood.log(), 64 * Math.max(1, parse(verb, 1))));
                }
                return "§aLogs of every wood.";
            }
            case "profile" -> {
                ForageProfile profile = isle.profiles().get(player);
                switch (verb) {
                    case "reset" -> {
                        if (!arg.equals("confirm")) {
                            return "§eSure? §7Run §f/grove dev profile:reset:confirm §7to wipe YOUR Foraging Eldervale profile.";
                        }
                        isle.profiles().wipe(player.getUniqueId());
                        return "§eYour Foraging Eldervale profile was wiped.";
                    }
                    case "standing" -> {
                        isle.standing().add(player, profile, Math.max(1, parse(arg, 10)), "dev");
                        return "§aStanding now §f" + profile.standing;
                    }
                    case "marks" -> {
                        for (Woodwright.Line line : Woodwright.LINES) {
                            profile.marks.put(line.id(), Woodwright.MAX_TIER);
                        }
                        profile.dirty = true;
                        return "§aEvery Grove Mark at V.";
                    }
                    case "mastery" -> {
                        int tier = Math.max(0, Math.min(GroveMastery.MAX_TIER, parse(arg, GroveMastery.MAX_TIER)));
                        for (Wood wood : Wood.values()) {
                            profile.fells.put(wood.key(), GroveMastery.threshold(wood, tier));
                            profile.masteryPaid.put(wood.key(), tier);
                        }
                        profile.dirty = true;
                        return "§aEvery wood at mastery " + ForageText.roman(tier) + " §8(no payout).";
                    }
                    case "cabinet" -> {
                        for (Find find : Find.values()) {
                            for (Grade grade : Grade.values()) {
                                profile.cabinet.add(find.id() + ":" + grade.name());
                            }
                        }
                        profile.dirty = true;
                        return "§aCabinet filled (claim rows/columns at Juniper).";
                    }
                    case "discover" -> {
                        for (Grove grove : Grove.values()) {
                            profile.groves.add(grove.id());
                        }
                        profile.places.addAll(isle.config().landmarks().keySet());
                        profile.dirty = true;
                        return "§aEvery forest and place marked found §8(no payout).";
                    }
                    case "shaker" -> {
                        profile.addCharge(Woodwright.CHARGE_SHAKER, 5);
                        return "§a+5 Crown Shaker charges.";
                    }
                    default -> {
                        return "§7profile:reset|standing[:n]|marks|mastery[:tier]|cabinet|discover|shaker";
                    }
                }
            }
            case "where" -> {
                Location at = player.getLocation();
                Grove grove = isle.grove(at);
                Grove grid = isle.map().gridAt(at.getX(), at.getY(), at.getZ());
                Landmark landmark = isle.landmark(at);
                return "§2Where §8· §7isle §f" + isle.onIsle(at) + " §8· §7forest §f" + (grove == null ? "—" : grove.display())
                        + " §8(grid " + (grid == null ? "—" : grid.id()) + ") §8· §7place §f" + (landmark == null ? "—" : landmark.display())
                        + " §8· §7weather §f" + isle.weatherKind(player) + " §8· §7grid " + isle.map().status();
            }
            case "reload" -> {
                isle.reload();
                return "§aforage-isle.yml reloaded · " + isle.config().landmarks().size() + " places · "
                        + isle.config().updrafts().size() + " updrafts.";
            }
            case "status" -> {
                return "§2Grove §8· §7on isle §f" + isle.playersOnIsle().size() + " §8· §7event " + isle.events().status()
                        + " §8· §7critters §f" + isle.critters().count() + " §8· §7falling finds §f" + isle.finds().active()
                        + " §8· §7riders §f" + isle.traversal().riding() + " §8· §7skills in Items §f" + ForageBridge.skillsInstalled();
            }
            default -> {
                return "§7Unknown DEV action §f" + action;
            }
        }
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ the hub

    public void open(Player player) {
        open(player, "main");
    }

    public void open(Player player, String page) {
        ForageMenus.Menu menu = new ForageMenus.Menu();
        Inventory inv = Bukkit.createInventory(menu, 54, ForageText.legacy("§2⚒ Forage Island §8· DEV · " + page));
        menu.inventory = inv;
        ItemStack fill = ForageMenus.button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, fill);
        }
        List<Btn> buttons = switch (page) {
            case "places" -> placesPage();
            case "updrafts" -> updraftPage();
            case "cast" -> castPage();
            case "live" -> livePage();
            case "items" -> itemsPage();
            case "progress" -> progressPage();
            default -> mainPage();
        };
        int[] inner = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (Btn b : buttons) {
            if (i >= inner.length) {
                break;
            }
            int slot = inner[i++];
            inv.setItem(slot, ForageMenus.button(b.icon, b.name, b.lore));
            menu.actions.put(slot, click -> {
                if (b.action.startsWith("page:")) {
                    open(player, b.action.substring(5));
                    return;
                }
                player.sendMessage(run(player, b.action));
                if (b.reopen) {
                    open(player, page);
                } else {
                    player.closeInventory();
                }
            });
        }
        inv.setItem(4, ForageMenus.button(Material.OAK_SAPLING, "§2Foraging Eldervale", "§7" + isle.map().status(),
                "§7" + isle.events().status()));
        if (!page.equals("main")) {
            inv.setItem(49, ForageMenus.button(Material.ARROW, "§eBack"));
            menu.actions.put(49, click -> open(player, "main"));
        } else {
            inv.setItem(49, ForageMenus.button(Material.BARRIER, "§cClose"));
            menu.actions.put(49, click -> player.closeInventory());
        }
        player.openInventory(inv);
    }

    private record Btn(Material icon, String name, String action, boolean reopen, String... lore) {
    }

    private List<Btn> mainPage() {
        return List.of(
                new Btn(Material.FILLED_MAP, "§aPlaces", "page:places", false, "§7Teleport to every forest and place."),
                new Btn(Material.FEATHER, "§fUpdrafts", "page:updrafts", false, "§7Ride, visit or re-measure the wind shafts."),
                new Btn(Material.VILLAGER_SPAWN_EGG, "§2Grove Cast", "page:cast", false, "§7Place · move · remove Pell, Tamsin, Juniper."),
                new Btn(Material.BELL, "§6Events & Critters", "page:live", false, "§7Start isle events, spawn critters."),
                new Btn(Material.CHEST, "§eItems", "page:items", false, "§7Finds · consumables · heartwood · logs."),
                new Btn(Material.EXPERIENCE_BOTTLE, "§dProgress", "page:progress", false, "§7Standing · marks · mastery · wipe."),
                new Btn(Material.COMPASS, "§bWhere am I?", "where", false, "§7Forest · grid cell · place · weather."),
                new Btn(Material.COMPARATOR, "§7Status", "status", true, "§7Events, critters, riders, Items skills."),
                new Btn(Material.WRITABLE_BOOK, "§7Reload forage-isle.yml", "reload", true, "§7Places, updrafts, tuning."));
    }

    private List<Btn> placesPage() {
        List<Btn> out = new ArrayList<>();
        for (Grove grove : Grove.values()) {
            out.add(new Btn(grove.icon(), grove.colored(), "tp:" + grove.id(), false, "§7" + grove.tagline()));
        }
        for (Landmark landmark : isle.config().landmarks().values()) {
            out.add(new Btn(Material.MAP, landmark.colored(), "tp:" + landmark.id(), false, "§7" + landmark.blurb(),
                    "§8/grove dev place:move:" + landmark.id()));
        }
        return out;
    }

    private List<Btn> updraftPage() {
        List<Btn> out = new ArrayList<>();
        for (ForageConfig.Updraft draft : isle.config().updrafts().values()) {
            out.add(new Btn(Material.FEATHER, "§f≋ " + draft.display(), "updraft:ride:" + draft.id(), false,
                    "§7Ride it (teleports to the vent).", "§8from " + (int) draft.fx() + " " + (int) draft.fy() + " " + (int) draft.fz(),
                    "§8to " + (int) draft.tx() + " " + (int) draft.ty() + " " + (int) draft.tz(),
                    "§8Re-measure: /grove dev updraft:setfrom|setto:" + draft.id()));
        }
        return out;
    }

    private List<Btn> castPage() {
        List<Btn> out = new ArrayList<>();
        out.add(new Btn(Material.EMERALD, "§aPlace all at measured spots", "cast:placeall", true));
        out.add(new Btn(Material.BARRIER, "§cRemove all", "cast:removeall", true));
        for (String role : ForageCast.ROLES) {
            ForageCast.Member m = ForageCast.member(role);
            out.add(new Btn(Material.VILLAGER_SPAWN_EGG, "§2" + m.name() + " §7anchor", "cast:anchor:" + role, false,
                    "§7Right-click to place, sneak to remove.", isle.cast().placed(role) ? "§aplaced" : "§8not placed"));
            out.add(new Btn(Material.ENDER_PEARL, "§7Go to " + m.name(), "tp:" + role, false));
            out.add(new Btn(Material.BOOK, "§7Open " + m.title(), "cast:talk:" + role, false));
        }
        return out;
    }

    private List<Btn> livePage() {
        List<Btn> out = new ArrayList<>();
        for (GroveEvents.Kind kind : GroveEvents.Kind.values()) {
            out.add(new Btn(Material.BELL, kind.title, "event:" + kind.name().toLowerCase(Locale.ROOT), false,
                    "§7Start in 3s (needs someone on the isle)."));
        }
        out.add(new Btn(Material.BARRIER, "§cStop event", "event:stop", true));
        for (GroveCritters.Kind kind : GroveCritters.Kind.values()) {
            out.add(new Btn(Material.LEAD, kind.display, "critter:" + kind.name().toLowerCase(Locale.ROOT), false, "§7Spawn one near you."));
        }
        out.add(new Btn(Material.GOLD_NUGGET, "§eDrop a Crown Find on me", "find:drop:fine", false, "§7Try the catch."));
        return out;
    }

    private List<Btn> itemsPage() {
        List<Btn> out = new ArrayList<>();
        for (Grade grade : Grade.values()) {
            out.add(new Btn(Material.CHEST, "§7Every find · " + grade.colored(), "find:all:" + grade.name().toLowerCase(Locale.ROOT), true));
        }
        for (Tonic tonic : Tonic.values()) {
            out.add(new Btn(tonic.icon, tonic.display, "tonic:" + tonic.id, true, "§7Gives 3."));
        }
        out.add(new Btn(Material.OAK_LOG, "§dOne of each heartwood", "heartwood", true));
        out.add(new Btn(Material.JUNGLE_LOG, "§a64 of every log", "logs", true));
        return out;
    }

    private List<Btn> progressPage() {
        return List.of(
                new Btn(Material.NAME_TAG, "§2+10 Standing", "profile:standing:10", true),
                new Btn(Material.SMITHING_TABLE, "§6Every Grove Mark V", "profile:marks", true),
                new Btn(Material.GOLDEN_AXE, "§6Mastery VII everywhere", "profile:mastery:7", true),
                new Btn(Material.CHISELED_BOOKSHELF, "§dFill the Cabinet", "profile:cabinet", true),
                new Btn(Material.FILLED_MAP, "§aDiscover everything", "profile:discover", true),
                new Btn(Material.STICK, "§e+5 Crown Shaker charges", "profile:shaker", true),
                new Btn(Material.TNT, "§cWipe my Foraging Eldervale profile", "profile:reset", true, "§7Districts, mastery, marks,",
                        "§7cabinet, orders, standing."));
    }
}
