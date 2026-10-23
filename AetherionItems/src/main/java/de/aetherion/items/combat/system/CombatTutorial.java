package de.aetherion.items.combat.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * First-fight tip: two chat lines with the bindings, once per player (PDC flag), re-readable with
 * {@code /combat}. Bump {@link #VERSION} when the bindings change so everyone sees the new map once.
 */
final class CombatTutorial {

    private static final byte VERSION = 2;
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
        system.feedback().plain(player, "F = parry", "F = parieren", 60);
    }

    void show(Player player) {
        boolean de = CombatSystem.german(player);
        player.sendMessage(Component.text()
                .append(Component.text(de ? "⚔ Kampf " : "⚔ Combat ", ACCENT))
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(key("F")).append(text(de ? " parieren · " : " parry · "))
                .append(key(de ? "Sprint + Schleichen" : "Sprint + Sneak")).append(text(de ? " Dash · " : " dash · "))
                .append(key(de ? "Schleichen + Springen" : "Sneak + Jump")).append(text(de ? " Rolle" : " roll"))
                .build());
        player.sendMessage(Component.text(de
                ? "Voll aufgeladene Schläge treffen hart, Spam trifft schwach. Drei getimte Treffer = Finisher, "
                + "nach einer Parade = Konter. Ausdauer steht über deiner HP. Schleichen + F tauscht die Hände. /combat"
                : "Full-charge swings hit hard, spam hits weak. Three timed hits = finisher, a hit right after a parry "
                + "= riposte. Stamina sits above your HP. Sneak + F swaps hands. /combat", NamedTextColor.GRAY));
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

    private static Component key(String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    private static Component text(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
