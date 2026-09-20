import '../data/server_snapshot.dart';

/// Parse online player names from Crafty stats payloads or `/list` log lines.
List<String> parsePlayerNames(dynamic raw) {
  if (raw == null) return const [];
  if (raw is List) {
    return raw
        .map(_nameFromEntry)
        .where((n) => n.isNotEmpty)
        .toList(growable: false);
  }
  if (raw is String) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty || trimmed == 'None' || trimmed == '[]') {
      return const [];
    }
    // JSON-ish list in a string
    if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
      final inner = trimmed.substring(1, trimmed.length - 1).trim();
      if (inner.isEmpty) return const [];
      return inner
          .split(',')
          .map((s) => s.trim().replaceAll(RegExp(r'''^["']|["']$'''), ''))
          .where((s) => s.isNotEmpty && s != 'None')
          .toList(growable: false);
    }
    // "There are 2 of a max of 40 players online: Alice, Bob"
    final colon = trimmed.indexOf(':');
    if (colon >= 0 && trimmed.toLowerCase().contains('player')) {
      final names = trimmed.substring(colon + 1).trim();
      if (names.isEmpty || names.toLowerCase() == 'none') return const [];
      return names
          .split(RegExp(r'[,;]'))
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .toList(growable: false);
    }
    // Comma-separated plain names
    if (trimmed.contains(',')) {
      return trimmed
          .split(',')
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .toList(growable: false);
    }
    // Single name
    if (RegExp(r'^[A-Za-z0-9_]{1,16}$').hasMatch(trimmed)) {
      return [trimmed];
    }
  }
  return const [];
}

String _nameFromEntry(dynamic e) {
  if (e is String) return e.trim();
  if (e is Map) {
    final name = e['name'] ?? e['player'] ?? e['username'] ?? e['id'];
    return '$name'.trim();
  }
  return '$e'.trim();
}

/// Extract names from recent console / log lines after a `/list`.
List<String> parsePlayerNamesFromLogs(Iterable<String> lines) {
  for (final line in lines.toList().reversed) {
    final names = parsePlayerNames(line);
    if (names.isNotEmpty) return names;
    // Vanilla: "Alice, Bob"
    final m = RegExp(
      r'players online:\s*(.+)$',
      caseSensitive: false,
    ).firstMatch(line);
    if (m != null) {
      final parsed = parsePlayerNames(m.group(1));
      if (parsed.isNotEmpty) return parsed;
    }
  }
  return const [];
}

List<PlayerPresence> playersOnServer({
  required String serverId,
  required String serverName,
  required Iterable<String> names,
}) {
  return [
    for (final n in names)
      if (n.isNotEmpty)
        PlayerPresence(name: n, serverId: serverId, serverName: serverName),
  ];
}
