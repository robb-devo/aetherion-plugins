import 'dart:convert';

import 'package:http/http.dart' as http;

import '../app_version.dart';

class AppRelease {
  const AppRelease({
    required this.tag,
    required this.version,
    required this.htmlUrl,
    this.apkUrl,
    this.windowsUrl,
    this.publishedAt,
    this.kind = OperatorReleaseKind.semver,
    this.notes,
  });

  final String tag;
  /// Display label: `0.2.2` or `2026.09.20`.
  final String version;
  final String htmlUrl;
  final String? apkUrl;
  /// Zipped Windows release folder (`operator_app_windows.zip`).
  final String? windowsUrl;
  final DateTime? publishedAt;
  final OperatorReleaseKind kind;
  final String? notes;
}

enum OperatorReleaseKind { semver, dateBuild }

abstract class UpdateChecker {
  Future<AppRelease?> latestNewerThan(
    String currentVersion, {
    String buildStamp = kOperatorBuildStamp,
    String? githubToken,
  });
}

class NoopUpdateChecker implements UpdateChecker {
  const NoopUpdateChecker();

  @override
  Future<AppRelease?> latestNewerThan(
    String currentVersion, {
    String buildStamp = kOperatorBuildStamp,
    String? githubToken,
  }) async =>
      null;
}

/// Tries the public site manifest first (no auth), then GitHub Releases.
class CascadingUpdateChecker implements UpdateChecker {
  CascadingUpdateChecker({
    UpdateChecker? primary,
    UpdateChecker? fallback,
    http.Client? httpClient,
  }) : primary =
           primary ??
           PublicManifestUpdateChecker(
             httpClient: httpClient,
             manifestUrl: Uri.parse(kOperatorUpdateManifestUrl),
           ),
       fallback = fallback ?? GithubReleaseChecker(httpClient: httpClient);

  final UpdateChecker primary;
  final UpdateChecker fallback;

  @override
  Future<AppRelease?> latestNewerThan(
    String currentVersion, {
    String buildStamp = kOperatorBuildStamp,
    String? githubToken,
  }) async {
    Object? primaryError;
    try {
      final fromSite = await primary.latestNewerThan(
        currentVersion,
        buildStamp: buildStamp,
        githubToken: githubToken,
      );
      if (fromSite != null) return fromSite;
    } catch (e) {
      primaryError = e;
    }
    try {
      return await fallback.latestNewerThan(
        currentVersion,
        buildStamp: buildStamp,
        githubToken: githubToken,
      );
    } catch (e) {
      // Public channel already answered "no update" — ignore private GitHub 404.
      if (primaryError == null) return null;
      throw primaryError;
    }
  }
}

/// Reads a public JSON file hosted on donnernet.de / Cloudflare.
///
/// Example `latest.json`:
/// ```json
/// {
///   "version": "0.2.3",
///   "buildStamp": "20260921",
///   "tag": "operator-app-0.2.3",
///   "apkUrl": "https://donnernet.de/operator-app/operator_app_release.apk",
///   "windowsUrl": "https://donnernet.de/operator-app/operator_app_windows.zip",
///   "htmlUrl": "https://donnernet.de/operator-app/",
///   "notes": "Optional changelog"
/// }
/// ```
class PublicManifestUpdateChecker implements UpdateChecker {
  PublicManifestUpdateChecker({
    http.Client? httpClient,
    Uri? manifestUrl,
  }) : _http = httpClient ?? http.Client(),
       manifestUrl = manifestUrl ?? Uri.parse(kOperatorUpdateManifestUrl);

  final http.Client _http;
  final Uri manifestUrl;

  @override
  Future<AppRelease?> latestNewerThan(
    String currentVersion, {
    String buildStamp = kOperatorBuildStamp,
    String? githubToken,
  }) async {
    final response = await _http.get(
      manifestUrl,
      headers: {
        'Accept': 'application/json',
        'User-Agent': 'aetherion-operator-app',
        'Cache-Control': 'no-cache',
      },
    );
    if (response.statusCode >= 400) {
      throw StateError(
        'Update manifest ${response.statusCode}: ${response.body}',
      );
    }
    // SPA fallback often returns HTML with 200 — reject that.
    final contentType = response.headers['content-type'] ?? '';
    final body = response.body.trimLeft();
    if (contentType.contains('text/html') || body.startsWith('<!')) {
      throw StateError(
        'Update manifest is HTML (SPA fallback). Host a real latest.json.',
      );
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map) return null;
    return releaseFromManifest(
      Map<String, dynamic>.from(decoded),
      currentVersion: currentVersion,
      buildStamp: buildStamp,
    );
  }
}

