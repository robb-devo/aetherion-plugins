import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../l10n/app_localizations.dart';
import '../platform/platform_info.dart';
import 'in_app_updater.dart';
import 'update_checker.dart';

/// Prefer in-app download + install. Falls back to opening the artifact URL.
Future<void> applyOperatorUpdate(
  BuildContext context,
  AppRelease release,
) async {
  final l10n = AppLocalizations.of(context);
  final messenger = ScaffoldMessenger.maybeOf(context);
  final progress = ValueNotifier<UpdateProgress>(
    const UpdateProgress(fraction: 0, label: 'download'),
  );

  showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) {
      return PopScope(
        canPop: false,
        child: AlertDialog(
          title: Text(l10n.updateDownloading),
          content: ValueListenableBuilder<UpdateProgress>(
            valueListenable: progress,
            builder: (context, value, _) {
              final indeterminate = value.fraction < 0;
              final label = value.label == 'install'
                  ? l10n.updateInstalling
                  : l10n.updateDownloading;
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(label),
                  const SizedBox(height: 14),
                  if (indeterminate)
                    const LinearProgressIndicator()
                  else
                    LinearProgressIndicator(value: value.fraction),
                  if (!indeterminate) ...[
                    const SizedBox(height: 8),
                    Text(
                      '${(value.fraction * 100).clamp(0, 100).toStringAsFixed(0)}%',
                      textAlign: TextAlign.end,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ],
                ],
              );
            },
          ),
        ),
      );
    },
  );

  try {
    await InAppUpdater().apply(
      release,
      onProgress: (p) => progress.value = p,
    );
    if (context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
    }
    // Windows exits the process after staging; Android opens the installer.
  } catch (e) {
    if (context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
    }
    // Last resort: open the direct download URL (donnernet, not GitHub).
    final fallback = _fallbackUrl(release);
    if (fallback != null) {
      try {
        await launchUrl(Uri.parse(fallback), mode: LaunchMode.externalApplication);
        return;
      } catch (_) {}
    }
    messenger?.showSnackBar(
      SnackBar(content: Text(l10n.updateFailed('$e'))),
    );
  } finally {
    progress.dispose();
  }
}

String? _fallbackUrl(AppRelease release) {
  if (isWindowsDesktop) {
    final win = release.windowsUrl;
    if (win != null && win.startsWith('http')) return win;
  }
  final apk = release.apkUrl;
  if (apk != null && apk.startsWith('http')) return apk;
  return release.htmlUrl.startsWith('http') ? release.htmlUrl : null;
}

/// @Deprecated — use [applyOperatorUpdate].
Future<void> openOperatorRelease(AppRelease release) async {
  final fallback = _fallbackUrl(release);
  if (fallback == null) return;
  await launchUrl(Uri.parse(fallback), mode: LaunchMode.externalApplication);
}
