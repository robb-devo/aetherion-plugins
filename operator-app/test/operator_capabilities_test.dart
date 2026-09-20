import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/data/operator_account.dart';
import 'package:operator_app/data/operator_capabilities.dart';
import 'package:operator_app/data/player_actions.dart';

void main() {
  test('Lime is observer with citrus PIN', () {
    final lime = OperatorAccount.seedLime();
    expect(lime.name, 'Lime');
    expect(lime.role, OperatorRole.observer);
    expect(lime.checkPin('citrus'), isTrue);
    expect(lime.checkPin('wrong'), isFalse);
  });

  test('observer can do mild world/chat commands only', () {
    const role = OperatorRole.observer;
    expect(OperatorCapabilities.allowsCommand(role, 'weather clear'), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'time set day'), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'list'), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'say hello'), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'msg Nova hi'), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'kick Nova'), isFalse);
    expect(OperatorCapabilities.allowsCommand(role, 'ban Nova'), isFalse);
    expect(OperatorCapabilities.allowsCommand(role, 'op Nova'), isFalse);
    expect(OperatorCapabilities.allowsCommand(role, 'save-all'), isFalse);
    expect(OperatorCapabilities.allowsPower(role), isFalse);
    expect(
      OperatorCapabilities.allowsPlayerAction(role, PlayerAction.msg),
      isTrue,
    );
    expect(
      OperatorCapabilities.allowsPlayerAction(role, PlayerAction.kick),
      isFalse,
    );
  });

  test('full role allows power and moderation', () {
    const role = OperatorRole.full;
    expect(OperatorCapabilities.allowsPower(role), isTrue);
    expect(OperatorCapabilities.allowsCommand(role, 'kick Nova'), isTrue);
    expect(
      OperatorCapabilities.allowsPlayerAction(role, PlayerAction.ban),
      isTrue,
    );
  });
}
