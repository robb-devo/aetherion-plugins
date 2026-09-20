import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/crafty/mock_crafty_client.dart';
import 'package:operator_app/data/account_store.dart';
import 'package:operator_app/data/crafty_secrets.dart';
import 'package:operator_app/data/github_token_store.dart';
import 'package:operator_app/data/operator_account.dart';
import 'package:operator_app/data/operator_capabilities.dart';
import 'package:operator_app/data/pin.dart';
import 'package:operator_app/data/session_store.dart';
import 'package:operator_app/state/operator_session.dart';
import 'package:operator_app/updates/update_checker.dart';

OperatorSession _session({
  AccountPersistence? persistence,
  SessionStore? sessionStore,
  CraftySecrets? secrets,
}) {
  return OperatorSession(
    persistence:
        persistence ??
        MemoryAccountPersistence(seed: [OperatorAccount.seedOperator()]),
    sessionStore: sessionStore ?? MemorySessionStore(),
    secrets: secrets ?? MemoryCraftySecrets(),
    githubTokens: MemoryGithubTokenStore(),
    crafty: MockCraftyClient(jitter: false),
    updates: const NoopUpdateChecker(),
  );
}

OperatorAccount _byName(OperatorSession session, String name) =>
    session.accounts.singleWhere((a) => a.name == name);

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

  test('seed Lime stores citrus PIN hash', () {
    final seed = OperatorAccount.seedLime();
    expect(seed.id, 'seed-lime');
    expect(seed.name, 'Lime');
    expect(seed.hasPin, isTrue);
    expect(seed.pinHash, hashPin('citrus'));
    expect(seed.pinHash!.contains('citrus'), isFalse);
    expect(seed.checkPin('citrus'), isTrue);
    expect(seed.checkPin('04206951'), isFalse);
    expect(seed.role, OperatorRole.observer);
  });

  test('bootstrap ensures Operator and Lime coexist', () async {
    final session = _session(
      persistence: MemoryAccountPersistence(
        seed: [OperatorAccount.seedOperator()],
      ),
    );
    await session.bootstrap();
    expect(session.accounts.map((a) => a.name), containsAll(['Operator', 'Lime']));
    expect(_byName(session, 'Lime').checkPin('citrus'), isTrue);
  });

  test('bootstrap hashes a legacy seed Operator that had no PIN', () async {
    final session = _session(
      persistence: MemoryAccountPersistence(
        seed: [
          OperatorAccount(
            id: 'seed-operator',
            name: 'Operator',
            createdAt: DateTime.utc(2026, 1, 1),
          ),
        ],
      ),
    );
    await session.bootstrap();
    final op = _byName(session, 'Operator');
    expect(op.hasPin, isTrue);
    expect(await session.signIn(op), 'pin');
    expect(await session.signIn(op, pin: '04206951'), isNull);
  });

  test('session add/remove names and optional PIN', () async {
    final session = _session();
    await session.bootstrap();
    final op = _byName(session, 'Operator');
    expect(op.hasPin, isTrue);
    expect(await session.signIn(op), 'pin');
    expect(await session.signIn(op, pin: '04206951'), isNull);
    await session.signOut();

    expect(await session.addAccount(name: 'bad name'), 'invalid');
    expect(await session.addAccount(name: 'Ledger', pin: '1111'), isNull);
    expect(session.accounts.where((a) => a.name == 'Ledger').single.hasPin, isTrue);

    expect(await session.signIn(session.accounts.last), 'pin');
    expect(await session.signIn(session.accounts.last, pin: '1111'), isNull);
    expect(session.current?.name, 'Ledger');

    await session.removeAccount(session.current!.id);
    expect(session.current, isNull);
    expect(session.accounts.any((a) => a.name == 'Ledger'), isFalse);
  });

  test('session stays signed in across bootstrap until sign-out', () async {
    final persistence = MemoryAccountPersistence(
      seed: [OperatorAccount.seedOperator()],
    );
    final store = MemorySessionStore();
    final secrets = MemoryCraftySecrets();

    final first = _session(
      persistence: persistence,
      sessionStore: store,
      secrets: secrets,
    );
    await first.bootstrap();
    expect(first.current, isNull);
    expect(
      await first.signIn(_byName(first, 'Operator'), pin: '04206951'),
      isNull,
    );

    final restored = _session(
      persistence: persistence,
      sessionStore: store,
      secrets: secrets,
    );
    await restored.bootstrap();
    expect(restored.current?.name, 'Operator');

    await restored.signOut();
    final afterSignOut = _session(
      persistence: persistence,
      sessionStore: store,
      secrets: secrets,
    );
    await afterSignOut.bootstrap();
    expect(afterSignOut.current, isNull);
  });

  test('mock Crafty feed exposes Hub / mmo backends', () async {
    final client = MockCraftyClient(jitter: false);
    final snap = await client.fetchNetwork();
    expect(snap.mock, isTrue);
    expect(snap.servers.map((s) => s.id), containsAll(['velocity', 'hub', 'mmo-r', 'mmo-d', 'mmo-c']));
    expect(snap.servers.firstWhere((s) => s.id == 'mmo-c').online, isFalse);
    expect(snap.allPlayers, isNotEmpty);

    final cmd = await client.sendCommand(serverId: 'hub', command: 'list');
    expect(cmd.ok, isTrue);
    expect(cmd.message, contains('players online'));
    expect(cmd.message, contains('Nova'));
  });

  test('console is capped at 200 lines', () async {
    final session = _session();
    await session.bootstrap();
    await session.signIn(_byName(session, 'Operator'), pin: '04206951');
    for (var i = 0; i < 220; i++) {
      await session.submitCommand('echo $i');
    }
    expect(session.console.length, lessThanOrEqualTo(200));
  });
}
