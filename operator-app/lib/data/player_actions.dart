/// Operator actions against a Minecraft player via Crafty stdin.
enum PlayerAction {
  kick,
  ban,
  pardon,
  op,
  deop,
  gamemodeSurvival,
  gamemodeCreative,
  gamemodeAdventure,
  gamemodeSpectator,
  msg,
  kill,
  clear,
  whitelistAdd,
  whitelistRemove,
  tpToPlayer,
  tpCoords,
}

extension PlayerActionX on PlayerAction {
  bool get needsConfirm => switch (this) {
    PlayerAction.kick ||
    PlayerAction.ban ||
    PlayerAction.kill ||
    PlayerAction.clear ||
    PlayerAction.deop => true,
    _ => false,
  };

  bool get needsExtraInput => switch (this) {
    PlayerAction.kick ||
    PlayerAction.ban ||
    PlayerAction.msg ||
    PlayerAction.tpToPlayer ||
    PlayerAction.tpCoords => true,
    _ => false,
  };

  String buildCommand(
    String player, {
    String? reason,
    String? targetPlayer,
    String? coords,
  }) {
    final name = player.trim();
    return switch (this) {
      PlayerAction.kick => reason == null || reason.isEmpty
          ? 'kick $name'
          : 'kick $name $reason',
      PlayerAction.ban => reason == null || reason.isEmpty
          ? 'ban $name'
          : 'ban $name $reason',
      PlayerAction.pardon => 'pardon $name',
      PlayerAction.op => 'op $name',
      PlayerAction.deop => 'deop $name',
      PlayerAction.gamemodeSurvival => 'gamemode survival $name',
      PlayerAction.gamemodeCreative => 'gamemode creative $name',
      PlayerAction.gamemodeAdventure => 'gamemode adventure $name',
      PlayerAction.gamemodeSpectator => 'gamemode spectator $name',
      PlayerAction.msg => 'msg $name ${reason ?? ''}'.trimRight(),
      PlayerAction.kill => 'kill $name',
      PlayerAction.clear => 'clear $name',
      PlayerAction.whitelistAdd => 'whitelist add $name',
      PlayerAction.whitelistRemove => 'whitelist remove $name',
      PlayerAction.tpToPlayer => 'tp $name ${targetPlayer ?? ''}'.trimRight(),
      PlayerAction.tpCoords => 'tp $name ${coords ?? '0 64 0'}',
    };
  }
}
