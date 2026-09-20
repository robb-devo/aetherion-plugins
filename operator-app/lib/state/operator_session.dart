import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app_version.dart';
import '../crafty/crafty_client.dart';
import '../crafty/crafty_config.dart';
import '../data/account_store.dart';
import '../data/console_line.dart';
import '../data/crafty_secrets.dart';
import '../data/github_token_store.dart';
import '../data/operator_account.dart';
import '../data/pin.dart';
import '../data/server_snapshot.dart';
import '../data/session_store.dart';
import '../updates/update_checker.dart';

class OperatorSession extends ChangeNotifier {
  OperatorSession({
    AccountPersistence? persistence,
    SessionStore? sessionStore,
    CraftySecrets? secrets,
    GithubTokenStore? githubTokens,
    CraftyClient? crafty,
    UpdateChecker? updates,
    this.now,
    this.appVersion = kOperatorAppVersion,
  }) : persistence = persistence ?? SharedPrefsAccountPersistence(),
       sessionStore = sessionStore ?? PrefsSessionStore(),
       secrets = secrets ?? DeviceCraftySecrets(),
       githubTokens = githubTokens ?? DeviceGithubTokenStore(),
       updates = updates ?? CascadingUpdateChecker(),
       _lockedCrafty = crafty != null,
       _crafty = crafty ?? createCraftyClient();

  final AccountPersistence persistence;
  final SessionStore sessionStore;
  final CraftySecrets secrets;
  final GithubTokenStore githubTokens;
  final UpdateChecker updates;
  final DateTime Function()? now;
  final String appVersion;
  final bool _lockedCrafty;

  CraftyClient _crafty;
  CraftyClient get crafty => _crafty;

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
  bool loadingLogs = false;

  StoredCraftySettings craftySettings = const StoredCraftySettings();
  bool savingCrafty = false;
  String? craftyTestMessage;
  bool? craftyTestOk;

  String githubToken = '';
  bool savingGithubToken = false;

  AppRelease? pendingUpdate;
  bool updateCheckFailed = false;
  bool checkingUpdates = false;
  var updateChecked = false;

