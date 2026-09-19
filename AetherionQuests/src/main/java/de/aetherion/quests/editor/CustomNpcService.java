package de.aetherion.quests.editor;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.MojangSkinFetcher;
import de.aetherion.quests.npc.NpcNametags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FancyNpcs spawn / move / remove for moderator NPCs. Same stack as living hosts.
 */
public final class CustomNpcService {

    public static final String FANCY_PREFIX = "ae_editor_";
    private static final String HOLO_TAG = "ae_editor_holo";
    private static final AtomicInteger SKIN_SLOT = new AtomicInteger();

    private final AetherionQuests plugin;
    private final CustomNpcStorage storage;
    private final NamespacedKey nameKey;
    private final Map<String, UUID> holograms = new ConcurrentHashMap<>();

    public CustomNpcService(AetherionQuests plugin, CustomNpcStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.nameKey = new NamespacedKey(plugin, "editor_npc_name");
    }

    public boolean available() {
        return FancyNpcFacade.isAvailable();
    }

    public int visibilityDistance() {
        return Math.max(16, plugin.getConfig().getInt("editor-npc-visibility-distance", 48));
    }

    public static String fancyName(String npcId) {
        return FANCY_PREFIX + npcId.toLowerCase(Locale.ROOT);
    }

    public static String idFromFancyName(String fancyName) {
        if (fancyName == null || !fancyName.startsWith(FANCY_PREFIX)) {
            return null;
        }
        return fancyName.substring(FANCY_PREFIX.length());
    }

