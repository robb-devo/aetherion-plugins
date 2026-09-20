# Aetherion Operator

Flutter companion for the Aetherion network on **Windows** and **Android**. Dark glass UI aligned with [donnernet.de](https://donnernet.de) (void / amethyst / cyan, Cinzel + Manrope). English by default, German toggle on every screen.

**No Crafty credentials are hardcoded.** Session stays signed in after PIN until you sign out. Live Crafty is optional (Settings or dart-define).

## Screens

1. **Login** — local operator list. Seed account `Operator` requires a PIN (hashed locally at seed time). Add teammate names (1–16 `[A-Za-z0-9_]`) with an optional PIN (SHA-256, device-local). After a successful PIN, the account id is remembered on this device.
2. **Dashboard** — Velocity + Hub + mmo-r / mmo-d / mmo-c: online/offline, players, TPS. Gold **Mock data** badge until Crafty is configured.
3. **DevKit** — command console, **Soft restart**, live **Start** / **Stop**, **Load logs**, **Whitelist note** (local; live Crafty also sends `whitelist add <name>`).
4. **Links** — `play.donnernet.de`, https://donnernet.de, Discord https://discord.gg/7BWHJaZChb.
5. **Team** — add / remove names on this device.
6. **Settings** — Crafty URL + API token (token prefers secure storage, XOR-obfuscated prefs fallback), self-signed TLS toggle, test connection, version + GitHub update check.

## Run

Need [Flutter](https://docs.flutter.dev/get-started/install) 3.24+ (this scaffold was created on 3.47 stable).

```bash
cd operator-app
flutter pub get
```

### Windows desktop

```bash
flutter config --enable-windows-desktop
flutter run -d windows
# release:
flutter build windows
```

The window title is **Aetherion Operator**. Installer-free folder: `build/windows/x64/runner/Release/`.

### Android

```bash
flutter devices          # USB debugging or an emulator
flutter run -d android
flutter build apk        # build/app/outputs/flutter-apk/app-release.apk
```

Min SDK is Flutter’s default (currently 24). The app id is `de.aetherion.operator_app`.

### Linux / web (debug only)

Same Dart UI; useful when you are not on Windows:

```bash
flutter run -d linux
flutter run -d chrome --web-port=8099
```

### Tests

```bash
flutter test
flutter analyze
```

## Crafty

Default client is `MockCraftyClient` until Settings (or dart-define) supply both a base URL and an API token.

### Settings (preferred)

Open **Settings** after sign-in:

- Controller URL, e.g. `https://crafty-host:8443`
- API token (stored in platform secure storage when available)
- **Allow self-signed TLS** (on by default for typical Crafty installs)
- **Test connection** hits `GET /api/v2/servers`

Empty token field on save keeps the previously stored token.

### dart-define (used only when Settings are empty)

```bash
flutter run -d windows --dart-define=CRAFTY_ENABLED=true \
  --dart-define=CRAFTY_BASE_URL=https://YOUR-CRAFTY-HOST \
  --dart-define=CRAFTY_API_TOKEN=YOUR-TOKEN
```

`createCraftyClient()` in `lib/crafty/crafty_client.dart` then returns `HttpCraftyClient`:

| Action | Request |
|--------|---------|
| Status | `GET /api/v2/servers` (+ `/stats` per id) |
| Logs | `GET /api/v2/servers/{id}/logs` |
| Console | `POST /api/v2/servers/{id}/stdin` |
| Restart / start / stop | `POST /api/v2/servers/{id}/action/{name}` |

JSON mapping is loose (`lib/crafty/http_crafty_client.dart`) so field names can be adjusted against a real Controller. Keep tokens in CI secrets or a private local script — never in source.

## Updates

Version is `0.2.2+4` in `pubspec.yaml` and `kOperatorAppVersion` / `kOperatorBuildStamp` in `lib/app_version.dart`.

### How the phone checks (no GitHub token)

On every start the app fetches the **public** file:

`https://donnernet.de/operator-app/latest.json`

If `version` (or `buildStamp`) is newer than the installed app, it shows an update dialog and opens the `apkUrl`. Files to upload live in [`update-channel/`](update-channel/README.md) — nginx or Cloudflare static hosting.

GitHub Releases (`operator-app-x.y.z` / `operator-app-apk-YYYYMMDD`) remain a **fallback** when a PAT is saved in Settings (private repo).

### First install

The APK already on your phone is too old to use this channel. Install the new `0.2.2` APK **once**, upload `latest.json` + APK to the domain, then every later bump of `latest.json` will prompt operators automatically.
## Branding

This plugins repo has no `website/` tree and no Discord brand pack (only Minecraft item textures).

**App icon** (Android launcher + adaptive + Windows `.ico`) is the Aetherion Discord guild / channel icon from invite `https://discord.gg/7BWHJaZChb` (`cdn.discordapp.com/icons/1550245657816600647/…`, original `assets/brand/discord_guild_icon.png`). Discord only stores that upload at 64×64; `discord_guild_icon_1024.png` is a Lanczos upscale used by `flutter_launcher_icons`.

**In-app mark** is the live site header SVG [donnernet.de/favicon.svg](https://donnernet.de/favicon.svg) at `assets/brand/favicon.svg` (amethyst–cyan gem). Web debug favicon stays that SVG.

Regenerate Android mipmaps + Windows ICO:

```bash
dart run flutter_launcher_icons
```

Palette from donnernet.de CSS variables: void `#07060F`, ink `#0C0A18`, mist `#C9C2DE`, amethyst `#C084FC` / `#7C3AED`, cyan `#22D3EE`, gold `#F5C56B`. Fonts bundled under `fonts/` (Manrope, Cinzel — SIL OFL).
