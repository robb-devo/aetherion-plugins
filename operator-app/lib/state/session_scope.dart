import 'package:flutter/material.dart';

import 'operator_session.dart';

class SessionScope extends InheritedNotifier<OperatorSession> {
  const SessionScope({
    super.key,
    required OperatorSession session,
    required super.child,
  }) : super(notifier: session);

  static OperatorSession of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<SessionScope>();
    assert(scope != null, 'SessionScope missing');
    return scope!.notifier!;
  }
}