  bool get craftyLive => !_crafty.mock;

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
    craftySettings = await secrets.load();
    githubToken = await githubTokens.load();
    if (githubToken.isEmpty) {
      const fromEnv = String.fromEnvironment('GITHUB_TOKEN');
      if (fromEnv.isNotEmpty) githubToken = fromEnv;
    }
    if (!_lockedCrafty) {
      _crafty = createCraftyClient(_resolveConfig());
    }
    await _restoreSession();
    notifyListeners();
    if (current != null) {
      await refreshNetwork();
    }
    await checkForUpdate();
  }

  CraftyConfig _resolveConfig() {
    if (craftySettings.isConfigured) return craftySettings.toConfig();
    return CraftyConfig.fromEnvironment();
  }

  Future<void> _restoreSession() async {
    final id = await sessionStore.readAccountId();
    if (id == null) return;
    for (final account in accounts) {
      if (account.id == id) {
        current = account;
        return;
      }
    }
    await sessionStore.clear();
  }

  Future<void> _loadLocale() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final code = prefs.getString('aetherion.operator.locale');
      if (code == 'de') locale = LocaleOption.de;
    } catch (_) {}
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
    if (current?.id == id) {
      current = null;
      await sessionStore.clear();
    }
    await persistence.save(accounts);
    notifyListeners();
  }

  Future<String?> signIn(OperatorAccount account, {String? pin}) async {
    if (account.hasPin) {
      if (pin == null || !account.checkPin(pin)) {
        return 'pin';
      }
    }
    current = account;
    await sessionStore.writeAccountId(account.id);
    notifyListeners();
    refreshNetwork();
    return null;
  }

  Future<void> signOut() async {
    current = null;
    await sessionStore.clear();
    notifyListeners();
  }

  Future<void> saveCraftySettings({
    required String baseUrl,
    required String apiToken,
    required bool allowInsecureTls,
  }) async {
    savingCrafty = true;
    craftyTestMessage = null;
    craftyTestOk = null;
    notifyListeners();
    final token = apiToken.trim().isEmpty
        ? craftySettings.apiToken
        : apiToken.trim();
    await secrets.save(
      baseUrl: baseUrl,
      apiToken: token,
      allowInsecureTls: allowInsecureTls,
    );
    craftySettings = await secrets.load();
    if (!_lockedCrafty) {
      _crafty = createCraftyClient(_resolveConfig());
    }
    savingCrafty = false;
    notifyListeners();
    await refreshNetwork();
  }

  Future<void> testCraftyConnection() async {
    craftyTestMessage = null;
    craftyTestOk = null;
    notifyListeners();
    try {
      final snap = await _crafty.fetchNetwork();
      craftyTestOk = true;
      craftyTestMessage =
          '${snap.servers.length} server(s) · ${snap.mock ? 'mock' : 'live'}';
      network = snap;
    } catch (e) {
      craftyTestOk = false;
      craftyTestMessage = e.toString();
    }
    notifyListeners();
  }

  Future<void> refreshNetwork() async {
    loadingNetwork = true;
    networkError = null;
    notifyListeners();
    try {
      network = await _crafty.fetchNetwork();
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
      final result = await _crafty.sendCommand(
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
    await _runAction('soft restart', (id) => _crafty.softRestart(serverId: id));
  }

  Future<void> queueStart() async {
    await _runAction('start', (id) => _crafty.startServer(serverId: id));
  }

  Future<void> queueStop() async {
    await _runAction('stop', (id) => _crafty.stopServer(serverId: id));
  }

  Future<void> _runAction(
    String label,
    Future<CommandResult> Function(String id) run,
  ) async {
    final target = selectedServerId;
    console.add(
      ConsoleLine(
        kind: ConsoleKind.system,
        text: '$label → $target',
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
    final result = await run(target);
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

  Future<void> loadRemoteLogs() async {
    loadingLogs = true;
    notifyListeners();
    try {
      final lines = await _crafty.fetchLogs(serverId: selectedServerId);
      for (final line in lines) {
        console.add(
          ConsoleLine(
            kind: ConsoleKind.response,
            text: line,
            at: _now(),
            serverId: selectedServerId,
          ),
        );
      }
      if (lines.isEmpty) {
        console.add(
          ConsoleLine(
            kind: ConsoleKind.system,
            text: 'no remote log lines',
            at: _now(),
            serverId: selectedServerId,
          ),
        );
      }
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
      loadingLogs = false;
      notifyListeners();
    }
  }

  Future<void> addWhitelistNote(String text) async {
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
    final name = trimmed.split(RegExp(r'\s+')).first;
    if (OperatorAccount.namePattern.hasMatch(name) && !_crafty.mock) {
      await submitCommand('whitelist add $name');
    }
  }

  Future<void> saveGithubToken(String token) async {
    savingGithubToken = true;
    notifyListeners();
    final trimmed = token.trim();
    // Empty field keeps the previously stored token.
    if (trimmed.isNotEmpty) {
      await githubTokens.save(trimmed);
      githubToken = trimmed;
    } else {
      githubToken = await githubTokens.load();
    }
    savingGithubToken = false;
    notifyListeners();
    await checkForUpdate();
  }

  Future<void> checkForUpdate() async {
    checkingUpdates = true;
    updateCheckFailed = false;
    notifyListeners();
    try {
      pendingUpdate = await updates.latestNewerThan(
        appVersion,
        buildStamp: kOperatorBuildStamp,
        githubToken: githubToken.isEmpty ? null : githubToken,
      );
    } catch (_) {
      updateCheckFailed = true;
      pendingUpdate = null;
    } finally {
      checkingUpdates = false;
      updateChecked = true;
      notifyListeners();
    }
  }

  /// Clear a snoozed prompt only — Settings still shows [pendingUpdate].
  void snoozeUpdatePrompt() {
    notifyListeners();
  }

  void dismissUpdate() {
    pendingUpdate = null;
    notifyListeners();
  }
}

enum LocaleOption { en, de }

extension LocaleOptionX on LocaleOption {
  String get code => this == LocaleOption.de ? 'de' : 'en';
}