AppRelease? releaseFromManifest(
  Map<String, dynamic> raw, {
  required String currentVersion,
  required String buildStamp,
}) {
  final version = '${raw['version'] ?? ''}'.trim();
  if (version.isEmpty) return null;
  final stamp = '${raw['buildStamp'] ?? raw['build_stamp'] ?? ''}'.trim();
  final rawTag = '${raw['tag'] ?? ''}'.trim();
  final tag = rawTag.isEmpty ? 'operator-app-$version' : rawTag;
  final apkUrl = '${raw['apkUrl'] ?? raw['apk_url'] ?? ''}'.trim();
  final windowsUrl =
      '${raw['windowsUrl'] ?? raw['windows_url'] ?? ''}'.trim();
  final htmlUrl =
      '${raw['htmlUrl'] ?? raw['html_url'] ?? kOperatorUpdateManifestUrl}'
          .trim();
  final notes = '${raw['notes'] ?? ''}'.trim();

  final newerBySemver = isVersionNewer(version, currentVersion);
  final newerByStamp =
      stamp.isNotEmpty &&
      (int.tryParse(stamp) ?? 0) > (int.tryParse(buildStamp) ?? 0);
  if (!newerBySemver && !newerByStamp) return null;
  final hasApk = apkUrl.startsWith('http');
  final hasWindows = windowsUrl.startsWith('http');
  if (!hasApk && !hasWindows) return null;

  return AppRelease(
    tag: tag,
    version: version,
    htmlUrl: htmlUrl.startsWith('http') ? htmlUrl : kOperatorUpdateManifestUrl,
    apkUrl: hasApk ? apkUrl : null,
    windowsUrl: hasWindows ? windowsUrl : null,
    publishedAt: DateTime.tryParse('${raw['publishedAt'] ?? ''}'),
    kind: OperatorReleaseKind.semver,
    notes: notes.isEmpty ? null : notes,
  );
}

class GithubReleaseChecker implements UpdateChecker {
  GithubReleaseChecker({
    http.Client? httpClient,
    this.repo = kOperatorGithubRepo,
    this.token,
  }) : _http = httpClient ?? http.Client();

  final http.Client _http;
  final String repo;
  /// Optional PAT for private repositories (contents:read is enough).
  final String? token;

  Map<String, String> _headers([String? overrideToken]) {
    final headers = <String, String>{
      'Accept': 'application/vnd.github+json',
      'User-Agent': 'aetherion-operator-app',
    };
    final t = (overrideToken ?? token)?.trim();
    if (t != null && t.isNotEmpty) {
      headers['Authorization'] = 'Bearer $t';
    }
    return headers;
  }

  @override
  Future<AppRelease?> latestNewerThan(
    String currentVersion, {
    String buildStamp = kOperatorBuildStamp,
    String? githubToken,
  }) async {
    final uri = Uri.https('api.github.com', '/repos/$repo/releases');
    final response = await _http.get(uri, headers: _headers(githubToken));
    if (response.statusCode >= 400) {
      throw StateError(
        'GitHub Releases ${response.statusCode}: ${response.body}',
      );
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! List) return null;

    AppRelease? best;
    for (final raw in decoded) {
      if (raw is! Map) continue;
      if (raw['draft'] == true) continue;
      final tag = '${raw['tag_name'] ?? ''}';
      final parsed = parseOperatorReleaseTag(tag);
      if (parsed == null) continue;
      final apkUrl = _apkAsset(raw['assets']);
      // Skip empty CI stubs without an installable APK.
      if (apkUrl == null && parsed.kind == OperatorReleaseKind.dateBuild) {
        continue;
      }
      if (!isReleaseNewer(
        parsed,
        currentVersion: currentVersion,
        buildStamp: buildStamp,
      )) {
        continue;
      }
      final publishedAt = DateTime.tryParse('${raw['published_at'] ?? ''}');
      final candidate = AppRelease(
        tag: tag,
        version: parsed.display,
        htmlUrl: '${raw['html_url'] ?? 'https://github.com/$repo/releases'}',
        apkUrl: apkUrl,
        publishedAt: publishedAt,
        kind: parsed.kind,
      );
      if (best == null || _isPreferred(candidate, best)) {
        best = candidate;
      }
    }
    return best;
  }

