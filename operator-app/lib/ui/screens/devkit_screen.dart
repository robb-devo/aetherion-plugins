import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../data/console_line.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';

class DevkitScreen extends StatefulWidget {
  const DevkitScreen({super.key});

  @override
  State<DevkitScreen> createState() => _DevkitScreenState();
}

class _DevkitScreenState extends State<DevkitScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final text = _input.text;
    if (text.trim().isEmpty) return;
    _input.clear();
    await SessionScope.of(context).submitCommand(text);
    await Future<void>.delayed(const Duration(milliseconds: 30));
    if (_scroll.hasClients) {
      await _scroll.animateTo(
        _scroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final servers = session.network?.servers ?? const [];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
          child: SectionHeader(title: l10n.devkitTitle, body: l10n.devkitBody),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
          child: Row(
            children: [
              Text(
                l10n.targetServer,
                style: const TextStyle(
                  color: AetherColors.mist,
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: InputDecorator(
                  decoration: const InputDecoration(
                    isDense: true,
                    contentPadding: EdgeInsets.symmetric(horizontal: 12),
                  ),
                  child: Material(
                    color: Colors.transparent,
                    child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      key: const Key('server-select'),
                      isExpanded: true,
                      value: servers.any((s) => s.id == session.selectedServerId)
                          ? session.selectedServerId
                          : null,
                      items: [
                        for (final s in servers)
                          DropdownMenuItem(
                            value: s.id,
                            child: Text(s.displayName),
                          ),
                      ],
                      onChanged: (id) {
                        if (id != null) session.selectServer(id);
                      },
                    ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.tonalIcon(
                key: const Key('soft-restart'),
                onPressed: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: Text(l10n.softRestart),
                      content: Text(
                        l10n.softRestartConfirm(
                          session.selectedServer?.displayName ??
                              session.selectedServerId,
                        ),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(ctx, false),
                          child: Text(l10n.cancel),
                        ),
                        FilledButton(
                          key: const Key('soft-restart-confirm'),
                          onPressed: () => Navigator.pop(ctx, true),
                          child: Text(l10n.confirm),
                        ),
                      ],
                    ),
                  );
                  if (ok == true && context.mounted) {
                    await session.queueSoftRestart();
                  }
                },
                icon: const Icon(Icons.restart_alt, size: 18),
                label: Text(l10n.softRestart),
              ),
              OutlinedButton.icon(
                key: const Key('whitelist-note'),
                onPressed: () => _whitelistNote(context),
                icon: const Icon(Icons.note_add_outlined, size: 18),
                label: Text(l10n.whitelistNote),
              ),
            ],
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
            child: GlassCard(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
              child: session.console.isEmpty
                  ? Center(
                      child: Text(
                        l10n.consoleEmpty,
                        style: const TextStyle(color: AetherColors.mist),
                      ),
                    )
                  : ListView.builder(
                      controller: _scroll,
                      itemCount: session.console.length,
                      itemBuilder: (context, i) {
                        final line = session.console[i];
                        return _ConsoleRow(line: line);
                      },
                    ),
            ),
          ),
        ),
        if (session.whitelistNotes.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                session.whitelistNotes
                    .map((n) => '• ${n.author}: ${n.text}')
                    .join('\n'),
                style: const TextStyle(
                  color: AetherColors.mist,
                  fontSize: 12,
                  height: 1.4,
                ),
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  key: const Key('console-input'),
                  controller: _input,
                  onSubmitted: (_) => _send(),
                  decoration: InputDecoration(hintText: l10n.consoleHint),
                ),
              ),
              const SizedBox(width: 10),
              FilledButton(
                key: const Key('console-send'),
                onPressed: session.sendingCommand ? null : _send,
                child: Text(l10n.send),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _whitelistNote(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final note = await showNoteDialog(
      context: context,
      title: l10n.whitelistNote,
      hint: l10n.whitelistNoteHint,
    );
    if (note != null && note.trim().isNotEmpty && context.mounted) {
      SessionScope.of(context).addWhitelistNote(note);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.whitelistSaved)));
    }
  }
}

class _ConsoleRow extends StatelessWidget {
  const _ConsoleRow({required this.line});

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
