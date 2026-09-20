import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/operator_session.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/aether_backdrop.dart';
import '../widgets/chrome.dart';
import '../widgets/role_chrome.dart';
import 'dashboard_screen.dart';
import 'devkit_screen.dart';
import 'links_screen.dart';
import 'players_screen.dart';
import 'settings_screen.dart';
import 'team_screen.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key});

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> with WidgetsBindingObserver {
  int _index = 0;
  OperatorSession? _session;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _session = SessionScope.of(context);
      _session?.setSoftRefreshEnabled(true);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _session = SessionScope.of(context);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _session?.setSoftRefreshEnabled(false);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final session = _session;
    if (session == null) return;
    switch (state) {
      case AppLifecycleState.resumed:
        session.setSoftRefreshEnabled(true);
      case AppLifecycleState.inactive:
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
      case AppLifecycleState.detached:
        session.setSoftRefreshEnabled(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final destinations = [
      (Icons.dashboard_outlined, Icons.dashboard, l10n.navDashboard),
      (Icons.people_outline, Icons.people, l10n.navPlayers),
      (Icons.terminal_outlined, Icons.terminal, l10n.navDevkit),
      (Icons.link_outlined, Icons.link, l10n.navLinks),
      (Icons.group_outlined, Icons.group, l10n.navTeam),
      (Icons.settings_outlined, Icons.settings, l10n.navSettings),
    ];
    const pages = [
      DashboardScreen(),
      PlayersScreen(),
      DevkitScreen(),
      LinksScreen(),
      TeamScreen(),
      SettingsScreen(),
    ];

    return AetherBackdrop(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: LayoutBuilder(
          builder: (context, constraints) {
            final rail = constraints.maxWidth >= 880;
            final body = Column(
              children: [
                SafeArea(
                  bottom: false,
                  child: _TopBar(name: session.current?.name ?? 'Operator'),
                ),
                if (session.current?.isObserver == true)
                  const ObserverBanner(),
                Expanded(
                  child: IndexedStack(
                    index: _index,
                    children: pages,
                  ),
                ),
              ],
            );

            if (rail) {
              return Row(
                children: [
                  NavigationRail(
                    backgroundColor: Colors.transparent,
                    selectedIndex: _index,
                    onDestinationSelected: (i) => setState(() => _index = i),
                    labelType: NavigationRailLabelType.all,
                    selectedIconTheme: const IconThemeData(
                      color: AetherColors.cyan,
                    ),
                    unselectedIconTheme: const IconThemeData(
                      color: AetherColors.mist,
                    ),
                    selectedLabelTextStyle: const TextStyle(
                      color: AetherColors.cyan,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                    unselectedLabelTextStyle: const TextStyle(
                      color: AetherColors.mist,
                      fontSize: 12,
                    ),
                    destinations: [
                      for (final d in destinations)
                        NavigationRailDestination(
                          icon: Icon(d.$1),
                          selectedIcon: Icon(d.$2),
                          label: Text(d.$3),
                        ),
                    ],
                  ),
                  const VerticalDivider(
                    width: 1,
                    color: AetherColors.glassStroke,
                  ),
                  Expanded(child: body),
                ],
              );
            }

            return Column(
              children: [
                Expanded(child: body),
                SafeArea(
                  top: false,
                  child: NavigationBar(
                    selectedIndex: _index,
                    onDestinationSelected: (i) => setState(() => _index = i),
                    destinations: [
                      for (final d in destinations)
                        NavigationDestination(
                          icon: Icon(d.$1),
                          selectedIcon: Icon(d.$2, color: AetherColors.cyan),
                          label: d.$3,
                        ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return DecoratedBox(
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: AetherColors.glassStroke, width: 1),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
        child: Row(
          children: [
            const AetherMark(size: 26),
            const SizedBox(width: 10),
            Text(
              l10n.networkName.toUpperCase(),
              style: const TextStyle(
                fontFamily: AetherTheme.cinzel,
                fontWeight: FontWeight.w800,
                letterSpacing: 2.4,
                fontSize: 15,
                height: 1.1,
              ),
            ),
            const SizedBox(width: 10),
            Flexible(
              child: Text(
                l10n.signedInAs(name),
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AetherColors.mist,
                  fontSize: 11.5,
                  letterSpacing: 0.2,
                ),
              ),
            ),
            const LanguageToggle(),
            const SizedBox(width: 4),
            TextButton(
              key: const Key('sign-out'),
              onPressed: () => SessionScope.of(context).signOut(),
              child: Text(l10n.signOut),
            ),
          ],
        ),
      ),
    );
  }
}
