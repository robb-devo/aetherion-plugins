import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import 'operator_account.dart';

abstract class AccountPersistence {
  Future<List<OperatorAccount>> load();
  Future<void> save(List<OperatorAccount> accounts);
}

class MemoryAccountPersistence implements AccountPersistence {
  MemoryAccountPersistence({List<OperatorAccount>? seed})
    : _accounts = List.of(seed ?? const []);

  final List<OperatorAccount> _accounts;

  @override
  Future<List<OperatorAccount>> load() async => List.of(_accounts);

  @override
  Future<void> save(List<OperatorAccount> accounts) async {
    _accounts
      ..clear()
      ..addAll(accounts);
  }
}

class SharedPrefsAccountPersistence implements AccountPersistence {
  SharedPrefsAccountPersistence({this.prefs});

  SharedPreferences? prefs;
  static const _key = 'aetherion.operator.accounts.v1';

  Future<SharedPreferences> _prefs() async {
    return prefs ??= await SharedPreferences.getInstance();
  }

  @override
  Future<List<OperatorAccount>> load() async {
    final raw = (await _prefs()).getString(_key);
    if (raw == null || raw.isEmpty) return [];
    final list = jsonDecode(raw) as List<dynamic>;
    return list
        .map((e) => OperatorAccount.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<void> save(List<OperatorAccount> accounts) async {
    final encoded = jsonEncode(accounts.map((a) => a.toJson()).toList());
    await (await _prefs()).setString(_key, encoded);
  }
}
