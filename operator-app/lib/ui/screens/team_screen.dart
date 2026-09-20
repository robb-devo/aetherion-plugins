import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../widgets/dialogs.dart';

class TeamScreen extends StatelessWidget {
  const TeamScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        SectionHeader(title: l10n.teamTitle, body: l10n.teamBody),
        const SizedBox(height: 16),
        if (session.accounts.isEmpty)
          Text(l10n.emptyTeam)
        else
          ...session.accounts.map(
            (account) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: AccountTile(
                account: account,
                onRemove: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: Text(l10n.removeAccount(account.name)),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(ctx, false),
                          child: Text(l10n.cancel),
                        ),
                        FilledButton(
                          onPressed: () => Navigator.pop(ctx, true),
                          child: Text(l10n.remove),
                        ),
                      ],
                    ),
                  );
                  if (ok == true && context.mounted) {
                    await session.removeAccount(account.id);
                  }
                },
              ),
            ),
          ),
        const SizedBox(height: 8),
        FilledButton.icon(
          key: const Key('team-add-account'),
          onPressed: () => showAddAccountDialog(context),
          icon: const Icon(Icons.add),
          label: Text(l10n.addAccount),
        ),
      ],
    );
  }
}
