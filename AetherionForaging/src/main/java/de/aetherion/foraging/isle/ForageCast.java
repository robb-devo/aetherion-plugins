package de.aetherion.foraging.isle;

import de.aetherion.core.npc.FancyNpcFacade;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Grove cast — three FancyNPCs that run the Foraging loops, spread from the Landing down the vale
 * and up onto the Blossom Shelf, so learning the isle means walking (and riding) it:
 * <ul>
 *   <li><b>Pell Ardwin</b>, Board Clerk — the Lumber Board, at the Landing stall.</li>
 *   <li><b>Tamsin Holt</b>, Woodwright — Grove Marks and consumables, in the Bell Lodge.</li>
 *   <li><b>Juniper Quell</b>, Archivist — the Forest Ledger, the Cabinet and find sales, in the Pagoda.</li>
 * </ul>
 * Spots were measured from the schematic ({@code forage-isle.yml → cast}); each has a DEV move-anchor.
 * Isle-local only: FancyNPC names are {@code ae_forage_cast_*}, nothing here touches the Quests NPC
 * stack, and there are no borrowed skins — an empty {@code skin} means the default Steve / Alex.
 */
public final class ForageCast implements Listener {

    public static final String BOARD_CLERK = "board_clerk";
    public static final String WOODWRIGHT = "woodwright";
    public static final String ARCHIVIST = "archivist";
    public static final List<String> ROLES = List.of(BOARD_CLERK, WOODWRIGHT, ARCHIVIST);

    private static final String PREFIX = "ae_forage_cast_";
    private static final String HOLO_TAG = "ae_grove_cast_holo";
    private static final String HIDE_TEAM = "ae_forage_hide_npc";

    public record Member(String role, String name, String title, Material hand, String[] lines) {
    }

    private static final Map<String, Member> MEMBERS = Map.of(
            BOARD_CLERK, new Member(BOARD_CLERK, "Pell Ardwin", "Lumber Board", Material.WRITABLE_BOOK, new String[] {
                    "Three orders, fair rates. The island pays its bills in wood.",
                    "Spruce is up this week. Everything is always up this week.",
                    "Pin it, fill it, get paid. I like simple people."}),
            WOODWRIGHT, new Member(WOODWRIGHT, "Tamsin Holt", "Woodwright", Material.IRON_AXE, new String[] {
                    "Marks don't live on the axe. They live on you. Axes break.",
                    "Bring me district wood. The bench knows the difference.",
                    "Heartwood hums when it's near the bell. Don't ask me why."}),
            ARCHIVIST, new Member(ARCHIVIST, "Juniper Quell", "Forest Ledger", Material.BOOK, new String[] {
                    "Every log you've ever cut is in here. Every single one.",
                    "The Cabinet has twenty-eight slots. Most people stop at nine.",
                    "You rode up? Good. Everyone should see the island from here once."})
    );

    private final ForageIsle isle;
    private final Map<String, UUID> holograms = new HashMap<>();
    private final Map<UUID, Long> talkCooldown = new ConcurrentHashMap<>();
    private NamespacedKey anchorKey;

    ForageCast(ForageIsle isle) {
        this.isle = isle;
        this.anchorKey = new NamespacedKey(isle.plugin(), "grove_cast_anchor");
        hookClicks();
    }

    public static Member member(String role) {
        return MEMBERS.get(role);
    }

    private static String fancyName(String role) {
        return PREFIX + role;
    }

    // ------------------------------------------------------------------ placing

    /** First start: place every member at its measured spot (once). Later starts only re-show them. */
    void ensureAll() {
        if (!FancyNpcFacade.isAvailable()) {
            isle.plugin().getLogger().warning("Grove cast: FancyNpcs missing — Pell, Tamsin and Juniper are not placed.");
            return;
        }
        var yaml = isle.config().yaml();
        boolean auto = yaml.getBoolean("cast.auto-place", true);
        for (String role : ROLES) {
            boolean placed = yaml.getBoolean("cast." + role + ".placed", false);
            if (!placed && !auto) {
                continue;
            }
            double[] at = isle.config().castAnchor(role);
            World world = isle.isleWorld();
            if (at == null || world == null) {
                continue;
            }
            Location loc = new Location(world, at[0], at[1], at[2], isle.config().castYaw(role), 0f);
            spawn(role, loc, false);
            if (!placed) {
                yaml.set("cast." + role + ".placed", true);
                isle.config().save();
            }
        }
    }

