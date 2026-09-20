import 'player_actions.dart';

/// Access tier for operator accounts.
///
/// - [full]: everything (seed Operator).
/// - [observer]: browse freely + mild world/chat tools (seed Lime).
enum OperatorRole { full, observer }

abstract final class OperatorCapabilities {
  /// Mild stdin prefixes Lime may send.
  static const observerCommandPrefixes = <String>[
    'time set',
    'weather ',
    'weather',
    'list',
    'say ',
    'say',
    'title ',
    'title',
    'msg ',
    'tell ',
    'difficulty ',
    'difficulty',
    'gamerule keepinventory',
    'tps',
    'help',
  ];

  static bool allowsPower(OperatorRole role) => role == OperatorRole.full;

  static bool allowsTeamMutate(OperatorRole role) => role == OperatorRole.full;

  static bool allowsCraftySettings(OperatorRole role) => true;

  static bool allowsPlayerAction(OperatorRole role, PlayerAction action) {
    if (role == OperatorRole.full) return true;
    return switch (action) {
      PlayerAction.msg => true,
      _ => false,
    };
  }

  /// Free-form / tool stdin gate.
  static bool allowsCommand(OperatorRole role, String command) {
    if (role == OperatorRole.full) return true;
    final c = command.trim().toLowerCase();
    if (c.isEmpty) return false;
    for (final prefix in observerCommandPrefixes) {
      if (c == prefix || c.startsWith(prefix)) return true;
    }
    return false;
  }

  static bool allowsToolCommand(OperatorRole role, String command) =>
      allowsCommand(role, command);
}
