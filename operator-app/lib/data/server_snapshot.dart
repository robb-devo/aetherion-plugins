enum ServerRole { proxy, hub, capital, dungeons, ashes }

class PlayerPresence {
  const PlayerPresence({
    required this.name,
    required this.serverId,
    this.serverName,
  });

  final String name;
  final String serverId;
  final String? serverName;

  @override
  bool operator ==(Object other) =>
      other is PlayerPresence &&
      other.name == name &&
      other.serverId == serverId;

  @override
  int get hashCode => Object.hash(name, serverId);
}

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
    this.cpuPercent,
    this.memPercent,
    this.worldName,
    this.version,
    this.onlinePlayers = const [],
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

  final double? cpuPercent;
  final double? memPercent;
  final String? worldName;
  final String? version;
  final List<PlayerPresence> onlinePlayers;

  ServerSnapshot copyWith({
    bool? online,
    int? players,
    double? tps,
    int? maxPlayers,
    double? cpuPercent,
    double? memPercent,
    String? worldName,
    String? version,
    List<PlayerPresence>? onlinePlayers,
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
      cpuPercent: cpuPercent ?? this.cpuPercent,
      memPercent: memPercent ?? this.memPercent,
      worldName: worldName ?? this.worldName,
      version: version ?? this.version,
      onlinePlayers: onlinePlayers ?? this.onlinePlayers,
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
  int get playerTotal => servers
      .where((s) => s.role != ServerRole.proxy)
      .fold(0, (a, s) => a + s.players);

  List<PlayerPresence> get allPlayers {
    final out = <PlayerPresence>[];
    for (final s in servers) {
      if (s.role == ServerRole.proxy) continue;
      out.addAll(s.onlinePlayers);
    }
    out.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return out;
  }

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
