import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../data/console_line.dart';
import '../../data/server_snapshot.dart';
import '../../l10n/app_localizations.dart';
import '../../state/operator_session.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/aether_backdrop.dart';
import '../widgets/chrome.dart';
import '../widgets/glass_card.dart';
import '../widgets/sky_tile.dart';

/// Server detail opened from the network dashboard.
class ServerDetailScreen extends StatefulWidget {
  const ServerDetailScreen({super.key, required this.serverId});

  final String serverId;

  static Future<void> open(BuildContext context, String serverId) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ServerDetailScreen(serverId: serverId),
      ),
    );
  }

  @override
  State<ServerDetailScreen> createState() => _ServerDetailScreenState();
}

class _ServerDetailScreenState extends State<ServerDetailScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _input = TextEditingController();
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 4, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      SessionScope.of(context).selectServer(widget.serverId);
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  ServerSnapshot? _server(OperatorSession session) {
    final servers = session.network?.servers ?? const <ServerSnapshot>[];
    for (final s in servers) {
      if (s.id == widget.serverId) return s;
    }
    return null;
  }

  Future<void> _send(BuildContext context) async {
    final text = _input.text;
    if (text.trim().isEmpty) return;
    _input.clear();
    await SessionScope.of(context).submitCommand(
      text,
      serverId: widget.serverId,
    );
    await Future<void>.delayed(const Duration(milliseconds: 40));
    if (_scroll.hasClients) {
      await _scroll.animateTo(
        _scroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 240),
        curve: Curves.easeOutCubic,
      );
    }
  }

  Future<void> _confirm(
    BuildContext context, {
    required String title,
    required String body,
    required Future<void> Function() onConfirm,
  }) async {
    final l10n = AppLocalizations.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(body),
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
    if (ok == true && context.mounted) await onConfirm();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final server = _server(session);
    final name = server?.displayName ?? widget.serverId;
    final online = server?.online ?? false;

    return AetherBackdrop(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          title: Text(name),
          actions: [
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Center(
                child: StatusPill(
                  online: online,
                  onlineLabel: l10n.statusOnline,
                  offlineLabel: l10n.statusOffline,
                ),
              ),
            ),
          ],
          bottom: TabBar(
            controller: _tabs,
            isScrollable: true,
            indicatorColor: AetherColors.cyan,
            labelColor: AetherColors.cyan,
            unselectedLabelColor: AetherColors.mist,
            tabs: [
              Tab(text: l10n.serverTabOverview),
              Tab(text: l10n.serverTabTerminal),
              Tab(text: l10n.serverTabPower),
              Tab(text: l10n.serverTabTools),
            ],
          ),
        ),
        body: server == null
            ? Center(
                child: Text(
                  l10n.serverMissing,
                  style: const TextStyle(color: AetherColors.mist),
                ),
              )
            : TabBarView(
                controller: _tabs,
                children: [
                  _OverviewTab(server: server, l10n: l10n),
                  _TerminalTab(
                    serverId: widget.serverId,
                    input: _input,
                    scroll: _scroll,
                    onSend: () => _send(context),
                    l10n: l10n,
                  ),
                  _PowerTab(
                    server: server,
                    l10n: l10n,
                    live: session.craftyLive,
                    onConfirm: _confirm,
                  ),
                  _ToolsTab(
                    serverId: widget.serverId,
                    l10n: l10n,
                    onJumpTerminal: () => _tabs.animateTo(1),
                  ),
                ],
              ),
      ),
    );
  }
}

