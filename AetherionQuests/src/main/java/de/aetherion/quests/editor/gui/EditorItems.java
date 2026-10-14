package de.aetherion.quests.editor.gui;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import de.aetherion.quests.editor.NpcEditor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Item + text building blocks for every studio screen, so they all look and read the same.
 * <p>
 * Style: names carry the role colour (§a create/confirm, §e edit, §b info, §c danger, §7 navigation),
 * lore starts with a short gray explanation, then the current value, then the controls.
 * Glint always means "selected / you are here".
 */
public final class EditorItems {

    public static final String BULLET = "§8• ";
    /** Shared footer slot for Hypixel-style designer chests (QuestHub / Rewards / …). */
    public static final int BACK = 49;

    private static NamespacedKey hideKey;

    private EditorItems() {
    }

    /** Double-chest chrome: gray fill, dark header/footer. */
    public static void chrome(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ItemStack fill = pane(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack dark = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            boolean edge = i < 9 || i >= inventory.getSize() - 9;
            inventory.setItem(i, (edge ? dark : fill).clone());
        }
    }

    public static ItemStack section(String name, String... lore) {
        return icon(Material.ORANGE_STAINED_GLASS_PANE).name("§6§l" + (name == null ? "" : name)).lore(lore).build();
    }

    public static ItemStack back(Player player) {
        return button(Material.ARROW, "§7Back", "§8Return to the previous screen");
    }

    public static ItemStack close(Player player) {
        return button(Material.BARRIER, "§cClose");
    }

    /** Title / copy helper — english fallback (DE overlay optional later). */
    public static String title(Player player, String key, String english) {
        return ui(player, key, english);
    }

    public static String ui(Player player, String key, String english) {
        return english == null ? "" : english;
    }

    /** Cancel click + only allow the editing player. */
    public static Player editorClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return null;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return null;
        }
        if (!NpcEditor.allowed(player)) {
            return null;
        }
        return player;
    }

    public static Builder icon(Material material) {
        return new Builder(new ItemStack(material == null ? Material.PAPER : material));
    }

    public static Builder icon(ItemStack base) {
        return new Builder(base == null ? new ItemStack(Material.PAPER) : base.clone());
    }

    public static ItemStack pane(Material material) {
        return icon(material).name(" ").build();
    }

    public static ItemStack pane(Material material, String name, String... lore) {
        return icon(material).name(name).lore(lore).build();
    }

    public static ItemStack filler() {
        return pane(Material.BLACK_STAINED_GLASS_PANE);
    }

    public static void fill(Inventory inventory) {
        ItemStack pane = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    public static ItemStack button(Material material, String name, String... lore) {
        return icon(material).name(name).lore(lore).build();
    }

    public static ItemStack head(String owner, String name, String... lore) {
        return icon(Material.PLAYER_HEAD).skull(owner).name(name).lore(lore).build();
    }

    /** Leather piece dyed in a colour (outfit swatches, Look tab). */
    public static ItemStack dyed(Material leatherPiece, Color color) {
        ItemStack stack = new ItemStack(leatherPiece);
        if (color != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------ text helpers

    /** Word-wraps plain text to lines of at most {@code width} characters (long words are kept whole). */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Wrapped lines, each prefixed with {@code color}. */
    public static List<String> paragraph(String color, String text, int width) {
        List<String> out = new ArrayList<>();
        for (String line : wrap(text, width)) {
            out.add(color + line);
        }
        return out;
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    public static String quote(String text, int max) {
        return "\"" + truncate(text, max) + "\"";
    }

    public static String plural(int count, String word) {
        return count + " " + word + (count == 1 ? "" : "s");
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    public static String ago(long epochMillis) {
        if (epochMillis <= 0L) {
            return "a while ago";
        }
        long seconds = Math.max(0L, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " h ago";
        }
        return (hours / 24) + " days ago";
    }

    private static NamespacedKey hideKey() {
        if (hideKey == null && AetherionQuests.getInstance() != null) {
            hideKey = new NamespacedKey(AetherionQuests.getInstance(), "studio_ui");
        }
        return hideKey;
    }

    /** Strips vanilla tooltip noise (attack damage, dye, enchant lines…) from a display item. */
    private static void hideVanilla(ItemMeta meta) {
        NamespacedKey key = hideKey();
        if (key != null) {
            try {
                // An explicit modifier replaces the default "When in Main Hand" block, which then gets hidden.
                meta.addAttributeModifier(Attribute.GENERIC_LUCK,
                        new AttributeModifier(key, 0.0, AttributeModifier.Operation.ADD_NUMBER));
            } catch (IllegalArgumentException ignored) {
            }
        }
        meta.addItemFlags(ItemFlag.values());
    }

    public static final class Builder {
        private final ItemStack stack;
        private String name;
        private final List<String> lore = new ArrayList<>();
        private boolean glow;
        private String skullOwner;
        private int amount = 1;

        private Builder(ItemStack stack) {
            this.stack = stack;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(String... lines) {
            if (lines != null) {
                for (String line : lines) {
                    addLine(line);
                }
            }
            return this;
        }

        public Builder lore(Collection<String> lines) {
            if (lines != null) {
                for (String line : lines) {
                    addLine(line);
                }
            }
            return this;
        }

        /** Gray, word-wrapped explanation. */
        public Builder text(String text) {
            return lore(paragraph("§7", text, 36));
        }

        /** Empty spacer line (never doubled, never first). */
        public Builder blank() {
            if (!lore.isEmpty() && !lore.get(lore.size() - 1).isEmpty()) {
                lore.add("");
            }
            return this;
        }

        /** Control hint: {@code §eClick §7to edit}. */
        public Builder click(String input, String what) {
            lore.add("§e" + input + " §7" + what);
            return this;
        }

        /** Destructive control hint: {@code §cPress Q §7to delete}. */
        public Builder danger(String input, String what) {
            lore.add("§c" + input + " §7" + what);
            return this;
        }

        public Builder glow(boolean glow) {
            this.glow = glow;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, Math.min(64, amount));
            return this;
        }

        public Builder skull(String owner) {
            this.skullOwner = owner;
            return this;
        }

        private void addLine(String line) {
            if (line == null) {
                return;
            }
            if (line.isEmpty()) {
                lore.add("");
                return;
            }
            // Lines without a leading colour code render purple + italic.
            lore.add(line.startsWith("§") ? line : "§7" + line);
        }

        @SuppressWarnings("deprecation")
        public ItemStack build() {
            ItemStack item = stack.clone();
            item.setAmount(amount);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
            String display = name == null ? " " : name;
            meta.setDisplayName(display.isBlank() || display.startsWith("§") ? display : "§f" + display);
            List<String> lines = new ArrayList<>(lore);
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            if (!lines.isEmpty()) {
                meta.setLore(lines);
            }
            hideVanilla(meta);
            meta.setEnchantmentGlintOverride(glow);
            if (skullOwner != null && !skullOwner.isBlank() && meta instanceof SkullMeta skull) {
                try {
                    skull.setOwner(skullOwner);
                } catch (IllegalArgumentException ignored) {
                }
            }
            item.setItemMeta(meta);
            return item;
        }
    }
}
