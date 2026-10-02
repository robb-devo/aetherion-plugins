package de.aetherion.hub.origin;

import de.aetherion.core.npc.FancyNpcFacade;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import com.destroystokyo.paper.event.entity.EntityZapEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the Origin cast (see {@link OriginRole}). Placements live in {@code plugins/AetherionHub/origin-cast.yml}.
 * Prefers FancyNpcs player models when available; falls back to AI-off villagers. Non-persistent either way —
 * rebuilt on chunk load so restarts never stack duplicates. Boards stay Hub GUIs (not Quests TalkUx).
 */
public final class OriginCast implements Listener {

    private static final double TURN_RANGE = 7.0d;
    private static final double BARK_RANGE = 4.5d;
    private static final long BARK_COOLDOWN_MS = 5L * 60_000L;
    private static final String FANCY_PREFIX = "aetherion_origin_";
    private static final long BARK_BUBBLE_TICKS = 90L;

    /** hologram always; villager when FancyNpcs missing; fancyName when using FancyNpcs. */
    private record Spawned(UUID hologram, UUID villager, String fancyName) {
    }

    private final OriginIsle isle;
    private final NamespacedKey npcKey;
    private final NamespacedKey anchorKey;
    private final File file;
    private final Map<OriginRole, Location> placed = new EnumMap<>(OriginRole.class);
    private final Map<OriginRole, ConfigurationSection> pending = new EnumMap<>(OriginRole.class);
    private final Map<OriginRole, Spawned> live = new EnumMap<>(OriginRole.class);
    private final Map<String, Long> barked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCool = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> barkBubble = new ConcurrentHashMap<>();
    private boolean seeded;
    private int ensureTick;
    private boolean fancyBridge;

    OriginCast(OriginIsle isle) {
        this.isle = isle;
        this.npcKey = new NamespacedKey(isle.plugin(), "origin_npc");
        this.anchorKey = new NamespacedKey(isle.plugin(), "origin_npc_anchor");
        this.file = new File(isle.plugin().getDataFolder(), "origin-cast.yml");
        load();
    }

    void start() {
        purgeStrays();
        registerFancyBridge();
        for (OriginRole role : List.copyOf(pending.keySet())) {
            Location at = fromSection(pending.get(role));
            if (at != null) {
                placed.put(role, at);
                pending.remove(role);
            }
        }
        if (!seeded && isle.config().castAutoPlace()) {
            for (OriginRole role : OriginRole.values()) {
                if (!placed.containsKey(role) && !pending.containsKey(role) && preset(role) != null) {
                    placed.put(role, preset(role));
                }
            }
            seeded = true;
            save();
        }
        for (OriginRole role : placed.keySet()) {
            ensure(role);
        }
    }

