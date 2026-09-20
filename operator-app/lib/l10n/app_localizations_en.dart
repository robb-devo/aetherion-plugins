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
      'Local operator list — add teammate names, optional PIN. No OAuth in this MVP.';

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
      'Swap MockCraftyClient for HttpCraftyClient when CRAFTY_BASE_URL and CRAFTY_API_TOKEN are set. No credentials live in source.';

  @override
  String get devkitTitle => 'DevKit';

  @override
  String get devkitBody =>
      'Command console against the selected backend. Soft restart and whitelist notes are placeholders until Crafty is wired.';

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
  String get confirm => 'Confirm';

  @override
  String get whitelistNote => 'Whitelist note';

  @override
  String get whitelistNoteHint =>
      'Who / why — stored locally until Crafty whitelist is wired';

  @override
  String get whitelistSaved => 'Note saved locally.';

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
      'Names live on this device. Optional PIN is hashed locally — never sent anywhere in MVP.';

  @override
  String get nameRequired => 'Enter a name.';

  @override
  String get nameTaken => 'That name is already on the list.';

  @override
  String get nameInvalid => 'Use 1–16 letters, numbers, or underscore.';
}