    public CustomNpc spawn(CustomNpc npc) {
        if (npc == null) {
            return null;
        }
        Location at = npc.location();
        if (at == null || at.getWorld() == null) {
            return null;
        }
        if (!available()) {
            plugin.getLogger().warning("FancyNpcs missing — cannot spawn editor NPC " + npc.getId());
            return null;
        }
        removeLive(npc.getId());
        try {
            String fancy = fancyName(npc.getId());
            Object data = FancyNpcFacade.createNpcData(fancy, new UUID(0L, 0L), at.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.invoke(data, "setTurnToPlayerDistance", int.class, 6);
            FancyNpcFacade.applyVisibility(data, visibilityDistance());
            FancyNpcFacade.invoke(data, "setInteractionCooldown", float.class, 0.4f);
            FancyNpcFacade.invoke(data, "setSpawnEntity", boolean.class, true);
            applyGear(data, npc.getPreset());

            Object fancyNpc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(fancyNpc, false);
            FancyNpcFacade.create(fancyNpc);
            Object manager = FancyNpcFacade.manager();
            FancyNpcFacade.register(manager, fancyNpc);
            FancyNpcFacade.spawnForAll(fancyNpc);
            scheduleSkin(fancyNpc, npc);
            hideVanilla(fancyNpc);
            ensureHologram(npc, at);
            return npc;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Editor NPC spawn failed for " + npc.getId() + ": " + ex.getMessage());
            return null;
        }
    }

    public void refresh(CustomNpc npc) {
        if (npc == null) {
            return;
        }
        spawn(npc);
    }

    public boolean move(CustomNpc npc, Location location) {
        if (npc == null || location == null || location.getWorld() == null) {
            return false;
        }
        npc.setLocation(location);
        storage.save(npc);
        if (!available()) {
            return true;
        }
        Object fancy = findFancy(npc.getId());
        if (fancy == null) {
            return spawn(npc) != null;
        }
        try {
            Object data = FancyNpcFacade.data(fancy);
            FancyNpcFacade.setLocation(data, location.clone());
            FancyNpcFacade.applyVisibility(data, visibilityDistance());
            FancyNpcFacade.moveForAll(fancy, false);
            FancyNpcFacade.updateForAll(fancy, false);
            ensureHologram(npc, location);
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Editor NPC move failed: " + ex.getMessage());
            return spawn(npc) != null;
        }
    }

    public boolean removeLive(String npcId) {
        boolean removed = false;
        if (available() && npcId != null) {
            try {
                Object existing = findFancy(npcId);
                if (existing != null) {
                    FancyNpcFacade.removeForAll(existing);
                    FancyNpcFacade.unregister(FancyNpcFacade.manager(), existing);
                    removed = true;
                }
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().warning("Editor NPC remove failed: " + ex.getMessage());
            }
        }
        removeHologram(npcId);
        return removed;
    }

    public void restoreAll() {
        if (!available()) {
            plugin.getLogger().warning("FancyNpcs missing — editor NPCs will not restore.");
            return;
        }
        int spawned = 0;
        for (CustomNpc npc : storage.all()) {
            if (npc.location() == null) {
                continue;
            }
            if (spawn(npc) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            plugin.getLogger().info("Restored " + spawned + " editor FancyNPC(s).");
        }
    }

    public CustomNpc nearby(Player player, double radius) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        Location here = player.getLocation();
        return storage.all().stream()
                .filter(npc -> npc.location() != null
                        && npc.location().getWorld() != null
                        && npc.location().getWorld().equals(here.getWorld()))
                .filter(npc -> npc.location().distanceSquared(here) <= radius * radius)
                .min(Comparator.comparingDouble(npc -> npc.location().distanceSquared(here)))
                .orElse(null);
    }

    public List<CustomNpc> inWorld(World world) {
        List<CustomNpc> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (CustomNpc npc : storage.all()) {
            if (world.getName().equalsIgnoreCase(npc.getWorld())) {
                out.add(npc);
            }
        }
        return out;
    }

    private Object findFancy(String npcId) {
        if (!available() || npcId == null) {
            return null;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            String expected = fancyName(npcId);
            Object found = FancyNpcFacade.tryGetNpc(manager, expected);
            if (found != null) {
                return found;
            }
            for (Object npc : FancyNpcFacade.allNpcs(manager)) {
                String name = FancyNpcFacade.nameOf(npc);
                if (expected.equalsIgnoreCase(name)) {
                    return npc;
                }
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Editor NPC lookup failed: " + ex.getMessage());
        }
        return null;
    }

    private void applyGear(Object data, AppearancePreset preset) throws ReflectiveOperationException {
        Class<?> slotClass = FancyNpcFacade.equipmentSlotClass();
        Method add = data.getClass().getMethod("addEquipment", slotClass, ItemStack.class);
        ItemStack hand = preset.handItem();
        if (hand != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), hand);
        }
        ItemStack chest = preset.chestItem();
        if (chest != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("CHEST"), chest);
        }
        ItemStack legs = preset.legsItem();
        if (legs != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("LEGS"), legs);
        }
        ItemStack boots = preset.bootsItem();
        if (boots != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("FEET"), boots);
        }
    }

    private void scheduleSkin(Object fancy, CustomNpc npc) {
        if (fancy == null || npc == null) {
            return;
        }
        String username = npc.getSkinUsername();
        if (username == null || username.isBlank()) {
            return;
        }
        long delay = 4L + (SKIN_SLOT.getAndIncrement() % 20) * 6L;
        boolean slim = npc.isSlim();
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            MojangSkinFetcher.Textures textures = MojangSkinFetcher.fetch(username);
            if (textures == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyTextures(fancy, username, textures, slim));
        }, delay);
    }

    private void applyTextures(
            Object fancy,
            String username,
            MojangSkinFetcher.Textures textures,
            boolean slim
    ) {
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            Object variant = Enum.valueOf(variantClass.asSubclass(Enum.class), slim ? "SLIM" : "AUTO");
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skinData = skinDataClass
                    .getConstructor(String.class, variantClass, String.class, String.class)
                    .newInstance(username, variant, textures.value(), textures.signature());
            Object data = FancyNpcFacade.data(fancy);
            data.getClass().getMethod("setSkinData", skinDataClass).invoke(data, skinData);
            FancyNpcFacade.updateForAll(fancy);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Object data = FancyNpcFacade.data(fancy);
                data.getClass().getMethod("setSkin", String.class).invoke(data, username);
                FancyNpcFacade.updateForAll(fancy);
            } catch (ReflectiveOperationException ignoredAgain) {
            }
        }
    }

    private void hideVanilla(Object fancyNpc) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
                if (entity instanceof Entity bukkit) {
                    NpcNametags.hide(bukkit);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }, 5L);
    }

    private void ensureHologram(CustomNpc npc, Location at) {
        if (npc == null || at == null || at.getWorld() == null) {
            return;
        }
        removeHologram(npc.getId());
        Location textAt = at.clone().add(0, 2.15, 0);
        float view = Math.max(0.4f, visibilityDistance() / 64.0f);
        TextDisplay holo = at.getWorld().spawn(textAt, TextDisplay.class, text -> {
            text.text(Component.text(npc.getName(), NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(npc.getSubtitle(), NamedTextColor.GRAY)));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setSeeThrough(false);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(40, 0, 0, 0));
            text.setViewRange(view);
            text.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            text.addScoreboardTag(HOLO_TAG);
            text.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, npc.getId());
            text.setPersistent(false);
        });
        holograms.put(npc.getId().toLowerCase(Locale.ROOT), holo.getUniqueId());
    }

    private void removeHologram(String npcId) {
        if (npcId == null) {
            return;
        }
        UUID id = holograms.remove(npcId.toLowerCase(Locale.ROOT));
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        CustomNpc npc = storage.get(npcId);
        Location at = npc == null ? null : npc.location();
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at.clone().add(0, 2, 0), 2.5, 3, 2.5)) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(HOLO_TAG)) {
                String tagged = display.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
                if (npcId.equalsIgnoreCase(tagged) || tagged == null) {
                    display.remove();
                }
            }
        }
    }

    public void shutdown() {
        for (String id : List.copyOf(holograms.keySet())) {
            removeHologram(id);
        }
    }
}
