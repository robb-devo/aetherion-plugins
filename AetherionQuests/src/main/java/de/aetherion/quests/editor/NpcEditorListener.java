package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.AppearanceMenu;
import de.aetherion.quests.editor.gui.DialogueMenu;
import de.aetherion.quests.editor.gui.EditMenu;
import de.aetherion.quests.editor.gui.ObjectiveMenu;
import de.aetherion.quests.editor.gui.QuestEditMenu;
import de.aetherion.quests.editor.gui.QuestLinkMenu;
import de.aetherion.quests.editor.gui.RequirementsMenu;
import de.aetherion.quests.editor.gui.RewardsMenu;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import java.util.ArrayList;
import java.util.List;

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
            session.setDirty(false);
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
                session.setDirty(false);
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
                session.setDirty(false);
                EditMenu.open(player, npc);
            }
            case SKIN -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                npc.setSkinUsername(message.replaceAll("[^A-Za-z0-9_]", ""));
                editor.persist(npc);
                session.setDirty(false);
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
            case QUEST_TITLE -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    player.sendMessage("§cNPC gone.");
                    return;
                }
                if (message.isBlank()) {
                    player.sendMessage("§cTitle cannot be empty.");
                    QuestLinkMenu.open(player, npc, 0);
                    return;
                }
                editor.createAndLinkQuest(player, npc, message);
                session.setDirty(false);
                QuestEditMenu.open(player, npc);
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
                npc.setMode(NpcMode.QUEST);
                editor.persistQuiet(npc);
                session.setDirty(false);
                player.sendMessage("§aLinked quest §f" + id);
                QuestLinkMenu.open(player, npc, 0);
            }
            case REWARD_NAME -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
                if (quest == null) {
                    return;
                }
                String name = message.trim();
                if (name.isBlank()) {
                    RewardsMenu.open(player, npc);
                    return;
                }
                List<Reward> rewards = new ArrayList<>(quest.getRewards());
                rewards.add(new Reward(name, EditorQuestFactory.isCurrencyReward(name) ? 50 : 1));
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                session.setDirty(false);
                RewardsMenu.open(player, npc);
            }
            case REQUIRE_LEVEL -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
                if (quest == null) {
                    return;
                }
                try {
                    quest.requireAccountLevel(Integer.parseInt(message.replaceAll("[^0-9-]", "")));
                } catch (NumberFormatException ignored) {
                    player.sendMessage("§cNeed a number.");
                }
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                session.setDirty(false);
                RequirementsMenu.open(player, npc);
            }
            case REQUIRE_PRIOR -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
                if (quest == null) {
                    return;
                }
                String id = message.toLowerCase().replace(' ', '_');
                if (editor.plugin().getQuestManager() == null
                        || editor.plugin().getQuestManager().getQuest(id) == null) {
                    player.sendMessage("§cUnknown quest id: §f" + id);
                    RequirementsMenu.open(player, npc);
                    return;
                }
                quest.requirePriorQuest(id);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                session.setDirty(false);
                RequirementsMenu.open(player, npc);
            }
            case OBJECTIVE_AMOUNT -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                Quest quest = npc == null ? null : editor.editorQuests().get(npc.getLinkedQuestId());
                if (quest == null) {
                    return;
                }
                Objective current = quest.getObjectives().isEmpty() ? null : quest.getObjectives().get(0);
                ObjectiveType type = current == null ? ObjectiveType.TALK : current.getType();
                String target = current == null ? npc.getId() : current.getTarget();
                int amount = 1;
                try {
                    amount = Math.max(1, Integer.parseInt(message.replaceAll("[^0-9]", "")));
                } catch (NumberFormatException ignored) {
                    player.sendMessage("§cNeed a number.");
                }
                EditorQuestFactory.setObjective(quest, type, target, amount);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                session.setDirty(false);
                ObjectiveMenu.open(player, npc);
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
