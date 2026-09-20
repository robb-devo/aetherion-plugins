import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/app.dart';
import 'package:operator_app/crafty/mock_crafty_client.dart';
import 'package:operator_app/data/account_store.dart';
import 'package:operator_app/data/crafty_secrets.dart';
import 'package:operator_app/data/github_token_store.dart';
import 'package:operator_app/data/operator_account.dart';
import 'package:operator_app/data/session_store.dart';
import 'package:operator_app/state/operator_session.dart';
import 'package:operator_app/updates/update_checker.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _seedPin = '04206951';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  Future<OperatorSession> boot() async {
    final session = OperatorSession(
      persistence: MemoryAccountPersistence(
        seed: [OperatorAccount.seedOperator()],
      ),
      sessionStore: MemorySessionStore(),
      secrets: MemoryCraftySecrets(),
      githubTokens: MemoryGithubTokenStore(),
      crafty: MockCraftyClient(jitter: false),
      updates: const NoopUpdateChecker(),
    );
    await session.bootstrap();
    return session;
  }

  testWidgets('name login opens mock dashboard', (tester) async {
    tester.view.physicalSize = const Size(1400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);

    final session = await boot();
    await tester.pumpWidget(OperatorApp(session: session));
    await tester.pumpAndSettle();

    expect(find.text('AETHERION'), findsWidgets);
    expect(find.byKey(const Key('signin-Operator')), findsOneWidget);

    await tester.tap(find.byKey(const Key('signin-Operator')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('pin-input')), findsOneWidget);

    await tester.enterText(find.byKey(const Key('pin-input')), '0000');
    await tester.tap(find.byKey(const Key('pin-continue')));
    await tester.pumpAndSettle();
    expect(find.text('That PIN does not match.'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('pin-input')), _seedPin);
    await tester.tap(find.byKey(const Key('pin-continue')));
    await tester.pumpAndSettle();

    expect(find.text('Hub'), findsWidgets);
    expect(find.text('mmo-r'), findsOneWidget);
    expect(find.text('Mock data'), findsOneWidget);
    expect(find.textContaining('Signed in as Operator'), findsOneWidget);
  });

  testWidgets('DevKit tools send console commands', (tester) async {
    tester.view.physicalSize = const Size(1400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);

    final session = await boot();
    expect(await session.signIn(OperatorAccount.seedOperator()), 'pin');
    expect(
      await session.signIn(OperatorAccount.seedOperator(), pin: _seedPin),
      isNull,
    );
    await tester.pumpWidget(OperatorApp(session: session));
    await tester.pumpAndSettle();

    await tester.tap(find.text('DevKit'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('List players'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Sent: list'), findsOneWidget);
    expect(session.console.any((l) => l.text.contains('list')), isTrue);
  });

  testWidgets('Dashboard opens server detail', (tester) async {
    tester.view.physicalSize = const Size(1400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);

    final session = await boot();
    expect(
      await session.signIn(OperatorAccount.seedOperator(), pin: _seedPin),
      isNull,
    );
    await tester.pumpWidget(OperatorApp(session: session));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Hub').first);
    await tester.pumpAndSettle();

    expect(find.text('Overview'), findsOneWidget);
    expect(find.text('Players'), findsWidgets);
    expect(find.text('Terminal'), findsOneWidget);
    expect(find.text('Power'), findsWidgets);
    expect(find.text('Tools'), findsOneWidget);
  });

  testWidgets('PIN gate rejects a wrong PIN then accepts the right one', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);

    final session = await boot();
    await session.addAccount(name: 'Staff', pin: '2468');
    await tester.pumpWidget(OperatorApp(session: session));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('signin-Staff')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('pin-input')), '0000');
    await tester.tap(find.byKey(const Key('pin-continue')));
    await tester.pumpAndSettle();
    expect(find.text('That PIN does not match.'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('pin-input')), '2468');
    await tester.tap(find.byKey(const Key('pin-continue')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Signed in as Staff'), findsOneWidget);
  });

  testWidgets('Settings shows mock Crafty and app version', (tester) async {
    tester.view.physicalSize = const Size(1400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);

    final session = await boot();
    expect(
      await session.signIn(OperatorAccount.seedOperator(), pin: _seedPin),
      isNull,
    );
    await tester.pumpWidget(OperatorApp(session: session));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Mock Crafty'), findsOneWidget);
    expect(find.textContaining('Version 0.2.7'), findsOneWidget);
    expect(find.byKey(const Key('crafty-url')), findsOneWidget);
    expect(find.byKey(const Key('check-updates')), findsOneWidget);
  });
}
