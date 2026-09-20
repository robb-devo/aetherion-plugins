import 'dart:convert';

import 'package:http/http.dart' as http;

import '../data/server_snapshot.dart';
import 'crafty_client.dart';
import 'crafty_config.dart';

/// Crafty Controller v2 client.
///
/// Endpoints (Crafty 4):
/// - `GET  {base}/api/v2/servers`
/// - `GET  {base}/api/v2/servers/{id}/stats`
/// - `GET  {base}/api/v2/servers/{id}/logs`
/// - `POST {base}/api/v2/servers/{id}/stdin`
/// - `POST {base}/api/v2/servers/{id}/action/restart_server` (also `/restart`)
///
/// Auth: `Authorization: Bearer <token>`.
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
    final normalized = path.startsWith('/') ? path.substring(1) : path;
    return base.resolve(normalized);
  }

  Future<http.Response> _get(String path) {
    return _http.get(_uri(path), headers: _headers);
  }

  Future<http.Response> _postJson(String path, [Map<String, dynamic>? body]) {
    return _http.post(
      _uri(path),
      headers: _headers,
      body: jsonEncode(body ?? const <String, dynamic>{}),
    );
  }

  @override
  Future<NetworkSnapshot> fetchNetwork() async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();

    final response = await _get('/api/v2/servers');
    if (response.statusCode >= 400) {
      throw StateError(
        'Crafty servers ${response.statusCode}: ${response.body}',
      );
    }
    final list = _asList(jsonDecode(response.body));
    final servers = <ServerSnapshot>[];
    for (final raw in list) {
      if (raw is! Map) continue;
      var server = _mapServer(Map<String, dynamic>.from(raw));
      try {
        final stats = await _get('/api/v2/servers/${server.id}/stats');
        if (stats.statusCode < 400) {
          server = _mergeStats(server, jsonDecode(stats.body));
        }
      } catch (_) {}
      servers.add(server);
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
    final trimmed = command.trim();
    if (trimmed.isEmpty) return CommandResult.error('Empty command');

    var response = await _postJson('/api/v2/servers/$serverId/stdin', {
      'command': trimmed,
    });
    if (response.statusCode >= 400) {
      response = await _http.post(
        _uri('/api/v2/servers/$serverId/stdin'),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'text/plain',
          'Authorization': 'Bearer ${config.apiToken}',
        },
        body: trimmed,
      );
    }
    if (response.statusCode >= 400) {
      return CommandResult.error(
        'Crafty stdin ${response.statusCode}: ${response.body}',
      );
    }
    return CommandResult.ok('sent → $serverId');
  }

  @override
  Future<CommandResult> softRestart({required String serverId}) {
    return _action(serverId, const ['restart_server', 'restart']);
  }

  @override
  Future<CommandResult> startServer({required String serverId}) {
    return _action(serverId, const ['start_server', 'start']);
  }

  @override
  Future<CommandResult> stopServer({required String serverId}) {
    return _action(serverId, const ['stop_server', 'stop']);
  }

  Future<CommandResult> _action(String serverId, List<String> names) async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();
    http.Response? last;
    for (final name in names) {
      last = await _postJson('/api/v2/servers/$serverId/action/$name');
      if (last.statusCode < 400) {
        return CommandResult.ok('$name queued → $serverId');
      }
    }
    return CommandResult.error(
      'Crafty action ${last?.statusCode}: ${last?.body}',
    );
  }

  @override
  Future<List<String>> fetchLogs({
    required String serverId,
    int lines = 80,
  }) async {
    if (!config.isConfigured) throw CraftyNotConfiguredException();
    final response = await _get('/api/v2/servers/$serverId/logs');
    if (response.statusCode >= 400) {
      throw StateError('Crafty logs ${response.statusCode}: ${response.body}');
    }
    final parsed = _parseLogBody(jsonDecode(response.body));
    if (parsed.length <= lines) return parsed;
    return parsed.sublist(parsed.length - lines);
  }

  List<String> _parseLogBody(dynamic decoded) {
    if (decoded is List) {
      return decoded.map((e) => '$e').where((s) => s.trim().isNotEmpty).toList();
    }
    if (decoded is Map) {
      final data = decoded['data'];
      if (data is List) {
        return data.map((e) => '$e').where((s) => s.trim().isNotEmpty).toList();
      }
      if (data is String) {
        return data.split(RegExp(r'\r?\n')).where((s) => s.isNotEmpty).toList();
      }
      if (data is Map && data['logs'] != null) {
        return _parseLogBody(data['logs']);
      }
    }
    if (decoded is String) {
      return decoded.split(RegExp(r'\r?\n')).where((s) => s.isNotEmpty).toList();
    }
    return const [];
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
        '${raw['status']}'.toLowerCase() == 'running' ||
        '${raw['status']}'.toLowerCase() == 'online';
    final players = _asInt(
      raw['player_count'] ??
          (raw['online'] is bool ? 0 : raw['online']) ??
          raw['players'],
    );
    final max = _asInt(raw['max'] ?? raw['max_players'] ?? 0);
    final tps = _asDouble(raw['tps'] ?? raw['mspt']);
    return ServerSnapshot(
      id: id,
      displayName: name,
      role: guessRole(name),
      online: running,
      players: players,
      maxPlayers: max,
      tps: tps,
      craftyId: id,
    );
  }

  ServerSnapshot _mergeStats(ServerSnapshot server, dynamic decoded) {
    Map<String, dynamic>? data;
    if (decoded is Map<String, dynamic>) {
      final inner = decoded['data'];
      if (inner is Map) {
        data = Map<String, dynamic>.from(inner);
      } else {
        data = decoded;
      }
    }
    if (data == null) return server;
    final stats = data['stats'] is Map
        ? Map<String, dynamic>.from(data['stats'] as Map)
        : data;
    final onlineFlag =
        data['running'] == true ||
        stats['running'] == true ||
        '${data['status']}'.toLowerCase() == 'online' ||
        '${stats['status']}'.toLowerCase() == 'online' ||
        '${data['status']}'.toLowerCase() == 'running';
    final players = _asInt(
      data['online'] ??
          data['players'] ??
          data['player_count'] ??
          stats['online'] ??
          stats['players'],
    );
    final max = _asInt(data['max'] ?? data['max_players'] ?? server.maxPlayers);
    final tps = _asDouble(data['tps'] ?? stats['tps'] ?? server.tps);
    return server.copyWith(
      online: onlineFlag || server.online,
      players: players,
      tps: tps,
      maxPlayers: max == 0 ? server.maxPlayers : max,
    );
  }

  int _asInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is bool) return 0;
    return int.tryParse('$value') ?? 0;
  }

  double _asDouble(dynamic value) {
    if (value is double) return value;
    if (value is num) return value.toDouble();
    return double.tryParse('$value') ?? 0;
  }
}

ServerRole guessRole(String name) {
  final n = name.toLowerCase();
  if (n.contains('hub')) return ServerRole.hub;
  if (n.contains('mmo-d') || n.contains('dungeon')) return ServerRole.dungeons;
  if (n.contains('mmo-c') || n.contains('ashes')) return ServerRole.ashes;
  if (n.contains('proxy') || n.contains('velocity')) return ServerRole.proxy;
  return ServerRole.capital;
}
