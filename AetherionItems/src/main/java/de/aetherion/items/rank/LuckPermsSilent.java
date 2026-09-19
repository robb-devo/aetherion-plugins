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

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class LuckPermsSilent {

    private LuckPermsSilent() {
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
            api.getGroupManager().saveGroup(group);
        }));
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
        user.data().clear(NodeType.INHERITANCE.predicate(node -> managed.contains(node.getGroupName().toLowerCase(Locale.ROOT))));
        user.data().add(InheritanceNode.builder(keep).build());
        if (extra != null && !extra.equals(keep)) {
            user.data().add(InheritanceNode.builder(extra).build());
        }
    }
}
