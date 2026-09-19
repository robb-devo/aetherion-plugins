package de.aetherion.pit.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.command.CommandSender;

/**
 * Hub chat/nametags: prefer {@code &}-codes + ASCII. Avoid {@code §} and fancy unicode
 * in YAML — Paper/UTF-8 double-encoding turns them into {@code Â§}/{@code Â»} on clients.
 */
public final class Msg {

    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.legacyAmpersand();

    private Msg() {
    }

    public static Component amp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        return AMP.deserialize(sanitize(raw));
    }

    public static void send(CommandSender to, String raw) {
        if (to == null) {
            return;
        }
        to.sendMessage(amp(raw));
    }

    /** Strip mojibake + section signs; keep {@code &} color codes. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        // Classic UTF-8 § read as Latin-1 then saved again
        s = s.replace("Â§", "&");
        s = s.replace("§", "&");
        // Broken arrows / dashes from previous unicode messages
        s = s.replace("Â»", "-");
        s = s.replace("Â¡", "");
        s = s.replace("â€”", "-");
        s = s.replace("â€“", "-");
        s = s.replace("â€¢", "-");
        s = s.replace("âš”", "");
        s = s.replace("Â", "");
        s = s.replace("»", "-");
        s = s.replace("—", "-");
        s = s.replace("–", "-");
        s = s.replace("·", "-");
        s = s.replace("→", "->");
        s = s.replace("⚔", "");
        s = s.replace("★", "*");
        s = s.replace("✦", "*");
        return s.trim().replaceAll(" +", " ");
    }
}
