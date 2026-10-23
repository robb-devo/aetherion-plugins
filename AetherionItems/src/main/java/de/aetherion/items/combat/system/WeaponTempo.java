package de.aetherion.items.combat.system;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Attack speed per weapon class.
 * <p>
 * Aetherion items carry a hidden attribute modifier so their vanilla tooltip stays hidden, and any item-level
 * modifier drops vanilla's default ones, attack speed included. Every Aetherion weapon therefore swung at the
 * bare-hand 4.0: the attack indicator was full again after five ticks and click-spam lost nothing. This puts one
 * player-level modifier on ATTACK_SPEED that brings the held weapon to its class speed (dagger fast, greataxe
 * slow), so the vanilla indicator and {@link MeleeCadence}'s charge both follow the weapon.
 * <p>
 * Left alone: non-Aetherion items (vanilla defaults still apply to them), items with their own ATTACK_SPEED
 * modifier, bows, crossbows and wands, the exempt items, and anything that is not a weapon or tool.
 */
final class WeaponTempo {

    private final CombatSystem system;
    private final NamespacedKey key = ItemKeys.key("weapon_tempo");
    /** Last tempo named in the action bar per player, so a switch only speaks up when the class changes. */
    private final Map<UUID, Double> named = new HashMap<>();

    WeaponTempo(CombatSystem system) {
        this.system = system;
    }

    /** Brings the player's attack speed in line with the held item; does nothing when it already matches. */
    void refresh(Player player) {
        AttributeInstance attack = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attack == null) {
            return;
        }
        double want = target(player);
        double amount = Double.isNaN(want) ? 0.0 : want - attack.getBaseValue();
        AttributeModifier current = find(attack);
        double has = current == null ? 0.0 : current.getAmount();
        if (Math.abs(has - amount) < 1.0E-4) {
            return;
        }
        if (current != null) {
            attack.removeModifier(current);
        }
        if (Math.abs(amount) >= 1.0E-4) {
            attack.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /**
     * After a hotbar switch: an old copy of the held weapon gets its {@code ⚡ Tempo} line (items from before the
     * tempo only got it on the next join), and the action bar names the tempo whenever the weapon class changes.
     */
    void announce(Player player) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = hand.getType().isAir() ? null : system.heldItemId(player);
        String line = id == null ? null : CombatSystem.tempoLine(hand, id);
        if (line == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore != null && lore.stream().anyMatch(WeaponTempo::damageLine)
                && lore.stream().noneMatch(l -> l != null && ChatColor.stripColor(l).contains("Tempo:"))) {
            system.items().refreshPlayerItems(player);
            player.updateInventory();
        }
        double speed = speedOf(hand, id);
        Double last = named.put(player.getUniqueId(), speed);
        if (last == null || Math.abs(last - speed) > 1.0E-4) {
            String plain = ChatColor.stripColor(line);
            system.feedback().plain(player, plain + " · strike when the crosshair is full",
                    plain + " · zuschlagen, wenn das Fadenkreuz voll ist", 50);
        }
    }

    /** The tooltip puts the tempo under the damage line; without one it never shows, so there is nothing to fix. */
    private static boolean damageLine(String line) {
        String plain = line == null ? "" : ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return plain.contains("damage:") || plain.contains("schaden:");
    }

    /** Quit or kit off: the modifier is saved with the player, so take it off. */
    void clear(Player player) {
        named.remove(player.getUniqueId());
        AttributeInstance attack = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        AttributeModifier current = attack == null ? null : find(attack);
        if (current != null) {
            attack.removeModifier(current);
        }
    }

    private AttributeModifier find(AttributeInstance attack) {
        for (AttributeModifier modifier : attack.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    /** Swings per second for the held item, or NaN to leave the attribute alone. */
    private double target(Player player) {
        CombatConfig cfg = system.config();
        if (cfg.worldDisabled(player.getWorld().getName())) {
            return Double.NaN;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = hand.getType().isAir() ? null : system.heldItemId(player);
        return id == null ? Double.NaN : speedOf(hand, id);
    }

    /** Class speed of an Aetherion item, or NaN when the tempo leaves it alone (also drives the tooltip line). */
    double speedOf(ItemStack item, String id) {
        CombatConfig cfg = system.config();
        if (!cfg.enabled || !cfg.melee || !cfg.tempo) {
            return Double.NaN;
        }
        if (id == null || id.isBlank() || system.isRangedOrWand(item) || cfg.exempt(id)) {
            return Double.NaN;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasAttributeModifiers()) {
            Collection<AttributeModifier> own = meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_SPEED);
            if (own != null && !own.isEmpty()) {
                return Double.NaN;
            }
        }
        String kind = kind(item.getType(), id.toLowerCase(Locale.ROOT));
        if (kind.equals("other") && !system.isWeapon(item)) {
            return Double.NaN;
        }
        Double speed = cfg.tempoSpeeds.get(kind);
        return speed == null ? Double.NaN : speed;
    }

    /** Weapon class from the item id first (a "dagger" on an iron sword is still a dagger), then the material. */
    static String kind(Material type, String id) {
        if (has(id, "dagger", "knife", "kunai", "dirk", "shiv", "stiletto")) {
            return "dagger";
        }
        if (has(id, "greataxe", "great_axe", "greatsword", "great_sword", "warhammer", "hammer", "maul", "cleaver", "mace")) {
            return "heavy";
        }
        if (has(id, "spear", "halberd", "glaive", "trident")) {
            return "spear";
        }
        if (has(id, "katana", "rapier", "saber", "sabre", "scimitar")) {
            return "blade";
        }
        if (has(id, "scythe", "sickle")) {
            return "scythe";
        }
        String name = type.name();
        if (type == Material.MACE) {
            return "mace";
        }
        if (type == Material.TRIDENT) {
            return "spear";
        }
        if (name.endsWith("_SWORD")) {
            return "sword";
        }
        if (name.endsWith("_AXE")) {
            return "axe";
        }
        if (name.endsWith("_HOE")) {
            return "scythe";
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")) {
            return "tool";
        }
        return "other";
    }

    private static boolean has(String id, String... words) {
        for (String word : words) {
            if (id.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
