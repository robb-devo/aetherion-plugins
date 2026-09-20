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
      'Lokale Operator-Liste — Teamnamen hinzufügen, PIN optional. Kein OAuth in diesem MVP.';

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
      'MockCraftyClient durch HttpCraftyClient ersetzen, sobald CRAFTY_BASE_URL und CRAFTY_API_TOKEN gesetzt sind. Keine Zugangsdaten im Quellcode.';

  @override
  String get devkitTitle => 'DevKit';

  @override
  String get devkitBody =>
      'Befehls-Konsole gegen das gewählte Backend. Soft-Restart und Whitelist-Notizen sind Platzhalter, bis Crafty angebunden ist.';

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
  String get confirm => 'Bestätigen';

  @override
  String get whitelistNote => 'Whitelist-Notiz';

  @override
  String get whitelistNoteHint =>
      'Wer / warum — lokal, bis Crafty-Whitelist angebunden ist';

  @override
  String get whitelistSaved => 'Notiz lokal gespeichert.';

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
      'Namen liegen auf diesem Gerät. Optionale PIN wird lokal gehasht — im MVP nirgendwohin gesendet.';

  @override
  String get nameRequired => 'Bitte einen Namen eingeben.';

  @override
  String get nameTaken => 'Dieser Name ist schon auf der Liste.';

  @override
  String get nameInvalid =>
      '1–16 Zeichen: Buchstaben, Zahlen oder Unterstrich.';
}
