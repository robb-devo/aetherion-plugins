/// Runtime Crafty wiring. Tokens come from Settings, `--dart-define`, or env
/// — never source.
class CraftyConfig {
  const CraftyConfig({
    required this.enabled,
    this.baseUrl,
    this.apiToken,
    this.allowInsecureTls = true,
  });

  final bool enabled;
  final Uri? baseUrl;
  final String? apiToken;
  final bool allowInsecureTls;

  bool get isConfigured =>
      enabled &&
      baseUrl != null &&
      apiToken != null &&
      apiToken!.trim().isNotEmpty;

  /// Compile-time defines (used when Settings are empty):
  /// `--dart-define=CRAFTY_ENABLED=true`
  /// `--dart-define=CRAFTY_BASE_URL=https://crafty.example:8443`
  /// `--dart-define=CRAFTY_API_TOKEN=...`
  factory CraftyConfig.fromEnvironment() {
    const enabled = bool.fromEnvironment('CRAFTY_ENABLED');
    const rawUrl = String.fromEnvironment('CRAFTY_BASE_URL');
    const token = String.fromEnvironment('CRAFTY_API_TOKEN');
    const insecure = bool.fromEnvironment(
      'CRAFTY_ALLOW_INSECURE_TLS',
      defaultValue: true,
    );
    return CraftyConfig(
      enabled: enabled || (rawUrl.isNotEmpty && token.isNotEmpty),
      baseUrl: rawUrl.isEmpty ? null : Uri.tryParse(rawUrl),
      apiToken: token.isEmpty ? null : token,
      allowInsecureTls: insecure,
    );
  }

  static const unset = CraftyConfig(enabled: false);
}

class CraftyNotConfiguredException implements Exception {
  @override
  String toString() =>
      'Crafty is not configured. Add the Controller URL and API token in Settings.';
}
