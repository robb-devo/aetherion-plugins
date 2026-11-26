package de.aetherion.pit.hub;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Hub prop models in the served pack (AetherionItems/tools/gen_hub_props.py): disc_fragment_5 + CustomModelData. */
public final class HubModels {

    public static final Material BASE = Material.DISC_FRAGMENT_5;
    public static final int SCROLL_PAPER = 8101;
    public static final int SCROLL_ROLLER = 8102;
    public static final int LAMP_POST = 8103;
    public static final int SIGN_BOARD = 8104;
    public static final int DUCKLING = 8105;
    public static final int CRYSTAL = 8106;
    public static final int SIGN_POST = 8107;
    public static final int RUNE_RING = 8108;
    public static final int SCROLL_PAPER_DARK = 8109;

    private HubModels() {
    }

    public static ItemStack item(int code) {
        ItemStack stack = new ItemStack(BASE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(code);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