  bool _isPreferred(AppRelease a, AppRelease b) {
    final ap = a.publishedAt;
    final bp = b.publishedAt;
    if (ap != null && bp != null && ap != bp) return ap.isAfter(bp);
    if (a.kind == OperatorReleaseKind.semver &&
        b.kind == OperatorReleaseKind.semver) {
      return isVersionNewer(a.version, b.version);
    }
    if (a.kind == OperatorReleaseKind.dateBuild &&
        b.kind == OperatorReleaseKind.dateBuild) {
      return a.version.compareTo(b.version) > 0;
    }
    // Prefer semver over date builds when published_at is missing/equal.
    if (a.kind != b.kind) {
      return a.kind == OperatorReleaseKind.semver;
    }
    return false;
  }

  String? _apkAsset(dynamic assets) {
    if (assets is! List) return null;
    String? fallback;
    for (final asset in assets) {
      if (asset is! Map) continue;
      final name = '${asset['name']}'.toLowerCase();
      if (!name.endsWith('.apk')) continue;
      final url = '${asset['browser_download_url']}';
      if (!url.startsWith('http')) continue;
      // Prefer a named operator APK over a generic app-release.apk.
      if (name.contains('operator')) return url;
      fallback ??= url;
    }
    return fallback;
  }
}

class ParsedOperatorTag {
  const ParsedOperatorTag({
    required this.display,
    required this.kind,
    this.semver,
    this.yyyymmdd,
  });

  final String display;
  final OperatorReleaseKind kind;
  final String? semver;
  final String? yyyymmdd;
}

ParsedOperatorTag? parseOperatorReleaseTag(String tag) {
  var t = tag.trim();
  if (t.startsWith('refs/tags/')) t = t.substring('refs/tags/'.length);
  final semver = RegExp(
    r'^operator-app-v?(\d+\.\d+\.\d+)$',
    caseSensitive: false,
  ).firstMatch(t);
  if (semver != null) {
    return ParsedOperatorTag(
      display: semver.group(1)!,
      kind: OperatorReleaseKind.semver,
      semver: semver.group(1),
    );
  }
  final date = RegExp(
    r'^operator-app-apk-(\d{8})$',
    caseSensitive: false,
  ).firstMatch(t);
  if (date != null) {
    final raw = date.group(1)!;
    final display =
        '${raw.substring(0, 4)}.${raw.substring(4, 6)}.${raw.substring(6, 8)}';
    return ParsedOperatorTag(
      display: display,
      kind: OperatorReleaseKind.dateBuild,
      yyyymmdd: raw,
    );
  }
  return null;
}

bool isReleaseNewer(
  ParsedOperatorTag candidate, {
  required String currentVersion,
  required String buildStamp,
}) {
  switch (candidate.kind) {
    case OperatorReleaseKind.semver:
      return isVersionNewer(candidate.semver!, currentVersion);
    case OperatorReleaseKind.dateBuild:
      final current = int.tryParse(buildStamp) ?? 0;
      final next = int.tryParse(candidate.yyyymmdd!) ?? 0;
      return next > current;
  }
}

bool isVersionNewer(String candidate, String current) {
  List<int> parts(String v) {
    final cleaned = v.split('+').first.split('-').first;
    return cleaned.split('.').map((p) => int.tryParse(p) ?? 0).toList();
  }

  final a = parts(candidate);
  final b = parts(current);
  final n = a.length > b.length ? a.length : b.length;
  for (var i = 0; i < n; i++) {
    final av = i < a.length ? a[i] : 0;
    final bv = i < b.length ? b[i] : 0;
    if (av != bv) return av > bv;
  }
  return false;
}
