import 'dart:async';

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
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';
import '../widgets/player_actions_sheet.dart';
import '../widgets/role_chrome.dart';
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
  Timer? _tailTimer;

  static const _terminalTab = 2;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 5, vsync: this);
    _tabs.addListener(_onTab);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      SessionScope.of(context).selectServer(widget.serverId);
    });
  }

  void _onTab() {
    if (_tabs.indexIsChanging) return;
    _syncTail();
  }

  void _syncTail() {
    _tailTimer?.cancel();
    _tailTimer = null;
    if (_tabs.index != _terminalTab) return;
    _tailTimer = Timer.periodic(const Duration(seconds: 8), (_) {
      if (!mounted || _tabs.index != _terminalTab) return;
      final session = SessionScope.of(context);
      if (!session.loadingLogs) {
        session.loadRemoteLogs(serverId: widget.serverId);
      }
    });
  }

  @override
  void dispose() {
    _tailTimer?.cancel();
    _tabs.removeListener(_onTab);
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

  Future<void> _runTool(BuildContext context, String command) async {
    final l10n = AppLocalizations.of(context);
    await SessionScope.of(context).submitCommand(
      command,
      serverId: widget.serverId,
    );
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.toolCommandSent(command))),
      );
    }
  }

  Future<void> _promptTool(
    BuildContext context, {
    required String title,
    required String hint,
    required String Function(String v) build,
  }) async {
    final value = await showNoteDialog(
      context: context,
      title: title,
      hint: hint,
    );
    if (value == null || value.trim().isEmpty || !context.mounted) return;
    await _runTool(context, build(value.trim()));
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
              Tab(text: l10n.serverTabPlayers),
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
                  _OverviewTab(
                    server: server,
                    l10n: l10n,
                    onConfirm: _confirm,
                    onSay: () => _promptTool(
                      context,
                      title: l10n.toolSay,
                      hint: l10n.toolSayHint,
                      build: (v) => 'say $v',
                    ),
                  ),
                  _PlayersTab(server: server, l10n: l10n),
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
                    onJumpTerminal: () => _tabs.animateTo(_terminalTab),
                    onRun: (cmd) => _runTool(context, cmd),
                    onPrompt: _promptTool,
                  ),
                ],
              ),
      ),
    );
  }
}

class _OverviewTab extends StatelessWidget {
  const _OverviewTab({
    required this.server,
    required this.l10n,
    required this.onConfirm,
    required this.onSay,
  });