class _OverviewTab extends StatelessWidget {
  const _OverviewTab({required this.server, required this.l10n});

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
    final tps = server.role == ServerRole.proxy
        ? '—'
        : server.online
        ? server.tps.toStringAsFixed(2)
        : '0.00';
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
      children: [
        GlassCard(
          accent: server.online ? AetherColors.cyan : AetherColors.offline,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                server.displayName,
                style: const TextStyle(
                  fontFamily: AetherTheme.cinzel,
                  fontWeight: FontWeight.w800,
                  fontSize: 22,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _role(),
                style: const TextStyle(color: AetherColors.mist, fontSize: 13),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  SkyStatChip(
                    label: l10n.players,
                    value: '${server.players}/${server.maxPlayers}',
                    accent: AetherColors.cyan,
                  ),
                  SkyStatChip(
                    label: l10n.tps,
                    value: tps,
                    accent: AetherColors.amethyst,
                  ),
                  SkyStatChip(
                    label: l10n.statusOnline,
                    value: server.online
                        ? l10n.statusOnline
                        : l10n.statusOffline,
                    accent: server.online
                        ? AetherColors.online
                        : AetherColors.offline,
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        SkySectionLabel(l10n.serverQuickNav, accent: AetherColors.cyan),
        Text(
          l10n.serverOverviewHint,
          style: const TextStyle(color: AetherColors.mist, fontSize: 13),
        ),
      ],
    );
  }
}

class _TerminalTab extends StatelessWidget {
  const _TerminalTab({
    required this.serverId,
    required this.input,
    required this.scroll,
    required this.onSend,
    required this.l10n,
  });

