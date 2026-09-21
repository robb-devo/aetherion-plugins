package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.AppearancePreset;
import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorScreen;
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
    private static final int SLIM = 8;
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
                EditorItems.title(player, "npc_appear", "§8Look")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(SKIN, EditorItems.head(
                npc.getSkinUsername(),
                EditorItems.ui(player, "editor_skin", "§eUse a Minecraft skin"),
                "§7" + npc.getSkinUsername(),
                EditorItems.ui(player, "editor_skin_hint", "§7Click and type a username.")
        ));
        inventory.setItem(SLIM, EditorItems.button(
                npc.isSlim() ? Material.LIME_DYE : Material.GRAY_DYE,
                npc.isSlim()
                        ? EditorItems.ui(player, "editor_slim_on", "§aSlim arms")
                        : EditorItems.ui(player, "editor_slim_off", "§7Wide arms"),
                EditorItems.ui(player, "editor_slim_hint", "§7Click to toggle.")
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_presets", "Presets"),
                EditorItems.ui(player, "editor_presets_hint", "§7One click. Outfit + skin hint.")
        ));
        AppearancePreset[] presets = AppearancePreset.values();
        for (int i = 0; i < presets.length && i < 10; i++) {
            AppearancePreset preset = presets[i];
            boolean selected = preset == npc.getPreset();
            inventory.setItem(PRESET_START + i, EditorItems.button(
                    preset.icon(),
                    (selected ? "§a" : "§e") + preset.label(),
                    selected
                            ? EditorItems.ui(player, "editor_selected", "§aSelected")
                            : EditorItems.ui(player, "editor_preset_click", "§7Click to apply")
            ));
        }
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        Player player = EditorItems.editorClick(event);
        if (player == null) {
            return;
        }
        CustomNpc npc = editor.storage().get(holder.npcId());
        if (npc == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == EditorItems.BACK) {
            EditMenu.open(player, npc);
            return;
        }
        if (slot == SKIN) {
            editor.sessions().of(player).setNpcId(npc.getId());
            editor.prompt(player, EditorSessions.Prompt.SKIN, EditorScreen.APPEARANCE,
                    EditorItems.ui(player, "editor_prompt_skin", "Type a Minecraft username for the skin"),
                    npc.getSkinUsername());
            return;
        }
        if (slot == SLIM) {
            npc.setSlim(!npc.isSlim());
            editor.persist(npc);
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
