/// Runtime Crafty wiring. Tokens come from `--dart-define` or env — never source.
class CraftyConfig {
  const CraftyConfig({
    required this.enabled,
    this.baseUrl,
    this.apiToken,
  });

  final bool enabled;
  final Uri? baseUrl;
  final String? apiToken;

  bool get isConfigured =>
      enabled &&
      baseUrl != null &&
      apiToken != null &&
      apiToken!.trim().isNotEmpty;

  /// Compile-time defines:
  /// `--dart-define=CRAFTY_ENABLED=true`
  /// `--dart-define=CRAFTY_BASE_URL=https://crafty.example`
  /// `--dart-define=CRAFTY_API_TOKEN=...`  (never commit the value)
  factory CraftyConfig.fromEnvironment() {
    const enabled = bool.fromEnvironment('CRAFTY_ENABLED');
    const rawUrl = String.fromEnvironment('CRAFTY_BASE_URL');
    const token = String.fromEnvironment('CRAFTY_API_TOKEN');
    return CraftyConfig(
      enabled: enabled,
      baseUrl: rawUrl.isEmpty ? null : Uri.tryParse(rawUrl),
      apiToken: token.isEmpty ? null : token,
    );
  }

  static const unset = CraftyConfig(enabled: false);
}

class CraftyNotConfiguredException implements Exception {
  @override
  String toString() =>
      'Crafty is not configured. Set CRAFTY_ENABLED, CRAFTY_BASE_URL, and '
      'CRAFTY_API_TOKEN via --dart-define (see operator-app/README.md).';
}
