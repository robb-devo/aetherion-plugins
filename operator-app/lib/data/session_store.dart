import 'package:shared_preferences/shared_preferences.dart';

abstract class SessionStore {
  Future<String?> readAccountId();
  Future<void> writeAccountId(String id);
  Future<void> clear();
}

class MemorySessionStore implements SessionStore {
  String? _id;

  @override
  Future<String?> readAccountId() async => _id;

  @override
  Future<void> writeAccountId(String id) async => _id = id;

  @override
  Future<void> clear() async => _id = null;
}

class PrefsSessionStore implements SessionStore {
  PrefsSessionStore({this.prefs});

  SharedPreferences? prefs;
  static const key = 'aetherion.operator.sessionAccountId';

  Future<SharedPreferences> _prefs() async {
    return prefs ??= await SharedPreferences.getInstance();
  }

  @override
  Future<String?> readAccountId() async {
    try {
      return (await _prefs()).getString(key);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> writeAccountId(String id) async {
    try {
      await (await _prefs()).setString(key, id);
    } catch (_) {}
  }

  @override
  Future<void> clear() async {
    try {
      await (await _prefs()).remove(key);
    } catch (_) {}
  }
}
