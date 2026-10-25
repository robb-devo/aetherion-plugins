package de.aetherion.items.combat.system;

import de.aetherion.core.api.ActionBarLane;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * First-fight tip: two chat lines with the bindings, once per player (PDC flag), re-readable with
 * {@code /combat}. Bump {@link #VERSION} when the bindings change so everyone sees the new map once.
 * The keys are keybind components: every client shows its own binding (a player with his own layout looked
 * for "F" for ages).
 */
final class CombatTutorial {

    private static final byte VERSION = 4;
    private static final TextColor ACCENT = TextColor.color(0xFFC94A);

    private final CombatSystem system;
    private final NamespacedKey seenKey;

    CombatTutorial(CombatSystem system) {
        this.system = system;
        this.seenKey = new NamespacedKey(system.plugin(), "combat_tip");
    }

    void maybeShow(Player player) {
        if (!system.config().tutorial || !system.config().enabled) {
            return;
        }
        Byte seen = player.getPersistentDataContainer().get(seenKey, PersistentDataType.BYTE);
        if (seen != null && seen >= VERSION) {
            return;
        }
        player.getPersistentDataContainer().set(seenKey, PersistentDataType.BYTE, VERSION);
        show(player);
        ActionBarLane.show(player, Component.text()
                .append(Component.keybind("key.swapOffhand", NamedTextColor.WHITE))
                .append(text(CombatSystem.german(player) ? " = parieren" : " = parry"))
                .build(), 60);
    }

    void show(Player player) {
        boolean de = CombatSystem.german(player);
        player.sendMessage(Component.text()
                .append(Component.text(de ? "⚔ Kampf " : "⚔ Combat ", ACCENT))
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(bound("key.swapOffhand")).append(text(de ? " parieren · " : " parry · "))
                .append(bound("key.sprint")).append(text(" + ")).append(bound("key.sneak"))
                .append(text(de ? " Dash · " : " dash · "))
                .append(bound("key.sneak")).append(text(" + ")).append(bound("key.jump"))
                .append(text(de ? " Rolle" : " roll"))
                .build());
        player.sendMessage(Component.text(de
                ? "Voll aufgeladene Schläge treffen hart, Spam trifft schwach. Drei getimte Treffer = Finisher, "
                + "nach einer Parade = Konter. Ausdauer steht über deiner HP. Schleichen + Parieren-Taste tauscht die Hände. /combat"
                : "Full-charge swings hit hard, spam hits weak. Three timed hits = finisher, a hit right after a parry "
                + "= riposte. Stamina sits above your HP. Sneak + parry key swaps hands. /combat", NamedTextColor.GRAY));
        player.sendMessage(Component.text()
                .append(Component.text("⚡ Tempo ", ACCENT))
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(text(de
                        ? "Jede Waffe hat ihr eigenes Tempo (steht als ⚡ Tempo auf dem Item): Dolche schlagen schnell, "
                        + "Großäxte und Hämmer langsam, aber hart. Warte, bis die Anzeige am Fadenkreuz voll ist. Halb "
                        + "aufgeladene Schläge prallen an Bossen ab, und nach ihren großen Angriffen sind sie offen: "
                        + "dann treffen getimte Schläge extra hart."
                        : "Every weapon has its own tempo (shown as ⚡ Tempo on the item): daggers swing fast, greataxes "
                        + "and hammers slow but hard. Wait until the crosshair indicator is full. Half-charged swings "
                        + "glance off bosses, and after their big attacks bosses are open: timed hits land extra hard."))
                .build());
    }

    /** The player's own binding for {@code key} (the client fills it in). */
    private static Component bound(String key) {
        return Component.keybind(key, NamedTextColor.WHITE);
    }

    private static Component text(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
