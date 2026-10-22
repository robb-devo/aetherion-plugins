package de.aetherion.helm.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who may do what. Owner and Co-Owner share everything except managing staff accounts, which stays with the owner
 * (one person who can lock everybody else out is enough). The lower roles are ready for future staff.
 */
public final class Roles {
    public static final List<String> PERMS = List.of(
            "view", "players.view", "players.inventory", "players.kick", "players.mute", "players.ban",
            "players.message", "players.send", "notes.write", "chat.send", "console.view", "console.command",
            "server.power", "host.view", "audit.view", "reports.manage", "settings.manage", "team.manage");

    public record Role(String id, String label, int rank, Set<String> perms) {}

    public static final Map<String, Role> ROLES = new LinkedHashMap<>();

    static {
        Set<String> all = Set.copyOf(PERMS);
        add("owner", "Owner", 100, all);
        add("coowner", "Co-Owner", 90, minus(all, "team.manage"));
        add("admin", "Admin", 70, minus(all, "team.manage", "settings.manage"));
        add("moderator", "Moderator", 50, Set.of("view", "players.view", "players.inventory", "players.kick",
                "players.mute", "players.ban", "players.message", "players.send", "notes.write", "chat.send",
                "console.view", "reports.manage", "audit.view"));
        add("supporter", "Supporter", 30, Set.of("view", "players.view", "players.message", "notes.write",
                "chat.send", "reports.manage"));
    }

    private Roles() {}

    private static void add(String id, String label, int rank, Set<String> perms) {
        ROLES.put(id, new Role(id, label, rank, perms));
    }

    private static Set<String> minus(Set<String> base, String... drop) {
        java.util.HashSet<String> s = new java.util.HashSet<>(base);
        for (String d : drop) s.remove(d);
        return Set.copyOf(s);
    }

    public static Role of(String id) {
        return ROLES.getOrDefault(id, ROLES.get("supporter"));
    }

    public static boolean has(String role, String perm) {
        return of(role).perms().contains(perm);
    }
}
