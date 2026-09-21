package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorScreen;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.NpcMode;
import de.aetherion.quests.model.Quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EditMenu implements Listener {

    private static final int HEAD = 4;
    private static final int NAME = 10;
    private static final int SUBTITLE = 11;
    private static final int APPEAR = 13;
    private static final int DIALOGUE = 20;
    private static final int MODE = 28;
    private static final int QUEST = 30;
    private static final int MOVE = 37;
    private static final int LOOK = 38;
    private static final int DUPLICATE = 39;
    private static final int DELETE = 43;

    private final NpcEditor editor;

    public EditMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_edit", "§8Edit NPC")
        );
        EditorItems.chrome(inventory);
        String firstLine = firstLine(npc);
        inventory.setItem(HEAD, EditorItems.head(
                npc.getSkinUsername(),
                "§b" + npc.getName(),
                "§7" + npc.getSubtitle(),
                npc.isQuestNpc()
                        ? EditorItems.ui(player, "editor_mode_quest", "§aGives a quest")
                        : EditorItems.ui(player, "editor_mode_talk", "§bJust talks"),
                npc.hasLinkedQuest()
                        ? "§7Quest §f" + npc.getLinkedQuestId()
                        : EditorItems.ui(player, "editor_no_job", "§8No quest yet"),
                "§8" + npc.getId()
        ));
        inventory.setItem(9, EditorItems.section(
                EditorItems.ui(player, "editor_sec_identity", "Identity"),
                EditorItems.ui(player, "editor_sec_identity_hint", "§7Name and the small line under it.")
        ));
        inventory.setItem(NAME, EditorItems.button(
                Material.NAME_TAG,
                EditorItems.ui(player, "editor_rename", "§eRename"),
                "§7" + npc.getName(),
                EditorItems.ui(player, "editor_click_chat", "§8Click · type in chat")
        ));
        inventory.setItem(SUBTITLE, EditorItems.button(
                Material.PAPER,
                EditorItems.ui(player, "editor_subtitle", "§eSubtitle"),
                "§7" + npc.getSubtitle(),
                EditorItems.ui(player, "editor_subtitle_hint", "§8Small line under the name")
        ));
        inventory.setItem(12, EditorItems.section(
                EditorItems.ui(player, "editor_sec_look", "Look"),
                EditorItems.ui(player, "editor_sec_look_hint", "§7Skin, outfit, arms.")
        ));
        inventory.setItem(APPEAR, EditorItems.button(
                Material.LEATHER_CHESTPLATE,
                EditorItems.ui(player, "editor_appear", "§6Appearance"),
                "§7" + npc.getPreset().label() + " · " + npc.getSkinUsername(),
                EditorItems.ui(player, "editor_appear_hint", "§8Presets or a Minecraft name")
        ));
        inventory.setItem(18, EditorItems.section(
                EditorItems.ui(player, "editor_sec_dialogue", "Dialogue"),
                EditorItems.ui(player, "editor_sec_dialogue_hint", "§7What they say and player replies.")
        ));
        inventory.setItem(DIALOGUE, EditorItems.button(
                Material.WRITABLE_BOOK,
                EditorItems.ui(player, "editor_dialogue", "§dWrite lines"),
                firstLine == null ? EditorItems.ui(player, "editor_dialogue_empty", "§7No lines yet") : "§f" + firstLine,
                "§7" + npc.pages().size() + " " + EditorItems.ui(player, "editor_pages", "page(s)"),
                EditorItems.ui(player, "editor_dialogue_hint", "§8Start here after creating them.")
        ));
        inventory.setItem(27, EditorItems.section(
                EditorItems.ui(player, "editor_sec_quest", "Quest"),
                EditorItems.ui(player, "editor_sec_quest_hint", "§7Optional job for the player.")
        ));
        inventory.setItem(MODE, modeButton(player, npc));
        inventory.setItem(QUEST, questButton(player, npc));
        inventory.setItem(36, EditorItems.section(
                EditorItems.ui(player, "editor_sec_place", "Placement"),
                EditorItems.ui(player, "editor_sec_place_hint", "§7Move without breaking the NPC.")
        ));
        inventory.setItem(MOVE, EditorItems.button(
                Material.ENDER_PEARL,
                EditorItems.ui(player, "editor_move", "§bMove here"),
                EditorItems.ui(player, "editor_move_hint", "§7Teleport them to your feet.")
        ));
        inventory.setItem(LOOK, EditorItems.button(
                Material.ENDER_EYE,
                EditorItems.ui(player, "editor_look", "§bFace me"),
                EditorItems.ui(player, "editor_look_hint", "§7Turn them toward you.")
        ));
        inventory.setItem(DUPLICATE, EditorItems.button(
                Material.PAPER,
                EditorItems.ui(player, "editor_copy", "§eDuplicate"),
                EditorItems.ui(player, "editor_copy_hint", "§7Clone beside you.")
        ));
        inventory.setItem(42, EditorItems.dangerSection(
                EditorItems.ui(player, "editor_sec_danger", "Danger zone"),
                EditorItems.ui(player, "editor_sec_danger_hint", "§7Asks first. Cannot undo.")
        ));
        inventory.setItem(DELETE, EditorItems.button(
                Material.BARRIER,
                EditorItems.ui(player, "editor_delete", "§cDelete"),
                EditorItems.ui(player, "editor_delete_hint", "§7Asks first.")
        ));
        inventory.setItem(EditorItems.BACK, EditorItems.back(player));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f);
    }

    private static org.bukkit.inventory.ItemStack modeButton(Player player, CustomNpc npc) {
        boolean quest = npc.isQuestNpc();
        return EditorItems.button(
                quest ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE,
                quest
                        ? EditorItems.ui(player, "editor_mode_quest", "§aGives a quest")
                        : EditorItems.ui(player, "editor_mode_talk", "§bJust talks"),
                quest
                        ? EditorItems.ui(player, "editor_mode_quest_hint", "§7They can offer a job.")
                        : EditorItems.ui(player, "editor_mode_talk_hint", "§7Conversation only."),
                EditorItems.ui(player, "editor_mode_click", "§eClick to switch.")
        );
    }

    private static org.bukkit.inventory.ItemStack questButton(Player player, CustomNpc npc) {
        if (!npc.isQuestNpc()) {
            return EditorItems.button(
                    Material.GRAY_DYE,
                    EditorItems.ui(player, "editor_quest_locked", "§8Set up quest"),
                    EditorItems.ui(player, "editor_quest_locked_hint", "§7Switch to Gives a quest first.")
            );
        }
        NpcEditor editor = playerEditor();
        Quest quest = linked(editor, npc);
        String title = quest != null ? quest.getTitle() : npc.getLinkedQuestId();
        return EditorItems.button(
                Material.MAP,
                EditorItems.ui(player, "editor_quest_setup", "§aSet up quest"),
                title == null
                        ? EditorItems.ui(player, "editor_quest_none", "§7None yet — create or pick one")
                        : "§f" + title,
                EditorItems.ui(player, "editor_quest_setup_hint", "§8Create, link, rewards")
        );
    }

    private static String firstLine(CustomNpc npc) {
        CustomNpc.DialoguePage page = npc.page(npc.getStartPage());
        if (page == null || page.lines().isEmpty()) {
            return null;
        }
        String line = page.lines().get(0);
        if (line == null || line.isBlank() || "…".equals(line)) {
            return null;
        }
        return line.length() > 32 ? line.substring(0, 32) + "…" : line;
    }

    private static Quest linked(NpcEditor editor, CustomNpc npc) {
        if (editor == null || npc == null || !npc.hasLinkedQuest()) {
            return null;
        }
        if (editor.plugin().getQuestManager() == null) {
            return null;
        }
        return editor.plugin().getQuestManager().getQuest(npc.getLinkedQuestId());
    }

    private static NpcEditor playerEditor() {
        var plugin = de.aetherion.quests.AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getNpcEditor();
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
            player.closeInventory();
            player.sendMessage(EditorItems.ui(player, "editor_npc_gone", "§cNPC not found."));
            return;
        }
        editor.sessions().of(player).setNpcId(npc.getId());
        switch (event.getRawSlot()) {
            case NAME -> editor.prompt(player, EditorSessions.Prompt.RENAME, EditorScreen.EDIT,
                    EditorItems.ui(player, "editor_prompt_rename", "Type the new name"),
                    npc.getName());
            case SUBTITLE -> editor.prompt(player, EditorSessions.Prompt.SUBTITLE, EditorScreen.EDIT,
                    EditorItems.ui(player, "editor_prompt_subtitle", "Type a short subtitle"),
                    npc.getSubtitle());
            case APPEAR -> AppearanceMenu.open(player, npc);
            case DIALOGUE -> DialogueMenu.openTree(player, npc);
            case MODE -> toggleMode(player, npc);
            case QUEST -> {
                if (!npc.isQuestNpc()) {
                    player.sendMessage(EditorItems.ui(player, "editor_quest_locked_hint",
                            "§7Switch to Gives a quest first."));
                    return;
                }
                QuestHubMenu.open(player, npc);
            }
            case MOVE -> {
                editor.moveHere(player, npc);
                open(player, npc);
            }
            case LOOK -> {
                editor.lookAt(player, npc);
                open(player, npc);
            }
            case DUPLICATE -> editor.duplicate(player, npc);
            case DELETE -> ConfirmMenu.open(player, npc);
            case EditorItems.BACK -> MainMenu.open(player);
            default -> {
            }
        }
    }

    private void toggleMode(Player player, CustomNpc npc) {
        if (npc.isQuestNpc()) {
            npc.setMode(NpcMode.DIALOG);
            editor.persistQuiet(npc);
            player.sendMessage(EditorItems.ui(player, "editor_mode_talk_hint", "§7Conversation only."));
        } else {
            npc.setMode(NpcMode.QUEST);
            editor.persistQuiet(npc);
            player.sendMessage(EditorItems.ui(player, "editor_mode_on",
                    "§aThis NPC can give a quest. Open Set up quest next."));
        }
        open(player, npc);
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
