import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

http.Client createHttpClient({required bool allowInsecureTls}) {
  if (!allowInsecureTls) return http.Client();
  final inner = HttpClient()
    ..badCertificateCallback = (cert, host, port) => true;
  return IOClient(inner);
}
