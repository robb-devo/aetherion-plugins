import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/crafty/mock_crafty_client.dart';
import 'package:operator_app/data/account_store.dart';
import 'package:operator_app/data/operator_account.dart';
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

  test('seed Operator stores a hash, not a clear PIN', () {
    final seed = OperatorAccount.seedOperator();
    expect(seed.name, 'Operator');
    expect(seed.hasPin, isTrue);
    expect(seed.pinHash, hashPin('04206951'));
    expect(seed.pinHash!.contains('04206951'), isFalse);
    expect(seed.checkPin('04206951'), isTrue);
    expect(seed.checkPin('0000'), isFalse);
  });

  test('bootstrap hashes a legacy seed Operator that had no PIN', () async {
    final session = OperatorSession(
      persistence: MemoryAccountPersistence(
        seed: [
          OperatorAccount(
            id: 'seed-operator',
            name: 'Operator',
            createdAt: DateTime.utc(2026, 1, 1),
          ),
        ],
      ),
      crafty: MockCraftyClient(jitter: false),
    );
    await session.bootstrap();
    expect(session.accounts.single.hasPin, isTrue);
    expect(session.signIn(session.accounts.single), 'pin');
    expect(session.signIn(session.accounts.single, pin: '04206951'), isNull);
  });

  test('session add/remove names and optional PIN', () async {
    final session = OperatorSession(
      persistence: MemoryAccountPersistence(seed: []),
      crafty: MockCraftyClient(jitter: false),
    );
    await session.bootstrap();
    expect(session.accounts.single.name, 'Operator');
    expect(session.accounts.single.hasPin, isTrue);
    expect(session.signIn(session.accounts.single), 'pin');
    expect(session.signIn(session.accounts.single, pin: '04206951'), isNull);
    session.signOut();

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
