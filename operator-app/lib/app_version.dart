/// Keep in lockstep with `pubspec.yaml` version (the part before `+`).
///
/// Update channels (checked in order):
/// 1. Public manifest on the network site (no token) — see [kOperatorUpdateManifestUrl]
/// 2. GitHub Releases (optional PAT for the private plugins repo)
///
/// GitHub tags still understood as fallback:
/// - `operator-app-x.y.z` / `operator-app-vx.y.z`
/// - `operator-app-apk-YYYYMMDD`
const kOperatorAppVersion = '0.2.4';

/// YYYYMMDD stamp for this binary. Used when comparing date-tagged builds.
const kOperatorBuildStamp = '20260920';

/// Public JSON the phone checks on every start (Cloudflare / donnernet.de).
/// Host `update-channel/latest.json` + the APK at this path — no GitHub token needed.
const kOperatorUpdateManifestUrl =
    'https://donnernet.de/operator-app/latest.json';

const kOperatorGithubRepo = 'robb-devo/aetherion-plugins';
const kOperatorReleaseTagPrefix = 'operator-app-';
