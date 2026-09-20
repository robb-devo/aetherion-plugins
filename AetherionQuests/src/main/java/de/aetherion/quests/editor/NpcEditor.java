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
import de.aetherion.quests.editor.gui.QuestEditMenu;
import de.aetherion.quests.editor.gui.QuestLinkMenu;
import de.aetherion.quests.editor.gui.RequirementsMenu;
import de.aetherion.quests.editor.gui.RewardsMenu;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-game FancyNPC creator for content helpers (moderators).
 * Permission: {@link #PERMISSION}. Does not touch story NPCs in {@code npcs.yml}.
 */
public final class NpcEditor {

    public static final String PERMISSION = "aetherion.npc.editor";

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
        // Primary is aethernpc — /npc belongs to FancyNpcs on live.
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
        new QuestLinkMenu(this);
        new QuestEditMenu(this);
        new RewardsMenu(this);
        new RequirementsMenu(this);
        new ObjectiveMenu(this);
        new GatherItemMenu(this);
        new ConfirmMenu(this);
        new HelpMenu(this);
        CustomNpcInteractListener.register(this);
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

    public void loadEditorQuests() {
        editorQuests.reload();
        editorQuests.registerAll(plugin.getQuestManager());
    }

    public de.aetherion.quests.model.Quest createAndLinkQuest(Player player, CustomNpc npc, String title) {
        if (npc == null) {
            return null;
        }
        de.aetherion.quests.model.Quest quest = editorQuests.createAndSave(title, npc.getId(), plugin.getQuestManager());
        npc.setLinkedQuestId(quest.getId());
        npc.setMode(NpcMode.QUEST);
        persistQuiet(npc);
        if (player != null) {
            sessions.of(player).setDirty(false);
            player.sendMessage("§aCreated and linked §f" + quest.getTitle() + " §7(§f" + quest.getId() + "§7).");
            player.sendMessage("§7Talk stub. Edit rewards / gather next.");
        }
        return quest;
    }

    public static boolean allowed(Player player) {
        return player != null && (player.hasPermission(PERMISSION)
                || player.isOp()
                || player.hasPermission("aetherion.dev"));
    }

    public void openMain(Player player) {
        MainMenu.open(player);
    }

    public void openEdit(Player player, CustomNpc npc) {
        if (npc == null) {
            player.sendMessage("§cNPC not found.");
            return;
        }
        sessions.of(player).setNpcId(npc.getId());
        EditMenu.open(player, npc);
    }

    public void beginCreate(Player player) {
        EditorSessions.Session session = sessions.of(player);
        session.setPrompt(EditorSessions.Prompt.NAME);
        session.setNpcId(null);
        player.closeInventory();
        player.sendMessage("§aType a name for the new NPC §7(or §fcancel§7).");
        player.sendTitle("§aNPC name", "§7Type in chat", 5, 80, 10);
    }

    public CustomNpc createAt(Player player, String name) {
        if (!service.available()) {
            player.sendMessage("§cFancyNpcs is not loaded. Ask an admin to install it, then restart.");
            return null;
        }
        String display = colorSafe(name);
        if (display.isBlank()) {
            player.sendMessage("§cName cannot be empty.");
            return null;
        }
        String id = nextId(display);
        CustomNpc npc = new CustomNpc(id, display);
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        npc.setLocation(at);
        storage.save(npc);
        if (service.spawn(npc) == null) {
            player.sendMessage("§cCould not spawn the FancyNPC. Check the console.");
            return npc;
        }
        player.sendMessage("§aCreated §f" + npc.getName() + " §7(§f" + npc.getId() + "§7) at your feet.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        openEdit(player, npc);
        return npc;
    }

    public boolean moveHere(Player player, CustomNpc npc) {
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        boolean ok = service.move(npc, at);
        if (ok) {
            player.sendMessage("§aMoved §f" + npc.getName() + " §ato your feet.");
        } else {
            player.sendMessage("§cCould not move that NPC.");
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
            player.sendMessage("§a" + npc.getName() + " §7now faces you.");
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
        player.sendMessage("§aDuplicated §f" + source.getName() + " §7→ §f" + copy.getId());
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
            player.sendMessage("§eDeleted §f" + npc.getName() + "§e.");
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
                    "§7Right-click §fair §7— open the menu.",
                    "§7Right-click §fan editor NPC §7— edit.",
                    "§7Sneak + right-click §7— delete confirm.",
                    "",
                    "§8Does not edit story NPCs (Egon, Twig, …)."
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
        player.sendMessage("§aNPC Editor Wand §7given. Right-click air for the menu.");
    }

    public void prompt(Player player, EditorSessions.Prompt prompt, String hint) {
        EditorSessions.Session session = sessions.of(player);
        session.setPrompt(prompt);
        session.setDirty(true);
        player.closeInventory();
        player.sendMessage("§a" + hint + " §7(or type §fcancel§7).");
        player.sendTitle("§aNPC Editor", "§7Type in chat", 5, 60, 8);
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
