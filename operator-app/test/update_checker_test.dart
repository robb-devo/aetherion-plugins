import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:operator_app/updates/update_checker.dart';

void main() {
  test('parses operator-app semver and apk date tags', () {
    expect(parseOperatorReleaseTag('operator-app-1.2.3')?.display, '1.2.3');
    expect(parseOperatorReleaseTag('operator-app-v0.2.0')?.display, '0.2.0');
    expect(
      parseOperatorReleaseTag('refs/tags/operator-app-0.3.0')?.display,
      '0.3.0',
    );
    expect(
      parseOperatorReleaseTag('operator-app-apk-20260920')?.display,
      '2026.09.20',
    );
    expect(
      parseOperatorReleaseTag('operator-app-apk-20260920')?.kind,
      OperatorReleaseKind.dateBuild,
    );
    expect(parseOperatorReleaseTag('v1.0.0'), isNull);
    expect(parseOperatorReleaseTag('plugin-1.0.0'), isNull);
  });

  test('isVersionNewer compares dotted triples', () {
    expect(isVersionNewer('0.2.0', '0.1.0'), isTrue);
    expect(isVersionNewer('0.2.0', '0.2.0'), isFalse);
    expect(isVersionNewer('0.1.9', '0.2.0'), isFalse);
    expect(isVersionNewer('1.0.0', '0.9.9'), isTrue);
  });

  test('date builds are newer only when YYYYMMDD is greater', () {
    final older = parseOperatorReleaseTag('operator-app-apk-20260919')!;
    final same = parseOperatorReleaseTag('operator-app-apk-20260920')!;
    final newer = parseOperatorReleaseTag('operator-app-apk-20260921')!;
    expect(
      isReleaseNewer(older, currentVersion: '0.2.1', buildStamp: '20260920'),
      isFalse,
    );
    expect(
      isReleaseNewer(same, currentVersion: '0.2.1', buildStamp: '20260920'),
      isFalse,
    );
    expect(
      isReleaseNewer(newer, currentVersion: '0.2.1', buildStamp: '20260920'),
      isTrue,
    );
  });

  test('GithubReleaseChecker picks newest matching APK release', () async {
    final httpClient = MockClient((request) async {
      expect(request.url.path, '/repos/robb-devo/aetherion-plugins/releases');
      return http.Response(
        jsonEncode([
          {
            'tag_name': 'unrelated-1.0.0',
            'html_url': 'https://example/unrelated',
            'published_at': '2026-09-01T00:00:00Z',
            'assets': [],
          },
          {
            'tag_name': 'operator-app-apk-20260920',
            'html_url': 'https://example/date',
            'published_at': '2026-09-20T11:00:00Z',
            'assets': [
              {
                'name': 'operator_app_release.apk',
                'browser_download_url': 'https://example/date.apk',
              },
            ],
          },
          {
            'tag_name': 'operator-app-0.1.0',
            'html_url': 'https://example/old',
            'published_at': '2026-09-10T00:00:00Z',
            'assets': [
              {
                'name': 'old.apk',
                'browser_download_url': 'https://example/old.apk',
              },
            ],
          },
          {
            'tag_name': 'operator-app-v0.3.0',
            'html_url': 'https://example/new',
            'published_at': '2026-09-21T00:00:00Z',
            'assets': [
              {
                'name': 'operator-app-0.3.0.apk',
                'browser_download_url': 'https://example/new.apk',
              },
            ],
          },
        ]),
        200,
      );
    });

    final checker = GithubReleaseChecker(httpClient: httpClient);
    final release = await checker.latestNewerThan(
      '0.2.0',
      buildStamp: '20260920',
    );
    expect(release, isNotNull);
    expect(release!.version, '0.3.0');
    expect(release.apkUrl, 'https://example/new.apk');
    expect(
      await checker.latestNewerThan('0.3.0', buildStamp: '20260920'),
      isNull,
    );
  });

  test('GithubReleaseChecker sends Bearer token for private repos', () async {
    final httpClient = MockClient((request) async {
      expect(request.headers['Authorization'], 'Bearer ghp_test');
      return http.Response('[]', 200);
    });
    final checker = GithubReleaseChecker(
      httpClient: httpClient,
      token: 'ghp_test',
    );
    expect(await checker.latestNewerThan('0.2.1'), isNull);
  });

  test('GithubReleaseChecker surfaces a newer date-tagged APK build', () async {
    final httpClient = MockClient((request) async {
      return http.Response(
        jsonEncode([
          {
            'tag_name': 'operator-app-apk-20260921',
            'html_url': 'https://example/next',
            'published_at': '2026-09-21T12:00:00Z',
            'assets': [
              {
                'name': 'app-release.apk',
                'browser_download_url': 'https://example/next.apk',
              },
            ],
          },
        ]),
        200,
      );
    });

    final checker = GithubReleaseChecker(httpClient: httpClient);
    final release = await checker.latestNewerThan(
      '0.2.1',
      buildStamp: '20260920',
    );
    expect(release?.version, '2026.09.21');
    expect(release?.apkUrl, 'https://example/next.apk');
  });

  test('PublicManifestUpdateChecker reads donnernet-style latest.json', () async {
    final httpClient = MockClient((request) async {
      expect(request.url.path, '/operator-app/latest.json');
      return http.Response(
        jsonEncode({
          'version': '0.3.0',
          'buildStamp': '20260921',
          'tag': 'operator-app-0.3.0',
          'apkUrl': 'https://donnernet.de/operator-app/operator_app_release.apk',
          'windowsUrl':
              'https://donnernet.de/operator-app/operator_app_windows.zip',
          'htmlUrl': 'https://donnernet.de/operator-app/',
          'notes': 'test',
        }),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    final checker = PublicManifestUpdateChecker(
      httpClient: httpClient,
      manifestUrl: Uri.parse('https://donnernet.de/operator-app/latest.json'),
    );
    final release = await checker.latestNewerThan('0.2.2');
    expect(release?.version, '0.3.0');
    expect(release?.apkUrl, contains('operator_app_release.apk'));
    expect(release?.windowsUrl, contains('operator_app_windows.zip'));
    expect(
      await checker.latestNewerThan('0.3.0', buildStamp: '20260921'),
      isNull,
    );
  });

  test('PublicManifestUpdateChecker rejects SPA HTML fallback', () async {
    final httpClient = MockClient((request) async {
      return http.Response(
        '<!doctype html><html></html>',
        200,
        headers: {'content-type': 'text/html'},
      );
    });
    final checker = PublicManifestUpdateChecker(
      httpClient: httpClient,
      manifestUrl: Uri.parse('https://donnernet.de/operator-app/latest.json'),
    );
    expect(checker.latestNewerThan('0.2.2'), throwsA(isA<StateError>()));
  });

  test('CascadingUpdateChecker prefers the public manifest', () async {
    final httpClient = MockClient((request) async {
      if (request.url.host.contains('donnernet')) {
        return http.Response(
          jsonEncode({
            'version': '0.9.0',
            'apkUrl': 'https://donnernet.de/operator-app/x.apk',
            'htmlUrl': 'https://donnernet.de/operator-app/',
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      }
      return http.Response('[]', 200);
    });
    final checker = CascadingUpdateChecker(httpClient: httpClient);
    final release = await checker.latestNewerThan('0.2.2');
    expect(release?.version, '0.9.0');
  });
}
