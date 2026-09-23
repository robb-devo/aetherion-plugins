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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class LuckPermsSilent {

    private LuckPermsSilent() {
    }

    /**
     * Homie / Monkey content kit — never {@code aetherion.dev} (full admin).
     * Tools only: Content Kit UI, NPC editor, self shards, flight.
     * Citrus and Beta do not receive this kit.
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

    /**
     * XP progression groups keep weight and prefix.
     * Cosmetic groups (monkey, citrus, beta, mvpplusplus, admin, owner) are never
     * created here and never receive a prefix or {@code aetherion.rank.*} node.
     * Those nodes used to be read back on join and written into {@code player-ranks.yml}.
     */
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
            if (RankBadgeService.isPermanentExtra(rank.group())) {
                continue;
            }
            api.getGroupManager().createAndLoadGroup(rank.group()).thenAccept(group -> {
                if (group == null) {
                    return;
                }
                writeGroupMeta(group, rank);
                api.getGroupManager().saveGroup(group);
            });
        }
        for (String cosmetic : cosmeticGroupNames(mvpGroup)) {
            api.getGroupManager().loadGroup(cosmetic).thenAccept(optional -> optional.ifPresent(group -> {
                stripCosmeticDisplay(group);
                if ("monkey".equalsIgnoreCase(cosmetic)) {
                    grantContentKit(group);
                    denyFullDev(group);
                } else if ("admin".equalsIgnoreCase(cosmetic)) {
                    group.data().add(PermissionNode.builder("aetherion.npc.editor").value(true).build());
                } else {
                    // Citrus, Beta, MVP++, owner: cosmetics only. Do not negate aetherion.dev
                    // on the user — an admin who also wears one must keep the full DEV tree.
                    stripFullDevGrant(group);
                    stripContentKitGrants(group);
                }
                api.getGroupManager().saveGroup(group);
            }));
        }
        for (String staff : List.of("moderator", "mod")) {
            api.getGroupManager().loadGroup(staff).thenAccept(optional -> optional.ifPresent(group -> {
                grantContentKit(group);
                denyFullDev(group);
                api.getGroupManager().saveGroup(group);
            }));
        }
    }

    /**
     * Cosmetic parent this apply would add. Always {@code null}: special ranks
     * are not copied from LuckPerms, permission nodes, or a detected group.
     */
    static String cosmeticParentToAttach(String storedOrDetected) {
        return null;
    }

    /** Progression parent written to LuckPerms. Cosmetic names fall back to adventurer. */
    static String progressionParentToAttach(String keepGroup) {
        if (keepGroup == null || keepGroup.isBlank() || RankBadgeService.isPermanentExtra(keepGroup)) {
            return "adventurer";
        }
        return keepGroup.toLowerCase(Locale.ROOT);
    }

    /**
     * Inheritance removed on every user apply. Cosmetic extras are included so a
     * wiped {@code player-ranks.yml} cannot leave LimePuppet in {@code citrus}.
     * The {@code admin} staff group is left in place; its prefix is cleared so it
     * cannot paint [Admin].
     */
    static Set<String> parentsClearedOnApply(Set<String> managedGroups) {
        Set<String> drop = new LinkedHashSet<>();
        if (managedGroups != null) {
            for (String group : managedGroups) {
                if (group == null || group.isBlank()) {
                    continue;
                }
                String key = group.toLowerCase(Locale.ROOT);
                if (!RankBadgeService.isPermanentExtra(key)) {
                    drop.add(key);
                }
            }
        }
        for (String extra : RankBadgeService.extraGroups()) {
            if ("admin".equalsIgnoreCase(extra)) {
                continue;
            }
            drop.add(extra.toLowerCase(Locale.ROOT));
        }
        drop.add("owner");
        return Set.copyOf(drop);
    }

    private static Set<String> cosmeticGroupNames(String mvpGroup) {
        Set<String> names = new LinkedHashSet<>(RankBadgeService.extraGroups());
        if (mvpGroup != null && !mvpGroup.isBlank()) {
            names.add(mvpGroup.toLowerCase(Locale.ROOT));
        }
        names.add("owner");
        return names;
    }

    /** Drop prefix and rank-permission nodes so LuckPerms cannot paint a special rank. */
    private static void stripCosmeticDisplay(Group group) {
        if (group == null) {
            return;
        }
        group.data().clear(NodeType.PREFIX.predicate(node -> true));
        group.data().clear(NodeType.PERMISSION.predicate(node -> isRankPermission(node.getKey())));
    }

    private static boolean isRankPermission(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).startsWith("aetherion.rank.");
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

    /** Citrus and Beta are cosmetics only — never pick up content-kit nodes. */
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

    /**
     * Writes the XP progression parent only. Cosmetic parents are removed and never re-added.
     */
    static void applyUser(UUID playerId, String keepGroup, Set<String> managedGroups) {
        if (playerId == null || keepGroup == null || !available()) {
            return;
        }
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return;
        }
        String keep = progressionParentToAttach(keepGroup);
        Set<String> clear = parentsClearedOnApply(managedGroups);
        api.getUserManager().modifyUser(playerId, user -> paintUser(user, keep, clear));
    }

    /** Dev Menu monkey row: content tools on the user, not a cosmetic parent group. */
    static void grantContentKitToUser(UUID playerId) {
        modifyUser(playerId, user -> {
            for (String node : CONTENT_PERMISSIONS) {
                user.data().add(PermissionNode.builder(node).value(true).build());
            }
        });
    }

    /** Dev Menu removed monkey. Does not touch {@code aetherion.dev}. */
    static void revokeContentKitFromUser(UUID playerId) {
        modifyUser(playerId, user -> {
            for (String node : CONTENT_PERMISSIONS) {
                user.data().clear(NodeType.PERMISSION.predicate(existing ->
                        node.equalsIgnoreCase(existing.getKey()) && existing.getValue()));
            }
        });
    }

    private static void modifyUser(UUID playerId, Consumer<User> editor) {
        if (playerId == null || editor == null || !available()) {
            return;
        }
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return;
        }
        api.getUserManager().modifyUser(playerId, editor::accept);
    }

    private static void writeGroupMeta(Group group, RankBadgeService.Rank rank) {
        group.data().clear(NodeType.WEIGHT.predicate(node -> true));
        group.data().clear(NodeType.PREFIX.predicate(node -> true));
        if (RankBadgeService.isPermanentExtra(rank.group())) {
            return;
        }
        group.data().add(WeightNode.builder(rank.weight()).build());
        group.data().add(PrefixNode.builder(rank.prefix(), rank.weight()).build());
    }

    private static void paintUser(User user, String keep, Set<String> clearParents) {
        user.data().clear(NodeType.INHERITANCE.predicate(node ->
                clearParents.contains(node.getGroupName().toLowerCase(Locale.ROOT))));
        user.data().add(InheritanceNode.builder(keep).build());
    }
}
