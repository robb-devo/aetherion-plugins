import 'package:flutter/material.dart';

import '../../state/operator_session.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';

class LanguageToggle extends StatelessWidget {
  const LanguageToggle({super.key});

  @override
  Widget build(BuildContext context) {
    final session = SessionScope.of(context);
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AetherColors.glassStroke),
        color: AetherColors.glassFill,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _pill(context, session, LocaleOption.en, 'EN'),
          _pill(context, session, LocaleOption.de, 'DE'),
        ],
      ),
    );
  }

  Widget _pill(
    BuildContext context,
    OperatorSession session,
    LocaleOption option,
    String label,
  ) {
    final on = session.locale == option;
    return GestureDetector(
      onTap: () => session.setLocale(option),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(999),
          color: on ? AetherColors.cyan.withValues(alpha: 0.2) : Colors.transparent,
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: AetherTheme.manrope,
            fontWeight: FontWeight.w700,
            fontSize: 11,
            letterSpacing: 0.6,
            color: on ? AetherColors.cyan : AetherColors.mist,
          ),
        ),
      ),
    );
  }
}

class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.online, required this.onlineLabel, required this.offlineLabel});

  final bool online;
  final String onlineLabel;
  final String offlineLabel;

  @override
  Widget build(BuildContext context) {
    final color = online ? AetherColors.online : AetherColors.offline;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: color.withValues(alpha: 0.14),
        border: Border.all(color: color.withValues(alpha: 0.45)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(color: color.withValues(alpha: 0.7), blurRadius: 6),
              ],
            ),
          ),
          const SizedBox(width: 6),
          Text(
            online ? onlineLabel : offlineLabel,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w700,
              fontSize: 11,
              letterSpacing: 0.3,
            ),
          ),
        ],
      ),
    );
  }
}

class MockBadge extends StatelessWidget {
  const MockBadge({super.key, required this.mock, required this.mockLabel, required this.liveLabel});

  final bool mock;
  final String mockLabel;
  final String liveLabel;

  @override
  Widget build(BuildContext context) {
    final color = mock ? AetherColors.gold : AetherColors.cyan;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: color.withValues(alpha: 0.12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        mock ? mockLabel : liveLabel,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w700,
          fontSize: 11,
          letterSpacing: 0.4,
        ),
      ),
    );
  }
}
