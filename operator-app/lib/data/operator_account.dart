import 'operator_capabilities.dart';
import 'pin.dart';

class OperatorAccount {
  const OperatorAccount({
    required this.id,
    required this.name,
    this.pinHash,
    required this.createdAt,
    this.role = OperatorRole.full,
  });

  final String id;
  final String name;
  final String? pinHash;
  final DateTime createdAt;
  final OperatorRole role;

  bool get hasPin => pinHash != null && pinHash!.isNotEmpty;

  bool get isObserver => role == OperatorRole.observer;

  bool checkPin(String pin) {
    if (!hasPin) return true;
    return pinMatches(pin: pin, hash: pinHash!);
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'pinHash': pinHash,
    'createdAt': createdAt.toIso8601String(),
    'role': role.name,
  };

  factory OperatorAccount.fromJson(Map<String, dynamic> json) {
    final roleRaw = '${json['role'] ?? ''}'.toLowerCase();
    final role = roleRaw == 'observer'
        ? OperatorRole.observer
        : OperatorRole.full;
    return OperatorAccount(
      id: json['id'] as String,
      name: json['name'] as String,
      pinHash: json['pinHash'] as String?,
      createdAt:
          DateTime.tryParse(json['createdAt'] as String? ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
      role: role,
    );
  }

  static final namePattern = RegExp(r'^[A-Za-z0-9_]{1,16}$');

  static OperatorAccount seedOperator() {
    return OperatorAccount(
      id: 'seed-operator',
      name: 'Operator',
      pinHash: hashPin('04206951'),
      createdAt: DateTime.utc(2026, 1, 1),
      role: OperatorRole.full,
    );
  }

  /// Browse + mild tools (weather, time, look at players). No power/moderation.
  static OperatorAccount seedLime() => OperatorAccount(
    id: 'seed-lime',
    name: 'Lime',
    pinHash: hashPin('citrus'),
    createdAt: DateTime.utc(2026, 1, 2),
    role: OperatorRole.observer,
  );
}
