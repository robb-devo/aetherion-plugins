import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../crafty/crafty_client.dart';
import '../crafty/crafty_config.dart';
import '../data/account_store.dart';
import '../data/console_line.dart';
import '../data/operator_account.dart';
import '../data/pin.dart';
import '../data/server_snapshot.dart';

class OperatorSession extends ChangeNotifier {
  OperatorSession({
    AccountPersistence? persistence,
    CraftyClient? crafty,
    this.now,
  }) : persistence = persistence ?? SharedPrefsAccountPersistence(),
       crafty = crafty ?? createCraftyClient(CraftyConfig.fromEnvironment());

  final AccountPersistence persistence;
  final CraftyClient crafty;
  final DateTime Function()? now;

  DateTime _now() => now?.call() ?? DateTime.now();

  List<OperatorAccount> accounts = [];
  OperatorAccount? current;
  LocaleOption locale = LocaleOption.en;

  NetworkSnapshot? network;
  bool loadingNetwork = false;
  String? networkError;

  String selectedServerId = 'hub';
  final List<ConsoleLine> console = [];
  final List<WhitelistNote> whitelistNotes = [];
  bool sendingCommand = false;

  Future<void> bootstrap() async {
    accounts = await persistence.load();
    final seed = OperatorAccount.seedOperator();
    if (accounts.isEmpty) {
      accounts = [seed];
      await persistence.save(accounts);
    } else {
      final i = accounts.indexWhere((a) => a.id == seed.id);
      if (i >= 0 && !accounts[i].hasPin) {
        accounts = [...accounts]..[i] = seed;
        await persistence.save(accounts);
      }
    }
    await _loadLocale();
    notifyListeners();
  }

  Future<void> _loadLocale() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final code = prefs.getString('aetherion.operator.locale');
      if (code == 'de') locale = LocaleOption.de;
    } catch (_) {
      // Tests without plugin binding keep EN.
    }
  }

  Future<void> setLocale(LocaleOption next) async {
    locale = next;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('aetherion.operator.locale', next.code);
    } catch (_) {}
  }

  Future<String?> addAccount({required String name, String? pin}) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return 'empty';
    if (!OperatorAccount.namePattern.hasMatch(trimmed)) return 'invalid';
    if (accounts.any((a) => a.name.toLowerCase() == trimmed.toLowerCase())) {
      return 'taken';
    }
    accounts = [
      ...accounts,
      OperatorAccount(
        id: 'acc-${_now().microsecondsSinceEpoch}',
        name: trimmed,
        pinHash: (pin == null || pin.isEmpty) ? null : hashPin(pin),
        createdAt: _now(),
      ),
    ];
    await persistence.save(accounts);
    notifyListeners();
    return null;
  }

  Future<void> removeAccount(String id) async {
    accounts = accounts.where((a) => a.id != id).toList();
    if (current?.id == id) current = null;
    await persistence.save(accounts);
    notifyListeners();
  }

  String? signIn(OperatorAccount account, {String? pin}) {
    if (account.hasPin) {
      if (pin == null || !account.checkPin(pin)) {
        return 'pin';
      }
    }
    current = account;
    notifyListeners();
    refreshNetwork();
    return null;
  }

  void signOut() {
    current = null;
    notifyListeners();
  }

  Future<void> refreshNetwork() async {
    loadingNetwork = true;
    networkError = null;
    notifyListeners();
    try {
      network = await crafty.fetchNetwork();
      if (network!.servers.isNotEmpty &&
          !network!.servers.any((s) => s.id == selectedServerId)) {
        selectedServerId = network!.servers.first.id;
      }
    } catch (e) {
      networkError = e.toString();
    } finally {
      loadingNetwork = false;
      notifyListeners();
    }
  }

  void selectServer(String id) {
    selectedServerId = id;
    notifyListeners();
  }

  ServerSnapshot? get selectedServer {
    final servers = network?.servers ?? const <ServerSnapshot>[];
    for (final s in servers) {
      if (s.id == selectedServerId) return s;
    }
    return servers.isEmpty ? null : servers.first;
  }

  Future<void> submitCommand(String command) async {
    final trimmed = command.trim();
    if (trimmed.isEmpty) return;
    sendingCommand = true;
    console.add(
      ConsoleLine(
        kind: ConsoleKind.command,
        text: trimmed,
        at: _now(),
        serverId: selectedServerId,
      ),
    );
    notifyListeners();
    try {
      final result = await crafty.sendCommand(
        serverId: selectedServerId,
        command: trimmed,
      );
      console.add(
        ConsoleLine(
          kind: result.ok ? ConsoleKind.response : ConsoleKind.error,
          text: result.message,
          at: _now(),
          serverId: selectedServerId,
        ),
      );
    } catch (e) {
      console.add(
        ConsoleLine(
          kind: ConsoleKind.error,
          text: e.toString(),
          at: _now(),
          serverId: selectedServerId,
        ),
      );
    } finally {
      sendingCommand = false;
      notifyListeners();
    }
  }

  Future<void> queueSoftRestart() async {
    final target = selectedServerId;
    console.add(
      ConsoleLine(
        kind: ConsoleKind.system,
        text: 'soft restart → $target',
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
    final result = await crafty.softRestart(serverId: target);
    console.add(
      ConsoleLine(
        kind: result.ok ? ConsoleKind.response : ConsoleKind.error,
        text: result.message,
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
  }

  void addWhitelistNote(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return;
    whitelistNotes.add(
      WhitelistNote(
        text: trimmed,
        at: _now(),
        author: current?.name ?? 'operator',
      ),
    );
    console.add(
      ConsoleLine(
        kind: ConsoleKind.system,
        text: 'whitelist note: $trimmed',
        at: _now(),
        serverId: selectedServerId,
      ),
    );
    notifyListeners();
  }
}

enum LocaleOption { en, de }

extension LocaleOptionX on LocaleOption {
  String get code => this == LocaleOption.de ? 'de' : 'en';
}
