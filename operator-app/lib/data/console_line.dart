enum ConsoleKind { command, response, system, error }

class ConsoleLine {
  const ConsoleLine({
    required this.kind,
    required this.text,
    required this.at,
    this.serverId,
  });

  final ConsoleKind kind;
  final String text;
  final DateTime at;
  final String? serverId;
}

class WhitelistNote {
  const WhitelistNote({
    required this.text,
    required this.at,
    required this.author,
  });

  final String text;
  final DateTime at;
  final String author;
}
