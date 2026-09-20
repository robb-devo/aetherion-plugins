import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app_version.dart';
import '../crafty/crafty_client.dart';
import '../crafty/crafty_config.dart';
import '../crafty/player_list_parser.dart';
import '../data/account_store.dart';
import '../data/console_line.dart';
import '../data/crafty_secrets.dart';
import '../data/github_token_store.dart';
import '../data/operator_account.dart';
import '../data/operator_capabilities.dart';
import '../data/pin.dart';
import '../data/player_actions.dart';
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

  static const _maxConsoleLines = 200;

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

  /// Shell tab index request (e.g. open Settings from mock banner).
  int? shellTabRequest;

  Timer? _softRefreshTimer;
  bool _softRefreshWanted = false;

  Future<void> bootstrap() async {
    accounts = await persistence.load();
    var changed = false;
    for (final seed in [
      OperatorAccount.seedOperator(),
      OperatorAccount.seedLime(),
    ]) {
      final i = accounts.indexWhere((a) => a.id == seed.id);
      if (i < 0) {
        accounts = [...accounts, seed];
        changed = true;
      } else if (accounts[i].pinHash != seed.pinHash ||
          accounts[i].role != seed.role) {
        // Keep seed accounts (role + PIN) in sync across app updates.
        accounts = [...accounts]..[i] = seed;
        changed = true;
      }
    }
    if (changed) await persistence.save(accounts);
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
      if (_softRefreshWanted) setSoftRefreshEnabled(true);
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
    if (!canMutateTeam) return 'restricted';
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

  Future<String?> removeAccount(String id) async {
    if (!canMutateTeam) return 'restricted';
    accounts = accounts.where((a) => a.id != id).toList();
    if (current?.id == id) {
      current = null;
      await sessionStore.clear();
      setSoftRefreshEnabled(false);
    }
    await persistence.save(accounts);
    notifyListeners();
    return null;
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
    if (_softRefreshWanted) setSoftRefreshEnabled(true);
    return null;
  }

  Future<void> signOut() async {
    setSoftRefreshEnabled(false);
    current = null;
    await sessionStore.clear();
    notifyListeners();
  }

  /// Soft network refresh every 15s while signed in. Shell enables/pauses this.
  void setSoftRefreshEnabled(bool enabled) {
    _softRefreshWanted = enabled;
    _softRefreshTimer?.cancel();
    _softRefreshTimer = null;
    if (!enabled || current == null) return;
    _softRefreshTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      if (current != null) refreshNetwork();
    });
  }

  @override
  void dispose() {
    _softRefreshTimer?.cancel();
    _softRefreshTimer = null;
    super.dispose();
  }

  void _trimConsole() {
    if (console.length > _maxConsoleLines) {
      console.removeRange(0, console.length - _maxConsoleLines);
    }
  }

  void _addConsole(ConsoleLine line) {
    console.add(line);
    _trimConsole();
  }

  Future<String?> saveCraftySettings({
    required String baseUrl,
    required String apiToken,
    required bool allowInsecureTls,
  }) async {
    if (!canEditCrafty) return 'restricted';
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
    return null;
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

  bool get craftyLive => !_crafty.mock;

  OperatorRole get role => current?.role ?? OperatorRole.full;

  bool get canPower => OperatorCapabilities.allowsPower(role);
  bool get canMutateTeam => OperatorCapabilities.allowsTeamMutate(role);
  bool get canEditCrafty => OperatorCapabilities.allowsCraftySettings(role);

  bool canPlayerAction(PlayerAction action) =>
      OperatorCapabilities.allowsPlayerAction(role, action);

  bool canCommand(String command) =>
      OperatorCapabilities.allowsCommand(role, command);

  /// Returns an error code if blocked (`restricted`), else null after send.
  Future<String?> submitCommand(String command, {String? serverId}) async {
    final trimmed = command.trim();
    if (trimmed.isEmpty) return null;
    if (!canCommand(trimmed)) {
      _addConsole(
        ConsoleLine(
          kind: ConsoleKind.system,
          text: 'restricted → $trimmed',
          at: _now(),
          serverId: serverId ?? selectedServerId,
        ),
      );
      notifyListeners();
      return 'restricted';
    }
    final target = serverId ?? selectedServerId;
    if (serverId != null) selectedServerId = serverId;
    sendingCommand = true;
    _addConsole(
      ConsoleLine(
        kind: ConsoleKind.command,
        text: trimmed,
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
    try {
      final result = await _crafty.sendCommand(
        serverId: target,
        command: trimmed,
      );
      _addConsole(
        ConsoleLine(
          kind: result.ok ? ConsoleKind.response : ConsoleKind.error,
          text: result.message,
          at: _now(),
          serverId: target,
        ),
      );
    } catch (e) {
      _addConsole(
        ConsoleLine(
          kind: ConsoleKind.error,
          text: e.toString(),
          at: _now(),
          serverId: target,
        ),
      );
    } finally {
      sendingCommand = false;
      notifyListeners();
    }
    return null;
  }

  Future<String?> runPlayerAction({
    required String serverId,
    required PlayerAction action,
    required String player,
    String? reason,
    String? targetPlayer,
    String? coords,
  }) async {
    if (!canPlayerAction(action)) return 'restricted';
    final command = action.buildCommand(
      player,
      reason: reason,
      targetPlayer: targetPlayer,
      coords: coords,
    );
    return submitCommand(command, serverId: serverId);
  }

  Future<void> refreshPlayersViaList({String? serverId}) async {
    final target = serverId ?? selectedServerId;
    await submitCommand('list', serverId: target);
    if (_crafty.mock) {
      final names = parsePlayerNamesFromLogs(console.map((l) => l.text));
      _mergeOnlinePlayersIfEmpty(target, names);
      return;
    }
    await loadRemoteLogs(serverId: target);
    final names = parsePlayerNamesFromLogs(
      consoleFor(target).map((l) => l.text),
    );
    _mergeOnlinePlayersIfEmpty(target, names);
  }

  void _mergeOnlinePlayersIfEmpty(String serverId, List<String> names) {
    final net = network;
    if (net == null || names.isEmpty) return;
    final servers = <ServerSnapshot>[];
    var changed = false;
    for (final s in net.servers) {
      if (s.id != serverId || s.onlinePlayers.isNotEmpty) {
        servers.add(s);
        continue;
      }
      changed = true;
      servers.add(
        s.copyWith(
          onlinePlayers: playersOnServer(
            serverId: s.id,
            serverName: s.displayName,
            names: names,
          ),
          players: names.length,
        ),
      );
    }
    if (!changed) return;
    network = NetworkSnapshot(
      servers: servers,
      mock: net.mock,
      fetchedAt: net.fetchedAt,
      notice: net.notice,
    );
    notifyListeners();
  }

  Future<void> queueSoftRestart({String? serverId}) async {
    if (!canPower) {
      _denyPower('soft restart', serverId);
      return;
    }
    await _runAction(
      'soft restart',
      (id) => _crafty.softRestart(serverId: id),
      serverId: serverId,
    );
  }

  Future<void> queueStart({String? serverId}) async {
    if (!canPower) {
      _denyPower('start', serverId);
      return;
    }
    await _runAction(
      'start',
      (id) => _crafty.startServer(serverId: id),
      serverId: serverId,
    );
  }

  Future<void> queueStop({String? serverId}) async {
    if (!canPower) {
      _denyPower('stop', serverId);
      return;
    }
    await _runAction(
      'stop',
      (id) => _crafty.stopServer(serverId: id),
      serverId: serverId,
    );
  }

  void _denyPower(String label, String? serverId) {
    _addConsole(
      ConsoleLine(
        kind: ConsoleKind.system,
        text: 'restricted → $label',
        at: _now(),
        serverId: serverId ?? selectedServerId,
      ),
    );
    notifyListeners();
  }

  Future<void> _runAction(
    String label,
    Future<CommandResult> Function(String id) run, {
    String? serverId,
  }) async {
    final target = serverId ?? selectedServerId;
    if (serverId != null) selectedServerId = serverId;
    _addConsole(
      ConsoleLine(
        kind: ConsoleKind.system,
        text: '$label → $target',
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
    final result = await run(target);
    _addConsole(
      ConsoleLine(
        kind: result.ok ? ConsoleKind.response : ConsoleKind.error,
        text: result.message,
        at: _now(),
        serverId: target,
      ),
    );
    notifyListeners();
    await refreshNetwork();
  }

  Future<void> loadRemoteLogs({String? serverId}) async {
    final target = serverId ?? selectedServerId;
    if (serverId != null) selectedServerId = serverId;
    loadingLogs = true;
    notifyListeners();
    try {
      final lines = await _crafty.fetchLogs(serverId: target);
      for (final line in lines) {
        _addConsole(
          ConsoleLine(
            kind: ConsoleKind.response,
            text: line,
            at: _now(),
            serverId: target,
          ),
        );
      }
      if (lines.isEmpty) {
        _addConsole(
          ConsoleLine(
            kind: ConsoleKind.system,
            text: 'no remote log lines',
            at: _now(),
            serverId: target,
          ),
        );
      }
    } catch (e) {
      _addConsole(
        ConsoleLine(
          kind: ConsoleKind.error,
          text: e.toString(),
          at: _now(),
          serverId: target,
        ),
      );
    } finally {
      loadingLogs = false;
      notifyListeners();
    }
  }

  List<ConsoleLine> consoleFor(String serverId) =>
      console.where((l) => l.serverId == null || l.serverId == serverId).toList();

  Future<void> addWhitelistNote(String text) async {
    if (!canPower) {
      _addConsole(
        ConsoleLine(
          kind: ConsoleKind.system,
          text: 'restricted → whitelist note',
          at: _now(),
          serverId: selectedServerId,
        ),
      );
      notifyListeners();
      return;
    }
    final trimmed = text.trim();
    if (trimmed.isEmpty) return;
    whitelistNotes.add(
      WhitelistNote(
        text: trimmed,
        at: _now(),
        author: current?.name ?? 'operator',
      ),
    );
    _addConsole(
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

  Future<String?> saveGithubToken(String token) async {
    if (!canEditCrafty) return 'restricted';
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
    return null;
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

  void requestShellTab(int index) {
    shellTabRequest = index;
    notifyListeners();
  }

  void consumeShellTabRequest() {
    shellTabRequest = null;
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
