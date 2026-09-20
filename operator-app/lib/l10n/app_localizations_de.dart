// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'Aetherion Operator';

  @override
  String get appTagline => 'donnernet Begleiter';

  @override
  String get networkName => 'Aetherion';

  @override
  String get loginHeadline => 'Anmeldung per Name';

  @override
  String get loginBody =>
      'Lokale Operator-Liste — Teamnamen hinzufügen, PIN optional. Angemeldet bleiben bis zur Abmeldung.';

  @override
  String get signIn => 'Anmelden';

  @override
  String signedInAs(String name) {
    return 'Angemeldet als $name';
  }

  @override
  String get noPin => 'Ohne PIN';

  @override
  String get pinProtected => 'PIN';

  @override
  String enterPin(String name) {
    return 'PIN für $name';
  }

  @override
  String get pinHint => 'PIN';

  @override
  String get pinWrong => 'Diese PIN stimmt nicht.';

  @override
  String get cancel => 'Abbrechen';

  @override
  String get continueAction => 'Weiter';

  @override
  String get addAccount => 'Team hinzufügen';

  @override
  String get addAccountTitle => 'Operator-Name';

  @override
  String get accountNameHint => 'Minecraft- / Operator-Name';

  @override
  String get optionalPinHint => 'Optionale PIN';

  @override
  String get save => 'Speichern';

  @override
  String get remove => 'Entfernen';

  @override
  String removeAccount(String name) {
    return '$name entfernen?';
  }

  @override
  String get emptyTeam =>
      'Noch keine Operatoren. Namen hinzufügen, um zu starten.';

  @override
  String get navDashboard => 'Übersicht';

  @override
  String get navDevkit => 'DevKit';

  @override
  String get navLinks => 'Links';

  @override
  String get navTeam => 'Team';

  @override
  String get navSettings => 'Einstellungen';

  @override
  String get signOut => 'Abmelden';

  @override
  String get languageEn => 'EN';

  @override
  String get languageDe => 'DE';

  @override
  String get statusOnline => 'Online';

  @override
  String get statusOffline => 'Offline';

  @override
  String get mockBadge => 'Testdaten';

  @override
  String get craftyLive => 'Crafty';

  @override
  String get networkOverview => 'Netzwerk';

  @override
  String get players => 'Spieler';

  @override
  String get tps => 'TPS';

  @override
  String get serversOnline => 'Online';

  @override
  String get refresh => 'Aktualisieren';

  @override
  String lastFetched(String time) {
    return 'Aktualisiert $time';
  }

  @override
  String get serverRoleProxy => 'Proxy';

  @override
  String get serverRoleHub => 'Hub';

  @override
  String get serverRoleCapital => 'Hauptstadt';

  @override
  String get serverRoleDungeons => 'Dungeons';

  @override
  String get serverRoleAshes => 'Ashes';

  @override
  String get extensionHint =>
      'Live-Crafty kommt aus den Einstellungen (URL + API-Token) oder dart-define. Keine Zugangsdaten im Quellcode.';

  @override
  String get devkitTitle => 'DevKit';

  @override
  String get devkitBody =>
      'Befehls-Konsole gegen das gewählte Backend. Restart, Start/Stop, Logs und Whitelist gehen live an Crafty.';

  @override
  String get devkitSkyBody =>
      'SkyCrypt-artige Operator-Tools — Server wählen, Welt/Spieler-Aktionen oder die Server-Seite öffnen.';

  @override
  String get devkitWorld => 'Welt';

  @override
  String get devkitPlayers => 'Spieler';

  @override
  String get devkitPower => 'Power';

  @override
  String get devkitConsole => 'Konsole';

  @override
  String get showConsole => 'Konsole zeigen';

  @override
  String get hideConsole => 'Konsole ausblenden';

  @override
  String get serverOpenDetail => 'Server öffnen';

  @override
  String get serverTabOverview => 'Übersicht';

  @override
  String get serverTabTerminal => 'Terminal';

  @override
  String get serverTabPower => 'Power';

  @override
  String get serverTabTools => 'Tools';

  @override
  String get serverMissing =>
      'Server ist nicht im aktuellen Netzwerk-Snapshot.';

  @override
  String get serverQuickNav => 'Steuerung';

  @override
  String get serverOverviewHint =>
      'Terminal für Befehle, Power für Start/Stop/Restart, Tools für häufige Operator-Shortcuts.';

  @override
  String get serverPowerTitle => 'Power & Lifecycle';

  @override
  String get serverPowerLiveHint =>
      'Diese Aktionen gehen an den Crafty Controller für diesen Server.';

  @override
  String get serverPowerMockHint =>
      'Mock-Modus — Start/Stop brauchen Live-Crafty. Soft-Restart loggt lokal.';

  @override
  String get serverPowerStartHint => 'Prozess über Crafty starten';

  @override
  String get serverPowerStopHint => 'Sauber stoppen über Crafty';

  @override
  String get serverPowerRestartHint => 'Crafty Soft-Restart / restart_server';

  @override
  String get serverPowerRefreshHint => 'Netzwerk-Stats von Crafty neu laden';

  @override
  String get serverToolsTitle => 'Operator-Tools';

  @override
  String get serverToolsHint =>
      'Ein-Tipp-Konsolenbefehle für dieses Backend (SkyCrypt-Style).';

  @override
  String get serverOpenTerminal => 'Terminal-Tab öffnen';

  @override
  String get serverOpenTerminalHint => 'Zur Live-Befehlskonsole springen';

  @override
  String get toolListPlayers => 'Spieler listen';

  @override
  String get toolListPlayersHint => 'Führt /list auf dem Ziel aus';

  @override
  String get toolDay => 'Tag setzen';

  @override
  String get toolDayHint => 'time set day';

  @override
  String get toolNight => 'Nacht setzen';

  @override
  String get toolNightHint => 'time set night';

  @override
  String get toolClearWeather => 'Wetter klar';

  @override
  String get toolClearWeatherHint => 'weather clear';

  @override
  String get toolSaveAll => 'Save-all';

  @override
  String get toolSaveAllHint => 'Welt speichern erzwingen';

  @override
  String get toolTps => 'TPS-Probe';

  @override
  String get toolTpsHint => 'tps senden (pluginabhängig)';

  @override
  String toolCommandSent(String command) {
    return 'Gesendet: $command';
  }

  @override
  String get targetServer => 'Ziel';

  @override
  String get consoleHint => 'Befehl — z. B. list, whitelist add Name';

  @override
  String get send => 'Senden';

  @override
  String get consoleEmpty => 'Noch keine Befehle. Oben eingeben und Senden.';

  @override
  String get quickActions => 'Schnellaktionen';

  @override
  String get softRestart => 'Soft-Restart';

  @override
  String softRestartConfirm(String server) {
    return 'Soft-Restart für $server einreihen? Im Mock wird nur die Absicht geloggt.';
  }

  @override
  String softRestartConfirmLive(String server) {
    return 'Crafty-Restart für $server einreihen?';
  }

  @override
  String get confirm => 'Bestätigen';

  @override
  String get startServer => 'Starten';

  @override
  String startServerConfirm(String server) {
    return '$server starten?';
  }

  @override
  String get stopServer => 'Stoppen';

  @override
  String stopServerConfirm(String server) {
    return '$server stoppen?';
  }

  @override
  String get loadLogs => 'Logs laden';

  @override
  String get whitelistNote => 'Whitelist-Notiz';

  @override
  String get whitelistNoteHint =>
      'Wer / warum — lokal gespeichert; live sendet Crafty whitelist add <name>';

  @override
  String get whitelistSaved => 'Notiz lokal gespeichert.';

  @override
  String get whitelistSynced =>
      'Notiz gespeichert und whitelist add an Crafty gesendet.';

  @override
  String get settingsTitle => 'Einstellungen';

  @override
  String get settingsBody =>
      'Crafty-Controller-URL und API-Token bleiben auf diesem Gerät. Token bevorzugt sicheren Speicher.';

  @override
  String get usingLive => 'Live-Crafty';

  @override
  String get usingMock =>
      'Mock-Crafty — URL und Token eintragen, um live zu gehen';

  @override
  String get craftyUrlHint => 'https://crafty-host:8443';

  @override
  String get craftyTokenHint => 'API-Token';

  @override
  String get craftyTokenSet =>
      'Token gespeichert — leer lassen, um ihn zu behalten';

  @override
  String get allowInsecureTls => 'Selbstsigniertes TLS erlauben';

  @override
  String get savedSettings => 'Crafty-Einstellungen gespeichert.';

  @override
  String get testConnection => 'Verbindung testen';

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
      'Beim Start prüft die App https://donnernet.de/operator-app/latest.json (öffentlich, ohne Token). Dort latest.json + APK ablegen. GitHub-Releases sind Fallback, wenn du einen Token speicherst.';

  @override
  String get checkUpdates => 'Nach Updates suchen';

  @override
  String get githubTokenHint => 'GitHub-Token (privates Repo)';

  @override
  String get githubTokenSet =>
      'GitHub-Token gespeichert — leer lassen, um ihn zu behalten';

  @override
  String get savedGithubToken => 'GitHub-Token gespeichert.';

  @override
  String updateAvailable(String version) {
    return 'Update $version verfügbar';
  }

  @override
  String updateBody(String current, String next) {
    return 'Du bist auf $current. Release $next liegt auf GitHub.';
  }

  @override
  String get later => 'Später';

  @override
  String get updateNow => 'Release öffnen';

  @override
  String get updateCheckFailed => 'GitHub-Releases nicht erreichbar.';

  @override
  String get upToDate => 'Kein neueres operator-app-Release.';

  @override
  String get linksTitle => 'Links';

  @override
  String get linksBody => 'Join-Adresse, Website und Discord.';

  @override
  String get copyAddress => 'Kopieren';

  @override
  String copied(String value) {
    return '$value kopiert';
  }

  @override
  String get openLink => 'Öffnen';

  @override
  String get playAddress => 'play.donnernet.de';

  @override
  String get playAddressHint => 'Minecraft Java · Velocity :25565';

  @override
  String get siteLabel => 'donnernet.de';

  @override
  String get siteHint => 'Öffentliche Seite';

  @override
  String get discordLabel => 'Discord';

  @override
  String get discordHint => 'Invite für Operator und Spieler';

  @override
  String get teamTitle => 'Operator-Liste';

  @override
  String get teamBody =>
      'Namen liegen auf diesem Gerät. Optionale PIN wird lokal gehasht. Sitzung bleibt bis zur Abmeldung.';

  @override
  String get nameRequired => 'Bitte einen Namen eingeben.';

  @override
  String get nameTaken => 'Dieser Name ist schon auf der Liste.';

  @override
  String get nameInvalid =>
      '1–16 Zeichen: Buchstaben, Zahlen oder Unterstrich.';
}
