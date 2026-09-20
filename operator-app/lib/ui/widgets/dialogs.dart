import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/operator_account.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/glass_card.dart';

Future<void> showAddAccountDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (_) => const _AddAccountDialog(),
  );
}

class _AddAccountDialog extends StatefulWidget {
  const _AddAccountDialog();

  @override
  State<_AddAccountDialog> createState() => _AddAccountDialogState();
}

class _AddAccountDialogState extends State<_AddAccountDialog> {
  final name = TextEditingController();
  final pin = TextEditingController();
  String? error;

  @override
  void dispose() {
    name.dispose();
    pin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.addAccountTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            key: const Key('add-account-name'),
            controller: name,
            autofocus: true,
            inputFormatters: [
              FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9_]')),
              LengthLimitingTextInputFormatter(16),
            ],
            decoration: InputDecoration(hintText: l10n.accountNameHint),
          ),
          const SizedBox(height: 10),
          TextField(
            key: const Key('add-account-pin'),
            controller: pin,
            obscureText: true,
            decoration: InputDecoration(hintText: l10n.optionalPinHint),
          ),
          if (error != null) ...[
            const SizedBox(height: 10),
            Text(error!, style: const TextStyle(color: AetherColors.offline)),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          key: const Key('add-account-save'),
          onPressed: () async {
            final session = SessionScope.of(context);
            final result = await session.addAccount(
              name: name.text,
              pin: pin.text,
            );
            if (!context.mounted) return;
            if (result == null) {
              Navigator.pop(context);
              return;
            }
            setState(() {
              error = switch (result) {
                'empty' => l10n.nameRequired,
                'taken' => l10n.nameTaken,
                _ => l10n.nameInvalid,
              };
            });
          },
          child: Text(l10n.save),
        ),
      ],
    );
  }
}

Future<String?> showPinDialog(
  BuildContext context,
  OperatorAccount account,
) {
  return showDialog<String>(
    context: context,
    builder: (_) => _PinDialog(account: account),
  );
}

class _PinDialog extends StatefulWidget {
  const _PinDialog({required this.account});

  final OperatorAccount account;

  @override
  State<_PinDialog> createState() => _PinDialogState();
}

class _PinDialogState extends State<_PinDialog> {
  final pin = TextEditingController();
  String? error;

  @override
  void dispose() {
    pin.dispose();
    super.dispose();
  }

  void _accept() {
    if (widget.account.checkPin(pin.text)) {
      Navigator.pop(context, pin.text);
    } else {
      setState(() => error = AppLocalizations.of(context).pinWrong);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.enterPin(widget.account.name)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            key: const Key('pin-input'),
            controller: pin,
            obscureText: true,
            autofocus: true,
            onSubmitted: (_) => _accept(),
            decoration: InputDecoration(hintText: l10n.pinHint),
          ),
          if (error != null) ...[
            const SizedBox(height: 10),
            Text(error!, style: const TextStyle(color: AetherColors.offline)),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          key: const Key('pin-continue'),
          onPressed: _accept,
          child: Text(l10n.continueAction),
        ),
      ],
    );
  }
}

Future<String?> showNoteDialog({
  required BuildContext context,
  required String title,
  required String hint,
}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _NoteDialog(title: title, hint: hint),
  );
}

class _NoteDialog extends StatefulWidget {
  const _NoteDialog({required this.title, required this.hint});

  final String title;
  final String hint;

  @override
  State<_NoteDialog> createState() => _NoteDialogState();
}

class _NoteDialogState extends State<_NoteDialog> {
  final text = TextEditingController();

  @override
  void dispose() {
    text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        key: const Key('whitelist-input'),
        controller: text,
        autofocus: true,
        decoration: InputDecoration(hintText: widget.hint),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          key: const Key('whitelist-save'),
          onPressed: () => Navigator.pop(context, text.text),
          child: Text(l10n.save),
        ),
      ],
    );
  }
}

class AccountTile extends StatelessWidget {
  const AccountTile({
    super.key,
    required this.account,
    this.onSignIn,
    this.onRemove,
  });

  final OperatorAccount account;
  final VoidCallback? onSignIn;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return GlassCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: AetherColors.amethyst.withValues(alpha: 0.22),
            foregroundColor: AetherColors.amethyst,
            child: Text(
              account.name.isEmpty ? '?' : account.name[0].toUpperCase(),
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  account.name,
                  key: Key('account-${account.name}'),
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                  ),
                ),
                Text(
                  account.hasPin ? l10n.pinProtected : l10n.noPin,
                  style: const TextStyle(
                    color: AetherColors.mist,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          if (onRemove != null)
            IconButton(
              key: Key('remove-${account.name}'),
              tooltip: l10n.remove,
              onPressed: onRemove,
              icon: const Icon(Icons.close, size: 18, color: AetherColors.mist),
            ),
          if (onSignIn != null)
            FilledButton(
              key: Key('signin-${account.name}'),
              onPressed: onSignIn,
              child: Text(l10n.signIn),
            ),
        ],
      ),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.title,
    this.body,
    this.trailing,
  });

  final String title;
  final String? body;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontFamily: AetherTheme.cinzel,
                  fontWeight: FontWeight.w700,
                  fontSize: 22,
                  letterSpacing: 1.2,
                ),
              ),
              if (body != null) ...[
                const SizedBox(height: 4),
                Text(
                  body!,
                  style: const TextStyle(
                    color: AetherColors.mist,
                    height: 1.35,
                  ),
                ),
              ],
            ],
          ),
        ),
        ?trailing,
      ],
    );
  }
}
