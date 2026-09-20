import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'crafty_secrets.dart' show DeviceCraftySecrets;

/// Optional GitHub PAT so private-repo Releases are visible to the update checker.
abstract class GithubTokenStore {
  Future<String> load();
  Future<void> save(String token);
}

class MemoryGithubTokenStore implements GithubTokenStore {
  MemoryGithubTokenStore([this._token = '']);

  String _token;

  @override
  Future<String> load() async => _token;

  @override
  Future<void> save(String token) async => _token = token.trim();
}

class DeviceGithubTokenStore implements GithubTokenStore {
  DeviceGithubTokenStore({
    FlutterSecureStorage? secure,
    this.prefs,
  }) : _secure = secure ?? const FlutterSecureStorage();

  final FlutterSecureStorage _secure;
  SharedPreferences? prefs;

  static const _secureKey = 'aetherion.operator.github.token';
  static const _prefsKey = 'aetherion.operator.github.token.obf';

  Future<SharedPreferences> _prefs() async {
    return prefs ??= await SharedPreferences.getInstance();
  }

  @override
  Future<String> load() async {
    try {
      final secure = await _secure.read(key: _secureKey);
      if (secure != null && secure.isNotEmpty) return secure;
    } catch (_) {}
    try {
      final raw = (await _prefs()).getString(_prefsKey) ?? '';
      return DeviceCraftySecrets.deobfuscatePublic(raw);
    } catch (_) {
      return '';
    }
  }

  @override
  Future<void> save(String token) async {
    final trimmed = token.trim();
    var storedSecure = false;
    if (trimmed.isNotEmpty) {
      try {
        await _secure.write(key: _secureKey, value: trimmed);
        storedSecure = true;
      } catch (_) {}
    } else {
      try {
        await _secure.delete(key: _secureKey);
      } catch (_) {}
    }
    try {
      final p = await _prefs();
      if (storedSecure || trimmed.isEmpty) {
        await p.remove(_prefsKey);
      } else {
        await p.setString(
          _prefsKey,
          DeviceCraftySecrets.obfuscatePublic(trimmed),
        );
      }
    } catch (_) {}
  }
}
