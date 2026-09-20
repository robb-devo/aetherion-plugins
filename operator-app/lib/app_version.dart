/// Keep in lockstep with `pubspec.yaml` version (the part before `+`).
///
/// Release tags the updater understands:
/// - `operator-app-x.y.z` / `operator-app-vx.y.z` (preferred)
/// - `operator-app-apk-YYYYMMDD` (CI date builds, e.g. operator-app-apk-20260920)
const kOperatorAppVersion = '0.2.1';

/// YYYYMMDD stamp for this binary. Date-tagged GitHub Releases newer than this
/// count as updates. Bump together with [kOperatorAppVersion] when shipping.
const kOperatorBuildStamp = '20260920';

const kOperatorGithubRepo = 'robb-devo/aetherion-plugins';
const kOperatorReleaseTagPrefix = 'operator-app-';