  final String serverId;
  final TextEditingController input;
  final ScrollController scroll;
  final VoidCallback onSend;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    final session = SessionScope.of(context);
    final lines = session.consoleFor(serverId);
    return Column(
      children: [
        Expanded(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: GlassCard(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
              child: lines.isEmpty
                  ? Center(
                      child: Text(
                        l10n.consoleEmpty,
                        style: const TextStyle(color: AetherColors.mist),
                      ),
                    )
                  : ListView.builder(
                      controller: scroll,
                      itemCount: lines.length,
                      itemBuilder: (context, i) =>
                          _ConsoleLineView(line: lines[i]),
                    ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Row(
            children: [
              OutlinedButton.icon(
                onPressed: session.loadingLogs
                    ? null
                    : () => session.loadRemoteLogs(serverId: serverId),
                icon: const Icon(Icons.receipt_long_outlined, size: 18),
                label: Text(l10n.loadLogs),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: input,
                  onSubmitted: (_) => onSend(),
                  decoration: InputDecoration(hintText: l10n.consoleHint),
                ),
              ),
              const SizedBox(width: 10),
              FilledButton(
                onPressed: session.sendingCommand ? null : onSend,
                child: Text(l10n.send),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _PowerTab extends StatelessWidget {
  const _PowerTab({
    required this.server,
    required this.l10n,
    required this.live,
    required this.onConfirm,
  });

  final ServerSnapshot server;
  final AppLocalizations l10n;
  final bool live;
  final Future<void> Function(
    BuildContext context, {
    required String title,
    required String body,
    required Future<void> Function() onConfirm,
  })
  onConfirm;

  @override
  Widget build(BuildContext context) {
    final session = SessionScope.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
      children: [
        SkySectionLabel(l10n.serverPowerTitle, accent: AetherColors.gold),
        Text(
          live ? l10n.serverPowerLiveHint : l10n.serverPowerMockHint,
          style: const TextStyle(color: AetherColors.mist, fontSize: 13),
        ),
        const SizedBox(height: 14),
        SkyTile(
          icon: Icons.play_arrow_rounded,
          title: l10n.startServer,
          subtitle: l10n.serverPowerStartHint,
          accent: AetherColors.online,
          enabled: live,
          onTap: () => onConfirm(
            context,
            title: l10n.startServer,
            body: l10n.startServerConfirm(server.displayName),
            onConfirm: () => session.queueStart(serverId: server.id),
          ),
        ),
        const SizedBox(height: 10),
        SkyTile(
          icon: Icons.stop_rounded,
          title: l10n.stopServer,
          subtitle: l10n.serverPowerStopHint,
          accent: AetherColors.offline,
          enabled: live,
          onTap: () => onConfirm(
            context,
            title: l10n.stopServer,
            body: l10n.stopServerConfirm(server.displayName),
            onConfirm: () => session.queueStop(serverId: server.id),
          ),
        ),
        const SizedBox(height: 10),
        SkyTile(
          icon: Icons.restart_alt_rounded,
          title: l10n.softRestart,
          subtitle: l10n.serverPowerRestartHint,
          accent: AetherColors.cyan,
          onTap: () => onConfirm(
            context,
            title: l10n.softRestart,
            body: live
                ? l10n.softRestartConfirmLive(server.displayName)
                : l10n.softRestartConfirm(server.displayName),
            onConfirm: () => session.queueSoftRestart(serverId: server.id),
          ),
        ),
        const SizedBox(height: 10),
        SkyTile(
          icon: Icons.refresh_rounded,
          title: l10n.refresh,
          subtitle: l10n.serverPowerRefreshHint,
          accent: AetherColors.amethyst,
          onTap: session.refreshNetwork,
        ),
      ],
    );
  }
}

class _ToolsTab extends StatelessWidget {
  const _ToolsTab({
    required this.serverId,
    required this.l10n,
    required this.onJumpTerminal,
  });

  final String serverId;
  final AppLocalizations l10n;
  final VoidCallback onJumpTerminal;

  Future<void> _run(BuildContext context, String command) async {
    await SessionScope.of(context).submitCommand(
      command,
      serverId: serverId,
    );
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.toolCommandSent(command))),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final tools = <(IconData, String, String, String, Color)>[
      (
        Icons.people_outline,
        l10n.toolListPlayers,
        'list',
        l10n.toolListPlayersHint,
        AetherColors.cyan,
      ),
      (
        Icons.wb_sunny_outlined,
        l10n.toolDay,
        'time set day',
        l10n.toolDayHint,
        AetherColors.gold,
      ),
      (
        Icons.nights_stay_outlined,
        l10n.toolNight,
        'time set night',
        l10n.toolNightHint,
        AetherColors.amethyst,
      ),
      (
        Icons.cloud_off_outlined,
        l10n.toolClearWeather,
        'weather clear',
        l10n.toolClearWeatherHint,
        AetherColors.cyan,
      ),
      (
        Icons.save_outlined,
        l10n.toolSaveAll,
        'save-all',
        l10n.toolSaveAllHint,
        AetherColors.online,
      ),
      (
        Icons.bolt_outlined,
        l10n.toolTps,
        'tps',
        l10n.toolTpsHint,
        AetherColors.amethystDeep,
      ),
    ];

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
      children: [
        SkySectionLabel(l10n.serverToolsTitle, accent: AetherColors.amethyst),
        Text(
          l10n.serverToolsHint,
          style: const TextStyle(color: AetherColors.mist, fontSize: 13),
        ),
        const SizedBox(height: 14),
        for (final t in tools) ...[
          SkyTile(
            icon: t.$1,
            title: t.$2,
            subtitle: t.$4,
            accent: t.$5,
            onTap: () => _run(context, t.$3),
          ),
          const SizedBox(height: 10),
        ],
        SkyTile(
          icon: Icons.terminal_rounded,
          title: l10n.serverOpenTerminal,
          subtitle: l10n.serverOpenTerminalHint,
          accent: AetherColors.gold,
          onTap: onJumpTerminal,
        ),
      ],
    );
  }
}

class _ConsoleLineView extends StatelessWidget {
  const _ConsoleLineView({required this.line});

  final ConsoleLine line;

  @override
  Widget build(BuildContext context) {
    final color = switch (line.kind) {
      ConsoleKind.command => AetherColors.cyan,
      ConsoleKind.response => AetherColors.mist,
      ConsoleKind.system => AetherColors.gold,
      ConsoleKind.error => AetherColors.offline,
    };
    final prefix = switch (line.kind) {
      ConsoleKind.command => '❯',
      ConsoleKind.response => '·',
      ConsoleKind.system => '✦',
      ConsoleKind.error => '!',
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Text(
        '${DateFormat.Hms().format(line.at)}  $prefix  ${line.text}',
        style: TextStyle(
          color: color,
          fontFamily: 'monospace',
          fontSize: 12.5,
          height: 1.35,
        ),
      ),
    );
  }
}
