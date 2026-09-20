import 'package:flutter/material.dart';

import '../../data/player_actions.dart';
import '../../data/server_snapshot.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import 'dialogs.dart';
import 'role_chrome.dart';
import 'sky_tile.dart';

Future<void> showPlayerActionsSheet(
  BuildContext context, {
  required PlayerPresence player,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: AetherColors.ink,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
    ),
    builder: (_) => PlayerActionsSheet(player: player),
  );
}

class PlayerActionsSheet extends StatelessWidget {
  const PlayerActionsSheet({super.key, required this.player});

  final PlayerPresence player;

  Future<void> _run(
    BuildContext context,
    PlayerAction action, {
    String? reason,
    String? targetPlayer,
    String? coords,
  }) async {
    final session = SessionScope.of(context);
    if (!session.canPlayerAction(action)) {
      showRoleRestrictedSnack(context);
      return;
    }
    final err = await session.runPlayerAction(
      serverId: player.serverId,
      action: action,
      player: player.name,
      reason: reason,
      targetPlayer: targetPlayer,
      coords: coords,
    );
    if (!context.mounted) return;
    if (err == 'restricted') {
      showRoleRestrictedSnack(context);
      return;
    }
    final cmd = action.buildCommand(
      player.name,
      reason: reason,
      targetPlayer: targetPlayer,
      coords: coords,
    );
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(AppLocalizations.of(context).toolCommandSent(cmd))),
    );
    Navigator.pop(context);
  }

  Future<void> _confirmThen(
    BuildContext context,
    PlayerAction action, {
    String? reason,
    String? targetPlayer,
    String? coords,
  }) async {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    if (!session.canPlayerAction(action)) {
      showRoleRestrictedSnack(context);
      return;
    }
    if (action.needsConfirm) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(l10n.confirm),
          content: Text(l10n.playerActionConfirm(action.name, player.name)),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(l10n.confirm),
            ),
          ],
        ),
      );
      if (ok != true || !context.mounted) return;
    }
    await _run(
      context,
      action,
      reason: reason,
      targetPlayer: targetPlayer,
      coords: coords,
    );
  }

  Future<void> _promptAndRun(
    BuildContext context, {
    required PlayerAction action,
    required String title,
    required String hint,
    required String field,
  }) async {
    final session = SessionScope.of(context);
    if (!session.canPlayerAction(action)) {
      showRoleRestrictedSnack(context);
      return;
    }
    final value = await showNoteDialog(
      context: context,
      title: title,
      hint: hint,
    );
    if (value == null || !context.mounted) return;
    final allowEmpty =
        action == PlayerAction.kick || action == PlayerAction.ban;
    if (!allowEmpty && value.trim().isEmpty) return;
    await _confirmThen(
      context,
      action,
      reason: field == 'reason' ? value.trim() : null,
      targetPlayer: field == 'target' ? value.trim() : null,
      coords: field == 'coords' ? value.trim() : null,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final server = player.serverName ?? player.serverId;
    final bottom = MediaQuery.paddingOf(context).bottom;

    final catalog = <(IconData, String, String, Color, PlayerAction, VoidCallback)>[
      (
        Icons.logout_rounded,
        l10n.toolKick,
        l10n.toolKickHint,
        AetherColors.gold,
        PlayerAction.kick,
        () => _promptAndRun(
          context,
          action: PlayerAction.kick,
          title: l10n.toolKick,
          hint: l10n.playerReasonHint,
          field: 'reason',
        ),
      ),
      (
        Icons.gavel_rounded,
        l10n.toolBan,
        l10n.toolBanHint,
        AetherColors.offline,
        PlayerAction.ban,
        () => _promptAndRun(
          context,
          action: PlayerAction.ban,
          title: l10n.toolBan,
          hint: l10n.playerReasonHint,
          field: 'reason',
        ),
      ),
      (
        Icons.healing_outlined,
        l10n.toolPardon,
        l10n.toolPardonHint,
        AetherColors.online,
        PlayerAction.pardon,
        () => _confirmThen(context, PlayerAction.pardon),
      ),
      (
        Icons.shield_outlined,
        l10n.toolOp,
        l10n.toolOpHint,
        AetherColors.cyan,
        PlayerAction.op,
        () => _confirmThen(context, PlayerAction.op),
      ),
      (
        Icons.shield_outlined,
        l10n.toolDeop,
        l10n.toolDeopHint,
        AetherColors.mist,
        PlayerAction.deop,
        () => _confirmThen(context, PlayerAction.deop),
      ),
      (
        Icons.landscape_outlined,
        l10n.toolGamemodeSurvival,
        'gamemode survival',
        AetherColors.online,
        PlayerAction.gamemodeSurvival,
        () => _confirmThen(context, PlayerAction.gamemodeSurvival),
      ),
      (
        Icons.auto_awesome_outlined,
        l10n.toolGamemodeCreative,
        'gamemode creative',
        AetherColors.amethyst,
        PlayerAction.gamemodeCreative,
        () => _confirmThen(context, PlayerAction.gamemodeCreative),
      ),
      (
        Icons.explore_outlined,
        l10n.toolGamemodeAdventure,
        'gamemode adventure',
        AetherColors.gold,
        PlayerAction.gamemodeAdventure,
        () => _confirmThen(context, PlayerAction.gamemodeAdventure),
      ),
      (
        Icons.visibility_outlined,
        l10n.toolGamemodeSpectator,
        'gamemode spectator',
        AetherColors.cyan,
        PlayerAction.gamemodeSpectator,
        () => _confirmThen(context, PlayerAction.gamemodeSpectator),
      ),
      (
        Icons.chat_bubble_outline,
        l10n.toolMsg,
        l10n.toolMsgHint,
        AetherColors.cyan,
        PlayerAction.msg,
        () => _promptAndRun(
          context,
          action: PlayerAction.msg,
          title: l10n.toolMsg,
          hint: l10n.playerMsgHint,
          field: 'reason',
        ),
      ),
      (
        Icons.dangerous_outlined,
        l10n.toolKill,
        l10n.toolKillHint,
        AetherColors.offline,
        PlayerAction.kill,
        () => _confirmThen(context, PlayerAction.kill),
      ),
      (
        Icons.inventory_2_outlined,
        l10n.toolClear,
        l10n.toolClearHint,
        AetherColors.gold,
        PlayerAction.clear,
        () => _confirmThen(context, PlayerAction.clear),
      ),
      (
        Icons.playlist_add_check,
        l10n.toolWhitelistAdd,
        l10n.toolWhitelistAddHint,
        AetherColors.online,
        PlayerAction.whitelistAdd,
        () => _confirmThen(context, PlayerAction.whitelistAdd),
      ),
      (
        Icons.playlist_remove,
        l10n.toolWhitelistRemove,
        l10n.toolWhitelistRemoveHint,
        AetherColors.offline,
        PlayerAction.whitelistRemove,
        () => _confirmThen(context, PlayerAction.whitelistRemove),
      ),
      (
        Icons.person_pin_circle_outlined,
        l10n.toolTpPlayer,
        l10n.toolTpPlayerHint,
        AetherColors.amethyst,
        PlayerAction.tpToPlayer,
        () => _promptAndRun(
          context,
          action: PlayerAction.tpToPlayer,
          title: l10n.toolTpPlayer,
          hint: l10n.playerTpTargetHint,
          field: 'target',
        ),
      ),
      (
        Icons.my_location_outlined,
        l10n.toolTpCoords,
        l10n.toolTpCoordsHint,
        AetherColors.cyan,
        PlayerAction.tpCoords,
        () => _promptAndRun(
          context,
          action: PlayerAction.tpCoords,
          title: l10n.toolTpCoords,
          hint: l10n.playerTpCoordsHint,
          field: 'coords',
        ),
      ),
    ];

    final actions = [
      for (final a in catalog)
        if (session.canPlayerAction(a.$5)) a,
    ];

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 16 + bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AetherColors.glassStrokeHot,
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              player.name,
              style: const TextStyle(
                fontFamily: AetherTheme.cinzel,
                fontWeight: FontWeight.w800,
                fontSize: 22,
                letterSpacing: 1.1,
              ),
            ),
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(999),
                  color: AetherColors.cyan.withValues(alpha: 0.14),
                  border: Border.all(
                    color: AetherColors.cyan.withValues(alpha: 0.4),
                  ),
                ),
                child: Text(
                  l10n.playerOnServer(server),
                  style: const TextStyle(
                    color: AetherColors.cyan,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              session.current?.isObserver == true
                  ? l10n.observerBanner
                  : l10n.playerActions,
              style: const TextStyle(color: AetherColors.mist, fontSize: 13),
            ),
            const SizedBox(height: 14),
            if (actions.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Text(
                  l10n.roleRestricted,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AetherColors.mist),
                ),
              )
            else
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: MediaQuery.sizeOf(context).height * 0.58,
                ),
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: actions.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, i) {
                    final a = actions[i];
                    return SkyTile(
                      icon: a.$1,
                      title: a.$2,
                      subtitle: a.$3,
                      accent: a.$4,
                      onTap: a.$6,
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}
