import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/aether_backdrop.dart';
import '../widgets/chrome.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';

class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    return AetherBackdrop(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 36, 24, 32),
              children: [
                Row(
                  children: [
                    const AetherMark(size: 42),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.networkName.toUpperCase(),
                            style: const TextStyle(
                              fontFamily: AetherTheme.cinzel,
                              fontWeight: FontWeight.w900,
                              fontSize: 28,
                              letterSpacing: 3.2,
                              height: 1.05,
                            ),
                          ),
                          Text(
                            l10n.appTagline,
                            style: const TextStyle(
                              color: AetherColors.mist,
                              fontSize: 13,
                              letterSpacing: 0.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const LanguageToggle(),
                  ],
                ),
                const SizedBox(height: 36),
                Text(
                  l10n.loginHeadline,
                  style: const TextStyle(
                    fontFamily: AetherTheme.cinzel,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.1,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  l10n.loginBody,
                  style: const TextStyle(color: AetherColors.mist, height: 1.4),
                ),
                const SizedBox(height: 22),
                GlassCard(
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                  child: Column(
                    children: [
                if (session.accounts.isEmpty)
                  Text(
                    l10n.emptyTeam,
                    style: const TextStyle(color: AetherColors.mist),
                  ),
                ...session.accounts.map(
                  (account) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: AccountTile(
                      account: account,
                      onSignIn: () async {
                        String? pin;
                        if (account.hasPin) {
                          pin = await showPinDialog(context, account);
                          if (pin == null) return;
                        }
                        await session.signIn(account, pin: pin);
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 4),
                OutlinedButton.icon(
                  key: const Key('add-account'),
                  onPressed: () => showAddAccountDialog(context),
                  icon: const Icon(Icons.add),
                  label: Text(l10n.addAccount),
                ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        ),
      ),
    );
  }
}
