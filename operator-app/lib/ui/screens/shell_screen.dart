import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';
import '../widgets/aether_backdrop.dart';
import '../widgets/chrome.dart';
import '../widgets/glass_card.dart';
import 'dashboard_screen.dart';
import 'devkit_screen.dart';
import 'links_screen.dart';
import 'settings_screen.dart';
import 'team_screen.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key});

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final destinations = [
      (Icons.dashboard_outlined, Icons.dashboard, l10n.navDashboard),
      (Icons.terminal_outlined, Icons.terminal, l10n.navDevkit),
      (Icons.link_outlined, Icons.link, l10n.navLinks),
      (Icons.group_outlined, Icons.group, l10n.navTeam),
      (Icons.settings_outlined, Icons.settings, l10n.navSettings),
    ];
    final pages = const [
      DashboardScreen(),
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
              _TopBar(name: session.current?.name ?? 'Operator'),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 280),
                  switchInCurve: Curves.easeOutCubic,
                  switchOutCurve: Curves.easeInCubic,
                  transitionBuilder: (child, anim) =>
                      FadeSlide(animation: anim, child: child),
                  child: KeyedSubtree(
                    key: ValueKey(_index),
                    child: SizedBox.expand(child: pages[_index]),
                  ),
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
              NavigationBar(
                backgroundColor: AetherColors.ink.withValues(alpha: 0.92),
                indicatorColor: AetherColors.cyan.withValues(alpha: 0.18),
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
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        children: [
          const AetherMark(size: 28),
          const SizedBox(width: 10),
          Text(
            l10n.networkName.toUpperCase(),
            style: const TextStyle(
              fontFamily: AetherTheme.cinzel,
              fontWeight: FontWeight.w800,
              letterSpacing: 2.2,
              fontSize: 16,
            ),
          ),
          const SizedBox(width: 10),
          Flexible(
            child: Text(
              l10n.signedInAs(name),
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: AetherColors.mist, fontSize: 12),
            ),
          ),
          const LanguageToggle(),
          const SizedBox(width: 8),
          TextButton(
            key: const Key('sign-out'),
            onPressed: () => SessionScope.of(context).signOut(),
            child: Text(l10n.signOut),
          ),
        ],
      ),
    );
  }
}
