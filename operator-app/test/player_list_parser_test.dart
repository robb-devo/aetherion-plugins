import 'package:flutter_test/flutter_test.dart';
import 'package:operator_app/crafty/player_list_parser.dart';

void main() {
  test('parsePlayerNames handles list, string, and map entries', () {
    expect(parsePlayerNames(['Alice', 'Bob']), ['Alice', 'Bob']);
    expect(parsePlayerNames('["Kai","Mira"]'), ['Kai', 'Mira']);
    expect(
      parsePlayerNames('There are 2 of a max of 40 players online: Nova, Echo'),
      ['Nova', 'Echo'],
    );
    expect(
      parsePlayerNames([
        {'name': 'Riven'},
        {'username': 'Ash'},
      ]),
      ['Riven', 'Ash'],
    );
    expect(parsePlayerNames('None'), isEmpty);
    expect(parsePlayerNames(null), isEmpty);
  });

  test('parsePlayerNamesFromLogs finds the latest list line', () {
    final names = parsePlayerNamesFromLogs([
      'Starting server...',
      'There are 3 of a max of 40 players online: Nova, Kai, Mira',
      'Done!',
    ]);
    expect(names, ['Nova', 'Kai', 'Mira']);
  });

  test('playersOnServer builds presence rows', () {
    final rows = playersOnServer(
      serverId: 'hub',
      serverName: 'Hub',
      names: ['Nova', ''],
    );
    expect(rows, hasLength(1));
    expect(rows.single.name, 'Nova');
    expect(rows.single.serverId, 'hub');
  });
}