  final ServerSnapshot server;
  final AppLocalizations l10n;
  final Future<void> Function(
    BuildContext context, {
    required String title,
    required String body,
    required Future<void> Function() onConfirm,
  })
  onConfirm;
  final VoidCallback onSay;

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
    final session = SessionScope.of(context);
    final tps = server.role == ServerRole.proxy
        ? '—'
        : server.online
        ? server.tps.toStringAsFixed(2)
        : '0.00';
    final cpu = server.cpuPercent == null
        ? '—'
        : '${server.cpuPercent!.toStringAsFixed(0)}%';
    final ram = server.memPercent == null
        ? '—'
        : '${server.memPercent!.toStringAsFixed(0)}%';

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
                    label: l10n.cpu,
                    value: cpu,
                    accent: AetherColors.gold,
                  ),
                  SkyStatChip(
                    label: l10n.ram,
                    value: ram,
                    accent: AetherColors.amethyst,
                  ),
                  SkyStatChip(
                    label: l10n.tps,
                    value: tps,
                    accent: AetherColors.cyan,
                  ),
                  SkyStatChip(
                    label: l10n.players,
                    value: '${server.players}/${server.maxPlayers}',
                    accent: AetherColors.online,
                  ),
                  if (server.worldName != null)
                    SkyStatChip(
                      label: l10n.world,
                      value: server.worldName!,
                      accent: AetherColors.gold,
                    ),
                  if (server.version != null)
                    SkyStatChip(
                      label: l10n.version,
                      value: server.version!,
                      accent: AetherColors.mist,
                    ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        SkySectionLabel(l10n.serverQuickNav, accent: AetherColors.cyan),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            FilledButton.tonalIcon(
              onPressed: () {
                if (!session.canPower) {
                  showRoleRestrictedSnack(context);
                  return;
                }
                if (!session.craftyLive) return;
                onConfirm(
                  context,
                  title: l10n.startServer,
                  body: l10n.startServerConfirm(server.displayName),
                  onConfirm: () => session.queueStart(serverId: server.id),
                );
              },
              icon: const Icon(Icons.play_arrow, size: 18),
              label: Text(l10n.startServer),
            ),
            OutlinedButton.icon(
              onPressed: () {
                if (!session.canPower) {
                  showRoleRestrictedSnack(context);
                  return;
                }
                if (!session.craftyLive) return;
                onConfirm(
                  context,
                  title: l10n.stopServer,
                  body: l10n.stopServerConfirm(server.displayName),
                  onConfirm: () => session.queueStop(serverId: server.id),
                );
              },
              icon: const Icon(Icons.stop, size: 18),
              label: Text(l10n.stopServer),
            ),
            OutlinedButton.icon(
              onPressed: () {
                if (!session.canPower) {
                  showRoleRestrictedSnack(context);
                  return;
                }
                onConfirm(
                  context,
                  title: l10n.softRestart,
                  body: session.craftyLive
                      ? l10n.softRestartConfirmLive(server.displayName)
                      : l10n.softRestartConfirm(server.displayName),
                  onConfirm: () =>
                      session.queueSoftRestart(serverId: server.id),
                );
              },
              icon: const Icon(Icons.restart_alt, size: 18),
              label: Text(l10n.softRestart),
            ),
            OutlinedButton.icon(
              onPressed: onSay,
              icon: const Icon(Icons.campaign_outlined, size: 18),
              label: Text(l10n.toolSay),
            ),
          ],
        ),
      ],
    );
  }
}

class _PlayersTab extends StatelessWidget {
  const _PlayersTab({required this.server, required this.l10n});

