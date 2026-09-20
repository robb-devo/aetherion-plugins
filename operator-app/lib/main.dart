import 'package:flutter/material.dart';

import 'app.dart';
import 'state/operator_session.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final session = OperatorSession();
  await session.bootstrap();
  runApp(OperatorApp(session: session));
}
