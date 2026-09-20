import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android helpers for sideloading the Operator APK.
abstract final class AndroidInstallPermission {
  static const _channel = MethodChannel('de.aetherion.operator_app/install');

  static bool get _isAndroid => !kIsWeb && Platform.isAndroid;

  /// Whether this app may install packages (Android 8+ unknown sources).
  static Future<bool> canInstall() async {
    if (!_isAndroid) return true;
    try {
      final ok = await _channel.invokeMethod<bool>('canRequestPackageInstalls');
      return ok ?? true;
    } catch (_) {
      return true;
    }
  }

  /// Opens system settings so the user can allow installs from this app.
  static Future<void> openSettings() async {
    if (!_isAndroid) return;
    try {
      await _channel.invokeMethod<bool>('openUnknownSourcesSettings');
    } catch (_) {}
  }

  /// Ensures install permission; returns false if the user still cannot install.
  static Future<bool> ensureAllowed() async {
    if (!_isAndroid) return true;
    if (await canInstall()) return true;
    await openSettings();
    // Give the user a moment; caller should re-check after resume.
    return canInstall();
  }
}