    void shutdown() {
        for (OriginRole role : OriginRole.values()) {
            despawn(role);
        }
        for (UUID id : barkBubble.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        barkBubble.clear();
    }

    // ------------------------------------------------------------------ placement

    public boolean isPlaced(OriginRole role) {
        return placed.containsKey(role);
    }

    public int placedCount() {
        return placed.size();
    }

    public Location location(OriginRole role) {
        Location at = placed.get(role);
        return at == null ? null : at.clone();
    }

    public Location preset(OriginRole role) {
        double[] p = isle.config().castPresets().get(role.id());
        return p == null ? null : isle.config().location(p);
    }

    /** Placed spot, else preset: where the compass points. */
    public Location whereabouts(OriginRole role) {
        Location at = location(role);
        return at != null ? at : preset(role);
    }

    public String place(OriginRole role, Location at) {
        if (at == null || at.getWorld() == null) {
            return "§cNo location for " + role.display() + ".";
        }
        pending.remove(role);
        despawn(role);
        placed.put(role, at.clone());
        save();
        ensure(role);
        return "§a" + role.display() + " §7placed at §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "§7.";
    }

    public String placePreset(OriginRole role) {
        Location preset = preset(role);
        if (preset == null) {
            return "§cNo preset for " + role.display() + " (origin.yml cast.presets." + role.id() + ").";
        }
        return place(role, preset);
    }

    public String placeAllPresets() {
        int count = 0;
        for (OriginRole role : OriginRole.values()) {
            if (preset(role) != null) {
                placePreset(role);
                count++;
            }
        }
        return "§aPlaced §f" + count + "§a Origin townsfolk on their presets.";
    }

    public String remove(OriginRole role) {
        pending.remove(role);
        despawn(role);
        boolean had = placed.remove(role) != null;
        save();
        return had ? "§e" + role.display() + " §7removed." : "§7" + role.display() + " was not placed.";
    }

    public String removeAll() {
        for (OriginRole role : OriginRole.values()) {
            despawn(role);
        }
        int count = placed.size();
        placed.clear();
        pending.clear();
        save();
        return "§eRemoved §f" + count + "§e Origin townsfolk.";
    }

    // ------------------------------------------------------------------ DEV anchors

    public ItemStack anchor(OriginRole role) {
        ItemStack item = new ItemStack(role.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(role.color() + role.display() + " §8(" + role.title() + ")");
            meta.setLore(List.of(
                    "§7DEV · Origin townsfolk anchor.",
                    "§7" + role.role(),
                    "",
                    "§eRight-click a block §7to place here.",
                    "§eSneak + right-click §7removes them.",
                    "§8/origin dev → Cast"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, role.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private OriginRole anchorRole(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return OriginRole.byId(item.getItemMeta().getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        OriginRole role = anchorRole(player.getInventory().getItemInMainHand());
        if (role == null || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (!OriginCommand.dev(player)) {
            return;
        }
        if (player.isSneaking()) {
            player.sendMessage(remove(role));
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getRelative(BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
        at.setYaw(player.getLocation().getYaw() + 180.0f);
        player.sendMessage(place(role, at));
        player.playSound(at, Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.1f);
    }

    // ------------------------------------------------------------------ entities

    private static String fancyName(OriginRole role) {
        return FANCY_PREFIX + role.id();
    }

    private boolean alive(OriginRole role) {
        Spawned spawned = live.get(role);
        if (spawned == null) {
            return false;
        }
        if (spawned.fancyName() != null) {
            try {
                Object manager = FancyNpcFacade.manager();
                return FancyNpcFacade.getNpc(manager, spawned.fancyName()) != null;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return false;
            }
        }
        if (spawned.villager() == null) {
            return false;
        }
        Entity villager = Bukkit.getEntity(spawned.villager());
        return villager != null && villager.isValid();
    }

    private void ensure(OriginRole role) {
        Location at = placed.get(role);
        if (at == null || at.getWorld() == null || alive(role)) {
            return;
        }
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        despawn(role);
        TextDisplay hologram = spawnHologram(role, at);
        if (FancyNpcFacade.isAvailable() && spawnFancy(role, at, hologram.getUniqueId())) {
            return;
        }
        Villager villager = at.getWorld().spawn(at, Villager.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setCollidable(false);
            spawned.setGravity(false);
            spawned.setAdult();
            spawned.setAgeLock(true);
            spawned.setProfession(role.profession());
            spawned.setVillagerType(role.villagerType());
            spawned.setVillagerLevel(5);
            spawned.setRecipes(new ArrayList<MerchantRecipe>());
            spawned.customName(Component.text(role.display()));
            spawned.setCustomNameVisible(false);
            spawned.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role.id());
            if (spawned.getEquipment() != null) {
                spawned.getEquipment().setItemInMainHand(new ItemStack(role.icon()));
            }
        });
        live.put(role, new Spawned(hologram.getUniqueId(), villager.getUniqueId(), null));
    }

    private TextDisplay spawnHologram(OriginRole role, Location at) {
        return at.getWorld().spawn(at.clone().add(0, 2.35, 0), TextDisplay.class, text -> {
            text.text(OriginText.legacy(role.color() + "§l" + role.display() + "\n§7" + role.title() + "\n§e▸ §7" + role.role()));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setViewRange(0.35f);
            text.setPersistent(false);
            text.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role.id());
        });
    }

    private boolean spawnFancy(OriginRole role, Location at, UUID hologramId) {
        String name = fancyName(role);
        try {
            Object existing = FancyNpcFacade.getNpc(FancyNpcFacade.manager(), name);
            if (existing != null) {
                FancyNpcFacade.removeFromPlayersQuiet(existing);
                FancyNpcFacade.unregister(FancyNpcFacade.manager(), existing);
            }
            Object data = FancyNpcFacade.createNpcData(name, new UUID(0L, 0L), at.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.invoke(data, "setTurnToPlayerDistance", int.class, 8);
            FancyNpcFacade.applyVisibility(data, 48);
            FancyNpcFacade.invoke(data, "setInteractionCooldown", float.class, 0.5f);
            FancyNpcFacade.invoke(data, "setSpawnEntity", boolean.class, true);
            applyOriginGear(data, role);

            Object fancy = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(fancy, false);
            FancyNpcFacade.create(fancy);
            FancyNpcFacade.register(FancyNpcFacade.manager(), fancy);
            FancyNpcFacade.spawnForAll(fancy);
            live.put(role, new Spawned(hologramId, null, name));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            isle.plugin().getLogger().warning("Origin FancyNPC spawn failed for " + role.id() + ": " + ex.getMessage());
            return false;
        }
    }

    private static void applyOriginGear(Object data, OriginRole role) throws ReflectiveOperationException {
        Class<?> slotClass = FancyNpcFacade.equipmentSlotClass();
        Method add = data.getClass().getMethod("addEquipment", slotClass, ItemStack.class);
        add.invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), new ItemStack(role.icon()));
        Color dye = leatherColor(role);
        add.invoke(data, FancyNpcFacade.equipmentSlot("CHEST"), dyed(Material.LEATHER_CHESTPLATE, dye));
        add.invoke(data, FancyNpcFacade.equipmentSlot("LEGS"), dyed(Material.LEATHER_LEGGINGS, dye));
        add.invoke(data, FancyNpcFacade.equipmentSlot("FEET"), dyed(Material.LEATHER_BOOTS, dye));
    }

    private static Color leatherColor(OriginRole role) {
        return switch (role) {
            case GUIDE -> Color.fromRGB(180, 120, 50);
            case KEEPER -> Color.fromRGB(70, 140, 170);
            case BELLKEEPER -> Color.fromRGB(200, 180, 90);
            case TENDER -> Color.fromRGB(50, 100, 140);
            case STARGAZER -> Color.fromRGB(140, 80, 160);
        };
    }

    private static ItemStack dyed(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            leather.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(leather);
        }
        return item;
    }

    private void despawn(OriginRole role) {
        Spawned spawned = live.remove(role);
        if (spawned == null) {
            return;
        }
        if (spawned.hologram() != null) {
            Entity hologram = Bukkit.getEntity(spawned.hologram());
            if (hologram != null) {
                hologram.remove();
            }
        }
        if (spawned.villager() != null) {
            Entity villager = Bukkit.getEntity(spawned.villager());
            if (villager != null) {
                villager.remove();
            }
        }
        if (spawned.fancyName() != null && FancyNpcFacade.isAvailable()) {
            try {
                Object fancy = FancyNpcFacade.getNpc(FancyNpcFacade.manager(), spawned.fancyName());
                if (fancy != null) {
                    FancyNpcFacade.removeFromPlayersQuiet(fancy);
                    FancyNpcFacade.unregister(FancyNpcFacade.manager(), fancy);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        if (FancyNpcFacade.isAvailable()) {
            try {
                Object manager = FancyNpcFacade.manager();
                for (OriginRole role : OriginRole.values()) {
                    Object fancy = FancyNpcFacade.getNpc(manager, fancyName(role));
                    if (fancy != null) {
                        FancyNpcFacade.removeFromPlayersQuiet(fancy);
                        FancyNpcFacade.unregister(manager, fancy);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        live.clear();
    }

    private void registerFancyBridge() {
        if (fancyBridge || !FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            Listener marker = new Listener() {
            };
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    OriginRole role = roleFromFancy(click.name());
                    if (role == null || click.player() == null) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Player player = click.player();
                    Bukkit.getScheduler().runTask(isle.plugin(), () -> handleClick(player, role));
                } catch (ReflectiveOperationException ex) {
                    isle.plugin().getLogger().warning("Origin FancyNPC click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(
                    eventClass, marker, EventPriority.NORMAL, executor, isle.plugin(), true);
            fancyBridge = true;
            isle.plugin().getLogger().info("Origin FancyNpcs interact bridge enabled.");
        } catch (ClassNotFoundException ex) {
            isle.plugin().getLogger().warning("FancyNpcs API missing — Origin cast uses villager fallback.");
        }
    }

    private static OriginRole roleFromFancy(String fancyName) {
        if (fancyName == null || !fancyName.startsWith(FANCY_PREFIX)) {
            return null;
        }
        return OriginRole.byId(fancyName.substring(FANCY_PREFIX.length()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isle.running()) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (Map.Entry<OriginRole, Location> entry : placed.entrySet()) {
            Location at = entry.getValue();
            if (at.getWorld() != null && at.getWorld().equals(chunk.getWorld())
                    && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                OriginRole role = entry.getKey();
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(role));
            }
        }
    }

    public OriginRole roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return OriginRole.byId(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        OriginRole role = roleOf(event.getRightClicked());
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleClick(event.getPlayer(), role);
    }

    private void handleClick(Player player, OriginRole role) {
        if (player == null || role == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = clickCool.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        clickCool.put(player.getUniqueId(), now + 400L);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.7f, 1.1f);
        boolean first = !isle.profiles().get(player).met.contains(role.id());
        if (first) {
            say(player, role, role.barks().get(0));
        }
        isle.compass().talked(player, role);
        isle.menus().openBoard(player, role);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (roleOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Lightning would turn a townsperson into an untagged, persistent witch. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onZap(EntityZapEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (roleOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    public void say(Player player, OriginRole role, String line) {
        if (player == null || role == null || line == null || line.isBlank()) {
            return;
        }
        player.sendMessage(role.color() + role.shortName() + " §8» §f" + line);
        Location at = location(role);
        if (at != null) {
            showBarkBubble(player, at, role, line);
        }
    }

    /** Player-only bark bubble above the townsfolk (Hub-owned — not Quests TalkUx). */
    private void showBarkBubble(Player player, Location at, OriginRole role, String line) {
        if (at.getWorld() == null || !player.getWorld().equals(at.getWorld())) {
            return;
        }
        UUID old = barkBubble.remove(player.getUniqueId());
        if (old != null) {
            Entity previous = Bukkit.getEntity(old);
            if (previous != null) {
                previous.remove();
            }
        }
        Location bubbleAt = at.clone().add(0, 2.55, 0);
        String wrapped = wrapBark(role.color() + role.shortName() + "\n§f" + line, 42);
        TextDisplay display = at.getWorld().spawn(bubbleAt, TextDisplay.class, text -> {
            text.setPersistent(false);
            text.setVisibleByDefault(false);
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(160, 12, 14, 22));
            text.setBrightness(new Display.Brightness(15, 15));
            text.setViewRange(0.4f);
            text.text(OriginText.legacy(wrapped));
            text.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(0.85f, 0.85f, 0.85f), new AxisAngle4f()));
        });
        player.showEntity(isle.plugin(), display);
        barkBubble.put(player.getUniqueId(), display.getUniqueId());
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            UUID id = barkBubble.get(player.getUniqueId());
            if (id != null && id.equals(display.getUniqueId())) {
                barkBubble.remove(player.getUniqueId());
            }
            if (display.isValid()) {
                display.remove();
            }
        }, BARK_BUBBLE_TICKS);
    }

    private static String wrapBark(String text, int width) {
        if (text == null || text.length() <= width) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        String[] paragraphs = text.split("\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            if (p > 0) {
                out.append('\n');
            }
            String[] words = paragraphs[p].split(" ");
            int lineLen = 0;
            for (String word : words) {
                int visible = word.replaceAll("§.", "").length();
                if (lineLen > 0 && lineLen + 1 + visible > width) {
                    out.append('\n');
                    lineLen = 0;
                } else if (lineLen > 0) {
                    out.append(' ');
                    lineLen++;
                }
                out.append(word);
                lineLen += visible;
            }
        }
        return out.toString();
    }

    /** A little flourish at an NPC for menu moments. */
    void flourish(OriginRole role, Particle particle, int count) {
        Location at = location(role);
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(particle, at.clone().add(0, 1.2, 0), count, 0.3, 0.4, 0.3, 0.02);
    }

    /** Every 20 ticks from the Origin loop. */
    void tick(List<Player> onIsle) {
        if (++ensureTick % 5 == 0) {
            for (OriginRole role : placed.keySet()) {
                ensure(role);
            }
        }
        if (onIsle.isEmpty() || live.isEmpty()) {
            return;
        }
        Map<OriginRole, Location> spots = new EnumMap<>(OriginRole.class);
        Map<OriginRole, Villager> villagers = new EnumMap<>(OriginRole.class);
        for (Map.Entry<OriginRole, Spawned> entry : live.entrySet()) {
            Location at = placed.get(entry.getKey());
            if (at == null) {
                continue;
            }
            spots.put(entry.getKey(), at);
            if (entry.getValue().villager() != null) {
                Entity entity = Bukkit.getEntity(entry.getValue().villager());
                if (entity instanceof Villager villager && villager.isValid()) {
                    villagers.put(entry.getKey(), villager);
                }
            }
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            boolean quiet = isle.quiet(player);
            for (Map.Entry<OriginRole, Location> entry : spots.entrySet()) {
                Location at = entry.getValue();
                if (at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distance = at.distance(player.getLocation());
                Villager villager = villagers.get(entry.getKey());
                if (villager != null && distance <= TURN_RANGE) {
                    face(villager, player);
                }
                if (!quiet && distance <= BARK_RANGE) {
                    String key = player.getUniqueId() + ":" + entry.getKey().id();
                    Long last = barked.get(key);
                    if (last == null || now - last > BARK_COOLDOWN_MS) {
                        barked.put(key, now);
                        List<String> barks = entry.getKey().barks();
                        say(player, entry.getKey(), barks.get(ThreadLocalRandom.current().nextInt(barks.size())));
                    }
                }
            }
        }
        // The Stargazer's scope glints at night; the Tender's glowberries shimmer.
        if (isle.night()) {
            Location stellan = spots.get(OriginRole.STARGAZER);
            if (stellan != null && stellan.getWorld() != null) {
                stellan.getWorld().spawnParticle(Particle.END_ROD, stellan.clone().add(0, 2.1, 0), 1, 0.15, 0.1, 0.15, 0.0);
            }
            Location fen = spots.get(OriginRole.TENDER);
            if (fen != null && fen.getWorld() != null) {
                fen.getWorld().spawnParticle(Particle.GLOW, fen.clone().add(0, 1.0, 0), 2, 0.4, 0.5, 0.4, 0.0);
            }
        }
    }

    private static void face(Villager villager, Player player) {
        Location from = villager.getLocation();
        double dx = player.getX() - from.getX();
        double dz = player.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (Math.abs(((yaw - from.getYaw()) % 360 + 540) % 360 - 180) > 4.0f) {
            villager.setRotation(yaw, 0.0f);
        }
    }

    void forget(UUID id) {
        clickCool.remove(id);
        String prefix = id + ":";
        barked.keySet().removeIf(key -> key.startsWith(prefix));
        UUID bubble = barkBubble.remove(id);
        if (bubble != null) {
            Entity entity = Bukkit.getEntity(bubble);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private static Location fromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        Location at = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
        at.setYaw((float) section.getDouble("yaw", 0.0d));
        return at;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        seeded = config.getBoolean("seeded", false);
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (String key : roles.getKeys(false)) {
            OriginRole role = OriginRole.byId(key);
            ConfigurationSection section = roles.getConfigurationSection(key);
            if (role == null || section == null) {
                continue;
            }
            Location at = fromSection(section);
            if (at != null) {
                placed.put(role, at);
            } else {
                pending.put(role, section);
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("seeded", seeded);
        pending.forEach((role, section) -> {
            if (!placed.containsKey(role)) {
                config.createSection("roles." + role.id(), section.getValues(false));
            }
        });
        placed.forEach((role, at) -> {
            String path = "roles." + role.id();
            config.set(path + ".world", at.getWorld() == null ? "world" : at.getWorld().getName());
            config.set(path + ".x", at.getX());
            config.set(path + ".y", at.getY());
            config.set(path + ".z", at.getZ());
            config.set(path + ".yaw", at.getYaw());
        });
        try {
            if (!isle.plugin().getDataFolder().exists()) {
                isle.plugin().getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Could not save origin-cast.yml: " + exception.getMessage());
        }
    }
}
