import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/crafty/default_crafty.dart';
import 'package:operator_app/data/crafty_secrets.dart';
import 'package:operator_app/data/account_store.dart';
import 'package:operator_app/data/github_token_store.dart';
import 'package:operator_app/data/operator_account.dart';
import 'package:operator_app/data/session_store.dart';
import 'package:operator_app/crafty/mock_crafty_client.dart';
import 'package:operator_app/state/operator_session.dart';
import 'package:operator_app/updates/update_checker.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('DefaultCrafty token round-trips through vault obfuscation', () {
    final token = DefaultCrafty.apiToken;
    expect(token.startsWith('eyJ'), isTrue);
    expect(token.split('.').length, 3);
    final again = DeviceCraftySecrets.deobfuscatePublic(
      DeviceCraftySecrets.obfuscatePublic(token),
    );
    expect(again, token);
  });

  test('bootstrap seeds Crafty defaults when empty', () async {
    final secrets = MemoryCraftySecrets();
    final session = OperatorSession(
      persistence: MemoryAccountPersistence(
        seed: [OperatorAccount.seedOperator()],
      ),
      sessionStore: MemorySessionStore(),
      secrets: secrets,
      githubTokens: MemoryGithubTokenStore(),
      crafty: MockCraftyClient(jitter: false),
      updates: const NoopUpdateChecker(),
    );
    await session.bootstrap();
    expect(session.craftySettings.isConfigured, isTrue);
    expect(session.craftySettings.baseUrl, DefaultCrafty.baseUrl);
    expect(session.craftySettings.apiToken, DefaultCrafty.apiToken);
  });
}
