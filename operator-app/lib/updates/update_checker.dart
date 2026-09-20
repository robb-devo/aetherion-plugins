import 'dart:convert';

import 'package:http/http.dart' as http;

import '../app_version.dart';

class AppRelease {
  const AppRelease({
    required this.tag,
    required this.version,
    required this.htmlUrl,
    this.apkUrl,
  });

  final String tag;
  final String version;
  final String htmlUrl;
  final String? apkUrl;
}

abstract class UpdateChecker {
  Future<AppRelease?> latestNewerThan(String currentVersion);
}

class NoopUpdateChecker implements UpdateChecker {
  const NoopUpdateChecker();

  @override
  Future<AppRelease?> latestNewerThan(String currentVersion) async => null;
}

class GithubReleaseChecker implements UpdateChecker {
  GithubReleaseChecker({
    http.Client? httpClient,
    this.repo = kOperatorGithubRepo,
  }) : _http = httpClient ?? http.Client();

  final http.Client _http;
  final String repo;

  @override
  Future<AppRelease?> latestNewerThan(String currentVersion) async {
    final uri = Uri.https('api.github.com', '/repos/$repo/releases');
    final response = await _http.get(
      uri,
      headers: {
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'aetherion-operator-app',
      },
    );
    if (response.statusCode >= 400) return null;
    final decoded = jsonDecode(response.body);
    if (decoded is! List) return null;

    AppRelease? best;
    for (final raw in decoded) {
      if (raw is! Map) continue;
      final tag = '${raw['tag_name'] ?? ''}';
      final version = parseOperatorReleaseTag(tag);
      if (version == null) continue;
      if (!isVersionNewer(version, currentVersion)) continue;
      if (best != null && !isVersionNewer(version, best.version)) continue;
      best = AppRelease(
        tag: tag,
        version: version,
        htmlUrl: '${raw['html_url'] ?? 'https://github.com/$repo/releases'}',
        apkUrl: _apkAsset(raw['assets']),
      );
    }
    return best;
  }

  String? _apkAsset(dynamic assets) {
    if (assets is! List) return null;
    for (final asset in assets) {
      if (asset is! Map) continue;
      final name = '${asset['name']}'.toLowerCase();
      if (name.endsWith('.apk')) {
        final url = '${asset['browser_download_url']}';
        if (url.startsWith('http')) return url;
      }
    }
    return null;
  }
}

String? parseOperatorReleaseTag(String tag) {
  var t = tag.trim();
  if (t.startsWith('refs/tags/')) t = t.substring('refs/tags/'.length);
  final match = RegExp(
    r'^operator-app-v?(\d+\.\d+\.\d+)$',
    caseSensitive: false,
  ).firstMatch(t);
  return match?.group(1);
}

bool isVersionNewer(String candidate, String current) {
  List<int> parts(String v) {
    final cleaned = v.split('+').first.split('-').first;
    return cleaned
        .split('.')
        .map((p) => int.tryParse(p) ?? 0)
        .toList();
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
