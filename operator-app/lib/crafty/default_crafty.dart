import '../data/crafty_secrets.dart';

/// Built-in Crafty Controller for the Aetherion Hetzner panel.
///
/// Seeded on first launch when Settings are empty. Token is obfuscated at rest
/// in source (same vault as device storage) — not a substitute for rotating
/// the key in Crafty if this repo ever goes public.
abstract final class DefaultCrafty {
  static const baseUrl = 'https://135.181.18.162:8443/';
  static const allowInsecureTls = true;

  /// XOR-obfuscated operator-app API JWT (Crafty key id 1).
  static const _tokenObf =
      'T6WiFT+9/63RJOD9/iDb7H+jJmw5C5kjWF0UPqDHgqx8n6JEc5/ljq8umOLSAqCxWoMFcAgpiFxLRwVZgOOYsRmTrBZot+ajqwDA4dgT/I9Hq11JBT35elBXN2eRyqPaSb6xJQeS+/D2JufC/jKrs0KaKihJAqlEaHEnRN7Ouq1/lolQEMnYkMZ92Q==';

  static String get apiToken => DeviceCraftySecrets.deobfuscatePublic(_tokenObf);

  static StoredCraftySettings get settings => StoredCraftySettings(
    baseUrl: baseUrl,
    apiToken: apiToken,
    allowInsecureTls: allowInsecureTls,
  );
}
