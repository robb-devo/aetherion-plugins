package de.aetherion.quests.editor;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.editor.gui.DialogueMenu;
import de.aetherion.quests.editor.gui.EditorItems;
import de.aetherion.quests.editor.gui.HomeMenu;
import de.aetherion.quests.editor.gui.Menu;
import de.aetherion.quests.editor.gui.OverviewMenu;
import de.aetherion.quests.editor.gui.QuestPickerMenu;
import de.aetherion.quests.editor.gui.ReplyMenu;
import de.aetherion.quests.editor.gui.TemplateMenu;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPCRegistry;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * NPC Studio facade — in-game FancyNPC + dialogue + quest-link editor for content helpers (moderators).
 * Permission: {@link #PERMISSION}. Never touches story NPCs in {@code npcs.yml}.
 * <p>
 * Every change goes through {@link #change}: snapshot for undo → apply → save → update the world → feedback.
 */
public final class NpcEditor {

    public static final String PERMISSION = "aetherion.npc.editor";
    private static final long INPUT_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final AetherionQuests plugin;
    private final CustomNpcStorage storage;
    private final CustomNpcService service;
    private final DialogueRuntime runtime;
    private final EditorSessions sessions;
    private final EditHistory history;
    private final QuestCatalog quests;
    private final NamespacedKey wandKey;
    private final Map<UUID, Integer> lastClickTick = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> inputBars = new ConcurrentHashMap<>();
    private BukkitTask inputTicker;

    public NpcEditor(AetherionQuests plugin) {
        this.plugin = plugin;
        this.storage = new CustomNpcStorage(plugin);
        this.service = new CustomNpcService(plugin, storage);
        this.runtime = new DialogueRuntime(plugin, storage);
        this.sessions = new EditorSessions();
        this.history = new EditHistory();
        this.quests = new QuestCatalog(plugin);
        this.wandKey = new NamespacedKey(plugin, "npc_editor_wand");
    }

    public void enable() {
        NpcEditorCommand command = new NpcEditorCommand(this);
        if (plugin.getCommand("npc") != null) {
            plugin.getCommand("npc").setExecutor(command);
            plugin.getCommand("npc").setTabCompleter(command);
        }
        new NpcEditorListener(this);
        Menu.register(plugin);
        CustomNpcInteractListener.register(this);
        inputTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickInputs, 20L, 20L);
    }

    public void restore() {
        service.restoreAll();
    }

    public void disable() {
        if (inputTicker != null) {
            inputTicker.cancel();
        }
        for (UUID id : List.copyOf(inputBars.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                hideBar(player);
            }
        }
        service.shutdown();
    }

    /** The live studio, for static screen helpers. */
    public static NpcEditor get() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getNpcEditor();
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

    public EditHistory history() {
        return history;
    }

    public QuestCatalog quests() {
        return quests;
    }

    public static boolean allowed(Player player) {
        return player != null && player.hasPermission(PERMISSION);
    }

    public CustomNpc npc(String id) {
        return storage.get(id);
    }

    // ------------------------------------------------------------------ navigation

    public void openMain(Player player) {
        HomeMenu.open(player);
    }

    public void openEdit(Player player, CustomNpc npc) {
        if (npc == null) {
            error(player, "That NPC doesn't exist anymore.");
            return;
        }
        OverviewMenu.open(player, npc.getId());
    }

    /** Reopens where the player left off (chat "Back to editor" links). */
    public void back(Player player) {
        Runnable returnTo = sessions.of(player).returnTo();
        sessions.of(player).setReturnTo(null);
        if (returnTo != null) {
            returnTo.run();
        } else {
            HomeMenu.open(player);
        }
    }

    // ------------------------------------------------------------------ create

    public void beginCreate(Player player) {
        TemplateMenu.open(player);
    }

    public void beginCreate(Player player, NpcTemplates.Template template) {
        if (!service.available()) {
            error(player, "FancyNpcs isn't loaded — ask an admin to install it, then restart.");
            return;
        }
        CustomNpc[] created = new CustomNpc[1];
        ask(player, TextInput.builder("Name your new " + template.title().toLowerCase(Locale.ROOT))
                .hint("Shown above its head and in front of every line it says.")
                .hint("It will appear right where you're standing.")
                .max(32)
                .handler((p, text) -> {
                    String name = colorSafe(text);
                    if (name.isBlank()) {
                        return "The name can't be empty.";
                    }
                    created[0] = create(p, name, template);
                    return created[0] == null ? "Couldn't create the NPC — details are in the console." : null;
                })
                .then(p -> afterCreate(p, created[0], template))
                .onCancel(TemplateMenu::open)
                .build());
    }

    /** {@code /npc create <name>} fast path — blank template, opens the overview. */
    public CustomNpc createAt(Player player, String name) {
        CustomNpc npc = create(player, name, NpcTemplates.Template.BLANK);
        if (npc != null) {
            OverviewMenu.open(player, npc.getId());
        }
        return npc;
    }

    public CustomNpc create(Player player, String name, NpcTemplates.Template template) {
        if (!service.available()) {
            error(player, "FancyNpcs isn't loaded — ask an admin to install it, then restart.");
            return null;
        }
        String display = colorSafe(name);
        if (display.isBlank()) {
            error(player, "The name can't be empty.");
            return null;
        }
        CustomNpc npc = new CustomNpc(nextId(display), display);
        NpcTemplates.apply(npc, template == null ? NpcTemplates.Template.BLANK : template);
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        npc.setLocation(at);
        npc.touch(player.getName());
        storage.save(npc);
        if (service.refresh(npc) == null) {
            player.sendMessage("§e" + npc.getName() + " was saved, but the FancyNPC didn't spawn — check the console.");
        }
        player.sendMessage("§a✔ Created §f" + npc.getName() + " §7— standing where you are.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.5f);
        return npc;
    }

    private void afterCreate(Player player, CustomNpc npc, NpcTemplates.Template template) {
        if (npc == null) {
            return;
        }
        switch (template) {
            case QUEST_GIVER -> {
                player.sendMessage("§7Next: pick the quest §f" + npc.getName() + " §7hands out.");
                QuestPickerMenu.forMainQuest(player, npc.getId(), true);
            }
            case SERVICE -> {
                player.sendMessage("§7Next: choose the command for §f\"Yes, please!\"§7.");
                ReplyMenu.open(player, npc.getId(), CustomNpc.START_PAGE, 0);
            }
            case GUIDE -> DialogueMenu.open(player, npc.getId());
            default -> OverviewMenu.open(player, npc.getId());
        }
    }

    // ------------------------------------------------------------------ changes (undoable)

    /**
     * The one way screens change an NPC: undo snapshot → mutation → save → refresh what the world shows.
     *
     * @param label short past-tense-free description for undo ("Rename to Bob", "Delete line 2")
     */
    public void change(Player player, CustomNpc npc, String label, Consumer<CustomNpc> mutation) {
        if (npc == null) {
            return;
        }
        CustomNpc before = npc.snapshot();
        mutation.accept(npc);
        npc.touch(player.getName());
        storage.save(npc);
        history.record(player, before, label, false);
        applyToWorld(before, npc);
        saved(player, label);
    }

    /** Keeps the live FancyNPC in sync with a changed NPC — only redoing what actually changed. */
    private void applyToWorld(CustomNpc before, CustomNpc after) {
        if (before == null) {
            service.refresh(after);
            return;
        }
        boolean gear = before.getPreset() != after.getPreset()
                || before.isSlim() != after.isSlim()
                || !before.getSkinUsername().equalsIgnoreCase(after.getSkinUsername());
        boolean moved = before.location() == null
                || after.location() == null
                || !before.location().equals(after.location());
        boolean label = !String.valueOf(before.getName()).equals(after.getName())
                || !before.getSubtitle().equals(after.getSubtitle());
        if (gear || (moved && before.location() == null)) {
            service.refresh(after);
        } else if (moved) {
            service.syncPosition(after);
        } else if (label) {
            service.refreshLabel(after);
        }
    }

    public boolean moveHere(Player player, CustomNpc npc) {
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        return place(player, npc, at, "Move " + npc.getName() + " to you");
    }

    /** Puts the NPC on the block the player looks at, facing the player. */
    public boolean moveToTarget(Player player, CustomNpc npc) {
        Block block = player.getTargetBlockExact(24);
        if (block == null || !block.getType().isSolid()) {
            error(player, "Look at a solid block within 24 blocks, then try again.");
            return false;
        }
        Location at = block.getLocation().add(0.5, 1.0, 0.5);
        Vector toPlayer = player.getLocation().toVector().subtract(at.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            at.setDirection(toPlayer);
        }
        at.setPitch(0f);
        return place(player, npc, at, "Move " + npc.getName() + " to a block");
    }

    public boolean lookAt(Player player, CustomNpc npc) {
        Location at = npc.location();
        if (at == null) {
            error(player, "Place the NPC first (Move here).");
            return false;
        }
        Location look = at.clone();
        Vector toPlayer = player.getLocation().toVector().subtract(at.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            look.setDirection(toPlayer);
        }
        look.setPitch(0f);
        return place(player, npc, look, "Turn " + npc.getName() + " to face you");
    }

    private boolean place(Player player, CustomNpc npc, Location at, String label) {
        if (npc == null || at == null || at.getWorld() == null) {
            return false;
        }
        CustomNpc before = npc.snapshot();
        npc.setLocation(at);
        npc.touch(player.getName());
        storage.save(npc);
        history.record(player, before, label, false);
        if (before.location() == null) {
            service.refresh(npc);
        } else {
            service.syncPosition(npc);
        }
        saved(player, label);
        return true;
    }

    /** Teleports the editor two blocks in front of the NPC, facing it. */
    public void teleportTo(Player player, CustomNpc npc) {
        Location at = npc == null ? null : npc.location();
        if (at == null) {
            error(player, "That NPC isn't placed in a loaded world.");
            return;
        }
        Vector forward = at.getDirection().setY(0);
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        Location target = at.clone().add(forward.normalize().multiply(2.0));
        if (!target.getBlock().isPassable() || !target.clone().add(0, 1, 0).getBlock().isPassable()) {
            target = at.clone();
        }
        Vector look = at.toVector().subtract(target.toVector());
        if (look.lengthSquared() > 0.01) {
            target.setDirection(look);
        }
        target.setPitch(0f);
        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.3f);
    }

    public CustomNpc duplicate(Player player, CustomNpc source) {
        if (source == null) {
            return null;
        }
        String name = EditorItems.truncate(source.getName() + " Copy", 32);
        CustomNpc copy = source.copy(nextId(name), name);
        Location at = player.getLocation().clone();
        at.setPitch(0f);
        copy.setLocation(at);
        copy.setCreatedBy(null);
        copy.touch(player.getName());
        storage.save(copy);
        service.refresh(copy);
        player.sendMessage("§a✔ Duplicated §f" + source.getName() + " §7→ §f" + copy.getName() + " §7(standing where you are).");
        return copy;
    }

    public boolean delete(Player player, CustomNpc npc) {
        if (npc == null) {
            return false;
        }
        if (player != null) {
            history.record(player, npc.snapshot(), "Delete " + npc.getName(), true);
        }
        service.removeLive(npc.getId());
        storage.delete(npc.getId());
        if (player != null) {
            player.sendMessage(Component.empty()
                    .append(legacy("§eDeleted §f" + npc.getName() + "§e. "))
                    .append(chip("↶ Undo", NamedTextColor.GOLD, ClickEvent.runCommand("/npc undo"),
                            "Bring " + npc.getName() + " back")));
        }
        return true;
    }

    /**
     * Reverts this player's latest change — on one NPC ({@code npcId}) or anywhere (null).
     *
     * @return the restored NPC, or null when there was nothing to undo
     */
    public CustomNpc undo(Player player, String npcId) {
        EditHistory.Entry entry = npcId == null ? history.pop(player) : history.pop(player, npcId);
        if (entry == null) {
            player.sendMessage("§7Nothing to undo.");
            return null;
        }
        CustomNpc current = storage.get(entry.npcId());
        CustomNpc restored = entry.before().snapshot();
        storage.save(restored);
        if (current == null) {
            service.refresh(restored);
        } else {
            applyToWorld(current, restored);
        }
        player.sendActionBar(legacy("§e↶ Undone §8· §7" + entry.label()));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.9f);
        return restored;
    }

    // ------------------------------------------------------------------ chat input

    /** Asks for text in chat: closes the menu, shows a framed prompt + a boss bar until answered. */
    public void ask(Player player, TextInput input) {
        EditorSessions.Session session = sessions.of(player);
        TextInput previous = session.input();
        session.setInput(input);
        if (previous == null) {
            player.closeInventory();
        }
        showPrompt(player, input);
        showBar(player, input);
    }

    /** Handles one chat message for a pending input (main thread). */
    public void handleInput(Player player, String raw) {
        EditorSessions.Session session = sessions.peek(player);
        TextInput input = session == null ? null : session.input();
        if (input == null) {
            return;
        }
        String text = raw == null ? "" : raw.trim().replace('§', '&');
        if (TextInput.isCancel(text)) {
            cancelInput(player, true);
            return;
        }
        if (input.multiLine() && TextInput.isDone(text)) {
            finishInput(player);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > input.maxLength()) {
            input.touch();
            error(player, "That's " + text.length() + " characters — keep it under " + input.maxLength() + ".");
            return;
        }
        session.clearInput();
        String problem;
        try {
            problem = input.handler().accept(player, text);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "NPC Studio input failed", ex);
            problem = "Something went wrong — details are in the server console.";
        }
        if (problem != null) {
            if (session.input() == null) {
                session.setInput(input);
            }
            input.touch();
            error(player, problem + " §7Try again, or type §fcancel§7.");
            return;
        }
        input.markAccepted();
        if (input.multiLine()) {
            if (session.input() == null) {
                session.setInput(input);
            }
            showBar(player, input);
            player.sendMessage(Component.empty()
                    .append(legacy("§a+ Line " + input.accepted() + " added. §7Keep typing, or "))
                    .append(chip("✔ Done", NamedTextColor.GREEN, ClickEvent.runCommand("/npc done"), "Finish adding lines")));
            return;
        }
        if (session.input() == null) {
            hideBar(player);
        }
        input.done(player);
    }

    public void cancelInput(Player player, boolean reopen) {
        EditorSessions.Session session = sessions.peek(player);
        TextInput input = session == null ? null : session.input();
        if (input == null) {
            return;
        }
        session.clearInput();
        hideBar(player);
        if (input.multiLine() && input.accepted() > 0) {
            player.sendMessage("§7Stopped — " + EditorItems.plural(input.accepted(), "line") + " kept.");
        } else {
            player.sendMessage("§7Cancelled.");
        }
        if (reopen) {
            input.cancel(player);
        }
    }

    public void finishInput(Player player) {
        EditorSessions.Session session = sessions.peek(player);
        TextInput input = session == null ? null : session.input();
        if (input == null) {
            return;
        }
        session.clearInput();
        hideBar(player);
        if (input.multiLine()) {
            player.sendMessage(input.accepted() == 0
                    ? "§7No lines added."
                    : "§a✔ " + EditorItems.plural(input.accepted(), "line") + " added.");
        }
        input.done(player);
    }

    public boolean prompting(Player player) {
        EditorSessions.Session session = sessions.peek(player);
        return session != null && session.prompting();
    }

    private void tickInputs() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, EditorSessions.Session> entry : sessions.all().entrySet()) {
            TextInput input = entry.getValue().input();
            if (input == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                entry.getValue().clearInput();
                continue;
            }
            long idle = now - input.lastActivity();
            if (idle > INPUT_TIMEOUT_MS) {
                entry.getValue().clearInput();
                hideBar(player);
                player.sendMessage("§7Your NPC Studio input timed out. §8(Open it again with /npc)");
                continue;
            }
            BossBar bar = inputBars.get(player.getUniqueId());
            if (bar != null) {
                bar.progress(Math.max(0f, Math.min(1f, 1f - idle / (float) INPUT_TIMEOUT_MS)));
            }
        }
    }

    private void showPrompt(Player player, TextInput input) {
        player.sendMessage(Component.empty());
        player.sendMessage(legacy("§e§l✎ " + input.title()));
        for (String hint : input.hints()) {
            player.sendMessage(legacy("§7" + hint));
        }
        if (input.current() != null) {
            player.sendMessage(legacy("§7Now: §f" + EditorItems.truncate(input.current(), 90)));
        }
        Component row = Component.empty();
        if (input.current() != null) {
            row = row.append(chip("✎ Edit current text", NamedTextColor.YELLOW, ClickEvent.suggestCommand(input.current()),
                    "Puts the current text into your chat box so you can tweak it")).append(Component.text("  "));
        }
        if (input.multiLine()) {
            row = row.append(chip("✔ Done", NamedTextColor.GREEN, ClickEvent.runCommand("/npc done"), "Finish adding lines"))
                    .append(Component.text("  "));
        }
        row = row.append(chip("✖ Cancel", NamedTextColor.RED, ClickEvent.runCommand("/npc cancel"), "Go back without changes"));
        player.sendMessage(row);
        player.showTitle(Title.title(Component.empty(), legacy("§e✎ Type in chat"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f);
    }

    private void showBar(Player player, TextInput input) {
        Component text = input.multiLine()
                ? legacy("§e✎ " + input.title() + " §8— §f" + input.accepted() + " §7added · type §fdone §7when finished")
                : legacy("§e✎ " + input.title() + " §8— §7type in chat · §fcancel §7to stop");
        BossBar bar = inputBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(text, 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            inputBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(text);
            bar.progress(1f);
        }
    }

    private void hideBar(Player player) {
        BossBar bar = inputBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    void forget(Player player) {
        EditorSessions.Session session = sessions.peek(player);
        if (session != null) {
            session.clearInput();
        }
        inputBars.remove(player.getUniqueId());
        sessions.forget(player.getUniqueId());
        runtime.forget(player.getUniqueId());
        lastClickTick.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ preview

    /** Plays the conversation to the editor without running commands or quests. */
    public void preview(Player player, CustomNpc npc, String pageId, QuestState stage, Runnable returnTo) {
        if (npc == null) {
            return;
        }
        sessions.of(player).setReturnTo(returnTo);
        player.closeInventory();
        runtime.preview(player, npc, pageId, stage, p -> p.sendMessage(Component.empty()
                .append(legacy("§8[Preview] §7End of conversation. "))
                .append(chip("↩ Back to the editor", NamedTextColor.AQUA, ClickEvent.runCommand("/npc back"),
                        "Reopen the screen you came from"))));
    }

    // ------------------------------------------------------------------ feedback

    public void saved(Player player, String label) {
        player.sendActionBar(legacy("§a✔ Saved §8· §7" + label));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.7f);
    }

    public void error(Player player, String text) {
        if (player == null) {
            return;
        }
        player.sendMessage("§c✖ " + text);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static Component chip(String label, NamedTextColor color, ClickEvent click, String hover) {
        return Component.text("[" + label + "]", color)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    // ------------------------------------------------------------------ lookup & ids

    public CustomNpc resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        CustomNpc byId = storage.get(raw);
        if (byId != null) {
            return byId;
        }
        String needle = raw.toLowerCase(Locale.ROOT);
        CustomNpc partial = null;
        for (CustomNpc npc : storage.all()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(raw)) {
                return npc;
            }
            if (partial == null && npc.getName() != null && npc.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                partial = npc;
            }
        }
        return partial;
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

    // ------------------------------------------------------------------ wand & world clicks

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lNPC Studio Wand");
            meta.setLore(List.of(
                    "§7Right-click §fair §8→ §7open NPC Studio",
                    "§7Right-click §fan NPC §8→ §7edit it",
                    "§7Sneak + right-click §fan NPC §8→ §7talk to it",
                    "§8  (for real — quests & commands run)",
                    "",
                    "§8Story NPCs (Egon, Twig, …) are protected."
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
        player.sendMessage("§a✔ NPC Studio Wand §7given. Right-click air to open the studio, right-click an NPC to edit it.");
    }

    public void clickEditorNpc(Player player, String fancyName) {
        String id = CustomNpcService.idFromFancyName(fancyName);
        CustomNpc npc = storage.get(id);
        if (npc == null) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        Integer last = lastClickTick.put(player.getUniqueId(), tick);
        if (last != null && tick - last < 10) {
            return;
        }
        if (allowed(player) && isWand(player.getInventory().getItemInMainHand())) {
            if (player.isSneaking()) {
                runtime.talk(player, npc);
                return;
            }
            OverviewMenu.open(player, npc.getId());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.15f);
            return;
        }
        runtime.talk(player, npc);
    }

    public boolean fancyAvailable() {
        return FancyNpcFacade.isAvailable();
    }
}
