import 'dart:math';

import '../data/server_snapshot.dart';
import 'crafty_client.dart';
import 'player_list_parser.dart';

/// Local stand-in for Crafty until API tokens exist. Deterministic layout,
/// optional jitter so Refresh feels alive in debug.
class MockCraftyClient implements CraftyClient {
  MockCraftyClient({this.jitter = true, Random? random})
    : _random = random ?? Random(7);

  final bool jitter;
  final Random _random;

  static const _hubPlayers = ['Nova', 'Kai', 'Mira'];
  static const _capitalPlayers = ['Riven', 'Ash'];
  static const _dungeonPlayers = ['Echo'];

  @override
  bool get mock => true;

  @override
  Future<NetworkSnapshot> fetchNetwork() async {
    await Future<void>.delayed(const Duration(milliseconds: 160));
    double tps(double base) {
      if (!jitter) return base;
      return double.parse(
        (base + (_random.nextDouble() - 0.5) * 0.3).toStringAsFixed(2),
      );
    }

    List<String> names(List<String> base) {
      if (!jitter) return base;
      if (_random.nextBool() && base.isNotEmpty) {
        return base.sublist(0, max(1, base.length - _random.nextInt(2)));
      }
      return base;
    }

    final hub = names(_hubPlayers);
    final capital = names(_capitalPlayers);
    final dungeons = names(_dungeonPlayers);

    return NetworkSnapshot(
      mock: true,
      fetchedAt: DateTime.now(),
      notice:
          'Mock Crafty feed. Wire HttpCraftyClient when the Controller token is ready.',
      servers: [
        ServerSnapshot(
          id: 'velocity',
          displayName: 'Velocity',
          role: ServerRole.proxy,
          online: true,
          players: hub.length + capital.length + dungeons.length,
          maxPlayers: 80,
          tps: 0,
        ),
        ServerSnapshot(
          id: 'hub',
          displayName: 'Hub',
          role: ServerRole.hub,
          online: true,
          players: hub.length,
          maxPlayers: 40,
          tps: tps(19.94),
          cpuPercent: 12.4,
          memPercent: 41.0,
          worldName: 'hub',
          version: '1.21.1',
          onlinePlayers: playersOnServer(
            serverId: 'hub',
            serverName: 'Hub',
            names: hub,
          ),
        ),
        ServerSnapshot(
          id: 'mmo-r',
          displayName: 'mmo-r',
          role: ServerRole.capital,
          online: true,
          players: capital.length,
          maxPlayers: 40,
          tps: tps(19.82),
          cpuPercent: 28.1,
          memPercent: 62.5,
          worldName: 'world',
          version: '1.21.1',
          onlinePlayers: playersOnServer(
            serverId: 'mmo-r',
            serverName: 'mmo-r',
            names: capital,
          ),
        ),
        ServerSnapshot(
          id: 'mmo-d',
          displayName: 'mmo-d',
          role: ServerRole.dungeons,
          online: true,
          players: dungeons.length,
          maxPlayers: 20,
          tps: tps(20.00),
          cpuPercent: 18.0,
          memPercent: 55.0,
          worldName: 'dungeons',
          version: '1.21.1',
          onlinePlayers: playersOnServer(
            serverId: 'mmo-d',
            serverName: 'mmo-d',
            names: dungeons,
          ),
        ),
        ServerSnapshot(
          id: 'mmo-c',
          displayName: 'mmo-c',
          role: ServerRole.ashes,
          online: false,
          players: 0,
          maxPlayers: 20,
          tps: 0,
          onlinePlayers: const [],
        ),
      ],
    );
  }

  @override
  Future<CommandResult> sendCommand({
    required String serverId,
    required String command,
  }) async {
    final trimmed = command.trim();
    if (trimmed.isEmpty) {
      return CommandResult.error('Empty command');
    }
    await Future<void>.delayed(const Duration(milliseconds: 80));
    if (trimmed == 'list' || trimmed.startsWith('list ')) {
      final snap = await fetchNetwork();
      final server = snap.servers.cast<ServerSnapshot?>().firstWhere(
        (s) => s?.id == serverId,
        orElse: () => null,
      );
      final names = server?.onlinePlayers.map((p) => p.name).join(', ') ?? '';
      return CommandResult.ok(
        '[mock:$serverId] There are ${server?.players ?? 0} of a max of '
        '${server?.maxPlayers ?? 0} players online: $names',
      );
    }
    return CommandResult.ok('[mock:$serverId] queued: $trimmed');
  }

  @override
  Future<CommandResult> softRestart({required String serverId}) async {
    await Future<void>.delayed(const Duration(milliseconds: 120));
    return CommandResult.ok('[mock:$serverId] soft restart queued');
  }

  @override
  Future<CommandResult> startServer({required String serverId}) async {
    return CommandResult.ok('[mock:$serverId] start queued');
  }

  @override
  Future<CommandResult> stopServer({required String serverId}) async {
    return CommandResult.ok('[mock:$serverId] stop queued');
  }

  @override
  Future<List<String>> fetchLogs({
    required String serverId,
    int lines = 80,
  }) async {
    return [
      '[mock:$serverId] Done (20.00s)! For help, type "help"',
      '[mock:$serverId] There are 3 of a max of 40 players online: Nova, Kai, Mira',
    ].take(lines).toList();
  }
}
