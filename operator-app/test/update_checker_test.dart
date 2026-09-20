import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:operator_app/updates/update_checker.dart';

void main() {
  test('parses operator-app tags with optional v prefix', () {
    expect(parseOperatorReleaseTag('operator-app-1.2.3'), '1.2.3');
    expect(parseOperatorReleaseTag('operator-app-v0.2.0'), '0.2.0');
    expect(parseOperatorReleaseTag('refs/tags/operator-app-0.3.0'), '0.3.0');
    expect(parseOperatorReleaseTag('v1.0.0'), isNull);
    expect(parseOperatorReleaseTag('plugin-1.0.0'), isNull);
  });

  test('isVersionNewer compares dotted triples', () {
    expect(isVersionNewer('0.2.0', '0.1.0'), isTrue);
    expect(isVersionNewer('0.2.0', '0.2.0'), isFalse);
    expect(isVersionNewer('0.1.9', '0.2.0'), isFalse);
    expect(isVersionNewer('1.0.0', '0.9.9'), isTrue);
  });

  test('GithubReleaseChecker picks the newest matching APK release', () async {
    final httpClient = MockClient((request) async {
      expect(request.url.path, '/repos/robb-devo/aetherion-plugins/releases');
      return http.Response(
        jsonEncode([
          {
            'tag_name': 'unrelated-1.0.0',
            'html_url': 'https://example/unrelated',
            'assets': [],
          },
          {
            'tag_name': 'operator-app-0.1.0',
            'html_url': 'https://example/old',
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
    final release = await checker.latestNewerThan('0.2.0');
    expect(release, isNotNull);
    expect(release!.version, '0.3.0');
    expect(release.apkUrl, 'https://example/new.apk');
    expect(await checker.latestNewerThan('0.3.0'), isNull);
  });
}
