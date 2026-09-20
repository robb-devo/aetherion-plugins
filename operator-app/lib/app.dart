import 'package:flutter/material.dart';

import 'l10n/app_localizations.dart';
import 'state/operator_session.dart';
import 'state/session_scope.dart';
import 'theme/aether_theme.dart';
import 'ui/screens/login_screen.dart';
import 'ui/screens/shell_screen.dart';
import 'ui/widgets/glass_card.dart';

class OperatorApp extends StatelessWidget {
  const OperatorApp({super.key, required this.session});

  final OperatorSession session;

  @override
  Widget build(BuildContext context) {
    return SessionScope(
      session: session,
      child: AnimatedBuilder(
        animation: session,
        builder: (context, _) {
          return MaterialApp(
            title: 'Aetherion Operator',
            debugShowCheckedModeBanner: false,
            theme: AetherTheme.dark(),
            locale: Locale(session.locale.code),
            supportedLocales: AppLocalizations.supportedLocales,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            home: AnimatedSwitcher(
              duration: const Duration(milliseconds: 420),
              switchInCurve: Curves.easeOutCubic,
              switchOutCurve: Curves.easeInCubic,
              transitionBuilder: (child, anim) =>
                  FadeSlide(animation: anim, child: child),
              child: session.current == null
                  ? const LoginScreen(key: ValueKey('login'))
                  : const ShellScreen(key: ValueKey('shell')),
            ),
          );
        },
      ),
    );
  }
}
