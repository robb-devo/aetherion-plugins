import '../data/server_snapshot.dart';
import 'crafty_config.dart';
import 'http_crafty_client.dart';
import 'http_factory.dart';
import 'mock_crafty_client.dart';

abstract class CraftyClient {
  bool get mock;

  Future<NetworkSnapshot> fetchNetwork();

  Future<CommandResult> sendCommand({
    required String serverId,
    required String command,
  });

  Future<CommandResult> softRestart({required String serverId});

  Future<CommandResult> startServer({required String serverId});

  Future<CommandResult> stopServer({required String serverId});

  Future<List<String>> fetchLogs({required String serverId, int lines = 80});
}

CraftyClient createCraftyClient([CraftyConfig config = CraftyConfig.unset]) {
  if (config.isConfigured) {
    return HttpCraftyClient(
      config: config,
      httpClient: createHttpClient(allowInsecureTls: config.allowInsecureTls),
    );
  }
  return MockCraftyClient(jitter: false);
}
