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

  /// No description provided for @navPlayers.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get navPlayers;

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

  /// No description provided for @playersTitle.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get playersTitle;

  /// No description provided for @playersBody.
  ///
  /// In en, this message translates to:
  /// **'Network-wide roster from Crafty stats. Pull to refresh or open actions on a name.'**
  String get playersBody;

  /// No description provided for @playersEmpty.
  ///
  /// In en, this message translates to:
  /// **'No players online right now.'**
  String get playersEmpty;

  /// No description provided for @playersSearch.
  ///
  /// In en, this message translates to:
  /// **'Search players or servers'**
  String get playersSearch;

  /// No description provided for @actionRestricted.
  ///
  /// In en, this message translates to:
  /// **'That action is not allowed for this account.'**
  String get actionRestricted;

  /// No description provided for @playerOnServer.
  ///
  /// In en, this message translates to:
  /// **'on {server}'**
  String playerOnServer(String server);

  /// No description provided for @playerActions.
  ///
  /// In en, this message translates to:
  /// **'Operator actions'**
  String get playerActions;

  /// No description provided for @playerActionConfirm.
  ///
  /// In en, this message translates to:
  /// **'Run {action} on {player}?'**
  String playerActionConfirm(String action, String player);

  /// No description provided for @playerReasonHint.
  ///
  /// In en, this message translates to:
  /// **'Reason (optional)'**
  String get playerReasonHint;

  /// No description provided for @playerMsgHint.
  ///
  /// In en, this message translates to:
  /// **'Message text'**
  String get playerMsgHint;

  /// No description provided for @playerTpTargetHint.
  ///
  /// In en, this message translates to:
  /// **'Target player name'**
  String get playerTpTargetHint;

  /// No description provided for @playerTpCoordsHint.
  ///
  /// In en, this message translates to:
  /// **'x y z'**
  String get playerTpCoordsHint;

  /// No description provided for @playerNameHint.
  ///
  /// In en, this message translates to:
  /// **'Player name'**
  String get playerNameHint;

  /// No description provided for @tps.
  ///
  /// In en, this message translates to:
  /// **'TPS'**
  String get tps;

  /// No description provided for @cpu.
  ///
  /// In en, this message translates to:
  /// **'CPU'**
  String get cpu;

  /// No description provided for @ram.
  ///
  /// In en, this message translates to:
  /// **'RAM'**
  String get ram;

  /// No description provided for @world.
  ///
  /// In en, this message translates to:
  /// **'World'**
  String get world;

  /// No description provided for @version.
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get version;

  /// No description provided for @softRefresh.
  ///
  /// In en, this message translates to:
  /// **'Auto-refresh network every 15s while signed in'**
  String get softRefresh;

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

  /// No description provided for @devkitSkyBody.
  ///
  /// In en, this message translates to:
  /// **'SkyCrypt-style operator tools — pick a server, run world/player actions, or open the full server page.'**
  String get devkitSkyBody;

  /// No description provided for @devkitWorld.
  ///
  /// In en, this message translates to:
  /// **'World'**
  String get devkitWorld;

  /// No description provided for @devkitPlayers.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get devkitPlayers;

  /// No description provided for @devkitChat.
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get devkitChat;

  /// No description provided for @devkitModeration.
  ///
  /// In en, this message translates to:
  /// **'Moderation'**
  String get devkitModeration;

  /// No description provided for @devkitMaintenance.
  ///
  /// In en, this message translates to:
  /// **'Maintenance'**
  String get devkitMaintenance;

  /// No description provided for @devkitPower.
  ///
  /// In en, this message translates to:
  /// **'Power'**
  String get devkitPower;

  /// No description provided for @devkitConsole.
  ///
  /// In en, this message translates to:
  /// **'Console'**
  String get devkitConsole;

  /// No description provided for @showConsole.
  ///
  /// In en, this message translates to:
  /// **'Show console'**
  String get showConsole;

  /// No description provided for @hideConsole.
  ///
  /// In en, this message translates to:
  /// **'Hide console'**
  String get hideConsole;

  /// No description provided for @serverOpenDetail.
  ///
  /// In en, this message translates to:
  /// **'Open server'**
  String get serverOpenDetail;

  /// No description provided for @serverTabOverview.
  ///
  /// In en, this message translates to:
  /// **'Overview'**
  String get serverTabOverview;

  /// No description provided for @serverTabPlayers.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get serverTabPlayers;

  /// No description provided for @serverTabTerminal.
  ///
  /// In en, this message translates to:
  /// **'Terminal'**
  String get serverTabTerminal;

  /// No description provided for @serverTabPower.
  ///
  /// In en, this message translates to:
  /// **'Power'**
  String get serverTabPower;

  /// No description provided for @serverTabTools.
  ///
  /// In en, this message translates to:
  /// **'Tools'**
  String get serverTabTools;

  /// No description provided for @serverMissing.
  ///
  /// In en, this message translates to:
  /// **'Server not in the current network snapshot.'**
  String get serverMissing;

  /// No description provided for @serverQuickNav.
  ///
  /// In en, this message translates to:
  /// **'Control'**
  String get serverQuickNav;

  /// No description provided for @serverOverviewHint.
  ///
  /// In en, this message translates to:
  /// **'Use Terminal for commands, Power for start/stop/restart, Tools for common operator shortcuts.'**
  String get serverOverviewHint;

  /// No description provided for @serverPowerTitle.
  ///
  /// In en, this message translates to:
  /// **'Power & lifecycle'**
  String get serverPowerTitle;

  /// No description provided for @serverPowerLiveHint.
  ///
  /// In en, this message translates to:
  /// **'These actions talk to Crafty Controller for this server.'**
  String get serverPowerLiveHint;

  /// No description provided for @serverPowerMockHint.
  ///
  /// In en, this message translates to:
  /// **'Mock mode — start/stop need live Crafty. Soft restart still logs locally.'**
  String get serverPowerMockHint;

  /// No description provided for @serverPowerStartHint.
  ///
  /// In en, this message translates to:
  /// **'Boot the process via Crafty'**
  String get serverPowerStartHint;

  /// No description provided for @serverPowerStopHint.
  ///
  /// In en, this message translates to:
  /// **'Graceful stop via Crafty'**
  String get serverPowerStopHint;

  /// No description provided for @serverPowerRestartHint.
  ///
  /// In en, this message translates to:
  /// **'Crafty soft restart / restart_server'**
  String get serverPowerRestartHint;

  /// No description provided for @serverPowerRefreshHint.
  ///
  /// In en, this message translates to:
  /// **'Refresh network stats from Crafty'**
  String get serverPowerRefreshHint;

  /// No description provided for @serverToolsTitle.
  ///
  /// In en, this message translates to:
  /// **'Operator tools'**
  String get serverToolsTitle;

  /// No description provided for @serverToolsHint.
  ///
  /// In en, this message translates to:
  /// **'One-tap console commands for this backend (SkyCrypt-style tiles).'**
  String get serverToolsHint;

  /// No description provided for @serverOpenTerminal.
  ///
  /// In en, this message translates to:
  /// **'Open terminal tab'**
  String get serverOpenTerminal;

  /// No description provided for @serverOpenTerminalHint.
  ///
  /// In en, this message translates to:
  /// **'Jump to the live command console'**
  String get serverOpenTerminalHint;

  /// No description provided for @toolListPlayers.
  ///
  /// In en, this message translates to:
  /// **'List players'**
  String get toolListPlayers;

  /// No description provided for @toolListPlayersHint.
  ///
  /// In en, this message translates to:
  /// **'Runs /list on the target'**
  String get toolListPlayersHint;

  /// No description provided for @toolJumpPlayersHint.
  ///
  /// In en, this message translates to:
  /// **'Use the Players tab for the full roster'**
  String get toolJumpPlayersHint;

  /// No description provided for @toolDay.
  ///
  /// In en, this message translates to:
  /// **'Set day'**
  String get toolDay;

  /// No description provided for @toolDayHint.
  ///
  /// In en, this message translates to:
  /// **'time set day'**
  String get toolDayHint;

  /// No description provided for @toolNight.
  ///
  /// In en, this message translates to:
  /// **'Set night'**
  String get toolNight;

  /// No description provided for @toolNightHint.
  ///
  /// In en, this message translates to:
  /// **'time set night'**
  String get toolNightHint;

  /// No description provided for @toolClearWeather.
  ///
  /// In en, this message translates to:
  /// **'Clear weather'**
  String get toolClearWeather;

  /// No description provided for @toolClearWeatherHint.
  ///
  /// In en, this message translates to:
  /// **'weather clear'**
  String get toolClearWeatherHint;

  /// No description provided for @toolRain.
  ///
  /// In en, this message translates to:
  /// **'Rain'**
  String get toolRain;

  /// No description provided for @toolRainHint.
  ///
  /// In en, this message translates to:
  /// **'weather rain'**
  String get toolRainHint;

  /// No description provided for @toolThunder.
  ///
  /// In en, this message translates to:
  /// **'Thunder'**
  String get toolThunder;

  /// No description provided for @toolThunderHint.
  ///
  /// In en, this message translates to:
  /// **'weather thunder'**
  String get toolThunderHint;

  /// No description provided for @toolDifficultyPeaceful.
  ///
  /// In en, this message translates to:
  /// **'Peaceful'**
  String get toolDifficultyPeaceful;

  /// No description provided for @toolDifficultyEasy.
  ///
  /// In en, this message translates to:
  /// **'Easy'**
  String get toolDifficultyEasy;

  /// No description provided for @toolDifficultyNormal.
  ///
  /// In en, this message translates to:
  /// **'Normal'**
  String get toolDifficultyNormal;

  /// No description provided for @toolDifficultyHard.
  ///
  /// In en, this message translates to:
  /// **'Hard'**
  String get toolDifficultyHard;

  /// No description provided for @toolKeepInvOn.
  ///
  /// In en, this message translates to:
  /// **'Keep inventory on'**
  String get toolKeepInvOn;

  /// No description provided for @toolKeepInvOff.
  ///
  /// In en, this message translates to:
  /// **'Keep inventory off'**
  String get toolKeepInvOff;

  /// No description provided for @toolSay.
  ///
  /// In en, this message translates to:
  /// **'Say'**
  String get toolSay;

  /// No description provided for @toolSayHint.
  ///
  /// In en, this message translates to:
  /// **'Broadcast a chat message'**
  String get toolSayHint;

  /// No description provided for @toolTitle.
  ///
  /// In en, this message translates to:
  /// **'Title'**
  String get toolTitle;

  /// No description provided for @toolTitleHint.
  ///
  /// In en, this message translates to:
  /// **'Show a title to all players'**
  String get toolTitleHint;

  /// No description provided for @toolKick.
  ///
  /// In en, this message translates to:
  /// **'Kick'**
  String get toolKick;

  /// No description provided for @toolKickHint.
  ///
  /// In en, this message translates to:
  /// **'Kick a player'**
  String get toolKickHint;

  /// No description provided for @toolBan.
  ///
  /// In en, this message translates to:
  /// **'Ban'**
  String get toolBan;

  /// No description provided for @toolBanHint.
  ///
  /// In en, this message translates to:
  /// **'Ban a player'**
  String get toolBanHint;

  /// No description provided for @toolPardon.
  ///
  /// In en, this message translates to:
  /// **'Pardon'**
  String get toolPardon;

  /// No description provided for @toolPardonHint.
  ///
  /// In en, this message translates to:
  /// **'Unban a player'**
  String get toolPardonHint;

  /// No description provided for @toolOp.
  ///
  /// In en, this message translates to:
  /// **'Op'**
  String get toolOp;

  /// No description provided for @toolOpHint.
  ///
  /// In en, this message translates to:
  /// **'Grant operator'**
  String get toolOpHint;

  /// No description provided for @toolDeop.
  ///
  /// In en, this message translates to:
  /// **'Deop'**
  String get toolDeop;

  /// No description provided for @toolDeopHint.
  ///
  /// In en, this message translates to:
  /// **'Revoke operator'**
  String get toolDeopHint;

  /// No description provided for @toolGamemodeSurvival.
  ///
  /// In en, this message translates to:
  /// **'Survival'**
  String get toolGamemodeSurvival;

  /// No description provided for @toolGamemodeCreative.
  ///
  /// In en, this message translates to:
  /// **'Creative'**
  String get toolGamemodeCreative;

  /// No description provided for @toolGamemodeAdventure.
  ///
  /// In en, this message translates to:
  /// **'Adventure'**
  String get toolGamemodeAdventure;

  /// No description provided for @toolGamemodeSpectator.
  ///
  /// In en, this message translates to:
  /// **'Spectator'**
  String get toolGamemodeSpectator;

  /// No description provided for @toolMsg.
  ///
  /// In en, this message translates to:
  /// **'Message'**
  String get toolMsg;

  /// No description provided for @toolMsgHint.
  ///
  /// In en, this message translates to:
  /// **'Private message'**
  String get toolMsgHint;

  /// No description provided for @toolKill.
  ///
  /// In en, this message translates to:
  /// **'Kill'**
  String get toolKill;

  /// No description provided for @toolKillHint.
  ///
  /// In en, this message translates to:
  /// **'Kill the player'**
  String get toolKillHint;

  /// No description provided for @toolClear.
  ///
  /// In en, this message translates to:
  /// **'Clear inventory'**
  String get toolClear;

  /// No description provided for @toolClearHint.
  ///
  /// In en, this message translates to:
  /// **'clear inventory'**
  String get toolClearHint;

  /// No description provided for @toolWhitelistAdd.
  ///
  /// In en, this message translates to:
  /// **'Whitelist add'**
  String get toolWhitelistAdd;

  /// No description provided for @toolWhitelistAddHint.
  ///
  /// In en, this message translates to:
  /// **'whitelist add'**
  String get toolWhitelistAddHint;

  /// No description provided for @toolWhitelistRemove.
  ///
  /// In en, this message translates to:
  /// **'Whitelist remove'**
  String get toolWhitelistRemove;

  /// No description provided for @toolWhitelistRemoveHint.
  ///
  /// In en, this message translates to:
  /// **'whitelist remove'**
  String get toolWhitelistRemoveHint;

  /// No description provided for @toolWhitelistOn.
  ///
  /// In en, this message translates to:
  /// **'Whitelist on'**
  String get toolWhitelistOn;

  /// No description provided for @toolWhitelistOff.
  ///
  /// In en, this message translates to:
  /// **'Whitelist off'**
  String get toolWhitelistOff;

  /// No description provided for @toolTpPlayer.
  ///
  /// In en, this message translates to:
  /// **'TP to player'**
  String get toolTpPlayer;

  /// No description provided for @toolTpPlayerHint.
  ///
  /// In en, this message translates to:
  /// **'Teleport to another player'**
  String get toolTpPlayerHint;

  /// No description provided for @toolTpCoords.
  ///
  /// In en, this message translates to:
  /// **'TP coords'**
  String get toolTpCoords;

  /// No description provided for @toolTpCoordsHint.
  ///
  /// In en, this message translates to:
  /// **'Teleport to coordinates'**
  String get toolTpCoordsHint;

  /// No description provided for @toolSaveAll.
  ///
  /// In en, this message translates to:
  /// **'Save-all'**
  String get toolSaveAll;

  /// No description provided for @toolSaveAllHint.
  ///
  /// In en, this message translates to:
  /// **'Force a world save'**
  String get toolSaveAllHint;

  /// No description provided for @toolTps.
  ///
  /// In en, this message translates to:
  /// **'TPS probe'**
  String get toolTps;

  /// No description provided for @toolTpsHint.
  ///
  /// In en, this message translates to:
  /// **'Send tps (plugin-dependent)'**
  String get toolTpsHint;

  /// No description provided for @toolCommandSent.
  ///
  /// In en, this message translates to:
  /// **'Sent: {command}'**
  String toolCommandSent(String command);

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
  /// **'On start the app checks https://donnernet.de/operator-app/latest.json. Tap Install update to download and install in the app (APK on Android, zip on Windows). GitHub Releases is only a fallback if you save a token.'**
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
  /// **'You are on {current}. Version {next} can be installed in the app.'**
  String updateBody(String current, String next);

  /// No description provided for @later.
  ///
  /// In en, this message translates to:
  /// **'Later'**
  String get later;

  /// No description provided for @updateNow.
  ///
  /// In en, this message translates to:
  /// **'Install update'**
  String get updateNow;

  /// No description provided for @updateDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading update…'**
  String get updateDownloading;

  /// No description provided for @updateInstalling.
  ///
  /// In en, this message translates to:
  /// **'Installing update…'**
  String get updateInstalling;

  /// No description provided for @updateFailed.
  ///
  /// In en, this message translates to:
  /// **'Update failed: {error}'**
  String updateFailed(String error);

  /// No description provided for @updateCheckFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not reach the update channel.'**
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

  /// No description provided for @roleRestricted.
  ///
  /// In en, this message translates to:
  /// **'Your account can look around and use mild tools — not power or moderation.'**
  String get roleRestricted;

  /// No description provided for @observerBanner.
  ///
  /// In en, this message translates to:
  /// **'Observer mode — browse freely, change weather/time, view players. Heavy actions stay locked.'**
  String get observerBanner;

  /// No description provided for @observerRoleLabel.
  ///
  /// In en, this message translates to:
  /// **'Observer'**
  String get observerRoleLabel;
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
