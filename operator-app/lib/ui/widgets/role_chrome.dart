import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../theme/aether_colors.dart';

void showRoleRestrictedSnack(BuildContext context) {
  final l10n = AppLocalizations.of(context);
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(l10n.roleRestricted)),
  );
}

/// Compact banner for observer (Lime) sessions.
class ObserverBanner extends StatelessWidget {
  const ObserverBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: AetherColors.gold.withValues(alpha: 0.1),
        border: Border.all(color: AetherColors.gold.withValues(alpha: 0.35)),
      ),
      child: Row(
        children: [
          Icon(
            Icons.visibility_outlined,
            size: 18,
            color: AetherColors.gold.withValues(alpha: 0.95),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              l10n.observerBanner,
              style: const TextStyle(
                color: AetherColors.mist,
                fontSize: 12.5,
                height: 1.3,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