  final ServerSnapshot server;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    final session = SessionScope.of(context);
    final players = server.onlinePlayers;
    return RefreshIndicator(
      color: AetherColors.cyan,
      onRefresh: () => session.refreshPlayersViaList(serverId: server.id),
      child: players.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 48, 20, 28),
              children: [
                Text(
                  l10n.playersEmpty,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AetherColors.mist),
                ),
              ],
            )
          : ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
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
                          p.name.isEmpty ? '?' : p.name[0].toUpperCase(),
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          p.name,
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 15,
                          ),
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
          enabled: live && session.canPower,
          onTap: live && session.canPower
              ? () => onConfirm(
                  context,
                  title: l10n.startServer,
                  body: l10n.startServerConfirm(server.displayName),
                  onConfirm: () => session.queueStart(serverId: server.id),
                )
              : null,
        ),
        const SizedBox(height: 10),
        SkyTile(
          icon: Icons.stop_rounded,
          title: l10n.stopServer,
          subtitle: l10n.serverPowerStopHint,
          accent: AetherColors.offline,
          enabled: live && session.canPower,
          onTap: live && session.canPower
              ? () => onConfirm(
                  context,
                  title: l10n.stopServer,
                  body: l10n.stopServerConfirm(server.displayName),
                  onConfirm: () => session.queueStop(serverId: server.id),
                )
              : null,
        ),
        const SizedBox(height: 10),
        SkyTile(
          icon: Icons.restart_alt_rounded,
          title: l10n.softRestart,
          subtitle: l10n.serverPowerRestartHint,
          accent: AetherColors.cyan,
          enabled: session.canPower,
          onTap: session.canPower
              ? () => onConfirm(
                  context,
                  title: l10n.softRestart,
                  body: live
                      ? l10n.softRestartConfirmLive(server.displayName)
                      : l10n.softRestartConfirm(server.displayName),
                  onConfirm: () =>
                      session.queueSoftRestart(serverId: server.id),
                )
              : null,
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
    required this.onRun,
    required this.onPrompt,
  });

  final String serverId;
  final AppLocalizations l10n;
  final VoidCallback onJumpTerminal;
  final Future<void> Function(String command) onRun;
  final Future<void> Function(
    BuildContext context, {
    required String title,
    required String hint,
    required String Function(String v) build,
  })
  onPrompt;

  Widget _grid(List<Widget> tiles) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth > 420;
        if (!wide) {
          return Column(
            children: [
              for (var i = 0; i < tiles.length; i++) ...[
                if (i > 0) const SizedBox(height: 8),
                tiles[i],
              ],
            ],
          );
        }
        final half = (constraints.maxWidth - 8) / 2;
        return Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final t in tiles) SizedBox(width: half, child: t),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
      children: [
        SkySectionLabel(l10n.serverToolsTitle, accent: AetherColors.amethyst),
        Text(
          l10n.serverToolsHint,
          style: const TextStyle(color: AetherColors.mist, fontSize: 13),
        ),
        const SizedBox(height: 14),
        SkySectionLabel(l10n.devkitWorld, accent: AetherColors.gold),
        _grid([
          SkyTile(
            icon: Icons.wb_sunny_outlined,
            title: l10n.toolDay,
            subtitle: l10n.toolDayHint,
            accent: AetherColors.gold,
            onTap: () => onRun('time set day'),
          ),
          SkyTile(
            icon: Icons.nights_stay_outlined,
            title: l10n.toolNight,
            subtitle: l10n.toolNightHint,
            accent: AetherColors.amethyst,
            onTap: () => onRun('time set night'),
          ),
          SkyTile(
            icon: Icons.cloud_off_outlined,
            title: l10n.toolClearWeather,
            subtitle: l10n.toolClearWeatherHint,
            accent: AetherColors.cyan,
            onTap: () => onRun('weather clear'),
          ),
          SkyTile(
            icon: Icons.water_drop_outlined,
            title: l10n.toolRain,
            subtitle: l10n.toolRainHint,
            accent: AetherColors.cyan,
            onTap: () => onRun('weather rain'),
          ),
          SkyTile(
            icon: Icons.thunderstorm_outlined,
            title: l10n.toolThunder,
            subtitle: l10n.toolThunderHint,
            accent: AetherColors.amethystDeep,
            onTap: () => onRun('weather thunder'),
          ),
          SkyTile(
            icon: Icons.save_outlined,
            title: l10n.toolSaveAll,
            subtitle: l10n.toolSaveAllHint,
            accent: AetherColors.online,
            onTap: () => onRun('save-all'),
          ),
          SkyTile(
            icon: Icons.bolt_outlined,
            title: l10n.toolTps,
            subtitle: l10n.toolTpsHint,
            accent: AetherColors.amethystDeep,
            onTap: () => onRun('tps'),
          ),
          SkyTile(
            icon: Icons.lock_outline,
            title: l10n.toolWhitelistOn,
            subtitle: 'whitelist on',
            accent: AetherColors.gold,
            onTap: () => onRun('whitelist on'),
          ),
          SkyTile(
            icon: Icons.lock_open_outlined,
            title: l10n.toolWhitelistOff,
            subtitle: 'whitelist off',
            accent: AetherColors.mist,
            onTap: () => onRun('whitelist off'),
          ),
          SkyTile(
            icon: Icons.campaign_outlined,
            title: l10n.toolSay,
            subtitle: l10n.toolSayHint,
            accent: AetherColors.cyan,
            onTap: () => onPrompt(
              context,
              title: l10n.toolSay,
              hint: l10n.toolSayHint,
              build: (v) => 'say $v',
            ),
          ),
          SkyTile(
            icon: Icons.people_outline,
            title: l10n.toolListPlayers,
            subtitle: l10n.toolListPlayersHint,
            accent: AetherColors.cyan,
            onTap: () => onRun('list'),
          ),
          SkyTile(
            icon: Icons.terminal_rounded,
            title: l10n.serverOpenTerminal,
            subtitle: l10n.serverOpenTerminalHint,
            accent: AetherColors.gold,
            onTap: onJumpTerminal,
          ),
        ]),
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
