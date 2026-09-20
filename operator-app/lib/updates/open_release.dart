import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import '../platform/platform_info.dart';
import 'update_checker.dart';

Future<void> openOperatorRelease(AppRelease release) async {
  final String target;
  if (!kIsWeb && isWindowsDesktop) {
    final win = release.windowsUrl;
    target = (win != null && win.startsWith('http')) ? win : release.htmlUrl;
  } else {
    final apk = release.apkUrl;
    target = (apk != null && apk.startsWith('http')) ? apk : release.htmlUrl;
  }
  await launchUrl(Uri.parse(target), mode: LaunchMode.externalApplication);
}
