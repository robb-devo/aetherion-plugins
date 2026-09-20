import 'package:flutter/material.dart';

import '../../data/server_snapshot.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';
import '../widgets/player_actions_sheet.dart';

class PlayersScreen extends StatefulWidget {
  const PlayersScreen({super.key});

  @override
  State<PlayersScreen> createState() => _PlayersScreenState();
}

class _PlayersScreenState extends State<PlayersScreen> {
  final _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  List<PlayerPresence> _filtered(List<PlayerPresence> all) {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return all;
    return all
        .where(
          (p) =>
              p.name.toLowerCase().contains(q) ||
              (p.serverName ?? p.serverId).toLowerCase().contains(q),
        )
        .toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final all = session.network?.allPlayers ?? const <PlayerPresence>[];
    final players = _filtered(all);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
          child: SectionHeader(
            title: l10n.playersTitle,
            body: l10n.playersBody,
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 8),
          child: TextField(
            controller: _search,
            onChanged: (v) => setState(() => _query = v),
            decoration: InputDecoration(
              hintText: l10n.playersSearch,
              prefixIcon: const Icon(Icons.search, color: AetherColors.mist),
              isDense: true,
            ),
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            color: AetherColors.cyan,
            onRefresh: () async {
              await session.refreshNetwork();
              await session.refreshPlayersViaList();
            },
            child: players.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 48, 20, 32),
                    children: [
                      Icon(
                        Icons.people_outline,
                        size: 48,
                        color: AetherColors.mist.withValues(alpha: 0.45),
                      ),
                      const SizedBox(height: 14),
                      Text(
                        l10n.playersEmpty,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: AetherColors.mist,
                          height: 1.4,
                        ),
                      ),
                    ],
                  )
                : ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 28),
                    itemCount: players.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, i) {
                      final p = players[i];
                      return GlassCard(
                        padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
                        accent: AetherColors.cyan,
                        onTap: () => showPlayerActionsSheet(context, player: p),
                        child: Row(
                          children: [
                            CircleAvatar(
                              backgroundColor: AetherColors.cyan.withValues(
                                alpha: 0.18,
                              ),
                              foregroundColor: AetherColors.cyan,
                              child: Text(
                                p.name.isEmpty
                                    ? '?'
                                    : p.name[0].toUpperCase(),
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    p.name,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w800,
                                      fontSize: 15,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    l10n.playerOnServer(
                                      p.serverName ?? p.serverId,
                                    ),
                                    style: const TextStyle(
                                      color: AetherColors.mist,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const Icon(
                              Icons.tune_rounded,
                              color: AetherColors.mist,
                              size: 20,
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ),
      ],
    );
  }
}
