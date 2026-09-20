import 'dart:math';

import '../data/server_snapshot.dart';
import 'crafty_client.dart';

/// Local stand-in for Crafty until API tokens exist. Deterministic layout,
/// optional jitter so Refresh feels alive in debug.
class MockCraftyClient implements CraftyClient {
  MockCraftyClient({this.jitter = true, Random? random})
    : _random = random ?? Random(7);

  final bool jitter;
  final Random _random;

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

    int players(int base) {
      if (!jitter) return base;
      return max(0, base + _random.nextInt(3) - 1);
    }

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
          players: players(12),
          maxPlayers: 80,
          tps: 0,
        ),
        ServerSnapshot(
          id: 'hub',
          displayName: 'Hub',
          role: ServerRole.hub,
          online: true,
          players: players(6),
          maxPlayers: 40,
          tps: tps(19.94),
        ),
        ServerSnapshot(
          id: 'mmo-r',
          displayName: 'mmo-r',
          role: ServerRole.capital,
          online: true,
          players: players(4),
          maxPlayers: 40,
          tps: tps(19.82),
        ),
        ServerSnapshot(
          id: 'mmo-d',
          displayName: 'mmo-d',
          role: ServerRole.dungeons,
          online: true,
          players: players(2),
          maxPlayers: 20,
          tps: tps(20.00),
        ),
        ServerSnapshot(
          id: 'mmo-c',
          displayName: 'mmo-c',
          role: ServerRole.ashes,
          online: false,
          players: 0,
          maxPlayers: 20,
          tps: 0,
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
      '[mock:$serverId] There are 3 of a max of 40 players online',
    ].take(lines).toList();
  }
}
