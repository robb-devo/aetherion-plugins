package de.aetherion.quests.dialog;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.ui.QuestAcceptGUI;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.QuestSkillGate;
import de.aetherion.quests.util.QuestStoryGate;
import de.aetherion.quests.ui.QuestHint;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/*
 * =========================================================
 * AETHERION DIALOG MANAGER
 * =========================================================
 *
 * Zentrale Basis für das neue Dialogsystem.
 *
 * Verantwortlich für:
 *
 * - NPC-Dialoge
 * - zeitversetzte Dialogzeilen
 * - Quest-Annahme
 * - Quest-Ablehnung
 * - Start der Quest nach Annahme
 * - klickbare Quest-Optionen
 *
 * Der eigentliche Quest-Progress und der Quest-Tracker
 * bleiben weiterhin beim QuestManager.
 *
 * Aktuell unterstützte Dialoge:
 *
 * - Egon the Equipper
 * - Quartermaster
 *
 * =========================================================
 */

public class DialogManager implements Listener {


    private final JavaPlugin plugin;

    private final QuestManager questManager;

    private QuestAcceptGUI acceptGUI;


    /*
     * =========================================================
     * OFFENE QUEST-ENTSCHEIDUNGEN
     * =========================================================
     *
     * Speichert für jeden Spieler die Quest, deren
     * ACCEPT / DECLINE-Auswahl gerade angezeigt wird.
     *
     * Dadurch kann der Klick eindeutig einer Quest
     * zugeordnet werden.
     */

    private final Map<UUID, String> pendingQuestChoices =
            new HashMap<>();

    /** One speaking task per player — prevents stacked / repeated NPC lines. */
    private final Map<UUID, BukkitTask> activeDialogs =
            new HashMap<>();

    /** NPC id currently speaking to the player (if any). */
    private final Map<UUID, String> activeDialogNpc =
            new HashMap<>();



    /*
     * =========================================================
     * KONSTRUKTOR
     * =========================================================
     */

