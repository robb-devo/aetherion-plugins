package de.aetherion.items.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class LuckPermsSilent {

    private LuckPermsSilent() {
    }

    /**
     * Homie / Monkey content kit — never {@code aetherion.dev} (full admin).
     * Tools only: Content Kit UI, NPC editor, self shards, flight.
     */
    static final String[] CONTENT_PERMISSIONS = {
            "aetherion.npc.editor",
            "aetherion.dev.content",
            "aetherion.devmenu",
            "aetherion.shards.admin",
            "aetherion.flight",
            "essentials.fly",
            "essentials.fly.safelogin"
    };

    private static final String FULL_DEV = "aetherion.dev";

    static {
        for (String node : CONTENT_PERMISSIONS) {
            if (FULL_DEV.equalsIgnoreCase(node)) {
                throw new IllegalStateException("CONTENT_PERMISSIONS must not include aetherion.dev");
            }
        }
    }

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    static void syncGroups(Iterable<RankBadgeService.Rank> ranks, String mvpGroup) {
        if (!available()) {
            return;
        }
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return;
        }
        for (RankBadgeService.Rank rank : ranks) {
            api.getGroupManager().createAndLoadGroup(rank.group()).thenAccept(group -> {
                if (group == null) {
                    return;
                }
                writeGroupMeta(group, rank);
                api.getGroupManager().saveGroup(group);
            });
        }
        String mvp = mvpGroup == null ? "mvpplusplus" : mvpGroup.toLowerCase(Locale.ROOT);
        api.getGroupManager().loadGroup(mvp).thenAccept(optional -> optional.ifPresent(group -> {
            group.data().add(PermissionNode.builder("aetherion.rank.mvpplusplus").value(true).build());
            api.getGroupManager().saveGroup(group);
        }));
        api.getGroupManager().loadGroup("admin").thenAccept(optional -> optional.ifPresent(group -> {
            group.data().add(PermissionNode.builder("aetherion.rank.admin").value(true).build());
            group.data().add(PermissionNode.builder("aetherion.npc.editor").value(true).build());
            api.getGroupManager().saveGroup(group);
        }));
        api.getGroupManager().createAndLoadGroup("monkey").thenAccept(group -> {
            if (group == null) {
                return;
            }
            group.data().add(PermissionNode.builder("aetherion.rank.monkey").value(true).build());
            grantContentKit(group);
            denyFullDev(group);
            api.getGroupManager().saveGroup(group);
        });
        // Cosmetic-only ultra. Rainbow letters (not Monkey celestial). No tools.
        api.getGroupManager().createAndLoadGroup("beta").thenAccept(group -> {
            if (group == null) {
                return;
            }
            group.data().add(PermissionNode.builder("aetherion.rank.beta").value(true).build());
            // Cosmetic only: strip accidental grants, but do not negate aetherion.dev
            // (an admin who also has Beta must keep the full DEV tree).
            stripFullDevGrant(group);
            stripContentKitGrants(group);
            api.getGroupManager().saveGroup(group);
        });
        for (String staff : List.of("moderator", "mod")) {
            api.getGroupManager().loadGroup(staff).thenAccept(optional -> optional.ifPresent(group -> {
                grantContentKit(group);
                denyFullDev(group);
                api.getGroupManager().saveGroup(group);
            }));
        }
    }

    /** Homie / Monkey content kit — not full admin. */
    private static void grantContentKit(Group group) {
        if (group == null) {
            return;
        }
        for (String node : CONTENT_PERMISSIONS) {
            group.data().add(PermissionNode.builder(node).value(true).build());
        }
        denyFullDev(group);
    }

    /**
     * Strip leftover manual {@code aetherion.dev} and plant an explicit deny
     * so Monkey / content kit cannot inherit the full Dev Menu.
     */
    private static void denyFullDev(Group group) {
        if (group == null) {
            return;
        }
        stripFullDevGrant(group);
        group.data().add(PermissionNode.builder(FULL_DEV).value(false).build());
    }

    private static void stripFullDevGrant(Group group) {
        if (group == null) {
            return;
        }
        group.data().clear(NodeType.PERMISSION.predicate(node ->
                FULL_DEV.equalsIgnoreCase(node.getKey()) && node.getValue()));
    }

    /** Beta is celestial cosmetics only — never pick up content-kit nodes. */
    private static void stripContentKitGrants(Group group) {
        if (group == null) {
            return;
        }
        for (String node : CONTENT_PERMISSIONS) {
            group.data().clear(NodeType.PERMISSION.predicate(existing ->
                    node.equalsIgnoreCase(existing.getKey()) && existing.getValue()));
        }
    }

    static void removeGroup(UUID playerId, String group) {
        if (playerId == null || group == null || group.isBlank() || !available()) {
            return;
        }
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return;
        }
        String key = group.toLowerCase(Locale.ROOT);
        api.getUserManager().modifyUser(playerId, user ->
                user.data().clear(NodeType.INHERITANCE.predicate(node ->
                        key.equals(node.getGroupName().toLowerCase(Locale.ROOT)))));
    }

    static void applyUser(UUID playerId, String keepGroup, String extraGroup, Set<String> managedGroups) {
        if (playerId == null || keepGroup == null || !available()) {
            return;
        }
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return;
        }
        Set<String> managed = managedGroups.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String keep = keepGroup.toLowerCase(Locale.ROOT);
        String extra = extraGroup == null ? null : extraGroup.toLowerCase(Locale.ROOT);
        api.getUserManager().modifyUser(playerId, user -> paintUser(user, keep, extra, managed));
    }

    private static void writeGroupMeta(Group group, RankBadgeService.Rank rank) {
        group.data().clear(NodeType.WEIGHT.predicate(node -> true));
        group.data().clear(NodeType.PREFIX.predicate(node -> true));
        group.data().add(WeightNode.builder(rank.weight()).build());
        group.data().add(PrefixNode.builder(rank.prefix(), rank.weight()).build());
    }

    private static void paintUser(User user, String keep, String extra, Set<String> managed) {
        // Wipe XP progression groups only. Ultra extras stay until Peter removes them.
        user.data().clear(NodeType.INHERITANCE.predicate(node -> {
            String name = node.getGroupName().toLowerCase(Locale.ROOT);
            return managed.contains(name) && !RankBadgeService.isPermanentExtra(name);
        }));
        user.data().add(InheritanceNode.builder(keep).build());
        if (extra != null && !extra.equals(keep)) {
            user.data().clear(NodeType.INHERITANCE.predicate(node -> {
                String name = node.getGroupName().toLowerCase(Locale.ROOT);
                return RankBadgeService.isPermanentExtra(name) && !name.equals(extra);
            }));
            user.data().add(InheritanceNode.builder(extra).build());
        }
    }
}
