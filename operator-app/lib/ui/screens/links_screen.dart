import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/network_links.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/aether_colors.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';

class LinksScreen extends StatelessWidget {
  const LinksScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        SectionHeader(title: l10n.linksTitle, body: l10n.linksBody),
        const SizedBox(height: 16),
        _LinkTile(
          title: l10n.playAddress,
          hint: l10n.playAddressHint,
          accent: AetherColors.cyan,
          copyValue: NetworkLinks.playHost,
          copyLabel: l10n.copyAddress,
        ),
        const SizedBox(height: 10),
        _LinkTile(
          title: l10n.siteLabel,
          hint: l10n.siteHint,
          accent: AetherColors.amethyst,
          url: NetworkLinks.siteUrl,
          openLabel: l10n.openLink,
        ),
        const SizedBox(height: 10),
        _LinkTile(
          title: l10n.discordLabel,
          hint: l10n.discordHint,
          accent: AetherColors.gold,
          url: NetworkLinks.discordInvite,
          openLabel: l10n.openLink,
        ),
      ],
    );
  }
}

class _LinkTile extends StatelessWidget {
  const _LinkTile({
    required this.title,
    required this.hint,
    required this.accent,
    this.copyValue,
    this.copyLabel,
    this.url,
    this.openLabel,
  });

  final String title;
  final String hint;
  final Color accent;
  final String? copyValue;
  final String? copyLabel;
  final String? url;
  final String? openLabel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return GlassCard(
      accent: accent,
      child: Row(
        children: [
          Container(
            width: 10,
            height: 42,
            decoration: BoxDecoration(
              color: accent,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                  ),
                ),
                Text(
                  hint,
                  style: const TextStyle(
                    color: AetherColors.mist,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          if (copyValue != null)
            OutlinedButton(
              key: const Key('copy-play-host'),
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: copyValue!));
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.copied(copyValue!))),
                  );
                }
              },
              child: Text(copyLabel ?? l10n.copyAddress),
            ),
          if (url != null) ...[
            const SizedBox(width: 8),
            FilledButton(
              onPressed: () => launchUrl(
                Uri.parse(url!),
                mode: LaunchMode.externalApplication,
              ),
              child: Text(openLabel ?? l10n.openLink),
            ),
          ],
        ],
      ),
    );
  }
}
