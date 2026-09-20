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
  String get navPlayers => 'Players';

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
  String get playersTitle => 'Players';

  @override
  String get playersBody =>
      'Network-wide roster from Crafty stats. Pull to refresh or open actions on a name.';

  @override
  String get playersEmpty => 'No players online right now.';

  @override
  String get playersSearch => 'Search players or servers';

  @override
  String get actionRestricted => 'That action is not allowed for this account.';

  @override
  String playerOnServer(String server) {
    return 'on $server';
  }

  @override
  String get playerActions => 'Operator actions';

  @override
  String playerActionConfirm(String action, String player) {
    return 'Run $action on $player?';
  }

  @override
  String get playerReasonHint => 'Reason (optional)';

  @override
  String get playerMsgHint => 'Message text';

  @override
  String get playerTpTargetHint => 'Target player name';

  @override
  String get playerTpCoordsHint => 'x y z';

  @override
  String get playerNameHint => 'Player name';

  @override
  String get tps => 'TPS';

  @override
  String get cpu => 'CPU';

  @override
  String get ram => 'RAM';

  @override
  String get world => 'World';

  @override
  String get version => 'Version';

  @override
  String get softRefresh => 'Auto-refresh network every 15s while signed in';

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
  String get devkitChat => 'Chat';

  @override
  String get devkitModeration => 'Moderation';

  @override
  String get devkitMaintenance => 'Maintenance';

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
  String get serverTabPlayers => 'Players';

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
  String get toolJumpPlayersHint => 'Use the Players tab for the full roster';

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
  String get toolRain => 'Rain';

  @override
  String get toolRainHint => 'weather rain';

  @override
  String get toolThunder => 'Thunder';

  @override
  String get toolThunderHint => 'weather thunder';

  @override
  String get toolDifficultyPeaceful => 'Peaceful';

  @override
  String get toolDifficultyEasy => 'Easy';

  @override
  String get toolDifficultyNormal => 'Normal';

  @override
  String get toolDifficultyHard => 'Hard';

  @override
  String get toolKeepInvOn => 'Keep inventory on';

  @override
  String get toolKeepInvOff => 'Keep inventory off';

  @override
  String get toolSay => 'Say';

  @override
  String get toolSayHint => 'Broadcast a chat message';

  @override
  String get toolTitle => 'Title';

  @override
  String get toolTitleHint => 'Show a title to all players';

  @override
  String get toolKick => 'Kick';

  @override
  String get toolKickHint => 'Kick a player';

  @override
  String get toolBan => 'Ban';

  @override
  String get toolBanHint => 'Ban a player';

  @override
  String get toolPardon => 'Pardon';

  @override
  String get toolPardonHint => 'Unban a player';

  @override
  String get toolOp => 'Op';

  @override
  String get toolOpHint => 'Grant operator';

  @override
  String get toolDeop => 'Deop';

  @override
  String get toolDeopHint => 'Revoke operator';

  @override
  String get toolGamemodeSurvival => 'Survival';

  @override
  String get toolGamemodeCreative => 'Creative';

  @override
  String get toolGamemodeAdventure => 'Adventure';

  @override
  String get toolGamemodeSpectator => 'Spectator';

  @override
  String get toolMsg => 'Message';

  @override
  String get toolMsgHint => 'Private message';

  @override
  String get toolKill => 'Kill';

  @override
  String get toolKillHint => 'Kill the player';

  @override
  String get toolClear => 'Clear inventory';

  @override
  String get toolClearHint => 'clear inventory';

  @override
  String get toolWhitelistAdd => 'Whitelist add';

  @override
  String get toolWhitelistAddHint => 'whitelist add';

  @override
  String get toolWhitelistRemove => 'Whitelist remove';

  @override
  String get toolWhitelistRemoveHint => 'whitelist remove';

  @override
  String get toolWhitelistOn => 'Whitelist on';

  @override
  String get toolWhitelistOff => 'Whitelist off';

  @override
  String get toolTpPlayer => 'TP to player';

  @override
  String get toolTpPlayerHint => 'Teleport to another player';

  @override
  String get toolTpCoords => 'TP coords';

  @override
  String get toolTpCoordsHint => 'Teleport to coordinates';

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

  @override
  String get roleRestricted =>
      'Your account can look around and use mild tools — not power or moderation.';

  @override
  String get observerBanner =>
      'Observer mode — browse freely, change weather/time, view players. Heavy actions stay locked.';

  @override
  String get observerRoleLabel => 'Observer';

  @override
  String get mockConnectTitle => 'Not connected to Crafty';

  @override
  String get mockConnectBody =>
      'These servers are demo placeholders — not your network. In Settings paste your Crafty URL (https://135.181.18.162:8443) and API token, keep insecure TLS on, then Save + Test.';

  @override
  String get mockConnectAction => 'Open Settings';

  @override
  String get updateFailedTitle => 'Update stalled';

  @override
  String get updateInstallerOpened =>
      'Installer opened — confirm install on the next screen.';

  @override
  String get updateOpenBrowser => 'Open download in browser';
}
