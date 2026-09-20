import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../crafty/crafty_config.dart';

class StoredCraftySettings {
  const StoredCraftySettings({
    this.baseUrl = '',
    this.apiToken = '',
    this.allowInsecureTls = true,
  });

  final String baseUrl;
  final String apiToken;
  final bool allowInsecureTls;

  bool get isConfigured =>
      baseUrl.trim().isNotEmpty && apiToken.trim().isNotEmpty;

  CraftyConfig toConfig() {
    final raw = baseUrl.trim();
    return CraftyConfig(
      enabled: isConfigured,
      baseUrl: raw.isEmpty ? null : Uri.tryParse(_withSlash(raw)),
      apiToken: apiToken.trim().isEmpty ? null : apiToken.trim(),
      allowInsecureTls: allowInsecureTls,
    );
  }

  static String _withSlash(String url) {
    return url.endsWith('/') ? url : '$url/';
  }
}

abstract class CraftySecrets {
  Future<StoredCraftySettings> load();

  Future<void> save({
    required String baseUrl,
    required String apiToken,
    required bool allowInsecureTls,
  });

  Future<void> clearToken();
}

class MemoryCraftySecrets implements CraftySecrets {
  MemoryCraftySecrets([this._settings = const StoredCraftySettings()]);

  StoredCraftySettings _settings;

  @override
  Future<StoredCraftySettings> load() async => _settings;

  @override
  Future<void> save({
    required String baseUrl,
    required String apiToken,
    required bool allowInsecureTls,
  }) async {
    _settings = StoredCraftySettings(
      baseUrl: baseUrl,
      apiToken: apiToken,
      allowInsecureTls: allowInsecureTls,
    );
  }

  @override
  Future<void> clearToken() async {
    _settings = StoredCraftySettings(
      baseUrl: _settings.baseUrl,
      allowInsecureTls: _settings.allowInsecureTls,
    );
  }
}

/// Token prefers platform secure storage; URL lives in prefs.
/// If Keystore/Credential Manager is unavailable, token is XOR-obfuscated in prefs.
class DeviceCraftySecrets implements CraftySecrets {
  DeviceCraftySecrets({
    FlutterSecureStorage? secure,
    this.prefs,
  }) : _secure = secure ?? const FlutterSecureStorage();

  final FlutterSecureStorage _secure;
  SharedPreferences? prefs;

  static const _urlKey = 'aetherion.operator.crafty.baseUrl';
  static const _tlsKey = 'aetherion.operator.crafty.insecureTls';
  static const _tokenPrefsKey = 'aetherion.operator.crafty.token.obf';
  static const _tokenSecureKey = 'aetherion.operator.crafty.token';

  Future<SharedPreferences> _prefs() async {
    return prefs ??= await SharedPreferences.getInstance();
  }

  @override
  Future<StoredCraftySettings> load() async {
    String url = '';
    var tls = true;
    try {
      final p = await _prefs();
      url = p.getString(_urlKey) ?? '';
      tls = p.getBool(_tlsKey) ?? true;
    } catch (_) {}

    var token = '';
    try {
      token = await _secure.read(key: _tokenSecureKey) ?? '';
    } catch (_) {}
    if (token.isEmpty) {
      try {
        token = _deobfuscate((await _prefs()).getString(_tokenPrefsKey) ?? '');
      } catch (_) {}
    }
    return StoredCraftySettings(
      baseUrl: url,
      apiToken: token,
      allowInsecureTls: tls,
    );
  }

  @override
  Future<void> save({
    required String baseUrl,
    required String apiToken,
    required bool allowInsecureTls,
  }) async {
    try {
      final p = await _prefs();
      await p.setString(_urlKey, baseUrl.trim());
      await p.setBool(_tlsKey, allowInsecureTls);
    } catch (_) {}

    var storedSecure = false;
    if (apiToken.trim().isNotEmpty) {
      try {
        await _secure.write(key: _tokenSecureKey, value: apiToken.trim());
        storedSecure = true;
      } catch (_) {}
    }
    try {
      final p = await _prefs();
      if (storedSecure || apiToken.trim().isEmpty) {
        await p.remove(_tokenPrefsKey);
      } else {
        await p.setString(_tokenPrefsKey, _obfuscate(apiToken.trim()));
      }
    } catch (_) {}
  }

  @override
  Future<void> clearToken() async {
    try {
      await _secure.delete(key: _tokenSecureKey);
    } catch (_) {}
    try {
      await (await _prefs()).remove(_tokenPrefsKey);
    } catch (_) {}
  }

  static final _obfKey = sha256.convert(utf8.encode('aetherion-operator-vault-v1')).bytes;

  static String obfuscatePublic(String value) => _obfuscate(value);

  static String deobfuscatePublic(String raw) => _deobfuscate(raw);

  static String _obfuscate(String value) {
    if (value.isEmpty) return '';
    final data = utf8.encode(value);
    final out = List<int>.generate(
      data.length,
      (i) => data[i] ^ _obfKey[i % _obfKey.length],
    );
    return base64Encode(out);
  }

  static String _deobfuscate(String raw) {
    if (raw.isEmpty) return '';
    try {
      final data = base64Decode(raw);
      final out = List<int>.generate(
        data.length,
        (i) => data[i] ^ _obfKey[i % _obfKey.length],
      );
      return utf8.decode(out);
    } catch (_) {
      return '';
    }
  }
}
