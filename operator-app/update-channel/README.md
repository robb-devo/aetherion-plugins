# Operator update channel (donnernet.de / Cloudflare)

The phone checks this **public** URL on every start — **no GitHub token**:

`https://donnernet.de/operator-app/latest.json`

## One-time: install 0.2.5 (or newer)

Older builds only opened GitHub. Install `operator_app_0.2.5` **once** by hand
(or from the site). From then on, “Install update” downloads and installs inside
the app — no GitHub redirect.

## Upload to the server

Put these files on the host that serves `donnernet.de` (nginx or Cloudflare R2/Pages), **as real static files** (not the SPA `index.html` fallback):

| File | URL |
|------|-----|
| `latest.json` (this folder) | `https://donnernet.de/operator-app/latest.json` |
| `operator_app_release.apk` | `https://donnernet.de/operator-app/operator_app_release.apk` |
| `operator_app_windows.zip` | `https://donnernet.de/operator-app/operator_app_windows.zip` |

Windows: unzip anywhere and run `operator_app.exe`. Login is remembered on this PC after the first sign-in.

### nginx example

```nginx
location /operator-app/ {
  alias /var/www/operator-app/;
  types { application/json json; application/vnd.android.package-archive apk; }
  default_type application/octet-stream;
  add_header Cache-Control "no-cache";
}
```

Copy:

```bash
sudo mkdir -p /var/www/operator-app
sudo cp latest.json /var/www/operator-app/
sudo cp operator_app_release.apk /var/www/operator-app/
# Content-Type for .json must be application/json (not text/html)
```

### Cloudflare

- **R2 / Pages**: upload both files under `operator-app/`.
- Or a Worker that serves `latest.json` + redirects APK downloads.
- Make sure `/operator-app/latest.json` returns JSON, not the marketing SPA HTML.

## Push a new update later

1. Bump `pubspec.yaml` + `lib/app_version.dart` (`kOperatorAppVersion` / `kOperatorBuildStamp`).
2. Tag `operator-app-x.y.z` (CI builds APK + Windows zip) **or** `flutter build apk --release` locally.
3. Replace `operator_app_release.apk` / `operator_app_windows.zip` on the server.
4. Edit `latest.json` so `"version"` is **higher** than what operators already have.
5. Operators open the app → dialog → **Install update** → download + system installer (Android) / auto-swap (Windows).

## Verify

```bash
curl -sI https://donnernet.de/operator-app/latest.json
# Content-Type: application/json
curl -s https://donnernet.de/operator-app/latest.json
```
