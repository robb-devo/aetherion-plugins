import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_de.dart';
import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('de'),
    Locale('en'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'Aetherion Operator'**
  String get appTitle;

  /// No description provided for @appTagline.
  ///
  /// In en, this message translates to:
  /// **'donnernet companion'**
  String get appTagline;

  /// No description provided for @networkName.
  ///
  /// In en, this message translates to:
  /// **'Aetherion'**
  String get networkName;

  /// No description provided for @loginHeadline.
  ///
  /// In en, this message translates to:
  /// **'Sign in by name'**
  String get loginHeadline;

  /// No description provided for @loginBody.
  ///
  /// In en, this message translates to:
  /// **'Local operator list — add teammate names, optional PIN. Stay signed in until you sign out.'**
  String get loginBody;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get signIn;

  /// No description provided for @signedInAs.
  ///
  /// In en, this message translates to:
  /// **'Signed in as {name}'**
  String signedInAs(String name);

  /// No description provided for @noPin.
  ///
  /// In en, this message translates to:
  /// **'No PIN'**
  String get noPin;

  /// No description provided for @pinProtected.
  ///
  /// In en, this message translates to:
  /// **'PIN'**
  String get pinProtected;

  /// No description provided for @enterPin.
  ///
  /// In en, this message translates to:
  /// **'Enter PIN for {name}'**
  String enterPin(String name);

  /// No description provided for @pinHint.
  ///
  /// In en, this message translates to:
  /// **'PIN'**
  String get pinHint;

  /// No description provided for @pinWrong.
  ///
  /// In en, this message translates to:
  /// **'That PIN does not match.'**
  String get pinWrong;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @continueAction.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get continueAction;

  /// No description provided for @addAccount.
  ///
  /// In en, this message translates to:
  /// **'Add teammate'**
  String get addAccount;

  /// No description provided for @addAccountTitle.
  ///
  /// In en, this message translates to:
  /// **'Add operator name'**
  String get addAccountTitle;

  /// No description provided for @accountNameHint.
  ///
  /// In en, this message translates to:
  /// **'Minecraft / operator name'**
  String get accountNameHint;

  /// No description provided for @optionalPinHint.
  ///
  /// In en, this message translates to:
  /// **'Optional PIN'**
  String get optionalPinHint;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @remove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get remove;

  /// No description provided for @removeAccount.
  ///
  /// In en, this message translates to:
  /// **'Remove {name}?'**
  String removeAccount(String name);

  /// No description provided for @emptyTeam.
  ///
  /// In en, this message translates to:
  /// **'No operators yet. Add a name to start.'**
  String get emptyTeam;

  /// No description provided for @navDashboard.
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get navDashboard;

  /// No description provided for @navDevkit.
  ///
  /// In en, this message translates to:
  /// **'DevKit'**
  String get navDevkit;

  /// No description provided for @navLinks.
  ///
  /// In en, this message translates to:
  /// **'Links'**
  String get navLinks;

  /// No description provided for @navTeam.
  ///
  /// In en, this message translates to:
  /// **'Team'**
  String get navTeam;

  /// No description provided for @navSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// No description provided for @signOut.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get signOut;

  /// No description provided for @languageEn.
  ///
  /// In en, this message translates to:
  /// **'EN'**
  String get languageEn;

  /// No description provided for @languageDe.
  ///
  /// In en, this message translates to:
  /// **'DE'**
  String get languageDe;

  /// No description provided for @statusOnline.
  ///
  /// In en, this message translates to:
  /// **'Online'**
  String get statusOnline;

  /// No description provided for @statusOffline.
  ///
  /// In en, this message translates to:
  /// **'Offline'**
  String get statusOffline;

  /// No description provided for @mockBadge.
  ///
  /// In en, this message translates to:
  /// **'Mock data'**
  String get mockBadge;

  /// No description provided for @craftyLive.
  ///
  /// In en, this message translates to:
  /// **'Crafty'**
  String get craftyLive;

  /// No description provided for @networkOverview.
  ///
  /// In en, this message translates to:
  /// **'Network'**
  String get networkOverview;

  /// No description provided for @players.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get players;

  /// No description provided for @tps.
  ///
  /// In en, this message translates to:
  /// **'TPS'**
  String get tps;

  /// No description provided for @serversOnline.
  ///
  /// In en, this message translates to:
  /// **'Online'**
  String get serversOnline;

  /// No description provided for @refresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// No description provided for @lastFetched.
  ///
  /// In en, this message translates to:
  /// **'Updated {time}'**
  String lastFetched(String time);

  /// No description provided for @serverRoleProxy.
  ///
  /// In en, this message translates to:
  /// **'Proxy'**
  String get serverRoleProxy;

  /// No description provided for @serverRoleHub.
  ///
  /// In en, this message translates to:
  /// **'Hub'**
  String get serverRoleHub;

  /// No description provided for @serverRoleCapital.
  ///
  /// In en, this message translates to:
  /// **'Capital'**
  String get serverRoleCapital;

  /// No description provided for @serverRoleDungeons.
  ///
  /// In en, this message translates to:
  /// **'Dungeons'**
  String get serverRoleDungeons;

  /// No description provided for @serverRoleAshes.
  ///
  /// In en, this message translates to:
  /// **'Ashes'**
  String get serverRoleAshes;

  /// No description provided for @extensionHint.
  ///
  /// In en, this message translates to:
  /// **'Live Crafty binds from Settings (URL + API token) or dart-define. No credentials live in source.'**
  String get extensionHint;

  /// No description provided for @devkitTitle.
  ///
  /// In en, this message translates to:
  /// **'DevKit'**
  String get devkitTitle;

  /// No description provided for @devkitBody.
  ///
  /// In en, this message translates to:
  /// **'Command console against the selected backend. Restart, start/stop, logs, and whitelist go to Crafty when live.'**
  String get devkitBody;

  /// No description provided for @targetServer.
  ///
  /// In en, this message translates to:
  /// **'Target'**
  String get targetServer;

  /// No description provided for @consoleHint.
  ///
  /// In en, this message translates to:
  /// **'Command — e.g. list, whitelist add Name'**
  String get consoleHint;

  /// No description provided for @send.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get send;

  /// No description provided for @consoleEmpty.
  ///
  /// In en, this message translates to:
  /// **'No commands yet. Type above and press Send.'**
  String get consoleEmpty;

  /// No description provided for @quickActions.
  ///
  /// In en, this message translates to:
  /// **'Quick actions'**
  String get quickActions;

  /// No description provided for @softRestart.
  ///
  /// In en, this message translates to:
  /// **'Soft restart'**
  String get softRestart;

  /// No description provided for @softRestartConfirm.
  ///
  /// In en, this message translates to:
  /// **'Queue a soft restart for {server}? Mock mode only logs the intent.'**
  String softRestartConfirm(String server);

  /// No description provided for @softRestartConfirmLive.
  ///
  /// In en, this message translates to:
  /// **'Queue a Crafty restart for {server}?'**
  String softRestartConfirmLive(String server);

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @startServer.
  ///
  /// In en, this message translates to:
  /// **'Start'**
  String get startServer;

  /// No description provided for @startServerConfirm.
  ///
  /// In en, this message translates to:
  /// **'Start {server}?'**
  String startServerConfirm(String server);

  /// No description provided for @stopServer.
  ///
  /// In en, this message translates to:
  /// **'Stop'**
  String get stopServer;

  /// No description provided for @stopServerConfirm.
  ///
  /// In en, this message translates to:
  /// **'Stop {server}?'**
  String stopServerConfirm(String server);

  /// No description provided for @loadLogs.
  ///
  /// In en, this message translates to:
  /// **'Load logs'**
  String get loadLogs;

  /// No description provided for @whitelistNote.
  ///
  /// In en, this message translates to:
  /// **'Whitelist note'**
  String get whitelistNote;

  /// No description provided for @whitelistNoteHint.
  ///
  /// In en, this message translates to:
  /// **'Who / why — stored locally; live Crafty also runs whitelist add <name>'**
  String get whitelistNoteHint;

  /// No description provided for @whitelistSaved.
  ///
  /// In en, this message translates to:
  /// **'Note saved locally.'**
  String get whitelistSaved;

  /// No description provided for @whitelistSynced.
  ///
  /// In en, this message translates to:
  /// **'Note saved and whitelist add sent to Crafty.'**
  String get whitelistSynced;

  /// No description provided for @settingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// No description provided for @settingsBody.
  ///
  /// In en, this message translates to:
  /// **'Crafty Controller URL and API token stay on this device. Token prefers secure storage.'**
  String get settingsBody;

  /// No description provided for @usingLive.
  ///
  /// In en, this message translates to:
  /// **'Live Crafty'**
  String get usingLive;

  /// No description provided for @usingMock.
  ///
  /// In en, this message translates to:
  /// **'Mock Crafty — add URL and token to go live'**
  String get usingMock;

  /// No description provided for @craftyUrlHint.
  ///
  /// In en, this message translates to:
  /// **'https://crafty-host:8443'**
  String get craftyUrlHint;

  /// No description provided for @craftyTokenHint.
  ///
  /// In en, this message translates to:
  /// **'API token'**
  String get craftyTokenHint;

  /// No description provided for @craftyTokenSet.
  ///
  /// In en, this message translates to:
  /// **'Token saved — leave blank to keep it'**
  String get craftyTokenSet;

  /// No description provided for @allowInsecureTls.
  ///
  /// In en, this message translates to:
  /// **'Allow self-signed TLS'**
  String get allowInsecureTls;

  /// No description provided for @savedSettings.
  ///
  /// In en, this message translates to:
  /// **'Crafty settings saved.'**
  String get savedSettings;

  /// No description provided for @testConnection.
  ///
  /// In en, this message translates to:
  /// **'Test connection'**
  String get testConnection;

  /// No description provided for @currentVersion.
  ///
  /// In en, this message translates to:
  /// **'Version {version}'**
  String currentVersion(String version);

  /// No description provided for @buildStamp.
  ///
  /// In en, this message translates to:
  /// **'Build {stamp}'**
  String buildStamp(String stamp);

  /// No description provided for @updateHowTo.
  ///
  /// In en, this message translates to:
  /// **'Updates come from GitHub Releases: operator-app-x.y.z or operator-app-apk-YYYYMMDD with an attached APK. Private repos need a read-only GitHub token below. Bump pubspec, app_version.dart, and kOperatorBuildStamp together.'**
  String get updateHowTo;

  /// No description provided for @checkUpdates.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get checkUpdates;

  /// No description provided for @githubTokenHint.
  ///
  /// In en, this message translates to:
  /// **'GitHub token (private repo)'**
  String get githubTokenHint;

  /// No description provided for @githubTokenSet.
  ///
  /// In en, this message translates to:
  /// **'GitHub token saved — leave blank to keep it'**
  String get githubTokenSet;

  /// No description provided for @savedGithubToken.
  ///
  /// In en, this message translates to:
  /// **'GitHub token saved.'**
  String get savedGithubToken;

  /// No description provided for @updateAvailable.
  ///
  /// In en, this message translates to:
  /// **'Update {version} available'**
  String updateAvailable(String version);

  /// No description provided for @updateBody.
  ///
  /// In en, this message translates to:
  /// **'You are on {current}. Release {next} is on GitHub.'**
  String updateBody(String current, String next);

  /// No description provided for @later.
  ///
  /// In en, this message translates to:
  /// **'Later'**
  String get later;

  /// No description provided for @updateNow.
  ///
  /// In en, this message translates to:
  /// **'Open release'**
  String get updateNow;

  /// No description provided for @updateCheckFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not reach GitHub Releases.'**
  String get updateCheckFailed;

  /// No description provided for @upToDate.
  ///
  /// In en, this message translates to:
  /// **'No newer operator-app release.'**
  String get upToDate;

  /// No description provided for @linksTitle.
  ///
  /// In en, this message translates to:
  /// **'Links'**
  String get linksTitle;

  /// No description provided for @linksBody.
  ///
  /// In en, this message translates to:
  /// **'Public join address, site, and Discord.'**
  String get linksBody;

  /// No description provided for @copyAddress.
  ///
  /// In en, this message translates to:
  /// **'Copy'**
  String get copyAddress;

  /// No description provided for @copied.
  ///
  /// In en, this message translates to:
  /// **'Copied {value}'**
  String copied(String value);

  /// No description provided for @openLink.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get openLink;

  /// No description provided for @playAddress.
  ///
  /// In en, this message translates to:
  /// **'play.donnernet.de'**
  String get playAddress;

  /// No description provided for @playAddressHint.
  ///
  /// In en, this message translates to:
  /// **'Minecraft Java · Velocity :25565'**
  String get playAddressHint;

  /// No description provided for @siteLabel.
  ///
  /// In en, this message translates to:
  /// **'donnernet.de'**
  String get siteLabel;

  /// No description provided for @siteHint.
  ///
  /// In en, this message translates to:
  /// **'Public site'**
  String get siteHint;

  /// No description provided for @discordLabel.
  ///
  /// In en, this message translates to:
  /// **'Discord'**
  String get discordLabel;

  /// No description provided for @discordHint.
  ///
  /// In en, this message translates to:
  /// **'Operator + player invite'**
  String get discordHint;

  /// No description provided for @teamTitle.
  ///
  /// In en, this message translates to:
  /// **'Operator list'**
  String get teamTitle;

  /// No description provided for @teamBody.
  ///
  /// In en, this message translates to:
  /// **'Names live on this device. Optional PIN is hashed locally. Session stays until you sign out.'**
  String get teamBody;

  /// No description provided for @nameRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter a name.'**
  String get nameRequired;

  /// No description provided for @nameTaken.
  ///
  /// In en, this message translates to:
  /// **'That name is already on the list.'**
  String get nameTaken;

  /// No description provided for @nameInvalid.
  ///
  /// In en, this message translates to:
  /// **'Use 1–16 letters, numbers, or underscore.'**
  String get nameInvalid;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['de', 'en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'de':
      return AppLocalizationsDe();
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
