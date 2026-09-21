package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.AppearanceMenu;
import de.aetherion.quests.editor.gui.DialogueMenu;
import de.aetherion.quests.editor.gui.EditMenu;
import de.aetherion.quests.editor.gui.QuestHubMenu;
import de.aetherion.quests.editor.gui.RewardsMenu;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            player.sendMessage(LangPack.ui(player, "editor_cancelled", "§7Cancelled. You're back."));
            editor.reopen(player);
            return;
        }
        EditorSessions.Prompt prompt = session.prompt();
        session.clearPrompt();
        switch (prompt) {
            case NAME -> editor.createAt(player, message);
            case RENAME -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    player.sendMessage(LangPack.ui(player, "editor_npc_gone", "§cNPC not found."));
                    return;
                }
                npc.setName(NpcEditor.colorSafe(message));
                editor.persist(npc);
                player.sendMessage(LangPack.format(player, "msg.editor_renamed",
                        "§aRenamed to §f{0}", npc.getName()));
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
                player.sendMessage(LangPack.format(player, "msg.editor_skin",
                        "§aSkin set to §f{0}", npc.getSkinUsername()));
                AppearanceMenu.open(player, npc);
            }
            case LINE -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                CustomNpc.DialoguePage page = npc == null ? null : npc.page(session.pageId());
                if (page == null) {
                    return;
                }
                if (page.lines().size() == 1 && "…".equals(page.lines().get(0))) {
                    page.lines().set(0, message);
                } else {
                    page.lines().add(message);
                }
                editor.persistQuiet(npc);
                DialogueMenu.openPage(player, npc, page.id());
            }
            case LINE_EDIT -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                CustomNpc.DialoguePage page = npc == null ? null : npc.page(session.pageId());
                if (page == null || session.choiceIndex() < 0 || session.choiceIndex() >= page.lines().size()) {
                    return;
                }
                page.lines().set(session.choiceIndex(), message);
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
                    player.sendMessage(LangPack.ui(player, "editor_command_blocked",
                            "§cThat command is blocked (op/stop/lp/…). Try something safer."));
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
                    return;
                }
                Quest quest = editor.editorQuests().createAndSave(message, npc.getId(), editor.plugin().getQuestManager());
                npc.setLinkedQuestId(quest.getId());
                npc.setMode(NpcMode.QUEST);
                editor.persistQuiet(npc);
                player.sendMessage(LangPack.format(player, "msg.editor_quest_created",
                        "§aCreated job §f{0}", quest.getTitle()));
                player.sendMessage(LangPack.ui(player, "editor_quest_created_next",
                        "§7Players get this after talking. Edit rewards if you want."));
                QuestHubMenu.open(player, npc);
            }
            case QUEST_ID -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                String id = message.toLowerCase(Locale.ROOT).replace(' ', '_');
                if (editor.plugin().getQuestManager() == null
                        || editor.plugin().getQuestManager().getQuest(id) == null) {
                    player.sendMessage(LangPack.format(player, "msg.editor_quest_unknown",
                            "§cUnknown quest: §f{0}", id));
                    CustomNpc.DialoguePage page = npc.page(session.pageId());
                    if (page != null && session.choiceIndex() >= 0) {
                        DialogueMenu.openChoice(player, npc, page.id(), session.choiceIndex());
                    } else {
                        QuestHubMenu.openPick(player, npc, 0);
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
                player.sendMessage(LangPack.format(player, "msg.editor_quest_linked",
                        "§aLinked quest §f{0}", id));
                QuestHubMenu.open(player, npc);
            }
            case REWARD_NAME -> {
                CustomNpc npc = editor.storage().get(session.npcId());
                if (npc == null) {
                    return;
                }
                Quest quest = editor.editorQuests().get(npc.getLinkedQuestId());
                if (quest == null) {
                    QuestHubMenu.open(player, npc);
                    return;
                }
                List<Reward> rewards = new ArrayList<>(quest.getRewards());
                String name = message.trim();
                if (name.equalsIgnoreCase("xp") || name.equalsIgnoreCase("exp")) {
                    name = "XP";
                } else if (name.equalsIgnoreCase("coin") || name.equalsIgnoreCase("coins")) {
                    name = "Coins";
                }
                rewards.add(new Reward(name, EditorQuestFactory.isCurrencyReward(name) ? 25 : 1));
                EditorQuestFactory.setRewards(quest, rewards);
                editor.editorQuests().persist(quest, editor.plugin().getQuestManager());
                RewardsMenu.open(player, npc);
            }
            default -> {
            }
        }
    }

    static boolean isCancel(String message) {
        if (message == null) {
            return false;
        }
        String key = message.trim().toLowerCase(Locale.ROOT);
        return key.equals("cancel")
                || key.equals("abbrechen")
                || key.equals("abort")
                || key.equals("stop")
                || key.equals("back")
                || key.equals("abbruch")
                || key.equals("exit");
    }
}
