import 'pin.dart';

class OperatorAccount {
  const OperatorAccount({
    required this.id,
    required this.name,
    this.pinHash,
    required this.createdAt,
  });

  final String id;
  final String name;
  final String? pinHash;
  final DateTime createdAt;

  bool get hasPin => pinHash != null && pinHash!.isNotEmpty;

  bool checkPin(String pin) {
    if (!hasPin) return true;
    return pinMatches(pin: pin, hash: pinHash!);
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'pinHash': pinHash,
    'createdAt': createdAt.toIso8601String(),
  };

  factory OperatorAccount.fromJson(Map<String, dynamic> json) {
    return OperatorAccount(
      id: json['id'] as String,
      name: json['name'] as String,
      pinHash: json['pinHash'] as String?,
      createdAt:
          DateTime.tryParse(json['createdAt'] as String? ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  static final namePattern = RegExp(r'^[A-Za-z0-9_]{1,16}$');

  static OperatorAccount seedOperator() {
    return OperatorAccount(
      id: 'seed-operator',
      name: 'Operator',
      createdAt: DateTime.utc(2026, 1, 1),
    );
  }
}
