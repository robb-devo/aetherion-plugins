package de.aetherion.items.social;

import de.aetherion.core.api.PartyAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService implements PartyAccess {

    public static final int MAX_SIZE = 4;
    private static final long INVITE_MS = 60_000L;

    private final Map<UUID, Party> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public boolean inParty(Player player) {
        return player != null && byPlayer.containsKey(player.getUniqueId());
    }

    public boolean isLeader(Player player) {
        if (player == null) {
            return false;
        }
        Party party = byPlayer.get(player.getUniqueId());
        return party != null && party.leader.equals(player.getUniqueId());
    }

    public Party partyOf(Player player) {
        return player == null ? null : byPlayer.get(player.getUniqueId());
    }

    public List<Player> onlineMembers(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            return player == null || !player.isOnline() ? List.of() : List.of(player);
        }
        List<Player> online = new ArrayList<>();
        for (UUID id : party.members) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                online.add(member);
            }
        }
        return online;
    }

    public boolean invite(Player leader, Player target) {
        if (leader == null || target == null || leader.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }
        Party party = byPlayer.get(leader.getUniqueId());
        if (party != null && !party.leader.equals(leader.getUniqueId())) {
            leader.sendMessage("§cOnly the party leader can invite.");
            return false;
        }
        if (party != null && party.members.size() >= MAX_SIZE) {
            leader.sendMessage("§cParty is full. §8(" + MAX_SIZE + ")");
            return false;
        }
        if (byPlayer.containsKey(target.getUniqueId())) {
            leader.sendMessage("§c" + target.getName() + " is already in a party.");
            return false;
        }
        invites.put(target.getUniqueId(), new Invite(leader.getUniqueId(), System.currentTimeMillis() + INVITE_MS));
        leader.sendMessage("§aInvited §f" + target.getName() + "§a. They have 60 seconds.");
        target.sendMessage("§e" + leader.getName() + " §7invited you to a party. §a/party accept");
        return true;
    }

    public boolean accept(Player player) {
        if (player == null) {
            return false;
        }
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            player.sendMessage("§cNo party invite waiting.");
            return false;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou are already in a party. §7/party leave first.");
            return false;
        }
        Player leader = Bukkit.getPlayer(invite.leader);
        if (leader == null || !leader.isOnline()) {
            player.sendMessage("§cThat party leader went offline.");
            return false;
        }
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            party = new Party(leader.getUniqueId());
            byPlayer.put(leader.getUniqueId(), party);
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            player.sendMessage("§cThat invite is stale.");
            return false;
        }
        if (party.members.size() >= MAX_SIZE) {
            player.sendMessage("§cThat party filled up.");
            return false;
        }
        party.members.add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), party);
        broadcast(party, "§a" + player.getName() + " §7joined the party. §f" + party.members.size() + "/" + MAX_SIZE);
        return true;
    }

    public boolean deny(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§cNo party invite waiting.");
            return false;
        }
        player.sendMessage("§7Invite declined.");
        Player leader = Bukkit.getPlayer(invite.leader);
        if (leader != null && leader.isOnline()) {
            leader.sendMessage("§e" + player.getName() + " §7declined the invite.");
        }
        return true;
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cYou are not in a party.");
            return false;
        }
        if (party.leader.equals(player.getUniqueId())) {
            if (party.members.size() <= 1) {
                disband(party, "§7Party disbanded.");
                player.sendMessage("§7Party disbanded.");
                return true;
            }
            party.members.remove(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            UUID next = party.members.iterator().next();
            party.leader = next;
            Player promoted = Bukkit.getPlayer(next);
            broadcast(party, "§e" + player.getName() + " §7left. §f"
                    + (promoted == null ? "A member" : promoted.getName()) + " §7is the new leader.");
            player.sendMessage("§7You left the party.");
            return true;
        }
        party.members.remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());
        broadcast(party, "§e" + player.getName() + " §7left the party. §f" + party.members.size() + "/" + MAX_SIZE);
        player.sendMessage("§7You left the party.");
        return true;
    }

    public boolean kick(Player leader, Player target) {
        Party party = partyOf(leader);
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            leader.sendMessage("§cOnly the party leader can kick.");
            return false;
        }
        if (target == null || !party.members.contains(target.getUniqueId())
                || target.getUniqueId().equals(leader.getUniqueId())) {
            leader.sendMessage("§cThey are not in your party.");
            return false;
        }
        party.members.remove(target.getUniqueId());
        byPlayer.remove(target.getUniqueId());
        target.sendMessage("§cYou were kicked from the party.");
        broadcast(party, "§e" + target.getName() + " §7was kicked. §f" + party.members.size() + "/" + MAX_SIZE);
        return true;
    }

    public boolean disband(Player leader) {
        Party party = partyOf(leader);
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            leader.sendMessage("§cOnly the party leader can disband.");
            return false;
        }
        disband(party, "§7Party disbanded by " + leader.getName() + ".");
        return true;
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        invites.remove(player.getUniqueId());
        if (byPlayer.containsKey(player.getUniqueId())) {
            leave(player);
        }
    }

    public void sendList(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            player.sendMessage("§7You are not in a party. §e/party invite <player>");
            return;
        }
        player.sendMessage("§eParty §8(§f" + party.members.size() + "§8/§f" + MAX_SIZE + "§8)");
        for (UUID id : party.members) {
            Player member = Bukkit.getPlayer(id);
            String name = member == null ? Bukkit.getOfflinePlayer(id).getName() : member.getName();
            boolean online = member != null && member.isOnline();
            String tag = party.leader.equals(id) ? "§6Leader" : "§7Member";
            player.sendMessage(" §8- " + tag + " §f" + name + (online ? " §a●" : " §8offline"));
        }
    }

    private void disband(Party party, String message) {
        broadcast(party, message);
        for (UUID id : new ArrayList<>(party.members)) {
            byPlayer.remove(id);
        }
        party.members.clear();
    }

    private void broadcast(Party party, String message) {
        for (UUID id : party.members) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        private Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
        }

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return members;
        }
    }

    private record Invite(UUID leader, long expiresAt) {
    }
}
