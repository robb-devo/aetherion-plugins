import '../data/server_snapshot.dart';
import 'crafty_config.dart';
import 'http_crafty_client.dart';
import 'mock_crafty_client.dart';

/// Extension point for Crafty Controller.
///
/// MVP ships [MockCraftyClient]. When [CraftyConfig.isConfigured] is true,
/// [createCraftyClient] returns [HttpCraftyClient] instead.
abstract class CraftyClient {
  bool get mock;

  Future<NetworkSnapshot> fetchNetwork();

  Future<CommandResult> sendCommand({
    required String serverId,
    required String command,
  });

  Future<CommandResult> softRestart({required String serverId});
}

CraftyClient createCraftyClient([CraftyConfig config = CraftyConfig.unset]) {
  if (config.isConfigured) {
    return HttpCraftyClient(config: config);
  }
  return MockCraftyClient();
}
