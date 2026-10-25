package de.aetherion.hub.origin;

import de.aetherion.core.npc.FancyNpcFacade;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A FancyNpcs person that code moves around: the boatmen of the bay, the hand in the crane's lighter, the pier
 * hand on the sky pier. Never saved to FancyNpcs' file; without FancyNpcs nothing spawns and every call is a no-op.
 */
final class OriginHand {

    private final String name;
    private Object npc;
    private boolean warned;

    OriginHand(String name) {
        this.name = name;
    }

    boolean alive() {
        return npc != null;
    }

    /** Spawns (or re-spawns) the person at {@code at}, dressed in {@code coat} with {@code hand} in the main hand. */
    boolean spawn(Location at, Color coat, ItemStack hand, Logger log) {
        despawn();
        if (at == null || at.getWorld() == null || !FancyNpcFacade.isAvailable()) {
            return false;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (manager == null) {
                return false;
            }
            Object existing = FancyNpcFacade.getNpc(manager, name);
            if (existing != null) {
                FancyNpcFacade.removeFromPlayersQuiet(existing);
                FancyNpcFacade.unregister(manager, existing);
            }
            Object data = FancyNpcFacade.createNpcData(name, new UUID(0L, 0L), at.clone());
            FancyNpcFacade.invokeQuiet(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setSpawnEntity", boolean.class, true);
            FancyNpcFacade.applyVisibility(data, 96);
            Class<?> slots = FancyNpcFacade.equipmentSlotClass();
            Method add = data.getClass().getMethod("addEquipment", slots, ItemStack.class);
            add.invoke(data, FancyNpcFacade.equipmentSlot("HEAD"), dyed(Material.LEATHER_HELMET, coat));
            add.invoke(data, FancyNpcFacade.equipmentSlot("CHEST"), dyed(Material.LEATHER_CHESTPLATE, coat));
            add.invoke(data, FancyNpcFacade.equipmentSlot("LEGS"), dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(70, 58, 44)));
            add.invoke(data, FancyNpcFacade.equipmentSlot("FEET"), dyed(Material.LEATHER_BOOTS, Color.fromRGB(52, 40, 30)));
            add.invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), hand == null ? new ItemStack(Material.AIR) : hand);
            Object made = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(made, false);
            FancyNpcFacade.create(made);
            FancyNpcFacade.register(manager, made);
            FancyNpcFacade.spawnForAll(made);
            npc = made;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (!warned && log != null) {
                warned = true;
                log.warning("Origin bay: FancyNpcs person '" + name + "' failed: " + ex);
            }
            return false;
        }
    }

    /** Puts the person at {@code at} for everyone who sees them (the client glides there). */
    void move(Location at, boolean swing) {
        if (npc == null || at == null) {
            return;
        }
        try {
            FancyNpcFacade.setLocation(FancyNpcFacade.data(npc), at);
            FancyNpcFacade.moveForAll(npc, swing);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // a missed frame; the next tick moves them again
        }
    }

    /** Swaps what the person holds in the main hand. */
    void hold(ItemStack item) {
        if (npc == null) {
            return;
        }
        try {
            Object data = FancyNpcFacade.data(npc);
            Method add = data.getClass().getMethod("addEquipment", FancyNpcFacade.equipmentSlotClass(), ItemStack.class);
            add.invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), item == null ? new ItemStack(Material.AIR) : item);
            FancyNpcFacade.updateForAll(npc);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    void despawn() {
        if (npc == null) {
            return;
        }
        Object gone = npc;
        npc = null;
        FancyNpcFacade.removeFromPlayersQuiet(gone);
        try {
            FancyNpcFacade.unregister(FancyNpcFacade.manager(), gone);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static ItemStack dyed(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            leather.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(leather);
        }
        return item;
    }
}
