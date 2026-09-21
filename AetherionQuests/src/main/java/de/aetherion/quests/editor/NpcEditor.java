package de.aetherion.quests.editor;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.editor.gui.AppearanceMenu;
import de.aetherion.quests.editor.gui.ConfirmMenu;
import de.aetherion.quests.editor.gui.DialogueMenu;
import de.aetherion.quests.editor.gui.EditMenu;
import de.aetherion.quests.editor.gui.GatherItemMenu;
import de.aetherion.quests.editor.gui.HelpMenu;
import de.aetherion.quests.editor.gui.ListMenu;
import de.aetherion.quests.editor.gui.MainMenu;
import de.aetherion.quests.editor.gui.ObjectiveMenu;
import de.aetherion.quests.editor.gui.QuestHubMenu;
import de.aetherion.quests.editor.gui.RewardsMenu;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-game FancyNPC + quest creator for content helpers.
 * Permission: {@link #PERMISSION}. Does not touch story NPCs in {@code npcs.yml}.
 */
public final class NpcEditor {

    public static final String PERMISSION = "aetherion.npc.editor";
    public static final long PROMPT_TIMEOUT_MS = 60_000L;

    private final AetherionQuests plugin;
    private final CustomNpcStorage storage;
    private final CustomNpcService service;
    private final DialogueRuntime runtime;
    private final EditorSessions sessions;
    private final EditorQuestStorage editorQuests;
    private final NamespacedKey wandKey;
    private final Map<UUID, Integer> lastClickTick = new ConcurrentHashMap<>();

    public NpcEditor(AetherionQuests plugin) {
        this.plugin = plugin;
        this.storage = new CustomNpcStorage(plugin);
        this.service = new CustomNpcService(plugin, storage);
        this.runtime = new DialogueRuntime(plugin, storage);
        this.sessions = new EditorSessions();
        this.editorQuests = new EditorQuestStorage(plugin);
        this.wandKey = new NamespacedKey(plugin, "npc_editor_wand");
    }

    public void enable() {
        NpcEditorCommand command = new NpcEditorCommand(this);
        if (plugin.getCommand("aethernpc") != null) {
            plugin.getCommand("aethernpc").setExecutor(command);
            plugin.getCommand("aethernpc").setTabCompleter(command);
        }
        new NpcEditorListener(this);
        new MainMenu(this);
        new EditMenu(this);
        new ListMenu(this);
        new AppearanceMenu(this);
        new DialogueMenu(this);
        new QuestHubMenu(this);
        new ConfirmMenu(this);
        new HelpMenu(this);
        new RewardsMenu(this);
        new ObjectiveMenu(this);
        new GatherItemMenu(this);
        CustomNpcInteractListener.register(this);
    }

    public void loadQuests() {
        editorQuests.registerAll(plugin.getQuestManager());
    }

    public void restore() {
        service.restoreAll();
    }

    public void disable() {
        service.shutdown();
    }

    public AetherionQuests plugin() {
        return plugin;
    }

    public CustomNpcStorage storage() {
        return storage;
    }

    public CustomNpcService service() {
        return service;
    }

    public DialogueRuntime runtime() {
        return runtime;
    }

    public EditorSessions sessions() {
        return sessions;
    }

    public EditorQuestStorage editorQuests() {
        return editorQuests;
    }

    public static boolean allowed(Player player) {
        return player != null && player.hasPermission(PERMISSION);
    }

    public void openMain(Player player) {
        cancelPrompt(player);
        MainMenu.open(player);
    }

    public void openEdit(Player player, CustomNpc npc) {
        if (npc == null) {
            player.sendMessage(LangPack.ui(player, "editor_npc_gone", "§cNPC not found."));
            return;
        }
        cancelPrompt(player);
        sessions.of(player).setNpcId(npc.getId());
        EditMenu.open(player, npc);
    }

    public void reopen(Player player) {
        EditorSessions.Session session = sessions.of(player);
        CustomNpc npc = storage.get(session.npcId());
        switch (session.returnTo()) {
            case MAIN -> MainMenu.open(player);
            case APPEARANCE -> {
                if (npc != null) {
                    AppearanceMenu.open(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
            case DIALOGUE_TREE -> {
                if (npc != null) {
                    DialogueMenu.openTree(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
            case DIALOGUE_PAGE -> {
                if (npc != null) {
                    DialogueMenu.openPage(player, npc, session.pageId());
                } else {
                    MainMenu.open(player);
                }
            }
            case CHOICE -> {
                if (npc != null) {
                    DialogueMenu.openChoice(player, npc, session.pageId(), session.choiceIndex());
                } else {
                    MainMenu.open(player);
                }
            }
            case QUEST_HUB -> {
                if (npc != null) {
                    QuestHubMenu.open(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
            case QUEST_PICK -> {
                if (npc != null) {
                    QuestHubMenu.openPick(player, npc, 0);
                } else {
                    MainMenu.open(player);
                }
            }
            case REWARDS -> {
                if (npc != null) {
                    RewardsMenu.open(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
            case OBJECTIVE -> {
                if (npc != null) {
                    ObjectiveMenu.open(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
            case GATHER -> {
                if (npc != null) {
                    GatherItemMenu.reopen(player, npc, session);
                } else {
                    MainMenu.open(player);
                }
            }
            case EDIT -> {
                if (npc != null) {
                    EditMenu.open(player, npc);
                } else {
                    MainMenu.open(player);
                }
            }
        }
    }

    public void beginCreate(Player player) {
        EditorSessions.Session session = sessions.of(player);
        session.setNpcId(null);
        session.setReturnTo(EditorScreen.MAIN);
        prompt(player, EditorSessions.Prompt.NAME, EditorScreen.MAIN,
                LangPack.ui(player, "editor_prompt_name", "Type a name for the new NPC"),
                null);
    }

    public CustomNpc createAt(Player player, String name) {
        if (!service.available()) {
            player.sendMessage(LangPack.ui(player, "editor_no_fancy",
                    "§cFancyNpcs is not loaded. Ask an admin to install it, then restart."));
            return null;
        }
        String display = colorSafe(name);
        if (display.isBlank()) {
            player.sendMessage(LangPack.ui(player, "editor_name_empty", "§cName cannot be empty."));
            return null;
        }
        String id = nextId(display);
        CustomNpc npc = new CustomNpc(id, display);
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        npc.setLocation(at);
        storage.save(npc);
        if (service.spawn(npc) == null) {
            player.sendMessage(LangPack.ui(player, "editor_spawn_fail",
                    "§cCould not spawn the FancyNPC. Check the console."));
            return npc;
        }
        player.sendMessage(LangPack.format(player, "msg.editor_created",
                "§aPlaced §f{0} §aat your feet.", npc.getName()));
        player.sendMessage(LangPack.ui(player, "editor_created_next",
                "§7Next: write what they say — or you're already done."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        openEdit(player, npc);
        return npc;
    }

    public boolean moveHere(Player player, CustomNpc npc) {
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        boolean ok = service.move(npc, at);
        if (ok) {
            player.sendMessage(LangPack.format(player, "msg.editor_moved",
                    "§aMoved §f{0} §ato your feet.", npc.getName()));
        } else {
            player.sendMessage(LangPack.ui(player, "editor_move_fail", "§cCould not move that NPC."));
        }
        return ok;
    }

    public boolean lookAt(Player player, CustomNpc npc) {
        Location at = npc.location();
        if (at == null) {
            return false;
        }
        Location look = at.clone();
        look.setDirection(player.getLocation().toVector().subtract(at.toVector()));
        look.setPitch(0f);
        boolean ok = service.move(npc, look);
        if (ok) {
            player.sendMessage(LangPack.format(player, "msg.editor_look",
                    "§a{0} §7now faces you.", npc.getName()));
        }
        return ok;
    }

    public CustomNpc duplicate(Player player, CustomNpc source) {
        if (source == null) {
            return null;
        }
        String id = nextId(source.getName());
        CustomNpc copy = source.copy(id, source.getName() + " Copy");
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        copy.setLocation(at);
        storage.save(copy);
        service.spawn(copy);
        player.sendMessage(LangPack.format(player, "msg.editor_duplicated",
                "§aCopied §f{0} §7beside you.", source.getName()));
        openEdit(player, copy);
        return copy;
    }

    public boolean delete(Player player, CustomNpc npc) {
        if (npc == null) {
            return false;
        }
        service.removeLive(npc.getId());
        storage.delete(npc.getId());
        if (player != null) {
            player.sendMessage(LangPack.format(player, "msg.editor_deleted",
                    "§eDeleted §f{0}§e.", npc.getName()));
        }
        return true;
    }

    public void persist(CustomNpc npc) {
        storage.save(npc);
        service.refresh(npc);
    }

    public void persistQuiet(CustomNpc npc) {
        storage.save(npc);
    }

    public CustomNpc resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        CustomNpc byId = storage.get(raw);
        if (byId != null) {
            return byId;
        }
        String needle = raw.toLowerCase(Locale.ROOT);
        for (CustomNpc npc : storage.all()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(raw)) {
                return npc;
            }
            if (npc.getName() != null && npc.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                return npc;
            }
        }
        return null;
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6NPC Editor Wand");
            meta.setLore(List.of(
                    "§7Right-click air — open the editor.",
                    "§7Right-click an editor NPC — edit them.",
                    "§7Sneak + right-click — ask to delete.",
                    "",
                    "§8Story NPCs (Egon, Twig, …) stay safe."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void giveWand(Player player) {
        player.getInventory().addItem(wand());
        player.sendMessage(LangPack.ui(player, "editor_wand_given",
                "§aWand given. §7Right-click air to open the editor."));
    }

    public void prompt(Player player, EditorSessions.Prompt prompt, String hint) {
        prompt(player, prompt, EditorScreen.EDIT, hint, null);
    }

    public void prompt(Player player, EditorSessions.Prompt prompt, EditorScreen returnTo, String hint, String preview) {
        EditorSessions.Session session = sessions.of(player);
        session.clearPrompt();
        session.setPrompt(prompt);
        session.setReturnTo(returnTo);
        session.setPromptHint(hint);
        session.setPromptPreview(preview);
        session.setPromptUntilMs(System.currentTimeMillis() + PROMPT_TIMEOUT_MS);
        player.closeInventory();
        player.sendMessage("§a" + hint);
        if (preview != null && !preview.isBlank()) {
            player.sendMessage(LangPack.format(player, "msg.editor_prompt_now",
                    "§7Currently: §f{0}", preview));
        }
        player.sendMessage(LangPack.ui(player, "editor_prompt_cancel",
                "§8Type §fcancel §8· §fabort §8· §fstop §8· or wait 60s."));
        String title = switch (prompt) {
            case NAME, RENAME -> "§aName";
            case SUBTITLE -> "§aSubtitle";
            case LINE, LINE_EDIT -> "§aDialogue line";
            case CHOICE_TEXT -> "§aReply button";
            case SKIN -> "§aSkin";
            case COMMAND -> "§aCommand";
            case PAGE_ID -> "§aPage name";
            case QUEST_ID, QUEST_TITLE -> "§aQuest";
            case REWARD_NAME -> "§aReward";
            case ITEM_SEARCH -> "§aSearch";
            default -> "§aNPC Editor";
        };
        player.sendTitle(title, "§7Type in chat · §fcancel §7to go back", 5, 70, 8);
        startPromptWatch(player, session);
    }

    public void cancelPrompt(Player player) {
        EditorSessions.Session session = sessions.peek(player);
        if (session != null) {
            session.clearPrompt();
        }
    }

    private void startPromptWatch(Player player, EditorSessions.Session session) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !session.prompting()) {
                session.clearPrompt();
                return;
            }
            if (System.currentTimeMillis() >= session.promptUntilMs()) {
                session.clearPrompt();
                player.sendMessage(LangPack.ui(player, "editor_prompt_timeout",
                        "§7Timed out. Type §fcancel §7was also an option — you're back."));
                reopen(player);
                return;
            }
            String hint = session.promptHint() == null ? "Type in chat" : session.promptHint();
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    hint + "  ·  cancel to go back"
            ));
        }, 10L, 40L);
        session.setPromptWatch(task);
    }

    public String nextId(String name) {
        String slug = name == null ? "npc" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (slug.isBlank()) {
            slug = "npc";
        }
        if (slug.length() > 16) {
            slug = slug.substring(0, 16);
        }
        String base = "mod_" + slug;
        if (freeId(base)) {
            return base;
        }
        for (int i = 2; i < 40; i++) {
            String candidate = base + "_" + i;
            if (freeId(candidate)) {
                return candidate;
            }
        }
        return base + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
    }

    private boolean freeId(String id) {
        if (storage.exists(id)) {
            return false;
        }
        if (QuestNPCRegistry.exists(id) || QuestNPCRegistry.exists(id.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return !LivingNpcProfile.isLiving(id);
    }

    public static String colorSafe(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }
        return trimmed.replace('§', '&');
    }

    public void clickEditorNpc(Player player, String fancyName) {
        String id = CustomNpcService.idFromFancyName(fancyName);
        CustomNpc npc = storage.get(id);
        if (npc == null) {
            return;
        }
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastClickTick.put(player.getUniqueId(), tick);
        if (last != null && tick - last < 10) {
            return;
        }
        if (allowed(player) && isWand(player.getInventory().getItemInMainHand())) {
            if (player.isSneaking()) {
                sessions.of(player).setNpcId(npc.getId());
                ConfirmMenu.open(player, npc);
                return;
            }
            openEdit(player, npc);
            return;
        }
        runtime.talk(player, npc);
    }

    public boolean fancyAvailable() {
        return FancyNpcFacade.isAvailable();
    }
}
