import 'dart:convert';

import 'package:http/http.dart' as http;

import '../data/server_snapshot.dart';
import 'crafty_client.dart';
import 'crafty_config.dart';

/// Crafty Controller v2 client.
///
/// Expected endpoints (Crafty 4):
/// - `GET  {base}/api/v2/servers`
/// - `GET  {base}/api/v2/servers/{id}/stats`
/// - `POST {base}/api/v2/servers/{id}/action`  body `{ "action": "restart_server" }`
/// - `POST {base}/api/v2/servers/{id}/stdin`   body `{ "command": "..." }`
///
/// Auth: `Authorization: Bearer <CRAFTY_API_TOKEN>`.
/// Field mapping is intentionally loose so a live Controller can land without
/// another UI rewrite. Adjust [_mapServer] when the real JSON is confirmed.
class HttpCraftyClient implements CraftyClient {
  HttpCraftyClient({required this.config, http.Client? httpClient})
    : _http = httpClient ?? http.Client();

  final CraftyConfig config;
  final http.Client _http;

  @override
  bool get mock => false;

  Map<String, String> get _headers => {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ${config.apiToken}',
  };

  Uri _uri(String path) {
    final base = config.baseUrl;
    if (base == null) throw CraftyNotConfiguredException();
    return base.resolve(path.startsWith('/') ? path.substring(1) : path);
  }

  @override
  Future<NetworkSnapshot> fetchNetwork() async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();

    final response = await _http.get(_uri('/api/v2/servers'), headers: _headers);
    if (response.statusCode >= 400) {
      throw StateError('Crafty servers ${response.statusCode}: ${response.body}');
    }
    final decoded = jsonDecode(response.body);
    final list = _asList(decoded);
    final servers = <ServerSnapshot>[];
    for (final raw in list) {
      if (raw is! Map) continue;
      servers.add(_mapServer(Map<String, dynamic>.from(raw)));
    }
    return NetworkSnapshot(
      servers: servers,
      mock: false,
      fetchedAt: DateTime.now(),
    );
  }

  @override
  Future<CommandResult> sendCommand({
    required String serverId,
    required String command,
  }) async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();
    final response = await _http.post(
      _uri('/api/v2/servers/$serverId/stdin'),
      headers: _headers,
      body: jsonEncode({'command': command}),
    );
    if (response.statusCode >= 400) {
      return CommandResult.error(
        'Crafty stdin ${response.statusCode}: ${response.body}',
      );
    }
    return CommandResult.ok('sent → $serverId');
  }

  @override
  Future<CommandResult> softRestart({required String serverId}) async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();
    final response = await _http.post(
      _uri('/api/v2/servers/$serverId/action'),
      headers: _headers,
      body: jsonEncode({'action': 'restart_server'}),
    );
    if (response.statusCode >= 400) {
      return CommandResult.error(
        'Crafty restart ${response.statusCode}: ${response.body}',
      );
    }
    return CommandResult.ok('restart queued → $serverId');
  }

  List<dynamic> _asList(dynamic decoded) {
    if (decoded is List) return decoded;
    if (decoded is Map && decoded['data'] is List) {
      return decoded['data'] as List<dynamic>;
    }
    return const [];
  }

  ServerSnapshot _mapServer(Map<String, dynamic> raw) {
    final id = '${raw['server_id'] ?? raw['id'] ?? raw['uuid'] ?? 'unknown'}';
    final name = '${raw['server_name'] ?? raw['name'] ?? id}';
    final running =
        raw['running'] == true ||
        raw['online'] == true ||
        '${raw['status']}'.toLowerCase() == 'running';
    final players = _asInt(raw['online'] ?? raw['players'] ?? raw['player_count']);
    final max = _asInt(raw['max'] ?? raw['max_players'] ?? 0);
    final tps = _asDouble(raw['tps'] ?? raw['mspt']);
    return ServerSnapshot(
      id: id,
      displayName: name,
      role: _guessRole(name),
      online: running,
      players: players,
      maxPlayers: max,
      tps: tps,
      craftyId: id,
    );
  }

  int _asInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse('$value') ?? 0;
  }

  double _asDouble(dynamic value) {
    if (value is double) return value;
    if (value is num) return value.toDouble();
    return double.tryParse('$value') ?? 0;
  }

  ServerRole _guessRole(String name) {
    final n = name.toLowerCase();
    if (n.contains('hub')) return ServerRole.hub;
    if (n.contains('mmo-d') || n.contains('dungeon')) return ServerRole.dungeons;
    if (n.contains('mmo-c') || n.contains('ashes')) return ServerRole.ashes;
    if (n.contains('proxy') || n.contains('velocity')) return ServerRole.proxy;
    return ServerRole.capital;
  }
}
