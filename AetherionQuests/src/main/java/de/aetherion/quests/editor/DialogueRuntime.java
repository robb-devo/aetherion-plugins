package de.aetherion.quests.editor;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.editor.gui.EditorItems;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Plays editor-NPC dialogue (chat lines + reply GUI) and runs reply actions.
 * <p>
 * Real mode is what players get. Preview mode (from the studio) plays the same pages but only
 * <em>describes</em> commands and quest actions, so testing never gives items or starts quests.
 */
public final class DialogueRuntime implements Listener, EditorQuestHook {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "op", "deop", "stop", "reload", "restart", "ban", "ban-ip", "pardon",
            "kick", "whitelist", "luckperms", "lp", "pex", "execute", "function",
            "minecraft:op", "minecraft:deop", "minecraft:stop", "minecraft:reload",
            "minecraft:ban", "minecraft:kick", "plugman", "spark", "timings",
            "aetherion", "aquest", "questnpc",
            "sudo", "rl", "save-off", "npc", "aethernpc", "npceditor"
    );

    /** How a conversation is played. {@code stage} = simulated quest stage for previews. */
    public record Mode(boolean preview, QuestState stage, Consumer<Player> onEnd) {
        public static final Mode REAL = new Mode(false, null, null);
    }

    private final AetherionQuests plugin;
    private final CustomNpcStorage storage;
    private final Map<UUID, BukkitTask> speaking = new ConcurrentHashMap<>();

    public DialogueRuntime(AetherionQuests plugin, CustomNpcStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** A player talks to the NPC for real — opens on the page for their quest stage. */
    public void talk(Player player, CustomNpc npc) {
        if (player == null || npc == null) {
            return;
        }
        play(player, npc, npc.startPageFor(stageOf(player, npc)), Mode.REAL);
    }

    /**
     * Safe preview for editors.
     *
     * @param pageId page to start on, or null for the page the stage opens on
     * @param stage  simulated stage of the main quest, or null for the editor's real stage
     */
    public void preview(Player player, CustomNpc npc, String pageId, QuestState stage, Consumer<Player> onEnd) {
        if (player == null || npc == null) {
            return;
        }
        QuestState effective = stage != null ? stage : stageOf(player, npc);
        String start = pageId != null ? pageId : npc.startPageFor(effective);
        player.sendMessage("");
        String who = stage == null ? "as you" : "as a player whose quest is §f" + NpcCheck.stageName(stage);
        player.sendMessage("§8[Preview] §7" + npc.getName() + " §8· §7" + who + " §8· §7nothing runs for real");
        play(player, npc, start, new Mode(true, effective, onEnd));
    }

    /** Kept for older callers — plays one page for real. */
    public void playPage(Player player, CustomNpc npc, String pageId) {
        play(player, npc, pageId, Mode.REAL);
    }

    /** The player's stage for the NPC's main quest ({@link QuestState#AVAILABLE} when there is none). */
    public QuestState stageOf(Player player, CustomNpc npc) {
        if (player == null || npc == null || !npc.hasLinkedQuest()) {
            return QuestState.AVAILABLE;
        }
        QuestState state = stateOf(player, npc.getLinkedQuestId());
        return state == null ? QuestState.AVAILABLE : state;
    }

    private QuestState stateOf(Player player, String questId) {
        QuestManager quests = plugin.getQuestManager();
        Quest quest = quests == null || questId == null ? null : quests.getQuest(questId);
        if (quest == null) {
            return null;
        }
        return quests.getQuestState(player, quest);
    }

    private void play(Player player, CustomNpc npc, String pageId, Mode mode) {
        if (player == null || npc == null) {
            return;
        }
        CustomNpc.DialoguePage page = npc.page(pageId);
        if (page == null) {
            page = npc.page(npc.getStartPage());
        }
        if (page == null) {
            return;
        }
        cancel(player.getUniqueId());
        List<String> lines = page.spokenLines();
        CustomNpc.DialoguePage shown = page;
        if (lines.isEmpty()) {
            afterLines(player, npc, shown, mode, null);
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int index;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(player.getUniqueId());
                    return;
                }
                if (index >= lines.size()) {
                    cancel(player.getUniqueId());
                    afterLines(player, npc, shown, mode, fill(lines.get(lines.size() - 1), player));
                    return;
                }
                String line = lines.get(index++);
                player.sendMessage("§b" + npc.getName() + " §8⟫ §f" + fill(line, player));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.35f);
            }
        }, 0L, DialogPace.LINE_GAP_TICKS);
        speaking.put(player.getUniqueId(), task);
    }

    private void afterLines(Player player, CustomNpc npc, CustomNpc.DialoguePage page, Mode mode, String lastLine) {
        if (!page.choices().isEmpty()) {
            ChoiceScreen.open(player, npc, page, mode, lastLine);
            return;
        }
        // Legacy behaviour: a page without replies offers the main quest (silently skipped once started).
        if (npc.hasLinkedQuest() && npc.isAutoQuest()) {
            if (mode.preview()) {
                QuestState stage = previewStage(player, npc, npc.getLinkedQuestId(), mode);
                player.sendMessage("§8[Preview] §7No replies here → " + (stage == QuestState.AVAILABLE
                        ? "offers §f" + questTitle(npc.getLinkedQuestId()) + "§7 (Accept / Decline)."
                        : "chat ends (quest already " + NpcCheck.stageName(stage).toLowerCase(Locale.ROOT) + ")."));
            } else if (stageOf(player, npc) == QuestState.AVAILABLE) {
                offer(player, npc, npc.getLinkedQuestId());
                return;
            }
        }
        end(player, mode);
    }

    private void end(Player player, Mode mode) {
        if (mode != null && mode.preview() && mode.onEnd() != null && player.isOnline()) {
            mode.onEnd().accept(player);
        }
    }

    public void runChoice(Player player, CustomNpc npc, CustomNpc.DialogueChoice choice) {
        runChoice(player, npc, choice, Mode.REAL);
    }

    private void runChoice(Player player, CustomNpc npc, CustomNpc.DialogueChoice choice, Mode mode) {
        if (player == null || npc == null || choice == null) {
            return;
        }
        String questId = npc.questFor(choice);
        switch (choice.action()) {
            case CLOSE -> end(player, mode);
            case PAGE -> play(player, npc, choice.target(), mode);
            case RUN_CONSOLE, RUN_PLAYER -> {
                boolean console = choice.action() == DialogueAction.RUN_CONSOLE;
                if (mode.preview()) {
                    String command = sanitize(player, choice.target());
                    player.sendMessage(command == null
                            ? "§8[Preview] §cBlocked or empty command §7— players would see an error."
                            : "§8[Preview] §7Runs §f/" + command + " §7as " + (console ? "the server" : "the player") + ".");
                    end(player, mode);
                } else {
                    runCommand(player, choice.target(), console);
                }
            }
            case OFFER_QUEST -> {
                if (mode.preview()) {
                    describeQuest(player, npc, questId, DialogueAction.OFFER_QUEST, mode);
                } else {
                    offer(player, npc, questId);
                }
            }
            case START_QUEST -> {
                if (mode.preview()) {
                    describeQuest(player, npc, questId, DialogueAction.START_QUEST, mode);
                } else {
                    start(player, npc, questId);
                }
            }
            case TURN_IN_QUEST -> {
                if (mode.preview()) {
                    describeQuest(player, npc, questId, DialogueAction.TURN_IN_QUEST, mode);
                } else {
                    turnIn(player, npc, questId);
                }
            }
        }
    }

    private QuestState previewStage(Player player, CustomNpc npc, String questId, Mode mode) {
        if (questId != null && questId.equalsIgnoreCase(npc.getLinkedQuestId()) && mode.stage() != null) {
            return mode.stage();
        }
        return stateOf(player, questId);
    }

    private void describeQuest(Player player, CustomNpc npc, String questId, DialogueAction action, Mode mode) {
        String prefix = "§8[Preview] §7";
        if (questId == null || questId.isBlank()) {
            player.sendMessage(prefix + "§cNo quest picked §7— players would be told there's nothing for them.");
            end(player, mode);
            return;
        }
        QuestState stage = previewStage(player, npc, questId, mode);
        String title = "§f" + questTitle(questId) + "§7";
        if (stage == null) {
            player.sendMessage(prefix + "§cQuest '" + questId + "' doesn't exist §7— players would see an error.");
            end(player, mode);
            return;
        }
        String text = switch (action) {
            case OFFER_QUEST -> switch (stage) {
                case AVAILABLE -> "Shows " + title + " with Accept / Decline.";
                case ACTIVE -> "Player is already on " + title + " — they're told so.";
                case READY -> "Player finished " + title + " — they're told to hand it in.";
                case COMPLETED -> "Player already completed " + title + " — they're told so.";
            };
            case START_QUEST -> switch (stage) {
                case AVAILABLE -> "Starts " + title + " right away (cancels any other active quest).";
                case ACTIVE, READY -> "Player already has " + title + " — it just gets tracked.";
                case COMPLETED -> "Player already completed " + title + " — nothing happens.";
            };
            default -> switch (stage) {
                case READY -> "Completes " + title + " and pays the rewards.";
                case ACTIVE -> "Player isn't finished with " + title + " — they're told so.";
                case AVAILABLE -> "Player never started " + title + " — they're told so.";
                case COMPLETED -> "Player already turned in " + title + " — they're told so.";
            };
        };
        player.sendMessage(prefix + text);
        end(player, mode);
    }

    private String questTitle(String questId) {
        QuestManager quests = plugin.getQuestManager();
        Quest quest = quests == null ? null : quests.getQuest(questId);
        return quest == null ? questId : QuestCatalog.title(quest);
    }

    @Override
    public void offer(Player player, CustomNpc npc, String questId) {
        if (player == null) {
            return;
        }
        if (questId == null || questId.isBlank()) {
            player.sendMessage("§7" + npc.getName() + " has nothing for you right now.");
            return;
        }
        QuestManager quests = plugin.getQuestManager();
        DialogManager dialogs = plugin.getDialogManager();
        Quest quest = quests == null ? null : quests.getQuest(questId);
        if (quest == null || dialogs == null) {
            player.sendMessage("§cThat quest isn't available right now.");
            plugin.getLogger().warning("Editor NPC " + npc.getId() + " offers unknown quest '" + questId + "'.");
            return;
        }
        QuestState state = quests.getQuestState(player, quest);
        switch (state == null ? QuestState.AVAILABLE : state) {
            case AVAILABLE -> dialogs.offerQuestById(player, npc.getName(), quest.getId());
            case ACTIVE -> player.sendMessage("§7You're already working on §f" + QuestCatalog.title(quest) + "§7.");
            case READY -> player.sendMessage("§aYou've finished §f" + QuestCatalog.title(quest) + "§a — hand it in!");
            case COMPLETED -> player.sendMessage("§7You've already completed §f" + QuestCatalog.title(quest) + "§7.");
        }
    }

    @Override
    public void start(Player player, CustomNpc npc, String questId) {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null || player == null || questId == null || questId.isBlank()) {
            if (player != null) {
                player.sendMessage("§7" + npc.getName() + " has nothing for you right now.");
            }
            return;
        }
        Quest quest = quests.getQuest(questId);
        if (quest == null) {
            player.sendMessage("§cThat quest isn't available right now.");
            plugin.getLogger().warning("Editor NPC " + npc.getId() + " starts unknown quest '" + questId + "'.");
            return;
        }
        if (quests.getQuestState(player, quest) == QuestState.COMPLETED) {
            player.sendMessage("§7You've already completed §f" + QuestCatalog.title(quest) + "§7.");
            return;
        }
        quests.startQuest(player, quest);
    }

    @Override
    public void turnIn(Player player, CustomNpc npc, String questId) {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null || player == null || questId == null || questId.isBlank()) {
            if (player != null) {
                player.sendMessage("§7" + npc.getName() + " isn't expecting anything from you.");
            }
            return;
        }
        Quest quest = quests.getQuest(questId);
        if (quest == null) {
            player.sendMessage("§cThat quest isn't available right now.");
            plugin.getLogger().warning("Editor NPC " + npc.getId() + " turns in unknown quest '" + questId + "'.");
            return;
        }
        QuestState state = quests.getQuestState(player, quest);
        String title = QuestCatalog.title(quest);
        if (state == QuestState.READY) {
            quests.completeQuest(player, quest);
            return;
        }
        if (state == QuestState.ACTIVE) {
            player.sendMessage("§eYou're not done with §f" + title + " §eyet.");
            return;
        }
        if (state == QuestState.COMPLETED) {
            player.sendMessage("§7You've already turned in §f" + title + "§7.");
            return;
        }
        player.sendMessage("§7You don't have §f" + title + " §7active.");
    }

    private void runCommand(Player player, String raw, boolean console) {
        String command = sanitize(player, raw);
        if (command == null) {
            player.sendMessage("§cThat command is not allowed on an NPC.");
            return;
        }
        if (console) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            player.performCommand(command);
        }
    }

    /** Resolves placeholders and blocks dangerous commands; null when not allowed. */
    public static String sanitize(Player player, String raw) {
        if (commandProblem(raw) != null) {
            return null;
        }
        String command = strip(raw);
        return command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
    }

    /** Why a reply command can't run, in plain words — or null when it's allowed. */
    public static String commandProblem(String raw) {
        if (raw == null || raw.isBlank()) {
            return "no command set yet.";
        }
        String command = strip(raw);
        if (command.isEmpty()) {
            return "no command set yet.";
        }
        if (command.length() > 128) {
            return "the command is too long (max 128).";
        }
        if (command.contains("\n") || command.contains("&&") || command.contains("|")) {
            return "chaining commands (&&, |) isn't allowed.";
        }
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String base = root.contains(":") ? root.substring(root.indexOf(':') + 1) : root;
        if (root.startsWith("/")
                || BLOCKED_COMMANDS.contains(root)
                || BLOCKED_COMMANDS.contains(base)
                || root.startsWith("lp:")
                || root.contains("luckperms")) {
            return "/" + root + " is blocked for safety.";
        }
        return null;
    }

    private static String strip(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command;
    }

    /** Dialogue placeholders: {player}. */
    public static String fill(String line, Player player) {
        if (line == null) {
            return "";
        }
        return player == null ? line : line.replace("{player}", player.getName());
    }

    private void cancel(UUID playerId) {
        BukkitTask task = speaking.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void forget(UUID playerId) {
        cancel(playerId);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChoiceScreen.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Integer index = holder.slots().get(event.getRawSlot());
        if (index == null || index < 0 || index >= holder.choices().size() || holder.chosen) {
            return;
        }
        holder.chosen = true;
        CustomNpc.DialogueChoice choice = holder.choices().get(index);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.2f);
            CustomNpc npc = storage.get(holder.npcId());
            if (npc == null) {
                npc = holder.npc();
            }
            runChoice(player, npc, choice, holder.mode());
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChoiceScreen.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChoiceScreen.Holder holder) || holder.chosen) {
            return;
        }
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER && event.getPlayer() instanceof Player player) {
            holder.chosen = true;
            end(player, holder.mode());
        }
    }

    /** The reply picker players see after the NPC finishes a page. */
    static final class ChoiceScreen {

        private static final int[][] LAYOUTS = {
                {13},
                {12, 14},
                {11, 13, 15},
                {10, 12, 14, 16},
                {9, 11, 13, 15, 17},
                {10, 11, 12, 14, 15, 16},
                {10, 11, 12, 13, 14, 15, 16}
        };

        private ChoiceScreen() {
        }

        static void open(Player player, CustomNpc npc, CustomNpc.DialoguePage page, Mode mode, String lastLine) {
            List<CustomNpc.DialogueChoice> choices = new ArrayList<>();
            for (CustomNpc.DialogueChoice choice : page.choices()) {
                if (choices.size() >= LAYOUTS.length) {
                    break;
                }
                choices.add(choice.copy());
            }
            Map<Integer, Integer> slots = new HashMap<>();
            Holder holder = new Holder(npc.getId(), npc, choices, slots, mode);
            Inventory inventory = Bukkit.createInventory(holder, 27, "§8" + npc.getName());
            holder.inventory = inventory;
            EditorItems.fill(inventory);
            List<String> lore = new ArrayList<>();
            if (lastLine != null && !lastLine.isBlank()) {
                for (String part : EditorItems.wrap("\"" + lastLine + "\"", 34)) {
                    lore.add("§f" + part);
                }
            }
            lore.add("");
            lore.add("§7Pick your reply.");
            inventory.setItem(4, EditorItems.icon(Material.PLAYER_HEAD)
                    .skull(npc.getSkinUsername())
                    .name("§b§l" + npc.getName())
                    .lore(lore)
                    .build());
            int[] layout = LAYOUTS[Math.max(0, choices.size() - 1)];
            for (int i = 0; i < choices.size(); i++) {
                CustomNpc.DialogueChoice choice = choices.get(i);
                String hint = choice.action().playerHint();
                EditorItems.Builder button = EditorItems.icon(choice.action().isQuest() ? Material.WRITABLE_BOOK : Material.PAPER)
                        .name("§e" + fill(choice.text(), player));
                if (hint != null) {
                    button.lore("§8" + hint);
                }
                inventory.setItem(layout[i], button.build());
                slots.put(layout[i], i);
            }
            if (mode.preview()) {
                inventory.setItem(22, EditorItems.icon(Material.GRAY_DYE)
                        .name("§7Preview")
                        .lore("§8Replies are only described —", "§8nothing runs for real.")
                        .build());
            }
            player.openInventory(inventory);
        }

        static final class Holder implements InventoryHolder {
            private final String npcId;
            private final CustomNpc npc;
            private final List<CustomNpc.DialogueChoice> choices;
            private final Map<Integer, Integer> slots;
            private final Mode mode;
            private Inventory inventory;
            private boolean chosen;

            Holder(String npcId, CustomNpc npc, List<CustomNpc.DialogueChoice> choices,
                   Map<Integer, Integer> slots, Mode mode) {
                this.npcId = npcId;
                this.npc = npc;
                this.choices = choices;
                this.slots = slots;
                this.mode = mode;
            }

            String npcId() {
                return npcId;
            }

            CustomNpc npc() {
                return npc;
            }

            List<CustomNpc.DialogueChoice> choices() {
                return choices;
            }

            Map<Integer, Integer> slots() {
                return slots;
            }

            Mode mode() {
                return mode;
            }

            @Override
            public Inventory getInventory() {
                return inventory;
            }
        }
    }
}