    public void place(String role, Location at) {
        isle.config().setCastAnchor(role, at.getX(), at.getY(), at.getZ(), at.getYaw());
        isle.config().yaml().set("cast." + role + ".placed", true);
        isle.config().save();
        spawn(role, at, true);
    }

    private void spawn(String role, Location at, boolean moved) {
        Member member = MEMBERS.get(role);
        if (member == null || !FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> spawn(role, at, moved), 40L);
                return;
            }
            String name = fancyName(role);
            Object existing = FancyNpcFacade.getNpc(manager, name);
            int visibility = Math.max(16, isle.config().yaml().getInt("cast.visibility-distance", 40));
            if (existing != null) {
                Object data = FancyNpcFacade.data(existing);
                if (moved) {
                    FancyNpcFacade.setLocation(data, at.clone());
                }
                FancyNpcFacade.applyVisibility(data, visibility);
                applySkin(data, role);
                FancyNpcFacade.moveForAll(existing);
                FancyNpcFacade.updateForAll(existing);
                FancyNpcFacade.spawnForAll(existing);
                if (moved) {
                    FancyNpcFacade.saveNpcs(manager, false);
                }
                hideNametag(existing);
                return;
            }
            UUID creator = new UUID(0L, Math.abs(name.hashCode()));
            Object data = FancyNpcFacade.createNpcData(name, creator, at.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invokeQuiet(data, "setType", org.bukkit.entity.EntityType.class, org.bukkit.entity.EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.applyVisibility(data, visibility);
            applySkin(data, role);
            try {
                data.getClass().getMethod("addEquipment", FancyNpcFacade.equipmentSlotClass(), ItemStack.class)
                        .invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), new ItemStack(member.hand()));
            } catch (ReflectiveOperationException ignored) {
            }
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.invokeQuiet(npc, "setSaveToFile", boolean.class, true);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            FancyNpcFacade.saveNpcs(manager, false);
            hideNametag(npc);
            isle.plugin().getLogger().info("Grove cast: " + member.name() + " placed at "
                    + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
        } catch (Throwable t) {
            isle.plugin().getLogger().warning("Grove cast: " + member.name() + " could not be placed: " + t.getMessage());
        }
    }

    private void applySkin(Object data, String role) {
        String skin = isle.config().castSkin(role);
        if (skin == null || skin.isBlank()) {
            return;
        }
        try {
            data.getClass().getMethod("setSkin", String.class).invoke(data, skin);
        } catch (Throwable ignored) {
        }
    }

    public void remove(String role) {
        removeHologram(role);
        isle.config().yaml().set("cast." + role + ".placed", false);
        isle.config().save();
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            Object npc = FancyNpcFacade.getNpc(manager, fancyName(role));
            if (npc == null) {
                return;
            }
            FancyNpcFacade.removeFromPlayersQuiet(npc);
            try {
                FancyNpcFacade.unregister(manager, npc);
            } catch (ReflectiveOperationException ignored) {
            }
            FancyNpcFacade.saveNpcs(manager, false);
        } catch (Throwable t) {
            isle.plugin().getLogger().warning("Grove cast remove failed: " + t.getMessage());
        }
    }

    public boolean placed(String role) {
        return isle.config().yaml().getBoolean("cast." + role + ".placed", false);
    }

    private void hideNametag(Object fancyNpc) {
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            try {
                Object entity = null;
                for (String method : List.of("getEntity", "getNpcEntity", "getBukkitEntity")) {
                    try {
                        entity = fancyNpc.getClass().getMethod(method).invoke(fancyNpc);
                        if (entity != null) {
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (!(entity instanceof Entity bukkit) || Bukkit.getScoreboardManager() == null) {
                    return;
                }
                var board = Bukkit.getScoreboardManager().getMainScoreboard();
                var team = board.getTeam(HIDE_TEAM);
                if (team == null) {
                    team = board.registerNewTeam(HIDE_TEAM);
                    team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                            org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                }
                team.addEntity(bukkit);
            } catch (Throwable ignored) {
            }
        }, 5L);
    }

    // ------------------------------------------------------------------ holograms (non-persistent, re-ensured)

    /** Every ~5 s: name plates above placed members whose chunk is loaded. */
    void tickHolograms() {
        World world = isle.isleWorld();
        if (world == null) {
            return;
        }
        for (String role : ROLES) {
            double[] at = isle.config().castAnchor(role);
            if (at == null || !placed(role)) {
                removeHologram(role);
                continue;
            }
            Location want = new Location(world, at[0], at[1] + 2.15d, at[2]);
            if (!world.isChunkLoaded(want.getBlockX() >> 4, want.getBlockZ() >> 4)) {
                holograms.remove(role);
                continue;
            }
            UUID id = holograms.get(role);
            Entity existing = id == null ? null : Bukkit.getEntity(id);
            if (existing instanceof TextDisplay text && text.isValid()) {
                if (text.getLocation().distanceSquared(want) > 0.01d) {
                    text.teleport(want);
                }
                continue;
            }
            Member member = MEMBERS.get(role);
            TextDisplay holo = world.spawn(want, TextDisplay.class, t -> {
                t.text(ForageText.legacy("§a§l" + member.name() + "\n§7" + member.title()));
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setShadowed(true);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setPersistent(false);
                t.addScoreboardTag(HOLO_TAG);
            });
            holograms.put(role, holo.getUniqueId());
        }
    }

    private void removeHologram(String role) {
        UUID id = holograms.remove(role);
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    void removeHolograms() {
        for (String role : ROLES) {
            removeHologram(role);
        }
    }

    static boolean isOurs(Entity entity) {
        return entity.getScoreboardTags().contains(HOLO_TAG);
    }

    // ------------------------------------------------------------------ talking

    private void hookClicks() {
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.player() == null || click.name() == null
                            || !click.name().toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    talk(click.player(), click.name().substring(PREFIX.length()));
                } catch (ReflectiveOperationException ex) {
                    isle.plugin().getLogger().warning("Grove cast click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.NORMAL, executor, isle.plugin(), false);
        } catch (ClassNotFoundException | LinkageError ex) {
            isle.plugin().getLogger().info("Grove cast: FancyNpcs interact event not found — use /grove board|bench|ledger.");
        }
    }

    public void talk(Player player, String role) {
        Member member = MEMBERS.get(role);
        if (member == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = talkCooldown.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        talkCooldown.put(player.getUniqueId(), now + 700L);
        String line = member.lines()[java.util.concurrent.ThreadLocalRandom.current().nextInt(member.lines().length)];
        player.sendMessage("§a" + member.name() + " §8» §f" + line);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 1.2f);
        isle.compass().talked(player, role);
        switch (role) {
            case BOARD_CLERK -> isle.menus().openBoard(player);
            case WOODWRIGHT -> isle.menus().openBench(player);
            case ARCHIVIST -> isle.menus().openLedger(player);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ DEV move anchors

    public ItemStack anchor(String role) {
        Member member = MEMBERS.get(role);
        ItemStack item = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && member != null) {
            meta.setDisplayName("§2Grove Cast · §f" + member.name());
            meta.setLore(List.of("§7DEV · " + member.title(), "", "§eRight-click a block §7to (re)place here.",
                    "§eSneak + right-click §7removes them."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }
        String role = event.getItem().getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING);
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("aetherion.forage.admin") && !player.hasPermission("aetherion.dev.content")) {
            return;
        }
        if (player.isSneaking()) {
            remove(role);
            player.sendMessage("§eRemoved §f" + MEMBERS.get(role).name() + "§e. The anchor can place them again.");
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getRelative(org.bukkit.block.BlockFace.UP).getLocation().add(0.5, 0, 0.5);
        at.setYaw(Math.round(player.getLocation().getYaw() + 180f) % 360);
        place(role, at);
        player.sendMessage("§a" + MEMBERS.get(role).name() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
    }

    void forget(UUID id) {
        talkCooldown.remove(id);
    }
}
