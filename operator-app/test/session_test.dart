import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/crafty/mock_crafty_client.dart';
import 'package:operator_app/data/account_store.dart';
import 'package:operator_app/data/pin.dart';
import 'package:operator_app/state/operator_session.dart';

void main() {
  test('PIN hash is stable and not stored in the clear', () {
    final a = hashPin('2468');
    final b = hashPin('2468');
    expect(a, b);
    expect(a.contains('2468'), isFalse);
    expect(pinMatches(pin: '2468', hash: a), isTrue);
    expect(pinMatches(pin: '0000', hash: a), isFalse);
  });

  test('session add/remove names and optional PIN', () async {
    final session = OperatorSession(
      persistence: MemoryAccountPersistence(seed: []),
      crafty: MockCraftyClient(jitter: false),
    );
    await session.bootstrap();
    expect(session.accounts.single.name, 'Operator');

    expect(await session.addAccount(name: 'bad name'), 'invalid');
    expect(await session.addAccount(name: 'Ledger', pin: '1111'), isNull);
    expect(session.accounts.where((a) => a.name == 'Ledger').single.hasPin, isTrue);

    expect(session.signIn(session.accounts.last), 'pin');
    expect(session.signIn(session.accounts.last, pin: '1111'), isNull);
    expect(session.current?.name, 'Ledger');

    await session.removeAccount(session.current!.id);
    expect(session.current, isNull);
    expect(session.accounts.any((a) => a.name == 'Ledger'), isFalse);
  });

  test('mock Crafty feed exposes Hub / mmo backends', () async {
    final client = MockCraftyClient(jitter: false);
    final snap = await client.fetchNetwork();
    expect(snap.mock, isTrue);
    expect(snap.servers.map((s) => s.id), containsAll(['velocity', 'hub', 'mmo-r', 'mmo-d', 'mmo-c']));
    expect(snap.servers.firstWhere((s) => s.id == 'mmo-c').online, isFalse);

    final cmd = await client.sendCommand(serverId: 'hub', command: 'list');
    expect(cmd.ok, isTrue);
    expect(cmd.message, contains('list'));
  });
}
