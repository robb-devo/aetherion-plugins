package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.AppearancePreset;
import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AppearanceMenu implements Listener {

    private static final int SKIN = 4;
    private static final int SLIM = 6;
    private static final int BACK = 49;
    private static final int PRESET_START = 19;

    private final NpcEditor editor;

    public AppearanceMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_appear", "§8Appearance")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(SKIN, EditorItems.head(
                npc.getSkinUsername(),
                "§eSkin username",
                "§7Currently §f" + npc.getSkinUsername(),
                "§7Click and type a Mojang name."
        ));
        inventory.setItem(SLIM, EditorItems.button(
                npc.isSlim() ? Material.LIME_DYE : Material.GRAY_DYE,
                npc.isSlim() ? "§aSlim arms" : "§7Wide arms",
                "§7Click to toggle."
        ));
        inventory.setItem(8, EditorItems.saved(false));
        inventory.setItem(18, EditorItems.section("Presets", "§7Leather look + skin hint."));
        AppearancePreset[] presets = AppearancePreset.values();
        for (int i = 0; i < presets.length && i < 18; i++) {
            AppearancePreset preset = presets[i];
            boolean selected = preset == npc.getPreset();
            inventory.setItem(PRESET_START + i, EditorItems.button(
                    preset.icon(),
                    (selected ? "§a" : "§e") + preset.label(),
                    "§7Skin hint §f" + preset.skinUsername(),
                    selected ? "§aSelected" : "§7Click to apply"
            ));
        }
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !NpcEditor.allowed(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        CustomNpc npc = editor.storage().get(holder.npcId());
        if (npc == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == SKIN) {
            editor.prompt(player, EditorSessions.Prompt.SKIN, "Type a Minecraft username for the skin");
            return;
        }
        if (slot == SLIM) {
            npc.setSlim(!npc.isSlim());
            editor.persist(npc);
            editor.sessions().of(player).setDirty(false);
            open(player, npc);
            return;
        }
        int presetIndex = slot - PRESET_START;
        AppearancePreset[] presets = AppearancePreset.values();
        if (presetIndex >= 0 && presetIndex < presets.length) {
            AppearancePreset preset = presets[presetIndex];
            npc.setPreset(preset);
            npc.setSlim(preset.slim());
            npc.setSkinUsername(preset.skinUsername());
            editor.persist(npc);
            editor.sessions().of(player).setDirty(false);
            player.sendMessage("§aAppearance §f" + preset.label());
            open(player, npc);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
