package net.aetherion.samuraiboss.render;

import net.aetherion.samuraiboss.Settings;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Erzeugt Items mit custom_model_data für die Modelle aus dem Resource Pack.
 * In 1.21.1 ist custom_model_data (Integer) + "overrides" im Item-Modell die korrekte Art,
 * eigene Modelle zuzuweisen (die item_model-Komponente gibt es erst ab 1.21.2).
 */
public final class PackItems {

    private PackItems() {
    }

    public static ItemStack item(Settings s, String key) {
        ItemStack st = new ItemStack(s.packBaseMaterial);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(s.modelData(key));
            st.setItemMeta(meta);
        }
        return st;
    }
}
