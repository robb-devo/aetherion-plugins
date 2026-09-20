import 'dart:convert';

import 'package:crypto/crypto.dart';

String hashPin(String pin) {
  final bytes = utf8.encode('aetherion-operator-pin|$pin');
  return sha256.convert(bytes).toString();
}

bool pinMatches({required String pin, required String hash}) {
  return hashPin(pin) == hash;
}
