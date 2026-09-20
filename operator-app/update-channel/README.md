# Operator update channel (donnernet.de / Cloudflare)

The phone checks this **public** URL on every start — **no GitHub token**:

`https://donnernet.de/operator-app/latest.json`

## One-time: install the new APK

The APK you already have on the phone **cannot** check this URL (old build).
Install `operator_app_0.2.2_release.apk` once by hand. After that, every newer
`latest.json` on the site will show “Update available”.

## Upload to the server

Put these two files on the host that serves `donnernet.de` (nginx or Cloudflare R2/Pages), **as real static files** (not the SPA `index.html` fallback):

| File | URL |
|------|-----|
| `latest.json` (this folder) | `https://donnernet.de/operator-app/latest.json` |
| `operator_app_release.apk` | `https://donnernet.de/operator-app/operator_app_release.apk` |

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
2. `flutter build apk --release`
3. Replace `operator_app_release.apk` on the server.
4. Edit `latest.json` so `"version"` is **higher** than what operators already have.
5. Operators open the app → dialog → download APK.

## Verify

```bash
curl -sI https://donnernet.de/operator-app/latest.json
# Content-Type: application/json
curl -s https://donnernet.de/operator-app/latest.json
```
