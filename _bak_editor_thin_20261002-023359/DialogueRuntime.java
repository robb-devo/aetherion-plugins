package de.aetherion.quests.editor;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.editor.gui.EditorItems;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays editor-NPC dialogue (chat lines + choice GUI) and runs simple actions.
 */
public final class DialogueRuntime implements Listener, EditorQuestHook {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "op", "deop", "stop", "reload", "restart", "ban", "ban-ip", "pardon",
            "kick", "whitelist", "luckperms", "lp", "pex", "execute", "function",
            "minecraft:op", "minecraft:deop", "minecraft:stop", "minecraft:reload",
            "minecraft:ban", "minecraft:kick", "plugman", "spark", "timings",
            "aetherion", "aquest", "questnpc"
    );

    private final AetherionQuests plugin;
    private final CustomNpcStorage storage;
    private final Map<UUID, BukkitTask> speaking = new ConcurrentHashMap<>();

    public DialogueRuntime(AetherionQuests plugin, CustomNpcStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void talk(Player player, CustomNpc npc) {
        if (player == null || npc == null) {
            return;
        }
        playPage(player, npc, npc.getStartPage());
    }

    public void playPage(Player player, CustomNpc npc, String pageId) {
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
        List<String> lines = page.lines().stream().filter(line -> line != null && !line.isBlank()).toList();
        CustomNpc.DialoguePage shown = page;
        if (lines.isEmpty()) {
            afterLines(player, npc, shown);
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
                    afterLines(player, npc, shown);
                    return;
                }
                String line = lines.get(index++);
                player.sendMessage("§b" + npc.getName() + " §8⟫ §f" + line);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.35f);
            }
        }, 0L, DialogPace.LINE_GAP_TICKS);
        speaking.put(player.getUniqueId(), task);
    }

    private void afterLines(Player player, CustomNpc npc, CustomNpc.DialoguePage page) {
        if (!page.choices().isEmpty()) {
            ChoiceMenu.open(player, npc, page);
            return;
        }
        if (npc.hasLinkedQuest()) {
            offer(player, npc, npc.getLinkedQuestId());
        }
    }

    public void runChoice(Player player, CustomNpc npc, CustomNpc.DialogueChoice choice) {
        if (player == null || npc == null || choice == null) {
            return;
        }
        switch (choice.action()) {
            case CLOSE -> player.closeInventory();
            case PAGE -> playPage(player, npc, choice.target());
            case RUN_CONSOLE -> runCommand(player, choice.target(), true);
            case RUN_PLAYER -> runCommand(player, choice.target(), false);
            case OFFER_QUEST -> offer(player, npc, resolveQuest(npc, choice.target()));
            case START_QUEST -> start(player, npc, resolveQuest(npc, choice.target()));
            case TURN_IN_QUEST -> turnIn(player, npc, resolveQuest(npc, choice.target()));
        }
    }

    private static String resolveQuest(CustomNpc npc, String target) {
        if (target != null && !target.isBlank()) {
            return target;
        }
        return npc.getLinkedQuestId();
    }

    @Override
    public void offer(Player player, CustomNpc npc, String questId) {
        DialogManager dialogs = plugin.getDialogManager();
        if (dialogs == null || questId == null || questId.isBlank()) {
            if (player != null && (questId == null || questId.isBlank())) {
                player.sendMessage("§cNo quest linked.");
            }
            return;
        }
        dialogs.offerQuestById(player, npc.getName(), questId);
    }

    @Override
    public void start(Player player, CustomNpc npc, String questId) {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null || player == null || questId == null) {
            return;
        }
        Quest quest = quests.getQuest(questId);
        if (quest == null) {
            player.sendMessage("§cQuest not found: §f" + questId);
            return;
        }
        quests.startQuest(player, quest);
    }

    @Override
    public void turnIn(Player player, CustomNpc npc, String questId) {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null || player == null || questId == null) {
            return;
        }
        Quest quest = quests.getQuest(questId);
        if (quest == null) {
            player.sendMessage("§cQuest not found: §f" + questId);
            return;
        }
        QuestState state = quests.getQuestState(player, quest);
        if (state == QuestState.READY) {
            quests.completeQuest(player, quest);
            return;
        }
        if (state == QuestState.ACTIVE) {
            player.sendMessage("§eNot ready to turn that in yet.");
            return;
        }
        player.sendMessage("§7You don't have §f" + quest.getTitle() + " §7active.");
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

    static String sanitize(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String command = raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.length() > 128 || command.contains("\n") || command.contains("&&") || command.contains("|")) {
            return null;
        }
        command = command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (BLOCKED_COMMANDS.contains(root) || root.startsWith("lp:") || root.contains("luckperms")) {
            return null;
        }
        return command;
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
        if (!(event.getInventory().getHolder() instanceof ChoiceMenu.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        CustomNpc npc = storage.get(holder.npcId());
        CustomNpc.DialoguePage page = npc == null ? null : npc.page(holder.pageId());
        if (npc == null || page == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        int index = slot - 10;
        if (index < 0 || index >= page.choices().size()) {
            return;
        }
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.2f);
        runChoice(player, npc, page.choices().get(index));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChoiceMenu.Holder) {
            event.setCancelled(true);
        }
    }

    static final class ChoiceMenu {
        private ChoiceMenu() {
        }

        static void open(Player player, CustomNpc npc, CustomNpc.DialoguePage page) {
            Holder holder = new Holder(npc.getId(), page.id());
            Inventory inventory = Bukkit.createInventory(holder, 27, "§8" + npc.getName());
            EditorItems.fill(inventory);
            inventory.setItem(4, EditorItems.button(
                    org.bukkit.Material.BOOK,
                    "§b" + npc.getName(),
                    "§7Pick a line."
            ));
            int slot = 10;
            for (CustomNpc.DialogueChoice choice : page.choices()) {
                if (slot > 16) {
                    break;
                }
                inventory.setItem(slot++, EditorItems.button(
                        org.bukkit.Material.PAPER,
                        "§e" + choice.text(),
                        "§8" + choice.action().label()
                ));
            }
            player.openInventory(inventory);
        }

        record Holder(String npcId, String pageId) implements InventoryHolder {
            @Override
            public Inventory getInventory() {
                return null;
            }
        }
    }
}