    public DialogManager(
            JavaPlugin plugin,
            QuestManager questManager
    ) {

        this.plugin = plugin;

        this.questManager = questManager;

        this.acceptGUI = new QuestAcceptGUI(plugin, this);

        /*
         * Der DialogManager verarbeitet selbst die
         * internen Klick-Aktionen (Chat-Fallback).
         */

        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        this,
                        plugin
                );

    }

    public void forget(java.util.UUID playerId) {
        if (playerId == null) {
            return;
        }
        pendingQuestChoices.remove(playerId);
        activeDialogNpc.remove(playerId);
        BukkitTask task = activeDialogs.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Dialog / chat first, short pause, then an action (menu open, etc.).
     * Never fire dialog and GUI in the same tick.
     */
    public void afterDialog(Player player, long delayTicks, Runnable action) {
        if (player == null || action == null) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                action.run();
            }
        }, delay);
    }

    /** Default beat between spoken line and follow-up action (~1.5s). */
    public void afterDialog(Player player, Runnable action) {
        afterDialog(player, DialogPace.LINE_GAP_TICKS, action);
    }

    public void clearPendingChoice(Player player) {
        if (player != null) {
            pendingQuestChoices.remove(player.getUniqueId());
            if (player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof QuestAcceptGUI.Holder) {
                player.closeInventory();
            }
        }
    }

    /** GUI Accept button — same path as chat clickables. */
    public void handleGuiAccept(Player player, String questId) {
        if (player == null || questId == null) {
            return;
        }
        String pending = pendingQuestChoices.get(player.getUniqueId());
        if (pending == null || !pending.equalsIgnoreCase(questId)) {
            return;
        }
        Quest quest = findQuest(pending);
        pendingQuestChoices.remove(player.getUniqueId());
        if (quest == null) {
            return;
        }
        // Conflict was already confirmed in the Accept GUI ("Accept anyway").
        acceptQuest(player, quest);
    }

    /** GUI Decline / Cancel. */
    public void handleGuiDecline(Player player, String questId) {
        if (player == null || questId == null) {
            return;
        }
        String pending = pendingQuestChoices.get(player.getUniqueId());
        if (pending == null || !pending.equalsIgnoreCase(questId)) {
            return;
        }
        pendingQuestChoices.remove(player.getUniqueId());
        Quest quest = findQuest(pending);
        if (quest != null) {
            declineQuest(player, quest);
        }
    }

    public boolean hasPendingChoice(Player player) {
        return player != null && pendingQuestChoices.containsKey(player.getUniqueId());
    }

    /** Re-print Accept/Decline without replaying speech. */
    public void reshowQuestOptions(Player player, QuestNPC npc) {
        if (player == null || npc == null) {
            return;
        }
        pendingQuestChoices.remove(player.getUniqueId());
        showQuestOptions(player, npc);
    }

    /**
     * Offer an existing quest from a moderator-created NPC.
     * Does not register a story NPC — richer quest authoring can replace this later.
     */
    public void offerQuestById(Player player, String speakerName, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return;
        }
        String name = speakerName == null || speakerName.isBlank() ? "NPC" : speakerName;
        QuestNPC temp = new QuestNPC(
                "editor_offer",
                name,
                de.aetherion.quests.npc.NPCType.QUEST,
                questId,
                "",
                null
        );
        showQuestOptions(player, temp);
    }

    /** True while lines are still being typed out (before Accept/Decline). */
    public boolean isSpeaking(Player player) {
        return player != null && activeDialogs.containsKey(player.getUniqueId());
    }

    public boolean isSpeakingWith(Player player, QuestNPC npc) {
        if (player == null || npc == null || !isSpeaking(player)) {
            return false;
        }
        String id = activeDialogNpc.get(player.getUniqueId());
        return id != null && id.equalsIgnoreCase(npc.getId());
    }



    /*
     * =========================================================
     * DIALOG STARTEN
     * =========================================================
     *
     * Startet den Dialog des angegebenen NPCs.
     *
     * Die Dialog-ID kommt direkt aus QuestNPC.
     *
     * Nach der letzten Dialogzeile werden die
     * Quest-Optionen angezeigt.
     *
     * =========================================================
     */

    public void startDialog(
            Player player,
            QuestNPC npc
    ) {


        if (player == null
                || npc == null) {

            return;

        }


        String dialogId =
                npc.getDialogId();


        if (dialogId == null
                || dialogId.isEmpty()) {

            return;

        }


        UUID playerId = player.getUniqueId();

        // Never restart mid-speech for the same NPC.
        if (isSpeakingWith(player, npc)) {
            return;
        }

        // Switching NPCs (or re-clicking) always restarts speech — never skip to Accept/Deny.
        BukkitTask existing = activeDialogs.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        activeDialogNpc.remove(playerId);
        pendingQuestChoices.remove(playerId);

        // Fishing Tackle: wait until Egon's wood quest is done. Fishmonger shop is always open.
        if ("fisher".equalsIgnoreCase(npc.getId())
                && !QuestStoryGate.questCompleted(player, questManager, "gather_wood")) {
            String[] blocked = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                    player,
                    "fishing_before_egon",
                    new String[] {
                            "Hold up. Finish with Egon first — then we talk fishing."
                    }
            ));
            playDialog(player, npc, blocked, false);
            QuestHint.show(player, "egon", "Egon");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Hint · Egon · wood first",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
            return;
        }

        // Miss Ledger: Skills only after First Shift. Early visit → redirect + Foreman hint.
        if ("ledger".equalsIgnoreCase(npc.getId())
                && !QuestStoryGate.ledgerUnlocked(player, questManager)) {
            Quest lesson = questManager.getQuest("lesson_manager");
            if (lesson == null
                    || questManager.getQuestState(player, lesson) == QuestState.AVAILABLE) {
                String[] blocked = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                        player,
                        "ledger_blocked",
                        QuestStoryGate.ledgerBlockedLines()
                ));
                playDialog(player, npc, blocked, false);
                QuestHint.show(player, "foreman", "Shaft Foreman");
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Hint · Mines · Shaft Foreman",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW
                ));
                return;
            }
        }

        // Rite Warden: Borderlands bosses only after full orientation (not Temper alone).
        if ("rite_keeper".equalsIgnoreCase(npc.getId())
                && !QuestStoryGate.riteKeeperUnlocked(player, questManager)) {
            String[] blocked = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                    player,
                    "rite_keeper_blocked",
                    QuestStoryGate.riteKeeperBlockedLines()
            ));
            playDialog(player, npc, blocked, false);
            QuestStoryGate.redirectToTutorial(player, npc.getName());
            return;
        }

        // Post-tutorial NPCs wait until Miss Ledger stamps orientation shut.
        if (QuestStoryGate.blockedByTutorial(player, questManager, npc.getId())) {
            String[] blocked = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                    player,
                    "tutorial_blocked",
                    QuestStoryGate.tutorialBlockedLines()
            ));
            playDialog(player, npc, blocked, false);
            QuestStoryGate.redirectToTutorial(player, npc.getName());
            return;
        }

        // Colosseum Proctor: first Crypt vial → one-time walk. After that → point at the pad.
        if ("arena_proctor".equalsIgnoreCase(npc.getId())) {
            boolean taught = isColosseumTaught(player);
            if (holdsCryptSpirit(player)) {
                if (taught) {
                    String[] lines = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                            player,
                            "arena_proctor_self",
                            new String[] {
                                    "You've seen the spill. I don't do encore walks.",
                                    "Vial to the glowing mark in the ring. Right-click it yourself.",
                                    "Same thunder. Same ten seconds. Fewer of my steps."
                            }
                    ));
                    playDialog(player, npc, lines, false);
                    return;
                }
                String[] lines = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                        player,
                        "arena_proctor_vial",
                        new String[] {
                                "There it is. Stronger than the Borderlands stuff.",
                                "Come on — we look at the ring. I'll check the vial on the way.",
                                "Don't shake it. Don't sniff it."
                        }
                ));
                playDialog(player, npc, lines, false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> startColosseumEscort(player), 55L);
                return;
            }
            if (taught) {
                String[] lines = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                        player,
                        "arena_proctor_done",
                        new String[] {
                                "Ring's open. Crypt vials go on the glowing mark — not in my hand.",
                                "I walked you through it once. That's the lesson.",
                                "Bring another if you've got one. Pathwarden isn't picky."
                        }
                ));
                playDialog(player, npc, lines, false);
                return;
            }
        }

        String[] lines = cleanLines(de.aetherion.quests.lang.LangPack.dialogs(
                player,
                dialogId,
                getDialogLines(dialogId, player)
        ));

        if (lines.length == 0) {
            return;
        }

        boolean offerQuest = npc.getType() != de.aetherion.quests.npc.NPCType.FLAVOR
                && npc.getQuestId() != null
                && !npc.getQuestId().isBlank();
        playDialog(
                player,
                npc,
                lines,
                offerQuest
        );

    }


    /**
     * Short post-quest chat (no Accept/Decline).
     */
    public void startCompletedDialog(Player player, QuestNPC npc) {
        if (player == null || npc == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        BukkitTask existing = activeDialogs.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        activeDialogNpc.remove(playerId);
        pendingQuestChoices.remove(playerId);

        String[] lines = cleanLines(de.aetherion.quests.lang.LangPack.completed(
                player,
                npc.getId(),
                getCompletedLines(npc.getId(), player)
        ));
        if (lines.length == 0) {
            lines = new String[] {
                    de.aetherion.quests.lang.LangPack.msg(
                            player,
                            "done_fallback",
                            "We're done here. Go bother someone else's clipboard."
                    )
            };
        }

        playDialog(player, npc, lines, false);
    }

    /** Drop null / blank entries so empty “first lines” never burn a delay slot. */
    private static String[] cleanLines(String[] raw) {
        if (raw == null || raw.length == 0) {
            return new String[0];
        }
        List<String> out = new ArrayList<>(raw.length);
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            if ("__BLANK__".equals(line) || !line.isBlank()) {
                out.add(line);
            }
        }
        return out.toArray(String[]::new);
    }

    private void unlockMerchantChests(Player player) {
        if (player == null) {
            return;
        }
        AetherionQuests quests = AetherionQuests.getInstance();
        if (quests == null || quests.getMerchantChests() == null) {
            return;
        }
        boolean first = quests.getMerchantChests().unlockFor(player);
        if (first) {
            player.sendMessage(de.aetherion.quests.lang.LangPack.msg(
                    player,
                    "msg.chests_unlocked",
                    "§aChests unlocked. §7Sample beside the merchant + world crates are live."
            ));
        }
    }



    /*
     * =========================================================
     * DIALOG ABSPIELEN
     * =========================================================
     *
     * Jede Zeile wird zeitversetzt ausgegeben.
     *
     * Abstand: {@link DialogPace#LINE_GAP_TICKS} für jeden NPC / jeden Dialog.
     *
     * Nach der letzten Zeile werden die
     * Quest-Optionen angezeigt.
     *
     * =========================================================
     */

    private void playDialog(
            Player player,
            QuestNPC npc,
            String[] lines
    ) {
        playDialog(player, npc, lines, true);
    }


    private void playDialog(
            Player player,
            QuestNPC npc,
            String[] lines,
            boolean offerQuest
    ) {

        UUID playerId = player.getUniqueId();

        BukkitRunnable runnable = new BukkitRunnable() {

            private int index = 0;

            @Override
            public void run() {

                if (!player.isOnline()) {
                    activeDialogs.remove(playerId);
                    activeDialogNpc.remove(playerId);
                    cancel();
                    return;
                }

                if (index >= lines.length) {
                    activeDialogs.remove(playerId);
                    activeDialogNpc.remove(playerId);
                    cancel();
                    if (offerQuest) {
                        showQuestOptions(player, npc);
                    } else if (npc != null && "merchant".equalsIgnoreCase(npc.getId())) {
                        unlockMerchantChests(player);
                    }
                    return;
                }

                String line = lines[index++];
                if ("__BLANK__".equals(line)) {
                    player.sendMessage("");
                } else {
                    player.sendMessage(formatNpcLine(npc, line));
                    playLivingChatCue(player, npc);
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, DialogPace.LINE_GAP_TICKS);
        activeDialogs.put(playerId, task);
        activeDialogNpc.put(playerId, npc.getId());

    }

    private static String formatNpcLine(QuestNPC npc, String line) {
        LivingNpcProfile profile = LivingNpcProfile.of(npc.getId());
        if (profile != null) {
            return profile.formatChatLine(npc.getName(), line);
        }
        return "§6" + npc.getName() + " §8» §f" + line;
    }

    private static void playLivingChatCue(Player player, QuestNPC npc) {
        if (player == null || npc == null || !LivingNpcProfile.isLiving(npc.getId())) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.35f);
    }



    /*
     * =========================================================
     * QUEST OPTIONEN
     * =========================================================
     *
     * Nach dem Dialog erscheinen:
     *
     * [ ACCEPT ] | [ DECLINE ]
     *
     * Beide Buttons sind direkt anklickbar.
     *
     * =========================================================
     */

    private void showQuestOptions(
            Player player,
            QuestNPC npc
    ) {


        String questId =
                npc.getQuestId();


        Quest quest =
                questManager.getQuestForNpc(
                        npc
                );


        if (quest == null) {

            // Flavor NPCs finish after the last line — no Accept/Decline.
            if (questId == null || questId.isEmpty()) {
                return;
            }

            player.sendMessage(
                    "§cQuest not found: §f" + questId
            );

            return;

        }


        QuestState state =
                questManager.getQuestState(
                        player,
                        quest
                );


        if (state != QuestState.AVAILABLE) {

            return;

        }


        String gateFail = QuestSkillGate.failReason(player, quest);
        if (gateFail == null && "lesson_manager".equalsIgnoreCase(quest.getId())) {
            gateFail = QuestStoryGate.ledgerFailReason(player, questManager);
            if (gateFail != null) {
                QuestHint.show(player, "foreman", "Shaft Foreman");
            }
        }
        Quest conflicting = gateFail == null ? getConflictingQuest(player, quest) : null;

        /*
         * Quest für die aktuelle Auswahl merken (auch bei Gate-Fail,
         * damit Decline/Close die pending map räumt).
         */
        pendingQuestChoices.put(
                player.getUniqueId(),
                quest.getId()
        );

        if (acceptGUI != null) {
            acceptGUI.open(player, npc, quest, conflicting, gateFail);
            return;
        }

        // Fallback: chat clickables if GUI failed to wire.
        showQuestOptionsChat(player, npc, quest, conflicting, gateFail);
    }

    /** Legacy chat Accept/Decline — kept as emergency fallback. */
    private void showQuestOptionsChat(
            Player player,
            QuestNPC npc,
            Quest quest,
            Quest conflicting,
            String gateFail
    ) {

        if (gateFail != null) {
            player.sendMessage("");
            player.sendMessage("§8§m--------------------------------");
            LivingNpcProfile profile = LivingNpcProfile.of(npc.getId());
            if (profile != null) {
                player.sendMessage(profile.chatPrefix() + npc.getName() + " §8⟫");
            } else {
                player.sendMessage("§6" + npc.getName() + " §8»");
            }
            player.sendMessage("");
            player.sendMessage(gateFail);
            String hint = QuestSkillGate.requirementHint(quest);
            if (hint != null) {
                player.sendMessage(hint);
            }
            player.sendMessage("§7Come back when the numbers agree.");
            player.sendMessage("§8§m--------------------------------");
            player.sendMessage("");
            pendingQuestChoices.remove(player.getUniqueId());
            return;
        }

        player.sendMessage("");

        player.sendMessage(
                "§8§m--------------------------------"
        );

        LivingNpcProfile profile = LivingNpcProfile.of(npc.getId());
        if (profile != null) {
            player.sendMessage(profile.chatPrefix() + npc.getName() + " §8⟫");
        } else {
            player.sendMessage(
                    "§6"
                            + npc.getName()
                            + " §8»"
            );
        }

        player.sendMessage("");

        String hint = QuestSkillGate.requirementHint(quest);
        if (hint != null) {
            player.sendMessage(hint);
            player.sendMessage("");
        }


        String acceptLabel =
                "§a§l[ ACCEPT ]";

        String acceptHover =
                "§aClick to accept this quest.";


        if (conflicting != null) {

            player.sendMessage(
                    "§c⚠ Accepting this will abort your current quest:"
            );

            player.sendMessage(
                    "§7" + conflicting.getTitle()
            );

            player.sendMessage("");

            acceptLabel =
                    "§6§l[ ACCEPT ANYWAY ]";

            acceptHover =
                    "§cThis will abort: §f"
                            + conflicting.getTitle();

        }


        /*
         * =====================================================
         * ACCEPT BUTTON
         * =====================================================
         */

        TextComponent accept =
                new TextComponent(
                        acceptLabel
                );

        accept.setClickEvent(
                new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/aetherionquest accept "
                                + quest.getId()
                )
        );

        accept.setHoverEvent(
                new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(
                                acceptHover
                        ).create()
                )
        );


        /*
         * =====================================================
         * DECLINE BUTTON
         * =====================================================
         */

        TextComponent separator =
                new TextComponent(
                        " §7| "
                );


        TextComponent decline =
                new TextComponent(
                        "§c§l[ DECLINE ]"
                );

        decline.setClickEvent(
                new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/aetherionquest decline "
                                + quest.getId()
                )
        );

        decline.setHoverEvent(
                new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(
                                "§cClick to decline this quest."
                        ).create()
                )
        );


        /*
         * Buttons gemeinsam senden.
         */

        TextComponent options =
                new TextComponent();

        options.addExtra(accept);
        options.addExtra(separator);
        options.addExtra(decline);


        player.spigot().sendMessage(
                options
        );


        player.sendMessage("");

        player.sendMessage(
                "§8§m--------------------------------"
        );

        player.sendMessage("");

    }



    /*
     * =========================================================
     * CLICK COMMAND ABFANGEN
     * =========================================================
     *
     * Die Chat-Buttons verwenden einen internen Command.
     *
     * Dieser wird hier abgefangen, bevor Bukkit versucht,
     * einen echten Command dafür zu finden.
     *
     * Dadurch benötigen wir keinen zusätzlichen Eintrag
     * in der plugin.yml.
     *
     * =========================================================
     */

    @EventHandler
    public void onDialogCommand(
            PlayerCommandPreprocessEvent event
    ) {


        String message =
                event.getMessage();


        if (message == null) {

            return;

        }


        String lower =
                message.toLowerCase();


        /*
         * Nur unsere beiden internen Aktionen behandeln.
         */

        if (!lower.startsWith("/aetherionquest accept ")
                && !lower.startsWith("/aetherionquest decline ")
                && !lower.startsWith("/aetherionquest confirm ")
                && !lower.startsWith("/aetherionquest cancel ")) {

            return;

        }


        event.setCancelled(true);


        Player player =
                event.getPlayer();


        String[] parts =
                message.substring(1)
                        .split("\\s+");


        if (parts.length < 3) {

            return;

        }


        String action =
                parts[1].toLowerCase();


        String questId =
                parts[2];


        String pendingQuestId =
                pendingQuestChoices.get(
                        player.getUniqueId()
                );


        /*
         * Kein gültiger Dialog offen.
         */

        if (pendingQuestId == null) {

            return;

        }


        /*
         * Der geklickte Button muss zur aktuell
         * offenen Quest gehören.
         */

        if (!pendingQuestId.equalsIgnoreCase(
                questId
        )) {

            return;

        }


        Quest quest =
                findQuest(
                        pendingQuestId
                );


        if (quest == null) {

            pendingQuestChoices.remove(
                    player.getUniqueId()
            );

            return;

        }


        if (action.equals("accept")) {

            Quest conflicting =
                    getConflictingQuest(
                            player,
                            quest
                    );

            if (conflicting != null) {

                showOverwriteConfirm(
                        player,
                        quest,
                        conflicting
                );

                return;

            }

            pendingQuestChoices.remove(
                    player.getUniqueId()
            );

            acceptQuest(
                    player,
                    quest
            );

            return;

        }


        if (action.equals("confirm")) {

            pendingQuestChoices.remove(
                    player.getUniqueId()
            );

            acceptQuest(
                    player,
                    quest
            );

            return;

        }


        if (action.equals("decline")
                || action.equals("cancel")) {

            pendingQuestChoices.remove(
                    player.getUniqueId()
            );

            declineQuest(
                    player,
                    quest
            );

        }

    }



    /*
     * =========================================================
     * QUEST ANNEHMEN
     * =========================================================
     *
     * Startet die Quest über den bestehenden QuestManager.
     *
     * Der vorhandene Tracker wird dadurch automatisch
     * aktiviert.
     *
     * =========================================================
     */

    private Quest getConflictingQuest(
            Player player,
            Quest incoming
    ) {

        if (player == null || incoming == null) {
            return null;
        }

        // Prefer tracked, but also catch any ACTIVE/READY quest (tracked map can be empty).
        Quest current = questManager.getTrackedQuest(player);
        if (current == null) {
            current = questManager.findActiveOrReadyQuest(player);
        }

        if (current == null) {
            return null;
        }

        if (current.getId().equalsIgnoreCase(incoming.getId())) {
            return null;
        }

        return current;

    }


    private void showOverwriteConfirm(
            Player player,
            Quest incoming,
            Quest current
    ) {

        player.sendMessage("");
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§cAccepting §f" + incoming.getTitle());
        player.sendMessage("§cwill abort §f" + current.getTitle() + "§c.");
        player.sendMessage("");

        TextComponent confirm =
                new TextComponent("§c§l[ CONFIRM ABORT ]");

        confirm.setClickEvent(
                new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/aetherionquest confirm " + incoming.getId()
                )
        );

        confirm.setHoverEvent(
                new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(
                                "§cAbort §f" + current.getTitle()
                                        + "\n§7and accept §f" + incoming.getTitle()
                        ).create()
                )
        );

        TextComponent separator =
                new TextComponent(" §7| ");

        TextComponent cancel =
                new TextComponent("§7§l[ CANCEL ]");

        cancel.setClickEvent(
                new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/aetherionquest cancel " + incoming.getId()
                )
        );

        cancel.setHoverEvent(
                new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(
                                "§7Keep §f" + current.getTitle()
                        ).create()
                )
        );

        TextComponent options = new TextComponent();
        options.addExtra(confirm);
        options.addExtra(separator);
        options.addExtra(cancel);

        player.spigot().sendMessage(options);

        player.sendMessage("");
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("");

    }


    private void acceptQuest(
            Player player,
            Quest quest
    ) {


        QuestState state =
                questManager.getQuestState(
                        player,
                        quest
                );


        if (state != QuestState.AVAILABLE) {

            return;

        }


        String gateFail = QuestSkillGate.failReason(player, quest);
        if (gateFail == null && "lesson_manager".equalsIgnoreCase(quest.getId())) {
            gateFail = QuestStoryGate.ledgerFailReason(player, questManager);
        }
        if (gateFail == null && "border_rites".equalsIgnoreCase(quest.getId())) {
            gateFail = QuestStoryGate.riteKeeperFailReason(player, questManager);
        }
        if (gateFail != null) {
            player.sendMessage("");
            player.sendMessage(gateFail);
            player.sendMessage("");
            if ("lesson_manager".equalsIgnoreCase(quest.getId())
                    && !QuestStoryGate.ledgerUnlocked(player, questManager)) {
                String speaking = activeDialogNpc.get(player.getUniqueId());
                QuestStoryGate.redirectToForeman(player, speaking != null ? "Miss Ledger" : "Miss Ledger");
            }
            if ("border_rites".equalsIgnoreCase(quest.getId())
                    && !QuestStoryGate.riteKeeperUnlocked(player, questManager)) {
                QuestStoryGate.redirectToTutorial(player, "Rite Warden");
            }
            return;
        }


        questManager.startQuest(
                player,
                quest
        );

        // gather_wood: axe is granted on start; chop unlock waits for ForagerChopDemo.
        if ("gather_wood".equalsIgnoreCase(quest.getId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                org.bukkit.Location near = null;
                try {
                    AetherionQuests aq = AetherionQuests.getInstance();
                    if (aq != null && aq.getNpcDataStorage() != null) {
                        near = aq.getNpcDataStorage().getSavedLocation("lumberjack");
                    }
                } catch (Throwable ignored) {
                }
                if (near == null) {
                    near = player.getLocation();
                }
                de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
                if (foraging != null) {
                    foraging.playChopDemo(player, near);
                } else {
                    de.aetherion.quests.bridge.QuestProgressBridge.unlockForagerChop(player);
                }
            }, 10L);
        }

        if ("lesson_manager".equalsIgnoreCase(quest.getId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Open Nether Star (slot 9) → Skills will blink",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                if (progress != null) {
                    progress.refreshManager(player);
                }
            });
        }
        if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Throw a Catch Sphere at a wild pet — then talk to Lark",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                if (progress != null) {
                    progress.refreshManager(player);
                }
            });
        }
        if ("a_simple_craft".equalsIgnoreCase(quest.getId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Manager → green Recipe Book — find Mining Pickaxe (2nd recipe)",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                if (progress != null) {
                    progress.refreshManager(player);
                }
            });
        }

        player.sendMessage("");

        player.sendMessage(
                "§a§lQUEST ACCEPTED"
        );

        player.sendMessage(
                "§7"
                        + quest.getTitle()
        );

        player.sendMessage("");

    }



    /*
     * =========================================================
     * QUEST ABLEHNEN
     * =========================================================
     */

    private void declineQuest(
            Player player,
            Quest quest
    ) {


        player.sendMessage("");

        player.sendMessage(
                "§7Maybe another time."
        );

        player.sendMessage("");

    }



    /*
     * =========================================================
     * NPC QUEST LADEN
     * =========================================================
     */

    private Quest getNPCQuest(
            QuestNPC npc
    ) {


        String questId =
                npc.getQuestId();


        if (questId == null
                || questId.isEmpty()) {

            return null;

        }


        return findQuest(
                questId
        );

    }



    /*
     * =========================================================
     * QUEST SUCHEN
     * =========================================================
     */

    private Quest findQuest(
            String questId
    ) {


        for (Quest quest :
                questManager.getQuests()) {


            if (quest == null) {

                continue;

            }


            if (quest.getId()
                    .equalsIgnoreCase(
                            questId
                    )) {

                return quest;

            }

        }


        return null;

    }



    /*
     * =========================================================
     * DIALOGDATEN
     * =========================================================
     *
     * Hier liegen ausschließlich die neuen
     * Egon-/Quartermaster-Dialoge.
     *
     * Keine alten Fisherman-/Blacksmith-Dialoge.
     *
     * =========================================================
     */

    private String[] getDialogLines(
            String dialogId,
            Player player
    ) {


        /*
         * =====================================================
         * EGON
         * =====================================================
         */

        if (dialogId.equalsIgnoreCase(
                "egon_intro"
        )) {

            return new String[] {
                    "Egon. Harbour kit. I need oak — you fetch it.",
                    "Forager up the hill lends the axe and shows the chop minigame. Ten oak logs, back here.",
                    "§eYellow arrow up top§f is your compass. Sparkle trail on the ground for this job."
            };

        }



        /*
         * =====================================================
         * QUARTERMASTER
         * =====================================================
         */

        if (dialogId.equalsIgnoreCase(
                "quartermaster_intro"
        )) {

            return new String[] {
                    "Forge is hungry. Twenty coal from the surface.",
                    "§eOre Ridge§f — hill past the little market. Open coal veins. Mine twenty, walk them back.",
                    "§eYellow arrow up top§f tracks the job."
            };

        }


        if (dialogId.equalsIgnoreCase("hunter_intro")) {
            return new String[] {
                    "Farm's getting loud. Too many cows wandering around.",
                    "Take down five of them.",
                    "Come back when the place is quieter."
            };
        }

        if (dialogId.equalsIgnoreCase("lumberjack_intro")) {
            return new String[] {
                    "Egon needs oak. Again.",
                    "Simple Axe. Left-click a §eglowing trunk§f — that's the chop bar.",
                    "When it hits §aCHOP§f (green), left-click again. Miss it, the tree resets.",
                    "Ten oak logs → §eEgon on the pier§f. Not my desk."
            };
        }

        if (dialogId.equalsIgnoreCase("farmer_intro")) {
            return farmerIntroLines(player);
        }

        if (dialogId.equalsIgnoreCase("craftsman_intro")) {
            return craftsmanIntroLines(player);
        }

        if (dialogId.equalsIgnoreCase("collector_intro")) {
            return new String[] {
                    "I like shiny things. You like rewards. Easy deal.",
                    "Mine me three diamonds."
            };
        }

        if (dialogId.equalsIgnoreCase("fisher_intro")) {
            return new String[] {
                    "Cast into water. Wait for the bite. §eREEL§f when the bar turns green.",
                    "Miss the window, the fish leaves. Catch §efive§f, then talk to me."
            };
        }

        if (dialogId.equalsIgnoreCase("fishmonger_intro")) {
            return new String[] {
                    "Shop. Rods. Armor. Coins welcome."
            };
        }

        if (dialogId.equalsIgnoreCase("foreman_intro")) {
            return foremanIntroLines(player);
        }

        if (dialogId.equalsIgnoreCase("surveyor_intro")) {
            return new String[] {
                    "Ore trolls drop blueprints now. Soft ores: coal, iron, copper.",
                    "Bring a page. I'll stamp it into a tool at this desk."
            };
        }

        if (dialogId.equalsIgnoreCase("merchant_intro")) {
            return new String[] {
                    "World chests. Not just this crate — they spawn all over.",
                    "Rarer chest, better loot. Exploring pays.",
                    "Talking to me unlocks them. Sample's beside me anytime."
            };
        }

        if (dialogId.equalsIgnoreCase("lark_intro")) {
            return new String[] {
                    "Fields are hunting ground. Wild animals — not my parrot.",
                    "Hold a Catch Sphere, look at a critter, §eright-click§f to throw. Catch §eone§f, then talk to me.",
                    "After that I'll show you how to §eequip§f it. One step at a time."
            };
        }

        if (dialogId.equalsIgnoreCase("miner_intro")) {
            return new String[] {
                    "WOAH!",
                    "Don't startle me like that...",
                    "I was just here, mining my own business...",
                    "But then I heard those sounds... AAAH!",
                    "Could you go and check out where they come from? I'll pay you handsomely!"
            };
        }

        if (dialogId.equalsIgnoreCase("chicken_keeper_intro")) {
            return new String[] {
                    "That is not a chicken. That is a logistics error with feathers.",
                    "McNugget got out. Again.",
                    "If you can put it down, I have coins. And two mystery jars I refuse to label."
            };
        }

        if (dialogId.equalsIgnoreCase("tollkeeper_intro")) {
            return new String[] {
                    "Bridge is closed. The troll is collecting a toll.",
                    "The toll is whoever tries to cross.",
                    "Go collect it back."
            };
        }

        if (dialogId.equalsIgnoreCase("dockhand_intro")) {
            return new String[] {
                    "Water's been writing its name on people lately.",
                    "Squidward's in a mood. Ink, poison, the works.",
                    "Kill it before the harbor smells like a stationery shop."
            };
        }

        if (dialogId.equalsIgnoreCase("ash_scout_intro")) {
            return new String[] {
                    "If you see flaming arrows, you're already late.",
                    "Skuldugery likes an audience. Don't be one.",
                    "Bring the bowstring to a stop. I'll pretend that's a plan."
            };
        }

        if (dialogId.equalsIgnoreCase("colossus_scholar_intro")) {
            return new String[] {
                    "It walks. That's the whole thesis.",
                    "The Aether Colossus is not a metaphor. Sadly.",
                    "Knock it over. I'll write you down as a primary source."
            };
        }

        if (dialogId.equalsIgnoreCase("veil_priest_intro")) {
            return new String[] {
                    "Aetherion is bored. That is a weather report.",
                    "It asked for a challenger. You look... available.",
                    "Win, and I'll pay you like you meant to survive. Lose, and it gets a story."
            };
        }

        if (dialogId.equalsIgnoreCase("patch_intern_intro")) {
            return new String[] {
                    "Welcome to Support. Your estimated wait time is Sir Balthazar.",
                    "He froze the last three people who asked for a status update. Politely.",
                    "Close the ticket. Permanently. I'll pay you in diamond blocks so you stop emailing."
            };
        }

        if (dialogId.equalsIgnoreCase("void_janitor_intro")) {
            return new String[] {
                    "The lobby is a crime scene. The crime is trash.",
                    "The Lobby Cleaner vacuumed three interns and a rumor. Do not ask which was louder.",
                    "Unplug it. Two compacted diamond blocks. I found them in Lost and Found. Obviously."
            };
        }

        if (dialogId.equalsIgnoreCase("fuse_intro")) {
            return new String[] {
                    "Sparky is employee of the month. Nobody voted. The clipboard melted.",
                    "It clocks in as lightning and clocks out as a hole in the guild quarry.",
                    "Turn it off. Two compacted diamond blocks. HR already signed. In soot."
            };
        }

        if (dialogId.equalsIgnoreCase("claims_adjuster_intro")) {
            return new String[] {
                    "Baron von Wurm ate a quarry. Then billed us for the chew marks.",
                    "The form has a box for 'swallowed whole.' We checked. Twice.",
                    "Kill the worm. Compacted diamond blocks. That's the settlement. Don't appeal."
            };
        }

        if (dialogId.equalsIgnoreCase("repo_agent_intro")) {
            return new String[] {
                    "The Insolvent Wither declared bankruptcy. On other people's skulls.",
                    "Collections sent me. I sent you. This is called delegation. Also cowardice.",
                    "Foreclose the wither. Two compacted diamond blocks. If anyone asks, we never met."
            };
        }

        if (dialogId.equalsIgnoreCase("vex_intro")) {
            return new String[] {
                    "Sergeant Vex. Gate to the §cBorderlands§f — wasteland past the wall.",
                    "First lesson: ten hostiles. Kill them. Husk, stray, crawler — I don't care which.",
                    "Struggling? Combat set from the Recipe Book, boosters on the anvil. Soft gear. Harder hits. Move."
            };
        }

        if (dialogId.equalsIgnoreCase("booster_tutor_intro")) {
            return new String[] {
                    "Temper. Boosters fuse onto gear — mining power, fortune, damage.",
                    "Manager → §eAnvil§f: gear left, booster right. Here's an Emerald — fuse it once. Recipes stay in the §eRecipe Book§f."
            };
        }

        if (dialogId.equalsIgnoreCase("rite_keeper_intro")) {
            return new String[] {
                    "Rite Warden. T1 bosses. Not a walking tour.",
                    "Kill hostiles for Spirit vials: §c5%§f T1 · §c10%§f Sturdy · §c15%§f elites. The §ebeacon pillar§f is the altar.",
                    "Right-click the light-gray powder with a vial. Ten seconds. Thunder. Then it walks. Kill it once — rite passed."
            };
        }

        if (dialogId.equalsIgnoreCase("arena_proctor_intro")) {
            return new String[] {
                    "Proctor. This ring used to mean something.",
                    "Borderlands vials are the starter kit. Crypt vials are the real ones.",
                    "Bring me a Crypt vial. I'll study it. Carefully. Probably."
            };
        }

        if (dialogId.equalsIgnoreCase("ledger_intro")) {
            return new String[] {
                    "Miss Ledger. Skills — and I stamp orientation shut when you're done.",
                    "Hotbar §e9§f — Nether Star → Manager → §dSkills§f (it blinks). Equip §eany one§f skill.",
                    "Come back when it's equipped."
            };
        }

        if (dialogId.equalsIgnoreCase("rook_intro")) {
            return new String[] {
                    "Rook. I prepare people for holes in the ground that charge admission.",
                    "Dungeons are caves with opinions. Bring me ten bones. Homework before the field trip.",
                    "Survive the paperwork first. The skeletons can wait their turn."
            };
        }

        if (dialogId.equalsIgnoreCase("gate_warden_intro")) {
            return new String[] {
                    "Road's closed. The Pathwarden is the reason and the billboard.",
                    "He is slow. He is patient. He is also very good at ending careers.",
                    "Move him. Or become a cautionary plaque. Your choice. I get paid either way."
            };
        }

        if (dialogId.equalsIgnoreCase("bench_cynic_intro")) {
            return new String[] {
                    "Don't sit. This bench is occupied by standards.",
                    "I need eight rotten flesh. Call it research. Call it lunch. I don't care.",
                    "Bring it. Then leave me to my disappointment."
            };
        }

        if (dialogId.equalsIgnoreCase("dust_intro")) {
            return new String[] {
                    "Name's Dust. I map places people already walked over.",
                    "Thirty cobblestone. Ambition needs roads. Roads need rocks. Rocks need you.",
                    "Don't make it poetry. Make it a pile."
            };
        }

        if (dialogId.equalsIgnoreCase("fry_gossip_intro")) {
            return new String[] {
                    "You smell like adventure. Or grease. Hard to tell around here.",
                    "Clucksworth keeps screaming about McNugget. Giant chicken. Very punchable. Very crispy.",
                    "If you survive it, don't brag near the fryer. The Gossip listens. And repeats."
            };
        }

        if (dialogId.equalsIgnoreCase("dock_whisper_intro")) {
            return new String[] {
                    "Quiet. The river has opinions.",
                    "Fisher will teach you the reel. Rare days, the line goes purple — Mythic Water Dragon. Only yours to catch.",
                    "And if something older than the reef comes up in a vial? Don't shake it. Don't ask Brine. Just... don't."
            };
        }

        if (dialogId.equalsIgnoreCase("larder_intro")) {
            return new String[] {
                    "Pantry's thin. My patience thinner.",
                    "Compress wheat — real Compressed Wheat, not the grassy leftovers.",
                    "Three stacks of that. Then we talk boosters."
            };
        }

        if (dialogId.equalsIgnoreCase("pet_scout_intro")) {
            return new String[] {
                    "Lark hands out spheres like candy. I want proof you can aim.",
                    "Catch a wild pig. One. Alive in a sphere, not as dinner.",
                    "Come back when the pocket squeals."
            };
        }

        if (dialogId.equalsIgnoreCase("ore_ledger_intro")) {
            return new String[] {
                    "I don't dig. I audit. The mountain already lied once.",
                    "Mining skill level eight. Minimum. Below that you're a tourist with a pickaxe.",
                    "Five Compressed Coal. Not dusty. Not 'almost'. Compressed. Then we close the ledger."
            };
        }

        if (dialogId.equalsIgnoreCase("timber_clerk_intro")) {
            return new String[] {
                    "Every stump files a complaint. I'm the complaints department.",
                    "Foraging eight or higher. Axes that still wobble can wait outside.",
                    "Five Compressed Oak Logs. Taxed wood. Quiet forest. Everybody wins except the trees."
            };
        }

        if (dialogId.equalsIgnoreCase("canopy_clerk_intro")) {
            return new String[] {
                    "I want isle wood — compressed. Not harbour leftovers.",
                    "Foraging 5. Then one Compressed Oak, one Birch, one Spruce.",
                    "Three samples. Fat payout. Then go chop."
            };
        }

        if (dialogId.equalsIgnoreCase("dock_scaler_intro")) {
            return new String[] {
                    "I weigh fish that refuse to be weighed. Professionally.",
                    "Fishing ten. If your cast still looks like a sneeze, keep walking.",
                    "Three Compressed Cod. School that learned solidarity. Clipboard stays wet either way."
            };
        }

        if (dialogId.equalsIgnoreCase("sphere_proctor_intro")) {
            return new String[] {
                    "Pigs were kindergarten. Cows are the midterm.",
                    "Aetherion Level five. The sphere has standards. So do I.",
                    "Catch one cow. Alive. Mooving. In custody. Do not bring me a steak and call it progress."
            };
        }

        if (dialogId.equalsIgnoreCase("quarry_broker_intro")) {
            return new String[] {
                    "Guild door's picky. I'm pickier.",
                    "Aetherion Level twenty. That's the club handshake, minus the handshake.",
                    "One Compacted Cobblestone. Not compressed. Compacted. The brick that proves you meant it."
            };
        }

        if (dialogId.equalsIgnoreCase("slag_poet_intro")) {
            return new String[] {
                    "Ode to a vein that ghosted me. Stanza one: betrayal. Stanza two: also betrayal.",
                    "Ore Ledger wants coal with receipts. I want metaphors. We are not the same.",
                    "If the mountain sings, don't answer. It bills by the hour."
            };
        }

        if (dialogId.equalsIgnoreCase("bait_theory_intro")) {
            return new String[] {
                    "Bait is a philosophy. Worms are the footnotes.",
                    "Dock Whisper mutters about purple lines. Dock Scaler mutters about clipboards. I mutter about entropy.",
                    "Cast like you mean it. Or cast like you don't. The river grades on vibes."
            };
        }

        if (dialogId.equalsIgnoreCase("stall_crumb_intro")) {
            return new String[] {
                    "Fresh loaf. Or yesterday's. Depends how honest you look.",
                    "Quartermaster pays in coal talk. I pay in crusts. Pick your religion."
            };
        }

        if (dialogId.equalsIgnoreCase("stall_tack_intro")) {
            return new String[] {
                    "Nails, twine, regret — three for a coin if I liked you.",
                    "Ore Ridge chews tools. I sell the chewing gum that almost helps."
            };
        }

        if (dialogId.equalsIgnoreCase("stall_brine_intro")) {
            return new String[] {
                    "Salt for fish, salt for wounds, salt for opinions.",
                    "Tackle on the dock sells rods. I sell the reason fish taste like something."
            };
        }

        if (dialogId.equalsIgnoreCase("farm_isle_guide_intro")) {
            return new String[] {
                    "Portal goes to the Farm Isle — shared fields off the hub farm.",
                    "Farming 10 to enter. Same portal brings you back."
            };
        }

        if (dialogId.equalsIgnoreCase("forage_pad_guide_intro")) {
            return new String[] {
                    "Slime pad → Forage Isle. Jump. Don't overthink it.",
                    "Trees drop what they look like. Spruce is spruce. Birch is birch.",
                    "Canopy Clerk on the isle wants compressed samples. Follow the arrow."
            };
        }

        if (dialogId.equalsIgnoreCase("eldervale_welcome_intro")) {
            return new String[] {
                    "Welcome to Eldervale.",
                    "Mining island. Deep rock. Don't fall off."
            };
        }

        if (dialogId.equalsIgnoreCase("eldervale_upgrade_intro")) {
            return new String[] {
                    "Blueprint forge. Tool plus Upgrade Stone. That's the whole trick.",
                    "Tier II, III, IV — stones get expensive. Fast."
            };
        }

        if (dialogId.equalsIgnoreCase("isle_clerk_intro")) {
            return new String[] {
                    "Personal island: Aetherion Level 20.",
                    "Nether Star, slot 9 → Island tab. Claim it there. I don't do paperwork."
            };
        }

        if (dialogId.equalsIgnoreCase("dungeon_gate_intro")) {
            return new String[] {
                    "That portal's the Dungeon Gate. It drops you on the dungeon hub — instances, floors, keepers.",
                    "Walk through when you're geared. The other side sends you back to Capital."
            };
        }

        if (dialogId.equalsIgnoreCase("living_test_intro")) {
            return new String[] {
                    "If you can read this, the living NPC pipeline works.",
                    "Player skin, looks at you, no glow. Villagers stay as they are.",
                    "Poke the team when you're ready to migrate the rest."
            };
        }

        if (dialogId.equalsIgnoreCase("rivet_intro")) {
            return new String[] {
                    "Hold still — if that plate rattles, I'm charging you for the echo.",
                    "Dock Whisper mutters about tides. I mutter about bolts that lie for a living.",
                    "Brass wants polish. Iron wants respect. Me? I want whoever keeps stealing my rivets.",
                    "You look useful. Or at least distractingly vertical. Don't lean on the wet paint."
            };
        }

        if (dialogId.equalsIgnoreCase("bar_whisper_intro")) {
            return new String[] {
                    "Bottom of your screen: §bAetherion Level§f.",
                    "Stats, unlocks, new areas — a lot of this world opens through that bar.",
                    "Keep playing. It notices."
            };
        }

        if (dialogId.equalsIgnoreCase("vince_intro")) {
            return new String[] {
                    "Casino's behind me. Pick a machine. Don't cry on the felt.",
                    "Slots. Roulette. Coins in. Dignity stays in your pocket.",
                    "I don't deal. I commentate. Machines do the dirty work.",
                    "The glass is hungrier than you. Feed it anyway."
            };
        }

        if (dialogId.equalsIgnoreCase("liquidator_intro")) {
            return new String[] {
                    "Crystal desk. Buy, sell, or melt mats into Aether Crystals.",
                    "Crystals come from the official shop — or as level rewards."
            };
        }

        if (dialogId.equalsIgnoreCase("root_cellar_intro")) {
            return new String[] {
                    "Millstone Pantry. Compacted crops go in. Refined pantry goods come out."
            };
        }



        /*
         * =====================================================
         * UNKNOWN DIALOG
         * =====================================================
         */

        return new String[0];

    }

    private static boolean holdsCryptSpirit(Player player) {
        try {
            Class<?> rite = Class.forName("de.aetherion.items.world.BorderlandsRiteService");
            Object held = rite.getMethod("playerHoldsCryptSpirit", Player.class).invoke(null, player);
            return held instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean isColosseumTaught(Player player) {
        try {
            Class<?> gateCl = Class.forName("de.aetherion.items.world.ColosseumGateService");
            Object gate = gateCl.getMethod("get").invoke(null);
            if (gate == null) {
                return false;
            }
            Object taught = gateCl.getMethod("isTaught", Player.class).invoke(gate, player);
            return taught instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private void startColosseumEscort(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isColosseumTaught(player)) {
            LivingNpcProfile.say(player, "arena_proctor", "Proctor",
                    de.aetherion.quests.lang.LangPack.msg(
                            player,
                            "say.proctor.had_tour",
                            "You've already had the tour. Use the glowing mark."
                    ));
            return;
        }
        try {
            Class<?> rite = Class.forName("de.aetherion.items.world.BorderlandsRiteService");
            Object vialObj = rite.getMethod("findCryptSpirit", Player.class).invoke(null, player);
            if (!(vialObj instanceof ItemStack vial) || vial.getAmount() <= 0) {
                LivingNpcProfile.say(player, "arena_proctor", "Proctor",
                        de.aetherion.quests.lang.LangPack.msg(
                                player,
                                "say.proctor.bring_vial",
                                "Bring the Crypt vial when you're ready."
                        ));
                return;
            }
            String bossId = "pathwarden";
            try {
                Object bid = rite.getMethod("spiritBossId", ItemStack.class).invoke(null, vial);
                if (bid instanceof String s && !s.isBlank()) {
                    bossId = s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
            vial.setAmount(vial.getAmount() - 1);

            // Unlock soft gate.
            try {
                Class<?> gateCl = Class.forName("de.aetherion.items.world.ColosseumGateService");
                Object gate = gateCl.getMethod("get").invoke(null);
                if (gate != null) {
                    gateCl.getMethod("unlock", Player.class).invoke(gate, player);
                }
            } catch (ReflectiveOperationException ignored) {
            }

            org.bukkit.World world = Bukkit.getWorld("world");
            if (world == null) {
                player.sendMessage("§cColosseum world offline.");
                return;
            }
            // Pad coords — same as ColosseumArena in Items.
            Location pad = new Location(world, -362.5, 66.0, -110.5);
            LivingNpcService living = de.aetherion.quests.AetherionQuests.getInstance() != null
                    ? de.aetherion.quests.AetherionQuests.getInstance().getLivingNpcService()
                    : null;
            if (living == null || !living.available()) {
                player.sendMessage("§cProctor walk offline (FancyNpcs).");
                return;
            }
            // If a prior walk left him locked, clear so the demo can run once.
            if (living.isMovementLocked("arena_proctor")) {
                living.setMovementLocked("arena_proctor", false);
            }
            final String spawnBoss = bossId;
            LivingNpcProfile.say(player, "arena_proctor", "Proctor",
                    de.aetherion.quests.lang.LangPack.msg(
                            player,
                            "say.proctor.walk",
                            "Let's look at the ring. I'll check the vial on the way."
                    ));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 0.7f, 1.1f);
            // Walk pace + stay at pad through spill + 10s summon before walking home.
            boolean ok = living.scriptWalk(
                    "arena_proctor",
                    pad,
                    0.14,
                    // Leave mid-countdown — before the boss lands.
                    20L * 5L,
                    () -> {
                        spillColosseum(player, spawnBoss);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                LivingNpcProfile.say(player, "arena_proctor", "Proctor",
                                        de.aetherion.quests.lang.LangPack.msg(
                                                player,
                                                "say.proctor.nope",
                                                "Nope. Hazard pay's a myth. §fThat's a §cyou §fproblem. Bye."
                                        ));
                            }
                        }, 20L * 4L);
                    }
            );
            if (!ok) {
                player.sendMessage("§cProctor could not start walking.");
                spillColosseum(player, spawnBoss);
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            player.sendMessage("§cCould not start the Colosseum escort.");
            plugin.getLogger().warning("Colosseum escort failed: " + ex.getMessage());
        }
    }

    private void spillColosseum(Player player, String bossId) {
        try {
            Class<?> escort = Class.forName("de.aetherion.items.world.ColosseumEscortService");
            Object service = escort.getMethod("get").invoke(null);
            if (service == null) {
                player.sendMessage("§cColosseum spill offline.");
                return;
            }
            escort.getMethod("spillAtPad", Player.class, String.class).invoke(service, player, bossId);
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            plugin.getLogger().warning("Colosseum spill failed: " + ex.getMessage());
            player.sendMessage("§cSpill failed.");
        }
    }


    /**
     * Mini chat after the NPC's quest is done. Keep short. Egon is GUI-only.
     */
    private String[] getCompletedLines(String npcId, Player player) {
        if (npcId == null || npcId.isBlank()) {
            return new String[0];
        }

        return switch (npcId.toLowerCase()) {
            case "quartermaster" -> new String[] {
                    "Coal's filed. Mines teleport stays open — §e/mines§f.",
                    "Foreman still lives at the Mines if you need him."
            };
            case "hunter" -> new String[] {
                    "First blood's filed. Don't get sentimental about it.",
                    "Next time bring bigger problems. Or quieter footsteps."
            };
            case "lumberjack" -> new String[] {
                    "Egon got his wood. Keep the axe.",
                    "Follow the arrow back to §eEgon§f on the pier."
            };
            case "egon" -> new String[] {
                    "Logs received. Kit stays yours — don't lose it.",
                    "§eQuartermaster§f is past the little market — talk to him next.",
                    "From here on: watch the §eyellow arrow up top§f. That's your guide."
            };
            case "farmer" -> farmerCompletedLines(player);
            case "ledger" -> new String[] {
                    "Skill equipped. Good.",
                    "Next: the fields — §eFarmer§f (wheat) and §eLark§f (pets), same area.",
                    "Come back after both. I close the tutorial — and I keep a help menu if you get stuck."
            };
            case "lark" -> new String[] {
                    "Catch done. You're set with pets.",
                    "§eMiss Ledger§f closes orientation — or §e/capital§f. Questions go to her desk."
            };
            case "craftsman", "blacksmith" -> new String[] {
                    "Nice pick. You're set for the Mines.",
                    "Next: §eShaft Foreman§f. Keep the Recipe Book handy."
            };
            case "collector" -> new String[] {
                    "Shiny things received. My shelf is smug. You're dismissed.",
                    "Mine deeper if you miss the sound of rock judging you."
            };
            case "fisher", "fisherman" -> new String[] {
                    "Catch counted. Line clear. Peek at your rod sometime — certain tools level up around here."
            };
            case "fishmonger" -> new String[] {
                    "Shop. Rods. Armor. Talk to me anytime."
            };
            case "foreman" -> new String[] {
                    "Shift's closed. Next: §eTemper§f toward the hub — Booster Tutor.",
                    "Fuse one booster before skills."
            };
            case "booster_tutor" -> new String[] {
                    "Booster fused. Good. Recipes stay in the §eRecipe Book§f.",
                    "Next: §eMiss Ledger§f at Capital — Skills. Then the Fields."
            };
            case "surveyor" -> new String[] {
                    "Desk stays open. Collection up top, stamp a blueprint into its tool below.",
                    "Trolls crawl any vein in this mine. Dig when you're hungry for pages."
            };
            case "merchant" -> new String[] {
                    "Chests stay unlocked. Sample beside me, more out in the world. Rarer chest, better loot."
            };
            case "miner" -> new String[] {
                    "Hollow Lurker's quiet. My nerves aren't. Thanks for that.",
                    "If the tunnels whisper again, I'm blaming your footsteps."
            };
            case "chicken_keeper" -> new String[] {
                    "McNugget's handled. The coop still files complaints.",
                    "Don't mention grease. Fry Gossip hears everything."
            };
            case "tollkeeper" -> new String[] {
                    "Toll's paid in troll. Bridge is smug. Keep walking.",
                    "Next crossing costs charm. You're overdrawn."
            };
            case "dockhand" -> new String[] {
                    "Ink contract closed. Squidward's a rumor again. Good.",
                    "Dock stays wet. So does my patience. Leave both alone."
            };
            case "ash_scout" -> new String[] {
                    "Ash filed. Arrows accounted for. Skuldugery's someone else's headache now.",
                    "Don't poke embers for fun. That's my job. Allegedly."
            };
            case "colossus_scholar" -> new String[] {
                    "Walking mountain: noted. Thesis: overdue. You: free.",
                    "If it stands up again, I was never here."
            };
            case "veil_priest" -> new String[] {
                    "The veil holds. Your curiosity does not. Restrain it.",
                    "Nyx keeps secrets. You keep walking."
            };
            case "patch_intern" -> new String[] {
                    "Ticket closed. Sir Balthazar patched. Intern still unpaid.",
                    "Please rate this interaction: one star, two sighs."
            };
            case "void_janitor" -> new String[] {
                    "Lost and found is empty. Lobby is judgmental. Mop is holy.",
                    "Don't track void on the tile. I just waxed existential dread."
            };
            case "fuse" -> new String[] {
                    "Overtime logged. Sparky's quiet. My shift isn't.",
                    "If something sparks, walk away. Dramatically."
            };
            case "claims_adjuster" -> new String[] {
                    "Claim denied. Then un-denied. Bureaucracy is an extreme sport.",
                    "Wormsworth stamps. You leave. Beautiful system."
            };
            case "repo_agent" -> new String[] {
                    "Check bounced. Then bounced back. Audit's done smirking.",
                    "Bring fewer IOUs next time. Bring more dignity."
            };
            case "vex" -> new String[] {
                    "Ten down. Steel filed. You're less of a liability.",
                    "Soft tip — combat set and boosters before the harder waste. Then Rite Warden if you want bosses."
            };
            case "rook" -> new String[] {
                    "Bones delivered. Curriculum chewed. Class dismissed.",
                    "Rook keeps receipts. You keep distance."
            };
            case "gate_warden" -> new String[] {
                    "Road's open. Gate's bored. Don't loiter like a tourist.",
                    "Closed roads reopen for people who finish jobs. Like you did."
            };
            case "bench_cynic" -> new String[] {
                    "Flesh researched. Standards unmet. Bench still occupied.",
                    "Don't sit. The disappointment is reserved."
            };
            case "dust" -> new String[] {
                    "Cobble stacked. Ambition briefly less dusty. Move along.",
                    "Maps can wait. Your feet shouldn't."
            };
            case "larder" -> new String[] {
                    "Pantry's less tragic. Wheat compressed. Ego inflated. Done.",
                    "Come back hungry, not nostalgic."
            };
            case "pet_scout" -> new String[] {
                    "Pig proof accepted. Pocket still squeals. Proud of you. Mildly.",
                    "Next catch: aim higher. Literally. Cows have altitude."
            };
            case "ore_ledger" -> new String[] {
                    "Coal audit closed. Numbers behaved. Miracle noted.",
                    "Don't forge receipts. The mountain already tried."
            };
            case "timber_clerk" -> new String[] {
                    "Stump census complete. Forest quieter. Clipboard happier.",
                    "Taxed wood only. Untaxed wood is a lifestyle I won't enable."
            };
            case "canopy_clerk" -> new String[] {
                    "Samples filed. Oak, birch, spruce — canopy approved.",
                    "Come back when the isle invents a fourth tree. Until then: chop."
            };
            case "dock_scaler" -> new String[] {
                    "Scale sample filed. Cod compressed. Clipboard still wet. Fine.",
                    "Next time bring fish that respect units."
            };
            case "sphere_proctor" -> new String[] {
                    "Bovine brief closed. Cow in custody. Midterm graded: pass.",
                    "No steak jokes. I've heard them all. Poorly."
            };
            case "quarry_broker" -> new String[] {
                    "Guild brick received. Club adjacent. Handshake imaginary.",
                    "Compacted means compacted. Come back when you forget that — I dare you."
            };
            default -> new String[] {
                    "We're squared. Clipboard says so.",
                    "Go make someone else's day slightly worse."
            };
        };
    }

    private String[] craftsmanIntroLines(Player player) {
        return new String[] {
                "Crafting's unlocked. Recipe Book lives in the Manager.",
                "Nether Star (hotbar §e9§f) → click the §agreen book§f. Every blueprint's there.",
                "Craft a §fMining Pickaxe§f: Simple Pickaxe in the middle, coal around it. Bring that pick back."
        };
    }

    private String[] farmerIntroLines(Player player) {
        boolean larkDone = player != null
                && QuestStoryGate.questCompleted(player, questManager, "pocket_zoo");
        if (larkDone) {
            return new String[] {
                    "Wheat from these fields. Birds off the crops.",
                    "Break wheat until you have §e48§f. Birds land? §eClick§f them — bossbar counts shoos."
            };
        }
        return new String[] {
                "Wheat from these fields. Birds off the crops.",
                "Break wheat until you have §e48§f. Birds land? §eClick§f them — bossbar counts shoos.",
                "§eLark§f is in the same area for pets after this. Different quest — the arrow moves when you're ready."
        };
    }

    private String[] farmerCompletedLines(Player player) {
        boolean larkDone = player != null
                && QuestStoryGate.questCompleted(player, questManager, "pocket_zoo");
        if (larkDone) {
            return new String[] {
                    "Wheat's done. Nice work.",
                    "Back to §eMiss Ledger§f for the stamp — or §e/capital§f if you know the way."
            };
        }
        return new String[] {
                "Wheat's done. Nice work.",
                "Finish §eLark§f if you haven't, then §eMiss Ledger§f — or §e/capital§f."
        };
    }

    private String[] foremanIntroLines(Player player) {
        boolean craftsmanSpoken = false;
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getPlayerQuestStorage() != null && player != null) {
            craftsmanSpoken = plugin.getPlayerQuestStorage()
                    .hasStarterKit(player.getUniqueId(), "craftsman_spoken");
        }
        // Backup unlock if they skipped Craftsman.
        if (!craftsmanSpoken) {
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.unlock(player, "WORKBENCH", "Crafting + Recipes", "Manager → green Recipe Book");
            }
            if (plugin != null && plugin.getPlayerQuestStorage() != null && player != null) {
                plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), "craftsman_spoken");
            }
            return new String[] {
                    "Shabby Mine — cave behind me. Dig §ecoal, copper, iron§f. Break §e32 ore blocks§f, then come back.",
                    "Crafting's open — green Recipe Book in the Manager. A mining pickaxe and some armor help down there."
            };
        }
        return new String[] {
                "Shabby Mine — cave behind me. Dig §ecoal, copper, iron§f. Break §e32 ore blocks§f, then come back.",
                "Mining pickaxe and some armor help. Recipe Book in the Manager if you still need them."
        };
    }

    public static boolean playerHasItemId(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getItemManager() == null) {
                return false;
            }
            for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                String id = items.getItemManager().getItemId(stack);
                if (id != null && id.equalsIgnoreCase(itemId)) {
                    return true;
                }
            }
            org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && !off.getType().isAir()) {
                String id = items.getItemManager().getItemId(off);
                return id != null && id.equalsIgnoreCase(itemId);
            }
        } catch (NoClassDefFoundError ignored) {
        }
        return false;
    }

}