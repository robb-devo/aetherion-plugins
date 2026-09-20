enum ServerRole { proxy, hub, capital, dungeons, ashes }

class ServerSnapshot {
  const ServerSnapshot({
    required this.id,
    required this.displayName,
    required this.role,
    required this.online,
    required this.players,
    required this.maxPlayers,
    required this.tps,
    this.craftyId,
  });

  final String id;
  final String displayName;
  final ServerRole role;
  final bool online;
  final int players;
  final int maxPlayers;
  final double tps;

  /// Crafty server UUID when live. Null on mock backends.
  final String? craftyId;

  ServerSnapshot copyWith({
    bool? online,
    int? players,
    double? tps,
    int? maxPlayers,
  }) {
    return ServerSnapshot(
      id: id,
      displayName: displayName,
      role: role,
      online: online ?? this.online,
      players: players ?? this.players,
      maxPlayers: maxPlayers ?? this.maxPlayers,
      tps: tps ?? this.tps,
      craftyId: craftyId,
    );
  }
}

class NetworkSnapshot {
  const NetworkSnapshot({
    required this.servers,
    required this.mock,
    required this.fetchedAt,
    this.notice,
  });

  final List<ServerSnapshot> servers;
  final bool mock;
  final DateTime fetchedAt;
  final String? notice;

  int get onlineCount => servers.where((s) => s.online).length;
  int get playerTotal =>
      servers.where((s) => s.role != ServerRole.proxy).fold(0, (a, s) => a + s.players);
  double get avgTps {
    final live = servers.where((s) => s.online && s.role != ServerRole.proxy);
    if (live.isEmpty) return 0;
    return live.map((s) => s.tps).reduce((a, b) => a + b) / live.length;
  }
}

class CommandResult {
  const CommandResult({required this.ok, required this.message});

  final bool ok;
  final String message;

  factory CommandResult.ok(String message) =>
      CommandResult(ok: true, message: message);
  factory CommandResult.error(String message) =>
      CommandResult(ok: false, message: message);
}
