// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Aetherion Operator';

  @override
  String get appTagline => 'donnernet companion';

  @override
  String get networkName => 'Aetherion';

  @override
  String get loginHeadline => 'Sign in by name';

  @override
  String get loginBody =>
      'Local operator list — add teammate names, optional PIN. Stay signed in until you sign out.';

  @override
  String get signIn => 'Sign in';

  @override
  String signedInAs(String name) {
    return 'Signed in as $name';
  }

  @override
  String get noPin => 'No PIN';

  @override
  String get pinProtected => 'PIN';

  @override
  String enterPin(String name) {
    return 'Enter PIN for $name';
  }

  @override
  String get pinHint => 'PIN';

  @override
  String get pinWrong => 'That PIN does not match.';

  @override
  String get cancel => 'Cancel';

  @override
  String get continueAction => 'Continue';

  @override
  String get addAccount => 'Add teammate';

  @override
  String get addAccountTitle => 'Add operator name';

  @override
  String get accountNameHint => 'Minecraft / operator name';

  @override
  String get optionalPinHint => 'Optional PIN';

  @override
  String get save => 'Save';

  @override
  String get remove => 'Remove';

  @override
  String removeAccount(String name) {
    return 'Remove $name?';
  }

  @override
  String get emptyTeam => 'No operators yet. Add a name to start.';

  @override
  String get navDashboard => 'Dashboard';

  @override
  String get navDevkit => 'DevKit';

  @override
  String get navLinks => 'Links';

  @override
  String get navTeam => 'Team';

  @override
  String get navSettings => 'Settings';

  @override
  String get signOut => 'Sign out';

  @override
  String get languageEn => 'EN';

  @override
  String get languageDe => 'DE';

  @override
  String get statusOnline => 'Online';

  @override
  String get statusOffline => 'Offline';

  @override
  String get mockBadge => 'Mock data';

  @override
  String get craftyLive => 'Crafty';

  @override
  String get networkOverview => 'Network';

  @override
  String get players => 'Players';

  @override
  String get tps => 'TPS';

  @override
  String get serversOnline => 'Online';

  @override
  String get refresh => 'Refresh';

  @override
  String lastFetched(String time) {
    return 'Updated $time';
  }

  @override
  String get serverRoleProxy => 'Proxy';

  @override
  String get serverRoleHub => 'Hub';

  @override
  String get serverRoleCapital => 'Capital';

  @override
  String get serverRoleDungeons => 'Dungeons';

  @override
  String get serverRoleAshes => 'Ashes';

  @override
  String get extensionHint =>
      'Live Crafty binds from Settings (URL + API token) or dart-define. No credentials live in source.';

  @override
  String get devkitTitle => 'DevKit';

  @override
  String get devkitBody =>
      'Command console against the selected backend. Restart, start/stop, logs, and whitelist go to Crafty when live.';

  @override
  String get devkitSkyBody =>
      'SkyCrypt-style operator tools — pick a server, run world/player actions, or open the full server page.';

  @override
  String get devkitWorld => 'World';

  @override
  String get devkitPlayers => 'Players';

  @override
  String get devkitPower => 'Power';

  @override
  String get devkitConsole => 'Console';

  @override
  String get showConsole => 'Show console';

  @override
  String get hideConsole => 'Hide console';

  @override
  String get serverOpenDetail => 'Open server';

  @override
  String get serverTabOverview => 'Overview';

  @override
  String get serverTabTerminal => 'Terminal';

  @override
  String get serverTabPower => 'Power';

  @override
  String get serverTabTools => 'Tools';

  @override
  String get serverMissing => 'Server not in the current network snapshot.';

  @override
  String get serverQuickNav => 'Control';

  @override
  String get serverOverviewHint =>
      'Use Terminal for commands, Power for start/stop/restart, Tools for common operator shortcuts.';

  @override
  String get serverPowerTitle => 'Power & lifecycle';

  @override
  String get serverPowerLiveHint =>
      'These actions talk to Crafty Controller for this server.';

  @override
  String get serverPowerMockHint =>
      'Mock mode — start/stop need live Crafty. Soft restart still logs locally.';

  @override
  String get serverPowerStartHint => 'Boot the process via Crafty';

  @override
  String get serverPowerStopHint => 'Graceful stop via Crafty';

  @override
  String get serverPowerRestartHint => 'Crafty soft restart / restart_server';

  @override
  String get serverPowerRefreshHint => 'Refresh network stats from Crafty';

  @override
  String get serverToolsTitle => 'Operator tools';

  @override
  String get serverToolsHint =>
      'One-tap console commands for this backend (SkyCrypt-style tiles).';

  @override
  String get serverOpenTerminal => 'Open terminal tab';

  @override
  String get serverOpenTerminalHint => 'Jump to the live command console';

  @override
  String get toolListPlayers => 'List players';

  @override
  String get toolListPlayersHint => 'Runs /list on the target';

  @override
  String get toolDay => 'Set day';

  @override
  String get toolDayHint => 'time set day';

  @override
  String get toolNight => 'Set night';

  @override
  String get toolNightHint => 'time set night';

  @override
  String get toolClearWeather => 'Clear weather';

  @override
  String get toolClearWeatherHint => 'weather clear';

  @override
  String get toolSaveAll => 'Save-all';

  @override
  String get toolSaveAllHint => 'Force a world save';

  @override
  String get toolTps => 'TPS probe';

  @override
  String get toolTpsHint => 'Send tps (plugin-dependent)';

  @override
  String toolCommandSent(String command) {
    return 'Sent: $command';
  }

  @override
  String get targetServer => 'Target';

  @override
  String get consoleHint => 'Command — e.g. list, whitelist add Name';

  @override
  String get send => 'Send';

  @override
  String get consoleEmpty => 'No commands yet. Type above and press Send.';

  @override
  String get quickActions => 'Quick actions';

  @override
  String get softRestart => 'Soft restart';

  @override
  String softRestartConfirm(String server) {
    return 'Queue a soft restart for $server? Mock mode only logs the intent.';
  }

  @override
  String softRestartConfirmLive(String server) {
    return 'Queue a Crafty restart for $server?';
  }

  @override
  String get confirm => 'Confirm';

  @override
  String get startServer => 'Start';

  @override
  String startServerConfirm(String server) {
    return 'Start $server?';
  }

  @override
  String get stopServer => 'Stop';

  @override
  String stopServerConfirm(String server) {
    return 'Stop $server?';
  }

  @override
  String get loadLogs => 'Load logs';

  @override
  String get whitelistNote => 'Whitelist note';

  @override
  String get whitelistNoteHint =>
      'Who / why — stored locally; live Crafty also runs whitelist add <name>';

  @override
  String get whitelistSaved => 'Note saved locally.';

  @override
  String get whitelistSynced => 'Note saved and whitelist add sent to Crafty.';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get settingsBody =>
      'Crafty Controller URL and API token stay on this device. Token prefers secure storage.';

  @override
  String get usingLive => 'Live Crafty';

  @override
  String get usingMock => 'Mock Crafty — add URL and token to go live';

  @override
  String get craftyUrlHint => 'https://crafty-host:8443';

  @override
  String get craftyTokenHint => 'API token';

  @override
  String get craftyTokenSet => 'Token saved — leave blank to keep it';

  @override
  String get allowInsecureTls => 'Allow self-signed TLS';

  @override
  String get savedSettings => 'Crafty settings saved.';

  @override
  String get testConnection => 'Test connection';

  @override
  String currentVersion(String version) {
    return 'Version $version';
  }

  @override
  String buildStamp(String stamp) {
    return 'Build $stamp';
  }

  @override
  String get updateHowTo =>
      'On start the app checks https://donnernet.de/operator-app/latest.json. Tap Install update to download and install in the app (APK on Android, zip on Windows). GitHub Releases is only a fallback if you save a token.';

  @override
  String get checkUpdates => 'Check for updates';

  @override
  String get githubTokenHint => 'GitHub token (private repo)';

  @override
  String get githubTokenSet => 'GitHub token saved — leave blank to keep it';

  @override
  String get savedGithubToken => 'GitHub token saved.';

  @override
  String updateAvailable(String version) {
    return 'Update $version available';
  }

  @override
  String updateBody(String current, String next) {
    return 'You are on $current. Version $next can be installed in the app.';
  }

  @override
  String get later => 'Later';

  @override
  String get updateNow => 'Install update';

  @override
  String get updateDownloading => 'Downloading update…';

  @override
  String get updateInstalling => 'Installing update…';

  @override
  String updateFailed(String error) {
    return 'Update failed: $error';
  }

  @override
  String get updateCheckFailed => 'Could not reach the update channel.';

  @override
  String get upToDate => 'No newer operator-app release.';

  @override
  String get linksTitle => 'Links';

  @override
  String get linksBody => 'Public join address, site, and Discord.';

  @override
  String get copyAddress => 'Copy';

  @override
  String copied(String value) {
    return 'Copied $value';
  }

  @override
  String get openLink => 'Open';

  @override
  String get playAddress => 'play.donnernet.de';

  @override
  String get playAddressHint => 'Minecraft Java · Velocity :25565';

  @override
  String get siteLabel => 'donnernet.de';

  @override
  String get siteHint => 'Public site';

  @override
  String get discordLabel => 'Discord';

  @override
  String get discordHint => 'Operator + player invite';

  @override
  String get teamTitle => 'Operator list';

  @override
  String get teamBody =>
      'Names live on this device. Optional PIN is hashed locally. Session stays until you sign out.';

  @override
  String get nameRequired => 'Enter a name.';

  @override
  String get nameTaken => 'That name is already on the list.';

  @override
  String get nameInvalid => 'Use 1–16 letters, numbers, or underscore.';
}
