import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:operator_app/crafty/crafty_config.dart';
import 'package:operator_app/crafty/http_crafty_client.dart';

void main() {
  test('HttpCraftyClient refuses to run without config', () {
    final client = HttpCraftyClient(config: CraftyConfig.unset);
    expect(client.fetchNetwork(), throwsA(isA<CraftyNotConfiguredException>()));
  });

  test('HttpCraftyClient maps a Controller-style payload', () async {
    final httpClient = MockClient((request) async {
      expect(request.headers['Authorization'], 'Bearer test-token');
      expect(request.url.path, '/api/v2/servers');
      return http.Response(
        jsonEncode({
          'data': [
            {
              'id': 'abc',
              'server_name': 'Hub',
              'running': true,
              'players': 3,
              'max_players': 40,
              'tps': 19.9,
            },
          ],
        }),
        200,
      );
    });

    final client = HttpCraftyClient(
      config: CraftyConfig(
        enabled: true,
        baseUrl: Uri.parse('https://crafty.example'),
        apiToken: 'test-token',
      ),
      httpClient: httpClient,
    );

    final snap = await client.fetchNetwork();
    expect(snap.mock, isFalse);
    expect(snap.servers, hasLength(1));
    expect(snap.servers.single.displayName, 'Hub');
    expect(snap.servers.single.online, isTrue);
    expect(snap.servers.single.players, 3);
  });
}
