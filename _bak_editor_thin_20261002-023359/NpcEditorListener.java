package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.AppearanceMenu;
import de.aetherion.quests.editor.gui.DialogueMenu;
import de.aetherion.quests.editor.gui.EditMenu;
import de.aetherion.quests.editor.gui.QuestLinkMenu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Wand use + chat prompts for the NPC editor.
 */
public final class NpcEditorListener implements Listener {

    private final NpcEditor editor;

    public NpcEditorListener(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!NpcEditor.allowed(player) || !editor.isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        editor.openMain(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.15f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        EditorSessions.Session session = editor.sessions().peek(player);
        if (session == null || !session.prompting()) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        editor.plugin().getServer().getScheduler().runTask(editor.plugin(), () -> handlePrompt(player, session, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.sessions().forget(event.getPlayer().getUniqueId());
        editor.runtime().forget(event.getPlayer().getUniqueId());
    }

    private void handlePrompt(Player player, EditorSessions.Session session, String message) {
        if (isCancel(message)) {
            session.clearPrompt();
            player.sendMessage("§7Cancelled.");
            CustomNpc npc = editor.storage().get(session.npcId());
            if (npc != null) {
                editor.openEdit(player, npc);
            } else {
                editor.openMain(player);
            }
            return;
        }
        EditorSessions.Prompt prompt = session.prompt();
        session.clearPrompt();
        switch (prompt) {
            case NAME -> editor.createAt(player, message);
            case RENAME -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    player.sendMessage("§cNPC gone.");
                    return;
                }
                npc.setName(NpcEditor.colorSafe(message));
                editor.persist(npc);
                player.sendMessage("§aRenamed to §f" + npc.getName());
                EditMenu.open(player, npc);
            }
            case SUBTITLE -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                npc.setSubtitle(NpcEditor.colorSafe(message));
                editor.persist(npc);
                EditMenu.open(player, npc);
            }
            case SKIN -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                npc.setSkinUsername(message.replaceAll("[^A-Za-z0-9_]", ""));
                editor.persist(npc);
                player.sendMessage("§aSkin set to §f" + npc.getSkinUsername());
                AppearanceMenu.open(player, npc);
            }
            case LINE -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                CustomNpc.DialoguePage page = npc == null ? null : npc.page(session.pageId());
                if (page == null) {
                    return;
                }
                page.lines().add(message);
                editor.persistQuiet(npc);
                DialogueMenu.openPage(player, npc, page.id());
            }
            case CHOICE_TEXT -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                CustomNpc.DialoguePage page = npc == null ? null : npc.page(session.pageId());
                if (page == null || session.choiceIndex() < 0 || session.choiceIndex() >= page.choices().size()) {
                    return;
                }
                page.choices().get(session.choiceIndex()).setText(message);
                editor.persistQuiet(npc);
                DialogueMenu.openChoice(player, npc, page.id(), session.choiceIndex());
            }
            case COMMAND -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                CustomNpc.DialoguePage page = npc == null ? null : npc.page(session.pageId());
                if (page == null || session.choiceIndex() < 0 || session.choiceIndex() >= page.choices().size()) {
                    return;
                }
                if (DialogueRuntime.sanitize(player, message) == null) {
                    player.sendMessage("§cThat command is blocked (op/stop/lp/…). Try something safer.");
                    DialogueMenu.openChoice(player, npc, page.id(), session.choiceIndex());
                    return;
                }
                page.choices().get(session.choiceIndex()).setTarget(message);
                editor.persistQuiet(npc);
                DialogueMenu.openChoice(player, npc, page.id(), session.choiceIndex());
            }
            case PAGE_ID -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                CustomNpc.DialoguePage current = npc.page(session.pageId());
                if (current != null
                        && session.choiceIndex() >= 0
                        && session.choiceIndex() < current.choices().size()) {
                    String pageId = CustomNpc.sanitizePageId(message);
                    npc.ensurePage(pageId);
                    current.choices().get(session.choiceIndex()).setTarget(pageId);
                    editor.persistQuiet(npc);
                    DialogueMenu.openChoice(player, npc, current.id(), session.choiceIndex());
                    return;
                }
                CustomNpc.DialoguePage page = npc.ensurePage(message);
                editor.persistQuiet(npc);
                DialogueMenu.openPage(player, npc, page.id());
            }
            case QUEST_ID -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                String id = message.toLowerCase().replace(' ', '_');
                if (editor.plugin().getQuestManager() == null
                        || editor.plugin().getQuestManager().getQuest(id) == null) {
                    player.sendMessage("§cUnknown quest id: §f" + id);
                    CustomNpc.DialoguePage page = npc.page(session.pageId());
                    if (page != null && session.choiceIndex() >= 0) {
                        DialogueMenu.openChoice(player, npc, page.id(), session.choiceIndex());
                    } else {
                        QuestLinkMenu.open(player, npc, 0);
                    }
                    return;
                }
                CustomNpc.DialoguePage current = npc.page(session.pageId());
                if (current != null
                        && session.choiceIndex() >= 0
                        && session.choiceIndex() < current.choices().size()) {
                    current.choices().get(session.choiceIndex()).setTarget(id);
                    editor.persistQuiet(npc);
                    DialogueMenu.openChoice(player, npc, current.id(), session.choiceIndex());
                    return;
                }
                npc.setLinkedQuestId(id);
                editor.persistQuiet(npc);
                player.sendMessage("§aLinked quest §f" + id);
                QuestLinkMenu.open(player, npc, 0);
            }
            default -> {
            }
        }
    }

    private static boolean isCancel(String message) {
        return message.equalsIgnoreCase("cancel")
                || message.equalsIgnoreCase("abbrechen")
                || message.equalsIgnoreCase("abort");
    }
}
