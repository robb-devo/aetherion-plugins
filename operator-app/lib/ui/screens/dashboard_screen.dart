import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../data/server_snapshot.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/chrome.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';
import 'server_detail_screen.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final network = session.network;
    final time = network == null
        ? '—'
        : DateFormat.Hms().format(network.fetchedAt);

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        SectionHeader(
          title: l10n.networkOverview,
          body: l10n.extensionHint,
          trailing: Row(
            children: [
              MockBadge(
                mock: network?.mock ?? true,
                mockLabel: l10n.mockBadge,
                liveLabel: l10n.craftyLive,
              ),
              const SizedBox(width: 8),
              IconButton(
                key: const Key('refresh-network'),
                tooltip: l10n.refresh,
                onPressed: session.loadingNetwork
                    ? null
                    : session.refreshNetwork,
                icon: session.loadingNetwork
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        Text(
          l10n.lastFetched(time),
          style: const TextStyle(color: AetherColors.mist, fontSize: 12),
        ),
        const SizedBox(height: 16),
        if (session.craftyLive == false)
          _MockConnectCard(
            onOpenSettings: () => session.requestShellTab(5),
          ),
        if (session.craftyLive == false) const SizedBox(height: 16),
        if (session.networkError != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(
              session.networkError!,
              style: const TextStyle(color: AetherColors.offline),
            ),
          ),
        LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 720;
            final stats = [
              _StatBox(
                label: l10n.players,
                value: '${network?.playerTotal ?? '—'}',
                accent: AetherColors.cyan,
              ),
              _StatBox(
                label: l10n.tps,
                value: network == null
                    ? '—'
                    : network.avgTps.toStringAsFixed(2),
                accent: AetherColors.amethyst,
              ),
              _StatBox(
                label: l10n.serversOnline,
                value: network == null
                    ? '—'
                    : '${network.onlineCount}/${network.servers.length}',
                accent: AetherColors.gold,
              ),
            ];
            if (wide) {
              return Row(
                children: [
                  for (var i = 0; i < stats.length; i++) ...[
                    if (i > 0) const SizedBox(width: 12),
                    Expanded(child: stats[i]),
                  ],
                ],
              );
            }
            return Column(
              children: [
                for (final s in stats)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: s,
                  ),
              ],
            );
          },
        ),
        const SizedBox(height: 18),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 320),
          switchInCurve: Curves.easeOutCubic,
          child: Column(
            key: ValueKey(network?.fetchedAt.toIso8601String() ?? 'empty'),
            children: [
              for (final server in network?.servers ?? const <ServerSnapshot>[])
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _ServerRow(server: server, l10n: l10n),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _MockConnectCard extends StatelessWidget {
  const _MockConnectCard({required this.onOpenSettings});

  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return GlassCard(
      accent: AetherColors.gold,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.cloud_off_outlined,
                color: AetherColors.gold.withValues(alpha: 0.95),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l10n.mockConnectTitle,
                  style: const TextStyle(
                    fontFamily: AetherTheme.cinzel,
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                    letterSpacing: 0.6,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            l10n.mockConnectBody,
            style: const TextStyle(
              color: AetherColors.mist,
              height: 1.4,
              fontSize: 13.5,
            ),
          ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: onOpenSettings,
            icon: const Icon(Icons.settings_outlined, size: 18),
            label: Text(l10n.mockConnectAction),
          ),
        ],
      ),
    );
  }
}

class _StatBox extends StatelessWidget {
  const _StatBox({
    required this.label,
    required this.value,
    required this.accent,
  });

  final String label;
  final String value;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      accent: accent,
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label.toUpperCase(),
            style: TextStyle(
              color: accent.withValues(alpha: 0.9),
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.1,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: const TextStyle(
              fontFamily: AetherTheme.cinzel,
              fontSize: 28,
              fontWeight: FontWeight.w700,
              height: 1.05,
            ),
          ),
        ],
      ),
    );
  }
}

class _ServerRow extends StatelessWidget {
  const _ServerRow({required this.server, required this.l10n});

  final ServerSnapshot server;
  final AppLocalizations l10n;

  String _role() {
    return switch (server.role) {
      ServerRole.proxy => l10n.serverRoleProxy,
      ServerRole.hub => l10n.serverRoleHub,
      ServerRole.capital => l10n.serverRoleCapital,
      ServerRole.dungeons => l10n.serverRoleDungeons,
      ServerRole.ashes => l10n.serverRoleAshes,
    };
  }

  @override
  Widget build(BuildContext context) {
    final tpsLabel = server.role == ServerRole.proxy
        ? '—'
        : server.online
        ? server.tps.toStringAsFixed(2)
        : '0.00';
    final meta = <String>[
      if (server.worldName != null) server.worldName!,
      if (server.version != null) server.version!,
    ].join(' · ');
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      accent: server.online ? AetherColors.cyan : AetherColors.offline,
      onTap: () => ServerDetailScreen.open(context, server.id),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      server.displayName,
                      style: const TextStyle(
                        fontFamily: AetherTheme.cinzel,
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                        letterSpacing: 0.6,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      meta.isEmpty ? _role() : '${_role()} · $meta',
                      style: const TextStyle(
                        color: AetherColors.mist,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              StatusPill(
                online: server.online,
                onlineLabel: l10n.statusOnline,
                offlineLabel: l10n.statusOffline,
              ),
              const SizedBox(width: 6),
              const Icon(
                Icons.chevron_right_rounded,
                color: AetherColors.mist,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _mini(l10n.players, '${server.players}/${server.maxPlayers}'),
              const SizedBox(width: 16),
              _mini(l10n.tps, tpsLabel),
              if (server.cpuPercent != null) ...[
                const SizedBox(width: 16),
                _mini(l10n.cpu, '${server.cpuPercent!.toStringAsFixed(0)}%'),
              ],
              const SizedBox(width: 16),
              Expanded(
                child: _TpsBar(
                  value: server.role == ServerRole.proxy ? 20 : server.tps,
                  online: server.online,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _mini(String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label.toUpperCase(),
          style: const TextStyle(
            color: AetherColors.mist,
            fontSize: 10,
            letterSpacing: 0.8,
            fontWeight: FontWeight.w700,
          ),
        ),
        Text(
          value,
          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
        ),
      ],
    );
  }
}

class _TpsBar extends StatelessWidget {
  const _TpsBar({required this.value, required this.online});

  final double value;
  final bool online;

  @override
  Widget build(BuildContext context) {
    final t = (value / 20).clamp(0.0, 1.0);
    final color = !online
        ? AetherColors.offline
        : t > 0.9
        ? AetherColors.online
        : t > 0.7
        ? AetherColors.gold
        : AetherColors.offline;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'TICK',
          style: TextStyle(
            color: AetherColors.mist,
            fontSize: 10,
            letterSpacing: 0.8,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: online ? t : 0),
            duration: const Duration(milliseconds: 500),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) {
              return LinearProgressIndicator(
                value: v,
                minHeight: 6,
                backgroundColor: AetherColors.glassFillStrong,
                color: color,
              );
            },
          ),
        ),
      ],
    );
  }
}
