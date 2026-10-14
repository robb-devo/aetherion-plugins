package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.HomeMenu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Set;

/**
 * Wand use + chat answers for the NPC Studio.
 */
public final class NpcEditorListener implements Listener {

    private static final Set<String> STUDIO_COMMANDS = Set.of("npc", "aethernpc", "npceditor");

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
        HomeMenu.open(player);
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
        String message = event.getMessage() == null ? "" : event.getMessage();
        editor.plugin().getServer().getScheduler().runTask(editor.plugin(), () -> {
            if (player.isOnline()) {
                editor.handleInput(player, message);
            }
        });
    }

    /** Command answers: a typed "/spawn" becomes the answer instead of running (studio commands still run). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        EditorSessions.Session session = editor.sessions().peek(player);
        TextInput input = session == null ? null : session.input();
        if (input == null || !input.acceptsCommand()) {
            return;
        }
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        String root = message.startsWith("/") ? message.substring(1) : message;
        root = root.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) {
            root = root.substring(root.indexOf(':') + 1);
        }
        if (STUDIO_COMMANDS.contains(root)) {
            return;
        }
        event.setCancelled(true);
        editor.handleInput(player, message.startsWith("/") ? message.substring(1) : message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.forget(event.getPlayer());
    }
}
