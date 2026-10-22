package de.aetherion.helm.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Turns an ItemStack into what the app needs to draw the slot and its tooltip, colours and all. */
final class Items {
    private Items() {}

    static JsonObject item(ItemStack it, int slot, int depth) {
        if (it == null || it.getType().isAir() || it.getAmount() <= 0) return null;
        JsonObject o = new JsonObject();
        if (slot >= 0) o.addProperty("slot", slot);
        o.addProperty("id", it.getType().getKey().getKey());
        o.addProperty("count", it.getAmount());
        if (!it.hasItemMeta()) return o;
        ItemMeta m = it.getItemMeta();
        if (m.hasDisplayName()) o.add("name", component(m.displayName()));
        else if (m.hasItemName()) o.add("name", component(m.itemName()));
        List<Component> lore = m.lore();
        if (lore != null && !lore.isEmpty()) {
            JsonArray l = new JsonArray();
            for (Component c : lore) l.add(component(c));
            o.add("lore", l);
        }
        Map<Enchantment, Integer> ench = m.getEnchants();
        if (!ench.isEmpty()) {
            JsonArray e = new JsonArray();
            ench.forEach((k, v) -> {
                JsonObject x = new JsonObject();
                x.addProperty("id", k.getKey().getKey());
                x.addProperty("lvl", v);
                e.add(x);
            });
            o.add("ench", e);
        }
        boolean glint = !ench.isEmpty();
        if (m.hasEnchantmentGlintOverride()) glint = Boolean.TRUE.equals(m.getEnchantmentGlintOverride());
        if (glint) o.addProperty("glint", true);
        if (m.hasCustomModelData()) o.addProperty("cmd", m.getCustomModelData());
        if (m.isUnbreakable()) o.addProperty("unbreakable", true);
        if (m.hasRarity()) o.addProperty("rarity", m.getRarity().name().toLowerCase());
        if (m instanceof Damageable d && it.getType().getMaxDurability() > 0) {
            o.addProperty("dmg", d.getDamage());
            o.addProperty("maxDmg", d.hasMaxDamage() ? d.getMaxDamage() : it.getType().getMaxDurability());
        }
        if (depth == 0) {
            JsonArray inner = null;
            if (m instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
                inner = list(box.getInventory().getContents(), 1);
            } else if (m instanceof BundleMeta bm && bm.hasItems()) {
                inner = new JsonArray();
                int i = 0;
                for (ItemStack x : bm.getItems()) {
                    JsonObject j = item(x, i++, 1);
                    if (j != null) inner.add(j);
                }
            }
            if (inner != null && !inner.isEmpty()) o.add("contents", inner);
        }
        return o;
    }

    static JsonArray list(ItemStack[] items, int depth) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < items.length; i++) {
            JsonObject j = item(items[i], i, depth);
            if (j != null) a.add(j);
        }
        return a;
    }

    static com.google.gson.JsonElement component(Component c) {
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(c));
    }
}
