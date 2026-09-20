import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:operator_app/crafty/crafty_config.dart';
import 'package:operator_app/crafty/http_crafty_client.dart';

CraftyConfig get _config => CraftyConfig(
  enabled: true,
  baseUrl: Uri.parse('https://crafty.example/'),
  apiToken: 'test-token',
);

void main() {
  test('HttpCraftyClient refuses to run without config', () {
    final client = HttpCraftyClient(config: CraftyConfig.unset);
    expect(client.fetchNetwork(), throwsA(isA<CraftyNotConfiguredException>()));
  });

  test('HttpCraftyClient maps a Controller-style payload', () async {
    final httpClient = MockClient((request) async {
      expect(request.headers['Authorization'], 'Bearer test-token');
      if (request.url.path == '/api/v2/servers') {
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
      }
      if (request.url.path == '/api/v2/servers/abc/stats') {
        return http.Response(
          jsonEncode({
            'data': {'running': true, 'online': 3, 'max': 40, 'tps': 19.9},
          }),
          200,
        );
      }
      return http.Response('nope', 404);
    });

    final client = HttpCraftyClient(config: _config, httpClient: httpClient);

    final snap = await client.fetchNetwork();
    expect(snap.mock, isFalse);
    expect(snap.servers, hasLength(1));
    expect(snap.servers.single.displayName, 'Hub');
    expect(snap.servers.single.online, isTrue);
    expect(snap.servers.single.players, 3);
  });

  test('stdin, restart, and logs hit Crafty v2 paths', () async {
    final seen = <String>[];
    final httpClient = MockClient((request) async {
      seen.add('${request.method} ${request.url.path}');
      if (request.url.path.endsWith('/stdin')) {
        expect(jsonDecode(request.body)['command'], 'list');
        return http.Response('{}', 200);
      }
      if (request.url.path.endsWith('/action/restart_server')) {
        return http.Response('{}', 200);
      }
      if (request.url.path.endsWith('/logs')) {
        return http.Response(
          jsonEncode({
            'data': ['line-a', 'line-b'],
          }),
          200,
        );
      }
      return http.Response('nope', 404);
    });

    final client = HttpCraftyClient(config: _config, httpClient: httpClient);
    final cmd = await client.sendCommand(serverId: 'hub', command: 'list');
    expect(cmd.ok, isTrue);
    final restart = await client.softRestart(serverId: 'hub');
    expect(restart.ok, isTrue);
    expect(restart.message, contains('restart_server'));
    final logs = await client.fetchLogs(serverId: 'hub');
    expect(logs, ['line-a', 'line-b']);
    expect(seen, contains('POST /api/v2/servers/hub/stdin'));
    expect(seen, contains('POST /api/v2/servers/hub/action/restart_server'));
    expect(seen, contains('GET /api/v2/servers/hub/logs'));
  });
}
