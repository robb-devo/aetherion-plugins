# Aetherion Operator

Flutter companion for the Aetherion network on **Windows** and **Android**. Dark glass UI aligned with [donnernet.de](https://donnernet.de) (void / amethyst / cyan, Cinzel + Manrope). English by default, German toggle on every screen.

MVP is local and mock-backed. **No Crafty credentials are hardcoded.**

## Screens

1. **Login** — local operator list. Seed account `Operator` requires a PIN (hashed locally at seed time). Add teammate names (1–16 `[A-Za-z0-9_]`) with an optional PIN (SHA-256, device-local).
2. **Dashboard** — Velocity + Hub + mmo-r / mmo-d / mmo-c placeholders: online/offline, players, TPS. Gold **Mock data** badge until Crafty is enabled.
3. **DevKit** — command console with history, target server, **Soft restart** (confirm → log), **Whitelist note** (local until Crafty whitelist exists).
4. **Links** — `play.donnernet.de`, https://donnernet.de, Discord https://discord.gg/7BWHJaZChb.
5. **Team** — add / remove names on this device.

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

## Crafty later

Default client is `MockCraftyClient` (`lib/crafty/mock_crafty_client.dart`).

To point at a live Crafty Controller **without putting tokens in git**:

```bash
flutter run -d windows --dart-define=CRAFTY_ENABLED=true \
  --dart-define=CRAFTY_BASE_URL=https://YOUR-CRAFTY-HOST \
  --dart-define=CRAFTY_API_TOKEN=YOUR-TOKEN
```

`createCraftyClient()` in `lib/crafty/crafty_client.dart` then returns `HttpCraftyClient`, which calls:

| Action | Request |
|--------|---------|
| Status | `GET /api/v2/servers` |
| Console | `POST /api/v2/servers/{id}/stdin` `{"command":"..."}` |
| Soft restart | `POST /api/v2/servers/{id}/action` `{"action":"restart_server"}` |

JSON mapping is loose (`lib/crafty/http_crafty_client.dart`) so field names can be adjusted once a real Controller response is captured. Keep tokens in CI secrets or a private local script — never in source.

## Branding

Palette copied from donnernet.de CSS variables: void `#07060F`, ink `#0C0A18`, mist `#C9C2DE`, amethyst `#C084FC` / `#7C3AED`, cyan `#22D3EE`, gold `#F5C56B`. Fonts bundled under `fonts/` (Manrope, Cinzel — SIL OFL).
