import 'package:flutter/material.dart';

import 'l10n/app_localizations.dart';
import 'state/operator_session.dart';
import 'state/session_scope.dart';
import 'theme/aether_theme.dart';
import 'ui/screens/login_screen.dart';
import 'ui/screens/shell_screen.dart';
import 'ui/widgets/glass_card.dart';
import 'updates/open_release.dart';

class OperatorApp extends StatefulWidget {
  const OperatorApp({super.key, required this.session});

  final OperatorSession session;

  @override
  State<OperatorApp> createState() => _OperatorAppState();
}

class _OperatorAppState extends State<OperatorApp> {
  final _nav = GlobalKey<NavigatorState>();
  var _prompted = false;

  @override
  void initState() {
    super.initState();
    widget.session.addListener(_onSession);
    WidgetsBinding.instance.addPostFrameCallback((_) => _maybePrompt());
  }

  @override
  void dispose() {
    widget.session.removeListener(_onSession);
    super.dispose();
  }

  void _onSession() {
    if (mounted) _maybePrompt();
  }

  Future<void> _maybePrompt() async {
    final release = widget.session.pendingUpdate;
    if (_prompted || release == null) return;
    final nav = _nav.currentContext;
    if (nav == null) return;
    _prompted = true;
    final l10n = AppLocalizations.of(nav);
    final go = await showDialog<bool>(
      context: nav,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.updateAvailable(release.version)),
        content: Text(l10n.updateBody(widget.session.appVersion, release.version)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(l10n.later),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(l10n.updateNow),
          ),
        ],
      ),
    );
    // Keep pendingUpdate so Settings can still install the release.
    widget.session.snoozeUpdatePrompt();
    if (go == true && _nav.currentContext != null) {
      await applyOperatorUpdate(_nav.currentContext!, release);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.session;
    return SessionScope(
      session: session,
      child: AnimatedBuilder(
        animation: session,
        builder: (context, _) {
          return MaterialApp(
            navigatorKey: _nav,
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
